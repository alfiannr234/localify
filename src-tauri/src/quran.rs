use crate::translate::http1_agent;
use serde::{Deserialize, Serialize};
use std::hash::{Hash, Hasher};
use std::path::{Path, PathBuf};
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::{DecoderOptions, CODEC_TYPE_NULL};
use symphonia::core::errors::Error;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;
use tauri::{AppHandle, Manager};

const QURAN_COM: &str = "https://api.quran.com/api/v4";
const EDITIONS: &str = "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions";
const ALAFASY: u32 = 7;

#[derive(Deserialize, Clone, Copy, Debug, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum WordLang {
    Id,
    En,
}

#[derive(Deserialize, Debug)]
#[serde(tag = "kind", rename_all = "camelCase")]
pub enum QuranSource {
    Verses { chapter: u16, lang: WordLang },
    Timings { chapter: u16 },
    Shubah { chapter: u16 },
    ClearQuran { chapter: u16 },
}

impl QuranSource {
    fn chapter(&self) -> u16 {
        match *self {
            QuranSource::Verses { chapter, .. }
            | QuranSource::Timings { chapter }
            | QuranSource::Shubah { chapter }
            | QuranSource::ClearQuran { chapter } => chapter,
        }
    }
}

pub fn build_quran_url(source: &QuranSource, page: u32) -> Result<String, String> {
    let c = source.chapter();
    if !(1..=114).contains(&c) {
        return Err("Surah must be 1..=114.".into());
    }
    Ok(match source {
        QuranSource::Verses { lang, .. } => {
            let l = if *lang == WordLang::Id { "id" } else { "en" };
            format!("{QURAN_COM}/verses/by_chapter/{c}?words=true&word_fields=text_uthmani&translations=33,20&language={l}&per_page=50&page={page}")
        }
        QuranSource::Timings { .. } => {
            format!("{QURAN_COM}/chapter_recitations/{ALAFASY}/{c}?segments=true")
        }
        QuranSource::Shubah { .. } => format!("{EDITIONS}/ara-quranshouba/{c}.json"),
        QuranSource::ClearQuran { .. } => format!("{EDITIONS}/eng-mustafakhattaba/{c}.json"),
    })
}

fn fetch_json(url: &str) -> Result<serde_json::Value, String> {
    let resp = http1_agent()
        .get(url)
        .call()
        .map_err(|e| format!("Network error fetching Qur'an data: {e}"))?;
    resp.into_json()
        .map_err(|_| "Unexpected response from Qur'an API.".to_string())
}

#[tauri::command]
pub async fn quran_fetch(source: QuranSource) -> Result<serde_json::Value, String> {
    tauri::async_runtime::spawn_blocking(move || {
        if !matches!(source, QuranSource::Verses { .. }) {
            return fetch_json(&build_quran_url(&source, 1)?);
        }
        let mut verses = Vec::new();
        for page in 1..=10 {
            let j = fetch_json(&build_quran_url(&source, page)?)?;
            verses.extend(j["verses"].as_array().cloned().unwrap_or_default());
            if j["pagination"]["next_page"].is_null() {
                break;
            }
        }
        Ok(serde_json::json!({ "verses": verses }))
    })
    .await
    .map_err(|e| e.to_string())?
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct Pauses {
    pub duration: f64,
    pub pauses: Vec<[f64; 2]>,
}

const FRAME: f64 = 0.02;

pub fn find_pauses(db: &[f32], min_len: f64) -> Vec<[f64; 2]> {
    if db.is_empty() {
        return vec![];
    }
    let smooth: Vec<f32> = (0..db.len())
        .map(|i| {
            let a = i.saturating_sub(2);
            let b = (i + 3).min(db.len());
            db[a..b].iter().sum::<f32>() / (b - a) as f32
        })
        .collect();
    let mut sorted = db.to_vec();
    sorted.sort_by(|a, b| a.total_cmp(b));
    let threshold = sorted[sorted.len() / 2] - 12.0;

    let mut out = Vec::new();
    let mut start: Option<usize> = None;
    for (i, &v) in smooth.iter().chain(std::iter::once(&f32::INFINITY)).enumerate() {
        match (v < threshold, start) {
            (true, None) => start = Some(i),
            (false, Some(s)) => {
                if (i - s) as f64 * FRAME >= min_len {
                    out.push([round2(s as f64 * FRAME), round2(i as f64 * FRAME)]);
                }
                start = None;
            }
            _ => {}
        }
    }
    out
}

fn round2(x: f64) -> f64 {
    (x * 100.0).round() / 100.0
}

fn loudness_db(path: &Path) -> Result<Vec<f32>, String> {
    let file = std::fs::File::open(path).map_err(|e| e.to_string())?;
    let mss = MediaSourceStream::new(Box::new(file), Default::default());
    let mut hint = Hint::new();
    if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
        hint.with_extension(ext);
    }
    let probed = symphonia::default::get_probe()
        .format(&hint, mss, &FormatOptions::default(), &MetadataOptions::default())
        .map_err(|e| format!("Cannot read audio format: {e}"))?;
    let mut format = probed.format;
    let track = format
        .tracks()
        .iter()
        .find(|t| t.codec_params.codec != CODEC_TYPE_NULL)
        .ok_or("No audio track")?;
    let (id, rate) = (
        track.id,
        track.codec_params.sample_rate.ok_or("Unknown sample rate")?,
    );
    let mut decoder = symphonia::default::get_codecs()
        .make(&track.codec_params, &DecoderOptions::default())
        .map_err(|e| format!("Cannot decode audio: {e}"))?;

    let per_frame = ((rate as f64) * FRAME).round() as usize;
    let (mut sum, mut n) = (0f64, 0usize);
    let mut out = Vec::new();
    let mut buf: Option<SampleBuffer<f32>> = None;

    loop {
        let packet = match format.next_packet() {
            Ok(p) => p,
            Err(Error::IoError(e)) if e.kind() == std::io::ErrorKind::UnexpectedEof => break,
            Err(Error::ResetRequired) => break,
            Err(e) => return Err(e.to_string()),
        };
        if packet.track_id() != id {
            continue;
        }
        let decoded = match decoder.decode(&packet) {
            Ok(d) => d,
            Err(Error::DecodeError(_)) => continue,
            Err(e) => return Err(e.to_string()),
        };
        let spec = *decoded.spec();
        let channels = spec.channels.count().max(1);
        if buf.as_ref().is_none_or(|b| b.capacity() < decoded.capacity()) {
            buf = Some(SampleBuffer::new(decoded.capacity() as u64, spec));
        }
        let b = buf.as_mut().unwrap();
        b.copy_interleaved_ref(decoded);
        for frame in b.samples().chunks(channels) {
            let m = frame.iter().sum::<f32>() / channels as f32;
            sum += (m * m) as f64;
            n += 1;
            if n == per_frame {
                out.push((10.0 * (sum / n as f64 + 1e-12).log10()) as f32);
                (sum, n) = (0.0, 0);
            }
        }
    }
    Ok(out)
}

fn cache_file(dir: &Path, path: &Path) -> Option<PathBuf> {
    let meta = std::fs::metadata(path).ok()?;
    let mut h = std::hash::DefaultHasher::new();
    path.to_string_lossy().to_lowercase().hash(&mut h);
    meta.len().hash(&mut h);
    meta.modified().ok()?.hash(&mut h);
    Some(dir.join(format!("{:016x}.json", h.finish())))
}

#[tauri::command]
pub async fn quran_pauses(app: AppHandle, path: String) -> Result<Pauses, String> {
    let dir = app
        .path()
        .app_local_data_dir()
        .map_err(|e| e.to_string())?
        .join("quran-sync");
    tauri::async_runtime::spawn_blocking(move || {
        let p = Path::new(&path);
        let cache = cache_file(&dir, p);
        if let Some(hit) = cache
            .as_ref()
            .and_then(|c| std::fs::read_to_string(c).ok())
            .and_then(|s| serde_json::from_str(&s).ok())
        {
            return Ok(hit);
        }
        let db = loudness_db(p)?;
        let res = Pauses {
            duration: round2(db.len() as f64 * FRAME),
            pauses: find_pauses(&db, 0.3),
        };
        if let Some(c) = cache {
            let _ = std::fs::create_dir_all(&dir);
            let _ = std::fs::write(c, serde_json::to_string(&res).unwrap_or_default());
        }
        Ok(res)
    })
    .await
    .map_err(|e| e.to_string())?
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_surah_urls_and_pauses() {
        assert!(build_quran_url(&QuranSource::Shubah { chapter: 114 }, 1).is_ok());
        assert!(build_quran_url(&QuranSource::ClearQuran { chapter: 115 }, 1).is_err());

        let mut db = vec![-20.0f32; 50];
        db.extend(vec![-70.0; 20]);
        db.extend(vec![-20.0; 40]);
        let pauses = find_pauses(&db, 0.3);
        assert_eq!(pauses.len(), 1);
    }
}
