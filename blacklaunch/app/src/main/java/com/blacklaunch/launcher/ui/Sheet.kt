package com.blacklaunch.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
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
        val w = width.toPx()
        val main = taperedPath(pencilLoop(size, radius.toPx(), seed, 1.3.dp.toPx()), w)
        // Другий, тьмяніший і трохи інакший штрих — як коли олівцем обводять двічі.
        val ghost = taperedPath(pencilLoop(size, radius.toPx(), seed + 101, 1.6.dp.toPx()), w * 0.55f)
        onDrawBehind {
            drawPath(main, ink.copy(alpha = alpha))
            drawPath(ghost, ink.copy(alpha = alpha * 0.35f))
        }
    }

/**
 * Рамка плашки: звичайно — скло (темне тло [fill] і тонкий відблиск), в «Аркуші» — рамка олівцем без тла
 * кольором [ink] (наприклад, вишневим, поки слухає мікрофон).
 */
@Composable
internal fun Modifier.glassBox(radius: Dp, seed: Int = 0, fill: Brush = Oled.Surface, ink: Color = SheetInk, paper: Color? = null): Modifier {
    val shape = RoundedCornerShape(radius)
    return if (LocalSheet.current) {
        // [paper] — чорне тло під рамкою, коли плашка лежить поверх чогось (фігур у грі).
        (if (paper != null) background(paper, shape) else this).pencilFrame(radius, seed, ink = ink).clip(shape)
    } else {
        clip(shape).background(fill).glassSheen().border(1.dp, Oled.Glint, shape)
    }
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
    val wob = 1.1.dp.toPx()
    val points = quadPoints(
        Offset(from, y + sin(seed * 1.3f) * wob),
        Offset((from + to) / 2, y + sin(seed * 2.1f + 1f) * wob * 2f),
        Offset(to, y + sin(seed * 0.7f + 2f) * wob),
        steps = 24,
    )
    drawPath(taperedPath(points, 2.2.dp.toPx()), ink.copy(alpha = alpha))
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

/** Короткий штрих олівцем від [a] до [b]: ледь вигнутий, загострений на кінцях. */
internal fun DrawScope.pencilTick(a: Offset, b: Offset, width: Float, color: Color, seed: Int) {
    val bend = sin(seed * 2.3f) * width * 0.6f
    val dir = b - a
    val len = dir.getDistance().coerceAtLeast(0.001f)
    val normal = Offset(-dir.y / len, dir.x / len)
    drawPath(taperedPath(quadPoints(a, (a + b) / 2f + normal * bend, b, steps = 10), width), color)
}

/**
 * «Аркуш»: батарейка, намальована олівцем, — як пальчикова: корпус, «носик», усередині рядок рисок (заповнені — скільки
 * заряду, решта ледь видно; мало заряду — червоні), під нею — відсотки.
 */
/** Батарейка огляду з відсотками під нею: в «Аркуші» — намальована олівцем, у склі — металева. */
@Composable
internal fun HandBattery(level: Int, modifier: Modifier = Modifier) {
    val low = level <= LOW_BATTERY
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BatteryGauge(level, Modifier.size(width = 64.dp, height = 28.dp))
        Text("$level%", color = if (low) Oled.Danger else Oled.TextSecondary, fontSize = 13.sp)
    }
}

/**
 * Батарейка з десятьма поділками (заповнені — скільки заряду), малюється на весь розмір [modifier] (пропорції ≈ 64×28).
 * «Аркуш» — контур і штрихи олівцем; скло — металева рамка з відблиском, усередині чорне скло і світні поділки.
 * [wave] від 0 до 1 — світло, що біжить поділками під час заряджання (−1 — без нього).
 */
@Composable
internal fun BatteryGauge(level: Int, modifier: Modifier, wave: Float = -1f) {
    val sheet = LocalSheet.current
    val low = level <= LOW_BATTERY
    Canvas(modifier) {
        val k = size.height / 28.dp.toPx()
        val nubW = 5.dp.toPx() * k
        val bodyW = size.width - nubW - 1.dp.toPx() * k
        val bodyH = size.height
        val filled = ((level + 5) / 10).coerceIn(if (level > 0) 1 else 0, BATTERY_TICKS)
        fun boost(i: Int) = if (wave >= 0f && i < filled) (1f - abs(i - wave * (filled + 3f)) / 3f).coerceIn(0f, 1f) else 0f
        if (sheet) {
            val ink = if (low) Oled.Danger else SheetInk
            drawPath(taperedPath(pencilLoop(Size(bodyW, bodyH), 6.dp.toPx() * k, 83, 0.6.dp.toPx() * k), 2.dp.toPx() * k.coerceAtMost(1.6f)), SheetInk.copy(alpha = 0.85f))
            val nubH = bodyH * 0.42f
            translate(bodyW + 1.dp.toPx() * k, (bodyH - nubH) / 2f) {
                drawPath(taperedPath(pencilLoop(Size(nubW, nubH), 2.dp.toPx() * k, 89, 0.3.dp.toPx() * k), 1.6.dp.toPx() * k.coerceAtMost(1.6f)), SheetInk.copy(alpha = 0.85f))
            }
            val pad = 5.dp.toPx() * k
            val step = (bodyW - 2 * pad) / BATTERY_TICKS
            repeat(BATTERY_TICKS) { i ->
                val x = pad + step * (i + 0.5f)
                val lean = step * 0.25f
                val on = i < filled
                pencilTick(
                    Offset(x - lean, bodyH - pad),
                    Offset(x + lean, pad),
                    (if (on) 2.4.dp.toPx() else 1.2.dp.toPx()) * k.coerceAtMost(1.8f),
                    if (on) lerp(ink, Color.White, 0.5f * boost(i)) else SheetInk.copy(alpha = 0.2f),
                    i + 7,
                )
            }
            return@Canvas
        }
        // Скло: металева рамка (світла вгорі, темніша посередині, з відбитим світлом унизу).
        val radius = CornerRadius(bodyH * 0.26f)
        drawRoundRect(metalBrush(bodyH), Offset.Zero, Size(bodyW, bodyH), radius)
        val nubH = bodyH * 0.42f
        drawRoundRect(
            metalBrush(bodyH, top = (bodyH - nubH) / 2f, height = nubH),
            Offset(bodyW + 0.6.dp.toPx() * k, (bodyH - nubH) / 2f),
            Size(nubW, nubH),
            CornerRadius(nubW * 0.45f),
        )
        // Усередині — чорне скло.
        val wall = bodyH * 0.13f
        val cavity = Size(bodyW - wall * 2, bodyH - wall * 2)
        val inner = CornerRadius(radius.x - wall * 0.7f)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF020202), Color(0xFF151515)), wall, bodyH - wall), Offset(wall, wall), cavity, inner)
        // Поділки: світні (заряд), ледь помітні (розряджена частина).
        val gap = cavity.height * 0.14f
        val cellW = (cavity.width - gap * (BATTERY_TICKS + 1)) / BATTERY_TICKS
        val cellH = cavity.height - gap * 2
        val lit = if (low) Oled.Danger else Color.White
        repeat(BATTERY_TICKS) { i ->
            val x = wall + gap + i * (cellW + gap)
            val topLeft = Offset(x, wall + gap)
            val cell = Size(cellW, cellH)
            val r = CornerRadius(cellW * 0.3f)
            if (i < filled) {
                val b = boost(i)
                val glow = gap * 0.45f
                drawRoundRect(lit.copy(alpha = 0.1f + 0.3f * b), topLeft - Offset(glow, glow), Size(cellW + glow * 2, cellH + glow * 2), CornerRadius(r.x + glow))
                drawRoundRect(
                    Brush.verticalGradient(listOf(lit, lerp(lit, Color.Black, 0.28f - 0.2f * b)), topLeft.y, topLeft.y + cellH),
                    topLeft, cell, r,
                )
            } else {
                drawRoundRect(Color(0x14FFFFFF), topLeft, cell, r)
            }
        }
        // Відблиск скла на верхній половині та тонкий кант рамки.
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color.Transparent), wall, wall + cavity.height * 0.5f),
            Offset(wall, wall), Size(cavity.width, cavity.height * 0.5f), inner,
        )
        drawRoundRect(Color(0x66FFFFFF), Offset(0.5f, 0.5f), Size(bodyW - 1f, bodyH - 1f), radius, style = Stroke(1f))
    }
}

/** Метал: світлий край угорі, темна смуга нижче середини, відбите світло біля низу. */
private fun metalBrush(h: Float, top: Float = 0f, height: Float = h) = Brush.verticalGradient(
    0f to Color(0xFFF2F2F2),
    0.18f to Color(0xFFB9B9B9),
    0.5f to Color(0xFF5E5E5E),
    0.78f to Color(0xFF2E2E2E),
    1f to Color(0xFF9A9A9A),
    startY = top,
    endY = top + height,
)

private const val BATTERY_TICKS = 10
private const val LOW_BATTERY = 15

/** Скільки висоти тане край прокручуваного вмісту. */
internal val EdgeFade = 18.dp

/**
 * Розмитий край прокручуваного вмісту: те, що гортається, плавно тане біля заголовка (згори) і біля низу, а не
 * обрізається різкою межею. Працює однаково в обох стилях.
 */
fun Modifier.fadingEdges(top: Dp = EdgeFade, bottom: Dp = EdgeFade): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val t = top.toPx()
            val b = bottom.toPx()
            if (t > 0f) {
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = t),
                    size = Size(size.width, t),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (b > 0f) {
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - b, endY = size.height),
                    topLeft = Offset(0f, size.height - b),
                    size = Size(size.width, b),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
