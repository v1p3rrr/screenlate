// Builds one script per language from a Yomitan checkout: node build.mjs <yomitan checkout> <out dir> <iso>...
// The descriptor list is cut down to the one language, so other languages' tables are left out of the bundle.
import * as esbuild from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const [yomitanRoot, outDir, ...languages] = process.argv.slice(2);
if (!yomitanRoot || !outDir || languages.length === 0) {
    console.error('usage: node build.mjs <yomitan checkout> <out dir> <iso>...');
    process.exit(1);
}
const yomitan = path.resolve(yomitanRoot, 'ext/js');
const descriptorsFile = path.join(yomitan, 'language/language-descriptors.js');

/** @param {string} source @param {string} iso */
function keepLanguage(source, iso) {
    const start = source.indexOf('const languageDescriptors = [');
    const end = source.indexOf('\n];', start);
    const blocks = source.slice(start, end).split(/\n {4}\{\n/).slice(1).map((b) => '    {\n' + b.replace(/,\s*$/, ''));
    const kept = blocks.filter((b) => b.includes(`iso: '${iso}'`));
    if (kept.length !== 1) throw new Error(`${iso}: found ${kept.length} descriptors`);
    return source.slice(0, start) + 'const languageDescriptors = [\n' + kept[0] + ',' + source.slice(end);
}

fs.mkdirSync(outDir, {recursive: true});
for (const iso of languages) {
    const plugin = {
        name: 'yomitan',
        setup(build) {
            build.onResolve({filter: /^yomitan\//}, (a) => ({path: path.join(yomitan, a.path.slice('yomitan/'.length))}));
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
    });
    console.log(`${iso}: ${fs.statSync(out).size} bytes`);
}
