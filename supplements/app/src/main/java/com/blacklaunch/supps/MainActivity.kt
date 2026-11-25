package com.blacklaunch.supps

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
import com.blacklaunch.supps.settings.AppSettings
import com.blacklaunch.supps.ui.AppRoot
import com.blacklaunch.supps.ui.AppStyle
import com.blacklaunch.supps.ui.FrameRate
import com.blacklaunch.supps.ui.Haptics
import com.blacklaunch.supps.ui.LocalHaptics
import com.blacklaunch.supps.ui.SheetTheme
import com.blacklaunch.supps.ui.StatusBar
import com.blacklaunch.supps.ui.theme.SuppsTheme

private const val MAX_FONT_SCALE = 1.15f

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** Вибрана в Налаштуваннях мова — для всієї програми. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        AppSettings.languageOverride(newBase)?.let { applyOverrideConfiguration(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = AppSettings(this)
        AppStyle.glass = settings.glass
        StatusBar.hidden = settings.hideStatusBar
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        FrameRate.boost(window)
        setContent {
            SuppsTheme {
                SheetTheme(enabled = !AppStyle.glass) {
                    val view = LocalView.current
                    LaunchedEffect(StatusBar.hidden) { StatusBar.apply(window) }
                    // Дуже великий системний шрифт ламав би рівні ряди кнопок — обмежуємо масштаб.
                    val density = LocalDensity.current
                    val capped = Density(density.density, density.fontScale.coerceAtMost(MAX_FONT_SCALE * if (AppStyle.glass) 1f else 1.22f))
                    CompositionLocalProvider(LocalHaptics provides Haptics(view, enabled = true), LocalDensity provides capped) {
                        AppRoot(vm, onLanguageChanged = { recreate() })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Повернулись після ночі — «сьогодні» могло змінитися.
        vm.refreshToday()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) StatusBar.apply(window)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        FrameRate.boost(window)
        return super.dispatchTouchEvent(ev)
    }
}
