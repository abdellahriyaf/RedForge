package com.redforge.app.data.repository

import com.redforge.app.data.local.dao.WorkoutDao
import com.redforge.app.data.local.entities.SetEntry
import com.redforge.app.data.local.entities.WorkoutSession
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val dao: WorkoutDao) {

    fun observeInProgressSession(): Flow<WorkoutSession?> = dao.observeInProgressSession()
    suspend fun getInProgressSession(): WorkoutSession? = dao.getInProgressSession()
    suspend fun archiveExpiredSessions() = dao.archiveExpiredSessions()

    fun observeAllSessions(): Flow<List<WorkoutSession>> = dao.observeAllSessions()
    suspend fun getSessionsBetween(from: Long, to: Long) = dao.getSessionsBetween(from, to)
    suspend fun getSession(id: Long) = dao.getSession(id)

    /** Creates and immediately persists a new session — exists on disk before any set is logged. */
    suspend fun startSession(splitDayId: Long?, splitDayName: String): Long =
        dao.upsertSession(WorkoutSession(splitDayId = splitDayId, splitDayNameSnapshot = splitDayName))

    suspend fun completeSession(id: Long) = dao.completeSession(id)
    suspend fun deleteSession(session: WorkoutSession) = dao.deleteSessionAndSets(session)

    fun observeSets(sessionId: Long): Flow<List<SetEntry>> = dao.observeSetsForSession(sessionId)
    suspend fun getSetsOnce(sessionId: Long) = dao.getSetsForSessionOnce(sessionId)

    /**
     * The single most important call in the data layer: writes one set to
     * Room synchronously with the suspend call site (a Room coroutine call
     * commits before returning). Callers should invoke this the instant a
     * set is confirmed — never batch sets in memory to write "later".
     */
    suspend fun logSet(set: SetEntry): Long = dao.upsertSet(set)

    suspend fun updateSet(set: SetEntry) = dao.updateSet(set)
    suspend fun deleteSet(set: SetEntry) = dao.deleteSet(set)

    suspend fun getMaxSetIndex(sessionId: Long, exerciseId: Long): Int =
        dao.getMaxSetIndex(sessionId, exerciseId)

    suspend fun convertAllSetWeights(factor: Double) = dao.scaleAllWeights(factor)

    suspend fun deleteSetAndReindex(set: SetEntry) = dao.deleteSetAndReindex(set)

    suspend fun getRecentSetsForExercise(exerciseId: Long, limit: Int = 50) =
        dao.getRecentSetsForExercise(exerciseId, limit)

    fun observeAllSets(): Flow<List<SetEntry>> = dao.observeAllSets()

    fun observeAllSetsForExercise(exerciseId: Long): Flow<List<SetEntry>> =
        dao.observeAllSetsForExercise(exerciseId)
}
