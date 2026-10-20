#!/usr/bin/env node
// Rebuilds the Wiktionary entries of the dictionary catalog from the Hugging Face tree of wty-release and writes the
// format 1 catalog that older app versions read. Hand-written entries (those without a Wiktionary template) are kept
// as they are. A dictionary is listed when it holds enough words (MIN_WORDS); archives are downloaded to count them,
// and the counts are kept in word-counts.json, so only new or changed archives are downloaded again.
//
//   node scripts/catalog/generate.mjs            fetch the tree, count new archives, rewrite both catalog files
//   node scripts/catalog/generate.mjs --tree t.json   use a recorded tree listing and the kept counts, no network
//   node scripts/catalog/generate.mjs --record t.json fetch the tree and also save the listing

import { readFile, writeFile } from 'node:fs/promises';
import { inflateRawSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const CATALOG = path.join(ROOT, 'dictionary/api/src/main/assets/catalog/dictionaries-v2.json');
const LEGACY = path.join(ROOT, 'dictionary/api/src/main/assets/catalog/dictionaries.json');
const COUNTS = path.join(ROOT, 'scripts/catalog/word-counts.json');

/** Source languages of the app's language groups; the app shows those it supports. */
export const SOURCES = [
  'ja', 'en', 'zh', 'ko', 'es', 'fr', 'de', 'it', 'pt', 'ru', 'pl', 'tr', 'vi', 'lv',
  'nl', 'cs', 'id', 'ms', 'tl', 'sh', 'uk', 'be', 'el', 'hi', 'bn', 'ka', 'hy', 'th', 'km',
  'ar', 'fa', 'he', 'kk', 'ky',
];

/** Gloss languages: the interface languages that have a Wiktionary edition. */
export const GLOSSES = ['en', 'ru', 'de', 'es', 'fr', 'it', 'ja', 'ko', 'pl', 'pt', 'tr', 'vi', 'zh'];

/**
 * A dictionary is listed when it holds at least this many words with a meaning of their own; words that are only
 * forms of others (`[lemma, [tags]]` glossary items) do not count. A transcription counts its transcribed words.
 */
export const MIN_WORDS = 1500;

const MIB = 1024 * 1024;

/** Larger archives are not downloaded for counting: each holds tens of thousands of words. */
export const COUNTED_BELOW = 8 * MIB;

/** Languages whose transcription is not ticked on the download screen (Chinese readings are in its dictionaries). */
const UNTICKED_TRANSCRIPTION = new Set(['zh']);

const RELEASE = 'https://huggingface.co/datasets/daxida/wty-release/resolve/main/latest';
const TREE_API = 'https://huggingface.co/api/datasets/daxida/wty-release/tree/main/latest/dict';
const HOMEPAGE = 'https://github.com/yomidevs/wiktionary-to-yomitan';
const LICENSE = 'CC BY-SA 4.0';

export const TEMPLATE_MAIN = 'wiktionary';
export const TEMPLATE_GLOSSARY = 'wiktionary-glossary';
export const TEMPLATE_TRANSCRIPTION = 'wiktionary-transcription';
const TEMPLATES = new Set([TEMPLATE_MAIN, TEMPLATE_GLOSSARY, TEMPLATE_TRANSCRIPTION]);

/** Files of a tree listing as `{path, size}`, without the MDict builds. */
export function archives(listing) {
  return listing
    .filter((item) => item.type === 'file' && item.path.endsWith('.zip') && !item.path.includes('/mdict/'))
    .map((item) => ({ path: item.path.replace(/^latest\//, ''), size: item.size }));
}

function mainEntry(source, target, size, recommended) {
  const name = `wty-${source}-${target}`;
  return {
    id: `wiktionary-${source}-${target}`,
    title: `Wiktionary (${source}–${target})`,
    template: TEMPLATE_MAIN,
    oldTitles: [name, `kty-${source}-${target}`],
    kind: 'TERM',
    category: 'MAIN',
    sourceLanguage: source,
    targetLanguage: target,
    recommended,
    indexUrl: `${RELEASE}/index/${name}-index.json?download=true`,
    downloadUrl: `${RELEASE}/dict/${source}/${target}/${name}.zip?download=true`,
    resolveLatest: true,
    downloadSize: size,
    license: LICENSE,
    homepage: HOMEPAGE,
  };
}

function glossaryEntry(source, target, size) {
  const name = `wty-${source}-${target}-gloss`;
  return {
    id: `wiktionary-${source}-${target}-glossary`,
    title: `Wiktionary glossary (${source}–${target})`,
    template: TEMPLATE_GLOSSARY,
    oldTitles: [name, `kty-${source}-${target}-gloss`],
    kind: 'TERM',
    category: 'GLOSSARY',
    sourceLanguage: source,
    targetLanguage: target,
    indexUrl: `${RELEASE}/index/${name}-index.json?download=true`,
    downloadUrl: `${RELEASE}/dict/${source}/${target}/${name}.zip?download=true`,
    resolveLatest: true,
    downloadSize: size,
    license: LICENSE,
    homepage: HOMEPAGE,
  };
}

function transcriptionEntry(source, size) {
  const name = `wty-${source}-ipa`;
  return {
    id: `wiktionary-${source}-transcription`,
    title: `Wiktionary transcription (${source})`,
    template: TEMPLATE_TRANSCRIPTION,
    oldTitles: [name],
    kind: 'PITCH',
    category: 'PRONUNCIATION',
    sourceLanguage: source,
    recommended: !UNTICKED_TRANSCRIPTION.has(source),
    indexUrl: `${RELEASE}/index/${name}-index.json?download=true`,
    downloadUrl: `${RELEASE}/dict/all/${source}/${name}.zip?download=true`,
    resolveLatest: true,
    downloadSize: size,
    license: LICENSE,
    homepage: HOMEPAGE,
  };
}

/**
 * The languages [source]'s dictionaries go into: the gloss languages and, for a language that is none of them, its
 * own Wiktionary edition, which the download screen lists unticked.
 */
export function targets(source) {
  return GLOSSES.includes(source) ? GLOSSES : [...GLOSSES, source];
}

/** Tree paths (below `dict/`) of every archive the catalog may list. */
export function candidates() {
  const paths = [];
  for (const source of SOURCES) {
    for (const target of targets(source)) paths.push(`dict/${source}/${target}/wty-${source}-${target}.zip`);
    for (const target of GLOSSES) paths.push(`dict/${source}/${target}/wty-${source}-${target}-gloss.zip`);
    paths.push(`dict/all/${source}/wty-${source}-ipa.zip`);
  }
  return paths;
}

/**
 * The Wiktionary entries for [files] (tree paths below `dict/`) that hold MIN_WORDS words by [words] (counts by
 * path); an archive of COUNTED_BELOW bytes or more passes uncounted. A main dictionary is recommended unless a
 * hand-written main dictionary of the same pair is, so each pair has one recommended entry, or it is the own edition of
 * a language outside the gloss languages.
 */
export function wiktionaryEntries(files, manual, words = new Map()) {
  const sizes = new Map(files.map((file) => [file.path, file.size]));
  const passes = (file) => sizes.has(file) && (sizes.get(file) >= COUNTED_BELOW || (words.get(file) ?? 0) >= MIN_WORDS);
  const recommendedPairs = new Set(
    manual.filter((e) => e.recommended && (e.category ?? 'MAIN') === 'MAIN' && e.kind === 'TERM')
      .map((e) => `${e.sourceLanguage}-${e.targetLanguage}`),
  );
  const entries = [];
  for (const source of SOURCES) {
    for (const target of targets(source)) {
      const file = `dict/${source}/${target}/wty-${source}-${target}.zip`;
      const recommended = GLOSSES.includes(target) && !recommendedPairs.has(`${source}-${target}`);
      if (passes(file)) entries.push(mainEntry(source, target, sizes.get(file), recommended));
    }
    for (const target of GLOSSES) {
      const file = `dict/${source}/${target}/wty-${source}-${target}-gloss.zip`;
      if (passes(file)) entries.push(glossaryEntry(source, target, sizes.get(file)));
    }
    const file = `dict/all/${source}/wty-${source}-ipa.zip`;
    if (passes(file)) entries.push(transcriptionEntry(source, sizes.get(file)));
  }
  return entries;
}

/** Whether a glossary item has a meaning of its own: anything but a form-of `[lemma, [tags]]`. */
function ownMeaning(item) {
  return !(Array.isArray(item) && typeof item[0] === 'string' && Array.isArray(item[1]));
}

/**
 * The number of words in a Yomitan archive that count for MIN_WORDS: headwords of term bank rows with a glossary item
 * of their own meaning, and words of `ipa` term meta rows. Reads the zip's central directory; stored and deflated
 * entries only, which is what the Wiktionary archives hold.
 */
export function countWords(buffer) {
  let end = buffer.length - 22;
  while (end >= 0 && buffer.readUInt32LE(end) !== 0x06054b50) end--;
  if (end < 0) throw new Error('not a zip archive');
  const entries = buffer.readUInt16LE(end + 10);
  let position = buffer.readUInt32LE(end + 16);
  const words = new Set();
  for (let i = 0; i < entries; i++) {
    const method = buffer.readUInt16LE(position + 10);
    const compressed = buffer.readUInt32LE(position + 20);
    const nameLength = buffer.readUInt16LE(position + 28);
    const local = buffer.readUInt32LE(position + 42);
    const name = buffer.toString('utf8', position + 46, position + 46 + nameLength);
    position += 46 + nameLength + buffer.readUInt16LE(position + 30) + buffer.readUInt16LE(position + 32);
    const term = /^term_bank_\d+\.json$/.test(name);
    if (!term && !/^term_meta_bank_\d+\.json$/.test(name)) continue;
    const start = local + 30 + buffer.readUInt16LE(local + 26) + buffer.readUInt16LE(local + 28);
    const data = buffer.subarray(start, start + compressed);
    const rows = JSON.parse((method === 8 ? inflateRawSync(data) : data).toString('utf8'));
    for (const row of rows) {
      if (term ? Array.isArray(row[5]) && row[5].some(ownMeaning) : row[1] === 'ipa') words.add(row[0]);
    }
  }
  return words.size;
}

/**
 * Whether a kept count ([counted], `{size, words}`) needs the archive counted again at [size]: it is missing, the
 * archive grew or shrank by more than a tenth since, or the count is close enough to MIN_WORDS for a small change to
 * cross it.
 */
export function needsCount(counted, size) {
  if (!counted) return true;
  if (counted.size === size) return false;
  return Math.abs(size - counted.size) > counted.size / 10 ||
    (counted.words >= MIN_WORDS * 0.8 && counted.words < MIN_WORDS * 1.25);
}

/**
 * [counts] (`{path: {size, words}}`) with the archives of [files] below COUNTED_BELOW counted where [needsCount] says
 * so; [download] gives an archive's bytes by its tree path.
 */
export async function refreshCounts(files, counts, download, parallel = 4) {
  const wanted = new Set(candidates());
  const queue = files.filter((file) =>
    wanted.has(file.path) && file.size < COUNTED_BELOW && needsCount(counts[file.path], file.size));
  const result = { ...counts };
  const worker = async () => {
    for (let file = queue.shift(); file; file = queue.shift()) {
      result[file.path] = { size: file.size, words: countWords(await download(file.path)) };
      console.log(`${file.path}: ${result[file.path].words} words`);
    }
  };
  await Promise.all(Array.from({ length: parallel }, worker));
  return Object.fromEntries(Object.entries(result).sort(([a], [b]) => a.localeCompare(b)));
}

async function downloadArchive(file) {
  const response = await fetch(`${RELEASE}/${file}?download=true`);
  if (!response.ok) throw new Error(`${response.status} for ${file}`);
  return Buffer.from(await response.arrayBuffer());
}

/** The catalog document with its Wiktionary entries replaced by [generated], hand-written entries first. */
export function rebuild(document, generated) {
  const manual = document.dictionaries.filter((e) => !TEMPLATES.has(e.template));
  return { ...document, dictionaries: [...manual, ...generated] };
}

/** Fills a template text in [locale]: `{source}`/`{target}` with language names, `{src}`/`{tgt}` with codes. */
export function fill(text, locale, source, target) {
  const names = new Intl.DisplayNames([locale], { type: 'language' });
  return text
    .replaceAll('{source}', names.of(source))
    .replaceAll('{target}', target ? names.of(target) : '')
    .replaceAll('{src}', source)
    .replaceAll('{tgt}', target ?? '');
}

/**
 * The format 1 document of the Japanese entries, which is all older versions know: main dictionaries only (their
 * title prefixes would also match glossaries), template texts filled in, sizes in whole megabytes.
 */
export function legacy(document) {
  const dictionaries = document.dictionaries
    .filter((e) => e.sourceLanguage === 'ja' && (e.category ?? 'MAIN') !== 'GLOSSARY' && e.template !== TEMPLATE_TRANSCRIPTION)
    .map((e) => {
      const template = e.template && document.templates[e.template];
      const description = template
        ? Object.fromEntries(Object.entries(template.description)
          .filter(([locale]) => !locale.includes('-'))
          .map(([locale, text]) => [locale, fill(text, locale, e.sourceLanguage, e.targetLanguage)]))
        : e.description;
      const wiktionary = e.template === TEMPLATE_MAIN;
      const entry = {
        id: e.id,
        title: e.title,
        installedTitle: wiktionary ? e.oldTitles[0] : e.installedTitle,
        formerTitles: wiktionary ? e.oldTitles.slice(1) : e.formerTitles,
        oldTitles: wiktionary ? undefined : e.oldTitles,
        kind: e.kind,
        sourceLanguage: e.sourceLanguage,
        targetLanguage: e.targetLanguage,
        description,
        indexUrl: e.indexUrl,
        downloadUrl: e.downloadUrl,
        resolveLatest: e.resolveLatest ?? false,
        sizeMb: Math.max(1, Math.round(e.downloadSize / MIB)),
        license: e.license,
        homepage: e.homepage,
      };
      return Object.fromEntries(Object.entries(entry).filter(([, value]) => value !== undefined));
    });
  return { format: 1, dictionaries };
}

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status} for ${url}`);
  const next = response.headers.get('link')?.match(/<([^>]+)>;\s*rel="next"/)?.[1];
  return { items: await response.json(), next };
}

/** Every file below `dict/<source>` and `dict/all/<source>` for the source languages, following the pages. */
async function fetchTree() {
  const listing = [];
  for (const folder of [...SOURCES, ...SOURCES.map((source) => `all/${source}`)]) {
    let url = `${TREE_API}/${folder}?recursive=true`;
    while (url) {
      const { items, next } = await fetchJson(url);
      listing.push(...items.map(({ type, path, size }) => ({ type, path, size })));
      url = next;
    }
  }
  return listing;
}

function json(value) {
  return `${JSON.stringify(value, null, 2)}\n`;
}

async function main() {
  const args = process.argv.slice(2);
  const option = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : undefined);
  const recorded = option('--tree');
  const listing = recorded ? JSON.parse(await readFile(recorded, 'utf8')) : await fetchTree();
  const record = option('--record');
  if (record) await writeFile(record, json(listing));

  const files = archives(listing);
  const kept = JSON.parse(await readFile(COUNTS, 'utf8').catch(() => '{}'));
  const counts = recorded ? kept : await refreshCounts(files, kept, downloadArchive);
  if (!recorded) await writeFile(COUNTS, json(counts));
  const words = new Map(Object.entries(counts).map(([file, count]) => [file, count.words]));

  const document = JSON.parse(await readFile(CATALOG, 'utf8'));
  const manual = document.dictionaries.filter((e) => !TEMPLATES.has(e.template));
  const rebuilt = rebuild(document, wiktionaryEntries(files, manual, words));
  await writeFile(CATALOG, json(rebuilt));
  await writeFile(LEGACY, json(legacy(rebuilt)));
  const generated = rebuilt.dictionaries.length - manual.length;
  console.log(`${manual.length} hand-written and ${generated} Wiktionary entries`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main();
}
