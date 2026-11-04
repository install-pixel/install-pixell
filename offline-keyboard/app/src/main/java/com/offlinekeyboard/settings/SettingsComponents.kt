package com.offlinekeyboard.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinekeyboard.R
import com.offlinekeyboard.core.layout.Language

@Composable
internal fun languageName(language: Language): String = stringResource(
    when (language) {
        Language.UK -> R.string.language_uk
        Language.PL -> R.string.language_pl
        Language.EN -> R.string.language_en
    },
)

/**
 * A settings card, as in the launcher: black glass rounded 20 dp with the top light and a bright top rim, or
 * (sheet) only a pencil frame on the black paper.
 */
@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .lookSurface(20.dp, seed = title.hashCode(), sheen = true, alpha = 0.6f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = Tone.TextPrimary, fontSize = 19.sp)
        content()
    }
}

/**
 * The one button of the settings screens, as wide as the card, so a long label wraps inside its own button.
 * Glass: a row of black glass rounded 14 dp; sheet: the label underlined with a pencil. The text colour says
 * what it does ([ButtonTone]); a disabled button fades instead of disappearing.
 */
@Composable
internal fun ActionButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.NEUTRAL,
    content: @Composable RowScope.() -> Unit,
) {
    val color = toneColor(tone)
    val sheet = LocalLook.current == Look.SHEET
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (sheet) Modifier else Modifier.lookSurface(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (sheet) 6.dp else 16.dp, vertical = if (sheet) 8.dp else 12.dp)
            .then(
                if (sheet) {
                    Modifier.drawWithContent {
                        drawContent()
                        pencilStroke(size.height - 2.dp.toPx(), 0f, size.width * 0.6f, seed = tone.ordinal + 3, alpha = 0.45f, ink = if (tone == ButtonTone.NEUTRAL) Tone.Ink else color)
                    }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)) { content() }
        }
    }
}

/**
 * A small button in a row that also holds text (a setup step, a device, a word field): lit glass rounded
 * 12 dp, or a pencil frame.
 */
@Composable
internal fun InlineButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.NEUTRAL,
    content: @Composable RowScope.() -> Unit,
) {
    val color = toneColor(tone)
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .lookSurface(12.dp, seed = tone.ordinal * 31 + 7, ink = if (tone == ButtonTone.NEUTRAL) Tone.Ink else color, lit = true)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, textAlign = TextAlign.Center)) { content() }
        }
    }
}

/** One setup step: a button while it is to do, a check mark once done. */
@Composable
internal fun SetupStep(title: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), color = Tone.TextPrimary, fontSize = 17.sp)
        if (done) {
            Text("✓ " + stringResource(R.string.setup_done), color = Tone.TextSecondary, fontSize = 15.sp)
        } else {
            InlineButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
internal fun StatusLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "!", color = if (ok) Tone.TextPrimary else Tone.Danger, fontSize = 17.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Tone.TextPrimary, fontSize = 17.sp)
    }
}

/** A row that opens another settings page: name, short hint and an arrow («›», or «→» on the sheet). */
@Composable
internal fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    val sheet = LocalLook.current == Look.SHEET
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = if (sheet) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // A name never pushes the arrow away; the hint stays on one line (… if a language makes it long).
            Text(title, color = Tone.TextPrimary, fontSize = if (sheet) 19.sp else 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Tone.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(if (sheet) "→" else "›", color = Tone.TextSecondary, fontSize = if (sheet) 20.sp else 22.sp)
    }
}

/**
 * An on/off setting. Glass: the label and the glass switch. Sheet (as the launcher): no switch — a setting
 * that is on is simply written, one that is off is crossed out with a pencil; a tap does the opposite.
 */
@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    if (LocalLook.current == Look.SHEET) {
        CrossedRow(label, checked, Role.Switch, onChange)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = Tone.TextPrimary, fontSize = 17.sp)
        Spacer(Modifier.width(12.dp))
        LookSwitch(checked)
    }
}

/** A multiple-choice item (languages, modifier keys): a glass check box, or written / crossed out on the sheet. */
@Composable
internal fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    if (LocalLook.current == Look.SHEET) {
        CrossedRow(label, checked, Role.Checkbox, onChange)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LookMark(checked)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Tone.TextPrimary, fontSize = 17.sp)
    }
}

/** The sheet's switch: the label, dimmed and crossed out with a wavy pencil line while it is off. */
@Composable
private fun CrossedRow(label: String, checked: Boolean, role: Role, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .toggleable(value = checked, role = role, onValueChange = onChange)
            .padding(vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (checked) Tone.TextPrimary else Tone.TextSecondary,
            fontSize = 18.sp,
            modifier = if (checked) {
                Modifier
            } else {
                Modifier.drawWithContent {
                    drawContent()
                    // One line through the first line of text, as far as the text goes.
                    val line = minOf(size.height, 30.sp.toPx())
                    pencilStroke(line * 0.55f, -2f, size.width + 2f, seed = label.length)
                }
            },
        )
    }
}

/**
 * One option of a choice, as in the launcher. Glass: options are equal rounded rows one under another; the
 * chosen one is lit glass with a brighter rim and a filled radio dot. Sheet: the chosen one is circled with a
 * pencil, the others are just written, dimmer.
 */
@Composable
internal fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val sheet = LocalLook.current == Look.SHEET
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    sheet && selected -> Modifier.pencilFrame(14.dp, label.length, alpha = 0.9f)
                    sheet -> Modifier
                    else -> Modifier.lookSurface(14.dp, chosen = selected, lit = selected)
                },
            )
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = if (sheet) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!sheet) {
            RadioDot(selected)
            Spacer(Modifier.width(12.dp))
        }
        Text(label, color = if (selected) Tone.TextPrimary else Tone.TextSecondary, fontSize = if (sheet) 17.sp else 16.sp)
    }
}

/** The ring of a glass option, with a dot inside when chosen. */
@Composable
private fun RadioDot(selected: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val color = if (selected) Tone.TextPrimary else Tone.TextSecondary
        val r = size.minDimension / 2
        drawCircle(color, r - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        if (selected) drawCircle(color, 4.dp.toPx())
    }
}

internal fun formatMb(bytes: Long): String = "%.0f MB".format(bytes / 1_000_000.0)

/** A thin line between rows of a glass card; the sheet has none (its rows are just written on the paper). */
@Composable
internal fun RowDivider() {
    if (LocalLook.current == Look.SHEET) return
    androidx.compose.material3.HorizontalDivider(color = androidx.compose.ui.graphics.Color(0x1FFFFFFF))
}
