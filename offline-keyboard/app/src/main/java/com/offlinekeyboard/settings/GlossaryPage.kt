package com.offlinekeyboard.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinekeyboard.R
import com.offlinekeyboard.core.translation.Glossary
import com.offlinekeyboard.translation.GlossaryStore

/** The translation glossary: a plain text the user edits, with a warehouse example to start from. */
@Composable
internal fun GlossaryPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { GlossaryStore.get(context) }
    var text by rememberSaveable { mutableStateOf(store.text.value) }
    var message by remember { mutableStateOf<String?>(null) }

    fun append(more: String) {
        text = if (text.isBlank()) more else text.trimEnd() + "\n\n" + more
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        message = try {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().takeIf { it.size <= MAX_BYTES }?.toString(Charsets.UTF_8)
            } ?: throw IllegalArgumentException("> 256 KB")
            append(content)
            null
        } catch (e: Exception) {
            context.getString(R.string.presets_import_failed, e.message ?: e.javaClass.simpleName)
        }
    }

    PageScaffold(stringResource(R.string.page_glossary), onBack) {
        Text(stringResource(R.string.glossary_intro), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.glossary_format), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; message = null },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            label = { Text(stringResource(R.string.glossary_field)) },
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(onClick = {
                store.save(text)
                message = context.getString(R.string.glossary_saved, Glossary.parse(text).size)
            }) { Text(stringResource(R.string.save)) }
            ActionButton(onClick = {
                runCatching { context.assets.open(GlossaryStore.EXAMPLE_ASSET).bufferedReader().use { it.readText() } }
                    .onSuccess { append(it) }
            }) { Text(stringResource(R.string.glossary_add_example)) }
            ActionButton(onClick = { filePicker.launch(arrayOf("text/plain", "*/*")) }) {
                Text(stringResource(R.string.presets_import))
            }
        }
    }
}

private const val MAX_BYTES = 256 * 1024
