package com.example.audio_bible

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.audio_bible.data.BibleBook
import com.example.audio_bible.ui.screens.BooksScreen
import com.example.audio_bible.ui.screens.ChaptersScreen
import com.example.audio_bible.ui.screens.PlayerScreen
import com.example.audio_bible.ui.screens.SettingsScreen
import com.example.audio_bible.ui.screens.StatsScreen
import com.example.audio_bible.ui.theme.AudiobibleTheme
import com.example.audio_bible.ui.viewmodel.BibleViewModel

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAVIGATE_TO = "navigate_to"
    }

    // Mutable so AudioBibleApp can observe changes from onNewIntent
    var pendingNavDestination = mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNavDestination.value = intent.getStringExtra(EXTRA_NAVIGATE_TO)
        enableEdgeToEdge()
        setContent {
            AudiobibleTheme {
                AudioBibleApp(activity = this)
            }
        }
    }

    /** Called when the activity is already running and a new intent arrives (FLAG_SINGLE_TOP). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingNavDestination.value = intent.getStringExtra(EXTRA_NAVIGATE_TO)
    }
}

@Composable
fun AudioBibleApp(activity: MainActivity) {
    val context       = LocalContext.current
    val navController = rememberNavController()
    val viewModel: BibleViewModel = viewModel()

    // Keeps the selected book across composable recompositions within the session
    var selectedBook by remember { mutableStateOf<BibleBook?>(null) }

    // Tracks which translation's audio folder the user is configuring
    var pendingFolderForTranslation by remember { mutableStateOf<String?>(null) }
    var pendingFsbForTranslation    by remember { mutableStateOf<String?>(null) }

    // Navigate to player whenever the activity receives a deep-link intent
    val pendingDest by activity.pendingNavDestination
    LaunchedEffect(pendingDest) {
        if (pendingDest == "player") {
            // Navigate, clearing any intermediate back stack entries above "books"
            navController.navigate("player") {
                launchSingleTop = true
            }
            activity.pendingNavDestination.value = null
        }
    }

    // Request POST_NOTIFICATIONS on Android 13+
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* silently accepted / denied */ }

    // Folder picker — takes persistent read permission on the chosen tree URI
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val target = pendingFolderForTranslation
            if (target != null) {
                viewModel.setFolderForTranslation(target, it)
                pendingFolderForTranslation = null
            } else {
                viewModel.onFolderSelected(it)
            }
        }
    }

    // FSB file picker for Bible text import
    val fsbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val target = pendingFsbForTranslation
            viewModel.importFsb(it, target)
            pendingFsbForTranslation = null
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    NavHost(
        navController    = navController,
        startDestination = "books",
        modifier         = Modifier.background(MaterialTheme.colorScheme.background),
        enterTransition  = { slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { it } },
        exitTransition   = { slideOutHorizontally(tween(350, easing = FastOutSlowInEasing)) { -it / 3 } },
        popEnterTransition  = { slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { -it / 3 } },
        popExitTransition   = { slideOutHorizontally(tween(350, easing = FastOutSlowInEasing)) { it } }
    ) {

        composable("books") {
            BooksScreen(
                viewModel      = viewModel,
                onBookClick    = { book ->
                    selectedBook = book
                    navController.navigate("chapters")
                },
                onImportFolder = { folderLauncher.launch(null) },
                onOpenPlayer   = { navController.navigate("player") },
                onOpenStats    = { navController.navigate("stats") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }

        composable("chapters") {
            selectedBook?.let { book ->
                ChaptersScreen(
                    book           = book,
                    viewModel      = viewModel,
                    onChapterClick = { navController.navigate("player") },
                    onBack         = { navController.popBackStack() },
                    onOpenPlayer   = { navController.navigate("player") }
                )
            }
        }

        // Player slides up from the bottom like a now-playing sheet
        composable(
            route = "player",
            enterTransition = {
                slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it }
            },
            exitTransition = {
                slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
            },
            popEnterTransition = {
                slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it }
            },
            popExitTransition = {
                slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
            }
        ) {
            PlayerScreen(
                viewModel = viewModel,
                onBack    = { navController.popBackStack() }
            )
        }

        // Stats slides in from the right (same direction as forward nav)
        composable("stats") {
            StatsScreen(
                onBack        = { navController.popBackStack() },
                bibleViewModel = viewModel
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel           = viewModel,
                onBack              = { navController.popBackStack() },
                onSetAudioFolder    = { translationName ->
                    pendingFolderForTranslation = translationName
                    folderLauncher.launch(null)
                },
                onImportFsb         = { translationName ->
                    pendingFsbForTranslation = translationName
                    fsbLauncher.launch(arrayOf("*/*"))
                }
            )
        }
    }
}
