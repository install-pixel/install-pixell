package com.offlinekeyboard.core.dictionary

import com.offlinekeyboard.core.layout.Language
import java.util.EnumMap

/**
 * Next-word prediction: after a finished word ("дякую "), the words most likely to follow it.
 *
 * Two sources, the user's own first: [WordPairs] learned from what the user types (only while learning is
 * on), then the shipped lists `resources/dictionary/<lang>_next.txt` built from open sentences by
 * `tools/dictionary/build_bigrams.py`.
 */
object NextWords {

    private val lists = EnumMap<Language, Map<String, List<String>>>(Language::class.java)

    /** The shipped next words of [previous] (lower case), most frequent first. */
    fun base(language: Language, previous: String): List<String> = synchronized(lists) {
        lists.getOrPut(language) { load(language) }
    }[previous.lowercase(language.locale)].orEmpty()

    /** Up to [limit] predictions after [previous]: the user's own pairs seen at least twice, then the base list. */
    fun predict(language: Language, previous: String, own: WordPairs?, limit: Int = 3): List<String> {
        if (previous.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, String>()
        own?.top(language, previous, limit)?.forEach { out.putIfAbsent(it.lowercase(language.locale), it) }
        for (word in base(language, previous)) {
            if (out.size >= limit) break
            out.putIfAbsent(word.lowercase(language.locale), word)
        }
        return out.values.take(limit)
    }

    internal fun parse(text: String): Map<String, List<String>> {
        val map = HashMap<String, List<String>>()
        for (line in text.lineSequence()) {
            if (line.isEmpty() || line.startsWith("#")) continue
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            map[line.substring(0, tab)] = line.substring(tab + 1).split(' ').filter { it.isNotEmpty() }
        }
        return map
    }

    private fun load(language: Language): Map<String, List<String>> {
        val name = "/dictionary/${language.name.lowercase()}_next.txt"
        val text = NextWords::class.java.getResourceAsStream(name)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: return emptyMap()
        return parse(text)
    }
}

/**
 * Which word the user typed after which (kept only while learning is on, only on the phone): the user's own
 * next-word predictions. Each previous word keeps its [MAX_NEXT] most used followers; the least used
 * previous words make room when there are more than [maxWords].
 */
class WordPairs(private val maxWords: Int = DEFAULT_MAX_WORDS) {

    private val byLanguage = EnumMap<Language, HashMap<String, HashMap<String, Int>>>(Language::class.java)

    var version = 0L
        private set

    fun learn(language: Language, previous: String, next: String) {
        val a = previous.lowercase(language.locale)
        val b = WordRules.normalizeForStorage(next) ?: return
        if (a.isEmpty() || a.length > MAX_LENGTH) return
        if (!WordRules.fitsLanguage(a, language) || !WordRules.fitsLanguage(b, language)) return
        val map = byLanguage.getOrPut(language) { HashMap() }
        val followers = map.getOrPut(a) { HashMap() }
        val key = b.lowercase(language.locale)
        followers[key] = (followers[key] ?: 0) + 1
        if (followers.size > MAX_NEXT) {
            followers.entries.minByOrNull { it.value }?.let { followers.remove(it.key) }
        }
        if (map.size > maxWords) {
            map.entries.minByOrNull { e -> e.value.values.sum() }?.let { map.remove(it.key) }
        }
        version++
    }

    /** The followers of [previous] typed at least [MIN_COUNT] times, most used first. */
    fun top(language: Language, previous: String, limit: Int): List<String> =
        byLanguage[language]?.get(previous.lowercase(language.locale))?.entries
            ?.filter { it.value >= MIN_COUNT }
            ?.sortedByDescending { it.value }
            ?.take(limit)
            ?.map { it.key }
            .orEmpty()

    fun size(language: Language): Int = byLanguage[language]?.size ?: 0

    fun clear() {
        byLanguage.clear()
        version++
    }

    /** Replaces every pair with those of a [serialize]d set (restoring a backup). */
    fun replaceWith(text: String) {
        val other = parse(text, maxWords)
        byLanguage.clear()
        byLanguage.putAll(other.byLanguage)
        version++
    }

    /** `LANG<TAB>previous<TAB>next<TAB>count` per line. */
    fun serialize(): String = buildString {
        for ((language, map) in byLanguage) for ((a, followers) in map) for ((b, count) in followers) {
            append(language.name).append('\t').append(a).append('\t').append(b).append('\t').append(count).append('\n')
        }
    }

    companion object {
        const val DEFAULT_MAX_WORDS = 3000
        const val MAX_NEXT = 8
        const val MIN_COUNT = 2
        private const val MAX_LENGTH = 40

        fun parse(text: String, maxWords: Int = DEFAULT_MAX_WORDS): WordPairs {
            val pairs = WordPairs(maxWords)
            for (line in text.lineSequence()) {
                val parts = line.split('\t')
                if (parts.size != 4) continue
                val language = Language.entries.firstOrNull { it.name == parts[0] } ?: continue
                val count = parts[3].toIntOrNull()?.coerceAtLeast(1) ?: continue
                pairs.byLanguage.getOrPut(language) { HashMap() }.getOrPut(parts[1]) { HashMap() }[parts[2]] = count
            }
            return pairs
        }
    }
}
