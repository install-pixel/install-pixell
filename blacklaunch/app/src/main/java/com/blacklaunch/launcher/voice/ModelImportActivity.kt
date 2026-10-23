package com.blacklaunch.launcher.voice

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.blacklaunch.launcher.BlackLaunchApp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.ui.theme.BlackLaunchTheme
import com.blacklaunch.launcher.ui.theme.Oled
import kotlinx.coroutines.launch

/**
 * Відкривається, коли завантажений zip моделі відкривають «за допомогою BlackLaunch»,
 * діляться кількома файлами або вибирають уже завантажені (наприклад, для клавіатури).
 * Мову визначає вміст архіву, а не назва файлу.
 */
class ModelImportActivity : ComponentActivity() {
    private sealed interface ImportState {
        data object Working : ImportState

        /** [installed] — мови встановлених моделей; [errors] — причини для файлів, що не встановились. */
        data class Done(val installed: List<String>, val errors: List<ImportError>) : ImportState
    }

    private var state by mutableStateOf<ImportState>(ImportState.Working)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlackLaunchTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Oled.Black)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (val current = state) {
                        ImportState.Working -> {
                            CircularProgressIndicator(color = Oled.Accent)
                            Text(stringResource(R.string.import_title), color = Oled.TextPrimary, fontSize = 18.sp)
                        }
                        is ImportState.Done -> {
                            if (current.installed.isNotEmpty()) {
                                val names = current.installed.map { stringResource(languageName(it)) }
                                Text(
                                    stringResource(R.string.import_done, names.joinToString(", ")),
                                    color = Oled.TextPrimary,
                                    fontSize = 18.sp,
                                )
                            }
                            current.errors.distinct().forEach { error ->
                                Text(stringResource(error.message()), color = Oled.TextSecondary, fontSize = 16.sp)
                            }
                            if (current.errors.isNotEmpty()) {
                                // Якщо «Відкрити за допомогою» не дало доступу до файлу — вибрати його вручну.
                                TextButton(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                                    Text(stringResource(R.string.import_choose_file), color = Oled.Accent)
                                }
                            }
                            TextButton(onClick = ::finish) { Text(stringResource(R.string.import_ok), color = Oled.Accent) }
                        }
                    }
                }
            }
        }
        if (savedInstanceState == null) importFrom(intent)
    }

    /** Кілька файлів за раз: мова кожного визначається за вмістом, а не за назвою. */
    private fun importFrom(intent: Intent) = importUris(sourceUris(intent))

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importUris(uris)
    }

    private fun importUris(uris: List<Uri>) {
        state = ImportState.Working
        lifecycleScope.launch {
            val store = (application as BlackLaunchApp).voiceModelStore
            val results = uris.map { store.import(it) }
            val errors = results.mapNotNull { result ->
                result.exceptionOrNull()?.let { (it as? ModelImportException)?.reason ?: ImportError.OTHER }
            }
            state = ImportState.Done(
                installed = results.mapNotNull { it.getOrNull() }.distinct(),
                errors = if (uris.isEmpty()) listOf(ImportError.READ) else errors,
            )
        }
    }

    private fun sourceUris(intent: Intent): List<Uri> {
        val clip = intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }
        val stream: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.streamExtra())
            Intent.ACTION_SEND_MULTIPLE -> intent.streamListExtra()
            else -> listOfNotNull(intent.data)
        }
        return (stream + clip.orEmpty()).distinct()
    }

    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun Intent.streamListExtra(): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }

    companion object {
        private fun ImportError.message(): Int = when (this) {
            ImportError.READ -> R.string.import_error_read
            ImportError.NOT_MODEL -> R.string.import_failed
            ImportError.LANGUAGE -> R.string.import_error_language
            ImportError.NO_SPACE -> R.string.import_error_space
            ImportError.OTHER -> R.string.import_error_other
        }

        fun languageName(language: String): Int = when (language) {
            VoiceLanguages.UKRAINIAN -> R.string.lang_uk
            VoiceLanguages.POLISH -> R.string.lang_pl
            else -> R.string.lang_en
        }
    }
}
