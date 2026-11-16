import type { ParsedLyrics } from './lyrics';

export type LyricsSourceId = 'local' | 'subsonic' | 'lrclib' | 'netease' | 'kugou';

export interface SourceSetting {
  id: LyricsSourceId;
  on: boolean;
}

export const LYRICS_SOURCES: Record<LyricsSourceId, { label: string; hint: string }> = {
  local: {
    label: 'Local files',
    hint: '.ttml, .krc, or .lrc next to the track, or lyrics embedded in tags',
  },
  subsonic: {
    label: 'Navidrome / Subsonic',
    hint: 'Synced or plain lyrics from your Subsonic-compatible server',
  },
  lrclib: {
    label: 'LRCLIB',
    hint: 'Open community database of time-synced lyrics',
  },
  netease: {
    label: 'NetEase Cloud Music',
    hint: 'Line-synced lyrics with strong Japanese and Chinese coverage',
  },
  kugou: {
    label: 'Kugou',
    hint: 'Word-by-word karaoke timing with strong Asian music coverage',
  },
};

export const DEFAULT_LYRICS_SOURCES: readonly SourceSetting[] = [
  { id: 'local', on: true },
  { id: 'subsonic', on: true },
  { id: 'lrclib', on: true },
  { id: 'netease', on: true },
  { id: 'kugou', on: true },
];

export function normalizeLyricsSources(stored: unknown, autoFetch?: boolean): SourceSetting[] {
  const known = new Set<LyricsSourceId>(['local', 'subsonic', 'lrclib', 'netease', 'kugou']);
  const out: SourceSetting[] = [];
  if (Array.isArray(stored)) {
    for (const item of stored) {
      if (item && known.has(item.id) && !out.some((o) => o.id === item.id)) {
        out.push({ id: item.id, on: Boolean(item.on) });
      }
    }
  }
  for (const d of DEFAULT_LYRICS_SOURCES) {
    if (!out.some((o) => o.id === d.id)) {
      const enabled = autoFetch === false && d.id !== 'local' ? false : d.on;
      out.push({ id: d.id, on: enabled });
    }
  }
  return out;
}

export async function resolveOrderedLyrics<T extends { parsed: ParsedLyrics }>(
  sources: SourceSetting[],
  fetcher: (id: LyricsSourceId) => Promise<T | null>,
  preferWordTiming: boolean
): Promise<{ result: T; source: LyricsSourceId } | null> {
  let fallback: { result: T; source: LyricsSourceId } | null = null;
  for (const s of sources) {
    if (!s.on) continue;
    try {
      const res = await fetcher(s.id);
      if (!res || res.parsed.lines.length === 0) continue;
      if (!preferWordTiming || res.parsed.wordSynced) {
        return { result: res, source: s.id };
      }
      if (!fallback) {
        fallback = { result: res, source: s.id };
      }
    } catch {
      // continue trying next configured provider
    }
  }
  return fallback;
}
