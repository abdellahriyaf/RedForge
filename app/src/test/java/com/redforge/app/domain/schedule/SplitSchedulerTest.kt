package com.redforge.app.domain.schedule

import com.redforge.app.data.local.entities.SplitDay
import com.redforge.app.data.local.entities.WorkoutSession
import com.redforge.app.data.local.entities.WorkoutSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SplitSchedulerTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private val days = listOf(
        SplitDay(id = 1, splitId = 10, name = "Push", dayOrder = 1),
        SplitDay(id = 2, splitId = 10, name = "Pull", dayOrder = 2),
        SplitDay(id = 3, splitId = 10, name = "Rest", dayOrder = 3, isRestDay = true),
        SplitDay(id = 4, splitId = 10, name = "Legs", dayOrder = 4)
    )

    @Test
    fun `rest day stays in the calendar cycle`() {
        val lastPull = millis(2026, Calendar.SEPTEMBER, 7)
        val targetRestDay = millis(2026, Calendar.SEPTEMBER, 8)

        val result = SplitScheduler.nextDay(
            days = days,
            recentSessions = listOf(session(dayId = 2, startedAt = lastPull)),
            targetTimeMillis = targetRestDay,
            timeZone = zone
        )

        assertEquals("Rest", result?.name)
        assertEquals(true, result?.isRestDay)
    }


    @Test
    fun `start tomorrow anchor delays a newly activated split`() {
        val today = millis(2026, Calendar.SEPTEMBER, 7)
        val tomorrow = millis(2026, Calendar.SEPTEMBER, 8)
        val anchor = millis(2026, Calendar.SEPTEMBER, 8, 0)

        val before = SplitScheduler.plannedDayForDate(
            days = days,
            recentSessions = emptyList(),
            targetTimeMillis = today,
            timeZone = zone,
            scheduleAnchorStartMillis = anchor
        )
        assertEquals(null, before)

        val firstDay = SplitScheduler.plannedDayForDate(
            days = days,
            recentSessions = emptyList(),
            targetTimeMillis = tomorrow,
            timeZone = zone,
            scheduleAnchorStartMillis = anchor
        )
        assertEquals("Push", firstDay?.name)
    }

    @Test
    fun `calendar advances past rest day to the next training day`() {
        val lastPull = millis(2026, Calendar.SEPTEMBER, 7)
        val targetLegDay = millis(2026, Calendar.SEPTEMBER, 9)

        val result = SplitScheduler.nextDay(
            days = days,
            recentSessions = listOf(session(dayId = 2, startedAt = lastPull)),
            targetTimeMillis = targetLegDay,
            timeZone = zone
        )

        assertEquals("Legs", result?.name)
    }

    private fun session(dayId: Long, startedAt: Long) = WorkoutSession(
        splitDayId = dayId,
        splitDayNameSnapshot = "Day",
        startedAt = startedAt,
        status = WorkoutSessionStatus.COMPLETED
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 18): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis
}
