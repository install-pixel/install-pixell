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

    /** Різні ще не встановлені програми (для підсумку — кожна один раз). */
    private val missing = HashSet<String>()
    val waiting: Int get() = missing.size

    fun map(key: String): String? {
        val found = if (key in exact) key else byComponent[key.substringBeforeLast('/')]?.firstOrNull()
        if (found == null) {
            skipped++
            missing += key.substringBeforeLast('/')
        }
        return found
    }

    fun mapAll(keys: Collection<String>): List<String> = keys.mapNotNull(::map).distinct()

    /**
     * Як [map], але програму, якої ще немає, запам'ятовуємо як є: коли її встановлять,
     * вона сама з'явиться у своєму профілі й на своєму місці.
     */
    fun mapOrKeep(key: String): String = map(key) ?: key

    fun mapAllOrKeep(keys: Collection<String>): List<String> = keys.map(::mapOrKeep).distinct()
}
