package com.redforge.app.domain.time

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WorkoutClockTest {

    private val zone = TimeZone.getTimeZone("UTC")

    @Test
    fun `same calendar day remains true across hours`() {
        val morning = millis(2026, Calendar.SEPTEMBER, 20, 8)
        val evening = millis(2026, Calendar.SEPTEMBER, 20, 23)

        assertTrue(WorkoutClock.isSameCalendarDay(morning, evening, zone))
    }

    @Test
    fun `crossing midnight creates a new workout day`() {
        val beforeMidnight = millis(2026, Calendar.SEPTEMBER, 20, 23)
        val afterMidnight = millis(2026, Calendar.SEPTEMBER, 21, 0)

        assertTrue(WorkoutClock.isBeforeCalendarDay(beforeMidnight, afterMidnight, zone))
        assertFalse(WorkoutClock.isSameCalendarDay(beforeMidnight, afterMidnight, zone))
    }

    @Test
    fun `active session remains valid across midnight`() {
        val beforeMidnight = millis(2026, Calendar.SEPTEMBER, 20, 23) + 50 * 60 * 1000L
        val afterMidnight = millis(2026, Calendar.SEPTEMBER, 21, 1)

        assertFalse(WorkoutClock.isActiveSessionExpired(beforeMidnight, afterMidnight))
    }

    @Test
    fun `active session expires only after inactivity timeout`() {
        val started = millis(2026, Calendar.SEPTEMBER, 20, 8)
        val justBeforeExpiry = started + WorkoutClock.ACTIVE_SESSION_TIMEOUT_MILLIS - 1
        val atExpiry = started + WorkoutClock.ACTIVE_SESSION_TIMEOUT_MILLIS

        assertFalse(WorkoutClock.isActiveSessionExpired(started, justBeforeExpiry))
        assertTrue(WorkoutClock.isActiveSessionExpired(started, atExpiry))
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis
}
