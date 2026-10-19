// Checks the built language bundles (dictionary/engine-hoshidicts/src/main/assets/yomitan-language) the way the
// engine calls them: node --test scripts/yomitan-language
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';

const here = path.dirname(fileURLToPath(import.meta.url));
const assets = path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/assets/yomitan-language');

/** @param {string} language */
function load(language) {
    const context = vm.createContext({});
    vm.runInContext(fs.readFileSync(path.join(assets, `${language}.js`), 'utf8'), context);
    return context.yomitanLang;
}

/**
 * The texts the engine would query for [text].
 * @param {string} language
 * @param {string} text
 */
function queried(language, text) {
    const bundle = load(language);
    return new Set(bundle.candidates(text, language, 'word').map((c) => c.d));
}

test('turkish capitals lower to their own letters', () => {
    const texts = queried('tr', 'IŞIK');
    assert.ok(texts.has('ışık'));
    assert.ok(!texts.has('işik'));
    assert.ok(queried('tr', 'İSTANBUL').has('istanbul'));
    assert.ok(queried('tr', 'istanbul').has('İstanbul'));
    assert.ok(queried('tr', 'ılık').has('Ilık'));
});

test('other languages keep the default case pairs', () => {
    assert.ok(queried('en', 'IRIS').has('iris'));
    assert.ok(queried('de', 'insel').has('Insel'));
});

/**
 * The candidates the engine would query for [text], with the untyped flag.
 * @param {string} language
 * @param {string} text
 */
function candidates(language, text) {
    return load(language).candidates(text, language, 'word');
}

test('russian stress marks drop and yo matches ye only in russian', () => {
    const stressed = 'ко\u0301шка';
    assert.ok(queried('ru', stressed).has('кошка'));
    assert.ok(queried('ru', 'ёж').has('еж'));
    assert.ok(queried('ru', 'еж').has('ёж'));
    for (const language of ['en', 'de', 'pl', 'lv', 'nl', 'cs']) {
        assert.ok(!queried(language, stressed).has('кошка'), language);
        assert.ok(!queried(language, 'еж').has('ёж'), language);
    }
});

test('vietnamese tone marks match in the old and the new placement only in vietnamese', () => {
    const old = 'ho\u00e0';
    const current = 'h\u00f2a';
    assert.ok(queried('vi', old).has(current));
    assert.ok(queried('vi', current).has(old));
    assert.ok(queried('vi', 'hoa\u0300').has(current));
    for (const language of ['fr', 'es', 'pt', 'it']) {
        assert.ok(!queried(language, old).has(current), language);
    }
});

test('a vietnamese phrase with a tone mark typed apart keeps the text as written for its match', () => {
    const written = 'hoa\u0300 b\u00ecnh';
    const found = candidates('vi', written + '.').filter((c) => c.d === 'ho\u00e0 b\u00ecnh');
    assert.ok(found.length > 0);
    // The engine reports the match in the text it was given, so the app maps it back over the mark.
    assert.ok(found.every((c) => c.o === written));
});

test('english phrasal verbs are untyped candidates only in english', () => {
    const picked = candidates('en', 'picked up');
    assert.ok(picked.some((c) => c.d === 'pick up' && c.u));
    assert.ok(!candidates('en', 'closed to').some((c) => c.u));
    for (const language of ['de', 'fr', 'es']) {
        assert.ok(!candidates(language, 'picked up').some((c) => c.u), language);
    }
});

test('a kangxi radical reads as its ideograph in chinese', () => {
    assert.ok(queried('zh', '\u2f47本').has('日本'));
});

test('turkish case pairs do not reach english', () => {
    assert.ok(!queried('en', 'IŞIK').has('ışık'));
    assert.ok(queried('en', 'IŞIK').has('işik'));
});
