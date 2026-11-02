package com.blacklaunch.qrcodes.qr

/** Що можна зробити із змістом коду (як камера телефона): відкрити сайт, подзвонити, підключити Wi‑Fi… */
sealed interface ContentAction {
    data class OpenUrl(val url: String) : ContentAction
    data class Dial(val number: String) : ContentAction
    data class Mail(val address: String) : ContentAction
    data class Map(val geo: String) : ContentAction
    data class Wifi(val ssid: String, val password: String, val type: String, val hidden: Boolean) : ContentAction
    data class Contact(val vcard: String) : ContentAction

    companion object {
        fun detect(content: String): ContentAction? {
            val c = content.trim()
            return when {
                Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(c) -> OpenUrl(c)
                Regex("^www\\.\\S+\\.\\S+$", RegexOption.IGNORE_CASE).matches(c) -> OpenUrl("https://$c")
                c.startsWith("tel:", ignoreCase = true) -> Dial(c.substring(4))
                c.startsWith("mailto:", ignoreCase = true) -> Mail(c.substring(7))
                c.startsWith("geo:", ignoreCase = true) -> Map(c)
                c.startsWith("WIFI:", ignoreCase = true) -> wifi(c)
                c.startsWith("BEGIN:VCARD", ignoreCase = true) -> Contact(c)
                else -> null
            }
        }

        /** WIFI:T:WPA;S:Мережа;P:пароль;H:true;; — з урахуванням екранування (\; \\, \\: \\\\). */
        fun wifi(c: String): Wifi? {
            val fields = HashMap<String, String>()
            var i = 5
            val key = StringBuilder()
            val value = StringBuilder()
            var inValue = false
            while (i < c.length) {
                val ch = c[i]
                when {
                    ch == '\\' && i + 1 < c.length -> {
                        (if (inValue) value else key).append(c[i + 1])
                        i++
                    }
                    ch == ':' && !inValue -> inValue = true
                    ch == ';' -> {
                        if (key.isNotEmpty()) fields[key.toString().uppercase()] = value.toString()
                        key.clear()
                        value.clear()
                        inValue = false
                    }
                    else -> (if (inValue) value else key).append(ch)
                }
                i++
            }
            val ssid = fields["S"] ?: return null
            return Wifi(ssid, fields["P"].orEmpty(), fields["T"].orEmpty().uppercase(), fields["H"].equals("true", ignoreCase = true))
        }
    }
}
