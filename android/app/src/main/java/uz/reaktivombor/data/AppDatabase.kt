package uz.reaktivombor.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun dateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun decimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)
}

@Dao
abstract class ReagentDao {
    @Query("SELECT * FROM reagents ORDER BY id")
    abstract fun observeAll(): Flow<List<Reagent>>

    @Query("SELECT * FROM reagents ORDER BY id")
    abstract suspend fun getAll(): List<Reagent>

    @Query("SELECT * FROM reagents WHERE id = :id")
    abstract suspend fun getById(id: Long): Reagent?

    @Insert
    abstract suspend fun insert(reagent: Reagent): Long

    @Insert
    abstract suspend fun insertAll(reagents: List<Reagent>)

    @Update
    abstract suspend fun update(reagent: Reagent)

    @Delete
    abstract suspend fun delete(reagent: Reagent)

    @Query("DELETE FROM reagents")
    abstract suspend fun deleteAll()

    @Query("UPDATE reagents SET notifiedStage = :stage WHERE id = :id")
    abstract suspend fun setNotifiedStage(id: Long, stage: Int)

    /** Zaxiradan tiklash: hammasi yoki hech narsa. */
    @Transaction
    open suspend fun replaceAll(reagents: List<Reagent>) {
        deleteAll()
        insertAll(reagents)
    }
}

@Database(entities = [Reagent::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reagentDao(): ReagentDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "reaktivlar.db")
                    .build()
                    .also { instance = it }
            }
    }
}
