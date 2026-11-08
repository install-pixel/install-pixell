package com.blacklaunch.diary.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Мова інтерфейсу: як у телефоні або одна з трьох (правило для всіх наших програм). */
enum class AppLanguage(val tag: String?) {
    System(null),
    Ukrainian("uk"),
    Polish("pl"),
    English("en"),
}

/** Через скільки «висихає» чорнило: далі текст дня можна лише викреслювати (дописувати — можна). */
enum class InkDrying(val hours: Long) {
    Day(24),
    Week(24 * 7),
}

/** Налаштування програми (спільні для всіх щоденників на полиці). */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = AppLanguage.entries.firstOrNull { it.name == prefs.getString(KEY_LANG, null) } ?: AppLanguage.System
        set(value) = prefs.edit().putString(KEY_LANG, value.name).apply()

    /** true — «Скло», false — «Аркуш». Типово — «Скло», як у решті програм. */
    var glass: Boolean
        get() = prefs.getBoolean(KEY_GLASS, true)
        set(value) = prefs.edit().putBoolean(KEY_GLASS, value).apply()

    /** Ховати рядок стану — лише свайпом згори, як у лаунчері. Типово — сховано. */
    var hideStatusBar: Boolean
        get() = prefs.getBoolean(KEY_HIDE_STATUS, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_STATUS, value).apply()

    /** Вібрація на гортанні, барабанах замка й кнопках. */
    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    /** Відкривати щоденник на закладці (true) чи завжди на сьогоднішньому дні (false). */
    var openAtBookmark: Boolean
        get() = prefs.getBoolean(KEY_OPEN_BOOKMARK, true)
        set(value) = prefs.edit().putBoolean(KEY_OPEN_BOOKMARK, value).apply()

    var inkDrying: InkDrying
        get() = InkDrying.entries.firstOrNull { it.name == prefs.getString(KEY_DRYING, null) } ?: InkDrying.Day
        set(value) = prefs.edit().putString(KEY_DRYING, value.name).apply()

    companion object {
        private const val KEY_LANG = "language"
        private const val KEY_GLASS = "glass"
        private const val KEY_HIDE_STATUS = "hide_status"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_OPEN_BOOKMARK = "open_bookmark"
        private const val KEY_DRYING = "ink_drying"

        /**
         * Мова, вибрана вручну, — як «перекриття» налаштувань активності (null — як у телефоні).
         */
        fun languageOverride(base: Context): Configuration? {
            val tag = AppSettings(base).language.tag
            if (tag == null) {
                Locale.setDefault(base.resources.configuration.locales[0])
                return null
            }
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            return Configuration().apply { setLocale(locale) }
        }
    }
}
