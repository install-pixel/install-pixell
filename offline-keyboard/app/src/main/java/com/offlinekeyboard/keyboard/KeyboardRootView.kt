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
 * Two fixed rows above the keys, so nothing shifts while typing:
 *
 * 1. language · message / live text / word suggestions · computer↔mobile · settings (close while recording)
 * 2. input mode: [Aa Text] [translate Укр→Pol] [clipboard: translate selection/clipboard] [mic Voice]
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
        /** Translate the selection in place, or paste the clipboard translated. */
        fun onTranslateClipboard()
        fun onToolbarVoice()
        fun onToolbarVoiceCancel()
        fun onToolbarMode()
        /** Bluetooth button: start or stop sending keys to the connected computer. */
        fun onToolbarBluetooth()
        fun onToolbarSettings()
        fun onSuggestionPicked(word: String)
        fun onSuggestionLongPress(word: String)
    }

    data class ToolbarState(
        val languageCode: String,
        val computerMode: Boolean,
        val translateActive: Boolean,
        val translateLabel: String,
        val translateOffered: Boolean,
        val voiceOffered: Boolean,
        val recording: Boolean,
        val bluetooth: BluetoothButton = BluetoothButton.HIDDEN,
    )

    /** HIDDEN: the Bluetooth keyboard is off in settings; IDLE: on, typing goes to the phone; SENDING: to the computer. */
    enum class BluetoothButton { HIDDEN, IDLE, SENDING }

    var toolbarListener: ToolbarListener? = null

    val keyboardView = KeyboardView(context)

    private var theme = keyboardView.theme

    private val languageButton = button(R.string.toolbar_language_description) { toolbarListener?.onToolbarLanguage() }
        .apply { bold = true; textSizeSp = 15f }
    private val status = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.START
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { statusAction?.let { action -> statusAction = null; action() } }
    }
    private val suggestionViews = List(SUGGESTIONS) { suggestionView() }
    private val suggestionsRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        suggestionViews.forEach { addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
    }
    private val modeButton = button(R.string.toolbar_mode_description) { toolbarListener?.onToolbarMode() }
    private val bluetoothButton = button(R.string.toolbar_bluetooth_description) { toolbarListener?.onToolbarBluetooth() }
        .apply { icon = Icon.BLUETOOTH }
    private val settingsButton = button(R.string.toolbar_settings_description) {
        if (recording) toolbarListener?.onToolbarVoiceCancel() else toolbarListener?.onToolbarSettings()
    }

    private val textChip = button(R.string.mode_text_description) { toolbarListener?.onModeText() }
    private val translateChip = button(R.string.toolbar_translate_description) { toolbarListener?.onModeTranslate() }
    private val clipboardChip = button(R.string.clipboard_chip_description) { toolbarListener?.onTranslateClipboard() }
    private val voiceChip = button(R.string.toolbar_voice_description) { toolbarListener?.onToolbarVoice() }

    private var recording = false
    private var lastState: ToolbarState? = null
    private var message: CharSequence? = null
    private var statusAction: (() -> Unit)? = null
    private var liveText: CharSequence? = null
    private var suggestions: List<String> = emptyList()
    private var echo: CharSequence? = null
    private val clearMessage = Runnable { message = null; statusAction = null; refreshCentre() }

    init {
        orientation = VERTICAL

        val infoRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(languageButton, LayoutParams(dp(56), LayoutParams.MATCH_PARENT))
            addView(status, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(suggestionsRow, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(bluetoothButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
            addView(modeButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
            addView(settingsButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
        }
        val modeRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(3), dp(2), dp(3), dp(3))
            for ((c, weight) in listOf(textChip to 1f, translateChip to 1.35f, clipboardChip to 0.65f, voiceChip to 1f)) {
                addView(c, LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
        }
        addView(infoRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        addView(modeRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        addView(keyboardView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        applyTheme(theme)
        refreshCentre()
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        keyboardView.theme = value
        setBackgroundColor(value.background)
        suggestionViews.forEach { it.setTextColor(value.text) }
        for (b in listOf(languageButton, modeButton, settingsButton, bluetoothButton)) {
            b.foreground = value.text
            b.pressedFill = value.pressed
        }
        // In the dark the accent would only show on the active buttons; the language name carries it too.
        if (value.dark) languageButton.foreground = value.accent
        lastState?.let { updateToolbar(it) }
        refreshCentre()
    }

    fun updateToolbar(state: ToolbarState) {
        lastState = state
        recording = state.recording
        languageButton.text = state.languageCode
        modeButton.icon = if (state.computerMode) Icon.PHONE else Icon.KEYBOARD
        bluetoothButton.visibility = if (state.bluetooth == BluetoothButton.HIDDEN) View.GONE else View.VISIBLE
        val sending = state.bluetooth == BluetoothButton.SENDING
        bluetoothButton.fill = if (sending) theme.accent else 0
        bluetoothButton.foreground = if (sending) theme.onAccent else theme.text
        settingsButton.icon = if (state.recording) Icon.CLOSE else Icon.GEAR
        settingsButton.contentDescription = context.getString(
            if (state.recording) R.string.toolbar_voice_cancel_description else R.string.toolbar_settings_description,
        )

        textChip.text = context.getString(R.string.mode_text)
        styleChip(textChip, active = !state.translateActive && !state.recording, enabled = true)
        translateChip.icon = Icon.TRANSLATE
        translateChip.text = state.translateLabel
        styleChip(translateChip, active = state.translateActive, enabled = state.translateOffered)
        clipboardChip.icon = Icon.CLIPBOARD
        styleChip(clipboardChip, active = false, enabled = state.translateOffered)
        voiceChip.icon = if (state.recording) Icon.STOP else Icon.MIC
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

    /**
     * The end of the text the user is writing (or what was sent over Bluetooth), shown when there is
     * nothing else to show, so the strip always says where the text is.
     */
    fun showEcho(text: CharSequence?) {
        if (text?.toString() == echo?.toString()) return
        echo = text
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
        val isEcho = centreText == null && !showSuggestions
        status.text = if (isEcho) echo ?: "" else centreText ?: ""
        status.setTextColor(theme.text)
        status.visibility = if (showSuggestions) View.GONE else View.VISIBLE
        suggestionsRow.visibility = if (showSuggestions) View.VISIBLE else View.GONE
        suggestionViews.forEachIndexed { i, v ->
            val word = suggestions.getOrNull(i)
            v.text = word ?: ""
            v.isEnabled = word != null
        }
    }

    private fun styleChip(view: ToolbarButton, active: Boolean, enabled: Boolean) {
        view.fill = if (active) theme.accent else theme.function
        view.pressedFill = theme.pressed
        view.foreground = if (active) theme.onAccent else theme.text
        view.bold = active
        view.alpha = if (enabled) 1f else 0.4f
    }

    private fun button(descriptionRes: Int, onClick: () -> Unit) = ToolbarButton(context).apply {
        contentDescription = context.getString(descriptionRes)
        setOnClickListener { onClick() }
    }

    private fun suggestionView() = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        // Long words get a smaller font instead of being cut.
        setAutoSizeTextTypeUniformWithConfiguration(11, 16, 1, TypedValue.COMPLEX_UNIT_SP)
        setPadding(dp(2), 0, dp(2), 0)
        isFocusable = false
        setOnClickListener { (it as TextView).text?.toString()?.takeIf(String::isNotEmpty)?.let { w -> toolbarListener?.onSuggestionPicked(w) } }
        setOnLongClickListener {
            (it as TextView).text?.toString()?.takeIf(String::isNotEmpty)?.let { w -> toolbarListener?.onSuggestionLongPress(w) }
            true
        }
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
