package com.blacklaunch.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// Етап 0: тимчасовий підпис, щоб на телефоні було видно, що APK запустився.
// На Етапі 1 екран стане повністю чорним.
@Composable
fun BlackHomeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "BlackLaunch 0.0.1", color = Color.DarkGray)
    }
}
