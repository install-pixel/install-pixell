package com.blacklaunch.launcher.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import com.blacklaunch.launcher.profiles.ProfileNotificationFilter
import kotlinx.coroutines.delay

/** «Відтворити» без перемикача: уже грає — нічого не тиснемо. */
object MediaKeys {
    /** Остання програма з музикою — системною кнопкою «відтворити». */
    fun play(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (audio.isMusicActive) return
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
    }

    /**
     * Щойно відкритий гравець [packageName]: кнопку «відтворити» Android віддає тому, хто грав останнім, тож шукаємо
     * саме цей гравець серед активних (з «Доступом до сповіщень») і кажемо йому «відтворити» напряму — поки він не
     * з'явиться, до кількох секунд. Без доступу чи не дочекались — звичайна кнопка.
     */
    suspend fun playIn(context: Context, packageName: String) {
        repeat(ATTEMPTS) {
            val controller = controllerFor(context, packageName)
            if (controller != null) {
                if (controller.playbackState?.state != PlaybackState.STATE_PLAYING) controller.transportControls.play()
                return
            }
            delay(STEP_MS)
        }
        // Загальна кнопка ввімкнула б інший гравець — лише коли гравців узагалі не видно (немає «Доступу до сповіщень»).
        if (!canSeeSessions(context)) play(context)
    }

    /**
     * Чи програма — гравець: позначена як «аудіо», має сервіс медіабібліотеки (для Android Auto, годинників),
     * відкриває аудіофайли або вже є серед активних гравців.
     */
    fun isPlayer(context: Context, packageName: String, category: Int): Boolean {
        if (category == ApplicationInfo.CATEGORY_AUDIO) return true
        val pm = context.packageManager
        val browser = Intent("android.media.browse.MediaBrowserService").setPackage(packageName)
        if (runCatching { pm.queryIntentServices(browser, 0) }.getOrDefault(emptyList()).isNotEmpty()) return true
        val audio = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///x.mp3"), "audio/*").setPackage(packageName)
        if (runCatching { pm.queryIntentActivities(audio, 0) }.getOrDefault(emptyList()).isNotEmpty()) return true
        return controllerFor(context, packageName) != null
    }

    private fun canSeeSessions(context: Context): Boolean = runCatching {
        context.getSystemService(MediaSessionManager::class.java)
            ?.getActiveSessions(ComponentName(context, ProfileNotificationFilter::class.java))
        true
    }.getOrDefault(false)

    private fun controllerFor(context: Context, packageName: String): MediaController? = runCatching {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        manager?.getActiveSessions(ComponentName(context, ProfileNotificationFilter::class.java))?.firstOrNull { it.packageName == packageName }
    }.getOrNull()

    private const val ATTEMPTS = 12
    private const val STEP_MS = 500L
}
