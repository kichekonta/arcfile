# Architecture Guide — Arc Files

Arc Files follows **Clean MVVM** paired with a **Registry-Based UI Architecture** to keep every Kotlin file small, reusable, and easy to extend without monolithic screens.

## 1. Layer Breakdown

```text
app/src/main/java/com/example/
├── MainActivity.kt                      # Entry point, Edge-to-Edge, System Launchers, Modal Hosts
├── data/
│   ├── FileModels.kt                    # Immutable domain models, TabDestination, EffectiveFolderLayout, GroupByField
│   ├── FileGroupingUtils.kt             # Deterministic multi-field Sort Order + Group By Type/Date bucketing
│   ├── PreferencesRepository.kt         # DataStore global defaults & per-key default Open-With apps
│   ├── MediaThumbnailLoader.kt          # Lazy Dispatchers.IO LruCache image bounds, APK icons & MediaMetadataRetriever frames
│   ├── FileExplorerRepository.kt        # StatFs/StorageManager, Local & SAF DocumentsContract I/O, FileProvider, PackageInstaller
│   └── db/                              # Room persistence (ArcDatabase, FolderLayout*, FileTag*)
└── ui/
    ├── ExplorerViewModel.kt             # Single source of truth StateFlow<ExplorerUiState> & tab/file actions
    ├── theme/                           # Color.kt, Type.kt, Dimens.kt (adaptive forWidth), Theme.kt (see THEME.md)
    ├── registry/                        # Declarative UI registries (keep files modular & extensible)
    │   ├── BinaryIconRegistry.kt        # Maps Categories, Drives, and FileItems -> Two-Tone Folder & File Canvas glyphs
    │   ├── FolderEmblemRenderer.kt      # Renders semantic centered emblems on special folders (Download, DCIM, etc.)
    │   ├── FileActionRegistry.kt        # Centralizes single-item & multi-select file actions
    │   └── TabDestinationRegistry.kt    # Routes TabDestination -> modular screen composables
    ├── components/
    │   ├── TopTabIsland.kt              # Translucent iOS Nav Bar, Staggered Menu, Tab Island, Search & View Options menu
    │   ├── ArcSidebarDrawer.kt          # Expandable sidebar drawer (Favorites, Local, Library, Tags, Tools, Theme)
    │   ├── common/ArcUiPrimitives.kt    # Reusable ArcCard, ArcBadge, ArcActionPill, ArcThumbnail, ArcEmptyStateCard
    │   └── dialogs/                     # ArchiveDialogs, BatchRenameDialog, DocumentViewerDialog, FileInspectorDialogs, MediaLightboxDialog, NewTabSheet
    └── screens/
        ├── home/                        # HomeScreen.kt + HomeComponents.kt
        ├── browser/                     # BrowserScreen.kt (with EmptyBrowserState) + BrowserHeaders.kt + BrowserItemViews.kt + BrowserDragDrop.kt
        ├── settings/                    # SettingsScreen.kt + SettingsComponents.kt
        └── tools/                       # ToolsScreens.kt (SpaceAnalyzerScreen, RecycleBinScreen, BookmarksScreen)
```

## 2. Registry-Based UI Rules (Mandatory for Contributors)

1. **File Size Cap**: Keep every UI file under **250 lines** (strictly < 500 lines). Split headers, item rows, and dialogs into focused sub-files.
2. **Reuse `ArcUiPrimitives` & Canonical `EmptyBrowserState`**:
   - Never write raw duplicated `Surface` cards, badges, pills, or thumbnails when `ArcCard`, `ArcBadge`, `ArcSectionHeader`, `ArcActionPill`, or `ArcThumbnail` already exist.
   - **Empty Directory View (`EmptyBrowserState` in `BrowserScreen.kt`)**: Empty folders (`DirectoryLoadState.Loaded(emptyList())`) MUST always keep the centered clean iOS-style illustration — dashed orbit ring + subtle tilted backing squircle + floating rounded-square folder badge + `"This folder is empty"` + `"Files and folders added here will appear automatically."` with **zero action buttons**. Never replace empty directories with `ArcEmptyStateCard` (which is reserved only for blocked/restricted `RestrictedRequiresSaf` or `Error` states and tools empty views).
3. **Zero-Allocation List/Grid Modifiers**: Use `ModifierNodeElement` / `DelegatingNode` (never `Modifier.composed`) inside `LazyColumn` and `LazyVerticalGrid` items for zero-lag native scroll performance.
4. **Adding a New Category or Drive Icon**:
   - Register the icon spec (`startLight`/`endLight`, `startDark`/`endDark`, `ModernGlyphRenderer`) in `BinaryIconRegistry.kt`.
   - Do **not** add inline `Canvas` drawing code inside screens.
5. **Adding a New Tab Destination**:
   - Add the `TabDestination` subtype in `FileModels.kt`.
   - Register its renderer and tab indicator in `TabDestinationRegistry.kt`.
   - Avoid modifying `MainActivity.kt` screen routing logic directly.
6. **Adding a New File Operation (e.g., Compress, Favorite)**:
   - Implement the I/O operation in `FileExplorerRepository.kt` and expose an intent method on `ExplorerViewModel.kt`.
   - Register the `FileActionSpec` in `FileActionRegistry.kt` so both single-item overflow menus and the multi-select bottom bar pick it up automatically.

## 3. Storage & Google Play Compliance Contract
- **No unnecessary media/legacy storage permissions** (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_*`) are declared in `AndroidManifest.xml`.
- **All Files Access (File Manager Core Use Case)**:
  - Declares `MANAGE_EXTERNAL_STORAGE` (`tools:ignore="ScopedStorage"`) and routes users to `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` when full Internal Storage access is needed (`Environment.isExternalStorageManager()`).
  - Once granted, `Internal Storage` and all shared folders/files (`Download`, `Pictures`, `DCIM`, `Documents`, `Movies`, `Music`) are browsed and indexed directly.
- **Drives & Pickers**:
  - `App Workspace` (`getExternalFilesDir(null)` / `filesDir`) is always writable.
  - External SD/USB or individual folders can also be mounted via `ActivityResultContracts.OpenDocumentTree` (SAF) with `takePersistableUriPermission` persisted in `DataStore`.
  - Media & documents can be imported via zero-permission `PickMultipleVisualMedia` and `OpenMultipleDocuments`.
- **Opening/Sharing Files**: Local files are exposed exclusively via `FileProvider` (`${applicationId}.fileprovider`).
