package com.blacklaunch.launcher.global

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {
    @Test fun allWordsInAnyOrder() {
        assertTrue(TextMatch.containsAll("Посилка чекає у відділенні №5", "відділенні посилка"))
        assertTrue(TextMatch.containsAll("Zamówienie wysłane", "zamowienie"))
        assertFalse(TextMatch.containsAll("Посилка чекає", "посилка оплата"))
        assertFalse(TextMatch.containsAll("Будь-що", "   "))
    }

    @Test fun snippetAroundMatch() {
        val text = "a".repeat(50) + " код 1234 для входу"
        assertEquals("…" + "a".repeat(19) + " код 1234 для входу", TextMatch.snippet(text, "код", before = 20))
    }
}
