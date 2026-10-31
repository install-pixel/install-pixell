package com.blacklaunch.launcher.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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
        BlurBehindDialog()
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .imePadding()
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(DialogShape)
                .background(Color(0xFF0A0A0A))
                .then(if (LocalSheet.current) Modifier.pencilFrame(24.dp, seed = 7) else Modifier.border(1.dp, Oled.Glint, DialogShape))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (title != null) {
                CompositionLocalProvider(LocalContentColor provides Oled.TextPrimary) { title() }
            }
            if (text != null) {
                // Довгий вміст гортається всередині; кнопки лишаються видимими.
                Box(Modifier.weight(1f, fill = false).fadingEdges(top = EdgeFade / 2, bottom = EdgeFade)) {
                    CompositionLocalProvider(LocalContentColor provides Oled.TextSecondary) { text() }
                }
            }
            DialogButtons {
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
    val sheet = LocalSheet.current
    Row(
        modifier = Modifier
            .clip(shape)
            .then(
                when {
                    // Синя (зберегти) чи червона (видалити) рамка олівцем — як акцент на склі.
                    sheet -> Modifier.pencilFrame(14.dp, text.length, alpha = if (enabled) 0.75f else 0.3f, ink = if (tone == Tone.Neutral) SheetInk else color)
                    else -> Modifier
                        .then(if (tone == Tone.Neutral) Modifier else Modifier.background(Oled.Surface))
                        .border(1.dp, if (tone == Tone.Neutral) Oled.Glint else androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.55f)), shape)
                },
            )
            .clickable(enabled = enabled) { haptics.tap(); onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Кнопці дали ширшу за напис ширину (кнопки рівні) — напис посередині.
        horizontalArrangement = Arrangement.Center,
    ) {
        if (tone == Tone.Confirm && !sheet) {
            HandIcon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/**
 * Кнопки внизу вікна: вміщаються в рядок — праворуч, кожна свого розміру; не вміщаються — однакової ширини
 * на весь рядок, напис переноситься лише цілими словами (не посеред слова).
 */
@Composable
private fun DialogButtons(content: @Composable () -> Unit) {
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val available = constraints.maxWidth
        val natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val fits = natural.sum() + gap * (measurables.size - 1).coerceAtLeast(0) <= available
        val equal = if (measurables.isEmpty()) 0 else (available - gap * (measurables.size - 1)) / measurables.size
        val placeables = measurables.mapIndexed { i, m ->
            val w = if (fits) natural[i] else equal
            m.measure(Constraints(minWidth = w, maxWidth = w))
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(available, height) {
            var x = if (fits) available - (placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)) else 0
            placeables.forEach { p ->
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width + gap
            }
        }
    }
}

/**
 * Відкрилось вікно — усе позаду нього розмивається (Android 12+, розмиття робить сама система; на старших —
 * лише притемнення, як і було).
 */
@Composable
internal fun BlurBehindDialog() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.also { it.blurBehindRadius = DIALOG_BLUR_PX }
        }
        onDispose { }
    }
}

private const val DIALOG_BLUR_PX = 48

/** Заголовок вікна — однаковий скрізь. */
@Composable
fun DialogTitle(text: String) {
    Text(text, color = Oled.TextPrimary, fontSize = 20.sp)
}
