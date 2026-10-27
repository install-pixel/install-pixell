package com.blacklaunch.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.ui.theme.Oled
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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
fun Modifier.pencilFrame(radius: Dp = 14.dp, seed: Int = 0, alpha: Float = 0.8f, width: Dp = 2.2.dp, ink: Color = SheetInk): Modifier =
    drawWithCache {
        // Контур рахуємо раз на розмір, а не щокадру.
        val main = pencilLoop(size, radius.toPx(), seed, 1.3.dp.toPx())
        val ghost = pencilLoop(size, radius.toPx(), seed + 101, 1.6.dp.toPx())
        val w = width.toPx()
        onDrawBehind {
            drawPath(main, ink.copy(alpha = alpha), style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Другий, тьмяніший і трохи інакший штрих — як коли олівцем обводять двічі.
            drawPath(ghost, ink.copy(alpha = alpha * 0.35f), style = Stroke(w * 0.55f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
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

/**
 * Контур «від руки» навколо заокругленого прямокутника (коло — коли радіус ≥ половини сторони): одна плавна лінія
 * з повільним тремтінням, що починається в іншому місці для кожного [seed] і трохи заходить за початок — як коли
 * малюєш рамку олівцем одним рухом.
 */
internal fun pencilLoop(size: Size, radius: Float, seed: Int, wobble: Float): Path {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return Path()
    val r = radius.coerceAtMost(minOf(w, h) / 2f).coerceAtLeast(0.5f)
    val straightW = w - 2 * r
    val straightH = h - 2 * r
    val arc = (Math.PI / 2 * r).toFloat()
    val perimeter = 2 * straightW + 2 * straightH + 4 * arc
    // Точка на контурі за відстанню від верхнього лівого кінця верхньої сторони (за годинниковою стрілкою).
    fun point(d0: Float): Offset {
        var d = ((d0 % perimeter) + perimeter) % perimeter
        fun corner(cx: Float, cy: Float, from: Double): Offset {
            val a = from + d / r
            return Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
        if (d < straightW) return Offset(r + d, 0f)
        d -= straightW
        if (d < arc) return corner(w - r, r, -Math.PI / 2)
        d -= arc
        if (d < straightH) return Offset(w, r + d)
        d -= straightH
        if (d < arc) return corner(w - r, h - r, 0.0)
        d -= arc
        if (d < straightW) return Offset(w - r - d, h)
        d -= straightW
        if (d < arc) return corner(r, h - r, Math.PI / 2)
        d -= arc
        if (d < straightH) return Offset(0f, h - r - d)
        d -= straightH
        return corner(r, r, Math.PI)
    }
    val random = Random(seed)
    val start = random.nextFloat() * perimeter
    val overlap = perimeter * (0.05f + random.nextFloat() * 0.04f)
    val p1 = random.nextFloat() * 6.28f
    val p2 = random.nextFloat() * 6.28f
    // Повільні хвилі (2–3 на весь контур); на малих фігурах — слабші, щоб коло не ставало кутастим.
    val f1 = 2 * 6.2832f / perimeter
    val f2 = 3 * 6.2832f / perimeter
    val wob = wobble * (0.45f + perimeter / 2500f).coerceAtMost(1f)
    val total = perimeter + overlap
    val steps = (total / 4f).toInt().coerceIn(48, 600)
    val centre = Offset(w / 2, h / 2)
    return Path().apply {
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val d = start + total * t
            val base = point(d)
            // Тремтіння — від центру назовні/всередину. Лінія починається трохи всередині, а закінчується, заходячи
            // за початок назовні, — видно, де олівець замкнув рамку.
            val head = ((0.1f - t) / 0.1f).coerceAtLeast(0f)
            val tail = ((t - 0.9f) / 0.1f).coerceAtLeast(0f)
            val drift = wob * (0.65f * sin(d * f1 + p1) + 0.35f * sin(d * f2 + p2)) + wob * 1.6f * tail * tail - wob * 1.2f * head * head
            val dir = (base - centre).let { v -> val len = v.getDistance().coerceAtLeast(1f); Offset(v.x / len, v.y / len) }
            val pt = base + dir * drift
            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
        }
    }
}

/** Хвиляста риска олівцем (перекреслення вимкненого, підкреслення вибраного). */
internal fun DrawScope.pencilStroke(y: Float, from: Float, to: Float, seed: Int, alpha: Float = 0.8f, ink: Color = SheetInk) {
    val wob = 1.1.dp.toPx()
    val path = Path().apply {
        moveTo(from, y + sin(seed * 1.3f) * wob)
        quadraticTo((from + to) / 2, y + sin(seed * 2.1f + 1f) * wob * 2f, to, y + sin(seed * 0.7f + 2f) * wob)
    }
    drawPath(path, ink.copy(alpha = alpha), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * Підказка «Аркуша» внизу екрана — замість системної спливаючої: на чорному клаптику в рамці олівцем, шрифтом від руки.
 * Сама зникає (час — у того, хто показує).
 */
@Composable
fun HintBanner(text: String?) {
    // Останній текст лишається, поки плашка тане.
    var shown by remember { mutableStateOf("") }
    if (text != null) shown = text
    Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                shown,
                color = Oled.TextPrimary,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, bottom = 96.dp)
                    .background(Oled.Black, RoundedCornerShape(18.dp))
                    .pencilFrame(18.dp, seed = shown.length)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}
