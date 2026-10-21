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
 *
 * Two kinds of requests: final translations (inserted into the field; beam search when [accurate]) and
 * previews while typing (fast greedy decoding; only the newest preview is computed, older ones are dropped).
 */
class TranslationController(private val models: ModelStore) {

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Beam search for final translations: more faithful, a little slower. */
    @Volatile
    var accurate = true

    // Accessed only on the worker thread.
    private val engines = HashMap<TranslationDirection, MarianTranslationEngine>()

    private class Preview(val text: String, val direction: TranslationDirection, val onResult: (TranslationResult) -> Unit)

    private val previewLock = Any()
    private var nextPreview: Preview? = null
    private var previewQueued = false

    fun isInstalled(direction: TranslationDirection): Boolean = models.translationModel(direction) != null

    /** Final translation; [onResult] runs on the main thread. */
    fun translate(text: String, direction: TranslationDirection, onResult: (TranslationResult) -> Unit) {
        val beams = if (accurate) MarianTranslationEngine.DEFAULT_BEAMS else 1
        worker.execute { deliver(run(text, direction, beams), onResult) }
    }

    /** Preview while typing; if several are requested quickly, only the last one is computed. */
    fun preview(text: String, direction: TranslationDirection, onResult: (TranslationResult) -> Unit) {
        synchronized(previewLock) {
            nextPreview = Preview(text, direction, onResult)
            if (previewQueued) return
            previewQueued = true
        }
        worker.execute {
            val job = synchronized(previewLock) {
                previewQueued = false
                nextPreview.also { nextPreview = null }
            } ?: return@execute
            deliver(run(job.text, job.direction, 1), job.onResult)
        }
    }

    private fun run(text: String, direction: TranslationDirection, beams: Int): TranslationResult = try {
        engineFor(direction).translate(text, direction, beams)
    } catch (e: Throwable) {
        // OutOfMemoryError included: report instead of crashing the keyboard.
        release(direction)
        TranslationResult.Failure(e.message ?: e.javaClass.simpleName)
    }

    private fun deliver(result: TranslationResult, onResult: (TranslationResult) -> Unit) {
        main.post { onResult(result) }
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
