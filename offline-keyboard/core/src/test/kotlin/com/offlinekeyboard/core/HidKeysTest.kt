package com.offlinekeyboard.core

import com.offlinekeyboard.core.input.KeyMods
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.LayoutProvider
import com.offlinekeyboard.core.layout.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HidKeysTest {

    private val none = KeyMods(shift = false, ctrl = false, alt = false, meta = false)

    @Test
    fun `letters keep their key position in every language`() {
        assertEquals(HidStroke(0, 0x04), HidKeys.forText("a", Language.EN))
        assertEquals(HidStroke(HidKeys.SHIFT, 0x04), HidKeys.forText("A", Language.EN))
        // Ukrainian ф is on the A key, х on [, ї on ], є on '.
        assertEquals(HidStroke(0, 0x04), HidKeys.forText("ф", Language.UK))
        assertEquals(HidStroke(HidKeys.SHIFT, 0x04), HidKeys.forText("Ф", Language.UK))
        assertEquals(HidStroke(0, 0x2F), HidKeys.forText("х", Language.UK))
        assertEquals(HidStroke(0, 0x30), HidKeys.forText("ї", Language.UK))
        assertEquals(HidStroke(0, 0x34), HidKeys.forText("є", Language.UK))
        // Ukrainian "," is Shift + the / key.
        assertEquals(HidStroke(HidKeys.SHIFT, 0x38), HidKeys.forText(",", Language.UK))
    }

    @Test
    fun `polish letters use altgr and symbols follow the us layout`() {
        assertEquals(HidStroke(HidKeys.RIGHT_ALT, 0x04), HidKeys.forText("ą", Language.PL))
        assertEquals(HidStroke(HidKeys.RIGHT_ALT or HidKeys.SHIFT, 0x1D), HidKeys.forText("Ż", Language.PL))
        assertEquals(HidStroke(HidKeys.SHIFT, 0x1F), HidKeys.forText("@", Language.EN))
        assertEquals(HidStroke(HidKeys.SHIFT, 0x38), HidKeys.forText("?", Language.PL))
    }

    @Test
    fun `characters a layout cannot type are refused`() {
        assertNull(HidKeys.forText("ы", Language.UK))
        assertNull(HidKeys.forText("@", Language.UK))
        assertNull(HidKeys.forText("€", Language.EN))
        assertNull(HidKeys.forText("ab", Language.EN))
    }

    @Test
    fun `computer keys send position plus modifiers`() {
        val layout = LayoutProvider.layout(KeyboardMode.COMPUTER, Language.UK, Layer.LETTERS)
        val es = layout.allKeys().first { (it.action as? KeyAction.Text)?.text == "с" }
        val ctrl = none.copy(ctrl = true)
        assertEquals(HidStroke(HidKeys.CTRL, 0x06), HidKeys.forKey(es, "с", ctrl, Language.UK))
        val space = layout.allKeys().first { (it.action as? KeyAction.Special)?.key == SpecialKey.SPACE }
        assertEquals(HidStroke(HidKeys.GUI, 0x2C), HidKeys.forKey(space, null, none.copy(meta = true), Language.UK))
        val altA = layout.allKeys().first { (it.action as? KeyAction.Text)?.text == "ф" }
        assertEquals(HidStroke(HidKeys.RIGHT_ALT, 0x04), HidKeys.forKey(altA, null, none.copy(alt = true), Language.PL))
    }

    @Test
    fun `special keys and edit actions`() {
        assertEquals(HidStroke(0, 0x2A), HidKeys.special(SpecialKey.BACKSPACE))
        assertEquals(HidStroke(0, 0x45), HidKeys.special(SpecialKey.F12))
        assertEquals(HidStroke(HidKeys.CTRL, 0x19), HidKeys.special(SpecialKey.PASTE))
        assertNull(HidKeys.special(SpecialKey.SHIFT))
    }
}
