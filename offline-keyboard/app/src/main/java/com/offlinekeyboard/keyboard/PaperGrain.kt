package com.offlinekeyboard.keyboard

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The grain of the sketch style's paper, to lay over a piece of paper (a key, a button): faint mottling and
 * fine fibres, as in a notebook's page. Light fibres for the dark graphite paper, brown ones for the
 * parchment. One seamless tile each (it repeats without a visible edge), made once in the background.
 */
object PaperGrain {

    @Volatile private var dark: BitmapShader? = null
    @Volatile private var light: BitmapShader? = null

    /** The tile for the [lightTone] paper as a repeating shader, or null until it is made. */
    fun shader(lightTone: Boolean): BitmapShader? = if (lightTone) light else dark

    private var making = false
    private val waiting = ArrayList<() -> Unit>()

    /** Makes both tiles in the background (once); [ready] runs on that thread when they are there. */
    fun prepare(density: Float, ready: () -> Unit) {
        synchronized(this) {
            if (dark != null && light != null) return
            waiting += ready
            if (making) return
            making = true
        }
        Thread {
            val size = (130 * density).toInt().coerceIn(220, 560)
            val d = runCatching { tile(size, density, lightTone = false) }.getOrNull()
            val l = runCatching { tile(size, density, lightTone = true) }.getOrNull()
            val callbacks = synchronized(this) {
                dark = d?.let { BitmapShader(it, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
                light = l?.let { BitmapShader(it, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
                making = false
                waiting.toList().also { waiting.clear() }
            }
            callbacks.forEach { it() }
        }.apply { name = "paper grain"; isDaemon = true }.start()
    }

    private fun tile(size: Int, density: Float, lightTone: Boolean): Bitmap {
        val random = Random(if (lightTone) 41 else 37)
        // Mottling on a small wrapping grid (seamless), and a fine grain: black and white at low alpha.
        val cells = 5
        val grid = FloatArray(cells * cells) { random.nextFloat() * 2f - 1f }
        fun g(a: Int, b: Int) = grid[(b % cells) * cells + (a % cells)]
        val pixels = IntArray(size * size)
        val mottle = if (lightTone) 0.07f else 0.05f
        for (y in 0 until size) {
            val gy = y * cells / size.toFloat()
            val cy = gy.toInt()
            val fy = gy - cy
            for (x in 0 until size) {
                val gx = x * cells / size.toFloat()
                val cx = gx.toInt()
                val fx = gx - cx
                val m = (g(cx, cy) * (1 - fx) + g(cx + 1, cy) * fx) * (1 - fy) + (g(cx, cy + 1) * (1 - fx) + g(cx + 1, cy + 1) * fx) * fy
                val v = m * mottle + (random.nextFloat() - 0.5f) * 0.06f
                val a = (kotlin.math.abs(v).coerceAtMost(1f) * 255).toInt()
                pixels[y * size + x] = if (v >= 0f) (a shl 24) or 0xFFFFFF else (a shl 24)
            }
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        // Fibres: short thin strokes, a little bent, every way; drawn again across each edge so the tile
        // repeats without a seam.
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, 0.4f * density)
            strokeCap = Paint.Cap.ROUND
        }
        repeat(size * size / (420 * density * density).toInt().coerceAtLeast(1)) {
            val x = random.nextFloat() * size
            val y = random.nextFloat() * size
            val length = (3f + random.nextFloat() * 10f) * density
            val angle = random.nextFloat() * 3.1416f
            val bend = (random.nextFloat() - 0.5f) * 0.6f
            val mx = x + cos(angle) * length / 2
            val my = y + sin(angle) * length / 2
            val ex = mx + cos(angle + bend) * length / 2
            val ey = my + sin(angle + bend) * length / 2
            paint.color = if (lightTone) {
                (((10 + random.nextInt(18)) shl 24) or 0x5A3C1E)
            } else {
                (((7 + random.nextInt(12)) shl 24) or 0xFFFFFF)
            }
            for (ox in -1..1) for (oy in -1..1) {
                val dx = ox * size.toFloat()
                val dy = oy * size.toFloat()
                canvas.drawLine(x + dx, y + dy, mx + dx, my + dy, paint)
                canvas.drawLine(mx + dx, my + dy, ex + dx, ey + dy, paint)
            }
        }
        return bitmap
    }
}
