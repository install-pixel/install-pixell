package com.blacklaunch.qrcodes.label

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.data.QrCode
import com.blacklaunch.qrcodes.qr.QrMatrix
import com.blacklaunch.qrcodes.ui.Glyph
import com.blacklaunch.qrcodes.ui.GlyphIcon
import com.blacklaunch.qrcodes.ui.LocalHaptics
import com.blacklaunch.qrcodes.ui.SheetButton
import com.blacklaunch.qrcodes.ui.SheetDialog
import com.blacklaunch.qrcodes.ui.SheetInk
import com.blacklaunch.qrcodes.ui.SheetTextField
import com.blacklaunch.qrcodes.ui.Tone
import com.blacklaunch.qrcodes.ui.pencilFrame
import com.blacklaunch.qrcodes.ui.theme.Oled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * «Друк і поділитися»: наклейка в масштабі, розмір наклейки (Zebra 150×100 мм за замовчуванням, інші готові або свій),
 * сторона QR-коду в мм, копії, підпис, кілька різних кодів на одній наклейці. Друк — PDF точного розміру через службу
 * друку Android; «Картинка» — PNG 300 dpi у будь-яку програму.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelScreen(start: List<QrCode>, saved: List<QrCode>, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val prefs = remember { LabelPrefs(context) }
    var settings by remember { mutableStateOf(prefs.load()) }
    var codes by remember { mutableStateOf(start) }
    var page by remember { mutableIntStateOf(0) }
    var customOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(settings) { prefs.save(settings) }
    BackHandler(onBack = onClose)

    val items = remember(codes) { codes.map { LabelItem(it.content, it.title) } }
    val matrices = remember(items) { items.map { QrMatrix.encode(it.content) } }
    val layout = remember(settings, items) { LabelLayout(settings, items.size) }
    val maxQr = LabelLayout.maxQrMm(settings.size, settings.caption)
    if (page >= layout.pageCount) page = (layout.pageCount - 1).coerceAtLeast(0)

    val jobName = stringResource(R.string.app_name)
    val shareTitle = stringResource(R.string.share)

    Column(Modifier.fillMaxSize().background(Oled.Black).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { haptics.tap(); onClose() }, contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Back, stringResource(R.string.back))
            }
            Text(stringResource(R.string.label_title), color = Oled.TextPrimary, fontSize = 22.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Наклейка в масштабі.
            BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val maxW = with(density) { maxWidth.toPx() }
                val maxH = with(density) { 320.dp.toPx() }
                val k = minOf(maxW / layout.widthMm, maxH / layout.heightMm)
                val w = with(density) { (layout.widthMm * k).toDp() }
                val h = with(density) { (layout.heightMm * k).toDp() }
                Canvas(Modifier.size(w, h).pencilFrame(4.dp, seed = 61, alpha = 0.5f)) {
                    drawIntoCanvas { LabelRenderer.drawPage(it.nativeCanvas, size.width / layout.widthMm, layout, page, items, matrices) }
                }
            }
            if (layout.pageCount > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    SheetButton("‹", { if (page > 0) page-- }, enabled = page > 0)
                    Text(
                        stringResource(R.string.label_page, page + 1, layout.pageCount),
                        color = Oled.TextSecondary,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    SheetButton("›", { if (page < layout.pageCount - 1) page++ }, enabled = page < layout.pageCount - 1)
                }
            }
            Text(
                stringResource(R.string.label_fit, layout.columns, layout.rows, layout.perPage),
                color = Oled.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Section(stringResource(R.string.label_size))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelSize.PRESETS.forEach { preset ->
                    val selected = !settings.size.custom &&
                        ((settings.size.widthMm == preset.widthMm && settings.size.heightMm == preset.heightMm) ||
                            (settings.size.widthMm == preset.heightMm && settings.size.heightMm == preset.widthMm))
                    Chip("${LabelRenderer.fmt(preset.widthMm)}×${LabelRenderer.fmt(preset.heightMm)}", selected) {
                        settings = settings.copy(size = preset)
                    }
                }
                Chip(
                    if (settings.size.custom) "${LabelRenderer.fmt(settings.size.widthMm)}×${LabelRenderer.fmt(settings.size.heightMm)}" else stringResource(R.string.label_custom),
                    settings.size.custom,
                ) { customOpen = true }
                Chip(stringResource(R.string.label_rotate), false) { settings = settings.copy(size = settings.size.rotated()) }
            }

            Section(stringResource(R.string.label_qr_size, layout.qrMm.roundToInt()))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetButton("−", { settings = settings.copy(qrMm = (layout.qrMm - 1f).coerceAtLeast(LabelLayout.MIN_QR_MM)) })
                Slider(
                    value = layout.qrMm,
                    onValueChange = { settings = settings.copy(qrMm = it.roundToInt().toFloat()) },
                    valueRange = LabelLayout.MIN_QR_MM..maxOf(maxQr, LabelLayout.MIN_QR_MM + 1f),
                    colors = SliderDefaults.colors(
                        thumbColor = SheetInk,
                        activeTrackColor = SheetInk.copy(alpha = 0.8f),
                        inactiveTrackColor = SheetInk.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                SheetButton("+", { settings = settings.copy(qrMm = (layout.qrMm + 1f).coerceAtMost(maxQr)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Швидкий вибір: малий, середній, на всю наклейку.
                listOf(20f, 30f, 40f, 60f).filter { it <= maxQr }.forEach { mm ->
                    Chip("${LabelRenderer.fmt(mm)} mm", layout.qrMm.roundToInt() == mm.toInt()) { settings = settings.copy(qrMm = mm) }
                }
                Chip(stringResource(R.string.label_qr_max), layout.qrMm >= maxQr - 0.5f) { settings = settings.copy(qrMm = maxQr.toInt().toFloat()) }
            }

            Section(stringResource(R.string.label_copies))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetButton("−", { settings = settings.copy(copies = (settings.copies - 1).coerceAtLeast(1)) }, enabled = settings.copies > 1)
                Text("${settings.copies}", color = Oled.TextPrimary, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 20.dp))
                SheetButton("+", { settings = settings.copy(copies = (settings.copies + 1).coerceAtMost(MAX_COPIES)) })
                Spacer(Modifier.width(16.dp))
                Chip(stringResource(R.string.label_fill_page), false) {
                    // Заповнити наклейку цілком: скільки вміщається, поділити на кількість кодів.
                    settings = settings.copy(copies = (layout.perPage / codes.size.coerceAtLeast(1)).coerceIn(1, MAX_COPIES))
                }
            }

            Section(stringResource(R.string.label_caption))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.yes), settings.caption) { settings = settings.copy(caption = true) }
                Chip(stringResource(R.string.no), !settings.caption) { settings = settings.copy(caption = false) }
            }

            Section(stringResource(R.string.label_codes))
            codes.forEachIndexed { i, code ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(code.title, color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (codes.size > 1) {
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { haptics.tap(); codes = codes.filterIndexed { j, _ -> j != i } }, contentAlignment = Alignment.Center) {
                            GlyphIcon(Glyph.Close, stringResource(R.string.remove), tint = Oled.TextSecondary, size = 20.dp)
                        }
                    }
                }
            }
            Box(Modifier.padding(top = 6.dp, bottom = 16.dp)) {
                SheetButton(stringResource(R.string.label_add_code), { pickerOpen = true })
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
                    val uris = withContext(Dispatchers.Default) { LabelRenderer.renderPngs(context, layout, items) }
                    busy = false
                    LabelRenderer.shareImages(context, shareTitle, uris)
                }
            }, enabled = !busy)
            SheetButton(stringResource(R.string.print), { LabelRenderer.print(context, jobName, layout, items) }, tone = Tone.Confirm)
        }
    }

    if (customOpen) {
        CustomSizeDialog(settings.size, onDismiss = { customOpen = false }) {
            settings = settings.copy(size = it)
            customOpen = false
        }
    }
    if (pickerOpen) {
        SheetDialog(onDismissRequest = { pickerOpen = false }, title = stringResource(R.string.label_add_code), buttons = {
            SheetButton(stringResource(R.string.done), { pickerOpen = false }, tone = Tone.Confirm)
        }) {
            LazyColumn {
                items(saved, key = { it.id }) { code ->
                    val on = codes.any { it.id == code.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptics.tap()
                                codes = if (on) {
                                    if (codes.size > 1) codes.filter { it.id != code.id } else codes
                                } else {
                                    codes + code
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (on) GlyphIcon(Glyph.Save, null, tint = Oled.Confirm)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(code.title, color = if (on) Oled.TextPrimary else Oled.TextSecondary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private const val MAX_COPIES = 200

@Composable
private fun Section(text: String) {
    Text(text, color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

/** Вибір-«табличка»: вибраний — підкреслений олівцем і яскравіший. */
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
        Text(text, color = if (selected) Oled.TextPrimary else Oled.TextSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun CustomSizeDialog(current: LabelSize, onDismiss: () -> Unit, onApply: (LabelSize) -> Unit) {
    var w by remember { mutableStateOf(LabelRenderer.fmt(current.widthMm)) }
    var h by remember { mutableStateOf(LabelRenderer.fmt(current.heightMm)) }
    val wv = w.replace(',', '.').toFloatOrNull()
    val hv = h.replace(',', '.').toFloatOrNull()
    val ok = wv != null && hv != null && wv in LabelSize.MIN_MM..LabelSize.MAX_MM && hv in LabelSize.MIN_MM..LabelSize.MAX_MM
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.label_custom_title), buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.apply), { if (ok) onApply(LabelSize(wv!!, hv!!, custom = true)) }, tone = Tone.Confirm, enabled = ok)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val numbers = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            Text(stringResource(R.string.label_width_mm), fontSize = 15.sp)
            SheetTextField(w, { w = it }, "150", keyboardOptions = numbers)
            Text(stringResource(R.string.label_height_mm), fontSize = 15.sp)
            SheetTextField(h, { h = it }, "100", keyboardOptions = numbers)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.label_custom_range, LabelSize.MIN_MM.toInt(), LabelSize.MAX_MM.toInt()), fontSize = 14.sp)
        }
    }
}
