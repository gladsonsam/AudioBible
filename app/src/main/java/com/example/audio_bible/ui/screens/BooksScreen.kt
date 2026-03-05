package com.example.audio_bible.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.audio_bible.data.BibleBook
import com.example.audio_bible.data.BibleChapter
import com.example.audio_bible.ui.theme.BibleAmber
import com.example.audio_bible.ui.theme.BibleGold
import com.example.audio_bible.ui.viewmodel.BibleViewModel

/** In-memory cache for book background bitmaps to avoid re-decoding on scroll. */
private val bookBitmapCache = mutableMapOf<String, android.graphics.Bitmap?>()

/** Parse "gen 1", "john 3:16", "1 samuel 5" → matching BibleChapter or null */
private fun parseChapterQuery(query: String, books: List<BibleBook>): BibleChapter? {
    val trimmed = query.trim()
    // Accept "Book N" or "Book N:V" (we only care about the chapter part)
    val match = Regex("""^(.+?)\s+(\d+)(?::\d+)?$""").find(trimmed) ?: return null
    val bookQuery  = match.groupValues[1].trim()
    val chapterNum = match.groupValues[2].toIntOrNull() ?: return null
    val book = books.firstOrNull {
        it.name.startsWith(bookQuery, ignoreCase = true) ||
        it.name.replace(" ", "").startsWith(bookQuery.replace(" ", ""), ignoreCase = true)
    } ?: return null
    return book.chapters.firstOrNull { it.chapterNumber == chapterNum }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    viewModel: BibleViewModel,
    onBookClick: (BibleBook) -> Unit,
    onImportFolder: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenStats: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val books             by viewModel.books.collectAsState()
    val isLoading         by viewModel.isLoading.collectAsState()
    val error             by viewModel.error.collectAsState()
    val progress          by viewModel.bookProgressMap.collectAsState(emptyMap())
    val continueInfo      by viewModel.continueListening.collectAsState()
    val verseResults      by viewModel.verseSearchResults.collectAsState()
    val activeTranslation by viewModel.activeTranslation.collectAsState()
    var query             by remember { mutableStateOf("") }
    var oldExpanded       by remember { mutableStateOf(true) }
    var newExpanded       by remember { mutableStateOf(true) }

    // Keep ViewModel search query in sync
    LaunchedEffect(query) { viewModel.verseSearchQuery.value = query }

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
                                "Audio Bible",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!activeTranslation.isNullOrBlank()) {
                                Text(
                                    activeTranslation!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BibleAmber
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    actions = {
                        IconButton(onClick = onOpenStats) {
                            Icon(Icons.Rounded.BarChart, "Stats",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, "Settings",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
                if (books.isNotEmpty()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search books…") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, "Clear")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = BibleAmber)
                        Spacer(Modifier.height(12.dp))
                        Text("Loading your Bible…", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                error != null -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )

                books.isEmpty() -> EmptyState(onImportFolder)

                else -> {
                    val chapterMatch = remember(query, books) { parseChapterQuery(query, books) }
                    val filtered = if (query.isBlank()) books
                                   else books.filter { it.name.contains(query, ignoreCase = true) }
                    val old = filtered.filter { it.isOldTestament }
                    val new = filtered.filter { !it.isOldTestament }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Continue Listening card (hidden during search)
                        if (continueInfo != null && query.isBlank()) {
                            item(span = { GridItemSpan(2) }) {
                                ContinueListeningCard(info = continueInfo!!) {
                                    viewModel.resumeLastPlayed()
                                    onOpenPlayer()
                                }
                            }
                        }
                        // Direct chapter search result
                        if (chapterMatch != null) {
                            item(span = { GridItemSpan(2) }) {
                                DirectChapterResult(chapter = chapterMatch) {
                                    viewModel.playChapter(chapterMatch)
                                    onOpenPlayer()
                                }
                            }
                        }
                        if (old.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                SectionHeader("Old Testament", old.size, oldExpanded) { oldExpanded = !oldExpanded }
                            }
                            if (oldExpanded) items(old) { BookCard(it, progress[it.number] ?: 0, onBookClick) }
                        }
                        if (new.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                SectionHeader("New Testament", new.size, newExpanded) { newExpanded = !newExpanded }
                            }
                            if (newExpanded) items(new) { BookCard(it, progress[it.number] ?: 0, onBookClick) }
                        }
                        // Verse search results (when query >= 3 chars)
                        if (verseResults.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                SectionHeader("Bible Search (${verseResults.size})", verseResults.size, true) {}
                            }
                            items(verseResults, span = { GridItemSpan(2) }) { result ->
                                VerseSearchResultCard(result, query) {
                                    result.chapter?.let { ch ->
                                        viewModel.playChapter(ch)
                                        onOpenPlayer()
                                    }
                                }
                            }
                        } else if (query.length >= 3 && filtered.isEmpty() && chapterMatch == null) {
                            item(span = { GridItemSpan(2) }) {
                                Text(
                                    "No results for \"$query\"",
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Bottom padding — ensures last row can scroll above the MiniPlayer
                        item(span = { GridItemSpan(2) }) {
                            Spacer(Modifier.height(110.dp))
                        }
                    }
                }
            }
            // MiniPlayer overlaid at the bottom — no Scaffold surface behind it
            MiniPlayer(
                viewModel = viewModel,
                onExpand = onOpenPlayer,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun VerseSearchResultCard(
    result: BibleViewModel.VerseSearchResult,
    query: String = "",
    onClick: () -> Unit
) {
    val hasAudio = result.chapter != null
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(enabled = hasAudio, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Reference badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BibleAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${result.bookName}\n${result.chapterNumber}:${result.verseNumber}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = BibleAmber,
                        textAlign = TextAlign.Center
                    )
                }
            }
            // Verse text with highlighted match
            val highlighted = buildAnnotatedString {
                val lower = result.text.lowercase()
                val qLower = query.lowercase()
                var cursor = 0
                if (qLower.isNotEmpty()) {
                    var idx = lower.indexOf(qLower, cursor)
                    while (idx >= 0) {
                        append(result.text.substring(cursor, idx))
                        withStyle(SpanStyle(
                            color = BibleAmber,
                            fontWeight = FontWeight.Bold,
                            background = BibleAmber.copy(alpha = 0.12f)
                        )) {
                            append(result.text.substring(idx, idx + qLower.length))
                        }
                        cursor = idx + qLower.length
                        idx = lower.indexOf(qLower, cursor)
                    }
                }
                append(result.text.substring(cursor))
            }
            Text(
                text = highlighted,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (hasAudio) 1f else 0.7f),
                modifier = Modifier.weight(1f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (hasAudio) {
                Icon(Icons.Rounded.PlayArrow, null,
                    tint = BibleAmber.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp).padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun DirectChapterResult(chapter: BibleChapter, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(BibleAmber, BibleGold))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null,
                    tint = Color.Black, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Tap to play",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                tint = BibleAmber)
        }
    }
}

@Composable
private fun ContinueListeningCard(
    info: BibleViewModel.ContinueListeningInfo,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(BibleAmber, BibleGold))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, null,
                    tint = Color.Black, modifier = Modifier.size(32.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Continue Listening",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                Text("${info.bookName} ${info.chapterNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (info.positionMs > 0) {
                    val m = info.positionMs / 60000
                    val s = (info.positionMs % 60000) / 1000
                    Text("Paused at %d:%02d".format(m, s),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                }
            }
            Icon(Icons.Rounded.ChevronRight, null,
                tint = BibleAmber)
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f, label = "chevron"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BibleAmber)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Text("$count", color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.ExpandMore, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp).rotate(chevronAngle)
        )
    }
}

@Composable
private fun BookCard(book: BibleBook, chaptersRead: Int, onClick: (BibleBook) -> Unit) {
    val total = book.chapters.size
    val fraction = if (total > 0) chaptersRead.toFloat() / total else 0f
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = bookBitmapCache[book.name],
        book.name
    ) {
        if (bookBitmapCache.containsKey(book.name)) {
            value = bookBitmapCache[book.name]
            return@produceState
        }
        val decoded = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                context.assets.open("bible_book_backgrounds/${book.name}.jpg")
                    .use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            }
        } catch (_: Exception) { null }
        bookBitmapCache[book.name] = decoded
        value = decoded
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(book) }
    ) {
        Box(modifier = Modifier.height(130.dp)) {
            // Background image
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            // Dark gradient overlay for text legibility
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.75f))
                        )
                    )
            )
            // Content
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BibleAmber,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
                Text(
                    text = "$chaptersRead/$total read",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chaptersRead > 0) BibleAmber else Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onImportFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.radialGradient(
                        listOf(BibleGold.copy(alpha = 0.3f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null,
                modifier = Modifier.size(64.dp), tint = BibleAmber)
        }
        Spacer(Modifier.height(24.dp))
        Text("Welcome to Audio Bible",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Open Settings and select your audio folder.\n\nExpected filename format:\n66_Revelation_022.mp3",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onImportFolder,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BibleAmber)
        ) {
            Icon(Icons.Rounded.FolderOpen, null)
            Spacer(Modifier.width(8.dp))
            Text("Select Folder", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}
