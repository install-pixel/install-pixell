package com.offlinekeyboard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.offlinekeyboard.R
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.keyboard.Accent

@Composable
internal fun languageName(language: Language): String = stringResource(
    when (language) {
        Language.UK -> R.string.language_uk
        Language.PL -> R.string.language_pl
        Language.EN -> R.string.language_en
    },
)

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
    if (LocalLook.current == Look.PLAIN) {
        Card(modifier = Modifier.fillMaxWidth()) { body() }
    } else {
        // Glass card (rounded 20 dp, with the top light) or a pencil frame.
        Box(Modifier.fillMaxWidth().lookSurface(20.dp, seed = title.hashCode(), sheen = true)) { body() }
    }
}

/** Light/dark Material colours tinted with the keyboard's pastel accent; text on the accent stays dark. */
internal fun settingsColors(dark: Boolean, accent: Accent): ColorScheme {
    val a = Color(accent.color)
    val onAccent = Color(0xFF1E2128)
    return if (dark) {
        darkColorScheme(
            primary = a, onPrimary = onAccent,
            primaryContainer = a.copy(alpha = 0.35f).compositeOver(Color(0xFF1C1D21)), onPrimaryContainer = Color(0xFFEDEEF0),
            secondaryContainer = a.copy(alpha = 0.25f).compositeOver(Color(0xFF1C1D21)),
        )
    } else {
        lightColorScheme(
            primary = a.copy(alpha = 0.85f).compositeOver(Color(0xFF3A3F4A)), onPrimary = Color.White,
            primaryContainer = a, onPrimaryContainer = onAccent,
            secondaryContainer = a.copy(alpha = 0.45f).compositeOver(Color.White),
            surfaceVariant = a.copy(alpha = 0.14f).compositeOver(Color(0xFFF4F5F7)),
        )
    }
}

/**
 * The one button of the settings screens: outlined and as wide as the screen, so a long label wraps
 * inside its own button instead of squeezing a neighbour. Several actions stack one under another.
 */
@Composable
internal fun ActionButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.NEUTRAL,
    content: @Composable RowScope.() -> Unit,
) {
    LookButton(onClick, enabled, tone, Modifier.fillMaxWidth(), content)
}

/** The same button for a row that also holds text (a setup step, a device, a word field). */
@Composable
internal fun InlineButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.NEUTRAL,
    content: @Composable RowScope.() -> Unit,
) {
    LookButton(onClick, enabled, tone, Modifier, content)
}

/**
 * Outlined Material button in the plain look; a glass "pill" or a pencil-framed one in glass and sheet. The
 * text colour says what it does ([ButtonTone]); a disabled button fades instead of disappearing.
 */
@Composable
private fun LookButton(onClick: () -> Unit, enabled: Boolean, tone: ButtonTone, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val color = toneColor(tone)
    if (LocalLook.current == Look.PLAIN) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = if (tone == ButtonTone.NEUTRAL) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.outlinedButtonColors(contentColor = color),
            content = content,
        )
        return
    }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .lookSurface(24.dp, seed = tone.ordinal * 31 + 7, ink = if (tone == ButtonTone.NEUTRAL) Tone.Ink else color)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center)) { content() }
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
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (done) {
            Text("✓ " + stringResource(R.string.setup_done), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        } else {
            InlineButton(onClick = onClick) { Text(action) }
        }
    }
}

/** Six round colour swatches, each a 40 dp touch target (they fit a 320 dp wide screen). */
@Composable
internal fun AccentPicker(selected: Accent, onSelect: (Accent) -> Unit) {
    // Glass and sheet have no colour accent (THEMES.md): the swatches fade but keep their place.
    val styled = LocalLook.current != Look.PLAIN
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.alpha(if (styled) 0.35f else 1f)) {
        for (accent in Accent.entries) {
            val name = stringResource(
                when (accent) {
                    Accent.SKY -> R.string.accent_sky
                    Accent.MINT -> R.string.accent_mint
                    Accent.LAVENDER -> R.string.accent_lavender
                    Accent.PEACH -> R.string.accent_peach
                    Accent.ROSE -> R.string.accent_rose
                    Accent.GREY -> R.string.accent_grey
                },
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(accent.color))
                    .border(
                        width = if (accent == selected) 3.dp else 0.dp,
                        color = if (accent == selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .semantics { contentDescription = name }
                    .selectable(selected = accent == selected, enabled = !styled, role = Role.RadioButton) { onSelect(accent) },
                contentAlignment = Alignment.Center,
            ) {
                if (accent == selected) Text("✓", color = Color(0xFF1E2128), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
internal fun StatusLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) "✓" else "!",
            color = when {
                !ok -> MaterialTheme.colorScheme.error
                LocalLook.current == Look.PLAIN -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** A row that opens another settings page. */
@Composable
internal fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (LocalLook.current == Look.PLAIN) Switch(checked = checked, onCheckedChange = null) else LookSwitch(checked)
    }
}

@Composable
internal fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (LocalLook.current == Look.PLAIN) Checkbox(checked = checked, onCheckedChange = null) else LookMark(checked, round = false)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
internal fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (LocalLook.current == Look.PLAIN) RadioButton(selected = selected, onClick = null) else LookMark(selected, round = true)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

internal fun formatMb(bytes: Long): String = "%.0f MB".format(bytes / 1_000_000.0)
