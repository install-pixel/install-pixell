package com.blacklaunch.launcher.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import com.blacklaunch.launcher.R

/**
 * Власний таймер BlackLaunch: не відкриває «Годинник». Відлік показує сповіщення з
 * хронометром (на Android 16 — «капсула» в рядку стану), а наприкінці будить AlarmManager.
 * Жодних фонових процесів: рахує система.
 */
object BlackTimer {
    private const val CHANNEL_RUNNING = "timer_running"
    private const val CHANNEL_DONE = "timer_done"
    private const val ID_RUNNING = 4101
    private const val ID_DONE = 4102
    private const val ACTION_DONE = "com.blacklaunch.launcher.timer.DONE"
    private const val ACTION_CANCEL = "com.blacklaunch.launcher.timer.CANCEL"
    private const val EXTRA_SECONDS = "seconds"

    // Live Updates (Android 16): просимо показати відлік у рядку стану.
    private const val EXTRA_REQUEST_PROMOTED = "android.requestPromotedOngoing"

    /** Чи може BlackLaunch сам вести таймер (точний будильник і сповіщення дозволені). */
    fun canRun(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        val notifications = context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
        return exact && notifications
    }

    /** false — точний будильник заборонено; тоді таймер ставить «Годинник». */
    fun start(context: Context, seconds: Int): Boolean {
        val app = context.applicationContext
        val endAt = System.currentTimeMillis() + seconds * 1000L
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) return false
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, broadcast(app, ACTION_DONE, seconds))
        } catch (e: SecurityException) {
            return false
        }
        createChannels(app)
        app.getSystemService(NotificationManager::class.java)?.cancel(ID_DONE)
        val notification = Notification.Builder(app, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(app.getString(R.string.timer_running_title))
            .setContentText(app.getString(R.string.timer_running_text, durationText(seconds)))
            .setWhen(endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .addAction(
                Notification.Action.Builder(null as Icon?, app.getString(R.string.timer_cancel), broadcast(app, ACTION_CANCEL, seconds)).build(),
            )
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED, true) })
            .build()
        app.getSystemService(NotificationManager::class.java)?.notify(ID_RUNNING, notification)
        return true
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(broadcast(app, ACTION_DONE, 0))
        app.getSystemService(NotificationManager::class.java)?.apply {
            cancel(ID_RUNNING)
            cancel(ID_DONE)
        }
    }

    internal fun finished(context: Context, seconds: Int) {
        val app = context.applicationContext
        createChannels(app)
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(ID_RUNNING)
        val notification = Notification.Builder(app, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(app.getString(R.string.timer_done_title))
            .setContentText(app.getString(R.string.timer_running_text, durationText(seconds)))
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDeleteIntent(broadcast(app, ACTION_CANCEL, seconds))
            .setContentIntent(broadcast(app, ACTION_CANCEL, seconds))
            .addAction(Notification.Action.Builder(null as Icon?, app.getString(R.string.timer_stop), broadcast(app, ACTION_CANCEL, seconds)).build())
            .build()
            // Дзвенить, доки не натиснуть «Зупинити» або не змахнуть сповіщення.
            .apply { flags = flags or Notification.FLAG_INSISTENT }
        manager.notify(ID_DONE, notification)
    }

    private fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, context.getString(R.string.timer_channel_running), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, context.getString(R.string.timer_channel_done), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
                enableVibration(true)
            },
        )
    }

    private fun broadcast(context: Context, action: String, seconds: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, TimerReceiver::class.java).setAction(action).putExtra(EXTRA_SECONDS, seconds),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** «10 хв», «1 год 30 хв», «45 с» — короткими одиницями мовою телефону. */
    private fun durationText(seconds: Int): String = com.blacklaunch.launcher.ui.timerText(seconds)

    class TimerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_DONE -> finished(context, intent.getIntExtra(EXTRA_SECONDS, 0))
                ACTION_CANCEL -> cancel(context)
            }
        }
    }
}
