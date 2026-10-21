package com.offlinekeyboard.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinekeyboard.BuildConfig
import com.offlinekeyboard.R
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.keyboard.KeyboardService
import com.offlinekeyboard.privacy.OfflinePolicy
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SettingsRepository(this)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(repository)
                }
            }
        }
    }
}

private fun isKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun isKeyboardSelected(context: Context): Boolean {
    val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
    return ComponentName.unflattenFromString(current) == ComponentName(context, KeyboardService::class.java)
}

@Composable
private fun SettingsScreen(repository: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
    var enabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    var selected by remember { mutableStateOf(isKeyboardSelected(context)) }
    var showResetDialog by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        enabled = isKeyboardEnabled(context)
        selected = isKeyboardSelected(context)
        onPauseOrDispose { }
    }

    fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        scope.launch { repository.update(transform) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)

        Section(stringResource(R.string.section_setup)) {
            StatusLine(stringResource(R.string.setup_enabled), enabled)
            StatusLine(stringResource(R.string.setup_selected), selected)
            Text(stringResource(R.string.setup_instructions), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }) { Text(stringResource(R.string.setup_open_settings)) }
                OutlinedButton(onClick = {
                    context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
                }) { Text(stringResource(R.string.setup_choose_keyboard)) }
            }
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

        val s = settings ?: return@Column

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
                val checked = language in s.enabledLanguages
                CheckRow(languageName(language), checked) { v ->
                    update {
                        val next = if (v) it.enabledLanguages + language else it.enabledLanguages - language
                        // At least one layout must stay enabled.
                        if (next.isEmpty()) it else it.copy(enabledLanguages = Language.entries.filter { l -> l in next })
                    }
                }
            }
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
            Text(stringResource(R.string.autocorrect_not_available), style = MaterialTheme.typography.bodySmall)
        }

        Section(stringResource(R.string.section_voice)) {
            Text(stringResource(R.string.voice_language), style = MaterialTheme.typography.titleSmall)
            for (language in Language.entries) {
                RadioRow(languageName(language), s.voiceLanguage == language) {
                    update { it.copy(voiceLanguage = language) }
                }
            }
            Text(stringResource(R.string.voice_model_status), style = MaterialTheme.typography.bodyMedium)
        }

        Section(stringResource(R.string.section_translation)) {
            Text(stringResource(R.string.translation_status), style = MaterialTheme.typography.bodyMedium)
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
        Spacer(Modifier.heightIn(min = 24.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_settings)) },
            text = { Text(stringResource(R.string.reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    scope.launch { repository.reset() }
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun languageName(language: Language): String = stringResource(
    when (language) {
        Language.UK -> R.string.language_uk
        Language.PL -> R.string.language_pl
        Language.EN -> R.string.language_en
    },
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✅" else "⚠️")
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
