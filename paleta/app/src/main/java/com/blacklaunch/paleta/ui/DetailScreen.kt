package com.blacklaunch.paleta.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.paleta.R
import com.blacklaunch.paleta.data.ProductCard
import com.blacklaunch.paleta.pack.Layout
import com.blacklaunch.paleta.pack.Num
import com.blacklaunch.paleta.plan
import com.blacklaunch.paleta.ui.theme.Oled
import com.blacklaunch.paleta.view

/**
 * Збережена картка на весь екран — повністю, як її рахували: розкладка шару, бланк, обмеження. «Дублювати» —
 * усе в новий розрахунок (змінюєш, що треба, і зберігаєш нову картку). «⋯» — поділитися, видалити.
 */
@Composable
fun DetailScreen(card: ProductCard, onBack: () -> Unit, onDuplicate: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val haptics = LocalHaptics.current
    val comma = decimalComma()
    var menu by remember { mutableStateOf(false) }
    val backdrop = backdropAmount(menu)
    BackHandler { onBack() }
    Box(Modifier.fillMaxSize().background(Oled.Black)) {
        Column(Modifier.fillMaxSize().backdrop(backdrop).statusBarsPadding()) {
            // Угорі: назад, назва, «⋯».
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { haptics.tap(); onBack() }, contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.Back, stringResource(R.string.back))
                }
                Text(
                    card.sku.ifBlank { stringResource(R.string.no_sku) },
                    color = Oled.TextPrimary,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { haptics.tap(); menu = true }, contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.More, stringResource(R.string.more))
                }
            }
            Column(
                Modifier.weight(1f).bouncyScroll().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle(stringResource(R.string.section_layout))
                val layout = remember(card.layout) { Layout.decode(card.layout) }
                var view3d by remember { mutableStateOf(false) }
                val layout2 = remember(card.layout2) { card.layout2?.let { Layout.decode(it) } }
                var showSecond by remember { mutableStateOf(false) }
                EqualWidthRow(Modifier.fillMaxWidth()) {
                    ChoiceChip(stringResource(R.string.view_top), selected = !view3d) { view3d = false }
                    ChoiceChip(stringResource(R.string.view_3d), selected = view3d) { view3d = true }
                }
                Box(Modifier.fillMaxWidth().pencilFrame(18.dp, seed = 51, alpha = 0.5f).padding(6.dp)) {
                    if (view3d) {
                        Pallet3D(layout, card.plan, card.boxH, layers = card.layers, extra = if (card.partial) card.boxes else 0, layout2 = layout2)
                    } else {
                        PalletView(if (showSecond && layout2 != null) layout2 else layout, card.plan, card.overhang, check = null)
                    }
                }
                // Другий шар розкладено інакше — можна подивитись кожен.
                if (layout2 != null && !view3d) {
                    EqualWidthRow(Modifier.fillMaxWidth()) {
                        ChoiceChip(stringResource(R.string.layer_1), selected = !showSecond) { showSecond = false }
                        ChoiceChip(stringResource(R.string.layer_2), selected = showSecond) { showSecond = true }
                    }
                }
                Text(
                    (if (card.customLayout) stringResource(R.string.status_custom, card.perLayer) else stringResource(R.string.status_auto, card.perLayer)) +
                        (if (card.overhang > 0) " · " + stringResource(R.string.overhang_option, Num.cm(card.overhang, comma)) else ""),
                    color = Oled.TextPrimary.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
                SectionTitle(stringResource(R.string.section_result))
                ResultCard(card.view())
                WindowHint(
                    stringResource(
                        R.string.detail_limits,
                        Num.metres(card.heightLimit, comma),
                        if (card.weightLimitG % 1000 == 0L) (card.weightLimitG / 1000).toString() else Num.kilos(card.weightLimitG, comma),
                        if (card.overhang == 0) stringResource(R.string.overhang_none_short) else Num.cm(card.overhang, comma) + " " + stringResource(R.string.unit_cm),
                    ),
                )
                Spacer(Modifier.height(8.dp))
            }
            // Головна дія — одна синя.
            SheetButton(
                stringResource(R.string.duplicate),
                onDuplicate,
                tone = Tone.Confirm,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (menu) {
            SheetDialog(onDismissRequest = { menu = false }, title = card.sku.ifBlank { stringResource(R.string.no_sku) }) {
                Column(Modifier.bouncyScroll()) {
                    MenuRow(Glyph.Copy, stringResource(R.string.duplicate), { menu = false; onDuplicate() })
                    MenuRow(Glyph.Share, stringResource(R.string.share), { menu = false; onShare() })
                    MenuRow(Glyph.Delete, stringResource(R.string.delete), { menu = false; onDelete() }, tone = Tone.Danger)
                }
            }
        }
    }
}
