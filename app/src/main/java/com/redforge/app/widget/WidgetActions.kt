package com.redforge.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionCallback
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.updateAll
import com.redforge.app.RedForgeApplication
import com.redforge.app.domain.schedule.SplitScheduler
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
        val inProgress = app.workoutRepository.observeInProgressSession().first()
        if (inProgress != null) return

        val sessions = app.workoutRepository.observeAllSessions().first()
        val days = app.splitRepository.observeDays(split.id).first()
        val settings = app.settingsDataStore.settingsFlow.first()

        val now = System.currentTimeMillis()
        val todayStart = startOfDayMillis(now)

        val alreadyCompleted = sessions.any { session ->
            session.completed &&
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

    private fun startOfDayMillis(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
