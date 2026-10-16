/*
 * Readings of headwords drawn by a language's own rules: Chinese pinyin, one syllable per character, and the stressed
 * form of Russian words. Japanese readings go through YomitanRender.furigana instead.
 *
 *   addLanguage(tag, rules)                 registers a language's rules; each language has them in a script of its
 *                                           own, such as reading-zh.js. rules.syllables(reading) splits a reading into
 *                                           parts { text, tone }, tone null for the text between syllables, or gives
 *                                           null for a reading it cannot split; rules.segments(expression, reading)
 *                                           gives [[text, parts | null], ...], one ruby per text with parts.
 *   segments(lang, expression, reading)     the language's segments, or null for a language without rules.
 *   syllables(lang, reading)                the language's syllables, or null.
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

    return { addLanguage, segments, syllables };
})();
