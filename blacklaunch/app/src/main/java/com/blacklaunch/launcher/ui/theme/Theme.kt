package com.blacklaunch.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * OLED-палітра: справжній чорний фон, сірі поверхні з м'якими градієнтами
 * та тонкою світлою гранню зверху — «відблиск» і відчуття об'єму.
 */
object Oled {
    val Black = Color(0xFF000000)
    val TextPrimary = Color(0xFFE6E6E6)
    val TextSecondary = Color(0xFF8C8C8C)
    val Accent = Color(0xFFBDBDBD)
    val Listening = Color(0xFFE53935)

    /** Поверхня: світліша вгорі, темніша внизу. */
    val Surface = Brush.verticalGradient(listOf(Color(0xFF242424), Color(0xFF111111)))

    /** Грань-відблиск: яскравіша зверху, майже зникає знизу. */
    val Glint = Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x0AFFFFFF)))

    /** Об'ємна кругла кнопка. */
    val Knob = Brush.radialGradient(listOf(Color(0xFF3C3C3C), Color(0xFF161616)))
    val KnobListening = Brush.radialGradient(listOf(Color(0xFFEF5350), Color(0xFF6D1B1B)))
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
fun BlackLaunchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OledColors, content = content)
}
