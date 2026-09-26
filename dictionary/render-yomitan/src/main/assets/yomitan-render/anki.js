/*
 * Anki note markup in Yomitan's format: pitch accent text, graphs and positions, pitch categories, inflected furigana,
 * and glossaries with inline styles.
 *
 * Derived from Yomitan (Copyright (c) 2023-2026 Yomitan Authors, Copyright (c) 2021-2022 Yomichan Authors):
 * ext/js/display/pronunciation-generator.js, ext/js/dom/css-style-applier.js, ext/js/language/ja/japanese.js,
 * ext/js/templates/anki-template-renderer.js, and the style data ext/data/pronunciation-style.json and
 * ext/data/structured-content-style.json. The Jidoujisho-style graph comes from Yomitan's port of
 * https://github.com/lrorpilla/jidoujisho. Modified for Screenlate.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Public API (window.YomitanAnki), used by the popup's note builder; needs window.YomitanRender:
 *   pronunciation(format, reading, value, nasal, devoice)  format: text | graph | graph-jj | position
 *   pitchCategory(reading, value, wordClasses)             heiban / atamadaka / nakadaka / odaka / kifuku / null
 *   distributeFuriganaInflected(term, reading, source)     [[text, reading], ...] for an inflected source text
 *   glossaryHtml(content, dictionary, options)             glossary markup with inline styles
 *   glossaryPlain(content, dictionary)                     glossary text for {glossary-plain}
 *   glossaryCss(css)                                       a dictionary's styles.css scoped to .yomitan-glossary
 */
'use strict';
window.YomitanAnki = (() => {
    const PRONUNCIATION_STYLE_DATA = [{"selectors": [".pronunciation-downstep-notation"], "styles": [["display", "inline"]]}, {"selectors": [".pronunciation-text"], "styles": [["display", "inline"]]}, {"selectors": [".pronunciation-mora"], "styles": [["display", "inline-block"], ["position", "relative"]]}, {"selectors": [".pronunciation-mora-line"], "styles": [["border-color", "currentColor"]]}, {"selectors": [".pronunciation-mora[data-pitch=high]>.pronunciation-mora-line"], "styles": [["display", "block"], ["user-select", "none"], ["pointer-events", "none"], ["position", "absolute"], ["top", "0.1em"], ["left", "0"], ["right", "0"], ["height", "0"], ["border-top-width", "0.1em"], ["border-top-style", "solid"]]}, {"selectors": [".pronunciation-mora[data-pitch=high][data-pitch-next=low]>.pronunciation-mora-line"], "styles": [["right", "-0.1em"], ["height", "0.4em"], ["border-right-width", "0.1em"], ["border-right-style", "solid"]]}, {"selectors": [".pronunciation-mora[data-pitch=high][data-pitch-next=low]"], "styles": [["padding-right", "0.1em"], ["margin-right", "0.1em"]]}, {"selectors": [".pronunciation-devoice-indicator"], "styles": [["display", "block"], ["position", "absolute"], ["left", "50%"], ["top", "50%"], ["width", "1.125em"], ["height", "1.125em"], ["border-radius", "50%"], ["box-sizing", "border-box"], ["z-index", "1"], ["transform", "translate(-50%, -50%)"], ["border", "1.5px dotted #c83c28"]]}, {"selectors": [".pronunciation-nasal-indicator"], "styles": [["display", "block"], ["position", "absolute"], ["right", "-0.125em"], ["top", "0.125em"], ["width", "0.375em"], ["height", "0.375em"], ["border-radius", "50%"], ["box-sizing", "border-box"], ["z-index", "1"], ["border", "1.5px solid #c83c28"]]}, {"selectors": [".pronunciation-nasal-diacritic"], "styles": [["position", "absolute"], ["width", "0"], ["height", "0"], ["opacity", "0"]]}, {"selectors": [".pronunciation-character"], "styles": [["display", "inline"]]}, {"selectors": [".pronunciation-character-group"], "styles": [["display", "inline-block"], ["position", "relative"]]}, {"selectors": [".pronunciation-graph"], "styles": [["display", "inline-block"], ["vertical-align", "middle"], ["height", "1.5em"]]}, {"selectors": [".pronunciation-graph-line", ".pronunciation-graph-line-tail"], "styles": [["fill", "none"], ["stroke-width", "5"], ["stroke", "currentColor"]]}, {"selectors": [".pronunciation-graph-line-tail"], "styles": [["stroke-dasharray", "5 5"]]}, {"selectors": [".pronunciation-graph-dot"], "styles": [["stroke-width", "5"], ["fill", "currentColor"], ["stroke", "currentColor"]]}, {"selectors": [".pronunciation-graph-dot-downstep1"], "styles": [["fill", "none"], ["stroke-width", "5"], ["stroke", "currentColor"]]}, {"selectors": [".pronunciation-graph-dot-downstep2"], "styles": [["fill", "currentColor"]]}, {"selectors": [".pronunciation-graph-triangle"], "styles": [["fill", "none"], ["stroke-width", "5"], ["stroke", "currentColor"]]}];
    const STRUCTURED_CONTENT_STYLE_DATA = [{"selectors": [".gloss-image-container"], "styles": [["display", "inline-block"], ["white-space", "nowrap"], ["max-width", "100%"], ["max-height", "100vh"], ["position", "relative"], ["vertical-align", "top"], ["line-height", "0"], ["overflow", "hidden"], ["font-size", "1px"]]}, {"selectors": [".gloss-image-link"], "styles": [["cursor", "inherit"], ["display", "inline-block"], ["position", "relative"], ["line-height", "1"], ["max-width", "100%"], ["color", "inherit"]]}, {"selectors": [".gloss-image-container-overlay"], "styles": [["position", "absolute"], ["left", "0"], ["top", "0"], ["width", "100%"], ["height", "100%"], ["font-size", "calc(1em * var(--font-size-no-units))"], ["line-height", "var(--line-height)"], ["display", "table"], ["table-layout", "fixed"], ["white-space", "normal"], ["color", "var(--text-color-light3)"]]}, {"selectors": [".gloss-image-link[data-has-image=true][data-image-load-state=load-error] .gloss-image-container-overlay::after"], "styles": [["content", "'Image failed to load'"], ["display", "table-cell"], ["width", "100%"], ["height", "100%"], ["vertical-align", "middle"], ["text-align", "center"], ["padding", "0.25em"]]}, {"selectors": [".gloss-image-background"], "styles": [["--image", "none"], ["position", "absolute"], ["left", "0"], ["top", "0"], ["width", "100%"], ["height", "100%"], ["-webkit-mask-repeat", "no-repeat"], ["-webkit-mask-position", "center center"], ["-webkit-mask-mode", "alpha"], ["-webkit-mask-size", "contain"], ["-webkit-mask-image", "var(--image)"], ["mask-repeat", "no-repeat"], ["mask-position", "center center"], ["mask-mode", "alpha"], ["mask-size", "contain"], ["mask-image", "var(--image)"], ["background-color", "currentColor"]]}, {"selectors": [".gloss-image"], "styles": [["display", "inline-block"], ["vertical-align", "top"], ["object-fit", "contain"], ["border", "none"], ["outline", "none"]]}, {"selectors": [".gloss-image-link[data-has-aspect-ratio=true] .gloss-image"], "styles": [["position", "absolute"], ["left", "0"], ["top", "0"], ["width", "100%"], ["height", "100%"]]}, {"selectors": [".gloss-image-link[data-image-rendering=pixelated] .gloss-image", ".gloss-image-link[data-image-rendering=pixelated] .gloss-image-background"], "styles": [["image-rendering", "auto"], ["image-rendering", "-moz-crisp-edges"], ["image-rendering", "-webkit-optimize-contrast"], ["image-rendering", "pixelated"], ["image-rendering", "crisp-edges"]]}, {"selectors": [".gloss-image-link[data-image-rendering=crisp-edges] .gloss-image", ".gloss-image-link[data-image-rendering=crisp-edges] .gloss-image-background"], "styles": [["image-rendering", "auto"], ["image-rendering", "-moz-crisp-edges"], ["image-rendering", "-webkit-optimize-contrast"], ["image-rendering", "crisp-edges"]]}, {"selectors": [":root[data-browser=firefox] .gloss-image-link[data-image-rendering=crisp-edges] .gloss-image", ":root[data-browser=firefox] .gloss-image-link[data-image-rendering=crisp-edges] .gloss-image-background", ":root[data-browser=firefox-mobile] .gloss-image-link[data-image-rendering=crisp-edges] .gloss-image", ":root[data-browser=firefox-mobile] .gloss-image-link[data-image-rendering=crisp-edges] .gloss-image-background"], "styles": [["image-rendering", "auto"]]}, {"selectors": [".gloss-image-link[data-has-aspect-ratio=true] .gloss-image-sizer"], "styles": [["display", "inline-block"], ["width", "0"], ["vertical-align", "top"], ["font-size", "0"]]}, {"selectors": [".gloss-image-link-text"], "styles": [["display", "none"], ["line-height", "var(--line-height)"]]}, {"selectors": [".gloss-image-link-text::before"], "styles": [["content", "'['"]]}, {"selectors": [".gloss-image-link-text::after"], "styles": [["content", "']'"]]}, {"selectors": [".gloss-image-description"], "styles": [["display", "block"], ["white-space", "pre-line"]]}, {"selectors": [".gloss-image-link[data-appearance=monochrome] .gloss-image"], "styles": [["--shadow-settings", "0 0 0.01px var(--text-color)"], ["filter", "grayscale(1) opacity(0.5) drop-shadow(var(--shadow-settings)) drop-shadow(var(--shadow-settings)) saturate(1000%) brightness(1000%)"], ["opacity", "0"]]}, {"selectors": [".gloss-image-link[data-size-units=em] .gloss-image-container"], "styles": [["font-size", "1em"]]}, {"selectors": [".gloss-image-link[data-vertical-align=baseline]"], "styles": [["vertical-align", "baseline"]]}, {"selectors": [".gloss-image-link[data-vertical-align=sub]"], "styles": [["vertical-align", "sub"]]}, {"selectors": [".gloss-image-link[data-vertical-align=super]"], "styles": [["vertical-align", "super"]]}, {"selectors": [".gloss-image-link[data-vertical-align=text-top]"], "styles": [["vertical-align", "top"]]}, {"selectors": [".gloss-image-link[data-vertical-align=text-bottom]"], "styles": [["vertical-align", "bottom"]]}, {"selectors": [".gloss-image-link[data-vertical-align=middle]"], "styles": [["vertical-align", "middle"]]}, {"selectors": [".gloss-image-link[data-vertical-align=top]"], "styles": [["vertical-align", "top"]]}, {"selectors": [".gloss-image-link[data-vertical-align=bottom]"], "styles": [["vertical-align", "bottom"]]}, {"selectors": [".gloss-image-link[data-collapsed=true]", ":root[data-glossary-layout-mode^=compact] .gloss-image-link[data-collapsible=true]"], "styles": [["vertical-align", "baseline"]]}, {"selectors": [".gloss-image-link[data-collapsed=true] .gloss-image-container", ":root[data-glossary-layout-mode^=compact] .gloss-image-link[data-collapsible=true] .gloss-image-container"], "styles": [["display", "none"], ["position", "absolute"], ["left", "0"], ["top", "100%"], ["z-index", "1"]]}, {"selectors": [".entry:nth-last-of-type(1):not(:nth-of-type(1)) .gloss-image-link[data-collapsed=true] .gloss-image-container", ":root[data-glossary-layout-mode^=compact] .entry:nth-last-of-type(1):not(:nth-of-type(1)) .gloss-image-link[data-collapsible=true] .gloss-image-container", ":root[data-glossary-layout-mode^=compact] .definition-item:nth-last-of-type(1) .gloss-image-link[data-collapsible=true] .gloss-image-container"], "styles": [["bottom", "100%"], ["top", "auto"]]}, {"selectors": [".gloss-image-link[data-collapsed=true]:hover .gloss-image-container", ".gloss-image-link[data-collapsed=true]:focus .gloss-image-container", ":root[data-glossary-layout-mode^=compact] .gloss-image-link[data-collapsible=true]:hover .gloss-image-container", ":root[data-glossary-layout-mode^=compact] .gloss-image-link[data-collapsible=true]:focus .gloss-image-container"], "styles": [["display", "block"]]}, {"selectors": [".gloss-image-link[data-collapsed=true] .gloss-image-link-text", ":root[data-glossary-layout-mode^=compact] .gloss-image-link[data-collapsible=true] .gloss-image-link-text"], "styles": [["display", "inline"]]}, {"selectors": [".gloss-image-link[data-collapsed=true]~.gloss-image-description", ":root[data-glossary-layout-mode^=compact] .gloss-image-description"], "styles": [["display", "inline"]]}, {"selectors": [".gloss-link-external-icon"], "styles": [["display", "none"]]}, {"selectors": [".gloss-sc-table-container"], "styles": [["display", "block"]]}, {"selectors": [".gloss-sc-table"], "styles": [["table-layout", "auto"], ["border-collapse", "collapse"]]}, {"selectors": [".gloss-sc-thead", ".gloss-sc-tfoot", ".gloss-sc-th"], "styles": [["font-weight", "bold"]]}, {"selectors": [".gloss-sc-th", ".gloss-sc-td"], "styles": [["border-style", "solid"], ["padding", "0.25em"], ["vertical-align", "top"], ["border-width", "1px"], ["border-color", "currentColor"]]}, {"selectors": [".gloss-image-link:not([data-appearance=monochrome]) .gloss-image-background"], "styles": [["display", "none"]]}];
    const SVG_NS = 'http://www.w3.org/2000/svg';
    const SMALL_KANA = new Set('ぁぃぅぇぉゃゅょゎァィゥェォャュョヮ');
    // Structured content keeps its data-sc-* attributes: dictionary styles select on them.
    const STRUCTURED_DATASET_KEEP = /^sc([^a-z]|$)/;

    // region Inline styles (Yomitan ext/js/dom/css-style-applier.js)

    function compileRules(raw) {
        return raw.map(({ selectors, styles }) => ({
            selectors: selectors.join(','),
            cssText: styles.map(([property, value]) => `${property}:${value};`).join(''),
        }));
    }

    const PRONUNCIATION_RULES = compileRules(PRONUNCIATION_STYLE_DATA);
    const STRUCTURED_RULES = compileRules(STRUCTURED_CONTENT_STYLE_DATA);

    function selectorMightMatch(selectors, classList) {
        for (const item of classList) {
            const prefixed = `.${item}`;
            let start = 0;
            while (true) {
                const index = selectors.indexOf(prefixed, start);
                if (index < 0) break;
                start = index + prefixed.length;
                if (start >= selectors.length || !/[0-9a-zA-Z-_]/.test(selectors[start])) return true;
            }
        }
        return false;
    }

    /**
     * Anki cards do not have the popup's stylesheet: styles selected by class are written into style attributes,
     * then classes and data attributes are dropped, as Yomitan does for its notes.
     */
    function inlineStyles(root, rules, keepDataset) {
        const elements = [...root.querySelectorAll('*')];
        const updates = [];
        for (const element of elements) {
            const className = element.getAttribute('class');
            if (!className) continue;
            const classList = className.split(/[\t\r\n\f ]+/).filter(Boolean);
            let cssText = '';
            for (const rule of rules) {
                if (!selectorMightMatch(rule.selectors, classList)) continue;
                try {
                    if (element.matches(rule.selectors)) cssText += rule.cssText;
                } catch (e) {
                    // Unsupported selector: skip it, like Yomitan.
                }
            }
            const own = element.getAttribute('style') || '';
            updates.push([element, cssText + own]);
        }
        for (const [element, style] of updates) {
            element.removeAttribute('class');
            if (style) element.setAttribute('style', style); else element.removeAttribute('style');
        }
        for (const element of elements) {
            if (!element.dataset) continue;
            for (const key of Object.keys(element.dataset)) {
                if (keepDataset && keepDataset.test(key)) continue;
                delete element.dataset[key];
            }
        }
    }

    function outerHtml(root, rules, keepDataset) {
        const container = document.createElement('div');
        container.appendChild(root);
        inlineStyles(container, rules, keepDataset);
        return container.innerHTML;
    }

    // endregion

    // region Japanese helpers (Yomitan ext/js/language/ja/japanese.js)

    function morae(text) {
        const result = [];
        for (const c of text) {
            if (SMALL_KANA.has(c) && result.length > 0) result[result.length - 1] += c;
            else result.push(c);
        }
        return result;
    }

    function isMoraPitchHigh(index, value) {
        if (typeof value === 'string') return value[index] === 'H';
        switch (value) {
            case 0: return index > 0;
            case 1: return index < 1;
            default: return index > 0 && index < value;
        }
    }

    function downstepPositions(pattern) {
        const result = [];
        for (let i = 1; i < pattern.length; i++) {
            if (pattern[i - 1] === 'H' && pattern[i] === 'L') result.push(i);
        }
        if (result.length === 0) result.push(pattern.startsWith('L') ? 0 : -1);
        return result;
    }

    function isNonNounVerbOrAdjective(wordClasses) {
        let verbOrAdjective = false;
        let suru = false;
        let noun = false;
        for (const wordClass of wordClasses) {
            switch (wordClass) {
                case 'v1': case 'v5': case 'vk': case 'vz': case 'adj-i': verbOrAdjective = true; break;
                case 'vs': verbOrAdjective = true; suru = true; break;
                case 'n': noun = true; break;
                default: break;
            }
        }
        return verbOrAdjective && !(suru && noun);
    }

    function pitchCategory(reading, value, wordClasses) {
        const position = typeof value === 'string' ? downstepPositions(value)[0] : value;
        if (position === 0) return 'heiban';
        if (isNonNounVerbOrAdjective(wordClasses)) return position > 0 ? 'kifuku' : null;
        if (position === 1) return 'atamadaka';
        if (position > 1) return position >= morae(reading).length ? 'odaka' : 'nakadaka';
        return null;
    }

    function toHiragana(text) {
        return text.replace(/[ァ-ヶ]/g, ch => String.fromCharCode(ch.charCodeAt(0) - 0x60));
    }

    function stemLength(a, b) {
        const chars1 = [...a];
        const chars2 = [...b];
        let length = 0;
        let i = 0;
        while (i < chars1.length && i < chars2.length && chars1[i] === chars2[i]) {
            length += chars1[i].length;
            i++;
        }
        return length;
    }

    /** Furigana for text that may be an inflected form of the term, e.g. 食べた for 食べる/たべる. */
    function distributeFuriganaInflected(term, reading, source) {
        const termNormalized = toHiragana(term);
        const readingNormalized = toHiragana(reading);
        const sourceNormalized = toHiragana(source);
        let mainText = term;
        let stem = stemLength(termNormalized, sourceNormalized);
        const readingStem = stemLength(readingNormalized, sourceNormalized);
        if (readingStem > 0 && readingStem >= stem) {
            mainText = reading;
            stem = readingStem;
            reading = `${source.substring(0, stem)}${reading.substring(stem)}`;
        }
        const segments = [];
        if (stem > 0) {
            mainText = `${source.substring(0, stem)}${mainText.substring(stem)}`;
            let consumed = 0;
            for (const [text, ruby] of YomitanRender.furiganaSegments(mainText, reading)) {
                const start = consumed;
                consumed += text.length;
                if (consumed < stem) {
                    segments.push([text, ruby]);
                } else if (consumed === stem) {
                    segments.push([text, ruby]);
                    break;
                } else {
                    if (start < stem) segments.push([mainText.substring(start, stem), '']);
                    break;
                }
            }
        }
        if (stem < source.length) {
            const remainder = source.substring(stem);
            const last = segments[segments.length - 1];
            if (last && last[1].length === 0) last[0] += remainder;
            else segments.push([remainder, '']);
        }
        return segments;
    }

    // endregion

    // region Pronunciation (Yomitan ext/js/display/pronunciation-generator.js)

    function pronunciationText(moraList, value, nasal, devoice) {
        const nasalSet = new Set(nasal);
        const devoiceSet = new Set(devoice);
        const container = document.createElement('span');
        container.className = 'pronunciation-text';
        moraList.forEach((mora, i) => {
            const n1 = document.createElement('span');
            n1.className = 'pronunciation-mora';
            n1.dataset.position = `${i}`;
            n1.dataset.pitch = isMoraPitchHigh(i, value) ? 'high' : 'low';
            n1.dataset.pitchNext = isMoraPitchHigh(i + 1, value) ? 'high' : 'low';
            const characterNodes = [];
            for (const character of mora) {
                const n2 = document.createElement('span');
                n2.className = 'pronunciation-character';
                n2.textContent = character;
                n1.appendChild(n2);
                characterNodes.push(n2);
            }
            if (devoiceSet.has(i + 1)) {
                n1.dataset.devoice = 'true';
                const n3 = document.createElement('span');
                n3.className = 'pronunciation-devoice-indicator';
                n1.appendChild(n3);
            }
            if (nasalSet.has(i + 1) && characterNodes.length > 0) {
                n1.dataset.nasal = 'true';
                const group = document.createElement('span');
                group.className = 'pronunciation-character-group';
                const n2 = characterNodes[0];
                const base = YomitanRender.undakuten ? YomitanRender.undakuten(n2.textContent) : null;
                if (base) n2.textContent = base;
                let n3 = document.createElement('span');
                n3.className = 'pronunciation-nasal-diacritic';
                n3.textContent = '゚';
                group.appendChild(n3);
                n3 = document.createElement('span');
                n3.className = 'pronunciation-nasal-indicator';
                group.appendChild(n3);
                n2.parentNode.replaceChild(group, n2);
                group.insertBefore(n2, group.firstChild);
            }
            const line = document.createElement('span');
            line.className = 'pronunciation-mora-line';
            n1.appendChild(line);
            container.appendChild(n1);
        });
        return container;
    }

    function svgElement(name, attributes) {
        const node = document.createElementNS(SVG_NS, name);
        for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, `${value}`);
        return node;
    }

    function pronunciationGraph(moraList, value) {
        const ii = moraList.length;
        const svg = svgElement('svg', {
            xmlns: SVG_NS,
            class: 'pronunciation-graph',
            focusable: 'false',
            viewBox: `0 0 ${50 * (ii + 1)} 100`,
        });
        if (ii <= 0) return svg;
        const path1 = svgElement('path', {});
        const path2 = svgElement('path', {});
        svg.append(path1, path2);
        const points = [];
        for (let i = 0; i < ii; ++i) {
            const high = isMoraPitchHigh(i, value);
            const highNext = isMoraPitchHigh(i + 1, value);
            const x = i * 50 + 25;
            const y = high ? 25 : 75;
            if (high && !highNext) {
                svg.appendChild(svgElement('circle', { class: 'pronunciation-graph-dot-downstep1', cx: x, cy: y, r: '15' }));
                svg.appendChild(svgElement('circle', { class: 'pronunciation-graph-dot-downstep2', cx: x, cy: y, r: '5' }));
            } else {
                svg.appendChild(svgElement('circle', { class: 'pronunciation-graph-dot', cx: x, cy: y, r: '15' }));
            }
            points.push(`${x} ${y}`);
        }
        path1.setAttribute('class', 'pronunciation-graph-line');
        path1.setAttribute('d', `M${points.join(' L')}`);
        points.splice(0, ii - 1);
        const x = ii * 50 + 25;
        const y = isMoraPitchHigh(ii, value) ? 25 : 75;
        svg.appendChild(svgElement('path', {
            class: 'pronunciation-graph-triangle',
            d: 'M0 13 L15 -13 L-15 -13 Z',
            transform: `translate(${x},${y})`,
        }));
        points.push(`${x} ${y}`);
        path2.setAttribute('class', 'pronunciation-graph-line-tail');
        path2.setAttribute('d', `M${points.join(' L')}`);
        return svg;
    }

    function patternJJ(moraCount, value) {
        if (typeof value === 'string') {
            const extended = value + value[value.length - 1];
            return extended.slice(0, moraCount + 1);
        }
        if (moraCount >= 1) {
            if (value === 0) return `L${'H'.repeat(moraCount)}`;
            if (value === 1) return `H${'L'.repeat(moraCount)}`;
            if (value >= 2) return `LH${'H'.repeat(value - 2)}${'L'.repeat(moraCount - value + 1)}`;
        }
        return '';
    }

    // Jidoujisho-style graph: https://github.com/lrorpilla/jidoujisho (GPL-3.0), via Yomitan.
    function pronunciationGraphJJ(moraList, value) {
        const pattern = patternJJ(moraList.length, value);
        const positions = Math.max(moraList.length, pattern.length);
        const stepWidth = 35;
        const margin = 16;
        const width = Math.max(0, (positions - 1) * stepWidth + margin * 2);
        const svg = svgElement('svg', {
            xmlns: SVG_NS,
            width: `${width * (3 / 5)}px`,
            height: '45px',
            viewBox: `0 0 ${width} 75`,
        });
        if (moraList.length <= 0) return svg;
        moraList.forEach((mora, i) => {
            const x = margin + i * stepWidth - 11;
            if (mora.length === 1) {
                const text = svgElement('text', { x, y: '67.5', style: 'font-size:20px;font-family:sans-serif;fill:currentColor;' });
                text.textContent = mora;
                svg.appendChild(text);
            } else {
                const first = svgElement('text', { x: x - 5, y: '67.5', style: 'font-size:20px;font-family:sans-serif;fill:currentColor;' });
                first.textContent = mora[0];
                const second = svgElement('text', { x: x + 12, y: '67.5', style: 'font-size:14px;font-family:sans-serif;fill:currentColor;' });
                second.textContent = mora[1];
                svg.append(first, second);
            }
        });
        const circles = [];
        const paths = [];
        let previous = [-1, -1];
        for (let i = 0; i < pattern.length; i++) {
            const x = margin + i * stepWidth;
            const y = pattern[i] === 'H' ? 5 : (pattern[i] === 'L' ? 30 : 0);
            circles.push(i >= moraList.length
                ? svgElement('circle', { r: '4', cx: x + 4, cy: y, stroke: 'currentColor', 'stroke-width': '2', fill: 'none' })
                : svgElement('circle', { r: '5', cx: x, cy: y, style: 'opacity:1;fill:currentColor;' }));
            if (i > 0) {
                const delta = previous[1] === y ? `${stepWidth},0` : (previous[1] < y ? `${stepWidth},25` : `${stepWidth},-25`);
                paths.push(svgElement('path', {
                    d: `m ${previous[0]},${previous[1]} ${delta}`,
                    style: 'fill:none;stroke:currentColor;stroke-width:1.5;',
                }));
            }
            previous = [x, y];
        }
        svg.append(...paths, ...circles);
        return svg;
    }

    function pronunciationPosition(value) {
        const positions = typeof value === 'string' ? downstepPositions(value) : value;
        const text = `${positions}`;
        const n1 = document.createElement('span');
        n1.className = 'pronunciation-downstep-notation';
        n1.dataset.downstepPosition = text;
        for (const [className, content] of [
            ['pronunciation-downstep-notation-prefix', '['],
            ['pronunciation-downstep-notation-number', text],
            ['pronunciation-downstep-notation-suffix', ']'],
        ]) {
            const n2 = document.createElement('span');
            n2.className = className;
            n2.textContent = content;
            n1.appendChild(n2);
        }
        return n1;
    }

    /**
     * One pitch accent as Anki markup with inline styles.
     *
     * @param format text | graph | graph-jj | position
     */
    function pronunciation(format, reading, value, nasal = [], devoice = []) {
        if (!reading) return '';
        const moraList = morae(reading);
        let node;
        switch (format) {
            case 'text': node = pronunciationText(moraList, value, nasal, devoice); break;
            case 'graph': node = pronunciationGraph(moraList, value); break;
            case 'graph-jj': node = pronunciationGraphJJ(moraList, value); break;
            case 'position': node = pronunciationPosition(value); break;
            default: return '';
        }
        return outerHtml(node, PRONUNCIATION_RULES, null);
    }

    // endregion

    /** Glossary markup for Anki: structured content with its class styles inlined. */
    function glossaryHtml(content, dictionary, options) {
        const container = document.createElement('div');
        YomitanRender.renderGlossary(container, content, dictionary, Object.assign({}, options, { exporting: true }));
        inlineStyles(container, STRUCTURED_RULES, STRUCTURED_DATASET_KEEP);
        return container.innerHTML;
    }

    /** Plain text of a glossary for `{glossary-plain}`: line breaks become `<br>`, markup is dropped. */
    function glossaryPlain(content, dictionary) {
        const items = Array.isArray(content) ? content : [content];
        const lines = [];
        for (const item of items) {
            if (typeof item === 'string') {
                lines.push(escapeHtml(item));
            } else if (item && item.type === 'text') {
                lines.push(escapeHtml(item.text || ''));
            } else if (item && item.type === 'structured-content') {
                const container = document.createElement('div');
                YomitanRender.renderGlossary(container, [item], dictionary, { exporting: true });
                for (const rt of container.querySelectorAll('rt')) rt.textContent = `[${rt.textContent}]`;
                const text = container.innerText || container.textContent || '';
                lines.push(escapeHtml(text.replace(/\n+/g, '\n').trim()).replace(/\n/g, '<br>'));
            }
        }
        return lines.join('<br>');
    }

    function escapeHtml(text) {
        return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    /** A dictionary's styles.css scoped to `.yomitan-glossary`, for markers that show one dictionary's entry. */
    function glossaryCss(css) {
        return YomitanRender.scopeCss ? YomitanRender.scopeCss(css || '', '.yomitan-glossary') : '';
    }

    return {
        pronunciation,
        pitchCategory,
        isNonNounVerbOrAdjective,
        distributeFuriganaInflected,
        downstepPositions,
        morae,
        glossaryHtml,
        glossaryPlain,
        glossaryCss,
    };
})();
