package com.offlinekeyboard.keyboard

import android.graphics.Bitmap

/**
 * Ink written on the sketch's paper bends with its relief, as the keys' letters do (see KeyboardView.writeInk):
 * for views other than the keys (the toolbar's and the remote's buttons). [mask] is the ink drawn in white on a
 * clear bitmap at window point ([left], [top]); the result is the same ink in [ink], every point taken from a
 * little up or down the paper's slope there and lit or shaded with its creases. Null while the paper is not
 * loaded or placed (the caller then draws the ink flat).
 */
internal object InkBend {
    fun bend(mask: Bitmap, left: Int, top: Int, ink: Int, density: Float, lightTone: Boolean): Bitmap? {
        val relief = PaperSheets.relief(lightTone) ?: return null
        val scale = PaperSheets.placeScale
        if (scale <= 0f) return null
        val w = mask.width
        val h = mask.height
        val src = IntArray(w * h)
        mask.getPixels(src, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val inkA = (ink ushr 24) and 0xFF
        val ir = (ink shr 16) and 0xFF
        val ig = (ink shr 8) and 0xFF
        val ib = ink and 0xFF
        val bend = 1.7f * density
        val toMap = relief.scale / scale
        val ox = left - PaperSheets.placeX
        val oy = top - PaperSheets.placeY
        for (py in 0 until h) {
            for (px in 0 until w) {
                val u = (ox + px) * toMap
                val v = (oy + py) * toMap
                val sx = px - relief.sample(relief.gx, u, v) * bend
                val sy = py - relief.sample(relief.gy, u, v) * bend
                val x0 = sx.toInt()
                val y0 = sy.toInt()
                if (x0 < 0 || y0 < 0 || x0 >= w - 1 || y0 >= h - 1) continue
                val fx = sx - x0
                val fy = sy - y0
                val i = y0 * w + x0
                val a = ((src[i] ushr 24) * (1 - fx) + (src[i + 1] ushr 24) * fx) * (1 - fy) +
                    ((src[i + w] ushr 24) * (1 - fx) + (src[i + w + 1] ushr 24) * fx) * fy
                if (a < 1f) continue
                val light = 1f + 0.45f * relief.sample(relief.shade, u, v)
                val alpha = (a * inkA / 255f).toInt().coerceIn(0, 255)
                out[py * w + px] = (alpha shl 24) or
                    ((ir * light).toInt().coerceIn(0, 255) shl 16) or
                    ((ig * light).toInt().coerceIn(0, 255) shl 8) or
                    (ib * light).toInt().coerceIn(0, 255)
            }
        }
        return runCatching { Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888) }.getOrNull()
    }
}
