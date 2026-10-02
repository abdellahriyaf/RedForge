package com.redforge.app.data.local.entities

/**
 * Lifecycle of a persisted workout session.
 *
 * ACTIVE is only valid for the current calendar day. When the calendar day
 * changes, an unfinished ACTIVE session becomes PARTIAL so its logged data
 * remains historical and it can never be resumed as if it were today's work.
 */
enum class WorkoutSessionStatus {
    ACTIVE,
    COMPLETED,
    PARTIAL,
    ABANDONED
}
