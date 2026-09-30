@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.redforge.app.ui.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.redforge.app.ui.components.ForgeCard
import com.redforge.app.viewmodel.ExerciseProgressDetailViewModel
import com.redforge.app.viewmodel.ExerciseTrendPoint
import com.redforge.app.viewmodel.redForgeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TrendMode { E1RM, VOLUME }

@Composable
fun ExerciseProgressDetailScreen(
    exerciseId: Long,
    onBack: () -> Unit
) {
    val vm: ExerciseProgressDetailViewModel = redForgeViewModel { app ->
        ExerciseProgressDetailViewModel(
            app.exerciseRepository,
            app.workoutRepository,
            exerciseId
        )
    }
    val state by vm.uiState.collectAsState()
    var mode by remember { mutableStateOf(TrendMode.E1RM) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.exercise?.name ?: "Progress") },
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
            ) { Text(state.error ?: "Couldn't load progress") }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Best e1RM", state.bestEstimated1RM.toString(), Modifier.weight(1f))
                    MetricCard("Total volume", state.allTimeVolume.toString(), Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == TrendMode.E1RM,
                        onClick = { mode = TrendMode.E1RM },
                        label = { Text("e1RM") }
                    )
                    FilterChip(
                        selected = mode == TrendMode.VOLUME,
                        onClick = { mode = TrendMode.VOLUME },
                        label = { Text("Volume") }
                    )
                }

                ForgeCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (mode == TrendMode.E1RM) "Estimated 1RM trend" else "Session volume trend",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    if (state.points.size < 2) {
                        Text(
                            "Log at least two completed sessions to see a trend line.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        TrendChart(
                            points = state.points,
                            mode = mode,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )
                    }
                }

                ForgeCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Recent sessions", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    state.points.takeLast(8).reversed().forEach { point ->
                        TrendRow(point, mode)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    ForgeCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun TrendChart(
    points: List<ExerciseTrendPoint>,
    mode: TrendMode,
    modifier: Modifier = Modifier
) {
    val values = points.map { if (mode == TrendMode.E1RM) it.estimated1RM.toFloat() else it.volume.toFloat() }
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 1f
    val range = (max - min).takeIf { it > 0f } ?: 1f
    val primary = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        val left = 18f
        val top = 16f
        val right = size.width - 8f
        val bottom = size.height - 22f
        val width = right - left
        val height = bottom - top

        drawLine(axis, Offset(left, bottom), Offset(right, bottom), strokeWidth = 2f)
        drawLine(axis, Offset(left, top), Offset(left, bottom), strokeWidth = 2f)

        points.zipWithNext().forEachIndexed { index, pair ->
            val x1 = left + width * index / (points.lastIndex.coerceAtLeast(1))
            val x2 = left + width * (index + 1) / (points.lastIndex.coerceAtLeast(1))
            val y1 = bottom - ((values[index] - min) / range) * height
            val y2 = bottom - ((values[index + 1] - min) / range) * height
            drawLine(
                primary,
                Offset(x1, y1),
                Offset(x2, y2),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }

        values.forEachIndexed { index, value ->
            val x = left + width * index / (points.lastIndex.coerceAtLeast(1))
            val y = bottom - ((value - min) / range) * height
            drawCircle(primary, radius = 5f, center = Offset(x, y))
        }
    }
}

@Composable
private fun TrendRow(point: ExerciseTrendPoint, mode: TrendMode) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(point.date)),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (mode == TrendMode.E1RM) "e1RM ${point.estimated1RM}" else "Volume ${point.volume}",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
