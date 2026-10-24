package com.blacklaunch.launcher.global

import androidx.annotation.StringRes
import com.blacklaunch.launcher.R

/** Назви розділів налаштувань мовою телефону (id — з [SettingsCatalog]). */
object SettingsTitles {
    @StringRes
    fun of(id: String): Int = when (id) {
        "wifi" -> R.string.setting_wifi
        "bluetooth" -> R.string.setting_bluetooth
        "mobile_data" -> R.string.setting_mobile_data
        "airplane" -> R.string.setting_airplane
        "display" -> R.string.setting_display
        "night_light" -> R.string.setting_night_light
        "sound" -> R.string.setting_sound
        "notifications" -> R.string.setting_notifications
        "dnd" -> R.string.setting_dnd
        "battery" -> R.string.setting_battery
        "storage" -> R.string.setting_storage
        "apps" -> R.string.setting_apps
        "default_apps" -> R.string.setting_default_apps
        "location" -> R.string.setting_location
        "security" -> R.string.setting_security
        "privacy" -> R.string.setting_privacy
        "accounts" -> R.string.setting_accounts
        "language" -> R.string.setting_language
        "keyboard" -> R.string.setting_keyboard
        "date_time" -> R.string.setting_date_time
        "accessibility" -> R.string.setting_accessibility
        "nfc" -> R.string.setting_nfc
        "vpn" -> R.string.setting_vpn
        "cast" -> R.string.setting_cast
        "about" -> R.string.setting_about
        "developer" -> R.string.setting_developer
        else -> R.string.group_settings
    }
}
