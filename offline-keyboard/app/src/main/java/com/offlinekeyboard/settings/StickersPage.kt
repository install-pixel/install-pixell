package com.offlinekeyboard.settings

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinekeyboard.R
import com.offlinekeyboard.core.layout.EmojiSet
import com.offlinekeyboard.keyboard.StickerCharacter
import com.offlinekeyboard.keyboard.Stickers

/** The user's own emoji and stickers, and who Lingo is in the built-in stickers. */
@Composable
internal fun StickersPage(s: KeyboardSettings, update: UpdateSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    var ownEmoji by rememberSaveable { mutableStateOf(s.ownEmoji) }
    var message by remember { mutableStateOf<String?>(null) }
    val version by Stickers.version.collectAsStateWithLifecycle()
    val custom = remember(version) { Stickers.custom(context) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val added = uris.count { Stickers.import(context, it) }
        message = context.getString(R.string.stickers_added, added, uris.size)
    }

    PageScaffold(stringResource(R.string.page_stickers), onBack) {
        Section(stringResource(R.string.own_emoji_title)) {
            Text(stringResource(R.string.own_emoji_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = ownEmoji,
                onValueChange = { ownEmoji = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                label = { Text(stringResource(R.string.own_emoji_field)) },
            )
            Button(onClick = {
                update { it.copy(ownEmoji = ownEmoji.trim()) }
                message = context.getString(R.string.own_emoji_saved, EmojiSet.parseOwn(ownEmoji).size)
            }) { Text(stringResource(R.string.save)) }
            Text(stringResource(R.string.emoji_frequent_hint), style = MaterialTheme.typography.bodySmall)
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Section(stringResource(R.string.own_stickers_title)) {
            Text(stringResource(R.string.own_stickers_hint), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { picker.launch(arrayOf("image/gif", "image/webp", "image/png", "image/jpeg")) }) {
                Text(stringResource(R.string.stickers_add))
            }
            if (custom.isEmpty()) {
                Text(stringResource(R.string.own_stickers_none), style = MaterialTheme.typography.bodySmall)
            }
            for (row in custom.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    for (name in row) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val bitmap = remember(name) { thumbnail(Stickers.customFile(context, name).path) }
                            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, Modifier.size(88.dp))
                            }
                            TextButton(onClick = { Stickers.delete(context, name) }) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Section(stringResource(R.string.sticker_character)) {
            RadioRow(stringResource(R.string.sticker_boy_description), s.stickerCharacter == StickerCharacter.BOY) {
                update { it.copy(stickerCharacter = StickerCharacter.BOY) }
            }
            RadioRow(stringResource(R.string.sticker_girl_description), s.stickerCharacter == StickerCharacter.GIRL) {
                update { it.copy(stickerCharacter = StickerCharacter.GIRL) }
            }
        }
    }
}

/** First frame, shrunk to about 256 px, so big photos do not fill the memory. */
private fun thumbnail(path: String) = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val max = maxOf(bounds.outWidth, bounds.outHeight)
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = (max / 256).coerceAtLeast(1) })
}.getOrNull()
