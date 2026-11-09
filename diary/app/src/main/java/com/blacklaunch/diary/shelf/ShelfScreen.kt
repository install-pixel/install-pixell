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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
    var spineMenu by remember { mutableStateOf<DiaryInfo?>(null) }
    var rename by remember { mutableStateOf<DiaryInfo?>(null) }
    var erase by remember { mutableStateOf<DiaryInfo?>(null) }
    var newDiary by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf<Pair<android.net.Uri, Boolean>?>(null) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restore = uri to false }

    BackHandler { if (page) page = false else onExit() }
    // Сторінка налаштувань «перегортається» поверх полиці від корінця (лівого краю).
    val turn by animateFloatAsState(if (page) 1f else 0f, tween(if (page) 620 else 480, easing = FastOutSlowInEasing), label = "settingsPage")
    Column(Modifier.fillMaxSize().background(Oled.Black).statusBarsPadding().navigationBarsPadding().revealsGear(gear)) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
       Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.shelf), color = Oled.TextPrimary, fontSize = 20.sp, modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Column(
            Modifier.weight(1f).fillMaxWidth().bouncyScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            vm.shelf.chunked(PER_SHELF).forEachIndexed { row, books ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                        books.forEachIndexed { i, b ->
                            Spine(
                                b,
                                height = (196 + ((b.id.hashCode() and 0x7fffffff) % 5) * 8).dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClick = { haptics.tap(); vm.current = b.id },
                                        onLongClick = { haptics.longPress(); spineMenu = b },
                                    ),
                                seed = row * 10 + i,
                            )
                        }
                        repeat(PER_SHELF - books.size) { Spacer(Modifier.weight(1f)) }
                    }
                    ShelfPlank(row)
                }
            }
            WindowHint(stringResource(R.string.shelf_hint))
        }
       }
        if (turn > 0.01f) {
            SettingsBook(
                onLanguageChanged = onLanguageChanged,
                onNewDiary = { newDiary = true },
                onRestore = { openBackup.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .padding(8.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = -100f * (1f - turn)
                        cameraDistance = 14f * density
                        alpha = if (turn < 0.15f) turn / 0.15f else 1f
                    },
            )
        }
      }
        // Шестерня — там само, де в щоденнику; з'являється після дотику.
        Box(Modifier.fillMaxWidth().height(GearRow), contentAlignment = Alignment.Center) {
            GearSpot(gear) { page = !page }
        }
    }

    spineMenu?.let { b ->
        MenuDialog(b.name, listOf(
            MenuEntry(Glyph.Book, stringResource(R.string.open)) { vm.current = b.id },
            MenuEntry(Glyph.Edit, stringResource(R.string.rename)) { rename = b },
            MenuEntry(Glyph.Delete, stringResource(R.string.erase), Tone.Danger) { erase = b },
        ), onDismiss = { spineMenu = null })
    }
    rename?.let { b -> TextDialog(stringResource(R.string.rename), b.name, stringResource(R.string.diary_name_hint), Limits.DIARY_NAME, onDismiss = { rename = null }) { vm.rename(b.id, it) } }
    erase?.let { b ->
        ConfirmDialog(stringResource(R.string.erase_title, b.name), stringResource(R.string.erase_text), stringResource(R.string.erase_go), Tone.Danger, onDismiss = { erase = null }) { vm.erase(b.id) }
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
private fun Spine(info: DiaryInfo, height: androidx.compose.ui.unit.Dp, modifier: Modifier, seed: Int) {
    val glass = AppStyle.glass
    Box(modifier.height(height).clip(RoundedCornerShape(8.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val r = 8.dp.toPx()
            if (glass) {
                drawRoundRect(Brush.horizontalGradient(0f to Color(0xFF0A0A0A), 0.35f to Color(0xFF202020), 1f to Color(0xFF080808)), cornerRadius = CornerRadius(r))
                drawRoundRect(com.blacklaunch.diary.ui.theme.Oled.Glint, cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
                drawLine(Color(0x22FFFFFF), Offset(size.width * 0.3f, 6.dp.toPx()), Offset(size.width * 0.3f, size.height - 6.dp.toPx()), 1.dp.toPx())
                for (y in listOf(18.dp.toPx(), size.height - 18.dp.toPx())) drawLine(Color(0x33FFFFFF), Offset(4.dp.toPx(), y), Offset(size.width - 4.dp.toPx(), y), 1.dp.toPx())
            } else {
                drawRoundRect(Color(0xFF050505), cornerRadius = CornerRadius(r))
                drawPath(taperedPath(pencilLoop(size, r, seed * 17 + 3, 1.2.dp.toPx()), 2.dp.toPx()), SheetInk.copy(alpha = 0.85f))
                for ((k, y) in listOf(16.dp.toPx(), 22.dp.toPx(), size.height - 22.dp.toPx(), size.height - 16.dp.toPx()).withIndex()) {
                    pencilTick(Offset(4.dp.toPx(), y + sin(seed + k * 1f)), Offset(size.width - 4.dp.toPx(), y), 1.3.dp.toPx(), SheetInk.copy(alpha = 0.5f), seed + k)
                }
            }
        }
        // Назва — вертикально, знизу вгору (як на корінцях книжок).
        Text(
            info.name,
            style = TextStyle(
                fontFamily = if (glass) GlassFont else HandFont,
                fontWeight = if (glass) FontWeight.Light else FontWeight.Medium,
                fontSize = if (glass) 16.sp else 21.sp,
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
            GlyphIcon(Glyph.Lock, stringResource(R.string.locked), tint = Oled.TextSecondary, size = 16.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))
        }
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
