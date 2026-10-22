package com.offlinekeyboard.core.dictionary

import com.offlinekeyboard.core.layout.Language
import java.util.EnumMap

/**
 * Words the user typed, per language, kept only on the device. Stores single words with a use count;
 * no sentences, order of typing or timestamps of individual messages.
 */
class UserDictionary(private val maxWordsPerLanguage: Int = DEFAULT_MAX_WORDS) {

    data class Entry(val word: String, val count: Int, val lastUsed: Long)

    private val byLanguage = EnumMap<Language, HashMap<String, Entry>>(Language::class.java)

    /** Incremented on every change; lets callers know when to save. */
    var version: Long = 0
        private set

    fun size(language: Language): Int = byLanguage[language]?.size ?: 0

    fun entries(language: Language): List<Entry> =
        byLanguage[language]?.values?.sortedWith(compareByDescending<Entry> { it.count }.thenBy { it.word }).orEmpty()

    /** Records one use of [word]. Returns false when the word is not something worth learning. */
    fun learn(language: Language, word: String, now: Long, weight: Int = 1): Boolean {
        val clean = WordRules.normalizeForStorage(word) ?: return false
        val map = byLanguage.getOrPut(language) { HashMap() }
        val key = clean.lowercase(language.locale)
        val old = map[key]
        map[key] = Entry(old?.word ?: clean, (old?.count ?: 0) + weight, now)
        if (map.size > maxWordsPerLanguage) evict(map)
        version++
        return true
    }

    fun contains(language: Language, word: String): Boolean =
        byLanguage[language]?.containsKey(word.lowercase(language.locale)) == true

    /** How many times [word] was typed (0 when unknown). */
    fun count(language: Language, word: String): Int = byLanguage[language]?.get(word.lowercase(language.locale))?.count ?: 0

    fun remove(language: Language, word: String): Boolean {
        val removed = byLanguage[language]?.remove(word.lowercase(language.locale)) != null
        if (removed) version++
        return removed
    }

    fun clear() {
        byLanguage.clear()
        version++
    }

    /**
     * Up to [limit] completions of [prefix] (case-insensitive), most used first. The typed word itself
     * is skipped. Results follow the case the user started with ("Wa" → "Warszawa", "WA" → "WARSZAWA").
     */
    fun suggestions(language: Language, prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val map = byLanguage[language] ?: return emptyList()
        val lowerPrefix = prefix.lowercase(language.locale)
        return map.entries.asSequence()
            .filter { (key, _) -> key.startsWith(lowerPrefix) && key != lowerPrefix }
            .map { it.value }
            .sortedWith(compareByDescending<Entry> { it.count }.thenByDescending { it.lastUsed }.thenBy { it.word })
            .take(limit)
            .map { WordRules.applyCase(it.word, prefix, language) }
            .toList()
    }

    /**
     * Suggestions for the toolbar: the user's own words first, then common words from [BaseDictionary],
     * without duplicates.
     */
    fun suggestionsWithBase(language: Language, prefix: String, limit: Int = 3): List<String> {
        val own = suggestions(language, prefix, limit)
        if (own.size >= limit) return own
        val seen = own.mapTo(HashSet()) { it.lowercase(language.locale) }
        val base = BaseDictionary.suggestions(language, prefix, limit + own.size)
            .filter { seen.add(it.lowercase(language.locale)) }
        return own + base.take(limit - own.size)
    }

    private fun evict(map: HashMap<String, Entry>) {
        val toRemove = map.size - maxWordsPerLanguage
        map.entries.sortedWith(compareBy<Map.Entry<String, Entry>> { it.value.count }.thenBy { it.value.lastUsed })
            .take(toRemove)
            .forEach { map.remove(it.key) }
    }

    /** Plain text: `LANG<TAB>word<TAB>count<TAB>lastUsed` per line. */
    fun serialize(): String = buildString {
        for ((language, map) in byLanguage) {
            for (e in map.values) {
                append(language.name).append('\t').append(e.word).append('\t')
                    .append(e.count).append('\t').append(e.lastUsed).append('\n')
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_WORDS = 20_000

        fun parse(text: String, maxWordsPerLanguage: Int = DEFAULT_MAX_WORDS): UserDictionary {
            val dict = UserDictionary(maxWordsPerLanguage)
            for (line in text.lineSequence()) {
                val parts = line.split('\t')
                if (parts.size != 4) continue
                val language = Language.entries.firstOrNull { it.name == parts[0] } ?: continue
                val count = parts[2].toIntOrNull() ?: continue
                val lastUsed = parts[3].toLongOrNull() ?: continue
                val word = WordRules.normalizeForStorage(parts[1]) ?: continue
                val map = dict.byLanguage.getOrPut(language) { HashMap() }
                map[word.lowercase(language.locale)] = Entry(word, count.coerceAtLeast(1), lastUsed)
            }
            return dict
        }
    }
}

object WordRules {
    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 40
    private val apostrophes = setOf('\'', 'ʼ', '’')

    private fun isWordChar(c: Char) = c.isLetter() || c in apostrophes || c == '-' ||
        Character.getType(c) == Character.NON_SPACING_MARK.toInt()

    /** The word being typed right before the cursor ("" if the cursor follows a space or punctuation). */
    fun trailingWord(textBefore: CharSequence): String {
        var i = textBefore.length
        while (i > 0 && isWordChar(textBefore[i - 1])) i--
        // A digit glued to the word (e.g. "abc1") means it is a code, not a word.
        if (i > 0 && textBefore[i - 1].isLetterOrDigit()) return ""
        return textBefore.substring(i).trimStart('-', '\'', 'ʼ', '’')
    }

    /** True if the text right after the cursor continues a word (then no suggestions/learning). */
    fun continuesWord(textAfter: CharSequence): Boolean = textAfter.isNotEmpty() && textAfter[0].isLetterOrDigit()

    /**
     * Returns the form to store, or null if [word] should not be learned: too short or long, no letters,
     * or not made of letters only. Capitalised-only-at-start words are stored lower-case (sentence start);
     * acronyms and mixed-case words (iPhone, NATO) keep their case.
     */
    fun normalizeForStorage(word: String): String? {
        val w = word.trim('-', '\'', 'ʼ', '’')
        if (w.length !in MIN_LENGTH..MAX_LENGTH) return null
        if (!w.all { isWordChar(it) } || w.none { it.isLetter() }) return null
        val rest = w.substring(1)
        return if (w[0].isUpperCase() && rest.none { it.isUpperCase() }) w.lowercase() else w
    }

    fun applyCase(word: String, typedPrefix: String, language: Language): String = when {
        typedPrefix.length >= 2 && typedPrefix.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase(language.locale)
        typedPrefix.firstOrNull()?.isUpperCase() == true ->
            word.replaceFirstChar { it.uppercase(language.locale) }
        else -> word
    }
}
