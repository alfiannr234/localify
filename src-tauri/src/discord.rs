use crate::lyrics_online::percent_encode;
use serde_json::json;
use std::collections::HashMap;
use std::fs::{File, OpenOptions};
use std::io::{Read, Write};
use std::sync::mpsc;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

const DEFAULT_CLIENT_ID: &str = "1570070424627191808";
const LOCALIFY_LOGO_URL: &str =
    "https://raw.githubusercontent.com/Alfiannr234/localify/main/localify.png";

const OP_HANDSHAKE: u32 = 0;
const OP_FRAME: u32 = 1;
const OP_CLOSE: u32 = 2;

#[derive(Debug, Clone)]
pub struct DiscordTrackState {
    pub enabled: bool,
    pub client_id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub position_secs: f64,
    pub duration_secs: f64,
    pub playing: bool,
}

enum RpcCmd {
    Update(DiscordTrackState),
    Clear,
}

pub struct DiscordRpc {
    tx: mpsc::Sender<RpcCmd>,
}

impl DiscordRpc {
    pub fn start() -> Self {
        let (tx, rx) = mpsc::channel::<RpcCmd>();
        std::thread::spawn(move || run_rpc_worker(rx));
        Self { tx }
    }

    pub fn update(&self, state: DiscordTrackState) {
        let _ = self.tx.send(RpcCmd::Update(state));
    }

    pub fn clear(&self) {
        let _ = self.tx.send(RpcCmd::Clear);
    }
}

fn run_rpc_worker(rx: mpsc::Receiver<RpcCmd>) {
    let mut pipe: Option<File> = None;
    let mut active_client_id = String::new();
    let mut art_cache: HashMap<String, Option<String>> = HashMap::new();
    let http = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(4))
        .timeout_read(Duration::from_secs(4))
        .build();

    while let Ok(mut cmd) = rx.recv() {
        // Coalesce bursts of queued updates so only latest state is dispatched
        while let Ok(newer) = rx.try_recv() {
            cmd = newer;
        }

        match cmd {
            RpcCmd::Clear => {
                if let Some(mut f) = pipe.take() {
                    let _ = send_clear(&mut f);
                    let _ = send_frame(&mut f, OP_CLOSE, "{}");
                }
                active_client_id.clear();
            }
            RpcCmd::Update(st) => {
                if !st.enabled || st.title.trim().is_empty() {
                    if let Some(mut f) = pipe.take() {
                        let _ = send_clear(&mut f);
                        let _ = send_frame(&mut f, OP_CLOSE, "{}");
                    }
                    active_client_id.clear();
                    continue;
                }

                let cid = if st.client_id.trim().is_empty() {
                    DEFAULT_CLIENT_ID.to_string()
                } else {
                    st.client_id.trim().to_string()
                };

                if active_client_id != cid {
                    if let Some(mut old) = pipe.take() {
                        let _ = send_frame(&mut old, OP_CLOSE, "{}");
                    }
                    active_client_id = cid.clone();
                }

                if pipe.is_none() {
                    pipe = connect_discord_ipc(&cid);
                }

                let Some(f) = pipe.as_mut() else {
                    continue;
                };

                let cover_url = lookup_cover_url(&http, &mut art_cache, &st.artist, &st.title);
                if send_activity(f, &st, cover_url.as_deref()).is_err() {
                    pipe = None;
                    if let Some(mut reconn) = connect_discord_ipc(&cid) {
                        if send_activity(&mut reconn, &st, cover_url.as_deref()).is_ok() {
                            pipe = Some(reconn);
                        }
                    }
                }
            }
        }
    }
}

fn connect_discord_ipc(client_id: &str) -> Option<File> {
    for idx in 0..10 {
        let pipe_name = format!(r"\\.\pipe\discord-ipc-{idx}");
        let Ok(mut f) = OpenOptions::new().read(true).write(true).open(&pipe_name) else {
            continue;
        };
        let hs = json!({
            "v": 1,
            "client_id": client_id,
        })
        .to_string();
        if send_frame(&mut f, OP_HANDSHAKE, &hs).is_ok() {
            let _ = read_frame(&mut f);
            return Some(f);
        }
    }
    None
}

fn send_activity(
    pipe: &mut File,
    st: &DiscordTrackState,
    cover_url: Option<&str>,
) -> std::io::Result<()> {
    let now_secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0);

    let artist = if st.artist.trim().is_empty() {
        "Unknown Artist"
    } else {
        st.artist.trim()
    };

    let mut assets = json!({
        "large_image": cover_url.unwrap_or(LOCALIFY_LOGO_URL),
    });
    if cover_url.is_some() {
        assets["small_image"] = json!(LOCALIFY_LOGO_URL);
        assets["small_text"] = json!("Localify");
    }

    let mut activity = json!({
        "type": 2,
        "name": artist,
        "details": clamp_str(&st.title, 120),
        "state": clamp_str(artist, 120),
        "assets": assets,
        "instance": false
    });

    if st.playing && st.duration_secs > 1.0 {
        let start = now_secs - st.position_secs.max(0.0).floor() as i64;
        let end = start + st.duration_secs.ceil() as i64;
        activity["timestamps"] = json!({
            "start": start,
            "end": end,
        });
    }

    let payload = json!({
        "cmd": "SET_ACTIVITY",
        "args": {
            "pid": std::process::id(),
            "activity": activity,
        },
        "nonce": format!("{now_secs}")
    })
    .to_string();

    send_frame(pipe, OP_FRAME, &payload)
}

fn send_clear(pipe: &mut File) -> std::io::Result<()> {
    let payload = json!({
        "cmd": "SET_ACTIVITY",
        "args": {
            "pid": std::process::id(),
            "activity": serde_json::Value::Null
        },
        "nonce": "clear"
    })
    .to_string();
    send_frame(pipe, OP_FRAME, &payload)
}

fn send_frame(pipe: &mut File, opcode: u32, json_str: &str) -> std::io::Result<()> {
    let data = json_str.as_bytes();
    let len = data.len() as u32;
    let mut buf = Vec::with_capacity(8 + data.len());
    buf.extend_from_slice(&opcode.to_le_bytes());
    buf.extend_from_slice(&len.to_le_bytes());
    buf.extend_from_slice(data);
    pipe.write_all(&buf)?;
    pipe.flush()
}

fn read_frame(pipe: &mut File) -> std::io::Result<()> {
    let mut header = [0u8; 8];
    pipe.read_exact(&mut header)?;
    let len = u32::from_le_bytes([header[4], header[5], header[6], header[7]]) as usize;
    if len > 0 && len < 65536 {
        let mut body = vec![0u8; len];
        pipe.read_exact(&mut body)?;
    }
    Ok(())
}

fn lookup_cover_url(
    agent: &ureq::Agent,
    cache: &mut HashMap<String, Option<String>>,
    artist: &str,
    title: &str,
) -> Option<String> {
    let key = format!("{}::{}", artist.to_lowercase(), title.to_lowercase());
    if let Some(cached) = cache.get(&key) {
        return cached.clone();
    }
    let query = format!("{artist} {title}");
    let url = format!(
        "https://itunes.apple.com/search?media=music&entity=song&limit=1&term={}",
        percent_encode(query.trim())
    );
    let mut found = agent.get(&url).call().ok().and_then(|resp| {
        let v = resp.into_json::<serde_json::Value>().ok()?;
        let art = v
            .get("results")?
            .as_array()?
            .first()?
            .get("artworkUrl100")?
            .as_str()?;
        Some(art.replace("100x100bb", "512x512bb"))
    });
    if found.is_none() {
        let dz_url = format!(
            "https://api.deezer.com/search?limit=1&q={}",
            percent_encode(query.trim())
        );
        found = agent.get(&dz_url).call().ok().and_then(|resp| {
            let v = resp.into_json::<serde_json::Value>().ok()?;
            let first = v.get("data")?.as_array()?.first()?;
            let album = first.get("album")?;
            album
                .get("cover_big")
                .or_else(|| album.get("cover_medium"))
                .and_then(|s| s.as_str())
                .map(|s| s.to_string())
        });
    }
    cache.insert(key, found.clone());
    found
}

fn clamp_str(s: &str, max_chars: usize) -> String {
    let t = s.trim();
    if t.chars().count() <= max_chars {
        t.to_string()
    } else {
        t.chars().take(max_chars).collect()
    }
}
