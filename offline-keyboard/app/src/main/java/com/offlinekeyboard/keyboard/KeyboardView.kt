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
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.offlinekeyboard.R
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
        /** Backspace dragged to the left: [words] words before the cursor; [done] when the finger lifts. */
        fun onBackspaceSwipe(words: Int, done: Boolean)
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

    /** Dragging Backspace to the left deletes whole words. */
    var backspaceSwipeEnabled = true

    /** Shows the pressed letter enlarged above the finger (drawn over the toolbar for the top row). */
    var keyPreview = true
    var longPressMillis = 350L

    private var layout = KeyboardLayout(emptyList())
    private var labelContext: LabelContext? = null
    private var highlight: (Key) -> KeyHighlight = { KeyHighlight.NONE }
    private var keyHeightPx = dp(48f)

    private val keyRects = ArrayList<Pair<Key, RectF>>()
    private val pointers = SparseArray<PointerState>()
    private val handler = Handler(Looper.getMainLooper())

    private var popup: Popup? = null
    private var popupAt = 0L
    private var closingPopup: Popup? = null
    private var closingAt = 0L

    /** When each key was let go, for the soft fade of its pressed glow. */
    private val released = HashMap<Key, Long>()

    private val keyGap = dp(3f)
    private val corner = dp(6f)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val surfaces = SurfacePainter(resources.displayMetrics.density)
    private val frost = Frost()
    private var letterFace: Typeface = Typeface.DEFAULT
    private var labelFace: Typeface = Typeface.DEFAULT_BOLD

    var theme: KeyboardTheme = KeyboardTheme.glass()
        set(value) {
            if (field == value) return
            field = value
            surfaces.brightness = value.brightness
            setBackgroundColor(value.background)
            val (letters, labels) = faces(value)
            letterFace = letters
            labelFace = labels
            hintPaint.typeface = letters
            invalidate()
        }

    /** Letters and word labels in the theme's built-in font (Exo 2, Caveat) or the system one. */
    private fun faces(theme: KeyboardTheme): Pair<Typeface, Typeface> {
        fun font(id: Int) = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
        return when (theme.style) {
            SurfaceStyle.FLAT -> Typeface.DEFAULT to Typeface.DEFAULT_BOLD
            SurfaceStyle.GLASS ->
                (font(R.font.exo2_regular) ?: Typeface.DEFAULT) to (font(R.font.exo2_semibold) ?: Typeface.DEFAULT_BOLD)
            SurfaceStyle.SHEET -> (font(R.font.caveat_semibold) ?: Typeface.DEFAULT).let { it to it }
        }
    }

    /** Keys of the glass and sheet styles are rounder (THEMES.md: items 14–16 dp, scaled to a key). */
    private val radius: Float get() = if (theme.styled) dp(9f) else corner

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
        excludeBackGesture(w, h)
    }

    /**
     * With gesture navigation a swipe from the screen edge is "Back". Over the keys it should be a key
     * swipe (Backspace at the right edge deletes words), so the keyboard asks Android to leave both edges
     * to it. Android grants at most 200 dp of each edge; the lower rows, where Backspace and Shift are,
     * come first.
     */
    private fun excludeBackGesture(w: Int, h: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || w == 0 || h == 0) return
        val top = (h - (EXCLUSION_DP * resources.displayMetrics.density).toInt()).coerceAtLeast(0)
        systemGestureExclusionRects = listOf(android.graphics.Rect(0, top, w, h))
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
        val now = SystemClock.uptimeMillis()
        drawKeys(canvas, ctx, now)
        val p = popup
        updateBubbles(if (p != null) emptyList() else previewBubbles(ctx), now)
        val closing = closingPopup?.takeIf { now - closingAt < POPUP_FADE_MS }
        if (closing == null) closingPopup = null
        if (theme.style == SurfaceStyle.GLASS && (p != null || closing != null || bubbles.isNotEmpty())) {
            frost.capture(this) { drawKeys(it, ctx, now) }
        }
        for (b in bubbles) drawBubble(canvas, b)
        if (closing != null) drawPopup(canvas, closing, 1f - (now - closingAt) / POPUP_FADE_MS.toFloat())
        if (p != null) drawPopup(canvas, p, ((now - popupAt) / POPUP_FADE_MS.toFloat()).coerceAtMost(1f))
        // Keep drawing while something fades or grows; nothing is redrawn once it all stands still.
        // A letter that has fully grown and is still held stands still: no more frames until something changes.
        val moving = bubbles.any { !it.pressed || it.shown < 1f } || closing != null || (p != null && now - popupAt < POPUP_FADE_MS) ||
            released.values.any { now - it < RELEASE_FADE_MS }
        if (moving) postInvalidateOnAnimation()
    }

    private fun drawKeys(canvas: Canvas, ctx: LabelContext, now: Long) {
        val pressedKeys = HashSet<Key>()
        for (i in 0 until pointers.size()) pointers.valueAt(i).key?.let(pressedKeys::add)
        released.entries.removeAll { now - it.value >= RELEASE_FADE_MS }

        for ((index, entry) in keyRects.withIndex()) {
            val (key, rect) = entry
            if (key.style == KeyStyle.SPACER) continue
            val inner = RectF(rect.left + keyGap / 2, rect.top + keyGap / 2, rect.right - keyGap / 2, rect.bottom - keyGap / 2)
            val state = highlight(key)
            val t = theme
            val pressed = key in pressedKeys
            val isAction = key.style == KeyStyle.ACTION
            val color: Int
            if (t.styled) {
                // Glass and sheet: an active key gets a brighter rim, Enter is written in the confirm blue.
                val surface = when {
                    pressed -> SurfaceState.PRESSED
                    state != KeyHighlight.NONE -> SurfaceState.SELECTED
                    else -> SurfaceState.NORMAL
                }
                color = if (isAction) t.confirm else t.text
                surfaces.draw(canvas, inner, radius, t.style, t.key, surface, seed = index, inkColor = color)
                // A released key lets its pressed glow go softly instead of switching it off.
                val fade = released[key]?.let { 1f - (now - it) / RELEASE_FADE_MS.toFloat() }
                if (!pressed && fade != null && fade > 0f) {
                    val layer = canvas.saveLayerAlpha(inner, (fade * fade * 255).toInt())
                    surfaces.draw(canvas, inner, radius, t.style, t.key, SurfaceState.PRESSED, seed = index, inkColor = color)
                    canvas.restoreToCount(layer)
                }
            } else {
                val fill = when {
                    pressed -> t.pressed
                    state != KeyHighlight.NONE -> t.accent
                    isAction -> t.accent
                    key.style == KeyStyle.FUNCTION || key.style == KeyStyle.MODIFIER -> t.function
                    else -> t.key
                }
                surfaces.draw(canvas, inner, corner, SurfaceStyle.FLAT, fill)
                val onAccent = isAction || state != KeyHighlight.NONE
                color = if (onAccent && !pressed) t.onAccent else t.text
            }
            val icon = iconFor(key, ctx, state)
            if (icon != null) {
                val size = minOf(inner.height() * 0.48f, inner.width() * 0.62f, dp(26f))
                Icons.draw(canvas, icon, inner.centerX(), inner.centerY(), size, color)
            } else {
                drawLabel(canvas, inner, KeyLabels.label(key, ctx), color, key)
            }

            KeyLabels.hint(key, ctx)?.let { hint ->
                // The digit sits small and quiet in the top corner, clear of the letter even in capitals.
                hintPaint.color = (t.hint and 0x00FFFFFF) or 0xB3000000.toInt()
                hintPaint.textSize = inner.height() * 0.19f * (1f + (theme.textScale - 1f) / 2f)
                canvas.drawText(hint, inner.right - minOf(inner.width() * 0.12f, dp(5f)), inner.top + hintPaint.textSize + dp(2.5f), hintPaint)
            }
            if (state == KeyHighlight.LOCKED && icon != Icon.CAPS) {
                dotPaint.color = color
                canvas.drawCircle(inner.centerX(), inner.bottom - dp(5f), dp(2.5f), dotPaint)
            }
        }
    }

    /** The enlarged letters above the pressed keys: bubble and label. */
    private fun previewBubbles(ctx: LabelContext): List<Triple<Key, RectF, String>> {
        if (!keyPreview) return emptyList()
        val out = ArrayList<Triple<Key, RectF, String>>(2)
        for (i in 0 until pointers.size()) {
            val state = pointers.valueAt(i)
            val key = state.key ?: continue
            if (key.action !is KeyAction.Text || state.repeating) continue
            val label = KeyLabels.label(key, ctx)
            if (label.isEmpty() || label.length > 4) continue
            val anchor = keyRects.firstOrNull { it.first === key }?.second ?: continue
            val w = maxOf(anchor.width() * 1.5f, dp(44f))
            val h = keyHeightPx * 1.2f
            val left = (anchor.centerX() - w / 2).coerceIn(0f, maxOf(0f, width - w))
            val bottom = anchor.top + keyGap
            out += Triple(key, RectF(left, bottom - h, left + w, bottom), label)
        }
        return out
    }

    /**
     * A bubble or popup surface that hides what is under it: frosted glass (the keys underneath, blurred, seen
     * through dark glass) or black paper under the pencil frame.
     */
    private fun drawOverlaySurface(canvas: Canvas, rect: RectF, r: Float, seed: Int) {
        val t = theme
        when (t.style) {
            SurfaceStyle.GLASS -> {
                frost.drawBehind(canvas, rect, r, t.background)
                surfaces.draw(canvas, rect, r, t.style, t.popup, seed = seed, inkColor = t.text, sheen = true, frosted = true)
            }
            SurfaceStyle.SHEET -> {
                surfaces.draw(canvas, rect, r, SurfaceStyle.FLAT, t.background)
                surfaces.draw(canvas, rect, r, t.style, t.popup, seed = seed, inkColor = t.text)
            }
            SurfaceStyle.FLAT -> surfaces.draw(canvas, rect, r, t.style, t.popup)
        }
    }

    /** An enlarged letter on its way in (while pressed) or out (after release); [shown] runs 0 → 1. */
    private class Bubble(val key: Key, var rect: RectF, var label: String, var at: Long) {
        var shown = 0f
        var pressed = true
    }

    private val bubbles = ArrayList<Bubble>(2)

    /** Grows the bubbles of pressed keys and fades out the others, by the time passed since the last frame. */
    private fun updateBubbles(live: List<Triple<Key, RectF, String>>, now: Long) {
        for (b in bubbles) b.pressed = false
        for ((key, rect, label) in live) {
            // Pressing a key again while its letter still fades brings that letter back instead of a new one.
            val b = bubbles.firstOrNull { it.key === key } ?: Bubble(key, rect, label, now).also { bubbles += it }
            b.pressed = true
            b.rect = rect
            b.label = label
        }
        val it = bubbles.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val dt = (now - b.at).coerceAtLeast(0L)
            b.at = now
            b.shown = if (b.pressed) {
                (b.shown + dt / BUBBLE_IN_MS.toFloat()).coerceAtMost(1f)
            } else {
                (b.shown - dt / BUBBLE_OUT_MS.toFloat()).coerceAtLeast(0f)
            }
            if (!b.pressed && b.shown <= 0f) it.remove()
        }
    }

    private fun drawBubble(canvas: Canvas, b: Bubble) {
        // Eased in and out: rises a little from the key, grows from 88 % and fades, with no jump at either end.
        val e = 1f - (1f - b.shown) * (1f - b.shown) * (1f - b.shown)
        if (e <= 0.01f) return
        val bubble = b.rect
        val scale = 0.88f + 0.12f * e
        val layer = canvas.saveLayerAlpha(
            bubble.left - dp(4f), bubble.top - dp(4f), bubble.right + dp(4f), bubble.bottom + dp(10f), (e * 255).toInt(),
        )
        canvas.translate(0f, (1f - e) * dp(6f))
        canvas.scale(scale, scale, bubble.centerX(), bubble.bottom)
        drawOverlaySurface(canvas, bubble, radius * 1.5f, b.label.hashCode())
        textPaint.color = theme.text
        textPaint.typeface = letterFace
        textPaint.textSize = bubble.height() * 0.5f * theme.textScale
        val y = bubble.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(b.label, bubble.centerX(), y, textPaint)
        canvas.restoreToCount(layer)
    }

    private fun drawLabel(canvas: Canvas, rect: RectF, label: String, color: Int, key: Key) {
        if (label.isEmpty()) return
        textPaint.color = color
        val isLetter = key.action is KeyAction.Text && label.length <= 2
        textPaint.typeface = if (isLetter) letterFace else labelFace
        var size = rect.height() * (if (isLetter) 0.46f else 0.30f) * theme.textScale
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

    private fun drawPopup(canvas: Canvas, p: Popup, shown: Float) {
        val t = theme
        // Fades and grows in (and out) like the enlarged letter.
        val e = 1f - (1f - shown) * (1f - shown) * (1f - shown)
        if (e <= 0.01f) return
        val b = p.bounds
        val layer = canvas.saveLayerAlpha(b.left - dp(4f), b.top - dp(4f), b.right + dp(4f), b.bottom + dp(10f), (e * 255).toInt())
        val scale = 0.92f + 0.08f * e
        canvas.scale(scale, scale, b.centerX(), b.bottom)
        drawOverlaySurface(canvas, p.bounds, radius, 7)
        p.cells.forEachIndexed { i, cell ->
            if (i == p.selected) {
                if (t.styled) {
                    surfaces.draw(canvas, cell, radius, t.style, t.popup, SurfaceState.SELECTED, seed = i, inkColor = t.text)
                } else {
                    surfaces.draw(canvas, cell, corner, SurfaceStyle.FLAT, t.accent)
                }
            }
            val chosen = i == p.selected && !t.styled
            // The option under the finger is full bright; the others a little quieter, so the choice is plain.
            val color = when {
                chosen -> t.onAccent
                i == p.selected -> t.text
                else -> (t.text and 0x00FFFFFF) or 0xA6000000.toInt()
            }
            drawLabel(canvas, cell, p.options[i], color, p.key)
        }
        canvas.restoreToCount(layer)
    }

    // ------------------------------------------------------------------ touch

    private inner class PointerState(var key: Key?, val downX: Float, val downY: Float) {
        var longPressFired = false
        var repeating = false
        var swipeConsumed = false
        /** Words chosen by dragging Backspace to the left; -1 while it is an ordinary press. */
        var swipeWords = -1
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
                    // A light tick for every character a held key deletes (or moves), not only the first.
                    feedback(light = true)
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
        if (isBackspace(key) && backspaceSwipeEnabled) {
            val dx = state.downX - x
            if (state.swipeWords < 0 && dx > dp(BACKSPACE_SWIPE_START_DP)) {
                // Turns into a word swipe: the key repeat stops.
                state.swipeConsumed = true
                state.cancelCallbacks()
                state.swipeWords = 0
            }
            if (state.swipeWords >= 0) {
                val words = if (dx < dp(BACKSPACE_SWIPE_START_DP)) 0 else 1 + ((dx - dp(BACKSPACE_SWIPE_START_DP)) / dp(BACKSPACE_SWIPE_STEP_DP)).toInt()
                if (words != state.swipeWords) {
                    state.swipeWords = words
                    feedback()
                    listener?.onBackspaceSwipe(words, done = false)
                }
            }
        }
    }

    private fun onPointerUp(id: Int, x: Float, y: Float) {
        val state = pointers[id] ?: return
        pointers.remove(id)
        state.cancelCallbacks()
        state.key?.let { released[it] = SystemClock.uptimeMillis() }
        val p = popup
        if (p != null && p.pointerId == id) {
            popup = null
            closingPopup = p
            closingAt = SystemClock.uptimeMillis()
            listener?.onAlternative(p.options[p.selected])
        } else {
            val key = state.key
            if (state.swipeWords >= 0) {
                listener?.onBackspaceSwipe(state.swipeWords, done = true)
            } else if (key != null && !state.repeating && !state.longPressFired && !state.swipeConsumed) {
                listener?.onKey(key)
            }
        }
        invalidate()
    }

    private fun openPopup(pointerId: Int, key: Key) {
        val anchor = keyRects.firstOrNull { it.first === key }?.second ?: return
        val options = key.alternatives
        // The same place and size as the enlarged letter: above the key (over the toolbar for the top row,
        // never under the finger), as tall as the letter, each option as wide as a key and a bit.
        val cellWidth = maxOf(anchor.width() * 1.2f, dp(40f))
        val total = cellWidth * options.size
        var left = anchor.centerX() - cellWidth / 2
        left = left.coerceIn(0f, maxOf(0f, width - total))
        val height = keyHeightPx * 1.2f
        val bottom = anchor.top + keyGap
        val top = bottom - height
        val cells = options.indices.map { i ->
            RectF(left + i * cellWidth, top, left + (i + 1) * cellWidth, bottom)
        }
        popup = Popup(key, options, RectF(left, top, left + total, bottom), cells, pointerId)
        popupAt = SystemClock.uptimeMillis()
        feedback()
    }

    private fun isSpace(key: Key) = (key.action as? KeyAction.Special)?.key == SpecialKey.SPACE
    private fun isBackspace(key: Key) = (key.action as? KeyAction.Special)?.key == SpecialKey.BACKSPACE
    private fun isLanguageKey(key: Key) = (key.action as? KeyAction.Special)?.key == SpecialKey.LANGUAGE

    /** Vibration for a tap, as set in settings; also used by the sticker panel. */
    fun feedback(light: Boolean = false) {
        if (!hapticFeedback) return
        val v = vibrator
        if (v == null || !v.hasVibrator()) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            return
        }
        val effect = tapEffect(if (light) VibrationStrength.LIGHT else vibration, v)
        runCatching { v.vibrate(effect) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAllPointers()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val EXCLUSION_DP = 200
        const val REPEAT_START_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L
        const val SPACE_SWIPE_DP = 60f
        const val BACKSPACE_SWIPE_START_DP = 28f
        const val BACKSPACE_SWIPE_STEP_DP = 30f

        // Soft timings: quick enough to follow fast typing, long enough that nothing pops.
        const val BUBBLE_IN_MS = 70L
        const val BUBBLE_OUT_MS = 150L
        const val POPUP_FADE_MS = 130L
        const val RELEASE_FADE_MS = 170L
    }
}
