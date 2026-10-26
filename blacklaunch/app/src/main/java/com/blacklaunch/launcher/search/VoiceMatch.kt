package com.blacklaunch.launcher.search

/**
 * Офлайн-модель не знає назв на кшталт «Spotify» і чує найближчі українські слова — «спорт фай», «спот і фай».
 * Тут порівнюємо звучання м'якше, ніж у звичайному пошуку: слова разом, окремо ще й за приголосними
 * (модель найчастіше плутає голосні). Лише для голосу.
 */
object VoiceMatch {
    private const val VOWELS = "aeiouy"

    /** Наскільки сказане [heard] схоже на назву [name]: 0..1. */
    fun similarity(heard: String, name: String): Double {
        val h = Phonetic.skeleton(SearchNormalizer.normalize(heard).replace(" ", ""))
        val n = Phonetic.skeleton(SearchNormalizer.normalize(name).replace(" ", ""))
        if (h.length < MIN_LENGTH || n.length < MIN_LENGTH) return 0.0
        val full = Phonetic.similarity(h, n)
        val hc = h.filter { it !in VOWELS }
        val nc = n.filter { it !in VOWELS }
        // За приголосними — лише коли їх достатньо, інакше «ютуб» ≈ будь-що на «т…б».
        val consonants = if (hc.length >= MIN_CONSONANTS && nc.length >= MIN_CONSONANTS) Phonetic.similarity(hc, nc) * CONSONANT_WEIGHT else 0.0
        return maxOf(full, consonants)
    }

    /** Найближча назва серед [candidates], якщо вона впевнено краща за решту; інакше null. */
    fun <T> best(heard: String, candidates: List<T>, names: (T) -> List<String>): T? {
        val scored = candidates.map { c -> c to (names(c).maxOfOrNull { similarity(heard, it) } ?: 0.0) }
            .filter { it.second >= THRESHOLD }
            .sortedByDescending { it.second }
        val top = scored.firstOrNull() ?: return null
        val second = scored.getOrNull(1)?.second ?: 0.0
        return if (top.second - second >= MARGIN) top.first else null
    }

    private const val MIN_LENGTH = 4
    private const val MIN_CONSONANTS = 3
    private const val CONSONANT_WEIGHT = 0.95
    private const val THRESHOLD = 0.72
    private const val MARGIN = 0.08
}
