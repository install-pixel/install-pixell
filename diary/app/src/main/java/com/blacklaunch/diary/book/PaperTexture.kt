package com.blacklaunch.diary.book

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * «Аркуш»: фактура старого чорного паперу — як щоденник, що пожив: вугільний тон із теплим відтінком, плямистість
 * фарбування, волокна й цятки, кілька м'яких заломів і зморшок, витерті світліші краї. Обкладинка — щільний чорний
 * картон / шкіра. Генерується кодом один раз (без мережі й без картинок у програмі), однакова на всіх сторінках — при
 * перегортанні нічого не «стрибає». Поки рахується (частку секунди у фоні), папір малюється рівним тоном [PAGE_BASE].
 */
object PaperTexture {
    /** Тон паперу без фактури (у кольорах рахунку — без обробки). */
    val PAGE_BASE = androidx.compose.ui.graphics.Color(0xFF15130F)
    val COVER_BASE = androidx.compose.ui.graphics.Color(0xFF0C0B0A)

    var page by mutableStateOf<ImageBitmap?>(null)
        private set
    var cover by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Зерно для олівця: білі лінії й текст ледь «рвуться» на шорсткому папері (накладається множенням). */
    var grain by mutableStateOf<ImageBitmap?>(null)
        private set

    @Volatile private var started = false

    fun ensure() {
        if (started) return
        started = true
        thread(name = "paper-texture", isDaemon = true) {
            page = sheet(W, H, seed = 11, base = intArrayOf(21, 19, 15), mottle = 7f, grainAmp = 3.5f, fibers = 2200, creases = 3, wrinkles = 9, wear = 13f, pores = 0f).asImageBitmap()
            cover = sheet(W, H, seed = 29, base = intArrayOf(12, 11, 10), mottle = 5f, grainAmp = 4f, fibers = 600, creases = 2, wrinkles = 5, wear = 18f, pores = 4f).asImageBitmap()
            grain = grainMap(W / 2, H / 2, seed = 5).asImageBitmap()
        }
    }

    private const val W = 540
    private const val H = 1080

    private fun sheet(
        w: Int, h: Int, seed: Int, base: IntArray, mottle: Float, grainAmp: Float,
        fibers: Int, creases: Int, wrinkles: Int, wear: Float, pores: Float,
    ): Bitmap {
        val rnd = java.util.Random(seed.toLong())
        val l = FloatArray(w * h)
        val n1 = ValueNoise(rnd, 170f)
        val n2 = ValueNoise(rnd, 60f)
        val n3 = ValueNoise(rnd, 18f)
        val n4 = ValueNoise(rnd, 3.5f)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            // Плямистість фарбування — великі м'які плями, і дрібніші в них.
            var v = (n1.at(x, y) * 0.6f + n2.at(x, y) * 0.3f + n3.at(x, y) * 0.1f - 0.5f) * 2f * mottle
            // Зерно паперу; у шкіри обкладинки — ще й пори.
            v += (rnd.nextFloat() - 0.5f) * 2f * grainAmp
            if (pores > 0f) v += (n4.at(x, y) - 0.5f) * 2f * pores
            // Витерті краї — світліші, нерівно.
            val e = minOf(minOf(x, w - 1 - x), minOf(y, h - 1 - y)).toFloat()
            val band = w * 0.05f
            if (e < band) {
                val k = 1f - e / band
                v += wear * k * k * (0.55f + 0.9f * n3.at(x + 500, y))
            }
            l[i] = v
        }
        // Заломи (через весь аркуш) і зморшки (коротші): світло з одного боку складки, тінь — з другого.
        repeat(creases + wrinkles) { k ->
            val long = k < creases
            val ax = rnd.nextFloat() * w
            val ay = rnd.nextFloat() * h
            val ang = (rnd.nextFloat() - 0.5f) * 2.4f + if (rnd.nextBoolean()) 0f else 1.57f
            val len = if (long) (w + h).toFloat() else w * (0.12f + rnd.nextFloat() * 0.25f)
            val amp = if (long) 7f else 4f
            val width = if (long) 7f else 4f
            val dx = cos(ang)
            val dy = sin(ang)
            for (y in 0 until h) for (x in 0 until w) {
                val px = x - ax
                val py = y - ay
                val t = px * dx + py * dy
                if (abs(t) > len / 2) continue
                val d = px * -dy + py * dx
                if (abs(d) > width * 3) continue
                val along = 1f - (abs(t) / (len / 2)).let { it * it }
                val s = d / width
                l[y * w + x] += amp * along * (s * exp(-s * s) * 2.3f) * (0.6f + 0.8f * n2.at(x + 900, y))
            }
        }
        // Волокна: короткі світлі вигнуті нитки.
        repeat(fibers) {
            var fx = rnd.nextFloat() * w
            var fy = rnd.nextFloat() * h
            var a = rnd.nextFloat() * 6.28f
            val len = 6 + rnd.nextInt(18)
            val b = 4f + rnd.nextFloat() * 6f
            for (s in 0 until len) {
                val ix = fx.toInt()
                val iy = fy.toInt()
                if (ix in 0 until w && iy in 0 until h) l[iy * w + ix] += b * (1f - s.toFloat() / len)
                a += (rnd.nextFloat() - 0.5f) * 0.5f
                fx += cos(a)
                fy += sin(a)
            }
        }
        // Цятки.
        repeat(w * h / 1500) {
            val ix = rnd.nextInt(w)
            val iy = rnd.nextInt(h)
            l[iy * w + ix] += 10f + rnd.nextFloat() * 10f
        }
        val px = IntArray(w * h) { i ->
            val v = l[i]
            val r = (base[0] + v).toInt().coerceIn(0, 255)
            val g = (base[1] + v * 0.94f).toInt().coerceIn(0, 255)
            val bl = (base[2] + v * 0.82f).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Карта зерна: біле з дрібними темнішими цятками (множення робить олівець «шорстким»). */
    private fun grainMap(w: Int, h: Int, seed: Int): Bitmap {
        val rnd = java.util.Random(seed.toLong())
        val n = ValueNoise(rnd, 2.2f)
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val v = (0.8f + 0.2f * sqrt(rnd.nextFloat()) * (0.6f + 0.4f * n.at(x, y))).coerceIn(0f, 1f)
            val c = (v * 255f).toInt()
            (0xFF shl 24) or (c shl 16) or (c shl 8) or c
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Плавний шум на гратці з кроком [cell] px (значення 0…1). */
    private class ValueNoise(rnd: java.util.Random, private val cell: Float) {
        private val size = 64
        private val grid = FloatArray(size * size) { rnd.nextFloat() }
        fun at(x: Int, y: Int): Float {
            val gx = x / cell
            val gy = y / cell
            val x0 = gx.toInt()
            val y0 = gy.toInt()
            val tx = smooth(gx - x0)
            val ty = smooth(gy - y0)
            fun g(i: Int, j: Int) = grid[(Math.floorMod(j, size)) * size + Math.floorMod(i, size)]
            val a = g(x0, y0) + (g(x0 + 1, y0) - g(x0, y0)) * tx
            val b = g(x0, y0 + 1) + (g(x0 + 1, y0 + 1) - g(x0, y0 + 1)) * tx
            return a + (b - a) * ty
        }
        private fun smooth(t: Float) = t * t * (3f - 2f * t)
    }
}
