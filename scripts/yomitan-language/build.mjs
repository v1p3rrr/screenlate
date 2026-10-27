// Builds one script per language from a Yomitan checkout: node build.mjs <yomitan checkout> <out dir> <iso>...
// The descriptor list is cut down to the one language, so other languages' tables are left out of the bundle.
import * as esbuild from 'esbuild';
import {execFileSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const [yomitanRoot, outDir, ...languages] = process.argv.slice(2);
if (!yomitanRoot || !outDir || languages.length === 0) {
    console.error('usage: node build.mjs <yomitan checkout> <out dir> <iso>...');
    process.exit(1);
}
const pinned = fs.readFileSync(path.join(here, 'yomitan-commit.txt'), 'utf8').trim();
const commit = execFileSync('git', ['-C', yomitanRoot, 'rev-parse', 'HEAD'], {encoding: 'utf8'}).trim();
if (commit !== pinned) {
    console.error(`the checkout is at ${commit}, the bundles are built from ${pinned} (yomitan-commit.txt)`);
    process.exit(1);
}
const banner = `/* Yomitan's language code (https://github.com/yomidevs/yomitan, commit ${pinned}), GPL-3.0-or-later.
 * Copyright (C) 2023-2025 Yomitan Authors; Copyright (C) 2021-2022 Yomichan Authors.
 * Bundled with scripts/yomitan-language/glue.js by scripts/yomitan-language/build.mjs. */`;
const yomitan = path.resolve(yomitanRoot, 'ext/js');
const descriptorsFile = path.join(yomitan, 'language/language-descriptors.js');

/**
 * Languages Yomitan has no descriptor for, built from a related language's descriptor under their own codes: Malay
 * takes Indonesian's text processors (both write the same alphabet), with no inflection rules of either; Bengali takes
 * Hindi's descriptor, which has neither, for the app's own processors and endings; Kazakh and Kyrgyz take Mongolian's
 * (Cyrillic, capitalization only) for the app's own endings; Lithuanian takes Latvian's (capitalization only), as
 * Wiktionary lists its forms; Slovak takes Czech's (capitalization only), the language its lookups fall back to; Slovenian takes Czech's as
 * well, as Wiktionary lists its forms.
 */
const borrowed = {
    ms: {from: 'id', iso639_3: 'msa', name: 'Malay'},
    bn: {from: 'hi', iso639_3: 'ben', name: 'Bengali', exampleText: 'পড়ার জন্য'},
    kk: {from: 'mn', iso639_3: 'kaz', name: 'Kazakh', exampleText: 'оқу'},
    ky: {from: 'mn', iso639_3: 'kir', name: 'Kyrgyz', exampleText: 'окуу'},
    lt: {from: 'lv', iso639_3: 'lit', name: 'Lithuanian', exampleText: 'skaityti'},
    sk: {from: 'cs', iso639_3: 'slk', name: 'Slovak', exampleText: 'čítať'},
    sl: {from: 'cs', iso639_3: 'slv', name: 'Slovenian', exampleText: 'brati'},
};

/**
 * Text processors a language takes from another one, by the names the descriptor file imports them under, or the
 * app's own (own-processors.js): Belarusian is written with the stress marks and apostrophes Ukrainian is (сям'я,
 * сям’я), and Yomitan gives it capitalization only; Hindi and Bengali text is not always composed as the headwords
 * are, and Hindi writes candrabindu and anusvara for each other; Georgian headings are written in Mtavruli capitals
 * (ᲡᲐᲥᲐᲠᲗᲕᲔᲚᲝ), which Yomitan does not lowercase; Armenian writes marks inside words and its ligature և out; Thai
 * text may give ำ in two parts or a tone mark before its vowel, and write ๆ without the spaces headwords have; Khmer
 * text carries zero-width spaces between words and writes subscript TA and DA for each other; Arabic text writes a
 * final ة as ه and may give Persian letters for its own; Persian text may give Arabic letters for its own, lose its
 * zero-width non-joiners and write a verb prefix or an ending apart; Hebrew text may carry vowel points and write the
 * geresh and gershayim as ASCII quotes; Kazakh text may give Latin i, h and ə for і, һ and ә; Romanian text may
 * give ş ţ for ș ț, and writes its rare apostrophes as Ukrainian does (dom'le, dom’le); Latin editions write u for
 * consonantal v (uita); Esperanto is typed in the x-system where its letters cannot be (cxu for ĉu) and writes its
 * elided o as Ukrainian writes apostrophes (dank' al, dank’ al). Processors join the descriptor's capitalization processors, its own list, or make its list.
 */
const addedPreprocessors = {
    be: ['removeUkrainianDiacritics', 'ukrainianApostropheVariants'],
    hi: ['composeCharacters', 'candrabinduAsAnusvara'],
    bn: ['composeCharacters'],
    ka: ['decapitalize'],
    hy: ['removeArmenianMarks', 'armenianLigatureEv'],
    th: ['thaiCharacterOrder', 'thaiMaiYamokSpace'],
    km: ['khmerZeroWidthSpace', 'khmerSubscriptDa'],
    ar: ['arabicTaMarbutaAsHa', 'arabicLetters'],
    fa: ['persianLetters', 'removePersianZeroWidthNonJoiner', 'persianJoinedAffixes'],
    he: ['removeHebrewPoints', 'hebrewQuotes'],
    kk: ['kazakhLatinLetters'],
    ro: ['romanianCommaLetters', 'ukrainianApostropheVariants'],
    la: ['latinUAsV'],
    eo: ['esperantoXSystem', 'ukrainianApostropheVariants'],
};

/**
 * Inflection rules of the app's own (own-transforms.js) for languages Yomitan has none for: Bengali noun endings,
 * Persian endings and verb prefixes, Hebrew prefixes, Kazakh and Kyrgyz endings, the Norwegian genitive, Romanian
 * article and agreement endings.
 */
const addedTransforms = {
    bn: 'bengaliTransforms',
    fa: 'persianTransforms',
    he: 'hebrewTransforms',
    kk: 'kazakhTransforms',
    ky: 'kyrgyzTransforms',
    no: 'norwegianTransforms',
    ro: 'romanianTransforms',
};

/** Rules of the app's own (own-transforms.js) added to Yomitan's for a language: the Latin enclitics. */
const extendedTransforms = {
    la: 'latinEnclitics',
};

const ownProcessors = Object.keys(await import(new URL('./own-processors.js', import.meta.url).href));

/** @param {string} source @param {string} iso */
function keepLanguage(source, iso) {
    const start = source.indexOf('const languageDescriptors = [');
    const end = source.indexOf('\n];', start);
    const blocks = source.slice(start, end).split(/\n {4}\{\n/).slice(1).map((b) => '    {\n' + b.replace(/,\s*$/, ''));
    const borrowing = borrowed[iso];
    const kept = blocks.filter((b) => b.includes(`iso: '${borrowing ? borrowing.from : iso}'`));
    if (kept.length !== 1) throw new Error(`${iso}: found ${kept.length} descriptors`);
    let block = kept[0];
    if (borrowing) {
        if (blocks.some((b) => b.includes(`iso: '${iso}'`))) throw new Error(`${iso}: Yomitan has a descriptor now`);
        if (block.includes('languageTransforms')) throw new Error(`${iso}: ${borrowing.from} has inflection rules`);
        block = block
            .replace(/iso: '[^']*'/, `iso: '${iso}'`)
            .replace(/iso639_3: '[^']*'/, `iso639_3: '${borrowing.iso639_3}'`)
            .replace(/name: '[^']*'/, `name: '${borrowing.name}'`);
        if (borrowing.exampleText) block = block.replace(/exampleText: '[^']*'/, `exampleText: '${borrowing.exampleText}'`);
    }
    const added = addedPreprocessors[iso];
    let imports = '';
    if (added) {
        const plain = 'textPreprocessors: capitalizationPreprocessors,';
        const example = /(\n {8}exampleText: [^\n]*\n)/;
        const list = /(\n {8}textPreprocessors: \{\n(?: {12}(?:\.\.\.)?\w+,\n)+)( {8}\},)/;
        if (block.includes(plain)) {
            block = block.replace(plain, `textPreprocessors: {...capitalizationPreprocessors, ${added.join(', ')}},`);
        } else if (!block.includes('textPreprocessors') && example.test(block)) {
            block = block.replace(example, `$1        textPreprocessors: {${added.join(', ')}},\n`);
        } else if (list.test(block)) {
            block = block.replace(list, `$1${added.map((name) => `            ${name},\n`).join('')}$2`);
        } else {
            throw new Error(`${iso}: Yomitan has more text processors now`);
        }
        const own = added.filter((name) => ownProcessors.includes(name));
        if (own.length > 0) imports = `import {${own.join(', ')}} from 'screenlate/own-processors.js';\n`;
    }
    const extension = extendedTransforms[iso];
    if (extension) {
        const own = /languageTransforms: (\w+),/;
        const yomitan = own.exec(block)?.[1];
        if (!yomitan) throw new Error(`${iso}: Yomitan has no inflection rules to add to`);
        block = block.replace(own, `languageTransforms: withOwnTransforms(${yomitan}, ${extension}),`);
        imports += `import {withOwnTransforms, ${extension}} from 'screenlate/own-transforms.js';\n`;
    }
    const transforms = addedTransforms[iso];
    if (transforms) {
        if (block.includes('languageTransforms')) throw new Error(`${iso}: Yomitan has inflection rules now`);
        block = block.replace(/\n {4}\}$/, `\n        languageTransforms: ${transforms},\n    }`);
        imports += `import {${transforms}} from 'screenlate/own-transforms.js';\n`;
    }
    return imports + source.slice(0, start) + 'const languageDescriptors = [\n' + block + ',' + source.slice(end);
}

fs.mkdirSync(outDir, {recursive: true});
for (const iso of languages) {
    const plugin = {
        name: 'yomitan',
        setup(build) {
            build.onResolve({filter: /^yomitan\//}, (a) => ({path: path.join(yomitan, a.path.slice('yomitan/'.length))}));
            build.onResolve({filter: /^screenlate\//}, (a) => ({path: path.join(here, a.path.slice('screenlate/'.length))}));
            build.onResolve({filter: /\/lib\/hangul-js\.js$/}, () => ({path: path.join(here, 'shims/hangul-js.js')}));
            build.onResolve({filter: /\/lib\/kanji-processor\.js$/}, () => ({path: path.join(here, 'shims/kanji-processor.js')}));
            // Lets esbuild drop the language modules nothing imports any more.
            build.onResolve({filter: /^\.\/[a-z]+\/.*\.js$/}, (a) => (a.importer === descriptorsFile ?
                {path: path.join(path.dirname(a.importer), a.path), sideEffects: false} :
                undefined));
            build.onLoad({filter: /language-descriptors\.js$/}, (a) => ({
                contents: keepLanguage(fs.readFileSync(a.path, 'utf8'), iso),
                loader: 'js',
                resolveDir: path.dirname(a.path),
            }));
        },
    };
    const out = path.join(outDir, `${iso}.js`);
    await esbuild.build({
        entryPoints: [path.join(here, 'glue.js')],
        bundle: true,
        format: 'iife',
        target: 'es2020',
        outfile: out,
        plugins: [plugin],
        nodePaths: [path.join(here, 'node_modules')],
        minify: true,
        legalComments: 'eof',
        banner: {js: banner},
    });
    console.log(`${iso}: ${fs.statSync(out).size} bytes`);
}
