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
        // Типово — Black Glass (на новому телефоні); вибір у Налаштуваннях запам'ятовується.
        get() = prefs.getBoolean(KEY_GLASS, true)
        set(value) = prefs.edit().putBoolean(KEY_GLASS, value).apply()

    /** Останній вибір на «Створити»: QR чи штрихкод — так і відкривається наступного разу. */
    var lastFormat: com.blacklaunch.qrcodes.qr.CodeKind
        get() = com.blacklaunch.qrcodes.qr.CodeKind.of(prefs.getString(KEY_FORMAT, null))
        set(value) = prefs.edit().putString(KEY_FORMAT, value.storage).apply()

    /** Форма для сканера: яскравість на максимум, поки відкрита (решта екрана тоді приглушена — світиться код). */
    var formBright: Boolean
        get() = prefs.getBoolean(KEY_FORM_BRIGHT, true)
        set(value) = prefs.edit().putBoolean(KEY_FORM_BRIGHT, value).apply()

    companion object {
        private const val KEY_FORM_BRIGHT = "form_bright"
        private const val KEY_FORMAT = "format"
        private const val KEY_LANG = "language"
        private const val KEY_GLASS = "glass"

        /** Контекст із вибраною мовою (для Activity.attachBaseContext). «Як у телефоні» — без змін. */
        /**
         * Мова, вибрана вручну, — як «перекриття» налаштувань активності (null — як у телефоні). Саму основу контексту не
         * підміняємо: інакше системні служби (друк) бачать не активність і відмовляють («друкувати можна лише з
         * активності»), і програма вилітає.
         */
        fun languageOverride(base: Context): Configuration? {
            val tag = AppSettings(base).language.tag
            if (tag == null) {
                // «Як у телефоні»: повертаємо системну мову (на випадок, якщо до цього була вибрана інша).
                Locale.setDefault(base.resources.configuration.locales[0])
                return null
            }
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            return Configuration().apply { setLocale(locale) }
        }
    }
}
