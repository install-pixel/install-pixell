package com.blacklaunch.launcher.global

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun calc(s: String) = Calculator.evaluate(s)

    @Test fun basics() {
        assertEquals("100", calc("25*4"))
        assertEquals("19.5", calc("12,5 + 7"))
        assertEquals("14", calc("(3+4)×2"))
        assertEquals("2.5", calc("5 ÷ 2"))
        assertEquals("1024", calc("2^10"))
        assertEquals("-3", calc("-5+2"))
    }

    @Test fun precedence() = assertEquals("14", calc("2+3*4"))

    @Test fun percent() {
        assertEquals("30", calc("200*15%"))
        assertEquals("220", calc("200+10%"))
        assertEquals("180", calc("200-10%"))
        assertEquals("0.15", calc("15%"))
    }

    @Test fun divisionKeepsPrecision() = assertEquals("0.333333333333", calc("1/3"))

    @Test fun notExpressions() {
        assertNull(calc("2026"))
        assertNull(calc("7,5"))
        assertNull(calc("telegram"))
        assertNull(calc(""))
        assertNull(calc("+"))
        assertNull(calc("2+"))
        assertNull(calc("(2+3"))
    }

    @Test fun divisionByZeroIsNotShown() = assertNull(calc("5/0"))

    @Test fun hugeExponentIsRejected() = assertNull(calc("2^100000"))
}
