# Arc Files (`v0.4.1-beta`)

A fast, lightweight Android file explorer built with **Kotlin** and **Jetpack Compose (Material 3)** following an **iOS Files / macOS Finder System Blue (`#007AFF`)** design, featuring a translucent **Top Tab Island**, **View Options & Tab Manager**, flat borderless **Breadcrumbs**, explicit **Sort Order** (`Asc` / `Desc`), **Group By** (`None` / `Type` / `Date`), **real-time Room per-folder layout persistence** (with automatic cleanup on folder rename/delete), **adaptive multi-DPI scaling** (`ldpi`–`xxxhdpi` & tablets), uniform **Square 2–5 column Grid**, **Original Ratio Image & Video Viewer**, **built-in APK Package Installer**, **Space Analyzer**, **Recycle Bin**, **Bookmarks**, **File Tags**, and a dedicated **Settings** page.

## Current Release Status
- **Stage:** Beta (`0.4.1-beta`, `versionCode = 14`)
- **Application ID:** `com.aistudio.applet.opqlxp`
- **Min SDK:** 24 · **Target/Compile SDK:** 36

## Key Features
- **iOS Translucent Top Header, Tab Island, Tab Manager & Sidebar Drawer**:
  - Top bar with staggered drawer menu toggle, segmented multi-tab pills (`Home`, `Directory`, `Category`, `Tag`, `Search`, `Analyzer`, `Recycle Bin`, `Bookmarks`, `Settings`), quick `+ New` menu, `Tabs & Windows` switcher, `Search`, and `View Options` (`List`/`Icons`, **Grid Size `2×`–`4×`**, **Original Media Ratio**, **Sort**, **Group By**).
  - Responsive `ArcSidebarDrawer` with expandable `Favorites`, `Local`, `Library`, `Tags`, and `Tools` sections, live `Hidden files` toggle, `All files access` status pill, Theme switcher (`Light` / `Dark` / `System`), and Settings link.
- **Square Media Grid & In-App Original-Ratio Viewer**:
  - Uniform `1:1` **Square Grid** (`2–4` columns on phones, `3–5` columns on tablets) with `GridCardMetrics` + `ArcDimens.forWidth` scaling spacing, radii, and thumbnail decode resolutions per viewport width and DPI.
  - Zero-allocation `ModifierNodeElement` drag-to-folder and long-press selection for smooth 60/120fps scrolling.
  - When **Original Ratio** is enabled, photos & video frames preserve their full uncropped aspect ratio (`ContentScale.Fit`) inside aligned square slots, and open in-app inside `OriginalImageViewer` (zoomable) or `OriginalVideoPlayer` (`TextureView` + `MediaPlayer` at true video aspect ratio).
- **Redesigned Home Dashboard & Built-in System Integrations**:
  - Circular Library strip (`Images`, `Movies`, `Documents`, `Music`, `Downloads`), dual storage donut gauge cards (`Internal Storage`, `App Workspace`, removable volumes, and persisted SAF mounts), **Space Analyzer**, **Tools Grid** (`Compressed` with `.zip`/`.7z`/`.tar`/`.rar` extraction & encrypted `.zip` support, `Note Editor`, `Recycle Bin`, `Mount SAF`, `Import Media`, `Import Docs`), and expandable **New Files** & **Bookmarks** bars.
  - Reliable **built-in Android Package Installer** flow (`REQUEST_INSTALL_PACKAGES` + SAF staging) and smart **External App Launcher** with broad MIME fallbacks, SAF staging, and explicit **"No App Installed"** notification & dialog fallback when no compatible viewer exists.
- **File Browser & Real-Time Per-Folder Layout State (`Room`)**:
  - Flat borderless Breadcrumb navigation with drag-and-drop move support and `BackHandler` step-up support, plus clean empty-folder view (`"This folder is empty"`).
  - **Room Database (`ArcDatabase`)** stores each folder's View Mode, Grid Size, Original Ratio, Sort Field, Sort Order, Group By, and File Tags in real time via reactive `Flow`, with automatic cleanup whenever folders are renamed, deleted, moved, or trashed.
  - Long-press multi-select + floating batch action bar, cross-tab clipboard (`Copy`, `Move`, `Paste`), iOS-style **Inline Rename**, **Advanced Batch Rename**, `File Tagging`, `Bookmark`, `Recycle Bin`, `Create Folder`, `Create Note`, and `FileProvider` sharing/opening.

## Reference Documentation
Before adding features or modifying UI/theme code, read:
- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — MVVM layers, Registry-based UI pattern, and file size rules.
- [`THEME.md`](./THEME.md) — Color tokens, Binary-Color Icon contract, typography, and spacing rules.
- [`ROADMAP.md`](./ROADMAP.md) — Step-by-step milestone plan from Alpha to Beta.

## Build & Verification
```bash
# Compile APK
gradle assembleDebug

# Run Unit & Robolectric Compose Tests
gradle :app:testDebugUnitTest
```

## CI/CD & GitHub Releases
The workflow at `.github/workflows/android-release.yml` automatically runs tests, builds the **Publish APK** (`assembleRelease`), **Play Store AAB** (`bundleRelease`), and **Universal APK** (`bundletool`), verifies signatures with `apksigner`, and publishes them to **GitHub Releases** on pushes to `main`, `v*` tags, or manual `workflow_dispatch`.

Configure these **Repository Secrets** (`Settings → Secrets and variables → Actions`) using a keystore whose key alias is `upload`:
- `KEYSTORE_BASE64`: Base64-encoded `.jks` upload keystore (`base64 -w 0 my-upload-key.jks`)
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password (defaults to `STORE_PASSWORD` if omitted)

