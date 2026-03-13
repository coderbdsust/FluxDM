# FluxDM — Download Manager v2.0.0

A fast, dark-themed Java Swing download manager with real HTTP streaming
and YouTube downloads via `yt-dlp`.

## Requirements

| Tool    | Version | Notes |
|---------|---------|-------|
| Java    | 11+     | [Adoptium](https://adoptium.net) recommended |
| yt-dlp  | latest  | Required for YouTube downloads |
| ffmpeg  | any     | Optional — enables 1080p/4K YouTube |

## Quick Start

```bash
# Run the JAR directly
java -jar FluxDM-2.0.0.jar

# macOS / Linux
./run.sh

# Windows
run.bat
```

## Install Dependencies (macOS)

```bash
# yt-dlp (required for YouTube)
brew install yt-dlp

# ffmpeg (optional — enables 1080p/4K)
brew install ffmpeg
```

Without ffmpeg, YouTube downloads are capped at **720p** (pre-muxed H.264+AAC).
With ffmpeg, you get full **1080p / 4K** with merged audio+video.

## Build from Source

### Prerequisites
- Java 21 SDK
- Maven 3.8+

### Commands

```bash
# Compile + test + package everything
mvn package

# Run directly
mvn exec:java

# Clean build
mvn clean package
```

### Build Outputs (`target/`)

| File | Description |
|------|-------------|
| `FluxDM-2.0.0.jar` | Thin JAR |
| `FluxDM-2.0.0-fat.jar` | **Executable fat JAR** ← use this |
| `FluxDM-2.0.0-dist.zip` | Distribution ZIP (JAR + launchers) |
| `FluxDM-2.0.0-dist.tar.gz` | Distribution tarball |

## Project Structure

```
fluxdm/
├── pom.xml
├── run.sh
├── run.bat
├── README.md
└── src/
    ├── assembly/
    │   └── dist.xml
    └── main/
        └── java/com/fluxdm/
            ├── Main.java                 Entry point
            ├── DownloadTask.java         Core engine: HTTP + yt-dlp + ffmpeg merge
            ├── FluxDMFrame.java          Main window
            ├── AddDownloadDialog.java    Add URL dialog
            ├── ActionCellHandler.java    Table row action buttons
            ├── DarkTheme.java            Full dark Swing theme
            ├── DownloadTableModel.java   Table data model
            ├── Formatter.java            Bytes / speed / ETA formatting
            └── ProgressCellRenderer.java Progress bar renderer
```

## Features

- Real HTTP downloads — streams bytes directly to disk with live progress
- YouTube via `yt-dlp` — real video, not simulated
- ffmpeg auto-detection — checks Homebrew, PATH, common locations
- `mergeIfNeeded()` — auto-merges leftover `.f137.mp4` + `.f140.m4a` split files
- Clipboard URL auto-detection on dialog open
- Pause / Resume / Cancel per download
- Dark theme — pure Swing, zero external UI libs, works on any JDK
- macOS Finder integration — `open -R` reveals downloaded file
- Zero runtime dependencies

## License
MIT
