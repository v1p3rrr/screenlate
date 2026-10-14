/*
 * Pinyin of Chinese headwords (reading.js): a tone-marked reading split into syllables, one above each character
 * where they match up, each with its tone (1 to 4, 5 for the neutral tone) for the tone colors.
 */
HeadwordReading.addLanguage('zh', (() => {
    /** Combining marks of the four tones. */
    const TONE_MARKS = { '\u0304': 1, '\u0301': 2, '\u030c': 3, '\u0300': 4 };
    const NEUTRAL = 5;

    /** One syllable without its tone mark: an initial and a final, a nasal interjection, and the erhua r. */
    const SYLLABLE = new RegExp(
        '^(?:(?:zh|ch|sh|[bpmfdtnlgkhjqxrzcsyw])?'
        + '(?:iang|iong|uang|ueng|ang|eng|ing|ong|ian|iao|uai|uan|üan|ai|ao|an|ei|en|er|ia|ie|in|iu|ou|ua|uo|ui|un|üe|ue|ün|a|o|e|i|u|ü|ê)'
        + '|hng|hm|ng|m|n)r?$',
    );
    const LONGEST_SYLLABLE = 7;
    const LETTER = /^[a-zêü]$/;
    const HAN = /^[\p{Script=Han}〇]$/u;
    const ERHUA = new Set(['儿', '兒']);

    /** Each character of [text] with its letter in lower case without marks (ü and ê kept) and its tone, 0 for none. */
    function letters(text) {
        return [...text.normalize('NFC')].map(character => {
            const [first, ...marks] = character.normalize('NFD');
            let base = first.toLowerCase();
            let tone = 0;
            for (const mark of marks) {
                if (mark === '\u0308' && base === 'u') base = 'ü';
                else if (mark === '\u0302' && base === 'e') base = 'ê';
                else if (TONE_MARKS[mark]) tone = TONE_MARKS[mark];
            }
            return { character, base, tone };
        });
    }

    /**
     * A run of letters as the fewest syllables, or null. A syllable that starts with a, o or e never follows another
     * directly (pinyin puts an apostrophe there: Xī'ān), so fāngàn is fān + gàn.
     */
    function splitRun(run) {
        const plain = run.map(letter => letter.base).join('');
        const best = new Array(plain.length + 1).fill(null);
        best[0] = [];
        for (let start = 0; start < plain.length; start++) {
            if (!best[start]) continue;
            for (let end = Math.min(plain.length, start + LONGEST_SYLLABLE); end > start; end--) {
                const piece = plain.slice(start, end);
                if (start > 0 && /^[aoeê]/.test(piece)) continue;
                if (!SYLLABLE.test(piece)) continue;
                const split = [...best[start], [start, end]];
                if (!best[end] || split.length < best[end].length) best[end] = split;
            }
        }
        return best[plain.length]?.map(([start, end]) => {
            const piece = run.slice(start, end);
            return {
                text: piece.map(letter => letter.character).join(''),
                tone: piece.find(letter => letter.tone)?.tone || NEUTRAL,
            };
        }) ?? null;
    }

    function syllables(reading) {
        const all = letters(reading);
        const parts = [];
        let index = 0;
        while (index < all.length) {
            let end = index;
            const isLetter = LETTER.test(all[index].base);
            while (end < all.length && LETTER.test(all[end].base) === isLetter) end++;
            const run = all.slice(index, end);
            if (isLetter) {
                const split = splitRun(run);
                if (!split) return null;
                parts.push(...split);
            } else {
                parts.push({ text: run.map(letter => letter.character).join(''), tone: null });
            }
            index = end;
        }
        return parts;
    }

    /** "diǎnr" over 点儿: diǎn over 点 and r over 儿; null when the syllables do not match the characters so. */
    function withErhua(sounds, characters) {
        const split = sounds.flatMap(sound => /..r$/i.test(sound.text)
            ? [{ text: sound.text.slice(0, -1), tone: sound.tone }, { text: sound.text.slice(-1), tone: NEUTRAL, erhua: true }]
            : [sound]);
        if (split.length !== characters.length) return null;
        if (!split.every((sound, i) => !sound.erhua || ERHUA.has(characters[i]))) return null;
        return split.map(({ text, tone }) => ({ text, tone }));
    }

    /**
     * One syllable above each character when the word is all characters and the syllables match them; otherwise the
     * whole reading above the whole word.
     */
    function segments(expression, reading) {
        const parts = reading && reading !== expression ? syllables(reading) : null;
        if (!parts) return [[expression, null]];
        const characters = [...expression];
        const sounds = parts.filter(part => part.tone);
        if (characters.every(character => HAN.test(character))) {
            const aligned = sounds.length === characters.length ? sounds : withErhua(sounds, characters);
            if (aligned) return characters.map((character, i) => [character, [aligned[i]]]);
        }
        return [[expression, parts]];
    }

    return { syllables, segments };
})());
