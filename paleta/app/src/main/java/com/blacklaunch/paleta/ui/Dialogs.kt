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

/** Стандарти обмежень: висота 1,80 м разом з палетою, вага 700 кг, без виступу за край. */
const val STD_HEIGHT_MM = 1800
const val STD_WEIGHT_G = 700_000L
const val STD_OVERHANG_MM = 0

/** Найбільший виступ, який можна вписати (з кожного боку), мм. */
const val MAX_OVERHANG_MM = 50

/**
 * Однакове вікно для всіх трьох обмежень: поле з поточним значенням (міняєш — і все), під ним — який стандарт;
 * кнопки «Стандарт» (повертає стандартне значення в поле) і «Готово».
 */
@Composable
private fun LimitDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    onStandard: () -> Unit,
    onDone: () -> Unit,
    doneEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    SheetDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = {
            SheetButton(stringResource(R.string.standard), onStandard)
            SheetButton(stringResource(R.string.done), onDone, tone = Tone.Confirm, enabled = doneEnabled)
        },
    ) {
        Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun LimitField(value: String, onChange: (String) -> Unit, placeholder: String, suffix: String, error: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier, last: Boolean = true) {
    SheetTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }.take(7)) },
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        suffix = suffix,
        error = error,
        modifier = modifier,
    )
}

/** Висота палети разом з самою палетою: стандарт 1,80 м, будь-яку іншу — вписати. */
@Composable
fun HeightDialog(current: Int, adr: Boolean, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    val comma = decimalComma()
    var text by remember { mutableStateOf(Num.metres(current, comma)) }
    val mm = Num.lengthMm(text)?.takeIf { it in (PALLET_HEIGHT_MM + 50)..3000 }
    LimitDialogFrame(
        stringResource(R.string.limit_height_title), onDismiss,
        onStandard = { text = Num.metres(STD_HEIGHT_MM, comma) },
        onDone = { mm?.let(onApply) }, doneEnabled = mm != null,
    ) {
        LimitField(text, { text = it }, Num.metres(STD_HEIGHT_MM, comma), stringResource(R.string.unit_m), text.isNotEmpty() && mm == null, { mm?.let(onApply) })
        WindowHint(stringResource(R.string.limit_height_hint, Num.metres(STD_HEIGHT_MM, comma), Num.cm(PALLET_HEIGHT_MM, comma)))
        if (adr) WindowHint(stringResource(R.string.limit_height_adr, Num.metres(ADR_HEIGHT_MM, comma)))
    }
}

/** Вага разом з товаром: стандарт 700 кг; перемикач — додавати вагу самої палети (запам'ятовується). */
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
    val apply = { if (ok) onApply(limitValue!!, on, palletValue ?: palletG) }
    LimitDialogFrame(
        stringResource(R.string.limit_weight_title), onDismiss,
        onStandard = { limit = kgText(STD_WEIGHT_G) },
        onDone = apply, doneEnabled = ok,
    ) {
        LimitField(limit, { limit = it }, kgText(STD_WEIGHT_G), stringResource(R.string.unit_kg), limit.isNotEmpty() && limitValue == null, apply, last = false)
        WindowHint(stringResource(R.string.limit_weight_hint, kgText(STD_WEIGHT_G)))
        SwitchRow(stringResource(R.string.pallet_add_title), on, Modifier.padding(horizontal = 6.dp), subtitle = stringResource(R.string.pallet_add_desc)) { on = it }
        // Вимкнене поле тьмяніє, а не зникає (нічого не смикається).
        LimitField(pallet, { pallet = it }, "25", stringResource(R.string.unit_kg), on && pallet.isNotEmpty() && palletValue == null, apply, Modifier.alpha(if (on) 1f else 0.35f))
    }
}

/**
 * Виступ за край палети, з кожного боку: стандарт — без виступу (так приймає більшість складів і перевізників);
 * інший — вписати, до 5 см.
 */
@Composable
fun OverhangDialog(current: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    val comma = decimalComma()
    var text by remember { mutableStateOf(Num.cm(current, comma)) }
    val mm = Num.parse(text)?.let { kotlin.math.round(it * 10).toInt() }?.takeIf { it in 0..MAX_OVERHANG_MM }
    LimitDialogFrame(
        stringResource(R.string.overhang_title), onDismiss,
        onStandard = { text = Num.cm(STD_OVERHANG_MM, comma) },
        onDone = { mm?.let(onApply) }, doneEnabled = mm != null,
    ) {
        LimitField(text, { text = it }, "0", stringResource(R.string.unit_cm_side), text.isNotEmpty() && mm == null, { mm?.let(onApply) })
        WindowHint(stringResource(R.string.overhang_hint, Num.cm(MAX_OVERHANG_MM, comma)))
        // Попередження — коли виступ дозволено.
        if ((mm ?: 0) > 0) WindowHint(stringResource(R.string.overhang_warning))
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
fun LayoutMenu(onDismiss: () -> Unit, onBest: () -> Unit, onOrient: () -> Unit, onFill: () -> Unit, onClear: () -> Unit) {
    SheetDialog(onDismissRequest = onDismiss, title = stringResource(R.string.section_layout)) {
        Column(Modifier.bouncyScroll()) {
            MenuRow(Glyph.Magic, stringResource(R.string.layout_best), onBest)
            MenuRow(Glyph.Box, stringResource(R.string.layout_orient), onOrient)
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
                    // Бланк: хто описує структуру — ім'я впишеться в рядок підпису.
                    Text(stringResource(R.string.settings_form), color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    SheetTextField(
                        value = FormSigner.name,
                        onValueChange = { v ->
                            val t = v.take(60)
                            FormSigner.name = t
                            settings.signer = t.trim()
                        },
                        placeholder = stringResource(R.string.signer_hint),
                        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    )
                    FieldHint(stringResource(R.string.signer_desc), Modifier.padding(top = 4.dp))
                    Text(stringResource(R.string.settings_screen), color = Oled.TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwitchRow(stringResource(R.string.status_hide_title), StatusBar.hidden, subtitle = stringResource(R.string.status_hide_desc)) {
                            StatusBar.hidden = it
                            settings.hideStatusBar = it
                        }
                    }
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
