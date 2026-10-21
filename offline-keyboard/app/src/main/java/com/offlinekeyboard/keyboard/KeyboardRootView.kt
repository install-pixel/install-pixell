package com.offlinekeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.offlinekeyboard.R

/**
 * Two fixed rows above the keys, so nothing shifts while typing:
 *
 * 1. language · message / live text / word suggestions · computer↔mobile · settings (✕ while recording)
 * 2. input mode: [Aa Text] [⇄ Translate UK→PL] [🎤 Voice]
 *
 * Every element has a fixed size; only texts and colours change.
 */
@SuppressLint("ViewConstructor")
class KeyboardRootView(context: Context) : LinearLayout(context) {

    interface ToolbarListener {
        fun onToolbarLanguage()
        fun onModeText()
        /** Turns translation on, or swaps the direction when it is already on. */
        fun onModeTranslate()
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
        val translateActive: Boolean,
        val translateLabel: String,
        val translateOffered: Boolean,
        val voiceOffered: Boolean,
        val recording: Boolean,
    )

    var toolbarListener: ToolbarListener? = null

    val keyboardView = KeyboardView(context)

    private val textColor = context.getColor(R.color.kb_toolbar_text)
    private val activeColor = context.getColor(R.color.kb_key_active)
    private val onActiveColor = context.getColor(R.color.kb_text_on_action)
    private val chipColor = context.getColor(R.color.kb_key_function)

    private val languageButton = iconButton(R.string.toolbar_language_description) { toolbarListener?.onToolbarLanguage() }
    private val status = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(textColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.START
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), 0, dp(6), 0)
        setOnClickListener { statusAction?.let { action -> statusAction = null; action() } }
    }
    private val suggestionViews = List(SUGGESTIONS) { suggestionView() }
    private val suggestionsRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        suggestionViews.forEach { addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
    }
    private val modeButton = iconButton(R.string.toolbar_mode_description) { toolbarListener?.onToolbarMode() }
    private val settingsButton = iconButton(R.string.toolbar_settings_description) {
        if (recording) toolbarListener?.onToolbarVoiceCancel() else toolbarListener?.onToolbarSettings()
    }

    private val textChip = chip(R.string.mode_text_description) { toolbarListener?.onModeText() }
    private val translateChip = chip(R.string.toolbar_translate_description) { toolbarListener?.onModeTranslate() }
    private val voiceChip = chip(R.string.toolbar_voice_description) { toolbarListener?.onToolbarVoice() }

    private var recording = false
    private var message: CharSequence? = null
    private var statusAction: (() -> Unit)? = null
    private var liveText: CharSequence? = null
    private var suggestions: List<String> = emptyList()
    private val clearMessage = Runnable { message = null; statusAction = null; refreshCentre() }

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.kb_background))

        val infoRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(languageButton, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
            addView(status, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(suggestionsRow, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(modeButton, LayoutParams(dp(42), LayoutParams.MATCH_PARENT))
            addView(settingsButton, LayoutParams(dp(42), LayoutParams.MATCH_PARENT))
        }
        val modeRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
            for (c in listOf(textChip, translateChip, voiceChip)) {
                addView(c, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
        }
        addView(infoRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(36)))
        addView(modeRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(38)))
        addView(keyboardView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        refreshCentre()
    }

    fun updateToolbar(state: ToolbarState) {
        recording = state.recording
        languageButton.text = state.languageCode
        modeButton.text = state.modeGlyph
        settingsButton.text = if (state.recording) "✕" else "⚙"
        settingsButton.contentDescription = context.getString(
            if (state.recording) R.string.toolbar_voice_cancel_description else R.string.toolbar_settings_description,
        )

        textChip.text = context.getString(R.string.mode_text)
        styleChip(textChip, active = !state.translateActive, enabled = true)
        translateChip.text = "⇄ ${state.translateLabel}"
        styleChip(translateChip, active = state.translateActive, enabled = state.translateOffered)
        voiceChip.text = context.getString(if (state.recording) R.string.mode_voice_stop else R.string.mode_voice)
        styleChip(voiceChip, active = state.recording, enabled = state.voiceOffered)
        refreshCentre()
    }

    /** Short, non-blocking message. [onTap] makes it tappable (e.g. "restore original"). */
    fun showStatus(text: CharSequence, durationMs: Long = STATUS_MILLIS, onTap: (() -> Unit)? = null) {
        removeCallbacks(clearMessage)
        message = text
        statusAction = onTap
        refreshCentre()
        postDelayed(clearMessage, durationMs)
    }

    /** Text shown while dictating or translating (source text in auto mode); null to clear. */
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

    private fun styleChip(view: TextView, active: Boolean, enabled: Boolean) {
        (view.background as GradientDrawable).setColor(if (active) activeColor else chipColor)
        view.setTextColor(if (active) onActiveColor else textColor)
        view.alpha = if (enabled) 1f else 0.4f
    }

    private fun chip(descriptionRes: Int, onClick: () -> Unit) = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        contentDescription = context.getString(descriptionRes)
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(chipColor) }
        isClickable = true
        isFocusable = false
        setOnClickListener { onClick() }
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

    private fun iconButton(descriptionRes: Int, onClick: () -> Unit) = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(textColor)
        maxLines = 1
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
