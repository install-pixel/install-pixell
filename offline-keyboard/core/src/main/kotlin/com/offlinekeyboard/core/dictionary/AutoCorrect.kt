package com.offlinekeyboard.core.dictionary

import com.offlinekeyboard.core.layout.Language

/**
 * Careful offline autocorrect: when a finished word is unknown and a known word is clearly the one meant
 * (one slip away, or two in a longer word), the known word replaces it. Known words are the base
 * dictionary plus the user's own words.
 *
 * "One slip" means a neighbouring key pressed instead of the right one, two letters swapped, or one
 * letter missing or doubled. The last letter is never touched: there a different letter usually means a
 * different word form ("мама"/"мами", "domu"/"domy"), not a mistake. Short words, words with capitals
 * inside, digits, and words the user has rejected (undone) are left alone, and so are all words the
 * keyboard knows as correct, including the rare ones it never suggests ([BaseDictionary.isKnown]).
 */
object AutoCorrect {

    const val MIN_LENGTH = 4

    /**
     * Letters at the end that are never changed. Measured on 800 simulated slips per language: one letter
     * fixes about 75% of them (two letters: about 60%) while rare correct words outside the known list are
     * changed about 7% of the time (two letters: 6%); zero letters fixes 89% but spoils 12%.
     */
    private const val ENDING = 1

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
     * [candidates] are known words in preference order (the user's own first, then most frequent first);
     * [context] are the words that usually follow the previous word (they win a close call).
     *
     * Every candidate gets the cost of the slips that turn it into [typed] ([slipCost]): a neighbouring key,
     * two swapped letters, a missing, doubled or extra letter; two slips are allowed in longer words. Less
     * frequent words cost a little more, words that fit after the previous word a little less. The word is
     * corrected only when the best candidate is clearly better than the next one: a close call is left as
     * typed, as is everything the old one-slip rule left alone (word endings, far keys, known words).
     */
    fun correction(
        language: Language,
        typed: String,
        known: (String) -> Boolean,
        candidates: Sequence<String>,
        rejected: Set<String> = emptySet(),
        context: Set<String> = emptySet(),
    ): String? {
        if (typed.length < MIN_LENGTH || typed.any { !it.isLetter() && it != '\'' && it != 'ʼ' && it != '’' && it != '-' }) return null
        // "iPhone", "McDonald": capitals inside a word are on purpose.
        if (typed.drop(1).any { it.isUpperCase() } && !typed.all { it.isUpperCase() }) return null
        val lower = typed.lowercase(language.locale)
        if (lower in rejected || known(lower)) return null
        // Ukrainian: a word with its apostrophe in place is right; one without it gets it back first.
        if (Apostrophes.isKnown(language, lower)) return null
        Apostrophes.restore(language, lower)?.let { return WordRules.applyCase(it, typed, language) }
        val near = neighbours[language].orEmpty()
        val budget = if (lower.length >= TWO_SLIPS_FROM) TWO_SLIPS else ONE_SLIP
        var best: String? = null
        var bestScore = Float.MAX_VALUE
        var secondScore = Float.MAX_VALUE
        val seen = HashSet<String>()
        for ((index, candidate) in candidates.withIndex()) {
            val word = candidate.lowercase(language.locale)
            if (!seen.add(word)) continue
            if (kotlin.math.abs(word.length - lower.length) > 2) continue
            // The ending is the word form, not a slip.
            if (word.takeLast(ENDING) != lower.takeLast(ENDING)) continue
            val cost = slipCost(lower, word, near, budget)
            if (cost > budget) continue
            val rarity = RARITY * (index.coerceAtMost(RARE_FROM) / RARE_FROM.toFloat())
            val fits = if (word in context) CONTEXT_BONUS else 0f
            val score = cost + rarity - fits
            if (score < bestScore) {
                secondScore = bestScore
                bestScore = score
                best = candidate
            } else if (score < secondScore) {
                secondScore = score
            }
        }
        val match = best ?: return null
        if (secondScore - bestScore < MARGIN) return null
        return WordRules.applyCase(match, typed, language)
    }

    /**
     * The cost of turning [word] into [typed] by slips (weighted Damerau–Levenshtein): a neighbouring key
     * 0.5, two swapped letters 0.6, a doubled letter 0.4, a missing or extra letter 0.8; a key far away is
     * never a slip. Stops early once the cost is above [limit] (then returns more than [limit]).
     */
    internal fun slipCost(typed: String, word: String, near: Map<Char, Set<Char>>, limit: Float): Float {
        val n = typed.length
        val m = word.length
        val inf = limit + 1f
        var prev2 = FloatArray(m + 1) { inf }
        var prev = FloatArray(m + 1) { it * INDEL }
        var cur = FloatArray(m + 1)
        for (i in 1..n) {
            cur[0] = i * INDEL
            var rowMin = cur[0]
            for (j in 1..m) {
                val a = typed[i - 1]
                val b = word[j - 1]
                val sub = when {
                    a == b -> 0f
                    b in near[a].orEmpty() -> NEIGHBOUR
                    else -> inf
                }
                // An extra letter typed: cheaper when it doubles the one before ("приивіт").
                val extra = if (i > 1 && typed[i - 2] == a) DOUBLE else INDEL
                // A letter missing: cheaper when the word has it twice in a row ("tomorow").
                val missing = if (j > 1 && word[j - 2] == b) DOUBLE else INDEL
                var c = minOf(prev[j - 1] + sub, prev[j] + extra, cur[j - 1] + missing)
                if (i > 1 && j > 1 && a == word[j - 2] && typed[i - 2] == b && a != b) c = minOf(c, prev2[j - 2] + SWAP)
                cur[j] = c
                if (c < rowMin) rowMin = c
            }
            if (rowMin > limit) return inf
            val t = prev2
            prev2 = prev
            prev = cur
            cur = t
        }
        return prev[m]
    }

    private const val NEIGHBOUR = 0.5f
    private const val SWAP = 0.6f
    private const val DOUBLE = 0.4f
    private const val INDEL = 0.8f
    /** What one slip may cost; longer words may have two. */
    private const val ONE_SLIP = 0.85f
    private const val TWO_SLIPS = 1.45f
    private const val TWO_SLIPS_FROM = 7
    /** The rarest base words cost this much more than the most frequent ones. */
    private const val RARITY = 0.3f
    private const val RARE_FROM = 30_000
    private const val CONTEXT_BONUS = 0.25f
    /** The best candidate must be this much better than the next, or the word stays as typed. */
    private const val MARGIN = 0.15f

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
