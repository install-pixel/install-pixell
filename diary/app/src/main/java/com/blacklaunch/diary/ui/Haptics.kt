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

    /**
     * Папір рветься: дуже короткий слабкий «тріск» ([strength] 0…1). Де телефон уміє — найлегший примітив вібрації
     * (нерівна сила від разу до разу дає відчуття волокон), інакше — найлегший системний відгук.
     */
    fun tear(strength: Float) {
        if (!enabled || view == null) return
        val ctx = view.context
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val vib = (ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
            if (vib != null && vib.areAllPrimitivesSupported(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                vib.vibrate(
                    android.os.VibrationEffect.startComposition()
                        .addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK, strength.coerceIn(0.05f, 1f))
                        .compose(),
                )
                return
            }
        }
        perform(if (android.os.Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun perform(type: Int) {
        if (enabled) view?.performHapticFeedback(type)
    }
}

/** Вібрація — одним перемикачем у Налаштуваннях. */
object HapticsSwitch {
    var on by androidx.compose.runtime.mutableStateOf(true)
}

val LocalHaptics = compositionLocalOf { Haptics(view = null, enabled = false) }
