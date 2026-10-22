package com.offlinekeyboard.core.dictionary

import com.offlinekeyboard.core.layout.Language
import java.util.EnumMap

/**
 * About ten thousand common words per language shipped with the app (`resources/dictionary/<lang>.txt`,
 * most frequent first; built by `tools/dictionary/build_base.py`), so suggestions and autocorrect work
 * before the user's own dictionary has learned anything. Read-only; the user's words rank above these.
 */
object BaseDictionary {

    private val cache = EnumMap<Language, List<String>>(Language::class.java)
    private val keys = EnumMap<Language, List<String>>(Language::class.java)

    private val known = EnumMap<Language, Set<String>>(Language::class.java)

    fun words(language: Language): List<String> = synchronized(cache) {
        cache.getOrPut(language) { load(language, "") }
    }

    /**
     * Whether [word] (lower case) is a correct word: one of [words] or one of the less common words in
     * `<lang>_known.txt` (about 15-25 thousand forms), which are never suggested but never "corrected".
     */
    fun isKnown(language: Language, word: String): Boolean = synchronized(known) {
        known.getOrPut(language) {
            (words(language) + load(language, "_known")).mapTo(HashSet()) { it.lowercase(language.locale) }
        }
    }.let { word in it || word.replace('ʼ', '\'').replace('’', '\'') in it }

    /** [words] in lower case with one apostrophe, computed once (suggestions run on every key). */
    private fun keys(language: Language): List<String> = synchronized(keys) {
        keys.getOrPut(language) { words(language).map { matchKey(it, language) } }
    }

    /** Up to [limit] words starting with [prefix] (case-insensitive, any apostrophe), in frequency order. */
    fun suggestions(language: Language, prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val key = matchKey(prefix, language)
        val words = words(language)
        return keys(language).asSequence()
            .withIndex()
            .filter { (_, k) -> k.startsWith(key) && k != key }
            .take(limit)
            .map { WordRules.applyCase(words[it.index], prefix, language) }
            .toList()
    }

    private fun matchKey(word: String, language: Language) =
        word.lowercase(language.locale).replace('ʼ', '\'').replace('’', '\'')

    private fun load(language: Language, suffix: String): List<String> {
        val name = "/dictionary/${language.name.lowercase()}$suffix.txt"
        val text = BaseDictionary::class.java.getResourceAsStream(name)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: return emptyList()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinctBy { it.lowercase(language.locale) }
            .toList()
    }
}
