package com.blacklaunch.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.ui.theme.Oled

/** Спільні елементи екранів налаштувань: картки, перемикачі, варіанти вибору, дії. */

internal val CardShape = RoundedCornerShape(20.dp)

@Composable
internal fun SmallButton(text: String, onClick: () -> Unit) = SmallButton(text, Tone.Neutral, onClick)

@Composable
internal fun SmallButton(text: String, tone: Tone, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(12.dp)
    val sheet = LocalSheet.current
    Text(
        text = text,
        color = if (tone == Tone.Neutral) Oled.Accent else tone.color(),
        fontSize = 15.sp,
        modifier = Modifier
            .then(if (sheet) Modifier.pencilFrame(12.dp, text.length, ink = if (tone == Tone.Neutral) SheetInk else tone.color()) else Modifier.clip(shape).background(Oled.Knob).border(1.dp, Oled.Glint, shape))
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    // «Аркуш»: без скляної поверхні — лише рамка олівцем на чорному.
    val sheet = LocalSheet.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (sheet) Modifier.pencilFrame(18.dp, seed = currentCompositeKeyHash, alpha = 0.6f) else Modifier.clip(CardShape).background(Oled.Surface).glassSheen().border(1.dp, Oled.Glint, CardShape))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/**
 * Скляний перемикач: капсула з чорного скла з кантом, усередині — скляна кулька зі світлом посередині. Увімкнено —
 * капсула підсвічена, кулька праворуч і яскрава; вимкнено — темна, кулька ліворуч і тьмяна. Кулька їде плавно.
 */
@Composable
internal fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    val position by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "switch")
    Box(
        Modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(shape)
            .background(if (checked) SwitchTrackOn else Oled.Surface)
            .border(1.dp, if (checked) Oled.GlintBright else Oled.Glint, shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(3.dp),
    ) {
        val knob = 24.dp
        Box(
            Modifier
                .offset(x = (52.dp - 6.dp - knob) * position)
                .size(knob)
                .clip(CircleShape)
                .background(if (checked) SwitchKnobOn else Oled.Knob)
                .border(1.dp, Oled.Glint, CircleShape),
        )
    }
}

/** Увімкнена капсула — скло, підсвічене зсередини. */
private val SwitchTrackOn = Brush.verticalGradient(0f to Color(0xFF4A4A4A), 1f to Color(0xFF262626))
/** Увімкнена кулька — світле скло зі світлом посередині. */
private val SwitchKnobOn = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFBDBDBD)))

/** Відблиск на скляній поверхні: м'яке світло вгорі всередині, що тане донизу (видно на великих картках). */
internal fun Modifier.glassSheen(): Modifier = drawBehind {
    val h = (size.height * 0.5f).coerceAtMost(120.dp.toPx())
    drawRect(Brush.verticalGradient(0f to Color(0x14FFFFFF), 1f to Color.Transparent, startY = 0f, endY = h), size = androidx.compose.ui.geometry.Size(size.width, h))
}

@Composable
internal fun Note(text: String) {
    Text(text, color = Oled.TextSecondary, fontSize = 13.sp)
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    // «Аркуш»: перемикача немає — увімкнене написано, вимкнене перекреслене олівцем; дотик — навпаки.
    if (LocalSheet.current) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { haptics.tap(); onCheckedChange(!checked) }
                .padding(vertical = 4.dp),
        ) {
            Text(
                title,
                color = if (checked) Oled.TextPrimary else Oled.TextSecondary,
                fontSize = 18.sp,
                modifier = if (checked) Modifier else Modifier.drawWithContent {
                    drawContent()
                    pencilStroke(size.height * 0.55f, -2f, size.width + 2f, seed = title.length)
                },
            )
            if (subtitle.isNotEmpty()) Text(subtitle, color = Oled.TextSecondary.copy(alpha = if (checked) 1f else 0.6f), fontSize = 13.sp)
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Oled.TextPrimary, fontSize = 17.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Oled.TextSecondary, fontSize = 13.sp)
        }
        GlassSwitch(checked) { haptics.tap(); onCheckedChange(it) }
    }
}

/** Варіант вибору: однакові рядки один під одним, вибраний — світліший і з точкою. */
@Composable
internal fun TargetOption(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) =
    TargetOption(stringResource(label), selected, onClick)

@Composable
internal fun TargetOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(14.dp)
    // «Аркуш»: вибране обведене олівцем, решта — просто написана, тьмяніше.
    if (LocalSheet.current) {
        Text(
            label,
            color = if (selected) Oled.TextPrimary else Oled.TextSecondary,
            fontSize = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (selected) Modifier.pencilFrame(14.dp, label.length) else Modifier)
                .selectable(selected = selected, onClick = { haptics.tap(); onClick() }, role = Role.RadioButton)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Oled.Knob else Oled.Surface)
            .border(1.dp, if (selected) Oled.GlintBright else Oled.Glint, shape)
            .selectable(selected = selected, onClick = { haptics.tap(); onClick() }, role = Role.RadioButton)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) Oled.TextPrimary else Oled.TextSecondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(Oled.TextPrimary))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (selected) Oled.TextPrimary else Oled.TextSecondary,
            fontSize = 16.sp,
        )
    }
}

/** Дія в тому ж вигляді, що й варіанти вибору, — щоб блок виглядав цілісно. */
@Composable
internal fun ActionOption(@StringRes label: Int, icon: ImageVector, onClick: () -> Unit) =
    ActionOption(stringResource(label), icon, onClick)

@Composable
internal fun ActionOption(label: String, icon: ImageVector, onClick: () -> Unit) = ActionOption(label, icon, Tone.Neutral, onClick)

/** Дія з акцентом: [Tone.Danger] — видалити/стерти (червоним), [Tone.Confirm] — зберегти/почати (синім). */
@Composable
internal fun ActionOption(@StringRes label: Int, icon: ImageVector, tone: Tone, onClick: () -> Unit) =
    ActionOption(stringResource(label), icon, tone, onClick)

@Composable
internal fun ActionOption(label: String, icon: ImageVector, tone: Tone, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(14.dp)
    // «Аркуш»: без значка — лише назва, підкреслена олівцем.
    if (LocalSheet.current) {
        Text(
            label,
            color = tone.color(),
            fontSize = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable { haptics.tap(); onClick() }
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .drawWithContent {
                    drawContent()
                    pencilStroke(size.height + 2.dp.toPx(), 0f, size.width * 0.6f, seed = label.length, alpha = 0.45f, ink = if (tone == Tone.Neutral) SheetInk else tone.color())
                },
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Oled.Surface)
            .border(1.dp, Oled.Glint, shape)
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HandIcon(icon, contentDescription = null, tint = if (tone == Tone.Neutral) Oled.TextSecondary else tone.color(), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = tone.color(), fontSize = 16.sp)
    }
}

/** Пункт меню налаштувань: значок, назва, коротке пояснення, стрілка. */
@Composable
internal fun MenuRow(icon: ImageVector, @StringRes title: Int, @StringRes hint: Int?, onClick: () -> Unit) =
    MenuRow({ m -> HandIcon(icon, null, m, Oled.TextSecondary) }, title, hint, onClick)

@Composable
internal fun MenuRow(icon: Glyph, @StringRes title: Int, @StringRes hint: Int?, onClick: () -> Unit) =
    MenuRow({ m -> HandGlyph(icon, null, m, Oled.TextSecondary) }, title, hint, onClick)

@Composable
private fun MenuRow(icon: @Composable (Modifier) -> Unit, @StringRes title: Int, @StringRes hint: Int?, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(14.dp)
    // «Аркуш»: пункти меню — просто написані рядки зі стрілкою, без значків.
    if (LocalSheet.current) {
        Row(
            Modifier.fillMaxWidth().clip(shape).clickable { haptics.tap(); onClick() }.padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), color = Oled.TextPrimary, fontSize = 19.sp)
                if (hint != null) Text(stringResource(hint), color = Oled.TextSecondary, fontSize = 13.sp)
            }
            Text("→", color = Oled.TextSecondary, fontSize = 20.sp)
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = Oled.TextPrimary, fontSize = 17.sp)
            if (hint != null) Text(stringResource(hint), color = Oled.TextSecondary, fontSize = 13.sp)
        }
        Text("›", color = Oled.TextSecondary, fontSize = 22.sp)
    }
}
