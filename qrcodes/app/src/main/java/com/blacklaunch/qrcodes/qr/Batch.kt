package com.blacklaunch.qrcodes.qr

/** Багато кодів одразу: серія з номерами або список рядків. */
object Batch {
    /** Найбільше за раз — щоб випадково не створити десятки тисяч. */
    const val MAX = 1000

    /**
     * Серія: [prefix] + номер + [suffix]; номер від [start] з кроком [step], доповнений нулями до [digits] цифр
     * (0 — без нулів). Напр. «ПАЛЕТА-» + 001…100.
     */
    fun series(prefix: String, start: Long, count: Int, digits: Int, suffix: String = "", step: Long = 1): List<String> =
        (0 until count.coerceIn(0, MAX)).map { i ->
            val n = start + i * step
            val num = if (digits > 0) n.toString().padStart(digits, '0') else n.toString()
            prefix + num + suffix
        }

    /** Список: кожен непорожній рядок — окремий код; повтори прибираються, порядок лишається. */
    fun lines(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX)
}
