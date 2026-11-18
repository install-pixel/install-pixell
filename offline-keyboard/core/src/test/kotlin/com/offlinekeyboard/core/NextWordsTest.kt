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
