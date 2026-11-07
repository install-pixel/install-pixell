package com.offlinekeyboard.core

import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.LayoutProvider
import com.offlinekeyboard.core.layout.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumpadTest {

    private fun texts(layer: Layer, language: Language = Language.UK, phone: Boolean = false) =
        LayoutProvider.layout(KeyboardMode.MOBILE, language, layer, phonePad = phone).allKeys()
            .mapNotNull { (it.action as? KeyAction.Text)?.text }

    @Test
    fun `the number pad has every digit, as many rows as the letters, the decimal sign of the language`() {
        val pad = LayoutProvider.layout(KeyboardMode.MOBILE, Language.UK, Layer.NUMPAD)
        val letters = LayoutProvider.layout(KeyboardMode.MOBILE, Language.UK, Layer.LETTERS)
        assertEquals(letters.rows.size, pad.rows.size)
        for (d in 0..9) assertTrue("$d", d.toString() in texts(Layer.NUMPAD))
        assertTrue("," in texts(Layer.NUMPAD, Language.UK))
        assertTrue("." in texts(Layer.NUMPAD, Language.EN))
        assertTrue("#" in texts(Layer.NUMPAD, phone = true))
        // Every row as wide: the pad is a clean grid.
        val widths = pad.rows.map { row -> row.sumOf { it.width.toDouble() } }
        assertTrue(widths.toString(), widths.all { kotlin.math.abs(it - widths[0]) < 0.01 })
    }

    @Test
    fun `number fields open on the pad, its keys lead to letters and symbols and back`() {
        val c = KeyboardController(KeyboardMode.MOBILE, Language.UK, Language.entries)
        c.onStartField(numeric = true, phone = true)
        assertEquals(Layer.NUMPAD, c.layer)
        assertTrue(c.layout(false).allKeys().any { (it.action as? KeyAction.Text)?.text == "#" })
        fun press(key: SpecialKey) = c.onKey(c.layout(false).allKeys().first { (it.action as? KeyAction.Special)?.key == key })
        press(SpecialKey.LAYER_SYMBOLS)
        assertEquals(Layer.SYMBOLS, c.layer)
        press(SpecialKey.LAYER_NUMPAD)
        assertEquals(Layer.NUMPAD, c.layer)
        press(SpecialKey.LAYER_LETTERS)
        assertEquals(Layer.LETTERS, c.layer)
        c.onStartField(numeric = false)
        assertEquals(Layer.LETTERS, c.layer)
    }
}
