package com.offlinekeyboard.core

import com.offlinekeyboard.core.input.Direction
import com.offlinekeyboard.core.input.EditAction
import com.offlinekeyboard.core.input.EditorCommand
import com.offlinekeyboard.core.input.KeyMods
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardLayout
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.LayoutProvider
import com.offlinekeyboard.core.layout.SpecialKey

/**
 * Keyboard state machine: which layout is shown and what each key press asks the editor to do.
 * Has no Android dependencies; the IME service feeds it key presses and executes the returned commands.
 */
class KeyboardController(
    mode: KeyboardMode,
    language: Language,
    enabledLanguages: List<Language> = Language.entries,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    interface Listener {
        fun onLanguageChanged(language: Language) {}
        fun onModeChanged(mode: KeyboardMode) {}
    }

    var listener: Listener? = null

    val modifiers = Modifiers()

    var mode: KeyboardMode = mode
        private set

    var language: Language = language
        private set

    var layer: Layer = Layer.LETTERS
        private set

    /**
     * The focused field takes raw key events only (terminals such as Termux): Ctrl+C, Ctrl+X ... are sent
     * to the app as keys (nano, vim, shells need them) instead of becoming copy/cut.
     */
    var rawKeyField = false

    /** User shortcuts (computer mode). Checked before the built-in Ctrl/Alt behaviour. */
    var shortcuts: List<Shortcut> = Shortcut.defaults

    var enabledLanguages: List<Language> = sanitize(enabledLanguages)
        set(value) {
            field = sanitize(value)
            if (language !in field) setLanguage(field.first())
        }

    init {
        if (this.language !in this.enabledLanguages) this.language = this.enabledLanguages.first()
    }

    fun layout(mobileCursorRow: Boolean): KeyboardLayout =
        LayoutProvider.layout(mode, language, layer, mobileCursorRow)

    /** A new text field got focus. Number and phone fields open on digits in mobile mode. */
    fun onStartField(numeric: Boolean) {
        modifiers.consumeOneShot()
        layer = if (numeric && mode == KeyboardMode.MOBILE) Layer.SYMBOLS else Layer.LETTERS
    }

    fun setMode(newMode: KeyboardMode) {
        if (newMode == mode) return
        mode = newMode
        layer = Layer.LETTERS
        modifiers.releaseAll()
        listener?.onModeChanged(newMode)
    }

    fun setLanguage(newLanguage: Language) {
        if (newLanguage == language) return
        language = newLanguage
        layer = Layer.LETTERS
        listener?.onLanguageChanged(newLanguage)
    }

    fun nextLanguage(step: Int = 1) {
        val list = enabledLanguages
        val index = list.indexOf(language).coerceAtLeast(0)
        setLanguage(list[Math.floorMod(index + step, list.size)])
    }

    /** Sentence-start capitalisation, mobile mode only. */
    fun setAutoShift(enabled: Boolean) {
        modifiers.setAutoShift(enabled && mode == KeyboardMode.MOBILE && layer == Layer.LETTERS)
    }

    /** User picked a long-press option. */
    fun onAlternative(text: String): List<EditorCommand> {
        val shift = modifiers.isActive(ModifierKey.SHIFT)
        val out = if (text.length == 1 && text[0].isLetter() && shift != modifiers.capsLock) {
            text.uppercase(language.locale)
        } else {
            text
        }
        modifiers.consumeOneShot()
        return listOf(EditorCommand.CommitText(out))
    }

    fun onKey(key: Key): List<EditorCommand> {
        pendingModifierShortcut = null
        return handleKey(key)
    }

    private fun handleKey(key: Key): List<EditorCommand> = when (val action = key.action) {
        is KeyAction.Text -> afterKey(onText(action, key.physical))
        is KeyAction.Special -> onSpecial(action.key)
        KeyAction.Spacer -> emptyList()
    }

    private fun currentMods() = KeyMods(
        shift = modifiers.isActive(ModifierKey.SHIFT),
        ctrl = modifiers.isActive(ModifierKey.CTRL),
        alt = modifiers.isActive(ModifierKey.ALT),
        meta = modifiers.isActive(ModifierKey.META),
    )

    private fun afterKey(commands: List<EditorCommand>): List<EditorCommand> {
        modifiers.consumeOneShot()
        return commands
    }

    private fun onText(action: KeyAction.Text, physical: Char?): List<EditorCommand> {
        val mods = currentMods()
        if (physical != null && modifiers.anyShortcutModifier) {
            val lower = physical.lowercaseChar()
            if (lower in 'a'..'z' || lower in '0'..'9') {
                findShortcut(mods, lower.uppercaseChar().toString())?.let { return runShortcut(it) }
            }
            // Polish programmer layout: Alt + letter = diacritic.
            if (language == Language.PL && mods.alt && !mods.ctrl && !mods.meta) {
                LayoutProvider.polishAltGr[lower]?.let { letter ->
                    val text = if (mods.shift != modifiers.capsLock) letter.uppercase(language.locale) else letter
                    return listOf(EditorCommand.CommitText(text))
                }
            }
            if (mods.ctrl && !mods.alt && !mods.meta && !rawKeyField) {
                val edit = when (lower) {
                    'a' -> EditAction.SELECT_ALL
                    'c' -> EditAction.COPY
                    'x' -> EditAction.CUT
                    'v' -> EditAction.PASTE
                    'z' -> if (mods.shift) EditAction.REDO else EditAction.UNDO
                    'y' -> EditAction.REDO
                    else -> null
                }
                if (edit != null) return listOf(EditorCommand.Edit(edit))
            }
            val keyName = PhysicalKeys.keyName(physical)
            if (keyName != null) return listOf(EditorCommand.SendKey(keyName, mods))
        }
        val text = KeyLabels.output(action, mods.shift, modifiers.capsLock, language)
        return listOf(EditorCommand.CommitText(text))
    }

    private fun findShortcut(mods: KeyMods, keyId: String?): Shortcut? =
        if (mode == KeyboardMode.COMPUTER) shortcuts.firstOrNull { it.isValid && it.matches(mods, keyId) } else null

    private fun runShortcut(shortcut: Shortcut): List<EditorCommand> {
        modifiers.releaseAll()
        return when (shortcut.action) {
            ShortcutAction.SWITCH_LANGUAGE -> { nextLanguage(); emptyList() }
            ShortcutAction.SWITCH_MODE -> {
                setMode(if (mode == KeyboardMode.COMPUTER) KeyboardMode.MOBILE else KeyboardMode.COMPUTER)
                emptyList()
            }
            ShortcutAction.VOICE_INPUT -> listOf(EditorCommand.StartVoice)
            ShortcutAction.TOGGLE_TRANSLATE -> listOf(EditorCommand.ToggleTranslate)
            ShortcutAction.TRANSLATE_SELECTION_OR_CLIPBOARD -> listOf(EditorCommand.TranslateSelectionOrClipboard)
            ShortcutAction.HIDE_KEYBOARD -> listOf(EditorCommand.HideKeyboard)
            ShortcutAction.INSERT_TEXT -> listOf(EditorCommand.CommitText(shortcut.text))
        }
    }

    /**
     * A completed modifier-only combination (e.g. Alt+Shift) waits like on Windows, where the switch happens
     * when the keys are released without another key: if the next key comes first (Alt+Shift+Z = Ż in Polish,
     * Ctrl+Shift+← selects a word), it is a normal combination and the shortcut is dropped.
     */
    var pendingModifierShortcut: Shortcut? = null
        private set

    /** Called by the service after [PENDING_SHORTCUT_DELAY_MS] without another key press. */
    fun firePendingShortcut(): List<EditorCommand> {
        val shortcut = pendingModifierShortcut ?: return emptyList()
        pendingModifierShortcut = null
        return runShortcut(shortcut)
    }

    private fun tapModifier(key: ModifierKey, now: Long): List<EditorCommand> {
        modifiers.tap(key, now)
        pendingModifierShortcut = if (modifiers.isActive(key)) findShortcut(currentMods(), null) else null
        return emptyList()
    }

    /** Key shortcuts on special keys (Space, Enter, Tab, F1–F12). */
    private fun specialShortcut(key: SpecialKey, mods: KeyMods): Shortcut? {
        if (!(mods.ctrl || mods.alt || mods.meta)) return null
        val id = when (key) {
            SpecialKey.SPACE -> "SPACE"
            SpecialKey.ENTER -> "ENTER"
            SpecialKey.TAB -> "TAB"
            SpecialKey.F1, SpecialKey.F2, SpecialKey.F3, SpecialKey.F4, SpecialKey.F5, SpecialKey.F6,
            SpecialKey.F7, SpecialKey.F8, SpecialKey.F9, SpecialKey.F10, SpecialKey.F11, SpecialKey.F12 -> key.name
            else -> return null
        }
        return findShortcut(mods, id)
    }

    private fun onSpecial(key: SpecialKey): List<EditorCommand> {
        val now = clock()
        val mods = currentMods()
        specialShortcut(key, mods)?.let { return runShortcut(it) }
        return when (key) {
            SpecialKey.SHIFT -> tapModifier(ModifierKey.SHIFT, now)
            SpecialKey.CTRL -> tapModifier(ModifierKey.CTRL, now)
            SpecialKey.ALT -> tapModifier(ModifierKey.ALT, now)
            SpecialKey.META -> tapModifier(ModifierKey.META, now)
            SpecialKey.CAPS_LOCK -> { modifiers.toggleCapsLock(); emptyList() }

            SpecialKey.BACKSPACE -> afterKey(listOf(if (mods.ctrl) EditorCommand.DeleteWordBackward else EditorCommand.DeleteBackward))
            SpecialKey.DELETE -> afterKey(listOf(if (mods.ctrl) EditorCommand.DeleteWordForward else EditorCommand.DeleteForward))
            SpecialKey.ENTER -> afterKey(
                listOf(
                    if (mods.ctrl || mods.alt || mods.meta) EditorCommand.SendKey("ENTER", mods)
                    else EditorCommand.Enter(forceNewline = mods.shift),
                ),
            )
            SpecialKey.SPACE -> afterKey(
                listOf(if (modifiers.anyShortcutModifier) EditorCommand.SendKey("SPACE", mods) else EditorCommand.CommitText(" ")),
            )
            SpecialKey.TAB -> afterKey(
                listOf(
                    if (mods.ctrl || mods.alt || mods.meta) EditorCommand.SendKey("TAB", mods)
                    else EditorCommand.Tab(reverse = mods.shift),
                ),
            )
            // Esc first drops armed modifiers (a local, visible action); otherwise the app gets a real Escape.
            SpecialKey.ESC -> if (modifiers.releaseAll()) emptyList() else listOf(EditorCommand.SendKey("ESCAPE"))

            SpecialKey.LEFT -> afterKey(listOf(move(Direction.LEFT, mods)))
            SpecialKey.RIGHT -> afterKey(listOf(move(Direction.RIGHT, mods)))
            SpecialKey.UP -> afterKey(listOf(move(Direction.UP, mods)))
            SpecialKey.DOWN -> afterKey(listOf(move(Direction.DOWN, mods)))
            SpecialKey.HOME -> afterKey(listOf(move(Direction.HOME, mods)))
            SpecialKey.END -> afterKey(listOf(move(Direction.END, mods)))
            SpecialKey.PAGE_UP -> afterKey(listOf(move(Direction.PAGE_UP, mods)))
            SpecialKey.PAGE_DOWN -> afterKey(listOf(move(Direction.PAGE_DOWN, mods)))
            SpecialKey.INSERT -> afterKey(listOf(EditorCommand.SendKey("INSERT", mods)))
            SpecialKey.F1, SpecialKey.F2, SpecialKey.F3, SpecialKey.F4, SpecialKey.F5, SpecialKey.F6,
            SpecialKey.F7, SpecialKey.F8, SpecialKey.F9, SpecialKey.F10, SpecialKey.F11, SpecialKey.F12 ->
                afterKey(listOf(EditorCommand.SendKey(key.name, mods)))

            SpecialKey.CUT -> afterKey(listOf(EditorCommand.Edit(EditAction.CUT)))
            SpecialKey.COPY -> afterKey(listOf(EditorCommand.Edit(EditAction.COPY)))
            SpecialKey.PASTE -> afterKey(listOf(EditorCommand.Edit(EditAction.PASTE)))
            SpecialKey.SELECT_ALL -> afterKey(listOf(EditorCommand.Edit(EditAction.SELECT_ALL)))
            SpecialKey.UNDO -> afterKey(listOf(EditorCommand.Edit(EditAction.UNDO)))
            SpecialKey.REDO -> afterKey(listOf(EditorCommand.Edit(EditAction.REDO)))

            SpecialKey.LANGUAGE -> { nextLanguage(); emptyList() }
            SpecialKey.MODE -> {
                setMode(if (mode == KeyboardMode.COMPUTER) KeyboardMode.MOBILE else KeyboardMode.COMPUTER)
                emptyList()
            }
            SpecialKey.LAYER_LETTERS -> { layer = Layer.LETTERS; emptyList() }
            SpecialKey.LAYER_SYMBOLS -> { toggleLayer(Layer.SYMBOLS); emptyList() }
            SpecialKey.LAYER_SYMBOLS_MORE -> { toggleLayer(Layer.SYMBOLS_MORE); emptyList() }
            SpecialKey.LAYER_FUNCTION -> { toggleLayer(Layer.FUNCTION); emptyList() }

            SpecialKey.VOICE -> listOf(EditorCommand.StartVoice)
            SpecialKey.SETTINGS -> listOf(EditorCommand.OpenSettings)
            SpecialKey.HIDE -> listOf(EditorCommand.HideKeyboard)
        }
    }

    /** In computer mode the symbol and Fn keys toggle; in mobile mode ?123 / =\< switch pages. */
    private fun toggleLayer(target: Layer) {
        layer = if (mode == KeyboardMode.COMPUTER && layer == target) Layer.LETTERS else target
    }

    private fun move(direction: Direction, mods: KeyMods): EditorCommand {
        if (mods.alt || mods.meta) return EditorCommand.SendKey(keyNameOf(direction), mods)
        val horizontal = direction == Direction.LEFT || direction == Direction.RIGHT
        val edge = direction == Direction.HOME || direction == Direction.END
        return EditorCommand.Move(
            direction = direction,
            select = mods.shift,
            byWord = mods.ctrl && horizontal,
            toDocumentEdge = mods.ctrl && edge,
        )
    }

    private fun keyNameOf(direction: Direction) = when (direction) {
        Direction.LEFT -> "DPAD_LEFT"
        Direction.RIGHT -> "DPAD_RIGHT"
        Direction.UP -> "DPAD_UP"
        Direction.DOWN -> "DPAD_DOWN"
        Direction.HOME -> "MOVE_HOME"
        Direction.END -> "MOVE_END"
        Direction.PAGE_UP -> "PAGE_UP"
        Direction.PAGE_DOWN -> "PAGE_DOWN"
    }

    companion object {
        const val PENDING_SHORTCUT_DELAY_MS = 500L

        private fun sanitize(languages: List<Language>): List<Language> {
            val ordered = Language.entries.filter { it in languages }
            return ordered.ifEmpty { Language.entries }
        }
    }
}

/** Maps a US-keyboard character to the suffix of its Android KEYCODE_ constant. */
object PhysicalKeys {
    fun keyName(c: Char): String? {
        val lower = c.lowercaseChar()
        return when {
            lower in 'a'..'z' -> lower.uppercaseChar().toString()
            lower in '0'..'9' -> lower.toString()
            else -> when (lower) {
                '`' -> "GRAVE"
                '-' -> "MINUS"
                '=' -> "EQUALS"
                '[' -> "LEFT_BRACKET"
                ']' -> "RIGHT_BRACKET"
                '\\' -> "BACKSLASH"
                ';' -> "SEMICOLON"
                '\'' -> "APOSTROPHE"
                ',' -> "COMMA"
                '.' -> "PERIOD"
                '/' -> "SLASH"
                else -> null
            }
        }
    }
}
