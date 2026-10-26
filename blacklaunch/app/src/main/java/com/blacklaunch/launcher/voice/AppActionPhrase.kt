package com.blacklaunch.launcher.voice

import com.blacklaunch.launcher.search.SearchNormalizer

/**
 * «Відкрий підписки в YouTube», «покажи продовжити читання в Moon Reader», «open subscriptions in YouTube»,
 * «otwórz subskrypcje w YouTube» — що відкрити (ярлик програми) і в якій програмі. Варіанти поділу — від останнього
 * прийменника до першого (назва програми може містити «в»); перевіряє той, хто знає встановлені програми.
 */
object AppActionPhrase {
    private val verbs = setOf("відкрий", "покажи", "запусти", "open", "show", "launch", "otworz", "pokaz", "uruchom")
    private val prepositions = setOf("в", "у", "на", "in", "on", "w", "na")

    /** Пари (що, програма) — оригінальними словами; порожньо — це не така фраза. */
    fun parse(text: String): List<Pair<String, String>> {
        val words = SearchNormalizer.words(SearchNormalizer.normalize(text))
        if (words.size < 4 || words.first() !in verbs) return emptyList()
        val original = text.trim().split(Regex("\\s+"))
        // Оригінальні слова, вирівняні з нормалізованими (по одному на слово).
        if (original.size != words.size) return emptyList()
        return (words.size - 2 downTo 2).filter { words[it] in prepositions }.map { at ->
            original.subList(1, at).joinToString(" ") to original.subList(at + 1, original.size).joinToString(" ")
        }
    }
}

/** Чи ярлик програми підходить під сказане: збіг слів або «останнє / продовжити» ↔ «Continue / Resume / Recent». */
object ShortcutMatch {
    private val lastWords = setOf(
        "останнє", "останню", "останній", "останнього", "продовжити", "продовж", "недавнє", "недавні",
        "last", "latest", "recent", "continue", "resume",
        "ostatnie", "ostatnia", "ostatni", "kontynuuj", "wznow", "niedawne",
    )
    private val lastLabelParts = listOf("остан", "продовж", "недавн", "continue", "resume", "recent", "last", "ostatni", "kontynu", "wznow", "niedawn")

    fun matches(what: String, label: String): Boolean {
        val asked = SearchNormalizer.words(SearchNormalizer.normalize(what))
        val name = SearchNormalizer.normalize(label)
        if (asked.isEmpty() || name.isEmpty()) return false
        if (asked.any { it in lastWords } && lastLabelParts.any { name.contains(it) }) return true
        val nameWords = SearchNormalizer.words(name)
        // Кожне сказане слово (від 3 літер — початком) є в назві ярлика.
        return asked.filter { it.length >= MIN_WORD }.ifEmpty { asked }.all { word ->
            nameWords.any { it == word || (word.length >= MIN_WORD && (it.startsWith(word.take(PREFIX)) || word.startsWith(it.take(PREFIX)))) }
        }
    }

    private const val MIN_WORD = 3
    private const val PREFIX = 4
}
