package com.blacklaunch.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.ui.theme.Oled
import kotlin.math.sin

/*
 * Тема «Аркуш» (етап 12d): усе ніби написано й намальовано від руки білим олівцем на чорному аркуші —
 * шрифт від руки (Caveat, вільна ліцензія OFL, вбудований — без мережі), рамки олівцем із легким тремтінням,
 * вимкнене — перекреслене, замість значків — лише назви. Вмикається в Оформленні.
 */

/** Чи ввімкнено «Аркуш» тут (компоненти малюють себе інакше). */
val LocalSheet = staticCompositionLocalOf { false }

val HandFont = FontFamily(
    Font(R.font.caveat_regular, FontWeight.Normal),
    Font(R.font.caveat_semibold, FontWeight.Medium),
    Font(R.font.caveat_semibold, FontWeight.SemiBold),
    Font(R.font.caveat_semibold, FontWeight.Bold),
)

/** Шрифт від руки дрібніший на око — трохи збільшуємо, щоб читалось, як звичайний. */
private const val HAND_SCALE = 1.22f

/**
 * Обгортка теми: з «Аркушем» — шрифт від руки для всього тексту всередині й трохи більший кегль;
 * без нього — нічого не змінює.
 */
@Composable
fun SheetTheme(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        CompositionLocalProvider(LocalSheet provides false, content = content)
        return
    }
    // Уже всередині «Аркуша» (налаштування поверх головного екрана) — вдруге не збільшуємо шрифт.
    if (LocalSheet.current) {
        content()
        return
    }
    val density = LocalDensity.current
    val hand = TextStyle(fontFamily = HandFont)
    val base = MaterialTheme.typography
    val typography = Typography(
        displayLarge = base.displayLarge.merge(hand), displayMedium = base.displayMedium.merge(hand), displaySmall = base.displaySmall.merge(hand),
        headlineLarge = base.headlineLarge.merge(hand), headlineMedium = base.headlineMedium.merge(hand), headlineSmall = base.headlineSmall.merge(hand),
        titleLarge = base.titleLarge.merge(hand), titleMedium = base.titleMedium.merge(hand), titleSmall = base.titleSmall.merge(hand),
        bodyLarge = base.bodyLarge.merge(hand), bodyMedium = base.bodyMedium.merge(hand), bodySmall = base.bodySmall.merge(hand),
        labelLarge = base.labelLarge.merge(hand), labelMedium = base.labelMedium.merge(hand), labelSmall = base.labelSmall.merge(hand),
    )
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = typography) {
        CompositionLocalProvider(
            LocalSheet provides true,
            LocalTextStyle provides LocalTextStyle.current.merge(hand),
            LocalDensity provides Density(density.density, density.fontScale * HAND_SCALE),
            content = content,
        )
    }
}

internal val SheetInk = Color(0xFFEDEDED)

/**
 * Рамка «від руки»: заокруглений прямокутник двома штрихами олівцем, сторони ледь хвилясті (кожна рамка — трохи
 * інакша, за [seed]), щоб не виглядало як лінійка.
 */
fun Modifier.pencilFrame(radius: Dp = 14.dp, seed: Int = 0, alpha: Float = 0.75f, width: Dp = 1.4.dp, ink: Color = SheetInk): Modifier = drawBehind {
    drawPencilFrame(radius.toPx(), seed, alpha, width.toPx(), ink)
}

/**
 * Рамка плашки: звичайно — скло (темне тло [fill] і тонкий відблиск), в «Аркуші» — рамка олівцем без тла
 * кольором [ink] (наприклад, вишневим, поки слухає мікрофон).
 */
@Composable
internal fun Modifier.glassBox(radius: Dp, seed: Int = 0, fill: Brush = Oled.Surface, ink: Color = SheetInk): Modifier {
    val shape = RoundedCornerShape(radius)
    return if (LocalSheet.current) pencilFrame(radius, seed, ink = ink).clip(shape) else clip(shape).background(fill).border(1.dp, Oled.Glint, shape)
}

/** Значок на початку рядка (і відступ після нього). В «Аркуші» не малюється — лише назва. */
@Composable
internal fun LeadingIcon(size: Dp = 40.dp, content: @Composable BoxScope.() -> Unit) {
    if (LocalSheet.current) return
    Box(Modifier.size(size), contentAlignment = Alignment.Center, content = content)
    Spacer(Modifier.width(16.dp))
}

internal fun DrawScope.drawPencilFrame(r: Float, seed: Int, alpha: Float, w: Float, ink: Color = SheetInk) {
    val wob = 1.2.dp.toPx()
    fun j(i: Int) = sin(seed * 1.7f + i * 2.3f) * wob
    val path = Path().apply {
        val right = size.width
        val bottom = size.height
        moveTo(r + j(1), j(2))
        quadraticTo(right / 2, j(3) * 1.5f, right - r + j(4), j(5))
        quadraticTo(right + j(6), j(7), right + j(8), r + j(9))
        quadraticTo(right + j(10) * 1.5f, bottom / 2, right + j(11), bottom - r + j(12))
        quadraticTo(right + j(13), bottom + j(14), right - r + j(15), bottom + j(16))
        quadraticTo(right / 2, bottom + j(17) * 1.5f, r + j(18), bottom + j(19))
        quadraticTo(j(20), bottom + j(21), j(22), bottom - r + j(23))
        quadraticTo(j(24) * 1.5f, bottom / 2, j(25), r + j(26))
        quadraticTo(j(27), j(28), r + j(29) + 2f, j(30))
    }
    drawPath(path, ink.copy(alpha = alpha), style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // Другий, ледь зсунутий тьмяний штрих — як від олівця.
    translateBy(Offset(w * 0.6f, w * 0.4f)) {
        drawPath(path, ink.copy(alpha = alpha * 0.3f), style = Stroke(w * 0.6f, cap = StrokeCap.Round))
    }
}

private inline fun DrawScope.translateBy(o: Offset, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(o.x, o.y)
    block()
    drawContext.transform.translate(-o.x, -o.y)
}

/** Хвиляста риска олівцем (перекреслення вимкненого, підкреслення вибраного). */
internal fun DrawScope.pencilStroke(y: Float, from: Float, to: Float, seed: Int, alpha: Float = 0.8f) {
    val wob = 1.1.dp.toPx()
    val path = Path().apply {
        moveTo(from, y + sin(seed * 1.3f) * wob)
        quadraticTo((from + to) / 2, y + sin(seed * 2.1f + 1f) * wob * 2f, to, y + sin(seed * 0.7f + 2f) * wob)
    }
    drawPath(path, SheetInk.copy(alpha = alpha), style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))
}
