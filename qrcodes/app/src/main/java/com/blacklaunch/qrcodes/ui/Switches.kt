package com.blacklaunch.qrcodes.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.qrcodes.ui.theme.Oled

/**
 * Скляний перемикач (як у лаунчері): капсула з чорного скла з кантом, усередині — скляна кулька. Увімкнено — капсула
 * підсвічена, кулька праворуч і яскрава; вимкнено — темна, кулька ліворуч і тьмяна. Кулька їде плавно.
 */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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

private val SwitchTrackOn = Brush.verticalGradient(0f to Color(0xFF4A4A4A), 1f to Color(0xFF262626))
private val SwitchKnobOn = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFBDBDBD)))

/**
 * Пункт «увімкнути / вимкнути» — однаково скрізь (налаштування, редактор форми, сама форма):
 *  - «Скло»: назва (і пояснення) ліворуч, скляний перемикач праворуч;
 *  - «Аркуш»: перемикача немає — увімкнене просто написане, вимкнене тьмяне й перекреслене олівцем; дотик — навпаки.
 * [subtitle] — пояснення чи значення під назвою (може бути порожнім).
 */
@Composable
fun SwitchRow(title: String, checked: Boolean, modifier: Modifier = Modifier, subtitle: String = "", onCheckedChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    if (!AppStyle.glass) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { haptics.tap(); onCheckedChange(!checked) }
                .padding(vertical = 4.dp),
        ) {
            Text(
                title,
                color = if (checked) Oled.TextPrimary else Oled.TextSecondary,
                fontSize = 17.sp,
                modifier = if (checked) {
                    Modifier
                } else {
                    Modifier.drawWithContent {
                        drawContent()
                        pencilStroke(size.height * 0.55f, -2f, size.width + 2f, seed = title.length)
                    }
                },
            )
            if (subtitle.isNotEmpty()) Text(subtitle, color = Oled.TextSecondary.copy(alpha = if (checked) 1f else 0.6f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Oled.TextPrimary, fontSize = 16.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        GlassSwitch(checked) { haptics.tap(); onCheckedChange(it) }
    }
}
