package com.offlinekeyboard.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.offlinekeyboard.R
import com.offlinekeyboard.keyboard.Accent
import com.offlinekeyboard.models.ModelEvents
import com.offlinekeyboard.models.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens a downloaded model zip straight from the browser ("Open" after the download, or "Open with" /
 * "Share" from a file manager) and installs it in the right place: one tap, no file names to match.
 */
class ModelInstallActivity : ComponentActivity() {

    private var state by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = urisOf(intent)
        setContent {
            MaterialTheme(colorScheme = settingsColors(isSystemInDarkTheme(), Accent.SKY)) {
                Surface(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.models_install_title), style = MaterialTheme.typography.titleLarge)
                        if (busy) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.models_install_running))
                            }
                        }
                        state?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        if (!busy) {
                            Button(onClick = { finish() }) { Text(stringResource(R.string.setup_done)) }
                        }
                    }
                }
            }
        }
        if (savedInstanceState != null) return
        if (uris.isEmpty()) {
            busy = false
            state = getString(R.string.models_import_failed)
            return
        }
        val store = ModelStore(this)
        lifecycleScope.launch {
            val lines = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    runCatching { describe(this@ModelInstallActivity, store.importAny(uri)) }
                        .getOrElse { e -> getString(R.string.models_import_failed) + ": " + (e.message ?: e.javaClass.simpleName) }
                }
            }
            ModelEvents.modelsChanged()
            state = lines.joinToString("\n")
            busy = false
        }
    }

    @Suppress("DEPRECATION") // getParcelableExtra(name, class) needs API 33.
    private fun urisOf(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_VIEW -> listOfNotNull(intent.data)
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM),
        )
        Intent.ACTION_SEND_MULTIPLE -> (
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            ).orEmpty()
        else -> emptyList()
    }
}
