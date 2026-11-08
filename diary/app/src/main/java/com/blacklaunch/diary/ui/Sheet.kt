package com.blacklaunch.diary.ui

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
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.blacklaunch.diary.R
import com.blacklaunch.diary.ui.theme.Oled
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

/** Шрифт «Скла» — Exo 2 (OFL, вбудований): чіткий, трохи технічний, з повною кирилицею. */
val GlassFont = FontFamily(
    Font(R.font.exo2_extralight, FontWeight.ExtraLight),
    Font(R.font.exo2_extralight, FontWeight.Thin),
    Font(R.font.exo2_light, FontWeight.Light),
    Font(R.font.exo2_regular, FontWeight.Normal),
    Font(R.font.exo2_medium, FontWeight.Medium),
    Font(R.font.exo2_semibold, FontWeight.SemiBold),
    Font(R.font.exo2_bold, FontWeight.Bold),
    Font(R.font.exo2_bold, FontWeight.ExtraBold),
    Font(R.font.exo2_bold, FontWeight.Black),
)

/** Шрифт від руки дрібніший на око — трохи збільшуємо, щоб читалось, як звичайний. */
private const val HAND_SCALE = 1.22f

/** «Аркуш»: шрифт від руки для всього тексту й трохи більший кегль. «Скло»: звичайний шрифт. */
@Composable
fun SheetTheme(enabled: Boolean, content: @Composable () -> Unit) {
    // Одне місце виклику [content] для обох стилів: зміна стилю лише перемальовує, а не будує екран заново —
    // відкриті вікна (Налаштування) й стан програми лишаються, де були.
    val density = LocalDensity.current
    val font = TextStyle(fontFamily = if (enabled) HandFont else GlassFont)
    val base = MaterialTheme.typography
    val typography = if (enabled) {
        Typography(
            displayLarge = base.displayLarge.merge(font), displayMedium = base.displayMedium.merge(font), displaySmall = base.displaySmall.merge(font),
            headlineLarge = base.headlineLarge.merge(font), headlineMedium = base.headlineMedium.merge(font), headlineSmall = base.headlineSmall.merge(font),
            titleLarge = base.titleLarge.merge(font), titleMedium = base.titleMedium.merge(font), titleSmall = base.titleSmall.merge(font),
            bodyLarge = base.bodyLarge.merge(font), bodyMedium = base.bodyMedium.merge(font), bodySmall = base.bodySmall.merge(font),
            labelLarge = base.labelLarge.merge(font), labelMedium = base.labelMedium.merge(font), labelSmall = base.labelSmall.merge(font),
        )
    } else {
        base.withFont(font)
    }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = typography) {
        CompositionLocalProvider(
            LocalSheet provides enabled,
            LocalTextStyle provides LocalTextStyle.current.merge(font),
            LocalDensity provides if (enabled) Density(density.density, density.fontScale * HAND_SCALE) else density,
            content = content,
        )
    }
}

internal val SheetInk = Color(0xFFEDEDED)

/**
 * «Вибране» для [pencilFrame]: у «Склі» — яскравий кант, в «Аркуші» — яскравіша й товща лінія олівця.
 * (Вибраний варіант позначається кантом і галочкою, а не кольором — THEMES.md, розділ 2.)
 */
internal val SelectedInk = Color(0xFFFFFFFF)

/** Усі стилі тексту теми — тим самим шрифтом. */
private fun Typography.withFont(font: TextStyle) = Typography(
    displayLarge = displayLarge.merge(font), displayMedium = displayMedium.merge(font), displaySmall = displaySmall.merge(font),
    headlineLarge = headlineLarge.merge(font), headlineMedium = headlineMedium.merge(font), headlineSmall = headlineSmall.merge(font),
    titleLarge = titleLarge.merge(font), titleMedium = titleMedium.merge(font), titleSmall = titleSmall.merge(font),
    bodyLarge = bodyLarge.merge(font), bodyMedium = bodyMedium.merge(font), bodySmall = bodySmall.merge(font),
    labelLarge = labelLarge.merge(font), labelMedium = labelMedium.merge(font), labelSmall = labelSmall.merge(font),
)

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
            val selected = ink == SelectedInk
            val edge: Brush = when {
                selected -> Oled.GlintBright
                ink == SheetInk -> Oled.Glint
                else -> SolidColor(ink.copy(alpha = 0.55f * strength))
            }
            val stroke = Stroke(1.dp.toPx())
            // Відблиск: м'яке світло вгорі всередині, до половини висоти (не більше 120 dp).
            val sheenH = (size.height * 0.5f).coerceAtMost(120.dp.toPx())
            val sheen = Brush.verticalGradient(0f to Color(0x14FFFFFF), 1f to Color.Transparent, startY = 0f, endY = sheenH)
            onDrawBehind {
                drawRoundRect(Oled.Surface, cornerRadius = CornerRadius(r), alpha = strength)
                // На всю форму: нижче sheenH градієнт уже прозорий. Окремий низький прямокутник із тим самим радіусом
                // виходив за межі круглих кнопок («привид» над ними).
                drawRoundRect(sheen, cornerRadius = CornerRadius(r), alpha = strength)
                drawRoundRect(edge, cornerRadius = CornerRadius(r), style = stroke, alpha = if (selected) 1f else strength)
            }
        } else {
            // Контур рахуємо раз на розмір, а не щокадру. Вибране — яскравіша й товща лінія.
            val bright = ink == SelectedInk
            val inkColor = if (bright) SheetInk else ink
            val w = width.toPx() * if (bright) 1.25f else 1f
            val main = taperedPath(pencilLoop(size, r, seed, 1.3.dp.toPx()), w)
            // Другий, тьмяніший і трохи інакший штрих — як коли олівцем обводять двічі.
            val ghost = taperedPath(pencilLoop(size, r, seed + 101, 1.6.dp.toPx()), w * 0.55f)
            val a = if (bright) 1f else alpha
            onDrawBehind {
                drawPath(main, inkColor.copy(alpha = a))
                drawPath(ghost, inkColor.copy(alpha = a * 0.35f))
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
internal fun DrawScope.pencilStroke(y: Float, from: Float, to: Float, seed: Int, alpha: Float = 0.8f, ink: Color = SheetInk, progress: Float = 1f) {
    // [progress] < 1 — лінія ще «малюється»: від початку до цієї частки довжини, як рукою зліва направо.
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0.01f) return
    if (AppStyle.glass) {
        // «Скло»: рівна тонка риска.
        val a = from + 4.dp.toPx()
        val b = to - 4.dp.toPx()
        drawLine(ink.copy(alpha = alpha), Offset(a, y), Offset(a + (b - a) * p, y), 2.dp.toPx(), cap = StrokeCap.Round)
        return
    }
    val wob = 1.1.dp.toPx()
    val points = quadPoints(
        Offset(from, y + sin(seed * 1.3f) * wob),
        Offset((from + to) / 2, y + sin(seed * 2.1f + 1f) * wob * 2f),
        Offset(to, y + sin(seed * 0.7f + 2f) * wob),
        steps = 48,
    )
    // Лінія завжди повна за формою (загострена на обох кінцях), а видно лише її частину від початку до [progress]:
    // так вона «пишеться» зліва направо. (Якщо різати саму лінію, загострені кінці ховаються — здається, що вона
    // росте з обох боків.)
    val path = taperedPath(points, 2.2.dp.toPx())
    if (p >= 1f) {
        drawPath(path, ink.copy(alpha = alpha))
    } else {
        clipRect(left = from - 4.dp.toPx(), top = 0f, right = from + (to - from) * p, bottom = size.height + 8.dp.toPx()) {
            drawPath(path, ink.copy(alpha = alpha))
        }
    }
}

/** Короткий штрих олівцем від [a] до [b]: ледь вигнутий, загострений на кінцях. */
internal fun DrawScope.pencilTick(a: Offset, b: Offset, width: Float, color: Color, seed: Int) {
    val bend = sin(seed * 2.3f) * width * 0.6f
    val dir = b - a
    val len = dir.getDistance().coerceAtLeast(0.001f)
    val normal = Offset(-dir.y / len, dir.x / len)
    drawPath(taperedPath(quadPoints(a, (a + b) / 2f + normal * bend, b, steps = 10), width), color)
}

