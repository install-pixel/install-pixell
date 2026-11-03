package com.blacklaunch.qrcodes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.data.QrCode
import com.blacklaunch.qrcodes.qr.CodeFormat
import com.blacklaunch.qrcodes.qr.Symbol
import com.blacklaunch.qrcodes.qr.codeFormat
import com.blacklaunch.qrcodes.qr.codeKind
import com.blacklaunch.qrcodes.ui.theme.Oled
import java.text.DateFormat
import java.util.Date

/**
 * Перша сторінка — «Вибране»: відзначені коди сіткою (на телефоні — у два стовпчики, на ширшому екрані — більше).
 * Дотик — код на весь екран. Довге натискання: відпустив на місці — меню; затиснув і ведеш — код їде за пальцем,
 * інші розступаються (так міняєш порядок); кинув на плашку вгорі — прибрати з вибраного (як «Прибрати» в Black Glass).
 */
@Composable
fun FavoritesPage(
    favorites: List<QrCode>?,
    onOpen: (QrCode) -> Unit,
    onMenu: (QrCode) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onUnfavorite: (QrCode) -> Unit,
    zone: DropZone,
) {
    val haptics = LocalHaptics.current
    if (favorites == null) return
    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.favorites_empty), color = Oled.TextSecondary, fontSize = 18.sp, textAlign = TextAlign.Center)
        }
        return
    }
    val grid = rememberLazyGridState()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var order by remember { mutableStateOf(favorites) }
    // Після перетягування тримаємо новий порядок, доки база його не поверне (інакше коди на мить стрибають назад).
    var pending by remember { mutableStateOf<List<Long>?>(null) }
    if (pending != null && favorites.map { it.id } == pending) pending = null
    if (draggingId == null && pending == null) order = favorites
    if (pending != null && draggingId == null) {
        val byId = favorites.associateBy { it.id }
        order = pending!!.mapNotNull { byId[it] } + favorites.filter { it.id !in pending!! }
    }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }

    fun itemAt(p: Offset) = grid.layoutInfo.visibleItemsInfo.firstOrNull {
        p.x >= it.offset.x && p.x <= it.offset.x + it.size.width && p.y >= it.offset.y && p.y <= it.offset.y + it.size.height
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            state = grid,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { gridOrigin = it.positionInRoot() }
                .fadingEdges(grid)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val item = itemAt(down.position) ?: return@awaitEachGesture
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val code = order.firstOrNull { it.id == item.key } ?: return@awaitEachGesture
                        haptics.longPress()
                        draggingId = code.id
                        zone.visible = true
                        grab = down.position - Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
                        finger = held.position
                        var moved = false
                        while (true) {
                            // Initial — щоб дотик після довгого натискання не відкрив ще й код на весь екран.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            event.changes.forEach { it.consume() }
                            if (!change.pressed) break
                            if ((change.position - held.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            finger = change.position
                            val over = zone.rect.contains(gridOrigin + finger)
                            if (over != zone.over) haptics.tick()
                            zone.over = over
                            // Міняємо місцями, лише коли палець у середині чужої клітинки (не на межі) — без сіпання.
                            val target = itemAt(finger)?.takeIf { t ->
                                val ix = t.size.width * 0.22f
                                val iy = t.size.height * 0.22f
                                finger.x >= t.offset.x + ix && finger.x <= t.offset.x + t.size.width - ix &&
                                    finger.y >= t.offset.y + iy && finger.y <= t.offset.y + t.size.height - iy
                            }
                            if (!over && target != null && target.key != code.id) {
                                val from = order.indexOfFirst { it.id == code.id }
                                val to = order.indexOfFirst { it.id == target.key }
                                if (from >= 0 && to >= 0) {
                                    // Сітка тримається за перший видимий код — без цього вона «їде» слідом за ним.
                                    grid.requestScrollToItem(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
                                    order = order.toMutableList().apply { add(to, removeAt(from)) }
                                    haptics.tick()
                                }
                            }
                        }
                        val dropped = zone.over
                        zone.over = false
                        zone.visible = false
                        when {
                            !moved -> onMenu(code)
                            dropped -> onUnfavorite(code)
                            order.map { it.id } != favorites.map { it.id } -> {
                                pending = order.map { it.id }
                                onReorder(order.map { it.id })
                            }
                        }
                        draggingId = null
                    }
                },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(order, key = { it.id }) { code ->
                val symbol = remember(code.content, code.format) { Symbol.encode(code.content, code.codeKind) }
                val dragged = code.id == draggingId
                Column(
                    Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .then(if (dragged) Modifier else Modifier.animateItem())
                        .graphicsLayer {
                            if (dragged) {
                                // Код — під пальцем, трохи більший і прозоріший (над плашкою — ще прозоріший).
                                val info = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == code.id }
                                if (info != null) {
                                    translationX = finger.x - grab.x - info.offset.x
                                    translationY = finger.y - grab.y - info.offset.y
                                }
                                scaleX = 1.06f
                                scaleY = 1.06f
                                alpha = if (zone.over) 0.5f else 0.9f
                            }
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { haptics.tap(); onOpen(code) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CodeImage(symbol, Modifier.fillMaxWidth(), seed = code.id.toInt(), barcode = code.codeFormat == CodeFormat.BARCODE)
                    Text(
                        code.title,
                        color = Oled.TextPrimary,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Плашка, на яку кидають код, щоб прибрати з вибраного. Малюється вгорі екрана (над вкладками, не на кодах). */
class DropZone {
    var visible by mutableStateOf(false)
    var over by mutableStateOf(false)
    var rect by mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
}

/** Сама плашка — того ж розміру й на тому ж місці, що й сповіщення. */
@Composable
fun DropZonePill(zone: DropZone, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = zone.visible,
            enter = fadeIn(tween(OPEN_MS)),
            exit = fadeOut(tween(CLOSE_MS)),
            modifier = Modifier.noticePlace(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .onGloballyPositioned { zone.rect = it.boundsInRoot().inflate(24f) }
                    .background(if (zone.over) Oled.Danger.copy(alpha = 0.25f) else Oled.Black.copy(alpha = 0.92f), RoundedCornerShape(28.dp))
                    .pencilFrame(28.dp, seed = 17, ink = if (zone.over) Oled.Danger else SheetInk),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, color = Oled.Danger, fontSize = 17.sp)
            }
        }
    }
}

/**
 * Друга сторінка — створення: зверху код того, що вводиш (живцем), під ним — збережені, внизу — капсула вводу.
 * Свайп уліво по збереженому — видалити, довге натискання — меню, дотик — на весь екран.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreatePage(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    mode: InputMode,
    onModeClick: () -> Unit,
    onSave: () -> Unit,
    saved: List<QrCode>?,
    folders: List<com.blacklaunch.qrcodes.data.Folder>,
    listActions: SavedListActions,
    focusRequester: FocusRequester,
    format: com.blacklaunch.qrcodes.qr.CodeKind,
    onFormatChange: (com.blacklaunch.qrcodes.qr.CodeKind) -> Unit,
    /** Пошук у збережених — у тій самій капсулі. */
    searching: Boolean = false,
    searchValue: TextFieldValue = TextFieldValue(""),
    onSearchChange: (TextFieldValue) -> Unit = {},
    onSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
) {
    val haptics = LocalHaptics.current
    val symbol = remember(input.text, format) { Symbol.encode(input.text, format) }
    val tooLong = input.text.isNotEmpty() && symbol == null
    val barcode = format.format == CodeFormat.BARCODE
    var kindPicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Що створюємо — вибирається раз тут і зберігається з кодом (у списку, на весь екран, на наклейці — так само).
        // Три однакові кнопки: QR-код, Штрихкод і вид кодування (для вибраного з них — «▾» відкриває список саме його видів).
        EqualWidthRow(Modifier.fillMaxWidth().padding(bottom = 10.dp), gap = 8.dp) {
            ChoiceChip(stringResource(R.string.format_qr), !barcode) { if (barcode) onFormatChange(com.blacklaunch.qrcodes.qr.CodeKind.QR) }
            ChoiceChip(stringResource(R.string.format_barcode), barcode) { if (!barcode) onFormatChange(com.blacklaunch.qrcodes.qr.CodeKind.AUTO) }
            ChoiceChip((if (format == com.blacklaunch.qrcodes.qr.CodeKind.AUTO) stringResource(R.string.kind_auto) else format.title) + " ▾", false) { kindPicker = true }
        }
        if (kindPicker) {
            KindPicker(format, onDismiss = { kindPicker = false }) {
                onFormatChange(it)
                kindPicker = false
            }
        }
        // Віконце сталого розміру — однакове для QR і штрихкоду (нічого не стрибає): біла картка, QR — квадратом по
        // центру, штрихкод — смугою на всю ширину.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val cardH = qrPreviewSide(maxWidth)
            val cardW = minOf(maxWidth * 0.86f, cardH * 1.35f)
            if (symbol != null) {
                Box(
                    Modifier
                        .size(cardW, cardH)
                        .pencilFrame(14.dp, seed = 3)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(QrPaper),
                    contentAlignment = Alignment.Center,
                ) {
                    CodeImage(
                        symbol,
                        if (barcode) Modifier.width(cardW - 32.dp) else Modifier.size(cardH - 28.dp),
                        framed = false,
                    )
                }
            } else {
                Box(Modifier.size(cardW, cardH).pencilFrame(14.dp, seed = 3, alpha = 0.35f).padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(
                            when {
                                !tooLong -> R.string.preview_hint
                                // Не підходить — підказка саме для цього виду (що він приймає).
                                format == com.blacklaunch.qrcodes.qr.CodeKind.AUTO -> R.string.barcode_invalid
                                format == com.blacklaunch.qrcodes.qr.CodeKind.QR -> R.string.too_long
                                else -> kindHint(format)
                            },
                        ),
                        color = if (tooLong) Oled.Danger else Oled.TextSecondary,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        SavedList(saved, folders, listActions, Modifier.weight(1f).fillMaxWidth(), query = if (searching) searchValue.text else "")
        InputCapsule(
            value = if (searching) searchValue else input,
            onValueChange = if (searching) onSearchChange else onInputChange,
            mode = mode,
            onModeClick = onModeClick,
            onClear = { if (searching) onSearchChange(TextFieldValue("")) else onInputChange(TextFieldValue("")) },
            onSave = onSave,
            canSave = symbol != null,
            focusRequester = focusRequester,
            modifier = Modifier.padding(top = 8.dp),
            searching = searching,
            onSearch = onSearch,
            onCloseSearch = onCloseSearch,
        )
    }
}

/** Ширина штрихкоду вгорі «Створити» і на весь екран: ширший за QR, щоб смуги не були надто тонкими. */
fun barPreviewWidth(available: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = minOf(available * 0.92f, 360.dp)

/** Розмір коду вгорі сторінки «Створити» — такий самий і в коду на весь екран (зручно для сканера). */
fun qrPreviewSide(available: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = minOf(available * 0.62f, 260.dp)

/** Рядок збереженого коду: маленький код, назва (зміст ховається, якщо є своя назва), дата, зірка вибраного. */
@Composable
internal fun SavedRow(code: QrCode, modifier: Modifier) {
    val symbol = remember(code.content, code.format) { Symbol.encode(code.content, code.codeKind) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Oled.Black)
            .then(modifier)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(60.dp), contentAlignment = Alignment.Center) {
            val bars = code.codeFormat == CodeFormat.BARCODE
            CodeImage(symbol, Modifier.width(if (bars) 60.dp else 48.dp), seed = code.id.toInt(), radius = 8.dp, barcode = bars)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(code.title, color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(code.createdAt)),
                color = Oled.TextSecondary,
                fontSize = 13.sp,
            )
        }
        if (code.isFavorite) {
            GlyphIcon(Glyph.Star, stringResource(R.string.in_favorites), tint = Oled.TextSecondary, size = 20.dp)
        }
    }
}
