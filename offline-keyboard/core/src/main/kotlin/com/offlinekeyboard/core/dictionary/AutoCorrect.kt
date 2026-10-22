package com.offlinekeyboard.core.dictionary

import com.offlinekeyboard.core.layout.Language

/**
 * Careful offline autocorrect: when a finished word is unknown and a known word is one slip away, the
 * known word replaces it. Known words are the base dictionary plus the user's own words.
 *
 * "One slip" means a neighbouring key pressed instead of the right one, two letters swapped, or one
 * letter missing or doubled. Word endings are never touched (the last two letters in Ukrainian and
 * Polish, the last one in English): there a different letter usually means a different word form
 * ("мама"/"мами", "dom"/"domy"), not a mistake. Short words, words with capitals inside, digits, and
 * words the user has rejected (undone) are left alone.
 */
object AutoCorrect {

    const val MIN_LENGTH = 4

    private val rows = mapOf(
        Language.UK to listOf("йцукенгшщзхї", "фівапролджє", "ячсмитьбю"),
        Language.PL to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        Language.EN to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
    )

    /** Keys around each letter on the mobile layout (same row ±1, rows above and below ±1). */
    private val neighbours: Map<Language, Map<Char, Set<Char>>> = rows.mapValues { (_, r) ->
        val map = HashMap<Char, MutableSet<Char>>()
        for ((ri, row) in r.withIndex()) {
            for ((ci, c) in row.withIndex()) {
                val set = map.getOrPut(c) { HashSet() }
                for (dr in -1..1) {
                    val other = r.getOrNull(ri + dr) ?: continue
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        other.getOrNull(ci + dc)?.let(set::add)
                    }
                }
            }
        }
        map
    }

    /**
     * The correction for [typed], or null to keep it. [known] tells whether a word (lower case) is known;
     * [candidates] are known words in preference order (most used first).
     */
    fun correction(
        language: Language,
        typed: String,
        known: (String) -> Boolean,
        candidates: Sequence<String>,
        rejected: Set<String> = emptySet(),
    ): String? {
        if (typed.length < MIN_LENGTH || typed.any { !it.isLetter() && it != '\'' && it != 'ʼ' && it != '’' && it != '-' }) return null
        // "iPhone", "McDonald": capitals inside a word are on purpose.
        if (typed.drop(1).any { it.isUpperCase() } && !typed.all { it.isUpperCase() }) return null
        val lower = typed.lowercase(language.locale)
        if (lower in rejected || known(lower)) return null
        val near = neighbours[language].orEmpty()
        val ending = if (language == Language.EN) 1 else 2
        val match = candidates.firstOrNull { oneSlip(lower, it.lowercase(language.locale), near, ending) } ?: return null
        return WordRules.applyCase(match, typed, language)
    }

    /**
     * One letter replaced by a neighbouring key, two adjacent letters swapped, or one letter missing or
     * extra; never within the last [ending] letters of [word].
     */
    internal fun oneSlip(typed: String, word: String, near: Map<Char, Set<Char>>, ending: Int): Boolean {
        if (typed == word) return false
        if (typed.length == word.length + 1) return oneLetterApart(longer = typed, shorter = word, word.length, ending)
        if (typed.length + 1 == word.length) return oneLetterApart(longer = word, shorter = typed, word.length, ending)
        if (typed.length != word.length) return false
        val diff = typed.indices.filter { typed[it] != word[it] }
        if (diff.any { it >= typed.length - ending }) return false
        return when (diff.size) {
            1 -> {
                val i = diff[0]
                word[i] in near[typed[i]].orEmpty()
            }
            2 -> {
                val (i, j) = diff
                j == i + 1 && typed[i] == word[j] && typed[j] == word[i]
            }
            else -> false
        }
    }

    /** [longer] is [shorter] with one letter added, and not within the ending of the correct word. */
    private fun oneLetterApart(longer: String, shorter: String, correctLength: Int, ending: Int): Boolean {
        var i = longer.indices.firstOrNull { it >= shorter.length || longer[it] != shorter[it] } ?: return false
        if (longer.removeRange(i, i + 1) != shorter) return false
        // A doubled or missing double letter counts at its first copy ("приивіт", "прівіт").
        while (i > 0 && longer[i - 1] == longer[i]) i--
        return i < correctLength - ending
    }
}
