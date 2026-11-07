package com.blacklaunch.paleta.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacklaunch.paleta.PaletaViewModel
import com.blacklaunch.paleta.R
import com.blacklaunch.paleta.UiEvent
import com.blacklaunch.paleta.data.ProductCard
import com.blacklaunch.paleta.scan.ScannerScreen
import com.blacklaunch.paleta.ui.theme.Oled
import com.blacklaunch.paleta.view
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class BannerMsg(val text: String, val action: String? = null, val onAction: () -> Unit = {}, val id: Long = System.nanoTime())

/** Куди піде засканований номер. */
private enum class ScanTarget { Sku, Search }

@Composable
fun AppRoot(vm: PaletaViewModel, onLanguageChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val cards by vm.cards.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = 0) { 2 }

    var settingsOpen by remember { mutableStateOf(false) }
    var limitDialog by remember { mutableStateOf<LimitDialog?>(null) }
    var layoutMenu by remember { mutableStateOf(false) }
    var cardMenu by remember { mutableStateOf<ProductCard?>(null) }
    var detail by remember { mutableStateOf<ProductCard?>(null) }
    var scanFor by rememberSaveable { mutableStateOf<ScanTarget?>(null) }
    var pendingScan by remember { mutableStateOf<ScanTarget?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var banner by remember { mutableStateOf<BannerMsg?>(null) }

    fun show(text: String, action: String? = null, onAction: () -> Unit = {}) {
        banner = BannerMsg(text, action, onAction)
    }
    LaunchedEffect(banner) {
        val b = banner ?: return@LaunchedEffect
        delay(if (b.action != null) 4500 else 2200)
        if (banner?.id == b.id) banner = null
    }

    val textSaved = stringResource(R.string.saved)
    val textNew = stringResource(R.string.new_product)
    val textReset = stringResource(R.string.layout_reset)
    val textNoRoom = stringResource(R.string.no_room)
    val textOnly = stringResource(R.string.only_variant)
    val textDuplicated = stringResource(R.string.duplicated)
    val textDeleted = stringResource(R.string.deleted)
    val textUndo = stringResource(R.string.undo)
    val textNoCamera = stringResource(R.string.camera_denied)
    val variantFormat = stringResource(R.string.variant_n)
    val textOnlyOrient = stringResource(R.string.only_orientation)
    val textOrientNoFit = stringResource(R.string.orientation_no_fit)
    val orientFormat = stringResource(R.string.orientation_now)
    val orientNames = listOf(stringResource(R.string.orient_flat), stringResource(R.string.orient_side), stringResource(R.string.orient_up))
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                UiEvent.Saved -> show(textSaved, textNew) { vm.clearAll(); banner = null }
                UiEvent.LayoutReset -> show(textReset)
                UiEvent.NoRoom -> show(textNoRoom)
                UiEvent.OnlyVariant -> show(textOnly)
                is UiEvent.Variant -> show(String.format(variantFormat, e.index, e.total))
                UiEvent.Duplicated -> show(textDuplicated)
                UiEvent.OnlyOrientation -> show(textOnlyOrient)
                UiEvent.OrientationNoFit -> show(textOrientNoFit)
                is UiEvent.Orientation -> {
                    val c = decimalComma()
                    show(String.format(orientFormat, orientNames[com.blacklaunch.paleta.pack.Orient.kind(e.l, e.w, e.h)], com.blacklaunch.paleta.pack.Num.metres(e.l, c), com.blacklaunch.paleta.pack.Num.metres(e.w, c), com.blacklaunch.paleta.pack.Num.metres(e.h, c)))
                }
                is UiEvent.Deleted -> show(textDeleted, textUndo) { vm.restore(e.card); banner = null }
            }
        }
    }
    // Перейшли на іншу сторінку — клавіатуру сховати.
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { focus.clearFocus() } }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanFor = pendingScan else show(textNoCamera)
        pendingScan = null
    }
    fun scan(target: ScanTarget) {
        focus.clearFocus()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scanFor = target
        } else {
            pendingScan = target
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    fun share(card: ProductCard) {
        val text = cardText(context, card.view())
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
    }

    fun duplicate(card: ProductCard) {
        vm.duplicate(card, decimalComma())
        detail = null
        cardMenu = null
        scope.launch { pager.animateScrollToPage(0, animationSpec = PAGE_ANIM) }
    }

    fun delete(card: ProductCard) {
        detail = null
        cardMenu = null
        vm.delete(card)
    }

    // Цей SKU уже є в історії — підказка під полем.
    val skuNow = vm.sku.trim()
    val known = remember(cards, skuNow) { if (skuNow.isEmpty()) null else cards?.firstOrNull { it.sku.equals(skuNow, ignoreCase = true) } }

    val dialogOpen = settingsOpen || limitDialog != null || layoutMenu || cardMenu != null
    val backdrop = backdropAmount(dialogOpen)

    Box(Modifier.fillMaxSize().background(Oled.Black)) {
        Column(Modifier.fillMaxSize().backdrop(backdrop).statusBarsPadding().imePadding()) {
            PageTabs(
                position = { pager.currentPage + pager.currentPageOffsetFraction },
                selected = { pager.targetPage },
                onSettings = { settingsOpen = true },
                onPick = { scope.launch { pager.animateScrollToPage(it, animationSpec = PAGE_ANIM) } },
            )
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = PAGE_FLING),
            ) { page ->
                when (page) {
                    0 -> EditorPage(
                        vm = vm,
                        known = known,
                        onScan = { scan(ScanTarget.Sku) },
                        onOpenKnown = { detail = it },
                        onLimit = { limitDialog = it },
                        onLayoutMenu = { layoutMenu = true },
                    )
                    else -> HistoryPage(
                        cards = cards,
                        query = query,
                        onQuery = { query = it },
                        onScan = { scan(ScanTarget.Search) },
                        onOpen = { detail = it },
                        onMenu = { cardMenu = it },
                        onDelete = { delete(it) },
                    )
                }
            }
        }

        // Збережена картка на весь екран.
        AnimatedVisibility(
            visible = detail != null,
            enter = fadeIn(tween(OPEN_MS)) + slideInHorizontally(tween(320)) { it / 8 },
            exit = fadeOut(tween(CLOSE_MS)) + slideOutHorizontally(tween(CLOSE_MS)) { it / 8 },
        ) {
            // Картка лишається на екрані, поки тане (detail уже null).
            var last by remember { mutableStateOf<ProductCard?>(null) }
            detail?.let { last = it }
            last?.let { card ->
                DetailScreen(
                    card = card,
                    onBack = { detail = null },
                    onDuplicate = { duplicate(card) },
                    onShare = { share(card) },
                    onDelete = { delete(card) },
                )
            }
        }

        scanFor?.let { target ->
            ScannerScreen(
                onResult = { code ->
                    scanFor = null
                    when (target) {
                        ScanTarget.Sku -> vm.sku = code.take(PaletaViewModel.MAX_SKU)
                        ScanTarget.Search -> query = code.take(40)
                    }
                },
                onClose = { scanFor = null },
            )
        }

        Banner(banner?.text, banner?.action, banner?.onAction ?: {})
    }

    if (settingsOpen) SettingsDialog(onDismiss = { settingsOpen = false }, onLanguageChanged = onLanguageChanged)
    when (limitDialog) {
        LimitDialog.Height -> HeightDialog(vm.heightLimit, vm.adr, onDismiss = { limitDialog = null }) { vm.setHeight(it); limitDialog = null }
        LimitDialog.Weight -> WeightDialog(vm.weightLimitG, vm.palletOn, vm.palletG, onDismiss = { limitDialog = null }) { limit, on, pallet ->
            vm.setWeight(limit)
            vm.setPallet(on, pallet)
            limitDialog = null
        }
        LimitDialog.Overhang -> OverhangDialog(vm.overhang, onDismiss = { limitDialog = null }) { vm.setOverhangMm(it); limitDialog = null }
        null -> Unit
    }
    if (layoutMenu) {
        LayoutMenu(
            onDismiss = { layoutMenu = false },
            onBest = { layoutMenu = false; vm.auto() },
            onOrient = { layoutMenu = false; vm.nextOrientation() },
            onFill = { layoutMenu = false; vm.fill() },
            onClear = { layoutMenu = false; vm.clearLayout() },
            onSameLayers = if (vm.layout2 != null) ({ layoutMenu = false; vm.sameLayers() }) else null,
        )
    }
    cardMenu?.let { card ->
        CardMenu(
            card,
            onDismiss = { cardMenu = null },
            onOpen = { cardMenu = null; detail = card },
            onDuplicate = { duplicate(card) },
            onShare = { cardMenu = null; share(card) },
            onDelete = { delete(card) },
        )
    }
}

/** Назви сторінок угорі: вибрана — підкреслена олівцем (лінія пишеться зліва направо). Шестірня — налаштування. */
@Composable
private fun PageTabs(position: () -> Float, selected: () -> Int, onSettings: () -> Unit, onPick: (Int) -> Unit) {
    val haptics = LocalHaptics.current
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp)) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            listOf(R.string.page_new, R.string.page_history).forEachIndexed { i, title ->
                val grow = remember { androidx.compose.animation.core.Animatable(if (selected() == i) 1f else 0f) }
                val shown = remember { androidx.compose.animation.core.Animatable(if (selected() == i) 1f else 0f) }
                LaunchedEffect(Unit) {
                    snapshotFlow { selected() == i }.collect { on ->
                        if (on) {
                            shown.snapTo(1f)
                            grow.snapTo(0f)
                            grow.animateTo(1f, tween(UNDERLINE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        } else {
                            shown.animateTo(0f, tween(UNDERLINE_OUT_MS))
                        }
                    }
                }
                Text(
                    stringResource(title),
                    color = Oled.TextPrimary,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { haptics.tap(); onPick(i) }
                        .drawBehind {
                            val a = shown.value
                            if (a > 0.02f) pencilStroke(size.height + 1.dp.toPx(), 0f, size.width, seed = i + 2, alpha = 0.8f * a, progress = grow.value)
                        }
                        .graphicsLayer {
                            val near = 1f - kotlin.math.abs(position() - i).coerceIn(0f, 1f)
                            alpha = 0.5f + 0.5f * near
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .clickable { haptics.tap(); onSettings() },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Settings, stringResource(R.string.settings), tint = Oled.TextSecondary, size = 22.dp)
        }
    }
}

private const val UNDERLINE_MS = 420
private const val UNDERLINE_OUT_MS = 220

/** Перехід між сторінками з дотику на назву: плавний розгін і гальмування (як у QRaft). */
private val PAGE_ANIM = tween<Float>(durationMillis = 520, easing = androidx.compose.animation.core.CubicBezierEasing(0.65f, 0f, 0.35f, 1f))

/** Після свайпу — пружина без підскоку. */
private val PAGE_FLING = androidx.compose.animation.core.spring<Float>(
    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
    stiffness = 220f,
)
