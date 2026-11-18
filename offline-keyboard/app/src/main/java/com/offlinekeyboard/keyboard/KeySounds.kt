package com.offlinekeyboard.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.offlinekeyboard.R

/**
 * The key sounds of the Black Typewriter style (synthesized, see tools/sounds): a metallic strike for keys
 * (three slightly different ones, so fast typing does not sound like a loop), a duller one for the space
 * bar, and the carriage return with its bell for Enter. Silent while the phone is on silent or vibrate.
 */
class KeySounds(private val context: Context) {

    enum class Kind { KEY, SPACE, RETURN, DELETE }

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val keys = listOf(R.raw.tw_key_1, R.raw.tw_key_2, R.raw.tw_key_3).map { pool.load(context, it, 1) }
    private val space = pool.load(context, R.raw.tw_space, 1)
    private val enter = pool.load(context, R.raw.tw_return, 1)
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
        }
        val pitch = if (kind == Kind.DELETE) 0.85f else 1f
        pool.play(id, volume, volume, 1, 0, pitch)
    }

    fun release() = pool.release()
}
