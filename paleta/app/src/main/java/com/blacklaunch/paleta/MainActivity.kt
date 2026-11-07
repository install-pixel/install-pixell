package com.blacklaunch.paleta

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import com.blacklaunch.paleta.settings.AppSettings
import com.blacklaunch.paleta.ui.AppRoot
import com.blacklaunch.paleta.ui.AppStyle
import com.blacklaunch.paleta.ui.FrameRate
import com.blacklaunch.paleta.ui.Haptics
import com.blacklaunch.paleta.ui.LocalHaptics
import com.blacklaunch.paleta.ui.SheetTheme
import com.blacklaunch.paleta.ui.StatusBar
import com.blacklaunch.paleta.ui.theme.PaletaTheme

private const val MAX_FONT_SCALE = 1.15f

class MainActivity : ComponentActivity() {
    private val vm: PaletaViewModel by viewModels()

    /** Вибрана в Налаштуваннях мова — для всієї програми (типово — як у телефоні). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        AppSettings.languageOverride(newBase)?.let { applyOverrideConfiguration(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = AppSettings(this)
        AppStyle.glass = settings.glass
        StatusBar.hidden = settings.hideStatusBar
        com.blacklaunch.paleta.ui.FormSigner.name = settings.signer
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        // Плавний старт — на високій частоті; далі — лише поки щось рухається.
        FrameRate.boost(window)
        setContent {
            PaletaTheme {
                // Стиль — як у Black Glass і QRaft: «Скло» або «Аркуш» (від руки); перемикається в Налаштуваннях.
                SheetTheme(enabled = !AppStyle.glass) {
                    val view = LocalView.current
                    // Рядок стану — за Налаштуваннями (типово сховано; з'являється свайпом від верхнього краю).
                    LaunchedEffect(StatusBar.hidden) { StatusBar.apply(window) }
                    // Дуже великий шрифт у системі ламав би рівні ряди кнопок — обмежуємо масштаб.
                    val density = LocalDensity.current
                    val capped = Density(density.density, density.fontScale.coerceAtMost(MAX_FONT_SCALE))
                    CompositionLocalProvider(LocalHaptics provides Haptics(view, enabled = true), LocalDensity provides capped) {
                        AppRoot(vm, onLanguageChanged = { recreate() })
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Повернулись (зі шторки, з іншої програми) — рядок стану знову як задано.
        if (hasFocus) StatusBar.apply(window)
    }

    /** Дотик — висока частота екрана на час руху (у спокої програма її не тримає). */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        FrameRate.boost(window)
        return super.dispatchTouchEvent(ev)
    }
}
