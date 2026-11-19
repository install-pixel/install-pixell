package com.offlinekeyboard.keyboard

import android.content.Context

/**
 * Where the user's finger really lands on each letter key, learned from their taps (only on the phone, in
 * the keyboard's own preferences): the average offset from the key's middle, as a share of the key's size.
 * A touch between two keys then goes to the key whose learned spot is nearer, so the invisible border moves
 * towards where the user actually aims. Offsets are small and need a number of taps before they count.
 */
class TouchModel(private val context: Context) {

    private class Spot(var dx: Float, var dy: Float, var taps: Int)

    private val spots = HashMap<String, Spot>()
    private var unsaved = 0
    private var loadedAt = System.currentTimeMillis()

    init {
        runCatching {
            val text = prefs().getString(KEY, null) ?: return@runCatching
            for (entry in text.split(';')) {
                val parts = entry.split(',')
                if (parts.size != 4) continue
                spots[parts[0]] = Spot(parts[1].toFloat(), parts[2].toFloat(), parts[3].toInt())
            }
        }
    }

    /** The learned offset of [letter] (x, y as shares of the key's width and height), weighted by how sure it is. */
    fun offset(letter: String): Pair<Float, Float> {
        val s = spots[letter] ?: return 0f to 0f
        val weight = (s.taps / SURE_AFTER.toFloat()).coerceAtMost(1f)
        return s.dx * weight to s.dy * weight
    }

    /** A tap on [letter], [dx] and [dy] from the key's middle as shares of its size. */
    fun learn(letter: String, dx: Float, dy: Float) {
        if (letter.length != 1 || !letter[0].isLetter()) return
        val s = spots.getOrPut(letter) { Spot(0f, 0f, 0) }
        val rate = maxOf(MIN_RATE, 1f / (s.taps + 1))
        s.dx = (s.dx + (dx.coerceIn(-1f, 1f) - s.dx) * rate).coerceIn(-MAX_OFFSET, MAX_OFFSET)
        s.dy = (s.dy + (dy.coerceIn(-1f, 1f) - s.dy) * rate).coerceIn(-MAX_OFFSET, MAX_OFFSET)
        s.taps = (s.taps + 1).coerceAtMost(MAX_TAPS)
        if (++unsaved >= SAVE_EVERY) save()
    }

    /** Drops what was learned if it was forgotten in the settings since (call when a field starts). */
    fun refresh() {
        val resetAt = runCatching { prefs().getLong(RESET_AT, 0L) }.getOrDefault(0L)
        if (resetAt > loadedAt) {
            spots.clear()
            unsaved = 0
            loadedAt = System.currentTimeMillis()
        }
    }

    fun save() {
        refresh()
        if (unsaved == 0) return
        unsaved = 0
        val text = spots.entries.joinToString(";") { (k, s) -> "$k,${s.dx},${s.dy},${s.taps}" }
        runCatching { prefs().edit().putString(KEY, text).apply() }
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS = "touch_model"
        private const val KEY = "spots"
        private const val RESET_AT = "reset_at"
        /** Taps before a key's learned spot counts fully. */
        private const val SURE_AFTER = 30
        private const val MIN_RATE = 0.04f
        /** A spot never moves further than this share of the key from its middle. */
        private const val MAX_OFFSET = 0.3f
        private const val MAX_TAPS = 10_000
        private const val SAVE_EVERY = 25

        /** Forgets everything learned (the settings' reset). */
        fun reset(context: Context) {
            runCatching {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .clear().putLong(RESET_AT, System.currentTimeMillis()).apply()
            }
        }
    }
}
