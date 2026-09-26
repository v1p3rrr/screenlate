'use strict';

/*
 * Popup page. Kotlin drives it through Popup.render / update / push / setStyles with JSON states:
 *
 * {
 *   theme: 'light' | 'dark',
 *   pending: boolean,              OCR is still refining the text
 *   engine: string,                OCR engine label, empty to hide
 *   source: { text, matched },     lookup text and the length of its matched prefix (code points)
 *   results: [LookupResult],       see dictionary.api.model.LookupResult
 *   message: string,               shown instead of results
 *   labels: { ... }                localized strings
 * }
 *
 * Glossary rendering comes from window.YomitanRender (yomitan-render/render.js) when it is present.
 */
const Popup = (() => {
    const backButton = document.getElementById('back');
    const source = document.getElementById('source');
    const engine = document.getElementById('engine');
    const spinner = document.getElementById('spinner');
    const content = document.getElementById('content');
    const dictionaryStyles = document.getElementById('dictionary-styles');
    const renderer = window.YomitanRender || null;
    const HOLD_MS = 450;
    const KANJI_STATS = ['strokes', 'grade', 'jlpt', 'freq'];
    const ICONS = {
        add: '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
        audio: '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M4 9v6h4l5 4V5L8 9H4z" fill="currentColor"/><path d="M16 8.5a5 5 0 0 1 0 7M18.5 6a8.5 8.5 0 0 1 0 12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
    };

    const history = [];
    let current = null;
    let drawnKey = null;
    let styles = [];
    let actions = { anki: false, audio: false };

    document.getElementById('close').addEventListener('click', () => ScreenlateBridge.onClose());
    backButton.addEventListener('click', back);

    const renderOptions = {
        mediaUrl: (dictionary, path) =>
            `/media?d=${encodeURIComponent(dictionary)}&p=${encodeURIComponent(path)}`,
        onLookup: (query, primaryReading) => ScreenlateBridge.onLookup(query, primaryReading || ''),
        onExternalLink: url => ScreenlateBridge.onOpenUrl(url),
    };

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined && text !== null) node.textContent = text;
        return node;
    }

    // region Header

    function drawHeader(state) {
        document.documentElement.dataset.theme = state.theme || 'light';
        backButton.hidden = history.length === 0;
        spinner.hidden = !state.pending;
        engine.hidden = !state.engine;
        engine.textContent = state.engine || '';

        source.replaceChildren();
        const text = state.source?.text || '';
        const chars = Array.from(text);
        const matched = Math.min(state.source?.matched || 0, chars.length);
        if (matched > 0) source.append(element('span', 'matched', chars.slice(0, matched).join('')));
        source.append(document.createTextNode(chars.slice(matched).join('')));
    }

    // endregion

    // region Entries

    function resultsKey(state) {
        return JSON.stringify([
            state.message,
            state.source,
            state.kanji?.character,
            (state.results || []).map(r => r.term.expression + r.term.reading),
        ]);
    }

    function drawResults(state) {
        drawnKey = resultsKey(state);
        if (state.kanji) {
            content.replaceChildren(kanjiView(state.kanji, state.labels || {}));
            return;
        }
        const results = state.results || [];
        if (state.message || results.length === 0) {
            content.replaceChildren(element('div', 'message', state.message || state.labels?.noResults || ''));
            return;
        }
        const fragment = document.createDocumentFragment();
        results.forEach((result, index) => fragment.append(entry(result, index, state.labels || {})));
        content.replaceChildren(fragment);
    }

    function entry(result, index, labels) {
        const term = result.term;
        const article = element('article', 'entry');
        article.dataset.index = String(index);

        const head = element('div', 'entry-head');
        const expression = element('span', 'expression');
        expression.lang = 'ja';
        if (renderer) {
            renderer.furigana(expression, term.expression, term.reading, 'kanji-char');
            expression.addEventListener('click', event => {
                const target = event.target.closest('.kanji-char');
                if (target) ScreenlateBridge.onKanji(target.textContent);
            });
        } else {
            expression.textContent = term.reading && term.reading !== term.expression
                ? `${term.expression}【${term.reading}】`
                : term.expression;
        }
        head.append(expression);
        const buttons = actionButtons(result, index);
        if (buttons) head.append(buttons);
        article.append(head);

        const meta = element('div', 'entry-meta');
        const trace = (result.trace || []).filter(step => step.name);
        if (trace.length) {
            const inflection = element('span', 'inflection');
            inflection.append(element('span', 'inflection-arrow', '«'));
            trace.forEach((step, i) => {
                if (i > 0) inflection.append(element('span', 'inflection-arrow', '«'));
                const chip = element('span', 'inflection-step', step.name);
                if (step.description) chip.title = step.description;
                inflection.append(chip);
            });
            meta.append(inflection);
        }
        for (const group of term.frequencies || []) {
            const values = (group.values || []).map(v => v.displayValue || String(v.value)).join(', ');
            if (!values) continue;
            const chip = element('span', 'frequency');
            chip.append(element('span', 'frequency-dictionary', shortName(group.dictionary)));
            chip.append(element('span', 'frequency-value', values));
            meta.append(chip);
        }
        if (meta.childNodes.length) article.append(meta);

        const pitches = pitchBlock(term);
        if (pitches) article.append(pitches);

        const glossary = element('div', 'yomitan-glossary');
        for (const [dictionary, glossaries] of groupByDictionary(term.glossaries || [])) {
            glossary.append(dictionarySection(dictionary, glossaries));
        }
        article.append(glossary);
        return article;
    }

    function actionButtons(result, index) {
        if (!actions.anki && !actions.audio) return null;
        const container = element('div', 'entry-actions');
        if (actions.audio) {
            const play = iconButton('play', ICONS.audio, labelOf('playAudio'));
            play.addEventListener('click', () => ScreenlateBridge.onPlayAudio(result.term.expression, result.term.reading || ''));
            container.append(play);
        }
        if (actions.anki) {
            const add = iconButton('add', ICONS.add, labelOf('addNote'));
            add.dataset.index = String(index);
            onTapOrHold(add, withScreenshot => {
                if (add.dataset.state === 'busy' || add.dataset.state === 'blocked') return;
                setButtonState(add, 'busy');
                ScreenlateBridge.onAddNote(index, JSON.stringify(NoteData.build(result, styles)), withScreenshot);
            });
            container.append(add);
        }
        return container;
    }

    function labelOf(key) {
        return current?.labels?.[key] || key;
    }

    function iconButton(kind, svg, label) {
        const button = element('button', `icon-button action-${kind}`);
        button.innerHTML = svg;
        button.setAttribute('aria-label', label);
        return button;
    }

    /** A short tap calls action(false), holding for HOLD_MS calls action(true). */
    function onTapOrHold(button, action) {
        let timer = null;
        let held = false;
        button.addEventListener('pointerdown', () => {
            held = false;
            timer = setTimeout(() => {
                held = true;
                action(true);
            }, HOLD_MS);
        });
        const cancel = () => clearTimeout(timer);
        button.addEventListener('pointerup', () => {
            cancel();
            if (!held) action(false);
        });
        button.addEventListener('pointerleave', cancel);
        button.addEventListener('pointercancel', cancel);
        button.addEventListener('contextmenu', event => event.preventDefault());
    }

    function setButtonState(button, state) {
        if (state) button.dataset.state = state;
        else delete button.dataset.state;
    }

    /** A kanji dictionary entry: the character, readings, meanings and a few statistics per dictionary. */
    function kanjiView(kanji, labels) {
        const view = element('article', 'entry kanji-entry');
        view.append(element('div', 'kanji-character', kanji.character));
        for (const entry of kanji.entries || []) {
            const section = element('section', 'dictionary');
            section.append(element('div', 'dictionary-name', entry.dictionary));
            const readings = [
                [labels.onyomi || 'On', entry.onyomi],
                [labels.kunyomi || 'Kun', entry.kunyomi],
            ];
            for (const [label, value] of readings) {
                if (!value) continue;
                const row = element('div', 'kanji-row');
                row.append(element('span', 'kanji-label', label));
                row.append(element('span', 'kanji-readings', value.split(' ').filter(Boolean).join('、')));
                section.append(row);
            }
            if ((entry.definitions || []).length) {
                const list = element('ol', 'definitions');
                entry.definitions.forEach(definition => list.append(element('li', 'definition', definition)));
                section.append(list);
            }
            const stats = element('div', 'entry-meta');
            for (const key of KANJI_STATS) {
                const value = entry.stats?.[key];
                if (!value) continue;
                const chip = element('span', 'frequency');
                chip.append(element('span', 'frequency-dictionary', labels[`stat_${key}`] || key));
                chip.append(element('span', 'frequency-value', value));
                stats.append(chip);
            }
            if (stats.childNodes.length) section.append(stats);
            view.append(section);
        }
        return view;
    }

    function pitchBlock(term) {
        const groups = (term.pitches || []).filter(group => (group.pitches || []).length);
        if (!groups.length || !renderer) return null;
        const block = element('div', 'pitch');
        for (const group of groups) {
            const row = element('div', 'pitch-row');
            row.append(element('span', 'pitch-dictionary', shortName(group.dictionary)));
            for (const accent of group.pitches) {
                const position = accent.pattern || accent.position;
                const item = element('span', 'pitch-item');
                item.append(renderer.pitchElement(term.reading || term.expression, position, accent.nasal, accent.devoice));
                item.append(element('span', 'pitch-position', `[${renderer.downsteps(position).join(', ')}]`));
                row.append(item);
            }
            block.append(row);
        }
        return block;
    }

    function groupByDictionary(glossaries) {
        const groups = new Map();
        for (const glossary of glossaries) {
            if (!groups.has(glossary.dictionary)) groups.set(glossary.dictionary, []);
            groups.get(glossary.dictionary).push(glossary);
        }
        return groups;
    }

    function dictionarySection(dictionary, glossaries) {
        const section = element('section', 'dictionary');
        section.dataset.dictionary = dictionary;
        section.append(element('div', 'dictionary-name', dictionary));
        const container = glossaries.length > 1 ? element('ol', 'definitions') : section;
        for (const glossary of glossaries) {
            const definition = glossaries.length > 1 ? element('li', 'definition') : element('div', 'definition');
            const tags = (glossary.definitionTags || '').split(' ').filter(Boolean);
            if (tags.length) {
                const tagRow = element('span', 'tags');
                tags.forEach(tag => tagRow.append(element('span', 'tag', tag)));
                definition.append(tagRow);
            }
            const body = element('div', 'definition-body');
            const content = glossaryContent(glossary);
            if (renderer) {
                renderer.renderGlossary(body, content, dictionary, renderOptions);
            } else {
                body.textContent = plainText(content);
            }
            definition.append(body);
            container.append(definition);
        }
        if (container !== section) section.append(container);
        return section;
    }

    /** Glossaries arrive as JSON text (see dictionary.api.model.Glossary). */
    function glossaryContent(glossary) {
        if (typeof glossary.content !== 'string') return glossary.content;
        try {
            return JSON.parse(glossary.content);
        } catch (e) {
            return [glossary.content];
        }
    }

    function plainText(content) {
        if (typeof content === 'string') return content;
        if (Array.isArray(content)) return content.map(plainText).join('; ');
        if (content && typeof content === 'object') return plainText(content.text || content.content || '');
        return '';
    }

    /** Dictionary titles often carry a revision in brackets; chips only need the name. */
    function shortName(title) {
        return (title || '').replace(/\s*[[(].*?[\])]\s*$/, '') || title;
    }

    // endregion

    // region Navigation

    function draw(state) {
        drawHeader(state);
        drawResults(state);
    }

    function render(state) {
        history.length = 0;
        current = state;
        draw(state);
        content.scrollTop = 0;
    }

    function update(state) {
        current = state;
        drawHeader(state);
        if (resultsKey(state) !== drawnKey) drawResults(state);
    }

    function push(state) {
        if (current) history.push({ state: current, scroll: content.scrollTop });
        current = state;
        draw(state);
        content.scrollTop = 0;
    }

    function back() {
        const previous = history.pop();
        if (!previous) return;
        current = previous.state;
        draw(previous.state);
        content.scrollTop = previous.scroll;
    }

    function setStyles(newStyles) {
        styles = newStyles || [];
        if (!renderer) return;
        dictionaryStyles.textContent = styles
            .map(style => renderer.dictionaryCss(style.css, style.dictionary))
            .join('\n');
    }

    /** Page options: { embedded: true } drops the card frame and the close button (app screens). */
    function configure(options) {
        document.documentElement.dataset.embedded = String(Boolean(options.embedded));
    }

    /** Which entry buttons to show: { anki: boolean, audio: boolean }. */
    function setActions(newActions) {
        const changed = JSON.stringify(newActions) !== JSON.stringify(actions);
        actions = newActions;
        if (changed && current) drawResults(current);
    }

    /**
     * Marks the ➕ buttons of the current view. states: { index: 'duplicate' | 'blocked' | 'added' | 'busy' | 'error' | '' }.
     * 'blocked' is a duplicate that may not be added again.
     */
    function setNoteStates(states) {
        for (const [index, state] of Object.entries(states)) {
            const button = content.querySelector(`.action-add[data-index="${index}"]`);
            if (button) setButtonState(button, state);
        }
    }

    /** Marker values of every entry in the current view, for the duplicate check. */
    function allNoteData() {
        return (current?.results || []).map(result => NoteData.build(result, styles).values);
    }

    // endregion

    return { configure, render, update, push, setStyles, setActions, setNoteStates, allNoteData };
})();

ScreenlateBridge.onReady();
