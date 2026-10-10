#!/usr/bin/env node
// Rebuilds the Wiktionary entries of the dictionary catalog from the Hugging Face tree of wty-release and writes the
// format 1 catalog that older app versions read. Hand-written entries (those without a Wiktionary template) are kept
// as they are.
//
//   node scripts/catalog/generate.mjs            fetch the tree and rewrite both catalog files
//   node scripts/catalog/generate.mjs --tree t.json   use a recorded tree listing instead of the network
//   node scripts/catalog/generate.mjs --record t.json fetch the tree and also save the listing

import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const CATALOG = path.join(ROOT, 'dictionary/api/src/main/assets/catalog/dictionaries-v2.json');
const LEGACY = path.join(ROOT, 'dictionary/api/src/main/assets/catalog/dictionaries.json');

/** Source languages of the app's language groups; the app shows those it supports. */
export const SOURCES = ['ja', 'en', 'zh', 'ko', 'es', 'fr', 'de', 'it', 'pt', 'ru', 'pl', 'tr', 'vi'];

/** Gloss languages: the interface languages that have a Wiktionary edition. */
export const GLOSSES = ['en', 'ru', 'de', 'es', 'fr', 'it', 'ja', 'ko', 'pl', 'pt', 'tr', 'vi', 'zh'];

/** Smaller archives cover only a few hundred words and are left out. */
export const MIN_BYTES = 512 * 1024;

/** Languages whose transcription is not ticked on the download screen (Chinese readings are in its dictionaries). */
const UNTICKED_TRANSCRIPTION = new Set(['zh']);

const RELEASE = 'https://huggingface.co/datasets/daxida/wty-release/resolve/main/latest';
const TREE_API = 'https://huggingface.co/api/datasets/daxida/wty-release/tree/main/latest/dict';
const HOMEPAGE = 'https://github.com/yomidevs/wiktionary-to-yomitan';
const LICENSE = 'CC BY-SA 4.0';
const MIB = 1024 * 1024;

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
 * The Wiktionary entries for [files] (tree paths below `dict/`). A main dictionary is recommended unless a hand-written
 * main dictionary of the same pair is, so each pair has one recommended entry.
 */
export function wiktionaryEntries(files, manual) {
  const sizes = new Map(files.map((file) => [file.path, file.size]));
  const recommendedPairs = new Set(
    manual.filter((e) => e.recommended && (e.category ?? 'MAIN') === 'MAIN' && e.kind === 'TERM')
      .map((e) => `${e.sourceLanguage}-${e.targetLanguage}`),
  );
  const entries = [];
  for (const source of SOURCES) {
    for (const target of GLOSSES) {
      const main = sizes.get(`dict/${source}/${target}/wty-${source}-${target}.zip`);
      if (main >= MIN_BYTES) entries.push(mainEntry(source, target, main, !recommendedPairs.has(`${source}-${target}`)));
    }
    for (const target of GLOSSES) {
      const glossary = sizes.get(`dict/${source}/${target}/wty-${source}-${target}-gloss.zip`);
      if (glossary >= MIN_BYTES) entries.push(glossaryEntry(source, target, glossary));
    }
    const transcription = sizes.get(`dict/all/${source}/wty-${source}-ipa.zip`);
    if (transcription >= MIN_BYTES) entries.push(transcriptionEntry(source, transcription));
  }
  return entries;
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

  const document = JSON.parse(await readFile(CATALOG, 'utf8'));
  const manual = document.dictionaries.filter((e) => !TEMPLATES.has(e.template));
  const rebuilt = rebuild(document, wiktionaryEntries(archives(listing), manual));
  await writeFile(CATALOG, json(rebuilt));
  await writeFile(LEGACY, json(legacy(rebuilt)));
  const generated = rebuilt.dictionaries.length - manual.length;
  console.log(`${manual.length} hand-written and ${generated} Wiktionary entries`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main();
}
