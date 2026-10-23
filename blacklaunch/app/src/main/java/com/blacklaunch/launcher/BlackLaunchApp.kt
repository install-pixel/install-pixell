package com.blacklaunch.launcher

import android.app.Application
import com.blacklaunch.launcher.apps.AppRepository

class BlackLaunchApp : Application() {
    val appRepository: AppRepository by lazy { AppRepository(this) }
}
