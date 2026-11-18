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
 * pins or unpins it. The bar below goes back to the letters, translates (the selection, or the clipboard
 * into the field), and forgets the unpinned texts.
 */
@SuppressLint("ViewConstructor")
class ClipboardPanel(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onClipPicked(text: String)
        fun onClipLongPressed(text: String)
        fun onClipboardClosed()
        fun onClipboardTranslate()
        fun onClipboardClear()
    }

    var listener: Listener? = null
    private var theme: KeyboardTheme? = null

    private val list = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }
    private val title = TextView(context).apply {
        text = context.getString(R.string.clipboard_title)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val lettersButton = ToolbarButton(context).apply {
        text = context.getString(R.string.stickers_letters)
        contentDescription = text
        setOnClickListener { listener?.onClipboardClosed() }
    }
    private val translateButton = ToolbarButton(context).apply {
        icon = Icon.TRANSLATE
        contentDescription = context.getString(R.string.clipboard_translate)
        setOnClickListener { listener?.onClipboardTranslate() }
    }
    private val clearButton = ToolbarButton(context).apply {
        icon = Icon.CLOSE
        contentDescription = context.getString(R.string.clipboard_clear)
        setOnClickListener { listener?.onClipboardClear() }
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
        setBackgroundColor(theme.background)
        title.setTextColor(theme.hint)
        title.typeface = theme.typeface(context)
        for (b in listOf(lettersButton, translateButton, clearButton)) {
            b.applyStyle(theme)
            b.fill = theme.function
            b.pressedFill = theme.pressed
            b.foreground = theme.text
        }
    }

    /** Shows [clips] (pinned ones marked with a pin). */
    fun show(clips: List<ClipboardHistory.Clip>) {
        list.removeAllViews()
        val t = theme
        if (clips.isEmpty()) {
            list.addView(TextView(context).apply {
                text = context.getString(R.string.clipboard_history_empty)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(24), dp(16), dp(24))
                t?.let { setTextColor(it.hint); typeface = it.typeface(context) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            return
        }
        for (clip in clips) {
            val row = ToolbarButton(context).apply {
                text = (if (clip.pinned) "📌 " else "") + clip.text.replace(Regex("\\s+"), " ").take(120)
                contentDescription = clip.text.take(120)
                textSizeSp = 15f
                t?.let {
                    applyStyle(it)
                    fill = it.function
                    pressedFill = it.pressed
                    foreground = it.text
                }
                setOnClickListener { listener?.onClipPicked(clip.text) }
                setOnLongClickListener {
                    listener?.onClipLongPressed(clip.text)
                    true
                }
            }
            list.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
