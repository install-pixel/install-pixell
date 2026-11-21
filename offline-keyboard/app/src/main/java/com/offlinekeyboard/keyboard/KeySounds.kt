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

    /** Whose sounds: the typewriter's, or the sheet's pencil and eraser. */
    enum class Voice { TYPEWRITER, SHEET }

    /** The style's sounds now in use (set with the style). */
    var voice = Voice.TYPEWRITER

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
            if (warmWanted && (id == keys.firstOrNull() || id == pencil.firstOrNull())) warm()
        }
    }

    private val keys = listOf(R.raw.tw_key_1, R.raw.tw_key_2, R.raw.tw_key_3, R.raw.tw_key_4, R.raw.tw_key_5)
        .map { pool.load(context, it, 1) }
    private val space = pool.load(context, R.raw.tw_space, 1)
    private val enter = pool.load(context, R.raw.tw_return, 1)
    private val bell = pool.load(context, R.raw.tw_bell, 1)
    private val sheet = pool.load(context, R.raw.tw_sheet, 1)
    // The sheet: twelve pencil strokes (never the same twice in a row), the lead's tap for space, a long
    // stroke and a dot for Enter, the eraser for Backspace, and its rubbing while Backspace is held.
    private val pencil = listOf(
        R.raw.sh_key_01, R.raw.sh_key_02, R.raw.sh_key_03, R.raw.sh_key_04, R.raw.sh_key_05, R.raw.sh_key_06,
        R.raw.sh_key_07, R.raw.sh_key_08, R.raw.sh_key_09, R.raw.sh_key_10, R.raw.sh_key_11, R.raw.sh_key_12,
    ).map { pool.load(context, it, 1) }
    private val tap = pool.load(context, R.raw.sh_space, 1)
    private val stroke = pool.load(context, R.raw.sh_return, 1)
    private val erase = pool.load(context, R.raw.sh_erase, 1)
    private val rubbing = pool.load(context, R.raw.sh_erase_hold, 1)
    private var lastPencil = -1
    private var rubbingStream = 0
    private val audio = context.getSystemService(AudioManager::class.java)
    private var next = 0

    /** Plays [kind] at [volume] (0–1). */
    fun play(kind: Kind, volume: Float) {
        if (volume <= 0f) return
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (voice == Voice.SHEET) {
            val id = when (kind) {
                Kind.KEY -> {
                    var i = (Math.random() * pencil.size).toInt().coerceIn(0, pencil.size - 1)
                    if (i == lastPencil) i = (i + 1) % pencil.size
                    lastPencil = i
                    pencil[i]
                }
                Kind.SPACE -> tap
                Kind.RETURN -> stroke
                Kind.DELETE -> erase
                Kind.BELL, Kind.SHEET -> return
            }
            pool.play(id, volume, volume, 1, 0, 1f)
            return
        }
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
        val id = if (voice == Voice.SHEET) pencil.first() else keys.first()
        if (id !in loaded) { warmWanted = true; return }
        warmWanted = false
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        pool.play(id, WARM_VOLUME, WARM_VOLUME, 0, 0, 1f)
    }

    /**
     * Backspace held on the sheet: the eraser keeps rubbing (one looped sound) instead of a stroke for every
     * letter; [stopRubbing] when it is let go. Nothing for the typewriter.
     */
    fun startRubbing(volume: Float): Boolean {
        if (voice != Voice.SHEET || volume <= 0f) return false
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return true
        if (rubbingStream == 0) rubbingStream = pool.play(rubbing, volume * 0.8f, volume * 0.8f, 1, -1, 1f)
        return true
    }

    fun stopRubbing() {
        if (rubbingStream != 0) {
            pool.stop(rubbingStream)
            rubbingStream = 0
        }
    }

    fun release() = pool.release()

    private companion object {
        const val WARM_VOLUME = 0.001f
    }
}
