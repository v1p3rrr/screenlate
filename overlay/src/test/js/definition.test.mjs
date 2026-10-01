import assert from 'node:assert/strict';
import { test } from 'node:test';
import { definitionPage } from '../../../../scripts/page-tests/harness.mjs';

const page = definitionPage();
const DefinitionCopy = page.global('DefinitionCopy');

const glossary = (content, definitionTags = '', dictionary = 'Dict') =>
    ({ dictionary, definitionTags, content: JSON.stringify(content) });
const meanings = (...glossaries) => DefinitionCopy.copy(glossaries, 'meanings').text;
const sc = content => ({ type: 'structured-content', content });

// Shaped as JMdict-based dictionaries (Jitendex, Kolobok) mark them: senses with a glossary list, examples and
// cross-references beside it.
const jitendexLike = sc([
    {
        tag: 'div', data: { content: 'sense-group' }, content: [
            { tag: 'span', data: { class: 'tag', content: 'part-of-speech-info' }, content: 'сущ.' },
            { tag: 'span', data: { class: 'tag', content: 'misc-info' }, content: 'нар.' },
            {
                tag: 'ol', content: [
                    {
                        tag: 'li', data: { content: 'sense' }, style: { listStyleType: '"①"' }, content: [
                            { tag: 'ul', data: { content: 'glossary' }, content: [{ tag: 'li', content: 'есть' }, { tag: 'li', content: 'кушать' }] },
                            {
                                tag: 'div', data: { content: 'extra-info' }, content: {
                                    tag: 'div', data: { content: 'example-sentence' }, content: [
                                        { tag: 'div', data: { content: 'example-sentence-a' }, content: ['もっと', { tag: 'ruby', content: ['果', { tag: 'rt', content: 'くだ' }] }, 'を食べる'] },
                                        { tag: 'div', data: { content: 'example-sentence-b' }, content: 'Ешьте больше фруктов.' },
                                    ],
                                },
                            },
                        ],
                    },
                    {
                        tag: 'li', data: { content: 'sense' }, style: { listStyleType: '"②"' }, content: [
                            { tag: 'ul', data: { content: 'glossary' }, content: { tag: 'li', content: 'зарабатывать на жизнь' } },
                            { tag: 'div', data: { content: 'xref' }, content: ['см. также ', { tag: 'a', href: '?query=食う', content: '食う' }] },
                        ],
                    },
                ],
            },
        ],
    },
    { tag: 'div', data: { content: 'attribution' }, content: { tag: 'a', href: 'https://example.org', content: 'JMdict' } },
]);

test('meanings of JMdict-based dictionaries are their glossary lists', () => {
    assert.equal(meanings(glossary([jitendexLike])), '1. есть; кушать\n2. зарабатывать на жизнь');
});

test('everything keeps markers, examples and tags as text, and formatting as html', () => {
    const { text, html } = DefinitionCopy.copy([glossary([jitendexLike])], 'all');
    assert.match(text, /^сущ\. нар\.\n/);
    assert.match(text, /①\n• есть\n• кушать\n/);
    assert.match(text, /もっと果を食べる/);
    assert.doesNotMatch(text, /くだ/);
    assert.match(html, /<li[^>]*>есть<\/li>/);
    assert.match(html, /<ruby>果<rt>くだ<\/rt><\/ruby>/);
});

test('pictures are left out of the html', () => {
    const picture = glossary([sc([{ tag: 'img', path: 'img/cat.png', width: 1, height: 1 }, 'кошка'])]);
    const { text, html } = DefinitionCopy.copy([picture], 'all');
    assert.equal(text, 'кошка');
    assert.doesNotMatch(html, /<img/);
});

test('several glossaries are numbered with their tags; text glossaries are escaped', () => {
    const { text, html } = DefinitionCopy.copy([glossary(['to eat'], 'v1 vt'), glossary(['a <b> c'])], 'all');
    assert.equal(text, '1. (v1, vt) to eat\n2. a <b> c');
    assert.equal(html, '<ol><li><i>(v1, vt)</i> to eat</li><li>a &lt;b&gt; c</li></ol>');
});

test('japanese dictionaries: the marked definitions without readings', () => {
    const sense = (number, text, example) => ({
        tag: 'div', content: [
            { tag: 'span', data: { name: '語義番号' }, content: number },
            { tag: 'span', data: { name: '語釈' }, content: [text[0], { tag: 'span', data: { name: 'ルビG' }, content: '(ガン)' }, text[1]] },
            { tag: 'span', data: { name: '用例' }, content: example },
        ],
    });
    const entry = sc([
        { tag: 'span', data: { name: '見出部' }, content: 'ねこ【猫】' },
        sense('㊀', ['家に飼う（愛玩', '用）小動物。'], '「三毛━」'),
        sense('㊁', ['土製の行火', '。'], ''),
    ]);
    assert.equal(meanings(glossary([entry])), '1. 家に飼う（愛玩用）小動物。\n2. 土製の行火。');
});

test('plain text: numbered senses without the headword, labels, examples and references', () => {
    const english = 'ねこ・ネコ【猫】\n〘n〙\n1 cat.\n2 〘col〙 shamisen.\n→猫車';
    assert.equal(meanings(glossary([english])), '1. cat.\n2. shamisen.');
    const monolingual = 'ね‐こ【猫】\n《「ね」は鳴き声》\n① 食肉目ネコ科の哺乳類。\n② 《胴を猫の皮で張るところから》三味線のこと。\n「―が一枚」〈魯文〉\n③\n㋐「猫火鉢」の略。「―に当たる」\n㋑「猫車」の略。\n[補説]作品名別項。';
    assert.equal(meanings(glossary([monolingual])), '1. 食肉目ネコ科の哺乳類。\n2. 三味線のこと。\n3. 「猫火鉢」の略。; 「猫車」の略。');
});

test('numbered lines continue their sense, examples and notes after them are left out', () => {
    const lines = ['1) кошка, кот', '{～の} кошачий', '2) (прост.) кошечка (о гейше)', '3) сямисэн'];
    assert.equal(meanings(glossary(lines)), '1. кошка, кот\n2. (прост.) кошечка (о гейше)\n3. сямисэн');
    const notes = '❶ 固形の食物をかんで飲み込む。\n「生で━」\n⑴ 「食う」を丁寧にいう語。\n❷ 生活する。食う。';
    assert.equal(meanings(glossary([notes])), '1. 固形の食物をかんで飲み込む。\n2. 生活する。食う。');
});

test('bilingual text without numbers: one sense per item, japanese-led lines are examples', () => {
    const warodai = ['есть\n…を食べている питаться чем-л.\n食べて見る пробовать', 'перен. жить, существовать\nこの収入では食べられない на этот доход не проживёшь'];
    assert.equal(meanings(glossary(warodai)), '1. есть\n2. перен. жить, существовать');
    const kenrowa = 'たべる 【食べる】\n〈食物をとる〉 есть; кормиться\n日本人は米を食べている∥Японцы едят рис.\n〈生活する〉 жить\n自分で働いて～∥жить своим трудом.\n◆猫に鰹節\nПослать волка стеречь овец.';
    assert.equal(meanings(glossary([kenrowa])), '1. 〈食物をとる〉 есть; кормиться\n2. 〈生活する〉 жить');
    assert.equal(meanings(glossary(['Katze\nKater'])), 'Katze; Kater');
});

test('examples inside a definition line are cut after its last sentence', () => {
    const line = '① 飲食物を口から体内に入れる。特に、食物にいう。宇津保物語「かの―・べまほしけれ」。「御飯を―・べる」';
    assert.equal(meanings(glossary([`た・べる【食べる】\n${line}\n② 生計を立てる。`])), '1. 飲食物を口から体内に入れる。特に、食物にいう。\n2. 生計を立てる。');
});

test('structured content without marked meanings is read as the page shows it', () => {
    const entry = sc([
        { tag: 'span', content: 'た・べる【食べる】' },
        { tag: 'div', content: '〘他下一〙〚文〛た・ぶ' },
        { tag: 'div', content: '①飲食物を口から体内に入れる。' },
        { tag: 'div', content: '②転じて、生計を立てる。' },
    ]);
    assert.equal(meanings(glossary([entry])), '1. 飲食物を口から体内に入れる。\n2. 転じて、生計を立てる。');
});

test('without anything that looks like a meaning the lines are copied as one', () => {
    const links = sc({ tag: 'ul', content: [{ tag: 'li', content: { tag: 'a', href: '?query=a', content: '猫に小判' } }, { tag: 'li', content: '猫の手' }] });
    assert.equal(meanings(glossary([links])), '猫に小判; 猫の手');
    assert.equal(meanings(glossary([sc([])])), '');
});

test('a plain gloss that starts with a number is not taken for a numbered meaning', () => {
    assert.equal(meanings(glossary(['all day', '24 hours'])), '1. all day\n2. 24 hours');
});

test('meanings of several glossaries are numbered together', () => {
    assert.equal(meanings(glossary(['to eat']), glossary(['to live on'])), '1. to eat\n2. to live on');
});
