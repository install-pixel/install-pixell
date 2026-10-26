package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.search.SearchNormalizer
import com.blacklaunch.launcher.voice.NumberWords

/** Кроки, які є лише в «Моїх командах» (не голосові дії): пауза між кроками, головний екран, попередній режим. */
sealed interface StepCommand {
    data class Wait(val seconds: Int) : StepCommand
    data object Home : StepCommand
    data object PreviousProfile : StepCommand
}

/** Розбір цих кроків трьома мовами — так само текстом, як і решта кроків. */
object StepCommands {
    private val waitWords = setOf("зачекай", "зачекати", "почекай", "почекати", "чекай", "wait", "czekaj", "poczekaj", "odczekaj")
    private val homePhrases = setOf(
        "головний екран", "на головний екран", "домашній екран", "home screen", "go to home screen", "ekran glowny", "na ekran glowny",
    )
    private val previousPhrases = setOf(
        "попередній режим", "попередній профіль", "previous mode", "previous profile", "poprzedni tryb", "poprzedni profil",
    )

    fun parse(text: String): StepCommand? {
        val normalized = SearchNormalizer.normalize(text)
        if (normalized in homePhrases) return StepCommand.Home
        if (normalized in previousPhrases) return StepCommand.PreviousProfile
        val words = SearchNormalizer.words(normalized)
        if (words.isEmpty() || words.first() !in waitWords) return null
        var total = 0
        var pending: Int? = null
        var i = 1
        while (i < words.size) {
            val number = NumberWords.readNumber(words, i)
            if (number != null) {
                pending = number.first
                i = number.second
                continue
            }
            unitSeconds(words[i])?.let { unit ->
                total += (pending ?: 1) * unit
                pending = null
            }
            i++
        }
        // «зачекай 10» — секунди.
        if (pending != null) total += pending
        return if (total > 0) StepCommand.Wait(total.coerceAtMost(MAX_WAIT_S)) else null
    }

    private fun unitSeconds(word: String): Int? = when {
        word.startsWith("секунд") || word.startsWith("sec") || word.startsWith("sekund") -> 1
        word.startsWith("хвилин") || word.startsWith("min") -> 60
        word.startsWith("годин") || word.startsWith("hour") || word.startsWith("godzin") -> 3600
        else -> null
    }

    /** Кроки до першої паузи, сама пауза (с) і решта після неї; без паузи — усі кроки й null. */
    fun splitAtWait(steps: List<String>): Triple<List<String>, Int?, List<String>> {
        val index = steps.indexOfFirst { parse(it) is StepCommand.Wait }
        if (index < 0) return Triple(steps, null, emptyList())
        val wait = parse(steps[index]) as StepCommand.Wait
        return Triple(steps.take(index), wait.seconds, steps.drop(index + 1))
    }

    private const val MAX_WAIT_S = 24 * 3600
}
