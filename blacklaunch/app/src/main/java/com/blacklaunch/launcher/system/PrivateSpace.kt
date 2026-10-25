package com.blacklaunch.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Приватний простір: серійний номер його користувача й чи він зараз заблокований. */
data class PrivateSpaceState(val userSerial: Long, val locked: Boolean)

/**
 * Приватний простір Android 15+ через офіційне API лаунчерів (працює, лише коли
 * Black Glass — лаунчер за замовчуванням). Розблокування підтверджує сама система
 * (PIN, відбиток); Black Glass лише просить її про це. Стан оновлює системне
 * повідомлення про зміну профілю — без фонової роботи.
 */
class PrivateSpace(context: Context, private val onAppsChanged: () -> Unit) {
    private val appContext = context.applicationContext
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)

    private val _state = MutableStateFlow<PrivateSpaceState?>(null)
    val state: StateFlow<PrivateSpaceState?> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh()
            onAppsChanged()
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_PROFILE_ADDED)
                addAction(Intent.ACTION_PROFILE_REMOVED)
            }
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        refresh()
    }

    fun refresh() {
        val user = privateUser()
        _state.value = user?.let { PrivateSpaceState(userManager.getSerialNumberForUser(it), userManager.isQuietModeEnabled(it)) }
    }

    /** Заблокувати або розблокувати; розблокування система підтверджує сама. */
    fun toggle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val user = privateUser() ?: return
        try {
            userManager.requestQuietModeEnabled(!userManager.isQuietModeEnabled(user), user)
        } catch (e: SecurityException) {
            Log.w(TAG, "Private space toggle refused", e)
        }
    }

    private fun privateUser(): UserHandle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return runCatching {
            userManager.userProfiles.firstOrNull {
                launcherApps.getLauncherUserInfo(it)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
            }
        }.getOrNull()
    }

    private companion object {
        const val TAG = "PrivateSpace"
    }
}
