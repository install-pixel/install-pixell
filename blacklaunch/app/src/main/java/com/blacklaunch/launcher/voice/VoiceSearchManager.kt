package com.blacklaunch.launcher.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val partial: String = "") : VoiceState
}

/**
 * Голосове введення через системний SpeechRecognizer. Слухає тільки після натискання
 * мікрофона і звільняє розпізнавач одразу після результату, помилки чи скасування.
 */
class VoiceSearchManager(
    private val context: Context,
    private val onResult: (List<String>) -> Unit,
    private val onError: (VoiceError) -> Unit,
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: String = DEFAULT_LANGUAGE) {
        release()
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(listener)
        _state.value = VoiceState.Listening()
        speech.startListening(recognizerIntent(language))
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        release()
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
        _state.value = VoiceState.Idle
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.texts().firstOrNull() ?: return
            if (_state.value is VoiceState.Listening) _state.value = VoiceState.Listening(text)
        }

        override fun onResults(results: Bundle?) {
            val texts = results.texts()
            release()
            if (texts.isEmpty()) onError(VoiceError.NO_MATCH) else onResult(texts)
        }

        override fun onError(error: Int) {
            release()
            onError(VoiceError.fromCode(error))
        }
    }

    private fun Bundle?.texts(): List<String> =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }

    companion object {
        const val DEFAULT_LANGUAGE = "uk-UA"
        private val ALLOWED_LANGUAGES = arrayListOf("uk-UA", "en-US")

        /** Також використовується для системного вікна розпізнавання, якщо сервісу немає. */
        fun recognizerIntent(language: String = DEFAULT_LANGUAGE): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажіть назву програми")
                // Android 14+: розпізнавач сам визначає, українська чи англійська.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, ALLOWED_LANGUAGES)
                }
            }
    }
}

enum class VoiceError(val message: String) {
    NO_MATCH("Не вдалося розпізнати. Спробуйте ще раз"),
    NETWORK("Розпізнавання потребує інтернету або завантаженого мовного пакета"),
    PERMISSION("Немає доступу до мікрофона"),
    LANGUAGE("Українська мова недоступна для розпізнавання на цьому телефоні"),
    BUSY("Розпізнавання зайняте. Спробуйте за мить"),
    OTHER("Помилка голосового введення");

    companion object {
        fun fromCode(code: Int): VoiceError = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> NO_MATCH
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> NETWORK
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> PERMISSION
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> BUSY
            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> LANGUAGE
            else -> OTHER
        }

        // Константи з API 31; значення фіксовані, тож працюють і на старіших версіях.
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
