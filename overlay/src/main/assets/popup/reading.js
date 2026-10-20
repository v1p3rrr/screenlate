/*
 * Readings of headwords drawn by a language's own rules: Chinese pinyin, one syllable per character, the stressed
 * form of Russian words, the vowelled form of Arabic ones and the Latin reading after Persian ones. Japanese readings
 * go through YomitanRender.furigana instead.
 *
 *   addLanguage(tag, rules)                 registers a language's rules; each language has them in a script of its
 *                                           own, such as reading-zh.js. rules.syllables(reading) splits a reading into
 *                                           parts { text, tone }, tone null for the text between syllables, or gives
 *                                           null for a reading it cannot split; rules.segments(expression, reading)
 *                                           gives [[text, parts | null], ...], one ruby per text with parts;
 *                                           rules.sentenceSegments(text, expression, reading), when a language has
 *                                           it, gives the segments of a word as a sentence spells it (text);
 *                                           rules.after(expression, reading), when a language has it, gives the text
 *                                           shown after the word instead of over it, or null.
 *   segments(lang, expression, reading)     the language's segments, or null for a language without rules.
 *   sentenceSegments(lang, text, expression, reading)
 *                                           the language's segments of a word in a sentence, or null when the
 *                                           language has no such rules.
 *   syllables(lang, reading)                the language's syllables, or null.
 *   after(lang, expression, reading)        the language's text after the word, or null.
 */
const HeadwordReading = (() => {
    const languages = new Map();

    function addLanguage(tag, rules) {
        languages.set(String(tag).toLowerCase(), rules);
    }

    /** The rules of [lang] ("zh", "zh-Hant"), or null. */
    function rulesOf(lang) {
        const tag = String(lang || '').toLowerCase();
        return languages.get(tag) || languages.get(tag.split('-')[0]) || null;
    }

    function segments(lang, expression, reading) {
        return rulesOf(lang)?.segments(expression, reading || '') ?? null;
    }

    function syllables(lang, reading) {
        return rulesOf(lang)?.syllables(reading || '') ?? null;
    }

    function sentenceSegments(lang, text, expression, reading) {
        return rulesOf(lang)?.sentenceSegments?.(text || '', expression || '', reading || '') ?? null;
    }

    function after(lang, expression, reading) {
        return rulesOf(lang)?.after?.(expression || '', reading || '') || null;
    }

    return { addLanguage, after, segments, sentenceSegments, syllables };
})();
