package com.redforge.app.data.local.dao

import androidx.room.*
import com.redforge.app.data.local.entities.SetEntry
import com.redforge.app.data.local.entities.WorkoutSession
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    /**
     * There should only ever be zero or one of these. If the app process
     * was killed mid-workout, this is how we find it again on next launch
     * and resume exactly where the user left off.
     */
    @Query("""
        SELECT * FROM workout_sessions
        WHERE completed = 0 AND abandoned = 0
          AND date(startedAt / 1000, 'unixepoch', 'localtime') = date('now', 'localtime')
        ORDER BY startedAt DESC LIMIT 1
    """)
    suspend fun getInProgressSession(): WorkoutSession?

    @Query("""
        SELECT * FROM workout_sessions
        WHERE completed = 0 AND abandoned = 0
          AND date(startedAt / 1000, 'unixepoch', 'localtime') = date('now', 'localtime')
        ORDER BY startedAt DESC LIMIT 1
    """)
    fun observeInProgressSession(): Flow<WorkoutSession?>

    /** Close yesterday's unfinished sessions without deleting their set entries. */
    @Query("""
        UPDATE workout_sessions
        SET abandoned = 1, endedAt = COALESCE(endedAt, :endedAt)
        WHERE completed = 0 AND abandoned = 0
          AND date(startedAt / 1000, 'unixepoch', 'localtime') < date('now', 'localtime')
    """)
    suspend fun archiveExpiredSessions(endedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun observeAllSessions(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE startedAt BETWEEN :from AND :to ORDER BY startedAt ASC")
    suspend fun getSessionsBetween(from: Long, to: Long): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: Long): WorkoutSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: WorkoutSession): Long

    @Query("UPDATE workout_sessions SET completed = 1, endedAt = :endedAt WHERE id = :id")
    suspend fun completeSession(id: Long, endedAt: Long = System.currentTimeMillis())

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
        WHERE se.exerciseId = :exerciseId AND ws.completed = 1
        ORDER BY se.loggedAt DESC LIMIT :limit
    """)
    suspend fun getRecentSetsForExercise(exerciseId: Long, limit: Int = 50): List<SetEntry>

    @Query("SELECT * FROM set_entries ORDER BY loggedAt DESC")
    fun observeAllSets(): Flow<List<SetEntry>>

    @Query("SELECT * FROM set_entries WHERE exerciseId = :exerciseId ORDER BY loggedAt DESC")
    fun observeAllSetsForExercise(exerciseId: Long): Flow<List<SetEntry>>

    /** Written immediately when a set is confirmed — this single call is the data-loss guarantee. */
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
