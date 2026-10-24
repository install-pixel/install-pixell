package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchTest {
    private val engine = AppSearchEngine()
    private fun app(label: String, pkg: String, category: Int = -1) = AppInfo(label, pkg, "Main", 0, category)

    private val busfor = app("Busfor", "ua.busfor.test")
    private val bolt = app("Bolt", "ee.mtakso.client")
    private val mono = app("monobank", "com.ftband.mono")
    private val spotify = app("Spotify", "com.spotify.music", Topics.CATEGORY_AUDIO)
    private val maps = app("Maps", "com.google.android.apps.maps", Topics.CATEGORY_MAPS)
    private val telegram = app("Telegram", "org.telegram.messenger")
    private val all = listOf(busfor, bolt, mono, spotify, maps, telegram)

    private fun search(q: String, learned: Map<String, Map<String, Int>> = emptyMap()) = engine.index(all, learned = learned).search(q)

    @Test fun topicByKnownPackage() {
        assertTrue(bolt in search("таксі"))
        assertTrue(mono in search("гроші"))
        assertTrue(mono in search("pieniądze"))
    }

    @Test fun topicByAndroidCategory() {
        assertTrue(spotify in search("музика"))
        assertTrue(maps in search("navigation"))
    }

    @Test fun wordFormsMatch() {
        assertTrue(bolt in search("квитки на маршрутку"))
        assertTrue(maps in search("карти"))
    }

    @Test fun nameMatchStaysAboveTopic() {
        val result = search("bolt")
        assertEquals(bolt, result.first())
    }

    @Test fun unknownAppWithoutCategoryIsNotGuessed() = assertTrue(busfor !in search("маршрутка"))

    @Test fun learningPutsChosenAppFirst() {
        val learned = mapOf("маршрутка" to mapOf(busfor.key to 2))
        assertEquals(busfor, search("маршрутка", learned).first())
        assertEquals(busfor, search("Маршрутка ", learned).first())
    }

    @Test fun learningAppliesOnlyToTheSameQuery() {
        val learned = mapOf("маршрутка" to mapOf(busfor.key to 2))
        assertTrue(busfor !in search("музика", learned))
    }

    @Test fun learnedQueriesStoreAndCap() {
        val store = InMemoryLearnedQueries(maxQueries = 2)
        store.record("Маршрутка", busfor.key)
        store.record("маршрутка", busfor.key)
        store.record("музика", spotify.key)
        store.record("гроші", mono.key)
        assertEquals(setOf("музика", "гроші"), store.learned.value.keys)
        store.record("маршрутка", bolt.key)
        assertEquals(1, store.learned.value["маршрутка"]?.get(bolt.key))
    }

    @Test fun learnedEncodeDecodeRoundTrip() {
        val data = mapOf("маршрутка" to mapOf("a/Main/0" to 3, "b/Main/0" to 1), "music" to mapOf("c/Main/0" to 1))
        assertEquals(data, InMemoryLearnedQueries.decode(InMemoryLearnedQueries.encode(data)))
        assertEquals(emptyMap<String, Map<String, Int>>(), InMemoryLearnedQueries.decode("broken\nline"))
    }
}
