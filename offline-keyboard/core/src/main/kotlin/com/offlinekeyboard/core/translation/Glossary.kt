package com.offlinekeyboard.core.translation

import com.offlinekeyboard.core.layout.Language

/**
 * The user's own corrections for translation: terms (and whole phrases) with the translation they must
 * get, e.g. warehouse words the small models translate wrongly. Plain text, one entry per line:
 *
 * ```
 * # українська | polski | English
 * накладна / накладн* | list przewozowy / list* przewozow* | delivery note / delivery notes
 * палета / палет* | paleta / palet* | pallet / pallets
 * ```
 *
 * A cell may list several forms separated by "/": the first is what a translation into that language
 * uses, all of them are recognised in the text, and "*" at the end of a word matches any ending
 * (Ukrainian and Polish inflect). Empty cells are allowed.
 *
 * A sentence that is exactly an entry gets that entry's translation. Otherwise the terms in the sentence
 * are replaced by their target-language form before the model translates, and the model keeps them.
 */
class Glossary private constructor(private val entries: List<Entry>) {

    private class Entry(val cells: Map<Language, List<String>>) {
        /** The first full form (without "*") is what translations into [language] use. */
        fun output(language: Language): String? = cells[language]?.firstOrNull { '*' !in it }
    }

    val size: Int get() = entries.size

    /** Fixed translation when the whole sentence is one glossary entry. */
    fun sentence(sentence: String, direction: TranslationDirection): String? {
        val key = normalize(sentence)
        val entry = entries.firstOrNull { e ->
            e.output(direction.target) != null && e.cells[direction.source].orEmpty().any { '*' !in it && normalize(it) == key }
        } ?: return null
        var result = entry.output(direction.target)!!
        if (sentence.firstOrNull { it.isLetter() }?.isUpperCase() == true) {
            result = result.replaceFirstChar { it.uppercase(direction.target.locale) }
        }
        return result + sentence.trimEnd().takeLastWhile { it in ".!?…" }
    }

    /** [sentence] with every glossary term replaced by its form in the target language. */
    fun prepare(sentence: String, direction: TranslationDirection): String {
        var text = sentence
        for ((regex, replacement) in rules(direction)) {
            text = regex.replace(text) { m ->
                if (m.value.first().isUpperCase()) replacement.replaceFirstChar { it.uppercase(direction.target.locale) } else replacement
            }
        }
        return text
    }

    private val ruleCache = HashMap<TranslationDirection, List<Pair<Regex, String>>>()

    private fun rules(direction: TranslationDirection): List<Pair<Regex, String>> = synchronized(ruleCache) {
        ruleCache.getOrPut(direction) {
            entries.flatMap { e ->
                val target = e.output(direction.target) ?: return@flatMap emptyList()
                e.cells[direction.source].orEmpty().map { form -> form to target }
            }
                // Longer terms first: "list przewozowy" before "list".
                .sortedByDescending { it.first.length }
                .map { (form, target) -> termRegex(form) to target }
        }
    }

    private fun termRegex(form: String): Regex {
        val words = form.trim().split(Regex("\\s+")).joinToString("\\s+") { word ->
            if (word.endsWith("*")) Regex.escape(word.dropLast(1)) + "[\\p{L}'ʼ’-]*" else Regex.escape(word)
        }
        return Regex("(?<![\\p{L}\\d])$words(?![\\p{L}\\d])", setOf(RegexOption.IGNORE_CASE))
    }

    companion object {
        val EMPTY = Glossary(emptyList())

        private const val EDGE = " \t.!?…,;:\"«»„”“()"

        private fun normalize(text: String) = text.lowercase()
            .replace('’', '\'').replace('ʼ', '\'')
            .trim { it in EDGE }
            .replace(Regex("\\s+"), " ")

        /** Columns are always Ukrainian, Polish, English. Malformed lines are skipped, never fatal. */
        fun parse(text: String): Glossary {
            val languages = listOf(Language.UK, Language.PL, Language.EN)
            val entries = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && '|' in it }
                .map { line ->
                    val cells = line.split('|').map { cell ->
                        cell.split('/').map { it.trim() }.filter { it.isNotEmpty() && it != "*" }
                    }
                    Entry(languages.zip(cells).filter { it.second.isNotEmpty() }.toMap())
                }
                .filter { it.cells.size >= 2 }
                .toList()
            return Glossary(entries)
        }
    }
}
