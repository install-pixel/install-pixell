package com.blacklaunch.launcher.search

import java.text.Normalizer
import java.util.Locale

/** Приводить назви програм і запити до спільного вигляду для порівняння. */
object SearchNormalizer {
    private val apostrophes = setOf('\'', 'ʼ', '’', '‘', '`', 'ʻ')
    private val combiningMark = Regex("\\p{Mn}")
    private val whitespace = Regex("\\s+")

    fun normalize(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
            .filterNot { it in apostrophes }
            .replace('ё', 'е')
            .replace('ґ', 'г')
            // «ł» не розкладається на «l» + діакритику, тому окремо.
            .replace('ł', 'l')
        return stripLatinDiacritics(lower)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .replace(whitespace, " ")
            .trim()
    }

    fun words(normalized: String): List<String> =
        if (normalized.isEmpty()) emptyList() else normalized.split(' ')

    // «é» → «e», але «й» і «ї» лишаються літерами, а не «и» / «і» з наголосом.
    private fun stripLatinDiacritics(text: String): String =
        text.map { ch ->
            if (ch.code in 0x00C0..0x024F) {
                Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).replace(combiningMark, "")
            } else {
                ch.toString()
            }
        }.joinToString("")
}
