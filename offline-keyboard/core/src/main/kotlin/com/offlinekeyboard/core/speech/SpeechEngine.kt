package com.offlinekeyboard.core.speech

import com.offlinekeyboard.core.input.FieldInfo
import com.offlinekeyboard.core.layout.Language

enum class EngineAvailability {
    READY,
    /** Engine is built in but the language model files are not installed on the device. */
    MODEL_MISSING,
    /** No engine is integrated yet in this build. */
    NOT_IMPLEMENTED,
}

/** Callbacks arrive on the main thread. */
interface SpeechListener {
    /** Model loaded and the microphone is recording. */
    fun onReady() {}

    /** Current, not yet final words of the phrase being spoken. */
    fun onPartialResult(text: String)

    /** A finished phrase (after a pause, or when the session is stopped). */
    fun onSegmentResult(text: String)

    fun onError(message: String)

    /** The session is over (stopped, cancelled or failed); no more callbacks follow. */
    fun onEnd() {}
}

interface SpeechSession {
    /** Finish and deliver the final result. */
    fun stop()

    /** Abort; nothing is inserted. */
    fun cancel()
}

/**
 * A fully on-device recogniser. Implementations must never fall back to a network service.
 * Swapping engines (Vosk, whisper.cpp, ...) must not require keyboard UI changes.
 */
interface SpeechEngine {
    val name: String
    fun availability(language: Language): EngineAvailability
    fun start(language: Language, listener: SpeechListener): SpeechSession?
}

/** Placeholder until stage 6 integrates a real offline engine. */
object UnavailableSpeechEngine : SpeechEngine {
    override val name = "none"
    override fun availability(language: Language) = EngineAvailability.NOT_IMPLEMENTED
    override fun start(language: Language, listener: SpeechListener): SpeechSession? = null
}

enum class VoiceDecision { START, BLOCKED_SENSITIVE_FIELD, ENGINE_NOT_AVAILABLE, MODEL_MISSING, PERMISSION_NEEDED }

object VoiceGate {
    /** Whether dictation may start now. Sensitive fields always win, even if everything else is ready. */
    fun check(field: FieldInfo, availability: EngineAvailability, hasMicPermission: Boolean): VoiceDecision = when {
        field.isSensitive -> VoiceDecision.BLOCKED_SENSITIVE_FIELD
        availability == EngineAvailability.NOT_IMPLEMENTED -> VoiceDecision.ENGINE_NOT_AVAILABLE
        availability == EngineAvailability.MODEL_MISSING -> VoiceDecision.MODEL_MISSING
        !hasMicPermission -> VoiceDecision.PERMISSION_NEEDED
        else -> VoiceDecision.START
    }
}
