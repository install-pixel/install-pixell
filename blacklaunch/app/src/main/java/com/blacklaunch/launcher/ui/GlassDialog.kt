package com.blacklaunch.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blacklaunch.launcher.ui.theme.Oled

/** Значення кнопки: звичайна, «підтвердити/зберегти» (синій акцент і ✓) чи «видалити» (червоний). */
enum class Tone { Neutral, Confirm, Danger }

internal fun Tone.color(): Color = when (this) {
    Tone.Neutral -> Oled.TextPrimary
    Tone.Confirm -> Oled.Confirm
    Tone.Danger -> Oled.Danger
}

private val DialogShape = RoundedCornerShape(24.dp)

/**
 * Вікно Black Glass замість стандартного системного: чорна скляна плашка з відблиском, як деталі моніторингу.
 * Параметри — як у AlertDialog, щоб усі вікна мали однаковий вигляд і порядок кнопок
 * (праворуч — головна дія, ліворуч від неї — «Скасувати»).
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .imePadding()
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(DialogShape)
                .background(Color(0xFF0A0A0A))
                .border(1.dp, Oled.Glint, DialogShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (title != null) {
                CompositionLocalProvider(LocalContentColor provides Oled.TextPrimary) { title() }
            }
            if (text != null) {
                // Довгий вміст гортається всередині; кнопки лишаються видимими.
                Box(Modifier.weight(1f, fill = false)) {
                    CompositionLocalProvider(LocalContentColor provides Oled.TextSecondary) { text() }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/**
 * Кнопка вікна: скляна «таблетка». [Tone.Confirm] — синій текст з ✓, [Tone.Danger] — червоний;
 * недоступна — притемнена.
 */
@Composable
fun DialogButton(text: String, onClick: () -> Unit, tone: Tone = Tone.Neutral, enabled: Boolean = true) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(14.dp)
    val color = if (enabled) tone.color() else Oled.TextSecondary.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .clip(shape)
            .then(if (tone == Tone.Neutral) Modifier else Modifier.background(Oled.Surface))
            .border(1.dp, if (tone == Tone.Neutral) Oled.Glint else androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.55f)), shape)
            .clickable(enabled = enabled) { haptics.tap(); onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tone == Tone.Confirm) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = 15.sp)
    }
}

/** Заголовок вікна — однаковий скрізь. */
@Composable
fun DialogTitle(text: String) {
    Text(text, color = Oled.TextPrimary, fontSize = 20.sp)
}
