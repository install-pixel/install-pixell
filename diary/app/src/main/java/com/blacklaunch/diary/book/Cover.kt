package com.blacklaunch.diary.book

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.GlassFont
import com.blacklaunch.diary.ui.HandFont
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.pencilFrame
import com.blacklaunch.diary.ui.pencilLoop
import com.blacklaunch.diary.ui.pencilTick
import com.blacklaunch.diary.ui.quadPoints
import com.blacklaunch.diary.ui.taperedPath
import com.blacklaunch.diary.ui.theme.Oled
import kotlin.math.cos
import kotlin.math.sin

/**
 * Обкладинка щоденника.
 *  - «Скло»: товста пластина чорного димчастого скла — подвійний кант (товщина), відблиск угорі й косий відсвіт;
 *    своє фото (чорно-біле) лежить під склом.
 *  - «Аркуш»: обкладинка, обведена олівцем, з орнаментом у кутах і резинкою-застібкою; своє фото — олівцевим ескізом.
 * Назва: «Скло» — на легкій скляній плашечці, «Аркуш» — просто напис почерком; пересувається (утримати й тягнути).
 * [lock] — замок (барабани) на застібці, якщо щоденник закритий на код.
 */
@Composable
fun CoverFace(
    name: String,
    photo: ImageBitmap?,
    pattern: Int,
    locked: Boolean,
    modifier: Modifier = Modifier,
    titleY: Float? = null,
    onTitleMoved: ((Float) -> Unit)? = null,
    onTitleTap: (() -> Unit)? = null,
    lock: (@Composable () -> Unit)? = null,
) {
    val glass = AppStyle.glass
    val haptics = com.blacklaunch.diary.ui.LocalHaptics.current
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawCover(photo, pattern, glass, locked) }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val heightPx = constraints.maxHeight.toFloat()
            // Одна й та сама змінна положення на весь час (жест і малювання бачать одне значення); збережене місце
            // підхоплюємо, лише коли не тягнемо.
            var y by remember { mutableFloatStateOf(titleY ?: DEFAULT_TITLE_Y) }
            var dragging by remember { mutableStateOf(false) }
            LaunchedEffect(titleY) { if (!dragging) y = titleY ?: DEFAULT_TITLE_Y }
            val moved by rememberUpdatedState(onTitleMoved)
            val tapped by rememberUpdatedState(onTitleTap)
            var titleH by remember { mutableFloatStateOf(0f) }
            if (lock != null) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(0.6f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xE6000000))
                            .pencilFrame(22.dp, seed = 409)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) { lock() }
                    Box(Modifier.weight(0.4f))
                }
            }
            // Назва. Утримай і тягни вгору чи вниз — пересунеться (не під кожне фото звичне місце підходить).
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (y * heightPx - titleH / 2).toInt()) }
                    .padding(horizontal = 26.dp)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .onSizeChanged { titleH = it.height.toFloat() }
                    .then(
                        if (onTitleMoved == null) Modifier
                        else Modifier
                            .pointerInput(heightPx) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = true; haptics.longPress() },
                                    onDrag = { change, d ->
                                        change.consume()
                                        y = (y + d.y / heightPx).coerceIn(0.08f, 0.92f)
                                    },
                                    onDragEnd = { dragging = false; haptics.tick(); moved?.invoke(y) },
                                    onDragCancel = { dragging = false },
                                )
                            }
                            // Дотик — перейменувати просто тут.
                            .pointerInput(Unit) { detectTapGestures(onTap = { tapped?.invoke() }) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (glass) {
                    // «Скло»: легка скляна плашечка — ледь прозора, з товщею й опуклістю.
                    GlassPlate(Modifier.fillMaxWidth()) {
                        Text(
                            name,
                            style = TextStyle(
                                fontFamily = GlassFont, fontWeight = FontWeight.Light, fontSize = 30.sp, letterSpacing = 2.sp,
                                color = Oled.TextPrimary, shadow = Shadow(Color(0x59FFFFFF), blurRadius = 18f), textAlign = TextAlign.Center,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                } else {
                    // «Аркуш»: просто напис почерком, без таблички; тінь — щоб читався на будь-якому фото.
                    Text(
                        name,
                        style = TextStyle(
                            fontFamily = HandFont, fontWeight = FontWeight.Medium, fontSize = 40.sp,
                            color = SheetInk.copy(alpha = 0.78f), shadow = Shadow(Color.Black, blurRadius = 14f), textAlign = TextAlign.Center,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Звичне місце назви на обкладинці (частка висоти). */
private const val DEFAULT_TITLE_Y = 0.24f

/**
 * Скляна плашечка — як скрізь у «Склі»: ледь прозора темна пластинка з товщею (світліший верхній край, темніший нижній)
 * і опуклістю (відблиск зверху), скляна грань по контуру.
 */
@Composable
internal fun GlassPlate(modifier: Modifier = Modifier, radius: androidx.compose.ui.unit.Dp = 14.dp, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .drawBehind {
                val r = CornerRadius(radius.toPx())
                drawRoundRect(Color(0x47000000), cornerRadius = r)
                drawRoundRect(Brush.verticalGradient(0f to Color(0x2EFFFFFF), 0.45f to Color(0x0AFFFFFF), 1f to Color(0x14FFFFFF)), cornerRadius = r)
                // Опуклість: м'який відблиск у верхній половині.
                drawRoundRect(
                    Brush.verticalGradient(0f to Color(0x24FFFFFF), 1f to Color.Transparent, startY = 0f, endY = size.height * 0.5f),
                    topLeft = Offset(3.dp.toPx(), 2.dp.toPx()),
                    size = Size(size.width - 6.dp.toPx(), size.height * 0.5f),
                    cornerRadius = r,
                )
                // Товща: темніший нижній край.
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0x40000000), startY = size.height * 0.7f, endY = size.height))
                drawRoundRect(Oled.Glint, cornerRadius = r, style = Stroke(1.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun DrawScope.drawCover(photo: ImageBitmap?, pattern: Int, glass: Boolean, locked: Boolean) {
    val r = BookCorner.toPx()
    val cr = CornerRadius(r)
    val inset = if (glass) 10.dp.toPx() else 14.dp.toPx()
    val inner = Size(size.width - 2 * inset, size.height - 2 * inset)
    val innerPath = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(r * 0.7f)))
    }
    // Тло обкладинки.
    if (glass) {
        // Товсте скло — ледь прозоре: крізь нього слабко видно першу сторінку (див. [com.blacklaunch.diary.book.FlipBook]).
        drawRoundRect(Brush.verticalGradient(0f to Color(0xFF1A1A1A), 0.5f to Color(0xFF0B0B0B), 1f to Color(0xFF060606)), cornerRadius = cr, alpha = GLASS_COVER_ALPHA)
    } else {
        // Щільний чорний картон / шкіра — та сама фактура, що й у паперу, тільки темніша й з порами.
        PaperTexture.ensure()
        clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, cr)) }) {
            val tex = PaperTexture.cover
            if (tex != null) drawImage(tex, dstSize = IntSize(size.width.toInt() + 1, size.height.toInt() + 1)) else drawRect(PaperTexture.COVER_BASE)
        }
    }
    // Фото під склом / у рамці олівцем.
    if (photo != null) {
        clipPath(innerPath) {
            val k = maxOf(inner.width / photo.width, inner.height / photo.height)
            val w = photo.width * k
            val h = photo.height * k
            drawImage(
                photo,
                dstOffset = IntOffset((inset + (inner.width - w) / 2).toInt(), (inset + (inner.height - h) / 2).toInt()),
                dstSize = IntSize(w.toInt(), h.toInt()),
                // «Аркуш»: на всю обкладинку олівець світиться сильніше, ніж маленьке фото дня, — трохи тьмяніше.
                alpha = if (glass) 1f else 0.72f,
            )
            if (glass) glassFilter(inset, inner)
        }
    } else if (glass) {
        // Без фото — чисте скло з тим самим «фільтром», що й на фото (товща, фаска), без жодних візерунків.
        clipPath(innerPath) { glassFilter(inset, inner) }
    }
    if (glass) {
        // Товщина скла: два канти — зовнішній яскравий, внутрішній тьмяний; відблиск і косий відсвіт.
        drawRoundRect(Brush.verticalGradient(0f to Color(0x1AFFFFFF), 1f to Color.Transparent, startY = 0f, endY = size.height * 0.4f), cornerRadius = cr)
        clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, cr)) }) {
            val a = Offset(size.width * 0.15f, 0f)
            drawPath(
                Path().apply {
                    moveTo(a.x, 0f); lineTo(a.x + size.width * 0.22f, 0f)
                    lineTo(a.x - size.width * 0.18f, size.height); lineTo(a.x - size.width * 0.32f, size.height); close()
                },
                Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color(0x0FFFFFFF), 1f to Color.Transparent),
            )
        }
        drawPath(innerPath, Brush.verticalGradient(0f to Color(0x40FFFFFF), 0.3f to Color(0x10FFFFFF), 1f to Color(0x20FFFFFF)), style = Stroke(1.dp.toPx()))
        // Корінець зліва — темна смуга з гранню.
        drawRect(Brush.horizontalGradient(0f to Color(0x99000000), 1f to Color.Transparent, startX = 0f, endX = 18.dp.toPx()))
    } else {
        val loop = pencilLoop(inner, r * 0.7f, 313, 1.4.dp.toPx()).map { it + Offset(inset, inset) }
        drawPath(taperedPath(loop, 1.6.dp.toPx()), SheetInk.copy(alpha = 0.55f))
        drawCorners(inset)
    }
    // Зовнішній контур — та сама рамка, що й у сторінок (одна на всю книжку).
    drawBookFrame(glass)
}


/** «Аркуш»: завитки олівцем у кутах обкладинки. */
private fun DrawScope.drawCorners(inset: Float) {
    val s = 30.dp.toPx()
    val ink = SheetInk.copy(alpha = 0.5f)
    val corners = listOf(
        Offset(inset, inset) to Offset(1f, 1f),
        Offset(size.width - inset, inset) to Offset(-1f, 1f),
        Offset(inset, size.height - inset) to Offset(1f, -1f),
        Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
    )
    corners.forEachIndexed { i, (c, d) ->
        val pts = List(30) { k ->
            val t = k / 29f
            val a = t * 4.2f
            val rr = s * (0.9f - 0.6f * t)
            Offset(c.x + d.x * (s * 0.3f + rr * (1 - cos(a)) * 0.5f), c.y + d.y * (s * 0.3f + rr * sin(a) * 0.5f))
        }
        drawPath(taperedPath(pts, 1.5.dp.toPx()), ink)
        pencilTick(c + Offset(d.x * s * 0.15f, d.y * s * 1.1f), c + Offset(d.x * s * 1.1f, d.y * s * 0.15f), 1.3.dp.toPx(), ink, i + 9)
    }
}

/** «Скло»: товща скла над обкладинкою — притемнені «заломлені» краї й фаска (світла вгорі ліворуч, темна внизу праворуч). */
private fun DrawScope.glassFilter(inset: Float, inner: Size) {
        // Як фото дня: під товстим склом — притемнені «заломлені» краї й фаска (світла вгорі ліворуч, темна внизу
        // праворуч).
        val bevel = 9.dp.toPx()
        val c = Offset(size.width / 2, size.height / 2)
        drawRect(Brush.radialGradient(0.5f to Color.Transparent, 1f to Color(0x80000000), center = c, radius = maxOf(inner.width, inner.height) * 0.6f))
        drawRect(Brush.verticalGradient(0f to Color(0x40FFFFFF), 1f to Color.Transparent, startY = inset, endY = inset + bevel), topLeft = Offset(inset, inset), size = Size(inner.width, bevel))
        drawRect(Brush.horizontalGradient(0f to Color(0x2EFFFFFF), 1f to Color.Transparent, startX = inset, endX = inset + bevel), topLeft = Offset(inset, inset), size = Size(bevel, inner.height))
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0x59000000), startY = size.height - inset - bevel, endY = size.height - inset), topLeft = Offset(inset, size.height - inset - bevel), size = Size(inner.width, bevel))
        drawRect(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color(0x4D000000), startX = size.width - inset - bevel, endX = size.width - inset), topLeft = Offset(size.width - inset - bevel, inset), size = Size(bevel, inner.height))
}

/** Наскільки непрозоре скло обкладинки (сторінки прозоріші — [GLASS_PAGE_ALPHA]). */
const val GLASS_COVER_ALPHA = 0.94f
