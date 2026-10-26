package com.blacklaunch.launcher.automation

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Parcelable
import android.os.SystemClock
import android.provider.Telephony
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import com.blacklaunch.launcher.BlackLaunchApp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.profiles.LauncherProfile
import com.blacklaunch.launcher.voice.VoiceActionRunner
import java.util.Calendar

/** Навушники зараз: Bluetooth-аудіо (звичайне чи LE) або дротові/USB — за виходами звуку, без дозволів. */
object Headphones {
    private val types = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET, TYPE_BLE_HEADSET,
    )

    fun connected(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
    }

    /** Розмова телефоном чи відеодзвінок — тоді нічого не озвучуємо. */
    fun inCall(context: Context): Boolean {
        val mode = context.getSystemService(AudioManager::class.java)?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE
    }

    // AudioDeviceInfo.TYPE_BLE_HEADSET (Android 12+); значення стале.
    private const val TYPE_BLE_HEADSET = 26
}

/**
 * «Розумне перемикання режимів»: виходимо з режиму, де грала музика, — пауза й головний екран (програма у фоні);
 * повертаємось у цей режим — музика грає далі. Закривати чужі програми Android не дозволяє, тож лише пауза.
 */
class SmartProfiles(private val app: BlackLaunchApp) {
    private val prefs = app.getSharedPreferences("smart_profiles", Context.MODE_PRIVATE)

    fun onSwitch(from: LauncherProfile?, to: LauncherProfile) {
        if (!app.automationSettings.smartProfiles.value) return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        if (from != null && audio.isMusicActive) {
            mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PAUSE)
            prefs.edit().putString(KEY_PAUSED_IN, from.id).apply()
            // Перемкнули не з головного екрана (плиткою в шторці) — ховаємо програму: Black Glass виходить наперед.
            if (!app.homeVisible && android.provider.Settings.canDrawOverlays(app)) {
                runCatching {
                    app.startActivity(
                        Intent(app, com.blacklaunch.launcher.MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                }
            }
        } else if (prefs.getString(KEY_PAUSED_IN, null) == to.id) {
            prefs.edit().remove(KEY_PAUSED_IN).apply()
            if (!audio.isMusicActive) mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    private fun mediaKey(audio: AudioManager, code: Int) {
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY && audio.isMusicActive) return
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0))
    }

    private companion object {
        const val KEY_PAUSED_IN = "paused_in"
    }
}

/** «Час щогодини в навушники»: будильник на кожну повну годину; говорить лише коли підключені навушники. */
class HourlyChime(private val app: BlackLaunchApp) {
    private val alarms get() = app.getSystemService(AlarmManager::class.java)

    fun reschedule() {
        val manager = alarms ?: return
        val pending = pending()
        if (!app.automationSettings.hourlyChime.value) {
            manager.cancel(pending)
            return
        }
        val next = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, 1)
        }.timeInMillis
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending) else manager.setWindow(AlarmManager.RTC_WAKEUP, next, WINDOW_MS, pending)
    }

    fun ring() {
        if (app.automationSettings.hourlyChime.value && Headphones.connected(app) && !Headphones.inCall(app)) {
            val runner = VoiceActionRunner(app, onMessage = {}, ttsEngine = { app.voiceActionSettings.ttsEngine.value })
            runner.say(runner.timeText())
        }
        reschedule()
    }

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        app, REQUEST, Intent(app, HourlyChimeReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST = 4_201
        const val WINDOW_MS = 60_000L
    }
}

class HourlyChimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as? BlackLaunchApp)?.hourlyChime?.ring()
    }
}

/**
 * «Читати повідомлення вголос»: нове повідомлення з месенджера чи SMS, лише коли підключені навушники й не йде розмова —
 * «Повідомлення від Олега: …». Те саме повідомлення (оновлення сповіщення) двічі не читається.
 */
class MessageReader(private val app: BlackLaunchApp) {
    private val recent = LinkedHashMap<String, Long>()

    fun onPosted(sbn: StatusBarNotification, ownPackage: String) {
        if (!app.automationSettings.readMessages.value) return
        if (sbn.packageName == ownPackage || sbn.isOngoing) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras ?: return
        val isMessage = notification.category == Notification.CATEGORY_MESSAGE ||
            extras.containsKey(Notification.EXTRA_MESSAGES) ||
            sbn.packageName == Telephony.Sms.getDefaultSmsPackage(app)
        if (!isMessage) return
        if (!Headphones.connected(app) || Headphones.inCall(app)) return
        val sender = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString()?.trim().orEmpty()
        val body = lastMessage(extras)?.trim().orEmpty()
        if (sender.isEmpty() && body.isEmpty()) return
        val key = "${sbn.packageName}|$sender|$body"
        val now = SystemClock.elapsedRealtime()
        synchronized(recent) {
            recent.entries.removeAll { now - it.value > DEDUPE_MS }
            if (recent.containsKey(key)) return
            recent[key] = now
        }
        val text = app.getString(R.string.read_message_text, sender.ifEmpty { appLabel(sbn.packageName) }, body.take(MAX_BODY))
        VoiceActionRunner(app, onMessage = {}, ttsEngine = { app.voiceActionSettings.ttsEngine.value }).say(text)
    }

    /** Останнє повідомлення розмови (MessagingStyle) або звичайний текст сповіщення. */
    private fun lastMessage(extras: android.os.Bundle): String? {
        @Suppress("DEPRECATION")
        val messages: Array<Parcelable>? = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val last = (messages?.lastOrNull() as? android.os.Bundle)?.getCharSequence("text")?.toString()
        return last ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    private fun appLabel(pkg: String): String = runCatching {
        app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private companion object {
        const val DEDUPE_MS = 2 * 60_000L
        const val MAX_BODY = 300
    }
}
