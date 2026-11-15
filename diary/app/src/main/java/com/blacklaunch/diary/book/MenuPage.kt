package com.blacklaunch.diary.book

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.MenuEntry
import com.blacklaunch.diary.ui.MenuRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.blacklaunch.diary.ui.theme.Oled

/**
 * Сторінка, намальована як аркуш щоденника, — замість вікон меню й налаштувань: заголовок шрифтом теми, далі пункти.
 * У щоденнику на неї перегортають шестернею; на полиці вона «перегортається» поверх полиці.
 */
@Composable
fun PaperPage(
    title: String,
    subtitle: String?,
    seed: Int,
    modifier: Modifier = Modifier,
    footer: String? = null,
    /** Далі є ще сторінка: за мить з'являється напівпрозора стрілочка гортання ([TurnHint]). */
    turnHint: Boolean = false,
    /** Сторінка-прилад (магнітофон): вміст займає всю висоту, без прокрутки, а [edge] — по всій ширині до нижнього краю. */
    edgeHeight: androidx.compose.ui.unit.Dp = 0.dp,
    edge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val glass = AppStyle.glass
    ReportPaperSeed(seed)
    val holes = androidx.compose.runtime.remember { ReliefHoles() }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { holes.page = it }.paperRelief(glass, seed, holes)) {
        val u = constraints.maxWidth / PageMetrics.WIDTH
        val style = pageTextStyle(u.coerceAtLeast(0.01f), density, glass)
        val headerSize = (if (glass) 5f else 6.2f) * u / (density.density * density.fontScale)
        Canvas(Modifier.fillMaxSize()) { drawPaper(u, seed, emptyList()) }
        // Сторінка сталого розміру: вміст не «пливе» й не пружинить. Прокрутка — лише на крайній випадок, якщо не вмістилось
        // (дуже великий шрифт у системі).
        Column(
            Modifier
                .fillMaxSize()
                .then(if (edge == null) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                // Сторінка-прилад — однакові поля з обох боків (прилад рівно посередині); звичайна — поле зліва під корінцем.
                .padding(
                    start = if (edge == null) with(density) { (PageMetrics.MARGIN_LEFT * u).toDp() } else 18.dp,
                    end = if (edge == null) 14.dp else 18.dp,
                    top = 22.dp,
                    bottom = if (edge == null) 22.dp else edgeHeight + 12.dp,
                ),
        ) {
            Text(title, style = style.copy(fontSize = headerSize.sp, lineHeight = (headerSize * 1.25f).sp, color = Oled.TextPrimary))
            if (subtitle != null) {
                Text(subtitle, style = style.copy(fontSize = (headerSize * 0.6f).sp, lineHeight = (headerSize * 0.8f).sp, color = Oled.TextSecondary))
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalReliefHoles provides holes) {
                Column(Modifier.padding(top = 10.dp).then(if (edge == null) Modifier else Modifier.weight(1f)), content = content)
            }
        }
        if (edge != null) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(edgeHeight)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = BookCorner, bottomEnd = BookCorner)),
            ) { edge() }
        }
        if (turnHint) TurnHint(Modifier.align(androidx.compose.ui.Alignment.BottomEnd))
        if (footer != null) {
            Text(
                footer,
                style = style.copy(fontSize = (headerSize * 0.55f).sp, color = Oled.TextSecondary),
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 18.dp, bottom = 14.dp),
            )
        }
    }
}

/**
 * Підказка «гортай» без слів: тонка напівпрозора стрілочка-дуга справа внизу — куди веде палець, коли гортаєш уперед
 * (справа наліво). З'являється не одразу ([delayMs]) і плавно, щоб не кидатися в очі; зникає разом зі сторінкою.
 */
@Composable
fun TurnHint(modifier: Modifier = Modifier, delayMs: Long = TURN_HINT_DELAY) {
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(delayMs); shown = true }
    val a by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, androidx.compose.animation.core.tween(900), label = "turnHint")
    val color = if (AppStyle.glass) Oled.TextPrimary else com.blacklaunch.diary.ui.SheetInk
    Canvas(modifier.padding(end = 20.dp, bottom = 16.dp).size(width = 44.dp, height = 22.dp)) {
        if (a <= 0f) return@Canvas
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()
        val end = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.7f)
        val ctrl = androidx.compose.ui.geometry.Offset(w * 0.55f, -h * 0.25f)
        val arc = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.95f, h * 0.8f)
            quadraticTo(ctrl.x, ctrl.y, end.x, end.y)
        }
        val ink = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawPath(arc, color, alpha = 0.38f * a, style = ink)
        // Вістря — уздовж дотичної в кінці дуги.
        val dx = end.x - ctrl.x
        val dy = end.y - ctrl.y
        val len = kotlin.math.hypot(dx, dy)
        val ux = dx / len
        val uy = dy / len
        val head = 7.dp.toPx()
        val head1 = androidx.compose.ui.graphics.Path().apply {
            for (s in listOf(1f, -1f)) {
                val c = kotlin.math.cos(0.6f)
                val n = kotlin.math.sin(0.6f) * s
                moveTo(end.x, end.y)
                lineTo(end.x - head * (ux * c - uy * n), end.y - head * (uy * c + ux * n))
            }
        }
        drawPath(head1, color, alpha = 0.38f * a, style = ink)
    }
}

/** Коли з'являється стрілочка гортання. */
const val TURN_HINT_DELAY = 3_000L

/** Пункти меню на сторінці: сірі підзаголовки й рядки (як у вікні меню, але на аркуші). */
@Composable
fun ColumnScope.MenuEntries(entries: List<MenuEntry>) {
    entries.forEach { e ->
        if (e.glyph == null) {
            Text(e.text, color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp))
        } else {
            MenuRow(e.glyph, e.text, e.onClick, e.tone)
        }
    }
}
