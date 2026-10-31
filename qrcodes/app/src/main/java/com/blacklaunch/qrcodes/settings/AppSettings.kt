package com.blacklaunch.qrcodes.settings

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

/**
 * Налаштування програми: мова й стиль. Мова «як у телефоні»: українська → українська, польська → польська,
 * будь-яка інша → англійська (це роблять самі ресурси values / values-uk / values-pl).
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = AppLanguage.entries.firstOrNull { it.name == prefs.getString(KEY_LANG, null) } ?: AppLanguage.System
        set(value) = prefs.edit().putString(KEY_LANG, value.name).apply()

    /** true — «Скло» (Black Glass), false — «Аркуш» (від руки). */
    var glass: Boolean
        get() = prefs.getBoolean(KEY_GLASS, false)
        set(value) = prefs.edit().putBoolean(KEY_GLASS, value).apply()

    companion object {
        private const val KEY_LANG = "language"
        private const val KEY_GLASS = "glass"

        /** Контекст із вибраною мовою (для Activity.attachBaseContext). «Як у телефоні» — без змін. */
        fun wrap(base: Context): Context {
            val tag = AppSettings(base).language.tag
            if (tag == null) {
                // «Як у телефоні»: повертаємо системну мову (на випадок, якщо до цього була вибрана інша).
                Locale.setDefault(base.resources.configuration.locales[0])
                return base
            }
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }
    }
}
