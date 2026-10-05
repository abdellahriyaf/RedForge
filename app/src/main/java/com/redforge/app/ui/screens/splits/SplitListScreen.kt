@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.redforge.app.ui.screens.splits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redforge.app.data.local.entities.Split
import com.redforge.app.domain.schedule.SplitTemplate
import com.redforge.app.ui.components.EmberEmptyState
import com.redforge.app.ui.components.ForgeCard
import com.redforge.app.ui.components.ForgeSectionHeader
import com.redforge.app.ui.theme.ForgeGreen
import com.redforge.app.viewmodel.SplitListViewModel
import com.redforge.app.viewmodel.SplitActivationPrompt
import com.redforge.app.viewmodel.redForgeViewModel

/** Lists every split the user has built; tap to open the editor, tap the radio to make it active. */
@Composable
fun SplitListScreen(onOpenSplit: (Long) -> Unit) {
    val vm: SplitListViewModel = redForgeViewModel { app -> SplitListViewModel(app.splitRepository, app.workoutRepository, app.applicationContext, app.settingsDataStore) }
    val splits by vm.splits.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingSplit by remember { mutableStateOf<Split?>(null) }
    var deletingSplit by remember { mutableStateOf<Split?>(null) }
    val activationPrompt by vm.activationPrompt.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New split")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp)) {
            ForgeSectionHeader("Your Splits", "Build, edit, and switch between training splits")
            Spacer(Modifier.height(12.dp))

            if (splits.isEmpty()) {
                EmberEmptyState(
                    title = "No splits yet",
                    message = "Tap + to create your first one — pick a template like Push/Pull/Legs, or start blank."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(splits, key = { it.id }) { split ->
                        SplitRow(
                            split = split,
                            onOpen = { onOpenSplit(split.id) },
                            onSetActive = { vm.setActive(split) },
                            onRename = { renamingSplit = split },
                            onDelete = { deletingSplit = split }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateSplitDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, template ->
                showCreateDialog = false
                vm.createSplit(name, template) { newId -> onOpenSplit(newId) }
            }
        )
    }

    renamingSplit?.let { split ->
        RenameSplitDialog(
            currentName = split.name,
            onDismiss = { renamingSplit = null },
            onConfirm = { newName ->
                vm.renameSplit(split, newName)
                renamingSplit = null
            }
        )
    }


    activationPrompt?.let { prompt ->
        var showDatePicker by remember(prompt.split.id) { mutableStateOf(false) }
        var selectedDate by remember(prompt.split.id) { mutableStateOf<Long?>(null) }

        AlertDialog(
            onDismissRequest = { vm.dismissActivationPrompt() },
            title = { Text("Start " + prompt.split.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose when this plan should begin. You can start today or pick a future date.")
                    OutlinedButton(
                        onClick = { vm.confirmActivation(prompt.split, System.currentTimeMillis()) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Start today") }
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Choose start date") }
                    selectedDate?.let { millis ->
                        Text(
                            "Selected start: " + java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                                .format(java.util.Date(millis)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedDate != null,
                    onClick = {
                        selectedDate?.let { vm.confirmActivation(prompt.split, it) }
                    }
                ) { Text("Use selected date") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissActivationPrompt() }) { Text("Cancel") }
            }
        )

        if (showDatePicker) {
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        selectedDate = pickerState.selectedDateMillis
                        showDatePicker = false
                    }) { Text("Select") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
            ) {
                DatePicker(state = pickerState)
            }
        }
    }

    deletingSplit?.let { split ->
        DeleteSplitDialog(
            split = split,
            onDismiss = { deletingSplit = null },
            onConfirm = {
                vm.deleteSplit(split)
                deletingSplit = null
            }
        )
    }
}

@Composable
private fun SplitRow(
    split: Split,
    onOpen: () -> Unit,
    onSetActive: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ForgeCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(split.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${split.daysPerCycle} day cycle" + if (split.isDeloadCycle) " · Deload" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSetActive) {
                Icon(
                    if (split.isActive) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (split.isActive) "Deselect active split" else "Set active",
                    tint = if (split.isActive) ForgeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateSplitDialog(onDismiss: () -> Unit, onCreate: (String, SplitTemplate) -> Unit) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(SplitTemplate.CUSTOM) }

    val isCustom = template == SplitTemplate.CUSTOM
    val resolvedName = if (isCustom) name.trim() else template.label

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New split") },
        text = {
            Column {
                Text(
                    "Choose a training split to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Text("Start from a template", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))

                SplitTemplate.values().forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = template == option,
                            onClick = { template = option }
                        )
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            if (option.days.isNotEmpty()) {
                                Text(
                                    option.days.joinToString(" · ") { it.name },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (isCustom) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Split name") },
                        singleLine = true,
                        placeholder = { Text("e.g. My 4-day split") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "RedForge will name this split “$resolvedName”. You can rename it later.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(resolvedName, template) },
                enabled = resolvedName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RenameSplitDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename split") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Split name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DeleteSplitDialog(split: Split, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${split.name}\"?") },
        text = { Text("This deletes the split and its days/exercise assignments. Your logged workout history is not affected. This can't be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
