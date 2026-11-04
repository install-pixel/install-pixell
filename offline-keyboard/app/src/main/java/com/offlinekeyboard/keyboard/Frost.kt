package com.offlinekeyboard.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

/**
 * Frosted glass for the enlarged letter and the long-press popup of the glass style: the keys are drawn once
 * more into a small bitmap, blurred, and shown inside the bubble under dark, partly transparent glass, so the
 * bubble reads as glass over the keyboard rather than a hole or a flat plate.
 *
 * Works on every Android version (no RenderEffect); the bitmap is [SCALE] times smaller than the view, so the
 * extra drawing and the box blur are cheap and happen only while a bubble is shown.
 */
internal class Frost {

    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private var scratch = IntArray(0)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val src = Rect()
    private val dst = RectF()
    private var viewWidth = 0
    private var viewHeight = 0

    /** Redraws [view]'s content with [draw] into the small bitmap and blurs it. */
    fun capture(view: View, draw: (Canvas) -> Unit) {
        val w = maxOf(1, view.width / SCALE)
        val h = maxOf(1, view.height / SCALE)
        viewWidth = view.width
        viewHeight = view.height
        val bmp = bitmap?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            bitmap?.recycle()
            bitmap = it
            pixels = IntArray(w * h)
            scratch = IntArray(w * h)
        }
        bmp.eraseColor(0xFF000000.toInt())
        val canvas = Canvas(bmp)
        canvas.scale(1f / SCALE, 1f / SCALE)
        draw(canvas)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(PASSES) {
            boxBlur(pixels, scratch, w, h, horizontal = true)
            boxBlur(scratch, pixels, w, h, horizontal = false)
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /** Fills [rect] (rounded by [radius]) with [background] and the blurred keys that lie under it. */
    fun drawBehind(canvas: Canvas, rect: RectF, radius: Float, background: Int) {
        canvas.save()
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        canvas.drawColor(background)
        bitmap?.let { bmp ->
            src.set(0, 0, bmp.width, bmp.height)
            dst.set(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
            canvas.drawBitmap(bmp, src, dst, paint)
        }
        canvas.restore()
    }

    private fun boxBlur(from: IntArray, to: IntArray, w: Int, h: Int, horizontal: Boolean) {
        val length = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val span = RADIUS * 2 + 1
        for (line in 0 until lines) {
            var r = 0
            var g = 0
            var b = 0
            fun at(i: Int): Int {
                val c = i.coerceIn(0, length - 1)
                return if (horizontal) from[line * w + c] else from[c * w + line]
            }
            for (i in -RADIUS..RADIUS) {
                val c = at(i)
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            for (i in 0 until length) {
                val out = (0xFF shl 24) or ((r / span) shl 16) or ((g / span) shl 8) or (b / span)
                if (horizontal) to[line * w + i] = out else to[i * w + line] = out
                val add = at(i + RADIUS + 1)
                val drop = at(i - RADIUS)
                r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
                b += (add and 0xFF) - (drop and 0xFF)
            }
        }
    }

    private companion object {
        const val SCALE = 6
        const val RADIUS = 2
        const val PASSES = 2
    }
}
