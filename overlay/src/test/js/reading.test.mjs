import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readingPage } from '../../../../scripts/page-tests/harness.mjs';

const HeadwordReading = readingPage().global('HeadwordReading');

// Arrays from the page are copied into this realm, so deepEqual compares them by value.
const syllables = reading => Array.from(HeadwordReading.syllables('zh', reading) ?? [], part => part.tone ? `${part.text}${part.tone}` : part.text);
const segments = (expression, reading) => Array.from(HeadwordReading.segments('zh-Hant', expression, reading),
    ([text, parts]) => parts ? `${text}[${parts.map(part => part.text).join('')}]` : text);

test('pinyin splits into syllables with their tones, unmarked ones neutral', () => {
    assert.deepEqual(syllables('túshūguǎn'), ['tú2', 'shū1', 'guǎn3']);
    assert.deepEqual(syllables('tóufa'), ['tóu2', 'fa5']);
    assert.deepEqual(syllables('Běijīng'), ['Běi3', 'jīng1']);
    assert.deepEqual(syllables('zhōngguó'), ['zhōng1', 'guó2']);
});

test('a syllable starting with a vowel follows an apostrophe, never another syllable', () => {
    assert.deepEqual(syllables('fāngàn'), ['fān1', 'gàn4']);
    assert.deepEqual(syllables("Xī'ān"), ['Xī1', "'", 'ān1']);
    assert.deepEqual(syllables('xīnán'), ['xī1', 'nán2']);
});

test('ü, ê, spaces and the erhua r are kept', () => {
    assert.deepEqual(syllables("nǚ'ér"), ['nǚ3', "'", 'ér2']);
    assert.deepEqual(syllables('lǜ chá'), ['lǜ4', ' ', 'chá2']);
    assert.deepEqual(syllables('yīdiǎnr'), ['yī1', 'diǎnr3']);
    assert.deepEqual(syllables('ế'), ['ế2']);
});

test('text that is no pinyin gives nothing', () => {
    assert.equal(HeadwordReading.syllables('zh', 'xyz'), null);
    assert.equal(HeadwordReading.syllables('ja', 'たべる'), null);
});

test('each character gets its syllable', () => {
    assert.deepEqual(segments('圖書館', 'túshūguǎn'), ['圖[tú]', '書[shū]', '館[guǎn]']);
    assert.deepEqual(segments('头发', 'tóufa'), ['头[tóu]', '发[fa]']);
});

test('the erhua r goes over 儿', () => {
    assert.deepEqual(segments('一点儿', 'yīdiǎnr'), ['一[yī]', '点[diǎn]', '儿[r]']);
});

test('a word with letters or a reading that does not match gets one ruby', () => {
    assert.deepEqual(segments('卡拉OK', 'kǎlā ōukèi'), ['卡拉OK[kǎlā ōukèi]']);
    assert.deepEqual(segments('图书馆', 'túshū'), ['图书馆[túshū]']);
    assert.deepEqual(segments('图书馆', ''), ['图书馆']);
});

test('a language without rules has no segments', () => {
    assert.equal(HeadwordReading.segments('ja', '猫', 'ねこ'), null);
    assert.equal(HeadwordReading.segments('', '猫', 'ねこ'), null);
});
