package com.blacklaunch.launcher.global

/** Джерела глобального пошуку; порядок — знизу вгору над полем пошуку. */
enum class GroupKind { CALCULATOR, PROFILES, SETTINGS, SHORTCUTS, CONTACTS, CALENDAR, SMS }

/** Результат глобального пошуку (не програма). */
sealed interface GlobalResult {
    val key: String
    val title: String
    val subtitle: String?

    data class Calculation(val expression: String, val value: String) : GlobalResult {
        override val key get() = "calc:$expression"
        override val title get() = "= $value"
        override val subtitle get() = expression
    }

    data class Profile(val profileId: String, val name: String) : GlobalResult {
        override val key get() = "profile:$profileId"
        override val title get() = name
        override val subtitle: String? get() = null
    }

    /** [title] підставляє інтерфейс за [entryId] — мовою телефону. */
    data class Setting(val entryId: String, val action: String, override val title: String = entryId) : GlobalResult {
        override val key get() = "setting:$entryId"
        override val subtitle: String? get() = null
    }

    data class Shortcut(
        val packageName: String,
        val shortcutId: String,
        val userSerial: Long,
        override val title: String,
        val appLabel: String,
    ) : GlobalResult {
        override val key get() = "shortcut:$packageName/$shortcutId/$userSerial"
        override val subtitle get() = appLabel
    }

    data class Contact(val lookupUri: String, val name: String, val detail: String?) : GlobalResult {
        override val key get() = "contact:$lookupUri"
        override val title get() = name
        override val subtitle get() = detail
    }

    data class Event(val eventId: Long, override val title: String, val whenText: String) : GlobalResult {
        override val key get() = "event:$eventId"
        override val subtitle get() = whenText
    }

    data class Sms(val address: String, val sender: String, val snippet: String) : GlobalResult {
        override val key get() = "sms:$address:${snippet.hashCode()}"
        override val title get() = sender
        override val subtitle get() = snippet
    }
}

data class ResultGroup(val kind: GroupKind, val items: List<GlobalResult>)
