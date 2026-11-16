import { describe, expect, it } from 'vitest';
import { contrastRatio, resolveAccessibleAccent } from '../lib/color';
import { EQ_PRESETS, evalEqResponseDb } from '../lib/eq';
import {
  formatOffsetSeconds,
  getActiveLyricState,
  parseLyrics,
  parseOffsetInput,
} from '../lib/lyrics';
import { buildQueueOrder, resolveNextIndex } from '../lib/queue';
import { needsRomanization, romanizeText } from '../lib/romanize';
import type { Track } from '../lib/types';

function mockTrack(path: string, title = 'Song'): Track {
  return {
    path,
    title,
    artist: 'Artist',
    album: 'Album',
    albumArtist: 'Artist',
    genre: 'Pop',
    year: 2024,
    trackNo: 1,
    discNo: 1,
    duration: 180,
    bitrate: 320,
    sampleRate: 44100,
    bitDepth: null,
    channels: 2,
    codec: 'MP3',
    fileSize: 1024,
    artHash: null,
    artColor: null,
    modified: 0,
    addedAt: 0,
    liked: false,
    playCount: 0,
  };
}

describe('Lyrics parser and sync engine', () => {
  it('parses enhanced LRC, multi-timestamp lines, and applies offset sign consistently', () => {
    const raw = `
[ti:Royalty]
[00:10.00][00:40.00]They call me royalty
[00:15.50]<00:15.50>Say <00:16.00>my <00:16.50>name
`;
    const parsed = parseLyrics(raw, 'lrc');
    expect(parsed.synced).toBe(true);
    expect(parsed.wordSynced).toBe(true);
    expect(parsed.lines.length).toBe(3);
    expect(parsed.lines[0].time).toBeCloseTo(10.0);
    expect(parsed.lines[2].time).toBeCloseTo(40.0);

    // Positive offset (+0.40s = lebih cepat) triggers 10.00s line at 9.60s track time
    expect(getActiveLyricState(parsed.lines, 9.65, 0.4).lineIndex).toBe(0);
    // Negative offset (-0.50s = lebih lambat) delays 10.00s line until 10.50s track time
    expect(getActiveLyricState(parsed.lines, 10.2, -0.5).lineIndex).toBe(-1);
    expect(getActiveLyricState(parsed.lines, 10.55, -0.5).lineIndex).toBe(0);
  });

  it('parses Kugou KRC word-by-word syllables', () => {
    const krc = '[5000,2000]<0,400,0>Hel<400,600,0>lo <1000,500,0>world';
    const parsed = parseLyrics(krc, 'krc');
    expect(parsed.wordSynced).toBe(true);
    expect(parsed.lines[0].text).toBe('Hello world');
    expect(parsed.lines[0].words.length).toBe(3);
    const active = getActiveLyricState(parsed.lines, 5.5, 0);
    expect(active.lineIndex).toBe(0);
    expect(active.wordIndex).toBe(1);
  });

  it('parses TTML spans with timing attributes', () => {
    const ttml = `<tt><body><div><p begin="00:12.50" end="00:16.00"><span begin="12.5s" end="13.5s">Night </span><span begin="13.5s" end="15.0s">sky</span></p></div></body></tt>`;
    const parsed = parseLyrics(ttml, 'ttml');
    expect(parsed.synced).toBe(true);
    expect(parsed.wordSynced).toBe(true);
    expect(parsed.lines[0].text).toBe('Night sky');
  });

  it('parses manual offset input with dots or Indonesian commas', () => {
    expect(parseOffsetInput('-0.40')).toBe(-0.4);
    expect(parseOffsetInput('0,20')).toBe(0.2);
    expect(parseOffsetInput('+1.5s')).toBe(1.5);
    expect(formatOffsetSeconds(0.2)).toBe('+0.20');
    expect(formatOffsetSeconds(-0.5)).toBe('-0.50');
  });
});

describe('Romanizer', () => {
  it('romanizes Japanese Kana and Hangul Jamo without dictionaries', () => {
    expect(needsRomanization('君の夢')).toBe(true);
    expect(needsRomanization('Hello world')).toBe(false);
    expect(romanizeText('さくら')).toBe('sakura');
    expect(romanizeText('사랑')).toBe('sarang');
    expect(romanizeText('「世界」')).toContain('sekai');
  });
});

describe('Queue context & Search fix', () => {
  it('keeps full context tracks ordered cleanly when playing a track', () => {
    const tracks = [mockTrack('a.mp3'), mockTrack('b.mp3'), mockTrack('c.mp3')];
    const order = buildQueueOrder(tracks, 'b.mp3', false);
    expect(order).toEqual(['a.mp3', 'b.mp3', 'c.mp3']);
    expect(resolveNextIndex(1, 3, 'off', false)).toBe(2);
    expect(resolveNextIndex(2, 3, 'all', false)).toBe(0);
  });
});

describe('EQ & Color WCAG contrast', () => {
  it('evaluates flat preset cleanly at 0 dB', () => {
    const flat = EQ_PRESETS.find((p) => p.id === 'flat')!;
    expect(evalEqResponseDb(1000, { enabled: true, preampDb: flat.preampDb, gainsDb: flat.gainsDb })).toBeCloseTo(0);
  });

  it('ensures adaptive artwork accents satisfy >= 4.5:1 WCAG contrast', () => {
    const resDark = resolveAccessibleAccent('#4a2010', '#111318', false);
    expect(contrastRatio(resDark.accent, '#111318')).toBeGreaterThanOrEqual(4.5);

    const resLight = resolveAccessibleAccent('#ffaa66', '#F4F5F7', true);
    expect(contrastRatio(resLight.accent, '#F4F5F7')).toBeGreaterThanOrEqual(4.5);
  });
});
