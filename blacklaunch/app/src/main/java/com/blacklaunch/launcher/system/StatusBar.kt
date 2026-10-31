package com.blacklaunch.launcher.system

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blacklaunch.launcher.BlackLaunchApp

/**
 * Рядок стану (годинник, мережа, батарея) — однаково на всіх екранах Black Glass: сховали в поточному режимі —
 * його немає ніде (і на екрані таймера, і в іграх), лише жестом згори.
 */
object StatusBar {
    fun apply(activity: Activity) {
        val app = activity.application as? BlackLaunchApp ?: return
        val hide = app.profileDisplay.of(app.activeProfile.value?.id).hideStatusBar
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
    }
}
