package com.redforge.app.domain.formulas

import com.redforge.app.data.local.entities.SetEntry
import kotlin.math.roundToInt

/**
 * Peer-reviewed / widely used strength formulas, applied to logged sets so
 * RedForge can give the user real scientific feedback beyond raw numbers.
 *
 * Sources for the constants used:
 * - Epley (1985) and Brzycki (1993) 1RM estimation formulas, the two most
 *   commonly validated equations for reps in the 1-10 range.
 * - Standard "tonnage" volume definition (sets x reps x weight) used in
 *   strength & conditioning literature to track training load over time.
 */
object StrengthFormulas {

    // High-rep 1RM extrapolation becomes increasingly unreliable and can
    // produce absurd PRs. RedForge only estimates 1RM from 1-12 reps; higher-
    // rep sets still count normally for volume.
    const val MAX_ESTIMATED_1RM_REPS: Int = 12

    /** Epley formula: 1RM = w * (1 + r/30). Slightly favors higher-rep estimates. */
    fun epley1RM(weight: Double, reps: Int): Double {
        if (reps <= 0) return 0.0
        val effectiveReps = reps.coerceAtMost(MAX_ESTIMATED_1RM_REPS)
        if (effectiveReps == 1) return weight
        return weight * (1 + effectiveReps / 30.0)
    }

    /** Brzycki formula: 1RM = w * 36 / (37 - r). More accurate for reps below ~10. */
    fun brzycki1RM(weight: Double, reps: Int): Double {
        if (reps <= 0) return 0.0
        val effectiveReps = reps.coerceAtMost(MAX_ESTIMATED_1RM_REPS)
        if (effectiveReps == 1) return weight
        return weight * 36.0 / (37.0 - effectiveReps)
    }

    /** Blended estimate — average of Epley and Brzycki, a reasonable single number to show the user. */
    fun estimated1RM(weight: Double, reps: Int): Double {
        if (reps <= 1) return weight
        return (epley1RM(weight, reps) + brzycki1RM(weight, reps)) / 2.0
    }

    /** Total tonnage for a list of sets: sum(weight * reps), excluding warm-up sets. */
    fun totalVolume(sets: List<SetEntry>, includeWarmups: Boolean = false): Double =
        sets.filter { includeWarmups || !it.isWarmup }
            .sumOf { it.weight * it.reps }

    /** Best estimated 1RM among a list of sets for a single exercise. */
    fun bestEstimated1RM(sets: List<SetEntry>): Double =
        sets.filter { !it.isWarmup }
            .maxOfOrNull { estimated1RM(it.weight, it.reps) } ?: 0.0

    /**
     * Percent change in a metric between two periods — used for progressive
     * overload feedback ("+6.2% volume vs last week").
     */
    fun percentChange(previous: Double, current: Double): Double {
        if (previous <= 0.0) return 0.0
        return ((current - previous) / previous) * 100.0
    }

    /** Rounds a display 1RM/volume figure to a clean, human-friendly number. */
    fun displayRounded(value: Double): Int = value.roundToInt()
}
