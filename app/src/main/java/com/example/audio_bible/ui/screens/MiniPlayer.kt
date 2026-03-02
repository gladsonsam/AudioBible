package com.example.audio_bible.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio_bible.service.RepeatMode
import com.example.audio_bible.ui.theme.BibleAmber
import com.example.audio_bible.ui.viewmodel.BibleViewModel

@Composable
fun MiniPlayer(
    viewModel: BibleViewModel,
    onExpand: () -> Unit
) {
    val currentChapter by viewModel.currentChapter.collectAsState()
    val isPlaying      by viewModel.isPlaying.collectAsState()
    val positionMs     by viewModel.positionMs.collectAsState()
    val durationMs     by viewModel.durationMs.collectAsState()
    val repeatMode     by viewModel.repeatMode.collectAsState()

    AnimatedVisibility(
        visible = currentChapter != null,
        enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
        exit  = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
    ) {
        val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                        )
                    )
                )
                .clickable(onClick = onExpand)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp)
                    .size(32.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )

            // Progress bar
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 6.dp),
                color = BibleAmber,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp),
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Title
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentChapter?.bookName ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(currentChapter?.let { "Chapter ${it.chapterNumber}" } ?: "")
                            if (repeatMode != RepeatMode.OFF)
                                append(" · ${if (repeatMode == RepeatMode.ONE) "🔂" else "🔁"}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Controls
                IconButton(onClick = { viewModel.previous() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = BibleAmber,
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = { viewModel.next() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
