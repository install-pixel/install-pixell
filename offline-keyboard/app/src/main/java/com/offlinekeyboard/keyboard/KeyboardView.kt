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
import kotlin.math.cos

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
        /** The space bar held and dragged: move the cursor [steps] characters (negative: to the left). */
        fun onCursorMove(steps: Int) {}
        /** Whether one more repeat of a held key changes anything (Backspace with text left to delete). */
        fun repeatHasEffect(key: Key): Boolean = true
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
    /** Key sounds (the typewriter style) and their volume, 0 for silence. */
    var keySounds: KeySounds? = null
    var soundVolume = 0f

    private fun sound(key: Key) {
        val sounds = keySounds ?: return
        if (soundVolume <= 0f) return
        val kind = when ((key.action as? KeyAction.Special)?.key) {
            SpecialKey.SPACE -> KeySounds.Kind.SPACE
            SpecialKey.ENTER -> KeySounds.Kind.RETURN
            SpecialKey.BACKSPACE, SpecialKey.DELETE -> KeySounds.Kind.DELETE
            else -> KeySounds.Kind.KEY
        }
        sounds.play(kind, soundVolume)
    }

    /** Hold the space bar, then slide: the cursor follows the finger. */
    var spaceCursorEnabled = true

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
            SurfaceStyle.TYPEWRITER ->
                (font(R.font.cousine_bold) ?: Typeface.MONOSPACE) to (font(R.font.cousine_regular) ?: Typeface.MONOSPACE)
        }
    }

    /** Keys of the glass and sheet styles are rounder (THEMES.md: items 14–16 dp, scaled to a key). */
    private val radius: Float get() = if (theme.styled) dp(9f) else corner

    init {
        setBackgroundColor(theme.background)
        isHapticFeedbackEnabled = true
    }

    /**
     * Set before [bind] when the next layout is another language or layer: on the typewriter the old keys
     * slide out that way (1 = to the right) and the new ones slide in after them. 0: no slide.
     */
    var slideDirection = 0

    private var slideFrom: android.graphics.Bitmap? = null
    private var slideAt = 0L
    private var slideWay = 0

    fun bind(layout: KeyboardLayout, labels: LabelContext, keyHeightDp: Float, highlight: (Key) -> KeyHighlight) {
        val heightChanged = dp(keyHeightDp) != keyHeightPx || layout.rows.size != this.layout.rows.size
        val layoutChanged = layout != this.layout
        val way = slideDirection
        slideDirection = 0
        if (layoutChanged && way != 0) snapshotForSlide(way)
        this.layout = layout
        this.labelContext = labels
        this.highlight = highlight
        keyHeightPx = dp(keyHeightDp)
        if (layoutChanged) {
            // Sliding the cursor on the space bar goes on through a new layout (Shift turning on at the start
            // of a sentence); anything else under a finger is let go.
            val sliding = (0 until pointers.size()).any { pointers.valueAt(it).cursorMode }
            if (!sliding) cancelAllPointers()
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
        // Typewriter letter keys are all one size, as on the machine: as large as the narrowest allows.
        typewriterSide = keyRects.filter { it.first.style != KeyStyle.SPACER && !isTypewriterBigKey(it.first) }
            .minOfOrNull { minOf(it.second.width(), it.second.height()) - keyGap * KEY_GAP_SHARE } ?: 0f
    }

    /** The diameter of a round typewriter key (and the height of a long one). */
    private var typewriterSide = 0f

    /**
     * The typewriter key in its [cell]: letters and marks are circles of [typewriterSide] (or pills as long
     * as the cell); Shift, Backspace, Enter, the layer and language keys use their own room, a bigger circle
     * or a taller pill, like the big keys of the machine.
     */
    private fun typewriterBody(cell: RectF, key: Key): RectF {
        val gap = keyGap * KEY_GAP_SHARE
        val cw = cell.width() - gap
        val ch = cell.height() - gap
        val side = typewriterSide.takeIf { it > 0f } ?: minOf(cw, ch)
        val h = if (isTypewriterBigKey(key)) minOf(ch, cw, side * BIG_KEY_SCALE) else minOf(side, cw, ch)
        val w = if (cw <= h * 1.3f) h else cw
        return RectF(cell.centerX() - w / 2, cell.centerY() - h / 2, cell.centerX() + w / 2, cell.centerY() + h / 2)
    }

    /** Keys that are not a letter or a mark: on the typewriter they take their own, bigger size. */
    private fun isTypewriterBigKey(key: Key) = key.action !is KeyAction.Text

    /** One of the most used letters of any of the languages: shows more wear on the typewriter. */
    private fun isWorn(key: Key) = letterOf(key)?.let { it in WORN_LETTERS } == true

    /**
     * The machine behind the typewriter keys: a straight steel lever from every key up into the machine,
     * towards where the letter strikes (the strip above); the levers pivot up there, so a pressed key pulls
     * its end of the lever down.
     */
    private fun drawMachine(canvas: Canvas, pressedKeys: Set<Key>, now: Long) {
        val w = maxOf(dp(2.4f), typewriterSide * 0.09f)
        for ((key, rect) in keyRects) {
            if (key.style == KeyStyle.SPACER) continue
            val body = typewriterBody(rect, key)
            val sink = typewriterDepth(key, pressedKeys, now) * surfaces.typewriterSink
            val long = body.width() > body.height() * 1.3f
            val ends = if (long) floatArrayOf(body.left + body.height() / 2, body.right - body.height() / 2) else floatArrayOf(body.centerX())
            for (x in ends) {
                surfaces.drawTypewriterRod(canvas, x + (width / 2f - x) * LEVER_CONVERGE, -dp(2f), x, body.centerY() + sink, w)
            }
        }
        surfaces.drawTypewriterDepth(canvas, width.toFloat(), dp(16f))
    }

    /**
     * How far down a typewriter key is (1 = all the way): down while held, then springing back up with a
     * small overshoot, as a key on its spring does.
     */
    private fun typewriterDepth(key: Key, pressedKeys: Set<Key>, now: Long): Float {
        if (key in pressedKeys) return 1f
        val at = released[key] ?: return 0f
        val t = ((now - at) / TYPEWRITER_SPRING_MS.toFloat()).coerceIn(0f, 1f)
        // Back up on its spring: a quick swing that dies away; above the rest it rises only a little.
        val left = 1f - t
        val swing = left * left * left * cos(t * SPRING_SWINGS * 2f * Math.PI.toFloat())
        return if (swing < 0f) swing * SPRING_OVERSHOOT else swing
    }

    // ------------------------------------------------------------------ drawing

    /** Keeps a picture of the keys as they are now, to slide out while the new layout slides in. */
    private fun snapshotForSlide(way: Int) {
        val ctx = labelContext ?: return
        if (theme.style != SurfaceStyle.TYPEWRITER || width == 0 || height == 0 || keyRects.isEmpty()) return
        val bitmap = slideFrom?.takeIf { it.width == width && it.height == height }
            ?: runCatching { android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888) }.getOrNull()
            ?: return
        bitmap.eraseColor(theme.background)
        drawKeys(Canvas(bitmap), ctx, SystemClock.uptimeMillis())
        slideFrom = bitmap
        slideAt = SystemClock.uptimeMillis()
        slideWay = way
    }

    override fun onDraw(canvas: Canvas) {
        val ctx = labelContext ?: return
        val now = SystemClock.uptimeMillis()
        // Switching layouts on the typewriter: the old keys slide out, the new ones follow them in.
        val old = slideFrom
        val slide = if (old != null) ((now - slideAt) / SLIDE_MS.toFloat()).coerceIn(0f, 1f) else 1f
        if (old != null && slide < 1f) {
            val e = 1f - (1f - slide) * (1f - slide)
            canvas.save()
            canvas.translate(slideWay * e * width, 0f)
            canvas.drawBitmap(old, 0f, 0f, null)
            canvas.restore()
            canvas.save()
            canvas.translate(-slideWay * (1f - e) * width, 0f)
            drawKeys(canvas, ctx, now)
            canvas.restore()
            postInvalidateOnAnimation()
            return
        }
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
            released.values.any { now - it < RELEASE_KEEP_MS }
        if (moving) postInvalidateOnAnimation()
    }

    private fun drawKeys(canvas: Canvas, ctx: LabelContext, now: Long) {
        val pressedKeys = HashSet<Key>()
        for (i in 0 until pointers.size()) pointers.valueAt(i).key?.let(pressedKeys::add)
        released.entries.removeAll { now - it.value >= RELEASE_KEEP_MS }

        val typewriter = theme.style == SurfaceStyle.TYPEWRITER
        if (typewriter) drawMachine(canvas, pressedKeys, now)
        for ((index, entry) in keyRects.withIndex()) {
            val (key, rect) = entry
            if (key.style == KeyStyle.SPACER) continue
            val inner = RectF(rect.left + keyGap / 2, rect.top + keyGap / 2, rect.right - keyGap / 2, rect.bottom - keyGap / 2)
            // Where the label goes: the key, or on the typewriter the black cap inside the rim, down when pressed.
            var face = inner
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
                if (typewriter) {
                    // A typewriter key goes down and springs back up instead of glowing.
                    val body = typewriterBody(rect, key)
                    val depth = typewriterDepth(key, pressedKeys, now)
                    surfaces.typewriterDepth = depth
                    surfaces.typewriterWorn = isWorn(key)
                    surfaces.draw(canvas, body, radius, t.style, t.key, surface, seed = index, inkColor = color)
                    surfaces.typewriterDepth = -2f
                    surfaces.typewriterWorn = false
                    val rim = surfaces.typewriterRim(body.height())
                    face = RectF(body).apply {
                        inset(rim, rim)
                        offset(0f, depth * surfaces.typewriterSink)
                    }
                } else {
                    surfaces.draw(canvas, inner, radius, t.style, t.key, surface, seed = index, inkColor = color)
                }
                // A released key lets its pressed glow go softly instead of switching it off.
                val fade = released[key]?.let { 1f - (now - it) / RELEASE_FADE_MS.toFloat() }
                if (!typewriter && !pressed && fade != null && fade > 0f) {
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
                val size = if (typewriter) {
                    minOf(face.height() * 0.56f, face.width() * 0.62f, dp(24f))
                } else {
                    minOf(inner.height() * 0.48f, inner.width() * 0.62f, dp(26f))
                }
                Icons.draw(canvas, icon, face.centerX(), face.centerY(), size, color)
            } else {
                // On the most used typewriter keys the paint of the letter is a little worn.
                val ink = if (typewriter && isWorn(key)) (color and 0x00FFFFFF) or (((color ushr 24) * WORN_INK).toInt() shl 24) else color
                drawLabel(canvas, face, KeyLabels.label(key, ctx), ink, key)
            }

            KeyLabels.hint(key, ctx)?.let { hint ->
                // The digit sits small and quiet in the top corner, clear of the letter even in capitals.
                hintPaint.color = (t.hint and 0x00FFFFFF) or 0xB3000000.toInt()
                if (typewriter) {
                    // Inside the cap, up and to the right of the letter: nothing sticks out of the key.
                    hintPaint.textSize = face.height() * 0.2f
                    hintPaint.textAlign = Paint.Align.CENTER
                    val y = face.centerY() - face.height() * 0.26f - (hintPaint.descent() + hintPaint.ascent()) / 2
                    canvas.drawText(hint, face.centerX() + face.width() * 0.25f, y, hintPaint)
                    hintPaint.textAlign = Paint.Align.RIGHT
                } else {
                    hintPaint.textSize = inner.height() * 0.19f * (1f + (theme.textScale - 1f) / 2f)
                    canvas.drawText(hint, inner.right - minOf(inner.width() * 0.12f, dp(5f)), inner.top + hintPaint.textSize + dp(2.5f), hintPaint)
                }
            }
            if (state == KeyHighlight.LOCKED && icon != Icon.CAPS) {
                dotPaint.color = color
                val y = if (typewriter) face.bottom - face.height() * 0.14f else inner.bottom - dp(5f)
                canvas.drawCircle(face.centerX(), y, dp(if (typewriter) 2f else 2.5f), dotPaint)
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
            SurfaceStyle.TYPEWRITER -> {
                surfaces.draw(canvas, rect, r, SurfaceStyle.FLAT, t.background)
                surfaces.draw(canvas, rect, r, t.style, t.popup, seed = seed, plate = true)
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
        if (theme.style == SurfaceStyle.TYPEWRITER) {
            // The typewriter shows a bigger round key with the same rim, rising above the finger.
            val d = minOf(bubble.width(), bubble.height())
            val body = RectF(bubble.centerX() - d / 2, bubble.bottom - d, bubble.centerX() + d / 2, bubble.bottom)
            // The same key, only bigger: the same rust and scratches in the same places.
            surfaces.typewriterWorn = isWorn(b.key)
            surfaces.draw(canvas, body, radius, theme.style, theme.key, seed = keyRects.indexOfFirst { it.first === b.key })
            surfaces.typewriterWorn = false
            val rim = surfaces.typewriterRim(d)
            body.inset(rim, rim)
            drawLabel(canvas, body, b.label, theme.text, b.key)
            canvas.restoreToCount(layer)
            return
        }
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
        val typewriter = theme.style == SurfaceStyle.TYPEWRITER
        // On the typewriter [rect] is the black cap: the letter is sized by its smaller side and stays inside it
        // (a round cap is narrower above and below the middle, so words get less of its width).
        val base = if (typewriter) minOf(rect.width(), rect.height()) * 1.25f else rect.height()
        var size = base * (if (isLetter) 0.46f else 0.30f) * theme.textScale
        textPaint.textSize = size
        val round = typewriter && rect.width() <= rect.height() * 1.05f
        val maxWidth = rect.width() * (if (round) 0.78f else 0.86f)
        val measured = textPaint.measureText(label)
        if (measured > maxWidth) {
            size *= maxWidth / measured
            textPaint.textSize = size
        }
        val y = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        if (typewriter) {
            // Struck through an old ribbon: every letter a little tilted, off its line and lighter or darker,
            // always the same way for the same letter.
            val h = label.hashCode() * 31 + key.width.toBits()
            // Only a single letter tilts: on a word a tilt reads as a broken line.
            val tilt = if (label.length <= 2) ((h and 0xFF) / 255f - 0.5f) * 4f else 0f
            val drop = (((h ushr 8) and 0xFF) / 255f - 0.5f) * size * 0.08f
            textPaint.alpha = (textPaint.alpha * (0.82f + ((h ushr 16) and 0xFF) / 255f * 0.18f)).toInt()
            canvas.save()
            canvas.rotate(tilt, rect.centerX(), rect.centerY())
            canvas.drawText(label, rect.centerX(), y + drop, textPaint)
            canvas.restore()
            return
        }
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
        if (t.style == SurfaceStyle.TYPEWRITER) {
            // Round keys on a steel bar; the one under the finger is down, with a brass rim.
            surfaces.drawTypewriterBar(canvas, p.bounds)
            p.cells.forEachIndexed { i, cell ->
                val chosen = i == p.selected
                val d = minOf(cell.width(), cell.height()) * 0.86f
                val body = RectF(cell.centerX() - d / 2, cell.centerY() - d / 2, cell.centerX() + d / 2, cell.centerY() + d / 2)
                surfaces.typewriterDepth = if (chosen) 1f else 0f
                surfaces.draw(canvas, body, radius, t.style, t.key, if (chosen) SurfaceState.SELECTED else SurfaceState.NORMAL, seed = i)
                surfaces.typewriterDepth = -2f
                val rim = surfaces.typewriterRim(d)
                body.inset(rim, rim)
                if (chosen) body.offset(0f, surfaces.typewriterSink)
                val color = if (chosen) t.text else (t.text and 0x00FFFFFF) or 0xA6000000.toInt()
                drawLabel(canvas, body, p.options[i], color, p.key)
            }
            canvas.restoreToCount(layer)
            return
        }
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
        /** Space held still for a moment: moving the finger now moves the cursor (iOS-style). */
        var cursorMode = false
        var cursorX = 0f
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

    /** Where the user's finger lands on each letter (null: the keys' own borders only). */
    var touchModel: TouchModel? = null

    private fun keyAt(x: Float, y: Float): Key? {
        val hit = keyRects.firstOrNull { (_, r) -> r.contains(x, y) }?.first?.takeIf { k -> k.style != KeyStyle.SPACER }
            // Touches in the gaps or just outside go to the nearest key.
            ?: keyRects.filter { it.first.style != KeyStyle.SPACER }.minByOrNull { (_, r) -> distanceSq(r, x, y) }?.first
        return learnedKey(hit, x, y)
    }

    /** The letter a touch is meant for: near a border, the key whose learned spot is nearer to it. */
    private fun learnedKey(hit: Key?, x: Float, y: Float): Key? {
        val model = touchModel ?: return hit
        val hitLetter = hit?.let(::letterOf) ?: return hit
        val hitRect = keyRects.firstOrNull { it.first === hit }?.second ?: return hit
        var best = hit
        var bestDistance = spotDistance(model, hitLetter, hitRect, x, y)
        for ((key, rect) in keyRects) {
            if (key === hit) continue
            val letter = letterOf(key) ?: continue
            // Only the keys right around the touch take part.
            if (x < rect.left - rect.width() * NEAR || x > rect.right + rect.width() * NEAR ||
                y < rect.top - rect.height() * NEAR || y > rect.bottom + rect.height() * NEAR
            ) continue
            val d = spotDistance(model, letter, rect, x, y)
            if (d < bestDistance) {
                best = key
                bestDistance = d
            }
        }
        return best
    }

    private fun spotDistance(model: TouchModel, letter: String, rect: RectF, x: Float, y: Float): Float {
        val (ox, oy) = model.offset(letter)
        val dx = (x - (rect.centerX() + ox * rect.width())) / rect.width()
        val dy = (y - (rect.centerY() + oy * rect.height())) / rect.height()
        return dx * dx + dy * dy
    }

    /** The letter a key types, lower case, if it is a single letter (the keys the touch model learns). */
    private fun letterOf(key: Key): String? {
        val text = (key.action as? KeyAction.Text)?.text ?: return null
        return text.lowercase().takeIf { it.length == 1 && it[0].isLetter() }
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
        sound(key)

        if (key.repeatable) {
            state.repeat = object : Runnable {
                override fun run() {
                    state.repeating = true
                    // A light tick for every character a held key deletes (or moves), not only the first;
                    // none once there is nothing left to delete.
                    if (listener?.repeatHasEffect(key) != false) {
                        feedback(light = true)
                        sound(key)
                    }
                    listener?.onKey(key)
                    handler.postDelayed(this, REPEAT_INTERVAL_MS)
                }
            }.also { handler.postDelayed(it, REPEAT_START_MS) }
        } else if (isSpace(key) && spaceCursorEnabled) {
            // Held still for a moment, the space bar turns into a trackpad for the cursor; a quick swipe
            // before that still switches the language.
            state.longPress = Runnable {
                state.longPressFired = true
                state.cursorMode = true
                state.cursorX = state.downX
                feedback(light = true)
                invalidate()
            }.also { handler.postDelayed(it, SPACE_CURSOR_HOLD_MS) }
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
        if (state.cursorMode) {
            val step = dp(SPACE_CURSOR_STEP_DP)
            val steps = ((x - state.cursorX) / step).toInt()
            if (steps != 0) {
                state.cursorX += steps * step
                listener?.onCursorMove(steps)
                feedback(light = true)
            }
            return
        }
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
                // The typewriter key springs back up: a light tick as it comes up.
                if (theme.style == SurfaceStyle.TYPEWRITER) feedback(light = true)
                // A plain tap on a letter: where the finger landed on it.
                val model = touchModel
                val letter = letterOf(key)
                val rect = keyRects.firstOrNull { it.first === key }?.second
                if (model != null && letter != null && rect != null) {
                    model.learn(letter, (state.downX - rect.centerX()) / rect.width(), (state.downY - rect.centerY()) / rect.height())
                }
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
        const val SPACE_CURSOR_HOLD_MS = 320L
        const val SPACE_CURSOR_STEP_DP = 9f
        const val BACKSPACE_SWIPE_START_DP = 28f
        const val BACKSPACE_SWIPE_STEP_DP = 30f

        // Soft timings: quick enough to follow fast typing, long enough that nothing pops.
        const val BUBBLE_IN_MS = 70L
        const val BUBBLE_OUT_MS = 150L
        const val POPUP_FADE_MS = 130L
        const val RELEASE_FADE_MS = 170L
        /** How long a layout takes to slide out and the next one in (typewriter). */
        const val SLIDE_MS = 180L
        /** How far around a touch (a share of a key) neighbouring keys compete for it. */
        const val NEAR = 0.35f
        /** How long a released typewriter key takes to spring back up (within [RELEASE_FADE_MS]). */
        const val TYPEWRITER_SPRING_MS = 260L
        const val SPRING_SWINGS = 2.5f
        const val SPRING_OVERSHOOT = 0.35f
        /** How long a released key is remembered: its glow fading out, or its spring. */
        const val RELEASE_KEEP_MS = 280L
        /** How far the typewriter levers lean in towards the middle as they go up. */
        const val LEVER_CONVERGE = 0.06f
        /** The share of the gap between keys the typewriter keeps (the round keys use the rest). */
        const val KEY_GAP_SHARE = 0.4f
        /** How much bigger than a letter the typewriter's big keys (Shift, Enter…) may be. */
        const val BIG_KEY_SCALE = 1.18f
        /** The most used letters of Ukrainian, Polish and English: the most worn keys of the typewriter. */
        const val WORN_LETTERS = "оаеинтрсвaeoinztrs"
        const val WORN_INK = 0.82f
    }
}
