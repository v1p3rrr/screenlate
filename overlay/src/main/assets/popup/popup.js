'use strict';

/*
 * Popup page. Kotlin drives it through Popup.render / update / push / setStyles with JSON states:
 *
 * {
 *   theme: 'light' | 'dark',
 *   pending: boolean,              OCR is still refining the text
 *   noteWait: boolean,             ➕ waits for the final text: it is grey, and a tap asks the app for a hint
 *   engine: string,                OCR engine label, empty to hide
 *   ocrError: string,              why cloud recognition failed, empty without a failure; ⚠ opens it in the panel
 *   source: { text, matched, lang }, lookup text, the length of its matched prefix (code points) and, for a language
 *                                  written in several scripts, the tag of the text's script (as each result's lang)
 *   results: [LookupResult],       see dictionary.api.model.LookupResult
 *   kanji: KanjiResult,            a kanji entry shown instead of results: a tapped headword kanji, or the first
 *                                  character when no word was found
 *   message: string,               shown instead of results
 *   installation: boolean,         opens the installation queue from the message
 *   ocrModels: boolean,            opens the language's on-device models from the message
 *   hideSource: boolean,           no header on the first view: it starts with the first entry, whose buttons get ✕
 *   compound: string,              a line above the entries of a word found only as its parts, empty otherwise
 *   rightToLeft: boolean,          the language is written right to left: the lookup text, headwords and readings run
 *                                  that way, the glossaries' blocks by their own first letters (an Arabic example
 *                                  among English meanings); the layout stays
 *   labels: { ... }                localized strings
 * }
 *
 * Sentence translation (setTranslation): the translation button sits in the header before the OCR chip, or without the header in the first
 * entry's buttons. A tap asks the app (onTranslate with a request number) and shows a block under the header, which
 * showTranslation fills; a second tap hides it. The block belongs to the first view: a new word drops it, a pushed
 * view hides it until back returns.
 *
 * Each entry starts with the word (with the language's name after it when the entry comes from the dictionaries of the
 * language the lookup falls back to: LookupResult.fallbackLanguage, its name in labels as language_<code>), then one row of details (inflection 🧩, the first frequency, pitch accents,
 * transcriptions) and the buttons. Tapping an inflection step, an accent or a transcription opens the info panel at the bottom of the card, and so does a
 * tag with a description: a dictionary's tag (its tag bank's notes, see setTagNotes) or a structured-content element
 * with a title, such as Jitendex's part-of-speech labels.
 *
 * Glossary rendering comes from window.YomitanRender (yomitan-render/render.js) when it is present.
 *
 * Text can be selected with a long press; in the popup, whose overlay window has no system toolbar, a Copy button
 * appears above the selection.
 *
 * Heavier text (setAppearance) applies to the whole page, or only to the language's script: then runs of it are
 * wrapped in .script-run spans as text arrives in the page.
 *
 * Pitch accent colors (setAppearance's pronunciationColors): the word carries the type of its first accent in
 * dictionary order and each accent button its own (data-pitch-category: heiban, atamadaka, nakadaka, odaka, kifuku);
 * popup.css colors them while the root's data-pronunciation-colors is on. The same switch colors Chinese pinyin: each
 * syllable carries its tone (data-tone, 1 to 5 for Chinese, 1 to 6 for Cantonese) from HeadwordReading (reading.js).
 *
 * Note buttons: ➕ adds (hold: with a picture); after adding, or for a duplicate that may not be added again, the
 * button becomes 📖, which opens the note in AnkiDroid (hold: add anyway). Holding 🔊 lists the audio sources.
 */
const Popup = (() => {
    const backButton = document.getElementById('back');
    const source = document.getElementById('source');
    const engine = document.getElementById('engine');
    const ocrErrorButton = document.getElementById('ocr-error');
    const translateButton = document.getElementById('translate');
    const translationBox = document.getElementById('translation');
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
    const SELECTION_GAP = 8;
    const KANJI_STATS = ['strokes', 'grade', 'jlpt', 'freq'];
    /** Characters of a headword that open their own entry when tapped (drawSegments). */
    const HAN_CHARACTER = /[\p{Script=Han}〇]/u;
    const HAN_PIECES = /([\p{Script=Han}〇])/u;
    /** Elements of a glossary that take the direction of their own first letter in a right-to-left language. */
    const TEXT_BLOCKS = 'div, p, li, dt, dd, td, th, summary';
    /** Pitch accents shown before "+N"; a word with more shows only its first. */
    const SHOWN_ACCENTS = 2;
    /** Transcriptions that "+N" shows before "Show all". */
    const SHOWN_TRANSCRIPTIONS = 5;
    /** The widest frequency dictionary name a chip shows whole, in Latin letters; a CJK character counts as two. */
    const SHORT_NAME_WIDTH = 4;
    const ICONS = {
        add: '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
        copy: '<svg viewBox="0 0 24 24" width="20" height="20"><rect x="8" y="8" width="12" height="12" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" fill="none" stroke="currentColor" stroke-width="1.8"/></svg>',
        open: '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M12 6.5C10.3 5.2 8 4.5 5 4.5H3.5v13H5c3 0 5.3.7 7 2 1.7-1.3 4-2 7-2h1.5v-13H19c-3 0-5.3.7-7 2zM12 6.5v13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>',
        close: '<svg viewBox="0 0 24 24" width="20" height="20"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
        puzzle: '<svg viewBox="0 0 24 24" width="15" height="15"><path d="M10 3.5a2 2 0 0 1 4 0V5h4a1 1 0 0 1 1 1v4h-1.5a2 2 0 0 0 0 4H19v4a1 1 0 0 1-1 1h-4v-1.5a2 2 0 0 0-4 0V19H6a1 1 0 0 1-1-1v-4h1.5a2 2 0 0 0 0-4H5V6a1 1 0 0 1 1-1h4z" fill="currentColor"/></svg>',
        audio: '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M4 9v6h4l5 4V5L8 9H4z" fill="currentColor"/><path d="M16 8.5a5 5 0 0 1 0 7M18.5 6a8.5 8.5 0 0 1 0 12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
        warning: '<svg viewBox="0 0 24 24" width="18" height="18"><path d="M12 3.5 2.5 20h19z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M12 10v4.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><circle cx="12" cy="17.3" r="1.1" fill="currentColor"/></svg>',
        // Material Icons "translate" (Apache 2.0, see NOTICE).
        translate: '<svg viewBox="0 0 24 24" width="17" height="17"><path d="M12.87 15.07l-2.54-2.51.03-.03c1.74-1.94 2.98-4.17 3.71-6.53H17V4h-7V2H8v2H1v1.99h11.17C11.5 7.92 10.44 9.75 9 11.35 8.07 10.32 7.3 9.19 6.69 8h-2c.73 1.63 1.73 3.17 2.98 4.56l-5.09 5.02L4 19l5-5 3.11 3.11.76-2.04zM18.5 10h-2L12 22h2l1.12-3h4.75L21 22h2l-4.5-12zm-2.62 7l1.62-4.33L19.12 17h-3.24z" fill="currentColor"/></svg>',
    };

    const history = [];
    let current = null;
    let drawnKey = null;
    let styles = [];
    let tagNotes = new Map();
    let actions = { anki: false, audio: false };
    /** The drawn entries' language is written right to left (state.rightToLeft). */
    let rightToLeft = false;
    let noteConfig = { markers: null, frequencyModes: {} };
    let menu = null;
    /** Whether the translation button shows. */
    let translationEnabled = false;
    /** The first view's translation: null, or { request, state: 'loading' | 'done' | 'error', text, service, error }. */
    let translation = null;
    /** The translation the block was last built for. */
    let drawnTranslation = null;
    let translationRequests = 0;

    document.getElementById('close').addEventListener('click', () => ScreenlateBridge.onClose());
    document.getElementById('info-close').addEventListener('click', hideInfo);
    ocrErrorButton.innerHTML = ICONS.warning;
    ocrErrorButton.addEventListener('click', showOcrError);
    translateButton.innerHTML = ICONS.translate;
    translateButton.addEventListener('click', toggleTranslation);
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
        document.documentElement.dataset.noteWait = String(Boolean(state.noteWait));
        backButton.hidden = history.length === 0;
        spinner.hidden = !state.pending;
        engine.hidden = !state.engine;
        engine.textContent = state.engine || '';
        ocrErrorButton.hidden = !state.ocrError;
        ocrErrorButton.title = state.labels?.ocrError || '';
        placeInlineOcrWarning(state);
        drawTranslation(state);

        source.replaceChildren();
        setLang(source, state.source?.lang);
        setDirection(source, state.rightToLeft ? 'rtl' : '');
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
            state.installation,
            state.ocrModels,
            state.source,
            state.kanji?.character,
            state.compound,
            (state.results || []).map(r => [r.term.expression, r.term.reading]),
        ]);
    }

    /** A button under a message that opens a screen of the app. */
    function messageLink(label, url) {
        const button = element('button', 'message-link', label);
        button.addEventListener('click', () => ScreenlateBridge.onOpenUrl(url));
        return button;
    }

    function termsKey(state) {
        return JSON.stringify([
            state?.message,
            state?.kanji?.character,
            (state?.results || []).map(r => [r.term.expression, r.term.reading]),
        ]);
    }

    function drawResults(state) {
        drawnKey = resultsKey(state);
        hideInfo();
        // A menu belongs to the buttons of the entries it was opened from.
        closeMenu();
        if (state.kanji) {
            content.replaceChildren(kanjiView(state.kanji, state.labels || {}));
            return;
        }
        const results = state.results || [];
        if (state.message || results.length === 0) {
            content.replaceChildren(element('div', 'message', state.message || state.labels?.noResults || ''));
            if (state.installation) {
                content.append(messageLink(state.labels?.installation || 'Installation status', 'screenlate://installation'));
            }
            if (state.ocrModels) {
                content.append(messageLink(state.labels?.ocrModels || 'On-device model', 'screenlate://ocr-models'));
            }
            return;
        }
        rightToLeft = Boolean(state.rightToLeft);
        const fragment = document.createDocumentFragment();
        if (state.compound) fragment.append(element('div', 'compound', state.compound));
        results.forEach((result, index) => fragment.append(entry(result, index, state.labels || {})));
        content.replaceChildren(fragment);
        // Layout is known only once the entries are in the document.
        content.querySelectorAll('.definition > .dictionary-name').forEach(placeDictionaryName);
        placeInlineOcrWarning(state);
        drawTranslation(state);
    }

    function tagNote(chip) {
        return tagNotes.get(chip.dataset.dictionary)?.notes.get(chip.dataset.tag) || '';
    }

    /** A dictionary's tag; a region chip of another language's region shows the label the app gives it. */
    function tagChip(tag, dictionary) {
        const chip = element('span', 'tag', tag);
        chip.dataset.dictionary = dictionary;
        chip.dataset.tag = tag;
        markTag(chip);
        return chip;
    }

    function markTag(chip) {
        chip.textContent = tagNotes.get(chip.dataset.dictionary)?.labels.get(chip.dataset.tag) || chip.dataset.tag;
        chip.classList.toggle('described', Boolean(tagNote(chip)));
    }

    /** Tags inside a definition (Wiktionary's sense tags) take the labels of the dictionary's tag chips. */
    function markContentTags(body, dictionary) {
        body.querySelectorAll('[data-sc-content="tag"]').forEach(tag => {
            tag.dataset.dictionary = dictionary;
            tag.dataset.tag = tag.textContent;
            markContentTag(tag);
        });
    }

    /** A labelled tag loses its title, the tag's name in the dictionary's language, as a labelled chip has no note. */
    function markContentTag(tag) {
        const label = tagNotes.get(tag.dataset.dictionary)?.labels.get(tag.dataset.tag);
        if (!label) return;
        tag.textContent = label;
        tag.removeAttribute('title');
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
        orderInlineButtons(actions);
    }

    /** The translation button, then ⚠, then ✕ at the start of the first entry's buttons, whichever came first. */
    function orderInlineButtons(actions) {
        const first = [actions.querySelector(':scope > .action-translate'), actions.querySelector(':scope > .ocr-warning')];
        actions.prepend(...first.filter(Boolean));
    }

    // endregion

    // region Translation

    /** The translation button on the first view while it has something to translate. */
    function translatable(state) {
        return translationEnabled && history.length === 0 && Boolean(state) && !state.message;
    }

    function drawTranslation(state) {
        const shown = translatable(state);
        const pressed = String(shown && translation !== null);
        const label = state?.labels?.translate || 'Translate';
        translateButton.hidden = !shown || compact(state);
        translateButton.setAttribute('aria-pressed', pressed);
        translateButton.setAttribute('aria-label', label);
        translateButton.title = label;
        placeInlineTranslate(state, shown, pressed, label);
        translationBox.hidden = !shown || translation === null;
        if (translationBox.hidden) {
            translationBox.replaceChildren();
            drawnTranslation = null;
            return;
        }
        // Built again only when it changed: header redraws (OCR status) would lose its scroll and a tap on copy.
        if (translation === drawnTranslation) return;
        drawnTranslation = translation;
        translationBox.dataset.state = translation.state;
        if (translation.state === 'loading') {
            const loading = element('div', 'translation-loading');
            loading.append(element('div', 'spinner'), element('span', null, state.labels?.translating || ''));
            translationBox.replaceChildren(loading);
            return;
        }
        if (translation.state === 'error') {
            translationBox.replaceChildren(element('div', 'translation-error', translation.error));
            return;
        }
        const tools = element('span', 'translation-tools');
        const copy = iconButton('copy-translation', ICONS.copy, labelOf('copyTranslation'));
        copy.title = labelOf('copyTranslation');
        const copied = translation.sentence ? `${translation.sentence}\n${translation.text}` : translation.text;
        copy.addEventListener('click', () => ScreenlateBridge.onCopy(copied));
        tools.append(element('span', 'chip translation-service', translation.service), copy);
        const text = element('div', 'translation-text');
        text.append(tools, translation.text);
        translationBox.replaceChildren(text);
    }

    /** Without the header, the translation button goes to the first entry's buttons, before ⚠ and ✕. */
    function placeInlineTranslate(state, shown, pressed, label) {
        const actions = content.querySelector('.entry .entry-actions');
        let inline = content.querySelector('.entry-actions > .action-translate');
        if (!shown || !compact(state) || !actions) {
            inline?.remove();
            return;
        }
        if (!inline) {
            inline = iconButton('translate', ICONS.translate, label);
            inline.addEventListener('click', toggleTranslation);
            actions.prepend(inline);
            orderInlineButtons(actions);
        }
        inline.setAttribute('aria-pressed', pressed);
        inline.title = label;
    }

    function toggleTranslation() {
        if (translation !== null) {
            translation = null;
        } else {
            translationRequests += 1;
            translation = { request: translationRequests, state: 'loading' };
            ScreenlateBridge.onTranslate(translationRequests);
        }
        drawTranslation(current);
    }

    /** { enabled }: whether the translation button shows. */
    function setTranslation(config) {
        translationEnabled = Boolean(config?.enabled);
        if (!translationEnabled) translation = null;
        if (current) drawTranslation(current);
    }

    /**
     * The answer to onTranslate(request): { text, service, sentence } or { error }; an answer to an older request is
     * dropped. The copy button copies the sentence and the translation on two lines.
     */
    function showTranslation(request, result) {
        if (translation === null || translation.request !== request) return;
        translation = result?.error
            ? { request, state: 'error', error: result.error }
            : { request, state: 'done', text: result?.text || '', service: result?.service || '', sentence: result?.sentence || '' };
        if (history.length === 0) drawTranslation(current);
    }

    /** A script's tag picks the glyph forms of the language's fonts (zh-Hans, zh-Hant); without one the page's applies. */
    function setLang(node, lang) {
        if (lang) node.lang = lang;
        else node.removeAttribute('lang');
    }

    /** 'rtl', 'auto', or '' for the page's own direction. */
    function setDirection(node, dir) {
        if (dir) node.dir = dir;
        else node.removeAttribute('dir');
    }

    function entry(result, index, labels) {
        const term = result.term;
        const article = element('article', 'entry');
        article.dataset.index = String(index);
        setLang(article, result.lang);

        const head = element('div', 'entry-head');
        const expression = element('span', 'expression');
        const lang = result.lang || document.documentElement.lang;
        const segments = HeadwordReading.segments(lang, term.expression, term.reading);
        if (segments || renderer) {
            if (segments) drawSegments(expression, segments);
            else renderer.furigana(expression, term.expression, term.reading, 'kanji-char');
            expression.addEventListener('click', event => {
                const target = event.target.closest('.kanji-char');
                if (target) ScreenlateBridge.onKanji(target.textContent);
            });
        } else {
            expression.textContent = term.reading && term.reading !== term.expression
                ? `${term.expression}【${term.reading}】`
                : term.expression;
        }
        const category = firstPitchCategory(term);
        if (category) expression.dataset.pitchCategory = category;
        if (rightToLeft) expression.dir = 'rtl';
        head.append(expression);
        const after = HeadwordReading.after(lang, term.expression, term.reading);
        if (after) {
            const reading = element('span', 'reading-after', after);
            reading.dir = 'ltr';
            head.append(reading);
        }
        if (result.fallbackLanguage) {
            head.append(element('span', 'tag fallback-language', labels[`language_${result.fallbackLanguage}`] || result.fallbackLanguage));
        }
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

    /** Ruby from HeadwordReading: characters tappable like kanji, syllables marked with their tone. */
    function drawSegments(parent, segments) {
        for (const [text, parts] of segments) {
            const target = parts ? document.createElement('ruby') : parent;
            // The text between such characters stays one node, so that joined scripts (Arabic) shape across it.
            for (const piece of text.split(HAN_PIECES).filter(Boolean)) {
                target.append(HAN_CHARACTER.test(piece) ? element('span', 'kanji-char', piece) : piece);
            }
            if (!parts) continue;
            const rt = document.createElement('rt');
            rt.append(...syllableNodes(parts));
            target.append(rt);
            parent.append(target);
        }
    }

    /** Syllables as spans with data-tone for the tone colors, the text between them as it is. */
    function syllableNodes(parts) {
        return parts.map(part => {
            if (!part.tone) return part.text;
            const span = element('span', 'syllable', part.text);
            span.dataset.tone = String(part.tone);
            return span;
        });
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
                if (current?.noteWait) {
                    ScreenlateBridge.onNoteWaiting();
                    return;
                }
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

    // region Selection

    const selectionCopy = element('button', 'selection-copy');
    selectionCopy.hidden = true;
    document.body.append(selectionCopy);
    // Pressing the button must not clear the selection it copies.
    selectionCopy.addEventListener('pointerdown', event => event.preventDefault());
    selectionCopy.addEventListener('mousedown', event => event.preventDefault());
    selectionCopy.addEventListener('click', () => {
        const text = selectedText(true);
        if (text) ScreenlateBridge.onCopy(text);
        window.getSelection()?.removeAllRanges();
        selectionCopy.hidden = true;
    });
    document.addEventListener('selectionchange', placeSelectionCopy);
    content.addEventListener('scroll', placeSelectionCopy);

    /**
     * The selected text inside the entries, or '' when nothing there is selected. [withoutReadings] leaves out
     * furigana: the selection's text skips what is not rendered, so readings are hidden while it is taken.
     */
    function selectedText(withoutReadings = false) {
        const selection = window.getSelection();
        if (!selection || selection.isCollapsed || selection.rangeCount === 0) return '';
        if (!content.contains(selection.getRangeAt(0).commonAncestorContainer)) return '';
        if (!withoutReadings) return selection.toString().trim();
        document.documentElement.classList.add('copying');
        try {
            return selection.toString().trim();
        } finally {
            document.documentElement.classList.remove('copying');
        }
    }

    /** Shows Copy above the selection (below when there is no room above); inside the app the system toolbar does it. */
    function placeSelectionCopy() {
        if (document.documentElement.dataset.embedded === 'true' || !selectedText()) {
            selectionCopy.hidden = true;
            return;
        }
        selectionCopy.textContent = labelOf('copy');
        const rect = window.getSelection().getRangeAt(0).getBoundingClientRect();
        selectionCopy.hidden = false;
        const width = selectionCopy.offsetWidth;
        const height = selectionCopy.offsetHeight;
        const above = rect.top - height - SELECTION_GAP;
        const top = above >= 0 ? above : Math.min(rect.bottom + SELECTION_GAP, window.innerHeight - height);
        const left = Math.max(4, Math.min(rect.left + rect.width / 2 - width / 2, window.innerWidth - width - 4));
        selectionCopy.style.top = `${top}px`;
        selectionCopy.style.left = `${left}px`;
    }

    // endregion

    /**
     * Audio clips of entry [index] for the menu opened by holding 🔊. items: [{ id, label, detail }];
     * loading: true while sources are still being asked.
     */
    function showAudioMenu(index, items, loading) {
        const button = content.querySelector(`.action-play[data-index="${index}"]`);
        if (!button) return;
        // The clips fill the menu that is waiting for them; once it was closed, they are not shown.
        const waiting = menu && menu.dataset.kind === 'audio' && menu.dataset.anchor === String(index);
        if (!loading && !waiting) return;
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
        setLang(view, kanji.lang);
        view.append(element('div', 'kanji-character', kanji.character));
        for (const entry of kanji.entries || []) {
            const section = element('section', 'dictionary');
            section.append(element('div', 'dictionary-name', entry.dictionary));
            // Lists the character is on (jouyou, 교육용), as Yomitan shows them; a tap opens the tag's note.
            const tags = (entry.tags || '').split(' ').filter(Boolean);
            if (tags.length) {
                const tagRow = element('div', 'tags kanji-tags');
                tags.forEach(tag => tagRow.append(tagChip(tag, entry.dictionary)));
                section.append(tagRow);
            }
            const readings = [
                [labels.onyomi || 'On', entry.onyomi],
                [labels.kunyomi || 'Kun', entry.kunyomi],
            ];
            for (const [label, value] of readings) {
                if (!value) continue;
                const row = element('div', 'kanji-row');
                row.append(element('span', 'kanji-label', label));
                row.append(kanjiReadings(value, kanji.lang));
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

    /** A character's readings separated by 、; syllables marked with their tone where the language has them. */
    function kanjiReadings(value, lang) {
        const readings = element('span', 'kanji-readings');
        value.split(' ').filter(Boolean).forEach((reading, i) => {
            if (i > 0) readings.append('、');
            const parts = HeadwordReading.syllables(lang || document.documentElement.lang, reading);
            readings.append(...(parts ? syllableNodes(parts) : [reading]));
        });
        return readings;
    }

    /**
     * Inflection, frequency, pitch accent and transcription in one row: beside the word when the popup is wide, below
     * it when not. The first rule chain is shown, one frequency value, the most frequent of the first group (the lookup
     * puts the sort dictionary first), and up to [SHOWN_ACCENTS] accents or transcriptions; "+N" reveals the hidden
     * chains, values, accents or transcriptions (up to [SHOWN_TRANSCRIPTIONS] of these, then "Show all"), "−" hides
     * them again.
     */
    function entryInfo(result, labels) {
        const row = element('div', 'entry-info');
        const chains = [result.trace, ...(result.otherTraces || [])]
            .map(trace => (trace || []).filter(step => step.name))
            .filter(trace => trace.length);
        if (chains.length) {
            row.append(collapsible([inflectionChain(chains[0])], chains.map(inflectionChain), chains.length - 1, 'inflection-more'));
        }

        const frequencies = (result.term.frequencies || [])
            .map(group => ({ dictionary: group.dictionary, values: frequencyValues(group) }))
            .filter(group => group.values.length);
        if (frequencies.length) {
            const first = frequencies[0];
            const hidden = frequencies.reduce((count, group) => count + group.values.length, 0) - 1;
            row.append(collapsible(
                [frequencyChip(first.dictionary, first.values.slice(0, 1))],
                frequencies.map((group, i) => frequencyChip(group.dictionary, group.values, i === 0)),
                hidden,
                'frequency-more',
            ));
        }

        const accents = pitchAccents(result.term).map(pitch => {
            const item = element('button', 'pitch-item');
            item.type = 'button';
            item.append(pitch.graph);
            item.append(element('span', 'pitch-position', `[${pitch.downsteps}]`));
            if (pitch.category) item.dataset.pitchCategory = pitch.category;
            item.addEventListener('click', () => showInfo(labels.pitchDictionaries || '', pitch.dictionaries.join('\n')));
            return item;
        });
        if (accents.length) {
            const shown = accents.length > SHOWN_ACCENTS ? 1 : accents.length;
            row.append(collapsible(accents.slice(0, shown), accents, accents.length - shown, 'pitch-more'));
        }

        const transcriptions = phoneticTranscriptions(result.term).map(item => {
            const chip = element('button', 'transcription');
            chip.type = 'button';
            for (const label of item.labels) chip.append(element('span', 'transcription-region', label));
            chip.append(document.createTextNode(item.ipa));
            chip.addEventListener('click', () => showInfo(labels.transcriptionDictionaries || '', item.dictionaries.join('\n')));
            return chip;
        });
        if (transcriptions.length > SHOWN_TRANSCRIPTIONS) {
            row.append(collapsible(
                transcriptions.slice(0, 1),
                transcriptions.slice(0, SHOWN_TRANSCRIPTIONS),
                transcriptions.length - 1,
                'pitch-more',
                { items: transcriptions, label: labels.showAll || '…' },
            ));
        } else if (transcriptions.length) {
            const shown = transcriptions.length > SHOWN_ACCENTS ? 1 : transcriptions.length;
            row.append(collapsible(transcriptions.slice(0, shown), transcriptions, transcriptions.length - shown, 'pitch-more'));
        }
        return row.childNodes.length ? row : null;
    }

    /**
     * [collapsed] with a "+[hidden]" button that swaps them for [expanded] and a "−" button that swaps them back; with
     * [all] ({ items, label }), [expanded] also gets a button with the label that shows all the items.
     * The group itself takes no box, so its badges wrap with the rest of the row.
     */
    function collapsible(collapsed, expanded, hidden, buttonClass, all = null) {
        const group = element('span', 'badge-group');
        group.append(...collapsed);
        if (hidden <= 0) return group;
        const toggle = text => {
            const button = element('button', `badge-toggle ${buttonClass}`, text);
            button.type = 'button';
            return button;
        };
        const more = toggle(`+${hidden}`);
        const less = toggle('−');
        if (all) {
            const showAll = toggle(all.label);
            showAll.classList.add('show-all');
            showAll.addEventListener('click', () => group.replaceChildren(...all.items, less));
            more.addEventListener('click', () => group.replaceChildren(...expanded, showAll, less));
        } else {
            more.addEventListener('click', () => group.replaceChildren(...expanded, less));
        }
        less.addEventListener('click', () => group.replaceChildren(...collapsed, more));
        group.append(more);
        return group;
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

    /**
     * The group's values as shown, the most frequent first: the lowest rank, or the highest count for a dictionary that
     * counts occurrences. A dictionary may give several, e.g. one for the word written in kana (marked ㋕).
     */
    function frequencyValues(group) {
        const occurrences = noteConfig.frequencyModes?.[group.dictionary] === 'occurrence-based';
        return (group.values || [])
            .map(value => ({ text: value.displayValue || String(value.value), number: frequencyNumber(value) }))
            .sort((a, b) => {
                if ((a.number > 0) !== (b.number > 0)) return a.number > 0 ? -1 : 1;
                return occurrences ? b.number - a.number : a.number - b.number;
            })
            .map(value => value.text);
    }

    /** The value, or the leading digits of the display value when the value is missing; 0 when neither is a number. */
    function frequencyNumber(value) {
        if (value.value > 0) return value.value;
        const digits = /^\d+/.exec(value.displayValue || '');
        return digits ? Number.parseInt(digits[0], 10) : 0;
    }

    /**
     * The dictionary's name and [values]. A long name shows as its first character until a tap on the chip, which
     * shows it whole and, tapped again, shortens it; [whole] shows it whole from the start.
     */
    function frequencyChip(dictionary, values, whole = false) {
        const full = shortName(dictionary);
        const short = chipName(full);
        const value = element('span', 'frequency-value', values.join(', '));
        if (short === full) {
            const chip = element('span', 'frequency');
            chip.append(element('span', 'frequency-dictionary', full), value);
            return chip;
        }
        const chip = element('button', 'frequency');
        chip.type = 'button';
        chip.setAttribute('aria-label', `${full} ${value.textContent}`);
        const name = element('span', 'frequency-dictionary', whole ? full : short);
        let shown = whole;
        chip.addEventListener('click', () => {
            shown = !shown;
            name.textContent = shown ? full : short;
        });
        chip.append(name, value);
        return chip;
    }

    /**
     * [name] when it is up to [SHORT_NAME_WIDTH] wide (JPDB, NieR), else its first character in upper case (BCCWJ → B,
     * wordfreq zh → W, 国語辞典 → 国), by the case rules of the page's language (ingilizce → İ in Turkish).
     */
    function chipName(name) {
        const characters = Array.from(name.trim());
        const width = characters.reduce((sum, character) => sum + (isWide(character) ? 2 : 1), 0);
        return width <= SHORT_NAME_WIDTH || !characters.length ? name : upperCase(characters[0]);
    }

    function upperCase(text) {
        const lang = document.documentElement.lang;
        try {
            return lang ? text.toLocaleUpperCase(lang) : text.toUpperCase();
        } catch (e) {
            // Not a language tag the browser knows.
            return text.toUpperCase();
        }
    }

    /** Whether [character] takes the width of two Latin letters: CJK characters, kana, hangul, full-width forms. */
    function isWide(character) {
        const code = character.codePointAt(0);
        return (code >= 0x1100 && code <= 0x115f) || (code >= 0x2e80 && code <= 0xa4cf)
            || (code >= 0xac00 && code <= 0xd7a3) || (code >= 0xf900 && code <= 0xfaff)
            || (code >= 0xfe30 && code <= 0xfe4f) || (code >= 0xff00 && code <= 0xff60)
            || (code >= 0xffe0 && code <= 0xffe6) || (code >= 0x20000 && code <= 0x3fffd);
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
                        category: pitchCategory(term, accent),
                        dictionaries: [],
                    });
                }
                const dictionaries = accents.get(key).dictionaries;
                if (!dictionaries.includes(group.dictionary)) dictionaries.push(group.dictionary);
            }
        }
        return [...accents.values()];
    }

    /** The type of [accent] of the word (render.js pitchCategory); null when it has none, such as a verb's heiban. */
    function pitchCategory(term, accent) {
        return renderer.pitchCategory(term.reading || term.expression, accent.pattern || accent.position, term.rules) || null;
    }

    /** The type of the word's first pitch accent in dictionary order; null without one. */
    function firstPitchCategory(term) {
        if (!renderer) return null;
        const accent = (term.pitches || []).flatMap(group => group.pitches || [])[0];
        return accent ? pitchCategory(term, accent) : null;
    }

    /**
     * Distinct phonetic transcriptions of the word (Yomitan's `ipa` term meta) with the labels of the regions their tags
     * name and the dictionaries that list each one. They follow the order of the audio regions (`noteConfig.regions`);
     * those without a region come last. A language without audio regions keeps the dictionary's order, where the
     * standard form usually has no region (Arabic /ki.taːb/ before Egyptian [keˈtæːb]).
     */
    function phoneticTranscriptions(term) {
        const transcriptions = new Map();
        for (const group of term.pitches || []) {
            (group.transcriptions || []).forEach((ipa, i) => {
                if (!transcriptions.has(ipa)) transcriptions.set(ipa, { ipa, regions: [], dictionaries: [] });
                const item = transcriptions.get(ipa);
                for (const region of group.transcriptionRegions?.[i] || []) {
                    if (!item.regions.includes(region)) item.regions.push(region);
                }
                if (!item.dictionaries.includes(group.dictionary)) item.dictionaries.push(group.dictionary);
            });
        }
        const order = noteConfig.regions || [];
        return [...transcriptions.values()]
            .map((item, index) => {
                const ranked = item.regions
                    .map((code, i) => ({ code, i, rank: regionRank(code, order) }))
                    .sort((a, b) => a.rank - b.rank || a.i - b.i);
                const labels = [...new Set(ranked.map(region => regionLabel(region.code, order)))];
                const rank = !order.length ? 0 : ranked.length ? ranked[0].rank : Number.MAX_SAFE_INTEGER;
                return { ipa: item.ipa, labels, dictionaries: item.dictionaries, rank, index };
            })
            .sort((a, b) => a.rank - b.rank || a.index - b.index);
    }

    /** The position of the audio region that has [code]; regions not listed go where `other` is, or last. */
    function regionRank(code, order) {
        const listed = order.findIndex(region => (region.codes || []).includes(code));
        if (listed >= 0) return listed;
        const other = order.findIndex(region => region.id === 'other');
        return other >= 0 ? other : order.length;
    }

    /** The name of the audio region that has [code] (`gb` is in `uk`), or the code itself, in capitals. */
    function regionLabel(code, order) {
        const region = order.find(item => (item.codes || []).includes(code));
        return (region ? region.id : code).toUpperCase();
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
        // Placed into the first block, whose own direction its name would otherwise decide.
        if (rightToLeft) name.dir = 'auto';
        const copy = element('button', 'copy-definition');
        copy.type = 'button';
        copy.innerHTML = ICONS.copy;
        copy.setAttribute('aria-label', labelOf('copyDefinition'));
        copy.addEventListener('click', () => copyDefinition(glossaries));
        const container = glossaries.length > 1 ? element('ol', 'definitions') : section;
        for (const glossary of glossaries) {
            const definition = glossaries.length > 1 ? element('li', 'definition') : element('div', 'definition');
            if (glossary === glossaries[0]) definition.append(name, copy);
            const tags = (glossary.definitionTags || '').split(' ').filter(Boolean);
            if (tags.length) {
                const tagRow = element('span', 'tags');
                tags.forEach(tag => tagRow.append(tagChip(tag, dictionary)));
                definition.append(tagRow);
            }
            const body = element('div', 'definition-body');
            const content = glossaryContent(glossary);
            if (renderer) {
                renderer.renderGlossary(body, content, dictionary, renderOptions);
                markContentTags(body, dictionary);
            } else {
                body.textContent = plainText(content);
            }
            if (rightToLeft) {
                body.dir = 'auto';
                body.querySelectorAll(TEXT_BLOCKS).forEach(block => setDirection(block, block.dir || 'auto'));
            }
            definition.append(body);
            container.append(definition);
        }
        if (container !== section) section.append(container);
        return section;
    }

    /** One dictionary's definitions, in the mode the settings chose; the button shows only when copying is on. */
    function copyDefinition(glossaries) {
        const mode = document.documentElement.dataset.definitionCopy;
        if (!mode || typeof DefinitionCopy === 'undefined') return;
        const { text, html } = DefinitionCopy.copy(glossaries, mode, document.documentElement.lang);
        if (text) ScreenlateBridge.onCopyDefinition(text, html);
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
        const copy = name.nextElementSibling?.classList.contains('copy-definition') ? name.nextElementSibling : null;
        name.remove();
        copy?.remove();
        for (;;) {
            const first = firstVisibleChild(container);
            if (opensBlock(first)) {
                container = first;
                continue;
            }
            container.insertBefore(name, first);
            if (copy) name.after(copy);
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

    /** { continued }: the popup stayed open, so a new scan result of the same word and sentence keeps the translation. */
    function render(state, options) {
        const first = history.length ? history[0].state : current;
        const same = state.sentence && state.sentence === first?.sentence && termsKey(state) === termsKey(first);
        if (!options?.continued || !same) translation = null;
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

    /**
     * Updates the first view (the app does not know about views pushed by links); pushed views keep their entries and
     * take the theme and OCR status, the ones under the top too, so going back does not bring an old status.
     */
    function update(state) {
        if (history.length) {
            const { theme, pending, noteWait, engine, ocrError } = state;
            const status = { theme, pending, noteWait, engine, ocrError };
            history[0].state = state;
            for (let i = 1; i < history.length; i++) history[i].state = { ...history[i].state, ...status };
            current = { ...current, ...status };
            drawHeader(current);
            return;
        }
        // Another word under the aim has another sentence; refined text around the same word keeps the translation.
        if (termsKey(state) !== termsKey(current)) translation = null;
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

    /** Drawing again loses the note buttons' states, so the app is told to mark them anew. */
    function back() {
        const previous = history.pop();
        if (!previous) return;
        current = previous.state;
        draw(previous.state);
        content.scrollTop = previous.scroll;
        ScreenlateBridge.onViewRestored();
    }

    /** Tag descriptions and chip labels by dictionary: [{ dictionary, notes: { tag: description }, labels: { tag: text } }]. */
    function setTagNotes(list) {
        tagNotes = new Map((list || []).map(item => [item.dictionary, {
            notes: new Map(Object.entries(item.notes || {})),
            labels: new Map(Object.entries(item.labels || {})),
        }]));
        content.querySelectorAll('.tag[data-tag]').forEach(markTag);
        content.querySelectorAll('.definition-body [data-sc-content="tag"][data-tag]').forEach(markContentTag);
    }

    function setStyles(newStyles) {
        styles = newStyles || [];
        if (!renderer) return;
        dictionaryStyles.textContent = styles
            .map(style => renderer.dictionaryCss(style.css, style.dictionary))
            .join('\n');
    }

    // region Heavier text

    const NORMAL_WEIGHT = 400;
    const BOLD_WEIGHT = 600;
    /** Elements whose text is not marked: editable fields, code, and SVG (such as pitch graphs), where spans do not belong. */
    const UNMARKED_TAGS = new Set(['SCRIPT', 'STYLE', 'TEXTAREA', 'INPUT', 'SELECT', 'OPTION']);
    const HTML_NAMESPACE = 'http://www.w3.org/1999/xhtml';

    /** { weight, runs } while the language's script is drawn heavier; runs matches a run of its characters. */
    let scriptRuns = null;
    let heavierKey = null;
    const scriptObserver = new MutationObserver(records => {
        const nodes = [];
        for (const record of records) {
            if (record.type === 'characterData') nodes.push(record.target);
            else nodes.push(...record.addedNodes);
        }
        markScript(nodes);
    });

    /**
     * { textWeight, textStroke, textScope, scriptPattern }: the CSS weight of normal text, the letter outline in em,
     * 'all' or 'script', and a character class of the language's script. Bold text stays at least as heavy.
     */
    function setHeavierText(appearance) {
        const weight = Math.min(Math.max(Number(appearance.textWeight) || NORMAL_WEIGHT, NORMAL_WEIGHT), 1000);
        const stroke = Math.max(Number(appearance.textStroke) || 0, 0);
        const on = weight > NORMAL_WEIGHT || stroke > 0;
        const scope = !on ? '' : appearance.textScope === 'all' ? 'all' : 'script';
        // The custom CSS may change the weights runs are compared with.
        const key = JSON.stringify([weight, stroke, scope, appearance.scriptPattern, appearance.customCss || '']);
        if (key === heavierKey) return;
        heavierKey = key;
        const root = document.documentElement;
        root.style.setProperty('--text-weight', String(weight));
        root.style.setProperty('--bold-weight', String(scope === 'all' ? Math.max(BOLD_WEIGHT, weight) : BOLD_WEIGHT));
        root.style.setProperty('--text-stroke', String(stroke));
        root.dataset.heavier = scope;
        scriptObserver.disconnect();
        scriptRuns = null;
        unmarkScript();
        if (scope !== 'script' || !appearance.scriptPattern) return;
        try {
            scriptRuns = { weight, runs: new RegExp(`${appearance.scriptPattern}+`, 'gu') };
        } catch (e) {
            return;
        }
        scriptObserver.observe(document.body, { childList: true, subtree: true, characterData: true });
        markScript([document.body]);
    }

    /**
     * Wraps the runs of the language's script in the text of [nodes] in .script-run spans. Weights are read before
     * anything changes, so style is computed once; a run whose text is already as heavy keeps its weight.
     */
    function markScript(nodes) {
        if (!scriptRuns) return;
        const texts = scriptTexts(nodes);
        const heavier = texts.map(text => scriptRuns.weight > NORMAL_WEIGHT && scriptRuns.weight > weightOf(text.parentElement));
        texts.forEach((text, i) => wrapRuns(text, heavier[i]));
        // The spans just added need no second look.
        scriptObserver.takeRecords();
    }

    function scriptTexts(nodes) {
        const test = new RegExp(scriptRuns.runs.source, 'u');
        const found = new Set();
        for (const node of nodes) {
            if (!node.isConnected) continue;
            if (node.nodeType === Node.TEXT_NODE) {
                if (test.test(node.data) && !insideUnmarked(node.parentElement)) found.add(node);
                continue;
            }
            if (node.nodeType !== Node.ELEMENT_NODE || insideUnmarked(node)) continue;
            const walker = document.createTreeWalker(node, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, {
                acceptNode: child => {
                    if (child.nodeType === Node.ELEMENT_NODE) return unmarked(child) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_SKIP;
                    return test.test(child.data) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP;
                },
            });
            for (let text = walker.nextNode(); text; text = walker.nextNode()) found.add(text);
        }
        return [...found];
    }

    function unmarked(node) {
        return node.namespaceURI !== HTML_NAMESPACE || UNMARKED_TAGS.has(node.tagName) ||
            node.hasAttribute('contenteditable') || node.classList.contains('script-run');
    }

    function insideUnmarked(node) {
        for (let current = node; current; current = current.parentElement) {
            if (unmarked(current)) return true;
        }
        return false;
    }

    function weightOf(node) {
        return node ? parseFloat(getComputedStyle(node).fontWeight) || NORMAL_WEIGHT : NORMAL_WEIGHT;
    }

    function wrapRuns(text, heavier) {
        const value = text.data;
        const fragment = document.createDocumentFragment();
        let last = 0;
        for (const match of value.matchAll(scriptRuns.runs)) {
            if (match.index > last) fragment.append(value.slice(last, match.index));
            fragment.append(element('span', heavier ? 'script-run heavier' : 'script-run', match[0]));
            last = match.index + match[0].length;
        }
        if (last < value.length) fragment.append(value.slice(last));
        text.replaceWith(fragment);
    }

    function unmarkScript() {
        const parents = new Set();
        document.querySelectorAll('.script-run').forEach(span => {
            parents.add(span.parentNode);
            span.replaceWith(...span.childNodes);
        });
        parents.forEach(parent => parent?.normalize());
    }

    // endregion

    /**
     * Language, fonts, text weight and the user's CSS: { lang, fontFaces, fontFamily, fontSize, textWeight, textStroke,
     * textScope, scriptPattern, customCss, preload, preloadText, definitionCopy, pronunciationColors }. The custom CSS comes after the
     * dictionaries' styles; preload names a font to load before it is first needed, for the characters of preloadText
     * (a face limited to the language's script loads only for them). See setHeavierText for the weight.
     * definitionCopy is the mode of the copy button next to each dictionary's name ('all' or 'meanings'); empty hides it.
     * pronunciationColors turns on the pitch accent colors, or the tone colors of Chinese and Cantonese.
     */
    function setAppearance(appearance) {
        const root = document.documentElement;
        root.lang = appearance.lang || '';
        root.dataset.definitionCopy = appearance.definitionCopy || '';
        root.dataset.pronunciationColors = String(Boolean(appearance.pronunciationColors));
        fontFaces.textContent = appearance.fontFaces || '';
        root.style.setProperty('--font-family', appearance.fontFamily || 'sans-serif');
        if (appearance.fontSize) root.style.setProperty('--font-size-no-units', String(appearance.fontSize));
        customCss.textContent = appearance.customCss || '';
        setHeavierText(appearance);
        if (appearance.preload && document.fonts) {
            const font = `1em "${appearance.preload.replace(/"/g, '\\"')}"`;
            document.fonts.load(font, appearance.preloadText || undefined).catch(() => {});
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
        if (!changed || !current) return;
        // Drawing again resets the ➕ buttons; the states they had carry over.
        const states = {};
        content.querySelectorAll('.action-add[data-index][data-state]').forEach(button => {
            states[button.dataset.index] = button.dataset.state;
        });
        drawResults(current);
        setNoteStates(states);
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
     * Which markers the note fields use, how frequency dictionaries count, and the audio regions in the user's order,
     * which also order transcriptions: { markers: [name], frequencyModes: { dictionary: 'rank-based' |
     * 'occurrence-based' }, regions: [{ id, codes: [code] }] }.
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
        setTranslation,
        showTranslation,
        allNoteData,
        terms,
        showAudioMenu,
        measureRender,
    };
})();

ScreenlateBridge.onReady();
