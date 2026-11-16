use crate::lyrics::{cache_lyrics_for_track, LyricsResult};
use base64::{engine::general_purpose::STANDARD as B64, Engine as _};
use flate2::read::ZlibDecoder;
use serde::{Deserialize, Serialize};
use std::io::Read;
use std::path::Path;
use std::time::Duration;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OnlineLyricsCandidate {
    pub id: String,
    pub provider: String, // "lrclib" | "netease" | "kugou"
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration: f64,
    pub synced: bool,
    pub word_synced: bool,
    pub format: String,
    pub content: String,
    pub preview: String,
}

const KUGOU_XOR_KEY: [u8; 16] = [
    0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d, 0xce, 0xd2, 0x6e,
    0x69,
];

fn http_agent() -> ureq::Agent {
    ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(5))
        .timeout_read(Duration::from_secs(8))
        .user_agent("Localify/2.0 (https://github.com/Alfiannr234/localify)")
        .build()
}

pub fn clean_query(text: &str) -> String {
    let mut s = text.to_string();
    for open_close in [('(', ')'), ('[', ']')] {
        loop {
            let Some(start) = s.find(open_close.0) else {
                break;
            };
            let Some(rel_end) = s[start..].find(open_close.1) else {
                break;
            };
            let end = start + rel_end + 1;
            let inner = s[start + 1..end - 1].to_ascii_lowercase();
            let noisy = [
                "official",
                "video",
                "audio",
                "lyrics",
                "lyric",
                "mv",
                "hd",
                "hq",
                "remaster",
                "feat",
                "ft.",
                "visualizer",
            ];
            if noisy.iter().any(|k| inner.contains(k)) {
                s.replace_range(start..end, " ");
            } else {
                break;
            }
        }
    }
    s.split_whitespace().collect::<Vec<_>>().join(" ")
}

pub fn percent_encode(input: &str) -> String {
    let mut out = String::with_capacity(input.len() * 2);
    for b in input.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char);
            }
            b' ' => out.push_str("%20"),
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

pub fn fetch_best_online_lyrics(
    track_path: &str,
    title: &str,
    artist: &str,
    album: &str,
    duration: f64,
    cache_dir: &Path,
) -> Option<LyricsResult> {
    let cands = search_all_providers(title, artist, album, duration);
    let best = cands.into_iter().next()?;
    let saved = cache_lyrics_for_track(track_path, &best.format, &best.content, cache_dir).ok();
    Some(LyricsResult {
        source: best.provider,
        format: best.format,
        content: best.content,
        path: saved.map(|p| p.to_string_lossy().into_owned()),
    })
}

pub fn search_all_providers(
    title: &str,
    artist: &str,
    album: &str,
    duration: f64,
) -> Vec<OnlineLyricsCandidate> {
    let clean_title = clean_query(title);
    let clean_artist = clean_query(artist);
    let agent = http_agent();

    let (lrc_res, netease_res, kugou_res) = std::thread::scope(|s| {
        let h1 = s.spawn(|| fetch_lrclib(&agent, &clean_title, &clean_artist, album, duration));
        let h2 = s.spawn(|| fetch_netease(&agent, &clean_title, &clean_artist, duration));
        let h3 = s.spawn(|| fetch_kugou(&agent, &clean_title, &clean_artist, duration));
        (
            h1.join().unwrap_or_default(),
            h2.join().unwrap_or_default(),
            h3.join().unwrap_or_default(),
        )
    });

    let mut all = Vec::new();
    all.extend(kugou_res);
    all.extend(lrc_res);
    all.extend(netease_res);

    all.sort_by(|a, b| {
        let sa = score_candidate(a, &clean_title, &clean_artist, duration);
        let sb = score_candidate(b, &clean_title, &clean_artist, duration);
        sb.partial_cmp(&sa).unwrap_or(std::cmp::Ordering::Equal)
    });

    all
}

pub fn fetch_from_provider(
    provider: &str,
    title: &str,
    artist: &str,
    album: &str,
    duration: f64,
) -> Option<OnlineLyricsCandidate> {
    let clean_title = clean_query(title);
    let clean_artist = clean_query(artist);
    let agent = http_agent();
    let mut cands = match provider {
        "lrclib" => fetch_lrclib(&agent, &clean_title, &clean_artist, album, duration),
        "netease" => fetch_netease(&agent, &clean_title, &clean_artist, duration),
        "kugou" => fetch_kugou(&agent, &clean_title, &clean_artist, duration),
        _ => Vec::new(),
    };
    cands.sort_by(|a, b| {
        let sa = score_candidate(a, &clean_title, &clean_artist, duration);
        let sb = score_candidate(b, &clean_title, &clean_artist, duration);
        sb.partial_cmp(&sa).unwrap_or(std::cmp::Ordering::Equal)
    });
    let best = cands.into_iter().next()?;
    if duration > 5.0 && best.duration > 5.0 && (duration - best.duration).abs() > 25.0 {
        return None;
    }
    if score_candidate(&best, &clean_title, &clean_artist, duration) < 35.0 {
        return None;
    }
    Some(best)
}

fn score_candidate(c: &OnlineLyricsCandidate, title: &str, artist: &str, duration: f64) -> f64 {
    let mut score = 0.0;
    if c.word_synced {
        score += 35.0;
    } else if c.synced {
        score += 25.0;
    }
    let ct = c.title.to_ascii_lowercase();
    let qt = title.to_ascii_lowercase();
    if ct == qt {
        score += 20.0;
    } else if ct.contains(&qt) || qt.contains(&ct) {
        score += 10.0;
    }
    let ca = c.artist.to_ascii_lowercase();
    let qa = artist.to_ascii_lowercase();
    if !qa.is_empty() && !ca.is_empty() && (ca.contains(&qa) || qa.contains(&ca)) {
        score += 15.0;
    }
    if duration > 5.0 && c.duration > 5.0 {
        let diff = (duration - c.duration).abs();
        if diff <= 2.0 {
            score += 20.0;
        } else if diff <= 6.0 {
            score += 10.0;
        } else if diff > 20.0 {
            score -= 20.0;
        }
    }
    score
}

fn fetch_lrclib(
    agent: &ureq::Agent,
    title: &str,
    artist: &str,
    album: &str,
    duration: f64,
) -> Vec<OnlineLyricsCandidate> {
    let mut out = Vec::new();
    let q = if artist.is_empty() || artist.eq_ignore_ascii_case("unknown artist") {
        title.to_string()
    } else {
        format!("{artist} {title}")
    };

    if duration > 1.0 && !artist.eq_ignore_ascii_case("unknown artist") {
        let exact_url = format!(
            "https://lrclib.net/api/get?track_name={}&artist_name={}&album_name={}&duration={}",
            percent_encode(title),
            percent_encode(artist),
            percent_encode(album),
            duration.round() as i64
        );
        if let Ok(resp) = agent.get(&exact_url).call() {
            if let Ok(item) = resp.into_json::<serde_json::Value>() {
                if let Some(cand) = map_lrclib_item(&item) {
                    out.push(cand);
                }
            }
        }
    }

    let search_url = format!("https://lrclib.net/api/search?q={}", percent_encode(&q));
    if let Ok(resp) = agent.get(&search_url).call() {
        if let Ok(arr) = resp.into_json::<Vec<serde_json::Value>>() {
            for item in arr.into_iter().take(6) {
                if let Some(cand) = map_lrclib_item(&item) {
                    if !out.iter().any(|c| c.id == cand.id) {
                        out.push(cand);
                    }
                }
            }
        }
    }
    out
}

fn map_lrclib_item(v: &serde_json::Value) -> Option<OnlineLyricsCandidate> {
    let synced_raw = v.get("syncedLyrics").and_then(|s| s.as_str()).unwrap_or("");
    let plain_raw = v.get("plainLyrics").and_then(|s| s.as_str()).unwrap_or("");
    let (content, synced, format) = if !synced_raw.trim().is_empty() {
        (synced_raw.to_string(), true, "lrc".to_string())
    } else if !plain_raw.trim().is_empty() {
        (plain_raw.to_string(), false, "txt".to_string())
    } else {
        return None;
    };
    let id = v
        .get("id")
        .and_then(|x| x.as_i64())
        .map(|n| format!("lrclib-{n}"))
        .unwrap_or_else(|| "lrclib-0".into());
    Some(OnlineLyricsCandidate {
        id,
        provider: "lrclib".into(),
        title: v.get("trackName").and_then(|s| s.as_str()).unwrap_or("").into(),
        artist: v.get("artistName").and_then(|s| s.as_str()).unwrap_or("").into(),
        album: v.get("albumName").and_then(|s| s.as_str()).unwrap_or("").into(),
        duration: v.get("duration").and_then(|d| d.as_f64()).unwrap_or(0.0),
        synced,
        word_synced: false,
        format,
        preview: make_preview(&content),
        content,
    })
}

fn fetch_netease(
    agent: &ureq::Agent,
    title: &str,
    artist: &str,
    _duration: f64,
) -> Vec<OnlineLyricsCandidate> {
    let mut out = Vec::new();
    let q = if artist.is_empty() || artist.eq_ignore_ascii_case("unknown artist") {
        title.to_string()
    } else {
        format!("{artist} {title}")
    };
    let url = format!(
        "https://music.163.com/api/search/get?s={}&type=1&limit=4",
        percent_encode(&q)
    );
    let Ok(resp) = agent
        .get(&url)
        .set("Referer", "https://music.163.com/")
        .call()
    else {
        return out;
    };
    let Ok(json) = resp.into_json::<serde_json::Value>() else {
        return out;
    };
    let Some(songs) = json
        .get("result")
        .and_then(|r| r.get("songs"))
        .and_then(|s| s.as_array())
    else {
        return out;
    };

    for song in songs.iter().take(3) {
        let Some(song_id) = song.get("id").and_then(|v| v.as_i64()) else {
            continue;
        };
        let lrc_url = format!(
            "https://music.163.com/api/song/lyric?id={song_id}&lv=1&kv=1&tv=-1"
        );
        let Ok(lr) = agent
            .get(&lrc_url)
            .set("Referer", "https://music.163.com/")
            .call()
        else {
            continue;
        };
        let Ok(lj) = lr.into_json::<serde_json::Value>() else {
            continue;
        };
        let lyric = lj
            .get("lrc")
            .and_then(|x| x.get("lyric"))
            .and_then(|s| s.as_str())
            .unwrap_or("")
            .trim();
        if lyric.is_empty() {
            continue;
        }
        let s_title = song.get("name").and_then(|s| s.as_str()).unwrap_or(title);
        let s_artist = song
            .get("artists")
            .and_then(|a| a.as_array())
            .and_then(|arr| arr.first())
            .and_then(|a| a.get("name"))
            .and_then(|n| n.as_str())
            .unwrap_or(artist);
        let dur_ms = song.get("duration").and_then(|d| d.as_f64()).unwrap_or(0.0);
        let synced = lyric.contains('[');
        out.push(OnlineLyricsCandidate {
            id: format!("netease-{song_id}"),
            provider: "netease".into(),
            title: s_title.into(),
            artist: s_artist.into(),
            album: song
                .get("album")
                .and_then(|al| al.get("name"))
                .and_then(|n| n.as_str())
                .unwrap_or("")
                .into(),
            duration: dur_ms / 1000.0,
            synced,
            word_synced: false,
            format: if synced { "lrc" } else { "txt" }.into(),
            preview: make_preview(lyric),
            content: lyric.to_string(),
        });
    }
    out
}

fn fetch_kugou(
    agent: &ureq::Agent,
    title: &str,
    artist: &str,
    duration: f64,
) -> Vec<OnlineLyricsCandidate> {
    let mut out = Vec::new();
    let keyword = if artist.is_empty() || artist.eq_ignore_ascii_case("unknown artist") {
        title.to_string()
    } else {
        format!("{artist} - {title}")
    };
    let dur_ms = (duration.max(0.0) * 1000.0).round() as i64;
    let search_url = format!(
        "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword={}&duration={}",
        percent_encode(&keyword),
        dur_ms
    );
    let Ok(resp) = agent.get(&search_url).call() else {
        return out;
    };
    let Ok(json) = resp.into_json::<serde_json::Value>() else {
        return out;
    };
    let Some(cands) = json.get("candidates").and_then(|c| c.as_array()) else {
        return out;
    };

    for cand in cands.iter().take(3) {
        let Some(id) = cand.get("id").and_then(|v| v.as_str()) else {
            continue;
        };
        let Some(accesskey) = cand.get("accesskey").and_then(|v| v.as_str()) else {
            continue;
        };
        let dl_url = format!(
            "https://lyrics.kugou.com/download?ver=1&client=pc&id={id}&accesskey={accesskey}&fmt=krc&charset=utf8"
        );
        let Ok(dl_resp) = agent.get(&dl_url).call() else {
            continue;
        };
        let Ok(dl_json) = dl_resp.into_json::<serde_json::Value>() else {
            continue;
        };
        let Some(b64) = dl_json.get("content").and_then(|s| s.as_str()) else {
            continue;
        };
        let Some(decoded_krc) = decode_krc_base64(b64) else {
            continue;
        };
        let c_title = cand.get("song").and_then(|s| s.as_str()).unwrap_or(title);
        let c_artist = cand.get("singer").and_then(|s| s.as_str()).unwrap_or(artist);
        let c_dur = cand.get("duration").and_then(|d| d.as_f64()).unwrap_or(0.0) / 1000.0;
        out.push(OnlineLyricsCandidate {
            id: format!("kugou-{id}"),
            provider: "kugou".into(),
            title: c_title.into(),
            artist: c_artist.into(),
            album: String::new(),
            duration: c_dur,
            synced: true,
            word_synced: true,
            format: "krc".into(),
            preview: make_preview(&decoded_krc),
            content: decoded_krc,
        });
    }
    out
}

pub fn decode_krc_base64(b64: &str) -> Option<String> {
    let bytes = B64.decode(b64.trim()).ok()?;
    decode_krc_bytes(&bytes)
}

pub fn decode_krc_bytes(bytes: &[u8]) -> Option<String> {
    if bytes.len() < 4 || &bytes[0..4] != b"krc1" {
        return None;
    }
    let encrypted = &bytes[4..];
    let mut xored = Vec::with_capacity(encrypted.len());
    for (i, &b) in encrypted.iter().enumerate() {
        xored.push(b ^ KUGOU_XOR_KEY[i % 16]);
    }
    let mut decoder = ZlibDecoder::new(&xored[..]);
    let mut out = String::new();
    decoder.read_to_string(&mut out).ok()?;
    let trimmed = out.trim_start_matches('\u{feff}').trim();
    if trimmed.is_empty() {
        return None;
    }
    Some(trimmed.to_string())
}

fn make_preview(content: &str) -> String {
    content
        .lines()
        .take(4)
        .collect::<Vec<_>>()
        .join(" / ")
        .chars()
        .take(140)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn clean_query_removes_video_suffixes() {
        assert_eq!(
            clean_query("You Are The Reason (Official Video)"),
            "You Are The Reason"
        );
        assert_eq!(
            clean_query("Royalty [Lyrics] (feat. Neoni)"),
            "Royalty"
        );
    }

    #[test]
    fn krc_decoder_decrypts_zlib_payload() {
        use flate2::write::ZlibEncoder;
        use flate2::Compression;
        use std::io::Write;

        let plain = "[1000,2000]<0,500,0>Hel<500,500,0>lo";
        let mut enc = ZlibEncoder::new(Vec::new(), Compression::default());
        enc.write_all(plain.as_bytes()).unwrap();
        let compressed = enc.finish().unwrap();

        let mut payload = b"krc1".to_vec();
        for (i, b) in compressed.iter().enumerate() {
            payload.push(b ^ KUGOU_XOR_KEY[i % 16]);
        }
        let b64 = B64.encode(payload);
        assert_eq!(decode_krc_base64(&b64).unwrap(), plain);
    }
}
