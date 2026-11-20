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
import com.offlinekeyboard.keyboard.ThemeMode

/** How the first setup sets the keyboard up. */
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
    val steps = buildList {
        add(STEP_HOW)
        if (choice == SetupChoice.CUSTOM) add(STEP_QUESTIONS)
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
            STEP_QUESTIONS -> {
                Section(stringResource(R.string.onb_languages)) {
                    for (language in Language.entries) {
                        CheckRow(languageName(language), language in s.enabledLanguages) { v ->
                            update {
                                val chosen = if (v) it.enabledLanguages + language else it.enabledLanguages - language
                                // At least one language stays.
                                if (chosen.isEmpty()) it else it.copy(enabledLanguages = Language.entries.filter { l -> l in chosen })
                            }
                        }
                    }
                }
                Section(null) {
                    val helping = s.showSuggestions && s.autoCorrect && s.learnWords
                    SwitchRow(stringResource(R.string.onb_help), helping) { v ->
                        update { it.copy(showSuggestions = v, autoCorrect = v, learnWords = v, longContext = v, learnTouch = v) }
                    }
                    SwitchRow(stringResource(R.string.onb_sound), s.keySound && s.hapticFeedback) { v ->
                        update { it.copy(keySound = v, hapticFeedback = v) }
                    }
                    Text(stringResource(R.string.onb_models_note), style = MaterialTheme.typography.bodySmall)
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
                Section(stringResource(R.string.onb_style)) {
                    for ((mode, label, description) in listOf(
                        Triple(ThemeMode.GLASS, R.string.theme_glass, R.string.theme_glass_desc),
                        Triple(ThemeMode.SHEET, R.string.theme_sheet, R.string.theme_sheet_desc),
                        Triple(ThemeMode.TYPEWRITER, R.string.theme_typewriter, R.string.theme_typewriter_desc),
                    )) {
                        RadioRow(stringResource(label), s.themeMode == mode, stringResource(description)) {
                            update { it.copy(themeMode = mode) }
                        }
                    }
                }
                ActionButton(onClick = next) { Text(stringResource(R.string.onb_done)) }
            }
        }
    }
}

private const val STEP_HOW = 0
private const val STEP_QUESTIONS = 1
private const val STEP_ENABLE = 2
private const val STEP_STYLE = 3

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
