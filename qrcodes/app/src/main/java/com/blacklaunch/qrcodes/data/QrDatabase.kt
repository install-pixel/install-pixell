package com.blacklaunch.qrcodes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [QrCode::class], version = 2, exportSchema = true)
abstract class QrDatabase : RoomDatabase() {
    abstract fun codes(): QrDao

    companion object {
        /** 1 → 2: формат коду (QR / штрихкод); усе, що було, — QR. */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE codes ADD COLUMN format TEXT NOT NULL DEFAULT 'QR'")
            }
        }

        @Volatile private var instance: QrDatabase? = null

        fun get(context: Context): QrDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, QrDatabase::class.java, "qrcodes.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
