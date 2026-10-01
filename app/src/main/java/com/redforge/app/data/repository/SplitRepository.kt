package com.redforge.app.data.repository

import com.redforge.app.data.local.dao.SplitDao
import com.redforge.app.data.local.entities.Split
import com.redforge.app.data.local.entities.SplitDay
import com.redforge.app.data.local.entities.SplitDayExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SplitRepository(private val dao: SplitDao) {

    private val activationMutex = Mutex()

    fun observeAllSplits(): Flow<List<Split>> = dao.observeAllSplits()
    fun observeActiveSplit(): Flow<Split?> = dao.observeActiveSplit()
    suspend fun getSplit(id: Long) = dao.getSplit(id)

    suspend fun saveSplit(split: Split): Long = dao.upsertSplit(split.copy(updatedAt = System.currentTimeMillis()))

    /** Deletes a split along with every day and exercise-assignment that belongs to it — nothing orphaned. */
    suspend fun deleteSplit(split: Split) {
        dao.deleteSplit(split)
    }

    /** Activates [splitId] as the one-and-only active split (used for streaks + Home). */
    suspend fun setActiveSplit(splitId: Long) {
        activationMutex.withLock {
            dao.clearActiveFlag()
            dao.markActive(splitId)
        }
    }

    suspend fun clearActiveSplit() {
        activationMutex.withLock { dao.clearActiveFlag() }
    }

    fun observeDays(splitId: Long): Flow<List<SplitDay>> = dao.observeDaysForSplit(splitId)
    suspend fun getDay(id: Long) = dao.getDay(id)
    suspend fun saveDay(day: SplitDay): Long = dao.upsertDay(day)

    /** Deletes a day and any exercise-assignments under it — nothing orphaned. */
    suspend fun deleteDay(day: SplitDay) {
        dao.deleteDay(day)
    }

    fun observeDayExercises(dayId: Long): Flow<List<SplitDayExercise>> = dao.observeExercisesForDay(dayId)
    suspend fun getDayExercisesOnce(dayId: Long) = dao.getExercisesForDayOnce(dayId)
    suspend fun saveDayExercise(entry: SplitDayExercise): Long = dao.upsertDayExercise(entry)
    suspend fun deleteDayExercise(entry: SplitDayExercise) = dao.deleteDayExercise(entry)

    /** Replaces the full exercise list for a day in one shot — used by the drag-to-reorder editor. */
    suspend fun replaceDayExercises(dayId: Long, exercises: List<SplitDayExercise>) {
        dao.replaceDayExercises(dayId, exercises)
    }

}
