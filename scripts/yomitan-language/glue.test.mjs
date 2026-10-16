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
