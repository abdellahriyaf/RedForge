package com.redforge.app.ui.screens.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.animateItemPlacement
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redforge.app.ui.components.EmberEmptyState
import com.redforge.app.ui.components.ForgeCard
import com.redforge.app.ui.components.ForgeSectionHeader
import com.redforge.app.ui.theme.ForgeGreen
import com.redforge.app.ui.theme.ForgeRedBright
import com.redforge.app.viewmodel.ExerciseProgressSummary
import com.redforge.app.viewmodel.ProgressViewModel
import com.redforge.app.viewmodel.redForgeViewModel
import kotlin.math.roundToInt

@Composable
fun ProgressDashboardScreen(
    onOpenPhotos: () -> Unit,
    onOpenMeasurements: () -> Unit,
    onOpenExercise: (Long) -> Unit
) {
    val vm: ProgressViewModel = redForgeViewModel { app ->
        ProgressViewModel(app.exerciseRepository, app.workoutRepository)
    }
    val summaries by vm.summaries.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ForgeSectionHeader(
                "Progress",
                "Track strength, volume and physique changes over time"
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ForgeCard(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPhotos
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(Modifier.height(6.dp))
                    Text("Photo tracking", style = MaterialTheme.typography.titleMedium)
                }
                ForgeCard(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenMeasurements
                ) {
                    Icon(Icons.Filled.Straighten, contentDescription = null)
                    Spacer(Modifier.height(6.dp))
                    Text("Measurements", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        item {
            Text(
                "Lift progress",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (summaries.isEmpty()) {
            item {
                EmberEmptyState(
                    title = "Nothing to show yet",
                    message = "Log a few workouts and your strength trends will show up here."
                )
            }
        } else {
            items(summaries, key = { it.exercise.id }) { summary ->
                ExerciseProgressCard(summary) {
                    onOpenExercise(summary.exercise.id)
                }
            }
        }
    }
}

@Composable
private fun ExerciseProgressCard(
    summary: ExerciseProgressSummary,
    onClick: () -> Unit
 ) {
    val best1Rm by animateIntAsState(summary.bestEstimated1RM, label = "best_1rm")
    val totalVolume by animateIntAsState(summary.totalVolumeAllTime, label = "total_volume")

    ForgeCard(
        modifier = Modifier.fillMaxWidth().animateItemPlacement(),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(summary.exercise.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${summary.sessionCount} sessions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "View trend")
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Best e1RM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("$best1Rm", style = MaterialTheme.typography.headlineMedium)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Total volume",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("$totalVolume", style = MaterialTheme.typography.headlineMedium)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Volume trend",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val positive = summary.volumeChangePercent >= 0
                    Icon(
                        if (positive) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = null,
                        tint = if (positive) ForgeGreen else ForgeRedBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "${if (positive) "+" else ""}${(summary.volumeChangePercent * 10).roundToInt() / 10.0}%",
                        color = if (positive) ForgeGreen else ForgeRedBright,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
