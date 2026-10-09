import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { popupPage } from '../../../../scripts/page-tests/harness.mjs';

const labels = { close: 'Close', copy: 'Copy', addNote: 'Add', openNote: 'Open note', openApp: 'Open settings', noResults: 'Nothing' };

function result(expression, reading, text = 'meaning') {
    return {
        matched: expression,
        term: { expression, reading, glossaries: [{ dictionary: 'Dict [1]', content: JSON.stringify([text]) }] },
    };
}

const state = (extra = {}) => ({
    theme: 'dark',
    source: { text: '猫が好き', matched: 1 },
    results: [result('猫', 'ねこ'), result('猫舌', 'ねこじた')],
    labels,
    ...extra,
});

let page;
let Popup;
let content;

beforeEach(() => {
    page = popupPage();
    Popup = page.global('Popup');
    content = page.document.getElementById('content');
});

const press = (button, type) => button.dispatchEvent(new page.window.Event(type, { bubbles: true }));
const tap = button => {
    press(button, 'pointerdown');
    press(button, 'pointerup');
};

test('the page reports that it is ready', () => {
    assert.deepEqual(page.calls[0], ['onReady']);
});

test('renders the header and one card per result', () => {
    Popup.render(state());
    const root = page.document.documentElement;
    assert.equal(root.dataset.theme, 'dark');
    assert.equal(root.dataset.compact, 'false');
    assert.equal(page.document.querySelector('#source .matched').textContent, '猫');
    assert.equal(page.document.getElementById('source').textContent, '猫が好き');
    assert.equal(content.querySelectorAll('article.entry').length, 2);
    const name = content.querySelector('.dictionary-name');
    assert.equal(name.textContent, 'Dict');
    assert.equal(name.title, 'Dict [1]');
    // The dictionary chip opens the first line of the definition instead of taking a line of its own.
    assert.equal(name.parentElement.className, 'definition-body');
    assert.equal(name.parentElement.firstChild, name);
    assert.match(content.querySelector('.definition-body').textContent, /meaning/);
    assert.equal(content.querySelectorAll('.action-close').length, 0);
});

test('the e-ink theme reaches the page root', () => {
    Popup.render(state({ theme: 'eink' }));
    assert.equal(page.document.documentElement.dataset.theme, 'eink');
});

test('without the header the first entry gets the close button', () => {
    Popup.render(state({ hideSource: true }));
    assert.equal(page.document.documentElement.dataset.compact, 'true');
    const entries = content.querySelectorAll('article.entry');
    assert.ok(entries[0].querySelector('.action-close'));
    assert.equal(entries[1].querySelector('.action-close'), null);
    entries[0].querySelector('.action-close').click();
    assert.deepEqual(page.calls.at(-1), ['onClose']);
});

test('a message keeps the header even when it is hidden', () => {
    Popup.render(state({ hideSource: true, results: [], message: 'No text here' }));
    assert.equal(page.document.documentElement.dataset.compact, 'false');
    assert.equal(content.textContent, 'No text here');
    Popup.render(state({ results: [] }));
    assert.equal(content.textContent, 'Nothing');
});

test('a tap on the note button sends the note data', () => {
    Popup.setActions({ anki: true, audio: true, ankiProblem: null });
    Popup.render(state());
    assert.equal(content.querySelectorAll('.action-play').length, 2);
    const add = content.querySelector('.action-add');
    tap(add);
    const [name, index, data, withScreenshot, force] = page.calls.at(-1);
    assert.equal(name, 'onAddNote');
    assert.equal(index, 0);
    assert.equal(JSON.parse(data).values.expression, '猫');
    assert.equal(withScreenshot, false);
    assert.equal(force, false);
    assert.equal(add.dataset.state, 'busy');
});

test('while the text is not final the note button is grey and asks for a hint', () => {
    Popup.setActions({ anki: true, audio: false, ankiProblem: null });
    Popup.render(state({ pending: true, noteWait: true }));
    assert.equal(page.document.documentElement.dataset.noteWait, 'true');
    const add = content.querySelector('.action-add');
    tap(add);
    assert.deepEqual(page.calls.at(-1), ['onNoteWaiting']);
    assert.equal(add.dataset.state, undefined);
    Popup.update(state({ pending: false, noteWait: false }));
    assert.equal(page.document.documentElement.dataset.noteWait, 'false');
    tap(add);
    assert.equal(page.calls.at(-1)[0], 'onAddNote');
});

test('a pushed view keeps waiting until the first one gets its final text', () => {
    Popup.setActions({ anki: true, audio: false, ankiProblem: null });
    Popup.render(state({ pending: true, noteWait: true }));
    Popup.push(state({ source: { text: '犬', matched: 1 }, results: [result('犬', 'いぬ')], noteWait: true }));
    Popup.update(state({ pending: false, noteWait: false }));
    tap(content.querySelector('.action-add'));
    assert.equal(page.calls.at(-1)[0], 'onAddNote');
});

test('added notes show the open button', () => {
    Popup.setActions({ anki: true, audio: false, ankiProblem: null });
    Popup.render(state());
    Popup.setNoteStates({ 0: 'added', 1: 'duplicate' });
    const [first, second] = content.querySelectorAll('.action-add');
    assert.equal(first.dataset.icon, 'open');
    assert.equal(first.getAttribute('aria-label'), 'Open note');
    assert.equal(second.dataset.icon, 'add');
    tap(first);
    assert.deepEqual(page.calls.at(-1), ['onOpenNote', 0]);
});

test('a broken Anki setup shows a grey button with the reason', () => {
    Popup.setActions({ anki: true, audio: false, ankiProblem: 'The note type is gone' });
    Popup.render(state());
    const add = content.querySelector('.action-add');
    assert.ok(add.classList.contains('unavailable'));
    assert.equal(add.getAttribute('aria-label'), 'The note type is gone');
    add.click();
    const box = content.querySelector('.note-problem');
    assert.match(box.textContent, /The note type is gone/);
    box.querySelector('button').click();
    assert.deepEqual(page.calls.at(-1), ['onOpenApp']);
    add.click();
    assert.equal(content.querySelector('.note-problem'), null);
});

test('links push a view and back restores the previous one', () => {
    Popup.render(state());
    const back = page.document.getElementById('back');
    assert.equal(back.hidden, true);
    Popup.push(state({ source: { text: '犬', matched: 1 }, results: [result('犬', 'いぬ')] }));
    assert.equal(back.hidden, false);
    assert.equal(content.querySelectorAll('article.entry').length, 1);
    back.click();
    assert.equal(back.hidden, true);
    assert.equal(content.querySelectorAll('article.entry').length, 2);
    // The note buttons were drawn anew, so the app marks them again.
    assert.deepEqual(page.calls.at(-1), ['onViewRestored']);
});

test('updates redraw only the header when the results are the same', () => {
    Popup.render(state({ pending: true }));
    const first = content.querySelector('article.entry');
    assert.equal(page.document.getElementById('spinner').hidden, false);
    Popup.update(state({ pending: false, engine: 'Lens' }));
    assert.equal(content.querySelector('article.entry'), first);
    assert.equal(page.document.getElementById('spinner').hidden, true);
    assert.equal(page.document.getElementById('engine').textContent, 'Lens');
    Popup.update(state({ results: [result('好き', 'すき')] }));
    assert.notEqual(content.querySelector('article.entry'), first);
});

test('kanji entries list readings and statistics', () => {
    Popup.render({
        theme: 'light',
        kanji: {
            character: '猫',
            entries: [{ dictionary: 'KANJIDIC', onyomi: 'ビョウ', kunyomi: 'ねこ', definitions: ['cat'], stats: { strokes: '11' } }],
        },
        labels: { onyomi: 'On', stat_strokes: 'Strokes' },
    });
    assert.equal(content.querySelector('.kanji-character').textContent, '猫');
    assert.equal(content.querySelector('.kanji-readings').textContent, 'ビョウ');
    assert.equal(content.querySelector('.frequency').textContent, 'Strokes11');
});

test('appearance sets the language, fonts and custom css', () => {
    Popup.setAppearance({
        lang: 'ja',
        fontFaces: '@font-face { font-family: "Screenlate Sans"; src: local("x"); }',
        fontFamily: '"Screenlate Sans", sans-serif',
        fontSize: 18,
        customCss: '.tag { color: red }',
    });
    const root = page.document.documentElement;
    assert.equal(root.lang, 'ja');
    assert.equal(root.style.getPropertyValue('--font-family'), '"Screenlate Sans", sans-serif');
    assert.equal(root.style.getPropertyValue('--font-size-no-units'), '18');
    assert.match(page.document.getElementById('font-faces').textContent, /Screenlate Sans/);
    assert.equal(page.document.getElementById('custom-css').textContent, '.tag { color: red }');
    const styles = [...page.document.head.querySelectorAll('style')].map(style => style.id);
    assert.ok(styles.indexOf('custom-css') > styles.indexOf('dictionary-styles'));
});

test('the chosen font is loaded for the sample text', () => {
    const loads = [];
    page.window.document.fonts = { load: (font, text) => { loads.push([font, text]); return Promise.resolve([]); } };
    Popup.setAppearance({ fontFamily: '"Screenlate Chosen", sans-serif', preload: 'Screenlate Chosen', preloadText: 'あ漢' });
    assert.deepEqual(loads, [['1em "Screenlate Chosen"', 'あ漢']]);
});

const heavier = (extra = {}) => ({
    lang: 'ja',
    fontFamily: '"Screenlate Sans", sans-serif',
    textWeight: 600,
    textStroke: 0.02,
    textScope: 'script',
    scriptPattern: '[\\u{3000}-\\u{30FF}\\u{4E00}-\\u{9FFF}]',
    ...extra,
});

/** Lets the page's mutation observer run. */
const settle = () => new Promise(resolve => setTimeout(resolve, 0));

const runs = node => [...node.querySelectorAll('.script-run')].map(span => span.textContent);

test('heavier text wraps runs of the script as they arrive', async () => {
    Popup.setAppearance(heavier());
    Popup.render(state({ results: [result('猫', 'ねこ', 'cat 猫舌 dog')] }));
    await settle();
    const root = page.document.documentElement;
    assert.equal(root.dataset.heavier, 'script');
    assert.equal(root.style.getPropertyValue('--text-weight'), '600');
    assert.equal(root.style.getPropertyValue('--bold-weight'), '600');
    assert.equal(root.style.getPropertyValue('--text-stroke'), '0.02');
    const body = content.querySelector('.definition-body');
    assert.deepEqual(runs(body), ['猫舌']);
    assert.equal(body.textContent.trim().endsWith('cat 猫舌 dog'), true);
    assert.ok(body.querySelector('.script-run').classList.contains('heavier'));
    assert.deepEqual(runs(page.document.getElementById('source')), ['猫', 'が好き']);

    // Text drawn later is marked too, and a kanji in the word still opens its entry.
    Popup.push(state({ source: { text: '犬', matched: 1 }, results: [result('犬', 'いぬ')] }));
    await settle();
    assert.deepEqual(runs(page.document.getElementById('source')), ['犬']);
    content.querySelector('.kanji-char .script-run').dispatchEvent(new page.window.MouseEvent('click', { bubbles: true }));
    assert.deepEqual(page.calls.at(-1), ['onKanji', '犬']);
});

test('heavier text leaves fields, graphs and bold text as they are', async () => {
    Popup.setAppearance(heavier());
    Popup.render(state());
    await settle();
    const extra = page.document.createElement('div');
    extra.innerHTML = '<textarea>猫</textarea><svg><text>猫</text></svg><b style="font-weight: 700">太字</b>';
    content.append(extra);
    await settle();
    assert.deepEqual(runs(extra), ['太字']);
    // The bold word is already heavier than the chosen weight: only the outline applies.
    assert.equal(extra.querySelector('.script-run').classList.contains('heavier'), false);
    assert.equal(extra.querySelector('textarea').value, '猫');
});

test('heavier text is looked at again when the custom css changes', async () => {
    Popup.setAppearance(heavier());
    Popup.render(state({ results: [result('猫', 'ねこ', 'cat 猫舌 dog')] }));
    await settle();
    const heavy = () => content.querySelector('.definition-body .script-run').classList.contains('heavier');
    assert.equal(heavy(), true);
    Popup.setAppearance(heavier({ customCss: '.definition-body { font-weight: 700 }' }));
    await settle();
    assert.equal(heavy(), false);
});

test('heavier text for all text or none drops the marks', async () => {
    Popup.setAppearance(heavier());
    Popup.render(state({ results: [result('猫', 'ねこ', 'cat 猫舌 dog')] }));
    await settle();
    const body = content.querySelector('.definition-body');
    const text = body.textContent;

    Popup.setAppearance(heavier({ textScope: 'all', textWeight: 700 }));
    const root = page.document.documentElement;
    assert.equal(root.dataset.heavier, 'all');
    assert.equal(root.style.getPropertyValue('--bold-weight'), '700');
    assert.equal(page.document.querySelectorAll('.script-run').length, 0);
    assert.equal(body.textContent, text);

    Popup.setAppearance(heavier({ textWeight: 400, textStroke: 0 }));
    assert.equal(root.dataset.heavier, '');
    Popup.render(state());
    await settle();
    assert.equal(page.document.querySelectorAll('.script-run').length, 0);
});

test('the definition copy button shows when copying is on and copies its dictionary', () => {
    const entry = result('猫', 'ねこ', 'cat');
    entry.term.glossaries.push({ dictionary: 'Other', content: JSON.stringify(['kitty']) });
    Popup.render(state({ results: [entry] }));
    const buttons = content.querySelectorAll('.copy-definition');
    assert.equal(buttons.length, 2);
    assert.equal(buttons[0].previousElementSibling.className, 'dictionary-name');
    buttons[0].click();
    assert.equal(page.calls.filter(call => call[0] === 'onCopyDefinition').length, 0);
    Popup.setAppearance({ definitionCopy: 'meanings' });
    assert.equal(page.document.documentElement.dataset.definitionCopy, 'meanings');
    buttons[1].click();
    assert.deepEqual(page.calls.at(-1), ['onCopyDefinition', 'kitty', '']);
    Popup.setAppearance({ definitionCopy: 'all' });
    buttons[0].click();
    assert.deepEqual(page.calls.at(-1), ['onCopyDefinition', 'cat', 'cat']);
});

test('the page language picks the markup rules of the copied meanings', () => {
    const entry = result('猫', 'ねこ', 'cat');
    entry.term.glossaries[0].content = JSON.stringify(['ねこ【猫】\n〘n〙 cat']);
    Popup.render(state({ results: [entry] }));
    const button = content.querySelector('.copy-definition');
    Popup.setAppearance({ lang: 'ja', definitionCopy: 'meanings' });
    button.click();
    assert.equal(page.calls.at(-1)[1], 'cat');
    Popup.setAppearance({ lang: '', definitionCopy: 'meanings' });
    button.click();
    assert.equal(page.calls.at(-1)[1], 'ねこ【猫】; 〘n〙 cat');
});

test('dictionary styles are scoped to their dictionary', () => {
    Popup.setStyles([{ dictionary: 'Dict [1]', css: '.x { color: blue }' }]);
    assert.match(page.document.getElementById('dictionary-styles').textContent, /Dict \[1\]/);
});

test('note data for the duplicate check', () => {
    Popup.render(state());
    const values = JSON.parse(JSON.stringify(Popup.allNoteData(['expression', 'reading'])));
    assert.deepEqual(values, [{ expression: '猫', reading: 'ねこ' }, { expression: '猫舌', reading: 'ねこじた' }]);
    assert.deepEqual(JSON.parse(JSON.stringify(Popup.terms())), [['猫', 'ねこ'], ['猫舌', 'ねこじた']]);
});

function detailed() {
    return {
        matched: '食べた',
        deinflected: '食べる',
        trace: [
            { name: '-た', label: '', description: 'Past tense.' },
            { name: 'n-slang', label: '', description: '' },
        ],
        term: {
            expression: '食べる',
            reading: 'たべる',
            frequencies: [
                { dictionary: 'Jiten', values: [{ value: 500, displayValue: '' }] },
                { dictionary: 'JPDB [2]', values: [{ value: 700, displayValue: '700㋕' }] },
            ],
            pitches: [
                { dictionary: 'Kanjium', pitches: [{ position: 2 }] },
                { dictionary: 'NHK', pitches: [{ position: 2 }, { position: 0 }] },
            ],
            glossaries: [{ dictionary: 'Dict [1]', content: JSON.stringify(['to eat']) }],
        },
    };
}

test('details sit in one row: inflection, the first frequency and distinct accents', () => {
    Popup.render(state({ results: [detailed()], labels: { ...labels, pitchDictionaries: 'Pitch from' } }));
    const head = content.querySelector('.entry-head');
    const row = head.querySelector('.entry-info');
    assert.ok(row);
    assert.equal(row.querySelectorAll('.frequency').length, 1);
    assert.equal(row.querySelector('.frequency').textContent, 'Jiten500');
    assert.equal(row.querySelectorAll('.pitch-item').length, 2);
    assert.equal(row.querySelector('.pitch-dictionary'), null);

    row.querySelector('.frequency-more').click();
    assert.deepEqual([...row.querySelectorAll('.frequency')].map(chip => chip.textContent), ['Jiten500', 'JPDB700㋕']);
    assert.equal(row.querySelector('.frequency-more').textContent, '−');
});

const chips = row => [...row.querySelectorAll('.frequency')].map(chip => chip.textContent);

function frequent(frequencies) {
    const result = detailed();
    result.term.frequencies = frequencies;
    result.term.pitches = [];
    return result;
}

test('one frequency value shows, the most frequent; "+N" counts the hidden values and "−" hides them again', () => {
    Popup.render(state({
        results: [frequent([
            { dictionary: 'Jiten', values: [{ value: 1066, displayValue: '1066㋕' }, { value: 39, displayValue: '39' }] },
            { dictionary: 'JPDB', values: [{ value: 120, displayValue: '120' }, { value: 4000, displayValue: '4000㋕' }] },
        ])],
    }));
    const row = content.querySelector('.entry-info');
    assert.deepEqual(chips(row), ['Jiten39']);
    const more = row.querySelector('.frequency-more');
    assert.equal(more.textContent, '+3');

    more.click();
    assert.deepEqual(chips(row), ['Jiten39, 1066㋕', 'JPDB120, 4000㋕']);
    const less = row.querySelector('.frequency-more');
    assert.equal(less.textContent, '−');
    assert.equal(row.lastElementChild.lastElementChild, less);

    less.click();
    assert.deepEqual(chips(row), ['Jiten39']);
    assert.equal(row.querySelector('.frequency-more').textContent, '+3');
});

test('a kana value alone in its dictionary needs no "+N"', () => {
    Popup.render(state({ results: [frequent([{ dictionary: 'Jiten', values: [{ value: 10, displayValue: '10㋕' }] }])] }));
    const row = content.querySelector('.entry-info');
    assert.deepEqual(chips(row), ['Jiten10㋕']);
    assert.equal(row.querySelector('.frequency-more'), null);
});

test('a dictionary counting occurrences shows its highest value first; values without a number go last', () => {
    Popup.setNoteConfig({ markers: null, frequencyModes: { Counts: 'occurrence-based' } });
    Popup.render(state({
        results: [frequent([
            { dictionary: 'Counts', values: [{ value: 3, displayValue: '' }, { value: 0, displayValue: '★' }, { value: 90, displayValue: '' }] },
        ])],
    }));
    const row = content.querySelector('.entry-info');
    assert.deepEqual(chips(row), ['Counts90']);
    row.querySelector('.frequency-more').click();
    assert.deepEqual(chips(row), ['Counts90, 3, ★']);
});

function accented(positions) {
    const result = detailed();
    result.term.frequencies = [];
    result.term.pitches = [{ dictionary: 'Kanjium', pitches: positions.map(position => ({ position })) }];
    return result;
}

test('two accents both show; three or more show the first, "+N" the rest and "−" hides them again', () => {
    Popup.render(state({ results: [accented([0, 2])] }));
    let row = content.querySelector('.entry-info');
    assert.equal(row.querySelectorAll('.pitch-item').length, 2);
    assert.equal(row.querySelector('.pitch-more'), null);

    Popup.render(state({ results: [accented([0, 2, 3, 1])] }));
    row = content.querySelector('.entry-info');
    const positions = () => [...row.querySelectorAll('.pitch-position')].map(item => item.textContent);
    assert.deepEqual(positions(), ['[0]']);
    const more = row.querySelector('.pitch-more');
    assert.equal(more.textContent, '+3');

    more.click();
    assert.equal(positions().length, 4);
    assert.equal(positions()[0], '[0]');
    const less = row.querySelector('.pitch-more');
    assert.equal(less.textContent, '−');

    less.click();
    assert.deepEqual(positions(), ['[0]']);
    assert.equal(row.querySelector('.pitch-more').textContent, '+3');
});

test('an inflection step or an accent opens the info panel', () => {
    Popup.render(state({ results: [detailed()], labels: { ...labels, pitchDictionaries: 'Pitch from' } }));
    const info = page.document.getElementById('info');
    assert.equal(info.hidden, true);
    const steps = content.querySelectorAll('.inflection-step');
    assert.equal(steps.length, 2);
    assert.ok(content.querySelector('.inflection-icon svg'));
    // A rule without a description is plain text.
    assert.equal(steps[1].tagName, 'SPAN');
    steps[0].click();
    assert.equal(info.hidden, false);
    assert.equal(page.document.getElementById('info-title').textContent, '-た');
    assert.equal(page.document.getElementById('info-text').textContent, 'Past tense.');

    content.querySelector('.pitch-item').click();
    assert.equal(page.document.getElementById('info-title').textContent, 'Pitch from');
    assert.equal(page.document.getElementById('info-text').textContent, 'Kanjium\nNHK');

    page.document.getElementById('info-close').click();
    assert.equal(info.hidden, true);
});

test('a label replaces the rule name and a new view closes the panel', () => {
    const result = detailed();
    result.trace = [{ name: 'passive', label: 'страдательная', description: 'Описание.' }];
    Popup.render(state({ results: [result] }));
    const step = content.querySelector('.inflection-step');
    assert.equal(step.textContent, 'страдательная');
    step.click();
    assert.equal(page.document.getElementById('info').hidden, false);
    Popup.render(state());
    assert.equal(page.document.getElementById('info').hidden, true);
});

test('the dictionary chip goes after a list marker and before tags, never inside a tag', () => {
    const list = {
        type: 'structured-content',
        content: { tag: 'ul', content: [{ tag: 'li', content: [{ tag: 'span', content: 'aux' }, ' will not'] }] },
    };
    const glossary = (dictionary, content, definitionTags = '') =>
        ({ matched: 'a', term: { expression: 'a', reading: 'a', glossaries: [{ dictionary, definitionTags, content: JSON.stringify(content) }] } });
    Popup.render(state({ results: [glossary('List', [list]), glossary('Tagged', ['one'], 'n')] }));
    const [listed, tagged] = content.querySelectorAll('.dictionary-name');
    // Inside the first list item, so the item's marker stays in front of it.
    assert.equal(listed.closest('li').textContent, 'Listaux will not');
    // The copy button moves with the chip.
    assert.equal(listed.nextElementSibling.className, 'copy-definition');
    assert.equal(listed.nextElementSibling.nextElementSibling.textContent, 'aux');
    // Definition tags already start a line of text: the chip goes in front of them.
    assert.equal(tagged.nextElementSibling.nextElementSibling.className, 'tags');
});

test('a cloud recognition failure shows ⚠, which opens its reason in the panel', () => {
    const failed = { ocrError: 'Cloud recognition did not answer.', labels: { ...labels, ocrError: 'Cloud recognition' } };
    Popup.render(state());
    const warning = page.document.getElementById('ocr-error');
    assert.equal(warning.hidden, true);

    Popup.update(state(failed));
    assert.equal(warning.hidden, false);
    warning.click();
    assert.equal(page.document.getElementById('info').hidden, false);
    assert.equal(page.document.getElementById('info-title').textContent, 'Cloud recognition');
    assert.equal(page.document.getElementById('info-text').textContent, 'Cloud recognition did not answer.');
});

test('without the header ⚠ sits before the close button of the first entry', () => {
    const failed = { hideSource: true, ocrError: 'Offline.', labels: { ...labels, ocrError: 'Cloud recognition' } };
    Popup.render(state({ hideSource: true }));
    assert.equal(content.querySelector('.ocr-warning'), null);

    // The final result may only update the header: the entry still gets its ⚠.
    Popup.update(state(failed));
    const actions = content.querySelector('article.entry .entry-actions');
    assert.ok(actions.firstChild.classList.contains('ocr-warning'));
    assert.ok(actions.firstChild.nextSibling.classList.contains('action-close'));
    actions.firstChild.click();
    assert.equal(page.document.getElementById('info-text').textContent, 'Offline.');

    Popup.update(state({ hideSource: true }));
    assert.equal(content.querySelector('.ocr-warning'), null);
});

test('a tag with a description opens it in the panel, one without stays plain', () => {
    const glossary = (dictionary, content, definitionTags) =>
        ({ matched: 'a', term: { expression: 'a', reading: 'a', glossaries: [{ dictionary, definitionTags, content: JSON.stringify(content) }] } });
    Popup.render(state({ results: [glossary('JMdict', ['one'], 'n vs')] }));
    Popup.setTagNotes([{ dictionary: 'JMdict', notes: { n: 'noun (common) (futsuumeishi)' } }]);
    const [noun, suru] = content.querySelectorAll('.tag');
    assert.ok(noun.classList.contains('described'));
    assert.equal(suru.classList.contains('described'), false);

    suru.click();
    assert.equal(page.document.getElementById('info').hidden, true);
    noun.click();
    assert.equal(page.document.getElementById('info').hidden, false);
    assert.equal(page.document.getElementById('info-title').textContent, 'n');
    assert.equal(page.document.getElementById('info-text').textContent, 'noun (common) (futsuumeishi)');
});

test('a structured-content label with a title opens the title in the panel', () => {
    const content_ = {
        type: 'structured-content',
        content: [
            { tag: 'span', title: 'noun (common) (futsuumeishi)', content: 'noun' },
            { tag: 'span', title: 'same', content: 'same' },
            ' library',
        ],
    };
    Popup.render(state({ results: [{ matched: 'a', term: { expression: 'a', reading: 'a', glossaries: [{ dictionary: 'Jitendex', content: JSON.stringify([content_]) }] } }] }));
    const [label, plain] = content.querySelectorAll('.definition-body .gloss-sc-span[title]');
    plain.click();
    assert.equal(page.document.getElementById('info').hidden, true);
    label.click();
    assert.equal(page.document.getElementById('info-title').textContent, 'noun');
    assert.equal(page.document.getElementById('info-text').textContent, 'noun (common) (futsuumeishi)');
    // The dictionary chip's title is its full name, not a description.
    page.document.getElementById('info').hidden = true;
    content.querySelector('.dictionary-name').click();
    assert.equal(page.document.getElementById('info').hidden, true);
});

test('a selection in the entries offers Copy, which sends the text without furigana', () => {
    const ruby = {
        type: 'structured-content',
        content: [{ tag: 'ruby', content: ['猫', { tag: 'rt', content: 'ねこ' }] }, ' is a cat'],
    };
    Popup.render(state({ results: [{ matched: '猫', term: { expression: '猫', reading: 'ねこ', glossaries: [{ dictionary: 'Dict', content: JSON.stringify([ruby]) }] } }] }));
    const copy = page.document.querySelector('.selection-copy');
    assert.equal(copy.hidden, true);
    // jsdom has no layout: a rect for placing the button.
    page.window.Range.prototype.getBoundingClientRect = () => ({ top: 100, bottom: 120, left: 10, width: 50 });

    const range = page.document.createRange();
    range.selectNodeContents(content.querySelector('.definition-body'));
    const selection = page.window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    page.document.dispatchEvent(new page.window.Event('selectionchange'));
    assert.equal(copy.hidden, false);

    copy.click();
    const [name, text] = page.calls.at(-1);
    assert.equal(name, 'onCopy');
    assert.match(text, /is a cat/);
    assert.equal(copy.hidden, true);
    assert.equal(page.window.getSelection().rangeCount, 0);
});

test('inside the app the system toolbar copies, so no Copy button appears', () => {
    Popup.configure({ embedded: true });
    Popup.render(state());
    const range = page.document.createRange();
    range.selectNodeContents(content.querySelector('.definition-body'));
    page.window.getSelection().addRange(range);
    page.document.dispatchEvent(new page.window.Event('selectionchange'));
    assert.equal(page.document.querySelector('.selection-copy').hidden, true);
});

test('audio clips do not reopen a menu that was closed while they loaded', () => {
    Popup.setActions({ anki: false, audio: true, ankiProblem: null });
    Popup.render(state());
    Popup.showAudioMenu(0, [], true);
    assert.ok(page.document.querySelector('.menu'));
    press(page.document.body, 'pointerdown');
    assert.equal(page.document.querySelector('.menu'), null);
    Popup.showAudioMenu(0, [{ id: 'a', label: 'Clip' }], false);
    assert.equal(page.document.querySelector('.menu'), null);
});

test('audio clips fill the menu waiting for them, and a new view closes it', () => {
    Popup.setActions({ anki: false, audio: true, ankiProblem: null });
    Popup.render(state());
    Popup.showAudioMenu(1, [], true);
    Popup.showAudioMenu(1, [{ id: 'a', label: 'Clip' }], false);
    const item = page.document.querySelector('.menu .menu-item');
    assert.equal(item.textContent, 'Clip');
    item.click();
    assert.deepEqual(page.calls.at(-1), ['onPlayClip', 1, 'a']);
    Popup.showAudioMenu(1, [], true);
    Popup.update(state({ results: [result('犬', 'いぬ')] }));
    assert.equal(page.document.querySelector('.menu'), null);
});

test('changing the entry buttons keeps the note states', () => {
    Popup.setActions({ anki: true, audio: false, ankiProblem: null });
    Popup.render(state());
    Popup.setNoteStates({ 1: 'added' });
    Popup.setActions({ anki: true, audio: true, ankiProblem: null });
    assert.equal(content.querySelectorAll('.action-play').length, 2);
    const [first, second] = content.querySelectorAll('.action-add');
    assert.equal(first.dataset.state, undefined);
    assert.equal(second.dataset.state, 'added');
});

test('an update while a link view is on top changes the first view and only the status of the top one', () => {
    Popup.render(state({ pending: true }));
    Popup.push(state({ source: { text: '犬', matched: 1 }, results: [result('犬', 'いぬ')] }));
    Popup.update(state({ theme: 'light', pending: false, engine: 'Lens', results: [result('好き', 'すき')] }));
    assert.equal(content.querySelectorAll('article.entry').length, 1);
    assert.match(content.textContent, /犬/);
    assert.equal(page.document.documentElement.dataset.theme, 'light');
    assert.equal(page.document.getElementById('spinner').hidden, true);
    assert.equal(page.document.getElementById('engine').textContent, 'Lens');
    page.document.getElementById('back').click();
    assert.equal(content.querySelectorAll('article.entry').length, 1);
    assert.match(content.textContent, /好すき/);
});

test('going back to a link view under the top one keeps the status of the last update', () => {
    Popup.render(state({ pending: true, noteWait: true }));
    Popup.push(state({ pending: true, noteWait: true, source: { text: '犬', matched: 1 }, results: [result('犬', 'いぬ')] }));
    Popup.push(state({ pending: true, noteWait: true, source: { text: '猿', matched: 1 }, results: [result('猿', 'さる')] }));
    Popup.update(state({ theme: 'light', pending: false, noteWait: false }));
    page.document.getElementById('back').click();
    assert.match(content.textContent, /犬/);
    assert.equal(page.document.documentElement.dataset.theme, 'light');
    assert.equal(page.document.documentElement.dataset.noteWait, 'false');
    assert.equal(page.document.getElementById('spinner').hidden, true);
});

const translateLabels = { ...labels, translate: 'Translate the sentence', translating: 'Translating…' };

test('文A stays hidden until the app turns it on', () => {
    Popup.render(state({ labels: translateLabels }));
    const button = page.document.getElementById('translate');
    assert.equal(button.hidden, true);
    Popup.setTranslation({ enabled: true });
    assert.equal(button.hidden, false);
    assert.equal(button.title, 'Translate the sentence');
    Popup.render(state({ labels: translateLabels, results: [], message: 'No text here' }));
    assert.equal(button.hidden, true);
});

test('a tap asks for the translation, the answer fills the block and a second tap hides it', () => {
    Popup.setTranslation({ enabled: true });
    Popup.render(state({ labels: translateLabels }));
    const button = page.document.getElementById('translate');
    const box = page.document.getElementById('translation');
    assert.equal(box.hidden, true);

    button.click();
    assert.deepEqual(page.calls.at(-1), ['onTranslate', 1]);
    assert.equal(box.hidden, false);
    assert.equal(box.dataset.state, 'loading');
    assert.match(box.textContent, /Translating…/);
    assert.equal(button.getAttribute('aria-pressed'), 'true');

    Popup.showTranslation(1, { text: 'I like cats', service: 'Bing' });
    assert.equal(box.dataset.state, 'done');
    assert.equal(box.querySelector('.translation-service').textContent, 'Bing');
    assert.match(box.textContent, /I like cats/);

    button.click();
    assert.equal(box.hidden, true);
    assert.equal(button.getAttribute('aria-pressed'), 'false');
    assert.equal(page.calls.filter(call => call[0] === 'onTranslate').length, 1);
});

test('the copy button copies the sentence and the translation on two lines', () => {
    Popup.setTranslation({ enabled: true });
    Popup.render(state({ labels: { ...translateLabels, copyTranslation: 'Copy sentence and translation' } }));
    const box = page.document.getElementById('translation');
    page.document.getElementById('translate').click();
    assert.equal(box.querySelector('.action-copy-translation'), null);

    Popup.showTranslation(1, { text: 'I like cats', service: 'Bing', sentence: '猫が好き' });
    const copy = box.querySelector('.translation-tools .action-copy-translation');
    assert.equal(copy.getAttribute('aria-label'), 'Copy sentence and translation');
    copy.click();
    assert.deepEqual(page.calls.at(-1), ['onCopy', '猫が好き\nI like cats']);
});

test('without a sentence the copy button copies the translation alone', () => {
    Popup.setTranslation({ enabled: true });
    Popup.render(state({ labels: translateLabels }));
    page.document.getElementById('translate').click();
    Popup.showTranslation(1, { text: 'I like cats', service: 'Bing' });
    page.document.querySelector('.action-copy-translation').click();
    assert.deepEqual(page.calls.at(-1), ['onCopy', 'I like cats']);
});

test('an error shows in the block; an answer to an older request is dropped', () => {
    Popup.setTranslation({ enabled: true });
    Popup.render(state({ labels: translateLabels }));
    const button = page.document.getElementById('translate');
    const box = page.document.getElementById('translation');
    button.click();
    button.click();
    button.click();
    assert.deepEqual(page.calls.at(-1), ['onTranslate', 2]);
    Popup.showTranslation(1, { text: 'old' });
    assert.equal(box.dataset.state, 'loading');
    Popup.showTranslation(2, { error: 'No connection.' });
    assert.equal(box.dataset.state, 'error');
    assert.equal(box.textContent, 'No connection.');
});

test('a new word drops the translation; refined text around the same word keeps it', () => {
    Popup.setTranslation({ enabled: true });
    Popup.render(state({ labels: translateLabels }));
    const box = page.document.getElementById('translation');
    page.document.getElementById('translate').click();
    Popup.showTranslation(1, { text: 'I like cats', service: 'Bing' });

    Popup.update(state({ labels: translateLabels, source: { text: '猫が好きです', matched: 1 } }));
    assert.equal(box.hidden, false);
    Popup.update(state({ labels: translateLabels, results: [result('犬', 'いぬ')] }));
    assert.equal(box.hidden, true);

    page.document.getElementById('translate').click();
    Popup.render(state({ labels: translateLabels }));
    assert.equal(box.hidden, true);
    Popup.showTranslation(2, { text: 'late' });
    assert.equal(box.hidden, true);
});

test('a new scan result with the same word and sentence keeps the translation while the popup stays open', () => {
    const sentence = '猫が好きです。';
    Popup.setTranslation({ enabled: true });
    Popup.render(state({ labels: translateLabels, sentence }));
    const box = page.document.getElementById('translation');
    page.document.getElementById('translate').click();
    Popup.showTranslation(1, { text: 'I like cats', service: 'Bing', sentence });

    Popup.render(state({ labels: translateLabels, sentence, engine: 'Lens' }), { continued: true });
    assert.equal(box.hidden, false);
    assert.match(box.textContent, /I like cats/);

    Popup.render(state({ labels: translateLabels, sentence: '猫がいます。' }), { continued: true });
    assert.equal(box.hidden, true);
});

test('a popup opened again starts without the translation, also for the same sentence', () => {
    const sentence = '猫が好きです。';
    Popup.setTranslation({ enabled: true });
    Popup.render(state({ labels: translateLabels, sentence }));
    const box = page.document.getElementById('translation');
    page.document.getElementById('translate').click();
    Popup.showTranslation(1, { text: 'I like cats', service: 'Bing', sentence });

    Popup.render(state({ labels: translateLabels, sentence }));
    assert.equal(box.hidden, true);
});

test('a pushed view hides the translation until back returns to the first view', () => {
    Popup.setTranslation({ enabled: true });
    Popup.render(state({ labels: translateLabels }));
    const button = page.document.getElementById('translate');
    const box = page.document.getElementById('translation');
    button.click();
    Popup.showTranslation(1, { text: 'I like cats', service: 'Google' });
    Popup.push(state({ labels: translateLabels, results: [result('好き', 'すき')] }));
    assert.equal(box.hidden, true);
    assert.equal(button.hidden, true);
    page.document.getElementById('back').click();
    assert.equal(box.hidden, false);
    assert.match(box.textContent, /I like cats/);
});

test('without the header 文A sits first in the first entry, before ⚠ and ✕', () => {
    const failed = { hideSource: true, ocrError: 'Offline.', labels: { ...translateLabels, ocrError: 'Cloud recognition' } };
    Popup.render(state({ hideSource: true, labels: translateLabels }));
    Popup.setTranslation({ enabled: true });
    assert.equal(page.document.getElementById('translate').hidden, true);
    Popup.update(state(failed));
    const actions = content.querySelector('article.entry .entry-actions');
    const classes = [...actions.children].slice(0, 3).map(button => button.className);
    assert.deepEqual(classes, ['icon-button action-translate', 'icon-button action-ocr-warning ocr-warning', 'icon-button action-close']);
    assert.equal(content.querySelectorAll('.action-translate').length, 1);

    actions.firstChild.click();
    assert.deepEqual(page.calls.at(-1), ['onTranslate', 1]);
    assert.equal(page.document.getElementById('translation').hidden, false);
    assert.equal(actions.firstChild.getAttribute('aria-pressed'), 'true');

    Popup.setTranslation({ enabled: false });
    assert.equal(content.querySelector('.action-translate'), null);
    assert.equal(page.document.getElementById('translation').hidden, true);
});
