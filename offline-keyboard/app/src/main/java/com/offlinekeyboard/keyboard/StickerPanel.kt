package com.offlinekeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.offlinekeyboard.R

/**
 * Shown in place of the keys: favourites and the most used stickers first, then the user's own and the
 * keyboard's animated ones, and a "+" tile to add pictures. A long press marks a favourite (★).
 * Animations run only while the panel is visible.
 */
@SuppressLint("ViewConstructor")
class StickerPanel(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onStickerPicked(name: String)
        fun onStickersClosed(backToEmoji: Boolean)
        fun onStickerCharacterPicked(character: StickerCharacter)
        fun onStickerLongPressed(name: String)
        fun onAddStickers()
    }

    var listener: Listener? = null

    /** Boy or girl; changing it redraws the grid. */
    var character: StickerCharacter = StickerCharacter.BOY
        set(value) {
            if (field == value) return
            field = value
            updateCharacterButtons()
            reload()
        }
    private val cells = ArrayList<ImageView>()
    private var loaded = false
    private var loadedVersion = -1

    private val grid = GridLayout(context).apply {
        columnCount = COLUMNS
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    private val title = TextView(context).apply {
        text = context.getString(R.string.stickers_title)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val lettersButton = ToolbarButton(context).apply {
        text = context.getString(R.string.stickers_letters)
        contentDescription = text
        setOnClickListener { listener?.onStickersClosed(backToEmoji = false) }
    }
    private val emojiButton = ToolbarButton(context).apply {
        icon = Icon.EMOJI
        contentDescription = context.getString(R.string.stickers_back_to_emoji)
        setOnClickListener { listener?.onStickersClosed(backToEmoji = true) }
    }

    private val boyButton = ToolbarButton(context).apply {
        text = context.getString(R.string.sticker_boy)
        contentDescription = context.getString(R.string.sticker_boy_description)
        setOnClickListener { listener?.onStickerCharacterPicked(StickerCharacter.BOY) }
    }
    private val girlButton = ToolbarButton(context).apply {
        text = context.getString(R.string.sticker_girl)
        contentDescription = context.getString(R.string.sticker_girl_description)
        setOnClickListener { listener?.onStickerCharacterPicked(StickerCharacter.GIRL) }
    }
    private var theme: KeyboardTheme? = null

    init {
        orientation = VERTICAL
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(grid)
        }
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            addView(lettersButton, LayoutParams(dp(72), LayoutParams.MATCH_PARENT).apply { setMargins(dp(3), 0, dp(3), 0) })
            addView(emojiButton, LayoutParams(dp(56), LayoutParams.MATCH_PARENT).apply { setMargins(dp(3), 0, dp(3), 0) })
            addView(title, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            for (b in listOf(boyButton, girlButton)) {
                addView(b, LayoutParams(dp(52), LayoutParams.MATCH_PARENT).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
    }

    fun applyTheme(theme: KeyboardTheme) {
        this.theme = theme
        setBackgroundColor(theme.background)
        title.setTextColor(theme.hint)
        for (b in listOf(lettersButton, emojiButton)) {
            b.fill = theme.function
            b.pressedFill = theme.pressed
            b.foreground = theme.text
        }
        updateCharacterButtons()
    }

    private fun updateCharacterButtons() {
        val t = theme ?: return
        for ((b, c) in listOf(boyButton to StickerCharacter.BOY, girlButton to StickerCharacter.GIRL)) {
            val active = c == character
            b.fill = if (active) t.accent else t.function
            b.pressedFill = t.pressed
            b.foreground = if (active) t.onAccent else t.text
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE && isShown) {
            // Own stickers added in settings, or a new favourite, since the grid was built.
            if (loadedVersion != Stickers.version.value) clear()
            load()
            play(true)
        } else {
            play(false)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        play(false)
    }

    /** Rebuilds the grid (new order, favourites, own stickers). */
    fun reload() {
        clear()
        if (isShown) {
            load()
            play(true)
        }
    }

    private fun clear() {
        play(false)
        grid.removeAllViews()
        cells.clear()
        loaded = false
    }

    /** Builds the grid the first time the panel is opened (the keyboard opens faster without it). */
    private fun load() {
        if (loaded) return
        loaded = true
        loadedVersion = Stickers.version.value
        val size = (resources.displayMetrics.widthPixels - dp(8)) / COLUMNS
        val favorites = Stickers.favorites(context)
        for (name in Stickers.ordered(context, character)) {
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(6), dp(6), dp(6), dp(6))
                contentDescription = name.substringAfterLast('/').substringAfter('-')
                tag = name
                setImageDrawable(preview(name))
                setOnClickListener { listener?.onStickerPicked(name) }
                setOnLongClickListener { listener?.onStickerLongPressed(name); true }
                selectableBackground()
            }
            cells += image
            val cell = FrameLayout(context).apply {
                addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                if (name in favorites) {
                    addView(
                        TextView(context).apply {
                            text = "★"
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            setTextColor(theme?.accent ?: 0xFFFFC107.toInt())
                        },
                        FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                            .apply { setMargins(0, dp(2), dp(4), 0) },
                    )
                }
            }
            grid.addView(cell, GridLayout.LayoutParams().apply { width = size; height = size })
        }
        val add = TextView(context).apply {
            text = "+"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            setTextColor(theme?.hint ?: 0xFF888888.toInt())
            contentDescription = context.getString(R.string.stickers_add)
            setOnClickListener { listener?.onAddStickers() }
            selectableBackground()
        }
        grid.addView(add, GridLayout.LayoutParams().apply { width = size; height = size })
    }

    private fun preview(name: String): Drawable? = runCatching {
        if (Stickers.isCustom(name)) {
            val file = Stickers.customFile(context, name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    // Big photos are shrunk for the grid.
                    val max = maxOf(info.size.width, info.size.height)
                    if (max > PREVIEW_PX) decoder.setTargetSampleSize(max / PREVIEW_PX)
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val max = maxOf(bounds.outWidth, bounds.outHeight)
                val options = BitmapFactory.Options().apply { inSampleSize = (max / PREVIEW_PX).coerceAtLeast(1) }
                BitmapDrawable(resources, BitmapFactory.decodeFile(file.path, options))
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, Stickers.previewPath(name)))
        } else {
            // Older Android reads only the first frame of a GIF.
            context.assets.open(Stickers.stillPath(name)).use { BitmapDrawable(resources, BitmapFactory.decodeStream(it)) }
        }
    }.getOrNull()

    private fun play(run: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        for (cell in cells) {
            val drawable = cell.drawable as? AnimatedImageDrawable ?: continue
            if (run) drawable.start() else drawable.stop()
        }
    }

    private fun View.selectableBackground() {
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        if (outValue.resourceId != 0) setBackgroundResource(outValue.resourceId)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val COLUMNS = 4
        const val PREVIEW_PX = 256
    }
}
