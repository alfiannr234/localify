import { api } from './api';
import { getActiveLyricState, parseLyrics } from './lyrics';
import { resolveOrderedLyrics } from './lyricsources';
import { needsRomanization, romanizeText } from './romanize';
import { settings } from './settings.svelte';
import { learnStore } from './learn.svelte';
import { triggerTranslateSync } from './translate.svelte';
import type { LyricLine, OnlineLyricsCandidate, Track } from './types';

class LyricsStore {
  trackPath = $state<string | null>(null);
  source = $state<string>('');
  format = $state<string>('lrc');
  lines = $state<LyricLine[]>([]);
  romanizedLines = $state<string[]>([]);
  synced = $state(false);
  wordSynced = $state(false);
  hasRomanizable = $state(false);
  loading = $state(false);
  searching = $state(false);
  searchResults = $state<OnlineLyricsCandidate[]>([]);
  trackOffset = $state(0);

  private reqCounter = 0;

  async loadForTrack(track: Track): Promise<void> {
    const reqId = ++this.reqCounter;
    this.trackPath = track.path;
    this.loading = true;
    this.searchResults = [];
    this.lines = [];
    this.romanizedLines = [];
    this.source = '';
    this.synced = false;
    this.wordSynced = false;

    try {
      const [savedOffset, local] = await Promise.all([
        api.getTrackOffset(track.path),
        api.loadLocalLyrics(track.path),
      ]);
      if (reqId !== this.reqCounter) return;
      this.trackOffset = savedOffset ?? settings.defaultOffset;

      if (local && local.source === 'cache') {
        this.applyResult(local.source, local.format, local.content);
        this.loading = false;
        return;
      }

      const resolved = await resolveOrderedLyrics(
        settings.lyricsSources,
        async (sourceId) => {
          const res =
            sourceId === 'local'
              ? local
              : await api.fetchLyricsSource(
                  sourceId,
                  track.path,
                  track.title,
                  track.artist,
                  track.album,
                  track.duration
                );
          if (!res) return null;
          return {
            res,
            parsed: parseLyrics(res.content, res.format),
          };
        },
        settings.preferWordTiming
      );
      if (reqId !== this.reqCounter) return;
      if (resolved) {
        const { res } = resolved.result;
        this.applyResult(resolved.source, res.format, res.content);
        if (resolved.source !== 'local') {
          void api.saveLyricsChoice(track.path, res.format, res.content);
        }
      }
    } finally {
      if (reqId === this.reqCounter) {
        this.loading = false;
      }
    }
  }

  async loadForPath(path: string): Promise<void> {
    const reqId = ++this.reqCounter;
    this.trackPath = path;
    this.loading = true;
    this.lines = [];
    this.romanizedLines = [];
    this.source = '';
    try {
      const [savedOffset, local] = await Promise.all([
        api.getTrackOffset(path),
        api.loadLocalLyrics(path),
      ]);
      if (reqId !== this.reqCounter) return;
      this.trackOffset = savedOffset ?? settings.defaultOffset;
      if (local) {
        this.applyResult(local.source, local.format, local.content);
      }
    } finally {
      if (reqId === this.reqCounter) {
        this.loading = false;
      }
    }
  }

  private applyResult(source: string, format: string, content: string): void {
    const parsed = parseLyrics(content, format);
    this.source = source;
    this.format = format;
    this.lines = parsed.lines;
    this.synced = parsed.synced;
    this.wordSynced = parsed.wordSynced;

    let anyRom = false;
    const roms: string[] = [];
    for (const line of parsed.lines) {
      if (needsRomanization(line.text)) {
        anyRom = true;
        roms.push(romanizeText(line.text));
      } else {
        roms.push('');
      }
    }
    this.hasRomanizable = anyRom;
    this.romanizedLines = roms;
    triggerTranslateSync();
    void learnStore.analyze(this.lines, settings.learnLyrics);
  }

  activeAt(songSecs: number) {
    return getActiveLyricState(this.lines, songSecs, this.trackOffset);
  }

  /** Updates offset silently without showing any toast popup. */
  async setOffset(offsetSecs: number): Promise<void> {
    const clamped = Math.max(-30, Math.min(30, Math.round(offsetSecs * 100) / 100));
    this.trackOffset = clamped;
    if (this.trackPath) {
      await api.setTrackOffset(this.trackPath, clamped);
    } else {
      settings.defaultOffset = clamped;
      settings.save('lyrics_default_offset', clamped.toFixed(2));
    }
  }

  async adjustOffset(deltaSecs: number): Promise<void> {
    await this.setOffset(this.trackOffset + deltaSecs);
  }

  async searchManual(track: Track): Promise<void> {
    this.searching = true;
    try {
      this.searchResults = await api.searchOnlineLyrics(
        track.title,
        track.artist,
        track.album,
        track.duration
      );
    } finally {
      this.searching = false;
    }
  }

  async applyCandidate(trackPath: string, cand: OnlineLyricsCandidate): Promise<void> {
    const saved = await api.saveLyricsChoice(trackPath, cand.format, cand.content);
    if (this.trackPath === trackPath) {
      this.applyResult(cand.provider, saved.format, saved.content);
      this.searchResults = [];
    }
  }

  async assignCustomFile(trackPath: string, filePath: string): Promise<void> {
    const res = await api.assignLyricsFile(trackPath, filePath);
    if (this.trackPath === trackPath) {
      this.applyResult(res.source, res.format, res.content);
    }
  }

  async refreshOnline(track: Track): Promise<void> {
    await api.clearCachedLyrics(track.path);
    await this.loadForTrack(track);
  }
}

export const lyricsStore = new LyricsStore();
