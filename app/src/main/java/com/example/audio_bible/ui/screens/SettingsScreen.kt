package com.example.audio_bible.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio_bible.ui.theme.BibleAmber
import com.example.audio_bible.ui.viewmodel.BibleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onChangeAudioFolder: () -> Unit,
    onImportFsb: () -> Unit
) {
    val activeTranslation by viewModel.activeTranslation.collectAsState()
    val importProgress    by viewModel.importProgress.collectAsState()
    val importError       by viewModel.importError.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearAllDialog     by remember { mutableStateOf(false) }
    var showReplaceDialog      by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── Audio Library ─────────────────────────────────────────────────
            item { SectionHeader(Icons.Rounded.AudioFile, "Audio Library") }
            item {
                SettingsTile(
                    icon = Icons.Rounded.Folder,
                    title = "Audio Folder",
                    subtitle = "Select folder containing MP3 files",
                    onClick = onChangeAudioFolder
                )
            }

            // ── Bible Text ────────────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader(Icons.AutoMirrored.Rounded.MenuBook, "Bible Text") }
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.FileOpen, null, tint = BibleAmber,
                                modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (activeTranslation != null) activeTranslation!!
                                    else "No translation imported",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    if (activeTranslation != null) "Tap to replace with a new .fsb file"
                                    else "Import a .fsb Bible text file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    if (activeTranslation != null) showReplaceDialog = true
                                    else onImportFsb()
                                },
                                enabled = importProgress == null,
                                colors = ButtonDefaults.buttonColors(containerColor = BibleAmber),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    if (activeTranslation != null) "Replace" else "Import",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.background
                                )
                            }
                        }
                        if (importProgress != null) {
                            val (done, total) = importProgress!!
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { if (total > 0) done.toFloat() / total else 0f },
                                modifier = Modifier.fillMaxWidth(),
                                color = BibleAmber,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (total == 0) "Starting…" else "Importing… $done / $total verses",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (importError != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(importError!!, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ── Data Management ───────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader(Icons.Rounded.Storage, "Data Management") }
            item {
                SettingsTile(
                    icon = Icons.Rounded.History,
                    title = "Clear Listening History",
                    subtitle = "Remove all play stats and reading progress",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { showClearHistoryDialog = true }
                )
            }
            item {
                SettingsTile(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Clear All Data",
                    subtitle = "Remove history and imported Bible text",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { showClearAllDialog = true }
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            icon = { Icon(Icons.Rounded.FileOpen, null, tint = BibleAmber) },
            title = { Text("Replace Translation") },
            text = { Text("This will remove \"$activeTranslation\" and import a new one.") },
            confirmButton = {
                TextButton(onClick = { showReplaceDialog = false; onImportFsb() }) {
                    Text("Replace", color = BibleAmber)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearHistoryDialog) {
        ConfirmDestructiveDialog(
            title = "Clear Listening History",
            message = "All play counts, reading progress and stats will be deleted. This cannot be undone.",
            confirmLabel = "Clear",
            onConfirm = { viewModel.clearListeningHistory(); showClearHistoryDialog = false },
            onDismiss = { showClearHistoryDialog = false }
        )
    }

    if (showClearAllDialog) {
        ConfirmDestructiveDialog(
            title = "Clear All Data",
            message = "History, stats, and the imported Bible translation will all be deleted. This cannot be undone.",
            confirmLabel = "Clear All",
            onConfirm = { viewModel.clearAllData(); showClearAllDialog = false },
            onDismiss = { showClearAllDialog = false }
        )
    }
}

@Composable
private fun SettingsTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color = BibleAmber,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    ) {
        Icon(icon, null, tint = BibleAmber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, null,
            tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
