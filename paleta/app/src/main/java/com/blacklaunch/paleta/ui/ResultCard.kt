package com.blacklaunch.paleta.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.unit.TextUnit
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

/**
 * Бланк-результат — схожий на паперову «Картку нового артикулу», щоб легко переписати: дата (сама), SKU,
 * таблиця Штука / Картон / Палета (довжина, ширина, висота, кількість, вага), на шарі, шарів, позначки.
 * Колонка «Штука» — лише якщо дані штуки вписали.
 */
@Composable
fun ResultCard(view: CardView, modifier: Modifier = Modifier) {
    val comma = decimalComma()
    val dash = "—"
    val s = view.stack
    val unit = view.unit
    val showUnit = unit != null || view.unitG != null
    fun m(mm: Int?) = mm?.let { Num.metres(it, comma) } ?: dash
    fun kg(g: Long?) = g?.let { Num.kilos(it, comma) } ?: dash
    Column(
        modifier
            .fillMaxWidth()
            .pencilFrame(18.dp, seed = 61)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.form_title), color = Oled.TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.form_date, formDate(view.dateMs)), color = Oled.TextPrimary, fontSize = 15.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("SKU", color = Oled.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(end = 10.dp, bottom = 2.dp))
            Text(view.sku.ifBlank { stringResource(R.string.no_sku) }, color = if (view.sku.isBlank()) Oled.TextSecondary else Oled.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FormLine()
        // Заголовок таблиці.
        TableRow(
            stringResource(R.string.col_param),
            if (showUnit) stringResource(R.string.col_unit) else null,
            stringResource(R.string.col_box),
            stringResource(R.string.col_pallet),
            header = true,
        )
        TableRow(stringResource(R.string.row_length), if (showUnit) m(unit?.first) else null, m(view.box.first), "")
        TableRow(stringResource(R.string.row_width), if (showUnit) m(unit?.second) else null, m(view.box.second), "")
        TableRow(stringResource(R.string.row_height), if (showUnit) m(unit?.third) else null, m(view.box.third), s?.let { m(it.heightMm) } ?: dash, strongLast = true)
        TableRow(stringResource(R.string.row_qty), if (showUnit) "1" else null, view.unitsPerBox.toString(), s?.boxes?.toString() ?: dash, strongLast = true)
        TableRow(stringResource(R.string.row_weight), if (showUnit) kg(view.unitG) else null, kg(view.boxG), s?.let { kg(it.weightG) } ?: dash, strongLast = true)
        FormLine()
        if (s != null) {
            Pair(stringResource(R.string.per_layer), stringResource(R.string.per_layer_value, s.perLayer.toLong() * view.unitsPerBox, s.perLayer)).let { (a, b) -> InfoRow(a, b) }
            InfoRow(stringResource(R.string.layers_on_pallet), if (s.partial) stringResource(R.string.layers_partial) else s.layers.toString())
            InfoRow(stringResource(R.string.units_on_pallet), (s.boxes.toLong() * view.unitsPerBox).toString())
            FormLine()
        }
        // Позначки з бланку: ТАК яскраво, НІ сіро.
        YesNoRow(stringResource(R.string.flag_structure), s?.partial == true)
        YesNoRow(stringResource(R.string.flag_oversize), view.flags.oversize)
        YesNoRow(stringResource(R.string.flag_adr), view.adr)
        YesNoRow(stringResource(R.string.flag_awkward), view.flags.awkward)
        YesNoRow(stringResource(R.string.flag_two_men), view.flags.twoMen)
        // Пояснення: що обмежило, палета у вазі, важкий картон.
        val notes = buildList {
            if (s != null && s.limit == Limit.Weight && !s.partial) add(stringResource(R.string.note_weight_limited, s.layersByHeight, s.layers))
            if (s?.partial == true) add(stringResource(R.string.note_partial))
            if (view.palletG > 0) add(stringResource(R.string.note_pallet_included, Num.kilos(view.palletG, comma)))
            if (view.adr) add(stringResource(R.string.note_adr))
            if (view.flags.heavy) add(stringResource(R.string.note_heavy))
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            notes.forEach { FieldHint(it) }
        }
    }
}

/** Рядок таблиці: назва, штука (null — колонки немає), картон, палета. */
@Composable
private fun TableRow(name: String, unit: String?, box: String, pallet: String, header: Boolean = false, strongLast: Boolean = false) {
    val size: TextUnit = if (header) 13.sp else 16.sp
    val color = if (header) Oled.TextSecondary else Oled.TextPrimary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Oled.TextSecondary, fontSize = if (header) 13.sp else 14.sp, modifier = Modifier.weight(1.35f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (unit != null) Cell(unit, size, color)
        Cell(box, size, color)
        Cell(pallet, size, if (strongLast && !header) Oled.TextPrimary else color, bold = strongLast && !header)
    }
}

@Composable
private fun RowScope.Cell(text: String, size: TextUnit, color: Color, bold: Boolean = false) {
    Text(text, color = color, fontSize = size, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
}

@Composable
private fun InfoRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Oled.TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Oled.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun YesNoRow(name: String, yes: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Oled.TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (yes) {
            GlyphIcon(Glyph.Save, null, tint = Oled.TextPrimary, size = 16.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(stringResource(if (yes) R.string.yes else R.string.no), color = if (yes) Oled.TextPrimary else Oled.TextSecondary.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = if (yes) FontWeight.SemiBold else FontWeight.Normal)
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
