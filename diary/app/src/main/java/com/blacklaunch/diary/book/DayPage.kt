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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.blacklaunch.diary.ui.HandFont
import com.blacklaunch.diary.ui.GlassFont
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
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
    /** Додати фото дня: 0 — камера, 1 — галерея, 2 — файл. */
    fun addPhoto(day: Long, source: Int)
    fun recordVoice(day: Long)
    fun dismissSlot(day: Long, photo: Boolean)
    /** Backspace на самому початку другої сторінки: стерти символ перед [at] (кінець першої). */
    fun joinBack(day: Long, at: Int)
}

/** Іде запис голосу (для смужки — живі обриси й час). */
class RecordingState(val day: Long, val side: Int = 0, val base: Long = 0L, val cutFrom: Long? = null, val cutTo: Long? = null, val mode: Int = 0) {
    /** «Запис + пауза»: стрічка стоїть, нічого не пишеться. */
    var paused by mutableStateOf(false)
    var wave by mutableStateOf<List<Float>>(emptyList())
    var elapsed by mutableStateOf(0L)
    /** Гучність просто зараз (0…1) — для стрілки. */
    var level by mutableStateOf(0f)
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
    photoSlot: Boolean,
    audioSlot: Boolean,
    pageWidthPx: Float,
    hours: Long,
    highlight: IntRange?,
    ink: InkSession?,
    yearsAgo: Long?,
    editable: Boolean,
    number: Int? = null,
    ribbon: () -> Float = { 0f },
    strikeMode: Boolean = false,
    stub: Boolean = false,
    /** Цю сторінку зараз виривають: палець (кут) — скотч лишається лише на тій частині, що лежить. */
    tearFinger: (() -> androidx.compose.ui.geometry.Offset?)? = null,
) {
    val density = LocalDensity.current
    val glass = AppStyle.glass
    // Силует під прозорою сторінкою — лише малюнок, без редагування.
    val preview = LocalPagePreview.current
    val underTear = LocalUnderTear.current
    // Висохлий день: нічого не змінити — ні текст, ні фото, ні голос, ні місце (лише олівець, він окремо).
    val dry = DiaryViewModel.isDry(day.day)
    @Suppress("NAME_SHADOWING") val editable = editable && !preview && !dry
    val u = pageWidthPx / PageMetrics.WIDTH
    fun dp(v: Float): Dp = with(density) { (v * u).toDp() }
    val now = remember(day.updated) { System.currentTimeMillis() }
    val first = part == 0
    val textTop = if (first) PageMetrics.firstTextTop(photoSlot, audioSlot) else PageMetrics.TEXT_TOP_SECOND
    val lineCount = if (first) PageMetrics.firstLines(photoSlot, audioSlot) else PageMetrics.secondLines
    val lines = remember(textTop, lineCount, u) { List(lineCount) { i -> (textTop + (i + 1) * PageMetrics.LINE - PageMetrics.LINE * 0.14f) * u } }
    val style = pageTextStyle(u, density, glass)
    val locale = Locale.getDefault()
    ReportPaperSeed(pageSeed(day.day, part), DiaryViewModel.today() - day.day)

    Box(
        Modifier
            .fillMaxSize()
            // Усе на сторінці — в її межах: фото, що відсуваєш свайпом, за край книжки не виходить, а зникає за ним.
            .clip(RoundedCornerShape(BookCorner))
            // «Аркуш»: усе написане й намальоване лежить на папері — темніє й світлішає разом із заломами, бере зерно.
            .paperRelief(glass, pageSeed(day.day, part))
            // Старіння: з віком сторінка тьмяніє, потирається, на ній з'являються плями — у кожної своє й назавжди те саме.
            .drawWithContent {
                drawContent()
                drawAging(DiaryViewModel.today() - day.day, (day.day * 31 + part).toInt(), glass)
                // Колись надірвали й передумали — розрив заклеєно скотчем.
                val tapeK = day.tearTry(part)
                val tapeSeed = tearSeedOf(day.day, part)
                val tapeMargin = PageMetrics.MARGIN_LEFT * size.width / PageMetrics.WIDTH
                val f = tearFinger?.invoke()
                if (underTear) Unit
                else if (f == null || tapeK <= 0.01f) drawTapedTear(tapeK, tapeSeed, glass, tapeMargin)
                else {
                    // Те, що піднялося, забрало свій скотч із собою (його малює клапоть) — тут лише те, що лежить.
                    val stay = TearGeometry(size.width, size.height, f).stay
                    val path = androidx.compose.ui.graphics.Path().apply {
                        if (stay.isNotEmpty()) { moveTo(stay[0].x, stay[0].y); stay.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                    }
                    clipPath(path) { drawTapedTear(tapeK, tapeSeed, glass, tapeMargin) }
                }
                // Клаптик раніше вирваної сторінки лежить ЗВЕРХУ: з-під нього видно лише те, що виглядає (скотч, розрив).
                if (stub && !underTear) drawTornStub((day.day * 7 + part).toInt(), glass)
                // Закладка-пошук — на кожній сторінці, поверх усього на ній (і клаптиків). Сторінки, що лише просвічують
                // крізь скло чи лежать під тією, яку виривають, її не малюють — інакше видно другу, розмиту.
                if (!preview) drawSearchRibbon(glass)
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawPaper(u, pageSeed(day.day, part), lines)
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
            // Правіше за закладку-пошук, що звисає згори біля корінця, — вона на дату не налягає.
            modifier = Modifier.offset(dp(RIBBON_RIGHT_U + 2.2f), dp(PageMetrics.HEADER_TOP)).width(dp((if (first) 56f else 80f) - (RIBBON_RIGHT_U + 2.2f - PageMetrics.MARGIN_LEFT))),
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
                val carried = if (day.place.isEmpty() && editable) host.vm.carriedPlace(day.day) else null
                val shownPlace = day.place.ifEmpty { carried.orEmpty() }
                Text(
                    shownPlace.ifEmpty { stringResource(R.string.place_hint) },
                    style = style.copy(fontSize = (headerSize * 0.9f).sp, lineHeight = (headerSize * 1.1f).sp, color = if (shownPlace.isEmpty()) Oled.TextSecondary.copy(alpha = 0.7f) else Oled.TextPrimary, textAlign = TextAlign.End),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(3.dp))
                GlyphIcon(Glyph.Pin, stringResource(R.string.place), tint = if (shownPlace.isEmpty()) Oled.TextSecondary else Oled.TextPrimary, size = dp(4.4f))
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
        } else if (first && photoSlot) {
            // Порожнє місце під фото: дотик до значка — камера / галерея / файл; свайп — віддати місце тексту.
            SlotBlock(
                host = host,
                key = "photo_${day.day}",
                label = stringResource(R.string.photo_slot),
                modifier = Modifier.offset(dp(PageMetrics.MARGIN_LEFT), dp(y)),
                width = dp(PageMetrics.textWidth),
                height = dp(PageMetrics.PHOTO_HEIGHT),
                editable = editable,
                actions = listOf(Glyph.Shutter to { host.addPhoto(day.day, 0) }, Glyph.Gallery to { host.addPhoto(day.day, 1) }),
                onDismiss = { host.dismissSlot(day.day, photo = true) },
            )
            y += PageMetrics.PHOTO_HEIGHT + PageMetrics.BLOCK_GAP
        } else {
            LaunchedEffect(day.photo) { host.block("photo_${day.day}", null) }
        }
        val rec = host.recording?.takeIf { it.day == day.day }
        if (first && (day.audio || day.audioB || rec != null)) {
            AudioPlate(
                host = host,
                day = day,
                recording = rec,
                modifier = Modifier.offset(dp(PageMetrics.MARGIN_LEFT), dp(y)),
                width = dp(PageMetrics.textWidth),
                height = dp(PageMetrics.AUDIO_HEIGHT),
                editable = editable,
            )
        } else if (first && audioSlot) {
            SlotBlock(
                host = host,
                key = "audio_${day.day}",
                label = stringResource(R.string.voice_slot),
                modifier = Modifier.offset(dp(PageMetrics.MARGIN_LEFT), dp(y)),
                width = dp(PageMetrics.textWidth),
                height = dp(PageMetrics.AUDIO_HEIGHT),
                editable = editable,
                actions = listOf(Glyph.Mic to { host.recordVoice(day.day) }),
                onDismiss = { host.dismissSlot(day.day, photo = false) },
            )
        } else {
            LaunchedEffect(day.audio, day.audioB) { host.block("audio_${day.day}", null) }
        }

        // Текст.
        val full = day.text
        val segStart = if (first) 0 else split.coerceAtMost(full.length)
        val segEnd = if (first) split.coerceAtMost(full.length) else full.length
        PageText(
            full = full,
            // Висохлий день — увесь текст висохлим чорнилом (тьмяніше): видно, що змінити вже не можна.
            sealedLen = if (dry) day.text.length else day.sealed.length,
            segStart = segStart,
            segEnd = segEnd,
            strikes = day.strikes,
            highlight = highlight,
            style = style,
            widthPx = PageMetrics.textWidth * u,
            heightPx = lineCount * PageMetrics.LINE * u,
            enabled = editable && ink == null,
            focusCursor = if (preview) null else host.focusRequest(day.day, part)?.let { it - segStart },
            onFocusConsumed = { host.consumeFocus() },
            sink = host.textSink(day.day, part),
            onFocus = { host.onFocus(day.day, part, it) },
            onBackAtStart = if (part == 1) ({ host.joinBack(day.day, segStart) }) else null,
            strikeMode = strikeMode,
            onCursor = { host.onCursor(it) },
            onStrike = { s, e -> host.vm.addStrike(day.day, s, e) },
            bookCoords = { host.bookCoords },
            hint = if (!editable) "" else stringResource(if (day.sealed.isEmpty()) R.string.write_hint else R.string.write_more_hint),
            modifier = Modifier.offset(dp(PageMetrics.MARGIN_LEFT), dp(textTop)),
            // Текст угорі сторінки — перший рядок за закладкою-пошуком.
            indentFirst = if (!first || (!photoSlot && !audioSlot)) ribbonIndentPx(u, density) else 0f,
        )

        // Перо — поверх усієї сторінки.
        InkLayer(
            lines = day.ink,
            part = part,
            pageWidth = pageWidthPx,
            session = ink?.takeIf { it.day == day.day && it.part == part },
            // Олівець — єдине, що працює й на висохлій сторінці: малюється, стирається, кольору не змінює.
            dry = { false },
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
    val measurer = rememberTextMeasurer()
    val label = stringResource(R.string.swipe_delete)
    val glassNow = AppStyle.glass
    val preview = LocalPagePreview.current
    var pageLeft by remember { mutableFloatStateOf(0f) }
    DisposableEffect(key) { onDispose { host.block(key, null) } }
    return this
        .onGloballyPositioned { c ->
            if (preview) return@onGloballyPositioned
            val book = host.bookCoords ?: return@onGloballyPositioned
            val o = runCatching { book.localPositionOf(c, Offset.Zero) }.getOrNull() ?: return@onGloballyPositioned
            host.block(key, Rect(o, Size(c.size.width.toFloat(), c.size.height.toFloat())))
            pageLeft = o.x
        }
        // Під тим, що відсуваєш, — напис «Видалити» праворуч (як в інших програмах); проступає разом зі свайпом.
        .drawBehind {
            val k = (-dx.value / (size.width * 0.33f)).coerceIn(0f, 1f)
            if (k > 0.01f) {
                val l = measurer.measure(label, TextStyle(color = Oled.Danger, fontFamily = if (glassNow) GlassFont else HandFont, fontSize = if (glassNow) 15.sp else 19.sp, fontWeight = FontWeight.Medium))
                drawText(l, topLeft = Offset(size.width - l.size.width - 12.dp.toPx(), (size.height - l.size.height) / 2), alpha = k)
            }
        }
        .graphicsLayer {
            translationX = dx.value
            alpha = 1f - (kotlin.math.abs(dx.value) / (size.width.coerceAtLeast(1f))).coerceIn(0f, 0.8f)
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
        }
        // Біля краю сторінки те, що відсуваєш, м'яко тане — ніби йде під край книжки, а не обрізається.
        .drawWithContent {
            drawContent()
            if (dx.value < -0.5f) {
                val edge = -pageLeft - dx.value
                val fade = 18.dp.toPx()
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = edge, endX = edge + fade),
                    blendMode = BlendMode.DstIn,
                )
            }
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

/**
 * Порожнє місце під фото чи голос: тонка пунктирна рамка, підпис і значки (дотик — додати). Свайп уліво з «Видалити» —
 * прибрати місце на цей день, його займе текст.
 */
@Composable
private fun SlotBlock(
    host: PageHost,
    key: String,
    label: String,
    modifier: Modifier,
    width: Dp,
    height: Dp,
    editable: Boolean,
    actions: List<Pair<Glyph, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    val glass = AppStyle.glass
    val haptics = LocalHaptics.current
    val ink = if (glass) Color(0x33FFFFFF) else SheetInk.copy(alpha = 0.3f)
    Box(modifier.size(width, height).swipeAway(host, key, editable, onDismiss)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = minOf(10.dp.toPx(), size.height / 2)
            drawRoundRect(
                ink,
                cornerRadius = CornerRadius(r),
                style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
            )
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (actions.size > 1) androidx.compose.foundation.layout.Arrangement.Center else androidx.compose.foundation.layout.Arrangement.Start,
        ) {
            if (actions.size == 1) {
                Text(label, color = Oled.TextSecondary, fontFamily = if (glass) GlassFont else HandFont, fontSize = if (glass) 14.sp else 18.sp, modifier = Modifier.weight(1f))
            }
            actions.forEach { (glyph, action) ->
                Box(
                    Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(enabled = editable) { haptics.tap(); action() },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(glyph, label, tint = Oled.TextSecondary, size = 24.dp) }
            }
        }
        if (actions.size > 1) {
            Text(
                label,
                color = Oled.TextSecondary.copy(alpha = 0.7f),
                fontFamily = if (glass) GlassFont else HandFont,
                fontSize = if (glass) 13.sp else 17.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun PhotoBlock(host: PageHost, day: Long, version: Long, modifier: Modifier, width: Dp, height: Dp, editable: Boolean) {
    val glass = AppStyle.glass
    // З пам'яті одразу: при перегортанні сторінка з'являється вже з фото, а не перемальовується на очах.
    var image by remember(day, glass) { mutableStateOf(host.vm.cachedPhoto(day, glass)) }
    LaunchedEffect(day, glass, version) { host.vm.photo(day, glass)?.let { image = it } }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(width, height)
            .swipeAway(host, "photo_$day", editable) { host.vm.removePhoto(day) }
            .pointerInput(day) { detectTapGestures(onTap = { host.openPhoto(day) }, onLongPress = { host.openPhoto(day) }) },
    ) {
        // Фото видно повністю, у його власній пропорції — вписане в місце (нічого не обрізається): вертикальне стоїть
        // посередині, горизонтальне — на всю ширину.
        val ratio = image?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }
        val slotRatio = width / height
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StyledPhoto(
                image,
                glass,
                if (ratio != null) Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < slotRatio) else Modifier.fillMaxSize(),
                ContentScale.Crop,
                caption = host.vm.day(day).caption,
                dry = DiaryViewModel.isDry(day),
                seed = day,
            )
        }
    }
}

/**
 * Смужка голосу — як програвач лаунчера: ліворуч капсула з обрисами звуку й часом (дотик чи ведення — перемотати),
 * праворуч кругла кнопка «грати / пауза». Поки йде запис — живі обриси, час і кнопка «стоп» (вишнева — мікрофон слухає).
 */
/**
 * Голос дня на сторінці — маленька плашка: касетка, «Запис голосу» й тривалість (під час запису — червоний вогник і час).
 * Дотик — магнітофон на весь щоденник (там слухати, перемотувати, записати заново). Свайп убік — прибрати запис.
 */
@Composable
private fun AudioPlate(host: PageHost, day: Day, recording: RecordingState?, modifier: Modifier, width: Dp, height: Dp, editable: Boolean) {
    val haptics = LocalHaptics.current
    val glass = AppStyle.glass
    val ink = if (glass) Oled.TextPrimary else SheetInk
    Row(
        modifier
            .size(width, height)
            .swipeAway(host, "audio_${day.day}", editable && recording == null) { host.vm.removeAudio(day.day) }
            .pencilFrame(height / 2, seed = 23)
            .clip(RoundedCornerShape(height / 2))
            .clickable { haptics.tap(); host.recordVoice(day.day) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Касетка — маленька, у справжніх пропорціях (≈ 100 × 63), по центру плашки.
        Canvas(Modifier.size(height * 0.52f * 1.58f, height * 0.52f)) {
            val w = size.width
            val h = size.height
            val line = ink.copy(alpha = 0.75f)
            val st = 1.1.dp.toPx()
            drawRoundRect(line, cornerRadius = CornerRadius(w * 0.07f), style = Stroke(st))
            // Наклейка.
            drawRoundRect(line.copy(alpha = 0.45f), topLeft = Offset(w * 0.09f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.82f, h * 0.52f), cornerRadius = CornerRadius(w * 0.04f), style = Stroke(0.8.dp.toPx()))
            // Віконце між котушками.
            drawRoundRect(line.copy(alpha = 0.5f), topLeft = Offset(w * 0.37f, h * 0.30f), size = androidx.compose.ui.geometry.Size(w * 0.26f, h * 0.22f), cornerRadius = CornerRadius(w * 0.02f), style = Stroke(0.8.dp.toPx()))
            // Котушки із зубчиками.
            val cy = h * 0.41f
            val rr = h * 0.13f
            listOf(w * 0.27f, w * 0.73f).forEach { cx ->
                drawCircle(line, rr, Offset(cx, cy), style = Stroke(st))
                for (k in 0 until 3) {
                    val t = k * 2.094f
                    drawLine(line, Offset(cx, cy) + Offset(kotlin.math.cos(t), kotlin.math.sin(t)) * (rr * 0.35f), Offset(cx, cy) + Offset(kotlin.math.cos(t), kotlin.math.sin(t)) * (rr * 0.8f), 0.9.dp.toPx())
                }
            }
            // Виступ знизу.
            val bump = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.2f, h); lineTo(w * 0.26f, h * 0.76f); lineTo(w * 0.74f, h * 0.76f); lineTo(w * 0.8f, h)
            }
            drawPath(bump, line.copy(alpha = 0.6f), style = Stroke(0.9.dp.toPx()))
        }
        Spacer(Modifier.width(18.dp))
        Text(
            stringResource(R.string.recorder_title),
            color = Oled.TextPrimary,
            fontFamily = if (glass) GlassFont else HandFont,
            fontSize = if (glass) 15.sp else 19.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (recording != null) {
            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFB0244F)))
            Spacer(Modifier.width(6.dp))
        }
        Text(clock(recording?.elapsed ?: ((if (day.audio) day.audioMs else 0L) + (if (day.audioB) day.audioMsB else 0L))), color = if (recording != null) Color(0xFFB0244F) else Oled.TextSecondary, fontSize = 13.sp, maxLines = 1)
    }
}

/** «1:05». */
fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Фото під тему — однаково й маленьким на сторінці, і збільшеним: «Скло» — під товстою скляною пластиною (тінь, фаска,
 * притемнені краї, відблиск); «Аркуш» — біла штриховка олівцем просто на папері, краї тануть. Жодних додаткових рамок.
 */
@Composable
internal fun StyledPhoto(image: ImageBitmap?, glass: Boolean, modifier: Modifier, scale: ContentScale, caption: String = "", seed: Long = 0L, dry: Boolean = false) {
    // Висохлий день: підпис тьмяніший, як і текст — видно, що змінити вже не можна.
    val captionInk = if (dry) DryInk else if (glass) Oled.TextPrimary else SheetInk
    val shape = RoundedCornerShape(10.dp)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        // Від висоти фото (а не ширини): на вузькому вертикальному фото підпис не дрібніє; довгий — у два рядки.
        val captionSize = with(LocalDensity.current) { minOf(maxHeight * 0.085f, maxWidth * 0.1f).toSp() }
        if (glass) {
            // «Скло»: фото лежить під товстою скляною пластиною — тінь під пластиною, фаска по краях (світла вгорі
            // ліворуч, темна внизу праворуч), притемнені «заломлені» краї й косий відблиск.
            Canvas(Modifier.fillMaxSize()) {
                val r = 10.dp.toPx()
                drawRoundRect(Color(0x80000000), topLeft = Offset(2.dp.toPx(), 3.dp.toPx()), size = size, cornerRadius = CornerRadius(r))
            }
            // Фото теж під тим самим товстим склом: просвічує, але менше, ніж сам аркуш (аркуш + фото).
            Box(Modifier.fillMaxSize().clip(shape).background(Color.Black.copy(alpha = GLASS_PHOTO_ALPHA))) {
                image?.let {
                    androidx.compose.foundation.Image(it, contentDescription = stringResource(R.string.photo_of_day), contentScale = scale, alpha = GLASS_PHOTO_ALPHA, modifier = Modifier.fillMaxSize())
                }
                Canvas(Modifier.fillMaxSize()) {
                    val bevel = 7.dp.toPx()
                    // Товща скла: краї темніші, ніби світло заломлюється.
                    drawRect(Brush.radialGradient(0.55f to Color.Transparent, 1f to Color(0x66000000), center = center, radius = size.maxDimension * 0.62f))
                    // Фаска: світла вгорі й ліворуч, темна внизу й праворуч.
                    drawRect(Brush.verticalGradient(0f to Color(0x40FFFFFF), 1f to Color.Transparent, startY = 0f, endY = bevel), size = Size(size.width, bevel))
                    drawRect(Brush.horizontalGradient(0f to Color(0x2EFFFFFF), 1f to Color.Transparent, startX = 0f, endX = bevel), size = Size(bevel, size.height))
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0x59000000), startY = size.height - bevel, endY = size.height), topLeft = Offset(0f, size.height - bevel), size = Size(size.width, bevel))
                    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color(0x4D000000), startX = size.width - bevel, endX = size.width), topLeft = Offset(size.width - bevel, 0f), size = Size(bevel, size.height))
                    // Відблиск на поверхні скла: широкий, з м'якими краями (без чітких ліній) і на кожному фото свій —
                    // кут, місце й ширина залежать від дня.
                    val rnd = java.util.Random(seed * 7919 + 13)
                    val ang = Math.toRadians(20.0 + rnd.nextDouble() * 50.0).toFloat()
                    val at = 0.25f + rnd.nextFloat() * 0.5f
                    val wide = size.width * (0.25f + rnd.nextFloat() * 0.25f)
                    val c = Offset(size.width * at, size.height / 2)
                    val n = Offset(kotlin.math.cos(ang), -kotlin.math.sin(ang))
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent, 0.35f to Color(0x08FFFFFF), 0.5f to Color(0x10FFFFFF), 0.65f to Color(0x08FFFFFF), 1f to Color.Transparent,
                            start = c - n * wide, end = c + n * wide,
                        ),
                    )
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val r = 10.dp.toPx()
                val w = 1.dp.toPx()
                drawRoundRect(Color(0x55FFFFFF), topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(r - w / 2), style = Stroke(w))
            }
        } else {
            // «Аркуш»: фото намальоване білим олівцем просто на цьому аркуші — без рамки й без чорного тла, краї тануть
            // у папір (прозорість — у самому малюнку, [com.blacklaunch.diary.image.Stylize.pencil]).
            image?.let {
                androidx.compose.foundation.Image(
                    it,
                    contentDescription = stringResource(R.string.photo_of_day),
                    contentScale = scale,
                    modifier = Modifier
                        .fillMaxSize()
                        // Краї тануть по видимій рамці (а не лише по краю самого фото — його може бути обрізано).
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val f = minOf(size.width, size.height) * 0.1f
                            val fx = f / size.width
                            val fy = f / size.height
                            drawRect(Brush.horizontalGradient(0f to Color.Transparent, fx to Color.Black, 1f - fx to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                            drawRect(Brush.verticalGradient(0f to Color.Transparent, fy to Color.Black, 1f - fy to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                        },
                )
            }
        }
        // Підпис до фото: «Аркуш» — почерком просто в розмитому низу фото; «Скло» — на маленькій скляній плашечці.
        if (caption.isNotBlank()) {
            // Усередині фото (у розмитому низу «Аркуша»), а не під ним — інакше обрізався б по краю фото.
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (glass) maxHeight * 0.05f else maxHeight * 0.015f, start = 12.dp, end = 12.dp)) {
                if (glass) {
                    com.blacklaunch.diary.book.GlassPlate(radius = 10.dp) {
                        Text(
                            caption,
                            style = TextStyle(fontFamily = GlassFont, fontWeight = FontWeight.Light, fontSize = captionSize * 0.85f, color = captionInk),
                            maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                } else {
                    Text(
                        caption,
                        style = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Medium, fontSize = captionSize, color = captionInk, shadow = Shadow(Color.Black, blurRadius = 10f)),
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** «Скло»: наскільки непрозоре фото на аркуші (просвічує менше, ніж сам аркуш). */
const val GLASS_PHOTO_ALPHA = 0.94f
