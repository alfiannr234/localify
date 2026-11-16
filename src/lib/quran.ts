const TABLE: Array<[string, string, number]> = [
  ['الفاتحة', 'Al-Fatihah', 7], ['البقرة', 'Al-Baqarah', 286], ['آل عمران', "Ali 'Imran", 200],
  ['النساء', 'An-Nisa', 176], ['المائدة', "Al-Ma'idah", 120], ['الأنعام', "Al-An'am", 165],
  ['الأعراف', "Al-A'raf", 206], ['الأنفال', 'Al-Anfal', 75], ['التوبة', 'At-Tawbah', 129],
  ['يونس', 'Yunus', 109], ['هود', 'Hud', 123], ['يوسف', 'Yusuf', 111],
  ['الرعد', "Ar-Ra'd", 43], ['ابراهيم', 'Ibrahim', 52], ['الحجر', 'Al-Hijr', 99],
  ['النحل', 'An-Nahl', 128], ['الإسراء', 'Al-Isra', 111], ['الكهف', 'Al-Kahf', 110],
  ['مريم', 'Maryam', 98], ['طه', 'Taha', 135], ['الأنبياء', 'Al-Anbya', 112],
  ['الحج', 'Al-Hajj', 78], ['المؤمنون', "Al-Mu'minun", 118], ['النور', 'An-Nur', 64],
  ['الفرقان', 'Al-Furqan', 77], ['الشعراء', "Ash-Shu'ara", 227], ['النمل', 'An-Naml', 93],
  ['القصص', 'Al-Qasas', 88], ['العنكبوت', "Al-'Ankabut", 69], ['الروم', 'Ar-Rum', 60],
  ['لقمان', 'Luqman', 34], ['السجدة', 'As-Sajdah', 30], ['الأحزاب', 'Al-Ahzab', 73],
  ['سبإ', 'Saba', 54], ['فاطر', 'Fatir', 45], ['يس', 'Ya-Sin', 83],
  ['الصافات', 'As-Saffat', 182], ['ص', 'Sad', 88], ['الزمر', 'Az-Zumar', 75],
  ['غافر', 'Ghafir', 85], ['فصلت', 'Fussilat', 54], ['الشورى', 'Ash-Shuraa', 53],
  ['الزخرف', 'Az-Zukhruf', 89], ['الدخان', 'Ad-Dukhan', 59], ['الجاثية', 'Al-Jathiyah', 37],
  ['الأحقاف', 'Al-Ahqaf', 35], ['محمد', 'Muhammad', 38], ['الفتح', 'Al-Fath', 29],
  ['الحجرات', 'Al-Hujurat', 18], ['ق', 'Qaf', 45], ['الذاريات', 'Adh-Dhariyat', 60],
  ['الطور', 'At-Tur', 49], ['النجم', 'An-Najm', 62], ['القمر', 'Al-Qamar', 55],
  ['الرحمن', 'Ar-Rahman', 78], ['الواقعة', "Al-Waqi'ah", 96], ['الحديد', 'Al-Hadid', 29],
  ['المجادلة', 'Al-Mujadila', 22], ['الحشر', 'Al-Hashr', 24], ['الممتحنة', 'Al-Mumtahanah', 13],
  ['الصف', 'As-Saf', 14], ['الجمعة', "Al-Jumu'ah", 11], ['المنافقون', 'Al-Munafiqun', 11],
  ['التغابن', 'At-Taghabun', 18], ['الطلاق', 'At-Talaq', 12], ['التحريم', 'At-Tahrim', 12],
  ['الملك', 'Al-Mulk', 30], ['القلم', 'Al-Qalam', 52], ['الحاقة', 'Al-Haqqah', 52],
  ['المعارج', "Al-Ma'arij", 44], ['نوح', 'Nuh', 28], ['الجن', 'Al-Jinn', 28],
  ['المزمل', 'Al-Muzzammil', 20], ['المدثر', 'Al-Muddaththir', 56], ['القيامة', 'Al-Qiyamah', 40],
  ['الانسان', 'Al-Insan', 31], ['المرسلات', 'Al-Mursalat', 50], ['النبإ', 'An-Naba', 40],
  ['النازعات', "An-Nazi'at", 46], ['عبس', "'Abasa", 42], ['التكوير', 'At-Takwir', 29],
  ['الإنفطار', 'Al-Infitar', 19], ['المطففين', 'Al-Mutaffifin', 36], ['الإنشقاق', 'Al-Inshiqaq', 25],
  ['البروج', 'Al-Buruj', 22], ['الطارق', 'At-Tariq', 17], ['الأعلى', "Al-A'la", 19],
  ['الغاشية', 'Al-Ghashiyah', 26], ['الفجر', 'Al-Fajr', 30], ['البلد', 'Al-Balad', 20],
  ['الشمس', 'Ash-Shams', 15], ['الليل', 'Al-Layl', 21], ['الضحى', 'Ad-Duhaa', 11],
  ['الشرح', 'Ash-Sharh', 8], ['التين', 'At-Tin', 8], ['العلق', "Al-'Alaq", 19],
  ['القدر', 'Al-Qadr', 5], ['البينة', 'Al-Bayyinah', 8], ['الزلزلة', 'Az-Zalzalah', 8],
  ['العاديات', "Al-'Adiyat", 11], ['القارعة', "Al-Qari'ah", 11], ['التكاثر', 'At-Takathur', 8],
  ['العصر', "Al-'Asr", 3], ['الهمزة', 'Al-Humazah', 9], ['الفيل', 'Al-Fil', 5],
  ['قريش', 'Quraysh', 4], ['الماعون', "Al-Ma'un", 7], ['الكوثر', 'Al-Kawthar', 3],
  ['الكافرون', 'Al-Kafirun', 6], ['النصر', 'An-Nasr', 3], ['المسد', 'Al-Masad', 5],
  ['الإخلاص', 'Al-Ikhlas', 4], ['الفلق', 'Al-Falaq', 5], ['الناس', 'An-Nas', 6],
];

export interface SurahInfo {
  n: number;
  ar: string;
  en: string;
  ayahs: number;
}

export const SURAHS: SurahInfo[] = TABLE.map(([ar, en, ayahs], i) => ({
  n: i + 1,
  ar,
  en,
  ayahs,
}));

export const hasBasmala = (n: number) => n !== 1 && n !== 9;

export const normalizeArabic = (s: string) =>
  s
    .replace(/[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED\u08D3-\u08FF\u0640]/g, '')
    .replace(/[أإآٱ]/g, 'ا')
    .replace(/ى/g, 'ي')
    .replace(/ة/g, 'ه')
    .replace(/ؤ/g, 'و')
    .replace(/ئ/g, 'ي')
    .replace(/\s+/g, ' ')
    .trim();

export const joinTanwin = (s: string) => s.replace(/([\u06F0-\u06F2])\s+(?=[اى])/g, '$1');

const ARTICLE = new Set(['al', 'an', 'ad', 'at', 'as', 'ash', 'az', 'ar', 'adh', 'ath', 'el']);

export const latinWords = (s: string) =>
  s
    .normalize('NFKD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/['’ʿʾ`]/g, '')
    .replace(/ee/g, 'i')
    .replace(/oo|ou/g, 'u')
    .replace(/y/g, 'i')
    .replace(/[^a-z]+/g, ' ')
    .split(' ')
    .filter((w) => w && !ARTICLE.has(w))
    .map((w) => w.replace(/(.)\1+/g, '$1').replace(/h$/, ''));

function oneEdit(a: string, b: string): boolean {
  if (Math.abs(a.length - b.length) > 1) return false;
  let i = 0;
  while (i < a.length && i < b.length && a[i] === b[i]) i++;
  const x = a.slice(i + 1);
  const y = b.slice(i + 1);
  return x === y || a.slice(i) === y || a.slice(i + 1) === b.slice(i);
}

const AR_ALIASES: Array<[string, number]> = [
  ['الانشراح', 94],
  ['اللهب', 111],
  ['الدهر', 76],
  ['بني اسراييل', 17],
  ['المومن', 40],
  ['براءه', 9],
];
const LAT_ALIASES: Array<[string, number]> = [
  ['Lahab', 111],
  ['Inshirah', 94],
  ['Dahr', 76],
  ['Bani Israil', 17],
  ["Al-Mu'min", 40],
  ['Taha', 20],
];

const arNames: Array<[string, number]> = [
  ...SURAHS.map((s): [string, number] => [normalizeArabic(s.ar), s.n]),
  ...AR_ALIASES,
];
const latNames = [...SURAHS.map((s): [string, number] => [s.en, s.n]), ...LAT_ALIASES].map(
  ([en, n]) => ({ n, words: latinWords(en) })
);

const PARTIAL = /ما تيسر|مقتطف|ايات من|excerpt|selection|verses from/;
const QURAN_HINT =
  /quran|koran|mushaf|juz|tilawa|recitation|murata|mujawad|afasi|mishari|sudais|shuraim|ghamdi|minshawi|husari|basit|muaiqli|مصحف|القران|قران|جزء|تلاوه|مرتل|مجود|العفاسي|السديس|الحصري|المنشاوي|عبد الباسط/;

function arabicSurah(text: string, bare: boolean): number | null {
  const s = normalizeArabic(text);
  const lead = s.length - s.replace(/^[\d\s\-_.]+/, '').length;
  let best: [number, number] | null = null;
  for (const [name, n] of arNames) {
    const keys = bare ? [`سوره ${name}`, name] : [`سوره ${name}`];
    for (const key of keys) {
      const found = s.indexOf(key);
      const i = key === name && found !== lead ? -1 : found;
      if (
        i >= 0 &&
        !/[\u0621-\u064A]/.test(s[i + key.length] ?? '') &&
        (!best || name.length > best[1])
      ) {
        best = [n, name.length];
      }
    }
  }
  return best?.[0] ?? null;
}

function latinSurah(text: string, bare: boolean): number | null {
  const words = latinWords(text);
  const at = words.findIndex((w) => w === 'sura' || w === 'surat');
  if (at < 0 && !bare) return null;
  const rest = at < 0 ? words : words.slice(at + 1);
  const line = ` ${rest.join(' ')} `;
  let best: [number, number] | null = null;

  for (const { n, words: name } of latNames) {
    const phrase = name.join(' ');
    const whole = at < 0 ? line.startsWith(` ${phrase} `) : line.includes(` ${phrase} `);
    const glued =
      name.join('').length >= 5 &&
      (at < 0 ? rest[0] === name.join('') : rest.includes(name.join('')));
    const last =
      at >= 0 &&
      name.length > 1 &&
      name[name.length - 1].length >= 4 &&
      rest[0] === name[name.length - 1];
    const len = phrase.length;
    if ((whole || glued || last) && (!best || len > best[1])) best = [n, len];
  }
  if (best) return best[0];
  const first = rest[0] ?? '';
  const near = latNames.filter(
    ({ words: name }) =>
      name.length === 1 && name[0].length >= 4 && first.length >= 4 && oneEdit(first, name[0])
  );
  return near.length === 1 ? near[0].n : null;
}

function juzRange(text: string): number[] | null {
  const ar = normalizeArabic(text);
  const lat = ` ${latinWords(text).join(' ')} `;
  const span = (a: number, b: number) => Array.from({ length: b - a + 1 }, (_, i) => a + i);
  if (/جزء عم(\s|$)/.test(ar) || / juz (ama|30) /.test(lat)) return span(78, 114);
  if (/جزء تبارك/.test(ar) || / juz (tabarak|29) /.test(lat)) return span(67, 77);
  return null;
}

export type Riwayah = 'hafs' | 'shubah';
export interface Recitation {
  surahs: number[];
  riwayah: Riwayah;
  partial: boolean;
}

export interface TrackLike {
  title: string;
  album: string;
  artist: string;
  albumArtist: string;
  path: string;
  trackNo: number | null;
}

export function detectRecitation(t: TrackLike): Recitation | null {
  const file = t.path.split(/[\\/]/).pop()?.replace(/\.[^.]+$/, '') ?? '';
  const context = `${t.album} ${t.artist} ${t.albumArtist} ${t.path}`;
  const hint = QURAN_HINT.test(`${normalizeArabic(context)} ${latinWords(context).join(' ')}`);
  const all = `${t.title} ${t.album} ${file}`;
  const words = latinWords(all).join(' ');
  const riwayah: Riwayah =
    /شعبه/.test(normalizeArabic(all)) || /(^| )(shuba|shouba)( |$)/.test(words)
      ? 'shubah'
      : 'hafs';
  const partial = PARTIAL.test(`${normalizeArabic(all)} ${words}`);

  const whole = juzRange(t.title) ?? juzRange(file);
  if (whole) return { surahs: whole, riwayah, partial };
  for (const text of [t.title, file]) {
    const n = arabicSurah(text, hint) ?? latinSurah(text, hint);
    if (n) return { surahs: [n], riwayah, partial };
  }
  if (/مصحف|mushaf/i.test(normalizeArabic(t.album)) && t.trackNo && t.trackNo >= 1 && t.trackNo <= 114) {
    return { surahs: [t.trackNo], riwayah, partial };
  }
  return null;
}

export type Pause = [number, number];
export interface Unit {
  weight: number;
  flexible?: boolean;
}

export const QURAN_TUNING = { length: 3, pause: 0.7, miss: 1.2 };
const pauseCost = (len: number) => QURAN_TUNING.pause * Math.max(0, 1 - len / 1.0);
const lengthCost = (actual: number, expected: number, flexible?: boolean) =>
  actual <= 0
    ? flexible
      ? 0
      : 50
    : QURAN_TUNING.length * Math.log(actual / expected) ** 2 * (flexible ? 0.3 : 1);

export function alignAyahs(
  units: Unit[],
  pauses: Pause[],
  duration: number,
  anchors: Record<number, number> = {}
): number[] {
  const n = units.length;
  if (!n) return [];
  let from = 0;
  let to = duration;
  const inner: Pause[] = [];
  for (const [s, e] of pauses) {
    if (s <= 0.05) from = Math.max(from, e);
    else if (e >= duration - 0.05) to = Math.min(to, s);
    else inner.push([s, e]);
  }
  const span = Math.max(0.01, to - from);
  const total = units.reduce((acc, u) => acc + u.weight, 0) || 1;
  const expectedLen = units.map((u) => (span * u.weight) / total);
  const expectedStart: number[] = [];
  units.reduce((at, _u, i) => ((expectedStart[i] = at), at + expectedLen[i]), from);

  const cands: Pause[] = [...inner];
  for (const t of Object.values(anchors)) {
    if (!inner.some(([, e]) => Math.abs(e - t) <= 1.5)) cands.push([t, t]);
  }
  cands.sort((a, b) => a[1] - b[1]);
  if (!cands.length || n === 1) return expectedStart.map((x) => Math.round(x * 100) / 100);

  const band = Math.max(20, 0.1 * span);
  const windows: number[][] = [[]];
  for (let b = 1; b < n; b++) {
    const anchor = anchors[b];
    let w: number[];
    if (anchor !== undefined) {
      let best = 0;
      cands.forEach(([, e], j) => Math.abs(e - anchor) < Math.abs(cands[best][1] - anchor) && (best = j));
      w = [best];
    } else {
      w = cands.flatMap(([, e], j) => (Math.abs(e - expectedStart[b]) <= band ? [j] : []));
      if (!w.length) {
        let best = 0;
        cands.forEach(
          ([, e], j) => Math.abs(e - expectedStart[b]) < Math.abs(cands[best][1] - expectedStart[b]) && (best = j)
        );
        w = [best];
      }
      if (units.slice(0, b).every((x) => x.flexible)) w = [-1, ...w];
    }
    windows.push(w);
  }

  const MISS = QURAN_TUNING.miss;
  const startOf = (j: number) => (j < 0 ? from : cands[j][1]);
  const sumLen = [0];
  const sumFixed = [0];
  const sumTapped = [0];
  for (let i = 0; i < n; i++) {
    sumLen.push(sumLen[i] + expectedLen[i]);
    sumFixed.push(sumFixed[i] + (units[i].flexible ? 0 : 1));
    sumTapped.push(sumTapped[i] + (anchors[i] !== undefined ? 1 : 0));
  }
  const share = (a: number, b: number) => sumLen[b] - sumLen[a];
  const optional = (a: number, b: number) => sumFixed[b] === sumFixed[a];
  windows[0] = [-1];
  const dp: Float64Array[] = [Float64Array.of(0)];
  const back: Array<Array<{ k: number; step: number }>> = [[]];

  for (let b = 1; b < n; b++) {
    const w = windows[b];
    dp[b] = new Float64Array(w.length).fill(Infinity);
    back[b] = [];
    for (let jj = 0; jj < w.length; jj++) {
      const j = w[jj];
      const end = j < 0 ? from : cands[j][0];
      const pc = j < 0 ? 0 : pauseCost(cands[j][1] - cands[j][0]);
      let best = Infinity;
      let arg = { k: -1, step: 1 };
      for (let step = 1; step <= Math.min(3, b); step++) {
        const a = b - step;
        if (step > 1 && sumTapped[b] - sumTapped[a + 1] > 0) break;
        const expected = share(a, b);
        const opt = optional(a, b);
        const prev = windows[a];
        const prevCost = dp[a];
        for (let k = 0; k < prev.length; k++) {
          const i = prev[k];
          const zero = i === j && step === 1 && units[a].flexible;
          if (!(i < j || zero)) continue;
          const before = prevCost[k];
          if (before === Infinity) continue;
          const c =
            before +
            (zero ? 0 : lengthCost(end - startOf(i), expected, opt) + pc) +
            MISS * (step - 1);
          if (c < best) {
            best = c;
            arg = { k, step };
          }
        }
      }
      dp[b][jj] = best;
      back[b].push(arg);
    }
  }

  let bestTotal = Infinity;
  let last = { b: 0, k: 0 };
  for (let a = n - 1; a >= Math.max(0, n - 3); a--) {
    if (a < n - 1 && sumTapped[n] - sumTapped[a + 1] > 0) break;
    windows[a].forEach((i, k) => {
      const c =
        dp[a][k] +
        lengthCost(to - startOf(i), share(a, n), optional(a, n)) +
        MISS * (n - 1 - a);
      if (c < bestTotal) {
        bestTotal = c;
        last = { b: a, k };
      }
    });
  }
  if (!Number.isFinite(bestTotal)) return expectedStart.map((x) => Math.round(x * 100) / 100);

  const starts = new Array<number>(n);
  const fill = (a: number, b: number, end: number) => {
    const t0 = starts[a];
    for (let x = a + 1; x < b; x++) starts[x] = t0 + ((end - t0) * share(a, x)) / share(a, b);
  };
  let { b: cur, k } = last;
  starts[cur] = startOf(windows[cur][k]);
  fill(cur, n, to);
  while (cur > 0) {
    const { k: pk, step } = back[cur][k];
    const prev = cur - step;
    starts[prev] = startOf(windows[prev][pk]);
    const j = windows[cur][k];
    fill(prev, cur, j < 0 ? from : cands[j][0]);
    cur = prev;
    k = pk;
  }
  return starts.map((x) => Math.round(x * 100) / 100);
}
