// node --test scripts/catalog/generate.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import {
  archives, wiktionaryEntries, rebuild, legacy, fill, TEMPLATE_MAIN, TEMPLATE_TRANSCRIPTION,
} from './generate.mjs';

const listing = JSON.parse(await readFile(new URL('./test-tree.json', import.meta.url), 'utf8'));

const manual = [{
  id: 'jitendex', title: 'Jitendex', installedTitle: 'Jitendex.org', kind: 'TERM', sourceLanguage: 'ja',
  targetLanguage: 'en', recommended: true, description: { en: 'Japanese–English' }, downloadUrl: 'https://x/j.zip',
  downloadSize: 40 * 1024 * 1024, license: 'CC BY-SA 4.0', homepage: 'https://jitendex.org',
}];

test('the recorded tree gives main, glossary and transcription entries above the threshold', () => {
  const entries = wiktionaryEntries(archives(listing), manual);
  assert.deepEqual(entries.map((e) => e.id), [
    'wiktionary-ja-en', 'wiktionary-ja-ru',
    'wiktionary-en-en', 'wiktionary-en-ru', 'wiktionary-en-ru-glossary', 'wiktionary-en-transcription',
    'wiktionary-zh-transcription',
  ]);
  const byId = Object.fromEntries(entries.map((e) => [e.id, e]));
  // Jitendex is the recommended Japanese–English dictionary; Wiktionary is for the other pairs.
  assert.equal(byId['wiktionary-ja-en'].recommended, false);
  assert.equal(byId['wiktionary-en-ru'].recommended, true);
  assert.equal(byId['wiktionary-en-ru-glossary'].recommended, undefined);
  assert.equal(byId['wiktionary-en-transcription'].recommended, true);
  assert.equal(byId['wiktionary-zh-transcription'].recommended, false);
  // The merged transcription comes from dict/all, never from the MDict build next to it.
  assert.equal(byId['wiktionary-zh-transcription'].downloadSize, 39356932);
  assert.match(byId['wiktionary-en-transcription'].downloadUrl, /\/dict\/all\/en\/wty-en-ipa\.zip/);
  assert.equal(byId['wiktionary-en-en'].downloadSize, 107871321);
  assert.deepEqual(byId['wiktionary-en-ru'].oldTitles, ['wty-en-ru', 'kty-en-ru']);
  assert.equal(byId['wiktionary-en-transcription'].targetLanguage, undefined);
});

test('rebuilding keeps hand-written entries first and replaces the Wiktionary ones', () => {
  const stale = { id: 'wiktionary-en-de', template: TEMPLATE_MAIN };
  const document = { format: 2, templates: {}, dictionaries: [manual[0], stale] };
  const rebuilt = rebuild(document, wiktionaryEntries(archives(listing), manual));
  assert.equal(rebuilt.dictionaries[0].id, 'jitendex');
  assert.ok(!rebuilt.dictionaries.some((e) => e.id === 'wiktionary-en-de'));
});

test('templates take language names in the locale and codes', () => {
  assert.equal(fill('{source} → {target} ({src}–{tgt})', 'en', 'en', 'ru'), 'English → Russian (en–ru)');
  assert.equal(fill('{source} → {target}', 'ru', 'en', 'ru'), 'английский → русский');
});

test('the format 1 copy has the Japanese main dictionaries with filled texts and whole megabytes', () => {
  const templates = {
    [TEMPLATE_MAIN]: { title: {}, description: { en: 'Wiktionary, {source} → {target}', 'zh-Hant': '{source}' } },
    [TEMPLATE_TRANSCRIPTION]: { title: {}, description: { en: '{source}' } },
  };
  const document = rebuild({ format: 2, templates, dictionaries: manual }, wiktionaryEntries(archives(listing), manual));
  const old = legacy(document);
  assert.equal(old.format, 1);
  assert.deepEqual(old.dictionaries.map((e) => e.id), ['jitendex', 'wiktionary-ja-en', 'wiktionary-ja-ru']);
  const wiktionary = old.dictionaries[1];
  assert.equal(wiktionary.installedTitle, 'wty-ja-en');
  assert.deepEqual(wiktionary.formerTitles, ['kty-ja-en']);
  assert.deepEqual(wiktionary.description, { en: 'Wiktionary, Japanese → English' });
  assert.equal(wiktionary.sizeMb, 16);
  assert.equal(old.dictionaries[0].sizeMb, 40);
  assert.equal(old.dictionaries[2].sizeMb, 1);
});
