package com.offlinekeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationComposerTest {

    private fun composer(editor: FakeEditor, knownCursor: Boolean = true, auto: Boolean = true) =
        TranslationComposer({ editor.connection }, { if (knownCursor) editor.cursor else -1 }).also { it.auto = auto }

    /** Every cursor update our own edits produced must be recognised as ours. */
    private fun assertAllOwn(editor: FakeEditor, c: TranslationComposer) {
        for (u in editor.updates) assertTrue("update ${u.toList()} seen as user move", c.isOwnCursorUpdate(u[0], u[1], u[2], u[3]))
        editor.updates.clear()
    }

    private fun typeAuto(editor: FakeEditor, c: TranslationComposer, source: String, translation: String) {
        for (ch in source) {
            c.append(ch.toString())
            assertAllOwn(editor, c)
        }
        assertTrue(c.showLive(c.generation, translation))
        assertAllOwn(editor, c)
    }

    @Test
    fun `auto mode in an app that does not report composing`() {
        val editor = FakeEditor("Hi ", reportsComposing = false)
        val c = composer(editor)
        typeAuto(editor, c, "Привіт", "Cześć")
        assertEquals("Hi Cześć", editor.text.toString())
        // Typing more keeps the old translation on screen until the new one arrives (no flicker).
        c.append(" як справи")
        assertEquals("Hi Cześć", editor.text.toString())
        assertAllOwn(editor, c)
        assertTrue(c.showLive(c.generation, "Cześć, jak się masz"))
        assertAllOwn(editor, c)
        assertEquals("Cześć, jak się masz", c.commitShown())
        assertEquals("Hi Cześć, jak się masz", editor.text.toString())
        assertTrue(c.isEmpty)
    }

    @Test
    fun `auto mode in an app that reports composing`() {
        val editor = FakeEditor("", reportsComposing = true)
        val c = composer(editor, knownCursor = false)
        typeAuto(editor, c, "Дякую", "Dziękuję")
        assertEquals("Dziękuję", c.commitShown())
        assertEquals("Dziękuję", editor.text.toString())
    }

    @Test
    fun `user moving the cursor is detected`() {
        val editor = FakeEditor("abc ", reportsComposing = false)
        val c = composer(editor)
        typeAuto(editor, c, "так", "tak")
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
    fun `stale translation is ignored and leave keeps the typed source`() {
        val editor = FakeEditor("", reportsComposing = false)
        val c = composer(editor)
        c.append("Привіт")
        val oldGeneration = c.generation
        c.append("!")
        assertFalse(c.showLive(oldGeneration, "Cześć"))
        assertEquals("Привіт!", editor.text.toString())
        assertNull(c.leave())
        assertEquals("Привіт!", editor.text.toString())
        assertTrue(c.isEmpty)
    }

    @Test
    fun `leave keeps an up-to-date auto translation`() {
        val editor = FakeEditor("", reportsComposing = false)
        val c = composer(editor)
        typeAuto(editor, c, " так", "tak")
        assertEquals(" tak", c.leave())
        assertEquals(" tak", editor.text.toString())
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
    fun `manual mode replaces the typed segment on request`() {
        val editor = FakeEditor("A ", reportsComposing = false)
        val c = composer(editor, auto = false)
        for (ch in "Добре ") c.append(ch.toString())
        assertEquals("A Добре ", editor.text.toString())
        val (text, gen) = c.beginTranslation()!!
        assertEquals("Добре ", text)
        assertEquals("Dobrze ", c.applyTranslation(gen, "Dobrze"))
        assertEquals("A Dobrze ", editor.text.toString())
    }
}
