package com.offlinekeyboard.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.offlinekeyboard.core.KeyboardController
import com.offlinekeyboard.core.LabelContext
import com.offlinekeyboard.core.LatchState
import com.offlinekeyboard.core.ModifierKey
import com.offlinekeyboard.core.input.EditorCommand
import com.offlinekeyboard.core.input.FieldInfo
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.SpecialKey
import com.offlinekeyboard.input.InputConnectionAdapter
import com.offlinekeyboard.settings.KeyboardSettings
import com.offlinekeyboard.settings.SettingsActivity
import com.offlinekeyboard.settings.SettingsRepository
import com.offlinekeyboard.speech.SpeechController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The system keyboard. Text goes straight into the focused field of the current app through
 * InputConnection; there is no intermediate typing window (fullscreen extract mode is disabled).
 */
class KeyboardService : InputMethodService(),
    KeyboardView.Listener,
    KeyboardRootView.ToolbarListener,
    KeyboardController.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: SettingsRepository
    private lateinit var controller: KeyboardController
    private var settings = KeyboardSettings()

    private val editor = InputConnectionAdapter(this)
    private val speech = SpeechController()
    private var field = FieldInfo(0, 0)
    private var rootView: KeyboardRootView? = null

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(this)
        // One small local read so the first keyboard already has the right mode and language.
        settings = runBlocking { withTimeoutOrNull(STARTUP_SETTINGS_TIMEOUT_MS) { repository.settings.first() } }
            ?: KeyboardSettings()
        controller = KeyboardController(settings.startMode, settings.lastLanguage, settings.enabledLanguages)
        controller.listener = this
        scope.launch {
            repository.settings.collect { applySettings(it) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun applySettings(new: KeyboardSettings) {
        val old = settings
        settings = new
        controller.enabledLanguages = new.enabledLanguages
        if (!new.rememberLastMode && old.defaultMode != new.defaultMode) controller.setMode(new.defaultMode)
        render()
    }

    override fun onCreateInputView(): View {
        val root = KeyboardRootView(this)
        root.keyboardView.listener = this
        root.toolbarListener = this
        rootView = root
        render()
        return root
    }

    // Never take over the screen with the extract-text window: typing must happen in the app's own field.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        field = FieldInfo(attribute?.inputType ?: 0, attribute?.imeOptions ?: 0)
        editor.field = field
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) controller.onStartField(field.isNumeric)
        updateAutoShift()
        render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        rootView?.keyboardView?.cancelAllPointers()
        speech.cancel()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (updateAutoShift()) render()
    }

    /** Returns true when the Shift state changed. */
    private fun updateAutoShift(): Boolean {
        val before = controller.modifiers.state(ModifierKey.SHIFT)
        val capsMode = if (settings.autoCapitalize && !field.isRawKeyField) {
            currentInputConnection?.getCursorCapsMode(field.inputType) ?: 0
        } else {
            0
        }
        controller.setAutoShift(capsMode != 0)
        return before != controller.modifiers.state(ModifierKey.SHIFT)
    }

    // ------------------------------------------------------------------ keys

    override fun onKey(key: Key) {
        execute(controller.onKey(key))
        render()
    }

    override fun onAlternative(text: String) {
        execute(controller.onAlternative(text))
        render()
    }

    override fun onSpaceSwipe(step: Int) {
        if (!settings.spaceSwipeSwitchesLanguage) return
        controller.nextLanguage(step)
        render()
    }

    override fun onLanguageKeyLongPress() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    private fun execute(commands: List<EditorCommand>) {
        for (command in commands) {
            when (command) {
                EditorCommand.StartVoice -> onToolbarVoice()
                EditorCommand.HideKeyboard -> onToolbarHide()
                EditorCommand.OpenSettings -> onToolbarSettings()
                else -> editor.execute(command)
            }
        }
    }

    // ------------------------------------------------------------------ toolbar

    override fun onToolbarLanguage() {
        controller.nextLanguage()
        render()
    }

    override fun onToolbarVoice() {
        val message = speech.onVoiceKey(field, settings.voiceLanguage) ?: return
        rootView?.showStatus(getString(message))
    }

    override fun onToolbarMode() {
        controller.setMode(if (controller.mode == KeyboardMode.COMPUTER) KeyboardMode.MOBILE else KeyboardMode.COMPUTER)
        render()
    }

    override fun onToolbarSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onToolbarHide() {
        requestHideSelf(0)
    }

    // ------------------------------------------------------------------ controller callbacks

    override fun onLanguageChanged(language: Language) {
        scope.launch { repository.update { it.copy(lastLanguage = language) } }
    }

    override fun onModeChanged(mode: KeyboardMode) {
        scope.launch { repository.update { it.copy(lastMode = mode) } }
    }

    // ------------------------------------------------------------------ rendering

    private fun render() {
        val root = rootView ?: return
        val mods = controller.modifiers
        val labels = LabelContext(
            shift = mods.isActive(ModifierKey.SHIFT),
            capsLock = mods.capsLock,
            language = controller.language,
            mode = controller.mode,
            enterLabel = field.enterLabel,
        )
        val baseHeight = if (controller.mode == KeyboardMode.COMPUTER) COMPUTER_KEY_DP else MOBILE_KEY_DP
        root.keyboardView.hapticFeedback = settings.hapticFeedback
        root.keyboardView.spaceSwipeEnabled = settings.spaceSwipeSwitchesLanguage
        root.keyboardView.bind(
            layout = controller.layout(settings.mobileCursorRow),
            labels = labels,
            keyHeightDp = baseHeight * settings.keyHeightScale,
            highlight = ::highlightFor,
        )
        root.updateToolbar(
            languageCode = controller.language.code,
            modeGlyph = if (controller.mode == KeyboardMode.COMPUTER) "📱" else "⌨",
            voiceEnabled = speech.isOffered(field),
        )
    }

    private fun highlightFor(key: Key): KeyboardView.KeyHighlight {
        val special = (key.action as? KeyAction.Special)?.key ?: return KeyboardView.KeyHighlight.NONE
        val mods = controller.modifiers
        fun latch(state: LatchState) = when (state) {
            LatchState.OFF -> KeyboardView.KeyHighlight.NONE
            LatchState.ONCE -> KeyboardView.KeyHighlight.ACTIVE
            LatchState.LOCKED -> KeyboardView.KeyHighlight.LOCKED
        }
        return when (special) {
            SpecialKey.SHIFT -> latch(mods.state(ModifierKey.SHIFT))
            SpecialKey.CTRL -> latch(mods.state(ModifierKey.CTRL))
            SpecialKey.ALT -> latch(mods.state(ModifierKey.ALT))
            SpecialKey.META -> latch(mods.state(ModifierKey.META))
            SpecialKey.CAPS_LOCK -> if (mods.capsLock) KeyboardView.KeyHighlight.LOCKED else KeyboardView.KeyHighlight.NONE
            SpecialKey.LAYER_FUNCTION ->
                if (controller.layer == Layer.FUNCTION) KeyboardView.KeyHighlight.ACTIVE else KeyboardView.KeyHighlight.NONE
            SpecialKey.LAYER_SYMBOLS ->
                if (controller.mode == KeyboardMode.COMPUTER && controller.layer == Layer.SYMBOLS) {
                    KeyboardView.KeyHighlight.ACTIVE
                } else {
                    KeyboardView.KeyHighlight.NONE
                }
            else -> KeyboardView.KeyHighlight.NONE
        }
    }

    private companion object {
        const val STARTUP_SETTINGS_TIMEOUT_MS = 500L
        const val COMPUTER_KEY_DP = 40f
        const val MOBILE_KEY_DP = 52f
    }
}
