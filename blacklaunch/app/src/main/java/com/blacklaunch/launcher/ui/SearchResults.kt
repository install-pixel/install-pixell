package com.blacklaunch.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.global.GlobalResult
import com.blacklaunch.launcher.global.GroupKind
import com.blacklaunch.launcher.global.ResultGroup
import com.blacklaunch.launcher.ui.theme.Oled

@Composable
fun SearchResults(
    query: String,
    results: List<AppInfo>,
    groups: List<ResultGroup>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onGlobal: (GlobalResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (query.isBlank()) {
        Box(modifier)
        return
    }
    if (results.isEmpty() && groups.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.BottomStart) {
            Text(stringResource(R.string.nothing_found), color = Oled.TextSecondary, modifier = Modifier.padding(16.dp))
        }
        return
    }
    val calculator = groups.filter { it.kind == GroupKind.CALCULATOR }
    val others = groups.filter { it.kind != GroupKind.CALCULATOR }.sortedBy { it.kind.ordinal }
    // Найкращий результат — унизу, ближче до поля пошуку й великого пальця:
    // калькулятор, програми, далі групи; заголовок групи — над її рядками.
    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        verticalArrangement = Arrangement.Bottom,
    ) {
        calculator.forEach { group ->
            items(group.items, key = { it.key }) { GlobalRow(it, onGlobal) }
        }
        items(results, key = { it.key }) { app ->
            AppRow(app, iconFor = iconFor, onLaunch = onLaunch, onLongPress = onLongPress)
        }
        others.forEach { group ->
            items(group.items, key = { it.key }) { GlobalRow(it, onGlobal) }
            item(key = "header:${group.kind}") { GroupHeader(group.kind) }
        }
    }
}

/** Рядок програми: значок + назва. Спільний для результатів пошуку й закріплених програм. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppRow(
    app: AppInfo,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    val icon by produceState<ImageBitmap?>(null, app.key) { value = iconFor(app) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
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

@Composable
private fun GroupHeader(kind: GroupKind) {
    Text(
        text = stringResource(kind.title()),
        color = Oled.TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun GlobalRow(result: GlobalResult, onClick: (GlobalResult) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .clickable { onClick(result) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (result is GlobalResult.Calculation) {
                Text("=", color = Oled.TextPrimary, fontSize = 22.sp)
            } else {
                Icon(result.icon(), contentDescription = null, tint = Oled.TextSecondary)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(result.title, color = Oled.TextPrimary, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            result.subtitle?.let {
                Text(it, color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun GlobalResult.icon(): ImageVector = when (this) {
    is GlobalResult.Calculation -> Icons.Filled.Check
    is GlobalResult.Profile -> Icons.Filled.AccountCircle
    is GlobalResult.Setting -> Icons.Filled.Settings
    is GlobalResult.Shortcut -> Icons.Filled.Star
    is GlobalResult.Contact -> Icons.Filled.Person
    is GlobalResult.Event -> Icons.Filled.DateRange
    is GlobalResult.Sms -> Icons.Filled.Email
}

@StringRes
private fun GroupKind.title(): Int = when (this) {
    GroupKind.CALCULATOR -> R.string.group_calculator
    GroupKind.PROFILES -> R.string.group_profiles
    GroupKind.SETTINGS -> R.string.group_settings
    GroupKind.SHORTCUTS -> R.string.group_shortcuts
    GroupKind.CONTACTS -> R.string.group_contacts
    GroupKind.CALENDAR -> R.string.group_calendar
    GroupKind.SMS -> R.string.group_sms
}
