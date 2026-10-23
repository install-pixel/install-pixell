package com.blacklaunch.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.ui.theme.Oled

@Composable
fun SearchResults(
    query: String,
    results: List<AppInfo>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (query.isBlank()) {
        Box(modifier)
        return
    }
    if (results.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.BottomStart) {
            Text("Нічого не знайдено", color = Oled.TextSecondary, modifier = Modifier.padding(16.dp))
        }
        return
    }
    // Найкращий результат — унизу, ближче до поля пошуку й великого пальця.
    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        verticalArrangement = Arrangement.Bottom,
    ) {
        items(results, key = { it.key }) { app ->
            AppRow(app, iconFor = iconFor, onLaunch = onLaunch)
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
) {
    val icon by produceState<ImageBitmap?>(null, app.key) { value = iconFor(app) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .clickable { onLaunch(app) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val current = icon
        if (current != null) {
            Image(bitmap = current, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Spacer(Modifier.size(36.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(app.label, color = Oled.TextPrimary, fontSize = 18.sp)
    }
}
