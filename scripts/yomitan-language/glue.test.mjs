// Checks the built language bundles (dictionary/engine-hoshidicts/src/main/assets/yomitan-language) the way the
// engine calls them: node --test scripts/yomitan-language
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';
import {
    hebrewTransforms, kazakhTransforms, kyrgyzTransforms, latinEnclitics, persianTransforms, withOwnTransforms,
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
 * The texts the engine would query for Thai [text], which it looks up letter by letter.
 * @param {string} text
 */
function queriedThai(text) {
    return new Set(load('th').candidates(text, 'th', 'letter').map((c) => c.d));
}

test('thai sara am in two parts and a tone mark before its vowel are tried as headwords write them', () => {
    // น + nikhahit + mai tho + sara aa, as a recognizer may give น้ำ.
    assert.ok(queriedThai('น\u0E4D\u0E49\u0E32').has('น้ำ'));
    assert.ok(queriedThai('ท\u0E4D\u0E32').has('ทำ'));
    // ก + mai ek + sara ii, typed for กี่.
    assert.ok(queriedThai('ก\u0E48\u0E35').has('กี่'));
    assert.ok(queriedThai('สิทธ\u0E4C\u0E34').has('สิทธิ์'));
    assert.ok(queriedThai('กี่').has('กี่'));
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
        assert.ok(!queried(language, 'ท\u0E4D\u0E32').has('ทำ'), language);
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
    for (const descriptor of [kazakhTransforms, kyrgyzTransforms]) {
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
