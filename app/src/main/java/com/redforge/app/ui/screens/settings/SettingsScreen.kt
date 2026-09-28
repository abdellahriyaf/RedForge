@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.redforge.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.redforge.app.data.datastore.WeightUnit
import com.redforge.app.ui.components.ForgeCard
import com.redforge.app.ui.components.ForgeSectionHeader
import com.redforge.app.util.DataBackupUtil
import com.redforge.app.util.NowPlayingController
import com.redforge.app.viewmodel.SettingsViewModel
import com.redforge.app.viewmodel.redForgeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = redForgeViewModel { app -> SettingsViewModel(app.settingsDataStore, app.workoutRepository) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var importError by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var timerExpanded by remember { mutableStateOf(true) }
    var backupExpanded by remember { mutableStateOf(false) }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Some document providers grant temporary access only.
        }

        scope.launch {
            isImporting = true
            val valid = withContext(Dispatchers.IO) {
                DataBackupUtil.isValidBackup(context, uri)
            }
            isImporting = false

            if (valid) {
                pendingImportUri = uri
            } else {
                importMessage = "This file isn't a valid RedForge backup. Choose a .zip exported by RedForge."
                importError = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        ForgeSectionHeader("Settings")
        Spacer(Modifier.height(12.dp))

        ForgeCard(modifier = Modifier.fillMaxWidth()) {
            Text("Weight unit", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.weightUnit == WeightUnit.KG, onClick = { vm.setWeightUnit(WeightUnit.KG) }, label = { Text("Kilograms (kg)") })
                FilterChip(selected = state.weightUnit == WeightUnit.LB, onClick = { vm.setWeightUnit(WeightUnit.LB) }, label = { Text("Pounds (lb)") })
            }
        }

        Spacer(Modifier.height(12.dp))
        ForgeCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Timer", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Rest duration, sound, and vibration",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { timerExpanded = !timerExpanded }) {
                    Text(if (timerExpanded) "Collapse" else "Expand")
                }
            }
            AnimatedVisibility(visible = timerExpanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text("Default rest time", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = state.defaultRestSeconds.toFloat(),
                        onValueChange = { vm.setDefaultRest(it.toInt()) },
                        valueRange = 15f..300f,
                        steps = 18
                    )
                    Text("${state.defaultRestSeconds} seconds", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Used when an exercise has no custom rest prescription.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchRow("Timer sound", state.timerSoundEnabled) { vm.setTimerSound(it) }
                    SwitchRow("Timer vibration", state.timerVibrationEnabled) { vm.setTimerVibration(it) }
                    Text(
                        "Timer completion uses a short notification tone and a tactile vibration pattern.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ForgeCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Media controls", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Control your active music app from the Home dashboard. Spotify stays a one-tap action on Home.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { NowPlayingController.openAccessSettings(context) }) {
                Text("Configure media access")
            }
        }

        Spacer(Modifier.height(12.dp))
        ForgeCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Export or restore your local RedForge data",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { backupExpanded = !backupExpanded }) {
                    Text(if (backupExpanded) "Collapse" else "Expand")
                }
            }
            AnimatedVisibility(visible = backupExpanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "RedForge has no automatic cloud sync. Export a backup whenever you want a safety copy, or before switching phones.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    OutlinedButton(
                        onClick = {
                            isExporting = true
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) {
                                    DataBackupUtil.exportBackup(context)
                                }
                                isExporting = false
                                if (uri != null) {
                                    DataBackupUtil.shareBackup(context, uri)
                                }
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isExporting) "Exporting…" else "Export backup")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Create a local .zip backup",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column {
                    OutlinedButton(
                        onClick = {
                            importPicker.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        enabled = !isImporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isImporting) "Checking…" else "Restore backup")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Replace current data from a RedForge .zip",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "RedForge stores workout data locally and never uploads it automatically. Backup export is always an explicit user action.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Restore this backup?") },
            text = { Text("This replaces everything currently in RedForge — splits, workout history, photos, and settings — with the contents of this backup. The restore is validated before live data is replaced, and the app will restart to finish.") },
            confirmButton = {
                TextButton(onClick = {
                    isImporting = true
                    scope.launch {
                        val success = withContext(Dispatchers.IO) { DataBackupUtil.importBackup(context, uri) }
                        pendingImportUri = null
                        if (success) {
                            DataBackupUtil.restartApp(context)
                        } else {
                            isImporting = false
                            importError = true
                        }
                    }
                }) { Text("Restore & restart", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") } }
        )
    }

    if (importError) {
        AlertDialog(
            onDismissRequest = { importError = false },
            title = { Text("Couldn't restore that backup") },
            text = { Text(importMessage ?: "The file is not a valid RedForge backup, is incomplete, is too large, or could not be safely restored.") },
            confirmButton = { TextButton(onClick = { importError = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
