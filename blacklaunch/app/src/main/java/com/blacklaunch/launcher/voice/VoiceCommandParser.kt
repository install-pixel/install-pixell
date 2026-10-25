package com.blacklaunch.launcher.voice

import com.blacklaunch.launcher.search.SearchNormalizer

sealed interface VoiceCommand {
    /**
     * «відкрий телеграм», «open youtube» або просто «ютуб».
     * [explicit] — було слово «відкрий/запусти/open»: лише тоді програма запускається одразу.
     */
    data class Open(val query: String, val explicit: Boolean = false) : VoiceCommand

    /** «відкрий останній», «попередній додаток», «last app». */
    data object OpenLast : VoiceCommand

    /** «режим робота», «профіль дім» — виконується після появи профілів (Етап 8). */
    data class SwitchProfile(val name: String) : VoiceCommand

    /** Дія: будильник, таймер, ліхтарик, плеєр, дзвінок, SMS… (Етап 8c). */
    data class Action(val action: VoiceAction) : VoiceCommand
}

/**
 * Почато лише команду — «відкрий», «подзвони», «режим»… Офлайн-модель вважає паузу кінцем фрази;
 * після такого початку слухаємо далі, щоб людина встигла договорити.
 */
object IncompletePhrase {
    private val starters = setOf(
        "відкрий", "відкрити", "запусти", "запустити", "покажи", "open", "launch", "start", "otworz", "uruchom", "pokaz",
        "режим", "профіль", "mode", "profile", "tryb", "profil",
        "подзвони", "зателефонуй", "набери", "call", "dial", "zadzwon",
        "напиши", "надішли", "відправ", "write", "text", "send", "message", "napisz", "wyslij",
        "таймер", "будильник", "timer", "alarm", "budzik", "minutnik",
        "маршрут", "навігація", "route", "navigate", "trasa", "nawiguj",
        "яскравість", "brightness", "jasnosc", "на", "до", "to", "w", "у", "в",
    )

    fun isIncomplete(text: String): Boolean {
        val words = SearchNormalizer.words(SearchNormalizer.normalize(text))
        return words.isNotEmpty() && words.all { it in starters }
    }
}

/** Простий локальний розбір голосових команд українською, польською та англійською, без ШІ. */
object VoiceCommandParser {
    private val politeness = listOf("будь ласка", "будьласка", "please", "пожалуйста", "prosze")
    private val openVerbs = listOf(
        "відкрий", "відкрийте", "відкрити", "запусти", "запустіть", "запустити", "увімкни", "покажи",
        "open", "launch", "start", "run",
        "otworz", "otworzyc", "uruchom", "wlacz", "pokaz",
    )
    private val appNouns = listOf(
        "додаток", "програму", "програма", "застосунок", "application", "app", "aplikacje", "aplikacja", "apke",
    )
    private val lastWords = setOf(
        "останній", "останню", "останнє", "останнього", "попередній", "попередню", "попереднє",
        "last", "previous", "recent",
        "ostatnia", "ostatnio", "ostatni", "ostatnie", "poprzednia", "poprzedni", "poprzednie",
    )
    private val profileWords = listOf("режим", "профіль", "mode", "profile", "tryb", "profil")

    fun parse(text: String): VoiceCommand? {
        var words = SearchNormalizer.words(removePoliteness(SearchNormalizer.normalize(text)))
        if (words.isEmpty()) return null

        VoiceActionParser.parse(text, words)?.let { return VoiceCommand.Action(it) }

        if (words.first() in profileWords && words.size > 1) {
            return VoiceCommand.SwitchProfile(words.drop(1).joinToString(" "))
        }
        val explicit = words.first() in openVerbs
        if (explicit) words = words.drop(1)
        if (words.isEmpty()) return null

        // «останній додаток», «last app», «попередню програму»
        if (words.first() in lastWords && words.drop(1).all { it in appNouns }) return VoiceCommand.OpenLast

        if (words.first() in appNouns && words.size > 1) words = words.drop(1)
        return VoiceCommand.Open(words.joinToString(" "), explicit)
    }

    private fun removePoliteness(text: String): String =
        politeness.fold(" $text ") { acc, phrase -> acc.replace(" $phrase ", " ") }.trim()
}
