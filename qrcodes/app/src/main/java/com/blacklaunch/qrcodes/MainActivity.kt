package com.blacklaunch.qrcodes

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
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
import com.blacklaunch.qrcodes.ui.AppStyle
import com.blacklaunch.qrcodes.settings.AppSettings
import com.blacklaunch.qrcodes.ui.theme.QrTheme

class MainActivity : ComponentActivity() {
    private val vm: QrViewModel by viewModels()

    /** Текст, яким поділились («Поділитися» → QRaft). */
    private var shared by mutableStateOf<String?>(null)

    /** Вибрана в Налаштуваннях мова — для всієї програми. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppStyle.glass = AppSettings(this).glass
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        useBestRefreshRate()
        if (savedInstanceState == null) shared = sharedText(intent)
        setContent {
            QrTheme {
                // Стиль — як у Black Glass: «Аркуш» (від руки) або «Скло»; перемикається в Налаштуваннях.
                SheetTheme(enabled = !AppStyle.glass) {
                    val view = LocalView.current
                    CompositionLocalProvider(LocalHaptics provides Haptics(view, enabled = true)) {
                        AppRoot(vm, shared, onSharedConsumed = { shared = null }, onLanguageChanged = { recreate() })
                    }
                }
            }
        }
    }

    /**
     * Частота екрана — найвища, яку дозволяє телефон: стоїть у налаштуваннях 120 Гц — буде 120, стоїть 60 — система
     * сама не дасть більше. Без цього багато оболонок тримають звичайні програми на 60 Гц.
     */
    private fun useBestRefreshRate() {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        display ?: return
        val now = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.also {
            it.preferredDisplayModeId = best.modeId
            it.preferredRefreshRate = best.refreshRate
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedText(intent)?.let { shared = it }
    }

    private fun sharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() } else null
}
