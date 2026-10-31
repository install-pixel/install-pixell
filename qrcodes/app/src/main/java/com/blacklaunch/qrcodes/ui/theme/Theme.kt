package com.blacklaunch.qrcodes.ui.theme

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
    /** Мікрофон слухає — вишневий, щоб не плутався з червоним «видалити». */
    val Listening = Color(0xFFB0244F)

    /** Стримані акценти (лише там, де важливо): що підтверджує/зберігає і що видаляє. Решта — сіра. */
    val Confirm = Color(0xFF8AB4F8)
    val Danger = Color(0xFFEF8A80)

    /** Поверхня: світліша вгорі, темніша внизу, але помітно світліша за чорне тло. */
    val Surface = Brush.verticalGradient(listOf(Color(0xFF242424), Color(0xFF161616)))

    /** Грань-відблиск: яскрава зверху, тьмяніша з боків, контур знизу лишається видимим. */
    val Glint = Brush.verticalGradient(listOf(Color(0x4DFFFFFF), Color(0x1AFFFFFF), Color(0x2EFFFFFF)))

    /** Об'ємна кругла кнопка. */
    val Knob = Brush.radialGradient(listOf(Color(0xFF3C3C3C), Color(0xFF161616)))
    val KnobListening = Brush.radialGradient(listOf(Color(0xFFC93A66), Color(0xFF4F0F24)))

    /** «Прибрати»/«видалити» під час перетягування — червоний, як усі дії, що видаляють. */
    val KnobDanger = Brush.radialGradient(listOf(Color(0xFFE5655A), Color(0xFF5E1D18)))
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
fun QrTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OledColors, content = content)
}
