package com.offlinekeyboard.keyboard

import android.content.ComponentCallbacks2
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.offlinekeyboard.R
import com.offlinekeyboard.core.KeyboardController
import com.offlinekeyboard.core.LabelContext
import com.offlinekeyboard.core.LatchState
import com.offlinekeyboard.core.ModifierKey
import com.offlinekeyboard.core.dictionary.WordRules
import com.offlinekeyboard.core.input.EditorCommand
import com.offlinekeyboard.core.input.EnterLabel
import com.offlinekeyboard.core.input.FieldInfo
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.SpecialKey
import com.offlinekeyboard.core.translation.TranslationCheck
import com.offlinekeyboard.core.translation.TranslationDirection
import com.offlinekeyboard.core.translation.TranslationResult
import com.offlinekeyboard.dictionary.UserDictionaryStore
import com.offlinekeyboard.input.InputConnectionAdapter
import com.offlinekeyboard.models.ModelEvents
import com.offlinekeyboard.models.ModelStore
import com.offlinekeyboard.settings.KeyboardSettings
import com.offlinekeyboard.settings.MicPermissionActivity
import com.offlinekeyboard.settings.SettingsActivity
import com.offlinekeyboard.settings.SettingsRepository
import com.offlinekeyboard.speech.SpeechController
import com.offlinekeyboard.speech.VoskSpeechEngine
import com.offlinekeyboard.translation.TranslationController
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
    KeyboardController.Listener,
    SpeechController.Host {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: SettingsRepository
    private lateinit var controller: KeyboardController
    private lateinit var models: ModelStore
    private lateinit var speechEngine: VoskSpeechEngine
    private lateinit var speech: SpeechController
    private lateinit var translation: TranslationController
    private lateinit var dictionary: UserDictionaryStore
    private var settings = KeyboardSettings()

    private val editor = InputConnectionAdapter(this)
    private val composer = TranslationComposer { currentInputConnection }
    private var currentField = FieldInfo(0, 0)
    private var rootView: KeyboardRootView? = null

    /** Dictation text currently shown as composing text in the field (normal, non-translate mode). */
    private var partialShown = false

    private val firePendingShortcut = Runnable {
        execute(controller.firePendingShortcut())
        render()
    }

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(this)
        models = ModelStore(this)
        speechEngine = VoskSpeechEngine(models)
        speech = SpeechController(this, speechEngine).also { it.host = this }
        translation = TranslationController(models)
        dictionary = UserDictionaryStore.get(this)
        // One small local read so the first keyboard already has the right mode and language.
        settings = runBlocking { withTimeoutOrNull(STARTUP_SETTINGS_TIMEOUT_MS) { repository.settings.first() } }
            ?: KeyboardSettings()
        controller = KeyboardController(settings.startMode, settings.lastLanguage, settings.enabledLanguages)
        controller.shortcuts = settings.shortcuts
        controller.listener = this
        composer.auto = settings.translationAuto
        scope.launch {
            repository.settings.collect { applySettings(it) }
        }
        scope.launch {
            // A model was imported or deleted in Settings: drop cached ones so the new files are used.
            ModelEvents.version.collect {
                if (speech.state == SpeechController.State.IDLE) speechEngine.release()
                translation.release()
            }
        }
    }

    override fun onDestroy() {
        speech.cancel()
        speechEngine.release()
        translation.release()
        dictionary.saveNow()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION") // TRIM_MEMORY_RUNNING_LOW is still the signal that the device is short of memory.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && speech.state == SpeechController.State.IDLE) {
            speechEngine.release()
            translation.release()
        }
    }

    private fun applySettings(new: KeyboardSettings) {
        val old = settings
        settings = new
        controller.enabledLanguages = new.enabledLanguages
        controller.shortcuts = new.shortcuts
        if (!new.rememberLastMode && old.defaultMode != new.defaultMode) controller.setMode(new.defaultMode)
        composer.auto = new.translationAuto
        if (!new.translateEnabled && old.translateEnabled) leaveSegment()
        updateSuggestions()
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
        composer.abandon()
        partialShown = false
        currentField = FieldInfo(attribute?.inputType ?: 0, attribute?.imeOptions ?: 0)
        editor.field = currentField
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) controller.onStartField(currentField.isNumeric)
        if (translateActive) translation.preload(settings.translationDirection)
        updateAutoShift()
        updateSuggestions()
        render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Recording never continues with the keyboard hidden; unconfirmed dictation text is dropped.
        speech.cancel()
        leaveSegment()
        rootView?.keyboardView?.cancelAllPointers()
        handler.removeCallbacks(firePendingShortcut)
        dictionary.saveNow()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        leaveSegment()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        val cursorLeftComposing = candidatesStart < 0 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd
        if (!composer.isEmpty && cursorLeftComposing) {
            // The user moved the cursor away from the segment being translated.
            leaveSegment()
        }
        if (partialShown && candidatesStart < 0) partialShown = false
        updateAutoShift()
        updateSuggestions()
        render()
    }

    /** Returns true when the Shift state changed. */
    private fun updateAutoShift(): Boolean {
        val before = controller.modifiers.state(ModifierKey.SHIFT)
        val capsMode = if (settings.autoCapitalize && !currentField.isRawKeyField) {
            currentInputConnection?.getCursorCapsMode(currentField.inputType) ?: 0
        } else {
            0
        }
        controller.setAutoShift(capsMode != 0)
        return before != controller.modifiers.state(ModifierKey.SHIFT)
    }

    // ------------------------------------------------------------------ translate mode

    private val translateOffered: Boolean get() = !currentField.isSensitive && !currentField.isRawKeyField

    private val translateActive: Boolean get() = settings.translateEnabled && translateOffered

    private fun directionLabel(direction: TranslationDirection) = "${direction.source.code}→${direction.target.code}"

    private fun startTranslation() {
        val (text, generation) = composer.beginTranslation() ?: return
        val direction = settings.translationDirection
        rootView?.showLive(getString(R.string.translating, directionLabel(direction)))
        translation.translate(text, direction) { result ->
            rootView?.showLive(null)
            when (result) {
                is TranslationResult.Success -> {
                    val inserted = composer.applyTranslation(generation, result.text)
                    if (inserted == null) {
                        rootView?.showStatus(getString(R.string.translation_discarded))
                    } else {
                        warnIfValuesChanged(text, inserted)
                    }
                }
                is TranslationResult.Failure -> {
                    composer.translationFailed(generation)
                    rootView?.showStatus(getString(R.string.translation_failed, result.reason))
                }
            }
            render()
        }
        render()
    }

    private val autoTranslate = Runnable { requestLive(commit = false) }

    /** Auto mode: the source being typed is shown above the keys; its translation follows after a short pause. */
    private fun scheduleLive() {
        handler.removeCallbacks(autoTranslate)
        val source = composer.text.trim()
        rootView?.showLive(if (source.isEmpty()) null else "${settings.translationDirection.source.code}: $source")
        if (!composer.isEmpty) handler.postDelayed(autoTranslate, AUTO_TRANSLATE_DELAY_MS)
    }

    /** Auto mode: translate the current segment into the field; [commit] ends the segment afterwards. */
    private fun requestLive(commit: Boolean) {
        handler.removeCallbacks(autoTranslate)
        val source = composer.text
        if (source.isBlank()) {
            if (commit) leaveSegment()
            return
        }
        val generation = composer.generation
        translation.translate(source, settings.translationDirection) { result ->
            when (result) {
                is TranslationResult.Success ->
                    if (composer.showLive(generation, result.text) && commit) {
                        val inserted = composer.commitShown()
                        rootView?.showLive(null)
                        if (inserted != null) warnIfValuesChanged(source, inserted)
                    }
                is TranslationResult.Failure -> rootView?.showStatus(getString(R.string.translation_failed, result.reason))
            }
            render()
        }
    }

    /** Ends the translate segment (keeps an up-to-date auto translation, otherwise the typed source). */
    private fun leaveSegment() {
        handler.removeCallbacks(autoTranslate)
        val source = composer.text
        val inserted = composer.leave()
        if (inserted != null) warnIfValuesChanged(source, inserted)
        rootView?.showLive(null)
    }

    private fun endsSentence(text: String) = text.isNotEmpty() && text.last() in ".!?…"

    /**
     * Numbers, times, codes and abbreviations must survive translation. If some are missing, warn and let
     * the user tap the warning to put the original text back.
     */
    private fun warnIfValuesChanged(source: String, inserted: String) {
        val missing = TranslationCheck.missing(source, inserted)
        if (missing.isEmpty()) return
        val list = missing.joinToString(", ") { "«$it»" }
        rootView?.showStatus(getString(R.string.translation_check_warning, list), CHECK_WARNING_MS) {
            val ic = currentInputConnection ?: return@showStatus
            val before = ic.getTextBeforeCursor(inserted.length, 0)?.toString()
            if (before == inserted) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(inserted.length, 0)
                ic.commitText(source, 1)
                ic.endBatchEdit()
                rootView?.showStatus(getString(R.string.translation_restored))
            } else {
                rootView?.showStatus(getString(R.string.translation_restore_impossible))
            }
        }
    }

    private fun setTranslateEnabled(enabled: Boolean, direction: TranslationDirection) {
        scope.launch { repository.update { it.copy(translateEnabled = enabled, translationDirection = direction) } }
        settings = settings.copy(translateEnabled = enabled, translationDirection = direction)
    }

    // ------------------------------------------------------------------ keys

    override fun onKey(key: Key) {
        handler.removeCallbacks(firePendingShortcut)
        execute(controller.onKey(key))
        if (controller.pendingModifierShortcut != null) {
            handler.postDelayed(firePendingShortcut, KeyboardController.PENDING_SHORTCUT_DELAY_MS)
        }
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
                EditorCommand.ToggleTranslate -> if (settings.translateEnabled) onModeText() else onModeTranslate()
                EditorCommand.HideKeyboard -> requestHideSelf(0)
                EditorCommand.OpenSettings -> onToolbarSettings()
                is EditorCommand.CommitText ->
                    if (translateActive) {
                        composer.append(command.text)
                        if (composer.auto) {
                            if (endsSentence(command.text)) requestLive(commit = true) else scheduleLive()
                        }
                    } else {
                        learnBeforeSeparator(command.text)
                        editor.execute(command)
                    }
                EditorCommand.DeleteBackward ->
                    if (translateActive && composer.backspace()) {
                        if (composer.auto) scheduleLive()
                    } else {
                        editor.execute(command)
                    }
                is EditorCommand.Enter ->
                    if (translateActive && !composer.isEmpty && !command.forceNewline) {
                        if (composer.auto) requestLive(commit = true) else startTranslation()
                    } else {
                        leaveSegment()
                        learnBeforeSeparator("\n")
                        editor.execute(command)
                    }
                else -> {
                    leaveSegment()
                    if (command is EditorCommand.Tab) learnBeforeSeparator("\t")
                    editor.execute(command)
                }
            }
        }
    }

    // ------------------------------------------------------------------ user dictionary

    private val learningAllowed: Boolean get() = settings.learnWords && currentField.allowsLearning

    /** When a space or punctuation follows a word, that word goes into the user dictionary. */
    private fun learnBeforeSeparator(text: String) {
        if (!learningAllowed || text.isEmpty()) return
        val first = text[0]
        if (!(first.isWhitespace() || first in ".,!?;:)…")) return
        val before = currentInputConnection?.getTextBeforeCursor(WORD_CONTEXT, 0) ?: return
        val word = WordRules.trailingWord(before)
        if (word.isNotEmpty() && dictionary.dictionary.learn(controller.language, word, System.currentTimeMillis())) {
            dictionary.changed()
        }
    }

    private fun updateSuggestions() {
        val root = rootView ?: return
        val show = settings.showSuggestions && currentField.allowsLearning && !translateActive &&
            speech.state == SpeechController.State.IDLE
        val ic = currentInputConnection
        if (!show || ic == null || !ic.getSelectedText(0).isNullOrEmpty()) {
            root.showSuggestions(emptyList())
            return
        }
        val before = ic.getTextBeforeCursor(WORD_CONTEXT, 0) ?: ""
        val after = ic.getTextAfterCursor(1, 0) ?: ""
        val prefix = if (WordRules.continuesWord(after)) "" else WordRules.trailingWord(before)
        root.showSuggestions(if (prefix.isEmpty()) emptyList() else dictionary.dictionary.suggestions(controller.language, prefix))
    }

    override fun onSuggestionPicked(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = WordRules.trailingWord(ic.getTextBeforeCursor(WORD_CONTEXT, 0) ?: "")
        ic.beginBatchEdit()
        if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        if (learningAllowed && dictionary.dictionary.learn(controller.language, word, System.currentTimeMillis())) {
            dictionary.changed()
        }
    }

    override fun onSuggestionLongPress(word: String) {
        if (dictionary.dictionary.remove(controller.language, word)) {
            dictionary.changed()
            rootView?.showStatus(getString(R.string.dictionary_word_removed, word))
            updateSuggestions()
        }
    }

    // ------------------------------------------------------------------ toolbar

    override fun onToolbarLanguage() {
        controller.nextLanguage()
        render()
    }

    /** "Aa Text" chip: plain typing (turns translation off). */
    override fun onModeText() {
        if (!settings.translateEnabled) return
        leaveSegment()
        setTranslateEnabled(false, settings.translationDirection)
        updateSuggestions()
        render()
    }

    /** "⇄" chip: turns translation on; when already on, swaps the direction. */
    override fun onModeTranslate() {
        if (!translateOffered) {
            rootView?.showStatus(getString(R.string.translate_not_here))
            return
        }
        val direction = when {
            settings.translateEnabled -> if (settings.translationDirection == TranslationDirection.UK_TO_PL) {
                TranslationDirection.PL_TO_UK
            } else {
                TranslationDirection.UK_TO_PL
            }
            else -> TranslationDirection.suggestedFor(controller.language) ?: settings.translationDirection
        }
        if (!translation.isInstalled(direction)) {
            rootView?.showStatus(getString(R.string.translation_model_missing, directionLabel(direction)))
            return
        }
        leaveSegment()
        controller.setLanguage(direction.source)
        setTranslateEnabled(true, direction)
        translation.preload(direction)
        rootView?.showStatus(
            getString(if (settings.translationAuto) R.string.translate_on_auto else R.string.translate_on, directionLabel(direction)),
        )
        updateSuggestions()
        render()
    }

    override fun onToolbarVoice() {
        val language = if (translateActive) settings.translationDirection.source else settings.voiceLanguage ?: controller.language
        leaveSegment()
        speech.toggle(currentField, language, settings.voiceCommands)
        render()
    }

    override fun onToolbarVoiceCancel() {
        speech.cancel()
        render()
    }

    override fun onToolbarMode() {
        controller.setMode(if (controller.mode == KeyboardMode.COMPUTER) KeyboardMode.MOBILE else KeyboardMode.COMPUTER)
        render()
    }

    override fun onToolbarSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ------------------------------------------------------------------ voice host

    override fun textBeforeCursor(): CharSequence = currentInputConnection?.getTextBeforeCursor(WORD_CONTEXT, 0) ?: ""

    override fun showPartial(text: String) {
        if (translateActive) {
            rootView?.showLive("🎤 $text")
        } else {
            currentInputConnection?.setComposingText(text, 1)
            partialShown = true
        }
    }

    override fun commitSegment(text: String, language: Language) {
        if (translateActive) {
            rootView?.showLive(null)
            composer.append(text)
            if (composer.auto) requestLive(commit = true) else startTranslation()
        } else {
            currentInputConnection?.commitText(text, 1)
            partialShown = false
        }
    }

    override fun clearPartial() {
        if (partialShown) {
            currentInputConnection?.let {
                it.setComposingText("", 1)
                it.finishComposingText()
            }
            partialShown = false
        }
        if (composer.isEmpty) rootView?.showLive(null)
    }

    override fun onStateChanged(state: SpeechController.State, language: Language) {
        rootView?.showLive(
            when (state) {
                SpeechController.State.LOADING -> getString(R.string.voice_loading, language.code)
                SpeechController.State.LISTENING -> getString(R.string.voice_listening, language.code)
                SpeechController.State.IDLE -> null
            },
        )
        updateSuggestions()
        render()
    }

    override fun showMessage(resId: Int, detail: String?) {
        val text = getString(resId)
        rootView?.showStatus(if (detail.isNullOrEmpty()) text else "$text ($detail)")
    }

    override fun requestMicPermission() {
        rootView?.showStatus(getString(R.string.voice_permission_needed))
        startActivity(Intent(this, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ------------------------------------------------------------------ controller callbacks

    override fun onLanguageChanged(language: Language) {
        scope.launch { repository.update { it.copy(lastLanguage = language) } }
        updateSuggestions()
    }

    override fun onModeChanged(mode: KeyboardMode) {
        scope.launch { repository.update { it.copy(lastMode = mode) } }
    }

    // ------------------------------------------------------------------ rendering

    private fun render() {
        val root = rootView ?: return
        val mods = controller.modifiers
        val enterLabel = if (translateActive && !composer.isEmpty) EnterLabel.TRANSLATE else currentField.enterLabel
        val labels = LabelContext(
            shift = mods.isActive(ModifierKey.SHIFT),
            capsLock = mods.capsLock,
            language = controller.language,
            mode = controller.mode,
            enterLabel = enterLabel,
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
            KeyboardRootView.ToolbarState(
                languageCode = controller.language.code,
                modeGlyph = if (controller.mode == KeyboardMode.COMPUTER) "📱" else "⌨",
                translateActive = translateActive,
                translateLabel = directionLabel(settings.translationDirection),
                translateOffered = translateOffered,
                voiceOffered = speech.isOffered(currentField),
                recording = speech.state != SpeechController.State.IDLE,
            ),
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
        const val WORD_CONTEXT = 64
        const val CHECK_WARNING_MS = 8000L
        const val AUTO_TRANSLATE_DELAY_MS = 400L
    }
}
