package com.redforge.app.data.local.dao

import androidx.room.*
import com.redforge.app.data.local.entities.Split
import com.redforge.app.data.local.entities.SplitDay
import com.redforge.app.data.local.entities.SplitDayExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitDao {

    @Query("SELECT * FROM splits ORDER BY updatedAt DESC")
    fun observeAllSplits(): Flow<List<Split>>

    @Query("SELECT * FROM splits WHERE isActive = 1 LIMIT 1")
    fun observeActiveSplit(): Flow<Split?>

    @Query("SELECT * FROM splits WHERE id = :id")
    suspend fun getSplit(id: Long): Split?

    @Upsert
    suspend fun upsertSplit(split: Split)

    @Delete
    suspend fun deleteSplit(split: Split)

    /** Clears the active flag on every split — call before setting a new active one. */
    @Query("UPDATE splits SET isActive = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE splits SET isActive = 1, updatedAt = :now WHERE id = :splitId")
    suspend fun markActive(splitId: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM split_days WHERE splitId = :splitId ORDER BY dayOrder ASC")
    fun observeDaysForSplit(splitId: Long): Flow<List<SplitDay>>

    @Query("SELECT * FROM split_days WHERE splitId = :splitId ORDER BY dayOrder ASC")
    suspend fun getDaysOnce(splitId: Long): List<SplitDay>

    @Query("SELECT * FROM split_days WHERE id = :id")
    suspend fun getDay(id: Long): SplitDay?

    @Upsert
    suspend fun upsertDay(day: SplitDay)

    @Delete
    suspend fun deleteDay(day: SplitDay)

    @Query("SELECT * FROM split_day_exercises WHERE splitDayId = :dayId ORDER BY orderIndex ASC")
    fun observeExercisesForDay(dayId: Long): Flow<List<SplitDayExercise>>

    @Query("SELECT * FROM split_day_exercises WHERE splitDayId = :dayId ORDER BY orderIndex ASC")
    suspend fun getExercisesForDayOnce(dayId: Long): List<SplitDayExercise>

    @Upsert
    suspend fun upsertDayExercise(entry: SplitDayExercise)

    @Delete
    suspend fun deleteDayExercise(entry: SplitDayExercise)

    @Query("DELETE FROM split_day_exercises WHERE splitDayId = :dayId")
    suspend fun clearExercisesForDay(dayId: Long)

    @Query("SELECT COUNT(*) FROM split_days WHERE splitId = :splitId")
    suspend fun countDays(splitId: Long): Int
}
