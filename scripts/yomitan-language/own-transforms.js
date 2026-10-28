// Inflection rules of the app's own, which build.mjs gives to languages Yomitan has none for (addedTransforms) or adds
// to Yomitan's (extendedTransforms).

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
 * @property {string} [glides] vowel letters that take a linking vowel as a consonant does (Kazakh у: оқуы, тауым)
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
 *   with a slash follows only the letters after it (на/ыі: баласына, not бала-на), with /C only a consonant; one
 *   written with > takes the stem's letters after it back (уып>п: тауып from тап-)
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
    const beforeLinking = grammar.glides ? `(?:${consonantClass}|[${grammar.glides}])` : consonantClass;
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
    const rulesOf = (item, slot) => {
        if (item.includes('>')) {
            const [ending, letters] = item.split('>');
            return spellings(ending).map((spelling) => rule(spelling, slot, '', (stem) => stem + letters));
        }
        return spellings(item.split('/')[0]).flatMap((spelling) => rulesOfSpelling(item, spelling, slot));
    };
    /**
     * The rules of [item] in one of its spellings.
     * @param {string} item
     * @param {string} spelling
     * @param {string} slot
     */
    const rulesOfSpelling = (item, spelling, slot) => {
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
        const rules = [rule(spelling, slot, linking.has(first) ? beforeLinking : '')];
        // Verb stems keep the consonant their dictionary form has (табады, табу).
        if (!turkicSlots[slot].leaves) {
            for (const [from, to] of Object.entries(grammar.hardened)) {
                rules.push(rule(spelling, slot, from, (stem) => stem.slice(0, -1) + to));
            }
        }
        return rules;
    };
    /** @type {Record<string, {name: string, description?: string, hidden?: boolean, rules: import('language-transformer').Rule<TurkicCondition>[]}>} */
    const transforms = {};
    const list = grammar.transforms.flatMap((item) => ('persons' in item ? turkicPersons(item.persons) : [item]));
    for (const {id, name, example, endings, afterNegation = []} of list) {
        const rules = Object.entries(endings).flatMap(([slot, list]) => list.split(' ').flatMap((ending) => rulesOf(ending, slot)));
        for (const ending of afterNegation) {
            for (const spelling of spellings(ending)) rules.push(rule(spelling, 'participle', '[мбп][аеоө]'));
        }
        const listed = [...new Set([...Object.values(endings).flatMap((list) => list.split(' ')), ...afterNegation]
            .map((item) => item.split(/[/>]/)[0]))];
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
    // у takes a linking vowel as a consonant does (оқуы, тауым).
    glides: 'у',
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
        {id: 'equative', name: 'equative', example: 'баладай, баласындай', endings: {case: 'дай дей тай тей ндай/ыі ндей/ыі'}},
        {id: 'manner', name: 'manner', example: 'қазақша, пікірінше', endings: {case: 'ша ше нша/ыі нше/ыі'}},
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
        {id: 'converb', name: 'converb', example: 'барып, тауып, бара', endings: {converb: 'ып іп п уып>п уіп>п', converbAlone: 'а е й'}},
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
        {id: 'equative', name: 'equative', example: 'баладай, өзүндөй', endings: {case: 'дай дей дой дөй тай тей той төй ндай/ыиуү ндей/ыиуү ндой/ыиуү ндөй/ыиуү'}},
        {id: 'manner', name: 'manner', example: 'кыргызча, өзүнчө', endings: {case: 'ча че чо чө нча/ыиуү нче/ыиуү нчо/ыиуү нчө/ыиуү'}},
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
        // Takes noun endings as a participle does (болушу, билдиришинче); written as the reciprocal voice is.
        {id: 'verbal noun', name: 'verbal noun', example: 'болушу, келиши', endings: {participle: 'ыш иш уш үш ш'}},
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

/**
 * The Norwegian genitive -s (dagens → dagen, verdens → verden, Norges → Norge), which Wiktionary's Bokmål entries do not
 * list: 7.1% of the lowercase words of news and Wikipedia text that have no entry. A word in -s takes no further s
 * (Hans' bil), so -ss is left alone. Wiktionary's entries leave the rules field empty and name the part of speech
 * among their definition tags, so the result matches nouns, names and forms only (glue.js): ellers does not reach
 * eller "or", and mens reaches men "harm", not men "but".
 */
/** @type {import('language-transformer').LanguageTransformDescriptor<'n'>} */
export const norwegianTransforms = {
    language: 'no',
    conditions: {
        n: {name: 'Noun', isDictionaryForm: true},
    },
    transforms: {
        '-s': {
            name: 'genitive',
            description: 'genitive (dagens, Norges)',
            rules: [{
                type: 'suffix',
                isInflected: /.[^s]s$/,
                deinflected: '',
                deinflect: (text) => text.slice(0, -1),
                conditionsIn: [],
                conditionsOut: ['n'],
            }],
        },
    },
};

/**
 * @param {string} suffix
 * @param {string} replacement
 * @returns {import('language-transformer').SuffixRule<'n'>}
 */
function romanianSuffix(suffix, replacement) {
    return {
        type: 'suffix',
        isInflected: new RegExp('.{2}' + suffix + '$'),
        deinflected: replacement,
        deinflect: (text) => text.slice(0, -suffix.length) + replacement,
        conditionsIn: [],
        conditionsOut: ['n'],
    };
}

/**
 * Romanian article and agreement endings, as the app's own rules. Wiktionary's form rows give most forms (stored
 * without the letters' marks: numarul for numărul, which Yomitan's removeAlphabeticDiacritics also looks up), but none
 * of words whose entries have no declension table: consiliul, dezvoltarea, comisiei, aflată (1,086 of the 5,230
 * words of wordfreq's 30,000 most frequent that find nothing). Each ending is undone once and leaves two letters at
 * least; the result must be a noun, a name, an adjective, a numeral, a pronoun, a determiner or a form (glue.js,
 * `taggedRules`), and the engine keeps it only for a text without an entry of its own
 * (`LanguageSupport.inflectionRulesOnlyWithoutEntry`): beside such entries the rules found wrong words for one word in
 * seven (militarii → militară beside militar). Endings written with ă, ș or ț have twins without the marks for text
 * typed without them; the import keys headwords without the marks too (`HeadwordMarks`), so araba finds arabă.
 * Verb forms are Wiktionary's own (există, știu) and get no rules.
 * @type {import('language-transformer').LanguageTransformDescriptor<'n'>}
 */
export const romanianTransforms = {
    language: 'ro',
    conditions: {
        n: {name: 'Noun or adjective', isDictionaryForm: true},
    },
    transforms: {
        '-ul': {
            name: 'definite',
            description: 'definite article (procesul from proces, consiliul from consiliu)',
            rules: [romanianSuffix('ul', ''), romanianSuffix('ul', 'u')],
        },
        '-le': {
            name: 'definite',
            description: 'definite article (fratele from frate, textele from texte)',
            rules: [romanianSuffix('le', '')],
        },
        '-a': {
            name: 'definite',
            description: 'definite article of feminine nouns (dezvoltarea from dezvoltare, comisia from comisie)',
            rules: [romanianSuffix('ea', 'e'), romanianSuffix('ia', 'ie')],
        },
        '-i': {
            name: 'definite plural',
            description: 'definite article of masculine plurals (pomii from pomi)',
            rules: [romanianSuffix('ii', 'i')],
        },
        '-ului': {
            name: 'definite genitive',
            description: 'genitive and dative with the definite article (procesului from proces, fratelui from frate)',
            rules: [romanianSuffix('ului', ''), romanianSuffix('lui', '')],
        },
        '-ei': {
            name: 'definite genitive',
            description: 'genitive and dative of feminine nouns with the definite article (Moldovei from Moldova, comisiei from comisie)',
            rules: [romanianSuffix('ei', 'a'), romanianSuffix('ei', 'ă'), romanianSuffix('iei', 'ie')],
        },
        '-ii': {
            name: 'definite genitive',
            description: 'genitive and dative of feminine nouns in -e with the definite article (lumii from lume, dezvoltării from dezvoltare)',
            rules: [romanianSuffix('ii', 'e'), romanianSuffix('ării', 'are'), romanianSuffix('arii', 'are')],
        },
        '-lor': {
            name: 'definite genitive plural',
            description: 'genitive and dative plural with the definite article (caselor from case)',
            rules: [romanianSuffix('lor', '')],
        },
        'agreement': {
            name: 'feminine or plural',
            description: 'feminine or plural of a participle or adjective (aflată, aflate, aflați from aflat; pusă from pus)',
            rules: [
                romanianSuffix('tă', 't'), romanianSuffix('ta', 't'), romanianSuffix('te', 't'),
                romanianSuffix('ți', 't'), romanianSuffix('ti', 't'),
                romanianSuffix('să', 's'), romanianSuffix('sa', 's'), romanianSuffix('se', 's'),
                romanianSuffix('și', 's'), romanianSuffix('si', 's'),
            ],
        },
    },
};

/**
 * Swahili verb prefixes, as the app's own rules written from the grammar: a verb is [negation] subject tense [object]
 * stem, joined into one word (a-li-ni-ona, ha-tu-ku-soma). Wiktionary lists the present, habitual, infinitive,
 * subjunctive and negative present forms of its verbs, and none of the past, future or perfect ones (alisoma, atasoma,
 * amesoma), nor of the forms with an object (ninakupenda) or a relative (aliyesoma). The rules take the prefixes off
 * from the outside in: a negation, then a subject, then a tense, then a relative, then an object or the reflexive ji-;
 * the habitual hu- and an infinitive's ku- stand alone (husoma, kusoma: Wiktionary links an infinitive to -soma, a
 * headword it does not have), and a relative may also end a verb without a tense (waishio). Every Yomitan rule
 * applies to the word as written as well, so the texts between the steps start with a mark no real text holds (what
 * comes next: a tense, a negative tense, a relative or an object), the rules after the first read only such texts,
 * and the engine asks for none of them (glue.js). A stem is asked for as it is, with ku- (kula, kuandika: some
 * dictionaries write their verbs so) and with a hyphen (-amka, as others write them). It keeps two letters at least,
 * and two syllables after li-, na-, ta-, me-, nge- and a relative, where one-syllable verbs keep their ku- (alikula,
 * aliyekuwa; so Angela is no a-nge-la); after ki-, ka-, ja- and the negative ku- they drop it (akiwa, akaja, hakuwa).
 * Wiktionary's Swahili entries leave the rules field empty and tag each definition (v, n, non-lemma), so a stem
 * reaches the verbs and forms only (glue.js, `taggedRules`), and the engine keeps these results
 * only for a text without an entry of its own (`LanguageSupport.inflectionRulesOnlyWithoutEntry`). The negative
 * present's -i (hasomi) is left to Wiktionary's form rows.
 */

/** Starts a text after an affirmative subject: a tense follows. */
const SWAHILI_TENSE = /* @__PURE__ */ String.fromCharCode(0xe010);

/** Starts a text after ha-: a subject or a negative tense follows. */
const SWAHILI_NEGATED = /* @__PURE__ */ String.fromCharCode(0xe011);

/** Starts a text after a negative subject (or si-, hu-): a negative tense follows. */
const SWAHILI_NEGATIVE_TENSE = /* @__PURE__ */ String.fromCharCode(0xe012);

/** Starts a text after a tense, a relative, the habitual or an infinitive's ku-: an object or the reflexive follows. */
const SWAHILI_OBJECT = /* @__PURE__ */ String.fromCharCode(0xe013);

/**
 * Relative markers, by the class of what they stand for: -ye- (a person), -o- (people; classes 3, 11, 14), -cho- -vyo-,
 * -lo- -yo-, -yo- -zo-, -po- -ko- -mo- (place and time).
 */
const swahiliRelatives = ['ye', 'o', 'cho', 'vyo', 'lo', 'yo', 'zo', 'po', 'ko', 'mo'];

/** @typedef {'s' | 'n' | 't' | 'o' | 'r' | 'v'} SwahiliCondition */

/** What follows a prefix: two letters at least, or two syllables. */
const SWAHILI_REST = "[a-z'’]{2}";
const SWAHILI_TWO_SYLLABLES = "[^aeiou]*[aeiou][^aeiou]*[aeiou]";

/**
 * A prefix taken off [reads] (the mark the text starts with, or the word as written), leaving [leaves] (the next
 * step's mark) or the stem as [headword] writes it ('' as it is, 'ku', '-'); [rest] is what has to follow.
 * @param {string} reads
 * @param {string} prefix
 * @param {SwahiliCondition[]} conditionsIn
 * @param {{leaves: string, condition: SwahiliCondition} | {headword: string}} out
 * @param {string} rest
 * @returns {import('language-transformer').Rule<SwahiliCondition>}
 */
function swahiliPrefix(reads, prefix, conditionsIn, out, rest = SWAHILI_REST) {
    const start = 'leaves' in out ? out.leaves : out.headword;
    const length = reads.length + prefix.length;
    return {
        type: 'prefix',
        isInflected: new RegExp(`^${reads}${prefix}${rest}`),
        deinflected: start,
        deinflect: (text) => start + text.slice(length),
        conditionsIn,
        conditionsOut: ['leaves' in out ? out.condition : 'v'],
    };
}

/**
 * A prefix that leaves a stem: the stem as it is, with a hyphen, and with ku- where [withKu], of two syllables where
 * [twoSyllables]; and, where [next] names what may follow (`o` a relative or an object, `r` an object), the text that
 * comes off.
 * @param {string} reads
 * @param {string} prefix
 * @param {SwahiliCondition[]} conditionsIn
 * @param {{withKu?: boolean, twoSyllables?: boolean, next?: 'o' | 'r'}} options
 * @returns {import('language-transformer').Rule<SwahiliCondition>[]}
 */
function swahiliStem(reads, prefix, conditionsIn, {withKu = false, twoSyllables = false, next} = {}) {
    const stem = twoSyllables ? SWAHILI_TWO_SYLLABLES : SWAHILI_REST;
    return [
        swahiliPrefix(reads, prefix, conditionsIn, {headword: ''}, stem),
        swahiliPrefix(reads, prefix, conditionsIn, {headword: '-'}, stem),
        ...(withKu ? [swahiliPrefix(reads, prefix, conditionsIn, {headword: 'ku'}, stem)] : []),
        ...(next ? [swahiliPrefix(reads, prefix, conditionsIn, {leaves: SWAHILI_OBJECT, condition: next})] : []),
    ];
}

/**
 * A tense prefix: after an affirmative subject or, for [negative] ones, after a negation; one-syllable verbs keep their
 * ku- after it where [keepsKu].
 * @param {string} prefix
 * @param {{affirmative?: boolean, negative?: boolean, keepsKu?: boolean}} kinds
 * @returns {import('language-transformer').Rule<SwahiliCondition>[]}
 */
function swahiliTense(prefix, {affirmative = true, negative = false, keepsKu = true} = {}) {
    const options = {withKu: true, twoSyllables: keepsKu, next: /** @type {'o'} */ ('o')};
    return [
        ...(affirmative ? swahiliStem(SWAHILI_TENSE, prefix, ['t'], options) : []),
        ...(negative ? swahiliStem(SWAHILI_NEGATED, prefix, ['s'], options) : []),
        ...(negative ? swahiliStem(SWAHILI_NEGATIVE_TENSE, prefix, ['n'], options) : []),
    ];
}

/**
 * A subject prefix: on the word as written, a tense follows; after ha-, a negative tense ([negative]: not ni- and a-,
 * which si- and ha- stand for).
 * @param {string} prefix
 * @param {boolean} negative
 * @returns {import('language-transformer').Rule<SwahiliCondition>[]}
 */
function swahiliSubject(prefix, negative = true) {
    return [
        swahiliPrefix('', prefix, [], {leaves: SWAHILI_TENSE, condition: 't'}),
        ...(negative ? [swahiliPrefix(SWAHILI_NEGATED, prefix, ['s'], {leaves: SWAHILI_NEGATIVE_TENSE, condition: 'n'})] : []),
    ];
}

/** Noun classes' subject prefixes: ki- vi- li- ya- i- zi- ku- pa- mu-; u- is also you's. */
const swahiliClassSubjects = ['ki', 'vi', 'li', 'ya', 'i', 'zi', 'ku', 'pa', 'mu'];

/** Object prefixes: ni- ku- m- mw- tu- wa- (persons) and the noun classes' ki- vi- li- ya- i- zi- u- pa- mu-. */
const swahiliObjects = ['ni', 'ku', 'mw', 'm', 'tu', 'wa', 'ki', 'vi', 'li', 'ya', 'i', 'zi', 'u', 'pa', 'mu'];

/**
 * A relative ending a verb without a tense, after its subject (wa-ishi-o, i-itwa-yo): the stem as it is and with a
 * hyphen. The stem ends in a vowel; it may be the copula's li- or si- (iliyo, isiyo).
 * @param {string} relative
 * @returns {import('language-transformer').Rule<SwahiliCondition>[]}
 */
function swahiliRelativeSuffix(relative) {
    return ['', '-'].map((headword) => ({
        type: 'prefix',
        isInflected: new RegExp(`^${SWAHILI_TENSE}[a-z'’]+[aeiu]${relative}$`),
        deinflected: headword,
        deinflect: (text) => headword + text.slice(SWAHILI_TENSE.length, -relative.length),
        conditionsIn: ['t'],
        conditionsOut: ['v'],
    }));
}

/** @type {import('language-transformer').LanguageTransformDescriptor<SwahiliCondition>} */
export const swahiliTransforms = {
    language: 'sw',
    conditions: {
        s: {name: 'After ha-', isDictionaryForm: false},
        n: {name: 'Before a negative tense', isDictionaryForm: false},
        t: {name: 'Before a tense', isDictionaryForm: false},
        o: {name: 'Before a relative or an object', isDictionaryForm: false},
        r: {name: 'Before an object', isDictionaryForm: false},
        v: {name: 'Verb', isDictionaryForm: true},
    },
    transforms: {
        'negative': {
            name: 'negative',
            description: 'negative: ha- before the subject (hatukusoma, hakusoma), si- for I and hu- for you (sikusoma, hukusoma), si- after the subject before a relative (isiyojulikana)',
            rules: [
                swahiliPrefix('', 'ha', [], {leaves: SWAHILI_NEGATED, condition: 's'}),
                swahiliPrefix('', 'si', [], {leaves: SWAHILI_NEGATIVE_TENSE, condition: 'n'}),
                swahiliPrefix('', 'hu', [], {leaves: SWAHILI_NEGATIVE_TENSE, condition: 'n'}),
                swahiliPrefix(SWAHILI_TENSE, 'si', ['t'], {leaves: SWAHILI_OBJECT, condition: 'o'}),
            ],
        },
        'I': {name: 'I', description: 'subject ni-: I (ninasoma, nilisoma)', rules: swahiliSubject('ni', false)},
        'you': {
            name: 'you',
            description: 'subject u-: you, or a noun of classes 3, 11 and 14 (unasoma; mti umeanguka, the tree has fallen)',
            rules: swahiliSubject('u'),
        },
        'he/she': {name: 'he/she', description: 'subject a-: he or she (anasoma, alisoma)', rules: swahiliSubject('a', false)},
        'we': {name: 'we', description: 'subject tu-: we (tunasoma, hatukusoma)', rules: swahiliSubject('tu')},
        'you pl.': {name: 'you pl.', description: 'subject m-: you, more than one (mnasoma, hamkusoma)', rules: swahiliSubject('m')},
        'they': {name: 'they', description: 'subject wa-: they (wanasoma, hawakusoma)', rules: swahiliSubject('wa')},
        'class': {
            name: 'class',
            description: 'subject of a noun class: ki- vi- (kitabu kimeanguka), li- ya- (jina, majina), i- zi- (nyumba), ku- pa- mu- (places: kulikuwa, there was)',
            rules: swahiliClassSubjects.flatMap((prefix) => swahiliSubject(prefix)),
        },
        'past': {
            name: 'past',
            description: 'past: li-, and ku- after a negation (alisoma, hakusoma)',
            rules: [...swahiliTense('li'), ...swahiliTense('ku', {affirmative: false, negative: true, keepsKu: false})],
        },
        'present': {name: 'present', description: 'present: na- (anasoma)', rules: swahiliTense('na')},
        'future': {
            name: 'future',
            description: 'future: ta-, and taka- before a relative (atasoma, hatasoma, atakayesoma)',
            rules: [...swahiliTense('ta', {negative: true}), ...swahiliTense('taka')],
        },
        'perfect': {name: 'perfect', description: 'perfect: me- (amesoma, has read)', rules: swahiliTense('me')},
        'not yet': {
            name: 'not yet',
            description: 'not yet: ja- after a negation (hajasoma, has not read yet)',
            rules: swahiliTense('ja', {affirmative: false, negative: true, keepsKu: false}),
        },
        'conditional': {
            name: 'conditional',
            description: 'conditional: nge- and ngali- (angesoma, would read; angalisoma, would have read)',
            rules: [...swahiliTense('nge'), ...swahiliTense('ngali')],
        },
        'when': {name: 'when', description: 'ki-: when, while or if (akisoma)', rules: swahiliTense('ki', {keepsKu: false})},
        'and then': {name: 'and then', description: 'ka-: and then, in a story (akasoma)', rules: swahiliTense('ka', {keepsKu: false})},
        'habitual': {
            name: 'habitual',
            description: 'habitual: hu- (husoma, reads as a rule)',
            rules: swahiliStem('', 'hu', [], {withKu: true, next: 'r'}),
        },
        'relative': {
            name: 'relative',
            description: 'relative: who, which, where (aliyesoma, the one who read; kinachoitwa, which is called; waishio, who live)',
            rules: [
                ...swahiliRelatives.flatMap((relative) => [
                    ...swahiliStem(SWAHILI_OBJECT, relative, ['o'], {twoSyllables: true}),
                    swahiliPrefix(SWAHILI_OBJECT, relative, ['o'], {leaves: SWAHILI_OBJECT, condition: 'r'}),
                ]),
                ...swahiliRelatives.flatMap(swahiliRelativeSuffix),
            ],
        },
        'object': {
            name: 'object',
            description: 'object: ni- me, ku- you, m- or mw- him or her, tu- us, wa- them, and the noun class prefixes ki- vi- li- ya- i- zi- u- pa- mu- (ninakupenda, alimwona)',
            rules: swahiliObjects.flatMap((prefix) => swahiliStem(SWAHILI_OBJECT, prefix, ['o', 'r'])),
        },
        'reflexive': {
            name: 'reflexive',
            description: 'reflexive ji-: oneself (alijificha, kujiunga)',
            rules: swahiliStem(SWAHILI_OBJECT, 'ji', ['o', 'r']),
        },
        'infinitive': {
            name: 'infinitive',
            description: 'infinitive: ku- or kw- (kusoma, kwandika, kumpa)',
            rules: [...swahiliStem('', 'ku', [], {next: 'r'}), ...swahiliStem('', 'kw', [])],
        },
    },
};

/**
 * A Latin enclitic undone: the word before it keeps two letters at least.
 * @param {string} enclitic
 * @returns {import('language-transformer').SuffixRule<never>}
 */
function latinEnclitic(enclitic) {
    return {
        type: 'suffix',
        isInflected: new RegExp('.{2}' + enclitic + '$'),
        deinflected: '',
        deinflect: (text) => text.slice(0, -enclitic.length),
        conditionsIn: [],
        conditionsOut: [],
    };
}

/**
 * Latin enclitics joined to a word (magisque, estne, mortuusve), which Wiktionary lists only for a few words (itaque,
 * nonne), added to Yomitan's Latin rules. They come first, as the outermost ending, and take any word. Many words
 * end in them without one (atque, sine, regione), so the engine shows these results only where the whole word has no
 * entry (LatinSupport's inflectionRulesOnlyWithoutEntry): on Latin Wikipedia text the endings reach 0.56% of the words
 * and on classical prose and verse 2.7%, nearly all of them right.
 * @type {import('language-transformer').LanguageTransformDescriptor<never>}
 */
export const latinEnclitics = {
    language: 'la',
    conditions: {},
    transforms: {
        '-que': {name: '-que', description: 'Enclitic "and" (magisque from magis)', rules: [latinEnclitic('que')]},
        '-ne': {name: '-ne', description: 'Enclitic asking a question (videsne from vides)', rules: [latinEnclitic('ne')]},
        '-ve': {name: '-ve', description: 'Enclitic "or" (mortuusve from mortuus)', rules: [latinEnclitic('ve')]},
    },
};

/** @typedef {'n' | 'adj' | 'v'} AlbanianCondition */

/**
 * An Albanian ending undone: the word keeps two letters at least. [lemmaOnly]: the texts the rule may reach only as a
 * headword of their own, not through a form-of entry. `albanianRanks` sets the rank.
 * @param {string} ending
 * @param {string} replacement
 * @param {AlbanianCondition} condition
 * @param {RegExp} [lemmaOnly]
 */
function albanianEnding(ending, replacement, condition, lemmaOnly) {
    return {
        type: /** @type {const} */ ('suffix'),
        isInflected: new RegExp('^[^ ]{2,}' + ending + '$'),
        deinflected: replacement,
        deinflect: (/** @type {string} */ text) => text.slice(0, -ending.length) + replacement,
        conditionsIn: [],
        conditionsOut: [condition],
        ending,
        rank: 0,
        ...(lemmaOnly ? {lemmaOnly} : {}),
    };
}

/**
 * Case endings that never stack: a noun that ends in one is not reached through a form-of entry of its text by a
 * second one (partneritet is not partnerit, a form of partner, with -et).
 */
const ALBANIAN_CASE_ENDINGS = /(it|in|ut|un|et|ët|ës|ën|isë|ve)$/;

/** @param {string} ending @param {string} replacement */
function albanianNounEnding(ending, replacement) {
    return albanianEnding(ending, replacement, 'n', ALBANIAN_CASE_ENDINGS);
}

const ALBANIAN_CONDITION_ORDER = {n: 0, adj: 1, v: 2};

/**
 * [descriptor] with each rule's rank, the order the engine tries the endings in: the longest first, at equal length
 * nouns, adjectives, then verbs, else as listed.
 * @template {{transforms: Record<string, {rules: ReturnType<typeof albanianEnding>[]}>}} T
 * @param {T} descriptor
 * @returns {T}
 */
function albanianRanks(descriptor) {
    Object.values(descriptor.transforms)
        .flatMap((transform) => transform.rules)
        .map((rule, index) => ({rule, index}))
        .sort((a, b) => (b.rule.ending.length - a.rule.ending.length) ||
            (ALBANIAN_CONDITION_ORDER[a.rule.conditionsOut[0]] - ALBANIAN_CONDITION_ORDER[b.rule.conditionsOut[0]]) ||
            (a.index - b.index))
        .forEach(({rule}, rank) => { rule.rank = rank; });
    return descriptor;
}

/**
 * Albanian endings added to Yomitan's rules, which leave adjective forms (rëndësishme, sotme), mediopassive verbs
 * (ndodhet, shtrihet) and many noun cases unfound: 20% of the lowercase words of Wikipedia text have no entry after
 * Yomitan's rules, and these endings find 18-20% of them (3.6-3.7% of the words), 1.3-1.4% of their hits wrong. Each
 * names the part of speech of the entry it must reach (an entry of the text, or a form-of entry whose lemma has it) and
 * is undone once. They are `fallback` rules (glue.js): the engine tries them only on a word that neither a headword
 * nor Yomitan's rules find, by rank (`albanianRanks`: lejen → leje, a noun, before lej, a verb), and the first rule
 * that reaches an entry gives the result.
 * @type {import('language-transformer').LanguageTransformDescriptor<AlbanianCondition>}
 */
export const albanianEndings = /* @__PURE__ */ albanianRanks({
    language: 'sq',
    conditions: {},
    transforms: {
        '-e': {
            name: 'feminine or plural',
            description: 'feminine or plural of an adjective (moderne from modern, turistike from turistik)',
            fallback: true,
            rules: [albanianEnding('e', '', 'adj')],
        },
        'ë as e': {
            name: 'e for ë',
            description: 'final ë written as e (gjate for gjatë, mire for mirë)',
            fallback: true,
            rules: [albanianEnding('e', 'ë', 'adj')],
        },
        '-a': {
            name: 'feminine plural',
            description: 'feminine plural of an adjective (gjitha from gjithë, larta from lartë, vjetra from vjetër, vogla from vogël)',
            fallback: true,
            rules: [
                albanianEnding('a', 'ë', 'adj'), albanianEnding('ta', 'të', 'adj'), albanianEnding('ra', 'ër', 'adj'),
                albanianEnding('la', 'ël', 'adj'),
            ],
        },
        '-me': {
            name: 'feminine or plural',
            description: 'feminine or plural of an adjective in -ëm (sotme from sotëm, rëndësishme from rëndësishëm)',
            fallback: true,
            rules: [albanianEnding('shme', 'shëm', 'adj'), albanianEnding('me', 'ëm', 'adj')],
        },
        '-het': {
            name: 'mediopassive',
            description: 'mediopassive present, third person (ndodhet and ndodhen from ndodh, shtrihet from shtrij, mendohet from mendoj)',
            fallback: true,
            rules: [
                albanianEnding('ohet', 'oj', 'v'), albanianEnding('ohen', 'oj', 'v'), albanianEnding('het', 'j', 'v'),
                albanianEnding('hen', 'j', 'v'), albanianEnding('et', '', 'v'), albanianEnding('en', '', 'v'),
            ],
        },
        '-hej': {
            name: 'mediopassive imperfect',
            description: 'mediopassive imperfect, third person singular (bëhej from bëj, formohej from formoj)',
            fallback: true,
            rules: [albanianEnding('ohej', 'oj', 'v'), albanianEnding('hej', 'j', 'v')],
        },
        '-uar': {
            name: 'participle',
            description: 'participle, also as a feminine or plural adjective (bazuar and banuara from bazoj, banoj; shkruar from shkruaj; thirrur and mbetura from thirr, mbet; kthyer from kthej)',
            fallback: true,
            rules: [
                albanianEnding('uara', 'oj', 'v'), albanianEnding('uara', 'uaj', 'v'), albanianEnding('uar', 'oj', 'v'),
                albanianEnding('uar', 'uaj', 'v'), albanianEnding('ura', '', 'v'), albanianEnding('ur', '', 'v'),
                albanianEnding('yer', 'ej', 'v'),
            ],
        },
        '-ua': {
            name: 'aorist',
            description: 'aorist, third person (përfundoi from përfundoj; botua and vërtetuan from botoj, vërtetoj; shkrua from shkruaj; kthye from kthej)',
            fallback: true,
            rules: [
                albanianEnding('uan', 'oj', 'v'), albanianEnding('ua', 'oj', 'v'), albanianEnding('ua', 'uaj', 'v'),
                albanianEnding('ye', 'ej', 'v'), albanianEnding('oi', 'oj', 'v'),
            ],
        },
        '-on': {
            name: 'present',
            description: 'present, third person (daton and datojnë from datoj, hyjnë from hyj)',
            fallback: true,
            rules: [albanianEnding('ojnë', 'oj', 'v'), albanianEnding('jnë', 'j', 'v'), albanianEnding('on', 'oj', 'v')],
        },
        '-onte': {
            name: 'imperfect',
            description: 'imperfect, third person singular (numëronte from numëroj)',
            fallback: true,
            rules: [albanianEnding('onte', 'oj', 'v')],
        },
        '-i': {
            name: 'definite',
            description: 'definite form of a masculine noun (termi from term, sporti from sport)',
            fallback: true,
            rules: [albanianNounEnding('i', ''), albanianNounEnding('u', '')],
        },
        '-it': {
            name: 'definite genitive',
            description: 'genitive, dative or ablative of a definite masculine noun (sulmit from sulm, jugut from jug)',
            fallback: true,
            rules: [albanianNounEnding('it', ''), albanianNounEnding('ut', '')],
        },
        '-in': {
            name: 'definite accusative',
            description: 'accusative of a definite masculine noun (sulmin from sulm, ndikimin from ndikim)',
            fallback: true,
            rules: [albanianNounEnding('in', ''), albanianNounEnding('un', '')],
        },
        '-ët': {
            name: 'definite plural',
            description: 'definite plural (aleatët from aleat, dokumentet from dokument)',
            fallback: true,
            rules: [albanianNounEnding('ët', ''), albanianNounEnding('et', '')],
        },
        '-ve': {
            name: 'genitive plural',
            description: 'genitive, dative or ablative plural (tipareve from tipar, shqiptarve from shqiptar, rrokjeve from rrokje)',
            fallback: true,
            rules: [albanianNounEnding('eve', ''), albanianNounEnding('ve', '')],
        },
        '-ia': {
            name: 'definite',
            description: 'definite form of a feminine noun (industria from industri, shitja from shitje, gjermanishtja from gjermanishte)',
            fallback: true,
            rules: [albanianNounEnding('ia', 'i'), albanianNounEnding('ja', 'je'), albanianNounEnding('ja', 'e')],
        },
        '-së': {
            name: 'definite genitive',
            description: 'genitive, dative or ablative of a definite feminine noun (drejtësisë from drejtësi, paqes from paqe, federatës from federatë)',
            fallback: true,
            rules: [albanianNounEnding('isë', 'i'), albanianNounEnding('es', 'e'), albanianNounEnding('ës', 'ë')],
        },
        '-ën': {
            name: 'definite accusative',
            description: 'accusative of a definite feminine noun (lejen from leje, konferencën from konferencë)',
            fallback: true,
            rules: [albanianNounEnding('en', 'e'), albanianNounEnding('ën', 'ë')],
        },
    },
});

/**
 * A letter of a Latin-script word, with its marks (ŵ ŷ á ò). Yomitan joins the patterns of a transform's rules into
 * one without flags (its heuristic), so the letters are given by range, not by Unicode property.
 */
const LATIN_LETTER = '[A-Za-zÀ-ÖØ-öø-ÿĀ-ſḀ-ỿ]';

/**
 * A rule undoing an initial mutation: [mutated] at the start of a word becomes [radical] (nghath → cath, Nghymru →
 * Cymru). It applies to the word as written only (no conditions in), so a word is never unmutated twice, and gives
 * [conditionsOut], every dictionary form of the language, so the radical matches entries of any part of speech
 * (glue.js `untypedRules` for entries without one). A letter follows the mutated ones and the radical keeps two
 * letters at least: Gaelic th' and bh' are elided tha and bha, not mutated t' and b'. [before] is a pattern the rest
 * of the word starts with (a vowel after h-), [not] one it does not (dd is soft d, not soft t + d); [nextCase] changes
 * the case of the letter after the mutated ones (Wely → Gwely, Hysgol → Ysgol).
 * @template {string} T
 * @param {string} mutated
 * @param {string} radical
 * @param {T[]} conditionsOut
 * @param {{before?: string, not?: string, nextCase?: 'lower' | 'upper'}} [context]
 * @returns {import('language-transformer').Rule<T>}
 */
function mutationRule(mutated, radical, conditionsOut, {before, not, nextCase} = {}) {
    const rest = Math.max(1, 2 - radical.length);
    /** @type {(c: string) => string} */
    const cased = nextCase === 'lower' ? (c) => c.toLowerCase() : nextCase === 'upper' ? (c) => c.toUpperCase() : (c) => c;
    return {
        type: 'prefix',
        isInflected: new RegExp('^' + mutated + (not ? `(?!${not})` : '') + (before ? `(?=${before})` : '') +
            `(?=${LATIN_LETTER}{${rest}})`),
        deinflected: radical,
        deinflect: (text) => radical + cased(text.charAt(mutated.length)) + text.slice(mutated.length + 1),
        conditionsIn: [],
        conditionsOut,
    };
}

/**
 * [mutationRule] for a word in lowercase and for one starting with a capital: [mutated] and [radical] with their first
 * letter in upper case (Ngh → C), or the radical alone when nothing was dropped (Ardd → Gardd, [upperBefore] then
 * naming the capitals that may follow).
 * @template {string} T
 * @param {string} mutated
 * @param {string} radical
 * @param {T[]} conditionsOut
 * @param {{before?: string, upperBefore?: string, not?: string}} [context]
 * @returns {import('language-transformer').Rule<T>[]}
 */
function mutationRules(mutated, radical, conditionsOut, {before, upperBefore, not} = {}) {
    /** @param {string} s */
    const capital = (s) => s.charAt(0).toUpperCase() + s.slice(1);
    return [
        mutationRule(mutated, radical, conditionsOut, {before, not}),
        mutated === '' ?
            mutationRule('', capital(radical), conditionsOut, {before: upperBefore, nextCase: 'lower'}) :
            mutationRule(capital(mutated), capital(radical), conditionsOut,
                {before: upperBefore ?? before, not, ...(radical === '' ? {nextCase: /** @type {const} */ ('upper')} : {})}),
    ];
}

/** Welsh vowels, w and y among them, with the marks the language writes on them. */
const WELSH_VOWELS = 'aeiouwyâêîôûŵŷáéíóúẃýàèìòùẁỳäëïöüẅÿ';
const WELSH_CAPITAL_VOWELS = 'AEIOUWYÂÊÎÔÛŴŶÁÉÍÓÚẂÝÀÈÌÒÙẀỲÄËÏÖÜẄŸ';

/** @typedef {'w'} WelshCondition */

/** @type {WelshCondition[]} */
const WELSH_WORD = ['w'];

/**
 * Welsh initial mutations undone ([mutationRule]): soft (b d g f dd l r for p t c b/m d ll rh, and the g it drops
 * before a vowel, w, l and r: wely → gwely, ardd → gardd), nasal (mh nh ngh m n ng for p t c b d g), aspirate (ph th
 * ch for p t c) and h before a vowel (ei hysgol → ysgol). Wiktionary's Welsh entries give no part of speech and list
 * mutated forms as form-of entries under tags of their own, which the engine names after these rules
 * (`LanguageSupport.formTagRules`). [unmutated] lists common words that are no mutated forms although the rules read
 * them so (mae "is", not bae "bay"), lowercase: the rules leave them as written (glue.js), their own form-of entries
 * still lead where they do.
 * @type {import('language-transformer').LanguageTransformDescriptor<WelshCondition> & {unmutated: string[]}}
 */
export const welshTransforms = {
    language: 'cy',
    conditions: {
        w: {name: 'Word', isDictionaryForm: true},
    },
    transforms: {
        'soft mutation': {
            name: 'soft mutation',
            description: 'soft mutation: b d g f dd l r for p t c b/m d ll rh, g dropped (ei gath from cath, dy wely from gwely)',
            rules: [
                ...mutationRules('b', 'p', WELSH_WORD),
                ...mutationRules('d', 't', WELSH_WORD, {not: 'd'}),
                ...mutationRules('g', 'c', WELSH_WORD),
                ...mutationRules('f', 'b', WELSH_WORD, {not: 'f'}),
                ...mutationRules('f', 'm', WELSH_WORD, {not: 'f'}),
                ...mutationRules('dd', 'd', WELSH_WORD),
                ...mutationRules('l', 'll', WELSH_WORD, {not: 'l'}),
                ...mutationRules('r', 'rh', WELSH_WORD, {not: 'h'}),
                ...mutationRules('', 'g', WELSH_WORD, {
                    before: `(?:[${WELSH_VOWELS}]|l(?!l)|r(?!h))`,
                    upperBefore: `(?:[${WELSH_CAPITAL_VOWELS}]|L(?!l)|R(?!h))`,
                }),
            ],
        },
        'nasal mutation': {
            name: 'nasal mutation',
            description: 'nasal mutation: mh nh ngh m n ng for p t c b d g (fy nghath from cath, yng Nghymru from Cymru)',
            rules: [
                ...mutationRules('mh', 'p', WELSH_WORD),
                ...mutationRules('nh', 't', WELSH_WORD),
                ...mutationRules('ngh', 'c', WELSH_WORD),
                ...mutationRules('m', 'b', WELSH_WORD, {not: 'h'}),
                ...mutationRules('n', 'd', WELSH_WORD, {not: '[hg]'}),
                ...mutationRules('ng', 'g', WELSH_WORD, {not: 'h'}),
            ],
        },
        'aspirate mutation': {
            name: 'aspirate mutation',
            description: 'aspirate mutation: ph th ch for p t c (ei chath from cath)',
            rules: [
                ...mutationRules('ph', 'p', WELSH_WORD),
                ...mutationRules('th', 't', WELSH_WORD),
                ...mutationRules('ch', 'c', WELSH_WORD),
            ],
        },
        'h-prothesis': {
            name: 'h-prothesis',
            description: 'h before a vowel (ei hysgol from ysgol)',
            rules: mutationRules('h', '', WELSH_WORD, {before: `[${WELSH_VOWELS}]`}),
        },
    },
    // From mutation-exceptions.mjs on Leipzig's Welsh Wikipedia sample (the 1,000 commonest words), reviewed by hand
    // with the words before each: those whose rule result the English dictionary does not give as their lemma (a,
    // mae, wedi; not elwir, which follows a), those with a sense of their own at least five times as common as the
    // result (ar, gan, dal, dŵr; not lan, mostly glan after ar), and a few that are themselves wherever they stand (yr,
    // hun, menywod, English the and of).
    unmutated: [
        'a', 'aeth', 'afon', 'allan', 'am', 'angen', 'ap', 'ar', 'awr', 'bellach', 'blaen', 'ble', 'da', 'dail', 'dal',
        'dan', 'dau', 'de', 'deg', 'draw', 'dros', 'drwy', 'du', 'dwyn', 'dyn', 'dynion', 'dŵr', 'ei', 'er', 'gaeaf',
        'gall', 'gan', 'ganwyd', 'gellir', 'ger', 'glas', 'glo', 'gol', 'golau', 'gorau', 'gwyn', 'haf', 'hanner',
        'hen', 'hir', 'holl', 'hun', 'hwn', 'hwy', 'hyd', 'hyn', 'hŷn', 'i', 'in', 'ledled', 'mab', 'mae', 'maen',
        'maent', 'mai', 'man', 'marw', 'math', 'mawr', 'menywod', 'miliwn', 'modd', 'mwy', 'môr', 'na', 'nad', 'nag',
        'naw', 'nes', 'ni', 'nodedig', 'nodi', 'nos', 'o', 'oed', 'oes', 'of', 'ras', 'rôl', 'the', 'wedi', 'wyneb',
        'wyth', 'ym', 'ymuno', 'ymysg', 'yr', 'yw', 'ôl',
    ],
};

/** Irish vowels, with the fada. */
const IRISH_VOWELS = 'aeiouáéíóú';
const IRISH_CAPITAL_VOWELS = 'AEIOUÁÉÍÓÚ';

/**
 * Yomitan's Irish conditions that dictionary forms take (`v`, `n`, `adj`, `adv`, with their subconditions), as
 * Wiktionary's Irish entries name their part of speech.
 * @type {('v' | 'n' | 'adj' | 'adv')[]}
 */
const IRISH_WORD = ['v', 'n', 'adj', 'adv'];

/**
 * Irish initial mutations undone ([mutationRule]), in place of Yomitan's noun eclipsis (build.mjs
 * `replacedTransforms`): lenition (bh ch dh fh gh mh ph sh th for b c d f g m p s t, and ts for s after the article:
 * an bhean, an tsráid), eclipsis (mb gc nd bhf ng bp dt for b c d f g p t, n- before a vowel: i nGaillimh, ár
 * n-athair), t before a vowel (an t-uisce) and h before one (na hÉireann). Before a capital the mutation is written
 * in lowercase (nGaillimh, bhFrainc, tSín, nAlbain, tAontas, hÉireann), and the capital stays. Each rule gives every
 * part of speech, so the radical matches entries of any (glue.js `untypedRules` for entries without one).
 * Wiktionary's own mutation rows show under the same names (`LanguageSupport.formTagRules`), which name ts for s
 * lenition as well. [unmutated] lists common words that are no mutated forms although the rules read them so,
 * lowercase. [replaces] names Yomitan's file of the rules these replace and its git object id when they were reviewed.
 * @type {import('language-transformer').LanguageTransformDescriptor<any> & {unmutated: string[], replaces: {file: string, blob: string}}}
 */
export const irishMutations = {
    language: 'ga',
    replaces: {file: 'ext/js/language/ga/irish-transforms.js', blob: '8a93988656aeb7beb057ca6c495981e8b46f1516'},
    conditions: {},
    transforms: {
        lenition: {
            name: 'lenition',
            description: 'lenition: bh ch dh fh gh mh ph sh th for b c d f g m p s t, ts for s after the article (an bhean from bean, an tsráid from sráid)',
            rules: [
                ...mutationRules('bh', 'b', IRISH_WORD, {not: '[fF]'}),
                ...mutationRules('ch', 'c', IRISH_WORD),
                ...mutationRules('dh', 'd', IRISH_WORD),
                ...mutationRules('fh', 'f', IRISH_WORD),
                ...mutationRules('gh', 'g', IRISH_WORD),
                ...mutationRules('mh', 'm', IRISH_WORD),
                ...mutationRules('ph', 'p', IRISH_WORD),
                ...mutationRules('sh', 's', IRISH_WORD),
                ...mutationRules('th', 't', IRISH_WORD),
                mutationRule('t', '', IRISH_WORD, {before: '[sS]'}),
            ],
        },
        eclipsis: {
            name: 'eclipsis',
            description: 'eclipsis: mb gc nd bhf ng bp dt for b c d f g p t, n- before a vowel (i nGaillimh from Gaillimh, ár n-athair from athair)',
            rules: [
                ...mutationRules('mb', 'b', IRISH_WORD),
                ...mutationRules('gc', 'c', IRISH_WORD),
                ...mutationRules('nd', 'd', IRISH_WORD),
                ...mutationRules('bhf', 'f', IRISH_WORD),
                ...mutationRules('ng', 'g', IRISH_WORD),
                ...mutationRules('bp', 'p', IRISH_WORD),
                ...mutationRules('dt', 't', IRISH_WORD),
                mutationRule('m', '', IRISH_WORD, {before: 'B'}),
                mutationRule('g', '', IRISH_WORD, {before: 'C'}),
                mutationRule('n', '', IRISH_WORD, {before: `[DG${IRISH_CAPITAL_VOWELS}]`}),
                mutationRule('bh', '', IRISH_WORD, {before: 'F'}),
                mutationRule('b', '', IRISH_WORD, {before: 'P'}),
                mutationRule('d', '', IRISH_WORD, {before: 'T'}),
                mutationRule('n-', '', IRISH_WORD, {before: `[${IRISH_VOWELS}${IRISH_CAPITAL_VOWELS}]`}),
            ],
        },
        't-prothesis': {
            name: 't-prothesis',
            description: 't before a vowel (an t-uisce from uisce, an tAontas from Aontas)',
            rules: [
                mutationRule('t-', '', IRISH_WORD, {before: `[${IRISH_VOWELS}]`}),
                mutationRule('t', '', IRISH_WORD, {before: `[${IRISH_CAPITAL_VOWELS}]`}),
            ],
        },
        'h-prothesis': {
            name: 'h-prothesis',
            description: 'h before a vowel (na hÉireann from Éireann, a hathair from athair)',
            rules: [
                mutationRule('h', '', IRISH_WORD, {before: `[${IRISH_VOWELS}${IRISH_CAPITAL_VOWELS}]`}),
            ],
        },
    },
    // From mutation-exceptions.mjs on Leipzig's Irish news sample (the 1,000 commonest words), reviewed by hand with
    // the words before each: words in their own right whose radical is another word (chun "towards", not cun; thar
    // "over", not tar "come"; cheana, chomh, thart, thíos, chuile "every", chuaigh "went"), bhfuil, whose own entry
    // leads to bí as the rule's fuil "blood" does not, and English the. The rest are right as mutations (bhí → bí, i
    // ndiaidh → diaidh, na hÉireann → Éireann).
    unmutated: ['bhfuil', 'chomh', 'cheana', 'chuaigh', 'chuile', 'chun', 'thar', 'thart', 'the', 'thíos'],
};

/** Scottish Gaelic vowels, with the grave accent and the acute of the older spelling. */
const GAELIC_VOWELS = 'aeiouàèìòùáéíóú';
const GAELIC_CAPITAL_VOWELS = 'AEIOUÀÈÌÒÙÁÉÍÓÚ';

/** @typedef {'w'} GaelicCondition */

/** @type {GaelicCondition[]} */
const GAELIC_WORD = ['w'];

/**
 * Scottish Gaelic initial mutations undone ([mutationRule]): lenition (bh ch dh fh gh mh ph sh th for b c d f g m p s
 * t, and t-s for s after the article: a' ghrian, an t-sràid), n- before a vowel (ar n-eilean), t- before one (an
 * t-uisge) and h- before one (na h-Alba). The prefixes keep their hyphen before a capital too (an t-Eilean, na
 * h-Alba). Wiktionary's Gaelic entries give no part of speech the rules could match, and list mutated forms as
 * form-of entries under tags of their own, which the engine names after these rules (`LanguageSupport.formTagRules`):
 * Wiktionary names t-s lenition and n- eclipsis, so the rules do too. [unmutated] lists common words that are no
 * mutated forms although the rules read them so, lowercase.
 * @type {import('language-transformer').LanguageTransformDescriptor<GaelicCondition> & {unmutated: string[]}}
 */
export const gaelicMutations = {
    language: 'gd',
    conditions: {
        w: {name: 'Word', isDictionaryForm: true},
    },
    transforms: {
        lenition: {
            name: 'lenition',
            description: 'lenition: bh ch dh fh gh mh ph sh th for b c d f g m p s t, t-s for s after the article (a\' ghrian from grian, an t-sràid from sràid)',
            rules: [
                ...mutationRules('bh', 'b', GAELIC_WORD),
                ...mutationRules('ch', 'c', GAELIC_WORD),
                ...mutationRules('dh', 'd', GAELIC_WORD),
                ...mutationRules('fh', 'f', GAELIC_WORD),
                ...mutationRules('gh', 'g', GAELIC_WORD),
                ...mutationRules('mh', 'm', GAELIC_WORD),
                ...mutationRules('ph', 'p', GAELIC_WORD),
                ...mutationRules('sh', 's', GAELIC_WORD),
                ...mutationRules('th', 't', GAELIC_WORD),
                mutationRule('t-', '', GAELIC_WORD, {before: '[sS]'}),
            ],
        },
        eclipsis: {
            name: 'eclipsis',
            description: 'n- before a vowel (ar n-eilean from eilean)',
            rules: [
                mutationRule('n-', '', GAELIC_WORD, {before: `[${GAELIC_VOWELS}${GAELIC_CAPITAL_VOWELS}]`}),
            ],
        },
        't-prothesis': {
            name: 't-prothesis',
            description: 't- before a vowel (an t-uisge from uisge, an t-Eilean from Eilean)',
            rules: [
                mutationRule('t-', '', GAELIC_WORD, {before: `[${GAELIC_VOWELS}${GAELIC_CAPITAL_VOWELS}]`}),
            ],
        },
        'h-prothesis': {
            name: 'h-prothesis',
            description: 'h- before a vowel (na h-Alba from Alba, na h-òrain from òrain)',
            rules: [
                mutationRule('h-', '', GAELIC_WORD, {before: `[${GAELIC_VOWELS}${GAELIC_CAPITAL_VOWELS}]`}),
            ],
        },
    },
    // From mutation-exceptions.mjs on the Gaelic Wikipedia dump of 2026-10-01 (the 1,000 commonest words), reviewed by
    // hand with the words before each: words in their own right whose radical is another word (bhathar "was", not
    // bathar "goods"; bheir "will give", not beir "catch"; dhuibh "to you", not duibh "black"; shuas "up", the place,
    // not suas, the motion), chan "not" and dha "to him", and English the. The rest are right as mutations or lead
    // back to the word (thug → tug → thoir, dheth → deth → dheth).
    unmutated: ['bhathar', 'bheir', 'chan', 'dha', 'dhuibh', 'shuas', 'the'],
};

/**
 * [own] in place of Yomitan's descriptor of a language, with Yomitan's conditions, which [own]'s rules give; Yomitan's
 * rules are dropped.
 * @template {string} T
 * @template {import('language-transformer').LanguageTransformDescriptor<any>} D
 * @param {import('language-transformer').LanguageTransformDescriptor<T>} base
 * @param {D} own
 * @returns {D}
 */
export function withReplacedTransforms(base, own) {
    if (base.language !== own.language) throw new Error(`${own.language}: replaces ${base.language}'s rules`);
    return {...own, conditions: {...base.conditions, ...own.conditions}};
}

/**
 * Yomitan's rules of a language with the app's own added: [own]'s rules after [base]'s, and its conditions.
 * @template {string} T
 * @param {import('language-transformer').LanguageTransformDescriptor<T>} base
 * @param {import('language-transformer').LanguageTransformDescriptor<any>} own
 * @returns {import('language-transformer').LanguageTransformDescriptor<T>}
 */
export function withOwnTransforms(base, own) {
    for (const id of Object.keys(own.transforms)) {
        if (id in base.transforms) throw new Error(`${base.language}: Yomitan has a rule ${id} now`);
    }
    return {
        ...base,
        conditions: {...base.conditions, ...own.conditions},
        transforms: {...base.transforms, ...own.transforms},
    };
}
