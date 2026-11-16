use crate::db::{unix_now, Db, Track};
use image::{imageops::FilterType, GenericImageView};
use lofty::file::{AudioFile, FileType};
use lofty::prelude::{Accessor, ItemKey, TaggedFileExt};
use rayon::prelude::*;
use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::Arc;
use tauri::{AppHandle, Emitter};
use walkdir::WalkDir;

#[derive(Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ScanProgress {
    pub current: usize,
    pub total: usize,
}

const AUDIO_EXTS: &[&str] = &[
    "mp3", "flac", "wav", "m4a", "aac", "alac", "ogg", "opus", "aiff", "aif", "wma",
];

const FOLDER_COVER_NAMES: &[&str] = &[
    "cover.jpg",
    "cover.png",
    "cover.webp",
    "folder.jpg",
    "folder.png",
    "front.jpg",
    "front.png",
    "album.jpg",
    "album.png",
];

pub fn is_audio_path(path: &Path) -> bool {
    path.extension()
        .and_then(|e| e.to_str())
        .map(|ext| AUDIO_EXTS.contains(&ext.to_ascii_lowercase().as_str()))
        .unwrap_or(false)
}

pub fn scan_library(
    db: &Db,
    art_dir: &Path,
    app: Option<&AppHandle>,
    cancel: Option<&Arc<AtomicBool>>,
) -> Result<usize, String> {
    let _ = fs::create_dir_all(art_dir);
    let folders = db.get_folders()?;
    let existing = db.get_modified_map()?;

    let mut discovered: Vec<PathBuf> = Vec::new();
    let mut seen_paths: HashSet<String> = HashSet::new();

    for folder in &folders {
        let root = Path::new(folder);
        if !root.exists() {
            continue;
        }
        for entry in WalkDir::new(root).follow_links(true).into_iter().flatten() {
            let p = entry.path();
            if p.is_file() && is_audio_path(p) {
                let s = p.to_string_lossy().to_string();
                if seen_paths.insert(s) {
                    discovered.push(p.to_path_buf());
                }
            }
        }
    }

    let stale_paths: Vec<String> = existing
        .keys()
        .filter(|p| !seen_paths.contains(*p))
        .cloned()
        .collect();

    let to_scan: Vec<PathBuf> = discovered
        .into_iter()
        .filter(|p| {
            let key = p.to_string_lossy().to_string();
            let Ok(meta) = fs::metadata(p) else {
                return false;
            };
            let modified = meta
                .modified()
                .ok()
                .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                .map(|d| d.as_secs() as i64)
                .unwrap_or(0);
            let size = meta.len() as i64;
            match existing.get(&key) {
                Some(&(prev_mod, prev_size)) => prev_mod != modified || prev_size != size,
                None => true,
            }
        })
        .collect();

    let total = to_scan.len();
    let done = Arc::new(AtomicUsize::new(0));
    let now = unix_now();

    if let Some(a) = app {
        let _ = a.emit("scan-progress", ScanProgress { current: 0, total });
    }

    let parsed: Vec<Track> = to_scan
        .par_iter()
        .filter_map(|p| {
            if cancel.map(|c| c.load(Ordering::Relaxed)).unwrap_or(false) {
                return None;
            }
            let track = read_track_metadata(p, art_dir, now);
            let count = done.fetch_add(1, Ordering::Relaxed) + 1;
            if let Some(a) = app {
                if count % 16 == 0 || count == total {
                    let _ = a.emit("scan-progress", ScanProgress { current: count, total });
                }
            }
            track
        })
        .collect();

    db.upsert_tracks_batch(&parsed, &stale_paths)?;

    if let Some(a) = app {
        let _ = a.emit(
            "scan-progress",
            ScanProgress {
                current: total,
                total,
            },
        );
    }

    Ok(parsed.len())
}

pub fn read_track_metadata(path: &Path, art_dir: &Path, added_at: i64) -> Option<Track> {
    let meta = fs::metadata(path).ok()?;
    let modified = meta
        .modified()
        .ok()
        .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0);
    let file_size = meta.len() as i64;
    let stem = path
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("Unknown Track");

    let (fallback_artist, fallback_title) = parse_filename_fallback(stem);

    let tagged = lofty::read_from_path(path).ok();
    let mut title = fallback_title;
    let mut artist = fallback_artist;
    let mut album = String::from("Unknown Album");
    let mut album_artist = String::new();
    let mut genre = String::new();
    let mut year: Option<i32> = None;
    let mut track_no: Option<i32> = None;
    let mut disc_no: Option<i32> = None;
    let mut duration = 0.0_f64;
    let mut bitrate: Option<i32> = None;
    let mut sample_rate: Option<i32> = None;
    let mut bit_depth: Option<i32> = None;
    let mut channels: Option<i32> = None;
    let codec: Option<String>;
    let mut art_hash: Option<String> = None;
    let mut art_color: Option<String> = None;

    if let Some(tf) = tagged {
        let props = tf.properties();
        duration = props.duration().as_secs_f64();
        bitrate = props.audio_bitrate().or_else(|| props.overall_bitrate()).map(|v| v as i32);
        sample_rate = props.sample_rate().map(|v| v as i32);
        bit_depth = props.bit_depth().map(|v| v as i32);
        channels = props.channels().map(|v| v as i32);
        codec = Some(format_codec(tf.file_type(), path));

        if let Some(tag) = tf.primary_tag().or_else(|| tf.first_tag()) {
            if let Some(t) = tag.title().map(|s| s.trim().to_string()).filter(|s| !s.is_empty()) {
                title = t;
            }
            if let Some(a) = tag.artist().map(|s| s.trim().to_string()).filter(|s| !s.is_empty()) {
                artist = a;
            }
            if let Some(al) = tag.album().map(|s| s.trim().to_string()).filter(|s| !s.is_empty()) {
                album = al;
            }
            if let Some(aa) = tag
                .get_string(&ItemKey::AlbumArtist)
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
            {
                album_artist = aa;
            }
            if let Some(g) = tag.genre().map(|s| s.trim().to_string()).filter(|s| !s.is_empty()) {
                genre = g;
            }
            year = tag.year().map(|y| y as i32);
            track_no = tag.track().map(|t| t as i32);
            disc_no = tag.disk().map(|d| d as i32);

            if let Some(pic) = tag.pictures().first() {
                if let Some((hash, color)) = cache_art_bytes(pic.data(), art_dir) {
                    art_hash = Some(hash);
                    art_color = color;
                }
            }
        }
    } else {
        codec = Some(format_ext(path));
    }

    if album_artist.is_empty() {
        album_artist = artist.clone();
    }

    if art_hash.is_none() {
        if let Some(folder_art) = find_folder_cover(path) {
            if let Ok(bytes) = fs::read(folder_art) {
                if let Some((hash, color)) = cache_art_bytes(&bytes, art_dir) {
                    art_hash = Some(hash);
                    art_color = color;
                }
            }
        }
    }

    Some(Track {
        path: path.to_string_lossy().to_string(),
        title,
        artist,
        album,
        album_artist,
        genre,
        year,
        track_no,
        disc_no,
        duration,
        bitrate,
        sample_rate,
        bit_depth,
        channels,
        codec,
        file_size,
        art_hash,
        art_color,
        modified,
        added_at,
        liked: false,
        play_count: 0,
    })
}

pub fn cache_art_bytes(bytes: &[u8], art_dir: &Path) -> Option<(String, Option<String>)> {
    if bytes.is_empty() {
        return None;
    }
    let hash = format!("{:016x}", fnv1a_64(bytes));
    let full_path = art_dir.join(format!("{hash}.jpg"));
    let thumb_path = art_dir.join(format!("{hash}_thumb.jpg"));

    let img = image::load_from_memory(bytes).ok()?;
    let color = extract_accent_hex(&img);

    if !full_path.exists() {
        let (w, h) = img.dimensions();
        let full = if w > 1200 || h > 1200 {
            img.resize(1200, 1200, FilterType::Triangle)
        } else {
            img.clone()
        };
        let _ = full.to_rgb8().save_with_format(&full_path, image::ImageFormat::Jpeg);
    }

    if !thumb_path.exists() {
        let thumb = img.resize(400, 400, FilterType::Triangle);
        let _ = thumb
            .to_rgb8()
            .save_with_format(&thumb_path, image::ImageFormat::Jpeg);
    }

    Some((hash, color))
}

fn find_folder_cover(audio_path: &Path) -> Option<PathBuf> {
    let dir = audio_path.parent()?;
    for name in FOLDER_COVER_NAMES {
        let candidate = dir.join(name);
        if candidate.is_file() {
            return Some(candidate);
        }
    }
    None
}

fn format_codec(ft: FileType, path: &Path) -> String {
    match ft {
        FileType::Mpeg => "MP3".into(),
        FileType::Flac => "FLAC".into(),
        FileType::Wav => "WAV".into(),
        FileType::Mp4 => "AAC / M4A".into(),
        FileType::Vorbis => "OGG Vorbis".into(),
        FileType::Opus => "Opus".into(),
        FileType::Aiff => "AIFF".into(),
        FileType::WavPack => "WavPack".into(),
        _ => format_ext(path),
    }
}

fn format_ext(path: &Path) -> String {
    path.extension()
        .and_then(|s| s.to_str())
        .map(|s| s.to_ascii_uppercase())
        .unwrap_or_else(|| "AUDIO".into())
}

pub fn parse_filename_fallback(stem: &str) -> (String, String) {
    let mut cleaned = stem.trim().to_string();
    // Strip leading track numbers like "01. ", "01 - "
    if let Some(pos) = cleaned.find(|c: char| !c.is_ascii_digit()) {
        if pos > 0 && pos <= 3 {
            let rest = cleaned[pos..].trim_start();
            if let Some(after_sep) = rest
                .strip_prefix('.')
                .or_else(|| rest.strip_prefix('-'))
                .map(str::trim_start)
            {
                if !after_sep.is_empty() {
                    cleaned = after_sep.to_string();
                }
            }
        }
    }
    if let Some((left, right)) = cleaned.split_once(" - ") {
        let a = left.trim();
        let t = right.trim();
        if !a.is_empty() && !t.is_empty() {
            return (a.to_string(), t.to_string());
        }
    }
    ("Unknown Artist".into(), cleaned)
}

fn extract_accent_hex(img: &image::DynamicImage) -> Option<String> {
    let small = img.resize_exact(32, 32, FilterType::Nearest).to_rgb8();
    let mut best_score = 0.0_f32;
    let mut best_rgb = (245u8, 130u8, 62u8);

    for px in small.pixels() {
        let r = px[0] as f32 / 255.0;
        let g = px[1] as f32 / 255.0;
        let b = px[2] as f32 / 255.0;
        let max = r.max(g).max(b);
        let min = r.min(g).min(b);
        let l = (max + min) * 0.5;
        let d = max - min;
        if d < 0.12 || l < 0.15 || l > 0.85 {
            continue;
        }
        let s = d / (1.0 - (2.0 * l - 1.0).abs().max(0.001));
        let score = s * (1.0 - (l - 0.55).abs());
        if score > best_score {
            best_score = score;
            best_rgb = (px[0], px[1], px[2]);
        }
    }

    if best_score <= 0.0 {
        return None;
    }

    // Clamp brightness so extracted accent stays readable on dark backgrounds
    let (mut r, mut g, mut b) = (
        best_rgb.0 as f32 / 255.0,
        best_rgb.1 as f32 / 255.0,
        best_rgb.2 as f32 / 255.0,
    );
    let lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
    if lum < 0.35 {
        let boost = (0.42 / lum.max(0.05)).min(2.2);
        r = (r * boost).min(1.0);
        g = (g * boost).min(1.0);
        b = (b * boost).min(1.0);
    }

    Some(format!(
        "#{:02x}{:02x}{:02x}",
        (r * 255.0).round() as u8,
        (g * 255.0).round() as u8,
        (b * 255.0).round() as u8
    ))
}

fn fnv1a_64(data: &[u8]) -> u64 {
    let mut hash: u64 = 0xcbf29ce484222325;
    for &b in data {
        hash ^= b as u64;
        hash = hash.wrapping_mul(0x100000001b3);
    }
    hash
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn filename_fallback_strips_track_num_and_splits_artist() {
        assert_eq!(
            parse_filename_fallback("02 - Egzod & Maestro Chives - Royalty"),
            ("Egzod & Maestro Chives".into(), "Royalty".into())
        );
        assert_eq!(
            parse_filename_fallback("Midnight City"),
            ("Unknown Artist".into(), "Midnight City".into())
        );
    }
}
