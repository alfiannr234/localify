use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::fs::File;
use std::io::{BufRead, BufReader, Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tauri::{AppHandle, Emitter, Manager, State};

const YTDLP_URL: &str =
    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
const YTDLP_SUMS: &str =
    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS";
const FFMPEG_ZIP: &str = "ffmpeg-master-latest-win64-gpl.zip";
const FFMPEG_URL: &str =
    "https://github.com/yt-dlp/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-win64-gpl.zip";
const FFMPEG_SUMS: &str =
    "https://github.com/yt-dlp/FFmpeg-Builds/releases/latest/download/checksums.sha256";
const RELEASE_HOSTS: [&str; 3] = [
    "github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
];

pub fn bin_dir(app: &AppHandle) -> Result<PathBuf, String> {
    Ok(app
        .path()
        .app_local_data_dir()
        .map_err(|e| e.to_string())?
        .join("bin"))
}

pub fn command(exe: &Path) -> Command {
    let mut c = Command::new(exe);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        c.creation_flags(0x0800_0000);
    }
    c.env("PYTHONUTF8", "1").env("PYTHONIOENCODING", "utf-8");
    c
}

pub fn ytdlp_path(app: &AppHandle) -> Result<PathBuf, String> {
    let p = bin_dir(app)?.join("yt-dlp.exe");
    if p.is_file() {
        Ok(p)
    } else {
        Err("yt-dlp is not installed yet. Click Get yt-dlp in Downloads.".into())
    }
}

pub fn ffmpeg_path(app: &AppHandle) -> Option<PathBuf> {
    let own = bin_dir(app).ok()?.join("ffmpeg.exe");
    if own.is_file() {
        return Some(own);
    }
    std::env::split_paths(&std::env::var_os("PATH")?)
        .map(|d| d.join("ffmpeg.exe"))
        .find(|p| p.is_file())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ToolsStatus {
    pub ytdlp: Option<String>,
    pub ffmpeg: Option<String>,
}

#[tauri::command]
pub async fn tools_status(app: AppHandle) -> ToolsStatus {
    tauri::async_runtime::spawn_blocking(move || {
        let ytdlp_ver = ytdlp_path(&app)
            .ok()
            .and_then(|p| command(&p).arg("--version").output().ok())
            .filter(|o| o.status.success())
            .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string());
        let ff = ffmpeg_path(&app).map(|p| p.to_string_lossy().into_owned());
        ToolsStatus {
            ytdlp: ytdlp_ver,
            ffmpeg: ff,
        }
    })
    .await
    .unwrap_or(ToolsStatus {
        ytdlp: None,
        ffmpeg: None,
    })
}

pub fn checksum_for(sums: &str, name: &str) -> Option<String> {
    sums.lines().find_map(|l| {
        let (hash, file) = l.trim().split_once(char::is_whitespace)?;
        let clean = file.trim().trim_start_matches('*');
        (clean == name && hash.len() == 64 && hash.bytes().all(|b| b.is_ascii_hexdigit()))
            .then(|| hash.to_ascii_lowercase())
    })
}

fn check_release_url(raw: &str) -> Result<(), String> {
    let parsed = url::Url::parse(raw).map_err(|e| e.to_string())?;
    let host = parsed.host_str().unwrap_or("");
    if parsed.scheme() == "https" && RELEASE_HOSTS.contains(&host) {
        Ok(())
    } else {
        Err(format!("Refused download from unexpected host: {host}"))
    }
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ToolProgress {
    tool: &'static str,
    done: u64,
    total: u64,
}

fn fetch_checked_blocking(
    app: &AppHandle,
    tool: &'static str,
    file_url: &str,
    sums_url: &str,
    name: &str,
    dest: &Path,
) -> Result<(), String> {
    check_release_url(file_url)?;
    check_release_url(sums_url)?;

    let agent = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(15))
        .timeout_read(Duration::from_secs(120))
        .user_agent("Localify/2.0")
        .build();

    let sums_resp = agent
        .get(sums_url)
        .call()
        .map_err(|e| format!("Failed to fetch SHA-256 list: {e}"))?;
    check_release_url(sums_resp.get_url())?;
    let sums_text = sums_resp
        .into_string()
        .map_err(|e| format!("Failed to read SHA-256 list: {e}"))?;
    let want = checksum_for(&sums_text, name)
        .ok_or_else(|| format!("SHA-256 entry for {name} missing in release sums."))?;

    let resp = agent
        .get(file_url)
        .call()
        .map_err(|e| format!("Download failed: {e}"))?;
    check_release_url(resp.get_url())?;
    let total = resp
        .header("Content-Length")
        .and_then(|v| v.parse::<u64>().ok())
        .unwrap_or(0);

    let tmp = dest.with_extension("part");
    let mut out = File::create(&tmp).map_err(|e| e.to_string())?;
    let mut reader = resp.into_reader();
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    let mut done = 0u64;
    let mut last_emit = 0u64;

    loop {
        let n = reader.read(&mut buf).map_err(|e| e.to_string())?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
        out.write_all(&buf[..n]).map_err(|e| e.to_string())?;
        done += n as u64;
        if done - last_emit >= 256 * 1024 {
            last_emit = done;
            let _ = app.emit("tool-progress", ToolProgress { tool, done, total });
        }
    }
    drop(out);
    let _ = app.emit("tool-progress", ToolProgress { tool, done, total });

    let got = format!("{:x}", hasher.finalize());
    if got != want {
        let _ = std::fs::remove_file(&tmp);
        return Err(format!(
            "{name} failed SHA-256 verification and was removed."
        ));
    }
    if dest.exists() {
        let _ = std::fs::remove_file(dest);
    }
    std::fs::rename(&tmp, dest).map_err(|e| e.to_string())
}

#[derive(Deserialize, Clone, Copy, PartialEq, Debug)]
#[serde(rename_all = "lowercase")]
pub enum ToolKind {
    Ytdlp,
    Ffmpeg,
}

#[tauri::command]
pub async fn tool_install(app: AppHandle, tool: ToolKind) -> Result<(), String> {
    let bin = bin_dir(&app)?;
    std::fs::create_dir_all(&bin).map_err(|e| e.to_string())?;
    tauri::async_runtime::spawn_blocking(move || match tool {
        ToolKind::Ytdlp => fetch_checked_blocking(
            &app,
            "ytdlp",
            YTDLP_URL,
            YTDLP_SUMS,
            "yt-dlp.exe",
            &bin.join("yt-dlp.exe"),
        ),
        ToolKind::Ffmpeg => {
            let zip = bin.join(FFMPEG_ZIP);
            fetch_checked_blocking(
                &app,
                "ffmpeg",
                FFMPEG_URL,
                FFMPEG_SUMS,
                FFMPEG_ZIP,
                &zip,
            )?;
            let unpack = bin.join("ffmpeg-unpack");
            let _ = std::fs::remove_dir_all(&unpack);
            std::fs::create_dir_all(&unpack).map_err(|e| e.to_string())?;
            let ok = command(Path::new("tar"))
                .args(["-xf"])
                .arg(&zip)
                .args(["-C"])
                .arg(&unpack)
                .status()
                .map_err(|e| e.to_string())?
                .success();
            let _ = std::fs::remove_file(&zip);
            let found = ok.then(|| {
                walkdir::WalkDir::new(&unpack)
                    .into_iter()
                    .flatten()
                    .find(|e| e.file_name() == "ffmpeg.exe")
                    .map(|e| e.path().to_path_buf())
            });
            let res = match found.flatten() {
                Some(ff) => {
                    let src_dir = ff.parent().unwrap_or(&unpack);
                    for name in ["ffmpeg.exe", "ffprobe.exe"] {
                        let s = src_dir.join(name);
                        if s.is_file() {
                            let d = bin.join(name);
                            if d.exists() {
                                let _ = std::fs::remove_file(&d);
                            }
                            std::fs::rename(&s, &d).map_err(|e| e.to_string())?;
                        }
                    }
                    Ok(())
                }
                None => Err("Could not unpack ffmpeg.exe from archive.".into()),
            };
            let _ = std::fs::remove_dir_all(&unpack);
            res
        }
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
pub async fn ytdlp_update(app: AppHandle) -> Result<String, String> {
    let exe = ytdlp_path(&app)?;
    tauri::async_runtime::spawn_blocking(move || {
        let out = command(&exe).arg("-U").output().map_err(|e| e.to_string())?;
        let line = String::from_utf8_lossy(&out.stdout)
            .lines()
            .last()
            .unwrap_or("")
            .to_string();
        if out.status.success() {
            Ok(line)
        } else {
            Err(String::from_utf8_lossy(&out.stderr)
                .lines()
                .last()
                .unwrap_or("Update failed")
                .to_string())
        }
    })
    .await
    .map_err(|e| e.to_string())?
}

#[derive(Deserialize, Serialize, Clone, Copy, PartialEq, Debug)]
#[serde(rename_all = "camelCase")]
pub enum DownloadPreset {
    AudioOriginal,
    AudioMp3,
    Video1080,
    VideoBest,
}

impl DownloadPreset {
    pub fn needs_ffmpeg(self) -> bool {
        matches!(self, DownloadPreset::AudioMp3)
    }
}

pub fn valid_url(raw: &str) -> bool {
    raw.len() <= 2048
        && url::Url::parse(raw)
            .is_ok_and(|p| matches!(p.scheme(), "http" | "https") && p.host_str().is_some())
}

pub fn build_ytdlp_args(
    preset: DownloadPreset,
    dest: &Path,
    tmp: &Path,
    ffmpeg_dir: Option<&Path>,
    urls: &[String],
) -> Vec<String> {
    let s = |v: &str| v.to_string();
    let mut a = vec![
        s("--ignore-config"),
        s("--no-cache-dir"),
        s("--newline"),
        s("--progress"),
        s("--quiet"),
        s("--no-warnings"),
        s("--yes-playlist"),
        s("--js-runtimes"),
        s("node"),
        s("--progress-template"),
        s("download:DL %(progress._percent_str)s|%(progress._eta_str)s|%(info.title)s"),
        s("--print"),
        s("after_move:FILE %(filepath)s"),
        s("-o"),
        s("%(playlist_title&{}/|)s%(title).180B.%(ext)s"),
        s("-P"),
        format!("home:{}", dest.display()),
        s("-P"),
        format!("temp:{}", tmp.display()),
    ];

    let is_video = matches!(preset, DownloadPreset::Video1080 | DownloadPreset::VideoBest);
    let fmt_args: Vec<&str> = match (preset, ffmpeg_dir.is_some()) {
        (DownloadPreset::AudioOriginal, true) => vec!["-f", "bestaudio/best", "-x"],
        (DownloadPreset::AudioOriginal, false) => vec!["-f", "bestaudio[ext=m4a]/bestaudio"],
        (DownloadPreset::AudioMp3, _) => {
            vec!["-f", "bestaudio/best", "-x", "--audio-format", "mp3", "--audio-quality", "0"]
        }
        (DownloadPreset::Video1080, true) => {
            vec!["-f", "bv*[height<=1080]+ba/b[height<=1080]/b", "--merge-output-format", "mp4"]
        }
        (DownloadPreset::Video1080, false) => vec!["-f", "b[height<=1080]/b"],
        (DownloadPreset::VideoBest, true) => vec!["-f", "bv*+ba/b", "--merge-output-format", "mp4"],
        (DownloadPreset::VideoBest, false) => vec!["-f", "b"],
    };
    a.extend(fmt_args.into_iter().map(s));

    if let Some(dir) = ffmpeg_dir {
        a.extend([
            s("--ffmpeg-location"),
            dir.display().to_string(),
            s("--embed-metadata"),
            s("--embed-thumbnail"),
        ]);
        if is_video {
            a.extend([s("--embed-subs"), s("--sub-langs"), s("all,-live_chat")]);
        }
    }
    a.push(s("--"));
    a.extend(urls.iter().cloned());
    a
}

#[derive(Clone, Serialize, Default, Debug)]
#[serde(rename_all = "camelCase")]
pub struct DownloadProgress {
    pub id: u64,
    pub status: &'static str,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub percent: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub eta: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub title: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub file: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

pub fn parse_download_progress(id: u64, line: &str) -> Option<DownloadProgress> {
    if let Some(rest) = line.strip_prefix("DL ") {
        let mut p = rest.splitn(3, '|');
        let percent = p.next()?.trim().trim_end_matches('%').parse().ok();
        let eta = p
            .next()
            .map(|e| e.trim().to_string())
            .filter(|e| !e.is_empty() && e != "NA" && e != "Unknown");
        let title = p
            .next()
            .map(|t| t.trim().to_string())
            .filter(|t| !t.is_empty() && t != "NA");
        return Some(DownloadProgress {
            id,
            status: "running",
            percent,
            eta,
            title,
            ..Default::default()
        });
    }
    let file = line.strip_prefix("FILE ")?.trim();
    Some(DownloadProgress {
        id,
        status: "running",
        file: Some(file.to_string()),
        ..Default::default()
    })
}

#[derive(Default)]
pub struct DownloadManager {
    next: AtomicU64,
    slot: Mutex<()>,
    jobs: Mutex<HashMap<u64, Option<Arc<Mutex<Child>>>>>,
}

#[tauri::command]
pub fn download_default_folder() -> String {
    dirs::audio_dir()
        .or_else(dirs::download_dir)
        .unwrap_or_else(|| PathBuf::from("."))
        .to_string_lossy()
        .into_owned()
}

#[tauri::command]
pub fn download_add(
    app: AppHandle,
    dl: State<DownloadManager>,
    urls: Vec<String>,
    preset: DownloadPreset,
    dest: String,
    add_to_library: bool,
) -> Result<u64, String> {
    let clean_urls: Vec<String> = urls
        .into_iter()
        .map(|u| u.trim().to_string())
        .filter(|u| !u.is_empty())
        .collect();
    if clean_urls.is_empty() || clean_urls.len() > 200 {
        return Err("Paste between 1 and 200 links.".into());
    }
    if let Some(bad) = clean_urls.iter().find(|u| !valid_url(u)) {
        return Err(format!("Not a web link: {bad}"));
    }

    let dest_path = PathBuf::from(&dest);
    if !dest_path.is_absolute() {
        return Err("Choose an absolute folder to save into.".into());
    }
    std::fs::create_dir_all(&dest_path).map_err(|e| e.to_string())?;

    let exe = ytdlp_path(&app)?;
    let ff = ffmpeg_path(&app);
    if preset.needs_ffmpeg() && ff.is_none() {
        return Err("MP3 conversion needs ffmpeg. Fetch it in Downloads first.".into());
    }
    let tmp = app
        .path()
        .app_local_data_dir()
        .map_err(|e| e.to_string())?
        .join("download-tmp");
    std::fs::create_dir_all(&tmp).map_err(|e| e.to_string())?;

    let argv = build_ytdlp_args(
        preset,
        &dest_path,
        &tmp,
        ff.as_deref().and_then(Path::parent),
        &clean_urls,
    );
    let id = dl.next.fetch_add(1, Ordering::SeqCst) + 1;
    dl.jobs.lock().unwrap().insert(id, None);
    let _ = app.emit(
        "download-progress",
        DownloadProgress {
            id,
            status: "queued",
            ..Default::default()
        },
    );

    let dest_owned = dest;
    std::thread::spawn(move || {
        let dl = app.state::<DownloadManager>();
        let _guard = dl.slot.lock().unwrap_or_else(|e| e.into_inner());
        if !dl.jobs.lock().unwrap().contains_key(&id) {
            return;
        }
        let finish = run_job(&app, &dl, id, &exe, &argv);
        dl.jobs.lock().unwrap().remove(&id);
        let success = finish.status == "done";
        let _ = app.emit("download-progress", finish);

        if success && add_to_library {
            let st = app.state::<crate::AppState>();
            let _ = st.db.add_folder(&dest_owned);
            crate::rebuild_watchers(&st, &app);
            if crate::scan::scan_library(&st.db, &st.art_dir, Some(&app), None).is_ok() {
                let _ = app.emit("library-updated", ());
            }
        }
    });

    Ok(id)
}

#[tauri::command]
pub fn download_cancel(app: AppHandle, dl: State<DownloadManager>, id: u64) {
    let entry = dl.jobs.lock().unwrap().insert(id, None);
    match entry {
        Some(Some(child)) => {
            let _ = child.lock().unwrap().kill();
        }
        _ => {
            dl.jobs.lock().unwrap().remove(&id);
            let _ = app.emit(
                "download-progress",
                DownloadProgress {
                    id,
                    status: "cancelled",
                    ..Default::default()
                },
            );
        }
    }
}

fn pump_pipe(app: AppHandle, id: u64, pipe: impl Read) -> Option<String> {
    let mut reader = BufReader::new(pipe);
    let mut buf = Vec::new();
    let mut error = None;
    while reader.read_until(b'\n', &mut buf).is_ok_and(|n| n > 0) {
        let line = String::from_utf8_lossy(&buf).trim_end().to_string();
        if let Some(p) = parse_download_progress(id, &line) {
            let _ = app.emit("download-progress", p);
        } else if line.starts_with("ERROR") {
            error = Some(line);
        }
        buf.clear();
    }
    error
}

fn run_job(
    app: &AppHandle,
    dl: &DownloadManager,
    id: u64,
    exe: &Path,
    argv: &[String],
) -> DownloadProgress {
    let mut child = match command(exe)
        .args(argv)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
    {
        Ok(c) => c,
        Err(e) => {
            return DownloadProgress {
                id,
                status: "failed",
                error: Some(e.to_string()),
                ..Default::default()
            };
        }
    };
    let (out, err) = (child.stdout.take().unwrap(), child.stderr.take().unwrap());
    let child = Arc::new(Mutex::new(child));
    dl.jobs.lock().unwrap().insert(id, Some(child.clone()));
    let _ = app.emit(
        "download-progress",
        DownloadProgress {
            id,
            status: "running",
            ..Default::default()
        },
    );
    let app_err = app.clone();
    let err_thread = std::thread::spawn(move || pump_pipe(app_err, id, err));
    let out_err = pump_pipe(app.clone(), id, out);
    let status = child.lock().unwrap().wait();
    let error = err_thread.join().ok().flatten().or(out_err);
    let cancelled = !dl.jobs.lock().unwrap().get(&id).is_some_and(|c| c.is_some());

    match status {
        _ if cancelled => DownloadProgress {
            id,
            status: "cancelled",
            ..Default::default()
        },
        Ok(s) if s.success() => DownloadProgress {
            id,
            status: "done",
            ..Default::default()
        },
        Ok(_) => DownloadProgress {
            id,
            status: "failed",
            error: Some(
                error
                    .map(|e| e.trim_start_matches("ERROR:").trim().to_string())
                    .unwrap_or_else(|| "yt-dlp failed.".into()),
            ),
            ..Default::default()
        },
        Err(e) => DownloadProgress {
            id,
            status: "failed",
            error: Some(e.to_string()),
            ..Default::default()
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn checksum_for_parses_sha256_sums() {
        let sums = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3  yt-dlp\nF5389D3698ECDE4EF06F4E6B1CE0294FE9AC1396228C8990B8D3416D6D177A7D *yt-dlp.exe\n";
        assert_eq!(
            checksum_for(sums, "yt-dlp.exe").as_deref(),
            Some("f5389d3698ecde4ef06f4e6b1ce0294fe9ac1396228c8990b8d3416d6d177a7d")
        );
        assert_eq!(checksum_for(sums, "missing.zip"), None);
    }

    #[test]
    fn valid_url_rejects_flags_and_local_paths() {
        assert!(valid_url("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assert!(!valid_url("--exec calc"));
        assert!(!valid_url("file:///C:/Windows/win.ini"));
    }
}
