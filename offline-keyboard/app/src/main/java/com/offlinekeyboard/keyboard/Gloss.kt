package com.offlinekeyboard.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/**
 * How the OLED theme draws a key or button (the BlackLaunch look): the colour runs from lighter at the
 * top to darker at the bottom, and a thin light rim is brightest on top, dimmer at the sides and visible
 * again at the bottom, so grey surfaces look raised on the black background.
 *
 * Gradients are made once per colour over a 0..1 height and stretched onto each key, so drawing
 * allocates nothing.
 */
class Gloss(density: Float) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val fills = HashMap<Int, LinearGradient>()
    private val rimShader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0x4DFFFFFF, 0x1AFFFFFF, 0x2EFFFFFF),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val matrix = Matrix()
    private val inner = RectF()

    fun draw(canvas: Canvas, rect: RectF, radius: Float, color: Int) {
        matrix.setScale(1f, rect.height())
        matrix.postTranslate(0f, rect.top)
        val shader = fills.getOrPut(color) {
            LinearGradient(0f, 0f, 0f, 1f, shade(color, TOP), shade(color, BOTTOM), Shader.TileMode.CLAMP)
        }
        shader.setLocalMatrix(matrix)
        fill.shader = shader
        canvas.drawRoundRect(rect, radius, radius, fill)
        rimShader.setLocalMatrix(matrix)
        rim.shader = rimShader
        val half = rim.strokeWidth / 2
        inner.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)
        canvas.drawRoundRect(inner, radius - half, radius - half, rim)
    }

    private companion object {
        // #1D1D1D becomes #242424 at the top and #161616 at the bottom, the launcher's surface.
        const val TOP = 1.24f
        const val BOTTOM = 0.76f

        fun shade(color: Int, factor: Float): Int {
            fun ch(v: Int) = (v * factor).toInt().coerceIn(0, 255)
            return Color.argb(Color.alpha(color), ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
        }
    }
}
