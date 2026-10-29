// Text processors of the app's own, which build.mjs adds to languages Yomitan gives none or too few of
// (addedPreprocessors).

/**
 * Text composed as dictionaries write their headwords (NFC): OCR and keyboards may give a vowel sign in two parts
 * (Bengali ো as ে + া) or a letter with nukta as one character (क़ U+0958, which NFC writes as क + ़).
 * @type {import('language').TextProcessor}
 */
export const composeCharacters = {
    name: 'Compose characters',
    description: 'ে + া → ো, क़ → क + ़',
    process: (str) => [str, str.normalize('NFC')],
};

/**
 * Hindi text with candrabindu where dictionaries write anusvara (हैँ for हैं). The other way round, the import keys
 * headwords with candrabindu under anusvara as well (`HeadwordMarks.replacements`).
 * @type {import('language').TextProcessor}
 */
export const candrabinduAsAnusvara = {
    name: 'Candrabindu as anusvara',
    description: 'हैँ → हैं',
    process: (str) => [str, str.replace(/\u0901/g, '\u0902')],
};

/**
 * Armenian emphasis, exclamation and question marks, written on the stressed vowel inside a word (ինչո՞ւ for ինչու,
 * կարդա՛): headwords are written without them. The import keys Wiktionary's forms written with them without them too.
 * @type {import('language').TextProcessor}
 */
export const removeArmenianMarks = {
    name: 'Remove Armenian marks',
    description: 'ինչո՞ւ → ինչու',
    process: (str) => [str, str.replace(/[\u055B\u055C\u055E]/g, '')],
};

/**
 * The Armenian ligature և, which dictionaries write for ե + վ inside a word, written out as եվ (capitals have no
 * ligature, so ԵՐԵՎԱՆ lowercases to երեվան) or in the classical spelling եւ. The text as written stays, for words
 * written with եվ (Եվրոպա).
 * @type {import('language').TextProcessor}
 */
export const armenianLigatureEv = {
    name: 'Armenian ligature ev',
    description: 'երեվան → երևան',
    process: (str) => [str, str.replace(/ե[վւ]/g, 'և')],
};

/**
 * Thai text in the order headwords are written: a tone mark or thanthakhat given before the vowel above its letter
 * (ก + ่ + ี for กี่), which looks the same as the headword and some typed text has.
 * @type {import('language').TextProcessor}
 */
export const thaiCharacterOrder = {
    name: 'Thai character order',
    description: 'ก่ี → กี่',
    process: (str) => [str, str.replace(/([\u0E48-\u0E4C])([\u0E31\u0E34-\u0E37])/g, '$2$1')],
};

/**
 * Thai sara am ำ, which on-device recognition gives as nikhahit + sara aa (ํ + า), also with the tone mark between them
 * (น + ํ + ้ + า for น้ำ). For words from OCR only (`ocrOnly`, glue.js): typed text almost never has the two parts.
 * @type {import('language').TextProcessor & {ocrOnly: true}}
 */
export const thaiSaraAmInTwoParts = {
    name: 'Sara am in two parts',
    description: 'ํา → ำ',
    ocrOnly: true,
    process: (str) => {
        const fixed = str.replace(/\u0E4D([\u0E48-\u0E4B]?)\u0E32/g, '$1\u0E33');
        return fixed === str ? [str] : [str, fixed];
    },
};

/**
 * The Thai repetition mark ๆ (mai yamok) written right after its word (เด็กๆ, งูๆปลาๆ), where dictionaries write it
 * apart, with a space before it and before the next word (เด็ก ๆ, งู ๆ ปลา ๆ); and the other way round. No space is
 * added after a ๆ that ends the text, so a match never takes in the space after ๆ (เด็กๆ ชอบ matches เด็กๆ).
 * @type {import('language').TextProcessor}
 */
export const thaiMaiYamokSpace = {
    name: 'Thai mai yamok space',
    description: 'เด็กๆ → เด็ก ๆ',
    process: (str) => [
        str,
        str.replace(/ *\u0E46 *(?=\S)/g, ' \u0E46 ').replace(/ *\u0E46$/, ' \u0E46').trimStart(),
        str.replace(/ *\u0E46/g, '\u0E46'),
    ],
};

/**
 * Khmer text with the zero-width spaces apps put between words, which headwords never have: they are dropped, except
 * one that ends the text, so a match never takes it in.
 * @type {import('language').TextProcessor}
 */
export const khmerZeroWidthSpace = {
    name: 'Khmer zero-width space',
    description: 'ខ្ញុំ + U+200B + ទៅ → ខ្ញុំទៅ',
    process: (str) => [str, str.replace(/\u200B+(?!$)/g, '')],
};

/**
 * Khmer subscript TA ្ត read as subscript DA ្ដ, which looks the same; dictionaries write either, and both in some
 * words (ក្ដារ, ក្តារ). The import indexes headwords written with ្ត under ្ដ as well.
 * @type {import('language').TextProcessor}
 */
export const khmerSubscriptDa = {
    name: 'Khmer subscript DA',
    description: 'ក្តារ → ក្ដារ',
    process: (str) => [str, str.replace(/\u17D2\u178F/g, '\u17D2\u178A')],
};

/**
 * Arabic taa marbuta ة at the end of a word written as ha ه, as running text often does the other way round (لغه for
 * لغة): the import keys headwords with ة, final ى and hamza alefs under ه, ي and a bare alif as well (ArabicSupport's
 * `HeadwordMarks`), and text compared with those keys needs ه and ي too (Yomitan gives final ى as ي).
 * @type {import('language').TextProcessor}
 */
export const arabicTaMarbutaAsHa = {
    name: 'Taa marbuta as ha',
    description: 'مدرسة → مدرسه',
    process: (str) => [str, str.replace(/\u0629(?=\s|$)/g, '\u0647')],
};

/**
 * Arabic text with the Persian kaf and yeh (ک ی), which Persian keyboards and fonts give: Arabic headwords write ك and
 * ي, and a final ى is keyed under ي at import (ArabicSupport's `HeadwordMarks`). Arabic never writes ک or ی.
 * @type {import('language').TextProcessor}
 */
export const arabicLetters = {
    name: 'Arabic letters',
    description: 'کتاب → كتاب, علی → علي',
    process: (str) => [str.replace(/ک/g, 'ك').replace(/ی/g, 'ي')],
};

/**
 * Persian text with the Arabic yeh, kaf and alif maqsura (ي ك ى), which Arabic keyboards and fonts give, and the heh
 * with yeh above ۀ of the ezafe (خانۀ): Persian headwords write ی ک ی and ه. The import keys headwords written with
 * the Arabic letters under the Persian ones (PersianSupport's `HeadwordMarks`).
 * @type {import('language').TextProcessor}
 */
export const persianLetters = {
    name: 'Persian letters',
    description: 'كتاب → کتاب, خانۀ → خانه',
    process: (str) => [str.replace(/ي/g, 'ی').replace(/ك/g, 'ک').replace(/ى/g, 'ی').replace(/ۀ/g, 'ه')],
};

/**
 * Persian text without the zero-width non-joiner inside words (می‌روم, کتاب‌ها), which recognition loses and
 * typing often leaves out; the import keys headwords written with it without it as well.
 * @type {import('language').TextProcessor}
 */
export const removePersianZeroWidthNonJoiner = {
    name: 'Remove zero-width non-joiner',
    description: 'می\u200Cروم → میروم',
    process: (str) => [str.replace(/\u200C/g, '')],
};

/**
 * A Persian verb prefix (می, نمی) or a plural or comparative ending (ها, های, تر, ترین) written apart from its word,
 * where headwords write them joined, also tried joined: می خواهم → میخواهم, کتاب ها → کتابها.
 * @type {import('language').TextProcessor}
 */
export const persianJoinedAffixes = {
    name: 'Persian affixes written apart',
    description: 'می خواهم → میخواهم, کتاب ها → کتابها',
    process: (str) => [
        str,
        str
            .replace(/(^|[^\p{L}\p{M}])(ن?می)\s+(?=\p{L})/gu, '$1$2')
            .replace(/(?<=\p{L})\s+(?=(?:ها|های|تر|ترین)(?![\p{L}\p{M}]))/gu, ''),
    ],
};

/**
 * Hebrew text with vowel points (niqqud) and cantillation marks, which headwords leave out (כֶּלֶב for כלב); the
 * import keys Wiktionary's pointed forms under their letters as well (HebrewSupport's `HeadwordMarks`). The maqaf,
 * paseq, sof pasuq and nun hafukha are punctuation and stay. The text is composed first, which splits a pointed
 * letter's presentation form (שּׁ U+FB2C) into the letter and its points.
 * @type {import('language').TextProcessor}
 */
export const removeHebrewPoints = {
    name: 'Remove vowel points',
    description: 'כֶּלֶב → כלב',
    process: (str) => [str, str.normalize('NFC').replace(/[\u0591-\u05BD\u05BF\u05C1\u05C2\u05C4\u05C5\u05C7]/g, '')],
};

/**
 * Hebrew text with ASCII quotes for the gershayim and the geresh (צה"ל, ג'ירפה), as recognition and keyboards give
 * them: headwords write ״ and ׳. Only a quote between two Hebrew letters, so the quotes of a quotation stay.
 * @type {import('language').TextProcessor}
 */
export const hebrewQuotes = {
    name: 'Geresh and gershayim',
    description: 'צה"ל → צה״ל, ג\'ירפה → ג׳ירפה',
    process: (str) => [
        str,
        str
            .replace(/(?<=[\u05D0-\u05EA])"(?=[\u05D0-\u05EA])/g, '\u05F4')
            .replace(/(?<=[\u05D0-\u05EA])'(?=[\u05D0-\u05EA])/g, '\u05F3'),
    ],
};

/** Kazakh letters by the Latin letters typed for them: i I h on keyboards without Kazakh letters, ə Ə from Latin layouts. */
const KAZAKH_BY_LATIN = {'i': 'і', 'I': 'І', 'ə': 'ә', 'Ə': 'Ә', 'h': 'һ'};

/**
 * Kazakh text with Latin i, I, ə, Ə, h inside a Cyrillic word (кiтап, Iле, шаhар): the Kazakh letters і І ә Ә һ, which
 * keyboards without them and recognition give that way. A word of Latin letters only stays as it is.
 * @type {import('language').TextProcessor}
 */
export const kazakhLatinLetters = {
    name: 'Kazakh letters typed in Latin',
    description: 'кiтап → кітап',
    process: (str) => [str, str.replace(/[\p{L}\p{M}]+/gu, (word) => (/[\u0400-\u04FF]/.test(word) ?
        word.replace(/[iIəƏh]/g, (letter) => KAZAKH_BY_LATIN[letter]) :
        word))],
};

/** The combining grave, which dictionaries and textbooks put on a stressed Cyrillic vowel (гра̀д). */
const GRAVE = /* @__PURE__ */ String.fromCharCode(0x300);

/** The Cyrillic vowels written with a grave as one character, with their letters. */
const GRAVE_LETTERS = {'ѐ': 'е', 'ѝ': 'и', 'Ѐ': 'Е', 'Ѝ': 'И'};

/**
 * Bulgarian and Macedonian text with the stress written as a grave, as dictionaries and textbooks print it (кнѝга,
 * паркѐт, гра̀д): words of three letters or more are also tried without it. Shorter words keep it: the Bulgarian
 * pronoun ѝ and the Macedonian сѐ and нѐ are words of their own. The acute is Yomitan's removeRussianDiacritics.
 * @type {import('language').TextProcessor}
 */
export const removeGraveStress = {
    name: 'Remove grave stress',
    description: 'кнѝга → книга, паркѐт → паркет',
    process: (str) => [str, str.replace(/[\p{L}\p{M}]+/gu, (word) => ([...word.replace(/\p{M}/gu, '')].length < 3 ?
        word :
        word.replace(/[ѐѝЀЍ]/g, (letter) => GRAVE_LETTERS[letter]).split(GRAVE).join('')))],
};

/**
 * Bulgarian й standing alone, as most typed text and recognition write the pronoun ѝ ("her"): also tried as ѝ, in any
 * text. A й inside a word is that letter.
 * @type {import('language').TextProcessor}
 */
export const bulgarianShortIAsPronoun = {
    name: 'Short i as the pronoun',
    description: 'й → ѝ',
    process: (str) => [str, str.replace(/(?<![\p{L}\p{M}])[йЙ](?![\p{L}\p{M}])/gu, (letter) => (letter === 'й' ? 'ѝ' : 'Ѝ'))],
};

/**
 * Macedonian text with the Latin è in a Cyrillic word, as most typed text writes ѐ (сè, нè, сèуште): the Macedonian ѐ,
 * in any text. A word of Latin letters only stays as it is.
 * @type {import('language').TextProcessor}
 */
export const macedonianLatinGrave = {
    name: 'Latin e grave as Cyrillic',
    description: 'сè → сѐ',
    process: (str) => [str, str.replace(/[\p{L}\p{M}]+/gu, (word) => (/\p{Script=Cyrillic}/u.test(word) ?
        word.replace(/[èÈ]/g, (letter) => (letter === 'è' ? 'ѐ' : 'Ѐ')) :
        word))],
};

/** The Romanian comma letters by the cedilla letters older keyboards and encodings type for them. */
const ROMANIAN_COMMA_LETTERS = {'ş': 'ș', 'ţ': 'ț', 'Ş': 'Ș', 'Ţ': 'Ț'};

/**
 * Romanian text with ş ţ Ş Ţ, the cedilla letters older keyboards and encodings type (about one ș or ț in eight in
 * news and Wikipedia text): dictionaries write the comma letters ș ț Ș Ț. The import keys the few headwords written
 * with a cedilla under the comma letters as well (`HeadwordMarks.replacements`).
 * @type {import('language').TextProcessor}
 */
export const romanianCommaLetters = {
    name: 'Romanian comma letters',
    description: 'şi → și, ţară → țară',
    process: (str) => [str, str.replace(/[şţŞŢ]/g, (letter) => ROMANIAN_COMMA_LETTERS[letter])],
};

/**
 * Latin text with u for consonantal v, as scholarly editions print it (uita, uenit, Uergilius): v is also tried for
 * each u before a vowel in the text's first word, one at a time (uita → vita, nouus → novus), and never after q or g
 * (quae, lingua). A word needing two (uiuere for vivere) is not reached; the text as written comes first.
 * @type {import('language').TextProcessor}
 */
export const latinUAsV = {
    name: 'u as v',
    description: 'uita → vita, Uergilius → Vergilius',
    process: (str) => {
        const word = /^[\p{L}\p{M}]*/u.exec(str)[0];
        const out = [str];
        for (let i = 0; i < word.length - 1; i++) {
            if ((word[i] === 'u' || word[i] === 'U') && LATIN_VOWEL.test(word[i + 1]) && !(i > 0 && /[qgQG]/.test(word[i - 1]))) {
                out.push(str.slice(0, i) + (word[i] === 'u' ? 'v' : 'V') + str.slice(i + 1));
            }
        }
        return out;
    },
};

/** A Latin vowel, with or without its length mark. */
const LATIN_VOWEL = /[aeiouyāēīōūȳăĕĭŏŭAEIOUYĀĒĪŌŪȲ]/;

/**
 * Esperanto typed in the x-system, which writes ĉ ĝ ĥ ĵ ŝ ŭ as cx gx hx jx sx ux where the letters cannot be typed:
 * every pair at once, in either case (CXu → Ĉu), the text as written first. The h-system (ch for ĉ, au for aŭ) is not
 * tried: ch and au are also letters of their own (ĉeĥa, Aŭstralio and aero).
 * @type {import('language').TextProcessor}
 */
export const esperantoXSystem = {
    name: 'Esperanto x-system',
    description: 'cxu → ĉu, Ehxosxangxo → Eĥoŝanĝo',
    process: (str) => {
        const hats = str.replace(/([cghjsu])x/giu, (pair, letter) => ESPERANTO_X_SYSTEM[letter]);
        return hats === str ? [str] : [str, hats];
    },
};

const ESPERANTO_X_SYSTEM = {c: 'ĉ', g: 'ĝ', h: 'ĥ', j: 'ĵ', s: 'ŝ', u: 'ŭ', C: 'Ĉ', G: 'Ĝ', H: 'Ĥ', J: 'Ĵ', S: 'Ŝ', U: 'Ŭ'};

/**
 * Welsh ŷ and ŵ, which recognition reads as ý and û (tý, dý for tŷ, dŷ; tûr for tŵr): the text is also tried with
 * them, as read first, so cytûn still finds cytûn. For words from OCR only (`ocrOnly`, glue.js): app text and typed
 * text are looked up as written.
 * @type {import('language').TextProcessor & {ocrOnly: true}}
 */
export const welshMisreadCircumflex = {
    name: 'Misread circumflex',
    description: 'tý → tŷ, tûr → tŵr',
    ocrOnly: true,
    process: (str) => {
        const fixed = str.replace(/[ýÝûÛ]/g, (letter) => WELSH_CIRCUMFLEX[letter]);
        return fixed === str ? [str] : [str, fixed];
    },
};

const WELSH_CIRCUMFLEX = {ý: 'ŷ', Ý: 'Ŷ', û: 'ŵ', Û: 'Ŵ'};

/**
 * The text without the marks over or under its Latin and Cyrillic letters, the letters Unicode does not decompose (ø ł
 * ı, ғ қ ө) as their base letters: fúzfa → fuzfa, Łódź → Lodz. The import keys every headword and form of the
 * languages that take this processor under the same spelling (`HeadwordMarks.fold_marks`, archive_split.cpp, whose
 * fold this one matches), so a mark recognition reads wrong (fúzfa for fűzfa) or adds still finds the word, after the
 * text as read. For words from OCR only (`ocrOnly`, glue.js): app text and typed text are looked up as written, and
 * a word written without marks reaches the folded keys as it is.
 * @type {import('language').TextProcessor & {ocrOnly: true}}
 */
export const foldedMarks = {
    name: 'Folded marks',
    description: 'fúzfa → fuzfa, Łódź → Lodz',
    ocrOnly: true,
    process: (str) => {
        const folded = foldMarks(str);
        return folded === str ? [str] : [str, folded];
    },
};

/**
 * Azerbaijani ə and ı, which recognition reads as e and i (seher for səhər, qiz for qız): the text's first word is also
 * tried with ə for its e and ı for its i, at the first three such letters, every combination of them, as read first, so
 * ev still finds ev; a capital E as Ə too (Eli for Əli). For words from OCR only (`ocrOnly`, glue.js): app text and
 * typed text are looked up as written.
 * @type {import('language').TextProcessor & {ocrOnly: true}}
 */
export const azerbaijaniMisreadLetters = {
    name: 'Misread schwa and dotless i',
    description: 'seher → səhər, qiz → qız',
    ocrOnly: true,
    process: (str) => withMisreadLetters(str, AZERBAIJANI_LETTERS),
};

const AZERBAIJANI_LETTERS = {e: 'ə', E: 'Ə', i: 'ı'};

/**
 * Uzbek ʻ (of oʻ and gʻ) and ʼ (the glottal stop) as dictionaries write them, from the ' ‘ ’ ` ´ that keyboards and
 * recognition give for both: a mark after o or g is ʻ, after another letter ʼ (so'z → soʻz, ta'lim → taʼlim, ta`til →
 * taʼtil). The import keys headwords written with those characters under the same spellings
 * (`HeadwordMarks.replacements`).
 * @type {import('language').TextProcessor}
 */
export const uzbekApostrophes = {
    name: 'Uzbek apostrophes',
    description: "so'z → soʻz, ta'lim → taʼlim",
    process: (str) => [str, str.replace(/(\p{L})['`´‘’ʻʼ]/gu, (_, letter) => letter + ('oOgG'.includes(letter) ? 'ʻ' : 'ʼ'))],
};

/**
 * Uzbek text in Cyrillic letters as the Latin alphabet writes it, which dictionaries use (китоб → kitob, ўқимоқ →
 * oʻqimoq, ғалла → gʻalla): е is ye at a word's start and after a vowel, ъ or ь, ц is ts after a vowel and s elsewhere,
 * ъ is ʼ and ь is dropped; a capital gives a capital first letter (Шаҳар → Shahar). Text without Cyrillic letters stays
 * as it is.
 * @type {import('language').TextProcessor}
 */
export const uzbekCyrillicAsLatin = {
    name: 'Uzbek Cyrillic as Latin',
    description: 'китоб → kitob, ўқимоқ → oʻqimoq',
    process: (str) => (/[а-яёўқғҳ]/i.test(str) ? [str, uzbekLatin(str)] : [str]),
};

const UZBEK_LATIN = {
    'а': 'a', 'б': 'b', 'в': 'v', 'г': 'g', 'ғ': 'gʻ', 'д': 'd', 'ж': 'j', 'з': 'z', 'и': 'i', 'й': 'y', 'к': 'k',
    'қ': 'q', 'л': 'l', 'м': 'm', 'н': 'n', 'о': 'o', 'п': 'p', 'р': 'r', 'с': 's', 'т': 't', 'у': 'u', 'ў': 'oʻ',
    'ф': 'f', 'х': 'x', 'ҳ': 'h', 'ч': 'ch', 'ш': 'sh', 'ъ': 'ʼ', 'ь': '', 'э': 'e', 'ё': 'yo', 'ю': 'yu', 'я': 'ya',
};

const UZBEK_CYRILLIC_VOWELS = 'аеёиоуўэюя';

/**
 * [str] with its Uzbek Cyrillic letters as Latin ones (`uzbekCyrillicAsLatin`).
 * @param {string} str
 * @returns {string}
 */
function uzbekLatin(str) {
    const letters = [...str];
    return letters.map((letter, i) => {
        const lower = letter.toLowerCase();
        const before = i > 0 ? letters[i - 1].toLowerCase() : '';
        let latin = UZBEK_LATIN[lower];
        if (lower === 'е') {
            const afterVowel = before !== '' && (UZBEK_CYRILLIC_VOWELS.includes(before) || 'ъь'.includes(before));
            latin = afterVowel || !/\p{L}/u.test(before) ? 'ye' : 'e';
        } else if (lower === 'ц') {
            latin = before !== '' && UZBEK_CYRILLIC_VOWELS.includes(before) ? 'ts' : 's';
        }
        if (latin === undefined) { return letter; }
        return letter === lower || latin === '' ? latin : latin[0].toUpperCase() + latin.slice(1);
    }).join('');
}

/**
 * [str] as read, then with the letters [misread] maps put right in its first word, at the first [MISREAD_PLACES] of
 * them, every combination: seven texts at most besides the one as read.
 * @param {string} str
 * @param {Record<string, string>} misread
 * @returns {string[]}
 */
function withMisreadLetters(str, misread) {
    const word = /^[\p{L}\p{M}]*/u.exec(str)[0];
    const places = [];
    for (let i = 0; i < word.length && places.length < MISREAD_PLACES; i++) {
        if (word[i] in misread) { places.push(i); }
    }
    const out = [str];
    for (let set = 1; set < 1 << places.length; set++) {
        const letters = str.split('');
        places.forEach((at, bit) => {
            if (set & (1 << bit)) { letters[at] = misread[letters[at]]; }
        });
        out.push(letters.join(''));
    }
    return out;
}

/** The most misread letters of a word tried as the letters meant. */
const MISREAD_PLACES = 3;

/** A consonant of Irish lenition with the combining dot above (after NFD). */
const IRISH_DOTTED = /* @__PURE__ */ new RegExp('([bcdfgmpstBCDFGMPST])' + String.fromCharCode(0x307), 'g');

/**
 * Irish in Gaelic type, which writes lenition as a dot over the consonant (ḃean, ṡlí for bhean, shlí): the text is
 * also tried with consonant + h, in capitals within a word in capitals (ḂEAN → BHEAN), in any text, since people write
 * it so. A few headwords keep the dot (ṡlógacallam), so the text as written comes first.
 * @type {import('language').TextProcessor}
 */
export const irishDottedConsonants = {
    name: 'Dotted consonants',
    description: 'ḃean → bhean, Ṡeán → Sheán',
    process: (str) => {
        const decomposed = str.normalize('NFD');
        const plain = decomposed.replace(IRISH_DOTTED, (pair, letter, offset) => {
            const next = decomposed.charAt(offset + pair.length);
            return letter + (letter !== letter.toLowerCase() && next !== next.toLowerCase() ? 'H' : 'h');
        });
        return plain === decomposed ? [str] : [str, plain.normalize('NFC')];
    },
};

/**
 * Scottish Gaelic in the older spelling, which writes some long vowels with the acute (mór, ór for mòr, òr):
 * the text is also tried with the grave, in any text, since people write it so. A few headwords keep the acute
 * (cóigneir), so the text as written comes first.
 * @type {import('language').TextProcessor}
 */
export const gaelicAcuteAsGrave = {
    name: 'Acute as grave',
    description: 'mór → mòr, Ó → Ò',
    process: (str) => {
        const grave = str.replace(/[áéíóúÁÉÍÓÚ]/g, (letter) => GAELIC_GRAVE[letter]);
        return grave === str ? [str] : [str, grave];
    },
};

const GAELIC_GRAVE = {á: 'à', é: 'è', í: 'ì', ó: 'ò', ú: 'ù', Á: 'À', É: 'È', Í: 'Ì', Ó: 'Ò', Ú: 'Ù'};

/** Code points of the Latin and Cyrillic blocks; their letters drop the marks after them when folded. */
const FOLDED_SCRIPTS = [
    [0x0041, 0x005A], [0x0061, 0x007A], [0x00C0, 0x02AF], [0x0400, 0x052F], [0x1C80, 0x1C8F], [0x1E00, 0x1EFF],
    [0x2C60, 0x2C7F], [0xA640, 0xA69F], [0xA720, 0xA7FF], [0xAB30, 0xAB6F], [0xFF21, 0xFF3A], [0xFF41, 0xFF5A],
];

/**
 * Latin and Cyrillic letters with a stroke, bar, descender or hook that Unicode does not decompose, with their base
 * letters: ø ł đ ħ ı ŧ ƀ ɨ ƶ ǥ, ғ ҕ ӷ қ ҝ ҟ ҡ ӄ ң ӈ ө ұ ү ҳ ҷ ҹ ӌ ҫ ҙ ҭ ԓ and their capitals.
 */
const FOLDED_LETTERS = /* @__PURE__ */ new Map([
    [0x00D8, 0x004F], [0x00F8, 0x006F], [0x0110, 0x0044], [0x0111, 0x0064], [0x0126, 0x0048], [0x0127, 0x0068],
    [0x0131, 0x0069], [0x0141, 0x004C], [0x0142, 0x006C], [0x0166, 0x0054], [0x0167, 0x0074], [0x0180, 0x0062],
    [0x0197, 0x0049], [0x01B5, 0x005A], [0x01B6, 0x007A], [0x01E4, 0x0047], [0x01E5, 0x0067], [0x0243, 0x0042],
    [0x0268, 0x0069], [0x0492, 0x0413], [0x0493, 0x0433], [0x0494, 0x0413], [0x0495, 0x0433], [0x0498, 0x0417],
    [0x0499, 0x0437], [0x049A, 0x041A], [0x049B, 0x043A], [0x049C, 0x041A], [0x049D, 0x043A], [0x049E, 0x041A],
    [0x049F, 0x043A], [0x04A0, 0x041A], [0x04A1, 0x043A], [0x04A2, 0x041D], [0x04A3, 0x043D], [0x04AA, 0x0421],
    [0x04AB, 0x0441], [0x04AC, 0x0422], [0x04AD, 0x0442], [0x04AE, 0x0423], [0x04AF, 0x0443], [0x04B0, 0x0423],
    [0x04B1, 0x0443], [0x04B2, 0x0425], [0x04B3, 0x0445], [0x04B6, 0x0427], [0x04B7, 0x0447], [0x04B8, 0x0427],
    [0x04B9, 0x0447], [0x04C3, 0x041A], [0x04C4, 0x043A], [0x04C7, 0x041D], [0x04C8, 0x043D], [0x04CB, 0x0427],
    [0x04CC, 0x0447], [0x04E8, 0x041E], [0x04E9, 0x043E], [0x04F6, 0x0413], [0x04F7, 0x0433], [0x0512, 0x041B],
    [0x0513, 0x043B],
]);

/**
 * [str] without the marks over or under its Latin and Cyrillic letters, composed again (NFC), as [foldedMarks] says.
 * @param {string} str
 * @returns {string}
 */
function foldMarks(str) {
    let out = '';
    let afterLetter = false;
    for (const char of str.normalize('NFD')) {
        if (/\p{Mn}/u.test(char)) {
            // A mark after another mark goes with the same letter.
            if (afterLetter) continue;
        } else {
            const point = char.codePointAt(0);
            afterLetter = /\p{L}/u.test(char) && FOLDED_SCRIPTS.some(([first, last]) => first <= point && point <= last);
        }
        const base = FOLDED_LETTERS.get(char.codePointAt(0));
        out += base === undefined ? char : String.fromCodePoint(base);
    }
    return out.normalize('NFC');
}
