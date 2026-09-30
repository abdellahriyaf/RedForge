package com.redforge.app.ui.screens.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redforge.app.data.local.entities.BodyMeasurement
import com.redforge.app.ui.components.EmberEmptyState
import com.redforge.app.ui.components.ForgeCard
import com.redforge.app.viewmodel.BodyMeasurementViewModel
import com.redforge.app.viewmodel.redForgeViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BodyMeasurementScreen() {
    val vm: BodyMeasurementViewModel = redForgeViewModel { app -> BodyMeasurementViewModel(app.progressRepository) }
    val measurements by vm.measurements.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "Add checkpoint") }
        }
    ) { padding ->
        if (measurements.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                EmberEmptyState(title = "No measurements yet", message = "Log your first checkpoint to start tracking trends over time.")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(padding)) {
                items(measurements, key = { it.id }) { m -> MeasurementCard(m) { vm.delete(m) } }
            }
        }
    }

    if (showAdd) {
        AddMeasurementDialog(onDismiss = { showAdd = false }, onSave = { vm.save(it); showAdd = false })
    }
}

@Composable
private fun MeasurementCard(m: BodyMeasurement, onDelete: () -> Unit) {
    val dateText = remember(m.date) { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(m.date)) }
    ForgeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dateText, style = MaterialTheme.typography.titleMedium)
                Text("${m.bodyWeight} kg" + (m.bodyFatPercent?.let { " · $it% BF" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}

@Composable
private fun AddMeasurementDialog(onDismiss: () -> Unit, onSave: (BodyMeasurement) -> Unit) {
    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var arm by remember { mutableStateOf("") }
    var thigh by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a checkpoint") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Body weight *") }, singleLine = true)
                OutlinedTextField(value = bodyFat, onValueChange = { bodyFat = it }, label = { Text("Body fat % (optional)") }, singleLine = true)
                OutlinedTextField(value = chest, onValueChange = { chest = it }, label = { Text("Chest cm (optional)") }, singleLine = true)
                OutlinedTextField(value = waist, onValueChange = { waist = it }, label = { Text("Waist cm (optional)") }, singleLine = true)
                OutlinedTextField(value = arm, onValueChange = { arm = it }, label = { Text("Arm cm (optional)") }, singleLine = true)
                OutlinedTextField(value = thigh, onValueChange = { thigh = it }, label = { Text("Thigh cm (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = weight.toDoubleOrNull() ?: return@TextButton
                    onSave(
                        BodyMeasurement(
                            bodyWeight = w,
                            bodyFatPercent = bodyFat.toFloatOrNull(),
                            chestCm = chest.toFloatOrNull(),
                            waistCm = waist.toFloatOrNull(),
                            armCm = arm.toFloatOrNull(),
                            thighCm = thigh.toFloatOrNull()
                        )
                    )
                },
                enabled = weight.toDoubleOrNull() != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
