// Tests of the mutation exception candidates: node --test scripts/yomitan-language/mutation-exceptions.test.mjs
import assert from 'node:assert/strict';
import test from 'node:test';
import {deflateRawSync} from 'node:zlib';
import {candidates, corpusCounts, readDictionary, ruleResults, shares, termRows} from './mutation-exceptions.mjs';
import {irishMutations, welshTransforms} from './own-transforms.js';

/** A zip archive of [files] (name → JSON value), deflated when [deflate]. */
function zip(files, deflate) {
    const locals = [];
    const centrals = [];
    let offset = 0;
    for (const [name, value] of Object.entries(files)) {
        const raw = Buffer.from(JSON.stringify(value));
        const data = deflate ? deflateRawSync(raw) : raw;
        const nameBytes = Buffer.from(name);
        const local = Buffer.alloc(30);
        local.writeUInt32LE(0x04034b50, 0);
        local.writeUInt16LE(deflate ? 8 : 0, 8);
        local.writeUInt32LE(data.length, 18);
        local.writeUInt32LE(raw.length, 22);
        local.writeUInt16LE(nameBytes.length, 26);
        const central = Buffer.alloc(46);
        central.writeUInt32LE(0x02014b50, 0);
        central.writeUInt16LE(deflate ? 8 : 0, 10);
        central.writeUInt32LE(data.length, 20);
        central.writeUInt32LE(raw.length, 24);
        central.writeUInt16LE(nameBytes.length, 28);
        central.writeUInt32LE(offset, 42);
        locals.push(local, nameBytes, data);
        centrals.push(central, nameBytes);
        offset += local.length + nameBytes.length + data.length;
    }
    const directory = Buffer.concat(centrals);
    const end = Buffer.alloc(22);
    end.writeUInt32LE(0x06054b50, 0);
    end.writeUInt16LE(Object.keys(files).length, 8);
    end.writeUInt16LE(Object.keys(files).length, 10);
    end.writeUInt32LE(directory.length, 12);
    end.writeUInt32LE(offset, 16);
    return Buffer.concat([...locals, directory, end]);
}

const row = (head, items) => [head, '', '', '', 0, items, 0, ''];

test('reads the rows of every term bank, stored or deflated, and nothing else', () => {
    for (const deflate of [false, true]) {
        const archive = zip({
            'index.json': {title: 'x'},
            'term_bank_1.json': [row('bara', ['bread'])],
            'term_meta_bank_1.json': [['bara', 'ipa', {}]],
            'term_bank_2.json': [row('fara', [['bara', ['soft']]])],
        }, deflate);
        assert.deepEqual([...termRows(archive)].map((r) => r[0]), ['bara', 'fara']);
    }
    assert.throws(() => [...termRows(Buffer.from('not a zip at all, longer than the end record'))], /not a zip/);
});

test('a dictionary tells its headwords, those with a sense of their own and the lemmas of its form-of items', () => {
    const dictionary = readDictionary([
        row('ar', ['on', ['gar', ['soft']]]),
        row('fara', [['bara', ['soft']]]),
        row('mae’r', ['is the']),
    ]);
    assert.deepEqual([...dictionary.heads], ['ar', 'fara', "mae'r"]);
    assert.deepEqual([...dictionary.own], ['ar', "mae'r"]);
    assert.deepEqual([...dictionary.lemmas.get('ar')], ['gar']);
    assert.deepEqual([...dictionary.lemmas.get('fara')], ['bara']);
});

test('the rules give every text they turn a word into, other than the word', () => {
    assert.deepEqual(ruleResults(welshTransforms, 'fara').sort(), ['bara', 'mara']);
    assert.deepEqual(ruleResults(welshTransforms, 'nghath'), ['cath']);
    assert.deepEqual(ruleResults(welshTransforms, 'mae'), ['bae']);
    assert.deepEqual(ruleResults(welshTransforms, 'ysgol'), ['gysgol']);
    assert.deepEqual(ruleResults(welshTransforms, 'cath'), []);
});

test('the irish rules give the radical once, with the capital kept', () => {
    assert.deepEqual(ruleResults(irishMutations, 'bhean'), ['bean']);
    assert.deepEqual(ruleResults(irishMutations, 'nGaillimh'), ['Gaillimh']);
    assert.deepEqual(ruleResults(irishMutations, 'tsráid'), ['sráid']);
    assert.deepEqual(ruleResults(irishMutations, 'hÉireann'), ['Éireann']);
    assert.deepEqual(ruleResults(irishMutations, 'mbmbean'), ['bmbean']);
    assert.deepEqual(ruleResults(irishMutations, 'bean'), []);
});

test('the corpus counts lowercase words and the words before them, after the sentence number', () => {
    const {counts, before} = corpusCounts(['1\tMae ar y bwrdd.', '2\tAr y llawr mae’r gath, ar ben y bwrdd.']);
    assert.equal(counts.get('ar'), 2);
    assert.equal(counts.get('y'), 3);
    assert.equal(counts.get("mae'r"), 1);
    assert.equal(counts.has('Mae'), false);
    assert.equal(counts.has('1'), false);
    assert.deepEqual([...before.get('ar')].sort(), [['gath', 1], ['mae', 1]]);
    assert.deepEqual([...before.get('y')].sort(), [['ar', 2], ['ben', 1]]);
});

test('candidates split by what the english dictionary attests and by how common the word is', () => {
    const english = readDictionary([
        row('mae', ['is']),
        row('ar', ['on', ['gar', ['soft']]]),
        row('bobl', [['pobl', ['soft']]]),
        row('cath', ['cat']),
        row('bae', ['bay']),
        row('gar', ['shin']),
        row('pobl', ['people']),
    ]);
    const corpus = corpusCounts(['1\tmae ar bobl ar ar ar ar gar cath']);
    const groups = candidates(welshTransforms, corpus, english, english.heads, 10);
    assert.deepEqual(groups.free.map((c) => [c.word, c.results]), [['mae', ['bae']]]);
    assert.deepEqual(groups.common.map((c) => [c.word, c.count]), [['ar', 5]]);
    assert.deepEqual(groups.attested.map((c) => c.word), ['bobl']);
    assert.deepEqual(groups.common[0].before, [['ar', 60], ['bobl', 20], ['mae', 20]]);
    // Only the commonest words are looked at.
    assert.deepEqual(candidates(welshTransforms, corpus, english, english.heads, 1).common.map((c) => c.word), ['ar']);
});

test('shares count tokens the rule gives an extra entry or alone finds, leaving out the listed words', () => {
    const heads = new Set(['mae', 'bae', 'bara']);
    const counts = new Map([['mae', 3], ['fara', 1], ['y', 4]]);
    assert.deepEqual(shares(welshTransforms, counts, heads, new Set()), {extra: 3 / 8, ruleOnly: 1 / 8});
    assert.deepEqual(shares(welshTransforms, counts, heads, new Set(['mae'])), {extra: 0, ruleOnly: 1 / 8});
});
