package com.offlinekeyboard.core.layout

import com.offlinekeyboard.core.layout.SpecialKey.*

/**
 * Builds every key grid of the keyboard. Layouts are plain data so they can be unit-tested without Android.
 *
 * Computer mode rows are all 15.5 units wide (PC proportions); mobile rows are 10 units (11 for Ukrainian).
 */
object LayoutProvider {

    fun layout(
        mode: KeyboardMode,
        language: Language,
        layer: Layer,
        mobileCursorRow: Boolean = false,
        emojiPage: Int = 0,
    ): KeyboardLayout = withoutDuplicateAlternatives(
        when (mode) {
        KeyboardMode.COMPUTER -> when (layer) {
            Layer.FUNCTION -> computerFunction()
            Layer.SYMBOLS, Layer.SYMBOLS_MORE -> computerSymbols()
            Layer.LETTERS, Layer.EMOJI -> computerLetters(language)
        }
        KeyboardMode.MOBILE -> {
            val base = when (layer) {
                Layer.SYMBOLS, Layer.FUNCTION -> mobileSymbols(language)
                Layer.SYMBOLS_MORE -> mobileSymbolsMore(language)
                Layer.LETTERS -> mobileLetters(language)
                Layer.EMOJI -> mobileEmoji(language, emojiPage)
            }
            if (mobileCursorRow && layer != Layer.EMOJI) withCursorRow(base) else base
        }
        },
    )

    /**
     * A long-press option that is already a key of the same layout would show the same character twice
     * (e.g. "є" as a key and as a hint on "е"), so it is dropped.
     */
    private fun withoutDuplicateAlternatives(layout: KeyboardLayout): KeyboardLayout {
        val onKeys = layout.allKeys().mapNotNull { (it.action as? KeyAction.Text)?.text }.toSet()
        return KeyboardLayout(
            layout.rows.map { row ->
                row.map { key -> if (key.alternatives.any { it in onKeys }) key.copy(alternatives = key.alternatives - onKeys) else key }
            },
        )
    }

    // ---------------------------------------------------------------- computer mode

    /** Characters printed on each PC key position, per language. */
    private class PcChars(
        val numberRow: List<Pair<String, String?>>, // ` 1 2 ... 0 - =
        val topRow: List<Pair<String, String?>>,    // q..p [ ] \
        val homeRow: List<Pair<String, String?>>,   // a..l ; '
        val bottomRow: List<Pair<String, String?>>, // z..m , . /
    )

    private const val PC_NUMBER = "`1234567890-="
    private const val PC_TOP = "qwertyuiop[]\\"
    private const val PC_HOME = "asdfghjkl;'"
    private const val PC_BOTTOM = "zxcvbnm,./"

    private fun latinPcChars(): PcChars {
        fun row(base: String, shifted: String) = base.indices.map { i ->
            val c = base[i]
            c.toString() to if (c.isLetter()) null else shifted[i].toString()
        }
        return PcChars(
            numberRow = row(PC_NUMBER, "~!@#$%^&*()_+"),
            topRow = row(PC_TOP, "QWERTYUIOP{}|"),
            homeRow = row(PC_HOME, "ASDFGHJKL:\""),
            bottomRow = row(PC_BOTTOM, "ZXCVBNM<>?"),
        )
    }

    /** Ukrainian (enhanced) PC layout: ЙЦУКЕН with Ї, Є, Ґ and the apostrophe on the ` key. */
    private fun ukrainianPcChars(): PcChars {
        val digitsShift = listOf("₴", "!", "\"", "№", ";", "%", ":", "?", "*", "(", ")", "_", "+")
        val numberBase = listOf("'", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=")
        fun letters(s: String) = s.map { it.toString() to null }
        return PcChars(
            numberRow = numberBase.zip(digitsShift),
            topRow = letters("йцукенгшщзхїґ"),
            homeRow = letters("фівапролджє"),
            bottomRow = letters("ячсмитьбю") + listOf("." to ","),
        )
    }

    private fun pcChars(language: Language) =
        if (language == Language.UK) ukrainianPcChars() else latinPcChars()

    private fun computerLetters(language: Language): KeyboardLayout {
        val chars = pcChars(language)
        fun keys(row: List<Pair<String, String?>>, physical: String) = row.mapIndexed { i, (text, shifted) ->
            Key(
                KeyAction.Text(text, shifted),
                alternatives = alternativesFor(language, text, mobile = false),
                physical = physical[i],
            )
        }
        val number = keys(chars.numberRow, PC_NUMBER)
        val top = keys(chars.topRow, PC_TOP)
        val home = keys(chars.homeRow, PC_HOME)
        val bottom = keys(chars.bottomRow, PC_BOTTOM)
        return KeyboardLayout(
            listOf(
                listOf(fn(ESC)) + number + fn(BACKSPACE, 1.5f, repeatable = true),
                listOf(fn(TAB, 1.5f)) + top.dropLast(1) + top.last().copy(width = 2f),
                listOf(mod(CAPS_LOCK, 2f)) + home + action(ENTER, 2.5f),
                listOf(mod(SHIFT, 2.25f)) + bottom + listOf(mod(SHIFT, 1.25f), arrow(UP), fn(DELETE, repeatable = true)),
                computerBottomRow(),
            ),
        )
    }

    private fun computerSymbols(): KeyboardLayout {
        fun texts(s: List<String>) = s.map { Key(KeyAction.Text(it)) }
        return KeyboardLayout(
            listOf(
                listOf(fn(ESC)) + texts("~ ` ! @ # $ % ^ & * ( )".split(" ")) + fn(BACKSPACE, 2.5f, repeatable = true),
                listOf(fn(TAB, 1.5f)) + texts("€ zł ₴ £ ¥ ¢ § ° № ©".split(" ")) +
                    texts(listOf("«", "»")) + Key(KeyAction.Text("|"), width = 2f),
                listOf(mod(CAPS_LOCK, 2f)) + texts("„ ” “ ‘ ’ – — … • ± ×".split(" ")) + action(ENTER, 2.5f),
                listOf(mod(SHIFT, 2.25f)) + texts("÷ ≠ ≈ ≤ ≥ √ ∞ ™ ® ¶".split(" ")) +
                    listOf(mod(SHIFT, 1.25f), arrow(UP), fn(DELETE, repeatable = true)),
                computerBottomRow(),
            ),
        )
    }

    private fun computerFunction(): KeyboardLayout {
        val fKeys = listOf(F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12).map { fn(it) }
        return KeyboardLayout(
            listOf(
                listOf(fn(ESC)) + fKeys + fn(BACKSPACE, 2.5f, repeatable = true),
                listOf(fn(TAB, 1.5f), fn(INSERT, 1.5f), arrow(HOME, 1.5f), arrow(PAGE_UP, 1.5f),
                    fn(CUT, 2.375f), fn(COPY, 2.375f), fn(PASTE, 2.375f), fn(SELECT_ALL, 2.375f)),
                listOf(spacer(1.5f), fn(DELETE, 1.5f, repeatable = true), arrow(END, 1.5f), arrow(PAGE_DOWN, 1.5f),
                    fn(UNDO, 2.375f), fn(REDO, 2.375f), action(ENTER, 4.75f)),
                listOf(mod(SHIFT, 2.25f), spacer(10f), mod(SHIFT, 1.25f), arrow(UP), spacer(1f)),
                computerBottomRow(),
            ),
        )
    }

    /**
     * Like a PC: no language or mode key. The language changes with Win+Space (or the user's shortcut);
     * computer/mobile is switched from the toolbar.
     */
    private fun computerBottomRow() = listOf(
        mod(CTRL, 1.25f), mod(LAYER_FUNCTION, 1.25f), mod(META), mod(ALT),
        Key(KeyAction.Special(SPACE), width = 7f, style = KeyStyle.SPACE),
        mod(LAYER_SYMBOLS), arrow(LEFT), arrow(DOWN), arrow(RIGHT),
    )

    // ---------------------------------------------------------------- mobile mode

    private fun mobileLetters(language: Language): KeyboardLayout {
        val rows = when (language) {
            Language.UK -> listOf("йцукенгшщзх", "фівапролджє", "ячсмитьбю")
            Language.PL, Language.EN -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        val physicalRows = listOf("qwertyuiop[", "asdfghjkl;'", "zxcvbnm,.")
        val digits = "1234567890"
        val letterRows = rows.mapIndexed { r, chars ->
            chars.mapIndexed { i, c ->
                val text = c.toString()
                val alts = alternativesFor(language, text, mobile = true).toMutableList()
                if (r == 0 && i < digits.length) alts += digits[i].toString()
                Key(KeyAction.Text(text), alternatives = alts, physical = physicalRows[r].getOrNull(i))
            }
        }
        val edge = if (language == Language.UK) 1f else 1.5f
        return KeyboardLayout(
            listOf(
                letterRows[0],
                letterRows[1],
                listOf(mod(SHIFT, edge)) + letterRows[2] + fn(BACKSPACE, edge, repeatable = true),
                mobileBottomRow(language, LAYER_SYMBOLS),
            ),
        )
    }

    private fun mobileSymbols(language: Language): KeyboardLayout {
        val currency = when (language) {
            Language.UK -> Key(KeyAction.Text("₴"), alternatives = listOf("$", "€", "zł", "£"))
            Language.PL -> Key(KeyAction.Text("zł"), alternatives = listOf("€", "$", "₴", "£"))
            Language.EN -> Key(KeyAction.Text("$"), alternatives = listOf("€", "£", "¥", "₴", "zł"))
        }
        fun texts(s: String) = s.split(" ").map { Key(KeyAction.Text(it), alternatives = punctuationAlternatives(it)) }
        return KeyboardLayout(
            listOf(
                texts("1 2 3 4 5 6 7 8 9 0"),
                texts("@ #") + currency + texts("_ & - + ( ) /"),
                listOf(mod(LAYER_SYMBOLS_MORE, 1.5f)) + texts("* \" ' : ; ! ?") + fn(BACKSPACE, 1.5f, repeatable = true),
                mobileBottomRow(language, LAYER_LETTERS),
            ),
        )
    }

    private fun mobileSymbolsMore(language: Language): KeyboardLayout {
        fun texts(s: String) = s.split(" ").map { Key(KeyAction.Text(it)) }
        return KeyboardLayout(
            listOf(
                texts("~ ` | • √ π ÷ × ¶ ∆"),
                texts("€ £ ¥ $ ^ ° = { } \\"),
                listOf(mod(LAYER_SYMBOLS, 1.5f)) + texts("% © ® ™ ✓ [ ]") + fn(BACKSPACE, 1.5f, repeatable = true),
                mobileBottomRow(language, LAYER_LETTERS),
            ),
        )
    }

    /** The emoji panel: 32 emoji (or 20 text smileys) per page, page arrows and back to letters. */
    private fun mobileEmoji(language: Language, page: Int): KeyboardLayout {
        val pages = EmojiSet.pages
        val current = pages[page.mod(pages.size)]
        val width = 8f / current.columns
        val rows = current.items.chunked(current.columns).map { row ->
            row.map { Key(KeyAction.Text(it), width = width) }
        }
        val letters = Key(
            KeyAction.Special(LAYER_LETTERS), width = 1.25f, style = KeyStyle.MODIFIER,
            label = if (language == Language.UK) "АБВ" else "ABC",
        )
        val bottom = listOf(
            letters,
            fn(EMOJI_PREVIOUS),
            Key(KeyAction.Special(EMOJI_NEXT), width = 1.5f, style = KeyStyle.FUNCTION, label = "${current.title} ${page.mod(pages.size) + 1}/${pages.size}"),
            fn(EMOJI_NEXT),
            fn(STICKERS),
            Key(KeyAction.Special(SPACE), style = KeyStyle.SPACE, label = "␣"),
            fn(BACKSPACE, 1.25f, repeatable = true),
        )
        return KeyboardLayout(rows + listOf(bottom))
    }

    private fun mobileBottomRow(language: Language, layerKey: SpecialKey): List<Key> {
        val apostrophe = if (language == Language.UK) {
            listOf(Key(KeyAction.Text("'"), alternatives = punctuationAlternatives("'")))
        } else {
            emptyList()
        }
        return listOf(mod(layerKey, 1.5f), fn(LANGUAGE), fn(LAYER_EMOJI)) + apostrophe + listOf(
            Key(KeyAction.Text(","), alternatives = punctuationAlternatives(",")),
            Key(KeyAction.Special(SPACE), width = 3f, style = KeyStyle.SPACE),
            Key(KeyAction.Text("."), alternatives = punctuationAlternatives(".")),
            action(ENTER, 1.5f),
        )
    }

    private fun withCursorRow(base: KeyboardLayout): KeyboardLayout {
        val width = base.maxRowUnits / 6f
        val row = listOf(HOME, LEFT, UP, DOWN, RIGHT, END).map { arrow(it, width) }
        return KeyboardLayout(listOf(row) + base.rows)
    }

    // ---------------------------------------------------------------- long-press alternatives

    private val polishAlternatives = mapOf(
        "a" to listOf("ą"), "c" to listOf("ć"), "e" to listOf("ę"), "l" to listOf("ł"),
        "n" to listOf("ń"), "o" to listOf("ó"), "s" to listOf("ś"), "z" to listOf("ż", "ź"), "x" to listOf("ź"),
    )

    private val ukrainianAlternatives = mapOf(
        // Ukrainian extras plus the Russian letters missing from the Ukrainian alphabet (ы, э, ё, ъ).
        "г" to listOf("ґ"), "і" to listOf("ї"), "е" to listOf("є", "э", "ё"), "и" to listOf("ы"),
        "ь" to listOf("ʼ", "ъ"),
    )

    private val englishAlternatives = mapOf(
        "a" to listOf("à", "á", "â", "ä"), "e" to listOf("é", "è", "ê", "ë"), "i" to listOf("í", "ì", "î", "ï"),
        "o" to listOf("ó", "ò", "ô", "ö"), "u" to listOf("ú", "ù", "û", "ü"), "c" to listOf("ç"), "n" to listOf("ñ"),
    )

    private fun alternativesFor(language: Language, text: String, mobile: Boolean): List<String> {
        val letters = when (language) {
            Language.PL -> polishAlternatives
            Language.UK -> ukrainianAlternatives
            Language.EN -> englishAlternatives
        }[text]
        return letters ?: if (mobile || text.length == 1 && !text[0].isLetterOrDigit()) punctuationAlternatives(text) else emptyList()
    }

    private fun punctuationAlternatives(text: String): List<String> = when (text) {
        "." -> listOf("…", "?", "!", ":", ";")
        "," -> listOf(";", ":", "-")
        "-" -> listOf("–", "—", "_")
        "'" -> listOf("ʼ", "’", "‘", "\"")
        "\"" -> listOf("„", "”", "“", "«", "»")
        "?" -> listOf("¿")
        "!" -> listOf("¡")
        "(" -> listOf("[", "{", "<")
        ")" -> listOf("]", "}", ">")
        else -> emptyList()
    }

    // ---------------------------------------------------------------- key helpers

    /**
     * Polish programmer layout: right Alt + letter gives the diacritic, e.g. Alt+A = ą.
     * Used by the controller in computer mode.
     */
    val polishAltGr: Map<Char, String> = mapOf(
        'a' to "ą", 'c' to "ć", 'e' to "ę", 'l' to "ł", 'n' to "ń", 'o' to "ó", 's' to "ś", 'x' to "ź", 'z' to "ż",
    )

    private fun fn(key: SpecialKey, width: Float = 1f, repeatable: Boolean = false) =
        Key(KeyAction.Special(key), width, repeatable = repeatable, style = KeyStyle.FUNCTION)

    private fun mod(key: SpecialKey, width: Float = 1f) =
        Key(KeyAction.Special(key), width, style = KeyStyle.MODIFIER)

    private fun action(key: SpecialKey, width: Float) =
        Key(KeyAction.Special(key), width, style = KeyStyle.ACTION)

    private fun arrow(key: SpecialKey, width: Float = 1f) =
        Key(KeyAction.Special(key), width, repeatable = true, style = KeyStyle.FUNCTION)

    private fun spacer(width: Float) = Key(KeyAction.Spacer, width, style = KeyStyle.SPACER)
}
