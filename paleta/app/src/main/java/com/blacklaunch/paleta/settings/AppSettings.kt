package com.blacklaunch.paleta.settings

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
 * Налаштування програми: мова, стиль, рядок стану і останні обмеження палети (висота, вага, палета у вазі, звис) —
 * один раз вибрав, і так лишається. Мова «як у телефоні»: українська → українська, польська → польська, будь-яка
 * інша → англійська (це роблять самі ресурси values / values-uk / values-pl).
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = AppLanguage.entries.firstOrNull { it.name == prefs.getString(KEY_LANG, null) } ?: AppLanguage.System
        set(value) = prefs.edit().putString(KEY_LANG, value.name).apply()

    /** true — «Скло» (Black Glass), false — «Аркуш» (від руки). Типово — Скло. */
    var glass: Boolean
        get() = prefs.getBoolean(KEY_GLASS, true)
        set(value) = prefs.edit().putBoolean(KEY_GLASS, value).apply()

    /** Ховати рядок стану (годинник, мережа, батарея) — лише свайпом згори, як у лаунчері. Типово — сховано. */
    var hideStatusBar: Boolean
        get() = prefs.getBoolean(KEY_HIDE_STATUS, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_STATUS, value).apply()

    /** Ліміт висоти разом з палетою, мм. Типово 1,80 м. */
    var heightLimit: Int
        get() = prefs.getInt(KEY_HEIGHT, 1800)
        set(value) = prefs.edit().putInt(KEY_HEIGHT, value).apply()

    /** Ліміт ваги, г. Типово 700 кг. */
    var weightLimitG: Long
        get() = prefs.getLong(KEY_WEIGHT, 700_000L)
        set(value) = prefs.edit().putLong(KEY_WEIGHT, value).apply()

    /** Чи додавати вагу палети. Типово — ні. */
    var palletOn: Boolean
        get() = prefs.getBoolean(KEY_PALLET_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_PALLET_ON, value).apply()

    /** Вага самої палети, г. Типово 25 кг. */
    var palletG: Long
        get() = prefs.getLong(KEY_PALLET_G, 25_000L)
        set(value) = prefs.edit().putLong(KEY_PALLET_G, value).apply()

    /** Дозволений звис з кожного боку, мм. Типово — без звису. */
    var overhang: Int
        get() = prefs.getInt(KEY_OVERHANG, 0)
        set(value) = prefs.edit().putInt(KEY_OVERHANG, value).apply()

    /** Ім'я та прізвище для рядка підпису в бланку (порожньо — рядок порожній). */
    var signer: String
        get() = prefs.getString(KEY_SIGNER, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_SIGNER, value).apply()

    /** «Усі коробки в один бік» (не змішувати напрямки в шарі). Типово — ні: змішувати можна, авто шукає максимум. */
    var uniform: Boolean
        get() = prefs.getBoolean(KEY_UNIFORM, false)
        set(value) = prefs.edit().putBoolean(KEY_UNIFORM, value).apply()

    companion object {
        private const val KEY_UNIFORM = "one_way"
        private const val KEY_SIGNER = "signer"
        private const val KEY_LANG = "language"
        private const val KEY_GLASS = "glass"
        private const val KEY_HIDE_STATUS = "hide_status"
        private const val KEY_HEIGHT = "height_limit"
        private const val KEY_WEIGHT = "weight_limit"
        private const val KEY_PALLET_ON = "pallet_on"
        private const val KEY_PALLET_G = "pallet_g"
        private const val KEY_OVERHANG = "overhang"

        /**
         * Мова, вибрана вручну, — як «перекриття» налаштувань активності (null — як у телефоні). Саму основу контексту
         * не підміняємо (так само, як у QRaft).
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
