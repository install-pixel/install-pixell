package com.offlinekeyboard.core.layout

import java.util.Locale

/** [code] is the short name shown on the keyboard, written in the language itself. */
enum class Language(val code: String, val locale: Locale) {
    UK("Укр", Locale.forLanguageTag("uk-UA")),
    PL("Pol", Locale.forLanguageTag("pl-PL")),
    EN("Eng", Locale.forLanguageTag("en-US")),
}

enum class KeyboardMode { COMPUTER, MOBILE }

/** A page of keys inside one mode. FUNCTION exists only in computer mode, SYMBOLS_MORE only in mobile mode. */
enum class Layer { LETTERS, SYMBOLS, SYMBOLS_MORE, FUNCTION }

enum class SpecialKey {
    SHIFT, CAPS_LOCK, CTRL, ALT, META,
    BACKSPACE, DELETE, ENTER, SPACE, TAB, ESC,
    LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN, INSERT,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    CUT, COPY, PASTE, SELECT_ALL, UNDO, REDO,
    LANGUAGE, MODE, LAYER_LETTERS, LAYER_SYMBOLS, LAYER_SYMBOLS_MORE, LAYER_FUNCTION,
    VOICE, SETTINGS, HIDE,
}

sealed interface KeyAction {
    /**
     * Inserts [text]. With Shift active, [shifted] is used when given; otherwise single letters are
     * upper-cased (Shift xor Caps Lock). Caps Lock never affects [shifted] (digits row behaves like a PC).
     */
    data class Text(val text: String, val shifted: String? = null) : KeyAction

    data class Special(val key: SpecialKey) : KeyAction

    /** Empty space used to align keys; ignores touches. */
    data object Spacer : KeyAction
}

enum class KeyStyle { NORMAL, MODIFIER, FUNCTION, ACTION, SPACE, SPACER }

data class Key(
    val action: KeyAction,
    val width: Float = 1f,
    /** Long-press options, in the order they appear in the popup. */
    val alternatives: List<String> = emptyList(),
    /**
     * Latin letter or digit printed on this position of a US PC keyboard. Ctrl/Alt/Win shortcuts use it,
     * so Ctrl+С on the Ukrainian layout is still Ctrl+C.
     */
    val physical: Char? = null,
    val repeatable: Boolean = false,
    val style: KeyStyle = KeyStyle.NORMAL,
    /** Fixed label; when null the label is derived from the action and keyboard state. */
    val label: String? = null,
)

data class KeyboardLayout(val rows: List<List<Key>>) {
    /** Width in key units of the widest row; narrower rows are centred. */
    val maxRowUnits: Float get() = rows.maxOfOrNull { row -> row.sumOf { it.width.toDouble() }.toFloat() } ?: 0f

    fun allKeys(): List<Key> = rows.flatten()
}
