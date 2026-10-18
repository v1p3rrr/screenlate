/*
 * Stress in Russian headwords (reading.js): dictionaries give the word with its stressed vowel marked (ко́шка) as its
 * reading, which takes the word's place instead of standing over it. Another reading (ёж for еж) goes over the word.
 * In a sentence only a word written in its dictionary form takes the stress; an inflected form may stress another
 * syllable (рука́, ру́ки) and stays as written.
 */
HeadwordReading.addLanguage('ru', (() => {
    /** The acute and grave accents of primary and secondary stress. */
    const STRESS = /[\u0300\u0301]/g;
    const STRESS_MARK = /^[\u0300\u0301]$/;

    /** Whether [reading] is [expression] with stress marks added. */
    function stressed(expression, reading) {
        return reading.normalize('NFD').replace(STRESS, '').normalize('NFC') === expression.normalize('NFC');
    }

    function segments(expression, reading) {
        if (!reading || reading === expression) return [[expression, null]];
        if (stressed(expression, reading)) return [[reading, null]];
        return [[expression, [{ text: reading, tone: null }]]];
    }

    /** [text], the word in another case (Кошка), with the stress marks of [reading]. */
    function withStress(text, reading) {
        const letters = Array.from(text.normalize('NFD'));
        let index = 0;
        let result = '';
        for (const character of reading.normalize('NFD')) {
            result += STRESS_MARK.test(character) ? character : (letters[index++] ?? character);
        }
        return result.normalize('NFC');
    }

    function sentenceSegments(text, expression, reading) {
        const dictionaryForm = text.toLowerCase() === expression.toLowerCase();
        if (!reading || !dictionaryForm || !stressed(expression, reading)) return [[text, null]];
        return [[withStress(text, reading), null]];
    }

    return { syllables: () => null, segments, sentenceSegments };
})());
