package com.blacklaunch.diary.image

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
     * «Аркуш»: малюнок білим олівцем на чорному папері. Білим олівцем малюють **світло**, тож яскравість іде за світлом
     * самого фото (обличчя світле — і на малюнку світле, очі й тіні — чорний папір): схожість зберігається, бо це
     * обробка фотографії, а не перемальовування. Поверх тону:
     *  - обриси — тонкі світлі лінії там, де на фото межа (прийом «освітлення основи» сірого фото з розмитим негативом);
     *  - текстура олівця — косі штрихи (шум, розтягнутий уздовж діагоналі), тож тон виглядає заштрихованим, а не
     *    гладким фото;
     *  - зерно паперу.
     */
    fun pencil(src: IntArray, w: Int, h: Int, seed: Int = 7): IntArray {
        val lum = luminance(src)
        autoLevels(lum)
        val n = w * h
        // Обриси: класичний ескіз (1 — папір, менше — лінія), звідси сила лінії.
        val inv = FloatArray(n) { 1f - lum[it] }
        val radius = max(1, min(w, h) / 200)
        val blurred = boxBlur(inv, w, h, radius, passes = 3)
        // Штрихи олівця: випадковий шум, розмазаний уздовж діагоналі.
        var rnd = seed * 1103515245 + 12345
        val noise = FloatArray(n) {
            rnd = rnd * 1103515245 + 12345
            ((rnd ushr 16) and 0xFF) / 255f
        }
        val len = max(3, min(w, h) / 110)
        val streak = diagonalBlur(noise, w, h, len)
        val out = IntArray(n)
        for (i in 0 until n) {
            val l = lum[i]
            val b = blurred[i]
            val dodge = if (b >= 0.999f) 1f else min(1f, l / (1f - b))
            val line = ((1f - dodge) * 1.3f).coerceIn(0f, 1f)
            // Тон за світлом фото, трохи стиснутий (малюнок, а не фото): темне — чорний папір.
            val t = smoothRange(0.18f, 0.95f, l).pow(1.15f)
            // Штрихи: світла частина тону «заштрихована» — то густіше, то рідше.
            val s = ((streak[i] - 0.5f) * 3.2f + 0.5f).coerceIn(0f, 1f)
            val tone = t * (0.68f + 0.32f * s)
            // Змішування «екраном»: лінії й тон складаються, як шари олівця.
            var v = 1f - (1f - line * 0.55f) * (1f - tone * 0.95f)
            rnd = rnd * 1103515245 + 12345
            v += (((rnd ushr 16) and 0xFF) / 255f - 0.5f) * 0.035f
            out[i] = gray((v * MAX_LIGHT * 1.08f).coerceIn(0f, MAX_LIGHT + 0.06f))
        }
        return out
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
