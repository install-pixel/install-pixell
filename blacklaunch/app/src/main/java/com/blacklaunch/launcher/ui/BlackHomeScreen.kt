package com.blacklaunch.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.global.GlobalResult
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.viewmodel.HomeState
import com.blacklaunch.launcher.viewmodel.LauncherUiState
import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.voice.VoiceState
import kotlin.math.abs

private val SwipeThreshold = 48.dp

/** «Гумовий» опір: панель їде вниз удвічі повільніше за палець. */
private const val RESISTANCE = 0.5f

/** Скільки (у порогах свайпу) треба відтягнути панель, щоб пошук закрився. */
private const val PULL = 1f

/** Те саме, якщо список уже гортали, — навмисний, довший свайп. */
private const val LONG_PULL = 2.5f

/** Тривалість анімації закриття пошуку. */
private const val CLOSE_MS = 280
private const val OPEN_MS = 180

@Composable
fun BlackHomeScreen(
    state: LauncherUiState,
    home: HomeState,
    /** Назва профілю, на який щойно перемкнули (показується на мить), або null. */
    profileBanner: String?,
    onSwitchProfile: (delta: Int) -> Unit,
    onMoveApp: (appKey: String, cell: Int) -> Unit,
    onRemoveApp: (appKey: String) -> Unit,
    /** Перемикання профілів свайпом вліво/вправо (можна вимкнути). */
    profileSwipe: Boolean,
    /** Назва профілю вгорі екрана (можна вимкнути — тоді екран повністю чорний). */
    showProfileLabel: Boolean,
    onOpenSearch: () -> Unit,
    /** false — пошук закривається миттєво (повернулись після довгої відсутності). */
    animateSearchClose: Boolean = true,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onCloseSearch: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onGlobal: (GlobalResult) -> Unit,
    /** Команда з результатів пошуку («wifi», «таймер 10 хвилин»). */
    onAction: (VoiceAction) -> Unit,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    voiceState: VoiceState,
    onMicClick: () -> Unit,
    /** Налаштування на весь екран (довге натискання на чорному екрані). */
    settingsScreen: @Composable (onDismiss: () -> Unit) -> Unit,
    /** Власні назви програми (з меню програми). */
    aliasDialog: @Composable (app: AppInfo, onDismiss: () -> Unit) -> Unit,
    /** Меню програми (довге натискання на результат пошуку). */
    appActions: @Composable (app: AppInfo, onNames: () -> Unit, onDismiss: () -> Unit) -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var aliasApp by remember { mutableStateOf<AppInfo?>(null) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }

    // На головному екрані Back нічого не робить; у пошуку — закриває його.
    BackHandler {
        if (state is LauncherUiState.SearchOpen) onCloseSearch()
    }

    if (showSettings) {
        settingsScreen { showSettings = false }
        return
    }

    val searchOpen = state is LauncherUiState.SearchOpen
    // Під час анімації закриття показуємо останній стан пошуку, а не порожнечу.
    var lastSearch by remember { mutableStateOf<LauncherUiState.SearchOpen?>(null) }
    if (state is LauncherUiState.SearchOpen) lastSearch = state
    val homeBlur by animateDpAsState(if (searchOpen) 10.dp else 0.dp, tween(if (animateSearchClose) CLOSE_MS else 0), label = "homeBlur")
    val homeAlpha by animateFloatAsState(if (searchOpen) 0.7f else 1f, tween(if (animateSearchClose) CLOSE_MS else 0), label = "homeAlpha")
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Під пошуком головний екран лишається на фоні — розмитий і притемнений.
        HomeIdle(
            home = home,
            profileBanner = profileBanner,
            iconFor = iconFor,
            onLaunch = onLaunch,
            onOpenSearch = onOpenSearch,
            onLongPress = { showSettings = true },
            onSwitchProfile = onSwitchProfile,
            onMoveApp = onMoveApp,
            onRemoveApp = onRemoveApp,
            profileSwipe = profileSwipe,
            showProfileLabel = showProfileLabel,
            modifier = Modifier.blur(homeBlur).alpha(homeAlpha),
        )
        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(tween(OPEN_MS)) + slideInVertically(tween(OPEN_MS)) { it / 10 },
            exit = if (animateSearchClose) {
                fadeOut(tween(CLOSE_MS)) + slideOutVertically(tween(CLOSE_MS)) { it / 6 }
            } else {
                ExitTransition.None
            },
        ) {
            val search = lastSearch ?: return@AnimatedVisibility
            SearchOpen(
                state = search,
                visible = transition.targetState == EnterExitState.Visible,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                onLaunch = onLaunch,
                onLongPress = { menuApp = it },
                onGlobal = onGlobal,
                onAction = onAction,
                iconFor = iconFor,
                voiceState = voiceState,
                onMicClick = onMicClick,
                // Сховали клавіатуру кнопкою «Назад» — закриваємо й пошук, одним рухом.
                onKeyboardHidden = {
                    if (aliasApp == null && menuApp == null && voiceState == VoiceState.Idle) onCloseSearch()
                },
                onSwipeDown = onCloseSearch,
            )
        }
    }

    menuApp?.let { app -> appActions(app, { menuApp = null; aliasApp = app }) { menuApp = null } }
    aliasApp?.let { app -> aliasDialog(app) { aliasApp = null } }
}

@Composable
private fun HomeIdle(
    modifier: Modifier = Modifier,
    home: HomeState,
    profileBanner: String?,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onOpenSearch: () -> Unit,
    onLongPress: () -> Unit,
    onSwitchProfile: (Int) -> Unit,
    onMoveApp: (appKey: String, cell: Int) -> Unit,
    onRemoveApp: (appKey: String) -> Unit,
    profileSwipe: Boolean,
    showProfileLabel: Boolean,
) {
    val thresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val haptics = LocalHaptics.current
    var iconBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var dragging by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Дотик більше не відкриває пошук (випадкові відкриття) — лише свайп угору.
                // Довге натискання на значку — це перетягування, а не налаштування.
                detectTapGestures(onLongPress = { position ->
                    if (iconBounds.values.none { it.contains(position) }) {
                        haptics.longPress()
                        onLongPress()
                    }
                })
            }
            .pointerInput(home.profileCount, profileSwipe) {
                var drag = Offset.Zero
                var handled = false
                detectDragGestures(
                    onDragStart = {
                        drag = Offset.Zero
                        handled = false
                    },
                    onDrag = { change, amount ->
                        if (handled) return@detectDragGestures
                        drag += amount
                        val horizontal = abs(drag.x) > abs(drag.y)
                        when {
                            // Свайп угору — пошук.
                            !horizontal && drag.y < -thresholdPx -> {
                                handled = true
                                change.consume()
                                onOpenSearch()
                            }
                            // Свайп уліво — наступний профіль, управо — попередній.
                            horizontal && profileSwipe && abs(drag.x) > thresholdPx && home.profileCount > 1 -> {
                                handled = true
                                change.consume()
                                haptics.tick()
                                onSwitchProfile(if (drag.x < 0) 1 else -1)
                            }
                        }
                    },
                )
            },
    ) {
        HomeGridView(
            apps = home.pinned,
            cells = home.cells,
            iconFor = iconFor,
            onLaunch = onLaunch,
            onMove = onMoveApp,
            onRemove = onRemoveApp,
            onIconBounds = { iconBounds = it },
            reservedTop = if (showProfileLabel) ProfileLabelSpace else 0.dp,
            reservedBottom = SearchFieldSpace,
            onDraggingChange = { dragging = it },
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
        val topModifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp)
        // Під час перетягування вгорі зона «Прибрати» — плашку ховаємо, щоб не накладались.
        if (!dragging && showProfileLabel && home.profileName.isNotEmpty()) {
            // Постійна «скляна» плашка з назвою профілю; натискання — наступний профіль.
            ProfilePill(
                name = home.profileName,
                highlighted = profileBanner != null,
                onClick = if (home.profileCount > 1) ({ onSwitchProfile(1) }) else null,
                modifier = topModifier,
            )
        } else if (!dragging) {
            // Без плашки назва коротко з'являється вгорі під час перемикання.
            AnimatedVisibility(visible = profileBanner != null, enter = fadeIn(), exit = fadeOut(), modifier = topModifier) {
                ProfilePill(name = profileBanner.orEmpty(), highlighted = true, onClick = null)
            }
        }
    }
}

/** Місце вгорі під плашку профілю і внизу під поле пошуку — значки туди не стають. */
private val ProfileLabelSpace = 48.dp
private val SearchFieldSpace = 64.dp

@Composable
private fun ProfilePill(name: String, highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = name,
        color = if (highlighted) Oled.TextPrimary else Oled.TextSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(shape)
            .background(Oled.Surface)
            .border(1.dp, Oled.Glint, shape)
            .then(if (onClick != null) Modifier.clickable { haptics.tap(); onClick() } else Modifier)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchOpen(
    state: LauncherUiState.SearchOpen,
    /** false — панель уже закривається (анімація виходу). */
    visible: Boolean,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onGlobal: (GlobalResult) -> Unit,
    /** Команда з результатів пошуку («wifi», «таймер 10 хвилин»). */
    onAction: (VoiceAction) -> Unit,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    voiceState: VoiceState,
    onMicClick: () -> Unit,
    onKeyboardHidden: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    val thresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val close by rememberUpdatedState(onSwipeDown)
    val scope = rememberCoroutineScope()
    // Наскільки панель пошуку відтягнута вниз (з «гумовим» опором) і висота панелі.
    var pull by remember { mutableFloatStateOf(0f) }
    var panelHeight by remember { mutableFloatStateOf(0f) }
    var listWasScrolled by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var keyboardWasShown by remember { mutableStateOf(false) }
    // Клавіатура ховається разом із панеллю, а не після неї.
    fun hideKeyboard() {
        keyboardWasShown = false
        focusManager.clearFocus(force = true)
    }
    // Пошук знову відкрили, поки панель ще від'їжджала, — повертаємо її на місце;
    // почали закривати (Назад, свайп) — клавіатура їде вниз одночасно з панеллю.
    LaunchedEffect(visible) { if (visible) pull = 0f else hideKeyboard() }

    // Відпустили палець: відтягнули достатньо — панель плавно їде вниз і пошук закривається;
    // замало — пружно повертається на місце (видно, що це край списку).
    fun release(canClose: Boolean) {
        val needed = thresholdPx * if (listWasScrolled) LONG_PULL else PULL
        scope.launch {
            if (canClose && pull > needed) {
                hideKeyboard()
                animate(pull, panelHeight.coerceAtLeast(pull), animationSpec = tween(CLOSE_MS, easing = FastOutLinearInEasing)) { v, _ -> pull = v }
                close()
            } else {
                animate(pull, 0f, animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> pull = v }
            }
        }
    }

    // Свайп униз закриває пошук: і по вільному місцю, і з краю списку результатів.
    // Але не тим самим жестом, яким гортали список: дійшли до кінця — список лише пружинить.
    // Якщо список уже гортали, закрити можна окремим, довшим свайпом.
    val pullToClose = remember(thresholdPx) {
        object : NestedScrollConnection {
            var scrolledThisGesture = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Палець пішов назад угору — спершу повертаємо відтягнуту панель.
                if (source != NestedScrollSource.UserInput || pull <= 0f || available.y >= 0f) return Offset.Zero
                val next = (pull + available.y * RESISTANCE).coerceAtLeast(0f)
                val used = (next - pull) / RESISTANCE
                pull = next
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (consumed.y != 0f) {
                    scrolledThisGesture = true
                    listWasScrolled = true
                }
                if (available.y > 0f) {
                    pull += available.y * RESISTANCE
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            // Палець відпущено — жест закінчився.
            override suspend fun onPreFling(available: Velocity): Velocity {
                val canClose = !scrolledThisGesture
                scrolledThisGesture = false
                if (pull <= 0f) return Velocity.Zero
                release(canClose)
                return available
            }
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var fieldFocused by remember { mutableStateOf(false) }
    // Пішли в іншу програму — ховаємо клавіатуру; повернулись — результати на місці, без клавіатури.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) hideKeyboard() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Рахуємо клавіатуру «нашою», лише коли вона відкрилась для поля пошуку. Інакше залишковий
    // стан клавіатури з попередньої програми закривав пошук одразу після відкриття.
    LaunchedEffect(imeVisible, fieldFocused) {
        if (imeVisible) {
            if (fieldFocused) keyboardWasShown = true
        } else if (keyboardWasShown && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            keyboardWasShown = false
            onKeyboardHidden()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { panelHeight = it.height.toFloat() }
            .graphicsLayer {
                translationY = pull
                alpha = if (panelHeight > 0f) (1f - pull / panelHeight).coerceIn(0f, 1f) else 1f
            }
            .background(Color(0x40000000))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .nestedScroll(pullToClose)
            .pointerInput(thresholdPx) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        pull = (pull + amount * RESISTANCE).coerceAtLeast(0f)
                    },
                    onDragEnd = { release(canClose = true) },
                    onDragCancel = { release(canClose = false) },
                )
            },
    ) {
        SearchResults(
            query = state.query,
            results = state.results,
            groups = state.groups,
            iconFor = iconFor,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
            onGlobal = onGlobal,
            actions = state.actions,
            onAction = onAction,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            onClear = onClearQuery,
            voice = voiceState,
            onMicClick = onMicClick,
            onFocusChange = { fieldFocused = it },
        )
    }
}
