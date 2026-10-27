package com.blacklaunch.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.viewmodel.LaunchPrompt
import com.blacklaunch.launcher.wellbeing.DayUsage
import com.blacklaunch.launcher.wellbeing.NowPlayingInfo
import com.blacklaunch.launcher.wellbeing.PauseRules
import com.blacklaunch.launcher.wellbeing.UsageMath
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** «1 год 05 хв» / «47 хв». */
@Composable
internal fun durationText(ms: Long): String {
    val (h, m) = UsageMath.hoursMinutes(ms)
    return if (h > 0) stringResource(R.string.wb_hours_minutes, h, m) else stringResource(R.string.wb_minutes, m)
}

/**
 * Пауза перед відволікальною програмою: кільце відліку, скільки вже сьогодні, і дві кнопки.
 * Головна — «Не відкривати»; «Відкрити» стає активною, коли кільце добіжить.
 */
@Composable
fun LaunchPauseDialog(prompt: LaunchPrompt, onOpen: () -> Unit, onCancel: () -> Unit) {
    val total = prompt.pause.seconds
    var left by remember(prompt) { mutableIntStateOf(total) }
    LaunchedEffect(prompt) {
        while (left > 0) {
            delay(1_000)
            left--
        }
    }
    GlassDialog(
        onDismissRequest = onCancel,
        title = { DialogTitle(prompt.app.label) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(96.dp)) {
                        val stroke = 4.dp.toPx()
                        drawArc(Color(0xFF262626), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                        drawArc(
                            Oled.TextPrimary, -90f, 360f * left / total.coerceAtLeast(1), false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        )
                    }
                    Text("$left", color = Oled.TextPrimary, fontSize = 30.sp)
                }
                Text(stringResource(R.string.wb_pause_breath), color = Oled.TextPrimary, fontSize = 16.sp, textAlign = TextAlign.Center)
                val pause = prompt.pause
                val used = pause.usedMinutes
                when {
                    pause.overLimit && used != null && pause.limitMinutes != null ->
                        Text(stringResource(R.string.wb_pause_over, used, pause.limitMinutes), color = Oled.Danger, fontSize = 15.sp, textAlign = TextAlign.Center)
                    used != null && pause.limitMinutes != null ->
                        Text(stringResource(R.string.wb_pause_used_limit, used, pause.limitMinutes), color = Oled.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
                    used != null ->
                        Text(stringResource(R.string.wb_pause_used, used), color = Oled.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.wb_pause_cancel), onCancel, Tone.Confirm) },
        dismissButton = {
            val label = stringResource(R.string.wb_pause_open)
            DialogButton(if (left > 0) "$label · $left" else label, onOpen, enabled = left == 0)
        },
    )
}

/** Маленькі значки керування музикою (у базовому наборі значків їх немає) — олівцевими лініями, як усе. */
private enum class MediaGlyph { PREVIOUS, PLAY, PAUSE, NEXT }

@Composable
private fun MediaButton(glyph: MediaGlyph, description: String, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = description) { haptics.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            val color = Oled.TextPrimary
            fun triangle(left: Float, right: Float) = Path().apply {
                moveTo(left, 0f); lineTo(right, h / 2); lineTo(left, h); close()
            }
            when (glyph) {
                MediaGlyph.PLAY -> drawPath(triangle(w * 0.15f, w * 0.95f), color)
                MediaGlyph.PAUSE -> {
                    drawRect(color, Offset(w * 0.18f, 0f), androidx.compose.ui.geometry.Size(w * 0.22f, h))
                    drawRect(color, Offset(w * 0.6f, 0f), androidx.compose.ui.geometry.Size(w * 0.22f, h))
                }
                MediaGlyph.NEXT -> {
                    drawPath(triangle(0f, w * 0.75f), color)
                    drawRect(color, Offset(w * 0.78f, 0f), androidx.compose.ui.geometry.Size(w * 0.16f, h))
                }
                MediaGlyph.PREVIOUS -> {
                    drawRect(color, Offset(w * 0.06f, 0f), androidx.compose.ui.geometry.Size(w * 0.16f, h))
                    drawPath(Path().apply { moveTo(w, 0f); lineTo(w * 0.25f, h / 2); lineTo(w, h); close() }, color)
                }
            }
        }
    }
}

/** «Що грає» внизу головного екрана: скляна плашка — назва й виконавець (дотик — відкрити гравець) і кнопки. */
@Composable
fun NowPlayingBar(info: NowPlayingInfo, onOpen: () -> Unit, onPrevious: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(Oled.Surface)
            .border(1.dp, Oled.Glint, shape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { haptics.tap(); onOpen() }
                .padding(vertical = 8.dp),
        ) {
            Text(info.title, color = Oled.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            info.artist?.let { Text(it, color = Oled.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        MediaButton(MediaGlyph.PREVIOUS, stringResource(R.string.wb_media_previous), onPrevious)
        MediaButton(if (info.playing) MediaGlyph.PAUSE else MediaGlyph.PLAY, stringResource(R.string.wb_media_toggle), onToggle)
        MediaButton(MediaGlyph.NEXT, stringResource(R.string.wb_media_next), onNext)
    }
}

/** Програма в списку паузи: назва й ліміт. */
data class PauseAppRow(val packageName: String, val label: String, val limitMinutes: Int?)

/** «Звички й екранний час»: пауза перед програмами з лімітами й тижневий підсумок. */
@Composable
fun WellbeingPage(
    onBack: () -> Unit,
    pauseApps: List<PauseAppRow>,
    pauseSeconds: Int,
    onPauseSeconds: (Int) -> Unit,
    onAddApps: () -> Unit,
    onLimit: (String, Int) -> Unit,
    onRemoveApp: (String) -> Unit,
    hasUsageAccess: Boolean,
    onGrantUsage: () -> Unit,
    week: List<DayUsage>?,
    appLabel: (String) -> String?,
) {
    var editing by remember { mutableStateOf<PauseAppRow?>(null) }
    SettingsPage(stringResource(R.string.wb_title), onBack) {
        Card {
            Text(stringResource(R.string.wb_pause_title), color = Oled.TextPrimary, fontSize = 17.sp)
            Note(stringResource(R.string.wb_pause_note))
            pauseApps.forEach { row ->
                val limit = row.limitMinutes
                TargetOption(
                    if (limit != null) stringResource(R.string.wb_app_limit, row.label, limit) else row.label,
                    selected = false,
                ) { editing = row }
            }
            ActionOption(R.string.wb_pause_add, Icons.Filled.Add, onAddApps)
            Text(stringResource(R.string.wb_pause_length), color = Oled.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
            PauseRules.PAUSE_SECONDS.forEach { seconds ->
                TargetOption(stringResource(R.string.wb_seconds, seconds), pauseSeconds == seconds) { onPauseSeconds(seconds) }
            }
            if (!hasUsageAccess) {
                Note(stringResource(R.string.wb_limits_need_access))
                ActionOption(R.string.monitor_usage_grant, Icons.Filled.Lock, onGrantUsage)
            }
        }
        Card {
            Text(stringResource(R.string.wb_week_title), color = Oled.TextPrimary, fontSize = 17.sp)
            if (!hasUsageAccess || week == null) {
                Note(stringResource(R.string.wb_week_need_access))
                ActionOption(R.string.monitor_usage_grant, Icons.Filled.Lock, onGrantUsage)
            } else {
                WeekChart(week)
                Text(
                    stringResource(R.string.wb_week_average, durationText(UsageMath.averageMs(week))),
                    color = Oled.TextSecondary,
                    fontSize = 14.sp,
                )
                val top = UsageMath.topApps(week, TOP_WEEK).mapNotNull { (pkg, ms) -> appLabel(pkg)?.let { it to ms } }
                if (top.isNotEmpty()) {
                    Text(stringResource(R.string.wb_week_top), color = Oled.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                    top.forEach { (label, ms) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = Oled.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(durationText(ms), color = Oled.TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
                Note(stringResource(R.string.wb_week_note))
            }
        }
    }
    editing?.let { row ->
        LimitDialog(
            row = row,
            onPick = { minutes ->
                onLimit(row.packageName, minutes)
                editing = null
            },
            onRemove = {
                onRemoveApp(row.packageName)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private const val TOP_WEEK = 5
private val BarMaxHeight = 88.dp

/** Сім стовпчиків: висота — час екрана за день, сьогодні — яскравіший. */
@Composable
private fun WeekChart(week: List<DayUsage>) {
    val max = week.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
    val dayFormat = remember { SimpleDateFormat("EEEEE", Locale.getDefault()) }
    Row(Modifier.fillMaxWidth().height(132.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEachIndexed { index, day ->
            val today = index == week.lastIndex
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val (h, m) = UsageMath.hoursMinutes(day.totalMs)
                Text(if (h > 0) "$h:${"%02d".format(m)}" else "$m", color = Oled.TextSecondary, fontSize = 10.sp, maxLines = 1)
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(18.dp)
                        .height(BarMaxHeight * (day.totalMs.toFloat() / max).coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (today) Oled.TextPrimary else Color(0xFF4A4A4A)),
                )
                Text(dayFormat.format(Date(day.dayStart)).uppercase(), color = if (today) Oled.TextPrimary else Oled.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

/** Денний ліміт програми (або прибрати її з паузи). */
@Composable
private fun LimitDialog(row: PauseAppRow, onPick: (Int) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(row.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.wb_limit_title), color = Oled.TextPrimary, fontSize = 16.sp)
                PauseRules.LIMITS.forEach { minutes ->
                    TargetOption(
                        if (minutes == 0) stringResource(R.string.wb_limit_none) else stringResource(R.string.wb_limit_minutes, minutes),
                        (row.limitMinutes ?: 0) == minutes,
                    ) { onPick(minutes) }
                }
                Note(stringResource(R.string.wb_limit_note))
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.close), onDismiss) },
        dismissButton = { DialogButton(stringResource(R.string.wb_pause_remove), onRemove, Tone.Danger) },
    )
}
