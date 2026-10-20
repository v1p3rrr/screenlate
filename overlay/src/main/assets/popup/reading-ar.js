/*
 * Vowel marks in Arabic headwords (reading.js): dictionaries give the word with its harakat (كِتَاب) as the reading of
 * the plain headword, which takes the word's place instead of standing over it, as Russian stress does. A reading with
 * other letters goes over the word.
 */
HeadwordReading.addLanguage('ar', (() => {
    /** Harakat, the dagger alef and Quranic marks; not the hamza and madda, which NFC writes into their letters. */
    const MARKS = /[\u0610-\u061A\u064B-\u0652\u0656-\u065F\u0670\u06D6-\u06DC\u06DF-\u06E4\u06E7\u06E8\u06EA-\u06ED]/g;

    function segments(expression, reading) {
        if (!reading || reading === expression) return [[expression, null]];
        if (reading.normalize('NFC').replace(MARKS, '') === expression.normalize('NFC')) return [[reading, null]];
        return [[expression, [{ text: reading, tone: null }]]];
    }

    return { syllables: () => null, segments };
})());
