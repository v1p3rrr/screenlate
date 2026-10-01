/*
 * Markup of Japanese dictionaries for definition copying (definition.js): the shapes of monolingual dictionaries
 * (大辞泉, 大辞林, 明鏡 and similar) and of bilingual ones with Japanese examples (Warodai, Kenkyusha).
 */
DefinitionCopy.addLanguage('ja', {
    /** Readings printed inside definitions, such as 愛玩(ガン). */
    readingNames: new Set(['ルビ', 'ルビG']),
    meaningNames: new Set(['語釈']),
    source: /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/gu,
    /** かな【漢字】. */
    headword: /【[^】]*】|〖[^〗]*〗/,
    /** ㋐, ㋑. */
    subMarker: /^\s*[\u32d0-\u32fe]\s*/,
    extraStart: /^[→↔⇒⇔☞◇◆■□▼▽◎●「『《［[※＊]/,
    /** 〔食する〕: a note in a monolingual dictionary, a translation in a bilingual one ("〔食する〕 eat"). */
    noteStart: /^〔/,
    sourceStart: /^[{｛]?[…‥]?[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}～〜]/u,
    /** 〈食物をとる〉 есть. */
    senseLabel: /^〈[^〉]+〉\s*\S/,
    labels: /〘[^〙]*〙/g,
    leadingNote: /^\s*《[^》]*》/,
    bullet: /^\s*・\s*/,
    /** A quote where a dash or ～ stands for the word: 「―が一枚」. */
    example: /「[^「」]*[―━～〜][^「」]*」/,
    examples: /「[^「」]*[―━～〜][^「」]*」(?:（[^（）]*）)?/g,
    sentenceEnd: '。',
});
