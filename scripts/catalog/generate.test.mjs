// node --test scripts/catalog/generate.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { deflateRawSync } from 'node:zlib';
import {
  archives, wiktionaryEntries, rebuild, legacy, fill, countWords, needsCount, refreshCounts, MIN_WORDS,
  TEMPLATE_MAIN, TEMPLATE_TRANSCRIPTION, TEMPLATE_FORMS,
} from './generate.mjs';

const listing = JSON.parse(await readFile(new URL('./test-tree.json', import.meta.url), 'utf8'));

/** Word counts of the recorded tree's archives below the size that passes uncounted. */
const words = new Map([
  ['dict/ja/en/wty-ja-en-gloss.zip', 1200],
  ['dict/ja/ru/wty-ja-ru.zip', 2100],
  ['dict/en/ru/wty-en-ru.zip', 30000],
  ['dict/en/ru/wty-en-ru-gloss.zip', 9000],
  ['dict/en/nl/wty-en-nl.zip', 4000],
  ['dict/zh/de/wty-zh-de.zip', 300],
  ['dict/all/en/wty-en-ipa.zip', 40000],
]);

/** A zip archive of [files] (name to text), stored or deflated, without checksums, which countWords does not read. */
function zip(files, deflate = true) {
  const locals = [];
  const central = [];
  let offset = 0;
  for (const [name, text] of Object.entries(files)) {
    const nameBytes = Buffer.from(name);
    const plain = Buffer.from(text);
    const data = deflate ? deflateRawSync(plain) : plain;
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(deflate ? 8 : 0, 8);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(plain.length, 22);
    local.writeUInt16LE(nameBytes.length, 26);
    const header = Buffer.alloc(46);
    header.writeUInt32LE(0x02014b50, 0);
    header.writeUInt16LE(deflate ? 8 : 0, 10);
    header.writeUInt32LE(data.length, 20);
    header.writeUInt32LE(plain.length, 24);
    header.writeUInt16LE(nameBytes.length, 28);
    header.writeUInt32LE(offset, 42);
    locals.push(local, nameBytes, data);
    central.push(header, nameBytes);
    offset += local.length + nameBytes.length + data.length;
  }
  const directory = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(central.length / 2, 8);
  end.writeUInt16LE(central.length / 2, 10);
  end.writeUInt32LE(directory.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, directory, end]);
}

const manual = [{
  id: 'jitendex', title: 'Jitendex', installedTitle: 'Jitendex.org', kind: 'TERM', sourceLanguage: 'ja',
  targetLanguage: 'en', recommended: true, description: { en: 'Japanese–English' }, downloadUrl: 'https://x/j.zip',
  downloadSize: 40 * 1024 * 1024, license: 'CC BY-SA 4.0', homepage: 'https://jitendex.org',
}];

test('the recorded tree gives main, glossary and transcription entries above the threshold', () => {
  const entries = wiktionaryEntries(archives(listing), manual, words);
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

test('archives below the uncounted size are left out until they are counted', () => {
  const entries = wiktionaryEntries(archives(listing), manual);
  assert.deepEqual(entries.map((e) => e.id), ['wiktionary-ja-en', 'wiktionary-en-en', 'wiktionary-zh-transcription']);
});

test('a source outside the gloss languages gets its own edition, not recommended', () => {
  const files = [
    { path: 'dict/lv/en/wty-lv-en.zip', size: 900000 },
    { path: 'dict/lv/lv/wty-lv-lv.zip', size: 700000 },
    { path: 'dict/lv/lv/wty-lv-lv-gloss.zip', size: 600000 },
    { path: 'dict/en/ru/wty-en-ru.zip', size: 5000000 },
  ];
  const counts = new Map(files.map((file) => [file.path, MIN_WORDS]));
  const entries = wiktionaryEntries(files, [], counts);
  assert.deepEqual(entries.map((e) => [e.id, e.recommended]), [
    ['wiktionary-en-ru', true], ['wiktionary-lv-en', true], ['wiktionary-lv-lv', false],
  ]);
});

test('words count once each, only with a meaning of their own, and transcriptions count their words', () => {
  const terms = [
    ['kot', '', 'n', '', 0, ['cat'], 1, ''],
    ['kot', '', 'n', '', 0, [{ type: 'structured-content', content: 'tomcat' }], 2, ''],
    ['kota', '', '', '', 0, [['kot', ['genitive']]], 3, ''],
    ['kotek', '', '', '', 0, [['kot', ['diminutive']], 'kitten'], 4, ''],
  ];
  const meta = [['kot', 'ipa', {}], ['pies', 'ipa', {}], ['pies', 'ipa', {}], ['kot', 'freq', 3]];
  const files = {
    'index.json': '{}',
    'term_bank_1.json': JSON.stringify(terms),
    'term_meta_bank_1.json': JSON.stringify(meta),
    'tag_bank_1.json': '[]',
  };
  assert.equal(countWords(zip(files)), 3);
  assert.equal(countWords(zip(files, false)), 3);
  assert.throws(() => countWords(Buffer.from('not a zip archive at all, not even close')));
});

test('a kept count is redone when the archive changed much or the count is near the threshold', () => {
  assert.equal(needsCount(undefined, 1000), true);
  assert.equal(needsCount({ size: 1000, words: 100 }, 1000), false);
  assert.equal(needsCount({ size: 1000, words: 30000 }, 1050), false);
  assert.equal(needsCount({ size: 1000, words: 30000 }, 1150), true);
  assert.equal(needsCount({ size: 1000, words: 100 }, 900), false);
  assert.equal(needsCount({ size: 1000, words: MIN_WORDS }, 1010), true);
});

test('only candidate archives below the uncounted size that need it are downloaded', async () => {
  const files = [
    { path: 'dict/en/ru/wty-en-ru.zip', size: 5000 },
    { path: 'dict/en/ru/wty-en-ru-gloss.zip', size: 3000 },
    { path: 'dict/en/en/wty-en-en.zip', size: 107871321 },
    { path: 'dict/en/ady/wty-en-ady.zip', size: 2000 },
  ];
  const kept = { 'dict/en/ru/wty-en-ru-gloss.zip': { size: 3000, words: 9000 } };
  const downloaded = [];
  const archive = zip({ 'term_bank_1.json': JSON.stringify([['a', '', '', '', 0, ['x'], 1, '']]) });
  const counts = await refreshCounts(files, kept, async (file) => {
    downloaded.push(file);
    return archive;
  });
  assert.deepEqual(downloaded, ['dict/en/ru/wty-en-ru.zip']);
  assert.deepEqual(counts, {
    'dict/en/ru/wty-en-ru-gloss.zip': { size: 3000, words: 9000 },
    'dict/en/ru/wty-en-ru.zip': { size: 5000, words: 1 },
  });
});

test('rebuilding keeps hand-written entries first and replaces the Wiktionary ones', () => {
  const stale = { id: 'wiktionary-en-de', template: TEMPLATE_MAIN };
  const document = { format: 2, templates: {}, dictionaries: [manual[0], stale] };
  const rebuilt = rebuild(document, wiktionaryEntries(archives(listing), manual, words));
  assert.equal(rebuilt.dictionaries[0].id, 'jitendex');
  assert.ok(!rebuilt.dictionaries.some((e) => e.id === 'wiktionary-en-de'));
});

test('a language whose forms only the English edition lists gets a mandatory word forms entry from it', () => {
  const MIB = 1024 * 1024;
  const files = [
    { path: 'dict/fi/en/wty-fi-en.zip', size: 40 * MIB },
    { path: 'dict/fi/ru/wty-fi-ru.zip', size: 9 * MIB },
    { path: 'dict/sw/en/wty-sw-en.zip', size: 9 * MIB },
  ];
  const entries = wiktionaryEntries(files, []);
  const forms = entries.filter((e) => e.template === TEMPLATE_FORMS);
  assert.deepEqual(forms.map((e) => e.id), ['wiktionary-fi-forms']);
  const [entry] = forms;
  assert.equal(entry.category, 'FORMS');
  assert.equal(entry.build, 'forms');
  assert.equal(entry.targetLanguage, 'en');
  assert.equal(entry.downloadUrl, entries.find((e) => e.id === 'wiktionary-fi-en').downloadUrl);
  assert.deepEqual(entry.oldTitles, ['wty-fi-en (forms)']);
  assert.equal(entry.installedSize, 65 * MIB);

  const document = { format: 2, templates: {}, mandatory: { et: ['MAIN', 'FORMS'], xx: ['MAIN', 'GLOSSARY'] }, dictionaries: [] };
  assert.deepEqual(rebuild(document, entries).mandatory, { xx: ['MAIN', 'GLOSSARY'], fi: ['MAIN', 'FORMS'] });
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
  const document = rebuild({ format: 2, templates, dictionaries: manual }, wiktionaryEntries(archives(listing), manual, words));
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

test('norwegian reads the bokmål, norwegian and nynorsk folders, each pair ticked from the first that has it', () => {
  const files = [
    { path: 'dict/nb/en/wty-nb-en.zip', size: 3480000 },
    { path: 'dict/nb/fr/wty-nb-fr.zip', size: 100000 },
    { path: 'dict/no/ru/wty-no-ru.zip', size: 440000 },
    { path: 'dict/no/fr/wty-no-fr.zip', size: 170000 },
    { path: 'dict/nn/en/wty-nn-en.zip', size: 2770000 },
    { path: 'dict/all/nb/wty-nb-ipa.zip', size: 150000 },
    { path: 'dict/all/nn/wty-nn-ipa.zip', size: 190000 },
  ];
  const counts = new Map(files.map((file) => [file.path, MIN_WORDS]));
  counts.set('dict/nb/fr/wty-nb-fr.zip', 1100);
  const templates = {
    [TEMPLATE_MAIN]: {
      title: { en: 'Wiktionary ({src}–{tgt})' },
      description: { en: 'Wiktionary, {source} → {target}.', ru: 'Викисловарь, {source} → {target}.' },
    },
    [TEMPLATE_TRANSCRIPTION]: { title: { en: 'Transcription' }, description: { en: 'Wiktionary, {source}.' } },
  };
  const entries = wiktionaryEntries(files, [], counts, templates);
  assert.deepEqual(entries.map((e) => [e.id, e.sourceLanguage, e.recommended]), [
    ['wiktionary-nb-en', 'no', true], ['wiktionary-no-ru', 'no', true], ['wiktionary-no-fr', 'no', true],
    ['wiktionary-nn-en', 'no', false], ['wiktionary-nb-transcription', 'no', true],
  ]);
  const byId = Object.fromEntries(entries.map((e) => [e.id, e]));
  assert.match(byId['wiktionary-nb-en'].downloadUrl, /\/dict\/nb\/en\/wty-nb-en\.zip/);
  assert.deepEqual(byId['wiktionary-nb-en'].oldTitles, ['wty-nb-en', 'kty-nb-en']);
  assert.equal(byId['wiktionary-nb-en'].titles.en, 'Wiktionary (nb–{tgt})');
  assert.equal(byId['wiktionary-nn-en'].description.en, 'Wiktionary, Norwegian Nynorsk → {target}.');
  assert.equal(byId['wiktionary-nb-en'].description.ru, 'Викисловарь, норвежский букмол → {target}.');
  assert.equal(byId['wiktionary-nb-transcription'].description.en, 'Wiktionary, Norwegian Bokmål.');
  // The folder that is the language's own code keeps the template's texts.
  assert.equal(byId['wiktionary-no-ru'].titles, undefined);
  assert.equal(byId['wiktionary-no-ru'].description, undefined);
});
