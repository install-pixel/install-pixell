package com.blacklaunch.launcher.global

import com.blacklaunch.launcher.search.IndexedName
import com.blacklaunch.launcher.search.NameMatcher
import com.blacklaunch.launcher.search.PreparedQuery

/**
 * Розділи системних налаштувань, які можна відкрити офіційною дією Android.
 * Відкривається розділ, а не конкретний перемикач: внутрішній пошук налаштувань
 * стороннім програмам недоступний.
 */
object SettingsCatalog {
    data class Entry(val id: String, val action: String, val keywords: List<String>) {
        val names: List<IndexedName> = keywords.map(::IndexedName)
    }

    val entries: List<Entry> = listOf(
        Entry("wifi", "android.settings.WIFI_SETTINGS", listOf("wi-fi", "wifi", "вайфай", "вай фай", "бездротова мережа", "інтернет", "internet", "sieć", "wlan")),
        Entry("bluetooth", "android.settings.BLUETOOTH_SETTINGS", listOf("bluetooth", "блютуз", "блютус", "навушники", "słuchawki", "headphones")),
        Entry("mobile_data", "android.settings.DATA_USAGE_SETTINGS", listOf("мобільні дані", "мобільний інтернет", "трафік", "дані", "dane mobilne", "mobile data", "data usage")),
        Entry("airplane", "android.settings.AIRPLANE_MODE_SETTINGS", listOf("режим польоту", "літак", "tryb samolotowy", "samolot", "airplane", "flight mode")),
        Entry("display", "android.settings.DISPLAY_SETTINGS", listOf("дисплей", "екран", "яскравість", "темна тема", "шрифт", "wyświetlacz", "ekran", "jasność", "display", "screen", "brightness", "dark mode", "font")),
        Entry("night_light", "android.settings.NIGHT_DISPLAY_SETTINGS", listOf("нічний режим", "нічне світло", "захист очей", "tryb nocny", "night light", "eye comfort")),
        Entry("sound", "android.settings.SOUND_SETTINGS", listOf("звук", "гучність", "рингтон", "мелодія", "вібрація", "dźwięk", "głośność", "dzwonek", "sound", "volume", "ringtone", "vibration")),
        Entry("notifications", "android.settings.NOTIFICATION_SETTINGS", listOf("сповіщення", "повідомлення", "powiadomienia", "notifications")),
        Entry("dnd", "android.settings.ZEN_MODE_SETTINGS", listOf("не турбувати", "тиша", "nie przeszkadzać", "do not disturb", "dnd")),
        Entry("battery", "android.intent.action.POWER_USAGE_SUMMARY", listOf("батарея", "акумулятор", "заряд", "енергозбереження", "bateria", "akumulator", "battery", "power saving")),
        Entry("storage", "android.settings.INTERNAL_STORAGE_SETTINGS", listOf("пам'ять", "сховище", "місце", "pamięć", "miejsce", "storage", "memory", "space")),
        Entry("apps", "android.settings.APPLICATION_SETTINGS", listOf("програми", "додатки", "застосунки", "aplikacje", "apps", "applications")),
        Entry("default_apps", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", listOf("програми за замовчуванням", "головний екран", "лаунчер", "domyślne aplikacje", "default apps", "launcher", "home screen")),
        Entry("location", "android.settings.LOCATION_SOURCE_SETTINGS", listOf("геолокація", "місцезнаходження", "gps", "джипіес", "lokalizacja", "location")),
        Entry("security", "android.settings.SECURITY_SETTINGS", listOf("безпека", "блокування екрана", "відбиток", "пароль", "bezpieczeństwo", "blokada ekranu", "odcisk", "security", "screen lock", "fingerprint", "password")),
        Entry("privacy", "android.settings.PRIVACY_SETTINGS", listOf("конфіденційність", "дозволи", "prywatność", "uprawnienia", "privacy", "permissions")),
        Entry("accounts", "android.settings.SYNC_SETTINGS", listOf("акаунти", "облікові записи", "синхронізація", "konta", "synchronizacja", "accounts", "sync")),
        Entry("language", "android.settings.LOCALE_SETTINGS", listOf("мова", "мови", "język", "language")),
        Entry("keyboard", "android.settings.INPUT_METHOD_SETTINGS", listOf("клавіатура", "введення", "klawiatura", "keyboard", "input")),
        Entry("date_time", "android.settings.DATE_SETTINGS", listOf("дата", "час", "годинник", "часовий пояс", "data", "czas", "strefa czasowa", "date", "time", "time zone")),
        Entry("accessibility", "android.settings.ACCESSIBILITY_SETTINGS", listOf("спеціальні можливості", "доступність", "ułatwienia dostępu", "accessibility")),
        Entry("nfc", "android.settings.NFC_SETTINGS", listOf("nfc", "енфісі", "безконтактна оплата", "płatności zbliżeniowe", "contactless")),
        Entry("vpn", "android.settings.VPN_SETTINGS", listOf("vpn", "впн")),
        Entry("cast", "android.settings.CAST_SETTINGS", listOf("трансляція", "дублювання екрана", "przesyłanie", "cast", "screen mirroring")),
        Entry("about", "android.settings.DEVICE_INFO_SETTINGS", listOf("про телефон", "модель", "версія android", "o telefonie", "about phone", "android version")),
        Entry("developer", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS", listOf("для розробників", "розробник", "opcje programisty", "developer options")),
    )

    /** Розділи, що підходять до запиту; лише впевнені збіги (не менше «початку слова»). */
    fun search(query: PreparedQuery, limit: Int = 4): List<GlobalResult.Setting> {
        if (query.text.length < MIN_QUERY) return emptyList()
        return entries
            .map { it to NameMatcher.score(it.names, query) }
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (entry, _) -> GlobalResult.Setting(entry.id, entry.action) }
    }

    private const val MIN_QUERY = 3
    private const val MIN_SCORE = NameMatcher.ALL_WORDS
}
