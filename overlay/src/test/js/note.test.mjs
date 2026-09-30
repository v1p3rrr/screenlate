import assert from 'node:assert/strict';
import { test } from 'node:test';
import { notePage } from '../../../../scripts/page-tests/harness.mjs';

const page = notePage();
const NoteData = page.global('NoteData');

const result = {
    matched: '食べた',
    trace: [{ name: 'past' }],
    term: {
        expression: '食べる',
        reading: 'たべる',
        rules: 'v1',
        glossaries: [
            { dictionary: 'Jitendex [2026]', content: '["to eat", "to live on"]', definitionTags: 'v1', termTags: 'common' },
            {
                dictionary: 'Pictures',
                content: JSON.stringify([{ type: 'structured-content', content: { tag: 'img', path: 'img/eat.png', width: 10, height: 10 } }]),
            },
        ],
        frequencies: [
            { dictionary: 'JPDB', values: [{ value: 1, displayValue: '1000㋕' }] },
            { dictionary: 'BCCWJ', values: [{ value: 2000 }] },
            { dictionary: 'Occurrences', values: [{ value: 50 }] },
        ],
        pitches: [{ dictionary: 'Kanjium', pitches: [{ position: 2 }] }],
    },
};
const context = {
    frequencyModes: { JPDB: 'rank-based', BCCWJ: 'rank-based', Occurrences: 'occurrence-based' },
    query: '食べた<b>',
};

test('headword, furigana and reading markers', () => {
    const { values, term } = NoteData.build(result, context);
    assert.equal(values.expression, '食べる');
    assert.equal(values.reading, 'たべる');
    assert.equal(values.furigana, '<ruby>食<rt>た</rt></ruby>べる');
    assert.equal(values['furigana-plain'], '食[た]べる');
    assert.equal(values['cloze-body-kana'], 'たべた');
    assert.equal(values.conjugation, 'past');
    assert.equal(values['search-query'], '食べた&lt;b&gt;');
    assert.equal(values['part-of-speech'], 'Ichidan verb');
    assert.equal(values.tags, 'common, v1');
    assert.equal(values.dictionary, 'Jitendex [2026]');
    assert.deepEqual(JSON.parse(JSON.stringify(term)), { expression: '食べる', reading: 'たべる' });
});

test('frequency numbers follow the dictionary modes', () => {
    const { values } = NoteData.build(result, context);
    assert.equal(values['frequency-harmonic-rank'], '1333');
    assert.equal(values['frequency-average-rank'], '1500');
    assert.equal(values['frequency-harmonic-occurrence'], '50');
    assert.equal(values['single-frequency-number-jpdb'], '1000');
    assert.match(values.frequencies, /<li>JPDB: 1000㋕<\/li><li>BCCWJ: 2000<\/li>/);
    const none = NoteData.build({ term: { expression: 'x', glossaries: [] } }, {});
    assert.equal(none.values['frequency-harmonic-rank'], '9999999');
    assert.equal(none.values['frequency-average-occurrence'], '0');
});

test('glossary markers in Yomitan formats', () => {
    const { values } = NoteData.build(result, context);
    assert.match(values['glossary-first'], /<i>\(v1, Jitendex \[2026\]\)<\/i> <ul><li>to eat<\/li><li>to live on<\/li><\/ul>/);
    assert.match(values['glossary-first-brief'], /class="yomitan-glossary"><ul>/);
    assert.equal(values['glossary-plain'].split('<br>')[0], '(Jitendex [2026])');
    assert.match(values['glossary-plain-no-dictionary'], /^to eat<br>to live on/);
    assert.match(values['single-glossary-jitendex-2026'], /to eat/);
    assert.doesNotMatch(values['single-glossary-jitendex-2026'], /eat\.png|screenlate-media/);
    assert.match(values['glossary-jitendex'], /to eat/);
});

test('glossary images become media placeholders', () => {
    const { values, media } = NoteData.build(result, context, ['single-glossary-pictures']);
    assert.equal(media.length, 1);
    assert.deepEqual(JSON.parse(JSON.stringify(media[0])), {
        dictionary: 'Pictures',
        path: 'img/eat.png',
        placeholder: 'screenlate-media-0~',
    });
    assert.match(values['single-glossary-pictures'], /src="screenlate-media-0~"/);
});

test('pitch accent markers', () => {
    const { values } = NoteData.build(result, context);
    assert.match(values['pitch-accent-positions'], />2</);
    assert.equal(values['pitch-accent-categories'], 'kifuku');
    assert.match(values['pitch-accents'], /^<span style=/);
});

test('only the requested markers are computed', () => {
    const { values } = NoteData.build(result, context, ['expression', 'single-frequency-number-bccwj']);
    assert.deepEqual(Object.keys(values).sort(), ['expression', 'single-frequency-number-bccwj']);
    assert.equal(values['single-frequency-number-bccwj'], '2000');
});

test('sentence furigana marks each looked-up term', () => {
    const sentence = NoteData.sentenceFurigana([
        { text: '私は' },
        { text: '食べた', expression: '食べる', reading: 'たべる' },
    ]);
    assert.equal(sentence.html, '<span class="term">私は</span><span class="term"><ruby>食<rt>た</rt></ruby>べた</span>');
    assert.equal(sentence.plain, '私は 食[た]べた');
});

test('dictionary names in marker names', () => {
    assert.equal(NoteData.kebab('Kanjium Pitch Accents'), 'kanjium-pitch-accents');
    assert.equal(NoteData.kebab('大辞林　第四版'), '大辞林-第四版');
    assert.equal(NoteData.kebab('JMdict (English) [2026-01]'), 'jmdict-english-2026-01');
});

test('furigana markers escape the text', () => {
    const odd = { matched: 'A&B<', term: { expression: 'A&B<', reading: 'えー', glossaries: [] } };
    const { values } = NoteData.build(odd, {});
    assert.doesNotMatch(values.furigana, /A&B</);
    assert.match(values.furigana, /A&amp;B&lt;/);
    assert.match(values['furigana-plain'], /A&amp;B&lt;/);
    const sentence = NoteData.sentenceFurigana([{ text: '<b>&' }, { text: '食べた', expression: '食べる', reading: 'たべる' }]);
    assert.equal(sentence.html, '<span class="term">&lt;b&gt;&amp;</span><span class="term"><ruby>食<rt>た</rt></ruby>べた</span>');
    assert.equal(sentence.plain, '&lt;b&gt;&amp; 食[た]べた');
});
