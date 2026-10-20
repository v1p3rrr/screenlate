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
