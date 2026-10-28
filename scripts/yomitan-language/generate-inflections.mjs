// Regenerates the inflection rule strings of the languages looked up through Yomitan's language code:
//   node generate-inflections.mjs <yomitan checkout> <iso>...
// Pass every such language the app has (the Kotlin map is written for exactly these). Writes
// res/values/inflections_<iso>.xml (rule names and English descriptions, as Yomitan has them, or as the app's own rules
// in own-transforms.js name them) and YomitanInflections.kt in dictionary/engine-hoshidicts, then lists the keys that
// translations in res/values-*/inflections_<iso>.xml lack or no longer need. Own rules added to Yomitan's (the Latin
// enclitics, build.mjs `extendedTransforms`) follow Yomitan's in the same file. Own rules of languages that share their
// names (`sharedLabels` of a descriptor: Kazakh and Kyrgyz, 'turkic') take their labels from one file,
// inflections_<shared>.xml, and keep their descriptions, which list endings, untranslated. Rules marked `hidden` get no
// strings. Translations are edited by hand.
import {execFileSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const [yomitanRoot, ...languages] = process.argv.slice(2);
if (!yomitanRoot || languages.length === 0) {
    console.error('usage: node generate-inflections.mjs <yomitan checkout> <iso>...');
    process.exit(1);
}
const pinned = fs.readFileSync(path.join(here, 'yomitan-commit.txt'), 'utf8').trim();
const commit = execFileSync('git', ['-C', yomitanRoot, 'rev-parse', 'HEAD'], {encoding: 'utf8'}).trim();
if (commit !== pinned) {
    console.error(`the checkout is at ${commit}, the strings come from ${pinned} (yomitan-commit.txt)`);
    process.exit(1);
}

const module = path.join(here, '../../dictionary/engine-hoshidicts');
const res = path.join(module, 'src/main/res');
const kotlin = path.join(module, 'src/main/java/com/vpr/screenlate/dictionary/engine/hoshidicts/YomitanInflections.kt');

/**
 * English labels of rules that Yomitan names by an id (Georgian "noun-adj-suffix-stripping") or by a capitalized
 * phrase (Arabic "Perfect Tense"), by language and rule id.
 * @type {Record<string, Record<string, string>>}
 */
const labels = {
    ka: {nounAdjSuffixStripping: 'declension', nounAdjStemCompletion: 'nominative', vowelRestoration: 'dropped vowel'},
    ar: {
        ...Object.fromEntries(['ah', 'n', 't', 'at', 'A', 'uw'].map((s) => [`PVSuff-${s}`, 'perfect'])),
        ...Object.fromEntries(['hw', 'hy', 'hmA', 'hmA-ta', 'hm', 'hn', 'Anta', 'Anti', 'AntmA', 'Antm', 'Antn', 'AnA', 'nHn']
            .map((p) => [`IVPref-${p}`, 'imperfect'])),
        'NSuff-wh': 'm. pl. + pos. pron.',
    },
};

/**
 * The transforms of [iso], imported from the language's own file: the descriptor list imports every language, and some
 * need libraries a checkout does not have until Yomitan is built. They are taken by the folder, as the bundles take
 * them by the descriptor's iso: the code they name their language by may differ (Georgian's say `kat`). A language
 * Yomitan has none for takes the app's own (`own`), whose names are labels for rules named by their affix or in
 * words, and [shared] names the label file of languages whose own rules share their names. Own rules of a language
 * Yomitan has rules for are added after Yomitan's (`added`), or take their place when they name Yomitan's file as one
 * they replace (Irish, build.mjs `replacedTransforms`).
 * @param {string} iso
 * @returns {Promise<{transforms: [string, {name: string, description?: string, hidden?: boolean}][], own: boolean,
 *     added?: Set<string>, shared?: string}>}
 */
async function transformsOf(iso) {
    const own = await import(pathToFileURL(path.join(here, 'own-transforms.js')).href);
    const ownDescriptor = Object.values(own).find((d) => d?.language === iso && d.transforms);
    if (ownDescriptor?.replaces) return {transforms: Object.entries(ownDescriptor.transforms), own: true};
    const dir = path.join(yomitanRoot, 'ext/js/language', iso);
    const files = fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => f.endsWith('transforms.js')) : [];
    for (const file of files) {
        const exports = await import(pathToFileURL(path.join(dir, file)).href);
        for (const descriptor of Object.values(exports)) {
            if (descriptor?.language && descriptor.transforms) {
                const added = Object.entries(ownDescriptor?.transforms ?? {});
                return {
                    transforms: [...Object.entries(descriptor.transforms), ...added],
                    own: false,
                    added: new Set(added.map(([id]) => id)),
                };
            }
        }
    }
    if (ownDescriptor) {
        return {transforms: Object.entries(ownDescriptor.transforms), own: true, shared: ownDescriptor.sharedLabels};
    }
    throw new Error(`${iso}: Yomitan has no transforms, nor does own-transforms.js`);
}

/** Latin letters for the Arabic-script and Hebrew letters of rule ids (-یشان → yshan), for the strings' names only. */
const LATIN = {
    'ا': 'a', 'آ': 'a', 'ب': 'b', 'پ': 'p', 'ت': 't', 'ث': 's', 'ج': 'j', 'چ': 'ch', 'ح': 'h', 'خ': 'kh', 'د': 'd',
    'ذ': 'z', 'ر': 'r', 'ز': 'z', 'ژ': 'zh', 'س': 's', 'ش': 'sh', 'ص': 's', 'ض': 'z', 'ط': 't', 'ظ': 'z', 'ع': 'e',
    'غ': 'gh', 'ف': 'f', 'ق': 'q', 'ک': 'k', 'گ': 'g', 'ل': 'l', 'م': 'm', 'ن': 'n', 'و': 'v', 'ه': 'h', 'ی': 'y',
    'א': 'a', 'ב': 'b', 'ג': 'g', 'ד': 'd', 'ה': 'h', 'ו': 'v', 'ז': 'z', 'ח': 'kh', 'ט': 't', 'י': 'y', 'כ': 'k',
    'ך': 'k', 'ל': 'l', 'מ': 'm', 'ם': 'm', 'נ': 'n', 'ן': 'n', 'ס': 's', 'ע': 'e', 'פ': 'p', 'ף': 'f', 'צ': 'ts',
    'ץ': 'ts', 'ק': 'q', 'ר': 'r', 'ש': 'sh', 'ת': 't',
};

function romanized(text) {
    return [...text].map((c) => LATIN[c] ?? c).join('');
}

/** Whether [text] names affixes: a hyphen at a word's edge (-able, un-, ka-...-an, mag- + rep1). */
function isAffix(text) {
    return /(^|[\s.])-|-($|[\s.])/.test(text);
}

/**
 * A rule worth a translated label: its name is words, not affixes (by the name or the id: Tagalog's -ng is named "ng")
 * or a name in the language's own script.
 */
function hasLabel(id, name) {
    return /[a-z]/i.test(name) && !isAffix(id) && !isAffix(name);
}

/** [text] as a resource name part; empty when it has no Latin letters or digits (a rule named in Hangul). */
function slugOf(text) {
    return text.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
}

function xml(text) {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/\\/g, '\\\\').replace(/'/g, '\\\'').replace(/"/g, '\\"').replace(/\n/g, '\\n');
}

function kotlinString(text) {
    return '"' + text.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$') + '"';
}

/**
 * Lists the keys that the translations of values/[file] lack or no longer need.
 * @param {string} file
 * @param {Set<string>} expected
 */
function checkTranslations(file, expected) {
    for (const dir of fs.readdirSync(res).filter((d) => d.startsWith('values-')).sort()) {
        const translated = path.join(res, dir, file);
        if (!fs.existsSync(translated)) {
            // Rules named only by affixes (Greek ξανα-) have nothing to translate.
            if (expected.size > 0) console.log(`${dir}: ${file} missing`);
            continue;
        }
        const present = new Set([...fs.readFileSync(translated, 'utf8').matchAll(/<string name="([^"]+)"/g)].map((m) => m[1]));
        const missing = [...expected].filter((k) => !present.has(k));
        const stale = [...present].filter((k) => !expected.has(k));
        if (missing.length || stale.length) console.log(`${dir}: missing ${missing}, no longer needed ${stale}`);
    }
}

/** Shared labels by file name, each by key, with the rule id and name of the first language that gave it. */
const sharedLabels = new Map();

const maps = [];
for (const iso of languages) {
    const {transforms, own: ownFile, added = new Set(), shared} = await transformsOf(iso);
    const keys = new Map();
    const lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        ownFile ?
            `<!-- Inflections of "${iso}": names and descriptions of the app's own rules (scripts/yomitan-language/own-transforms.js).` :
            `<!-- Inflections of "${iso}": rule names and descriptions from Yomitan's language code (GPL-3.0), commit ${pinned}` +
                (added.size > 0 ? ',\n     then the app\'s own rules (scripts/yomitan-language/own-transforms.js).' : '.'),
        '     Generated by scripts/yomitan-language/generate-inflections.mjs; do not edit. -->',
        '<resources>',
    ];
    const expected = new Set();
    const rows = [];
    const labelsOf = shared && (sharedLabels.get(shared) ?? sharedLabels.set(shared, new Map()).get(shared));
    for (const [id, {name, description, hidden}] of transforms) {
        if (hidden) continue;
        if (labelsOf) {
            // Labels from the shared file, the same for each language with the rule; descriptions list endings.
            const slug = `inflection_${iso}_${slugOf(id)}`;
            const label = `inflection_${shared}_${slugOf(id)}_label`;
            const known = labelsOf.get(label);
            if (known && (known.id !== id || known.name !== name)) {
                throw new Error(`${iso}: ${id} is named "${name}", ${known.iso} names ${known.id} "${known.name}"`);
            }
            if (!known) labelsOf.set(label, {iso, id, name});
            lines.push(`    <!-- ${id.replace(/--/g, '- -')} -->`);
            lines.push(`    <string name="${slug}" translatable="false">${xml(description)}</string>`);
            rows.push(`            ${kotlinString(id)} to Strings(R.string.${label}, R.string.${slug}),`);
            continue;
        }
        let slug = `inflection_${iso}_` + (slugOf(id) || slugOf(romanized(id)) || slugOf(description ?? '') || 'rule');
        while ([...keys.values()].includes(slug)) slug += '_';
        keys.set(id, slug);
        const ownRule = ownFile || added.has(id);
        const label = ownRule ? name !== id || hasLabel(id, name) : hasLabel(id, name);
        lines.push(`    <!-- ${id.replace(/--/g, '- -')} -->`);
        if (label) {
            // Labels are lowercase like the other rule names; Yomitan capitalizes a few (French "Preterite").
            const own = /^[A-Z][a-z]+$/.test(name) ? name.toLowerCase() : name;
            const shown = labels[iso]?.[id] ?? own;
            lines.push(`    <string name="${slug}_label">${xml(shown)}</string>`);
            expected.add(`${slug}_label`);
        }
        if (description) {
            lines.push(`    <string name="${slug}">${xml(description)}</string>`);
            expected.add(slug);
        }
        rows.push(`            ${kotlinString(id)} to Strings(${label ? `R.string.${slug}_label` : 'null'}, ${description ? `R.string.${slug}` : 'null'}),`);
    }
    lines.push('</resources>');
    fs.writeFileSync(path.join(res, `values/inflections_${iso}.xml`), lines.join('\n') + '\n');
    maps.push(`        ${kotlinString(iso)} to mapOf(\n${rows.join('\n')}\n        ),`);
    console.log(`${iso}: ${transforms.length} rules`);
    checkTranslations(`inflections_${iso}.xml`, expected);
}

for (const [shared, labelsOf] of sharedLabels) {
    const isos = [...new Set([...labelsOf.values()].map((l) => l.iso))];
    const lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        `<!-- Names of the app's own rules shared by "${isos.join('", "')}" (scripts/yomitan-language/own-transforms.js).`,
        '     Generated by scripts/yomitan-language/generate-inflections.mjs; do not edit. -->',
        '<resources>',
        ...[...labelsOf].map(([key, {name}]) => `    <string name="${key}">${xml(name)}</string>`),
        '</resources>',
    ];
    fs.writeFileSync(path.join(res, `values/inflections_${shared}.xml`), lines.join('\n') + '\n');
    console.log(`${shared}: ${labelsOf.size} labels`);
    checkTranslations(`inflections_${shared}.xml`, new Set(labelsOf.keys()));
}

fs.writeFileSync(kotlin, `package com.vpr.screenlate.dictionary.engine.hoshidicts

import android.content.res.Resources
import androidx.annotation.StringRes
import com.vpr.screenlate.dictionary.api.model.Transform

/**
 * Localized names and descriptions of the inflection rules of languages looked up through Yomitan's language code
 * (Yomitan's rules or the app's own), by language code and rule id (what the engine reports). Affixes and names in the
 * language's own script stay as they are unless the app's own rules name them; the id stays in [Transform.name] for
 * Anki's \`{conjugation}\`. Generated by scripts/yomitan-language/generate-inflections.mjs.
 */
internal object YomitanInflections {
    private class Strings(@param:StringRes val label: Int?, @param:StringRes val description: Int?)

    private val STRINGS: Map<String, Map<String, Strings>> = mapOf(
${maps.join('\n')}
    )

    /** Rule ids of [language] (ISO 639-1); empty for a language without rules. */
    fun names(language: String): Set<String> = STRINGS[language]?.keys.orEmpty()

    /** [transform] with its label and description in the resources' language; null when [language] has no such rule. */
    fun localize(resources: Resources, language: String, transform: Transform): Transform? {
        val strings = STRINGS[language]?.get(transform.name) ?: return null
        return transform.copy(
            label = strings.label?.let(resources::getString) ?: transform.label,
            description = strings.description?.let(resources::getString) ?: transform.description,
        )
    }
}
`);
