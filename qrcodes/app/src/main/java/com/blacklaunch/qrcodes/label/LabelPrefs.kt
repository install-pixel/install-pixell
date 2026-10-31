package com.blacklaunch.qrcodes.label

import android.content.Context

/** Останні налаштування друку — щоб щоразу не виставляти ту саму наклейку Zebra. Кількість копій не запам'ятовуємо. */
class LabelPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("label", Context.MODE_PRIVATE)

    fun load(): LabelSettings {
        val d = LabelSettings()
        return LabelSettings(
            size = LabelSize(
                prefs.getFloat(KEY_W, d.size.widthMm),
                prefs.getFloat(KEY_H, d.size.heightMm),
                prefs.getBoolean(KEY_CUSTOM, false),
            ),
            qrMm = prefs.getFloat(KEY_QR, d.qrMm),
            caption = prefs.getBoolean(KEY_CAPTION, d.caption),
        )
    }

    fun save(s: LabelSettings) {
        prefs.edit()
            .putFloat(KEY_W, s.size.widthMm)
            .putFloat(KEY_H, s.size.heightMm)
            .putBoolean(KEY_CUSTOM, s.size.custom)
            .putFloat(KEY_QR, s.qrMm)
            .putBoolean(KEY_CAPTION, s.caption)
            .apply()
    }

    private companion object {
        const val KEY_W = "w"
        const val KEY_H = "h"
        const val KEY_CUSTOM = "custom"
        const val KEY_QR = "qr"
        const val KEY_CAPTION = "caption"
    }
}
