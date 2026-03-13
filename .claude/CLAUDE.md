# FluxDM Project Memory

## Architecture
- Java 21 Swing download manager, Maven build, package `com.fluxdm`
- Main entry: `Main.java` → `ThemeManager.init()` → `FluxDMFrame`
- 9 source files in `src/main/java/com/fluxdm/`:
  - `Main` — entry point
  - `FluxDMFrame` — main window, sidebar, toolbar, table, status bar, theme toggle button
  - `AddDownloadDialog` — modal dialog for adding URLs, clipboard detection, YouTube quality picker
  - `DownloadTask` — core download engine: HTTP streaming + yt-dlp + ffmpeg merge + MP3
  - `ThemeManager` — dark/light theme engine (FlatLaf), color accessors, factory methods
  - `DownloadTableModel` — JTable data model
  - `ProgressCellRenderer` — custom progress bar cell renderer
  - `ActionCellHandler` — pause/resume/open/remove action buttons in table
  - `Formatter` — bytes/speed/ETA formatting utilities
- Fat JAR via maven-shade-plugin (classifier=fat), thin JAR also produced
- Assembly plugin creates dist ZIP + tar.gz

## Theme System
- `ThemeManager.java` replaced old `DarkTheme.java` (deleted)
- FlatLaf 3.5.4: `FlatDarkLaf` / `FlatLightLaf`
- Color accessors are **static methods** (not fields) — return theme-appropriate values dynamically
- Key colors: `bgDarkest()`, `bgDark()`, `bgMedium()`, `accentBlue()`, `textPrimary()`, `green()`, `red()`, `border()`, `gridColor()`, `speedBg()`, `completedRow()`, etc.
- `init()` reads Preferences, applies FlatLaf L&F
- `toggle()` flips dark↔light, persists, calls `SwingUtilities.updateComponentTreeUI()`, notifies listeners
- `addThemeChangeListener(Runnable)` — FluxDMFrame registers `rebuildUI()`
- Factory methods: `createButton(text, bg)`, `createTextField(placeholder)`, `card(bg)`

## YouTube Downloads
- Uses yt-dlp with optional ffmpeg for merging/conversion
- **MP3 support** (with ffmpeg): `--extract-audio --audio-format mp3 --audio-quality 0`
- Without ffmpeg: audio falls back to m4a (format 140), shows red warning in dialog
- Without ffmpeg: video capped at 720p (format 22/18 pre-muxed)
- With ffmpeg: video gets best quality via separate streams + merge
- Parses yt-dlp output: `[download] Destination:`, `[ExtractAudio] Destination:`, `[Merger] Merging formats into`
- `mergeIfNeeded()` handles leftover split streams — skipped for audio-only
- `buildFmtFfmpeg(quality)` builds format selector string for video qualities
- `findFfmpeg()` checks Homebrew paths, PATH, common locations, `which ffmpeg`

## Build & Run
- `mvn clean package` — produces thin JAR + fat JAR + dist archives
- `mvn exec:java` — run directly
- FlatLaf 3.5.4 bundled as runtime dependency
- Test deps: JUnit 5, AssertJ, Mockito (test scope only)
- Java 21 required for build, 11+ for running

## Key Patterns
- All DarkTheme refs were replaced with ThemeManager method calls (not fields)
- FluxDMFrame.table is non-final (rebuilt on theme toggle)
- AddDownloadDialog uses theme-aware colors for panels (info, YouTube, clipboard badge, quality toggles)
- Emoji/unicode chars used for icons throughout (no image assets)
- Preferences API used for both theme persistence and save path