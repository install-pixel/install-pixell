package com.offlinekeyboard.core

import com.offlinekeyboard.core.input.KeyMods
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.LayoutProvider
import com.offlinekeyboard.core.layout.SpecialKey

/** One USB/Bluetooth HID key press: modifier bits and a key usage id (0 = modifiers only). */
data class HidStroke(val modifiers: Int, val usage: Int)

/**
 * Turns keyboard keys into HID key presses for the Bluetooth keyboard mode. Like a real keyboard, it sends
 * key *positions*: the computer turns them into letters with its own input language, so that language must
 * match the one shown on the phone (Win+Space switches both).
 */
object HidKeys {
    const val CTRL = 0x01
    const val SHIFT = 0x02
    const val ALT = 0x04
    const val GUI = 0x08
    const val RIGHT_ALT = 0x40

    private const val US_KEYS = "abcdefghijklmnopqrstuvwxyz1234567890"
    private const val US_SHIFTED = "!@#\$%^&*()"

    /** Usage id of a character printed on a US keyboard (unshifted), or null. */
    fun usageOf(c: Char): Int? {
        val i = US_KEYS.indexOf(c.lowercaseChar())
        if (i >= 0) return 0x04 + i
        return when (c) {
            '\n' -> 0x28
            '\t' -> 0x2B
            ' ' -> 0x2C
            '-' -> 0x2D
            '=' -> 0x2E
            '[' -> 0x2F
            ']' -> 0x30
            '\\' -> 0x31
            ';' -> 0x33
            '\'' -> 0x34
            '`' -> 0x35
            ',' -> 0x36
            '.' -> 0x37
            '/' -> 0x38
            else -> null
        }
    }

    /** A character of the US layout, shifted ones included ("A", "@", "?" ...). */
    fun usStroke(c: Char): HidStroke? {
        usageOf(c)?.let { return HidStroke(if (c.isUpperCase()) SHIFT else 0, it) }
        US_SHIFTED.indexOf(c).takeIf { it >= 0 }?.let { return HidStroke(SHIFT, usageOf(US_KEYS[26 + it])!!) }
        val base = "_+{}|:\"~<>?".indexOf(c).takeIf { it >= 0 }?.let { "-=[]\\;'`,./"[it] } ?: return null
        return HidStroke(SHIFT, usageOf(base)!!)
    }

    fun special(key: SpecialKey): HidStroke? = when (key) {
        SpecialKey.ENTER -> HidStroke(0, 0x28)
        SpecialKey.ESC -> HidStroke(0, 0x29)
        SpecialKey.BACKSPACE -> HidStroke(0, 0x2A)
        SpecialKey.TAB -> HidStroke(0, 0x2B)
        SpecialKey.SPACE -> HidStroke(0, 0x2C)
        SpecialKey.CAPS_LOCK -> HidStroke(0, 0x39)
        SpecialKey.F1, SpecialKey.F2, SpecialKey.F3, SpecialKey.F4, SpecialKey.F5, SpecialKey.F6,
        SpecialKey.F7, SpecialKey.F8, SpecialKey.F9, SpecialKey.F10, SpecialKey.F11, SpecialKey.F12 ->
            HidStroke(0, 0x3A + (key.ordinal - SpecialKey.F1.ordinal))
        SpecialKey.INSERT -> HidStroke(0, 0x49)
        SpecialKey.HOME -> HidStroke(0, 0x4A)
        SpecialKey.PAGE_UP -> HidStroke(0, 0x4B)
        SpecialKey.DELETE -> HidStroke(0, 0x4C)
        SpecialKey.END -> HidStroke(0, 0x4D)
        SpecialKey.PAGE_DOWN -> HidStroke(0, 0x4E)
        SpecialKey.RIGHT -> HidStroke(0, 0x4F)
        SpecialKey.LEFT -> HidStroke(0, 0x50)
        SpecialKey.DOWN -> HidStroke(0, 0x51)
        SpecialKey.UP -> HidStroke(0, 0x52)
        SpecialKey.CUT -> HidStroke(CTRL, usageOf('x')!!)
        SpecialKey.COPY -> HidStroke(CTRL, usageOf('c')!!)
        SpecialKey.PASTE -> HidStroke(CTRL, usageOf('v')!!)
        SpecialKey.SELECT_ALL -> HidStroke(CTRL, usageOf('a')!!)
        SpecialKey.UNDO -> HidStroke(CTRL, usageOf('z')!!)
        SpecialKey.REDO -> HidStroke(CTRL, usageOf('y')!!)
        else -> null
    }

    // Polish "programmer" layout: AltGr + base letter.
    private const val POLISH = "ąćęłńóśźż"
    private const val POLISH_BASE = "acelnosxz"

    /**
     * Key press that types [text] (one character) on a computer set to [language], or null if that layout
     * has no key for it (then the phone says so instead of sending a wrong character).
     */
    fun forText(text: String, language: Language): HidStroke? {
        if (text.length != 1) return null
        val c = text[0]
        if (c == ' ' || c == '\n' || c == '\t') return HidStroke(0, usageOf(c)!!)
        layoutStrokes(language)[text]?.let { return it }
        if (language == Language.PL) {
            val i = POLISH.indexOf(c.lowercaseChar())
            if (i >= 0) return HidStroke(RIGHT_ALT or (if (c.isUpperCase()) SHIFT else 0), usageOf(POLISH_BASE[i])!!)
        }
        // Latin layouts: anything a US keyboard can type (Polish programmer layout is US-based).
        return if (language != Language.UK) usStroke(c) else null
    }

    private val strokeCache = HashMap<Language, Map<String, HidStroke>>()

    /** Every character of the language's computer layout and the key position (plus Shift) that types it. */
    private fun layoutStrokes(language: Language): Map<String, HidStroke> = synchronized(strokeCache) {
        strokeCache.getOrPut(language) {
            val map = HashMap<String, HidStroke>()
            for (key in LayoutProvider.layout(KeyboardMode.COMPUTER, language, Layer.LETTERS).allKeys()) {
                val action = key.action as? KeyAction.Text ?: continue
                val usage = key.physical?.let(::usageOf) ?: continue
                map.putIfAbsent(action.text, HidStroke(0, usage))
                action.shifted?.let { map.putIfAbsent(it, HidStroke(SHIFT, usage)) }
                if (action.text.length == 1 && action.text[0].isLetter()) {
                    map.putIfAbsent(action.text.uppercase(language.locale), HidStroke(SHIFT, usage))
                }
            }
            map
        }
    }

    fun modifierBits(mods: KeyMods, language: Language): Int {
        var bits = 0
        if (mods.ctrl) bits = bits or CTRL
        if (mods.shift) bits = bits or SHIFT
        // On Polish computers Alt+letter means AltGr+letter (ą, ś ...), as on the phone.
        if (mods.alt) bits = bits or if (language == Language.PL && !mods.ctrl && !mods.meta) RIGHT_ALT else ALT
        if (mods.meta) bits = bits or GUI
        return bits
    }

    /**
     * The press for [key] with the phone's modifier state. Letters keep their key position (so Ctrl+С on the
     * Ukrainian layout is Ctrl+C); [output] is what the key would type on the phone, used when the key has
     * no fixed position (mobile layout, symbols).
     */
    fun forKey(key: Key, output: String?, mods: KeyMods, language: Language): HidStroke? {
        val modBits = modifierBits(mods, language)
        return when (val action = key.action) {
            is KeyAction.Special -> special(action.key)?.let { HidStroke(it.modifiers or modBits, it.usage) }
            is KeyAction.Text -> {
                val physical = key.physical?.let(::usageOf)
                when {
                    physical != null -> HidStroke(modBits, physical)
                    output != null -> forText(output, language)?.let { s ->
                        // Shift is already part of the character; keep Ctrl/Alt/Win.
                        HidStroke(s.modifiers or (modBits and SHIFT.inv()), s.usage)
                    }
                    else -> null
                }
            }
            KeyAction.Spacer -> null
        }
    }
}
