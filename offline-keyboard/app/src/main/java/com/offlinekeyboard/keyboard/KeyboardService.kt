package com.offlinekeyboard.keyboard

import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import com.offlinekeyboard.R
import com.offlinekeyboard.bluetooth.Bluetooth
import com.offlinekeyboard.bluetooth.bluetoothKeyboardSupported
import com.offlinekeyboard.core.HidKeys
import com.offlinekeyboard.core.KeyLabels
import com.offlinekeyboard.core.KeyboardController
import com.offlinekeyboard.core.LabelContext
import com.offlinekeyboard.core.LatchState
import com.offlinekeyboard.core.ModifierKey
import com.offlinekeyboard.core.dictionary.WordRules
import com.offlinekeyboard.core.input.EditorCommand
import com.offlinekeyboard.core.input.EnterLabel
import com.offlinekeyboard.core.input.FieldInfo
import com.offlinekeyboard.core.input.KeyMods
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
import com.offlinekeyboard.translation.GlossaryStore
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
    private val composer = TranslationComposer(
        connection = { currentInputConnection },
        cursorPosition = ::cursorPosition,
    )

    /** Translate state just chosen by the user, until DataStore confirms it (an older emission must not undo it). */
    private var pendingTranslateState: Pair<Boolean, TranslationDirection>? = null
    private var currentField = FieldInfo(0, 0)
    private var rootView: KeyboardRootView? = null

    /** Dictation text currently shown as composing text in the field (normal, non-translate mode). */
    private var partialShown = false

    /** Bluetooth keyboard mode: keys go to the connected computer instead of the phone's field. */
    private var bluetoothSending = false

    /** The end of what was typed on the computer, shown above the keys (the phone field stays empty). */
    private val sentText = StringBuilder()

    private val firePendingShortcut = Runnable {
        execute(controller.firePendingShortcut())
        render()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = SettingsRepository(this)
        models = ModelStore(this)
        speechEngine = VoskSpeechEngine(models)
        speech = SpeechController(this, speechEngine).also { it.host = this }
        translation = TranslationController(models, GlossaryStore.get(this))
        dictionary = UserDictionaryStore.get(this)
        // One small local read so the first keyboard already has the right mode and language.
        settings = runBlocking { withTimeoutOrNull(STARTUP_SETTINGS_TIMEOUT_MS) { repository.settings.first() } }
            ?: KeyboardSettings()
        controller = KeyboardController(settings.startMode, settings.lastLanguage, settings.enabledLanguages)
        controller.shortcuts = settings.shortcuts
        controller.listener = this
        translation.accurate = settings.translationAccurate
        scope.launch {
            repository.settings.collect { applySettings(it) }
        }
        if (settings.bluetoothKeyboard) Bluetooth.start(this)
        scope.launch {
            Bluetooth.status.collect {
                updateSuggestions()
                render()
            }
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
        if (instance === this) instance = null
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

    private fun applySettings(incoming: KeyboardSettings) {
        val old = settings
        val pending = pendingTranslateState
        val new = if (pending == null) {
            incoming
        } else if (incoming.translateEnabled == pending.first && incoming.translationDirection == pending.second) {
            pendingTranslateState = null
            incoming
        } else {
            incoming.copy(translateEnabled = pending.first, translationDirection = pending.second)
        }
        settings = new
        controller.enabledLanguages = new.enabledLanguages
        controller.shortcuts = new.shortcuts
        if (!new.rememberLastMode && old.defaultMode != new.defaultMode) controller.setMode(new.defaultMode)
        translation.accurate = new.translationAccurate
        if (new.bluetoothKeyboard != old.bluetoothKeyboard) {
            if (new.bluetoothKeyboard) Bluetooth.start(this) else bluetoothSending = false
        }
        if (old.themeMode != new.themeMode || old.accent != new.accent) rootView?.applyTheme(currentTheme())
        if (!new.translateEnabled && old.translateEnabled) leaveSegment()
        updateSuggestions()
        render()
    }

    override fun onCreateInputView(): View {
        val root = KeyboardRootView(this)
        root.keyboardView.listener = this
        root.toolbarListener = this
        root.applyTheme(currentTheme())
        rootView = root
        render()
        return root
    }

    private fun currentTheme() = KeyboardTheme.resolve(this, settings.themeMode, settings.accent)

    // Never take over the screen with the extract-text window: typing must happen in the app's own field.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composer.abandon()
        partialShown = false
        currentField = FieldInfo(attribute?.inputType ?: 0, attribute?.imeOptions ?: 0)
        editor.field = currentField
        controller.rawKeyField = currentField.isRawKeyField
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) controller.onStartField(currentField.isNumeric)
        syncTranslateSource(showProblems = false)
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
        if (!composer.isEmpty && !composer.isOwnCursorUpdate(newSelStart, newSelEnd, candidatesStart, candidatesEnd)) {
            // The user moved the cursor away from the segment being translated.
            leaveSegment()
        }
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

    private val previewRunnable = Runnable { requestPreview() }
    private val pauseRunnable = Runnable { if (settings.translationAuto && !composer.isEmpty && !composer.pending) startTranslation() }

    /** After each change of the segment: preview soon, and (automatic mode) insert after a pause. */
    private fun segmentChanged() {
        handler.removeCallbacks(previewRunnable)
        handler.removeCallbacks(pauseRunnable)
        if (composer.isEmpty) {
            rootView?.showLive(null)
            return
        }
        rootView?.showLive("${settings.translationDirection.target.code}: …")
        handler.postDelayed(previewRunnable, PREVIEW_DELAY_MS)
        if (settings.translationAuto && settings.translationPauseMs > 0) {
            handler.postDelayed(pauseRunnable, settings.translationPauseMs.toLong())
        }
    }

    private fun requestPreview() {
        val source = composer.text
        if (source.isBlank()) return
        val generation = composer.generation
        val direction = settings.translationDirection
        translation.preview(source, direction) { result ->
            if (generation != composer.generation || composer.pending) return@preview
            if (result is TranslationResult.Success) rootView?.showLive("${direction.target.code}: ${result.text.trim()}")
        }
    }

    /** Final translation of the segment; it replaces exactly the typed segment once. */
    private fun startTranslation() {
        handler.removeCallbacks(previewRunnable)
        handler.removeCallbacks(pauseRunnable)
        val (text, generation) = composer.beginTranslation() ?: return
        val direction = settings.translationDirection
        rootView?.showLive(getString(R.string.translating, directionLabel(direction)))
        translation.translate(text, direction) { result ->
            when (result) {
                is TranslationResult.Success -> {
                    val inserted = composer.applyTranslation(generation, result.text)
                    if (inserted != null) {
                        rootView?.showLive(null)
                        showTranslated(text, inserted, direction.target)
                    } else if (!settings.translationAuto) {
                        rootView?.showStatus(getString(R.string.translation_discarded))
                    }
                    // Automatic mode: the user kept typing; the next pause or sentence end translates again.
                }
                is TranslationResult.Failure -> {
                    composer.translationFailed(generation)
                    rootView?.showLive(null)
                    rootView?.showStatus(getString(R.string.translation_failed, result.reason))
                }
            }
            render()
        }
        render()
    }

    /** Ends the translate segment; the typed source stays as it is. */
    private fun leaveSegment() {
        handler.removeCallbacks(previewRunnable)
        handler.removeCallbacks(pauseRunnable)
        composer.leave()
        rootView?.showLive(null)
    }

    private fun endsSentence(text: String) = text.isNotEmpty() && text.last() in ".!?…"

    /**
     * After a translation was inserted: a short confirmation, or a warning if numbers, times, codes or
     * abbreviations changed. Tapping either puts the original text back (if the translation is still
     * right before the cursor).
     */
    private fun showTranslated(source: String, inserted: String, target: Language) {
        val missing = TranslationCheck.missing(source, inserted)
        val message = if (missing.isEmpty()) {
            getString(R.string.translation_done_undo, target.code, inserted.trim())
        } else {
            getString(R.string.translation_check_warning, missing.joinToString(", ") { "«$it»" })
        }
        rootView?.showStatus(message, if (missing.isEmpty()) DONE_MESSAGE_MS else CHECK_WARNING_MS) {
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

    /**
     * 📋⇄ button: translates the selected text in place; without a selection, inserts the clipboard
     * translated. The direction is detected from the text (Cyrillic → Polish, Latin → Ukrainian).
     */
    override fun onTranslateClipboard() {
        val ic = currentInputConnection ?: return
        if (!translateOffered) {
            rootView?.showStatus(getString(R.string.translate_not_here))
            return
        }
        leaveSegment()
        val selected = ic.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() }
        val source = selected ?: clipboardText()
        if (source.isNullOrBlank()) {
            rootView?.showStatus(getString(R.string.clipboard_empty))
            return
        }
        // Into the language being written; text already in that language goes to the translation target.
        val writing = if (translateActive) settings.translationDirection.target else controller.language
        val direction = TranslationDirection.detect(source, writing, settings.translationDirection.target)
            ?: settings.translationDirection
        if (!translation.isInstalled(direction)) {
            rootView?.showStatus(getString(R.string.translation_model_missing, directionLabel(direction)))
            return
        }
        rootView?.showLive(getString(R.string.translating, directionLabel(direction)))
        translation.translate(source, direction) { result ->
            rootView?.showLive(null)
            val conn = currentInputConnection
            when {
                result is TranslationResult.Failure ->
                    rootView?.showStatus(getString(R.string.translation_failed, result.reason))
                conn == null -> Unit
                selected != null && conn.getSelectedText(0)?.toString() != selected ->
                    rootView?.showStatus(getString(R.string.translation_discarded))
                else -> {
                    val text = (result as TranslationResult.Success).text
                    conn.commitText(text, 1)
                    showTranslated(source, text, direction.target)
                }
            }
        }
    }

    private fun clipboardText(): String? {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    /** Absolute cursor position from the field's own text, or -1 (then typing is never interrupted). */
    private fun cursorPosition(): Int {
        val extracted = currentInputConnection?.getExtractedText(ExtractedTextRequest(), 0) ?: return -1
        return if (extracted.selectionEnd >= 0) extracted.startOffset + extracted.selectionEnd else -1
    }

    private fun setTranslateEnabled(enabled: Boolean, direction: TranslationDirection) {
        pendingTranslateState = enabled to direction
        scope.launch { repository.update { it.copy(translateEnabled = enabled, translationDirection = direction) } }
        settings = settings.copy(translateEnabled = enabled, translationDirection = direction)
    }

    // ------------------------------------------------------------------ keys

    override fun onKey(key: Key) {
        if (sendToComputer(key, null)) return
        handler.removeCallbacks(firePendingShortcut)
        execute(controller.onKey(key))
        if (controller.pendingModifierShortcut != null) {
            handler.postDelayed(firePendingShortcut, KeyboardController.PENDING_SHORTCUT_DELAY_MS)
        }
        render()
    }

    override fun onAlternative(text: String) {
        if (sendToComputer(null, text)) return
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
                EditorCommand.TranslateSelectionOrClipboard -> onTranslateClipboard()
                EditorCommand.HideKeyboard -> requestHideSelf(0)
                EditorCommand.OpenSettings -> onToolbarSettings()
                is EditorCommand.CommitText ->
                    if (translateActive) {
                        composer.append(command.text)
                        if (settings.translationAuto && endsSentence(command.text)) startTranslation() else segmentChanged()
                    } else {
                        learnBeforeSeparator(command.text)
                        editor.execute(command)
                    }
                EditorCommand.DeleteBackward ->
                    if (translateActive && composer.backspace()) {
                        segmentChanged()
                    } else {
                        editor.execute(command)
                    }
                is EditorCommand.Enter ->
                    if (translateActive && !composer.isEmpty && !command.forceNewline) {
                        startTranslation()
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
        val ic = currentInputConnection
        if (bluetoothSending && bluetoothConnected) {
            root.showSuggestions(emptyList())
            root.showEcho("→ ${Bluetooth.deviceName.orEmpty()}: ${sentText.toString().takeLast(ECHO_MAX)}")
            return
        }
        val before = ic?.getTextBeforeCursor(WORD_CONTEXT, 0) ?: ""
        // Passwords are never repeated on screen.
        root.showEcho(if (currentField.isSensitive) null else echoOf(before))
        val show = settings.showSuggestions && currentField.allowsLearning && !translateActive &&
            speech.state == SpeechController.State.IDLE
        if (!show || ic == null || !ic.getSelectedText(0).isNullOrEmpty()) {
            root.showSuggestions(emptyList())
            return
        }
        val after = ic.getTextAfterCursor(1, 0) ?: ""
        val prefix = if (WordRules.continuesWord(after)) "" else WordRules.trailingWord(before)
        root.showSuggestions(if (prefix.isEmpty()) emptyList() else dictionary.dictionary.suggestionsWithBase(controller.language, prefix))
    }

    /** Last line before the cursor, shortened from the start. */
    private fun echoOf(before: CharSequence): String? {
        val line = before.toString().substringAfterLast('\n').trim()
        if (line.isEmpty()) return null
        return if (line.length > ECHO_MAX) "…" + line.takeLast(ECHO_MAX) else line
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

    // ------------------------------------------------------------------ Bluetooth keyboard

    private val bluetoothConnected: Boolean get() = Bluetooth.isConnected

    /**
     * In Bluetooth mode, sends [key] (or the long-press option [alternative]) to the computer and returns
     * true. Keys that only change this keyboard (Shift, Ctrl, layers, language ...) still work locally.
     */
    private fun sendToComputer(key: Key?, alternative: String?): Boolean {
        if (!bluetoothSending) return false
        val special = (key?.action as? KeyAction.Special)?.key
        if (special != null && special in LOCAL_KEYS) return false
        val mods = controller.modifiers
        val language = controller.language
        val shift = mods.isActive(ModifierKey.SHIFT)
        val textAction = key?.action as? KeyAction.Text
        val output = alternative?.let {
            if (it.length == 1 && it[0].isLetter() && shift != mods.capsLock) it.uppercase(language.locale) else it
        } ?: textAction?.let { KeyLabels.output(it, shift, mods.capsLock, language) }
        val isLetter = output != null && output.length == 1 && output[0].isLetter()
        val keyMods = KeyMods(
            // Caps Lock lives on the phone only, so letters carry their own Shift.
            shift = if (isLetter) shift != mods.capsLock else shift,
            ctrl = mods.isActive(ModifierKey.CTRL),
            alt = mods.isActive(ModifierKey.ALT),
            meta = mods.isActive(ModifierKey.META),
        )
        val stroke = when {
            key != null -> HidKeys.forKey(key, output, keyMods, language)
            output != null -> HidKeys.forText(output, language)
            else -> null
        }
        // Let the phone keyboard update itself (one-shot Shift used up, Win+Space switches the labels too),
        // but nothing is typed into the phone's field.
        handler.removeCallbacks(firePendingShortcut)
        val commands = if (key != null) controller.onKey(key) else controller.onAlternative(alternative.orEmpty())
        // A shortcut that inserts text (Excel formulas, Vim commands ...) is typed on the computer letter by letter.
        val shortcutText = (commands.singleOrNull() as? EditorCommand.CommitText)?.text
            ?.takeIf { (keyMods.ctrl || keyMods.alt || keyMods.meta) && it != output }
        when {
            shortcutText != null -> {
                val strokes = shortcutText.map { HidKeys.forText(it.toString(), language) }
                val missing = shortcutText.filterIndexed { i, _ -> strokes[i] == null }
                if (missing.isNotEmpty()) {
                    rootView?.showStatus(getString(R.string.bluetooth_cannot_send, missing.take(3)))
                } else if (!strokes.all { Bluetooth.send(it!!) }) {
                    rootView?.showStatus(getString(R.string.bluetooth_not_connected))
                } else {
                    shortcutText.forEach { rememberSent(null, it.toString(), KeyMods()) }
                }
            }
            stroke == null -> rootView?.showStatus(getString(R.string.bluetooth_cannot_send, output ?: "?"))
            !Bluetooth.send(stroke) -> rootView?.showStatus(getString(R.string.bluetooth_not_connected))
            else -> rememberSent(special, output, keyMods)
        }
        updateSuggestions()
        render()
        return true
    }

    private fun rememberSent(special: SpecialKey?, output: String?, mods: KeyMods) {
        if (mods.ctrl || mods.alt || mods.meta) return
        when (special) {
            SpecialKey.BACKSPACE -> if (sentText.isNotEmpty()) sentText.setLength(sentText.length - 1)
            SpecialKey.SPACE -> sentText.append(' ')
            SpecialKey.ENTER -> sentText.setLength(0)
            null -> output?.let { sentText.append(it.filter { c -> c >= ' ' }) }
            else -> Unit
        }
        if (sentText.length > SENT_TEXT_MAX) sentText.delete(0, sentText.length - SENT_TEXT_MAX)
    }

    override fun onToolbarBluetooth() {
        if (bluetoothSending) {
            bluetoothSending = false
            rootView?.showStatus(getString(R.string.bluetooth_typing_here))
        } else if (!bluetoothConnected) {
            Bluetooth.start(this)
            rootView?.showStatus(getString(R.string.bluetooth_not_connected), CHECK_WARNING_MS) { openSettings("bluetooth") }
        } else {
            bluetoothSending = true
            sentText.setLength(0)
            leaveSegment()
            rootView?.showStatus(getString(R.string.bluetooth_typing_there, Bluetooth.deviceName ?: ""))
        }
        updateSuggestions()
        render()
    }

    // ------------------------------------------------------------------ toolbar

    /** "Aa Text" chip: plain typing (turns translation off). */
    override fun onModeText() {
        if (!settings.translateEnabled) return
        leaveSegment()
        setTranslateEnabled(false, settings.translationDirection)
        updateSuggestions()
        render()
    }

    /**
     * Translate chip. Off → on: from the keyboard language to the last target. On → the next target
     * language (Укр→Pol → Укр→Eng → Укр→Pol). The source always follows the keyboard language.
     */
    override fun onModeTranslate() {
        if (!translateOffered) {
            rootView?.showStatus(getString(R.string.translate_not_here))
            return
        }
        val source = controller.language
        val current = settings.translationDirection
        val direction = if (settings.translateEnabled) {
            val others = Language.entries.filter { it != source }
            // Next target in order, preferring one whose model is installed.
            val ordered = others.dropWhile { it != current.target }.drop(1) + others.takeWhile { it != current.target } +
                listOfNotNull(current.target.takeIf { it != source })
            val candidates = ordered.mapNotNull { TranslationDirection.of(source, it) }
            candidates.firstOrNull { translation.isInstalled(it) } ?: candidates.first()
        } else {
            TranslationDirection.forSource(source, current.target, current.source)
        }
        if (!translation.isInstalled(direction)) {
            rootView?.showStatus(getString(R.string.translation_model_missing, directionLabel(direction)), CHECK_WARNING_MS) {
                openSettings("models")
            }
            return
        }
        leaveSegment()
        setTranslateEnabled(true, direction)
        translation.preload(direction)
        rootView?.showStatus(
            getString(if (settings.translationAuto) R.string.translate_on_auto else R.string.translate_on, directionLabel(direction)),
            DONE_MESSAGE_MS,
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
        openSettings(null)
    }

    private fun openSettings(page: String?) {
        val intent = Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (page != null) intent.putExtra(SettingsActivity.EXTRA_PAGE, page)
        startActivity(intent)
    }

    // ------------------------------------------------------------------ voice host

    override fun textBeforeCursor(): CharSequence = currentInputConnection?.getTextBeforeCursor(WORD_CONTEXT, 0) ?: ""

    override fun showPartial(text: String) {
        if (translateActive) {
            rootView?.showLive("● $text")
        } else {
            currentInputConnection?.setComposingText(text, 1)
            partialShown = true
        }
    }

    override fun commitSegment(text: String, language: Language) {
        if (translateActive) {
            rootView?.showLive(null)
            composer.append(text)
            startTranslation()
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
        syncTranslateSource(showProblems = true)
        updateSuggestions()
    }

    /** Translation always starts from the keyboard language (Укр→Pol becomes Pol→Укр when writing Polish). */
    private fun syncTranslateSource(showProblems: Boolean) {
        val language = controller.language
        if (!translateActive || settings.translationDirection.source == language) return
        leaveSegment()
        val current = settings.translationDirection
        val direction = TranslationDirection.forSource(language, current.target, current.source)
        setTranslateEnabled(true, direction)
        if (translation.isInstalled(direction)) {
            translation.preload(direction)
        } else if (showProblems) {
            rootView?.showStatus(getString(R.string.translation_model_missing, directionLabel(direction)), CHECK_WARNING_MS) {
                openSettings("models")
            }
        }
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
                computerMode = controller.mode == KeyboardMode.COMPUTER,
                translateActive = translateActive,
                translateLabel = directionLabel(settings.translationDirection),
                translateOffered = translateOffered,
                voiceOffered = speech.isOffered(currentField),
                recording = speech.state != SpeechController.State.IDLE,
                bluetooth = when {
                    !bluetoothKeyboardSupported || !settings.bluetoothKeyboard -> KeyboardRootView.BluetoothButton.HIDDEN
                    bluetoothSending && bluetoothConnected -> KeyboardRootView.BluetoothButton.SENDING
                    else -> KeyboardRootView.BluetoothButton.IDLE
                },
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

    // ------------------------------------------------------------------ hooks for the emulator UI tests

    internal val inputRootForTest: KeyboardRootView? get() = rootView
    internal val modeForTest: KeyboardMode get() = controller.mode
    internal val languageForTest: Language get() = controller.language

    internal fun applyThemeForTest(theme: KeyboardTheme) {
        rootView?.applyTheme(theme)
    }

    companion object {
        /** The running keyboard, for instrumentation tests only. */
        @Volatile
        internal var instance: KeyboardService? = null
            private set

        private const val STARTUP_SETTINGS_TIMEOUT_MS = 500L
        private const val COMPUTER_KEY_DP = 40f
        private const val MOBILE_KEY_DP = 52f
        private const val WORD_CONTEXT = 64
        private const val CHECK_WARNING_MS = 8000L
        private const val PREVIEW_DELAY_MS = 350L
        private const val DONE_MESSAGE_MS = 4000L
        private const val SENT_TEXT_MAX = 60
        private const val ECHO_MAX = 48

        /** Keys that change only this keyboard, also in Bluetooth mode. */
        private val LOCAL_KEYS = setOf(
            SpecialKey.SHIFT, SpecialKey.CAPS_LOCK, SpecialKey.CTRL, SpecialKey.ALT, SpecialKey.META,
            SpecialKey.LANGUAGE, SpecialKey.MODE, SpecialKey.LAYER_LETTERS, SpecialKey.LAYER_SYMBOLS,
            SpecialKey.LAYER_SYMBOLS_MORE, SpecialKey.LAYER_FUNCTION, SpecialKey.VOICE, SpecialKey.SETTINGS,
            SpecialKey.HIDE,
        )
    }
}
