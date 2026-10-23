package com.blacklaunch.launcher.apps

/**
 * Програма, яку можна запустити з лаунчера. Без Android-типів,
 * щоб пошук і ViewModel тестувалися звичайними JVM-тестами.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    /** Серійний номер профілю користувача Android (основний або робочий). */
    val userSerial: Long,
) {
    val key: String get() = "$packageName/$activityName/$userSerial"
}
