package com.offlinekeyboard.privacy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/**
 * Runtime self-check shown in Settings. The build also strips network permissions in the manifest,
 * and CI fails if INTERNET ends up in the APK.
 */
object OfflinePolicy {

    private val networkPermissions = listOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
    )

    /** Network permissions this installed APK requests; empty means the OS will refuse any socket. */
    fun requestedNetworkPermissions(context: Context): List<String> {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toSet().orEmpty()
        return networkPermissions.filter { it in requested }
    }
}
