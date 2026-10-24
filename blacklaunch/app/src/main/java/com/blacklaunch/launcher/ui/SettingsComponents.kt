package com.blacklaunch.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.ui.theme.Oled
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/** Спільні елементи екранів налаштувань: картки, перемикачі, варіанти вибору, дії. */

internal val CardShape = RoundedCornerShape(20.dp)

@Composable
internal fun SmallButton(text: String, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = text,
        color = Oled.Accent,
        fontSize = 15.sp,
        modifier = Modifier
            .clip(shape)
            .background(Oled.Knob)
            .border(1.dp, Oled.Glint, shape)
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Oled.Surface)
            .border(1.dp, Oled.Glint, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
internal fun Note(text: String) {
    Text(text, color = Oled.TextSecondary, fontSize = 13.sp)
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Oled.TextPrimary, fontSize = 17.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Oled.TextSecondary, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = { haptics.tap(); onCheckedChange(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFF2F2F2),
                checkedTrackColor = Color(0xFF5A5A5A),
                uncheckedThumbColor = Color(0xFF7A7A7A),
                uncheckedTrackColor = Color(0xFF1E1E1E),
                uncheckedBorderColor = Color(0xFF3A3A3A),
            ),
        )
    }
}

/** Варіант вибору: однакові рядки один під одним, вибраний — світліший і з точкою. */
@Composable
internal fun TargetOption(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Oled.Knob else Oled.Surface)
            .border(1.dp, Oled.Glint, shape)
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
            stringResource(label),
            color = if (selected) Oled.TextPrimary else Oled.TextSecondary,
            fontSize = 16.sp,
        )
    }
}

/** Дія в тому ж вигляді, що й варіанти вибору, — щоб блок виглядав цілісно. */
@Composable
internal fun ActionOption(@StringRes label: Int, icon: ImageVector, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(14.dp)
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
        Icon(icon, contentDescription = null, tint = Oled.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(label), color = Oled.TextPrimary, fontSize = 16.sp)
    }
}

/** Пункт меню налаштувань: значок, назва, коротке пояснення, стрілка. */
@Composable
internal fun MenuRow(icon: ImageVector, @StringRes title: Int, @StringRes hint: Int?, onClick: () -> Unit) =
    MenuRow(rememberVectorPainter(icon), title, hint, onClick)

@Composable
internal fun MenuRow(icon: Painter, @StringRes title: Int, @StringRes hint: Int?, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Oled.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = Oled.TextPrimary, fontSize = 17.sp)
            if (hint != null) Text(stringResource(hint), color = Oled.TextSecondary, fontSize = 13.sp)
        }
        Text("›", color = Oled.TextSecondary, fontSize = 22.sp)
    }
}
