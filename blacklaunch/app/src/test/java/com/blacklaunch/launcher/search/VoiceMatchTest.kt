package com.blacklaunch.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceMatchTest {
    private val apps = listOf("Spotify", "Telegram", "YouTube", "Symfonium", "Chrome", "Calculator", "Settings", "WhatsApp")

    private fun best(heard: String) = VoiceMatch.best(heard, apps) { listOf(it) }

    @Test fun whatOfflineModelHears() {
        assertEquals("Spotify", best("спорт фай"))
        assertEquals("Spotify", best("спот і фай"))
        assertEquals("Symfonium", best("симфоніум"))
        assertEquals("Telegram", best("теле грам"))
        assertEquals("WhatsApp", best("вотсап"))
    }

    @Test fun noRandomMatches() {
        assertNull(best("картопля"))
        assertNull(best("дім"))
    }
}
