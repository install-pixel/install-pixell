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
        "network" -> R.string.setting_network
        "sim" -> R.string.setting_sim
        "roaming" -> R.string.setting_roaming
        "hotspot" -> R.string.setting_hotspot
        "battery_saver" -> R.string.setting_battery_saver
        "battery_optimization" -> R.string.setting_battery_optimization
        "all_apps" -> R.string.setting_all_apps
        "home_app" -> R.string.setting_home_app
        "usage_access" -> R.string.setting_usage_access
        "overlay" -> R.string.setting_overlay
        "write_settings" -> R.string.setting_write_settings
        "notification_access" -> R.string.setting_notification_access
        "dnd_access" -> R.string.setting_dnd_access
        "unknown_sources" -> R.string.setting_unknown_sources
        "captions" -> R.string.setting_captions
        "hard_keyboard" -> R.string.setting_hard_keyboard
        "voice_input" -> R.string.setting_voice_input
        "dictionary" -> R.string.setting_dictionary
        "screensaver" -> R.string.setting_screensaver
        "print" -> R.string.setting_print
        "add_account" -> R.string.setting_add_account
        "nfc_payment" -> R.string.setting_nfc_payment
        "device_name" -> R.string.setting_device_name
        "biometrics" -> R.string.setting_biometrics
        "auto_rotate" -> R.string.setting_auto_rotate
        "settings" -> R.string.setting_settings
        "monitor" -> R.string.setting_monitor
        "commands" -> R.string.custom_commands_title
        "focus" -> R.string.focus_timer_title
        else -> R.string.group_settings
    }
}
