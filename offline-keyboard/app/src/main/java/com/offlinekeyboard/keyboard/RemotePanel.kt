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
        keyboardButton = key("", Icon.KEYBOARD) { listener?.onRemoteKeyboard() }
        bottom.addView(keyboardButton, LayoutParams(dp(56), LayoutParams.MATCH_PARENT).apply { setMargins(dp(2), 0, dp(2), 0) })
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))
        pad.listener = { b, x, y, w -> listener?.onRemoteMouse(b, x, y, w) }
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

    private fun fillKeyRow() {
        keyRow.removeAllViews()
        val items: List<Triple<String, Icon?, () -> Unit>> = if (!media) listOf(
            Triple("Esc", null) { listener?.onRemoteKey(HID_ESC, 0) },
            Triple("Tab", null) { listener?.onRemoteKey(HID_TAB, 0) },
            Triple("Win", null) { listener?.onRemoteKey(0, MOD_GUI) },
            Triple("Alt⇥", null) { listener?.onRemoteKey(HID_TAB, MOD_ALT) },
            Triple("", Icon.LEFT) { listener?.onRemoteKey(HID_LEFT, 0) },
            Triple("", Icon.UP) { listener?.onRemoteKey(HID_UP, 0) },
            Triple("", Icon.DOWN) { listener?.onRemoteKey(HID_DOWN, 0) },
            Triple("", Icon.RIGHT) { listener?.onRemoteKey(HID_RIGHT, 0) },
            Triple("♪", null) { media = true; fillKeyRow() },
        ) else listOf(
            Triple("|◁", null) { listener?.onRemoteMedia(MEDIA_PREVIOUS) },
            Triple("▷‖", null) { listener?.onRemoteMedia(MEDIA_PLAY_PAUSE) },
            Triple("▷|", null) { listener?.onRemoteMedia(MEDIA_NEXT) },
            Triple("−", null) { listener?.onRemoteMedia(MEDIA_VOLUME_DOWN) },
            Triple("+", null) { listener?.onRemoteMedia(MEDIA_VOLUME_UP) },
            Triple("Mute", null) { listener?.onRemoteMedia(MEDIA_MUTE) },
            // Slides: the arrow keys a presentation listens to.
            Triple("◀", null) { listener?.onRemoteKey(HID_LEFT, 0) },
            Triple("▶", null) { listener?.onRemoteKey(HID_RIGHT, 0) },
            Triple("⌨", null) { media = false; fillKeyRow() },
        )
        for ((label, icon, action) in items) {
            keyRow.addView(key(label, icon, action), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
    }

    private fun key(label: String, icon: Icon?, action: () -> Unit) = ToolbarButton(context).apply {
        text = label
        this.icon = icon
        textSizeSp = 13f
        setOnClickListener { action() }
        buttons += this
        style(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun mouseButton(label: String, button: Int) = ToolbarButton(context).apply {
        text = label
        textSizeSp = 14f
        buttons += this
        style(this)
        // Held as long as the finger stays: drag with the other finger on the touchpad.
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pad.held = button; listener?.onRemoteMouse(button, 0, 0, 0) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { pad.held = 0; listener?.onRemoteMouse(0, 0, 0, 0) }
            }
            v.onTouchEvent(e)
        }
    }

    companion object {
        const val HID_ESC = 0x29
        const val HID_TAB = 0x2B
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
    /** A mouse button held on the panel (dragging with the touchpad). */
    var held = 0
    /** How far the pointer moves for a finger's move (1 = as far as the finger). */
    var speed = 1.6f

    private val density = resources.displayMetrics.density
    private val surfaces = SurfacePainter(density)
    private var style = SurfaceStyle.FLAT
    private var fillColor = 0xFF1A1A1A.toInt()
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
        fillColor = theme.key
        label.color = (theme.text and 0x00FFFFFF) or 0x40000000
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        surfaces.draw(canvas, bounds, 14f * density, style, fillColor, plate = true)
        label.textSize = 13f * density * resources.configuration.fontScale
        canvas.drawText("· · ·", width / 2f, height / 2f, label)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fingers = 1
                maxFingers = 1
                downAt = SystemClock.uptimeMillis()
                downX = e.x
                downY = e.y
                lastX = e.x
                lastY = e.y
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
                moved += hypot(dx, dy)
                if (e.pointerCount >= 2) {
                    // Two fingers: scroll, a notch every few millimetres.
                    restWheel += -dy / (WHEEL_STEP_DP * density)
                    val steps = restWheel.toInt()
                    if (steps != 0) {
                        restWheel -= steps
                        send(buttons(), 0, 0, steps.coerceIn(-127, 127))
                    }
                } else {
                    restX += dx * speed
                    restY += dy * speed
                    val mx = restX.toInt()
                    val my = restY.toInt()
                    if (mx != 0 || my != 0) {
                        restX -= mx
                        restY -= my
                        send(buttons(), mx.coerceIn(-127, 127), my.coerceIn(-127, 127), 0)
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
    }
}
