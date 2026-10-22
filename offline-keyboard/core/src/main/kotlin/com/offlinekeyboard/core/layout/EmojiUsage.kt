package com.offlinekeyboard.core.layout

/**
 * How often each emoji (or sticker) was used, for the "most used" page. Kept small: only the
 * [MAX_ITEMS] most used survive, and counts are halved when they grow large, so new favourites can
 * catch up with old ones. Stored as one line of text: `😀=5\u0001👍=3`.
 */
class EmojiUsage private constructor(private val counts: LinkedHashMap<String, Int>) {

    constructor() : this(LinkedHashMap())

    fun record(item: String) {
        if (item.isEmpty()) return
        counts[item] = (counts[item] ?: 0) + 1
        if (counts.getValue(item) > HALVE_AT) {
            for (key in counts.keys.toList()) counts[key] = counts.getValue(key) / 2
            counts.values.removeAll { it == 0 }
        }
        if (counts.size > MAX_ITEMS) {
            counts.entries.minByOrNull { it.value }?.key?.let { counts.remove(it) }
        }
    }

    fun count(item: String): Int = counts[item] ?: 0

    /** Most used first; ties keep the order of first use. */
    fun top(limit: Int): List<String> =
        counts.entries.sortedByDescending { it.value }.take(limit).map { it.key }

    fun encode(): String = counts.entries.joinToString(SEPARATOR) { "${it.key}=${it.value}" }

    companion object {
        private const val SEPARATOR = "\u0001"
        const val MAX_ITEMS = 64
        private const val HALVE_AT = 1000

        fun decode(text: String?): EmojiUsage {
            val map = LinkedHashMap<String, Int>()
            text.orEmpty().split(SEPARATOR).forEach { entry ->
                val eq = entry.lastIndexOf('=')
                if (eq <= 0) return@forEach
                val n = entry.substring(eq + 1).toIntOrNull() ?: return@forEach
                if (n > 0) map[entry.substring(0, eq)] = n
            }
            return EmojiUsage(map)
        }
    }
}
