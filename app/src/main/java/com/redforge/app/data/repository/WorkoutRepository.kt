package com.redforge.app.data.repository

import com.redforge.app.data.local.dao.WorkoutDao
import com.redforge.app.data.local.entities.SetEntry
import com.redforge.app.data.local.entities.WorkoutSession
import kotlinx.coroutines.flow.Flow
import com.redforge.app.domain.time.WorkoutClock

class WorkoutRepository(private val dao: WorkoutDao) {

    fun observeInProgressSession(): Flow<WorkoutSession?> = dao.observeInProgressSession()

    /**
     * Returns only today's active session. Any ACTIVE session from an earlier
     * calendar day is archived as PARTIAL first, preserving its sets.
     */
    suspend fun getInProgressSession(
        nowMillis: Long = WorkoutClock.nowMillis()
    ): WorkoutSession? {
        val todayStart = WorkoutClock.startOfDayMillis(nowMillis)
        dao.archiveExpiredSessions(todayStart, nowMillis)
        return dao.getInProgressSession(todayStart)
    }

    suspend fun archiveExpiredSessions(
        nowMillis: Long = WorkoutClock.nowMillis()
    ) {
        dao.archiveExpiredSessions(
            WorkoutClock.startOfDayMillis(nowMillis),
            nowMillis
        )
    }

    fun observeAllSessions(): Flow<List<WorkoutSession>> = dao.observeAllSessions()
    suspend fun getSessionsBetween(from: Long, to: Long) = dao.getSessionsBetween(from, to)
    suspend fun getSession(id: Long) = dao.getSession(id)

    /** Creates and immediately persists a new session — exists on disk before any set is logged. */
    suspend fun startSession(splitDayId: Long?, splitDayName: String): Long =
        dao.upsertSession(WorkoutSession(splitDayId = splitDayId, splitDayNameSnapshot = splitDayName))

    suspend fun completeSession(id: Long, nowMillis: Long = WorkoutClock.nowMillis()): Boolean {
        val todayStart = WorkoutClock.startOfDayMillis(nowMillis)
        dao.archiveExpiredSessions(todayStart, nowMillis)
        return dao.completeSession(id, todayStart, nowMillis) == 1
    }

    /**
     * Explicit user discard never destroys logged sets. Empty sessions can be
     * removed; sessions with data become ABANDONED history. The whole decision
     * is made atomically against the current lifecycle state.
     */
    suspend fun abandonSession(session: WorkoutSession, nowMillis: Long = WorkoutClock.nowMillis()): Boolean {
        val todayStart = WorkoutClock.startOfDayMillis(nowMillis)
        dao.archiveExpiredSessions(todayStart, nowMillis)
        return dao.abandonActiveSession(session.id, todayStart, nowMillis)
    }

    suspend fun deleteSession(session: WorkoutSession) = dao.deleteSessionAndSets(session)

    fun observeSets(sessionId: Long): Flow<List<SetEntry>> = dao.observeSetsForSession(sessionId)
    suspend fun getSetsOnce(sessionId: Long) = dao.getSetsForSessionOnce(sessionId)

    /**
     * Writes one set only while the session is ACTIVE, atomically with its
     * set-index allocation.
     */
    suspend fun logSet(set: SetEntry, nowMillis: Long = WorkoutClock.nowMillis()): Boolean {
        val todayStart = WorkoutClock.startOfDayMillis(nowMillis)
        dao.archiveExpiredSessions(todayStart, nowMillis)
        return dao.logSetIfActive(set, todayStart)
    }

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
