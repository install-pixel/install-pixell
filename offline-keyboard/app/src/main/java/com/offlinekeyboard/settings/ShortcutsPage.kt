package com.offlinekeyboard.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.offlinekeyboard.R
import com.offlinekeyboard.core.Shortcut
import com.offlinekeyboard.core.ShortcutAction
import com.offlinekeyboard.core.ShortcutKeys

@Composable
internal fun actionName(action: ShortcutAction): String = stringResource(
    when (action) {
        ShortcutAction.SWITCH_LANGUAGE -> R.string.action_switch_language
        ShortcutAction.SWITCH_MODE -> R.string.action_switch_mode
        ShortcutAction.VOICE_INPUT -> R.string.action_voice
        ShortcutAction.TOGGLE_TRANSLATE -> R.string.action_translate
        ShortcutAction.HIDE_KEYBOARD -> R.string.action_hide
        ShortcutAction.INSERT_TEXT -> R.string.action_insert_text
    },
)

@Composable
internal fun ShortcutsPage(s: KeyboardSettings, update: UpdateSettings, onBack: () -> Unit) {
    var editing by remember { mutableStateOf(false) }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = true }) { Text(stringResource(R.string.shortcut_add)) }
                OutlinedButton(onClick = { update { it.copy(shortcuts = Shortcut.defaults) } }) {
                    Text(stringResource(R.string.shortcuts_restore_defaults))
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
