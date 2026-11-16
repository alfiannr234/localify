export interface LookupSense {
  pos: string;
  terms: string[];
}

export interface WordLookup {
  translation: string;
  romaji: string;
  source: string;
  senses: LookupSense[];
}

export function parseLookup(json: unknown): WordLookup {
  if (!Array.isArray(json) || !Array.isArray(json[0])) {
    throw new Error('Unexpected response from dictionary lookup');
  }
  const segs = json[0] as unknown[][];
  const translation = segs
    .map((s) => (typeof s?.[0] === 'string' ? s[0] : ''))
    .join('')
    .trim();
  const romaji = segs
    .map((s) => (s?.[0] == null && typeof s?.[3] === 'string' ? s[3] : ''))
    .join('')
    .trim();
  const senses = (Array.isArray(json[1]) ? (json[1] as unknown[][]) : [])
    .filter((entry) => typeof entry?.[0] === 'string' && Array.isArray(entry[1]))
    .map((entry) => ({
      pos: entry[0] as string,
      terms: (entry[1] as unknown[]).filter((t): t is string => typeof t === 'string'),
    }));
  return {
    translation,
    romaji,
    source: typeof json[2] === 'string' ? json[2] : '',
    senses,
  };
}
