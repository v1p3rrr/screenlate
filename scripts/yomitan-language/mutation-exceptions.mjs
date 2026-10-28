#!/usr/bin/env node
// Candidates of a language's mutation exception list (`unmutated` of its descriptor in own-transforms.js): the
// commonest lowercase words of a corpus that have an entry in the English dictionary and that the language's mutation
// rules turn into another headword of the dictionaries given. Printed for the hand review in three groups, each word
// with its count, the rules' results and the words most often before it in the corpus:
//   not attested      the English dictionary gives no result as the word's lemma (mae → bae);
//   own and common    it does, but the word has a sense of its own and is at least 5 times as common as each result
//                     (ar → gar);
//   left to the rule  the other attested ones (bobl → pobl).
// Words on the list already are marked with *. Then, per dictionary set, the share of tokens the rule shows an extra
// entry (the word has one of its own) and the share it alone finds, with and without the list.
//
//   node scripts/yomitan-language/mutation-exceptions.mjs <iso> <corpus> <top N> <english zip> [<other zip>...]
//
// The corpus is a Leipzig sentences file (number, tab, sentence) or plain text.

import {readFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {inflateRawSync} from 'node:zlib';
import {welshTransforms} from './own-transforms.js';

/** Descriptors whose mutation rules the script applies. */
export const DESCRIPTORS = {cy: welshTransforms};

const WORD = /\p{L}+(?:['’-]\p{L}+)*/gu;

/** The rows of the term banks of a Yomitan archive; stored and deflated entries only. */
export function* termRows(buffer) {
    let end = buffer.length - 22;
    while (end >= 0 && buffer.readUInt32LE(end) !== 0x06054b50) end--;
    if (end < 0) throw new Error('not a zip archive');
    const entries = buffer.readUInt16LE(end + 10);
    let position = buffer.readUInt32LE(end + 16);
    for (let i = 0; i < entries; i++) {
        const method = buffer.readUInt16LE(position + 10);
        const compressed = buffer.readUInt32LE(position + 20);
        const nameLength = buffer.readUInt16LE(position + 28);
        const local = buffer.readUInt32LE(position + 42);
        const name = buffer.toString('utf8', position + 46, position + 46 + nameLength);
        position += 46 + nameLength + buffer.readUInt16LE(position + 30) + buffer.readUInt16LE(position + 32);
        if (!/(^|\/)term_bank_\d+\.json$/.test(name)) continue;
        const start = local + 30 + buffer.readUInt16LE(local + 26) + buffer.readUInt16LE(local + 28);
        const data = buffer.subarray(start, start + compressed);
        yield* JSON.parse((method === 8 ? inflateRawSync(data) : data).toString('utf8'));
    }
}

const apostrophes = (text) => text.replace(/’/g, "'");

/** A glossary item that only points at a lemma: `[lemma, [tags]]`. */
const isFormOf = (item) => Array.isArray(item) && typeof item[0] === 'string' && Array.isArray(item[1]);

/**
 * What a dictionary's rows tell: its headwords, those with a sense of their own (any item that is not a form-of
 * pointer), and the lemmas each headword's form-of items give.
 */
export function readDictionary(rows) {
    const heads = new Set();
    const own = new Set();
    const lemmas = new Map();
    for (const row of rows) {
        const head = apostrophes(row[0]);
        heads.add(head);
        const items = Array.isArray(row[5]) ? row[5] : [];
        if (!items.every(isFormOf)) own.add(head);
        for (const item of items.filter(isFormOf)) {
            if (!lemmas.has(head)) lemmas.set(head, new Set());
            lemmas.get(head).add(apostrophes(item[0]));
        }
    }
    return {heads, own, lemmas};
}

/** The texts the descriptor's rules turn [word] into, other than the word itself. */
export function ruleResults(descriptor, word) {
    const results = new Set();
    for (const {rules} of Object.values(descriptor.transforms)) {
        for (const rule of rules) {
            if (rule.isInflected.test(word)) results.add(rule.deinflect(word));
        }
    }
    results.delete(word);
    return [...results];
}

/** Counts of the corpus's words that start in lowercase, and of the words before each (^ at a sentence start). */
export function corpusCounts(lines) {
    const counts = new Map();
    const before = new Map();
    for (const line of lines) {
        let previous = '^';
        for (const [match] of line.slice(line.indexOf('\t') + 1).matchAll(WORD)) {
            const word = apostrophes(match);
            if (word[0] !== word[0].toUpperCase()) {
                counts.set(word, (counts.get(word) ?? 0) + 1);
                if (!before.has(word)) before.set(word, new Map());
                const seen = before.get(word);
                seen.set(previous.toLowerCase(), (seen.get(previous.toLowerCase()) ?? 0) + 1);
            }
            previous = word;
        }
    }
    return {counts, before};
}

const commonest = (counts, n) => [...counts].sort((a, b) => b[1] - a[1] || (a[0] < b[0] ? -1 : 1)).slice(0, n);

/**
 * The candidates among the [top] commonest words: `{word, count, results, before}` in the groups `free` (not
 * attested), `common` (own and common) and `attested` (left to the rule).
 */
export function candidates(descriptor, corpus, english, headwords, top) {
    const groups = {free: [], common: [], attested: []};
    for (const [word, count] of commonest(corpus.counts, top)) {
        const results = ruleResults(descriptor, word).filter((text) => headwords.has(text)).sort();
        if (!english.heads.has(word) || results.length === 0) continue;
        const before = commonest(corpus.before.get(word), 3).map(([text, n]) => [text, Math.floor((100 * n) / count)]);
        const item = {word, count, results, before};
        const lemmas = english.lemmas.get(word) ?? new Set();
        if (!results.some((text) => lemmas.has(text))) {
            groups.free.push(item);
        } else if (english.own.has(word) && results.every((text) => count >= 5 * (corpus.counts.get(text) ?? 0))) {
            groups.common.push(item);
        } else {
            groups.attested.push(item);
        }
    }
    return groups;
}

/**
 * Shares of the corpus's tokens, leaving out [listed] words: `extra`, the rule reaches another headword of
 * [headwords] for a word that is one itself; `ruleOnly`, it reaches one for a word that is not.
 */
export function shares(descriptor, counts, headwords, listed) {
    let total = 0;
    let extra = 0;
    let ruleOnly = 0;
    for (const [word, count] of counts) {
        total += count;
        if (listed.has(word) || !ruleResults(descriptor, word).some((text) => headwords.has(text))) continue;
        if (headwords.has(word)) extra += count;
        else ruleOnly += count;
    }
    return {extra: extra / total, ruleOnly: ruleOnly / total};
}

function main() {
    const [iso, corpusFile, top, englishZip, ...otherZips] = process.argv.slice(2);
    const descriptor = DESCRIPTORS[iso];
    if (!descriptor || !englishZip) {
        console.error('usage: node mutation-exceptions.mjs <iso> <corpus> <top N> <english zip> [<other zip>...]');
        process.exit(1);
    }
    const listed = new Set(descriptor.unmutated ?? []);
    const dictionaries = new Map([englishZip, ...otherZips].map((file) => [
        path.basename(file, '.zip').replace(/^wty-/, ''),
        readDictionary(termRows(readFileSync(file))),
    ]));
    const [english] = dictionaries.values();
    const headwords = new Set([...dictionaries.values()].flatMap((d) => [...d.heads]));
    const corpus = corpusCounts(readFileSync(corpusFile, 'utf8').split('\n'));
    const groups = candidates(descriptor, corpus, english, headwords, Number(top));
    const line = ({word, count, results, before}) => `    ${listed.has(word) ? '*' : ' '}${word} ${count} → ` +
        `${results.join('/')} (${before.map(([text, percent]) => `${text} ${percent}%`).join(', ')})`;
    console.log(`${iso}: top ${top}: ${groups.free.length} not attested, ${groups.common.length} own and common, ` +
        `${groups.attested.length} left to the rule; ${listed.size} listed`);
    for (const [title, items] of [['own and common', groups.common], ['not attested', groups.free],
        ['left to the rule', groups.attested]]) {
        console.log(`  ${title}:`);
        for (const item of items) console.log(line(item));
    }
    const percent = (share) => `${(100 * share).toFixed(2)}%`;
    for (const [name, dictionary] of dictionaries) {
        const [without, withList] = [new Set(), listed].map((skip) => shares(descriptor, corpus.counts, dictionary.heads, skip));
        console.log(`  ${name}: extra entries ${percent(without.extra)} → ${percent(withList.extra)}, ` +
            `found by the rule alone ${percent(without.ruleOnly)} → ${percent(withList.ruleOnly)}`);
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
