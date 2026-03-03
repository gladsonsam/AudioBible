package com.example.audio_bible.ui.screens

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audio_bible.data.db.BibleVerse
import com.example.audio_bible.service.RepeatMode
import com.example.audio_bible.ui.theme.BibleAmber
import com.example.audio_bible.ui.theme.BibleGold
import com.example.audio_bible.ui.viewmodel.BibleViewModel

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit
) {
    val currentChapter  by viewModel.currentChapter.collectAsState()
    val isPlaying       by viewModel.isPlaying.collectAsState()
    val positionMs      by viewModel.positionMs.collectAsState()
    val durationMs      by viewModel.durationMs.collectAsState()
    val playbackSpeed   by viewModel.playbackSpeed.collectAsState()
    val repeatMode      by viewModel.repeatMode.collectAsState()
    val sleepTimerMs    by viewModel.sleepTimerMs.collectAsState()
    val verses          by viewModel.currentChapterVerses.collectAsState(emptyList())
    val syncData        by viewModel.currentSyncData.collectAsState()
    val activeVerse     by viewModel.activeVerseNumber.collectAsState()
    val selectedVerses  by viewModel.selectedVerses.collectAsState()

    val context = LocalContext.current

    // Clear selection when chapter changes
    LaunchedEffect(currentChapter) { viewModel.clearVerseSelection() }

    var showSleepDialog by remember { mutableStateOf(false) }
    var showSpeedSheet  by remember { mutableStateOf(false) }

    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f

    // Playing indicator animation (3 bars)
    val infiniteTransition = rememberInfiniteTransition(label = "bars")
    val bar1 by infiniteTransition.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(400), androidx.compose.animation.core.RepeatMode.Reverse), label = "b1")
    val bar2 by infiniteTransition.animateFloat(0.5f, 1f,
        infiniteRepeatable(tween(600, delayMillis = 100), androidx.compose.animation.core.RepeatMode.Reverse), label = "b2")
    val bar3 by infiniteTransition.animateFloat(0.2f, 1f,
        infiniteRepeatable(tween(500, delayMillis = 200), androidx.compose.animation.core.RepeatMode.Reverse), label = "b3")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Animated bars when playing
                        if (isPlaying) {
                            Row(
                                modifier = Modifier.padding(end = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                listOf(bar1, bar2, bar3).forEach { h ->
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height((14 * h).dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(BibleAmber)
                                    )
                                }
                            }
                        }
                        Column {
                            Text(
                                if (selectedVerses.isNotEmpty())
                                    "${selectedVerses.size} verse${if (selectedVerses.size == 1) "" else "s"} selected"
                                else currentChapter?.bookName ?: "–",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedVerses.isNotEmpty()) BibleAmber
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (selectedVerses.isNotEmpty()) "Long-press to select more"
                                else currentChapter?.let { "Chapter ${it.chapterNumber}" } ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    if (selectedVerses.isNotEmpty()) {
                        // Share selected verses
                        IconButton(onClick = {
                            val chapter = currentChapter
                            val verseList = verses
                                .filter { it.verseNumber in selectedVerses }
                                .sortedBy { it.verseNumber }
                            val text = formatShareText(chapter?.bookName, chapter?.chapterNumber, verseList)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }) {
                            Icon(Icons.Rounded.Share, "Share verses", tint = BibleAmber)
                        }
                        // Clear selection
                        IconButton(onClick = { viewModel.clearVerseSelection() }) {
                            Icon(Icons.Rounded.Close, "Clear selection",
                                tint = MaterialTheme.colorScheme.onSurface)
                        }
                    } else {
                        if (sleepTimerMs > 0) {
                            TextButton(onClick = { showSleepDialog = true }) {
                                Icon(Icons.Rounded.Timer, null,
                                    tint = BibleAmber, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(formatTime(sleepTimerMs), color = BibleAmber,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            IconButton(onClick = { showSleepDialog = true }) {
                                Icon(Icons.Rounded.Timer, "Sleep timer",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Verse text (fills remaining space) ───────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                if (verses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Rounded.MenuBook, null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(12.dp))
                            Text("No text loaded",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Import a Bible translation in Settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp))
                        }
                    }
                } else {
                    VerseList(
                    verses         = verses,
                    syncData       = syncData,
                    activeVerse    = activeVerse,
                    selectedVerses = selectedVerses,
                    onVerseClick = { verseNum ->
                        if (selectedVerses.isNotEmpty()) {
                            viewModel.toggleVerseSelection(verseNum)
                        } else {
                            val sync = syncData.firstOrNull { it.verse == verseNum }
                            if (sync != null) viewModel.seekTo((sync.startSec * 1000).toLong())
                        }
                    },
                    onVerseLongClick = { verseNum -> viewModel.toggleVerseSelection(verseNum) }
                )
                }
            }

            // ── Compact controls panel (pinned to bottom) ─────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    // Seek bar
                    Slider(
                        value = fraction,
                        onValueChange = { viewModel.seekTo((it * durationMs).toLong()) },
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = BibleAmber,
                            activeTrackColor = BibleAmber,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(positionMs), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatTime(durationMs), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Controls row: Repeat | << | ▶ | >> | Speed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Repeat
                        IconButton(onClick = { viewModel.cycleRepeat() }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    RepeatMode.ONE -> Icons.Rounded.RepeatOne
                                    else           -> Icons.Rounded.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = if (repeatMode != RepeatMode.OFF) BibleAmber
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Previous
                        IconButton(onClick = { viewModel.previous() }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, "Previous",
                                modifier = Modifier.size(30.dp),
                                tint = MaterialTheme.colorScheme.onSurface)
                        }

                        // Play / Pause
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(BibleAmber, BibleGold)))
                                .clickable { viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp),
                                tint = Color.Black
                            )
                        }

                        // Next
                        IconButton(onClick = { viewModel.next() }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.SkipNext, "Next",
                                modifier = Modifier.size(30.dp),
                                tint = MaterialTheme.colorScheme.onSurface)
                        }

                        // Speed chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (playbackSpeed != 1.0f) BibleAmber.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { showSpeedSheet = true }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                formatSpeed(playbackSpeed),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (playbackSpeed != 1.0f) BibleAmber
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    if (showSpeedSheet) {
        SpeedPickerSheet(
            current  = playbackSpeed,
            onSelect = { speed -> viewModel.setSpeed(speed); showSpeedSheet = false },
            onDismiss = { showSpeedSheet = false }
        )
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            activeMs  = sleepTimerMs,
            onSet     = { minutes -> viewModel.setSleepTimer(minutes); showSleepDialog = false },
            onCancel  = { viewModel.cancelSleepTimer(); showSleepDialog = false },
            onDismiss = { showSleepDialog = false }
        )
    }
}

// ── Verse list ────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VerseList(
    verses: List<BibleVerse>,
    syncData: List<com.example.audio_bible.data.VerseSync>,
    activeVerse: Int,
    selectedVerses: Set<Int>,
    onVerseClick: (Int) -> Unit,
    onVerseLongClick: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val hasSyncData = syncData.isNotEmpty()

    // Auto-scroll to active verse when it changes
    LaunchedEffect(activeVerse) {
        if (activeVerse > 0 && verses.isNotEmpty()) {
            val idx = verses.indexOfFirst { it.verseNumber == activeVerse }
            if (idx >= 0) listState.animateScrollToItem(idx, scrollOffset = -200)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(verses, key = { it.verseNumber }) { verse ->
            val isActive   = hasSyncData && verse.verseNumber == activeVerse
            val isSelected = verse.verseNumber in selectedVerses
            val bgColor by animateColorAsState(
                targetValue = when {
                    isSelected -> BibleAmber.copy(alpha = 0.25f)
                    isActive   -> BibleAmber.copy(alpha = 0.15f)
                    else       -> Color.Transparent
                },
                animationSpec = tween(300),
                label = "verseBg"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .combinedClickable(
                        onClick = { onVerseClick(verse.verseNumber) },
                        onLongClick = { onVerseLongClick(verse.verseNumber) }
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Verse number or checkmark when selected
                if (isSelected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = BibleAmber,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = "${verse.verseNumber}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        color = if (isActive) BibleAmber else BibleAmber.copy(alpha = 0.6f),
                        modifier = Modifier.width(16.dp),
                        textAlign = TextAlign.Start
                    )
                }
                Text(
                    text = verse.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 28.sp,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                    ),
                    color = if (isActive || isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── Speed Sheet ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedPickerSheet(
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Playback Speed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, bottom = 16.dp)
        )
        SPEED_OPTIONS.forEach { speed ->
            val selected = speed == current
            ListItem(
                headlineContent = {
                    Text(formatSpeed(speed),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) BibleAmber else MaterialTheme.colorScheme.onSurface)
                },
                supportingContent = {
                    Text(speedLabel(speed), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingContent = {
                    if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = BibleAmber)
                },
                modifier = Modifier.clickable { onSelect(speed) }
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── Sleep Timer Dialog ────────────────────────────────────────────────────────

@Composable
private fun SleepTimerDialog(
    activeMs: Long,
    onSet: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(5, 10, 15, 30, 45, 60)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 8.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, null, tint = BibleAmber)
                    Spacer(Modifier.width(8.dp))
                    Text("Sleep Timer", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                if (activeMs > 0) {
                    Text("Active: ${formatTime(activeMs)} remaining",
                        style = MaterialTheme.typography.bodySmall, color = BibleAmber)
                    Spacer(Modifier.height(8.dp))
                }
                Text("Stop playback after:", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                presets.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { minutes ->
                            OutlinedButton(
                                onClick = { onSet(minutes) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("${minutes}m", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (activeMs > 0) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Rounded.TimerOff, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel Timer")
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toInt()}×" else "${speed}×"

private fun speedLabel(speed: Float) = when (speed) {
    0.5f  -> "Half speed"
    0.75f -> "Slow"
    1.0f  -> "Normal"
    1.25f -> "Slightly faster"
    1.5f  -> "Fast"
    2.0f  -> "Double speed"
    else  -> ""
}

private fun formatShareText(bookName: String?, chapterNumber: Int?, verses: List<BibleVerse>): String {
    if (verses.isEmpty()) return ""
    val book = bookName ?: ""
    val chap = chapterNumber ?: 0

    // Group verses into consecutive runs
    val runs = mutableListOf<List<BibleVerse>>()
    var current = mutableListOf(verses[0])
    for (i in 1 until verses.size) {
        if (verses[i].verseNumber == verses[i - 1].verseNumber + 1) {
            current.add(verses[i])
        } else {
            runs.add(current)
            current = mutableListOf(verses[i])
        }
    }
    runs.add(current)

    return runs.joinToString("\n\n") { run ->
        val text = run.joinToString(" ") { it.text }
        val ref = if (run.size == 1) "$book $chap:${run[0].verseNumber}"
                  else "$book $chap:${run.first().verseNumber}-${run.last().verseNumber}"
        "$text\n— $ref"
    }
}
