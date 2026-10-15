// Entry point of a language bundle: Yomitan's own language code (text processors, deinflection transforms) as a
// candidate generator for the engine's native lookup (dictionary/engine-hoshidicts/src/main/cpp/language).
import './intl-stub.js';
import {Translator} from 'yomitan/language/translator.js';
import {getAllLanguageTextProcessors, getAllLanguageTransformDescriptors} from 'yomitan/language/languages.js';
import {LanguageTransformer} from 'yomitan/language/language-transformer.js';

const translator = new Translator(/** @type {any} */ (null));

/**
 * Prepares one language only; `Translator.prepare` builds every language's transformer (about 30 MB in QuickJS).
 * @param {string} language
 */
function prepare(language) {
    if (translator._textProcessors.has(language)) { return; }
    for (const {languageTransforms: descriptor} of getAllLanguageTransformDescriptors()) {
        if (descriptor.language !== language) { continue; }
        const transformer = new LanguageTransformer();
        transformer.addDescriptor(descriptor);
        translator._multiLanguageTransformer._languageTransformers.set(language, transformer);
    }
    for (const {iso, textPreprocessors = [], textPostprocessors = []} of getAllLanguageTextProcessors()) {
        if (iso === language) { translator._textProcessors.set(iso, {textPreprocessors, textPostprocessors}); }
    }
}

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
        u: verb !== 0 && isUntypedPhrasalVerb(d, verb),
    }));
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
