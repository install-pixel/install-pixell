package com.blacklaunch.launcher.search

/**
 * Грубий «звуковий скелет» латинського слова, щоб «спотіфай» (spotifay) збігався зі Spotify,
 * а «вотсап» (votsap) — з WhatsApp. Голосове розпізнавання часто пише англійські назви кирилицею.
 */
object Phonetic {
    private val rules = listOf(
        "wh" to "v", "ph" to "f", "ck" to "k", "qu" to "kv", "th" to "t", "sch" to "sh",
        "x" to "ks", "q" to "k", "w" to "v", "c" to "k", "j" to "y", "y" to "i", "ee" to "i", "oo" to "u",
    )

    /** Працює з нормалізованим текстом; кирилицю спершу транслітерує. */
    fun skeleton(normalized: String): String {
        var text = Transliteration.toLatin(normalized).filter { it.isLetterOrDigit() }
        rules.forEach { (from, to) -> text = text.replace(from, to) }
        // Подвоєні літери: «settings» ≈ «setings», «whatsapp» ≈ «vatsap».
        return text.fold(StringBuilder()) { acc, ch -> if (acc.lastOrNull() != ch) acc.append(ch); acc }.toString()
    }

    /** Схожість 0..1 за відстанню Левенштейна. */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val distance = levenshtein(a, b)
        return 1.0 - distance.toDouble() / maxOf(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
