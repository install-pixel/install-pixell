package com.offlinekeyboard.core.translation

/**
 * Safety net for machine translation: numbers, times, dates, codes and abbreviations must come through
 * unchanged. Small offline models sometimes drop or alter them ("22:00" → "10:00", "PESEL" → "ESEL"),
 * so the keyboard warns the user instead of silently inserting a wrong value.
 */
object TranslationCheck {

    // 12, 22:00, 30.06.2026, 345,67, 1/2, 45821 ...
    private val number = Regex("\\d+(?:[:.,/\\-]\\d+)*")

    // WMS, PDF, SSCC, PESEL, IBAN, HR, ABC-123, B2B (two or more capitals, optionally with digits/hyphen)
    private val code = Regex("(?<![\\p{L}\\d])\\p{Lu}[\\p{Lu}\\d]*(?:-[\\p{Lu}\\d]+)*(?![\\p{L}\\d])")

    /** Values in [text] that a translation must keep exactly. */
    fun protectedTokens(text: String): List<String> {
        val numbers = number.findAll(text).map { it.value.trimEnd('.', ',', ':', '/', '-') }
        val codes = code.findAll(text).map { it.value }
            .filter { it.count { c -> c.isUpperCase() } >= 2 || it.any { c -> c.isDigit() } }
        return (numbers + codes).filter { it.isNotEmpty() }.distinct().toList()
    }

    /** Protected values of [source] that do not appear in [translation]. Empty means the check passed. */
    fun missing(source: String, translation: String): List<String> =
        protectedTokens(source).filterNot { token -> containsToken(translation, token) }

    /**
     * Puts back values the model distorted, when the distortion is unambiguous:
     * - a code written in Cyrillic or with one letter wrong ("АБС-123" → "ABC-123", "ІБАН" → "IBAN",
     *   "ESEL" → "PESEL", "SSC" → "SSCC");
     * - numbers/times when exactly as many unknown numbers appear as are missing ("10:00" → "22:00").
     * Anything else stays for [missing] to report.
     */
    fun repair(source: String, translation: String): String {
        var result = translation
        val lost = missing(source, result)
        if (lost.isEmpty()) return result

        // Codes: a word of the translation that is the same code transliterated or with one edit.
        for (token in lost.filter { t -> t.any { it.isLetter() } }) {
            val candidate = wordRegex.findAll(result).map { it.value }.firstOrNull { word ->
                word.length >= 2 && !containsToken(source, word) && (
                    toLatin(word).equals(token, ignoreCase = true) ||
                        (word.all { it.isUpperCase() || !it.isLetter() } && editDistanceAtMostOne(toLatin(word), token))
                    )
            } ?: continue
            result = replaceWord(result, candidate, token)
        }

        // Numbers: pair the missing ones with numbers that are not in the source, in order of appearance.
        val lostNumbers = missing(source, result).filter { t -> t.all { it.isDigit() || it in ":.,/-" } }
        if (lostNumbers.isNotEmpty()) {
            val sourceNumbers = protectedTokens(source).toSet()
            val strange = number.findAll(result).map { it.value.trimEnd('.', ',', ':', '/', '-') }
                .filter { it !in sourceNumbers }.toList()
            if (strange.size == lostNumbers.size) {
                for ((wrong, right) in strange.zip(lostNumbers)) result = replaceWord(result, wrong, right)
            }
        }
        return result
    }

    private val wordRegex = Regex("[\\p{L}\\d]+(?:-[\\p{L}\\d]+)*")

    private val cyrillicToLatin = mapOf(
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Ґ' to "G", 'Д' to "D", 'Е' to "E", 'Є' to "E",
        'Ж' to "ZH", 'З' to "Z", 'И' to "Y", 'І' to "I", 'Ї' to "I", 'Й' to "J", 'К' to "K", 'Л' to "L",
        'М' to "M", 'Н' to "N", 'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "C", 'Т' to "T", 'У' to "U",
        'Ф' to "F", 'Х' to "H", 'Ц' to "C", 'Ч' to "CH", 'Ш' to "SH", 'Щ' to "SH", 'Ь' to "", 'Ю' to "JU",
        'Я' to "JA",
    )

    private fun toLatin(word: String): String = buildString {
        for (c in word) {
            val up = c.uppercaseChar()
            append(cyrillicToLatin[up] ?: up.toString())
        }
    }.replace("С", "C")

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        val x = a.uppercase()
        val y = b.uppercase()
        if (kotlin.math.abs(x.length - y.length) > 1 || minOf(x.length, y.length) < 2) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < x.length && j < y.length) {
            if (x[i] == y[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                x.length > y.length -> i++
                x.length < y.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (x.length - i) + (y.length - j) <= 1
    }

    private fun replaceWord(text: String, word: String, replacement: String): String {
        var from = 0
        while (true) {
            val i = text.indexOf(word, from)
            if (i < 0) return text
            val before = text.getOrNull(i - 1)
            val after = text.getOrNull(i + word.length)
            if ((before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetterOrDigit())) {
                return text.substring(0, i) + replacement + text.substring(i + word.length)
            }
            from = i + 1
        }
    }

    private fun containsToken(text: String, token: String): Boolean {
        var from = 0
        while (true) {
            val i = text.indexOf(token, from)
            if (i < 0) return false
            val before = text.getOrNull(i - 1)
            val after = text.getOrNull(i + token.length)
            // "12" must not be satisfied by "120"; "HR" not by "HRK".
            val boundaryBefore = before == null || !before.isLetterOrDigit()
            val boundaryAfter = after == null || !after.isLetterOrDigit()
            if (boundaryBefore && boundaryAfter) return true
            from = i + 1
        }
    }
}
