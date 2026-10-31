package com.blacklaunch.qrcodes.qr

/**
 * Готові тексти для QR-кодів у форматах, які телефон розуміє сам: наведеш камеру — він пропонує підключитися до
 * Wi‑Fi, зберегти контакт чи відкрити сайт. Користувач заповнює форму, а не пише службові рядки вручну.
 */
object Payloads {
    enum class WifiSecurity(val code: String) { WPA("WPA"), WEP("WEP"), NONE("nopass") }

    /** Wi‑Fi: `WIFI:T:WPA;S:назва;P:пароль;H:true;;` — спецсимволи \ ; , : " екрануються зворотною рискою. */
    fun wifi(ssid: String, password: String, security: WifiSecurity, hidden: Boolean): String = buildString {
        append("WIFI:T:").append(security.code).append(';')
        append("S:").append(escapeWifi(ssid)).append(';')
        if (security != WifiSecurity.NONE) append("P:").append(escapeWifi(password)).append(';')
        if (hidden) append("H:true;")
        append(';')
    }

    private fun escapeWifi(s: String): String = buildString {
        s.forEach { c ->
            if (c in "\\;,:\"") append('\\')
            append(c)
        }
    }

    /**
     * Контакт у форматі vCard 3.0 (розуміють і Android, і iPhone). Порожні поля пропускаються. Спецсимволи vCard
     * (\ ; ,) екрануються, переноси рядків — `\n`.
     */
    fun contact(name: String, phone: String, email: String, organization: String, note: String = ""): String = buildString {
        append("BEGIN:VCARD\n")
        append("VERSION:3.0\n")
        val n = name.trim()
        if (n.isNotEmpty()) {
            append("FN:").append(escapeVcard(n)).append('\n')
            // N: прізвище;ім'я — без складного розбору: усе як ім'я, щоб телефон показав так, як написано.
            append("N:;").append(escapeVcard(n)).append(";;;\n")
        }
        if (organization.isNotBlank()) append("ORG:").append(escapeVcard(organization.trim())).append('\n')
        if (phone.isNotBlank()) append("TEL:").append(phone.trim().replace(" ", "")).append('\n')
        if (email.isNotBlank()) append("EMAIL:").append(email.trim()).append('\n')
        if (note.isNotBlank()) append("NOTE:").append(escapeVcard(note.trim())).append('\n')
        append("END:VCARD")
    }

    private fun escapeVcard(s: String): String = s
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    /** Сайт: якщо не вказано, як відкривати (https://…), — додаємо https://. */
    fun url(address: String): String {
        val a = address.trim()
        if (a.isEmpty()) return ""
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(a)) a else "https://$a"
    }

    /** Коротка назва сайту для підпису: без https://, www. і хвоста після «/». */
    fun siteName(address: String): String = address.trim()
        .replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
        .removePrefix("www.")
        .substringBefore('/')
        .ifEmpty { address.trim() }
}
