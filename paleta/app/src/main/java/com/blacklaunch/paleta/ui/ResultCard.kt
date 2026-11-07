package com.blacklaunch.paleta.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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

/** Ширини колонок таблиці розмірів — однакові в усіх рядках, щоб лінії сходились, як на папері. */
private val W_ARROW = 0.82f
private val W_PARAM = 1.28f
private const val W_COL = 1f
private val W_LEFT = W_ARROW + W_PARAM

/**
 * Бланк-результат — як паперова «Картка нового артикулу»: ті самі клітинки й лінії на тих самих місцях. Заповнюємо те,
 * що рахує програма (SKU, дата, розміри, кількості, ваги, на шарі, шарів, ТАК/НІ, позначки); решта (номер поставки,
 * назва, EAN, REC, підписи) — порожні клітинки, щоб дописати від руки. Так легко переписати рядок у рядок.
 */
@Composable
fun ResultCard(view: CardView, modifier: Modifier = Modifier) {
    val comma = decimalComma()
    val s = view.stack
    val unit = view.unit
    val kgUnit = stringResource(R.string.unit_kg)
    fun m(mm: Int?) = mm?.let { Num.metres(it, comma) } ?: ""
    fun kg(g: Long?) = g?.let { "${Num.kilos(it, comma)} $kgUnit" } ?: ""
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .pencilFrame(8.dp, seed = 61, alpha = 0.75f)
                .padding(2.dp),
        ) {
            // --- Шапка: номер поставки | дата; SKU | EAN; назва | REC ---
            val top = listOf(1.3f, 1.8f, 0.95f, 1.15f)
            GridRow(top) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_delivery), "(Pre-advice)")
                    2 -> Label(stringResource(R.string.form_date_label))
                    3 -> Value(formDate(view.dateMs))
                }
            }
            HLine()
            GridRow(top) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_sku), "(SKU)")
                    1 -> Value(view.sku, big = true)
                    2 -> Label(stringResource(R.string.form_ean))
                }
            }
            HLine()
            GridRow(top) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_name), "(Description)")
                    2 -> Label("REC")
                }
            }
            HLine(strong = true)
            // --- Розміри ---
            GridRow(listOf(1f)) { Label(stringResource(R.string.form_dims), center = true, bold = true) }
            HLine()
            val dims = listOf(W_ARROW, W_PARAM, W_COL, W_COL, W_COL)
            GridRow(dims) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_layer_by), small = true)
                    1 -> Label(stringResource(R.string.col_param), center = true, bold = true)
                    2 -> Label(stringResource(R.string.col_unit), "(each)", center = true, bold = true)
                    3 -> Label(stringResource(R.string.col_box), "(box)", center = true, bold = true)
                    4 -> Label(stringResource(R.string.col_pallet), "(pallet)", center = true, bold = true)
                }
            }
            HLine()
            DimRow(stringResource(R.string.row_length), "[Depth]", m(unit?.first), m(view.box.first), "")
            HLine()
            DimRow(stringResource(R.string.row_width), "[Width]", m(unit?.second), m(view.box.second), "")
            HLine()
            DimRow(stringResource(R.string.row_height), "[Height]", m(unit?.third), m(view.box.third), s?.let { m(it.heightMm) } ?: "")
            HLine()
            GridRow(dims) { i ->
                when (i) {
                    0 -> Crossed()
                    1 -> Label(stringResource(R.string.row_qty), center = true)
                    2 -> Value("1")
                    3 -> Value(view.unitsPerBox.toString(), note = stringResource(R.string.form_qty_box))
                    4 -> Value(s?.boxes?.toString() ?: "", note = stringResource(R.string.form_qty_pallet))
                }
            }
            HLine()
            GridRow(dims) { i ->
                when (i) {
                    0 -> Crossed()
                    1 -> Label(stringResource(R.string.row_weight_form), center = true)
                    2 -> Value(kg(view.unitG), end = true)
                    3 -> Value(kg(view.boxG), end = true)
                    4 -> Value(s?.let { kg(it.weightG) } ?: "", end = true)
                }
            }
            HLine(strong = true)
            // --- На шарі, шарів ---
            GridRow(listOf(W_LEFT, W_COL, W_COL, W_COL, W_COL)) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_per_layer), "(Eaches per layer)", center = true)
                    1 -> Label(stringResource(R.string.form_pcs), center = true, bold = true)
                    2 -> Value(s?.let { (it.perLayer.toLong() * view.unitsPerBox).toString() } ?: "")
                    3 -> Label(stringResource(R.string.form_boxes), center = true, bold = true)
                    4 -> Value(s?.perLayer?.toString() ?: "")
                }
            }
            HLine()
            GridRow(listOf(W_LEFT, 3 * W_COL)) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_layers), center = true)
                    1 -> Value(s?.let { if (it.partial) "0 (${stringResource(R.string.layers_partial)})" else it.layers.toString() } ?: "")
                }
            }
            HLine()
            // --- ТАК / НІ ---
            val yn = listOf(W_LEFT + W_COL, W_COL, W_COL)
            YesNo(stringResource(R.string.form_structure), stringResource(R.string.form_structure_sub), if (s == null) null else s.partial, yn)
            HLine()
            YesNo(stringResource(R.string.form_oversize), stringResource(R.string.form_oversize_sub), view.flags.oversize, yn)
            HLine()
            YesNo(stringResource(R.string.form_adr), "(HAZMAT)", view.adr, yn)
            HLine()
            YesNo(stringResource(R.string.form_inner), null, null, yn)
            HLine()
            // --- Познач поле ---
            GridRow(dims) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_mark), small = true)
                    1 -> Check("UGLY", false)
                    2 -> Check("AWKWARD", view.flags.awkward)
                    3 -> Check("TWO MEN LIFT", view.flags.twoMen)
                    4 -> Check("HANGING GARMENT", false)
                }
            }
            HLine(strong = true)
            // --- Підписи ---
            val sign = listOf(W_LEFT + W_COL, 2 * W_COL)
            GridRow(sign, minHeight = 46.dp) { i ->
                when (i) {
                    0 -> Label(stringResource(R.string.form_sign_struct), stringResource(R.string.form_sign_struct_sub), center = true)
                    1 -> Value(FormSigner.name)
                }
            }
            HLine()
            GridRow(sign, minHeight = 46.dp) { i -> if (i == 0) Label(stringResource(R.string.form_sign_wms), center = true) }
            HLine()
            GridRow(sign, minHeight = 46.dp) { i -> if (i == 0) Label(stringResource(R.string.form_sign_dok), center = true) }
        }
        // Пояснення під бланком: що обмежило, палета у вазі, важкий картон.
        val notes = buildList {
            if (s != null && s.limit == Limit.Weight && !s.partial && s.boxes > 0) add(stringResource(R.string.note_weight_limited, s.layersByHeight, s.layers))
            if (s?.partial == true) add(stringResource(R.string.note_partial))
            if (s != null && s.boxes == 0) add(stringResource(R.string.note_nothing_fits))
            if (view.palletG > 0) add(stringResource(R.string.note_pallet_included, Num.kilos(view.palletG, comma)))
            if (view.adr) add(stringResource(R.string.note_adr))
            if (view.flags.heavy) add(stringResource(R.string.note_heavy))
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { notes.forEach { FieldHint(it) } }
        }
    }
}

/** Рядок таблиці розмірів: стрілка, параметр, штука, картон, палета. */
@Composable
private fun DimRow(name: String, sub: String, unit: String, box: String, pallet: String) {
    GridRow(listOf(W_ARROW, W_PARAM, W_COL, W_COL, W_COL)) { i ->
        when (i) {
            0 -> Label("------>", center = true)
            1 -> Label(name, "$sub ${stringResource(R.string.form_in_metres)}", center = true)
            2 -> Value(unit)
            3 -> Value(box)
            4 -> Value(pallet, bold = true)
        }
    }
}

/** Рядок ТАК / НІ: вибране — з галочкою й яскраво; [yes] null — не позначено (дописати від руки). */
@Composable
private fun YesNo(name: String, sub: String?, yes: Boolean?, weights: List<Float>) {
    GridRow(weights) { i ->
        when (i) {
            0 -> Label(name, sub)
            1 -> Mark(stringResource(R.string.yes), yes == true)
            2 -> Mark(stringResource(R.string.no), yes == false)
        }
    }
}

/** Рядок клітинок з вертикальними лініями між ними; висота — за найвищою клітинкою. */
@Composable
private fun GridRow(weights: List<Float>, minHeight: Dp = 34.dp, cell: @Composable (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        weights.forEachIndexed { i, w ->
            if (i > 0) VLine(seed = i * 13 + weights.size)
            Box(
                Modifier.weight(w).fillMaxHeight().heightIn(min = minHeight).padding(horizontal = 5.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) { cell(i) }
        }
    }
}

/** Підпис клітинки (як надрукований текст бланку): назва і дрібніше пояснення під нею. */
@Composable
private fun Label(text: String, sub: String? = null, center: Boolean = false, bold: Boolean = false, small: Boolean = false) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            text,
            color = Oled.TextSecondary,
            fontSize = if (small) 10.sp else 12.sp,
            lineHeight = if (small) 12.sp else 14.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        )
        if (sub != null) Text(sub, color = Oled.TextSecondary.copy(alpha = 0.7f), fontSize = 9.sp, lineHeight = 11.sp, textAlign = if (center) TextAlign.Center else TextAlign.Start)
    }
}

/** Заповнене значення (як вписане ручкою): яскраво, по центру (вага — праворуч, як «… kg» на бланку). */
@Composable
private fun Value(text: String, big: Boolean = false, bold: Boolean = false, end: Boolean = false, note: String? = null) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (end) Alignment.End else Alignment.CenterHorizontally) {
        if (text.isNotEmpty()) {
            Text(
                text,
                color = Oled.TextPrimary,
                fontSize = if (big) 19.sp else 16.sp,
                fontWeight = if (bold || big) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (note != null) Text(note, color = Oled.TextSecondary.copy(alpha = 0.7f), fontSize = 9.sp, lineHeight = 11.sp, textAlign = TextAlign.Center)
    }
}

/** ТАК / НІ: вибране — з галочкою, яскраве; інше — тьмяне. */
@Composable
private fun Mark(text: String, chosen: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (chosen) {
            GlyphIcon(Glyph.Save, null, tint = Oled.TextPrimary, size = 16.dp)
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text,
            color = if (chosen) Oled.TextPrimary else Oled.TextSecondary.copy(alpha = 0.55f),
            fontSize = if (chosen) 16.sp else 14.sp,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Квадратик позначки (UGLY, AWKWARD…): позначене — з галочкою. */
@Composable
private fun Check(text: String, checked: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(18.dp).pencilFrame(4.dp, seed = text.length, alpha = if (checked) 0.95f else 0.5f, width = 1.6.dp, ink = if (checked) SelectedInk else SheetInk),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) GlyphIcon(Glyph.Save, null, tint = Oled.TextPrimary, size = 14.dp)
        }
        Spacer(Modifier.height(2.dp))
        Text(text, color = if (checked) Oled.TextPrimary else Oled.TextSecondary, fontSize = 9.sp, lineHeight = 10.sp, textAlign = TextAlign.Center, fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Перекреслена клітинка (як на бланку — пунктирний хрестик). */
@Composable
private fun Crossed() {
    Canvas(Modifier.fillMaxSize()) {
        val c = Oled.TextSecondary.copy(alpha = 0.45f)
        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), 1.dp.toPx(), pathEffect = dash)
        drawLine(c, Offset(0f, size.height), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = dash)
    }
}

/** Горизонтальна лінія сітки: «Скло» — ледь світла, «Аркуш» — штрих олівцем. [strong] — межа розділу. */
@Composable
private fun HLine(strong: Boolean = false) {
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        val y = size.height / 2
        if (AppStyle.glass) {
            drawLine(if (strong) Color(0x55FFFFFF) else Color(0x2EFFFFFF), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        } else {
            pencilTick(Offset(0f, y), Offset(size.width, y), (if (strong) 1.6f else 1.1f).dp.toPx(), SheetInk.copy(alpha = if (strong) 0.6f else 0.38f), (size.width % 97).toInt())
        }
    }
}

/** Вертикальна лінія сітки. */
@Composable
private fun VLine(seed: Int) {
    Canvas(Modifier.width(2.dp).fillMaxHeight()) {
        val x = size.width / 2
        if (AppStyle.glass) {
            drawLine(Color(0x2EFFFFFF), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        } else {
            pencilTick(Offset(x, 0f), Offset(x, size.height), 1.1.dp.toPx(), SheetInk.copy(alpha = 0.38f), seed)
        }
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
            appendLine("${r.getString(R.string.per_layer)}: ${r.getString(R.string.per_layer_value, s.perLayer.toLong() * view.unitsPerBox, s.perLayer)}")
            append("${r.getString(R.string.layers_on_pallet)}: ${if (s.partial) r.getString(R.string.layers_partial) else s.layers.toString()}")
        }
    }
}
