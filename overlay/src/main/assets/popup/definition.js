/*
 * Definition copying: the definitions of one dictionary in an entry, as the copy button next to the dictionary's name
 * puts them on the clipboard.
 *
 * Public API (DefinitionCopy):
 *   copy(glossaries, mode, lang)  { text, html }; glossaries are those of one dictionary ({ content, definitionTags }).
 *                                 'all': everything the dictionary shows, as markup with inline styles (html) and as
 *                                 plain text for apps without formatting. 'meanings': the meanings alone as plain
 *                                 text, numbered when there are several; html is empty. lang, the page's language
 *                                 tag, picks the markup rules of that language's dictionaries.
 *   addLanguage(tag, rules)       registers the markup rules of a language's dictionaries (fields of NO_RULES); each
 *                                 language has them in a script of its own, such as definition-ja.js.
 *
 * Meanings are found by the dictionary's markup: lists marked as glossaries (data "content": "glossary", as JMdict,
 * Jitendex and dictionaries built from them mark them), definitions marked by name in the language's dictionaries,
 * and in plain text the lines left when the language's headwords, examples, phrases and references are taken out.
 * Where nothing is found, the whole text is copied.
 */
const DefinitionCopy = (() => {
    const anki = window.YomitanAnki || null;

    const BLOCK_TAGS = new Set([
        'div', 'p', 'li', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'details', 'summary', 'blockquote',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    ]);
    const SKIPPED_TAGS = new Set(['img', 'rt', 'rp', 'svg', 'style', 'script']);
    const BULLET_STYLES = new Set(['disc', 'circle', 'square']);

    /** A number that opens a meaning: "1 ", "1.", "1)", "(1)", "①", "㊀". */
    const MARKER = /^\s*(?:\d{1,2}(?:[.)．）]\s*|\s+)|[(（]\d{1,2}[)）]\s*|[①-⑳㉑-㉟㊱-㊿❶-❿➀-➉㊀-㊉]\s*)/;
    /**
     * The marker of the first meaning. A glossary counts as numbered only with it, so a plain gloss that starts with a
     * number ("24 hours") is not taken for a numbered meaning.
     */
    const FIRST_MARKER = /^\s*(?:1(?:[.)．）]\s*|\s+)|[(（]1[)）]\s*|[①❶➀㊀]\s*)/;
    /** A list bullet at the start of a line. */
    const BULLET = /^\s*•\s*/;
    /** Letters of the languages dictionaries translate into, against those of the looked-up language. */
    const TARGET = /[\p{Script=Latin}\p{Script=Cyrillic}\p{Script=Greek}]/gu;

    /**
     * The markup rules of a language's dictionaries. A language without rules of its own gets these: meanings come
     * from marked glossary lists and numbered lines only. A pattern left null never matches.
     */
    const NO_RULES = Object.freeze({
        /** data "name" of readings printed inside definitions, left out of a meaning. */
        readingNames: new Set(),
        /** data "name" of a definition in structured content. */
        meaningNames: new Set(),
        /** Letters of the looked-up language (global): a text with twice as many target letters is bilingual. */
        source: null,
        /** A headword line of a plain-text item. */
        headword: null,
        /** The letter of a sub-sense at the start of a line. */
        subMarker: null,
        /** The start of a line of examples, phrases, references or notes. */
        extraStart: null,
        /** The start of a note line in a monolingual dictionary; in a bilingual one such a line is a translation. */
        noteStart: null,
        /** A phrase or example in a bilingual dictionary: the looked-up language first, then its translation. */
        sourceStart: null,
        /** A bilingual line that opens with the sense of the word it translates; it starts a meaning. */
        senseLabel: null,
        /** Labels anywhere in a line (global), and a note that opens it; both are taken out. */
        labels: null,
        leadingNote: null,
        /** A list bullet of the language's dictionaries at the start of a line, besides •. */
        bullet: null,
        /** A quoted example inside a line, and every such example with what follows it (global). */
        example: null,
        examples: null,
        /** The end of a sentence: a line with an example is cut after the last one before it. */
        sentenceEnd: '.',
    });
    const languages = new Map();

    function addLanguage(tag, rules) {
        languages.set(String(tag).toLowerCase(), Object.freeze({ ...NO_RULES, ...rules }));
    }

    /** The rules of [lang] ("ja", "ja-JP"), or NO_RULES. */
    function rulesOf(lang) {
        const tag = String(lang || '').toLowerCase();
        return languages.get(tag) || languages.get(tag.split('-')[0]) || NO_RULES;
    }

    function copy(glossaries, mode, lang) {
        const whole = allText(glossaries);
        if (mode === 'meanings') {
            const rules = rulesOf(lang);
            const found = glossaries.flatMap(glossary => meaningsOf(items(glossary), rules));
            return { text: found.length ? numbered(found) : whole, html: '' };
        }
        return { text: whole, html: allHtml(glossaries) };
    }

    function items(glossary) {
        let content = glossary.content;
        if (typeof content === 'string') {
            try {
                content = JSON.parse(content);
            } catch (e) {
                // A plain string glossary.
            }
        }
        return Array.isArray(content) ? content : [content];
    }

    function tagsOf(glossary) {
        return (glossary.definitionTags || '').split(' ').filter(Boolean);
    }

    function numbered(lines) {
        return lines.length > 1 ? lines.map((line, i) => `${i + 1}. ${line}`).join('\n') : lines[0] || '';
    }

    // region Everything

    function allText(glossaries) {
        const parts = glossaries.map(glossary => {
            const tags = tagsOf(glossary);
            const text = items(glossary).map(item => textOf(item)).filter(Boolean).join('\n');
            return (tags.length ? `(${tags.join(', ')}) ` : '') + text;
        });
        return numbered(parts.filter(part => part.trim()));
    }

    function allHtml(glossaries) {
        const parts = glossaries.map(glossary => {
            const tags = tagsOf(glossary);
            const formatted = items(glossary).map(item => itemHtml(item, glossary.dictionary)).filter(Boolean);
            const body = formatted.length > 1 ? `<ul>${formatted.map(f => `<li>${f}</li>`).join('')}</ul>` : formatted.join('');
            return (tags.length ? `<i>(${escapeHtml(tags.join(', '))})</i> ` : '') + body;
        });
        return parts.length > 1 ? `<ol>${parts.map(part => `<li>${part}</li>`).join('')}</ol>` : parts[0] || '';
    }

    function itemHtml(item, dictionary) {
        const text = plainString(item);
        if (text !== null) return escapeHtml(text).replace(/\n/g, '<br>');
        if (!anki) return escapeHtml(textOf(item)).replace(/\n/g, '<br>');
        // Pictures would point at files of the app, which other apps cannot open.
        const template = document.createElement('template');
        template.innerHTML = anki.glossaryHtml([item], dictionary, { mediaUrl: () => '' });
        template.content.querySelectorAll('img').forEach(image => image.remove());
        const container = document.createElement('div');
        container.append(template.content);
        return container.innerHTML;
    }

    function escapeHtml(text) {
        return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    /** The text of a glossary item or structured-content node: blocks and list items on lines of their own. */
    function textOf(node, skip = null) {
        const out = [];
        write(node, out, skip);
        return out.join('').split('\n').map(line => line.replace(/[ \t\u00a0]+/g, ' ').trim()).filter(Boolean).join('\n');
    }

    function write(node, out, skip) {
        if (node === null || node === undefined) return;
        if (typeof node === 'string') {
            out.push(node);
            return;
        }
        if (Array.isArray(node)) {
            node.forEach(child => write(child, out, skip));
            return;
        }
        if (typeof node !== 'object') {
            out.push(String(node));
            return;
        }
        if (node.type === 'structured-content') return write(node.content, out, skip);
        if (node.type === 'text') return write(node.text, out, skip);
        if (node.type === 'image' || SKIPPED_TAGS.has(node.tag) || skip?.(node)) return;
        if (node.tag === 'br') {
            out.push('\n');
            return;
        }
        if (node.tag === 'ul' || node.tag === 'ol') {
            writeList(node, out, skip);
            return;
        }
        const block = BLOCK_TAGS.has(node.tag);
        if (block) out.push('\n');
        if (node.tag === 'td' || node.tag === 'th') out.push(' ');
        write(node.content, out, skip);
        if (block) out.push('\n');
        // Tags stand apart on the page.
        if (node.data?.class === 'tag') out.push(' ');
    }

    function writeList(list, out, skip) {
        let number = 0;
        out.push('\n');
        for (const child of asArray(list.content)) {
            if (child && typeof child === 'object' && child.tag === 'li' && !skip?.(child)) {
                number += 1;
                out.push('\n', marker(list, child, number));
                write(child.content, out, skip);
                out.push('\n');
            } else {
                write(child, out, skip);
            }
        }
        out.push('\n');
    }

    /** The list marker as the page draws it: a quoted list-style-type ("① "), a number, or a bullet. */
    function marker(list, item, number) {
        const style = String(item.style?.listStyleType ?? list.style?.listStyleType ?? '').trim();
        const quoted = /^(["'])(.*)\1$/.exec(style);
        if (quoted) return quoted[2].trim() ? `${quoted[2].trim()} ` : '';
        if (style === 'none') return '';
        if (BULLET_STYLES.has(style) || (list.tag === 'ul' && !style)) return '• ';
        return `${number}. `;
    }

    function asArray(value) {
        if (value === null || value === undefined) return [];
        return Array.isArray(value) ? value : [value];
    }

    // endregion

    // region Meanings

    function plainString(item) {
        if (typeof item === 'string') return item;
        if (item && typeof item === 'object' && item.type === 'text') return String(item.text ?? '');
        return null;
    }

    /**
     * The meanings of one glossary's items, in order; empty when none can be told apart. Structured items without
     * marked meanings are read as text, as the page shows them.
     */
    function meaningsOf(list, rules) {
        const parts = list.map(item => {
            const text = plainString(item);
            if (text !== null) return { text };
            const marked = [];
            markedMeanings(item, marked, rules);
            return marked.length ? { marked } : { text: textOf(item) };
        });
        const text = parts.filter(part => part.text !== undefined).map(part => part.text).join('\n');
        const state = {
            numbered: text.split('\n').some(line => FIRST_MARKER.test(line)),
            bilingual: isBilingual(text, rules),
            started: false,
            extra: false,
        };
        const found = [];
        parts.forEach(part => {
            if (part.marked) {
                found.push(...part.marked);
            } else {
                textMeanings(part.text, state, found, rules);
            }
        });
        return found.filter(Boolean);
    }

    /** Whether the dictionary explains in another language, so lines in the looked-up language are examples. */
    function isBilingual(text, rules) {
        if (!rules.source) return false;
        const target = (text.match(TARGET) || []).length;
        const source = (text.match(rules.source) || []).length;
        return target > 0 && target * 2 > source;
    }

    /**
     * Meanings of a text item. In a numbered glossary a number starts a meaning, lines before the first number (the
     * headword, grammar, origin) are left out, and once a line is an example, phrase or note, the lines after it are
     * too until the next number. Without numbers every item is a meaning, its first line left out when it is a
     * headword; a bilingual line that opens with the sense it translates starts another one.
     */
    function textMeanings(text, state, found, rules) {
        const lines = text.split('\n');
        let startsNew = true;
        if (!state.numbered) state.extra = false;
        lines.forEach((raw, index) => {
            let line = raw.trim();
            if (!line) return;
            const number = state.numbered ? MARKER.exec(line) : null;
            if (number) {
                state.started = true;
                state.extra = false;
                startsNew = true;
                line = line.slice(number[0].length);
            } else if (state.numbered && !state.started) {
                return;
            } else if (!state.numbered && index === 0 && lines.length > 1 && rules.headword?.test(line)) {
                return;
            } else if (rules.subMarker?.test(line)) {
                state.extra = false;
            } else if (state.bilingual && !state.numbered && rules.senseLabel?.test(line)) {
                state.extra = false;
                startsNew = true;
            } else if (state.extra || isExtra(line, state.bilingual, rules)) {
                state.extra = true;
                return;
            }
            line = tidy(line, rules);
            if (!line) return;
            if (startsNew || found.length === 0) {
                found.push(line);
                startsNew = false;
            } else {
                found[found.length - 1] += `; ${line}`;
            }
        });
    }

    function isExtra(line, bilingual, rules) {
        // An example and its translation around a double bar: 日本人は米を食べている∥Японцы едят рис.
        if (rules.extraStart?.test(line) || line.includes('∥')) return true;
        return Boolean(bilingual ? rules.sourceStart?.test(line) : rules.noteStart?.test(line));
    }

    /**
     * Takes out the language's labels, a leading note, list bullets and sub-sense letters, and examples: a line is cut
     * after the last sentence before its first example.
     */
    function tidy(line, rules) {
        let text = line;
        if (rules.labels) text = text.replace(rules.labels, ' ');
        if (rules.leadingNote) text = text.replace(rules.leadingNote, '');
        text = text.replace(BULLET, '');
        if (rules.bullet) text = text.replace(rules.bullet, '');
        if (rules.subMarker) text = text.replace(rules.subMarker, '');
        const example = rules.example?.exec(text);
        if (example) {
            const end = text.lastIndexOf(rules.sentenceEnd, example.index);
            text = end >= 0 ? text.slice(0, end + rules.sentenceEnd.length) : text.replace(rules.examples, '');
        }
        return text.replace(/\s+/g, ' ').trim();
    }

    /** Meanings marked in structured content; a glossary list's items go on one line. */
    function markedMeanings(node, found, rules) {
        if (!node || typeof node !== 'object') return;
        if (Array.isArray(node)) {
            node.forEach(child => markedMeanings(child, found, rules));
            return;
        }
        if (node.type === 'structured-content') {
            markedMeanings(node.content, found, rules);
            return;
        }
        const data = node.data || {};
        if (data.content === 'glossary') {
            const children = asArray(node.content);
            const listed = children.filter(child => child && typeof child === 'object' && child.tag === 'li');
            const parts = listed.length ? listed.map(li => oneLine(textOf(li.content))) : [oneLine(textOf(node.content))];
            found.push(parts.filter(Boolean).join('; '));
            return;
        }
        if (rules.meaningNames.has(data.name)) {
            found.push(oneLine(textOf(node.content, child => rules.readingNames.has(child.data?.name))));
            return;
        }
        markedMeanings(node.content, found, rules);
    }

    function oneLine(text) {
        return text.replace(/\s*\n\s*/g, ' ').trim();
    }

    // endregion

    return { copy, addLanguage };
})();
