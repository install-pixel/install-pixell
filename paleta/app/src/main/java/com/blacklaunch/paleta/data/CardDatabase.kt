package com.blacklaunch.paleta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProductCard::class], version = 1, exportSchema = true)
abstract class CardDatabase : RoomDatabase() {
    abstract fun cards(): CardDao

    companion object {
        @Volatile private var instance: CardDatabase? = null

        fun get(context: Context): CardDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CardDatabase::class.java, "cards.db").build().also { instance = it }
        }
    }
}
