package com.blacklaunch.diary.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.DiaryViewModel
import com.blacklaunch.diary.R
import com.blacklaunch.diary.audio.Player
import com.blacklaunch.diary.data.Day
import com.blacklaunch.diary.data.InkLine
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.Glyph
import com.blacklaunch.diary.ui.GlyphIcon
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.pencilFrame
import com.blacklaunch.diary.ui.pencilLoop
import com.blacklaunch.diary.ui.pencilTick
import com.blacklaunch.diary.ui.quadPoints
import com.blacklaunch.diary.ui.taperedPath
import com.blacklaunch.diary.ui.theme.Oled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Що сторінка дня просить у книги. */
interface PageHost {
    val vm: DiaryViewModel
    val bookCoords: LayoutCoordinates?
    fun textSink(day: Long, part: Int): TextSink
    fun onFocus(day: Long, part: Int, focused: Boolean)
    fun onCursor(rect: Rect)
    fun focusRequest(day: Long, part: Int): Int?
    fun consumeFocus()
    fun editPlace(day: Long)
    fun openPhoto(day: Long)
    fun block(key: String, rect: Rect?)
    fun jumpTo(day: Long)
    fun dryHint()
    val recording: RecordingState?
    fun stopRecording()
}

/** Іде запис голосу (для смужки — живі обриси й час). */
class RecordingState(val day: Long) {
    var wave by mutableStateOf<List<Float>>(emptyList())
    var elapsed by mutableStateOf(0L)
}

/**
 * Сторінка дня. [part] 0 — перша (шапка, фото, аудіо, початок тексту), 1 — продовження. [split] — де текст переходить
 * на другу сторінку. Усе розміщено в одиницях сторінки ([PageMetrics]), тож виглядає однаково на будь-якому екрані.
 */
@Composable
fun DayPage(
    host: PageHost,
    day: Day,
    part: Int,
    split: Int,
    pageWidthPx: Float,
    edgeBefore: Int,
    edgeAfter: Int,
    hours: Long,
    highlight: IntRange?,
    ink: InkSession?,
    yearsAgo: Long?,
    editable: Boolean,
    number: Int? = null,
    ribbon: () -> Float = { 0f },
) {
    val density = LocalDensity.current
    val glass = AppStyle.glass
    val u = pageWidthPx / PageMetrics.WIDTH
    fun dp(v: Float): Dp = with(density) { (v * u).toDp() }
    val now = remember(day.updated) { System.currentTimeMillis() }
    val first = part == 0
    val textTop = if (first) PageMetrics.firstTextTop(day.photo, day.audio || host.recording?.day == day.day) else PageMetrics.TEXT_TOP_SECOND
    val lineCount = if (first) PageMetrics.firstLines(day.photo, day.audio || host.recording?.day == day.day) else PageMetrics.secondLines
    val lines = remember(textTop, lineCount, u) { List(lineCount) { i -> (textTop + (i + 1) * PageMetrics.LINE - PageMetrics.LINE * 0.14f) * u } }
    val style = pageTextStyle(u, density, glass)
    val locale = Locale.getDefault()

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawPaper(u, (day.day % 997).toInt() + part * 31, lines, edgeBefore, edgeAfter)
            drawRibbonStrip(ribbon(), glass)
        }

        // Шапка: дата ліворуч, місце праворуч.
        val date = LocalDate.ofEpochDay(day.day)
        val dateText = if (first) {
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale).format(date).replaceFirstChar { it.titlecase(locale) }
        } else {
            DateTimeFormatter.ofPattern("d MMMM", locale).format(date) + " · " + stringResource(R.string.page_continued)
        }
        val headerSize = (if (glass) 3.6f else 4.5f) * u / (density.density * density.fontScale)
        Text(
            dateText,
            style = style.copy(fontSize = headerSize.sp, lineHeight = (headerSize * 1.2f).sp, color = if (first) Oled.TextPrimary else Oled.TextSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(dp(PageMetrics.MARGIN_LEFT), dp(PageMetrics.HEADER_TOP)).width(dp(if (first) 56f else 80f)),
        )
        if (first) {
            Row(
                Modifier
                    .offset(dp(PageMetrics.WIDTH - PageMetrics.MARGIN_RIGHT - 32f), dp(PageMetrics.HEADER_TOP - 1.2f))
                    .width(dp(32f))
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = editable) { host.editPlace(day.day) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                Text(
                    day.place.ifEmpty { stringResource(R.string.place_hint) },
                    style = style.copy(fontSize = (headerSize * 0.9f).sp, lineHeight = (headerSize * 1.1f).sp, color = if (day.place.isEmpty()) Oled.TextSecondary.copy(alpha = 0.7f) else Oled.TextPrimary, textAlign = TextAlign.End),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(3.dp))
                GlyphIcon(Glyph.Pin, stringResource(R.string.place), tint = if (day.place.isEmpty()) Oled.TextSecondary else Oled.TextPrimary, size = dp(4.4f))
            }
        }

        var y = PageMetrics.TEXT_TOP_FIRST - 1.6f
        if (first && day.photo) {
            PhotoBlock(
                host = host,
                day = day.day,
                version = day.updated,
                modifier = Modifier.offset(dp(PageMetrics.MARGIN_LEFT), dp(y)),
                width = dp(PageMetrics.textWidth),
                height = dp(PageMetrics.PHOTO_HEIGHT),
                editable = editable,
            )
            y += PageMetrics.PHOTO_HEIGHT + PageMetrics.BLOCK_GAP
        } else {
            LaunchedEffect(day.photo) { host.block("photo_${day.day}", null) }
        }
        val rec = host.recording?.takeIf { it.day == day.day }
        if (first && (day.audio || rec != null)) {
            AudioStrip(
                host = host,
                day = day,
                recording = rec,
                modifier = Modifier.offset(dp(PageMetrics.MARGIN_LEFT), dp(y)),
                width = dp(PageMetrics.textWidth),
                height = dp(PageMetrics.AUDIO_HEIGHT),
                editable = editable,
            )
        } else {
            LaunchedEffect(day.audio) { host.block("audio_${day.day}", null) }
        }

        // Текст.
        val full = day.text
        val segStart = if (first) 0 else split.coerceAtMost(full.length)
        val segEnd = if (first) split.coerceAtMost(full.length) else full.length
        PageText(
            full = full,
            sealedLen = day.sealed.length,
            segStart = segStart,
            segEnd = segEnd,
            strikes = day.strikes,
            highlight = highlight,
            style = style,
            widthPx = PageMetrics.textWidth * u,
            heightPx = lineCount * PageMetrics.LINE * u,
            enabled = editable && ink == null,
            focusCursor = host.focusRequest(day.day, part)?.let { it - segStart },
            onFocusConsumed = { host.consumeFocus() },
            sink = host.textSink(day.day, part),
            onFocus = { host.onFocus(day.day, part, it) },
            onCursor = { host.onCursor(it) },
            onStrike = { s, e -> host.vm.addStrike(day.day, s, e) },
            bookCoords = { host.bookCoords },
            hint = stringResource(if (day.sealed.isEmpty()) R.string.write_hint else R.string.write_more_hint),
            modifier = Modifier.offset(dp(PageMetrics.MARGIN_LEFT), dp(textTop)),
        )

        // Перо — поверх усієї сторінки.
        InkLayer(
            lines = day.ink,
            part = part,
            pageWidth = pageWidthPx,
            session = ink?.takeIf { it.day == day.day && it.part == part },
            dry = { day.isInkDry(it, now, hours) },
            onChange = { host.vm.setInk(day.day, it) },
            onDryErase = { host.dryHint() },
        )

        // Номер сторінки (лише заповнені) — унизу посередині.
        if (number != null) {
            Text(
                number.toString(),
                style = style.copy(fontSize = (headerSize * 0.8f).sp, lineHeight = (headerSize * 0.9f).sp, color = Oled.TextSecondary, textAlign = TextAlign.Center),
                modifier = Modifier.offset(dp(PageMetrics.WIDTH / 2 - 8f), dp(PageMetrics.HEIGHT - 6.6f)).width(dp(16f)),
            )
        }

        // «Цей день рік тому»: загнутий ріжок унизу праворуч.
        if (first && yearsAgo != null) {
            YearsAgoCorner(yearsAgo, u, Modifier.align(Alignment.BottomEnd)) { host.jumpTo(yearsAgo) }
        }
    }
}

@Composable
private fun YearsAgoCorner(target: Long, u: Float, modifier: Modifier, onClick: () -> Unit) {
    val density = LocalDensity.current
    val s = with(density) { (13f * u).toDp() }
    val years = LocalDate.now().year - LocalDate.ofEpochDay(target).year
    val label = if (years <= 1) stringResource(R.string.year_ago) else androidx.compose.ui.res.pluralStringResource(R.plurals.years_ago, years, years)
    Box(modifier.size(s * 2.6f, s).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }) {
        Canvas(Modifier.fillMaxSize()) {
            val c = size.height
            val x0 = size.width - c
            val corner = androidx.compose.ui.graphics.Path().apply {
                moveTo(x0, size.height); lineTo(size.width, size.height - c); lineTo(size.width, size.height); close()
            }
            drawPath(corner, Color.Black)
            val fold = androidx.compose.ui.graphics.Path().apply {
                moveTo(x0, size.height); lineTo(size.width, size.height - c); lineTo(x0 + c * 0.1f, size.height - c * 0.9f); close()
            }
            if (AppStyle.glass) {
                drawPath(fold, Brush.linearGradient(listOf(Color(0xFF2A2A2A), Color(0xFF0E0E0E)), Offset(x0, size.height - c), Offset(size.width, size.height)))
                drawPath(fold, Color(0x66FFFFFF), style = Stroke(1.dp.toPx()))
            } else {
                drawPath(fold, Color(0xFF080808))
                drawPath(taperedPath(listOf(Offset(x0, size.height), Offset(x0 + c * 0.1f, size.height - c * 0.9f), Offset(size.width, size.height - c)), 1.6.dp.toPx()), SheetInk.copy(alpha = 0.8f))
            }
        }
        Text(label, color = Oled.TextSecondary, fontSize = 11.sp, maxLines = 1, modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp))
    }
}

/**
 * Свайп уліво — прибрати (з «Повернути» у сповіщенні, як скрізь). Ділянка реєструється як така, де гортання не
 * починається ([PageHost.block]).
 */
@Composable
private fun Modifier.swipeAway(host: PageHost, key: String, enabled: Boolean, onRemove: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val haptics = LocalHaptics.current
    DisposableEffect(key) { onDispose { host.block(key, null) } }
    return this
        .onGloballyPositioned { c ->
            val book = host.bookCoords ?: return@onGloballyPositioned
            val o = runCatching { book.localPositionOf(c, Offset.Zero) }.getOrNull() ?: return@onGloballyPositioned
            host.block(key, Rect(o, Size(c.size.width.toFloat(), c.size.height.toFloat())))
        }
        .graphicsLayer {
            translationX = dx.value
            alpha = 1f - (kotlin.math.abs(dx.value) / (size.width.coerceAtLeast(1f))).coerceIn(0f, 0.8f)
        }
        .then(
            if (!enabled) Modifier else Modifier.pointerInput(key) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { c, d ->
                        if (d < 0 || dx.value < 0) {
                            c.consume()
                            scope.launch { dx.snapTo((dx.value + d).coerceAtMost(0f)) }
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (dx.value < -size.width * 0.33f) {
                                haptics.longPress()
                                dx.animateTo(-size.width.toFloat(), tween(160))
                                onRemove()
                                dx.snapTo(0f)
                            } else {
                                dx.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { dx.animateTo(0f) } },
                )
            },
        )
}

@Composable
private fun PhotoBlock(host: PageHost, day: Long, version: Long, modifier: Modifier, width: Dp, height: Dp, editable: Boolean) {
    val glass = AppStyle.glass
    var image by remember(day, glass) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(day, glass, version) { image = host.vm.photo(day, glass) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(width, height)
            .swipeAway(host, "photo_$day", editable) { host.vm.removePhoto(day) }
            .pointerInput(day) { detectTapGestures(onTap = { host.openPhoto(day) }, onLongPress = { host.openPhoto(day) }) },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.Black),
        ) {
            image?.let {
                androidx.compose.foundation.Image(it, contentDescription = stringResource(R.string.photo_of_day), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Canvas(Modifier.fillMaxSize()) {
                if (glass) {
                    drawRect(Brush.verticalGradient(0f to Color(0x1FFFFFFF), 0.45f to Color.Transparent))
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val r = 10.dp.toPx()
            if (glass) {
                drawRoundRect(Oled.Glint, cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
            } else {
                drawPath(taperedPath(pencilLoop(size, r, (day % 97).toInt(), 1.2.dp.toPx()), 2.dp.toPx()), SheetInk.copy(alpha = 0.8f))
                // «Скотч» на кутах — дві риски олівцем навскіс.
                val t = 14.dp.toPx()
                pencilTick(Offset(-t * 0.4f, t * 0.7f), Offset(t * 0.9f, -t * 0.3f), 5.dp.toPx(), SheetInk.copy(alpha = 0.25f), 3)
                pencilTick(Offset(size.width - t * 0.9f, -t * 0.3f), Offset(size.width + t * 0.4f, t * 0.7f), 5.dp.toPx(), SheetInk.copy(alpha = 0.25f), 5)
            }
        }
    }
}

/**
 * Смужка голосу — як програвач лаунчера: ліворуч капсула з обрисами звуку й часом (дотик чи ведення — перемотати),
 * праворуч кругла кнопка «грати / пауза». Поки йде запис — живі обриси, час і кнопка «стоп» (вишнева — мікрофон слухає).
 */
@Composable
private fun AudioStrip(host: PageHost, day: Day, recording: RecordingState?, modifier: Modifier, width: Dp, height: Dp, editable: Boolean) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val glass = AppStyle.glass
    val player = remember(day.day) { Player() }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var bytes by remember(day.day, day.updated) { mutableStateOf<ByteArray?>(null) }
    DisposableEffect(day.day) { onDispose { player.stop() } }
    LaunchedEffect(playing) {
        while (playing) {
            val d = player.duration
            if (d > 0) progress = player.position / d.toFloat()
            delay(50)
        }
    }
    val total = if (recording != null) recording.elapsed else day.audioMs
    val wave = recording?.wave ?: day.wave
    fun toggle() {
        haptics.tap()
        if (recording != null) { host.stopRecording(); return }
        if (playing) {
            player.pause(); playing = false
        } else {
            scope.launch {
                val b = bytes ?: host.vm.audio(day.day).also { bytes = it } ?: return@launch
                val from = if (progress in 0.01f..0.99f) (progress * player.duration.coerceAtLeast(day.audioMs.toInt())).toInt() else 0
                if (player.duration > 0 && progress in 0.01f..0.99f) { player.resume(); playing = true }
                else playing = player.play(b, from) { playing = false; progress = 0f }
            }
        }
    }
    Row(modifier.size(width, height).swipeAway(host, "audio_${day.day}", editable && recording == null) { player.stop(); playing = false; host.vm.removeAudio(day.day) }, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pencilFrame(height / 2, seed = 23)
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(day.day, recording) {
                        if (recording != null) return@pointerInput
                        fun seekTo(x: Float) {
                            progress = (x / size.width).coerceIn(0f, 1f)
                            if (player.duration > 0) player.seek((progress * player.duration).toInt())
                        }
                        detectTapGestures { seekTo(it.x) }
                    },
            ) {
                val bars = wave.ifEmpty { List(40) { 0.08f } }
                val n = bars.size.coerceAtLeast(1)
                val step = size.width / n
                val mid = size.height / 2
                bars.forEachIndexed { i, v ->
                    val h = (0.12f + v * 0.88f) * size.height * 0.42f
                    val x = i * step + step / 2
                    val done = recording != null || (i + 0.5f) / n <= progress
                    val color = when {
                        recording != null -> Color(0xFFB0244F).copy(alpha = 0.9f)
                        done -> Oled.TextPrimary
                        else -> Oled.TextSecondary.copy(alpha = 0.45f)
                    }
                    if (glass) {
                        drawLine(color, Offset(x, mid - h), Offset(x, mid + h), (step * 0.55f).coerceIn(1f, 4.dp.toPx()), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    } else {
                        pencilTick(Offset(x, mid - h), Offset(x + 0.6f, mid + h), (step * 0.6f).coerceIn(1f, 3.dp.toPx()), color, i)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(clock(if (playing || progress > 0f) (progress * total).toLong() else total), color = if (recording != null) Color(0xFFB0244F) else Oled.TextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        val ring = height
        Box(
            Modifier
                .size(ring)
                .then(
                    if (glass) Modifier.clip(CircleShape).background(Oled.Knob).border(1.dp, if (recording != null) androidx.compose.ui.graphics.SolidColor(Color(0xFFB0244F)) else Oled.Glint, CircleShape)
                    else Modifier.pencilFrame(ring / 2, seed = 29, ink = if (recording != null) Color(0xFFB0244F) else SheetInk).clip(CircleShape),
                )
                .clickable { toggle() },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                when {
                    recording != null -> Glyph.Stop
                    playing -> Glyph.Pause
                    else -> Glyph.Play
                },
                stringResource(if (recording != null) R.string.record_stop else R.string.play),
                tint = if (recording != null) Color(0xFFB0244F) else Oled.TextPrimary,
                size = ring * 0.42f,
            )
        }
    }
}

/** «1:05». */
fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}


