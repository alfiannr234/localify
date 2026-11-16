use lofty::prelude::{ItemKey, TaggedFileExt};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LyricsResult {
    pub source: String,
    pub format: String, // "ttml" | "lrc" | "krc" | "txt"
    pub content: String,
    pub path: Option<String>,
}

pub fn load_local_lyrics(track_path: &str, cache_dir: &Path) -> Option<LyricsResult> {
    let p = Path::new(track_path);
    let stem = p.file_stem()?.to_string_lossy();
    let dir = p.parent()?;

    // 1. User-cached custom/downloaded lyrics in app cache
    let hash_name = format!("{:016x}", fnv1a_64(track_path.as_bytes()));
    for (ext, fmt) in [("ttml", "ttml"), ("krc", "krc"), ("lrc", "lrc"), ("txt", "txt")] {
        let cached = cache_dir.join(format!("{hash_name}.{ext}"));
        if cached.is_file() {
            if let Ok(content) = fs::read_to_string(&cached) {
                if !content.trim().is_empty() {
                    return Some(LyricsResult {
                        source: "cache".into(),
                        format: fmt.into(),
                        content,
                        path: Some(cached.to_string_lossy().into_owned()),
                    });
                }
            }
        }
    }

    // 2. Sidecar files alongside the audio file
    for (ext, fmt) in [("ttml", "ttml"), ("krc", "krc"), ("lrc", "lrc"), ("txt", "txt")] {
        let candidate = dir.join(format!("{stem}.{ext}"));
        if candidate.is_file() {
            if let Ok(content) = fs::read_to_string(&candidate) {
                if !content.trim().is_empty() {
                    return Some(LyricsResult {
                        source: "sidecar".into(),
                        format: fmt.into(),
                        content,
                        path: Some(candidate.to_string_lossy().into_owned()),
                    });
                }
            }
        }
    }

    // 3. Embedded tag lyrics via Lofty
    if let Ok(tf) = lofty::read_from_path(p) {
        if let Some(tag) = tf.primary_tag().or_else(|| tf.first_tag()) {
            if let Some(lyrics_raw) = tag.get_string(&ItemKey::Lyrics) {
                let trimmed = lyrics_raw.trim();
                if !trimmed.is_empty() {
                    let fmt = detect_format(trimmed);
                    return Some(LyricsResult {
                        source: "embedded".into(),
                        format: fmt.into(),
                        content: trimmed.to_string(),
                        path: None,
                    });
                }
            }
        }
    }

    None
}

pub fn cache_lyrics_for_track(
    track_path: &str,
    format: &str,
    content: &str,
    cache_dir: &Path,
) -> Result<PathBuf, String> {
    fs::create_dir_all(cache_dir).map_err(|e| e.to_string())?;
    let hash_name = format!("{:016x}", fnv1a_64(track_path.as_bytes()));
    for old_ext in ["ttml", "krc", "lrc", "txt"] {
        let old = cache_dir.join(format!("{hash_name}.{old_ext}"));
        if old.exists() {
            let _ = fs::remove_file(old);
        }
    }
    let clean_ext = match format.to_ascii_lowercase().as_str() {
        "ttml" => "ttml",
        "krc" => "krc",
        "txt" => "txt",
        _ => "lrc",
    };
    let dest = cache_dir.join(format!("{hash_name}.{clean_ext}"));
    fs::write(&dest, content).map_err(|e| e.to_string())?;
    Ok(dest)
}

pub fn assign_custom_lyrics(
    track_path: &str,
    lyrics_file: &str,
    cache_dir: &Path,
) -> Result<LyricsResult, String> {
    let p = Path::new(lyrics_file);
    let content = fs::read_to_string(p).map_err(|e| format!("Cannot read lyrics file: {e}"))?;
    let ext = p
        .extension()
        .and_then(|s| s.to_str())
        .map(|s| s.to_ascii_lowercase())
        .unwrap_or_else(|| detect_format(&content).to_string());
    let fmt = match ext.as_str() {
        "ttml" => "ttml",
        "krc" => "krc",
        "lrc" => "lrc",
        _ => detect_format(&content),
    };
    let saved = cache_lyrics_for_track(track_path, fmt, &content, cache_dir)?;
    Ok(LyricsResult {
        source: "custom".into(),
        format: fmt.into(),
        content,
        path: Some(saved.to_string_lossy().into_owned()),
    })
}

pub fn detect_format(content: &str) -> &'static str {
    let trim = content.trim_start();
    if trim.starts_with("<?xml") || trim.starts_with("<tt") || trim.contains("<body") {
        "ttml"
    } else if trim.contains('[') && trim.lines().any(|l| l.starts_with('[') && l.contains(':')) {
        if trim.lines().any(|l| l.starts_with('[') && l.contains(',') && l.contains('<')) {
            "krc"
        } else {
            "lrc"
        }
    } else {
        "txt"
    }
}

fn fnv1a_64(data: &[u8]) -> u64 {
    let mut hash: u64 = 0xcbf29ce484222325;
    for &b in data {
        hash ^= b as u64;
        hash = hash.wrapping_mul(0x100000001b3);
    }
    hash
}
