package com.blacklaunch.qrcodes.ui

import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Рядок стану (годинник, мережа, батарея) — як у лаунчері: сховали в Налаштуваннях — його немає ніде в програмі
 * (і в усіх вікнах), лише свайпом згори він ненадовго з'являється.
 */
object StatusBar {
    var hidden by mutableStateOf(false)

    fun apply(window: Window?) {
        window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
    }
}

/**
 * Підвищена яскравість для коду, який сканують з екрана (на весь екран і у формі): одне налаштування на всю програму,
 * перемикається в меню «⋯» там, де показано код.
 */
object QrBrightness {
    var on by mutableStateOf(true)
    private var save: (Boolean) -> Unit = {}

    fun init(settings: com.blacklaunch.qrcodes.settings.AppSettings) {
        on = settings.qrBright
        save = { settings.qrBright = it }
    }

    fun toggle() {
        on = !on
        save(on)
    }
}
