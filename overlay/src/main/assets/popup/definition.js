/*
 * Definition copying: the definitions of one dictionary in an entry, as the copy button next to the dictionary's name
 * puts them on the clipboard.
 *
 * Public API (DefinitionCopy):
 *   copy(glossaries, mode)   { text, html }; glossaries are those of one dictionary ({ content, definitionTags }).
 *                            'all': everything the dictionary shows, as markup with inline styles (html) and as plain
 *                            text for apps without formatting. 'meanings': the meanings alone as plain text, numbered
 *                            when there are several; html is empty.
 *
 * Meanings are found by the dictionary's markup: lists marked as glossaries (data "content": "glossary", as JMdict,
 * Jitendex and dictionaries built from them mark them), definitions of Japanese dictionaries (data "name": "語釈"),
 * and in plain text the lines left when headwords, examples, phrases and references are taken out. Where nothing is
 * found, the whole text is copied.
 */
const DefinitionCopy = (() => {
    const anki = window.YomitanAnki || null;

    const BLOCK_TAGS = new Set([
        'div', 'p', 'li', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'details', 'summary', 'blockquote',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    ]);
    const SKIPPED_TAGS = new Set(['img', 'rt', 'rp', 'svg', 'style', 'script']);
    const BULLET_STYLES = new Set(['disc', 'circle', 'square']);
    /** Readings printed inside Japanese definitions, such as 愛玩(ガン). */
    const READING_NAMES = new Set(['ルビ', 'ルビG']);

    /** A number that opens a meaning: "1 ", "1.", "1)", "(1)", "①", "㊀". */
    const MARKER = /^\s*(?:\d{1,2}(?:[.)．）]\s*|\s+)|[(（]\d{1,2}[)）]\s*|[①-⑳㉑-㉟㊱-㊿❶-❿➀-➉㊀-㊉]\s*)/;
    /** Letters of sub-senses: ㋐, ㋑. */
    const SUB_MARKER = /^\s*[\u32d0-\u32fe]\s*/;
    /** A headword line: かな【漢字】. */
    const HEADWORD = /【[^】]*】|〖[^〗]*〗/;
    /** Lines of examples, phrases, references and notes. */
    const EXTRA_START = /^[→↔⇒⇔☞◇◆■□▼▽◎●「『《［[※＊]/;
    /** A phrase or example in a bilingual dictionary: the looked-up language first, then its translation. */
    const SOURCE_START = /^[{｛]?[…‥]?[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Hangul}～〜]/u;
    /** The Japanese sense a bilingual line translates: 〈食物をとる〉 есть. */
    const SENSE_LABEL = /^〈[^〉]+〉\s*\S/;
    /** A quoted example; a dash or ～ stands for the word. */
    const EXAMPLE = /「[^「」]*[―━～〜][^「」]*」/;
    const EXAMPLES = /「[^「」]*[―━～〜][^「」]*」(?:（[^（）]*）)?/g;
    /** Letters of the looked-up (CJK) languages and of the languages they are translated into. */
    const SOURCE = /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Hangul}]/gu;
    const TARGET = /[\p{Script=Latin}\p{Script=Cyrillic}\p{Script=Greek}]/gu;

    function copy(glossaries, mode) {
        const whole = allText(glossaries);
        if (mode === 'meanings') {
            const found = glossaries.flatMap(glossary => meaningsOf(items(glossary)));
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
    function meaningsOf(list) {
        const parts = list.map(item => {
            const text = plainString(item);
            if (text !== null) return { text };
            const marked = [];
            markedMeanings(item, marked);
            return marked.length ? { marked } : { text: textOf(item) };
        });
        const text = parts.filter(part => part.text !== undefined).map(part => part.text).join('\n');
        const state = {
            numbered: text.split('\n').some(line => MARKER.test(line)),
            bilingual: isBilingual(text),
            started: false,
            extra: false,
        };
        const found = [];
        parts.forEach(part => {
            if (part.marked) {
                found.push(...part.marked);
            } else {
                textMeanings(part.text, state, found);
            }
        });
        return found.filter(Boolean);
    }

    /** Whether the dictionary explains in another language, so lines in the looked-up language are examples. */
    function isBilingual(text) {
        const target = (text.match(TARGET) || []).length;
        const source = (text.match(SOURCE) || []).length;
        return target > 0 && target * 2 > source;
    }

    /**
     * Meanings of a text item. In a numbered glossary a number starts a meaning, lines before the first number (the
     * headword, grammar, origin) are left out, and once a line is an example, phrase or note, the lines after it are
     * too until the next number. Without numbers every item is a meaning, its first line a headword when it has 【】;
     * a bilingual line opening with 〈…〉 (the Japanese sense it translates) starts another one.
     */
    function textMeanings(text, state, found) {
        const lines = text.split('\n');
        let startsNew = true;
        if (!state.numbered) state.extra = false;
        lines.forEach((raw, index) => {
            let line = raw.trim();
            if (!line) return;
            const number = MARKER.exec(line);
            if (number) {
                state.started = true;
                state.extra = false;
                startsNew = true;
                line = line.slice(number[0].length);
            } else if (state.numbered && !state.started) {
                return;
            } else if (!state.numbered && index === 0 && lines.length > 1 && HEADWORD.test(line)) {
                return;
            } else if (SUB_MARKER.test(line)) {
                state.extra = false;
            } else if (state.bilingual && !state.numbered && SENSE_LABEL.test(line)) {
                state.extra = false;
                startsNew = true;
            } else if (state.extra || isExtra(line, state.bilingual)) {
                state.extra = true;
                return;
            }
            line = tidy(line);
            if (!line) return;
            if (startsNew || found.length === 0) {
                found.push(line);
                startsNew = false;
            } else {
                found[found.length - 1] += `; ${line}`;
            }
        });
    }

    function isExtra(line, bilingual) {
        if (EXTRA_START.test(line) || line.includes('∥')) return true;
        // A bilingual line of notes in brackets is still a translation: 〔食する〕 eat.
        return bilingual ? SOURCE_START.test(line) : line.startsWith('〔');
    }

    /**
     * Takes out labels in 〘〙, a leading note in 《》, list bullets and sub-sense letters, and examples: a line is cut
     * after the last sentence before its first example (a quote with a dash or ～ standing for the word).
     */
    function tidy(line) {
        let text = line.replace(/〘[^〙]*〙/g, ' ').replace(/^\s*《[^》]*》/, '').replace(/^\s*[•・]\s*/, '')
            .replace(SUB_MARKER, '');
        const example = EXAMPLE.exec(text);
        if (example) {
            const end = text.lastIndexOf('。', example.index);
            text = end >= 0 ? text.slice(0, end + 1) : text.replace(EXAMPLES, '');
        }
        return text.replace(/\s+/g, ' ').trim();
    }

    /** Meanings marked in structured content; a glossary list's items go on one line. */
    function markedMeanings(node, found) {
        if (!node || typeof node !== 'object') return;
        if (Array.isArray(node)) {
            node.forEach(child => markedMeanings(child, found));
            return;
        }
        if (node.type === 'structured-content') {
            markedMeanings(node.content, found);
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
        if (data.name === '語釈') {
            found.push(oneLine(textOf(node.content, child => READING_NAMES.has(child.data?.name))));
            return;
        }
        markedMeanings(node.content, found);
    }

    function oneLine(text) {
        return text.replace(/\s*\n\s*/g, ' ').trim();
    }

    // endregion

    return { copy };
})();
