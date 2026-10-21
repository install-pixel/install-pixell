package com.offlinekeyboard.translation

import android.os.Handler
import android.os.Looper
import com.offlinekeyboard.core.translation.TranslationDirection
import com.offlinekeyboard.core.translation.TranslationResult
import com.offlinekeyboard.models.ModelStore
import com.offlinekeyboard.mt.MarianPackage
import com.offlinekeyboard.mt.MarianTranslationEngine
import java.util.concurrent.Executors

/**
 * Runs offline translation on one background thread. The model of a direction is loaded on first use
 * and kept until [release] (low memory, keyboard destroyed, or the model was replaced).
 */
class TranslationController(private val models: ModelStore) {

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // Accessed only on the worker thread.
    private val engines = HashMap<TranslationDirection, MarianTranslationEngine>()

    fun isInstalled(direction: TranslationDirection): Boolean = models.translationModel(direction) != null

    /** [onResult] runs on the main thread. */
    fun translate(text: String, direction: TranslationDirection, onResult: (TranslationResult) -> Unit) {
        worker.execute {
            val result = try {
                engineFor(direction).translate(text, direction)
            } catch (e: Throwable) {
                // OutOfMemoryError included: report instead of crashing the keyboard.
                release(direction)
                TranslationResult.Failure(e.message ?: e.javaClass.simpleName)
            }
            main.post { onResult(result) }
        }
    }

    /** Loads the model in the background so the first translation is faster. */
    fun preload(direction: TranslationDirection) {
        worker.execute { runCatching { engineFor(direction) } }
    }

    fun release(direction: TranslationDirection? = null) {
        worker.execute {
            val keys = direction?.let { listOf(it) } ?: engines.keys.toList()
            for (key in keys) engines.remove(key)?.close()
        }
    }

    private fun engineFor(direction: TranslationDirection): MarianTranslationEngine {
        engines[direction]?.let { return it }
        val dir = models.translationModel(direction)?.dir ?: error("No translation model for $direction")
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        return MarianTranslationEngine(MarianPackage(dir), threads).also { engines[direction] = it }
    }
}
