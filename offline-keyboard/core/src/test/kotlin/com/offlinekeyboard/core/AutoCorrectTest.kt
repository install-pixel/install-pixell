package com.offlinekeyboard.core

import com.offlinekeyboard.core.dictionary.AutoCorrect
import com.offlinekeyboard.core.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoCorrectTest {

    private fun fix(language: Language, typed: String, words: List<String>, rejected: Set<String> = emptySet()) =
        AutoCorrect.correction(language, typed, { it in words }, words.asSequence(), rejected)

    @Test
    fun `a neighbouring key or swapped letters are corrected`() {
        assertEquals("привіт", fix(Language.UK, "пртвіт", listOf("привіт")))
        assertEquals("Привіт", fix(Language.UK, "Пртвіт", listOf("привіт")))
        assertEquals("dzień", fix(Language.PL, "dizeń", listOf("dzień")))
        assertEquals("thanks", fix(Language.EN, "tganks", listOf("thanks")))
        assertEquals("HELLO", fix(Language.EN, "HWLLO", listOf("hello")))
        // One letter missing or doubled inside the word.
        assertEquals("привіт", fix(Language.UK, "првіт", listOf("привіт")))
        assertEquals("привіт", fix(Language.UK, "приивіт", listOf("привіт")))
        assertEquals("tomorrow", fix(Language.EN, "tomorow", listOf("tomorrow")))
        assertEquals("dziękuję", fix(Language.PL, "dzękuję", listOf("dziękuję")))
    }

    @Test
    fun `word forms, far keys, short and known words are left alone`() {
        // Endings change the form in Ukrainian and Polish.
        assertNull(fix(Language.UK, "мами", listOf("мама")))
        assertNull(fix(Language.PL, "domy", listOf("domu")))
        // A missing or extra letter in the ending is a different form, not a slip.
        assertNull(fix(Language.UK, "привт", listOf("привіт")))
        assertNull(fix(Language.UK, "книг", listOf("книга")))
        assertNull(fix(Language.PL, "domów", listOf("domu")))
        // "q" and "m" are far apart.
        assertNull(fix(Language.EN, "mhanks", listOf("thanks")))
        assertNull(fix(Language.EN, "thw", listOf("the")))
        assertNull(fix(Language.EN, "hello", listOf("hello", "hallo")))
        assertNull(fix(Language.EN, "iPhome", listOf("iphone")))
        assertNull(fix(Language.EN, "tganks", listOf("thanks"), rejected = setOf("tganks")))
    }
}
