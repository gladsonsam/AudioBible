package com.example.audio_bible.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio_bible.data.BibleBook
import com.example.audio_bible.data.BibleChapter
import com.example.audio_bible.data.db.ChapterPlayCount
import com.example.audio_bible.ui.theme.BibleAmber
import com.example.audio_bible.ui.theme.BibleGold
import com.example.audio_bible.ui.viewmodel.BibleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(
    book: BibleBook,
    viewModel: BibleViewModel,
    onChapterClick: (BibleChapter) -> Unit,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val currentChapter by viewModel.currentChapter.collectAsState()
    val isPlaying      by viewModel.isPlaying.collectAsState()
    val playCounts     by viewModel.chapterPlayCounts(book.number).collectAsState(emptyList())
    // Map chapterNumber → play count for O(1) lookup
    val playCountMap   = remember(playCounts) { playCounts.associate { it.chapterNumber to it.count } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                book.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${if (book.isOldTestament) "Old" else "New"} Testament · ${book.chapters.size} chapters",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )

                // Now playing chip
                currentChapter?.takeIf { it.bookName == book.name }?.let { chapter ->
                    AssistChip(
                        onClick = onOpenPlayer,
                        label = {
                            Text(
                                "Playing: Chapter ${chapter.chapterNumber}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (isPlaying) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeMute,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = BibleAmber
                            )
                        },
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        },
        bottomBar = {}
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(book.chapters) { chapter ->
                    val isActive  = currentChapter == chapter
                    val playCount = playCountMap[chapter.chapterNumber] ?: 0
                    ChapterCell(
                        chapter    = chapter,
                        isActive   = isActive,
                        isPlaying  = isPlaying && isActive,
                        playCount  = playCount,
                        onClick    = {
                            viewModel.playChapter(chapter)
                            onChapterClick(chapter)
                        }
                    )
                }
            }
            MiniPlayer(
                viewModel = viewModel,
                onExpand = onOpenPlayer,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun ChapterCell(
    chapter: BibleChapter,
    isActive: Boolean,
    isPlaying: Boolean,
    playCount: Int,
    onClick: () -> Unit
) {
    val bgBrush = if (isActive)
        Brush.linearGradient(listOf(BibleAmber, BibleGold))
    else
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant
            )
        )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bgBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            Icon(
                Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = "Now playing",
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = chapter.chapterNumber.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Read marker badge — top-right corner
        if (playCount > 0 && !isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(if (playCount > 1) 18.dp else 10.dp)
                    .clip(CircleShape)
                    .background(BibleAmber),
                contentAlignment = Alignment.Center
            ) {
                if (playCount > 1) {
                    Text(
                        text = if (playCount > 99) "99+" else playCount.toString(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 8.sp
                    )
                }
            }
        }
    }
}

