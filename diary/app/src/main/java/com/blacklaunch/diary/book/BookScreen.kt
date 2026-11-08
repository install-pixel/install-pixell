package com.blacklaunch.diary.book

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.blacklaunch.diary.DiaryViewModel
import com.blacklaunch.diary.OpenResult
import com.blacklaunch.diary.R
import com.blacklaunch.diary.audio.Recorder
import com.blacklaunch.diary.data.DiaryInfo
import com.blacklaunch.diary.search.SearchScreen
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.ConfirmDialog
import com.blacklaunch.diary.ui.EqualWidthRow
import com.blacklaunch.diary.ui.Glyph
import com.blacklaunch.diary.ui.GlyphIcon
import com.blacklaunch.diary.ui.Limits
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.MenuDialog
import com.blacklaunch.diary.ui.MenuEntry
import com.blacklaunch.diary.ui.PasswordDialog
import com.blacklaunch.diary.ui.SettingsDialog
import com.blacklaunch.diary.ui.SheetButton
import com.blacklaunch.diary.ui.SheetDialog
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.TextDialog
import com.blacklaunch.diary.ui.Tone
import com.blacklaunch.diary.ui.WindowHint
import com.blacklaunch.diary.ui.pencilFrame
import com.blacklaunch.diary.ui.theme.Oled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Сторінка книги: обкладинка або одна з (до двох) сторінок дня. */
sealed interface BookPage {
    data object Cover : BookPage
    data class DayPart(val day: Long, val part: Int) : BookPage
}

/** Які вікна відкриті. */
private sealed interface BookDialog {
    data object Menu : BookDialog
    data class Place(val day: Long) : BookDialog
    data class Photo(val day: Long) : BookDialog
    data object Calendar : BookDialog
    data object LockSet : BookDialog
    data class LockConfirm(val code: String) : BookDialog
    data object LockRemove : BookDialog
    data object Rename : BookDialog
    data object NewDiary : BookDialog
    data object Settings : BookDialog
    data object Backup : BookDialog
    data class Restore(val uri: android.net.Uri, val wrong: Boolean) : BookDialog
    data object Erase : BookDialog
    data object Forgot : BookDialog
}

/**
 * Щоденник на весь екран: обкладинка (із замком, якщо поставлено код), далі — сторінки днів. Гортається пальцем,
 * зверху лише «назад», назва й «⋯» (THEMES.md, розділ 7).
 */
@Composable
fun BookScreen(
    vm: DiaryViewModel,
    info: DiaryInfo,
    onShelf: (() -> Unit)?,
    onExit: () -> Unit,
    onLanguageChanged: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val glass = AppStyle.glass
    val open = vm.open?.takeIf { it.info.id == info.id }
    val hours = vm.settings.inkDrying.hours

    // Без замка щоденник відкривається сам (ключем телефона).
    LaunchedEffect(info.id, info.locked, open == null) {
        if (!info.locked && open == null && vm.tryOpen(info.id, "") == OpenResult.Failed) vm.show(R.string.open_failed)
    }
    // Щоденник із замком, поки відкритий, — без знімків екрана й прев'ю в «Недавніх».
    DisposableEffect(open != null && info.locked) {
        val w = (context as? Activity)?.window
        if (open != null && info.locked) w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val today = remember { DiaryViewModel.today() }
    val extraDays = remember { mutableStateListOf<Long>() }
    val days = open?.days
    val dayList = remember(days?.keys?.toSet(), extraDays.toList()) {
        ((days?.keys ?: emptySet()) + today + extraDays).toSortedSet().toList()
    }

    // Розміри сторінки й поділ тексту між двома сторінками дня.
    var pageW by remember { mutableStateOf(0f) }
    val u = pageW / PageMetrics.WIDTH
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val style = pageTextStyle(u.coerceAtLeast(0.01f), density, glass)
    val splitCache = remember(glass, pageW) { HashMap<String, PageSplit>() }
    fun splitFor(text: String, photo: Boolean, audio: Boolean): PageSplit {
        val first = PageMetrics.firstLines(photo, audio)
        val second = PageMetrics.secondLines
        if (pageW <= 0f) return PageSplit(text.length, true, 0)
        // Короткий текст без багатьох рядків точно вміщається на першій — без вимірювань.
        if (text.length < first * 12 && text.count { it == '\n' } < first - 1) return PageSplit(text.length, true, 1)
        val key = "$photo$audio" + text
        splitCache[key]?.let { return it }
        val l = measurer.measure(text, style, constraints = Constraints(maxWidth = (PageMetrics.textWidth * u).toInt().coerceAtLeast(1)))
        val starts = List(l.lineCount) { l.getLineStart(it) }
        val r = splitPages(text.length, starts, first, second)
        if (splitCache.size > 200) splitCache.clear()
        splitCache[key] = r
        return r
    }

    var recording by remember { mutableStateOf<RecordingState?>(null) }
    val pages: List<BookPage> = buildList {
        add(BookPage.Cover)
        if (open != null) {
            dayList.forEach { d ->
                val day = vm.day(d)
                add(BookPage.DayPart(d, 0))
                val split = splitFor(day.text, day.photo, day.audio || recording?.day == d)
                if (split.split < day.text.length || day.ink.any { it.page == 1 }) add(BookPage.DayPart(d, 1))
            }
        }
    }
    val flip = remember(info.id) { FlipState(0) }
    var currentKey by remember(info.id) { mutableStateOf<BookPage>(BookPage.Cover) }
    // Сторінки додались чи зникли (новий день, друга сторінка) — лишаємось на тій самій.
    LaunchedEffect(pages) {
        val i = pages.indexOf(currentKey)
        if (i >= 0 && i != flip.index && !flip.moving) flip.index = i
        if (flip.index >= pages.size) flip.index = pages.size - 1
    }
    LaunchedEffect(open == null) { if (open == null) { flip.index = 0; currentKey = BookPage.Cover } }
    val currentPage = pages.getOrNull(flip.index) ?: BookPage.Cover
    val currentDay = (currentPage as? BookPage.DayPart)?.day

    // Куди відкривається обкладинка: на закладку (якщо так у Налаштуваннях) чи на сьогодні.
    val openTarget = run {
        val bm = open?.bookmark
        val day = if (vm.settings.openAtBookmark && bm != null && bm in dayList) bm else today
        pages.indexOf(BookPage.DayPart(day, 0)).coerceAtLeast(if (pages.size > 1) 1 else 0)
    }

    var dialog by remember { mutableStateOf<BookDialog?>(null) }
    var searching by remember { mutableStateOf(false) }
    var ink by remember { mutableStateOf<InkSession?>(null) }
    var highlight by remember { mutableStateOf<Pair<Long, IntRange>?>(null) }
    var editing by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var focusReq by remember { mutableStateOf<Triple<Long, Int, Int>?>(null) }
    var pendingFlip by remember { mutableStateOf<BookPage?>(null) }
    val blockers = remember { mutableStateMapOf<String, Rect>() }
    var bookCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var bookTop by remember { mutableStateOf(0f) }
    val cursorBottom = remember { Animatable(0f) }
    val recorder = remember { Recorder(context) }

    LaunchedEffect(pages, pendingFlip) {
        val p = pendingFlip ?: return@LaunchedEffect
        val i = pages.indexOf(p)
        if (i >= 0) {
            pendingFlip = null
            currentKey = p
            flip.flipTo(i)
        }
    }

    fun jumpToDay(day: Long, mark: IntRange? = null) {
        if (day !in dayList) extraDays += day
        val target = BookPage.DayPart(day, 0)
        val partTarget = if (mark != null) {
            val d = vm.day(day)
            val split = splitFor(d.text, d.photo, d.audio)
            if (mark.first >= split.split && split.split < d.text.length) BookPage.DayPart(day, 1) else target
        } else target
        highlight = mark?.let { day to it }
        pendingFlip = partTarget
    }

    // ——— Запис голосу ———
    fun startRecording(day: Long) {
        if (recording != null) return
        if (!recorder.start()) { vm.show(R.string.record_failed); return }
        haptics.longPress()
        val st = RecordingState(day)
        recording = st
        scope.launch {
            while (recording === st) {
                recorder.sample()
                st.wave = DiaryViewModel.compactWave(recorder.wave.toList())
                st.elapsed = recorder.elapsed
                if (recorder.elapsed >= Limits.AUDIO_MS) break
                delay(80)
            }
            if (recording === st) {
                recording = null
                val r = recorder.finish()
                if (r != null && r.second >= 800) vm.saveAudio(st.day, r.first, r.second, recorder.wave.toList()) else vm.show(R.string.record_short)
            }
        }
    }
    fun stopRecording() {
        val st = recording ?: return
        recording = null
        haptics.tap()
        val r = recorder.finish()
        if (r != null && r.second >= 800) vm.saveAudio(st.day, r.first, r.second, recorder.wave.toList()) else vm.show(R.string.record_short)
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    var micFor by remember { mutableStateOf<Long?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val d = micFor
        micFor = null
        if (ok && d != null) startRecording(d) else if (!ok) vm.show(R.string.mic_denied)
    }
    fun record(day: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording(day)
        else { micFor = day; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    // ——— Вибір фото й файлів ———
    var photoFor by remember { mutableStateOf<Long?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val d = photoFor
        photoFor = null
        if (uri != null && d != null) vm.setPhoto(d, uri)
    }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setCover(info.id, uri)
    }
    var backupPassword by remember { mutableStateOf<String?>(null) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = backupPassword
        backupPassword = null
        if (uri != null && pw != null) vm.backup(uri, pw)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dialog = BookDialog.Restore(uri, false)
    }

    val host = object : PageHost {
        override val vm: DiaryViewModel = vm
        override val bookCoords: LayoutCoordinates? get() = bookCoords
        override fun textSink(day: Long, part: Int) = TextSink { full, cursor ->
            val d = vm.dayNow(day)
            if (!full.startsWith(d.sealed) || full.length > Limits.DAY_TEXT) return@TextSink false
            val split = splitFor(full, d.photo, d.audio || recording?.day == day)
            if (!split.fits) { vm.show(R.string.day_full); return@TextSink false }
            vm.editText(day, full)
            if (part == 0 && split.split < full.length && cursor >= split.split) {
                // Сторінка закінчилась — переходимо на продовження дня, курсор там само.
                focusReq = Triple(day, 1, cursor)
                pendingFlip = BookPage.DayPart(day, 1)
            } else if (part == 1 && (cursor < split.split || split.split >= full.length)) {
                focusReq = Triple(day, 0, cursor)
                pendingFlip = BookPage.DayPart(day, 0)
            }
            true
        }
        override fun onFocus(day: Long, part: Int, focused: Boolean) {
            editing = if (focused) day to part else if (editing == day to part) null else editing
        }
        override fun onCursor(rect: Rect) {
            scope.launch { cursorBottom.animateTo(rect.bottom, tween(140)) }
        }
        override fun focusRequest(day: Long, part: Int): Int? = focusReq?.takeIf { it.first == day && it.second == part }?.third
        override fun consumeFocus() { focusReq = null }
        override fun editPlace(day: Long) { dialog = BookDialog.Place(day) }
        override fun openPhoto(day: Long) { dialog = BookDialog.Photo(day) }
        override fun block(key: String, rect: Rect?) { if (rect == null) blockers.remove(key) else blockers[key] = rect }
        override fun jumpTo(day: Long) = jumpToDay(day)
        override fun dryHint() = vm.show(R.string.ink_dry)
        override val recording: RecordingState? get() = recording
        override fun stopRecording() = stopRecording()
    }

    // Лок-екран: барабани на обкладинці.
    val digits = remember(info.id) { mutableStateListOf(0, 0, 0, 0) }
    var shake by remember { mutableStateOf(0) }
    var waitUntil by remember(info.id) { mutableStateOf(info.retryAt) }
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(waitUntil) {
        while (System.currentTimeMillis() < waitUntil) { nowTick = System.currentTimeMillis(); delay(500) }
        nowTick = System.currentTimeMillis()
    }

    BackHandler {
        when {
            ink != null -> ink = null
            editing != null -> focusManager.clearFocus()
            flip.index > 0 -> scope.launch { currentKey = BookPage.Cover; flip.flipTo(0) }
            onShelf != null -> onShelf()
            else -> onExit()
        }
    }

    Column(Modifier.fillMaxSize().background(Oled.Black).statusBarsPadding().navigationBarsPadding()) {
        // Верхній рядок: назад, назва, «⋯».
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val canBack = flip.index > 0 || onShelf != null
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = canBack) {
                haptics.tap()
                if (flip.index > 0) scope.launch { focusManager.clearFocus(); ink = null; currentKey = BookPage.Cover; flip.flipTo(0) } else onShelf?.invoke()
            }, contentAlignment = Alignment.Center) {
                if (canBack) GlyphIcon(Glyph.Back, stringResource(R.string.back), tint = Oled.TextPrimary)
            }
            Text(
                if (flip.index > 0) info.name else "",
                color = Oled.TextSecondary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { haptics.tap(); focusManager.clearFocus(); dialog = BookDialog.Menu }, contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.More, stringResource(R.string.menu), tint = Oled.TextPrimary)
            }
        }

        // Книга. Коли з'являється клавіатура, вся сторінка (папір, лінії й текст разом) підсувається вгору, щоб рядок
        // з курсором був над клавіатурою, — як коли підсовуєш щоденник. Тло лишається на місці.
        val ime = WindowInsets.ime
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { bookTop = it.positionInWindow().y },
        ) {
            val availW = maxWidth - 28.dp
            val availH = maxHeight - RibbonTop - RibbonBottom
            val size = with(density) { pageSize(androidx.compose.ui.geometry.Size(availW.toPx(), availH.toPx())) }
            val pw = with(density) { size.width.toDp() }
            val ph = with(density) { size.height.toDp() }
            LaunchedEffect(size.width) { pageW = size.width }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 0.dp)
                    .graphicsLayer {
                        val imeH = ime.getBottom(this).toFloat()
                        val rootH = view.rootView.height.toFloat()
                        val topOfPage = bookTop + RibbonTop.toPx()
                        val visible = rootH - imeH - 20.dp.toPx()
                        translationY = if (editing != null && imeH > 0f) -(topOfPage + cursorBottom.value - visible).coerceAtLeast(0f) else 0f
                    }
                    .size(pw, ph + RibbonTop + RibbonBottom),
            ) {
                Box(
                    Modifier
                        .offset(y = RibbonTop)
                        .size(pw, ph)
                        .onGloballyPositioned { bookCoords = it },
                ) {
                    FlipBook(
                        state = flip,
                        count = pages.size,
                        enabled = editing == null && ink == null && recording == null,
                        heavy = { pages.getOrNull(it) == BookPage.Cover },
                        canFlipForward = { i -> !(i == 0 && open == null) },
                        nextOf = { i -> if (i == 0) openTarget else i + 1 },
                        prevOf = { i -> i - 1 },
                        blocked = { blockers.values.toList() },
                        onFlipped = { i -> currentKey = pages.getOrNull(i) ?: BookPage.Cover; highlight = highlight?.takeIf { h -> (pages.getOrNull(i) as? BookPage.DayPart)?.day == h.first } },
                    ) { i ->
                        when (val p = pages.getOrNull(i)) {
                            BookPage.Cover, null -> {
                                val photo = if (info.cover) vm.coverBitmap(info.id, glass) else null
                                CoverFace(
                                    name = info.name,
                                    photo = photo,
                                    pattern = info.tint,
                                    locked = info.locked,
                                    lock = when {
                                        info.locked && open == null -> {
                                            {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    val waiting = nowTick < waitUntil
                                                    CombinationLock(digits.toList(), { w, d -> digits[w] = d }, enabled = !waiting, shake = shake, description = stringResource(R.string.lock_wheels))
                                                    if (waiting) {
                                                        Text(stringResource(R.string.lock_wait, ((waitUntil - nowTick) / 1000 + 1).toInt()), color = Oled.Danger, fontSize = 14.sp)
                                                    }
                                                    SheetButton(stringResource(R.string.lock_open), {
                                                        scope.launch {
                                                            when (val r = vm.tryOpen(info.id, digits.joinToString(""))) {
                                                                OpenResult.Opened -> { haptics.longPress(); digits.indices.forEach { digits[it] = 0 } }
                                                                is OpenResult.Wrong -> { shake++; haptics.longPress(); waitUntil = r.retryAt; vm.show(R.string.lock_wrong) }
                                                                is OpenResult.Wait -> { waitUntil = r.retryAt }
                                                                OpenResult.Failed -> vm.show(R.string.open_failed)
                                                            }
                                                        }
                                                    }, tone = Tone.Confirm, enabled = !waiting)
                                                }
                                            }
                                        }
                                        else -> null
                                    },
                                )
                                if (open != null) {
                                    Box(Modifier.fillMaxSize().padding(bottom = 22.dp), contentAlignment = Alignment.BottomCenter) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (info.locked) GlyphIcon(Glyph.Unlock, null, tint = Oled.TextSecondary, size = 18.dp)
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.cover_turn_hint), color = Oled.TextSecondary, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                            is BookPage.DayPart -> {
                                val d = vm.day(p.day).dried(System.currentTimeMillis(), hours)
                                val split = splitFor(d.text, d.photo, d.audio || recording?.day == p.day)
                                val before = i - 1
                                val after = pages.size - 1 - i
                                val years = if (p.day == today) {
                                    val t = LocalDate.ofEpochDay(today)
                                    dayList.lastOrNull { it < today && LocalDate.ofEpochDay(it).let { x -> x.monthValue == t.monthValue && x.dayOfMonth == t.dayOfMonth } }
                                } else null
                                DayPage(
                                    host = host,
                                    day = d,
                                    part = p.part,
                                    split = split.split,
                                    pageWidthPx = size.width,
                                    edgeBefore = edgeCount(before),
                                    edgeAfter = edgeCount(after),
                                    hours = hours,
                                    highlight = highlight?.takeIf { it.first == p.day }?.second,
                                    ink = ink,
                                    yearsAgo = years,
                                    editable = true,
                                )
                            }
                        }
                    }
                }
                // Закладка — над книжкою (з корінця), лише коли щоденник відкритий.
                if (open != null && currentDay != null) {
                    val bm = open.bookmark
                    BookmarkRibbon(
                        pageW = pw,
                        pageH = ph,
                        here = bm == currentDay,
                        elsewhere = bm != null && bm != currentDay && bm in dayList,
                        enabled = editing == null && ink == null && !flip.moving,
                        onPlace = { vm.setBookmark(currentDay) },
                        onRemove = { vm.setBookmark(null) },
                        onJump = { bm?.let { jumpToDay(it) } },
                        description = stringResource(R.string.bookmark),
                    )
                }
            }
        }

        // Панель пера.
        ink?.let { s -> InkToolbar(s, onUndo = {
            val last = s.undo.removeLastOrNull() ?: return@InkToolbar
            s.redo += vm.day(s.day).ink
            vm.setInk(s.day, last)
        }, onRedo = {
            val next = s.redo.removeLastOrNull() ?: return@InkToolbar
            s.undo += vm.day(s.day).ink
            vm.setInk(s.day, next)
        }, onDone = { ink = null }) }
    }

    if (searching) {
        SearchScreen(
            search = { q, ph, au -> vm.search(q, ph, au) },
            onClose = { searching = false },
            onOpen = { h -> searching = false; jumpToDay(h.day, if (h.start >= 0) h.start until h.end else null) },
        )
    }

    // ——— Вікна ———
    when (val dl = dialog) {
        null -> Unit
        BookDialog.Menu -> MenuDialog(null, buildList {
            val day = currentDay
            if (open != null && day != null) {
                val d = vm.day(day)
                add(MenuEntry(null, stringResource(R.string.menu_page)))
                add(MenuEntry(Glyph.Gallery, stringResource(if (d.photo) R.string.photo_replace else R.string.photo_add)) { photoFor = day; vm.keepOpenOnce = true; pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                if (!d.audio && recording == null) add(MenuEntry(Glyph.Mic, stringResource(R.string.record)) { record(day) })
                add(MenuEntry(Glyph.Pen, stringResource(R.string.ink_mode)) { focusManager.clearFocus(); ink = InkSession(day, (currentPage as BookPage.DayPart).part) })
                add(MenuEntry(Glyph.Pin, stringResource(R.string.place)) { dialog = BookDialog.Place(day) })
            }
            if (open != null) {
                add(MenuEntry(null, stringResource(R.string.menu_diary)))
                add(MenuEntry(Glyph.Search, stringResource(R.string.search)) { searching = true })
                add(MenuEntry(Glyph.Calendar, stringResource(R.string.calendar)) { dialog = BookDialog.Calendar })
                add(MenuEntry(Glyph.Photo, stringResource(if (info.cover) R.string.cover_photo_change else R.string.cover_photo)) { vm.keepOpenOnce = true; pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                if (info.cover) add(MenuEntry(Glyph.Close, stringResource(R.string.cover_photo_remove)) { vm.removeCover(info.id) })
                add(MenuEntry(Glyph.Edit, stringResource(R.string.rename)) { dialog = BookDialog.Rename })
                add(MenuEntry(Glyph.Lock, stringResource(if (info.locked) R.string.lock_change else R.string.lock_set)) { dialog = BookDialog.LockSet })
                if (info.locked) add(MenuEntry(Glyph.Unlock, stringResource(R.string.lock_remove)) { dialog = BookDialog.LockRemove })
                add(MenuEntry(Glyph.Backup, stringResource(R.string.backup)) { dialog = BookDialog.Backup })
            }
            add(MenuEntry(null, stringResource(R.string.menu_more)))
            add(MenuEntry(Glyph.Plus, stringResource(R.string.new_diary)) { dialog = BookDialog.NewDiary })
            if (onShelf != null) add(MenuEntry(Glyph.Grid, stringResource(R.string.shelf)) { onShelf() })
            add(MenuEntry(Glyph.Restore, stringResource(R.string.restore)) { vm.keepOpenOnce = true; openBackup.launch(arrayOf("*/*")) })
            add(MenuEntry(Glyph.Settings, stringResource(R.string.settings)) { dialog = BookDialog.Settings })
            if (open != null) add(MenuEntry(Glyph.Delete, stringResource(R.string.erase), Tone.Danger) { dialog = BookDialog.Erase })
            else if (info.locked) add(MenuEntry(Glyph.Delete, stringResource(R.string.forgot_code), Tone.Danger) { dialog = BookDialog.Forgot })
        }, onDismiss = { dialog = null })
        is BookDialog.Place -> {
            val recent = remember { open?.days?.values?.sortedByDescending { it.day }?.map { it.place }?.filter { it.isNotBlank() }?.distinct().orEmpty() }
            TextDialog(
                title = stringResource(R.string.place),
                initial = vm.day(dl.day).place,
                placeholder = stringResource(R.string.place_hint),
                limit = Limits.PLACE,
                suggestions = recent.filter { it != vm.day(dl.day).place },
                hint = stringResource(R.string.place_explain),
                allowEmpty = true,
                onDismiss = { dialog = null },
            ) { vm.setPlace(dl.day, it) }
        }
        is BookDialog.Photo -> PhotoViewer(vm, dl.day, onDismiss = { dialog = null }, onReplace = {
            photoFor = dl.day
            vm.keepOpenOnce = true; pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, onDelete = { vm.removePhoto(dl.day) })
        BookDialog.Calendar -> CalendarDialog(
            written = open?.days?.keys?.toSet().orEmpty(),
            start = currentDay ?: today,
            onDismiss = { dialog = null },
        ) { jumpToDay(it) }
        BookDialog.LockSet -> LockSetDialog(onDismiss = { dialog = null }) { code -> dialog = BookDialog.LockConfirm(code) }
        is BookDialog.LockConfirm -> ConfirmDialog(
            title = stringResource(R.string.lock_confirm_title),
            text = stringResource(R.string.lock_confirm_text, dl.code),
            confirm = stringResource(R.string.lock_fix),
            tone = Tone.Confirm,
            onDismiss = { dialog = null },
        ) { vm.setPin(dl.code) }
        BookDialog.LockRemove -> ConfirmDialog(
            title = stringResource(R.string.lock_remove),
            text = stringResource(R.string.lock_remove_text),
            confirm = stringResource(R.string.lock_remove_go),
            tone = Tone.Neutral,
            onDismiss = { dialog = null },
        ) { vm.removePin() }
        BookDialog.Rename -> TextDialog(stringResource(R.string.rename), info.name, stringResource(R.string.diary_name_hint), Limits.DIARY_NAME, onDismiss = { dialog = null }) { vm.rename(info.id, it) }
        BookDialog.NewDiary -> TextDialog(stringResource(R.string.new_diary), "", stringResource(R.string.diary_name_hint), Limits.DIARY_NAME, onDismiss = { dialog = null }, hint = stringResource(R.string.new_diary_hint), allowEmpty = true) {
            val d = vm.createDiary(it)
            vm.closeDiary()
            vm.current = d.id
        }
        BookDialog.Settings -> SettingsDialog(onDismiss = { dialog = null }, onLanguageChanged = onLanguageChanged)
        BookDialog.Backup -> PasswordDialog(restore = false, wrong = false, onDismiss = { dialog = null }) { pw ->
            backupPassword = pw
            val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now())
            vm.keepOpenOnce = true; createBackup.launch("${info.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").ifBlank { "diary" }}-$stamp.diary")
        }
        is BookDialog.Restore -> PasswordDialog(restore = true, wrong = dl.wrong, onDismiss = { dialog = null }) { pw ->
            vm.restore(dl.uri, pw) { dialog = BookDialog.Restore(dl.uri, true) }
        }
        BookDialog.Erase -> ConfirmDialog(
            title = stringResource(R.string.erase_title, info.name),
            text = stringResource(R.string.erase_text),
            confirm = stringResource(R.string.erase_go),
            tone = Tone.Danger,
            onDismiss = { dialog = null },
        ) { vm.erase(info.id) }
        BookDialog.Forgot -> ConfirmDialog(
            title = stringResource(R.string.forgot_code),
            text = stringResource(R.string.forgot_text),
            confirm = stringResource(R.string.erase_go),
            tone = Tone.Danger,
            onDismiss = { dialog = null },
        ) { vm.erase(info.id) }
    }
}

/** Поставити код: 4 барабани й «Зафіксувати». */
@Composable
private fun LockSetDialog(onDismiss: () -> Unit, onFix: (String) -> Unit) {
    val digits = remember { mutableStateListOf(0, 0, 0, 0) }
    SheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.lock_set),
        buttons = {
            SheetButton(stringResource(R.string.cancel), onDismiss)
            SheetButton(stringResource(R.string.lock_fix), { onFix(digits.joinToString("")) }, tone = Tone.Confirm)
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            CombinationLock(digits.toList(), { w, d -> digits[w] = d }, enabled = true, description = stringResource(R.string.lock_wheels))
            WindowHint(stringResource(R.string.lock_set_hint))
        }
    }
}

/** Фото дня великим (теж оброблене під тему — оригінал виглядав би чужим). Хрестик унизу, «⋯» — замінити, прибрати. */
@Composable
private fun PhotoViewer(vm: DiaryViewModel, day: Long, onDismiss: () -> Unit, onReplace: () -> Unit, onDelete: () -> Unit) {
    val haptics = LocalHaptics.current
    var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var menu by remember { mutableStateOf(false) }
    val glass = AppStyle.glass
    LaunchedEffect(day, glass) { image = vm.photo(day, glass) }
    com.blacklaunch.diary.ui.SmoothDialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { haptics.tap(); menu = true }, contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.More, stringResource(R.string.menu), tint = Oled.TextPrimary)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                image?.let {
                    androidx.compose.foundation.Image(
                        it,
                        contentDescription = stringResource(R.string.photo_of_day),
                        modifier = Modifier.fillMaxSize().pencilFrame(14.dp, seed = 88).padding(3.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
            }
            Box(Modifier.padding(bottom = 24.dp).size(56.dp).background(Oled.Black, CircleShape).pencilFrame(28.dp, seed = 13).clip(CircleShape).clickable { haptics.tap(); onDismiss() }, contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Close, stringResource(R.string.close), tint = SheetInk)
            }
        }
    }
    if (menu) {
        MenuDialog(null, listOf(
            MenuEntry(Glyph.Gallery, stringResource(R.string.photo_replace)) { onDismiss(); onReplace() },
            MenuEntry(Glyph.Delete, stringResource(R.string.photo_remove), Tone.Danger) { onDismiss(); onDelete() },
        ), onDismiss = { menu = false })
    }
}

/** Панель пера: підказка рядком угорі, три товщини й гумка однаковими кнопками, «назад» / «вперед», «Готово». */
@Composable
private fun InkToolbar(s: InkSession, onUndo: () -> Unit, onRedo: () -> Unit, onDone: () -> Unit) {
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.ink_hint), color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            EqualWidthRow(Modifier.weight(1f), gap = 6.dp) {
                InkTool.entries.forEach { t ->
                    val selected = s.tool == t
                    Box(
                        Modifier
                            .height(44.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .pencilFrame(12.dp, seed = t.ordinal + 41, alpha = if (selected) 0.95f else 0.35f, ink = if (selected) com.blacklaunch.diary.ui.SelectedInk else SheetInk)
                            .clickable { haptics.tap(); s.tool = t },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (t == InkTool.Eraser) {
                            GlyphIcon(Glyph.Eraser, stringResource(R.string.ink_eraser), tint = if (selected) Oled.TextPrimary else Oled.TextSecondary, size = 22.dp)
                        } else {
                            Box(Modifier.size((6 + t.ordinal * 5).dp).clip(CircleShape).background(if (selected) Oled.TextPrimary else Oled.TextSecondary))
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = s.undo.isNotEmpty()) { haptics.tap(); onUndo() }, contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Undo, stringResource(R.string.undo), tint = Oled.TextPrimary.copy(alpha = if (s.undo.isNotEmpty()) 1f else 0.3f))
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = s.redo.isNotEmpty()) { haptics.tap(); onRedo() }, contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Redo, stringResource(R.string.redo), tint = Oled.TextPrimary.copy(alpha = if (s.redo.isNotEmpty()) 1f else 0.3f))
            }
            Spacer(Modifier.width(4.dp))
            SheetButton(stringResource(R.string.done), onDone, tone = Tone.Confirm)
        }
    }
}

