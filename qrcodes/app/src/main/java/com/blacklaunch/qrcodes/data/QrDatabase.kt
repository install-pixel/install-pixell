package com.blacklaunch.qrcodes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [QrCode::class], version = 1, exportSchema = true)
abstract class QrDatabase : RoomDatabase() {
    abstract fun codes(): QrDao

    companion object {
        @Volatile private var instance: QrDatabase? = null

        fun get(context: Context): QrDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, QrDatabase::class.java, "qrcodes.db").build().also { instance = it }
        }
    }
}
