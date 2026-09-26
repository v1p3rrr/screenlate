'use strict';

/*
 * Values of Anki field markers for one lookup result (see core.anki.note.FieldTemplate).
 *
 * NoteData.build(result, styles) returns { values: { marker: text }, media: [{ dictionary, path, placeholder }] }.
 * Glossary images point at placeholders; Kotlin copies the files into AnkiDroid and substitutes the names.
 */
const NoteData = (() => {
    const renderer = window.YomitanRender || null;

    function escapeHtml(text) {
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function shortName(title) {
        return (title || '').replace(/\s*[[(].*?[\])]\s*$/, '') || title;
    }

    /** Same normalization as FieldTemplate.glossaryMarker. */
    function glossaryMarker(title) {
        return 'glossary-' + shortName(title).toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-+|-+$/g, '');
    }

    function furiganaHtml(expression, reading) {
        if (!renderer) return escapeHtml(expression);
        return renderer.furiganaSegments(expression, reading)
            .map(([text, ruby]) => ruby ? `<ruby>${escapeHtml(text)}<rt>${escapeHtml(ruby)}</rt></ruby>` : escapeHtml(text))
            .join('');
    }

    /** Anki's `漢字[かんじ]` notation; a space separates a reading group from the text before it. */
    function furiganaPlain(expression, reading) {
        if (!renderer) return expression;
        return renderer.furiganaSegments(expression, reading)
            .map(([text, ruby], index) => ruby ? `${index > 0 ? ' ' : ''}${text}[${ruby}]` : text)
            .join('');
    }

    function glossaryHtml(glossaries, styles, media) {
        const options = {
            exporting: true,
            mediaUrl: (dictionary, path) => {
                // Terminated so that "-1~" is never a prefix of "-10~".
                const placeholder = `screenlate-media-${media.length}~`;
                media.push({ dictionary, path, placeholder });
                return placeholder;
            },
        };
        const byDictionary = new Map();
        for (const glossary of glossaries) {
            if (!byDictionary.has(glossary.dictionary)) byDictionary.set(glossary.dictionary, []);
            byDictionary.get(glossary.dictionary).push(glossary);
        }
        const root = document.createElement('div');
        root.className = 'yomitan-glossary';
        root.style.textAlign = 'left';
        const list = document.createElement('ol');
        for (const [dictionary, items] of byDictionary) {
            for (const glossary of items) {
                const li = document.createElement('li');
                li.dataset.dictionary = dictionary;
                const tags = (glossary.definitionTags || '').split(' ').filter(Boolean);
                const label = document.createElement('i');
                label.textContent = `(${[...tags, shortName(dictionary)].join(', ')}) `;
                li.append(label);
                const body = document.createElement('span');
                if (renderer) renderer.renderGlossary(body, glossary.content, dictionary, options);
                else body.textContent = JSON.stringify(glossary.content);
                li.append(body);
                list.append(li);
            }
        }
        root.append(list);
        const css = renderer
            ? [...byDictionary.keys()]
                .map(dictionary => styles.find(style => style.dictionary === dictionary))
                .filter(Boolean)
                .map(style => renderer.dictionaryCss(style.css, style.dictionary))
                .join('\n')
            : '';
        return root.outerHTML + (css.trim() ? `<style>${css}</style>` : '');
    }

    function pitchHtml(term) {
        if (!renderer) return '';
        const items = [];
        for (const group of term.pitches || []) {
            for (const accent of group.pitches || []) {
                const position = accent.pattern || accent.position;
                const morae = renderer.morae(term.reading || term.expression);
                const high = index => typeof position === 'string'
                    ? position[index] === 'H'
                    : (position === 0 ? index > 0 : (position === 1 ? index < 1 : index > 0 && index < position));
                const spans = morae.map((mora, index) => {
                    const styles = [];
                    if (high(index)) styles.push('border-top: 1px solid currentColor');
                    if (high(index) && !high(index + 1)) styles.push('border-right: 1px solid currentColor');
                    return styles.length ? `<span style="${styles.join('; ')}">${escapeHtml(mora)}</span>` : escapeHtml(mora);
                }).join('');
                items.push(`<li>${spans} [${renderer.downsteps(position).join(', ')}]</li>`);
            }
        }
        return items.length ? `<ol>${items.join('')}</ol>` : '';
    }

    function pitchPositions(term) {
        if (!renderer) return '';
        const group = (term.pitches || []).find(item => (item.pitches || []).length);
        if (!group) return '';
        return [...new Set(group.pitches.flatMap(accent => renderer.downsteps(accent.pattern || accent.position)))].join(',');
    }

    function frequenciesHtml(term) {
        const items = (term.frequencies || [])
            .map(group => {
                const values = (group.values || []).map(v => v.displayValue || String(v.value)).join(', ');
                return values ? `<li>${escapeHtml(shortName(group.dictionary))}: ${escapeHtml(values)}</li>` : '';
            })
            .filter(Boolean);
        return items.length ? `<ul style="text-align: left;">${items.join('')}</ul>` : '';
    }

    function build(result, styles) {
        const term = result.term;
        const glossaries = term.glossaries || [];
        const media = [];
        const values = {
            expression: term.expression,
            reading: term.reading || term.expression,
            furigana: furiganaHtml(term.expression, term.reading),
            'furigana-plain': furiganaPlain(term.expression, term.reading),
            glossary: glossaryHtml(glossaries, styles, media),
            'pitch-accents': pitchHtml(term),
            'pitch-accent-positions': pitchPositions(term),
            frequencies: frequenciesHtml(term),
            'part-of-speech': (term.rules || '').split(' ').filter(Boolean).join(', '),
            tags: [...new Set(glossaries.flatMap(g => `${g.termTags || ''} ${g.definitionTags || ''}`.split(' ')).filter(Boolean))].join(', '),
            dictionary: glossaries[0]?.dictionary || '',
        };
        const firstDictionary = glossaries[0]?.dictionary;
        values['glossary-first'] = firstDictionary
            ? glossaryHtml(glossaries.filter(g => g.dictionary === firstDictionary), styles, media)
            : '';
        for (const dictionary of new Set(glossaries.map(g => g.dictionary))) {
            values[glossaryMarker(dictionary)] = glossaryHtml(glossaries.filter(g => g.dictionary === dictionary), styles, media);
        }
        return { values, media };
    }

    return { build };
})();
