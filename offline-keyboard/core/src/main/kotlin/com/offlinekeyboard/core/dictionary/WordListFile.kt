package com.offlinekeyboard.core.dictionary

import com.offlinekeyboard.core.layout.Language

/**
 * The user's own words in a file, to move them between phones or add a list made elsewhere.
 *
 * * Plain text: one word per line; the words go to the language chosen when importing. Empty lines and
 *   lines starting with `#` are skipped.
 * * JSON: an object with a list of words per language code, `{"uk": ["слово"], "pl": ["słowo"], "en": []}`.
 *
 * Export always writes JSON, most used words first.
 */
object WordListFile {

    data class Result(val added: Int, val skipped: Int)

    fun parse(text: String, plainTextLanguage: Language): Map<Language, List<String>> {
        val body = text.removePrefix("﻿").trim()
        if (!body.startsWith("{")) {
            val words = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
            return mapOf(plainTextLanguage to words)
        }
        val result = LinkedHashMap<Language, List<String>>()
        for (match in LIST.findAll(body)) {
            val language = Language.entries.firstOrNull { it.name.equals(match.groupValues[1], ignoreCase = true) } ?: continue
            result[language] = result[language].orEmpty() + STRING.findAll(match.groupValues[2]).map { unescape(it.groupValues[1]) }.toList()
        }
        return result
    }

    /** Adds the words as if each was typed a few times (as a word added by hand). */
    fun import(dictionary: UserDictionary, words: Map<Language, List<String>>, now: Long): Result {
        var added = 0
        var skipped = 0
        for ((language, list) in words) for (word in list) {
            if (dictionary.learn(language, word, now, weight = WEIGHT)) added++ else skipped++
        }
        return Result(added, skipped)
    }

    fun toJson(dictionary: UserDictionary): String = buildString {
        append("{\n")
        Language.entries.forEachIndexed { i, language ->
            append("  \"").append(language.name.lowercase()).append("\": [")
            append(dictionary.entries(language).joinToString(", ") { "\"" + escape(it.word) + "\"" })
            append(if (i < Language.entries.lastIndex) "],\n" else "]\n")
        }
        append("}\n")
    }

    private const val WEIGHT = 5
    private val LIST = Regex("\"([A-Za-z]+)\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
    private val STRING = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun unescape(s: String): String {
        if ('\\' !in s) return s
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) { out.append(c); i++; continue }
            when (val n = s[i + 1]) {
                'u' -> if (i + 5 < s.length) {
                    s.substring(i + 2, i + 6).toIntOrNull(16)?.let { out.append(it.toChar()) }
                    i += 6
                    continue
                } else out.append(n)
                'n' -> out.append('\n')
                't' -> out.append('\t')
                else -> out.append(n)
            }
            i += 2
        }
        return out.toString()
    }
}
