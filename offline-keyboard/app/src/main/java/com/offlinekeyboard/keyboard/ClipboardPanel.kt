package com.offlinekeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.offlinekeyboard.R

/**
 * Shown in place of the keys: the last copied texts (pinned first). A tap pastes a text, a long press
 * pins or unpins it (it moves up or down the list softly), a swipe to the left forgets it (a tap on the
 * title brings it back for a few seconds). The bar below goes back to the letters, translates (the
 * selection, or the clipboard into the field), and forgets the unpinned texts after a second tap.
 */
@SuppressLint("ViewConstructor")
class ClipboardPanel(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onClipPicked(text: String)
        fun onClipLongPressed(text: String)
        fun onClipboardClosed()
        fun onClipboardTranslate()
        fun onClipboardClear()
        /** [text] was swiped away. */
        fun onClipDeleted(text: String) {}
        /** The last text swiped away is wanted back. */
        fun onClipRestore() {}
    }

    var listener: Listener? = null

    /** The keyboard's click and key sound, for presses here. */
    var tapFeedback: (() -> Unit)? = null
    private var theme: KeyboardTheme? = null

    private val list = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
        // A pinned text moves to its new place, the others make room, instead of the list jumping.
        // Rows moving (a pin) are never cut at their edges.
        clipChildren = false
        clipToPadding = false
        layoutTransition = android.animation.LayoutTransition().apply {
            enableTransitionType(android.animation.LayoutTransition.CHANGING)
            setDuration(220)
        }
    }
    /** The rows by their text, kept between updates so a row can move instead of being made again. */
    private val rows = LinkedHashMap<String, View>()
    private var clipsShown: List<ClipboardHistory.Clip> = emptyList()
    private val title = TextView(context).apply {
        text = context.getString(R.string.clipboard_title)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        gravity = Gravity.CENTER
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val lettersButton = ToolbarButton(context).apply {
        text = context.getString(R.string.stickers_letters)
        contentDescription = text
        setOnClickListener { tapFeedback?.invoke(); listener?.onClipboardClosed() }
    }
    private val translateButton = ToolbarButton(context).apply {
        icon = Icon.TRANSLATE
        contentDescription = context.getString(R.string.clipboard_translate)
        setOnClickListener { tapFeedback?.invoke(); listener?.onClipboardTranslate() }
    }
    private val clearButton = ToolbarButton(context).apply {
        icon = Icon.CLOSE
        contentDescription = context.getString(R.string.clipboard_clear)
        setOnClickListener { tapFeedback?.invoke(); clearTapped() }
    }
    private val resetTitle = Runnable {
        clearArmed = false
        showTitle(context.getString(R.string.clipboard_title), null)
    }
    private var clearArmed = false

    /** The first tap on ✕ says what it will do; the second, within a few seconds, does it. */
    private fun clearTapped() {
        val unpinned = clipsShown.count { !it.pinned }
        val pinned = clipsShown.size - unpinned
        when {
            unpinned == 0 && pinned > 0 -> say(context.getString(R.string.clipboard_only_pinned))
            unpinned == 0 -> Unit
            clearArmed -> {
                clearArmed = false
                removeCallbacks(resetTitle)
                resetTitle.run()
                listener?.onClipboardClear()
            }
            else -> {
                clearArmed = true
                say(context.getString(R.string.clipboard_clear_confirm, unpinned, pinned))
            }
        }
    }

    /** A note in the title for a few seconds; [onTap] makes the title tappable meanwhile. */
    fun say(text: String, onTap: (() -> Unit)? = null) {
        showTitle(text, onTap)
        removeCallbacks(resetTitle)
        postDelayed(resetTitle, NOTE_MS)
    }

    private fun showTitle(text: String, onTap: (() -> Unit)?) {
        title.text = text
        title.paintFlags = if (onTap != null) title.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        else title.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
        if (onTap != null) title.setOnClickListener { onTap(); removeCallbacks(resetTitle); resetTitle.run() }
        else { title.setOnClickListener(null); title.isClickable = false }
    }

    init {
        orientation = VERTICAL
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(18))
            addView(list)
        }
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            addView(lettersButton, LayoutParams(dp(72), LayoutParams.MATCH_PARENT).apply { setMargins(dp(3), 0, dp(3), 0) })
            addView(translateButton, LayoutParams(dp(56), LayoutParams.MATCH_PARENT).apply { setMargins(dp(3), 0, dp(3), 0) })
            addView(title, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(clearButton, LayoutParams(dp(52), LayoutParams.MATCH_PARENT).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
    }

    fun applyTheme(theme: KeyboardTheme) {
        this.theme = theme
        // The sketch lies on the paper the root draws under everything, as the keys; glass over the app likewise.
        setBackgroundColor(if (theme.seeThrough || theme.style == SurfaceStyle.SHEET) 0 else theme.background)
        title.setTextColor(theme.hint)
        title.typeface = theme.typeface(context)
        for (b in listOf(lettersButton, translateButton, clearButton)) {
            b.applyStyle(theme)
            b.fill = theme.function
            b.pressedFill = theme.pressed
            b.foreground = theme.text
        }
        // The rows in the new style.
        rows.clear()
        list.removeAllViews()
        if (clipsShown.isNotEmpty()) show(clipsShown)
    }

    /** Shows [clips] (pinned ones marked with a pin); rows that stay move to their places. */
    fun show(clips: List<ClipboardHistory.Clip>) {
        clipsShown = clips
        val t = theme
        if (clips.isEmpty()) {
            rows.clear()
            list.removeAllViews()
            list.addView(TextView(context).apply {
                text = context.getString(R.string.clipboard_history_empty)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(24), dp(16), dp(24))
                t?.let { setTextColor(it.hint); typeface = it.typeface(context) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            return
        }
        // Gone texts (and the "empty" note) out; the rest in order, made only when new.
        val wanted = clips.map { it.text }.toSet()
        rows.keys.filter { it !in wanted }.forEach { list.removeView(rows.remove(it)) }
        for (i in list.childCount - 1 downTo 0) if (list.getChildAt(i).tag == null) list.removeViewAt(i)
        clips.forEachIndexed { index, clip ->
            val row = rows[clip.text]?.also { updateRow(it, clip) } ?: makeRow(clip).also { rows[clip.text] = it }
            if (list.indexOfChild(row) != index) {
                list.removeView(row)
                list.addView(row, index, LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_DP)).apply { setMargins(0, dp(3), 0, dp(3)) })
            }
        }
    }

    private fun updateRow(row: View, clip: ClipboardHistory.Clip) {
        ((row as android.widget.FrameLayout).getChildAt(1) as TextView).text = label(clip)
    }

    private fun label(clip: ClipboardHistory.Clip) =
        (if (clip.pinned) "📌 " else "") + clip.text.replace(Regex("\\s+"), " ").take(240)

    /**
     * A row: the style's plate with the text on it in up to two lines. A swipe to the left takes it off
     * the list; otherwise a tap pastes and a long press pins, as the plate's own clicks.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun makeRow(clip: ClipboardHistory.Clip): View {
        val t = theme
        val plate = ToolbarButton(context).apply {
            contentDescription = clip.text.take(120)
            t?.let {
                applyStyle(it)
                fill = it.function
                pressedFill = it.pressed
                foreground = it.text
            }
            // The plate a little inside its row: its frame stays whole while rows move.
            plateInset = 2f * resources.displayMetrics.density
            setOnClickListener { listener?.onClipPicked(clip.text) }
            setOnLongClickListener {
                tapFeedback?.invoke()
                listener?.onClipLongPressed(clip.text)
                true
            }
        }
        val text = TextView(context).apply {
            text = label(clip)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = false
            isFocusable = false
            t?.let { setTextColor(it.text); typeface = it.typeface(context) }
        }
        val row = android.widget.FrameLayout(context).apply {
            tag = clip.text
            clipChildren = false
            clipToPadding = false
            addView(plate, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
            addView(text, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var swiping = false
        plate.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; swiping = false }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    if (!swiping && dx < -slop && kotlin.math.abs(dx) > kotlin.math.abs(e.rawY - downY)) {
                        swiping = true
                        row.parent?.requestDisallowInterceptTouchEvent(true)
                        v.onTouchEvent(android.view.MotionEvent.obtain(e).apply { action = android.view.MotionEvent.ACTION_CANCEL })
                    }
                    if (swiping) {
                        row.translationX = dx.coerceAtMost(0f)
                        row.alpha = 1f - (kotlin.math.abs(row.translationX) / row.width.coerceAtLeast(1)).coerceIn(0f, 0.8f)
                        return@setOnTouchListener true
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> if (swiping) {
                    swiping = false
                    if (e.actionMasked == android.view.MotionEvent.ACTION_UP && -row.translationX > row.width * SWIPE_SHARE) {
                        row.animate().translationX(-row.width.toFloat()).alpha(0f).setDuration(160).withEndAction {
                            row.translationX = 0f
                            row.alpha = 1f
                            listener?.onClipDeleted(clip.text)
                        }.start()
                    } else {
                        row.animate().translationX(0f).alpha(1f).setDuration(160).start()
                    }
                    return@setOnTouchListener true
                }
            }
            v.onTouchEvent(e)
        }
        return row
    }

    private companion object {
        const val ROW_DP = 56
        const val NOTE_MS = 4000L
        /** How far to the left a row must be swiped to go. */
        const val SWIPE_SHARE = 0.35f
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
