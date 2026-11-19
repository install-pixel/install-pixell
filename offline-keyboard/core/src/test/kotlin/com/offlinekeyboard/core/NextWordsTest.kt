package com.offlinekeyboard.core

import com.offlinekeyboard.core.dictionary.NextWords
import com.offlinekeyboard.core.dictionary.WordPairs
import com.offlinekeyboard.core.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWordsTest {

    @Test
    fun `the shipped list is word, tab, followers`() {
        val map = NextWords.parse("# comment\nдякую\tза вам\nдобрий\tдень ранок вечір\n")
        assertEquals(listOf("за", "вам"), map["дякую"])
        assertEquals(listOf("день", "ранок", "вечір"), map["добрий"])
    }

    @Test
    fun `own pairs typed twice come first and survive a save`() {
        val pairs = WordPairs()
        pairs.learn(Language.UK, "Дякую", "друже")
        assertTrue(pairs.top(Language.UK, "дякую", 3).isEmpty())
        pairs.learn(Language.UK, "дякую", "друже")
        assertEquals(listOf("друже"), pairs.top(Language.UK, "дякую", 3))
        val back = WordPairs.parse(pairs.serialize())
        assertEquals(listOf("друже"), back.top(Language.UK, "ДЯКУЮ", 3))
        assertEquals(listOf("друже"), NextWords.predict(Language.UK, "дякую", back, 1))
    }
}

class LongContextTest {
    @org.junit.Test
    fun `the word after two words comes first, then the word after one`() {
        val own = com.offlinekeyboard.core.dictionary.WordPairs()
        val uk = com.offlinekeyboard.core.layout.Language.UK
        repeat(2) { own.learn(uk, "тебе", "бачу") }
        repeat(2) { own.learn(uk, "я тебе", "люблю") }
        val words = com.offlinekeyboard.core.dictionary.NextWords.predict(uk, "тебе", own, earlier = "я")
        org.junit.Assert.assertEquals("люблю", words.first())
        org.junit.Assert.assertTrue("бачу" in words)
        // Without the word before, only what follows the last word.
        org.junit.Assert.assertEquals("бачу", com.offlinekeyboard.core.dictionary.NextWords.predict(uk, "тебе", own).first())
    }

    @org.junit.Test
    fun `words of the other alphabet are not learned`() {
        val dict = com.offlinekeyboard.core.dictionary.UserDictionary()
        val pl = com.offlinekeyboard.core.layout.Language.PL
        val uk = com.offlinekeyboard.core.layout.Language.UK
        org.junit.Assert.assertFalse(dict.learn(pl, "привіт", 0))
        org.junit.Assert.assertFalse(dict.learn(uk, "dzień", 0))
        org.junit.Assert.assertTrue(dict.learn(pl, "dzień", 0))
        org.junit.Assert.assertTrue(dict.learn(uk, "м'ясо", 0))
        val restored = com.offlinekeyboard.core.dictionary.UserDictionary.parse("PL\tпривіт\t3\t0\nPL\tdobry\t3\t0\n")
        org.junit.Assert.assertEquals(1, restored.size(pl))
    }
}
