package com.blacklaunch.diary.shelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.DiaryViewModel
import com.blacklaunch.diary.R
import com.blacklaunch.diary.data.DiaryInfo
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.ConfirmDialog
import com.blacklaunch.diary.ui.GlassFont
import com.blacklaunch.diary.ui.Glyph
import com.blacklaunch.diary.ui.GlyphIcon
import com.blacklaunch.diary.ui.HandFont
import com.blacklaunch.diary.ui.Limits
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.MenuDialog
import com.blacklaunch.diary.ui.MenuEntry
import com.blacklaunch.diary.ui.PasswordDialog
import com.blacklaunch.diary.ui.SettingsBook
import com.blacklaunch.diary.book.GearRow
import com.blacklaunch.diary.book.GearSpot
import com.blacklaunch.diary.book.MenuEntries
import com.blacklaunch.diary.book.PaperPage
import com.blacklaunch.diary.book.rememberGearReveal
import com.blacklaunch.diary.book.revealsGear
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.TextDialog
import com.blacklaunch.diary.ui.Tone
import com.blacklaunch.diary.ui.WindowHint
import com.blacklaunch.diary.ui.bouncyScroll
import com.blacklaunch.diary.ui.pencilLoop
import com.blacklaunch.diary.ui.pencilTick
import com.blacklaunch.diary.ui.quadPoints
import com.blacklaunch.diary.ui.taperedPath
import com.blacklaunch.diary.ui.theme.Oled
import kotlin.math.sin

/** Скільки корінців на одній полиці. */
private const val PER_SHELF = 4

/**
 * Полиця щоденників (коли їх більше одного): корінці з назвами, у закритих на код — замок. Дотик — дістати щоденник
 * (обкладинка на весь екран), довге натискання — меню корінця. Шестерня (там само, де в щоденнику) — сторінка загальних
 * налаштувань для всіх щоденників: новий щоденник, відновити з копії, стиль, мова, екран, довідка.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfScreen(vm: DiaryViewModel, onLanguageChanged: () -> Unit, onExit: () -> Unit) {
    val haptics = LocalHaptics.current
    var page by remember { mutableStateOf(false) }
    val gear = rememberGearReveal()
    var newDiary by remember { mutableStateOf(false) }
    // Загальна копія: коди щоденників із замком (питаємо по черзі; не дав — щоденник не ввійде), потім пароль копії.
    var askCode by remember { mutableStateOf<List<DiaryInfo>?>(null) }
    val codes = remember { HashMap<String, String>() }
    var askBackupPw by remember { mutableStateOf(false) }
    var backupPw by remember { mutableStateOf<String?>(null) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = backupPw
        backupPw = null
        if (uri != null && pw != null) vm.backupAll(uri, pw, HashMap(codes))
        codes.clear()
    }
    fun startBackup() {
        codes.clear()
        val locked = vm.shelf.filter { it.locked }
        if (locked.isEmpty()) askBackupPw = true else askCode = locked
    }
    var restore by remember { mutableStateOf<Pair<android.net.Uri, Boolean>?>(null) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restore = uri to false }

    // Загальні налаштування — сторінка, що за загальним правилом гортання (валик) лягає від корінця поверх полиці:
    // сторінка 0 — налаштування, сторінка 1 — «порожня» (крізь неї видно полицю).
    val turn = remember { com.blacklaunch.diary.book.FlipState(1) }
    val scope = rememberCoroutineScope()
    // Поки налаштування закриті, їхньої (порожньої) сторінки немає зовсім — дотики йдуть до корінців.
    var settingsShown by remember { mutableStateOf(false) }
    fun toggleSettings() {
        if (turn.moving) return
        scope.launch {
            if (turn.index == 0) {
                turn.flipTo(1)
                settingsShown = false
            } else {
                settingsShown = true
                // Кадр-два, щоб сторінка встигла стати на місце, — тоді вона лягає гортанням.
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }
                turn.flipTo(0)
            }
        }
    }
    BackHandler { if (turn.index == 0) toggleSettings() else onExit() }
    fun diarySettings(id: String) { vm.settingsFor = id; vm.current = id }
    Column(Modifier.fillMaxSize().background(Oled.Black).statusBarsPadding().navigationBarsPadding().revealsGear(gear)) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
        // Полиця на всю зону щоденника: корінці на всю висоту, гортається вбік; під кожним — шестерня.
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)) {
            // Дошка — під корінцями (книжки стоять на ній), шестерні — під дошкою.
            Box(Modifier.align(Alignment.BottomCenter)) { ShelfPlank(0) }
            Row(
                Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                vm.shelf.forEachIndexed { i, b ->
                    Column(Modifier.width(78.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spine(
                            b,
                            height = androidx.compose.ui.unit.Dp.Unspecified,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = (((b.id.hashCode() and 0x7fffffff) % 5) * 6).dp)
                                .combinedClickable(
                                    onClick = { haptics.tap(); vm.current = b.id },
                                    // Довге натискання — налаштування цього щоденника (та сама сторінка, що й із загальних).
                                    onLongClick = { haptics.longPress(); diarySettings(b.id) },
                                ),
                            seed = i,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }
        if (settingsShown) com.blacklaunch.diary.book.FlipBook(
            state = turn,
            count = 2,
            enabled = false,
            modifier = Modifier.fillMaxSize().padding(8.dp),
            canFlipForward = { false },
        ) { i ->
            if (i == 0) {
                SettingsBook(
                    onLanguageChanged = onLanguageChanged,
                    onNewDiary = { newDiary = true },
                    onRestore = { openBackup.launch(arrayOf("*/*")) },
                    onBackup = { startBackup() },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(com.blacklaunch.diary.book.BookCorner)).background(Color.Black),
                    diaries = vm.shelf.map { it.id to it.name },
                    onDiarySettings = { diarySettings(it) },
                )
            }
        }
      }
        // Шестерня посередині — загальні налаштування (там само, де в щоденнику); з'являється після дотику.
        Box(Modifier.fillMaxWidth().height(GearRow), contentAlignment = Alignment.Center) {
            GearSpot(gear) { toggleSettings() }
        }
    }

    askCode?.firstOrNull()?.let { b ->
        fun next() { askCode = askCode?.drop(1)?.takeIf { it.isNotEmpty() }; if (askCode == null) askBackupPw = true }
        // Скасувати — цей щоденник не ввійде в копію.
        TextDialog(stringResource(R.string.backup_code_title, b.name), "", "0000", 4, onDismiss = { next() }, hint = stringResource(R.string.backup_code_hint)) { code ->
            codes[b.id] = code.filter { it.isDigit() }
            next()
        }
    }
    if (askBackupPw) {
        PasswordDialog(restore = false, wrong = false, onDismiss = { askBackupPw = false; codes.clear() }) { pw ->
            askBackupPw = false
            backupPw = pw
            val stamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").format(java.time.LocalDate.now())
            vm.keepOpenOnce = true
            createBackup.launch("diaries-$stamp.diary")
        }
    }
    if (newDiary) {
        TextDialog(stringResource(R.string.new_diary), "", stringResource(R.string.diary_name_hint), Limits.DIARY_NAME, onDismiss = { newDiary = false }, hint = stringResource(R.string.new_diary_hint), allowEmpty = true) {
            vm.current = vm.createDiary(it).id
        }
    }
    restore?.let { (uri, wrong) ->
        PasswordDialog(restore = true, wrong = wrong, onDismiss = { restore = null }) { pw -> vm.restore(uri, pw) { restore = uri to true } }
    }
}

/** Корінець щоденника: «Скло» — скляний брусок із гранями; «Аркуш» — обведений олівцем, зі смужками-палітурками. */
@Composable
private fun Spine(
    info: DiaryInfo,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    seed: Int,
) {
    val glass = AppStyle.glass
    Box(modifier.then(if (height != androidx.compose.ui.unit.Dp.Unspecified) Modifier.height(height) else Modifier).clip(RoundedCornerShape(8.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val r = 8.dp.toPx()
            if (glass) {
                drawRoundRect(Brush.horizontalGradient(0f to Color(0xFF0A0A0A), 0.35f to Color(0xFF202020), 1f to Color(0xFF080808)), cornerRadius = CornerRadius(r))
                drawRoundRect(com.blacklaunch.diary.ui.theme.Oled.Glint, cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
                drawLine(Color(0x22FFFFFF), Offset(size.width * 0.3f, 6.dp.toPx()), Offset(size.width * 0.3f, size.height - 6.dp.toPx()), 1.dp.toPx())
                for (y in listOf(18.dp.toPx(), size.height - 18.dp.toPx())) drawLine(Color(0x33FFFFFF), Offset(4.dp.toPx(), y), Offset(size.width - 4.dp.toPx(), y), 1.dp.toPx())
            } else {
                // «Аркуш»: палітурка з того самого паперу, що й сторінки (тон і волокна), але своя — без заломів і
                // вм'ятин: рівні поздовжні волокна, ледь опуклий корінець (темніше до країв), потерті кути.
                drawRoundRect(com.blacklaunch.diary.book.PaperTexture.PAGE_BASE, cornerRadius = CornerRadius(r))
                SpineTexture.get()?.let { tex ->
                    drawImage(tex, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt() + 1, size.height.toInt() + 1), filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
                }
                drawRoundRect(Brush.horizontalGradient(0f to Color(0x66000000), 0.18f to Color(0x14000000), 0.42f to Color(0x0AFFFFFF), 0.7f to Color(0x10000000), 1f to Color(0x70000000)), cornerRadius = CornerRadius(r))
                drawPath(taperedPath(pencilLoop(size, r, seed * 17 + 3, 1.2.dp.toPx()), 2.dp.toPx()), SheetInk.copy(alpha = 0.85f))
                // Смужки-палітурки згори й знизу — потерті: шматками різної сили, подекуди стерті зовсім (там корінець
                // найбільше тримають пальцями).
                for ((k, y) in listOf(16.dp.toPx(), 22.dp.toPx(), size.height - 22.dp.toPx(), size.height - 16.dp.toPx()).withIndex()) {
                    val rnd = java.util.Random((seed * 31 + k * 7).toLong())
                    val x0 = 4.dp.toPx()
                    val x1 = size.width - 4.dp.toPx()
                    var x = x0
                    while (x < x1) {
                        val len = (x1 - x0) * (0.08f + rnd.nextFloat() * 0.22f)
                        val e = minOf(x + len, x1)
                        val worn = rnd.nextFloat()
                        // Чверть — стерто майже до нуля, решта — від ледь видного до чіткого.
                        val a = if (worn < 0.25f) 0.06f else 0.18f + 0.34f * rnd.nextFloat()
                        val yy = y + sin(seed + k * 1f) * (1f - (x - x0) / (x1 - x0))
                        pencilTick(Offset(x, yy), Offset(e, y), (0.8f + rnd.nextFloat() * 0.6f).dp.toPx(), SheetInk.copy(alpha = a), seed + k + x.toInt())
                        x = e + rnd.nextFloat() * 2.dp.toPx()
                    }
                }
            }
        }
        // Назва — вертикально, знизу вгору (як на корінцях книжок).
        Text(
            info.name,
            style = TextStyle(
                fontFamily = if (glass) GlassFont else HandFont,
                fontWeight = if (glass) FontWeight.Light else FontWeight.Medium,
                fontSize = if (glass) 20.sp else 26.sp,
                letterSpacing = if (glass) 1.5.sp else 0.sp,
                color = Oled.TextPrimary,
                shadow = if (glass) Shadow(Color(0x59FFFFFF), blurRadius = 12f) else null,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .vertical()
                .rotate(-90f)
                .padding(horizontal = 30.dp),
        )
        if (info.locked) {
            GlyphIcon(Glyph.Lock, stringResource(R.string.locked), tint = Oled.TextSecondary, size = 16.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp))
        }
    }
}

/**
 * Фактура корінця для «Аркуша»: тон паперу сторінок, дрібне зерно й рівні поздовжні волокна (як у палітурного паперу),
 * подекуди світліші потертості. Малюється один раз (сталий візерунок) і розтягується на корінець.
 */
private object SpineTexture {
    private var tex: androidx.compose.ui.graphics.ImageBitmap? = null
    fun get(): androidx.compose.ui.graphics.ImageBitmap? {
        tex?.let { return it }
        val w = 96
        val h = 480
        val base = com.blacklaunch.diary.book.PaperTexture.PAGE_BASE
        val br = (base.red * 255).toInt(); val bg = (base.green * 255).toInt(); val bb = (base.blue * 255).toInt()
        val rnd = java.util.Random(4242)
        // Поздовжні волокна: для кожного стовпчика — своя ледь інша яскравість, що повільно «пливе» вздовж.
        val col = FloatArray(w) { (rnd.nextFloat() - 0.5f) * 7f }
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val drift = kotlin.math.sin((y * 0.021f) + x * 0.37f) * 1.6f
                val v = col[x] + drift + (rnd.nextFloat() - 0.5f) * 6f
                val r = (br + v).toInt().coerceIn(0, 255)
                val g = (bg + v).toInt().coerceIn(0, 255)
                val b = (bb + v * 0.9f).toInt().coerceIn(0, 255)
                px[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        // Довгі тонкі волокна, світліші й темніші.
        repeat(70) {
            val x0 = rnd.nextInt(w)
            val y0 = rnd.nextInt(h)
            val len = 30 + rnd.nextInt(140)
            val d = if (rnd.nextBoolean()) 9 else -7
            var x = x0.toFloat()
            for (k in 0 until len) {
                val y = y0 + k
                if (y >= h) break
                x += (rnd.nextFloat() - 0.5f) * 0.35f
                val xi = x.toInt().coerceIn(0, w - 1)
                val c = px[y * w + xi]
                fun ch(sh: Int) = (((c shr sh) and 0xFF) + d).coerceIn(0, 255)
                px[y * w + xi] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
        val bmp = android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888)
        return bmp.asImageBitmap().also { tex = it }
    }
}

/** Повернутий на 90° текст займає місце по висоті, а не по ширині. */
private fun Modifier.vertical(): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(p.height, p.width) { p.place(-(p.width - p.height) / 2, (p.width - p.height) / 2) }
}

/** Дошка полиці: скляна грань або дві лінії олівцем. */
@Composable
private fun ShelfPlank(seed: Int) {
    val glass = AppStyle.glass
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        if (glass) {
            drawRoundRect(Brush.verticalGradient(0f to Color(0xFF262626), 1f to Color(0xFF080808)), cornerRadius = CornerRadius(4.dp.toPx()))
            drawRoundRect(com.blacklaunch.diary.ui.theme.Oled.Glint, cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
        } else {
            drawPath(taperedPath(quadPoints(Offset(0f, 2.dp.toPx()), Offset(size.width / 2, 3.dp.toPx() + sin(seed * 1f) * 2), Offset(size.width, 1.5.dp.toPx()), 48), 2.4.dp.toPx()), SheetInk.copy(alpha = 0.85f))
            drawPath(taperedPath(quadPoints(Offset(6.dp.toPx(), size.height - 3.dp.toPx()), Offset(size.width / 2, size.height - 2.dp.toPx()), Offset(size.width - 6.dp.toPx(), size.height - 3.5.dp.toPx()), 48), 1.4.dp.toPx()), SheetInk.copy(alpha = 0.4f))
        }
    }
}
