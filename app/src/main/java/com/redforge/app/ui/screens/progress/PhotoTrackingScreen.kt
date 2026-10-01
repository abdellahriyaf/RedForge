package com.redforge.app.ui.screens.progress

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.redforge.app.data.local.entities.PhotoAngle
import com.redforge.app.data.local.entities.ProgressPhoto
import com.redforge.app.viewmodel.PhotoTrackingViewModel
import com.redforge.app.viewmodel.redForgeViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoTrackingScreen() {
    val context = LocalContext.current
    val vm: PhotoTrackingViewModel = redForgeViewModel { app ->
        PhotoTrackingViewModel(
            app.progressRepository,
            context.applicationContext
        )
    }
    val photos by vm.photos.collectAsState()

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var showCameraPermissionDialog by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = cameraUri
        val file = cameraFile
        cameraUri = null
        cameraFile = null

        if (success && uri != null) {
            pendingUri = uri
            cameraFile = file
        } else {
            file?.delete()
        }
    }

    val requestCameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture(
                context = context,
                onCaptureCreated = { file, uri ->
                    cameraFile = file
                    cameraUri = uri
                    takePicture.launch(uri)
                }
            )
        } else {
            showCameraPermissionDialog = true
        }
    }

    fun startCamera() {
        showSourceDialog = false

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            launchCameraCapture(
                context = context,
                onCaptureCreated = { file, uri ->
                    cameraFile = file
                    cameraUri = uri
                    takePicture.launch(uri)
                }
            )
        } else {
            requestCameraPermission.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Scaffold(
            floatingActionButton = {
            FloatingActionButton(
                onClick = { showSourceDialog = true }
            ) {
                Icon(
                    Icons.Filled.AddAPhoto,
                    contentDescription = "Add progress photo"
                )
            }
        }
    ) { padding ->
        if (photos.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                com.redforge.app.ui.components.EmberEmptyState(
                    title = "No progress photos yet",
                    message = "Take a photo with your camera or choose one from your gallery."
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    photos,
                    key = { it.id }
                ) { photo ->
                    PhotoGridItem(photo) {
                        vm.deletePhoto(photo)
                    }
                }
            }
        }
        }
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Add progress photo") },
            text = { Text("Choose how you want to add your physique check-in.") },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    TextButton(
                        onClick = {
                            startCamera()
                        }
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Take photo")
                    }
                    TextButton(
                        onClick = {
                            showSourceDialog = false
                            pickImage.launch("image/*")
                        }
                    ) {
                        Icon(
                            Icons.Filled.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Choose from gallery")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSourceDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCameraPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDialog = false },
            title = { Text("Camera access needed") },
            text = {
                Text(
                    "RedForge needs camera access only when you choose to take a progress photo inside the app. Gallery photos do not require camera access."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCameraPermissionDialog = false
                        requestCameraPermission.launch(Manifest.permission.CAMERA)
                    }
                ) {
                    Text("Allow camera")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCameraPermissionDialog = false }
                ) {
                    Text("Not now")
                }
            }
        )
    }

    pendingUri?.let { uri ->
        AngleDialog(
            onDismiss = {
                if (cameraFile != null) {
                    cameraFile?.delete()
                    cameraFile = null
                    cameraUri = null
                }
                pendingUri = null
            },
            onConfirm = { angle, note ->
                val temporaryCameraFile = cameraFile
                vm.savePhoto(uri, angle, note).invokeOnCompletion {
                    temporaryCameraFile?.delete()
                }
                cameraFile = null
                cameraUri = null
                pendingUri = null
            }
        )
    }
}

private fun launchCameraCapture(
    context: android.content.Context,
    onCaptureCreated: (File, Uri) -> Unit
) {
    val cameraDir = File(
        context.cacheDir,
        "redforge_camera"
    ).apply {
        mkdirs()
    }

    val file = File.createTempFile(
        "redforge_camera_",
        ".jpg",
        cameraDir
    )

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    onCaptureCreated(file, uri)
}

@Composable
private fun PhotoGridItem(
    photo: ProgressPhoto,
    onDelete: () -> Unit
) {
    val dateText = remember(photo.takenAt) {
        SimpleDateFormat(
            "MMM d, yyyy",
            Locale.getDefault()
        ).format(Date(photo.takenAt))
    }

    Column {
        Box {
            AsyncImage(
                model = photo.filePath,
                contentDescription = "Progress photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .clip(RoundedCornerShape(14.dp))
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Text(
            photo.angle.name
                .lowercase()
                .replaceFirstChar { it.uppercase() } +
                " · $dateText",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun AngleDialog(
    onDismiss: () -> Unit,
    onConfirm: (PhotoAngle, String) -> Unit
) {
    var angle by remember {
        mutableStateOf(PhotoAngle.FRONT)
    }
    var note by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag this photo") },
        text = {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PhotoAngle.values().forEach { a ->
                        FilterChip(
                            selected = angle == a,
                            onClick = { angle = a },
                            label = {
                                Text(
                                    a.name
                                        .lowercase()
                                        .replaceFirstChar {
                                            it.uppercase()
                                        }
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        angle,
                        note.trim()
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
