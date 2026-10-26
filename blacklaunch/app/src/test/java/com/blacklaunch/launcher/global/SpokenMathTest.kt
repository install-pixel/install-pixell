package com.blacklaunch.launcher.global

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenMathTest {
    private fun calc(text: String) = SpokenMath.toExpression(text)?.let(Calculator::evaluate)

    @Test fun ukrainian() {
        assertEquals("4", calc("два плюс два"))
        assertEquals("1000", calc("двісті п'ятдесят помножити на чотири"))
        assertEquals("12.5", calc("скільки буде сто поділити на вісім"))
        assertEquals("1500", calc("тисяча плюс п'ятсот"))
        assertEquals("7.5", calc("2,5 помножити на 3"))
        assertEquals("1024", calc("два в степені десять"))
        assertEquals("230", calc("200 плюс 15 відсотків"))
        assertEquals("4.5", calc("два кома п'ять плюс два"))
    }

    @Test fun englishAndPolish() {
        assertEquals("36", calc("what is twelve times three"))
        assertEquals("1300", calc("one thousand three hundred minus 0"))
        assertEquals("4", calc("ile to dwa razy dwa"))
        assertEquals("25", calc("sto podzielić przez cztery"))
    }

    @Test fun digitsAndSymbolsFromRecognizer() {
        assertEquals("4", calc("2 + 2"))
        assertEquals("21", calc("7 × 3"))
    }

    @Test fun notMath() {
        assertNull(SpokenMath.toExpression("2 кімнати"))
        assertNull(SpokenMath.toExpression("відкрий телеграм"))
        assertNull(SpokenMath.toExpression("плюс"))
        assertNull(SpokenMath.toExpression("двадцять"))
        assertNull(SpokenMath.toExpression("таймер 10 хвилин"))
    }
}
