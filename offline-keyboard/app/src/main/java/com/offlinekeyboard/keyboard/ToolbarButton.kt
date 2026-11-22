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

    /**
     * The typewriter: not a key (keys type, and have their levers) but a control on the machine's body, a flat
     * metal plate in a frame that goes down a little when pressed, its text with it (the mode chips, the
     * clipboard's texts, the touchpad's mouse buttons).
     */
    var bodyPlate = false
        set(value) { if (field != value) { field = value; invalidate() } }
    /** Told when the button goes down or comes up (a text laid over it moves with it). */
    var onPress: ((Boolean) -> Unit)? = null

    /**
     * The typewriter strip's buttons: pressed only a little way (a full key's travel took the plate under the
     * paper holder's line).
     */
    var shallowPress = false

    /**
     * How far down the button's face is now (px): what lies on it moves as far. A body plate that is a switch
     * (a mode: [chosen]) stays down while it is on, as a latching key, and comes up when another is pressed;
     * the others come up as the finger lets go.
     */
    val sink: Float
        get() = when {
            style != SurfaceStyle.TYPEWRITER || (fill == 0 && pressedFill == 0) -> 0f
            bodyPlate && (pressed || chosen) -> PLATE_SINK_DP * density
            !pressed -> 0f
            shallowPress -> surfaces.typewriterSink * SHALLOW
            else -> surfaces.typewriterSink
        }

    /** How far in from the edges a filled plate is drawn (the touch area stays the whole button). */
    var plateInset = 0f
        set(value) { if (field != value) { field = value; invalidate() } }
    /** How small the text may shrink before it is cut (sp). */
    var minTextSp = MIN_TEXT_SP
    /** The icon's size against the usual one (a busy icon a little smaller). */
    var iconScale = 1f
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
        surfaces.milk = theme.milk
        surfaces.sheetLight = !theme.dark
        surfaces.seeThrough = theme.seeThrough
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
            SurfaceStyle.TYPEWRITER -> {
                regular = font(R.font.cousine_regular) ?: Typeface.MONOSPACE
                boldFace = font(R.font.cousine_bold) ?: Typeface.MONOSPACE
            }
        }
        invalidate()
    }

    private val spot = IntArray(2)

    override fun onDraw(canvas: Canvas) {
        if (style == SurfaceStyle.SHEET) {
            // Written on the keyboard's paper like the keys (a dent when pressed, a pencil ring when chosen).
            surfaces.paperPieces = false
            getLocationInWindow(spot)
            surfaces.sheetOffsetX = spot[0].toFloat()
            surfaces.sheetOffsetY = spot[1].toFloat()
        }
        // Pressed, a button without a plate shows one as small as its lit plate (on the typewriter the volume
        // button's pressed plate reached past the paper holder's line).
        val inset = when {
            fill != 0 -> plateInset
            pressed && pressedFill != 0 -> maxOf(plateInset, 3f * density)
            else -> 3f * density
        }
        bounds.set(inset, inset, width - inset, height - inset)
        val background = if (pressed) pressedFill else fill
        if (background != 0) {
            val r = 12f * density
            val state = when {
                pressed -> SurfaceState.PRESSED
                chosen -> SurfaceState.SELECTED
                else -> SurfaceState.NORMAL
            }
            val plate = bodyPlate && style == SurfaceStyle.TYPEWRITER
            if (plate) {
                // A body plate sits in a recess of the machine: down, the recess shows dark above it and the
                // plate's face is a little in shadow.
                val down = sink
                recessPaint.color = 0x99000000.toInt()
                canvas.drawRoundRect(bounds, r, r, recessPaint)
                canvas.save()
                canvas.translate(0f, down)
                surfaces.draw(canvas, bounds, r, style, background, SurfaceState.NORMAL, seed = id xor text.hashCode(), inkColor = foreground, plate = true)
                if (down > 0f) {
                    recessPaint.color = 0x2E000000
                    canvas.drawRoundRect(bounds, r, r, recessPaint)
                    // The recess's upper edge throws a shadow onto the plate's top.
                    recessShade.shader = android.graphics.LinearGradient(
                        0f, bounds.top, 0f, bounds.top + 7f * density,
                        0x66000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP,
                    )
                    canvas.save()
                    canvas.clipRect(bounds.left, bounds.top, bounds.right, bounds.top + 7f * density)
                    canvas.drawRoundRect(bounds, r, r, recessShade)
                    canvas.restore()
                }
                canvas.restore()
            } else {
                if (shallowPress && pressed && style == SurfaceStyle.TYPEWRITER) surfaces.typewriterDepth = SHALLOW
                surfaces.draw(canvas, bounds, r, style, background, state, seed = id xor text.hashCode(), inkColor = foreground)
                surfaces.typewriterDepth = -2f
            }
        }
        // The ink goes down with the face it is on (it stayed put while the key went down under it).
        val inkSink = sink
        if (inkSink != 0f) {
            canvas.save()
            canvas.translate(0f, inkSink)
        }

        // A narrow button keeps its icon well inside the plate, with room round it (the remote's row).
        val plateWidth = width - 2f * (if (fill != 0) plateInset else 3f * density)
        val iconSize = minOf(height * 0.56f, 24f * density, plateWidth * (if (text.isEmpty()) 0.6f else 1f)) * iconScale
        textPaint.color = foreground
        textPaint.textSize = textSizeSp * textScale * scaledDensity
        textPaint.typeface = if (bold) boldFace else regular
        val gap = 5f * density
        // The text stays inside the plate: on the typewriter inside its metal rim, elsewhere with a little
        // margin (less on narrow buttons, so "Esc" stays whole on the remote's row).
        val room = if (fill != 0) plateWidth else width.toFloat()
        val margin = if (fill != 0 && style == SurfaceStyle.TYPEWRITER) {
            2f * (surfaces.typewriterRim(bounds.height()) + 1.5f * density)
        } else {
            minOf(10f * density, room * 0.12f)
        }
        val available = room - margin - (if (icon != null) iconSize + gap else 0f)
        // Shrink before cutting the text, so narrow phones still show whole words.
        var size = textSizeSp * textScale
        while (size > minTextSp && textPaint.measureText(text) > available) {
            size -= 0.5f
            textPaint.textSize = size * scaledDensity
        }
        val shown = if (text.isEmpty()) "" else TextUtils.ellipsize(text, textPaint, maxOf(available, 0f), TextUtils.TruncateAt.END).toString()
        val textWidth = if (shown.isEmpty()) 0f else textPaint.measureText(shown)
        val total = (if (icon != null) iconSize else 0f) + (if (icon != null && shown.isNotEmpty()) gap else 0f) + textWidth
        val x0 = (width - total) / 2
        val cy = height / 2f
        fun drawInk(c: Canvas, color: Int) {
            var x = x0
            icon?.let {
                Icons.draw(c, it, x + iconSize / 2, cy, iconSize, color)
                x += iconSize + gap
            }
            if (shown.isNotEmpty()) {
                textPaint.color = color
                c.drawText(shown, x, cy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
            }
        }
        if (style == SurfaceStyle.SHEET && width > 0 && height > 0) {
            // Written on the paper, it bends with its relief as the keys' letters (made again only on a change).
            val key = "$shown|$icon|$iconSize|${textPaint.textSize}|${spot[0]},${spot[1]}|$width,$height|$foreground|${PaperSheets.placed}|${surfaces.sheetLight}"
            if (key != bentKey) {
                bentKey = key
                bent = runCatching {
                    val mask = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    drawInk(Canvas(mask), 0xFFFFFFFF.toInt())
                    InkBend.bend(mask, spot[0], spot[1], foreground, density, surfaces.sheetLight).also { mask.recycle() }
                }.getOrNull()
            }
            bent?.let {
                canvas.drawBitmap(it, 0f, 0f, null)
                if (inkSink != 0f) canvas.restore()
                return
            }
        }
        drawInk(canvas, foreground)
        if (inkSink != 0f) canvas.restore()
    }

    private val recessPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val recessShade = Paint(Paint.ANTI_ALIAS_FLAG)

    private var bentKey: String? = null
    private var bent: android.graphics.Bitmap? = null

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
            onPress?.invoke(value)
            invalidate()
        }
    }

    private companion object {
        const val MIN_TEXT_SP = 11f
        const val PLATE_SINK_DP = 3.5f
        /** How far a strip button goes down, of a key's travel. */
        const val SHALLOW = 0.35f
    }
}
