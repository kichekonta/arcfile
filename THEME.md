# Theme & Design System — Arc Files (`THEME.md`)

Arc Files uses a clean **iOS Files / macOS Finder System Blue** design system with responsive multi-DPI proportions and **centralized theme tokens** inside **`app/src/main/java/com/example/ui/theme/`**.

## 1. Centralized Theme Files (`ui/theme/`)

| File | Responsibility |
| :--- | :--- |
| `Color.kt` | iOS System Grouped Light (`#F2F2F7`) / True Black Dark (`#000000`) surfaces, System Blue (`#007AFF` / `#0A84FF`), and vibrant icon accents |
| `Type.kt` | `Roboto` (Display, Title, Body) & `Roboto Mono` (Telemetry, Badges) |
| `Dimens.kt` | `ArcDimens` + `ArcDimens.forWidth(availableWidth)` — single place for adaptive paddings, `0.5.dp` hairlines, radii, and DPI-scaled card/grid metrics |
| `Theme.kt` | `ArcFilesTheme` & `ArcTheme.dimens` (`CompositionLocalProvider` fed by root `BoxWithConstraints`) |

## 2. iOS Files Color Tokens (`Color.kt` & `Theme.kt`)

| Token | Light Mode | Dark Mode | Usage |
| :--- | :--- | :--- | :--- |
| `background` | `#F2F2F7` | `#000000` | App canvas & grouped screens |
| `surface` | `#FFFFFF` | `#1C1C1E` | Cards, Top Tab Pills, Sheets |
| `surfaceContainer` | `#E5E5EA` | `#2C2C2E` | Icon wells, search fields, badges |
| `surfaceVariant` | `#ECECF1` | `#3A3A3C` | Chips, progress tracks |
| `outline` | `#C6C6C8` | `#38383A` | `0.5.dp` hairline dividers & strokes |
| `onSurface` (Ink) | `#1C1C1E` | `#FFFFFF` | Primary text & ink |
| `onSurfaceVariant` | `#8E8E93` | `#98989D` | Metadata, subtitles |
| `primary` | `#007AFF` | `#0A84FF` | System Blue accents, active pills, icons |

## 3. Canonical Empty Folder View (`EmptyBrowserState`)

Empty folders in `BrowserScreen.kt` preserve the clean centered illustration with **no clutter or extra create buttons**:
- `124.dp` atmospheric radial glow + subtle dashed circular orbit (`primary.copy(alpha = 0.22f)`)
- Tilted `66.dp` light-blue backing card (`rotationZ = -9f`) behind a crisp floating `72.dp` rounded-square badge (`surfaceContainerLow -> surfaceContainerHigh` gradient, `2.dp` shadow) containing `Icons.Outlined.FolderOpen` (`34.dp`, `primary`)
- Title: `"This folder is empty"` (`titleMedium`, `SemiBold`)
- Subtitle: `"Files and folders added here will appear automatically."` (`bodyMedium`, muted `onSurfaceVariant`)

## 4. Two-Tone Folder & File Icon Contract (`BinaryIconRegistry.kt`)

- **Two-Tone Folders (`1.27 : 1` iOS silhouette)**: Layered deep-sapphire back tab (`FolderBackLight` / `FolderBackDark`) paired with a sky-blue front pocket (`FolderFrontStart` -> `FolderFrontEnd`) centered inside `1:1` Square grid slots with zero outer padded box, scaling cleanly from `0.75f` (`ldpi`) to `4.0f` (`xxxhdpi`).
- **Semantic Folder Emblems (`FolderEmblemRenderer.kt`)**: Recognized folders (`Download`, `Pictures`/`DCIM`, `Movies`/`Videos`, `Music`, `Documents`, `Android`/`Workspace`, `Archive`) render crisp centered white emblems directly on the folder pocket.
- **Folded-Corner File Cards**: Rounded document sheets with a folded top-right dog-ear corner and centered extension label (`pdf`, `zip`, `mp4`, `txt`, etc.).
- **Monochrome Mode**: Toggleable in Settings to pure `onSurface` line-art.

## 5. Multi-DPI Proportions & Version Sync (`Dimens.kt` -> `ArcTheme.dimens`)
- **Adaptive Width Scaling (`ArcDimens.forWidth`)**: Adapts `screenHorizontalPadding` (`12.dp` narrow `<350dp`, `16.dp` standard, `20.dp` medium `>=600dp`, `24.dp` expanded `>=840dp`), icon sizes, and `maxContentWidth` (`820.dp`–`1120.dp`) automatically without `LocalConfiguration`.
- **Responsive Grid Radii (`GridCardMetrics`)**: Computed dynamically per column count (`2–4` phones, `3–5` tablets) and available width so narrow columns never over-clip corners.
- **Storage Cards**: `StorageGaugeSection` aligns 100% flush with column bounds (`fillMaxWidth()` primary hero + balanced `chunked(2)` secondary rows).
- **Project Version Sync**: `ArcSidebarDrawer` and `SettingsScreen` always read `BuildConfig.VERSION_NAME` & `BuildConfig.VERSION_CODE` directly from `app/build.gradle.kts`.
- **Touch Targets & Insets**: `enableEdgeToEdge()`, `statusBarsPadding()`, `navigationBarsPadding()`, and `48.dp` minimum interactive touch targets.
