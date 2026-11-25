package com.blacklaunch.supps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.CheckIn
import com.blacklaunch.supps.data.DayType
import com.blacklaunch.supps.ui.theme.Oled

/**
 * Оцінка дня — один екран без гортання: тип дня, 1–4 питання (1–5) і необов'язкова нотатка. Усе можна пропустити.
 */
@Composable
fun CheckInScreen(day: Long, existing: CheckIn?, questions: List<String>, onBack: () -> Unit, onSave: (CheckIn) -> Unit) {
    val locale = appLocale()
    var dayType by rememberSaveable { mutableStateOf(existing?.dayTypeE) }
    var a1 by rememberSaveable { mutableStateOf(existing?.a1) }
    var a2 by rememberSaveable { mutableStateOf(existing?.a2) }
    var a3 by rememberSaveable { mutableStateOf(existing?.a3) }
    var a4 by rememberSaveable { mutableStateOf(existing?.a4) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    val answers = listOf(a1, a2, a3, a4)
    fun setAnswer(i: Int, v: Int?) {
        when (i) {
            0 -> a1 = v
            1 -> a2 = v
            2 -> a3 = v
            else -> a4 = v
        }
    }
    FullPage(
        title = stringResource(R.string.checkin_title),
        onBack = onBack,
        actions = {
            SheetButton(
                stringResource(R.string.save),
                {
                    onSave(
                        CheckIn(
                            day = day,
                            a1 = a1,
                            a2 = if (questions.size > 1) a2 else null,
                            a3 = if (questions.size > 2) a3 else null,
                            a4 = if (questions.size > 3) a4 else null,
                            dayType = dayType?.name,
                            note = note.trim().take(200),
                        ),
                    )
                },
                tone = Tone.Confirm,
            )
        },
    ) {
        Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(longDate(day, locale).replaceFirstChar { it.titlecase(locale) }, color = Oled.TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            Label(stringResource(R.string.checkin_day_type))
            // Два ряди по дві: назви вміщаються повністю, без «…».
            val types = DayType.entries
            types.chunked(2).forEach { rowTypes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowTypes.forEach { t ->
                        ScoreChip(dayTypeName(t), dayType == t, Modifier.weight(1f)) { dayType = if (dayType == t) null else t }
                    }
                }
            }
            questions.forEachIndexed { i, q ->
                Label(q)
                ScoreRow(listOf("1", "2", "3", "4", "5"), answers[i]?.let { it - 1 }) { picked -> setAnswer(i, picked?.let { it + 1 }) }
            }
            Spacer(Modifier.height(4.dp))
            LabeledField(stringResource(R.string.checkin_note), note, { note = it }, placeholder = stringResource(R.string.checkin_note_hint), maxLength = 200)
            WindowHint(stringResource(R.string.checkin_hint))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Oled.TextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp))
}
