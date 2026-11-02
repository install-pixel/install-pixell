package com.blacklaunch.qrcodes.label

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.blacklaunch.qrcodes.ui.bouncyScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.data.QrCode
import com.blacklaunch.qrcodes.qr.CodeFormat
import com.blacklaunch.qrcodes.qr.codeFormat
import com.blacklaunch.qrcodes.ui.Glyph
import com.blacklaunch.qrcodes.ui.GlyphIcon
import com.blacklaunch.qrcodes.ui.LocalHaptics
import com.blacklaunch.qrcodes.ui.SheetButton
import com.blacklaunch.qrcodes.ui.SheetDialog
import com.blacklaunch.qrcodes.ui.SheetInk
import com.blacklaunch.qrcodes.ui.SheetTextField
import com.blacklaunch.qrcodes.ui.Tone
import com.blacklaunch.qrcodes.ui.MenuRow
import com.blacklaunch.qrcodes.ui.RoundButton
import androidx.compose.animation.togetherWith
import com.blacklaunch.qrcodes.ui.OPEN_MS
import com.blacklaunch.qrcodes.ui.CLOSE_MS
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.blacklaunch.qrcodes.ui.backdrop
import com.blacklaunch.qrcodes.ui.backdropAmount
import androidx.compose.ui.graphics.graphicsLayer
import com.blacklaunch.qrcodes.ui.pencilFrame
import com.blacklaunch.qrcodes.ui.outline
import com.blacklaunch.qrcodes.ui.theme.Oled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * «Друк і поділитися»: наклейка в масштабі на чорному «аркуші», як уся програма. Елементи (коди, свій текст, значки)
 * тягнеш пальцем, розмір — двома пальцями або повзунком; вони прилипають до центру й країв. Друкується — чорне на
 * білому, точного розміру (PDF через службу друку Android, для Zebra — Zebra Print Service), або картинкою PNG.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelScreen(start: List<QrCode>, saved: List<QrCode>, onClose: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val prefs = remember { LabelPrefs(context) }
    val renderer = remember { LabelRenderer.create(context) }
    val defaults = remember { prefs.load() }
    var captionDefault by remember { mutableStateOf(defaults.caption) }
    val tw: (String, Float) -> Float = renderer::textWidth
    val noRoom = stringResource(R.string.label_no_room)
    val scanMe = stringResource(R.string.label_scan_me)

    fun newQr(id: Int, code: QrCode, size: LabelSize): Element.Qr {
        val barcode = code.codeFormat == CodeFormat.BARCODE
        return Element.Qr(
            id = id,
            cx = size.widthMm / 2,
            cy = size.heightMm / 2,
            content = code.content,
            title = code.title,
            // Штрихкод — ширший (смуги не мають бути надто тонкими), QR — останній вибраний розмір.
            side = if (barcode) minOf(DEFAULT_BAR_MM, size.widthMm - 12f) else defaults.qrMm.coerceAtMost(LabelLayout.maxQrMm(size, captionDefault)),
            caption = captionDefault,
            barcode = barcode,
            kind = code.format,
        )
    }

    // Чернетка: якщо минулого разу тут була наклейка з цими самими кодами — повертаємо її, як була.
    val draftStore = remember { DraftStore(context) }
    val restored = remember {
        val draft = draftStore.load()
        val wanted = start.map { it.content }.toSet()
        draft?.takeIf { d -> wanted.isNotEmpty() && d.elements.filterIsInstance<Element.Qr>().map { it.content }.toSet().containsAll(wanted) }
    }
    var design by remember {
        val first = LabelDesign(size = defaults.size)
        // Серія (багато кодів) — на наклейці перший як зразок макета; друк — кожен код окремою наклейкою.
        val onSheet = if (start.size > MAX_ON_SHEET) start.take(1) else start
        val withCodes = onSheet.foldIndexed(first) { i, d, code -> d.add(newQr(i + 1, code, d.size)) }
        // Кілька кодів — одразу сіткою, кожен на своєму місці.
        mutableStateOf(restored ?: if (onSheet.size > 1) Arrange.grid(withCodes, tw) else withCodes)
    }
    val draftRestored = stringResource(R.string.label_draft_restored)
    LaunchedEffect(Unit) {
        if (restored != null) onMessage(draftRestored)
        // Старі фото фону, якими вже ніщо не користується, — геть (у фоні, щоб не гальмувати).
        withContext(Dispatchers.IO) { BackgroundFiles.cleanup(context) }
        // Кожна зміна — у чернетку (не частіше, ніж раз на півсекунди).
        androidx.compose.runtime.snapshotFlow { design }.collectLatest { d ->
            kotlinx.coroutines.delay(DRAFT_SAVE_MS)
            withContext(Dispatchers.IO) { draftStore.save(d) }
        }
    }
    // «Крок назад»: попередні стани наклейки.
    val history = remember { ArrayDeque<LabelDesign>() }
    var canUndo by remember { mutableStateOf(false) }
    var clashing by remember { mutableStateOf(false) }
    var clearAsk by remember { mutableStateOf(false) }
    var captionEdit by remember { mutableStateOf<Element.Qr?>(null) }
    var bannerEdit by remember { mutableStateOf<Element.Qr?>(null) }
    var selectedId by remember { mutableStateOf(design.elements.singleOrNull()?.id) }
    var dragging by remember { mutableStateOf(false) }
    var overZone by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var frameOpen by remember { mutableStateOf(false) }
    var templatesOpen by remember { mutableStateOf(false) }
    var templateNameOpen by remember { mutableStateOf(false) }
    val templateStore = remember { TemplateStore(context) }
    var savedTemplates by remember { mutableStateOf(templateStore.load()) }
    var hiddenBuiltIns by remember { mutableStateOf(templateStore.hiddenBuiltIns()) }
    // Свій шаблон, який зараз відкрито: «Зберегти як шаблон» пропонує ту саму назву (збереже поверх).
    var openedTemplate by remember { mutableStateOf<String?>(null) }
    var guides by remember { mutableStateOf<Pair<Float?, Float?>>(null to null) }
    var gapMarks by remember { mutableStateOf<List<Snap.Gap>>(emptyList()) }
    var sizeOpen by remember { mutableStateOf(false) }
    var textEdit by remember { mutableStateOf<Element.Text?>(null) }
    var textNew by remember { mutableStateOf(false) }
    var stickerFor by remember { mutableStateOf<Element.Icon?>(null) }
    var stickerNew by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    // Не null — вибір коду замінює цей код на аркуші, а не додає новий.
    var replaceFor by remember { mutableStateOf<Element.Qr?>(null) }
    var busy by remember { mutableStateOf(false) }
    var zoneRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    // Нижня панель: «Додати» чи «Змінити» (вибрав елемент — сама перемикається на «Змінити»).
    var editTab by remember { mutableStateOf(false) }
    var bgMenu by remember { mutableStateOf(false) }
    var printChoice by remember { mutableStateOf(false) }
    // Тло вибране (як елемент): тягнеш — пересуваєш, двома пальцями — масштаб і поворот.
    var bgSelected by remember { mutableStateOf(false) }
    // Гумка по фону: водиш пальцем по наклейці — картинка під пальцем затирається (кольором паперу).
    var bgErase by remember { mutableStateOf(false) }
    // Малювання прямо на наклейці: штрихи в мм, поки не натиснеш «Готово».
    var drawMode by remember { mutableStateOf(false) }
    val drawStrokes = remember { androidx.compose.runtime.mutableStateListOf<List<Offset>>() }
    var drawThick by remember { mutableStateOf(false) }
    var drawErase by remember { mutableStateOf(false) }
    var drawEditId by remember { mutableStateOf<Int?>(null) }

    val selected = design.elements.firstOrNull { it.id == selectedId }
    // Кому «Рамка» міняє рамку коду: вибраному коду, а якщо код один — йому.
    val frameTarget = (selected as? Element.Qr) ?: design.elements.filterIsInstance<Element.Qr>().singleOrNull()

    // Розмір наклейки, розмір коду й підпис — на наступний раз.
    LaunchedEffect(design.size, (selected as? Element.Qr)?.side, captionDefault) {
        val qr = selected as? Element.Qr
        // Підпис як типовий запам'ятовуємо лише тоді, коли його міняють руками (не після сцени чи шаблону).
        prefs.save(LabelSettings(design.size, qr?.takeIf { !it.barcode }?.side ?: defaults.qrMm, 1, captionDefault))
    }
    LaunchedEffect(bgSelected) { if (!bgSelected) bgErase = false }
    // Вибрав елемент на наклейці — панель сама показує «Змінити».
    LaunchedEffect(selectedId) { if (selectedId != null) editTab = true }
    /** Почати малювати на наклейці: новий малюнок або домалювати наявний (його штрихи — у мм наклейки). */
    fun startDraw(edit: Element.Drawing?) {
        drawStrokes.clear()
        drawErase = false
        drawThick = edit?.thick ?: false
        drawEditId = edit?.id
        if (edit != null) {
            val w = edit.width
            val h = edit.width * edit.aspect
            val a = Math.toRadians(edit.rotation.toDouble())
            val c = kotlin.math.cos(a).toFloat()
            val sn = kotlin.math.sin(a).toFloat()
            edit.strokes.forEach { st ->
                drawStrokes.add(st.chunked(2).filter { it.size == 2 }.map { (u, v) ->
                    val x = (u - 0.5f) * w
                    val y = (v - 0.5f) * h
                    Offset(edit.cx + x * c - y * sn, edit.cy + x * sn + y * c)
                })
            }
        }
        selectedId = null
        bgSelected = false
        drawMode = true
    }

    // Клавіатура з головного екрана (поле в капсулі) тут не потрібна — ховаємо її, щоб не стискала наклейку.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    BackHandler(enabled = drawMode) { drawMode = false; drawStrokes.clear() }
    BackHandler(onBack = onClose)

    fun pushHistory(old: LabelDesign) {
        history.addLast(old)
        if (history.size > MAX_UNDO) history.removeFirst()
        canUndo = true
    }

    fun commit(next: LabelDesign) {
        if (next == design) return
        pushHistory(design)
        design = next
    }

    fun undo() {
        val prev = history.removeLastOrNull() ?: return
        design = prev
        canUndo = history.isNotEmpty()
        if (design.elements.none { it.id == selectedId }) selectedId = null
    }

    /** Під час перетягування — без перевірок (перевіряємо, коли відпустили). */
    fun update(e: Element) {
        design = design.replace(e)
    }

    /**
     * Зміна з кнопок (розмір, рамка, підпис…): якщо після неї елемент вилазить за край чи налазить на інший —
     * шукаємо йому найближче вільне місце; немає — зміну не робимо й кажемо чому.
     */
    fun tryChange(e: Element) {
        val placed = if (Placement.fits(design, e, tw)) e else Placement.nearestFree(design, e, tw)
        if (placed == null) {
            haptics.longPress()
            onMessage(noRoom)
        } else {
            commit(design.replace(placed))
        }
    }

    /** Новий елемент — на найближче до [wantX], [wantY] вільне місце (типово — центр наклейки). */
    fun addElement(e: Element, wantX: Float = design.size.widthMm / 2, wantY: Float = design.size.heightMm / 2) {
        val placed = Placement.nearestFree(design, e, tw, wantX, wantY)
        if (placed == null) {
            haptics.longPress()
            onMessage(noRoom)
            return
        }
        commit(design.add(placed))
        selectedId = placed.id
    }

    val jobName = stringResource(R.string.app_name)
    val shareTitle = stringResource(R.string.share)
    val codes = design.elements.filterIsInstance<Element.Qr>()

    // Фон: своє зображення з галереї — копіюємо в пам'ять програми (щоб не зникло, коли фото видалять).
    val bgFailed = stringResource(R.string.label_bg_failed)
    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyBackground(context, uri) }
                busy = false
                if (path != null) {
                    commit(design.copy(background = BgImage(path, TemplateStore.imageAspect(path), 0f, 0f, 1f).fitted(design.size)))
                    selectedId = null
                    bgSelected = true
                    editTab = true
                } else {
                    onMessage(bgFailed)
                }
            }
        }
    }
    fun pickBackground() {
        pickImage.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val dialogOpen = printChoice || bgMenu || templatesOpen || templateNameOpen || frameOpen || shareOpen || sizeOpen || textNew || textEdit != null || stickerNew || stickerFor != null || pickerOpen || clearAsk || captionEdit != null || bannerEdit != null
    val blur = backdropAmount(dialogOpen)
    Box(Modifier.fillMaxSize().background(Oled.Black).backdrop(blur)) {
    // Без підлаштування під клавіатуру: на цьому екрані вона не потрібна, і наклейка не стрибає (поля вводу — у вікнах).
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { haptics.tap(); onClose() }, contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Back, stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.label_header),
                color = Oled.TextPrimary,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, end = 8.dp).weight(1f),
            )
            // Дві головні дії — круглими кнопками в шапці, як у всій програмі: поділитися й надрукувати (синя — головна).
            RoundButton(Glyph.Share, stringResource(R.string.share), { shareOpen = true }, enabled = !busy && design.elements.isNotEmpty(), size = 46.dp)
            Spacer(Modifier.width(10.dp))
            RoundButton(Glyph.Print, stringResource(R.string.print), {
                // Відкрили з кількома кодами — спитати: цю наклейку чи кожен код окремою наклейкою.
                if (start.size > 1 && design.elements.any { it is Element.Qr }) printChoice = true else renderer.print(context, jobName, design, 1)
            }, ink = Oled.Confirm, enabled = design.elements.isNotEmpty(), size = 46.dp)
            Spacer(Modifier.width(8.dp))
        }

        // Наклейка.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val maxW = with(density) { maxWidth.toPx() }
            val maxH = with(density) { maxHeight.toPx() }
            val k = minOf(maxW / design.size.widthMm, maxH / design.size.heightMm)
            val w = with(density) { (design.size.widthMm * k).toDp() }
            val h = with(density) { (design.size.heightMm * k).toDp() }
            val current by rememberUpdatedState(design)
            var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
            var lastTap by remember { mutableStateOf(0L to -1) }
            Canvas(
                Modifier
                    .size(w, h)
                    // Чорний аркуш «Екран телефону» на чорному тлі — тонкою рамкою, щоб було видно межі.
                    .then(if (design.size.screen) Modifier.border(1.dp, SheetInk.copy(alpha = 0.35f)) else Modifier)
                    .onGloballyPositioned { canvasOrigin = it.positionInRoot() }
                    .pointerInput(k) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (drawMode) {
                                // Малюємо (чи стираємо) прямо на наклейці, у мм.
                                val first = down.position / k
                                if (drawErase) {
                                    fun erase(p: Offset) {
                                        val r = DRAW_ERASER_MM
                                        val next = ArrayList<List<Offset>>()
                                        drawStrokes.forEach { st ->
                                            var run = ArrayList<Offset>()
                                            st.forEach { pt ->
                                                if ((pt - p).getDistance() < r) {
                                                    if (run.isNotEmpty()) next.add(run)
                                                    run = ArrayList()
                                                } else {
                                                    run.add(pt)
                                                }
                                            }
                                            if (run.isNotEmpty()) next.add(run)
                                        }
                                        if (next.sumOf { it.size } != drawStrokes.sumOf { it.size }) {
                                            drawStrokes.clear()
                                            drawStrokes.addAll(next)
                                        }
                                    }
                                    erase(first)
                                    do {
                                        val event = awaitPointerEvent()
                                        event.changes.firstOrNull()?.let { ch -> if (ch.pressed) { erase(ch.position / k); ch.consume() } }
                                    } while (event.changes.any { it.pressed })
                                } else {
                                    val pts = mutableListOf(first)
                                    drawStrokes.add(pts.toList())
                                    val index = drawStrokes.lastIndex
                                    do {
                                        val event = awaitPointerEvent()
                                        event.changes.firstOrNull()?.let { ch ->
                                            if (ch.pressed) {
                                                // Густо — щоб гумка стирала рівно.
                                                val p = ch.position / k
                                                val last = pts.last()
                                                val steps = ((p - last).getDistance() / 0.4f).toInt()
                                                for (i in 1..steps) pts.add(last + (p - last) * (i / (steps + 1f)))
                                                pts.add(p)
                                                drawStrokes[index] = pts.toList()
                                                ch.consume()
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                                return@awaitEachGesture
                            }
                            val d = current
                            val hit = d.hit(down.position.x / k, down.position.y / k, 10.dp.toPx() / k, renderer::textWidth)
                            if (bgErase && bgSelected && d.background != null) {
                                // Гумка: штрих у частках картинки (рухатиметься разом з нею).
                                selectedId = null
                                val bgE = d.background!!
                                val stroke = ArrayList<Float>()
                                fun addPoint(p: Offset) {
                                    val (fx, fy) = bgE.fractionOf(p.x / k, p.y / k)
                                    stroke.add(fx)
                                    stroke.add(fy)
                                    design = current.copy(background = bgE.copy(erased = bgE.erased + listOf(stroke.toList())))
                                }
                                addPoint(down.position)
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.firstOrNull()?.let { ch ->
                                        if (ch.pressed) {
                                            addPoint(ch.position)
                                            ch.consume()
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                pushHistory(d)
                                return@awaitEachGesture
                            }
                            val bgNow = d.background
                            // Фон: вибраний — будь-який жест рухає його (дотик без руху по елементу — вибрати елемент);
                            // не вибраний — двома пальцями по наклейці теж масштабуєш і повертаєш фон.
                            if (bgNow != null && (bgSelected || hit == null)) {
                                var bx = bgNow.cx
                                var by = bgNow.cy
                                var bw = bgNow.width
                                var br = bgNow.rotation
                                var travel = 0f
                                var movedBg = false
                                var fingers = 1
                                do {
                                    val event = awaitPointerEvent()
                                    fingers = maxOf(fingers, event.changes.count { it.pressed })
                                    val pan = event.calculatePan()
                                    val zoom = event.calculateZoom()
                                    val turn = event.calculateRotation()
                                    travel += pan.getDistance()
                                    val active = bgSelected || fingers >= 2
                                    if (active && (travel > viewConfiguration.touchSlop || zoom != 1f || turn != 0f)) {
                                        // Збільшення й поворот — навколо точки між пальцями (як фото в галереї).
                                        val newW = (bw * zoom).coerceIn(5f, BG_MAX_WIDTH_MM)
                                        val z = newW / bw
                                        val cen = event.calculateCentroid(useCurrent = true)
                                        if (cen != Offset.Unspecified && (z != 1f || turn != 0f)) {
                                            val px = cen.x / k
                                            val py = cen.y / k
                                            val a = Math.toRadians(turn.toDouble())
                                            val c = kotlin.math.cos(a).toFloat()
                                            val sn = kotlin.math.sin(a).toFloat()
                                            val dx = (bx - px) * z
                                            val dy = (by - py) * z
                                            bx = px + dx * c - dy * sn
                                            by = py + dx * sn + dy * c
                                        }
                                        bx += pan.x / k
                                        by += pan.y / k
                                        bw = newW
                                        br += turn
                                        design = current.copy(background = bgNow.copy(cx = bx, cy = by, width = bw, rotation = normalizeAngle(br, snap = true)))
                                        if (!movedBg) {
                                            selectedId = null
                                            bgSelected = true
                                            editTab = true
                                        }
                                        movedBg = true
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                                if (movedBg) {
                                    pushHistory(d)
                                } else if (hit != null) {
                                    // Дотик по елементу — вибрати його (фон перестає бути вибраним).
                                    bgSelected = false
                                    selectedId = hit.id
                                } else {
                                    bgSelected = false
                                    selectedId = null
                                }
                                return@awaitEachGesture
                            }
                            selectedId = hit?.id
                            if (hit != null) bgSelected = false
                            if (hit == null) return@awaitEachGesture
                            haptics.tap()
                            val before = d
                            var moved = false
                            var wantX = hit.cx
                            var wantY = hit.cy
                            var size = hit.size
                            var angle = hit.rotation
                            var lastGuides: Pair<Float?, Float?> = null to null
                            var lastGaps: List<Snap.Gap> = emptyList()
                            // Інші елементи — щоб ставити рівно в ряд і з однаковими проміжками.
                            val others = d.elements.filter { it.id != hit.id }.map { it.bounds(renderer::textWidth) }
                            var finger = down.position
                            do {
                                val event = awaitPointerEvent()
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                val turn = event.calculateRotation()
                                event.changes.firstOrNull { it.pressed }?.let { finger = it.position }
                                val travelled = (finger - down.position).getDistance() > viewConfiguration.touchSlop
                                if ((pan != Offset.Zero && (moved || travelled)) || zoom != 1f || turn != 0f) {
                                    val now = current.elements.firstOrNull { it.id == hit.id } ?: break
                                    if (!moved) dragging = true
                                    wantX += pan.x / k
                                    wantY += pan.y / k
                                    angle += turn
                                    val changed = now.resized(size * zoom).rotated(normalizeAngle(angle, snap = true))
                                    size = changed.size
                                    val snap = Snap.place(wantX, wantY, changed.bounds(renderer::textWidth), changed, current.size, edge = Placement.edge(current), others = others)
                                    val next = changed.moved(snap.cx, snap.cy)
                                    update(next)
                                    moved = true
                                    val over = zoneRect.contains(canvasOrigin + finger)
                                    if (over != overZone) haptics.tick()
                                    overZone = over
                                    clashing = !over && !Placement.fits(current, next, renderer::textWidth)
                                    val g = snap.vertical to snap.horizontal
                                    val newly = (g != lastGuides && (g.first != null || g.second != null)) || (snap.gaps.isNotEmpty() && snap.gaps != lastGaps)
                                    if (newly) haptics.tick()
                                    lastGuides = g
                                    lastGaps = snap.gaps
                                    guides = g
                                    gapMarks = snap.gaps
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            guides = null to null
                            gapMarks = emptyList()
                            clashing = false
                            dragging = false
                            if (moved && overZone) {
                                // Кинули на «Видалити».
                                overZone = false
                                haptics.longPress()
                                design = before.remove(hit.id)
                                pushHistory(before)
                                selectedId = null
                            } else if (moved) {
                                // Відпустили: якщо налізло на інший чи вилізло — на найближче вільне місце; немає — назад.
                                val now = current.elements.firstOrNull { it.id == hit.id }
                                if (now != null) {
                                    val placed = if (Placement.fits(current, now, renderer::textWidth)) now else Placement.nearestFree(current, now, renderer::textWidth)
                                    if (placed == null) {
                                        design = before
                                        haptics.longPress()
                                        onMessage(noRoom)
                                    } else {
                                        design = current.replace(placed)
                                        pushHistory(before)
                                    }
                                }
                            } else {
                                // Подвійний дотик — змінити: текст, підпис коду, значок.
                                val t = System.currentTimeMillis()
                                if (lastTap.second == hit.id && t - lastTap.first < DOUBLE_TAP_MS) {
                                    lastTap = 0L to -1
                                    when (hit) {
                                        is Element.Text -> textEdit = hit
                                        is Element.Qr -> captionEdit = hit
                                        is Element.Icon -> stickerFor = hit
                                        is Element.Drawing -> startDraw(hit)
                                    }
                                } else {
                                    lastTap = t to hit.id
                                }
                            }
                        }
                    },
            ) {
                val editingId = drawEditId
                val shown = if (drawMode && editingId != null) design.remove(editingId) else design
                drawIntoCanvas { renderer.draw(it.nativeCanvas, k, shown, LabelPalette.forView(design)) }
                if (drawMode) {
                    val inkColor = androidx.compose.ui.graphics.Color(LabelPalette.forView(design).ink)
                    val pen = (if (drawThick) DRAW_THICK_MM else DRAW_THIN_MM) * k
                    drawStrokes.forEach { st ->
                        if (st.isEmpty()) return@forEach
                        if (st.size == 1) {
                            drawCircle(inkColor, pen / 2, st[0] * k)
                        } else {
                            val path = androidx.compose.ui.graphics.Path()
                            path.moveTo(st[0].x * k, st[0].y * k)
                            st.drop(1).forEach { path.lineTo(it.x * k, it.y * k) }
                            drawPath(path, inkColor, style = androidx.compose.ui.graphics.drawscope.Stroke(pen, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        }
                    }
                }
                // Лінії, до яких прилипло.
                guides.first?.let { x -> drawLine(PAPER_GUIDE, Offset(x * k, 0f), Offset(x * k, size.height), 1.dp.toPx()) }
                guides.second?.let { y -> drawLine(PAPER_GUIDE, Offset(0f, y * k), Offset(size.width, y * k), 1.dp.toPx()) }
                // Однакові проміжки: відрізок з «вусиками» на кінцях між сусідами — видно, що відстані рівні.
                gapMarks.forEach { g ->
                    val tick = 4.dp.toPx()
                    val stroke = 1.5.dp.toPx()
                    if (g.horizontal) {
                        val y = g.at * k
                        drawLine(PAPER_SELECT, Offset(g.from * k, y), Offset(g.to * k, y), stroke)
                        drawLine(PAPER_SELECT, Offset(g.from * k, y - tick), Offset(g.from * k, y + tick), stroke)
                        drawLine(PAPER_SELECT, Offset(g.to * k, y - tick), Offset(g.to * k, y + tick), stroke)
                    } else {
                        val x = g.at * k
                        drawLine(PAPER_SELECT, Offset(x, g.from * k), Offset(x, g.to * k), stroke)
                        drawLine(PAPER_SELECT, Offset(x - tick, g.from * k), Offset(x + tick, g.from * k), stroke)
                        drawLine(PAPER_SELECT, Offset(x - tick, g.to * k), Offset(x + tick, g.to * k), stroke)
                    }
                }
                // Вибране тло — тонкою сірою рамкою по межах картинки (разом з поворотом).
                val bgSel = design.background
                if (bgSelected && bgSel != null) {
                    rotate(bgSel.rotation, Offset(bgSel.cx * k, bgSel.cy * k)) {
                        val bw = bgSel.width * k
                        val bh = bgSel.height * k
                        drawRect(
                            PAPER_SELECT,
                            topLeft = Offset(bgSel.cx * k - bw / 2, bgSel.cy * k - bh / 2),
                            size = Size(bw, bh),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                        )
                    }
                }
                // Вибраний — у синій рамці олівцем (повернутій разом з ним); налазить — червоній.
                selected?.let { e ->
                    val b = e.baseBounds(renderer::textWidth)
                    val pad = 2.dp.toPx()
                    val bw = b.width * k + 2 * pad
                    val bh = b.height * k + 2 * pad
                    rotate(e.rotation, Offset(e.cx * k, e.cy * k)) {
                        translate(b.left * k - pad, b.top * k - pad) {
                            outline(Offset.Zero, Size(bw, bh), 6.dp.toPx(), e.id + 400, if (clashing || overZone) Oled.Danger else PAPER_SELECT, 2.dp.toPx())
                        }
                    }
                }
            }
        }

        // Керування: розмір аркуша й дві вкладки — «Додати» (що поставити на наклейку) і «Змінити» (вибране або весь аркуш).
        Column(Modifier.fillMaxWidth().heightIn(max = 430.dp).bouncyScroll().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Обидві вкладки однакової ширини — перемикання нічого не зсуває.
                com.blacklaunch.qrcodes.ui.ChoiceChip(stringResource(R.string.label_tab_add), !editTab, Modifier.widthIn(min = TAB_WIDTH)) { editTab = false }
                Spacer(Modifier.width(8.dp))
                com.blacklaunch.qrcodes.ui.ChoiceChip(stringResource(R.string.label_tab_edit), editTab, Modifier.widthIn(min = TAB_WIDTH)) { editTab = true }
                Spacer(Modifier.weight(1f))
                SheetButton(
                    if (design.size.screen) stringResource(R.string.label_screen) else "${LabelRenderer.fmt(design.size.widthMm)}×${LabelRenderer.fmt(design.size.heightMm)} mm",
                    { sizeOpen = true },
                    compact = true,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            if (drawMode) {
                // Поки малюєш: товщина / гумка й дії; наклейка вгорі — це й є полотно.
                Column(Modifier.fillMaxWidth().height(TAB_AREA_HEIGHT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.label_draw_hint), color = Oled.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        com.blacklaunch.qrcodes.ui.ChoiceChip(stringResource(R.string.label_draw_thin), !drawThick && !drawErase) { drawThick = false; drawErase = false }
                        com.blacklaunch.qrcodes.ui.ChoiceChip(stringResource(R.string.label_draw_thick), drawThick && !drawErase) { drawThick = true; drawErase = false }
                        com.blacklaunch.qrcodes.ui.ChoiceChip(stringResource(R.string.label_eraser), drawErase) { drawErase = true }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Tool(Glyph.Undo, stringResource(R.string.label_undo), Modifier.weight(1f), enabled = drawStrokes.isNotEmpty()) { drawStrokes.removeLastOrNull() }
                        Tool(Glyph.Delete, stringResource(R.string.label_clear), Modifier.weight(1f), enabled = drawStrokes.isNotEmpty(), danger = true) { drawStrokes.clear() }
                        Tool(Glyph.Close, stringResource(R.string.cancel), Modifier.weight(1f)) { drawMode = false; drawStrokes.clear() }
                        Tool(Glyph.Save, stringResource(R.string.done), Modifier.weight(1f)) {
                            val editing = drawEditId
                            val base = if (editing != null) design.remove(editing) else design
                            val pts = drawStrokes.flatten()
                            if (pts.isEmpty()) {
                                // Усе стерли — малюнка немає.
                                if (editing != null) commit(base)
                            } else {
                                val pen = if (drawThick) DRAW_THICK_MM else DRAW_THIN_MM
                                val minX = pts.minOf { it.x }
                                val maxX = pts.maxOf { it.x }
                                val minY = pts.minOf { it.y }
                                val maxY = pts.maxOf { it.y }
                                val bw = (maxX - minX).coerceAtLeast(1f)
                                val bh = (maxY - minY).coerceAtLeast(1f)
                                val norm = drawStrokes.map { st -> st.flatMap { listOf((it.x - minX) / bw, (it.y - minY) / bh) } }
                                val el = Element.Drawing(editing ?: design.nextId, (minX + maxX) / 2, (minY + maxY) / 2, norm, bh / bw, bw, drawThick, line = pen / bw)
                                // Там, де намалював; налізло на код чи за край — на найближче вільне місце.
                                val placed = if (Placement.fits(base, el, tw)) el else Placement.nearestFree(base, el, tw, el.cx, el.cy)
                                if (placed == null) {
                                    onMessage(noRoom)
                                    return@Tool
                                }
                                commit(base.add(placed))
                                selectedId = placed.id
                            }
                            drawMode = false
                            drawStrokes.clear()
                        }
                    }
                }
            } else androidx.compose.animation.AnimatedContent(
                targetState = editTab,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
                },
                label = "labelTab",
                // Стала висота під обидві вкладки: панель не стрибає, коли перемикаєш чи вибираєш елемент.
                modifier = Modifier.fillMaxWidth().height(TAB_AREA_HEIGHT),
                contentAlignment = Alignment.TopCenter,
            ) { edit ->
                if (!edit) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Tool(Glyph.ScanQr, stringResource(R.string.label_add_code), Modifier.weight(1f)) { replaceFor = null; pickerOpen = true }
                            Tool(Glyph.TextT, stringResource(R.string.label_add_text), Modifier.weight(1f)) { textNew = true }
                            Tool(Glyph.Star, stringResource(R.string.label_add_icon), Modifier.weight(1f)) { stickerFor = null; stickerNew = true }
                            Tool(Glyph.Edit, stringResource(R.string.label_drawing), Modifier.weight(1f)) { startDraw(null) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Tool(Glyph.Gallery, stringResource(R.string.label_background), Modifier.weight(1f), on = design.background != null) { bgMenu = true }
                            Tool(Glyph.Template, stringResource(R.string.label_templates), Modifier.weight(1f)) { templatesOpen = true }
                            Tool(Glyph.Frame, stringResource(R.string.label_frame), Modifier.weight(1f), on = design.border != QrFrame.None || (frameTarget?.frame ?: QrFrame.None) != QrFrame.None) { frameOpen = true }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    when (val e = selected) {
                        null -> if (bgSelected && design.background != null) {
                            val bg = design.background!!
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(if (bgErase) R.string.label_bg_erasing else R.string.label_bg_selected), color = Oled.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Tool(Glyph.Frame, stringResource(R.string.label_bg_fit), Modifier.weight(1f)) { commit(design.copy(background = bg.fitted(design.size))) }
                                    Tool(Glyph.Grid, stringResource(R.string.label_bg_fill), Modifier.weight(1f)) { commit(design.copy(background = bg.filled(design.size))) }
                                    Tool(Glyph.RotateLeft, stringResource(R.string.label_rotate_left), Modifier.weight(1f)) { commit(design.copy(background = bg.copy(rotation = normalizeAngle(bg.rotation - 90f, snap = true)))) }
                                    Tool(Glyph.RotateRight, stringResource(R.string.label_rotate_right), Modifier.weight(1f)) { commit(design.copy(background = bg.copy(rotation = normalizeAngle(bg.rotation + 90f, snap = true)))) }
                                    Tool(Glyph.Center, stringResource(R.string.label_center), Modifier.weight(1f)) { commit(design.copy(background = bg.copy(cx = design.size.widthMm / 2, cy = design.size.heightMm / 2))) }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Tool(Glyph.Gallery, stringResource(R.string.label_bg_other), Modifier.weight(1f)) { bgMenu = true }
                                    Tool(Glyph.Undo, stringResource(R.string.label_undo), Modifier.weight(1f), enabled = canUndo) { undo() }
                                    Tool(Glyph.Close, stringResource(R.string.label_bg_remove_short), Modifier.weight(1f)) {
                                        commit(design.copy(background = null))
                                        bgSelected = false
                                    }
                                    Tool(Glyph.Eraser, stringResource(R.string.label_eraser), Modifier.weight(1f), on = bgErase) { bgErase = !bgErase }
                                    Tool(Glyph.Save, stringResource(R.string.done), Modifier.weight(1f)) { bgSelected = false; bgErase = false }
                                }
                            }
                        } else {
                            Column {
                                Text(
                                    stringResource(R.string.label_hint),
                                    color = Oled.TextSecondary,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                )
                                // Нічого не вибрано — дії з усім аркушем; «Фон» — вибрати тло, щоб рухати його.
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Tool(Glyph.Grid, stringResource(R.string.label_grid), Modifier.weight(1f), enabled = codes.size > 1) { commit(Arrange.grid(design, tw)) }
                                    Tool(Glyph.Undo, stringResource(R.string.label_undo), Modifier.weight(1f), enabled = canUndo) { undo() }
                                    if (design.background != null) Tool(Glyph.Gallery, stringResource(R.string.label_background), Modifier.weight(1f)) { bgSelected = true }
                                    Tool(Glyph.Delete, stringResource(R.string.delete), Modifier.weight(1f), enabled = design.elements.isNotEmpty(), danger = true) { clearAsk = true }
                                }
                            }
                        }
                        else -> SelectedPanel(
                            element = e,
                            design = design,
                            renderer = renderer,
                            onChange = ::tryChange,
                            onCenter = {
                                val b = e.bounds(tw)
                                val offY = (b.top + b.bottom) / 2 - e.cy
                                val cx = design.size.widthMm / 2
                                val cy = design.size.heightMm / 2 - offY
                                val placed = Placement.nearestFree(design, e, tw, cx, cy)
                                if (placed == null) onMessage(noRoom) else commit(design.replace(placed))
                            },
                            onCopy = {
                                val copy = when (e) {
                                    is Element.Qr -> e.copy(id = design.nextId)
                                    is Element.Text -> e.copy(id = design.nextId)
                                    is Element.Icon -> e.copy(id = design.nextId)
                                    is Element.Drawing -> e.copy(id = design.nextId)
                                }
                                // Копія — поруч з оригіналом, на вільному місці.
                                addElement(copy, e.cx, e.cy)
                            },
                            onDelete = {
                                commit(design.remove(e.id))
                                selectedId = null
                            },
                            onFill = { qr -> commit(Arrange.fill(design, qr, tw)) },
                            onEditCaption = { captionEdit = it },
                            onEditBanner = { bannerEdit = it },
                            onReplace = { replaceFor = it; pickerOpen = true },
                            onReplaceIcon = { stickerFor = it },
                            onEditDrawing = { startDraw(it) },
                            onEditText = { textEdit = it },
                            canUndo = canUndo,
                            onUndo = { undo() },
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
            // Плашка «Видалити» — на місці шапки, поки тягнеш (як «Прибрати» в Black Glass): не закриває верх наклейки.
            androidx.compose.animation.AnimatedVisibility(
                visible = dragging,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(OPEN_MS)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(CLOSE_MS)),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 2.dp, start = 12.dp, end = 12.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .height(56.dp)
                        .onGloballyPositioned { zoneRect = it.boundsInRoot().inflate(24f) }
                        .background(if (overZone) Oled.Danger.copy(alpha = 0.25f) else Oled.Black.copy(alpha = 0.92f), RoundedCornerShape(28.dp))
                        .pencilFrame(28.dp, seed = 17, ink = if (overZone) Oled.Danger else SheetInk),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.delete), color = Oled.Danger, fontSize = 16.sp)
                }
            }
    }

    if (printChoice) {
        SheetDialog(onDismissRequest = { printChoice = false }, title = stringResource(R.string.print_choice_title)) {
            Column {
                MenuRow(Glyph.Print, stringResource(R.string.print_this), {
                    printChoice = false
                    renderer.print(context, jobName, design, 1)
                })
                MenuRow(Glyph.Grid, stringResource(R.string.print_each, start.size), {
                    printChoice = false
                    // Кожен код — окрема наклейка за цим макетом (усі коди на ній стають цим кодом).
                    val pages = start.map { code ->
                        val q = newQr(0, code, design.size)
                        design.copy(elements = design.elements.map { e -> if (e is Element.Qr) e.copy(content = q.content, title = q.title, barcode = q.barcode, kind = q.kind) else e })
                    }
                    renderer.printPages(context, jobName, pages)
                })
                Text(stringResource(R.string.print_each_hint), color = Oled.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            }
        }
    }
    if (bgMenu) {
        val sceneCodes = codes.ifEmpty { listOfNotNull(start.firstOrNull()?.let { newQr(1, it, design.size) }) }
        SheetDialog(onDismissRequest = { bgMenu = false }, title = stringResource(R.string.label_background)) {
            Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuRow(Glyph.Gallery, stringResource(R.string.label_bg_own), {
                    bgMenu = false
                    pickBackground()
                })
                if (sceneCodes.isNotEmpty()) {
                    // Сцени: малюнок із місцями під коди — коди самі стають у квадратики.
                    Text(stringResource(R.string.label_scenes), color = Oled.TextPrimary, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Scenes.all.forEach { scene ->
                            val hgt = (96f * scene.aspect).coerceIn(64f, 150f).dp
                            SampleBox(null, selected = design.background?.source == scene.source, onClick = {
                                commit(Scenes.apply(scene, design, sceneCodes))
                                selectedId = null
                                bgSelected = true
                                editTab = true
                                openedTemplate = null
                                bgMenu = false
                            }, width = 96.dp, height = hgt) { canvas, w, h -> renderer.drawScenePreview(canvas, w, h, scene) }
                        }
                    }
                }
                if (design.background != null) {
                    MenuRow(Glyph.Close, stringResource(R.string.label_bg_remove), {
                        bgMenu = false
                        bgSelected = false
                        commit(design.copy(background = null))
                    })
                }
                Text(stringResource(R.string.label_bg_hint), color = Oled.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }

    if (templatesOpen) {
        // Код для готових шаблонів: вибраний, інакше перший на аркуші, інакше той, з яким відкрили друк.
        val code = (selected as? Element.Qr) ?: codes.firstOrNull() ?: start.firstOrNull()?.let { newQr(1, it, design.size) }
        val templateDeleted = stringResource(R.string.label_template_deleted)
        val templateHidden = stringResource(R.string.label_template_hidden)
        SheetDialog(onDismissRequest = { templatesOpen = false }, title = stringResource(R.string.label_templates)) {
            Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val shownKinds = Templates.Kind.entries.filter { it.name !in hiddenBuiltIns }
                if (code != null && shownKinds.isNotEmpty()) {
                    Text(stringResource(R.string.label_templates_ready), color = Oled.TextPrimary, fontSize = 17.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shownKinds.forEach { kind ->
                            val built = remember(kind, design.size, code, AppStyleKey()) { Templates.build(kind, design.size, code, scanMe, tw) }
                            TemplateSample(
                                stringResource(kind.title()),
                                built,
                                renderer,
                                onLongClick = {
                                    hiddenBuiltIns = hiddenBuiltIns + kind.name
                                    templateStore.setHiddenBuiltIns(hiddenBuiltIns)
                                    onMessage(templateHidden)
                                },
                            ) {
                                commit(built)
                                openedTemplate = null
                                selectedId = null
                                templatesOpen = false
                            }
                        }
                    }
                }
                Text(stringResource(R.string.label_templates_mine), color = Oled.TextPrimary, fontSize = 17.sp)
                if (savedTemplates.isEmpty()) {
                    Text(stringResource(R.string.label_templates_mine_empty), fontSize = 14.sp)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        savedTemplates.forEach { t ->
                            TemplateSample(
                                t.name,
                                t.design,
                                renderer,
                                onLongClick = {
                                    savedTemplates = templateStore.remove(t.name)
                                    if (openedTemplate == t.name) openedTemplate = null
                                    onMessage(templateDeleted)
                                },
                            ) {
                                // Свій шаблон відкривається таким, як збережений: той самий аркуш, ті самі коди на своїх
                                // місцях. Далі можна прибрати, замінити чи додати коди й зберегти поверх.
                                commit(t.design)
                                openedTemplate = t.name
                                selectedId = null
                                templatesOpen = false
                            }
                        }
                    }
                }
                Text(stringResource(R.string.label_templates_hint), fontSize = 14.sp)
                if (hiddenBuiltIns.isNotEmpty()) {
                    SheetButton(stringResource(R.string.label_templates_restore), {
                        hiddenBuiltIns = emptySet()
                        templateStore.setHiddenBuiltIns(emptySet())
                    })
                }
                SheetButton(stringResource(R.string.label_template_save), {
                    templatesOpen = false
                    templateNameOpen = true
                }, enabled = design.elements.isNotEmpty())
            }
        }
    }
    if (templateNameOpen) {
        val savedText = stringResource(R.string.label_template_saved)
        TextDialog(openedTemplate.orEmpty(), title = stringResource(R.string.label_template_save), onDismiss = { templateNameOpen = false }) { name ->
            savedTemplates = templateStore.save(name, design)
            openedTemplate = name
            templateNameOpen = false
            onMessage(savedText)
        }
    }

    if (frameOpen) {
        // Усі рамки в одному місці: навколо коду (вибраного або єдиного) і навколо всього аркуша.
        SheetDialog(onDismissRequest = { frameOpen = false }, title = stringResource(R.string.label_frame)) {
            Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val target = frameTarget
                if (target != null) {
                    Text(stringResource(R.string.label_frame_code, target.title), color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QrFrame.entries.forEach { f ->
                            Sample(stringResource(f.title()), selected = target.frame == f, onClick = {
                                tryChange(target.copy(frame = f, banner = if (f == QrFrame.ScanMe && target.banner.isEmpty()) scanMe else target.banner))
                                frameOpen = false
                            }) { Element.Qr(0, 0f, 0f, "QR", "", 20f, caption = false, frame = f, banner = scanMe) }
                        }
                    }
                } else if (codes.size > 1) {
                    Text(stringResource(R.string.label_frame_pick_code), fontSize = 15.sp)
                }
                Text(stringResource(R.string.label_frame_sheet), color = Oled.TextPrimary, fontSize = 17.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QrFrame.entries.filter { it != QrFrame.ScanMe }.forEach { f ->
                        SampleBox(stringResource(f.title()), selected = design.border == f, onClick = {
                            // Нова рамка відсуває елементи від краю — що не вміщається, переїжджає.
                            var d = design.copy(border = f)
                            d.elements.forEach { e -> if (!Placement.fits(d, e, tw)) Placement.nearestFree(d, e, tw)?.let { p -> d = d.replace(p) } }
                            commit(d)
                            frameOpen = false
                        }) { canvas, w, h ->
                            val sheet = LabelDesign(size = LabelSize(30f, 20f), border = f)
                            val k = w / 34f
                            canvas.save()
                            canvas.translate(2f * k, (h - 20f * k) / 2f)
                            renderer.draw(canvas, k, sheet, LabelPalette.Screen)
                            canvas.restore()
                        }
                    }
                }
            }
        }
    }

    if (shareOpen) {
        val savedToGallery = stringResource(R.string.share_saved_gallery)
        val saveFailed = stringResource(R.string.share_save_failed)
        val textCode = (selected as? Element.Qr) ?: codes.singleOrNull()
        SheetDialog(onDismissRequest = { shareOpen = false }, title = stringResource(R.string.share)) {
            Column {
                MenuRow(Glyph.Gallery, stringResource(R.string.share_label_image), {
                    shareOpen = false
                    busy = true
                    scope.launch {
                        val uri = withContext(Dispatchers.Default) { renderer.renderPng(context, design) }
                        busy = false
                        LabelRenderer.shareImage(context, shareTitle, uri)
                    }
                })
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    MenuRow(Glyph.Download, stringResource(R.string.share_save_gallery), {
                        shareOpen = false
                        busy = true
                        scope.launch {
                            val name = "QRaft-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())
                            val ok = withContext(Dispatchers.IO) { renderer.saveToGallery(context, design, name) }
                            busy = false
                            onMessage(if (ok) savedToGallery else saveFailed)
                        }
                    })
                }
                if (textCode != null) {
                    MenuRow(Glyph.TextT, stringResource(R.string.share_code_text), {
                        shareOpen = false
                        LabelRenderer.shareText(context, shareTitle, textCode.content)
                    })
                }
            }
        }
    }

    if (sizeOpen) {
        SizeDialog(design.size, onDismiss = { sizeOpen = false }) {
            // Нова наклейка: що не влізло — на вільні місця.
            var d = design.resizedTo(it)
            d.elements.forEach { e -> if (!Placement.fits(d, e, tw)) Placement.nearestFree(d, e, tw)?.let { p -> d = d.replace(p) } }
            commit(d)
            sizeOpen = false
        }
    }
    if (textNew || textEdit != null) {
        TextDialog(textEdit?.text.orEmpty(), title = stringResource(R.string.label_add_text), onDismiss = { textNew = false; textEdit = null }) { text ->
            val edit = textEdit
            if (edit != null) {
                tryChange(edit.copy(text = text))
            } else {
                addElement(Element.Text(design.nextId, design.size.widthMm / 2, design.size.heightMm / 2, text, DEFAULT_LETTERS))
            }
            textNew = false
            textEdit = null
        }
    }
    if (stickerNew || stickerFor != null) {
        StickerDialog(current = stickerFor?.sticker, onDismiss = { stickerNew = false; stickerFor = null }) { sticker ->
            val edit = stickerFor
            if (edit != null) {
                tryChange(edit.copy(sticker = sticker))
            } else {
                addElement(Element.Icon(design.nextId, design.size.widthMm / 2, design.size.heightMm / 2, sticker, DEFAULT_ICON))
            }
            stickerNew = false
            stickerFor = null
        }
    }
    if (clearAsk) {
        SheetDialog(onDismissRequest = { clearAsk = false }, title = stringResource(R.string.label_clear_title), buttons = {
            SheetButton(stringResource(R.string.cancel), { clearAsk = false })
            SheetButton(stringResource(R.string.label_clear), {
                commit(design.copy(elements = emptyList()))
                selectedId = null
                clearAsk = false
            }, tone = Tone.Danger)
        }) {
            Text(stringResource(R.string.label_clear_text), fontSize = 16.sp)
        }
    }
    captionEdit?.let { qr ->
        // Підпис: видно теперішній; «Скасувати» — лишається як був; стер усе й «Застосувати» — підпису немає.
        TextDialog(if (qr.caption) qr.title else "", title = stringResource(R.string.label_caption), onDismiss = { captionEdit = null }, allowEmpty = true) {
            tryChange(if (it.isEmpty()) qr.copy(caption = false) else qr.copy(title = it, caption = true))
            captionDefault = it.isNotEmpty()
            captionEdit = null
        }
    }
    bannerEdit?.let { qr ->
        TextDialog(qr.banner, title = stringResource(R.string.label_edit_banner), onDismiss = { bannerEdit = null }) {
            tryChange(qr.copy(banner = it))
            bannerEdit = null
        }
    }
    if (pickerOpen) {
        val replacing = replaceFor
        SheetDialog(onDismissRequest = { pickerOpen = false }, title = stringResource(if (replacing != null) R.string.label_replace_code else R.string.label_add_code)) {
            LazyColumn {
                items(saved, key = { it.id }) { code ->
                    Text(
                        code.title,
                        color = Oled.TextPrimary,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptics.tap()
                                if (replacing != null) {
                                    val fresh = newQr(replacing.id, code, design.size)
                                    tryChange(replacing.copy(content = fresh.content, title = fresh.title, barcode = fresh.barcode, kind = fresh.kind))
                                    replaceFor = null
                                } else {
                                    // Сцена з вільним квадратиком — новий код стає саме туди (розмір і нахил місця).
                                    val slot = Scenes.freeSlot(design)
                                    val inSlot = slot?.let { (c, side, rot) ->
                                        newQr(design.nextId, code, design.size).copy(cx = c.first, cy = c.second, side = side, caption = false, frame = QrFrame.None, rotation = rot)
                                    }
                                    if (inSlot != null && Placement.fits(design, inSlot, tw)) {
                                        commit(design.add(inSlot))
                                        selectedId = inSlot.id
                                    } else {
                                        addElement(newQr(design.nextId, code, design.size))
                                    }
                                }
                                pickerOpen = false
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
