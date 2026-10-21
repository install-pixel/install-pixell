package com.offlinekeyboard.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinekeyboard.BuildConfig
import com.offlinekeyboard.R
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.translation.TranslationDirection
import com.offlinekeyboard.dictionary.UserDictionaryStore
import com.offlinekeyboard.keyboard.Accent
import com.offlinekeyboard.keyboard.KeyboardService
import com.offlinekeyboard.keyboard.ThemeMode
import com.offlinekeyboard.models.ModelStore
import com.offlinekeyboard.privacy.OfflinePolicy
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SettingsRepository(this)
        setContent {
            val current by repository.settings.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (current?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MaterialTheme(colorScheme = settingsColors(dark, current?.accent ?: Accent.SKY)) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsApp(repository)
                }
            }
        }
    }
}

internal enum class Page { MAIN, SHORTCUTS, DICTIONARY, MODELS }

internal typealias UpdateSettings = ((KeyboardSettings) -> KeyboardSettings) -> Unit

private fun isKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun isKeyboardSelected(context: Context): Boolean {
    val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
    return ComponentName.unflattenFromString(current) == ComponentName(context, KeyboardService::class.java)
}

@Composable
private fun SettingsApp(repository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    val update: UpdateSettings = { transform -> scope.launch { repository.update(transform) } }

    BackHandler(enabled = page != Page.MAIN) { page = Page.MAIN }

    val s = settings ?: return
    when (page) {
        Page.MAIN -> MainPage(s, update, onOpen = { page = it }, onReset = { scope.launch { repository.reset() } })
        Page.SHORTCUTS -> ShortcutsPage(s, update, onBack = { page = Page.MAIN })
        Page.DICTIONARY -> DictionaryPage(s, update, onBack = { page = Page.MAIN })
        Page.MODELS -> ModelsPage(onBack = { page = Page.MAIN })
    }
}

/** Scrollable page with a title and an optional back button. */
@Composable
internal fun PageScaffold(title: String, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("‹ " + stringResource(R.string.back)) }
            }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
        Spacer(Modifier.heightIn(min = 24.dp))
    }
}

@Composable
private fun MainPage(s: KeyboardSettings, update: UpdateSettings, onOpen: (Page) -> Unit, onReset: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    var selected by remember { mutableStateOf(isKeyboardSelected(context)) }
    var showResetDialog by remember { mutableStateOf(false) }
    val models = remember { ModelStore(context) }
    val dictionary = remember { UserDictionaryStore.get(context) }
    val dictionaryVersion by dictionary.changes.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        enabled = isKeyboardEnabled(context)
        selected = isKeyboardSelected(context)
        onPauseOrDispose { }
    }

    PageScaffold(stringResource(R.string.app_name), onBack = null) {
        Section(stringResource(R.string.section_setup)) {
            SetupStep(stringResource(R.string.setup_step_enable), enabled, stringResource(R.string.setup_enable_button)) {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            SetupStep(stringResource(R.string.setup_step_select), selected, stringResource(R.string.setup_select_button)) {
                context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            }
            Text(
                stringResource(if (enabled && selected) R.string.setup_ready else R.string.setup_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section(stringResource(R.string.section_try)) {
            var sample by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                value = sample,
                onValueChange = { sample = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                label = { Text(stringResource(R.string.try_hint)) },
            )
        }

        Section(stringResource(R.string.section_features)) {
            NavRow(
                stringResource(R.string.page_shortcuts),
                stringResource(R.string.page_shortcuts_summary, s.shortcuts.size),
            ) { onOpen(Page.SHORTCUTS) }
            HorizontalDivider()
            val words = remember(dictionaryVersion) { Language.entries.sumOf { dictionary.dictionary.size(it) } }
            NavRow(
                stringResource(R.string.page_dictionary),
                stringResource(
                    if (s.learnWords) R.string.page_dictionary_summary_on else R.string.page_dictionary_summary_off,
                    words,
                ),
            ) { onOpen(Page.DICTIONARY) }
            HorizontalDivider()
            val installed = remember(models) {
                Language.entries.count { models.speechModel(it) != null } +
                    TranslationDirection.entries.count { models.translationModel(it) != null }
            }
            NavRow(stringResource(R.string.page_models), stringResource(R.string.page_models_summary, installed, 5)) {
                onOpen(Page.MODELS)
            }
        }

        Section(stringResource(R.string.section_appearance)) {
            Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleSmall)
            for ((mode, label) in listOf(
                ThemeMode.SYSTEM to R.string.theme_system,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.DARK to R.string.theme_dark,
            )) {
                RadioRow(stringResource(label), s.themeMode == mode) { update { it.copy(themeMode = mode) } }
            }
            Text(stringResource(R.string.accent_title), style = MaterialTheme.typography.titleSmall)
            AccentPicker(s.accent) { a -> update { it.copy(accent = a) } }
        }

        Section(stringResource(R.string.section_keyboard)) {
            Text(stringResource(R.string.default_mode), style = MaterialTheme.typography.titleSmall)
            RadioRow(stringResource(R.string.mode_mobile), s.defaultMode == KeyboardMode.MOBILE) {
                update { it.copy(defaultMode = KeyboardMode.MOBILE) }
            }
            RadioRow(stringResource(R.string.mode_computer), s.defaultMode == KeyboardMode.COMPUTER) {
                update { it.copy(defaultMode = KeyboardMode.COMPUTER) }
            }
            SwitchRow(stringResource(R.string.remember_last_mode), s.rememberLastMode) { v ->
                update { it.copy(rememberLastMode = v) }
            }
            HorizontalDivider()
            Text(stringResource(R.string.input_languages), style = MaterialTheme.typography.titleSmall)
            for (language in Language.entries) {
                CheckRow(languageName(language), language in s.enabledLanguages) { v ->
                    update {
                        val next = if (v) it.enabledLanguages + language else it.enabledLanguages - language
                        // At least one layout must stay enabled.
                        if (next.isEmpty()) it else it.copy(enabledLanguages = Language.entries.filter { l -> l in next })
                    }
                }
            }
            Text(stringResource(R.string.language_switch_hint), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(
                stringResource(R.string.key_height, (s.keyHeightScale * 100).toInt()),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = s.keyHeightScale,
                onValueChange = { v -> update { it.copy(keyHeightScale = v) } },
                valueRange = KeyboardSettings.MIN_KEY_HEIGHT_SCALE..KeyboardSettings.MAX_KEY_HEIGHT_SCALE,
                steps = 6,
            )
            SwitchRow(stringResource(R.string.mobile_cursor_row), s.mobileCursorRow) { v ->
                update { it.copy(mobileCursorRow = v) }
            }
            SwitchRow(stringResource(R.string.auto_capitalize), s.autoCapitalize) { v ->
                update { it.copy(autoCapitalize = v) }
            }
            SwitchRow(stringResource(R.string.space_swipe), s.spaceSwipeSwitchesLanguage) { v ->
                update { it.copy(spaceSwipeSwitchesLanguage = v) }
            }
            SwitchRow(stringResource(R.string.haptic), s.hapticFeedback) { v ->
                update { it.copy(hapticFeedback = v) }
            }
        }

        Section(stringResource(R.string.section_voice)) {
            Text(stringResource(R.string.voice_language), style = MaterialTheme.typography.titleSmall)
            RadioRow(stringResource(R.string.voice_language_follow), s.voiceLanguage == null) {
                update { it.copy(voiceLanguage = null) }
            }
            for (language in Language.entries) {
                RadioRow(languageName(language), s.voiceLanguage == language) {
                    update { it.copy(voiceLanguage = language) }
                }
            }
            SwitchRow(stringResource(R.string.voice_commands), s.voiceCommands) { v -> update { it.copy(voiceCommands = v) } }
            Text(stringResource(R.string.voice_commands_hint), style = MaterialTheme.typography.bodySmall)
        }

        Section(stringResource(R.string.section_translation)) {
            Text(stringResource(R.string.translation_how), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.translation_mode), style = MaterialTheme.typography.titleSmall)
            RadioRow(stringResource(R.string.translation_mode_auto), s.translationAuto) {
                update { it.copy(translationAuto = true) }
            }
            RadioRow(stringResource(R.string.translation_mode_manual), !s.translationAuto) {
                update { it.copy(translationAuto = false) }
            }
            if (s.translationAuto) {
                Text(stringResource(R.string.translation_pause), style = MaterialTheme.typography.titleSmall)
                for ((ms, label) in listOf(
                    1500 to stringResource(R.string.translation_pause_seconds, "1,5"),
                    2000 to stringResource(R.string.translation_pause_seconds, "2"),
                    3000 to stringResource(R.string.translation_pause_seconds, "3"),
                    0 to stringResource(R.string.translation_pause_off),
                )) {
                    RadioRow(label, s.translationPauseMs == ms) { update { it.copy(translationPauseMs = ms) } }
                }
            }
            SwitchRow(stringResource(R.string.translation_accurate), s.translationAccurate) { v ->
                update { it.copy(translationAccurate = v) }
            }
            Text(stringResource(R.string.translation_direction), style = MaterialTheme.typography.titleSmall)
            for (direction in TranslationDirection.entries) {
                val label = "${languageName(direction.source)} → ${languageName(direction.target)}"
                RadioRow(label, s.translationDirection == direction) {
                    update { it.copy(translationDirection = direction) }
                }
            }
        }

        Section(stringResource(R.string.section_privacy)) {
            val networkPermissions = remember { OfflinePolicy.requestedNetworkPermissions(context) }
            StatusLine(stringResource(R.string.privacy_no_network), networkPermissions.isEmpty())
            Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodyMedium)
        }

        Section(stringResource(R.string.section_about)) {
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
            OutlinedButton(onClick = { showResetDialog = true }) { Text(stringResource(R.string.reset_settings)) }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_settings)) },
            text = { Text(stringResource(R.string.reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onReset()
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
