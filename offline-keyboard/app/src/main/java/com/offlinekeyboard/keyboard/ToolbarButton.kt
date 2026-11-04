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
import androidx.core.content.res.ResourcesCompat
import com.offlinekeyboard.R

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
    /** Chosen (an active mode, the selected character): glass and sheet show it with a brighter rim. */
    var chosen = false
        set(value) { if (field != value) { field = value; invalidate() } }
    private var style = SurfaceStyle.FLAT
    private var regular: Typeface = Typeface.DEFAULT
    private var boldFace: Typeface = Typeface.DEFAULT_BOLD
    private var textScale = 1f
    var foreground: Int = 0
        set(value) { if (field != value) { field = value; invalidate() } }
    var textSizeSp: Float = 15f
        set(value) { field = value; invalidate() }
    var bold = false

    private val density = resources.displayMetrics.density
    private val scaledDensity = density * resources.configuration.fontScale
    private val surfaces by lazy { SurfacePainter(density) }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var pressed = false

    init {
        isClickable = true
        isFocusable = false
    }

    /** The theme's surface style and font; colours are set by the owner. */
    fun applyStyle(theme: KeyboardTheme) {
        style = theme.style
        surfaces.brightness = theme.brightness
        textScale = theme.textScale
        fun font(id: Int) = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
        when (theme.style) {
            SurfaceStyle.FLAT -> { regular = Typeface.DEFAULT; boldFace = Typeface.DEFAULT_BOLD }
            SurfaceStyle.GLASS -> {
                regular = font(R.font.exo2_regular) ?: Typeface.DEFAULT
                boldFace = font(R.font.exo2_semibold) ?: Typeface.DEFAULT_BOLD
            }
            SurfaceStyle.SHEET -> {
                regular = font(R.font.caveat_semibold) ?: Typeface.DEFAULT
                boldFace = regular
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = if (fill != 0) 0f else 3f * density
        bounds.set(inset, inset, width - inset, height - inset)
        val background = if (pressed) pressedFill else fill
        if (background != 0) {
            val r = 12f * density
            val state = when {
                pressed -> SurfaceState.PRESSED
                chosen -> SurfaceState.SELECTED
                else -> SurfaceState.NORMAL
            }
            surfaces.draw(canvas, bounds, r, style, background, state, seed = id xor text.hashCode(), inkColor = foreground)
        }

        val iconSize = minOf(height * 0.56f, 24f * density)
        textPaint.color = foreground
        textPaint.textSize = textSizeSp * textScale * scaledDensity
        textPaint.typeface = if (bold) boldFace else regular
        val gap = 5f * density
        val available = width - 10f * density - (if (icon != null) iconSize + gap else 0f)
        // Shrink a little before cutting the text, so narrow phones still show whole words.
        var size = textSizeSp * textScale
        while (size > MIN_TEXT_SP && textPaint.measureText(text) > available) {
            size -= 0.5f
            textPaint.textSize = size * scaledDensity
        }
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

    private companion object {
        const val MIN_TEXT_SP = 11f
    }
}
