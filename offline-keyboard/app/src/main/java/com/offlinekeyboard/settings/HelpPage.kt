package com.offlinekeyboard.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.offlinekeyboard.R

/**
 * «Довідка» as in QRAFT: the first item of the settings menu opens a list of topics, a topic opens its
 * text on a page of its own; «Назад» goes one step back.
 */
internal enum class HelpTopic(val title: Int, val summary: Int, val body: Int) {
    START(R.string.help_start, R.string.help_start_summary, R.string.help_start_body),
    TYPING(R.string.help_typing, R.string.help_typing_summary, R.string.help_typing_body),
    CORRECTION(R.string.help_correction, R.string.help_correction_summary, R.string.help_correction_body),
    PUNCTUATION(R.string.help_punctuation, R.string.help_punctuation_summary, R.string.help_punctuation_body),
    NUMBERS(R.string.help_numbers, R.string.help_numbers_summary, R.string.help_numbers_body),
    COMPUTER(R.string.help_computer, R.string.help_computer_summary, R.string.help_computer_body),
    VOICE(R.string.help_voice, R.string.help_voice_summary, R.string.help_voice_body),
    BLUETOOTH(R.string.help_bluetooth, R.string.help_bluetooth_summary, R.string.help_bluetooth_body),
    EMOJI(R.string.help_emoji, R.string.help_emoji_summary, R.string.help_emoji_body),
    DICTIONARY(R.string.help_dictionary, R.string.help_dictionary_summary, R.string.help_dictionary_body),
}

/** The topic the help page opens (one at a time, so a plain state is enough). */
internal var shownHelpTopic by mutableStateOf(HelpTopic.START)

@Composable
internal fun HelpPage(onOpen: (Page) -> Unit, onBack: () -> Unit) {
    PageScaffold(stringResource(R.string.page_help), onBack) {
        Section(stringResource(R.string.help_topics)) {
            HelpTopic.entries.forEachIndexed { i, topic ->
                if (i > 0) RowDivider()
                NavRow(stringResource(topic.title), stringResource(topic.summary)) {
                    shownHelpTopic = topic
                    onOpen(Page.HELP_TOPIC)
                }
            }
        }
    }
}

@Composable
internal fun HelpTopicPage(onBack: () -> Unit) {
    val topic = shownHelpTopic
    PageScaffold(stringResource(topic.title), onBack) {
        Text(stringResource(topic.body), color = Tone.TextPrimary.copy(alpha = 0.9f), fontSize = 16.sp, lineHeight = 23.sp)
    }
}
