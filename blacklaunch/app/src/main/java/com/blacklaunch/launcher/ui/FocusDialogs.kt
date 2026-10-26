package com.blacklaunch.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.profiles.FocusRules
import com.blacklaunch.launcher.profiles.FocusSummary
import com.blacklaunch.launcher.profiles.LauncherProfile
import com.blacklaunch.launcher.ui.theme.Oled
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DialogColor = Color(0xFF141414)

/** Відлік, що йде лише поки вікно на екрані: закрили — наступного разу спочатку. */
@Composable
private fun countdown(seconds: Int): Int {
    var left by remember(seconds) { mutableIntStateOf(seconds) }
    LaunchedEffect(seconds) {
        while (left > 0) {
            delay(1_000)
            left--
        }
    }
    return left
}

/** Поточний час, що оновлюється кожні [periodMs], — для «ще 23:41» (лише поки це на екрані). */
@Composable
internal fun tickingNow(periodMs: Long = 1_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * «Важкий вихід»: вийти з режиму чи відкрити налаштування можна лише після відліку.
 * Головна кнопка — «Залишитись»: передумати легше, ніж піти.
 */
@Composable
fun ExitDelayDialog(title: String, seconds: Int, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val left = countdown(seconds)
    val haptics = LocalHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogColor,
        title = { Text(title, color = Oled.TextPrimary) },
        text = { Text(stringResource(R.string.focus_exit_note), color = Oled.TextSecondary, fontSize = 15.sp) },
        confirmButton = {
            TextButton(onClick = { haptics.tap(); onDismiss() }) { Text(stringResource(R.string.focus_stay), color = Oled.TextPrimary) }
        },
        dismissButton = {
            TextButton(onClick = { haptics.tap(); onConfirm() }, enabled = left == 0) {
                Text(if (left > 0) "$confirm ($left)" else confirm, color = if (left > 0) Oled.TextSecondary.copy(alpha = 0.5f) else Oled.TextSecondary)
            }
        },
    )
}

/** Фокус-таймер іде: нічого не перемикається; звідси — достроково (з утриманням і хвилиною відліку). */
@Composable
fun FocusLockedDialog(profileName: String, endsAt: Long, strict: Boolean, onEarlyExit: () -> Unit, onOpenStrict: () -> Unit, onDismiss: () -> Unit) {
    val now = tickingNow()
    var early by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    if (early) {
        EarlyExitDialog(onConfirm = onEarlyExit, onDismiss = onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogColor,
        title = { Text(stringResource(R.string.focus_locked_title, profileName), color = Oled.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(FocusRules.clock(endsAt - now), color = Oled.TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Light)
                Text(stringResource(R.string.focus_locked_note), color = Oled.TextSecondary, fontSize = 15.sp)
                if (strict) {
                    Text(
                        stringResource(R.string.focus_open_strict),
                        color = Oled.Accent,
                        fontSize = 15.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { haptics.tap(); onOpenStrict() }.padding(vertical = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { haptics.tap(); onDismiss() }) { Text(stringResource(R.string.focus_stay), color = Oled.TextPrimary) }
        },
        dismissButton = {
            TextButton(onClick = { haptics.tap(); early = true }) { Text(stringResource(R.string.focus_end_early), color = Oled.TextSecondary) }
        },
    )
}

/** Достроково: спершу утримати кнопку, потім хвилина відліку — імпульс за цей час зазвичай минає. */
@Composable
fun EarlyExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var held by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogColor,
        title = { Text(stringResource(R.string.focus_end_early_title), color = Oled.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.focus_end_early_note), color = Oled.TextSecondary, fontSize = 15.sp)
                if (!held) HoldButton(stringResource(R.string.focus_hold)) { held = true }
            }
        },
        confirmButton = {
            TextButton(onClick = { haptics.tap(); onDismiss() }) { Text(stringResource(R.string.focus_stay), color = Oled.TextPrimary) }
        },
        dismissButton = {
            if (held) {
                val left = countdown(FocusRules.EARLY_EXIT_S)
                val label = stringResource(R.string.focus_end)
                TextButton(onClick = { haptics.tap(); onConfirm() }, enabled = left == 0) {
                    Text(if (left > 0) "$label ($left)" else label, color = if (left > 0) Oled.TextSecondary.copy(alpha = 0.5f) else Oled.TextSecondary)
                }
            }
        },
    )
}

/** Кнопка, яку треба втримати [FocusRules.HOLD_MS]; відпустили раніше — смужка повертається на нуль. */
@Composable
private fun HoldButton(text: String, onHeld: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(Oled.Knob)
            .border(1.dp, Oled.Glint, shape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    haptics.tap()
                    val done = coroutineScope {
                        val filling = launch {
                            val start = System.currentTimeMillis()
                            while (progress < 1f) {
                                progress = ((System.currentTimeMillis() - start).toFloat() / FocusRules.HOLD_MS).coerceAtMost(1f)
                                delay(16)
                            }
                        }
                        tryAwaitRelease()
                        filling.cancel()
                        progress >= 1f
                    }
                    if (done) {
                        haptics.longPress()
                        onHeld()
                    } else {
                        progress = 0f
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress).background(Oled.Glint))
        Text(text, color = Oled.Accent, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
    }
}

/** Запуск фокус-таймера: режим, тривалість і «Суворо». */
@Composable
fun FocusStartDialog(profiles: List<LauncherProfile>, initialId: String?, onStart: (String, Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    var profileId by rememberSaveable { mutableStateOf(initialId ?: profiles.firstOrNull()?.id) }
    var minutes by rememberSaveable { mutableIntStateOf(FocusRules.DEFAULT_MINUTES) }
    var strict by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogColor,
        title = { Text(stringResource(R.string.focus_start_title), color = Oled.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.focus_start_profile), color = Oled.TextPrimary, fontSize = 16.sp)
                profiles.forEach { profile ->
                    TargetOption(profile.name, profileId == profile.id) { profileId = profile.id }
                }
                Text(stringResource(R.string.focus_start_duration), color = Oled.TextPrimary, fontSize = 16.sp)
                FocusRules.DURATIONS.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { value ->
                            DurationChip(stringResource(R.string.focus_minutes, value), value == minutes, Modifier.weight(1f)) { minutes = value }
                        }
                    }
                }
                SwitchRow(stringResource(R.string.focus_strict_title), stringResource(R.string.focus_strict_subtitle), strict) { strict = it }
                Note(stringResource(R.string.focus_start_note))
            }
        },
        confirmButton = {
            TextButton(onClick = { haptics.tap(); profileId?.let { onStart(it, minutes, strict) } }, enabled = profileId != null) {
                Text(stringResource(R.string.focus_start), color = Oled.TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = { haptics.tap(); onDismiss() }) { Text(stringResource(R.string.cancel), color = Oled.TextSecondary) }
        },
    )
}

@Composable
private fun DurationChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = text,
        color = if (selected) Color.Black else Oled.TextPrimary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(shape)
            .then(if (selected) Modifier.background(Oled.TextPrimary) else Modifier.background(Oled.Knob))
            .border(1.dp, Oled.Glint, shape)
            .clickable { haptics.tap(); onClick() }
            .padding(vertical = 8.dp),
    )
}

/** Підсумок після таймера — раз, коли повернулися на екран. */
@Composable
fun FocusSummaryDialog(summary: FocusSummary, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogColor,
        title = {
            Text(stringResource(if (summary.early) R.string.focus_ended_early else R.string.focus_done), color = Oled.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(summary.profileName, color = Oled.TextSecondary, fontSize = 14.sp)
                Text(stringResource(R.string.focus_minutes_of, summary.minutes, summary.plannedMinutes), color = Oled.TextPrimary, fontSize = 22.sp)
                Text(LocalContext.current.resources.getQuantityString(R.plurals.focus_attempts, summary.attempts, summary.attempts), color = Oled.TextSecondary, fontSize = 15.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { haptics.tap(); onDismiss() }) { Text(stringResource(R.string.alias_done), color = Oled.TextPrimary) }
        },
    )
}

/**
 * Екран «Суворо»: лише відлік. Android закріплює його (системне «Закріплення програми») — «Недавні», шторку
 * й інші програми блокує сама система до кінця таймера.
 */
@Composable
fun FocusStrictScreen(
    profileName: String,
    endsAt: Long,
    pinned: Boolean,
    onPin: () -> Unit,
    onEarlyExit: () -> Unit,
) {
    val now = tickingNow()
    var early by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(profileName.uppercase(), color = Oled.TextSecondary, fontSize = 13.sp, letterSpacing = 2.sp)
        Text(FocusRules.clock(endsAt - now), color = Oled.TextPrimary, fontSize = 64.sp, fontWeight = FontWeight.Light)
        Text(
            stringResource(if (pinned) R.string.focus_strict_pinned else R.string.focus_strict_not_pinned),
            color = Oled.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!pinned) SmallButton(stringResource(R.string.focus_pin), onPin)
            SmallButton(stringResource(R.string.focus_end_early)) { early = true }
        }
    }
    if (early) EarlyExitDialog(onConfirm = { early = false; onEarlyExit() }, onDismiss = { early = false })
}
