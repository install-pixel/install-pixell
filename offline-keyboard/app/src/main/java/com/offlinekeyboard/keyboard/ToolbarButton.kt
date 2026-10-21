package com.offlinekeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View

/**
 * A toolbar button: an [Icon], a short text, or both, on an optional rounded background. The whole
 * area is the touch target, so even narrow-looking buttons are at least as large as their row.
 */
@SuppressLint("ViewConstructor")
class ToolbarButton(context: Context) : View(context) {

    var icon: Icon? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    var text: String = ""
        set(value) { if (field != value) { field = value; invalidate() } }
    /** Background colour; 0 draws no background. */
    var fill: Int = 0
        set(value) { if (field != value) { field = value; invalidate() } }
    var pressedFill: Int = 0
    var foreground: Int = 0
        set(value) { if (field != value) { field = value; invalidate() } }
    var textSizeSp: Float = 15f
        set(value) { field = value; invalidate() }
    var bold = false

    private val density = resources.displayMetrics.density
    private val scaledDensity = density * resources.configuration.fontScale
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var pressed = false

    init {
        isClickable = true
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        val inset = if (fill != 0) 0f else 3f * density
        bounds.set(inset, inset, width - inset, height - inset)
        val background = if (pressed) pressedFill else fill
        if (background != 0) {
            bgPaint.color = background
            val r = 12f * density
            canvas.drawRoundRect(bounds, r, r, bgPaint)
        }

        val iconSize = minOf(height * 0.56f, 24f * density)
        textPaint.color = foreground
        textPaint.textSize = textSizeSp * scaledDensity
        textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val gap = 6f * density
        val available = width - 12f * density - (if (icon != null) iconSize + gap else 0f)
        val shown = if (text.isEmpty()) "" else TextUtils.ellipsize(text, textPaint, maxOf(available, 0f), TextUtils.TruncateAt.END).toString()
        val textWidth = if (shown.isEmpty()) 0f else textPaint.measureText(shown)
        val total = (if (icon != null) iconSize else 0f) + (if (icon != null && shown.isNotEmpty()) gap else 0f) + textWidth
        var x = (width - total) / 2
        val cy = height / 2f
        icon?.let {
            Icons.draw(canvas, it, x + iconSize / 2, cy, iconSize, foreground)
            x += iconSize + gap
        }
        if (shown.isNotEmpty()) {
            canvas.drawText(shown, x, cy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> setPressedState(true)
            MotionEvent.ACTION_CANCEL -> setPressedState(false)
            MotionEvent.ACTION_UP -> setPressedState(false)
            MotionEvent.ACTION_MOVE -> if (event.x < 0 || event.y < 0 || event.x > width || event.y > height) setPressedState(false)
        }
        return super.onTouchEvent(event)
    }

    private fun setPressedState(value: Boolean) {
        if (pressed != value) {
            pressed = value
            invalidate()
        }
    }
}
