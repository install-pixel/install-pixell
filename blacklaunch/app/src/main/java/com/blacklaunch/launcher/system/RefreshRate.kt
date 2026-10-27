package com.blacklaunch.launcher.system

import android.app.Activity
import android.os.Build
import android.view.Display

/**
 * Частота оновлення екрана: просимо в системи найвищу, яку вміє екран у поточній роздільності (на OnePlus 13 —
 * 120 Гц). Без прохання OxygenOS часто тримає сторонні програми на 60 Гц. Вимкнено — вирішує система.
 */
object RefreshRate {
    fun apply(activity: Activity, high: Boolean) {
        val display = currentDisplay(activity) ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
            ?: return
        val wanted = if (high) best.modeId else 0
        val params = activity.window.attributes
        if (params.preferredDisplayModeId == wanted) return
        params.preferredDisplayModeId = wanted
        activity.window.attributes = params
    }

    /** Найвища частота, яку вміє екран (для підпису в налаштуваннях). */
    fun maxHz(activity: Activity): Int? = currentDisplay(activity)?.supportedModes?.maxOfOrNull { it.refreshRate }?.toInt()

    private fun currentDisplay(activity: Activity): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }
}
