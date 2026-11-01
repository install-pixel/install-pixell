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
            side = if (barcode) minOf(DEFAULT_BAR_MM, size.widthMm - 12f) else defaults.qrMm.coerceAtMost(LabelLayout.maxQrMm(size, defaults.caption)),
            caption = defaults.caption,
            barcode = barcode,
        )
    }

    var design by remember {
        val first = LabelDesign(size = defaults.size)
        val withCodes = start.foldIndexed(first) { i, d, code -> d.add(newQr(i + 1, code, d.size)) }
        // Кілька кодів — одразу сіткою, кожен на своєму місці.
        mutableStateOf(if (start.size > 1) Arrange.grid(withCodes, tw) else withCodes)
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
    // Тло вибране (як елемент): тягнеш — пересуваєш, двома пальцями — масштаб і поворот.
    var bgSelected by remember { mutableStateOf(false) }
    var drawNew by remember { mutableStateOf(false) }
    var drawEdit by remember { mutableStateOf<Element.Drawing?>(null) }

    val selected = design.elements.firstOrNull { it.id == selectedId }
    // Кому «Рамка» міняє рамку коду: вибраному коду, а якщо код один — йому.
    val frameTarget = (selected as? Element.Qr) ?: design.elements.filterIsInstance<Element.Qr>().singleOrNull()

    // Розмір наклейки, розмір коду й підпис — на наступний раз.
    LaunchedEffect(design.size, (selected as? Element.Qr)?.side, (selected as? Element.Qr)?.caption) {
        val qr = selected as? Element.Qr
        prefs.save(LabelSettings(design.size, qr?.takeIf { !it.barcode }?.side ?: defaults.qrMm, 1, qr?.caption ?: defaults.caption))
    }
    // Вибрав елемент на наклейці — панель сама показує «Змінити».
    LaunchedEffect(selectedId) { if (selectedId != null) editTab = true }
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

    val dialogOpen = drawNew || drawEdit != null || bgMenu || templatesOpen || templateNameOpen || frameOpen || shareOpen || sizeOpen || textNew || textEdit != null || stickerNew || stickerFor != null || pickerOpen || clearAsk || captionEdit != null || bannerEdit != null
    val blur = backdropAmount(dialogOpen)
    Box(Modifier.fillMaxSize().background(Oled.Black).backdrop(blur)) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
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
            RoundButton(Glyph.Print, stringResource(R.string.print), { renderer.print(context, jobName, design, 1) }, ink = Oled.Confirm, enabled = design.elements.isNotEmpty(), size = 46.dp)
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
                            val d = current
                            val hit = d.hit(down.position.x / k, down.position.y / k, 10.dp.toPx() / k, renderer::textWidth)
                            selectedId = hit?.id
                            if (hit != null) bgSelected = false
                            val bgNow = d.background
                            if (hit == null) {
                                if (bgSelected && bgNow != null) {
                                    // Вибране тло: тягнеш — пересуваєш, двома пальцями — масштаб і поворот.
                                    var bx = bgNow.cx
                                    var by = bgNow.cy
                                    var bw = bgNow.width
                                    var br = bgNow.rotation
                                    var travel = 0f
                                    var movedBg = false
                                    do {
                                        val event = awaitPointerEvent()
                                        val pan = event.calculatePan()
                                        val zoom = event.calculateZoom()
                                        val turn = event.calculateRotation()
                                        travel += pan.getDistance()
                                        if (travel > viewConfiguration.touchSlop || zoom != 1f || turn != 0f) {
                                            bx += pan.x / k
                                            by += pan.y / k
                                            bw = (bw * zoom).coerceIn(5f, 3000f)
                                            br += turn
                                            design = current.copy(background = bgNow.copy(cx = bx, cy = by, width = bw, rotation = normalizeAngle(br, snap = true)))
                                            movedBg = true
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                    if (movedBg) pushHistory(d) else bgSelected = false
                                }
                                return@awaitEachGesture
                            }
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
                                        is Element.Drawing -> drawEdit = hit
                                    }
                                } else {
                                    lastTap = t to hit.id
                                }
                            }
                        }
                    },
            ) {
                drawIntoCanvas { renderer.draw(it.nativeCanvas, k, design, LabelPalette.forView(design)) }
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
            androidx.compose.animation.AnimatedContent(
                targetState = editTab,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
                },
                label = "labelTab",
                // Стала висота під обидві вкладки: панель не стрибає, коли перемикаєш чи вибираєш елемент.
                modifier = Modifier.fillMaxWidth().heightIn(min = TAB_AREA_HEIGHT),
                contentAlignment = Alignment.TopCenter,
            ) { edit ->
                if (!edit) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Tool(Glyph.ScanQr, stringResource(R.string.label_add_code), Modifier.weight(1f)) { replaceFor = null; pickerOpen = true }
                            Tool(Glyph.TextT, stringResource(R.string.label_add_text), Modifier.weight(1f)) { textNew = true }
                            Tool(Glyph.Star, stringResource(R.string.label_add_icon), Modifier.weight(1f)) { stickerFor = null; stickerNew = true }
                            Tool(Glyph.Edit, stringResource(R.string.label_drawing), Modifier.weight(1f)) { drawNew = true }
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
                                Text(stringResource(R.string.label_bg_selected), color = Oled.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                                    Tool(Glyph.Save, stringResource(R.string.done), Modifier.weight(1f)) { bgSelected = false }
                                    Spacer(Modifier.weight(1f))
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
                            onEditDrawing = { drawEdit = it },
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
                                bgSelected = false
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
    if (drawNew || drawEdit != null) {
        DrawDialog(drawEdit, onDismiss = { drawNew = false; drawEdit = null }) { strokes, aspect, thick ->
            val edit = drawEdit
            if (edit != null) {
                tryChange(edit.copy(strokes = strokes, aspect = aspect, thick = thick))
            } else {
                var width = minOf(50f, design.size.widthMm * 0.5f)
                if (width * aspect > design.size.heightMm * 0.7f) width = design.size.heightMm * 0.7f / aspect
                addElement(Element.Drawing(design.nextId, design.size.widthMm / 2, design.size.heightMm / 2, strokes, aspect, width.coerceAtLeast(Element.MIN_DRAWING), thick))
            }
            drawNew = false
            drawEdit = null
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
                                    tryChange(replacing.copy(content = fresh.content, title = fresh.title, barcode = fresh.barcode))
                                    replaceFor = null
                                } else {
                                    addElement(newQr(design.nextId, code, design.size))
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

/** Вибраний елемент і лінії прилипання — сірим: видно і на білому аркуші, і на чорному (білим не видно на папері). */
private val PAPER_SELECT = androidx.compose.ui.graphics.Color(0xFF8A8A8A)
private val PAPER_GUIDE = androidx.compose.ui.graphics.Color(0xB39A9A9A)

private const val MAX_UNDO = 40
private const val DOUBLE_TAP_MS = 320L
private const val DEFAULT_LETTERS = 7f
private const val DEFAULT_ICON = 14f
private const val DEFAULT_BAR_MM = 70f

/** Інструмент: значок олівцем у кружечку й підпис під ним. */
@Composable
private fun Tool(glyph: Glyph, label: String, modifier: Modifier = Modifier.width(80.dp), on: Boolean = false, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { haptics.tap(); onClick() }
            .padding(vertical = 2.dp, horizontal = 2.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).pencilFrame(22.dp, seed = label.length + 5, ink = if (danger) Oled.Danger else if (on) com.blacklaunch.qrcodes.ui.SelectedInk else SheetInk, alpha = 0.8f),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, null, tint = if (danger) Oled.Danger else if (on) com.blacklaunch.qrcodes.ui.SelectedInk else SheetInk)
        }
        // Переносимо лише між словами; задовге слово — трохи меншим шрифтом, а не розірване.
        com.blacklaunch.qrcodes.ui.FitLabel(
            label,
            color = if (danger) Oled.Danger else Oled.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        )
    }
}

/**
 * Налаштування вибраного елемента: розмір (повзунок), круглі дії (повернути, по центру, копія, видалити); для коду —
 * підпис і рамки (зразками-картинками), для значка — усі значки (торкнись іншого — заміниться), для тексту — змінити.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedPanel(
    element: Element,
    design: LabelDesign,
    renderer: LabelRenderer,
    onChange: (Element) -> Unit,
    onCenter: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onFill: (Element.Qr) -> Unit,
    onEditText: (Element.Text) -> Unit,
    onEditCaption: (Element.Qr) -> Unit,
    onEditBanner: (Element.Qr) -> Unit,
    onReplace: (Element.Qr) -> Unit,
    onReplaceIcon: (Element.Icon) -> Unit,
    onEditDrawing: (Element.Drawing) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
) {
    val (label, min, max) = when (element) {
        is Element.Qr -> if (element.barcode) {
            Triple(R.string.label_bar_size, Element.MIN_QR, minOf(Element.MAX_QR, design.size.widthMm))
        } else {
            Triple(R.string.label_qr_size, Element.MIN_QR, minOf(Element.MAX_QR, minOf(design.size.widthMm, design.size.heightMm)))
        }
        is Element.Text -> Triple(R.string.label_text_size, Element.MIN_LETTERS, Element.MAX_LETTERS)
        is Element.Icon -> Triple(R.string.label_icon_size, Element.MIN_ICON, minOf(Element.MAX_ICON, minOf(design.size.widthMm, design.size.heightMm)))
        is Element.Drawing -> Triple(R.string.label_drawing_size, Element.MIN_DRAWING, minOf(Element.MAX_DRAWING, design.size.widthMm))
    }
    // Усі дії вибраного — круглими кнопками, рядками по п'ять (без прокрутки вбік і без окремих «табличок»).
    val actions = buildList {
        add(Action(Glyph.RotateLeft, R.string.label_rotate_left) { onChange(element.rotated(normalizeAngle(element.rotation - 90f, snap = true))) })
        add(Action(Glyph.RotateRight, R.string.label_rotate_right) { onChange(element.rotated(normalizeAngle(element.rotation + 90f, snap = true))) })
        add(Action(Glyph.Center, R.string.label_center, run = onCenter))
        add(Action(Glyph.Copy, R.string.label_duplicate, run = onCopy))
        when (element) {
            is Element.Qr -> {
                add(Action(Glyph.Grid, R.string.label_fill_short) { onFill(element) })
                add(Action(Glyph.TextT, R.string.label_caption) { onEditCaption(element) })
                add(Action(Glyph.ScanQr, R.string.label_replace_short) { onReplace(element) })
                if (element.frame == QrFrame.ScanMe) add(Action(Glyph.Edit, R.string.label_banner_short) { onEditBanner(element) })
            }
            is Element.Text -> add(Action(Glyph.Edit, R.string.label_edit_short) { onEditText(element) })
            is Element.Icon -> add(Action(Glyph.Star, R.string.label_replace_short) { onReplaceIcon(element) })
            is Element.Drawing -> add(Action(Glyph.Edit, R.string.label_edit_short) { onEditDrawing(element) })
        }
        add(Action(Glyph.Undo, R.string.label_undo, enabled = canUndo, run = onUndo))
        add(Action(Glyph.Delete, R.string.delete, danger = true, run = onDelete))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Розмір — одним рядком: назва зі значенням, «−», повзунок, «+».
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.widthIn(max = 150.dp)) {
                Text(stringResource(label, element.size.roundToInt()), color = Oled.TextPrimary, fontSize = 14.sp, maxLines = 2, lineHeight = 16.sp)
                if (element.rotation != 0f) Text(stringResource(R.string.label_angle, element.rotation.roundToInt()), color = Oled.TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            SheetButton("−", { onChange(element.resized((element.size - 1f).coerceAtLeast(min))) }, compact = true, modifier = Modifier.width(40.dp))
            Slider(
                value = element.size.coerceIn(min, max),
                onValueChange = { onChange(element.resized(it.roundToInt().toFloat())) },
                valueRange = min..maxOf(max, min + 1f),
                colors = SliderDefaults.colors(
                    thumbColor = SheetInk,
                    activeTrackColor = SheetInk.copy(alpha = 0.8f),
                    inactiveTrackColor = SheetInk.copy(alpha = 0.2f),
                ),
                modifier = Modifier.weight(1f).height(36.dp).padding(horizontal = 6.dp),
            )
            SheetButton("+", { onChange(element.resized((element.size + 1f).coerceAtMost(max))) }, compact = true, modifier = Modifier.width(40.dp))
        }
        actions.chunked(ACTIONS_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { a -> Tool(a.glyph, stringResource(a.label), Modifier.weight(1f), enabled = a.enabled, danger = a.danger, onClick = a.run) }
                repeat(ACTIONS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val ACTIONS_PER_ROW = 5
private val TAB_WIDTH = 116.dp

/** Висота панелі вкладок: розмір + два рядки дій вибраного елемента (найвища з вкладок). */
private val TAB_AREA_HEIGHT = 228.dp

/** Дія з панелі вибраного елемента. */
private class Action(val glyph: Glyph, val label: Int, val danger: Boolean = false, val enabled: Boolean = true, val run: () -> Unit)

/**
 * Зразок — як це виглядатиме на наклейці: маленький білий клаптик, намальований тим самим кодом, що й друк.
 * [make] дає елемент (розмір ~20 мм, центр підставляється сам).
 */
@Composable
private fun Sample(label: String?, selected: Boolean, onClick: () -> Unit, make: () -> Element) {
    val context = LocalContext.current
    val renderer = remember { LabelRenderer.create(context) }
    val element = remember(label, selected) { make() }
    SampleBox(label, selected, onClick) { canvas, w, h ->
        val b = element.bounds(renderer::textWidth)
        val k = minOf(w / (b.width + 4f), h / (b.height + 4f))
        renderer.drawElement(canvas, k, element.moved(w / 2 / k, h / 2 / k), LabelPalette.Screen)
    }
}

/** Клаптик-зразок 64 dp: малює [paint] (полотно, ширина, висота в px); вибраний — у синій рамці. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SampleBox(
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = 64.dp,
    height: androidx.compose.ui.unit.Dp = 64.dp,
    onLongClick: (() -> Unit)? = null,
    paint: (android.graphics.Canvas, Float, Float) -> Unit,
) {
    val haptics = LocalHaptics.current
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { haptics.tap(); onClick() },
                onLongClick = onLongClick?.let { { haptics.longPress(); it() } },
            )
            .padding(4.dp)
            // Підпис може бути ширшим за клаптик (довгі назви польською/англійською) — місце на два рядки.
            .width(width + 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .size(width, height)
                .clip(RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.graphics.Color(LabelPalette.Screen.paper))
                .then(
                    when {
                        !selected -> Modifier
                        // «Скло»: тонка синя грань (скляне тло не повинно перекрити білий клаптик).
                        com.blacklaunch.qrcodes.ui.AppStyle.glass -> Modifier.border(1.5.dp, com.blacklaunch.qrcodes.ui.SelectedInk, RoundedCornerShape(10.dp))
                        else -> Modifier.pencilFrame(10.dp, seed = 9, ink = com.blacklaunch.qrcodes.ui.SelectedInk)
                    },
                ),
        ) {
            drawIntoCanvas { paint(it.nativeCanvas, size.width, size.height) }
        }
        if (label != null) {
            com.blacklaunch.qrcodes.ui.FitLabel(
                label,
                color = if (selected) Oled.TextPrimary else Oled.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

private fun Templates.Kind.title(): Int = when (this) {
    Templates.Kind.Poster -> R.string.template_poster
    Templates.Kind.Badge -> R.string.template_badge
    Templates.Kind.Shelf -> R.string.template_shelf
    Templates.Kind.ScanMe -> R.string.template_scanme
    Templates.Kind.Copies -> R.string.template_copies
}

/** Стиль (шрифт) змінився — зразки шаблонів треба перемалювати й переміряти. */
private data class AppStyleKeyData(val glass: Boolean)

@Composable
private fun AppStyleKey() = AppStyleKeyData(com.blacklaunch.qrcodes.ui.AppStyle.glass)

/** Зразок шаблону: увесь аркуш у мініатюрі (ширина 96 dp, висота — за пропорціями наклейки). */
@Composable
private fun TemplateSample(label: String, design: LabelDesign, renderer: LabelRenderer, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val ratio = design.size.heightMm / design.size.widthMm
    SampleBox(label, selected = false, onClick = onClick, width = 96.dp, height = (96f * ratio).coerceIn(40f, 140f).dp, onLongClick = onLongClick) { canvas, w, h ->
        val k = minOf(w / design.size.widthMm, h / design.size.heightMm)
        canvas.save()
        canvas.translate((w - design.size.widthMm * k) / 2f, (h - design.size.heightMm * k) / 2f)
        renderer.draw(canvas, k, design, LabelPalette.forView(design))
        canvas.restore()
    }
}

private fun QrFrame.title(): Int = when (this) {
    QrFrame.None -> R.string.label_frame_none
    QrFrame.Pencil -> R.string.label_frame_pencil
    QrFrame.Double -> R.string.label_frame_double
    QrFrame.Dashed -> R.string.label_frame_dashed
    QrFrame.Corners -> R.string.label_frame_corners
    QrFrame.Bold -> R.string.label_frame_bold
    QrFrame.Ticket -> R.string.label_frame_ticket
    QrFrame.Dots -> R.string.label_frame_dots
    QrFrame.ScanMe -> R.string.label_frame_scanme
}

/** Вибір-«табличка»: вибраний — синьою рамкою, текст ніколи не переноситься й не вилазить. */
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .pencilFrame(14.dp, seed = text.length + 3, alpha = if (selected) 0.9f else 0.35f, ink = if (selected) com.blacklaunch.qrcodes.ui.SelectedInk else SheetInk)
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (selected) Oled.TextPrimary else Oled.TextSecondary, fontSize = 15.sp, maxLines = 1, softWrap = false)
    }
}

/** Розмір наклейки: готові, свій, повернути. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SizeDialog(current: LabelSize, onDismiss: () -> Unit, onApply: (LabelSize) -> Unit) {
    val context = LocalContext.current
    val screenSize = remember { LabelRenderer.screenPixels(context).let { (pw, ph) -> LabelSize.forScreen(pw, ph) } }
    var w by remember { mutableStateOf(LabelRenderer.fmt(current.widthMm)) }
    var h by remember { mutableStateOf(LabelRenderer.fmt(current.heightMm)) }
    // «Екран телефону» — поки розміри не змінили вручну.
    var screen by remember { mutableStateOf(current.screen) }
    val wv = w.replace(',', '.').toFloatOrNull()
    val hv = h.replace(',', '.').toFloatOrNull()
    val ok = wv != null && hv != null && wv in LabelSize.MIN_MM..LabelSize.MAX_MM && hv in LabelSize.MIN_MM..LabelSize.MAX_MM
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.label_size), buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.apply), {
            if (ok) {
                val preset = LabelSize.PRESETS.any { (it.widthMm == wv && it.heightMm == hv) || (it.widthMm == hv && it.heightMm == wv) }
                onApply(if (screen) screenSize else LabelSize(wv!!, hv!!, custom = !preset))
            }
        }, tone = Tone.Confirm, enabled = ok)
    }) {
        Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelSize.PRESETS.forEach { p ->
                    val text = "${LabelRenderer.fmt(p.widthMm)}×${LabelRenderer.fmt(p.heightMm)}"
                    Chip(text, !screen && wv == p.widthMm && hv == p.heightMm) {
                        w = LabelRenderer.fmt(p.widthMm)
                        h = LabelRenderer.fmt(p.heightMm)
                        screen = false
                    }
                }
                // Під екран цього телефона: картинка — точно в його пікселях.
                Chip(stringResource(R.string.label_screen), screen) {
                    w = LabelRenderer.fmt(screenSize.widthMm)
                    h = LabelRenderer.fmt(screenSize.heightMm)
                    screen = true
                }
                Chip(stringResource(R.string.label_rotate), false) {
                    val t = w
                    w = h
                    h = t
                    screen = false
                }
            }
            val numbers = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            Text(stringResource(R.string.label_width_mm), fontSize = 15.sp)
            SheetTextField(w, { w = it; screen = false }, "150", keyboardOptions = numbers)
            Text(stringResource(R.string.label_height_mm), fontSize = 15.sp)
            SheetTextField(h, { h = it; screen = false }, "100", keyboardOptions = numbers)
            if (screen) Text(stringResource(R.string.label_screen_hint), fontSize = 14.sp)
            Text(stringResource(R.string.label_custom_range, LabelSize.MIN_MM.toInt(), LabelSize.MAX_MM.toInt()), fontSize = 14.sp)
        }
    }
}

/** Свій текст на наклейку (шрифтом від руки). */
@Composable
private fun TextDialog(initial: String, title: String, onDismiss: () -> Unit, allowEmpty: Boolean = false, onApply: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SheetDialog(onDismissRequest = onDismiss, title = title, buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.apply), { onApply(text.trim()) }, tone = Tone.Confirm, enabled = allowEmpty || text.isNotBlank())
    }) {
        SheetTextField(text, { text = it }, stringResource(R.string.label_text_hint), focusRequester = focus)
    }
}

/** Значки олівцем. */
/** Усі значки зразками з підписами — так само, як вибір рамки. [current] — значок, що замінюється (або null — новий). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickerDialog(current: Sticker?, onDismiss: () -> Unit, onPick: (Sticker) -> Unit) {
    val title = stringResource(if (current != null) R.string.label_icon_replace else R.string.label_add_icon)
    SheetDialog(onDismissRequest = onDismiss, title = title) {
        FlowRow(
            Modifier.bouncyScroll(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Sticker.entries.forEach { st ->
                Sample(stringResource(st.title()), selected = current == st, onClick = { onPick(st) }) {
                    Element.Icon(0, 0f, 0f, st, 20f)
                }
            }
        }
    }
}

/** Що означає значок — підпис під зразком. */
private fun Sticker.title(): Int = when (this) {
    Sticker.Wifi -> R.string.sticker_wifi
    Sticker.Phone -> R.string.sticker_phone
    Sticker.Link -> R.string.sticker_link
    Sticker.Download -> R.string.sticker_download
    Sticker.Email -> R.string.sticker_email
    Sticker.Location -> R.string.sticker_location
    Sticker.Contact -> R.string.sticker_contact
    Sticker.ScanPhone -> R.string.sticker_scan
    Sticker.Smile -> R.string.sticker_smile
    Sticker.Heart -> R.string.sticker_heart
}

/**
 * Копія вибраного зображення в пам'ять програми (зменшена до розумного розміру, JPEG) — шлях до файлу або null.
 */
private fun copyBackground(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
    val resolver = context.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
    val bitmap = resolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val dir = java.io.File(context.filesDir, "backgrounds").apply { mkdirs() }
    val file = java.io.File(dir, "bg_${System.currentTimeMillis()}.jpg")
    java.io.FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
    bitmap.recycle()
    file.absolutePath
}.getOrNull()

/**
 * Полотно для малювання пальцем: одна лінія (тонко чи товсто), «Крок назад», «Очистити». Готово — штрихи в частках
 * свого прямокутника й співвідношення сторін (малюнок стає елементом, що масштабується).
 */
@Composable
private fun DrawDialog(initial: Element.Drawing?, onDismiss: () -> Unit, onDone: (List<List<Float>>, Float, Boolean) -> Unit) {
    // Точки — частки ширини полотна (полотно квадратне).
    val strokes = remember {
        androidx.compose.runtime.mutableStateListOf<List<Offset>>().apply {
            if (initial != null) {
                // Наявний малюнок — уміщаємо в полотно з полем, зберігаючи пропорції.
                val s = 0.8f / maxOf(1f, initial.aspect)
                val w = s
                val h = s * initial.aspect
                val left = (1f - w) / 2f
                val top = (1f - h) / 2f
                initial.strokes.forEach { st -> add(st.chunked(2).filter { it.size == 2 }.map { (x, y) -> Offset(left + x * w, top + y * h) }) }
            }
        }
    }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var thick by remember { mutableStateOf(initial?.thick ?: false) }
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.label_drawing), buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.done), {
            val pts = strokes.flatten()
            if (pts.isNotEmpty()) {
                val minX = pts.minOf { it.x }
                val maxX = pts.maxOf { it.x }
                val minY = pts.minOf { it.y }
                val maxY = pts.maxOf { it.y }
                val bw = (maxX - minX).coerceAtLeast(0.02f)
                val bh = (maxY - minY).coerceAtLeast(0.02f)
                val norm = strokes.map { st -> st.flatMap { listOf((it.x - minX) / bw, (it.y - minY) / bh) } }
                onDone(norm, bh / bw, thick)
            }
        }, tone = Tone.Confirm, enabled = strokes.isNotEmpty())
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color(LabelPalette.Screen.paper))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val side = size.width.toFloat().coerceAtLeast(1f)
                            val pts = mutableListOf(down.position / side)
                            current = pts.toList()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull()?.let { ch ->
                                    if (ch.pressed) {
                                        pts.add(ch.position / side)
                                        ch.consume()
                                    }
                                }
                                current = pts.toList()
                            } while (event.changes.any { it.pressed })
                            strokes.add(pts.toList())
                            current = emptyList()
                        }
                    },
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val ink = androidx.compose.ui.graphics.Color(LabelPalette.Screen.ink)
                    val width = size.width * if (thick) 0.024f else 0.012f
                    (strokes + listOf(current)).forEach { st ->
                        if (st.isEmpty()) return@forEach
                        if (st.size == 1) {
                            drawCircle(ink, width / 2, st[0] * size.width)
                        } else {
                            val path = androidx.compose.ui.graphics.Path()
                            path.moveTo(st[0].x * size.width, st[0].y * size.width)
                            st.drop(1).forEach { path.lineTo(it.x * size.width, it.y * size.width) }
                            drawPath(path, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.blacklaunch.qrcodes.ui.ChoiceChip(stringResource(R.string.label_draw_thin), !thick) { thick = false }
                com.blacklaunch.qrcodes.ui.ChoiceChip(stringResource(R.string.label_draw_thick), thick) { thick = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton(stringResource(R.string.label_undo), { strokes.removeLastOrNull() }, enabled = strokes.isNotEmpty())
                SheetButton(stringResource(R.string.label_clear), { strokes.clear() }, enabled = strokes.isNotEmpty())
            }
        }
    }
}
