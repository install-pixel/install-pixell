package com.blacklaunch.launcher.global

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification

/**
 * Тексти сповіщень для пошуку — лише в пам'яті й лише поки джерело ввімкнене.
 * Нічого не пишеться на диск і нікуди не надсилається; після перезапуску Black Glass
 * лишаються тільки ті, що зараз у шторці.
 */
object NotificationTexts {
    class Entry(val id: String, val packageName: String, val title: String, val text: String, val postedAt: Long, val open: PendingIntent?)

    private val entries = LinkedHashMap<String, Entry>()

    /** Android передає сповіщення сам у момент появи — тут лише запам'ятовуємо текст. */
    @Synchronized
    fun record(sbn: StatusBarNotification, ownPackage: String) {
        val entry = entryOf(sbn, ownPackage) ?: return
        entries.remove(entry.id)
        entries[entry.id] = entry
        while (entries.size > MAX) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun clear() = entries.clear()

    /** Збіги серед запам'ятованих і тих, що зараз у шторці (або відкладені фільтром профілю). */
    fun search(query: String, current: List<StatusBarNotification>, ownPackage: String, limit: Int): List<Entry> {
        val all = LinkedHashMap<String, Entry>()
        synchronized(this) { entries.values.forEach { all[it.id] = it } }
        current.mapNotNull { entryOf(it, ownPackage) }.forEach { all[it.id] = it }
        return all.values
            .filter { TextMatch.containsAll(it.title + " " + it.text, query) }
            .sortedByDescending { it.postedAt }
            .take(limit)
    }

    @Synchronized
    fun find(id: String): Entry? = entries[id]

    /** Відкрити сповіщення як із шторки; якщо воно вже недійсне — false (тоді відкриваємо програму). */
    fun open(context: Context, entry: Entry): Boolean {
        val intent = entry.open ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+: запуск із PendingIntent дозволений явно — Black Glass на екрані.
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                } else {
                    @Suppress("DEPRECATION")
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode)
                intent.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                intent.send()
            }
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }

    private fun entryOf(sbn: StatusBarNotification, ownPackage: String): Entry? {
        val notification = sbn.notification
        if (sbn.packageName == ownPackage || sbn.isOngoing) return null
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        // Той самий чат оновлює сповіщення — ключ разом із текстом, щоб не губити попередні повідомлення.
        val id = "${sbn.key}|${text.hashCode()}"
        return Entry(id, sbn.packageName, title, text, sbn.postTime, notification.contentIntent)
    }

    private const val MAX = 200
}
