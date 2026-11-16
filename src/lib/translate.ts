export const TRANSLATE_LANGUAGES: Array<[string, string]> = [
  ['ar', 'Arabic'],
  ['bn', 'Bengali'],
  ['zh-CN', 'Chinese (Simplified)'],
  ['zh-TW', 'Chinese (Traditional)'],
  ['nl', 'Dutch'],
  ['en', 'English'],
  ['tl', 'Filipino'],
  ['fr', 'French'],
  ['de', 'German'],
  ['hi', 'Hindi'],
  ['id', 'Indonesian'],
  ['it', 'Italian'],
  ['ja', 'Japanese'],
  ['jw', 'Javanese'],
  ['ko', 'Korean'],
  ['ms', 'Malay'],
  ['pt', 'Portuguese'],
  ['ru', 'Russian'],
  ['es', 'Spanish'],
  ['su', 'Sundanese'],
  ['th', 'Thai'],
  ['tr', 'Turkish'],
  ['vi', 'Vietnamese'],
];

export function languageName(code: string): string {
  return TRANSLATE_LANGUAGES.find(([c]) => c === code)?.[1] ?? code;
}

export function targetForLocale(locale: string): string {
  const l = locale.toLowerCase();
  if (l.startsWith('zh')) return /tw|hk|mo|hant/.test(l) ? 'zh-TW' : 'zh-CN';
  const base = l.split('-')[0];
  return TRANSLATE_LANGUAGES.some(([c]) => c === base) ? base : 'en';
}

export function sameLanguage(a: string, b: string): boolean {
  return (
    a.toLowerCase() === b.toLowerCase() ||
    (!a.toLowerCase().startsWith('zh') && a.split('-')[0].toLowerCase() === b.split('-')[0].toLowerCase())
  );
}

export function uniqueLines(lines: string[]): string[] {
  return [...new Set(lines.map((l) => l.trim()).filter(Boolean))];
}

export function batchTexts(texts: string[], maxChars = 4000): string[][] {
  const out: string[][] = [];
  let len = 0;
  for (const t of texts) {
    const cur = out[out.length - 1];
    if (cur && len + 1 + t.length <= maxChars) {
      cur.push(t);
      len += 1 + t.length;
    } else {
      out.push([t]);
      len = t.length;
    }
  }
  return out;
}

export function parseGtx(json: unknown): { text: string; source: string } {
  if (!Array.isArray(json) || !Array.isArray(json[0])) {
    throw new Error('Unexpected response from translation service');
  }
  const text = (json[0] as unknown[])
    .map((seg) => (Array.isArray(seg) && typeof seg[0] === 'string' ? seg[0] : ''))
    .join('');
  const source = typeof json[2] === 'string' ? json[2] : '';
  return { text, source };
}

export function alignTranslatedLines(sent: string[], reply: string): string[] | null {
  const out = reply
    .replace(/\n+$/, '')
    .split('\n')
    .map((s) => s.trim());
  return out.length === sent.length ? out : null;
}

export function mapLinesBack(lines: string[], dict: Record<string, string>): Array<string | null> {
  return lines.map((l) => {
    const t = dict[l.trim()];
    if (!t) return null;
    return t.trim().toLowerCase() === l.trim().toLowerCase() ? null : t;
  });
}

export function fnvHashHex(input: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    h = Math.imul(h ^ input.charCodeAt(i), 0x01000193);
  }
  return (h >>> 0).toString(16).padStart(8, '0');
}
