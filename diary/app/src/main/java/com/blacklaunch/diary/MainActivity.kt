package com.blacklaunch.diary

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
import com.blacklaunch.diary.settings.AppSettings
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.FrameRate
import com.blacklaunch.diary.ui.Haptics
import com.blacklaunch.diary.ui.HapticsSwitch
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetTheme
import com.blacklaunch.diary.ui.StatusBar
import com.blacklaunch.diary.ui.theme.DiaryTheme

private const val MAX_FONT_SCALE = 1.15f

class MainActivity : ComponentActivity() {
    private val vm: DiaryViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        AppSettings.languageOverride(newBase)?.let { applyOverrideConfiguration(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = AppSettings(this)
        AppStyle.glass = settings.glass
        StatusBar.hidden = settings.hideStatusBar
        HapticsSwitch.on = settings.haptics
        // Від краю до краю: клавіатура не стискає вікно, а лише повідомляє свою висоту — сторінка підсувається сама,
        // а тло лишається на місці (див. BookScreen).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        FrameRate.boost(window)
        setContent {
            DiaryTheme {
                SheetTheme(enabled = !AppStyle.glass) {
                    val view = LocalView.current
                    LaunchedEffect(StatusBar.hidden) { StatusBar.apply(window) }
                    val density = LocalDensity.current
                    val capped = Density(density.density, density.fontScale.coerceAtMost(MAX_FONT_SCALE * if (AppStyle.glass) 1f else 1.22f))
                    CompositionLocalProvider(LocalHaptics provides Haptics(view, enabled = HapticsSwitch.on), LocalDensity provides capped) {
                        DiaryApp(vm, onExit = { finish() }, onLanguageChanged = { recreate() })
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) StatusBar.apply(window)
    }

    /** Пішли з програми: щоденник із замком закривається (повернувшись — знову код), решта — зберігається. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.onBackground()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        FrameRate.boost(window)
        return super.dispatchTouchEvent(ev)
    }
}
