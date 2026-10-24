package com.blacklaunch.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class MonogramTest {
    @Test fun lettersFromTheName() {
        assertEquals("Ка", Monogram.of("Калькулятор"))
        assertEquals("SM", Monogram.of("SIM-Menu"))
        assertEquals("ІП", Monogram.of("ІЧ-пульт"))
        assertEquals("PL", Monogram.of("PLAY"))
        assertEquals("•", Monogram.of("…"))
    }
}
