import type { EqConfig } from './types';

export const EQ_FREQUENCIES = [32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000] as const;

export const EQ_BAND_LABELS = ['32', '64', '125', '250', '500', '1K', '2K', '4K', '8K', '16K'] as const;

export interface EqPreset {
  id: string;
  label: string;
  preampDb: number;
  gainsDb: number[];
}

export const EQ_PRESETS: EqPreset[] = [
  { id: 'flat', label: 'Flat', preampDb: 0, gainsDb: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0] },
  { id: 'bass', label: 'Bass Boost', preampDb: -2, gainsDb: [6.5, 5.5, 4, 2, 0, 0, 0, 0, 0.5, 0.5] },
  { id: 'vocal', label: 'Vocal Presence', preampDb: -1, gainsDb: [-2, -1, 0, 1.5, 3, 4.5, 4, 2.5, 1, 0] },
  { id: 'acoustic', label: 'Acoustic', preampDb: -1, gainsDb: [3, 2, 1, 0.5, 1.5, 1.5, 2.5, 3.5, 3, 2] },
  { id: 'electronic', label: 'Electronic', preampDb: -2, gainsDb: [5, 4.5, 2, -1, -1.5, 0, 1.5, 3.5, 4.5, 5] },
  { id: 'rock', label: 'Rock', preampDb: -1.5, gainsDb: [4, 3, 1.5, -0.5, -1, 0.5, 2.5, 3.5, 4, 3.5] },
  { id: 'treble', label: 'Air & Sparkle', preampDb: -1.5, gainsDb: [0, 0, 0, 0, 0.5, 1, 2.5, 4, 5.5, 6] },
  { id: 'latenight', label: 'Late Night', preampDb: -2, gainsDb: [-3, -1.5, 0.5, 2, 2.5, 2.5, 1.5, 0.5, -1, -2.5] },
];

export function defaultEqConfig(): EqConfig {
  return {
    enabled: false,
    preampDb: 0,
    gainsDb: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  };
}

/** Evaluates combined RBJ peaking biquad frequency response magnitude (dB) at `freqHz`. */
export function evalEqResponseDb(freqHz: number, cfg: EqConfig, sampleRate = 48000): number {
  let totalDb = cfg.preampDb;
  const q = 1.2;
  const wTest = (2 * Math.PI * freqHz) / sampleRate;
  const cosWTest = Math.cos(wTest);
  const cos2WTest = Math.cos(2 * wTest);
  const sinWTest = Math.sin(wTest);
  const sin2WTest = Math.sin(2 * wTest);

  for (let i = 0; i < EQ_FREQUENCIES.length; i++) {
    const gainDb = cfg.gainsDb[i] ?? 0;
    const fc = EQ_FREQUENCIES[i];
    if (Math.abs(gainDb) < 0.05 || fc >= sampleRate * 0.48) continue;

    const a = Math.pow(10, gainDb / 40);
    const omega = (2 * Math.PI * fc) / sampleRate;
    const sinW = Math.sin(omega);
    const cosW = Math.cos(omega);
    const alpha = sinW / (2 * q);

    const b0 = 1 + alpha * a;
    const b1 = -2 * cosW;
    const b2 = 1 - alpha * a;
    const a0 = 1 + alpha / a;
    const a1 = -2 * cosW;
    const a2 = 1 - alpha / a;

    const nb0 = b0 / a0;
    const nb1 = b1 / a0;
    const nb2 = b2 / a0;
    const na1 = a1 / a0;
    const na2 = a2 / a0;

    const numRe = nb0 + nb1 * cosWTest + nb2 * cos2WTest;
    const numIm = -(nb1 * sinWTest + nb2 * sin2WTest);
    const denRe = 1 + na1 * cosWTest + na2 * cos2WTest;
    const denIm = -(na1 * sinWTest + na2 * sin2WTest);

    const numMag2 = numRe * numRe + numIm * numIm;
    const denMag2 = denRe * denRe + denIm * denIm;
    const mag = Math.sqrt(numMag2 / Math.max(1e-12, denMag2));
    totalDb += 20 * Math.log10(Math.max(1e-6, mag));
  }

  return totalDb;
}
