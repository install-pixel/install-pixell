package com.blacklaunch.paleta.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.paleta.CardView
import com.blacklaunch.paleta.R
import com.blacklaunch.paleta.pack.Limit
import com.blacklaunch.paleta.pack.Num
import com.blacklaunch.paleta.ui.theme.Oled
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Десятковий знак як у мові телефону: кома (uk, pl) чи крапка (en). */
fun decimalComma(): Boolean = DecimalFormatSymbols.getInstance().decimalSeparator == ','

/** Дата як у бланку: 16.10.26. */
fun formDate(ms: Long): String = SimpleDateFormat("dd.MM.yy", Locale.ROOT).format(Date(ms))

/** Дата й час для списку історії. */
fun listDate(ms: Long): String = SimpleDateFormat("dd.MM.yy  HH:mm", Locale.ROOT).format(Date(ms))

/** Ім'я та прізвище для рядка підпису в бланку (з Налаштувань; порожньо — рядок порожній). */
object FormSigner {
    var name by mutableStateOf("")
}

/**
 * Сітка бланку — шість базових колонок з пропорціями паперового зразка (виміряно з фото). Кожен рядок складає
 * клітинки з цих колонок, тож вертикальні лінії стоять одна над одною, як на папері.
 */
private val COLS = floatArrayOf(211f, 104f, 213f, 212f, 211f, 212f)

/** Ширина клітинки, що займає колонки [from]..[to] (включно). */
private fun span(from: Int, to: Int = from): Float = (from..to).sumOf { COLS[it].toDouble() }.toFloat()

/** Висота звичайного рядка бланку (як зменшений А4) і рядка-заголовка таблиці. */
private val ROW = 24.dp
private val ROW_HEAD = 28.dp

/**
 * Бланк-результат — копія паперової «Картки нового артикулу», як зменшений аркуш: ті самі клітинки й лінії на тих
 * самих місцях. Заповнюємо те, що рахує програма (SKU, дата, розміри, кількості, ваги, на шарі, шарів, ТАК/НІ,
 * позначки, ім'я в підписі); решта (номер поставки, назва, EAN, REC, підписи) — порожні, щоб дописати від руки.
 */
@Composable
fun ResultCard(view: CardView, modifier: Modifier = Modifier) {
    val comma = decimalComma()
    val s = view.stack
    val unit = view.unit
    val kgUnit = stringResource(R.string.unit_kg)
    fun m(mm: Int?) = mm?.let { Num.metres(it, comma) } ?: ""
    fun kg(g: Long?) = g?.let { "${Num.kilos(it, comma)} $kgUnit" } ?: ""
    val top = listOf(span(0), span(1, 2), span(3), span(4, 5))
    val dims = listOf(span(0), span(1, 2), span(3), span(4), span(5))
    val wide = listOf(span(0, 1), span(2), span(3), span(4), span(5))
    val yn = listOf(span(0, 3), span(4), span(5))
    val sign = listOf(span(0, 3), span(4, 5))
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().pencilFrame(6.dp, seed = 61, alpha = 0.75f).padding(1.dp)) {
            // --- Шапка ---
            FormRow(top, ROW_HEAD, first = true) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_delivery), "(Pre-advice)")
                    2 -> Label(stringResource(R.string.form_date_label))
                    3 -> Value(formDate(view.dateMs))
                }
            }
            FormRow(top, ROW_HEAD) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_sku), "(SKU)")
                    1 -> Value(view.sku)
                    2 -> Label(stringResource(R.string.form_ean))
                }
            }
            FormRow(top, ROW_HEAD) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_name), "(Description)")
                    2 -> Label("REC")
                }
            }
            // --- Розміри ---
            FormRow(listOf(1f), ROW, strong = true) { Label(stringResource(R.string.form_dims), bold = true, size = 11f) }
            FormRow(dims, ROW_HEAD) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_layer_by))
                    1 -> Label(stringResource(R.string.col_param), bold = true)
                    2 -> Label(stringResource(R.string.col_unit), "(each)", bold = true)
                    3 -> Label(stringResource(R.string.col_box), "(box)", bold = true)
                    4 -> Label(stringResource(R.string.col_pallet), "(pallet)", bold = true)
                }
            }
            DimRow(dims, stringResource(R.string.row_length), m(unit?.first), m(view.box.first), "")
            DimRow(dims, stringResource(R.string.row_width), m(unit?.second), m(view.box.second), "")
            DimRow(dims, stringResource(R.string.row_height), m(unit?.third), m(view.box.third), s?.let { m(it.heightMm) } ?: "")
            FormRow(dims, ROW_HEAD) { i ->
                when (i) {
                    0 -> Crossed()
                    1 -> Label(stringResource(R.string.row_qty))
                    2 -> Value("1")
                    3 -> Value(view.unitsPerBox.toString(), note = stringResource(R.string.form_qty_box))
                    4 -> Value(s?.boxes?.toString() ?: "", note = stringResource(R.string.form_qty_pallet), bold = true)
                }
            }
            FormRow(dims, ROW) { i ->
                when (i) {
                    0 -> Crossed()
                    1 -> Label(stringResource(R.string.row_weight_form))
                    2 -> Value(kg(view.unitG))
                    3 -> Value(kg(view.boxG))
                    4 -> Value(s?.let { kg(it.weightG) } ?: "", bold = true)
                }
            }
            // --- На шарі, шарів ---
            FormRow(wide, ROW, strong = true) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_per_layer), "(Eaches per layer)")
                    1 -> Label(stringResource(R.string.form_pcs), bold = true)
                    2 -> Value(s?.let { perLayerText(it.perLayer.toLong() * view.unitsPerBox, it.perLayer2.toLong() * view.unitsPerBox) } ?: "")
                    3 -> Label(stringResource(R.string.form_boxes), bold = true)
                    4 -> Value(s?.let { perLayerText(it.perLayer.toLong(), it.perLayer2.toLong()) } ?: "")
                }
            }
            FormRow(listOf(span(0, 1), span(2, 5)), ROW) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_layers))
                    1 -> Value(s?.let { maxOf(it.layers, if (it.partial) 1 else 0).toString() } ?: "", bold = true)
                }
            }
            // --- ТАК / НІ ---
            YesNo(yn, stringResource(R.string.form_structure), if (s == null) null else s.partial)
            YesNo(yn, stringResource(R.string.form_oversize), view.flags.oversize)
            YesNo(yn, stringResource(R.string.form_adr), view.adr)
            YesNo(yn, stringResource(R.string.form_inner), null)
            FormRow(wide, ROW) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_mark))
                    1 -> Check("UGLY", false)
                    2 -> Check("AWKWARD", view.flags.awkward)
                    3 -> Check("TWO MEN LIFT", view.flags.twoMen)
                    4 -> Check("HANGING GARMENT", false)
                }
            }
            // --- Підписи ---
            FormRow(sign, ROW_HEAD, strong = true) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_sign_struct), align = TextAlign.Start)
                    1 -> Value(FormSigner.name, start = true)
                }
            }
            FormRow(sign, ROW_HEAD) { i -> if (i == 0) Label(stringResource(R.string.form_sign_wms), align = TextAlign.Start) }
            FormRow(sign, ROW_HEAD) { i -> if (i == 0) Label(stringResource(R.string.form_sign_dok), align = TextAlign.Start) }
        }
    }
}

/** На шарі: одне число або «перший / другий», якщо другий шар розкладено інакше. */
private fun perLayerText(a: Long, b: Long): String = if (a == b) a.toString() else "$a / $b"

@Composable
private fun DimRow(cols: List<Float>, name: String, unit: String, box: String, pallet: String) {
    FormRow(cols, ROW) { i ->
        when (i) {
            0 -> Label("------>")
            1 -> Label(name)
            2 -> Value(unit)
            3 -> Value(box)
            4 -> Value(pallet, bold = true)
        }
    }
}

@Composable
private fun YesNo(cols: List<Float>, name: String, yes: Boolean?) {
    FormRow(cols, ROW) { i ->
        when (i) {
            0 -> Label(name, align = TextAlign.Start)
            1 -> Mark(stringResource(R.string.yes), yes == true)
            2 -> Mark(stringResource(R.string.no), yes == false)
        }
    }
}

/**
 * Рядок бланку сталої висоти: клітинки за вагами [cols], лінії малюються поверх меж (не займають місця), тож
 * вертикальні лінії різних рядків стоять точно одна над одною. [strong] — товща лінія зверху (межа розділу).
 */
@Composable
private fun FormRow(cols: List<Float>, height: Dp, first: Boolean = false, strong: Boolean = false, cell: @Composable (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind { if (!first) gridLine(Offset(0f, 0f), Offset(size.width, 0f), strong, (size.width % 89).toInt()) },
    ) {
        cols.forEachIndexed { i, w ->
            Box(
                Modifier
                    .weight(w)
                    .fillMaxHeight()
                    .drawBehind { if (i > 0) gridLine(Offset(0f, 0f), Offset(0f, size.height), false, i * 7 + cols.size) }
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) { cell(i) }
        }
    }
}

/** Лінія сітки: «Скло» — ледь світла, «Аркуш» — штрих олівцем. */
private fun DrawScope.gridLine(a: Offset, b: Offset, strong: Boolean, seed: Int) {
    if (AppStyle.glass) {
        drawLine(if (strong) Color(0x55FFFFFF) else Color(0x30FFFFFF), a, b, 1.dp.toPx())
    } else {
        pencilTick(a, b, (if (strong) 1.5f else 1.05f).dp.toPx(), SheetInk.copy(alpha = if (strong) 0.6f else 0.4f), seed)
    }
}

/**
 * Текст, що сам зменшується, щоб поміститися в клітинку (як дрібний друк на аркуші): від [maxSp] донизу, до [lines]
 * рядків, не виходячи за межі.
 */
@Composable
private fun Fit(text: String, maxSp: Float, color: Color, bold: Boolean = false, lines: Int = 2, align: TextAlign = TextAlign.Center) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.Center) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val weight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        val size = remember(text, w, h, maxSp, base, bold, lines) {
            var sp = maxSp
            while (sp > 5f) {
                val r = measurer.measure(
                    text,
                    base.copy(fontSize = sp.sp, lineHeight = (sp * 1.12f).sp, fontWeight = weight),
                    maxLines = lines,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = w.coerceAtLeast(1)),
                )
                if (!r.hasVisualOverflow && r.size.height <= h) break
                sp -= 0.5f
            }
            sp
        }
        Text(text, color = color, fontSize = size.sp, lineHeight = (size * 1.12f).sp, fontWeight = weight, maxLines = lines, textAlign = align, overflow = TextOverflow.Clip)
    }
}

/** Надрукований підпис клітинки (сірим, дрібно); [sub] — ще дрібніше пояснення в другому рядку. */
@Composable
private fun Label(text: String, sub: String? = null, bold: Boolean = false, size: Float = 9.5f, align: TextAlign = TextAlign.Center) {
    Fit(if (sub != null) "$text\n$sub" else text, size, Oled.TextSecondary, bold = bold, lines = if (sub != null) 3 else 2, align = align)
}

/** Вписане значення (яскраво, як ручкою); [note] — дрібний надрукований підпис під ним. */
@Composable
private fun Value(text: String, big: Boolean = false, bold: Boolean = false, note: String? = null, start: Boolean = false) {
    if (note == null) {
        // [start] — ім'я в підписі: з лівого краю, до двох рядків (обидва рядки починаються з одного місця).
        if (text.isNotEmpty()) Fit(text, if (big) 15f else 12.5f, Oled.TextPrimary, bold = bold || big, lines = if (start) 2 else 1, align = if (start) TextAlign.Start else TextAlign.Center)
        return
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.6f).fillMaxWidth()) { if (text.isNotEmpty()) Fit(text, 12.5f, Oled.TextPrimary, bold = bold, lines = 1) }
        Box(Modifier.weight(0.4f).fillMaxWidth()) { Fit(note, 7f, Oled.TextSecondary.copy(alpha = 0.75f), lines = 1) }
    }
}

/** ТАК / НІ: вибране — з галочкою, яскраве; інше — тьмяне. */
@Composable
private fun Mark(text: String, chosen: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (chosen) {
            GlyphIcon(Glyph.Save, null, tint = Oled.TextPrimary, size = 13.dp)
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text,
            color = if (chosen) Oled.TextPrimary else Oled.TextSecondary.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Квадратик позначки (UGLY, AWKWARD…) з назвою; позначене — з галочкою. */
@Composable
private fun Check(text: String, checked: Boolean) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(12.dp).pencilFrame(3.dp, seed = text.length, alpha = if (checked) 0.95f else 0.5f, width = 1.3.dp, ink = if (checked) SelectedInk else SheetInk),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) GlyphIcon(Glyph.Save, null, tint = Oled.TextPrimary, size = 10.dp)
        }
        Spacer(Modifier.width(3.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Fit(text, 8f, if (checked) Oled.TextPrimary else Oled.TextSecondary, bold = checked, lines = 2, align = TextAlign.Start)
        }
    }
}

/** Перекреслена клітинка (як на бланку — пунктирний хрестик). */
@Composable
private fun Crossed() {
    Canvas(Modifier.fillMaxSize()) {
        val c = Oled.TextSecondary.copy(alpha = 0.45f)
        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
        drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), 1.dp.toPx(), pathEffect = dash)
        drawLine(c, Offset(0f, size.height), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = dash)
    }
}

/** Тонка лінія бланку: «Скло» — ледь світла риска, «Аркуш» — штрих олівцем. */
@Composable
fun FormLine(modifier: Modifier = Modifier, seed: Int = 3) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        if (AppStyle.glass) {
            drawLine(Color(0x22FFFFFF), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        } else {
            pencilStroke(size.height / 2, 0f, size.width, seed = seed, alpha = 0.3f)
        }
    }
}

/** Картка текстом — «Поділитися» (переслати в месенджер, вставити в WMS). */
fun cardText(context: Context, view: CardView): String {
    val comma = decimalComma()
    val s = view.stack
    fun m(mm: Int) = Num.metres(mm, comma)
    fun kg(g: Long) = Num.kilos(g, comma)
    val r = context.resources
    return buildString {
        appendLine("${r.getString(R.string.form_title)} · ${formDate(view.dateMs)}")
        appendLine("SKU: ${view.sku.ifBlank { r.getString(R.string.no_sku) }}")
        appendLine("${r.getString(R.string.col_box)}: ${m(view.box.first)} × ${m(view.box.second)} × ${m(view.box.third)} ${r.getString(R.string.unit_m)} · ${kg(view.boxG)} ${r.getString(R.string.unit_kg)} · ${view.unitsPerBox} ${r.getString(R.string.unit_pcs)}")
        view.unit?.let { u ->
            append("${r.getString(R.string.col_unit)}: ${m(u.first)} × ${m(u.second)} × ${m(u.third)} ${r.getString(R.string.unit_m)}")
            view.unitG?.let { append(" · ${kg(it)} ${r.getString(R.string.unit_kg)}") }
            appendLine()
        }
        if (s != null) {
            appendLine("${r.getString(R.string.col_pallet)}: ${s.boxes} · ${m(s.heightMm)} ${r.getString(R.string.unit_m)} · ${kg(s.weightG)} ${r.getString(R.string.unit_kg)}")
            appendLine("${r.getString(R.string.per_layer)}: ${r.getString(R.string.per_layer_value, s.perLayer.toLong() * view.unitsPerBox, s.perLayer)}" +
                if (s.perLayer2 != s.perLayer) " / ${r.getString(R.string.per_layer_value, s.perLayer2.toLong() * view.unitsPerBox, s.perLayer2)}" else "")
            append("${r.getString(R.string.layers_on_pallet)}: ${maxOf(s.layers, if (s.partial) 1 else 0)}")
        }
    }
}
