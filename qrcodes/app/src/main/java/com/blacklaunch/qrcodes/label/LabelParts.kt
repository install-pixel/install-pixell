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
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

// Частини екрана друку: панелі, вікна, зразки, допоміжне (винесено з LabelScreen.kt, щоб його було легше читати й міняти).

/** Вибраний елемент і лінії прилипання — сірим: видно і на білому аркуші, і на чорному (білим не видно на папері). */
internal val PAPER_SELECT = androidx.compose.ui.graphics.Color(0xFF8A8A8A)
internal val PAPER_GUIDE = androidx.compose.ui.graphics.Color(0xB39A9A9A)

internal const val MAX_UNDO = 40
internal const val DOUBLE_TAP_MS = 320L
internal const val DEFAULT_LETTERS = 7f
internal const val DEFAULT_ICON = 14f
internal const val DEFAULT_BAR_MM = 70f

/** Інструмент: значок олівцем у кружечку й підпис під ним. */
@Composable
internal fun Tool(glyph: Glyph, label: String, modifier: Modifier = Modifier.width(80.dp), on: Boolean = false, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
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
internal fun SelectedPanel(
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
            // Шкала від руки (олівцем / склом — за стилем), як у лаунчері.
            com.blacklaunch.qrcodes.ui.HandSlider(
                value = element.size.coerceIn(min, max),
                onValueChange = { v -> val r = v.roundToInt().toFloat(); if (r != element.size) onChange(element.resized(r)) },
                valueRange = min..maxOf(max, min + 1f),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
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

internal const val ACTIONS_PER_ROW = 5

/** Більше кодів — на наклейку лише перший (як зразок), а друк — кожен окремою наклейкою. */
internal const val MAX_ON_SHEET = 12
internal const val DRAFT_SAVE_MS = 500L

/** Наскільки можна збільшити фон (ширина картинки, мм) — що за краєм наклейки, просто не видно. */
internal const val BG_MAX_WIDTH_MM = 5000f

/** Малювання на наклейці: товщина ліній і радіус гумки, мм. */
internal const val DRAW_THIN_MM = 0.6f
internal const val DRAW_MEDIUM_MM = 1.1f
internal const val DRAW_THICK_MM = 1.8f

/** Три товщини пера: тонко, середньо, товсто (мм). */
internal val DRAW_PENS_MM = floatArrayOf(DRAW_THIN_MM, DRAW_MEDIUM_MM, DRAW_THICK_MM)
internal const val DRAW_ERASER_MM = 2.5f
internal val TAB_WIDTH = 116.dp

/** Висота панелі вкладок: розмір + два рядки дій вибраного елемента (найвища з вкладок). */
internal val TAB_AREA_HEIGHT = 240.dp

/** Дія з панелі вибраного елемента. */
internal class Action(val glyph: Glyph, val label: Int, val danger: Boolean = false, val enabled: Boolean = true, val run: () -> Unit)

/**
 * Зразок — як це виглядатиме на наклейці: маленький білий клаптик, намальований тим самим кодом, що й друк.
 * [make] дає елемент (розмір ~20 мм, центр підставляється сам).
 */
@Composable
internal fun Sample(label: String?, selected: Boolean, onClick: () -> Unit, make: () -> Element) {
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
internal fun SampleBox(
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

internal fun Templates.Kind.title(): Int = when (this) {
    Templates.Kind.Poster -> R.string.template_poster
    Templates.Kind.Badge -> R.string.template_badge
    Templates.Kind.Shelf -> R.string.template_shelf
    Templates.Kind.ScanMe -> R.string.template_scanme
    Templates.Kind.Copies -> R.string.template_copies
}

/** Стиль (шрифт) змінився — зразки шаблонів треба перемалювати й переміряти. */
internal data class AppStyleKeyData(val glass: Boolean)

@Composable
internal fun AppStyleKey() = AppStyleKeyData(com.blacklaunch.qrcodes.ui.AppStyle.glass)

/** Зразок шаблону: увесь аркуш у мініатюрі (ширина 96 dp, висота — за пропорціями наклейки). */
@Composable
internal fun TemplateSample(label: String, design: LabelDesign, renderer: LabelRenderer, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val ratio = design.size.heightMm / design.size.widthMm
    SampleBox(label, selected = false, onClick = onClick, width = 96.dp, height = (96f * ratio).coerceIn(40f, 140f).dp, onLongClick = onLongClick) { canvas, w, h ->
        val k = minOf(w / design.size.widthMm, h / design.size.heightMm)
        canvas.save()
        canvas.translate((w - design.size.widthMm * k) / 2f, (h - design.size.heightMm * k) / 2f)
        renderer.draw(canvas, k, design, LabelPalette.forView(design))
        canvas.restore()
    }
}

internal fun QrFrame.title(): Int = when (this) {
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
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
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
internal fun SizeDialog(current: LabelSize, onDismiss: () -> Unit, onApply: (LabelSize) -> Unit) {
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
internal fun TextDialog(initial: String, title: String, onDismiss: () -> Unit, allowEmpty: Boolean = false, onApply: (String) -> Unit) {
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
internal fun StickerDialog(current: Sticker?, onDismiss: () -> Unit, onPick: (Sticker) -> Unit) {
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
internal fun Sticker.title(): Int = when (this) {
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
internal fun copyBackground(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
    val resolver = context.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3200) sample *= 2
    val bitmap = resolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    // Фото з камери часто лежать «боком», а поворот записаний лише в EXIF — повертаємо як треба.
    val degrees = runCatching {
        resolver.openInputStream(uri)?.use { s ->
            when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)
    val upright = if (degrees == 0f) bitmap else {
        val m = android.graphics.Matrix().apply { postRotate(degrees) }
        android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true).also { bitmap.recycle() }
    }
    val dir = BackgroundFiles.dir(context)
    val file = java.io.File(dir, "bg_${System.currentTimeMillis()}.jpg")
    java.io.FileOutputStream(file).use { upright.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
    upright.recycle()
    file.absolutePath
}.getOrNull()

