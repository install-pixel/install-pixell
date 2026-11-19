package com.offlinekeyboard.core.dictionary

import com.offlinekeyboard.core.layout.Language

/**
 * The Ukrainian apostrophe, put back where it was not typed: "мясо" → "м'ясо", "пять" → "п'ять", "обєкт" →
 * "об'єкт", "зявився" → "з'явився". A word gets one only when a known Ukrainian word begins that way
 * (`resources/dictionary/uk_apostrophe.txt`, from the LibreOffice Ukrainian dictionary): the apostrophe goes
 * between a consonant and я, ю, є or ї, and the beginning up to two letters after it must match ("м'яс").
 * "бюро" or "буряк" stay as they are.
 */
object Apostrophes {

    private const val APOSTROPHE = '\''
    private const val SOFT_AFTER = "яюєї"
    /** A beginning with one letter after the apostrophe ("здоров'я") also covers longer words when this long. */
    private const val SHORT_KEY_FROM = 3

    private val beginnings: Set<String> by lazy {
        val text = Apostrophes::class.java.getResourceAsStream("/dictionary/uk_apostrophe.txt")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return@lazy emptySet()
        parse(text)
    }

    fun parse(text: String): Set<String> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toHashSet()

    /** Whether [word] (lower case) has an apostrophe where a known Ukrainian word has one. */
    fun isKnown(language: Language, word: String, known: Set<String> = beginnings): Boolean {
        if (language != Language.UK) return false
        val normal = normalize(word)
        val i = normal.indexOf(APOSTROPHE)
        if (i <= 0) return false
        return normal.take(i + 3) in known || (i >= SHORT_KEY_FROM || normal.length == i + 2) && normal.take(i + 2) in known
    }

    /** [word] (lower case) with its missing apostrophe put back, or null when it needs none. */
    fun restore(language: Language, word: String, known: Set<String> = beginnings): String? {
        if (language != Language.UK || word.length < 3 || normalize(word).indexOf(APOSTROPHE) >= 0) return null
        for (i in 1 until word.length) {
            if (word[i] !in SOFT_AFTER || !isConsonant(word[i - 1])) continue
            val fixed = word.substring(0, i) + APOSTROPHE + word.substring(i)
            if (isKnown(language, fixed, known)) return fixed
        }
        return null
    }

    private fun normalize(word: String) = word.replace('ʼ', APOSTROPHE).replace('’', APOSTROPHE)

    private fun isConsonant(c: Char) = c in "бвгґджзйклмнпрстфхцчшщ"
}
