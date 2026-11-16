import { api } from './api';
import {
  hasJapanese,
  hasKanji,
  JP_PARTICLES,
  kanaOnlyReading,
  kanaToRomaji,
  toHiragana,
} from './kana';
import { parseLookup, type WordLookup } from './lookup';
import { lyricsStore } from './lyrics.svelte';
import { player } from './player.svelte';
import { needsRomanization, romanizeText } from './romanize';
import { library } from './library.svelte';
import { translateStore } from './translate.svelte';
import { reviewVocab, vocabId, type VocabItem } from './vocab';
import type { LyricLine } from './types';

export interface LearnPiece {
  text: string;
  tap: boolean;
  reading: string;
  romaji: string;
  note: string;
}

class LearnStore {
  japanese = $state(false);
  pieces = $state<LearnPiece[][] | null>(null);
  romajiLines = $state<string[] | null>(null);
  error = $state('');

  card = $state({
    open: false,
    line: -1,
    piece: -1,
    text: '',
    reading: '',
    baseWord: '',
    romaji: '',
    pos: '',
    note: '',
    lookup: null as WordLookup | null,
    busy: false,
    error: '',
  });

  vocab = $state<VocabItem[]>([]);
  vocabLoaded = $state(false);

  private seq = 0;
  private cardSeq = 0;
  private jpCache = new Map<string, { reading: string; meanings: string[]; pos: string; base: string }>();

  async loadVocab(): Promise<void> {
    try {
      const raw = await api.getKv('vocab_items');
      if (raw) {
        const parsed = JSON.parse(raw) as VocabItem[];
        if (Array.isArray(parsed)) this.vocab = parsed;
      }
    } catch {
      // ignore invalid stored vocab
    } finally {
      this.vocabLoaded = true;
    }
  }

  private persistVocab(): void {
    if (!this.vocabLoaded) return;
    void api.setKv('vocab_items', JSON.stringify(this.vocab));
  }

  isSaved(id: string): boolean {
    return this.vocab.some((v) => v.id === id);
  }

  saveWordDirect(item: Omit<VocabItem, 'added' | 'box' | 'due'>): void {
    if (!this.vocabLoaded || this.isSaved(item.id)) return;
    const now = Date.now();
    this.vocab = [{ ...item, added: now, box: 0, due: now }, ...this.vocab];
    this.persistVocab();
  }

  removeWord(id: string): void {
    this.vocab = this.vocab.filter((v) => v.id !== id);
    this.persistVocab();
  }

  gradeWord(id: string, knew: boolean): void {
    this.vocab = this.vocab.map((v) => (v.id === id ? reviewVocab(v, knew) : v));
    this.persistVocab();
  }

  closeCard(): void {
    this.cardSeq++;
    this.card.open = false;
  }

  async analyze(rawLines: LyricLine[], active: boolean): Promise<void> {
    const mine = ++this.seq;
    this.japanese = false;
    this.pieces = null;
    this.romajiLines = null;
    this.error = '';
    this.closeCard();

    if (!active || rawLines.length === 0) return;

    const lines = rawLines.map((l) => l.text);
    const isJp = hasJapanese(lines);
    this.japanese = isJp;

    const segmenter = new Intl.Segmenter(isJp ? 'ja' : undefined, { granularity: 'word' });
    const built: LearnPiece[][] = lines.map((line) =>
      [...segmenter.segment(line)].map((seg) => {
        const text = seg.segment;
        const tap = Boolean(seg.isWordLike);
        let reading = '';
        let romaji = '';
        let note = '';
        if (isJp && tap) {
          reading = kanaOnlyReading(text);
          if (JP_PARTICLES[text]) note = JP_PARTICLES[text];
          if (reading) {
            romaji =
              text === 'は' ? 'wa' : text === 'へ' ? 'e' : text === 'を' ? 'o' : kanaToRomaji(reading);
          }
        }
        return { text, tap, reading, romaji, note };
      })
    );

    if (mine !== this.seq) return;
    this.pieces = built;

    if (isJp) {
      this.updateRomajiLines(built);
      void this.hydrateJapaneseReadings(mine, built);
    } else if (lines.some(needsRomanization)) {
      this.romajiLines = lines.map((line) => (needsRomanization(line) ? romanizeText(line) : ''));
    }
  }

  private updateRomajiLines(matrix: LearnPiece[][]): void {
    this.romajiLines = matrix.map((row) =>
      row
        .map((p) => (p.romaji ? p.romaji : p.text))
        .join(' ')
        .replace(/\s+/g, ' ')
        .trim()
    );
  }

  private async hydrateJapaneseReadings(mine: number, matrix: LearnPiece[][]): Promise<void> {
    const unknownKanji = new Set<string>();
    for (const row of matrix) {
      for (const p of row) {
        if (p.tap && hasKanji(p.text) && !p.reading && !this.jpCache.has(p.text)) {
          unknownKanji.add(p.text);
        }
      }
    }

    for (const word of [...unknownKanji].slice(0, 35)) {
      if (mine !== this.seq) return;
      await this.fetchJishoEntry(word);
    }

    if (mine !== this.seq) return;
    let changed = false;
    const nextMatrix = matrix.map((row) =>
      row.map((p) => {
        const hit = this.jpCache.get(p.text);
        if (hit && hit.reading && !p.reading) {
          changed = true;
          return {
            ...p,
            reading: toHiragana(hit.reading),
            romaji: kanaToRomaji(hit.reading),
          };
        }
        return p;
      })
    );
    if (changed) {
      this.pieces = nextMatrix;
      this.updateRomajiLines(nextMatrix);
    }
  }

  private async fetchJishoEntry(
    word: string
  ): Promise<{ reading: string; meanings: string[]; pos: string; base: string } | null> {
    if (this.jpCache.has(word)) return this.jpCache.get(word)!;
    try {
      const raw = await api.jishoLookup(word);
      const parsed = JSON.parse(raw) as {
        data?: Array<{
          japanese?: Array<{ word?: string; reading?: string }>;
          senses?: Array<{ english_definitions?: string[]; parts_of_speech?: string[] }>;
        }>;
      };
      const first = parsed.data?.[0];
      if (!first) return null;
      const jp = first.japanese?.[0];
      const sense = first.senses?.[0];
      const item = {
        reading: toHiragana(jp?.reading ?? ''),
        base: jp?.word ?? word,
        meanings: sense?.english_definitions?.slice(0, 5) ?? [],
        pos: sense?.parts_of_speech?.[0]?.toLowerCase() ?? 'word',
      };
      if (this.jpCache.size >= 120) {
        const oldest = this.jpCache.keys().next().value;
        if (oldest !== undefined) this.jpCache.delete(oldest);
      }
      this.jpCache.set(word, item);
      return item;
    } catch {
      return null;
    }
  }

  sourceLang(): string {
    return this.japanese ? 'ja' : translateStore.source || 'auto';
  }

  cardId(): string {
    return vocabId(this.sourceLang(), this.card.baseWord || this.card.text);
  }

  async openWord(lineIdx: number, pieceIdx: number): Promise<void> {
    const piece = this.pieces?.[lineIdx]?.[pieceIdx];
    if (!piece || !piece.tap) return;
    const mine = ++this.cardSeq;

    this.card = {
      open: true,
      line: lineIdx,
      piece: pieceIdx,
      text: piece.text,
      reading: piece.reading,
      baseWord: piece.text.toLocaleLowerCase(),
      romaji: piece.romaji,
      pos: '',
      note: piece.note,
      lookup: null,
      busy: true,
      error: '',
    };

    try {
      if (this.japanese) {
        const jisho = await this.fetchJishoEntry(piece.text);
        if (mine !== this.seq && mine !== this.cardSeq) return;
        if (jisho) {
          this.card.baseWord = jisho.base;
          if (!this.card.reading && jisho.reading) {
            this.card.reading = jisho.reading;
            this.card.romaji = kanaToRomaji(jisho.reading);
          }
          if (jisho.pos) this.card.pos = jisho.pos;
        }
      }

      if (!this.card.note) {
        const sl = this.sourceLang();
        const tl = translateStore.targetLanguage();
        const raw = await api.googleTranslate(this.card.baseWord || piece.text, sl, tl, true);
        if (mine !== this.cardSeq) return;
        const parsed = parseLookup(JSON.parse(raw));
        this.card.lookup = parsed;
        if (!this.card.romaji && parsed.romaji && parsed.romaji.toLowerCase() !== piece.text.toLowerCase()) {
          this.card.romaji = parsed.romaji;
        }
      }
    } catch (e) {
      if (mine === this.cardSeq) {
        this.card.error = e instanceof Error ? e.message : String(e);
      }
    } finally {
      if (mine === this.cardSeq) {
        this.card.busy = false;
      }
    }
  }

  toggleSaveCard(): void {
    if (!this.vocabLoaded) return;
    const id = this.cardId();
    if (this.isSaved(id)) {
      this.removeWord(id);
      return;
    }
    const l = lyricsStore.lines[this.card.line];
    const t = player.currentTrack;
    const dictMeanings = this.card.note
      ? [this.card.note]
      : this.card.lookup?.senses.length
        ? this.card.lookup.senses.flatMap((s) => s.terms.slice(0, 4)).slice(0, 8)
        : this.card.lookup?.translation
          ? [this.card.lookup.translation]
          : [];
    this.saveWordDirect({
      id,
      word: this.card.baseWord || this.card.text,
      reading: this.card.reading,
      romaji: this.card.romaji,
      pos: this.card.pos,
      meanings: dictMeanings,
      lang: this.sourceLang(),
      line: l?.text ?? '',
      translation: translateStore.lines?.[this.card.line] ?? '',
      path: t?.path ?? '',
      title: t?.title ?? '',
      artist: t?.artist ?? '',
      t: lyricsStore.synced && l ? l.time : 0,
    });
  }

  async playVocabLine(path: string, secs: number): Promise<void> {
    if (!path) return;
    if (player.currentPath !== path) {
      const track = library.trackMap.get(path);
      if (!track) return;
      await player.playTrack(track, library.tracks);
      for (let i = 0; i < 40 && player.currentPath !== path; i++) {
        await new Promise((r) => setTimeout(r, 100));
      }
    }
    await player.seek(secs);
    if (!player.playing) await player.togglePlay();
  }
}

export const learnStore = new LearnStore();
