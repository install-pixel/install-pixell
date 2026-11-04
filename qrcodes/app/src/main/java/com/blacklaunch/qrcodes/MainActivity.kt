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

private const val MAX_FONT_SCALE = 1.15f

class MainActivity : ComponentActivity() {
    private val vm: QrViewModel by viewModels()

    /** Текст, яким поділились («Поділитися» → QRaft). */
    private var shared by mutableStateOf<String?>(null)

    /** Вибрана в Налаштуваннях мова — для всієї програми. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        AppSettings.languageOverride(newBase)?.let { applyOverrideConfiguration(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppStyle.glass = AppSettings(this).glass
        com.blacklaunch.qrcodes.ui.StatusBar.hidden = AppSettings(this).hideStatusBar
        com.blacklaunch.qrcodes.ui.QrBrightness.init(AppSettings(this))
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        // Плавний старт (поява першого екрана) — на високій частоті; далі — лише поки щось рухається.
        com.blacklaunch.qrcodes.ui.FrameRate.boost(window)
        if (savedInstanceState == null) shared = sharedText(intent)
        setContent {
            QrTheme {
                // Стиль — як у Black Glass: «Аркуш» (від руки) або «Скло»; перемикається в Налаштуваннях.
                SheetTheme(enabled = !AppStyle.glass) {
                    val view = LocalView.current
                    // Рядок стану — за Налаштуваннями (міняється одразу).
                    androidx.compose.runtime.LaunchedEffect(com.blacklaunch.qrcodes.ui.StatusBar.hidden) { com.blacklaunch.qrcodes.ui.StatusBar.apply(window) }
                    // Дуже великий шрифт у системі ламав би рівні ряди кнопок і панелі сталої висоти — обмежуємо масштаб.
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val capped = androidx.compose.ui.unit.Density(density.density, density.fontScale.coerceAtMost(MAX_FONT_SCALE))
                    CompositionLocalProvider(LocalHaptics provides Haptics(view, enabled = true), androidx.compose.ui.platform.LocalDensity provides capped) {
                        AppRoot(vm, shared, onSharedConsumed = { shared = null }, onLanguageChanged = { recreate() })
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Повернулись (зі шторки, з іншої програми) — рядок стану знову як задано.
        if (hasFocus) com.blacklaunch.qrcodes.ui.StatusBar.apply(window)
    }

    /** Дотик — висока частота екрана на час руху (у спокої програма її не тримає, див. FrameRate). */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        com.blacklaunch.qrcodes.ui.FrameRate.boost(window)
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedText(intent)?.let { shared = it }
    }

    private fun sharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() } else null
}
