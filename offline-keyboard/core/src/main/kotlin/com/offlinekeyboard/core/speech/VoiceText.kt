package com.offlinekeyboard.core.speech

import com.offlinekeyboard.core.layout.Language

/**
 * Turns raw recogniser output into text ready to insert. Small offline models return lower-case words
 * without punctuation, so spoken commands ("кома", "kropka", "question mark") become punctuation and
 * sentences get capital letters.
 */
object VoiceText {

    private val commands: Map<Language, List<Pair<List<String>, String>>> = mapOf(
        Language.UK to listOf(
            "крапка з комою" to ";", "знак питання" to "?", "знак оклику" to "!", "новий рядок" to "\n",
            "новий абзац" to "\n\n", "двокрапка" to ":", "крапка" to ".", "кома" to ",", "тире" to " —",
        ),
        Language.PL to listOf(
            "znak zapytania" to "?", "nowa linia" to "\n", "nowy wiersz" to "\n", "nowy akapit" to "\n\n",
            "wykrzyknik" to "!", "dwukropek" to ":", "średnik" to ";", "przecinek" to ",", "kropka" to ".",
            "myślnik" to " —",
        ),
        Language.EN to listOf(
            "question mark" to "?", "exclamation mark" to "!", "exclamation point" to "!", "full stop" to ".",
            "new paragraph" to "\n\n", "new line" to "\n", "semicolon" to ";", "colon" to ":", "comma" to ",",
            "period" to ".", "dash" to " —",
        ),
    ).mapValues { (_, list) -> list.map { (phrase, mark) -> phrase.split(" ") to mark }.sortedByDescending { it.first.size } }

    /**
     * @param textBefore text already in the field before the cursor (a few characters are enough)
     * @param commandsEnabled replace spoken punctuation words
     */
    fun format(raw: String, language: Language, textBefore: CharSequence, commandsEnabled: Boolean = true): String {
        val words = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val out = StringBuilder()
        val list = if (commandsEnabled) commands.getValue(language) else emptyList()
        var i = 0
        while (i < words.size) {
            val match = list.firstOrNull { (phrase, _) ->
                i + phrase.size <= words.size &&
                    phrase.indices.all { j -> words[i + j].equals(phrase[j], ignoreCase = true) }
            }
            if (match != null) {
                // Punctuation sticks to the previous word.
                while (out.endsWith(" ")) out.setLength(out.length - 1)
                out.append(match.second)
                if (!match.second.endsWith("\n")) out.append(' ')
                i += match.first.size
            } else {
                out.append(words[i]).append(' ')
                i++
            }
        }
        var text = out.toString().trimEnd(' ')
        text = capitalizeSentences(text, language, startsSentence(textBefore))
        val needsSpace = textBefore.isNotEmpty() && !textBefore.last().isWhitespace() &&
            text.isNotEmpty() && text[0] !in ".,;:!?\n"
        return if (needsSpace) " $text" else text
    }

    /** A new sentence starts at the beginning of the field or after . ! ? or a line break. */
    fun startsSentence(textBefore: CharSequence): Boolean {
        val trimmed = textBefore.trimEnd(' ', '\t')
        if (trimmed.isEmpty()) return true
        val last = trimmed.last()
        return last == '.' || last == '!' || last == '?' || last == '\n' || (trimmed.length < textBefore.length && last == '…')
    }

    private fun capitalizeSentences(text: String, language: Language, startCapital: Boolean): String {
        val sb = StringBuilder(text)
        var capitalizeNext = startCapital
        for (i in sb.indices) {
            val c = sb[i]
            if (c.isLetter()) {
                if (capitalizeNext) sb.setCharAt(i, c.uppercase(language.locale)[0])
                capitalizeNext = false
            } else if (c == '.' || c == '!' || c == '?' || c == '\n') {
                capitalizeNext = true
            }
        }
        return sb.toString()
    }
}
