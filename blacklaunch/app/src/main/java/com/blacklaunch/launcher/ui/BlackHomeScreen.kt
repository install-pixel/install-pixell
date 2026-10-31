package com.blacklaunch.launcher.ui

import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.commands.CustomCommand
import com.blacklaunch.launcher.global.GlobalResult
import com.blacklaunch.launcher.global.GroupKind
import com.blacklaunch.launcher.system.GlanceInfo
import com.blacklaunch.launcher.system.PrivateSpaceState
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.viewmodel.HomeState
import com.blacklaunch.launcher.viewmodel.LauncherUiState
import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.voice.VoiceState
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val SwipeThreshold = 48.dp

/** Скільки після увімкнення екрана подвійний дотик не перемикає профіль (дотик «розбудити» — не команда). */
private const val WAKE_GUARD_MS = 800L
private const val REVEAL_DELAY_MS = 150L
private const val HIDE_MS = 90
private const val REVEAL_MS = 220

/** «Гумовий» опір: панель їде вниз удвічі повільніше за палець. */
private const val RESISTANCE = 0.5f

/** Скільки (у порогах свайпу) треба відтягнути панель, щоб пошук закрився. */
private const val PULL = 1f

/** Те саме, якщо список уже гортали, — навмисний, довший свайп (до 0.33.0 було 2.5 — закривалось лише з третьої спроби). */
private const val LONG_PULL = 1.8f

/** Швидкий змах униз (пікселів за секунду) закриває пошук і з коротшим відтягуванням. */
private const val FLICK_VELOCITY = 1500f

/** Частка порогу, якої досить для швидкого змаху: легкий дотик усе одно нічого не закриває. */
private const val FLICK_PULL = 0.4f

/** Тривалість анімації закриття пошуку. */
private const val CLOSE_MS = 280
private const val MIN_CLOSE_MS = 120
/** Плашка з назвою режиму з'являється й тане плавно. */
private const val BANNER_FADE_MS = 400
/** Спуск після «Назад» — трохи повільніший за змах. */
private const val BACK_CLOSE_MS = 360
private val HOME_BLUR = 10.dp
private const val HOME_DIM = 0.7f
/** Скільки чекати, поки клавіатура сховається, перш ніж панель поїде вниз. */
private const val IME_WAIT_MS = 600L
/** Меню програми з'являється, коли клавіатура вже з'їхала. */
private const val MENU_AFTER_KEYBOARD_MS = 220L
/** Меню закрили — клавіатура повертається трохи згодом (щоб меню встигло зникнути). */
private const val KEYBOARD_BACK_DELAY_MS = 120L
private const val KEYBOARD_BACK_WAIT_MS = 700L
/** За скільки фон проясняється, коли пошук закривається. */
private const val BLUR_FADE_MS = 420
/** Гумка вгору: наскільки туго й наскільки далеко панель піддається. */
private const val UP_RESISTANCE = 0.25f
private val MAX_UP = 48.dp
/** Яка частка клавіатури ще видна, коли панель уже підхоплює спуск («Назад» із клавіатурою). */
private const val IME_HANDOFF = 0.35f
/** Найменший шлях спуску панелі (щоб навіть порожній пошук закривався рухом, а не стрибком). */
private val MIN_HIDE = 160.dp
/** Клавіатуру відпустили — за скільки вона доїжджає вниз чи повертається. */
private const val IME_SETTLE_MS = 220
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
    /** «Короткий огляд» (дата, будильник, подія, батарея) або null — вимкнено. */
    glance: GlanceInfo? = null,
    /** Головний екран чорний і нічого не відкрито (щоб сховати рядок стану). */
    onIdleChange: (Boolean) -> Unit = {},
    /** false — пошук закривається миттєво (повернулись після довгої відсутності). */
    animateSearchClose: Boolean = true,
    onQueryChange: (String) -> Unit,
    /** Натиснули на поле пошуку (сказане голосом звільняє місце для набору). */
    onFieldFocused: () -> Unit = {},
    onClearQuery: () -> Unit,
    onCloseSearch: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onGlobal: (GlobalResult) -> Unit,
    /** Команда з результатів пошуку («wifi», «таймер 10 хвилин»). */
    onAction: (VoiceAction) -> Unit,
    /** Власна команда з результатів пошуку. */
    onCustom: (CustomCommand) -> Unit,
    /** Останній запит у порожньому пошуку: натискання — шукати знову, довге — прибрати. */
    onRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    /** Усі програми за абеткою (список A–Z у порожньому пошуку). */
    allApps: List<AppInfo>,
    /** Приватний простір Android 15+ унизу списку A–Z; null — його немає. */
    privateSpace: PrivateSpaceState? = null,
    onTogglePrivate: () -> Unit = {},
    /** Профіль-монітор: замість програм на чорному екрані. */
    monitor: (@Composable (Modifier) -> Unit)? = null,
    /** «Показати всі» / «Згорнути» під групою результатів. */
    onExpand: (GroupKind?) -> Unit = {},
    /** Мініатюра фото чи відео за адресою в сховищі. */
    thumbFor: suspend (String) -> ImageBitmap? = { null },
    onShareMedia: (List<GlobalResult.Media>) -> Unit = {},
    onTrashMedia: ((List<GlobalResult.Media>) -> Unit)? = null,
    /** Біля контакту в пошуку: true — подзвонити, false — написати. */
    onContactAction: (GlobalResult.Contact, Boolean) -> Unit,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    voiceState: VoiceState,
    onMicClick: () -> Unit,
    /** Налаштування на весь екран (довге натискання на чорному екрані). */
    settingsScreen: @Composable (onDismiss: () -> Unit) -> Unit,
    /** Відкрити налаштування ззовні (з результату пошуку): кожне збільшення — відкрити. */
    settingsRequest: Int = 0,
    /** Збільшується, коли налаштування треба закрити (почався фокус-таймер). */
    settingsClose: Int = 0,
    /** Пакети програм зі сповіщеннями — крапки біля значків. */
    dots: Set<String> = emptySet(),
    /** «Що грає» внизу головного екрана (null — немає). */
    nowPlaying: (@Composable (Modifier) -> Unit)? = null,
    /** Відкрити налаштування — через «Зосередження» (таймер, «Важкий вихід»). */
    onSettingsRequest: (open: () -> Unit) -> Unit = { it() },
    /** Власні назви програми (з меню програми). */
    aliasDialog: @Composable (app: AppInfo, onDismiss: () -> Unit) -> Unit,
    /** Меню програми (довге натискання на результат пошуку). */
    appActions: @Composable (app: AppInfo, onNames: () -> Unit, onDismiss: () -> Unit) -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settingsRequest) { if (settingsRequest > 0) showSettings = true }
    LaunchedEffect(settingsClose) { if (settingsClose > 0) showSettings = false }
    // Повністю чорний екран: рядок стану схований і на головному, і в пошуку (з клавіатурою теж) — з'являється
    // лише жестом згори; у налаштуваннях — видно.
    val idle = !showSettings
    LaunchedEffect(idle) { onIdleChange(idle) }
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
    // Розмиття й притемнення фону: з'являються з відкриттям пошуку, а при закритті тануть рівно настільки,
    // наскільки панель уже опущена (тягнете пальцем — фон світлішає під пальцем).
    val openAmount = remember { Animatable(0f) }
    var closeFraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            closeFraction = 0f
            openAmount.animateTo(1f, tween(OPEN_MS))
        } else if (closeFraction >= 1f || !animateSearchClose) {
            // Панель уже доїхала донизу (свайп, «Назад») — фон уже ясний; або закрили без анімації.
            openAmount.snapTo(0f)
            closeFraction = 0f
        } else {
            openAmount.animateTo(0f, tween(CLOSE_MS))
        }
    }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Під пошуком головний екран лишається на фоні — розмитий і притемнений.
        HomeIdle(
            home = home,
            profileBanner = profileBanner,
            iconFor = iconFor,
            onLaunch = onLaunch,
            onOpenSearch = onOpenSearch,
            onLongPress = { onSettingsRequest { showSettings = true } },
            onLongPressApp = { menuApp = it },
            onSwitchProfile = onSwitchProfile,
            onMoveApp = onMoveApp,
            onRemoveApp = onRemoveApp,
            profileSwipe = profileSwipe,
            showProfileLabel = showProfileLabel,
            glance = glance,
            monitor = monitor,
            dots = dots,
            nowPlaying = nowPlaying,
            // Значення читаються лише під час малювання — головний екран не перебудовується щокадру.
            modifier = Modifier.graphicsLayer {
                val k = openAmount.value * (1f - closeFraction)
                val radius = with(density) { HOME_BLUR.toPx() } * k
                renderEffect = if (radius > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(radius, radius, TileMode.Decal) else null
                alpha = 1f - (1f - HOME_DIM) * k
                clip = true
            },
        )
        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(tween(OPEN_MS)) + slideInVertically(tween(OPEN_MS)) { it / 10 },
            // Панель уже з'їхала сама (свайп, «Назад») — прибираємо її одразу: інакше ще чверть секунди
            // невидима панель перехоплювала б наступний жест, і він спрацьовував лише з другого разу.
            exit = if (animateSearchClose && closeFraction == 0f) {
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
                onFieldFocused = onFieldFocused,
                onClearQuery = onClearQuery,
                onLaunch = onLaunch,
                onLongPress = { menuApp = it },
                onGlobal = onGlobal,
                onAction = onAction,
                onCustom = onCustom,
                onRecent = onRecent,
                onRemoveRecent = onRemoveRecent,
                allApps = allApps,
                privateSpace = privateSpace,
                onTogglePrivate = onTogglePrivate,
                onExpand = onExpand,
                thumbFor = thumbFor,
                onShareMedia = onShareMedia,
                onTrashMedia = onTrashMedia,
                onContactAction = onContactAction,
                iconFor = iconFor,
                voiceState = voiceState,
                onMicClick = onMicClick,
                // Сховали клавіатуру кнопкою «Назад» — закриваємо й пошук, одним рухом (якщо не відкрите вікно чи голос).
                canCloseWithKeyboard = { aliasApp == null && menuApp == null && voiceState == VoiceState.Idle },
                // Відкрили меню програми — клавіатура на цей час ховається (вікно забирає фокус), а місце під неї
                // лишається: список і поле не стрибають ні вниз, ні назад угору, коли меню закрили.
                holdKeyboardSpace = aliasApp != null || menuApp != null,
                onSwipeDown = onCloseSearch,
                onCloseProgress = { closeFraction = it },
            )
        }
    }

    menuApp?.let { app -> appActions(app, { menuApp = null; aliasApp = app }) { menuApp = null } }
    aliasApp?.let { app -> aliasDialog(app) { aliasApp = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeIdle(
    modifier: Modifier = Modifier,
    home: HomeState,
    profileBanner: String?,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onOpenSearch: () -> Unit,
    onLongPress: () -> Unit,
    /** Довго натиснули на програмі й відпустили, не перетягуючи, — меню програми. */
    onLongPressApp: (AppInfo) -> Unit = {},
    onSwitchProfile: (Int) -> Unit,
    onMoveApp: (appKey: String, cell: Int) -> Unit,
    onRemoveApp: (appKey: String) -> Unit,
    profileSwipe: Boolean,
    showProfileLabel: Boolean,
    glance: GlanceInfo?,
    monitor: (@Composable (Modifier) -> Unit)? = null,
    dots: Set<String> = emptySet(),
    nowPlaying: (@Composable (Modifier) -> Unit)? = null,
) {
    // На моніторі свій верхній рядок — «Короткий огляд» не накладається на нього.
    @Suppress("NAME_SHADOWING")
    val glance = if (monitor != null) null else glance
    // Екран увімкнули подвійним дотиком — цей самий дотик не має перемикати профіль.
    val lifecycleOwner = LocalLifecycleOwner.current
    val shownAt = remember { mutableLongStateOf(0L) }
    // Список останніх програм: OxygenOS показує напівпрозорі картки поверх нашого екрана (і коли входимо, і коли
    // виходимо), і світлий текст огляду просвічував крізь них. Тож щойно Black Glass втрачає фокус чи паузиться —
    // головний екран швидко гасне, а з'являється плавно, коли картки вже зникли.
    val reveal = remember { Animatable(1f) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val shownNow = windowFocused && resumed
    LaunchedEffect(shownNow) {
        if (!shownNow) {
            reveal.animateTo(0f, tween(HIDE_MS))
        } else if (reveal.value < 1f) {
            delay(REVEAL_DELAY_MS)
            reveal.animateTo(1f, tween(REVEAL_MS))
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) shownAt.longValue = SystemClock.uptimeMillis()
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val thresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val haptics = LocalHaptics.current
    var iconBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    val profileCount = rememberUpdatedState(home.profileCount)
    var dragging by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = reveal.value }
            .pointerInput(Unit) {
                // Дотик більше не відкриває пошук (випадкові відкриття) — лише свайп угору.
                // Довге натискання на значку — це перетягування, а не налаштування.
                detectTapGestures(
                    onLongPress = { position ->
                        if (iconBounds.values.none { it.contains(position) }) {
                            haptics.longPress()
                            onLongPress()
                        }
                    },
                    // Подвійний дотик на порожньому місці — наступний профіль (коли немає ні назви, ні свайпу).
                    onDoubleTap = { position ->
                        val justWoke = SystemClock.uptimeMillis() - shownAt.longValue < WAKE_GUARD_MS
                        // На моніторингу подвійний дотик не перемикає профіль: випадкові дотики там часті, а профіль може вимкнути звук.
                        if (monitor == null && !justWoke && profileCount.value > 1 && iconBounds.values.none { it.contains(position) }) {
                            haptics.tick()
                            onSwitchProfile(1)
                        }
                    },
                )
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
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        // Профіль-монітор: замість значків — монітор системи (під назвою профілю, над полем пошуку).
        if (monitor != null) {
            // Моніторинг прокручується; свайп угору — пошук, але лише окремим жестом, почавши вже внизу:
            // якщо цим самим рухом гортали картки, пошук не вискакує посеред прокрутки (було «нервово»).
            val searchPull = remember(thresholdPx) {
                object : NestedScrollConnection {
                    var pull = 0f
                    var scrolled = false
                    var opened = false
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (source != NestedScrollSource.UserInput) return Offset.Zero
                        if (consumed.y != 0f) scrolled = true
                        if (scrolled || opened) return Offset.Zero
                        pull = if (available.y < 0f) pull - available.y else 0f
                        if (pull > thresholdPx) {
                            opened = true
                            onOpenSearch()
                        }
                        return Offset.Zero
                    }
                    // Палець відпустили — наступний жест рахуємо заново.
                    override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                        pull = 0f
                        scrolled = false
                        opened = false
                        return androidx.compose.ui.unit.Velocity.Zero
                    }
                }
            }
            monitor(
                Modifier
                    .nestedScroll(searchPull)
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .navigationBarsPadding()
                    .padding(top = 16.dp, bottom = SearchFieldSpace),
            )
        } else HomeGridView(
            apps = home.pinned,
            cells = home.cells,
            iconFor = iconFor,
            onLaunch = onLaunch,
            onMove = onMoveApp,
            onRemove = onRemoveApp,
            onIconBounds = { iconBounds = it },
            // З оглядом назва профілю — його перший рядок, окремого місця під плашку не треба.
            // Великий шрифт Android — більше місця під текст, щоб значки не налазили.
            reservedTop = when {
                glance != null -> GlanceSpace + ((if (showProfileLabel) GlanceProfileLine else 0.dp) +
                    (if (glance.time != null) GlanceClockLine else 0.dp) +
                    (if (glance.battery != null) GlanceBatteryLine else 0.dp)) * fontScale
                showProfileLabel -> ProfileLabelSpace * fontScale
                else -> 0.dp
            },
            reservedBottom = SearchFieldSpace,
            onDraggingChange = { dragging = it },
            dots = dots,
            onLongPressApp = onLongPressApp,
            modifier = Modifier
                // Місце під рядок стану лишається, навіть коли він схований, — значки не стрибають.
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
        // «Що грає» — у смузі під поле пошуку, тож значки не зсуваються; на моніторингу й під час перетягування — ні.
        if (monitor == null && !dragging) {
            // Там само, де поле пошуку (той самий відступ знизу), — пошук відкривається на місці плашки.
            nowPlaying?.invoke(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
        }
        val topModifier = Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(top = 16.dp)
        // Короткий огляд одним блоком зліва: назва профілю (натискання — наступний), дата, подробиці.
        if (glance != null && !dragging) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp),
            ) {
                if (showProfileLabel && home.profileName.isNotEmpty()) {
                    Text(
                        text = (profileBanner ?: home.profileName).uppercase(),
                        color = if (profileBanner != null) Oled.TextPrimary else Oled.TextSecondary,
                        fontSize = 12.sp,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .then(if (home.profileCount > 1) Modifier.clickable { haptics.tap(); onSwitchProfile(1) } else Modifier)
                            .padding(vertical = 2.dp),
                    )
                }
                glance.time?.let { Text(it, color = Oled.TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Light) }
                Text(glance.date, color = Oled.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                // Батарейка намальована (у «Аркуші» — олівцем, у склі — металева), під нею — відсотки.
                if (glance.details.isNotEmpty()) Text(glance.details, color = Oled.TextSecondary, fontSize = 13.sp)
                glance.battery?.let { HandBattery(it, Modifier.padding(top = 8.dp)) }
            }
        }
        // Під час перетягування вгорі зона «Прибрати» — плашку ховаємо, щоб не накладались.
        // На моніторі плашки немає: назва профілю — у верхньому рядку монітора (не «віджет у віджеті»).
        if (monitor == null && glance == null && !dragging && showProfileLabel && home.profileName.isNotEmpty()) {
            // Постійна «скляна» плашка з назвою профілю; натискання — наступний профіль.
            ProfilePill(
                name = profileBanner ?: home.profileName,
                highlighted = profileBanner != null,
                onClick = if (home.profileCount > 1) ({ onSwitchProfile(1) }) else null,
                modifier = topModifier,
            )
        } else if (glance == null && !dragging) {
            // Без плашки назва коротко з'являється вгорі під час перемикання.
            // Поки плашка тане, на ній лишається остання назва (раніше текст зникав одразу — і на мить
            // з'являлася крихітна порожня плашка).
            var lastBanner by remember { mutableStateOf("") }
            if (profileBanner != null) lastBanner = profileBanner
            AnimatedVisibility(
                visible = profileBanner != null,
                enter = fadeIn(tween(BANNER_FADE_MS)),
                exit = fadeOut(tween(BANNER_FADE_MS)),
                modifier = topModifier,
            ) {
                ProfilePill(name = lastBanner, highlighted = true, onClick = null)
            }
        }
    }
}

/** Місце вгорі під плашку профілю і внизу під поле пошуку — значки туди не стають. */
private val ProfileLabelSpace = 48.dp
private val SearchFieldSpace = 64.dp
private val GlanceSpace = 64.dp
private val GlanceProfileLine = 20.dp
private val GlanceClockLine = 52.dp
/** «Аркуш»: намальована батарейка з підписом під оглядом. */
private val GlanceBatteryLine = 56.dp

@Composable
private fun ProfilePill(name: String, highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    TopPill(name, if (highlighted) Oled.TextPrimary else Oled.TextSecondary, onClick = onClick, modifier = modifier)
}

/**
 * Плашка вгорі головного екрана — однакова для назви режиму й зони «Прибрати»: сталого розміру (довга назва
 * скорочується «…»), на тому самому місці. Як поле пошуку й «Що грає» внизу — пара однакових плашок.
 */
@Composable
internal fun TopPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fill: Brush = Oled.Surface,
    ink: Color = SheetInk,
    onClick: (() -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    Box(
        modifier
            .size(TopPillWidth, TopPillHeight)
            .glassBox(TopPillHeight / 2, seed = 5, fill = fill, ink = ink)
            .then(if (onClick != null) Modifier.clickable { haptics.tap(); onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

internal val TopPillWidth = 220.dp
internal val TopPillHeight = 40.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchOpen(
    state: LauncherUiState.SearchOpen,
    /** false — панель уже закривається (анімація виходу). */
    visible: Boolean,
    onQueryChange: (String) -> Unit,
    onFieldFocused: () -> Unit,
    onClearQuery: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onGlobal: (GlobalResult) -> Unit,
    /** Команда з результатів пошуку («wifi», «таймер 10 хвилин»). */
    onAction: (VoiceAction) -> Unit,
    /** Власна команда з результатів пошуку. */
    onCustom: (CustomCommand) -> Unit,
    /** Останній запит у порожньому пошуку: натискання — шукати знову, довге — прибрати. */
    onRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    /** Усі програми за абеткою (список A–Z у порожньому пошуку). */
    allApps: List<AppInfo>,
    /** Приватний простір Android 15+ унизу списку A–Z; null — його немає. */
    privateSpace: PrivateSpaceState? = null,
    onTogglePrivate: () -> Unit = {},
    /** «Показати всі» / «Згорнути» під групою результатів. */
    onExpand: (GroupKind?) -> Unit = {},
    /** Мініатюра фото чи відео за адресою в сховищі. */
    thumbFor: suspend (String) -> ImageBitmap? = { null },
    onShareMedia: (List<GlobalResult.Media>) -> Unit = {},
    onTrashMedia: ((List<GlobalResult.Media>) -> Unit)? = null,
    /** Біля контакту в пошуку: true — подзвонити, false — написати. */
    onContactAction: (GlobalResult.Contact, Boolean) -> Unit,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    voiceState: VoiceState,
    onMicClick: () -> Unit,
    /** Клавіатуру сховали «Назад»: чи закривати й пошук. */
    canCloseWithKeyboard: () -> Boolean,
    /** Тримати місце під клавіатуру, навіть якщо вона на мить сховалась (поверх відкрите меню). */
    holdKeyboardSpace: Boolean = false,
    onSwipeDown: () -> Unit,
    /** Наскільки панель опущена: 0 — на місці, 1 — сховалась (для розмиття фону). */
    onCloseProgress: (Float) -> Unit = {},
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
    var closing by remember { mutableStateOf(false) }
    // Під час закриття фон проясняється власним плавним згасанням (не швидше за [BLUR_FADE_MS]), а не стрибком.
    var fade by remember { mutableFloatStateOf(-1f) }
    // Клавіатуру цим жестом опустили до кінця — відпустили палець: пошук закривається, а не пружинить назад.
    var pushedKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            pull = 0f
            fade = -1f
            closing = false
        } else {
            hideKeyboard()
        }
    }
    // Видимий вміст пошуку притиснутий донизу: поле й рядки над ним. Щоб він повністю сховався, панель має опуститися
    // лише на його висоту, а не на весь екран. Від цієї відстані рахуються і спуск, і прояснення фону — фон стає ясним
    // рівно тоді, коли сховалось останнє, що було видно.
    var fieldTop by remember { mutableFloatStateOf(0f) }
    var contentHeight by remember { mutableIntStateOf(0) }
    val minHidePx = with(LocalDensity.current) { MIN_HIDE.toPx() }
    val maxUpPx = with(LocalDensity.current) { MAX_UP.toPx() }
    fun hideDistance(): Float {
        if (panelHeight <= 0f) return 0f
        if (fieldTop <= 0f || contentHeight == Int.MAX_VALUE) return panelHeight
        return (panelHeight - (fieldTop - contentHeight)).coerceIn(minHidePx.coerceAtMost(panelHeight), panelHeight)
    }
    val progress by rememberUpdatedState(onCloseProgress)
    LaunchedEffect(Unit) {
        snapshotFlow {
            if (fade >= 0f) fade else hideDistance().let { d -> if (d > 0f) (pull / d).coerceIn(0f, 1f) else 0f }
        }.collect { progress(it) }
    }

    // Скільки клавіатури зараз видно (пікселі) — панель лежить на ній і не заходить під неї.
    val imeNow = rememberUpdatedState(WindowInsets.ime.getBottom(LocalDensity.current))
    val view = LocalView.current
    val imeDrag = remember(view) { ImeDrag(view) }
    // Клавіатуру опустили пальцем самі — знаємо, що її вже немає, і не чекаємо системного «сховалась»
    // (воно приходить із запізненням, і виходила пауза перед спуском панелі).
    var imeReleased by remember { mutableStateOf(false) }
    val keyboardThere = { imeNow.value > 0 && !imeReleased }
    // Повна висота клавіатури (найбільша, яку бачили) — щоб знати, яку частину шляху вона вже пройшла.
    var imeFull by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { snapshotFlow { imeNow.value }.collect { if (it > imeFull) imeFull = it } }

    // Одне й те саме закриття для свайпу, змаху, «Назад» і клавіатури: клавіатура ховається, панель їде вниз
    // разом із нею (лежить на ній), а тоді плавно доїжджає донизу — і лише тоді пошук закривається.
    // [alreadyMoving] — до панелі щойно рухалась клавіатура: панель підхоплює рух рівно, без розгону з місця.
    // [fromBack] — закрили кнопкою чи жестом «Назад»: спуск трохи повільніший, спокійніший.
    fun closeSmoothly(alreadyMoving: Boolean = false, fromBack: Boolean = false) {
        if (closing) return
        closing = true
        hideKeyboard()
        scope.launch {
            // Панель їхала разом із клавіатурою — далі рівний рух, без нового розгону.
            val rideKeyboard = keyboardThere()
            // Системна анімація клавіатури в кінці дуже сповільнюється — не чекаємо останніх пікселів: панель
            // підхоплює рух, щойно клавіатура пройшла більшу частину шляху.
            if (rideKeyboard) {
                withTimeoutOrNull(IME_WAIT_MS) {
                    snapshotFlow { !keyboardThere() || imeNow.value <= imeFull * IME_HANDOFF }.first { it }
                }
            }
            val distance = hideDistance()
            val start = if (distance > 0f) (pull / distance).coerceIn(0f, 1f) else 0f
            launch {
                animate(start, 1f, animationSpec = tween((BLUR_FADE_MS * (1f - start)).toInt().coerceAtLeast(MIN_CLOSE_MS), easing = FastOutSlowInEasing)) { v, _ -> fade = v }
            }
            val target = distance.coerceAtLeast(pull)
            val left = if (target > 0f) (target - pull) / target else 1f
            animate(pull, target, animationSpec = tween(((if (fromBack) BACK_CLOSE_MS else CLOSE_MS) * left).toInt().coerceAtLeast(MIN_CLOSE_MS), easing = if (alreadyMoving || rideKeyboard) LinearEasing else FastOutLinearInEasing)) { v, _ -> pull = v }
            // Панель доїхала — пошук закривається одразу (фон догасає сам), щоб невидима панель не ловила дотики.
            close()
        }
    }
    BackHandler(enabled = visible && !closing) { closeSmoothly(fromBack = true) }

    // Палець рухається на [dy] (униз — додатне). Відкрита клавіатура йде за пальцем (поле лежить на ній, одним блоком);
    // сховалась — далі вниз їде вже панель. Клавіатура не дає себе вести — ховається сама, а панель чекає на ній,
    // доки та не сховається, і тоді йде за пальцем (ніколи не заходить під клавіатуру).
    fun dragBy(dy: Float) {
        if (pull <= 0f && keyboardWasShown) {
            if (dy > 0f) imeDrag.start()
            when {
                imeDrag.active -> {
                    val rest = imeDrag.moveBy(dy)
                    if (imeDrag.shown <= 0f) {
                        imeDrag.finish(false)
                        imeReleased = true
                        pushedKeyboard = true
                        hideKeyboard()
                    }
                    if (rest > 0f) pull += rest * RESISTANCE
                    return
                }
                imeDrag.pending -> return
                dy > 0f -> hideKeyboard()
            }
        }
        if (dy > 0f && keyboardThere()) return
        // Угору панель трохи піддається пальцю (туга гумка) і потім пружно повертається — живий відгук,
        // навіть коли гортати нема чого.
        val resistance = if (pull < 0f || (pull == 0f && dy < 0f)) UP_RESISTANCE else RESISTANCE
        pull = (pull + dy * resistance).coerceIn(-maxUpPx, Float.MAX_VALUE)
    }
    // Новий запит — новий список: довший свайп потрібен, лише якщо гортали саме його.
    LaunchedEffect(state.query) { listWasScrolled = false }

    // Відпустили палець: відтягнули достатньо — панель плавно їде вниз і пошук закривається;
    // замало — пружно повертається на місце (видно, що це край списку).
    fun release(canClose: Boolean, velocity: Float = 0f) {
        // Відпустили, поки тягнули саму клавіатуру: опустили більше половини чи змахнули — ховається клавіатура
        // і закривається пошук (одним рухом); інакше клавіатура пружно повертається.
        if (imeDrag.active) {
            val hide = canClose && (velocity > FLICK_VELOCITY || imeDrag.shown < imeDrag.full / 2f)
            val target = if (hide) 0f else imeDrag.full
            scope.launch {
                // Клавіатура доїжджає вниз, а панель одразу продовжує рух — без зупинки між ними.
                animate(imeDrag.shown, target, animationSpec = tween(IME_SETTLE_MS, easing = if (hide) LinearEasing else FastOutSlowInEasing)) { v, _ -> imeDrag.moveTo(v) }
                imeDrag.finish(!hide)
                if (hide) {
                    imeReleased = true
                    closeSmoothly(alreadyMoving = true)
                }
            }
            return
        }
        imeDrag.reset()
        val pushed = pushedKeyboard
        pushedKeyboard = false
        if (pushed && canClose && velocity >= 0f) {
            closeSmoothly(alreadyMoving = true)
            return
        }
        val needed = thresholdPx * if (listWasScrolled) LONG_PULL else PULL
        val flick = velocity > FLICK_VELOCITY && pull > thresholdPx * FLICK_PULL
        scope.launch {
            if (canClose && pull > 0f && (pull > needed || flick)) {
                closeSmoothly()
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
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Панель відтягнута вгору (гумка), а палець пішов униз — спершу повертаємо її.
                if (pull < 0f && available.y > 0f) {
                    val next = (pull + available.y * UP_RESISTANCE).coerceAtMost(0f)
                    val used = (next - pull) / UP_RESISTANCE
                    pull = next
                    return Offset(0f, used)
                }
                if (available.y >= 0f) return Offset.Zero
                // Клавіатуру опустили пальцем і ведуть назад угору — вона піднімається за пальцем.
                if (imeDrag.active) {
                    dragBy(available.y)
                    return Offset(0f, available.y)
                }
                if (pull <= 0f) return Offset.Zero
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
                if (available.y > 0f || (available.y < 0f && !imeDrag.active && consumed.y == 0f)) {
                    dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            // Палець відпущено — жест закінчився.
            override suspend fun onPreFling(available: Velocity): Velocity {
                val canClose = !scrolledThisGesture
                scrolledThisGesture = false
                if (pull == 0f && !imeDrag.active && !pushedKeyboard) {
                    imeDrag.reset()
                    return Velocity.Zero
                }
                release(canClose, available.y)
                return available
            }
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    // Скільки місця займала клавіатура над панеллю навігації — запам'ятовуємо, поки меню не відкрите.
    val navBottom = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    // Меню програми: клавіатура спершу плавно з'їжджає (сама, системною анімацією), і лише тоді — меню; закрили меню —
    // клавіатура так само плавно повертається. Увесь цей час місце під неї тримається — поле й список не стрибають.
    var hidingForMenu by remember { mutableStateOf(false) }
    val holdSpace = holdKeyboardSpace || hidingForMenu
    var heldKeyboard by remember { mutableStateOf(0.dp) }
    if (!holdSpace) heldKeyboard = with(LocalDensity.current) { (imeNow.value - navBottom).coerceAtLeast(0).toDp() }
    val activity = LocalActivity.current
    val insetsController = remember(activity, view) { activity?.window?.let { WindowCompat.getInsetsController(it, view) } }
    val showMenu by rememberUpdatedState(onLongPress)
    fun openMenuSmoothly(target: AppInfo) {
        if (imeNow.value > 0 && insetsController != null) {
            hidingForMenu = true
            insetsController.hide(WindowInsetsCompat.Type.ime())
            scope.launch {
                delay(MENU_AFTER_KEYBOARD_MS)
                showMenu(target)
            }
        } else {
            showMenu(target)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val heldPx = with(LocalDensity.current) { heldKeyboard.toPx() }
    LaunchedEffect(holdKeyboardSpace) {
        if (holdKeyboardSpace || !hidingForMenu) return@LaunchedEffect
        // Меню закрили: повертаємо клавіатуру, якщо ми ще тут (не пішли в програму), і тримаємо місце, доки вона не стане на місце.
        delay(KEYBOARD_BACK_DELAY_MS)
        if (visible && !closing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            insetsController?.show(WindowInsetsCompat.Type.ime())
            withTimeoutOrNull(KEYBOARD_BACK_WAIT_MS) { snapshotFlow { imeNow.value - navBottom >= heldPx - 2f }.first { it } }
        }
        hidingForMenu = false
    }
    var fieldFocused by remember { mutableStateOf(false) }
    // Пішли в іншу програму — ховаємо клавіатуру; повернулись — результати на місці, без клавіатури.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) hideKeyboard() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Рахуємо клавіатуру «нашою», лише коли вона відкрилась для поля пошуку. Інакше залишковий
    // стан клавіатури з попередньої програми закривав пошук одразу після відкриття.
    // Клавіатура почала ховатися («Назад») — не чекаємо, поки вона зникне: панель починає спуск одночасно з нею.
    val imeGoingAway = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) == 0
    LaunchedEffect(imeVisible, imeGoingAway, fieldFocused) {
        if (imeVisible && !imeGoingAway) {
            if (fieldFocused) {
                keyboardWasShown = true
                imeReleased = false
            }
        } else if (keyboardWasShown && !hidingForMenu && !imeDrag.active && !imeDrag.pending && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // Клавіатуру ведемо пальцем — це не «Назад», рішення прийме сам жест.
            keyboardWasShown = false
            if (canCloseWithKeyboard()) closeSmoothly(fromBack = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { panelHeight = it.height.toFloat() }
            .graphicsLayer {
                // Без прозорості: панель повністю з'їжджає за нижній край, а не «розчиняється» посеред екрана.
                translationY = pull
            }
            .background(Color(0x40000000))
            // Відступ під рядок стану — однаковий, видно рядок чи ні: витягнули його жестом — нічого не стрибає.
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .navigationBarsPadding()
            .then(if (holdSpace) Modifier.padding(bottom = heldKeyboard) else Modifier.imePadding())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .nestedScroll(pullToClose)
            .pointerInput(thresholdPx) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        dragBy(amount)
                    },
                    onDragEnd = { release(canClose = true, velocity = tracker.calculateVelocity().y) },
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
            onLongPress = ::openMenuSmoothly,
            onGlobal = onGlobal,
            actions = state.actions,
            onAction = onAction,
            custom = state.custom,
            onCustom = onCustom,
            suggestions = state.suggestions,
            recent = state.recent,
            onRecent = onRecent,
            onRemoveRecent = onRemoveRecent,
            allApps = allApps,
            privateSpace = privateSpace,
            onTogglePrivate = onTogglePrivate,
            onExpand = onExpand,
            thumbFor = thumbFor,
            onShareMedia = onShareMedia,
            onTrashMedia = onTrashMedia,
            onContactAction = onContactAction,
            canCall = state.canCall,
            canMessage = state.canMessage,
            onContentHeight = { contentHeight = it },
            // І згори, і над полем пошуку список тане, а не обрізається різко.
            modifier = Modifier.weight(1f).fillMaxWidth().fadingEdges(),
        )
        SearchField(
            // Де поле, коли панель на місці (у координатах екрана; зсув від пальця віднімаємо).
            modifier = Modifier.onGloballyPositioned { fieldTop = it.boundsInRoot().top - pull },
            query = state.query,
            onQueryChange = onQueryChange,
            onClear = onClearQuery,
            voice = voiceState,
            onMicClick = onMicClick,
            onFocusChange = {
                fieldFocused = it
                if (it) onFieldFocused()
            },
            // Найнижчий результат — той, що найближче до поля: калькулятор, своя команда, дія, програма, решта.
            onSubmit = {
                val calculation = state.groups.firstOrNull { it.kind == GroupKind.CALCULATOR }?.items?.firstOrNull()
                when {
                    calculation != null -> onGlobal(calculation)
                    state.custom.isNotEmpty() -> onCustom(state.custom.first())
                    state.actions.isNotEmpty() -> onAction(state.actions.first())
                    state.results.isNotEmpty() -> onLaunch(state.results.first())
                    else -> state.groups.sortedBy { it.kind.ordinal }.firstOrNull()?.items?.firstOrNull()?.let(onGlobal)
                }
            },
        )
    }
}
