import { api } from './api';
import type { DownloadPreset } from './downloads.svelte';
import {
  DEFAULT_LYRICS_SOURCES,
  normalizeLyricsSources,
  type SourceSetting,
} from './lyricsources';
import type { TranslationId } from './quran.svelte';

export const DEFAULT_DISCORD_CLIENT_ID = '1570070424627191808';

class SettingsStore {
  theme = $state('obsidian');
  accent = $state('zest');
  matchArtwork = $state(false);
  defaultOffset = $state(0);
  autoFetchLyrics = $state(true);
  preferWordTiming = $state(true);
  lyricsSources = $state<SourceSetting[]>([...DEFAULT_LYRICS_SOURCES]);
  translateLyrics = $state(false);
  lyricsLanguage = $state('');
  learnLyrics = $state(false);
  quranTranslations = $state<TranslationId[]>(['kemenag', 'saheeh']);
  quranTranslit = $state(true);
  romanizeLyrics = $state(true);
  lyricsFontSize = $state(24);
  downloadFolder = $state('');
  downloadPreset = $state<DownloadPreset>('audioOriginal');
  downloadToLibrary = $state(true);
  discordEnabled = $state(false);
  discordClientId = $state(DEFAULT_DISCORD_CLIENT_ID);
  discordClientIdRevealed = $state(false);
  outputDevice = $state('');
  volume = $state(0.85);
  speed = $state(1.0);
  loaded = $state(false);

  async load(): Promise<void> {
    try {
      const kv = await api.getAllKv();
      if (kv.theme) this.theme = kv.theme === 'dark' ? 'obsidian' : kv.theme;
      if (kv.accent) this.accent = kv.accent;
      if (kv.match_artwork) this.matchArtwork = kv.match_artwork === 'true';
      if (kv.lyrics_default_offset) {
        const n = Number(kv.lyrics_default_offset);
        if (Number.isFinite(n)) this.defaultOffset = n;
      }
      if (kv.auto_fetch_lyrics) this.autoFetchLyrics = kv.auto_fetch_lyrics !== 'false';
      if (kv.prefer_word_timing) this.preferWordTiming = kv.prefer_word_timing !== 'false';
      if (kv.lyrics_sources) {
        try {
          this.lyricsSources = normalizeLyricsSources(
            JSON.parse(kv.lyrics_sources),
            this.autoFetchLyrics
          );
        } catch {
          this.lyricsSources = normalizeLyricsSources(null, this.autoFetchLyrics);
        }
      } else {
        this.lyricsSources = normalizeLyricsSources(null, this.autoFetchLyrics);
      }
      if (kv.translate_lyrics) this.translateLyrics = kv.translate_lyrics === 'true';
      if (kv.lyrics_language !== undefined) this.lyricsLanguage = kv.lyrics_language;
      if (kv.learn_lyrics) this.learnLyrics = kv.learn_lyrics === 'true';
      if (kv.quran_translations) {
        try {
          const parsed = JSON.parse(kv.quran_translations) as TranslationId[];
          if (Array.isArray(parsed) && parsed.length > 0) this.quranTranslations = parsed;
        } catch {
          // keep default
        }
      }
      if (kv.quran_translit) this.quranTranslit = kv.quran_translit !== 'false';
      if (kv.romanize_lyrics) this.romanizeLyrics = kv.romanize_lyrics !== 'false';
      if (kv.lyrics_font_size) {
        const fs = Number(kv.lyrics_font_size);
        if (Number.isFinite(fs) && fs >= 16 && fs <= 36) this.lyricsFontSize = fs;
      }
      if (kv.download_folder !== undefined) this.downloadFolder = kv.download_folder;
      if (
        kv.download_preset === 'audioOriginal' ||
        kv.download_preset === 'audioMp3' ||
        kv.download_preset === 'video1080' ||
        kv.download_preset === 'videoBest'
      ) {
        this.downloadPreset = kv.download_preset;
      }
      if (kv.download_to_library) this.downloadToLibrary = kv.download_to_library !== 'false';
      if (kv.discord_enabled) this.discordEnabled = kv.discord_enabled === 'true';
      if (kv.discord_client_id && kv.discord_client_id.trim()) {
        this.discordClientId = kv.discord_client_id.trim();
      }
      if (kv.output_device !== undefined) this.outputDevice = kv.output_device;
      if (kv.volume) {
        const v = Number(kv.volume);
        if (Number.isFinite(v)) this.volume = Math.max(0, Math.min(1, v));
      }
      if (kv.speed) {
        const sp = Number(kv.speed);
        if (Number.isFinite(sp)) this.speed = Math.max(0.5, Math.min(2, sp));
      }
    } finally {
      this.loaded = true;
    }
  }

  save(key: string, value: string): void {
    void api.setKv(key, value);
  }
}

export const settings = new SettingsStore();
