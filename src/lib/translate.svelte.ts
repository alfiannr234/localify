import { api } from './api';
import { lyricsStore } from './lyrics.svelte';
import { settings } from './settings.svelte';
import {
  alignTranslatedLines,
  batchTexts,
  fnvHashHex,
  mapLinesBack,
  parseGtx,
  sameLanguage,
  targetForLocale,
  uniqueLines,
} from './translate';
import type { LyricLine } from './types';

class TranslateStore {
  lines = $state<Array<string | null> | null>(null);
  source = $state('');
  same = $state(false);
  busy = $state(false);
  error = $state('');

  private reqId = 0;

  targetLanguage(): string {
    return settings.lyricsLanguage || targetForLocale(navigator.language || 'en');
  }

  async sync(rawLines: LyricLine[], active: boolean): Promise<void> {
    const id = ++this.reqId;
    this.lines = null;
    this.source = '';
    this.same = false;
    this.error = '';

    if (!active || rawLines.length === 0) return;
    const textLines = rawLines.map((l) => l.text);
    const uniq = uniqueLines(textLines);
    if (uniq.length === 0) return;

    const tl = this.targetLanguage();
    this.busy = true;
    try {
      const kvKey = `tr2:${tl}:${fnvHashHex(uniq.join('\n'))}`;
      const rawCached = await api.getKv(kvKey).catch(() => null);
      let cached: { source: string; dict: Record<string, string> } | null = null;
      if (rawCached) {
        try {
          cached = JSON.parse(rawCached);
        } catch {
          cached = null;
        }
      }

      if (!cached) {
        const dict: Record<string, string> = {};
        let detected = '';
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        const translateChunk = async (items: string[]): Promise<void> => {
          if (id !== this.reqId || items.length === 0) return;
          const raw = await api.googleTranslate(items.join('\n'), 'auto', tl, false);
          const parsed = parseGtx(JSON.parse(raw));
          if (!detected) detected = parsed.source;
          const aligned = alignTranslatedLines(items, parsed.text);
          if (aligned) {
            items.forEach((orig, idx) => {
              const val = aligned[idx] ?? '';
              if (val) dict[orig] = val;
            });
            return;
          }
          if (items.length === 1) {
            const val = parsed.text.trim();
            if (val) dict[items[0]] = val;
            return;
          }
          const mid = Math.ceil(items.length / 2);
          await translateChunk(items.slice(0, mid));
          await sleep(180);
          await translateChunk(items.slice(mid));
        };

        const chunks = batchTexts(uniq);
        for (let i = 0; i < chunks.length; i++) {
          if (id !== this.reqId) return;
          if (i > 0) await sleep(180);
          await translateChunk(chunks[i]);
        }
        cached = { source: detected, dict };
        if (Object.keys(dict).length > 0) {
          void api.setKv(kvKey, JSON.stringify(cached));
        }
      }

      if (id !== this.reqId) return;
      this.source = cached.source;
      const mapped = mapLinesBack(textLines, cached.dict);
      const hasDiff = mapped.some((v) => v !== null);
      this.same = sameLanguage(cached.source, tl) && !hasDiff;
      this.lines = this.same ? null : mapped;
    } catch (e) {
      if (id === this.reqId) {
        this.error = e instanceof Error ? e.message : String(e);
      }
    } finally {
      if (id === this.reqId) {
        this.busy = false;
      }
    }
  }
}

export const translateStore = new TranslateStore();

export function triggerTranslateSync(): void {
  const active = settings.translateLyrics || settings.learnLyrics;
  void translateStore.sync(lyricsStore.lines, active);
}
