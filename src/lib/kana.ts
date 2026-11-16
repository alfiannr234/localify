const BASE_KANA: Record<string, string> = {
  あ: 'a', い: 'i', う: 'u', え: 'e', お: 'o',
  か: 'ka', き: 'ki', く: 'ku', け: 'ke', こ: 'ko', が: 'ga', ぎ: 'gi', ぐ: 'gu', げ: 'ge', ご: 'go',
  さ: 'sa', し: 'shi', す: 'su', せ: 'se', そ: 'so', ざ: 'za', じ: 'ji', ず: 'zu', ぜ: 'ze', ぞ: 'zo',
  た: 'ta', ち: 'chi', つ: 'tsu', て: 'te', と: 'to', だ: 'da', ぢ: 'ji', づ: 'zu', で: 'de', ど: 'do',
  な: 'na', に: 'ni', ぬ: 'nu', ね: 'ne', の: 'no',
  は: 'ha', ひ: 'hi', ふ: 'fu', へ: 'he', ほ: 'ho', ば: 'ba', び: 'bi', ぶ: 'bu', べ: 'be', ぼ: 'bo',
  ぱ: 'pa', ぴ: 'pi', ぷ: 'pu', ぺ: 'pe', ぽ: 'po',
  ま: 'ma', み: 'mi', む: 'mu', め: 'me', も: 'mo', や: 'ya', ゆ: 'yu', よ: 'yo',
  ら: 'ra', り: 'ri', る: 'ru', れ: 're', ろ: 'ro', わ: 'wa', ゐ: 'i', ゑ: 'e', を: 'o', ゔ: 'vu',
};

const YOON: Record<string, string> = { ゃ: 'a', ゅ: 'u', ょ: 'o' };
const SMALL: Record<string, string> = { ぁ: 'a', ぃ: 'i', ぅ: 'u', ぇ: 'e', ぉ: 'o', ゎ: 'wa' };

export const COMMON_KANJI_READINGS: Record<string, string> = {
  夢: 'ゆめ', 恋: 'こい', 愛: 'あい', 空: 'そら', 星: 'ほし', 夜: 'よる', 君: 'きみ', 僕: 'ぼく',
  私: 'わたし', 俺: 'おれ', 名前: 'なまえ', 世界: 'せかい', 未来: 'みらい', 涙: 'なみだ',
  心: 'こころ', 花: 'はな', 風: 'かぜ', 雨: 'あめ', 光: 'ひかり', 闇: 'やみ', 時: 'とき',
  永遠: 'えいえん', 記憶: 'きおく', 桜: 'さくら', 夏: 'なつ', 冬: 'ふゆ', 春: 'はる', 秋: 'あき',
  海: 'うみ', 月: 'つき', 太陽: 'たいよう', 命: 'いのち', 希望: 'きぼう', 痛み: 'いたみ',
  笑顔: 'えがお', 約束: 'やくそく', 声: 'こえ', 歌: 'うた', 明日: 'あした', 今日: 'きょう',
  昨日: 'きのう', 一人: 'ひとり', 二人: 'ふたり', 今: 'いま', 神: 'かみ', 雪: 'ゆき',
  言葉: 'ことば', 気持ち: 'きもち', 瞳: 'ひとみ', 季節: 'きせつ', 雲: 'くも', 道: 'みち',
  扉: 'とびら', 影: 'かげ', 夢中: 'むちゅう', 青空: 'あおぞら', 真実: 'しんじつ',
  運命: 'うんめい', 奇跡: 'きせき', 悲しみ: 'かなしみ', 幸せ: 'しあわせ', 優しさ: 'やさしさ',
  強さ: 'つよさ', 呼ぶ: 'よぶ', 呼ん: 'よん', 見る: 'みる', 見: 'み', 聞く: 'きく',
  歩く: 'あるく', 走る: 'はしる', 笑う: 'わらう', 泣く: 'なく', 知る: 'しる',
  思う: 'おもう', 忘れる: 'わすれる', 信じ: 'しんじ', 愛し: 'あいし', 好き: 'すき',
};

export const JP_PARTICLES: Record<string, string> = {
  は: 'topic marker: "as for ..."',
  が: 'subject marker',
  を: 'object marker: what the verb acts on',
  に: 'to / at / in (target, time, place)',
  へ: 'toward',
  で: 'at / by / with',
  と: 'and / with / quotes',
  の: 'links nouns: possessive',
  も: 'also / too / even',
  から: 'from / because',
  まで: 'until / as far as',
  より: 'than / from',
  や: 'and (among others)',
  か: 'question marker / or',
  よ: 'emphasis ("I tell you")',
  ね: 'confirmation ("right?")',
  な: 'emphasis or negative command',
  けど: 'but / although',
  のに: 'even though',
  ので: 'because / so',
  だけ: 'only / just',
  て: 'te-form connector ("and then / -ing")',
  た: 'past tense ending',
  ない: 'negative: "not"',
  たい: 'want to ...',
};

export function toHiragana(s: string): string {
  return s.replace(/[ァ-ヶ]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 0x60));
}

export function hasKanji(s: string): boolean {
  return /[\u3400-\u9fff\uf900-\ufaff々〆]/.test(s);
}

export function hasJapanese(lines: string[]): boolean {
  return lines.some((l) => /[\u3040-\u30ff]/.test(l));
}

const stem = (r: string) => (r === 'u' ? 'w' : r.replace(/[aiueo]$/, ''));

export function kanaToRomaji(input: string): string {
  const s = [...toHiragana(input)];
  const syl: string[] = [];
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    const next = s[i + 1];
    let r = BASE_KANA[c];
    if (r === undefined) {
      syl.push(YOON[c] ? `y${YOON[c]}` : (SMALL[c] ?? c));
      continue;
    }
    if (next in YOON && r.length > 1 && r.endsWith('i')) {
      const st = stem(r);
      r = /(sh|ch|j)$/.test(st) ? st + YOON[next] : `${st}y${YOON[next]}`;
      i++;
    } else if (next in SMALL && next !== 'ゎ') {
      r = stem(r) + SMALL[next];
      i++;
    }
    syl.push(r);
  }
  let out = '';
  syl.forEach((r, k) => {
    const next = syl[k + 1] ?? '';
    if (r === 'っ') {
      out += /^[b-df-hj-np-tv-z]/.test(next) ? (next.startsWith('ch') ? 't' : next[0]) : '';
    } else if (r === 'ん') {
      out += /^[aiueoy]/.test(next) ? "n'" : 'n';
    } else if (r === 'ー') {
      out += /[aiueo]$/.exec(out)?.[0] ?? '-';
    } else {
      out += r;
    }
  });
  return out;
}

export interface RubySegment {
  text: string;
  rt?: string;
}

export function buildFurigana(surface: string, reading: string): RubySegment[] {
  if (!reading || !hasKanji(surface)) return [{ text: surface }];
  const runs = surface.match(/[\u3400-\u9fff\uf900-\ufaff々〆]+|[^\u3400-\u9fff\uf900-\ufaff々〆]+/g) ?? [surface];
  const esc = (k: string) => toHiragana(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const re = new RegExp(`^${runs.map((r) => (hasKanji(r) ? '(.+?)' : esc(r))).join('')}$`);
  const m = re.exec(toHiragana(reading));
  if (!m) return [{ text: surface, rt: toHiragana(reading) }];
  let g = 1;
  return runs.map((r) => (hasKanji(r) ? { text: r, rt: m[g++] } : { text: r }));
}

export function kanaOnlyReading(text: string): string {
  if (!text) return '';
  if (COMMON_KANJI_READINGS[text]) return COMMON_KANJI_READINGS[text];
  if (!hasKanji(text) && /[\u3040-\u30ff]/.test(text)) return toHiragana(text);
  return '';
}
