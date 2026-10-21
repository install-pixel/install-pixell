package com.offlinekeyboard.core

import com.offlinekeyboard.core.translation.Glossary
import com.offlinekeyboard.core.translation.TranslationDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GlossaryTest {

    private val glossary = Glossary.parse(
        """
        # українська | polski | English
        накладна / накладн* | list przewozowy / list* przewozow* | delivery note
        палета / палет* | paleta / palet* | pallet / pallets
        як справи на складі | co słychać w magazynie |
        broken line without separators
        """.trimIndent(),
    )

    @Test
    fun `terms in any form get the target term`() {
        assertEquals(
            "Принеси list przewozowy і дві paleta.",
            glossary.prepare("Принеси накладну і дві палети.", TranslationDirection.UK_TO_PL),
        )
        assertEquals("Paleta стоїть тут", glossary.prepare("Палета стоїть тут", TranslationDirection.UK_TO_PL))
        assertEquals("Gdzie jest pallet?", glossary.prepare("Gdzie jest paleta?", TranslationDirection.PL_TO_EN))
        assertEquals("Wydrukuj накладна", glossary.prepare("Wydrukuj listy przewozowe", TranslationDirection.PL_TO_UK))
        // Only whole words: "палетка" is not "палета" with an ending here, but "палет*" matches it — prefix rules are
        // the user's choice; "сопалета" never matches.
        assertEquals("сопалета", glossary.prepare("сопалета", TranslationDirection.UK_TO_PL))
    }

    @Test
    fun `whole sentence entries win`() {
        assertEquals("Co słychać w magazynie?", glossary.sentence("Як справи на складі?", TranslationDirection.UK_TO_PL))
        assertNull(glossary.sentence("Як справи на складі?", TranslationDirection.UK_TO_EN))
        assertEquals(3, glossary.size)
    }

    @Test
    fun `the example warehouse glossary parses`() {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "glossary/warehouse.glossary") }.first { it.isFile }
        val warehouse = Glossary.parse(file.readText())
        assertTrue(warehouse.size >= 30)
        assertEquals(
            "Paleta стоїть на rampa.",
            warehouse.prepare("Палета стоїть на рампі.", TranslationDirection.UK_TO_PL),
        )
        assertEquals("Check the delivery note", warehouse.prepare("Check the накладну", TranslationDirection.UK_TO_EN).replace("накладну", "delivery note"))
    }
}
