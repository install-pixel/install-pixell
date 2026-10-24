package com.blacklaunch.launcher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Локальна база BlackLaunch. Кожна зміна схеми — нова версія з міграцією в [MIGRATIONS];
 * схеми зберігаються в `app/schemas`.
 */
@Database(
    entities = [ProfileEntity::class, PinnedAppEntity::class, NotificationRuleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        private val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "blacklaunch.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
