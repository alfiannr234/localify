export interface VocabItem {
  id: string;
  word: string;
  reading: string;
  romaji: string;
  pos: string;
  meanings: string[];
  lang: string;
  line: string;
  translation: string;
  path: string;
  title: string;
  artist: string;
  t: number;
  added: number;
  box: number;
  due: number;
}

export const DAY_MS = 86_400_000;
export const LEITNER_INTERVALS = [0, 1, 3, 7, 14, 30];

export function vocabId(lang: string, word: string): string {
  return `${lang}:${word}`;
}

export function reviewVocab(item: VocabItem, knew: boolean, now = Date.now()): VocabItem {
  const box = knew ? Math.min(item.box + 1, LEITNER_INTERVALS.length - 1) : 0;
  return {
    ...item,
    box,
    due: now + LEITNER_INTERVALS[box] * DAY_MS,
  };
}

export function dueVocabItems(items: VocabItem[], now = Date.now()): VocabItem[] {
  return items.filter((i) => i.due <= now).sort((a, b) => a.due - b.due);
}
