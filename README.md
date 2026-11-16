<p align="center">
  <img src="localify.png" width="128" height="128" alt="Localify Logo" />
</p>

<h1 align="center">Localify</h1>

<p align="center">
  <strong>Lightweight desktop music player built with TypeScript, Svelte 5, Tauri 2, and Rust.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Rust-Pure%20Audio%20%2B%20Tauri%202-F5823E?style=for-the-badge&logo=rust&logoColor=white" alt="Rust + Tauri 2" />
  <img src="https://img.shields.io/badge/TypeScript-Svelte%205-3178C6?style=for-the-badge&logo=typescript&logoColor=white" alt="TypeScript + Svelte 5" />
  <img src="https://img.shields.io/badge/Platform-Windows-0078D6?style=for-the-badge&logo=windows&logoColor=white" alt="Windows" />
  <img src="https://img.shields.io/badge/Discord-Rich%20Presence-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord RPC" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="MIT License" />
</p>

---

## Overview

**Localify** is a fast, compact local music player rewritten from the ground up with **Tauri 2**, **Svelte 5**, **TypeScript**, and a **pure Rust** backend. It decodes audio directly in native Rust with zero external media runtime dependencies (~10-15 MB binary), keeps lyrics tightly synced to the hardware sample clock, and migrates your existing Localify v1 offsets and `.m3u8` playlists automatically.

---

## Key Features

- **Pure Rust Audio Engine & Biquad EQ**: Decodes `MP3`, `FLAC`, `WAV`, `M4A / AAC / ALAC`, `OGG Vorbis`, `Opus`, `AIFF`, and `WavPack` via `rodio` + `symphonia` with selectable WASAPI output devices and a real-time 10-band RBJ biquad equalizer + preamp.
- **Word-Synced, Ordered & Translated Lyrics**: Loads local `.ttml`, `.krc`, `.lrc`, `.txt`, and embedded tags, plus **Navidrome / Subsonic**, **LRCLIB**, **NetEase**, and **Kugou** in the exact order you configure with an optional word-timing preference and line-by-line translation underneath each line.
- **Learn Mode & Vocabulary Review**: Tap any word in lyrics for dictionary meanings (plus Japanese `<ruby>` furigana and Hepburn romaji) and review saved words with Leitner spaced repetition flashcards and one-click lyric line replay.
- **Qur'an Recitation Recognition**: Automatically recognises surahs, Juz 'Amma, Juz Tabarak, Hafs, and Shu'bah recitations by title; aligns ayahs to recitation pauses; shows Uthmani text with word meanings, transliteration, Kemenag, Saheeh International, and Clear Quran translations; and saves words to Vocabulary.
- **Downloads**: Paste video, song, playlist, or channel URLs to save original audio, MP3, 1080p, or best video directly into your library folder using official GitHub releases of `yt-dlp` and `ffmpeg` verified against published SHA-256 checksums.
- **Per-Track Lyrics Offset**: Stores individual timing adjustments per song in SQLite with direct text editing and `-0.1s` / `+0.1s` controls.
- **Fast Library & Batch Workflow**: Parallel metadata/artwork indexer with SQLite WAL persistence, automatic folder watching, virtualized song list with `A-Z` jump rail, multi-select batch play/queue actions, `.m3u8` import/export, and tag + technical properties editor.
- **Desktop Integrations**: Windows SMTC media controls with lock-screen artwork, frameless always-on-top MiniPlayer, multi-theme Obsidian + Warm Zest palette with adaptive cover accent, and Discord Rich Presence (`Listening` activity with live cover lookup).

---

## Technology Stack

| Component | Technology | Purpose |
| :--- | :--- | :--- |
| **Backend Runtime** | Rust + Tauri 2 | Single-binary Windows host, SQLite index, filesystem watcher |
| **Audio & DSP** | `rodio` + `symphonia` + `cpal` | Zero-DLL audio decoding, sample-accurate clock, 10-band biquad EQ |
| **Frontend UI** | TypeScript + Svelte 5 + Vite | Reactive state, virtualized song lists, SVG EQ curve, word-sweep lyrics |
| **Metadata & Covers** | `lofty` + `image` | Tag reading/writing, thumbnail caching, adaptive artwork accent |
| **Desktop Hooks** | `souvlaki` + Windows Named Pipes | Windows SMTC transport controls and Discord Rich Presence IPC |
| **Packaging** | Tauri + Inno Setup 6 | Compact Windows installer (`Localify-Setup-1.0.exe`) |

---

## Build and Installation

### Prerequisites
- **Rust** (`rustup` / `cargo`)
- **Node.js 20+** and **npm**
- **WebView2 Runtime** (pre-installed on Windows 10/11)

### 1. Run in Development Mode
```bash
npm install
npm run dev
```
Or launch full Tauri desktop development window:
```bash
npm run tauri dev
```

### 2. Run Checks and Tests
```bash
npm run check
npm test
cargo test --manifest-path src-tauri/Cargo.toml
```

### 3. Build Release Binary & Windows Installer
```bash
npm run tauri build -- --no-bundle
.\build-installer.bat
```
For in-place updates of `%LOCALAPPDATA%\Programs\Localify`:
```bash
.\update-installed-app.bat
```

---

## Project Structure

```text
localify/
├── DESIGN.md                 # Visual tokens, contrast palette, and layout guidelines
├── src/                      # TypeScript & Svelte 5 frontend (views, components, stores, tests)
├── src-tauri/                # Rust backend (audio engine, DB, scanner, lyrics, SMTC, Discord RPC)
├── scripts/                  # SVG icon bundler
├── build-installer.bat       # Build release executable + Inno Setup installer
├── update-installed-app.bat  # In-place update for installed Localify binary
├── localify-installer.iss    # Inno Setup script
├── localify.ico              # Multi-resolution Windows icon
└── localify.png              # Application logo
```

---

## License

This project is licensed under the [MIT License](LICENSE).
