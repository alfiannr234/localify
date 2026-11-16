use crate::lyrics_online::percent_encode;
use std::time::Duration;

const ENDPOINT: &str = "https://translate.googleapis.com/translate_a/single";

pub fn valid_lang(code: &str) -> bool {
    let mut parts = code.split('-');
    let base = parts.next().unwrap_or("");
    let region = parts.next();
    (2..=3).contains(&base.len())
        && base.bytes().all(|b| b.is_ascii_alphabetic())
        && region.is_none_or(|r| (2..=4).contains(&r.len()) && r.bytes().all(|b| b.is_ascii_alphanumeric()))
        && parts.next().is_none()
}

pub fn http1_agent() -> ureq::Agent {
    ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(6))
        .timeout_read(Duration::from_secs(15))
        .user_agent("Localify/2.0")
        .build()
}

#[tauri::command]
pub async fn google_translate(
    text: String,
    from: String,
    to: String,
    dictionary: bool,
) -> Result<String, String> {
    if !(from == "auto" || valid_lang(&from)) || !valid_lang(&to) {
        return Err("Invalid language code.".into());
    }
    if text.len() > 20_000 {
        return Err("Text too long for one request.".into());
    }

    tauri::async_runtime::spawn_blocking(move || {
        let mut url = format!(
            "{ENDPOINT}?client=gtx&sl={}&tl={}&dt=t",
            percent_encode(&from),
            percent_encode(&to)
        );
        if dictionary {
            url.push_str("&dt=rm&dt=bd");
        }
        let body = format!("q={}", percent_encode(&text));
        let resp = http1_agent()
            .post(&url)
            .set("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .send_string(&body);
        match resp {
            Ok(r) => r.into_string().map_err(|e| e.to_string()),
            Err(ureq::Error::Status(429, _)) => {
                Err("Translation service is busy. Try again shortly.".into())
            }
            Err(ureq::Error::Status(code, _)) => {
                Err(format!("Translation service returned HTTP {code}"))
            }
            Err(e) => Err(format!("Network error: {e}")),
        }
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
pub async fn jisho_lookup(word: String) -> Result<String, String> {
    let trimmed = word.trim().to_string();
    if trimmed.is_empty() || trimmed.len() > 100 {
        return Err("Invalid query.".into());
    }
    tauri::async_runtime::spawn_blocking(move || {
        let url = format!(
            "https://jisho.org/api/v1/search/words?keyword={}",
            percent_encode(&trimmed)
        );
        let resp = http1_agent()
            .get(&url)
            .call()
            .map_err(|e| format!("Jisho lookup failed: {e}"))?;
        resp.into_string().map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| e.to_string())?
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_language_codes() {
        assert!(valid_lang("id"));
        assert!(valid_lang("zh-CN"));
        assert!(!valid_lang("english"));
        assert!(!valid_lang("../x"));
    }
}
