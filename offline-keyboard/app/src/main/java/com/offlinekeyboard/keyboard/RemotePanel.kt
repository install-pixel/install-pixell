package com.offlinekeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The remote for the computer over Bluetooth: a touchpad, the two mouse buttons and a row of keys (Esc, Tab,
 * Win, Alt+Tab, arrows, Home, End), which a button turns into media keys and slide keys. In portrait it takes
 * the keys' place; in landscape it stands beside the keyboard (then without the «keyboard» button).
 */
@SuppressLint("ViewConstructor")
class RemotePanel(context: Context) : LinearLayout(context) {

    interface Listener {
        /** Mouse: [buttons] held (1 left, 2 right), a move and a wheel step. */
        fun onRemoteMouse(buttons: Int, dx: Int, dy: Int, wheel: Int)
        /** A key on the computer: HID [usage] with [modifiers] (0 for a modifier alone). */
        fun onRemoteKey(usage: Int, modifiers: Int)
        /** A media key (HID consumer usage). */
        fun onRemoteMedia(usage: Int)
        /** Back to the keys (portrait). */
        fun onRemoteKeyboard()
    }

    var listener: Listener? = null

    /** Called on every key and button press here: the keyboard's own click and sound. */
    var tapFeedback: (() -> Unit)? = null

    /** Landscape: the panel stands beside the keyboard, so it needs no «keyboard» button. */
    var besideKeyboard = false
        set(value) { field = value; keyboardButton.visibility = if (value) View.GONE else View.VISIBLE }

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).roundToInt()

    private var media = false
    private val keyRow = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val pad = Touchpad(context)
    private lateinit var keyboardButton: ToolbarButton
    private val buttons = ArrayList<ToolbarButton>()
    private var theme: KeyboardTheme? = null

    init {
        orientation = VERTICAL
        setPadding(dp(4), dp(2), dp(4), dp(4))
        addView(keyRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
        addView(pad, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(dp(2), dp(4), dp(2), dp(4)) })
        val bottom = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bottom.addView(mouseButton(context.getString(com.offlinekeyboard.R.string.remote_left), 1), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        bottom.addView(mouseButton(context.getString(com.offlinekeyboard.R.string.remote_right), 2), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        keyboardButton = key(RowKey("", Icon.KEYBOARD) { listener?.onRemoteKeyboard() })
        bottom.addView(keyboardButton, LayoutParams(dp(56), LayoutParams.MATCH_PARENT).apply { setMargins(dp(2), 0, dp(2), 0) })
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))
        pad.listener = { b, x, y, w -> listener?.onRemoteMouse(b, x, y, w) }
        pad.keyListener = { usage -> tapFeedback?.invoke(); listener?.onRemoteKey(usage, 0) }
        fillKeyRow()
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        for (b in buttons) style(b)
        pad.applyTheme(value)
    }

    private fun style(b: ToolbarButton) {
        val t = theme ?: return
        b.applyStyle(t)
        b.fill = t.function
        b.pressedFill = t.pressed
        b.foreground = t.text
    }

    /**
     * One key of the row: [weight] of its width, [action] on a tap, and on a long hold [held] again and again
     * every [every] ms (or [action] when [held] is null and [every] is set: arrows and volume repeat).
     */
    private class RowKey(
        val label: String,
        val icon: Icon? = null,
        val weight: Float = 1f,
        val every: Long = 0L,
        val held: (() -> Unit)? = null,
        val action: () -> Unit,
    )

    /** Shows the music row (tests). */
    internal fun showMediaForTest() {
        media = true
        fillKeyRow()
    }

    /** Shows the navigation row (tests). */
    internal fun showNavForTest() {
        media = false
        fillKeyRow()
    }

    private fun fillKeyRow() {
        keyRow.removeAllViews()
        val keys = if (!media) {
            // Navigation, built round the arrows in the middle: what leaves on the left (Esc, Tab), what
            // switches on the right (Alt+Tab, Win); the music row is one tap away at the far right.
            listOf(
                RowKey("Esc") { listener?.onRemoteKey(HID_ESC, 0) },
                RowKey("Tab") { listener?.onRemoteKey(HID_TAB, 0) },
                RowKey("", Icon.LEFT, 1.1f, REPEAT_ARROW_MS) { listener?.onRemoteKey(HID_LEFT, 0) },
                null, // the up and down pair, stacked in the middle
                RowKey("", Icon.RIGHT, 1.1f, REPEAT_ARROW_MS) { listener?.onRemoteKey(HID_RIGHT, 0) },
                RowKey("", Icon.ENTER) { listener?.onRemoteKey(HID_ENTER, 0) },
                RowKey("", Icon.WINDOWS) { listener?.onRemoteKey(HID_TAB, MOD_ALT) },
                RowKey("Win") { listener?.onRemoteKey(0, MOD_GUI) },
                RowKey("", Icon.MUSIC, 0.9f) { media = true; fillKeyRow() },
            )
        } else {
            // Music, mirrored round a bigger play/pause: seek, then track, then volume, then mute and the way
            // back to the keys at the ends. A tap seeks a step, holding keeps seeking; holding a track key keeps
            // skipping.
            listOf(
                RowKey("", Icon.SPEAKER_OFF, 0.95f) { listener?.onRemoteMedia(MEDIA_MUTE) },
                RowKey("", Icon.VOLUME_DOWN, every = REPEAT_VOLUME_MS) { listener?.onRemoteMedia(MEDIA_VOLUME_DOWN) },
                RowKey("", Icon.PREVIOUS, every = REPEAT_TRACK_MS) { listener?.onRemoteMedia(MEDIA_PREVIOUS) },
                RowKey("", Icon.SEEK_BACK, every = REPEAT_SEEK_MS) { listener?.onRemoteKey(HID_LEFT, 0) },
                RowKey("", Icon.PLAY_PAUSE, 1.6f) { listener?.onRemoteMedia(MEDIA_PLAY_PAUSE) },
                RowKey("", Icon.SEEK_FORWARD, every = REPEAT_SEEK_MS) { listener?.onRemoteKey(HID_RIGHT, 0) },
                RowKey("", Icon.NEXT, every = REPEAT_TRACK_MS) { listener?.onRemoteMedia(MEDIA_NEXT) },
                RowKey("", Icon.VOLUME_UP, every = REPEAT_VOLUME_MS) { listener?.onRemoteMedia(MEDIA_VOLUME_UP) },
                RowKey("", Icon.KEYBOARD, 0.95f) { media = false; fillKeyRow() },
            )
        }
        for (k in keys) {
            if (k == null) {
                val pair = LinearLayout(context).apply { orientation = VERTICAL }
                pair.addView(key(RowKey("", Icon.UP, every = REPEAT_ARROW_MS) { listener?.onRemoteKey(HID_UP, 0) }),
                    LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(0, 0, 0, dp(1)) })
                pair.addView(key(RowKey("", Icon.DOWN, every = REPEAT_ARROW_MS) { listener?.onRemoteKey(HID_DOWN, 0) }),
                    LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(0, dp(1), 0, 0) })
                keyRow.addView(pair, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.3f).apply { setMargins(dp(2), 0, dp(2), 0) })
            } else {
                keyRow.addView(key(k), LayoutParams(0, LayoutParams.MATCH_PARENT, k.weight).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun key(k: RowKey) = ToolbarButton(context).apply {
        // The plate a little inside its place: the rim and its shadow whole, not cut by the row.
        plateInset = 2f * this@RemotePanel.density
        text = k.label
        icon = k.icon
        // Two windows are busier than an arrow: a little smaller, so the drawing breathes inside the key.
        if (k.icon == Icon.WINDOWS) iconScale = 0.82f
        textSizeSp = 13f
        // Short names (Esc, Tab, Win) shrink rather than lose letters on a narrow row.
        minTextSp = 7f
        contentDescription = k.icon?.let { DESCRIPTIONS[it] } ?: k.label.ifEmpty { k.icon?.name?.lowercase() }
        buttons += this
        style(this)
        if (k.every <= 0L) {
            setOnClickListener { tapFeedback?.invoke(); k.action() }
        } else {
            // A tap: once. Held: again and again while the finger stays.
            val repeat = object : Runnable {
                override fun run() {
                    (k.held ?: k.action)()
                    postDelayed(this, k.every)
                }
            }
            var repeated = false
            val start = Runnable { repeated = true; repeat.run() }
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { repeated = false; tapFeedback?.invoke(); postDelayed(start, HOLD_MS) }
                    MotionEvent.ACTION_UP -> {
                        removeCallbacks(start); removeCallbacks(repeat)
                        if (!repeated && e.x in 0f..v.width.toFloat() && e.y in 0f..v.height.toFloat()) k.action()
                    }
                    MotionEvent.ACTION_CANCEL -> { removeCallbacks(start); removeCallbacks(repeat) }
                }
                v.onTouchEvent(e)
                true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun mouseButton(label: String, button: Int) = ToolbarButton(context).apply {
        bodyPlate = true
        text = label
        textSizeSp = 14f
        buttons += this
        style(this)
        // Held as long as the finger stays: drag with the other finger on the touchpad.
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pad.held = button; tapFeedback?.invoke(); listener?.onRemoteMouse(button, 0, 0, 0) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { pad.held = 0; listener?.onRemoteMouse(0, 0, 0, 0) }
            }
            v.onTouchEvent(e)
        }
    }

    companion object {
        const val HID_ESC = 0x29
        const val HID_TAB = 0x2B
        const val HID_ENTER = 0x28
        const val HID_RIGHT = 0x4F
        const val HID_LEFT = 0x50
        const val HID_DOWN = 0x51
        const val HID_UP = 0x52
        const val MOD_ALT = 0x04
        const val MOD_GUI = 0x08
        const val MEDIA_PLAY_PAUSE = 0xCD
        const val MEDIA_NEXT = 0xB5
        const val MEDIA_PREVIOUS = 0xB6
        const val MEDIA_MUTE = 0xE2
        const val MEDIA_VOLUME_UP = 0xE9
        const val MEDIA_VOLUME_DOWN = 0xEA
        /** What the short labels mean, read out by TalkBack. */
        val DESCRIPTIONS = mapOf(
            Icon.ENTER to "Enter", Icon.SPEAKER_OFF to "Mute", Icon.VOLUME_DOWN to "Volume down",
            Icon.VOLUME_UP to "Volume up", Icon.PREVIOUS to "Previous track", Icon.SEEK_BACK to "Seek back",
            Icon.PLAY_PAUSE to "Play / pause", Icon.SEEK_FORWARD to "Seek forward", Icon.NEXT to "Next track",
            Icon.KEYBOARD to "Keys", Icon.MUSIC to "Music keys", Icon.WINDOWS to "Switch window",
            Icon.LEFT to "Left", Icon.RIGHT to "Right", Icon.UP to "Up", Icon.DOWN to "Down",
        )
        /** How long a key is held before it repeats, and how often it repeats then. */
        const val HOLD_MS = 400L
        const val REPEAT_ARROW_MS = 110L
        const val REPEAT_SEEK_MS = 140L
        const val REPEAT_TRACK_MS = 550L
        const val REPEAT_VOLUME_MS = 180L
    }
}

/**
 * The touchpad: one finger moves the pointer, a tap clicks, a tap with two fingers is the right click, two
 * fingers sliding up or down scroll, and a double tap held down drags.
 */
@SuppressLint("ViewConstructor")
class Touchpad(context: Context) : View(context) {

    /** Sends the mouse state: buttons held, a move and a wheel step. */
    var listener: ((Int, Int, Int, Int) -> Unit)? = null
    /** Presses a key on the computer (HID usage): the pad in its arrows mode. */
    var keyListener: ((Int) -> Unit)? = null
    /**
     * The pad's way of working, switched by the button in its corner: a mouse (the pointer follows the finger)
     * or arrows (a swipe presses the arrow that way, as a remote: for media centres moved about with the arrow
     * keys; a tap is Enter, a hold the menu key, a two-finger tap Esc).
     */
    var arrows = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ARROWS_KEY, false)
        private set
    /** A mouse button held on the panel (dragging with the touchpad). */
    var held = 0
    /** How far the pointer moves for a finger's move (1 = as far as the finger). */
    var speed = 1.6f

    private val density = resources.displayMetrics.density
    private val surfaces = SurfacePainter(density)
    private var style = SurfaceStyle.FLAT
    private var fillColor = 0xFF1A1A1A.toInt()
    private var ink = 0xFFEDEDED.toInt()
    private val bounds = RectF()
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var moved = 0f
    private var fingers = 0
    private var maxFingers = 0
    private var lastTapAt = 0L
    private var dragging = false
    private var restX = 0f
    private var restY = 0f
    private var restWheel = 0f

    fun applyTheme(theme: KeyboardTheme) {
        style = theme.style
        surfaces.brightness = theme.brightness
        surfaces.milk = theme.milk
        surfaces.sheetLight = !theme.dark
        surfaces.seeThrough = theme.seeThrough
        fillColor = theme.key
        ink = theme.text
        label.color = (theme.text and 0x00FFFFFF) or 0x40000000
        invalidate()
    }

    private val spot = IntArray(2)
    /** The sketch's pencil outline of the pad, made again only when the pad's size changes. */
    private var outline: android.graphics.Path? = null
    private var outlineW = 0
    private var outlineH = 0
    private val outlinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        getLocationInWindow(spot)
        surfaces.sheetOffsetX = spot[0].toFloat()
        surfaces.sheetOffsetY = spot[1].toFloat()
        if (style == SurfaceStyle.SHEET) {
            // The sketch has no pieces of paper: the keyboard's own paper runs on under the touchpad, which is
            // only outlined in pencil, as the ring round a chosen key.
            val inset = 3f * density
            val w = width - 2 * inset
            val h = height - 2 * inset
            if (w > 0f && h > 0f) {
                if (outline == null || outlineW != width || outlineH != height) {
                    outline = Pencil.tapered(Pencil.loop(w, h, 16f * density, 11, 1.4f * density), 1.7f * density)
                    outlineW = width
                    outlineH = height
                }
                outlinePaint.color = ink
                outlinePaint.alpha = (150 * surfaces.brightness).toInt().coerceIn(60, 255)
                canvas.save()
                canvas.translate(inset, inset)
                canvas.drawPath(outline!!, outlinePaint)
                canvas.restore()
            }
        } else {
            surfaces.draw(canvas, bounds, 14f * density, style, fillColor, inkColor = ink, plate = true)
        }
        label.textSize = 13f * density * resources.configuration.fontScale
        canvas.drawText(if (arrows) "←  ↑  ↓  →" else "· · ·", width / 2f, height / 2f, label)
        drawModeButton(canvas)
    }

    /** The mode button's place, the pad's top right corner. */
    private val modeBox = RectF()
    private var onModeBox = false
    private val modePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Draws the corner button: the mode it is in (a pointer, or four arrows). */
    private fun drawModeButton(canvas: Canvas) {
        val size = MODE_BOX_DP * density
        modeBox.set(width - size, 0f, width.toFloat(), size)
        val cx = modeBox.centerX() - 2f * density
        val cy = modeBox.centerY() + 2f * density
        val r = 9f * density
        modePaint.color = ink
        modePaint.alpha = (200 * surfaces.brightness).toInt().coerceIn(110, 230)
        modePaint.strokeWidth = 1.6f * density
        if (arrows) {
            // Four arrows, a cross.
            canvas.drawLine(cx - r, cy, cx + r, cy, modePaint)
            canvas.drawLine(cx, cy - r, cx, cy + r, modePaint)
            val h = 3f * density
            for ((x, y, dx, dy) in listOf(floatArrayOf(cx + r, cy, -1f, 0f), floatArrayOf(cx - r, cy, 1f, 0f), floatArrayOf(cx, cy + r, 0f, -1f), floatArrayOf(cx, cy - r, 0f, 1f))) {
                canvas.drawLine(x, y, x + dx * h + dy * h, y + dy * h + dx * h, modePaint)
                canvas.drawLine(x, y, x + dx * h - dy * h, y + dy * h - dx * h, modePaint)
            }
        } else {
            // A mouse pointer.
            val path = android.graphics.Path().apply {
                moveTo(cx - r * 0.55f, cy - r)
                lineTo(cx - r * 0.55f, cy + r * 0.55f)
                lineTo(cx - r * 0.15f, cy + r * 0.15f)
                lineTo(cx + r * 0.2f, cy + r * 0.95f)
                lineTo(cx + r * 0.45f, cy + r * 0.82f)
                lineTo(cx + r * 0.1f, cy + r * 0.05f)
                lineTo(cx + r * 0.65f, cy + r * 0.05f)
                close()
            }
            canvas.drawPath(path, modePaint)
        }
    }

    private fun toggleMode() {
        arrows = !arrows
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ARROWS_KEY, arrows).apply()
        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        invalidate()
    }

    // The mouse's moves, gathered and sent at a steady pace (one report per touch event queued up in Bluetooth
    // and went in bursts: the pointer jerked and lagged).
    private var pendingX = 0
    private var pendingY = 0
    private var flushScheduled = false
    private val flush = Runnable {
        flushScheduled = false
        if (pendingX != 0 || pendingY != 0) {
            val x = pendingX.coerceIn(-127, 127)
            val y = pendingY.coerceIn(-127, 127)
            pendingX -= x
            pendingY -= y
            send(buttons(), x, y, 0)
            if (pendingX != 0 || pendingY != 0) scheduleFlush()
        }
    }

    private fun scheduleFlush() {
        if (flushScheduled) return
        flushScheduled = true
        postDelayed(flush, FLUSH_MS)
    }

    private var lastMoveAt = 0L

    // The arrows mode.
    private var arrowRestX = 0f
    private var arrowRestY = 0f
    private var held2 = false
    private val holdMenu = Runnable {
        held2 = true
        keyListener?.invoke(HID_MENU)
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        // The mode button: a tap on it switches the mode, nothing is sent.
        if (e.actionMasked == MotionEvent.ACTION_DOWN && modeBox.contains(e.x, e.y)) {
            onModeBox = true
            return true
        }
        if (onModeBox) {
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                onModeBox = false
                if (modeBox.contains(e.x, e.y)) toggleMode()
            } else if (e.actionMasked == MotionEvent.ACTION_CANCEL) {
                onModeBox = false
            }
            return true
        }
        return if (arrows) arrowsTouch(e) else mouseTouch(e)
    }

    /** The arrows mode: a swipe presses the arrow that way, one press every so far; a tap is Enter. */
    private fun arrowsTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fingers = 1
                maxFingers = 1
                downAt = SystemClock.uptimeMillis()
                lastX = e.x
                lastY = e.y
                moved = 0f
                arrowRestX = 0f
                arrowRestY = 0f
                held2 = false
                postDelayed(holdMenu, HOLD_MENU_MS)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                fingers = e.pointerCount
                maxFingers = maxOf(maxFingers, fingers)
                removeCallbacks(holdMenu)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX
                val dy = e.y - lastY
                lastX = e.x
                lastY = e.y
                moved += hypot(dx, dy)
                if (moved > TAP_SLOP_DP * density) removeCallbacks(holdMenu)
                if (held2 || e.pointerCount > 1) return true
                arrowRestX += dx
                arrowRestY += dy
                val step = ARROW_STEP_DP * density
                // The way the finger goes most: one arrow press for every step along it.
                if (kotlin.math.abs(arrowRestX) >= kotlin.math.abs(arrowRestY)) {
                    while (arrowRestX >= step) { keyListener?.invoke(HID_RIGHT); arrowRestX -= step; arrowRestY = 0f }
                    while (arrowRestX <= -step) { keyListener?.invoke(HID_LEFT); arrowRestX += step; arrowRestY = 0f }
                } else {
                    while (arrowRestY >= step) { keyListener?.invoke(HID_DOWN); arrowRestY -= step; arrowRestX = 0f }
                    while (arrowRestY <= -step) { keyListener?.invoke(HID_UP); arrowRestY += step; arrowRestX = 0f }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> fingers = e.pointerCount - 1
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(holdMenu)
                val now = SystemClock.uptimeMillis()
                val tap = e.actionMasked == MotionEvent.ACTION_UP && !held2 && now - downAt < TAP_MS * 2 && moved < TAP_SLOP_DP * density
                if (tap) keyListener?.invoke(if (maxFingers >= 2) HID_ESC else HID_ENTER)
                fingers = 0
            }
        }
        return true
    }

    private fun mouseTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fingers = 1
                maxFingers = 1
                downAt = SystemClock.uptimeMillis()
                downX = e.x
                downY = e.y
                lastX = e.x
                lastY = e.y
                lastMoveAt = e.eventTime
                moved = 0f
                // A second tap right after a tap, held down: drag.
                if (downAt - lastTapAt < DOUBLE_TAP_MS) {
                    dragging = true
                    send(1, 0, 0, 0)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                fingers = e.pointerCount
                maxFingers = maxOf(maxFingers, fingers)
                lastX = avgX(e)
                lastY = avgY(e)
            }
            MotionEvent.ACTION_MOVE -> {
                val x = avgX(e)
                val y = avgY(e)
                val dx = x - lastX
                val dy = y - lastY
                lastX = x
                lastY = y
                val dt = (e.eventTime - lastMoveAt).coerceAtLeast(1L)
                lastMoveAt = e.eventTime
                val distance = hypot(dx, dy)
                moved += distance
                if (e.pointerCount >= 2) {
                    // Two fingers: scroll, a notch every few millimetres.
                    restWheel += -dy / (WHEEL_STEP_DP * density)
                    val steps = restWheel.toInt()
                    if (steps != 0) {
                        restWheel -= steps
                        send(buttons(), 0, 0, steps.coerceIn(-127, 127))
                    }
                } else {
                    // As a laptop's touchpad: slow moves are fine (the pointer goes less far than the finger), quick
                    // ones go far.
                    val dpPerMs = distance / density / dt
                    val gain = speed * (SLOW_GAIN + (FAST_GAIN - SLOW_GAIN) * ((dpPerMs - 0.05f) / 1.2f).coerceIn(0f, 1f))
                    restX += dx * gain
                    restY += dy * gain
                    val mx = restX.toInt()
                    val my = restY.toInt()
                    if (mx != 0 || my != 0) {
                        restX -= mx
                        restY -= my
                        pendingX += mx
                        pendingY += my
                        scheduleFlush()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                fingers = e.pointerCount - 1
                // Continue from the fingers that stay.
                val stay = (0 until e.pointerCount).filter { it != e.actionIndex }
                if (stay.isNotEmpty()) {
                    lastX = stay.map { e.getX(it) }.average().toFloat()
                    lastY = stay.map { e.getY(it) }.average().toFloat()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // What is left of the move goes first.
                removeCallbacks(flush)
                flushScheduled = false
                while (pendingX != 0 || pendingY != 0) flush.run()
                val now = SystemClock.uptimeMillis()
                val tap = e.actionMasked == MotionEvent.ACTION_UP && now - downAt < TAP_MS && moved < TAP_SLOP_DP * density
                when {
                    dragging -> { dragging = false; send(held, 0, 0, 0); lastTapAt = 0L }
                    tap && maxFingers >= 2 -> { click(2); lastTapAt = 0L }
                    tap -> { click(1); lastTapAt = now }
                    else -> lastTapAt = 0L
                }
                fingers = 0
                restX = 0f
                restY = 0f
                restWheel = 0f
            }
        }
        return true
    }

    private fun buttons() = (if (dragging) 1 else 0) or held

    private fun click(button: Int) {
        send(button or held, 0, 0, 0)
        send(held, 0, 0, 0)
        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun send(buttons: Int, dx: Int, dy: Int, wheel: Int) = listener?.invoke(buttons, dx, dy, wheel)

    private fun avgX(e: MotionEvent) = (0 until e.pointerCount).map { e.getX(it) }.average().toFloat()
    private fun avgY(e: MotionEvent) = (0 until e.pointerCount).map { e.getY(it) }.average().toFloat()

    private companion object {
        const val TAP_MS = 220L
        const val TAP_SLOP_DP = 8f
        const val DOUBLE_TAP_MS = 300L
        const val WHEEL_STEP_DP = 14f
        /** How often the gathered mouse move is sent (ms). */
        const val FLUSH_MS = 10L
        /** The pointer against the finger, slowest and quickest (times [speed]). */
        const val SLOW_GAIN = 0.6f
        const val FAST_GAIN = 1.9f
        const val MODE_BOX_DP = 44f
        /** The arrows mode: a press for every so far the finger goes, and how long a hold is for the menu key. */
        const val ARROW_STEP_DP = 36f
        const val HOLD_MENU_MS = 550L
        const val HID_RIGHT = 0x4F
        const val HID_LEFT = 0x50
        const val HID_DOWN = 0x51
        const val HID_UP = 0x52
        const val HID_ENTER = 0x28
        const val HID_ESC = 0x29
        /** The keyboard's menu key (Application): a media centre's context menu. */
        const val HID_MENU = 0x65
        const val PREFS = "touchpad"
        const val ARROWS_KEY = "arrows"
    }
}
