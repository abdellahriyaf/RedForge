package com.redforge.app.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redforge.app.ui.components.EmberEmptyState
import com.redforge.app.ui.components.ForgeCard
import com.redforge.app.ui.components.ForgeSectionHeader
import com.redforge.app.viewmodel.HistorySessionUi
import com.redforge.app.viewmodel.HistoryViewModel
import com.redforge.app.viewmodel.redForgeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onOpenSession: (Long) -> Unit
) {
    val vm: HistoryViewModel = redForgeViewModel { app -> HistoryViewModel(app.workoutRepository) }
    val sessions by vm.sessions.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        ForgeSectionHeader(
            "History",
            "Every completed session, ready to review and correct"
        )
        Spacer(Modifier.height(8.dp))

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmberEmptyState(
                    title = "No completed workouts yet",
                    message = "Finish your first training session and RedForge will keep the record here."
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(sessions, key = { it.session.id }) { item ->
                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                        HistoryCard(item) { onOpenSession(item.session.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: HistorySessionUi,
    onClick: () -> Unit
) {
    ForgeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.session.splitDayNameSnapshot,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    SimpleDateFormat(
                        "EEE, MMM d · HH:mm",
                        Locale.getDefault()
                    ).format(Date(item.session.startedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = "Open workout"
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HistoryStat("Sets", item.setCount.toString())
            HistoryStat("Working", item.workingSetCount.toString())
            HistoryStat("Volume", item.volume.toString())
        }
    }
}

@Composable
private fun HistoryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
