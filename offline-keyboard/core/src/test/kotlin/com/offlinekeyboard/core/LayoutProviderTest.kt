package com.offlinekeyboard.core

import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.LayoutProvider
import com.offlinekeyboard.core.layout.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutProviderTest {

    private fun texts(mode: KeyboardMode, language: Language, layer: Layer = Layer.LETTERS): Set<String> {
        val keys = LayoutProvider.layout(mode, language, layer).allKeys()
        return keys.mapNotNull { (it.action as? KeyAction.Text)?.text }.toSet() + keys.flatMap { it.alternatives }
    }

    private fun specials(mode: KeyboardMode, language: Language, layer: Layer): Set<SpecialKey> =
        LayoutProvider.layout(mode, language, layer).allKeys().mapNotNull { (it.action as? KeyAction.Special)?.key }.toSet()

    @Test
    fun `computer rows all have PC width`() {
        for (language in Language.entries) for (layer in listOf(Layer.LETTERS, Layer.SYMBOLS, Layer.FUNCTION)) {
            val layout = LayoutProvider.layout(KeyboardMode.COMPUTER, language, layer)
            layout.rows.forEachIndexed { i, row ->
                assertEquals("$language $layer row $i", 15.5f, row.sumOf { it.width.toDouble() }.toFloat(), 0.001f)
            }
        }
    }

    @Test
    fun `every layer of every mode can reach letters, delete and enter`() {
        for (mode in KeyboardMode.entries) for (language in Language.entries) {
            val layers = if (mode == KeyboardMode.COMPUTER) listOf(Layer.LETTERS, Layer.SYMBOLS, Layer.FUNCTION)
            else listOf(Layer.LETTERS, Layer.SYMBOLS, Layer.SYMBOLS_MORE)
            for (layer in layers) {
                val s = specials(mode, language, layer)
                assertTrue("$mode $language $layer backspace", SpecialKey.BACKSPACE in s)
                assertTrue("$mode $language $layer enter", SpecialKey.ENTER in s)
                assertTrue("$mode $language $layer language", SpecialKey.LANGUAGE in s)
            }
        }
    }

    @Test
    fun `ukrainian layouts contain every ukrainian letter`() {
        val alphabet = "абвгґдеєжзиіїйклмнопрстуфхцчшщьюя"
        for (mode in KeyboardMode.entries) {
            val t = texts(mode, Language.UK)
            for (c in alphabet) assertTrue("$mode missing $c", c.toString() in t)
            assertTrue("$mode apostrophe", "'" in t)
        }
    }

    @Test
    fun `polish layouts reach every polish diacritic`() {
        for (mode in KeyboardMode.entries) {
            val t = texts(mode, Language.PL)
            for (c in "ąćęłńóśźż") assertTrue("$mode missing $c", c.toString() in t)
        }
    }

    @Test
    fun `english layouts contain a to z`() {
        for (mode in KeyboardMode.entries) {
            val t = texts(mode, Language.EN)
            for (c in 'a'..'z') assertTrue("$mode missing $c", c.toString() in t)
        }
    }

    @Test
    fun `computer mode has all PC keys across its layers`() {
        val all = Layer.entries.filter { it != Layer.SYMBOLS_MORE }
            .flatMap { specials(KeyboardMode.COMPUTER, Language.EN, it) }.toSet()
        val required = listOf(
            SpecialKey.SHIFT, SpecialKey.CAPS_LOCK, SpecialKey.BACKSPACE, SpecialKey.DELETE, SpecialKey.ENTER,
            SpecialKey.SPACE, SpecialKey.TAB, SpecialKey.ESC, SpecialKey.LEFT, SpecialKey.RIGHT, SpecialKey.UP,
            SpecialKey.DOWN, SpecialKey.HOME, SpecialKey.END, SpecialKey.PAGE_UP, SpecialKey.PAGE_DOWN,
            SpecialKey.INSERT, SpecialKey.CTRL, SpecialKey.ALT, SpecialKey.META,
            SpecialKey.F1, SpecialKey.F12, SpecialKey.CUT, SpecialKey.COPY, SpecialKey.PASTE, SpecialKey.SELECT_ALL,
            SpecialKey.MODE, SpecialKey.LAYER_FUNCTION, SpecialKey.LAYER_SYMBOLS,
        )
        for (k in required) assertTrue("missing $k", k in all)
    }

    @Test
    fun `digits row is reachable in both modes`() {
        for (mode in KeyboardMode.entries) {
            val t = texts(mode, Language.UK) + texts(mode, Language.UK, Layer.SYMBOLS)
            for (d in '0'..'9') assertTrue("$mode missing $d", d.toString() in t)
        }
    }

    @Test
    fun `cursor row is optional in mobile mode`() {
        val plain = LayoutProvider.layout(KeyboardMode.MOBILE, Language.EN, Layer.LETTERS, mobileCursorRow = false)
        val withRow = LayoutProvider.layout(KeyboardMode.MOBILE, Language.EN, Layer.LETTERS, mobileCursorRow = true)
        assertEquals(plain.rows.size + 1, withRow.rows.size)
        assertFalse(plain.allKeys().any { (it.action as? KeyAction.Special)?.key == SpecialKey.LEFT })
        assertEquals(plain.maxRowUnits, withRow.rows.first().sumOf { it.width.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `ukrainian pc keys keep latin physical positions for shortcuts`() {
        val keys = LayoutProvider.layout(KeyboardMode.COMPUTER, Language.UK, Layer.LETTERS).allKeys()
        val es = keys.first { (it.action as? KeyAction.Text)?.text == "с" }
        assertEquals('c', es.physical)
        val ya = keys.first { (it.action as? KeyAction.Text)?.text == "я" }
        assertEquals('z', ya.physical)
    }
}
