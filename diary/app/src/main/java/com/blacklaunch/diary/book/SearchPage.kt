package com.blacklaunch.diary.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.DiaryViewModel
import com.blacklaunch.diary.R
import com.blacklaunch.diary.audio.Player
import com.blacklaunch.diary.search.Found
import com.blacklaunch.diary.search.Period
import com.blacklaunch.diary.search.SearchHit
import com.blacklaunch.diary.search.SearchMode
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.Glyph
import com.blacklaunch.diary.ui.GlyphIcon
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.SheetTextField
import com.blacklaunch.diary.ui.WindowHint
import com.blacklaunch.diary.ui.pencilFrame
import com.blacklaunch.diary.ui.pencilTick
import com.blacklaunch.diary.ui.taperedPath
import com.blacklaunch.diary.ui.theme.Oled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Стан пошуку, спільний для всіх його сторінок: запит, режим, нова добірка «найкраще», показ фото, один програвач. */
class SearchUi {
    var query by mutableStateOf("")
    var mode by mutableStateOf(SearchMode.Notes)
    var shuffle by mutableIntStateOf(0)
    /** Показ фото: з якої знахідки і чи самі гортаються. */
    var show by mutableStateOf<Pair<Int, Boolean>?>(null)
    val player = Player()
    var playingDay by mutableLongStateOf(-1L)
}

/**
 * Скільки знахідок уміщається на кожній сторінці пошуку (у щоденнику нічого не прокручується — результати йдуть
 * сторінками, як і все інше). Розміри — ті самі, що в [PaperPage] і в рядках нижче; [w] / [h] — сторінка в dp.
 */
fun searchSlices(found: Found, ui: SearchUi, w: Float, h: Float, fontScale: Float): List<IntRange> {
    val n = found.hits.size
    if (n == 0 || w <= 0f || h <= 0f || (ui.mode == SearchMode.Notes && ui.query.isBlank())) return listOf(IntRange.EMPTY)
    val u = w / PageMetrics.WIDTH
    val fs = fontScale
    // Рядок тексту — ~1.6 розміру шрифту (виміряно на телефоні). Заголовок сторінки — в одиницях сторінки (від шрифту
    // системи не залежить), поля, місце під підпис «1–8 з 90» унизу.
    val line = 1.6f * fs
    val frame = 6.2f * u * (1.25f + 0.8f) + 22f + 10f + 22f + 30f
    val content = w - PageMetrics.MARGIN_LEFT * u - 18f
    val firstExtra = 17f * line + 11f + 64f + 10f + when (ui.mode) {
        SearchMode.Photo -> 30f * line + 20f + (if (found.best && found.period != Period.Week) 13f * line + 12f else 0f) + 10f
        else -> 0f
    }
    val (item, perRow) = when (ui.mode) {
        SearchMode.Notes -> (13f + 2 * 16f) * line + 28f to 1
        SearchMode.Photo -> {
            val cell = (content - (PHOTO_COLS - 1) * 8f) / PHOTO_COLS
            cell + 11f * line + 2f + 10f to PHOTO_COLS
        }
        SearchMode.Audio -> 54f to 1
    }
    fun fit(space: Float) = (((space + 8f) / item).toInt().coerceAtLeast(1)) * perRow
    val out = ArrayList<IntRange>()
    var from = 0
    while (from < n) {
        val cap = fit(h - frame - if (out.isEmpty()) firstExtra else 0f)
        val to = (from + cap).coerceAtMost(n)
        out += from until to
        from = to
    }
    return out
}

internal const val PHOTO_COLS = 4

/**
 * Сторінка пошуку. Перша — три режими, підписані олівцем («Нотатки» — знайдені дні повністю; «Фото» — лише фото:
 * мініатюри з підписом, перегляд гортанням і «Показ»; «Аудіо» — лише записи, по одному в рядок), поле й перші знахідки;
 * далі — лише знахідки, сторінка за сторінкою. Поле одне: слова, дати, числа, місяці, а ще «тиждень» / «місяць» /
 * «рік» і «найкраще» (добірка фото навмання).
 */
@Composable
fun ColumnScope.SearchPage(vm: DiaryViewModel, ui: SearchUi, found: Found, slice: IntRange, first: Boolean, active: Boolean, onCalendar: () -> Unit, onRecorder: (Long) -> Unit = {}, onOpen: (SearchHit) -> Unit) {
    val haptics = LocalHaptics.current
    if (first) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(active) { if (active && ui.mode == SearchMode.Notes && ui.query.isEmpty()) runCatching { focus.requestFocus() } }
        ModeTabs(ui.mode) { if (it != ui.mode) { haptics.tap(); ui.mode = it } }
        // Поле, а праворуч — календарик: місяці сторінками, гортаються.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SheetTextField(
            ui.query,
            { ui.query = it.take(80) },
            stringResource(
                when (ui.mode) {
                    SearchMode.Notes -> R.string.search_hint
                    SearchMode.Photo -> R.string.search_hint_photo
                    SearchMode.Audio -> R.string.search_hint_audio
                },
            ),
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            focusRequester = focus,
        )
        Box(Modifier.padding(start = 6.dp).size(44.dp).clip(CircleShape).clickable { haptics.tap(); onCalendar() }, contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.Calendar, stringResource(R.string.calendar), tint = Oled.TextSecondary, size = 24.dp)
        }
        }
        Spacer(Modifier.height(10.dp))
    }
    val part = if (slice.isEmpty()) emptyList() else found.hits.subList(slice.first, slice.last + 1)
    when (ui.mode) {
        SearchMode.Notes -> NotesResults(found, part, ui.query, first, onOpen)
        SearchMode.Photo -> PhotoResults(vm, found, part, slice.first, first, ui.query, onShow = { i, auto -> ui.show = i to auto }, onShuffle = { haptics.tap(); ui.shuffle++ })
        SearchMode.Audio -> AudioResults(vm, ui, part, first, onRecorder)
    }
}

/** Підпис унизу сторінки пошуку: «9–16 з 90» (коли знахідок більше, ніж на одну сторінку). */
@Composable
fun searchFooter(found: Found, slices: List<IntRange>, n: Int): String? {
    if (slices.size < 2) return null
    val s = slices.getOrNull(n) ?: return null
    return stringResource(R.string.search_range, s.first + 1, s.last + 1, found.hits.size)
}

/** Показ фото — поверх усього (вікно), з будь-якої сторінки пошуку. */
@Composable
fun SearchShow(vm: DiaryViewModel, ui: SearchUi, found: Found) {
    val (start, auto) = ui.show ?: return
    // Показ — від найдавнішого до найновішого, як гортаєш альбом.
    val days = remember(found) { found.hits.map { it.day }.sortedBy { it } }
    if (days.isEmpty()) { ui.show = null; return }
    val from = days.indexOf(found.hits.getOrNull(start)?.day ?: days.first()).coerceAtLeast(0)
    PhotoShow(vm, days, if (auto) 0 else from, auto) { ui.show = null }
}


/** Три режими пошуку: підписи олівцем, вибраний — яскравий і підкреслений. */
@Composable
private fun ModeTabs(mode: SearchMode, onMode: (SearchMode) -> Unit) {
    val glass = AppStyle.glass
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            SearchMode.Notes to R.string.search_mode_notes,
            SearchMode.Photo to R.string.search_mode_photo,
            SearchMode.Audio to R.string.search_mode_audio,
        ).forEach { (m, label) ->
            val on = m == mode
            Text(
                stringResource(label),
                color = if (on) Oled.TextPrimary else Oled.TextSecondary.copy(alpha = 0.7f),
                fontSize = 17.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMode(m) }
                    .drawBehind {
                        if (!on) return@drawBehind
                        val y = size.height + 1.dp.toPx()
                        val ink = if (glass) Oled.TextPrimary.copy(alpha = 0.85f) else SheetInk.copy(alpha = 0.9f)
                        drawPath(taperedPath(listOf(Offset(0f, y), Offset(size.width * 0.5f, y + 1.2f), Offset(size.width, y - 0.6f)), 1.8.dp.toPx()), ink)
                    }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun NotesResults(found: Found, part: List<SearchHit>, query: String, first: Boolean, onOpen: (SearchHit) -> Unit) {
    val haptics = LocalHaptics.current
    val locale = Locale.getDefault()
    if (query.isBlank()) {
        WindowHint(stringResource(R.string.search_explain))
        return
    }
    if (first && found.hits.isEmpty()) WindowHint(stringResource(R.string.search_nothing))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 4.dp)) {
        part.forEach { h ->
            key(h.day * 31 + h.start) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .pencilFrame(16.dp, seed = (h.day % 50).toInt(), alpha = 0.5f)
                        .clickable { haptics.tap(); onOpen(h) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(DateTimeFormatter.ofPattern("dd.MM.yyyy", locale).format(LocalDate.ofEpochDay(h.day)), color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1)
                    // Завжди два рядки — сторінка знає, скільки знахідок на ній уміститься.
                    Text(
                        buildAnnotatedString {
                            val a = h.snippetFrom.coerceIn(0, h.snippet.length)
                            val b = h.snippetTo.coerceIn(a, h.snippet.length)
                            append(h.snippet.substring(0, a))
                            withStyle(SpanStyle(color = Oled.TextPrimary, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) { append(h.snippet.substring(a, b)) }
                            append(h.snippet.substring(b))
                        },
                        color = Oled.TextPrimary.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Фото: на першій сторінці — «Показ» (і «Вибрати інші» для добірки), далі — сітка маленьких мініатюр із підписом. */
@Composable
private fun PhotoResults(vm: DiaryViewModel, found: Found, part: List<SearchHit>, offset: Int, first: Boolean, query: String, onShow: (Int, Boolean) -> Unit, onShuffle: () -> Unit) {
    val haptics = LocalHaptics.current
    val glass = AppStyle.glass
    if (found.hits.isEmpty()) {
        if (query.isBlank()) { WindowHint(stringResource(R.string.search_photo_explain)); return }
        WindowHint(stringResource(if (found.best) R.string.search_best_nothing else R.string.search_photo_nothing))
        return
    }
    if (first) {
        // «Показ» — окрема, особлива річ: плівка з підписом, що саме покажуть.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .pencilFrame(14.dp, seed = 77, alpha = 0.9f, ink = if (glass) Oled.TextPrimary else SheetInk)
                .clickable { haptics.tap(); onShow(0, true) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilmMark(Modifier.size(28.dp, 22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.search_show), color = Oled.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    when {
                        found.best && found.period == Period.Year -> stringResource(R.string.search_best_year)
                        found.best && found.period == Period.Week -> stringResource(R.string.search_best_week)
                        found.best -> stringResource(R.string.search_best_month)
                        else -> androidx.compose.ui.res.pluralStringResource(R.plurals.search_photos, found.hits.size, found.hits.size)
                    },
                    color = Oled.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GlyphIcon(Glyph.Play, stringResource(R.string.search_show), tint = Oled.TextPrimary, size = 22.dp)
        }
        if (found.best && found.period != Period.Week) {
            Text(
                stringResource(R.string.search_best_again),
                color = Oled.TextSecondary,
                fontSize = 13.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { onShuffle() }.padding(4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 4.dp)) {
        part.chunked(PHOTO_COLS).forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEachIndexed { i, h ->
                    key(h.day) {
                        Thumb(vm, h.day, Modifier.weight(1f)) { haptics.tap(); onShow(offset + row * PHOTO_COLS + i, false) }
                    }
                }
                repeat(PHOTO_COLS - items.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}


/** Значок плівки: рамка з дірочками згори й знизу. */
@Composable
private fun FilmMark(modifier: Modifier) {
    val glass = AppStyle.glass
    Canvas(modifier) {
        val ink = if (glass) Oled.TextPrimary else SheetInk
        val s = 1.4.dp.toPx()
        drawRoundRect(ink, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(s))
        val hole = size.width / 9f
        for (i in 0 until 4) {
            val x = size.width * (0.17f + i * 0.22f)
            drawRect(ink, Offset(x - hole / 2, 2.5f * s), androidx.compose.ui.geometry.Size(hole, hole * 0.8f))
            drawRect(ink, Offset(x - hole / 2, size.height - 2.5f * s - hole * 0.8f), androidx.compose.ui.geometry.Size(hole, hole * 0.8f))
        }
    }
}

/** Маленька мініатюра (під фільтром теми) і підпис під нею — підпис фото, а як його нема — дата. */
@Composable
private fun Thumb(vm: DiaryViewModel, day: Long, modifier: Modifier, onClick: () -> Unit) {
    val glass = AppStyle.glass
    val locale = Locale.getDefault()
    var image by remember(day, glass) { mutableStateOf(vm.cachedPhoto(day, glass)) }
    LaunchedEffect(day, glass) { if (image == null) image = vm.photo(day, glass) }
    val caption = vm.day(day).caption
    Column(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))) {
            StyledPhoto(image, glass, Modifier.fillMaxSize(), ContentScale.Crop, seed = day, dry = DiaryViewModel.isDry(day))
        }
        Text(
            caption.ifBlank { DateTimeFormatter.ofPattern("dd.MM.yy", locale).format(LocalDate.ofEpochDay(day)) },
            color = Oled.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}


/**
 * Фото на весь екран — так само, як збільшене на сторінці (той самий фільтр, «Аркуш» — на папері, «Скло» — на чорному).
 * Свайп — гортати знайдені. [auto] — показ: фото самі змінюються з плавним наближенням; щойно гортнеш сам — показ стає.
 */
@Composable
private fun PhotoShow(vm: DiaryViewModel, days: List<Long>, start: Int, auto: Boolean, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    val glass = AppStyle.glass
    val locale = Locale.getDefault()
    val scope = rememberCoroutineScope()
    val n = days.size
    val first = start.coerceIn(0, (n - 1).coerceAtLeast(0))
    // Плівка: кадри в рамочках, перфорація згори й знизу. [pos] — де стоїть камера (у кадрах; дробове — між кадрами),
    // [zoom] — наближення (1 — кадр на весь екран, менше — видно шматок плівки).
    val images = remember { androidx.compose.runtime.mutableStateMapOf<Long, ImageBitmap>() }
    val pos = remember { Animatable(first - LEADER) }
    val zoom = remember { Animatable(FAR) }
    val endA = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }
    val cur = pos.value.let { kotlin.math.round(it).toInt() }.coerceIn(0, (n - 1).coerceAtLeast(0))
    LaunchedEffect(cur, glass) {
        for (k in listOf(cur, cur + 1, cur - 1, cur + 2, cur + 3)) {
            val d = days.getOrNull(k) ?: continue
            if (d in images) continue
            (vm.cachedPhoto(d, glass) ?: vm.photo(d, glass))?.let { images[d] = it }
        }
    }
    // Відкриття: плівка заїжджає здалеку й наближається разом із першим кадром.
    suspend fun open(to: Int) {
        kotlinx.coroutines.coroutineScope {
            launch { zoom.animateTo(1f, tween(OPEN_MS, easing = FastOutSlowInEasing)) }
            pos.animateTo(to.toFloat(), tween(OPEN_MS, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(Unit) {
        open(first)
        opened = true
        playing = auto
    }
    LaunchedEffect(playing, opened) {
        if (!opened) return@LaunchedEffect
        if (!playing) {
            if (!ended) pos.animateTo(kotlin.math.round(pos.value).coerceIn(0f, (n - 1).toFloat()), tween(500, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        if (ended) {
            // Спочатку: плівку перемотано, вона знову заїжджає.
            ended = false
            launch { endA.animateTo(0f, tween(400)) }
            pos.snapTo(-LEADER)
            zoom.snapTo(FAR)
            open(0)
        }
        while (true) {
            val at = kotlin.math.round(pos.value).coerceIn(0f, (n - 1).toFloat())
            if (kotlin.math.abs(pos.value - at) > 0.01f) pos.animateTo(at, tween(600, easing = FastOutSlowInEasing))
            delay(HOLD_MS.toLong())
            if (at.toInt() >= n - 1) {
                // Кінець плівки: камера відступає, видно хвіст плівки й напис «Кінець».
                kotlinx.coroutines.coroutineScope {
                    launch { zoom.animateTo(FAR, tween(END_MS, easing = FastOutSlowInEasing)) }
                    launch { delay(END_MS / 2L); endA.animateTo(1f, tween(END_MS / 2)) }
                    pos.animateTo(n - 1 + LEADER * 0.8f, tween(END_MS, easing = FastOutSlowInEasing))
                }
                ended = true
                playing = false
                break
            }
            pos.animateTo(at + 1f, tween(GLIDE_MS, easing = FastOutSlowInEasing))
        }
    }
    val bg = if (glass) Color.Black else PaperTexture.PAGE_BASE
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    com.blacklaunch.diary.ui.SmoothDialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(
                    if (glass) Modifier
                    else Modifier.drawBehind {
                        PaperTexture.ensure()
                        PaperTexture.page?.let { drawImage(it, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt() + 1, size.height.toInt() + 1)) }
                            ?: drawRect(PaperTexture.PAGE_BASE)
                    }.paperRelief(glass),
                ),
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val wPx = with(density) { maxWidth.toPx() }
                    val hPx = with(density) { maxHeight.toPx() }
                    // Розміри плівки: висота кадру однакова, ширина — за пропорцією фото (як кадр на плівці).
                    val fh = minOf(wPx / 1.75f, hPx * 0.45f)
                    val rb = fh * 0.22f
                    val gap = fh * 0.08f
                    val std = fh * 1.333f
                    fun widthOf(k: Int): Float = images[days[k]]?.let { fh * (it.width.toFloat() / it.height.coerceAtLeast(1)).coerceIn(0.62f, 1.5f) } ?: std
                    val lefts = FloatArray(n)
                    val widths = FloatArray(n)
                    var x = 0f
                    for (k in 0 until n) { widths[k] = widthOf(k); lefts[k] = x; x += widths[k] + gap }
                    val stripEnd = x - gap
                    fun centerOf(k: Int) = lefts[k] + widths[k] / 2
                    fun camAt(p: Float): Float = when {
                        n == 0 -> 0f
                        p <= 0f -> centerOf(0) + p * (std + gap)
                        p >= n - 1 -> centerOf(n - 1) + (p - (n - 1)) * (std + gap)
                        else -> {
                            val a = kotlin.math.floor(p).toInt()
                            val f = p - a
                            centerOf(a) + (centerOf(a + 1) - centerOf(a)) * f
                        }
                    }
                    val stepPx = std + gap
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(n) {
                                detectTapGestures {
                                    haptics.tap()
                                    if (opened) playing = !playing
                                }
                            }
                            .pointerInput(n) {
                                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        tracker.resetTracking()
                                        playing = false
                                        if (ended) {
                                            ended = false
                                            scope.launch { endA.animateTo(0f, tween(300)) }
                                            scope.launch { zoom.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                                        }
                                    },
                                    onDragEnd = {
                                        val v = tracker.calculateVelocity().x
                                        val target = kotlin.math.round(pos.value - v * 0.18f / (stepPx * zoom.value)).coerceIn(0f, (n - 1).toFloat())
                                        scope.launch {
                                            if (zoom.value < 1f) launch { zoom.animateTo(1f, tween(600)) }
                                            pos.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 120f))
                                        }
                                    },
                                ) { change, dx ->
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    change.consume()
                                    scope.launch { pos.snapTo((pos.value - dx / (stepPx * zoom.value)).coerceIn(-0.4f, n - 1 + 0.4f)) }
                                }
                            },
                    ) {
                        if (n == 0) return@Canvas
                        val z = zoom.value
                        val p = pos.value
                        val camX = camAt(p)
                        val sh = fh + 2 * rb
                        val top = size.height / 2 - sh / 2
                        val leader = LEADER * stepPx
                        val left = lefts[0] - leader
                        val right = stripEnd + leader
                        val viewL = camX - size.width / 2 / z - 10f
                        val viewR = camX + size.width / 2 / z + 10f
                        val film = if (glass) Color(0xFF1B1B1B) else Color(0xFF0B0A08)
                        val line = 1.2.dp.toPx() / z
                        withTransform({
                            translate(size.width / 2 - camX, 0f)
                            scale(z, z, Offset(camX, size.height / 2))
                        }) {
                            // Сама плівка: тіло, на кінцях — косо обрізаний хвіст.
                            val body = androidx.compose.ui.graphics.Path().apply {
                                moveTo(left + rb, top); lineTo(right - rb, top); lineTo(right, top + rb * 1.4f)
                                lineTo(right, top + sh); lineTo(left, top + sh); lineTo(left, top + rb * 1.4f); close()
                            }
                            drawPath(body, film)
                            // Ледь світліші краї (як на справжній плівці — прозора основа).
                            drawPath(body, ink.copy(alpha = if (glass) 0.18f else 0.55f), style = Stroke(line))
                            // Перфорація: прямокутні дірочки з заокругленими кутами, крізь них — тло.
                            val hw = rb * 0.36f
                            val hh = rb * 0.4f
                            val pitch = rb * 0.82f
                            var hx = left + rb * 1.6f
                            if (hx < viewL) hx += kotlin.math.floor((viewL - hx) / pitch) * pitch
                            while (hx < right - rb * 1.2f && hx < viewR) {
                                for (hy in floatArrayOf(top + rb * 0.12f, top + sh - rb * 0.12f - hh)) {
                                    drawRoundRect(bg, Offset(hx, hy), Size(hw, hh), CornerRadius(hw * 0.25f))
                                    if (!glass) drawRoundRect(ink.copy(alpha = 0.45f), Offset(hx, hy), Size(hw, hh), CornerRadius(hw * 0.25f), style = Stroke(line))
                                }
                                hx += pitch
                            }
                            // Кадри.
                            for (k in 0 until n) {
                                val fl = lefts[k]
                                val fw = widths[k]
                                if (fl + fw < viewL || fl > viewR) continue
                                val ft = top + rb
                                drawRect(Color(0xFF050505), Offset(fl, ft), Size(fw, fh))
                                images[days[k]]?.let { img ->
                                    val a = img.width.toFloat() / img.height.coerceAtLeast(1)
                                    var dw = fw
                                    var dh = fw / a
                                    if (dh > fh) { dh = fh; dw = fh * a }
                                    drawImage(
                                        img,
                                        dstOffset = androidx.compose.ui.unit.IntOffset((fl + (fw - dw) / 2).toInt(), (ft + (fh - dh) / 2).toInt()),
                                        dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()),
                                        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                                    )
                                }
                                // Рамочка кадру й притемнення сусідніх (поточний — яскравий).
                                val dim = (kotlin.math.abs(k - p)).coerceIn(0f, 1f) * 0.5f
                                if (dim > 0.01f) drawRect(Color.Black.copy(alpha = dim), Offset(fl, ft), Size(fw, fh))
                                drawRect(ink.copy(alpha = 0.55f), Offset(fl, ft), Size(fw, fh), style = Stroke(line))
                                // Дата на краю плівки, як друк на справжній плівці.
                                val label = measurer.measure(
                                    DateTimeFormatter.ofPattern("dd.MM.yy", locale).format(LocalDate.ofEpochDay(days[k])) + "  ${k + 1}",
                                    androidx.compose.ui.text.TextStyle(color = ink.copy(alpha = 0.45f), fontSize = (rb * 0.22f / density.fontScale / density.density).sp),
                                )
                                drawText(label, topLeft = Offset(fl + fw * 0.06f, top + sh - rb + (rb * 0.88f - hh) / 2 - label.size.height / 2f))
                            }
                        }
                    }
                    // Під плівкою — дата й підпис поточного кадру; наприкінці — «Кінець».
                    val below = with(density) { (hPx / 2 + (fh + 2 * rb) * zoom.value / 2).toDp() + 14.dp }
                    Column(Modifier.fillMaxWidth().padding(top = below, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val near = 1f - (kotlin.math.abs(pos.value - cur) * 2f).coerceIn(0f, 1f)
                        val a = near * (1f - endA.value) * ((zoom.value - FAR) / (1f - FAR)).coerceIn(0f, 1f)
                        if (n > 0) {
                            Text(
                                DateTimeFormatter.ofPattern("dd.MM.yyyy", locale).format(LocalDate.ofEpochDay(days[cur])),
                                color = ink.copy(alpha = 0.7f * a),
                                fontSize = 14.sp,
                            )
                            val cap = vm.day(days[cur]).caption
                            if (cap.isNotBlank()) Text(cap, color = ink.copy(alpha = a), fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    if (endA.value > 0f) {
                        Text(
                            stringResource(R.string.show_end),
                            color = ink.copy(alpha = endA.value),
                            fontSize = 20.sp,
                            modifier = Modifier.align(Alignment.Center).padding(top = with(density) { (fh * FAR + 2 * rb * FAR).toDp() } + 80.dp),
                        )
                    }
                }
                Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable {
                            haptics.tap()
                            if (opened) playing = !playing
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(if (playing) Glyph.Pause else Glyph.Play, stringResource(R.string.search_show), tint = if (glass) Oled.TextSecondary else SheetInk, size = 24.dp)
                    }
                    Box(Modifier.size(56.dp).clip(CircleShape).clickable { haptics.tap(); onDismiss() }, contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.Close, stringResource(R.string.close), tint = if (glass) Oled.TextPrimary else SheetInk)
                    }
                    Text("${cur + 1} / $n", color = if (glass) Oled.TextSecondary else SheetInk.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.width(44.dp))
                }
            }
        }
    }
}

/** Скільки «кадрів» порожньої плівки перед першим кадром і після останнього. */
private const val LEADER = 1.7f
/** Наближення, з якого плівка заїжджає й до якого відступає наприкінці. */
private const val FAR = 0.5f
private const val OPEN_MS = 2000
private const val HOLD_MS = 2800
private const val GLIDE_MS = 1800
private const val END_MS = 2400

/** Аудіо: лише записи, по одному в рядок — касетка (відкрити магнітофон), дата, смужка, кнопка. */
@Composable
private fun AudioResults(vm: DiaryViewModel, ui: SearchUi, part: List<SearchHit>, first: Boolean, onRecorder: (Long) -> Unit) {
    if (first && part.isEmpty()) {
        if (ui.query.isBlank()) WindowHint(stringResource(R.string.search_audio_explain))
        else WindowHint(stringResource(R.string.search_audio_nothing))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 4.dp)) {
        part.forEach { h ->
            key(h.day) { AudioRow(vm, h.day, ui, onRecorder) }
        }
    }
}

/**
 * Рядок запису в пошуку: увесь запис дня — сторона A, а за нею B (одна смужка). Касетка ліворуч — відкрити магнітофон
 * (там зручніше довгі записи: перемотка по відрізках, сторони; висохлий день — лише слухати).
 */
@Composable
private fun AudioRow(vm: DiaryViewModel, epochDay: Long, ui: SearchUi, onRecorder: (Long) -> Unit) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val glass = AppStyle.glass
    val locale = Locale.getDefault()
    val day = vm.day(epochDay)
    val player = ui.player
    val mine = ui.playingDay == epochDay
    val msA = if (day.audio) day.audioMs else 0L
    val msB = if (day.audioB) day.audioMsB else 0L
    val total = (msA + msB).coerceAtLeast(1L)
    val wave = remember(day.wave, day.waveB, msA, msB) {
        DiaryViewModel.stitchWave(listOfNotNull(if (day.audio) day.wave to msA else null, if (day.audioB) day.waveB to msB else null), 60)
    }
    var playing by remember { mutableStateOf(false) }
    var sideNow by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(mine) { if (!mine) { playing = false; progress = 0f } }
    LaunchedEffect(playing) {
        while (playing) {
            val base = if (sideNow == 1) msA else 0L
            progress = ((base + player.position) / total.toFloat()).coerceIn(0f, 1f)
            delay(50)
        }
    }
    fun playFrom(side: Int, fromMs: Int) {
        scope.launch {
            val b = vm.audio(epochDay, side) ?: return@launch
            player.stop()
            ui.playingDay = epochDay
            sideNow = side
            playing = player.play(b, fromMs) {
                // Скінчилась сторона A — далі B, якщо є.
                if (side == 0 && day.audioB) playFrom(1, 0) else { playing = false; progress = 0f }
            }
        }
    }
    fun toggle() {
        haptics.tap()
        if (playing) { player.pause(); playing = false; return }
        if (mine && player.duration > 0 && progress in 0.01f..0.99f) { player.resume(); playing = true; return }
        playFrom(if (day.audio) 0 else 1, 0)
    }
    fun seekTo(f: Float) {
        val t = (f * total).toLong()
        val side = if (t < msA || !day.audioB) 0 else 1
        val local = (if (side == 0) t else t - msA).toInt()
        progress = f
        if (mine && sideNow == side && player.duration > 0) player.seek(local) else if (mine) playFrom(side, local)
    }
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        // Касетка — у магнітофон.
        Box(
            Modifier.size(40.dp, 44.dp).clip(RoundedCornerShape(8.dp)).clickable { haptics.tap(); player.stop(); playing = false; onRecorder(epochDay) },
            contentAlignment = Alignment.Center,
        ) {
            val ink = if (glass) Oled.TextPrimary else SheetInk
            Canvas(Modifier.size(26.dp, 17.dp)) {
                val w = size.width
                val h = size.height
                val line = ink.copy(alpha = 0.75f)
                drawRoundRect(line, cornerRadius = CornerRadius(w * 0.08f), style = Stroke(1.1.dp.toPx()))
                drawRoundRect(line.copy(alpha = 0.45f), topLeft = Offset(w * 0.1f, h * 0.14f), size = Size(w * 0.8f, h * 0.5f), cornerRadius = CornerRadius(w * 0.04f), style = Stroke(0.8.dp.toPx()))
                drawCircle(line, h * 0.13f, Offset(w * 0.3f, h * 0.4f), style = Stroke(1.dp.toPx()))
                drawCircle(line, h * 0.13f, Offset(w * 0.7f, h * 0.4f), style = Stroke(1.dp.toPx()))
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.22f, h); lineTo(w * 0.28f, h * 0.76f); lineTo(w * 0.72f, h * 0.76f); lineTo(w * 0.78f, h) }, line.copy(alpha = 0.6f), style = Stroke(0.9.dp.toPx()))
            }
        }
        Text(
            // Дата цифрами — завжди однакової ширини (назви місяців різної довжини й різними мовами).
            DateTimeFormatter.ofPattern("dd.MM.yy", locale).format(LocalDate.ofEpochDay(epochDay)),
            color = Oled.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.width(60.dp),
        )
        Row(Modifier.weight(1f).fillMaxHeight().pencilFrame(22.dp, seed = (epochDay % 40).toInt() + 23).padding(start = 12.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(epochDay, mine) {
                        detectTapGestures { p -> if (mine) seekTo((p.x / size.width).coerceIn(0f, 1f)) }
                    },
            ) {
                val bars = wave.ifEmpty { List(40) { 0.08f } }
                val n = bars.size.coerceAtLeast(1)
                val step = size.width / n
                val mid = size.height / 2
                bars.forEachIndexed { i, v ->
                    val hh = (0.12f + v * 0.88f) * size.height * 0.42f
                    val x = i * step + step / 2
                    val color = if ((i + 0.5f) / n <= progress) Oled.TextPrimary else Oled.TextSecondary.copy(alpha = 0.45f)
                    if (glass) drawLine(color, Offset(x, mid - hh), Offset(x, mid + hh), (step * 0.55f).coerceIn(1f, 4.dp.toPx()), cap = StrokeCap.Round)
                    else pencilTick(Offset(x, mid - hh), Offset(x + 0.6f, mid + hh), (step * 0.6f).coerceIn(1f, 3.dp.toPx()), color, i)
                }
            }
            Spacer(Modifier.width(8.dp))
            // Час — однакові за шириною цифри й стала ширина: смужка поруч не смикається, поки цифри біжать.
            Text(
                clock(if (playing || progress > 0f) (progress * total).toLong() else total),
                color = Oled.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.width(38.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(44.dp)
                .then(
                    if (glass) Modifier.clip(CircleShape).background(Oled.Knob).border(1.dp, Oled.Glint, CircleShape)
                    else Modifier.pencilFrame(22.dp, seed = 29, ink = SheetInk).clip(CircleShape),
                )
                .clickable { toggle() },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(if (playing) Glyph.Pause else Glyph.Play, stringResource(R.string.play), tint = Oled.TextPrimary, size = 19.dp)
        }
    }
}
