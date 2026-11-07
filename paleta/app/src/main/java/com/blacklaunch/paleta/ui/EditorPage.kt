package com.blacklaunch.paleta.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.paleta.PaletaViewModel
import com.blacklaunch.paleta.R
import com.blacklaunch.paleta.data.ProductCard
import com.blacklaunch.paleta.pack.Num
import com.blacklaunch.paleta.ui.theme.Oled

/** Яке вікно обмежень відкрите. */
enum class LimitDialog { Height, Weight, Overhang }

/**
 * Сторінка «Новий»: SKU (зі сканером), коробка, штука (необов'язково), обмеження, розкладка шару зверху, бланк і
 * «Зберегти». Усе рахується одразу, поки вписуєш, — окремої кнопки «Розрахувати» немає.
 */
@Composable
fun EditorPage(
    vm: PaletaViewModel,
    known: ProductCard?,
    onScan: () -> Unit,
    onOpenKnown: (ProductCard) -> Unit,
    onLimit: (LimitDialog) -> Unit,
    onLayoutMenu: () -> Unit,
) {
    val comma = decimalComma()
    val scroll = rememberScrollState()
    val haptics = LocalHaptics.current
    var unitOpen by rememberSaveable { mutableStateOf(false) }
    val anyInput = listOf(vm.sku, vm.boxL, vm.boxW, vm.boxH, vm.boxKg, vm.units, vm.unitL, vm.unitW, vm.unitH, vm.unitKg).any { it.isNotBlank() }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    val number = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)

    Column(
        Modifier
            .fillMaxSize()
            .bouncyScroll(scroll)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // --- SKU ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.section_product), Modifier.weight(1f))
            // «Очистити» — завжди на своєму місці; коли нічого не вписано — тьмяне.
            Text(
                stringResource(R.string.clear_all),
                color = Oled.TextSecondary.copy(alpha = if (anyInput) 1f else 0.35f),
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = anyInput) { haptics.tap(); unitOpen = false; vm.clearAll() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        SheetTextField(
            value = vm.sku,
            onValueChange = { vm.sku = it.take(PaletaViewModel.MAX_SKU) },
            placeholder = stringResource(R.string.sku_hint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
            trailing = {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { haptics.tap(); onScan() },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.Barcode, stringResource(R.string.scan), tint = Oled.TextSecondary) }
            },
        )
        // Цей SKU уже рахували — можна відкрити й здублювати.
        Box {
            AnimatedVisibility(known != null, enter = fadeIn(tween(OPEN_MS)) + expandVertically(tween(OPEN_MS)), exit = fadeOut(tween(CLOSE_MS)) + shrinkVertically(tween(CLOSE_MS))) {
                val k = known
                if (k != null) {
                    Text(
                        stringResource(R.string.sku_known, listDate(k.createdAt)),
                        color = Oled.TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { haptics.tap(); onOpenKnown(k) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // --- Коробка ---
        SectionTitle(stringResource(R.string.section_box))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledField(stringResource(R.string.f_length), vm.boxL, { vm.boxL = it.take(8) }, "0,47", stringResource(R.string.unit_m), decimal, Modifier.weight(1f))
            LabeledField(stringResource(R.string.f_width), vm.boxW, { vm.boxW = it.take(8) }, "0,40", stringResource(R.string.unit_m), decimal, Modifier.weight(1f))
            LabeledField(stringResource(R.string.f_height), vm.boxH, { vm.boxH = it.take(8) }, "0,40", stringResource(R.string.unit_m), decimal, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledField(stringResource(R.string.f_box_weight), vm.boxKg, { vm.boxKg = it.take(9) }, "2,30", stringResource(R.string.unit_kg), decimal, Modifier.weight(1f))
            LabeledField(stringResource(R.string.f_units), vm.units, { vm.units = it.filter(Char::isDigit).take(6) }, "1", stringResource(R.string.unit_pcs), number, Modifier.weight(1f))
        }
        // Сантиметри зрозуміли як сантиметри — показуємо, що вийшло.
        val cmHint = listOf(vm.boxL, vm.boxW, vm.boxH).filter { Num.isCentimetres(it) }.mapNotNull { Num.lengthMm(it) }
        FieldHint(
            if (cmHint.isNotEmpty()) stringResource(R.string.hint_cm_read, cmHint.joinToString(" · ") { Num.metres(it, comma) })
            else stringResource(R.string.hint_metres),
        )

        // --- Одна штука (необов'язково) ---
        val unitFilled = listOf(vm.unitL, vm.unitW, vm.unitH, vm.unitKg).any { it.isNotBlank() }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { haptics.tap(); unitOpen = !unitOpen }
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.section_unit), color = Oled.TextPrimary, fontSize = 17.sp)
                Text(
                    if (unitFilled) unitSummary(vm, comma) else stringResource(R.string.unit_optional),
                    color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(if (unitOpen) "↑" else "↓", color = Oled.TextSecondary, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 8.dp))
        }
        AnimatedVisibility(unitOpen, enter = fadeIn(tween(OPEN_MS)) + expandVertically(tween(OPEN_MS)), exit = fadeOut(tween(CLOSE_MS)) + shrinkVertically(tween(CLOSE_MS))) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledField(stringResource(R.string.f_length), vm.unitL, { vm.unitL = it.take(8) }, "—", stringResource(R.string.unit_m), decimal, Modifier.weight(1f))
                    LabeledField(stringResource(R.string.f_width), vm.unitW, { vm.unitW = it.take(8) }, "—", stringResource(R.string.unit_m), decimal, Modifier.weight(1f))
                    LabeledField(stringResource(R.string.f_height), vm.unitH, { vm.unitH = it.take(8) }, "—", stringResource(R.string.unit_m), decimal, Modifier.weight(1f))
                }
                LabeledField(stringResource(R.string.f_unit_weight), vm.unitKg, { vm.unitKg = it.take(9) }, "—", stringResource(R.string.unit_kg), decimal.copy(imeAction = ImeAction.Done), Modifier.fillMaxWidth(0.5f))
                FieldHint(stringResource(R.string.unit_hint))
            }
        }

        // --- Обмеження: три плашки, дотик — змінити ---
        SectionTitle(stringResource(R.string.section_limits))
        EqualWidthRow(Modifier.fillMaxWidth()) {
            LimitTile(
                stringResource(R.string.limit_height),
                "${Num.metres(vm.effectiveHeight, comma)} ${stringResource(R.string.unit_m)}",
                if (vm.adr) stringResource(R.string.limit_adr_short) else stringResource(R.string.limit_with_pallet),
            ) { onLimit(LimitDialog.Height) }
            LimitTile(
                stringResource(R.string.limit_weight),
                "${(vm.weightLimitG / 1000.0).let { if (it % 1.0 == 0.0) it.toLong().toString() else Num.kilos(vm.weightLimitG, comma) }} ${stringResource(R.string.unit_kg)}",
                if (vm.palletOn) stringResource(R.string.limit_pallet_added, Num.kilos(vm.palletG, comma)) else stringResource(R.string.limit_pallet_not),
                bright = vm.palletOn,
            ) { onLimit(LimitDialog.Weight) }
            LimitTile(
                stringResource(R.string.limit_overhang),
                if (vm.overhang == 0) stringResource(R.string.overhang_none_short) else "${Num.cm(vm.overhang, comma)} ${stringResource(R.string.unit_cm)}",
                if (vm.overhang == 0) stringResource(R.string.overhang_flush) else stringResource(R.string.overhang_per_side),
                bright = vm.overhang > 0,
            ) { onLimit(LimitDialog.Overhang) }
        }
        SwitchRow(
            stringResource(R.string.adr_title),
            vm.adr,
            Modifier.padding(horizontal = 6.dp),
            subtitle = stringResource(R.string.adr_desc),
        ) { vm.adr = it }

        // --- Розкладка шару ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.section_layout), Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(enabled = vm.box != null) { haptics.tap(); onLayoutMenu() },
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.More, stringResource(R.string.more), tint = Oled.TextSecondary.copy(alpha = if (vm.box != null) 1f else 0.35f)) }
        }
        val check = vm.check
        Box(Modifier.fillMaxWidth().pencilFrame(18.dp, seed = 51, alpha = 0.5f).padding(6.dp)) {
            PalletView(
                layout = vm.layout,
                box = vm.box,
                overhang = vm.overhang,
                check = check,
                selected = vm.selected,
                editable = true,
                onSelect = { vm.selected = it },
                onMove = { i, x, y -> vm.move(i, x, y) },
                onRotate = { vm.selected = it; vm.rotateSelected() },
            )
            if (vm.box == null) {
                Text(
                    stringResource(R.string.layout_empty),
                    color = Oled.TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
        }
        LayoutStatus(vm, comma)
        // Дії з розкладкою — однакові кнопки в ряд; «Повернути» і «Прибрати» тьмяні, поки нічого не вибрано.
        val hasBox = vm.box != null
        val hasSel = vm.selected != null && vm.selected!! in vm.layout.indices
        EqualWidthRow(Modifier.fillMaxWidth(), gap = 6.dp) {
            SheetButton(stringResource(R.string.act_auto), { vm.auto() }, enabled = hasBox, compact = true)
            SheetButton(stringResource(R.string.act_add), { vm.addBox() }, enabled = hasBox, compact = true)
            SheetButton(stringResource(R.string.act_rotate), { vm.rotateSelected() }, enabled = hasSel, compact = true)
            SheetButton(stringResource(R.string.act_remove), { vm.removeSelected() }, enabled = hasSel, compact = true)
        }
        FieldHint(stringResource(R.string.layout_hint))

        // --- Бланк ---
        Spacer(Modifier.height(6.dp))
        SectionTitle(stringResource(R.string.section_result))
        val view = vm.currentView()
        if (view != null) {
            ResultCard(view)
        } else {
            Box(
                Modifier.fillMaxWidth().pencilFrame(18.dp, seed = 61, alpha = 0.35f).padding(20.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.result_empty), color = Oled.TextSecondary, fontSize = 15.sp) }
        }
        Spacer(Modifier.height(4.dp))
        SheetButton(
            stringResource(R.string.save),
            { vm.save() },
            tone = Tone.Confirm,
            enabled = vm.canSave,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!vm.canSave && view != null && check?.ok == false) FieldHint(stringResource(R.string.save_blocked))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Oled.TextPrimary, fontSize = 18.sp, modifier = modifier.padding(top = 4.dp))
}

/** Поле з підписом над ним (як клітинка бланку). */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    suffix: String,
    keyboard: KeyboardOptions,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
        SheetTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }) },
            placeholder = placeholder,
            keyboardOptions = keyboard,
            suffix = suffix,
        )
    }
}

/** Плашка обмеження: назва, значення, пояснення; дотик — змінити. */
@Composable
private fun LimitTile(title: String, value: String, note: String, bright: Boolean = false, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .pencilFrame(16.dp, seed = title.length + 40, alpha = if (bright) 0.9f else 0.6f, ink = if (bright) SelectedInk else SheetInk)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = if (AppStyle.glass) androidx.compose.material3.ripple() else null) { haptics.tap(); onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(title, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Oled.TextPrimary, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(note, color = if (bright) Oled.TextPrimary.copy(alpha = 0.85f) else Oled.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Рядок під палетою: авто чи своя, скільки в шарі і що не так. Сталої висоти — нічого не смикається. */
@Composable
private fun LayoutStatus(vm: PaletaViewModel, comma: Boolean) {
    val check = vm.check
    val n = vm.layout.size
    val (text, danger) = when {
        vm.box == null -> stringResource(R.string.status_no_box) to false
        n == 0 && !vm.custom && vm.variants.isNotEmpty() && vm.variants.first().isEmpty() -> stringResource(R.string.status_too_big) to true
        n == 0 -> stringResource(R.string.status_empty) to false
        check != null && check.overlapping.isNotEmpty() -> stringResource(R.string.status_overlap, check.overlapping.size) to true
        check != null && check.outside.isNotEmpty() -> stringResource(R.string.status_outside, Num.cm(check.outside.values.max(), comma)) to true
        else -> {
            val base = if (vm.custom) stringResource(R.string.status_custom, n)
            else if (vm.variants.size > 1) stringResource(R.string.status_auto_variant, n, vm.variantIndex + 1, vm.variants.size)
            else stringResource(R.string.status_auto, n)
            val over = check?.overhangUsed ?: 0
            (if (over > 0) base + " · " + stringResource(R.string.status_overhang, Num.cm(over, comma)) else base) to false
        }
    }
    Text(
        text,
        color = if (danger) Oled.Danger else Oled.TextPrimary.copy(alpha = 0.9f),
        fontSize = 15.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 4.dp),
    )
}

@Composable
private fun unitSummary(vm: PaletaViewModel, comma: Boolean): String {
    val t = vm.unitTriple
    val g = Num.grams(vm.unitKg)
    val parts = buildList {
        if (t != null) add("${Num.metres(t.first, comma)} × ${Num.metres(t.second, comma)} × ${Num.metres(t.third, comma)} ${stringResource(R.string.unit_m)}")
        if (g != null) add("${Num.kilos(g, comma)} ${stringResource(R.string.unit_kg)}")
    }
    return if (parts.isEmpty()) stringResource(R.string.unit_incomplete) else parts.joinToString(" · ")
}
