'use strict';

/*
 * Values of Anki field markers for one lookup result, in the format Yomitan's default field templates produce, so
 * note types made for Yomitan (Lapis, Senren, ...) render them as they expect. See core.anki.note.FieldTemplate for
 * the marker list.
 *
 * NoteData.build(result, context, markers) returns
 *   { values: { marker: html }, media: [{ dictionary, path, placeholder }], term: { expression, reading } }.
 *   context: { styles: [{ dictionary, css }], frequencyModes: { dictionary: 'rank-based' | 'occurrence-based' },
 *              query: lookup text }
 *   markers: marker names to compute; the rest are skipped because glossaries are expensive to render.
 * Glossary images point at placeholders; Kotlin copies the files into AnkiDroid and substitutes the names. Sentence,
 * cloze, screenshot, audio and document title values come from Kotlin.
 *
 * NoteData.sentenceFurigana(parts, lang) formats a sentence split into looked-up terms: { html, plain } for
 * {sentence-furigana} and {sentence-furigana-plain}. parts: [{ text, expression?, reading? }]; lang: the note
 * language's tag.
 *
 * Furigana of a language whose readings give one syllable per character (Chinese pinyin, reading.js) put each
 * syllable over its character; Russian stress marks go into the word, in a sentence only into words written in their
 * dictionary form; Japanese readings go through Yomitan's furigana distribution.
 */
const NoteData = (() => {
    const render = window.YomitanRender || null;
    const anki = window.YomitanAnki || null;

    const PART_OF_SPEECH_NAMES = {
        v1: 'Ichidan verb',
        v5: 'Godan verb',
        vk: 'Kuru verb',
        vs: 'Suru verb',
        vz: 'Zuru verb',
        'adj-i': 'I-adjective',
        n: 'Noun',
    };

    function escapeHtml(text) {
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    /**
     * A `<style>` element for a note field, or '' for no styles. The CSS comes from dictionaries (styles.css and the
     * title in the scope selector), and a `</style` in it would end the element early and turn the rest into markup of
     * the card; written as `<\/style` it is the same text inside CSS strings and no longer an end tag.
     */
    function styleElement(css) {
        return css.trim() ? `<style>${css.replace(/<\/(style)/gi, '<\\/$1')}</style>` : '';
    }

    /** Yomitan's getKebabCase: the dictionary part of single-glossary-* and single-frequency-* markers. */
    function kebab(title) {
        return title
            .replace(/[\s_　]/g, '-')
            .replace(/[^\p{L}\p{N}-]/gu, '')
            .replace(/--+/g, '-')
            .replace(/^-|-$/g, '')
            .toLowerCase();
    }

    /** Markers of Screenlate versions before the Yomitan names: glossary-<dictionary without its version>. */
    function legacyGlossaryMarker(title) {
        const short = (title || '').replace(/\s*[[(].*?[\])]\s*$/, '') || title;
        return 'glossary-' + short.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-+|-+$/g, '');
    }

    function parseContent(text) {
        if (typeof text !== 'string') return text;
        try {
            return JSON.parse(text);
        } catch (e) {
            return [text];
        }
    }

    // region Furigana

    /**
     * [[text, ruby], ...] of a word; [lang] picks the reading.js rules where the language has them. A reading those
     * rules cannot split goes over the whole word.
     */
    function segments(expression, reading, lang) {
        const syllables = typeof HeadwordReading === 'undefined' ? null : HeadwordReading.segments(lang, expression, reading);
        if (syllables) {
            // A single segment that keeps the word gets the whole reading over it; one that respells the word (Russian
            // stress marks) takes its place.
            const kept = syllables.length === 1 && !syllables[0][1] && syllables[0][0] === expression;
            if (kept) return [[expression, reading && reading !== expression ? reading : '']];
            return rubyPieces(syllables);
        }
        return render ? render.furiganaSegments(expression, reading) : [[expression, '']];
    }

    /** [[text, ruby], ...] of reading.js segments. */
    function rubyPieces(syllables) {
        return syllables.map(([text, parts]) => [text, parts ? parts.map(part => part.text).join('') : '']);
    }

    /** Whether [lang] has reading.js rules, so its furigana do not go through Yomitan's Japanese distribution. */
    function hasSyllableRules(lang) {
        return typeof HeadwordReading !== 'undefined' && HeadwordReading.segments(lang, '', '') !== null;
    }

    /** Ruby markup of one piece; text and reading are escaped, as Yomitan's templates do. */
    function rubyHtml(text, ruby) {
        return ruby ? `<ruby>${escapeHtml(text)}<rt>${escapeHtml(ruby)}</rt></ruby>` : escapeHtml(text);
    }

    function furiganaHtml(expression, reading, lang) {
        return segments(expression, reading, lang).map(([text, ruby]) => rubyHtml(text, ruby)).join('');
    }

    /** Anki's `漢字[かんじ]` notation; a space separates a reading group from the text before it. */
    function furiganaPlain(expression, reading, lang) {
        let result = '';
        for (const [text, ruby] of segments(expression, reading, lang)) {
            if (ruby) {
                if (result.length > 0) result += ' ';
                result += `${escapeHtml(text)}[${escapeHtml(ruby)}]`;
            } else {
                result += escapeHtml(text);
            }
        }
        return result;
    }

    function sentenceFurigana(parts, lang) {
        const syllables = hasSyllableRules(lang);
        let html = '';
        let plain = '';
        for (const part of parts) {
            let pieces = [[part.text, '']];
            if (part.expression && syllables) {
                const own = HeadwordReading.sentenceSegments(lang, part.text, part.expression, part.reading || '');
                // The text may spell the word in the other script; its characters take the word's syllables.
                pieces = own ? rubyPieces(own) : segments(part.text, part.reading || '', lang);
            } else if (part.expression && anki) {
                pieces = anki.distributeFuriganaInflected(part.expression, part.reading || part.expression, part.text);
            }
            html += '<span class="term">';
            for (const [text, ruby] of pieces) {
                html += rubyHtml(text, ruby);
                plain += ruby ? ` ${escapeHtml(text)}[${escapeHtml(ruby)}]` : escapeHtml(text);
            }
            html += '</span>';
        }
        return { html, plain: plain.trimStart() };
    }

    // endregion

    // region Glossary (Yomitan templates "glossary-single", "glossary", "glossary-first", "glossary-plain")

    function glossaryBuilder(context, media) {
        const options = {
            mediaUrl: (dictionary, path) => {
                // Terminated so that "-1~" is never a prefix of "-10~".
                const placeholder = `screenlate-media-${media.length}~`;
                media.push({ dictionary, path, placeholder });
                return placeholder;
            },
        };
        const styleOf = dictionary => (context.styles || []).find(style => style.dictionary === dictionary)?.css || '';

        function formatItem(dictionary, item) {
            if (typeof item === 'string') return escapeHtml(item).replace(/\n/g, '<br>');
            if (!anki) return escapeHtml(JSON.stringify(item));
            return anki.glossaryHtml([item], dictionary, options);
        }

        function single(glossary, brief, noDictionaryTag) {
            let html = '';
            if (!brief) {
                const labels = (glossary.definitionTags || '').split(' ').filter(Boolean);
                if (!noDictionaryTag) labels.push(glossary.dictionary);
                if (labels.length) html += `<i>(${labels.map(escapeHtml).join(', ')})</i> `;
            }
            const items = parseContent(glossary.content);
            const list = Array.isArray(items) ? items : [items];
            if (list.length <= 1) {
                html += list.map(item => formatItem(glossary.dictionary, item)).join('');
            } else {
                html += `<ul>${list.map(item => `<li>${formatItem(glossary.dictionary, item)}</li>`).join('')}</ul>`;
            }
            return html;
        }

        function all(glossaries, brief, noDictionaryTag) {
            let html = '<div style="text-align: left;" class="yomitan-glossary"><ol>';
            const styled = new Set();
            for (const glossary of glossaries) {
                html += `<li data-dictionary="${escapeHtml(glossary.dictionary)}">${single(glossary, brief, noDictionaryTag)}</li>`;
                if (!styled.has(glossary.dictionary)) {
                    styled.add(glossary.dictionary);
                    html += styleElement(render ? render.dictionaryCss(styleOf(glossary.dictionary), glossary.dictionary) : '');
                }
            }
            return html + '</ol></div>';
        }

        function first(glossary, brief, noDictionaryTag) {
            if (!glossary) return '';
            const css = anki ? anki.glossaryCss(styleOf(glossary.dictionary)) : '';
            return '<div style="text-align: left;" class="yomitan-glossary">'
                + single(glossary, brief, noDictionaryTag)
                + styleElement(css)
                + '</div>';
        }

        function plain(glossaries, noDictionaryTag) {
            return glossaries.map(glossary => {
                const items = parseContent(glossary.content);
                const list = Array.isArray(items) ? items : [items];
                const text = list
                    .map(item => (anki ? anki.glossaryPlain([item], glossary.dictionary) : escapeHtml(String(item))))
                    .join('<br>');
                return (noDictionaryTag ? '' : `(${escapeHtml(glossary.dictionary)})<br>`) + text;
            }).join('<br>');
        }

        return { all, first, plain };
    }

    // endregion

    // region Frequencies (Yomitan anki-note-data-creator.js getFrequencyNumbers / Harmonic / Average)

    /** One number per dictionary: its first value, or the leading digits of its display value. */
    function frequencyNumbers(term, modes, mode, dictionary) {
        const numbers = [];
        for (const group of term.frequencies || []) {
            if (dictionary && group.dictionary !== dictionary) continue;
            const dictionaryMode = modes[group.dictionary];
            if (mode && dictionaryMode && dictionaryMode !== mode) continue;
            const value = (group.values || [])[0];
            if (!value) continue;
            const display = /^\d+/.exec(value.displayValue || '');
            const parsed = display ? Number.parseInt(display[0], 10) : 0;
            if (parsed > 0) numbers.push(parsed);
            else if (value.value > 0) numbers.push(value.value);
        }
        return numbers;
    }

    function harmonic(numbers) {
        if (!numbers.length) return -1;
        return Math.floor(numbers.length / numbers.reduce((sum, n) => sum + 1 / n, 0));
    }

    function average(numbers) {
        if (!numbers.length) return -1;
        return Math.floor(numbers.reduce((sum, n) => sum + n, 0) / numbers.length);
    }

    function frequencyList(term, dictionary) {
        const items = [];
        for (const group of term.frequencies || []) {
            if (dictionary && group.dictionary !== dictionary) continue;
            for (const value of group.values || []) {
                const shown = value.displayValue || String(value.value);
                items.push(`<li>${escapeHtml(group.dictionary)}: ${escapeHtml(shown)}</li>`);
            }
        }
        return items.length ? `<ul style="text-align: left;">${items.join('')}</ul>` : '';
    }

    // endregion

    // region Pitch accent (Yomitan template "pitch-accent-list")

    /** A transcription's tags as Yomitan's Anki marker writes them before it: `<i>(UK, US)</i> `, each tag a span. */
    function transcriptionTagList(tags) {
        if (!tags.length) return '';
        const spans = tags.map(name => `<span class="tag" data-details="${escapeHtml(name)}">${escapeHtml(name)}</span>`);
        return `<i>(${spans.join(', ')})</i> `;
    }

    function pitchList(term, format) {
        const reading = term.reading || term.expression;
        const items = [];
        for (const group of term.pitches || []) {
            for (const accent of group.pitches || []) {
                const value = accent.pattern || accent.position;
                items.push(anki ? anki.pronunciation(format, reading, value, accent.nasal || [], accent.devoice || []) : '');
            }
        }
        if (items.length === 0) return '';
        if (items.length === 1) return items[0];
        return `<ol>${items.map(item => `<li>${item}</li>`).join('')}</ol>`;
    }

    function pitchCategories(term) {
        if (!anki) return '';
        const reading = term.reading || term.expression;
        const wordClasses = (term.rules || '').split(' ').filter(Boolean);
        const categories = new Set();
        for (const group of term.pitches || []) {
            for (const accent of group.pitches || []) {
                const category = anki.pitchCategory(reading, accent.pattern || accent.position, wordClasses);
                if (category) categories.add(category);
            }
        }
        return [...categories].join(',');
    }

    // endregion

    function build(result, context = {}, markers = null) {
        const term = result.term;
        const glossaries = term.glossaries || [];
        const modes = context.frequencyModes || {};
        const media = [];
        const glossary = glossaryBuilder(context, media);
        const wanted = markers ? new Set(markers) : null;
        const values = {};
        const reading = term.reading || term.expression;
        const lang = result.lang || document.documentElement.lang;
        const wordClasses = (term.rules || '').split(' ').filter(Boolean);

        const standard = {
            expression: () => escapeHtml(term.expression),
            reading: () => escapeHtml(reading),
            furigana: () => furiganaHtml(term.expression, term.reading, lang),
            'furigana-plain': () => furiganaPlain(term.expression, term.reading, lang),
            glossary: () => glossary.all(glossaries, false, false),
            'glossary-brief': () => glossary.all(glossaries, true, false),
            'glossary-no-dictionary': () => glossary.all(glossaries, false, true),
            'glossary-plain': () => glossary.plain(glossaries, false),
            'glossary-plain-no-dictionary': () => glossary.plain(glossaries, true),
            'glossary-first': () => glossary.first(glossaries[0], false, false),
            'glossary-first-brief': () => glossary.first(glossaries[0], true, false),
            'glossary-first-no-dictionary': () => glossary.first(glossaries[0], false, true),
            'cloze-body-kana': () => (anki
                ? anki.distributeFuriganaInflected(term.expression, reading, result.matched || term.expression)
                    .map(([text, ruby]) => escapeHtml(ruby || text)).join('')
                : ''),
            conjugation: () => escapeHtml((result.trace || []).map(step => step.name).join(' « ')),
            dictionary: () => escapeHtml(glossaries[0]?.dictionary || ''),
            'dictionary-alias': () => escapeHtml(glossaries[0]?.dictionary || ''),
            frequencies: () => frequencyList(term, null),
            'frequency-harmonic-rank': () => {
                const value = harmonic(frequencyNumbers(term, modes, 'rank-based', null));
                return String(value === -1 ? 9999999 : value);
            },
            'frequency-harmonic-occurrence': () => {
                const value = harmonic(frequencyNumbers(term, modes, 'occurrence-based', null));
                return String(value === -1 ? 0 : value);
            },
            'frequency-average-rank': () => {
                const value = average(frequencyNumbers(term, modes, 'rank-based', null));
                return String(value === -1 ? 9999999 : value);
            },
            'frequency-average-occurrence': () => {
                const value = average(frequencyNumbers(term, modes, 'occurrence-based', null));
                return String(value === -1 ? 0 : value);
            },
            'part-of-speech': () => {
                const names = [...new Set(wordClasses.map(wordClass => PART_OF_SPEECH_NAMES[wordClass] || wordClass))];
                return escapeHtml(names.length ? names.join(', ') : 'Unknown');
            },
            'phonetic-transcriptions': () => {
                const items = (term.pitches || []).flatMap(group => (group.transcriptions || [])
                    .map((ipa, i) => ({ ipa, tags: group.transcriptionTags?.[i] || [] })));
                return items.length
                    ? `<ul>${items.map(({ ipa, tags }) => `<li class="pronunciation" data-pronunciation-type="phonetic-transcription">${transcriptionTagList(tags)}${escapeHtml(ipa)}</li>`).join('')}</ul>`
                    : '';
            },
            'pitch-accents': () => pitchList(term, 'text'),
            'pitch-accent-graphs': () => pitchList(term, 'graph'),
            'pitch-accent-graphs-jj': () => pitchList(term, 'graph-jj'),
            'pitch-accent-positions': () => pitchList(term, 'position'),
            'pitch-accent-categories': () => pitchCategories(term),
            'search-query': () => escapeHtml(context.query || '').replace(/\n/g, '<br>'),
            tags: () => escapeHtml([...new Set(glossaries.flatMap(g => `${g.termTags || ''} ${g.definitionTags || ''}`.split(' ')).filter(Boolean))].join(', ')),
        };

        for (const [marker, compute] of Object.entries(standard)) {
            if (!wanted || wanted.has(marker)) values[marker] = compute();
        }

        // Per-dictionary markers, named like Yomitan's dynamic templates.
        const dictionaries = [...new Set(glossaries.map(g => g.dictionary))];
        for (const dictionary of dictionaries) {
            const own = glossaries.filter(g => g.dictionary === dictionary);
            const name = `single-glossary-${kebab(dictionary)}`;
            const variants = {
                [name]: () => glossary.all(own, false, false),
                [`${name}-brief`]: () => glossary.all(own, true, false),
                [`${name}-no-dictionary`]: () => glossary.all(own, false, true),
                [`${name}-plain`]: () => glossary.plain(own, false),
                [`${name}-plain-no-dictionary`]: () => glossary.plain(own, true),
                [legacyGlossaryMarker(dictionary)]: () => glossary.all(own, false, false),
            };
            for (const [marker, compute] of Object.entries(variants)) {
                if (!wanted || wanted.has(marker)) values[marker] = compute();
            }
        }
        for (const group of term.frequencies || []) {
            const name = kebab(group.dictionary);
            const numberMarker = `single-frequency-number-${name}`;
            const listMarker = `single-frequency-${name}`;
            if (!wanted || wanted.has(numberMarker)) {
                values[numberMarker] = frequencyNumbers(term, modes, null, group.dictionary).join('');
            }
            if (!wanted || wanted.has(listMarker)) values[listMarker] = frequencyList(term, group.dictionary);
        }
        return { values, media, term: { expression: term.expression, reading: term.reading || '' } };
    }

    return { build, sentenceFurigana, kebab };
})();
