# Development Roadmap — Arc Files

Step-by-step execution plan to progress cleanly from `v0.1-alpha` to `v1.0`.

## Phase 1 — Foundation (`0.1-alpha`) [Completed]
- [x] Floating **Top Tab Island** with pinned `Home` tab, dynamic tabs, close buttons, and utility actions.
- [x] **Home Dashboard** with live `Drives` telemetry (`StatFs` / `StorageManager` / SAF mounts) and category overview.
- [x] **Registry-Based UI** (`BinaryIconRegistry`, `TabDestinationRegistry`, `FileActionRegistry`) + reusable `ArcUiPrimitives`.
- [x] Real local & SAF file operations (browse, create folder/note, copy, move, rename, delete, share, preview/edit text).
- [x] Custom adaptive launcher icon & bundled local typography.

## Phase 1.5 — Minimalist Visual System (`0.2-alpha`) [Completed]
- [x] **Aesthetic Blue** Light/Dark palette + **Roboto / Roboto Mono** typography + centralized `ArcDimens`.
- [x] **Modern Linear Colored Icons** across folders, drives, categories, and files.
- [x] Full-grid-width borderless folder icons without padded background boxes, with standard minimal gaps.
- [x] Dynamic `BuildConfig.VERSION_NAME` display synced directly from `app/build.gradle.kts`.
- [x] (`0.2.1-alpha`) Bridged local shared folders to persisted SAF trees, fixed SAF nested document/column fetching, and added Scoped Storage folder-access banner.
- [x] (`0.2.2-alpha`) Two-tone Aesthetic Blue folder & folded-corner file icons with semantic folder emblems (Downloads, Pictures, Videos, Music, Documents, Android, Archive).
- [x] (`0.2.3-alpha`) Full Storage Access (`MANAGE_EXTERNAL_STORAGE`) flow with 1-tap Settings launch and automatic `ON_RESUME` storage refresh.
- [x] (`0.2.4-alpha`) Dedicated Settings page (`TabDestination.Settings`) with Appearance, Browsing & Sorting, Storage & Permissions, and About sections.
- [x] (`0.2.5-alpha`) Aesthetic Blue Header & frosted Tab Island, expandable `ArcSidebarDrawer`, circular category strip, donut storage gauges, Space Analyzer, Recycle Bin (`.arc_trash`), Bookmarks, and `.zip` inspection/extraction.
- [x] (`0.2.6-alpha`) 3-dot overflow **Grid Size (2–4)** & **Original Ratio** controls, flexible staggered media grid with lazy `MediaMetadataRetriever` video frames, and in-app original-ratio image zoom viewer & `TextureView`/`MediaPlayer` video player.
- [x] (`0.2.7-alpha`) Unified `#2173E5` Aesthetic Blue base palette across Light/Dark headers & icons, plus custom adaptive + raster launcher icon suite.
- [x] (`0.2.8-alpha`) Uniform `1:1` Square grid cards (preventing ragged masonry rows), proportional concentric border radii (`GridCardMetrics`) across `2–4+` columns, and responsive drawer/storage cards on narrow viewports.
- [x] (`0.2.9-alpha`) Replaced Settings header shortcut with **Tab Manager** open-tab dropdown switcher and redesigned **Breadcrumbs** as a compact flat borderless trail.
- [x] (`0.3.0-alpha`) Added explicit **Sort Order (`Asc`/`Desc`)**, **Group By (`Type`/`Date`)** headers across List & Square Grid views, real-time **Room** per-folder layout persistence (`ArcDatabase`), and automatic DB cleanup on folder rename, delete, move, or trash.

## Phase 2 — Rich Media & Productivity (`0.4.1-beta`) [Current]
- [x] Video thumbnail extraction (`MediaMetadataRetriever`) in `ArcThumbnail` & `FileGridCard`.
- [x] iOS-inspired `1.27 : 1` folder silhouette proportions & minimal Batch Rename modal + Inline Rename.
- [x] Promoted to **Beta (`0.4.1-beta`, `versionCode = 14`)** with R8 minification, resource shrinking, dynamic UI version sync, Light default theme, and smooth animated Light/Dark/Auto theme transitions.
- [x] File Tagging system (`ArcDatabase`), floating batch multi-select bottom bar, and clean `EmptyBrowserState` preserved across readable empty folders.
- [x] Built-in Android `PackageInstaller` APK installation flow, SAF external view staging, broad MIME fallback matching, and explicit "No App Installed" feedback.
- [x] Multi-DPI adaptive scaling (`ArcDimens.forWidth`), DPI-bucketed thumbnail decoding, and zero-allocation `ModifierNodeElement` drag-drop for 0-lag scrolling.
- [x] Batch rename tool (numbers only with start index/padding, prefix/postfix, replace original name) registered in `FileActionRegistry`.
- [x] Modern `DocumentViewerDialog` with **Preview-first** Markdown & HTML rendering, Source toggle, inline code editing, and a **Built-in Viewer vs External App** chooser & Settings toggle.
- [ ] Audio mini-player preview inside `FilePreviewDialog`.
- [ ] Split-pane / dual-tab side-by-side mode on `Expanded` tablet screens.

## Phase 4 — Beta Hardening (`0.9-beta`)
- [ ] Background WorkManager notification progress for large multi-file Copy/Move jobs.
- [ ] Full Robolectric UI test coverage for every registered tab destination and file action.
- [ ] Performance profiling on directories with 5,000+ SAF documents.
