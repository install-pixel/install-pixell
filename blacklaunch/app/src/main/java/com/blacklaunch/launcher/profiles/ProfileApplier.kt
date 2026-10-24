package com.blacklaunch.launcher.profiles

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

/** Що вийшло після перемикання профілю: бажане й фактичне (Етап 9–10). */
data class ApplyResult(
    val profileName: String,
    /** Для «Без звуку» чи «Не турбувати» бракує доступу «Не турбувати». */
    val needsAccess: Boolean,
    /** Звук не встановився (система не дала). */
    val soundFailed: Boolean,
) {
    val ok get() = !needsAccess && !soundFailed
}

/**
 * Застосовує звук і «Не турбувати» профілю. NORMAL / VIBRATE працюють без доступу,
 * SILENT і «Не турбувати» — лише з доступом «Не турбувати», який дає користувач.
 * «Не турбувати» вимикаємо лише тоді, коли його вмикав Black Glass, — ручне не чіпаємо.
 */
class ProfileApplier(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val prefs = app.getSharedPreferences("profile_applier", Context.MODE_PRIVATE)

    fun hasDndAccess(): Boolean = notifications?.isNotificationPolicyAccessGranted == true

    fun apply(profile: LauncherProfile): ApplyResult {
        val access = hasDndAccess()
        val plan = ProfileModes.plan(profile, access, enabledByUs = prefs.getBoolean(KEY_OURS, false))
        var soundFailed = false
        runCatching {
            when (plan.dnd) {
                true -> {
                    notifications?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    prefs.edit().putBoolean(KEY_OURS, true).apply()
                }
                false -> {
                    notifications?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    prefs.edit().putBoolean(KEY_OURS, false).apply()
                }
                null -> Unit
            }
        }
        plan.ringer?.let { mode ->
            val target = when (mode) {
                SoundMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                SoundMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                SoundMode.SILENT -> AudioManager.RINGER_MODE_SILENT
            }
            runCatching { audio?.ringerMode = target }
            // Перевіряємо фактичний результат, а не віримо на слово.
            soundFailed = audio?.ringerMode != target
        }
        return ApplyResult(profile.name, plan.needsAccess, soundFailed)
    }

    private companion object {
        const val KEY_OURS = "dnd_enabled_by_us"
    }
}

/** Чисте рішення «що вмикати» — окремо, щоб перевіряти тестами. */
object ProfileModes {
    /** [dnd]: true — увімкнути, false — вимкнути, null — не чіпати; [ringer] null — не можна. */
    data class Plan(val dnd: Boolean?, val ringer: SoundMode?, val needsAccess: Boolean)

    fun plan(profile: LauncherProfile, hasAccess: Boolean, enabledByUs: Boolean): Plan {
        val wantDnd = profile.notificationMode != NotificationMode.SYSTEM_DEFAULT
        val dnd = when {
            wantDnd && hasAccess -> true
            !wantDnd && enabledByUs && hasAccess -> false
            else -> null
        }
        val silentBlocked = profile.soundMode == SoundMode.SILENT && !hasAccess
        return Plan(
            dnd = dnd,
            ringer = if (silentBlocked) null else profile.soundMode,
            needsAccess = (wantDnd || profile.soundMode == SoundMode.SILENT) && !hasAccess,
        )
    }
}
