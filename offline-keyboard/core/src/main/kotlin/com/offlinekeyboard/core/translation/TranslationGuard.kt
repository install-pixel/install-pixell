package com.offlinekeyboard.core.translation

/**
 * The small offline model makes something up for what it cannot translate (one letter "т" came back as
 * "Т.Т" or with digits): such a result is not used, the text stays as written.
 */
object TranslationGuard {
    /** [translated] when it is a translation of [source]; otherwise [source] itself. */
    fun accept(source: String, translated: String): String {
        val out = translated.trim()
        if (out.isEmpty()) return source
        // Digits the text did not have: never a translation.
        if (out.any { it.isDigit() && it !in source }) return source
        val letters = source.count { it.isLetter() }
        if (letters <= SHORT_LETTERS) {
            // A short word: no marks it did not have, and not several times longer.
            if (out.any { !it.isLetter() && !it.isWhitespace() && it !in source }) return source
            if (out.count { it.isLetter() } > letters * 3 + 2) return source
        }
        return translated
    }

    private const val SHORT_LETTERS = 3
}
