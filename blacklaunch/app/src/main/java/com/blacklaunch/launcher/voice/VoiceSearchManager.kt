package com.blacklaunch.launcher.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.blacklaunch.launcher.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val partial: String = "") : VoiceState
}

enum class OfflineStatus { INSTALLED, DOWNLOADING, DOWNLOADABLE, UNSUPPORTED, UNKNOWN }

/** Мови розпізнавання: мова телефону (українська чи польська), інакше англійська. */
object VoiceLanguages {
    const val UKRAINIAN = "uk-UA"
    const val POLISH = "pl-PL"
    const val ENGLISH = "en-US"
    val ALL = listOf(UKRAINIAN, POLISH, ENGLISH)

    fun forLocale(locale: Locale = Locale.getDefault()): String = when (locale.language) {
        "uk" -> UKRAINIAN
        "pl" -> POLISH
        else -> ENGLISH
    }

    /** Мови, для яких має сенс показувати офлайн-статус: мова телефону та англійська. */
    fun relevant(locale: Locale = Locale.getDefault()): List<String> = listOf(forLocale(locale), ENGLISH).distinct()
}

/**
 * Голосове введення через системний SpeechRecognizer. Слухає тільки після натискання
 * мікрофона і звільняє розпізнавач одразу після результату, помилки чи скасування.
 *
 * Онлайн: основний розпізнавач; якщо мова недоступна — ще раз без автовизначення,
 * потім англійською. Без мережі — вбудований офлайн-розпізнавач із завантаженою мовою.
 */
class VoiceSearchManager(
    private val context: Context,
    private val onResult: (List<String>) -> Unit,
    private val onMessage: (Int) -> Unit,
) {
    /**
     * Спроба розпізнавання. [onDevice] — вбудований офлайн-розпізнавач Android (API 31+):
     * саме для нього завантажуються мовні пакети, основний розпізнавач Google працює через мережу.
     */
    private data class Attempt(val language: String, val detectLanguage: Boolean, val onDevice: Boolean = false)

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var downloadRecognizer: SpeechRecognizer? = null
    private var attempts: List<Attempt> = emptyList()
    private var current: Attempt? = null
    private var language: String = VoiceLanguages.forLocale()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context) || onDeviceAvailable()

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(language: String = VoiceLanguages.forLocale()) {
        this.language = language
        if (!hasInternet() && onDeviceAvailable()) {
            startOffline()
            return
        }
        attempts = buildList {
            add(Attempt(language, detectLanguage = true))
            add(Attempt(language, detectLanguage = false))
            if (language != VoiceLanguages.ENGLISH) add(Attempt(VoiceLanguages.ENGLISH, detectLanguage = false))
        }
        nextAttempt()
    }

    /** Без мережі: вбудований розпізнавач із завантаженою мовою (мова телефону, потім англійська). */
    private fun startOffline() {
        _state.value = VoiceState.Listening()
        val preferred = (listOf(language, VoiceLanguages.ENGLISH) + VoiceLanguages.ALL).distinct()
        checkOffline(preferred) { status ->
            if (_state.value !is VoiceState.Listening) return@checkOffline // скасовано, поки перевіряли
            val installed = preferred.filter { status[it] == OfflineStatus.INSTALLED }
            // До Android 13 статус невідомий — просто пробуємо по черзі.
            val candidates = installed.ifEmpty {
                preferred.filter { status[it] == OfflineStatus.UNKNOWN }
            }
            if (candidates.isEmpty()) {
                release()
                onMessage(R.string.voice_offline_no_pack)
                return@checkOffline
            }
            if (candidates.first() != language) onMessage(fallbackMessage(candidates.first()))
            attempts = candidates.map { Attempt(it, detectLanguage = false, onDevice = true) }
            nextAttempt()
        }
    }

    private fun hasInternet(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun nextAttempt() {
        val attempt = attempts.firstOrNull() ?: return
        attempts = attempts.drop(1)
        release()
        val speech = if (attempt.onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = speech
        current = attempt
        speech.setRecognitionListener(listener)
        _state.value = VoiceState.Listening()
        speech.startListening(recognizerIntent(context, attempt.language, attempt.detectLanguage))
    }

    @StringRes
    private fun fallbackMessage(to: String): Int =
        if (to == VoiceLanguages.ENGLISH) R.string.voice_fallback_english else R.string.voice_fallback_other

    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        attempts = emptyList()
        current = null
        recognizer?.cancel()
        release()
    }

    fun destroy() {
        cancel()
        downloadRecognizer?.destroy()
        downloadRecognizer = null
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
        _state.value = VoiceState.Idle
    }

    /** Офлайн-статус мов (Android 13+). На старіших версіях — UNKNOWN. */
    fun checkOffline(languages: List<String>, onStatus: (Map<String, OfflineStatus>) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onStatus(languages.associateWith { OfflineStatus.UNKNOWN })
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onStatus(languages.associateWith { OfflineStatus.UNSUPPORTED })
            return
        }
        checkOfflineApi33(languages, onStatus)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkOfflineApi33(languages: List<String>, onStatus: (Map<String, OfflineStatus>) -> Unit) {
        val probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        probe.checkRecognitionSupport(
            recognizerIntent(context, languages.first(), detectLanguage = false),
            ContextCompat.getMainExecutor(context),
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    probe.destroy()
                    onStatus(languages.associateWith { statusOf(it, support) })
                }

                override fun onError(error: Int) {
                    probe.destroy()
                    onStatus(languages.associateWith { OfflineStatus.UNKNOWN })
                }
            },
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun statusOf(language: String, support: RecognitionSupport): OfflineStatus {
        fun List<String>.has() = any { it.equals(language, true) || it.substringBefore('-').equals(language.substringBefore('-'), true) }
        return when {
            support.installedOnDeviceLanguages.has() -> OfflineStatus.INSTALLED
            support.pendingOnDeviceLanguages.has() -> OfflineStatus.DOWNLOADING
            support.supportedOnDeviceLanguages.has() -> OfflineStatus.DOWNLOADABLE
            else -> OfflineStatus.UNSUPPORTED
        }
    }

    /** Просить систему завантажити офлайн-модель мови (Android 13+). */
    fun downloadOffline(language: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return
        downloadRecognizer?.destroy()
        downloadRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
            it.triggerModelDownload(recognizerIntent(context, language, detectLanguage = false))
        }
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
            attempts = emptyList()
            release()
            if (texts.isEmpty()) onMessage(R.string.voice_error_no_match) else onResult(texts)
        }

        override fun onError(error: Int) {
            val failed = current
            // Мережа зникла посеред розмови — переходимо на вбудований офлайн-розпізнавач.
            if (isNetworkError(error) && failed != null && !failed.onDevice && onDeviceAvailable()) {
                attempts = emptyList()
                startOffline()
                return
            }
            val next = attempts.firstOrNull()
            if ((isLanguageError(error) || failed?.onDevice == true && error == SpeechRecognizer.ERROR_CLIENT) && next != null) {
                if (next.language != failed?.language) onMessage(fallbackMessage(next.language))
                nextAttempt()
                return
            }
            attempts = emptyList()
            release()
            onMessage(errorMessage(error))
        }
    }

    private fun Bundle?.texts(): List<String> =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }

    companion object {
        // Константи з API 31; значення фіксовані, тож працюють і на старіших версіях.
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        private fun isLanguageError(code: Int) =
            code == ERROR_LANGUAGE_NOT_SUPPORTED || code == ERROR_LANGUAGE_UNAVAILABLE

        private fun isNetworkError(code: Int) =
            code == SpeechRecognizer.ERROR_NETWORK || code == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                code == SpeechRecognizer.ERROR_SERVER

        @StringRes
        fun errorMessage(code: Int): Int = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_error_no_match
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> R.string.voice_error_network
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_error_permission
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_error_busy
            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> R.string.voice_error_language
            else -> R.string.voice_error_other
        }

        /** Також використовується для системного вікна розпізнавання, якщо сервісу немає. */
        fun recognizerIntent(
            context: Context,
            language: String = VoiceLanguages.forLocale(),
            detectLanguage: Boolean = true,
        ): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_prompt))
            // Android 14+: розпізнавач сам визначає, українська, польська чи англійська.
            if (detectLanguage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    ArrayList(VoiceLanguages.ALL),
                )
            }
        }
    }
}
