package com.offlinekeyboard.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinekeyboard.R
import com.offlinekeyboard.keyboard.Icon
import com.offlinekeyboard.keyboard.Icons
import com.offlinekeyboard.keyboard.SurfaceStyle

/**
 * «Як користуватися» as in QRAFT: the first item of the settings menu opens a list of sections (an icon and
 * a name each), a section opens short points «• action — what happens» on a page of its own; «Назад» goes
 * one step back.
 */
internal enum class HelpTopic(val title: Int, val body: Int, val icon: Icon) {
    START(R.string.help_start, R.string.help_start_body, Icon.PHONE),
    TYPING(R.string.help_typing, R.string.help_typing_body, Icon.BACKSPACE),
    CORRECTION(R.string.help_correction, R.string.help_correction_body, Icon.UNDO),
    PUNCTUATION(R.string.help_punctuation, R.string.help_punctuation_body, Icon.SHIFT),
    NUMBERS(R.string.help_numbers, R.string.help_numbers_body, Icon.MODES),
    COMPUTER(R.string.help_computer, R.string.help_computer_body, Icon.KEYBOARD),
    VOICE(R.string.help_voice, R.string.help_voice_body, Icon.MIC),
    BLUETOOTH(R.string.help_bluetooth, R.string.help_bluetooth_body, Icon.BLUETOOTH),
    EMOJI(R.string.help_emoji, R.string.help_emoji_body, Icon.EMOJI),
    DICTIONARY(R.string.help_dictionary, R.string.help_dictionary_body, Icon.CLIPBOARD),
}

/** The section the help page opens (one at a time, so a plain state is enough). */
internal var shownHelpTopic by mutableStateOf(HelpTopic.START)

@Composable
internal fun HelpPage(onOpen: (Page) -> Unit, onBack: () -> Unit) {
    PageScaffold(stringResource(R.string.page_help), onBack) {
        Section(null) {
            HelpTopic.entries.forEach { topic ->
                HelpRow(topic.icon, stringResource(topic.title)) {
                    shownHelpTopic = topic
                    onOpen(Page.HELP_TOPIC)
                }
            }
        }
    }
}

/** One section of the help, as QRAFT's menu rows: a small line icon, the name, an arrow. */
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
        val color = Tone.TextSecondary.toArgb()
        Canvas(Modifier.size(20.dp)) {
            drawIntoCanvas {
                Icons.draw(it.nativeCanvas, icon, size.width / 2, size.height / 2, size.minDimension, color, if (sheet) SurfaceStyle.SHEET else SurfaceStyle.GLASS)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = Tone.TextPrimary, fontSize = if (sheet) 18.sp else 16.sp, modifier = Modifier.weight(1f))
        Text(if (sheet) "→" else "›", color = Tone.TextSecondary, fontSize = if (sheet) 20.sp else 22.sp)
    }
}

@Composable
internal fun HelpTopicPage(onBack: () -> Unit) {
    val topic = shownHelpTopic
    PageScaffold(stringResource(topic.title), onBack) {
        Section(null) {
            Text(stringResource(topic.body), color = Tone.TextPrimary.copy(alpha = 0.9f), fontSize = 16.sp, lineHeight = 23.sp)
        }
    }
}
