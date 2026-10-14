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
 * original substring, preprocessed text, deinflected text, preprocessor steps, condition flags, rule chains.
 * @param {string} text
 * @param {string} language
 * @param {'letter'|'word'} searchResolution
 */
function candidates(text, language, searchResolution) {
    prepare(language);
    const list = translator._getAlgorithmDeinflections(text, {language, searchResolution, textReplacements: [null]})
        .filter((d) => isWellFormed(d.originalText) && isWellFormed(d.transformedText) && isWellFormed(d.deinflectedText));
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
