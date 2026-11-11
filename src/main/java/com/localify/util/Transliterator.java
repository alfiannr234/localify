package com.localify.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Automatically romanizes Asian scripts (Korean Hangul, Japanese Kana & Kanji, Chinese)
 * into readable Latin alphabet words preserving spoken pronunciation ("pembacaan katanya sama").
 */
public final class Transliterator {

    private Transliterator() {}

    // Korean Revised Romanization tables
    private static final String[] HANGUL_INITIALS = {
            "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    };

    private static final String[] HANGUL_VOWELS = {
            "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    };

    private static final String[] HANGUL_FINALS = {
            "", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lg", "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "bs", "s", "ss", "ng", "j", "ch", "k", "t", "p", "h"
    };

    // Common Asian phrases and Kanji -> Romaji / Spoken pronunciation
    private static final Map<String, String> WORD_MAP = new LinkedHashMap<>();

    // Japanese Kana mappings
    private static final Map<String, String> KANA_COMBO_MAP = new HashMap<>();
    private static final Map<Character, String> KANA_SINGLE_MAP = new HashMap<>();

    static {
        // High-frequency phrases, titles & song lyrics
        WORD_MAP.put("鬼滅の刃", "Kimetsu no Yaiba");
        WORD_MAP.put("すずめの戸締まり", "Suzume no Tojimari");
        WORD_MAP.put("凛として時雨", "Ling tosite sigure");
        WORD_MAP.put("廻廻奇譚", "Kaikai Kitan");
        WORD_MAP.put("残響散歌", "Zankyou Sanka");
        WORD_MAP.put("いきものがかり", "Ikimonogakari");
        WORD_MAP.put("ブルーバード", "Blue Bird");
        WORD_MAP.put("主題歌", "Shudaika");
        WORD_MAP.put("炎", "Homura");
        WORD_MAP.put("怪獣", "Kaijuu");
        WORD_MAP.put("紅蓮華", "Gurenge");
        WORD_MAP.put("夜に駆ける", "Yoru ni Kakeru");
        WORD_MAP.put("新時代", "Shin Jidai");
        WORD_MAP.put("竈門炭治郎のうた", "Kamado Tanjiro no Uta");
        WORD_MAP.put("竈門炭治郎", "Kamado Tanjiro");
        WORD_MAP.put("うた", "Uta");
        WORD_MAP.put("サカナクション", "sakanaction");
        WORD_MAP.put("戸締まり", "Tojimari");
        WORD_MAP.put("すずめ", "Suzume");

        // Common anime song lines
        WORD_MAP.put("飛翔いたら", "habataitara");
        WORD_MAP.put("飛翔たいたら", "habataitara");
        WORD_MAP.put("羽ばたいたら", "habataitara");
        WORD_MAP.put("戻らないと言って", "modoranai to itte");
        WORD_MAP.put("戻らない", "modoranai");
        WORD_MAP.put("目指したのは", "mezashita no wa");
        WORD_MAP.put("蒼い", "aoi");
        WORD_MAP.put("あの空", "ano sora");
        WORD_MAP.put("有象無象", "uzou muzou");
        WORD_MAP.put("人の成り", "hito no nari");
        WORD_MAP.put("虚勢", "kyosei");
        WORD_MAP.put("心象", "shinshou");
        WORD_MAP.put("人外", "jingai");
        WORD_MAP.put("誰の声も", "dare no koe mo");
        WORD_MAP.put("届かない", "todokanai");
        WORD_MAP.put("深い夜", "fukai yoru");
        WORD_MAP.put("鮮やかに", "azayaka ni");
        WORD_MAP.put("咲き誇る", "sakihokoru");
        WORD_MAP.put("散りゆく", "chiriyuku");
        WORD_MAP.put("教えて", "oshiete");
        WORD_MAP.put("僕の中に", "boku no naka ni");
        WORD_MAP.put("誰がいるの", "dare ga iru no");
        WORD_MAP.put("壊れた", "kowareta");
        WORD_MAP.put("この世界で", "kono sekai de");
        WORD_MAP.put("君が笑う", "kimi ga warau");
        WORD_MAP.put("何も見えずに", "nanimo miezu ni");
        WORD_MAP.put("何度でも叫ぶ", "nandodemo sakebu");
        WORD_MAP.put("この暗い夜の", "kono kurai yoru no");
        WORD_MAP.put("君の中にある", "kimi no naka ni aru");
        WORD_MAP.put("赤と青き線", "aka to aoki sen");
        WORD_MAP.put("風の中でも", "kaze no naka demo");
        WORD_MAP.put("負けないような声で", "makenai you na koe de");
        WORD_MAP.put("さよなら", "sayonara");
        WORD_MAP.put("ありがとう", "arigatou");
        WORD_MAP.put("声の限り", "koe no kagiri");
        WORD_MAP.put("悲しみより", "kanashimi yori");
        WORD_MAP.put("去りゆく", "sariyuku");
        WORD_MAP.put("温もりと", "nukumori to");

        // High frequency Kanji in lyrics
        WORD_MAP.put("僕", "boku");
        WORD_MAP.put("君", "kimi");
        WORD_MAP.put("私", "watashi");
        WORD_MAP.put("貴方", "anata");
        WORD_MAP.put("世界", "sekai");
        WORD_MAP.put("心", "kokoro");
        WORD_MAP.put("愛", "ai");
        WORD_MAP.put("夢", "yume");
        WORD_MAP.put("空", "sora");
        WORD_MAP.put("夜", "yoru");
        WORD_MAP.put("今", "ima");
        WORD_MAP.put("声", "koe");
        WORD_MAP.put("涙", "namida");
        WORD_MAP.put("道", "michi");
        WORD_MAP.put("光", "hikari");
        WORD_MAP.put("影", "kage");
        WORD_MAP.put("時", "toki");
        WORD_MAP.put("日", "hi");
        WORD_MAP.put("月", "tsuki");
        WORD_MAP.put("星", "hoshi");
        WORD_MAP.put("手", "te");
        WORD_MAP.put("目", "me");
        WORD_MAP.put("花", "hana");
        WORD_MAP.put("風", "kaze");
        WORD_MAP.put("雨", "ame");
        WORD_MAP.put("海", "umi");
        WORD_MAP.put("青", "ao");
        WORD_MAP.put("白", "shiro");
        WORD_MAP.put("黒", "kuro");
        WORD_MAP.put("赤", "aka");
        WORD_MAP.put("歌", "uta");
        WORD_MAP.put("音", "oto");
        WORD_MAP.put("神", "kami");
        WORD_MAP.put("人", "hito");
        WORD_MAP.put("命", "inochi");
        WORD_MAP.put("未来", "mirai");
        WORD_MAP.put("過去", "kako");
        WORD_MAP.put("明日", "ashita");
        WORD_MAP.put("昨日", "kinou");
        WORD_MAP.put("笑顔", "egao");
        WORD_MAP.put("記憶", "kioku");
        WORD_MAP.put("言葉", "kotoba");
        WORD_MAP.put("約束", "yakusoku");
        WORD_MAP.put("永遠", "eien");
        WORD_MAP.put("自由", "jiyuu");
        WORD_MAP.put("痛み", "itami");
        WORD_MAP.put("温もり", "nukumori");
        WORD_MAP.put("背中", "senaka");
        WORD_MAP.put("悲しみ", "kanashimi");
        WORD_MAP.put("切なさ", "setsunasa");
        WORD_MAP.put("大切", "daisetsu");
        WORD_MAP.put("運命", "unmei");
        WORD_MAP.put("真実", "shinjitsu");
        WORD_MAP.put("想い", "omoi");
        WORD_MAP.put("希望", "kibou");
        WORD_MAP.put("孤独", "kodoku");
        WORD_MAP.put("暗闇", "kurayami");
        WORD_MAP.put("何", "nani");
        WORD_MAP.put("誰", "dare");
        WORD_MAP.put("何故", "naze");
        WORD_MAP.put("魂", "tamashii");
        WORD_MAP.put("朝", "asa");
        WORD_MAP.put("深", "fuka");
        WORD_MAP.put("笑う", "warau");
        WORD_MAP.put("生", "sei");
        WORD_MAP.put("死", "shi");

        // Chinese common greetings / words
        WORD_MAP.put("你好", "ni hao");
        WORD_MAP.put("谢谢", "xie xie");
        WORD_MAP.put("再见", "zai jian");
        WORD_MAP.put("加油", "jia you");
        WORD_MAP.put("中国", "zhong guo");
        WORD_MAP.put("我爱你", "wo ai ni");

        initKanaCombos();
        initKanaSingles();
    }

    private static void initKanaCombos() {
        KANA_COMBO_MAP.put("きゃ", "kya"); KANA_COMBO_MAP.put("きゅ", "kyu"); KANA_COMBO_MAP.put("きょ", "kyo");
        KANA_COMBO_MAP.put("しゃ", "sha"); KANA_COMBO_MAP.put("しゅ", "shu"); KANA_COMBO_MAP.put("しょ", "sho");
        KANA_COMBO_MAP.put("ちゃ", "cha"); KANA_COMBO_MAP.put("ちゅ", "chu"); KANA_COMBO_MAP.put("ちょ", "cho");
        KANA_COMBO_MAP.put("にゃ", "nya"); KANA_COMBO_MAP.put("にゅ", "nyu"); KANA_COMBO_MAP.put("にょ", "nyo");
        KANA_COMBO_MAP.put("ひゃ", "hya"); KANA_COMBO_MAP.put("ひゅ", "hyu"); KANA_COMBO_MAP.put("ひょ", "hyo");
        KANA_COMBO_MAP.put("みゃ", "mya"); KANA_COMBO_MAP.put("みゅ", "myu"); KANA_COMBO_MAP.put("みょ", "myo");
        KANA_COMBO_MAP.put("りゃ", "rya"); KANA_COMBO_MAP.put("りゅ", "ryu"); KANA_COMBO_MAP.put("りょ", "ryo");
        KANA_COMBO_MAP.put("ぎゃ", "gya"); KANA_COMBO_MAP.put("ぎゅ", "gyu"); KANA_COMBO_MAP.put("ぎょ", "gyo");
        KANA_COMBO_MAP.put("じゃ", "ja");  KANA_COMBO_MAP.put("じゅ", "ju");  KANA_COMBO_MAP.put("じょ", "jo");
        KANA_COMBO_MAP.put("びゃ", "bya"); KANA_COMBO_MAP.put("びゅ", "byu"); KANA_COMBO_MAP.put("びょ", "byo");
        KANA_COMBO_MAP.put("ぴゃ", "pya"); KANA_COMBO_MAP.put("ぴゅ", "pyu"); KANA_COMBO_MAP.put("ぴょ", "pyo");

        KANA_COMBO_MAP.put("キャ", "kya"); KANA_COMBO_MAP.put("キュ", "kyu"); KANA_COMBO_MAP.put("キョ", "kyo");
        KANA_COMBO_MAP.put("シャ", "sha"); KANA_COMBO_MAP.put("シュ", "shu"); KANA_COMBO_MAP.put("ショ", "sho");
        KANA_COMBO_MAP.put("チャ", "cha"); KANA_COMBO_MAP.put("チュ", "chu"); KANA_COMBO_MAP.put("チョ", "cho");
        KANA_COMBO_MAP.put("ニャ", "nya"); KANA_COMBO_MAP.put("ニュ", "nyu"); KANA_COMBO_MAP.put("ニョ", "nyo");
        KANA_COMBO_MAP.put("ヒャ", "hya"); KANA_COMBO_MAP.put("ヒュ", "hyu"); KANA_COMBO_MAP.put("ヒョ", "hyo");
        KANA_COMBO_MAP.put("ミャ", "mya"); KANA_COMBO_MAP.put("ミュ", "myu"); KANA_COMBO_MAP.put("ミョ", "myo");
        KANA_COMBO_MAP.put("リャ", "rya"); KANA_COMBO_MAP.put("リュ", "ryu"); KANA_COMBO_MAP.put("リョ", "ryo");
        KANA_COMBO_MAP.put("ギャ", "gya"); KANA_COMBO_MAP.put("ギュ", "gyu"); KANA_COMBO_MAP.put("ギョ", "gyo");
        KANA_COMBO_MAP.put("ジャ", "ja");  KANA_COMBO_MAP.put("ジュ", "ju");  KANA_COMBO_MAP.put("ジョ", "jo");
        KANA_COMBO_MAP.put("ビャ", "bya"); KANA_COMBO_MAP.put("ビュ", "byu"); KANA_COMBO_MAP.put("ビョ", "byo");
        KANA_COMBO_MAP.put("ピャ", "pya"); KANA_COMBO_MAP.put("ピュ", "pyu"); KANA_COMBO_MAP.put("ピョ", "pyo");

        KANA_COMBO_MAP.put("ファ", "fa"); KANA_COMBO_MAP.put("フィ", "fi"); KANA_COMBO_MAP.put("フェ", "fe"); KANA_COMBO_MAP.put("フォ", "fo");
        KANA_COMBO_MAP.put("ティ", "ti"); KANA_COMBO_MAP.put("ディ", "di"); KANA_COMBO_MAP.put("トゥ", "tu"); KANA_COMBO_MAP.put("ドゥ", "du");
        KANA_COMBO_MAP.put("ウィ", "wi"); KANA_COMBO_MAP.put("ウェ", "we"); KANA_COMBO_MAP.put("ウォ", "wo");
        KANA_COMBO_MAP.put("ヴァ", "va"); KANA_COMBO_MAP.put("ヴィ", "vi"); KANA_COMBO_MAP.put("ヴェ", "ve"); KANA_COMBO_MAP.put("ヴォ", "vo");
        KANA_COMBO_MAP.put("チェ", "che"); KANA_COMBO_MAP.put("シェ", "she"); KANA_COMBO_MAP.put("ジェ", "je");
    }

    private static void initKanaSingles() {
        char[] hira = {
                'あ','い','う','え','お',
                'か','き','く','け','こ',
                'さ','し','す','せ','そ',
                'た','ち','つ','て','と',
                'な','に','ぬ','ね','の',
                'は','ひ','ふ','へ','ほ',
                'ま','み','む','め','も',
                'や','ゆ','よ',
                'ら','り','る','れ','ろ',
                'わ','ゐ','ゑ','を','ん',
                'が','ぎ','ぐ','げ','ご',
                'ざ','じ','ず','ぜ','ぞ',
                'だ','ぢ','づ','で','ど',
                'ば','び','ぶ','べ','ぼ',
                'ぱ','ぴ','ぷ','ぺ','ぽ',
                'ぁ','ぃ','ぅ','ぇ','ぉ',
                'ゃ','ゅ','ょ','ゎ'
        };
        String[] hiraRomaji = {
                "a","i","u","e","o",
                "ka","ki","ku","ke","ko",
                "sa","shi","su","se","so",
                "ta","chi","tsu","te","to",
                "na","ni","nu","ne","no",
                "ha","hi","fu","he","ho",
                "ma","mi","mu","me","mo",
                "ya","yu","yo",
                "ra","ri","ru","re","ro",
                "wa","wi","we","wo","n",
                "ga","gi","gu","ge","go",
                "za","ji","zu","ze","zo",
                "da","ji","zu","de","do",
                "ba","bi","bu","be","bo",
                "pa","pi","pu","pe","po",
                "a","i","u","e","o",
                "ya","yu","yo","wa"
        };
        for (int i = 0; i < hira.length; i++) {
            KANA_SINGLE_MAP.put(hira[i], hiraRomaji[i]);
        }

        char[] kata = {
                'ア','イ','ウ','エ','オ',
                'カ','キ','ク','ケ','コ',
                'サ','シ','ス','セ','ソ',
                'タ','チ','ツ','テ','ト',
                'ナ','ニ','ヌ','ネ','ノ',
                'ハ','ヒ','フ','ヘ','ホ',
                'マ','ミ','ム','メ','モ',
                'ヤ','ユ','ヨ',
                'ラ','リ','ル','レ','ロ',
                'ワ','ヰ','ヱ','ヲ','ン',
                'ガ','ギ','グ','ゲ','ゴ',
                'ザ','ジ','ズ','ゼ','ゾ',
                'ダ','ヂ','ヅ','デ','ド',
                'バ','ビ','ブ','ベ','ボ',
                'パ','ピ','プ','ペ','ポ',
                'ァ','ィ','ゥ','ェ','ォ',
                'ャ','ュ','ョ','ヮ','ヴ'
        };
        String[] kataRomaji = {
                "a","i","u","e","o",
                "ka","ki","ku","ke","ko",
                "sa","shi","su","se","so",
                "ta","chi","tsu","te","to",
                "na","ni","nu","ne","no",
                "ha","hi","fu","he","ho",
                "ma","mi","mu","me","mo",
                "ya","yu","yo",
                "ra","ri","ru","re","ro",
                "wa","wi","we","wo","n",
                "ga","gi","gu","ge","go",
                "za","ji","zu","ze","zo",
                "da","ji","zu","de","do",
                "ba","bi","bu","be","bo",
                "pa","pi","pu","pe","po",
                "a","i","u","e","o",
                "ya","yu","yo","wa","vu"
        };
        for (int i = 0; i < kata.length; i++) {
            KANA_SINGLE_MAP.put(kata[i], kataRomaji[i]);
        }

        char[] hw = {
                'ｱ','ｲ','ｳ','ｴ','ｵ',
                'ｶ','ｷ','ｸ','ｹ','ｺ',
                'ｻ','ｼ','ｽ','ｾ','ｿ',
                'ﾀ','ﾁ','ﾂ','ﾃ','ﾄ',
                'ﾅ','ﾆ','ﾇ','ﾈ','ﾉ',
                'ﾊ','ﾋ','ﾌ','ﾍ','ﾎ',
                'ﾏ','ﾐ','ﾑ','ﾒ','ﾓ',
                'ﾔ','ﾕ','ﾖ',
                'ﾗ','ﾘ','ﾙ','ﾚ','ﾛ',
                'ﾜ','ﾝ','ｧ','ｨ','ｩ','ｪ','ｫ','ｬ','ｭ','ｮ','ｯ'
        };
        String[] hwRomaji = {
                "a","i","u","e","o",
                "ka","ki","ku","ke","ko",
                "sa","shi","su","se","so",
                "ta","chi","tsu","te","to",
                "na","ni","nu","ne","no",
                "ha","hi","fu","he","ho",
                "ma","mi","mu","me","mo",
                "ya","yu","yo",
                "ra","ri","ru","re","ro",
                "wa","n","a","i","u","e","o","ya","yu","yo","tsu"
        };
        for (int i = 0; i < hw.length; i++) {
            KANA_SINGLE_MAP.put(hw[i], hwRomaji[i]);
        }
    }

    public static boolean isAsianChar(char c) {
        return (c >= 0xAC00 && c <= 0xD7A3) // Hangul syllables
                || (c >= 0x1100 && c <= 0x11FF) // Hangul Jamo
                || (c >= 0x3130 && c <= 0x318F) // Hangul Compatibility Jamo
                || (c >= 0x3040 && c <= 0x309F) // Hiragana
                || (c >= 0x30A0 && c <= 0x30FF) // Katakana
                || (c >= 0xFF65 && c <= 0xFF9F) // Halfwidth Katakana
                || (c >= 0x4E00 && c <= 0x9FFF) // CJK Unified Ideographs (Kanji/Hanzi)
                || (c >= 0x3400 && c <= 0x4DBF); // CJK Extension A
    }

    public static boolean isAsianPunctuation(char c) {
        return c == '「' || c == '」' || c == '『' || c == '』'
                || c == '（' || c == '）' || c == '【' || c == '】'
                || c == '《' || c == '》' || c == '、' || c == '。'
                || c == '〜' || c == '～' || c == '・' || c == '　';
    }

    public static boolean containsAsianCharacters(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            if (isAsianChar(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsAsianCharactersOrMarks(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isAsianChar(c) || isAsianPunctuation(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Primary transliteration entry point.
     * Converts any Korean, Japanese Kana/Kanji, or Chinese text into standard Latin alphabet words.
     */
    public static String romanize(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }

        if (!containsAsianCharactersOrMarks(text)) {
            return text;
        }

        String result = text;

        // 1. Replace Japanese / Asian typography punctuation with Latin equivalents
        result = result
                .replace('「', '"').replace('」', '"')
                .replace('『', '"').replace('』', '"')
                .replace('（', '(').replace('）', ')')
                .replace('【', '[').replace('】', ']')
                .replace('《', '<').replace('》', '>')
                .replace('、', ',')
                .replace('。', '.')
                .replace('〜', '~').replace('～', '~')
                .replace('・', ' ')
                .replace('　', ' ');

        // 2. High-frequency word and phrase substitutions
        for (Map.Entry<String, String> entry : WORD_MAP.entrySet()) {
            if (result.contains(entry.getKey())) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }

        if (!containsAsianCharacters(result)) {
            return cleanWhitespace(result);
        }

        // 3. Syllable-by-syllable & Kana parsing
        StringBuilder sb = new StringBuilder(result.length() * 2);
        int len = result.length();

        for (int i = 0; i < len; i++) {
            char c = result.charAt(i);

            // Check 2-character Japanese Kana combos
            if (i + 1 < len) {
                String pair = result.substring(i, i + 2);
                String comboRomaji = KANA_COMBO_MAP.get(pair);
                if (comboRomaji != null) {
                    sb.append(comboRomaji);
                    i++;
                    continue;
                }
            }

            // Sokuon (small tsu: っ / ッ)
            if (c == 'っ' || c == 'ッ') {
                if (i + 1 < len) {
                    char next = result.charAt(i + 1);
                    String nextRomaji = getKanaRomaji(next);
                    if (nextRomaji != null && !nextRomaji.isEmpty()) {
                        char firstChar = nextRomaji.charAt(0);
                        if (nextRomaji.startsWith("ch")) {
                            sb.append('t');
                        } else {
                            sb.append(firstChar);
                        }
                        continue;
                    }
                }
                sb.append('\'');
                continue;
            }

            // Chōonpu (ー): prolong vowel or hyphen
            if (c == 'ー') {
                if (sb.length() > 0) {
                    char last = sb.charAt(sb.length() - 1);
                    if ("aeiou".indexOf(Character.toLowerCase(last)) >= 0) {
                        sb.append(last);
                        continue;
                    }
                }
                sb.append('-');
                continue;
            }

            // Single Kana
            String kanaRomaji = KANA_SINGLE_MAP.get(c);
            if (kanaRomaji != null) {
                sb.append(kanaRomaji);
                continue;
            }

            // Korean Hangul Syllables (0xAC00 to 0xD7A3)
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int code = c - 0xAC00;
                int initial = code / 588;
                int vowel = (code % 588) / 28;
                int finalConsonant = code % 28;

                sb.append(HANGUL_INITIALS[initial]);
                sb.append(HANGUL_VOWELS[vowel]);
                sb.append(HANGUL_FINALS[finalConsonant]);
                continue;
            }

            // Korean Compatibility Jamo (0x3131 to 0x318F)
            if (c >= 0x3131 && c <= 0x3163) {
                sb.append(jamoToLatin(c));
                continue;
            }

            // Default: preserve char
            sb.append(c);
        }

        return cleanWhitespace(sb.toString());
    }

    private static String getKanaRomaji(char c) {
        return KANA_SINGLE_MAP.get(c);
    }

    private static String jamoToLatin(char c) {
        return switch (c) {
            case 'ㄱ' -> "g";
            case 'ㄲ' -> "kk";
            case 'ㄳ' -> "gs";
            case 'ㄴ' -> "n";
            case 'ㄵ' -> "nj";
            case 'ㄶ' -> "nh";
            case 'ㄷ' -> "d";
            case 'ㄸ' -> "dd";
            case 'ㄹ' -> "r";
            case 'ㄺ' -> "lg";
            case 'ㄻ' -> "lm";
            case 'ㄼ' -> "lb";
            case 'ㄽ' -> "ls";
            case 'ㄾ' -> "lt";
            case 'ㄿ' -> "lp";
            case 'ㅀ' -> "lh";
            case 'ㅁ' -> "m";
            case 'ㅂ' -> "b";
            case 'ㅃ' -> "bb";
            case 'ㅄ' -> "bs";
            case 'ㅅ' -> "s";
            case 'ㅆ' -> "ss";
            case 'ㅇ' -> "ng";
            case 'ㅈ' -> "j";
            case 'ㅉ' -> "jj";
            case 'ㅊ' -> "ch";
            case 'ㅋ' -> "k";
            case 'ㅌ' -> "t";
            case 'ㅍ' -> "p";
            case 'ㅎ' -> "h";
            case 'ㅏ' -> "a";
            case 'ㅐ' -> "ae";
            case 'ㅑ' -> "ya";
            case 'ㅒ' -> "yae";
            case 'ㅓ' -> "eo";
            case 'ㅔ' -> "e";
            case 'ㅕ' -> "yeo";
            case 'ㅖ' -> "ye";
            case 'ㅗ' -> "o";
            case 'ㅘ' -> "wa";
            case 'ㅙ' -> "wae";
            case 'ㅚ' -> "oe";
            case 'ㅛ' -> "yo";
            case 'ㅜ' -> "u";
            case 'ㅝ' -> "wo";
            case 'ㅞ' -> "we";
            case 'ㅟ' -> "wi";
            case 'ㅠ' -> "yu";
            case 'ㅡ' -> "eu";
            case 'ㅢ' -> "ui";
            case 'ㅣ' -> "i";
            default -> String.valueOf(c);
        };
    }

    private static String cleanWhitespace(String s) {
        return s.replaceAll(" +", " ").trim();
    }
}
