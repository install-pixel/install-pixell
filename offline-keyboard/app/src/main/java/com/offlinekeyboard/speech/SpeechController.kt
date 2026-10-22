package com.offlinekeyboard.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.offlinekeyboard.R
import com.offlinekeyboard.core.input.FieldInfo
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.speech.SpeechEngine
import com.offlinekeyboard.core.speech.SpeechListener
import com.offlinekeyboard.core.speech.SpeechSession
import com.offlinekeyboard.core.speech.VoiceDecision
import com.offlinekeyboard.core.speech.VoiceGate
import com.offlinekeyboard.core.speech.VoiceText

/**
 * One dictation session at a time: start/stop/cancel, and turning recogniser output into text for the
 * field. Recording starts only from an explicit user action and stops when the keyboard is hidden.
 */
class SpeechController(private val context: Context, private val engine: SpeechEngine) {

    enum class State { IDLE, LOADING, LISTENING }

    interface Host {
        /** A few characters before the cursor, without the live (composing) dictation text. */
        fun textBeforeCursor(): CharSequence
        fun showPartial(text: String)
        /** A finished phrase, already formatted (spacing, punctuation, capitals). */
        fun commitSegment(text: String, language: Language)
        fun clearPartial()
        fun onStateChanged(state: State, language: Language)
        fun showMessage(resId: Int, detail: String? = null)
        fun requestMicPermission()
    }

    var host: Host? = null
    var state = State.IDLE
        private set

    private var session: SpeechSession? = null
    private var language = Language.UK
    private var commands = true
    private var segmentContext: CharSequence? = null

    fun isOffered(field: FieldInfo): Boolean = !field.isSensitive

    fun hasMicPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Mic button: start, or stop (keeping what was said) when already running. */
    fun toggle(field: FieldInfo, language: Language, voiceCommands: Boolean) {
        if (state != State.IDLE) {
            stop()
            return
        }
        val h = host ?: return
        when (VoiceGate.check(field, engine.availability(language), hasMicPermission())) {
            VoiceDecision.BLOCKED_SENSITIVE_FIELD -> h.showMessage(R.string.voice_blocked_sensitive)
            VoiceDecision.ENGINE_NOT_AVAILABLE -> h.showMessage(R.string.voice_not_available)
            VoiceDecision.MODEL_MISSING -> h.showMessage(R.string.voice_model_missing, languageCode(language))
            VoiceDecision.PERMISSION_NEEDED -> h.requestMicPermission()
            VoiceDecision.START -> begin(language, voiceCommands)
        }
    }

    private fun begin(language: Language, voiceCommands: Boolean) {
        this.language = language
        commands = voiceCommands
        segmentContext = null
        setState(State.LOADING)
        session = engine.start(language, listener)
    }

    fun stop() {
        session?.stop()
    }

    /** Drops the session and anything not yet committed (e.g. the keyboard was hidden). */
    fun cancel() {
        val s = session ?: return
        session = null
        s.cancel()
        host?.clearPartial()
        setState(State.IDLE)
    }

    private fun setState(new: State) {
        state = new
        host?.onStateChanged(new, language)
    }

    private val listener = object : SpeechListener {
        override fun onReady() {
            if (session != null) setState(State.LISTENING)
        }

        override fun onPartialResult(text: String) {
            val h = host ?: return
            if (session == null) return
            val ctx = segmentContext ?: h.textBeforeCursor().also { segmentContext = it }
            h.showPartial(VoiceText.format(text, language, ctx, commands))
        }

        override fun onSegmentResult(text: String) {
            val h = host ?: return
            val ctx = segmentContext ?: h.textBeforeCursor()
            segmentContext = null
            val formatted = VoiceText.format(text, language, ctx, commands)
            if (formatted.isBlank()) h.clearPartial() else h.commitSegment(formatted, language)
        }

        override fun onError(message: String) {
            host?.showMessage(R.string.voice_error, message)
        }

        override fun onEnd() {
            session = null
            host?.clearPartial()
            setState(State.IDLE)
        }
    }

    private fun languageCode(language: Language) = language.code
}
