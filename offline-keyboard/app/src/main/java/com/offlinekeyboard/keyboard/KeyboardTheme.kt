package com.offlinekeyboard.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** OLED: true black with grey surfaces in soft gradients and a light rim, as in the BlackLaunch launcher. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, OLED }

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
    /** Surfaces drawn lighter at the top with a thin light rim ([Gloss]) instead of flat. */
    val glossy: Boolean = false,
) {
    companion object {
        fun resolve(context: Context, mode: ThemeMode, accent: Accent): KeyboardTheme {
            if (mode == ThemeMode.OLED) return oled(accent)
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.OLED -> true
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
         * Black background (OLED pixels off) and neutral grey keys; the accent only marks Enter and
         * active keys, so it stays as calm as the launcher.
         */
        fun oled(accent: Accent) = KeyboardTheme(
            dark = true,
            background = 0xFF000000.toInt(),
            key = 0xFF1D1D1D.toInt(),
            function = 0xFF2B2B2B.toInt(),
            pressed = 0xFF4A4A4A.toInt(),
            accent = accent.color,
            text = 0xFFE6E6E6.toInt(),
            onAccent = 0xFF1E2128.toInt(),
            hint = 0xFF8C8C8C.toInt(),
            popup = 0xFF2E2E2E.toInt(),
            glossy = true,
        )

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
