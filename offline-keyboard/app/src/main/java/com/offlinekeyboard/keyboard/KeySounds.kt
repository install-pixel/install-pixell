package com.offlinekeyboard.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.offlinekeyboard.R

/**
 * The sounds of the Black Typewriter style, from recordings of a real machine (see tools/sounds): a strike
 * for keys (three strikes with the key coming back up, each a little higher or lower and louder or softer, so fast typing does not sound like a loop), a duller one for the
 * space bar, the carriage return for Enter, the margin bell near the end of a line and a new sheet of paper
 * going in; the sheet's pencil and eraser; the glass's bubble wrap. Silent while the phone is on silent or
 * vibrate.
 */
class KeySounds(private val context: Context) {

    enum class Kind { KEY, SPACE, RETURN, DELETE, BELL, SHEET }

    /** Whose sounds: the typewriter's, the sheet's pencil and eraser, or the glass's bubble wrap. */
    enum class Voice { TYPEWRITER, SHEET, GLASS }

    companion object {
        /** The sounds of a keyboard style. */
        fun voiceOf(mode: ThemeMode): Voice = when {
            mode.sheet -> Voice.SHEET
            mode.glass -> Voice.GLASS
            else -> Voice.TYPEWRITER
        }

        private const val WARM_VOLUME = 0.001f
        /** The pencil: the writing's level, and how much of a stroke touches each letter on top of it. */
        private const val PEN_WRITE = 0.95f
        private const val PEN_TOUCH = 0.28f
        /** How long the writing goes on after a letter before it fades. */
        private const val WRITE_HOLD_MS = 260L
    }

    /** The style's sounds now in use (set with the style). */
    var voice = Voice.TYPEWRITER
        set(value) {
            if (value != Voice.SHEET) haltWriting()
            field = value
        }

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
            if (warmWanted && (id == keys.firstOrNull() || id == pencil.firstOrNull() || id == bubbles.firstOrNull())) warm()
        }
    }

    private val keys = listOf(R.raw.tw_key_1, R.raw.tw_key_2, R.raw.tw_key_3)
        .map { pool.load(context, it, 1) }
    private val space = pool.load(context, R.raw.tw_space, 1)
    private val enters = listOf(R.raw.tw_return_1, R.raw.tw_return_2, R.raw.tw_return_3).map { pool.load(context, it, 1) }
    private val bell = pool.load(context, R.raw.tw_bell, 1)
    private val sheet = pool.load(context, R.raw.tw_sheet, 1)
    // The sheet: twelve pencil strokes (never the same twice in a row), the lead's tap for space, a long
    // stroke and a dot for Enter, the eraser for Backspace, and its rubbing while Backspace is held.
    private val pencil = listOf(
        R.raw.sh_key_01, R.raw.sh_key_02, R.raw.sh_key_03, R.raw.sh_key_04, R.raw.sh_key_05, R.raw.sh_key_06,
        R.raw.sh_key_07, R.raw.sh_key_08, R.raw.sh_key_09, R.raw.sh_key_10, R.raw.sh_key_11, R.raw.sh_key_12,
    ).map { pool.load(context, it, 1) }
    // Several of each, taken in turn at random (never the same twice in a row), so quick typing is not a loop.
    private val taps = listOf(R.raw.sh_space_1, R.raw.sh_space_2, R.raw.sh_space_3, R.raw.sh_space_4).map { pool.load(context, it, 1) }
    private val strokes = listOf(R.raw.sh_return_1, R.raw.sh_return_2, R.raw.sh_return_3).map { pool.load(context, it, 1) }
    private val erases = listOf(R.raw.sh_erase_1, R.raw.sh_erase_2, R.raw.sh_erase_3, R.raw.sh_erase_4).map { pool.load(context, it, 1) }
    private val rubbing = pool.load(context, R.raw.sh_erase_hold, 1)
    // The pencil writing on while letters come: one continuous line (looped), kept up by every letter and let
    // fade out a moment after the last, so typing sounds like a hand writing, not a stroke per letter.
    private val writeLoop = pool.load(context, R.raw.sh_write_loop, 1)
    private var writeStream = 0
    private var writing = false
    private var writeGen = 0
    private val stopWriting = Runnable { fadeWriting() }
    // The glass: seven bubble-wrap pops (never the same twice in a row), deeper ones for space, two or three in
    // a row for Enter, a puff of air for Backspace, and those puffs in turn while Backspace is held.
    private val bubbles = listOf(
        R.raw.gl_key_01, R.raw.gl_key_02, R.raw.gl_key_03, R.raw.gl_key_04, R.raw.gl_key_05,
        R.raw.gl_key_06, R.raw.gl_key_07,
    ).map { pool.load(context, it, 1) }
    private val popsForSpace = listOf(R.raw.gl_space_1, R.raw.gl_space_2, R.raw.gl_space_3, R.raw.gl_space_4).map { pool.load(context, it, 1) }
    private val popsForReturn = listOf(R.raw.gl_return_1, R.raw.gl_return_2, R.raw.gl_return_3).map { pool.load(context, it, 1) }
    private val airs = listOf(R.raw.gl_delete_1, R.raw.gl_delete_2).map { pool.load(context, it, 1) }
    private val rustle = pool.load(context, R.raw.gl_delete_hold, 1)
    private var lastBubble = -1
    private var lastPencil = -1
    private val lastOf = HashMap<List<Int>, Int>()

    /** One of [ids] at random, never the one played last from them. */
    private fun anyOf(ids: List<Int>): Int {
        var i = (Math.random() * ids.size).toInt().coerceIn(0, ids.size - 1)
        if (ids.size > 1 && i == lastOf[ids]) i = (i + 1) % ids.size
        lastOf[ids] = i
        return ids[i]
    }
    private var rubbingStream = 0
    private val audio = context.getSystemService(AudioManager::class.java)
    private var next = 0

    /** Plays [kind] at [volume] (0–1). */
    fun play(kind: Kind, volume: Float) {
        if (volume <= 0f) return
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (voice == Voice.GLASS) {
            val id = when (kind) {
                Kind.KEY -> {
                    var i = (Math.random() * bubbles.size).toInt().coerceIn(0, bubbles.size - 1)
                    if (i == lastBubble) i = (i + 1) % bubbles.size
                    lastBubble = i
                    bubbles[i]
                }
                Kind.SPACE -> anyOf(popsForSpace)
                Kind.RETURN -> anyOf(popsForReturn)
                Kind.DELETE -> anyOf(airs)
                Kind.BELL, Kind.SHEET -> return
            }
            pool.play(id, volume, volume, 1, 0, 1f)
            return
        }
        if (voice == Voice.SHEET) {
            val id = when (kind) {
                Kind.KEY -> {
                    var i = (Math.random() * pencil.size).toInt().coerceIn(0, pencil.size - 1)
                    if (i == lastPencil) i = (i + 1) % pencil.size
                    lastPencil = i
                    write(volume)
                    // Only a light touch of a stroke on top of the writing.
                    pool.play(pencil[i], volume * PEN_TOUCH, volume * PEN_TOUCH, 1, 0, 1f)
                    return
                }
                Kind.SPACE -> anyOf(taps)
                Kind.RETURN -> anyOf(strokes)
                Kind.DELETE -> {
                    // The eraser does not rub while the pencil writes.
                    haltWriting()
                    anyOf(erases)
                }
                Kind.BELL, Kind.SHEET -> return
            }
            pool.play(id, volume, volume, 1, 0, 1f)
            return
        }
        val id = when (kind) {
            Kind.KEY -> keys[next++ % keys.size]
            Kind.DELETE -> keys[2]
            Kind.SPACE -> space
            Kind.RETURN -> anyOf(enters)
            Kind.BELL -> bell
            Kind.SHEET -> sheet
        }
        // Every strike a little different, as real keys: ±5 % in pitch and 82-100 % loud (three strikes alone
        // would repeat); Backspace lower, as before.
        val vary = kind == Kind.KEY || kind == Kind.DELETE
        val pitch = (if (kind == Kind.DELETE) 0.85f else 1f) * (if (vary) 0.95f + Math.random().toFloat() * 0.1f else 1f)
        val loud = volume * (if (vary) 0.82f + Math.random().toFloat() * 0.18f else 1f)
        pool.play(id, loud, loud, 1, 0, pitch)
    }

    /** Keeps the pencil writing (starts or resumes it at [volume]); it fades out a moment after the last letter. */
    private fun write(volume: Float) {
        writeGen++
        fader.removeCallbacks(stopWriting)
        val v = volume * PEN_WRITE
        if (writeStream == 0) {
            writeStream = pool.play(writeLoop, v, v, 0, -1, 1f)
        } else {
            pool.setVolume(writeStream, v, v)
            if (!writing) pool.resume(writeStream)
        }
        writing = writeStream != 0
        writeVolume = v
        fader.postDelayed(stopWriting, WRITE_HOLD_MS)
    }

    private var writeVolume = 0f

    /** Stops the writing at once (Backspace, another style). */
    private fun haltWriting() {
        writeGen++
        fader.removeCallbacks(stopWriting)
        if (writeStream != 0 && writing) pool.pause(writeStream)
        writing = false
    }

    /** The writing dies away over ~150 ms and pauses (the next letter goes on from where it stopped). */
    private fun fadeWriting() {
        val stream = writeStream
        if (stream == 0 || !writing) return
        val gen = writeGen
        val steps = 5
        for (k in 1..steps) {
            fader.postDelayed({
                if (gen != writeGen) return@postDelayed
                val v = writeVolume * (1f - k / steps.toFloat())
                pool.setVolume(stream, v, v)
                if (k == steps) {
                    pool.pause(stream)
                    writing = false
                }
            }, k * 30L)
        }
    }

    /**
     * Wakes the phone's audio output with an almost silent strike, so the first real key is heard at once
     * (the output otherwise starts with the first sound, and the first letters came late). Runs again when
     * the sounds are still loading.
     */
    fun warm() {
        val id = when (voice) {
            Voice.SHEET -> pencil.first()
            Voice.GLASS -> bubbles.first()
            Voice.TYPEWRITER -> keys.first()
        }
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
        if (voice == Voice.TYPEWRITER || volume <= 0f) return false
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return true
        rubbingVolume = volume
        if (rubbingStream == 0) rubbingStream = pool.play(if (voice == Voice.GLASS) rustle else rubbing, volume, volume, 1, -1, 1f)
        return true
    }

    /** Lets the held sound go: it fades out over ~60 ms (cut off at once, its last sample clicked like a tap). */
    fun stopRubbing() {
        val stream = rubbingStream
        if (stream == 0) return
        rubbingStream = 0
        val start = rubbingVolume
        val steps = 6
        for (k in 1..steps) {
            fader.postDelayed({
                val v = start * (1f - k / steps.toFloat())
                pool.setVolume(stream, v, v)
                if (k == steps) pool.stop(stream)
            }, k * 10L)
        }
    }

    private val fader = android.os.Handler(android.os.Looper.getMainLooper())
    private var rubbingVolume = 1f

    fun release() = pool.release()

}
