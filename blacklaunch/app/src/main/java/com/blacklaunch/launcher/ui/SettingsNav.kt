package com.blacklaunch.launcher.ui

/** Сторінки налаштувань. */
sealed interface SettingsNav {
    data object Main : SettingsNav
    data object HomeScreen : SettingsNav
    data object Appearance : SettingsNav
    data object Search : SettingsNav
    data object Voice : SettingsNav
    data object VoiceActions : SettingsNav
    data object Recognition : SettingsNav
    data object CustomCommands : SettingsNav
    data object BatteryApps : SettingsNav
    data object Battery : SettingsNav
    data object About : SettingsNav
    data object Permissions : SettingsNav
    data object Monitor : SettingsNav
    data object Profiles : SettingsNav
    data object Wellbeing : SettingsNav
    data object Games : SettingsNav
    data object Presets : SettingsNav
    data object PickPauseApps : SettingsNav
    data class Edit(val profileId: String) : SettingsNav
    data class PickApp(val profileId: String) : SettingsNav
    data class PickNotificationApps(val profileId: String) : SettingsNav
}
