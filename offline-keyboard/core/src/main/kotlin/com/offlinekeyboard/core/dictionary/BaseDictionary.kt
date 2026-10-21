package com.offlinekeyboard.core.dictionary

import com.offlinekeyboard.core.layout.Language
import java.util.EnumMap

/**
 * A few hundred common words per language shipped with the app (`resources/dictionary/<lang>.txt`,
 * most frequent first), so suggestions work before the user's own dictionary has learned anything.
 * Read-only; the user's words always rank above these.
 */
object BaseDictionary {

    private val cache = EnumMap<Language, List<String>>(Language::class.java)

    fun words(language: Language): List<String> = synchronized(cache) {
        cache.getOrPut(language) { load(language) }
    }

    /** Up to [limit] words starting with [prefix] (case-insensitive, any apostrophe), in frequency order. */
    fun suggestions(language: Language, prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val key = matchKey(prefix, language)
        return words(language).asSequence()
            .filter { word -> matchKey(word, language).let { it.startsWith(key) && it != key } }
            .take(limit)
            .map { WordRules.applyCase(it, prefix, language) }
            .toList()
    }

    private fun matchKey(word: String, language: Language) =
        word.lowercase(language.locale).replace('ʼ', '\'').replace('’', '\'')

    private fun load(language: Language): List<String> {
        val name = "/dictionary/${language.name.lowercase()}.txt"
        val text = BaseDictionary::class.java.getResourceAsStream(name)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: return emptyList()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinctBy { it.lowercase(language.locale) }
            .toList()
    }
}
