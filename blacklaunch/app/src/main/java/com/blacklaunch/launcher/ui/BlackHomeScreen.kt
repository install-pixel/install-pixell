package com.blacklaunch.launcher.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.global.GlobalResult
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.viewmodel.HomeState
import com.blacklaunch.launcher.viewmodel.LauncherUiState
import com.blacklaunch.launcher.voice.VoiceState
import kotlin.math.abs

private val SwipeThreshold = 48.dp

@Composable
fun BlackHomeScreen(
    state: LauncherUiState,
    home: HomeState,
    /** Назва профілю, на який щойно перемкнули (показується на мить), або null. */
    profileBanner: String?,
    onSwitchProfile: (delta: Int) -> Unit,
    onOpenSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onCloseSearch: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onGlobal: (GlobalResult) -> Unit,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    voiceState: VoiceState,
    onMicClick: () -> Unit,
    /** Налаштування на весь екран (довге натискання на чорному екрані). */
    settingsScreen: @Composable (onDismiss: () -> Unit) -> Unit,
    /** Власні назви програми (довге натискання на результат). */
    aliasDialog: @Composable (app: AppInfo, onDismiss: () -> Unit) -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var aliasApp by remember { mutableStateOf<AppInfo?>(null) }

    // На головному екрані Back нічого не робить; у пошуку — закриває його.
    BackHandler {
        if (state is LauncherUiState.SearchOpen) onCloseSearch()
    }

    if (showSettings) {
        settingsScreen { showSettings = false }
        return
    }

    when (state) {
        LauncherUiState.HomeIdle -> HomeIdle(
            home = home,
            profileBanner = profileBanner,
            iconFor = iconFor,
            onLaunch = onLaunch,
            onOpenSearch = onOpenSearch,
            onLongPress = { showSettings = true },
            onSwitchProfile = onSwitchProfile,
        )
        is LauncherUiState.SearchOpen -> SearchOpen(
            state = state,
            onQueryChange = onQueryChange,
            onClearQuery = onClearQuery,
            onLaunch = onLaunch,
            onLongPress = { aliasApp = it },
            onGlobal = onGlobal,
            iconFor = iconFor,
            voiceState = voiceState,
            onMicClick = onMicClick,
            // Сховали клавіатуру кнопкою «Назад» — закриваємо й пошук, одним рухом.
            onKeyboardHidden = {
                if (aliasApp == null && voiceState == VoiceState.Idle) onCloseSearch()
            },
            onSwipeDown = onCloseSearch,
        )
    }

    aliasApp?.let { app -> aliasDialog(app) { aliasApp = null } }
}

@Composable
private fun HomeIdle(
    home: HomeState,
    profileBanner: String?,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onOpenSearch: () -> Unit,
    onLongPress: () -> Unit,
    onSwitchProfile: (Int) -> Unit,
) {
    val thresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val view = LocalView.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Дотик більше не відкриває пошук (випадкові відкриття) — лише свайп угору.
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .pointerInput(home.profileCount) {
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
                            horizontal && abs(drag.x) > thresholdPx && home.profileCount > 1 -> {
                                handled = true
                                change.consume()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onSwitchProfile(if (drag.x < 0) 1 else -1)
                            }
                        }
                    },
                )
            },
    ) {
        if (home.pinned.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            ) {
                home.pinned.forEach { app ->
                    AppRow(app, iconFor = iconFor, onLaunch = onLaunch, onLongPress = {})
                }
            }
        }
        AnimatedVisibility(
            visible = profileBanner != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                text = profileBanner.orEmpty(),
                color = Oled.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchOpen(
    state: LauncherUiState.SearchOpen,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onGlobal: (GlobalResult) -> Unit,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    voiceState: VoiceState,
    onMicClick: () -> Unit,
    onKeyboardHidden: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    val thresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val close by rememberUpdatedState(onSwipeDown)
    // Свайп униз закриває пошук: і по вільному місцю, і коли список результатів уже догорнуто.
    val pullToClose = remember(thresholdPx) {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > thresholdPx) {
                        pulled = 0f
                        close()
                    }
                } else {
                    pulled = 0f
                }
                return Offset.Zero
            }
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var keyboardWasShown by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
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
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .nestedScroll(pullToClose)
            .pointerInput(thresholdPx) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { change, amount ->
                        dragged += amount
                        if (dragged > thresholdPx) {
                            change.consume()
                            dragged = 0f
                            close()
                        }
                    },
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
