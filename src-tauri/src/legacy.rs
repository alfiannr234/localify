use crate::db::{file_offset_key, path_offset_key, Db};
use std::collections::HashMap;
use std::path::{Path, PathBuf};

/// One-time import from legacy Localify v1 (`%USERPROFILE%\.localify\config.properties` and `playlists\*.m3u8`).
pub fn migrate_legacy_v1_if_needed(db: &Db) {
    if db.get_kv("legacy_v1_migrated").ok().flatten().is_some() {
        return;
    }
    let Some(home) = dirs::home_dir() else {
        let _ = db.set_kv("legacy_v1_migrated", "1");
        return;
    };
    let legacy_dir = home.join(".localify");
    if !legacy_dir.exists() {
        let _ = db.set_kv("legacy_v1_migrated", "1");
        return;
    }

    let props_path = legacy_dir.join("config.properties");
    if props_path.exists() {
        if let Ok(raw) = std::fs::read_to_string(&props_path) {
            let props = parse_java_properties(&raw);
            apply_legacy_properties(db, &props);
        }
    }

    let playlists_dir = legacy_dir.join("playlists");
    if playlists_dir.exists() {
        import_legacy_playlists(db, &playlists_dir);
    }

    let _ = db.set_kv("legacy_v1_migrated", "1");
}

fn apply_legacy_properties(db: &Db, props: &HashMap<String, String>) {
    if let Some(v) = props.get("lyrics.sync.offset") {
        if let Ok(secs) = v.trim().parse::<f64>() {
            let _ = db.set_kv("lyrics_default_offset", &format!("{secs:.2}"));
        }
    }
    if let Some(v) = props.get("discord.rpc.enabled") {
        let enabled = v.trim().eq_ignore_ascii_case("true");
        let _ = db.set_kv("discord_enabled", if enabled { "true" } else { "false" });
    }
    if let Some(v) = props.get("discord.rpc.client_id") {
        let id = v.trim();
        if !id.is_empty() {
            let _ = db.set_kv("discord_client_id", id);
        }
    }
    if let Some(v) = props.get("app.theme") {
        let theme = match v.trim().to_uppercase().as_str() {
            "LIGHT" => "light",
            _ => "dark",
        };
        if db.get_kv("theme").ok().flatten().is_none() {
            let _ = db.set_kv("theme", theme);
        }
    }

    for (key, value) in props {
        if let Some(rest) = key.strip_prefix("lyrics.track_offset.path.") {
            if let Ok(secs) = value.trim().parse::<f64>() {
                let norm = path_offset_key(rest);
                let _ = db.set_raw_track_offset(&norm, secs);
            }
        } else if let Some(rest) = key.strip_prefix("lyrics.track_offset.file.") {
            if let Ok(secs) = value.trim().parse::<f64>() {
                if let Some(fkey) = file_offset_key(rest) {
                    let _ = db.set_raw_track_offset(&fkey, secs);
                } else {
                    let _ = db.set_raw_track_offset(&format!("file:{}", rest.to_lowercase()), secs);
                }
            }
        }
    }

    // Auto-add Windows Music folder if no folders registered yet and default Music exists
    if db.get_folders().map(|f| f.is_empty()).unwrap_or(false) {
        if let Some(music) = dirs::audio_dir() {
            if music.exists() {
                let _ = db.add_folder(&music.to_string_lossy());
            }
        }
    }
}

fn import_legacy_playlists(db: &Db, playlists_dir: &Path) {
    let Ok(entries) = std::fs::read_dir(playlists_dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path: PathBuf = entry.path();
        let ext = path
            .extension()
            .and_then(|s| s.to_str())
            .unwrap_or("")
            .to_ascii_lowercase();
        if ext != "m3u8" && ext != "m3u" {
            continue;
        }
        let Some(name) = path.file_stem().and_then(|s| s.to_str()) else {
            continue;
        };
        let Ok(content) = std::fs::read_to_string(&path) else {
            continue;
        };
        let items: Vec<String> = content
            .lines()
            .map(str::trim)
            .filter(|l| !l.is_empty() && !l.starts_with('#'))
            .map(|l| l.to_string())
            .collect();
        if let Ok(pl_id) = db.ensure_playlist(name) {
            let existing = db.get_playlist_tracks(pl_id).unwrap_or_default();
            if existing.is_empty() && !items.is_empty() {
                let _ = db.add_tracks_to_playlist(pl_id, &items);
            }
        }
    }
}

pub fn parse_java_properties(input: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for raw_line in input.lines() {
        let line = raw_line.trim_start();
        if line.is_empty() || line.starts_with('#') || line.starts_with('!') {
            continue;
        }
        if let Some((k, v)) = split_key_val(line) {
            map.insert(unescape_java(&k), unescape_java(&v));
        }
    }
    map
}

fn split_key_val(line: &str) -> Option<(String, String)> {
    let mut escaped = false;
    for (idx, ch) in line.char_indices() {
        if escaped {
            escaped = false;
            continue;
        }
        if ch == '\\' {
            escaped = true;
            continue;
        }
        if ch == '=' || ch == ':' {
            let key = line[..idx].trim_end().to_string();
            let val = line[idx + ch.len_utf8()..].trim_start().to_string();
            return Some((key, val));
        }
    }
    None
}

fn unescape_java(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let mut chars = input.chars().peekable();
    while let Some(c) = chars.next() {
        if c != '\\' {
            out.push(c);
            continue;
        }
        match chars.next() {
            Some('n') => out.push('\n'),
            Some('r') => out.push('\r'),
            Some('t') => out.push('\t'),
            Some('\\') => out.push('\\'),
            Some(':') => out.push(':'),
            Some('=') => out.push('='),
            Some('u') => {
                let mut hex = String::with_capacity(4);
                for _ in 0..4 {
                    if let Some(&h) = chars.peek() {
                        if h.is_ascii_hexdigit() {
                            hex.push(h);
                            chars.next();
                        }
                    }
                }
                if let Ok(code) = u32::from_str_radix(&hex, 16) {
                    if let Some(decoded) = char::from_u32(code) {
                        out.push(decoded);
                        continue;
                    }
                }
                out.push_str("\\u");
                out.push_str(&hex);
            }
            Some(other) => out.push(other),
            None => {}
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_escaped_windows_paths_in_java_properties() {
        let sample = r#"
#Mon Nov 16 14:15:18 WIB 2026
discord.rpc.client_id=1570070424627191808
lyrics.track_offset.path.c\:/users/estong/music/royalty\ (lyrics).mp3=-0.50
lyrics.track_offset.file.song.mp3=0.20
"#;
        let map = parse_java_properties(sample);
        assert_eq!(map.get("discord.rpc.client_id").unwrap(), "1570070424627191808");
        assert_eq!(
            map.get("lyrics.track_offset.path.c:/users/estong/music/royalty (lyrics).mp3").unwrap(),
            "-0.50"
        );
        assert_eq!(map.get("lyrics.track_offset.file.song.mp3").unwrap(), "0.20");
    }
}
