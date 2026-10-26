package com.blacklaunch.launcher.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.blacklaunch.launcher.BlackLaunchApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Крок «зачекати»: коротку паузу витримуємо в самому Black Glass, довгу — системним будильником
 * (Android розбудить у потрібну мить, нічого не працює у фоні й не тримає батарею).
 */
class StepScheduler(private val app: BlackLaunchApp) {
    fun schedule(title: String, steps: List<String>, seconds: Int) {
        if (seconds <= SHORT_S) {
            app.appScope.launch {
                delay(seconds * 1000L)
                withContext(Dispatchers.Main) { app.automationRunner.runSteps(title, steps) }
            }
            return
        }
        val intent = Intent(app, StepAlarmReceiver::class.java)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_STEPS, steps.toTypedArray())
        val pending = PendingIntent.getBroadcast(app, Random.nextInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Неточний будильник (без окремого дозволу): у глибокому сні Android може зсунути його на кілька хвилин.
        app.getSystemService(AlarmManager::class.java)
            ?.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + seconds * 1000L, pending)
    }

    companion object {
        const val EXTRA_TITLE = "com.blacklaunch.launcher.STEP_TITLE"
        const val EXTRA_STEPS = "com.blacklaunch.launcher.STEP_LIST"
        private const val SHORT_S = 30
    }
}

/** Будильник кроку «зачекати» — решта кроків команди. */
class StepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? BlackLaunchApp ?: return
        val steps = intent.getStringArrayExtra(StepScheduler.EXTRA_STEPS)?.toList() ?: return
        app.automationRunner.runSteps(intent.getStringExtra(StepScheduler.EXTRA_TITLE).orEmpty(), steps)
    }
}
