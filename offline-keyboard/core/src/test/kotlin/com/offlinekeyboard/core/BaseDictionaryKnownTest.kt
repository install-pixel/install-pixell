package com.offlinekeyboard.core

import com.offlinekeyboard.core.dictionary.BaseDictionary
import com.offlinekeyboard.core.layout.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseDictionaryKnownTest {

    @Test
    fun `rare correct words are known, typos are not`() {
        for (language in Language.values()) {
            val common = BaseDictionary.words(language)
            assertTrue(BaseDictionary.isKnown(language, common.first().lowercase(language.locale)))
        }
        // Valid forms too rare for the 10k list still count as correct.
        assertTrue(BaseDictionary.isKnown(Language.UK, "книгою"))
        assertFalse(BaseDictionary.isKnown(Language.UK, "пртвіт"))
        assertFalse(BaseDictionary.isKnown(Language.EN, "tganks"))
    }
}
