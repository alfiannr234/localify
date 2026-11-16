use souvlaki::{
    MediaControlEvent, MediaControls, MediaMetadata, MediaPlayback, MediaPosition, PlatformConfig,
};
use std::ffi::c_void;
use std::path::Path;
use std::sync::Mutex;
use std::time::Duration;
use tauri::{AppHandle, Emitter};

pub struct SmtcController {
    controls: Mutex<Option<MediaControls>>,
}

unsafe impl Send for SmtcController {}
unsafe impl Sync for SmtcController {}

impl SmtcController {
    pub fn init(app: &AppHandle, hwnd_raw: Option<*mut c_void>) -> Self {
        let config = PlatformConfig {
            dbus_name: "com.localify.app",
            display_name: "Localify",
            hwnd: hwnd_raw,
        };

        let controls = match MediaControls::new(config) {
            Ok(mut mc) => {
                let app_handle = app.clone();
                let _ = mc.attach(move |event: MediaControlEvent| {
                    let name = match event {
                        MediaControlEvent::Play => "play",
                        MediaControlEvent::Pause => "pause",
                        MediaControlEvent::Toggle => "toggle",
                        MediaControlEvent::Next => "next",
                        MediaControlEvent::Previous => "previous",
                        MediaControlEvent::Stop => "stop",
                        MediaControlEvent::Raise => "raise",
                        MediaControlEvent::Quit => "quit",
                        MediaControlEvent::SetPosition(pos) => {
                            let _ = app_handle.emit("media-seek", pos.0.as_secs_f64());
                            return;
                        }
                        MediaControlEvent::OpenUri(_)
                        | MediaControlEvent::SetVolume(_)
                        | MediaControlEvent::Seek(_)
                        | MediaControlEvent::SeekBy(_, _) => return,
                    };
                    let _ = app_handle.emit("media-control", name);
                });
                Some(mc)
            }
            Err(_) => None,
        };

        Self {
            controls: Mutex::new(controls),
        }
    }

    #[allow(clippy::too_many_arguments)]
    pub fn update(
        &self,
        title: &str,
        artist: &str,
        album: &str,
        duration_secs: f64,
        position_secs: f64,
        playing: bool,
        art_path: Option<&Path>,
    ) {
        let Ok(mut guard) = self.controls.lock() else {
            return;
        };
        let Some(mc) = guard.as_mut() else {
            return;
        };

        let cover_url = art_path.and_then(|p| {
            if p.exists() {
                Some(format!("file:///{}", p.to_string_lossy().replace('\\', "/")))
            } else {
                None
            }
        });

        let _ = mc.set_metadata(MediaMetadata {
            title: Some(title),
            artist: Some(artist),
            album: Some(album),
            duration: (duration_secs > 0.0).then(|| Duration::from_secs_f64(duration_secs)),
            cover_url: cover_url.as_deref(),
        });

        let pos = MediaPosition(Duration::from_secs_f64(position_secs.max(0.0)));
        let pb = if playing {
            MediaPlayback::Playing {
                progress: Some(pos),
            }
        } else {
            MediaPlayback::Paused {
                progress: Some(pos),
            }
        };
        let _ = mc.set_playback(pb);
    }

    pub fn clear(&self) {
        if let Ok(mut guard) = self.controls.lock() {
            if let Some(mc) = guard.as_mut() {
                let _ = mc.set_playback(MediaPlayback::Stopped);
            }
        }
    }
}
