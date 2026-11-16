mod audio;
mod db;
mod discord;
mod downloads;
mod legacy;
mod lyrics;
mod lyrics_online;
mod media_keys;
mod quran;
mod scan;
mod subsonic;
mod tags;
mod translate;

use audio::{list_output_devices, AudioDeviceInfo, AudioEngine, AudioTick, EqConfig};
use db::{Db, PlaylistSummary, Track};
use discord::{DiscordRpc, DiscordTrackState};
use lyrics::{
    assign_custom_lyrics, cache_lyrics_for_track, load_local_lyrics as load_local_lyrics_impl,
    LyricsResult,
};
use lyrics_online::{
    fetch_best_online_lyrics, fetch_from_provider, search_all_providers, OnlineLyricsCandidate,
};
use media_keys::SmtcController;
use notify::RecursiveMode;
use notify_debouncer_mini::new_debouncer;
use scan::is_audio_path;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tags::{read_full_tags, write_full_tags, FullTags, TagEditPayload};
use tauri::{AppHandle, Emitter, Manager, State};

pub struct AppState {
    pub db: Arc<Db>,
    pub audio: AudioEngine,
    pub discord: DiscordRpc,
    pub smtc: Mutex<Option<SmtcController>>,
    pub art_dir: PathBuf,
    pub lyrics_dir: PathBuf,
    pub launch_files: Mutex<Vec<String>>,
    pub watcher: Mutex<Option<notify_debouncer_mini::Debouncer<notify::RecommendedWatcher>>>,
}

pub fn rebuild_watchers(state: &AppState, app: &AppHandle) {
    let Ok(folders) = state.db.get_folders() else {
        return;
    };
    let db = Arc::clone(&state.db);
    let art_dir = state.art_dir.clone();
    let app_handle = app.clone();

    if let Ok(mut debouncer) = new_debouncer(
        Duration::from_secs(2),
        move |res: notify_debouncer_mini::DebounceEventResult| {
            if let Ok(events) = res {
                let relevant = events
                    .iter()
                    .any(|e| is_audio_path(&e.path) || e.path.is_dir());
                if relevant {
                    if scan::scan_library(&db, &art_dir, Some(&app_handle), None).is_ok() {
                        let _ = app_handle.emit("library-updated", ());
                    }
                }
            }
        },
    ) {
        for f in folders {
            let p = Path::new(&f);
            if p.exists() {
                let _ = debouncer.watcher().watch(p, RecursiveMode::Recursive);
            }
        }
        if let Ok(mut w) = state.watcher.lock() {
            *w = Some(debouncer);
        }
    }
}

#[tauri::command]
fn get_library(state: State<AppState>) -> Result<Vec<Track>, String> {
    state.db.get_all_tracks()
}

#[tauri::command]
fn get_folders(state: State<AppState>) -> Result<Vec<String>, String> {
    state.db.get_folders()
}

#[tauri::command]
fn add_folder(path: String, state: State<AppState>, app: AppHandle) -> Result<Vec<Track>, String> {
    state.db.add_folder(&path)?;
    scan::scan_library(&state.db, &state.art_dir, Some(&app), None)?;
    rebuild_watchers(&state, &app);
    state.db.get_all_tracks()
}

#[tauri::command]
fn remove_folder(
    path: String,
    state: State<AppState>,
    app: AppHandle,
) -> Result<Vec<Track>, String> {
    state.db.remove_folder(&path)?;
    rebuild_watchers(&state, &app);
    state.db.get_all_tracks()
}

#[tauri::command]
async fn scan_folders(state: State<'_, AppState>, app: AppHandle) -> Result<Vec<Track>, String> {
    let db = Arc::clone(&state.db);
    let art_dir = state.art_dir.clone();
    tauri::async_runtime::spawn_blocking(move || {
        scan::scan_library(&db, &art_dir, Some(&app), None)?;
        db.get_all_tracks()
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
fn get_art_dir(state: State<AppState>) -> String {
    state.art_dir.to_string_lossy().into_owned()
}

#[tauri::command]
fn audio_play(path: String, start_secs: Option<f64>, state: State<AppState>) -> Result<(), String> {
    state.audio.play(path, start_secs.unwrap_or(0.0))
}

#[tauri::command]
fn audio_pause(paused: bool, state: State<AppState>) {
    state.audio.set_paused(paused);
}

#[tauri::command]
fn audio_seek(secs: f64, state: State<AppState>) {
    state.audio.seek(secs);
}

#[tauri::command]
fn audio_stop(state: State<AppState>) {
    state.audio.stop();
}

#[tauri::command]
fn audio_set_volume(volume: f32, state: State<AppState>) {
    state.audio.set_volume(volume);
}

#[tauri::command]
fn audio_set_mute(muted: bool, state: State<AppState>) {
    state.audio.set_mute(muted);
}

#[tauri::command]
fn audio_set_speed(speed: f32, state: State<AppState>) {
    state.audio.set_speed(speed);
}

#[tauri::command]
fn audio_set_eq(cfg: EqConfig, state: State<AppState>) {
    state.audio.set_eq(cfg);
}

#[tauri::command]
fn audio_state(state: State<AppState>) -> AudioTick {
    state.audio.state()
}

#[tauri::command]
fn audio_devices() -> Vec<AudioDeviceInfo> {
    list_output_devices()
}

#[tauri::command]
fn audio_set_device(name: Option<String>, state: State<AppState>) -> Result<(), String> {
    state.audio.set_device(name)
}

#[tauri::command]
fn toggle_like(path: String, state: State<AppState>) -> Result<bool, String> {
    state.db.toggle_like(&path)
}

#[tauri::command]
fn record_play(path: String, state: State<AppState>) -> Result<i64, String> {
    state.db.record_play(&path)
}

#[tauri::command]
fn get_recently_played(limit: Option<i64>, state: State<AppState>) -> Result<Vec<String>, String> {
    state.db.get_recently_played(limit.unwrap_or(24))
}

#[tauri::command]
fn get_playlists(state: State<AppState>) -> Result<Vec<PlaylistSummary>, String> {
    state.db.get_playlists()
}

#[tauri::command]
fn get_playlist_tracks(playlist_id: i64, state: State<AppState>) -> Result<Vec<String>, String> {
    state.db.get_playlist_tracks(playlist_id)
}

#[tauri::command]
fn create_playlist(name: String, state: State<AppState>) -> Result<PlaylistSummary, String> {
    state.db.create_playlist(&name)
}

#[tauri::command]
fn rename_playlist(id: i64, name: String, state: State<AppState>) -> Result<(), String> {
    state.db.rename_playlist(id, &name)
}

#[tauri::command]
fn delete_playlist(id: i64, state: State<AppState>) -> Result<(), String> {
    state.db.delete_playlist(id)
}

#[tauri::command]
fn add_tracks_to_playlist(
    playlist_id: i64,
    paths: Vec<String>,
    state: State<AppState>,
) -> Result<(), String> {
    state.db.add_tracks_to_playlist(playlist_id, &paths)
}

#[tauri::command]
fn set_playlist_tracks(
    playlist_id: i64,
    paths: Vec<String>,
    state: State<AppState>,
) -> Result<(), String> {
    state.db.set_playlist_tracks(playlist_id, &paths)
}

#[tauri::command]
fn export_m3u8(
    playlist_id: i64,
    dest_path: String,
    state: State<AppState>,
) -> Result<(), String> {
    let tracks = state.db.get_playlist_tracks(playlist_id)?;
    let mut out = String::from("#EXTM3U\n");
    for p in tracks {
        out.push_str(&p);
        out.push('\n');
    }
    std::fs::write(&dest_path, out).map_err(|e| e.to_string())
}

#[tauri::command]
fn import_m3u8(
    name: String,
    src_path: String,
    state: State<AppState>,
) -> Result<PlaylistSummary, String> {
    let content = std::fs::read_to_string(&src_path).map_err(|e| e.to_string())?;
    let paths: Vec<String> = content
        .lines()
        .map(str::trim)
        .filter(|l| !l.is_empty() && !l.starts_with('#'))
        .map(|l| l.to_string())
        .collect();
    let summary = state.db.create_playlist(&name)?;
    if !paths.is_empty() {
        state.db.add_tracks_to_playlist(summary.id, &paths)?;
    }
    Ok(PlaylistSummary {
        track_count: paths.len() as i64,
        ..summary
    })
}

#[tauri::command]
fn read_tags(path: String) -> Result<FullTags, String> {
    read_full_tags(&path)
}

#[tauri::command]
fn write_tags(payload: TagEditPayload, state: State<AppState>) -> Result<Track, String> {
    let snap = state.audio.state();
    let was_current = snap.path.as_deref() == Some(payload.path.as_str());
    if was_current {
        state.audio.stop();
    }
    let updated = write_full_tags(&payload, &state.db, &state.art_dir)?;
    if was_current {
        let _ = state.audio.play(payload.path, snap.position);
        if !snap.playing {
            state.audio.set_paused(true);
        }
    }
    Ok(updated)
}

#[tauri::command]
fn get_track_offset(path: String, state: State<AppState>) -> Result<Option<f64>, String> {
    state.db.get_track_offset(&path)
}

#[tauri::command]
fn set_track_offset(path: String, offset_secs: f64, state: State<AppState>) -> Result<(), String> {
    state.db.set_track_offset(&path, offset_secs)
}

#[tauri::command]
fn load_local_lyrics(track_path: String, state: State<AppState>) -> Option<LyricsResult> {
    load_local_lyrics_impl(&track_path, &state.lyrics_dir)
}

#[tauri::command]
async fn fetch_online_lyrics(
    track_path: String,
    title: String,
    artist: String,
    album: String,
    duration: f64,
    state: State<'_, AppState>,
) -> Result<Option<LyricsResult>, String> {
    let cache_dir = state.lyrics_dir.clone();
    tauri::async_runtime::spawn_blocking(move || {
        fetch_best_online_lyrics(&track_path, &title, &artist, &album, duration, &cache_dir)
    })
    .await
    .map_err(|e| e.to_string())
}

#[tauri::command]
async fn search_online_lyrics(
    title: String,
    artist: String,
    album: String,
    duration: f64,
) -> Result<Vec<OnlineLyricsCandidate>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        search_all_providers(&title, &artist, &album, duration)
    })
    .await
    .map_err(|e| e.to_string())
}

#[tauri::command]
fn save_lyrics_choice(
    track_path: String,
    format: String,
    content: String,
    state: State<AppState>,
) -> Result<LyricsResult, String> {
    let saved = cache_lyrics_for_track(&track_path, &format, &content, &state.lyrics_dir)?;
    Ok(LyricsResult {
        source: "cache".into(),
        format,
        content,
        path: Some(saved.to_string_lossy().into_owned()),
    })
}

#[tauri::command]
fn assign_lyrics_file(
    track_path: String,
    lyrics_file: String,
    state: State<AppState>,
) -> Result<LyricsResult, String> {
    assign_custom_lyrics(&track_path, &lyrics_file, &state.lyrics_dir)
}

#[tauri::command]
fn clear_cached_lyrics(track_path: String, state: State<AppState>) -> Result<(), String> {
    let mut hash: u64 = 0xcbf29ce484222325;
    for &b in track_path.as_bytes() {
        hash ^= b as u64;
        hash = hash.wrapping_mul(0x100000001b3);
    }
    let prefix = format!("{hash:016x}");
    for ext in ["ttml", "krc", "lrc", "txt"] {
        let p = state.lyrics_dir.join(format!("{prefix}.{ext}"));
        if p.exists() {
            let _ = std::fs::remove_file(p);
        }
    }
    Ok(())
}

#[tauri::command]
fn get_kv(key: String, state: State<AppState>) -> Result<Option<String>, String> {
    state.db.get_kv(&key)
}

#[tauri::command]
fn set_kv(key: String, value: String, state: State<AppState>) -> Result<(), String> {
    state.db.set_kv(&key, &value)
}

#[tauri::command]
fn get_all_kv(state: State<AppState>) -> Result<HashMap<String, String>, String> {
    state.db.get_all_kv()
}

#[allow(clippy::too_many_arguments)]
#[tauri::command]
fn discord_update(
    enabled: bool,
    client_id: String,
    title: String,
    artist: String,
    album: String,
    position_secs: f64,
    duration_secs: f64,
    playing: bool,
    state: State<AppState>,
) {
    state.discord.update(DiscordTrackState {
        enabled,
        client_id,
        title,
        artist,
        album,
        position_secs,
        duration_secs,
        playing,
    });
}

#[tauri::command]
fn discord_clear(state: State<AppState>) {
    state.discord.clear();
}

#[allow(clippy::too_many_arguments)]
#[tauri::command]
fn smtc_update(
    title: String,
    artist: String,
    album: String,
    duration_secs: f64,
    position_secs: f64,
    playing: bool,
    art_hash: Option<String>,
    state: State<AppState>,
) {
    let Ok(guard) = state.smtc.lock() else {
        return;
    };
    let Some(smtc) = guard.as_ref() else {
        return;
    };
    let art_path = art_hash.map(|h| state.art_dir.join(format!("{h}.jpg")));
    smtc.update(
        &title,
        &artist,
        &album,
        duration_secs,
        position_secs,
        playing,
        art_path.as_deref(),
    );
}

#[tauri::command]
fn smtc_clear(state: State<AppState>) {
    if let Ok(guard) = state.smtc.lock() {
        if let Some(smtc) = guard.as_ref() {
            smtc.clear();
        }
    }
}

#[tauri::command]
fn reveal_in_explorer(path: String) -> Result<(), String> {
    #[cfg(target_os = "windows")]
    {
        std::process::Command::new("explorer.exe")
            .args(["/select,", &path])
            .spawn()
            .map_err(|e| e.to_string())?;
    }
    #[cfg(not(target_os = "windows"))]
    {
        let _ = path;
    }
    Ok(())
}

#[tauri::command]
fn set_window_theme(theme: String, app: AppHandle) {
    if let Some(win) = app.get_webview_window("main") {
        let t = if theme.eq_ignore_ascii_case("light") {
            Some(tauri::Theme::Light)
        } else {
            Some(tauri::Theme::Dark)
        };
        let _ = win.set_theme(t);
    }
}

#[tauri::command]
fn set_window_zoom(zoom: f64, app: AppHandle) -> Result<(), String> {
    let clamped = if zoom.is_finite() {
        zoom.clamp(0.65, 1.5)
    } else {
        1.0
    };
    if let Some(win) = app.get_webview_window("main") {
        win.set_zoom(clamped).map_err(|e| e.to_string())?;
    }
    Ok(())
}

#[tauri::command]
async fn fetch_lyrics_source(
    source: String,
    track_path: String,
    title: String,
    artist: String,
    album: String,
    duration: f64,
    state: State<'_, AppState>,
) -> Result<Option<LyricsResult>, String> {
    if source == "local" {
        return Ok(load_local_lyrics_impl(&track_path, &state.lyrics_dir));
    }
    if source == "subsonic" {
        let db = Arc::clone(&state.db);
        return tauri::async_runtime::spawn_blocking(move || {
            let Some((content, synced)) = subsonic::fetch_subsonic_lyrics(&db, &artist, &title) else {
                return None;
            };
            let fmt = if synced { "lrc" } else { "txt" };
            Some(LyricsResult {
                source: "subsonic".into(),
                format: fmt.into(),
                content,
                path: None,
            })
        })
        .await
        .map_err(|e| e.to_string());
    }
    tauri::async_runtime::spawn_blocking(move || {
        fetch_from_provider(&source, &title, &artist, &album, duration).map(|c| LyricsResult {
            source: c.provider,
            format: c.format,
            content: c.content,
            path: None,
        })
    })
    .await
    .map_err(|e| e.to_string())
}

#[tauri::command]
fn get_launch_files(state: State<AppState>) -> Vec<String> {
    if let Ok(mut guard) = state.launch_files.lock() {
        let out = guard.clone();
        guard.clear();
        return out;
    }
    Vec::new()
}

pub fn run() {
    let launch_args: Vec<String> = std::env::args()
        .skip(1)
        .filter(|a| is_audio_path(Path::new(a)))
        .collect();

    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .setup(move |app| {
            let data_dir = app
                .path()
                .app_local_data_dir()
                .unwrap_or_else(|_| PathBuf::from("."));
            let db_path = data_dir.join("library.db");
            let art_dir = data_dir.join("art");
            let lyrics_dir = data_dir.join("lyrics");
            let _ = std::fs::create_dir_all(&art_dir);
            let _ = std::fs::create_dir_all(&lyrics_dir);

            let db = Arc::new(Db::open(&db_path).expect("failed to initialize database"));
            legacy::migrate_legacy_v1_if_needed(&db);

            let audio = AudioEngine::start(app.handle().clone());
            let discord = DiscordRpc::start();

            let mut hwnd_raw = None;
            if let Some(win) = app.get_webview_window("main") {
                #[cfg(target_os = "windows")]
                {
                    if let Ok(hwnd) = win.hwnd() {
                        hwnd_raw = Some(hwnd.0 as *mut std::ffi::c_void);
                    }
                }
                if let Ok(Some(saved_theme)) = db.get_kv("theme") {
                    let t = if saved_theme == "light" {
                        Some(tauri::Theme::Light)
                    } else {
                        Some(tauri::Theme::Dark)
                    };
                    let _ = win.set_theme(t);
                }
                if let Ok(Some(saved_zoom)) = db.get_kv("ui_zoom") {
                    if let Ok(z) = saved_zoom.parse::<f64>() {
                        if z.is_finite() {
                            let _ = win.set_zoom(z.clamp(0.65, 1.5));
                        }
                    }
                }
            }

            let smtc = SmtcController::init(app.handle(), hwnd_raw);

            let state = AppState {
                db,
                audio,
                discord,
                smtc: Mutex::new(Some(smtc)),
                art_dir,
                lyrics_dir,
                launch_files: Mutex::new(launch_args),
                watcher: Mutex::new(None),
            };

            rebuild_watchers(&state, app.handle());
            app.manage(state);
            app.manage(downloads::DownloadManager::default());

            // Background startup incremental scan so the window renders immediately
            let handle = app.handle().clone();
            std::thread::spawn(move || {
                let st = handle.state::<AppState>();
                if let Ok(changed) =
                    scan::scan_library(&st.db, &st.art_dir, Some(&handle), None)
                {
                    if changed > 0 {
                        let _ = handle.emit("library-updated", ());
                    }
                }
            });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_library,
            get_folders,
            add_folder,
            remove_folder,
            scan_folders,
            get_art_dir,
            audio_play,
            audio_pause,
            audio_seek,
            audio_stop,
            audio_set_volume,
            audio_set_mute,
            audio_set_speed,
            audio_set_eq,
            audio_state,
            audio_devices,
            audio_set_device,
            toggle_like,
            record_play,
            get_recently_played,
            get_playlists,
            get_playlist_tracks,
            create_playlist,
            rename_playlist,
            delete_playlist,
            add_tracks_to_playlist,
            set_playlist_tracks,
            export_m3u8,
            import_m3u8,
            read_tags,
            write_tags,
            get_track_offset,
            set_track_offset,
            load_local_lyrics,
            fetch_online_lyrics,
            fetch_lyrics_source,
            search_online_lyrics,
            save_lyrics_choice,
            assign_lyrics_file,
            clear_cached_lyrics,
            get_kv,
            set_kv,
            get_all_kv,
            discord_update,
            discord_clear,
            smtc_update,
            smtc_clear,
            reveal_in_explorer,
            set_window_theme,
            set_window_zoom,
            get_launch_files,
            downloads::tools_status,
            downloads::tool_install,
            downloads::ytdlp_update,
            downloads::download_default_folder,
            downloads::download_add,
            downloads::download_cancel,
            subsonic::subsonic_status,
            subsonic::subsonic_connect,
            subsonic::subsonic_logout,
            translate::google_translate,
            translate::jisho_lookup,
            quran::quran_fetch,
            quran::quran_pauses,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Localify");
}
