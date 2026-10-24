package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.AppInfo

/**
 * Локальний пошук програм: без мережі, без автозапуску.
 * Порядок: точний збіг > початок назви > початок слова > частковий збіг;
 * назва програми > псевдонім > транслітерація.
 */
class AppSearchEngine(private val aliases: AppAliases = AppAliases()) {

    fun index(
        apps: List<AppInfo>,
        userAliases: Map<String, Set<String>> = emptyMap(),
        learned: Map<String, Map<String, Int>> = emptyMap(),
    ): SearchIndex =
        SearchIndex(
            learned = learned,
            entries = apps.map { app ->
                val label = IndexedName(app.label)
                val names = buildList {
                    add(label)
                    aliases.aliasesFor(app, userAliases).forEach { add(IndexedName(it, ALIAS_BONUS)) }
                    if (Transliteration.hasCyrillic(label.text)) {
                        add(IndexedName(Transliteration.toLatin(label.text), TRANSLIT_BONUS))
                    }
                }
                SearchIndex.Entry(app, names)
            },
        )

    companion object {
        const val ALIAS_BONUS = -5
        /** Транслітерація дає лише точні збіги та збіги за початком, без часткових. */
        const val TRANSLIT_BONUS = -50
    }
}

class IndexedName(raw: String, val bonus: Int = 0) {
    val text: String = SearchNormalizer.normalize(raw)
    val words: List<String> = SearchNormalizer.words(text)
    val skeleton: String = Phonetic.skeleton(text)
    val wordSkeletons: List<String> = words.map(Phonetic::skeleton)
}

data class ScoredApp(val app: AppInfo, val score: Int)

class SearchIndex(
    private val entries: List<Entry>,
    /** Навчання на діях: нормалізований запит → (ключ програми → кількість виборів). */
    private val learned: Map<String, Map<String, Int>> = emptyMap(),
) {
    class Entry(val app: AppInfo, val names: List<IndexedName>)

    /**
     * [launchCounts] — скільки разів запускали програму (за ключем): серед однаково
     * добрих збігів частіші програми йдуть вище.
     */
    fun search(query: String, launchCounts: Map<String, Int> = emptyMap()): List<AppInfo> =
        searchScored(query, launchCounts).map { it.app }

    fun searchScored(query: String, launchCounts: Map<String, Int> = emptyMap()): List<ScoredApp> {
        val prepared = PreparedQuery(query)
        if (prepared.isEmpty) return emptyList()
        val topics = Topics.matching(prepared.text)
        val chosen = learned[prepared.text].orEmpty()
        return entries
            .mapNotNull { entry ->
                val byName = NameMatcher.score(entry.names, prepared)
                val byTopic = if (topics.any { Topics.fits(it, entry.app) }) TOPIC else 0
                // Цю програму вже обирали на такий самий запит — вона перша.
                val byLearning = chosen[entry.app.key]?.let { LEARNED + minOf(it, LEARNED_MAX_BONUS) } ?: 0
                val score = maxOf(byName, byTopic, byLearning)
                if (score > 0) ScoredApp(entry.app, score) else null
            }
            .sortedWith(
                compareByDescending<ScoredApp> { it.score }
                    .thenByDescending { launchCounts[it.app.key] ?: 0 }
                    .thenBy { it.app.label.length }
                    .thenBy { it.app.label.lowercase() },
            )
    }

    companion object {
        private const val TOPIC = 35
        private const val LEARNED = 90
        private const val LEARNED_MAX_BONUS = 9

        /** Точний збіг назви або псевдоніма. */
        private const val CONFIDENT = NameMatcher.EXACT + AppSearchEngine.ALIAS_BONUS

        /**
         * Програма, яку можна запустити голосом без вибору: єдиний результат
         * або єдиний точний збіг назви чи псевдоніма.
         */
        fun unambiguous(results: List<ScoredApp>): AppInfo? {
            val top = results.firstOrNull() ?: return null
            if (results.size == 1) return top.app
            val second = results[1]
            return if (top.score >= CONFIDENT && second.score < CONFIDENT) top.app else null
        }
    }
}
