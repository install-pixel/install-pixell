package com.blacklaunch.qrcodes.label

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.blacklaunch.qrcodes.ui.Glyph
import com.blacklaunch.qrcodes.ui.GlyphIcon
import com.blacklaunch.qrcodes.ui.LocalHaptics
import com.blacklaunch.qrcodes.ui.SheetButton
import com.blacklaunch.qrcodes.ui.SheetDialog
import com.blacklaunch.qrcodes.ui.SheetInk
import com.blacklaunch.qrcodes.ui.SheetTextField
import com.blacklaunch.qrcodes.ui.Tone
import com.blacklaunch.qrcodes.ui.pencilFrame
import com.blacklaunch.qrcodes.ui.pencilLoop
import com.blacklaunch.qrcodes.ui.taperedPath
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
fun LabelScreen(start: List<QrCode>, saved: List<QrCode>, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val prefs = remember { LabelPrefs(context) }
    val renderer = remember { LabelRenderer.create(context) }
    val defaults = remember { prefs.load() }

    fun newQr(id: Int, code: QrCode, size: LabelSize) = Element.Qr(
        id = id,
        cx = size.widthMm / 2,
        cy = size.heightMm / 2,
        content = code.content,
        title = code.title,
        side = defaults.qrMm.coerceAtMost(LabelLayout.maxQrMm(size, defaults.caption)),
        caption = defaults.caption,
    )

    var design by remember {
        val first = LabelDesign(size = defaults.size)
        val withCodes = start.foldIndexed(first) { i, d, code -> d.add(newQr(i + 1, code, d.size)) }
        mutableStateOf(if (start.size > 1) Arrange.grid(withCodes) else withCodes)
    }
    var selectedId by remember { mutableStateOf(design.elements.singleOrNull()?.id) }
    var copies by remember { mutableIntStateOf(1) }
    var guides by remember { mutableStateOf<Pair<Float?, Float?>>(null to null) }
    var sizeOpen by remember { mutableStateOf(false) }
    var textEdit by remember { mutableStateOf<Element.Text?>(null) }
    var textNew by remember { mutableStateOf(false) }
    var stickerFor by remember { mutableStateOf<Element.Icon?>(null) }
    var stickerNew by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val selected = design.elements.firstOrNull { it.id == selectedId }

    // Розмір наклейки, розмір коду й підпис — на наступний раз.
    LaunchedEffect(design.size, (selected as? Element.Qr)?.side, (selected as? Element.Qr)?.caption) {
        val qr = selected as? Element.Qr
        prefs.save(LabelSettings(design.size, qr?.side ?: defaults.qrMm, 1, qr?.caption ?: defaults.caption))
    }
    BackHandler(onBack = onClose)

    fun update(e: Element) {
        design = design.replace(e)
    }

    fun addElement(e: Element) {
        design = design.add(e)
        selectedId = e.id
    }

    val jobName = stringResource(R.string.app_name)
    val shareTitle = stringResource(R.string.share)
    val codes = design.elements.filterIsInstance<Element.Qr>()

    Column(Modifier.fillMaxSize().background(Oled.Black).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { haptics.tap(); onClose() }, contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Back, stringResource(R.string.back))
            }
            Text(stringResource(R.string.label_title), color = Oled.TextPrimary, fontSize = 22.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
            SheetButton(
                "${LabelRenderer.fmt(design.size.widthMm)}×${LabelRenderer.fmt(design.size.heightMm)} mm",
                { sizeOpen = true },
                modifier = Modifier.padding(end = 8.dp),
            )
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
            Canvas(
                Modifier
                    .size(w, h)
                    .pencilFrame(6.dp, seed = 61, alpha = 0.55f)
                    .pointerInput(k) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val d = current
                            val hit = d.hit(down.position.x / k, down.position.y / k, 10.dp.toPx() / k, renderer::textWidth)
                            selectedId = hit?.id
                            if (hit == null) return@awaitEachGesture
                            haptics.tap()
                            var wantX = hit.cx
                            var wantY = hit.cy
                            var size = hit.size
                            var lastGuides: Pair<Float?, Float?> = null to null
                            do {
                                val event = awaitPointerEvent()
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                if (pan != Offset.Zero || zoom != 1f) {
                                    val now = current.elements.firstOrNull { it.id == hit.id } ?: break
                                    wantX += pan.x / k
                                    wantY += pan.y / k
                                    val resized = now.resized(size * zoom)
                                    size = resized.size
                                    val snap = Snap.place(wantX, wantY, resized.bounds(renderer::textWidth), resized, current.size)
                                    update(resized.moved(snap.cx, snap.cy))
                                    val g = snap.vertical to snap.horizontal
                                    if (g != lastGuides && (g.first != null || g.second != null)) haptics.tick()
                                    lastGuides = g
                                    guides = g
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            guides = null to null
                        }
                    },
            ) {
                drawIntoCanvas { renderer.draw(it.nativeCanvas, k, design, LabelPalette.Screen) }
                // Лінії, до яких прилипло.
                guides.first?.let { x -> drawLine(Oled.Confirm.copy(alpha = 0.7f), Offset(x * k, 0f), Offset(x * k, size.height), 1.dp.toPx()) }
                guides.second?.let { y -> drawLine(Oled.Confirm.copy(alpha = 0.7f), Offset(0f, y * k), Offset(size.width, y * k), 1.dp.toPx()) }
                // Вибраний — у синій рамці олівцем.
                selected?.bounds(renderer::textWidth)?.let { b ->
                    val pad = 2.dp.toPx()
                    val bw = b.width * k + 2 * pad
                    val bh = b.height * k + 2 * pad
                    translate(b.left * k - pad, b.top * k - pad) {
                        drawPath(taperedPath(pencilLoop(Size(bw, bh), 6.dp.toPx(), selected.id + 400, 0.8.dp.toPx()), 2.dp.toPx()), Oled.Confirm)
                    }
                }
            }
        }

        // Керування.
        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tool(Glyph.ScanQr, stringResource(R.string.label_add_code)) { pickerOpen = true }
                Tool(Glyph.TextT, stringResource(R.string.label_add_text)) { textNew = true }
                Tool(Glyph.Star, stringResource(R.string.label_add_icon)) { stickerNew = true }
                Tool(Glyph.Frame, stringResource(R.string.label_border), on = design.border) { design = design.copy(border = !design.border) }
                if (codes.size > 1) Tool(Glyph.Keyboard, stringResource(R.string.label_grid)) { design = Arrange.grid(design) }
            }
            Spacer(Modifier.size(12.dp))
            when (val e = selected) {
                null -> Text(
                    stringResource(R.string.label_hint),
                    color = Oled.TextSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                else -> SelectedPanel(
                    element = e,
                    design = design,
                    onChange = ::update,
                    onCenter = {
                        val b = e.bounds(renderer::textWidth)
                        val offY = (b.top + b.bottom) / 2 - e.cy
                        update(e.moved(design.size.widthMm / 2, design.size.heightMm / 2 - offY))
                    },
                    onCopy = {
                        val copy = when (e) {
                            is Element.Qr -> e.copy(id = design.nextId)
                            is Element.Text -> e.copy(id = design.nextId)
                            is Element.Icon -> e.copy(id = design.nextId)
                        }
                        addElement(copy.moved(e.cx + 4f, e.cy + 4f))
                    },
                    onDelete = {
                        design = design.remove(e.id)
                        selectedId = null
                    },
                    onFill = { qr -> design = Arrange.fill(design, qr) },
                    onEditText = { textEdit = it },
                    onPickSticker = { stickerFor = it },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_copies), color = Oled.TextPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
                SheetButton("−", { copies = (copies - 1).coerceAtLeast(1) }, enabled = copies > 1)
                Text("$copies", color = Oled.TextPrimary, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 16.dp))
                SheetButton("+", { copies = (copies + 1).coerceAtMost(MAX_COPIES) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            if (codes.size == 1) {
                SheetButton(stringResource(R.string.share_text), { LabelRenderer.shareText(context, shareTitle, codes.first().content) })
            }
            SheetButton(stringResource(R.string.share_image), {
                if (busy) return@SheetButton
                busy = true
                scope.launch {
                    val uri = withContext(Dispatchers.Default) { renderer.renderPng(context, design) }
                    busy = false
                    LabelRenderer.shareImage(context, shareTitle, uri)
                }
            }, enabled = !busy && design.elements.isNotEmpty())
            SheetButton(stringResource(R.string.print), { renderer.print(context, jobName, design, copies) }, tone = Tone.Confirm, enabled = design.elements.isNotEmpty())
        }
    }

    if (sizeOpen) {
        SizeDialog(design.size, onDismiss = { sizeOpen = false }) {
            design = design.resizedTo(it)
            sizeOpen = false
        }
    }
    if (textNew || textEdit != null) {
        TextDialog(textEdit?.text.orEmpty(), onDismiss = { textNew = false; textEdit = null }) { text ->
            val edit = textEdit
            if (edit != null) {
                update(edit.copy(text = text))
            } else {
                addElement(Element.Text(design.nextId, design.size.widthMm / 2, design.size.heightMm / 2, text, DEFAULT_LETTERS))
            }
            textNew = false
            textEdit = null
        }
    }
    if (stickerNew || stickerFor != null) {
        StickerDialog(onDismiss = { stickerNew = false; stickerFor = null }) { sticker ->
            val edit = stickerFor
            if (edit != null) {
                update(edit.copy(sticker = sticker))
            } else {
                addElement(Element.Icon(design.nextId, design.size.widthMm / 2, design.size.heightMm / 2, sticker, DEFAULT_ICON))
            }
            stickerNew = false
            stickerFor = null
        }
    }
    if (pickerOpen) {
        SheetDialog(onDismissRequest = { pickerOpen = false }, title = stringResource(R.string.label_add_code)) {
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
                                addElement(newQr(design.nextId, code, design.size))
                                pickerOpen = false
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

private const val MAX_COPIES = 500
private const val DEFAULT_LETTERS = 7f
private const val DEFAULT_ICON = 14f

/** Інструмент: значок олівцем у кружечку й підпис під ним. */
@Composable
private fun Tool(glyph: Glyph, label: String, on: Boolean = false, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable { haptics.tap(); onClick() }.padding(4.dp).width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).pencilFrame(24.dp, seed = label.length + 5, ink = if (on) Oled.Confirm else SheetInk, alpha = 0.8f),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, null, tint = if (on) Oled.Confirm else SheetInk)
        }
        Text(label, color = Oled.TextSecondary, fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Налаштування вибраного елемента: розмір, підпис і рамка (для коду), текст, значок, копія, видалити. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedPanel(
    element: Element,
    design: LabelDesign,
    onChange: (Element) -> Unit,
    onCenter: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onFill: (Element.Qr) -> Unit,
    onEditText: (Element.Text) -> Unit,
    onPickSticker: (Element.Icon) -> Unit,
) {
    val (label, min, max) = when (element) {
        is Element.Qr -> Triple(R.string.label_qr_size, Element.MIN_QR, minOf(Element.MAX_QR, minOf(design.size.widthMm, design.size.heightMm)))
        is Element.Text -> Triple(R.string.label_text_size, Element.MIN_LETTERS, Element.MAX_LETTERS)
        is Element.Icon -> Triple(R.string.label_icon_size, Element.MIN_ICON, minOf(Element.MAX_ICON, minOf(design.size.widthMm, design.size.heightMm)))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(label, element.size.roundToInt()), color = Oled.TextPrimary, fontSize = 17.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetButton("−", { onChange(element.resized((element.size - 1f).coerceAtLeast(min))) })
            Slider(
                value = element.size.coerceIn(min, max),
                onValueChange = { onChange(element.resized(it.roundToInt().toFloat())) },
                valueRange = min..maxOf(max, min + 1f),
                colors = SliderDefaults.colors(
                    thumbColor = SheetInk,
                    activeTrackColor = SheetInk.copy(alpha = 0.8f),
                    inactiveTrackColor = SheetInk.copy(alpha = 0.2f),
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            SheetButton("+", { onChange(element.resized((element.size + 1f).coerceAtMost(max))) })
        }
        if (element is Element.Qr) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.label_caption), element.caption) { onChange(element.copy(caption = !element.caption)) }
                Chip(stringResource(R.string.label_frame_none), element.frame == QrFrame.None) { onChange(element.copy(frame = QrFrame.None)) }
                Chip(stringResource(R.string.label_frame_pencil), element.frame == QrFrame.Pencil) { onChange(element.copy(frame = QrFrame.Pencil)) }
                Chip(stringResource(R.string.label_frame_double), element.frame == QrFrame.Double) { onChange(element.copy(frame = QrFrame.Double)) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetButton(stringResource(R.string.label_center), onCenter)
            when (element) {
                is Element.Qr -> SheetButton(stringResource(R.string.label_fill_page), { onFill(element) })
                is Element.Text -> SheetButton(stringResource(R.string.label_edit_text), { onEditText(element) })
                is Element.Icon -> SheetButton(stringResource(R.string.label_change_icon), { onPickSticker(element) })
            }
            SheetButton(stringResource(R.string.label_duplicate), onCopy)
            SheetButton(stringResource(R.string.delete), onDelete, tone = Tone.Danger)
        }
    }
}

/** Вибір-«табличка»: вибраний — синьою рамкою, текст ніколи не переноситься й не вилазить. */
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .pencilFrame(14.dp, seed = text.length + 3, alpha = if (selected) 0.9f else 0.35f, ink = if (selected) Oled.Confirm else SheetInk)
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
    var w by remember { mutableStateOf(LabelRenderer.fmt(current.widthMm)) }
    var h by remember { mutableStateOf(LabelRenderer.fmt(current.heightMm)) }
    val wv = w.replace(',', '.').toFloatOrNull()
    val hv = h.replace(',', '.').toFloatOrNull()
    val ok = wv != null && hv != null && wv in LabelSize.MIN_MM..LabelSize.MAX_MM && hv in LabelSize.MIN_MM..LabelSize.MAX_MM
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.label_size), buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.apply), {
            if (ok) {
                val preset = LabelSize.PRESETS.any { (it.widthMm == wv && it.heightMm == hv) || (it.widthMm == hv && it.heightMm == wv) }
                onApply(LabelSize(wv!!, hv!!, custom = !preset))
            }
        }, tone = Tone.Confirm, enabled = ok)
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelSize.PRESETS.forEach { p ->
                    val text = "${LabelRenderer.fmt(p.widthMm)}×${LabelRenderer.fmt(p.heightMm)}"
                    Chip(text, wv == p.widthMm && hv == p.heightMm) {
                        w = LabelRenderer.fmt(p.widthMm)
                        h = LabelRenderer.fmt(p.heightMm)
                    }
                }
                Chip(stringResource(R.string.label_rotate), false) {
                    val t = w
                    w = h
                    h = t
                }
            }
            val numbers = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            Text(stringResource(R.string.label_width_mm), fontSize = 15.sp)
            SheetTextField(w, { w = it }, "150", keyboardOptions = numbers)
            Text(stringResource(R.string.label_height_mm), fontSize = 15.sp)
            SheetTextField(h, { h = it }, "100", keyboardOptions = numbers)
            Text(stringResource(R.string.label_custom_range, LabelSize.MIN_MM.toInt(), LabelSize.MAX_MM.toInt()), fontSize = 14.sp)
        }
    }
}

/** Свій текст на наклейку (шрифтом від руки). */
@Composable
private fun TextDialog(initial: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.label_add_text), buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.apply), { onApply(text.trim()) }, tone = Tone.Confirm, enabled = text.isNotBlank())
    }) {
        SheetTextField(text, { text = it }, stringResource(R.string.label_text_hint), focusRequester = focus)
    }
}

/** Значки олівцем. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickerDialog(onDismiss: () -> Unit, onPick: (Sticker) -> Unit) {
    val haptics = LocalHaptics.current
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.label_add_icon)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Sticker.entries.forEach { s ->
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .pencilFrame(16.dp, seed = s.ordinal + 20, alpha = 0.5f)
                        .clickable { haptics.tap(); onPick(s) },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(s.glyph(), null, size = 36.dp)
                }
            }
        }
    }
}
