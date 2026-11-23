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
                    SwitchRow(stringResource(R.string.space_swipe), s.spaceSwipeSwitchesLanguage) { v -> update { it.copy(spaceSwipeSwitchesLanguage = v) } }
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_TEXT -> {
                GuideIntro(R.string.onb_g_text_title, R.string.onb_g_text)
                Section(null) {
                    SwitchRow(stringResource(R.string.auto_correct), s.autoCorrect) { v -> update { it.copy(autoCorrect = v) } }
                    SwitchRow(stringResource(R.string.auto_capitalize), s.autoCapitalize) { v -> update { it.copy(autoCapitalize = v) } }
                    SwitchRow(stringResource(R.string.double_space_period), s.doubleSpacePeriod) { v -> update { it.copy(doubleSpacePeriod = v) } }
                    SwitchRow(stringResource(R.string.smart_punctuation), s.smartPunctuation) { v -> update { it.copy(smartPunctuation = v) } }
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_WORDS -> {
                GuideIntro(R.string.onb_g_words_title, R.string.onb_g_words)
                Section(null) {
                    SwitchRow(stringResource(R.string.dictionary_suggestions), s.showSuggestions) { v -> update { it.copy(showSuggestions = v) } }
                    SwitchRow(stringResource(R.string.dictionary_learn), s.learnWords) { v -> update { it.copy(learnWords = v) } }
                    Text(stringResource(R.string.dictionary_learn_hint), style = MaterialTheme.typography.bodySmall)
                    SwitchRow(stringResource(R.string.long_context), s.longContext) { v -> update { it.copy(longContext = v) } }
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_KEYS -> {
                GuideIntro(R.string.onb_g_keys_title, R.string.onb_g_keys)
                Section(null) {
                    SwitchRow(stringResource(R.string.key_preview), s.keyPreview) { v -> update { it.copy(keyPreview = v) } }
                    SwitchRow(stringResource(R.string.backspace_swipe), s.backspaceSwipe) { v -> update { it.copy(backspaceSwipe = v) } }
                    SwitchRow(stringResource(R.string.space_cursor), s.spaceCursor) { v -> update { it.copy(spaceCursor = v) } }
                    SwitchRow(stringResource(R.string.learn_touch), s.learnTouch) { v -> update { it.copy(learnTouch = v) } }
                    Text(stringResource(R.string.learn_touch_hint), style = MaterialTheme.typography.bodySmall)
                    SwitchRow(stringResource(R.string.mobile_cursor_row), s.mobileCursorRow) { v -> update { it.copy(mobileCursorRow = v) } }
                    SwitchRow(stringResource(R.string.compact_toolbar), s.compactToolbar) { v -> update { it.copy(compactToolbar = v) } }
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_SOUND -> {
                GuideIntro(R.string.section_sound_feel, R.string.onb_g_sound)
                Section(null) {
                    SwitchRow(stringResource(R.string.key_sound), s.keySound) { v ->
                        update { it.copy(keySound = v, keySoundVolume = if (v && it.keySoundVolume <= 0f) 0.3f else it.keySoundVolume) }
                    }
                    SwitchRow(stringResource(R.string.haptic), s.hapticFeedback) { v -> update { it.copy(hapticFeedback = v) } }
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_VOICE -> {
                GuideIntro(R.string.page_voice, R.string.onb_g_voice)
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_next)) }
            }
            STEP_G_COMPUTER -> {
                GuideIntro(R.string.page_bluetooth, R.string.onb_g_computer)
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
private const val STEP_G_WORDS = 12
private const val STEP_G_KEYS = 13
private const val STEP_G_SOUND = 14
private const val STEP_G_VOICE = 15
private const val STEP_G_COMPUTER = 16

/** Every group of settings, in the order of the menu, for «Go through every setting with the helper». */
private val GUIDED_STEPS = listOf(STEP_G_LANGUAGES, STEP_G_TEXT, STEP_G_WORDS, STEP_G_KEYS, STEP_G_SOUND, STEP_G_VOICE, STEP_G_COMPUTER)

/** One helper step's title and what this group of settings is for. */
@Composable
private fun GuideIntro(title: Int, text: Int) {
    Section(stringResource(title)) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Suggestions, corrections and a learning dictionary on; the phone's languages. */
private fun recommended(s: KeyboardSettings, languages: List<Language>) = s.copy(
    enabledLanguages = languages,
    lastLanguage = languages.first(),
    showSuggestions = true,
    autoCorrect = true,
    learnWords = true,
    longContext = true,
    learnTouch = true,
)

/** A plain keyboard: no suggestions, corrections, learning, smart punctuation or sounds; the phone's languages. */
private fun minimum(s: KeyboardSettings, languages: List<Language>) = s.copy(
    enabledLanguages = languages,
    lastLanguage = languages.first(),
    showSuggestions = false,
    autoCorrect = false,
    learnWords = false,
    longContext = false,
    learnTouch = false,
    smartPunctuation = false,
    keySound = false,
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
