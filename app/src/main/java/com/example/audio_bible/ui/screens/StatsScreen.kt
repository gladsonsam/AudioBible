package com.example.audio_bible.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio_bible.data.db.BookStat
import com.example.audio_bible.data.db.HeatmapDay
import com.example.audio_bible.data.db.ReadingLog
import com.example.audio_bible.ui.theme.BibleAmber
import com.example.audio_bible.ui.theme.BibleGold
import com.example.audio_bible.ui.viewmodel.StatsViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    bibleViewModel: com.example.audio_bible.ui.viewmodel.BibleViewModel? = null
) {
    val vm: StatsViewModel = viewModel()
    val activeTranslation by (bibleViewModel?.activeTranslation
        ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()

    // Keep StatsViewModel in sync whenever the active translation changes
    LaunchedEffect(activeTranslation) {
        vm.setTranslationFilter(activeTranslation ?: "")
    }

    val totalPlays       by vm.totalPlays.collectAsState(0)
    val uniqueChapters   by vm.uniqueChapters.collectAsState(0)
    val totalMinutes     by vm.totalMinutes.collectAsState(0L)
    val top5             by vm.top5Books.collectAsState(emptyList())
    val allBooks         by vm.allBookStats.collectAsState(emptyList())
    val heatmap          by vm.heatmap.collectAsState(emptyList())
    val streak           by vm.currentStreak.collectAsState(0)
    val longestStreak    by vm.longestStreak.collectAsState(0)
    val completionFrac   by vm.completionFraction.collectAsState(0f)
    val recent           by vm.recentHistory.collectAsState(emptyList())
    val selectedDay      by vm.selectedDay.collectAsState()
    val selectedDayLogs  by vm.selectedDayLogs.collectAsState(emptyList())

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (selectedDay != null) {
        ModalBottomSheet(
            onDismissRequest = { vm.selectedDay.value = null },
            sheetState = sheetState
        ) {
            DayDetailSheet(day = selectedDay!!, logs = selectedDayLogs)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Streak hero card ───────────────────────────────────────────────
            item { StreakCard(streak, longestStreak) }

            // ── Summary row ───────────────────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Modifier.weight(1f), Icons.Rounded.Schedule,
                        formatMinutes(totalMinutes), "Time Listened",
                        MaterialTheme.colorScheme.tertiary)
                    StatCard(Modifier.weight(1f), Icons.Rounded.LibraryMusic,
                        "$uniqueChapters", "Chapters Heard",
                        BibleGold)
                }
            }

            // ── Bible completion progress ──────────────────────────────────────
            item { CompletionCard(uniqueChapters, completionFrac) }

            // ── Heatmap ───────────────────────────────────────────────────────
            item {
                SectionTitle("Listening History")
                Spacer(Modifier.height(8.dp))
                HeatmapGrid(heatmap, onDayClick = { vm.selectedDay.value = it })
            }

            // ── Top books ─────────────────────────────────────────────────────
            if (top5.isNotEmpty()) {
                item { SectionTitle("Most Played Books") }
                items(top5) { book ->
                    BookStatRow(book, maxPlays = top5.first().timesPlayed)
                }
            }

            // ── Recent activity ───────────────────────────────────────────────
            if (recent.isNotEmpty()) {
                item { SectionTitle("Recent Activity") }
                items(recent.take(10)) { log -> RecentRow(log) }
            }

            // ── All books ─────────────────────────────────────────────────────
            if (allBooks.size > 5) {
                item { SectionTitle("All Books") }
                items(allBooks) { book ->
                    BookStatRow(book, maxPlays = allBooks.first().timesPlayed)
                }
            }

            // ── Empty state ───────────────────────────────────────────────────
            if (totalPlays == 0) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.BarChart, null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(12.dp))
                        Text("No stats yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Finish a chapter to start tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

// ── Streak hero card ──────────────────────────────────────────────────────────

@Composable
private fun StreakCard(current: Int, longest: Int) {
    // Pulse the flame when streak is active
    val infiniteTransition = rememberInfiniteTransition(label = "flame")
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (current > 0) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "flameScale"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        color = if (current > 0)
            Color(0xFFE25822).copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Rounded.LocalFireDepartment, null,
                tint = if (current > 0) Color(0xFFE25822) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(52.dp).scale(flameScale)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (current > 0) "$current day streak" else "No active streak",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (current > 0) Color(0xFFE25822)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (current > 0) "Keep listening every day to maintain it!"
                    else "Listen today to start a streak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (longest > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$longest", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BibleAmber)
                    Text("best", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Bible completion card ─────────────────────────────────────────────────────

@Composable
private fun CompletionCard(chaptersHeard: Int, fraction: Float) {
    val pct = (fraction * 100).toInt().coerceIn(0, 100)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoStories, null,
                    tint = BibleAmber, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bible Completion", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("$chaptersHeard / 1189 chapters",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = BibleAmber,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    pct == 0   -> "Start your journey through the Bible"
                    pct < 10   -> "$pct% complete — great start!"
                    pct < 50   -> "$pct% complete — keep going!"
                    pct < 100  -> "$pct% complete — almost there!"
                    else       -> "You've heard the whole Bible! 🎉"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Stat card ─────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Heatmap ───────────────────────────────────────────────────────────────────

@Composable
private fun HeatmapGrid(data: List<HeatmapDay>, onDayClick: (String) -> Unit) {
    val today     = LocalDate.now()
    val dayFmt    = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val mthFmt    = DateTimeFormatter.ofPattern("MMM")
    val countMap  = data.associate { it.dayLabel to it.count }
    val maxCount  = data.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val cellSize  = 13.dp
    val cellGap   = 3.dp
    val labelH    = 14.dp
    val yearH     = 16.dp

    val dayOfWeekOffset = (today.dayOfWeek.value - 1)
    // Span from earliest data point (or just today if empty)
    val earliestDay = data.minOfOrNull { it.dayLabel }
        ?.let { runCatching { LocalDate.parse(it, dayFmt) }.getOrNull() }
        ?: today
    val daysBack = java.time.temporal.ChronoUnit.DAYS.between(earliestDay, today).toInt()
    val allDays = ((daysBack + dayOfWeekOffset) downTo 0).map { today.minusDays(it.toLong()) }
    val weeks = allDays.chunked(7)

    val dotColor  = MaterialTheme.colorScheme.onSurfaceVariant
    val accentCol = MaterialTheme.colorScheme.primary

    // Pre-compute per-column labels statelessly
    data class WeekLabels(val month: String?, val year: String?)
    var lastMonth = -1
    var lastYear  = -1
    val weekLabels = weeks.map { week ->
        val first = week.firstOrNull { !it.isAfter(today) }
        val showMonth = first != null && first.dayOfMonth <= 7 && first.monthValue != lastMonth
        val showYear  = first != null && first.year != lastYear && (showMonth || weeks.indexOf(week) == 0)
        if (showMonth) lastMonth = first!!.monthValue
        if (showYear)  lastYear  = first!!.year
        WeekLabels(
            month = if (showMonth) first!!.format(mthFmt) else null,
            year  = if (showYear)  "'${first!!.year.toString().takeLast(2)}" else null
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                // Fixed left column: day-of-week labels (offset by yearH + labelH)
                Column(
                    modifier = Modifier.padding(end = 4.dp, top = yearH + labelH),
                    verticalArrangement = Arrangement.spacedBy(cellGap)
                ) {
                    listOf("Mon","","Wed","","Fri","","Sun").forEach { label ->
                        Box(
                            Modifier.size(width = 22.dp, height = cellSize),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                color = dotColor.copy(alpha = 0.45f))
                        }
                    }
                }

                // Horizontally scrollable grid (starts scrolled to today)
                val scrollState = rememberScrollState(Int.MAX_VALUE)
                Row(
                    modifier = Modifier.horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(cellGap)
                ) {
                    weeks.forEachIndexed { i, week ->
                        val labels = weekLabels[i]
                        Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                            // Year label (bold, primary color)
                            Box(
                                Modifier.size(width = cellSize, height = yearH),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                if (labels.year != null) {
                                    Text(
                                        labels.year,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.sp, fontWeight = FontWeight.Bold
                                        ),
                                        color = accentCol.copy(alpha = 0.9f),
                                        maxLines = 1
                                    )
                                }
                            }
                            // Month label
                            Box(
                                Modifier.size(width = cellSize, height = labelH),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                if (labels.month != null) {
                                    Text(
                                        labels.month,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = dotColor.copy(alpha = 0.65f),
                                        maxLines = 1
                                    )
                                }
                            }
                            // 7 day cells
                            week.forEach { day ->
                                val isFuture = day.isAfter(today)
                                val key      = day.format(dayFmt)
                                val count    = if (isFuture) 0 else countMap[key] ?: 0
                                val isToday  = day == today
                                val alpha = when {
                                    isFuture   -> 0f
                                    count == 0 -> 0.07f
                                    else       -> 0.25f + 0.75f * (count.toFloat() / maxCount)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (isFuture) Color.Transparent
                                            else BibleAmber.copy(alpha = alpha)
                                        )
                                        .then(
                                            if (!isFuture) Modifier.clickable { onDayClick(key) }
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isToday) {
                                        Box(Modifier
                                            .size(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                if (count > 0) Color.Black.copy(alpha = 0.45f)
                                                else BibleAmber.copy(alpha = 0.7f)
                                            ))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Footer: activity summary + legend
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (data.isNotEmpty()) "${data.size} active days recorded"
                    else "No activity recorded yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = dotColor.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                Text("Less", style = MaterialTheme.typography.labelSmall, color = dotColor.copy(alpha = 0.45f))
                Spacer(Modifier.width(4.dp))
                listOf(0.07f, 0.3f, 0.55f, 0.8f, 1.0f).forEach { a ->
                    Box(modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BibleAmber.copy(alpha = a)))
                }
                Spacer(Modifier.width(4.dp))
                Text("More", style = MaterialTheme.typography.labelSmall, color = dotColor.copy(alpha = 0.45f))
            }
        }
    }
}

// ── Day detail bottom sheet ───────────────────────────────────────────────────

@Composable
private fun DayDetailSheet(day: String, logs: List<ReadingLog>) {
    val displayFmt = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }
    val timeFmt    = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val date = remember(day) {
        try { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(day) } catch (_: Exception) { null }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp)
    ) {
        Text(
            text = date?.let { displayFmt.format(it) } ?: day,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No activity on this day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val totalMs = logs.sumOf { it.durationListenedMs }
            Text("${logs.size} chapter${if (logs.size > 1) "s" else ""}  ·  ${formatMinutes(totalMs / 60000)} total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))
            logs.forEach { log ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BibleAmber.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Headphones, null,
                            tint = BibleAmber, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${log.bookName} ${log.chapterNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(timeFmt.format(Date(log.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatMinutes(log.durationListenedMs / 60000),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            }
        }
    }
}

// ── Book stat row ─────────────────────────────────────────────────────────────

@Composable
private fun BookStatRow(book: BookStat, maxPlays: Int) {
    val fraction = if (maxPlays > 0) book.timesPlayed.toFloat() / maxPlays else 0f
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(book.bookName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                Text("${book.timesPlayed}×  ·  ${book.totalMinutes}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = BibleAmber,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        }
    }
}

// ── Recent activity row ───────────────────────────────────────────────────────

@Composable
private fun RecentRow(log: ReadingLog) {
    val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BibleAmber.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Headphones, null,
                tint = BibleAmber, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("${log.bookName} ${log.chapterNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
            Text(dateFmt.format(Date(log.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatMinutes(log.durationListenedMs / 60000),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)
}

private fun formatMinutes(minutes: Long): String = when {
    minutes < 60   -> "${minutes}m"
    minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m"
    else           -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
}
