package com.blacklaunch.qrcodes

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import com.blacklaunch.qrcodes.ui.AppRoot
import com.blacklaunch.qrcodes.ui.Haptics
import com.blacklaunch.qrcodes.ui.LocalHaptics
import com.blacklaunch.qrcodes.ui.SheetTheme
import com.blacklaunch.qrcodes.ui.theme.QrTheme

class MainActivity : ComponentActivity() {
    private val vm: QrViewModel by viewModels()

    /** Текст, яким поділились («Поділитися» → «QR-коди»). */
    private var shared by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) shared = sharedText(intent)
        setContent {
            QrTheme {
                // Поки що одна тема — «Аркуш» (рукописна, чорна), як у Black Glass.
                SheetTheme(enabled = true) {
                    val view = LocalView.current
                    CompositionLocalProvider(LocalHaptics provides Haptics(view, enabled = true)) {
                        AppRoot(vm, shared, onSharedConsumed = { shared = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedText(intent)?.let { shared = it }
    }

    private fun sharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() } else null
}
