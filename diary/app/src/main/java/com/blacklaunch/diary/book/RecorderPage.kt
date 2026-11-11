package com.blacklaunch.diary.book

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.DiaryViewModel
import com.blacklaunch.diary.R
import com.blacklaunch.diary.audio.Player
import com.blacklaunch.diary.data.Day
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.ConfirmDialog
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.Tone
import com.blacklaunch.diary.ui.WindowHint
import com.blacklaunch.diary.ui.taperedPath
import com.blacklaunch.diary.ui.theme.Oled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val RecRed = Color(0xFFB0244F)

/** Що натиснуто на магнітофоні (клавіша «западає», поки триває дія). */
private enum class Deck { Idle, Play, Pause, Rewind, Forward, Record }

/**
 * Магнітофон дня — сторінка на весь щоденник (щось середнє між диктофоном і старим касетником): угорі віконце касети
 * (котушки крутяться, стрічка перемотується з лівої на праву — видно, скільки лишилось), під ним — решітка динаміка й
 * стрілочний «контроль запису» з лічильником, унизу — великі клавіші: запис, назад, вперед, пуск, пауза, стоп.
 * [editable] = false (день висох) — лише слухати: запис не натискається.
 */
@Composable
fun ColumnScope.RecorderPage(
    vm: DiaryViewModel,
    day: Day,
    editable: Boolean,
    recording: RecordingState?,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val glass = AppStyle.glass
    val player = remember(day.day) { Player() }
    DisposableEffect(day.day) { onDispose { player.stop() } }
    var deck by remember { mutableStateOf(Deck.Idle) }
    var loaded by remember(day.day, day.updated) { mutableStateOf<ByteArray?>(null) }
    var position by remember(day.day) { mutableIntStateOf(0) }
    var confirmReplace by remember { mutableStateOf(false) }
    val total = (if (player.duration > 0) player.duration.toLong() else day.audioMs).coerceAtLeast(1L)
    val isRec = recording != null
    val shown = if (isRec) Deck.Record else deck

    // Позиція й стан під час програвання.
    LaunchedEffect(deck) {
        while (deck == Deck.Play) {
            position = player.position
            delay(50)
        }
    }

    suspend fun ensureLoaded(): ByteArray? = loaded ?: vm.audio(day.day).also { loaded = it }

    fun play() {
        if (isRec || !day.audio) return
        scope.launch {
            if (deck == Deck.Pause && player.duration > 0) { player.resume(); deck = Deck.Play; return@launch }
            val b = ensureLoaded() ?: return@launch
            val from = position.takeIf { it in 1 until total.toInt() - 200 } ?: 0
            if (player.play(b, from) { deck = Deck.Idle; position = 0 }) deck = Deck.Play
        }
    }

    fun seekBy(ms: Int) {
        if (isRec || !day.audio) return
        scope.launch {
            if (player.duration <= 0) {
                val b = ensureLoaded() ?: return@launch
                // Підготувати, не граючи: запустити й одразу на паузу.
                if (!player.play(b, position) { deck = Deck.Idle; position = 0 }) return@launch
                player.pause()
            }
            position = (position + ms).coerceIn(0, player.duration.coerceAtLeast(1))
            player.seek(position)
        }
    }

    // Котушки: кут крутиться, поки грає, пише чи перемотується (перемотка — швидше).
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(shown) {
        val speed = when (shown) {
            Deck.Play, Deck.Record -> 1.6f
            Deck.Rewind -> -7f
            Deck.Forward -> 7f
            else -> 0f
        }
        if (speed == 0f) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) angle += speed * (t - last) / 1e9f
                last = t
            }
        }
    }

    // Скільки стрічки вже на правій котушці.
    val progress = when {
        isRec -> (recording!!.elapsed.toFloat() / com.blacklaunch.diary.ui.Limits.AUDIO_MS).coerceIn(0f, 1f)
        else -> (position.toFloat() / total).coerceIn(0f, 1f)
    }
    // Стрілка: під час запису — гучність голосу, під час програвання — обриси запису в цьому місці.
    val rawLevel = when {
        isRec -> recording!!.wave.lastOrNull() ?: 0f
        deck == Deck.Play && day.wave.isNotEmpty() -> day.wave[(progress * (day.wave.size - 1)).toInt().coerceIn(0, day.wave.size - 1)]
        else -> 0f
    }
    val level by animateFloatAsState(rawLevel, spring(dampingRatio = 0.5f, stiffness = 260f), label = "vu")

    val ink = if (glass) Oled.TextPrimary else SheetInk
    val faint = ink.copy(alpha = if (glass) 0.35f else 0.45f)

    BoxWithConstraints(Modifier.fillMaxWidth().padding(end = 4.dp)) {
        val w = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ——— Касета ———
            Canvas(Modifier.fillMaxWidth().height(w * 0.62f)) {
                drawCassette(glass, ink, faint, angle, progress, dateLabel = null)
            }
            // ——— Динамік і «контроль запису» ———
            Row(Modifier.fillMaxWidth().height(w * 0.74f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(Modifier.weight(1f).fillMaxHeight()) { drawGrille(glass, ink, faint) }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.fillMaxWidth().weight(1f)) { drawMeter(glass, ink, faint, level, isRec) }
                    val now = if (isRec) recording!!.elapsed else position.toLong()
                    Text(
                        if (isRec || !day.audio) clock(now) else "${clock(now)} / ${clock(day.audioMs)}",
                        color = if (isRec) RecRed else Oled.TextPrimary,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
            // ——— Клавіші ———
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val canRec = editable && !isRec
                DeckKey(Deck.Record, shown == Deck.Record, enabled = canRec || isRec, desc = stringResource(R.string.record), glass = glass) {
                    if (isRec) return@DeckKey
                    player.stop(); deck = Deck.Idle; position = 0
                    if (day.audio) confirmReplace = true else { haptics.longPress(); onRecord() }
                }
                DeckKey(Deck.Rewind, shown == Deck.Rewind, enabled = day.audio && !isRec, desc = stringResource(R.string.recorder_rewind), glass = glass, hold = { down ->
                    if (down) { deck = Deck.Rewind; scope.launch { while (deck == Deck.Rewind) { seekBy(-1500); delay(150) } } } else if (deck == Deck.Rewind) deck = if (player.playing) Deck.Play else Deck.Idle
                }) { seekBy(-5000) }
                DeckKey(Deck.Forward, shown == Deck.Forward, enabled = day.audio && !isRec, desc = stringResource(R.string.recorder_forward), glass = glass, hold = { down ->
                    if (down) { deck = Deck.Forward; scope.launch { while (deck == Deck.Forward) { seekBy(1500); delay(150) } } } else if (deck == Deck.Forward) deck = if (player.playing) Deck.Play else Deck.Idle
                }) { seekBy(5000) }
                DeckKey(Deck.Play, shown == Deck.Play, enabled = day.audio && !isRec, desc = stringResource(R.string.play), glass = glass) { play() }
                DeckKey(Deck.Pause, shown == Deck.Pause, enabled = day.audio && !isRec, desc = stringResource(R.string.recorder_pause), glass = glass) {
                    when (deck) {
                        Deck.Play -> { player.pause(); deck = Deck.Pause }
                        Deck.Pause -> play()
                        else -> Unit
                    }
                }
                DeckKey(Deck.Idle, false, enabled = isRec || deck != Deck.Idle || position > 0, desc = stringResource(R.string.record_stop), glass = glass) {
                    if (isRec) onStopRecording() else { player.stop(); deck = Deck.Idle; position = 0 }
                }
            }
            WindowHint(
                stringResource(
                    when {
                        isRec -> R.string.recorder_hint_recording
                        !editable -> R.string.recorder_hint_dry
                        !day.audio -> R.string.recorder_hint_empty
                        else -> R.string.recorder_hint
                    },
                ),
            )
        }
    }
    if (confirmReplace) {
        ConfirmDialog(
            title = stringResource(R.string.recorder_replace_title),
            text = stringResource(R.string.recorder_replace_text),
            confirm = stringResource(R.string.record),
            tone = Tone.Danger,
            onDismiss = { confirmReplace = false },
        ) { confirmReplace = false; haptics.longPress(); onRecord() }
    }
}

/**
 * Клавіша магнітофона: велика, «западає», поки натиснута чи поки триває її дія ([latched]). [hold] — для перемотки:
 * поки тримаєш — мотає (true — натиснули, false — відпустили); короткий дотик — [onClick].
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.DeckKey(
    kind: Deck,
    latched: Boolean,
    enabled: Boolean,
    desc: String,
    glass: Boolean,
    hold: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val down = pressed || latched
    val sink by animateFloatAsState(if (down) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "key")
    val ink = if (glass) Oled.TextPrimary else SheetInk
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics { contentDescription = desc }
            .pointerInput(enabled, hold) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    haptics.tap()
                    var held = false
                    val job = if (hold != null) scope.launch { delay(350); held = true; hold(true) } else null
                    val up = waitForUpOrCancellation()
                    job?.cancel()
                    pressed = false
                    if (held) hold?.invoke(false) else if (up != null) onClick()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val depth = 6.dp.toPx()
            val top = depth * sink
            val r = CornerRadius(7.dp.toPx())
            val faceH = size.height - depth
            // Тінь / бічна грань клавіші — видно, поки вона не натиснута.
            drawRoundRect(
                if (glass) Color(0xFF050506) else Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, depth),
                size = Size(size.width, faceH),
                cornerRadius = r,
            )
            val faceTop = Offset(0f, top)
            val faceSize = Size(size.width, faceH)
            if (glass) {
                drawRoundRect(
                    Brush.verticalGradient(0f to Color(0xFF2A2B2F), 1f to Color(0xFF141518), startY = top, endY = top + faceH),
                    topLeft = faceTop, size = faceSize, cornerRadius = r,
                )
                drawRoundRect(Color.White.copy(alpha = if (down) 0.10f else 0.22f), topLeft = faceTop, size = faceSize, cornerRadius = r, style = Stroke(1.dp.toPx()))
                drawLine(Color.White.copy(alpha = if (down) 0.06f else 0.18f), Offset(6.dp.toPx(), top + 2.dp.toPx()), Offset(size.width - 6.dp.toPx(), top + 2.dp.toPx()), 1.dp.toPx())
            } else {
                drawRoundRect(Color(0xFF15130F), topLeft = faceTop, size = faceSize, cornerRadius = r)
                val pts = listOf(
                    Offset(r.x, top), Offset(size.width - r.x, top + 0.6f), Offset(size.width, top + r.y), Offset(size.width - 0.6f, top + faceH - r.y),
                    Offset(size.width - r.x, top + faceH), Offset(r.x, top + faceH - 0.6f), Offset(0f, top + faceH - r.y), Offset(0.6f, top + r.y), Offset(r.x, top),
                )
                drawPath(taperedPath(pts, 1.8.dp.toPx()), SheetInk.copy(alpha = if (down) 0.95f else 0.75f))
            }
            if (down) drawRoundRect(Color.Black.copy(alpha = 0.25f), topLeft = faceTop, size = faceSize, cornerRadius = r)
            // Знак на клавіші.
            val c = Offset(size.width / 2, top + faceH / 2)
            val s = minOf(size.width, faceH) * 0.18f
            val a = if (enabled) 0.9f else 0.3f
            drawKeySymbol(kind, c, s, if (kind == Deck.Record) RecRed.copy(alpha = a) else ink.copy(alpha = a))
        }
    }
}

private fun DrawScope.drawKeySymbol(kind: Deck, c: Offset, s: Float, color: Color) {
    fun tri(cx: Float, dir: Float) {
        drawPath(Path().apply { moveTo(cx - s * 0.6f * dir, c.y - s * 0.75f); lineTo(cx + s * 0.6f * dir, c.y); lineTo(cx - s * 0.6f * dir, c.y + s * 0.75f); close() }, color)
    }
    when (kind) {
        Deck.Record -> drawCircle(color, s * 0.8f, c)
        Deck.Rewind -> { tri(c.x - s * 0.55f, -1f); tri(c.x + s * 0.55f, -1f) }
        Deck.Forward -> { tri(c.x - s * 0.55f, 1f); tri(c.x + s * 0.55f, 1f) }
        Deck.Play -> tri(c.x + s * 0.1f, 1f)
        Deck.Pause -> {
            drawRect(color, Offset(c.x - s * 0.65f, c.y - s * 0.75f), Size(s * 0.45f, s * 1.5f))
            drawRect(color, Offset(c.x + s * 0.2f, c.y - s * 0.75f), Size(s * 0.45f, s * 1.5f))
        }
        Deck.Idle -> drawRect(color, Offset(c.x - s * 0.7f, c.y - s * 0.7f), Size(s * 1.4f, s * 1.4f))
    }
}

/** Рамка в стилі теми: «Скло» — тонка скляна грань із відблиском, «Аркуш» — олівцем. */
private fun DrawScope.frame(topLeft: Offset, sz: Size, radius: Float, glass: Boolean, ink: Color, alpha: Float = 1f, seed: Int = 0) {
    if (glass) {
        drawRoundRect(Color.White.copy(alpha = 0.20f * alpha), topLeft, sz, CornerRadius(radius), style = Stroke(1.dp.toPx()))
        drawLine(Color.White.copy(alpha = 0.10f * alpha), Offset(topLeft.x + radius, topLeft.y + 1.5.dp.toPx()), Offset(topLeft.x + sz.width - radius, topLeft.y + 1.5.dp.toPx()), 1.dp.toPx())
    } else {
        val w = 0.5f + (seed % 3) * 0.2f
        val l = topLeft.x; val t = topLeft.y; val r = l + sz.width; val b = t + sz.height
        val pts = listOf(
            Offset(l + radius, t), Offset(r - radius, t + w), Offset(r, t + radius), Offset(r - w, b - radius),
            Offset(r - radius, b), Offset(l + radius, b - w), Offset(l, b - radius), Offset(l + w, t + radius), Offset(l + radius, t),
        )
        drawPath(taperedPath(pts, 1.6.dp.toPx()), ink.copy(alpha = 0.8f * alpha))
    }
}

/**
 * Касета у віконці: корпус, наклейка, віконце з двома котушками (на лівій — ще не прослухане, на правій — уже), стрічка
 * внизу через ролики. [angle] — оберт котушок (радіани), [progress] — скільки стрічки вже на правій.
 */
private fun DrawScope.drawCassette(glass: Boolean, ink: Color, faint: Color, angle: Float, progress: Float, dateLabel: String?) {
    val w = size.width
    val h = size.height
    val bodyR = 10.dp.toPx()
    if (glass) drawRoundRect(Brush.verticalGradient(0f to Color(0xFF1C1D21), 1f to Color(0xFF101114)), cornerRadius = CornerRadius(bodyR))
    frame(Offset.Zero, size, bodyR, glass, ink, seed = 1)
    // Наклейка — смуга вгорі.
    val labelTop = h * 0.08f
    val labelH = h * 0.62f
    frame(Offset(w * 0.06f, labelTop), Size(w * 0.88f, labelH), 6.dp.toPx(), glass, ink, alpha = 0.6f, seed = 2)
    for (i in 1..2) {
        val y = labelTop + labelH * (0.12f + i * 0.07f)
        drawLine(faint, Offset(w * 0.12f, y), Offset(w * 0.88f, y), 0.8.dp.toPx())
    }
    // Віконце з котушками.
    val winL = w * 0.18f
    val winR = w * 0.82f
    val cy = labelTop + labelH * 0.62f
    val hubR = h * 0.075f
    val packMax = h * 0.22f
    val leftC = Offset(w * 0.32f, cy)
    val rightC = Offset(w * 0.68f, cy)
    val leftR = hubR + (packMax - hubR) * sqrt(1f - progress)
    val rightR = hubR + (packMax - hubR) * sqrt(progress)
    // Віконце (прозоре, з відблиском): стрічку на котушках видно лише крізь нього, як у справжньої касети.
    val winTop = cy - packMax * 0.75f
    val winH = packMax * 1.5f
    val tape = if (glass) Color(0xFF26231F) else SheetInk.copy(alpha = 0.16f)
    clipRect(winL, winTop, winR, winTop + winH) {
        drawCircle(tape, leftR, leftC)
        drawCircle(tape, rightR, rightC)
        if (!glass) {
            drawCircle(SheetInk.copy(alpha = 0.6f), leftR, leftC, style = Stroke(1.2.dp.toPx()))
            drawCircle(SheetInk.copy(alpha = 0.6f), rightR, rightC, style = Stroke(1.2.dp.toPx()))
        }
    }
    frame(Offset(winL, winTop), Size(winR - winL, winH), 5.dp.toPx(), glass, ink, alpha = 0.7f, seed = 3)
    if (glass) drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), start = Offset(winL, winTop), end = Offset(winR, winTop + winH)), Offset(winL, winTop), Size(winR - winL, winH), CornerRadius(5.dp.toPx()))
    // Маточини зі спицями — крутяться.
    fun hub(c: Offset, a: Float) {
        drawCircle(if (glass) Color(0xFF0C0C0E) else Color(0xFF15130F), hubR, c)
        drawCircle(ink.copy(alpha = 0.8f), hubR, c, style = Stroke(1.4.dp.toPx()))
        rotate(a * 180f / PI.toFloat(), c) {
            for (k in 0 until 6) {
                val t = k * PI.toFloat() / 3f
                drawLine(ink.copy(alpha = 0.85f), c + Offset(cos(t), sin(t)) * (hubR * 0.35f), c + Offset(cos(t), sin(t)) * (hubR * 0.9f), 1.6.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        drawCircle(ink.copy(alpha = 0.6f), hubR * 0.25f, c, style = Stroke(1.dp.toPx()))
    }
    hub(leftC, angle)
    hub(rightC, angle)
    // Стрічка внизу: від котушок до роликів і між ними — повз голівку.
    val rollY = h * 0.86f
    val rollL = Offset(w * 0.14f, rollY)
    val rollR = Offset(w * 0.86f, rollY)
    val tapeLine = if (glass) Color(0xFF6B6157) else SheetInk.copy(alpha = 0.55f)
    drawLine(tapeLine, Offset(leftC.x - leftR, leftC.y), rollL, 1.4.dp.toPx())
    drawLine(tapeLine, rollL, rollR, 1.4.dp.toPx())
    drawLine(tapeLine, Offset(rightC.x + rightR, rightC.y), rollR, 1.4.dp.toPx())
    drawCircle(ink.copy(alpha = 0.7f), 3.dp.toPx(), rollL, style = Stroke(1.2.dp.toPx()))
    drawCircle(ink.copy(alpha = 0.7f), 3.dp.toPx(), rollR, style = Stroke(1.2.dp.toPx()))
    // Голівка — посередині під стрічкою.
    frame(Offset(w * 0.44f, rollY + 2.dp.toPx()), Size(w * 0.12f, h * 0.08f), 2.dp.toPx(), glass, ink, alpha = 0.7f, seed = 4)
}

/** Решітка динаміка — квадратні комірки рядами (як на старих касетниках). */
private fun DrawScope.drawGrille(glass: Boolean, ink: Color, faint: Color) {
    val r = 8.dp.toPx()
    if (glass) drawRoundRect(Brush.verticalGradient(0f to Color(0xFF17181B), 1f to Color(0xFF0E0F11)), cornerRadius = CornerRadius(r))
    frame(Offset.Zero, size, r, glass, ink, seed = 5)
    val pad = 8.dp.toPx()
    val cols = 6
    val gap = 3.dp.toPx()
    val cell = (size.width - pad * 2 - gap * (cols - 1)) / cols
    val rows = ((size.height - pad * 2 + gap) / (cell * 0.8f + gap)).toInt().coerceAtLeast(1)
    val ch = (size.height - pad * 2 - gap * (rows - 1)) / rows
    for (y in 0 until rows) for (x in 0 until cols) {
        val tl = Offset(pad + x * (cell + gap), pad + y * (ch + gap))
        drawRoundRect(if (glass) Color.Black.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.45f), tl, Size(cell, ch), CornerRadius(1.5.dp.toPx()))
        drawRoundRect(faint, tl, Size(cell, ch), CornerRadius(1.5.dp.toPx()), style = Stroke(0.8.dp.toPx()))
    }
}

/** Стрілочний «контроль запису»: дуга з поділками, червона зона праворуч, стрілка від гучності [level] (0…1). */
private fun DrawScope.drawMeter(glass: Boolean, ink: Color, faint: Color, level: Float, recording: Boolean) {
    val r = 8.dp.toPx()
    if (glass) drawRoundRect(Brush.verticalGradient(0f to Color(0xFF1E1F22), 1f to Color(0xFF121315)), cornerRadius = CornerRadius(r))
    frame(Offset.Zero, size, r, glass, ink, seed = 6)
    val pivot = Offset(size.width / 2, size.height * 0.92f)
    val rad = minOf(size.width * 0.42f, size.height * 0.78f)
    val from = -140f
    val sweep = 100f
    fun at(deg: Float, rr: Float) = pivot + Offset(cos(deg * PI.toFloat() / 180f), sin(deg * PI.toFloat() / 180f)) * rr
    // Поділки; останні — червоні.
    for (i in 0..10) {
        val d = from + sweep * i / 10f
        val red = i >= 8
        drawLine(if (red) RecRed.copy(alpha = 0.9f) else ink.copy(alpha = 0.7f), at(d, rad * 0.86f), at(d, rad * (if (i % 5 == 0) 1f else 0.95f)), (if (i % 5 == 0) 1.6f else 1.1f).dp.toPx())
    }
    drawArc(faint, from, sweep * 0.8f, false, topLeft = pivot - Offset(rad * 0.8f, rad * 0.8f), size = Size(rad * 1.6f, rad * 1.6f), style = Stroke(1.dp.toPx()))
    drawArc(RecRed.copy(alpha = 0.7f), from + sweep * 0.8f, sweep * 0.2f, false, topLeft = pivot - Offset(rad * 0.8f, rad * 0.8f), size = Size(rad * 1.6f, rad * 1.6f), style = Stroke(2.dp.toPx()))
    // Стрілка.
    val d = from + sweep * level.coerceIn(0f, 1f)
    drawLine(ink, pivot, at(d, rad * 0.98f), 1.6.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(ink, 3.dp.toPx(), pivot)
    // Вогник запису.
    if (recording) drawCircle(RecRed, 3.5.dp.toPx(), Offset(size.width - 12.dp.toPx(), 12.dp.toPx()))
}
