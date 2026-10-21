package com.offlinekeyboard.core.translation

import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.speech.EngineAvailability

enum class TranslationDirection(val source: Language, val target: Language) {
    UK_TO_PL(Language.UK, Language.PL),
    PL_TO_UK(Language.PL, Language.UK),
    ;

    companion object {
        /** Suggested direction for the current input language; the user can always override it. */
        fun suggestedFor(inputLanguage: Language): TranslationDirection? = when (inputLanguage) {
            Language.UK -> UK_TO_PL
            Language.PL -> PL_TO_UK
            Language.EN -> null
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
