package com.redforge.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.redforge.app.data.local.dao.ExerciseDao
import com.redforge.app.data.local.dao.ProgressDao
import com.redforge.app.data.local.dao.SplitDao
import com.redforge.app.data.local.dao.WorkoutDao
import com.redforge.app.data.local.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Exercise::class,
        Split::class,
        SplitDay::class,
        SplitDayExercise::class,
        WorkoutSession::class,
        SetEntry::class,
        ProgressPhoto::class,
        BodyMeasurement::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class RedForgeDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun splitDao(): SplitDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile private var INSTANCE: RedForgeDatabase? = null

        fun getInstance(context: Context): RedForgeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RedForgeDatabase::class.java,
                    "redforge.db"
                )
                    // Automatic Android backup/device-transfer is disabled; explicit
                    // user-initiated backup export is handled by DataBackupUtil.
                    .addCallback(SeedCallback(context.applicationContext))
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }

        /** Closes and clears the cached instance so its underlying file can be safely overwritten — used by manual data import. */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }

    /** Ensures the v0.6 curated catalog exists without touching custom exercises. */
    private class SeedCallback(private val context: Context) : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            CoroutineScope(Dispatchers.IO).launch {
                val database = getInstance(context)
                ExerciseLibrarySeeder.ensureSeeded(database)
            }
        }
    }
}
