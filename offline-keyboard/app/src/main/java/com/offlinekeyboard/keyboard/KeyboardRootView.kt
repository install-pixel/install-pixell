package com.offlinekeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.offlinekeyboard.R

/**
 * Two fixed rows above the keys, so nothing shifts while typing:
 *
 * 1. message / live text / word suggestions · computer↔mobile · settings (close while recording)
 * 2. input mode: [Aa Text] [translate Укр→Pol] [clipboard: translate selection/clipboard] [mic Voice]
 *
 * Every element has a fixed size; only texts and colours change.
 */
@SuppressLint("ViewConstructor")
class KeyboardRootView(context: Context) : LinearLayout(context) {

    interface ToolbarListener {
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
        /** Puts the original text back in place of the last translation. */
        fun onUndoTranslation()
        fun onToolbarSettings()
        fun onSuggestionPicked(word: String)
        fun onSuggestionLongPress(word: String)
    }

    data class ToolbarState(
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

    /** The keyboard's own stickers, shown in place of the keys. */
    val stickerPanel = StickerPanel(context).apply { visibility = View.GONE }

    val stickersShown: Boolean get() = stickerPanel.visibility == View.VISIBLE

    private var theme = keyboardView.theme

    /** The whole text on one line; the strip shows the end and can be swiped back to the beginning. */
    private val status = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isSingleLine = true
        setHorizontallyScrolling(true)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { statusAction?.let { action -> statusAction = null; action() } }
    }
    private val statusScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
    private val undoButton = button(R.string.toolbar_undo_translation_description) { toolbarListener?.onUndoTranslation() }
        .apply { icon = Icon.UNDO; visibility = View.GONE }
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

    /** Compact toolbar: shows or hides the row of input modes. */
    private val modesButton = button(R.string.toolbar_modes_description) { toggleModes() }
        .apply { icon = Icon.MODES; visibility = View.GONE }

    private val textChip = button(R.string.mode_text_description) { toolbarListener?.onModeText() }
    private val translateChip = button(R.string.toolbar_translate_description) { toolbarListener?.onModeTranslate() }
    private val clipboardChip = button(R.string.clipboard_chip_description) { toolbarListener?.onTranslateClipboard() }
    private val voiceChip = button(R.string.toolbar_voice_description) { toolbarListener?.onToolbarVoice() }

    private var recording = false
    private lateinit var modeRow: LinearLayout

    /** One row above the keys: the input modes appear on demand (and while translating or dictating). */
    var compact = false
        set(value) {
            if (field == value) return
            field = value
            refreshModesRow()
        }
    private var modesExpanded = false
    /** Folded by hand while translating or dictating (the row opens by itself for those). */
    private var foldedWhileBusy = false
    private var lastState: ToolbarState? = null
    private var message: CharSequence? = null
    private var statusAction: (() -> Unit)? = null
    private var liveText: CharSequence? = null
    private var suggestions: List<String> = emptyList()
    private var echo: CharSequence? = null
    private val clearMessage = Runnable { message = null; statusAction = null; refreshCentre() }

    init {
        orientation = VERTICAL
        // The key preview of the top row is drawn over the toolbar above it.
        clipChildren = false
        clipToPadding = false
        // Android 15 draws the keyboard window behind the navigation bar (back, home, the keyboard switch
        // globe): the keys move up by the bar's height, and the keyboard's background shows behind it.
        // Older Android places the window above the bar and reports no inset here.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            updateBottomSpace()
            insets
        }

        val infoRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(modesButton, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
            addView(statusScroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(suggestionsRow, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(undoButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
            addView(bluetoothButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
            addView(modeButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
            addView(settingsButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
        }
        modeRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(3), dp(2), dp(3), dp(3))
            for ((c, weight) in listOf(textChip to 1f, translateChip to 1.35f, clipboardChip to 0.65f, voiceChip to 1f)) {
                addView(c, LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
        }
        addView(infoRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        addView(modeRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        addView(keyboardView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(stickerPanel, LayoutParams(LayoutParams.MATCH_PARENT, 0))
        applyTheme(theme)
        refreshCentre()
    }

    private var navBars = Insets.NONE
    internal var bottomSpaceChangesForTest = 0
    private val layoutWatcher = ViewTreeObserver.OnGlobalLayoutListener { updateBottomSpace() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutWatcher)
    }

    /**
     * Room below the keys: the navigation bar, or the row of keyboard buttons Android draws inside the
     * keyboard window with gesture navigation (hide ∨ and the keyboard switch globe), whichever is taller.
     * That row is taller than the gesture bar's inset, so the inset alone left it over the bottom keys.
     */
    private fun updateBottomSpace() {
        val bottom = maxOf(navBars.bottom, imeButtonsHeight())
        if (paddingLeft != navBars.left || paddingRight != navBars.right || paddingBottom != bottom) {
            bottomSpaceChangesForTest++
            setPadding(navBars.left, 0, navBars.right, bottom)
        }
    }

    /** Height of the system's button row in the keyboard window (Android 13+), 0 when there is none. */
    private fun imeButtonsHeight(): Int {
        fun find(view: View): View? {
            if (view === this) return null
            if (view.javaClass.name.endsWith("NavigationBarFrame")) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        val frame = find(rootView) ?: return 0
        return if (frame.visibility == View.VISIBLE) frame.height else 0
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        keyboardView.theme = value
        stickerPanel.applyTheme(value)
        setBackgroundColor(value.background)
        suggestionViews.forEach { it.setTextColor(value.text) }
        for (b in listOf(textChip, translateChip, clipboardChip, voiceChip)) b.glossy = value.glossy
        for (b in listOf(modeButton, settingsButton, bluetoothButton, undoButton, modesButton)) {
            b.foreground = value.text
            b.pressedFill = value.pressed
            b.glossy = value.glossy
        }
        lastState?.let { updateToolbar(it) }
        refreshCentre()
    }

    fun updateToolbar(state: ToolbarState) {
        lastState = state
        recording = state.recording
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
        refreshModesRow()
        refreshCentre()
    }

    /** Folds the modes row away again (the keyboard was hidden). */
    fun collapseModes() {
        modesExpanded = false
        refreshModesRow()
    }

    private fun busy() = lastState?.let { it.translateActive || it.recording } ?: false

    private fun toggleModes() {
        if (busy()) foldedWhileBusy = !foldedWhileBusy else modesExpanded = !modesExpanded
        refreshModesRow()
    }

    private fun refreshModesRow() {
        val busy = busy()
        if (!busy) foldedWhileBusy = false
        val open = if (busy) !foldedWhileBusy else modesExpanded
        modesButton.visibility = if (compact) View.VISIBLE else View.GONE
        // Lit while translating or dictating even when folded, so the mode is not forgotten.
        modesButton.fill = if (compact && (open || busy)) theme.function else 0
        modeRow.visibility = if (!compact || open) View.VISIBLE else View.GONE
    }

    /** Swaps the keys for the sticker panel (same height, so nothing jumps) or back. */
    fun showStickers(show: Boolean) {
        if (show == stickersShown) return
        if (show) {
            stickerPanel.layoutParams = stickerPanel.layoutParams.apply { height = keyboardView.height.coerceAtLeast(dp(120)) }
            keyboardView.visibility = View.GONE
            stickerPanel.visibility = View.VISIBLE
        } else {
            stickerPanel.visibility = View.GONE
            keyboardView.visibility = View.VISIBLE
        }
    }

    /** Short, non-blocking message. [onTap] makes it tappable (e.g. "restore original"). */
    fun showStatus(text: CharSequence, durationMs: Long = STATUS_MILLIS, onTap: (() -> Unit)? = null) {
        removeCallbacks(clearMessage)
        message = text
        statusAction = onTap
        refreshCentre()
        postDelayed(clearMessage, durationMs)
    }

    fun clearStatus() {
        removeCallbacks(clearMessage)
        clearMessage.run()
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

    /** Shows the "restore original" button while the last translation can still be undone. */
    fun showUndo(visible: Boolean) {
        undoButton.visibility = if (visible) View.VISIBLE else View.GONE
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
        val text = if (isEcho) echo ?: "" else centreText ?: ""
        if (text.toString() != status.text.toString()) {
            status.text = text
            // Newest text in view; earlier parts are a swipe away.
            statusScroll.post { statusScroll.fullScroll(View.FOCUS_RIGHT) }
        }
        status.setTextColor(theme.text)
        statusScroll.visibility = if (showSuggestions) View.GONE else View.VISIBLE
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
        viewTreeObserver.removeOnGlobalLayoutListener(layoutWatcher)
        super.onDetachedFromWindow()
        removeCallbacks(clearMessage)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val STATUS_MILLIS = 3500L
        const val SUGGESTIONS = 3
    }
}
