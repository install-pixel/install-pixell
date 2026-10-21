package com.offlinekeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.offlinekeyboard.R

/**
 * Toolbar (language, status messages, voice, mode switch, settings, hide) above the key grid.
 * Built in code to keep the input view light.
 */
@SuppressLint("ViewConstructor")
class KeyboardRootView(context: Context) : LinearLayout(context) {

    interface ToolbarListener {
        fun onToolbarLanguage()
        fun onToolbarVoice()
        fun onToolbarMode()
        fun onToolbarSettings()
        fun onToolbarHide()
    }

    var toolbarListener: ToolbarListener? = null

    val keyboardView = KeyboardView(context)

    private val languageButton = toolbarButton(R.string.toolbar_language_description) { toolbarListener?.onToolbarLanguage() }
    private val status = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(context.getColor(R.color.kb_toolbar_text))
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
    }
    private val voiceButton = toolbarButton(R.string.toolbar_voice_description) { toolbarListener?.onToolbarVoice() }
    private val modeButton = toolbarButton(R.string.toolbar_mode_description) { toolbarListener?.onToolbarMode() }
    private val settingsButton = toolbarButton(R.string.toolbar_settings_description) { toolbarListener?.onToolbarSettings() }
    private val hideButton = toolbarButton(R.string.toolbar_hide_description) { toolbarListener?.onToolbarHide() }

    private val clearStatus = Runnable { status.text = "" }

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.kb_background))
        val toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(languageButton, LayoutParams(dp(52), LayoutParams.MATCH_PARENT))
            addView(status, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(voiceButton, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
            addView(modeButton, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
            addView(settingsButton, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
            addView(hideButton, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
        }
        addView(toolbar, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
        addView(keyboardView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        voiceButton.text = "🎤"
        settingsButton.text = "⚙"
        hideButton.text = "▾"
    }

    fun updateToolbar(languageCode: String, modeGlyph: String, voiceEnabled: Boolean) {
        languageButton.text = languageCode
        modeButton.text = modeGlyph
        voiceButton.alpha = if (voiceEnabled) 1f else 0.4f
    }

    /** Short, non-blocking message (e.g. why a feature is unavailable). */
    fun showStatus(message: CharSequence) {
        removeCallbacks(clearStatus)
        status.text = message
        postDelayed(clearStatus, STATUS_MILLIS)
    }

    private fun toolbarButton(descriptionRes: Int, onClick: () -> Unit) = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(context.getColor(R.color.kb_toolbar_text))
        contentDescription = context.getString(descriptionRes)
        isClickable = true
        isFocusable = false
        setOnClickListener { onClick() }
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        if (outValue.resourceId != 0) setBackgroundResource(outValue.resourceId)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(clearStatus)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val STATUS_MILLIS = 3500L
    }
}

