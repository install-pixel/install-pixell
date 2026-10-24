package com.offlinekeyboard.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinekeyboard.R
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.dictionary.UserDictionaryStore

@Composable
internal fun DictionaryPage(s: KeyboardSettings, update: UpdateSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { UserDictionaryStore.get(context) }
    val version by store.changes.collectAsStateWithLifecycle()
    var language by remember { mutableStateOf(s.lastLanguage) }
    var query by remember { mutableStateOf("") }
    var newWord by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf(false) }

    PageScaffold(stringResource(R.string.page_dictionary), onBack) {
        Section(stringResource(R.string.dictionary_settings)) {
            SwitchRow(stringResource(R.string.dictionary_learn), s.learnWords) { v -> update { it.copy(learnWords = v) } }
            Text(stringResource(R.string.dictionary_learn_hint), style = MaterialTheme.typography.bodySmall)
            SwitchRow(stringResource(R.string.dictionary_suggestions), s.showSuggestions) { v ->
                update { it.copy(showSuggestions = v) }
            }
            Text(stringResource(R.string.dictionary_suggestions_hint), style = MaterialTheme.typography.bodySmall)
        }

        Section(stringResource(R.string.dictionary_words)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in Language.entries) {
                    val count = remember(version, l) { store.dictionary.size(l) }
                    FilterChip(selected = language == l, onClick = { language = l }, label = { Text("${l.code} ($count)") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it; addError = false },
                    label = { Text(stringResource(R.string.dictionary_add_word)) },
                    singleLine = true,
                    isError = addError,
                    modifier = Modifier.weight(1f),
                )
                InlineButton(onClick = {
                    if (store.dictionary.learn(language, newWord.trim(), System.currentTimeMillis(), weight = 5)) {
                        store.changed()
                        newWord = ""
                    } else {
                        addError = true
                    }
                }) { Text(stringResource(R.string.add)) }
            }
            if (addError) Text(stringResource(R.string.dictionary_add_invalid), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val entries = remember(version, language, query) {
                store.dictionary.entries(language).filter { query.isBlank() || it.word.contains(query.trim(), ignoreCase = true) }
            }
            if (entries.isEmpty()) Text(stringResource(R.string.dictionary_empty), style = MaterialTheme.typography.bodySmall)
            entries.take(MAX_SHOWN).forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.word, modifier = Modifier.weight(1f))
                    Text("×${e.count}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        store.dictionary.remove(language, e.word)
                        store.changed()
                    }) { Text(stringResource(R.string.delete)) }
                }
            }
            if (entries.size > MAX_SHOWN) {
                Text(stringResource(R.string.dictionary_more, entries.size - MAX_SHOWN), style = MaterialTheme.typography.bodySmall)
            }
            ActionButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.dictionary_clear)) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.dictionary_clear)) },
            text = { Text(stringResource(R.string.dictionary_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    store.dictionary.clear()
                    store.changed()
                    store.saveNow()
                    confirmClear = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private const val MAX_SHOWN = 200
