/*
 * Persian readings (reading.js): dictionaries give a Latin romanization (مجازی majâzi), which stands after the word
 * in the headword line rather than over it, where a long Latin reading would stretch a few Arabic letters.
 */
HeadwordReading.addLanguage('fa', (() => {
    function segments(expression) {
        return [[expression, null]];
    }

    function after(expression, reading) {
        return reading && reading !== expression ? reading : null;
    }

    return { syllables: () => null, segments, after };
})());
