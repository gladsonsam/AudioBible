# Copilot Instructions

## Build & lint commands

```bash
# Full debug build
./gradlew assembleDebug

# Compile-only check (fastest way to verify changes)
./gradlew :app:compileDebugKotlin

# Run all tests
./gradlew test

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.example.audio_bible.ExampleUnitTest"
```

Always use `./gradlew :app:compileDebugKotlin` to validate changes before finishing — it's faster than a full build.

---

## Architecture overview

The app is structured in three layers that communicate via `PlayerState` (a global singleton):

```
UI (Compose screens)
    └── BibleViewModel (AndroidViewModel)
            ├── BibleRepository  — SAF file access + sync JSON loading
            ├── FsbParser        — .fsb Bible text importer
            ├── BibleDatabase    — Room DB (stats + verse text)
            └── AudioPlayerService (via Intent actions)
                    └── PlayerState (shared MutableStateFlows)
```

### PlayerState — the shared state bus
`service/PlayerState.kt` is a plain `object` (singleton) holding `MutableStateFlow`s for all playback state. The service writes to it; the ViewModel exposes the same flows directly to the UI. This means there is **no LiveData, no callbacks, no broadcast for state updates** — just collect from `PlayerState.*`.

### AudioPlayerService — intent-driven commands
All playback control goes through `Intent` actions (defined as constants in `AudioPlayerService.companion`). The ViewModel's `send()` helper calls `ContextCompat.startForegroundService()` with the appropriate action. The service also extends `MediaBrowserServiceCompat` for Galaxy Routines / system media button integration.

### BibleViewModel — single ViewModel for the whole app
One `BibleViewModel` instance (created in `MainActivity`) is passed down to every screen as a parameter. There is no DI framework — `BibleRepository`, `FsbParser`, and `BibleDatabase` are all instantiated directly inside the ViewModel.

---

## Key conventions

### SAF (Storage Access Framework) file loading
- **Never use `DocumentFile.listFiles()`** — it makes one IPC call per file, which is 1000+ calls for a full Bible. Always use `ContentResolver.query()` on `DocumentsContract.buildChildDocumentsUriUsingTree()` (single call).
- Audio URIs are SAF tree URIs. Pass them to `MediaPlayer.setDataSource(context, uri)` — **not** `openFileDescriptor()` which fails in cold-start contexts.

### Audio file naming convention
Audio files must follow: `NN_BookName_NNN.mp3` (e.g. `01_Genesis_001.mp3`).  
Verse sync files: `NN_BookName_NNN_sync.json` inside a `sync/` subdirectory of the audio folder.

### .fsb Bible text format
JSON array: `[hashString, {name, books:[{number, chapters:[{number(str), verses:[{number(str), text}]}]}]}]`.  
Chapter and verse numbers are **strings** in this format. Parsed by `FsbParser`.

### Verse sync JSON format
```json
[{"v": 1, "s": 0.54, "e": 10.21}, ...]
```
`v` = verse number, `s` = start seconds (float), `e` = end seconds (float).

### SharedPreferences — single file `"bible_prefs"`
Both `BibleRepository` and `AudioPlayerService` use the same prefs file. Keys:
- `folder_uri` — SAF tree URI for the audio folder
- `active_translation` — currently imported Bible translation name
- `last_uri`, `last_book_name`, `last_book_number`, `last_chapter_number`, `last_position_ms` — resume state

### Room DB versioning
Current version: **2**. Always add a `Migration(from, to)` object to `BibleDatabase.addMigrations()` — do not use `fallbackToDestructiveMigration()`.

### RepeatMode naming collision
`com.example.audio_bible.service.RepeatMode` conflicts with `androidx.compose.animation.core.RepeatMode`. In Compose files always use the fully-qualified `androidx.compose.animation.core.RepeatMode.Reverse` when needed for animations.

### Deprecated icon imports
Use `Icons.AutoMirrored.Rounded.*` instead of `Icons.Rounded.*` for `MenuBook`, `VolumeUp`, and `VolumeMute`. The non-auto-mirrored variants produce deprecation warnings.

### Book number ranges
Books 1–39 = Old Testament, 40–66 = New Testament. This is encoded in `BibleBook.isOldTestament`.

### Theme colors
All custom colors are defined in `ui/theme/Color.kt`. Key accent colors: `BibleAmber` (`#E8A838`) and `BibleGold` (`#D4A017`). Use these instead of hardcoded hex values for consistency with the warm amber/brown Bible theme.

### Navigation
NavHost is defined in `MainActivity.kt`. Routes: `books`, `chapters`, `player`, `stats`, `settings`. The player route uses vertical slide transitions (slides up from bottom); all other routes use horizontal slides. `selectedBook` state is held in `AudioBibleApp()` and passed to `ChaptersScreen` via closure — it is not part of the nav back stack arguments.
