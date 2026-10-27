package com.blacklaunch.launcher.games

/**
 * Збереження стану гри простим рядком «ключ=значення;…» — без бібліотек, щоб двигуни ігор лишались чистим Kotlin
 * (перевіряються тестами без Android). Пошкоджений рядок — гра просто починається заново.
 */
internal class GameWriter {
    private val parts = ArrayList<String>()
    fun put(key: String, value: Any) = apply { parts += "$key=$value" }
    fun putList(key: String, values: List<Any>) = apply { parts += "$key=" + values.joinToString(",") }
    override fun toString() = parts.joinToString(";")
}

internal class GameReader(text: String) {
    private val map = text.split(';').mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
    }.toMap()

    fun int(key: String): Int? = map[key]?.toIntOrNull()
    fun float(key: String): Float? = map[key]?.toFloatOrNull()
    fun string(key: String): String? = map[key]
    fun ints(key: String): List<Int>? = map[key]?.let { v -> if (v.isEmpty()) emptyList() else v.split(',').map { it.toIntOrNull() ?: return null } }
}

/** Подія для вібрації: легка (влучив, з'їв), помітна (рядок, прокачка), сильна (збили, врізався). */
internal enum class GameBuzz { LIGHT, MEDIUM, STRONG }

/** Що спільне в усіх ігор: рахунок, рівень, кінець гри, збереження, події для вібрації. */
internal interface ArcadeGame {
    val score: Int
    val level: Int
    val over: Boolean
    fun step(dt: Float)
    fun encode(): String

    /** Події з минулого разу (і очистити). */
    fun drainBuzz(): List<GameBuzz>
}

/** Черга подій вібрації — спільна для ігор. */
internal class BuzzQueue {
    private val list = ArrayList<GameBuzz>()
    fun add(b: GameBuzz) {
        if (list.size < 8) list += b
    }
    fun drain(): List<GameBuzz> = list.toList().also { list.clear() }
}
