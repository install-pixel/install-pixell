package com.blacklaunch.launcher.profiles

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.blacklaunch.launcher.BlackLaunchApp
import com.blacklaunch.launcher.global.GroupKind
import com.blacklaunch.launcher.global.NotificationTexts

/**
 * «Сповіщення лише від програм профілю»: сповіщення програм поза профілем відкладаються
 * (не губляться) і повертаються, коли профіль перемикають. Android сам передає кожне
 * сповіщення в момент появи — постійної фонової роботи немає. Нічого нікуди не надсилається.
 */
class ProfileNotificationFilter : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = this
        refresh()
    }

    override fun onListenerDisconnected() {
        if (connected === this) connected = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = application as? BlackLaunchApp
        if (app != null && GroupKind.NOTIFICATIONS in app.globalSearchSettings.enabled.value) NotificationTexts.record(sbn, packageName)
        if (shouldHide(sbn)) {
            runCatching { snoozeNotification(sbn.key, HIDE_MS) }
        } else {
            // Приховане фільтром профілю не читаємо — інакше фільтр втрачає сенс.
            app?.messageReader?.onPosted(sbn, packageName)
        }
    }

    /** Профіль змінився: відкладаємо чуже й повертаємо те, що тепер дозволено. */
    fun refresh() {
        runCatching { activeNotifications.orEmpty().filter(::shouldHide).forEach { snoozeNotification(it.key, HIDE_MS) } }
        runCatching {
            // Повернути — «відкласти» на мить: Android одразу покаже сповіщення знову.
            snoozedNotifications.orEmpty().filterNot(::shouldHide).forEach { snoozeNotification(it.key, RETURN_MS) }
        }
    }

    private fun shouldHide(sbn: StatusBarNotification): Boolean {
        val app = application as? BlackLaunchApp ?: return false
        val profile = app.activeProfile.value ?: return false
        if (profile.id !in app.searchScopes.notificationFilter.value) return false
        val notification = sbn.notification
        return NotificationRules.hide(
            packageName = sbn.packageName,
            ownPackage = packageName,
            allowedPackages = profile.pinnedAppKeys.mapTo(HashSet()) { it.substringBefore('/') } +
                app.searchScopes.notificationExtras.value[profile.id].orEmpty(),
            category = notification.category,
            ongoing = sbn.isOngoing || notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
        )
    }

    companion object {
        private const val HIDE_MS = 24L * 60 * 60 * 1000
        private const val RETURN_MS = 1L

        @Volatile
        private var connected: ProfileNotificationFilter? = null

        /** Сповіщення в шторці й відкладені фільтром профілю — для пошуку за текстом. */
        fun current(): List<StatusBarNotification> {
            val service = connected ?: return emptyList()
            return runCatching { service.activeNotifications.orEmpty().toList() }.getOrDefault(emptyList()) +
                runCatching { service.snoozedNotifications.orEmpty().toList() }.getOrDefault(emptyList())
        }

        /** Перемкнули профіль чи змінили налаштування — перевірити сповіщення ще раз. */
        fun refreshNow() = connected?.refresh()

        fun hasAccess(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            val me = ComponentName(context, ProfileNotificationFilter::class.java).flattenToString()
            return enabled.split(':').any { it == me }
        }
    }
}

/** Чисте правило «ховати чи ні» — окремо, щоб перевіряти тестами. */
object NotificationRules {
    // Системне й важливе ніколи не ховаємо: дзвінки, будильники, нагадування, системні служби.
    private val alwaysShown = setOf(
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_ALARM,
        Notification.CATEGORY_REMINDER,
        Notification.CATEGORY_SYSTEM,
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_TRANSPORT,
    )
    private val systemPackages = setOf("android", "com.android.systemui", "com.android.phone", "com.android.server.telecom")

    fun hide(packageName: String, ownPackage: String, allowedPackages: Set<String>, category: String?, ongoing: Boolean): Boolean = when {
        packageName == ownPackage || packageName in systemPackages -> false
        category in alwaysShown -> false
        // Плеєри, навігація, завантаження — постійні сповіщення не ховаємо.
        ongoing -> false
        else -> packageName !in allowedPackages
    }
}
