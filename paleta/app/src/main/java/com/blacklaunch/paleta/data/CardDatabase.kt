package com.blacklaunch.paleta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProductCard::class], version = 2, exportSchema = true)
abstract class CardDatabase : RoomDatabase() {
    abstract fun cards(): CardDao

    companion object {
        /** 1 → 2: другий шар зі своєю розкладкою. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN layout2 TEXT")
                db.execSQL("ALTER TABLE cards ADD COLUMN perLayer2 INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: CardDatabase? = null

        fun get(context: Context): CardDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CardDatabase::class.java, "cards.db").addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
