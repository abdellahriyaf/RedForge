package com.redforge.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.redforge.app.data.local.dao.ExerciseDao
import com.redforge.app.data.local.dao.ProgressDao
import com.redforge.app.data.local.dao.SplitDao
import com.redforge.app.data.local.dao.WorkoutDao
import com.redforge.app.data.local.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    version = 3,
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
        private var seedJob: Job? = null

        fun getInstance(context: Context): RedForgeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RedForgeDatabase::class.java,
                    "redforge.db"
                )
                    // Automatic Android backup/device-transfer is disabled; explicit
                    // user-initiated backup export is handled by DataBackupUtil.
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { database ->
                        INSTANCE = database
                        seedJob?.cancel()
                        seedJob = CoroutineScope(Dispatchers.IO).launch {
                            ExerciseLibrarySeeder.ensureSeeded(database)
                        }
                    }
            }

        /** Closes and clears the cached instance so its underlying file can be safely overwritten — used by manual data import. */
        fun closeInstance() {
            synchronized(this) {
                seedJob?.cancel()
                seedJob = null
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }

}
