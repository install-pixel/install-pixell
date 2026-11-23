package com.offlinekeyboard.core

import com.offlinekeyboard.core.translation.TranslationGuard
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationGuardTest {
    @Test
    fun `made-up results of short words are dropped`() {
        assertEquals("т", TranslationGuard.accept("т", "Т.Т"))
        assertEquals("т", TranslationGuard.accept("т", "B37"))
        assertEquals("я", TranslationGuard.accept("я", "ja ja ja ja ja ja"))
    }

    @Test
    fun `real translations stay`() {
        assertEquals("ja", TranslationGuard.accept("я", "ja"))
        assertEquals("i", TranslationGuard.accept("і", "i"))
        assertEquals("cześć, jak się masz", TranslationGuard.accept("привіт як справи", "cześć, jak się masz"))
        assertEquals("mam 3 koty", TranslationGuard.accept("маю 3 коти", "mam 3 koty"))
    }

    @Test
    fun `digits that were not there are dropped in long text too`() {
        assertEquals("привіт друже", TranslationGuard.accept("привіт друже", "cześć 37 przyjacielu"))
    }
}
