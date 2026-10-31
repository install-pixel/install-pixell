package com.blacklaunch.launcher.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.profiles.HomeGrid
import com.blacklaunch.launcher.ui.theme.Oled
import kotlin.math.roundToInt

private val CellHeight = 104.dp
/** Палець вище цієї межі — програма прибирається (плашка «Прибрати» + запас, щоб не тягти за край екрана). */
private val RemoveZoneHeight = 96.dp
/** Наскільки вище пальця тримається значок, коли його тягнуть. */
private val DragLift = 36.dp
/** Скільки треба зрушити палець, щоб це вважалось перетягуванням, а не довгим натисканням. */
private val DragSlop = 12.dp
private const val SUPPRESS_TAP_MS = 400L

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
    /** Пакети програм зі сповіщеннями — крапка біля значка. */
    dots: Set<String> = emptySet(),
    /** Довго натиснули й відпустили, не перетягуючи, — меню програми (як у пошуку). */
    onLongPressApp: (AppInfo) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val move by rememberUpdatedState(onMove)
    val remove by rememberUpdatedState(onRemove)
    val draggingChanged by rememberUpdatedState(onDraggingChange)
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // Де палець (у координатах сітки) і чи справді тягнули — довге натискання без руху відкриває меню, а не програму.
    var finger by remember { mutableStateOf(Offset.Zero) }
    var pressedAt by remember { mutableStateOf(Offset.Zero) }
    var moved by remember { mutableStateOf(false) }
    // Після довгого натискання звичайний «дотик» на відпусканні не запускає програму (хоч би в якому порядку
    // прийшли події): до [suppressUntil] запуск цієї програми ігнорується.
    var suppressKey by remember { mutableStateOf<String?>(null) }
    var suppressUntil by remember { mutableLongStateOf(0L) }
    val longPressMenu by rememberUpdatedState(onLongPressApp)
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
        val liftPx = with(density) { DragLift.toPx() }
        val movedPx = with(density) { DragSlop.toPx() }
        // Прибираємо, коли до зони дотягнувся сам палець (не треба тягти значок за край екрана).
        val overRemoveZone = dragging != null && finger.y < removeZonePx

        // Зона «Прибрати» з'являється лише під час перетягування.
        if (dragging != null) {
            RemoveZone(highlighted = overRemoveZone, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
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
                            onDragStart = { at ->
                                dragging = app.key
                                // Значок «стрибає» під палець — трохи вище нього, щоб палець його не закривав.
                                dragOffset = Offset(at.x - cellWidthPx / 2, at.y - cellHeightPx / 2 - liftPx)
                                finger = Offset(x + at.x, y + at.y)
                                pressedAt = finger
                                moved = false
                                suppressKey = app.key
                                suppressUntil = Long.MAX_VALUE
                                draggingChanged(true)
                                haptics.longPress()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                                finger += amount
                                if (!moved && (finger - pressedAt).getDistance() > movedPx) moved = true
                            },
                            onDragEnd = {
                                if (!moved) {
                                    // Довго натиснули й відпустили на місці — меню програми.
                                    longPressMenu(app)
                                } else if (finger.y < removeZonePx) {
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
                                suppressUntil = SystemClock.uptimeMillis() + SUPPRESS_TAP_MS
                                draggingChanged(false)
                            },
                            onDragCancel = {
                                // Відпустили на місці, а «дотик» значка забрав подію відпускання собі — жест вважається
                                // скасованим. Це те саме довге натискання без руху: відкриваємо меню.
                                if (!moved) longPressMenu(app)
                                dragging = null
                                dragOffset = Offset.Zero
                                suppressUntil = SystemClock.uptimeMillis() + SUPPRESS_TAP_MS
                                draggingChanged(false)
                            },
                        )
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        // Відпускання після довгого натискання — не запуск (меню відкриває сам жест).
                        if (suppressKey == app.key && SystemClock.uptimeMillis() < suppressUntil) return@clickable
                        haptics.tap()
                        onLaunch(app)
                    },
                contentAlignment = Alignment.Center,
            ) {
                GridIcon(app, iconFor, dot = app.packageName in dots)
            }
        }
    }
}

@Composable
private fun RemoveZone(highlighted: Boolean, modifier: Modifier) {
    // Така сама плашка, як назва режиму, і на тому самому місці; напис — червоним.
    TopPill(
        text = stringResource(R.string.remove_from_home),
        color = if (LocalSheet.current || highlighted) Oled.Danger else Oled.TextPrimary,
        fill = if (highlighted) Oled.KnobDanger else Oled.Surface,
        ink = if (highlighted) Oled.Danger else SheetInk,
        modifier = modifier,
    )
}

@Composable
private fun GridIcon(app: AppInfo, iconFor: suspend (AppInfo) -> ImageBitmap?, dot: Boolean = false) {
    // «Аркуш»: замість значка — лише назва від руки (крапка сповіщення — поруч).
    if (LocalSheet.current) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = app.label,
                color = Oled.TextPrimary,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (dot) Text(" •", color = Oled.TextPrimary, fontSize = 17.sp)
        }
        return
    }
    val icon by produceState<ImageBitmap?>(null, app.key, iconFor) { value = iconFor(app) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(54.dp)) }
            // Є сповіщення в шторці — біла крапка в куті (з чорним обідком, щоб не зливалась зі значком).
            if (dot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Oled.Black)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Oled.TextPrimary),
                )
            }
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
