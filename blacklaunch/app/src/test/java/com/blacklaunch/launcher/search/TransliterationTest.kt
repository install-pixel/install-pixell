package com.blacklaunch.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransliterationTest {
    @Test fun brandStyleGhe() = assertEquals("telegram", Transliteration.toLatin("телеграм"))

    @Test fun multiLetterSounds() = assertEquals("shchastya", Transliteration.toLatin("щастя"))

    @Test fun keepsLatinAndDigits() = assertEquals("privat24", Transliteration.toLatin("privat24"))

    @Test fun detectsCyrillic() {
        assertTrue(Transliteration.hasCyrillic("дія"))
        assertFalse(Transliteration.hasCyrillic("diia"))
    }
}
