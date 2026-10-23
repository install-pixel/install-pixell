package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class BilingualSearchTest {
    private val engine = AppSearchEngine()

    private fun app(label: String, pkg: String) = AppInfo(label, pkg, "Main", 0)

    private val youtube = app("YouTube", "com.google.android.youtube")
    private val telegram = app("Telegram", "org.telegram.messenger")
    private val camera = app("Камера", "com.oplus.camera")
    private val novaPoshta = app("Нова Пошта", "ua.novaposhta.test")
    private val maps = app("Maps", "com.google.android.apps.maps")
    private val mono = app("monobank", "com.ftband.mono")
    private val all = listOf(youtube, telegram, camera, novaPoshta, maps, mono)

    private fun search(query: String, user: Map<String, Set<String>> = emptyMap()) =
        engine.index(all, user).search(query)

    @Test fun ukrainianAliasFindsBrand() = assertEquals(youtube, search("ютуб").first())

    @Test fun aliasPrefixWorksWhileTyping() = assertEquals(youtube, search("юту").first())

    @Test fun englishNameStillWorks() = assertEquals(youtube, search("youtube").first())

    @Test fun synonymGroupMatchesSystemAppInOtherLanguage() =
        assertEquals(camera, search("camera").first())

    @Test fun cyrillicQueryTransliteratedToLatinLabel() =
        assertEquals(telegram, search("телегр").first())

    @Test fun latinQueryFindsCyrillicLabel() =
        assertEquals(novaPoshta, search("nova posh").first())

    @Test fun labelMatchBeatsAlias() {
        // «maps» — справжня назва Maps; «Камера»/інші з псевдонімом «maps» не мають бути вище.
        assertEquals(maps, search("maps").first())
    }

    @Test fun exactLabelBeatsTransliteration() {
        val cyr = app("Моно", "test.mono.cyr")
        val result = engine.index(listOf(mono, cyr)).search("моно")
        assertEquals(cyr, result.first())
    }

    @Test fun transliterationDoesNotMatchInsideWords() {
        // «гнал» → «gnal» є всередині «signal», але транслітерація дає лише збіги з початку.
        val signal = app("Signal", "org.thoughtcrime.securesms")
        assertEquals(emptyList<AppInfo>(), engine.index(listOf(signal)).search("гнал"))
        assertEquals(listOf(signal), engine.index(listOf(signal)).search("сігнал"))
    }

    @Test fun ukrainianAliasMatchesInsideWord() =
        assertEquals(telegram, engine.index(listOf(telegram)).search("еграм").first())

    @Test fun unknownCyrillicWordFindsNothing() =
        assertEquals(emptyList<AppInfo>(), search("холодильник"))

    @Test fun userAliasIsSearchable() {
        val user = mapOf("com.oplus.camera" to setOf("фотик"))
        assertEquals(camera, search("фотик", user).first())
    }

    @Test fun aliasesForUnknownPackageAreEmpty() =
        assertEquals(emptyList<String>(), AppAliases().aliasesFor(app("Something", "x.y.z")))
}
