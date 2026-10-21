package com.offlinekeyboard.input

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.offlinekeyboard.core.input.Direction
import com.offlinekeyboard.core.input.EditAction
import com.offlinekeyboard.core.input.EditorCommand
import com.offlinekeyboard.core.input.EnterResolution
import com.offlinekeyboard.core.input.FieldInfo
import com.offlinekeyboard.core.input.KeyMods
import com.offlinekeyboard.core.input.SelectionMath
import com.offlinekeyboard.core.input.TabResolution
import com.offlinekeyboard.core.input.TextBoundaries
import com.offlinekeyboard.core.input.TextSelection

/**
 * Executes [EditorCommand]s on the focused field of another app through its [InputConnection].
 *
 * Strategy: text goes through commitText/deleteSurroundingText (works everywhere, no extra window);
 * horizontal moves use setSelection on the field's own text when the field exposes it, otherwise key events;
 * vertical moves, function keys and shortcuts are key events. Every call tolerates a missing connection
 * (focus lost, app closed) by doing nothing.
 */
class InputConnectionAdapter(private val service: InputMethodService) {

    var field: FieldInfo = FieldInfo(0, 0)

    private val ic: InputConnection? get() = service.currentInputConnection

    fun execute(command: EditorCommand) {
        val ic = ic ?: return
        when (command) {
            is EditorCommand.CommitText -> commitText(ic, command.text)
            EditorCommand.DeleteBackward -> deleteBackward(ic, byWord = false)
            EditorCommand.DeleteWordBackward -> deleteBackward(ic, byWord = true)
            EditorCommand.DeleteForward -> deleteForward(ic, byWord = false)
            EditorCommand.DeleteWordForward -> deleteForward(ic, byWord = true)
            is EditorCommand.Move -> move(ic, command)
            is EditorCommand.SendKey -> sendKey(ic, command.keyName, command.mods)
            is EditorCommand.Edit -> edit(ic, command.action)
            is EditorCommand.Enter -> enter(ic, command.forceNewline)
            is EditorCommand.Tab -> tab(ic, command.reverse)
            // Handled by the service, not the field.
            EditorCommand.StartVoice, EditorCommand.ToggleTranslate, EditorCommand.TranslateSelectionOrClipboard,
            EditorCommand.HideKeyboard,
            EditorCommand.OpenSettings -> Unit
        }
    }

    private fun commitText(ic: InputConnection, text: String) {
        if (field.isRawKeyField) {
            // Terminal-like fields expect key events; sendKeyChar maps digits/newline to keys, the rest to text.
            text.forEach { service.sendKeyChar(it) }
        } else {
            ic.commitText(text, 1)
        }
    }

    private fun deleteBackward(ic: InputConnection, byWord: Boolean) {
        if (field.isRawKeyField) {
            sendKey(ic, "DEL", KeyMods(ctrl = byWord))
            return
        }
        if (!ic.getSelectedText(0).isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(CONTEXT_CHARS, 0)
        if (before.isNullOrEmpty()) {
            // Nothing visible to delete (or the field hides its text): let the app handle a real Backspace,
            // e.g. to remove a chip or attachment.
            sendKey(ic, "DEL", KeyMods())
            return
        }
        val count = if (byWord) TextBoundaries.previousWordLength(before) else TextBoundaries.previousGraphemeLength(before)
        ic.deleteSurroundingText(count.coerceAtLeast(1), 0)
    }

    private fun deleteForward(ic: InputConnection, byWord: Boolean) {
        if (field.isRawKeyField) {
            sendKey(ic, "FORWARD_DEL", KeyMods(ctrl = byWord))
            return
        }
        if (!ic.getSelectedText(0).isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val after = ic.getTextAfterCursor(CONTEXT_CHARS, 0)
        if (after.isNullOrEmpty()) {
            sendKey(ic, "FORWARD_DEL", KeyMods())
            return
        }
        val count = if (byWord) TextBoundaries.nextWordLength(after) else TextBoundaries.nextGraphemeLength(after)
        ic.deleteSurroundingText(0, count.coerceAtLeast(1))
    }

    private fun move(ic: InputConnection, cmd: EditorCommand.Move) {
        if (!field.isRawKeyField && SelectionMath.supports(cmd.direction) && moveWithinText(ic, cmd)) return

        val keyName = when (cmd.direction) {
            Direction.LEFT -> "DPAD_LEFT"
            Direction.RIGHT -> "DPAD_RIGHT"
            Direction.UP -> "DPAD_UP"
            Direction.DOWN -> "DPAD_DOWN"
            Direction.HOME -> "MOVE_HOME"
            Direction.END -> "MOVE_END"
            Direction.PAGE_UP -> "PAGE_UP"
            Direction.PAGE_DOWN -> "PAGE_DOWN"
        }
        sendKey(ic, keyName, KeyMods(shift = cmd.select, ctrl = cmd.byWord || cmd.toDocumentEdge))
    }

    /** Returns false when the field does not expose its text, so the caller falls back to key events. */
    private fun moveWithinText(ic: InputConnection, cmd: EditorCommand.Move): Boolean {
        val request = ExtractedTextRequest().apply { hintMaxChars = MAX_EXTRACTED_CHARS }
        val extracted = ic.getExtractedText(request, 0) ?: return false
        val text = extracted.text ?: return false
        if (extracted.selectionStart < 0 || extracted.selectionEnd < 0) return false
        // Only part of a long document came back: document edges are unknown, let the app handle Ctrl+Home/End.
        val partial = extracted.startOffset != 0 || text.length >= MAX_EXTRACTED_CHARS
        if (partial && cmd.toDocumentEdge) return false

        val current = TextSelection(extracted.selectionStart, extracted.selectionEnd)
        val next = SelectionMath.move(text, current, cmd.direction, cmd.select, cmd.byWord, cmd.toDocumentEdge)
        val offset = extracted.startOffset
        ic.setSelection(next.anchor + offset, next.active + offset)
        return true
    }

    private fun edit(ic: InputConnection, action: EditAction) {
        val handled = when (action) {
            EditAction.CUT -> ic.performContextMenuAction(android.R.id.cut)
            EditAction.COPY -> ic.performContextMenuAction(android.R.id.copy)
            EditAction.PASTE -> ic.performContextMenuAction(android.R.id.paste)
            EditAction.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            EditAction.UNDO, EditAction.REDO -> false
        }
        if (handled) return
        // Fallback (and the only path for undo/redo): the standard PC shortcut as a key event.
        when (action) {
            EditAction.CUT -> sendKey(ic, "X", KeyMods(ctrl = true))
            EditAction.COPY -> sendKey(ic, "C", KeyMods(ctrl = true))
            EditAction.PASTE -> sendKey(ic, "V", KeyMods(ctrl = true))
            EditAction.SELECT_ALL -> sendKey(ic, "A", KeyMods(ctrl = true))
            EditAction.UNDO -> sendKey(ic, "Z", KeyMods(ctrl = true))
            EditAction.REDO -> sendKey(ic, "Z", KeyMods(ctrl = true, shift = true))
        }
    }

    private fun enter(ic: InputConnection, forceNewline: Boolean) {
        when (val resolution = field.resolveEnter(forceNewline)) {
            EnterResolution.Newline -> ic.commitText("\n", 1)
            EnterResolution.KeyEvent -> sendKey(ic, "ENTER", KeyMods())
            is EnterResolution.EditorAction -> ic.performEditorAction(resolution.actionId)
        }
    }

    private fun tab(ic: InputConnection, reverse: Boolean) {
        when (field.resolveTab(reverse)) {
            TabResolution.InsertTab -> ic.commitText("\t", 1)
            TabResolution.KeyEvent -> sendKey(ic, "TAB", KeyMods(shift = reverse))
        }
    }

    /**
     * Sends a down/up pair. Modifier keys are pressed around it as well, because many views read the
     * modifier state from the preceding modifier key events rather than from the meta flags.
     */
    private fun sendKey(ic: InputConnection, keyName: String, mods: KeyMods) {
        val keyCode = KeyEvent.keyCodeFromString("KEYCODE_$keyName")
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return

        val modifierKeys = buildList {
            if (mods.ctrl) add(KeyEvent.KEYCODE_CTRL_LEFT to (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
            if (mods.alt) add(KeyEvent.KEYCODE_ALT_LEFT to (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON))
            if (mods.shift) add(KeyEvent.KEYCODE_SHIFT_LEFT to (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON))
            if (mods.meta) add(KeyEvent.KEYCODE_META_LEFT to (KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON))
        }
        val now = SystemClock.uptimeMillis()
        var meta = 0
        for ((code, flag) in modifierKeys) {
            meta = meta or flag
            ic.sendKeyEvent(keyEvent(now, KeyEvent.ACTION_DOWN, code, meta))
        }
        ic.sendKeyEvent(keyEvent(now, KeyEvent.ACTION_DOWN, keyCode, meta))
        ic.sendKeyEvent(keyEvent(now, KeyEvent.ACTION_UP, keyCode, meta))
        for ((code, flag) in modifierKeys.asReversed()) {
            meta = meta and flag.inv()
            ic.sendKeyEvent(keyEvent(now, KeyEvent.ACTION_UP, code, meta))
        }
    }

    private fun keyEvent(downTime: Long, action: Int, code: Int, meta: Int) = KeyEvent(
        downTime, SystemClock.uptimeMillis(), action, code, 0, meta,
        KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
        KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
    )

    private companion object {
        const val CONTEXT_CHARS = 64
        const val MAX_EXTRACTED_CHARS = 20_000
    }
}
