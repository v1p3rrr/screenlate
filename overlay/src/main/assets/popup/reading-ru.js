/*
 * Stress in Russian headwords (reading.js): dictionaries give the word with its stressed vowel marked (ко́шка) as its
 * reading, which takes the word's place instead of standing over it. Another reading (ёж for еж) goes over the word.
 */
HeadwordReading.addLanguage('ru', (() => {
    /** The acute and grave accents of primary and secondary stress. */
    const STRESS = /[\u0300\u0301]/g;

    /** Whether [reading] is [expression] with stress marks added. */
    function stressed(expression, reading) {
        return reading.normalize('NFD').replace(STRESS, '').normalize('NFC') === expression.normalize('NFC');
    }

    function segments(expression, reading) {
        if (!reading || reading === expression) return [[expression, null]];
        if (stressed(expression, reading)) return [[reading, null]];
        return [[expression, [{ text: reading, tone: null }]]];
    }

    return { syllables: () => null, segments };
})());
