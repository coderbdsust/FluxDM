# Session Notes — 2026-03-13

## Task
Implement three major features for FluxDM:
1. Light theme with dark/light toggle
2. FlatLaf integration for professional Swing rendering
3. YouTube MP3 download support

## What Was Done

### 1. FlatLaf Dependency (`pom.xml`)
- Added `com.formdev:flatlaf:3.5.4` as runtime dependency
- Shade plugin bundles it into the fat JAR (363 classes)

### 2. Created `ThemeManager.java` (new file)
- Replaced `DarkTheme.java` as the central theme engine
- Dual palettes: dark (original colors) + light (new matching equivalents)
- Static method color accessors (`bgDarkest()`, `textPrimary()`, etc.) for dynamic switching
- `init()` loads saved preference, applies FlatDarkLaf or FlatLightLaf
- `toggle()` switches theme, persists to Preferences, updates all windows, notifies listeners
- Carries over factory methods: `createButton()`, `createTextField()`, `card()`

### 3. Deleted `DarkTheme.java`
- All ~90 references across 5 files migrated to `ThemeManager.xxx()` calls

### 4. Updated `Main.java`
- `DarkTheme.apply()` → `ThemeManager.init()`

### 5. Updated `FluxDMFrame.java` (largest change)
- ~45 `DarkTheme.XXX` refs → `ThemeManager.xxx()` method calls
- `table` field changed from `final` to non-final (rebuilt on theme toggle)
- Added sun/moon theme toggle button in title bar
- Added `rebuildUI()` method registered as theme change listener
- Hardcoded colors (speed badge bg, completed row bg) replaced with ThemeManager methods

### 6. Updated `AddDownloadDialog.java`
- ~30 `DarkTheme.XXX` refs → `ThemeManager.xxx()` calls
- Panel colors (info, YouTube, clipboard badge, quality toggles) are now theme-aware
- ffmpeg hint text updated to mention MP3 conversion
- Red warning label shown when "Audio Only (MP3)" selected without ffmpeg

### 7. Updated `ProgressCellRenderer.java`
- 8 DarkTheme refs replaced with ThemeManager calls

### 8. Updated `ActionCellHandler.java`
- 5 DarkTheme refs replaced with ThemeManager calls

### 9. YouTube MP3 Support (`DownloadTask.java`)
- Audio Only + ffmpeg: uses `--extract-audio --audio-format mp3 --audio-quality 0`
- Audio Only without ffmpeg: falls back to m4a (format 140)
- Predicted filename uses `.mp3` extension when ffmpeg is available
- Added `[ExtractAudio] Destination:` output parser for tracking converted file
- `mergeIfNeeded()` skipped for audio-only downloads
- Removed "Audio Only" case from `buildFmtFfmpeg()` (handled separately now)

### 10. Updated `README.md`
- Reflects new theming, FlatLaf, MP3 support, updated project structure

### 11. Saved Project Memory
- `.claude/CLAUDE.md` — full project knowledge for future sessions
- `.claude/memory/MEMORY.md` — auto memory copy

## Build Status
`mvn clean package` — passes cleanly, FlatLaf bundled in fat JAR.

## Files Changed
| File | Action |
|------|--------|
| `pom.xml` | Modified — added FlatLaf dep |
| `ThemeManager.java` | **Created** |
| `DarkTheme.java` | **Deleted** |
| `Main.java` | Modified (1 line) |
| `FluxDMFrame.java` | Modified (major rewrite) |
| `AddDownloadDialog.java` | Modified (major rewrite) |
| `ProgressCellRenderer.java` | Modified |
| `ActionCellHandler.java` | Modified |
| `DownloadTask.java` | Modified (MP3 logic) |
| `README.md` | Modified |
| `.claude/CLAUDE.md` | Created |