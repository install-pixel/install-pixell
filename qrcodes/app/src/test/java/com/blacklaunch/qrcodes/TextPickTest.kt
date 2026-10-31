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

class WordJoinTest {
    @Test
    fun chosenWordsInReadingOrder() {
        val lines = listOf(listOf("Paleta", "PL-0099812", "Zebra"), listOf("SKU", "445-221"))
        val picked = setOf(
            com.blacklaunch.qrcodes.scan.WordRef(1, 1),
            com.blacklaunch.qrcodes.scan.WordRef(0, 1),
        )
        assertEquals("PL-0099812 445-221", com.blacklaunch.qrcodes.scan.TextPick.join(lines, picked))
        assertEquals("", com.blacklaunch.qrcodes.scan.TextPick.join(lines, emptySet()))
    }
}
