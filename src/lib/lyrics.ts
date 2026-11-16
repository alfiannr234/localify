import type { LyricLine, LyricWord } from './types';

export interface ParsedLyrics {
  lines: LyricLine[];
  synced: boolean;
  wordSynced: boolean;
}

const TIME_TAG_RE = /\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]/g;
const ENHANCED_WORD_RE = /<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>([^<]*)/g;
const KRC_LINE_RE = /^\[(\d+),(\d+)\](.*)$/;
const KRC_WORD_RE = /<(\d+),(\d+),\d+>([^<]*)/g;

export function parseLyrics(raw: string, formatHint?: string): ParsedLyrics {
  const trimmed = raw.trim();
  if (!trimmed) return { lines: [], synced: false, wordSynced: false };

  const fmt = (formatHint ?? detectLyricsFormat(trimmed)).toLowerCase();
  if (fmt === 'ttml') return parseTtml(trimmed);
  if (fmt === 'krc') return parseKrc(trimmed);
  if (fmt === 'lrc') return parseLrc(trimmed);

  // Fallback plain text lines
  const lines: LyricLine[] = trimmed.split(/\r?\n/).map((line, idx) => ({
    time: idx * 3,
    end: (idx + 1) * 3,
    text: line.trim(),
    words: [],
  }));
  return { lines: lines.filter((l) => l.text.length > 0), synced: false, wordSynced: false };
}

export function detectLyricsFormat(raw: string): 'ttml' | 'krc' | 'lrc' | 'txt' {
  const head = raw.trimStart();
  if (head.startsWith('<?xml') || head.startsWith('<tt') || head.includes('<p ')) return 'ttml';
  if (head.split(/\r?\n/).some((l) => KRC_LINE_RE.test(l.trim()))) return 'krc';
  if (head.split(/\r?\n/).some((l) => /\[\d{1,3}:\d{2}/.test(l))) return 'lrc';
  return 'txt';
}

function parseTagSeconds(minStr: string, secStr: string, fracStr?: string): number {
  const mins = Number(minStr);
  const secs = Number(secStr);
  let frac = 0;
  if (fracStr) {
    if (fracStr.length === 1) frac = Number(fracStr) / 10;
    else if (fracStr.length === 2) frac = Number(fracStr) / 100;
    else frac = Number(fracStr.slice(0, 3)) / 1000;
  }
  return mins * 60 + secs + frac;
}

function parseLrc(raw: string): ParsedLyrics {
  const rawLines = raw.split(/\r?\n/);
  let lrcHeaderOffsetSecs = 0;

  for (const line of rawLines) {
    const om = /^\[offset:([+-]?\d+)\]/i.exec(line.trim());
    if (om) {
      lrcHeaderOffsetSecs = -Number(om[1]) / 1000;
    }
  }

  const parsed: LyricLine[] = [];
  let hasWords = false;

  for (const rawLine of rawLines) {
    const line = rawLine.trim();
    if (!line) continue;
    // Skip metadata tags like [ar:...], [ti:...], [al:...], [offset:...]
    if (/^\[[a-zA-Z]+:[^\]]*\]$/.test(line)) continue;

    const times: number[] = [];
    let match: RegExpExecArray | null;
    TIME_TAG_RE.lastIndex = 0;
    let lastEnd = 0;
    while ((match = TIME_TAG_RE.exec(line)) !== null) {
      times.push(Math.max(0, parseTagSeconds(match[1], match[2], match[3]) + lrcHeaderOffsetSecs));
      lastEnd = match.index + match[0].length;
    }
    if (times.length === 0) continue;

    const rest = line.slice(lastEnd).trim();
    const words: LyricWord[] = [];

    if (ENHANCED_WORD_RE.test(rest)) {
      ENHANCED_WORD_RE.lastIndex = 0;
      let wm: RegExpExecArray | null;
      while ((wm = ENHANCED_WORD_RE.exec(rest)) !== null) {
        const wTime = Math.max(0, parseTagSeconds(wm[1], wm[2], wm[3]) + lrcHeaderOffsetSecs);
        words.push({ start: wTime, end: wTime + 0.35, text: wm[4] });
      }
      for (let i = 0; i < words.length - 1; i++) {
        words[i].end = Math.max(words[i].start + 0.05, words[i + 1].start);
      }
      if (words.length > 0) hasWords = true;
    }

    const plainText = words.length > 0 ? words.map((w) => w.text).join('').trim() : rest;
    for (const t of times) {
      const shiftedWords =
        times.length === 1
          ? words
          : words.map((w, idx) => {
              const delta = w.start - times[0];
              return {
                start: t + Math.max(0, delta),
                end: t + Math.max(0, w.end - times[0]),
                text: words[idx].text,
              };
            });
      parsed.push({
        time: t,
        end: t + 5,
        text: plainText,
        words: shiftedWords,
      });
    }
  }

  parsed.sort((a, b) => a.time - b.time);
  fillLineEnds(parsed);
  return { lines: parsed, synced: parsed.length > 0, wordSynced: hasWords };
}

function parseKrc(raw: string): ParsedLyrics {
  const lines: LyricLine[] = [];
  let hasWords = false;

  for (const rawLine of raw.split(/\r?\n/)) {
    const m = KRC_LINE_RE.exec(rawLine.trim());
    if (!m) continue;
    const startSec = Number(m[1]) / 1000;
    const durSec = Number(m[2]) / 1000;
    const body = m[3];

    const words: LyricWord[] = [];
    KRC_WORD_RE.lastIndex = 0;
    let wm: RegExpExecArray | null;
    while ((wm = KRC_WORD_RE.exec(body)) !== null) {
      const wStart = startSec + Number(wm[1]) / 1000;
      const wDur = Number(wm[2]) / 1000;
      words.push({
        start: wStart,
        end: wStart + Math.max(0.05, wDur),
        text: wm[3],
      });
    }

    if (words.length > 0) hasWords = true;
    const text = words.length > 0 ? words.map((w) => w.text).join('').trim() : body.trim();
    if (!text) continue;

    lines.push({
      time: startSec,
      end: startSec + Math.max(0.2, durSec),
      text,
      words,
    });
  }

  lines.sort((a, b) => a.time - b.time);
  return { lines, synced: lines.length > 0, wordSynced: hasWords };
}

function parseTtmlTime(expr: string | undefined): number | null {
  if (!expr) return null;
  const s = expr.trim();
  if (s.endsWith('ms')) {
    const n = Number(s.slice(0, -2));
    return Number.isFinite(n) ? n / 1000 : null;
  }
  if (s.endsWith('s')) {
    const n = Number(s.slice(0, -1));
    return Number.isFinite(n) ? n : null;
  }
  const parts = s.split(':').map(Number);
  if (parts.some(Number.isNaN)) return null;
  if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
  if (parts.length === 2) return parts[0] * 60 + parts[1];
  if (parts.length === 1) return parts[0];
  return null;
}

function decodeXmlEntities(str: string): string {
  return str
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'");
}

function parseTtml(raw: string): ParsedLyrics {
  const lines: LyricLine[] = [];
  let hasWords = false;
  const pRegex = /<p\b([^>]*)>([\s\S]*?)<\/p>/gi;
  let pm: RegExpExecArray | null;

  while ((pm = pRegex.exec(raw)) !== null) {
    const attrs = pm[1];
    const inner = pm[2];
    const beginM = /\bbegin\s*=\s*["']([^"']+)["']/i.exec(attrs);
    const endM = /\bend\s*=\s*["']([^"']+)["']/i.exec(attrs);
    const time = parseTtmlTime(beginM?.[1]) ?? 0;
    const end = parseTtmlTime(endM?.[1]) ?? time + 4;
    const bg = /x-bg/i.test(attrs);

    const words: LyricWord[] = [];
    const spanRegex = /<span\b([^>]*)>([\s\S]*?)<\/span>/gi;
    let sm: RegExpExecArray | null;
    while ((sm = spanRegex.exec(inner)) !== null) {
      const sAttrs = sm[1];
      const sb = /\bbegin\s*=\s*["']([^"']+)["']/i.exec(sAttrs);
      const se = /\bend\s*=\s*["']([^"']+)["']/i.exec(sAttrs);
      const st = parseTtmlTime(sb?.[1]);
      if (st !== null) {
        const send = parseTtmlTime(se?.[1]) ?? st + 0.35;
        words.push({
          start: st,
          end: Math.max(st + 0.05, send),
          text: decodeXmlEntities(sm[2].replace(/<[^>]+>/g, '')),
        });
      }
    }

    if (words.length > 0) hasWords = true;
    const plain = decodeXmlEntities(inner.replace(/<[^>]+>/g, ''))
      .replace(/\s+/g, ' ')
      .trim();
    if (!plain) continue;

    lines.push({
      time,
      end,
      text: plain,
      words,
      bg,
    });
  }

  lines.sort((a, b) => a.time - b.time);
  fillLineEnds(lines);
  return { lines, synced: lines.length > 0, wordSynced: hasWords };
}

function fillLineEnds(lines: LyricLine[]): void {
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].words.length > 0) {
      const lastWordEnd = lines[i].words[lines[i].words.length - 1].end;
      if (lastWordEnd > lines[i].time) {
        lines[i].end = lastWordEnd;
        continue;
      }
    }
    if (i < lines.length - 1 && lines[i + 1].time > lines[i].time) {
      lines[i].end = Math.min(lines[i].time + 12, lines[i + 1].time);
    }
  }
}

export interface ActiveLyricState {
  lineIndex: number;
  wordIndex: number;
  wordProgress: number;
}

/**
 * Positive `offsetSecs` makes lyrics appear earlier (matches Localify "+ = lebih cepat").
 */
export function getActiveLyricState(
  lines: LyricLine[],
  songSecs: number,
  offsetSecs: number
): ActiveLyricState {
  if (lines.length === 0) return { lineIndex: -1, wordIndex: -1, wordProgress: 0 };
  const t = songSecs + offsetSecs;
  if (t < lines[0].time) return { lineIndex: -1, wordIndex: -1, wordProgress: 0 };

  let lo = 0;
  let hi = lines.length - 1;
  let idx = 0;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (lines[mid].time <= t) {
      idx = mid;
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }

  const line = lines[idx];
  if (line.words.length === 0) {
    return { lineIndex: idx, wordIndex: -1, wordProgress: 0 };
  }

  let wIdx = -1;
  let wProg = 0;
  for (let i = 0; i < line.words.length; i++) {
    const w = line.words[i];
    if (t >= w.start) {
      wIdx = i;
      const span = Math.max(0.05, w.end - w.start);
      wProg = Math.min(1, Math.max(0, (t - w.start) / span));
    } else {
      break;
    }
  }

  return { lineIndex: idx, wordIndex: wIdx, wordProgress: wProg };
}

export function formatOffsetSeconds(secs: number): string {
  const fixed = Math.abs(secs) < 0.005 ? 0 : secs;
  const str = fixed.toFixed(2);
  return fixed > 0 ? `+${str}` : str;
}

export function parseOffsetInput(raw: string): number | null {
  const clean = raw.replace(/[sS\s]/g, '').replace(',', '.');
  if (!clean || clean === '+' || clean === '-') return null;
  const val = Number(clean);
  if (!Number.isFinite(val)) return null;
  return Math.max(-30, Math.min(30, Math.round(val * 100) / 100));
}
