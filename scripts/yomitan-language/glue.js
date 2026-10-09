// Experiment: Yomitan's own language code (text processors, transforms) as a candidate generator.
import './intl-stub.js';
import {Translator} from 'yomitan/language/translator.js';
import {getAllLanguageTextProcessors, getAllLanguageTransformDescriptors, isTextLookupWorthy} from 'yomitan/language/languages.js';
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
 * Every text the database should be asked for, as Yomitan's translator builds them before its database lookup.
 * @param {string} text
 * @param {string} language
 * @param {'letter'|'word'} searchResolution
 */
function candidates(text, language, searchResolution) {
    prepare(language);
    const list = translator._getAlgorithmDeinflections(text, {language, searchResolution, textReplacements: [null]});
    return list.map((d) => ({
        o: d.originalText,
        t: d.transformedText,
        d: d.deinflectedText,
        s: Math.min(...d.textProcessorRuleChainCandidates.map((chain) => chain.length)),
        c: d.conditions,
        r: d.inflectionRuleChainCandidates.map((x) => x.inflectionRules),
    }));
}

/**
 * @param {string} language
 * @param {string[]} partsOfSpeech
 */
function posFlags(language, partsOfSpeech) {
    return translator._multiLanguageTransformer.getConditionFlagsFromPartsOfSpeech(language, partsOfSpeech);
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
    partOfSpeechFlagsJson,
    prepare,
    candidates,
    candidatesJson: (text, language, resolution) => JSON.stringify(candidates(text, language, resolution)),
    posFlags,
    isTextLookupWorthy,
};
