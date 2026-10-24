package com.blacklaunch.launcher.profiles

enum class SoundMode { NORMAL, VIBRATE, SILENT }

enum class NotificationMode { SYSTEM_DEFAULT, DO_NOT_DISTURB, ALLOW_SELECTED }

/**
 * Профіль роботи телефона. Контракт із roadmap (розділ 7); закріплені програми зберігаються
 * за ключем `AppInfo.key` (пакет/activity/користувач), а не лише за пакетом, щоб розрізняти
 * кілька точок входу одного пакета й робочий профіль Android.
 */
data class LauncherProfile(
    val id: String,
    val name: String,
    val pinnedAppKeys: List<String> = emptyList(),
    val soundMode: SoundMode = SoundMode.NORMAL,
    val notificationMode: NotificationMode = NotificationMode.SYSTEM_DEFAULT,
    val allowedNotificationPackages: List<String> = emptyList(),
    val scheduleEnabled: Boolean = false,
)

object DefaultProfiles {
    const val WORK = "work"
    const val HOME = "home"
    const val REST = "rest"

    /** Назви — мовою телефону на момент першого запуску; потім їх можна перейменувати. */
    fun create(workName: String, homeName: String, restName: String): List<LauncherProfile> = listOf(
        LauncherProfile(WORK, workName, soundMode = SoundMode.VIBRATE),
        LauncherProfile(HOME, homeName, soundMode = SoundMode.NORMAL),
        LauncherProfile(
            REST,
            restName,
            soundMode = SoundMode.SILENT,
            notificationMode = NotificationMode.DO_NOT_DISTURB,
        ),
    )
}
