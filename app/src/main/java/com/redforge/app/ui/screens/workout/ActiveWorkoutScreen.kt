@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.redforge.app.ui.screens.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.redforge.app.data.datastore.WeightUnit
import com.redforge.app.data.local.entities.SetEntry
import com.redforge.app.domain.formulas.TrainingCalculators
import com.redforge.app.domain.formulas.WarmupSet
import com.redforge.app.R
import com.redforge.app.ui.components.EmberEmptyState
import com.redforge.app.ui.components.ForgeButton
import com.redforge.app.ui.components.ForgeCard
import com.redforge.app.ui.theme.ForgeGold
import com.redforge.app.ui.theme.ForgeHeroGradient
import com.redforge.app.ui.theme.ForgeGreen
import com.redforge.app.util.RestTimerController
import com.redforge.app.viewmodel.ActiveWorkoutViewModel
import com.redforge.app.viewmodel.PrCelebration
import com.redforge.app.viewmodel.WorkoutExerciseBlock
import com.redforge.app.viewmodel.redForgeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ActiveWorkoutScreen(onFinished: () -> Unit, onBack: () -> Unit) {
    val vm: ActiveWorkoutViewModel = redForgeViewModel { app ->
        ActiveWorkoutViewModel(app.splitRepository, app.workoutRepository, app.exerciseRepository, app.settingsDataStore)
    }
    val state by vm.uiState.collectAsState()
    val prEvent by vm.prEvent.collectAsState()
    val restTrigger by vm.restTriggerSeconds.collectAsState()
    val context = LocalContext.current
    val timerController = remember { RestTimerController(context) }
    val timerState by timerController.state.collectAsState()
    var showEmptyFinishDialog by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(state.finished) {
        if (state.finished) {
            onFinished()
        }
    }

    LaunchedEffect(restTrigger) {
        val seconds = restTrigger ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        timerController.start(seconds)
        vm.consumeRestTrigger()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.session?.splitDayNameSnapshot ?: "Workout") },
                navigationIcon = {
                    IconButton(onClick = {
                        timerController.stop()
                        onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (vm.hasWorkingSets()) {
                                timerController.stop()
                                vm.finishWorkout()
                            } else {
                                showEmptyFinishDialog = true
                            }
                        },
                        enabled = !state.isFinishing
                    ) {
                        Text(if (state.isFinishing) "Saving…" else "Finish")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.isDeloadCycle) {
                    DeloadBanner()
                }
                if (timerState.isRunning || timerState.isPaused) {
                    RestTimerBar(timerState.totalSeconds, timerState.secondsRemaining, timerState.isPaused, timerController)
                }

                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        EmberEmptyState(title = "Can't start yet", message = state.error!!)
                    }
                    state.blocks.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        EmberEmptyState(title = "No exercises today", message = "This day has no exercises assigned yet.")
                    }
                    else -> ExercisePager(state.blocks, state.weightUnit, vm)
                }
            }

            prEvent?.let { celebration ->
                PrCelebrationOverlay(celebration, onDismiss = { vm.consumePrEvent() })
            }
        }
    }

    if (showEmptyFinishDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyFinishDialog = false },
            title = { Text("Discard empty session?") },
            text = { Text("No working sets are logged yet. Discarding this session keeps it out of your history, streak, and weekly totals.") },
            confirmButton = {
                TextButton(onClick = {
                    showEmptyFinishDialog = false
                    timerController.stop()
                    vm.discardWorkout()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showEmptyFinishDialog = false }) { Text("Keep training") } }
        )
    }
}

@Composable
private fun ExercisePager(
    blocks: List<WorkoutExerciseBlock>,
    weightUnit: WeightUnit,
    vm: ActiveWorkoutViewModel
) {
    val pagerState = rememberPagerState(pageCount = { blocks.size })
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(blocks.size) {
        if (blocks.isNotEmpty() && pagerState.currentPage >= blocks.size) {
            pagerState.scrollToPage(blocks.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                "Exercise ${pagerState.currentPage + 1} of ${blocks.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                blocks.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .weight(1f)
                            .clip(CircleShape)
                            .background(
                                if (i <= pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val block = blocks.getOrNull(page)
            if (block == null) {
                Box(Modifier.fillMaxSize())
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    ExerciseBlockCard(
                        block = block,
                        weightUnit = weightUnit,
                        estimated1RM = vm.estimatedOneRepMax(block.loggedSets),
                        onLogSet = { weight, reps, warmup, rpe ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.logSet(block.exercise.id, weight, reps, warmup, rpe)
                        },
                        onDeleteSet = { vm.deleteSet(it) }
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage - 1).coerceAtLeast(0)
                        )
                    }
                },
                enabled = pagerState.currentPage > 0
            ) { Text("← Previous") }

            TextButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(blocks.lastIndex)
                        )
                    }
                },
                enabled = pagerState.currentPage < blocks.lastIndex
            ) { Text("Next →") }
        }
    }
}

@Composable
private fun DeloadBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = ForgeGold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Deload — today's target volume is reduced by 40%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun PrCelebrationOverlay(celebration: PrCelebration, onDismiss: () -> Unit) {
    LaunchedEffect(celebration) {
        delay(2600)
        onDismiss()
    }
    val visibleState = remember(celebration) { MutableTransitionState(false).apply { targetState = true } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.7f, animationSpec = tween(300))
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(painterResource(R.drawable.ic_trophy_pr), contentDescription = null, tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("New PR!", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(celebration.exerciseName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Est. 1RM ${celebration.previousBest} → ${celebration.newEstimated1RM}",
                    style = MaterialTheme.typography.titleLarge,
                    color = ForgeGold,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RestTimerBar(totalSeconds: Int, secondsRemaining: Int, isPaused: Boolean, controller: RestTimerController) {
    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60
    val urgent = secondsRemaining in 1..5 && !isPaused
    val pulse by animateFloatAsState(
        targetValue = if (urgent) 1.15f else 1f,
        animationSpec = tween(400),
        label = "timer_pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
             .background(ForgeHeroGradient)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format(Locale.US, "%d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.graphicsLayer(scaleX = pulse, scaleY = pulse)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isPaused) "Paused" else "Rest",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                )
            }
            LinearProgressIndicator(
                progress = { if (totalSeconds > 0) secondsRemaining.toFloat() / totalSeconds else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
            )
        }
        TextButton(onClick = { if (isPaused) controller.resume() else controller.pause() }) {
            Text(if (isPaused) "Resume" else "Pause", color = MaterialTheme.colorScheme.onPrimary)
        }
        TextButton(onClick = { controller.addFifteenSeconds() }) { Text("+15s", color = MaterialTheme.colorScheme.onPrimary) }
        TextButton(onClick = { controller.skip() }) { Text("Skip", color = MaterialTheme.colorScheme.onPrimary) }
    }
}

@Composable
private fun ExerciseBlockCard(
    block: WorkoutExerciseBlock,
    weightUnit: WeightUnit,
    estimated1RM: Int,
    onLogSet: (Double, Int, Boolean, Float?) -> Unit,
    onDeleteSet: (SetEntry) -> Unit
) {
    var weightInput by remember(block.exercise.id) { mutableStateOf("") }
    var repsInput by remember(block.exercise.id) { mutableStateOf("") }
    var warmup by remember(block.exercise.id) { mutableStateOf(false) }
    var rpe by remember(block.exercise.id) { mutableStateOf<Float?>(null) }
    var showRpeDialog by remember(block.exercise.id) { mutableStateOf(false) }
    var showWarmupRamp by remember(block.exercise.id) { mutableStateOf(false) }
    var showPlates by remember(block.exercise.id) { mutableStateOf(false) }

    ForgeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(block.exercise.name, style = MaterialTheme.typography.titleMedium)
                    if (block.dayExercise.supersetGroup != null) {
                        Spacer(Modifier.width(6.dp))
                        AssistChip(onClick = {}, label = { Text("Superset") })
                    }
                }
                Text(
                    "Target: ${block.targetSetsForToday} x ${block.dayExercise.targetRepsLow}-${block.dayExercise.targetRepsHigh} · ${block.restSecondsForToday}s rest",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (estimated1RM > 0) {
                AssistChip(onClick = {}, label = { Text("~1RM $estimated1RM ${unitLabel(weightUnit)}") }, leadingIcon = {
                    Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = ForgeGold)
                })
            }
        }

        block.lastPreviousSet?.let { prev ->
            Text(
                "Last time: ${formatWeight(prev.weight)} ${unitLabel(weightUnit)} x ${prev.reps}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        block.loggedSets.forEach { set ->
            key(set.id) {
                val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Icon(
                            when {
                                set.isWarmup -> Icons.Filled.Whatshot
                                set.isPersonalRecord -> Icons.Filled.EmojiEvents
                                else -> Icons.Filled.CheckCircle
                            },
                            contentDescription = null,
                            tint = when {
                                set.isWarmup -> ForgeGold
                                set.isPersonalRecord -> ForgeGold
                                else -> ForgeGreen
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Set ${set.setIndex}: ${formatWeight(set.weight)} ${unitLabel(weightUnit)} x ${set.reps}" + if (set.isPersonalRecord) "  •  PR" else "",
                            modifier = Modifier.weight(1f),
                            fontWeight = if (set.isPersonalRecord) FontWeight.Bold else FontWeight.Normal
                        )
                        IconButton(onClick = { onDeleteSet(set) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove set", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { showWarmupRamp = !showWarmupRamp }) { Text(if (showWarmupRamp) "Hide warm-up" else "Warm-up ramp") }
            TextButton(onClick = { showPlates = true }, enabled = weightInput.toDoubleOrNull() != null) { Text("Plates") }
        }

        if (showWarmupRamp) {
            val workingWeight = weightInput.toDoubleOrNull() ?: block.lastPreviousSet?.weight ?: 0.0
            WarmupRampSection(
                workingWeight = workingWeight,
                weightUnit = weightUnit,
                onFill = { w, r ->
                    weightInput = formatWeight(w)
                    repsInput = r.toString()
                    warmup = true
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showRpeDialog = true }) {
                Text(if (rpe == null) "RPE" else "RPE ${rpe!!.toInt()}")
            }
            Text("Optional", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = weightInput,
                onValueChange = { weightInput = it },
                label = { Text(unitLabel(weightUnit)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).heightIn(min = 60.dp)
            )
            OutlinedTextField(
                value = repsInput,
                onValueChange = { repsInput = it },
                label = { Text("Reps") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).heightIn(min = 60.dp)
            )
            FilterChip(selected = warmup, onClick = { warmup = !warmup }, label = { Text("W") })
        }
        Spacer(Modifier.height(8.dp))
        ForgeButton(
            text = "Log set",
            enabled = weightInput.toDoubleOrNull() != null && repsInput.toIntOrNull() != null,
            onClick = {
                val w = weightInput.toDoubleOrNull() ?: return@ForgeButton
                val r = repsInput.toIntOrNull() ?: return@ForgeButton
                onLogSet(w, r, warmup, rpe)
                weightInput = ""
                repsInput = ""
                warmup = false
                rpe = null
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showRpeDialog) {
        AlertDialog(
            onDismissRequest = { showRpeDialog = false },
            title = { Text("Rate of perceived exertion") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Optional. 10 = maximal effort, 6–7 = comfortably hard.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..5).forEach { value ->
                            TextButton(onClick = { rpe = value.toFloat(); showRpeDialog = false }) { Text(value.toString()) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (6..10).forEach { value ->
                            TextButton(onClick = { rpe = value.toFloat(); showRpeDialog = false }) { Text(value.toString()) }
                        }
                    }
                    TextButton(onClick = { rpe = null; showRpeDialog = false }) { Text("Clear") }
                }
            },
            confirmButton = { TextButton(onClick = { showRpeDialog = false }) { Text("Close") } }
        )
    }

    if (showPlates) {
        val target = weightInput.toDoubleOrNull() ?: 0.0
        PlateCalculatorDialog(targetWeight = target, weightUnit = weightUnit, onDismiss = { showPlates = false })
    }
}

@Composable
private fun WarmupRampSection(workingWeight: Double, weightUnit: WeightUnit, onFill: (Double, Int) -> Unit) {
    val ramp = remember(workingWeight) { TrainingCalculators.warmupRamp(workingWeight) }
    if (workingWeight <= 0.0 || ramp.isEmpty()) {
        Text(
            "Enter a working weight below first, or log a set, to see a suggested ramp.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        return
    }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        ramp.forEach { step: WarmupSet ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Text(
                    "${step.percentOfWorking}% · ${formatWeight(step.weight)} ${unitLabel(weightUnit)} x ${step.reps}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { onFill(step.weight, step.reps) }) { Text("Fill") }
            }
        }
    }
}

@Composable
private fun PlateCalculatorDialog(targetWeight: Double, weightUnit: WeightUnit, onDismiss: () -> Unit) {
    val useKg = weightUnit == WeightUnit.KG
    val barWeight = if (useKg) 20.0 else 45.0
    val (plates, residual) = remember(targetWeight, weightUnit) { TrainingCalculators.platesPerSide(targetWeight, barWeight, useKg) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plates per side") },
        text = {
            Column {
                Text("Bar: ${formatWeight(barWeight)} ${unitLabel(weightUnit)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (plates.isEmpty()) {
                    Text("Target is at or below the bar — no plates needed.")
                } else {
                    Text(plates.joinToString("  +  ") { formatWeight(it) }, style = MaterialTheme.typography.titleMedium)
                    if (residual > 0.01) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Can't hit this exactly with standard plates — off by ${formatWeight(residual * 2)} ${unitLabel(weightUnit)} total.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun unitLabel(unit: WeightUnit) = if (unit == WeightUnit.KG) "kg" else "lb"
private fun formatWeight(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
