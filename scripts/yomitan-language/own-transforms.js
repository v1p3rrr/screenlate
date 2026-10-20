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

/**
 * Persian endings and verb prefixes, as the app's own rules: Wiktionary lists the verb forms (میفرستادیم, نمیدانم) and
 * few noun forms. An enclitic comes off first: a possessive (کتابم, کتابشان, خانهاش, پایم), the indefinite or ezafe
 * ی (کتابی, خانهای, پایی), a copula (رفتهاند) or a joined را (آنرا); then a plural (کتابها, درختان, ستارگان →
 * ستاره, دانشجویان); then a comparative (بزرگتر, بزرگترین). A verb prefix (می, نمی, ب, ن) comes off a word on its own.
 * The text has no zero-width non-joiner by then (own-processors.js). Wiktionary's Persian entries give no part of
 * speech, so the results match entries without one (glue.js, `untypedRules`). Two letters stay at least, and three
 * after ب and ن, which start many words (نام, باد); a space does not count as one, so an affix is never cut off the
 * end or start of a phrase's word alone (Yomitan builds a test of every rule's pattern without its flags, so the
 * patterns use no Unicode classes).
 */

/**
 * @param {string} suffix
 * @param {string} replacement
 * @param {import('language-transformer').Condition[]} conditionsIn
 * @param {import('language-transformer').Condition[]} conditionsOut
 * @returns {import('language-transformer').SuffixRule<'n' | 'p' | 'c'>}
 */
function persianSuffix(suffix, replacement, conditionsIn, conditionsOut) {
    return {
        type: 'suffix',
        isInflected: new RegExp('\\S{2}' + suffix + '$'),
        deinflected: replacement,
        deinflect: (text) => text.slice(0, -suffix.length) + replacement,
        conditionsIn,
        conditionsOut,
    };
}

/**
 * @param {string} prefix
 * @param {number} rest the letters that stay at least
 * @returns {import('language-transformer').Rule<'n' | 'p' | 'c'>}
 */
function persianPrefix(prefix, rest) {
    return {
        type: 'prefix',
        isInflected: new RegExp('^' + prefix + '\\S{' + rest + '}'),
        deinflected: '',
        deinflect: (text) => text.slice(prefix.length),
        conditionsIn: [],
        conditionsOut: ['n'],
    };
}

/**
 * Possessives, the indefinite and ezafe ی, the copula and a joined را, which come off before a plural or comparative,
 * each with the name and description the popup shows (generate-inflections.mjs writes them to the app's strings).
 * @type {[string, string, string][]}
 */
const persianEnclitics = [
    ['م', '1st sg.', 'first person singular: my (کتابم) or I am (خوشحالم)'],
    ['ت', '2nd sg.', 'second person singular: your (کتابت)'],
    ['ش', '3rd sg.', 'third person singular: his, her, its (کتابش)'],
    ['مان', '1st pl.', 'first person plural: our (کتابمان)'],
    ['تان', '2nd pl.', 'second person plural: your (کتابتان)'],
    ['شان', '3rd pl.', 'third person plural: their (کتابشان)'],
    ['ام', '1st sg.', 'first person singular after ه: my (خانه\u200Cام) or I am (خسته\u200Cام)'],
    ['ات', '2nd sg.', 'second person singular after ه: your (خانه\u200Cات)'],
    ['اش', '3rd sg.', 'third person singular after ه: his, her, its (خانه\u200Cاش)'],
    ['یم', '1st sg.; 1st pl.', 'first person: my after a vowel (پایم) or we are (خوبیم)'],
    ['یت', '2nd sg.', 'second person singular after a vowel: your (پایت)'],
    ['یش', '3rd sg.', 'third person singular after a vowel: his, her, its (پایش)'],
    ['یمان', '1st pl.', 'first person plural after a vowel: our (پایمان)'],
    ['یتان', '2nd pl.', 'second person plural after a vowel: your (پایتان)'],
    ['یشان', '3rd pl.', 'third person plural after a vowel: their (پایشان)'],
    ['ی', 'indef.; 2nd sg.; ezafe', 'indefinite (کتابی), you are (خوبی) or the ezafe after a vowel (پای)'],
    ['ای', 'indef.; 2nd sg.', 'after ه: indefinite (خانه\u200Cای) or you are (خسته\u200Cای)'],
    ['یی', 'indef.; 2nd sg.', 'after a vowel: indefinite (دانشجویی) or you are (کجایی)'],
    ['اند', '3rd pl.', 'third person plural after ه: they are (خسته\u200Cاند)'],
    ['اید', '2nd pl.', 'second person plural after ه: you are (خسته\u200Cاید)'],
    ['ایم', '1st pl.', 'first person plural after ه: we are (خسته\u200Cایم)'],
    ['را', 'object', 'object marker را written joined (آنرا)'],
];

/**
 * Plural endings and what the word ends in without them, with a name and a description.
 * @type {[string, string, string, string][]}
 */
const persianPlurals = [
    ['ها', '', 'pl.', 'plural (کتابها)'],
    ['ان', '', 'pl.', 'plural, mostly of people and animals (مردان)'],
    ['یان', '', 'pl.', 'plural after a vowel (دانشجویان)'],
    ['گان', 'ه', 'pl.', 'plural of a word ending in a silent ه, which becomes گ (ستارگان from ستاره)'],
];

/** @type {[string, string, string][]} */
const persianComparatives = [
    ['تر', 'comparative', 'comparative (بزرگتر)'],
    ['ترین', 'superlative', 'superlative (بزرگترین)'],
];

/**
 * Verb prefixes and the letters that stay at least, with a name and a description.
 * @type {[string, number, string, string][]}
 */
const persianPrefixes = [
    ['می', 2, 'durative', 'durative prefix: present (می\u200Cروم) and past continuous (می\u200Cرفتم)'],
    ['نمی', 2, 'neg. durative', 'negated durative prefix: present (نمی\u200Cروم) and past continuous (نمی\u200Cرفتم)'],
    ['ب', 3, 'subjunctive', 'prefix of the subjunctive (بروم) and the imperative (برو)'],
    ['ن', 3, 'negative', 'negative prefix: past (نرفتم) and imperative (نرو)'],
];

/** @type {import('language-transformer').LanguageTransformDescriptor<'n' | 'p' | 'c'>} */
export const persianTransforms = {
    language: 'fa',
    conditions: {
        n: {name: 'Word', isDictionaryForm: true},
        p: {name: 'Plural', isDictionaryForm: false},
        c: {name: 'Comparative', isDictionaryForm: false},
    },
    transforms: Object.fromEntries([
        ...persianEnclitics.map(([ending, name, description]) => [`-${ending}`, {
            name,
            description,
            rules: [persianSuffix(ending, '', [], ['n', 'p', 'c'])],
        }]),
        ...persianPlurals.map(([ending, replacement, name, description]) => [`-${ending}`, {
            name,
            description,
            rules: [persianSuffix(ending, replacement, ['p'], ['n', 'c'])],
        }]),
        ...persianComparatives.map(([ending, name, description]) => [`-${ending}`, {
            name,
            description,
            rules: [persianSuffix(ending, '', ['c'], ['n'])],
        }]),
        ...persianPrefixes.map(([prefix, rest, name, description]) => [`${prefix}-`, {
            name,
            description,
            rules: [persianPrefix(prefix, rest)],
        }]),
    ]),
};

/**
 * Hebrew prefixes, written joined to their word, with a name and a description: ו (and), ש and כש (that, when), ב כ ל
 * (in, as, to), מ (from) and the article ה. They come off in the order the grammar has them: ו first, then ש or כש,
 * then one of ב כ ל מ, then ה, which never follows ב כ ל (they take its place: בבית for ב + הבית); three letters at
 * most (ושב, וכש, שמה, ומה). Each rule applies to the word itself and after the prefixes its conditions name, one that
 * names none to the word itself only. The conditions say what has come off: `w` ו, `s1` ש alone, `s2` ש after ו or
 * כש, `m` מ, `x` the last prefix the three letters allow. A word starting with ש, כש or מ gets the rest twice at the
 * first step, the second time under the narrower condition; the engine merges their entries.
 * @type {[string, string, string, [HebrewCondition[], HebrewCondition][]][]}
 */
const hebrewPrefixes = [
    ['ו', 'and', 'and (ואני, והבית)', [[[], 'w']]],
    ['ש', 'that', 'that, which, who (שאמר, שבבית)', [[[], 's1'], [['w'], 's2']]],
    ['כש', 'when', 'when, as (כשהגעתי)', [[[], 's2'], [['w'], 'x']]],
    ['ב', 'in', 'in, at, with (בבית)', [[['w', 's1', 's2'], 'x']]],
    ['כ', 'as', 'like, as (כילד)', [[['w', 's1', 's2'], 'x']]],
    ['ל', 'to', 'to, for (לבית)', [[['w', 's1', 's2'], 'x']]],
    ['מ', 'from', 'from, than (מירושלים, מהבית)', [[['w', 's1'], 'm'], [['s2'], 'x']]],
    ['ה', 'the', 'definite article (הבית)', [[['w', 's1', 's2', 'm'], 'x']]],
];

/** @typedef {'w' | 's1' | 's2' | 'm' | 'x'} HebrewCondition */

/**
 * A prefix that leaves a Hebrew letter and one more character at least. Wiktionary's Hebrew entries give no part of
 * speech, so the engine lets the rest match entries without one (glue.js, `untypedRules`).
 * @param {string} prefix
 * @param {HebrewCondition[]} conditionsIn
 * @param {HebrewCondition} conditionOut
 * @returns {import('language-transformer').Rule<HebrewCondition>}
 */
function hebrewPrefix(prefix, conditionsIn, conditionOut) {
    return {
        type: 'prefix',
        isInflected: new RegExp('^' + prefix + '[\u05D0-\u05F2]\\S'),
        deinflected: '',
        deinflect: (text) => text.slice(prefix.length),
        conditionsIn,
        conditionsOut: [conditionOut],
    };
}

/** @type {import('language-transformer').LanguageTransformDescriptor<HebrewCondition>} */
export const hebrewTransforms = {
    language: 'he',
    conditions: {
        w: {name: 'After ו', isDictionaryForm: false},
        s1: {name: 'After ש', isDictionaryForm: false},
        s2: {name: 'After וש or כש', isDictionaryForm: false},
        m: {name: 'After מ', isDictionaryForm: false},
        x: {name: 'After the last prefix', isDictionaryForm: false},
    },
    transforms: Object.fromEntries(hebrewPrefixes.map(([prefix, name, description, rules]) => [`${prefix}-`, {
        name,
        description,
        rules: rules.map(([conditionsIn, conditionOut]) => hebrewPrefix(prefix, conditionsIn, conditionOut)),
    }])),
};
