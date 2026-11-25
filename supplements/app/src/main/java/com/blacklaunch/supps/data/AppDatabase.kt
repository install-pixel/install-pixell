package com.blacklaunch.supps.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SuppsDao {
    @Query("SELECT * FROM supplement ORDER BY sortOrder, id")
    abstract fun supplements(): Flow<List<Supplement>>

    @Query("SELECT * FROM alt_group ORDER BY id")
    abstract fun groups(): Flow<List<AltGroup>>

    @Query("SELECT * FROM intake ORDER BY day")
    abstract fun intakes(): Flow<List<Intake>>

    @Query("SELECT * FROM check_in ORDER BY day")
    abstract fun checkIns(): Flow<List<CheckIn>>

    @Query("SELECT * FROM purchase ORDER BY day, id")
    abstract fun purchases(): Flow<List<Purchase>>

    @Query("SELECT * FROM supplement WHERE id = :id")
    abstract suspend fun supplement(id: Long): Supplement?

    @Insert
    abstract suspend fun insertSupplement(s: Supplement): Long

    @Upsert
    abstract suspend fun upsertSupplement(s: Supplement)

    @Delete
    abstract suspend fun deleteSupplement(s: Supplement)

    @Insert
    abstract suspend fun insertGroup(g: AltGroup): Long

    @Upsert
    abstract suspend fun upsertGroup(g: AltGroup)

    @Delete
    abstract suspend fun deleteGroup(g: AltGroup)

    @Query("SELECT * FROM intake WHERE supplementId = :id AND day = :day")
    abstract suspend fun intake(id: Long, day: Long): Intake?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertIntake(i: Intake): Long

    @Delete
    abstract suspend fun deleteIntake(i: Intake)

    @Upsert
    abstract suspend fun upsertCheckIn(c: CheckIn)

    @Query("SELECT * FROM check_in WHERE day = :day")
    abstract suspend fun checkIn(day: Long): CheckIn?

    @Query("DELETE FROM check_in WHERE day = :day")
    abstract suspend fun deleteCheckIn(day: Long)

    @Insert
    abstract suspend fun insertPurchase(p: Purchase): Long

    @Query("DELETE FROM intake WHERE supplementId = :id")
    abstract suspend fun deleteIntakesOf(id: Long)

    @Query("DELETE FROM purchase WHERE supplementId = :id")
    abstract suspend fun deletePurchasesOf(id: Long)

    @Query("UPDATE supplement SET groupId = NULL, plan = 'Daily' WHERE groupId = :groupId")
    abstract suspend fun ungroup(groupId: Long)

    @Query("DELETE FROM supplement")
    abstract suspend fun clearSupplements()

    @Query("DELETE FROM alt_group")
    abstract suspend fun clearGroups()

    @Query("DELETE FROM intake")
    abstract suspend fun clearIntakes()

    @Query("DELETE FROM check_in")
    abstract suspend fun clearCheckIns()

    @Query("DELETE FROM purchase")
    abstract suspend fun clearPurchases()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSupplements(list: List<Supplement>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertGroups(list: List<AltGroup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertIntakes(list: List<Intake>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCheckIns(list: List<CheckIn>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPurchases(list: List<Purchase>)

    /** Повне відновлення з копії: усе старе замінюється. */
    @Transaction
    open suspend fun replaceAll(
        supplements: List<Supplement>,
        groups: List<AltGroup>,
        intakes: List<Intake>,
        checkIns: List<CheckIn>,
        purchases: List<Purchase>,
    ) {
        clearIntakes()
        clearPurchases()
        clearCheckIns()
        clearSupplements()
        clearGroups()
        insertGroups(groups)
        insertSupplements(supplements)
        insertIntakes(intakes)
        insertCheckIns(checkIns)
        insertPurchases(purchases)
    }
}

@Database(
    entities = [Supplement::class, AltGroup::class, Intake::class, CheckIn::class, Purchase::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SuppsDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "supps.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

        /**
         * v1 → v2: оцінка дня ділиться на ранок і вечір (тип дня прибрано — час береться з відміток), у добавки —
         * категорія. Старі відповіді: «Сон» → перше ранкове, «Настрій» і «Ясність» → перші вечірні.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE supplement ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS check_in_new (day INTEGER NOT NULL, m1 INTEGER, m2 INTEGER, m3 INTEGER, " +
                        "e1 INTEGER, e2 INTEGER, e3 INTEGER, morningAt INTEGER, eveningAt INTEGER, note TEXT NOT NULL, PRIMARY KEY(day))",
                )
                db.execSQL("INSERT INTO check_in_new (day, m1, e1, e2, note) SELECT day, a2, a1, a3, note FROM check_in")
                db.execSQL("DROP TABLE check_in")
                db.execSQL("ALTER TABLE check_in_new RENAME TO check_in")
            }
        }
    }
}
