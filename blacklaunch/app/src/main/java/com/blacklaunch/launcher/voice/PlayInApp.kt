package com.blacklaunch.launcher.voice

import com.blacklaunch.launcher.search.SearchNormalizer

/**
 * «Увімкни музику на Symfonium», «грай у Spotify», «play music on YouTube Music», «włącz muzykę w Spotify» —
 * назва програми, в якій увімкнути музику; null — це не така команда.
 */
object PlayInApp {
    private val verbs = setOf("грай", "увімкни", "включи", "відтвори", "постав", "play", "start", "graj", "wlacz", "pusc", "odtworz")
    private val music = setOf(
        "музику", "музика", "пісні", "пісню", "відео", "подкаст", "music", "songs", "video", "podcast",
        "muzyke", "muzyka", "piosenki", "wideo", "podcast",
    )
    private val prepositions = setOf("в", "у", "на", "через", "on", "in", "with", "w", "na", "przez")

    fun parse(text: String): String? {
        val words = SearchNormalizer.words(SearchNormalizer.normalize(text))
        if (words.size < 3 || words.first() !in verbs) return null
        var i = 1
        if (words[i] in music) i++
        if (i >= words.size || words[i] !in prepositions) return null
        val app = words.drop(i + 1)
        return if (app.isEmpty()) null else VoiceActionParser.originalTail(text, app.size)
    }
}
