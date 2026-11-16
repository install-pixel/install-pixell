package com.blacklaunch.diary.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Обробка фото під тему. Чисті обчислення над пікселями (ARGB у [IntArray]) — без Android, тож перевіряються тестами.
 *
 * Обидва фільтри тримають фото **темним**: найсвітліше місце не світліше ~70 %, тож білий текст, замок і рамки поверх
 * фото завжди читаються (не буде «біле на білому»).
 */
object Stylize {

    /** Найсвітліше, що може дати фільтр (0…1). */
    const val MAX_LIGHT = 0.70f

    /**
     * «Скло»: чорно-біле, контрастне, з м'якою віньєткою, притемнене під текст. Скляний відблиск і кант
     * домальовуються поверх під час показу (вони залежать від розміру рамки).
     */
    fun glass(src: IntArray, w: Int, h: Int): IntArray {
        val lum = luminance(src)
        autoLevels(lum)
        val out = IntArray(w * h)
        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(cx * cx + cy * cy)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                var v = lum[i]
                // S-крива: глибші тіні й чіткіші світлі — фото виглядає «склом», а не сірою плямою.
                v = mix(v, smooth(v), 0.65f)
                // Віньєтка: краї темніші, світло ніби падає в центр скла.
                val dx = x - cx
                val dy = y - cy
                val r = sqrt(dx * dx + dy * dy) / maxR
                v *= 1f - 0.38f * r * r
                v = 0.03f + v * (MAX_LIGHT - 0.03f)
                out[i] = gray(v)
            }
        }
        return out
    }

    /**
     * «Аркуш»: чорно-білий комікс білою фарбою на чорному папері — як друкований комікс, лише навпаки:
     *  - тіні — чистий чорний папір;
     *  - півтони — растрові крапки під 45° (що світліше — то більші, аж до суцільного);
     *  - найсвітліше — суцільна біла фарба;
     *  - обриси — чорні лінії, що прорізають світле (тушшю по фарбі), а не світні ореоли — ніякого «рентгену»;
     *  - краї малюнка тануть у папір.
     * Тон береться з трохи розмитого фото (великі плями, як у коміксі), обриси — лише там, де на фото справжня межа.
     * [transparent] — біла фарба з прозорістю (лягає просто на фактуру паперу); інакше — сірий на чорному.
     */
    @Suppress("UNUSED_PARAMETER")
    fun pencil(src: IntArray, w: Int, h: Int, seed: Int = 7, transparent: Boolean = false): IntArray {
        val lum = luminance(src)
        autoLevels(lum)
        val n = w * h
        val soft = boxBlur(lum, w, h, max(1, min(w, h) / 300), passes = 2)
        val tone = boxBlur(lum, w, h, max(1, min(w, h) / 150), passes = 2)
        // Обриси: перепад яскравості (Собель) на трохи розмитому фото.
        val edge = FloatArray(n)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = soft[i - w + 1] + 2 * soft[i + 1] + soft[i + w + 1] - soft[i - w - 1] - 2 * soft[i - 1] - soft[i + w - 1]
            val gy = soft[i + w - 1] + 2 * soft[i + w] + soft[i + w + 1] - soft[i - w - 1] - 2 * soft[i - w] - soft[i - w + 1]
            edge[i] = sqrt(gx * gx + gy * gy)
        }
        val cell = max(4f, min(w, h) / 72f)
        val ca = 0.7071f
        val aa = 0.75f / cell
        val fade = min(w, h) * 0.06f
        val out = IntArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val t = smoothRange(0.16f, 0.86f, tone[i])
            // Растр під 45°: відстань до центру своєї клітинки.
            val u = (x + y) * ca / cell
            val v = (y - x) * ca / cell
            val fu = u - kotlin.math.floor(u) - 0.5f
            val fv = v - kotlin.math.floor(v) - 0.5f
            val dist = sqrt(fu * fu + fv * fv)
            val r = sqrt(t) * 0.64f
            var ink = ((r + aa - dist) / (2f * aa)).coerceIn(0f, 1f)
            if (t > 0.9f) ink = 1f
            if (t < 0.05f) ink = 0f
            // Обрис — чорна лінія тушшю по фарбі.
            val e = ((edge[i] - 0.14f) * 4.5f).coerceIn(0f, 1f)
            ink *= 1f - 0.95f * e
            val dEdge = min(min(x, w - 1 - x), min(y, h - 1 - y)).toFloat()
            ink *= smooth((dEdge / fade).coerceIn(0f, 1f))
            val value = (ink * MAX_LIGHT * 1.08f).coerceIn(0f, MAX_LIGHT + 0.06f)
            out[i] = if (transparent) ink(value / (MAX_LIGHT + 0.06f)) else gray(value)
        }
        return out
    }

    /** Білий олівець (трохи теплий, як [com.blacklaunch.diary.ui.SheetInk]) із прозорістю [a]. */
    private fun ink(a: Float): Int {
        val al = (a.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (al shl 24) or (0xEC shl 16) or (0xE8 shl 8) or 0xE0
    }

    /** Розмити вздовж діагоналі (зліва знизу — вправо вгору): з шуму виходять косі штрихи олівця. */
    private fun diagonalBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val out = FloatArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var cnt = 0
                for (k in -r..r) {
                    val xx = x + k
                    val yy = y - k
                    if (xx in 0 until w && yy in 0 until h) { sum += src[yy * w + xx]; cnt++ }
                }
                out[y * w + x] = sum / cnt
            }
        }
        return out
    }

    private fun smoothRange(a: Float, b: Float, v: Float): Float = smooth(((v - a) / (b - a)).coerceIn(0f, 1f))

    /** Яскравість 0…1 кожного пікселя. */
    fun luminance(src: IntArray): FloatArray = FloatArray(src.size) { i ->
        val c = src[i]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /** Розтягнути яскравість так, щоб 1 % найтемніших став чорним, а 1 % найсвітліших — білим. */
    fun autoLevels(lum: FloatArray) {
        if (lum.isEmpty()) return
        val hist = IntArray(256)
        lum.forEach { hist[(it * 255).toInt().coerceIn(0, 255)]++ }
        val cut = lum.size / 100
        var lo = 0
        var acc = 0
        while (lo < 255 && acc + hist[lo] <= cut) { acc += hist[lo]; lo++ }
        var hi = 255
        acc = 0
        while (hi > 0 && acc + hist[hi] <= cut) { acc += hist[hi]; hi-- }
        if (hi - lo < 16) return
        val a = lo / 255f
        val span = (hi - lo) / 255f
        for (i in lum.indices) lum[i] = ((lum[i] - a) / span).coerceIn(0f, 1f)
    }

    /** Розмиття «коробкою» кілька разів — майже гаусове, а рахується за лінійний час. */
    fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int, passes: Int): FloatArray {
        var a = src.copyOf()
        var b = FloatArray(src.size)
        repeat(passes) {
            blurH(a, b, w, h, radius)
            blurV(b, a, w, h, radius)
        }
        return a
    }

    private fun blurH(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1f
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum / div
                val add = (x + r + 1).coerceAtMost(w - 1)
                val sub = (x - r).coerceAtLeast(0)
                sum += src[row + add] - src[row + sub]
            }
        }
    }

    private fun blurV(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1f
        for (x in 0 until w) {
            var sum = 0f
            for (k in -r..r) sum += src[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / div
                val add = (y + r + 1).coerceAtMost(h - 1)
                val sub = (y - r).coerceAtLeast(0)
                sum += src[add * w + x] - src[sub * w + x]
            }
        }
    }

    private fun smooth(v: Float): Float = v * v * (3f - 2f * v)
    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun gray(v: Float): Int {
        val c = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (0xFF shl 24) or (c shl 16) or (c shl 8) or c
    }
}
