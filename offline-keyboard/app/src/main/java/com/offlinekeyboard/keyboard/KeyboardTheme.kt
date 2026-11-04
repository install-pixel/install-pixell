package com.offlinekeyboard.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * GLASS and SHEET are the two dark styles shared by all the user's apps (THEMES.md): black glass lit from
 * above, and white pencil on black paper. GLASS was called OLED before 1.1 (stored settings still say so).
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK, GLASS, SHEET }

/** How surfaces (keys, buttons, popups) are drawn: flat colour, black glass, or pencil on paper. */
enum class SurfaceStyle { FLAT, GLASS, SHEET }

/** Key vibration: light tick, normal click, or a firm click (for thick cases and gloves). */
enum class VibrationStrength { LIGHT, MEDIUM, STRONG }

/** Soft pastel accents. Text on an accent is always dark, so every accent stays readable. */
enum class Accent(val color: Int) {
    SKY(0xFF8DB4E2.toInt()),
    MINT(0xFF8FD1B9.toInt()),
    LAVENDER(0xFFB3A6DF.toInt()),
    PEACH(0xFFF0B49A.toInt()),
    ROSE(0xFFE6A6BA.toInt()),
    GREY(0xFFB4BAC4.toInt()),
}

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
        fun resolve(context: Context, mode: ThemeMode, accent: Accent): KeyboardTheme {
            if (mode == ThemeMode.GLASS) return glass()
            if (mode == ThemeMode.SHEET) return sheet()
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.GLASS, ThemeMode.SHEET -> true
                ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            }
            return create(dark, accent)
        }

        fun create(dark: Boolean, accent: Accent): KeyboardTheme {
            val a = accent.color
            return if (dark) {
                // Dark greys swallow pastel easily, so the accent is mixed in more strongly than in light.
                KeyboardTheme(
                    dark = true,
                    background = blend(0xFF1C1D21.toInt(), a, 0.16f),
                    key = blend(0xFF35373D.toInt(), a, 0.12f),
                    function = blend(0xFF2A2C31.toInt(), a, 0.32f),
                    pressed = blend(0xFF55585F.toInt(), a, 0.45f),
                    accent = a,
                    text = 0xFFEDEEF0.toInt(),
                    onAccent = 0xFF1E2128.toInt(),
                    hint = blend(0xFF9EA3AB.toInt(), a, 0.6f),
                    popup = blend(0xFF4A4D54.toInt(), a, 0.2f),
                )
            } else {
                KeyboardTheme(
                    dark = false,
                    background = blend(0xFFE8EAEE.toInt(), a, 0.12f),
                    key = 0xFFFFFFFF.toInt(),
                    function = blend(0xFFD3D7DE.toInt(), a, 0.22f),
                    pressed = blend(0xFFBEC4CC.toInt(), a, 0.30f),
                    accent = a,
                    text = 0xFF1E2128.toInt(),
                    onAccent = 0xFF1E2128.toInt(),
                    hint = 0xFF6E7480.toInt(),
                    popup = 0xFFF7F8FA.toInt(),
                )
            }
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

        private fun blend(base: Int, tint: Int, amount: Float): Int {
            fun mix(x: Int, y: Int) = (x + (y - x) * amount).toInt().coerceIn(0, 255)
            return Color.rgb(
                mix(Color.red(base), Color.red(tint)),
                mix(Color.green(base), Color.green(tint)),
                mix(Color.blue(base), Color.blue(tint)),
            )
        }
    }
}
