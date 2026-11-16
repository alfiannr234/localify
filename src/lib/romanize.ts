// Zero-dependency Hangul Jamo + Japanese Kana + common lyric Kanji romanizer.

const HANGUL_START = 0xac00;
const HANGUL_END = 0xd7a3;

const INITIALS = [
  'g', 'kk', 'n', 'd', 'tt', 'r', 'm', 'b', 'pp', 's', 'ss', '', 'j', 'jj', 'ch', 'k', 't', 'p', 'h',
];
const MEDIALS = [
  'a', 'ae', 'ya', 'yae', 'eo', 'e', 'yeo', 'ye', 'o', 'wa', 'wae', 'oe', 'yo', 'u', 'wo', 'we', 'wi', 'yu', 'eu', 'ui', 'i',
];
const FINALS = [
  '', 'k', 'k', 'k', 'n', 'n', 'n', 't', 'l', 'l', 'l', 'l', 'l', 'l', 'l', 'l', 'm', 'p', 'p', 't', 't', 'ng', 't', 't', 'k', 't', 'p', 't',
];

const KANJI_WORDS: Array<[string, string]> = [
  ['永遠', 'eien'],
  ['記憶', 'kioku'],
  ['太陽', 'taiyou'],
  ['約束', 'yakusoku'],
  ['世界', 'sekai'],
  ['未来', 'mirai'],
  ['希望', 'kibou'],
  ['笑顔', 'egao'],
  ['痛み', 'itami'],
  ['涙', 'namida'],
  ['心', 'kokoro'],
  ['桜', 'sakura'],
  ['夢', 'yume'],
  ['恋', 'koi'],
  ['愛', 'ai'],
  ['空', 'sora'],
  ['星', 'hoshi'],
  ['夜', 'yoru'],
  ['君', 'kimi'],
  ['僕', 'boku'],
  ['私', 'watashi'],
  ['花', 'hana'],
  ['風', 'kaze'],
  ['雨', 'ame'],
  ['光', 'hikari'],
  ['闇', 'yami'],
  ['時', 'toki'],
  ['夏', 'natsu'],
  ['冬', 'fuyu'],
  ['春', 'haru'],
  ['秋', 'aki'],
  ['海', 'umi'],
  ['月', 'tsuki'],
  ['命', 'inochi'],
  ['声', 'koe'],
  ['歌', 'uta'],
  ['夢中', 'muchuu'],
  ['明日', 'ashita'],
  ['今日', 'kyou'],
  ['昨日', 'kinou'],
  ['一人', 'hitori'],
  ['二人', 'futari'],
  ['今', 'ima'],
  ['神', 'kami'],
  ['雪', 'yuki'],
  ['空色', 'sorairo'],
  ['青空', 'aozora'],
];

const DIGRAPHS: Record<string, string> = {
  きゃ: 'kya', きゅ: 'kyu', きょ: 'kyo',
  しゃ: 'sha', しゅ: 'shu', しょ: 'sho',
  ちゃ: 'cha', ちゅ: 'chu', ちょ: 'cho',
  にゃ: 'nya', にゅ: 'nyu', にょ: 'nyo',
  ひゃ: 'hya', ひゅ: 'hyu', ひょ: 'hyo',
  みゃ: 'mya', みゅ: 'myu', みょ: 'myo',
  りゃ: 'rya', りゅ: 'ryu', りょ: 'ryo',
  ぎゃ: 'gya', ぎゅ: 'gyu', ぎょ: 'gyo',
  じゃ: 'ja', じゅ: 'ju', じょ: 'jo',
  びゃ: 'bya', びゅ: 'byu', びょ: 'byo',
  ぴゃ: 'pya', ぴゅ: 'pyu', ぴょ: 'pyo',
  ふぁ: 'fa', ふぃ: 'fi', ふぇ: 'fe', ふぉ: 'fo',
  てぃ: 'ti', でぃ: 'di', とぅ: 'tu', どぅ: 'du',
  うぃ: 'wi', うぇ: 'we', うぉ: 'wo',
  ヴぁ: 'va', ヴぃ: 'vi', ヴぇ: 've', ヴぉ: 'vo',
};

const KANA: Record<string, string> = {
  あ: 'a', い: 'i', う: 'u', え: 'e', お: 'o',
  か: 'ka', き: 'ki', く: 'ku', け: 'ke', こ: 'ko',
  さ: 'sa', し: 'shi', す: 'su', せ: 'se', そ: 'so',
  た: 'ta', ち: 'chi', つ: 'tsu', て: 'te', と: 'to',
  な: 'na', に: 'ni', ぬ: 'nu', ね: 'ne', の: 'no',
  は: 'ha', ひ: 'hi', ふ: 'fu', へ: 'he', ほ: 'ho',
  ま: 'ma', み: 'mi', む: 'mu', め: 'me', も: 'mo',
  や: 'ya', ゆ: 'yu', よ: 'yo',
  ら: 'ra', り: 'ri', る: 'ru', れ: 're', ろ: 'ro',
  わ: 'wa', を: 'o', ん: 'n',
  が: 'ga', ぎ: 'gi', ぐ: 'gu', げ: 'ge', ご: 'go',
  ざ: 'za', じ: 'ji', ず: 'zu', ぜ: 'ze', ぞ: 'zo',
  だ: 'da', ぢ: 'ji', づ: 'zu', で: 'de', ど: 'do',
  ば: 'ba', び: 'bi', ぶ: 'bu', べ: 'be', ぼ: 'bo',
  ぱ: 'pa', ぴ: 'pi', ぷ: 'pu', ぺ: 'pe', ぽ: 'po',
  ぁ: 'a', ぃ: 'i', ぅ: 'u', ぇ: 'e', ぉ: 'o',
  ゃ: 'ya', ゅ: 'yu', ょ: 'yo', ヴ: 'vu',
};

function toHiraganaChar(ch: string): string {
  const code = ch.charCodeAt(0);
  if (code >= 0x30a1 && code <= 0x30f6) {
    return String.fromCharCode(code - 0x60);
  }
  return ch;
}

export function needsRomanization(text: string): boolean {
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    if (
      (c >= 0x3040 && c <= 0x30ff) || // Hiragana & Katakana
      (c >= HANGUL_START && c <= HANGUL_END) || // Hangul Syllables
      (c >= 0x4e00 && c <= 0x9fff) // CJK Unified Ideographs
    ) {
      return true;
    }
  }
  return false;
}

export function romanizeText(input: string): string {
  let work = input
    .replace(/[「」『』]/g, '"')
    .replace(/[・]/g, ' ')
    .replace(/　/g, ' ');

  for (const [kanji, reading] of KANJI_WORDS) {
    if (work.includes(kanji)) {
      work = work.split(kanji).join(` ${reading} `);
    }
  }

  let out = '';
  let lastVowel = '';

  for (let i = 0; i < work.length; i++) {
    const rawCh = work[i];
    const code = rawCh.charCodeAt(0);

    // Hangul decomposition
    if (code >= HANGUL_START && code <= HANGUL_END) {
      const idx = code - HANGUL_START;
      const l = Math.floor(idx / (21 * 28));
      const v = Math.floor((idx % (21 * 28)) / 28);
      const t = idx % 28;
      const syl = INITIALS[l] + MEDIALS[v] + FINALS[t];
      out += syl;
      lastVowel = MEDIALS[v].slice(-1);
      continue;
    }

    const hira = toHiraganaChar(rawCh);
    if (hira === 'ー' || rawCh === 'ー') {
      if (lastVowel) out += lastVowel;
      continue;
    }

    if (hira === 'っ') {
      const nextHira = i + 1 < work.length ? toHiraganaChar(work[i + 1]) : '';
      const nextRom = KANA[nextHira];
      if (nextRom && /^[bcdfghjklmnpqrstvwxyz]/.test(nextRom)) {
        out += nextRom[0];
      }
      continue;
    }

    if (i + 1 < work.length) {
      const pair = hira + toHiraganaChar(work[i + 1]);
      if (DIGRAPHS[pair]) {
        const rom = DIGRAPHS[pair];
        out += rom;
        lastVowel = rom.slice(-1);
        i++;
        continue;
      }
    }

    if (KANA[hira]) {
      const rom = KANA[hira];
      out += rom;
      const m = /[aeiou]$/.exec(rom);
      lastVowel = m ? m[0] : '';
      continue;
    }

    out += rawCh;
    if (!/[a-zA-Z0-9]/.test(rawCh)) {
      lastVowel = '';
    }
  }

  return out.replace(/\s{2,}/g, ' ').trim();
}
