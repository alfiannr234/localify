export interface Track {
  path: string;
  title: string;
  artist: string;
  album: string;
  albumArtist: string;
  genre: string;
  year: number | null;
  trackNo: number | null;
  discNo: number | null;
  duration: number;
  bitrate: number | null;
  sampleRate: number | null;
  bitDepth: number | null;
  channels: number | null;
  codec: string | null;
  fileSize: number;
  artHash: string | null;
  artColor: string | null;
  modified: number;
  addedAt: number;
  liked: boolean;
  playCount: number;
}

export interface PlaylistSummary {
  id: number;
  name: string;
  createdAt: number;
  trackCount: number;
}

export interface AudioTick {
  path: string | null;
  position: number;
  duration: number;
  playing: boolean;
  volume: number;
  muted: boolean;
  speed: number;
}

export interface AudioDeviceInfo {
  name: string;
  isDefault: boolean;
}

export interface EqConfig {
  enabled: boolean;
  preampDb: number;
  gainsDb: number[];
}

export interface LyricsResult {
  source: string;
  format: 'ttml' | 'lrc' | 'krc' | 'txt' | string;
  content: string;
  path: string | null;
}

export interface OnlineLyricsCandidate {
  id: string;
  provider: string;
  title: string;
  artist: string;
  album: string;
  duration: number;
  synced: boolean;
  wordSynced: boolean;
  format: string;
  content: string;
  preview: string;
}

export interface FullTags {
  path: string;
  title: string;
  artist: string;
  album: string;
  albumArtist: string;
  genre: string;
  year: number | null;
  trackNo: number | null;
  discNo: number | null;
  comment: string;
  lyrics: string;
}

export interface TagEditPayload extends FullTags {
  coverPath: string | null;
}

export interface LyricWord {
  start: number;
  end: number;
  text: string;
}

export interface LyricLine {
  time: number;
  end: number;
  text: string;
  words: LyricWord[];
  bg?: boolean;
}

export type RepeatMode = 'off' | 'all' | 'one';

export type ViewRoute =
  | { kind: 'home' }
  | { kind: 'songs' }
  | { kind: 'albums' }
  | { kind: 'album'; name: string; artist: string }
  | { kind: 'artists'; selected?: string }
  | { kind: 'genres'; selected?: string }
  | { kind: 'liked' }
  | { kind: 'playlist'; id: number }
  | { kind: 'search' }
  | { kind: 'eq' }
  | { kind: 'downloads' }
  | { kind: 'vocabulary' }
  | { kind: 'settings' };
