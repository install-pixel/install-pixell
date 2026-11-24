package com.offlinekeyboard.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinekeyboard.R
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.dictionary.UserDictionaryStore
import kotlinx.coroutines.launch

@Composable
internal fun DictionaryPage(s: KeyboardSettings, update: UpdateSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { UserDictionaryStore.get(context) }
    val version by store.changes.collectAsStateWithLifecycle()
    var language by remember { mutableStateOf(s.lastLanguage) }
    var query by remember { mutableStateOf("") }
    var newWord by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmFragments by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf(false) }
    var fileMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            fileMessage = runCatching { Backup.importDictionary(context, uri, language) }
                .map { context.getString(R.string.dictionary_imported, it.added, it.skipped) }
                .getOrElse { context.getString(R.string.file_read_failed) }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            fileMessage = runCatching { Backup.exportDictionary(context, uri) }
                .map { context.getString(R.string.file_saved) }
                .getOrElse { context.getString(R.string.file_write_failed) }
        }
    }

    PageScaffold(stringResource(R.string.page_dictionary), onBack) {
        Section(stringResource(R.string.dictionary_settings)) {
            // Both are part of «Підказки й виправлення» («Текст і клавіші»): pale while that is off.
            if (!s.assistEnabled) {
                Text(stringResource(R.string.dictionary_assist_off), style = MaterialTheme.typography.bodySmall, color = Tone.Danger)
            }
            SwitchRow(stringResource(R.string.dictionary_learn), s.learnWords, enabled = s.assistEnabled) { v -> update { it.copy(learnWords = v) } }
            Text(stringResource(R.string.dictionary_learn_hint), style = MaterialTheme.typography.bodySmall)
            SwitchRow(stringResource(R.string.dictionary_suggestions), s.showSuggestions, enabled = s.assistEnabled) { v ->
                update { it.copy(showSuggestions = v) }
            }
            Text(stringResource(R.string.dictionary_suggestions_hint), style = MaterialTheme.typography.bodySmall)
        }

        Section(stringResource(R.string.dictionary_words)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in Language.entries) {
                    val count = remember(version, l) { store.dictionary.size(l) }
                    LanguageChip("${l.code} ($count)", language == l) { language = l }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it; addError = false },
                    // A hint inside the field: a floating label cut the frame (as the try field).
                    placeholder = { Text(stringResource(R.string.dictionary_add_word)) },
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
            // The search and the words under it are one block: on the sketch's pages they never part (the window of
            // words stood alone at the top of the next page, its first word faded, and it was not clear what it was).
            androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val entries = remember(version, language, query) {
                store.dictionary.entries(language).filter { query.isBlank() || it.word.contains(query.trim(), ignoreCase = true) }
            }
            if (entries.isEmpty()) Text(stringResource(R.string.dictionary_empty), style = MaterialTheme.typography.bodySmall)
            if (entries.isNotEmpty()) {
                // Every word, in a small window of its own scrolled inside (a lazy list: thousands of words
                // stay smooth), so a word typed by mistake can be found and deleted without knowing it.
                val listState = rememberLazyListState()
                // The sketch: the words written one per ruled pencil line, five lines high, scrolled inside (the
                // lines go with the words, as in the try field); a notebook of thousands of words is not turned.
                val sheet = LocalLook.current == Look.SHEET
                val lineHeight = 44.dp
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (sheet) lineHeight * 5 else 320.dp)
                        .fadingEdges(listState),
                ) {
                    itemsIndexed(entries, key = { _, e -> e.word }) { i, e ->
                        if (i > 0 && !sheet) RowDivider()
                        // A word typed once (and not a known word) is kept but not suggested yet: shown dimmer.
                        val waiting = !store.dictionary.isTrusted(language, e.word.lowercase(language.locale), e)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .then(
                                    if (sheet) {
                                        Modifier
                                            .heightIn(min = lineHeight)
                                            .drawBehind {
                                                pencilStroke(size.height - 3.dp.toPx(), size.width * 0.01f, size.width * 0.99f, seed = 31 + i % 7, alpha = 0.45f)
                                            }
                                    } else {
                                        Modifier
                                    },
                                )
                                .alpha(if (waiting) 0.5f else 1f),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.word)
                                if (waiting) Text(stringResource(R.string.dictionary_waiting), style = MaterialTheme.typography.bodySmall)
                            }
                            Text("×${e.count}", style = MaterialTheme.typography.bodySmall)
                            TextButton(colors = toneTextColors(ButtonTone.DANGER), onClick = {
                                store.dictionary.remove(language, e.word)
                                store.changed()
                            }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
                // How many more words are below the window: plain that it goes on.
                val more by androidx.compose.runtime.remember(entries) {
                    androidx.compose.runtime.derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        (entries.size - last - 1).coerceAtLeast(0)
                    }
                }
                if (more > 0) {
                    Text(
                        stringResource(R.string.dictionary_more, more),
                        style = MaterialTheme.typography.bodySmall,
                        color = Tone.TextSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            }
            // Pieces of common words learned by mistake («швидк» for «швидко»): listed, removed only when confirmed.
            val fragments = remember(version) {
                Language.entries.flatMap { l ->
                    store.dictionary.entries(l).map { it.word }
                        .filter { com.offlinekeyboard.core.dictionary.BaseDictionary.isFragment(l, it) }
                        .map { l to it }
                }
            }
            if (fragments.isNotEmpty()) {
                ActionButton(onClick = { confirmFragments = true }) {
                    Text(stringResource(R.string.dictionary_fragments, fragments.size))
                }
            }
            if (confirmFragments) {
                AlertDialog(
                    onDismissRequest = { confirmFragments = false },
                    title = { Text(stringResource(R.string.dictionary_fragments, fragments.size)) },
                    text = {
                        Text(stringResource(R.string.dictionary_fragments_confirm, fragments.joinToString(", ") { it.second }))
                    },
                    confirmButton = {
                        TextButton(colors = toneTextColors(ButtonTone.DANGER), onClick = {
                            fragments.forEach { (l, w) -> store.dictionary.remove(l, w) }
                            store.changed()
                            store.saveNow()
                            confirmFragments = false
                        }) { Text(stringResource(R.string.delete)) }
                    },
                    dismissButton = { TextButton(onClick = { confirmFragments = false }) { Text(stringResource(R.string.cancel)) } },
                )
            }
            ActionButton(tone = ButtonTone.DANGER, onClick = { confirmClear = true }) { Text(stringResource(R.string.dictionary_clear)) }
        }

        Section(stringResource(R.string.dictionary_file)) {
            Text(stringResource(R.string.dictionary_file_text, language.code), style = MaterialTheme.typography.bodySmall)
            ActionButton(onClick = { importer.launch(arrayOf("text/plain", "application/json", "application/octet-stream")) }) {
                Text(stringResource(R.string.dictionary_import))
            }
            ActionButton(onClick = { exporter.launch("offkey-dictionary.json") }) { Text(stringResource(R.string.dictionary_export)) }
            fileMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.dictionary_clear)) },
            text = { Text(stringResource(R.string.dictionary_clear_confirm)) },
            confirmButton = {
                TextButton(colors = toneTextColors(ButtonTone.DANGER), onClick = {
                    store.dictionary.clear()
                    store.pairs.clear()
                    store.changed()
                    store.saveNow()
                    confirmClear = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * A language of the dictionary to show. The sketch: its name written, the chosen one circled in pencil (the
 * glass chip's gloss looked like lacquer on the paper); the other styles: their chip.
 */
@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (LocalLook.current == Look.SHEET) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .then(if (selected) Modifier.pencilFrame(14.dp, label.length, alpha = 0.9f) else Modifier)
                .clickable(interactionSource = null, indication = null, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(label, color = Tone.TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    } else {
        FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
    }
}
