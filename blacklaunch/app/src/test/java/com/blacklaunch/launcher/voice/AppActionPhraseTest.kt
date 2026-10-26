package com.blacklaunch.launcher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppActionPhraseTest {
    @Test fun splitsFromLastPreposition() {
        assertEquals(listOf("підписки" to "YouTube"), AppActionPhrase.parse("відкрий підписки в YouTube"))
        assertEquals(listOf("продовжити читання" to "Moon Reader"), AppActionPhrase.parse("покажи продовжити читання в Moon Reader"))
        assertEquals(listOf("subscriptions" to "YouTube"), AppActionPhrase.parse("open subscriptions in YouTube"))
        assertEquals(listOf("subskrypcje" to "YouTube"), AppActionPhrase.parse("otwórz subskrypcje w YouTube"))
        // Кілька прийменників — спершу останній.
        assertEquals("чат з Олегом" to "Telegram", AppActionPhrase.parse("відкрий чат з Олегом у Telegram").first())
    }

    @Test fun notThisPhrase() {
        assertEquals(emptyList<Pair<String, String>>(), AppActionPhrase.parse("відкрий YouTube"))
        assertEquals(emptyList<Pair<String, String>>(), AppActionPhrase.parse("увімкни музику в Symfonium"))
    }

    @Test fun shortcutLabels() {
        assertTrue(ShortcutMatch.matches("підписки", "Підписки"))
        assertTrue(ShortcutMatch.matches("останню книгу", "Продовжити читання"))
        assertTrue(ShortcutMatch.matches("last video", "Continue watching"))
        assertTrue(ShortcutMatch.matches("пошук", "Пошук"))
        assertFalse(ShortcutMatch.matches("підписки", "Пошук"))
    }
}
