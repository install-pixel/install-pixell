package com.blacklaunch.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun BlackHomeScreen() {
    // Лаунчер — нижній екран системи: Back на ньому нічого не робить.
    BackHandler {}
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    )
}
