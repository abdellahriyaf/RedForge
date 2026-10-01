package com.redforge.app.domain.time

import java.util.Calendar
import java.util.TimeZone

/**
 * Calendar helpers plus the lifecycle timeout for an active workout session.
 *
 * A workout session is not tied to midnight: a workout started late at night
 * remains resumable after midnight. The timeout is a safety valve for sessions
 * that were actually abandoned without an explicit finish/discard action.
 */
object WorkoutClock {

    const val ACTIVE_SESSION_TIMEOUT_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun nowMillis(): Long = System.currentTimeMillis()

    fun startOfDayMillis(
        millis: Long = nowMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long = Calendar.getInstance(timeZone).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun isSameCalendarDay(
        firstMillis: Long,
        secondMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean = startOfDayMillis(firstMillis, timeZone) ==
        startOfDayMillis(secondMillis, timeZone)

    fun isBeforeCalendarDay(
        firstMillis: Long,
        secondMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean = startOfDayMillis(firstMillis, timeZone) <
        startOfDayMillis(secondMillis, timeZone)

    fun isActiveSessionExpired(
        lastActivityMillis: Long,
        nowMillis: Long = nowMillis()
    ): Boolean = nowMillis - lastActivityMillis >= ACTIVE_SESSION_TIMEOUT_MILLIS
}
