import { api } from './api';
import {
  alignAyahs,
  detectRecitation,
  hasBasmala,
  joinTanwin,
  normalizeArabic,
  type Pause,
  type Recitation,
  type Unit,
} from './quran';
import { settings } from './settings.svelte';
import type { Track } from './types';

export type TranslationId = 'kemenag' | 'saheeh' | 'clear';
export const QURAN_TRANSLATIONS: Array<{
  id: TranslationId;
  name: string;
  short: string;
  lang: string;
}> = [
  { id: 'kemenag', name: 'Kemenag (Indonesian)', short: 'Kemenag', lang: 'id' },
  { id: 'saheeh', name: 'Saheeh International', short: 'Saheeh', lang: 'en' },
  { id: 'clear', name: 'The Clear Quran (Dr. Mustafa Khattab)', short: 'Clear Quran', lang: 'en' },
];

export interface QWord {
  ar: string;
  tr: string;
  mean: string;
  seg?: [number, number];
}

export interface QAyah {
  surah: number;
  n: number;
  ar: string;
  words: QWord[] | null;
  tr: Partial<Record<TranslationId, string>>;
}

export type QUnit =
  | { kind: 'istiadha' }
  | { kind: 'basmala'; surah: number }
  | { kind: 'ayah'; ayah: QAyah };

export const ISTIADHA = 'أَعُوذُ بِٱللَّهِ مِنَ ٱلشَّيْطَٰنِ ٱلرَّجِيمِ';
export const BASMALA = 'بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ';

interface SurahData {
  ayahs: Array<{
    n: number;
    words: Array<{ ar: string; tr: string; mean: string }>;
    kemenag: string;
    saheeh: string;
  }>;
  timings: Array<{ from: number; to: number; segs: number[][] }> | null;
  shubah: string[] | null;
  clear: string[] | null;
}

const cleanHtml = (s: string) =>
  s
    .replace(/<sup[^>]*>.*?<\/sup>/g, '')
    .replace(/<[^>]+>/g, '')
    .replace(/\s+/g, ' ')
    .trim();

const arabicLetters = (s: string) => normalizeArabic(s).replace(/[^\u0621-\u064a]/g, '');

class QuranStore {
  rec = $state<Recitation | null>(null);
  path = $state('');
  units = $state<QUnit[]>([]);
  starts = $state<number[] | null>(null);
  exact = $state(false);
  status = $state<'' | 'loading' | 'analysing' | 'ready' | 'error'>('');
  error = $state('');
  fixing = $state(false);
  anchors = $state<Record<number, number>>({});

  private seq = 0;
  private data = new Map<number, SurahData>();
  private lastPauses: { duration: number; pauses: Pause[] } | null = null;
  private memCache = new Map<string, unknown>();

  private async cached<T>(key: string, fetcher: () => Promise<T>): Promise<T> {
    if (this.memCache.has(key)) return this.memCache.get(key) as T;
    const raw = await api.getKv(key).catch(() => null);
    if (raw) {
      try {
        const val = JSON.parse(raw) as T;
        this.rememberCache(key, val);
        return val;
      } catch {
        // ignore corrupt cache
      }
    }
    const fresh = await fetcher();
    this.rememberCache(key, fresh);
    void api.setKv(key, JSON.stringify(fresh));
    return fresh;
  }

  private rememberCache(key: string, val: unknown): void {
    if (this.memCache.size >= 4) {
      const oldest = this.memCache.keys().next().value;
      if (oldest !== undefined) this.memCache.delete(oldest);
    }
    this.memCache.set(key, val);
  }

  private buildAyah(
    surah: number,
    a: SurahData['ayahs'][number],
    d: SurahData,
    idx: number,
    exact: boolean
  ): QAyah {
    const seg = exact ? d.timings?.[idx]?.segs : undefined;
    let words: QWord[] | null = a.words.map((w, k) => {
      const s = seg?.find((x) => x[0] === k + 1);
      return { ...w, ...(s && { seg: [s[1] / 1000, s[2] / 1000] as [number, number] }) };
    });
    let ar = a.words.map((w) => w.ar).join(' ');
    const shu = d.shubah?.[idx] && joinTanwin(d.shubah[idx]);
    if (shu) {
      ar = shu;
      const tokens = shu.split(/\s+/).filter((x) => arabicLetters(x));
      words = tokens.length === words.length ? words.map((w, k) => ({ ...w, ar: tokens[k] })) : null;
    }
    return {
      surah,
      n: a.n,
      ar,
      words,
      tr: { kemenag: a.kemenag, saheeh: a.saheeh, clear: d.clear?.[idx] ?? '' },
    };
  }

  private async loadSurah(
    n: number,
    lang: 'id' | 'en',
    shubah: boolean,
    clear: boolean
  ): Promise<SurahData> {
    type VersesResp = {
      verses: Array<{
        verse_number: number;
        words: Array<{
          char_type_name: string;
          text_uthmani: string;
          transliteration?: { text: string | null };
          translation?: { text: string | null };
        }>;
        translations?: Array<{ resource_id: number; text: string }>;
      }>;
    };
    type TimingsResp = {
      audio_file: {
        timestamps: Array<{
          timestamp_from: number;
          timestamp_to: number;
          segments?: number[][];
        }>;
      };
    };
    type EditionResp = { chapter: Array<{ verse: number; text: string }> };

    const [verses, timings, shu, cq] = await Promise.all([
      this.cached(`quran:v1:verses:${lang}:${n}`, async () => {
        const v = (await api.quranFetch({ kind: 'verses', chapter: n, lang })) as VersesResp;
        return v.verses.map((x) => ({
          n: x.verse_number,
          words: x.words
            .filter((w) => w.char_type_name === 'word')
            .map((w) => ({
              ar: w.text_uthmani,
              tr: w.transliteration?.text ?? '',
              mean: w.translation?.text ?? '',
            })),
          kemenag: cleanHtml(x.translations?.find((t) => t.resource_id === 33)?.text ?? ''),
          saheeh: cleanHtml(x.translations?.find((t) => t.resource_id === 20)?.text ?? ''),
        }));
      }),
      this.cached(`quran:v1:timings:${n}`, async () => {
        const t = (await api.quranFetch({ kind: 'timings', chapter: n })) as TimingsResp;
        return t.audio_file.timestamps.map((x) => ({
          from: x.timestamp_from,
          to: x.timestamp_to,
          segs: x.segments ?? [],
        }));
      }).catch(() => null),
      shubah
        ? this.cached(`quran:v1:shubah:${n}`, async () => {
            const e = (await api.quranFetch({ kind: 'shubah', chapter: n })) as EditionResp;
            return e.chapter.map((x) => x.text);
          })
        : Promise.resolve(null),
      clear
        ? this.cached(`quran:v1:clear:${n}`, async () => {
            const e = (await api.quranFetch({ kind: 'clearQuran', chapter: n })) as EditionResp;
            return e.chapter.map((x) => x.text);
          }).catch(() => null)
        : Promise.resolve(null),
    ]);

    return { ayahs: verses, timings, shubah: shu, clear: cq };
  }

  async loadTrack(t: Track | null): Promise<void> {
    const mine = ++this.seq;
    this.rec = null;
    this.path = '';
    this.units = [];
    this.starts = null;
    this.exact = false;
    this.status = '';
    this.error = '';
    this.fixing = false;
    this.anchors = {};
    this.lastPauses = null;
    this.data.clear();

    if (!t) {
      this.memCache.clear();
      return;
    }
    const rec = detectRecitation(t);
    if (!rec) return;

    this.rec = rec;
    this.path = t.path;
    this.status = 'loading';

    try {
      const lang: 'id' | 'en' = settings.quranTranslations.includes('kemenag') ? 'id' : 'en';
      const wantClear = settings.quranTranslations.includes('clear');
      const loaded = new Map<number, SurahData>();
      for (const n of rec.surahs) {
        loaded.set(n, await this.loadSurah(n, lang, rec.riwayah === 'shubah', wantClear));
      }
      if (mine !== this.seq) return;
      this.data = loaded;

      const units: QUnit[] = [{ kind: 'istiadha' }];
      for (const n of rec.surahs) {
        const d = loaded.get(n)!;
        if (hasBasmala(n)) units.push({ kind: 'basmala', surah: n });
        d.ayahs.forEach((a, i) =>
          units.push({ kind: 'ayah', ayah: this.buildAyah(n, a, d, i, false) })
        );
      }
      this.units = units;

      const rawAnchors = await api.getKv('quran_anchors').catch(() => null);
      if (rawAnchors) {
        try {
          const anchorsMap = JSON.parse(rawAnchors) as Record<string, Record<number, number>>;
          this.anchors = anchorsMap[t.path.toLowerCase()] ?? {};
        } catch {
          this.anchors = {};
        }
      }

      await this.syncTrack(t, mine);
    } catch (e) {
      if (mine === this.seq) {
        this.status = 'error';
        this.error = e instanceof Error ? e.message : String(e);
      }
    }
  }

  private async syncTrack(t: Track, mine: number): Promise<void> {
    if (!this.rec) return;
    if (this.rec.partial) {
      this.starts = null;
      this.status = 'ready';
      return;
    }

    const one = this.rec.surahs.length === 1 ? this.data.get(this.rec.surahs[0]) : undefined;
    const murattalEnd = one?.timings?.at(-1)?.to;
    if (
      murattalEnd &&
      t.duration &&
      Math.abs(t.duration - murattalEnd / 1000) / (murattalEnd / 1000) < 0.01
    ) {
      this.exact = true;
      this.units = this.units.map((u) =>
        u.kind === 'ayah'
          ? {
              kind: 'ayah',
              ayah: this.buildAyah(u.ayah.surah, one!.ayahs[u.ayah.n - 1], one!, u.ayah.n - 1, true),
            }
          : u
      );
      this.starts = this.units.map((u) =>
        u.kind === 'ayah' ? one!.timings![u.ayah.n - 1].from / 1000 : 0
      );
      this.status = 'ready';
      return;
    }

    this.status = 'analysing';
    const pauses = await api.quranPauses(t.path);
    if (mine !== this.seq) return;
    this.realign(pauses);
  }

  private unitWeight(u: QUnit): number {
    if (u.kind !== 'ayah') return 5;
    const t = this.data.get(u.ayah.surah)?.timings?.[u.ayah.n - 1];
    return t ? (t.to - t.from) / 1000 : Math.max(1, arabicLetters(u.ayah.ar).length * 0.08);
  }

  private realign(p = this.lastPauses): void {
    if (!p) return;
    this.lastPauses = p;
    const units: Unit[] = this.units.map((u) => ({
      weight: this.unitWeight(u),
      flexible: u.kind !== 'ayah',
    }));
    this.starts = alignAyahs(units, p.pauses, p.duration, this.anchors);
    this.status = 'ready';
  }

  async markStart(unitIdx: number, secs: number): Promise<void> {
    this.anchors = { ...this.anchors, [unitIdx]: Math.round(secs * 100) / 100 };
    this.fixing = false;
    await this.saveAnchors();
    if (this.rec?.partial) this.starts = null;
    else this.realign();
  }

  async clearMarks(): Promise<void> {
    this.anchors = {};
    await this.saveAnchors();
    this.realign();
  }

  private async saveAnchors(): Promise<void> {
    const rawAnchors = await api.getKv('quran_anchors').catch(() => null);
    let map: Record<string, Record<number, number>> = {};
    if (rawAnchors) {
      try {
        map = JSON.parse(rawAnchors);
      } catch {
        map = {};
      }
    }
    if (Object.keys(this.anchors).length === 0) delete map[this.path.toLowerCase()];
    else map[this.path.toLowerCase()] = this.anchors;
    void api.setKv('quran_anchors', JSON.stringify(map));
  }

  currentUnitIndex(timeSecs: number): number {
    const s = this.starts;
    if (s) {
      let lo = 0;
      let hi = s.length - 1;
      let ans = 0;
      while (lo <= hi) {
        const mid = (lo + hi) >> 1;
        if (s[mid] <= timeSecs) {
          ans = mid;
          lo = mid + 1;
        } else {
          hi = mid - 1;
        }
      }
      return ans;
    }
    let best = -1;
    let bestAt = -Infinity;
    for (const [u, t] of Object.entries(this.anchors)) {
      if (t <= timeSecs && t > bestAt) {
        best = Number(u);
        bestAt = t;
      }
    }
    return best;
  }
}

export const quranStore = new QuranStore();
