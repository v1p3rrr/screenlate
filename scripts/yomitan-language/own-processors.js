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
 * Thai text in the order and form headwords are written: sara am ำ given as nikhahit + sara aa (ํ + า), also with the
 * tone mark between them (น + ํ + ้ + า for น้ำ), and a tone mark or thanthakhat given before the vowel above its letter
 * (ก + ่ + ี for กี่). Both look the same as the headword; recognizers and keyboards give either.
 * @type {import('language').TextProcessor}
 */
export const thaiCharacterOrder = {
    name: 'Thai character order',
    description: 'ํา → ำ, ก่ี → กี่',
    process: (str) => [str, str
        .replace(/\u0E4D([\u0E48-\u0E4B]?)\u0E32/g, '$1\u0E33')
        .replace(/([\u0E48-\u0E4C])([\u0E31\u0E34-\u0E37])/g, '$2$1')],
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
