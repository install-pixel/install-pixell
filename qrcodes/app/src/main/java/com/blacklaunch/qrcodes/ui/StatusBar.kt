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
