package com.offlinekeyboard.core

import com.offlinekeyboard.core.dictionary.Apostrophes
import com.offlinekeyboard.core.dictionary.AutoCorrect
import com.offlinekeyboard.core.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApostrophesTest {

    @Test
    fun `a missing apostrophe is put back where a known word has one`() {
        assertEquals("м'ясо", Apostrophes.restore(Language.UK, "мясо"))
        assertEquals("п'ять", Apostrophes.restore(Language.UK, "пять"))
        assertEquals("об'єкт", Apostrophes.restore(Language.UK, "обєкт"))
        assertEquals("з'явився", Apostrophes.restore(Language.UK, "зявився"))
        assertEquals("пам'ятаю", Apostrophes.restore(Language.UK, "памятаю"))
        assertEquals("здоров'ям", Apostrophes.restore(Language.UK, "здоровям"))
        assertEquals("комп'ютер", Apostrophes.restore(Language.UK, "компютер"))
    }

    @Test
    fun `words without one, other languages and typed apostrophes stay`() {
        assertNull(Apostrophes.restore(Language.UK, "бюро"))
        assertNull(Apostrophes.restore(Language.UK, "буряк"))
        assertNull(Apostrophes.restore(Language.UK, "свято"))
        assertNull(Apostrophes.restore(Language.PL, "mjaso"))
        assertNull(Apostrophes.restore(Language.UK, "м'ясо"))
        assertTrue(Apostrophes.isKnown(Language.UK, "м'ясо"))
        assertTrue(Apostrophes.isKnown(Language.UK, "мʼясо"))
        assertFalse(Apostrophes.isKnown(Language.UK, "б'юро"))
    }

    @Test
    fun `autocorrect puts the apostrophe back and keeps the capital`() {
        val known = setOf("масло", "мисо")
        assertEquals("М'ясо", AutoCorrect.correction(Language.UK, "Мясо", { it in known }, known.asSequence()))
        // A word typed with its apostrophe is left alone, not "corrected" to a near word.
        assertNull(AutoCorrect.correction(Language.UK, "м'ясо", { it in known }, known.asSequence()))
    }
}
