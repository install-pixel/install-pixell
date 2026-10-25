package com.blacklaunch.launcher.system

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.core.content.ContextCompat
import com.blacklaunch.launcher.R
import java.util.Date

/** «Короткий огляд» на головному екрані: [time] (якщо ввімкнено), дата і рядок — будильник, подія, батарея. */
data class GlanceInfo(val time: String?, val date: String, val details: String)

/** Що показувати в огляді. */
data class GlanceParts(val clock: Boolean = true, val alarm: Boolean = true, val event: Boolean = true, val battery: Boolean = true)

object Glance {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Читається лише в момент показу (раз на хвилину, поки екран відкритий). */
    fun read(context: Context, parts: GlanceParts = GlanceParts()): GlanceInfo {
        val now = System.currentTimeMillis()
        val date = DateUtils.formatDateTime(
            context,
            now,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR,
        )
        val time = DateFormat.getTimeFormat(context)
        val details = listOfNotNull(
            if (!parts.alarm) null else context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
                ?.takeIf { it - now in 0..DAY_MS }
                ?.let { context.getString(R.string.glance_alarm, time.format(Date(it))) },
            if (!parts.event) null else nextEvent(context, now)?.let { (title, begin) -> "$title, ${time.format(Date(begin))}" },
            if (!parts.battery) null else context.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
                ?.let { context.getString(R.string.glance_battery, it) },
        )
        return GlanceInfo(if (parts.clock) time.format(Date(now)) else null, date, details.joinToString("  ·  "))
    }

    // Найближча подія на добу вперед — лише якщо календар уже дозволено (для пошуку).
    private fun nextEvent(context: Context, now: Long): Pair<String, Long>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath((now + DAY_MS).toString())
            .build()
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                "${CalendarContract.Instances.ALL_DAY} = 0 AND ${CalendarContract.Instances.BEGIN} >= ?",
                arrayOf(now.toString()),
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() to c.getLong(1) else null }
        }.getOrNull()
    }
}
