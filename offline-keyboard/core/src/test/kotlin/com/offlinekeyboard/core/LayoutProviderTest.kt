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
                if (mode == KeyboardMode.MOBILE) assertTrue("$mode $language $layer language", SpecialKey.LANGUAGE in s)
                else assertTrue("PC layouts have no language key", SpecialKey.LANGUAGE !in s && SpecialKey.MODE !in s)
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
            SpecialKey.LAYER_FUNCTION, SpecialKey.LAYER_SYMBOLS,
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

class LayoutDuplicatesTest {
    @org.junit.Test
    fun `no character appears both as a key and as a long-press option`() {
        for (mode in com.offlinekeyboard.core.layout.KeyboardMode.entries)
            for (language in com.offlinekeyboard.core.layout.Language.entries)
                for (layer in com.offlinekeyboard.core.layout.Layer.entries) {
                    val keys = com.offlinekeyboard.core.layout.LayoutProvider.layout(mode, language, layer).allKeys()
                    val onKeys = keys.mapNotNull { (it.action as? com.offlinekeyboard.core.layout.KeyAction.Text)?.text }.toSet()
                    val dup = keys.flatMap { it.alternatives }.filter { it in onKeys }
                    org.junit.Assert.assertTrue("$mode $language $layer duplicates $dup", dup.isEmpty())
                }
    }

    @org.junit.Test
    fun `each ukrainian special letter is reachable exactly once in mobile mode`() {
        val keys = com.offlinekeyboard.core.layout.LayoutProvider.layout(
            com.offlinekeyboard.core.layout.KeyboardMode.MOBILE, com.offlinekeyboard.core.layout.Language.UK,
            com.offlinekeyboard.core.layout.Layer.LETTERS,
        ).allKeys()
        for (c in listOf("ї", "є", "ґ")) {
            val count = keys.count { (it.action as? com.offlinekeyboard.core.layout.KeyAction.Text)?.text == c } +
                keys.count { c in it.alternatives }
            org.junit.Assert.assertEquals(c, 1, count)
        }
    }

    @org.junit.Test
    fun `direction is detected from the script`() {
        org.junit.Assert.assertEquals(com.offlinekeyboard.core.translation.TranslationDirection.UK_TO_PL, com.offlinekeyboard.core.translation.TranslationDirection.detect("Привіт, як справи?"))
        org.junit.Assert.assertEquals(com.offlinekeyboard.core.translation.TranslationDirection.PL_TO_UK, com.offlinekeyboard.core.translation.TranslationDirection.detect("Cześć, co słychać?"))
        org.junit.Assert.assertEquals(com.offlinekeyboard.core.translation.TranslationDirection.UK_TO_PL, com.offlinekeyboard.core.translation.TranslationDirection.detect("Код ABC-123 у системі WMS"))
        org.junit.Assert.assertEquals(null, com.offlinekeyboard.core.translation.TranslationDirection.detect("12:30 — 45821"))
    }
}
