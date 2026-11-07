package com.blacklaunch.paleta.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.paleta.R
import com.blacklaunch.paleta.data.ProductCard
import com.blacklaunch.paleta.pack.ADR_HEIGHT_MM
import com.blacklaunch.paleta.pack.Num
import com.blacklaunch.paleta.pack.PALLET_HEIGHT_MM
import com.blacklaunch.paleta.settings.AppLanguage
import com.blacklaunch.paleta.settings.AppSettings
import com.blacklaunch.paleta.ui.theme.Oled

/** Рядок вибору: назва, пояснення і галочка, якщо вибрано (вибране — галочкою, не кольором). */
@Composable
fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) Oled.TextPrimary else Oled.TextSecondary, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = Oled.TextSecondary, fontSize = 14.sp)
        }
        GlyphIcon(Glyph.Save, null, tint = SelectedInk.copy(alpha = if (selected) 1f else 0f))
    }
}

/** Типові висоти палети (разом з палетою), мм. */
private val HEIGHTS = listOf(1200, 1300, 1800)

/**
 * Висота: три типові (1,20 / 1,30 / 1,80 — дотик одразу застосовує) або своя. Висота — разом з палетою (15 см).
 */
@Composable
fun HeightDialog(current: Int, adr: Boolean, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    val comma = decimalComma()
    var own by remember { mutableStateOf(if (current in HEIGHTS) "" else Num.metres(current, comma)) }
    val ownMm = Num.lengthMm(own)?.takeIf { it in (PALLET_HEIGHT_MM + 50)..3000 }
    SheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.limit_height_title),
        buttons = {
            SheetButton(stringResource(R.string.cancel), onDismiss)
            SheetButton(stringResource(R.string.done), { ownMm?.let(onApply) }, tone = Tone.Confirm, enabled = ownMm != null)
        },
    ) {
        Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EqualWidthRow(Modifier.fillMaxWidth()) {
                HEIGHTS.forEach { h ->
                    ChoiceChip("${Num.metres(h, comma)} ${stringResource(R.string.unit_m)}", selected = current == h) { onApply(h) }
                }
            }
            SheetTextField(
                value = own,
                onValueChange = { v -> own = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                placeholder = stringResource(R.string.limit_height_own),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { ownMm?.let(onApply) }),
                suffix = stringResource(R.string.unit_m),
            )
            WindowHint(stringResource(R.string.limit_height_hint, Num.cm(PALLET_HEIGHT_MM, comma)))
            if (adr) WindowHint(stringResource(R.string.limit_height_adr, Num.metres(ADR_HEIGHT_MM, comma)))
        }
    }
}

/**
 * Вага: ліміт (типово 700 кг) і чи додавати вагу палети (типово — ні; вибір запам'ятовується).
 */
@Composable
fun WeightDialog(limitG: Long, palletOn: Boolean, palletG: Long, onDismiss: () -> Unit, onApply: (limitG: Long, palletOn: Boolean, palletG: Long) -> Unit) {
    val comma = decimalComma()
    fun kgText(g: Long) = if (g % 1000 == 0L) (g / 1000).toString() else Num.kilos(g, comma)
    var limit by remember { mutableStateOf(kgText(limitG)) }
    var on by remember { mutableStateOf(palletOn) }
    var pallet by remember { mutableStateOf(kgText(palletG)) }
    val limitValue = Num.grams(limit)?.takeIf { it in 1_000..5_000_000 }
    val palletValue = Num.grams(pallet)?.takeIf { it in 1..200_000 }
    val ok = limitValue != null && (!on || palletValue != null)
    SheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.limit_weight_title),
        buttons = {
            SheetButton(stringResource(R.string.cancel), onDismiss)
            SheetButton(stringResource(R.string.done), { if (ok) onApply(limitValue!!, on, palletValue ?: palletG) }, tone = Tone.Confirm, enabled = ok)
        },
    ) {
        Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.limit_weight_max), color = Oled.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            SheetTextField(
                value = limit,
                onValueChange = { v -> limit = v.filter { it.isDigit() || it == ',' || it == '.' }.take(7) },
                placeholder = "700",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                suffix = stringResource(R.string.unit_kg),
                error = limit.isNotEmpty() && limitValue == null,
            )
            SwitchRow(stringResource(R.string.pallet_add_title), on, Modifier.padding(horizontal = 6.dp), subtitle = stringResource(R.string.pallet_add_desc)) { on = it }
            // Вимкнене поле тьмяніє, а не зникає (нічого не смикається).
            SheetTextField(
                value = pallet,
                onValueChange = { v -> pallet = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                placeholder = "25",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                suffix = stringResource(R.string.unit_kg),
                modifier = Modifier.alpha(if (on) 1f else 0.35f),
            )
            WindowHint(stringResource(R.string.limit_weight_hint))
        }
    }
}

/** Звис з кожного боку, мм: без звису, 1, 1,5, 2, 2,5 см (разом — до 5 см на сторону палети). */
private val OVERHANGS = listOf(0, 10, 15, 20, 25)

/** Звис: без звису (типово) або до 2,5 см з кожного боку — з попередженням. */
@Composable
fun OverhangDialog(current: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    val comma = decimalComma()
    SheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.overhang_title),
        buttons = { SheetButton(stringResource(R.string.close), onDismiss) },
    ) {
        Column(Modifier.bouncyScroll()) {
            OVERHANGS.forEach { mm ->
                ChoiceRow(
                    if (mm == 0) stringResource(R.string.overhang_none) else stringResource(R.string.overhang_option, Num.cm(mm, comma)),
                    if (mm == 0) null else stringResource(R.string.overhang_option_total, Num.cm(mm * 2, comma)),
                    selected = current == mm,
                ) { onApply(mm) }
            }
            // Попередження — видно, поки звис дозволено.
            WindowHint(stringResource(if (current > 0) R.string.overhang_warning else R.string.overhang_hint))
        }
    }
}

/** Меню картки в історії (довге натискання). */
@Composable
fun CardMenu(card: ProductCard, onDismiss: () -> Unit, onOpen: () -> Unit, onDuplicate: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    SheetDialog(onDismissRequest = onDismiss, title = card.sku.ifBlank { stringResource(R.string.no_sku) }) {
        Column(Modifier.bouncyScroll()) {
            MenuRow(Glyph.Pallet, stringResource(R.string.open), onOpen)
            MenuRow(Glyph.Copy, stringResource(R.string.duplicate), onDuplicate)
            MenuRow(Glyph.Share, stringResource(R.string.share), onShare)
            MenuRow(Glyph.Delete, stringResource(R.string.delete), onDelete, tone = Tone.Danger)
        }
    }
}

/** Меню розкладки («⋯» біля палети). */
@Composable
fun LayoutMenu(onDismiss: () -> Unit, onBest: () -> Unit, onFill: () -> Unit, onClear: () -> Unit) {
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.section_layout)) {
        Column(Modifier.bouncyScroll()) {
            MenuRow(Glyph.Magic, stringResource(R.string.layout_best), onBest)
            MenuRow(Glyph.Plus, stringResource(R.string.layout_fill), onFill)
            MenuRow(Glyph.Eraser, stringResource(R.string.layout_clear), onClear)
            WindowHint(stringResource(R.string.layout_menu_hint))
        }
    }
}

/** Сторінки вікна Налаштувань. */
private sealed interface SettingsPage {
    data object Main : SettingsPage
    data object Help : SettingsPage
    data class Topic(val topic: HelpTopic) : SettingsPage
}

private val SettingsPage.depth: Int get() = when (this) {
    SettingsPage.Main -> 0
    SettingsPage.Help -> 1
    is SettingsPage.Topic -> 2
}

private fun SettingsPage.up(): SettingsPage = when (this) {
    is SettingsPage.Topic -> SettingsPage.Help
    else -> SettingsPage.Main
}

private const val PAGE_IN_MS = 300
private const val PAGE_OUT_MS = 180

/** Теми довідки: назва й докладний текст. */
private enum class HelpTopic(val title: Int, val body: Int, val glyph: Glyph) {
    Input(R.string.help_input_t, R.string.help_input, Glyph.Box),
    Limits(R.string.help_limits_t, R.string.help_limits, Glyph.Pallet),
    Layout(R.string.help_layout_t, R.string.help_layout, Glyph.Grid),
    Result(R.string.help_result_t, R.string.help_result, Glyph.Template),
    History(R.string.help_history_t, R.string.help_history, Glyph.Clock),
}

/**
 * Налаштування: довідка, стиль («Скло» — Black Glass, «Аркуш» — від руки), мова (як у телефоні або одна з трьох),
 * екран (рядок стану). Стиль міняється одразу; мова — після короткого перезапуску екрана.
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit, onLanguageChanged: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var language by remember { mutableStateOf(settings.language) }
    var page by remember { mutableStateOf<SettingsPage>(SettingsPage.Main) }
    val current = page
    val back: @Composable () -> Unit = when (current) {
        SettingsPage.Main -> { { SheetButton(stringResource(R.string.close), onDismiss) } }
        else -> { { SheetButton(stringResource(R.string.back), { page = current.up() }) } }
    }
    SheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(
            when (current) {
                SettingsPage.Main -> R.string.settings
                SettingsPage.Help -> R.string.help
                is SettingsPage.Topic -> current.topic.title
            },
        ),
        buttons = back,
        tall = true,
    ) {
        androidx.activity.compose.BackHandler(enabled = current != SettingsPage.Main) { page = current.up() }
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val forward = targetState.depth > initialState.depth
                ((fadeIn(tween(PAGE_IN_MS, delayMillis = 60)) + slideInHorizontally(tween(PAGE_IN_MS, easing = FastOutSlowInEasing)) { (if (forward) it else -it) / 8 }) togetherWith
                    (fadeOut(tween(PAGE_OUT_MS)) + slideOutHorizontally(tween(PAGE_OUT_MS, easing = FastOutSlowInEasing)) { (if (forward) -it else it) / 8 }))
                    .using(null)
            },
            label = "settingsPage",
        ) { shown ->
            when (shown) {
                SettingsPage.Main -> Column(Modifier.fillMaxSize().bouncyScroll()) {
                    MenuRow(Glyph.More, stringResource(R.string.help), { page = SettingsPage.Help })
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_style), color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
                    ChoiceRow(stringResource(R.string.style_glass), stringResource(R.string.style_glass_desc), selected = AppStyle.glass) {
                        AppStyle.glass = true
                        settings.glass = true
                    }
                    ChoiceRow(stringResource(R.string.style_sheet), stringResource(R.string.style_sheet_desc), selected = !AppStyle.glass) {
                        AppStyle.glass = false
                        settings.glass = false
                    }
                    Text(stringResource(R.string.settings_language), color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    AppLanguage.entries.forEach { lang ->
                        // Назви мов — кожна своєю мовою, щоб її легко знайти з будь-якої.
                        val title = when (lang) {
                            AppLanguage.System -> stringResource(R.string.lang_system)
                            AppLanguage.Ukrainian -> "Українська"
                            AppLanguage.Polish -> "Polski"
                            AppLanguage.English -> "English"
                        }
                        ChoiceRow(title, null, selected = language == lang) {
                            if (language != lang) {
                                language = lang
                                settings.language = lang
                                onLanguageChanged()
                            }
                        }
                    }
                    Text(stringResource(R.string.settings_screen), color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwitchRow(stringResource(R.string.status_hide_title), StatusBar.hidden, subtitle = stringResource(R.string.status_hide_desc)) {
                            StatusBar.hidden = it
                            settings.hideStatusBar = it
                        }
                    }
                    WindowHint(stringResource(R.string.settings_hint))
                }
                SettingsPage.Help -> Column(Modifier.fillMaxSize().bouncyScroll()) {
                    HelpTopic.entries.forEach { t -> MenuRow(t.glyph, stringResource(t.title), { page = SettingsPage.Topic(t) }) }
                }
                is SettingsPage.Topic -> Text(
                    stringResource(shown.topic.body),
                    color = Oled.TextPrimary.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.fillMaxSize().bouncyScroll(),
                )
            }
        }
    }
}
