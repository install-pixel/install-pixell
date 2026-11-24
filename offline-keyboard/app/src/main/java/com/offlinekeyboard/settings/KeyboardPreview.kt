package com.offlinekeyboard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.offlinekeyboard.core.LabelContext
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.LayoutProvider
import com.offlinekeyboard.keyboard.KeySounds
import com.offlinekeyboard.keyboard.KeyboardTheme
import com.offlinekeyboard.keyboard.KeyboardView
import com.offlinekeyboard.keyboard.SurfaceStyle

/**
 * The real keyboard with the settings as they are now, at the top of «Look and sound»: it changes while a
 * slider moves (style, brightness, height, see-through, sound). A tap plays the key's sound and vibration and
 * types nothing. The sketch writes it straight on the page's paper (as its keyboard is written on paper); glass
 * and the typewriter show their keyboard as a small card.
 */
@Composable
internal fun KeyboardPreview(s: KeyboardSettings) {
    val context = LocalContext.current
    val sounds = remember { runCatching { KeySounds(context) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { sounds?.release() } }
    val theme = remember(s.themeMode, s.keyBrightness) { KeyboardTheme.resolve(s.themeMode, s.keyBrightness) }
    val sheet = theme.style == SurfaceStyle.SHEET
    val language = s.enabledLanguages.firstOrNull { it == s.lastLanguage } ?: s.enabledLanguages.first()
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            // A thin frame, so a black keyboard on a black page still reads as a keyboard.
            .then(
                if (sheet) Modifier
                else Modifier.shadow(6.dp, shape).clip(shape)
                    .background(androidx.compose.ui.graphics.Color(theme.background))
                    .border(1.dp, Tone.TextSecondary.copy(alpha = 0.35f), shape),
            ),
    ) {
        AndroidView(
            factory = { KeyboardView(it).apply { listener = Silent } },
            // The typewriter's round keys rise above their row (the top one was cut by the card's edge).
            modifier = Modifier.fillMaxWidth().padding(top = if (theme.style == SurfaceStyle.TYPEWRITER) 10.dp else 0.dp),
            update = { view ->
                view.theme = theme
                view.keyPreview = s.keyPreview
                view.hapticFeedback = s.hapticFeedback
                view.vibration = s.vibration
                sounds?.voice = KeySounds.voiceOf(s.themeMode)
                view.keySounds = sounds
                view.soundVolume = if (s.keySound) s.keySoundVolume else 0f
                view.spaceSwipeEnabled = false
                view.spaceCursorEnabled = false
                view.backspaceSwipeEnabled = false
                val layout = LayoutProvider.layout(KeyboardMode.MOBILE, language, Layer.LETTERS, s.mobileCursorRow)
                view.bind(
                    layout = layout,
                    labels = LabelContext(shift = false, capsLock = false, language = language, mode = KeyboardMode.MOBILE),
                    // A little smaller than the keyboard itself, so the sliders stay in sight; the height setting
                    // still shows as it grows and shrinks.
                    keyHeightDp = PREVIEW_KEY_DP * s.keyHeightScale,
                    highlight = { KeyboardView.KeyHighlight.NONE },
                )
            },
        )
    }
}

/** Keys pressed in the preview do nothing (their sound and vibration come from the view itself). */
private object Silent : KeyboardView.Listener {
    override fun onKey(key: Key) {}
    override fun onAlternative(text: String) {}
    override fun onSpaceSwipe(step: Int) {}
    override fun onBackspaceSwipe(words: Int, done: Boolean) {}
    override fun onLanguageKeyLongPress() {}
}

private const val PREVIEW_KEY_DP = 40f
