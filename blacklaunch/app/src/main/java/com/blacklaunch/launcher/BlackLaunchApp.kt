package com.blacklaunch.launcher

import android.app.Application
import com.blacklaunch.launcher.apps.AppRepository
import com.blacklaunch.launcher.apps.PrefsLaunchHistory
import com.blacklaunch.launcher.search.UserAliasStore
import com.blacklaunch.launcher.settings.AppearanceSettings

class BlackLaunchApp : Application() {
    val appRepository: AppRepository by lazy { AppRepository(this) }
    val userAliasStore: UserAliasStore by lazy { UserAliasStore(this) }
    val launchHistory: PrefsLaunchHistory by lazy { PrefsLaunchHistory(this) }
    val appearanceSettings: AppearanceSettings by lazy { AppearanceSettings(this) }
}
