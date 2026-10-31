package com.blacklaunch.qrcodes.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
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
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.ui.theme.Oled
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * Два стилі, як у Black Glass:
 * - «Аркуш» — усе ніби написано й намальовано від руки білим олівцем на чорному аркуші: шрифт від руки (Caveat, OFL,
 *   вбудований), рамки олівцем із легким тремтінням, значки — штрихами олівця;
 * - «Скло» — чорне скло: темні плашки з м'яким градієнтом і тонким відблиском по краю, звичайний шрифт, рівні значки.
 * Стиль перемикає все повністю: кожна рамка, значок, шрифт і підкреслення малюють себе за [AppStyle].
 */

/** Поточний стиль програми. Змінюється в Налаштуваннях; усе, що малюється, перемальовується саме. */
object AppStyle {
    var glass by mutableStateOf(false)
}

/** Чи зараз «Аркуш» (для тексту — шрифт від руки). */
val LocalSheet = staticCompositionLocalOf { true }

val HandFont = FontFamily(
    Font(R.font.caveat_regular, FontWeight.Normal),
    Font(R.font.caveat_semibold, FontWeight.Medium),
    Font(R.font.caveat_semibold, FontWeight.SemiBold),
    Font(R.font.caveat_semibold, FontWeight.Bold),
)

/** Шрифт від руки дрібніший на око — трохи збільшуємо, щоб читалось, як звичайний. */
private const val HAND_SCALE = 1.22f

/** «Аркуш»: шрифт від руки для всього тексту й трохи більший кегль. «Скло»: звичайний шрифт. */
@Composable
fun SheetTheme(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        CompositionLocalProvider(LocalSheet provides false, content = content)
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
 * Рамка плашки. «Аркуш»: заокруглений прямокутник двома штрихами олівцем, сторони ледь хвилясті (кожна рамка —
 * трохи інакша, за [seed]). «Скло»: темне скло з градієнтом і тонким відблиском; кольорова рамка ([ink] — синій
 * «вибрано», червоний «видалити») — тонкою кольоровою гранню. [alpha] — наскільки помітно (неактивне — блідіше).
 */
fun Modifier.pencilFrame(radius: Dp = 14.dp, seed: Int = 0, alpha: Float = 0.8f, width: Dp = 2.2.dp, ink: Color = SheetInk): Modifier =
    drawWithCache {
        val r = radius.toPx().coerceAtMost(size.minDimension / 2)
        if (AppStyle.glass) {
            val strength = (alpha / 0.8f).coerceIn(0.3f, 1f)
            val edge: Brush = if (ink == SheetInk) Oled.Glint else SolidColor(ink.copy(alpha = 0.7f * strength))
            val stroke = Stroke(1.dp.toPx())
            onDrawBehind {
                drawRoundRect(Oled.Surface, cornerRadius = CornerRadius(r), alpha = strength)
                drawRoundRect(edge, cornerRadius = CornerRadius(r), style = stroke, alpha = strength)
            }
        } else {
            // Контур рахуємо раз на розмір, а не щокадру.
            val w = width.toPx()
            val main = taperedPath(pencilLoop(size, r, seed, 1.3.dp.toPx()), w)
            // Другий, тьмяніший і трохи інакший штрих — як коли олівцем обводять двічі.
            val ghost = taperedPath(pencilLoop(size, r, seed + 101, 1.6.dp.toPx()), w * 0.55f)
            onDrawBehind {
                drawPath(main, ink.copy(alpha = alpha))
                drawPath(ghost, ink.copy(alpha = alpha * 0.35f))
            }
        }
    }

/**
 * Контур без тла (рамки на фото, вибраний елемент наклейки, видошукач): олівцем або рівною скляною лінією.
 * [topLeft], [size] — у px.
 */
internal fun DrawScope.outline(topLeft: Offset, size: Size, radius: Float, seed: Int, color: Color, width: Float) {
    if (AppStyle.glass) {
        drawRoundRect(color, topLeft, size, CornerRadius(radius), style = Stroke(width * 0.6f))
    } else {
        val points = pencilLoop(size, radius, seed, width * 0.4f).map { it + topLeft }
        drawPath(taperedPath(points, width), color)
    }
}

/**
 * Контур «від руки» навколо заокругленого прямокутника (коло — коли радіус ≥ половини сторони): одна плавна лінія
 * з повільним тремтінням, що починається в іншому місці для кожного [seed] і трохи заходить за початок — як коли
 * малюєш рамку олівцем одним рухом.
 */
internal fun pencilLoop(size: Size, radius: Float, seed: Int, wobble: Float): List<Offset> {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return emptyList()
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
    return List(steps + 1) { i ->
        val t = i / steps.toFloat()
        val d = start + total * t
        val base = point(d)
        // Тремтіння — від центру назовні/всередину. Лінія починається трохи всередині, а закінчується, заходячи
        // за початок назовні, — видно, де олівець замкнув рамку.
        val head = ((0.1f - t) / 0.1f).coerceAtLeast(0f)
        val tail = ((t - 0.9f) / 0.1f).coerceAtLeast(0f)
        val drift = wob * (0.65f * sin(d * f1 + p1) + 0.35f * sin(d * f2 + p2)) + wob * 1.6f * tail * tail - wob * 1.2f * head * head
        val dir = (base - centre).let { v -> val len = v.getDistance().coerceAtLeast(1f); Offset(v.x / len, v.y / len) }
        base + dir * drift
    }
}

/**
 * Лінія олівця вздовж [points] завтовшки [width] — посередині повна, до кінців тоншає й загострюється
 * (як справжній штрих: олівець торкається паперу й відривається від нього).
 */
internal fun taperedPath(points: List<Offset>, width: Float): Path {
    val path = Path()
    val n = points.size
    if (n < 2) return path
    val left = ArrayList<Offset>(n)
    val right = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val a = points[maxOf(0, i - 1)]
        val b = points[minOf(n - 1, i + 1)]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = kotlin.math.hypot(dx, dy).coerceAtLeast(0.001f)
        val t = i / (n - 1f)
        val taper = minOf(1f, t / 0.1f, (1f - t) / 0.14f).coerceAtLeast(0.04f).pow(0.8f)
        val half = width * taper / 2f
        val normal = Offset(-dy / len * half, dx / len * half)
        left += points[i] + normal
        right += points[i] - normal
    }
    path.moveTo(left[0].x, left[0].y)
    for (i in 1 until n) path.lineTo(left[i].x, left[i].y)
    for (i in n - 1 downTo 0) path.lineTo(right[i].x, right[i].y)
    path.close()
    return path
}

/** Точки квадратичної кривої (для штрихів олівцем). */
internal fun quadPoints(from: Offset, control: Offset, to: Offset, steps: Int = 16): List<Offset> = List(steps + 1) { i ->
    val t = i / steps.toFloat()
    val u = 1 - t
    Offset(u * u * from.x + 2 * u * t * control.x + t * t * to.x, u * u * from.y + 2 * u * t * control.y + t * t * to.y)
}

/** Хвиляста риска олівцем (перекреслення вимкненого, підкреслення вибраного). */
internal fun DrawScope.pencilStroke(y: Float, from: Float, to: Float, seed: Int, alpha: Float = 0.8f, ink: Color = SheetInk) {
    if (AppStyle.glass) {
        // «Скло»: рівна тонка риска.
        drawLine(ink.copy(alpha = alpha), Offset(from + 4.dp.toPx(), y), Offset(to - 4.dp.toPx(), y), 2.dp.toPx(), cap = StrokeCap.Round)
        return
    }
    val wob = 1.1.dp.toPx()
    val points = quadPoints(
        Offset(from, y + sin(seed * 1.3f) * wob),
        Offset((from + to) / 2, y + sin(seed * 2.1f + 1f) * wob * 2f),
        Offset(to, y + sin(seed * 0.7f + 2f) * wob),
        steps = 24,
    )
    drawPath(taperedPath(points, 2.2.dp.toPx()), ink.copy(alpha = alpha))
}

/** Короткий штрих олівцем від [a] до [b]: ледь вигнутий, загострений на кінцях. */
internal fun DrawScope.pencilTick(a: Offset, b: Offset, width: Float, color: Color, seed: Int) {
    val bend = sin(seed * 2.3f) * width * 0.6f
    val dir = b - a
    val len = dir.getDistance().coerceAtLeast(0.001f)
    val normal = Offset(-dir.y / len, dir.x / len)
    drawPath(taperedPath(quadPoints(a, (a + b) / 2f + normal * bend, b, steps = 10), width), color)
}

