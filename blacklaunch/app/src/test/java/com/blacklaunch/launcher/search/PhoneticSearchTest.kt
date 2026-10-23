package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneticSearchTest {
    private val engine = AppSearchEngine(AppAliases(byPackage = emptyMap(), groups = emptyList()))
    private fun app(label: String) = AppInfo(label, "test." + label.lowercase().filter { it.isLetter() }, "Main", 0)

    private val spotify = app("Spotify")
    private val whatsapp = app("WhatsApp")
    private val netflix = app("Netflix")
    private val settings = app("Settings")
    private val calculator = app("Calculator")
    private val all = listOf(spotify, whatsapp, netflix, settings, calculator)

    private fun search(q: String) = engine.index(all).search(q)

    @Test fun cyrillicSpokenBrands() {
        assertEquals(spotify, search("спотіфай").first())
        assertEquals(whatsapp, search("вотсап").first())
        assertEquals(netflix, search("нетфлікс").first())
        assertEquals(settings, search("сетінгс").first())
    }

    @Test fun latinMisspelling() = assertEquals(spotify, search("spotifai").first())

    @Test fun unrelatedWordFindsNothing() {
        assertEquals(emptyList<AppInfo>(), search("холодильник"))
        assertEquals(emptyList<AppInfo>(), search("банан"))
    }

    @Test fun shortQueriesAreNotFuzzy() = assertEquals(emptyList<AppInfo>(), search("спф"))

    @Test fun realMatchBeatsFuzzy() {
        val spot = app("Spot")
        val result = engine.index(listOf(spotify, spot)).search("spot")
        assertEquals(spot, result.first())
    }

    @Test fun skeletonCollapsesDoublesAndSounds() {
        assertEquals(Phonetic.skeleton("whatsapp"), Phonetic.skeleton("вотсап").replace('o', 'a'))
        assertTrue(Phonetic.similarity(Phonetic.skeleton("settings"), Phonetic.skeleton("сетінгс")) >= 0.99)
    }
}
