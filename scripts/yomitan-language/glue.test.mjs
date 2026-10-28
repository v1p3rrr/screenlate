// Checks the built language bundles (dictionary/engine-hoshidicts/src/main/assets/yomitan-language) the way the
// engine calls them: node --test scripts/yomitan-language
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';
import {
    albanianEndings, azerbaijaniTransforms, estonianTransforms, finnishTransforms, gaelicMutations, hebrewTransforms, hungarianTransforms,
    irishMutations, kazakhTransforms, kyrgyzTransforms, latinEnclitics, persianTransforms, swahiliTransforms,
    uzbekTransforms, welshTransforms, withOwnTransforms, withReplacedTransforms,
} from './own-transforms.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const assets = path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/assets/yomitan-language');

/** @param {string} language */
function load(language) {
    const context = vm.createContext({});
    vm.runInContext(fs.readFileSync(path.join(assets, `${language}.js`), 'utf8'), context);
    return context.yomitanLang;
}

/**
 * The texts the engine would query for [text].
 * @param {string} language
 * @param {string} text
 */
function queried(language, text) {
    const bundle = load(language);
    return new Set(bundle.candidates(text, language, 'word').map((c) => c.d));
}

test('turkish capitals lower to their own letters', () => {
    const texts = queried('tr', 'IŞIK');
    assert.ok(texts.has('ışık'));
    assert.ok(!texts.has('işik'));
    assert.ok(queried('tr', 'İSTANBUL').has('istanbul'));
    assert.ok(queried('tr', 'istanbul').has('İstanbul'));
    assert.ok(queried('tr', 'ılık').has('Ilık'));
});

test('other languages keep the default case pairs', () => {
    assert.ok(queried('en', 'IRIS').has('iris'));
    assert.ok(queried('de', 'insel').has('Insel'));
});

/**
 * The candidates the engine would query for [text], with the untyped flag.
 * @param {string} language
 * @param {string} text
 */
function candidates(language, text) {
    return load(language).candidates(text, language, 'word');
}

test('russian stress marks drop and yo matches ye only in russian', () => {
    const stressed = 'ко\u0301шка';
    assert.ok(queried('ru', stressed).has('кошка'));
    assert.ok(queried('ru', 'ёж').has('еж'));
    assert.ok(queried('ru', 'еж').has('ёж'));
    for (const language of ['en', 'de', 'pl', 'lv', 'nl', 'cs', 'id', 'ms', 'tl', 'sv', 'no', 'da', 'ro', 'lt', 'sk', 'sl']) {
        // Indonesian, Malay, Tagalog and Romanian drop every combining mark, as Yomitan's processors for them do.
        if (!['id', 'ms', 'tl', 'ro'].includes(language)) assert.ok(!queried(language, stressed).has('кошка'), language);
        assert.ok(!queried(language, 'еж').has('ёж'), language);
    }
});

test('swedish lowers capitals and keeps the marks of its letters', () => {
    assert.ok(queried('sv', 'Läsa').has('läsa'));
    assert.ok(queried('sv', 'ÅR').has('år'));
    // ä and å are letters of their own: neither is looked up without its mark.
    assert.ok(!queried('sv', 'läsa').has('lasa'));
    assert.ok(!queried('sv', 'år').has('ar'));
});

test('lithuanian takes the latvian capitalization under its own code and keeps the marks of its letters', () => {
    assert.ok(queried('lt', 'Žmogus').has('žmogus'));
    assert.ok(queried('lt', 'ŽMOGUS').has('žmogus'));
    // Words without the letters' marks are found through keys made at import, not by the bundle.
    assert.ok(!queried('lt', 'žmogus').has('zmogus'));
    assert.ok(!queried('lt', 'zmogus').has('žmogus'));
});

test('slovak takes the czech capitalization under its own code and keeps the marks of its letters', () => {
    assert.ok(queried('sk', 'Ďakujem').has('ďakujem'));
    assert.ok(queried('sk', 'ŽRAŤ').has('žrať'));
    // Words without the letters' marks are found through keys made at import, not by the bundle.
    assert.ok(!queried('sk', 'robiť').has('robit'));
    assert.ok(!queried('sk', 'robit').has('robiť'));
});

test('slovenian takes the czech capitalization under its own code and keeps the marks of its letters', () => {
    assert.ok(queried('sl', 'Žena').has('žena'));
    assert.ok(queried('sl', 'ŽENA').has('žena'));
    // Words without the caron and forms without the tonemic marks are found through keys made at import, not by the
    // bundle.
    assert.ok(!queried('sl', 'žena').has('zena'));
    assert.ok(!queried('sl', 'zena').has('žena'));
    assert.ok(!queried('sl', 'vôde').has('vode'));
});

test('malay takes the indonesian text processors under its own code', () => {
    for (const language of ['id', 'ms']) {
        assert.ok(queried(language, 'Baca').has('baca'), language);
        assert.ok(queried(language, 'bacá').has('baca'), language);
    }
    assert.ok(!queried('en', 'bacá').has('baca'));
});

test('tagalog affixes and the linker ng are undone only in tagalog', () => {
    // Yomitan has no infix rules (kumain → kain); the dictionaries' form-of entries cover those.
    assert.ok(queried('tl', 'kainin').has('kain'));
    assert.ok(queried('tl', 'magluto').has('luto'));
    assert.ok(queried('tl', 'ipagluto').has('luto'));
    assert.ok(queried('tl', 'magandang').has('maganda'));
    assert.ok(queried('tl', 'pag-asa').has('pagasa'));
    assert.ok(!queried('id', 'kainin').has('kain'));
});

test('serbo-croatian text without diacritics finds the letters and accent marks drop, only in serbo-croatian', () => {
    // Croatian and Serbian read the one bundle under the dictionaries' code.
    assert.ok(queried('sh', 'kuca').has('kuća'));
    assert.ok(queried('sh', 'sesir').has('šešir'));
    assert.ok(queried('sh', 'djak').has('đak'));
    assert.ok(queried('sh', 'ku\u030F\u0107a').has('kuća'));
    assert.ok(queried('sh', 'ку\u030Fћа').has('кућа'));
    assert.ok(queried('sh', 'Ку\u0301ћа').has('кућа'));
    assert.ok(queried('sh', 'КУЋА').has('кућа'));
    assert.ok(!queried('cs', 'kuca').has('kuća'));
    assert.ok(!queried('ru', 'ку\u030Fћа').has('кућа'));
});

test('a text with more variants than yomitan keeps is looked up without a console, as in quickjs', () => {
    // Node's contexts have a console; QuickJS has none, and Yomitan warns through it when it cuts the variants.
    const context = vm.createContext({console: undefined});
    vm.runInContext(fs.readFileSync(path.join(assets, 'sh.js'), 'utf8'), context);
    const found = context.yomitanLang.candidates('cecescecescecesc', 'sh', 'word');
    assert.ok(found.length > 0);
});

test('vietnamese tone marks match in the old and the new placement only in vietnamese', () => {
    const old = 'ho\u00e0';
    const current = 'h\u00f2a';
    assert.ok(queried('vi', old).has(current));
    assert.ok(queried('vi', current).has(old));
    assert.ok(queried('vi', 'hoa\u0300').has(current));
    for (const language of ['fr', 'es', 'pt', 'it']) {
        assert.ok(!queried(language, old).has(current), language);
    }
});

test('a vietnamese phrase with a tone mark typed apart keeps the text as written for its match', () => {
    const written = 'hoa\u0300 b\u00ecnh';
    const found = candidates('vi', written + '.').filter((c) => c.d === 'ho\u00e0 b\u00ecnh');
    assert.ok(found.length > 0);
    // The engine reports the match in the text it was given, so the app maps it back over the mark.
    assert.ok(found.every((c) => c.o === written));
});

test('english phrasal verbs are untyped candidates only in english', () => {
    const picked = candidates('en', 'picked up');
    assert.ok(picked.some((c) => c.d === 'pick up' && c.u));
    assert.ok(!candidates('en', 'closed to').some((c) => c.u));
    for (const language of ['de', 'fr', 'es']) {
        assert.ok(!candidates(language, 'picked up').some((c) => c.u), language);
    }
});

test('a kangxi radical reads as its ideograph in chinese', () => {
    assert.ok(queried('zh', '\u2f47本').has('日本'));
});

test('turkish case pairs do not reach english', () => {
    assert.ok(!queried('en', 'IŞIK').has('ışık'));
    assert.ok(queried('en', 'IŞIK').has('işik'));
});

test('ukrainian deinflects, drops stress marks and matches every apostrophe', () => {
    assert.ok(queried('uk', 'книги').has('книга'));
    assert.ok(queried('uk', 'зробив').has('зробити'));
    assert.ok(queried('uk', 'чита\u0301ю').has('читаю'));
    for (const apostrophe of ["'", '’', 'ʼ']) assert.ok(queried('uk', 'п’ять').has(`п${apostrophe}ять`), apostrophe);
});

test('belarusian takes the ukrainian stress and apostrophe processors, russian does not', () => {
    assert.ok(queried('be', 'ва\u0301да').has('вада'));
    assert.ok(queried('be', 'сям’я').has("сям'я"));
    assert.ok(queried('be', "сям'я").has('сямʼя'));
    assert.ok(!queried('ru', 'п’ять').has("п'ять"));
});

test('greek capitals lower with a final sigma, a second accent drops and xana- is undone', () => {
    assert.ok(queried('el', 'ΣΚΥΛΟΣ').has('σκυλος'));
    assert.ok(queried('el', 'πρόσωπό').has('πρόσωπο'));
    const xana = queried('el', 'ξαναρώτησε');
    assert.ok(xana.has('ρώτησε'));
    // Yomitan's other ξανα- rule drops the accents, for forms written apart in no dictionary.
    assert.ok(xana.has('ρωτησε'));
    // The tonos stays: a word written with it is matched as written.
    assert.ok(!queried('el', 'πότε').has('ποτε'));
});

test('hindi text is composed as headwords are and candrabindu reads as anusvara, only in hindi', () => {
    // NFC writes क़ (U+0958) as क + nukta, as dictionaries do.
    const precomposed = '\u0958लम';
    const composed = '\u0915\u093Cलम';
    assert.ok(queried('hi', precomposed).has(composed));
    assert.ok(queried('hi', 'हैँ').has('हैं'));
    // The import keys candrabindu headwords under anusvara; the text is not respelled the other way.
    assert.ok(!queried('hi', 'हैं').has('हैँ'));
    // The accent processors of Greek, Indonesian and others decompose and compose the text as well.
    for (const language of ['en', 'ru', 'de', 'pl']) {
        assert.ok(!queried(language, precomposed).has(composed), language);
        assert.ok(!queried(language, 'हैँ').has('हैं'), language);
    }
});

test('bengali noun endings are undone once, in composed text, and match entries without a part of speech', () => {
    const book = candidates('bn', 'বইটির').find((c) => c.d === 'বই');
    assert.ok(book);
    assert.ok(book.u);
    // The bundle's arrays come from another context, so they are compared as JSON.
    assert.equal(JSON.stringify(book.r), JSON.stringify([['-টির']]));
    // ো typed as ে + া, and য় as one character, are composed as the headwords are.
    assert.ok(queried('bn', 'বইগুল\u09C7\u09BE').has('বই'));
    assert.ok(queried('bn', 'মা\u09DFের').has('মা'));
    assert.ok(queried('bn', 'ঘরে').has('ঘর'));
    // One ending only: ছেলেদেরকে is not cut down to ছেলে.
    const children = queried('bn', 'ছেলেদেরকে');
    assert.ok(children.has('ছেলেদের'));
    assert.ok(!children.has('ছেলে'));
    // Two letters stay before an ending: ভয় is not cut down to ভ.
    assert.ok(!queried('bn', 'ভয়').has('ভ'));
    // The text itself stays typed.
    assert.ok(!candidates('bn', 'বইটির').find((c) => c.d === 'বইটির').u);
});

test('bengali endings and untyped matches stay out of hindi', () => {
    assert.ok(!queried('hi', 'বইটির').has('বই'));
    assert.ok(candidates('hi', 'पढ़ने').every((c) => !c.u));
});

test('georgian rules load, chain and match entries without a part of speech when three letters stay', () => {
    // Yomitan files its Georgian rules under kat, not ka.
    const book = candidates('ka', 'წიგნს').find((c) => c.d === 'წიგნი');
    assert.ok(book);
    assert.ok(book.u);
    assert.equal(JSON.stringify(book.r), JSON.stringify([['nounAdjStemCompletion', 'nounAdjSuffixStripping']]));
    assert.ok(candidates('ka', 'სახლში').find((c) => c.d === 'სახლ').u);
    // მას (him) is not cut down to an untyped მა, a word of its own.
    const short = candidates('ka', 'მას').find((c) => c.d === 'მა');
    assert.ok(short);
    assert.ok(!short.u);
    // -მა stripped from მა leaves nothing, which is not queried.
    assert.ok(!queried('ka', 'მას').has(''));
    assert.ok(!candidates('ka', 'წიგნს').find((c) => c.d === 'წიგნს').u);
});

test('georgian headings in mtavruli capitals are looked up in the ordinary letters', () => {
    assert.ok(queried('ka', 'ᲡᲐᲥᲐᲠᲗᲕᲔᲚᲝ').has('საქართველო'));
});

test('georgian rules stay out of other languages', () => {
    for (const language of ['en', 'ru', 'bn']) {
        assert.ok(!queried(language, 'წიგნს').has('წიგნი'), language);
    }
});

test('armenian marks on a stressed vowel drop and the ligature ev is tried for ev and the classical spelling', () => {
    assert.ok(queried('hy', 'ինչո՞ւ').has('ինչու'));
    assert.ok(queried('hy', 'կարդա՛').has('կարդա'));
    // Capitals have no ligature: ԵՐԵՎԱՆ finds Երևան.
    assert.ok(queried('hy', 'ԵՐԵՎԱՆ').has('Երևան'));
    assert.ok(queried('hy', 'եւ').has('և'));
    // The text as written stays, for words written with եվ.
    assert.ok(queried('hy', 'Եվրոպա').has('Եվրոպա'));
});

test('armenian marks and the ligature stay out of other languages', () => {
    for (const language of ['en', 'ru', 'ka']) {
        assert.ok(!queried(language, 'ինչո՞ւ').has('ինչու'), language);
        assert.ok(!queried(language, 'երեվան').has('երևան'), language);
    }
});

/**
 * The texts the engine would query for Thai [text], which it looks up letter by letter; [fromOcr]: recognized in a
 * screenshot.
 * @param {string} text
 * @param {boolean} [fromOcr]
 */
function queriedThai(text, fromOcr = false) {
    return new Set(load('th').candidates(text, 'th', 'letter', fromOcr).map((c) => c.d));
}

test('thai sara am in two parts is tried as one in recognized text only', () => {
    // น + nikhahit + mai tho + sara aa, as Tesseract gives น้ำ.
    assert.ok(queriedThai('น\u0E4D\u0E49\u0E32', true).has('น้ำ'));
    assert.ok(queriedThai('ท\u0E4D\u0E32', true).has('ทำ'));
    assert.ok(queriedThai('ท\u0E4D\u0E32', true).has('ท\u0E4D\u0E32'));
    // App text and typed text are looked up as written.
    assert.ok(!queriedThai('น\u0E4D\u0E49\u0E32').has('น้ำ'));
    assert.ok(!queriedThai('ท\u0E4D\u0E32').has('ทำ'));
});

test('thai tone mark before its vowel is tried as headwords write it in any text', () => {
    for (const fromOcr of [false, true]) {
        // ก + mai ek + sara ii, typed for กี่.
        assert.ok(queriedThai('ก\u0E48\u0E35', fromOcr).has('กี่'));
        assert.ok(queriedThai('สิทธ\u0E4C\u0E34', fromOcr).has('สิทธิ์'));
        assert.ok(queriedThai('กี่', fromOcr).has('กี่'));
    }
    // With sara am in two parts in the same text, recognized: both.
    assert.ok(queriedThai('ท\u0E4D\u0E32ก\u0E48\u0E35', true).has('ทำกี่'));
});

test('thai mai yamok is tried with and without the spaces around it', () => {
    assert.ok(queriedThai('เด็กๆ').has('เด็ก ๆ'));
    assert.ok(queriedThai('งูๆปลาๆ').has('งู ๆ ปลา ๆ'));
    assert.ok(queriedThai('ช้า ๆ').has('ช้าๆ'));
    assert.ok(queriedThai('ช้า ๆ').has('ช้า ๆ'));
    // A space after ๆ stays out of the match: the shorter text เด็กๆ is the one that finds เด็ก ๆ.
    const spaced = load('th').candidates('เด็กๆ ชอบ', 'th', 'letter').filter((c) => c.d === 'เด็ก ๆ');
    assert.deepEqual([...new Set(spaced.map((c) => c.o))], ['เด็กๆ']);
    assert.ok(queriedThai('เด็กๆชอบ').has('เด็ก ๆ ชอบ'));
});

test('thai processors stay out of other languages', () => {
    for (const language of ['en', 'vi', 'hi']) {
        assert.ok(!queriedFromOcr(language, 'ท\u0E4D\u0E32').has('ทำ'), language);
        assert.ok(!queried(language, 'ก\u0E48\u0E35').has('กี่'), language);
        assert.ok(!queried(language, 'เด็กๆ').has('เด็ก ๆ'), language);
    }
});

/**
 * The texts the engine would query for Khmer [text], which it looks up letter by letter.
 * @param {string} text
 */
function queriedKhmer(text) {
    return new Set(load('km').candidates(text, 'km', 'letter').map((c) => c.d));
}

test('khmer zero-width spaces between words are dropped, one that ends the text stays out of the match', () => {
    assert.ok(queriedKhmer('ខ្ញុំ\u200Bទៅ').has('ខ្ញុំទៅ'));
    assert.ok(queriedKhmer('សាលា\u200Bរៀន').has('សាលារៀន'));
    const matches = load('km').candidates('ខ្ញុំ\u200Bទៅ', 'km', 'letter').filter((c) => c.d === 'ខ្ញុំ');
    assert.deepEqual([...new Set(matches.map((c) => c.o))], ['ខ្ញុំ']);
});

test('khmer subscript TA is tried as subscript DA', () => {
    assert.ok(queriedKhmer('ក្តារ').has('ក្ដារ'));
    assert.ok(queriedKhmer('សួស្តី').has('សួស្ដី'));
    // Text with DA finds headwords with TA through the keys the import adds, not through the processor.
    assert.ok(!queriedKhmer('ក្ដារ').has('ក្តារ'));
});

test('khmer processors stay out of other languages', () => {
    for (const language of ['en', 'th', 'hi']) {
        assert.ok(!queried(language, 'ក្តារ').has('ក្ដារ'), language);
        assert.ok(!queried(language, 'ខ្ញុំ\u200Bទៅ').has('ខ្ញុំទៅ'), language);
    }
});

test('arabic prefixes, the article and endings are undone; noun rules match entries without a part of speech', () => {
    const book = candidates('ar', 'والكتاب').find((c) => c.d === 'كتاب');
    assert.ok(book);
    assert.ok(book.u);
    assert.ok(queried('ar', 'مدرسة').has('مدرس'));
    // Verb rules stay typed: سيكتبون (they will write) is كتب only as a verb.
    const write = candidates('ar', 'سيكتبون').filter((c) => c.d === 'كتب');
    assert.ok(write.length > 0);
    assert.ok(write.every((c) => !c.u));
    // Two letters stay before a prefix: وفي finds في, untyped; nothing shorter is untyped or empty.
    assert.ok(candidates('ar', 'وفي').find((c) => c.d === 'في').u);
    assert.ok(candidates('ar', 'وب').every((c) => !c.u || [...c.d].length >= 2));
    assert.ok(!queried('ar', 'فى').has(''));
    assert.ok(!candidates('ar', 'والكتاب').find((c) => c.d === 'والكتاب').u);
});

test('a final alif maqsura and taa marbuta are tried as ya and ha; hamza is left to the import keys', () => {
    assert.ok(queried('ar', 'فى').has('في'));
    assert.ok(queried('ar', 'مدرسة').has('مدرسه'));
    assert.ok(queried('ar', 'مستشفى كبير').has('مستشفي كبير'));
    // A bare alif finds headwords with hamza through the keys the import adds, not through Yomitan's processors.
    assert.ok(!queried('ar', 'اكبر').has('أكبر'));
    assert.ok(!queried('ar', 'اسلام').has('إسلام'));
    assert.ok(queried('ar', 'أكبر').has('أكبر'));
});

test('arabic text with persian kaf and yeh is tried with the arabic letters', () => {
    assert.ok(queried('ar', 'کتاب').has('كتاب'));
    assert.ok(queried('ar', 'والکتاب').has('كتاب'));
    assert.ok(queried('ar', 'فی').has('في'));
    // A final ی is ي; headwords with ى are keyed under ي at import.
    assert.ok(queried('ar', 'علی').has('علي'));
    assert.equal(first('ar', 'کبیر'), 'كبير');
});

test('arabic rules and processors stay out of other languages', () => {
    for (const language of ['en', 'ru', 'tr']) {
        assert.ok(!queried(language, 'فى').has('في'), language);
        assert.ok(!queried(language, 'مدرسة').has('مدرسه'), language);
        assert.ok(!queried(language, 'والكتاب').has('كتاب'), language);
    }
    for (const language of ['fa', 'en', 'ru', 'tr']) {
        assert.ok(!queried(language, 'کتاب').has('كتاب'), language);
        assert.ok(!queried(language, 'علی').has('علي'), language);
    }
});

/**
 * The cases of Yomitan's own Arabic transform test that should be found (`valid`), from a checkout at the bundles'
 * commit named by the environment variable YOMITAN.
 */
function yomitanArabicCases() {
    const file = path.join(process.env.YOMITAN, 'test/language/arabic-transforms.test.js');
    const source = fs.readFileSync(file, 'utf8');
    const cases = [];
    for (const category of source.split(/\n {4}\{\n {8}category: /).slice(1)) {
        if (!/^\S[^\n]*\n {8}valid: true,/.test(category)) continue;
        for (const match of category.matchAll(/\{term: '([^']+)', source: '([^']+)', rule: '([^']+)'/g)) {
            cases.push({term: match[1], source: match[2]});
        }
    }
    return cases;
}

test('yomitan\'s own arabic cases are found through the bundle', {skip: !process.env.YOMITAN && 'YOMITAN is not set'}, () => {
    const cases = yomitanArabicCases();
    assert.ok(cases.length > 250, `${cases.length} cases`);
    const bundle = load('ar');
    const missed = cases.filter(({term, source}) => !bundle.candidates(source, 'ar', 'word').some((c) => c.d === term));
    assert.deepEqual(missed, []);
});

/**
 * The candidate the engine shows first for [text]: the longest match, then the fewest processor steps, then the
 * shortest chain of rules (YomitanSorter's order before frequencies).
 * @param {string} language
 * @param {string} text
 */
function first(language, text) {
    const chain = (c) => Math.max(0, ...c.r.map((r) => r.length));
    return [...candidates(language, text)]
        .sort((a, b) => [...b.o].length - [...a.o].length || a.s - b.s || chain(a) - chain(b))[0].d;
}

test('persian endings and verb prefixes are undone and chained; the results match entries without a part of speech', () => {
    // An enclitic first, then a plural: کتابهایشان (their books) is کتاب through -یشان and -ها.
    const book = candidates('fa', 'کتاب\u200Cهایشان').find((c) => c.d === 'کتاب');
    assert.ok(book);
    assert.ok(book.u);
    assert.equal(JSON.stringify(book.r), JSON.stringify([['-ها', '-یشان']]));
    assert.ok(queried('fa', 'درختان').has('درخت'));
    assert.ok(queried('fa', 'ستارگان').has('ستاره'));
    assert.ok(queried('fa', 'بزرگترین').has('بزرگ'));
    assert.ok(queried('fa', 'نمی\u200Cدانم').has('دانم'));
    assert.ok(queried('fa', 'بروم').has('روم'));
    // Two letters stay, and three after ب and ن: نان is not cut to ان or ن, nor ام to ا.
    assert.deepEqual([...queried('fa', 'نان')], ['نان']);
    assert.deepEqual([...queried('fa', 'ام')], ['ام']);
    // An enclitic comes off before a plural, not after it.
    assert.ok(!queried('fa', 'کتابیها').has('کتاب'));
    // The text itself stays typed.
    assert.ok(!candidates('fa', 'درختان').find((c) => c.d === 'درختان').u);
});

test('the spoken persian plural possessives and the ordinal in -مین after a number are undone, other spoken endings not', () => {
    assert.ok(queried('fa', 'کتابمون').has('کتاب'));
    assert.ok(queried('fa', 'بهتون').has('به'));
    assert.equal(JSON.stringify(candidates('fa', 'خودشون').find((c) => c.d === 'خود').r), JSON.stringify([['-شون']]));
    // The ordinal in -مین leaves the one in -م that Wiktionary lists, and only after a number: مسلمین and تامین stay.
    assert.ok(queried('fa', 'دومین').has('دوم'));
    assert.ok(queried('fa', 'سیزدهمین').has('سیزدهم'));
    assert.ok(queried('fa', 'دویستمین').has('دویستم'));
    assert.ok(!queried('fa', 'مسلمین').has('مسلم'));
    assert.ok(!queried('fa', 'تامین').has('تام'));
    // The spoken copula, object marker, plural and -ین mislead more often than they help (language-rules.md).
    assert.ok(!queried('fa', 'خوبه').has('خوب'));
    assert.ok(!queried('fa', 'اینو').has('این'));
    assert.ok(!queried('fa', 'دخترا').has('دختر'));
    assert.ok(!queried('fa', 'دارین').has('دار'));
});

test('every persian rule has a name and a description of its own, which the app strings are generated from', () => {
    const strings = fs.readFileSync(
        path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/res/values/inflections_fa.xml'), 'utf8');
    const rules = Object.entries(persianTransforms.transforms);
    assert.equal(rules.length, 36);
    for (const [id, {name, description}] of rules) {
        // The id (-ها) stays what the engine reports and Anki's {conjugation} shows; the name is what the popup shows.
        assert.notEqual(name, id, id);
        assert.ok(description.includes('('), id);
        assert.ok(strings.includes(`<!-- ${id} -->`), id);
        assert.ok(strings.includes(`>${name}</string>`) && strings.includes(`>${description}</string>`), id);
    }
});

test('persian text drops the zero-width non-joiner and writes arabic letters as persian ones', () => {
    assert.ok(queried('fa', 'می\u200Cروم').has('میروم'));
    assert.ok(queried('fa', 'كتاب').has('کتاب'));
    assert.ok(queried('fa', 'يك').has('یک'));
    assert.ok(queried('fa', 'مى').has('می'));
    assert.ok(queried('fa', 'خانۀ').has('خانه'));
    // The word itself comes first, without the non-joiner and in persian letters.
    assert.equal(first('fa', 'می\u200Cشود'), 'میشود');
    assert.equal(first('fa', 'کتاب\u200Cها'), 'کتابها');
    assert.equal(first('fa', 'خانه\u200Cای'), 'خانهای');
    assert.equal(first('fa', 'كتاب'), 'کتاب');
    assert.equal(first('fa', 'است'), 'است');
});

test('a persian verb prefix or ending written apart is tried joined to its word', () => {
    assert.ok(queried('fa', 'می خواهم بروم').has('میخواهم'));
    assert.ok(queried('fa', 'می خواهم بروم').has('خواهم'));
    assert.ok(queried('fa', 'نمی دانم').has('نمیدانم'));
    const books = queried('fa', 'کتاب ها را');
    assert.ok(books.has('کتابها'));
    assert.ok(books.has('کتاب'));
    assert.ok(queried('fa', 'کتاب های من').has('کتابهای'));
    assert.ok(queried('fa', 'بزرگ ترین').has('بزرگ'));
    // A word ending in می, and a word that only starts like an ending, stay apart.
    assert.ok(!queried('fa', 'سمی خواهم').has('سمیخواهم'));
    assert.ok(!queried('fa', 'کتاب هوا').has('کتابهوا'));
    assert.ok(!queried('fa', 'کتاب ترس').has('کتابترس'));
});

test('persian rules and processors stay out of other languages, arabic ones out of persian', () => {
    for (const language of ['ar', 'en', 'tr']) {
        // Written joined: every language cuts a text at a non-joiner, which would leave کتاب itself.
        assert.ok(!queried(language, 'کتابهایشان').has('کتاب'), language);
        assert.ok(!queried(language, 'می خواهم').has('میخواهم'), language);
        assert.ok(!queried(language, 'كتاب').has('کتاب'), language);
        // Arabic's dual -ان gives درخت from درختان too; -گان → ه is Persian's own.
        assert.ok(!queried(language, 'ستارگان').has('ستاره'), language);
        assert.ok(!queried(language, 'میخواهم').has('خواهم'), language);
    }
    assert.ok(!queried('fa', 'والكتاب').has('کتاب'));
    assert.ok(!queried('fa', 'مدرسة').has('مدرسه'));
    assert.ok(candidates('fa', 'والكتاب').every((c) => !c.u));
});

test('hebrew prefixes come off in the order of the grammar, three letters at most, and match entries without a part of speech', () => {
    // ו first, then ה: והבית is בית through ו- and ה-, the last rule first as in every chain.
    const house = candidates('he', 'והבית').find((c) => c.d === 'בית');
    assert.ok(house);
    assert.ok(house.u);
    assert.equal(JSON.stringify(house.r), JSON.stringify([['ה-', 'ו-']]));
    assert.ok(queried('he', 'והבית').has('הבית'));
    assert.ok(queried('he', 'בבית').has('בית'));
    assert.ok(queried('he', 'ושבבית').has('בית'));
    assert.ok(queried('he', 'ומהבית').has('בית'));
    assert.ok(queried('he', 'שמהבית').has('בית'));
    assert.ok(queried('he', 'כשהגעתי').has('הגעתי'));
    assert.ok(queried('he', 'וכשהגעתי').has('הגעתי'));
    assert.ok(queried('he', 'לירושלים').has('ירושלים'));
    // Three letters at most: ושמהבית loses ו ש מ, not the article as well; וכשהגעתי keeps its ה.
    assert.ok(queried('he', 'ושמהבית').has('הבית'));
    assert.ok(!queried('he', 'ושמהבית').has('בית'));
    assert.ok(!queried('he', 'וכשהגעתי').has('געתי'));
    // Out of order: ה after ב, ו after ש, ש after מ, two of ב כ ל.
    assert.ok(!queried('he', 'בהבית').has('בית'));
    assert.ok(!queried('he', 'שוהבית').has('הבית'));
    assert.ok(!queried('he', 'משבית').has('בית'));
    assert.ok(!queried('he', 'לבבית').has('בית'));
    // A letter and one more character stay: בן, הם and לא are not cut down to a letter.
    for (const word of ['בן', 'הם', 'לא']) assert.deepEqual([...queried('he', word)], [word], word);
    // The word itself stays typed and comes first.
    assert.ok(!candidates('he', 'הבית').find((c) => c.d === 'הבית').u);
    assert.equal(first('he', 'הבית'), 'הבית');
    assert.equal(first('he', 'ולכן'), 'ולכן');
});

test('every hebrew rule has a name and a description of its own, which the app strings are generated from', () => {
    const strings = fs.readFileSync(
        path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/res/values/inflections_he.xml'), 'utf8');
    const rules = Object.entries(hebrewTransforms.transforms);
    assert.deepEqual(rules.map(([id]) => id), ['ו-', 'ש-', 'כש-', 'ב-', 'כ-', 'ל-', 'מ-', 'ה-']);
    for (const [id, {name, description}] of rules) {
        assert.notEqual(name, id, id);
        assert.ok(description.includes('('), id);
        assert.ok(strings.includes(`<!-- ${id} -->`), id);
        assert.ok(strings.includes(`>${name}</string>`) && strings.includes(`>${description}</string>`), id);
    }
});

test('hebrew vowel points and cantillation drop, the maqaf stays, ascii quotes between letters read as geresh and gershayim', () => {
    const dog = queried('he', 'כֶּלֶב');
    assert.ok(dog.has('כלב'));
    assert.equal(first('he', 'כֶּלֶב'), 'כֶּלֶב');
    assert.ok(queried('he', 'בְּבֵית').has('בית'));
    assert.ok(queried('he', 'בְּרֵאשִׁ֖ית').has('בראשית'));
    // A shin with its dot as one presentation form.
    assert.ok(queried('he', '\uFB2Aלום').has('שלום'));
    assert.ok(queried('he', 'בית־ספר').has('בית־ספר'));
    assert.ok(!queried('he', 'בית־ספר').has('ביתספר'));
    assert.ok(queried('he', 'צה"ל').has('צה״ל'));
    assert.ok(queried('he', "ג'ירפה").has('ג׳ירפה'));
    assert.ok(queried('he', 'צה״ל').has('צה״ל'));
    // A quote that is not between two letters stays.
    assert.ok(!queried('he', 'שלום"').has('שלום״'));
});

test('hebrew rules and processors stay out of other languages', () => {
    for (const language of ['ar', 'fa', 'en', 'ru']) {
        assert.ok(!queried(language, 'והבית').has('בית'), language);
        assert.ok(!queried(language, 'בבית').has('בית'), language);
        assert.ok(!queried(language, 'כֶּלֶב').has('כלב'), language);
        assert.ok(!queried(language, 'צה"ל').has('צה״ל'), language);
    }
    assert.ok(!queried('he', 'والكتاب').has('كتاب'));
    assert.ok(candidates('he', 'والكتاب').every((c) => !c.u));
    assert.ok(!queried('he', 'کتابها').has('کتاب'));
});

/**
 * The candidates of [language] for [text] that deinflect to [lemma]; there is one at least.
 * @param {string} language
 * @param {string} text
 * @param {string} lemma
 */
function candidatesTo(language, text, lemma) {
    const found = candidates(language, text).filter((c) => c.d === lemma);
    assert.ok(found.length > 0, `${text} → ${lemma}`);
    return found;
}

/**
 * Whether one of [found] is a verb form, tagged so, through [chain].
 * @param {{u: boolean, g?: string[], r: string[][]}[]} found
 * @param {string[]} chain
 */
function isVerbThrough(found, chain) {
    return found.every((c) => c.u && JSON.stringify(c.g) === JSON.stringify(['v'])) &&
        found.some((c) => c.r.some((r) => JSON.stringify(r) === JSON.stringify(chain)));
}

test('kazakh noun endings come off in the order of the grammar and match entries without a part of speech', () => {
    const [book] = candidatesTo('kk', 'кітаптарымызда', 'кітап');
    assert.ok(book.u);
    assert.equal(book.g, undefined);
    assert.deepEqual([...book.r[0]], ['plural', 'possessive 1pl', 'locative']);
    // A stem's last consonant voiced before a vowel: кітабы, аумағы.
    assert.deepEqual([...candidatesTo('kk', 'кітабы', 'кітап')[0].r[0]], ['possessive 3rd']);
    assert.ok(queried('kk', 'аумағы').has('аумақ'));
    assert.ok(queried('kk', 'баласына').has('бала'));
    assert.ok(queried('kk', 'Казакстанның').has('Казакстан'));
    assert.deepEqual([...candidatesTo('kk', 'мұғаліммін', 'мұғалім')[0].r[0]], ['1sg']);
    // -на follows a possessive's vowel only: кітап-на is no dative. балана is балаңа typed with н.
    assert.ok(!queried('kk', 'кітапна').has('кітап'));
    assert.deepEqual([...candidatesTo('kk', 'балана', 'бала')[0].r[0]], ['possessive 2sg', 'dative']);
    // -ды and -дың follow a consonant: оқыды is a past tense, not оқ with -ы and -ды; суды is су's accusative.
    assert.ok(!queried('kk', 'оқыды').has('оқ'));
    assert.ok(!queried('kk', 'оқыдың').has('оқ'));
    assert.deepEqual([...candidatesTo('kk', 'суды', 'су')[0].r[0]], ['accusative']);
    assert.deepEqual([...candidatesTo('kk', 'кітапты', 'кітап')[0].r[0]], ['accusative']);
    assert.deepEqual([...candidatesTo('kk', 'жолдың', 'жол')[0].r[0]], ['genitive']);
    // у takes a linking vowel as a consonant does: болуы, оқуым, тауы.
    assert.deepEqual([...candidatesTo('kk', 'болуы', 'болу')[0].r[0]], ['possessive 3rd']);
    assert.deepEqual([...candidatesTo('kk', 'оқуым', 'оқу')[0].r[0]], ['possessive 1sg']);
    assert.deepEqual([...candidatesTo('kk', 'тауы', 'тау')[0].r[0]], ['possessive 3rd']);
    // -нша and -ндай follow a possessive's vowel, as -нда does: пікірінше, баласындай; not кітап-нша.
    const chains = (text, lemma) => candidatesTo('kk', text, lemma).flatMap((c) => c.r.map((r) => r.join(' < ')));
    assert.ok(chains('пікірінше', 'пікір').includes('possessive 3rd < manner'));
    assert.ok(chains('баласындай', 'бала').includes('possessive 3rd < equative'));
    assert.ok(!queried('kk', 'кітапнша').has('кітап'));
    // The word as written stays typed.
    assert.ok(!candidates('kk', 'кітап').find((c) => c.d === 'кітап').u);
});

test('kazakh verb forms reach the dictionary form and match only definitions tagged as verbs', () => {
    const forms = {
        'барды': ['бару', ['past']],
        'бармады': ['бару', ['negative', 'past']],
        'барамын': ['бару', ['present', '1sg']],
        'оқиды': ['оқу', ['present', '3rd']],
        'сөйлейді': ['сөйлеу', ['present', '3rd']],
        'жаздырды': ['жазу', ['causative', 'past']],
        'айтшы': ['айту', ['please']],
        'бармас': ['бару', ['negative', 'aorist']],
        'оқитын': ['оқу', ['habitual participle']],
        'қойды': ['қою', ['past']],
        'тапты': ['табу', ['past']],
        'тауып': ['табу', ['converb']],
        'шауып': ['шабу', ['converb']],
    };
    for (const [text, [lemma, chain]] of Object.entries(forms)) {
        assert.ok(isVerbThrough(candidatesTo('kk', text, lemma), chain), text);
    }
    // A converb does not follow a predicative ending: барамын is not бара + -мын read as a converb.
    const going = candidates('kk', 'барамын').filter((c) => c.d === 'бару');
    assert.ok(going.every((c) => c.r.every((r) => !r.includes('converb'))));
});

test('turkic steps between rules are not asked for and the hidden rule leaves the chains', () => {
    const marked = (text) => [...text].some((c) => c.charCodeAt(0) >= 0xe000 && c.charCodeAt(0) <= 0xf8ff);
    for (const [language, text] of [['kk', 'бармадыңыз'], ['kk', 'жазылды'], ['ky', 'барасыңбы'], ['ky', 'келбейт']]) {
        const list = candidates(language, text);
        assert.ok(list.every((c) => !marked(c.d)), text);
        assert.ok(list.every((c) => c.r.every((r) => !r.includes('dictionary form'))), text);
        // Every text but the one as written names a dictionary form: a nominal one untyped, a verb tagged.
        assert.ok(list.every((c) => c.c === 0 || c.u), text);
    }
});

test('kazakh latin i, h and schwa inside a cyrillic word read as the kazakh letters, in kazakh only', () => {
    assert.ok(queried('kk', 'кiтап').has('кітап'));
    assert.ok(queried('kk', 'Iле').has('Іле'));
    assert.ok(queried('kk', 'шаhар').has('шаһар'));
    assert.ok(queried('kk', 'əлем').has('әлем'));
    assert.ok(queried('kk', 'кiтаптар').has('кітап'));
    // A word of Latin letters stays as it is.
    assert.deepEqual([...queried('kk', 'hit')].filter((t) => t.toLowerCase() !== 'hit'), []);
    for (const language of ['ky', 'ru', 'uk']) assert.ok(!queried(language, 'кiтап').has('кітап'), language);
});

test('kyrgyz endings in four-way harmony, the question particle and verb stems that lengthen into the dictionary form', () => {
    assert.deepEqual([...candidatesTo('ky', 'китептерибизде', 'китеп')[0].r[0]], ['plural', 'possessive 1pl', 'locative']);
    assert.ok(queried('ky', 'китеби').has('китеп'));
    assert.ok(queried('ky', 'үйлөрдө').has('үй'));
    assert.deepEqual([...candidatesTo('ky', 'китепти', 'китеп')[0].r[0]], ['accusative']);
    assert.ok(!queried('ky', 'окуду').has('ок'));
    // -нча and -ндай follow a possessive's vowel: өзүнчө, өзүндөй.
    for (const [text, rule] of [['өзүнчө', 'manner'], ['өзүндөй', 'equative']]) {
        assert.ok(candidatesTo('ky', text, 'өз').some((c) => c.r.some((r) => r.join() === `possessive 3rd,${rule}`)), text);
    }
    const forms = {
        'барасыңбы': ['баруу', ['present', '2sg', 'question']],
        'иштейт': ['иштөө', ['present', '3rd']],
        'жашады': ['жашоо', ['past']],
        'баруучу': ['баруу', ['habitual participle']],
        'көрдүм': ['көрүү', ['past', '1sg']],
        'келбейт': ['келүү', ['negative', 'present', '3rd']],
        'окуйт': ['окуу', ['present', '3rd']],
    };
    for (const [text, [lemma, chain]] of Object.entries(forms)) {
        assert.ok(isVerbThrough(candidatesTo('ky', text, lemma), chain), text);
    }
});

test('kyrgyz verbal nouns in -ыш take noun endings and reach the verb, in kyrgyz only', () => {
    const forms = {
        'болушу': ['болуу', ['verbal noun', 'possessive 3rd']],
        'калышы': ['калуу', ['verbal noun', 'possessive 3rd']],
        'кетиши': ['кетүү', ['verbal noun', 'possessive 3rd']],
        // -ш after a vowel.
        'окушу': ['окуу', ['verbal noun', 'possessive 3rd']],
        'билдиришинче': ['билдирүү', ['verbal noun', 'possessive 3rd', 'manner']],
    };
    for (const [text, [lemma, chain]] of Object.entries(forms)) {
        assert.ok(isVerbThrough(candidatesTo('ky', text, lemma), chain), text);
    }
    assert.ok(candidates('kk', 'болушу').every((c) => c.r.every((r) => !r.includes('verbal noun'))));
});

test('kazakh and kyrgyz noun endings skip the pronouns whose case forms change their stem', () => {
    // алды is алуу's past, not ал with the accusative (аны). Kazakh бул is бұл typed with у.
    for (const [language, text, pronoun] of [
        ['ky', 'алды', 'ал'], ['ky', 'Алды', 'ал'], ['ky', 'булда', 'бул'], ['ky', 'ошолду', 'ошол'],
        ['kk', 'олды', 'ол'], ['kk', 'бұлды', 'бұл'], ['kk', 'булды', 'бул'], ['kk', 'солға', 'сол'],
    ]) {
        const found = candidates(language, text).filter((c) => c.d.toLowerCase() === pronoun);
        assert.ok(found.length > 0 && found.every((c) => !c.u), text);
    }
    assert.ok(isVerbThrough(candidatesTo('ky', 'алды', 'алуу'), ['past']));
    // Pronouns with regular forms keep them: бизге, өзі.
    assert.ok(candidatesTo('ky', 'бизге', 'биз').some((c) => c.u));
    assert.ok(candidatesTo('kk', 'өзі', 'өз').some((c) => c.u));
});

test('every turkic rule has a shared name and a description of its endings, which the app strings are generated from', () => {
    const res = path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/res/values');
    const labels = fs.readFileSync(path.join(res, 'inflections_turkic.xml'), 'utf8');
    for (const descriptor of [kazakhTransforms, kyrgyzTransforms, azerbaijaniTransforms, uzbekTransforms]) {
        const strings = fs.readFileSync(path.join(res, `inflections_${descriptor.language}.xml`), 'utf8');
        assert.equal(descriptor.sharedLabels, 'turkic');
        for (const [id, {name, description, hidden}] of Object.entries(descriptor.transforms)) {
            if (hidden) {
                assert.ok(!strings.includes(`<!-- ${id} -->`), id);
                continue;
            }
            assert.ok(description.startsWith('-') && description.endsWith(')'), id);
            assert.ok(strings.includes(`<!-- ${id} -->`) && strings.includes(`>${description}</string>`), id);
            assert.ok(labels.includes(`>${name}</string>`), id);
        }
    }
});

test('kazakh and kyrgyz rules stay out of other languages', () => {
    for (const language of ['ru', 'uk', 'tr', 'en']) {
        assert.ok(!queried(language, 'кітаптарымызда').has('кітап'), language);
        assert.ok(!queried(language, 'барды').has('бару'), language);
        assert.ok(candidates(language, 'барды').every((c) => c.g === undefined), language);
    }
    assert.ok(!queried('kk', 'китептерибизде').has('китеп'));
    assert.ok(!queried('ky', 'жазғы').has('жаз'));
    assert.ok(!queried('kk', 'барасыңбы').has('барасың'));
});

test('norwegian genitive -s matches nouns, names and forms by their definition tags', () => {
    const [day] = candidatesTo('no', 'dagens', 'dagen');
    assert.ok(day.u);
    assert.deepEqual([...day.g], ['n', 'name', 'non-lemma']);
    // The lemma a form leads to (dagen, a form of dag) needs the tags too.
    assert.equal(day.f, undefined);
    assert.deepEqual([...day.r[0]], ['-s']);
    assert.ok(queried('no', 'Norges').has('Norge'));
    assert.ok(queried('no', 'Verdens').has('verden'));
    // A word in -s takes no further s; a stem keeps two letters.
    assert.ok(!queried('no', 'glass').has('glas'));
    assert.ok(!queried('no', 'ås').has('å'));
    // The word as written stays typed and untagged.
    const written = candidates('no', 'dagens').find((c) => c.d === 'dagens');
    assert.ok(!written.u);
    assert.equal(written.g, undefined);
});

test('norwegian lowers capitals and keeps the marks of its letters, and its genitive stays out of other languages', () => {
    assert.ok(queried('no', 'LESE').has('lese'));
    assert.ok(!queried('no', 'år').has('ar'));
    assert.ok(!queried('no', 'søster').has('soster'));
    // English has an -s rule of its own.
    for (const language of ['sv', 'da', 'de', 'nl', 'pl']) {
        assert.ok(!queried(language, 'dagens').has('dagen'), language);
    }
});

test('danish lowers capitals and keeps the marks of its letters', () => {
    assert.ok(queried('da', 'Læse').has('læse'));
    assert.ok(queried('da', 'ÅR').has('år'));
    // æ, ø and å are letters of their own; Wiktionary lists Danish genitives (dagens) as forms, so no rule adds them.
    assert.ok(!queried('da', 'år').has('ar'));
    assert.ok(!queried('da', 'søster').has('soster'));
    assert.ok(!queried('da', 'læse').has('lase'));
});

test('romanian reads cedilla letters as comma ones and is looked up without the marks of its letters', () => {
    assert.ok(queried('ro', 'şi').has('și'));
    assert.ok(queried('ro', 'ŞTIU').has('știu'));
    assert.ok(queried('ro', 'ţară').has('țară'));
    // Wiktionary keys Romanian forms without marks (numarul, a form of număr).
    assert.ok(queried('ro', 'numărul').has('numarul'));
    assert.ok(queried('ro', 'dom’le').has("dom'le"));
    for (const language of ['tr', 'en', 'pl', 'de']) assert.ok(!queried(language, 'şi').has('și'), language);
});

test('romanian article and agreement endings match nominal words and forms by their definition tags', () => {
    const [council] = candidatesTo('ro', 'Consiliul', 'consiliu');
    assert.ok(council.u);
    assert.deepEqual([...council.g], ['n', 'name', 'adj', 'num', 'pron', 'det', 'non-lemma']);
    // The lemma a form leads to may be any word: aflat is a form of the verb afla.
    assert.equal(council.f, true);
    assert.deepEqual([...council.r[0]], ['-ul']);
    for (const [text, lemma] of [
        ['procesul', 'proces'], ['fratele', 'frate'], ['dezvoltarea', 'dezvoltare'], ['comisia', 'comisie'],
        ['pomii', 'pomi'], ['procesului', 'proces'], ['fratelui', 'frate'], ['Moldovei', 'Moldova'],
        ['comisiei', 'comisie'], ['lumii', 'lume'], ['dezvoltării', 'dezvoltare'], ['dezvoltarii', 'dezvoltare'],
        ['caselor', 'case'], ['aflați', 'aflat'], ['aflate', 'aflat'], ['pusă', 'pus'],
    ]) {
        assert.ok(queried('ro', text).has(lemma), `${text} → ${lemma}`);
    }
    assert.deepEqual([...candidatesTo('ro', 'aflată', 'aflat')[0].r[0]], ['agreement']);
    // A stem keeps two letters.
    assert.ok(!queried('ro', 'ful').has('f'));
    assert.ok(!queried('ro', 'tei').has('ta'));
    // The word as written stays typed and untagged.
    const written = candidates('ro', 'casă').find((c) => c.d === 'casă');
    assert.ok(!written.u);
    assert.equal(written.g, undefined);
    assert.equal(written.f, undefined);
});

test('romanian rules and cedilla letters stay out of other languages', () => {
    for (const language of ['it', 'es', 'fr', 'pt', 'en', 'de', 'no']) {
        assert.ok(!queried(language, 'consiliul').has('consiliu'), language);
        assert.ok(!queried(language, 'caselor').has('case'), language);
        assert.ok(!queried(language, 'aflată').has('aflat'), language);
    }
});

test('latin u before a vowel is tried as v, one at a time and not after q or g', () => {
    assert.ok(queried('la', 'uita').has('vita'));
    assert.ok(queried('la', 'Uergilius').has('Vergilius'));
    assert.ok(queried('la', 'seruus').has('servus'));
    // Both u of uiuere at once would turn uiuo into vivo where the text means uiuo; Wiktionary has the u spellings too.
    assert.ok(!queried('la', 'uiuere').has('vivere'));
    for (const text of ['quae', 'lingua', 'sanguis']) {
        assert.ok([...queried('la', text)].every((d) => !/v/i.test(d)), text);
    }
    // æ and œ read as ae and oe, as Yomitan's Latin code has them.
    assert.ok(queried('la', 'Aquæ').has('aquae'));
});

test('latin enclitics -que, -ne and -ve come off as the outermost ending', () => {
    assert.deepEqual([...candidatesTo('la', 'magisque', 'magis')[0].r[0]], ['-que']);
    assert.deepEqual([...candidatesTo('la', 'videsne', 'vides')[0].r[0]], ['-ne']);
    assert.deepEqual([...candidatesTo('la', 'mortuusve', 'mortuus')[0].r[0]], ['-ve']);
    assert.ok(candidatesTo('la', 'rosaeque', 'rosa').some((c) => c.r.some((r) => r.includes('-que') && r.includes('plural'))));
    // The engine drops these where the text has an entry of its own (atque is not at + que).
    assert.ok(queried('la', 'atque').has('at'));
    // Two letters stay before the enclitic.
    assert.ok(!queried('la', 'ique').has('i'));
    assert.ok(!queried('la', 'ene').has('e'));
    assert.ok(queried('la', 'neque').has('ne'));
});

test('the own enclitics may not take a name yomitan already gives a latin rule', () => {
    const yomitan = {language: 'la', conditions: {}, transforms: {'-que': {name: '-que', rules: []}}};
    assert.throws(() => withOwnTransforms(yomitan, latinEnclitics));
});

test('latin u for v and the enclitics stay out of other languages', () => {
    for (const language of ['it', 'es', 'pt', 'ro', 'en', 'fr']) {
        assert.ok(!queried(language, 'uita').has('vita'), language);
        assert.ok(!queried(language, 'magisque').has('magis'), language);
        assert.ok(!queried(language, 'videsne').has('vides'), language);
    }
});

test('esperanto reads the x-system as its letters and undoes yomitan\'s endings and affixes', () => {
    assert.ok(queried('eo', 'cxu').has('ĉu'));
    assert.ok(queried('eo', 'CXu').has('ĉu'));
    assert.ok(queried('eo', 'Ehxosxangxo').has('eĥoŝanĝo'));
    assert.ok(queried('eo', 'aux').has('aŭ'));
    assert.ok(queried('eo', 'domojn').has('domo'));
    assert.ok(queried('eo', 'mangxajxojn').has('manĝaĵo'));
    assert.ok(queried('eo', 'malbona').has('bona'));
    // The elided o is written with either apostrophe.
    assert.ok(queried('eo', 'dank’').has("dank'"));
    // The h-system is not tried, and the hats stay: the import keys headwords without them.
    assert.ok(!queried('eo', 'chu').has('ĉu'));
    assert.ok(!queried('eo', 'ĉu').has('cu'));
});

test('the esperanto x-system stays out of other languages', () => {
    for (const language of ['en', 'de', 'pl', 'cs', 'sk', 'la']) {
        assert.ok(!queried(language, 'cxu').has('ĉu'), language);
        assert.ok(!queried(language, 'aux').has('aŭ'), language);
    }
});

test('albanian endings are tried in rank order: the longest first, at equal length nouns, adjectives, then verbs', () => {
    const rules = Object.values(albanianEndings.transforms).flatMap((transform) => transform.rules);
    assert.deepEqual(rules.map((rule) => rule.rank).sort((a, b) => a - b), rules.map((_, index) => index));
    const rank = (ending, replacement, condition) => rules.find((rule) => rule.ending === ending &&
        rule.deinflected === replacement && rule.conditionsOut[0] === condition).rank;
    assert.ok(rank('uara', 'oj', 'v') < rank('uar', 'oj', 'v'));
    assert.ok(rank('uar', 'oj', 'v') < rank('ua', 'oj', 'v'));
    assert.ok(rank('en', 'e', 'n') < rank('en', '', 'v'));
    assert.ok(rank('me', 'ëm', 'adj') < rank('et', '', 'v'));
    // Same ending and part of speech: as listed.
    assert.ok(rank('uara', 'oj', 'v') < rank('uara', 'uaj', 'v'));
});

test('albanian endings give texts with their rank, undone once and alone; texts of yomitan\'s rules carry none', () => {
    const important = candidatesTo('sq', 'rëndësishme', 'rëndësishëm');
    assert.ok(important.every((c) => typeof c.p === 'number' && c.l === undefined));
    assert.ok(important.every((c) => JSON.stringify(c.r) === JSON.stringify([['-me']])));
    assert.equal(candidates('sq', 'rëndësishme').find((c) => c.d === 'rëndësishme').p, undefined);
    // lejen: the noun leje before the verb lej, both after Yomitan's own leje.
    const permit = candidates('sq', 'lejen');
    const own = (lemma) => permit.find((c) => c.d === lemma && c.p !== undefined).p;
    assert.ok(own('leje') < own('lej'));
    assert.ok(permit.some((c) => c.d === 'leje' && c.p === undefined));
    for (const text of ['ndodhet', 'botuan', 'shkruara', 'vërtetuan', 'tipareve', 'drejtësisë', 'aleatët', 'moderne',
        'formohej', 'numëronte', 'kthyer', 'gjitha', 'konferencën']) {
        for (const c of candidates('sq', text).filter((c) => c.p !== undefined)) {
            assert.equal(c.r.length, 1, `${text} → ${c.d}`);
            assert.equal(c.r[0].length, 1, `${text} → ${c.d}`);
        }
    }
    for (const [text, lemma] of [
        ['moderne', 'modern'], ['gjate', 'gjatë'], ['vjetra', 'vjetër'], ['sotme', 'sotëm'], ['shtrihet', 'shtrij'],
        ['mendohet', 'mendoj'], ['bëhej', 'bëj'], ['bazuar', 'bazoj'], ['shkruar', 'shkruaj'], ['thirrur', 'thirr'],
        ['kthyer', 'kthej'], ['përfundoi', 'përfundoj'], ['botua', 'botoj'], ['shkrua', 'shkruaj'], ['daton', 'datoj'],
        ['hyjnë', 'hyj'], ['numëronte', 'numëroj'], ['termi', 'term'], ['jugut', 'jug'], ['ndikimin', 'ndikim'],
        ['dokumentet', 'dokument'], ['shqiptarve', 'shqiptar'], ['shitja', 'shitje'], ['paqes', 'paqe'],
        ['federatës', 'federatë'],
    ]) {
        assert.ok(candidates('sq', text).some((c) => c.d === lemma && c.p !== undefined), `${text} → ${lemma}`);
    }
    // A stem keeps two letters.
    assert.ok(candidates('sq', 'mie').some((c) => c.d === 'mi' && c.p !== undefined));
    for (const text of ['ae', 'ia', 'uar']) assert.ok(candidates('sq', text).every((c) => c.p === undefined), text);
});

test('an albanian noun ending that leaves a case ending reaches only a headword of that text', () => {
    const own = (text, lemma) => candidatesTo('sq', text, lemma).filter((c) => c.p !== undefined);
    const through = (rule) => own('partneritet', 'partnerit').filter((c) => c.r[0][0] === rule);
    assert.equal(through('-ët').length, 1);
    assert.ok(through('-ët').every((c) => c.l === true));
    // A verb has no case endings: partnerit as a verb (-het) is any entry's text.
    assert.ok(through('-het').every((c) => c.l === undefined));
    assert.ok(own('partnerit', 'partner').every((c) => c.l === undefined));
    assert.ok(own('kryesore', 'kryesor').every((c) => c.l === undefined));
});

test('every albanian rule has a name and a description of its own, which the app strings are generated from', () => {
    const strings = fs.readFileSync(
        path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/res/values/inflections_sq.xml'), 'utf8');
    const rules = Object.entries(albanianEndings.transforms);
    assert.equal(rules.length, 18);
    for (const [id, {name, description, fallback}] of rules) {
        assert.equal(fallback, true, id);
        assert.notEqual(name, id, id);
        assert.ok(description.includes('('), id);
        assert.ok(strings.includes(`<!-- ${id} -->`), id);
        assert.ok(strings.includes(`>${name}</string>`) && strings.includes(`>${description}</string>`), id);
    }
});

test('the albanian endings may not take a name yomitan already gives an albanian rule', () => {
    const yomitan = {language: 'sq', conditions: {}, transforms: {'-me': {name: '-me', rules: []}}};
    assert.throws(() => withOwnTransforms(yomitan, albanianEndings));
});

test('albanian negation and elisions are tried with every apostrophe', () => {
    assert.ok(queried('sq', 's’ka').has("s'ka"));
    assert.ok(queried('sq', 'ç’është').has("ç'është"));
});

test('albanian endings stay out of other languages', () => {
    for (const language of ['en', 'ro', 'it', 'la', 'de', 'tr', 'no']) {
        assert.ok(candidates(language, 'rëndësishme').every((c) => c.p === undefined && c.l === undefined), language);
        assert.ok(!queried(language, 'rëndësishme').has('rëndësishëm'), language);
        assert.ok(!queried(language, 'ndodhet').has('ndodh'), language);
    }
});

/**
 * The rule chains, innermost step first, by which [text] reaches [lemma] in Swahili.
 * @param {string} text
 * @param {string} lemma
 */
function swahiliChains(text, lemma) {
    return candidatesTo('sw', text, lemma).flatMap((c) => c.r.map((r) => r.join(' < ')));
}

test('swahili verb prefixes come off from the outside in and reach verbs and forms by their definition tags', () => {
    const [read] = candidatesTo('sw', 'alisoma', 'soma');
    assert.ok(read.u);
    assert.deepEqual([...read.g], ['v', 'non-lemma']);
    assert.equal(read.f, undefined);
    assert.deepEqual([...read.r[0]], ['past', 'he/she']);
    for (const [text, lemma, chain] of [
        ['ninasoma', 'soma', 'present < I'],
        ['unasoma', 'soma', 'present < you'],
        ['tutasoma', 'soma', 'future < we'],
        ['mnasoma', 'soma', 'present < you pl.'],
        ['wamesoma', 'soma', 'perfect < they'],
        ['kimeanguka', 'anguka', 'perfect < class'],
        ['hatukusoma', 'soma', 'past < we < negative'],
        ['sikusoma', 'soma', 'past < negative'],
        ['hatasoma', 'soma', 'future < negative'],
        ['hajasoma', 'soma', 'not yet < negative'],
        ['angesoma', 'soma', 'conditional < he/she'],
        ['angalisoma', 'soma', 'conditional < he/she'],
        ['akisoma', 'soma', 'when < he/she'],
        ['akasoma', 'soma', 'and then < he/she'],
        ['husoma', 'soma', 'habitual'],
        ['kusoma', 'soma', 'infinitive'],
        ['kwandika', 'andika', 'infinitive'],
        ['ninakupenda', 'penda', 'object < present < I'],
        ['alimwona', 'ona', 'object < past < he/she'],
        ['alijificha', 'ficha', 'reflexive < past < he/she'],
        ['kujiunga', 'unga', 'reflexive < infinitive'],
        ['kumpa', 'pa', 'object < infinitive'],
        ['aliyesoma', 'soma', 'relative < past < he/she'],
        ['kinachoitwa', 'itwa', 'relative < present < class'],
        ['atakayesoma', 'soma', 'relative < future < he/she'],
        ['aliyemwona', 'ona', 'object < relative < past < he/she'],
        ['waishio', 'ishi', 'relative < they'],
        ['isiyojulikana', 'julikana', 'relative < negative < class'],
    ]) {
        assert.ok(swahiliChains(text, lemma).includes(chain), `${text} → ${lemma}: ${swahiliChains(text, lemma)}`);
    }
    // The stem is asked for as it is, with ku- and with a hyphen, as dictionaries write verbs all three ways.
    for (const stem of ['soma', 'kusoma', '-soma']) assert.ok(queried('sw', 'alisoma').has(stem), stem);
    // The word as written stays typed and untagged.
    const written = candidates('sw', 'alisoma').find((c) => c.d === 'alisoma');
    assert.ok(!written.u);
    assert.equal(written.g, undefined);
});

test('one-syllable swahili verbs keep their ku- after li-, na-, ta-, me-, nge- and a relative, and drop it elsewhere', () => {
    assert.ok(swahiliChains('alikula', 'kula').includes('past < he/she'));
    assert.ok(!swahiliChains('alikula', 'la').includes('past < he/she'));
    assert.ok(swahiliChains('aliyekuwa', 'kuwa').includes('relative < past < he/she'));
    assert.ok(swahiliChains('akaja', 'ja').includes('and then < he/she'));
    assert.ok(swahiliChains('akiwa', 'wa').includes('when < he/she'));
    assert.ok(swahiliChains('hakuwa', 'wa').includes('past < negative'));
    assert.ok(swahiliChains('hajafa', 'fa').includes('not yet < negative'));
    // The copula li and si end a relative without a tense: iliyo, which is; isiyo, which is not.
    assert.ok(swahiliChains('iliyo', 'li').includes('relative < class'));
    assert.ok(swahiliChains('isiyo', 'si').includes('relative < class'));
    // Names read as one-syllable stems after a tense find nothing: Angela is no a-nge-la, Pamela no pa-me-la.
    assert.ok(!queried('sw', 'Angela').has('la'));
    assert.ok(!queried('sw', 'Pamela').has('la'));
    // A stem keeps two letters.
    assert.ok(!queried('sw', 'alia').has('a'));
});

test('swahili steps between prefixes are not asked for, and nouns are not read as verbs', () => {
    const marked = (text) => [...text].some((c) => c.charCodeAt(0) >= 0xe000 && c.charCodeAt(0) <= 0xf8ff);
    for (const text of ['hatukusoma', 'aliyemwona', 'ninakupenda', 'kinachoitwa', 'waishio']) {
        const list = candidates('sw', text);
        assert.ok(list.every((c) => !marked(c.d)), text);
        // Every text but the one as written reaches only verbs and forms.
        assert.ok(list.every((c) => c.r.length === 0 || c.r.every((r) => r.length === 0) ||
            JSON.stringify(c.g) === JSON.stringify(['v', 'non-lemma'])), text);
    }
    // wali (rice) has no tense after wa-, so it is no verb form.
    assert.deepEqual([...queried('sw', 'wali')].filter((d) => d !== 'wali' && d !== 'Wali'), []);
});

test('every swahili rule has a name and a description of its own, which the app strings are generated from', () => {
    const strings = fs.readFileSync(
        path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/res/values/inflections_sw.xml'), 'utf8');
    const rules = Object.entries(swahiliTransforms.transforms);
    assert.equal(rules.length, 21);
    for (const [id, {name, description}] of rules) {
        assert.equal(name, id, id);
        assert.ok(description.includes('('), id);
        assert.ok(strings.includes(`<!-- ${id} -->`), id);
        // Named in words, each rule gets a label the translations give in their language.
        assert.ok(strings.includes(`_label">${name}</string>`) && strings.includes(`>${description}</string>`), id);
    }
});

test('swahili ng' + "'" + ' is tried with every apostrophe', () => {
    assert.ok(queried('sw', 'ng’ombe').has("ng'ombe"));
    assert.ok(queried('sw', "ng'ombe").has('ng’ombe'));
});

test('swahili prefixes stay out of other languages', () => {
    for (const language of ['cs', 'sk', 'en', 'id', 'ms', 'tl']) {
        assert.ok(!queried(language, 'alisoma').has('soma'), language);
        assert.ok(!queried(language, 'ninakupenda').has('penda'), language);
        assert.ok(candidates(language, 'alisoma').every((c) => c.g === undefined), language);
    }
});

/**
 * The texts the engine would query for [text] recognized in a screenshot.
 * @param {string} language
 * @param {string} text
 */
function queriedFromOcr(language, text) {
    return new Set(load(language).candidates(text, language, 'word', true).map((c) => c.d));
}

/** The rule chains through which [text] reaches [lemma] in Welsh. */
function welshChains(text, lemma) {
    return candidatesTo('cy', text, lemma).flatMap((c) => c.r.map((r) => r.join(' < ')));
}

test('welsh mutations are undone into the radical under the mutation name, capitals kept', () => {
    for (const [text, lemma, name] of [
        ['gath', 'cath', 'soft mutation'],
        ['bysgod', 'pysgod', 'soft mutation'],
        ['dad', 'tad', 'soft mutation'],
        ['fara', 'bara', 'soft mutation'],
        ['fam', 'mam', 'soft mutation'],
        ['ddraig', 'draig', 'soft mutation'],
        ['law', 'llaw', 'soft mutation'],
        ['Lanelli', 'Llanelli', 'soft mutation'],
        ['raglen', 'rhaglen', 'soft mutation'],
        ['wely', 'gwely', 'soft mutation'],
        ['Wely', 'Gwely', 'soft mutation'],
        ['ardd', 'gardd', 'soft mutation'],
        ['lan', 'glan', 'soft mutation'],
        ['mhen', 'pen', 'nasal mutation'],
        ['nhad', 'tad', 'nasal mutation'],
        ['nghath', 'cath', 'nasal mutation'],
        ['Nghymru', 'Cymru', 'nasal mutation'],
        ['mara', 'bara', 'nasal mutation'],
        ['nannedd', 'dannedd', 'nasal mutation'],
        ['ngardd', 'gardd', 'nasal mutation'],
        ['phen', 'pen', 'aspirate mutation'],
        ['thŷ', 'tŷ', 'aspirate mutation'],
        ['chath', 'cath', 'aspirate mutation'],
        ['hysgol', 'ysgol', 'h-prothesis'],
        ['Hysgol', 'Ysgol', 'h-prothesis'],
        ['hŵyr', 'ŵyr', 'h-prothesis'],
    ]) {
        assert.ok(welshChains(text, lemma).includes(name), `${text} → ${lemma}: ${welshChains(text, lemma)}`);
    }
    // The text reached matches entries of any part of speech (Wiktionary's Welsh entries give none).
    const [radical] = candidatesTo('cy', 'nghath', 'cath');
    assert.ok(radical.u);
    // The word as written stays typed and untagged.
    const written = candidates('cy', 'nghath').find((c) => c.d === 'nghath');
    assert.ok(!written.u);
    assert.equal(written.r.flat().length, 0);
});

test('welsh letters that only look mutated stay, and a radical keeps two letters', () => {
    // ll, rh, ff, dd, ng, nh, mh, ph, th and ch are letters of their own.
    assert.ok(!queried('cy', 'llaw').has('lllaw'));
    assert.ok(!queried('cy', 'rhaglen').has('rhhaglen'));
    assert.ok(!queried('cy', 'ffordd').has('bfordd'));
    assert.ok(!queried('cy', 'ffordd').has('mfordd'));
    assert.ok(!queried('cy', 'ddraig').has('tdraig'));
    assert.ok(!queried('cy', 'nghath').has('dghath'));
    assert.ok(!queried('cy', 'nhad').has('dhad'));
    assert.ok(!queried('cy', 'nghath').has('ghath'));
    assert.ok(!queried('cy', 'mhen').has('bhen'));
    // g is dropped before a vowel, w, l and r only, not before ll or rh.
    assert.ok(!queried('cy', 'llaw').has('gllaw'));
    assert.ok(!queried('cy', 'rhaglen').has('grhaglen'));
    assert.ok(!queried('cy', 'nos').has('gnos'));
    // A radical keeps two letters: b and dd alone are no mutated forms.
    assert.ok(!queried('cy', 'b').has('p'));
    assert.ok(!queried('cy', 'dd').has('d'));
    // One mutation only: a radical is not mutated again (aradr → garadr, not caradr).
    assert.ok(!queried('cy', 'nghath').has('gath'));
    assert.ok(queried('cy', 'aradr').has('garadr'));
    assert.ok(!queried('cy', 'aradr').has('caradr'));
});

test('common welsh words the rules would misread stay as written, in any case', () => {
    for (const word of ['mae', 'Mae', 'MAE', 'ar', 'gan', 'yr', 'i', 'dŵr', 'hyn']) {
        assert.ok(candidates('cy', word).every((c) => c.r.every((r) => r.length === 0)), word);
    }
    assert.ok(!queried('cy', 'mae').has('bae'));
    assert.ok(!queried('cy', 'Mae').has('Bae'));
    assert.ok(!queried('cy', 'dŵr').has('tŵr'));
    // A mutated form beside them is still undone, and so are words not on the list.
    assert.ok(queried('cy', 'fara').has('bara'));
    assert.ok(queried('cy', 'elwir').has('gelwir'));
    assert.ok(queried('cy', 'lan').has('glan'));
    assert.deepEqual([...new Set(welshTransforms.unmutated)], welshTransforms.unmutated);
    assert.ok(welshTransforms.unmutated.every((word) => word === word.toLowerCase()));
});

test('welsh ý and û are tried as ŷ and ŵ for words from ocr only', () => {
    assert.ok(queriedFromOcr('cy', 'tý').has('tŷ'));
    assert.ok(queriedFromOcr('cy', 'tý').has('tý'));
    assert.ok(queriedFromOcr('cy', 'Tý').has('Tŷ'));
    assert.ok(queriedFromOcr('cy', 'tûr').has('tŵr'));
    assert.ok(queriedFromOcr('cy', 'TÛR').has('TŴR'));
    // Through a mutation as well: thý → tŷ.
    assert.ok(queriedFromOcr('cy', 'thý').has('tŷ'));
    // Headwords with û keep theirs.
    assert.ok(queriedFromOcr('cy', 'cytûn').has('cytûn'));
    // App text and typed text are looked up as written.
    assert.ok(!queried('cy', 'tý').has('tŷ'));
    assert.ok(!queried('cy', 'tûr').has('tŵr'));
    // Other languages read the same either way.
    for (const [language, word] of [['en', 'tý'], ['tr', 'IŞIK'], ['sw', 'alisoma'], ['ro', 'şi']]) {
        assert.deepEqual(queriedFromOcr(language, word), queried(language, word), language);
    }
});

test('welsh apostrophes match each other', () => {
    assert.ok(queried('cy', 'mae’r').has("mae'r"));
    assert.ok(queried('cy', "mae'r").has('mae’r'));
    assert.ok(queried('cy', 'fe’i').has("fe'i"));
});

test('every welsh rule has a name and a description of its own, which the app strings are generated from', () => {
    const strings = fs.readFileSync(
        path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/res/values/inflections_cy.xml'), 'utf8');
    const rules = Object.entries(welshTransforms.transforms);
    assert.deepEqual(rules.map(([id]) => id), ['soft mutation', 'nasal mutation', 'aspirate mutation', 'h-prothesis']);
    for (const [id, {name, description}] of rules) {
        assert.equal(name, id, id);
        assert.ok(description.includes('('), id);
        assert.ok(strings.includes(`<!-- ${id} -->`), id);
        assert.ok(strings.includes(`_label">${name}</string>`) && strings.includes(`>${description}</string>`), id);
    }
});

test('welsh mutations stay out of other languages', () => {
    for (const language of ['en', 'sw', 'sq', 'ro', 'la']) {
        assert.ok(!queried(language, 'nghath').has('cath'), language);
        assert.ok(!queried(language, 'Nghymru').has('Cymru'), language);
        assert.ok(!queried(language, 'hysgol').has('ysgol'), language);
    }
});

/** The rule chains through which [text] reaches [lemma] in Irish. */
function irishChains(text, lemma) {
    return candidatesTo('ga', text, lemma).flatMap((c) => c.r.map((r) => r.join(' < ')));
}

test('irish mutations are undone into the radical under the mutation name, capitals kept', () => {
    for (const [text, lemma, name] of [
        ['bhean', 'bean', 'lenition'],
        ['chat', 'cat', 'lenition'],
        ['dheacair', 'deacair', 'lenition'],
        ['fhág', 'fág', 'lenition'],
        ['ghrian', 'grian', 'lenition'],
        ['mháthair', 'máthair', 'lenition'],
        ['phobal', 'pobal', 'lenition'],
        ['shaol', 'saol', 'lenition'],
        ['thír', 'tír', 'lenition'],
        ['Bhí', 'Bí', 'lenition'],
        ['tsráid', 'sráid', 'lenition'],
        ['tSín', 'Sín', 'lenition'],
        ['mbean', 'bean', 'eclipsis'],
        ['gcat', 'cat', 'eclipsis'],
        ['ndul', 'dul', 'eclipsis'],
        ['bhfear', 'fear', 'eclipsis'],
        ['nglinn', 'glinn', 'eclipsis'],
        ['bpáiste', 'páiste', 'eclipsis'],
        ['dtír', 'tír', 'eclipsis'],
        ['n-athair', 'athair', 'eclipsis'],
        ['nGaillimh', 'Gaillimh', 'eclipsis'],
        ['nGaeilge', 'Gaeilge', 'eclipsis'],
        ['bhFrainc', 'Frainc', 'eclipsis'],
        ['mBéarla', 'Béarla', 'eclipsis'],
        ['gCorcaigh', 'Corcaigh', 'eclipsis'],
        ['nDoire', 'Doire', 'eclipsis'],
        ['bPáras', 'Páras', 'eclipsis'],
        ['dTuaisceart', 'Tuaisceart', 'eclipsis'],
        ['nAlbain', 'Albain', 'eclipsis'],
        ['t-uisce', 'uisce', 't-prothesis'],
        ['tAontas', 'Aontas', 't-prothesis'],
        ['hÉireann', 'Éireann', 'h-prothesis'],
        ['hiomlán', 'iomlán', 'h-prothesis'],
        ['hAlbain', 'Albain', 'h-prothesis'],
    ]) {
        assert.ok(irishChains(text, lemma).includes(name), `${text} → ${lemma}: ${irishChains(text, lemma)}`);
    }
    // The radical matches entries of any part of speech and those without one; the word as written stays typed.
    const [radical] = candidatesTo('ga', 'nGaillimh', 'Gaillimh');
    assert.ok(radical.u);
    const written = candidates('ga', 'nGaillimh').find((c) => c.d === 'nGaillimh');
    assert.ok(!written.u);
    assert.equal(written.r.flat().length, 0);
});

test('irish letters that only look mutated stay, and a word is unmutated once', () => {
    // bhf is eclipsed f, not lenited b.
    assert.ok(!queried('ga', 'bhfear').has('bfear'));
    assert.ok(!queried('ga', 'bhFrainc').has('bFrainc'));
    // n and t join a lowercase vowel with a hyphen only.
    assert.ok(!queried('ga', 'nathair').has('athair'));
    assert.ok(!queried('ga', 'tuisce').has('uisce'));
    // A radical keeps two letters.
    for (const [text, lemma] of [['bh', 'b'], ['ts', 's'], ['hÉ', 'É'], ['n-a', 'a'], ['mb', 'b']]) {
        assert.ok(!queried('ga', text).has(lemma), text);
    }
    // One mutation only: Yomitan's eclipsis chained (mbmbean → bean), these rules do not.
    assert.ok(queried('ga', 'mbmbean').has('bmbean'));
    assert.ok(!queried('ga', 'mbmbean').has('bean'));
    assert.ok(!queried('ga', 'bhfhear').has('fear'));
});

test("the irish rules replace yomitan's, with its conditions", () => {
    const yomitan = {
        language: 'ga',
        conditions: {n: {name: 'Noun', isDictionaryForm: true}},
        transforms: {eclipsis: {name: 'eclipsis', rules: []}},
    };
    const replaced = withReplacedTransforms(yomitan, irishMutations);
    assert.deepEqual(Object.keys(replaced.transforms), ['lenition', 'eclipsis', 't-prothesis', 'h-prothesis']);
    assert.deepEqual(replaced.conditions, yomitan.conditions);
    assert.deepEqual(replaced.unmutated, irishMutations.unmutated);
    assert.throws(() => withReplacedTransforms({...yomitan, language: 'gd'}, irishMutations));
    assert.match(irishMutations.replaces.blob, /^[0-9a-f]{40}$/);
});

test('common irish words the rules would misread stay as written, in any case', () => {
    for (const word of ['chun', 'Chun', 'thar', 'cheana', 'bhfuil', 'the']) {
        assert.ok(candidates('ga', word).every((c) => c.r.every((r) => r.length === 0)), word);
    }
    assert.ok(!queried('ga', 'thar').has('tar'));
    // Mutated forms beside them are still undone.
    assert.ok(queried('ga', 'bhí').has('bí'));
    assert.ok(queried('ga', 'ndiaidh').has('diaidh'));
    assert.deepEqual([...new Set(irishMutations.unmutated)], irishMutations.unmutated);
    assert.ok(irishMutations.unmutated.every((word) => word === word.toLowerCase()));
});

test('irish dotted consonants are read as consonant and h, in any text', () => {
    for (const lookup of [queried, queriedFromOcr]) {
        assert.ok(lookup('ga', 'ḃean').has('bhean'));
        assert.ok(lookup('ga', 'ḃean').has('bean'));
        assert.ok(lookup('ga', 'ḃean').has('ḃean'));
        assert.ok(lookup('ga', 'Ṡeán').has('Sheán'));
        assert.ok(lookup('ga', 'ṠEÁN').has('SHEÁN'));
        // Decomposed dots as well.
        assert.ok(lookup('ga', 'c' + String.fromCharCode(0x307) + 'eann').has('cheann'));
    }
    assert.ok(!queried('cy', 'ḃean').has('bhean'));
});

test('irish apostrophes match each other', () => {
    assert.ok(queried('ga', 'd’fhág').has("d'fhág"));
    assert.ok(queried('ga', "b'fhéidir").has('b’fhéidir'));
});

test('every irish rule has a name and a description of its own, which the app strings are generated from', () => {
    const strings = fs.readFileSync(
        path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/res/values/inflections_ga.xml'), 'utf8');
    for (const [id, {name, description}] of Object.entries(irishMutations.transforms)) {
        assert.equal(name, id, id);
        assert.ok(description.includes('('), id);
        assert.ok(strings.includes(`<!-- ${id} -->`), id);
        assert.ok(strings.includes(`_label">${name}</string>`) && strings.includes(`>${description}</string>`), id);
    }
    // Yomitan's own eclipsis rule is gone.
    assert.ok(!strings.includes('eclipsis form of a noun'));
});

test('irish mutations stay out of other languages', () => {
    for (const language of ['en', 'sw', 'sq', 'ro']) {
        assert.ok(!queried(language, 'nGaillimh').has('Gaillimh'), language);
        assert.ok(!queried(language, 'tSín').has('Sín'), language);
        assert.ok(!queried(language, 'hÉireann').has('Éireann'), language);
    }
});

/** The rule chains through which [text] reaches [lemma] in Scottish Gaelic. */
function gaelicChains(text, lemma) {
    return candidatesTo('gd', text, lemma).flatMap((c) => c.r.map((r) => r.join(' < ')));
}

test('gaelic mutations are undone into the radical under the mutation name, capitals kept', () => {
    for (const [text, lemma, name] of [
        ['bhean', 'bean', 'lenition'],
        ['chat', 'cat', 'lenition'],
        ['dhoras', 'doras', 'lenition'],
        ['fhada', 'fada', 'lenition'],
        ['ghrian', 'grian', 'lenition'],
        ['mhàthair', 'màthair', 'lenition'],
        ['phìob', 'pìob', 'lenition'],
        ['shràid', 'sràid', 'lenition'],
        ['thaobh', 'taobh', 'lenition'],
        ['Bhreatainn', 'Breatainn', 'lenition'],
        ['Ghàidhealtachd', 'Gàidhealtachd', 'lenition'],
        ['t-sràid', 'sràid', 'lenition'],
        ['t-Sultain', 'Sultain', 'lenition'],
        ['n-eilean', 'eilean', 'eclipsis'],
        ['n-Alba', 'Alba', 'eclipsis'],
        ['t-uisge', 'uisge', 't-prothesis'],
        ['t-Eilean', 'Eilean', 't-prothesis'],
        ['t-òran', 'òran', 't-prothesis'],
        ['h-Alba', 'Alba', 'h-prothesis'],
        ['h-uile', 'uile', 'h-prothesis'],
        ['h-òrain', 'òrain', 'h-prothesis'],
        ['h-Èireann', 'Èireann', 'h-prothesis'],
    ]) {
        assert.ok(gaelicChains(text, lemma).includes(name), `${text} → ${lemma}: ${gaelicChains(text, lemma)}`);
    }
    // The radical matches entries of any part of speech (Wiktionary's Gaelic entries give none the rules could match);
    // the word as written stays typed and untagged.
    const [radical] = candidatesTo('gd', 'h-Alba', 'Alba');
    assert.ok(radical.u);
    const written = candidates('gd', 'h-Alba').find((c) => c.d === 'h-Alba');
    assert.ok(!written.u);
    assert.equal(written.r.flat().length, 0);
});

test('gaelic prefixes need their hyphen, a radical keeps two letters, and a word is unmutated once', () => {
    // h, t and n join a vowel with a hyphen: hiort, tuath and naoi are words of their own.
    assert.ok(!queried('gd', 'hiort').has('iort'));
    assert.ok(!queried('gd', 'tuath').has('uath'));
    assert.ok(!queried('gd', 'naoi').has('aoi'));
    // t- before s is lenition, before another consonant nothing.
    assert.ok(!queried('gd', 't-bàta').has('bàta'));
    // Elided th' and bh' (tha, bha) keep their letters: a radical keeps two.
    for (const [text, lemma] of [['th', 't'], ['bh', 'b'], ['h-a', 'a'], ['t-s', 's'], ['n-e', 'e']]) {
        assert.ok(!queried('gd', text).has(lemma), text);
    }
    // One mutation only.
    assert.ok(!queried('gd', 't-shràid').has('sràid'));
    assert.ok(!queried('gd', 'h-t-uisge').has('uisge'));
});

test('common gaelic words the rules would misread stay as written, in any case', () => {
    for (const word of ['chan', 'Chan', 'dha', 'bhathar', 'bheir', 'shuas', 'the']) {
        assert.ok(candidates('gd', word).every((c) => c.r.every((r) => r.length === 0)), word);
    }
    assert.ok(!queried('gd', 'bhathar').has('bathar'));
    assert.ok(!queried('gd', 'the').has('te'));
    // Mutated forms beside them are still undone.
    assert.ok(queried('gd', 'thug').has('tug'));
    assert.ok(queried('gd', 'bhliadhna').has('bliadhna'));
    assert.deepEqual([...new Set(gaelicMutations.unmutated)], gaelicMutations.unmutated);
    assert.ok(gaelicMutations.unmutated.every((word) => word === word.toLowerCase()));
});

test('gaelic acute vowels are tried as grave ones, the text as written first, in any text', () => {
    for (const lookup of [queried, queriedFromOcr]) {
        assert.ok(lookup('gd', 'mór').has('mòr'));
        assert.ok(lookup('gd', 'mór').has('mór'));
        assert.ok(lookup('gd', 'Ór').has('Òr'));
        assert.ok(lookup('gd', 'MÓR').has('MÒR'));
        // Through a mutation as well: mhór → mòr.
        assert.ok(lookup('gd', 'mhór').has('mòr'));
    }
    assert.equal(candidates('gd', 'cóigneir')[0].d, 'cóigneir');
    assert.ok(!queried('ga', 'mór').has('mòr'));
    assert.ok(!queried('cy', 'mór').has('mòr'));
});

test('gaelic apostrophes match each other', () => {
    assert.ok(queried('gd', 'dh’fhàg').has("dh'fhàg"));
    assert.ok(queried('gd', "a' ghrian").has('a’ ghrian'));
});

test('every gaelic rule has a name and a description of its own, which the app strings are generated from', () => {
    const strings = fs.readFileSync(
        path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/res/values/inflections_gd.xml'), 'utf8');
    const rules = Object.entries(gaelicMutations.transforms);
    assert.deepEqual(rules.map(([id]) => id), ['lenition', 'eclipsis', 't-prothesis', 'h-prothesis']);
    for (const [id, {name, description}] of rules) {
        assert.equal(name, id, id);
        assert.ok(description.includes('('), id);
        assert.ok(strings.includes(`<!-- ${id} -->`), id);
        const escaped = description.replace(/'/g, "\\'");
        assert.ok(strings.includes(`_label">${name}</string>`) && strings.includes(`>${escaped}</string>`), id);
    }
});

test('gaelic mutations stay out of other languages', () => {
    for (const language of ['en', 'cy', 'ga', 'sw', 'ro']) {
        assert.ok(!queried(language, 'h-Alba').has('Alba'), language);
        assert.ok(!queried(language, 't-sràid').has('sràid'), language);
    }
    assert.ok(!queried('cy', 'shràid').has('sràid'));
});

/**
 * The rule chains, outermost rule last as the engine reports them, that lead from [text] to [lemma] in Finnish.
 * @param {string} text
 * @param {string} lemma
 */
function finnishChains(text, lemma) {
    return candidatesTo('fi', text, lemma).flatMap((c) => c.r.map((r) => r.join(' < ')));
}

test('finnish clitics and possessive suffixes come off into the case form, untyped', () => {
    for (const [text, lemma, chain] of [
        ['talokin', 'talo', 'clitic'],
        ['rahatkin', 'rahat', 'clitic'],
        ['talossakaan', 'talossa', 'clitic'],
        ['kädessäkään', 'kädessä', 'clitic'],
        ['talossako', 'talossa', 'clitic'],
        ['tulepahan', 'tule', 'clitic < clitic'],
        ['tulossapa', 'tulossa', 'clitic'],
        ['talossani', 'talossa', 'possessive'],
        ['talossasi', 'talossa', 'possessive'],
        ['talossamme', 'talossa', 'possessive'],
        ['talossanne', 'talossa', 'possessive'],
        ['talossansa', 'talossa', 'possessive'],
        ['kädessänsä', 'kädessä', 'possessive'],
        ['talossaan', 'talossa', 'possessive'],
        ['kädessään', 'kädessä', 'possessive'],
        ['taloni', 'talon', 'possessive'],
        ['taloni', 'talo', 'possessive'],
        ['tueksesi', 'tueksi', 'possessive'],
        ['tuekseen', 'tueksi', 'possessive'],
        ['talossanikin', 'talossa', 'possessive < clitic'],
        ['kirjoissammekaan', 'kirjoissa', 'possessive < clitic'],
        ['Talossanikin', 'Talossa', 'possessive < clitic'],
    ]) {
        assert.ok(finnishChains(text, lemma).includes(chain), `${text} → ${lemma}: ${finnishChains(text, lemma)}`);
    }
    // Wiktionary's Finnish entries give no part of speech: the texts the rules give match any entry, the text as
    // written stays typed.
    assert.ok(candidatesTo('fi', 'talossani', 'talossa').every((c) => c.u));
    const written = candidates('fi', 'talossani').find((c) => c.d === 'talossani');
    assert.ok(!written.u);
    assert.equal(written.r.flat().length, 0);
});

test('finnish endings leave three letters, come off in their order and once each', () => {
    // Three letters stay before an ending: vesi is no ve with -si, onko no on with -ko.
    assert.ok(!queried('fi', 'vesi').has('ve'));
    assert.ok(!queried('fi', 'onko').has('on'));
    assert.ok(queried('fi', 'kuusi').has('kuu'));
    // -han is the outermost clitic, -ko the inner one: taloko-han, not talohan-ko.
    assert.ok(queried('fi', 'talokohan').has('talo'));
    assert.ok(!queried('fi', 'talohanko').has('talo'));
    // One clitic of each place and one possessive suffix.
    assert.ok(!queried('fi', 'talokinkin').has('talo'));
    assert.ok(!queried('fi', 'talossanini').has('talossa'));
    // A possessive suffix comes before a clitic, not after it.
    assert.ok(!queried('fi', 'talossakinni').has('talossa'));
    // -Vn needs the vowel before it twice: talossan is no talossa with a possessive.
    assert.ok(!candidates('fi', 'talossan').some((c) => c.d === 'talossa' && c.r.flat().length > 0));
});

test('every finnish rule has a name and a description of its own, which the app strings are generated from', () => {
    const strings = fs.readFileSync(
        path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/res/values/inflections_fi.xml'), 'utf8');
    const rules = Object.entries(finnishTransforms.transforms);
    assert.deepEqual(rules.map(([id]) => id), ['clitic', 'possessive']);
    for (const [id, {name, description}] of rules) {
        assert.equal(name, id, id);
        assert.ok(description.includes('('), id);
        assert.ok(strings.includes(`<!-- ${id} -->`), id);
        assert.ok(strings.includes(`_label">${name}</string>`) && strings.includes(`>${description}</string>`), id);
    }
});

test('finnish endings stay out of other languages', () => {
    for (const language of ['en', 'sv', 'no', 'lt', 'sw']) {
        assert.ok(!queried(language, 'talossanikin').has('talossa'), language);
        assert.ok(!queried(language, 'talokin').has('talo'), language);
    }
});

/**
 * The rule chains, outermost rule last as the engine reports them, that lead from [text] to [lemma] in Hungarian.
 * @param {string} text
 * @param {string} lemma
 */
function hungarianChains(text, lemma) {
    return candidatesTo('hu', text, lemma).flatMap((c) => c.r.map((r) => r.join(' < ')));
}

test('hungarian verb prefixes come off once and reach verbs and their forms by their definition tags', () => {
    for (const [text, lemma] of [
        ['megtartottam', 'tartottam'],
        ['elolvasta', 'olvasta'],
        ['kimentek', 'mentek'],
        ['visszaadta', 'adta'],
        ['keresztülment', 'ment'],
        ['összeszedte', 'szedte'],
        ['előkerült', 'került'],
        ['eléállt', 'állt'],
        ['felmegy', 'megy'],
        ['fölment', 'ment'],
        ['átadta', 'adta'],
        ['rávette', 'vette'],
        ['Megtartottam', 'tartottam'],
    ]) {
        assert.deepEqual([...hungarianChains(text, lemma)], ['prefix'], `${text} → ${lemma}`);
    }
    const kept = candidatesTo('hu', 'megtartottam', 'tartottam')[0];
    assert.ok(kept.u);
    assert.deepEqual([...kept.g], ['v', 'non-lemma']);
    // The text as written stays typed and needs no tags.
    const written = candidates('hu', 'megtartottam').find((c) => c.d === 'megtartottam');
    assert.equal(written.g, undefined);
    assert.equal(written.r.flat().length, 0);
});

test('hungarian prefixes leave three letters and come off once', () => {
    // elég is no el- with ég, kifli no ki- with fli: three letters stay after a prefix.
    assert.ok(!queried('hu', 'elég').has('ég'));
    assert.ok(!queried('hu', 'leül').has('ül'));
    assert.ok(queried('hu', 'leült').has('ült'));
    // One prefix only: visszaelad is vissza- + elad, not vissza- + el- + ad.
    assert.ok(queried('hu', 'visszaelad').has('elad'));
    assert.ok(!queried('hu', 'visszaelad').has('ad'));
    // elő- and elé- come off whole, el- leaves őkerült as well, which no entry has.
    assert.ok(queried('hu', 'előkerült').has('került'));
    // A prefix inside a word is not one.
    assert.ok(!queried('hu', 'hazamegy').has('hazagy'));
});

test('hungarian ö and ü are tried as ő and ű for words from ocr only, as read first', () => {
    assert.ok(queriedFromOcr('hu', 'füzfa').has('fűzfa'));
    // The built-in recognizer also reads ű as ú.
    assert.ok(queriedFromOcr('hu', 'fúzfa').has('fűzfa'));
    assert.ok(queriedFromOcr('hu', 'húvös').has('hűvös'));
    assert.ok(queriedFromOcr('hu', 'úr').has('úr'));
    assert.ok(queriedFromOcr('hu', 'Úr').has('Űr'));
    assert.ok(queriedFromOcr('hu', 'öt').has('őt'));
    assert.ok(queriedFromOcr('hu', 'öt').has('öt'));
    assert.ok(queriedFromOcr('hu', 'Öt').has('Őt'));
    assert.ok(queriedFromOcr('hu', 'ÖSZ').has('ŐSZ'));
    // Every combination of the first three: tükörfürö gives tűkörfürö, tükőrfürö, tűkőrfűrö and the rest.
    const texts = queriedFromOcr('hu', 'tükörfürö');
    for (const text of ['tűkörfürö', 'tükőrfürö', 'tükörfűrö', 'tűkőrfűrö']) {
        assert.ok(texts.has(text), text);
    }
    // The fourth is left as read.
    assert.ok(!texts.has('tükörfürő'));
    // The text as read comes first: it takes no processor step, the variants one.
    const steps = new Map(load('hu').candidates('öt', 'hu', 'word', true).map((c) => [c.d, c.s]));
    assert.ok(steps.get('öt') < steps.get('őt'));
    // A written ő or ű stays itself, ó is read right, and only the first word changes.
    assert.ok(!queriedFromOcr('hu', 'őt').has('öt'));
    assert.ok(!queriedFromOcr('hu', 'jó').has('jő'));
    assert.ok(!queriedFromOcr('hu', 'öt ünnep').has('öt űnnep'));
    // Through the prefix rule as well: elő- misread as elö- still comes off.
    assert.ok(queriedFromOcr('hu', 'elöfordult').has('fordult'));
    // App text and typed text are looked up as written.
    assert.ok(!queried('hu', 'füzfa').has('fűzfa'));
    assert.ok(!queried('hu', 'fúzfa').has('fűzfa'));
    assert.ok(!queried('hu', 'öt').has('őt'));
    // Other languages read the same either way.
    for (const [language, word] of [['de', 'öde'], ['fi', 'yö'], ['sv', 'höst'], ['tr', 'gönül']]) {
        assert.deepEqual(queriedFromOcr(language, word), queried(language, word), language);
    }
});

test('every hungarian rule has a name and a description of its own, which the app strings are generated from', () => {
    const strings = fs.readFileSync(
        path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/res/values/inflections_hu.xml'), 'utf8');
    const rules = Object.entries(hungarianTransforms.transforms);
    assert.deepEqual(rules.map(([id]) => id), ['prefix']);
    assert.equal(hungarianTransforms.transforms.prefix.rules.length, 25);
    for (const [id, {name, description}] of rules) {
        assert.ok(description.startsWith(name), id);
        assert.ok(description.includes('('), id);
        assert.ok(strings.includes(`<!-- ${id} -->`), id);
        assert.ok(strings.includes(`_label">${name}</string>`) && strings.includes(`>${description}</string>`), id);
    }
});

test('hungarian prefixes stay out of other languages', () => {
    for (const language of ['en', 'fi', 'de', 'sw', 'ro']) {
        assert.ok(!queried(language, 'megtartottam').has('tartottam'), language);
    }
});

test('estonian negative forms of olema reach the verb by its definition tag, under the rule name', () => {
    for (const form of ['pole', 'polnud', 'poleks', 'poldud', 'polevat', 'Pole']) {
        const reached = candidatesTo('et', form, 'olema');
        assert.deepEqual([...reached.flatMap((c) => c.r.map((r) => r.join(' < ')))], ['negative'], form);
        assert.ok(reached.every((c) => c.u && JSON.stringify(c.g) === JSON.stringify(['v'])), form);
    }
    // The text as written stays typed and needs no tags: pole's own entry comes first.
    const written = candidates('et', 'pole').find((c) => c.d === 'pole');
    assert.equal(written.g, undefined);
    assert.equal(written.r.flat().length, 0);
    // Only the whole word.
    assert.ok(!queried('et', 'polegi').has('olema'));
    assert.ok(!queried('et', 'tpole').has('olema'));
    assert.ok(!queried('et', 'olen').has('olema'));
});

test('estonian ö is tried as õ for words from ocr only, as read first', () => {
    assert.ok(queriedFromOcr('et', 'möte').has('mõte'));
    assert.ok(queriedFromOcr('et', 'vöi').has('või'));
    assert.ok(queriedFromOcr('et', 'Öhtu').has('Õhtu'));
    assert.ok(queriedFromOcr('et', 'öö').has('öö'));
    // Every combination of the first three: köögiöö gives kõögiöö, köõgiöö, kõõgiõö and the rest.
    const texts = queriedFromOcr('et', 'köögiöö');
    for (const text of ['kõögiöö', 'köõgiöö', 'köögiõö', 'kõõgiõö']) {
        assert.ok(texts.has(text), text);
    }
    assert.ok(!texts.has('köögiöõ'));
    const steps = new Map(load('et').candidates('möte', 'et', 'word', true).map((c) => [c.d, c.s]));
    assert.ok(steps.get('möte') < steps.get('mõte'));
    // A written õ stays itself, and only the first word changes.
    assert.ok(!queriedFromOcr('et', 'mõte').has('möte'));
    assert.ok(!queriedFromOcr('et', 'öö möödus').has('öö mõõdus'));
    // App text and typed text are looked up as written.
    assert.ok(!queried('et', 'möte').has('mõte'));
    // Finnish, which shares ö, reads the same either way.
    assert.deepEqual(queriedFromOcr('fi', 'yö'), queried('fi', 'yö'));
});

test('every estonian rule has a name and a description of its own, which the app strings are generated from', () => {
    const strings = fs.readFileSync(
        path.resolve(here, '../../dictionary/engine-hoshidicts/src/main/res/values/inflections_et.xml'), 'utf8');
    const rules = Object.entries(estonianTransforms.transforms);
    assert.deepEqual(rules.map(([id]) => id), ['negative']);
    assert.equal(estonianTransforms.transforms.negative.rules.length, 5);
    for (const [id, {name, description}] of rules) {
        assert.ok(description.startsWith(name), id);
        assert.ok(description.includes('('), id);
        assert.ok(strings.includes(`<!-- ${id} -->`), id);
        assert.ok(strings.includes(`_label">${name}</string>`) && strings.includes(`>${description}</string>`), id);
    }
});

test('the estonian negative rule stays out of other languages', () => {
    for (const language of ['en', 'fi', 'hu', 'de']) {
        assert.ok(!queried(language, 'pole').has('olema'), language);
    }
});

test('azerbaijani noun endings come off in the order of the grammar and match entries without a part of speech', () => {
    const [book] = candidatesTo('az', 'kitablarımızda', 'kitab');
    assert.ok(book.u);
    assert.equal(book.g, undefined);
    assert.deepEqual([...book.r[0]], ['plural', 'possessive 1pl', 'locative']);
    // After a vowel the ending takes y or n first: ataya, atanın, atasına.
    const chains = (text, lemma) => candidatesTo('az', text, lemma).flatMap((c) => c.r.map((r) => r.join(' < ')));
    assert.ok(chains('ataya', 'ata').includes('dative'));
    assert.ok(chains('atanın', 'ata').includes('genitive'));
    assert.ok(chains('atasına', 'ata').includes('possessive 3rd < dative'));
    // k and q written y and ğ before a vowel: çörəyi → çörək, ayağı → ayaq.
    assert.ok(queried('az', 'çörəyi').has('çörək'));
    assert.ok(queried('az', 'ayağı').has('ayaq'));
    // -ya follows a vowel only, -na a possessive's vowel only: kitab-ya and kitab-na are no datives.
    assert.ok(!queried('az', 'kitabya').has('kitab'));
    assert.ok(!queried('az', 'kitabna').has('kitab'));
    // The predicate's endings and the copula after it: müəlliməm, məktəbdəydi, Azərbaycandır.
    assert.ok(chains('müəlliməm', 'müəllim').includes('1sg'));
    assert.ok(chains('məktəbdəydi', 'məktəb').includes('locative < past copula'));
    assert.ok(queried('az', 'Azərbaycandır').has('Azərbaycan'));
    // A stem keeps three letters: su-yu is left to Wiktionary's own rows.
    assert.ok(!queried('az', 'suyu').has('su'));
    // The word as written stays typed.
    assert.ok(!candidates('az', 'kitab').find((c) => c.d === 'kitab').u);
});

test('azerbaijani verb forms reach the infinitive and match only definitions tagged as verbs', () => {
    const forms = {
        'gəldi': ['gəlmək', ['past']],
        'gəlmədi': ['gəlmək', ['negative', 'past']],
        'gəlirəm': ['gəlmək', ['present', '1sg']],
        'gəlmir': ['gəlmək', ['negative', 'present']],
        'oxumur': ['oxumaq', ['negative', 'present']],
        'oxuyur': ['oxumaq', ['present']],
        'gələcəyik': ['gəlmək', ['future', '1pl']],
        'oxuyacaq': ['oxumaq', ['future']],
        'gəlirdim': ['gəlmək', ['present', 'past copula', '1sg']],
        'gəlirsənmi': ['gəlmək', ['present', '2sg', 'question']],
        'gəldiyi': ['gəlmək', ['past participle', 'possessive 3rd']],
        'oxuduğu': ['oxumaq', ['past participle', 'possessive 3rd']],
        'yazdırdı': ['yazmaq', ['causative', 'past']],
        'yazıldı': ['yazmaq', ['passive', 'past']],
        'baxmalı': ['baxmaq', ['necessity']],
        'gələn': ['gəlmək', ['participle']],
        'oxuyub': ['oxumaq', ['converb']],
        'gedək': ['getmək', ['imperative']],
        'gedir': ['getmək', ['present']],
    };
    for (const [text, [lemma, chain]] of Object.entries(forms)) {
        assert.ok(isVerbThrough(candidatesTo('az', text, lemma), chain), text);
    }
    // ed- and ged- are also et- and get-; the infinitive takes -maq or -mək by the last vowel.
    assert.ok(queried('az', 'gedir').has('gedmək'));
    assert.ok(!queried('az', 'gəldi').has('gəlmaq'));
    assert.ok(!queried('az', 'oxudu').has('oxumək'));
    // Two-letter stems are left to Wiktionary's rows (olur, edir, dedi).
    for (const text of ['olur', 'edir', 'dedi']) {
        assert.ok(candidates('az', text).every((c) => !c.g), text);
    }
});

test('azerbaijani capitals lower and rise with the turkish case pairs', () => {
    assert.ok(queried('az', 'İLHAM').has('ilham'));
    assert.ok(queried('az', 'QIZLAR').has('qız'));
    assert.ok(!queried('az', 'QIZLAR').has('qiz'));
    assert.ok(queried('az', 'ilham').has('İlham'));
    assert.ok(queried('az', 'ılıq').has('Ilıq'));
});

test('azerbaijani e and i are tried as ə and ı for words from ocr only, as read first', () => {
    assert.ok(queriedFromOcr('az', 'seher').has('səhər'));
    assert.ok(queriedFromOcr('az', 'qiz').has('qız'));
    assert.ok(queriedFromOcr('az', 'Eli').has('Əli'));
    assert.ok(queriedFromOcr('az', 'ev').has('ev'));
    // The letters change before the endings come off: qizlar finds qız.
    assert.ok(queriedFromOcr('az', 'qizlar').has('qız'));
    const steps = new Map(load('az').candidates('seher', 'az', 'word', true).map((c) => [c.d, c.s]));
    assert.ok(steps.get('seher') < steps.get('səhər'));
    // A written ə stays itself, and only the first word changes.
    assert.ok(!queriedFromOcr('az', 'səhər').has('seher'));
    assert.ok(!queriedFromOcr('az', 'ev seher').has('ev səhər'));
    // App text and typed text are looked up as written.
    assert.ok(!queried('az', 'seher').has('səhər'));
    // Turkish reads the same either way.
    assert.deepEqual(queriedFromOcr('tr', 'kiz'), queried('tr', 'kiz'));
});

test('azerbaijani rules stay out of other languages', () => {
    for (const language of ['tr', 'kk', 'en']) {
        assert.ok(!queried(language, 'kitablarımızda').has('kitab'), language);
        assert.ok(!queried(language, 'gəldi').has('gəlmək'), language);
        assert.ok(candidates(language, 'gəldi').every((c) => c.g === undefined), language);
    }
});

test('uzbek noun endings come off in the order of the grammar and match entries without a part of speech', () => {
    const [book] = candidatesTo('uz', 'kitoblarimizda', 'kitob');
    assert.ok(book.u);
    assert.equal(book.g, undefined);
    assert.deepEqual([...book.r[0]], ['plural', 'possessive 1pl', 'locative']);
    const chains = (text, lemma) => candidatesTo('uz', text, lemma).flatMap((c) => c.r.map((r) => r.join(' < ')));
    // After a vowel an ending drops its linking i: otam, otasiga.
    assert.ok(chains('otam', 'ota').includes('possessive 1sg'));
    assert.ok(chains('otasiga', 'ota').includes('possessive 3rd < dative'));
    // -si and -m follow a vowel only, -im a consonant only.
    assert.ok(!queried('uz', 'kitobsi').has('kitob'));
    assert.ok(!queried('uz', 'otaim').has('ota'));
    // -ka after k, -qa after q: tokka, qishloqqa.
    assert.ok(chains('tokka', 'tok').includes('dative'));
    assert.ok(chains('qishloqqa', 'qishloq').includes('dative'));
    assert.ok(!queried('uz', 'kitobqa').has('kitob'));
    // k and q written g and gʻ before a vowel: yuragi → yurak, qishlogʻimizda → qishloq.
    assert.ok(queried('uz', 'yuragi').has('yurak'));
    assert.ok(chains('qishlogʻi', 'qishloq').includes('possessive 3rd'));
    assert.ok(chains('qishlogʻimizda', 'qishloq').includes('possessive 1pl < locative'));
    // Predicates and the question particle: talabaman, kitobmi.
    assert.ok(chains('talabaman', 'talaba').includes('1sg'));
    assert.ok(chains('kitobmi', 'kitob').includes('question'));
    // The particle follows a noun in Kyrgyz and Azerbaijani as well: китепби, kitabmı.
    assert.ok(queried('ky', 'китепби').has('китеп'));
    assert.ok(queried('az', 'kitabmı').has('kitab'));
    // A stem keeps three letters: uy-da is left to Wiktionary's rows.
    assert.ok(!queried('uz', 'uyda').has('uy'));
    // The word as written stays typed.
    assert.ok(!candidates('uz', 'kitob').find((c) => c.d === 'kitob').u);
});

test('uzbek verb forms reach the infinitive and match only definitions tagged as verbs', () => {
    const forms = {
        'bordi': ['bormoq', ['past']],
        'bordim': ['bormoq', ['past', '1sg']],
        'bordilar': ['bormoq', ['past', '3rd']],
        'bormadi': ['bormoq', ['negative', 'past']],
        'boraman': ['bormoq', ['present', '1sg']],
        'boradi': ['bormoq', ['present', '3rd']],
        'bormayman': ['bormoq', ['negative', 'present', '1sg']],
        'ishlaydi': ['ishlamoq', ['present', '3rd']],
        'boryapti': ['bormoq', ['present']],
        'boryapman': ['bormoq', ['present', '1sg']],
        'borgan': ['bormoq', ['past participle']],
        'chiqqan': ['chiqmoq', ['past participle']],
        'borsa': ['bormoq', ['conditional']],
        'borsam': ['bormoq', ['conditional', '1sg']],
        'borib': ['bormoq', ['converb']],
        'ishlab': ['ishlamoq', ['converb']],
        'borgach': ['bormoq', ['converb']],
        'boradigan': ['bormoq', ['habitual participle']],
        'borayotgan': ['bormoq', ['participle']],
        'bormoqchiman': ['bormoq', ['intention', '1sg']],
        'boribdi': ['bormoq', ['evidential']],
        'borguncha': ['bormoq', ['until']],
        'borsin': ['bormoq', ['imperative']],
        'yozildi': ['yozmoq', ['passive', 'past']],
        'oʻqitdi': ['oʻqimoq', ['causative', 'past']],
        'borasanmi': ['bormoq', ['present', '2sg', 'question']],
    };
    for (const [text, [lemma, chain]] of Object.entries(forms)) {
        assert.ok(isVerbThrough(candidatesTo('uz', text, lemma), chain), text);
    }
    // -moqda is the continuous present and the infinitive's locative.
    const continuous = candidatesTo('uz', 'bormoqda', 'bormoq');
    assert.ok(continuous.some((c) => c.u && JSON.stringify(c.g) === JSON.stringify(['v']) &&
        c.r.some((r) => r.join() === 'present')));
    // The infinitive takes -moq.
    assert.ok(!queried('uz', 'bordi').has('bormaq'));
});

test('uzbek apostrophes are written as the dictionaries write them, in any text', () => {
    assert.ok(queried('uz', "so'z").has('soʻz'));
    assert.ok(queried('uz', 'so‘z').has('soʻz'));
    assert.ok(queried('uz', "O'zbekiston").has('Oʻzbekiston'));
    assert.ok(queried('uz', "ta'lim").has('taʼlim'));
    assert.ok(queried('uz', 'ta`til').has('taʼtil'));
    assert.ok(queried('uz', 'ta’sir').has('taʼsir'));
    // A turned comma after another letter is the apostrophe.
    assert.ok(queried('uz', 'taʻlim').has('taʼlim'));
    // The endings come off the text so written: qishlog'imizda finds qishloq.
    assert.ok(queried('uz', "qishlog'imizda").has('qishloq'));
    // The text as written is tried as well.
    assert.ok(queried('uz', "so'z").has("so'z"));
    assert.ok(queried('uz', 'soʻz').has('soʻz'));
});

test('uzbek cyrillic text is looked up in latin letters', () => {
    for (const [cyrillic, latin] of [
        ['китоб', 'kitob'], ['ўқимоқ', 'oʻqimoq'], ['ғалла', 'gʻalla'], ['Шаҳар', 'shahar'], ['ер', 'yer'],
        ['келди', 'keldi'], ['маъно', 'maʼno'], ['цирк', 'sirk'], ['милиция', 'militsiya'], ['чой', 'choy'],
    ]) {
        assert.ok(queried('uz', cyrillic).has(latin), cyrillic);
    }
    // A capital gives a capital first letter: Тошкент → Toshkent.
    assert.ok(queried('uz', 'Тошкент').has('Toshkent'));
    // The endings come off the Latin text: китобларимизда finds kitob.
    assert.ok(queried('uz', 'китобларимизда').has('kitob'));
    // Latin text gains nothing from it.
    assert.ok(![...queried('uz', 'kitob')].some((text) => /[а-я]/.test(text)));
});

test('uzbek capitals take the default case pairs', () => {
    assert.ok(queried('uz', 'ILM').has('ilm'));
    assert.ok(!queried('uz', 'ILM').has('ılm'));
    assert.ok(queried('uz', 'ilm').has('Ilm'));
    assert.ok(!queried('uz', 'ilm').has('İlm'));
});

test('uzbek rules and text processors stay out of other languages', () => {
    for (const language of ['tr', 'kk', 'en']) {
        assert.ok(!queried(language, 'kitoblarimizda').has('kitob'), language);
    }
    // Azerbaijani writes some of the same endings (-lar, -imiz, -da), not -gʻi or -moq.
    for (const language of ['tr', 'az', 'kk', 'en']) {
        assert.ok(!queried(language, 'qishlogʻi').has('qishloq'), language);
        assert.ok(!queried(language, 'bordi').has('bormoq'), language);
        assert.ok(!queried(language, "so'z").has('soʻz'), language);
        assert.ok(!queried(language, 'китоб').has('kitob'), language);
    }
});
