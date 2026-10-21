package com.offlinekeyboard.core

/**
 * A shareable set of shortcuts plus a cheat sheet of the app's own key combinations, stored as plain
 * text so it can be written by hand and kept on GitHub (`shortcut-presets/` in the repository):
 *
 * ```
 * # comment
 * title = Vim
 * title.uk = Vim
 * about.uk = Команди звичайного режиму.
 * tip = Esc — normal mode
 * tip.uk = Esc — звичайний режим
 * Alt+W = text ":w\n"
 * Win+Space = language
 * ```
 *
 * `key.<lang>` lines override `key` for that interface language. Actions: language, mode, voice,
 * translate, translate-clipboard, hide, text "…" (with \n, \t, \" and \\ escapes).
 */
data class ShortcutPreset(
    val title: String,
    val about: String,
    /** Combinations the target app already understands; shown as a cheat sheet, nothing to install. */
    val tips: List<String>,
    val shortcuts: List<Shortcut>,
) {
    companion object {

        private val actionNames = mapOf(
            "language" to ShortcutAction.SWITCH_LANGUAGE,
            "mode" to ShortcutAction.SWITCH_MODE,
            "voice" to ShortcutAction.VOICE_INPUT,
            "translate" to ShortcutAction.TOGGLE_TRANSLATE,
            "translate-clipboard" to ShortcutAction.TRANSLATE_SELECTION_OR_CLIPBOARD,
            "hide" to ShortcutAction.HIDE_KEYBOARD,
        )

        /**
         * Parses [source] for interface [language] (e.g. "uk"). Throws [IllegalArgumentException] with the
         * line number when a line is not understood, so a broken file is reported instead of half-imported.
         */
        fun parse(source: String, language: String): ShortcutPreset {
            val plain = HashMap<String, String>()
            val localized = HashMap<String, String>()
            val plainTips = ArrayList<String>()
            val localizedTips = ArrayList<String>()
            val shortcuts = ArrayList<Shortcut>()

            source.lines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val eq = line.indexOf('=')
                require(eq > 0) { "line ${index + 1}: expected «name = value»" }
                val name = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim()
                val field = name.substringBefore('.').lowercase()
                val lang = name.substringAfter('.', "").lowercase()
                when (field) {
                    "title", "about" -> when {
                        lang.isEmpty() -> plain[field] = value
                        lang == language -> localized[field] = value
                    }
                    "tip" -> when {
                        lang.isEmpty() -> plainTips += value
                        lang == language -> localizedTips += value
                    }
                    else -> shortcuts += parseShortcut(name, value) ?: throw IllegalArgumentException(
                        "line ${index + 1}: cannot read «$line»",
                    )
                }
            }
            val title = localized["title"] ?: plain["title"]
            require(!title.isNullOrBlank()) { "the file has no «title = …» line" }
            return ShortcutPreset(
                title = title,
                about = localized["about"] ?: plain["about"].orEmpty(),
                tips = localizedTips.ifEmpty { plainTips },
                shortcuts = shortcuts,
            )
        }

        private fun parseShortcut(combo: String, value: String): Shortcut? {
            var ctrl = false
            var alt = false
            var shift = false
            var meta = false
            var key: String? = null
            for (part in combo.split('+').map { it.trim() }) {
                when (part.lowercase()) {
                    "ctrl" -> ctrl = true
                    "alt" -> alt = true
                    "shift" -> shift = true
                    "win", "meta" -> meta = true
                    else -> {
                        if (key != null) return null
                        key = ShortcutKeys.ids.firstOrNull { it.equals(part, ignoreCase = true) } ?: return null
                    }
                }
            }
            val shortcut = if (value.startsWith("text")) {
                val text = unquote(value.removePrefix("text").trim()) ?: return null
                Shortcut(ctrl, alt, shift, meta, key, ShortcutAction.INSERT_TEXT, text)
            } else {
                Shortcut(ctrl, alt, shift, meta, key, actionNames[value.lowercase()] ?: return null)
            }
            return shortcut.takeIf { it.isValid }
        }

        private fun unquote(value: String): String? {
            if (value.length < 2 || !value.startsWith('"') || !value.endsWith('"')) return null
            val body = value.substring(1, value.length - 1)
            return buildString {
                var i = 0
                while (i < body.length) {
                    val c = body[i]
                    if (c == '\\' && i + 1 < body.length) {
                        append(
                            when (val n = body[i + 1]) {
                                'n' -> '\n'
                                't' -> '\t'
                                else -> n
                            },
                        )
                        i += 2
                    } else {
                        append(c)
                        i++
                    }
                }
            }
        }
    }
}
