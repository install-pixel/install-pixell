package com.blacklaunch.launcher.voice

import java.util.Locale

/** Мови розпізнавання: мова телефону (українська чи польська), інакше англійська. */
object VoiceLanguages {
    const val UKRAINIAN = "uk-UA"
    const val POLISH = "pl-PL"
    const val ENGLISH = "en-US"
    val ALL = listOf(UKRAINIAN, POLISH, ENGLISH)

    fun forLocale(locale: Locale = Locale.getDefault()): String = when (locale.language) {
        "uk" -> UKRAINIAN
        "pl" -> POLISH
        else -> ENGLISH
    }

    /** Мови, для яких має сенс показувати офлайн-статус: мова телефону та англійська. */
    fun relevant(locale: Locale = Locale.getDefault()): List<String> = listOf(forLocale(locale), ENGLISH).distinct()
}
