package com.blacklaunch.diary.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.R
import com.blacklaunch.diary.ui.ChoiceChip
import com.blacklaunch.diary.ui.Glyph
import com.blacklaunch.diary.ui.GlyphIcon
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetTextField
import com.blacklaunch.diary.ui.WindowHint
import com.blacklaunch.diary.ui.fadingEdges
import com.blacklaunch.diary.ui.pencilFrame
import com.blacklaunch.diary.ui.theme.Oled
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Пошук на весь екран: поле вгорі, під ним — «Є фото» / «Є голос», нижче — знайдені дні (дата й уривок із виділеним
 * словом). Дотик — щоденник перегортається на цей день і підсвічує знайдене.
 */
@Composable
fun SearchScreen(search: (String, Boolean, Boolean) -> List<SearchHit>, onClose: () -> Unit, onOpen: (SearchHit) -> Unit) {
    val haptics = LocalHaptics.current
    var query by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val hits = remember(query, photo, audio) { search(query, photo, audio) }
    val locale = Locale.getDefault()
    val list = rememberLazyListState()
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(Oled.Black)
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { haptics.tap(); onClose() }, contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Back, stringResource(R.string.back), tint = Oled.TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            SheetTextField(
                query,
                { query = it.take(80) },
                stringResource(R.string.search_hint),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                focusRequester = focus,
            )
        }
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(stringResource(R.string.filter_photo), photo) { photo = !photo }
            ChoiceChip(stringResource(R.string.filter_audio), audio) { audio = !audio }
        }
        if (query.isBlank() && !photo && !audio) {
            WindowHint(stringResource(R.string.search_explain))
        } else if (hits.isEmpty()) {
            WindowHint(stringResource(R.string.search_nothing))
        }
        LazyColumn(Modifier.weight(1f).fadingEdges(list), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hits, key = { it.day * 31 + it.start }) { h ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .pencilFrame(16.dp, seed = (h.day % 50).toInt(), alpha = 0.5f)
                        .clickable { haptics.tap(); onOpen(h) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", locale).format(LocalDate.ofEpochDay(h.day)),
                        color = Oled.TextSecondary,
                        fontSize = 13.sp,
                    )
                    if (h.snippet.isNotBlank()) {
                        Text(
                            buildAnnotatedString {
                                val a = h.snippetFrom.coerceIn(0, h.snippet.length)
                                val b = h.snippetTo.coerceIn(a, h.snippet.length)
                                append(h.snippet.substring(0, a))
                                withStyle(SpanStyle(color = Oled.TextPrimary, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) { append(h.snippet.substring(a, b)) }
                                append(h.snippet.substring(b))
                            },
                            color = Oled.TextPrimary.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Пошук — сторінка щоденника (закладка-пошук перегортає на неї, назад — гортанням або «назад», як і всі сторінки): поле,
 * «Є фото» / «Є голос», знайдені дні. Дотик — щоденник перегортається на цей день і підсвічує знайдене.
 * [active] — сторінка відкрита (тоді поле одразу готове до письма).
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.SearchPageContent(
    search: (String, Boolean, Boolean) -> List<SearchHit>,
    active: Boolean,
    onOpen: (SearchHit) -> Unit,
) {
    val haptics = LocalHaptics.current
    var query by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(active) { if (active) runCatching { focus.requestFocus() } }
    val hits = remember(query, photo, audio) { search(query, photo, audio) }
    val locale = Locale.getDefault()
    SheetTextField(
        query,
        { query = it.take(80) },
        stringResource(R.string.search_hint),
        modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        focusRequester = focus,
    )
    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip(stringResource(R.string.filter_photo), photo) { photo = !photo }
        ChoiceChip(stringResource(R.string.filter_audio), audio) { audio = !audio }
    }
    if (query.isBlank() && !photo && !audio) {
        WindowHint(stringResource(R.string.search_explain))
    } else if (hits.isEmpty()) {
        WindowHint(stringResource(R.string.search_nothing))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 4.dp)) {
        hits.take(60).forEach { h ->
            androidx.compose.runtime.key(h.day * 31 + h.start) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .pencilFrame(16.dp, seed = (h.day % 50).toInt(), alpha = 0.5f)
                        .clickable { haptics.tap(); onOpen(h) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", locale).format(LocalDate.ofEpochDay(h.day)), color = Oled.TextSecondary, fontSize = 13.sp)
                    if (h.snippet.isNotBlank()) {
                        Text(
                            buildAnnotatedString {
                                val a = h.snippetFrom.coerceIn(0, h.snippet.length)
                                val b = h.snippetTo.coerceIn(a, h.snippet.length)
                                append(h.snippet.substring(0, a))
                                withStyle(SpanStyle(color = Oled.TextPrimary, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) { append(h.snippet.substring(a, b)) }
                                append(h.snippet.substring(b))
                            },
                            color = Oled.TextPrimary.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
