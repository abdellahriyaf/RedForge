package com.redforge.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One workout instance — a specific date the user trained a given split day.
 * Created the moment the user opens "Start Workout" and immediately written
 * to Room, so it exists on disk before a single set is even logged. This is
 * the anchor that lets us survive process death: on relaunch we resume only
 * an [WorkoutSessionStatus.ACTIVE] session from the current calendar day.
 * Expired unfinished sessions are converted to PARTIAL and remain in history.
 */
@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(entity = SplitDay::class, parentColumns = ["id"], childColumns = ["splitDayId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["status", "startedAt"]), Index("splitDayId")]
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitDayId: Long?, // null for a freeform/off-split workout
    val splitDayNameSnapshot: String, // captured at start time so history reads correctly even if the split is edited later
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val status: WorkoutSessionStatus = WorkoutSessionStatus.ACTIVE,
    val notes: String = ""
)

/**
 * A single logged set. Every field is written to Room the instant the user
 * confirms the set (not held in a ViewModel/memory-only state) — this is
 * the core guarantee behind "if the app is killed mid-workout, nothing is
 * lost". Re-opening the active session just re-queries these rows.
 */
@Entity(tableName = "set_entries", foreignKeys = [
    ForeignKey(entity = WorkoutSession::class, parentColumns = ["id"], childColumns = ["workoutSessionId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT)
], indices = [
    Index("workoutSessionId"), Index("exerciseId") , Index(value = ["workoutSessionId", "exerciseId", "setIndex"])
])
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutSessionId: Long,
    val exerciseId: Long,
    val setIndex: Int, // 1-based order within that exercise for that session
    val weight: Double,
    val reps: Int,
    val isWarmup: Boolean = false,
    val rpe: Float? = null, // optional 1-10 rate of perceived exertion, for scientific tracking
    val completed: Boolean = true,
    val isPersonalRecord: Boolean = false, // set true if this beat the exercise's best estimated 1RM at log time
    val loggedAt: Long = System.currentTimeMillis()
)
