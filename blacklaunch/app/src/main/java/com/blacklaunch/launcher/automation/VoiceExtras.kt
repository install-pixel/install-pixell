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
import com.blacklaunch.launcher.commands.HourMath
import com.blacklaunch.launcher.profiles.LauncherProfile
import com.blacklaunch.launcher.voice.VoiceActionRunner

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

/**
 * «Час щогодини в навушники»: будильник на кожну повну годину; говорить лише коли підключені навушники.
 * Android може розбудити на мить раніше (12:59:59) — тож називаємо саме ту годину, на яку стояв будильник,
 * кожну годину — лише раз, а наступну рахуємо від неї, а не від «зараз».
 */
class HourlyChime(private val app: BlackLaunchApp) {
    private val alarms get() = app.getSystemService(AlarmManager::class.java)
    private val prefs = app.getSharedPreferences("hourly_chime", Context.MODE_PRIVATE)

    /** [after] — після якої миті шукати наступну повну годину; [grace] — запас після щойно сказаної години. */
    fun reschedule(after: Long = System.currentTimeMillis(), grace: Long = 0L) {
        val manager = alarms ?: return
        if (!app.automationSettings.hourlyChime.value) {
            manager.cancel(pending(0))
            return
        }
        val next = HourMath.nextFullHour(after, grace)
        val pending = pending(next)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending) else manager.setWindow(AlarmManager.RTC_WAKEUP, next, WINDOW_MS, pending)
    }

    fun ring(at: Long) {
        val hour = if (at > 0) at else HourMath.nearestFullHour(System.currentTimeMillis())
        val alreadySpoken = prefs.getLong(KEY_LAST, 0L) == hour
        if (!alreadySpoken && app.automationSettings.hourlyChime.value && Headphones.connected(app) && !Headphones.inCall(app)) {
            app.speech.say(app.speech.timeText(hour))
        }
        prefs.edit().putLong(KEY_LAST, hour).apply()
        reschedule(after = maxOf(System.currentTimeMillis(), hour), grace = HourMath.GRACE_MS)
    }

    // Той самий запит (код) — новий будильник замінює попередній, двох не буває.
    private fun pending(at: Long): PendingIntent = PendingIntent.getBroadcast(
        app, REQUEST, Intent(app, HourlyChimeReceiver::class.java).putExtra(EXTRA_AT, at),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val EXTRA_AT = "at"
        private const val REQUEST = 4_201
        private const val WINDOW_MS = 60_000L
        private const val KEY_LAST = "last_hour"
    }
}

class HourlyChimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as? BlackLaunchApp)?.hourlyChime?.ring(intent.getLongExtra(HourlyChime.EXTRA_AT, 0L))
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
