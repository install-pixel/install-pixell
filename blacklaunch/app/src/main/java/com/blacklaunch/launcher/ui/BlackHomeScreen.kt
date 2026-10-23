package com.blacklaunch.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.settings.Appearance
import com.blacklaunch.launcher.viewmodel.LauncherUiState
import com.blacklaunch.launcher.voice.OfflineStatus
import com.blacklaunch.launcher.voice.VoiceState

private val SwipeUpThreshold = 48.dp

@Composable
fun BlackHomeScreen(
    state: LauncherUiState,
    onOpenSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onCloseSearch: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    appearance: Appearance,
    onAppearanceChange: ((Appearance) -> Appearance) -> Unit,
    onSetBlackWallpaperNow: () -> Unit,
    onOpenSystemTheme: () -> Unit,
    offlineStatus: Map<String, OfflineStatus>,
    onCheckOffline: () -> Unit,
    onDownloadOffline: (String) -> Unit,
    voiceState: VoiceState,
    onMicClick: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // На головному екрані Back нічого не робить; у пошуку — закриває його.
    BackHandler {
        if (state is LauncherUiState.SearchOpen) onCloseSearch()
    }

    when (state) {
        LauncherUiState.HomeIdle -> HomeIdle(onOpenSearch, onLongPress = { showSettings = true })
        is LauncherUiState.SearchOpen ->
            SearchOpen(state, onQueryChange, onClearQuery, onLaunch, iconFor, voiceState, onMicClick)
    }

    if (showSettings) {
        SettingsSheet(
            appearance = appearance,
            onChange = onAppearanceChange,
            onSetBlackWallpaperNow = onSetBlackWallpaperNow,
            onOpenSystemTheme = onOpenSystemTheme,
            offlineStatus = offlineStatus,
            onCheckOffline = onCheckOffline,
            onDownloadOffline = onDownloadOffline,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun HomeIdle(onOpenSearch: () -> Unit, onLongPress: () -> Unit) {
    val thresholdPx = with(LocalDensity.current) { SwipeUpThreshold.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onOpenSearch() }, onLongPress = { onLongPress() })
            }
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        dragged += dragAmount
                        if (dragged < -thresholdPx) {
                            change.consume()
                            dragged = 0f
                            onOpenSearch()
                        }
                    },
                )
            },
    )
}

@Composable
private fun SearchOpen(
    state: LauncherUiState.SearchOpen,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    voiceState: VoiceState,
    onMicClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SearchResults(
            query = state.query,
            results = state.results,
            iconFor = iconFor,
            onLaunch = onLaunch,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            onClear = onClearQuery,
            listening = voiceState is VoiceState.Listening,
            partialSpeech = (voiceState as? VoiceState.Listening)?.partial.orEmpty(),
            onMicClick = onMicClick,
        )
    }
}
