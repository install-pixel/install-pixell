package com.offlinekeyboard.core

import java.net.URLDecoder
import java.net.URLEncoder

enum class ShortcutAction {
    SWITCH_LANGUAGE, SWITCH_MODE, VOICE_INPUT, TOGGLE_TRANSLATE, TRANSLATE_SELECTION_OR_CLIPBOARD, HIDE_KEYBOARD, INSERT_TEXT,
}

/**
 * A user-defined key combination (computer mode). [key] is null for modifier-only combinations such as
 * Alt+Shift, which fire as soon as the second modifier is tapped. Otherwise [key] is a trigger id from
 * [ShortcutKeys.ids], e.g. "L", "7", "SPACE", "F2".
 */
data class Shortcut(
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
    val key: String? = null,
    val action: ShortcutAction,
    /** Text for [ShortcutAction.INSERT_TEXT]. */
    val text: String = "",
) {
    val modifierCount: Int get() = listOf(ctrl, alt, shift, meta).count { it }

    /** Shift alone (or nothing) would break normal typing, so a combination needs Ctrl, Alt or Win, or two modifiers. */
    val isValid: Boolean
        get() {
            val hasShortcutModifier = ctrl || alt || meta
            val keyOk = key == null || key in ShortcutKeys.ids
            val shape = if (key == null) modifierCount >= 2 else hasShortcutModifier
            val textOk = action != ShortcutAction.INSERT_TEXT || text.isNotEmpty()
            return keyOk && shape && textOk
        }

    fun matches(mods: com.offlinekeyboard.core.input.KeyMods, keyId: String?): Boolean =
        ctrl == mods.ctrl && alt == mods.alt && shift == mods.shift && meta == mods.meta && key == keyId

    /** Human-readable form, e.g. "Ctrl+Shift+L". */
    fun describe(): String = buildList {
        if (ctrl) add("Ctrl")
        if (alt) add("Alt")
        if (shift) add("Shift")
        if (meta) add("Win")
        key?.let { add(ShortcutKeys.label(it)) }
    }.joinToString("+")

    /** True if this combination replaces one of the keyboard's own PC shortcuts (Ctrl+C, Ctrl+Shift+Z ...). */
    val overridesBuiltIn: Boolean
        get() = ctrl && !alt && !meta && key in setOf("A", "C", "X", "V", "Z", "Y")

    companion object {
        val defaults: List<Shortcut> = listOf(
            Shortcut(alt = true, shift = true, action = ShortcutAction.SWITCH_LANGUAGE),
            Shortcut(meta = true, key = "SPACE", action = ShortcutAction.SWITCH_LANGUAGE),
        )

        private const val SEP = "|"

        fun encode(list: List<Shortcut>): String = list.joinToString("\n") { s ->
            val mods = buildList {
                if (s.ctrl) add("ctrl")
                if (s.alt) add("alt")
                if (s.shift) add("shift")
                if (s.meta) add("meta")
            }.joinToString(",")
            listOf(mods, s.key.orEmpty(), s.action.name, URLEncoder.encode(s.text, "UTF-8")).joinToString(SEP)
        }

        /** Ignores malformed lines rather than failing, so a bad entry never breaks the keyboard. */
        fun decode(encoded: String): List<Shortcut> = encoded.lines().mapNotNull { line ->
            val parts = line.split(SEP)
            if (parts.size != 4) return@mapNotNull null
            val mods = parts[0].split(",").toSet()
            val action = ShortcutAction.entries.firstOrNull { it.name == parts[2] } ?: return@mapNotNull null
            Shortcut(
                ctrl = "ctrl" in mods,
                alt = "alt" in mods,
                shift = "shift" in mods,
                meta = "meta" in mods,
                key = parts[1].ifEmpty { null },
                action = action,
                text = runCatching { URLDecoder.decode(parts[3], "UTF-8") }.getOrDefault(""),
            ).takeIf { it.isValid }
        }
    }
}

object ShortcutKeys {
    val ids: List<String> =
        ('A'..'Z').map { it.toString() } + ('0'..'9').map { it.toString() } +
            listOf("SPACE", "ENTER", "TAB") + (1..12).map { "F$it" }

    fun label(id: String): String = when (id) {
        "SPACE" -> "Space"
        "ENTER" -> "Enter"
        "TAB" -> "Tab"
        else -> id
    }
}
