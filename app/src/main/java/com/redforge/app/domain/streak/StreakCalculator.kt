package com.redforge.app.domain.streak

import com.redforge.app.data.local.entities.WorkoutSession
import com.redforge.app.data.local.entities.WorkoutSessionStatus
import java.util.Calendar
import java.util.TimeZone

data class StreakResult(
    val current: Int,
    val longest: Int,
    val lastCompletedDayMillis: Long
)

/**
 * Computes a training streak from completed sessions.
 *
 * When schedule information is supplied, only scheduled training days count toward the streak;
 * scheduled rest days do not break it. A missed scheduled training day breaks the streak, while
 * the current day remains open until it is completed or explicitly skipped.
 *
 * The max-gap behavior remains available as a fallback for callers that do not have a split
 * schedule.
 */
object StreakCalculator {

    fun compute(
        sessions: List<WorkoutSession>,
        maxGapDays: Int = 2,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        scheduledTrainingDayOrders: Set<Int> = emptySet(),
        cycleLength: Int = 0,
        scheduleAnchorStartMillis: Long? = null,
        skippedDayStartMillis: Long? = null
    ): StreakResult {
        val completed = sessions.filter { it.status == WorkoutSessionStatus.COMPLETED }.sortedBy { it.startedAt }
        if (completed.isEmpty()) return StreakResult(0, 0, 0L)

        val uniqueDays = completed
            .map { CalendarDay.from(it.startedAt, timeZone) }
            .distinct()
            .sorted()

        if (scheduledTrainingDayOrders.isNotEmpty() &&
            cycleLength > 0 &&
            scheduleAnchorStartMillis != null
        ) {
            return computeScheduled(
                uniqueCompletedDays = uniqueDays.toSet(),
                lastCompletedMillis = completed.last().startedAt,
                nowMillis = nowMillis,
                timeZone = timeZone,
                scheduledTrainingDayOrders = scheduledTrainingDayOrders,
                cycleLength = cycleLength,
                scheduleAnchorStartMillis = scheduleAnchorStartMillis,
                skippedDayStartMillis = skippedDayStartMillis
            )
        }

        var current = 1
        var longest = 1
        for (index in 1 until uniqueDays.size) {
            val gap = uniqueDays[index].differenceFrom(uniqueDays[index - 1])
            current = if (gap <= maxGapDays) current + 1 else 1
            longest = maxOf(longest, current)
        }

        val today = CalendarDay.from(nowMillis, timeZone)
        val daysSinceLast = today.differenceFrom(uniqueDays.last())
        val liveCurrent = if (daysSinceLast > maxGapDays) 0 else current

        return StreakResult(
            current = liveCurrent,
            longest = longest,
            lastCompletedDayMillis = completed.last().startedAt
        )
    }

    private fun computeScheduled(
        uniqueCompletedDays: Set<CalendarDay>,
        lastCompletedMillis: Long,
        nowMillis: Long,
        timeZone: TimeZone,
        scheduledTrainingDayOrders: Set<Int>,
        cycleLength: Int,
        scheduleAnchorStartMillis: Long,
        skippedDayStartMillis: Long?
    ): StreakResult {
        val anchor = CalendarDay.from(scheduleAnchorStartMillis, timeZone)
        val today = CalendarDay.from(nowMillis, timeZone)
        val firstDay = minOf(anchor, uniqueCompletedDays.minOrNull() ?: anchor)
        val endDay = maxOf(today, uniqueCompletedDays.maxOrNull() ?: today)

        var day = firstDay
        var running = 0
        var longest = 0
        var current = 0
        var sawCurrentTrainingDay = false
        var brokenSinceLastCompleted = false

        while (day <= endDay) {
            val scheduled = isScheduledTrainingDay(
                day = day,
                anchor = anchor,
                cycleLength = cycleLength,
                scheduledTrainingDayOrders = scheduledTrainingDayOrders,
                timeZone = timeZone
            )

            if (scheduled) {
                val completedToday = day in uniqueCompletedDays
                val skippedToday = skippedDayStartMillis != null &&
                    day == CalendarDay.from(skippedDayStartMillis, timeZone)

                when {
                    completedToday -> {
                        running += 1
                        longest = maxOf(longest, running)
                        if (day <= today) {
                            current = running
                            sawCurrentTrainingDay = true
                            brokenSinceLastCompleted = false
                        }
                    }
                    day < today || skippedToday -> {
                        running = 0
                        if (day <= today) {
                            brokenSinceLastCompleted = true
                            if (day < today || skippedToday) current = 0
                        }
                    }
                    else -> {
                        // Today is still open; don't break an otherwise live streak.
                    }
                }
            }

            day = day.plusDays(timeZone)
        }

        val lastCompletedDay = CalendarDay.from(lastCompletedMillis, timeZone)
        val todayScheduled = isScheduledTrainingDay(
            day = today,
            anchor = anchor,
            cycleLength = cycleLength,
            scheduledTrainingDayOrders = scheduledTrainingDayOrders,
            timeZone = timeZone
        )

        val liveCurrent = when {
            skippedDayStartMillis != null &&
                today == CalendarDay.from(skippedDayStartMillis, timeZone) -> 0
            todayScheduled && !uniqueCompletedDays.contains(today) -> {
                if (brokenSinceLastCompleted) 0 else current
            }
            lastCompletedDay == today -> current
            else -> current
        }

        return StreakResult(
            current = liveCurrent.coerceAtLeast(0),
            longest = longest,
            lastCompletedDayMillis = lastCompletedMillis
        )
    }

    private fun isScheduledTrainingDay(
        day: CalendarDay,
        anchor: CalendarDay,
        cycleLength: Int,
        scheduledTrainingDayOrders: Set<Int>,
        timeZone: TimeZone
    ): Boolean {
        val elapsed = day.differenceFrom(anchor)
        if (elapsed < 0) return false
        val zeroBasedOrder = (elapsed % cycleLength).toInt()
        return (zeroBasedOrder + 1) in scheduledTrainingDayOrders
    }

    private data class CalendarDay(
        val era: Int,
        val year: Int,
        val dayOfYear: Int
    ) : Comparable<CalendarDay> {
        companion object {
            fun from(millis: Long, timeZone: TimeZone): CalendarDay {
                val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
                return CalendarDay(
                    era = calendar.get(Calendar.ERA),
                    year = calendar.get(Calendar.YEAR),
                    dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
                )
            }
        }

        override fun compareTo(other: CalendarDay): Int =
            compareValuesBy(this, other, { it.era }, { it.year }, { it.dayOfYear })

        fun differenceFrom(other: CalendarDay): Int {
            // Calendar-day ordinal arithmetic keeps DST transitions from changing the
            // distance and avoids the previous day-by-day traversal (O(gapDays)).
            val thisYear = if (era == 1) year else 1 - year
            val otherYear = if (other.era == 1) other.year else 1 - other.year
            return (daysBeforeYear(thisYear) + dayOfYear - 1L -
                (daysBeforeYear(otherYear) + other.dayOfYear - 1L)).toInt()
        }

        private fun daysBeforeYear(year: Int): Long {
            val y = year - 1L
            return 365L * y + Math.floorDiv(y, 4L) -
                Math.floorDiv(y, 100L) + Math.floorDiv(y, 400L)
        }

        fun plusDays(timeZone: TimeZone): CalendarDay {
            val calendar = toCalendar(timeZone).also {
                it.set(Calendar.ERA, era)
                it.set(Calendar.YEAR, year)
                it.set(Calendar.DAY_OF_YEAR, dayOfYear)
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            return from(calendar.timeInMillis, timeZone)
        }

        private fun toCalendar(timeZone: TimeZone): Calendar =
            Calendar.getInstance(timeZone).apply {
                clear()
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
    }

}
