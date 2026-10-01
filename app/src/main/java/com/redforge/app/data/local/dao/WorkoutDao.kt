package com.redforge.app.data.local.dao

import androidx.room.*
import com.redforge.app.data.local.entities.SetEntry
import com.redforge.app.data.local.entities.WorkoutSession
import com.redforge.app.data.local.entities.WorkoutSessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    /**
     * Returns only an ACTIVE session from the requested calendar day.
     * Older ACTIVE sessions are archived by the repository before this query.
     */
    @Query("""
        SELECT * FROM workout_sessions
        WHERE status = 'ACTIVE' AND startedAt >= :todayStart
        ORDER BY startedAt DESC LIMIT 1
    """)
    suspend fun getInProgressSession(todayStart: Long): WorkoutSession?

    /**
     * Legacy reactive observer retained for callers that need the raw ACTIVE stream.
     * Callers must apply the calendar-day rule before presenting it as resumable.
     */
    @Query("""
        SELECT * FROM workout_sessions
        WHERE status = 'ACTIVE'
        ORDER BY startedAt DESC LIMIT 1
    """)
    fun observeInProgressSession(): Flow<WorkoutSession?>

    @Query("""
        UPDATE workout_sessions
        SET status = 'PARTIAL', endedAt = :endedAt
        WHERE status = 'ACTIVE' AND startedAt < :todayStart
    """)
    suspend fun archiveExpiredSessions(
        todayStart: Long,
        endedAt: Long
    ): Int

    @Query("""
        UPDATE workout_sessions
        SET status = 'COMPLETED', endedAt = :endedAt
        WHERE id = :id AND status = 'ACTIVE' AND startedAt >= :todayStart
    """)
    suspend fun completeSession(id: Long, todayStart: Long, endedAt: Long = System.currentTimeMillis()): Int

    @Query("""
        UPDATE workout_sessions
        SET status = 'ABANDONED', endedAt = :endedAt
        WHERE id = :id AND status = 'ACTIVE' AND startedAt >= :todayStart
    """)
    suspend fun abandonSession(id: Long, todayStart: Long, endedAt: Long = System.currentTimeMillis()): Int

    /**
     * Atomically verifies that a session is still ACTIVE and then either removes
     * an empty session or preserves its logged sets as ABANDONED history.
     */
    @Transaction
    suspend fun abandonActiveSession(id: Long, todayStart: Long, endedAt: Long = System.currentTimeMillis()): Boolean {
        val session = getSession(id) ?: return false
        if (session.status != WorkoutSessionStatus.ACTIVE || session.startedAt < todayStart) return false

        if (getSetCountForSession(id) == 0) {
            deleteSessionAndSets(session)
        } else {
            if (abandonSession(id, todayStart, endedAt) != 1) return false
        }
        return true
    }

    /**
     * Atomically verifies the session lifecycle and writes the set.
     * This prevents midnight archival from occurring between an ACTIVE check
     * and the actual set insert.
     */
    @Transaction
    suspend fun logSetIfActive(set: SetEntry, todayStart: Long): Boolean {
        val session = getSession(set.workoutSessionId) ?: return false
        if (session.status != WorkoutSessionStatus.ACTIVE || session.startedAt < todayStart) return false

        val nextIndex = getMaxSetIndex(set.workoutSessionId, set.exerciseId) + 1
        upsertSet(set.copy(setIndex = nextIndex))
        return true
    }

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun observeAllSessions(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE startedAt BETWEEN :from AND :to ORDER BY startedAt ASC")
    suspend fun getSessionsBetween(from: Long, to: Long): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: Long): WorkoutSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: WorkoutSession): Long

    @Delete
    suspend fun deleteSession(session: WorkoutSession)

    @Transaction
    suspend fun deleteSessionAndSets(session: WorkoutSession) {
        clearSetsForSession(session.id)
        deleteSession(session)
    }

    // ---- Sets ----

    @Query("SELECT * FROM set_entries WHERE workoutSessionId = :sessionId ORDER BY exerciseId, setIndex ASC")
    fun observeSetsForSession(sessionId: Long): Flow<List<SetEntry>>

    @Query("SELECT * FROM set_entries WHERE workoutSessionId = :sessionId ORDER BY exerciseId, setIndex ASC")
    suspend fun getSetsForSessionOnce(sessionId: Long): List<SetEntry>

    /** Every previous set logged for this exercise, most recent workout first — used for "last time you did X" prompts. */
    @Query("""
        SELECT se.* FROM set_entries se
        INNER JOIN workout_sessions ws ON ws.id = se.workoutSessionId
        WHERE se.exerciseId = :exerciseId AND ws.status = 'COMPLETED'
        ORDER BY se.loggedAt DESC LIMIT :limit
    """)
    suspend fun getRecentSetsForExercise(exerciseId: Long, limit: Int = 50): List<SetEntry>

    @Query("SELECT * FROM set_entries ORDER BY loggedAt DESC")
    fun observeAllSets(): Flow<List<SetEntry>>

    @Query("SELECT * FROM set_entries WHERE exerciseId = :exerciseId ORDER BY loggedAt DESC")
    fun observeAllSetsForExercise(exerciseId: Long): Flow<List<SetEntry>>

    @Query("SELECT COUNT(*) FROM set_entries WHERE workoutSessionId = :sessionId")
    suspend fun getSetCountForSession(sessionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSet(set: SetEntry): Long

    @Query("SELECT COALESCE(MAX(setIndex), 0) FROM set_entries WHERE workoutSessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun getMaxSetIndex(sessionId: Long, exerciseId: Long): Int

    @Query("UPDATE set_entries SET setIndex = :setIndex WHERE id = :setId")
    suspend fun updateSetIndex(setId: Long, setIndex: Int)

    @Query("UPDATE set_entries SET weight = weight * :factor")
    suspend fun scaleAllWeights(factor: Double)

    @Update
    suspend fun updateSet(set: SetEntry)

    @Delete
    suspend fun deleteSet(set: SetEntry)

    @Query("DELETE FROM set_entries WHERE workoutSessionId = :sessionId")
    suspend fun clearSetsForSession(sessionId: Long)

    @Transaction
    suspend fun deleteSetAndReindex(set: SetEntry) {
        deleteSet(set)
        val remaining = getSetsForSessionOnce(set.workoutSessionId)
            .filter { it.exerciseId == set.exerciseId }
            .sortedBy { it.setIndex }
        remaining.forEachIndexed { index, entry ->
            val wantedIndex = index + 1
            if (entry.setIndex != wantedIndex) updateSetIndex(entry.id, wantedIndex)
        }
    }
}
