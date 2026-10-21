package com.offlinekeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.offlinekeyboard.R

/**
 * Toolbar above the key grid: language, a centre area (status message, live dictation text or word
 * suggestions), translate, voice, mode switch and settings. Built in code to keep the input view light.
 */
@SuppressLint("ViewConstructor")
class KeyboardRootView(context: Context) : LinearLayout(context) {

    interface ToolbarListener {
        fun onToolbarLanguage()
        fun onToolbarTranslate()
        fun onToolbarTranslateLongPress()
        fun onToolbarVoice()
        fun onToolbarVoiceCancel()
        fun onToolbarMode()
        fun onToolbarSettings()
        fun onSuggestionPicked(word: String)
        fun onSuggestionLongPress(word: String)
    }

    data class ToolbarState(
        val languageCode: String,
        val modeGlyph: String,
        val voiceOffered: Boolean,
        val recording: Boolean,
        val translateLabel: String?, // null: translate off
        val translateOffered: Boolean,
    )

    var toolbarListener: ToolbarListener? = null

    val keyboardView = KeyboardView(context)

    private val textColor = context.getColor(R.color.kb_toolbar_text)
    private val activeColor = context.getColor(R.color.kb_key_active)

    private val languageButton = toolbarButton(R.string.toolbar_language_description) { toolbarListener?.onToolbarLanguage() }
    private var statusAction: (() -> Unit)? = null
    private val status = TextView(context).apply {
        setOnClickListener { statusAction?.let { action -> statusAction = null; action() } }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(textColor)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.START
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), 0, dp(6), 0)
    }
    private val suggestionViews = List(SUGGESTIONS) { suggestionView() }
    private val suggestionsRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        suggestionViews.forEach { addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
    }
    private val translateButton = toolbarButton(R.string.toolbar_translate_description) { toolbarListener?.onToolbarTranslate() }.apply {
        setOnLongClickListener { toolbarListener?.onToolbarTranslateLongPress(); true }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(6), 0, dp(6), 0)
    }
    private val voiceButton = toolbarButton(R.string.toolbar_voice_description) { toolbarListener?.onToolbarVoice() }
    private val modeButton = toolbarButton(R.string.toolbar_mode_description) { toolbarListener?.onToolbarMode() }
    private val settingsButton = toolbarButton(R.string.toolbar_settings_description) {
        if (recording) toolbarListener?.onToolbarVoiceCancel() else toolbarListener?.onToolbarSettings()
    }

    private var recording = false
    private var message: CharSequence? = null
    private var liveText: CharSequence? = null
    private var suggestions: List<String> = emptyList()
    private val clearMessage = Runnable { message = null; statusAction = null; refreshCentre() }

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.kb_background))
        val toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(languageButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
            addView(status, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(suggestionsRow, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(translateButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            addView(voiceButton, LayoutParams(dp(42), LayoutParams.MATCH_PARENT))
            addView(modeButton, LayoutParams(dp(42), LayoutParams.MATCH_PARENT))
            addView(settingsButton, LayoutParams(dp(42), LayoutParams.MATCH_PARENT))
        }
        addView(toolbar, LayoutParams(LayoutParams.MATCH_PARENT, dp(42)))
        addView(keyboardView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        refreshCentre()
    }

    fun updateToolbar(state: ToolbarState) {
        recording = state.recording
        languageButton.text = state.languageCode
        modeButton.text = state.modeGlyph
        voiceButton.text = if (state.recording) "⏹" else "🎤"
        voiceButton.setTextColor(if (state.recording) activeColor else textColor)
        voiceButton.alpha = if (state.voiceOffered) 1f else 0.4f
        settingsButton.text = if (state.recording) "✕" else "⚙"
        settingsButton.contentDescription = context.getString(
            if (state.recording) R.string.toolbar_voice_cancel_description else R.string.toolbar_settings_description,
        )
        translateButton.text = state.translateLabel ?: "⇄"
        translateButton.setTextColor(if (state.translateLabel != null) activeColor else textColor)
        translateButton.alpha = if (state.translateOffered) 1f else 0.4f
        refreshCentre()
    }

    /** Short, non-blocking message (e.g. why a feature is unavailable). [onTap] makes it tappable. */
    fun showStatus(text: CharSequence, durationMs: Long = STATUS_MILLIS, onTap: (() -> Unit)? = null) {
        removeCallbacks(clearMessage)
        message = text
        statusAction = onTap
        refreshCentre()
        postDelayed(clearMessage, durationMs)
    }

    /** Text shown while dictating or while a translation segment is being typed; null to clear. */
    fun showLive(text: CharSequence?) {
        liveText = text
        refreshCentre()
    }

    fun showSuggestions(words: List<String>) {
        if (words == suggestions) return
        suggestions = words
        refreshCentre()
    }

    private fun refreshCentre() {
        val centreText = message ?: liveText
        val showSuggestions = centreText == null && suggestions.isNotEmpty()
        status.text = centreText ?: ""
        status.visibility = if (showSuggestions) View.GONE else View.VISIBLE
        suggestionsRow.visibility = if (showSuggestions) View.VISIBLE else View.GONE
        suggestionViews.forEachIndexed { i, v ->
            val word = suggestions.getOrNull(i)
            v.text = word ?: ""
            v.isEnabled = word != null
        }
    }

    private fun suggestionView() = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(textColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(2), 0, dp(2), 0)
        isFocusable = false
        setOnClickListener { (it as TextView).text?.toString()?.takeIf(String::isNotEmpty)?.let { w -> toolbarListener?.onSuggestionPicked(w) } }
        setOnLongClickListener {
            (it as TextView).text?.toString()?.takeIf(String::isNotEmpty)?.let { w -> toolbarListener?.onSuggestionLongPress(w) }
            true
        }
        selectableBackground()
    }

    private fun toolbarButton(descriptionRes: Int, onClick: () -> Unit) = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(textColor)
        contentDescription = context.getString(descriptionRes)
        isClickable = true
        isFocusable = false
        setOnClickListener { onClick() }
        selectableBackground()
    }

    private fun View.selectableBackground() {
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        if (outValue.resourceId != 0) setBackgroundResource(outValue.resourceId)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(clearMessage)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val STATUS_MILLIS = 3500L
        const val SUGGESTIONS = 3
    }
}
