import { convertFileSrc, invoke } from '@tauri-apps/api/core';
import type {
  AudioDeviceInfo,
  AudioTick,
  EqConfig,
  FullTags,
  LyricsResult,
  OnlineLyricsCandidate,
  PlaylistSummary,
  TagEditPayload,
  Track,
} from './types';

let cachedArtDir = '';

export function setCachedArtDir(dir: string): void {
  cachedArtDir = dir;
}

export function artUrl(artHash: string | null | undefined, thumb = true): string | null {
  if (!artHash || !cachedArtDir) return null;
  const suffix = thumb ? `${artHash}_thumb.jpg` : `${artHash}.jpg`;
  const sep = cachedArtDir.endsWith('\\') || cachedArtDir.endsWith('/') ? '' : '/';
  return convertFileSrc(`${cachedArtDir}${sep}${suffix}`);
}

export const api = {
  getLibrary: () => invoke<Track[]>('get_library'),
  getFolders: () => invoke<string[]>('get_folders'),
  addFolder: (path: string) => invoke<Track[]>('add_folder', { path }),
  removeFolder: (path: string) => invoke<Track[]>('remove_folder', { path }),
  scanFolders: () => invoke<Track[]>('scan_folders'),
  getArtDir: () => invoke<string>('get_art_dir'),

  audioPlay: (path: string, startSecs?: number) => invoke<void>('audio_play', { path, startSecs }),
  audioPause: (paused: boolean) => invoke<void>('audio_pause', { paused }),
  audioSeek: (secs: number) => invoke<void>('audio_seek', { secs }),
  audioStop: () => invoke<void>('audio_stop'),
  audioSetVolume: (volume: number) => invoke<void>('audio_set_volume', { volume }),
  audioSetMute: (muted: boolean) => invoke<void>('audio_set_mute', { muted }),
  audioSetSpeed: (speed: number) => invoke<void>('audio_set_speed', { speed }),
  audioSetEq: (cfg: EqConfig) => invoke<void>('audio_set_eq', { cfg }),
  audioState: () => invoke<AudioTick>('audio_state'),
  audioDevices: () => invoke<AudioDeviceInfo[]>('audio_devices'),
  audioSetDevice: (name: string | null) => invoke<void>('audio_set_device', { name }),

  toggleLike: (path: string) => invoke<boolean>('toggle_like', { path }),
  recordPlay: (path: string) => invoke<number>('record_play', { path }),
  getRecentlyPlayed: (limit = 24) => invoke<string[]>('get_recently_played', { limit }),

  getPlaylists: () => invoke<PlaylistSummary[]>('get_playlists'),
  getPlaylistTracks: (playlistId: number) => invoke<string[]>('get_playlist_tracks', { playlistId }),
  createPlaylist: (name: string) => invoke<PlaylistSummary>('create_playlist', { name }),
  renamePlaylist: (id: number, name: string) => invoke<void>('rename_playlist', { id, name }),
  deletePlaylist: (id: number) => invoke<void>('delete_playlist', { id }),
  addTracksToPlaylist: (playlistId: number, paths: string[]) =>
    invoke<void>('add_tracks_to_playlist', { playlistId, paths }),
  setPlaylistTracks: (playlistId: number, paths: string[]) =>
    invoke<void>('set_playlist_tracks', { playlistId, paths }),
  exportM3u8: (playlistId: number, destPath: string) =>
    invoke<void>('export_m3u8', { playlistId, destPath }),
  importM3u8: (name: string, srcPath: string) =>
    invoke<PlaylistSummary>('import_m3u8', { name, srcPath }),

  readTags: (path: string) => invoke<FullTags>('read_tags', { path }),
  writeTags: (payload: TagEditPayload) => invoke<Track>('write_tags', { payload }),

  getTrackOffset: (path: string) => invoke<number | null>('get_track_offset', { path }),
  setTrackOffset: (path: string, offsetSecs: number) =>
    invoke<void>('set_track_offset', { path, offsetSecs }),
  loadLocalLyrics: (trackPath: string) => invoke<LyricsResult | null>('load_local_lyrics', { trackPath }),
  fetchOnlineLyrics: (trackPath: string, title: string, artist: string, album: string, duration: number) =>
    invoke<LyricsResult | null>('fetch_online_lyrics', { trackPath, title, artist, album, duration }),
  fetchLyricsSource: (
    source: string,
    trackPath: string,
    title: string,
    artist: string,
    album: string,
    duration: number
  ) => invoke<LyricsResult | null>('fetch_lyrics_source', { source, trackPath, title, artist, album, duration }),
  searchOnlineLyrics: (title: string, artist: string, album: string, duration: number) =>
    invoke<OnlineLyricsCandidate[]>('search_online_lyrics', { title, artist, album, duration }),
  saveLyricsChoice: (trackPath: string, format: string, content: string) =>
    invoke<LyricsResult>('save_lyrics_choice', { trackPath, format, content }),
  assignLyricsFile: (trackPath: string, lyricsFile: string) =>
    invoke<LyricsResult>('assign_lyrics_file', { trackPath, lyricsFile }),
  clearCachedLyrics: (trackPath: string) => invoke<void>('clear_cached_lyrics', { trackPath }),

  getAllKv: () => invoke<Record<string, string>>('get_all_kv'),
  setKv: (key: string, value: string) => invoke<void>('set_kv', { key, value }),

  discordUpdate: (params: {
    enabled: boolean;
    clientId: string;
    title: string;
    artist: string;
    album: string;
    positionSecs: number;
    durationSecs: number;
    playing: boolean;
  }) => invoke<void>('discord_update', params),
  discordClear: () => invoke<void>('discord_clear'),

  smtcUpdate: (params: {
    title: string;
    artist: string;
    album: string;
    durationSecs: number;
    positionSecs: number;
    playing: boolean;
    artHash: string | null;
  }) => invoke<void>('smtc_update', params),
  smtcClear: () => invoke<void>('smtc_clear'),

  revealInExplorer: (path: string) => invoke<void>('reveal_in_explorer', { path }),
  setWindowTheme: (theme: string) => invoke<void>('set_window_theme', { theme }),
  getLaunchFiles: () => invoke<string[]>('get_launch_files'),

  toolsStatus: () => invoke<{ ytdlp: string | null; ffmpeg: string | null }>('tools_status'),
  toolInstall: (tool: 'ytdlp' | 'ffmpeg') => invoke<void>('tool_install', { tool }),
  ytdlpUpdate: () => invoke<string>('ytdlp_update'),
  downloadDefaultFolder: () => invoke<string>('download_default_folder'),
  downloadAdd: (params: {
    urls: string[];
    preset: 'audioOriginal' | 'audioMp3' | 'video1080' | 'videoBest';
    dest: string;
    addToLibrary: boolean;
  }) => invoke<number>('download_add', params),
  downloadCancel: (id: number) => invoke<void>('download_cancel', { id }),

  subsonicStatus: () => invoke<{ server: string; user: string } | null>('subsonic_status'),
  subsonicConnect: (server: string, user: string, password: string) =>
    invoke<{ server: string; user: string }>('subsonic_connect', { server, user, password }),
  subsonicLogout: () => invoke<void>('subsonic_logout'),

  googleTranslate: (text: string, from: string, to: string, dictionary: boolean) =>
    invoke<string>('google_translate', { text, from, to, dictionary }),
  jishoLookup: (word: string) => invoke<string>('jisho_lookup', { word }),

  quranFetch: (source: Record<string, unknown>) => invoke<unknown>('quran_fetch', { source }),
  quranPauses: (path: string) =>
    invoke<{ duration: number; pauses: Array<[number, number]> }>('quran_pauses', { path }),
};
