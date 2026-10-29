/*
 * Jyutping of Cantonese headwords (reading.js): a reading with tone digits ("nei5 hou2") split into syllables, one
 * above each character where they match up, each with its tone (1 to 6) for the tone colors.
 */
HeadwordReading.addLanguage('yue', (() => {
    /** One syllable with its tone: an initial and a final, or a syllabic nasal (m4 唔, ng5 五). */
    const SYLLABLE = /^(?:(?:gw|kw|ng|[bpmfdtnlgkhwzcsj])?(?:aa|a|e|i|o|u|yu|oe|eo)(?:i|u|m|n|ng|p|t|k)?|m|ng)[1-6]$/i;
    const HAN = /^[\p{Script=Han}〇]$/u;

    /** The syllables of a reading and the spaces between them; null unless every word of it is Jyutping with tones. */
    function syllables(reading) {
        const parts = [];
        for (const piece of reading.split(/(\s+)/).filter(Boolean)) {
            if (/^\s+$/.test(piece)) {
                parts.push({ text: piece, tone: null });
                continue;
            }
            const found = piece.match(/[a-z]+[1-6]/gi);
            if (!found || found.join('') !== piece || !found.every(syllable => SYLLABLE.test(syllable))) return null;
            parts.push(...found.map(syllable => ({ text: syllable, tone: Number(syllable.slice(-1)) })));
        }
        return parts.length > 0 ? parts : null;
    }

    /**
     * One syllable above each character when the word is all characters and the syllables match them; otherwise the
     * whole reading above the whole word, as written where it is no Jyutping (loanwords: "!bwan1 !cyu").
     */
    function segments(expression, reading) {
        if (!reading || reading === expression) return [[expression, null]];
        const parts = syllables(reading);
        if (!parts) return [[expression, /[a-z]/i.test(reading) ? [{ text: reading, tone: null }] : null]];
        const characters = [...expression];
        const sounds = parts.filter(part => part.tone);
        if (sounds.length === characters.length && characters.every(character => HAN.test(character))) {
            return characters.map((character, i) => [character, [sounds[i]]]);
        }
        return [[expression, parts]];
    }

    return { syllables, segments };
})());
