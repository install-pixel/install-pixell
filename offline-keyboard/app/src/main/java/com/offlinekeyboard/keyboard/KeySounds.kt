package com.offlinekeyboard.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.offlinekeyboard.R

/**
 * The sounds of the Black Typewriter style, from recordings of a real machine (see tools/sounds): a strike
 * for keys (five slightly different ones, so fast typing does not sound like a loop), a duller one for the
 * space bar, the carriage return for Enter, the margin bell near the end of a line and a new sheet of paper
 * going in. Silent while the phone is on silent or vibrate.
 */
class KeySounds(private val context: Context) {

    enum class Kind { KEY, SPACE, RETURN, DELETE, BELL, SHEET }

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    /** Sounds that finished loading: SoundPool loads in the background and plays nothing before that. */
    private val loaded = java.util.Collections.synchronizedSet(HashSet<Int>())
    private var warmWanted = false

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) loaded += id
            if (warmWanted && id == keys.firstOrNull()) warm()
        }
    }

    private val keys = listOf(R.raw.tw_key_1, R.raw.tw_key_2, R.raw.tw_key_3, R.raw.tw_key_4, R.raw.tw_key_5)
        .map { pool.load(context, it, 1) }
    private val space = pool.load(context, R.raw.tw_space, 1)
    private val enter = pool.load(context, R.raw.tw_return, 1)
    private val bell = pool.load(context, R.raw.tw_bell, 1)
    private val sheet = pool.load(context, R.raw.tw_sheet, 1)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var next = 0

    /** Plays [kind] at [volume] (0–1). */
    fun play(kind: Kind, volume: Float) {
        if (volume <= 0f) return
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = when (kind) {
            Kind.KEY -> keys[next++ % keys.size]
            Kind.DELETE -> keys[2]
            Kind.SPACE -> space
            Kind.RETURN -> enter
            Kind.BELL -> bell
            Kind.SHEET -> sheet
        }
        val pitch = if (kind == Kind.DELETE) 0.85f else 1f
        pool.play(id, volume, volume, 1, 0, pitch)
    }

    /**
     * Wakes the phone's audio output with an almost silent strike, so the first real key is heard at once
     * (the output otherwise starts with the first sound, and the first letters came late). Runs again when
     * the sounds are still loading.
     */
    fun warm() {
        val id = keys.first()
        if (id !in loaded) { warmWanted = true; return }
        warmWanted = false
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        pool.play(id, WARM_VOLUME, WARM_VOLUME, 0, 0, 1f)
    }

    fun release() = pool.release()

    private companion object {
        const val WARM_VOLUME = 0.001f
    }
}
