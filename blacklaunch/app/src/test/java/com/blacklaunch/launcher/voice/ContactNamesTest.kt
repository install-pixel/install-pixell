package com.blacklaunch.launcher.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactNamesTest {
    @Test fun caseFormsShrinkToTheStartOfTheName() {
        assertEquals(listOf("марії", "марі", "мар"), ContactNames.variants("марії"))
        assertEquals(listOf("мамі", "мам"), ContactNames.variants("мамі"))
        assertEquals(listOf("mom"), ContactNames.variants("mom"))
        assertEquals(listOf("олегу петренку", "олег петренк", "оле петрен"), ContactNames.variants("олегу петренку"))
    }
}
