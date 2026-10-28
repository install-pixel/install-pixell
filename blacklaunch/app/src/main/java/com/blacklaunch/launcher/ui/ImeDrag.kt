package com.blacklaunch.launcher.ui

import android.annotation.SuppressLint
import android.graphics.Insets
import android.os.Build
import android.os.CancellationSignal
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import android.view.animation.LinearInterpolator
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

/**
 * Клавіатура за пальцем (Android 11+, офіційний WindowInsetsAnimationController): тягнемо панель пошуку вниз —
 * клавіатура їде вниз рівно під пальцем, а панель лежить на ній (одним блоком). Система рухає вікно клавіатури сама,
 * тож це працює з більшістю клавіатур. Не вдалося взяти керування ([failed]) — клавіатура ховається звичайною анімацією.
 */
// Керування ([controller]) з'являється лише на Android 11+ — [start] перевіряє версію, тож решта викликів безпечна.
@SuppressLint("NewApi")
internal class ImeDrag(private val view: View) {
    private var controller: WindowInsetsAnimationController? = null
    private var signal: CancellationSignal? = null

    /** Система не дала керувати клавіатурою (старий Android чи клавіатура не підтримує). */
    var failed = false
        private set

    /** Керування отримано — клавіатура йде за пальцем. */
    val active: Boolean get() = controller != null

    /** Попросили керування, чекаємо (зазвичай один кадр). */
    val pending: Boolean get() = signal != null && controller == null

    /** Скільки клавіатури зараз видно (пікселі). */
    var shown = 0f
        private set

    /** Повна висота відкритої клавіатури. */
    val full: Float get() = controller?.shownStateInsets?.bottom?.toFloat() ?: 0f

    fun start() {
        if (failed || signal != null || controller != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            failed = true
            return
        }
        request()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun request() {
        val insetsController = view.windowInsetsController
        if (insetsController == null) {
            failed = true
            return
        }
        val cancel = CancellationSignal()
        signal = cancel
        insetsController.controlWindowInsetsAnimation(
            WindowInsets.Type.ime(),
            -1L,
            LinearInterpolator(),
            cancel,
            object : WindowInsetsAnimationControlListener {
                override fun onReady(c: WindowInsetsAnimationController, types: Int) {
                    controller = c
                    shown = c.currentInsets.bottom.toFloat()
                }

                override fun onFinished(c: WindowInsetsAnimationController) = done()

                override fun onCancelled(c: WindowInsetsAnimationController?) {
                    if (controller == null) failed = true
                    done()
                }
            },
        )
    }

    /** Палець зсунувся на [dy] (униз — додатне). Повертає рух, що лишився після того, як клавіатура сховалась повністю. */
    fun moveBy(dy: Float): Float {
        val c = controller ?: return dy
        val next = shown - dy
        shown = next.coerceIn(0f, full)
        apply(c)
        return if (next < 0f) -next else 0f
    }

    fun moveTo(px: Float) {
        val c = controller ?: return
        shown = px.coerceIn(0f, full)
        apply(c)
    }

    private fun apply(c: WindowInsetsAnimationController) {
        val total = full
        c.setInsetsAndAlpha(Insets.of(0, 0, 0, shown.roundToInt()), 1f, if (total > 0f) 1f - shown / total else 1f)
    }

    /** Завершити: клавіатура лишається відкритою ([show]) чи схованою. */
    fun finish(show: Boolean) {
        controller?.finish(show)
        done()
    }

    /** Новий жест — можна знову спробувати взяти керування. */
    fun reset() {
        if (controller == null) {
            signal?.cancel()
            signal = null
        }
        failed = false
    }

    private fun done() {
        controller = null
        signal = null
    }
}
