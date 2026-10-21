package com.offlinekeyboard.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.offlinekeyboard.R
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.translation.TranslationDirection
import com.offlinekeyboard.models.ModelEvents
import com.offlinekeyboard.models.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")

@Composable
internal fun ModelsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ModelStore(context) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var speechTarget by remember { mutableStateOf(Language.UK) }
    var micGranted by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        micGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }

    val importDone = stringResource(R.string.models_import_done)
    val importFailed = stringResource(R.string.models_import_failed)

    fun runImport(label: String, block: () -> String) {
        busy = label
        message = null
        scope.launch {
            message = try {
                val name = withContext(Dispatchers.IO) { block() }
                "$importDone: $name"
            } catch (e: Exception) {
                "$importFailed: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = null
            refresh++
            ModelEvents.modelsChanged()
        }
    }

    val speechPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val lang = speechTarget
            runImport(lang.code) { store.importSpeech(uri, lang).name }
        }
    }
    val translationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runImport("MT") { store.importTranslation(uri).let { (d, m) -> "${d.source.code}→${d.target.code} ${m.name}" } }
    }
    val micRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }

    PageScaffold(stringResource(R.string.page_models), onBack) {
        Text(stringResource(R.string.models_intro), style = MaterialTheme.typography.bodyMedium)

        busy?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.models_importing, it))
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Section(stringResource(R.string.models_speech)) {
            StatusLine(stringResource(R.string.mic_permission), micGranted)
            if (!micGranted) {
                OutlinedButton(onClick = { micRequest.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text(stringResource(R.string.mic_permission_grant))
                }
            }
            Text(stringResource(R.string.models_speech_hint), style = MaterialTheme.typography.bodySmall)
            for (language in Language.entries) {
                HorizontalDivider()
                val model = remember(refresh, language) { store.speechModel(language) }
                ModelRow(
                    title = languageName(language),
                    status = model?.let { "${it.name} · ${formatMb(it.sizeBytes)}" } ?: stringResource(R.string.models_not_installed),
                    installed = model != null,
                    enabled = busy == null,
                    onImport = {
                        speechTarget = language
                        speechPicker.launch(ZIP_TYPES)
                    },
                    onDelete = {
                        store.deleteSpeech(language)
                        refresh++
                        ModelEvents.modelsChanged()
                    },
                )
            }
        }

        Section(stringResource(R.string.models_translation)) {
            Text(stringResource(R.string.models_translation_hint), style = MaterialTheme.typography.bodySmall)
            for (direction in TranslationDirection.entries) {
                HorizontalDivider()
                val model = remember(refresh, direction) { store.translationModel(direction) }
                ModelRow(
                    title = "${languageName(direction.source)} → ${languageName(direction.target)}",
                    status = model?.let { "${it.name} · ${formatMb(it.sizeBytes)}" } ?: stringResource(R.string.models_not_installed),
                    installed = model != null,
                    enabled = busy == null,
                    onImport = { translationPicker.launch(ZIP_TYPES) },
                    onDelete = {
                        store.deleteTranslation(direction)
                        refresh++
                        ModelEvents.modelsChanged()
                    },
                )
            }
        }

        Section(stringResource(R.string.models_where)) {
            Text(stringResource(R.string.models_where_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ModelRow(
    title: String,
    status: String,
    installed: Boolean,
    enabled: Boolean,
    onImport: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        StatusLine(title, installed)
        Text(status, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = enabled, onClick = onImport) {
                Text(stringResource(if (installed) R.string.models_replace else R.string.models_import))
            }
            if (installed) TextButton(enabled = enabled, onClick = onDelete) { Text(stringResource(R.string.delete)) }
        }
    }
}
