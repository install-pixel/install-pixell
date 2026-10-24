package com.blacklaunch.launcher.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Переходи до системних налаштувань батареї. Змінювати їх може лише користувач —
 * BlackLaunch тільки відкриває потрібне місце.
 */
object BatterySettings {
    /** Загальні налаштування акумулятора (там у OxygenOS — захист/обмеження заряду). */
    fun openBattery(context: Context) {
        context.startFirst(
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    /**
     * Налаштування батареї конкретної програми, щоб обмежити її фонову роботу.
     * Окремої публічної дії немає на всіх телефонах, тому запасний варіант — «Про програму».
     */
    fun openForApp(context: Context, packageName: String) {
        val uri = Uri.fromParts("package", packageName, null)
        context.startFirst(
            Intent(ACTION_APP_BATTERY_SETTINGS, uri),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri),
        )
    }

    private fun Context.startFirst(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
    }

    // Є в налаштуваннях Android 12+ (і в OxygenOS), але не в публічному SDK.
    private const val ACTION_APP_BATTERY_SETTINGS = "android.settings.APP_BATTERY_SETTINGS"
}
