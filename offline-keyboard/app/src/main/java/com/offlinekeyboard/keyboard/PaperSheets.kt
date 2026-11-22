package com.offlinekeyboard.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import com.offlinekeyboard.R

/**
 * The sketch style's papers, from the owner's photographs of real paper: the sheet the keyboard is written on
 * (crumpled black paper, or stained parchment) and the paper of the scraps laid on top (cards in the settings,
 * the touchpad, popups: a lighter crumpled black, or an old page). Loaded once in the background.
 */
object PaperSheets {

    private class Papers(val keyboard: Bitmap, val cards: BitmapShader, val relief: Relief)

    /**
     * The relief of the keyboard's paper, read from its photograph (where it is lighter or darker): how a
     * letter written on it bends ([gx], [gy]: the slope, smoothed so it follows the big creases, not the
     * grain) and how much it is in shade ([shade], -1 in the creases' shadow .. 1 on the lit faces). On a small
     * copy of the photograph: [scale] of its size.
     */
    class Relief(val width: Int, val height: Int, val scale: Float, val shade: FloatArray, val gx: FloatArray, val gy: FloatArray) {
        /** [values] at ([u], [v]) on the small copy, smoothly between its points. */
        fun sample(values: FloatArray, u: Float, v: Float): Float {
            val x = u.coerceIn(0f, width - 1.001f)
            val y = v.coerceIn(0f, height - 1.001f)
            val x0 = x.toInt()
            val y0 = y.toInt()
            val fx = x - x0
            val fy = y - y0
            val i = y0 * width + x0
            val a = values[i] * (1 - fx) + values[i + 1] * fx
            val b = values[i + width] * (1 - fx) + values[i + width + 1] * fx
            return a * (1 - fy) + b * fy
        }
    }

    /** The relief of the keyboard's paper, or null until it is loaded. */
    fun relief(lightTone: Boolean): Relief? = (if (lightTone) light else dark)?.relief

    /**
     * Where the keyboard's paper lies on screen: the photograph's pixel at (x, y) is at window point
     * ([placeScale] · x + [placeX], [placeScale] · y + [placeY]); [placed] counts the changes.
     */
    @Volatile var placeScale = 0f
        private set
    @Volatile var placeX = 0f
        private set
    @Volatile var placeY = 0f
        private set
    @Volatile var placed = 0
        private set

    fun place(scale: Float, x: Float, y: Float) {
        if (scale == placeScale && x == placeX && y == placeY) return
        placeScale = scale
        placeX = x
        placeY = y
        placed++
    }

    private fun relief(bitmap: Bitmap): Relief {
        val w = 360
        val h = (bitmap.height * w / bitmap.width.toFloat()).toInt().coerceAtLeast(2)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val lum = FloatArray(w * h) { i ->
            val c = px[i]
            (((c shr 16) and 0xFF) * 0.299f + ((c shr 8) and 0xFF) * 0.587f + (c and 0xFF) * 0.114f) / 255f
        }
        fun box(src: FloatArray, r: Int): FloatArray {
            val tmp = FloatArray(w * h)
            val out = FloatArray(w * h)
            val k = 2 * r + 1
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0f
                for (d in -r..r) sum += src[y * w + (x + d).coerceIn(0, w - 1)]
                tmp[y * w + x] = sum / k
            }
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0f
                for (d in -r..r) sum += tmp[(y + d).coerceIn(0, h - 1) * w + x]
                out[y * w + x] = sum / k
            }
            return out
        }
        val fine = box(lum, 2)
        val wide = box(lum, 14)
        val mid = box(box(lum, 3), 3)
        val shade = FloatArray(w * h) { fine[it] - wide[it] }
        var sq = 0f
        for (v in shade) sq += v * v
        val spread = kotlin.math.sqrt(sq / shade.size) * 2.2f + 1e-6f
        for (i in shade.indices) shade[i] = (shade[i] / spread).coerceIn(-1f, 1f)
        val gx = FloatArray(w * h)
        val gy = FloatArray(w * h)
        val mags = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            gx[i] = (mid[y * w + (x + 1).coerceAtMost(w - 1)] - mid[y * w + (x - 1).coerceAtLeast(0)]) / 2
            gy[i] = (mid[(y + 1).coerceAtMost(h - 1) * w + x] - mid[(y - 1).coerceAtLeast(0) * w + x]) / 2
            mags[i] = kotlin.math.hypot(gx[i], gy[i])
        }
        val sorted = mags.copyOf().also { it.sort() }
        val norm = sorted[(sorted.size * 0.95f).toInt().coerceAtMost(sorted.size - 1)] + 1e-6f
        for (i in gx.indices) {
            gx[i] = (gx[i] / norm).coerceIn(-1.5f, 1.5f)
            gy[i] = (gy[i] / norm).coerceIn(-1.5f, 1.5f)
        }
        return Relief(w, h, w / bitmap.width.toFloat(), shade, gx, gy)
    }

    @Volatile private var light: Papers? = null
    @Volatile private var dark: Papers? = null
    private val making = HashSet<Boolean>()
    private val waiting = HashMap<Boolean, ArrayList<() -> Unit>>()

    /** The sheet the keyboard is written on, or null until it is loaded. */
    fun keyboard(lightTone: Boolean): Bitmap? = (if (lightTone) light else dark)?.keyboard

    /** The paper of the scraps on top, repeating, or null until it is loaded. */
    fun shader(lightTone: Boolean): BitmapShader? = (if (lightTone) light else dark)?.cards

    /** Loads the papers of [lightTone] in the background (once); [ready] runs on that thread when they are there. */
    fun prepare(context: Context, lightTone: Boolean, ready: () -> Unit) {
        val resources = context.applicationContext.resources
        synchronized(this) {
            if ((if (lightTone) light else dark) != null) return
            waiting.getOrPut(lightTone) { ArrayList() } += ready
            if (!making.add(lightTone)) return
        }
        Thread {
            val made = runCatching {
                val options = BitmapFactory.Options().apply { inScaled = false }
                val keyboard = BitmapFactory.decodeResource(resources, if (lightTone) R.drawable.sheet_keys_light else R.drawable.sheet_keys_dark, options)
                val cards = BitmapFactory.decodeResource(resources, if (lightTone) R.drawable.sheet_cards_light else R.drawable.sheet_cards_dark, options)
                Papers(keyboard, BitmapShader(cards, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR), relief(keyboard))
            }.getOrNull()
            val callbacks = synchronized(this) {
                if (lightTone) light = made else dark = made
                making.remove(lightTone)
                waiting.remove(lightTone).orEmpty()
            }
            callbacks.forEach { it() }
        }.apply { name = "papers"; isDaemon = true }.start()
    }
}
