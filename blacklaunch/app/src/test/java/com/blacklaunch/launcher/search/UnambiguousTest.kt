package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnambiguousTest {
    private val engine = AppSearchEngine()
    private fun app(label: String, pkg: String = "test." + label.lowercase().filter { it.isLetter() }) =
        AppInfo(label, pkg, "Main", 0)

    private fun pick(query: String, vararg apps: AppInfo) =
        SearchIndex.unambiguous(engine.index(apps.toList()).searchScored(query))

    @Test fun singleResultIsUnambiguous() {
        val telegram = app("Telegram")
        assertEquals(telegram, pick("teleg", telegram, app("YouTube")))
    }

    @Test fun exactNameAmongPrefixesIsUnambiguous() {
        val maps = app("Maps")
        assertEquals(maps, pick("maps", maps, app("Maps Go")))
    }

    @Test fun exactAliasIsUnambiguous() {
        val youtube = app("YouTube", "com.google.android.youtube")
        assertEquals(youtube, pick("ютуб", youtube, app("YouTube Music", "com.google.android.apps.youtube.music")))
    }

    @Test fun severalPrefixMatchesAreAmbiguous() =
        assertNull(pick("goo", app("Google"), app("Google Maps")))

    @Test fun twoExactMatchesAreAmbiguous() =
        assertNull(pick("notes", app("Notes", "a.notes"), app("Notes", "b.notes")))

    @Test fun nothingFoundIsNull() = assertNull(pick("zzz", app("Telegram")))

    @Test fun frequentAppGoesFirstAmongEqualMatches() {
        val a = app("Camera A")
        val b = app("Camera B")
        val result = engine.index(listOf(a, b)).search("camera", mapOf(b.key to 3))
        assertEquals(listOf(b, a), result)
    }
}
