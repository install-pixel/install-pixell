package com.blacklaunch.launcher.global

import com.blacklaunch.launcher.search.SearchNormalizer

/** Збіг тексту (сповіщення, назви файлу): усі слова запиту є в тексті, у будь-якому порядку. */
object TextMatch {
    fun containsAll(text: String, query: String): Boolean {
        val words = SearchNormalizer.words(SearchNormalizer.normalize(query))
        if (words.isEmpty()) return false
        val haystack = SearchNormalizer.normalize(text)
        return words.all { it in haystack }
    }

    /** Уривок довкола першого збігу — щоб у рядку було видно, чому знайшлося. */
    fun snippet(text: String, query: String, before: Int = 20, after: Int = 60): String {
        val flat = text.replace('\n', ' ').trim()
        val first = SearchNormalizer.words(SearchNormalizer.normalize(query)).firstOrNull().orEmpty()
        val at = if (first.isEmpty()) 0 else flat.indexOf(first, ignoreCase = true).coerceAtLeast(0)
        val start = (at - before).coerceAtLeast(0)
        val end = (at + first.length + after).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }
}
