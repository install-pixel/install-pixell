package com.offlinekeyboard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
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

/** One setup step: a filled button while it is to do, a check mark once done. */
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
            Button(onClick = onClick) { Text(action) }
        }
    }
}

/** Six round colour swatches, each a 40 dp touch target (they fit a 320 dp wide screen). */
@Composable
internal fun AccentPicker(selected: Accent, onSelect: (Accent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    .selectable(selected = accent == selected, role = Role.RadioButton) { onSelect(accent) },
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
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
        Switch(checked = checked, onCheckedChange = null)
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
        Checkbox(checked = checked, onCheckedChange = null)
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
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

internal fun formatMb(bytes: Long): String = "%.0f MB".format(bytes / 1_000_000.0)
