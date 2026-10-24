package com.offlinekeyboard.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.offlinekeyboard.R
import com.offlinekeyboard.core.Shortcut
import com.offlinekeyboard.core.ShortcutAction
import com.offlinekeyboard.core.ShortcutKeys
import com.offlinekeyboard.core.ShortcutPreset

@Composable
internal fun actionName(action: ShortcutAction): String = stringResource(
    when (action) {
        ShortcutAction.SWITCH_LANGUAGE -> R.string.action_switch_language
        ShortcutAction.SWITCH_MODE -> R.string.action_switch_mode
        ShortcutAction.VOICE_INPUT -> R.string.action_voice
        ShortcutAction.TOGGLE_TRANSLATE -> R.string.action_translate
        ShortcutAction.TRANSLATE_SELECTION_OR_CLIPBOARD -> R.string.action_translate_clipboard
        ShortcutAction.HIDE_KEYBOARD -> R.string.action_hide
        ShortcutAction.INSERT_TEXT -> R.string.action_insert_text
    },
)

@Composable
internal fun ShortcutsPage(s: KeyboardSettings, update: UpdateSettings, onBack: () -> Unit) {
    var editing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val language = context.resources.configuration.locales[0].language
    val presets = remember(language) { bundledPresets(context, language) }
    var presetMessage by remember { mutableStateOf<String?>(null) }

    fun addPreset(preset: ShortcutPreset) {
        val fresh = preset.shortcuts.filter { p ->
            s.shortcuts.none { it.ctrl == p.ctrl && it.alt == p.alt && it.shift == p.shift && it.meta == p.meta && it.key == p.key }
        }
        if (fresh.isNotEmpty()) update { it.copy(shortcuts = it.shortcuts + fresh) }
        presetMessage = if (fresh.isEmpty()) {
            context.getString(R.string.presets_nothing_new)
        } else {
            context.getString(R.string.presets_added, fresh.size)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().takeIf { it.size <= MAX_PRESET_BYTES }?.toString(Charsets.UTF_8)
            } ?: throw IllegalArgumentException("> 64 KB")
            addPreset(ShortcutPreset.parse(text, language))
        } catch (e: Exception) {
            presetMessage = context.getString(R.string.presets_import_failed, e.message ?: e.javaClass.simpleName)
        }
    }

    PageScaffold(stringResource(R.string.page_shortcuts), onBack) {
        Text(stringResource(R.string.shortcuts_intro), style = MaterialTheme.typography.bodyMedium)

        Section(stringResource(R.string.shortcuts_list)) {
            if (s.shortcuts.isEmpty()) Text(stringResource(R.string.shortcuts_empty))
            s.shortcuts.forEachIndexed { index, shortcut ->
                if (index > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(shortcut.describe(), style = MaterialTheme.typography.titleSmall)
                        val action = actionName(shortcut.action)
                        Text(
                            if (shortcut.action == ShortcutAction.INSERT_TEXT) "$action: «${shortcut.text}»" else action,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = {
                        update { it.copy(shortcuts = it.shortcuts.filterIndexed { i, _ -> i != index }) }
                    }) { Text(stringResource(R.string.delete)) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(onClick = { editing = true }) { Text(stringResource(R.string.shortcut_add)) }
                ActionButton(onClick = { update { it.copy(shortcuts = Shortcut.defaults) } }) {
                    Text(stringResource(R.string.shortcuts_restore_defaults))
                }
            }
        }

        Section(stringResource(R.string.presets_title)) {
            Text(stringResource(R.string.presets_intro), style = MaterialTheme.typography.bodySmall)
            presetMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            presets.forEachIndexed { index, preset ->
                if (index > 0) HorizontalDivider()
                PresetRow(preset, onAdd = { addPreset(preset) })
            }
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(onClick = { filePicker.launch(arrayOf("text/plain", "*/*")) }) {
                    Text(stringResource(R.string.presets_import))
                }
                ActionButton(onClick = { Links.open(context, Links.SHORTCUT_PRESETS) }) {
                    Text(stringResource(R.string.presets_more))
                }
            }
        }
    }

    if (editing) {
        ShortcutEditor(
            existing = s.shortcuts,
            onDismiss = { editing = false },
            onSave = { shortcut ->
                update { it.copy(shortcuts = it.shortcuts + shortcut) }
                editing = false
            },
        )
    }
}

private const val MAX_PRESET_BYTES = 64 * 1024

/** Presets bundled from the repository's `shortcut-presets` folder; "general" first. */
private fun bundledPresets(context: Context, language: String): List<ShortcutPreset> =
    (context.assets.list("") ?: emptyArray())
        .filter { it.endsWith(".txt") }
        .sortedBy { if (it == "general.txt") "" else it }
        .mapNotNull { name ->
            runCatching {
                ShortcutPreset.parse(context.assets.open(name).bufferedReader().use { it.readText() }, language)
            }.getOrNull()
        }

@Composable
private fun PresetRow(preset: ShortcutPreset, onAdd: () -> Unit) {
    var open by rememberSaveable(preset.title) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(preset.title, style = MaterialTheme.typography.titleSmall)
        Text(preset.about, style = MaterialTheme.typography.bodySmall)
        if (open) {
            for (tip in preset.tips) Text("• $tip", style = MaterialTheme.typography.bodyMedium)
            for (sc in preset.shortcuts) {
                val action = actionName(sc.action)
                val detail = if (sc.action == ShortcutAction.INSERT_TEXT) "«${sc.text.replace("\n", "↵")}»" else action
                Text("• ${sc.describe()} — $detail", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(onClick = { open = !open }) {
                Text(stringResource(if (open) R.string.presets_hide_tips else R.string.presets_show_tips))
            }
            if (preset.shortcuts.isNotEmpty()) {
                ActionButton(onClick = onAdd) { Text(stringResource(R.string.presets_add, preset.shortcuts.size)) }
            }
        }
    }
}

@Composable
private fun ShortcutEditor(existing: List<Shortcut>, onDismiss: () -> Unit, onSave: (Shortcut) -> Unit) {
    var ctrl by remember { mutableStateOf(true) }
    var alt by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    var meta by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf<String?>("L") }
    var action by remember { mutableStateOf(ShortcutAction.SWITCH_LANGUAGE) }
    var text by remember { mutableStateOf("") }
    var keyMenu by remember { mutableStateOf(false) }

    val candidate = Shortcut(ctrl, alt, shift, meta, key, action, text)
    val duplicate = existing.any { it.ctrl == ctrl && it.alt == alt && it.shift == shift && it.meta == meta && it.key == key }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shortcut_add)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.shortcut_modifiers), style = MaterialTheme.typography.titleSmall)
                CheckRow("Ctrl", ctrl) { ctrl = it }
                CheckRow("Alt", alt) { alt = it }
                CheckRow("Shift", shift) { shift = it }
                CheckRow("Win", meta) { meta = it }
                Text(stringResource(R.string.shortcut_key), style = MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(onClick = { keyMenu = true }) {
                        Text(key?.let { ShortcutKeys.label(it) } ?: stringResource(R.string.shortcut_key_none))
                    }
                    DropdownMenu(expanded = keyMenu, onDismissRequest = { keyMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shortcut_key_none)) },
                            onClick = { key = null; keyMenu = false },
                        )
                        for (id in ShortcutKeys.ids) {
                            DropdownMenuItem(text = { Text(ShortcutKeys.label(id)) }, onClick = { key = id; keyMenu = false })
                        }
                    }
                }
                Text(stringResource(R.string.shortcut_action), style = MaterialTheme.typography.titleSmall)
                for (a in ShortcutAction.entries) {
                    RadioRow(actionName(a), action == a) { action = a }
                }
                if (action == ShortcutAction.INSERT_TEXT) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.shortcut_text)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(candidate.describe().ifEmpty { "—" }, style = MaterialTheme.typography.titleMedium)
                when {
                    !candidate.isValid -> Text(stringResource(R.string.shortcut_invalid), color = MaterialTheme.colorScheme.error)
                    duplicate -> Text(stringResource(R.string.shortcut_duplicate), color = MaterialTheme.colorScheme.error)
                    candidate.overridesBuiltIn -> Text(stringResource(R.string.shortcut_overrides), style = MaterialTheme.typography.bodySmall)
                    key == null -> Text(stringResource(R.string.shortcut_modifier_only_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = candidate.isValid && !duplicate, onClick = { onSave(candidate) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
