use rusqlite::{params, Connection, OptionalExtension};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::Path;
use std::sync::Mutex;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Track {
    pub path: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub genre: String,
    pub year: Option<i32>,
    pub track_no: Option<i32>,
    pub disc_no: Option<i32>,
    pub duration: f64,
    pub bitrate: Option<i32>,
    pub sample_rate: Option<i32>,
    pub bit_depth: Option<i32>,
    pub channels: Option<i32>,
    pub codec: Option<String>,
    pub file_size: i64,
    pub art_hash: Option<String>,
    pub art_color: Option<String>,
    pub modified: i64,
    pub added_at: i64,
    pub liked: bool,
    pub play_count: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaylistSummary {
    pub id: i64,
    pub name: String,
    pub created_at: i64,
    pub track_count: i64,
}

pub struct Db {
    conn: Mutex<Connection>,
}

impl Db {
    pub fn open(db_path: &Path) -> Result<Self, String> {
        if let Some(parent) = db_path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let conn = Connection::open(db_path).map_err(|e| e.to_string())?;
        conn.execute_batch(
            "
            PRAGMA journal_mode = WAL;
            PRAGMA synchronous = NORMAL;
            PRAGMA foreign_keys = ON;

            CREATE TABLE IF NOT EXISTS folders (
                path TEXT PRIMARY KEY,
                added_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS tracks (
                path TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                album_artist TEXT NOT NULL DEFAULT '',
                genre TEXT NOT NULL DEFAULT '',
                year INTEGER,
                track_no INTEGER,
                disc_no INTEGER,
                duration REAL NOT NULL DEFAULT 0,
                bitrate INTEGER,
                sample_rate INTEGER,
                bit_depth INTEGER,
                channels INTEGER,
                codec TEXT,
                file_size INTEGER NOT NULL DEFAULT 0,
                art_hash TEXT,
                art_color TEXT,
                modified INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS liked_tracks (
                path TEXT PRIMARY KEY,
                liked_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS playlist_items (
                playlist_id INTEGER NOT NULL,
                position INTEGER NOT NULL,
                track_path TEXT NOT NULL,
                PRIMARY KEY (playlist_id, position),
                FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS plays (
                track_path TEXT PRIMARY KEY,
                play_count INTEGER NOT NULL DEFAULT 0,
                last_played INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS track_offsets (
                track_key TEXT PRIMARY KEY,
                offset_secs REAL NOT NULL
            );

            CREATE TABLE IF NOT EXISTS kv (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_tracks_album ON tracks(album, album_artist);
            CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks(artist);
            CREATE INDEX IF NOT EXISTS idx_tracks_genre ON tracks(genre);
            ",
        )
        .map_err(|e| e.to_string())?;

        Ok(Self {
            conn: Mutex::new(conn),
        })
    }

    pub fn in_memory() -> Result<Self, String> {
        let conn = Connection::open_in_memory().map_err(|e| e.to_string())?;
        let db = Self {
            conn: Mutex::new(conn),
        };
        db.init_schema()?;
        Ok(db)
    }

    fn init_schema(&self) -> Result<(), String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.execute_batch(
            "
            PRAGMA foreign_keys = ON;
            CREATE TABLE IF NOT EXISTS folders (path TEXT PRIMARY KEY, added_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS tracks (
                path TEXT PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL,
                album_artist TEXT NOT NULL DEFAULT '', genre TEXT NOT NULL DEFAULT '',
                year INTEGER, track_no INTEGER, disc_no INTEGER, duration REAL NOT NULL DEFAULT 0,
                bitrate INTEGER, sample_rate INTEGER, bit_depth INTEGER, channels INTEGER, codec TEXT,
                file_size INTEGER NOT NULL DEFAULT 0, art_hash TEXT, art_color TEXT,
                modified INTEGER NOT NULL DEFAULT 0, added_at INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS liked_tracks (path TEXT PRIMARY KEY, liked_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS playlist_items (playlist_id INTEGER NOT NULL, position INTEGER NOT NULL, track_path TEXT NOT NULL, PRIMARY KEY (playlist_id, position), FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE);
            CREATE TABLE IF NOT EXISTS plays (track_path TEXT PRIMARY KEY, play_count INTEGER NOT NULL DEFAULT 0, last_played INTEGER NOT NULL DEFAULT 0);
            CREATE TABLE IF NOT EXISTS track_offsets (track_key TEXT PRIMARY KEY, offset_secs REAL NOT NULL);
            CREATE TABLE IF NOT EXISTS kv (key TEXT PRIMARY KEY, value TEXT NOT NULL);
            ",
        )
        .map_err(|e| e.to_string())
    }

    pub fn get_folders(&self) -> Result<Vec<String>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare("SELECT path FROM folders ORDER BY added_at ASC")
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map([], |r| r.get::<_, String>(0))
            .map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        for item in rows {
            out.push(item.map_err(|e| e.to_string())?);
        }
        Ok(out)
    }

    pub fn add_folder(&self, path: &str) -> Result<(), String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let now = unix_now();
        conn.execute(
            "INSERT OR IGNORE INTO folders (path, added_at) VALUES (?1, ?2)",
            params![path, now],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    }

    pub fn remove_folder(&self, path: &str) -> Result<(), String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.execute("DELETE FROM folders WHERE path = ?1", params![path])
            .map_err(|e| e.to_string())?;
        let norm = normalize_prefix(path);
        let mut stmt = conn
            .prepare("SELECT path FROM tracks")
            .map_err(|e| e.to_string())?;
        let all_paths: Vec<String> = stmt
            .query_map([], |r| r.get(0))
            .map_err(|e| e.to_string())?
            .filter_map(|r| r.ok())
            .collect();
        drop(stmt);
        for p in all_paths {
            let np = normalize_prefix(&p);
            if np.starts_with(&norm) {
                conn.execute("DELETE FROM tracks WHERE path = ?1", params![p])
                    .map_err(|e| e.to_string())?;
            }
        }
        Ok(())
    }

    pub fn get_modified_map(&self) -> Result<HashMap<String, (i64, i64)>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare("SELECT path, modified, file_size FROM tracks")
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map([], |r| {
                Ok((
                    r.get::<_, String>(0)?,
                    r.get::<_, i64>(1)?,
                    r.get::<_, i64>(2)?,
                ))
            })
            .map_err(|e| e.to_string())?;
        let mut map = HashMap::new();
        for row in rows {
            let (p, m, s) = row.map_err(|e| e.to_string())?;
            map.insert(p, (m, s));
        }
        Ok(map)
    }

    pub fn upsert_tracks_batch(&self, tracks: &[Track], stale_paths: &[String]) -> Result<(), String> {
        let mut conn = self.conn.lock().map_err(|e| e.to_string())?;
        let tx = conn.transaction().map_err(|e| e.to_string())?;
        {
            let mut del = tx
                .prepare("DELETE FROM tracks WHERE path = ?1")
                .map_err(|e| e.to_string())?;
            for p in stale_paths {
                del.execute(params![p]).map_err(|e| e.to_string())?;
            }
        }
        {
            let mut ins = tx
                .prepare(
                    "INSERT INTO tracks (
                        path, title, artist, album, album_artist, genre, year, track_no, disc_no,
                        duration, bitrate, sample_rate, bit_depth, channels, codec, file_size,
                        art_hash, art_color, modified, added_at
                    ) VALUES (
                        ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9,
                        ?10, ?11, ?12, ?13, ?14, ?15, ?16,
                        ?17, ?18, ?19, ?20
                    )
                    ON CONFLICT(path) DO UPDATE SET
                        title = excluded.title,
                        artist = excluded.artist,
                        album = excluded.album,
                        album_artist = excluded.album_artist,
                        genre = excluded.genre,
                        year = excluded.year,
                        track_no = excluded.track_no,
                        disc_no = excluded.disc_no,
                        duration = excluded.duration,
                        bitrate = excluded.bitrate,
                        sample_rate = excluded.sample_rate,
                        bit_depth = excluded.bit_depth,
                        channels = excluded.channels,
                        codec = excluded.codec,
                        file_size = excluded.file_size,
                        art_hash = excluded.art_hash,
                        art_color = excluded.art_color,
                        modified = excluded.modified",
                )
                .map_err(|e| e.to_string())?;
            for t in tracks {
                ins.execute(params![
                    t.path,
                    t.title,
                    t.artist,
                    t.album,
                    t.album_artist,
                    t.genre,
                    t.year,
                    t.track_no,
                    t.disc_no,
                    t.duration,
                    t.bitrate,
                    t.sample_rate,
                    t.bit_depth,
                    t.channels,
                    t.codec,
                    t.file_size,
                    t.art_hash,
                    t.art_color,
                    t.modified,
                    t.added_at
                ])
                .map_err(|e| e.to_string())?;
            }
        }
        tx.commit().map_err(|e| e.to_string())
    }

    pub fn get_all_tracks(&self) -> Result<Vec<Track>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare(
                "SELECT
                    t.path, t.title, t.artist, t.album, t.album_artist, t.genre,
                    t.year, t.track_no, t.disc_no, t.duration, t.bitrate, t.sample_rate,
                    t.bit_depth, t.channels, t.codec, t.file_size, t.art_hash, t.art_color,
                    t.modified, t.added_at,
                    CASE WHEN l.path IS NOT NULL THEN 1 ELSE 0 END AS liked,
                    COALESCE(p.play_count, 0) AS play_count
                FROM tracks t
                LEFT JOIN liked_tracks l ON l.path = t.path
                LEFT JOIN plays p ON p.track_path = t.path
                ORDER BY t.title COLLATE NOCASE ASC",
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map([], map_track_row)
            .map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r.map_err(|e| e.to_string())?);
        }
        Ok(out)
    }

    pub fn get_track(&self, path: &str) -> Result<Option<Track>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare(
                "SELECT
                    t.path, t.title, t.artist, t.album, t.album_artist, t.genre,
                    t.year, t.track_no, t.disc_no, t.duration, t.bitrate, t.sample_rate,
                    t.bit_depth, t.channels, t.codec, t.file_size, t.art_hash, t.art_color,
                    t.modified, t.added_at,
                    CASE WHEN l.path IS NOT NULL THEN 1 ELSE 0 END AS liked,
                    COALESCE(p.play_count, 0) AS play_count
                FROM tracks t
                LEFT JOIN liked_tracks l ON l.path = t.path
                LEFT JOIN plays p ON p.track_path = t.path
                WHERE t.path = ?1",
            )
            .map_err(|e| e.to_string())?;
        stmt.query_row(params![path], map_track_row)
            .optional()
            .map_err(|e| e.to_string())
    }

    pub fn toggle_like(&self, path: &str) -> Result<bool, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let exists: Option<i64> = conn
            .query_row(
                "SELECT 1 FROM liked_tracks WHERE path = ?1",
                params![path],
                |r| r.get(0),
            )
            .optional()
            .map_err(|e| e.to_string())?;
        if exists.is_some() {
            conn.execute("DELETE FROM liked_tracks WHERE path = ?1", params![path])
                .map_err(|e| e.to_string())?;
            Ok(false)
        } else {
            conn.execute(
                "INSERT INTO liked_tracks (path, liked_at) VALUES (?1, ?2)",
                params![path, unix_now()],
            )
            .map_err(|e| e.to_string())?;
            Ok(true)
        }
    }

    pub fn record_play(&self, path: &str) -> Result<i64, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let now = unix_now();
        conn.execute(
            "INSERT INTO plays (track_path, play_count, last_played) VALUES (?1, 1, ?2)
             ON CONFLICT(track_path) DO UPDATE SET play_count = play_count + 1, last_played = ?2",
            params![path, now],
        )
        .map_err(|e| e.to_string())?;
        let count: i64 = conn
            .query_row(
                "SELECT play_count FROM plays WHERE track_path = ?1",
                params![path],
                |r| r.get(0),
            )
            .map_err(|e| e.to_string())?;
        Ok(count)
    }

    pub fn get_recently_played(&self, limit: i64) -> Result<Vec<String>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare(
                "SELECT p.track_path FROM plays p
                 INNER JOIN tracks t ON t.path = p.track_path
                 ORDER BY p.last_played DESC LIMIT ?1",
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map(params![limit], |r| r.get(0))
            .map_err(|e| e.to_string())?;
        Ok(rows.filter_map(|r| r.ok()).collect())
    }

    pub fn get_playlists(&self) -> Result<Vec<PlaylistSummary>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare(
                "SELECT p.id, p.name, p.created_at, COUNT(pi.track_path) AS track_count
                 FROM playlists p
                 LEFT JOIN playlist_items pi ON pi.playlist_id = p.id
                 GROUP BY p.id
                 ORDER BY p.created_at ASC",
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map([], |r| {
                Ok(PlaylistSummary {
                    id: r.get(0)?,
                    name: r.get(1)?,
                    created_at: r.get(2)?,
                    track_count: r.get(3)?,
                })
            })
            .map_err(|e| e.to_string())?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r.map_err(|e| e.to_string())?);
        }
        Ok(out)
    }

    pub fn get_playlist_tracks(&self, playlist_id: i64) -> Result<Vec<String>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare(
                "SELECT track_path FROM playlist_items
                 WHERE playlist_id = ?1
                 ORDER BY position ASC",
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map(params![playlist_id], |r| r.get(0))
            .map_err(|e| e.to_string())?;
        Ok(rows.filter_map(|r| r.ok()).collect())
    }

    pub fn create_playlist(&self, name: &str) -> Result<PlaylistSummary, String> {
        let trimmed = name.trim();
        if trimmed.is_empty() {
            return Err("Playlist name cannot be empty".into());
        }
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let now = unix_now();
        conn.execute(
            "INSERT INTO playlists (name, created_at) VALUES (?1, ?2)",
            params![trimmed, now],
        )
        .map_err(|e| e.to_string())?;
        let id = conn.last_insert_rowid();
        Ok(PlaylistSummary {
            id,
            name: trimmed.to_string(),
            created_at: now,
            track_count: 0,
        })
    }

    pub fn ensure_playlist(&self, name: &str) -> Result<i64, String> {
        let trimmed = name.trim();
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        if let Some(id) = conn
            .query_row(
                "SELECT id FROM playlists WHERE name = ?1",
                params![trimmed],
                |r| r.get::<_, i64>(0),
            )
            .optional()
            .map_err(|e| e.to_string())?
        {
            return Ok(id);
        }
        conn.execute(
            "INSERT INTO playlists (name, created_at) VALUES (?1, ?2)",
            params![trimmed, unix_now()],
        )
        .map_err(|e| e.to_string())?;
        Ok(conn.last_insert_rowid())
    }

    pub fn rename_playlist(&self, id: i64, name: &str) -> Result<(), String> {
        let trimmed = name.trim();
        if trimmed.is_empty() {
            return Err("Playlist name cannot be empty".into());
        }
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.execute(
            "UPDATE playlists SET name = ?1 WHERE id = ?2",
            params![trimmed, id],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    }

    pub fn delete_playlist(&self, id: i64) -> Result<(), String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.execute("DELETE FROM playlists WHERE id = ?1", params![id])
            .map_err(|e| e.to_string())?;
        Ok(())
    }

    pub fn add_tracks_to_playlist(&self, playlist_id: i64, paths: &[String]) -> Result<(), String> {
        let mut conn = self.conn.lock().map_err(|e| e.to_string())?;
        let tx = conn.transaction().map_err(|e| e.to_string())?;
        let max_pos: i64 = tx
            .query_row(
                "SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlist_id = ?1",
                params![playlist_id],
                |r| r.get(0),
            )
            .map_err(|e| e.to_string())?;
        let mut next_pos = max_pos + 1;
        {
            let mut stmt = tx
                .prepare("INSERT INTO playlist_items (playlist_id, position, track_path) VALUES (?1, ?2, ?3)")
                .map_err(|e| e.to_string())?;
            for p in paths {
                stmt.execute(params![playlist_id, next_pos, p])
                    .map_err(|e| e.to_string())?;
                next_pos += 1;
            }
        }
        tx.commit().map_err(|e| e.to_string())
    }

    pub fn set_playlist_tracks(&self, playlist_id: i64, paths: &[String]) -> Result<(), String> {
        let mut conn = self.conn.lock().map_err(|e| e.to_string())?;
        let tx = conn.transaction().map_err(|e| e.to_string())?;
        tx.execute(
            "DELETE FROM playlist_items WHERE playlist_id = ?1",
            params![playlist_id],
        )
        .map_err(|e| e.to_string())?;
        {
            let mut stmt = tx
                .prepare("INSERT INTO playlist_items (playlist_id, position, track_path) VALUES (?1, ?2, ?3)")
                .map_err(|e| e.to_string())?;
            for (i, p) in paths.iter().enumerate() {
                stmt.execute(params![playlist_id, i as i64, p])
                    .map_err(|e| e.to_string())?;
            }
        }
        tx.commit().map_err(|e| e.to_string())
    }

    pub fn get_track_offset(&self, track_path: &str) -> Result<Option<f64>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let pkey = path_offset_key(track_path);
        if let Some(val) = conn
            .query_row(
                "SELECT offset_secs FROM track_offsets WHERE track_key = ?1",
                params![pkey],
                |r| r.get::<_, f64>(0),
            )
            .optional()
            .map_err(|e| e.to_string())?
        {
            return Ok(Some(val));
        }
        if let Some(fkey) = file_offset_key(track_path) {
            return conn
                .query_row(
                    "SELECT offset_secs FROM track_offsets WHERE track_key = ?1",
                    params![fkey],
                    |r| r.get::<_, f64>(0),
                )
                .optional()
                .map_err(|e| e.to_string());
        }
        Ok(None)
    }

    pub fn set_track_offset(&self, track_path: &str, offset_secs: f64) -> Result<(), String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let pkey = path_offset_key(track_path);
        conn.execute(
            "INSERT INTO track_offsets (track_key, offset_secs) VALUES (?1, ?2)
             ON CONFLICT(track_key) DO UPDATE SET offset_secs = excluded.offset_secs",
            params![pkey, offset_secs],
        )
        .map_err(|e| e.to_string())?;
        if let Some(fkey) = file_offset_key(track_path) {
            conn.execute(
                "INSERT INTO track_offsets (track_key, offset_secs) VALUES (?1, ?2)
                 ON CONFLICT(track_key) DO UPDATE SET offset_secs = excluded.offset_secs",
                params![fkey, offset_secs],
            )
            .map_err(|e| e.to_string())?;
        }
        Ok(())
    }

    pub fn set_raw_track_offset(&self, raw_key: &str, offset_secs: f64) -> Result<(), String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.execute(
            "INSERT INTO track_offsets (track_key, offset_secs) VALUES (?1, ?2)
             ON CONFLICT(track_key) DO UPDATE SET offset_secs = excluded.offset_secs",
            params![raw_key, offset_secs],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    }

    pub fn get_all_track_offsets(&self) -> Result<HashMap<String, f64>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare("SELECT track_key, offset_secs FROM track_offsets")
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, f64>(1)?)))
            .map_err(|e| e.to_string())?;
        let mut out = HashMap::new();
        for r in rows {
            let (k, v) = r.map_err(|e| e.to_string())?;
            out.insert(k, v);
        }
        Ok(out)
    }

    pub fn get_kv(&self, key: &str) -> Result<Option<String>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.query_row("SELECT value FROM kv WHERE key = ?1", params![key], |r| {
            r.get(0)
        })
        .optional()
        .map_err(|e| e.to_string())
    }

    pub fn set_kv(&self, key: &str, value: &str) -> Result<(), String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.execute(
            "INSERT INTO kv (key, value) VALUES (?1, ?2)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            params![key, value],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    }

    pub fn get_all_kv(&self) -> Result<HashMap<String, String>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare("SELECT key, value FROM kv")
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?)))
            .map_err(|e| e.to_string())?;
        let mut out = HashMap::new();
        for r in rows {
            let (k, v) = r.map_err(|e| e.to_string())?;
            out.insert(k, v);
        }
        Ok(out)
    }

    pub fn get_settings_kv(&self) -> Result<HashMap<String, String>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare(
                "SELECT key, value FROM kv
                 WHERE key NOT LIKE 'tr:%'
                   AND key NOT LIKE 'tr2:%'
                   AND key NOT LIKE 'quran:%'
                   AND key NOT IN ('vocab_items', 'quran_anchors')",
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?)))
            .map_err(|e| e.to_string())?;
        let mut out = HashMap::new();
        for r in rows {
            let (k, v) = r.map_err(|e| e.to_string())?;
            out.insert(k, v);
        }
        Ok(out)
    }
}

fn map_track_row(r: &rusqlite::Row) -> rusqlite::Result<Track> {
    Ok(Track {
        path: r.get(0)?,
        title: r.get(1)?,
        artist: r.get(2)?,
        album: r.get(3)?,
        album_artist: r.get(4)?,
        genre: r.get(5)?,
        year: r.get(6)?,
        track_no: r.get(7)?,
        disc_no: r.get(8)?,
        duration: r.get(9)?,
        bitrate: r.get(10)?,
        sample_rate: r.get(11)?,
        bit_depth: r.get(12)?,
        channels: r.get(13)?,
        codec: r.get(14)?,
        file_size: r.get(15)?,
        art_hash: r.get(16)?,
        art_color: r.get(17)?,
        modified: r.get(18)?,
        added_at: r.get(19)?,
        liked: r.get::<_, i64>(20)? != 0,
        play_count: r.get(21)?,
    })
}

pub fn path_offset_key(path: &str) -> String {
    format!("path:{}", path.replace('\\', "/").to_lowercase())
}

pub fn file_offset_key(path: &str) -> Option<String> {
    Path::new(path)
        .file_name()
        .and_then(|n| n.to_str())
        .map(|n| format!("file:{}", n.to_lowercase()))
}

fn normalize_prefix(path: &str) -> String {
    let mut s = path.replace('\\', "/").to_lowercase();
    if !s.ends_with('/') {
        s.push('/');
    }
    s
}

pub fn unix_now() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn offset_lookup_prefers_path_and_falls_back_to_file() {
        let db = Db::in_memory().unwrap();
        db.set_raw_track_offset("file:song.mp3", -0.4).unwrap();
        assert_eq!(db.get_track_offset("C:\\Music\\song.mp3").unwrap(), Some(-0.4));

        db.set_track_offset("C:\\Music\\song.mp3", 0.25).unwrap();
        assert_eq!(db.get_track_offset("C:\\Music\\song.mp3").unwrap(), Some(0.25));
    }

    #[test]
    fn playlists_and_likes_roundtrip() {
        let db = Db::in_memory().unwrap();
        let pl = db.create_playlist("Night Drive").unwrap();
        db.add_tracks_to_playlist(pl.id, &["a.flac".into(), "b.mp3".into()]).unwrap();
        assert_eq!(db.get_playlist_tracks(pl.id).unwrap(), vec!["a.flac", "b.mp3"]);
        assert!(db.toggle_like("a.flac").unwrap());
        assert!(!db.toggle_like("a.flac").unwrap());
    }
}
