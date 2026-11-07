package com.offlinekeyboard.core

import com.offlinekeyboard.core.dictionary.UserDictionary
import com.offlinekeyboard.core.dictionary.WordListFile
import com.offlinekeyboard.core.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordListFileTest {

    @Test
    fun `plain text goes to the chosen language, one word per line`() {
        val words = WordListFile.parse("﻿привіт\n\n# comment\n  машина \n", Language.UK)
        assertEquals(mapOf(Language.UK to listOf("привіт", "машина")), words)
    }

    @Test
    fun `JSON has a list per language`() {
        val words = WordListFile.parse("""{"uk": ["слово", "п'ять"], "PL": ["słowo"], "xx": ["a"], "en": []}""", Language.EN)
        assertEquals(listOf("слово", "п'ять"), words[Language.UK])
        assertEquals(listOf("słowo"), words[Language.PL])
        assertEquals(emptyList<String>(), words[Language.EN])
    }

    @Test
    fun `export and import keep the words, too short ones are skipped`() {
        val dict = UserDictionary()
        val result = WordListFile.import(dict, mapOf(Language.UK to listOf("привіт", "так"), Language.PL to listOf("dzień")), 1L)
        assertEquals(WordListFile.Result(added = 2, skipped = 1), result)
        val again = UserDictionary()
        WordListFile.import(again, WordListFile.parse(WordListFile.toJson(dict), Language.EN), 2L)
        assertTrue(again.contains(Language.UK, "привіт"))
        assertTrue(again.contains(Language.PL, "dzień"))
    }

    @Test
    fun `a backup replaces the whole dictionary`() {
        val dict = UserDictionary()
        dict.learn(Language.UK, "старе", 1L)
        val backup = UserDictionary().apply { learn(Language.PL, "nowe słowo".split(" ")[0], 1L) }.serialize()
        dict.replaceWith(backup)
        assertTrue(!dict.contains(Language.UK, "старе"))
        assertTrue(dict.contains(Language.PL, "nowe"))
    }
}
