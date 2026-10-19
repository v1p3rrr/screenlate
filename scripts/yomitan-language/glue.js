// Entry point of a language bundle: Yomitan's own language code (text processors, deinflection transforms) as a
// candidate generator for the engine's native lookup (dictionary/engine-hoshidicts/src/main/cpp/language).
import './intl-stub.js';
import './console-stub.js';
import {Translator} from 'yomitan/language/translator.js';
import {getAllLanguageTextProcessors, getAllLanguageTransformDescriptors} from 'yomitan/language/languages.js';
import {LanguageTransformer} from 'yomitan/language/language-transformer.js';

const translator = new Translator(/** @type {any} */ (null));

/**
 * Prepares one language only; `Translator.prepare` builds every language's transformer (about 30 MB in QuickJS). The
 * transforms are taken by the descriptor's code: Georgian's name their language `kat`, under which Yomitan files them
 * and its Georgian lookups never find them.
 * @param {string} language
 */
function prepare(language) {
    if (translator._textProcessors.has(language)) { return; }
    for (const {iso, languageTransforms: descriptor} of getAllLanguageTransformDescriptors()) {
        if (iso !== language) { continue; }
        const transformer = new LanguageTransformer();
        transformer.addDescriptor(descriptor);
        translator._multiLanguageTransformer._languageTransformers.set(language, transformer);
    }
    for (const {iso, textPreprocessors = [], textPostprocessors = []} of getAllLanguageTextProcessors()) {
        if (iso !== language) { continue; }
        const preprocessors = textPreprocessors.map((p) => ({...p, textProcessor: replacedProcessors[iso]?.[p.id] ?? p.textProcessor}));
        translator._textProcessors.set(iso, {textPreprocessors: preprocessors, textPostprocessors});
    }
}

/**
 * Turkish upper case of [text]: i ↔ İ and ı ↔ I are the Turkish letter pairs.
 * @param {string} text
 */
function turkishUpperCase(text) {
    return text.replace(/i/g, 'İ').replace(/ı/g, 'I').toUpperCase();
}

/**
 * Text processors that replace Yomitan's by id. Turkish case pairs: without a locale (QuickJS has none) JavaScript
 * lowercases I to i and İ to i with a combining dot, so a Turkish word written in capitals (IŞIK, İSTANBUL) would find
 * no entry. Serbo-Croatian accent marks: Yomitan drops them from Latin vowels and r only, so Cyrillic text written
 * with the marks of readings (ку̏ћа) would find no entry.
 * @type {Record<string, Record<string, import('language').TextProcessor>>}
 */
const replacedProcessors = {
    tr: {
        decapitalize: {
            name: 'Decapitalize text',
            description: 'IŞIK → ışık, İSTANBUL → istanbul',
            process: (str) => [str, str.replace(/I/g, 'ı').replace(/İ/g, 'i').toLowerCase()],
        },
        capitalizeFirstLetter: {
            name: 'Capitalize first letter',
            description: 'istanbul → İstanbul, ılık → Ilık',
            process: (str) => [str, turkishUpperCase(str.charAt(0)) + str.slice(1)],
        },
    },
    sh: {
        removeSerboCroatianAccentMarks: {
            name: 'Remove vowel accents',
            description: 'A\u0301 → A, a\u0301 → a, а\u0301 → а',
            process: (str) => [
                str,
                str.normalize('NFD').replace(/[aeiourAEIOURаеиоурАЕИОУР][\u0300-\u036f]/g, (match) => match[0]),
            ],
        },
    },
};

/**
 * Every text the database should be asked for, as Yomitan's translator builds them before its database lookup:
 * original substring, preprocessed text, deinflected text, preprocessor steps, condition flags, rule chains, and
 * whether an entry without a part of speech may match despite the conditions.
 * @param {string} text
 * @param {string} language
 * @param {'letter'|'word'} searchResolution
 */
function candidates(text, language, searchResolution) {
    prepare(language);
    const list = translator._getAlgorithmDeinflections(text, {language, searchResolution, textReplacements: [null]})
        .filter((d) => isWellFormed(d.originalText) && isWellFormed(d.transformedText) && isWellFormed(d.deinflectedText));
    const verb = englishVerbFlags(language);
    return list.map((d) => ({
        o: d.originalText,
        t: d.transformedText,
        d: d.deinflectedText,
        s: Math.min(...d.textProcessorRuleChainCandidates.map((chain) => chain.length)),
        c: d.conditions,
        r: d.inflectionRuleChainCandidates.map((x) => x.inflectionRules),
        u: (verb !== 0 && isUntypedPhrasalVerb(d, verb)) || isUntypedRule(d, language),
    }));
}

/**
 * Languages whose deinflected texts may match entries without a part of speech, with the shortest such text in
 * characters: Wiktionary's Bengali and Georgian entries give none. The app's Bengali endings (own-transforms.js) are
 * undone once only and leave two characters at least. Yomitan's Georgian rules chain and cut words down to a letter or
 * two (ვადის → ვ), which would find only the letters' entries.
 */
const untypedRules = new Map([['bn', 2], ['ka', 3]]);

/**
 * Whether [d], a deinflected text, may match entries without a part of speech (`untypedRules`).
 * @param {{conditions: number, deinflectedText: string}} d
 * @param {string} language
 */
function isUntypedRule(d, language) {
    const shortest = untypedRules.get(language);
    return shortest !== undefined && d.conditions !== 0 && [...d.deinflectedText].length >= shortest;
}

/**
 * Adverb particles of English phrasal verbs (`phrasalVerbParticles` of Yomitan's english-transforms.js). Prepositions
 * are left out: "closed to" would reach the phrase "close to" (near), "saved for" the phrase "save for" (except).
 */
const englishAdverbParticles = new Set([
    'aboard', 'about', 'above', 'across', 'ahead', 'alongside', 'apart', 'around', 'aside', 'astray', 'away', 'back',
    'before', 'behind', 'below', 'beneath', 'besides', 'between', 'beyond', 'by', 'close', 'down', 'east', 'west',
    'north', 'south', 'eastward', 'westward', 'northward', 'southward', 'forward', 'backward', 'backwards', 'forwards',
    'home', 'in', 'inside', 'instead', 'near', 'off', 'on', 'opposite', 'out', 'outside', 'over', 'overhead', 'past',
    'round', 'since', 'through', 'throughout', 'together', 'under', 'underneath', 'up', 'within', 'without',
]);

/**
 * Condition flags of an English verb, or 0 for other languages. A verb and a phrasal verb have the same flags: the
 * verb condition only groups its sub-condition.
 * @param {string} language
 */
function englishVerbFlags(language) {
    if (language !== 'en') { return 0; }
    const transformer = translator._multiLanguageTransformer._languageTransformers.get(language);
    return transformer?._conditionTypeToConditionFlagsMap.get('v') ?? 0;
}

/**
 * Whether [d] is a verb form of a phrase whose second word is an adverb particle ("picked up" for "pick up").
 * Dictionaries such as Wiktionary exports often give phrases no part of speech, and Yomitan's conditions then reject
 * them; this case lets such entries match. The interposed-object rule ("picked the book up") stays typed only: it
 * drops arbitrary words and would reach phrases like "time out" from "time is running out".
 * @param {{conditions: number, deinflectedText: string, inflectionRuleChainCandidates: {inflectionRules: string[]}[]}} d
 * @param {number} verb
 */
function isUntypedPhrasalVerb(d, verb) {
    if (d.conditions !== verb) { return false; }
    if (d.inflectionRuleChainCandidates.some(({inflectionRules}) => inflectionRules.includes('interposed object'))) {
        return false;
    }
    const words = d.deinflectedText.split(' ');
    return words.length >= 2 && englishAdverbParticles.has(words[1]);
}

/**
 * Whether [text] holds no half of a surrogate pair. The letter resolution shortens the text one UTF-16 unit at a time,
 * which splits a character outside the BMP (𠮶); such a text matches no term, and the engine's JSON reader refuses it.
 * @param {string} text
 */
function isWellFormed(text) {
    for (let i = 0; i < text.length; i++) {
        const unit = text.charCodeAt(i);
        if (unit >= 0xdc00 && unit <= 0xdfff) { return false; }
        if (unit >= 0xd800 && unit <= 0xdbff) {
            const next = text.charCodeAt(i + 1);
            if (!(next >= 0xdc00 && next <= 0xdfff)) { return false; }
            i++;
        }
    }
    return true;
}

/**
 * Condition flags of the parts of speech a dictionary entry may name (`rules`), for filtering deinflections natively.
 * @param {string} language
 * @returns {string}
 */
function partOfSpeechFlagsJson(language) {
    prepare(language);
    const transformer = translator._multiLanguageTransformer._languageTransformers.get(language);
    return JSON.stringify(Object.fromEntries(transformer ? transformer._partOfSpeechToConditionFlagsMap : []));
}

globalThis.yomitanLang = {
    candidates,
    candidatesJson: (text, language, resolution) => JSON.stringify(candidates(text, language, resolution)),
    partOfSpeechFlagsJson,
};
