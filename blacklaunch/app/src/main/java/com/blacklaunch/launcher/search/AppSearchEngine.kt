package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.AppInfo

/**
 * Локальний пошук програм: без мережі, без автозапуску.
 * Порядок: точний збіг > початок назви > початок слова > частковий збіг;
 * назва програми > псевдонім > транслітерація.
 */
class AppSearchEngine(private val aliases: AppAliases = AppAliases()) {

    fun index(apps: List<AppInfo>, userAliases: Map<String, Set<String>> = emptyMap()): SearchIndex =
        SearchIndex(
            apps.map { app ->
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

class SearchIndex(private val entries: List<Entry>) {
    class Entry(val app: AppInfo, val names: List<IndexedName>)

    /**
     * [launchCounts] — скільки разів запускали програму (за ключем): серед однаково
     * добрих збігів частіші програми йдуть вище.
     */
    fun search(query: String, launchCounts: Map<String, Int> = emptyMap()): List<AppInfo> =
        searchScored(query, launchCounts).map { it.app }

    fun searchScored(query: String, launchCounts: Map<String, Int> = emptyMap()): List<ScoredApp> {
        val q = SearchNormalizer.normalize(query)
        if (q.isEmpty()) return emptyList()
        val qWords = SearchNormalizer.words(q)
        // Кириличний запит до латинської назви: «телеграм» → «telegram».
        val qLatin = if (Transliteration.hasCyrillic(q)) Transliteration.toLatin(q) else null
        val qLatinWords = qLatin?.let(SearchNormalizer::words)
        val qSkeleton = Phonetic.skeleton(q)
        return entries
            .mapNotNull { entry ->
                val score = entry.names.maxOf { name ->
                    val direct = score(name, q, qWords)
                    val latin = if (qLatin != null && qLatinWords != null) {
                        score(name, qLatin, qLatinWords) + AppSearchEngine.TRANSLIT_BONUS
                    } else {
                        0
                    }
                    maxOf(direct, latin, fuzzy(name, qSkeleton))
                }
                if (score > 0) ScoredApp(entry.app, score) else null
            }
            .sortedWith(
                compareByDescending<ScoredApp> { it.score }
                    .thenByDescending { launchCounts[it.app.key] ?: 0 }
                    .thenBy { it.app.label.length }
                    .thenBy { it.app.label.lowercase() },
            )
    }

    private fun score(name: IndexedName, q: String, qWords: List<String>): Int {
        val base = when {
            name.text == q -> EXACT
            name.text.startsWith(q) -> PREFIX
            name.words.any { it.startsWith(q) } -> WORD_PREFIX
            qWords.size > 1 && qWords.all { qw -> name.words.any { it.startsWith(qw) } } -> ALL_WORDS
            q.length >= 2 && name.text.contains(q) -> CONTAINS
            else -> return 0
        }
        return base + name.bonus
    }

    /**
     * Найнижчий рівень: схоже звучання («спотіфай» ≈ Spotify). Лише для запитів від 4 літер
     * і схожості від 75 %, щоб не давати випадкових збігів.
     */
    private fun fuzzy(name: IndexedName, qSkeleton: String): Int {
        if (qSkeleton.length < FUZZY_MIN_LENGTH || name.skeleton.isEmpty()) return 0
        val best = (name.wordSkeletons + name.skeleton).maxOf { Phonetic.similarity(it, qSkeleton) }
        if (best < FUZZY_THRESHOLD) return 0
        return FUZZY + ((best - FUZZY_THRESHOLD) * FUZZY_RANGE / (1 - FUZZY_THRESHOLD)).toInt()
    }

    companion object {
        private const val FUZZY = 15
        private const val FUZZY_RANGE = 10
        private const val FUZZY_MIN_LENGTH = 4
        private const val FUZZY_THRESHOLD = 0.75
        private const val EXACT = 100
        private const val PREFIX = 80
        private const val WORD_PREFIX = 60
        private const val ALL_WORDS = 55
        private const val CONTAINS = 40

        /** Точний збіг назви або псевдоніма. */
        private const val CONFIDENT = EXACT + AppSearchEngine.ALIAS_BONUS

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
