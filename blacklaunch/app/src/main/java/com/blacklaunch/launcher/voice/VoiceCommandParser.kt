package com.blacklaunch.launcher.voice

import com.blacklaunch.launcher.search.SearchNormalizer

sealed interface VoiceCommand {
    /** «відкрий телеграм», «open youtube» або просто «ютуб». */
    data class Open(val query: String) : VoiceCommand

    /** «відкрий останній», «попередній додаток», «last app». */
    data object OpenLast : VoiceCommand

    /** «режим робота», «профіль дім» — виконується після появи профілів (Етап 8). */
    data class SwitchProfile(val name: String) : VoiceCommand
}

/** Простий локальний розбір голосових команд українською та англійською, без ШІ. */
object VoiceCommandParser {
    private val politeness = listOf("будь ласка", "будьласка", "please", "пожалуйста")
    private val openVerbs = listOf(
        "відкрий", "відкрийте", "відкрити", "запусти", "запустіть", "запустити", "увімкни", "покажи",
        "open", "launch", "start", "run",
    )
    private val appNouns = listOf("додаток", "програму", "програма", "застосунок", "application", "app")
    private val lastWords = setOf(
        "останній", "останню", "останнє", "останнього", "попередній", "попередню", "попереднє",
        "last", "previous", "recent",
    )
    private val profileWords = listOf("режим", "профіль", "mode", "profile")

    fun parse(text: String): VoiceCommand? {
        var words = SearchNormalizer.words(removePoliteness(SearchNormalizer.normalize(text)))
        if (words.isEmpty()) return null

        if (words.first() in profileWords && words.size > 1) {
            return VoiceCommand.SwitchProfile(words.drop(1).joinToString(" "))
        }
        if (words.first() in openVerbs) words = words.drop(1)
        if (words.isEmpty()) return null

        // «останній додаток», «last app», «попередню програму»
        if (words.first() in lastWords && words.drop(1).all { it in appNouns }) return VoiceCommand.OpenLast

        if (words.first() in appNouns && words.size > 1) words = words.drop(1)
        return VoiceCommand.Open(words.joinToString(" "))
    }

    private fun removePoliteness(text: String): String =
        politeness.fold(" $text ") { acc, phrase -> acc.replace(" $phrase ", " ") }.trim()
}
