package com.offlinekeyboard.keyboard

import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import com.offlinekeyboard.R
import com.offlinekeyboard.bluetooth.Bluetooth
import com.offlinekeyboard.core.HidStroke
import kotlin.math.roundToInt
import com.offlinekeyboard.bluetooth.bluetoothKeyboardSupported
import com.offlinekeyboard.core.HidKeys
import com.offlinekeyboard.core.KeyLabels
import com.offlinekeyboard.core.KeyboardController
import com.offlinekeyboard.core.LabelContext
import com.offlinekeyboard.core.LatchState
import com.offlinekeyboard.core.ModifierKey
import com.offlinekeyboard.core.dictionary.AutoCorrect
import com.offlinekeyboard.core.dictionary.BaseDictionary
import com.offlinekeyboard.core.dictionary.NextWords
import com.offlinekeyboard.core.dictionary.WordRules
import com.offlinekeyboard.core.input.EditorCommand
import com.offlinekeyboard.core.input.EnterLabel
import com.offlinekeyboard.core.input.FieldInfo
import com.offlinekeyboard.core.input.KeyMods
import com.offlinekeyboard.core.layout.EmojiSet
import com.offlinekeyboard.core.layout.EmojiUsage
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.LayoutProvider
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
    StickerPanel.Listener,
    ClipboardPanel.Listener,
    RemotePanel.Listener,
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
        runCatching { getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipboardWatcher) }
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
        // The base dictionaries are large (30 000 words plus the known forms): read them off the main
        // thread now, so the first suggestion or correction does not wait for them.
        val languages = settings.enabledLanguages.toList()
        scope.launch(Dispatchers.Default) {
            for (language in languages) {
                BaseDictionary.isKnown(language, "")
                BaseDictionary.suggestions(language, "a", 1)
            }
        }
        controller.shortcuts = settings.shortcuts
        controller.listener = this
        emojiUsage = EmojiUsage.decode(emojiPrefs().getString(EMOJI_USAGE_KEY, null))
        refreshEmojiPages()
        translation.accurate = true
        scope.launch {
            repository.settings.collect { applySettings(it) }
        }
        if (settings.bluetoothKeyboard) Bluetooth.start(this)
        // Loaded now, not with the first key: SoundPool reads the sounds in the background, and before that
        // the first letters stayed silent or came late.
        keySounds = runCatching { KeySounds(this) }.getOrNull()
        scope.launch {
            Bluetooth.status.collect {
                // A switch to another device: it is connected now, so typing goes there.
                val target = switchingTo
                if (target != null && Bluetooth.isConnected && Bluetooth.hostAddress == target) {
                    switchingTo = null
                    handler.removeCallbacks(switchTimeout)
                    bluetoothSending = true
                    sentText.setLength(0)
                    leaveSegment()
                    rootView?.showStatus(getString(R.string.bluetooth_typing_there, Bluetooth.deviceName ?: "", controller.language.code))
                }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurListener?.let { l ->
            runCatching { getSystemService(WindowManager::class.java)?.removeCrossWindowBlurEnabledListener(l) }
        }
        runCatching { getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipboardWatcher) }
        speech.cancel()
        speechEngine.release()
        keySounds?.release()
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
        if (old.ownEmoji != new.ownEmoji) refreshEmojiPages()
        // Choosing a mode in the settings switches to it now (also while the last mode is remembered).
        if (old.startMode != new.startMode && controller.mode != new.startMode) controller.setMode(new.startMode)
        // Always the best the model can do (several candidates weighed): a weaker phone takes the smaller model.
        translation.accurate = true
        if (new.bluetoothKeyboard != old.bluetoothKeyboard) {
            if (new.bluetoothKeyboard) Bluetooth.start(this) else bluetoothSending = false
        }
        if (old.themeMode != new.themeMode || old.keyBrightness != new.keyBrightness || old.glassSeeThrough != new.glassSeeThrough) {
            updateBlur()
            rootView?.applyTheme(currentTheme())
            lightBars(currentTheme())
        }
        if (!new.translateEnabled && old.translateEnabled) leaveSegment()
        // The translate button taken away in the settings while translating: back to plain typing.
        if (!new.translateButton && new.translateEnabled) setTranslateEnabled(false, new.translationDirection)
        if (!new.voiceButton && old.voiceButton && speech.state != SpeechController.State.IDLE) speech.cancel()
        updateSuggestions()
        render()
    }

    override fun onCreateInputView(): View {
        // As smooth as the owner set the phone (90/120 Hz where allowed).
        RefreshRate.apply(window?.window, this)
        val root = KeyboardRootView(this)
        root.keyboardView.listener = this
        root.toolbarListener = this
        root.stickerPanel.listener = this
        root.clipboardPanel.listener = this
        root.remotePanel.listener = this
        root.applyTheme(currentTheme())
        rootView = root
        lightBars(currentTheme())
        window.window?.let {
            com.offlinekeyboard.settings.preferHighestRefreshRate(it)
            // Lay the keyboard out under the camera cutout too (landscape): its black background fills the
            // side instead of the app showing through a gap; the keys are padded clear of the cutout.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.attributes = it.attributes.apply {
                    layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
        }
        // The window's size decides whether the blur fits it (see updateBlur).
        root.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) updateBlur()
        }
        updateBlur()
        render()
        return root
    }

    /** On a light keyboard the system's buttons below the keys (back, the keyboard switch) are drawn dark. */
    private fun lightBars(theme: KeyboardTheme) {
        val w = window.window ?: return
        runCatching { androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightNavigationBars = !theme.dark }
    }

    private fun currentTheme(): KeyboardTheme {
        // Glass lies over the app only blurred: with «see-through glass» on and the system blurring behind the
        // keyboard. Otherwise (off, or a phone that cannot blur) solid, the same in every app: the app sharp
        // behind the letters reads badly, and most apps leave only their plain background there anyway.
        val theme = KeyboardTheme.resolve(settings.themeMode, settings.keyBrightness)
        return if (blurring) theme.overBlur(blurred = true) else theme
    }

    /** Whether the system blurs the app behind the keyboard now (glass, Android 12+, where the phone can). */
    private var blurring = false
    /** Whether the keyboard window's own background was made see-through (once, for the glass). */
    private var windowSeeThrough = false
    private var blurListener: java.util.function.Consumer<Boolean>? = null
    private val blurSpot = IntArray(2)

    /**
     * Glass lies over the app, blurred: turns the blur behind the keyboard window on for the glass styles where
     * the phone allows it (cross-window blur; battery saver can turn it off, and back on), off otherwise. The
     * system blurs the whole window, so only while the window is no taller than the keyboard itself.
     */
    private fun updateBlur() {
        val w = window.window ?: return
        // Glass always shows the app through, blurred or not: the window itself must be see-through, or the
        // half-transparent glass lies on the window's default black (phones that do not allow blur, Android < 12).
        if (settings.themeMode.glass && !windowSeeThrough) {
            windowSeeThrough = true
            runCatching { w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)) }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        if (blurListener == null) {
            val listener = java.util.function.Consumer<Boolean> { handler.post { updateBlur() } }
            blurListener = listener
            runCatching { wm.addCrossWindowBlurEnabledListener(mainExecutor, listener) }
        }
        val root = rootView
        val decor = w.peekDecorView()
        // Only once both are laid out: a window taller than the keys would blur the app above them too.
        // What matters is the room above the keys (the system would blur the app there too); room below them
        // (the system buttons' strip, Android 15+) is the keyboard's own.
        val above = if (root != null && root.height > 0) {
            root.getLocationInWindow(blurSpot)
            blurSpot[1]
        } else -1
        val fits = decor != null && above in 0..(4 * resources.displayMetrics.density).toInt()
        val allowed = runCatching { wm.isCrossWindowBlurEnabled }.getOrDefault(false)
        val want = settings.themeMode.glass && settings.glassSeeThrough && allowed && fits
        if (settings.themeMode.glass) {
            android.util.Log.i("OffKeyBlur", "window=${decor?.height} keys=${root?.height} above=$above allowed=$allowed blur=$want")
            // For the settings: whether the glass is over the blurred app now, and if not, why.
            if (above >= 0) {
                val state = when {
                    want -> BlurState.ON
                    !allowed -> BlurState.NOT_ALLOWED
                    else -> BlurState.WINDOW
                }
                BlurState.save(this, state, above)
            }
        }
        if (want == blurring) return
        blurring = want
        runCatching {
            w.setBackgroundBlurRadius(if (want) (BLUR_DP * resources.displayMetrics.density).toInt() else 0)
        }
        rootView?.applyTheme(currentTheme())
    }

    /**
     * The fading band at the top of the keyboard is not part of the keyboard for the app: the app is laid
     * out under it (so it shows through the fade) and taps there reach the app.
     */
    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        val root = rootView ?: return
        if (!root.isShown || isFullscreenMode) return
        val band = root.fadeHeight
        val top = outInsets.contentTopInsets + band
        // The compact modes row lies over the app: the app's room ends below it (it neither shrinks nor jumps
        // when the row comes and goes), and touches on the row still come to the keyboard.
        val over = root.overlayHeight
        outInsets.contentTopInsets = top + over
        outInsets.visibleTopInsets = top + over
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, top, root.width.coerceAtLeast(1), top + root.height)
    }

    // Never take over the screen with the extract-text window: typing must happen in the app's own field.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composer.abandon()
        softSpace = false
        partialShown = false
        currentField = FieldInfo(attribute?.inputType ?: 0, attribute?.imeOptions ?: 0)
        // The keyboard's own settings (the test field, glossary, stickers): typing there to try keys is
        // not writing, so nothing there is learned.
        ownField = attribute?.packageName == packageName
        editor.field = currentField
        controller.rawKeyField = currentField.isRawKeyField
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) controller.onStartField(currentField.isNumeric, currentField.isPhone)
        carriageColumn = -1
        lastShown = null
        if (settings.learnTouch) touchModel.refresh()
        keySounds?.voice = KeySounds.voiceOf(settings.themeMode)
        if (soundStyle && settings.keySound) keySounds?.warm()
        selStart = info?.let { minOf(it.initialSelStart, it.initialSelEnd) } ?: -1
        selEnd = info?.let { maxOf(it.initialSelStart, it.initialSelEnd) } ?: -1
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
        rootView?.showStickers(false)
        rootView?.showClipboard(false)
        rootView?.collapseModes()
        lastCorrection = null
        handler.removeCallbacks(firePendingShortcut)
        dictionary.saveNow()
        if (settings.learnTouch) touchModel.save()
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
        selStart = minOf(newSelStart, newSelEnd)
        selEnd = maxOf(newSelStart, newSelEnd)
        if (!composer.isEmpty && !composer.isOwnCursorUpdate(newSelStart, newSelEnd, candidatesStart, candidatesEnd)) {
            // The user moved the cursor away from the segment being translated.
            leaveSegment()
        }
        updateAutoShift()
        updateSuggestions()
        render()
    }

    /** Returns true when the Shift state changed. */
    /**
     * "word?" or "word!" with no space yet: smart punctuation puts the space in when the next letter is
     * typed, so that letter starts a new sentence and gets its capital already.
     */
    private fun sentenceEndCapsMode(ic: android.view.inputmethod.InputConnection): Int {
        if (!(assist && settings.smartPunctuation) || controller.mode != KeyboardMode.MOBILE || !currentField.allowsCorrection) return 0
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: return 0
        if (before.length < 2 || before[1] !in "?!") return 0
        if (!before[0].isLetter() && before[0] != ')' && before[0] != '»' && before[0] != '"') return 0
        return android.text.TextUtils.getCapsMode("$before ", before.length + 1, currentField.inputType)
    }

    private fun updateAutoShift(): Boolean {
        val before = controller.modifiers.state(ModifierKey.SHIFT)
        val capsMode = if (settings.autoCapitalize && !currentField.isRawKeyField) {
            currentInputConnection?.let { ic ->
                ic.getCursorCapsMode(currentField.inputType).takeIf { it != 0 } ?: sentenceEndCapsMode(ic)
            } ?: 0
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
    // A pause translates only finished words: after a space or a mark (a word half written was translated,
    // and its rest later on its own).
    private val pauseRunnable = Runnable {
        if (settings.translationAuto && !composer.isEmpty && !composer.pending && wordFinished(composer.text)) startTranslation()
    }

    private fun wordFinished(text: String) = text.isNotEmpty() && (text.last().isWhitespace() || text.last() in WORD_ENDS)

    /** The part of [text] made of finished words (up to its last space or mark); the word being typed is left out. */
    private fun finishedWords(text: String): String {
        val end = text.indexOfLast { it.isWhitespace() || it in WORD_ENDS }
        return if (end < 0) "" else text.substring(0, end + 1)
    }

    /** After each change of the segment: preview soon, and (automatic mode) insert after a pause. */
    private fun segmentChanged() {
        handler.removeCallbacks(previewRunnable)
        handler.removeCallbacks(pauseRunnable)
        if (composer.isEmpty) {
            rootView?.showLive(null)
            return
        }
        // On the strip: what is being typed, and a space after it the translation of its finished words.
        showTyping(composer.text, lastPreview)
        handler.postDelayed(previewRunnable, PREVIEW_DELAY_MS)
        if (settings.translationAuto && settings.translationPauseMs > 0) {
            handler.postDelayed(pauseRunnable, settings.translationPauseMs.toLong())
        }
    }

    /** The preview of the finished words shown last (kept while the next word is typed). */
    private var lastPreview: String? = null

    /** The strip while translating: the typed source (the typebar strikes after it) and the preview a space on. */
    private fun showTyping(source: String, preview: String?) {
        val code = settings.translationDirection.target.code
        val shown = if (preview.isNullOrBlank()) source else "$source   $code: $preview"
        rootView?.showLive(shown, typedEnd = source.length)
    }

    private fun requestPreview() {
        val typed = composer.text
        // Only finished words: two letters of a word have no translation, and the model makes one up.
        val source = finishedWords(typed)
        if (source.isBlank()) {
            lastPreview = null
            showTyping(typed, null)
            return
        }
        val generation = composer.generation
        val direction = settings.translationDirection
        translation.preview(source, direction) { result ->
            if (generation != composer.generation || composer.pending) return@preview
            if (result is TranslationResult.Success) {
                lastPreview = result.text.trim()
                showTyping(composer.text, lastPreview)
            }
        }
    }

    /** Final translation of the segment; it replaces exactly the typed segment once. */
    private fun startTranslation() {
        handler.removeCallbacks(previewRunnable)
        handler.removeCallbacks(pauseRunnable)
        val (text, generation) = composer.beginTranslation() ?: return
        val direction = settings.translationDirection
        rootView?.showLive(getString(R.string.translating, directionLabel(direction)), read = true)
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
        lastPreview = null
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
        lastTranslation = source to inserted
        val missing = TranslationCheck.missing(source, inserted)
        val message = if (missing.isEmpty()) {
            getString(R.string.translation_done_undo, target.code, inserted.trim())
        } else {
            getString(R.string.translation_check_warning, missing.joinToString(", ") { "«$it»" })
        }
        rootView?.showStatus(message, if (missing.isEmpty()) DONE_MESSAGE_MS else CHECK_WARNING_MS) { onUndoTranslation() }
        updateSuggestions()
    }

    /** The last inserted translation and its source, while it can still be put back. */
    private var lastTranslation: Pair<String, String>? = null

    /** True while the last translation is still right before the cursor (spaces after it allowed). */
    private fun undoAvailable(before: CharSequence): Boolean {
        val inserted = lastTranslation?.second?.trimEnd() ?: return false
        return inserted.isNotEmpty() && before.toString().trimEnd().endsWith(inserted)
    }

    override fun onUndoTranslation() {
        val (source, inserted) = lastTranslation ?: return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(inserted.length + UNDO_SLACK, 0)?.toString().orEmpty()
        val trimmed = before.trimEnd()
        if (!trimmed.endsWith(inserted.trimEnd())) {
            lastTranslation = null
            rootView?.showStatus(getString(R.string.translation_restore_impossible))
            updateSuggestions()
            return
        }
        val trailing = before.length - trimmed.length
        ic.beginBatchEdit()
        ic.deleteSurroundingText(inserted.trimEnd().length + trailing, 0)
        ic.commitText(source + before.substring(trimmed.length), 1)
        ic.endBatchEdit()
        lastTranslation = null
        rootView?.showStatus(getString(R.string.translation_restored))
        updateSuggestions()
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
        // The phone's clipboard, or else the newest text kept in the keyboard's list.
        val source = selected ?: clipboardText()?.takeIf { it.isNotBlank() } ?: ClipboardHistory.items(this).firstOrNull()?.text
        if (source.isNullOrBlank()) {
            rootView?.showStatus(getString(R.string.clipboard_empty))
            return
        }
        // Into the language being written; text already in that language goes to the translation target.
        val writing = if (translateActive) settings.translationDirection.target else controller.language
        val direction = TranslationDirection.detect(source, writing, settings.translationDirection.target)
            ?: settings.translationDirection
        if (!translation.isInstalled(direction)) {
            rootView?.showStatus(getString(R.string.translation_model_missing, directionLabel(direction)), CHECK_WARNING_MS) {
                openSettings("models")
            }
            return
        }
        rootView?.showLive(getString(R.string.translating, directionLabel(direction)), read = true)
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
        // Typing: a note on the strip (the volume set, a mode turned on) gives way to the text at once.
        if (key.action is KeyAction.Text || (key.action as? KeyAction.Special)?.key in NOTE_CLEARING_KEYS) rootView?.dismissNote()
        if (controller.layer == Layer.EMOJI) (key.action as? KeyAction.Text)?.let { recordEmoji(it.text) }
        // The most used page is rebuilt when the panel opens, never under the finger.
        if ((key.action as? KeyAction.Special)?.key == SpecialKey.LAYER_EMOJI && controller.layer != Layer.EMOJI) refreshEmojiPages()
        if (sendToComputer(key, null)) return
        if (undoPunctuation(key)) return
        if (undoCorrection(key)) return
        if (doubleSpacePeriod(key)) return
        handler.removeCallbacks(firePendingShortcut)
        execute(controller.onKey(key))
        if (controller.pendingModifierShortcut != null) {
            handler.postDelayed(firePendingShortcut, KeyboardController.PENDING_SHORTCUT_DELAY_MS)
        }
        render()
    }

    override fun onAlternative(text: String) {
        if (text == com.offlinekeyboard.core.layout.LayoutProvider.EMOJI_OPTION) {
            // The comma's first option opens the emoji panel (never typed, not even to a computer).
            if (controller.layer != Layer.EMOJI) refreshEmojiPages()
            execute(controller.onAlternative(text))
            render()
            return
        }
        if (sendToComputer(null, text)) return
        execute(controller.onAlternative(text))
        render()
    }

    override fun onSpaceSwipe(step: Int) {
        if (!settings.spaceSwipeSwitchesLanguage) return
        swipeWay = if (step > 0) 1 else -1
        controller.nextLanguage(step)
        render()
    }

    override fun onCursorMove(steps: Int) {
        if (steps == 0 || (bluetoothSending && bluetoothConnected)) return
        leaveSegment()
        val ic = currentInputConnection
        if (ic != null && !currentField.isRawKeyField && selStart >= 0 && selEnd >= 0) {
            // From the cursor as the field last reported it (and as moved since): both ways, as far as the
            // text goes, however long it is.
            val target = if (steps > 0) {
                selEnd + (ic.getTextAfterCursor(steps, 0)?.length ?: 0)
            } else {
                selStart - (ic.getTextBeforeCursor(-steps, 0)?.length ?: 0)
            }
            selStart = target
            selEnd = target
            ic.setSelection(target, target)
            return
        }
        val direction = if (steps < 0) com.offlinekeyboard.core.input.Direction.LEFT else com.offlinekeyboard.core.input.Direction.RIGHT
        repeat(kotlin.math.abs(steps)) { editor.execute(com.offlinekeyboard.core.input.EditorCommand.Move(direction)) }
    }

    override fun repeatHasEffect(key: Key): Boolean {
        if (bluetoothSending && bluetoothConnected) return true
        if (translateActive && !composer.isEmpty) return true
        val ic = currentInputConnection ?: return false
        if (!ic.getSelectedText(0).isNullOrEmpty()) return true
        return when ((key.action as? KeyAction.Special)?.key) {
            SpecialKey.BACKSPACE, SpecialKey.LEFT, SpecialKey.HOME -> !ic.getTextBeforeCursor(1, 0).isNullOrEmpty()
            SpecialKey.DELETE, SpecialKey.RIGHT, SpecialKey.END -> !ic.getTextAfterCursor(1, 0).isNullOrEmpty()
            else -> true
        }
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
                EditorCommand.OpenStickers -> showStickers()
                is EditorCommand.CommitText ->
                    if (translateActive) {
                        // The word goes on right after its translation: it is the same word, translated whole.
                        val letter = command.text.firstOrNull()?.isLetterOrDigit() == true
                        lastTranslation?.let { (source, inserted) ->
                            if (letter && composer.isEmpty && composer.reopen(source, inserted)) lastTranslation = null
                        }
                        composer.append(command.text)
                        if (settings.translationAuto && endsSentence(command.text)) startTranslation() else segmentChanged()
                    } else if (!punctuate(command.text)) {
                        autoCorrectBefore(command.text)
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

    /**
     * Suggestions, corrections, smart punctuation and learning: on in the settings («Підказки й виправлення»)
     * and not paused from the keyboard (the Text button).
     */
    private val assist: Boolean get() = settings.assistEnabled && !settings.assistPaused

    private val learningAllowed: Boolean get() = assist && settings.learnWords && currentField.allowsLearning && !ownField &&
        // Search boxes report words while they are still being typed (and are no place to learn from).
        currentField.imeOptions and EditorInfo.IME_MASK_ACTION != EditorInfo.IME_ACTION_SEARCH

    /** The field is in the keyboard's own app (see onStartInput). */
    private var ownField = false

    /** When a space or punctuation follows a word, that word goes into the user dictionary. */
    private fun learnBeforeSeparator(text: String) {
        if (!learningAllowed || text.isEmpty()) return
        val first = text[0]
        if (!(first.isWhitespace() || first in ".,!?;:)…")) return
        val before = currentInputConnection?.getTextBeforeCursor(WORD_CONTEXT, 0) ?: return
        val word = WordRules.trailingWord(before)
        // A common word cut short is not a new word: it is not kept (nor paired with the next one).
        if (word.isNotEmpty() && BaseDictionary.isFragment(controller.language, word)) return
        if (word.isNotEmpty() && dictionary.dictionary.learn(controller.language, word, System.currentTimeMillis())) {
            dictionary.changed()
        }
        if (word.isNotEmpty()) learnPair(before.subSequence(0, before.length - word.length), word)
    }

    /**
     * The word just before the cursor when the cursor stands after "word " (one or more spaces right after a
     * letter), or null: after a full stop or a line break there is no previous word to predict from.
     */
    private fun previousWord(before: CharSequence): String? {
        val trimmed = before.trimEnd(' ')
        if (trimmed.length == before.length || trimmed.isEmpty() || !trimmed.last().isLetter()) return null
        return WordRules.trailingWord(trimmed).ifEmpty { null }
    }

    /** Remembers that [word] followed the word before it in [before] (the text before [word]). */
    private fun learnPair(before: CharSequence, word: String) {
        if (!learningAllowed) return
        val previous = previousWord(before) ?: return
        dictionary.pairs.learn(controller.language, previous, word)
        // And after the two words before it ("я тебе" → "люблю"), kept with the pairs.
        if (assist && settings.longContext) {
            wordBefore(before, previous)?.let { dictionary.pairs.learn(controller.language, "$it $previous", word) }
        }
        dictionary.changed()
    }

    /** The word before [previous], the last word of [before] (null after punctuation or at the start). */
    private fun wordBefore(before: CharSequence, previous: String): String? {
        val trimmed = before.trimEnd(' ')
        if (trimmed.length < previous.length) return null
        return previousWord(trimmed.subSequence(0, trimmed.length - previous.length))
    }

    private fun updateSuggestions() {
        val root = rootView ?: return
        val ic = currentInputConnection
        if (bluetoothSending && bluetoothConnected) {
            root.showPredictions(emptyList())
            root.showSuggestions(emptyList())
            root.showUndo(false)
            root.showEcho("→ ${Bluetooth.deviceName.orEmpty()}: ${sentText.takeLast(ECHO_CONTEXT)}")
            return
        }
        val before = ic?.getTextBeforeCursor(ECHO_CONTEXT, 0) ?: ""
        carriage(before)
        // The whole message (passwords are never repeated on screen): what is before the cursor, and what
        // is after it, dimmer, so every word of it can be reached from the strip.
        // Passwords are never repeated on screen; and some want the text only in the app's field.
        if (currentField.isSensitive || !settings.showTypedText) {
            root.showEcho(null)
        } else {
            val shownAfter = (ic?.getTextAfterCursor(ECHO_AFTER, 0) ?: "").toString().replace('\n', LINE_MARK).trimEnd()
            var shownBefore = before.toString().replace('\n', LINE_MARK).trimStart()
            // At the end of the text the spaces just typed are not shown; touched words count them back.
            echoTrimmed = 0
            if (shownAfter.isEmpty()) {
                val trimmed = shownBefore.trimEnd()
                echoTrimmed = shownBefore.length - trimmed.length
                shownBefore = trimmed
            }
            root.showEcho((shownBefore + shownAfter).ifEmpty { null }, shownBefore.length)
        }
        // A long translation reaches further back than the strip's text: read as much as it needs.
        lastTranslation?.let { (_, inserted) ->
            val back = if (inserted.length + UNDO_SLACK > before.length) ic?.getTextBeforeCursor(inserted.length + UNDO_SLACK, 0) ?: before else before
            if (!undoAvailable(back)) lastTranslation = null
        }
        root.showUndo(lastTranslation != null)
        val show = assist && settings.showSuggestions && currentField.allowsLearning && !translateActive &&
            speech.state == SpeechController.State.IDLE
        if (!show || ic == null || !ic.getSelectedText(0).isNullOrEmpty()) {
            root.showSuggestions(emptyList())
            root.showPredictions(emptyList())
            return
        }
        val after = ic.getTextAfterCursor(1, 0) ?: ""
        val prefix = if (WordRules.continuesWord(after)) "" else WordRules.trailingWord(before.takeLast(WORD_CONTEXT))
        if (prefix.isNotEmpty()) {
            root.showPredictions(emptyList())
            root.showSuggestions(dictionary.dictionary.suggestionsWithBase(controller.language, prefix))
            return
        }
        // After a finished word: the words that usually come next (the user's own pairs first).
        val language = controller.language
        val previous = if (WordRules.continuesWord(after)) null else previousWord(before.takeLast(WORD_CONTEXT))
        val earlier = if (previous != null && assist && settings.longContext) wordBefore(before.takeLast(WORD_CONTEXT), previous) else null
        val next = previous?.let { NextWords.predict(language, it, dictionary.pairs, earlier = earlier) }.orEmpty()
        val capital = controller.modifiers.isActive(com.offlinekeyboard.core.ModifierKey.SHIFT)
        root.showSuggestions(emptyList())
        root.showPredictions(next.map { if (capital) it.replaceFirstChar { c -> c.titlecase(language.locale) } else it })
    }

    override fun onEchoWord(start: Int, end: Int, select: Boolean) {
        val ic = currentInputConnection ?: return
        if (currentField.isSensitive || bluetoothSending || selStart < 0) return
        // The strip shows the text before the selection and after it, one character for one: a word after
        // the cursor counts from the selection's end, one before it from its start (and the spaces at the
        // end of the text the strip leaves out).
        val base = if (start >= 0) selEnd else selStart - echoTrimmed
        val from = base + start
        val to = base + end
        if (from < 0) return
        leaveSegment()
        selStart = if (select) from else to
        selEnd = to
        ic.setSelection(selStart, selEnd)
    }

    override fun onSuggestionPicked(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = WordRules.trailingWord(ic.getTextBeforeCursor(WORD_CONTEXT, 0) ?: "")
        ic.beginBatchEdit()
        if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
        val textBefore = ic.getTextBeforeCursor(WORD_CONTEXT, 0) ?: ""
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        // The space added after the word counts as the first of two quick spaces: one more tap on the space
        // bar makes ". ", as if both spaces had been typed. A mark (, . ! ?) moves before it by itself.
        softSpace = true
        lastPunctuation = null
        if (learningAllowed && dictionary.dictionary.learn(controller.language, word, System.currentTimeMillis())) {
            dictionary.changed()
        }
        learnPair(textBefore, word)
        // Predictions for the word after it right away.
        updateSuggestions()
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

    /**
     * Types [text] on the computer letter by letter, in the computer's current layout (as set for the keyboard
     * language). Letters that layout cannot type are left out and named.
     */
    private fun typeOnComputer(text: String) {
        val language = controller.language
        val missing = StringBuilder()
        for (c in text) {
            val stroke = HidKeys.forText(c.toString(), language)
            if (stroke == null) {
                if (missing.length < 3 && c !in missing) missing.append(c)
                continue
            }
            if (!Bluetooth.send(stroke)) {
                rootView?.showStatus(getString(R.string.bluetooth_not_connected))
                return
            }
            rememberSent(null, c.toString(), KeyMods())
        }
        if (missing.isNotEmpty()) rootView?.showStatus(getString(R.string.bluetooth_cannot_send, missing.toString()))
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

    /** Where in the cycle of chosen devices (and the phone, last) the last long press went. */
    private var switchPosition = -1

    /** The device a switch is connecting to (its address), until it has. */
    private var switchingTo: String? = null
    private val switchTimeout = Runnable {
        val target = switchingTo ?: return@Runnable
        switchingTo = null
        val name = Bluetooth.pairedDevices(this).firstOrNull { it.address == target }?.let(Bluetooth::deviceName) ?: target
        rootView?.showStatus(getString(R.string.bluetooth_switch_failed, name), CHECK_WARNING_MS)
        render()
    }

    /**
     * The Bluetooth button held: on to the next of the devices starred in the settings, round and round (the
     * phone itself is a tap away). The device typed on now is let go first (a phone is a keyboard for one at
     * a time). With one starred device, the press connects to it again.
     */
    override fun onToolbarBluetoothSwitch() {
        if (!bluetoothKeyboardSupported || !settings.bluetoothKeyboard) return
        val hosts = Bluetooth.switchHosts(this)
        if (hosts.isEmpty()) {
            rootView?.showStatus(getString(R.string.bluetooth_switch_none), CHECK_WARNING_MS) { openSettings("bluetooth") }
            return
        }
        val cycle: List<android.bluetooth.BluetoothDevice> = hosts
        // From where typing goes now; while a switch is under way or failed (a device away), from the one
        // last tried, so every long press moves on whether or not the last one answered.
        val current = if (bluetoothSending && bluetoothConnected) Bluetooth.hostAddress else null
        val at = when {
            current != null -> cycle.indexOfFirst { it.address == current }
            switchPosition in cycle.indices -> switchPosition
            else -> cycle.lastIndex
        }
        val nextAt = (at + 1) % cycle.size
        switchPosition = nextAt
        connectTo(cycle[nextAt])
    }

    /** Typing goes to [next]: sent there now when it is connected already, otherwise connected first (the button pulses). */
    private fun connectTo(next: android.bluetooth.BluetoothDevice) {
        handler.removeCallbacks(switchTimeout)
        leaveSegment()
        if (bluetoothConnected && Bluetooth.hostAddress == next.address) {
            // Already connected to it (typing went to the phone): only send there again.
            switchingTo = null
            bluetoothSending = true
            sentText.setLength(0)
            rootView?.showStatus(getString(R.string.bluetooth_typing_there, Bluetooth.deviceName ?: "", controller.language.code))
        } else {
            switchingTo = next.address
            bluetoothSending = false
            Bluetooth.start(this)
            Bluetooth.switchTo(next)
            rootView?.showStatus(getString(R.string.bluetooth_switching, Bluetooth.deviceName(next)), SWITCH_TIMEOUT_MS)
            handler.postDelayed(switchTimeout, SWITCH_TIMEOUT_MS)
        }
        updateSuggestions()
        render()
    }

    override fun onToolbarBluetooth() {
        if (bluetoothSending) {
            bluetoothSending = false
            rootView?.showStatus(getString(R.string.bluetooth_typing_here))
        } else if (!bluetoothConnected) {
            // Not connected: straight to the first device starred for switching, when there is one.
            val first = if (settings.bluetoothKeyboard) Bluetooth.switchHosts(this).firstOrNull() else null
            if (first != null) {
                switchPosition = 0
                connectTo(first)
            } else {
                Bluetooth.start(this)
                rootView?.showStatus(getString(R.string.bluetooth_not_connected), CHECK_WARNING_MS) { openSettings("bluetooth") }
            }
        } else {
            bluetoothSending = true
            sentText.setLength(0)
            leaveSegment()
            rootView?.showStatus(getString(R.string.bluetooth_typing_there, Bluetooth.deviceName ?: "", controller.language.code))
        }
        updateSuggestions()
        render()
    }

    // ------------------------------------------------------------------ remote

    override fun onToolbarRemote() {
        val root = rootView ?: return
        root.showRemote(!root.remoteShown)
    }

    override fun onRemoteMouse(buttons: Int, dx: Int, dy: Int, wheel: Int) {
        if (!Bluetooth.sendMouse(buttons, dx, dy, wheel)) remoteLost()
    }

    override fun onRemoteKey(usage: Int, modifiers: Int) {
        if (!Bluetooth.send(HidStroke(modifiers, usage))) remoteLost()
    }

    override fun onRemoteMedia(usage: Int) {
        if (!Bluetooth.sendMedia(usage)) remoteLost()
    }

    override fun onRemoteKeyboard() {
        rootView?.showRemote(false)
    }

    private fun remoteLost() {
        rootView?.showStatus(getString(R.string.bluetooth_not_connected), CHECK_WARNING_MS) { openSettings("bluetooth") }
    }

    // ------------------------------------------------------------------ sound

    /** The styles that have key sounds (all of them since 1.17.0); the sound button shows in them. */
    private val soundStyle: Boolean get() = true

    override fun onToolbarSound() {
        val on = !settings.keySound
        // Turned on from 0, at a quiet level rather than silent.
        val volume = if (on && settings.keySoundVolume <= 0f) 0.3f else settings.keySoundVolume
        settings = settings.copy(keySound = on, keySoundVolume = volume)
        scope.launch { repository.update { it.copy(keySound = on, keySoundVolume = volume) } }
        if (on) keySounds?.play(KeySounds.Kind.KEY, settings.keySoundVolume)
        rootView?.showStatus(getString(if (on) R.string.sound_on else R.string.sound_off))
        render()
    }

    override fun onSoundVolume(volume: Float, done: Boolean) {
        val v = volume.coerceIn(0f, 1f)
        if (!done) {
            // A click at every step, at the new volume (heard even while sounds are off, to hear the level).
            if (v > 0f) keySounds?.play(KeySounds.Kind.KEY, v)
            return
        }
        // Down to 0 the sounds go off (the button crossed out) and 0 is kept: held again, it starts from there.
        val on = v > 0f
        settings = settings.copy(keySoundVolume = v, keySound = on)
        scope.launch { repository.update { it.copy(keySoundVolume = v, keySound = on) } }
        rootView?.showStatus(if (on) getString(R.string.sound_volume_set, (v * 100).roundToInt()) else getString(R.string.sound_off))
        render()
    }

    // ------------------------------------------------------------------ toolbar

    /** "Aa Text" chip: plain typing (turns translation off). */
    override fun onModeText() {
        if (!settings.translateEnabled) {
            // Already typing plain text: the tap pauses suggestions and corrections, or brings them back.
            if (!settings.assistEnabled) return
            val paused = !settings.assistPaused
            settings = settings.copy(assistPaused = paused)
            scope.launch { repository.update { it.copy(assistPaused = paused) } }
            rootView?.showStatus(getString(if (paused) R.string.assist_paused else R.string.assist_resumed), DONE_MESSAGE_MS)
            updateSuggestions()
            render()
            return
        }
        leaveSegment()
        setTranslateEnabled(false, settings.translationDirection)
        // The translation's note on the strip («Переклад Укр→Pol: …») goes with it, not seconds later.
        rootView?.clearStatus()
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

    // ------------------------------------------------------------------ autocorrect

    /** The last correction (typed word, its fix, the separator after it), while Backspace can undo it. */
    private var lastCorrection: Triple<String, String, String>? = null

    private fun correctionPrefs() = getSharedPreferences(CORRECTION_PREFS, MODE_PRIVATE)

    /** Words the user undid; they are never corrected again. */
    private fun rejectedWords(): Set<String> = correctionPrefs().getStringSet(REJECTED_KEY, emptySet()).orEmpty()

    /** Before a space or punctuation is typed: fixes a one-key slip in the word that just ended. */
    private fun autoCorrectBefore(separator: String) {
        lastCorrection = null
        if (!(assist && settings.autoCorrect) || controller.mode != KeyboardMode.MOBILE || !currentField.allowsCorrection) return
        if (controller.modifiers.anyShortcutModifier) return
        val first = separator.firstOrNull() ?: return
        if (!(first.isWhitespace() || first in ".,!?;:…")) return
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty() || WordRules.continuesWord(ic.getTextAfterCursor(1, 0) ?: "")) return
        val textBefore = ic.getTextBeforeCursor(WORD_CONTEXT, 0) ?: return
        val word = WordRules.trailingWord(textBefore)
        if (word.isEmpty()) return
        val language = controller.language
        val user = dictionary.dictionary
        val base = BaseDictionary.words(language)
        // The user's own words take part in autocorrect: a word added by hand or imported (it starts with a
        // count of 5) or typed often is never "corrected" away, and a slip in it is fixed to it before any
        // base word. Words seen only once or twice are left out: the dictionary also learns typos.
        val own = user.entries(language).filter { it.count >= OWN_WORD_MIN_COUNT }
        val ownKeys = own.mapTo(HashSet()) { it.word.lowercase(language.locale) }
        val trusted = own.filter { it.count >= TRUSTED_WORD_COUNT }.map { it.word }
        val fixed = AutoCorrect.correction(
            language,
            word,
            known = { it in ownKeys || BaseDictionary.isKnown(language, it) },
            candidates = trusted.asSequence() + base.asSequence() + own.asSequence().map { it.word },
            rejected = rejectedWords(),
            // Words that usually follow the previous word win a close call ("добрий деньь" → "день").
            context = previousWord(textBefore.subSequence(0, textBefore.length - word.length))
                ?.let { NextWords.predict(language, it, dictionary.pairs, limit = 8).toSet() }
                .orEmpty(),
        ) ?: return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(fixed, 1)
        ic.endBatchEdit()
        lastCorrection = Triple(word, fixed, separator)
        rootView?.showStatus(getString(R.string.autocorrect_done, word, fixed), CORRECTION_MESSAGE_MS)
    }

    /** Backspace right after a correction puts the typed word back (and remembers it). */
    private fun undoCorrection(key: Key): Boolean {
        val correction = lastCorrection ?: return false
        lastCorrection = null
        if ((key.action as? KeyAction.Special)?.key != SpecialKey.BACKSPACE) return false
        val (typed, fixed, separator) = correction
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(fixed.length + separator.length, 0)?.toString() ?: return false
        if (before != fixed + separator) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(before.length, 0)
        ic.commitText(typed, 1)
        ic.endBatchEdit()
        if (!currentField.isIncognito) {
            correctionPrefs().edit()
                .putStringSet(REJECTED_KEY, rejectedWords() + typed.lowercase(controller.language.locale))
                .apply()
        }
        rootView?.keyboardView?.feedback()
        rootView?.showStatus(getString(R.string.autocorrect_undone, typed))
        updateAutoShift()
        updateSuggestions()
        render()
        return true
    }

    // ------------------------------------------------------------------ punctuation

    /** What the last punctuation rule typed, and what the user had typed instead (for one Backspace). */
    private var lastPunctuation: Pair<String, String>? = null

    /**
     * Applies the small punctuation rules ([Punctuation]) to [text] about to be typed: no space before a comma,
     * a space after it before the next word, none after an opening bracket. Mobile mode, ordinary text only.
     * Returns true when the rule typed it.
     */
    private fun punctuate(text: String): Boolean {
        lastPunctuation = null
        if (!(assist && settings.smartPunctuation) || controller.mode != KeyboardMode.MOBILE) return false
        if (!currentField.allowsCorrection || controller.modifiers.anyShortcutModifier) return false
        val ic = currentInputConnection ?: return false
        if (!ic.getSelectedText(0).isNullOrEmpty()) return false
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return false
        val edit = com.offlinekeyboard.core.input.Punctuation.adjust(before, text) ?: return false
        ic.beginBatchEdit()
        if (edit.delete > 0) ic.deleteSurroundingText(edit.delete, 0)
        if (edit.insert.isNotEmpty()) ic.commitText(edit.insert, 1)
        ic.endBatchEdit()
        if (edit.insert.isNotEmpty()) lastPunctuation = edit.insert to edit.undo
        return true
    }

    /** One Backspace right after a punctuation rule brings back exactly what was typed. */
    private fun undoPunctuation(key: Key): Boolean {
        val last = lastPunctuation ?: return false
        lastPunctuation = null
        if ((key.action as? KeyAction.Special)?.key != SpecialKey.BACKSPACE) return false
        val (inserted, typed) = last
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(inserted.length, 0)?.toString() ?: return false
        if (before != inserted) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(inserted.length, 0)
        ic.commitText(typed, 1)
        ic.endBatchEdit()
        rootView?.keyboardView?.feedback()
        updateAutoShift()
        updateSuggestions()
        render()
        return true
    }

    // ------------------------------------------------------------------ backspace swipe

    override fun onBackspaceSwipe(words: Int, done: Boolean) {
        val ic = currentInputConnection ?: return
        val usable = !currentField.isRawKeyField && !translateActive && !(bluetoothSending && bluetoothConnected)
        if (!usable) return
        val before = ic.getTextBeforeCursor(BACKSPACE_SWIPE_CONTEXT, 0)?.toString().orEmpty()
        val start = wordsStart(before, words)
        val chunk = before.substring(start)
        if (!done) {
            if (chunk.isNotEmpty()) rootView?.showStatus(getString(R.string.backspace_swipe_preview, chunk.trim()), CORRECTION_MESSAGE_MS)
            return
        }
        if (chunk.isEmpty()) return
        lastCorrection = null
        ic.deleteSurroundingText(chunk.length, 0)
        rootView?.clearStatus()
        updateAutoShift()
        updateSuggestions()
        render()
    }

    /** Where the last [words] words (with the spaces after them) start in [text]. */
    private fun wordsStart(text: String, words: Int): Int {
        var i = text.length
        repeat(words) {
            while (i > 0 && text[i - 1].isWhitespace()) i--
            while (i > 0 && !text[i - 1].isWhitespace()) i--
        }
        return i
    }

    // ------------------------------------------------------------------ double space

    private var lastSpaceAt = 0L

    /** A picked suggestion just added its space (see [onSuggestionPicked]); cleared by the next key. */
    private var softSpace = false

    /** Where the finger lands on each letter (used while "learn how I tap" is on). */
    private val touchModel by lazy { TouchModel(this) }

    /** The typewriter's key sounds, made when the keyboard is first drawn. */
    private var keySounds: KeySounds? = null

    /** The language, layer and mode last drawn (null: none yet in this field), and the way of a language swipe. */
    private var lastShown: Triple<Language, Layer, KeyboardMode>? = null
    private var swipeWay: Int? = null

    /** How far a layer is from the letters: going deeper slides to the right, coming back to the left. */
    private fun layerDepth(layer: Layer) = when (layer) {
        Layer.LETTERS -> 0
        Layer.SYMBOLS_MORE -> 2
        else -> 1
    }

    /** The selection in the field (start ≤ end; -1 while unknown), as last reported or as we just set it. */
    private var selStart = -1
    private var selEnd = -1
    /** Spaces at the end of the text left out of the strip (see updateSuggestions). */
    private var echoTrimmed = 0

    /** Where the typewriter's carriage is: characters since the last line break (-1: not known yet). */
    private var carriageColumn = -1
    /** Lines typed on the current sheet (returns, and lines that ran past the margin). */
    private var sheetLines = 0

    /**
     * The typewriter's bell and paper: the bell rings a few characters before the end of a line, as on the
     * machine, and a new sheet goes in every [SHEET_LINES] lines. Only while key sounds are on.
     */
    private fun carriage(before: CharSequence) {
        val column = before.length - (before.lastIndexOf('\n') + 1)
        val last = carriageColumn
        carriageColumn = column
        val sounds = keySounds ?: return
        if (!settings.typewriterBell) return
        val volume = rootView?.keyboardView?.soundVolume ?: 0f
        if (last < 0 || volume <= 0f || currentField.isSensitive) return
        // Typed, not pasted: the carriage moved on by a character or a word.
        val typed = column > last && column - last <= MAX_TYPED_STEP
        if (typed && last % LINE_LENGTH < BELL_COLUMN && column % LINE_LENGTH >= BELL_COLUMN) {
            sounds.play(KeySounds.Kind.BELL, volume)
        }
        val returned = column == 0 && last > 0 && before.endsWith("\n")
        val wrapped = typed && column / LINE_LENGTH > last / LINE_LENGTH
        if (returned || wrapped) {
            sheetLines++
            if (sheetLines >= SHEET_LINES) {
                sheetLines = 0
                sounds.play(KeySounds.Kind.SHEET, volume)
            }
        }
    }

    /**
     * A second space within about a second after "word " turns into ". " (then the next letter is a capital), like
     * on most phone keyboards. Only in mobile mode, in normal text, never in passwords or terminals.
     */
    private fun doubleSpacePeriod(key: Key): Boolean {
        val isSpace = (key.action as? KeyAction.Special)?.key == SpecialKey.SPACE
        val afterPickedWord = softSpace
        softSpace = false
        if (!isSpace) {
            lastSpaceAt = 0L
            return false
        }
        val now = android.os.SystemClock.uptimeMillis()
        val quick = afterPickedWord || now - lastSpaceAt <= DOUBLE_SPACE_MS
        lastSpaceAt = now
        if (!quick || !settings.doubleSpacePeriod || controller.mode != KeyboardMode.MOBILE || translateActive) return false
        if (currentField.isSensitive || currentField.isRawKeyField || controller.modifiers.anyShortcutModifier) return false
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: return false
        if (before.length < 2 || before[1] != ' ' || !before[0].isLetterOrDigit()) return false
        lastSpaceAt = 0L
        ic.beginBatchEdit()
        ic.deleteSurroundingText(1, 0)
        ic.commitText(". ", 1)
        ic.endBatchEdit()
        rootView?.keyboardView?.feedback()
        updateAutoShift()
        updateSuggestions()
        render()
        return true
    }

    // ------------------------------------------------------------------ emoji

    private var emojiUsage = EmojiUsage()

    private fun emojiPrefs() = getSharedPreferences(EMOJI_PREFS, MODE_PRIVATE)

    private fun recordEmoji(text: String) {
        emojiUsage.record(text)
        emojiPrefs().edit().putString(EMOJI_USAGE_KEY, emojiUsage.encode()).apply()
    }

    private fun refreshEmojiPages() {
        controller.emojiPages = EmojiSet.pagesWith(emojiUsage.top(EmojiSet.PAGE_SIZE), EmojiSet.parseOwn(settings.ownEmoji))
    }

    // ------------------------------------------------------------------ clipboard history

    private val clipboardWatcher = ClipboardManager.OnPrimaryClipChangedListener { rememberClip() }

    /** Keeps what was just copied (never a text the app marks sensitive, nor one copied from a password). */
    private fun rememberClip() {
        val manager = getSystemService(ClipboardManager::class.java) ?: return
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return
        val sensitive = clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
        if (sensitive || currentField.isSensitive) return
        val text = (0 until clip.itemCount).joinToString("\n") { clip.getItemAt(it).coerceToText(this)?.toString().orEmpty() }
        ClipboardHistory.add(this, text)
        if (rootView?.clipboardShown == true) rootView?.clipboardPanel?.show(ClipboardHistory.items(this))
    }

    override fun onClipboardChip() {
        val root = rootView ?: return
        if (root.clipboardShown) {
            root.showClipboard(false)
            return
        }
        root.clipboardPanel.show(ClipboardHistory.items(this))
        root.showClipboard(true)
    }

    override fun onClipPicked(text: String) {
        rootView?.keyboardView?.panelTap()
        leaveSegment()
        // Typing goes to a computer: the copied text is typed there, as everything else is.
        if (bluetoothSending) typeOnComputer(text) else currentInputConnection?.commitText(text, 1)
        rootView?.showClipboard(false)
    }

    override fun onClipLongPressed(text: String) {
        val pinned = ClipboardHistory.togglePin(this, text)
        rootView?.showStatus(getString(if (pinned) R.string.clipboard_pinned else R.string.clipboard_unpinned))
        rootView?.clipboardPanel?.show(ClipboardHistory.items(this))
    }

    override fun onClipboardClosed() {
        rootView?.showClipboard(false)
    }

    override fun onClipboardTranslate() {
        rootView?.showClipboard(false)
        onTranslateClipboard()
    }

    override fun onClipboardClear() {
        ClipboardHistory.clear(this)
        rootView?.clipboardPanel?.show(ClipboardHistory.items(this))
    }

    /** The last text swiped off the clipboard list, to bring back. */
    private var deletedClip: ClipboardHistory.Clip? = null

    override fun onClipDeleted(text: String) {
        deletedClip = ClipboardHistory.remove(this, text)
        val panel = rootView?.clipboardPanel ?: return
        panel.show(ClipboardHistory.items(this))
        panel.say(getString(R.string.clipboard_deleted)) { onClipRestore() }
    }

    override fun onClipRestore() {
        val clip = deletedClip ?: return
        deletedClip = null
        ClipboardHistory.restore(this, clip)
        rootView?.clipboardPanel?.show(ClipboardHistory.items(this))
    }

    // ------------------------------------------------------------------ stickers

    private fun showStickers() {
        if (bluetoothSending) {
            rootView?.showStatus(getString(R.string.stickers_not_over_bluetooth))
            return
        }
        if (Stickers.mimeFor(currentInputEditorInfo) == null) {
            // Still opened, so the stickers can be seen; picking one explains why nothing is sent.
            rootView?.showStatus(getString(R.string.stickers_not_supported), CHECK_WARNING_MS)
        }
        rootView?.showStickers(true)
    }

    override fun onStickerPicked(name: String) {
        rootView?.keyboardView?.feedback()
        val sent = runCatching {
            Stickers.send(this, currentInputConnection, currentInputEditorInfo, name, getString(R.string.sticker_description))
        }.getOrDefault(false)
        if (!sent) rootView?.showStatus(getString(R.string.stickers_not_supported), CHECK_WARNING_MS)
    }

    override fun onStickerLongPressed(name: String) {
        val favorite = Stickers.toggleFavorite(this, name)
        rootView?.showStatus(getString(if (favorite) R.string.sticker_favorite_added else R.string.sticker_favorite_removed))
        rootView?.stickerPanel?.reload()
    }

    override fun onAddStickers() {
        rootView?.showStickers(false)
        openSettings("stickers")
    }

    override fun onStickerCharacterPicked(character: StickerCharacter) {
        settings = settings.copy(stickerCharacter = character)
        rootView?.stickerPanel?.character = character
        scope.launch { repository.update { it.copy(stickerCharacter = character) } }
    }

    override fun onStickersClosed(backToEmoji: Boolean) {
        rootView?.showStickers(false)
        if (!backToEmoji) {
            controller.onKey(Key(KeyAction.Special(SpecialKey.LAYER_LETTERS)))
            render()
        }
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
            read = true,
        )
        updateSuggestions()
        render()
    }

    override fun showMessage(resId: Int, detail: String?) {
        val text = getString(resId)
        val shown = if (detail.isNullOrEmpty()) text else "$text ($detail)"
        // No voice model yet: a tap on the message opens the models page to download it.
        if (resId == R.string.voice_model_missing) {
            rootView?.showStatus(shown, CHECK_WARNING_MS) { openSettings("models") }
        } else {
            rootView?.showStatus(shown)
        }
    }

    override fun requestMicPermission() {
        rootView?.showStatus(getString(R.string.voice_permission_needed))
        startActivity(Intent(this, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ------------------------------------------------------------------ controller callbacks

    override fun onLanguageChanged(language: Language) {
        scope.launch { repository.update { it.copy(lastLanguage = language) } }
        // Typing goes to a device: its own language turns the keys into letters, so it is said which to set.
        if (bluetoothSending) rootView?.showStatus(getString(R.string.bluetooth_device_language, language.code))
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

    private var renderedLayer: Layer? = null
    private var renderedLanguage: Language? = null

    private fun render() {
        val root = rootView ?: return
        // Back on the letters (from digits, symbols, emoji) or in another language: the sentence-start capital
        // is worked out again from the text, so ". " then ?123 then ABC still gives a capital.
        if (controller.layer != renderedLayer || controller.language != renderedLanguage) {
            renderedLayer = controller.layer
            renderedLanguage = controller.language
            updateAutoShift()
        }
        val mods = controller.modifiers
        val enterLabel = if (translateActive && !composer.isEmpty) EnterLabel.TRANSLATE else currentField.enterLabel
        val labels = LabelContext(
            shift = mods.isActive(ModifierKey.SHIFT),
            capsLock = mods.capsLock,
            language = controller.language,
            mode = controller.mode,
            enterLabel = enterLabel,
        )
        root.keyboardView.hapticFeedback = settings.hapticFeedback
        root.keyboardView.vibration = settings.vibration
        root.stickerPanel.character = settings.stickerCharacter
        root.keyboardView.spaceSwipeEnabled = settings.spaceSwipeSwitchesLanguage
        root.keyboardView.keyPreview = settings.keyPreview
        root.keyboardView.backspaceSwipeEnabled = settings.backspaceSwipe
        root.keyboardView.spaceCursorEnabled = settings.spaceCursor
        root.keyboardView.touchModel = if (settings.learnTouch) touchModel else null
        root.keyboardView.touchLearning = !ownField
        if (keySounds == null) keySounds = runCatching { KeySounds(this) }.getOrNull()
        keySounds?.voice = KeySounds.voiceOf(settings.themeMode)
        root.keyboardView.keySounds = keySounds
        root.keyboardView.soundVolume = if (settings.keySound && soundStyle) settings.keySoundVolume else 0f
        root.compact = settings.compactToolbar
        root.hideButtons = settings.hideToolbarButtons
        val layout = controller.layout(settings.mobileCursorRow)
        // Every layer of both modes is exactly as tall as the mobile letters: switching between the
        // computer and the mobile keyboard, the emoji or the number pad never makes the keyboard jump.
        // A layer with more rows gets lower keys instead.
        val mobileRows = LayoutProvider.layout(KeyboardMode.MOBILE, controller.language, Layer.LETTERS, false).rows.size
        val keyHeight = if (layout.rows.isEmpty()) MOBILE_KEY_DP else MOBILE_KEY_DP * mobileRows / layout.rows.size
        // Another language or layer: which way the keys slide (from the letters away: to the right; back to
        // the letters: to the left; the language key always the same way round; a swipe its own way).
        val shown = Triple(controller.language, controller.layer, controller.mode)
        val before = lastShown
        lastShown = shown
        root.keyboardView.slideDirection = when {
            before == null || before == shown -> 0
            before.third != shown.third -> 1
            before.second != shown.second -> if (layerDepth(shown.second) < layerDepth(before.second)) -1 else 1
            else -> swipeWay ?: 1
        }
        swipeWay = null
        root.keyboardView.bind(
            layout = layout,
            labels = labels,
            keyHeightDp = keyHeight * settings.keyHeightScale,
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
                    bluetoothSending || switchingTo != null -> KeyboardRootView.BluetoothButton.WAITING
                    else -> KeyboardRootView.BluetoothButton.IDLE
                },
                soundOn = if (soundStyle && settings.soundButton) settings.keySound else null,
                soundVolume = settings.keySoundVolume,
                remoteOffered = bluetoothKeyboardSupported && settings.bluetoothKeyboard && bluetoothSending && bluetoothConnected,
                inSettings = ownField,
                textShown = settings.assistEnabled,
                assistOn = !settings.assistPaused,
                translateShown = settings.translateButton,
                clipboardShown = settings.clipboardButton,
                voiceShown = settings.voiceButton,
                // Taken away in the settings, it is still there while typing goes to another device (Ctrl, Esc,
                // F-keys and arrows are needed there) and while the other keyboard is open (the way back).
                modeKeyShown = settings.computerButton || bluetoothSending || controller.mode != settings.defaultMode,
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
        /** Marks that end a word for the translation (with a space). */
        private const val WORD_ENDS = ",;:.!?…)»\""

        /** Keys that are typing too: a note on the strip goes when they are pressed. */
        private val NOTE_CLEARING_KEYS = setOf(SpecialKey.SPACE, SpecialKey.BACKSPACE, SpecialKey.ENTER)

        /** The running keyboard, for instrumentation tests only. */
        @Volatile
        internal var instance: KeyboardService? = null
            private set

        private const val STARTUP_SETTINGS_TIMEOUT_MS = 500L
        /** How far the app behind the glass is blurred. */
        private const val BLUR_DP = 26
        private const val MOBILE_KEY_DP = 52f
        private const val WORD_CONTEXT = 64
        private const val CHECK_WARNING_MS = 8000L
        /** How long a switch to another device may take before it is given up. */
        private const val SWITCH_TIMEOUT_MS = 10_000L
        private const val PREVIEW_DELAY_MS = 350L
        private const val DONE_MESSAGE_MS = 4000L
        private const val SENT_TEXT_MAX = 2000
        /**
         * How much of the text before and after the cursor the strip shows: a few lines' worth around the
         * cursor (it moves with the cursor). More made every key slow in a long text, the strip being set
         * again, letter by letter on the typewriter, after each one.
         */
        private const val ECHO_CONTEXT = 300
        private const val ECHO_AFTER = 150
        /** A line break in the strip: one mark for one character, so a touched word maps back exactly. */
        private const val LINE_MARK = '⏎'
        /** A typewriter line: the bell rings at [BELL_COLUMN], eight characters before its end. */
        private const val LINE_LENGTH = 65
        private const val BELL_COLUMN = 57
        private const val SHEET_LINES = 30
        /** More than this at once (a picked word with its space is fewer) is a paste: no bell for it. */
        private const val MAX_TYPED_STEP = 24
        private const val UNDO_SLACK = 8
        private const val DOUBLE_SPACE_MS = 1100L
        private const val CORRECTION_PREFS = "autocorrect"
        private const val OWN_WORD_MIN_COUNT = 3
        /** A word added by hand or imported starts at 5: it is preferred to base words when fixing a slip. */
        private const val TRUSTED_WORD_COUNT = 5
        private const val REJECTED_KEY = "rejected"
        private const val CORRECTION_MESSAGE_MS = 2500L
        private const val BACKSPACE_SWIPE_CONTEXT = 1000
        private const val EMOJI_PREFS = "emoji"
        private const val EMOJI_USAGE_KEY = "usage"

        /** Keys that change only this keyboard, also in Bluetooth mode. */
        private val LOCAL_KEYS = setOf(
            SpecialKey.SHIFT, SpecialKey.CAPS_LOCK, SpecialKey.CTRL, SpecialKey.ALT, SpecialKey.META,
            SpecialKey.LANGUAGE, SpecialKey.MODE, SpecialKey.LAYER_LETTERS, SpecialKey.LAYER_SYMBOLS,
            SpecialKey.LAYER_SYMBOLS_MORE, SpecialKey.LAYER_FUNCTION, SpecialKey.LAYER_EMOJI, SpecialKey.LAYER_NUMPAD,
            SpecialKey.EMOJI_PREVIOUS, SpecialKey.EMOJI_NEXT, SpecialKey.STICKERS, SpecialKey.VOICE, SpecialKey.SETTINGS,
            SpecialKey.HIDE,
        )
    }
}
