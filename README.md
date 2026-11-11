<p align="center">
  <img src="localify.png" width="128" height="128" alt="Localify Logo" />
</p>

<h1 align="center">Localify</h1>

<p align="center">
  <strong>Modern, Elegant Desktop Audio Player with Synchronized Lyrics & Discord Rich Presence</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17+" />
  <img src="https://img.shields.io/badge/JavaFX-21-FF6F00?style=for-the-badge&logo=java&logoColor=white" alt="JavaFX 21" />
  <img src="https://img.shields.io/badge/Platform-Windows-0078D6?style=for-the-badge&logo=windows&logoColor=white" alt="Windows" />
  <img src="https://img.shields.io/badge/Discord-Rich%20Presence-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord RPC" />
  <img src="https://img.shields.io/badge/Theme-Dark%20%7C%20Light%20%7C%20Nord%20%7C%20Dracula-121212?style=for-the-badge" alt="Themes" />
</p>

---

## 🌟 Tentang Localify

**Localify** adalah aplikasi pemutar musik desktop lokal berbasis **JavaFX 21** dengan desain antarmuka modern yang terinspirasi oleh Spotify. Localify dirancang untuk memberikan pengalaman mendengarkan musik lokal terbaik dengan sinkronisasi lirik otomatis, integrasi Discord Rich Presence, mode Mini Player mengambang (*always-on-top*), serta fleksibilitas kustomisasi tema dan warna aksen.

---

## ✨ Fitur Utama

- 🎵 **Pemindaian Koleksi Audio Otomatis**: Mendukung format `.mp3`, `.flac`, `.wav`, dan `.m4a` langsung dari folder `Music` pengguna atau folder kustom pilihan.
- 🎤 **Lirik Berjalan Tersinkronisasi (`.lrc`)**:
  - Auto-scroll lirik berjalan dengan penandaan baris aktif secara halus.
  - Dukungan transliterasi Romaji otomatis untuk lagu Jepang, Korea, dan Asia.
  - Pencarian lirik online *on-the-fly* via API LRCLIB jika file lirik lokal belum tersedia.
  - Fitur *"Tambah / Ganti lirik (.lrc)..."* langsung melalui menu klik kanan pada lagu.
- 🎮 **Discord Rich Presence (RPC)**:
  - Menampilkan judul lagu, artis, album, cover art, dan durasi berjalan langsung di status profil Discord Anda secara real-time.
- 🪟 **Mode Mini Player**:
  - Jendela ringkas tanpa bingkai (*frameless / undecorated*), mengambang selalu di atas jendela lain (*always-on-top*).
  - Kontrol pemutaran lengkap: seek bar durasi, volume, preview lirik, dan daftar *Playing Next*.
- 🎨 **Sistem Tema & Aksen Warna**:
  - Tema: **Spotify Dark**, **Light**, **Nord**, dan **Dracula**.
  - Warna aksen: **Orange**, **Spotify Green**, **Ocean Blue**, **Royal Purple**, **Crimson**, dsb.
- ⚡ **Navigasi Cepat & Section Jump**:
  - Filter cepat per Artis, Album, dan Playlist.
  - *Section Jump Picker* (`&`, `#`, `A–Z`) untuk melompat langsung ke abjad lagu pilihan.
  - Fitur multi-seleksi batch dan pemutaran kelompok lagu sekaligus.
- ℹ️ **Dialog Properties Multi-Kolom**:
  - Menampilkan informasi metadata lagu dan detail teknis file audio (bitrate, sample rate, channels, encoding, size, modified date) secara berdampingan.

---

## 🛠️ Stack Teknologi

| Komponen | Teknologi | Keterangan |
| :--- | :--- | :--- |
| **Bahasa & Runtime** | Java 17+ (JDK 21 / 25) | Core logic & high-performance NIO file scanning |
| **Framework GUI** | JavaFX 21 (`controls`, `fxml`, `media`) | UI layout & cross-platform audio engine |
| **UI Components** | AtlantaFX 2.0.1 | Modern base dark/light design system |
| **Icons** | Ikonli (`materialdesign2`) | Vektor Material Design glyphs |
| **Audio Metadata** | JAudioTagger 3.0.1 | Ekstraksi ID3 tags (v1/v2), cover art, dan properties audio |
| **Discord RPC** | Discord RPC (IPC / Named Pipes) | Koneksi real-time ke Discord desktop client |
| **Installer** | Inno Setup 6 & jpackage | Pembuatan installer Windows mandiri (`.exe`) dengan JRE terpaket |

---

## 🚀 Panduan Menjalankan & Membangun (Build)

### Prasyarat
- **JDK 17 atau lebih baru** (JDK 21 LTS disarankan)
- Git

### 1. Menjalankan dalam Mode Pengembangan
```bash
# Clone repositori
git clone https://github.com/localify/localify.git
cd localify

# Jalankan via Maven Wrapper
.\mvnw.cmd clean javafx:run
```

### 2. Membangun Executable JAR
```bash
.\mvnw.cmd clean package -DskipTests
```
File fat/shaded JAR yang dapat langsung dijalankan akan dihasilkan di `target/localify-player-1.0.jar`.

### 3. Membangun Windows Installer Mandiri (`.exe`)
Proyek ini telah dilengkapi dengan script otomatis:
```bash
# Jalankan script pembuat installer lengkap
.\build-installer.bat
```
Hasil installer akan tersedia di:
`dist\installer\Localify-Setup-1.0.exe`

### 4. Pembaruan Cepat (In-Place Fast Update)
Jika Anda sudah menginstal Localify di Windows dan ingin memperbarui file executable serta JAR tanpa perlu melakukan instalasi ulang:
```bash
.\update-installed-app.bat
```

---

## 📁 Struktur Direktori Proyek

```text
localify/
├── .mvn/                             # Maven wrapper configuration
├── src/
│   ├── main/
│   │   ├── java/com/localify/
│   │   │   ├── App.java              # Entry point aplikasi JavaFX
│   │   │   ├── controller/           # Controller FXML (Main, MiniPlayer, ContextMenu)
│   │   │   ├── model/                # Model data (Track, LyricLine, Playlist)
│   │   │   └── service/              # Service (Audio, Scanner, LrcParser, Discord, Theme)
│   │   └── resources/com/localify/
│   │       ├── css/                  # Styling tema (spotify-dark.css)
│   │       ├── fxml/                 # Layout antarmuka (main-view, mini-player)
│   │       └── images/               # Multi-resolution icons & assets
│   └── test/                         # Unit & end-to-end smoke verification runner
├── build-installer.bat               # Otomasi build JAR, jpackage, & Inno Setup
├── update-installed-app.bat          # Fast in-place updater script
├── localify-installer.iss            # Skrip kompilasi Inno Setup
├── localify.ico                      # Multi-resolution application icon
├── localify.png                      # High-resolution logo
├── pom.xml                           # Definisi dependensi & build Maven
└── README.md                         # Dokumentasi proyek
```

---

## 📄 Lisensi

Didistribusikan di bawah lisensi MIT. Silakan gunakan, pelajari, dan modifikasi secara bebas.
