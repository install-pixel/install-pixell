package com.blacklaunch.launcher.voice

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException

/**
 * Офлайн-розпізнавання Vosk. Модель вантажиться при першому натисканні мікрофона
 * (1–2 с, «Чекайте…») і тримається в пам'яті, доки система не попросить звільнити її.
 * Мікрофон працює лише під час слухання.
 */
class VoskEngine(private val store: VoiceModelStore) {
    private var model: Model? = null
    private var modelLanguage: String? = null
    /** Коли встановлено модель: імпортували іншу — завантажуємо нову, а не стару з пам'яті. */
    private var modelStamp = 0L
    private var service: SpeechService? = null
    private var delivered = false

    init {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
    }

    suspend fun prepare(language: String): Boolean = withContext(Dispatchers.IO) {
        val stamp = store.dir(language).lastModified()
        if (model != null && modelLanguage == language && modelStamp == stamp) return@withContext true
        releaseModel()
        try {
            val loaded = Model(store.dir(language).absolutePath)
            warmUp(loaded)
            model = loaded
            modelLanguage = language
            modelStamp = stamp
            true
        } catch (e: IOException) {
            Log.w(TAG, "Cannot load model $language", e)
            false
        }
    }

    /**
     * Велика модель перші секунди «розігрівається» (телефон підтягує її з пам'яті) і не встигає за мікрофоном —
     * початок фрази губився, хоча вже було «Слухаю». Проганяємо пів секунди тиші ще під час «Чекайте…».
     */
    private fun warmUp(loaded: Model) {
        runCatching {
            val recognizer = Recognizer(loaded, SAMPLE_RATE)
            try {
                val silence = ShortArray((SAMPLE_RATE / 2).toInt())
                recognizer.acceptWaveForm(silence, silence.size)
                recognizer.finalResult
            } finally {
                recognizer.close()
            }
        }.onFailure { Log.w(TAG, "Model warm-up failed", it) }
    }

    /** Викликати в головному потоці після [prepare]. */
    fun listen(onPartial: (String) -> Unit, onResult: (List<String>) -> Unit, onError: () -> Unit): Boolean {
        val current = model ?: return false
        cancel()
        delivered = false
        return try {
            val recognizer = Recognizer(current, SAMPLE_RATE).apply { setMaxAlternatives(ALTERNATIVES) }
            val speech = SpeechService(recognizer, SAMPLE_RATE)
            service = speech
            speech.startListening(
                object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        partial(hypothesis)?.takeIf { it.isNotBlank() }?.let(onPartial)
                    }

                    override fun onResult(hypothesis: String?) = deliver(hypothesis)

                    override fun onFinalResult(hypothesis: String?) = deliver(hypothesis)

                    override fun onError(exception: Exception?) {
                        Log.w(TAG, "Vosk error", exception)
                        finish()
                        onError()
                    }

                    override fun onTimeout() = deliver(null)

                    private fun deliver(hypothesis: String?) {
                        if (delivered) return
                        val texts = texts(hypothesis)
                        // Порожній проміжний результат (пауза до мовлення) — слухаємо далі.
                        if (texts.isEmpty() && hypothesis != null && service != null) return
                        delivered = true
                        finish()
                        onResult(texts)
                    }
                },
                TIMEOUT_MS,
            )
            true
        } catch (e: IOException) {
            Log.w(TAG, "Cannot start Vosk", e)
            false
        }
    }

    /** Завершити слухання й отримати те, що вже сказано. */
    fun stop() {
        service?.stop()
    }

    fun cancel() {
        delivered = true
        finish()
    }

    private fun finish() {
        service?.let {
            it.cancel()
            it.shutdown()
        }
        service = null
    }

    fun releaseModel() {
        cancel()
        model?.close()
        model = null
        modelLanguage = null
    }

    private fun partial(json: String?): String? = try {
        json?.let { JSONObject(it).optString("partial") }
    } catch (e: JSONException) {
        null
    }

    private fun texts(json: String?): List<String> {
        if (json == null) return emptyList()
        return try {
            val obj = JSONObject(json)
            val alternatives = obj.optJSONArray("alternatives")
            val list = if (alternatives != null) {
                (0 until alternatives.length()).map { alternatives.getJSONObject(it).optString("text") }
            } else {
                listOf(obj.optString("text"))
            }
            list.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private companion object {
        const val TAG = "VoskEngine"
        const val SAMPLE_RATE = 16000f
        const val ALTERNATIVES = 3
        const val TIMEOUT_MS = 10_000
    }
}
