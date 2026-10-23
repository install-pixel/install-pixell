package com.blacklaunch.launcher.system

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

enum class WallpaperTarget { HOME, HOME_AND_LOCK }

/**
 * Встановлює чорні системні шпалери, щоб у системних анімаціях (Home, останні програми)
 * не з'являлися старі. Попередні шпалери Android не дає зберегти без доступу до файлів,
 * тому зміна виконується лише після явного підтвердження користувача.
 */
class WallpaperController(context: Context) {
    private val wallpaperManager = WallpaperManager.getInstance(context.applicationContext)

    /** Змінюється щоразу, коли хтось встановлює нові шпалери головного екрана. */
    fun currentId(): Int = wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)

    /** Повертає ідентифікатор нових шпалер або null, якщо змінити не вдалося. */
    suspend fun setBlack(target: WallpaperTarget): Int? = withContext(Dispatchers.IO) {
        if (!wallpaperManager.isWallpaperSupported || !wallpaperManager.isSetWallpaperAllowed) {
            return@withContext null
        }
        val which = when (target) {
            WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.HOME_AND_LOCK -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        val black = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        try {
            wallpaperManager.setBitmap(black, null, true, which)
            currentId()
        } catch (e: IOException) {
            Log.w(TAG, "Cannot set wallpaper", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot set wallpaper", e)
            null
        } finally {
            black.recycle()
        }
    }

    private companion object {
        const val TAG = "WallpaperController"
        const val SIZE = 64
    }
}
