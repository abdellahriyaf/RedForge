package com.redforge.app.domain.time

import java.util.Calendar
import java.util.TimeZone

/**
 * Single source of truth for calendar-day boundaries used by workout lifecycle
 * decisions. Workout sessions are calendar-day based, not rolling 24-hour based.
 */
object WorkoutClock {

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
}
