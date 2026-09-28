'use strict';

/*
 * Popup page. Kotlin drives it through Popup.render / update / push / setStyles with JSON states:
 *
 * {
 *   theme: 'light' | 'dark',
 *   pending: boolean,              OCR is still refining the text
 *   engine: string,                OCR engine label, empty to hide
 *   ocrError: string,              why cloud recognition failed, empty without a failure; ⚠ opens it in the panel
 *   source: { text, matched },     lookup text and the length of its matched prefix (code points)
 *   results: [LookupResult],       see dictionary.api.model.LookupResult
 *   message: string,               shown instead of results
 *   hideSource: boolean,           no header on the first view: it starts with the first entry, whose buttons get ✕
 *   labels: { ... }                localized strings
 * }
 *
 * Each entry starts with the word, then one row of details (inflection 🧩, the first frequency, pitch accents) and
 * the buttons. Tapping an inflection step or an accent opens the info panel at the bottom of the card, and so does a
 * tag with a description: a dictionary's tag (its tag bank's notes, see setTagNotes) or a structured-content element
 * with a title, such as Jitendex's part-of-speech labels.
 *
 * Glossary rendering comes from window.YomitanRender (yomitan-render/render.js) when it is present.
 *
 * Note buttons: ➕ adds (hold: with a picture); after adding, or for a duplicate that may not be added again, the
 * button becomes 📖, which opens the note in AnkiDroid (hold: add anyway). Holding 🔊 lists the audio sources.
 */
const Popup = (() => {
    const backButton = document.getElementById('back');
    const source = document.getElementById('source');
    const engine = document.getElementById('engine');
    const ocrErrorButton = document.getElementById('ocr-error');
    const spinner = document.getElementById('spinner');
    const content = document.getElementById('content');
    const info = document.getElementById('info');
    const infoTitle = document.getElementById('info-title');
    const infoText = document.getElementById('info-text');
    const dictionaryStyles = document.getElementById('dictionary-styles');
    const fontFaces = document.getElementById('font-faces');
    const customCss = document.getElementById('custom-css');
    const renderer = window.YomitanRender || null;
    const HOLD_MS = 450;
    const KANJI_STATS = ['strokes', 'grade', 'jlpt', 'freq'];
    const ICONS = {
        add: '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
        copy: '<svg viewBox="0 0 24 24" width="20" height="20"><rect x="8" y="8" width="12" height="12" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" fill="none" stroke="currentColor" stroke-width="1.8"/></svg>',
        open: '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M12 6.5C10.3 5.2 8 4.5 5 4.5H3.5v13H5c3 0 5.3.7 7 2 1.7-1.3 4-2 7-2h1.5v-13H19c-3 0-5.3.7-7 2zM12 6.5v13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>',
        close: '<svg viewBox="0 0 24 24" width="20" height="20"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
        puzzle: '<svg viewBox="0 0 24 24" width="15" height="15"><path d="M10 3.5a2 2 0 0 1 4 0V5h4a1 1 0 0 1 1 1v4h-1.5a2 2 0 0 0 0 4H19v4a1 1 0 0 1-1 1h-4v-1.5a2 2 0 0 0-4 0V19H6a1 1 0 0 1-1-1v-4h1.5a2 2 0 0 0 0-4H5V6a1 1 0 0 1 1-1h4z" fill="currentColor"/></svg>',
        audio: '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M4 9v6h4l5 4V5L8 9H4z" fill="currentColor"/><path d="M16 8.5a5 5 0 0 1 0 7M18.5 6a8.5 8.5 0 0 1 0 12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
        warning: '<svg viewBox="0 0 24 24" width="18" height="18"><path d="M12 3.5 2.5 20h19z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M12 10v4.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><circle cx="12" cy="17.3" r="1.1" fill="currentColor"/></svg>',
    };

    const history = [];
    let current = null;
    let drawnKey = null;
    let styles = [];
    let tagNotes = new Map();
    let actions = { anki: false, audio: false };
    let noteConfig = { markers: null, frequencyModes: {} };
    let menu = null;

    document.getElementById('close').addEventListener('click', () => ScreenlateBridge.onClose());
    document.getElementById('info-close').addEventListener('click', hideInfo);
    ocrErrorButton.innerHTML = ICONS.warning;
    ocrErrorButton.addEventListener('click', showOcrError);
    backButton.addEventListener('click', back);
    content.addEventListener('click', explainTag);

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

    /** The header holds the back button, so it stays after following a link; messages keep it for ✕. */
    function compact(state) {
        return Boolean(state.hideSource) && history.length === 0 && !state.message && !state.kanji &&
            (state.results || []).length > 0;
    }

    function drawHeader(state) {
        document.documentElement.dataset.theme = state.theme || 'light';
        document.documentElement.dataset.compact = String(compact(state));
        backButton.hidden = history.length === 0;
        spinner.hidden = !state.pending;
        engine.hidden = !state.engine;
        engine.textContent = state.engine || '';
        ocrErrorButton.hidden = !state.ocrError;
        ocrErrorButton.title = state.labels?.ocrError || '';
        placeInlineOcrWarning(state);

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
            compact(state),
            state.message,
            state.source,
            state.kanji?.character,
            (state.results || []).map(r => r.term.expression + r.term.reading),
        ]);
    }

    function drawResults(state) {
        drawnKey = resultsKey(state);
        hideInfo();
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
        // Layout is known only once the entries are in the document.
        content.querySelectorAll('.definition > .dictionary-name').forEach(placeDictionaryName);
        placeInlineOcrWarning(state);
    }

    function tagNote(chip) {
        return tagNotes.get(chip.dataset.dictionary)?.get(chip.textContent) || '';
    }

    function markTag(chip) {
        chip.classList.toggle('described', Boolean(tagNote(chip)));
    }

    /**
     * A tap on a tag with a description opens it in the panel: a dictionary's tag with notes, or a structured-content
     * element whose title says more than its text. Links, images and the dictionary chip keep their own behavior.
     */
    function explainTag(event) {
        const target = event.target instanceof Element ? event.target : event.target.parentElement;
        const chip = target?.closest('.tag');
        if (chip && content.contains(chip)) {
            const note = tagNote(chip);
            if (note) showInfo(chip.textContent, note);
            return;
        }
        const titled = target?.closest('.definition-body [title]');
        if (!titled || titled.closest('a, summary, button, .gloss-image-link, .dictionary-name')) return;
        const title = titled.title.trim();
        const text = titled.textContent.trim();
        if (title && title !== text) showInfo(text || title, title);
    }

    function showOcrError() {
        if (current?.ocrError) showInfo(current.labels?.ocrError || '', current.ocrError);
    }

    /** Without the header, ⚠ goes to the first entry's buttons, before ✕. */
    function placeInlineOcrWarning(state) {
        const actions = content.querySelector('.entry .entry-actions');
        const inline = content.querySelector('.entry-actions > .ocr-warning');
        const wanted = Boolean(state.ocrError) && compact(state) && actions;
        if (!wanted) {
            inline?.remove();
            return;
        }
        if (inline) return;
        const button = iconButton('ocr-warning', ICONS.warning, state.labels?.ocrError || '');
        button.classList.add('ocr-warning');
        button.addEventListener('click', showOcrError);
        actions.prepend(button);
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
        const details = entryInfo(result, labels);
        if (details) head.append(details);
        const buttons = actionButtons(result, index);
        if (buttons) head.append(buttons);
        article.append(head);

        const glossary = element('div', 'yomitan-glossary');
        for (const [dictionary, glossaries] of groupByDictionary(term.glossaries || [])) {
            glossary.append(dictionarySection(dictionary, glossaries));
        }
        article.append(glossary);
        return article;
    }

    function actionButtons(result, index) {
        const container = element('div', 'entry-actions');
        if (index === 0 && document.documentElement.dataset.compact === 'true') {
            const close = iconButton('close', ICONS.close, labelOf('close'));
            close.addEventListener('click', () => ScreenlateBridge.onClose());
            container.append(close);
        }
        const copy = iconButton('copy', ICONS.copy, labelOf('copy'));
        copy.addEventListener('click', () => ScreenlateBridge.onCopy(result.term.expression));
        container.append(copy);
        const expression = result.term.expression;
        const reading = result.term.reading || '';
        if (actions.audio) {
            const play = iconButton('play', ICONS.audio, labelOf('playAudio'));
            play.dataset.index = String(index);
            onTapOrHold(play, held => {
                if (held) ScreenlateBridge.onAudioMenu(index, expression, reading);
                else ScreenlateBridge.onPlayAudio(index, expression, reading);
            });
            container.append(play);
        }
        if (actions.ankiProblem) {
            const add = iconButton('add', ICONS.add, actions.ankiProblem);
            add.classList.add('unavailable');
            add.addEventListener('click', () => toggleNoteProblem(container));
            container.append(add);
        } else if (actions.anki) {
            const add = iconButton('add', ICONS.add, labelOf('addNote'));
            add.dataset.index = String(index);
            const addNote = (withScreenshot, force) => {
                setButtonState(add, 'busy');
                const data = NoteData.build(result, noteContext(), noteConfig.markers);
                ScreenlateBridge.onAddNote(index, JSON.stringify(data), withScreenshot, force);
            };
            onTapOrHold(add, held => {
                const state = add.dataset.state;
                if (state === 'busy') return;
                if (state === 'added' || state === 'open') {
                    if (!held) {
                        ScreenlateBridge.onOpenNote(index);
                    } else {
                        showMenu(add, [
                            { label: labelOf('addAnyway'), action: () => addNote(false, true) },
                            { label: labelOf('addAnywayWithPicture'), action: () => addNote(true, true) },
                        ]);
                    }
                    return;
                }
                addNote(held, false);
            });
            container.append(add);
        }
        return container;
    }

    /** The reason why ➕ is grey, with a button that opens the app, below the entry's buttons. */
    function toggleNoteProblem(container) {
        const head = container.parentElement;
        const next = head.nextElementSibling;
        if (next && next.classList.contains('note-problem')) {
            next.remove();
            return;
        }
        const box = element('div', 'note-problem');
        box.append(element('span', null, actions.ankiProblem));
        const open = element('button', 'note-problem-open', labelOf('openApp'));
        open.addEventListener('click', () => ScreenlateBridge.onOpenApp());
        box.append(open);
        head.after(box);
    }

    function noteContext() {
        return { styles, frequencyModes: noteConfig.frequencyModes || {}, query: current?.source?.text || '' };
    }

    // region Menu

    /** A small menu under (or above) [anchor]; items: [{ label, detail?, action?, disabled? }]. */
    function showMenu(anchor, items) {
        closeMenu();
        menu = element('div', 'menu');
        for (const item of items) {
            const row = element('button', 'menu-item');
            row.append(element('span', 'menu-label', item.label));
            if (item.detail) row.append(element('span', 'menu-detail', item.detail));
            if (item.disabled || !item.action) {
                row.disabled = true;
            } else {
                row.addEventListener('click', event => {
                    event.stopPropagation();
                    closeMenu();
                    item.action();
                });
            }
            menu.append(row);
        }
        document.body.append(menu);
        const rect = anchor.getBoundingClientRect();
        const height = menu.offsetHeight;
        const width = menu.offsetWidth;
        const below = rect.bottom + 4 + height <= window.innerHeight;
        menu.style.top = `${Math.max(4, below ? rect.bottom + 4 : rect.top - 4 - height)}px`;
        menu.style.left = `${Math.max(4, Math.min(rect.right - width, window.innerWidth - width - 4))}px`;
        menu.dataset.anchor = anchor.dataset.index || '';
        menu.dataset.kind = anchor.classList.contains('action-play') ? 'audio' : 'note';
    }

    function closeMenu() {
        if (menu) menu.remove();
        menu = null;
    }

    document.addEventListener('pointerdown', event => {
        if (menu && !menu.contains(event.target)) closeMenu();
    }, true);

    /**
     * Audio clips of entry [index] for the menu opened by holding 🔊. items: [{ id, label, detail }];
     * loading: true while sources are still being asked.
     */
    function showAudioMenu(index, items, loading) {
        const button = content.querySelector(`.action-play[data-index="${index}"]`);
        if (!button) return;
        if (menu && (menu.dataset.kind !== 'audio' || menu.dataset.anchor !== String(index)) && !loading) return;
        const rows = (items || []).map(item => ({
            label: item.label,
            detail: item.detail,
            action: () => ScreenlateBridge.onPlayClip(index, item.id),
        }));
        if (loading) rows.push({ label: labelOf('audioLoading'), disabled: true });
        else if (!rows.length) rows.push({ label: labelOf('audioNone'), disabled: true });
        showMenu(button, rows);
    }

    // endregion

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
        if (!button.classList.contains('action-add')) return;
        const open = state === 'added' || state === 'open';
        const kind = open ? 'open' : 'add';
        if (button.dataset.icon !== kind) {
            button.dataset.icon = kind;
            button.innerHTML = open ? ICONS.open : ICONS.add;
            button.setAttribute('aria-label', labelOf(open ? 'openNote' : 'addNote'));
        }
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
            const stats = element('div', 'kanji-stats');
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

    /**
     * Inflection, frequency and pitch accent in one row: beside the word when the popup is wide, below it when not.
     * One frequency is shown (the first group: the lookup puts the sort dictionary first), "+N" reveals the rest.
     */
    function entryInfo(result, labels) {
        const row = element('div', 'entry-info');
        const trace = (result.trace || []).filter(step => step.name);
        if (trace.length) row.append(inflectionChain(trace));

        const frequencies = (result.term.frequencies || [])
            .map(group => ({ dictionary: group.dictionary, values: frequencyValues(group) }))
            .filter(group => group.values);
        if (frequencies.length) {
            const chips = frequencies.map(frequencyChip);
            row.append(chips[0]);
            if (chips.length > 1) {
                const more = element('button', 'frequency-more', `+${chips.length - 1}`);
                more.type = 'button';
                more.addEventListener('click', () => more.replaceWith(...chips.slice(1)));
                row.append(more);
            }
        }

        for (const pitch of pitchAccents(result.term)) {
            const item = element('button', 'pitch-item');
            item.type = 'button';
            item.append(pitch.graph);
            item.append(element('span', 'pitch-position', `[${pitch.downsteps}]`));
            item.addEventListener('click', () => showInfo(labels.pitchDictionaries || '', pitch.dictionaries.join('\n')));
            row.append(item);
        }
        return row.childNodes.length ? row : null;
    }

    /** 🧩 and the rule chain; a rule with a description opens it in the panel below the entries. */
    function inflectionChain(trace) {
        const chain = element('span', 'inflection');
        const icon = element('span', 'inflection-icon');
        icon.innerHTML = ICONS.puzzle;
        chain.append(icon);
        trace.forEach((step, i) => {
            if (i > 0) chain.append(element('span', 'inflection-arrow', '«'));
            const name = step.label || step.name;
            if (step.description) {
                const chip = element('button', 'inflection-step', name);
                chip.type = 'button';
                chip.addEventListener('click', () => showInfo(name, step.description));
                chain.append(chip);
            } else {
                chain.append(element('span', 'inflection-step', name));
            }
        });
        return chain;
    }

    function frequencyValues(group) {
        return (group.values || []).map(v => v.displayValue || String(v.value)).join(', ');
    }

    function frequencyChip(group) {
        const chip = element('span', 'frequency');
        chip.append(element('span', 'frequency-dictionary', shortName(group.dictionary)));
        chip.append(element('span', 'frequency-value', group.values));
        return chip;
    }

    /** Distinct accents of the word; the dictionaries that list each one open from it. */
    function pitchAccents(term) {
        if (!renderer) return [];
        const accents = new Map();
        for (const group of term.pitches || []) {
            for (const accent of group.pitches || []) {
                const position = accent.pattern || accent.position;
                const key = JSON.stringify([position, accent.nasal || [], accent.devoice || []]);
                if (!accents.has(key)) {
                    accents.set(key, {
                        graph: renderer.pitchElement(term.reading || term.expression, position, accent.nasal, accent.devoice),
                        downsteps: renderer.downsteps(position).join(', '),
                        dictionaries: [],
                    });
                }
                const dictionaries = accents.get(key).dictionaries;
                if (!dictionaries.includes(group.dictionary)) dictionaries.push(group.dictionary);
            }
        }
        return [...accents.values()];
    }

    function showInfo(title, text) {
        infoTitle.textContent = title;
        infoText.textContent = text;
        info.hidden = false;
        info.scrollTop = 0;
    }

    function hideInfo() {
        info.hidden = true;
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
        // A chip in the first definition, as in Yomitan, instead of a line of its own; placed after drawing.
        const name = element('span', 'dictionary-name', shortName(dictionary));
        name.title = dictionary;
        const container = glossaries.length > 1 ? element('ol', 'definitions') : section;
        for (const glossary of glossaries) {
            const definition = glossaries.length > 1 ? element('li', 'definition') : element('div', 'definition');
            if (glossary === glossaries[0]) definition.append(name);
            const tags = (glossary.definitionTags || '').split(' ').filter(Boolean);
            if (tags.length) {
                const tagRow = element('span', 'tags');
                tags.forEach(tag => {
                    const chip = element('span', 'tag', tag);
                    chip.dataset.dictionary = dictionary;
                    markTag(chip);
                    tagRow.append(chip);
                });
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

    const BLOCK_DISPLAYS = new Set(['block', 'list-item', 'flow-root']);
    const INLINE_DISPLAYS = new Set(['inline', 'contents', '']);

    /**
     * Moves a dictionary chip to where the definition's first line of text starts: down through blocks, list items
     * and inline wrappers of blocks, so a list that opens the definition keeps its marker in front of the chip
     * instead of under it. The chip stays outside inline elements that start with text, such as a tag.
     */
    function placeDictionaryName(name) {
        let container = name.parentElement;
        name.remove();
        for (;;) {
            const first = firstVisibleChild(container);
            if (opensBlock(first)) {
                container = first;
                continue;
            }
            container.insertBefore(name, first);
            return;
        }
    }

    function opensBlock(node) {
        if (node?.nodeType !== Node.ELEMENT_NODE) return false;
        const display = getComputedStyle(node).display;
        if (BLOCK_DISPLAYS.has(display)) return true;
        return INLINE_DISPLAYS.has(display) && opensBlock(firstVisibleChild(node));
    }

    function firstVisibleChild(node) {
        for (const child of node.childNodes) {
            if (child.nodeType === Node.TEXT_NODE && child.textContent.trim()) return child;
            if (child.nodeType !== Node.ELEMENT_NODE || child.tagName === 'STYLE' || child.tagName === 'SCRIPT') continue;
            if (getComputedStyle(child).display !== 'none') return child;
        }
        return null;
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

    /** Development aid (scripts/popup-eval.mjs): redraws the current results and returns the time with layout. */
    function measureRender() {
        if (!current) return null;
        const started = performance.now();
        drawResults(current);
        void content.scrollHeight;
        return { entries: (current.results || []).length, ms: Math.round(performance.now() - started) };
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

    /** Tag descriptions by dictionary: [{ dictionary, notes: { tag: description } }]. */
    function setTagNotes(list) {
        tagNotes = new Map((list || []).map(item => [item.dictionary, new Map(Object.entries(item.notes || {}))]));
        content.querySelectorAll('.tag').forEach(markTag);
    }

    function setStyles(newStyles) {
        styles = newStyles || [];
        if (!renderer) return;
        dictionaryStyles.textContent = styles
            .map(style => renderer.dictionaryCss(style.css, style.dictionary))
            .join('\n');
    }

    /**
     * Language, fonts and the user's CSS: { lang, fontFaces, fontFamily, fontSize, customCss, preload }. The custom
     * CSS comes after the dictionaries' styles; preload names a font to load before it is first needed.
     */
    function setAppearance(appearance) {
        const root = document.documentElement;
        root.lang = appearance.lang || '';
        fontFaces.textContent = appearance.fontFaces || '';
        root.style.setProperty('--font-family', appearance.fontFamily || 'sans-serif');
        if (appearance.fontSize) root.style.setProperty('--font-size-no-units', String(appearance.fontSize));
        customCss.textContent = appearance.customCss || '';
        if (appearance.preload && document.fonts) {
            document.fonts.load(`1em "${appearance.preload.replace(/"/g, '\\"')}"`).catch(() => {});
        }
    }

    /** Page options: { embedded: true } drops the card frame and the close button (app screens). */
    function configure(options) {
        document.documentElement.dataset.embedded = String(Boolean(options.embedded));
    }

    /** Which entry buttons to show: { anki: boolean, audio: boolean, ankiProblem: string | null }. */
    function setActions(newActions) {
        const changed = JSON.stringify(newActions) !== JSON.stringify(actions);
        actions = newActions;
        if (changed && current) drawResults(current);
    }

    /**
     * Marks the ➕ buttons of the current view. states: { index: 'duplicate' | 'open' | 'added' | 'busy' | 'error' | '' }.
     * 'duplicate' may be added again; 'open' is a duplicate that may not, 'added' was added during this scan: both show 📖.
     */
    function setNoteStates(states) {
        for (const [index, state] of Object.entries(states)) {
            const button = content.querySelector(`.action-add[data-index="${index}"]`);
            if (button) setButtonState(button, state);
        }
    }

    /**
     * Which markers the note fields use and how frequency dictionaries count:
     * { markers: [name], frequencyModes: { dictionary: 'rank-based' | 'occurrence-based' } }.
     */
    function setNoteConfig(config) {
        noteConfig = config || { markers: null, frequencyModes: {} };
    }

    /** Values of [markers] for every entry in the current view, for the duplicate check. */
    function allNoteData(markers) {
        return (current?.results || []).map(result => NoteData.build(result, noteContext(), markers).values);
    }

    /** Terms of the current view as [expression, reading] pairs. */
    function terms() {
        return (current?.results || []).map(result => [result.term.expression, result.term.reading || '']);
    }

    // endregion

    return {
        configure,
        render,
        update,
        push,
        setStyles,
        setTagNotes,
        setAppearance,
        setActions,
        setNoteStates,
        setNoteConfig,
        allNoteData,
        terms,
        showAudioMenu,
        measureRender,
    };
})();

ScreenlateBridge.onReady();
