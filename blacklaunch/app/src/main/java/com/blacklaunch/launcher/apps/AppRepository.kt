package com.blacklaunch.launcher.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Список програм через LauncherApps — офіційний API лаунчерів. Сам повідомляє
 * про встановлення, видалення та оновлення, тож окремий BroadcastReceiver не потрібен.
 */
class AppRepository(context: Context) : AppSource {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    override val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val activities = ConcurrentHashMap<String, LauncherActivityInfo>()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        scope.launch {
            val found = mutableMapOf<String, LauncherActivityInfo>()
            val list = userManager.userProfiles.flatMap { user ->
                val serial = userManager.getSerialNumberForUser(user)
                launcherApps.getActivityList(null, user)
                    .filter { it.applicationInfo.packageName != appContext.packageName }
                    .map { info ->
                        AppInfo(
                            label = info.label.toString(),
                            packageName = info.componentName.packageName,
                            activityName = info.componentName.className,
                            userSerial = serial,
                        ).also { found[it.key] = info }
                    }
            }.sortedBy { it.label.lowercase() }
            activities.keys.retainAll(found.keys)
            activities.putAll(found)
            _apps.value = list
        }
    }

    fun icon(app: AppInfo, density: Int): Drawable? =
        runCatching { activities[app.key]?.getBadgedIcon(density) }.getOrNull()

    override fun launch(app: AppInfo): Boolean {
        val user = userManager.getUserForSerialNumber(app.userSerial)
        val component = ComponentName(app.packageName, app.activityName)
        if (user == null || !launcherApps.isActivityEnabled(component, user)) {
            refresh()
            return false
        }
        return try {
            launcherApps.startMainActivity(component, user, null, null)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Activity not found: $component", e)
            refresh()
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot launch: $component", e)
            false
        }
    }

    private companion object {
        const val TAG = "AppRepository"
    }
}
