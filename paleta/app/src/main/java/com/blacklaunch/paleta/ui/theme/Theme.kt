package com.blacklaunch.paleta.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Палітра — за THEMES.md (спільні правила тем): справжній чорний фон, сірі поверхні з м'якими градієнтами
 * та тонкою світлою гранню зверху — «відблиск» і відчуття об'єму.
 */
object Oled {
    val Black = Color(0xFF000000)
    val TextPrimary = Color(0xFFE6E6E6)
    val TextSecondary = Color(0xFF8C8C8C)
    val Accent = Color(0xFFBDBDBD)

    /** Стримані акценти (лише там, де важливо): що підтверджує/зберігає і що видаляє. Решта — сіра. */
    val Confirm = Color(0xFF8AB4F8)
    val Danger = Color(0xFFEF8A80)

    /**
     * Чорне скло (THEMES.md, розділ 3): темне, ледь світліше вгорі (світло падає згори) і майже чорне внизу.
     */
    val Surface = Brush.verticalGradient(0f to Color(0xFF1B1B1B), 0.45f to Color(0xFF0D0D0D), 1f to Color(0xFF080808))

    /** Кант скла: яскравий угорі, тьмяніє донизу, знизу — ледь помітний контур. */
    val Glint = Brush.verticalGradient(0f to Color(0x80FFFFFF), 0.25f to Color(0x24FFFFFF), 0.8f to Color(0x12FFFFFF), 1f to Color(0x26FFFFFF))

    /** Кант вибраного — яскравіший (вибране позначається кантом і галочкою, а не кольором). */
    val GlintBright = Brush.verticalGradient(0f to Color(0xB3FFFFFF), 0.3f to Color(0x40FFFFFF), 1f to Color(0x33FFFFFF))

    /** Кругла кнопка / вибране: скло зі світлом посередині. */
    val Knob = Brush.radialGradient(listOf(Color(0xFF3A3A3A), Color(0xFF101010)))
}

private val OledColors = darkColorScheme(
    primary = Oled.Accent,
    onPrimary = Oled.Black,
    background = Oled.Black,
    surface = Color(0xFF121212),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    onBackground = Oled.TextPrimary,
    onSurface = Oled.TextPrimary,
    onSurfaceVariant = Oled.TextSecondary,
    outline = Color(0xFF3A3A3A),
)

@Composable
fun PaletaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OledColors, content = content)
}
