package com.redforge.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A training split (e.g. "Push/Pull/Legs", "Upper/Lower", "Bro Split").
 * Exactly one split should have [isActive] = true at a time — that's the
 * one shown on the Home screen and used for streak calculation.
 */
@Entity(tableName = "splits")
data class Split(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean = false,
    val daysPerCycle: Int = 0, // denormalized count, kept in sync by the repository
    val isDeloadCycle: Boolean = false, // when true, Active Workout scales displayed target sets down
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * One day within a split's cycle (e.g. "Push Day", "Leg Day", "Rest Day").
 * [dayOrder] defines the sequence the days repeat in (1, 2, 3, ... then back
 * to 1). A day with no exercises attached is treated as a rest day.
 */
@Entity(
    tableName = "split_days",
    foreignKeys = [
        ForeignKey(
            entity = Split::class,
            parentColumns = ["id"],
            childColumns = ["splitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("splitId"), Index(value = ["splitId", "dayOrder"], unique = true)]
)
data class SplitDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitId: Long,
    val name: String,
    val dayOrder: Int,
    val isRestDay: Boolean = false
)

/**
 * An exercise placed inside a split day, with the day-specific prescription
 * (target sets/reps/rest). The exercise itself lives in [Exercise] so it can
 * be reused and edited across many split days.
 *
 * [supersetGroup] links two or more exercises within the same day into a
 * superset/circuit: exercises sharing the same non-null group id are
 * performed back-to-back, with the rest timer only firing after the last
 * exercise in the group rather than after every single one.
 */
@Entity(
    tableName = "split_day_exercises",
    foreignKeys = [
        ForeignKey(
            entity = SplitDay::class,
            parentColumns = ["id"],
            childColumns = ["splitDayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("splitDayId"), Index("exerciseId"), Index(value = ["splitDayId", "orderIndex"], unique = true)]
)
data class SplitDayExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitDayId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val targetSets: Int = 3,
    val targetRepsLow: Int = 8,
    val targetRepsHigh: Int = 12,
    val targetRestSeconds: Int = 0,
    val supersetGroup: Int? = null
)
