package com.blacklaunch.launcher.search

/** Запит, підготовлений один раз для порівняння з багатьма назвами. */
class PreparedQuery(query: String) {
    val text: String = SearchNormalizer.normalize(query)
    val words: List<String> = SearchNormalizer.words(text)

    // Кириличний запит до латинської назви: «телеграм» → «telegram».
    val latin: String? = if (Transliteration.hasCyrillic(text)) Transliteration.toLatin(text) else null
    val latinWords: List<String>? = latin?.let(SearchNormalizer::words)
    val skeleton: String = Phonetic.skeleton(text)

    val isEmpty: Boolean get() = text.isEmpty()
}

/**
 * Оцінка збігу назв із запитом — спільна для програм, налаштувань, ярликів і контактів.
 * Порядок: точний збіг > початок назви > початок слова > усі слова > частковий збіг;
 * далі транслітерація й схоже звучання.
 */
object NameMatcher {
    const val EXACT = 100
    const val PREFIX = 80
    const val WORD_PREFIX = 60
    const val ALL_WORDS = 55
    const val CONTAINS = 40
    private const val FUZZY = 15
    private const val FUZZY_RANGE = 10
    private const val FUZZY_MIN_LENGTH = 4
    private const val FUZZY_THRESHOLD = 0.75

    fun score(names: List<IndexedName>, query: PreparedQuery): Int {
        if (query.isEmpty || names.isEmpty()) return 0
        return names.maxOf { name ->
            val direct = direct(name, query.text, query.words)
            val latin = if (query.latin != null && query.latinWords != null) {
                direct(name, query.latin, query.latinWords) + AppSearchEngine.TRANSLIT_BONUS
            } else {
                0
            }
            maxOf(direct, latin, fuzzy(name, query.skeleton))
        }
    }

    private fun direct(name: IndexedName, q: String, qWords: List<String>): Int {
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
}
