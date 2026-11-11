package com.blacklaunch.diary.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
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

/**
 * Пошук — сторінка щоденника. Угорі три режими, підписані олівцем: «Нотатки» (знайдені дні повністю), «Фото» (лише фото:
 * сітка, перегляд гортанням і «Показ» — слайд-шоу), «Аудіо» (лише записи, слухаються просто в списку). Поле одне: слова,
 * дати, числа, місяці, а ще «тиждень» / «місяць» / «рік» і «найкраще» (добірка фото навмання).
 */
@Composable
fun ColumnScope.SearchPage(vm: DiaryViewModel, active: Boolean, onOpen: (SearchHit) -> Unit) {
    val haptics = LocalHaptics.current
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SearchMode.Notes) }
    // Щоб «найкраще» тягнуло нову добірку щоразу, коли просиш знову (а не лише коли змінився текст).
    var shuffle by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(active) { if (active && mode == SearchMode.Notes) runCatching { focus.requestFocus() } }
    val found = remember(query, mode, shuffle) { vm.find(query, mode) }
    var show by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    ModeTabs(mode) { if (it != mode) { haptics.tap(); mode = it } }
    SheetTextField(
        query,
        { query = it.take(80) },
        stringResource(
            when (mode) {
                SearchMode.Notes -> R.string.search_hint
                SearchMode.Photo -> R.string.search_hint_photo
                SearchMode.Audio -> R.string.search_hint_audio
            },
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 4.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        focusRequester = focus,
    )
    Spacer(Modifier.height(10.dp))
    when (mode) {
        SearchMode.Notes -> NotesResults(found, query, onOpen)
        SearchMode.Photo -> PhotoResults(vm, found, onShow = { i, auto -> show = i to auto }, onShuffle = { haptics.tap(); shuffle++ })
        SearchMode.Audio -> AudioResults(vm, found)
    }
    show?.let { (start, auto) ->
        // Показ — від найдавнішого до найновішого, як гортаєш альбом.
        val days = remember(found) { found.hits.map { it.day }.sortedBy { it } }
        val from = days.indexOf(found.hits.getOrNull(start)?.day ?: days.first()).coerceAtLeast(0)
        PhotoShow(vm, days, if (auto) 0 else from, auto) { show = null }
    }
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
private fun NotesResults(found: Found, query: String, onOpen: (SearchHit) -> Unit) {
    val haptics = LocalHaptics.current
    val locale = Locale.getDefault()
    if (query.isBlank()) {
        WindowHint(stringResource(R.string.search_explain))
        return
    }
    if (found.hits.isEmpty()) WindowHint(stringResource(R.string.search_nothing))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 4.dp)) {
        found.hits.take(60).forEach { h ->
            key(h.day * 31 + h.start) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .pencilFrame(16.dp, seed = (h.day % 50).toInt(), alpha = 0.5f)
                        .clickable { haptics.tap(); onOpen(h) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", locale).format(LocalDate.ofEpochDay(h.day)), color = Oled.TextSecondary, fontSize = 13.sp)
                    if (h.snippet.isNotBlank()) {
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
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Фото: підпис добірки (якщо «найкраще»), «Показ», сітка мініатюр під фільтром теми. */
@Composable
private fun PhotoResults(vm: DiaryViewModel, found: Found, onShow: (Int, Boolean) -> Unit, onShuffle: () -> Unit) {
    val haptics = LocalHaptics.current
    val glass = AppStyle.glass
    if (found.hits.isEmpty()) {
        WindowHint(stringResource(if (found.best) R.string.search_best_nothing else R.string.search_photo_nothing))
        WindowHint(stringResource(R.string.search_photo_explain))
        return
    }
    // «Показ» — окрема, особлива річ: вклеєна плівка з підписом, що саме покажуть.
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
            Text(stringResource(R.string.search_show), color = Oled.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    found.best && found.period == Period.Year -> stringResource(R.string.search_best_year)
                    found.best && found.period == Period.Week -> stringResource(R.string.search_best_week)
                    found.best -> stringResource(R.string.search_best_month)
                    else -> androidx.compose.ui.res.pluralStringResource(R.plurals.search_photos, found.hits.size, found.hits.size)
                },
                color = Oled.TextSecondary,
                fontSize = 13.sp,
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
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).clickable { onShuffle() }.padding(4.dp),
        )
    }
    Spacer(Modifier.height(10.dp))
    val cols = 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 4.dp)) {
        found.hits.take(90).chunked(cols).forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEachIndexed { i, h ->
                    key(h.day) {
                        Thumb(vm, h.day, Modifier.weight(1f).aspectRatio(1f)) { haptics.tap(); onShow(row * cols + i, false) }
                    }
                }
                repeat(cols - items.size) { Spacer(Modifier.weight(1f)) }
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

@Composable
private fun Thumb(vm: DiaryViewModel, day: Long, modifier: Modifier, onClick: () -> Unit) {
    val glass = AppStyle.glass
    var image by remember(day, glass) { mutableStateOf(vm.cachedPhoto(day, glass)) }
    LaunchedEffect(day, glass) { if (image == null) image = vm.photo(day, glass) }
    Box(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        StyledPhoto(image, glass, Modifier.fillMaxSize(), ContentScale.Crop, seed = day, dry = DiaryViewModel.isDry(day))
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
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (days.size - 1).coerceAtLeast(0))) { days.size }
    var playing by remember { mutableStateOf(auto) }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) playing = false }
    // Повільне наближення поточного фото під час показу.
    val zoom = remember { Animatable(1f) }
    LaunchedEffect(pager.currentPage, playing) {
        zoom.snapTo(1f)
        if (!playing) return@LaunchedEffect
        launch { zoom.animateTo(1.08f, tween(SHOW_MS + 900, easing = LinearEasing)) }
        delay(SHOW_MS.toLong())
        if (pager.currentPage < days.size - 1) pager.animateScrollToPage(pager.currentPage + 1, animationSpec = tween(900))
        else playing = false
    }
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
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { i ->
                    val day = days[i]
                    var image by remember(day, glass) { mutableStateOf<ImageBitmap?>(vm.cachedPhoto(day, glass)) }
                    LaunchedEffect(day, glass) { if (image == null) image = vm.photo(day, glass) }
                    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        image?.let {
                            StyledPhoto(
                                it, glass,
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(it.width.toFloat() / it.height.coerceAtLeast(1))
                                    .graphicsLayer {
                                        val z = if (i == pager.currentPage) zoom.value else 1f
                                        scaleX = z; scaleY = z
                                        // Сусідні фото тануть, поки гортається: перехід — плавний, не різкий зсув.
                                        val off = kotlin.math.abs((pager.currentPage - i) + pager.currentPageOffsetFraction)
                                        alpha = (1f - off * 0.7f).coerceIn(0f, 1f)
                                    },
                                ContentScale.Fit,
                                caption = vm.day(day).caption,
                                seed = day,
                                dry = DiaryViewModel.isDry(day),
                            )
                        }
                        Text(
                            DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(LocalDate.ofEpochDay(day)),
                            color = if (glass) Oled.TextSecondary else SheetInk.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }
                Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { haptics.tap(); playing = !playing }, contentAlignment = Alignment.Center) {
                        GlyphIcon(if (playing) Glyph.Pause else Glyph.Play, stringResource(R.string.search_show), tint = if (glass) Oled.TextSecondary else SheetInk, size = 24.dp)
                    }
                    Box(Modifier.size(56.dp).clip(CircleShape).clickable { haptics.tap(); onDismiss() }, contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.Close, stringResource(R.string.close), tint = if (glass) Oled.TextPrimary else SheetInk)
                    }
                    Text("${pager.currentPage + 1} / ${days.size}", color = if (glass) Oled.TextSecondary else SheetInk.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.width(44.dp))
                }
            }
        }
    }
}

private const val SHOW_MS = 3200

/** Аудіо: лише записи — дата й смужка; слухаєш просто тут, одночасно грає один. */
@Composable
private fun AudioResults(vm: DiaryViewModel, found: Found) {
    if (found.hits.isEmpty()) {
        WindowHint(stringResource(R.string.search_audio_nothing))
        WindowHint(stringResource(R.string.search_audio_explain))
        return
    }
    val player = remember { Player() }
    var current by remember { mutableLongStateOf(-1L) }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 4.dp)) {
        found.hits.take(100).forEach { h ->
            key(h.day) {
                AudioRow(vm, h.day, player, current == h.day) { current = it }
            }
        }
    }
}

@Composable
private fun AudioRow(vm: DiaryViewModel, epochDay: Long, player: Player, mine: Boolean, onTake: (Long) -> Unit) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val glass = AppStyle.glass
    val locale = Locale.getDefault()
    val day = vm.day(epochDay)
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(mine) { if (!mine) { playing = false; progress = 0f } }
    LaunchedEffect(playing) {
        while (playing) {
            val d = player.duration
            if (d > 0) progress = player.position / d.toFloat()
            delay(50)
        }
    }
    fun toggle() {
        haptics.tap()
        if (playing) { player.pause(); playing = false; return }
        scope.launch {
            if (mine && player.duration > 0 && progress in 0.01f..0.99f) { player.resume(); playing = true; return@launch }
            val b = vm.audio(epochDay) ?: return@launch
            player.stop()
            onTake(epochDay)
            playing = player.play(b, 0) { playing = false; progress = 0f }
        }
    }
    Column {
        Text(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", locale).format(LocalDate.ofEpochDay(epochDay)), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).fillMaxHeight().pencilFrame(22.dp, seed = (epochDay % 40).toInt() + 23).padding(start = 12.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(epochDay, mine) {
                            detectTapGestures { p ->
                                if (!mine) return@detectTapGestures
                                progress = (p.x / size.width).coerceIn(0f, 1f)
                                if (player.duration > 0) player.seek((progress * player.duration).toInt())
                            }
                        },
                ) {
                    val bars = day.wave.ifEmpty { List(40) { 0.08f } }
                    val n = bars.size.coerceAtLeast(1)
                    val step = size.width / n
                    val mid = size.height / 2
                    bars.forEachIndexed { i, v ->
                        val h = (0.12f + v * 0.88f) * size.height * 0.42f
                        val x = i * step + step / 2
                        val color = if ((i + 0.5f) / n <= progress) Oled.TextPrimary else Oled.TextSecondary.copy(alpha = 0.45f)
                        if (glass) drawLine(color, Offset(x, mid - h), Offset(x, mid + h), (step * 0.55f).coerceIn(1f, 4.dp.toPx()), cap = StrokeCap.Round)
                        else pencilTick(Offset(x, mid - h), Offset(x + 0.6f, mid + h), (step * 0.6f).coerceIn(1f, 3.dp.toPx()), color, i)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(clock(if (playing || progress > 0f) (progress * day.audioMs).toLong() else day.audioMs), color = Oled.TextSecondary, fontSize = 12.sp, maxLines = 1)
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
}
