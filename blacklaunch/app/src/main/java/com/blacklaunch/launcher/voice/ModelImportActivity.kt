package com.blacklaunch.launcher.voice

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
 * Відкривається, коли завантажений zip моделі відкривають «за допомогою BlackLaunch».
 * Мову визначає вміст архіву, а не назва файлу.
 */
class ModelImportActivity : ComponentActivity() {
    private sealed interface ImportState {
        data object Working : ImportState
        data class Done(val language: String) : ImportState
        data object Failed : ImportState
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
                            Text(
                                stringResource(R.string.import_done, stringResource(languageName(current.language))),
                                color = Oled.TextPrimary,
                                fontSize = 18.sp,
                            )
                            TextButton(onClick = ::finish) { Text(stringResource(R.string.import_ok), color = Oled.Accent) }
                        }
                        ImportState.Failed -> {
                            Text(stringResource(R.string.import_failed), color = Oled.TextPrimary, fontSize = 18.sp)
                            TextButton(onClick = ::finish) { Text(stringResource(R.string.import_ok), color = Oled.Accent) }
                        }
                    }
                }
            }
        }
        if (savedInstanceState == null) importFrom(intent)
    }

    private fun importFrom(intent: Intent) {
        val uri = sourceUri(intent)
        if (uri == null) {
            state = ImportState.Failed
            return
        }
        lifecycleScope.launch {
            val result = (application as BlackLaunchApp).voiceModelStore.import(uri)
            state = result.fold(onSuccess = { ImportState.Done(it) }, onFailure = { ImportState.Failed })
        }
    }

    private fun sourceUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        else -> intent.data
    }

    companion object {
        fun languageName(language: String): Int = when (language) {
            VoiceLanguages.UKRAINIAN -> R.string.lang_uk
            VoiceLanguages.POLISH -> R.string.lang_pl
            else -> R.string.lang_en
        }
    }
}
