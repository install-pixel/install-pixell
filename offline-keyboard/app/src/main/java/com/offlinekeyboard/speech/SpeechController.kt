package com.offlinekeyboard.speech

import com.offlinekeyboard.R
import com.offlinekeyboard.core.input.FieldInfo
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.speech.SpeechEngine
import com.offlinekeyboard.core.speech.UnavailableSpeechEngine
import com.offlinekeyboard.core.speech.VoiceDecision
import com.offlinekeyboard.core.speech.VoiceGate

/**
 * Entry point for dictation from the keyboard. This build has no recogniser yet (stage 6), so the
 * controller only explains why voice is unavailable; it never falls back to an online service.
 */
class SpeechController(private val engine: SpeechEngine = UnavailableSpeechEngine) {

    fun isOffered(field: FieldInfo): Boolean = !field.isSensitive

    /** Returns a string resource to show in the toolbar, or null when a session started. */
    fun onVoiceKey(field: FieldInfo, language: Language): Int? {
        // Microphone permission is not requested in this build; the engine check fails first.
        return when (VoiceGate.check(field, engine.availability(language), hasMicPermission = false)) {
            VoiceDecision.BLOCKED_SENSITIVE_FIELD -> R.string.voice_blocked_sensitive
            VoiceDecision.ENGINE_NOT_AVAILABLE -> R.string.voice_not_available
            VoiceDecision.MODEL_MISSING -> R.string.voice_model_missing
            VoiceDecision.PERMISSION_NEEDED -> R.string.voice_permission_needed
            VoiceDecision.START -> null
        }
    }

    fun cancel() = Unit
}
