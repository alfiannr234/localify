<p align="center">
  <img src="localify.png" width="128" height="128" alt="Localify Logo" />
</p>

<h1 align="center">Localify</h1>

<p align="center">
  <strong>Desktop audio player built with JavaFX featuring synchronized lyrics, Mini Player mode, and Discord Rich Presence.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17+" />
  <img src="https://img.shields.io/badge/JavaFX-21-FF6F00?style=for-the-badge&logo=java&logoColor=white" alt="JavaFX 21" />
  <img src="https://img.shields.io/badge/Platform-Windows-0078D6?style=for-the-badge&logo=windows&logoColor=white" alt="Windows" />
  <img src="https://img.shields.io/badge/Discord-Rich%20Presence-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord RPC" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="MIT License" />
</p>

---

## Overview

**Localify** is a local desktop music player built with **JavaFX 21** and a clean interface inspired by modern streaming players. It scans local audio libraries, displays time-synced lyrics, updates your Discord Rich Presence status in real time, and switches seamlessly between full library view and a compact floating Mini Player.

---

## Screenshot

<p align="center">
  <img src="Screenshot%202026-11-12%20065518.png" alt="Localify Your Library View" width="100%" />
</p>

---

## Key Features

- **Automatic Library Scanning**: Scans `.mp3`, `.flac`, `.wav`, and `.m4a` audio files from the user's `Music` folder or a custom directory, extracting ID3 tags and embedded cover artwork with JAudioTagger.
- **Synchronized Lyrics (.lrc)**:
  - Smooth auto-scrolling lyrics with active line highlighting.
  - Automatic transliteration to Romaji for Japanese and Asian tracks.
  - On-the-fly online `.lrc` lookup via LRCLIB when no local lyrics file is found.
  - Manual `.lrc` file assignment directly from each track's right-click context menu.
- **Discord Rich Presence (RPC)**: Displays current track title, artist, album, and live playback timestamps on your Discord profile over local IPC.
- **Mini Player Mode**: Frameless, always-on-top compact window with seek bar, volume slider, lyrics toggle, and an upcoming queue view.
- **Themes and Accent Customization**: Switch between **Dark**, **Light**, **Nord**, and **Dracula** themes with customizable accent colors (Orange, Green, Blue, Purple, Crimson, and more).
- **Fast Navigation and Batch Controls**: Browse by Songs, Albums, Artists, or Playlists; jump alphabetically with the Section Jump Picker (`&`, `#`, `A-Z`); and select multiple tracks for batch playback.
- **Multi-Column Properties Inspector**: View song metadata alongside audio encoding details (format, bitrate, sample rate, channels, size, and modified timestamp) side by side.
- **Playlist Persistence**: Create and manage playlists stored in standard M3U8 format.

---

## Technology Stack

| Component | Technology | Purpose |
| :--- | :--- | :--- |
| **Language & Runtime** | Java 17+ (JDK 21 / 25) | Core application logic and NIO file scanning |
| **GUI & Audio Engine** | JavaFX 21 (`controls`, `fxml`, `media`) | User interface rendering and media playback |
| **UI Styling** | AtlantaFX 2.0.1 | Base theme foundation and custom CSS stylesheets |
| **Vector Icons** | Ikonli (`materialdesign2`) | Material Design icons across player controls and menus |
| **Metadata Parser** | JAudioTagger 3.0.1 | ID3 tag extraction, embedded cover art, and audio technical info |
| **Discord Integration** | Discord RPC (IPC / Named Pipes) | Real-time status updates with the desktop Discord client |
| **Windows Packaging** | `jpackage` & Inno Setup 6 | Self-contained Windows installer (`Localify-Setup-1.0.exe`) |

---

## Build and Installation

### Prerequisites
- **JDK 17 or newer** (JDK 21 LTS recommended)
- Git

### 1. Run in Development Mode
```bash
# Clone the repository
git clone https://github.com/Alfiannr234/localify.git
cd localify

# Run using the Maven Wrapper
.\mvnw.cmd clean javafx:run
```

### 2. Build Shaded Executable JAR
```bash
.\mvnw.cmd clean package -DskipTests
```
The compiled shaded JAR is output to `target/localify-player-1.0.jar`.

### 3. Build Windows Installer (.exe)
Run the automated build script to compile the JAR, create the `jpackage` app-image, and package the Inno Setup installer:
```bash
.\build-installer.bat
```
The generated installer is placed at `dist/installer/Localify-Setup-1.0.exe`.

### 4. Fast In-Place Update (Installed App)
If Localify is already installed locally and you want to update the JAR and executable without reinstalling:
```bash
.\update-installed-app.bat
```

---

## Project Structure

```text
localify/
├── .mvn/                             # Maven wrapper configuration
├── src/
│   ├── main/
│   │   ├── java/com/localify/
│   │   │   ├── App.java              # JavaFX application entry point
│   │   │   ├── controller/           # FXML controllers (Main, MiniPlayer, ContextMenu)
│   │   │   ├── model/                # Data models (Track, LyricLine, Playlist)
│   │   │   └── service/              # Core services (Audio, Scanner, LrcParser, Discord, Theme)
│   │   └── resources/com/localify/
│   │       ├── css/                  # Application themes (spotify-dark.css)
│   │       ├── fxml/                 # UI layouts (main-view.fxml, mini-player.fxml)
│   │       └── images/               # Multi-resolution application icons
│   └── test/                         # UI and service smoke verification runner
├── Screenshot 2026-11-12 065518.png  # Application library view screenshot
├── build-installer.bat               # Build script for JAR, app-image, and installer
├── update-installed-app.bat          # In-place update script for installed builds
├── localify-installer.iss            # Inno Setup installer configuration
├── localify.ico                      # Multi-resolution Windows icon
├── localify.png                      # High-resolution application logo
├── LICENSE                           # MIT License
├── pom.xml                           # Maven project configuration
└── README.md                         # Project documentation
```

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
