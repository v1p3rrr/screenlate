import assert from 'node:assert/strict';
import { test } from 'node:test';
import { renderPage } from '../../../../../scripts/page-tests/harness.mjs';

const { window } = renderPage();
const anki = window.YomitanAnki;
const plain = value => JSON.parse(JSON.stringify(value));

test('morae join small kana to the previous character', () => {
    assert.deepEqual(plain(anki.morae('きょうしゅう')), ['きょ', 'う', 'しゅ', 'う']);
});

test('downstep positions of high-low patterns', () => {
    assert.deepEqual(plain(anki.downstepPositions('HLL')), [1]);
    assert.deepEqual(plain(anki.downstepPositions('LHHL')), [3]);
    assert.deepEqual(plain(anki.downstepPositions('LHH')), [0]);
    assert.deepEqual(plain(anki.downstepPositions('HHH')), [-1]);
});

test('pitch categories follow the word class', () => {
    assert.equal(anki.pitchCategory('はし', 0, ['n']), 'heiban');
    assert.equal(anki.pitchCategory('はし', 1, ['n']), 'atamadaka');
    assert.equal(anki.pitchCategory('はし', 2, ['n']), 'odaka');
    assert.equal(anki.pitchCategory('たまご', 2, ['n']), 'nakadaka');
    assert.equal(anki.pitchCategory('たべる', 2, ['v1']), 'kifuku');
    assert.equal(anki.pitchCategory('たべる', 0, ['v1']), 'heiban');
    assert.equal(anki.pitchCategory('はし', 'HLL', ['n']), 'atamadaka');
});

test('a suru noun is not treated as a verb', () => {
    assert.equal(anki.isNonNounVerbOrAdjective(['vs', 'n']), false);
    assert.equal(anki.isNonNounVerbOrAdjective(['vs']), true);
    assert.equal(anki.isNonNounVerbOrAdjective(['adj-i']), true);
    assert.equal(anki.isNonNounVerbOrAdjective(['n']), false);
});

test('furigana follows an inflected source text', () => {
    assert.deepEqual(plain(anki.distributeFuriganaInflected('食べる', 'たべる', '食べた')), [['食', 'た'], ['べた', '']]);
    assert.deepEqual(plain(anki.distributeFuriganaInflected('たべる', 'たべる', 'たべた')), [['たべた', '']]);
    assert.deepEqual(plain(anki.distributeFuriganaInflected('食べる', 'たべる', '食べる')), [['食', 'た'], ['べる', '']]);
});

test('pronunciation text has inline styles and no classes or data attributes', () => {
    const html = anki.pronunciation('text', 'はし', 1);
    assert.match(html, /^<span style="display:inline;">/);
    assert.doesNotMatch(html, /class=|data-/);
    assert.match(html, /border-top-style:solid/);
    assert.match(html, />は<\/span>/);
});

test('pronunciation position and graphs', () => {
    assert.match(anki.pronunciation('position', 'はし', 2), /\[<\/span><span[^>]*>2<\/span><span[^>]*>]/);
    const graph = anki.pronunciation('graph', 'はし', 0);
    assert.match(graph, /^<svg/);
    assert.equal((graph.match(/<circle/g) || []).length, 2);
    assert.match(anki.pronunciation('graph-jj', 'きょう', 1), /<text[^>]*>き<\/text><text[^>]*>ょ<\/text>/);
    assert.equal(anki.pronunciation('unknown', 'はし', 0), '');
    assert.equal(anki.pronunciation('text', '', 0), '');
});

test('plain glossaries escape text and mark readings', () => {
    assert.equal(anki.glossaryPlain(['a<b', { type: 'text', text: 'c' }], 'D'), 'a&lt;b<br>c');
    const ruby = { type: 'structured-content', content: { tag: 'ruby', content: ['漢', { tag: 'rt', content: 'かん' }] } };
    assert.equal(anki.glossaryPlain([ruby], 'D'), '漢[かん]');
});

test('glossary markup inlines structured content styles', () => {
    const table = {
        type: 'structured-content',
        content: { tag: 'table', content: [{ tag: 'tr', content: [{ tag: 'td', content: 'x' }] }] },
    };
    const html = anki.glossaryHtml([table], 'D', {});
    assert.match(html, /<td[^>]*style="[^"]*border-style:solid/);
    assert.doesNotMatch(html, /class="gloss-sc/);
});

test('dictionary css is scoped to the glossary', () => {
    assert.match(anki.glossaryCss('.a { color: red }'), /\.yomitan-glossary \.a/);
});

test('statement at-rules do not swallow the next rule', () => {
    const css = window.YomitanRender.scopeCss('@charset "utf-8";\n@import url(x.css);\n.a { color: red }', '.p');
    assert.doesNotMatch(css, /@charset|@import/);
    assert.match(css, /\.p \.a \{ color: red \}/);
});

test('keyframe selectors are kept as they are', () => {
    const css = window.YomitanRender.scopeCss('@keyframes spin { from { opacity: 0 } 50% { opacity: 1 } }', '.p');
    assert.match(css, /from \{ opacity: 0 \}/);
    assert.doesNotMatch(css, /\.p from|\.p 50%/);
});

test('rules inside media queries are scoped', () => {
    const css = window.YomitanRender.scopeCss('@media (min-width: 1px) { .a { color: red } }', '.p');
    assert.match(css, /@media \(min-width: 1px\)/);
    assert.match(css, /\.p \.a/);
});

test('kanji outside the basic plane get furigana of their own', () => {
    const segments = window.YomitanRender.furiganaSegments('𠮟る', 'しかる');
    assert.deepEqual(plain(segments), [['𠮟', 'しか'], ['る', '']]);
});

test('structured content keeps only the style properties Yomitan allows', () => {
    const parent = window.document.createElement('div');
    const node = { type: 'structured-content', content: { tag: 'span', style: { color: 'red', position: 'fixed', marginTop: 1 }, content: 'x' } };
    window.YomitanRender.renderGlossary(parent, [node], 'D', {});
    const span = parent.querySelector('.gloss-sc-span');
    assert.equal(span.style.color, 'red');
    assert.equal(span.style.position, '');
    assert.equal(span.style.marginTop, '1em');
});
