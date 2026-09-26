/*
 * Yomitan-compatible rendering of dictionary content.
 *
 * Derived from Hoshi Reader's popup.js (Copyright (c) 2026 Manhhao), which ports code from
 * Yomitan (Copyright (c) 2023-2025 Yomitan Authors) and Yomichan (Copyright (c) 2021-2022 Yomichan Authors).
 * Modified for Screenlate.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Public API (window.YomitanRender):
 *   renderGlossary(parent, content, dictionary, options)  structured content, text and images
 *   dictionaryCss(css, dictionary)                         a dictionary's styles.css scoped to its glossaries
 *   furigana(parent, expression, reading)                  ruby markup for a term
 *   furiganaSegments(expression, reading)                  [[text, reading], ...]
 *   pitchElement(reading, position, nasal, devoice)        mora-level pitch accent markup
 *   pitchCategory(reading, position, rules)                heiban / atamadaka / nakadaka / odaka / kifuku
 *   downsteps(position)                                    downstep positions of a numeric or HL pattern
 *
 * options: { mediaUrl(dictionary, path) -> url, onLookup(query, primaryReading), onExternalLink(url),
 *            exporting: true for Anki markup (plain images, no popup styles) }
 */
'use strict';

window.YomitanRender = (() => {
    const KANJI_RANGE = '一-鿿㐀-䶿豈-﫿々';
    const KANJI_PATTERN = new RegExp(`[${KANJI_RANGE}]`);
    const KANJI_SEGMENT_PATTERN = new RegExp(`[${KANJI_RANGE}]+|[^${KANJI_RANGE}]+`, 'g');
    const SMALL_KANA = new Set('ぁぃぅぇぉゃゅょゎァィゥェォャュョヮ');
    const ALLOWED_TAGS = new Set([
        'br', 'ruby', 'rt', 'rp', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'td', 'th',
        'div', 'span', 'ol', 'ul', 'li', 'details', 'summary', 'a', 'img',
    ]);
    const EM_STYLE_PROPERTIES = new Set([
        'marginTop', 'marginLeft', 'marginRight', 'marginBottom',
        'paddingTop', 'paddingLeft', 'paddingRight', 'paddingBottom',
    ]);

    // region Structured content

    function renderGlossary(parent, content, dictionary, options) {
        const items = Array.isArray(content) ? content : [content];
        if (items.length > 1) {
            const list = document.createElement('ul');
            list.className = 'glossary-list';
            for (const item of items) {
                const li = document.createElement('li');
                li.className = 'glossary-item';
                renderDefinition(li, item, dictionary, options);
                list.appendChild(li);
            }
            parent.appendChild(list);
        } else if (items.length === 1) {
            renderDefinition(parent, items[0], dictionary, options);
        }
    }

    function renderDefinition(parent, item, dictionary, options) {
        if (typeof item === 'string') {
            appendText(parent, item);
        } else if (item && typeof item === 'object') {
            switch (item.type) {
                case 'text':
                    appendText(parent, item.text || '');
                    break;
                case 'structured-content': {
                    const container = document.createElement('span');
                    container.className = 'structured-content';
                    renderNode(container, item.content, dictionary, options);
                    parent.appendChild(container);
                    break;
                }
                case 'image':
                    parent.appendChild(createImage(item, dictionary, options));
                    break;
                default:
                    break;
            }
        }
    }

    function appendText(parent, text) {
        const lines = String(text).split(/\r?\n/);
        lines.forEach((line, index) => {
            if (index > 0) parent.appendChild(document.createElement('br'));
            if (line) parent.appendChild(document.createTextNode(line));
        });
    }

    function renderNode(parent, node, dictionary, options) {
        if (node === null || node === undefined) return;
        if (typeof node === 'string') {
            appendText(parent, node);
            return;
        }
        if (Array.isArray(node)) {
            for (const child of node) renderNode(parent, child, dictionary, options);
            return;
        }
        if (typeof node !== 'object') return;

        const tag = ALLOWED_TAGS.has(node.tag) ? node.tag : 'span';
        if (tag === 'img') {
            parent.appendChild(createImage(node, dictionary, options));
            return;
        }
        if (tag === 'br') {
            parent.appendChild(document.createElement('br'));
            return;
        }

        const element = document.createElement(tag);
        element.classList.add(`gloss-sc-${tag}`);
        if (typeof node.lang === 'string') element.lang = node.lang;
        if (typeof node.title === 'string') element.title = node.title;
        if (node.data && typeof node.data === 'object') setData(element, node.data);
        if (node.style && typeof node.style === 'object') setStyle(element, node.style);
        if ((tag === 'td' || tag === 'th')) {
            if (node.colSpan) element.colSpan = node.colSpan;
            if (node.rowSpan) element.rowSpan = node.rowSpan;
        }
        if (tag === 'details' && node.open) element.open = true;
        if (tag === 'a') setupLink(element, node.href, options);

        renderNode(element, node.content, dictionary, options);

        if (tag === 'table') {
            const container = document.createElement('div');
            container.className = 'gloss-sc-table-container';
            container.appendChild(element);
            parent.appendChild(container);
        } else {
            parent.appendChild(element);
        }
    }

    function setData(element, data) {
        for (const [key, value] of Object.entries(data)) {
            const name = key.replace(/([A-Z])/g, (_, c, i) => (i ? '-' : '') + c.toLowerCase());
            try {
                // Keys that start with CJK characters are kept verbatim, as some dictionaries' CSS expects.
                const cjk = /^[　-鿿豈-﫿]/.test(key);
                element.setAttribute(cjk ? `data-sc${name}` : `data-sc-${name}`, String(value));
            } catch (e) {
                // Not a valid attribute name; the dictionary CSS cannot target it anyway.
            }
        }
    }

    function setStyle(element, style) {
        for (const [property, value] of Object.entries(style)) {
            if (EM_STYLE_PROPERTIES.has(property) && typeof value === 'number') {
                element.style[property] = `${value}em`;
            } else if (Array.isArray(value)) {
                element.style[property] = value.join(' ');
            } else if (property === 'margin' || property === 'padding') {
                element.style[property] = typeof value === 'number' ? `${value}em` : value;
            } else {
                element.style[property] = value;
            }
        }
    }

    function setupLink(element, href, options) {
        if (typeof href !== 'string') return;
        element.setAttribute('href', href);
        const external = /^https?:\/\//i.test(href);
        element.dataset.external = String(external);
        element.addEventListener('click', event => {
            event.preventDefault();
            event.stopPropagation();
            if (external) {
                options.onExternalLink?.(href);
                return;
            }
            const queryStart = href.indexOf('?');
            if (queryStart < 0) return;
            const params = new URLSearchParams(href.slice(queryStart + 1));
            const query = params.get('query');
            if (query) options.onLookup?.(query, params.get('primary_reading'));
        });
    }

    // endregion

    // region Images

    function createImage(data, dictionary, options) {
        const {
            path,
            width = 100,
            height = 100,
            preferredWidth,
            preferredHeight,
            title,
            alt,
            pixelated,
            imageRendering,
            appearance,
            background,
            verticalAlign,
            border,
            borderRadius,
            sizeUnits,
        } = data;

        const hasPreferredWidth = typeof preferredWidth === 'number';
        const hasPreferredHeight = typeof preferredHeight === 'number';
        const hasDimensions = hasPreferredWidth || hasPreferredHeight
            || typeof data.width === 'number' || typeof data.height === 'number';
        const invAspectRatio = hasPreferredWidth && hasPreferredHeight ? preferredHeight / preferredWidth : height / width;
        const usedWidth = hasPreferredWidth
            ? preferredWidth
            : (hasPreferredHeight ? preferredHeight / invAspectRatio : width);

        if (options.exporting) return createExportImage(data, dictionary, options, usedWidth, sizeUnits);

        const link = document.createElement('span');
        link.className = 'gloss-image-link';
        link.dataset.hasAspectRatio = 'true';
        link.dataset.imageRendering = typeof imageRendering === 'string' ? imageRendering : (pixelated ? 'pixelated' : 'auto');
        link.dataset.appearance = typeof appearance === 'string' ? appearance : 'auto';
        link.dataset.background = typeof background === 'boolean' ? String(background) : 'true';
        if (typeof verticalAlign === 'string') link.dataset.verticalAlign = verticalAlign;
        if (typeof sizeUnits === 'string') link.dataset.sizeUnits = sizeUnits;

        const container = document.createElement('span');
        container.className = 'gloss-image-container';
        container.style.width = `${usedWidth}em`;
        if (typeof border === 'string') container.style.border = border;
        if (typeof borderRadius === 'string') container.style.borderRadius = borderRadius;
        if (typeof title === 'string') container.title = title;
        link.appendChild(container);

        const sizer = document.createElement('span');
        sizer.className = 'gloss-image-sizer';
        sizer.style.paddingTop = `${invAspectRatio * 100}%`;
        container.appendChild(sizer);

        const url = options.mediaUrl ? options.mediaUrl(dictionary, path) : '';
        const maskLayer = document.createElement('span');
        maskLayer.className = 'gloss-image-background';
        if (link.dataset.appearance === 'monochrome') maskLayer.style.setProperty('--image', `url("${url}")`);
        container.appendChild(maskLayer);

        const image = document.createElement('img');
        image.className = 'gloss-image';
        image.alt = (data.data && data.data.alt) || alt || title || '';
        image.loading = 'lazy';
        image.decoding = 'async';
        if (!hasDimensions) {
            // Without dimensions the natural size decides, limited to the popup width.
            image.addEventListener('load', () => {
                const imageWidth = Math.min(image.naturalWidth, window.innerWidth - 32);
                container.style.width = `${imageWidth}px`;
                sizer.style.paddingTop = `${(image.naturalHeight / image.naturalWidth) * 100}%`;
            }, { once: true });
        }
        image.src = url;
        container.appendChild(image);
        return link;
    }

    /** Plain markup for Anki cards, which do not have the popup's stylesheet. */
    function createExportImage(data, dictionary, options, usedWidth, sizeUnits) {
        const image = document.createElement('img');
        image.src = options.mediaUrl ? options.mediaUrl(dictionary, data.path) : '';
        image.alt = (data.data && data.data.alt) || data.alt || data.title || '';
        const unit = sizeUnits === 'em' ? 'em' : 'px';
        image.style.width = `${usedWidth}${unit}`;
        image.style.maxWidth = '100%';
        image.style.height = 'auto';
        image.style.verticalAlign = typeof data.verticalAlign === 'string' ? data.verticalAlign : 'middle';
        if (typeof data.border === 'string') image.style.border = data.border;
        if (typeof data.borderRadius === 'string') image.style.borderRadius = data.borderRadius;
        return image;
    }

    // endregion

    // region Dictionary CSS

    /** Prefixes every selector of a dictionary's styles.css so it only applies inside its own glossaries. */
    function dictionaryCss(css, dictionary) {
        if (!css) return '';
        const escaped = dictionary.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
        return scopeCss(css, `.yomitan-glossary [data-dictionary="${escaped}"]`);
    }

    function scopeCss(css, prefix) {
        const parts = [];
        let i = 0;
        while (i < css.length) {
            while (i < css.length && /\s/.test(css[i])) parts.push(css[i++]);
            if (css.startsWith('/*', i)) {
                const end = css.indexOf('*/', i + 2);
                if (end === -1) break;
                i = end + 2;
                continue;
            }
            const bracePos = css.indexOf('{', i);
            if (bracePos === -1) break;
            const selectorPart = css.slice(i, bracePos);
            const atRule = selectorPart.trim().startsWith('@');
            const selectors = atRule ? selectorPart : selectorPart.split(',').map(selector => {
                const trimmed = selector.trim();
                if (!trimmed) return '';
                return trimmed.startsWith('&') ? trimmed : `${prefix} ${trimmed}`;
            }).join(', ');
            parts.push(selectors, ' {');
            i = bracePos + 1;
            let depth = 1;
            const blockStart = i;
            while (i < css.length && depth > 0) {
                if (css[i] === '{') depth++;
                else if (css[i] === '}') depth--;
                i++;
            }
            const block = css.slice(blockStart, i - 1);
            if (block.includes('{')) {
                const { properties, nested } = splitNestedBlock(block);
                parts.push(properties);
                if (nested) parts.push(scopeCss(nested, atRule ? prefix : '&'));
            } else {
                parts.push(block);
            }
            parts.push('}');
        }
        return parts.join('');
    }

    function splitNestedBlock(block) {
        let pos = 0;
        let properties = '';
        let nested = '';
        while (pos < block.length) {
            while (pos < block.length && /\s/.test(block[pos])) pos++;
            if (pos >= block.length) break;
            const nextSemi = block.indexOf(';', pos);
            const nextBrace = block.indexOf('{', pos);
            if (nextBrace !== -1 && (nextSemi === -1 || nextBrace < nextSemi)) {
                let depth = 1;
                let end = nextBrace + 1;
                while (end < block.length && depth > 0) {
                    if (block[end] === '{') depth++;
                    else if (block[end] === '}') depth--;
                    end++;
                }
                nested += block.slice(pos, end);
                pos = end;
            } else if (nextSemi !== -1) {
                properties += block.slice(pos, nextSemi + 1);
                pos = nextSemi + 1;
            } else {
                properties += block.slice(pos);
                break;
            }
        }
        return { properties, nested };
    }

    // endregion

    // region Furigana (Yomitan ext/js/language/ja/japanese.js)

    function toHiragana(text) {
        return text.replace(/[ァ-ヶ]/g, ch => String.fromCharCode(ch.charCodeAt(0) - 0x60));
    }

    function segment(text, reading) {
        return { text, reading };
    }

    function kanaSegments(text, reading) {
        const segments = [];
        let start = 0;
        let state = reading[0] === text[0];
        for (let i = 1; i < text.length; ++i) {
            const newState = reading[i] === text[i];
            if (state === newState) continue;
            segments.push(segment(text.substring(start, i), state ? '' : reading.substring(start, i)));
            state = newState;
            start = i;
        }
        segments.push(segment(text.substring(start), state ? '' : reading.substring(start)));
        return segments;
    }

    function segmentize(reading, readingNormalized, groups, groupsStart) {
        const groupCount = groups.length - groupsStart;
        if (groupCount <= 0) return reading.length === 0 ? [] : null;

        const group = groups[groupsStart];
        const { isKana, text } = group;
        if (isKana) {
            if (group.textNormalized !== null && readingNormalized.startsWith(group.textNormalized)) {
                const segments = segmentize(
                    reading.substring(text.length),
                    readingNormalized.substring(text.length),
                    groups,
                    groupsStart + 1,
                );
                if (segments !== null) {
                    if (reading.startsWith(text)) {
                        segments.unshift(segment(text, ''));
                    } else {
                        segments.unshift(...kanaSegments(text, reading));
                    }
                    return segments;
                }
            }
            return null;
        }

        let result = null;
        for (let i = reading.length; i >= text.length; --i) {
            const segments = segmentize(reading.substring(i), readingNormalized.substring(i), groups, groupsStart + 1);
            if (segments !== null) {
                // More than one way to split the tail: ambiguous.
                if (result !== null) return null;
                segments.unshift(segment(text, reading.substring(0, i)));
                result = segments;
            }
            // The last non-kana group can only be split one way.
            if (groupCount === 1) break;
        }
        return result;
    }

    function furiganaSegments(expression, reading) {
        if (!reading || reading === expression) return [[expression, '']];
        const groups = (expression.match(KANJI_SEGMENT_PATTERN) || []).map(text => {
            const isKana = !KANJI_PATTERN.test(text[0]);
            return { isKana, text, textNormalized: isKana ? toHiragana(text) : null };
        });
        const segments = segmentize(reading, toHiragana(reading), groups, 0);
        return segments !== null ? segments.map(s => [s.text, s.reading]) : [[expression, reading]];
    }

    function furigana(parent, expression, reading) {
        for (const [text, ruby] of furiganaSegments(expression, reading)) {
            if (ruby) {
                const element = document.createElement('ruby');
                element.appendChild(document.createTextNode(text));
                const rt = document.createElement('rt');
                rt.textContent = ruby;
                element.appendChild(rt);
                parent.appendChild(element);
            } else {
                parent.appendChild(document.createTextNode(text));
            }
        }
    }

    // endregion

    // region Pitch accent (Yomitan ext/js/display/pronunciation-generator.js)

    const DIACRITICS = (() => {
        const kana = 'うゔ-かが-きぎ-くぐ-けげ-こご-さざ-しじ-すず-せぜ-そぞ-ただ-ちぢ-つづ-てで-とど-はばぱひびぴふぶぷへべぺほぼぽ'
            + 'ワヷ-ヰヸ-ウヴ-ヱヹ-ヲヺ-カガ-キギ-クグ-ケゲ-コゴ-サザ-シジ-スズ-セゼ-ソゾ-タダ-チヂ-ツヅ-テデ-トド-ハバパヒビピフブプヘベペホボポ';
        const mapping = new Map();
        for (let i = 0; i < kana.length; i += 3) {
            mapping.set(kana[i + 1], kana[i]);
            if (kana[i + 2] !== '-') mapping.set(kana[i + 2], kana[i]);
        }
        return mapping;
    })();

    function morae(text) {
        const result = [];
        for (const c of text) {
            if (SMALL_KANA.has(c) && result.length > 0) {
                result[result.length - 1] += c;
            } else {
                result.push(c);
            }
        }
        return result;
    }

    function isMoraHigh(index, position) {
        if (typeof position === 'string') return position[index] === 'H';
        switch (position) {
            case 0: return index > 0;
            case 1: return index < 1;
            default: return index > 0 && index < position;
        }
    }

    function downsteps(position) {
        if (typeof position !== 'string') return [position];
        const result = [];
        for (let i = 1; i < position.length; i++) {
            if (position[i - 1] === 'H' && position[i] === 'L') result.push(i);
        }
        if (!result.length) result.push(position.startsWith('L') ? 0 : -1);
        return result;
    }

    function pitchCategory(reading, position, rules) {
        const downstep = downsteps(position)[0];
        if (downstep === 0) return 'heiban';
        const verbOrAdjective = (rules || '').split(' ').some(rule => rule.startsWith('v') || rule.startsWith('adj-i'));
        if (verbOrAdjective) return downstep > 0 ? 'kifuku' : null;
        if (downstep === 1) return 'atamadaka';
        if (downstep > 1) return downstep >= morae(reading).length ? 'odaka' : 'nakadaka';
        return null;
    }

    function pitchElement(reading, position, nasal = [], devoice = []) {
        const moraList = morae(reading);
        const nasalSet = new Set(nasal);
        const devoiceSet = new Set(devoice);
        const container = document.createElement('span');
        container.className = 'pronunciation-text';
        moraList.forEach((mora, i) => {
            const span = document.createElement('span');
            span.className = 'pronunciation-mora';
            span.dataset.pitch = isMoraHigh(i, position) ? 'high' : 'low';
            span.dataset.pitchNext = isMoraHigh(i + 1, position) ? 'high' : 'low';
            if (nasalSet.has(i + 1)) {
                span.dataset.nasal = 'true';
                const group = document.createElement('span');
                group.className = 'pronunciation-character-group';
                const base = document.createElement('span');
                base.textContent = DIACRITICS.get(mora[0]) || mora[0];
                const diacritic = document.createElement('span');
                diacritic.className = 'pronunciation-nasal-diacritic';
                diacritic.textContent = '゚';
                const indicator = document.createElement('span');
                indicator.className = 'pronunciation-nasal-indicator';
                group.append(base, diacritic, indicator);
                span.appendChild(group);
                if (mora.length > 1) span.appendChild(document.createTextNode(mora.slice(1)));
            } else {
                span.appendChild(document.createTextNode(mora));
            }
            if (devoiceSet.has(i + 1)) {
                span.dataset.devoice = 'true';
                const indicator = document.createElement('span');
                indicator.className = 'pronunciation-devoice-indicator';
                span.appendChild(indicator);
            }
            const line = document.createElement('span');
            line.className = 'pronunciation-mora-line';
            span.appendChild(line);
            container.appendChild(span);
        });
        return container;
    }

    // endregion

    return {
        renderGlossary,
        dictionaryCss,
        furigana,
        furiganaSegments,
        pitchElement,
        pitchCategory,
        downsteps,
        morae,
    };
})();
