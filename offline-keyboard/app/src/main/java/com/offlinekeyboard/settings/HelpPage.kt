package com.offlinekeyboard.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinekeyboard.R
import com.offlinekeyboard.keyboard.Icon
import com.offlinekeyboard.keyboard.Icons
import com.offlinekeyboard.keyboard.SurfaceStyle

/**
 * «Як користуватися»: a search on top, then the sections in the order of the settings menu (an icon and a name
 * each). A section opens its points: what each setting or button does, on and off. Read-only: nothing here
 * changes a setting. The search finds a word in any point and shows those points right there.
 */

/** The section the help page opens (one at a time, so a plain state is enough). */
internal var shownHelpSection by mutableStateOf(HelpSection.START)

@Composable
internal fun HelpPage(onOpen: (Page) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    PageScaffold(stringResource(R.string.page_help), onBack) {
        Section(null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.help_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) {
            Section(null) {
                HelpSection.entries.forEach { section ->
                    HelpRow(section.icon, stringResource(section.title)) {
                        shownHelpSection = section
                        onOpen(Page.HELP_TOPIC)
                    }
                }
            }
        } else {
            // Every word of the search in the point's title or text (any order), in the sections' order.
            val found = HelpSection.entries.flatMap { section ->
                section.items.filter { item ->
                    val text = (context.getString(item.title) + " " + context.getString(item.body) + " " +
                        context.getString(section.title)).lowercase()
                    words.all { it in text }
                }.map { section to it }
            }
            if (found.isEmpty()) {
                Section(null) { Text(stringResource(R.string.help_nothing_found), color = Tone.TextSecondary) }
            }
            for ((section, item) in found) {
                Section(stringResource(section.title)) { HelpPoint(item) }
            }
        }
    }
}

/** One section of the help: a small line icon, the name, an arrow. */
@Composable
private fun HelpRow(icon: Icon, title: String, onClick: () -> Unit) {
    val sheet = LocalLook.current == Look.SHEET
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HelpIcon(icon, 20)
        Spacer(Modifier.width(14.dp))
        // The sketch: a topic can be tapped, so it is written in full ink and bolder (as the other menus).
        Text(
            title,
            color = Tone.TextPrimary,
            fontSize = if (sheet) 18.sp else 16.sp,
            fontWeight = if (sheet) FontWeight.SemiBold else null,
            modifier = Modifier.weight(1f),
        )
        Text(if (sheet) "→" else "›", color = if (sheet) Tone.TextPrimary else Tone.TextSecondary, fontSize = if (sheet) 20.sp else 22.sp)
    }
}

/** The keyboard's own icon in the line style of the page's look (only the icon, never a whole button). */
@Composable
private fun HelpIcon(icon: Icon, sizeDp: Int) {
    val look = LocalLook.current
    val color = Tone.TextPrimary.toArgb()
    Canvas(Modifier.size(sizeDp.dp)) {
        drawIntoCanvas {
            Icons.draw(it.nativeCanvas, icon, size.width / 2, size.height / 2, size.minDimension, color, when (look) {
                Look.SHEET -> SurfaceStyle.SHEET
                Look.TYPEWRITER -> SurfaceStyle.TYPEWRITER
                Look.GLASS -> SurfaceStyle.GLASS
            })
        }
    }
}

/** A point: its icon (a button of the keyboard), the title in bold, then what it does. */
@Composable
internal fun HelpPoint(item: HelpItem) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.icon?.let {
                HelpIcon(it, 22)
                Spacer(Modifier.width(10.dp))
            }
            Text(stringResource(item.title), color = Tone.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(stringResource(item.body), color = Tone.TextPlain, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
internal fun HelpTopicPage(onBack: () -> Unit) {
    val section = shownHelpSection
    PageScaffold(stringResource(section.title), onBack) {
        // Each point a block of its own, so the pages turn between points and never cut one in two.
        for (item in section.items) {
            Section(null) { HelpPoint(item) }
        }
    }
}
