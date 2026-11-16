export function parseHex(hex: string): [number, number, number] {
  const clean = hex.replace('#', '').trim();
  if (clean.length !== 6) return [245, 130, 62];
  const r = parseInt(clean.slice(0, 2), 16);
  const g = parseInt(clean.slice(2, 4), 16);
  const b = parseInt(clean.slice(4, 6), 16);
  if ([r, g, b].some(Number.isNaN)) return [245, 130, 62];
  return [r, g, b];
}

export function toHex(r: number, g: number, b: number): string {
  const c = (n: number) => Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, '0');
  return `#${c(r)}${c(g)}${c(b)}`;
}

export function relativeLuminance(rgb: [number, number, number]): number {
  const linear = rgb.map((v) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
}

export function contrastRatio(hexA: string, hexB: string): number {
  const la = relativeLuminance(parseHex(hexA));
  const lb = relativeLuminance(parseHex(hexB));
  const bright = Math.max(la, lb);
  const dark = Math.min(la, lb);
  return (bright + 0.05) / (dark + 0.05);
}

/**
 * Adjusts an album art accent hex so it stays readable against `surfaceHex`
 * (achieving >= 4.5:1 WCAG AA ratio) and computes a high-contrast foreground color.
 */
export function resolveAccessibleAccent(
  rawHex: string,
  surfaceHex: string,
  isLightTheme: boolean
): { accent: string; accentHover: string; accentFg: string; accentSoft: string } {
  let [r, g, b] = parseHex(rawHex);
  for (let i = 0; i < 24; i++) {
    const candidate = toHex(r, g, b);
    if (contrastRatio(candidate, surfaceHex) >= 4.55) {
      break;
    }
    if (isLightTheme) {
      r *= 0.88;
      g *= 0.88;
      b *= 0.88;
    } else {
      r = r + (255 - r) * 0.14;
      g = g + (255 - g) * 0.14;
      b = b + (255 - b) * 0.14;
    }
  }

  const accent = toHex(r, g, b);
  const hover = isLightTheme
    ? toHex(r * 0.88, g * 0.88, b * 0.88)
    : toHex(r + (255 - r) * 0.16, g + (255 - g) * 0.16, b + (255 - b) * 0.16);

  const darkContrast = contrastRatio(accent, '#111318');
  const lightContrast = contrastRatio(accent, '#FFFFFF');
  const accentFg = darkContrast >= lightContrast ? '#111318' : '#FFFFFF';
  const [ar, ag, ab] = parseHex(accent);
  const accentSoft = `rgba(${ar}, ${ag}, ${ab}, ${isLightTheme ? 0.13 : 0.18})`;

  return { accent, accentHover: hover, accentFg, accentSoft };
}
