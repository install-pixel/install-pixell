package com.blacklaunch.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import com.blacklaunch.launcher.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.profiles.HomeGrid
import com.blacklaunch.launcher.ui.theme.Oled
import kotlin.math.roundToInt

private val CellHeight = 104.dp
private val RemoveZoneHeight = 64.dp

/**
 * Вільна сітка головного екрана: кожна програма — окремий значок у будь-якій клітинці
 * (4 стовпці). Довге натискання й перетягування ставить значок у найближчу клітинку;
 * якщо там уже є програма — вони міняються місцями. Порожнє місце лишається чорним.
 */
@Composable
fun HomeGridView(
    apps: List<AppInfo>,
    cells: Map<String, Int>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onMove: (appKey: String, cell: Int) -> Unit,
    /** Значок кинули в зону «Прибрати» вгорі — відкріпити з головного екрана. */
    onRemove: (appKey: String) -> Unit,
    /** Де на екрані значки — щоб довге натискання на них не відкривало налаштування. */
    onIconBounds: (Map<String, Rect>) -> Unit,
    /** Смуга вгорі під назву профілю — туди значки не ставляться. */
    reservedTop: Dp = 0.dp,
    /** Смуга внизу під поле пошуку — щоб пошук не закривав значки. */
    reservedBottom: Dp = 0.dp,
    /** Почалось/закінчилось перетягування — щоб сховати плашку профілю під зоною «Прибрати». */
    onDraggingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val move by rememberUpdatedState(onMove)
    val remove by rememberUpdatedState(onRemove)
    val draggingChanged by rememberUpdatedState(onDraggingChange)
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableMapOf<String, Rect>() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val cellWidth = maxWidth / HomeGrid.COLUMNS
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val cellHeightPx = with(density) { CellHeight.toPx() }
        val topPx = with(density) { reservedTop.toPx() }
        // Нижня межа сітки: рядки рахуються знизу, тож розкладка просто піднімається над смугою.
        val heightPx = with(density) { maxHeight.toPx() - reservedBottom.toPx() }
        val rows = ((heightPx - topPx) / cellHeightPx).toInt().coerceAtLeast(1)
        val removeZonePx = with(density) { RemoveZoneHeight.toPx() }
        val draggedTop = dragging?.let { key ->
            cells[key]?.let { heightPx - (HomeGrid.rowFromBottom(it).coerceAtMost(rows - 1) + 1) * cellHeightPx + dragOffset.y }
        }
        // Прибираємо, коли центр значка в зоні — легкий рух верхнього ряду не видаляє.
        val overRemoveZone = draggedTop != null && draggedTop + cellHeightPx / 2 < removeZonePx

        // Зона «Прибрати» з'являється лише під час перетягування.
        if (dragging != null) {
            RemoveZone(highlighted = overRemoveZone, modifier = Modifier.align(Alignment.TopCenter))
        }

        apps.forEach { app ->
            val cell = cells[app.key] ?: return@forEach
            val column = HomeGrid.column(cell)
            val row = HomeGrid.rowFromBottom(cell).coerceAtMost(rows - 1)
            val x = column * cellWidthPx
            val y = heightPx - (row + 1) * cellHeightPx
            val isDragged = dragging == app.key
            Box(
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(cellWidth, CellHeight)
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        if (isDragged) {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = 1.15f
                            scaleY = 1.15f
                        }
                    }
                    .onGloballyPositioned { coordinates ->
                        bounds[app.key] = coordinates.boundsInRoot()
                        onIconBounds(bounds.filterKeys { key -> apps.any { it.key == key } })
                    }
                    .pointerInput(app.key, cell, rows) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = app.key
                                dragOffset = Offset.Zero
                                draggingChanged(true)
                                haptics.longPress()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                            },
                            onDragEnd = {
                                if (y + dragOffset.y + cellHeightPx / 2 < removeZonePx) {
                                    haptics.longPress()
                                    remove(app.key)
                                } else {
                                    val target = HomeGridMath.targetCell(
                                        x + dragOffset.x, y + dragOffset.y, cellWidthPx, cellHeightPx, heightPx, rows,
                                    )
                                    move(app.key, target)
                                }
                                dragging = null
                                dragOffset = Offset.Zero
                                draggingChanged(false)
                            },
                            onDragCancel = {
                                dragging = null
                                dragOffset = Offset.Zero
                                draggingChanged(false)
                            },
                        )
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { haptics.tap(); onLaunch(app) },
                contentAlignment = Alignment.Center,
            ) {
                GridIcon(app, iconFor)
            }
        }
    }
}

@Composable
private fun RemoveZone(highlighted: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .padding(top = 8.dp)
            .clip(shape)
            .background(if (highlighted) Oled.KnobListening else Oled.Surface)
            .border(1.dp, Oled.Glint, shape)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Close, contentDescription = null, tint = Oled.TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.remove_from_home), color = Oled.TextPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun GridIcon(app: AppInfo, iconFor: suspend (AppInfo) -> ImageBitmap?) {
    val icon by produceState<ImageBitmap?>(null, app.key) { value = iconFor(app) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(54.dp)) }
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

/** У яку клітинку стане значок, відпущений з лівим верхнім кутом у ([left], [top]). */
object HomeGridMath {
    fun targetCell(left: Float, top: Float, cellWidth: Float, cellHeight: Float, height: Float, rows: Int): Int {
        val centerX = left + cellWidth / 2
        val centerY = top + cellHeight / 2
        val column = (centerX / cellWidth).toInt().coerceIn(0, HomeGrid.COLUMNS - 1)
        val rowFromBottom = ((height - centerY) / cellHeight).toInt().coerceIn(0, rows - 1)
        return HomeGrid.cell(column, rowFromBottom)
    }
}
