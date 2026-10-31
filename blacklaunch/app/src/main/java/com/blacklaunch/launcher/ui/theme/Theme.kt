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
    /** Мікрофон слухає — вишневий, щоб не плутався з червоним «видалити». */
    val Listening = Color(0xFFB0244F)

    /** Стримані акценти (лише там, де важливо): що підтверджує/зберігає і що видаляє. Решта — сіра. */
    val Confirm = Color(0xFF8AB4F8)
    val Danger = Color(0xFFEF8A80)

    /**
     * Чорне скло: темне, ледь світліше вгорі (світло падає згори) і майже чорне внизу — глибина, а не сіра плашка.
     */
    val Surface = Brush.verticalGradient(0f to Color(0xFF1B1B1B), 0.45f to Color(0xFF0D0D0D), 1f to Color(0xFF080808))

    /** Кант скла: яскравий відблиск по верхній грані, по боках тьмяніє, знизу — ледь помітний контур. */
    val Glint = Brush.verticalGradient(0f to Color(0x80FFFFFF), 0.25f to Color(0x24FFFFFF), 0.8f to Color(0x12FFFFFF), 1f to Color(0x26FFFFFF))

    /** Кант вибраного — яскравіший. */
    val GlintBright = Brush.verticalGradient(0f to Color(0xB3FFFFFF), 0.3f to Color(0x40FFFFFF), 1f to Color(0x33FFFFFF))

    /** Вибране / кругла кнопка: скло зі світлом посередині — видно серед інших. */
    val Knob = Brush.radialGradient(listOf(Color(0xFF3A3A3A), Color(0xFF101010)))
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
fun BlackLaunchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OledColors, content = content)
}
