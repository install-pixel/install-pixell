package com.blacklaunch.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

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
private enum class MediaGlyph { PREVIOUS, PLAY, PAUSE, NEXT, STOP }

@Composable
private fun MediaButton(glyph: MediaGlyph, description: String, big: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val sheet = LocalSheet.current
    val box = if (big) 64.dp else 40.dp
    val shape = RoundedCornerShape(box / 2)
    Box(
        modifier = Modifier
            .size(box)
            .then(
                when {
                    !big -> Modifier.clip(shape)
                    sheet -> Modifier.pencilFrame(box / 2, seed = glyph.ordinal + 31, alpha = if (enabled) 0.75f else 0.3f).clip(shape)
                    else -> Modifier.clip(shape).background(Oled.Knob).border(1.dp, Oled.Glint, shape)
                },
            )
            // Гравець цього не вміє — кнопка притемнена й не натискається.
            .clickable(enabled = enabled, onClickLabel = description) { haptics.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(if (big) 24.dp else 16.dp)) {
            val color = if (enabled) Oled.TextPrimary else Oled.TextSecondary.copy(alpha = 0.35f)
            if (sheet) drawHandGlyph(glyph, color, glyph.ordinal) else drawGlyph(glyph, color)
        }
    }
}

private fun DrawScope.drawGlyph(glyph: MediaGlyph, color: Color) {
    val w = size.width
    val h = size.height
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
        MediaGlyph.STOP -> drawRect(color, Offset(w * 0.1f, h * 0.1f), androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.8f))
        MediaGlyph.PREVIOUS -> {
            drawRect(color, Offset(w * 0.06f, 0f), androidx.compose.ui.geometry.Size(w * 0.16f, h))
            drawPath(Path().apply { moveTo(w, 0f); lineTo(w * 0.25f, h / 2); lineTo(w, h); close() }, color)
        }
    }
}

/**
 * «Аркуш»: ті самі знаки, але намальовані від руки — контуром олівця з легким тремтінням і другим тьмяним штрихом,
 * без заливки (як усе на аркуші).
 */
private fun DrawScope.drawHandGlyph(glyph: MediaGlyph, color: Color, seed: Int) {
    val w = size.width
    val h = size.height
    val wob = w * 0.035f
    fun j(i: Int) = kotlin.math.sin(seed * 1.9f + i * 2.7f) * wob
    fun p(x: Float, y: Float) = Offset(x, y)
    // Кожен знак — кілька штрихів; кожен штрих тоншає й загострюється на кінцях.
    fun tri(tip: Float, back: Float, k: Int): List<Offset> {
        val mid = (tip + back) / 2
        return quadPoints(p(back + j(k), j(k + 1)), p(mid + j(k + 2), h * 0.25f + j(k + 3)), p(tip + j(k + 4), h / 2 + j(k + 5))) +
            quadPoints(p(tip + j(k + 4), h / 2 + j(k + 5)), p(mid + j(k + 6), h * 0.75f + j(k + 7)), p(back + j(k + 8), h + j(k + 9))) +
            quadPoints(p(back + j(k + 8), h + j(k + 9)), p(back - wob + j(k + 10), h / 2), p(back + j(k + 11), j(k + 12) + wob))
    }
    fun bar(x: Float, k: Int) = quadPoints(p(x + j(k), j(k + 1)), p(x + j(k + 2) * 2f, h / 2), p(x + j(k + 3), h + j(k + 4)))
    val strokes: List<List<Offset>> = when (glyph) {
        MediaGlyph.PLAY -> listOf(tri(w * 0.95f, w * 0.18f, 0))
        MediaGlyph.PAUSE -> listOf(bar(w * 0.3f, 0), bar(w * 0.7f, 5))
        MediaGlyph.NEXT -> listOf(tri(w * 0.72f, w * 0.02f, 0), bar(w * 0.86f, 13))
        MediaGlyph.PREVIOUS -> listOf(bar(w * 0.14f, 13), tri(w * 0.28f, w * 0.98f, 0))
        MediaGlyph.STOP -> {
            val a = w * 0.1f
            val b = w * 0.9f
            listOf(
                quadPoints(p(a + j(0), a + j(1)), p(w / 2, a + j(2) * 1.5f), p(b + j(3), a + j(4))) +
                    quadPoints(p(b + j(3), a + j(4)), p(b + j(5) * 1.5f, h / 2), p(b + j(6), b + j(7))) +
                    quadPoints(p(b + j(6), b + j(7)), p(w / 2, b + j(8) * 1.5f), p(a + j(9), b + j(10))) +
                    quadPoints(p(a + j(9), b + j(10)), p(a + j(11) * 1.5f, h / 2), p(a + j(12) + wob, a + j(13))),
            )
        }
    }
    val stroke = if (glyph == MediaGlyph.PAUSE) w * 0.17f else w * 0.13f
    strokes.forEach { points ->
        drawPath(taperedPath(points, stroke), color)
        translate(stroke * 0.5f, stroke * 0.35f) {
            drawPath(taperedPath(points, stroke * 0.6f), color.copy(alpha = color.alpha * 0.3f))
        }
    }
}

/** Плашка «Що грає» — як поле пошуку: та сама висота й заокруглення на всю висоту. */
private val NowPlayingHeight = 56.dp
private val NowPlayingRadius = 28.dp

/**
 * «Що грає» внизу головного екрана: скляна плашка — назва й виконавець (дотик — повноекранний гравець)
 * і кнопки «назад», «пауза», «далі», «стоп» (зупиняє й одразу прибирає плашку).
 */
@Composable
fun NowPlayingBar(
    info: NowPlayingInfo,
    onExpand: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val sheet = LocalSheet.current
    // Як поле пошуку з мікрофоном: ліворуч капсула (назва, «назад», «далі», «стоп»), праворуч — окреме коло
    // з «пауза / грати» на місці мікрофона. Та сама висота й ті самі відступи — пошук відкривається на тому ж місці.
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                // Не нижча за поле пошуку, але росте, якщо шрифт телефона великий (текст не обрізається).
                .heightIn(min = NowPlayingHeight)
                .glassBox(NowPlayingRadius, seed = 17)
                .padding(start = 18.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { haptics.tap(); onExpand() }
                    .padding(vertical = 6.dp),
            ) {
                Text(info.title, color = Oled.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                info.artist?.let { Text(it, color = Oled.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            MediaButton(MediaGlyph.PREVIOUS, stringResource(R.string.wb_media_previous), enabled = info.canPrevious, onClick = onPrevious)
            MediaButton(MediaGlyph.NEXT, stringResource(R.string.wb_media_next), enabled = info.canNext, onClick = onNext)
            MediaButton(MediaGlyph.STOP, stringResource(R.string.wb_media_stop), onClick = onStop)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(NowPlayingHeight)
                .then(
                    if (sheet) {
                        Modifier.pencilFrame(NowPlayingHeight / 2, seed = 19).clip(CircleShape)
                    } else {
                        Modifier.clip(CircleShape).background(Oled.Knob).border(1.dp, Oled.Glint, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            MediaButton(if (info.playing) MediaGlyph.PAUSE else MediaGlyph.PLAY, stringResource(R.string.wb_media_toggle), onClick = onToggle)
        }
    }
}

/**
 * «Аркуш»: шкала програвання від руки — тонка тьмяна лінія олівцем на всю довжину, зігране — товстіший штрих,
 * а повзунок — кружечок, обведений олівцем. Тягнути чи торкнутися — перемотати (якщо гравець дозволяє).
 */
@Composable
private fun HandSeekBar(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val ink = if (enabled) Oled.TextPrimary else Oled.TextSecondary
    Canvas(
        modifier
            .height(32.dp)
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        val pad = 10.dp.toPx()
                        fun at(x: Float) = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                        detectHorizontalDragGestures(
                            onDragStart = { change(at(it.x)) },
                            onHorizontalDrag = { c, _ -> c.consume(); change(at(c.position.x)) },
                            onDragEnd = { finished() },
                            onDragCancel = { finished() },
                        )
                    }.pointerInput(Unit) {
                        val pad = 10.dp.toPx()
                        detectTapGestures { change(((it.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)); finished() }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val pad = 10.dp.toPx()
        val y = size.height / 2
        val start = pad
        val end = size.width - pad
        val x = start + (end - start) * value
        // Уся шкала — легка, ледь хвиляста лінія.
        val track = quadPoints(Offset(start, y + 1.dp.toPx()), Offset((start + end) / 2, y - 1.5.dp.toPx()), Offset(end, y + 0.5.dp.toPx()), steps = 40)
        drawPath(taperedPath(track, 2.dp.toPx()), ink.copy(alpha = 0.3f))
        // Зігране — густіший штрих по тій самій лінії.
        val played = track.filter { it.x <= x }
        if (played.size >= 2) drawPath(taperedPath(played + Offset(x, played.last().y), 4.dp.toPx()), ink)
        // Повзунок — кружечок олівцем на чорному.
        val r = 8.dp.toPx()
        val thumbY = played.lastOrNull()?.y ?: y
        drawCircle(Color.Black, r, Offset(x, thumbY))
        translate(x - r, thumbY - r) {
            drawPath(taperedPath(pencilLoop(Size(2 * r, 2 * r), r, 37, 0.6.dp.toPx()), 2.dp.toPx()), ink)
        }
    }
}

/** «Аркуш»: нота, намальована олівцем (замість обкладинки, коли її немає). */
@Composable
private fun HandNote(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.06f
        // Головка ноти — овал олівцем, штиль угору й прапорець.
        val headW = w * 0.42f
        val headH = h * 0.3f
        translate(w * 0.12f, h * 0.64f) {
            drawPath(taperedPath(pencilLoop(Size(headW, headH), headH / 2, 97, stroke * 0.3f), stroke), color)
        }
        val stemX = w * 0.12f + headW - stroke * 0.4f
        pencilTick(Offset(stemX, h * 0.78f), Offset(stemX + w * 0.01f, h * 0.06f), stroke, color, 3)
        drawPath(
            taperedPath(quadPoints(Offset(stemX, h * 0.06f), Offset(w * 0.98f, h * 0.18f), Offset(w * 0.84f, h * 0.46f), steps = 20), stroke),
            color,
        )
    }
}

/** «1:05» / «1:02:07». */
private fun clockText(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/**
 * Повноекранний гравець: обкладинка, назва, виконавець, альбом, смужка часу (перемотування, якщо гравець дозволяє),
 * великі кнопки, «Стоп» і «Відкрити в програмі». «Назад» чи стрілка вгорі — згорнути.
 */
@Composable
fun NowPlayingScreen(
    info: NowPlayingInfo,
    appName: String?,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenApp: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(info.playing) {
            while (info.playing) {
                delay(500)
                tick++
            }
        }
        // Поки тягнуть смужку — показуємо, куди перемотають; відпустили — перемотуємо.
        var dragging by remember { mutableStateOf<Float?>(null) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (LocalSheet.current) {
                    // «Аркуш»: «згорнути» — стрілка вниз, намальована олівцем.
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        HandIcon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.back), tint = Oled.TextSecondary, modifier = Modifier.size(30.dp))
                    }
                } else {
                    Text(
                        "⌄",
                        color = Oled.TextSecondary,
                        fontSize = 30.sp,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClose).padding(horizontal = 12.dp),
                    )
                }
                Text(
                    appName.orEmpty().uppercase(),
                    color = Oled.TextSecondary,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Box(Modifier.size(42.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val shape = RoundedCornerShape(28.dp)
                Box(
                    Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                        .glassBox(28.dp, seed = 29, fill = androidx.compose.ui.graphics.SolidColor(Color(0xFF0A0A0A))),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = info.art
                    if (art != null) {
                        androidx.compose.foundation.Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (LocalSheet.current) {
                        HandNote(Oled.TextSecondary, Modifier.fillMaxSize(0.42f))
                    } else {
                        Text("♪", color = Oled.TextSecondary, fontSize = 72.sp)
                    }
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(info.title, color = Oled.TextPrimary, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                info.artist?.let { Text(it, color = Oled.TextSecondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                info.album?.let { Text(it, color = Oled.TextSecondary.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (info.durationMs > 0) {
                // [tick] — щоб позиція перераховувалась щопівсекунди, поки грає.
                val position = dragging?.let { (it * info.durationMs).toLong() } ?: (tick.let { info.positionNow() })
                if (LocalSheet.current) {
                    HandSeekBar(
                        value = (position.toFloat() / info.durationMs).coerceIn(0f, 1f),
                        enabled = info.canSeek,
                        onValueChange = { dragging = it },
                        onValueChangeFinished = {
                            dragging?.let { onSeek((it * info.durationMs).toLong()) }
                            dragging = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                } else androidx.compose.material3.Slider(
                    value = (position.toFloat() / info.durationMs).coerceIn(0f, 1f),
                    onValueChange = { if (info.canSeek) dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onSeek((it * info.durationMs).toLong()) }
                        dragging = null
                    },
                    enabled = info.canSeek,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Oled.TextPrimary,
                        activeTrackColor = Oled.TextPrimary,
                        inactiveTrackColor = Color(0xFF333333),
                        disabledThumbColor = Oled.TextSecondary,
                        disabledActiveTrackColor = Oled.TextSecondary,
                        disabledInactiveTrackColor = Color(0xFF262626),
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(clockText(position), color = Oled.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(clockText(info.durationMs), color = Oled.TextSecondary, fontSize = 12.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaButton(MediaGlyph.STOP, stringResource(R.string.wb_media_stop), onClick = onStop)
                MediaButton(MediaGlyph.PREVIOUS, stringResource(R.string.wb_media_previous), big = true, enabled = info.canPrevious, onClick = onPrevious)
                MediaButton(if (info.playing) MediaGlyph.PAUSE else MediaGlyph.PLAY, stringResource(R.string.wb_media_toggle), big = true, onClick = onToggle)
                MediaButton(MediaGlyph.NEXT, stringResource(R.string.wb_media_next), big = true, enabled = info.canNext, onClick = onNext)
                Box(Modifier.size(40.dp))
            }
            DialogButton(stringResource(R.string.wb_media_open_app), onOpenApp)
        }
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
    // «пн», «вт»… — дві літери: одна плутається (понеділок/п'ятниця, середа/субота).
    val dayFormat = remember { SimpleDateFormat("EE", Locale.getDefault()) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEachIndexed { index, day ->
            val today = index == week.lastIndex
            // Кожен ряд фіксованої висоти: час угорі, стовпчик знизу, назва дня завжди під ним — нічого не виштовхується.
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(BarMaxHeight + 18.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val (h, m) = UsageMath.hoursMinutes(day.totalMs)
                        Text(if (h > 0) "$h:${"%02d".format(m)}" else "$m", color = Oled.TextSecondary, fontSize = 10.sp, maxLines = 1)
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .width(18.dp)
                                .height(BarMaxHeight * (day.totalMs.toFloat() / max).coerceAtLeast(0.03f))
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (today) Oled.TextPrimary else Color(0xFF4A4A4A)),
                        )
                    }
                }
                Text(
                    dayFormat.format(Date(day.dayStart)).replaceFirstChar { it.uppercase() },
                    color = if (today) Oled.TextPrimary else Oled.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
