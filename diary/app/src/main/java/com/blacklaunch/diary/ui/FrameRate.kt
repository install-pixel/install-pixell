package com.blacklaunch.diary.ui

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Window
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Частота екрана: найвища (напр. 120 Гц) — лише поки щось рухається (дотик, гортання, анімації після нього); у
 * спокої програма нічого не просить, і телефон сам знижує частоту — не витрачає батарею даремно.
 */
object FrameRate {
    /** Скільки тримати високу частоту після останнього дотику (догортання й анімації). */
    private const val IDLE_MS = 2000L
    private val handler = Handler(Looper.getMainLooper())

    fun boost(window: Window?) {
        window ?: return
        val best = bestMode(window) ?: return
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId != best.first) {
            attrs.preferredDisplayModeId = best.first
            attrs.preferredRefreshRate = best.second
            window.attributes = attrs
        }
        handler.removeCallbacksAndMessages(window)
        handler.postAtTime({ relax(window) }, window, SystemClock.uptimeMillis() + IDLE_MS)
    }

    private fun relax(window: Window) {
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId == 0 && attrs.preferredRefreshRate == 0f) return
        attrs.preferredDisplayModeId = 0
        attrs.preferredRefreshRate = 0f
        runCatching { window.attributes = attrs }
    }

    /** Режим із найвищою частотою за поточної роздільності (id і частота) або null. */
    private fun bestMode(window: Window): Pair<Int, Float>? {
        val display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.decorView.display else null)
            ?: @Suppress("DEPRECATION") window.windowManager.defaultDisplay ?: return null
        val now = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return null
        return best.modeId to best.refreshRate
    }
}

/** Дотики в будь-якому вікні програми (діалоги — окремі вікна) — висока частота на час руху. */
fun Modifier.boostFrameRate(window: Window?): Modifier =
    if (window == null) {
        this
    } else {
        this.pointerInput(window) {
            awaitPointerEventScope {
                while (true) {
                    // Лише дивимось (нічого не забираємо): дотики йдуть далі, куди йшли.
                    awaitPointerEvent(PointerEventPass.Initial)
                    FrameRate.boost(window)
                }
            }
        }
    }
