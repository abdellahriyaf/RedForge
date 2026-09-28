package com.redforge.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.redforge.app.R
import com.redforge.app.ui.components.ForgeButton
import com.redforge.app.ui.components.ForgeCard
import com.redforge.app.ui.theme.ForgeGold
import com.redforge.app.ui.theme.ForgeGradientEnd
import com.redforge.app.ui.theme.ForgeGradientStart
import com.redforge.app.ui.theme.ForgeRed
import com.redforge.app.ui.theme.ForgeRedDark
import com.redforge.app.util.NowPlayingController
import com.redforge.app.util.SpotifyLauncher
import com.redforge.app.viewmodel.HomeViewModel
import com.redforge.app.viewmodel.redForgeViewModel
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onOpenSplits: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenShare: () -> Unit
) {
    val vm: HomeViewModel = redForgeViewModel { app ->
        HomeViewModel(app.splitRepository, app.workoutRepository, app.settingsDataStore)
    }
    val state by vm.uiState.collectAsState()
    val milestone by vm.milestoneEvent.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("REDFORGE", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        state.activeSplit?.name ?: "No active split yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }

            Spacer(Modifier.height(20.dp))

            // Read the nullable value once so Kotlin can smart-cast it safely.
            val nextDay = state.nextDay

            WorkoutHeroCard(
                inProgress = state.inProgressSession != null,
                todayCompleted = state.todayCompleted,
                isRestDay = nextDay?.isRestDay == true && state.inProgressSession == null,
                scheduleNotStarted = state.scheduleNotStarted,
                todaySkipped = state.todaySkipped,
                sessionOrDayName = state.inProgressSession?.splitDayNameSnapshot
                    ?: if (state.scheduleNotStarted) "Starts later" else nextDay?.name,
                onStartOrResume = if (state.inProgressSession != null) onResumeWorkout else onStartWorkout,
                onResetWorkout = { showResetDialog = true },
                onSkipToday = { vm.skipTodayWorkout() },
                canStart = (nextDay != null && !nextDay.isRestDay && !state.todayCompleted && !state.todaySkipped) || state.inProgressSession != null
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StreakChip(
                    currentStreak = state.currentStreak,
                    longestStreak = state.longestStreak,
                    onClick = onOpenShare,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))
            MediaControlCard()

            Spacer(Modifier.height(12.dp))
            WeeklySummaryCard(
                workouts = state.weekWorkouts,
                sets = state.weekSets,
                volume = state.weekVolume,
                onClick = onOpenHistory
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                QuickActionCard(
                    icon = Icons.Filled.CalendarViewWeek,
                    label = "Splits",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSplits
                )
                QuickActionCard(
                    icon = Icons.Filled.InsertChartOutlined,
                    label = "Progress",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenProgress
                )
                QuickActionCard(
                    icon = Icons.Filled.History,
                    label = "History",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenHistory
                )
            }
        }

        milestone?.let { days ->
            MilestoneCelebrationOverlay(days = days, onDismiss = { vm.consumeMilestoneEvent() })
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset workout?") },
                text = { Text("This removes the unfinished workout and its logged sets. It will not affect completed workout history.") },
                confirmButton = {
                    TextButton(onClick = {
                        showResetDialog = false
                        vm.resetInProgressWorkout()
                    }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Keep workout") } }
            )
        }
    }
}

@Composable
private fun WorkoutHeroCard(
    inProgress: Boolean,
    todayCompleted: Boolean,
    isRestDay: Boolean,
    scheduleNotStarted: Boolean,
    todaySkipped: Boolean,
    sessionOrDayName: String?,
    onStartOrResume: () -> Unit,
    onResetWorkout: () -> Unit,
    onSkipToday: () -> Unit,
    canStart: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ForgeRedDark, ForgeRed)))
            .padding(24.dp)
    ) {
        Column {
            Text(
                when {
                    inProgress -> "IN PROGRESS"
                    todayCompleted -> "TODAY COMPLETE"
                    todaySkipped -> "TODAY SKIPPED"
                    isRestDay -> "REST DAY"
                    else -> "TODAY'S SESSION"
                },
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                sessionOrDayName ?: "Build a split to get started",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp),
                color = Color.White,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    inProgress -> "Your active workout is safely saved."
                    todayCompleted -> "You logged a session today. Nice work."
                    todaySkipped -> "Today's scheduled session was skipped. The plan continues tomorrow."
                    isRestDay -> "Recovery is part of the plan."
                    scheduleNotStarted -> "Your plan is waiting for its start date."
                    else -> "Show up. Log it. Beat it next time."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f)
            )
            Spacer(Modifier.height(18.dp))
            if (inProgress) {
                Button(
                    onClick = onStartOrResume,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ForgeRed),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Resume workout", fontWeight = FontWeight.Bold) }
                TextButton(
                    onClick = onResetWorkout,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Reset workout", color = Color.White.copy(alpha = 0.78f)) }
            } else {
                Button(
                    onClick = onStartOrResume,
                    enabled = canStart,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ForgeRed),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(
                        when {
                            todayCompleted -> "Completed"
                            todaySkipped -> "Skipped"
                            isRestDay -> "Recovery day"
                            scheduleNotStarted -> "Not started yet"
                            else -> "Start workout"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!todayCompleted && !todaySkipped && !isRestDay && !scheduleNotStarted) {
                    TextButton(
                        onClick = onSkipToday,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text("Skip today's workout", color = Color.White.copy(alpha = 0.72f)) }
                }
            }
        }
    }
}

@Composable
private fun StreakChip(currentStreak: Int, longestStreak: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ForgeCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_flame_streak), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("$currentStreak-day streak", style = MaterialTheme.typography.titleMedium)
                Text("Best: $longestStreak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClick) { Text("Share") }
        }
    }
}

@Composable
private fun WeeklySummaryCard(workouts: Int, sets: Int, volume: Int, onClick: () -> Unit) {
    ForgeCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("This week", style = MaterialTheme.typography.titleMedium)
                Text("$workouts workouts · $sets logged sets", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$volume", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("volume", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MilestoneCelebrationOverlay(days: Int, onDismiss: () -> Unit) {
    LaunchedEffect(days) {
        delay(3000)
        onDismiss()
    }
    val visibleState = remember(days) { MutableTransitionState(false).apply { targetState = true } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.7f, animationSpec = tween(320))
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(ForgeGradientStart, ForgeGradientEnd)))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(painterResource(R.drawable.ic_flame_streak), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("$days days strong!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(4.dp))
                Text(
                    "You've kept your streak alive for $days days. Keep forging.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MediaControlCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaState by NowPlayingController.state.collectAsState()

    LaunchedEffect(Unit) { NowPlayingController.init(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) NowPlayingController.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ForgeCard(modifier = Modifier.fillMaxWidth()) {
        when {
            !mediaState.accessGranted -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    MusicIconBadge()
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Media controls", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Enable one-time access to control your active music app from Home.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { NowPlayingController.openAccessSettings(context) }) { Text("Enable") }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { SpotifyLauncher.openSpotify(context) }) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Open Spotify")
                }
            }
            mediaState.title == null -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    MusicIconBadge()
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Music", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Nothing playing yet. Start your session soundtrack.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = { SpotifyLauncher.openSpotify(context) }) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Spotify")
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        MusicIconBadge()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mediaState.title.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                listOfNotNull(mediaState.artist, mediaState.appLabel).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { NowPlayingController.skipPrevious() }) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                        }
                        IconButton(onClick = { NowPlayingController.playPause() }) {
                            Icon(
                                if (mediaState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (mediaState.isPlaying) "Pause" else "Play"
                            )
                        }
                        IconButton(onClick = { NowPlayingController.skipNext() }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                        }
                        TextButton(onClick = { SpotifyLauncher.openSpotify(context) }) { Text("Spotify") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicIconBadge() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = ForgeGold)
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ForgeCard(modifier = modifier, onClick = onClick) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}
