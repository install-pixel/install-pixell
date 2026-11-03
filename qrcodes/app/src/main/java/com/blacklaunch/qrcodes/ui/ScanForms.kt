package com.blacklaunch.qrcodes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.data.QrCode
import com.blacklaunch.qrcodes.qr.CodeKind
import com.blacklaunch.qrcodes.qr.FieldType
import com.blacklaunch.qrcodes.qr.FormField
import com.blacklaunch.qrcodes.qr.MoveKey
import com.blacklaunch.qrcodes.qr.ScanForm
import com.blacklaunch.qrcodes.qr.Symbol
import com.blacklaunch.qrcodes.ui.theme.Oled

/** Багаторядкове поле (варіанти вибору тощо) — у стилі інших полів вводу. */
@Composable
private fun AreaField(value: String, onChange: (String) -> Unit, placeholder: String, minLines: Int = 3) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (AppStyle.glass) {
                    Modifier.clip(shape).background(if (focused) Color(0x0FFFFFFF) else Color(0x08FFFFFF)).border(1.dp, if (focused) Color(0x99FFFFFF) else Color(0x38FFFFFF), shape)
                } else {
                    Modifier.pencilFrame(14.dp, seed = 41, alpha = if (focused) 0.9f else 0.6f)
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = Oled.TextSecondary, fontSize = 16.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = fieldStyle().copy(color = Oled.TextPrimary),
            cursorBrush = SolidColor(if (AppStyle.glass) Oled.TextPrimary else Oled.TextSecondary),
            minLines = minLines,
            maxLines = 10,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Маленька кругла кнопка-значок (у рядках полів). */
@Composable
private fun IconSpot(glyph: Glyph, description: String?, enabled: Boolean = true, tint: Color = Oled.TextSecondary, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).graphicsLayer { alpha = if (enabled) 1f else 0.3f },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, description, tint = tint, size = 20.dp)
    }
}

/** Список форм для сканера: дотик — відкрити й заповнити; олівець — змінити; кошик — видалити. */
@Composable
fun FormsListDialog(forms: List<ScanForm>, onDismiss: () -> Unit, onOpen: (ScanForm) -> Unit, onNew: () -> Unit, onEdit: (ScanForm) -> Unit, onDelete: (ScanForm) -> Unit) {
    var deleting by remember { mutableStateOf<ScanForm?>(null) }
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.forms_list_title), tall = true, buttons = {
        SheetButton(stringResource(R.string.forms_new), onNew, tone = Tone.Confirm)
    }) {
        Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (forms.isEmpty()) Text(stringResource(R.string.forms_empty), color = Oled.TextSecondary, fontSize = 15.sp)
            forms.forEach { f ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpen(f) }.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(f.name, color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.forms_fields_count, f.fields.size), color = Oled.TextSecondary, fontSize = 13.sp)
                    }
                    IconSpot(Glyph.Edit, stringResource(R.string.forms_edit)) { onEdit(f) }
                    IconSpot(Glyph.Delete, stringResource(R.string.delete), tint = Oled.Danger) { deleting = f }
                }
            }
            Text(stringResource(R.string.forms_list_hint), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
    deleting?.let { f ->
        SheetDialog(onDismissRequest = { deleting = null }, title = stringResource(R.string.forms_delete_title, f.name), buttons = {
            SheetButton(stringResource(R.string.cancel), { deleting = null })
            SheetButton(stringResource(R.string.delete), { onDelete(f); deleting = null }, tone = Tone.Danger)
        }) {
            Text(stringResource(R.string.forms_delete_text), fontSize = 15.sp)
        }
    }
}

private fun FieldType.title(): Int = when (this) {
    FieldType.TEXT -> R.string.ftype_text
    FieldType.SCAN -> R.string.ftype_scan
    FieldType.CHOICE -> R.string.ftype_choice
    FieldType.NUMBER -> R.string.ftype_number
    FieldType.DERIVED -> R.string.ftype_derived
    FieldType.DATE -> R.string.ftype_date
    FieldType.SKIP -> R.string.ftype_skip
}

private fun FieldType.hint(): Int = when (this) {
    FieldType.TEXT -> R.string.fhint_text
    FieldType.SCAN -> R.string.fhint_scan
    FieldType.CHOICE -> R.string.fhint_choice
    FieldType.NUMBER -> R.string.fhint_number
    FieldType.DERIVED -> R.string.fhint_derived
    FieldType.DATE -> R.string.fhint_date
    FieldType.SKIP -> R.string.fhint_skip
}

private fun FieldType.glyph(): Glyph = when (this) {
    FieldType.TEXT -> Glyph.TextT
    FieldType.SCAN -> Glyph.ScanQr
    FieldType.CHOICE -> Glyph.More
    FieldType.NUMBER -> Glyph.Plus
    FieldType.DERIVED -> Glyph.Copy
    FieldType.DATE -> Glyph.Grid
    FieldType.SKIP -> Glyph.RotateRight
}

/** Коротка назва клавіші переходу. */
@Composable
private fun moveName(k: Int): String = when (k) {
    MoveKey.FORM -> stringResource(R.string.forms_move_form)
    MoveKey.ENTER -> "Enter"
    MoveKey.TAB -> "Tab"
    MoveKey.DOWN -> "↓"
    MoveKey.RIGHT -> "→"
    else -> stringResource(R.string.key_none)
}

/** Повна назва клавіші (у списку вибору). */
@Composable
private fun moveLongName(k: Int): String = when (k) {
    MoveKey.DOWN -> stringResource(R.string.key_down)
    MoveKey.RIGHT -> stringResource(R.string.key_right)
    else -> moveName(k)
}

/** «↓ ×2», «Як у формі», «Нічого». */
@Composable
private fun moveSummary(k: Int, times: Int): String =
    moveName(k) + if (k in MoveKey.ENTER..MoveKey.RIGHT && times != 1) " ×$times" else ""

@Composable
private fun offSummary(f: FormField): String =
    if (f.offKey < 0) stringResource(R.string.forms_off_same) else moveName(f.offKey) + " ×${f.offTimes}"

/** Назва переходу між полями для всієї форми. */
@Composable
private fun betweenName(separator: Int, custom: String): String = when (separator) {
    1 -> "Tab"
    2 -> stringResource(R.string.fields_custom) + " «" + custom.ifEmpty { "|" } + "»"
    3 -> stringResource(R.string.fields_down_a)
    4 -> stringResource(R.string.fields_down_b)
    else -> "Enter"
}

/** Рядок-налаштування: підпис ліворуч, значення праворуч і «›» — відкриває вибір. Усі однакової висоти й форми. */
@Composable
private fun SettingRow(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .pencilFrame(14.dp, seed = label.length + 11, alpha = 0.35f)
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Oled.TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, color = Oled.TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).widthIn(max = 190.dp))
        Text("›", color = Oled.TextSecondary, fontSize = 18.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

/** «Разів: 3  − +». */
@Composable
private fun TimesStepper(times: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.forms_times, times), color = Oled.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        SheetButton("−", { onChange((times - 1).coerceAtLeast(1)) }, compact = true, modifier = Modifier.width(56.dp))
        SheetButton("+", { onChange((times + 1).coerceAtMost(99)) }, compact = true, modifier = Modifier.width(56.dp))
    }
}

/** Вибір одного з варіантів (однакові широкі кнопки), за потреби — «скільки разів» і підказка. */
@Composable
private fun OptionsDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onDismiss: () -> Unit,
    times: Int? = null,
    timesFor: (Int) -> Boolean = { false },
    hint: @Composable (Int) -> String? = { null },
    onPick: (Int, Int) -> Unit,
) {
    var sel by remember { mutableIntStateOf(selected) }
    var n by remember { mutableIntStateOf(times ?: 1) }
    SheetDialog(onDismissRequest = onDismiss, title = title, buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.apply), { onPick(sel, n) }, tone = Tone.Confirm)
    }) {
        Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (k, label) -> ChoiceChip(label, sel == k, Modifier.fillMaxWidth()) { sel = k } }
            if (times != null && timesFor(sel)) TimesStepper(n) { n = it }
            hint(sel)?.let { Text(it, color = Oled.TextSecondary, fontSize = 13.sp) }
        }
    }
}

/** Який вибір зараз відкритий у редакторі. */
private enum class PickKind { TYPE, AFTER, OFF, SOURCE, BETWEEN }

private data class Pick(val kind: PickKind, val index: Int = -1)

/**
 * Редактор форми («кухня»): назва, поля згорнутими картками (дотик — розгорнути), у кожного — тип, значення,
 * перехід після нього, чи можна його вимикати; унизу — налаштування всієї форми.
 */
@Composable
fun FormEditorDialog(initial: ScanForm?, onDismiss: () -> Unit, onSave: (ScanForm) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    val fields = remember { mutableStateListOf<FormField>().apply { addAll(initial?.fields ?: listOf(FormField("", FieldType.SCAN, switchable = false))) } }
    var separator by remember { mutableStateOf(initial?.separator ?: 0) }
    var custom by remember { mutableStateOf(initial?.custom ?: "|") }
    var endEnter by remember { mutableStateOf(initial?.endEnter ?: true) }
    var keepValues by remember { mutableStateOf(initial?.remember ?: false) }
    var expanded by remember { mutableIntStateOf(if (initial == null) 0 else -1) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    val today = remember { java.util.Date() }

    // Поле переїхало чи зникло — похідні поля далі беруть значення з того самого поля.
    fun remapSources(map: (Int) -> Int) {
        for (k in fields.indices) {
            val f = fields[k]
            if (f.source >= 0) fields[k] = f.copy(source = map(f.source))
        }
    }
    fun moveUp(i: Int) {
        if (i <= 0) return
        fields.add(i - 1, fields.removeAt(i))
        remapSources { s -> when (s) { i -> i - 1; i - 1 -> i; else -> s } }
        expanded = i - 1
    }
    fun moveDown(i: Int) {
        if (i >= fields.lastIndex) return
        fields.add(i + 1, fields.removeAt(i))
        remapSources { s -> when (s) { i -> i + 1; i + 1 -> i; else -> s } }
        expanded = i + 1
    }
    fun remove(i: Int) {
        fields.removeAt(i)
        remapSources { s -> when { s == i -> -1; s > i -> s - 1; else -> s } }
        expanded = -1
    }

    SheetDialog(onDismissRequest = onDismiss, title = stringResource(if (initial == null) R.string.forms_new else R.string.forms_edit), tall = true, buttons = {
        SheetButton(stringResource(R.string.cancel), onDismiss)
        SheetButton(stringResource(R.string.forms_save), {
            onSave(
                ScanForm(
                    id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(),
                    fields = fields.toList(),
                    separator = separator,
                    custom = custom,
                    endEnter = endEnter,
                    remember = keepValues,
                    // Поля змінились — старі значення вже не відповідають своїм місцям.
                    lastValues = if (initial?.fields?.size == fields.size) initial.lastValues else emptyList(),
                    lastEnabled = if (initial?.fields?.size == fields.size) initial.lastEnabled else emptyList(),
                    steps = initial?.steps ?: false,
                ),
            )
        }, tone = Tone.Confirm, enabled = name.isNotBlank() && fields.isNotEmpty())
    }) {
        Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetTextField(name, { name = it }, stringResource(R.string.forms_name_hint))
            Text(stringResource(R.string.forms_fields_header), color = Oled.TextSecondary, fontSize = 13.sp)
            fields.forEachIndexed { i, f ->
                val open = expanded == i
                Column(
                    Modifier.fillMaxWidth().pencilFrame(16.dp, seed = 50 + i, alpha = if (open) 0.7f else 0.4f).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Шапка картки: номер, значок типу, назва і коротко — що робить поле.
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = if (open) -1 else i }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", color = Oled.TextSecondary, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
                        GlyphIcon(f.type.glyph(), null, tint = Oled.TextSecondary, size = 20.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(f.label.ifBlank { stringResource(f.type.title()) }, color = Oled.TextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val extra = if (f.switchable) " · " + stringResource(R.string.forms_switch_short) else ""
                            Text(
                                stringResource(f.type.title()) + " · " + moveSummary(f.move, f.moveTimes) + extra,
                                color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(if (open) "▴" else "▾", color = Oled.TextSecondary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                    if (open) {
                        SheetTextField(f.label, { fields[i] = f.copy(label = it) }, stringResource(R.string.forms_label_hint))
                        SettingRow(stringResource(R.string.forms_type), stringResource(f.type.title())) { pick = Pick(PickKind.TYPE, i) }
                        when (f.type) {
                            FieldType.TEXT -> SheetTextField(f.value, { fields[i] = f.copy(value = it) }, stringResource(R.string.forms_text_value))
                            FieldType.NUMBER -> SheetTextField(f.value, { fields[i] = f.copy(value = it.filter { c -> c.isDigit() || c == '.' || c == ',' }) }, stringResource(R.string.forms_number_default), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            FieldType.CHOICE -> {
                                AreaField(f.options.joinToString("\n"), { fields[i] = f.copy(options = it.split('\n')) }, stringResource(R.string.forms_options_hint))
                                EqualWidthRow(Modifier.fillMaxWidth(), gap = 8.dp) {
                                    ChoiceChip(stringResource(R.string.forms_allow_manual), f.allowManual) { fields[i] = f.copy(allowManual = !f.allowManual) }
                                    ChoiceChip(stringResource(R.string.forms_allow_saved), f.allowSaved) { fields[i] = f.copy(allowSaved = !f.allowSaved) }
                                }
                            }
                            FieldType.DERIVED -> {
                                val src = fields.getOrNull(f.source)
                                SettingRow(
                                    stringResource(R.string.forms_source),
                                    if (src == null || f.source == i) "—" else "${f.source + 1} · " + src.label.ifBlank { stringResource(src.type.title()) },
                                ) { pick = Pick(PickKind.SOURCE, i) }
                            }
                            FieldType.DATE -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SheetTextField(f.datePattern, { fields[i] = f.copy(datePattern = it) }, "ddMMyyyy", Modifier.weight(1f))
                                    Text("→ «" + ScanForm.formatDate(f.datePattern, today) + "»", color = Oled.TextPrimary, fontSize = 15.sp)
                                }
                                Text(stringResource(R.string.forms_date_hint), color = Oled.TextSecondary, fontSize = 13.sp)
                            }
                            FieldType.SCAN, FieldType.SKIP -> {}
                        }
                        if (f.type != FieldType.SKIP) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SheetTextField(f.prefix, { fields[i] = f.copy(prefix = it) }, stringResource(R.string.forms_prefix), Modifier.weight(1f))
                            SheetTextField(f.suffix, { fields[i] = f.copy(suffix = it) }, stringResource(R.string.forms_suffix), Modifier.weight(1f))
                        }
                        SettingRow(
                            stringResource(if (f.type == FieldType.SKIP) R.string.forms_skip_keys else R.string.forms_after),
                            moveSummary(f.move, f.moveTimes),
                        ) { pick = Pick(PickKind.AFTER, i) }
                        EqualWidthRow(Modifier.fillMaxWidth(), gap = 8.dp) {
                            ChoiceChip(stringResource(R.string.forms_switchable), f.switchable) { fields[i] = f.copy(switchable = !f.switchable) }
                            if (f.switchable) ChoiceChip(stringResource(R.string.forms_on_default), f.onByDefault) { fields[i] = f.copy(onByDefault = !f.onByDefault) }
                        }
                        if (f.switchable) SettingRow(stringResource(R.string.forms_when_off), offSummary(f)) { pick = Pick(PickKind.OFF, i) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SheetButton(stringResource(R.string.forms_up), { moveUp(i) }, enabled = i > 0, compact = true, modifier = Modifier.weight(1f))
                            SheetButton(stringResource(R.string.forms_down), { moveDown(i) }, enabled = i < fields.lastIndex, compact = true, modifier = Modifier.weight(1f))
                            SheetButton(stringResource(R.string.delete), { remove(i) }, tone = Tone.Danger, enabled = fields.size > 1, compact = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            SheetButton(stringResource(R.string.fields_add), {
                if (fields.size < 40) {
                    fields.add(FormField("", FieldType.SCAN, switchable = false))
                    expanded = fields.lastIndex
                }
            }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.forms_general), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            SettingRow(stringResource(R.string.forms_between), betweenName(separator, custom)) { pick = Pick(PickKind.BETWEEN) }
            if (separator == 2) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetTextField(custom, { custom = it.replace("\n", "").replace("\t", "").take(3) }, "|", Modifier.width(88.dp))
                Text(stringResource(R.string.fields_custom_hint), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
            EqualWidthRow(Modifier.fillMaxWidth(), gap = 8.dp) {
                ChoiceChip(stringResource(R.string.fields_finish), endEnter) { endEnter = !endEnter }
                ChoiceChip(stringResource(R.string.forms_remember), keepValues) { keepValues = !keepValues }
            }
        }
    }

    pick?.let { p ->
        val f = fields.getOrNull(p.index)
        val close = { pick = null }
        when (p.kind) {
            PickKind.TYPE -> if (f != null) OptionsDialog(
                stringResource(R.string.forms_type),
                FieldType.entries.map { it.ordinal to stringResource(it.title()) },
                f.type.ordinal,
                onDismiss = close,
                hint = { stringResource(FieldType.entries[it].hint()) },
            ) { t, _ ->
                val type = FieldType.entries[t]
                fields[p.index] = if (type == FieldType.SKIP && f.type != type && f.move == MoveKey.FORM) f.copy(type = type, move = MoveKey.DOWN) else f.copy(type = type)
                close()
            }
            PickKind.AFTER -> if (f != null) OptionsDialog(
                stringResource(if (f.type == FieldType.SKIP) R.string.forms_skip_keys else R.string.forms_after),
                listOf(MoveKey.FORM, MoveKey.DOWN, MoveKey.RIGHT, MoveKey.TAB, MoveKey.ENTER, MoveKey.NONE).map {
                    it to if (it == MoveKey.FORM) moveName(it) + " (" + betweenName(separator, custom) + ")" else moveLongName(it)
                },
                f.move,
                onDismiss = close,
                times = f.moveTimes,
                timesFor = { it in MoveKey.ENTER..MoveKey.RIGHT },
            ) { k, n ->
                fields[p.index] = f.copy(move = k, moveTimes = n)
                close()
            }
            PickKind.OFF -> if (f != null) OptionsDialog(
                stringResource(R.string.forms_when_off),
                listOf(-1, MoveKey.DOWN, MoveKey.RIGHT, MoveKey.TAB).map { it to if (it < 0) stringResource(R.string.forms_off_same) else moveLongName(it) },
                f.offKey,
                onDismiss = close,
                times = f.offTimes,
                timesFor = { it >= 0 },
                hint = { stringResource(R.string.forms_off_hint) },
            ) { k, n ->
                fields[p.index] = f.copy(offKey = k, offTimes = n)
                close()
            }
            PickKind.SOURCE -> if (f != null) OptionsDialog(
                stringResource(R.string.forms_source),
                fields.indices.filter { it != p.index }.map { j -> j to "${j + 1} · " + fields[j].label.ifBlank { stringResource(fields[j].type.title()) } },
                f.source,
                onDismiss = close,
                hint = { stringResource(R.string.forms_derived_hint) },
            ) { j, _ ->
                fields[p.index] = f.copy(source = j)
                close()
            }
            PickKind.BETWEEN -> OptionsDialog(
                stringResource(R.string.forms_between),
                listOf(0, 1, 3, 4, 2).map { it to betweenName(it, custom) },
                separator,
                onDismiss = close,
                hint = { when (it) { 3, 4 -> stringResource(R.string.fields_down_hint); 2 -> stringResource(R.string.fields_custom_hint); else -> null } },
            ) { k, _ ->
                separator = k
                close()
            }
        }
    }
}

/**
 * Заповнення форми («вітрина»): угорі великий QR (оновлюється одразу), нижче — лише те, що треба міняти під час роботи:
 * поля для вводу (скан, вибір, число) і перемикачі «вкл / вимк» там, де їх задали. Сталі поля (текст, дата, похідні,
 * переходи) — у коді, але не на екрані. Яскравість — на максимум.
 */
@Composable
fun ScanFormScreen(
    form: ScanForm,
    saved: List<QrCode>,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onRemember: (ScanForm) -> Unit,
    onMessage: (String) -> Unit,
) {
    val n = form.fields.size
    val values = remember(form.id) {
        mutableStateListOf<String>().apply {
            addAll(
                if (form.remember && form.lastValues.size == n) form.lastValues
                else form.fields.map { f -> if (f.type == FieldType.CHOICE && f.value.isEmpty()) f.options.firstOrNull { it.isNotBlank() }.orEmpty() else f.value },
            )
        }
    }
    val enabled = remember(form.id) {
        mutableStateListOf<Boolean>().apply { addAll(if (form.remember && form.lastEnabled.size == n) form.lastEnabled else form.fields.map { it.onByDefault }) }
    }
    val today = remember { java.util.Date() }
    val content = form.content(values.toList(), enabled.toList(), today)
    val symbol = remember(content) { Symbol.encode(content, CodeKind.QR) }
    var steps by remember(form.id) { mutableStateOf(form.steps) }
    var step by remember(form.id) { mutableIntStateOf(0) }
    fun snapshot(st: Boolean) = form.copy(lastValues = values.toList(), lastEnabled = enabled.toList(), steps = st)
    var camFor by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var pickFor by remember { mutableStateOf<Int?>(null) }
    var choiceFor by remember { mutableStateOf<Int?>(null) }
    val codes = remember(saved) { saved.filter { !it.isForm } }
    androidx.activity.compose.BackHandler(enabled = camFor == null) { onClose() }

    // Яскравість — на максимум, поки форма відкрита.
    val activity = androidx.activity.compose.LocalActivity.current
    DisposableEffect(activity) {
        val w = activity?.window
        w?.attributes = w?.attributes?.apply { screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { w?.attributes = w?.attributes?.apply { screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }
    // Пам'ять введеного — якщо так задано у формі.
    if (form.remember) {
        LaunchedEffect(content) {
            kotlinx.coroutines.delay(500)
            onRemember(snapshot(steps))
        }
    }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    // Поки відкрита клавіатура, код менший — щоб поле, яке вводиш, було видно.
    val side = if (imeOpen) 150.dp else minOf(width * 0.68f, 300.dp)

    Box(Modifier.fillMaxSize().background(Oled.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.Back, stringResource(R.string.back)) }
                Text(form.name, color = Oled.TextPrimary, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                IconSpot(Glyph.Edit, stringResource(R.string.forms_edit), onClick = onEdit)
            }
            // Код — угорі, великий. Усе навколо нього має сталі місця: «‹ ›» — з боків (у режимі «Один код» місце лишається
            // порожнім), підпис — під кодом у рядку сталої висоти, тож при перемиканні нічого не смикається.
            val active = form.activeSteps(values.toList(), enabled.toList(), today)
            val cur = step.coerceIn(0, (active.size - 1).coerceAtLeast(0))
            val idx = if (steps) active.getOrNull(cur) else null
            val stepText = idx?.let { form.part(it, values.toList(), enabled.toList(), today) }.orEmpty()
            val stepSymbol = remember(stepText) { if (stepText.isEmpty()) null else Symbol.encode(stepText, CodeKind.QR) }
            val shownSymbol = if (steps) stepSymbol else symbol
            val canStep = steps && active.size > 1
            val next = { step = if (cur >= active.lastIndex) 0 else cur + 1 }
            val qrSide = minOf(side, width - 32.dp - 120.dp)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) {
                        if (canStep) SheetButton("‹", { step = if (cur == 0) active.lastIndex else cur - 1 }, compact = true, modifier = Modifier.width(52.dp))
                    }
                    Box(
                        Modifier.weight(1f).height(qrSide).clip(RoundedCornerShape(12.dp)).clickable(enabled = canStep) { next() },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            shownSymbol != null -> CodeImage(shownSymbol, Modifier.size(qrSide))
                            steps && idx == null -> Text(stringResource(R.string.forms_step_empty), color = Oled.TextSecondary, fontSize = 16.sp, textAlign = TextAlign.Center)
                            else -> Text(stringResource(R.string.too_long), color = Oled.Danger, fontSize = 16.sp, textAlign = TextAlign.Center)
                        }
                    }
                    Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
                        if (canStep) SheetButton("›", { next() }, compact = true, modifier = Modifier.width(52.dp))
                    }
                }
                val info = when {
                    !steps -> stringResource(R.string.forms_one_hint)
                    idx == null -> ""
                    else -> {
                        val label = form.fields[idx].label.ifBlank { stringResource(R.string.forms_field_n).format(idx + 1) }
                        val skip = form.skipBefore(active, cur)
                        stringResource(R.string.forms_step, cur + 1, active.size, label) + if (skip > 0) " · " + stringResource(R.string.forms_step_skip, skip) else ""
                    }
                }
                Box(Modifier.fillMaxWidth().height(44.dp).padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    Text(info, color = Oled.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                EqualWidthRow(Modifier.fillMaxWidth(), gap = 8.dp) {
                    ChoiceChip(stringResource(R.string.forms_mode_one), !steps) { steps = false; onRemember(snapshot(false)) }
                    ChoiceChip(stringResource(R.string.forms_mode_steps), steps) { steps = true; step = 0; onRemember(snapshot(true)) }
                }
            }
            // Те, що міняєш під час роботи.
            val shown = form.fields.indices.filter { form.fields[it].shownWhenFilling }
            Column(
                Modifier.weight(1f).fillMaxWidth().bouncyScroll().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shown.isEmpty()) Text(stringResource(R.string.forms_nothing_to_fill), color = Oled.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                shown.forEach { i ->
                    val f = form.fields[i]
                    val on = !f.switchable || enabled.getOrElse(i) { true }
                    Column(
                        Modifier.fillMaxWidth().pencilFrame(16.dp, seed = 70 + i, alpha = if (on) 0.5f else 0.25f).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                f.label.ifBlank { stringResource(R.string.forms_field_n).format(i + 1) },
                                color = if (on) Oled.TextPrimary else Oled.TextSecondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            // Сталі поля (текст, дата, похідне, перехід) — значення тут же, в одному рядку з перемикачем.
                            val fixed = when (f.type) {
                                FieldType.SKIP -> moveSummary(f.move, f.moveTimes)
                                FieldType.TEXT, FieldType.DERIVED, FieldType.DATE -> form.part(i, values.toList(), List(n) { true }, today).ifEmpty { "—" }
                                else -> null
                            }
                            if (fixed != null) Text(
                                fixed, color = Oled.TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp).widthIn(max = 150.dp).graphicsLayer { alpha = if (on) 1f else 0.4f },
                            )
                            if (f.switchable) ChoiceChip(stringResource(if (on) R.string.forms_on else R.string.forms_off), on, Modifier.width(104.dp)) { enabled[i] = !on }
                        }
                        // Поле вводу лишається на місці й тоді, коли вимкнене (лише тьмяніє) — список не стрибає.
                        if (f.type == FieldType.SCAN || f.type == FieldType.NUMBER || f.type == FieldType.CHOICE) Row(
                            Modifier.fillMaxWidth().graphicsLayer { alpha = if (on) 1f else 0.35f },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when (f.type) {
                                FieldType.SKIP, FieldType.TEXT, FieldType.DERIVED, FieldType.DATE -> {}
                                FieldType.SCAN -> {
                                    SheetTextField(values[i], { values[i] = it.replace("\n", " ").replace("\t", " ") }, stringResource(R.string.forms_scan_placeholder), Modifier.weight(1f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                                    IconSpot(Glyph.ScanQr, stringResource(R.string.mode_scan)) { camFor = i to true }
                                    IconSpot(Glyph.Photo, stringResource(R.string.mode_photo)) { camFor = i to false }
                                }
                                FieldType.NUMBER -> {
                                    SheetTextField(values[i], { values[i] = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "0", Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
                                    if (f.suffix.isNotEmpty()) Text(f.suffix, color = Oled.TextSecondary, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                                    SheetButton("−", { values[i] = ((values[i].toIntOrNull() ?: 0) - 1).coerceAtLeast(0).toString() }, compact = true, modifier = Modifier.width(52.dp).padding(start = 8.dp))
                                    SheetButton("+", { values[i] = ((values[i].toIntOrNull() ?: 0) + 1).toString() }, compact = true, modifier = Modifier.width(52.dp).padding(start = 8.dp))
                                }
                                FieldType.CHOICE -> {
                                    val opts = f.options.filter { it.isNotBlank() }
                                    if (opts.size > 1) SheetButton("‹", {
                                        val k = opts.indexOf(values[i]); values[i] = opts[if (k <= 0) opts.lastIndex else k - 1]
                                    }, compact = true, modifier = Modifier.width(52.dp))
                                    if (f.allowManual) {
                                        SheetTextField(values[i], { values[i] = it.replace("\n", " ").replace("\t", " ") }, stringResource(R.string.forms_choice_placeholder), Modifier.weight(1f).padding(horizontal = 8.dp))
                                    } else {
                                        Text(
                                            values[i].ifEmpty { "—" },
                                            color = Oled.TextPrimary,
                                            fontSize = 17.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = opts.isNotEmpty()) { choiceFor = i }.padding(vertical = 10.dp),
                                        )
                                    }
                                    if (opts.size > 1) SheetButton("›", {
                                        val k = opts.indexOf(values[i]); values[i] = opts[if (k < 0 || k >= opts.lastIndex) 0 else k + 1]
                                    }, compact = true, modifier = Modifier.width(52.dp))
                                    if (opts.isNotEmpty()) IconSpot(Glyph.More, stringResource(R.string.forms_choose)) { choiceFor = i }
                                    if (f.allowSaved && codes.isNotEmpty()) IconSpot(Glyph.Folder, stringResource(R.string.fields_from_saved)) { pickFor = i }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        camFor?.let { (index, qr) ->
            com.blacklaunch.qrcodes.scan.CameraScreen(
                scanQr = qr,
                onResult = { text, _ ->
                    if (index < values.size) values[index] = text.replace("\n", " ").replace("\t", " ").trim()
                    camFor = null
                },
                onClose = { camFor = null },
                onMessage = onMessage,
            )
        }
    }
    pickFor?.let { index ->
        SavedCodePicker(codes, onDismiss = { pickFor = null }) { code ->
            if (index < values.size) values[index] = code.content.replace("\n", " ").replace("\t", " ")
            pickFor = null
        }
    }
    choiceFor?.let { index ->
        val f = form.fields[index]
        SheetDialog(onDismissRequest = { choiceFor = null }, title = f.label.ifBlank { stringResource(R.string.forms_choose) }, tall = true) {
            Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                f.options.filter { it.isNotBlank() }.forEach { o ->
                    ChoiceChip(o, values.getOrNull(index) == o, Modifier.fillMaxWidth()) { values[index] = o; choiceFor = null }
                }
            }
        }
    }
}
