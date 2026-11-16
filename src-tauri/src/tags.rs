use crate::db::{unix_now, Db, Track};
use crate::scan::read_track_metadata;
use lofty::config::WriteOptions;
use lofty::file::AudioFile;
use lofty::picture::{MimeType, Picture, PictureType};
use lofty::prelude::{Accessor, ItemKey, TaggedFileExt};
use lofty::tag::Tag;
use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FullTags {
    pub path: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub genre: String,
    pub year: Option<i32>,
    pub track_no: Option<i32>,
    pub disc_no: Option<i32>,
    pub comment: String,
    pub lyrics: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TagEditPayload {
    pub path: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub genre: String,
    pub year: Option<i32>,
    pub track_no: Option<i32>,
    pub disc_no: Option<i32>,
    pub comment: String,
    pub lyrics: String,
    pub cover_path: Option<String>,
}

pub fn read_full_tags(path_str: &str) -> Result<FullTags, String> {
    let path = Path::new(path_str);
    let tf = lofty::read_from_path(path).map_err(|e| format!("Failed to read tags: {e}"))?;
    let tag = tf.primary_tag().or_else(|| tf.first_tag());

    let mut out = FullTags {
        path: path_str.to_string(),
        title: String::new(),
        artist: String::new(),
        album: String::new(),
        album_artist: String::new(),
        genre: String::new(),
        year: None,
        track_no: None,
        disc_no: None,
        comment: String::new(),
        lyrics: String::new(),
    };

    if let Some(t) = tag {
        out.title = t.title().map(|s| s.to_string()).unwrap_or_default();
        out.artist = t.artist().map(|s| s.to_string()).unwrap_or_default();
        out.album = t.album().map(|s| s.to_string()).unwrap_or_default();
        out.album_artist = t
            .get_string(&ItemKey::AlbumArtist)
            .map(|s| s.to_string())
            .unwrap_or_default();
        out.genre = t.genre().map(|s| s.to_string()).unwrap_or_default();
        out.year = t.year().map(|y| y as i32);
        out.track_no = t.track().map(|v| v as i32);
        out.disc_no = t.disk().map(|v| v as i32);
        out.comment = t.comment().map(|s| s.to_string()).unwrap_or_default();
        out.lyrics = t
            .get_string(&ItemKey::Lyrics)
            .map(|s| s.to_string())
            .unwrap_or_default();
    }

    Ok(out)
}

pub fn write_full_tags(
    payload: &TagEditPayload,
    db: &Db,
    art_dir: &Path,
) -> Result<Track, String> {
    let path = Path::new(&payload.path);
    let mut tf = lofty::read_from_path(path).map_err(|e| format!("Failed to open audio file: {e}"))?;

    let primary_type = tf.primary_tag_type();
    if tf.primary_tag().is_none() {
        tf.insert_tag(Tag::new(primary_type));
    }

    if let Some(tag) = tf.primary_tag_mut() {
        tag.set_title(payload.title.trim().to_string());
        tag.set_artist(payload.artist.trim().to_string());
        tag.set_album(payload.album.trim().to_string());

        let aa = payload.album_artist.trim();
        if aa.is_empty() {
            tag.remove_key(&ItemKey::AlbumArtist);
        } else {
            tag.insert_text(ItemKey::AlbumArtist, aa.to_string());
        }

        let g = payload.genre.trim();
        if g.is_empty() {
            tag.remove_key(&ItemKey::Genre);
        } else {
            tag.set_genre(g.to_string());
        }

        match payload.year {
            Some(y) if y > 0 => tag.set_year(y as u32),
            _ => tag.remove_key(&ItemKey::Year),
        }
        match payload.track_no {
            Some(n) if n > 0 => tag.set_track(n as u32),
            _ => tag.remove_key(&ItemKey::TrackNumber),
        }
        match payload.disc_no {
            Some(n) if n > 0 => tag.set_disk(n as u32),
            _ => tag.remove_key(&ItemKey::DiscNumber),
        }

        let comment = payload.comment.trim();
        if comment.is_empty() {
            tag.remove_key(&ItemKey::Comment);
        } else {
            tag.set_comment(comment.to_string());
        }

        let lyrics = payload.lyrics.trim();
        if lyrics.is_empty() {
            tag.remove_key(&ItemKey::Lyrics);
        } else {
            tag.insert_text(ItemKey::Lyrics, lyrics.to_string());
        }

        if let Some(cover_src) = &payload.cover_path {
            let bytes = std::fs::read(cover_src).map_err(|e| format!("Failed to read cover: {e}"))?;
            let ext = Path::new(cover_src)
                .extension()
                .and_then(|e| e.to_str())
                .unwrap_or("")
                .to_ascii_lowercase();
            let mime = match ext.as_str() {
                "png" => MimeType::Png,
                "webp" => MimeType::Unknown("image/webp".into()),
                _ => MimeType::Jpeg,
            };
            let pic = Picture::new_unchecked(PictureType::CoverFront, Some(mime), None, bytes);
            tag.set_picture(0, pic);
        }
    }

    tf.save_to_path(path, WriteOptions::default())
        .map_err(|e| format!("Failed to save metadata (if playing, pause or close file locks): {e}"))?;

    let track = read_track_metadata(path, art_dir, unix_now())
        .ok_or_else(|| "Failed to re-read saved metadata".to_string())?;
    db.upsert_tracks_batch(std::slice::from_ref(&track), &[])?;
    db.get_track(&payload.path)?
        .ok_or_else(|| "Track missing after update".into())
}
