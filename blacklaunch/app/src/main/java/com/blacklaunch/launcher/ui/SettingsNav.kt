package com.blacklaunch.launcher.ui

/** Сторінки налаштувань. */
sealed interface SettingsNav {
    data object Main : SettingsNav
    data object Profiles : SettingsNav
    data class Edit(val profileId: String) : SettingsNav
    data class PickApp(val profileId: String) : SettingsNav
}
