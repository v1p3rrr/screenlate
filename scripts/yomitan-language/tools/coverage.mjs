// Lookup coverage of a frequency list, the way the app looks words up:
//
//   node coverage.mjs <bundle.js> <language> <list.tsv> <latin|cyrillic> [--fold] [--fallback a.zip,b.zip] <dict.zip>...
//
// Each word of <list.tsv> (freq_list.py) goes through the bundle's `candidates` (glue.js, as the engine calls it) and
// each candidate is matched as candidate_lookup.cpp matches it: a candidate with conditions needs an untyped flag
// (wty's rows have no part of speech), its tags (`taggedRules`) a definition carrying one or none, and form rows lead
// to their lemma (one step, a second one from a lemma that is only a form). --fold also keys every headword without its
// marks (row "Marks folded in the lookup"; `foldMarks` of own-processors.js, its tables read from that file).
// --fallback searches other dictionaries with the same candidates when the own ones find nothing (Slovak → Czech).
// Printed per top 1,000 / 5,000 / 20,000 and the whole list: the share of word types and of tokens (weighted by the
// list's counts) found as a headword, + through a form row, + through the bundle's rules, + in the fallback. The
// script argument keeps words of Cyrillic letters (and hyphens) or of any letters with ' ’ · - inside.
// Environment: MISSES=n prints the words of the top n found by nothing; SAMPLE=n draws n rule hits by frequency with
// the texts they reached (for a precision check by hand); BOTH=1 counts words that have an entry as written and that
// the rules also take to another word (group 6 question 27). Turkic form rows tagged as other scripts are left out as
// TurkicCyrillicSupport.ignoredFormTags says; rows leading to Arabic-script lemmas are left out everywhere.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';
import {inflateRawSync} from 'node:zlib';

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const [bundlePath, language, listPath, script] = args;
if (!script) {
    console.error('usage: node coverage.mjs <bundle.js> <language> <list.tsv> <latin|cyrillic> [--fold] [--fallback a.zip,b.zip] <dict.zip>...');
    process.exit(1);
}
let fold = false;
let fallback = [];
const dictionaries = [];
for (let i = 4; i < args.length; i++) {
    if (args[i] === '--fold') fold = true;
    else if (args[i] === '--fallback') fallback = args[++i].split(',');
    else dictionaries.push(args[i]);
}

/** The rows of every term bank of the zip archive at [file] (stored and deflated entries). */
function* termRows(file) {
    const buffer = fs.readFileSync(file);
    let end = buffer.length - 22;
    while (end >= 0 && buffer.readUInt32LE(end) !== 0x06054b50) end--;
    const entries = buffer.readUInt16LE(end + 10);
    let position = buffer.readUInt32LE(end + 16);
    for (let i = 0; i < entries; i++) {
        const method = buffer.readUInt16LE(position + 10);
        const compressed = buffer.readUInt32LE(position + 20);
        const nameLength = buffer.readUInt16LE(position + 28);
        const local = buffer.readUInt32LE(position + 42);
        const name = buffer.toString('utf8', position + 46, position + 46 + nameLength);
        position += 46 + nameLength + buffer.readUInt16LE(position + 30) + buffer.readUInt16LE(position + 32);
        if (!/^term_bank_\d+\.json$/.test(name)) continue;
        const start = local + 30 + buffer.readUInt16LE(local + 26) + buffer.readUInt16LE(local + 28);
        const data = buffer.subarray(start, start + compressed);
        yield* JSON.parse((method === 8 ? inflateRawSync(data) : data).toString('utf8'));
    }
}

const processors = fs.readFileSync(path.join(here, '..', 'own-processors.js'), 'utf8');
const pairs = (text) => [...text.matchAll(/\[(0x[0-9A-Fa-f]+), (0x[0-9A-Fa-f]+)\]/g)].map((m) => [Number(m[1]), Number(m[2])]);
const FOLDED_SCRIPTS = pairs(/const FOLDED_SCRIPTS = \[([\s\S]*?)\];/.exec(processors)[1]);
const FOLDED_LETTERS = new Map(pairs(/const FOLDED_LETTERS = [\s\S]*?new Map\(\[([\s\S]*?)\]\);/.exec(processors)[1]));

/** [text] without the marks after its Latin and Cyrillic letters, as `foldMarks` writes it. */
function foldMarks(text) {
    let out = '';
    let afterLetter = false;
    for (const char of text.normalize('NFD')) {
        if (/\p{Mn}/u.test(char)) {
            if (afterLetter) continue;
        } else {
            const point = char.codePointAt(0);
            afterLetter = /\p{L}/u.test(char) && FOLDED_SCRIPTS.some(([first, last]) => first <= point && point <= last);
        }
        const base = FOLDED_LETTERS.get(char.codePointAt(0));
        out += base === undefined ? char : String.fromCodePoint(base);
    }
    return out.normalize('NFC');
}

const IGNORED_TURKIC_TAGS = new Set(['Arabic', 'romanization', 'Yañalif', 'Cyrillic', 'Cyrillic sometimes']);
const turkic = ['kk', 'ky', 'tt', 'ba'].includes(language);
const isFormOf = (item) => Array.isArray(item) && typeof item[0] === 'string' && Array.isArray(item[1]);

/** Headwords with the definition tags of their rows of own meaning (`own`) and the lemmas of their form rows. */
function index(files) {
    const own = new Map();
    const forms = new Map();
    const add = (map, key, value) => {
        let list = map.get(key);
        if (!list) map.set(key, list = []);
        list.push(value);
    };
    for (const file of files) {
        for (const [headword, , tags, , , glossary] of termRows(file)) {
            const keys = [headword];
            if (fold && foldMarks(headword) !== headword) keys.push(foldMarks(headword));
            const meanings = glossary.some((item) => !isFormOf(item));
            for (const key of keys) {
                if (meanings) add(own, key, tags || '');
                for (const [lemma, formTags] of glossary.filter(isFormOf)) {
                    if (turkic && formTags.some((t) => IGNORED_TURKIC_TAGS.has(t))) continue;
                    if (/[؀-ۿ]/.test(lemma)) continue;
                    add(forms, key, lemma);
                }
            }
        }
    }
    return {own, forms};
}

const main = index(dictionaries);
const back = fallback.length ? index(fallback) : null;

/** Whether definition tags [tags] fit [wanted] (candidate_lookup.cpp `has_tag`). */
function hasTag(tags, wanted) {
    if (!wanted || wanted.length === 0) return true;
    const list = tags.split(/\s+/).filter(Boolean);
    return list.length === 0 || list.some((t) => wanted.includes(t));
}
const ownFits = (ix, text, wanted) => (ix.own.get(text) ?? []).some((tags) => hasTag(tags, wanted));

/** 0 when candidate [c] finds nothing in [ix], 1 when a headword, 2 when a form row's lemma. */
function found(ix, c) {
    if (c.c !== 0 && !c.u) return 0;
    if (ownFits(ix, c.d, c.g)) return 1;
    for (const lemma of ix.forms.get(c.d) ?? []) {
        if (ownFits(ix, lemma, c.g)) return 2;
        for (const second of ix.forms.get(lemma) ?? []) if (second !== c.d && ownFits(ix, second, c.g)) return 2;
    }
    return 0;
}

const context = vm.createContext({});
vm.runInContext(fs.readFileSync(bundlePath, 'utf8'), context);
const bundle = context.yomitanLang;
const letters = script === 'cyrillic' ? /^[Ѐ-ӿ]+(-[Ѐ-ӿ]+)*$/ : /^\p{L}+(['’·-]\p{L}+)*$/u;
const list = fs.readFileSync(listPath, 'utf8').split('\n').filter(Boolean).map((line) => line.split('\t'))
    .map(([word, count]) => [word, Number(count)]).filter(([word]) => letters.test(word));

const reached = new Map();
const besideEntry = new Map();
const isPlain = (c) => c.r.some((chain) => chain.length === 0);

/** 1 headword, 2 form row, 3 the rules, 4 the fallback, 0 nothing. */
function classify(word) {
    const candidates = bundle.candidates(word, language, 'word').filter((c) => c.o === word);
    let best = 0;
    for (const c of candidates) {
        const f = found(main, c);
        if (!f) continue;
        const kind = isPlain(c) ? f : 3;
        if (best === 0 || kind < best) best = kind;
        if (kind === 3) reached.set(word, [...(reached.get(word) ?? []), `${c.d} (${c.r.map((r) => r.join(' > ')).join(' | ')})`]);
    }
    if (best === 1 && process.env.BOTH) {
        const others = candidates.filter((c) => !isPlain(c) && c.d.toLowerCase() !== word && found(main, c)).map((c) => c.d);
        if (others.length) besideEntry.set(word, [...new Set(others)].slice(0, 3));
    }
    if (best === 0 && back && candidates.some((c) => found(back, c))) best = 4;
    return best;
}

const kinds = list.map(([word, count]) => [word, count, classify(word)]);
const report = (slice, label) => {
    const tokens = slice.reduce((sum, [, n]) => sum + n, 0);
    const share = (k) => (100 * slice.filter(([, , c]) => c !== 0 && c <= k).reduce((sum, [, n]) => sum + n, 0) / tokens).toFixed(1);
    const types = (k) => (100 * slice.filter(([, , c]) => c !== 0 && c <= k).length / slice.length).toFixed(1);
    return `${label}: types ${[1, 2, 3, 4].map(types).join(' / ')}; tokens ${[1, 2, 3, 4].map(share).join(' / ')}`;
};
const names = (files) => files.map((f) => path.basename(f)).join(' + ');
console.log(`${language} ${names(dictionaries)}${back ? `, fallback ${names(fallback)}` : ''}${fold ? ', folded keys' : ''}` +
    ' (headword / + form rows / + rules / + fallback)');
for (const n of [1000, 5000, 20000]) console.log('  ' + report(kinds.slice(0, n), `top ${n}`));
console.log('  ' + report(kinds, `all ${kinds.length}`));
if (process.env.BOTH) {
    const tokens = kinds.reduce((sum, [, n]) => sum + n, 0);
    const hits = kinds.filter(([word, , c]) => c === 1 && besideEntry.has(word));
    console.log(`  entry as written and a rule result besides: ${(100 * hits.reduce((s, [, n]) => s + n, 0) / tokens).toFixed(1)}% of tokens, ${hits.length} types`);
    console.log('  ' + hits.slice(0, 30).map(([word]) => `${word} → ${besideEntry.get(word).join(' / ')}`).join(', '));
}
if (process.env.SAMPLE) {
    let seed = 7;
    const random = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
    const hits = kinds.filter(([, , c]) => c === 3);
    const total = hits.reduce((sum, [, n]) => sum + n, 0);
    for (let i = 0; i < Number(process.env.SAMPLE); i++) {
        let r = random() * total;
        const hit = hits.find(([, n]) => (r -= n) < 0) ?? hits[0];
        console.log(`  ${hit[0]} → ${[...new Set(reached.get(hit[0]))].slice(0, 4).join(', ')}`);
    }
}
if (process.env.MISSES) {
    const misses = kinds.slice(0, Number(process.env.MISSES)).filter(([, , c]) => c === 0).map(([word]) => word);
    console.log('  not found:', misses.slice(0, 80).join(' '));
}
