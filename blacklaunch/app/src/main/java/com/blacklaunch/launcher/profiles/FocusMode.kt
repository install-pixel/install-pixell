package com.blacklaunch.launcher.profiles

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blacklaunch.launcher.BlackLaunchApp
import com.blacklaunch.launcher.MainActivity
import com.blacklaunch.launcher.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * «Зосередження» (етап 16): «Важкий вихід» кожного режиму, фокус-таймер і його підсумок.
 * Кінець таймера — один системний будильник (як у подій «за часом»): у фоні нічого не працює.
 */
class FocusMode(private val app: BlackLaunchApp) {
    private val prefs = app.getSharedPreferences("focus", Context.MODE_PRIVATE)
    private val alarms get() = app.getSystemService(AlarmManager::class.java)

    private val _exitDelays = MutableStateFlow(
        prefs.all.filterKeys { it.startsWith(PREFIX_DELAY) }
            .mapNotNull { (key, value) -> (value as? Int)?.let { key.removePrefix(PREFIX_DELAY) to it } }
            .toMap(),
    )

    /** Режим → секунди відліку перед виходом (немає — без відліку). */
    val exitDelays: StateFlow<Map<String, Int>> = _exitDelays.asStateFlow()

    fun exitDelay(profileId: String?): Int = profileId?.let { _exitDelays.value[it] } ?: 0

    fun setExitDelay(profileId: String, seconds: Int) {
        _exitDelays.value = if (seconds > 0) _exitDelays.value + (profileId to seconds) else _exitDelays.value - profileId
        prefs.edit().apply { if (seconds > 0) putInt(PREFIX_DELAY + profileId, seconds) else remove(PREFIX_DELAY + profileId) }.apply()
    }

    private val _cat = MutableStateFlow(prefs.getBoolean(KEY_CAT, true))

    /** Котик на екрані таймера (можна вимкнути). */
    val cat: StateFlow<Boolean> = _cat.asStateFlow()

    fun setCat(on: Boolean) {
        _cat.value = on
        prefs.edit().putBoolean(KEY_CAT, on).apply()
    }

    private val _session = MutableStateFlow(FocusSession.decode(prefs.getString(KEY_SESSION, null)))
    val session: StateFlow<FocusSession?> = _session.asStateFlow()

    private val _summary = MutableStateFlow<FocusSummary?>(null)

    /** Підсумок щойно завершеного таймера — показується раз, коли повернетесь на екран. */
    val summary: StateFlow<FocusSummary?> = _summary.asStateFlow()

    fun clearSummary() {
        _summary.value = null
    }

    private val now get() = System.currentTimeMillis()

    fun running(): Boolean = FocusRules.running(_session.value, now)

    fun switchGate(targetId: String): FocusGate {
        val active = app.activeProfile.value?.id
        return FocusRules.switchGate(active, targetId, _session.value, exitDelay(active), now)
    }

    fun settingsGate(): FocusGate = FocusRules.settingsGate(_session.value, exitDelay(app.activeProfile.value?.id), now)

    fun automationMaySwitch(targetId: String): Boolean =
        FocusRules.automationMaySwitch(app.activeProfile.value?.id, targetId, _session.value, now)

    fun start(profileId: String, minutes: Int, strict: Boolean) {
        val active = app.activeProfile.value?.id
        val startedAt = now
        // Таймер у тому самому режимі, що й зараз, — потім повертаємось туди, де були до нього.
        val previous = if (active == profileId) app.profileRepository.previousActiveId() else active
        save(FocusSession(profileId, previous?.takeIf { it != profileId }, startedAt, startedAt + minutes * 60_000L, strict))
        _summary.value = null
        app.profileRepository.setActive(profileId)
        schedule()
    }

    /** Спроба вийти чи відкрити налаштування під час таймера — для підсумку. */
    fun noteAttempt() {
        val current = _session.value ?: return
        if (!FocusRules.running(current, now)) return
        save(current.copy(attempts = current.attempts + 1))
    }

    /** Після довгого утримання й відліку. */
    fun endEarly() = finish(now)

    /** Будильник кінця таймера (чи запуск після перезавантаження, коли час уже минув). */
    fun complete() {
        val current = _session.value ?: return
        // Будильник міг прийти трохи раніше — такий таймер теж завершено, а не перервано.
        if (now < current.endsAt - EARLY_ALARM_MS) {
            schedule()
            return
        }
        finish(current.endsAt)
    }

    /** Запуск програми, перезавантаження, зміна часу: таймер, що вже минув, завершуємо, інакше ставимо будильник знову. */
    fun restore() {
        val current = _session.value ?: return
        if (now >= current.endsAt) complete() else schedule()
    }

    private fun finish(endedAt: Long) {
        val current = _session.value ?: return
        // Black Glass на екрані — підсумок покаже наше вікно в стилі теми; системне сповіщення — лише коли нас не видно.
        val onScreen = app.homeVisible || app.focusVisible
        alarms?.cancel(pending())
        save(null)
        app.appScope.launch {
            // Після перезавантаження профілі ще читаються з бази — чекаємо їх, а не беремо порожній список.
            val profiles = app.profileRepository.profiles.first()
            val name = profiles.firstOrNull { it.id == current.profileId }?.name.orEmpty()
            val summary = FocusRules.summary(current, name, endedAt)
            _summary.value = summary
            // Назад туди, де були до таймера (якщо той режим ще є).
            current.previousId?.takeIf { id -> profiles.any { it.id == id } }?.let { app.profileRepository.setActive(it) }
            if (!onScreen) notify(summary)
        }
    }

    private fun save(session: FocusSession?) {
        _session.value = session
        prefs.edit().apply { if (session != null) putString(KEY_SESSION, session.encode()) else remove(KEY_SESSION) }.apply()
    }

    private fun schedule() {
        val manager = alarms ?: return
        val at = _session.value?.endsAt ?: return
        if (app.timeTriggers.canBeExact()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending())
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending())
        }
    }

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        app, REQUEST, Intent(app, FocusReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(summary: FocusSummary) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.focus_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            app, REQUEST, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(app.getString(if (summary.early) R.string.focus_ended_early else R.string.focus_done))
            .setContentText(summaryText(app, summary))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val PREFIX_DELAY = "exit_delay_"
        private const val KEY_SESSION = "session"
        private const val KEY_CAT = "cat"
        private const val CHANNEL = "focus"
        private const val REQUEST = 7316
        private const val NOTIFICATION_ID = 7316
        private const val EARLY_ALARM_MS = 5_000L

        /** «30 хв · спроб відволіктися: 2» — і в сповіщенні, і в підсумку на екрані. */
        fun summaryText(context: Context, summary: FocusSummary): String = context.getString(
            R.string.focus_summary_text,
            summary.minutes,
            summary.plannedMinutes,
            summary.attempts,
        )
    }
}

/** Кінець фокус-таймера. */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as? BlackLaunchApp)?.focus?.complete()
    }
}
