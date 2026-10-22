package com.offlinekeyboard.core.translation

import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.speech.EngineAvailability

/** Every pair of the keyboard's three languages, in both directions. */
enum class TranslationDirection(val source: Language, val target: Language) {
    UK_TO_PL(Language.UK, Language.PL),
    PL_TO_UK(Language.PL, Language.UK),
    UK_TO_EN(Language.UK, Language.EN),
    EN_TO_UK(Language.EN, Language.UK),
    PL_TO_EN(Language.PL, Language.EN),
    EN_TO_PL(Language.EN, Language.PL),
    ;

    /** File-name form, e.g. "uk-pl". */
    val slug: String get() = "${source.name.lowercase()}-${target.name.lowercase()}"

    companion object {
        fun of(source: Language, target: Language): TranslationDirection? =
            entries.firstOrNull { it.source == source && it.target == target }

        /**
         * Direction for writing in [source]: towards [preferredTarget] when it is another language,
         * otherwise towards [fallbackTarget] (typically the previous source, so Укр→Pol becomes Pol→Укр).
         */
        fun forSource(source: Language, preferredTarget: Language, fallbackTarget: Language): TranslationDirection {
            val target = when {
                preferredTarget != source -> preferredTarget
                fallbackTarget != source -> fallbackTarget
                else -> if (source == Language.UK) Language.PL else Language.UK
            }
            return of(source, target)!!
        }

        private val polishLetters = "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ".toSet()
        private val polishWords = setOf(
            "nie", "się", "jest", "że", "czy", "jak", "to", "na", "do", "dla", "jestem", "mam", "proszę", "dziękuję",
            "co", "tak", "ale", "już", "albo", "będzie", "może", "tylko", "przez", "dzień", "dobry", "cześć",
        )
        private val englishWords = setOf(
            "the", "and", "is", "are", "you", "to", "of", "in", "it", "what", "how", "this", "that", "i", "we",
            "have", "will", "please", "thank", "thanks", "hello", "with", "for", "my", "your", "can", "do",
        )

        /** Language of a piece of text: Cyrillic → Ukrainian; Latin → Polish or English by letters and words. */
        fun detectLanguage(text: String): Language? {
            var cyrillic = 0
            var latin = 0
            var polishMarks = 0
            for (c in text) {
                if (!c.isLetter()) continue
                if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) cyrillic++ else latin++
                if (c in polishLetters) polishMarks++
            }
            if (cyrillic == 0 && latin == 0) return null
            if (cyrillic >= latin) return Language.UK
            val words = text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }
            val pl = polishMarks * 2 + words.count { it in polishWords }
            val en = words.count { it in englishWords }
            return if (pl >= en && pl > 0) Language.PL else Language.EN
        }

        /**
         * Direction for pasted or selected text: from its detected language to [preferredTarget], or, when the
         * text is already in that language, to [otherwise]. Null when the text has no letters.
         */
        fun detect(text: String, preferredTarget: Language = Language.PL, otherwise: Language = Language.UK): TranslationDirection? {
            val source = detectLanguage(text) ?: return null
            return forSource(source, preferredTarget, otherwise)
        }
    }
}

sealed interface TranslationResult {
    data class Success(val text: String) : TranslationResult
    data class Failure(val reason: String) : TranslationResult
}

/** Direct, on-device translation. Planned for stage 8/9; the keyboard only depends on this interface. */
interface TranslationEngine {
    fun availability(direction: TranslationDirection): EngineAvailability
    fun translate(text: String, direction: TranslationDirection): TranslationResult
}

object UnavailableTranslationEngine : TranslationEngine {
    override fun availability(direction: TranslationDirection) = EngineAvailability.NOT_IMPLEMENTED
    override fun translate(text: String, direction: TranslationDirection) =
        TranslationResult.Failure("Offline translation is not part of this build yet")
}
