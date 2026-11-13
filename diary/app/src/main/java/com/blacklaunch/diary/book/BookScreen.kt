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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.drawText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
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
import com.blacklaunch.diary.ui.SwitchRow
import com.blacklaunch.diary.ui.ChoiceRow
import com.blacklaunch.diary.ui.FieldHint
import com.blacklaunch.diary.settings.InkDrying
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

/** Позначка «знімок / файл — для обкладинки» (не день). */
private const val COVER_TARGET = Long.MIN_VALUE

/** Сторінка книги: обкладинка або одна з (до двох) сторінок дня. */
sealed interface BookPage {
    data object Cover : BookPage
    data class DayPart(val day: Long, val part: Int) : BookPage
    /**
     * Сторінки меню (шестерня): 0 — щоденник (обкладинка, назва, замок, копія), 1 — його налаштування. Сторінки сталого
     * розміру, без прокрутки; між ними — таке саме гортання. Повертаються туди, звідки прийшли.
     */
    data class Menu(val n: Int) : BookPage
    /** Пошук — сторінка, на яку перегортає закладка-пошук; назад — туди, звідки прийшли. */
    data object Search : BookPage
    /** Наступні сторінки знахідок пошуку (у щоденнику нічого не прокручується). */
    data class SearchMore(val n: Int) : BookPage
    /** Календар — сторінка на місяць ([ym] = рік * 12 + місяць - 1); відкривається з пошуку, місяці гортаються. */
    data class Calendar(val ym: Int) : BookPage
    /** Магнітофон дня — на весь щоденник; назад і вперед — туди, звідки прийшли. */
    data class Recorder(val day: Long) : BookPage
}

/** Сторінки, що належать пошуку (знахідки й календар): гортання вперед з останньої — туди, звідки прийшли. */
private fun BookPage?.isSearch() = this == BookPage.Search || this is BookPage.SearchMore || this is BookPage.Calendar

/** Наступна сторінка — того самого роду (знахідки за знахідками, місяць за місяцем). */
private fun sameRun(a: BookPage?, b: BookPage?) = (a is BookPage.Calendar) == (b is BookPage.Calendar) && a.isSearch() && b.isSearch()

private fun java.time.YearMonth.ym() = year * 12 + monthValue - 1

/** Які вікна відкриті. */
private sealed interface BookDialog {
    data class Place(val day: Long) : BookDialog
    data class Caption(val day: Long) : BookDialog
    data class Photo(val day: Long) : BookDialog
    data object LockSet : BookDialog
    data class LockConfirm(val code: String) : BookDialog
    data object LockRemove : BookDialog
    data object Rename : BookDialog
    data object Backup : BookDialog
    data object Erase : BookDialog
    data object Forgot : BookDialog
}

/**
 * Щоденник на весь екран: обкладинка (із замком, якщо поставлено код), далі — сторінки днів. Гортається пальцем.
 * Жодних стандартних рядків і кнопок: меню — шестерня, намальована на самому щоденнику; назад — системним жестом.
 */
@Composable
fun BookScreen(
    vm: DiaryViewModel,
    info: DiaryInfo,
    onShelf: () -> Unit,
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
    val hours = vm.dryingOf(info).hours

    // Без замка щоденник відкривається сам (ключем телефона).
    LaunchedEffect(info.id, info.locked, open == null) {
        if (!info.locked && open == null && vm.tryOpen(info.id, "") == OpenResult.Failed) vm.show(R.string.open_failed)
    }
    // Поки відкрито щоденник, сповіщення — всередині нього; пішли на полицю — звичне місце.
    DisposableEffect(Unit) { onDispose { com.blacklaunch.diary.ui.NoticeArea.rect = null } }
    // Щоденник із замком, поки відкритий, — без знімків екрана й прев'ю в «Недавніх».
    DisposableEffect(open != null && info.locked) {
        val w = (context as? Activity)?.window
        if (open != null && info.locked) w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val today = remember { DiaryViewModel.today() }
    val extraDays = remember { mutableStateListOf<Long>() }
    val days = open?.days
    // У книжці — лише дні, де щось є (текст, фото, голос, малюнок, місце), плюс сьогодні й ті, що відкрили з календаря.
    // Порожній минулий день (лише прибрані місця під фото / голос) — не показується.
    val dayList = remember(days?.values?.toList(), extraDays.toList()) {
        ((days?.filterValues { !it.isBlank }?.keys ?: emptySet()) + today + extraDays).toSortedSet().toList()
    }

    // Розміри сторінки й поділ тексту між двома сторінками дня.
    var pageW by remember { mutableStateOf(0f) }
    var pageDp by remember { mutableStateOf(androidx.compose.ui.unit.DpSize.Zero) }
    val u = pageW / PageMetrics.WIDTH
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val style = pageTextStyle(u.coerceAtLeast(0.01f), density, glass)
    val splitCache = remember(glass, pageW) { HashMap<String, PageSplit>() }
    fun splitFor(text: String, photo: Boolean, audio: Boolean, torn: Int = 0): PageSplit {
        // Вирвана сторінка місця не дає: вирвали першу — увесь текст на другій; другу — лише на першій.
        val first = if (torn and 1 != 0) 0 else PageMetrics.firstLines(photo, audio)
        val second = if (torn and 2 != 0) 0 else PageMetrics.secondLines
        if (pageW <= 0f) return PageSplit(text.length, true, 0)
        // Короткий текст без багатьох рядків точно вміщається на першій — без вимірювань.
        if (first > 0 && text.length < first * 12 && text.count { it == '\n' } < first - 1) return PageSplit(text.length, true, 1)
        val key = "$photo$audio$torn" + text
        splitCache[key]?.let { return it }
        // Текст, що починається вгорі сторінки (перша без фото й голосу, друга завжди), — перший рядок із відступом за
        // закладкою-пошуком; тож першу сторінку й продовження міряємо окремо, кожну зі своїм відступом.
        val indent = ribbonIndentPx(u, density)
        val cons = Constraints(maxWidth = (PageMetrics.textWidth * u).toInt().coerceAtLeast(1))
        fun usedLines(l: androidx.compose.ui.text.TextLayoutResult, t: String): Int {
            // Пробіли й порожні рядки в кінці дня місця не займають — рахуємо лише до останнього написаного символу.
            val trimmed = t.trimEnd().length
            return if (trimmed == 0) 0 else l.getLineForOffset(trimmed - 1) + 1
        }
        val l = measurer.measure(indentFirstParagraph(text, if (!photo && !audio) indent else 0f, density), style, constraints = cons)
        val starts = List(l.lineCount) { l.getLineStart(it) }
        val base = splitPages(text.length, starts, first, second)
        val used = if (base.split >= text.length) usedLines(l, text) else {
            val rest = text.substring(base.split)
            first + usedLines(measurer.measure(indentFirstParagraph(rest, indent, density), style, constraints = cons), rest)
        }
        val r = base.copy(fits = used <= first + second, lines = used)
        if (splitCache.size > 200) splitCache.clear()
        splitCache[key] = r
        return r
    }

    var recording by remember { mutableStateOf<RecordingState?>(null) }

    /**
     * Які місця є на першій сторінці дня: фото / голос, якщо вони вже є, або порожнє місце-запрошення, якщо в щоденнику
     * це увімкнено, на цей день його не прибрали й воно вміщається разом із текстом (інакше місце віддається тексту).
     */
    fun slotsFor(d: com.blacklaunch.diary.data.Day, text: String = d.text): Pair<Boolean, Boolean> {
        if (d.torn and 1 != 0) return false to false
        val hasP = d.photo
        val hasA = d.audio || d.audioB || recording?.day == d.day
        // Висохлий день — без місць-запрошень: додати вже нічого не можна.
        val open = !DiaryViewModel.isDry(d.day)
        val wantP = open && !hasP && info.photos && !d.noPhoto
        val wantA = open && !hasA && info.voice && !d.noAudio
        var p = hasP || wantP
        var a = hasA || wantA
        if (wantA && !splitFor(text, p, a, d.torn).fits) a = hasA
        if (wantP && !splitFor(text, p, a, d.torn).fits) p = hasP
        return p to a
    }
    /** Поділ тексту дня з його місцями й вирваними сторінками. */
    fun splitDay(d: com.blacklaunch.diary.data.Day, text: String = d.text): PageSplit =
        slotsFor(d, text).let { splitFor(text, it.first, it.second, d.torn) }
    fun roomFor(day: Long, photo: Boolean): Boolean {
        val d = vm.day(day)
        val (p, a) = slotsFor(d)
        return if (photo) p || splitFor(d.text, true, a, d.torn).fits else a || splitFor(d.text, p, true, d.torn).fits
    }

    var menuOpen by remember(info.id) { mutableStateOf(false) }
    var searchOpen by remember(info.id) { mutableStateOf(false) }
    var calendarOpen by remember(info.id) { mutableStateOf(false) }
    var recorderFor by remember(info.id) { mutableStateOf<Long?>(null) }
    var recorderReturn by remember(info.id) { mutableStateOf<BookPage>(BookPage.Cover) }
    var searchReturn by remember(info.id) { mutableStateOf<BookPage>(BookPage.Cover) }
    // Нижня панель: розгорнутий вибір джерела фото.
    var photoTools by remember(info.id) { mutableStateOf(false) }
    var menuReturn by remember(info.id) { mutableStateOf<BookPage>(BookPage.Cover) }
    // Усі сторінки книжки разом із вирваними: вирвана займає своє місце (номер пропущено), але не показується, а
    // наступна після неї має рваний клаптик біля корінця.
    val physical: List<Pair<BookPage.DayPart, Boolean>> = buildList {
        if (open == null) return@buildList
        ((days?.filterValues { it.torn != 0 }?.keys ?: emptySet()) + dayList).toSortedSet().forEach { d ->
            val day = vm.day(d)
            val t = day.torn
            val shown = d in dayList
            val split = splitDay(day)
            if (t and 1 != 0) add(BookPage.DayPart(d, 0) to true) else if (shown) add(BookPage.DayPart(d, 0) to false)
            val second = if (t and 1 != 0) day.text.isNotBlank() || day.ink.any { it.page == 1 } || d == today || d in extraDays
            else shown && (split.split < day.text.length || day.ink.any { it.page == 1 })
            if (t and 2 != 0) add(BookPage.DayPart(d, 1) to true) else if (second) add(BookPage.DayPart(d, 1) to false)
        }
    }
    val pageNumber = HashMap<BookPage, Int>()
    val stubs = HashSet<BookPage>()
    run {
        var n = 0
        var prevTorn = false
        physical.forEach { (p, torn) ->
            if (torn) { n++; prevTorn = true; return@forEach }
            if (!vm.day(p.day).isBlank || vm.day(p.day).torn != 0) pageNumber[p] = ++n
            if (prevTorn) stubs += p
            prevTorn = false
        }
    }
    val searchUi = remember(info.id) { SearchUi() }
    val searchFound = remember(searchOpen, searchUi.query, searchUi.mode, searchUi.shuffle) {
        if (searchOpen) vm.find(searchUi.query, searchUi.mode) else com.blacklaunch.diary.search.Found(emptyList(), null, false)
    }
    val resultSlices = remember(searchFound, searchUi.mode, searchUi.query, pageDp, density.fontScale) {
        searchSlices(searchFound, searchUi, pageDp.width.value, pageDp.height.value, density.fontScale)
    }
    LaunchedEffect(searchOpen) { if (!searchOpen) { searchUi.player.stop(); searchUi.playingDay = -1L } }
    // Місяці календаря: від першого запису (чи створення щоденника) до поточного.
    val calendarMonths: List<Int> = run {
        val now = java.time.YearMonth.now().ym()
        val firstDay = listOfNotNull(
            open?.days?.keys?.minOrNull(),
            java.time.Instant.ofEpochMilli(info.created).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay(),
        ).minOrNull() ?: today
        val first = java.time.YearMonth.from(LocalDate.ofEpochDay(firstDay)).ym().coerceAtMost(now)
        (first..now).toList()
    }
    val pages: List<BookPage> = buildList {
        add(BookPage.Cover)
        physical.forEach { (p, torn) -> if (!torn) add(p) }
        if (menuOpen) {
            add(BookPage.Menu(0))
            if (open != null) add(BookPage.Menu(1))
        }
        if (searchOpen && open != null) {
            add(BookPage.Search)
            for (n in 1 until resultSlices.size) add(BookPage.SearchMore(n))
            if (calendarOpen) calendarMonths.forEach { add(BookPage.Calendar(it)) }
        }
        recorderFor?.let { if (open != null) add(BookPage.Recorder(it)) }
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
    // Клаптик раніше вирваної сторінки відгортається вліво, поки цю виривають, і повертається зверху, коли
    // відпустили (сторінка вже лягла й заклеєна).
    val stubFold by animateFloatAsState(
        if (flip.tearFinger != null && currentPage in stubs) 1f else 0f,
        tween(if (flip.tearFinger != null) 300 else 380, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "stubFold",
    )
    // Підказка «Гортай обкладинку» — через 5 с на обкладинці, плавно.
    var hintShown by remember(info.id) { mutableStateOf(false) }
    LaunchedEffect(open != null, flip.index == 0) {
        hintShown = false
        if (open != null && flip.index == 0) { delay(5_000); hintShown = true }
    }
    val hintAlpha by animateFloatAsState(if (hintShown) 1f else 0f, tween(900), label = "hint")
    val currentDay = (currentPage as? BookPage.DayPart)?.day

    // Куди відкривається обкладинка: на закладку (якщо так у Налаштуваннях) чи на сьогодні.
    val openTarget = run {
        val bm = open?.bookmark
        val day = if (vm.openAtBookmarkOf(info) && bm != null && bm in dayList) bm else today
        // Можна відкривати й з першої сторінки (гортати щоденник від початку).
        if (info.openFirst && pages.size > 1) 1
        else pages.indexOf(BookPage.DayPart(day, 0)).coerceAtLeast(if (pages.size > 1) 1 else 0)
    }

    // Фото сусідніх днів обробляємо наперед — поки гортаєш, вони вже готові (без перемальовування на очах).
    LaunchedEffect(currentDay, glass, open != null) {
        if (open == null) return@LaunchedEffect
        val i = dayList.indexOf(currentDay ?: openTargetDay(dayList, today, open.bookmark, vm.openAtBookmarkOf(info)))
        listOf(i, i - 1, i + 1, i - 2, i + 2).mapNotNull { dayList.getOrNull(it) }.filter { vm.day(it).photo }.forEach { vm.photo(it, glass) }
    }

    var dialog by remember { mutableStateOf<BookDialog?>(null) }

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
            // Окремою корутиною: зміна ключів цього ефекту (pendingFlip = null) не має обривати гортання на півдорозі.
            scope.launch { flip.flipTo(i) }
        }
    }

    // ——— Сторінка меню (шестерня) ———
    // Налаштування щоденника відкрили з полиці (шестерня під корінцем) — закриваючи, повертаємось на полицю.
    var menuFromShelf by remember(info.id) { mutableStateOf(false) }
    fun openMenu() {
        if (menuOpen) return
        focusManager.clearFocus()
        ink = null
        menuReturn = currentKey
        menuOpen = true
        pendingFlip = BookPage.Menu(0)
    }
    /** Магнітофон дня — сторінкою на весь щоденник. */
    fun openRecorder(day: Long) {
        focusManager.clearFocus()
        ink = null
        if (currentKey !is BookPage.Recorder) recorderReturn = currentKey
        recorderFor = day
        pendingFlip = BookPage.Recorder(day)
    }
    fun openSearch() {
        if (searchOpen) return
        focusManager.clearFocus()
        ink = null
        searchReturn = currentKey
        searchOpen = true
        pendingFlip = BookPage.Search
    }
    fun closeSearch() {
        if (!searchOpen) return
        focusManager.clearFocus()
        pendingFlip = searchReturn
    }
    fun closeMenu() {
        if (!menuOpen) return
        if (menuFromShelf) onShelf() else pendingFlip = menuReturn
    }
    LaunchedEffect(info.id) {
        if (vm.settingsFor == info.id) {
            vm.settingsFor = null
            menuFromShelf = true
            openMenu()
        }
    }

    fun jumpToDay(day: Long, mark: IntRange? = null) {
        if (day !in dayList) extraDays += day
        val target = BookPage.DayPart(day, 0)
        val partTarget = if (mark != null) {
            val d = vm.day(day)
            val split = splitDay(d)
            if ((d.torn and 1 != 0) || (mark.first >= split.split && split.split < d.text.length)) BookPage.DayPart(day, 1) else target
        } else target
        highlight = mark?.let { day to it }
        pendingFlip = partTarget
    }

    // ——— Запис голосу ———
    // Пишеться на сторону касети [side] — у кінець того, що там уже є; стрічки на стороні — 45 хвилин.
    fun startRecording(day: Long, side: Int, cutFrom: Long? = null, cutTo: Long? = null) {
        if (recording != null) return
        val d = vm.day(day)
        val full = if (side == 0) (if (d.audio) d.audioMs else 0L) else (if (d.audioB) d.audioMsB else 0L)
        // Переписати відрізок [cutFrom, cutTo) — з місця, де стоїть стрічка; інакше — у кінець записаного.
        val replace = cutFrom != null && cutFrom < full - 100
        val base = if (replace) cutFrom!!.coerceIn(0L, full) else full
        val tail = if (replace) full - (cutTo ?: full).coerceIn(base, full) else 0L
        if (base + tail >= Limits.SIDE_MS - 1000) { vm.show(R.string.recorder_side_full); return }
        if (!recorder.start(info.voiceMode)) { vm.show(R.string.record_failed); return }
        haptics.longPress()
        val st = RecordingState(day, side, base, if (replace) base else null, if (replace) (cutTo ?: full).coerceIn(base, full) else null, info.voiceMode)
        recording = st
        scope.launch {
            while (recording === st) {
                st.level = recorder.sample()
                st.wave = DiaryViewModel.compactWave(recorder.wave.toList())
                st.elapsed = recorder.elapsed
                if (base + recorder.elapsed + (if (st.cutTo != null) full - st.cutTo else 0L) >= Limits.SIDE_MS) break
                delay(80)
            }
            if (recording === st) {
                recording = null
                val r = recorder.finish()
                if (r != null && r.second >= 800) vm.saveAudio(st.day, r.first, r.second, recorder.wave.toList(), st.side, st.cutFrom, st.cutTo, st.mode) else vm.show(R.string.record_short)
            }
        }
    }
    fun pauseRecording(on: Boolean) {
        val st = recording ?: return
        if (on) recorder.pause() else recorder.resume()
        st.paused = on
    }
    fun stopRecording() {
        val st = recording ?: return
        recording = null
        haptics.tap()
        val r = recorder.finish()
        if (r != null && r.second >= 800) vm.saveAudio(st.day, r.first, r.second, recorder.wave.toList(), st.side, st.cutFrom, st.cutTo, st.mode) else vm.show(R.string.record_short)
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    var micFor by remember { mutableStateOf<List<Long?>?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val d = micFor
        micFor = null
        if (ok && d != null) startRecording(d[0]!!, d[1]!!.toInt(), d[2], d[3]) else if (!ok) vm.show(R.string.mic_denied)
    }
    fun record(day: Long, side: Int, cutFrom: Long? = null, cutTo: Long? = null) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording(day, side, cutFrom, cutTo)
        else { micFor = listOf(day, side.toLong(), cutFrom, cutTo); micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    /** Поділитися стороною касети — одразу системне вікно «Поділитися». */
    fun shareTape(day: Long, side: Int) {
        scope.launch {
            val f = vm.exportAudio(day, side) ?: return@launch
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".camera", f)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.keepOpenOnce = true
            runCatching { context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        }
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
    // Камера — своя ([DiaryCamera]), не системна: знімок пишеться в приватну теку програми (не в галерею й не у спільні
    // файли), одразу шифрується в щоденник, а тимчасовий файл стирається.
    val shotFile = remember { java.io.File(context.cacheDir, "camera").apply { mkdirs() }.let { java.io.File(it, "shot.jpg") } }
    val shotUri = remember { androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".camera", shotFile) }
    var cameraFor by remember { mutableStateOf<Long?>(null) }
    var askCameraFor by remember { mutableStateOf<Long?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val t = askCameraFor
        askCameraFor = null
        if (ok && t != null) cameraFor = t else if (!ok) vm.show(R.string.camera_permission)
    }
    fun shoot(target: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraFor = target
        else { askCameraFor = target; vm.keepOpenOnce = true; cameraPermission.launch(Manifest.permission.CAMERA) }
    }
    fun shotTaken(target: Long, bitmap: android.graphics.Bitmap) {
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { shotFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) } }.getOrDefault(false)
            }
            bitmap.recycle()
            when {
                !ok -> { shotFile.delete(); vm.show(R.string.camera_failed) }
                target == COVER_TARGET -> vm.setCover(info.id, shotUri, shotFile)
                else -> vm.setPhoto(target, shotUri, shotFile)
            }
        }
    }
    var backupPassword by remember { mutableStateOf<String?>(null) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = backupPassword
        backupPassword = null
        if (uri != null && pw != null) vm.backup(uri, pw)
    }

    val host = object : PageHost {
        override val vm: DiaryViewModel = vm
        override val bookCoords: LayoutCoordinates? get() = bookCoords
        override fun textSink(day: Long, part: Int) = TextSink { input, cursor ->
            val d = vm.dayNow(day)
            if (!input.startsWith(d.sealed) || input.length > Limits.DAY_TEXT) return@TextSink false
            // Порожні рядки після курсора — вільне місце, як на папері: текст займає найближчий порожній рядок, а не
            // впирається в кінець дня.
            val full = absorbBlankLines(input, cursor) { t -> splitDay(d, t).fits }
            val split = splitDay(d, full)
            if (!split.fits) {
                // Стирати можна завжди — навіть коли день уже переповнений; не можна лише додавати понад місце.
                val old = splitDay(d)
                if (split.lines > old.lines) { vm.show(R.string.day_full); return@TextSink false }
            }
            vm.editText(day, full)
            if (part == 0 && d.torn and 2 == 0 && split.split < full.length && cursor >= split.split) {
                // Сторінка закінчилась — переходимо на продовження дня, курсор там само.
                focusReq = Triple(day, 1, cursor)
                pendingFlip = BookPage.DayPart(day, 1)
            } else if (part == 1 && d.torn and 1 == 0 && (cursor < split.split || split.split >= full.length)) {
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
        override fun addPhoto(day: Long, source: Int) {
            when (source) {
                0 -> shoot(day)
                else -> { photoFor = day; vm.keepOpenOnce = true; pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
        }
        override fun recordVoice(day: Long) = openRecorder(day)
        override fun dismissSlot(day: Long, photo: Boolean) = vm.dismissSlot(day, photo)
        override fun joinBack(day: Long, at: Int) {
            // Backspace на початку другої сторінки — стерти останній символ першої (якщо він не висох).
            val d = vm.dayNow(day)
            if (at <= d.sealed.length || at > d.text.length) return
            textSink(day, 1).change(d.text.removeRange(at - 1, at), at - 1)
        }
    }

    /** Перша сторінка меню: щоденник — підпис до фото дня, обкладинка, назва, замок, копія (щоденні дії — на панелі внизу). */
    @Composable
    fun androidx.compose.foundation.layout.ColumnScope.DiaryMenu() {
        val back = menuReturn
        MenuEntries(buildList {
            val day = (back as? BookPage.DayPart)?.day
            if (open != null) {
                if (day != null && vm.day(day).photo && !DiaryViewModel.isDry(day)) {
                    add(MenuEntry(null, stringResource(R.string.menu_page)))
                    add(MenuEntry(Glyph.Edit, stringResource(R.string.photo_caption)) { dialog = BookDialog.Caption(day) })
                }
                add(MenuEntry(null, stringResource(R.string.menu_diary)))
                add(MenuEntry(Glyph.Photo, stringResource(if (info.cover) R.string.cover_photo_change else R.string.cover_photo)) { vm.keepOpenOnce = true; pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                add(MenuEntry(Glyph.Shutter, stringResource(R.string.cover_from_camera)) { shoot(COVER_TARGET) })
                if (info.cover) add(MenuEntry(Glyph.Close, stringResource(R.string.cover_photo_remove)) { vm.removeCover(info.id) })
                add(MenuEntry(Glyph.Edit, stringResource(R.string.rename)) { dialog = BookDialog.Rename })
                add(MenuEntry(Glyph.Lock, stringResource(if (info.locked) R.string.lock_change else R.string.lock_set)) { dialog = BookDialog.LockSet })
                if (info.locked) add(MenuEntry(Glyph.Unlock, stringResource(R.string.lock_remove)) { dialog = BookDialog.LockRemove })
                add(MenuEntry(Glyph.Backup, stringResource(R.string.backup)) { dialog = BookDialog.Backup })
            } else {
                // Закритий на код: лише полиця й «забув код» (решта налаштувань — після коду: їх бачить лише власник).
                add(MenuEntry(null, stringResource(R.string.locked_settings_hint)))
                add(MenuEntry(Glyph.Grid, stringResource(R.string.shelf_and_settings)) { onShelf() })
                if (info.locked) add(MenuEntry(Glyph.Delete, stringResource(R.string.forgot_code), Tone.Danger) { dialog = BookDialog.Forgot })
            }
        }.map { e -> MenuEntry(e.glyph, e.text, e.tone) { closeMenu(); e.onClick() } })
    }

    /** Друга сторінка меню: налаштування цього щоденника, полиця, стерти (найнебезпечніше — найнижче). */
    @Composable
    fun androidx.compose.foundation.layout.ColumnScope.DiarySettings() {
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SwitchRow(stringResource(R.string.settings_photos), info.photos, subtitle = stringResource(R.string.settings_photos_desc)) { vm.setPhotosOn(info.id, it) }
            SwitchRow(stringResource(R.string.settings_voice), info.voice, subtitle = stringResource(R.string.settings_voice_desc)) { vm.setVoiceOn(info.id, it) }
            SwitchRow(stringResource(R.string.settings_carry_place), info.carryPlace, subtitle = stringResource(R.string.settings_carry_place_desc)) { vm.setCarryPlace(info.id, it) }
            SwitchRow(stringResource(R.string.settings_open_today), !info.openFirst, subtitle = stringResource(R.string.settings_open_today_desc)) { vm.setOpenFirst(info.id, !it) }
        }
        Text(stringResource(R.string.drying_title), color = Oled.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
        FieldHint(stringResource(R.string.drying_fixed), Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        MenuEntries(listOf(
            MenuEntry(null, stringResource(R.string.menu_more)),
            MenuEntry(Glyph.Grid, stringResource(R.string.shelf_and_settings)) { closeMenu(); onShelf() },
            MenuEntry(Glyph.Delete, stringResource(R.string.erase), Tone.Danger) { closeMenu(); dialog = BookDialog.Erase },
        ))
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
            recording != null -> stopRecording()
            photoTools -> photoTools = false
            ink != null -> ink = null
            menuOpen -> closeMenu()
            searchOpen -> closeSearch()
            editing != null -> focusManager.clearFocus()
            flip.index > 0 -> scope.launch { currentKey = BookPage.Cover; flip.flipTo(0) }
            else -> onShelf()
        }
    }

    val gear = rememberGearReveal()
    Column(Modifier.fillMaxSize().background(Oled.Black).statusBarsPadding().navigationBarsPadding().revealsGear(gear)) {
        // Книга. Коли з'являється клавіатура, вся сторінка (папір, лінії й текст разом) підсувається вгору, щоб рядок
        // з курсором був над клавіатурою, — як коли підсовуєш щоденник. Тло лишається на місці.
        val ime = WindowInsets.ime
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp)
                .onGloballyPositioned { bookTop = it.positionInWindow().y },
        ) {
            val availW = maxWidth - 16.dp
            val availH = maxHeight - RibbonBottom
            val size = with(density) { pageSize(androidx.compose.ui.geometry.Size(availW.toPx(), availH.toPx())) }
            val pw = with(density) { size.width.toDp() }
            val ph = with(density) { size.height.toDp() }
            LaunchedEffect(size.width) { pageW = size.width }
            LaunchedEffect(pw, ph) { pageDp = androidx.compose.ui.unit.DpSize(pw, ph) }
            // Книжка — посередині вільного місця (вона вужча за екран, тож зверху й знизу лишається тло).
            val top = ((maxHeight - ph - RibbonBottom) / 2).coerceAtLeast(0.dp)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = top)
                    .graphicsLayer {
                        val imeH = ime.getBottom(this).toFloat()
                        val rootH = view.rootView.height.toFloat()
                        val topOfPage = bookTop + top.toPx()
                        val visible = rootH - imeH - 20.dp.toPx()
                        translationY = if (editing != null && imeH > 0f) -(topOfPage + cursorBottom.value - visible).coerceAtLeast(0f) else 0f
                    }
                    .size(pw, ph + RibbonBottom),
            ) {
                Box(
                    Modifier
                        .size(pw, ph)
                        .onGloballyPositioned {
                            bookCoords = it
                            com.blacklaunch.diary.ui.NoticeArea.rect = it.boundsInWindow()
                        },
                ) {
                    FlipBook(
                        state = flip,
                        count = pages.size,
                        // Гортати можна й під час письма: щойно сторінка рушила — клавіатура ховається.
                        enabled = ink == null && recording == null,
                        heavy = { pages.getOrNull(it) == BookPage.Cover },
                        paperSeed = { i ->
                            when (val pg = pages.getOrNull(i)) {
                                is BookPage.DayPart -> pageSeed(pg.day, pg.part)
                                BookPage.Cover -> COVER_SEED + info.tint
                                else -> 0
                            }
                        },
                        canFlipForward = { i ->
                            // Пошук гортається вперед — назад на ту сторінку, з якої прийшли: коло замикається.
                            if (pages.getOrNull(i) is BookPage.Recorder) pages.indexOf(recorderReturn) >= 0
                            else if (pages.getOrNull(i).isSearch()) pages.indexOf(searchReturn) >= 0 else run {
                            val next = if (i == 0) openTarget else i + 1
                            // У меню заходять лише шестернею; усередині меню — гортання між його сторінками.
                            val here = pages.getOrNull(i)
                            val there = pages.getOrNull(next)
                            !(i == 0 && open == null) && (here is BookPage.Menu) == (there is BookPage.Menu) &&
                                !there.isSearch() && there !is BookPage.Recorder
                            }
                        },
                        nextOf = { i ->
                            when {
                                i == 0 -> openTarget
                                pages.getOrNull(i) is BookPage.Recorder -> pages.indexOf(recorderReturn).coerceAtLeast(0)
                                // Пошук: далі — наступна сторінка знахідок, а після останньої — туди, звідки прийшли.
                                pages.getOrNull(i).isSearch() -> if (sameRun(pages.getOrNull(i), pages.getOrNull(i + 1))) i + 1 else pages.indexOf(searchReturn).coerceAtLeast(0)
                                else -> i + 1
                            }
                        },
                        // Крізь скляну обкладинку видно першу сторінку щоденника; гортнеш — відкривається сьогоднішня.
                        peekOf = { i -> if (i == 0 && pages.size > 1) 1 else null },
                        backOf = { top, under -> if (top == 0 && under > 1) under - 1 else null },
                        prevOf = { i ->
                            when (pages.getOrNull(i)) {
                                BookPage.Menu(0) -> pages.indexOf(menuReturn).coerceAtLeast(0)
                                BookPage.Search -> pages.indexOf(searchReturn).coerceAtLeast(0)
                                is BookPage.Recorder -> pages.indexOf(recorderReturn).coerceAtLeast(0)
                                // Найдавніший місяць — назад на пошук.
                                is BookPage.Calendar -> if (pages.getOrNull(i - 1) is BookPage.Calendar) i - 1 else pages.indexOf(BookPage.Search).coerceAtLeast(0)
                                else -> i - 1
                            }
                        },
                        onStart = { focusManager.clearFocus() },
                        blankUnder = {
                            val day = currentDay ?: today
                            DayPage(
                                host = host, day = com.blacklaunch.diary.data.Day(day), part = 1, split = 0,
                                photoSlot = false, audioSlot = false, pageWidthPx = size.width, hours = hours,
                                highlight = null, ink = null, yearsAgo = null, editable = false,
                            )
                        },
                        blocked = { blockers.values.toList() },
                        onFlipped = { i ->
                            currentKey = pages.getOrNull(i) ?: BookPage.Cover
                            if (currentKey !is BookPage.Menu) menuOpen = false
                            if (currentKey !is BookPage.Recorder) recorderFor = null
                            // Магнітофон, відкритий із пошуку, — частина пошуку: назад повертаєшся до знахідок.
                            val inSearchRecorder = currentKey is BookPage.Recorder && recorderReturn.isSearch()
                            if (!currentKey.isSearch() && !inSearchRecorder) { searchOpen = false; calendarOpen = false }
                            else if (currentKey !is BookPage.Calendar) calendarOpen = false
                            highlight = highlight?.takeIf { h -> (pages.getOrNull(i) as? BookPage.DayPart)?.day == h.first } },
                    ) { i ->
                        when (val p = pages.getOrNull(i)) {
                            BookPage.Cover, null -> {
                                val photo = if (info.cover) vm.coverBitmap(info.id, glass) else null
                                CoverFace(
                                    name = info.name,
                                    photo = photo,
                                    pattern = info.tint,
                                    locked = info.locked,
                                    titleY = info.titleY,
                                    onTitleMoved = { vm.setTitleY(info.id, it) },
                                    onTitleTap = if (open != null) ({ dialog = BookDialog.Rename }) else null,
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
                                Column(Modifier.fillMaxSize().padding(bottom = 22.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Підказка не висить постійно — з'являється, якщо обкладинку довго не гортають.
                                    if (open != null) {
                                        Row(Modifier.graphicsLayer { alpha = hintAlpha }.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (info.locked) GlyphIcon(Glyph.Unlock, null, tint = Oled.TextSecondary, size = 18.dp)
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.cover_turn_hint), color = Oled.TextSecondary, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                            BookPage.Search, is BookPage.SearchMore -> {
                                val n = (p as? BookPage.SearchMore)?.n ?: 0
                                PaperPage(
                                    title = stringResource(R.string.search),
                                    subtitle = info.name,
                                    seed = info.tint * 7 + 41 + n * 13,
                                    footer = searchFooter(searchFound, resultSlices, n),
                                ) {
                                    SearchPage(
                                        vm = vm,
                                        onRecorder = { d -> focusManager.clearFocus(); openRecorder(d) },
                                        onCalendar = {
                                            focusManager.clearFocus()
                                            calendarOpen = true
                                            pendingFlip = BookPage.Calendar(java.time.YearMonth.now().ym())
                                        },
                                        ui = searchUi,
                                        found = searchFound,
                                        slice = resultSlices.getOrNull(n) ?: IntRange.EMPTY,
                                        first = n == 0,
                                        active = currentPage == BookPage.Search && !flip.moving,
                                        onOpen = { h -> focusManager.clearFocus(); jumpToDay(h.day, if (h.start >= 0) h.start until h.end else null) },
                                    )
                                }
                            }
                            is BookPage.Recorder -> {
                                val d = vm.day(p.day)
                                RecorderPage(
                                    vm = vm,
                                    day = d,
                                    title = stringResource(R.string.recorder_title),
                                    subtitle = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy", java.util.Locale.getDefault()).format(LocalDate.ofEpochDay(p.day)),
                                    seed = info.tint * 7 + 71,
                                    editable = !DiaryViewModel.isDry(p.day),
                                    recording = recording?.takeIf { it.day == p.day },
                                    onRecord = { side, from, to -> if (d.audio || d.audioB || roomFor(p.day, photo = false)) record(p.day, side, from, to) else vm.show(R.string.no_room_voice) },
                                    onStopRecording = { stopRecording() },
                                    onPauseRecording = { pauseRecording(it) },
                                    voiceMode = info.voiceMode,
                                    onVoiceMode = { vm.setVoiceMode(info.id, it) },
                                    onShare = { side -> shareTape(p.day, side) },
                                    host = host,
                                )
                            }
                            is BookPage.Calendar -> PaperPage(
                                title = monthTitle(java.time.YearMonth.of(p.ym / 12, p.ym % 12 + 1)),
                                subtitle = info.name,
                                seed = info.tint * 7 + 59 + p.ym * 3,
                            ) {
                                CalendarMonth(java.time.YearMonth.of(p.ym / 12, p.ym % 12 + 1), open?.days?.filterValues { !it.isBlank }?.keys.orEmpty()) { day ->
                                    jumpToDay(day)
                                }
                            }
                            is BookPage.Menu -> PaperPage(
                                title = if (p.n == 0) info.name else stringResource(R.string.settings_this_diary),
                                subtitle = if (p.n == 0) stringResource(R.string.menu_diary_desc) else info.name,
                                seed = info.tint * 7 + 3 + p.n * 17,
                                footer = if (p.n == 0 && open != null) stringResource(R.string.menu_next) else null,
                            ) { if (p.n == 0) DiaryMenu() else DiarySettings() }
                            is BookPage.DayPart -> {
                                val d = vm.day(p.day).dried(System.currentTimeMillis(), hours)
                                val (sp, sa) = slotsFor(d)
                                val split = splitDay(d)
                                val years = if (p.day == today) {
                                    val t = LocalDate.ofEpochDay(today)
                                    dayList.lastOrNull { it < today && LocalDate.ofEpochDay(it).let { x -> x.monthValue == t.monthValue && x.dayOfMonth == t.dayOfMonth } }
                                } else null
                                DayPage(
                                    host = host,
                                    day = d,
                                    part = p.part,
                                    split = split.split,
                                    photoSlot = sp,
                                    audioSlot = sa,
                                    pageWidthPx = size.width,
                                    hours = hours,
                                    highlight = highlight?.takeIf { it.first == p.day }?.second,
                                    ink = ink,
                                    yearsAgo = years,
                                    editable = true,
                                    number = pageNumber[p],
                                    ribbon = { 0f },
                                    // Клаптик попередньої вирваної — поверх сторінки; поки цю виривають, він лежить окремо (зверху).
                                    stub = p in stubs && !(p == currentPage && (flip.tearFinger != null || stubFold > 0f)),
                                    tearFinger = if (p == currentPage) ({ flip.tearFinger }) else null,
                                )
                            }
                        }
                    }
                }
                // Вирвати сторінку — затиснути нижній лівий кут (біля корінця) і вести вгору: сторінка відривається за
                // пальцем, під нею видно наступну. За половину — червоне попередження; довів до кінця — вирвано назавжди;
                // передумав — розрив лишається, заклеєний скотчем (запам'ятовується найвища межа).
                val tearPage = currentPage as? BookPage.DayPart
                if (open != null && tearPage != null) {
                    val stubHere = tearPage in stubs
                    val renew = !DiaryViewModel.isDry(tearPage.day)
                    Canvas(Modifier.size(pw, ph).clip(androidx.compose.foundation.shape.RoundedCornerShape(BookCorner))) {
                        if (flip.tearFinger == null && stubFold <= 0f) return@Canvas
                        // Клаптик раніше вирваної — зверху, але відгорнутий уліво (до корінця), щоб не плутався з розривом.
                        if (stubHere) {
                            // Гортається навколо корінця, як сторінка: ширина — косинус кута; стає ребром біля корінця
                            // (за корінець, поза сторінку, не малюємо), трохи темніє, поки повернутий від світла.
                            val sf: Float = stubFold
                            // Повністю стає ребром — і за корінцем його не видно: жодних стиснутих рисочок біля краю.
                            val angle = sf * (Math.PI.toFloat() / 2f)
                            val c = kotlin.math.cos(angle)
                            if (c > 0.04f) withTransform({ scale(c, 1f, pivot = Offset.Zero) }) {
                                drawTornStub((tearPage.day * 7 + tearPage.part).toInt(), glass)
                                if (sf > 0f) drawRect(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.10f * sf), 1f to Color.Black.copy(alpha = 0.30f * sf), startX = 0f, endX = TEAR_X.toPx()),
                                    size = androidx.compose.ui.geometry.Size(TEAR_X.toPx() + 6.dp.toPx(), size.height),
                                )
                            }
                        }
                    }
                    fun Modifier.tearInput(zoneH: androidx.compose.ui.unit.Dp): Modifier = this
                            .pointerInput(tearPage, ink, zoneH) {
                                if (ink != null) return@pointerInput
                                // Наскільки вже надірвано — читаємо щоразу на початку жесту (після «передумав» межа зростає).
                                var already = 0f
                                val w = pw.toPx()
                                val h = ph.toPx()
                                val zoneTop = h - zoneH.toPx()
                                // «Тріск паперу»: короткі слабкі поштовхи, лише коли розрив іде вгору далі, ніж уже було в цьому
                                // жесті (назад — тиша); крок і сила трохи нерівні, як волокна.
                                var peak = 0f
                                var visPeak = 0f
                                var tapes = emptyList<Float>()
                                var peeled = BooleanArray(0)
                                var nextStep = 0.015f
                                val rnd = java.util.Random()
                                var warned = false
                                fun follow(pos: Offset) {
                                    // Рух — лише вгору: кут іде рівно, легенько набік (вліво-вправо палець не смикає сторінку).
                                    val fy = (zoneTop + pos.y).coerceIn(-h * 0.2f, h)
                                    val f = Offset((h - fy) * 0.38f, fy)
                                    val g = TearGeometry(w, h, f)
                                    flip.tearFinger = f
                                    // Розрив — за тим, наскільки підняли палець (до кінця — на ~80 % висоти сторінки), але не нижче,
                                    // ніж уже загнуто; уже надірване не «зростається» — межа не нижча за колишню.
                                    val k = maxOf(already, g.tearHeight, ((h - f.y) / (h * 0.8f)).coerceIn(0f, 1f))
                                    flip.tear = k
                                    // Скільки розірвано насправді — де лінія загину перетинає корінець (так і малюється, і
                                    // запам'ятовується). Вібрація — лише на новому папері: вище за все, що вже було розірвано
                                    // (раніше чи в цьому ж жесті).
                                    val torn = g.tearHeight
                                    if (torn > visPeak) visPeak = torn
                                    if (torn > peak + nextStep) {
                                        peak = torn
                                        nextStep = 0.008f + rnd.nextFloat() * 0.02f
                                        haptics.tear(0.2f + rnd.nextFloat() * 0.45f)
                                    }
                                    // Скотч відклеюється — коли лінія загину проходить його середину (раз на кожен шматочок).
                                    val foldY = h * (1f - torn)
                                    tapes.forEachIndexed { i, cy -> if (!peeled[i] && foldY < cy) { peeled[i] = true; haptics.tick() } }
                                    if (!warned && k >= 0.5f) { warned = true; haptics.longPress(); vm.warn(R.string.tear_hint) }
                                    if (warned && k < 0.45f) { warned = false; vm.clearNotice(R.string.tear_hint) }
                                }
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        warned = false
                                        already = vm.day(tearPage.day).tearTry(tearPage.part)
                                        peak = already
                                        visPeak = 0f
                                        tapes = tapeLayout(already, tearSeedOf(tearPage.day, tearPage.part), h, 1.dp.toPx()).map { it.first }
                                        peeled = BooleanArray(tapes.size)
                                        flip.tearSeed = tearSeedOf(tearPage.day, tearPage.part)
                                        flip.tapeK = already
                                        flip.tearRenew = renew
                                        flip.tapeSeed = tearSeedOf(tearPage.day, tearPage.part)
                                        flip.tapeMargin = PageMetrics.MARGIN_LEFT * w / PageMetrics.WIDTH
                                        focusManager.clearFocus()
                                        follow(pos)
                                    },
                                    onDrag = { c, _ -> c.consume(); follow(c.position) },
                                    onDragEnd = {
                                        vm.clearNotice(R.string.tear_hint)
                                        scope.launch {
                                            val k = flip.tear
                                            val from = flip.tearFinger ?: Offset(0f, h)
                                            if (k >= 0.92f) {
                                                // Вирвали: кут іде далі вгору-праворуч, аркуш відходить увесь і тане.
                                                haptics.longPress()
                                                val to = Offset(w * 1.9f, -h * 0.5f)
                                                androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { v, _ ->
                                                    flip.tearFinger = from + (to - from) * v
                                                    flip.tear = 1f
                                                    flip.tearFall = ((v - 0.55f) / 0.45f).coerceIn(0f, 1f)
                                                }
                                                val d = vm.day(tearPage.day)
                                                vm.tearPage(tearPage.day, tearPage.part, splitDay(d).split)
                                                delay(30)
                                            } else {
                                                // Передумали: кут лягає назад, розрив лишається (заклеєний скотчем).
                                                // Запам'ятовується рівно те, що видно: до лінії загину (а не за підйомом пальця).
                                                if (visPeak > 0.02f) vm.setTearTry(tearPage.day, tearPage.part, visPeak)
                                                val home = Offset(0f, h)
                                                androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(320)) { v, _ ->
                                                    flip.tearFinger = from + (home - from) * v
                                                }
                                            }
                                            flip.tearFinger = null
                                            flip.tearFall = 0f
                                            flip.tear = 0f
                                        }
                                    },
                                    onDragCancel = { vm.clearNotice(R.string.tear_hint); flip.tearFinger = null; flip.tear = 0f },
                                )
                            }
                    // Де брати: кутик біля корінця (вище, у полі) і смужка вздовж низу майже до номера сторінки (нижче
                    // за останній рядок — дотики до тексту не забирає).
                    listOf(52.dp to 84.dp, (pw / 2 - 30.dp) to 46.dp).forEach { (zw, zh) ->
                        Box(Modifier.offset(y = ph - zh).size(zw, zh).tearInput(zh))
                    }
                }
                // Закладка-пошук — коротка стрічка згори біля корінця, поверх усього (і клаптиків вирваних): потягнув униз —
                // пошук.
                if (open != null && currentPage is BookPage.DayPart) {
                    SearchRibbon(
                        gear = gear,
                        pageW = pw,
                        pageH = ph,
                        enabled = ink == null && !flip.moving && flip.tearFinger == null,
                        onOpen = { openSearch() },
                        description = stringResource(R.string.search),
                    )
                }
            }
        }

        // Шестерня — завжди в одному місці внизу (як і на полиці); з'являється після дотику. Поки йде запис голосу — на її
        // місці завжди видно «стоп» із часом: зупинити запис можна звідусіль.
        if (ink == null) {
            Box(Modifier.fillMaxWidth().height(GearRow), contentAlignment = Alignment.Center) {
                // На магнітофоні час запису вже видно на приладі — тут не дублюємо.
                val rec = recording?.takeIf { currentPage !is BookPage.Recorder }
                if (rec != null) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Color(0x33B0244F))
                            .clickable { stopRecording() }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlyphIcon(Glyph.Stop, stringResource(R.string.record_stop), tint = Color(0xFFE0607E), size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        val sec = rec.elapsed / 1000
                        Text("%d:%02d".format(sec / 60, sec % 60), color = Oled.TextPrimary, fontSize = 15.sp)
                    }
                } else if (open != null && currentDay != null) {
                    // Нижня панель — щоденні дії під рукою, без меню. «Фото» розгортає камеру / галерею / файл.
                    val day = currentDay
                    val part = (currentPage as? BookPage.DayPart)?.part ?: 0
                    val d = vm.day(day)
                    val dry = DiaryViewModel.isDry(day)
                    fun photo(src: Int) { photoTools = false; if (roomFor(day, photo = true) || d.photo) host.addPhoto(day, src) else vm.show(R.string.no_room_photo) }
                    val tools = if (photoTools) listOf(
                        Tool(Glyph.Shutter, stringResource(R.string.photo_from_camera)) { photo(0) },
                        Tool(Glyph.Gallery, stringResource(R.string.photo_from_gallery)) { photo(1) },
                        Tool(Glyph.Close, stringResource(R.string.close)) { photoTools = false },
                    ) else listOf(
                        Tool(Glyph.Pen, stringResource(R.string.ink_mode)) { focusManager.clearFocus(); ink = InkSession(day, part) },
                        Tool(Glyph.Photo, stringResource(R.string.tool_photo)) {
                            when {
                                d.photo -> dialog = BookDialog.Photo(day)
                                dry -> vm.show(R.string.page_dry)
                                else -> photoTools = true
                            }
                        },
                        Tool(Glyph.Mic, stringResource(R.string.record)) {
                            when {
                                dry && !d.audio && !d.audioB -> vm.show(R.string.page_dry)
                                d.audio || d.audioB || roomFor(day, photo = false) -> openRecorder(day)
                                else -> vm.show(R.string.no_room_voice)
                            }
                        },
                        // Шестерня — на полицю: там усі щоденники, налаштування кожного й загальні.
                        Tool(Glyph.Settings, stringResource(R.string.shelf)) { onShelf() },
                    )
                    ToolPanel(gear, tools, pinned = photoTools)
                } else {
                    GearSpot(gear) { if (menuOpen) closeMenu() else onShelf() }
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


    if (searchOpen) SearchShow(vm, searchUi, searchFound)
    cameraFor?.let { target ->
        com.blacklaunch.diary.ui.SmoothDialog(onDismissRequest = { cameraFor = null }) {
            DiaryCamera(
                onShot = { bmp -> cameraFor = null; shotTaken(target, bmp) },
                onClose = { cameraFor = null },
                onFail = { cameraFor = null; vm.show(R.string.camera_failed) },
            )
        }
    }

    // ——— Вікна ———
    when (val dl = dialog) {
        null -> Unit
        is BookDialog.Place -> {
            val recent = remember { open?.days?.values?.sortedByDescending { it.day }?.map { it.place }?.filter { it.isNotBlank() }?.distinct().orEmpty() }
            TextDialog(
                title = stringResource(R.string.place),
                initial = vm.day(dl.day).place.ifEmpty { vm.carriedPlace(dl.day).orEmpty() },
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
        }, onRetake = { shoot(dl.day) }, onDelete = { vm.removePhoto(dl.day) }, onCaption = { dialog = BookDialog.Caption(dl.day) })
        is BookDialog.Caption -> TextDialog(
            title = stringResource(R.string.photo_caption),
            initial = vm.day(dl.day).caption,
            placeholder = stringResource(R.string.photo_caption_hint),
            limit = Limits.CAPTION,
            allowEmpty = true,
            onDismiss = { dialog = null },
        ) { vm.setCaption(dl.day, it) }
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
        BookDialog.Backup -> PasswordDialog(restore = false, wrong = false, onDismiss = { dialog = null }) { pw ->
            backupPassword = pw
            val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now())
            vm.keepOpenOnce = true; createBackup.launch("${info.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").ifBlank { "diary" }}-$stamp.diary")
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
private fun PhotoViewer(vm: DiaryViewModel, day: Long, onDismiss: () -> Unit, onReplace: () -> Unit, onRetake: () -> Unit, onDelete: () -> Unit, onCaption: () -> Unit) {
    val haptics = LocalHaptics.current
    var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var menu by remember { mutableStateOf(false) }
    val glass = AppStyle.glass
    LaunchedEffect(day, glass) { image = vm.photo(day, glass) }
    com.blacklaunch.diary.ui.SmoothDialog(onDismissRequest = onDismiss) {
        // Збільшене фото — те саме, що на сторінці (той самий фільтр, ті самі краї), без жодних рамок довкола.
        // «Аркуш» — на тому самому старому папері, «Скло» — на чорному.
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
                Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    image?.let {
                        StyledPhoto(it, glass, Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height.coerceAtLeast(1)), androidx.compose.ui.layout.ContentScale.Fit, caption = vm.day(day).caption, seed = day, dry = DiaryViewModel.isDry(day))
                    }
                }
                // Унизу — закрити, поруч шестерня (замінити чи прибрати фото).
                Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Spacer(Modifier.size(44.dp))
                    Box(Modifier.size(56.dp).clip(CircleShape).clickable { haptics.tap(); onDismiss() }, contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.Close, stringResource(R.string.close), tint = if (glass) Oled.TextPrimary else SheetInk)
                    }
                    // Висохлий день — лише перегляд: ні підпису, ні заміни, ні видалення.
                    if (!DiaryViewModel.isDry(day)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { haptics.tap(); menu = true }, contentAlignment = Alignment.Center) {
                            GlyphIcon(Glyph.Settings, stringResource(R.string.menu), tint = Oled.TextSecondary, size = 24.dp)
                        }
                    } else {
                        Spacer(Modifier.size(44.dp))
                    }
                }
            }
        }
    }
    if (menu) {
        MenuDialog(null, listOf(
            MenuEntry(Glyph.Edit, stringResource(R.string.photo_caption)) { onDismiss(); onCaption() },
            MenuEntry(Glyph.Shutter, stringResource(R.string.photo_retake)) { onDismiss(); onRetake() },
            MenuEntry(Glyph.Gallery, stringResource(R.string.photo_replace)) { onDismiss(); onReplace() },
            MenuEntry(Glyph.Delete, stringResource(R.string.photo_remove), Tone.Danger) { onDismiss(); onDelete() },
        ), onDismiss = { menu = false })
    }
}

/**
 * Панель малювання: угорі — інструменти (твердий і м'який олівці, ручка, маркер, гумка), «назад» / «вперед», «Готово»;
 * нижче — товщина (три розміри) і вигляд лінії (суцільна, пунктир, крапки, хвиля) чи, для гумки, точна / м'яка.
 */
@Composable
private fun InkToolbar(s: InkSession, onUndo: () -> Unit, onRedo: () -> Unit, onDone: () -> Unit) {
    val haptics = LocalHaptics.current
    val glass = AppStyle.glass
    val ink = if (glass) Oled.TextPrimary else SheetInk
    @Composable
    fun Chip(selected: Boolean, desc: String, seed: Int, modifier: Modifier = Modifier, onClick: () -> Unit, draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
        Box(
            modifier
                .height(40.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .pencilFrame(12.dp, seed = seed, alpha = if (selected) 0.95f else 0.3f, ink = if (selected) com.blacklaunch.diary.ui.SelectedInk else SheetInk)
                .semantics { contentDescription = desc }
                .clickable { haptics.tap(); onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(30.dp, 22.dp)) { draw(if (selected) ink else Oled.TextSecondary) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.ink_hint), color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            EqualWidthRow(Modifier.weight(1f), gap = 5.dp) {
                InkTool.entries.forEach { t ->
                    val label = stringResource(when (t) {
                        InkTool.Hard -> R.string.ink_hard
                        InkTool.Soft -> R.string.ink_soft
                        InkTool.Pen -> R.string.ink_pen
                        InkTool.Marker -> R.string.ink_marker
                        InkTool.Eraser -> R.string.ink_eraser
                    })
                    Chip(s.tool == t, label, t.ordinal + 41, onClick = { s.tool = t }) { c -> drawToolGlyph(t, c) }
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = s.undo.isNotEmpty()) { haptics.tap(); onUndo() }, contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Undo, stringResource(R.string.undo), tint = Oled.TextPrimary.copy(alpha = if (s.undo.isNotEmpty()) 1f else 0.3f))
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = s.redo.isNotEmpty()) { haptics.tap(); onRedo() }, contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Redo, stringResource(R.string.redo), tint = Oled.TextPrimary.copy(alpha = if (s.redo.isNotEmpty()) 1f else 0.3f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            EqualWidthRow(Modifier.weight(1f), gap = 5.dp) {
                // Товщина — три кружки різного розміру.
                for (k in 0..2) {
                    s.sizeTick
                    Chip(s.size == k, stringResource(R.string.ink_size), 60 + k, onClick = { s.size = k }) { c ->
                        drawCircle(c, size.minDimension * (0.12f + k * 0.1f), center)
                    }
                }
                if (s.tool == InkTool.Eraser) {
                    Chip(!s.softEraser, stringResource(R.string.ink_eraser_hard), 70, onClick = { s.softEraser = false }) { c ->
                        drawCircle(c, size.minDimension * 0.34f, center)
                    }
                    Chip(s.softEraser, stringResource(R.string.ink_eraser_soft), 71, onClick = { s.softEraser = true }) { c ->
                        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.copy(alpha = 0.8f), Color.Transparent), center = center, radius = size.minDimension * 0.45f), size.minDimension * 0.45f, center)
                    }
                } else {
                    for (st in 0..3) {
                        Chip(s.style == st, stringResource(R.string.ink_style), 80 + st, onClick = { s.style = st }) { c -> drawStyleGlyph(st, c) }
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            SheetButton(stringResource(R.string.done), onDone, tone = Tone.Confirm)
        }
    }
}

/** Значок інструмента на панелі. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawToolGlyph(t: InkTool, c: Color) {
    val w = size.width
    val h = size.height
    val st = 1.6.dp.toPx()
    when (t) {
        InkTool.Eraser -> {
            rotate(-30f, center) {
                drawRoundRect(c, Offset(w * 0.22f, h * 0.28f), androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.44f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(st))
                drawLine(c, Offset(w * 0.42f, h * 0.28f), Offset(w * 0.42f, h * 0.72f), st)
            }
        }
        InkTool.Marker -> {
            rotate(-35f, center) {
                drawRoundRect(c, Offset(w * 0.32f, h * 0.05f), androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.62f), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(st))
                drawRect(c, Offset(w * 0.38f, h * 0.67f), androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.22f))
            }
        }
        else -> {
            // Олівець / ручка: тіло й кінчик; м'який — товстіший слід, твердий — тонкий.
            rotate(-35f, center) {
                val bw = w * 0.2f
                drawRect(c, Offset(w * 0.5f - bw / 2, h * 0.02f), androidx.compose.ui.geometry.Size(bw, h * 0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(st))
                val tip = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f - bw / 2, h * 0.62f); lineTo(w * 0.5f + bw / 2, h * 0.62f); lineTo(w * 0.5f, h * 0.92f); close()
                }
                if (t == InkTool.Pen) drawPath(tip, c) else drawPath(tip, c, style = androidx.compose.ui.graphics.drawscope.Stroke(st))
            }
            val trail = when (t) { InkTool.Hard -> 1.dp.toPx(); InkTool.Soft -> 3.dp.toPx(); else -> 2.dp.toPx() }
            drawLine(c.copy(alpha = if (t == InkTool.Hard) 0.6f else 0.9f), Offset(w * 0.05f, h * 0.95f), Offset(w * 0.4f, h * 0.95f), trail, cap = StrokeCap.Round)
        }
    }
}

/** Значок вигляду лінії: суцільна, пунктир, крапки, хвиля. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStyleGlyph(style: Int, c: Color) {
    val y = size.height / 2
    val st = 2.dp.toPx()
    when (style) {
        0 -> drawLine(c, Offset(0f, y), Offset(size.width, y), st, cap = StrokeCap.Round)
        1 -> drawLine(c, Offset(0f, y), Offset(size.width, y), st, cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        2 -> drawLine(c, Offset(st, y), Offset(size.width, y), st, cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(0.01f, 5.dp.toPx())))
        else -> {
            val path = androidx.compose.ui.graphics.Path()
            val amp = size.height * 0.22f
            var x = 0f
            path.moveTo(0f, y)
            while (x <= size.width) {
                path.lineTo(x, y + amp * kotlin.math.sin(x / size.width * 4f * Math.PI.toFloat()))
                x += 1f
            }
            drawPath(path, c, style = androidx.compose.ui.graphics.drawscope.Stroke(st, cap = StrokeCap.Round))
        }
    }
}

/** День, на якому відкриється щоденник: закладка (якщо так налаштовано й вона є) чи сьогодні. */
/** Щоденник завжди відкривається на сьогоднішньому дні (закладки-позначки більше немає — вона стала пошуком). */
@Suppress("UNUSED_PARAMETER")
private fun openTargetDay(days: List<Long>, today: Long, bookmark: Long?, atBookmark: Boolean): Long = today
