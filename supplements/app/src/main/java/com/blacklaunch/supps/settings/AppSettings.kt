package com.blacklaunch.supps.settings

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

/** Налаштування програми. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = AppLanguage.entries.firstOrNull { it.name == prefs.getString(KEY_LANG, null) } ?: AppLanguage.System
        set(value) = prefs.edit().putString(KEY_LANG, value.name).apply()

    /** true — «Скло», false — «Аркуш» (від руки). */
    var glass: Boolean
        get() = prefs.getBoolean(KEY_GLASS, true)
        set(value) = prefs.edit().putBoolean(KEY_GLASS, value).apply()

    /** Ховати рядок стану — лише свайпом згори, як у лаунчері. Типово — сховано. */
    var hideStatusBar: Boolean
        get() = prefs.getBoolean(KEY_HIDE_STATUS, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_STATUS, value).apply()

    /**
     * О котрій починається новий день (0–6). Після другої зміни лягаєш після півночі — вечірні добавки мають
     * рахуватися ще за «вчора».
     */
    var dayStartHour: Int
        get() = prefs.getInt(KEY_DAY_START, 4).coerceIn(0, 6)
        set(value) = prefs.edit().putInt(KEY_DAY_START, value.coerceIn(0, 6)).apply()

    /** Позначка валюти біля сум. */
    var currency: String
        get() = prefs.getString(KEY_CURRENCY, null) ?: "zł"
        set(value) = prefs.edit().putString(KEY_CURRENCY, value.trim().take(6)).apply()

    /** Скільки питань у щоденній оцінці (1–4). */
    var questionCount: Int
        get() = prefs.getInt(KEY_Q_COUNT, 3).coerceIn(1, 4)
        set(value) = prefs.edit().putInt(KEY_Q_COUNT, value.coerceIn(1, 4)).apply()

    /** Свій текст питання (порожньо — типовий текст із ресурсів). */
    fun question(i: Int): String = prefs.getString("$KEY_Q$i", null).orEmpty()

    fun setQuestion(i: Int, text: String) = prefs.edit().putString("$KEY_Q$i", text.trim().take(40)).apply()

    companion object {
        private const val KEY_LANG = "language"
        private const val KEY_GLASS = "glass"
        private const val KEY_HIDE_STATUS = "hide_status"
        private const val KEY_DAY_START = "day_start"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_Q_COUNT = "q_count"
        private const val KEY_Q = "q_"

        /** Мова, вибрана вручну, — як «перекриття» налаштувань активності (null — як у телефоні). */
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
