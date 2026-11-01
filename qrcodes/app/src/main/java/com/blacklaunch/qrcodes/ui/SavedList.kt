package com.blacklaunch.qrcodes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
) {
    val haptics = LocalHaptics.current
    var openFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val codes = saved.orEmpty()
    val openFolder = folders.firstOrNull { it.id == openFolderId }
    if (openFolderId != null && openFolder == null) openFolderId = null
    BackHandler(enabled = openFolder != null || searchOpen) {
        if (searchOpen) {
            searchOpen = false
            query = ""
        } else {
            openFolderId = null
        }
    }

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
        // Заголовок: у папці — «назад», назва, меню папки; інакше — кількість і пошук.
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
            } else if (searchOpen) {
                SheetTextField(query, { query = it }, stringResource(R.string.search_hint), modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { haptics.tap(); searchOpen = false; query = "" }, contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.Close, stringResource(R.string.close), tint = Oled.TextSecondary)
                }
            } else {
                Text(
                    stringResource(R.string.saved_count, codes.size),
                    color = Oled.TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 4.dp).weight(1f),
                )
                if (codes.isNotEmpty()) {
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable { haptics.tap(); searchOpen = true }, contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.Search, stringResource(R.string.search), tint = Oled.TextSecondary)
                    }
                }
            }
        }

        val list = rememberLazyListState()
        var draggingKey by remember { mutableStateOf<String?>(null) }
        var fingerY by remember { mutableStateOf(0f) }
        var grabY by remember { mutableStateOf(0f) }
        var dropKey by remember { mutableStateOf<String?>(null) }
        var dropKind by remember { mutableStateOf(ListDrop.Kind.Before) }
        val current by androidx.compose.runtime.rememberUpdatedState(entries)
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
                                alpha = 0.92f
                            }
                        }
                        // Куди ляже: на середину — рамка «в цей рядок», між — синя лінія зверху чи знизу.
                        .then(
                            when {
                                !dropHere -> Modifier
                                dropKind == ListDrop.Kind.Into -> Modifier.pencilFrame(12.dp, seed = 88, ink = Oled.Confirm)
                                else -> Modifier.drawBehind {
                                    val y = if (dropKind == ListDrop.Kind.Before) 0f else size.height
                                    drawLine(Oled.Confirm, Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
                                }
                            },
                        ),
                ) {
                    when (entry) {
                        is Entry.FolderEntry -> FolderRow(entry.folder, entry.codes.size) {
                            haptics.tap()
                            openFolderId = entry.folder.id
                        }
                        is Entry.CodeEntry -> SwipeRow(entry.code, actions)
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
 * Рядок коду зі свайпом уліво «Видалити»: лише довгий свайп (≈ половина ширини). Поки тягнеш — від краю рядка м'яка
 * тінь, напис «Видалити» проявляється разом із рухом; відпустив раніше — рядок плавно вертається.
 */
@Composable
private fun SwipeRow(code: QrCode, actions: SavedListActions) {
    val haptics = LocalHaptics.current
    val rowWidth = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    var stateRef: SwipeToDismissBoxState? = null
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            val pulled = runCatching { -(stateRef?.requireOffset() ?: 0f) }.getOrDefault(0f)
            if (it == SwipeToDismissBoxValue.EndToStart && pulled >= rowWidth * DELETE_SWIPE) {
                actions.onDelete(code)
                true
            } else {
                false
            }
        },
        positionalThreshold = { total -> total * DELETE_SWIPE },
    )
    stateRef = state
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val pulled = runCatching { -state.requireOffset() }.getOrDefault(0f).coerceAtLeast(0f)
            val progress = (pulled / (rowWidth * DELETE_SWIPE)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    val edge = size.width - pulled
                    // Червонуватий відблиск з'являється разом із рухом.
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Oled.Danger.copy(alpha = 0.22f * progress)), startX = edge, endX = size.width),
                    )
                    // М'яка тінь від краю рядка — замість різкого обрізу.
                    val shadow = 36.dp.toPx()
                    drawRect(
                        Brush.horizontalGradient(listOf(Oled.Black, Color.Transparent), startX = edge, endX = edge + shadow),
                        topLeft = Offset(edge, 0f),
                        size = androidx.compose.ui.geometry.Size(shadow.coerceAtMost(size.width - edge + 1f), size.height),
                    )
                }
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 20.dp)
                        .graphicsLayer {
                            alpha = progress
                            val s = 0.85f + 0.15f * progress
                            scaleX = s
                            scaleY = s
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.delete), color = Oled.Danger, fontSize = 17.sp)
                    Spacer(Modifier.width(8.dp))
                    GlyphIcon(Glyph.Delete, null, tint = Oled.Danger)
                }
            }
        },
    ) {
        SavedRow(code, Modifier.clickable { haptics.tap(); actions.onOpen(code) })
    }
}

/** Яку частку ширини треба протягнути рядок, щоб видалити. */
private const val DELETE_SWIPE = 0.45f
