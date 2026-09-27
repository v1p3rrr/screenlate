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
    assert.equal(row.querySelector('.frequency-more'), null);
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
    assert.equal(listed.nextElementSibling.textContent, 'aux');
    // Definition tags already start a line of text: the chip goes in front of them.
    assert.equal(tagged.nextElementSibling.className, 'tags');
});
