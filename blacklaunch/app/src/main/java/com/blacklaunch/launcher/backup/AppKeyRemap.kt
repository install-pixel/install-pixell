package com.blacklaunch.launcher.backup

/**
 * Ключі програм із резервної копії («пакет/activity/користувач») → ключі на цьому телефоні.
 * Номер користувача на іншому телефоні може відрізнятися, тож шукаємо за пакетом і activity;
 * програми, якої немає, пропускаємо.
 */
class AppKeyRemap(installedKeys: Collection<String>) {
    private val exact = installedKeys.toSet()
    private val byComponent = installedKeys.groupBy { it.substringBeforeLast('/') }

    var skipped = 0
        private set

    fun map(key: String): String? {
        val found = if (key in exact) key else byComponent[key.substringBeforeLast('/')]?.firstOrNull()
        if (found == null) skipped++
        return found
    }

    fun mapAll(keys: Collection<String>): List<String> = keys.mapNotNull(::map).distinct()
}
