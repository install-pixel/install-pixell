package com.blacklaunch.launcher.wellbeing

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.blacklaunch.launcher.profiles.ProfileNotificationFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Що грає зараз: назва, виконавець, чи грає. */
data class NowPlayingInfo(val packageName: String, val title: String, val artist: String?, val playing: Boolean)

/**
 * «Що грає» на головному екрані (етап 16g): активна медіасесія через «Доступ до сповіщень» (як «увімкни музику в …»).
 * Слухаємо лише поки головний екран показано ([start]/[stop]) — у фоні нічого не працює.
 * На паузі плашка лишається ще хвилину (щоб можна було продовжити), потім ховається.
 */
class NowPlaying(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val component = ComponentName(app, ProfileNotificationFilter::class.java)
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null

    private val _info = MutableStateFlow<NowPlayingInfo?>(null)
    val info: StateFlow<NowPlayingInfo?> = _info.asStateFlow()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onSessionDestroyed() = pick(currentSessions())
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }
    private val hideLater = Runnable { update() }

    fun start() {
        val sessions = app.getSystemService(MediaSessionManager::class.java) ?: return
        // Без «Доступу до сповіщень» Android сесій не дає — тоді плашки просто немає.
        if (runCatching { sessions.addOnActiveSessionsChangedListener(sessionsChanged, component, main) }.isFailure) return
        manager = sessions
        pick(currentSessions())
    }

    fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        manager = null
        controller?.unregisterCallback(callback)
        controller = null
        main.removeCallbacks(hideLater)
        _info.value = null
    }

    fun toggle() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    /** Відкрити гравець: його власний екран сесії або просто програму. */
    fun open(): Boolean {
        val c = controller ?: return false
        if (c.sessionActivity?.let { runCatching { it.send() }.isSuccess } == true) return true
        val launch = app.packageManager.getLaunchIntentForPackage(c.packageName) ?: return false
        return runCatching { app.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    private fun currentSessions(): List<MediaController> =
        runCatching { manager?.getActiveSessions(component) }.getOrNull().orEmpty()

    private fun pick(sessions: List<MediaController>) {
        val chosen = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback, main)
        }
        update()
    }

    private fun update() {
        val c = controller
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (c == null || title.isNullOrBlank()) {
            _info.value = null
            return
        }
        val playback = c.playbackState
        val state = playback?.state
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        main.removeCallbacks(hideLater)
        if (!playing) {
            // Скільки вже на паузі — з часу, коли гравець востаннє змінив стан (а не з нашого повернення на екран).
            val since = SystemClock.elapsedRealtime() - (playback?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime())
            if (since >= PAUSED_VISIBLE_MS) {
                _info.value = null
                return
            }
            main.postDelayed(hideLater, PAUSED_VISIBLE_MS - since + 50)
        }
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        _info.value = NowPlayingInfo(c.packageName, title, artist?.takeIf { it.isNotBlank() }, playing)
    }

    private companion object {
        const val PAUSED_VISIBLE_MS = 60_000L
    }
}
