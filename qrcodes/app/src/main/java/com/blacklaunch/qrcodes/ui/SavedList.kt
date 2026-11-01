package com.blacklaunch.qrcodes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.data.Folder
import com.blacklaunch.qrcodes.data.ListDrop
import com.blacklaunch.qrcodes.data.QrCode
import com.blacklaunch.qrcodes.ui.theme.Oled

/** Рядок списку збережених: папка або код. */
private sealed interface Entry {
    val key: String
    val order: Long

    data class FolderEntry(val folder: Folder, val codes: List<QrCode>) : Entry {
        override val key get() = ListDrop.folderKey(folder.id)
        override val order get() = folder.sortOrder
    }

    data class CodeEntry(val code: QrCode) : Entry {
        override val key get() = ListDrop.codeKey(code.id)
        override val order get() = code.sortOrder
    }
}

/** Що робити, коли рядок перетягнули. */
class SavedListActions(
    val onOpen: (QrCode) -> Unit,
    val onCodeMenu: (QrCode) -> Unit,
    val onFolderMenu: (Folder) -> Unit,
    val onDelete: (QrCode) -> Unit,
    /** Новий порядок (ключі «f…»/«c…» зверху вниз). */
    val onReorder: (List<String>) -> Unit,
    /** Код [dragged] кинули на код [target] — нова папка з обох. */
    val onCombine: (dragged: QrCode, target: QrCode) -> Unit,
    /** Код кинули на папку. */
    val onIntoFolder: (QrCode, Folder) -> Unit,
)

/**
 * Список збережених кодів із папками. Дотик — код на весь екран (папка — відкрити). Довге натискання: відпустив на
 * місці — меню; затиснув і ведеш — код їде за пальцем: на середину іншого коду — нова папка з обох, на папку — у неї,
 * між рядками (синя лінія) — на це місце. Свайп уліво (довгий) — видалити. Угорі — пошук за назвою й змістом.
 */
@Composable
fun SavedList(
    saved: List<QrCode>?,
    folders: List<Folder>,
    actions: SavedListActions,
    modifier: Modifier = Modifier,
    /** Що шукаємо (з капсули внизу); порожньо — звичайний список. */
    query: String = "",
) {
    val haptics = LocalHaptics.current
    var openFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    val codes = saved.orEmpty()
    val openFolder = folders.firstOrNull { it.id == openFolderId }
    if (openFolderId != null && openFolder == null) openFolderId = null
    BackHandler(enabled = openFolder != null) { openFolderId = null }

    val q = query.trim()
    val entries: List<Entry> = when {
        q.isNotEmpty() -> codes.filter { it.content.contains(q, ignoreCase = true) || it.name.orEmpty().contains(q, ignoreCase = true) }.map { Entry.CodeEntry(it) }
        openFolder != null -> codes.filter { it.folderId == openFolder.id }.map { Entry.CodeEntry(it) }
        else -> {
            val byFolder = codes.filter { it.folderId != null }.groupBy { it.folderId }
            (folders.map { Entry.FolderEntry(it, byFolder[it.id].orEmpty()) } + codes.filter { it.folderId == null || folders.none { f -> f.id == it.folderId } }.map { Entry.CodeEntry(it) })
                .sortedByDescending { it.order }
        }
    }
    // Перетягувати можна лише у звичайному списку чи в папці (не в результатах пошуку).
    val canDrag = q.isEmpty()

    Column(modifier) {
        // Заголовок: у папці — «назад», назва, меню папки; інакше — скільки збережено (чи знайдено).
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (openFolder != null) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { haptics.tap(); openFolderId = null }, contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.Back, stringResource(R.string.back), tint = Oled.TextSecondary)
                }
                GlyphIcon(Glyph.Folder, null, tint = Oled.TextSecondary, size = 20.dp)
                Text(openFolder.name, color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { haptics.tap(); actions.onFolderMenu(openFolder) }, contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.More, stringResource(R.string.more), tint = Oled.TextSecondary)
                }
            } else {
                Text(
                    if (q.isNotEmpty()) stringResource(R.string.search_found, entries.size) else stringResource(R.string.saved_count, codes.size),
                    color = Oled.TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp).weight(1f),
                )
            }
        }

        val list = rememberLazyListState()
        var draggingKey by remember { mutableStateOf<String?>(null) }
        var fingerY by remember { mutableStateOf(0f) }
        var grabY by remember { mutableStateOf(0f) }
        var dropKey by remember { mutableStateOf<String?>(null) }
        var dropKind by remember { mutableStateOf(ListDrop.Kind.Before) }
        val current by androidx.compose.runtime.rememberUpdatedState(entries)
        // Розмиття решти списку, поки щось тягнуть: з'являється й зникає плавно.
        val blurUnder = androidx.compose.animation.core.animateFloatAsState(
            if (draggingKey != null) 1f else 0f,
            androidx.compose.animation.core.tween(if (draggingKey != null) OPEN_MS else CLOSE_MS),
            label = "blurUnder",
        )
        val inFolder by androidx.compose.runtime.rememberUpdatedState(openFolder != null)

        LazyColumn(
            state = list,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fadingEdges()
                .then(
                    if (!canDrag) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { down.position.y >= it.offset && down.position.y <= it.offset + it.size }
                                    ?: return@awaitEachGesture
                                val entry = current.firstOrNull { it.key == item.key } ?: return@awaitEachGesture
                                val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                                haptics.longPress()
                                draggingKey = entry.key
                                grabY = down.position.y - item.offset
                                fingerY = held.position.y
                                var moved = false
                                while (true) {
                                    // Initial — щоб після довгого натискання не спрацював ще й дотик / свайп рядка.
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    event.changes.forEach { it.consume() }
                                    if (!change.pressed) break
                                    if (kotlin.math.abs(change.position.y - held.position.y) > viewConfiguration.touchSlop) moved = true
                                    fingerY = change.position.y
                                    val target = list.layoutInfo.visibleItemsInfo.firstOrNull { fingerY >= it.offset && fingerY <= it.offset + it.size && it.key != entry.key }
                                    val targetEntry = target?.let { t -> current.firstOrNull { it.key == t.key } }
                                    if (target != null && targetEntry != null && moved) {
                                        // Класти «в» можна лише код (не папку) і лише в загальному списку.
                                        val canInto = entry is Entry.CodeEntry && !inFolder
                                        val kind = ListDrop.decide(fingerY, target.offset.toFloat(), target.size.toFloat(), canInto)
                                        if (dropKey != targetEntry.key || dropKind != kind) haptics.tick()
                                        dropKey = targetEntry.key
                                        dropKind = kind
                                    } else if (target == null) {
                                        dropKey = null
                                    }
                                }
                                val target = dropKey?.let { k -> current.firstOrNull { it.key == k } }
                                val kind = dropKind
                                draggingKey = null
                                dropKey = null
                                when {
                                    !moved -> when (entry) {
                                        is Entry.CodeEntry -> actions.onCodeMenu(entry.code)
                                        is Entry.FolderEntry -> actions.onFolderMenu(entry.folder)
                                    }
                                    target == null -> Unit
                                    kind == ListDrop.Kind.Into && entry is Entry.CodeEntry && target is Entry.CodeEntry -> actions.onCombine(entry.code, target.code)
                                    kind == ListDrop.Kind.Into && entry is Entry.CodeEntry && target is Entry.FolderEntry -> actions.onIntoFolder(entry.code, target.folder)
                                    else -> actions.onReorder(ListDrop.move(current.map { it.key }, entry.key, target.key, after = kind == ListDrop.Kind.After))
                                }
                            }
                        }
                    },
                ),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (saved != null && entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (q.isNotEmpty()) R.string.search_none else R.string.saved_empty),
                        color = Oled.TextSecondary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
            items(entries, key = { it.key }) { entry ->
                val dragged = entry.key == draggingKey
                val dropHere = entry.key == dropKey
                Box(
                    Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .then(if (dragged) Modifier else Modifier.animateItem())
                        .graphicsLayer {
                            if (dragged) {
                                val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.key }
                                if (info != null) translationY = fingerY - grabY - info.offset
                                scaleX = 1.02f
                                scaleY = 1.02f
                                // Той, що тягнеш, — напівпрозорий: краще видно, куди ляже (над «Нова папка» — ще прозоріший).
                                alpha = if (dropKey != null && dropKind == ListDrop.Kind.Into) 0.4f else 0.6f
                            }
                        }
                        // Куди ляже — малюємо поверх рядка (інакше чорне тло рядка закриває підказку).
                        .drawWithContent {
                            drawContent()
                            if (dropHere && dropKind != ListDrop.Kind.Into) {
                                val y = if (dropKind == ListDrop.Kind.Before) 1.5.dp.toPx() else size.height - 1.5.dp.toPx()
                                val r = 5.dp.toPx()
                                drawCircle(Oled.Confirm, r, Offset(r, y))
                                drawLine(Oled.Confirm, Offset(r, y), Offset(size.width, y), 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            }
                        },
                ) {
                    // Поки тягнуть інший рядок — цей трохи розмитий (плавно), щоб той, що переноситься, і підказки
                    // було видно чітко. Підказки «Нова папка» / синя лінія — поверх, не розмиваються.
                    Box(
                        Modifier.graphicsLayer {
                            val r = if (dragged) 0f else BLUR_UNDER.toPx() * blurUnder.value
                            renderEffect = if (r > 0.5f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal)
                            } else {
                                null
                            }
                        },
                    ) {
                        when (entry) {
                            is Entry.FolderEntry -> FolderRow(entry.folder, entry.codes.size) {
                                haptics.tap()
                                openFolderId = entry.folder.id
                            }
                            is Entry.CodeEntry -> SwipeRow(entry.code, actions)
                        }
                    }
                    // Код кинуть «у цей рядок»: синя підсвітка, товста рамка й напис — що саме станеться.
                    if (dropHere && dropKind == ListDrop.Kind.Into) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(Oled.Confirm.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                                .border(2.5.dp, Oled.Confirm, RoundedCornerShape(12.dp))
                                .padding(end = 14.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Row(
                                Modifier.background(Oled.Black.copy(alpha = 0.85f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GlyphIcon(if (entry is Entry.FolderEntry) Glyph.Folder else Glyph.Plus, null, tint = Oled.Confirm, size = 20.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(if (entry is Entry.FolderEntry) R.string.folder_drop_into else R.string.folder_drop_new),
                                    color = Oled.Confirm,
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Рядок папки: значок, назва, скільки кодів. */
@Composable
private fun FolderRow(folder: Folder, count: Int, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Oled.Black)
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(60.dp).size(width = 60.dp, height = 48.dp).pencilFrame(10.dp, seed = folder.id.toInt() + 5, alpha = 0.6f), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.Folder, null, size = 28.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.folder_count, count), color = Oled.TextSecondary, fontSize = 13.sp)
        }
    }
}

/**
 * Рядок коду зі свайпом уліво «Видалити». Реагує лише на рух уліво (управо — гортаються сторінки «Вибране» /
 * «Створити»). Видаляє лише довгий свайп (≈ половина ширини); поки тягнеш, рядок біля краю м'яко розчиняється (як
 * список угорі й унизу), а «Видалити» проявляється за ним. Відпустив раніше — рядок пружно вертається.
 */
@Composable
private fun SwipeRow(code: QrCode, actions: SavedListActions) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableStateOf(1f) }
    val fade = with(LocalDensity.current) { 56.dp.toPx() }
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        // Під рядком — «Видалити», проявляється разом із рухом.
        val progress = (-offset.value / (width * DELETE_SWIPE)).coerceIn(0f, 1f)
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .graphicsLayer {
                    alpha = progress
                    val sc = 0.85f + 0.15f * progress
                    scaleX = sc
                    scaleY = sc
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.delete), color = if (progress >= 1f) Oled.Danger else Oled.Danger.copy(alpha = 0.75f), fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
            GlyphIcon(Glyph.Delete, null, tint = Oled.Danger)
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                // Рядок лишається непрозорим (нічого не просвічує): краї й увесь рядок затемнюються в чорне разом із
                // рухом — зірочка й текст біля краю «зафарбовуються», а не стають прозорими.
                .drawWithContent {
                    drawContent()
                    val moved = -offset.value
                    if (moved > 0.5f) {
                        val progress = (moved / (width * DELETE_SWIPE)).coerceIn(0f, 1f)
                        val f = minOf(fade, moved * 1.5f)
                        drawRect(Color.Black.copy(alpha = 0.35f * progress))
                        drawRect(
                            Brush.horizontalGradient(
                                // Лівий край — там, де рядок зараз виходить за край списку.
                                0f to Color.Black,
                                (moved / size.width).coerceIn(0f, 1f) to Color.Black,
                                ((moved + f) / size.width).coerceIn(0f, 1f) to Color.Transparent,
                                ((size.width - f) / size.width).coerceIn(0f, 1f) to Color.Transparent,
                                1f to Color.Black,
                            ),
                        )
                    }
                }
                .pointerInput(code.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var start = 0f
                        // Лише вліво: рух управо не чіпаємо — його забирають сторінки.
                        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            if (over < 0f) {
                                change.consume()
                                start = over
                            }
                        } ?: return@awaitEachGesture
                        var reached = false
                        scope.launch { offset.snapTo((offset.value + start).coerceIn(-width, 0f)) }
                        horizontalDrag(drag.id) { change ->
                            val next = (offset.value + change.positionChange().x).coerceIn(-width, 0f)
                            val now = -next >= width * DELETE_SWIPE
                            if (now != reached) haptics.tick()
                            reached = now
                            scope.launch { offset.snapTo(next) }
                            change.consume()
                        }
                        scope.launch {
                            if (-offset.value >= width * DELETE_SWIPE) {
                                offset.animateTo(-width, tween(CLOSE_MS))
                                actions.onDelete(code)
                            } else {
                                offset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    }
                }
                .clickable { haptics.tap(); actions.onOpen(code) },
        ) {
            // Рядок (з чорним тлом) рухається й тане весь, разом зі своїм тлом.
            SavedRow(code, Modifier)
        }
    }
}

/** Наскільки розмиваються інші рядки, поки тягнуть один. */
private val BLUR_UNDER = 3.dp

/** Яку частку ширини треба протягнути рядок, щоб видалити. */
private const val DELETE_SWIPE = 0.45f
