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
