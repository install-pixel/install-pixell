package com.offlinekeyboard.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.offlinekeyboard.R
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.keyboard.KeyboardService

/** How the first setup sets the keyboard up: standard, minimum, or every setting with the helper. */
private enum class SetupChoice { RECOMMENDED, MINIMUM, CUSTOM }

/**
 * The first setup, shown once when the app is opened from the launcher: how to set the keyboard up
 * («Recommended», «Minimum» or a few short questions), turning the keyboard on (skipped when it already is),
 * and the style. Translation and voice are not asked about: their models are offered the first time their
 * buttons are touched. [onDone] marks it done.
 */
@Composable
internal fun OnboardingFlow(s: KeyboardSettings, update: UpdateSettings, onDone: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var choice by rememberSaveable { mutableStateOf(SetupChoice.RECOMMENDED) }
    val keyboardReady = remember { isReady(context) }
    // The steps that are shown: the questions only for «I'll set it up», turning on only when needed.
    // With the helper: every group of settings in turn, each explained, before turning the keyboard on and the style.
    val steps = buildList {
        add(STEP_HOW)
        if (choice == SetupChoice.CUSTOM) addAll(GUIDED_STEPS)
        if (!keyboardReady) add(STEP_ENABLE)
        add(STEP_STYLE)
    }
    val current = steps[step.coerceIn(0, steps.lastIndex)]
    val next: () -> Unit = { if (step < steps.lastIndex) step++ else onDone() }
    BackHandler(enabled = step > 0) { step-- }

    PageScaffold(stringResource(R.string.onb_title), onBack = if (step > 0) ({ step-- }) else null) {
        when (current) {
            STEP_HOW -> {
                Section(stringResource(R.string.onb_how)) {
                    RadioRow(stringResource(R.string.onb_recommended), choice == SetupChoice.RECOMMENDED, stringResource(R.string.onb_recommended_desc)) {
                        choice = SetupChoice.RECOMMENDED
                    }
                    RadioRow(stringResource(R.string.onb_minimum), choice == SetupChoice.MINIMUM, stringResource(R.string.onb_minimum_desc)) {
                        choice = SetupChoice.MINIMUM
                    }
                    RadioRow(stringResource(R.string.onb_custom), choice == SetupChoice.CUSTOM, stringResource(R.string.onb_custom_desc)) {
                        choice = SetupChoice.CUSTOM
                    }
                }
                ActionButton(onClick = {
                    val languages = phoneLanguages(context)
                    when (choice) {
                        SetupChoice.RECOMMENDED -> update { recommended(it, languages) }
                        SetupChoice.MINIMUM -> update { minimum(it, languages) }
                        SetupChoice.CUSTOM -> update { recommended(it, languages) }
                    }
                    next()
                }) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_LANGUAGES -> {
                GuideIntro(R.string.onb_languages, R.string.onb_g_languages)
                Section(null) {
                    for (language in Language.entries) {
                        CheckRow(languageName(language), language in s.enabledLanguages) { v ->
                            update {
                                val chosen = if (v) it.enabledLanguages + language else it.enabledLanguages - language
                                // At least one language stays.
                                if (chosen.isEmpty()) it else it.copy(enabledLanguages = Language.entries.filter { l -> l in chosen })
                            }
                        }
                    }
                    GuideNote(HelpItems.languages_langs)
                }
                Section(stringResource(R.string.default_mode)) {
                    RadioRow(stringResource(R.string.mode_mobile), s.defaultMode == KeyboardMode.MOBILE) {
                        update { it.copy(defaultMode = KeyboardMode.MOBILE, lastMode = KeyboardMode.MOBILE) }
                    }
                    RadioRow(stringResource(R.string.mode_computer), s.defaultMode == KeyboardMode.COMPUTER) {
                        update { it.copy(defaultMode = KeyboardMode.COMPUTER, lastMode = KeyboardMode.COMPUTER) }
                    }
                    GuideNote(HelpItems.languages_mode)
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_ASSIST -> {
                GuideIntro(R.string.section_assist, R.string.onb_g_assist)
                Section(null) {
                    SwitchRow(stringResource(R.string.assist_master), s.assistEnabled) { v -> update { it.copy(assistEnabled = v, assistPaused = false) } }
                    GuideNote(HelpItems.assist_master)
                }
                Section(null) {
                    SwitchRow(stringResource(R.string.auto_correct), s.autoCorrect, enabled = s.assistEnabled) { v -> update { it.copy(autoCorrect = v) } }
                    GuideNote(HelpItems.assist_autocorrect)
                    SwitchRow(stringResource(R.string.dictionary_suggestions), s.showSuggestions, enabled = s.assistEnabled) { v -> update { it.copy(showSuggestions = v) } }
                    GuideNote(HelpItems.assist_suggestions)
                }
                Section(null) {
                    SwitchRow(stringResource(R.string.long_context), s.longContext, enabled = s.assistEnabled) { v -> update { it.copy(longContext = v) } }
                    GuideNote(HelpItems.assist_nextword)
                    SwitchRow(stringResource(R.string.smart_punctuation), s.smartPunctuation, enabled = s.assistEnabled) { v -> update { it.copy(smartPunctuation = v) } }
                    GuideNote(HelpItems.assist_punctuation)
                }
                Section(null) {
                    SwitchRow(stringResource(R.string.dictionary_learn), s.learnWords, enabled = s.assistEnabled) { v -> update { it.copy(learnWords = v) } }
                    GuideNote(HelpItems.assist_learn)
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_TEXT -> {
                GuideIntro(R.string.page_typing_text, R.string.onb_g_text)
                Section(null) {
                    SwitchRow(stringResource(R.string.auto_capitalize), s.autoCapitalize) { v -> update { it.copy(autoCapitalize = v) } }
                    GuideNote(HelpItems.text_caps)
                    SwitchRow(stringResource(R.string.double_space_period), s.doubleSpacePeriod) { v -> update { it.copy(doubleSpacePeriod = v) } }
                    GuideNote(HelpItems.text_doublespace)
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_KEYS -> {
                GuideIntro(R.string.page_typing_keys, R.string.onb_g_keys)
                Section(null) {
                    SwitchRow(stringResource(R.string.key_preview), s.keyPreview) { v -> update { it.copy(keyPreview = v) } }
                    GuideNote(HelpItems.keys_preview)
                    SwitchRow(stringResource(R.string.backspace_swipe), s.backspaceSwipe) { v -> update { it.copy(backspaceSwipe = v) } }
                    GuideNote(HelpItems.keys_bswipe)
                }
                Section(null) {
                    SwitchRow(stringResource(R.string.space_cursor), s.spaceCursor) { v -> update { it.copy(spaceCursor = v) } }
                    GuideNote(HelpItems.keys_spacecursor)
                    SwitchRow(stringResource(R.string.space_swipe), s.spaceSwipeSwitchesLanguage) { v -> update { it.copy(spaceSwipeSwitchesLanguage = v) } }
                    GuideNote(HelpItems.keys_spaceswipe)
                }
                Section(null) {
                    SwitchRow(stringResource(R.string.learn_touch), s.learnTouch) { v -> update { it.copy(learnTouch = v) } }
                    GuideNote(HelpItems.keys_learntouch)
                    SwitchRow(stringResource(R.string.mobile_cursor_row), s.mobileCursorRow) { v -> update { it.copy(mobileCursorRow = v) } }
                    GuideNote(HelpItems.keys_cursorrow)
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_BUTTONS -> {
                GuideIntro(R.string.section_buttons, R.string.onb_g_buttons)
                Section(null) {
                    SwitchRow(stringResource(R.string.compact_toolbar), s.compactToolbar) { v -> update { it.copy(compactToolbar = v) } }
                    GuideNote(HelpItems.buttons_compact)
                    SwitchRow(stringResource(R.string.menu_button_show), !s.hideToolbarButtons) { v -> update { it.copy(hideToolbarButtons = !v) } }
                    GuideNote(HelpItems.buttons_hide)
                    SwitchRow(stringResource(R.string.typed_text_show), s.showTypedText) { v -> update { it.copy(showTypedText = v) } }
                    GuideNote(HelpItems.buttons_typedtext)
                }
                Section(null) {
                    SwitchRow(stringResource(R.string.clipboard_button_show), s.clipboardButton) { v -> update { it.copy(clipboardButton = v) } }
                    GuideNote(HelpItems.buttons_clipboard)
                    SwitchRow(stringResource(R.string.sound_button_show), s.soundButton) { v -> update { it.copy(soundButton = v) } }
                    GuideNote(HelpItems.buttons_sound)
                    SwitchRow(stringResource(R.string.computer_button_show), s.computerButton) { v -> update { it.copy(computerButton = v) } }
                    GuideNote(HelpItems.buttons_modekey)
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_SOUND -> {
                GuideIntro(R.string.section_sound_feel, R.string.onb_g_sound)
                Section(null) {
                    SwitchRow(stringResource(R.string.key_sound), s.keySound) { v ->
                        update { it.copy(keySound = v, keySoundVolume = if (v && it.keySoundVolume <= 0f) 0.5f else it.keySoundVolume) }
                    }
                    GuideNote(HelpItems.look_keysound)
                    SwitchRow(stringResource(R.string.haptic), s.hapticFeedback) { v -> update { it.copy(hapticFeedback = v) } }
                    GuideNote(HelpItems.look_haptic)
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_VOICE -> {
                GuideIntro(R.string.page_voice, R.string.onb_g_voice)
                Section(null) {
                    SwitchRow(stringResource(R.string.voice_button_show), s.voiceButton) { v -> update { it.copy(voiceButton = v) } }
                    GuideNote(HelpItems.voice_voiceon)
                }
                Section(null) {
                    SwitchRow(stringResource(R.string.translate_button_show), s.translateButton) { v ->
                        update { it.copy(translateButton = v, translateEnabled = if (v) it.translateEnabled else false) }
                    }
                    GuideNote(HelpItems.voice_translateon)
                    GuideNote(HelpItems.voice_models)
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_COMPUTER -> {
                GuideIntro(R.string.page_bluetooth, R.string.onb_g_computer)
                Section(null) {
                    GuideNote(HelpItems.devices_what)
                    GuideNote(HelpItems.devices_add)
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_ENABLE -> {
                var enabled by remember { mutableStateOf(isEnabled(context)) }
                var selected by remember { mutableStateOf(isSelected(context)) }
                LifecycleResumeEffect(Unit) {
                    enabled = isEnabled(context)
                    selected = isSelected(context)
                    onPauseOrDispose { }
                }
                Section(stringResource(R.string.onb_enable)) {
                    SetupStep(stringResource(R.string.setup_step_enable), enabled, stringResource(R.string.setup_enable_button)) {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }
                    SetupStep(stringResource(R.string.setup_step_select), selected, stringResource(R.string.setup_select_button)) {
                        context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
                        selected = isSelected(context)
                    }
                    Text(
                        stringResource(if (enabled && selected) R.string.setup_ready else R.string.setup_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            else -> {
                StylePicker(s, update, stringResource(R.string.onb_style))
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_done)) }
            }
        }
    }
}

private const val STEP_HOW = 0
private const val STEP_ENABLE = 2
private const val STEP_STYLE = 3
private const val STEP_G_LANGUAGES = 10
private const val STEP_G_TEXT = 11
private const val STEP_G_KEYS = 13
private const val STEP_G_SOUND = 14
private const val STEP_G_VOICE = 15
private const val STEP_G_COMPUTER = 16
private const val STEP_G_ASSIST = 17
private const val STEP_G_BUTTONS = 18

/** Every group of settings, in the order of the menu, for «Go through every setting with the helper». */
private val GUIDED_STEPS = listOf(
    STEP_G_LANGUAGES, STEP_G_ASSIST, STEP_G_TEXT, STEP_G_KEYS, STEP_G_BUTTONS, STEP_G_SOUND, STEP_G_VOICE, STEP_G_COMPUTER,
)

/** One helper step's title and what this group of settings is for. */
@Composable
private fun GuideIntro(title: Int, text: Int) {
    Section(stringResource(title)) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Under a setting, the same explanation the help gives for it (what happens on and off). */
@Composable
private fun GuideNote(item: HelpItem) {
    Text(stringResource(item.body), style = MaterialTheme.typography.bodyMedium, color = Tone.TextPlain)
}

/**
 * What every way of setting up starts from, the same in all of them: a middle volume, the usual brightness and
 * size, the letter above the finger (not on the typewriter, whose key goes down by itself).
 */
private fun common(s: KeyboardSettings, languages: List<Language>) = s.copy(
    enabledLanguages = languages,
    lastLanguage = languages.first(),
    keySoundVolume = 0.5f,
    keyBrightness = com.offlinekeyboard.keyboard.KeyboardTheme.DEFAULT_BRIGHTNESS,
    keyHeightScale = 1f,
    keyPreview = !s.themeMode.typewriter,
    assistPaused = false,
    translateEnabled = false,
    // The menu button at the left in sight, in every way of setting up (in Minimum it is all there is).
    hideToolbarButtons = false,
    // Translation and voice wait until they are turned on (their buttons appear then).
    translateButton = false,
    voiceButton = false,
)

/** Everything that helps typing on: suggestions, corrections, punctuation, learning words and taps. */
private fun recommended(s: KeyboardSettings, languages: List<Language>) = common(s, languages).copy(
    assistEnabled = true,
    showSuggestions = true,
    autoCorrect = true,
    smartPunctuation = true,
    learnWords = true,
    longContext = true,
    learnTouch = true,
    clipboardButton = true,
    soundButton = true,
    computerButton = true,
)

/** Just a keyboard: no help while typing, nothing learned, no mode buttons (no row at all), no sound button. */
private fun minimum(s: KeyboardSettings, languages: List<Language>) = common(s, languages).copy(
    assistEnabled = false,
    showSuggestions = false,
    autoCorrect = false,
    learnWords = false,
    longContext = false,
    learnTouch = false,
    smartPunctuation = false,
    keySound = false,
    clipboardButton = false,
    soundButton = false,
    computerButton = false,
    defaultMode = KeyboardMode.MOBILE,
    lastMode = KeyboardMode.MOBILE,
)

/** The keyboard's languages the phone uses, in the keyboard's order; Ukrainian when it uses none of them. */
private fun phoneLanguages(context: Context): List<Language> {
    val locales = context.resources.configuration.locales
    val codes = (0 until locales.size()).map { locales[it].language }.toSet()
    val found = Language.entries.filter { it.locale.language in codes }
    return found.ifEmpty { listOf(Language.UK) }
}

private fun isEnabled(context: Context): Boolean = runCatching {
    val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
    imm.enabledInputMethodList.any { it.packageName == context.packageName }
}.getOrDefault(false)

private fun isSelected(context: Context): Boolean = runCatching {
    val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
    ComponentName.unflattenFromString(current) == ComponentName(context, KeyboardService::class.java)
}.getOrDefault(false)

private fun isReady(context: Context) = isEnabled(context) && isSelected(context)
