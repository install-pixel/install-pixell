package com.blacklaunch.diary.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.data.Strike
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.GlassFont
import com.blacklaunch.diary.ui.HandFont
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.pencilLoop
import com.blacklaunch.diary.ui.pencilStroke
import com.blacklaunch.diary.ui.taperedPath
import com.blacklaunch.diary.ui.theme.Oled

/** Свіже чорнило — яскраве; висохле — трохи тьмяніше, як справжнє. */
val FreshInk = Color(0xFFEDEDED)
val DryInk = Color(0xFFC4C4C4)

/**
 * Шрифт сторінки — у px від ширини сторінки, тож однаковий на будь-якому екрані й у будь-якому масштабі шрифту
 * телефона (див. [PageMetrics]).
 */
fun pageTextStyle(unitPx: Float, density: Density, glass: Boolean): TextStyle {
    val k = density.density * density.fontScale
    val font = (if (glass) PageMetrics.FONT_GLASS else PageMetrics.FONT_SHEET) * unitPx / k
    val line = PageMetrics.LINE * unitPx / k
    return TextStyle(
        fontFamily = if (glass) GlassFont else HandFont,
        fontSize = font.sp,
        lineHeight = line.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        // «Аркуш»: чорнило ледь прозоре — крізь нього проступає папір, текст вписаний, а не накладений.
        color = if (glass) FreshInk else FreshInk.copy(alpha = 0.82f),
    )
}

/** Що станеться після зміни тексту (вирішує книга: місце, висохле, перехід на іншу сторінку). */
fun interface TextSink {
    /** true — прийнято. [cursor] — курсор у повному тексті дня. */
    fun change(full: String, cursor: Int): Boolean
}

/**
 * Текст однієї сторінки дня: шматок [segStart]…[segEnd] повного тексту [full]. Поки не пишеш — дотик ставить курсор
 * (і відкриває клавіатуру), а довге натискання й ведення по висохлому — викреслює. Поки пишеш — звичайне поле, але
 * висохле не змінюється (це стереже книга, [TextSink]).
 */
@Composable
fun PageText(
    full: String,
    sealedLen: Int,
    segStart: Int,
    segEnd: Int,
    strikes: List<Strike>,
    highlight: IntRange?,
    style: TextStyle,
    widthPx: Float,
    heightPx: Float,
    enabled: Boolean,
    focusCursor: Int?,
    onFocusConsumed: () -> Unit,
    sink: TextSink,
    onFocus: (Boolean) -> Unit,
    onCursor: (Rect) -> Unit,
    onStrike: (Int, Int) -> Unit,
    bookCoords: () -> LayoutCoordinates?,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    val keyboard = LocalSoftwareKeyboardController.current
    val glass = AppStyle.glass
    val segment = full.substring(segStart.coerceIn(0, full.length), segEnd.coerceIn(segStart.coerceIn(0, full.length), full.length))
    val localSealed = (sealedLen - segStart).coerceIn(0, segment.length)
    var value by remember { mutableStateOf(TextFieldValue(segment, TextRange(segment.length))) }
    if (value.text != segment) {
        // Текст змінився ззовні (перенесення на іншу сторінку, «Повернути»): лишаємо курсор, де можна.
        value = TextFieldValue(segment, TextRange(value.selection.start.coerceIn(0, segment.length), value.selection.end.coerceIn(0, segment.length)))
    }
    var focused by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val focus = remember { FocusRequester() }
    var strikeFrom by remember { mutableStateOf<Int?>(null) }
    var strikeTo by remember { mutableStateOf<Int?>(null) }
    val currentSink by rememberUpdatedState(sink)
    val highlightAlpha = remember { Animatable(0f) }
    LaunchedEffect(highlight) {
        if (highlight != null) {
            highlightAlpha.snapTo(1f)
            kotlinx.coroutines.delay(2600)
            highlightAlpha.animateTo(0f, tween(900))
        }
    }

    fun reportCursor() {
        val l = layout ?: return
        val c = coords ?: return
        val book = bookCoords() ?: return
        // Розкладка могла ще не встигнути за текстом (швидке письмо) — курсор лише в її межах, інакше падіння.
        val r = l.getCursorRect(value.selection.end.coerceIn(0, minOf(value.text.length, l.layoutInput.text.length)))
        val origin = runCatching { book.localPositionOf(c, Offset.Zero) }.getOrNull() ?: return
        onCursor(r.translate(origin))
    }

    // Курсор перемістився без зміни тексту (дотик в інше місце, стрілки) — книжка теж має знати, куди піднятися.
    LaunchedEffect(value.selection, focused) { if (focused) reportCursor() }

    LaunchedEffect(focusCursor) {
        if (focusCursor != null) {
            val local = focusCursor.coerceIn(0, segment.length).coerceAtLeast(localSealed)
            value = value.copy(selection = TextRange(local))
            runCatching { focus.requestFocus() }
            keyboard?.show()
            onFocusConsumed()
        }
    }

    // Висохле — тьмяніше (лише колір; розкладка тексту та сама).
    val dry = remember(localSealed) {
        VisualTransformation { text ->
            val n = localSealed.coerceAtMost(text.length)
            TransformedText(
                buildAnnotatedString {
                    append(text)
                    if (n > 0) addStyle(SpanStyle(color = if (AppStyle.glass) DryInk else DryInk.copy(alpha = 0.8f)), 0, n)
                },
                OffsetMapping.Identity,
            )
        }
    }

    val wDp = with(density) { widthPx.toDp() }
    val hDp = with(density) { heightPx.toDp() }
    Box(modifier.size(wDp, hDp)) {
        if (segment.isEmpty() && !focused && segStart == 0) {
            Text(hint, style = style.copy(color = Oled.TextSecondary))
        }
        BasicTextField(
            value = value,
            onValueChange = { v ->
                if (v.text == value.text) {
                    // Лише курсор/виділення: у висохле курсор не ставимо (там пишеться лише викресленням).
                    val sel = if (v.selection.collapsed && v.selection.start < localSealed) TextRange(localSealed) else v.selection
                    value = v.copy(selection = sel)
                    return@BasicTextField
                }
                val newFull = full.substring(0, segStart.coerceIn(0, full.length)) + v.text + full.substring(segEnd.coerceIn(0, full.length))
                if (currentSink.change(newFull, segStart + v.selection.end)) value = v else haptics.tick()
            },
            readOnly = !enabled,
            textStyle = style,
            cursorBrush = SolidColor(if (glass) Oled.TextPrimary else Oled.TextSecondary),
            visualTransformation = dry,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            onTextLayout = { layout = it; if (focused) reportCursor() },
            // Висота — за текстом, а не рівно N рядків: шрифт заокруглюється до пікселів, і текст бував на 1–2 px вищим
            // за поле — тоді поле прокручувало себе, і текст «плив» відносно ліній. Поле, вище за текст, не прокручується.
            modifier = Modifier
                .width(wDp)
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .heightIn(min = hDp)
                .focusRequester(focus)
                .onFocusChanged { f ->
                    if (f.isFocused != focused) {
                        focused = f.isFocused
                        onFocus(f.isFocused)
                        if (f.isFocused) reportCursor()
                    }
                }
                .onGloballyPositioned { coords = it; if (focused) reportCursor() }
                .drawWithContent {
                    drawContent()
                    val l = layout ?: return@drawWithContent
                    strikes.forEachIndexed { i, s -> drawStrike(l, s.start - segStart, s.end - segStart, segment.length, i, 1f) }
                    val a = strikeFrom
                    val b = strikeTo
                    if (a != null && b != null) drawStrike(l, minOf(a, b), maxOf(a, b), segment.length, 99, 0.55f)
                    if (highlight != null && highlightAlpha.value > 0f) drawHighlight(l, highlight.first - segStart, highlight.last + 1 - segStart, segment.length, highlightAlpha.value)
                },
        )
        if (!focused && enabled) {
            // Поки не пишеш: дотик — писати з цього місця; довге натискання й ведення — викреслити висохле.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(segStart, segEnd, localSealed) {
                        detectTapGestures { pos ->
                            val l = layout
                            val off = l?.getOffsetForPosition(pos) ?: segment.length
                            value = value.copy(selection = TextRange(off.coerceIn(localSealed, segment.length)))
                            runCatching { focus.requestFocus() }
                            keyboard?.show()
                        }
                    }
                    .pointerInput(segStart, segEnd, localSealed) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                val l = layout ?: return@detectDragGesturesAfterLongPress
                                val off = l.getOffsetForPosition(pos)
                                if (off < localSealed) {
                                    haptics.longPress()
                                    strikeFrom = off
                                    strikeTo = off
                                }
                            },
                            onDrag = { c, _ ->
                                if (strikeFrom != null) {
                                    c.consume()
                                    strikeTo = layout?.getOffsetForPosition(c.position)?.coerceAtMost(localSealed)
                                }
                            },
                            onDragEnd = {
                                val a = strikeFrom
                                val b = strikeTo
                                strikeFrom = null
                                strikeTo = null
                                if (a != null && b != null) {
                                    val (s, e) = wordRange(segment, minOf(a, b), maxOf(a, b))
                                    val ge = (segStart + e).coerceAtMost(sealedLen)
                                    if (ge > segStart + s) onStrike(segStart + s, ge)
                                }
                            },
                            onDragCancel = { strikeFrom = null; strikeTo = null },
                        )
                    },
            )
        }
    }
}

/** Розширити виділення до цілих слів (викреслюють слова, а не половинки). */
fun wordRange(text: String, start: Int, end: Int): Pair<Int, Int> {
    var s = start.coerceIn(0, text.length)
    var e = end.coerceIn(0, text.length)
    while (s > 0 && text[s - 1].isLetterOrDigit()) s--
    while (e < text.length && text[e].isLetterOrDigit()) e++
    if (e == s && e < text.length) e++
    return s to e
}

/** Ділянки рядків для діапазону [start, end) — по одній на рядок. */
private fun lineBoxes(l: TextLayoutResult, start: Int, end: Int, len: Int): List<Rect> {
    val s = start.coerceIn(0, len)
    val e = end.coerceIn(0, len)
    if (e <= s) return emptyList()
    val out = ArrayList<Rect>()
    val first = l.getLineForOffset(s)
    val last = l.getLineForOffset((e - 1).coerceAtLeast(s))
    for (line in first..last) {
        val ls = maxOf(s, l.getLineStart(line))
        val le = minOf(e, l.getLineEnd(line, visibleEnd = true))
        if (le <= ls) continue
        val x1 = l.getHorizontalPosition(ls, usePrimaryDirection = true)
        val x2 = l.getHorizontalPosition(le, usePrimaryDirection = true)
        out += Rect(minOf(x1, x2), l.getLineTop(line), maxOf(x1, x2), l.getLineBottom(line))
    }
    return out
}

/**
 * Викреслене. «Аркуш» — перекреслено олівцем (двома штрихами, як рукою). «Скло» — на словах лежить скляна «крапля»:
 * матова, з відблиском і кантом, під нею слова ледь видно, ніби розмазані.
 */
private fun DrawScope.drawStrike(l: TextLayoutResult, start: Int, end: Int, len: Int, seed: Int, alpha: Float) {
    lineBoxes(l, start, end, len).forEachIndexed { i, r ->
        if (AppStyle.glass) {
            val h = r.height * 0.62f
            val top = r.center.y - h / 2
            val rect = Rect(r.left - 2.dp.toPx(), top, r.right + 2.dp.toPx(), top + h)
            val cr = CornerRadius(h / 2)
            drawRoundRect(Brush.verticalGradient(0f to Color(0xF02A2A2A), 0.5f to Color(0xF5141414), 1f to Color(0xF20C0C0C), startY = rect.top, endY = rect.bottom), rect.topLeft, rect.size, cr, alpha = alpha)
            drawRoundRect(Color(0x22FFFFFF), Offset(rect.left + h * 0.3f, rect.top + h * 0.12f), Size((rect.width - h * 0.6f).coerceAtLeast(1f), h * 0.22f), CornerRadius(h * 0.11f), alpha = alpha)
            drawRoundRect(Oled.Glint, rect.topLeft, rect.size, cr, style = Stroke(1.dp.toPx()), alpha = alpha)
        } else {
            val y = r.center.y + r.height * 0.05f
            pencilStroke(y, r.left - 2.dp.toPx(), r.right + 2.dp.toPx(), seed * 7 + i, alpha = 0.85f * alpha, ink = SheetInk)
            pencilStroke(y + r.height * 0.09f, r.left, r.right, seed * 7 + i + 31, alpha = 0.5f * alpha, ink = SheetInk)
        }
    }
}

/** Підсвітка знайденого: «Аркуш» — обведено олівцем; «Скло» — світиться. */
private fun DrawScope.drawHighlight(l: TextLayoutResult, start: Int, end: Int, len: Int, alpha: Float) {
    lineBoxes(l, start, end, len).forEachIndexed { i, r ->
        val rect = r.inflate(3.dp.toPx())
        if (AppStyle.glass) {
            drawRoundRect(Color(0x26FFFFFF), rect.topLeft, rect.size, CornerRadius(8.dp.toPx()), alpha = alpha)
            drawRoundRect(Oled.GlintBright, rect.topLeft, rect.size, CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()), alpha = alpha)
        } else {
            val loop = pencilLoop(rect.size, rect.height / 2, 71 + i, 1.2.dp.toPx()).map { it + rect.topLeft }
            drawPath(taperedPath(loop, 2.dp.toPx()), SheetInk.copy(alpha = alpha))
        }
    }
}
