package com.offlinekeyboard.translation

import com.offlinekeyboard.core.speech.EngineAvailability
import com.offlinekeyboard.core.translation.TranslationDirection
import com.offlinekeyboard.core.translation.TranslationEngine
import com.offlinekeyboard.core.translation.UnavailableTranslationEngine

/** Placeholder for stages 8–9; the keyboard UI will talk to translation only through this class. */
class TranslationController(private val engine: TranslationEngine = UnavailableTranslationEngine) {
    fun isAvailable(direction: TranslationDirection): Boolean =
        engine.availability(direction) == EngineAvailability.READY
}
