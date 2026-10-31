package com.blacklaunch.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

/** Велике кільце з числом посередині: скільки секунд лишилось із [total]. */
@Composable
private fun CountdownRing(left: Int, total: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            drawArc(Color(0xFF262626), 0f, 360f, false, style = Stroke(stroke))
            drawArc(Oled.TextPrimary, -90f, 360f * left / total.coerceAtLeast(1), false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$left", color = Oled.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Light)
    }
}

/**
 * «Вихід із режиму» з відліком: вийти чи відкрити налаштування — лише коли кільце добіжить.
 * Головна кнопка — «Залишитись»: передумати легше, ніж піти.
 */
@Composable
fun ExitDelayDialog(title: String, seconds: Int, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val left = countdown(seconds)
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CountdownRing(left, seconds)
                Text(stringResource(R.string.focus_exit_note), color = Oled.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.focus_stay), onDismiss, Tone.Confirm) },
        dismissButton = { DialogButton(if (left > 0) "$confirm · $left" else confirm, onConfirm, enabled = left == 0) },
    )
}

/** Фокус-таймер іде: нічого не перемикається. Звідси — екран таймера або «достроково» (два кроки). */
@Composable
fun FocusLockedDialog(profileName: String, endsAt: Long, onEarlyExit: () -> Unit, onOpenTimer: () -> Unit, onDismiss: () -> Unit) {
    val now = tickingNow()
    var early by rememberSaveable { mutableStateOf(false) }
    if (early) {
        EarlyExitDialog(onConfirm = onEarlyExit, onDismiss = onDismiss)
        return
    }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(stringResource(R.string.focus_locked_title, profileName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(FocusRules.clock(endsAt - now), color = Oled.TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Light)
                Text(stringResource(R.string.focus_locked_note), color = Oled.TextSecondary, fontSize = 15.sp)
                ActionOption(R.string.focus_open_timer, Icons.Filled.PlayArrow, onOpenTimer)
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.focus_stay), onDismiss, Tone.Confirm) },
        dismissButton = { DialogButton(stringResource(R.string.focus_end_early), { early = true }, Tone.Danger) },
    )
}

/**
 * Достроково — два явні кроки: 1) утримати кнопку 3 с (смужка заповнюється, «ще 2 с»);
 * 2) хвилина відліку на кільці. Лише тоді «Завершити» стає активною. «Залишитись» — завжди.
 */
@Composable
fun EarlyExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var held by rememberSaveable { mutableStateOf(false) }
    // Один відлік на весь крок 2 — і для кільця, і для кнопки.
    val left = if (held) countdown(FocusRules.EARLY_EXIT_S) else FocusRules.EARLY_EXIT_S
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(stringResource(R.string.focus_end_early_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!held) {
                    Text(stringResource(R.string.focus_step1), color = Oled.TextSecondary, fontSize = 15.sp)
                    HoldButton { held = true }
                } else {
                    Text(
                        stringResource(if (left > 0) R.string.focus_step2 else R.string.focus_step_done),
                        color = Oled.TextSecondary,
                        fontSize = 15.sp,
                    )
                    CountdownRing(left, FocusRules.EARLY_EXIT_S)
                }
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.focus_stay), onDismiss, Tone.Confirm) },
        dismissButton = {
            if (held) {
                val label = stringResource(R.string.focus_end)
                DialogButton(if (left > 0) "$label · $left" else label, onConfirm, Tone.Danger, enabled = left == 0)
            }
        },
    )
}

/** Кнопка, яку треба втримати [FocusRules.HOLD_MS]: смужка заповнюється й лічить «ще 2 с»; відпустили — з нуля. */
@Composable
private fun HoldButton(onHeld: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHaptics.current
    val held by rememberUpdatedState(onHeld)
    val shape = RoundedCornerShape(14.dp)
    val secondsLeft = ((1f - progress) * FocusRules.HOLD_MS / 1000f).let { kotlin.math.ceil(it).toInt() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (LocalSheet.current) {
                    Modifier.pencilFrame(14.dp, seed = 23, ink = Oled.Danger).clip(shape)
                } else {
                    Modifier.clip(shape).background(Oled.Surface).border(1.dp, androidx.compose.ui.graphics.SolidColor(Oled.Danger.copy(alpha = 0.55f)), shape)
                },
            )
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
                        held()
                    } else {
                        progress = 0f
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress).background(Oled.Danger.copy(alpha = 0.22f)))
        Text(stringResource(R.string.focus_hold, secondsLeft.coerceAtLeast(1)), color = Oled.Danger, fontSize = 16.sp, modifier = Modifier.padding(14.dp))
    }
}

/** Запуск фокус-таймера: режим, тривалість, «Суворо» і котик. */
@Composable
fun FocusStartDialog(
    profiles: List<LauncherProfile>,
    initialId: String?,
    cat: Boolean,
    onCatChange: (Boolean) -> Unit,
    onStart: (String, Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var profileId by rememberSaveable { mutableStateOf(initialId ?: profiles.firstOrNull()?.id) }
    var hours by rememberSaveable { mutableIntStateOf(FocusRules.DEFAULT_MINUTES / 60) }
    var mins by rememberSaveable { mutableIntStateOf(FocusRules.DEFAULT_MINUTES % 60) }
    // Щонайменше хвилина.
    val minutes = (hours * 60 + mins).coerceAtLeast(1)
    var strict by rememberSaveable { mutableStateOf(false) }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(stringResource(R.string.focus_start_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.focus_start_profile), color = Oled.TextPrimary, fontSize = 16.sp)
                profiles.forEach { profile ->
                    TargetOption(profile.name, profileId == profile.id) { profileId = profile.id }
                }
                Text(stringResource(R.string.focus_start_duration), color = Oled.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                // Години й хвилини — коліщатками, як у звичайному таймері (гортати пальцем).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WheelPicker(MAX_HOURS + 1, hours, { hours = it }, { "$it" }, Modifier.weight(1f))
                    Text(stringResource(R.string.focus_hours_short), color = Oled.TextSecondary, fontSize = 15.sp)
                    WheelPicker(60, mins, { mins = it }, { "%02d".format(it) }, Modifier.weight(1f))
                    Text(stringResource(R.string.focus_minutes_short), color = Oled.TextSecondary, fontSize = 15.sp)
                }
                SwitchRow(stringResource(R.string.focus_strict_title), stringResource(R.string.focus_strict_subtitle), strict) { strict = it }
                SwitchRow(stringResource(R.string.focus_cat_title), stringResource(R.string.focus_cat_subtitle), cat, onCatChange)
                Note(stringResource(R.string.focus_start_note))
            }
        },
        confirmButton = {
            DialogButton(stringResource(R.string.focus_start), { profileId?.let { onStart(it, minutes, strict) } }, Tone.Confirm, enabled = profileId != null)
        },
        dismissButton = { DialogButton(stringResource(R.string.cancel), onDismiss) },
    )
}

/**
 * Коліщатко вибору числа (як у системному таймері): гортається пальцем і зупиняється рівно на числі; вибране —
 * посередині, у рамці (скло чи олівець — за темою).
 */
@Composable
private fun WheelPicker(count: Int, value: Int, onValue: (Int) -> Unit, label: (Int) -> String, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val itemHeight = 40.dp
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value)
    val changed by rememberUpdatedState(onValue)
    LaunchedEffect(state) {
        var last = value
        snapshotFlow { (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0).coerceIn(0, count - 1) }
            .collect { picked ->
                if (picked != last) {
                    last = picked
                    haptics.tick()
                    changed(picked)
                }
            }
    }
    Box(modifier.height(itemHeight * 3), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(itemHeight).glassBox(12.dp, seed = count))
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = itemHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count) { i ->
                Box(Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        label(i),
                        color = if (i == value) Oled.TextPrimary else Oled.TextSecondary.copy(alpha = 0.6f),
                        fontSize = if (i == value) 22.sp else 17.sp,
                    )
                }
            }
        }
    }
}

private const val MAX_HOURS = 12

/** Підсумок після таймера — раз, коли повернулися на екран. */
@Composable
fun FocusSummaryDialog(summary: FocusSummary, onDismiss: () -> Unit) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(stringResource(if (summary.early) R.string.focus_ended_early else R.string.focus_done)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(summary.profileName, color = Oled.TextSecondary, fontSize = 14.sp)
                Text(stringResource(R.string.focus_minutes_of, summary.minutes, summary.plannedMinutes), color = Oled.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Light)
                Text(
                    LocalContext.current.resources.getQuantityString(R.plurals.focus_attempts, summary.attempts, summary.attempts),
                    color = Oled.TextSecondary,
                    fontSize = 15.sp,
                )
                // Що таке «спроби» — щоб не гадати.
                Note(stringResource(R.string.focus_attempts_note))
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.alias_done), onDismiss, Tone.Confirm) },
    )
}

/** Стан закріплення на екрані «Суворо». */
enum class PinState { NONE, ASKING, PINNED, NOT_PINNED }

/**
 * Екран фокус-таймера: великий відлік на скляній плашці (огорожа для котика), котик гуляє довкола.
 * «Суворо» — угорі позначка закріплення; без нього — «Згорнути».
 */
@Composable
fun FocusTimerScreen(
    profileName: String,
    endsAt: Long,
    pin: PinState,
    cat: Boolean,
    onPin: () -> Unit,
    onMinimize: (() -> Unit)?,
    onEarlyExit: () -> Unit,
) {
    val now = tickingNow()
    var early by rememberSaveable { mutableStateOf(false) }
    // Перешкоди для котика: плашка відліку, кнопки внизу, позначка закріплення вгорі (поки видна).
    var fence by remember { mutableStateOf<Rect?>(null) }
    var buttons by remember { mutableStateOf<Rect?>(null) }
    var chip by remember { mutableStateOf<Rect?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cat) FocusCat(listOfNotNull(fence, buttons, chip))
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // «Закріплено» — коротко підтверджуємо й ховаємо: далі напис не потрібен.
            var pinnedShown by remember(pin) { mutableStateOf(true) }
            LaunchedEffect(pin) {
                if (pin == PinState.PINNED) {
                    delay(PINNED_VISIBLE_MS)
                    pinnedShown = false
                }
            }
            val chipShown = pin != PinState.NONE && (pin != PinState.PINNED || pinnedShown)
            if (chipShown) {
                Box(Modifier.onGloballyPositioned { chip = it.boundsInRoot() }) { PinChip(pin, onPin) }
            }
            LaunchedEffect(chipShown) { if (!chipShown) chip = null }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .onGloballyPositioned { fence = it.boundsInRoot() }
                        .glassBox(28.dp, seed = 53, fill = SolidColor(Color(0xFF0A0A0A)))
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(profileName.uppercase(), color = Oled.TextSecondary, fontSize = 12.sp, letterSpacing = 2.sp)
                    Text(FocusRules.clock(endsAt - now), color = Oled.TextPrimary, fontSize = 60.sp, fontWeight = FontWeight.Light)
                }
            }
            Row(Modifier.onGloballyPositioned { buttons = it.boundsInRoot() }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                onMinimize?.let { DialogButton(stringResource(R.string.focus_minimize), it) }
                DialogButton(stringResource(R.string.focus_end_early), { early = true }, Tone.Danger)
            }
        }
    }
    if (early) EarlyExitDialog(onConfirm = { early = false; onEarlyExit() }, onDismiss = { early = false })
}

private const val PINNED_VISIBLE_MS = 3_000L

/** Позначка вгорі: закріплено / Android питає / не закріплено + «Закріпити». */
@Composable
private fun PinChip(pin: PinState, onPin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassBox(16.dp, seed = 59, fill = SolidColor(Color(0xFF0A0A0A)), paper = Color.Black)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val text = when (pin) {
            PinState.PINNED -> R.string.focus_strict_pinned
            PinState.ASKING -> R.string.focus_strict_asking
            else -> R.string.focus_strict_not_pinned
        }
        Text(stringResource(text), color = if (pin == PinState.PINNED) Oled.TextPrimary else Oled.TextSecondary, fontSize = 15.sp)
        if (pin == PinState.NOT_PINNED) DialogButton(stringResource(R.string.focus_pin), onPin, Tone.Confirm)
    }
}
