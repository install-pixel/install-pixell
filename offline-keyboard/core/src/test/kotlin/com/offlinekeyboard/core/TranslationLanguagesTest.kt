package com.offlinekeyboard.core

import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.translation.Phrasebook
import com.offlinekeyboard.core.translation.TranslationDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationLanguagesTest {

    @Test
    fun `all six directions exist`() {
        for (source in Language.entries) for (target in Language.entries) {
            if (source != target) assertEquals("$source-$target", source, TranslationDirection.of(source, target)!!.source)
        }
        assertEquals("en-pl", TranslationDirection.EN_TO_PL.slug)
    }

    @Test
    fun `language of a text`() {
        assertEquals(Language.UK, TranslationDirection.detectLanguage("Привіт, як справи?"))
        assertEquals(Language.PL, TranslationDirection.detectLanguage("Cześć, co słychać?"))
        assertEquals(Language.PL, TranslationDirection.detectLanguage("To nie jest problem"))
        assertEquals(Language.EN, TranslationDirection.detectLanguage("How are you today?"))
        assertEquals(Language.EN, TranslationDirection.detectLanguage("Meeting at 5"))
        assertNull(TranslationDirection.detectLanguage("12:30"))
    }

    @Test
    fun `direction follows the writing language`() {
        // Writing Polish while translating Укр→Pol turns into Pol→Укр.
        assertEquals(TranslationDirection.PL_TO_UK, TranslationDirection.forSource(Language.PL, Language.PL, Language.UK))
        assertEquals(TranslationDirection.EN_TO_PL, TranslationDirection.forSource(Language.EN, Language.PL, Language.UK))
        assertEquals(TranslationDirection.EN_TO_UK, TranslationDirection.detect("Thank you very much", Language.UK, Language.PL))
        assertEquals(TranslationDirection.UK_TO_EN, TranslationDirection.detect("Дякую", Language.EN, Language.PL))
    }

    @Test
    fun `everyday phrases have fixed translations`() {
        assertEquals("Jak się masz?", Phrasebook.lookup("Як справи?", TranslationDirection.UK_TO_PL))
        assertEquals("How are you?", Phrasebook.lookup("як справи?", TranslationDirection.UK_TO_EN).let { it!!.replaceFirstChar { c -> c.uppercase() } })
        assertEquals("як справи", Phrasebook.lookup("Jak się masz", TranslationDirection.PL_TO_UK)!!.lowercase())
        assertEquals("Дякую!", Phrasebook.lookup("Thank you!", TranslationDirection.EN_TO_UK))
        assertEquals("I don't understand.", Phrasebook.lookup("Nie rozumiem.", TranslationDirection.PL_TO_EN))
        assertEquals("Я з України", Phrasebook.lookup("I'm from Ukraine", TranslationDirection.EN_TO_UK))
        assertEquals("I'm fine", Phrasebook.lookup("у мене все добре", TranslationDirection.UK_TO_EN))
        assertNull(Phrasebook.lookup("Як справи на складі?", TranslationDirection.UK_TO_PL))
    }
}
