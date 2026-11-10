package com.blacklaunch.diary.book

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.MenuEntry
import com.blacklaunch.diary.ui.MenuRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.blacklaunch.diary.ui.theme.Oled

/**
 * Сторінка, намальована як аркуш щоденника, — замість вікон меню й налаштувань: заголовок шрифтом теми, далі пункти.
 * У щоденнику на неї перегортають шестернею; на полиці вона «перегортається» поверх полиці.
 */
@Composable
fun PaperPage(title: String, subtitle: String?, seed: Int, modifier: Modifier = Modifier, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val glass = AppStyle.glass
    BoxWithConstraints(modifier.fillMaxSize().paperRelief(glass, seed)) {
        val u = constraints.maxWidth / PageMetrics.WIDTH
        val style = pageTextStyle(u.coerceAtLeast(0.01f), density, glass)
        val headerSize = (if (glass) 5f else 6.2f) * u / (density.density * density.fontScale)
        Canvas(Modifier.fillMaxSize()) { drawPaper(u, seed, emptyList()) }
        // Сторінка сталого розміру: вміст не «пливе» й не пружинить. Прокрутка — лише на крайній випадок, якщо не вмістилось
        // (дуже великий шрифт у системі).
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = with(density) { (PageMetrics.MARGIN_LEFT * u).toDp() }, end = 14.dp, top = 22.dp, bottom = 22.dp),
        ) {
            Text(title, style = style.copy(fontSize = headerSize.sp, lineHeight = (headerSize * 1.25f).sp, color = Oled.TextPrimary))
            if (subtitle != null) {
                Text(subtitle, style = style.copy(fontSize = (headerSize * 0.6f).sp, lineHeight = (headerSize * 0.8f).sp, color = Oled.TextSecondary))
            }
            Column(Modifier.padding(top = 10.dp), content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = style.copy(fontSize = (headerSize * 0.55f).sp, color = Oled.TextSecondary),
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 18.dp, bottom = 14.dp),
            )
        }
    }
}

/** Пункти меню на сторінці: сірі підзаголовки й рядки (як у вікні меню, але на аркуші). */
@Composable
fun ColumnScope.MenuEntries(entries: List<MenuEntry>) {
    entries.forEach { e ->
        if (e.glyph == null) {
            Text(e.text, color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp))
        } else {
            MenuRow(e.glyph, e.text, e.onClick, e.tone)
        }
    }
}
