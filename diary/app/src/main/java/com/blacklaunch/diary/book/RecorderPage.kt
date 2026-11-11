package com.blacklaunch.diary.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blacklaunch.diary.DiaryViewModel
import com.blacklaunch.diary.R
import com.blacklaunch.diary.audio.Player
import com.blacklaunch.diary.data.Day
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.Limits
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.taperedPath
import com.blacklaunch.diary.ui.theme.Oled
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val RecRed = Color(0xFFB0244F)
private val TapeBrown = Color(0xFF3B2E24)

/** Що натиснуто на магнітофоні (клавіша «западає», поки триває її дія). */
private enum class Deck { Idle, Play, Pause, Rewind, Forward, Record }

/** Наскільки товстішає повна котушка проти порожньої маточини. */
private const val PACK_RATIO = 2.7f

/** Швидкість перемотки — у скільки разів швидше за звичайну. */
private const val WIND_SPEED = 24f

/**
 * Магнітофон дня — сторінка на весь щоденник: угорі в шухлядці касета (сторони A і B — торкнись касети, щоб перевернути),
 * під нею — решітка динаміка, праворуч стрілочний «контроль запису» й лічильник стрічки; унизу — великі клавіші до самого
 * краю сторінки.
 *
 * Касета — справжня C90: на стороні 45 хвилин стрічки. Що записано — намотується на праву котушку рівно стільки, скільки
 * записано; решта лежить на лівій. Запис дописується в кінець (позначки дописів — на лічильнику). Пуск з кінця — спершу
 * перемотує на початок. Перемотати можна лише записане.
 */
@Composable
fun RecorderPage(
    vm: DiaryViewModel,
    day: Day,
    title: String,
    subtitle: String,
    seed: Int,
    editable: Boolean,
    recording: RecordingState?,
    onRecord: (side: Int) -> Unit,
    onStopRecording: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val glass = AppStyle.glass
    var side by remember(day.day) { mutableIntStateOf(recording?.side ?: 0) }
    val has = if (side == 0) day.audio else day.audioB
    // Тривалість — лише якщо запис на цій стороні є (після видалення стара цифра лишається для «Повернути»).
    val dur = if (side == 0) (if (day.audio) day.audioMs else 0L) else (if (day.audioB) day.audioMsB else 0L)
    val takes = if (side == 0) day.takes else day.takesB
    val wave = if (side == 0) day.wave else day.waveB
    val isRec = recording != null && recording.side == side
    val player = remember(day.day) { Player() }
    DisposableEffect(day.day) { onDispose { player.stop() } }
    var deck by remember(day.day) { mutableStateOf(Deck.Idle) }
    // Де стоїть стрічка (мс від початку сторони). Касету залишили там, де записали, — у кінці записаного.
    var head by remember(day.day, side) { mutableFloatStateOf(dur.toFloat()) }
    var loaded by remember(day.day, side, dur) { mutableStateOf<ByteArray?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val flip = remember(day.day) { Animatable(0f) }

    // Дописали — стрічка стоїть у кінці нового запису.
    LaunchedEffect(dur) { if (deck == Deck.Idle || deck == Deck.Record) head = dur.toFloat() }
    if (isRec) head = recording!!.base + recording.elapsed.toFloat()

    suspend fun bytes(): ByteArray? = loaded ?: vm.audio(day.day, side).also { loaded = it }

    /** Перемотати стрічку до [target] (з крутінням котушок), як справжня перемотка. */
    suspend fun windTo(target: Float) {
        val from = head
        if (abs(target - from) < 50f) { head = target; return }
        deck = if (target < from) Deck.Rewind else Deck.Forward
        val ms = (abs(target - from) / WIND_SPEED).toInt().coerceIn(350, 2600)
        val a = Animatable(from)
        a.animateTo(target, tween(ms, easing = FastOutSlowInEasing)) { head = value }
        deck = Deck.Idle
    }

    fun cancelJob() { job?.cancel(); job = null }

    fun play() {
        if (!has || isRec) return
        if (deck == Deck.Pause && player.duration > 0) { player.resume(); deck = Deck.Play; return }
        cancelJob()
        job = scope.launch {
            // З кінця — спершу перемотати на початок.
            if (head >= dur - 300) windTo(0f)
            val b = bytes() ?: return@launch
            val ok = player.play(b, head.toInt().coerceAtLeast(0)) {
                deck = Deck.Idle
                head = dur.toFloat()
            }
            if (!ok) return@launch
            deck = Deck.Play
            while (deck == Deck.Play || deck == Deck.Pause) {
                if (deck == Deck.Play) head = player.position.toFloat().coerceAtMost(dur.toFloat())
                delay(40)
            }
        }
    }

    fun stopAll() {
        cancelJob()
        player.stop()
        deck = Deck.Idle
    }

    fun nudge(ms: Float) {
        if (!has || isRec) return
        head = (head + ms).coerceIn(0f, dur.toFloat())
        if (player.duration > 0) player.seek(head.toInt())
    }

    // Котушки: кожна крутиться від того, скільки стрічки пройшло, і тим швидше, чим вона тонша (як справжні).
    var angleL by remember { mutableFloatStateOf(0f) }
    var angleR by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = head
        while (true) {
            withFrameNanos { }
            val d = head - last
            last = head
            if (d != 0f) {
                val f = (head / Limits.SIDE_MS).coerceIn(0f, 1f)
                val rl = sqrt(1f + (1f - f) * (PACK_RATIO * PACK_RATIO - 1f))
                val rr = sqrt(1f + f * (PACK_RATIO * PACK_RATIO - 1f))
                // Пів оберту на секунду, поки маточина порожня; товста котушка — повільніше.
                angleL += d * (PI.toFloat() / 1000f) / rl
                angleR += d * (PI.toFloat() / 1000f) / rr
            }
        }
    }

    val rawLevel = when {
        isRec -> recording!!.level
        deck == Deck.Play && wave.isNotEmpty() && dur > 0 -> wave[((head / dur) * (wave.size - 1)).toInt().coerceIn(0, wave.size - 1)]
        else -> 0f
    }
    val level by animateFloatAsState(rawLevel, spring(dampingRatio = 0.45f, stiffness = 240f), label = "vu")
    val shown = if (isRec) Deck.Record else deck
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val faint = ink.copy(alpha = if (glass) 0.30f else 0.42f)

    val keys: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            DeckKey(Deck.Record, shown == Deck.Record, enabled = editable && recording == null, desc = stringResource(R.string.record), glass = glass) {
                if (dur >= Limits.SIDE_MS - 1000) { vm.show(R.string.recorder_side_full); return@DeckKey }
                stopAll()
                job = scope.launch {
                    // Пишемо в кінець записаного: стрічку спершу домотати туди.
                    if (head < dur) windTo(dur.toFloat())
                    haptics.longPress()
                    onRecord(side)
                }
            }
            DeckKey(Deck.Rewind, shown == Deck.Rewind, enabled = has && !isRec, desc = stringResource(R.string.recorder_rewind), glass = glass, hold = { down ->
                if (down) {
                    if (deck == Deck.Play) player.pause()
                    deck = Deck.Rewind
                    job = scope.launch { while (deck == Deck.Rewind && head > 0f) { head = (head - 40f * WIND_SPEED).coerceAtLeast(0f); delay(40) } }
                } else if (deck == Deck.Rewind) {
                    if (player.duration > 0) { player.seek(head.toInt()); player.resume(); deck = Deck.Play } else deck = Deck.Idle
                }
            }) { nudge(-5000f) }
            DeckKey(Deck.Forward, shown == Deck.Forward, enabled = has && !isRec, desc = stringResource(R.string.recorder_forward), glass = glass, hold = { down ->
                if (down) {
                    if (deck == Deck.Play) player.pause()
                    deck = Deck.Forward
                    job = scope.launch { while (deck == Deck.Forward && head < dur) { head = (head + 40f * WIND_SPEED).coerceAtMost(dur.toFloat()); delay(40) } }
                } else if (deck == Deck.Forward) {
                    if (player.duration > 0) { player.seek(head.toInt()); player.resume(); deck = Deck.Play } else deck = Deck.Idle
                }
            }) { nudge(5000f) }
            DeckKey(Deck.Play, shown == Deck.Play, enabled = has && !isRec, desc = stringResource(R.string.play), glass = glass) { play() }
            DeckKey(Deck.Pause, shown == Deck.Pause, enabled = has && !isRec, desc = stringResource(R.string.recorder_pause), glass = glass) {
                when (deck) {
                    Deck.Play -> { player.pause(); deck = Deck.Pause }
                    Deck.Pause -> { player.resume(); deck = Deck.Play }
                    else -> Unit
                }
            }
            DeckKey(Deck.Idle, false, enabled = true, desc = stringResource(R.string.record_stop), glass = glass) {
                if (isRec) onStopRecording() else stopAll()
            }
        }
    }

    PaperPage(title = title, subtitle = subtitle, seed = seed, edgeHeight = 118.dp, edge = keys) {
        Column(Modifier.fillMaxSize().padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ——— Шухлядка з касетою (торкнись — перевернути на іншу сторону) ———
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .weight(1.05f)
                    .semantics { contentDescription = if (side == 0) "A" else "B" }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = deck == Deck.Idle && recording == null && !flip.isRunning) {
                        haptics.tap()
                        stopAll()
                        scope.launch {
                            flip.animateTo(90f, tween(220, easing = LinearEasing))
                            side = 1 - side
                            flip.snapTo(-90f)
                            flip.animateTo(0f, tween(220, easing = LinearEasing))
                        }
                    },
            ) {
                drawDeckWell(glass, ink)
                val c = cassetteRect(size)
                val sx = cos(flip.value * PI.toFloat() / 180f)
                withScaleX(sx, c.center) {
                    drawCassette(c, glass, ink, faint, side, (head / Limits.SIDE_MS).coerceIn(0f, 1f), angleL, angleR, moving = shown != Deck.Idle && shown != Deck.Pause, labelText = subtitle)
                }
                // Скло дверцят — легкий відблиск поверх.
                if (glass) drawRoundRect(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.White.copy(alpha = 0.03f)), start = Offset.Zero, end = Offset(size.width, size.height)),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }
            // ——— Динамік і два прилади праворуч ———
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(Modifier.weight(1f).fillMaxHeight()) { drawGrille(glass, ink, faint) }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.fillMaxWidth().weight(1f)) { drawMeter(glass, ink, faint, level, isRec) }
                    Canvas(Modifier.fillMaxWidth().weight(1f)) { drawCounter(glass, ink, faint, head.toLong(), dur, takes, isRec) }
                }
            }
        }
    }
}

/** Касета, що перевертається, — стискається по ширині до ребра й розкривається іншою стороною. */
private inline fun DrawScope.withScaleX(sx: Float, pivot: Offset, block: DrawScope.() -> Unit) {
    val k = abs(sx).coerceIn(0.02f, 1f)
    drawContext.canvas.save()
    drawContext.canvas.translate(pivot.x, pivot.y)
    drawContext.canvas.scale(k, 1f)
    drawContext.canvas.translate(-pivot.x, -pivot.y)
    block()
    drawContext.canvas.restore()
}

/** Касета в шухлядці: пропорції справжньої (≈ 100 × 63 мм), посередині. */
private fun cassetteRect(size: Size): Rect {
    val pad = size.minDimension * 0.07f
    val maxW = size.width - pad * 2
    val maxH = size.height - pad * 2
    val w = minOf(maxW, maxH * 1.58f)
    val h = w / 1.58f
    return Rect(Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h))
}

/**
 * Клавіша магнітофона — велика, донизу до самого краю сторінки. «Западає», поки натиснута чи поки триває її дія.
 * [hold] — для перемотки: тримаєш — мотає (true — натиснули, false — відпустили); короткий дотик — [onClick].
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
                    val j = if (hold != null) scope.launch { delay(320); held = true; hold(true) } else null
                    val up = waitForUpOrCancellation()
                    j?.cancel()
                    pressed = false
                    if (held) hold?.invoke(false) else if (up != null) onClick()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val travel = 9.dp.toPx()
            val top = travel * sink
            val r = CornerRadius(6.dp.toPx())
            // Щілина над клавішею — темна (видно, коли клавіша запала).
            drawRect(Color.Black.copy(alpha = 0.7f), size = Size(size.width, travel + 2f))
            val face = Size(size.width, size.height - top + r.y)
            val tl = Offset(0f, top)
            val edgeY = top + size.height * 0.42f
            if (glass) {
                drawRoundRect(
                    Brush.verticalGradient(0f to Color(0xFF34353A), 0.18f to Color(0xFF26272B), 1f to Color(0xFF111215), startY = top, endY = size.height),
                    topLeft = tl, size = face, cornerRadius = r,
                )
                drawLine(Color.White.copy(alpha = if (down) 0.08f else 0.24f), Offset(r.x, top + 1.dp.toPx()), Offset(size.width - r.x, top + 1.dp.toPx()), 1.dp.toPx())
                drawRoundRect(Color.White.copy(alpha = 0.10f), topLeft = tl, size = face, cornerRadius = r, style = Stroke(1.dp.toPx()))
                // Ребро, де верх клавіші переходить у її передню стінку.
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(2.dp.toPx(), edgeY), Offset(size.width - 2.dp.toPx(), edgeY), 1.dp.toPx())
            } else {
                drawRoundRect(Color(0xFF13110E), topLeft = tl, size = face, cornerRadius = r)
                val b = size.height + r.y
                val pts = listOf(Offset(0.6f, b), Offset(0f, top + r.y), Offset(r.x, top), Offset(size.width - r.x, top + 0.6f), Offset(size.width, top + r.y), Offset(size.width - 0.6f, b))
                drawPath(taperedPath(pts, 1.8.dp.toPx()), SheetInk.copy(alpha = if (down) 0.95f else 0.8f))
                drawPath(taperedPath(listOf(Offset(3.dp.toPx(), edgeY), Offset(size.width / 2, edgeY + 0.8f), Offset(size.width - 3.dp.toPx(), edgeY)), 1.2.dp.toPx()), SheetInk.copy(alpha = 0.45f))
            }
            if (down) drawRect(Color.Black.copy(alpha = 0.22f), topLeft = tl, size = face)
            val c = Offset(size.width / 2, top + size.height * 0.22f)
            val s = minOf(size.width * 0.16f, 9.dp.toPx())
            val a = if (enabled) 0.9f else 0.28f
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

/** Шухлядка (касетний відсік): заглиблення з дверцятами. */
private fun DrawScope.drawDeckWell(glass: Boolean, ink: Color) {
    val r = 12.dp.toPx()
    drawRoundRect(if (glass) Color(0xFF0B0B0D) else Color.Black.copy(alpha = 0.5f), cornerRadius = CornerRadius(r))
    // Внутрішня тінь — шухлядка глибока.
    drawRoundRect(
        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.6f), 0.15f to Color.Transparent, 0.85f to Color.Transparent, 1f to Color.White.copy(alpha = if (glass) 0.04f else 0f)),
        cornerRadius = CornerRadius(r),
    )
    frame(Offset.Zero, size, r, glass, ink, seed = 7)
    val inset = 5.dp.toPx()
    frame(Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), r - inset / 2, glass, ink, alpha = 0.45f, seed = 8)
}

/**
 * Касета: корпус із гвинтиками, наклейка з літерою сторони, віконце (крізь нього видно намотану стрічку), отвори для
 * маточин, а знизу — виступ із прорізами, де стрічку видно лише в отворах (голівка, ролики).
 * [progress] — скільки стрічки вже на правій котушці (від усієї сторони).
 */
private fun DrawScope.drawCassette(rc: Rect, glass: Boolean, ink: Color, faint: Color, side: Int, progress: Float, angleL: Float, angleR: Float, moving: Boolean, labelText: String) {
    val w = rc.width
    val h = rc.height
    fun p(x: Float, y: Float) = Offset(rc.left + x * w, rc.top + y * h)
    val shellR = w * 0.035f
    val body = if (glass) Color(0xFF1B1C20) else Color(0xFF15130F)
    drawRoundRect(body, rc.topLeft, rc.size, CornerRadius(shellR))
    if (glass) drawRoundRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 1f to Color.Transparent, startY = rc.top, endY = rc.bottom), rc.topLeft, rc.size, CornerRadius(shellR))
    frame(rc.topLeft, rc.size, shellR, glass, ink, seed = 11)
    // Гвинтики в кутах і посередині знизу.
    listOf(p(0.04f, 0.06f), p(0.96f, 0.06f), p(0.04f, 0.94f), p(0.96f, 0.94f), p(0.5f, 0.9f)).forEach {
        drawCircle(faint, w * 0.014f, it, style = Stroke(1.dp.toPx()))
        drawLine(faint, it - Offset(w * 0.009f, 0f), it + Offset(w * 0.009f, 0f), 1.dp.toPx())
    }
    // Наклейка.
    val label = Rect(p(0.07f, 0.08f), p(0.93f, 0.68f))
    drawRoundRect(if (glass) Color(0xFF26272B) else Color(0xFF1D1A15), label.topLeft, label.size, CornerRadius(w * 0.02f))
    frame(label.topLeft, label.size, w * 0.02f, glass, ink, alpha = 0.55f, seed = 12)
    // Лінійки для підпису.
    for (i in 0..1) {
        val y = label.top + label.height * (0.16f + i * 0.11f)
        drawLine(faint, Offset(label.left + label.width * 0.2f, y), Offset(label.right - label.width * 0.05f, y), 0.8.dp.toPx())
    }
    // Літера сторони — велика, ліворуч на наклейці.
    drawSideLetter(side, Offset(label.left + label.width * 0.09f, label.top + label.height * 0.2f), label.height * 0.13f, ink)
    // Підпис на наклейці — дата дня (як від руки на справжній касеті).
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = label.height * 0.13f
        color = ink.copy(alpha = 0.85f).argb()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, if (glass) android.graphics.Typeface.NORMAL else android.graphics.Typeface.ITALIC)
    }
    drawContext.canvas.nativeCanvas.drawText(labelText, label.left + label.width * 0.21f, label.top + label.height * 0.16f - 3.dp.toPx(), paint)
    // Віконце між маточинами: крізь нього видно стрічку на котушках.
    // Як у справжньої касети: маточини на ~42 мм одна від одної (на 100 мм ширини), віконце між ними.
    val win = Rect(p(0.355f, 0.38f), p(0.645f, 0.64f))
    val hubL = p(0.29f, 0.51f)
    val hubR = p(0.71f, 0.51f)
    val hubRad = w * 0.05f
    val rl = hubRad * sqrt(1f + (1f - progress) * (PACK_RATIO * PACK_RATIO - 1f))
    val rr = hubRad * sqrt(1f + progress * (PACK_RATIO * PACK_RATIO - 1f))
    drawRoundRect(Color.Black.copy(alpha = 0.85f), win.topLeft, win.size, CornerRadius(w * 0.012f))
    clipRect(win.left, win.top, win.right, win.bottom) {
        val tape = if (glass) TapeBrown else SheetInk.copy(alpha = 0.18f)
        drawCircle(tape, rl, hubL)
        drawCircle(tape, rr, hubR)
        if (!glass) {
            drawCircle(SheetInk.copy(alpha = 0.55f), rl, hubL, style = Stroke(1.dp.toPx()))
            drawCircle(SheetInk.copy(alpha = 0.55f), rr, hubR, style = Stroke(1.dp.toPx()))
        }
        // Межа між шарами — ледь видні кільця.
        drawCircle(Color.Black.copy(alpha = 0.25f), (rl + hubRad) / 2, hubL, style = Stroke(0.6.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.25f), (rr + hubRad) / 2, hubR, style = Stroke(0.6.dp.toPx()))
        // Прозорий пластик віконця з відблиском.
        drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), start = win.topLeft, end = win.bottomRight), win.topLeft, win.size)
    }
    frame(win.topLeft, win.size, w * 0.012f, glass, ink, alpha = 0.55f, seed = 13)
    // Отвори для маточин і самі маточини з зубцями — крутяться.
    fun hub(c: Offset, a: Float) {
        drawCircle(Color.Black.copy(alpha = 0.9f), hubRad * 1.25f, c)
        drawCircle(ink.copy(alpha = 0.5f), hubRad * 1.25f, c, style = Stroke(1.dp.toPx()))
        drawCircle(if (glass) Color(0xFFE9E6DF).copy(alpha = 0.85f) else SheetInk.copy(alpha = 0.8f), hubRad, c, style = Stroke(1.6.dp.toPx()))
        rotate(a * 180f / PI.toFloat(), c) {
            for (k in 0 until 6) {
                val t = k * PI.toFloat() / 3f
                val d = Offset(cos(t), sin(t))
                drawLine(ink.copy(alpha = 0.85f), c + d * (hubRad * 0.55f), c + d * (hubRad * 0.95f), 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
    hub(hubL, angleL)
    hub(hubR, angleR)
    // Виступ знизу (трапеція) з прорізами.
    val bumpPts = listOf(Offset(rc.left + w * 0.17f, rc.bottom), Offset(rc.left + w * 0.22f, rc.top + h * 0.78f), Offset(rc.left + w * 0.78f, rc.top + h * 0.78f), Offset(rc.left + w * 0.83f, rc.bottom))
    val bump = Path().apply { moveTo(bumpPts[0].x, bumpPts[0].y); bumpPts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
    drawPath(bump, if (glass) Color(0xFF151619) else Color(0xFF110F0C))
    if (glass) drawPath(bump, Color.White.copy(alpha = 0.16f), style = Stroke(1.dp.toPx()))
    else drawPath(taperedPath(bumpPts, 1.4.dp.toPx()), SheetInk.copy(alpha = 0.6f))
    // Прорізи: ролики (круглі), голівка (посередині), притиск — і стрічка, видна лише в них, по самому низу.
    val tapeY = rc.bottom - h * 0.035f
    val holes = listOf(
        Rect(p(0.235f, 0.88f), Size(w * 0.05f, h * 0.10f)),
        Rect(p(0.36f, 0.88f), Size(w * 0.07f, h * 0.10f)),
        Rect(p(0.455f, 0.86f), Size(w * 0.09f, h * 0.12f)),
        Rect(p(0.57f, 0.88f), Size(w * 0.07f, h * 0.10f)),
        Rect(p(0.715f, 0.88f), Size(w * 0.05f, h * 0.10f)),
    )
    val tapeLine = if (glass) Color(0xFF6E5A47) else SheetInk.copy(alpha = 0.7f)
    holes.forEachIndexed { i, hr ->
        drawRoundRect(Color.Black, hr.topLeft, hr.size, CornerRadius(w * 0.006f))
        clipRect(hr.left, hr.top, hr.right, hr.bottom) {
            drawLine(tapeLine, Offset(hr.left, tapeY), Offset(hr.right, tapeY), 1.6.dp.toPx())
            if (moving && glass) drawLine(Color.White.copy(alpha = 0.18f), Offset(hr.left, tapeY - 0.5f), Offset(hr.right, tapeY - 0.5f), 0.6.dp.toPx())
        }
        if (i == 0 || i == 4) drawCircle(faint, hr.width * 0.3f, hr.center + Offset(0f, -hr.height * 0.15f), style = Stroke(0.8.dp.toPx()))
    }
}

/** Літера сторони касети — «A» чи «B». */
private fun DrawScope.drawSideLetter(side: Int, c: Offset, s: Float, ink: Color) {
    val st = 1.8.dp.toPx()
    if (side == 0) {
        drawLine(ink, c + Offset(-s * 0.6f, s), c + Offset(0f, -s), st, cap = StrokeCap.Round)
        drawLine(ink, c + Offset(0f, -s), c + Offset(s * 0.6f, s), st, cap = StrokeCap.Round)
        drawLine(ink, c + Offset(-s * 0.32f, s * 0.25f), c + Offset(s * 0.32f, s * 0.25f), st, cap = StrokeCap.Round)
    } else {
        val path = Path().apply {
            moveTo(c.x - s * 0.45f, c.y + s)
            lineTo(c.x - s * 0.45f, c.y - s)
            cubicTo(c.x + s * 0.55f, c.y - s, c.x + s * 0.55f, c.y - s * 0.05f, c.x - s * 0.45f, c.y - s * 0.05f)
            cubicTo(c.x + s * 0.7f, c.y - s * 0.05f, c.x + s * 0.7f, c.y + s, c.x - s * 0.45f, c.y + s)
        }
        drawPath(path, ink, style = Stroke(st, cap = StrokeCap.Round))
    }
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
        drawRoundRect(Color.Black.copy(alpha = if (glass) 0.75f else 0.45f), tl, Size(cell, ch), CornerRadius(1.5.dp.toPx()))
        drawRoundRect(faint, tl, Size(cell, ch), CornerRadius(1.5.dp.toPx()), style = Stroke(0.8.dp.toPx()))
    }
}

/** Стрілочний «контроль запису»: пласка дуга з поділками, червона зона праворуч, стрілка від гучності [level] (0…1). */
private fun DrawScope.drawMeter(glass: Boolean, ink: Color, faint: Color, level: Float, recording: Boolean) {
    val r = 8.dp.toPx()
    drawRoundRect(if (glass) Color(0xFF1E1F22) else Color(0xFF1A1813), cornerRadius = CornerRadius(r))
    frame(Offset.Zero, size, r, glass, ink, seed = 6)
    // Стрілка обертається з-під низу приладу: видно лише верхню частину дуги — прилад низький і широкий.
    val pivot = Offset(size.width / 2, size.height * 1.35f)
    val rad = size.height * 1.12f
    val half = Math.toDegrees(kotlin.math.asin((size.width * 0.40f / rad).toDouble().coerceAtMost(0.95))).toFloat()
    val from = -90f - half
    val sweep = half * 2
    fun at(deg: Float, rr: Float) = pivot + Offset(cos(deg * PI.toFloat() / 180f), sin(deg * PI.toFloat() / 180f)) * rr
    clipRect(0f, 0f, size.width, size.height) {
        for (i in 0..10) {
            val d = from + sweep * i / 10f
            val red = i >= 8
            drawLine(if (red) RecRed.copy(alpha = 0.9f) else ink.copy(alpha = 0.7f), at(d, rad * 0.9f), at(d, rad * (if (i % 5 == 0) 1f else 0.96f)), (if (i % 5 == 0) 1.5f else 1f).dp.toPx())
        }
        drawArc(faint, from, sweep * 0.8f, false, topLeft = pivot - Offset(rad * 0.88f, rad * 0.88f), size = Size(rad * 1.76f, rad * 1.76f), style = Stroke(1.dp.toPx()))
        drawArc(RecRed.copy(alpha = 0.7f), from + sweep * 0.8f, sweep * 0.2f, false, topLeft = pivot - Offset(rad * 0.88f, rad * 0.88f), size = Size(rad * 1.76f, rad * 1.76f), style = Stroke(2.dp.toPx()))
        val d = from + sweep * level.coerceIn(0f, 1f)
        drawLine(ink, at(d, rad * 0.55f), at(d, rad * 0.99f), 1.5.dp.toPx(), cap = StrokeCap.Round)
    }
    if (glass) drawRoundRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 0.5f to Color.Transparent), cornerRadius = CornerRadius(r))
    if (recording) drawCircle(RecRed, 3.5.dp.toPx(), Offset(size.width - 10.dp.toPx(), 10.dp.toPx()))
}

private fun Color.argb(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/**
 * Лічильник стрічки (лише показує, не натискається): цифри, як на механічному лічильнику, і шкала від 0 до кінця записаного
 * — з рисками там, де почались дописи, і бігунком там, де зараз стрічка.
 */
private fun DrawScope.drawCounter(glass: Boolean, ink: Color, faint: Color, head: Long, dur: Long, takes: List<Long>, recording: Boolean) {
    val r = 8.dp.toPx()
    drawRoundRect(if (glass) Color(0xFF1E1F22) else Color(0xFF1A1813), cornerRadius = CornerRadius(r))
    frame(Offset.Zero, size, r, glass, ink, seed = 9)
    // Цифри в окремих віконцях.
    val digits = clock(head).padStart(5, '0').toCharArray()
    // Цифри вміщаються і по висоті, і по ширині приладу.
    val boxW = minOf(size.height * 0.42f * 0.62f, size.width * 0.78f / (digits.size + (digits.size - 1) * 0.12f))
    val boxH = boxW / 0.62f
    val gap = boxW * 0.12f
    val totalW = digits.size * boxW + (digits.size - 1) * gap
    var x = (size.width - totalW) / 2
    val y = size.height * 0.12f
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = boxH * 0.72f
        typeface = android.graphics.Typeface.MONOSPACE
    }
    digits.forEach { ch ->
        if (ch == ':') {
            paint.color = ink.copy(alpha = 0.8f).argb()
            drawContext.canvas.nativeCanvas.drawText(":", x + boxW / 2, y + boxH * 0.75f, paint)
        } else {
            drawRoundRect(Color.Black.copy(alpha = 0.85f), Offset(x, y), Size(boxW, boxH), CornerRadius(2.dp.toPx()))
            drawRoundRect(faint, Offset(x, y), Size(boxW, boxH), CornerRadius(2.dp.toPx()), style = Stroke(0.8.dp.toPx()))
            paint.color = (if (recording) RecRed else ink).argb()
            drawContext.canvas.nativeCanvas.drawText(ch.toString(), x + boxW / 2, y + boxH * 0.76f, paint)
        }
        x += boxW + gap
    }
    // Шкала записаного: від початку до кінця.
    val l = size.width * 0.1f
    val rr = size.width * 0.9f
    val sy = size.height * 0.8f
    drawLine(faint, Offset(l, sy), Offset(rr, sy), 1.dp.toPx())
    drawLine(ink.copy(alpha = 0.6f), Offset(l, sy - 4.dp.toPx()), Offset(l, sy + 4.dp.toPx()), 1.dp.toPx())
    drawLine(ink.copy(alpha = 0.6f), Offset(rr, sy - 4.dp.toPx()), Offset(rr, sy + 4.dp.toPx()), 1.dp.toPx())
    if (dur > 0) {
        takes.forEach { t ->
            val tx = l + (rr - l) * (t.toFloat() / dur).coerceIn(0f, 1f)
            drawLine(ink.copy(alpha = 0.8f), Offset(tx, sy - 5.dp.toPx()), Offset(tx, sy + 5.dp.toPx()), 1.2.dp.toPx())
        }
        val hx = l + (rr - l) * (head.toFloat() / dur).coerceIn(0f, 1f)
        drawPath(Path().apply { moveTo(hx, sy - 1.dp.toPx()); lineTo(hx - 4.dp.toPx(), sy - 7.dp.toPx()); lineTo(hx + 4.dp.toPx(), sy - 7.dp.toPx()); close() }, if (recording) RecRed else ink)
    }
    if (glass) drawRoundRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 0.5f to Color.Transparent), cornerRadius = CornerRadius(r))
}
