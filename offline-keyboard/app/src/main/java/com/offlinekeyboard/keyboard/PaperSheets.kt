package com.offlinekeyboard.keyboard

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Shader

/**
 * The paper the sketch style's keys, buttons and settings cards are cut from: one big sheet per tone (the
 * lighter parchment, or the same paper in charcoal, see PaperTexture.journal), made once in the background.
 * Every piece shows the part of the sheet where it lies, so no two look alike, and a fold that crosses a key
 * is the same fold its letter bends with.
 */
object PaperSheets {

    private class Made(val journal: PaperTexture.Journal, val shader: BitmapShader)

    @Volatile private var light: Made? = null
    @Volatile private var dark: Made? = null
    private val making = HashSet<Boolean>()
    private val waiting = HashMap<Boolean, ArrayList<() -> Unit>>()

    /** The sheet as a repeating shader, or null until it is made. */
    fun shader(lightTone: Boolean): BitmapShader? = (if (lightTone) light else dark)?.shader

    /** The sheet itself (its folds), or null until it is made. */
    fun journal(lightTone: Boolean): PaperTexture.Journal? = (if (lightTone) light else dark)?.journal

    /** Makes the sheet of [lightTone] in the background (once); [ready] runs on that thread when it is there. */
    fun prepare(context: Context, lightTone: Boolean, ready: () -> Unit) {
        val metrics = context.resources.displayMetrics
        synchronized(this) {
            if ((if (lightTone) light else dark) != null) return
            waiting.getOrPut(lightTone) { ArrayList() } += ready
            if (!making.add(lightTone)) return
        }
        Thread {
            // As wide as the screen's long side (landscape too) and about half the screen tall: big enough that
            // pieces rarely meet the place where it repeats.
            val width = maxOf(metrics.widthPixels, metrics.heightPixels)
            val height = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast((420 * metrics.density).toInt())
            val journal = runCatching { PaperTexture.journal(width, height, metrics.density, lightTone, keys = true) }.getOrNull()
            val made = journal?.let { Made(it, BitmapShader(it.bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)) }
            val callbacks = synchronized(this) {
                if (lightTone) light = made else dark = made
                making.remove(lightTone)
                waiting.remove(lightTone).orEmpty()
            }
            callbacks.forEach { it() }
        }.apply { name = "paper sheet"; isDaemon = true }.start()
    }
}
