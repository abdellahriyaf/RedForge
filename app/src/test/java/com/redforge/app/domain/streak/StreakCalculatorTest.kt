package com.redforge.app.domain.streak

import com.redforge.app.data.local.entities.WorkoutSession
import com.redforge.app.data.local.entities.WorkoutSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StreakCalculatorTest {
    private val zone = TimeZone.getTimeZone("UTC")

    @Test
    fun `multiple sessions on one day count once`() {
        val mondayMorning = millis(2026, Calendar.SEPTEMBER, 7, 9)
        val mondayEvening = millis(2026, Calendar.SEPTEMBER, 7, 18)
        val tuesday = millis(2026, Calendar.SEPTEMBER, 8, 18)

        val sessions = listOf(
            completed(mondayMorning),
            completed(mondayEvening),
            completed(tuesday)
        )

        val result = StreakCalculator.compute(sessions, nowMillis = tuesday, timeZone = zone)
        assertEquals(2, result.current)
        assertEquals(2, result.longest)
    }

    @Test
    fun `partial and abandoned sessions do not count toward streaks`() {
        val monday = millis(2026, Calendar.SEPTEMBER, 7, 18)
        val tuesday = millis(2026, Calendar.SEPTEMBER, 8, 18)
        val wednesday = millis(2026, Calendar.SEPTEMBER, 9, 18)

        val sessions = listOf(
            completed(monday),
            WorkoutSession(
                splitDayId = 1L,
                splitDayNameSnapshot = "Training",
                startedAt = tuesday,
                status = WorkoutSessionStatus.PARTIAL
            ),
            WorkoutSession(
                splitDayId = 1L,
                splitDayNameSnapshot = "Training",
                startedAt = wednesday,
                status = WorkoutSessionStatus.ABANDONED
            )
        )

        val result = StreakCalculator.compute(
            sessions,
            nowMillis = wednesday,
            timeZone = zone
        )

        assertEquals(1, result.current)
        assertEquals(1, result.longest)
        assertEquals(monday, result.lastCompletedDayMillis)
    }

    @Test
    fun `gap larger than maximum breaks current streak`() {
        val monday = millis(2026, Calendar.SEPTEMBER, 7, 18)
        val thursday = millis(2026, Calendar.SEPTEMBER, 10, 18)

        val result = StreakCalculator.compute(
            listOf(completed(monday), completed(thursday)),
            maxGapDays = 2,
            nowMillis = thursday,
            timeZone = zone
        )

        assertEquals(1, result.current)
        assertEquals(1, result.longest)
    }

    private fun completed(startedAt: Long) = WorkoutSession(
        splitDayId = 1L,
        splitDayNameSnapshot = "Training",
        startedAt = startedAt,
        status = WorkoutSessionStatus.COMPLETED
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis
}
