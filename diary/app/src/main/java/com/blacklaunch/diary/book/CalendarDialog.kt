package com.blacklaunch.diary.book

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.R
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.Glyph
import com.blacklaunch.diary.ui.GlyphIcon
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetButton
import com.blacklaunch.diary.ui.SheetDialog
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.WindowHint
import com.blacklaunch.diary.ui.pencilLoop
import com.blacklaunch.diary.ui.taperedPath
import com.blacklaunch.diary.ui.theme.Oled
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Календар щоденника: місяць, дні з записами обведено (олівцем чи склом), сьогодні — яскравіше. Дотик по дню —
 * перегорнути туди; по порожньому минулому дню — відкрити для нього чисту сторінку (майбутнє — не можна).
 */
@Composable
fun CalendarDialog(written: Set<Long>, start: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val haptics = LocalHaptics.current
    var month by remember { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(start))) }
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    SheetDialog(
        onDismissRequest = onDismiss,
        title = null,
        buttons = { SheetButton(stringResource(R.string.close), onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { haptics.tap(); month = month.minusMonths(1) }, contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.Back, stringResource(R.string.month_prev), tint = Oled.TextSecondary)
                }
                Text(
                    DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month).replaceFirstChar { it.titlecase(locale) },
                    color = Oled.TextPrimary,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                val canNext = month < YearMonth.from(today)
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(enabled = canNext) { haptics.tap(); month = month.plusMonths(1) },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Back, stringResource(R.string.month_next), tint = Oled.TextSecondary.copy(alpha = if (canNext) 1f else 0.3f), modifier = Modifier.graphicsLayer { scaleX = -1f })
                }
            }
            // Дні тижня — з понеділка.
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val dow = DayOfWeek.MONDAY.plus(i.toLong())
                    Text(dow.getDisplayName(TextStyle.SHORT_STANDALONE, locale), color = Oled.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            val first = month.atDay(1)
            val shift = (first.dayOfWeek.value - 1)
            val days = month.lengthOfMonth()
            val rows = (shift + days + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val n = r * 7 + c - shift + 1
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (n in 1..days) {
                                val date = month.atDay(n)
                                val ep = date.toEpochDay()
                                val has = ep in written
                                val isToday = date == today
                                val future = date.isAfter(today)
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !future) { haptics.tap(); onDismiss(); onPick(ep) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (has || isToday) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            if (AppStyle.glass) {
                                                drawCircle(if (isToday) Oled.TextPrimary.copy(alpha = 0.9f) else Oled.TextSecondary.copy(alpha = 0.7f), style = Stroke(1.dp.toPx()))
                                                if (has) drawCircle(Oled.TextPrimary.copy(alpha = 0.10f))
                                            } else {
                                                drawPath(taperedPath(pencilLoop(Size(size.width, size.height), size.minDimension / 2, n * 5, 1.dp.toPx()), if (isToday) 2.2.dp.toPx() else 1.6.dp.toPx()), SheetInk.copy(alpha = if (isToday) 0.95f else 0.6f))
                                            }
                                        }
                                    }
                                    Text(
                                        n.toString(),
                                        color = when {
                                            future -> Oled.TextSecondary.copy(alpha = 0.3f)
                                            has || isToday -> Oled.TextPrimary
                                            else -> Oled.TextSecondary
                                        },
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            WindowHint(stringResource(R.string.calendar_hint))
        }
    }
}

