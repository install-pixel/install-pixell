package com.blacklaunch.launcher.apps

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Іконки вантажаться лише для показаних результатів і кешуються в пам'яті. */
class AppIconCache(
    private val repository: AppRepository,
    private val density: Int,
    private val sizePx: Int,
) {
    private val cache = LruCache<String, ImageBitmap>(MAX_ICONS)

    suspend fun load(app: AppInfo): ImageBitmap? {
        cache.get(app.key)?.let { return it }
        return withContext(Dispatchers.IO) {
            repository.icon(app, density)
                ?.toBitmap(sizePx, sizePx)
                ?.asImageBitmap()
                ?.also { cache.put(app.key, it) }
        }
    }

    private companion object {
        const val MAX_ICONS = 100
    }
}
