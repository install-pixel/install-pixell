package com.blacklaunch.supps.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.blacklaunch.supps.AppData
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.Form
import com.blacklaunch.supps.data.Phase
import com.blacklaunch.supps.data.Plan
import com.blacklaunch.supps.data.Schedule
import com.blacklaunch.supps.data.Slot
import com.blacklaunch.supps.data.Supplement

/** Що редактор віддає на збереження. */
data class EditResult(val supplement: Supplement, val doneDays: Int?, val newGroupName: String?, val newGroupBreak: Int)

private fun numText(v: Double?): String = v?.let { num(it) }.orEmpty()

/**
 * Редактор добавки. Усе, чого не знаєш (мг, залишок, ціна), можна лишити порожнім — тоді просто не буде
 * відповідного розрахунку.
 */
@Composable
fun EditorScreen(original: Supplement?, data: AppData, today: Long, currency: String, onBack: () -> Unit, onSave: (EditResult) -> Unit) {
    val base = original ?: Supplement(name = "")
    var name by rememberSaveable { mutableStateOf(base.name) }
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
    var picker by rememberSaveable { mutableStateOf<String?>(null) }

    val unit = unitShort(form)
    val groupValid = plan != Plan.Group || (parseInt(onDays) ?: 0) >= 1
    val cycleValid = plan != Plan.Cycle || (parseInt(onDays) ?: 0) >= 1
    val doseValid = (parseNumber(perDose) ?: 0.0) > 0
    val valid = name.isNotBlank() && groupValid && cycleValid && doseValid

    // Що зараз за схемою — як підказка до «вже приймаю днів».
    val currentStatus = original?.let { Schedule.status(it, today, data.groups, data.supplements) }
    val nowHint = if (original != null && currentStatus != null && currentStatus.phase == Phase.Active && currentStatus.length > 0 && original.planE == plan) {
        stringResource(R.string.edit_done_now, currentStatus.dayNo)
    } else {
        null
    }

    FullPage(
        title = stringResource(if (original == null) R.string.add_supplement else R.string.edit_supplement),
        onBack = onBack,
        actions = {
            SheetButton(
                stringResource(R.string.save),
                {
                    val isNewGroup = plan == Plan.Group && groupId == -1L
                    val s = base.copy(
                        name = name.trim().take(80),
                        form = form.name,
                        mgPerUnit = parseNumber(mg)?.takeIf { it > 0 },
                        unitsPerDose = (parseNumber(perDose) ?: 1.0).coerceIn(0.01, 1000.0),
                        slot = slot.name,
                        plan = plan.name,
                        onDays = if (plan == Plan.Cycle || plan == Plan.Group) (parseInt(onDays) ?: 1).coerceIn(1, 3650) else 0,
                        offDays = if (plan == Plan.Cycle) (parseInt(offDays) ?: 0).coerceIn(0, 3650) else 0,
                        groupId = if (plan == Plan.Group && !isNewGroup) groupId else null,
                        unitsLeft = parseNumber(stock),
                        packUnits = parseInt(pack)?.takeIf { it > 0 },
                        packPrice = parseNumber(price),
                        note = note.trim().take(300),
                    )
                    val doneDays = if (plan == Plan.Cycle || plan == Plan.Group) parseInt(done)?.coerceIn(0, 3650) else null
                    onSave(EditResult(s, doneDays, if (isNewGroup) newGroupName else null, parseInt(newGroupBreak) ?: 0))
                },
                tone = Tone.Confirm,
                enabled = valid,
            )
        },
    ) {
        Column(Modifier.fillMaxSize().imePadding().bouncyScroll().padding(horizontal = 16.dp)) {
            LabeledField(stringResource(R.string.edit_name), name, { name = it }, placeholder = stringResource(R.string.edit_name_hint))
            SettingRow(stringResource(R.string.edit_form), formName(form), { picker = "form" })
            LabeledField(
                stringResource(R.string.edit_mg, unit),
                mg,
                { mg = it },
                placeholder = stringResource(R.string.unknown),
                hint = stringResource(R.string.edit_mg_hint),
                keyboardType = KeyboardType.Decimal,
            )
            LabeledField(stringResource(R.string.edit_per_dose, unit), perDose, { perDose = it }, placeholder = "1", keyboardType = KeyboardType.Decimal)
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
                        placeholder = stringResource(R.string.edit_done_placeholder),
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
                    LabeledField(
                        stringResource(R.string.edit_block_days),
                        onDays,
                        { onDays = it.filter { c -> c.isDigit() }.take(4) },
                        placeholder = "56",
                        hint = stringResource(R.string.edit_block_hint),
                        keyboardType = KeyboardType.Number,
                    )
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

            SectionTitle(stringResource(R.string.edit_note_section))
            LabeledField(stringResource(R.string.edit_note), note, { note = it }, placeholder = stringResource(R.string.edit_note_hint), maxLength = 300)
            Spacer(Modifier.height(24.dp))
        }
    }

    when (picker) {
        "form" -> OptionsDialog(stringResource(R.string.edit_form), Form.entries.map { formName(it) }, Form.entries.indexOf(form), { picker = null }) { form = Form.entries[it] }
        "slot" -> OptionsDialog(stringResource(R.string.edit_slot), Slot.entries.map { slotName(it) }, Slot.entries.indexOf(slot), { picker = null }, hint = stringResource(R.string.edit_slot_hint)) { slot = Slot.entries[it] }
        "plan" -> OptionsDialog(
            stringResource(R.string.edit_plan),
            Plan.entries.map { planName(it) },
            Plan.entries.indexOf(plan),
            { picker = null },
            hint = stringResource(R.string.edit_plan_hint),
        ) { plan = Plan.entries[it] }
        "group" -> {
            val options = data.groups.map { it.name } + stringResource(R.string.group_new)
            val selected = data.groups.indexOfFirst { it.id == groupId }.let { if (it < 0) options.size - 1 else it }
            OptionsDialog(stringResource(R.string.edit_group), options, selected, { picker = null }) { i ->
                groupId = if (i < data.groups.size) data.groups[i].id else -1L
            }
        }
    }
}
