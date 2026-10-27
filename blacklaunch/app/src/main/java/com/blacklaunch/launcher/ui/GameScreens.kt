package com.blacklaunch.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.games.ArcadeGame
import com.blacklaunch.launcher.games.BlocksGame
import com.blacklaunch.launcher.games.GameKind
import com.blacklaunch.launcher.games.SnakeGame
import com.blacklaunch.launcher.games.StarsGame
import com.blacklaunch.launcher.ui.theme.Oled
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Ink = Color(0xFFEDEDED)

/** Штрих олівцем, як у котика: основна лінія й ледь зсунута тьмяніша. */
private fun DrawScope.pencil(path: Path, width: Float, alpha: Float = 0.9f) {
    drawPath(path, Ink.copy(alpha = alpha), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    translate(width * 0.4f, width * 0.3f) {
        drawPath(path, Ink.copy(alpha = alpha * 0.3f), style = Stroke(width * 0.6f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.pencilLine(a: Offset, b: Offset, width: Float, alpha: Float = 0.9f) {
    drawLine(Ink.copy(alpha = alpha), a, b, width, cap = StrokeCap.Round)
    drawLine(Ink.copy(alpha = alpha * 0.3f), a + Offset(width * 0.4f, width * 0.3f), b + Offset(width * 0.3f, -width * 0.2f), width * 0.6f, cap = StrokeCap.Round)
}

private fun DrawScope.pencilCircle(c: Offset, r: Float, width: Float, alpha: Float = 0.9f) {
    drawCircle(Ink.copy(alpha = alpha), r, c, style = Stroke(width))
    drawCircle(Ink.copy(alpha = alpha * 0.3f), r, c + Offset(width * 0.4f, width * 0.3f), style = Stroke(width * 0.6f))
}

/** Створити гру: збережену (продовжити) або нову. */
internal fun newGame(kind: GameKind, saved: String?): ArcadeGame {
    val restored: ArcadeGame? = when (kind) {
        GameKind.STARS -> StarsGame.decode(saved)
        GameKind.BLOCKS -> BlocksGame.decode(saved)
        GameKind.SNAKE -> SnakeGame.decode(saved)
    }
    return restored?.takeIf { !it.over } ?: when (kind) {
        GameKind.STARS -> StarsGame()
        GameKind.BLOCKS -> BlocksGame()
        GameKind.SNAKE -> SnakeGame()
    }
}

@Composable
internal fun gameTitle(kind: GameKind) = stringResource(
    when (kind) {
        GameKind.STARS -> R.string.game_stars
        GameKind.BLOCKS -> R.string.game_blocks
        GameKind.SNAKE -> R.string.game_snake
    },
)

/**
 * Екран гри: угорі — назва, очки, рекорд, рівень (і життя / наступна фігура); посередині — поле; знизу — керування
 * (у «Блоках») і «Пауза» / «Заново». Згорнули чи вийшли — гра зберігається, наступного разу — з того самого місця.
 */
@Composable
fun GameScreen(
    kind: GameKind,
    saved: String?,
    best: Int,
    /** Зберегти стан (null — гру закінчено, нічого продовжувати). */
    onSave: (String?) -> Unit,
    /** Записати рахунок; true — новий рекорд. */
    onRecord: (Int) -> Boolean,
    onExit: () -> Unit,
    /** Екран програми на паузі (згорнули) — гра теж. */
    active: Boolean,
) {
    var game by remember { mutableStateOf(newGame(kind, saved)) }
    // Продовжуємо збережену — спершу пауза, щоб не почати грати наосліп.
    var paused by remember { mutableStateOf(saved != null) }
    // Лічильник кадрів: поле й рядок очок перемальовуються щокадру, поки гра йде.
    val frame = remember { mutableLongStateOf(0L) }
    var newRecord by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var levelShown by remember { mutableIntStateOf(game.level) }
    var levelBanner by remember { mutableStateOf(0f) }
    val save by rememberUpdatedState(onSave)
    val haptics = LocalHaptics.current

    LaunchedEffect(active) { if (!active) { paused = true; save(game.takeIf { !it.over }?.encode()) } }
    // Вікно гри закривається (вийшли, система прибрала) — зберігаємо останній стан.
    val current by rememberUpdatedState(game)
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { save(current.takeIf { !it.over }?.encode()) } }
    androidx.activity.compose.BackHandler {
        save(game.takeIf { !it.over }?.encode())
        onExit()
    }
    LaunchedEffect(game, paused, active) {
        if (paused || !active) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                game.step(dt)
                // Вібрація на подіях гри (вимикається загальним «Вібровідгуком» у налаштуваннях).
                game.drainBuzz().forEach {
                    when (it) {
                        com.blacklaunch.launcher.games.GameBuzz.LIGHT -> haptics.tick()
                        com.blacklaunch.launcher.games.GameBuzz.MEDIUM -> haptics.tap()
                        com.blacklaunch.launcher.games.GameBuzz.STRONG -> haptics.longPress()
                    }
                }
                if (levelBanner > 0f) levelBanner -= dt
                if (game.level != levelShown) {
                    levelShown = game.level
                    if (kind != GameKind.STARS) levelBanner = 1.5f
                }
                frame.longValue++
            }
            if (game.over) {
                if (!recorded) {
                    recorded = true
                    newRecord = onRecord(game.score)
                    save(null)
                }
                break
            }
        }
    }

    fun restart() {
        game = newGame(kind, null)
        paused = false
        recorded = false
        newRecord = false
        levelShown = 1
        levelBanner = 0f
        save(null)
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(horizontal = 6.dp),
    ) {
        frame.longValue // перемальовувати щокадру
        // Верхній рядок.
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                color = Oled.TextSecondary,
                fontSize = 30.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable {
                    save(game.takeIf { !it.over }?.encode())
                    onExit()
                }.padding(horizontal = 10.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(gameTitle(kind).uppercase(), color = Oled.TextSecondary, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(stringResource(R.string.game_score, game.score), color = Oled.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                powerText(game)?.let { Text(it, color = Oled.Confirm, fontSize = 12.sp) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.game_best, maxOf(best, game.score)), color = Oled.TextSecondary, fontSize = 12.sp)
                Text(
                    when (val g = game) {
                        is StarsGame -> stringResource(R.string.game_level_lives, g.level, g.lives)
                        is BlocksGame -> stringResource(R.string.game_level_lines, g.level, g.lines)
                        is SnakeGame -> stringResource(R.string.game_level_lives, g.level, g.lives)
                        else -> stringResource(R.string.game_level, g.level)
                    },
                    color = Oled.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        // Поле.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val g = game) {
                is StarsGame -> StarsField(g, frame, enabled = !paused)
                is BlocksGame -> BlocksField(g, frame, enabled = !paused)
                is SnakeGame -> SnakeField(g, frame, enabled = !paused)
            }
            val stars = game as? StarsGame
            val starsBanner = stars?.banner?.takeIf { it > 0f }
            if ((starsBanner != null || levelBanner > 0f) && !game.over) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.game_level, game.level),
                        color = Oled.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                    )
                    if (stars?.challenge == true) Text(stringResource(R.string.game_challenge), color = Oled.Confirm, fontSize = 16.sp)
                    // Підсумок бонусного рівня, що щойно минув.
                    if (stars != null && stars.challengeResult > 0f) {
                        Text(stringResource(R.string.game_challenge_hits, stars.challengeHits, stars.challengeTotal), color = Oled.TextSecondary, fontSize = 15.sp)
                        if (stars.challengeHits == stars.challengeTotal) Text(stringResource(R.string.game_perfect, StarsGame.PERFECT_BONUS), color = Oled.Confirm, fontSize = 15.sp)
                    }
                }
            }
            if (stars != null && stars.shipDown > 0f && !stars.over) {
                Text(stringResource(R.string.game_ship_down, stars.lives), color = Oled.Danger, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
            }
            if (paused && !game.over) {
                Overlay(
                    title = stringResource(R.string.game_paused),
                    note = stringResource(
                        when (kind) {
                            GameKind.STARS -> R.string.game_hint_stars
                            GameKind.BLOCKS -> R.string.game_hint_blocks
                            GameKind.SNAKE -> R.string.game_hint_snake
                        },
                    ),
                ) {
                    DialogButton(stringResource(R.string.game_restart), ::restart)
                    DialogButton(stringResource(R.string.game_continue), { paused = false }, Tone.Confirm)
                }
            }
            if (game.over) {
                Overlay(
                    title = stringResource(R.string.game_over),
                    note = if (newRecord) stringResource(R.string.game_new_record, game.score) else stringResource(R.string.game_score, game.score),
                ) {
                    DialogButton(stringResource(R.string.game_exit), onExit)
                    DialogButton(stringResource(R.string.game_again), ::restart, Tone.Confirm)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            if (!game.over && !paused) DialogButton(stringResource(R.string.game_pause), { paused = true; save(game.encode()) })
        }
    }
}

/** Що зараз діє: гармата, швидкострільність, щит, равлик — коротко для верхнього рядка. */
@Composable
private fun powerText(game: ArcadeGame): String? = when (game) {
    is StarsGame -> listOfNotNull(
        game.gun?.let { stringResource(if (it == StarsGame.Power.TRIPLE) R.string.game_power_triple else R.string.game_power_double, game.gunLeft.toInt() + 1) },
        game.rapidLeft.takeIf { it > 0f }?.let { stringResource(R.string.game_power_rapid, it.toInt() + 1) },
        stringResource(R.string.game_power_shield).takeIf { game.shield },
    ).joinToString(" · ").ifEmpty { null }
    is SnakeGame -> game.slowLeft.takeIf { it > 0f }?.let { stringResource(R.string.game_power_slow, it.toInt() + 1) }
    else -> null
}

@Composable
private fun Overlay(title: String, note: String, buttons: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .padding(24.dp)
            .clip(shape)
            .background(Color(0xF20A0A0A))
            .border(1.dp, Oled.Glint, shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Oled.TextPrimary, fontSize = 22.sp)
        Text(note, color = Oled.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
    }
}

/** Поле гри з потрібними пропорціями посередині вільного місця. */
private fun DrawScope.fit(w: Float, h: Float): Pair<Float, Offset> {
    val s = min(size.width / w, size.height / h)
    return s to Offset((size.width - w * s) / 2f, (size.height - h * s) / 2f)
}

// ——— «Зоряний патруль» ———

@Composable
private fun StarsField(game: StarsGame, frame: androidx.compose.runtime.MutableLongState, enabled: Boolean) {
    // Масштаб поля для дотиків — не стан (оновлюється під час малювання).
    val view = remember { FieldView() }
    Canvas(
        Modifier.fillMaxSize().pointerInput(enabled) {
            if (!enabled) return@pointerInput
            // Палець будь-де: кораблик пливе туди, куди показують по горизонталі.
            awaitEachGesture {
                val down = awaitFirstDown()
                game.target = ((down.position.x - view.origin.x) / view.scale).coerceIn(0f, StarsGame.W)
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    game.target = ((change.position.x - view.origin.x) / view.scale).coerceIn(0f, StarsGame.W)
                    change.consume()
                }
            }
        },
    ) {
        frame.longValue
        val (s, o) = fit(StarsGame.W, StarsGame.H)
        view.scale = s
        view.origin = o
        val p = { x: Float, y: Float -> Offset(o.x + x * s, o.y + y * s) }
        // Рамка поля й зорі, що повільно пливуть униз.
        drawRoundRect(Ink.copy(alpha = 0.18f), topLeft = o, size = Size(StarsGame.W * s, StarsGame.H * s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f), style = Stroke(1.5f))
        for (i in 0 until 46) {
            val sx = ((i * 37) % 100).toFloat()
            val sy = ((i * 53 + game.time * (6 + i % 5) * 1.5f) % StarsGame.H)
            val tw = 0.25f + 0.25f * sin(game.time * 2f + i)
            drawCircle(Ink.copy(alpha = tw), (0.5f + (i % 3) * 0.25f) * s * 0.5f, p(sx, sy))
        }
        // Прибульці.
        game.enemies.forEach { e -> drawEnemy(e, p(e.x, e.y), s, game.time) }
        // Постріли й бомби.
        game.shots.forEach { pencilLine(p(it.x, it.y - 2.5f), p(it.x, it.y + 2.5f), 0.9f * s) }
        game.bombs.forEach { pencilCircle(p(it.x, it.y), 1.1f * s, 0.6f * s) }
        // Капсули-прокачки: кружечок зі знаком.
        game.capsules.forEach { c -> drawCapsule(c, p(c.x, c.y), s, game.time) }
        // Вибухи — промені, що розлітаються й тануть; кораблик — уламки, що крутяться й розлітаються далі.
        game.sparks.forEach { sp ->
            if (sp.big) {
                val k = sp.t / 1.4f
                for (r in 0 until 10) {
                    val a = r * 0.628f + r * 0.3f
                    val d = 2f + k * (14f + (r % 3) * 6f)
                    val cx = sp.x + cos(a) * d
                    val cy = sp.y + sin(a) * d + k * k * 10f
                    val spin = a + sp.t * 6f
                    pencilLine(p(cx - cos(spin) * 1.6f, cy - sin(spin) * 1.6f), p(cx + cos(spin) * 1.6f, cy + sin(spin) * 1.6f), 0.6f * s, 0.95f * (1f - k))
                }
                pencilCircle(p(sp.x, sp.y), (3f + k * 16f) * s, 0.4f * s, 0.6f * (1f - k))
                return@forEach
            }
            val k = sp.t / 0.6f
            for (r in 0 until 8) {
                val a = r * (Math.PI.toFloat() / 4f)
                val d0 = 1.5f + k * 4f
                val d1 = 2.5f + k * 8f
                pencilLine(p(sp.x + cos(a) * d0, sp.y + sin(a) * d0), p(sp.x + cos(a) * d1, sp.y + sin(a) * d1), 0.6f * s, 0.9f * (1f - k))
            }
        }
        // Кораблик (блимає, поки невразливий; збитий — немає, доки не з'явиться новий).
        if (game.shipDown <= 0f && (game.invulnerable <= 0f || ((game.time * 10).toInt() % 2 == 0))) {
            val c = p(game.shipX, StarsGame.SHIP_Y)
            val ship = Path().apply {
                moveTo(c.x, c.y - 6f * s)
                lineTo(c.x + 2f * s, c.y - 1f * s)
                lineTo(c.x + 6f * s, c.y + 3.5f * s)
                lineTo(c.x + 2f * s, c.y + 2.5f * s)
                lineTo(c.x, c.y + 4f * s)
                lineTo(c.x - 2f * s, c.y + 2.5f * s)
                lineTo(c.x - 6f * s, c.y + 3.5f * s)
                lineTo(c.x - 2f * s, c.y - 1f * s)
                close()
            }
            drawPath(ship, Color.Black)
            pencil(ship, 0.8f * s)
            pencilCircle(Offset(c.x, c.y - 1.2f * s), 1f * s, 0.5f * s)
            // Додаткові гармати на крилах.
            if (game.gun != null) {
                pencilLine(Offset(c.x - 4f * s, c.y + 2f * s), Offset(c.x - 4f * s, c.y - 2.5f * s), 0.6f * s)
                pencilLine(Offset(c.x + 4f * s, c.y + 2f * s), Offset(c.x + 4f * s, c.y - 2.5f * s), 0.6f * s)
            }
            // Щит — коло довкола.
            if (game.shield) pencilCircle(Offset(c.x, c.y), (8f + sin(game.time * 5f) * 0.5f) * s, 0.4f * s, 0.6f)
            // Полум'я двигуна.
            val flame = 1.5f + abs(sin(game.time * 20f)) * 1.5f
            pencilLine(Offset(c.x, c.y + 4f * s), Offset(c.x, c.y + (4f + flame) * s), 0.6f * s, 0.6f)
        }
    }
}

/** Капсула прокачки: кружечок, що повільно обертається, і знак усередині. */
private fun DrawScope.drawCapsule(c: StarsGame.Capsule, at: Offset, s: Float, time: Float) {
    val r = 3f * s
    pencilCircle(at, r, 0.5f * s)
    val w = 0.5f * s
    when (c.power) {
        StarsGame.Power.DOUBLE -> { pencilLine(at + Offset(-1f * s, -1.6f * s), at + Offset(-1f * s, 1.6f * s), w); pencilLine(at + Offset(1f * s, -1.6f * s), at + Offset(1f * s, 1.6f * s), w) }
        StarsGame.Power.TRIPLE -> {
            pencilLine(at + Offset(0f, -1.8f * s), at + Offset(0f, 1.6f * s), w)
            pencilLine(at + Offset(-1.6f * s, -1.4f * s), at + Offset(-0.8f * s, 1.6f * s), w)
            pencilLine(at + Offset(1.6f * s, -1.4f * s), at + Offset(0.8f * s, 1.6f * s), w)
        }
        StarsGame.Power.RAPID -> {
            val bolt = Path().apply { moveTo(at.x + 0.6f * s, at.y - 2f * s); lineTo(at.x - 1f * s, at.y + 0.2f * s); lineTo(at.x + 0.6f * s, at.y + 0.2f * s); lineTo(at.x - 0.6f * s, at.y + 2f * s) }
            pencil(bolt, w)
        }
        StarsGame.Power.SHIELD -> pencilCircle(at, r * 0.55f + sin(time * 6f) * 0.2f * s, w)
        StarsGame.Power.LIFE -> {
            val heart = Path().apply {
                moveTo(at.x, at.y + 1.8f * s)
                cubicTo(at.x - 2.6f * s, at.y - 0.2f * s, at.x - 1.4f * s, at.y - 2.4f * s, at.x, at.y - 0.9f * s)
                cubicTo(at.x + 1.4f * s, at.y - 2.4f * s, at.x + 2.6f * s, at.y - 0.2f * s, at.x, at.y + 1.8f * s)
            }
            pencil(heart, w)
        }
    }
}

private fun DrawScope.drawEnemy(e: StarsGame.Enemy, c: Offset, s: Float, time: Float) {
    val alpha = if (e.hitFlash > 0f) 1f else 0.9f
    val flap = sin(time * 8f + e.col) * 1.2f
    when (e.kind) {
        StarsGame.Kind.DRONE -> {
            // Жучок: тільце, крильця, що тріпочуть, вусики.
            val body = Path().apply { addOval(Rect(c.x - 2.6f * s, c.y - 2.2f * s, c.x + 2.6f * s, c.y + 2.2f * s)) }
            drawPath(body, Color.Black)
            pencil(body, 0.6f * s, alpha)
            pencilLine(Offset(c.x - 2.4f * s, c.y), Offset(c.x - 5f * s, c.y - (1.5f + flap) * s), 0.6f * s, alpha)
            pencilLine(Offset(c.x + 2.4f * s, c.y), Offset(c.x + 5f * s, c.y - (1.5f + flap) * s), 0.6f * s, alpha)
            pencilLine(Offset(c.x - 1f * s, c.y - 2f * s), Offset(c.x - 2f * s, c.y - 4f * s), 0.45f * s, alpha)
            pencilLine(Offset(c.x + 1f * s, c.y - 2f * s), Offset(c.x + 2f * s, c.y - 4f * s), 0.45f * s, alpha)
            drawCircle(Ink.copy(alpha = alpha), 0.5f * s, Offset(c.x - 0.9f * s, c.y - 0.3f * s))
            drawCircle(Ink.copy(alpha = alpha), 0.5f * s, Offset(c.x + 0.9f * s, c.y - 0.3f * s))
        }
        StarsGame.Kind.GUARD -> {
            // Вартовий: більший, з крилами-трикутниками; поранений — тріщина.
            val body = Path().apply {
                moveTo(c.x, c.y - 3.4f * s)
                lineTo(c.x + 2.6f * s, c.y)
                lineTo(c.x, c.y + 3f * s)
                lineTo(c.x - 2.6f * s, c.y)
                close()
            }
            val wings = Path().apply {
                moveTo(c.x - 2.4f * s, c.y - 0.5f * s); lineTo(c.x - (6f + flap) * s, c.y - 3f * s); lineTo(c.x - 4.5f * s, c.y + 2f * s); close()
                moveTo(c.x + 2.4f * s, c.y - 0.5f * s); lineTo(c.x + (6f + flap) * s, c.y - 3f * s); lineTo(c.x + 4.5f * s, c.y + 2f * s); close()
            }
            drawPath(wings, Color.Black)
            pencil(wings, 0.55f * s, alpha)
            drawPath(body, Color.Black)
            pencil(body, 0.7f * s, alpha)
            drawCircle(Ink.copy(alpha = alpha), 0.6f * s, Offset(c.x, c.y - 0.5f * s))
            if (e.hp < e.kind.hp) pencilLine(Offset(c.x - 1f * s, c.y + 1.6f * s), Offset(c.x + 0.8f * s, c.y - 1.8f * s), 0.4f * s, alpha)
        }
    }
}

// ——— «Блоки» ———

@Composable
private fun BlocksField(game: BlocksGame, frame: androidx.compose.runtime.MutableLongState, enabled: Boolean) {
    val density = LocalDensity.current
    val view = remember { FieldView() }
    Canvas(
        Modifier.fillMaxSize().pointerInput(enabled) {
            if (!enabled) return@pointerInput
            // Пальцем, як у решті ігор: потягнути вліво-вправо — фігура йде за пальцем по клітинках; вниз — швидше падає;
            // різкий змах униз — одразу донизу; легкий дотик — поворот.
            awaitEachGesture {
                val down = awaitFirstDown()
                val start = down.position
                val startTime = down.uptimeMillis
                var lastX = start.x
                var lastY = start.y
                var moved = false
                var dropped = false
                val slop = with(density) { 10.dp.toPx() }
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    val cell = view.scale.coerceAtLeast(1f)
                    if (!change.pressed) {
                        val dy = change.position.y - start.y
                        val dt = (change.uptimeMillis - startTime).coerceAtLeast(1)
                        when {
                            // Швидкий змах донизу — скинути.
                            !dropped && dy > cell * 2f && dy / dt > 1.2f * density.density -> game.hardDrop()
                            !moved -> game.rotate()
                        }
                        break
                    }
                    val p = change.position
                    if ((p - start).getDistance() > slop) moved = true
                    while (p.x - lastX > cell) { game.move(1); lastX += cell }
                    while (lastX - p.x > cell) { game.move(-1); lastX -= cell }
                    while (p.y - lastY > cell) { if (game.softDrop()) dropped = true; lastY += cell }
                    change.consume()
                }
            }
        },
    ) {
        frame.longValue
        // Склянка ліворуч, наступна фігура праворуч.
        val (s, o) = fit(BlocksGame.COLS + 5f, BlocksGame.ROWS.toFloat())
        view.scale = s
        val cell = s
        val glass = Rect(o.x, o.y, o.x + BlocksGame.COLS * cell, o.y + BlocksGame.ROWS * cell)
        val frame = Path().apply { moveTo(glass.left, glass.top); lineTo(glass.left, glass.bottom); lineTo(glass.right, glass.bottom); lineTo(glass.right, glass.top) }
        pencil(frame, 0.12f * cell, 0.7f)
        fun cellAt(x: Int, y: Int, type: Int, alpha: Float, ghost: Boolean = false) {
            if (y < 0) return
            val r = Rect(glass.left + x * cell + cell * 0.1f, glass.top + y * cell + cell * 0.1f, glass.left + (x + 1) * cell - cell * 0.1f, glass.top + (y + 1) * cell - cell * 0.1f)
            val box = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r, androidx.compose.ui.geometry.CornerRadius(cell * 0.18f))) }
            pencil(box, cell * (if (ghost) 0.05f else 0.08f), alpha)
            if (!ghost) hatch(r, type, cell, alpha * 0.6f)
        }
        for (y in 0 until BlocksGame.ROWS) for (x in 0 until BlocksGame.COLS) {
            val v = game.board[y * BlocksGame.COLS + x]
            if (v != 0) cellAt(x, y, v - 1, if (y in game.clearing) 0.35f else 0.9f)
        }
        // Рядки, що зникають, — спалах.
        game.clearing.forEach { y ->
            val k = (game.clearTimer / BlocksGame.CLEAR_TIME).coerceIn(0f, 1f)
            drawRect(Ink.copy(alpha = 0.5f * k), Offset(glass.left, glass.top + y * cell), Size(glass.width, cell))
        }
        if (!game.over && game.clearing.isEmpty()) {
            val ghost = game.ghostY()
            game.cells(y = ghost).forEach { (x, y) -> cellAt(x, y, game.piece, 0.25f, ghost = true) }
            game.cells().forEach { (x, y) -> cellAt(x, y, game.piece, 0.95f) }
        }
        // Наступна фігура.
        val nx = glass.right + cell * 0.8f
        val ny = glass.top + cell * 1.2f
        BlocksGame.SHAPES[game.next][0].forEach { (cx, cy) ->
            val r = Rect(nx + cx * cell * 0.8f, ny + cy * cell * 0.8f, nx + (cx + 1) * cell * 0.8f - cell * 0.1f, ny + (cy + 1) * cell * 0.8f - cell * 0.1f)
            pencil(Path().apply { addRect(r) }, cell * 0.06f, 0.7f)
        }
    }
}

/** Візерунок усередині квадратика — свій для кожної фігури (кольорів немає, тож різнимо штрихуванням). */
private fun DrawScope.hatch(r: Rect, type: Int, cell: Float, alpha: Float) {
    val w = cell * 0.05f
    val c = r.center
    val q = r.width * 0.22f
    when (type) {
        0 -> pencilLine(Offset(r.left + q, c.y), Offset(r.right - q, c.y), w, alpha)
        1 -> pencilCircle(c, q, w, alpha)
        2 -> { pencilLine(Offset(c.x, r.top + q), Offset(c.x, r.bottom - q), w, alpha); pencilLine(Offset(r.left + q, c.y), Offset(r.right - q, c.y), w, alpha) }
        3 -> pencilLine(Offset(r.left + q, r.bottom - q), Offset(r.right - q, r.top + q), w, alpha)
        4 -> pencilLine(Offset(r.left + q, r.top + q), Offset(r.right - q, r.bottom - q), w, alpha)
        5 -> drawCircle(Ink.copy(alpha = alpha), q * 0.45f, c)
        else -> { pencilLine(Offset(r.left + q, r.top + q), Offset(r.right - q, r.bottom - q), w, alpha); pencilLine(Offset(r.left + q, r.bottom - q), Offset(r.right - q, r.top + q), w, alpha) }
    }
}

// ——— «Змійка» ———

@Composable
private fun SnakeField(game: SnakeGame, frame: androidx.compose.runtime.MutableLongState, enabled: Boolean) {
    val swipe = with(LocalDensity.current) { 22.dp.toPx() }
    Canvas(
        Modifier.fillMaxSize().pointerInput(enabled) {
            if (!enabled) return@pointerInput
            // Свайп у будь-який бік — поворот; довгий свайп може дати два повороти поспіль.
            awaitEachGesture {
                val down = awaitFirstDown()
                var from = down.position
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val d = change.position - from
                    if (abs(d.x) > swipe || abs(d.y) > swipe) {
                        game.turn(
                            if (abs(d.x) > abs(d.y)) (if (d.x > 0) SnakeGame.Dir.RIGHT else SnakeGame.Dir.LEFT)
                            else (if (d.y > 0) SnakeGame.Dir.DOWN else SnakeGame.Dir.UP),
                        )
                        from = change.position
                    }
                    change.consume()
                }
            }
        },
    ) {
        frame.longValue
        val (s, o) = fit(SnakeGame.COLS.toFloat(), SnakeGame.ROWS.toFloat())
        val center = { c: Int -> Offset(o.x + (c % SnakeGame.COLS + 0.5f) * s, o.y + (c / SnakeGame.COLS + 0.5f) * s) }
        drawRoundRect(Ink.copy(alpha = 0.3f), topLeft = o, size = Size(SnakeGame.COLS * s, SnakeGame.ROWS * s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.4f), style = Stroke(s * 0.06f))
        // Перешкоди — квадратики олівцем з хрестиком.
        game.obstacles.forEach { c ->
            val m = center(c)
            val h = s * 0.38f
            pencil(Path().apply { addRect(Rect(m.x - h, m.y - h, m.x + h, m.y + h)) }, s * 0.07f, 0.7f)
            pencilLine(m + Offset(-h * 0.6f, -h * 0.6f), m + Offset(h * 0.6f, h * 0.6f), s * 0.05f, 0.45f)
        }
        // Бонус: блимає, коли ось-ось зникне.
        game.bonus?.let { b ->
            if (game.bonusLeft > 2f || ((game.bonusLeft * 6).toInt() % 2 == 0)) drawSnakeBonus(b, center(game.bonusCell), s)
        }
        // Їжа — кружечок, що ледь пульсує.
        pencilCircle(center(game.food), s * 0.22f, s * 0.08f)
        // Змійка — одна лінія; з'їла — на мить товща.
        val path = Path()
        game.body.forEachIndexed { i, c -> center(c).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
        // Щойно врізалась — блимає, поки з'являється знову; вперлась у стіну — голова теж блимає (ще можна повернути).
        val blink = game.respawn > 0f && ((game.respawn * 8).toInt() % 2 == 0)
        if (!blink) pencil(path, s * (0.42f + game.flash), 0.9f)
        val head = center(game.body.first())
        if (game.braking) pencilCircle(head, s * 0.42f, s * 0.06f, 0.8f)
        drawCircle(Color.Black, s * 0.08f, head + Offset(game.dir.dx * s * 0.12f, game.dir.dy * s * 0.12f))
    }
}

/** Бонуси змійки: золото — зірочка, равлик — спіраль, ножиці — два кола й хрест, серце. */
private fun DrawScope.drawSnakeBonus(b: SnakeGame.Bonus, at: Offset, s: Float) {
    val w = s * 0.07f
    val r = s * 0.36f
    when (b) {
        SnakeGame.Bonus.GOLD -> for (i in 0 until 5) {
            val a = -Math.PI.toFloat() / 2 + i * 2f * Math.PI.toFloat() / 5f
            pencilLine(at, at + Offset(cos(a) * r, sin(a) * r), w)
        }
        SnakeGame.Bonus.SLOW -> {
            val spiral = Path().apply {
                for (i in 0..24) {
                    val a = i * 0.5f
                    val rr = r * i / 24f
                    val pt = at + Offset(cos(a) * rr, sin(a) * rr)
                    if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
            }
            pencil(spiral, w)
        }
        SnakeGame.Bonus.SHRINK -> {
            pencilCircle(at + Offset(-r * 0.45f, r * 0.45f), r * 0.28f, w)
            pencilCircle(at + Offset(r * 0.45f, r * 0.45f), r * 0.28f, w)
            pencilLine(at + Offset(-r * 0.3f, r * 0.2f), at + Offset(r * 0.5f, -r * 0.7f), w)
            pencilLine(at + Offset(r * 0.3f, r * 0.2f), at + Offset(-r * 0.5f, -r * 0.7f), w)
        }
        SnakeGame.Bonus.LIFE -> {
            val heart = Path().apply {
                moveTo(at.x, at.y + r)
                cubicTo(at.x - r * 1.4f, at.y - r * 0.1f, at.x - r * 0.8f, at.y - r * 1.3f, at.x, at.y - r * 0.5f)
                cubicTo(at.x + r * 0.8f, at.y - r * 1.3f, at.x + r * 1.4f, at.y - r * 0.1f, at.x, at.y + r)
            }
            pencil(heart, w)
        }
    }
}

/** Сторінка «Ігри»: вмикач і три гри з рекордом, «Грати» / «Продовжити», «Почати заново». */
@Composable
fun GamesPage(
    onBack: () -> Unit,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    best: Map<GameKind, Int>,
    saved: Set<GameKind>,
    onPlay: (GameKind, Boolean) -> Unit,
) {
    SettingsPage(stringResource(R.string.games_title), onBack) {
        Card {
            SwitchRow(stringResource(R.string.games_enabled), stringResource(R.string.games_enabled_subtitle), enabled, onEnabled)
        }
        if (enabled) {
            GameKind.entries.forEach { kind ->
                Card {
                    Text(gameTitle(kind), color = Oled.TextPrimary, fontSize = 17.sp)
                    Note(
                        stringResource(
                            when (kind) {
                                GameKind.STARS -> R.string.game_stars_desc
                                GameKind.BLOCKS -> R.string.game_blocks_desc
                                GameKind.SNAKE -> R.string.game_snake_desc
                            },
                        ),
                    )
                    val record = best[kind] ?: 0
                    if (record > 0) Text(stringResource(R.string.game_best, record), color = Oled.TextSecondary, fontSize = 14.sp)
                    ActionOption(
                        if (kind in saved) R.string.game_continue else R.string.game_play,
                        Icons.Filled.PlayArrow,
                        Tone.Confirm,
                    ) { onPlay(kind, false) }
                    if (kind in saved) ActionOption(R.string.game_restart, Icons.Filled.Refresh) { onPlay(kind, true) }
                }
            }
            Note(stringResource(R.string.games_note))
        }
    }
}

/** Де поле на екрані (для дотиків у «Зоряному патрулі»). */
private class FieldView {
    var scale = 1f
    var origin = Offset.Zero
}
