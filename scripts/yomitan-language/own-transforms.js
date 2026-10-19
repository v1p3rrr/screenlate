// Inflection rules of the app's own, which build.mjs gives to languages Yomitan has none for (addedTransforms).

/**
 * Bengali noun endings, longest first: classifiers (টি, টা, খানা, গুলো), plurals (রা, দের) and cases (র, কে, তে, এ).
 * Wiktionary lists few Bengali forms and its entries give no part of speech, so each ending is undone once and the
 * engine lets the result match entries without one (glue.js, `untypedRules`). An ending needs two characters before
 * it, so short words are not cut down to one letter. The endings are in NFC, as the composed text and the headwords.
 */
const bengaliEndings = [
    'গুলোতে', 'গুলিতে', 'গুলোর', 'গুলির', 'গুলোকে', 'গুলিকে', 'গুলো', 'গুলি',
    'টিতে', 'টাতে', 'টির', 'টার', 'টিকে', 'টাকে', 'টি', 'টা', 'খানা', 'খানি',
    'দের', 'েরা', 'রা', 'য়ের', 'য়', 'ের', 'র', 'কে', 'তে', 'ে',
];

/**
 * @param {string} ending
 * @returns {import('language-transformer').SuffixRule<'n'>}
 */
function bengaliEnding(ending) {
    return {
        type: 'suffix',
        isInflected: new RegExp('.{2}' + ending + '$'),
        deinflected: '',
        deinflect: (text) => text.slice(0, -ending.length),
        conditionsIn: [],
        conditionsOut: ['n'],
    };
}

/** @type {import('language-transformer').LanguageTransformDescriptor<'n'>} */
export const bengaliTransforms = {
    language: 'bn',
    conditions: {
        n: {name: 'Noun', isDictionaryForm: true},
    },
    transforms: Object.fromEntries(bengaliEndings.map((ending) => [
        `-${ending}`,
        {name: `-${ending}`, rules: [bengaliEnding(ending)]},
    ])),
};
