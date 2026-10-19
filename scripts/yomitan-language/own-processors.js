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
