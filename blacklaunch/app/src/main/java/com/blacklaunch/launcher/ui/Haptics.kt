package com.blacklaunch.launcher.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.compositionLocalOf

/**
 * Єдиний вібровідгук для всього BlackLaunch: легкий «тап» на натисканнях,
 * сильніший — на довгому натисканні. Вимикається одним перемикачем у налаштуваннях.
 */
class Haptics(private val view: View?, private val enabled: Boolean) {
    /** Натискання: запуск, кнопка, перемикач, пункт меню. */
    fun tap() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    /** Довге натискання: меню програми, налаштування, початок перетягування. */
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    /** Перемикання профілю свайпом. */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    private fun perform(type: Int) {
        if (enabled) view?.performHapticFeedback(type)
    }
}

val LocalHaptics = compositionLocalOf { Haptics(view = null, enabled = false) }
