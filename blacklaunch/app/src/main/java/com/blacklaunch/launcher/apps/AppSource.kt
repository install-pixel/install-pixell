package com.blacklaunch.launcher.apps

import kotlinx.coroutines.flow.StateFlow

interface AppSource {
    val apps: StateFlow<List<AppInfo>>

    /** Повертає false, якщо програму вже видалено або вимкнено. */
    fun launch(app: AppInfo): Boolean
}
