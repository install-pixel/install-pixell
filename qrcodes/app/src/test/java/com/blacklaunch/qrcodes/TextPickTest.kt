package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.data.displayTitle
import com.blacklaunch.qrcodes.scan.TextPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPickTest {
    @Test
    fun onlyLongerThanFiveCharacters() {
        assertFalse(TextPick.accept("12345"))
        assertFalse(TextPick.accept("  abc12  "))
        assertTrue(TextPick.accept("123456"))
        assertTrue(TextPick.accept("DSV-12345"))
    }

    @Test
    fun duplicatesAndShortOnesDropped() {
        val found = TextPick.filter(listOf("SKU", "PL-0099812", "PL-0099812 ", "Zebra ZT411", "OK")) { it }
        assertEquals(listOf("PL-0099812", "Zebra ZT411"), found)
    }

    @Test
    fun ownNameHidesContent() {
        assertEquals("Пароль Wi‑Fi", displayTitle("Пароль Wi‑Fi", "s3cr3t-pass"))
        assertEquals("s3cr3t-pass", displayTitle(null, "s3cr3t-pass"))
        assertEquals("a b", displayTitle("  ", "a\n b"))
    }
}
