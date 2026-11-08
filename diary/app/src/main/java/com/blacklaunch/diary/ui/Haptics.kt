package com.blacklaunch.diary.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Єдиний вібровідгук (як у Black Glass): легкий «тап» на натисканнях,
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

/** Вібрація — одним перемикачем у Налаштуваннях. */
object HapticsSwitch {
    var on by androidx.compose.runtime.mutableStateOf(true)
}

val LocalHaptics = compositionLocalOf { Haptics(view = null, enabled = false) }
