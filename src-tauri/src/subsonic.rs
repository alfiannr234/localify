use crate::db::Db;
use md5::{Digest, Md5};
use serde::{Deserialize, Serialize};
use std::time::Duration;
use tauri::State;

const KV_CREDS: &str = "subsonic_creds";

#[derive(Serialize, Deserialize, Clone)]
pub struct SubsonicCreds {
    pub server: String,
    pub user: String,
    pub password: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SubsonicAccount {
    pub server: String,
    pub user: String,
}

pub fn normalize_server(s: &str) -> Result<String, String> {
    let trimmed = s.trim();
    let full = if trimmed.contains("://") {
        trimmed.to_string()
    } else {
        format!("http://{trimmed}")
    };
    let u = url::Url::parse(&full).map_err(|_| "Invalid server address.".to_string())?;
    if !matches!(u.scheme(), "http" | "https")
        || u.host_str().is_none()
        || u.query().is_some()
        || u.fragment().is_some()
        || !u.username().is_empty()
    {
        return Err("Use an address like https://music.example.com or 192.168.1.5:4533.".into());
    }
    Ok(u.as_str().trim_end_matches('/').to_string())
}

pub fn valid_id(id: &str) -> bool {
    (1..=128).contains(&id.len())
        && id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'-' | b'_' | b'.'))
}

fn gen_salt() -> Result<String, String> {
    let mut b = [0u8; 12];
    getrandom::getrandom(&mut b).map_err(|e| e.to_string())?;
    Ok(b.iter().map(|x| format!("{x:02x}")).collect())
}

pub fn signed_url(
    c: &SubsonicCreds,
    endpoint: &str,
    params: &[(&str, String)],
    salt: &str,
) -> Result<String, String> {
    let mut u =
        url::Url::parse(&format!("{}/rest/{endpoint}", c.server)).map_err(|e| e.to_string())?;
    let mut hasher = Md5::new();
    hasher.update(format!("{}{salt}", c.password).as_bytes());
    let token = format!("{:x}", hasher.finalize());

    u.query_pairs_mut()
        .append_pair("u", &c.user)
        .append_pair("t", &token)
        .append_pair("s", salt)
        .append_pair("v", "1.16.1")
        .append_pair("c", "Localify")
        .append_pair("f", "json")
        .extend_pairs(params.iter().map(|(k, v)| (*k, v.as_str())));
    Ok(u.to_string())
}

fn load_creds(db: &Db) -> Option<SubsonicCreds> {
    db.get_kv(KV_CREDS)
        .ok()
        .flatten()
        .and_then(|s| serde_json::from_str(&s).ok())
}

fn subsonic_get(
    c: &SubsonicCreds,
    endpoint: &str,
    params: &[(&str, String)],
) -> Result<serde_json::Value, String> {
    let salt = gen_salt()?;
    let url = signed_url(c, endpoint, params, &salt)?;
    let agent = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(6))
        .timeout_read(Duration::from_secs(12))
        .build();
    let resp = agent
        .get(&url)
        .call()
        .map_err(|e| format!("Cannot reach server: {e}"))?;
    let val: serde_json::Value = resp
        .into_json()
        .map_err(|_| "Unexpected response from Subsonic server.".to_string())?;
    unwrap_subsonic(val)
}

pub fn unwrap_subsonic(v: serde_json::Value) -> Result<serde_json::Value, String> {
    let r = v
        .get("subsonic-response")
        .cloned()
        .ok_or("Not a valid Subsonic response.")?;
    if r["status"] == "ok" {
        Ok(r)
    } else {
        Err(r["error"]["message"]
            .as_str()
            .unwrap_or("Server refused request.")
            .to_string())
    }
}

pub fn structured_to_lrc(list: &serde_json::Value) -> String {
    let all = list.as_array().map(Vec::as_slice).unwrap_or_default();
    let Some(l) = all.iter().find(|l| l["synced"] == true).or(all.first()) else {
        return String::new();
    };
    let lines = l["line"].as_array().map(Vec::as_slice).unwrap_or_default();
    let mut out = String::new();
    if l["synced"] == true {
        if let Some(o) = l["offset"].as_i64().filter(|o| *o != 0) {
            out.push_str(&format!("[offset:{o}]\n"));
        }
        for x in lines {
            let ms = x["start"].as_i64().unwrap_or(0).max(0);
            let txt = x["value"].as_str().unwrap_or_default();
            out.push_str(&format!(
                "[{:02}:{:02}.{:03}]{txt}\n",
                ms / 60000,
                (ms / 1000) % 60,
                ms % 1000
            ));
        }
    } else {
        for x in lines {
            out.push_str(x["value"].as_str().unwrap_or_default());
            out.push('\n');
        }
    }
    if out.trim().is_empty() {
        String::new()
    } else {
        out
    }
}

pub fn fetch_subsonic_lyrics(db: &Db, artist: &str, title: &str) -> Option<(String, bool)> {
    let c = load_creds(db)?;
    // Search matching song id first so OpenSubsonic timed lyrics work even for local files
    let query = format!("{} {}", artist.trim(), title.trim());
    if let Ok(search_res) = subsonic_get(
        &c,
        "search3",
        &[
            ("query", query.trim().to_string()),
            ("artistCount", "0".into()),
            ("albumCount", "0".into()),
            ("songCount", "5".into()),
        ],
    ) {
        if let Some(songs) = search_res["searchResult3"]["song"].as_array() {
            if let Some(id) = songs.first().and_then(|s| s["id"].as_str()) {
                if valid_id(id) {
                    if let Ok(r) = subsonic_get(&c, "getLyricsBySongId", &[("id", id.into())]) {
                        let lrc = structured_to_lrc(&r["lyricsList"]["structuredLyrics"]);
                        if !lrc.is_empty() {
                            let synced = lrc.contains('[');
                            return Some((lrc, synced));
                        }
                    }
                }
            }
        }
    }

    if let Ok(r) = subsonic_get(
        &c,
        "getLyrics",
        &[("artist", artist.to_string()), ("title", title.to_string())],
    ) {
        let plain = r["lyrics"]["value"].as_str().unwrap_or_default().trim();
        if !plain.is_empty() {
            let synced = plain.lines().any(|l| l.trim_start().starts_with('['));
            return Some((plain.to_string(), synced));
        }
    }
    None
}

#[tauri::command]
pub fn subsonic_status(state: State<crate::AppState>) -> Option<SubsonicAccount> {
    load_creds(&state.db).map(|c| SubsonicAccount {
        server: c.server,
        user: c.user,
    })
}

#[tauri::command]
pub async fn subsonic_connect(
    server: String,
    user: String,
    password: String,
    state: State<'_, crate::AppState>,
) -> Result<SubsonicAccount, String> {
    if user.trim().is_empty() || password.is_empty() {
        return Err("Enter your username and password.".into());
    }
    let norm = normalize_server(&server)?;
    let creds = SubsonicCreds {
        server: norm,
        user: user.trim().to_string(),
        password,
    };
    let check_creds = creds.clone();
    tauri::async_runtime::spawn_blocking(move || subsonic_get(&check_creds, "ping", &[]))
        .await
        .map_err(|e| e.to_string())??;

    let raw = serde_json::to_string(&creds).map_err(|e| e.to_string())?;
    state.db.set_kv(KV_CREDS, &raw)?;
    Ok(SubsonicAccount {
        server: creds.server,
        user: creds.user,
    })
}

#[tauri::command]
pub fn subsonic_logout(state: State<crate::AppState>) -> Result<(), String> {
    state.db.set_kv(KV_CREDS, "")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn signs_subsonic_url_with_md5_token() {
        let c = SubsonicCreds {
            server: "https://m.example.com".into(),
            user: "me".into(),
            password: "sesame".into(),
        };
        let u = signed_url(&c, "ping", &[], "c19b2d").unwrap();
        assert!(u.contains("t=26719a1196d2a940705a59634eb18eab"));
        assert!(!u.contains("sesame"));
    }

    #[test]
    fn converts_structured_lyrics_to_lrc() {
        let list = serde_json::json!([
            {"synced": true, "offset": -100, "line": [{"start": 1500, "value": "Hello"}]}
        ]);
        assert_eq!(structured_to_lrc(&list), "[offset:-100]\n[00:01.500]Hello\n");
    }
}
