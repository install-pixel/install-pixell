package com.blacklaunch.qrcodes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [QrCode::class, Folder::class], version = 3, exportSchema = true)
abstract class QrDatabase : RoomDatabase() {
    abstract fun codes(): QrDao

    companion object {
        /** 2 → 3: папки й власний порядок у списку (спершу — як було: новіші вище). */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE codes ADD COLUMN folderId INTEGER")
                db.execSQL("ALTER TABLE codes ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE codes SET sortOrder = createdAt")
                db.execSQL("CREATE TABLE IF NOT EXISTS folders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
            }
        }

        /** 1 → 2: формат коду (QR / штрихкод); усе, що було, — QR. */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE codes ADD COLUMN format TEXT NOT NULL DEFAULT 'QR'")
            }
        }

        @Volatile private var instance: QrDatabase? = null

        fun get(context: Context): QrDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, QrDatabase::class.java, "qrcodes.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
