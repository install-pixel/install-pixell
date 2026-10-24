package com.blacklaunch.launcher.ui

/** Сторінки налаштувань. */
sealed interface SettingsNav {
    data object Main : SettingsNav
    data object HomeScreen : SettingsNav
    data object Appearance : SettingsNav
    data object Search : SettingsNav
    data object Voice : SettingsNav
    data object Battery : SettingsNav
    data object About : SettingsNav
    data object Profiles : SettingsNav
    data class Edit(val profileId: String) : SettingsNav
    data class PickApp(val profileId: String) : SettingsNav
    data class BatteryApps(val profileId: String) : SettingsNav
}
