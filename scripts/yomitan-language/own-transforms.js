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
 * few noun forms. An enclitic comes off first: a possessive (کتابم, کتابشان, خانهاش, پایم, spoken کتابشون), the
 * indefinite or ezafe ی (کتابی, خانهای, پایی), a copula (رفتهاند) or a joined را (آنرا); then a plural (کتابها,
 * درختان, ستارگان → ستاره, دانشجویان); then a comparative (بزرگتر, بزرگترین) or an ordinal in -مین (دومین → دوم). A
 * verb prefix (می, نمی, ب, ن) comes off a word on its own. The other spoken endings (the copula خوبه, the object marker
 * اینو, the plural دخترا, -ین) are left out: on the commonest words they find a wrong entry more often than the right
 * one (ai/notes/language-rules.md).
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
    ['مون', '1st pl.', 'spoken first person plural: our (کتابمون) or to us (بهمون)'],
    ['تون', '2nd pl.', 'spoken second person plural: your (کتابتون) or to you (بهتون)'],
    ['شون', '3rd pl.', 'spoken third person plural: their (کتابشون) or to them (بهشون)'],
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
 * The numbers an ordinal in -مین ends in (دومین, سومین, سیزدهمین, دویستمین), which leave the ordinal in -م that
 * Wiktionary lists (دوم, سوم). Only after a number: مسلمین and معلمین are Arabic plurals.
 */
const persianOrdinalNumbers = [
    'یک', 'دو', 'سو', 'چهار', 'پنج', 'شش', 'هفت', 'هشت', 'نه', 'ده', 'یست', 'چهل', 'پنجاه', 'شصت', 'هفتاد', 'هشتاد',
    'نود', 'صد', 'هزار', 'میلیون', 'میلیارد',
];

/** @returns {import('language-transformer').SuffixRule<'n' | 'p' | 'c'>} */
function persianOrdinal() {
    return {
        ...persianSuffix('مین', 'م', ['c'], ['n']),
        isInflected: new RegExp('(' + persianOrdinalNumbers.join('|') + ')مین$'),
    };
}

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
        ['-مین', {name: 'ordinal', description: 'ordinal before a noun (دومین from دوم)', rules: [persianOrdinal()]}],
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

/**
 * Kazakh and Kyrgyz endings, the app's own rules written from the grammar: a noun is stem [plural] [possessive] [case]
 * [predicative], a verb stem [voice] [negation] and then a tense, mood, participle or converb, a participle taking noun
 * endings after it; Kyrgyz attaches its question particle to the word (барасыңбы). Every ending comes in its
 * vowel-harmony and consonant variants, and also with the language's own letters as their Russian twins (-нын for
 * -ның), as OCR and keyboards write them; the import keys headwords under those twins too (`HeadwordMarks`). A stem
 * keeps two letters and a vowel at least; after an ending that starts with a vowel, a noun stem is also tried with its
 * last consonant as the dictionary writes it (аумағы → аумақ, кітабы → кітап).
 *
 * Every Yomitan rule applies to the word as written as well, so the steps that only continue another (negation, voice,
 * the present tense mark before its person ending, a verb stem's dictionary form) read a text that ends in a mark no
 * real text holds, and the engine asks for no text with one (glue.js). A verb stem becomes its dictionary form (оқы- →
 * оқу, жаша- → жашоо) in a step the popup does not show (`hidden`). Wiktionary's entries leave the rules field empty
 * and give the part of speech among their definition tags, so a nominal result matches any entry and a verb result the
 * entries tagged as verbs (glue.js).
 */

/** Ends a text whose rest is a verb stem; see above. */
const TURKIC_VERB = /* @__PURE__ */ String.fromCharCode(0xe000);

/** Ends a text whose rest ends in the present tense mark, its person ending taken off. */
const TURKIC_PRESENT = /* @__PURE__ */ String.fromCharCode(0xe001);

/** @typedef {'n' | 'v' | 'q' | 'cs' | 'px' | 'pl' | 'pt' | 'cv' | 'im' | 'pc' | 'pr' | 'vt' | 'vn' | 'vv'} TurkicCondition */

/**
 * What each kind of ending applies after and leaves, and the mark the text it reads (`reads`) or leaves (`leaves`)
 * ends in. A word as written has no conditions, so every kind applies to it except the ones that read a mark.
 * @type {Record<string, {conditionsIn: TurkicCondition[], conditionsOut: TurkicCondition[], reads?: string, leaves?: string}>}
 */
const turkicSlots = {
    question: {conditionsIn: [], conditionsOut: ['q']},
    please: {conditionsIn: [], conditionsOut: ['im']},
    pleaseStem: {conditionsIn: [], conditionsOut: ['vt'], leaves: TURKIC_VERB},
    predicative: {conditionsIn: ['q'], conditionsOut: ['cs', 'px', 'pl', 'pt', 'cv', 'n']},
    presentPerson: {conditionsIn: ['q'], conditionsOut: ['pr'], leaves: TURKIC_PRESENT},
    pastPerson: {conditionsIn: ['q'], conditionsOut: ['pc']},
    case: {conditionsIn: ['q', 'cs'], conditionsOut: ['px', 'pl', 'pt', 'n']},
    possessive: {conditionsIn: ['q', 'cs', 'px'], conditionsOut: ['pl', 'pt', 'n']},
    plural: {conditionsIn: ['q', 'cs', 'px', 'pl'], conditionsOut: ['pt', 'n']},
    verbalNoun: {conditionsIn: ['q', 'cs', 'px', 'pl', 'pt'], conditionsOut: ['v']},
    participle: {conditionsIn: ['q', 'cs', 'px', 'pl', 'pt'], conditionsOut: ['vt'], leaves: TURKIC_VERB},
    past: {conditionsIn: ['q', 'pc'], conditionsOut: ['vt'], leaves: TURKIC_VERB},
    conditional: {conditionsIn: ['q', 'pc'], conditionsOut: ['vt'], leaves: TURKIC_VERB},
    present: {conditionsIn: ['pr'], conditionsOut: ['vt'], reads: TURKIC_PRESENT, leaves: TURKIC_VERB},
    converb: {conditionsIn: ['q', 'cv'], conditionsOut: ['vt'], leaves: TURKIC_VERB},
    converbAlone: {conditionsIn: ['q'], conditionsOut: ['vt'], leaves: TURKIC_VERB},
    imperative: {conditionsIn: ['q', 'im'], conditionsOut: ['vt'], leaves: TURKIC_VERB},
    negation: {conditionsIn: ['vt'], conditionsOut: ['vn'], reads: TURKIC_VERB, leaves: TURKIC_VERB},
    voice: {conditionsIn: ['vt', 'vn', 'vv'], conditionsOut: ['vv'], reads: TURKIC_VERB, leaves: TURKIC_VERB},
};

/**
 * One language's letters and endings.
 * @typedef {object} TurkicGrammar
 * @property {string} language
 * @property {string} vowels the vowels, the Russian twins of the language's own included
 * @property {string} linkingVowels vowels an ending starts with only after a consonant (-ым after кітап, -м after бала)
 * @property {string} consonants the letters a stem ends in before an ending written with /C (кітап-ты, су-ды, not
 *   оқы-ды, which is a past tense)
 * @property {string} presentVowels what the present tense mark ends in before its person ending (бара-ды, сөйлей-ді)
 * @property {Record<string, string>} twins the language's own letters as the Russian ones OCR and keyboards give
 * @property {Record<string, string>} hardened a noun stem's last consonant before a vowel, as the dictionary writes it
 * @property {Record<string, string[]>} contracted endings written together with a stem's last vowel, with the vowels
 *   the stem may have ended in (Kazakh оқиды from оқы- and the present mark -й)
 * @property {(stem: string) => string[]} dictionaryForms a verb stem's dictionary forms
 * @property {(TurkicTransform | {persons: string[][]})[]} transforms rules, and rows of person endings (`turkicPersons`)
 *   in their place; plain data, so that bundles of other languages drop the tables
 */

/**
 * @typedef {object} TurkicTransform
 * @property {string} id
 * @property {string} name
 * @property {string} example
 * @property {Record<string, string>} endings by kind (`turkicSlots`), each a space-separated list; an ending written
 *   with a slash follows only the letters after it (на/ыі: баласына, not бала-на), with /C only a consonant
 * @property {string[]} [afterNegation] participle endings that follow only the negation (Kazakh бар-ма-с)
 */

/**
 * The transforms of [grammar]. Kazakh and Kyrgyz name their rules alike, so their labels are one set of strings
 * (`sharedLabels`, generate-inflections.mjs); a rule's description lists its endings with an example, which needs no
 * translation.
 * @param {TurkicGrammar} grammar
 * @returns {import('language-transformer').LanguageTransformDescriptor<TurkicCondition> & {sharedLabels: string}}
 */
function turkicTransforms(grammar) {
    const vowels = new Set(grammar.vowels);
    const linking = new Set([...grammar.linkingVowels].flatMap((c) => [c, grammar.twins[c] ?? c]));
    const vowelClass = `[${grammar.vowels}]`;
    const consonantClass = `[^ ${grammar.vowels}${TURKIC_VERB}${TURKIC_PRESENT}]`;
    /** Whether the last word of [text] has two characters and a vowel at least. */
    const isStem = (text) => {
        const word = [...text.slice(text.lastIndexOf(' ') + 1)];
        return word.length >= 2 && word.some((c) => vowels.has(c));
    };
    /** [ending] and, when it has letters of the language's own, the spelling with their Russian twins. */
    const spellings = (ending) => [...new Set([ending, [...ending].map((c) => grammar.twins[c] ?? c).join('')])];
    /**
     * @param {string} ending
     * @param {string} slot
     * @param {string} before what the stem ends in, as a regular expression; '' for anything
     * @param {(stem: string) => string} restore the stem as the rest of the word writes it
     * @returns {import('language-transformer').SuffixRule<TurkicCondition>}
     */
    const rule = (ending, slot, before, restore = (stem) => stem) => {
        const {conditionsIn, conditionsOut, reads = '', leaves = ''} = turkicSlots[slot];
        const length = ending.length + reads.length;
        return {
            type: 'suffix',
            isInflected: new RegExp(before + ending + reads + '$'),
            deinflected: '',
            // A stem too short is no text, which the engine leaves out.
            deinflect: (text) => {
                const stem = restore(text.slice(0, -length));
                return isStem(stem) ? stem + leaves : '';
            },
            conditionsIn,
            conditionsOut,
        };
    };
    /**
     * The rules of one ending in each spelling: a single consonant follows a vowel, a linking vowel a consonant, the
     * present tense mark's person ending the mark, and a past or conditional person ending the vowel of its tense.
     */
    const rulesOf = (item, slot) => spellings(item.split('/')[0]).flatMap((spelling) => {
        const [ending, after] = item.split('/');
        const first = spelling[0];
        const contracted = grammar.contracted[ending];
        const letters = after === 'C' ? grammar.consonants : after;
        if (letters) return [rule(spelling, slot, `[${[...new Set(spellings(letters).join(''))].join('')}]`)];
        if (contracted) return contracted.map((vowel) => rule(spelling, slot, consonantClass, (stem) => stem + vowel));
        if (slot === 'presentPerson') return [rule(spelling, slot, `[${grammar.presentVowels}]`)];
        if (slot === 'pastPerson') return [rule(spelling, slot, vowelClass)];
        if ([...spelling].length === 1 && !vowels.has(first)) return [rule(spelling, slot, vowelClass)];
        if (!vowels.has(first)) return [rule(spelling, slot, '')];
        const rules = [rule(spelling, slot, linking.has(first) ? consonantClass : '')];
        // Verb stems keep the consonant their dictionary form has (табады, табу).
        if (!turkicSlots[slot].leaves) {
            for (const [from, to] of Object.entries(grammar.hardened)) {
                rules.push(rule(spelling, slot, from, (stem) => stem.slice(0, -1) + to));
            }
        }
        return rules;
    });
    /** @type {Record<string, {name: string, description?: string, hidden?: boolean, rules: import('language-transformer').Rule<TurkicCondition>[]}>} */
    const transforms = {};
    const list = grammar.transforms.flatMap((item) => ('persons' in item ? turkicPersons(item.persons) : [item]));
    for (const {id, name, example, endings, afterNegation = []} of list) {
        const rules = Object.entries(endings).flatMap(([slot, list]) => list.split(' ').flatMap((ending) => rulesOf(ending, slot)));
        for (const ending of afterNegation) {
            for (const spelling of spellings(ending)) rules.push(rule(spelling, 'participle', '[мбп][аеоө]'));
        }
        const listed = [...new Set([...Object.values(endings).flatMap((list) => list.split(' ')), ...afterNegation]
            .map((item) => item.split('/')[0]))];
        transforms[id] = {name, description: `${listed.map((e) => '-' + e).join(' ')} (${example})`, rules};
    }
    // As many rules as a stem has dictionary forms at most; each gives one, or no text.
    transforms['dictionary form'] = {
        name: 'dictionary form',
        hidden: true,
        rules: [0, 1, 2, 3].map((index) => ({
            type: 'suffix',
            isInflected: new RegExp(TURKIC_VERB + '$'),
            deinflected: '',
            deinflect: (text) => grammar.dictionaryForms(text.slice(0, -1))[index] ?? '',
            conditionsIn: ['vt', 'vn', 'vv'],
            conditionsOut: ['v'],
        })),
    };
    return {
        language: grammar.language,
        sharedLabels: 'turkic',
        conditions: {
            n: {name: 'Nominal', isDictionaryForm: true},
            v: {name: 'Verb', isDictionaryForm: true},
            q: {name: 'Before the question particle', isDictionaryForm: false},
            cs: {name: 'Before a case ending', isDictionaryForm: false},
            px: {name: 'Before a possessive ending', isDictionaryForm: false},
            pl: {name: 'Before a plural ending', isDictionaryForm: false},
            pt: {name: 'Before a participle ending', isDictionaryForm: false},
            cv: {name: 'Before a converb ending', isDictionaryForm: false},
            im: {name: 'Before an imperative ending', isDictionaryForm: false},
            pc: {name: 'Before a past or conditional ending', isDictionaryForm: false},
            pr: {name: 'Before the present tense mark', isDictionaryForm: false},
            vt: {name: 'Verb stem', isDictionaryForm: false},
            vn: {name: 'Verb stem before the negation', isDictionaryForm: false},
            vv: {name: 'Verb stem before a voice ending', isDictionaryForm: false},
        },
        transforms,
    };
}

/**
 * The person endings of nominal predicates, of the present tense and of the past and conditional, as transforms: rows
 * of id, name, example and the three lists.
 * @param {string[][]} persons
 * @returns {TurkicTransform[]}
 */
function turkicPersons(persons) {
    return persons.map(([id, name, example, predicative, present, past]) => ({
        id,
        name,
        example,
        endings: Object.fromEntries([['predicative', predicative], ['presentPerson', present], ['pastPerson', past]]
            .filter(([, list]) => list !== '')),
    }));
}

/** Kazakh: two-way vowel harmony (-лар, -лер), the letters ә ғ қ ң ө ұ ү һ і. */
export const kazakhTransforms = /* @__PURE__ */ turkicTransforms({
    language: 'kk',
    vowels: 'аәеиоөұүыіэюяуё',
    linkingVowels: 'ыі',
    // у after a vowel is a consonant (су, ау).
    consonants: 'бвгғджзйкқлмнңпрстуфхһцчшщъь',
    presentVowels: 'аейи',
    twins: {'қ': 'к', 'ғ': 'г', 'ң': 'н', 'ұ': 'у', 'ү': 'у', 'ө': 'о', 'ә': 'а', 'і': 'и', 'һ': 'х'},
    hardened: {'ғ': 'қ', 'г': 'к', 'б': 'п'},
    contracted: {'и': ['ы', 'і'], 'итын': ['ы'], 'итін': ['і']},
    // ы and і give way to у (оқы- → оқу), й to ю (қой- → қою), и takes ю (ки- → кию). A last п қ к is voiced
    // before у (тап- → табу).
    dictionaryForms: (stem) => {
        const last = stem.slice(-1);
        const rest = stem.slice(0, -1);
        const voiced = {'п': 'б', 'қ': 'ғ', 'к': 'г'}[last];
        if ('ыі'.includes(last)) return [rest + 'у'];
        if (last === 'й') return [rest + 'ю'];
        if (last === 'и') return [stem + 'ю'];
        return voiced ? [stem + 'у', rest + voiced + 'у'] : [stem + 'у'];
    },
    transforms: [
        {id: 'plural', name: 'plural', example: 'кітаптар', endings: {plural: 'лар лер дар дер тар тер'}},
        {id: 'possessive 1sg', name: 'my', example: 'кітабым', endings: {possessive: 'ым ім м'}},
        {id: 'possessive 2sg', name: 'your', example: 'кітабың', endings: {possessive: 'ың ің ң'}},
        {id: 'possessive 2sg polite', name: 'your (polite)', example: 'кітабыңыз', endings: {possessive: 'ыңыз іңіз ңыз ңіз'}},
        {id: 'possessive 3rd', name: 'his, her, their', example: 'кітабы, баласы', endings: {possessive: 'ы і сы сі'}},
        {id: 'possessive 1pl', name: 'our', example: 'кітабымыз', endings: {possessive: 'ымыз іміз мыз міз'}},
        {id: 'genitive', name: 'genitive', example: 'кітаптың', endings: {case: 'ның нің дың/C дің/C тың/C тің/C'}},
        {id: 'dative', name: 'dative', example: 'кітапқа, баласына', endings: {case: 'ға ге қа ке на/ыі не/ыі а/мң е/мң'}},
        {id: 'accusative', name: 'accusative', example: 'кітапты, баласын', endings: {case: 'ны ні ды/C ді/C ты/C ті/C н/ыі'}},
        {id: 'locative', name: 'locative', example: 'кітапта, баласында', endings: {case: 'да де та те нда/ыі нде/ыі'}},
        {id: 'ablative', name: 'ablative', example: 'кітаптан, баласынан', endings: {case: 'дан ден тан тен нан нен'}},
        {id: 'instrumental', name: 'instrumental', example: 'кітаппен', endings: {case: 'мен бен пен менен бенен пенен'}},
        {id: 'attributive locative', name: 'attributive locative', example: 'үйдегі', endings: {case: 'дағы дегі тағы тегі ндағы/ыі ндегі/ыі'}},
        {id: 'attributive', name: 'attributive', example: 'жазғы', endings: {case: 'ғы гі қы кі'}},
        {id: 'equative', name: 'equative', example: 'баладай', endings: {case: 'дай дей тай тей'}},
        {id: 'manner', name: 'manner', example: 'қазақша', endings: {case: 'ша ше'}},
        {persons: [
            ['1sg', '1st sg.', 'барамын, бардым', 'мын мін бын бін пын пін', 'мын мін', 'м'],
            ['2sg', '2nd sg.', 'барасың, бардың', 'сың сің', 'сың сің', 'ң'],
            ['2sg polite', '2nd sg. polite', 'барасыз, бардыңыз', 'сыз сіз', 'сыз сіз', 'ңыз ңіз'],
            ['3rd', '3rd person', 'барады', '', 'ды ді', ''],
            ['1pl', '1st pl.', 'барамыз, бардық', 'мыз міз быз біз пыз піз', 'мыз міз', 'қ к'],
            ['2pl', '2nd pl.', 'барасыңдар, бардыңдар', 'сыңдар сіңдер', 'сыңдар сіңдер', 'ңдар ңдер'],
            ['2pl polite', '2nd pl. polite', 'барасыздар, бардыңыздар', 'сыздар сіздер', 'сыздар сіздер', 'ңыздар ңіздер'],
        ]},
        {id: 'past', name: 'past', example: 'барды', endings: {past: 'ды ді ты ті'}},
        {id: 'present', name: 'present', example: 'барады, оқиды', endings: {present: 'а е й и'}},
        {id: 'conditional', name: 'conditional', example: 'барса', endings: {conditional: 'са се'}},
        {id: 'past participle', name: 'past participle', example: 'барған', endings: {participle: 'ған ген қан кен'}},
        {id: 'habitual participle', name: 'habitual participle', example: 'баратын, оқитын', endings: {participle: 'атын етін йтын йтін итын итін'}},
        {id: 'aorist', name: 'aorist', example: 'барар, бармас', endings: {participle: 'ар ер р'}, afterNegation: ['с']},
        {id: 'intention', name: 'intention', example: 'бармақ', endings: {participle: 'мақ мек бақ бек пақ пек'}},
        {id: 'converb', name: 'converb', example: 'барып, бара', endings: {converb: 'ып іп п', converbAlone: 'а е й'}},
        {id: 'purpose', name: 'purpose', example: 'барғалы', endings: {converb: 'ғалы гелі қалы келі'}},
        {id: 'until', name: 'until', example: 'барғанша', endings: {converb: 'ғанша генше қанша кенше'}},
        {id: 'imperative', name: 'imperative', example: 'барыңыз, барсын', endings: {imperative: 'ыңыз іңіз ңыз ңіз ыңдар іңдер ңдар ңдер ыңыздар іңіздер ңыздар ңіздер сын сін айын ейін йын йін айық ейік йық йік'}},
        {id: 'please', name: 'please', example: 'айтшы', endings: {please: 'шы ші', pleaseStem: 'шы ші'}},
        {id: 'negative', name: 'negative', example: 'бармады', endings: {negation: 'ма ме ба бе па пе'}},
        {id: 'passive', name: 'passive', example: 'жазылды', endings: {voice: 'ыл іл л'}},
        {id: 'reflexive', name: 'reflexive', example: 'киінді', endings: {voice: 'ын ін н'}},
        {id: 'reciprocal', name: 'reciprocal', example: 'көрісті', endings: {voice: 'ыс іс с'}},
        {id: 'causative', name: 'causative', example: 'жаздырды', endings: {voice: 'дыр дір тыр тір ғыз гіз қыз кіз т'}},
    ],
});

/** Kyrgyz: four-way vowel harmony (-лар, -лер, -лор, -лөр), the letters ң ө ү, the question particle joined. */
export const kyrgyzTransforms = /* @__PURE__ */ turkicTransforms({
    language: 'ky',
    vowels: 'аеиоөуүыэюяё',
    linkingVowels: 'ыиуү',
    consonants: 'бвгджзйклмнңпрстфхцчшщъь',
    presentVowels: 'аеоөй',
    twins: {'ң': 'н', 'ө': 'о', 'ү': 'у'},
    hardened: {'г': 'к', 'б': 'п'},
    contracted: {},
    // A vowel lengthens into the ending (жаша- → жашоо, иште- → иштөө, оку- → окуу); after a consonant -уу or -үү by
    // the last vowel, a last п к voiced (тап- → табуу); й takes -юу or -уу (кой- → коюу, сүй- → сүйүү). With ө and ү
    // written о and у the forms come out as the import keys them.
    dictionaryForms: (stem) => {
        const last = stem.slice(-1);
        const rest = stem.slice(0, -1);
        const vowel = [...stem].reverse().find((c) => 'аеиоөуүыэюяё'.includes(c)) ?? 'а';
        const ending = 'аыоуяё'.includes(vowel) ? 'уу' : 'үү';
        const lengthened = {'а': rest + 'оо', 'е': rest + 'өө', 'о': stem + 'о', 'ө': stem + 'ө', 'у': stem + 'у', 'ү': stem + 'ү', 'ы': rest + 'уу', 'и': rest + 'үү'}[last];
        if (lengthened) return [lengthened];
        if (last === 'й') return [rest + (ending === 'уу' ? 'юу' : 'юү'), stem + ending];
        const voiced = {'п': 'б', 'к': 'г'}[last];
        return voiced ? [stem + ending, rest + voiced + ending] : [stem + ending];
    },
    transforms: [
        {id: 'question', name: 'question', example: 'барасыңбы', endings: {question: 'бы би бу бү пы пи пу пү'}},
        {id: 'plural', name: 'plural', example: 'китептер', endings: {plural: 'лар лер лор лөр дар дер дор дөр тар тер тор төр'}},
        {id: 'possessive 1sg', name: 'my', example: 'китебим', endings: {possessive: 'ым им ум үм м'}},
        {id: 'possessive 2sg', name: 'your', example: 'китебиң', endings: {possessive: 'ың иң уң үң ң'}},
        {id: 'possessive 2sg polite', name: 'your (polite)', example: 'китебиңиз', endings: {possessive: 'ыңыз иңиз уңуз үңүз ңыз ңиз ңуз ңүз'}},
        {id: 'possessive 3rd', name: 'his, her, their', example: 'китеби, баласы', endings: {possessive: 'ы и у ү сы си су сү'}},
        {id: 'possessive 1pl', name: 'our', example: 'китебибиз', endings: {possessive: 'ыбыз ибиз убуз үбүз быз биз буз бүз'}},
        {id: 'possessive 2pl', name: 'your (pl.)', example: 'китебиңер', endings: {possessive: 'ыңар иңер уңар үңөр ңар ңер ңор ңөр'}},
        {id: 'genitive', name: 'genitive', example: 'китептин', endings: {case: 'нын нин нун нүн дын/C дин/C дун/C дүн/C тын/C тин/C тун/C түн/C'}},
        {id: 'dative', name: 'dative', example: 'китепке, баласына', endings: {case: 'га ге го гө ка ке ко кө на/ыиуү не/ыиуү но/ыиуү нө/ыиуү а/мң е/мң о/мң ө/мң'}},
        {id: 'accusative', name: 'accusative', example: 'китепти, баласын', endings: {case: 'ны ни ну нү ды/C ди/C ду/C дү/C ты/C ти/C ту/C тү/C н/ыиуү'}},
        {id: 'locative', name: 'locative', example: 'китепте, баласында', endings: {case: 'да де до дө та те то тө нда/ыиуү нде/ыиуү ндо/ыиуү ндө/ыиуү'}},
        {id: 'ablative', name: 'ablative', example: 'китептен, баласынан', endings: {case: 'дан ден дон дөн тан тен тон төн нан/ыиуү нен/ыиуү нон/ыиуү нөн/ыиуү'}},
        {id: 'attributive locative', name: 'attributive locative', example: 'үйдөгү', endings: {case: 'дагы деги догу дөгү тагы теги тогу төгү ндагы/ыиуү ндеги/ыиуү ндогу/ыиуү ндөгү/ыиуү'}},
        {id: 'equative', name: 'equative', example: 'баладай', endings: {case: 'дай дей дой дөй тай тей той төй'}},
        {id: 'manner', name: 'manner', example: 'кыргызча', endings: {case: 'ча че чо чө'}},
        {persons: [
            ['1sg', '1st sg.', 'барамын, бардым', 'мын мин мун мүн', 'мын мин мун мүн м', 'м'],
            ['2sg', '2nd sg.', 'барасың, бардың', 'сың сиң суң сүң', 'сың сиң суң сүң', 'ң'],
            ['2sg polite', '2nd sg. polite', 'барасыз, бардыңыз', 'сыз сиз суз сүз', 'сыз сиз суз сүз', 'ңыз ңиз ңуз ңүз'],
            ['3rd', '3rd person', 'барат', '', 'т', ''],
            ['1pl', '1st pl.', 'барабыз, бардык', 'быз биз буз бүз пыз пиз пуз пүз', 'быз биз буз бүз', 'к'],
            ['2pl', '2nd pl.', 'барасыңар, бардыңар', 'сыңар сиңер суңар сүңөр', 'сыңар сиңер суңар сүңөр', 'ңар ңер ңор ңөр'],
            ['2pl polite', '2nd pl. polite', 'барасыздар, бардыңыздар', 'сыздар сиздер суздар сүздөр', 'сыздар сиздер суздар сүздөр', 'ңыздар ңиздер ңуздар ңүздөр'],
        ]},
        {id: 'past', name: 'past', example: 'барды', endings: {past: 'ды ди ду дү ты ти ту тү'}},
        {id: 'present', name: 'present', example: 'барат, иштейт', endings: {present: 'а е о ө й'}},
        {id: 'conditional', name: 'conditional', example: 'барса', endings: {conditional: 'са се со сө'}},
        {id: 'past participle', name: 'past participle', example: 'барган', endings: {participle: 'ган ген гон гөн кан кен кон көн'}},
        {id: 'habitual participle', name: 'habitual participle', example: 'баруучу', endings: {verbalNoun: 'чу чү'}},
        {id: 'aorist', name: 'aorist', example: 'барар, барбас', endings: {participle: 'ар ер ор өр р'}, afterNegation: ['с']},
        {id: 'intention', name: 'intention', example: 'бармак', endings: {participle: 'мак мек мок мөк'}},
        {id: 'converb', name: 'converb', example: 'барып, бара', endings: {converb: 'ып ип уп үп п', converbAlone: 'а е о ө й'}},
        {id: 'purpose', name: 'purpose', example: 'баргалы', endings: {converb: 'галы гели голу гөлү калы кели колу көлү'}},
        {id: 'until', name: 'until', example: 'баргыча', endings: {converb: 'гыча гиче гуча гүчө кыча киче куча күчө'}},
        {id: 'imperative', name: 'imperative', example: 'барыңыз, барсын', endings: {imperative: 'гыла гиле гула гүлө кыла киле кула күлө ыңыз иңиз уңуз үңүз ңыз ңиз ңуз ңүз сын син сун сүн айын ейин ойун өйүн алы ели олу өлү ыңар иңер уңар үңөр ңар ңер ңор ңөр'}},
        {id: 'negative', name: 'negative', example: 'барбады', endings: {negation: 'ба бе бо бө па пе по пө ма ме мо мө'}},
        {id: 'passive', name: 'passive', example: 'жазылды', endings: {voice: 'ыл ил ул үл л'}},
        {id: 'reflexive', name: 'reflexive', example: 'жуунду', endings: {voice: 'ын ин ун үн н'}},
        {id: 'reciprocal', name: 'reciprocal', example: 'көрүштү', endings: {voice: 'ыш иш уш үш ш'}},
        {id: 'causative', name: 'causative', example: 'жаздырды', endings: {voice: 'дыр дир дур дүр тыр тир тур түр гыз гиз гуз гүз кыз киз куз күз т'}},
    ],
});
