package com.blacklaunch.qrcodes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.data.QrCode
import com.blacklaunch.qrcodes.qr.QrMatrix
import com.blacklaunch.qrcodes.ui.theme.Oled
import java.text.DateFormat
import java.util.Date

/**
 * Перша сторінка — «Вибране»: відзначені коди сіткою (на телефоні — у два стовпчики, на ширшому екрані — більше).
 * Дотик — код на весь екран, довге натискання — меню.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesPage(favorites: List<QrCode>?, onOpen: (QrCode) -> Unit, onMenu: (QrCode) -> Unit) {
    val haptics = LocalHaptics.current
    if (favorites == null) return
    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.favorites_empty), color = Oled.TextSecondary, fontSize = 18.sp, textAlign = TextAlign.Center)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(favorites, key = { it.id }) { code ->
            val matrix = remember(code.content) { QrMatrix.encode(code.content) }
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { haptics.tap(); onOpen(code) },
                        onLongClick = { haptics.longPress(); onMenu(code) },
                    )
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                QrImage(matrix, Modifier.fillMaxWidth(), seed = code.id.toInt())
                Text(
                    code.title,
                    color = Oled.TextPrimary,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Друга сторінка — створення: зверху код того, що вводиш (живцем), під ним — збережені, внизу — капсула вводу.
 * Свайп уліво по збереженому — видалити, довге натискання — меню, дотик — на весь екран.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreatePage(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    mode: InputMode,
    onModeClick: () -> Unit,
    onSave: () -> Unit,
    saved: List<QrCode>?,
    onOpen: (QrCode) -> Unit,
    onMenu: (QrCode) -> Unit,
    onDelete: (QrCode) -> Unit,
    focusRequester: FocusRequester,
) {
    val haptics = LocalHaptics.current
    val matrix = remember(input.text) { QrMatrix.encode(input.text) }
    val tooLong = input.text.isNotEmpty() && matrix == null
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth * 0.62f, 260.dp)
            if (matrix != null) {
                QrImage(matrix, Modifier.size(side), seed = 3)
            } else {
                Box(Modifier.size(side).pencilFrame(14.dp, seed = 3, alpha = 0.35f).padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (tooLong) R.string.too_long else R.string.preview_hint),
                        color = if (tooLong) Oled.Danger else Oled.TextSecondary,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (saved != null && saved.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.saved_empty),
                        color = Oled.TextSecondary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
            items(saved.orEmpty(), key = { it.id }) { code ->
                val state = rememberSwipeToDismissBoxState(
                    confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.EndToStart) {
                            onDelete(code)
                            true
                        } else {
                            false
                        }
                    },
                )
                SwipeToDismissBox(
                    state = state,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Row(Modifier.fillMaxSize().padding(end = 20.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.delete), color = Oled.Danger, fontSize = 17.sp)
                            Spacer(Modifier.width(8.dp))
                            GlyphIcon(Glyph.Delete, null, tint = Oled.Danger)
                        }
                    },
                ) {
                    SavedRow(
                        code,
                        Modifier.combinedClickable(
                            onClick = { haptics.tap(); onOpen(code) },
                            onLongClick = { haptics.longPress(); onMenu(code) },
                        ),
                    )
                }
            }
        }
        InputCapsule(
            value = input,
            onValueChange = onInputChange,
            mode = mode,
            onModeClick = onModeClick,
            onClear = { onInputChange(TextFieldValue("")) },
            onSave = onSave,
            canSave = matrix != null,
            focusRequester = focusRequester,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Рядок збереженого коду: маленький код, назва (зміст ховається, якщо є своя назва), дата, зірка вибраного. */
@Composable
private fun SavedRow(code: QrCode, modifier: Modifier) {
    val matrix = remember(code.content) { QrMatrix.encode(code.content) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Oled.Black)
            .then(modifier)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QrImage(matrix, Modifier.size(48.dp), seed = code.id.toInt(), radius = 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(code.title, color = Oled.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(code.createdAt)),
                color = Oled.TextSecondary,
                fontSize = 13.sp,
            )
        }
        if (code.isFavorite) {
            GlyphIcon(Glyph.Star, stringResource(R.string.in_favorites), tint = Oled.TextSecondary, size = 20.dp)
        }
    }
}
