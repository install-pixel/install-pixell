package com.blacklaunch.launcher.apps

import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppSource(vararg labels: String) : AppSource {
    override val apps = MutableStateFlow(labels.map(::app))
    val launched = mutableListOf<AppInfo>()
    var launchSucceeds = true

    override fun launch(app: AppInfo): Boolean {
        if (launchSucceeds) launched += app
        return launchSucceeds
    }

    companion object {
        fun app(label: String) = AppInfo(
            label = label,
            packageName = "test." + label.lowercase().filter { it.isLetterOrDigit() },
            activityName = "Main",
            userSerial = 0,
        )
    }
}
