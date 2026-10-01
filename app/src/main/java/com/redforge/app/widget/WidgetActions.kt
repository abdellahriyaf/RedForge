package com.redforge.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.updateAll
import com.redforge.app.RedForgeApplication
import com.redforge.app.domain.schedule.SplitScheduler
import com.redforge.app.data.local.entities.WorkoutSessionStatus
import com.redforge.app.domain.time.WorkoutClock
import kotlinx.coroutines.flow.first
import java.util.Calendar

class SkipWorkoutAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val app = context.applicationContext as RedForgeApplication
        val split = app.splitRepository.observeActiveSplit().first() ?: return
        val inProgress = app.workoutRepository.getInProgressSession()
        if (inProgress != null) return

        val sessions = app.workoutRepository.observeAllSessions().first()
        val days = app.splitRepository.observeDays(split.id).first()
        val settings = app.settingsDataStore.settingsFlow.first()

        val now = System.currentTimeMillis()
        val todayStart = WorkoutClock.startOfDayMillis(now)

        val alreadyCompleted = sessions.any { session ->
            session.status == WorkoutSessionStatus.COMPLETED &&
                session.splitDayId != null &&
                days.any { it.id == session.splitDayId } &&
                startOfDayMillis(session.startedAt) == todayStart
        }
        if (alreadyCompleted) return

        val alreadySkipped = settings.skippedSplitId == split.id &&
            settings.skippedWorkoutDayStartMillis == todayStart
        if (alreadySkipped) return

        val anchor = settings.scheduleAnchorStartMillis.takeIf {
            it != null && settings.scheduleAnchorSplitId == split.id
        }
        val plannedDay = SplitScheduler.plannedDayForDate(
            days = days,
            recentSessions = sessions,
            targetTimeMillis = now,
            scheduleAnchorStartMillis = anchor
        )

        if (plannedDay == null || plannedDay.isRestDay) return

        app.settingsDataStore.skipWorkoutDay(split.id, todayStart)
        RedForgeWidget().updateAll(context)
    }

}
