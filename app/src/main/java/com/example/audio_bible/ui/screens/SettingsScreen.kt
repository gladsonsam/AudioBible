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
import androidx.compose.ui.unit.sp
import com.example.audio_bible.data.db.TranslationProfile
import com.example.audio_bible.ui.theme.BibleAmber
import com.example.audio_bible.ui.viewmodel.BibleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onSetAudioFolder: (translationName: String) -> Unit,
    onImportFsb: (translationName: String) -> Unit
) {
    val activeTranslation  by viewModel.activeTranslation.collectAsState()
    val allProfiles        by viewModel.allTranslationProfiles.collectAsState(emptyList())
    val availableTranslations by viewModel.availableTranslations.collectAsState(emptyList())
    val importProgress     by viewModel.importProgress.collectAsState()
    val importError        by viewModel.importError.collectAsState()
    val playbackSpeed      by viewModel.playbackSpeed.collectAsState()
    val verseFontSize      by viewModel.verseFontSize.collectAsState()

    var showClearHistoryDialog   by remember { mutableStateOf(false) }
    var showClearAllDialog       by remember { mutableStateOf(false) }
    var showAddTranslationDialog by remember { mutableStateOf(false) }
    var deleteTarget             by remember { mutableStateOf<String?>(null) }

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

            // ── Playback ──────────────────────────────────────────────────────────
            item { SectionHeader(Icons.Rounded.Speed, "Playback") }
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, null, tint = BibleAmber,
                                modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Default Speed", fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge)
                                Text("Playback speed used when opening a chapter",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                        Slider(
                            value = speeds.indexOf(playbackSpeed).toFloat().coerceAtLeast(0f),
                            onValueChange = { viewModel.setDefaultSpeed(speeds[it.toInt()]) },
                            valueRange = 0f..(speeds.size - 1).toFloat(),
                            steps = speeds.size - 2,
                            colors = SliderDefaults.colors(thumbColor = BibleAmber, activeTrackColor = BibleAmber),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            speeds.forEach { speed ->
                                Text(
                                    if (speed == 1f) "1×" else "${speed}×",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                    color = if (playbackSpeed == speed) BibleAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Reading ───────────────────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader(Icons.Rounded.FormatSize, "Reading") }
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.FormatSize, null, tint = BibleAmber, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Verse Text Size", fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge)
                                Text("Preview: The word of the Lord",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = verseFontSize.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val fontSizes = listOf(12f, 14f, 16f, 18f, 20f, 22f, 24f)
                        Slider(
                            value = fontSizes.indexOf(verseFontSize).toFloat().coerceAtLeast(0f),
                            onValueChange = { viewModel.setVerseFontSize(fontSizes[it.toInt()]) },
                            valueRange = 0f..(fontSizes.size - 1).toFloat(),
                            steps = fontSizes.size - 2,
                            colors = SliderDefaults.colors(thumbColor = BibleAmber, activeTrackColor = BibleAmber),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            fontSizes.forEach { size ->
                                Text("${size.toInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (verseFontSize == size) FontWeight.Bold else FontWeight.Normal,
                                    color = if (verseFontSize == size) BibleAmber else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ── Translations ──────────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader(Icons.AutoMirrored.Rounded.MenuBook, "Translations") }

            // One card per translation profile
            items(allProfiles.size) { i ->
                val profile = allProfiles[i]
                val isActive = profile.name == activeTranslation
                TranslationCard(
                    profile        = profile,
                    isActive       = isActive,
                    hasFsb         = profile.name in availableTranslations,
                    importProgress = if (isActive) importProgress else null,
                    importError    = if (isActive) importError else null,
                    onActivate     = { viewModel.setActiveTranslation(profile.name) },
                    onSetFolder    = { onSetAudioFolder(profile.name) },
                    onImportFsb    = { onImportFsb(profile.name) },
                    onDelete       = { deleteTarget = profile.name }
                )
            }

            // Add Translation button
            item {
                OutlinedButton(
                    onClick = { showAddTranslationDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Translation", fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Data Management ───────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader(Icons.Rounded.Storage, "Data Management") }
            item {
                SettingsTile(
                    icon = Icons.Rounded.History,
                    title = "Clear Listening History",
                    subtitle = "Remove play stats for the active translation",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { showClearHistoryDialog = true }
                )
            }
            item {
                SettingsTile(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Clear All Data",
                    subtitle = "Remove all translations, history and stats",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { showClearAllDialog = true }
                )
            }

            // ── Credits ───────────────────────────────────────────────────────────
            item { Spacer(Modifier.height(24.dp)) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Made by Gladson Sam",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "\"So then faith comes by hearing, and hearing by the word of God.\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        "— Romans 10:17",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showAddTranslationDialog) {
        AddTranslationDialog(
            onConfirm = { name ->
                viewModel.createTranslation(name)
                showAddTranslationDialog = false
            },
            onDismiss = { showAddTranslationDialog = false }
        )
    }

    if (deleteTarget != null) {
        ConfirmDestructiveDialog(
            title   = "Delete \"${deleteTarget}\"",
            message = "Bible text, audio folder link, and listening history for this translation will be removed.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteTranslation(deleteTarget!!); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }

    if (showClearHistoryDialog) {
        ConfirmDestructiveDialog(
            title   = "Clear Listening History",
            message = "All play counts and reading progress for the active translation will be deleted.",
            confirmLabel = "Clear",
            onConfirm = { viewModel.clearListeningHistory(); showClearHistoryDialog = false },
            onDismiss = { showClearHistoryDialog = false }
        )
    }

    if (showClearAllDialog) {
        ConfirmDestructiveDialog(
            title   = "Clear All Data",
            message = "All translations, history and stats will be deleted. This cannot be undone.",
            confirmLabel = "Clear All",
            onConfirm = { viewModel.clearAllData(); showClearAllDialog = false },
            onDismiss = { showClearAllDialog = false }
        )
    }
}

@Composable
private fun TranslationCard(
    profile: TranslationProfile,
    isActive: Boolean,
    hasFsb: Boolean,
    importProgress: Pair<Int, Int>?,
    importError: String?,
    onActivate: () -> Unit,
    onSetFolder: () -> Unit,
    onImportFsb: () -> Unit,
    onDelete: () -> Unit
) {
    val hasFolder = profile.folderUri != null
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (isActive) 4.dp else 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Header row: radio + name + delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = isActive,
                    onClick  = onActivate,
                    colors   = RadioButtonDefaults.colors(selectedColor = BibleAmber)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge)
                    if (isActive) {
                        Text("Active", style = MaterialTheme.typography.labelSmall,
                            color = BibleAmber, fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteForever, "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Audio folder row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null, tint = BibleAmber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Audio Folder", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(if (hasFolder) "Configured" else "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasFolder) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onSetFolder) {
                    Text(if (hasFolder) "Change" else "Set Folder",
                        color = BibleAmber, fontWeight = FontWeight.SemiBold)
                }
            }

            // FSB Bible text row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FileOpen, null, tint = BibleAmber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bible Text (.fsb)", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(if (hasFsb) "Imported" else "Not imported",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasFsb) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onImportFsb, enabled = importProgress == null) {
                    Text(if (hasFsb) "Re-import" else "Import",
                        color = BibleAmber, fontWeight = FontWeight.SemiBold)
                }
            }

            // Import progress / error
            if (importProgress != null) {
                Spacer(Modifier.height(8.dp))
                val (done, total) = importProgress
                LinearProgressIndicator(
                    progress = { if (total > 0) done.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = BibleAmber,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(4.dp))
                Text(if (total == 0) "Starting…" else "Importing… $done / $total verses",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (importError != null) {
                Spacer(Modifier.height(4.dp))
                Text(importError, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AddTranslationDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = BibleAmber) },
        title = { Text("Add Translation") },
        text  = {
            Column {
                Text("Enter a name for this translation (e.g. KJV, NIV).",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Translation name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Add", color = BibleAmber) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text  = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

