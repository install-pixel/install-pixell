package com.offlinekeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationComposerTest {

    private fun composer(editor: FakeEditor, knownCursor: Boolean = true) =
        TranslationComposer({ editor.connection }, { if (knownCursor) editor.cursor else -1 })

    /** Every cursor update our own edits produced must be recognised as ours. */
    private fun assertAllOwn(editor: FakeEditor, c: TranslationComposer) {
        for (u in editor.updates) assertTrue("update ${u.toList()} seen as user move", c.isOwnCursorUpdate(u[0], u[1], u[2], u[3]))
        editor.updates.clear()
    }

    private fun type(editor: FakeEditor, c: TranslationComposer, source: String) {
        for (ch in source) {
            c.append(ch.toString())
            assertAllOwn(editor, c)
        }
    }

    @Test
    fun `typed source stays visible and is replaced once by the translation`() {
        for (reports in listOf(false, true)) {
            val editor = FakeEditor("Hi ", reportsComposing = reports)
            val c = composer(editor, knownCursor = !reports)
            type(editor, c, "Привіт, як справи")
            assertEquals("Hi Привіт, як справи", editor.text.toString())
            val (text, gen) = c.beginTranslation()!!
            assertEquals("Cześć, co słychać", c.applyTranslation(gen, "Cześć, co słychać"))
            assertAllOwn(editor, c)
            assertEquals("Hi Cześć, co słychać", editor.text.toString())
            assertEquals("Привіт, як справи", text)
            assertTrue(c.isEmpty)
        }
    }

    @Test
    fun `user moving the cursor is detected`() {
        val editor = FakeEditor("abc ", reportsComposing = false)
        val c = composer(editor)
        type(editor, c, "так")
        editor.moveCursor(0)
        val u = editor.updates.last()
        assertFalse(c.isOwnCursorUpdate(u[0], u[1], u[2], u[3]))
        assertFalse("a selection is never ours", c.isOwnCursorUpdate(1, 3, -1, -1))
    }

    @Test
    fun `unknown cursor position never interrupts typing`() {
        val editor = FakeEditor("x", reportsComposing = false)
        val c = composer(editor, knownCursor = false)
        c.append("а")
        assertTrue(c.isOwnCursorUpdate(57, 57, -1, -1))
    }

    @Test
    fun `a translation for older text is ignored`() {
        val editor = FakeEditor("", reportsComposing = false)
        val c = composer(editor)
        type(editor, c, "Привіт")
        val (_, gen) = c.beginTranslation()!!
        c.append(" друже")
        assertNull(c.applyTranslation(gen, "Cześć"))
        assertEquals("Привіт друже", editor.text.toString())
    }

    @Test
    fun `leave keeps the typed source`() {
        val editor = FakeEditor("", reportsComposing = false)
        val c = composer(editor)
        type(editor, c, "так")
        c.leave()
        assertEquals("так", editor.text.toString())
        assertTrue(c.isEmpty)
    }

    @Test
    fun `backspace to empty clears the segment`() {
        val editor = FakeEditor("ok", reportsComposing = false)
        val c = composer(editor)
        c.append("т")
        assertTrue(c.backspace())
        assertEquals("ok", editor.text.toString())
        assertFalse(c.backspace())
    }

    @Test
    fun `spaces around the segment are kept`() {
        val editor = FakeEditor("A", reportsComposing = false)
        val c = composer(editor)
        type(editor, c, " Добре ")
        val (_, gen) = c.beginTranslation()!!
        assertEquals(" Dobrze ", c.applyTranslation(gen, "Dobrze"))
        assertEquals("A Dobrze ", editor.text.toString())
    }
}
