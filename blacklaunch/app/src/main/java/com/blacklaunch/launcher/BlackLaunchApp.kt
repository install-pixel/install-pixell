package com.blacklaunch.launcher

import android.app.Application
import com.blacklaunch.launcher.apps.AppRepository
import com.blacklaunch.launcher.search.UserAliasStore

class BlackLaunchApp : Application() {
    val appRepository: AppRepository by lazy { AppRepository(this) }
    val userAliasStore: UserAliasStore by lazy { UserAliasStore(this) }
}
