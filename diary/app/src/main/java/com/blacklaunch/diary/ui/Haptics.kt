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

    /**
     * Клавіша (магнітофон) пішла вниз — тугий клац; піднялась — легший, глухіший відскок. Напряму через вібромотор, а не
     * через системний «відгук на дотик» (його часто вимикають у налаштуваннях телефона — тоді клавіші мовчали).
     */
    fun keyDown() = direct(strong = true)

    fun keyUp() = direct(strong = false)

    private fun direct(strong: Boolean) {
        if (!enabled || view == null) return
        val ctx = view.context
        val vib: android.os.Vibrator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
        if (vib == null || !vib.hasVibrator()) return
        // Той самий шлях, що й «тріск» паперу (він на телефоні відчувається): найпростіші примітиви, без позначки «дотик».
        // Натиснули — найтугіший із тих, що телефон уміє; відпустили — легкий тик.
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val press = listOf(android.os.VibrationEffect.Composition.PRIMITIVE_CLICK, android.os.VibrationEffect.Composition.PRIMITIVE_THUD, android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
            val release = listOf(android.os.VibrationEffect.Composition.PRIMITIVE_TICK, android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
            val p = (if (strong) press else release).firstOrNull { vib.areAllPrimitivesSupported(it) }
            if (p != null) {
                val comp = android.os.VibrationEffect.startComposition()
                if (strong && p == android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK) {
                    // Лише слабкий тик — натискання робимо подвійним і повним, щоб відрізнялось від відпускання.
                    comp.addPrimitive(p, 1f).addPrimitive(p, 1f, 12)
                } else {
                    comp.addPrimitive(p, if (strong) 1f else 0.45f)
                }
                runCatching { vib.vibrate(comp.compose()) }
                return
            }
        }
        val effect = if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.os.VibrationEffect.createPredefined(if (strong) android.os.VibrationEffect.EFFECT_CLICK else android.os.VibrationEffect.EFFECT_TICK)
        } else {
            android.os.VibrationEffect.createOneShot(if (strong) 18L else 8L, if (strong) 255 else 90)
        }
        // Запасний шлях — як «медіа», а не «дотик»: інакше телефон мовчить, коли відгук на дотик вимкнено.
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                vib.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_MEDIA))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(effect, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
            }
        }
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
