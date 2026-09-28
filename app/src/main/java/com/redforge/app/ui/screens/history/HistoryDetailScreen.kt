@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.redforge.app.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redforge.app.data.local.entities.SetEntry
import com.redforge.app.ui.components.ForgeCard
import com.redforge.app.ui.components.ForgeSectionHeader
import com.redforge.app.viewmodel.HistoryDetailViewModel
import com.redforge.app.viewmodel.redForgeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryDetailScreen(
    sessionId: Long,
    onBack: () -> Unit
) {
    val vm: HistoryDetailViewModel = redForgeViewModel { app ->
        HistoryDetailViewModel(
            app.workoutRepository,
            app.exerciseRepository,
            sessionId
        )
    }
    val state by vm.uiState.collectAsState()

    var editSet by remember { mutableStateOf<SetEntry?>(null) }
    var deleteSet by remember { mutableStateOf<SetEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.session?.splitDayNameSnapshot ?: "Workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text(state.error ?: "Couldn't load workout") }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.session?.let { session ->
                    ForgeSectionHeader(
                        "${state.exercises.size} exercises · ${state.totalVolume} volume",
                        SimpleDateFormat(
                            "EEE, MMM d · HH:mm",
                            Locale.getDefault()
                        ).format(Date(session.startedAt))
                    )
                }

                state.exercises.forEach { exercise ->
                    ForgeCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                        Text(
                            exercise.name,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))

                        exercise.sets.forEach { set ->
                            HistorySetRow(
                                set = set,
                                onEdit = { editSet = set },
                                onDelete = { deleteSet = set }
                            )
                        }
                    }
                }
            }
        }
    }

    editSet?.let { set ->
        EditSetDialog(
            set = set,
            onDismiss = { editSet = null },
            onSave = { weight, reps, rpe ->
                vm.updateSet(set, weight, reps, rpe)
                editSet = null
            }
        )
    }

    deleteSet?.let { set ->
        AlertDialog(
            onDismissRequest = { deleteSet = null },
            title = { Text("Delete this set?") },
            text = { Text("This removes the set from the saved workout history. The remaining sets will be renumbered.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSet(set)
                    deleteSet = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSet = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HistorySetRow(
    set: SetEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Set ${set.setIndex}  ·  ${formatWeight(set.weight)} × ${set.reps}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                buildString {
                    if (set.isWarmup) append("Warm-up") else append("Working set")
                    set.rpe?.let { append(" · RPE ${trimRpe(it)}") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit set")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete set")
        }
    }
}

@Composable
private fun EditSetDialog(
    set: SetEntry,
    onDismiss: () -> Unit,
    onSave: (Double, Int, Float?) -> Unit
) {
    var weight by remember(set.id) { mutableStateOf(set.weight.toString()) }
    var reps by remember(set.id) { mutableStateOf(set.reps.toString()) }
    var rpe by remember(set.id) { mutableStateOf(set.rpe?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Set ${set.setIndex}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = { Text("Reps") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = rpe,
                    onValueChange = { rpe = it },
                    label = { Text("RPE (optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedWeight = weight.toDoubleOrNull() ?: return@TextButton
                val parsedReps = reps.toIntOrNull() ?: return@TextButton
                val parsedRpe = rpe.toFloatOrNull()?.coerceIn(1f, 10f)
                onSave(parsedWeight, parsedReps, parsedRpe)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatWeight(weight: Double): String =
    if (weight % 1.0 == 0.0) weight.toInt().toString() else String.format(Locale.getDefault(), "%.1f", weight)

private fun trimRpe(rpe: Float): String =
    if (rpe % 1f == 0f) rpe.toInt().toString() else String.format(Locale.getDefault(), "%.1f", rpe)
