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
    suspend fun upsertSplit(split: Split): Long

    @Delete
    suspend fun deleteSplit(split: Split)

    @Query("UPDATE splits SET isActive = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE splits SET isActive = 1, updatedAt = :now WHERE id = :splitId")
    suspend fun markActive(splitId: Long, now: Long = System.currentTimeMillis()): Int

    /**
     * Activation is one database transaction so observers can never see two
     * active splits or an activation half-applied between the two writes.
     */
    @Transaction
    suspend fun activateSplit(splitId: Long, now: Long = System.currentTimeMillis()): Boolean {
        clearActiveFlag()
        return markActive(splitId, now) == 1
    }

    @Query("SELECT * FROM split_days WHERE splitId = :splitId ORDER BY dayOrder ASC")
    fun observeDaysForSplit(splitId: Long): Flow<List<SplitDay>>

    @Query("SELECT * FROM split_days WHERE splitId = :splitId ORDER BY dayOrder ASC")
    suspend fun getDaysOnce(splitId: Long): List<SplitDay>

    @Query("SELECT * FROM split_days WHERE id = :id")
    suspend fun getDay(id: Long): SplitDay?

    @Upsert
    suspend fun upsertDay(day: SplitDay): Long

    @Delete
    suspend fun deleteDay(day: SplitDay)

    @Query("SELECT * FROM split_day_exercises WHERE splitDayId = :dayId ORDER BY orderIndex ASC")
    fun observeExercisesForDay(dayId: Long): Flow<List<SplitDayExercise>>

    @Query("SELECT * FROM split_day_exercises WHERE splitDayId = :dayId ORDER BY orderIndex ASC")
    suspend fun getExercisesForDayOnce(dayId: Long): List<SplitDayExercise>

    @Upsert
    suspend fun upsertDayExercise(entry: SplitDayExercise): Long

    @Delete
    suspend fun deleteDayExercise(entry: SplitDayExercise)

    @Query("DELETE FROM split_day_exercises WHERE splitDayId = :dayId")
    suspend fun clearExercisesForDay(dayId: Long)

    @Transaction
    suspend fun replaceDayExercises(dayId: Long, exercises: List<SplitDayExercise>) {
        clearExercisesForDay(dayId)
        exercises.forEachIndexed { index, entry ->
            upsertDayExercise(entry.copy(splitDayId = dayId, orderIndex = index))
        }
    }

    @Query("SELECT COUNT(*) FROM split_days WHERE splitId = :splitId")
    suspend fun countDays(splitId: Long): Int
}
