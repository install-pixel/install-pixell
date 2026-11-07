package com.blacklaunch.paleta.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.paleta.R
import com.blacklaunch.paleta.data.ProductCard
import com.blacklaunch.paleta.pack.Layout
import com.blacklaunch.paleta.pack.Num
import com.blacklaunch.paleta.plan
import com.blacklaunch.paleta.ui.theme.Oled
import kotlinx.coroutines.launch

/** Довгий свайп (частка ширини), що видаляє рядок. */
private const val DELETE_SWIPE = 0.5f

/**
 * Історія: пошук за SKU (вписати чи засканувати) і список карток — найновіші вгорі. Дотик — відкрити повністю;
 * довге натискання — меню; свайп уліво — видалити (з «Повернути»).
 */
@Composable
fun HistoryPage(
    cards: List<ProductCard>?,
    query: String,
    onQuery: (String) -> Unit,
    onScan: () -> Unit,
    onOpen: (ProductCard) -> Unit,
    onMenu: (ProductCard) -> Unit,
    onDelete: (ProductCard) -> Unit,
) {
    val haptics = LocalHaptics.current
    val q = query.trim()
    val shown = remember(cards, q) { cards.orEmpty().filter { q.isEmpty() || it.sku.contains(q, ignoreCase = true) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Заголовок рядком, як на сторінці «Новий» («Товар · Очистити») — сторінки починаються на одній висоті.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.history_title), Modifier.weight(1f))
            Text(
                cards?.size?.toString().orEmpty(),
                color = Oled.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        SheetTextField(
            value = query,
            onValueChange = { onQuery(it.take(40)) },
            placeholder = stringResource(R.string.search_hint),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = ImeAction.Search),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Порожньо — лупа (просто позначка); вписано — «очистити».
                    if (query.isNotEmpty()) {
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { haptics.tap(); onQuery("") }, contentAlignment = Alignment.Center) {
                            GlyphIcon(Glyph.Close, stringResource(R.string.clear), tint = Oled.TextSecondary, size = 20.dp)
                        }
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { haptics.tap(); onScan() }, contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.Barcode, stringResource(R.string.scan), tint = Oled.TextSecondary)
                    }
                }
            },
        )
        Spacer(Modifier.size(10.dp))
        when {
            cards == null -> Unit
            cards.isEmpty() -> EmptyText(stringResource(R.string.history_empty))
            shown.isEmpty() -> EmptyText(stringResource(R.string.history_not_found, q))
            else -> {
                val list = rememberLazyListState()
                val effect = rememberOverscrollEffect()
                val fits = list.fits
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().fadingEdges(list).bounceWhenFits(fits, shared = effect),
                    userScrollEnabled = !fits,
                    overscrollEffect = effect,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { card ->
                        SwipeRow(card, onOpen = onOpen, onMenu = onMenu, onDelete = onDelete, modifier = Modifier.animateItem())
                    }
                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, color = Oled.TextSecondary, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 12.dp, end = 12.dp))
}

/** Рядок історії: мініатюра палети, SKU, дата й головне (кор., шари, висота, вага). */
@Composable
fun HistoryRow(card: ProductCard, modifier: Modifier = Modifier) {
    val comma = decimalComma()
    Row(
        modifier
            .fillMaxWidth()
            .background(Oled.Black, RoundedCornerShape(16.dp))
            .pencilFrame(16.dp, seed = (card.id % 97).toInt() + 5, alpha = 0.6f)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PalletView(
            layout = remember(card.layout) { Layout.decode(card.layout) },
            box = card.plan,
            overhang = card.overhang,
            check = null,
            thumbnail = true,
            modifier = Modifier.width(86.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(card.sku.ifBlank { stringResource(R.string.no_sku) }, color = if (card.sku.isBlank()) Oled.TextSecondary else Oled.TextPrimary, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.history_line, card.boxes, card.perLayer, if (card.partial) "1" else card.layers.toString(), Num.metres(card.heightMm, comma), Num.kilos(card.weightG, comma)),
                color = Oled.TextPrimary.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${listDate(card.createdAt)} · ${Num.metres(card.boxL, comma)}×${Num.metres(card.boxW, comma)}×${Num.metres(card.boxH, comma)} · ${Num.kilos(card.boxG, comma)} ${stringResource(R.string.unit_kg)}",
                color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Рядок зі свайпом уліво «Видалити» (як у QRaft): реагує лише на рух уліво (управо гортаються сторінки); видаляє
 * довгий свайп (≈ пів ширини), «Видалити» проявляється за рядком. Відпустив раніше — рядок пружно вертається.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeRow(card: ProductCard, onOpen: (ProductCard) -> Unit, onMenu: (ProductCard) -> Unit, onDelete: (ProductCard) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val fade = with(LocalDensity.current) { 56.dp.toPx() }
    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        val progress = (-offset.value / (width * DELETE_SWIPE)).coerceIn(0f, 1f)
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val edge = size.width + offset.value
                    val e0 = (edge / size.width).coerceIn(0f, 1f)
                    val e1 = ((edge + fade) / size.width).coerceIn(0f, 1f)
                    drawRect(Brush.horizontalGradient(0f to Color.Transparent, e0 to Color.Transparent, e1 to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
                },
        ) {
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).graphicsLayer {
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
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .drawWithContent {
                    drawContent()
                    val moved = -offset.value
                    if (moved > 0.5f) {
                        val p = (moved / (width * DELETE_SWIPE)).coerceIn(0f, 1f)
                        drawRect(Color.Black.copy(alpha = 0.35f * p))
                    }
                }
                .pointerInput(card.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var start = 0f
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
                                onDelete(card)
                            } else {
                                offset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    }
                }
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { haptics.tap(); onOpen(card) },
                    onLongClick = { haptics.longPress(); onMenu(card) },
                ),
        ) {
            HistoryRow(card)
        }
    }
}
