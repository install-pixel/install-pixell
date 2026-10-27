package com.blacklaunch.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.settings.Preset
import com.blacklaunch.launcher.ui.theme.Oled

@Composable
internal fun presetTitle(preset: Preset) = stringResource(
    when (preset) {
        Preset.MINIMUM -> R.string.preset_minimum
        Preset.TEMPO -> R.string.preset_tempo
        Preset.COZY -> R.string.preset_cozy
    },
)

@Composable
private fun presetDescription(preset: Preset) = stringResource(
    when (preset) {
        Preset.MINIMUM -> R.string.preset_minimum_desc
        Preset.TEMPO -> R.string.preset_tempo_desc
        Preset.COZY -> R.string.preset_cozy_desc
    },
)

/** Один набір: назва, що в ньому, кнопка. */
@Composable
private fun PresetCard(preset: Preset, current: Boolean, onPick: () -> Unit) {
    Card {
        Text(presetTitle(preset) + if (current) "  ✓" else "", color = if (current) Oled.Confirm else Oled.TextPrimary, fontSize = 18.sp)
        Note(presetDescription(preset))
        ActionOption(stringResource(R.string.preset_apply), Icons.Filled.Done, Tone.Confirm, onPick)
    }
}

/** Налаштування → «Набори налаштувань»: застосувати набір (з підтвердженням) — далі все можна змінити самому. */
@Composable
fun PresetsPage(onBack: () -> Unit, current: Preset?, onApply: (Preset) -> Unit) {
    var confirm by rememberSaveable { mutableStateOf<Preset?>(null) }
    SettingsPage(stringResource(R.string.presets_title), onBack) {
        Note(stringResource(R.string.presets_note))
        Preset.entries.forEach { preset -> PresetCard(preset, current == preset) { confirm = preset } }
    }
    confirm?.let { preset ->
        GlassDialog(
            onDismissRequest = { confirm = null },
            title = { DialogTitle(stringResource(R.string.preset_confirm_title, presetTitle(preset))) },
            text = { Text(stringResource(R.string.preset_confirm_note), color = Oled.TextSecondary, fontSize = 15.sp) },
            confirmButton = {
                DialogButton(stringResource(R.string.preset_apply), {
                    confirm = null
                    onApply(preset)
                }, Tone.Confirm)
            },
            dismissButton = { DialogButton(stringResource(R.string.cancel), { confirm = null }) },
        )
    }
}

/** Перший запуск: з чого почати. «Лишити як є» — нічого не змінювати (зручно, якщо вже все налаштовано). */
@Composable
fun FirstRunPresetDialog(onPick: (Preset) -> Unit, onKeep: () -> Unit) {
    GlassDialog(
        onDismissRequest = {},
        title = { DialogTitle(stringResource(R.string.presets_first_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.presets_first_note), color = Oled.TextSecondary, fontSize = 15.sp)
                Preset.entries.forEach { preset -> PresetCard(preset, current = false) { onPick(preset) } }
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.preset_keep), onKeep) },
    )
}
