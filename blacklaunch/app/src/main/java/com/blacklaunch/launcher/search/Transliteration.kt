package com.blacklaunch.launcher.search

/**
 * Спрощена транслітерація кирилиці латиницею, орієнтована на назви брендів
 * («г» → «g», як у Telegram і Google). Працює з уже нормалізованим текстом.
 */
object Transliteration {
    private val map = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
        'є' to "ye", 'ж' to "zh", 'з' to "z", 'и' to "y", 'і' to "i", 'ї' to "yi",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f",
        'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ь' to "",
        'ю' to "yu", 'я' to "ya", 'ы' to "y", 'э' to "e", 'ъ' to "",
    )

    fun hasCyrillic(text: String): Boolean = text.any { it in map }

    fun toLatin(text: String): String =
        buildString { text.forEach { append(map[it] ?: it) } }
}
