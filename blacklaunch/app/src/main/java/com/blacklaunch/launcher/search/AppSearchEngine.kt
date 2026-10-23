package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.AppInfo

/**
 * Локальний пошук програм: без мережі, без автозапуску.
 * Порядок: точний збіг > початок назви > початок слова > частковий збіг.
 */
class AppSearchEngine {

    fun index(apps: List<AppInfo>): SearchIndex =
        SearchIndex(apps.map { app -> SearchIndex.Entry(app, listOf(IndexedName(app.label))) })
}

class IndexedName(raw: String, val bonus: Int = 0) {
    val text: String = SearchNormalizer.normalize(raw)
    val words: List<String> = SearchNormalizer.words(text)
}

class SearchIndex(private val entries: List<Entry>) {
    class Entry(val app: AppInfo, val names: List<IndexedName>)

    fun search(query: String): List<AppInfo> {
        val q = SearchNormalizer.normalize(query)
        if (q.isEmpty()) return emptyList()
        val qWords = SearchNormalizer.words(q)
        return entries
            .mapNotNull { entry ->
                val score = entry.names.maxOf { score(it, q, qWords) }
                if (score > 0) entry.app to score else null
            }
            .sortedWith(
                compareByDescending<Pair<AppInfo, Int>> { it.second }
                    .thenBy { it.first.label.length }
                    .thenBy { it.first.label.lowercase() },
            )
            .map { it.first }
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

    private companion object {
        const val EXACT = 100
        const val PREFIX = 80
        const val WORD_PREFIX = 60
        const val ALL_WORDS = 55
        const val CONTAINS = 40
    }
}
