package com.redforge.app.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.redforge.app.MainActivity
import com.redforge.app.RedForgeApplication
import com.redforge.app.domain.schedule.SplitScheduler
import com.redforge.app.data.local.entities.WorkoutSessionStatus
import com.redforge.app.domain.time.WorkoutClock
import com.redforge.app.domain.streak.StreakCalculator
import java.util.Calendar
import kotlinx.coroutines.flow.first

class RedForgeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as RedForgeApplication
        val activeSplit = app.splitRepository.observeActiveSplit().first()
        val sessions = app.workoutRepository.observeAllSessions().first()
        val inProgress = app.workoutRepository.getInProgressSession()
        val settings = app.settingsDataStore.settingsFlow.first()
        val todayStart = WorkoutClock.startOfDayMillis()
        val todaySkipped = activeSplit != null &&
            settings.skippedSplitId == activeSplit.id &&
            settings.skippedWorkoutDayStartMillis == todayStart
        val rawStreak = StreakCalculator.compute(sessions).current
        val streak = if (todaySkipped) 0 else rawStreak

        val days = activeSplit?.let {
            app.splitRepository.observeDays(it.id).first()
        }.orEmpty()

        val anchor = settings.scheduleAnchorStartMillis.takeIf {
            it != null && settings.scheduleAnchorSplitId == activeSplit?.id
        }

        val nextDay = if (days.isNotEmpty()) {
            SplitScheduler.nextDay(
                days = days,
                recentSessions = sessions,
                scheduleAnchorStartMillis = anchor
            )
        } else null

        val todayCompleted = sessions.any { session ->
            if (session.status != WorkoutSessionStatus.COMPLETED) return@any false
            val start = Calendar.getInstance().apply { timeInMillis = session.startedAt }
            val today = Calendar.getInstance()
            start.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                start.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        }

        val title = when {
            inProgress != null -> "Workout in progress"
            todayCompleted -> "Training complete"
            todaySkipped -> "Workout skipped today"
            nextDay?.isRestDay == true -> "Rest day"
            anchor?.let { SplitScheduler.isBeforeAnchor(it, System.currentTimeMillis()) } == true -> "Starts later"
            nextDay != null -> nextDay.name
            else -> "Build a split"
        }

        val startIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("start_workout", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF17171A))
                    .padding(16.dp)
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        "REDFORGE",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFFB020)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    title,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    "🔥 $streak day streak",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFE4141B)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )

                Spacer(modifier = GlanceModifier.height(10.dp))

                if (inProgress != null) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            "RESUME",
                            modifier = GlanceModifier
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                                .background(Color(0xFFE4141B))
                                .clickable(actionStartActivity(startIntent)),
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            "OPEN",
                            modifier = GlanceModifier
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                                .clickable(actionStartActivity(openIntent)),
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFB7B7C0)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                } else if (!todayCompleted && !todaySkipped && nextDay != null && !nextDay.isRestDay) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            "START",
                            modifier = GlanceModifier
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                                .background(Color(0xFFE4141B))
                                .clickable(actionStartActivity(startIntent)),
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            "SKIP",
                            modifier = GlanceModifier
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                                .clickable(actionRunCallback<SkipWorkoutAction>()),
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFB7B7C0)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            "OPEN",
                            modifier = GlanceModifier
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                                .clickable(actionStartActivity(openIntent)),
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFB7B7C0)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                } else {
                    Text(
                        "OPEN REDFORGE",
                        modifier = GlanceModifier
                            .padding(top = 4.dp)
                            .clickable(actionStartActivity(openIntent)),
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

class RedForgeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RedForgeWidget()
}
