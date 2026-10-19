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
