package com.offlinekeyboard.speech

import android.os.Handler
import android.os.Looper
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.speech.EngineAvailability
import com.offlinekeyboard.core.speech.SpeechEngine
import com.offlinekeyboard.core.speech.SpeechListener
import com.offlinekeyboard.core.speech.SpeechSession
import com.offlinekeyboard.models.ModelStore
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.Executors

/**
 * On-device recognition with Vosk (Kaldi). Audio is read from the microphone into memory by
 * Vosk's SpeechService and never written to storage; no network is involved.
 */
class VoskSpeechEngine(private val models: ModelStore) : SpeechEngine {

    override val name = "Vosk"

    private val loader = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // Accessed only on the loader thread.
    private var loadedLanguage: Language? = null
    private var loadedModel: Model? = null

    override fun availability(language: Language): EngineAvailability =
        if (models.speechModel(language) != null) EngineAvailability.READY else EngineAvailability.MODEL_MISSING

    override fun start(language: Language, listener: SpeechListener): SpeechSession {
        val session = VoskSession(listener)
        loader.execute {
            try {
                val model = modelFor(language)
                val recognizer = Recognizer(model, SAMPLE_RATE)
                val service = try {
                    SpeechService(recognizer, SAMPLE_RATE)
                } catch (e: Exception) {
                    recognizer.close()
                    throw e
                }
                main.post { session.begin(service, recognizer) }
            } catch (e: Exception) {
                main.post { session.fail(e.message ?: e.javaClass.simpleName) }
            }
        }
        return session
    }

    /** Frees the model's memory (tens to hundreds of MB); it is loaded again on the next dictation. */
    fun release() {
        loader.execute {
            loadedModel?.close()
            loadedModel = null
            loadedLanguage = null
        }
    }

    private fun modelFor(language: Language): Model {
        loadedModel?.let { if (loadedLanguage == language) return it }
        loadedModel?.close()
        loadedModel = null
        val dir = models.speechModel(language)?.dir ?: error("No speech model for $language")
        return Model(dir.absolutePath).also {
            loadedModel = it
            loadedLanguage = language
        }
    }

    private class VoskSession(private val listener: SpeechListener) : SpeechSession, RecognitionListener {
        private var service: SpeechService? = null
        private var recognizer: Recognizer? = null
        private var stopRequested = false
        private var finished = false

        fun begin(service: SpeechService, recognizer: Recognizer) {
            if (finished) {
                service.shutdown()
                recognizer.close()
                return
            }
            this.service = service
            this.recognizer = recognizer
            if (stopRequested) {
                finish()
                return
            }
            if (!service.startListening(this)) {
                fail("Microphone is busy")
                return
            }
            listener.onReady()
        }

        fun fail(message: String) {
            if (finished) return
            listener.onError(message)
            finish()
        }

        override fun stop() {
            stopRequested = true
            val s = service ?: return // still loading: begin() finishes right away
            s.stop() // delivers onFinalResult
        }

        override fun cancel() {
            service?.cancel()
            finish()
        }

        private fun finish() {
            if (finished) return
            finished = true
            service?.shutdown()
            recognizer?.close()
            service = null
            recognizer = null
            listener.onEnd()
        }

        override fun onPartialResult(hypothesis: String?) {
            if (finished) return
            val text = parse(hypothesis, "partial")
            if (text.isNotEmpty()) listener.onPartialResult(text)
        }

        override fun onResult(hypothesis: String?) {
            if (finished) return
            val text = parse(hypothesis, "text")
            if (text.isNotEmpty()) listener.onSegmentResult(text)
        }

        override fun onFinalResult(hypothesis: String?) {
            if (finished) return
            val text = parse(hypothesis, "text")
            if (text.isNotEmpty()) listener.onSegmentResult(text)
            finish()
        }

        override fun onError(exception: Exception?) = fail(exception?.message ?: "Recognition error")

        override fun onTimeout() {
            service?.stop()
        }

        private fun parse(json: String?, key: String): String =
            json?.let { runCatching { JSONObject(it).optString(key) }.getOrNull() }?.trim().orEmpty()
    }

    private companion object {
        const val SAMPLE_RATE = 16000f
    }
}
