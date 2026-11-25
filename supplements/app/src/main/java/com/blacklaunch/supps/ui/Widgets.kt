package com.blacklaunch.supps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.supps.R
import com.blacklaunch.supps.ui.theme.Oled

/*
 * Дрібні спільні елементи програми — за THEMES.md: «Назва · значення ›», вибір у вікні з однакових рядків, поля з
 * підписом і підказкою, картка, нижня панель (кнопка «Назад» — завжди внизу).
 */

/** Заголовок розділу. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Oled.TextPrimary,
        fontSize = 18.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** Рядок-налаштування «Назва · значення ›»: відкриває вікно вибору. */
@Composable
fun SettingRow(title: String, value: String, onClick: () -> Unit, enabled: Boolean = true) {
    val haptics = LocalHaptics.current
    val sheet = LocalSheet.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { haptics.tap(); onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = if (enabled) Oled.TextPrimary else Oled.TextSecondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = Oled.TextSecondary,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (sheet) "→" else "›", color = Oled.TextSecondary, fontSize = if (sheet) 18.sp else 20.sp)
    }
}

/** Рядок вибору: назва, пояснення і ✓, якщо вибрано (вибране — галочкою, не кольором). */
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
            Text(title, color = if (selected) Oled.TextPrimary else Oled.TextSecondary, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, color = Oled.TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        // Місце під ✓ є завжди — рядок не змінює ширини.
        GlyphIcon(Glyph.Save, null, tint = SelectedInk.copy(alpha = if (selected) 1f else 0f))
    }
}

/** Вікно вибору одного варіанта: однакові рядки з ✓; дотик — вибрати й закрити. */
@Composable
fun OptionsDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, hint: String? = null, onPick: (Int) -> Unit) {
    SheetDialog(onDismissRequest = onDismiss, title = title, buttons = { SheetButton(stringResource(R.string.cancel), onDismiss) }) {
        Column(Modifier.bouncyScroll()) {
            options.forEachIndexed { i, text ->
                ChoiceRow(text, null, selected = i == selected) {
                    onPick(i)
                    onDismiss()
                }
            }
            if (hint != null) WindowHint(hint)
        }
    }
}

/** Вікно з одним полем (назва, число). [onApply] отримує введене. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    onDismiss: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    hint: String? = null,
    allowEmpty: Boolean = true,
    onApply: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SheetDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = {
            SheetButton(stringResource(R.string.cancel), onDismiss)
            SheetButton(stringResource(R.string.save), { onApply(value.trim()); onDismiss() }, tone = Tone.Confirm, enabled = allowEmpty || value.isNotBlank())
        },
    ) {
        Column {
            SheetTextField(value, { value = it.take(80) }, placeholder, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), focusRequester = focus)
            if (hint != null) WindowHint(hint)
        }
    }
}

/** Поле з підписом над ним і підказкою під ним. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLength: Int = 80,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = Oled.TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        SheetTextField(value, { onValueChange(it.take(maxLength)) }, placeholder, keyboardOptions = KeyboardOptions(keyboardType = keyboardType))
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            FieldHint(hint)
        }
    }
}

/** Ціле або дробове число з поля: кома теж годиться; порожньо чи нісенітниця — null. */
fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0 }

fun parseInt(text: String): Int? = parseNumber(text)?.toInt()

/** Картка: рамка олівцем / скляна плашка. Дотик і довге натискання — без «ореолу» (відповідь — вібровідгук). */
@Composable
fun Card(modifier: Modifier = Modifier, seed: Int = 0, selected: Boolean = false, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val haptics = LocalHaptics.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .pencilFrame(18.dp, seed = seed, ink = if (selected) SelectedInk else SheetInk, alpha = if (selected) 0.95f else 0.7f)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.pointerInput(onClick, onLongClick) {
                        detectTapGestures(
                            onTap = { if (onClick != null) { haptics.tap(); onClick() } },
                            onLongPress = if (onLongClick != null) { _ -> haptics.longPress(); onLongClick() } else null,
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        content()
    }
}

/** Відмітка «прийнято»: квадратик олівцем / скляний; прийнято — яскравий кант і ✓. */
@Composable
fun CheckMark(checked: Boolean) {
    Box(
        Modifier
            .size(34.dp)
            .pencilFrame(10.dp, seed = 29, ink = if (checked) SelectedInk else SheetInk, alpha = if (checked) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.Save, null, tint = SelectedInk.copy(alpha = if (checked) 1f else 0f), size = 20.dp)
    }
}

/** Повноекранна сторінка: заголовок угорі, вміст, нижня панель з «Назад» ліворуч і діями праворуч. */
@Composable
fun FullPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Oled.Black)
            .statusBarsPadding(),
    ) {
        Text(
            title,
            color = Oled.TextPrimary,
            fontSize = 24.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        BottomBar {
            SheetButton(stringResource(R.string.back), onBack)
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}

/** Нижня панель: над жестовою смугою, кнопки в один ряд. */
@Composable
fun BottomBar(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/** Кнопка-оцінка 1–5: вибрана — яскравим кантом; текст по центру. */
@Composable
fun ScoreChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Box(
        modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .pencilFrame(14.dp, seed = text.hashCode(), ink = if (selected) SelectedInk else SheetInk, alpha = if (selected) 1f else 0.4f)
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Oled.TextPrimary else Oled.TextSecondary,
            fontSize = 16.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Ряд однакових кнопок-оцінок, що ділять ширину порівну. */
@Composable
fun ScoreRow(labels: List<String>, selected: Int?, onPick: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            ScoreChip(label, selected == i, Modifier.weight(1f)) { onPick(if (selected == i) null else i) }
        }
    }
}
