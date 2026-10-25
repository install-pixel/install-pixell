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
        // Глибше: офіційні сторінки Android, що відкриваються напряму (0.21.0).
        Entry("network", "android.settings.WIRELESS_SETTINGS", listOf("мережа", "мережа та інтернет", "з'єднання", "sieć i internet", "połączenia", "network", "connections")),
        Entry("sim", "android.settings.NETWORK_OPERATOR_SETTINGS", listOf("sim", "сім", "сім-карта", "оператор", "karta sim", "operator", "carrier")),
        Entry("roaming", "android.settings.DATA_ROAMING_SETTINGS", listOf("роумінг", "мобільна мережа", "roaming", "sieć komórkowa", "mobile network")),
        Entry("hotspot", "android.settings.TETHER_SETTINGS", listOf("точка доступу", "роздати інтернет", "модем", "hotspot", "punkt dostępu", "tethering", "udostępnianie internetu")),
        Entry("battery_saver", "android.settings.BATTERY_SAVER_SETTINGS", listOf("енергозбереження", "економія батареї", "oszczędzanie baterii", "battery saver", "power saving mode")),
        Entry("battery_optimization", "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS", listOf("оптимізація батареї", "optymalizacja baterii", "battery optimization")),
        Entry("all_apps", "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS", listOf("усі програми", "всі додатки", "список програм", "wszystkie aplikacje", "all apps")),
        Entry("home_app", "android.settings.HOME_SETTINGS", listOf("головна програма", "вибір лаунчера", "aplikacja ekranu głównego", "home app")),
        Entry("usage_access", "android.settings.USAGE_ACCESS_SETTINGS", listOf("доступ до використання", "статистика використання", "dostęp do danych o użyciu", "usage access")),
        Entry("overlay", "android.settings.action.MANAGE_OVERLAY_PERMISSION", listOf("поверх інших програм", "накладання", "wyświetlanie nad innymi", "display over other apps", "overlay")),
        Entry("write_settings", "android.settings.action.MANAGE_WRITE_SETTINGS", listOf("зміна системних налаштувань", "modyfikowanie ustawień systemu", "modify system settings")),
        Entry("notification_access", "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", listOf("доступ до сповіщень", "dostęp do powiadomień", "notification access")),
        Entry("dnd_access", "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS", listOf("доступ не турбувати", "dostęp nie przeszkadzać", "do not disturb access")),
        Entry("unknown_sources", "android.settings.MANAGE_UNKNOWN_APP_SOURCES", listOf("невідомі джерела", "встановлення програм", "apk", "nieznane źródła", "instalowanie aplikacji", "unknown sources", "install unknown apps")),
        Entry("captions", "android.settings.CAPTIONING_SETTINGS", listOf("субтитри", "napisy", "captions", "subtitles")),
        Entry("hard_keyboard", "android.settings.HARD_KEYBOARD_SETTINGS", listOf("фізична клавіатура", "klawiatura fizyczna", "physical keyboard")),
        Entry("voice_input", "android.settings.VOICE_INPUT_SETTINGS", listOf("голосовий ввід", "голосовий помічник", "asystent", "wprowadzanie głosowe", "voice input", "assistant")),
        Entry("dictionary", "android.settings.USER_DICTIONARY_SETTINGS", listOf("словник", "słownik", "dictionary")),
        Entry("screensaver", "android.settings.DREAM_SETTINGS", listOf("заставка", "wygaszacz ekranu", "screen saver", "daydream")),
        Entry("print", "android.settings.ACTION_PRINT_SETTINGS", listOf("друк", "принтер", "drukowanie", "drukarka", "printing", "printer")),
        Entry("add_account", "android.settings.ADD_ACCOUNT_SETTINGS", listOf("додати акаунт", "новий обліковий запис", "dodaj konto", "add account")),
        Entry("nfc_payment", "android.settings.NFC_PAYMENT_SETTINGS", listOf("оплата nfc", "безконтактні платежі", "гаманець", "płatności nfc", "portfel", "tap to pay", "wallet")),
        Entry("device_name", "android.settings.DEVICE_NAME", listOf("назва пристрою", "ім'я телефону", "nazwa urządzenia", "device name")),
        Entry("biometrics", "android.settings.BIOMETRIC_ENROLL", listOf("відбиток пальця", "розблокування обличчям", "біометрія", "odcisk palca", "rozpoznawanie twarzy", "fingerprint", "face unlock", "biometrics")),
        Entry("auto_rotate", "android.settings.AUTO_ROTATE_SETTINGS", listOf("автоповорот", "поворот екрана", "autoobracanie", "auto rotate", "rotation")),
        Entry("settings", "android.settings.SETTINGS", listOf("налаштування", "параметри", "ustawienia", "settings")),
        // Власний екран Black Glass, не сторінка Android: відкривається в самому лаунчері.
        Entry("monitor", MONITOR_ACTION, listOf("моніторинг", "monitoring", "стан системи", "оперативна пам'ять", "температура телефона", "монітор", "dashboard", "system dashboard", "panel", "panel systemu", "ram")),
    )

    /** «Дія» розділу «Монітор системи» — Black Glass відкриває свій екран замість налаштувань Android. */
    const val MONITOR_ACTION = "com.blacklaunch.launcher.MONITOR"

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
