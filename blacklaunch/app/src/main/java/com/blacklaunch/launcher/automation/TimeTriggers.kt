package com.blacklaunch.launcher.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.blacklaunch.launcher.BlackLaunchApp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.commands.TimeSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Подія «за часом»: на кожну команду — один системний будильник на найближче спрацювання; після нього —
 * наступний. У фоні нічого не працює. З дозволом «Будильники й нагадування» — хвилина в хвилину,
 * без нього — неточно (у глибокому сні Android може зсунути на кілька хвилин).
 */
class TimeTriggers(private val app: BlackLaunchApp) {
    private val prefs = app.getSharedPreferences("time_triggers", Context.MODE_PRIVATE)
    private val alarms get() = app.getSystemService(AlarmManager::class.java)

    fun canBeExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms?.canScheduleExactAlarms() == true

    /** Переплановує всі команди з подією «за часом» (після змін, запуску, перезавантаження, зміни часу). */
    fun rescheduleAll() {
        val manager = alarms ?: return
        val now = System.currentTimeMillis()
        val commands = app.customCommands.commands.value
        val scheduled = mutableSetOf<String>()
        commands.forEach { command ->
            val at = TimeSchedule.nextFor(command, now) ?: return@forEach
            val pending = pendingFor(command.id)
            if (canBeExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pending)
            }
            scheduled += command.id
        }
        // Команди, що зникли чи втратили подію «за часом», — їхні будильники прибираємо.
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().filter { it !in scheduled }.forEach { manager.cancel(pendingFor(it)) }
        prefs.edit().putStringSet(KEY_IDS, scheduled).apply()
    }

    private fun pendingFor(id: String): PendingIntent = PendingIntent.getBroadcast(
        app, id.hashCode(), Intent(app, TimeTriggerReceiver::class.java).setAction(ACTION).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Будильник спрацював: умови → кроки; і одразу — наступний раз. */
    fun fire(id: String, done: () -> Unit) {
        val command = app.customCommands.commands.value.firstOrNull { it.id == id }
        if (command == null) {
            rescheduleAll()
            done()
            return
        }
        app.automationRunner.noteTime()
        app.appScope.launch {
            val steps = app.commandConditions.resolve(command)
            if (!steps.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    app.automationRunner.runSteps(command.phrase.ifBlank { app.getString(R.string.custom_commands_title) }, steps)
                }
            }
            withContext(Dispatchers.Main) { rescheduleAll() }
            done()
        }
    }

    companion object {
        const val ACTION = "com.blacklaunch.launcher.TIME_TRIGGER"
        const val EXTRA_ID = "command_id"
        private const val KEY_IDS = "ids"
        private const val WINDOW_MS = 2 * 60_000L
    }
}

/** Будильник події «за часом» і системні події, після яких будильники треба поставити знову. */
class TimeTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? BlackLaunchApp ?: return
        if (intent.action == TimeTriggers.ACTION) {
            val id = intent.getStringExtra(TimeTriggers.EXTRA_ID) ?: return
            val pending = goAsync()
            app.timeTriggers.fire(id) { pending.finish() }
        } else {
            // Перезавантаження, оновлення, зміна часу чи поясу, дозвіл на точні будильники.
            app.timeTriggers.rescheduleAll()
            app.hourlyChime.reschedule()
            app.focus.restore()
        }
    }
}
