package com.blacklaunch.launcher.global

import com.blacklaunch.launcher.search.SearchNormalizer

/** Сторінки налаштувань конкретної програми, що відкриваються офіційно. */
enum class AppSettingKind { NOTIFICATIONS, BATTERY, INFO, DATA, LINKS }

/**
 * «сповіщення Telegram», «батарея spotify», «дозволи камера», «telegram powiadomienia» —
 * слово налаштування на початку або в кінці, решта — назва програми.
 */
object AppSettingsParser {
    private val words = mapOf(
        AppSettingKind.NOTIFICATIONS to setOf("сповіщення", "notifications", "notification", "powiadomienia"),
        AppSettingKind.BATTERY to setOf("батарея", "батарею", "акумулятор", "battery", "bateria", "baterię", "baterie"),
        AppSettingKind.INFO to setOf("дозволи", "інформація", "permissions", "info", "uprawnienia", "informacje"),
        AppSettingKind.DATA to setOf("дані", "трафік", "data", "dane"),
        AppSettingKind.LINKS to setOf("посилання", "links", "linki"),
    ).mapValues { (_, set) -> set.map(SearchNormalizer::normalize).toSet() }

    /** Вид налаштування і назва програми; null — це не налаштування програми. */
    fun parse(query: String): Pair<AppSettingKind, String>? {
        val tokens = SearchNormalizer.words(SearchNormalizer.normalize(query))
        if (tokens.size < 2) return null
        for ((kind, keys) in words) {
            val app = when {
                tokens.first() in keys -> tokens.drop(1)
                tokens.last() in keys -> tokens.dropLast(1)
                else -> continue
            }.joinToString(" ")
            if (app.length >= MIN_APP) return kind to app
        }
        return null
    }

    private const val MIN_APP = 2
}
