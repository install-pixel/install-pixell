package com.offlinekeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.util.forEach
import com.offlinekeyboard.R
import com.offlinekeyboard.core.KeyLabels
import com.offlinekeyboard.core.LabelContext
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyStyle
import com.offlinekeyboard.core.layout.KeyboardLayout
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

    private val colorBackground = context.getColor(R.color.kb_background)
    private val colorKey = context.getColor(R.color.kb_key)
    private val colorFunction = context.getColor(R.color.kb_key_function)
    private val colorAction = context.getColor(R.color.kb_key_action)
    private val colorPressed = context.getColor(R.color.kb_key_pressed)
    private val colorActive = context.getColor(R.color.kb_key_active)
    private val colorText = context.getColor(R.color.kb_text)
    private val colorTextOnAction = context.getColor(R.color.kb_text_on_action)
    private val colorHint = context.getColor(R.color.kb_hint)
    private val colorPopup = context.getColor(R.color.kb_popup)

    init {
        setBackgroundColor(colorBackground)
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

    /** Stops repeats and popups, e.g. when the keyboard is hidden mid-press. */
    fun cancelAllPointers() {
        pointers.forEach { _, state -> state.cancelCallbacks() }
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
        pointers.forEach { _, state -> state.key?.let(pressedKeys::add) }

        for ((key, rect) in keyRects) {
            if (key.style == KeyStyle.SPACER) continue
            val inner = RectF(rect.left + keyGap / 2, rect.top + keyGap / 2, rect.right - keyGap / 2, rect.bottom - keyGap / 2)
            val state = highlight(key)
            keyPaint.color = when {
                key in pressedKeys -> colorPressed
                state != KeyHighlight.NONE -> colorActive
                key.style == KeyStyle.ACTION -> colorAction
                key.style == KeyStyle.FUNCTION || key.style == KeyStyle.MODIFIER -> colorFunction
                else -> colorKey
            }
            canvas.drawRoundRect(inner, corner, corner, keyPaint)

            val onColor = key.style == KeyStyle.ACTION || state != KeyHighlight.NONE
            drawLabel(canvas, inner, KeyLabels.label(key, ctx), if (onColor) colorTextOnAction else colorText, key)

            KeyLabels.hint(key, ctx)?.let { hint ->
                hintPaint.color = colorHint
                hintPaint.textSize = inner.height() * 0.24f
                canvas.drawText(hint, inner.right - inner.width() * 0.1f, inner.top + hintPaint.textSize * 1.05f, hintPaint)
            }
            if (state == KeyHighlight.LOCKED) {
                dotPaint.color = colorTextOnAction
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

    private fun drawPopup(canvas: Canvas, p: Popup) {
        keyPaint.color = colorPopup
        canvas.drawRoundRect(p.bounds, corner, corner, keyPaint)
        p.cells.forEachIndexed { i, cell ->
            if (i == p.selected) {
                keyPaint.color = colorActive
                canvas.drawRoundRect(cell, corner, corner, keyPaint)
            }
            drawLabel(canvas, cell, p.options[i], if (i == p.selected) colorTextOnAction else colorText, p.key)
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

    private fun feedback() {
        if (hapticFeedback) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
