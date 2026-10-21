package com.offlinekeyboard.core.input

enum class Direction { LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN }

enum class EditAction { CUT, COPY, PASTE, SELECT_ALL, UNDO, REDO }

data class KeyMods(
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
)

/** What the keyboard asks the text field to do. The Android adapter turns these into InputConnection calls. */
sealed interface EditorCommand {
    data class CommitText(val text: String) : EditorCommand
    data object DeleteBackward : EditorCommand
    data object DeleteForward : EditorCommand
    data object DeleteWordBackward : EditorCommand
    data object DeleteWordForward : EditorCommand

    /**
     * [byWord]: Ctrl+←/→. [toDocumentEdge]: Ctrl+Home/End. [select]: Shift held, extend selection.
     */
    data class Move(
        val direction: Direction,
        val select: Boolean = false,
        val byWord: Boolean = false,
        val toDocumentEdge: Boolean = false,
    ) : EditorCommand

    /** A raw key event; [keyName] is the suffix of an Android KEYCODE_ constant, e.g. "F5", "A", "ESCAPE". */
    data class SendKey(val keyName: String, val mods: KeyMods = KeyMods()) : EditorCommand

    data class Edit(val action: EditAction) : EditorCommand
    data class Enter(val forceNewline: Boolean) : EditorCommand
    data class Tab(val reverse: Boolean) : EditorCommand

    data object StartVoice : EditorCommand
    data object ToggleTranslate : EditorCommand
    data object HideKeyboard : EditorCommand
    data object OpenSettings : EditorCommand
}
