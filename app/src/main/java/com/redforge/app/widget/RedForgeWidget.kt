package com.redforge.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.redforge.app.MainActivity
import com.redforge.app.R
import com.redforge.app.RedForgeApplication
import com.redforge.app.domain.schedule.SplitScheduler
import com.redforge.app.domain.streak.StreakCalculator
import java.util.Calendar
import kotlinx.coroutines.flow.first

class RedForgeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as RedForgeApplication

        val activeSplit = app.splitRepository.observeActiveSplit().first()
        val sessions = app.workoutRepository.observeAllSessions().first()
        val inProgress = app.workoutRepository.observeInProgressSession().first()
        val settings = app.settingsDataStore.settingsFlow.first()

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todaySkipped = activeSplit != null &&
            settings.skippedSplitId == activeSplit.id &&
            settings.skippedWorkoutDayStartMillis == todayStart

        val streak = if (todaySkipped) 0 else StreakCalculator.compute(sessions).current

        val days = activeSplit?.let { splitRepository ->
            app.splitRepository.observeDays(splitRepository.id).first()
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
            if (!session.completed) return@any false
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
                    .background(ImageProvider(R.drawable.widget_background_gradient))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(ImageProvider(R.drawable.widget_header_surface))
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    Text(
                        "REDFORGE",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFFB020)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        title,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    )
                }

                Spacer(GlanceModifier.height(10.dp))

                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(ImageProvider(R.drawable.widget_stat_surface))
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Text(
                        "CURRENT STREAK",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFB7B7C0)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        "$streak DAYS",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFF6B35)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(GlanceModifier.height(10.dp))

                when {
                    inProgress != null -> {
                        Text(
                            "RESUME WORKOUT",
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ImageProvider(R.drawable.widget_primary_action))
                                .padding(vertical = 13.dp, horizontal = 16.dp)
                                .clickable(actionStartActivity(startIntent)),
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }

                    !todayCompleted && !todaySkipped && nextDay != null && !nextDay.isRestDay -> {
                        Text(
                            "START WORKOUT",
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ImageProvider(R.drawable.widget_primary_action))
                                .padding(vertical = 13.dp, horizontal = 16.dp)
                                .clickable(actionStartActivity(startIntent)),
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(GlanceModifier.height(8.dp))
                        Text(
                            "SKIP TODAY",
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ImageProvider(R.drawable.widget_secondary_action))
                                .padding(vertical = 11.dp, horizontal = 16.dp)
                                .clickable(actionRunCallback<SkipWorkoutAction>()),
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFD5D5DB)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }

                    else -> {
                        Text(
                            "OPEN REDFORGE",
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ImageProvider(R.drawable.widget_secondary_action))
                                .padding(vertical = 12.dp, horizontal = 16.dp)
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
}

class RedForgeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RedForgeWidget()
}
