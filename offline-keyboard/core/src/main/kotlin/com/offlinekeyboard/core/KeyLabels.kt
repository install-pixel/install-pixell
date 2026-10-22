package com.offlinekeyboard.core

import com.offlinekeyboard.core.input.EnterLabel
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.SpecialKey

/** What a key shows, given the keyboard state. Shared by the Android view and the tests. */
data class LabelContext(
    val shift: Boolean,
    val capsLock: Boolean,
    val language: Language,
    val mode: KeyboardMode,
    val enterLabel: EnterLabel = EnterLabel.NEWLINE,
)

object KeyLabels {

    val languageNames: Map<Language, String> = mapOf(
        Language.UK to "Українська",
        Language.PL to "Polski",
        Language.EN to "English",
    )

    /** Text that a [KeyAction.Text] key inserts in the given state. */
    fun output(action: KeyAction.Text, shift: Boolean, capsLock: Boolean, language: Language): String {
        val text = action.text
        if (text.length == 1 && text[0].isLetter()) {
            return if (shift != capsLock) text.uppercase(language.locale) else text
        }
        return if (shift && action.shifted != null) action.shifted else text
    }

    fun label(key: Key, ctx: LabelContext): String {
        key.label?.let { return it }
        return when (val action = key.action) {
            is KeyAction.Text -> output(action, ctx.shift, ctx.capsLock, ctx.language)
            is KeyAction.Special -> specialLabel(action.key, ctx)
            KeyAction.Spacer -> ""
        }
    }

    /**
     * Small corner hint: the Shift symbol on PC keys, otherwise the first long-press option of a letter or
     * digit key. Punctuation keys show no hint (their options are still on long press) to keep the
     * keyboard free of characters that look duplicated.
     */
    fun hint(key: Key, ctx: LabelContext): String? {
        val action = key.action as? KeyAction.Text ?: return null
        if (action.shifted != null && !ctx.shift) return action.shifted
        if (!action.text.first().isLetterOrDigit()) return null
        return key.alternatives.firstOrNull()
    }

    private fun specialLabel(key: SpecialKey, ctx: LabelContext): String = when (key) {
        SpecialKey.SHIFT -> "⇧"
        SpecialKey.CAPS_LOCK -> "Caps"
        SpecialKey.CTRL -> "Ctrl"
        SpecialKey.ALT -> "Alt"
        SpecialKey.META -> "Win"
        SpecialKey.BACKSPACE -> "⌫"
        SpecialKey.DELETE -> "Del"
        SpecialKey.ENTER -> enterGlyph(ctx.enterLabel)
        SpecialKey.SPACE -> languageNames.getValue(ctx.language)
        SpecialKey.TAB -> "Tab"
        SpecialKey.ESC -> "Esc"
        SpecialKey.LEFT -> "←"
        SpecialKey.RIGHT -> "→"
        SpecialKey.UP -> "↑"
        SpecialKey.DOWN -> "↓"
        SpecialKey.HOME -> "Home"
        SpecialKey.END -> "End"
        SpecialKey.PAGE_UP -> "PgUp"
        SpecialKey.PAGE_DOWN -> "PgDn"
        SpecialKey.INSERT -> "Ins"
        SpecialKey.F1, SpecialKey.F2, SpecialKey.F3, SpecialKey.F4, SpecialKey.F5, SpecialKey.F6,
        SpecialKey.F7, SpecialKey.F8, SpecialKey.F9, SpecialKey.F10, SpecialKey.F11, SpecialKey.F12 -> key.name
        SpecialKey.CUT -> "Cut"
        SpecialKey.COPY -> "Copy"
        SpecialKey.PASTE -> "Paste"
        SpecialKey.SELECT_ALL -> "Sel. all"
        SpecialKey.UNDO -> "Undo"
        SpecialKey.REDO -> "Redo"
        SpecialKey.LANGUAGE -> ctx.language.code
        SpecialKey.MODE -> if (ctx.mode == KeyboardMode.COMPUTER) "📱" else "⌨"
        SpecialKey.LAYER_LETTERS -> if (ctx.language == Language.UK) "АБВ" else "ABC"
        SpecialKey.LAYER_SYMBOLS -> if (ctx.mode == KeyboardMode.COMPUTER) "Sym" else "?123"
        SpecialKey.LAYER_SYMBOLS_MORE -> "=\\<"
        SpecialKey.LAYER_FUNCTION -> "Fn"
        SpecialKey.LAYER_EMOJI -> "☺"
        SpecialKey.EMOJI_PREVIOUS -> "‹"
        SpecialKey.EMOJI_NEXT -> "›"
        SpecialKey.VOICE -> "🎤"
        SpecialKey.SETTINGS -> "⚙"
        SpecialKey.HIDE -> "▾"
    }

    private fun enterGlyph(label: EnterLabel): String = when (label) {
        EnterLabel.NEWLINE -> "↵"
        EnterLabel.GO -> "Go"
        EnterLabel.SEARCH -> "🔍"
        EnterLabel.SEND -> "➤"
        EnterLabel.NEXT -> "Next"
        EnterLabel.PREVIOUS -> "Prev"
        EnterLabel.DONE -> "✓"
        EnterLabel.TRANSLATE -> "⇄"
    }
}
