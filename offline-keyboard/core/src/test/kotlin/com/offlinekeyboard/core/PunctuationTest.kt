package com.offlinekeyboard.core

import com.offlinekeyboard.core.input.Punctuation
import com.offlinekeyboard.core.input.Punctuation.Edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PunctuationTest {

    private fun typed(before: String, c: String): String {
        val edit = Punctuation.adjust(before, c) ?: return before + c
        return before.dropLast(edit.delete) + edit.insert
    }

    @Test
    fun `no space before closing marks, one after`() {
        assertEquals("Привіт, ", typed("Привіт ", ","))
        assertEquals("так. ", typed("так ", "."))
        assertEquals("Чому? ", typed("Чому  ", "?"))
        assertEquals("(тут) ", typed("(тут ", ")"))
        assertEquals("«так» ", typed("«так ", "»"))
        assertEquals("50%", typed("50 ", "%"))
        assertEquals(Edit(1, ", ", " ,"), Punctuation.adjust("слово ", ","))
    }

    @Test
    fun `spaces stay where they belong`() {
        assertEquals("слово,", typed("слово", ","))
        assertEquals(" ,", typed(" ", ","))
        assertEquals("а - ,", typed("а - ", ","))
        assertEquals("( ,", typed("( ", ","))
        assertEquals("\n,", typed("\n", ","))
    }

    @Test
    fun `a space after a comma-like mark before the next word`() {
        assertEquals("так, а", typed("так,", "а"))
        assertEquals("Ok! W", typed("Ok!", "W"))
        assertEquals("note: b", typed("note:", "b"))
        assertEquals("(так), н", typed("(так),", "н"))
    }

    @Test
    fun `numbers, addresses and dots are left alone`() {
        assertEquals("1,5", typed("1,", "5"))
        assertEquals("10:a", typed("10:", "a"))
        assertEquals("file.t", typed("file.", "t"))
        assertEquals("e.g", typed("e.", "g"))
        assertEquals("http:/", typed("http:", "/"))
        assertEquals("https:a", typed("https:", "a"))
        assertEquals("mail@x,y", typed("mail@x,", "y"))
        assertEquals("www.a,b", typed("www.a,", "b"))
    }

    @Test
    fun `no space right after an opening mark`() {
        assertEquals("(", typed("(", " "))
        assertEquals("«", typed("«", " "))
        assertEquals("так  ", typed("так ", " "))
    }

    @Test
    fun `multi-character input is never changed`() {
        assertNull(Punctuation.adjust("слово ", ", "))
        assertNull(Punctuation.adjust("слово ", ""))
    }
}
