package com.offlinekeyboard.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import com.offlinekeyboard.R

/**
 * The sketch style's papers, from the owner's photographs of real paper: the sheet the keyboard is written on
 * (crumpled black paper, or stained parchment) and the paper of the scraps laid on top (cards in the settings,
 * the touchpad, popups: a lighter crumpled black, or an old page). Loaded once in the background.
 */
object PaperSheets {

    private class Papers(val keyboard: Bitmap, val cards: BitmapShader)

    @Volatile private var light: Papers? = null
    @Volatile private var dark: Papers? = null
    private val making = HashSet<Boolean>()
    private val waiting = HashMap<Boolean, ArrayList<() -> Unit>>()

    /** The sheet the keyboard is written on, or null until it is loaded. */
    fun keyboard(lightTone: Boolean): Bitmap? = (if (lightTone) light else dark)?.keyboard

    /** The paper of the scraps on top, repeating, or null until it is loaded. */
    fun shader(lightTone: Boolean): BitmapShader? = (if (lightTone) light else dark)?.cards

    /** Loads the papers of [lightTone] in the background (once); [ready] runs on that thread when they are there. */
    fun prepare(context: Context, lightTone: Boolean, ready: () -> Unit) {
        val resources = context.applicationContext.resources
        synchronized(this) {
            if ((if (lightTone) light else dark) != null) return
            waiting.getOrPut(lightTone) { ArrayList() } += ready
            if (!making.add(lightTone)) return
        }
        Thread {
            val made = runCatching {
                val options = BitmapFactory.Options().apply { inScaled = false }
                val keyboard = BitmapFactory.decodeResource(resources, if (lightTone) R.drawable.sheet_keys_light else R.drawable.sheet_keys_dark, options)
                val cards = BitmapFactory.decodeResource(resources, if (lightTone) R.drawable.sheet_cards_light else R.drawable.sheet_cards_dark, options)
                Papers(keyboard, BitmapShader(cards, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR))
            }.getOrNull()
            val callbacks = synchronized(this) {
                if (lightTone) light = made else dark = made
                making.remove(lightTone)
                waiting.remove(lightTone).orEmpty()
            }
            callbacks.forEach { it() }
        }.apply { name = "papers"; isDaemon = true }.start()
    }
}
