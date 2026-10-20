// Checks the built language bundles (dictionary/engine-hoshidicts/src/main/assets/yomitan-language) the way the
// engine calls them: node --test scripts/yomitan-language
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';

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
    for (const language of ['en', 'de', 'pl', 'lv', 'nl', 'cs', 'id', 'ms', 'tl']) {
        // Indonesian, Malay and Tagalog drop every combining mark, as Yomitan's processors for them do.
        if (!['id', 'ms', 'tl'].includes(language)) assert.ok(!queried(language, stressed).has('кошка'), language);
        assert.ok(!queried(language, 'еж').has('ёж'), language);
    }
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
