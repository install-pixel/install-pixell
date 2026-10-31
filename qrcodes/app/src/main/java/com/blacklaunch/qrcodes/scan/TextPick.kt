package com.blacklaunch.qrcodes.scan

/**
 * Які тексти з фото пропонувати для QR-коду: лише довші за [MIN_LENGTH] символів (без пробілів по краях) —
 * короткі шматки камера не «плодить», їх простіше ввести вручну. Однакові рядки — один раз.
 */
object TextPick {
    const val MIN_LENGTH = 5

    fun accept(text: String): Boolean = text.trim().length > MIN_LENGTH

    fun <T> filter(items: List<T>, text: (T) -> String): List<T> {
        val seen = HashSet<String>()
        return items.filter { item ->
            val t = text(item).trim()
            accept(t) && seen.add(t)
        }
    }
}
