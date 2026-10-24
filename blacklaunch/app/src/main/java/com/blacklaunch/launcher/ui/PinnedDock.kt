package com.blacklaunch.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.view.HapticFeedbackConstants
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.settings.DockPosition
import com.blacklaunch.launcher.ui.theme.Oled
import kotlin.math.roundToInt

private const val COLUMNS = 4
private val CellHeight = 84.dp
private val DockShape = RoundedCornerShape(28.dp)

/**
 * Закріплені програми — «скляна» панель у стилі теми: сірі значки сіткою по 4.
 * Довге натискання на значок і перетягування змінює порядок.
 */
@Composable
fun PinnedDock(
    apps: List<AppInfo>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onReorder: (List<String>) -> Unit,
    /** Панель перетягнули за порожнє місце на [deltaY] пікселів по вертикалі. */
    onMoved: (deltaY: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) return
    val view = LocalView.current
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val currentApps by rememberUpdatedState(apps)
    val reorder by rememberUpdatedState(onReorder)
    val moved by rememberUpdatedState(onMoved)
    var dockDrag by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = dockDrag ?: 0f }
            .pointerInput(Unit) {
                // Довге натискання на порожньому місці панелі — переміщення всієї панелі.
                val columnWidth = (size.width - 16.dp.toPx()) / COLUMNS
                val rowHeight = CellHeight.toPx()
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        val col = ((start.x - 8.dp.toPx()) / columnWidth).toInt().coerceIn(0, COLUMNS - 1)
                        val row = ((start.y - 10.dp.toPx()) / rowHeight).toInt().coerceAtLeast(0)
                        if (row * COLUMNS + col >= currentApps.size) {
                            dockDrag = 0f
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    },
                    onDrag = { change, amount ->
                        dockDrag?.let {
                            change.consume()
                            dockDrag = it + amount.y
                        }
                    },
                    onDragEnd = {
                        dockDrag?.let(moved)
                        dockDrag = null
                    },
                    onDragCancel = { dockDrag = null },
                )
            }
            .clip(DockShape)
            .background(Oled.Surface)
            .border(1.dp, Oled.Glint, DockShape)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        val cellWidth = maxWidth / COLUMNS
        val cellWidthPx = with(LocalDensity.current) { cellWidth.toPx() }
        val cellHeightPx = with(LocalDensity.current) { CellHeight.toPx() }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            apps.chunked(COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        val isDragged = dragging == app.key
                        DockCell(
                            app = app,
                            iconFor = iconFor,
                            modifier = Modifier
                                .width(cellWidth)
                                .height(CellHeight)
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragged) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.12f
                                        scaleY = 1.12f
                                    }
                                }
                                .pointerInput(app.key) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragging = app.key
                                            dragOffset = Offset.Zero
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                        },
                                        onDragEnd = {
                                            val keys = currentApps.map { it.key }
                                            val from = keys.indexOf(app.key)
                                            val target = DockMath.targetIndex(from, dragOffset, cellWidthPx, cellHeightPx, COLUMNS, keys.size)
                                            if (from >= 0 && target != from) {
                                                reorder(keys.toMutableList().apply { add(target, removeAt(from)) })
                                            }
                                            dragging = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            dragOffset = Offset.Zero
                                        },
                                    )
                                }
                                .clickable { onLaunch(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockCell(app: AppInfo, iconFor: suspend (AppInfo) -> ImageBitmap?, modifier: Modifier) {
    val icon by produceState<ImageBitmap?>(null, app.key) { value = iconFor(app) }
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).padding(top = 6.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(44.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = Oled.TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Куди поставити значок після перетягування (чиста функція — для тестів). */
object DockMath {
    fun targetIndex(from: Int, offset: Offset, cellWidth: Float, cellHeight: Float, columns: Int, count: Int): Int {
        if (from < 0 || count == 0) return from
        val col = from % columns + (offset.x / cellWidth).roundToInt()
        val row = from / columns + (offset.y / cellHeight).roundToInt()
        return (row * columns + col.coerceIn(0, columns - 1)).coerceIn(0, count - 1)
    }
}

/** Куди стане панель після перетягування: верхня, середня чи нижня третина екрана. */
object DockLayout {
    fun positionFor(centerY: Float, screenHeight: Float): DockPosition = when {
        centerY < screenHeight / 3 -> DockPosition.TOP
        centerY < screenHeight * 2 / 3 -> DockPosition.CENTER
        else -> DockPosition.BOTTOM
    }
}
