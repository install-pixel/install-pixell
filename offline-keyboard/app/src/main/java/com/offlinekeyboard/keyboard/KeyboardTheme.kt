package com.offlinekeyboard.keyboard


/**
 * The two styles shared by all the user's apps (THEMES.md): black glass lit from above, and white pencil on
 * black paper. Since 1.2 they are the only themes; the old system/light/dark/OLED settings open as GLASS.
 */
enum class ThemeMode { GLASS, SHEET }

/** How surfaces (keys, buttons, popups) are drawn: flat colour, black glass, or pencil on paper. */
enum class SurfaceStyle { FLAT, GLASS, SHEET }

/** Key vibration: light tick, normal click, or a firm click (for thick cases and gloves). */
enum class VibrationStrength { LIGHT, MEDIUM, STRONG }

/** All colours of the keyboard window; the views never read colour resources directly. */
data class KeyboardTheme(
    val dark: Boolean,
    val background: Int,
    val key: Int,
    val function: Int,
    val pressed: Int,
    val accent: Int,
    val text: Int,
    val onAccent: Int,
    val hint: Int,
    val popup: Int,
    val style: SurfaceStyle = SurfaceStyle.FLAT,
    /** The main finishing action (Enter) in the glass and sheet styles; 0 elsewhere (the accent is used). */
    val confirm: Int = 0,
    /** Recording (the voice chip) in the glass and sheet styles. */
    val listening: Int = 0,
    /** Built-in font for every label (Exo 2 for glass, Caveat for sheet), or 0 for the system font. */
    val fontRes: Int = 0,
    /** Handwriting reads smaller: its labels are drawn this much larger. */
    val textScale: Float = 1f,
) {
    /** Glass and sheet mark active keys with a brighter rim, not a colour (THEMES.md, section 2). */
    val styled: Boolean get() = style != SurfaceStyle.FLAT

    companion object {
        fun resolve(mode: ThemeMode): KeyboardTheme = when (mode) {
            ThemeMode.GLASS -> glass()
            ThemeMode.SHEET -> sheet()
        }

        /**
         * «Скло · Темна»: true black, keys of black glass lit from above with a bright top rim, Exo 2. No
         * colour accent: the grey [ACCENT] marks the cursor; Enter is written in [CONFIRM] blue.
         */
        fun glass() = KeyboardTheme(
            dark = true,
            background = BLACK,
            key = 0xFF0D0D0D.toInt(),
            function = 0xFF0D0D0D.toInt(),
            pressed = 0xFF2A2A2A.toInt(),
            accent = ACCENT,
            text = TEXT,
            onAccent = TEXT,
            hint = HINT,
            popup = 0xFF101010.toInt(),
            style = SurfaceStyle.GLASS,
            confirm = CONFIRM,
            listening = LISTENING,
            fontRes = com.offlinekeyboard.R.font.exo2_regular,
        )

        /** «Аркуш · Темна»: white pencil on black paper; every key a hand-drawn double-stroked frame, Caveat. */
        fun sheet() = KeyboardTheme(
            dark = true,
            background = BLACK,
            key = BLACK,
            function = BLACK,
            pressed = 0xFF1C1C1C.toInt(),
            accent = ACCENT,
            text = INK,
            onAccent = INK,
            hint = HINT,
            popup = BLACK,
            style = SurfaceStyle.SHEET,
            confirm = CONFIRM,
            listening = LISTENING,
            fontRes = com.offlinekeyboard.R.font.caveat_semibold,
            textScale = 1.22f,
        )

        // Shared tokens of THEMES.md, section 2.
        private const val BLACK = 0xFF000000.toInt()
        private const val TEXT = 0xFFE6E6E6.toInt()
        private const val INK = 0xFFEDEDED.toInt()
        private const val HINT = 0xFF8C8C8C.toInt()
        private const val ACCENT = 0xFFBDBDBD.toInt()
        const val CONFIRM = 0xFF8AB4F8.toInt()
        const val DANGER = 0xFFEF8A80.toInt()
        private const val LISTENING = 0xFFB0244F.toInt()
    }
}
