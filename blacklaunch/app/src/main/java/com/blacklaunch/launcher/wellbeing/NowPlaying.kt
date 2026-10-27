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

/**
 * Що грає зараз: назва, виконавець, чи грає; для повноекранного гравця — альбом, обкладинка й позиція
 * ([positionMs] на мить [positionAt] за `SystemClock.elapsedRealtime`, далі рахується з [speed]).
 */
data class NowPlayingInfo(
    val packageName: String,
    val title: String,
    val artist: String?,
    val playing: Boolean,
    val album: String? = null,
    val art: android.graphics.Bitmap? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val positionAt: Long = 0,
    val speed: Float = 1f,
    val canSeek: Boolean = false,
    /** Чи гравець уміє «далі» / «назад» (не вміє — кнопка притемнена й нічого не робить). */
    val canNext: Boolean = true,
    val canPrevious: Boolean = true,
) {
    /** Позиція зараз (поки грає — рухається). */
    fun positionNow(): Long {
        val moved = if (playing) ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong() else 0L
        val pos = positionMs + moved
        return if (durationMs > 0) pos.coerceIn(0, durationMs) else pos.coerceAtLeast(0)
    }
}

/**
 * «Що грає» на головному екрані (етап 16g): активна медіасесія через «Доступ до сповіщень» (як «увімкни музику в …»).
 * Слухаємо лише поки головний екран показано ([listen]/[release]) — у фоні нічого не працює.
 * «Стоп» зупиняє гравець і прибирає плашку одразу (до наступного «відтворити»).
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

    /** Сесія, яку прибрали «Стопом», — не показуємо, доки знову не заграє. */
    private var dismissed: android.media.session.MediaSession.Token? = null

    fun listen() {
        val sessions = app.getSystemService(MediaSessionManager::class.java) ?: return
        // Без «Доступу до сповіщень» Android сесій не дає — тоді плашки просто немає.
        if (runCatching { sessions.addOnActiveSessionsChangedListener(sessionsChanged, component, main) }.isFailure) return
        manager = sessions
        pick(currentSessions())
    }

    fun release() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        manager = null
        controller?.unregisterCallback(callback)
        controller = null
        main.removeCallbacks(hideLater)
        _info.value = null
    }

    fun toggle() {
        val c = controller ?: return
        // Сесія гравця могла вже закритись — тоді просто нічого (а не виліт).
        runCatching { if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play() }
    }

    /** «Стоп»: зупинити гравець (деякі розуміють лише паузу) і одразу прибрати плашку. */
    fun stopPlayback() {
        val c = controller ?: return
        dismissed = c.sessionToken
        runCatching { c.transportControls.pause() }
        runCatching { c.transportControls.stop() }
        main.removeCallbacks(hideLater)
        _info.value = null
    }

    fun seekTo(ms: Long) {
        if (_info.value?.canSeek != true) return
        runCatching { controller?.transportControls?.seekTo(ms) }
    }

    fun next() {
        if (_info.value?.canNext == false) return
        runCatching { controller?.transportControls?.skipToNext() }
    }

    fun previous() {
        if (_info.value?.canPrevious == false) return
        runCatching { controller?.transportControls?.skipToPrevious() }
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
        if (c == null || meta == null || title.isNullOrBlank()) {
            _info.value = null
            return
        }
        val playback = c.playbackState
        val state = playback?.state
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        main.removeCallbacks(hideLater)
        if (dismissed == c.sessionToken) {
            if (!playing) {
                _info.value = null
                return
            }
            dismissed = null
        }
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
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        _info.value = NowPlayingInfo(
            packageName = c.packageName,
            title = title,
            artist = artist?.takeIf { it.isNotBlank() },
            playing = playing,
            album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() },
            art = art,
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            positionMs = playback?.position ?: 0L,
            positionAt = playback?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = playback?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            canSeek = (playback?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L,
            // Невідомо (гравець не повідомив дій) — дозволяємо; повідомив і без «далі» — ні.
            canNext = playback == null || playback.actions == 0L || playback.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canPrevious = playback == null || playback.actions == 0L || playback.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
        )
    }

    private companion object {
        const val PAUSED_VISIBLE_MS = 60_000L
    }
}
