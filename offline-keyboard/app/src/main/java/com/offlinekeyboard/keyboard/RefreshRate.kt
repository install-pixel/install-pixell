package com.offlinekeyboard.keyboard

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.Window

/**
 * Draws as smoothly as the owner set the phone to: when the phone allows more than 60 Hz («smooth display»,
 * 90/120 Hz), the window asks for the fastest mode the phone allows, at the screen's own resolution; set to
 * 60 Hz, nothing is asked and it stays at 60 (saving the battery as the owner chose).
 */
internal object RefreshRate {
    fun apply(window: Window?, context: Context) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val allowed = peak(context)
        if (allowed <= 61f) return
        val display = context.display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .filter { it.refreshRate <= allowed + 1f }
            .maxByOrNull { it.refreshRate } ?: return
        if (best.refreshRate <= 61f) return
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId == best.modeId) return
        attributes.preferredDisplayModeId = best.modeId
        window.attributes = attributes
    }

    /** The highest refresh rate the owner allows in the phone's display settings (0 when the phone does not say). */
    private fun peak(context: Context): Float {
        val resolver = context.contentResolver
        for (key in listOf("peak_refresh_rate", "user_refresh_rate")) {
            val v = runCatching { Settings.System.getFloat(resolver, key) }.getOrNull()
                ?: runCatching { Settings.Secure.getFloat(resolver, key) }.getOrNull()
            if (v != null && v > 0f) return v
        }
        return 0f
    }
}
