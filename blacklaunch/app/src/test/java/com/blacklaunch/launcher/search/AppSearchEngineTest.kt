package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.FakeAppSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchEngineTest {
    private val engine = AppSearchEngine()

    private fun search(query: String, vararg labels: String): List<String> =
        engine.index(labels.map(FakeAppSource::app)).search(query).map { it.label }

    @Test fun emptyQueryReturnsNothing() =
        assertEquals(emptyList<String>(), search("  ", "Telegram"))

    @Test fun noMatchReturnsEmptyList() =
        assertEquals(emptyList<String>(), search("zzz", "Telegram", "YouTube"))

    @Test fun matchesCaseInsensitively() =
        assertEquals(listOf("YouTube"), search("YOUTU", "YouTube", "Telegram"))

    @Test fun exactMatchBeatsPrefix() =
        assertEquals(listOf("Maps", "Maps Go"), search("maps", "Maps Go", "Maps"))

    @Test fun prefixBeatsWordPrefixBeatsContains() =
        assertEquals(
            listOf("Camera", "Open Camera", "Pocamera"),
            search("cam", "Pocamera", "Open Camera", "Camera"),
        )

    @Test fun partialMatchInsideName() =
        assertEquals(listOf("Telegram"), search("legr", "Telegram", "YouTube"))

    @Test fun singleLetterDoesNotMatchInsideWords() =
        assertEquals(listOf("Xbox"), search("x", "Xbox", "Maxim"))

    @Test fun multiWordQueryMatchesWordsInAnyOrder() =
        assertEquals(listOf("Google Maps"), search("maps goo", "Google Maps", "Google"))

    @Test fun ukrainianLabels() =
        assertEquals(listOf("Калькулятор"), search("кальк", "Калькулятор", "Календар"))

    @Test fun apostropheInsensitive() =
        assertEquals(listOf("П'ятниця"), search("пятн", "П'ятниця"))

    @Test fun longQueryDoesNotCrash() =
        assertEquals(emptyList<String>(), search("a".repeat(10_000), "Telegram"))

    @Test fun equalScoresSortShorterFirst() =
        assertEquals(listOf("Notes", "Notebook"), search("note", "Notebook", "Notes"))

    @Test fun fastOnLargeAppList() {
        val labels = (1..1_000).map { "Application number $it" }.toTypedArray()
        val index = engine.index(labels.map(FakeAppSource::app))
        // Розминка: перші виклики повільніші, поки JVM компілює код, — міряємо вже «теплий» пошук.
        repeat(50) { index.search("number ${it % 100}") }
        val start = System.nanoTime()
        repeat(200) { index.search("number ${it % 100}") }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("200 пошуків по 1000 програм зайняли $ms мс", ms < 2_000)
    }
}
