package com.blacklaunch.supps.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.supps.AppData
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.Form
import com.blacklaunch.supps.data.Phase
import com.blacklaunch.supps.data.Plan
import com.blacklaunch.supps.data.Purchase
import com.blacklaunch.supps.data.Schedule
import com.blacklaunch.supps.data.Slot
import com.blacklaunch.supps.data.Supplement
import com.blacklaunch.supps.ui.theme.Oled

/** Що редактор віддає на збереження. [purchase] — «купив нову банку» (запишеться в історію витрат). */
data class EditResult(val supplement: Supplement, val doneDays: Int?, val newGroupName: String?, val newGroupBreak: Int, val purchase: Purchase?)

private fun numText(v: Double?): String = v?.let { num(it) }.orEmpty()

/** Готові схеми для нової добавки: щоб не вигадувати цифри з нуля. */
private enum class Preset(val plan: Plan, val on: Int, val off: Int) {
    Daily(Plan.Daily, 0, 0),
    Cycle8x2(Plan.Cycle, 56, 14),
    Cycle12x4(Plan.Cycle, 84, 28),
    CycleCustom(Plan.Cycle, 0, 0),
    Group(Plan.Group, 56, 0),
    AsNeeded(Plan.AsNeeded, 0, 0),
}

@Composable
private fun presetName(p: Preset): String = stringResource(
    when (p) {
        Preset.Daily -> R.string.plan_daily
        Preset.Cycle8x2 -> R.string.preset_cycle_8_2
        Preset.Cycle12x4 -> R.string.preset_cycle_12_4
        Preset.CycleCustom -> R.string.preset_cycle_custom
        Preset.Group -> R.string.plan_group
        Preset.AsNeeded -> R.string.plan_as_needed
    },
)

/**
 * Картка добавки — усе про неї в одному місці: що й скільки, коли, схема, банка (залишок, ціна, «купив нову»),
 * нотатка, видалення. Невідоме можна лишити порожнім — тоді просто не буде відповідного розрахунку.
 */
@Composable
fun EditorScreen(
    original: Supplement?,
    data: AppData,
    today: Long,
    currency: String,
    onBack: () -> Unit,
    onSave: (EditResult) -> Unit,
    onDelete: (Supplement) -> Unit,
) {
    val base = original ?: Supplement(name = "")
    var name by rememberSaveable { mutableStateOf(base.name) }
    var category by rememberSaveable { mutableStateOf(base.category) }
    var form by rememberSaveable { mutableStateOf(base.formE) }
    var mg by rememberSaveable { mutableStateOf(numText(base.mgPerUnit)) }
    var perDose by rememberSaveable { mutableStateOf(num(base.unitsPerDose)) }
    var slot by rememberSaveable { mutableStateOf(base.slotE) }
    var plan by rememberSaveable { mutableStateOf(base.planE) }
    var onDays by rememberSaveable { mutableStateOf(if (base.onDays > 0) base.onDays.toString() else "") }
    var offDays by rememberSaveable { mutableStateOf(if (base.offDays > 0) base.offDays.toString() else "") }
    var done by rememberSaveable { mutableStateOf("") }
    // Група: id наявної або -1 — нова.
    var groupId by rememberSaveable { mutableStateOf(base.groupId ?: data.groups.firstOrNull()?.id ?: -1L) }
    var newGroupName by rememberSaveable { mutableStateOf("") }
    var newGroupBreak by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf(numText(base.unitsLeft)) }
    var pack by rememberSaveable { mutableStateOf(base.packUnits?.toString().orEmpty()) }
    var price by rememberSaveable { mutableStateOf(numText(base.packPrice)) }
    var note by rememberSaveable { mutableStateOf(base.note) }
    // «Купив нову банку»: кількість і ціна — запишуться в історію витрат під час збереження.
    var boughtUnits by rememberSaveable { mutableStateOf<Int?>(null) }
    var boughtPrice by rememberSaveable { mutableStateOf<Double?>(null) }
    var bought by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }

    val unit = unitShort(form)
    val needDays = plan == Plan.Cycle || plan == Plan.Group
    val valid = name.isNotBlank() && (!needDays || (parseInt(onDays) ?: 0) >= 1) && (parseNumber(perDose) ?: 0.0) > 0
    val categories = remember(data) { data.active.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted() }

    val currentStatus = original?.let { Schedule.status(it, today, data.groups, data.supplements) }
    val nowHint = if (original != null && currentStatus != null && currentStatus.phase == Phase.Active && currentStatus.length > 0 && original.planE == plan) {
        stringResource(R.string.edit_done_now, currentStatus.dayNo)
    } else {
        null
    }

    FullPage(
        title = if (original == null) stringResource(R.string.add_supplement) else original.name,
        onBack = onBack,
        actions = {
            SheetButton(
                stringResource(R.string.save),
                {
                    val isNewGroup = plan == Plan.Group && groupId == -1L
                    val s = base.copy(
                        name = name.trim().take(80),
                        category = category.trim().take(40),
                        form = form.name,
                        mgPerUnit = parseNumber(mg)?.takeIf { it > 0 },
                        unitsPerDose = (parseNumber(perDose) ?: 1.0).coerceIn(0.01, 1000.0),
                        slot = slot.name,
                        plan = plan.name,
                        onDays = if (needDays) (parseInt(onDays) ?: 1).coerceIn(1, 3650) else 0,
                        offDays = if (plan == Plan.Cycle) (parseInt(offDays) ?: 0).coerceIn(0, 3650) else 0,
                        groupId = if (plan == Plan.Group && !isNewGroup) groupId else null,
                        unitsLeft = parseNumber(stock),
                        packUnits = parseInt(pack)?.takeIf { it > 0 },
                        packPrice = parseNumber(price),
                        note = note.trim().take(300),
                    )
                    val doneDays = if (needDays) parseInt(done)?.coerceIn(0, 3650) else null
                    val purchase = if (bought) Purchase(supplementId = 0, day = today, units = boughtUnits, price = boughtPrice) else null
                    onSave(EditResult(s, doneDays, if (isNewGroup) newGroupName else null, parseInt(newGroupBreak) ?: 0, purchase))
                },
                tone = Tone.Confirm,
                enabled = valid,
            )
        },
    ) {
        Column(Modifier.fillMaxSize().imePadding().bouncyScroll().padding(horizontal = 16.dp)) {
            LabeledField(stringResource(R.string.edit_name), name, { name = it }, placeholder = stringResource(R.string.edit_name_hint))
            SettingRow(stringResource(R.string.edit_category), category.ifEmpty { stringResource(R.string.category_none) }, { picker = "category" })
            SettingRow(stringResource(R.string.edit_form), formName(form), { picker = "form" })
            LabeledField(stringResource(R.string.edit_per_dose, unit), perDose, { perDose = it }, placeholder = "1", keyboardType = KeyboardType.Decimal)
            LabeledField(stringResource(R.string.edit_mg, unit), mg, { mg = it }, placeholder = stringResource(R.string.unknown), hint = stringResource(R.string.edit_mg_hint), keyboardType = KeyboardType.Decimal)
            SettingRow(stringResource(R.string.edit_slot), slotName(slot), { picker = "slot" })

            SectionTitle(stringResource(R.string.edit_schedule))
            SettingRow(stringResource(R.string.edit_plan), planName(plan), { picker = "plan" })
            when (plan) {
                Plan.Cycle -> {
                    LabeledField(stringResource(R.string.edit_on_days), onDays, { onDays = it.filter { c -> c.isDigit() }.take(4) }, placeholder = "84", keyboardType = KeyboardType.Number)
                    LabeledField(stringResource(R.string.edit_off_days), offDays, { offDays = it.filter { c -> c.isDigit() }.take(4) }, placeholder = "28", keyboardType = KeyboardType.Number)
                    LabeledField(
                        stringResource(R.string.edit_done),
                        done,
                        { done = it.filter { c -> c.isDigit() }.take(4) },
                        placeholder = stringResource(if (original == null) R.string.edit_done_placeholder else R.string.edit_done_placeholder_group),
                        hint = nowHint ?: stringResource(R.string.edit_done_hint),
                        keyboardType = KeyboardType.Number,
                    )
                }
                Plan.Group -> {
                    val groupLabel = data.groups.firstOrNull { it.id == groupId }?.name ?: stringResource(R.string.group_new)
                    SettingRow(stringResource(R.string.edit_group), groupLabel, { picker = "group" })
                    if (groupId == -1L) {
                        LabeledField(stringResource(R.string.group_name), newGroupName, { newGroupName = it }, placeholder = stringResource(R.string.group_name_hint))
                        LabeledField(stringResource(R.string.group_break), newGroupBreak, { newGroupBreak = it.filter { c -> c.isDigit() }.take(4) }, placeholder = "0", keyboardType = KeyboardType.Number)
                    }
                    LabeledField(stringResource(R.string.edit_block_days), onDays, { onDays = it.filter { c -> c.isDigit() }.take(4) }, placeholder = "56", hint = stringResource(R.string.edit_block_hint), keyboardType = KeyboardType.Number)
                    LabeledField(
                        stringResource(R.string.edit_done),
                        done,
                        { done = it.filter { c -> c.isDigit() }.take(4) },
                        placeholder = stringResource(R.string.edit_done_placeholder_group),
                        hint = nowHint ?: stringResource(R.string.edit_done_hint_group),
                        keyboardType = KeyboardType.Number,
                    )
                }
                Plan.Daily, Plan.AsNeeded -> Unit
            }

            SectionTitle(stringResource(R.string.edit_stock_section))
            LabeledField(stringResource(R.string.edit_stock, unit), stock, { stock = it }, placeholder = stringResource(R.string.unknown), hint = stringResource(R.string.edit_stock_hint), keyboardType = KeyboardType.Decimal)
            LabeledField(stringResource(R.string.edit_pack, unit), pack, { pack = it.filter { c -> c.isDigit() }.take(6) }, placeholder = stringResource(R.string.unknown), keyboardType = KeyboardType.Number)
            LabeledField(stringResource(R.string.edit_price, currency), price, { price = it }, placeholder = stringResource(R.string.unknown), hint = stringResource(R.string.edit_price_hint), keyboardType = KeyboardType.Decimal)
            SettingRow(stringResource(R.string.purchase), if (bought) stringResource(R.string.purchase_pending) else "", { picker = "purchase" })

            SectionTitle(stringResource(R.string.edit_note_section))
            LabeledField(stringResource(R.string.edit_note), note, { note = it }, placeholder = stringResource(R.string.edit_note_hint), maxLength = 300)
            if (original != null) {
                Spacer(Modifier.height(12.dp))
                MenuRow(Glyph.Delete, stringResource(R.string.delete), { picker = "delete" }, tone = Tone.Danger)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (picker) {
        "category" -> {
            val options = categories + stringResource(R.string.category_none) + stringResource(R.string.category_new)
            val selected = categories.indexOf(category.trim()).let { if (it >= 0) it else if (category.isBlank()) categories.size else -1 }
            OptionsDialog(stringResource(R.string.edit_category), options, selected, { if (picker == "category") picker = null }, hint = stringResource(R.string.category_hint)) { i ->
                when {
                    i < categories.size -> category = categories[i]
                    i == categories.size -> category = ""
                    else -> picker = "categoryNew"
                }
            }
        }
        "categoryNew" -> TextInputDialog(stringResource(R.string.category_new), "", stringResource(R.string.category_example), { picker = null }, allowEmpty = false) { category = it.take(40) }
        "form" -> OptionsDialog(stringResource(R.string.edit_form), Form.entries.map { formName(it) }, Form.entries.indexOf(form), { picker = null }) { form = Form.entries[it] }
        "slot" -> OptionsDialog(stringResource(R.string.edit_slot), Slot.entries.map { slotName(it) }, Slot.entries.indexOf(slot), { picker = null }, hint = stringResource(R.string.edit_slot_hint)) { slot = Slot.entries[it] }
        "plan" -> {
            val presets = Preset.entries
            val current = when {
                plan == Plan.Cycle && onDays == "56" && offDays == "14" -> Preset.Cycle8x2
                plan == Plan.Cycle && onDays == "84" && offDays == "28" -> Preset.Cycle12x4
                plan == Plan.Cycle -> Preset.CycleCustom
                plan == Plan.Group -> Preset.Group
                plan == Plan.AsNeeded -> Preset.AsNeeded
                else -> Preset.Daily
            }
            OptionsDialog(stringResource(R.string.edit_plan), presets.map { presetName(it) }, presets.indexOf(current), { picker = null }, hint = stringResource(R.string.edit_plan_hint)) { i ->
                val p = presets[i]
                plan = p.plan
                if (p.on > 0) onDays = p.on.toString()
                if (p.plan == Plan.Cycle && p.on > 0) offDays = p.off.toString()
            }
        }
        "group" -> {
            val options = data.groups.map { it.name } + stringResource(R.string.group_new)
            val selected = data.groups.indexOfFirst { it.id == groupId }.let { if (it < 0) options.size - 1 else it }
            OptionsDialog(stringResource(R.string.edit_group), options, selected, { picker = null }) { i ->
                groupId = if (i < data.groups.size) data.groups[i].id else -1L
            }
        }
        "purchase" -> PurchaseDialog(
            unit = unit,
            currency = currency,
            initialUnits = parseInt(pack),
            initialPrice = parseNumber(price),
            stockKnown = parseNumber(stock) != null,
            onDismiss = { picker = null },
        ) { units, cost, add ->
            bought = true
            boughtUnits = units
            boughtPrice = cost
            if (units != null) {
                val left = parseNumber(stock)
                stock = num(if (add && left != null) left + units else units.toDouble())
                pack = units.toString()
            }
            if (cost != null) price = num(cost)
        }
        "delete" -> SheetDialog(
            onDismissRequest = { picker = null },
            title = stringResource(R.string.delete_q, base.name),
            buttons = {
                SheetButton(stringResource(R.string.cancel), { picker = null })
                SheetButton(stringResource(R.string.delete), { picker = null; original?.let(onDelete) }, tone = Tone.Danger)
            },
        ) { Text(stringResource(R.string.delete_body), color = Oled.TextSecondary, fontSize = 15.sp) }
    }
}

/** Купив банку: кількість, ціна, додати до залишку чи почати з нової. Застосується разом із «Зберегти». */
@Composable
private fun PurchaseDialog(
    unit: String,
    currency: String,
    initialUnits: Int?,
    initialPrice: Double?,
    stockKnown: Boolean,
    onDismiss: () -> Unit,
    onApply: (Int?, Double?, Boolean) -> Unit,
) {
    var units by remember { mutableStateOf(initialUnits?.toString().orEmpty()) }
    var price by remember { mutableStateOf(initialPrice?.let { num(it) }.orEmpty()) }
    var add by remember { mutableStateOf(true) }
    SheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.purchase),
        buttons = {
            SheetButton(stringResource(R.string.cancel), onDismiss)
            SheetButton(stringResource(R.string.apply), { onApply(parseInt(units)?.takeIf { it > 0 }, parseNumber(price), add); onDismiss() }, tone = Tone.Confirm)
        },
    ) {
        Column(Modifier.bouncyScroll()) {
            LabeledField(stringResource(R.string.edit_pack, unit), units, { units = it.filter { c -> c.isDigit() }.take(6) }, placeholder = stringResource(R.string.unknown), keyboardType = KeyboardType.Number)
            LabeledField(stringResource(R.string.edit_price, currency), price, { price = it }, placeholder = stringResource(R.string.unknown), keyboardType = KeyboardType.Decimal)
            if (stockKnown) {
                Spacer(Modifier.height(6.dp))
                SwitchRow(stringResource(R.string.purchase_add), add, subtitle = stringResource(R.string.purchase_add_desc)) { add = it }
            } else {
                WindowHint(stringResource(R.string.purchase_unknown_stock))
            }
            WindowHint(stringResource(R.string.purchase_hint))
        }
    }
}
