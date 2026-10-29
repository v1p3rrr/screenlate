import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readingPage } from '../../../../scripts/page-tests/harness.mjs';

const HeadwordReading = readingPage().global('HeadwordReading');

// Arrays from the page are copied into this realm, so deepEqual compares them by value.
const syllables = reading => Array.from(HeadwordReading.syllables('zh', reading) ?? [], part => part.tone ? `${part.text}${part.tone}` : part.text);
const segments = (expression, reading) => Array.from(HeadwordReading.segments('zh-Hant', expression, reading),
    ([text, parts]) => parts ? `${text}[${parts.map(part => part.text).join('')}]` : text);

test('pinyin splits into syllables with their tones, unmarked ones neutral', () => {
    assert.deepEqual(syllables('túshūguǎn'), ['tú2', 'shū1', 'guǎn3']);
    assert.deepEqual(syllables('tóufa'), ['tóu2', 'fa5']);
    assert.deepEqual(syllables('Běijīng'), ['Běi3', 'jīng1']);
    assert.deepEqual(syllables('zhōngguó'), ['zhōng1', 'guó2']);
});

test('a syllable starting with a vowel follows an apostrophe, never another syllable', () => {
    assert.deepEqual(syllables('fāngàn'), ['fān1', 'gàn4']);
    assert.deepEqual(syllables("Xī'ān"), ['Xī1', "'", 'ān1']);
    assert.deepEqual(syllables('xīnán'), ['xī1', 'nán2']);
});

test('ü, ê, spaces and the erhua r are kept', () => {
    assert.deepEqual(syllables("nǚ'ér"), ['nǚ3', "'", 'ér2']);
    assert.deepEqual(syllables('lǜ chá'), ['lǜ4', ' ', 'chá2']);
    assert.deepEqual(syllables('yīdiǎnr'), ['yī1', 'diǎnr3']);
    assert.deepEqual(syllables('ế'), ['ế2']);
});

test('text that is no pinyin gives nothing', () => {
    assert.equal(HeadwordReading.syllables('zh', 'xyz'), null);
    assert.equal(HeadwordReading.syllables('ja', 'たべる'), null);
});

test('each character gets its syllable', () => {
    assert.deepEqual(segments('圖書館', 'túshūguǎn'), ['圖[tú]', '書[shū]', '館[guǎn]']);
    assert.deepEqual(segments('头发', 'tóufa'), ['头[tóu]', '发[fa]']);
});

test('the erhua r goes over 儿', () => {
    assert.deepEqual(segments('一点儿', 'yīdiǎnr'), ['一[yī]', '点[diǎn]', '儿[r]']);
});

test('a word with letters or a reading that does not match gets one ruby', () => {
    assert.deepEqual(segments('卡拉OK', 'kǎlā ōukèi'), ['卡拉OK[kǎlā ōukèi]']);
    assert.deepEqual(segments('图书馆', 'túshū'), ['图书馆[túshū]']);
    assert.deepEqual(segments('图书馆', ''), ['图书馆']);
});

test('a language without rules has no segments', () => {
    assert.equal(HeadwordReading.segments('ja', '猫', 'ねこ'), null);
    assert.equal(HeadwordReading.segments('', '猫', 'ねこ'), null);
});

const jyutping = reading => Array.from(HeadwordReading.syllables('yue', reading) ?? [], part => part.tone ? `${part.text}|${part.tone}` : part.text);
const cantonese = (expression, reading, lang = 'yue-HK') => Array.from(HeadwordReading.segments(lang, expression, reading),
    ([text, parts]) => parts ? `${text}[${parts.map(part => part.text).join('')}]` : text);

test('jyutping splits into syllables with tones 1 to 6, spaced or not', () => {
    assert.deepEqual(jyutping('nei5 hou2'), ['nei5|5', ' ', 'hou2|2']);
    assert.deepEqual(jyutping('ngo5dei6'), ['ngo5|5', 'dei6|6']);
    assert.deepEqual(jyutping('gwong2 dung1 waa2'), ['gwong2|2', ' ', 'dung1|1', ' ', 'waa2|2']);
    // Syllabic nasals and the finals oe, eo and yu.
    assert.deepEqual(jyutping('m4 goi1'), ['m4|4', ' ', 'goi1|1']);
    assert.deepEqual(jyutping('ng5 zoek3 seoi2 jyu4'), ['ng5|5', ' ', 'zoek3|3', ' ', 'seoi2|2', ' ', 'jyu4|4']);
});

test('text that is no jyutping with tones gives nothing', () => {
    assert.equal(HeadwordReading.syllables('yue', 'nei hou'), null);
    assert.equal(HeadwordReading.syllables('yue', 'nei7'), null);
    assert.equal(HeadwordReading.syllables('yue', 'nǐ hǎo'), null);
    assert.equal(HeadwordReading.syllables('yue', 'ok1 k'), null);
});

test('each cantonese character gets its syllable, in either script', () => {
    assert.deepEqual(cantonese('你好', 'nei5 hou2'), ['你[nei5]', '好[hou2]']);
    assert.deepEqual(cantonese('我哋', 'ngo5dei6'), ['我[ngo5]', '哋[dei6]']);
    assert.deepEqual(cantonese('广东话', 'gwong2 dung1 waa2', 'yue-Hans'), ['广[gwong2]', '东[dung1]', '话[waa2]']);
});

test('a cantonese word with letters or a reading that does not match gets one ruby', () => {
    assert.deepEqual(cantonese('OK啦', 'ou1 kei1 laa1'), ['OK啦[ou1 kei1 laa1]']);
    assert.deepEqual(cantonese('你好', 'nei5'), ['你好[nei5]']);
    // A reading that is no jyutping still goes over the word as written; one without letters is left out.
    assert.deepEqual(cantonese('卡拉OK', 'kaa1 laai1 ou kei'), ['卡拉OK[kaa1 laai1 ou kei]']);
    assert.deepEqual(cantonese('你好', '你好'), ['你好']);
    assert.deepEqual(cantonese('你好', ''), ['你好']);
});

test('cantonese and chinese rules stay with their languages', () => {
    assert.deepEqual(Array.from(HeadwordReading.syllables('zh-Hant', 'nei5 hou2'), part => part.tone), [5, null, 5, null]);
    assert.deepEqual(cantonese('你好', 'nǐ hǎo'), ['你好[nǐ hǎo]']);
    for (const lang of ['ja', 'ko', 'vi']) assert.equal(HeadwordReading.syllables(lang, 'nei5 hou2'), null, lang);
});

const russian = (expression, reading) => Array.from(HeadwordReading.segments('ru', expression, reading),
    ([text, parts]) => parts ? `${text}[${parts.map(part => part.text).join('')}]` : text);

test('a russian reading that only adds stress marks takes the word\'s place', () => {
    assert.deepEqual(russian('кошка', 'ко́шка'), ['ко́шка']);
    assert.deepEqual(russian('пароход', 'парохо́д'), ['парохо́д']);
    assert.deepEqual(russian('ёж', ''), ['ёж']);
    assert.deepEqual(russian('что', 'что'), ['что']);
});

test('another russian reading goes over the word', () => {
    assert.deepEqual(russian('еж', 'ёж'), ['еж[ёж]']);
    assert.equal(HeadwordReading.syllables('ru', 'ко́шка'), null);
});

test('russian and chinese rules stay with their languages', () => {
    const stressed = 'ко\u0301шка';
    // Other languages written in Cyrillic, and Latin ones, take no rules of their own.
    for (const lang of ['uk', 'be', 'bg', 'en', 'de']) {
        assert.equal(HeadwordReading.segments(lang, 'кошка', stressed), null, lang);
    }
    assert.deepEqual(Array.from(HeadwordReading.segments('ru-RU', 'кошка', stressed), ([text]) => text), [stressed]);
    // Pinyin-like readings of Vietnamese and Korean words are not split into syllables.
    for (const lang of ['vi', 'ko', 'ja']) {
        assert.equal(HeadwordReading.syllables(lang, 'túshūguǎn'), null, lang);
    }
});

test('a russian word in a sentence takes the stress only in its dictionary form', () => {
    const inSentence = (text, expression, reading) =>
        Array.from(HeadwordReading.sentenceSegments('ru', text, expression, reading), ([part, parts]) => parts ? `${part}[]` : part);
    assert.deepEqual(inSentence('Кошка', 'кошка', 'ко\u0301шка'), ['Ко\u0301шка']);
    assert.deepEqual(inSentence('руки', 'рука', 'рука\u0301'), ['руки']);
    assert.deepEqual(inSentence('еж', 'ёж', 'ёж'), ['еж']);
    assert.deepEqual(inSentence('кошка', 'кошка', ''), ['кошка']);
    // Chinese and languages without rules have no sentence rules.
    assert.equal(HeadwordReading.sentenceSegments('zh', '学习', '学习', 'xuéxí'), null);
    assert.equal(HeadwordReading.sentenceSegments('ja', '猫', '猫', 'ねこ'), null);
});

const arabic = (expression, reading, lang = 'ar') => Array.from(HeadwordReading.segments(lang, expression, reading) ?? [],
    ([text, parts]) => parts ? `${text}[${parts.map(part => part.text).join('')}]` : text);

test("an arabic reading that only adds vowel marks takes the word's place", () => {
    assert.deepEqual(arabic('كتاب', 'كِتَاب'), ['كِتَاب']);
    assert.deepEqual(arabic('أكبر', 'أَكْبَر'), ['أَكْبَر']);
    // The dagger alef, shadda and tanwin are marks as well; the hamza of أ written as a mark is part of the letter.
    assert.deepEqual(arabic('كتب', 'كِتٰبٌ'), ['كِتٰبٌ']);
    assert.deepEqual(arabic('مدرس', 'مُدَرِّس'), ['مُدَرِّس']);
    assert.deepEqual(arabic('أكل', 'ا\u0654كَلَ'), ['ا\u0654كَلَ']);
    assert.deepEqual(arabic('كتاب', ''), ['كتاب']);
});

test('an arabic reading with other letters goes over the word, and the rules stay with arabic', () => {
    assert.deepEqual(arabic('اكبر', 'أَكْبَر'), ['اكبر[أَكْبَر]']);
    assert.equal(HeadwordReading.syllables('ar', 'كِتَاب'), null);
    for (const lang of ['ur', 'he', 'en']) assert.deepEqual(arabic('كتاب', 'كِتَاب', lang), [], lang);
    // Persian keeps the word as written.
    assert.deepEqual(arabic('كتاب', 'كِتَاب', 'fa'), ['كتاب']);
});

test('a persian latin reading stands after the word, which keeps its place', () => {
    assert.deepEqual(arabic('مجازی', 'majâzi', 'fa'), ['مجازی']);
    assert.equal(HeadwordReading.after('fa', 'مجازی', 'majâzi'), 'majâzi');
    assert.equal(HeadwordReading.after('fa', 'مجازی', ''), null);
    assert.equal(HeadwordReading.after('fa', 'مجازی', 'مجازی'), null);
    assert.equal(HeadwordReading.syllables('fa', 'majâzi'), null);
    // Other languages draw their readings over or in the word.
    for (const lang of ['ar', 'zh', 'ru', 'ur', 'en']) assert.equal(HeadwordReading.after(lang, 'كتاب', 'kitāb'), null, lang);
});
