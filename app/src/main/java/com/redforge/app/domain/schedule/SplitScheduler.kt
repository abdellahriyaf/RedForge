package com.redforge.app.domain.schedule

import com.redforge.app.data.local.entities.SplitDay
import com.redforge.app.data.local.entities.WorkoutSession
import com.redforge.app.data.local.entities.WorkoutSessionStatus
import java.util.Calendar
import java.util.TimeZone

/**
 * Pure schedule logic for the active split.
 *
 * The current RedForge scheduler treats a split as a planned calendar cycle. Rest days remain
 * in the cycle, so a three-day cycle of Push / Pull / Rest really does place
 * Rest on the third calendar day. Missed days advance the calendar too; the
 * plan belongs to the calendar rather than getting stuck waiting for a rest
 * day to be "completed" as a workout.
 */
object SplitScheduler {

    fun nextDay(
        days: List<SplitDay>,
        recentSessions: List<WorkoutSession>,
        targetTimeMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        scheduleAnchorStartMillis: Long? = null
    ): SplitDay? = plannedDayForDate(
        days,
        recentSessions,
        targetTimeMillis,
        timeZone,
        scheduleAnchorStartMillis
    )

    fun plannedDayForDate(
        days: List<SplitDay>,
        recentSessions: List<WorkoutSession>,
        targetTimeMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        scheduleAnchorStartMillis: Long? = null
    ): SplitDay? {
        if (days.isEmpty()) return null
        val ordered = days.sortedBy { it.dayOrder }
        val dayIdToIndex = ordered.mapIndexed { index, day -> day.id to index }.toMap()

        val anchorDay = scheduleAnchorStartMillis?.let { calendarFor(it, timeZone) }
        val targetDay = calendarFor(targetTimeMillis, timeZone)

        if (anchorDay != null) {
            val beforeAnchor = calendarDayDifference(
                anchorDay.timeInMillis,
                targetTimeMillis,
                timeZone
            ) < 0
            if (beforeAnchor) return null
        }

        val lastCompletedForThisSplit = recentSessions
            .asSequence()
            .filter { it.status == WorkoutSessionStatus.COMPLETED && it.splitDayId != null && dayIdToIndex.containsKey(it.splitDayId) }
            .filter { session ->
                anchorDay == null ||
                    calendarDayDifference(anchorDay.timeInMillis, session.startedAt, timeZone) >= 0
            }
            .maxByOrNull { it.startedAt }

        if (lastCompletedForThisSplit == null) {
            if (anchorDay == null) return ordered.first()
            val elapsedSinceAnchor = calendarDayDifference(
                anchorDay.timeInMillis,
                targetDay.timeInMillis,
                timeZone
            )
            val targetIndex = elapsedSinceAnchor.toInt() % ordered.size
            return ordered[targetIndex]
        }

        val lastIndex = dayIdToIndex[lastCompletedForThisSplit.splitDayId] ?: return ordered.first()
        val elapsedCalendarDays = calendarDayDifference(
            lastCompletedForThisSplit.startedAt,
            targetTimeMillis,
            timeZone
        )

        if (elapsedCalendarDays < 0) return ordered.first()
        val targetIndex = (lastIndex + elapsedCalendarDays.toInt()) % ordered.size
        return ordered[targetIndex]
    }


    fun isBeforeAnchor(
        anchorStartMillis: Long,
        targetTimeMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean = calendarDayDifference(
        anchorStartMillis,
        targetTimeMillis,
        timeZone
    ) < 0

    private fun calendarDayDifference(fromMillis: Long, toMillis: Long, timeZone: TimeZone): Long {
        val from = calendarFor(fromMillis, timeZone)
        val to = calendarFor(toMillis, timeZone)
        if (sameDay(from, to)) return 0L

        var cursor = from.clone() as Calendar
        var difference = 0L
        if (cursor.before(to)) {
            while (!sameDay(cursor, to)) {
                cursor.add(Calendar.DAY_OF_YEAR, 1)
                difference++
                if (difference > 100_000) break
            }
            return difference
        }

        while (!sameDay(cursor, to)) {
            cursor.add(Calendar.DAY_OF_YEAR, -1)
            difference--
            if (difference < -100_000) break
        }
        return difference
    }

    private fun calendarFor(millis: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = millis }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
