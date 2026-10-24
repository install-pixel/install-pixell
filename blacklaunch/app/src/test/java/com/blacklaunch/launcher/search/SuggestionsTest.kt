package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.apps.LaunchRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class SuggestionsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun app(label: String) = AppInfo(label, "pkg.$label", "Main", 0)
    private val news = app("News")
    private val music = app("Music")
    private val bank = app("Bank")
    private val apps = listOf(news, music, bank)
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    @Test fun appsUsedAtThisTimeOfDayComeFirst() {
        // Новини щоранку о 8, музика щовечора о 20; зараз 8:30 через тиждень.
        val records = (0 until 7).flatMap { d ->
            listOf(LaunchRecord(news.key, d * day + 8 * hour), LaunchRecord(music.key, d * day + 20 * hour))
        }
        val now = 7 * day + 8 * hour + 30 * 60 * 1000
        assertEquals(news, AppSuggestions.suggest(records, apps, now, zone = utc).first())
    }

    @Test fun lastAppIsAlwaysSuggestedAndUninstalledSkipped() {
        val records = listOf(
            LaunchRecord(news.key, 8 * hour),
            LaunchRecord("pkg.Gone/Main/0", 9 * hour),
            LaunchRecord(bank.key, 15 * hour),
        )
        val result = AppSuggestions.suggest(records, apps, 8 * hour, zone = utc)
        assertEquals(setOf(news, bank), result.toSet())
    }

    @Test fun recentQueriesNewestFirstWithoutDuplicates() {
        val recent = InMemoryRecentQueries(max = 3)
        listOf("таймер 10 хвилин", "Telegram", "wifi", "telegram!", "music").forEach(recent::record)
        assertEquals(listOf("music", "telegram!", "wifi"), recent.queries.value)
        recent.remove("wifi")
        assertEquals(listOf("music", "telegram!"), recent.queries.value)
    }
}
