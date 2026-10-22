package com.offlinekeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.offlinekeyboard.core.KeyLabels
import com.offlinekeyboard.core.LabelContext
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyStyle
import com.offlinekeyboard.core.layout.KeyboardLayout
import com.offlinekeyboard.core.input.EnterLabel
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.SpecialKey
import kotlin.math.abs

/**
 * Draws a [KeyboardLayout] on a canvas and turns touches into key presses.
 *
 * Multi-touch: every pointer is tracked separately so fast two-thumb typing works. Repeatable keys
 * (Backspace, Delete, arrows) auto-repeat while held; keys with alternatives open a popup on long press,
 * and the finger slides to pick one.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKey(key: Key)
        fun onAlternative(text: String)
        fun onSpaceSwipe(step: Int)
        fun onLanguageKeyLongPress()
    }

    enum class KeyHighlight { NONE, ACTIVE, LOCKED }

    var listener: Listener? = null
    var hapticFeedback = true
    var vibration = VibrationStrength.MEDIUM

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()
    var spaceSwipeEnabled = true
    var longPressMillis = 350L

    private var layout = KeyboardLayout(emptyList())
    private var labelContext: LabelContext? = null
    private var highlight: (Key) -> KeyHighlight = { KeyHighlight.NONE }
    private var keyHeightPx = dp(48f)

    private val keyRects = ArrayList<Pair<Key, RectF>>()
    private val pointers = SparseArray<PointerState>()
    private val handler = Handler(Looper.getMainLooper())

    private var popup: Popup? = null

    private val keyGap = dp(3f)
    private val corner = dp(6f)

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var theme: KeyboardTheme = KeyboardTheme.create(dark = false, accent = Accent.SKY)
        set(value) {
            if (field == value) return
            field = value
            setBackgroundColor(value.background)
            invalidate()
        }

    init {
        setBackgroundColor(theme.background)
        isHapticFeedbackEnabled = true
    }

    fun bind(layout: KeyboardLayout, labels: LabelContext, keyHeightDp: Float, highlight: (Key) -> KeyHighlight) {
        val heightChanged = dp(keyHeightDp) != keyHeightPx || layout.rows.size != this.layout.rows.size
        val layoutChanged = layout != this.layout
        this.layout = layout
        this.labelContext = labels
        this.highlight = highlight
        keyHeightPx = dp(keyHeightDp)
        if (layoutChanged) {
            cancelAllPointers()
            computeKeyRects()
        }
        if (heightChanged) requestLayout()
        invalidate()
    }

    /** Test hook: screen position of the centre of the first key matching [match], or null. */
    internal fun keyCenterOnScreen(match: (Key) -> Boolean): PointF? {
        val rect = keyRects.firstOrNull { match(it.first) }?.second ?: return null
        val location = IntArray(2)
        getLocationOnScreen(location)
        return PointF(location[0] + rect.centerX(), location[1] + rect.centerY())
    }

    /** Stops repeats and popups, e.g. when the keyboard is hidden mid-press. */
    fun cancelAllPointers() {
        for (i in 0 until pointers.size()) pointers.valueAt(i).cancelCallbacks()
        pointers.clear()
        popup = null
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (layout.rows.size * keyHeightPx + keyGap).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeKeyRects()
    }

    private fun computeKeyRects() {
        keyRects.clear()
        val units = layout.maxRowUnits
        if (units <= 0f || width == 0) return
        val unit = (width - paddingLeft - paddingRight) / units
        var top = paddingTop + keyGap / 2
        for (row in layout.rows) {
            val rowUnits = row.sumOf { it.width.toDouble() }.toFloat()
            var left = paddingLeft + (units - rowUnits) * unit / 2
            for (key in row) {
                val w = key.width * unit
                keyRects += key to RectF(left, top, left + w, top + keyHeightPx)
                left += w
            }
            top += keyHeightPx
        }
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        val ctx = labelContext ?: return
        val pressedKeys = HashSet<Key>()
        for (i in 0 until pointers.size()) pointers.valueAt(i).key?.let(pressedKeys::add)

        for ((key, rect) in keyRects) {
            if (key.style == KeyStyle.SPACER) continue
            val inner = RectF(rect.left + keyGap / 2, rect.top + keyGap / 2, rect.right - keyGap / 2, rect.bottom - keyGap / 2)
            val state = highlight(key)
            val t = theme
            keyPaint.color = when {
                key in pressedKeys -> t.pressed
                state != KeyHighlight.NONE -> t.accent
                key.style == KeyStyle.ACTION -> t.accent
                key.style == KeyStyle.FUNCTION || key.style == KeyStyle.MODIFIER -> t.function
                else -> t.key
            }
            canvas.drawRoundRect(inner, corner, corner, keyPaint)

            val onAccent = key.style == KeyStyle.ACTION || state != KeyHighlight.NONE
            val color = if (onAccent && key !in pressedKeys) t.onAccent else t.text
            val icon = iconFor(key, ctx, state)
            if (icon != null) {
                val size = minOf(inner.height() * 0.48f, inner.width() * 0.62f, dp(26f))
                Icons.draw(canvas, icon, inner.centerX(), inner.centerY(), size, color)
            } else {
                drawLabel(canvas, inner, KeyLabels.label(key, ctx), color, key)
            }

            KeyLabels.hint(key, ctx)?.let { hint ->
                hintPaint.color = t.hint
                hintPaint.textSize = inner.height() * 0.24f
                canvas.drawText(hint, inner.right - inner.width() * 0.1f, inner.top + hintPaint.textSize * 1.05f, hintPaint)
            }
            if (state == KeyHighlight.LOCKED && icon != Icon.CAPS) {
                dotPaint.color = color
                canvas.drawCircle(inner.centerX(), inner.bottom - dp(5f), dp(2.5f), dotPaint)
            }
        }
        popup?.let { drawPopup(canvas, it) }
    }

    private fun drawLabel(canvas: Canvas, rect: RectF, label: String, color: Int, key: Key) {
        if (label.isEmpty()) return
        textPaint.color = color
        val isLetter = key.action is KeyAction.Text && label.length <= 2
        textPaint.typeface = if (isLetter) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
        var size = rect.height() * if (isLetter) 0.46f else 0.30f
        textPaint.textSize = size
        val maxWidth = rect.width() * 0.86f
        val measured = textPaint.measureText(label)
        if (measured > maxWidth) {
            size *= maxWidth / measured
            textPaint.textSize = size
        }
        val y = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, rect.centerX(), y, textPaint)
    }

    /** Keys with a fixed meaning get a drawn icon; letters, names and text actions keep their label. */
    private fun iconFor(key: Key, ctx: LabelContext, state: KeyHighlight): Icon? {
        if (key.label != null) return null
        return when ((key.action as? KeyAction.Special)?.key ?: return null) {
            SpecialKey.SHIFT -> when (state) {
                KeyHighlight.LOCKED -> Icon.CAPS
                KeyHighlight.ACTIVE -> Icon.SHIFT_ON
                KeyHighlight.NONE -> Icon.SHIFT
            }
            SpecialKey.BACKSPACE -> Icon.BACKSPACE
            SpecialKey.ENTER -> when (ctx.enterLabel) {
                EnterLabel.NEWLINE -> Icon.ENTER
                EnterLabel.SEARCH -> Icon.SEARCH
                EnterLabel.SEND, EnterLabel.GO -> Icon.SEND
                EnterLabel.DONE -> Icon.DONE
                EnterLabel.TRANSLATE -> Icon.TRANSLATE
                EnterLabel.NEXT, EnterLabel.PREVIOUS -> null
            }
            SpecialKey.LEFT -> Icon.LEFT
            SpecialKey.RIGHT -> Icon.RIGHT
            SpecialKey.UP -> Icon.UP
            SpecialKey.DOWN -> Icon.DOWN
            SpecialKey.VOICE -> Icon.MIC
            SpecialKey.SETTINGS -> Icon.GEAR
            SpecialKey.HIDE -> Icon.HIDE
            SpecialKey.MODE -> if (ctx.mode == KeyboardMode.COMPUTER) Icon.PHONE else Icon.KEYBOARD
            SpecialKey.LAYER_EMOJI -> Icon.EMOJI
            SpecialKey.EMOJI_PREVIOUS -> Icon.LEFT
            SpecialKey.EMOJI_NEXT -> Icon.RIGHT
            SpecialKey.STICKERS -> Icon.STICKER
            else -> null
        }
    }

    private fun drawPopup(canvas: Canvas, p: Popup) {
        keyPaint.color = theme.popup
        canvas.drawRoundRect(p.bounds, corner, corner, keyPaint)
        p.cells.forEachIndexed { i, cell ->
            if (i == p.selected) {
                keyPaint.color = theme.accent
                canvas.drawRoundRect(cell, corner, corner, keyPaint)
            }
            drawLabel(canvas, cell, p.options[i], if (i == p.selected) theme.onAccent else theme.text, p.key)
        }
    }

    // ------------------------------------------------------------------ touch

    private inner class PointerState(var key: Key?, val downX: Float, val downY: Float) {
        var longPressFired = false
        var repeating = false
        var swipeConsumed = false
        var longPress: Runnable? = null
        var repeat: Runnable? = null

        fun cancelCallbacks() {
            longPress?.let(handler::removeCallbacks)
            repeat?.let(handler::removeCallbacks)
            longPress = null
            repeat = null
        }
    }

    private class Popup(val key: Key, val options: List<String>, val bounds: RectF, val cells: List<RectF>, val pointerId: Int) {
        var selected = 0
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                onPointerDown(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) onPointerMove(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                onPointerUp(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_CANCEL -> cancelAllPointers()
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): Key? {
        keyRects.firstOrNull { (_, r) -> r.contains(x, y) }?.let { return it.first.takeIf { k -> k.style != KeyStyle.SPACER } }
        // Touches in the gaps or just outside go to the nearest key.
        return keyRects.filter { it.first.style != KeyStyle.SPACER }
            .minByOrNull { (_, r) -> distanceSq(r, x, y) }?.first
    }

    private fun distanceSq(r: RectF, x: Float, y: Float): Float {
        val dx = maxOf(r.left - x, 0f, x - r.right)
        val dy = maxOf(r.top - y, 0f, y - r.bottom)
        return dx * dx + dy * dy
    }

    private fun onPointerDown(id: Int, x: Float, y: Float) {
        val key = keyAt(x, y) ?: return
        val state = PointerState(key, x, y)
        pointers.put(id, state)
        feedback()

        if (key.repeatable) {
            state.repeat = object : Runnable {
                override fun run() {
                    state.repeating = true
                    listener?.onKey(key)
                    handler.postDelayed(this, REPEAT_INTERVAL_MS)
                }
            }.also { handler.postDelayed(it, REPEAT_START_MS) }
        } else if (key.alternatives.isNotEmpty() || isLanguageKey(key)) {
            state.longPress = Runnable {
                state.longPressFired = true
                if (isLanguageKey(key)) {
                    pointers.remove(id)
                    listener?.onLanguageKeyLongPress()
                } else {
                    openPopup(id, key)
                }
                invalidate()
            }.also { handler.postDelayed(it, longPressMillis) }
        }
        invalidate()
    }

    private fun onPointerMove(id: Int, x: Float, y: Float) {
        val state = pointers[id] ?: return
        val p = popup
        if (p != null && p.pointerId == id) {
            val index = p.cells.indexOfFirst { x >= it.left && x <= it.right }
            p.selected = when {
                index >= 0 -> index
                x < p.cells.first().left -> 0
                else -> p.cells.lastIndex
            }
            invalidate()
            return
        }
        val key = state.key ?: return
        if (isSpace(key) && spaceSwipeEnabled && !state.swipeConsumed && abs(x - state.downX) > dp(SPACE_SWIPE_DP)) {
            state.swipeConsumed = true
            state.cancelCallbacks()
            listener?.onSpaceSwipe(if (x > state.downX) 1 else -1)
            invalidate()
        }
    }

    private fun onPointerUp(id: Int, x: Float, y: Float) {
        val state = pointers[id] ?: return
        pointers.remove(id)
        state.cancelCallbacks()
        val p = popup
        if (p != null && p.pointerId == id) {
            popup = null
            listener?.onAlternative(p.options[p.selected])
        } else {
            val key = state.key
            if (key != null && !state.repeating && !state.longPressFired && !state.swipeConsumed) {
                listener?.onKey(key)
            }
        }
        invalidate()
    }

    private fun openPopup(pointerId: Int, key: Key) {
        val anchor = keyRects.firstOrNull { it.first === key }?.second ?: return
        val options = key.alternatives
        val cellWidth = maxOf(anchor.width(), dp(36f))
        val total = cellWidth * options.size
        var left = anchor.centerX() - cellWidth / 2
        left = left.coerceIn(0f, maxOf(0f, width - total))
        val top = maxOf(0f, anchor.top - keyHeightPx * 1.1f)
        val cells = options.indices.map { i ->
            RectF(left + i * cellWidth, top, left + (i + 1) * cellWidth, top + keyHeightPx)
        }
        popup = Popup(key, options, RectF(left, top, left + total, top + keyHeightPx), cells, pointerId)
        feedback()
    }

    private fun isSpace(key: Key) = (key.action as? KeyAction.Special)?.key == SpecialKey.SPACE
    private fun isLanguageKey(key: Key) = (key.action as? KeyAction.Special)?.key == SpecialKey.LANGUAGE

    /** Vibration for a tap, as set in settings; also used by the sticker panel. */
    fun feedback() {
        if (!hapticFeedback) return
        val v = vibrator
        if (v == null || !v.hasVibrator()) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            return
        }
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(
                when (vibration) {
                    VibrationStrength.LIGHT -> VibrationEffect.EFFECT_TICK
                    VibrationStrength.MEDIUM -> VibrationEffect.EFFECT_CLICK
                    VibrationStrength.STRONG -> VibrationEffect.EFFECT_HEAVY_CLICK
                },
            )
        } else {
            val (ms, amplitude) = when (vibration) {
                VibrationStrength.LIGHT -> 8L to 60
                VibrationStrength.MEDIUM -> 15L to 140
                VibrationStrength.STRONG -> 28L to 255
            }
            VibrationEffect.createOneShot(ms, if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE)
        }
        runCatching { v.vibrate(effect) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAllPointers()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val REPEAT_START_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L
        const val SPACE_SWIPE_DP = 60f
    }
}
