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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.layout.onGloballyPositioned
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
    /** Записати на сторону: [cutAt] = null — у кінець записаного; число — поверх, з цього місця (решта стирається). */
    onRecord: (side: Int, cutAt: Long?) -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: (Boolean) -> Unit,
    voiceMode: Int,
    onVoiceMode: (Int) -> Unit,
    onShare: (side: Int) -> Unit,
    /** Книжка: щоб жест на крутілці не гортав сторінку (як і на фото чи записі на сторінці дня). */
    host: PageHost? = null,
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
    // Крутілка діє й на слухання — наживо, поверх записаного.
    LaunchedEffect(voiceMode) { player.denoise(voiceMode) }
    // Де стоїть стрічка (мс від початку сторони). Касету залишили там, де записали, — у кінці записаного.
    var head by remember(day.day) { mutableFloatStateOf(dur.toFloat()) }
    var loaded by remember(day.day, side, dur) { mutableStateOf<ByteArray?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val flip = remember(day.day) { Animatable(0f) }
    val eject = remember(day.day) { Animatable(0f) }
    // Для жестів по касеті (обробник один на весь час — беремо свіжі значення).
    val sideNow by androidx.compose.runtime.rememberUpdatedState({ side })
    val hasNow by androidx.compose.runtime.rememberUpdatedState({ if (side == 0) day.audio else day.audioB })
    val onShareNow by androidx.compose.runtime.rememberUpdatedState(onShare)
    val canTouchCassette by androidx.compose.runtime.rememberUpdatedState({ (deck == Deck.Idle || deck == Deck.Pause) && recording == null && !flip.isRunning && !eject.isRunning })

    // Дописали — стрічка стоїть у кінці нового запису.
    // (Лише коли змінився сам запис, а не сторона: перевернута касета стоїть там, де стрічка.)
    LaunchedEffect(day.audioMs, day.audioMsB) { if (deck == Deck.Idle || deck == Deck.Record) head = dur.toFloat() }
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
            // З кінця (чи з порожньої частини стрічки після перевертання) — спершу перемотати на початок запису.
            if (head >= dur - 300) windTo(0f)
            val b = bytes() ?: return@launch
            val ok = player.play(b, head.toInt().coerceAtLeast(0)) {
                deck = Deck.Idle
                head = dur.toFloat()
            }
            if (!ok) return@launch
            deck = Deck.Play
        }
    }

    // Поки грає (і поки мотає зі звуком) — стрічка йде плавно, кадр за кадром, звіряючись із програвачем.
    var cue by remember { mutableStateOf(false) }
    var reverse by remember { mutableStateOf<com.blacklaunch.diary.audio.ReverseCue?>(null) }
    LaunchedEffect(deck, cue) {
        val speed = when {
            deck == Deck.Play -> 1f
            deck == Deck.Forward && cue -> 3f
            deck == Deck.Rewind && cue -> -3f
            else -> return@LaunchedEffect
        }
        var base = head
        var baseT = 0L
        var lastSync = 0L
        while (true) {
            val t = withFrameNanos { it }
            if (baseT == 0L) { baseT = t; lastSync = t }
            var h = base + (t - baseT) / 1e6f * speed
            // Раз на чверть секунди — звірити з програвачем (крім «назад», там грає свій звук).
            if (speed > 0 && t - lastSync > 250_000_000L) {
                base = player.position.toFloat(); baseT = t; lastSync = t; h = base
            }
            head = h.coerceIn(0f, dur.toFloat())
            if (speed < 0 && head <= 0f) break
        }
    }

    fun stopAll() {
        cancelJob()
        reverse?.stop(); reverse = null
        player.stop()
        cue = false
        deck = Deck.Idle
    }

    /** «Стоп»: зупинити й перемотати на початок записаного. */
    fun stopAndRewind() {
        stopAll()
        if (!has || head <= 0f) return
        job = scope.launch { windTo(0f) }
    }

    fun nudge(ms: Float) {
        if (!has || isRec) return
        // Вперед — лише по записаному; назад — завжди (і з порожньої частини стрічки після перевертання).
        if (ms > 0 && head >= dur) return
        head = if (ms > 0) (head + ms).coerceAtMost(dur.toFloat()) else (head + ms).coerceIn(0f, maxOf(dur.toFloat(), head))
        if (player.duration > 0) player.seek(head.toInt())
    }

    // Котушки: кут — точна функція того, де стрічка (а не накопичена сума), тож маточина й намотка на ній завжди
    // разом, без смикань. Тонка котушка крутиться швидше за товсту — так і в справжній касеті (стрічка йде з однаковою
    // швидкістю, а коло різне).
    val angleL = reelAngle(head, left = true)
    val angleR = reelAngle(head, left = false)

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
            DeckKey(Deck.Record, shown == Deck.Record, enabled = editable && recording == null, desc = stringResource(R.string.record), glass = glass, hold = { down ->
                // Утримання «Запису» — запис поверх, з місця, де стоїть стрічка (як на магнітофоні: перемотав назад
                // і пишеш заново — старе далі стирається). Невдалий шматок так і переписується.
                if (!down) return@DeckKey
                if (has && head < dur - 300) {
                    stopAll()
                    haptics.longPress()
                    onRecord(side, head.toLong())
                } else {
                    stopAll()
                    job = scope.launch {
                        if (head < dur) windTo(dur.toFloat())
                        haptics.longPress()
                        onRecord(side, null)
                    }
                }
            }) {
                if (dur >= Limits.SIDE_MS - 1000) { vm.show(R.string.recorder_side_full); return@DeckKey }
                stopAll()
                job = scope.launch {
                    // Короткий дотик — у кінець записаного: стрічку спершу домотати туди.
                    if (head < dur) windTo(dur.toFloat())
                    haptics.longPress()
                    onRecord(side, null)
                }
            }
            DeckKey(Deck.Rewind, shown == Deck.Rewind, enabled = has && !isRec, desc = stringResource(R.string.recorder_rewind), glass = glass, hold = { down ->
                if (down) {
                    val b = loaded
                    if (deck == Deck.Play && b != null) {
                        // Під час слухання — назад зі звуком: задом наперед і прискорено.
                        player.pause()
                        reverse = com.blacklaunch.diary.audio.ReverseCue(b, head.toLong()).also { it.start() }
                        cue = true
                        deck = Deck.Rewind
                    } else {
                        if (deck == Deck.Play) player.pause()
                        deck = Deck.Rewind
                        job = scope.launch { while (deck == Deck.Rewind && head > 0f) { head = (head - 40f * WIND_SPEED).coerceAtLeast(0f); delay(40) } }
                    }
                } else if (deck == Deck.Rewind) {
                    reverse?.stop(); reverse = null
                    cue = false
                    if (player.duration > 0) { player.seek(head.toInt()); player.resume(); deck = Deck.Play } else deck = Deck.Idle
                }
            }) { nudge(-5000f) }
            DeckKey(Deck.Forward, shown == Deck.Forward, enabled = has && !isRec, desc = stringResource(R.string.recorder_forward), glass = glass, hold = { down ->
                if (down) {
                    if (deck == Deck.Play) {
                        // Під час слухання — мотає вперед зі звуком (прискорено), як «пошук» на магнітофоні.
                        player.speed(3f)
                        cue = true
                        deck = Deck.Forward
                    } else if (head < dur) {
                        deck = Deck.Forward
                        job = scope.launch { while (deck == Deck.Forward && head < dur) { head = (head + 40f * WIND_SPEED).coerceAtMost(dur.toFloat()); delay(40) } }
                    }
                } else if (deck == Deck.Forward) {
                    if (cue) { player.speed(1f); cue = false; deck = Deck.Play }
                    else if (player.duration > 0) { player.seek(head.toInt()); player.resume(); deck = Deck.Play } else deck = Deck.Idle
                }
            }) { nudge(5000f) }
            DeckKey(Deck.Play, shown == Deck.Play, enabled = has && !isRec, desc = stringResource(R.string.play), glass = glass) { play() }
            DeckKey(Deck.Pause, shown == Deck.Pause || (isRec && recording!!.paused), enabled = isRec || has, desc = stringResource(R.string.recorder_pause), glass = glass) {
                if (isRec) { onPauseRecording(!recording!!.paused); return@DeckKey }
                when (deck) {
                    Deck.Play -> { player.pause(); deck = Deck.Pause }
                    Deck.Pause -> { player.resume(); deck = Deck.Play }
                    else -> Unit
                }
            }
            DeckKey(Deck.Idle, false, enabled = true, desc = stringResource(R.string.record_stop), glass = glass) {
                if (isRec) onStopRecording() else stopAndRewind()
            }
        }
    }

    // Дата вже написана на касеті — під заголовком її не повторюємо.
    PaperPage(title = title, subtitle = null, seed = seed, edgeHeight = 118.dp, edge = keys) {
        val holesHolder = LocalReliefHoles.current
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ——— Шухлядка з касетою (торкнись — перевернути на іншу сторону) ———
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .weight(1.05f)
                    .semantics { contentDescription = if (side == 0) "A" else "B" }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (!canTouchCassette()) return@detectTapGestures
                                haptics.tap()
                                stopAll()
                                scope.launch {
                                    flip.animateTo(90f, tween(220, easing = LinearEasing))
                                    // Одна стрічка: перевернув — що на A вже прокрутилось, на B ще попереду (котушки міняються місцями).
                                    side = 1 - side
                                    head = (Limits.SIDE_MS - head).coerceIn(0f, Limits.SIDE_MS.toFloat())
                                    flip.snapTo(-90f)
                                    flip.animateTo(0f, tween(220, easing = LinearEasing))
                                }
                            },
                            onLongPress = {
                                // Затиснув касету — виймаєш її, і одразу системне «Поділитися» цією стороною.
                                if (!canTouchCassette() || !hasNow()) return@detectTapGestures
                                haptics.longPress()
                                stopAll()
                                scope.launch {
                                    eject.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                                    onShareNow(sideNow())
                                    delay(900)
                                    eject.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
                                }
                            },
                        )
                    }
                    .onGloballyPositioned { c ->
                        // Касета — пластик: на ній немає заломів паперу.
                        val page = holesHolder?.page ?: return@onGloballyPositioned
                        val off = page.localPositionOf(c, Offset.Zero)
                        val r = cassetteRect(Size(c.size.width.toFloat(), c.size.height.toFloat())).translate(off)
                        holesHolder.rects["cassette"] = androidx.compose.ui.geometry.RoundRect(r, CornerRadius(r.width * 0.035f))
                    },
            ) {
                drawDeckWell(glass, ink)
                val c = cassetteRect(size)
                val sx = cos(flip.value * PI.toFloat() / 180f)
                // Механізм під касетою: голівка, притискний ролик, ведучий вал — касета на них стоїть.
                drawMechanism(c, glass, ink, faint, engaged = shown == Deck.Play || shown == Deck.Record)
                // Виймаєш касету — вона висувається вгору й ховається за краєм шухлядки (поза сторінку не малюємо).
                val lift = eject.value * (c.bottom + 4.dp.toPx())
                drawContext.canvas.save()
                drawContext.canvas.clipRect(0f, 0f, size.width, size.height)
                drawContext.canvas.translate(0f, -lift)
                withScaleX(sx, c.center) {
                    drawCassette(c, glass, ink, faint, side, (head / Limits.SIDE_MS).coerceIn(0f, 1f), angleL, angleR, moving = shown != Deck.Idle && shown != Deck.Pause, labelText = subtitle)
                }
                drawContext.canvas.restore()
                // Пази й притискні лапки: касета вставлена в шухлядку, а не висить.
                drawHolders(c, glass, ink)
                // Скло дверцят — легкий відблиск поверх.
                if (glass) drawRoundRect(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.White.copy(alpha = 0.03f)), start = Offset.Zero, end = Offset(size.width, size.height)),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }
            // ——— Динамік і два прилади праворуч ———
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.fillMaxWidth().weight(1f)) { drawGrille(glass, ink, faint) }
                    NoiseKnob(voiceMode, enabled = recording == null, glass = glass, modifier = Modifier.fillMaxWidth().weight(1f).noFlip(host, "knob_${day.day}")) { onVoiceMode(it) }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Час цього запису: поки пишеш — скільки вже, інакше — скільки тривав останній допис.
                    val takeMs = if (isRec) recording!!.elapsed else if (has) dur - (takes.lastOrNull() ?: 0L) else 0L
                    Canvas(Modifier.fillMaxWidth().weight(1f)) { drawMeter(glass, ink, faint, level, isRec, takeMs) }
                    Canvas(Modifier.fillMaxWidth().weight(1f)) { drawCounter(glass, ink, faint, head.toLong(), dur, takes, isRec, if (isRec) recording!!.base else null, if (isRec) recording!!.cutAt else null) }
                }
            }
        }
    }
}

/**
 * Кут котушки (радіани) для місця на стрічці [head]: інтеграл від швидкості стрічки, поділеної на радіус намотки.
 * Порожня маточина — пів оберту на секунду звичайного ходу.
 */
private fun reelAngle(head: Float, left: Boolean): Float {
    val c = (PACK_RATIO * PACK_RATIO - 1.0)
    val side = Limits.SIDE_MS.toDouble()
    val f = (head / side).coerceIn(0.0, 1.0)
    val k = PI / 1000.0 * side * 2.0 / c
    val a = if (left) -k * sqrt(1.0 + (1.0 - f) * c) else k * sqrt(1.0 + f * c)
    return (a % (2 * PI)).toFloat()
}

/**
 * Галетний перемикач «ШУМОДАВ» на панелі: три положення — 0 (вимкнено), 1 (легкий), 2 (сильний).
 * Повертається жестом, як справжня ручка: ведеш пальцем по колу (чи просто вбік / вгору-вниз) — ручка йде за пальцем
 * і клацає (з вібрацією) на кожному положенні; відпустив — стає точно в найближче. Поки йде запис — не крутиться.
 */
@Composable
private fun NoiseKnob(mode: Int, enabled: Boolean, glass: Boolean, modifier: Modifier, onMode: (Int) -> Unit) {
    val haptics = LocalHaptics.current
    val title = stringResource(R.string.knob_title)
    // Поки палець крутить — ручка йде за ним (зсув у градусах від положення); відпустив — до найближчого клацання.
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val target = -60f + 60f * mode
    val shownAngle by animateFloatAsState(if (dragging) (target + drag).coerceIn(-75f, 75f) else target, if (dragging) spring(stiffness = 4000f) else spring(dampingRatio = 0.55f, stiffness = 600f), label = "knob")
    val modeNow by androidx.compose.runtime.rememberUpdatedState(mode)
    val onModeNow by androidx.compose.runtime.rememberUpdatedState(onMode)
    val ink = if (glass) Oled.TextPrimary else SheetInk
    Canvas(
        modifier
            .semantics { contentDescription = "$title $mode" }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val c = Offset(size.width / 2f, size.height * 0.62f)
                    var last = down.position
                    var acc = 0f
                    dragging = true
                    drag = 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        val p = ch.position
                        // Поворот = кут руху пальця навколо центру ручки (за годинниковою — більше).
                        val a0 = kotlin.math.atan2((last.y - c.y).toDouble(), (last.x - c.x).toDouble())
                        val a1 = kotlin.math.atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())
                        var d = Math.toDegrees(a1 - a0).toFloat()
                        if (d > 180f) d -= 360f
                        if (d < -180f) d += 360f
                        // Біля самого центру кут скаче — там рахуємо простим рухом убік / вгору.
                        if ((p - c).getDistance() < 14.dp.toPx()) d = ((p.x - last.x) - (p.y - last.y)) * 0.8f
                        acc += d
                        last = p
                        // Кожні ~40° — наступне положення (з клацанням).
                        val step = when {
                            acc > 40f && modeNow < 2 -> 1
                            acc < -40f && modeNow > 0 -> -1
                            else -> 0
                        }
                        if (step != 0) {
                            acc -= 60f * step
                            haptics.keyDown()
                            onModeNow(modeNow + step)
                        }
                        drag = acc.coerceIn(-30f, 30f)
                        ch.consume()
                    }
                    dragging = false
                    drag = 0f
                }
            },
    ) {
        val r = 8.dp.toPx()
        drawRoundRect(if (glass) Color(0xFF1E1F22) else Color(0xFF1A1813), cornerRadius = CornerRadius(r))
        frame(Offset.Zero, size, r, glass, ink, seed = 14)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        }
        // Напис угорі, як шовкографія на панелі.
        paint.textSize = minOf(size.height * 0.11f, 12.dp.toPx())
        paint.letterSpacing = 0.12f
        paint.color = ink.copy(alpha = if (enabled) 0.8f else 0.45f).argb()
        drawContext.canvas.nativeCanvas.drawText(title, size.width / 2, size.height * 0.15f, paint)
        val c = Offset(size.width / 2f, size.height * 0.62f)
        val kr = minOf(size.width * 0.19f, size.height * 0.25f)
        // Поділки й цифри положень.
        paint.letterSpacing = 0f
        paint.textSize = minOf(size.height * 0.12f, 13.dp.toPx())
        for (k in 0..2) {
            val a = Math.toRadians((-60.0 + 60.0 * k) - 90.0)
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            val on = k == mode
            drawLine(if (on) ink else ink.copy(alpha = 0.45f), c + dir * (kr * 1.22f), c + dir * (kr * 1.42f), (if (on) 2.2f else 1.3f).dp.toPx(), cap = StrokeCap.Round)
            paint.color = (if (on) ink else ink.copy(alpha = if (enabled) 0.55f else 0.3f)).argb()
            val tp = c + dir * (kr * 1.72f)
            drawContext.canvas.nativeCanvas.drawText(k.toString(), tp.x, tp.y + paint.textSize * 0.36f, paint)
        }
        // Тінь під ручкою — вона випукла, стоїть над панеллю.
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), center = c + Offset(kr * 0.12f, kr * 0.22f), radius = kr * 1.35f), kr * 1.35f, c + Offset(kr * 0.12f, kr * 0.22f))
        if (glass) {
            // Спідниця ручки (нижня ширша частина) з ребрами, що крутяться разом із ручкою.
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF3A3B41), Color(0xFF16171A)), startY = c.y - kr, endY = c.y + kr), kr, c)
            rotate(shownAngle, c) {
                for (k in 0 until 30) {
                    val a = k * (2 * PI.toFloat() / 30)
                    val d = Offset(cos(a), sin(a))
                    drawLine(Color.Black.copy(alpha = 0.55f), c + d * (kr * 0.84f), c + d * (kr * 0.99f), 1.4.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.10f), c + d.rotated(0.05f) * (kr * 0.84f), c + d.rotated(0.05f) * (kr * 0.99f), 0.8.dp.toPx())
                }
            }
            // Верх ручки — опукла кришка з відблиском (світло згори зліва, не крутиться).
            val top = kr * 0.78f
            drawCircle(Brush.radialGradient(listOf(Color(0xFF4A4C53), Color(0xFF232428), Color(0xFF17181B)), center = c - Offset(top * 0.35f, top * 0.45f), radius = top * 1.5f), top, c)
            drawCircle(Color.White.copy(alpha = 0.18f), top, c, style = Stroke(1.dp.toPx()))
            drawArc(Color.White.copy(alpha = 0.22f), 200f, 70f, false, topLeft = c - Offset(top * 0.82f, top * 0.82f), size = Size(top * 1.64f, top * 1.64f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(Color.White.copy(alpha = 0.16f), kr, c, style = Stroke(1.dp.toPx()))
            rotate(shownAngle, c) {
                drawLine(Color.White, c + Offset(0f, -top * 0.2f), c + Offset(0f, -top * 0.92f), 2.6.dp.toPx(), cap = StrokeCap.Round)
            }
        } else {
            // «Аркуш»: олівцем — дві кромки, ребра по колу, тіньова штриховка знизу справа, риска-покажчик.
            drawCircle(Color(0xFF15130F), kr, c)
            drawCircle(SheetInk.copy(alpha = 0.85f), kr, c, style = Stroke(1.6.dp.toPx()))
            rotate(shownAngle, c) {
                for (k in 0 until 30) {
                    val a = k * (2 * PI.toFloat() / 30)
                    val d = Offset(cos(a), sin(a))
                    drawLine(SheetInk.copy(alpha = 0.45f), c + d * (kr * 0.84f), c + d * (kr * 0.98f), 1.dp.toPx())
                }
            }
            val top = kr * 0.78f
            drawCircle(SheetInk.copy(alpha = 0.7f), top, c, style = Stroke(1.2.dp.toPx()))
            for (k in 0 until 7) {
                val y = c.y + top * (0.15f + k * 0.11f)
                val half = kotlin.math.sqrt((top * top - (y - c.y) * (y - c.y)).coerceAtLeast(0f))
                drawLine(SheetInk.copy(alpha = 0.18f), Offset(c.x + half * 0.1f, y), Offset(c.x + half * 0.95f, y - top * 0.08f), 0.8.dp.toPx())
            }
            rotate(shownAngle, c) {
                drawLine(SheetInk, c + Offset(0f, -top * 0.2f), c + Offset(0f, -top * 0.92f), 2.4.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

private fun Offset.rotated(rad: Float): Offset = Offset(x * cos(rad) - y * sin(rad), x * sin(rad) + y * cos(rad))

private fun Float.roundToIntSafe(): Int = kotlin.math.round(this).toInt()

/** Тут сторінка не гортається: жести (крутілка) лишаються приладу. */
@Composable
private fun Modifier.noFlip(host: PageHost?, key: String): Modifier {
    if (host == null) return this
    val preview = LocalPagePreview.current
    DisposableEffect(key) { onDispose { host.block(key, null) } }
    return this.onGloballyPositioned { c ->
        if (preview) return@onGloballyPositioned
        val book = host.bookCoords ?: return@onGloballyPositioned
        val o = runCatching { book.localPositionOf(c, Offset.Zero) }.getOrNull() ?: return@onGloballyPositioned
        host.block(key, Rect(o, Size(c.size.width.toFloat(), c.size.height.toFloat())))
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
    // Обробник дотику не перезапускається на кожен кадр (раніше нові лямбди щокадру скидали жест — дотики губились).
    val click by androidx.compose.runtime.rememberUpdatedState(onClick)
    val holdNow by androidx.compose.runtime.rememberUpdatedState(hold)
    val hasHold = hold != null
    val sink by animateFloatAsState(if (down) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "key")
    val ink = if (glass) Oled.TextPrimary else SheetInk
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics { contentDescription = desc }
            .pointerInput(enabled, hasHold) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    haptics.keyDown()
                    var held = false
                    val j = if (hasHold) scope.launch { delay(320); held = true; holdNow?.invoke(true) } else null
                    val up = waitForUpOrCancellation()
                    j?.cancel()
                    pressed = false
                    haptics.keyUp()
                    if (held) holdNow?.invoke(false) else if (up != null) click()
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
    // Між віконцем і отворами маточин — стінка корпусу (так, як можна відлити насправді).
    val win = Rect(p(0.395f, 0.40f), p(0.605f, 0.62f))
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
        // Межа між шарами — ледь видні кільця й нерівності намотки, що крутяться разом із котушкою (видно, що вона йде).
        fun packTexture(c: Offset, rad: Float, a: Float) {
            drawCircle(Color.Black.copy(alpha = 0.25f), (rad + hubRad) / 2, c, style = Stroke(0.6.dp.toPx()))
            if (rad < hubRad * 1.3f) return
            // М'які відблиски на намотці (розмиті, без різких ліній) — крутяться разом із котушкою.
            val hi = if (glass) Color.White else SheetInk
            val a1 = if (glass) 0.09f else 0.16f
            rotate(a * 180f / PI.toFloat(), c) {
                drawCircle(
                    Brush.sweepGradient(
                        0f to Color.Transparent, 0.07f to hi.copy(alpha = a1), 0.16f to Color.Transparent,
                        0.5f to Color.Transparent, 0.58f to hi.copy(alpha = a1 * 0.7f), 0.68f to Color.Transparent, 1f to Color.Transparent,
                        center = c,
                    ),
                    rad, c,
                )
            }
        }
        packTexture(hubL, rl, angleL)
        packTexture(hubR, rr, angleR)
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
private fun DrawScope.drawMeter(glass: Boolean, ink: Color, faint: Color, level: Float, recording: Boolean, takeMs: Long) {
    val r = 8.dp.toPx()
    drawRoundRect(if (glass) Color(0xFF1E1F22) else Color(0xFF1A1813), cornerRadius = CornerRadius(r))
    frame(Offset.Zero, size, r, glass, ink, seed = 6)
    // Угорі — шкала зі стрілкою (вісь стрілки під шкалою), знизу — секундомір цього запису.
    val dialH = size.height * 0.66f
    val pivot = Offset(size.width / 2, dialH * 1.3f)
    val rad = dialH * 1.08f
    val half = Math.toDegrees(kotlin.math.asin((size.width * 0.40f / rad).toDouble().coerceAtMost(0.95))).toFloat()
    val from = -90f - half
    val sweep = half * 2
    fun at(deg: Float, rr: Float) = pivot + Offset(cos(deg * PI.toFloat() / 180f), sin(deg * PI.toFloat() / 180f)) * rr
    clipRect(0f, 0f, size.width, dialH) {
        for (i in 0..10) {
            val d = from + sweep * i / 10f
            val red = i >= 8
            drawLine(if (red) RecRed.copy(alpha = 0.9f) else ink.copy(alpha = 0.7f), at(d, rad * 0.9f), at(d, rad * (if (i % 5 == 0) 1f else 0.96f)), (if (i % 5 == 0) 1.5f else 1f).dp.toPx())
        }
        drawArc(faint, from, sweep * 0.8f, false, topLeft = pivot - Offset(rad * 0.88f, rad * 0.88f), size = Size(rad * 1.76f, rad * 1.76f), style = Stroke(1.dp.toPx()))
        drawArc(RecRed.copy(alpha = 0.7f), from + sweep * 0.8f, sweep * 0.2f, false, topLeft = pivot - Offset(rad * 0.88f, rad * 0.88f), size = Size(rad * 1.76f, rad * 1.76f), style = Stroke(2.dp.toPx()))
        val d = from + sweep * level.coerceIn(0f, 1f)
        drawLine(ink, at(d, rad * 0.5f), at(d, rad * 0.99f), 1.5.dp.toPx(), cap = StrokeCap.Round)
    }
    drawLine(faint, Offset(size.width * 0.08f, dialH), Offset(size.width * 0.92f, dialH), 0.8.dp.toPx())
    // Секундомір: тонкі цифри, червоні під час запису.
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = (size.height - dialH) * 0.55f
        typeface = android.graphics.Typeface.MONOSPACE
        color = (if (recording) RecRed else ink.copy(alpha = 0.85f)).argb()
    }
    drawContext.canvas.nativeCanvas.drawText(clock(takeMs).padStart(5, '0'), size.width / 2, dialH + (size.height - dialH) * 0.7f, paint)
    if (glass) drawRoundRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 0.5f to Color.Transparent), cornerRadius = CornerRadius(r))
    if (recording) drawCircle(RecRed, 3.5.dp.toPx(), Offset(size.width - 10.dp.toPx(), 10.dp.toPx()))
}

/** Пази шухлядки: бічні напрямні, за які касета зачеплена, і дві притискні лапки зверху. */
private fun DrawScope.drawHolders(rc: Rect, glass: Boolean, ink: Color) {
    val metal = if (glass) Color(0xFF2C2D31) else Color(0xFF1C1914)
    val edge = if (glass) Color.White.copy(alpha = 0.22f) else SheetInk.copy(alpha = 0.7f)
    val t = rc.height * 0.08f
    // Бічні напрямні: обхоплюють нижні кути касети.
    listOf(-1f, 1f).forEach { sgn ->
        val x = if (sgn < 0) rc.left - t * 0.55f else rc.right - t * 0.45f
        val rr = Rect(Offset(x, rc.top + rc.height * 0.55f), Size(t, rc.height * 0.47f))
        drawRoundRect(metal, rr.topLeft, rr.size, CornerRadius(t * 0.3f))
        drawRoundRect(edge, rr.topLeft, rr.size, CornerRadius(t * 0.3f), style = Stroke(1.dp.toPx()))
        drawLine(edge.copy(alpha = edge.alpha * 0.6f), Offset(rr.center.x, rr.top + t * 0.4f), Offset(rr.center.x, rr.bottom - t * 0.4f), 0.8.dp.toPx())
    }
    // Притискні лапки зверху (як на дверцятах, що тримають касету).
    listOf(0.2f, 0.8f).forEach { fx ->
        val cx = rc.left + rc.width * fx
        val rr = Rect(Offset(cx - rc.width * 0.05f, rc.top - t * 0.55f), Size(rc.width * 0.1f, t))
        drawRoundRect(metal, rr.topLeft, rr.size, CornerRadius(t * 0.35f))
        drawRoundRect(edge, rr.topLeft, rr.size, CornerRadius(t * 0.35f), style = Stroke(1.dp.toPx()))
    }
}

/**
 * Механізм під касетою: планка з голівкою (заходить у середній проріз), стираюча голівка ліворуч, ведучий вал і гумовий
 * притискний ролик праворуч. Поки грає чи пише — планка піднята (голівка торкається стрічки).
 */
private fun DrawScope.drawMechanism(rc: Rect, glass: Boolean, ink: Color, faint: Color, engaged: Boolean) {
    val w = rc.width
    val h = rc.height
    val lift = if (engaged) h * 0.035f else 0f
    val chrome = if (glass) Brush.verticalGradient(0f to Color(0xFF9A9CA3), 0.5f to Color(0xFF55575D), 1f to Color(0xFF2A2B2F)) else null
    val inkLine = if (glass) Color.White.copy(alpha = 0.25f) else SheetInk.copy(alpha = 0.75f)
    // Планка під касетою.
    val bar = Rect(Offset(rc.left + w * 0.12f, rc.bottom + h * 0.03f - lift), Size(w * 0.76f, h * 0.07f))
    drawRoundRect(if (glass) Color(0xFF1E1F23) else Color(0xFF171410), bar.topLeft, bar.size, CornerRadius(h * 0.02f))
    drawRoundRect(inkLine, bar.topLeft, bar.size, CornerRadius(h * 0.02f), style = Stroke(1.dp.toPx()))
    // Голівка відтворення — посередині, заходить у проріз касети.
    val head = Rect(Offset(rc.left + w * 0.465f, rc.bottom - h * 0.06f - lift), Size(w * 0.07f, h * 0.1f))
    if (chrome != null) drawRoundRect(chrome, head.topLeft, head.size, CornerRadius(w * 0.012f)) else drawRoundRect(Color(0xFF26221B), head.topLeft, head.size, CornerRadius(w * 0.012f))
    drawRoundRect(inkLine, head.topLeft, head.size, CornerRadius(w * 0.012f), style = Stroke(1.dp.toPx()))
    drawLine(inkLine, Offset(head.center.x, head.top + 2.dp.toPx()), Offset(head.center.x, head.top + head.height * 0.45f), 0.8.dp.toPx())
    // Стираюча голівка ліворуч.
    val erase = Rect(Offset(rc.left + w * 0.375f, rc.bottom - h * 0.04f - lift), Size(w * 0.045f, h * 0.08f))
    drawRoundRect(if (glass) Color(0xFF3A3B40) else Color(0xFF221E18), erase.topLeft, erase.size, CornerRadius(w * 0.008f))
    drawRoundRect(inkLine, erase.topLeft, erase.size, CornerRadius(w * 0.008f), style = Stroke(0.8.dp.toPx()))
    // Ведучий вал (тонкий) і гумовий притискний ролик праворуч.
    val capstan = Offset(rc.left + w * 0.6f, rc.bottom - h * 0.005f - lift)
    drawCircle(if (glass) Color(0xFFB9BBC2) else SheetInk.copy(alpha = 0.9f), w * 0.008f, capstan)
    val roller = Offset(rc.left + w * 0.635f, rc.bottom + h * 0.01f - lift)
    drawCircle(Color(0xFF0C0C0D), w * 0.024f, roller)
    drawCircle(inkLine, w * 0.024f, roller, style = Stroke(1.dp.toPx()))
    drawCircle(faint, w * 0.008f, roller)
}

private fun Color.argb(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/**
 * Лічильник стрічки (лише показує, не натискається): цифри, як на механічному лічильнику, і шкала від 0 до кінця записаного
 * — з рисками там, де почались дописи, і бігунком там, де зараз стрічка.
 */
private fun DrawScope.drawCounter(glass: Boolean, ink: Color, faint: Color, head: Long, dur0: Long, takes: List<Long>, recording: Boolean, recFrom: Long? = null, cutAt: Long? = null) {
    // Під час запису шкала тягнеться до поточного місця: записане — як є, що стирається (запис поверх) — бліде червоне,
    // нове — червоне.
    val dur = if (recFrom != null) maxOf(dur0, head) else dur0
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
    // Шкала записаного: кожен запис — окремий відрізок (один запис — одна суцільна смужка, допис — ще одна після проміжку).
    val l = size.width * 0.1f
    val rr = size.width * 0.9f
    val sy = size.height * 0.8f
    if (dur > 0) {
        val kept = cutAt ?: (if (recFrom != null) recFrom else dur0)
        val keptEnd = minOf(kept, dur0)
        val bounds = (listOf(0L) + takes.filter { it in 1 until keptEnd } + listOf(keptEnd)).distinct()
        val gapPx = 3.dp.toPx()
        for (k in 0 until bounds.size - 1) {
            val x0 = l + (rr - l) * bounds[k].toFloat() / dur + (if (k > 0) gapPx / 2 else 0f)
            val x1 = l + (rr - l) * bounds[k + 1].toFloat() / dur - (if (k < bounds.size - 2) gapPx / 2 else 0f)
            if (x1 > x0) drawLine(ink.copy(alpha = 0.7f), Offset(x0, sy), Offset(x1, sy), 2.dp.toPx(), cap = StrokeCap.Butt)
        }
        fun xOf(t: Long) = l + (rr - l) * (t.toFloat() / dur).coerceIn(0f, 1f)
        if (cutAt != null && cutAt < dur0) drawLine(RecRed.copy(alpha = 0.35f), Offset(xOf(cutAt), sy + 4.dp.toPx()), Offset(xOf(dur0), sy + 4.dp.toPx()), 2.dp.toPx())
        if (recFrom != null && head > recFrom) drawLine(RecRed, Offset(xOf(recFrom) + 1.5.dp.toPx(), sy), Offset(xOf(head), sy), 2.dp.toPx())
        val hx = l + (rr - l) * (head.toFloat() / dur).coerceIn(0f, 1f)
        drawPath(Path().apply { moveTo(hx, sy - 2.dp.toPx()); lineTo(hx - 4.dp.toPx(), sy - 8.dp.toPx()); lineTo(hx + 4.dp.toPx(), sy - 8.dp.toPx()); close() }, if (recording) RecRed else ink)
    } else {
        drawLine(faint, Offset(l, sy), Offset(rr, sy), 1.dp.toPx())
    }
    if (glass) drawRoundRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 0.5f to Color.Transparent), cornerRadius = CornerRadius(r))
}
