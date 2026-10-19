# Yomitan behavior to match

Reference: `yomidevs/yomitan` `ext/js/language/translator.js`, `ext/data/schemas/options-schema.json`.

## Lookup

1. Take up to `scanLength` (16) characters from the cursor. Try prefixes from longest to shortest.
2. For each prefix, build text variants with preprocessors (width conversion, hiragana ↔ katakana, collapsing emphatic sequences, …), deinflect each variant with the language transforms (keeping the rule chain), post-process.
3. Query all candidates at once, matching term or reading exactly. Drop matches whose part-of-speech rules do not satisfy the deinflection conditions.
4. No term found: the scanner looks up the first character in the kanji dictionaries and shows its kanji entry, highlighting one character (`TextScanner._findDictionaryEntries` in `ext/js/language/text-scanner.js`; `searchKanji` is on by default for every scanning input). Screenlate does the same in the popup, popup links and the Search screen (`DictionaryLookup.characterEntry`). A kanji entry shows the dictionary's tags for the character (KANJIDIC jouyou, jinmeiyou; the hanja dictionary's 교육용, 인명용); Screenlate's character view shows them since 2026-10-15.
5. Radical look-alikes (Kangxi radicals U+2F00–2FD5, the supplement U+2E80–2EF3; ⽇ for 日, common in text copied
   from PDFs) are read as their ideographs: Yomitan's `normalizeRadicalCharacters` for zh, and the Japanese path
   does the same. Screenlate's `lookupStart` dropped a lookup starting at one before the engine saw it; since
   2026-10-15 `CjkText.isRadical` lets it start a word (instrumented: `ChineseLookupTest`, `LookupPipelineTest`).
6. English phrasal verbs: the past, -ing and third-person rules have phrasal variants ("picked up" → "pick up",
   conditions `v_phr`), and the interposed-object rule drops the words between a verb and an adverb particle
   ("picked the book up" → "picked up"). `v` only groups its sub-condition `v_phr`, so both have flag 1, and an entry
   without a part of speech (wty-en-ru: 333 "word particle" phrases, all with empty rules and the dtag "phrase")
   never matches. Screenlate's own deviation since 2026-10-15: `glue.js` marks a candidate `u` when its conditions are
   the verb flags, no chain has "interposed object" and the deinflected text's second word is one of Yomitan's
   adverb particles (`phrasalVerbParticles`; prepositions such as to, for, with left out); `candidate_lookup.cpp`
   then also accepts entries and form-of items with blank rules. Checked over the 20,562 English example sentences
   of wty-en-ru: every hit was a real phrasal verb except "ways out" → "way out" (right entry, labelled
   "3rd pers. sing. pres"); plural nouns like "sons in law" behave the same. Prepositions would have added "closed
   to" → "close to" (near), "saved for" → "save for" (except), "puts upon"; the interposed-object rule would have
   added "time is running out" → "time out" and "way the money ran out" → "way out". Lost by the strict rule: 18
   verbal phrases ending in a preposition (look for, deal with, apply to, …). Instrumented:
   `LanguageLookupTest.phrasalVerbsWithoutPartOfSpeechMatchTheirFormsWithAdverbParticles`.
7. The letter resolution shortens the text one UTF-16 unit at a time, which splits a character outside the BMP
   (𠮶, CJK Extension B). Such a candidate matches no term, and the engine's JSON reader refuses an unpaired
   surrogate, so `glue.js` (`isWellFormed`) drops candidates whose original, transformed or deinflected text holds
   half a pair.
8. Form-of entries (a glossary item `[lemma, [tags]]`, Yomitan's dictionary deinflections) give a chain of one step
   with no conditions, so a form that names several lemmas lists them all, ordered by frequency: Korean Wiktionary
   gives 마셨어요 as a form of 마시다, 말다 and 마다, and wordfreq ranks 마다 high from the particle, so 마다 comes
   first; a particle entry of 마다 in another dictionary joins its group. Screenlate does the same. It cuts a tag at
   two or more spaces (`FormOfTags.clean`): about 1,000 Korean Wiktionary tags carry a piece of gloss there ("hanja
   form   the Korean language”)").
   Yomitan follows them for Japanese as well (Wiktionary's "non-lemma" rows: じどう → 自動 "transliteration", 社會 →
   社会 "kyūjitai"); Screenlate's Japanese lookup does since 2026-10-15 (`form_of.cpp` in the engine module), with the
   tags as the dictionary writes them and one chain per term: a term also found by its reading or by deinflection
   keeps that match. Jitendex and Kolobok put such an item next to a visible "⟶ lemma" link (Jitendex "redirected
   from 社會", Kolobok "вариант написания: 社會"), so 社會 also brings 社会 with that tag; when several dictionaries
   lead to the same term, the tag of the first one in the user's order shows.
   Screenlate differs where a chain leads back to the text: Yomitan shows every non-empty chain of a term, so its
   Ukrainian rules and Wiktionary give книга "nominative plural « vocative singular" (книга as the plural of книго,
   which Wiktionary lists as the vocative of книга). Screenlate drops a form-of step that leads back to the scanned
   text through another text, and a second form-of step back to the text before it (Jansen → Janssen → Jansen).
9. Korean irregular-stem rules reach unrelated words: 수업은 deinflects to 수없다 (ㅅ-irregular, -(으)ㄴ) as well as
   to 수업, and the longer match sorts first; Yomitan's candidates are the same (checked with the bundle in Node).

## Result modes (`resultOutputMode`, default `group`)

- `group`: one entry per term + normalized reading + inflection chain; definitions from all dictionaries inside. The reading is normalized by the language's `readingNormalizer` (Chinese: NFC, lower case, without spaces, apostrophes, hyphens, colons and `//`), so "Xi'an" and "xī ān" readings of one word group together; Screenlate's `LanguageSupport.normalizeReading`.
- `merge`: entries sharing a sequence number in the main dictionary are merged (all spellings of a JMdict entry in one card).
- `split`: one entry per dictionary. `term`: group by term only.

## Entry sort order

primary reading match (from xref links) → longer original text → shorter text-processing chain → shorter inflection chain → more exact source matches → frequency order (only if a sort frequency dictionary is chosen) → dictionary order → score → longer term → term string → more definitions.

Screenlate's `YomitanSorter` follows it; the term string is compared by UTF-16 code units, not with a collator. The
engine cuts the list at the result limit before this sort, by its own order without dictionary priority, and reads
glossaries only for what it keeps. With more than one dictionary with definitions `DictionaryLookup` asks it for every
candidate (`engineLimit`) and cuts after the sort (question Q6 of the 2026-09-30 reviews). Measured on the emulator
with JMdict: one-kana and long queries found 5-32 candidates; uncut and cut lookups took the same few to tens of ms
(first, cold runs a few hundred ms either way), so no margin was needed. The search field and dictionary links are looked up whole (Yomitan's `termsFind` has no
scan length; the scan length applies to text under the cursor); Screenlate caps them at 100 characters
(`DictionaryLookup.lookupQuery`).

Definitions inside an entry: frequency order → dictionary order → score → headword indices → original order.

## Anki duplicate options (defaults)

- `checkForDuplicates`: true
- `duplicateScope`: `collection` | `deck` | `deck-root` (default `collection`)
- `duplicateScopeCheckAllModels`: false
- `duplicateBehavior`: `prevent` | `overwrite` | `new` (default `new`)

## Structured content

- Tags: `br ruby rt rp table thead tbody tfoot tr td th span div ol ul li details summary img a`.
- `data: {k: v}` becomes `data-sc-k="v"`; dictionary `styles.css` targets these attributes (Kolobok relies on it).
  Yomitan sets `dataset["sc" + K]` with the key's first letter in capitals, so only a key starting with a Latin letter
  gets the dash and kebab case (`fooBar` → `data-sc-foo-bar`); others follow `data-sc` as written (`data-sc日本`,
  `data-sc한국`, `data-scКлюч`, `data-sc1x`), and a key the dataset refuses (`foo-bar`) is skipped.
- `style` objects use camelCase CSS property names from a whitelist. `render.js` applies the same list (`STYLE_PROPERTIES`); other properties are dropped.
- Internal links look like `?query=…&wildcards=off&primary_reading=…`.
- Images: `path`, `width`/`height` + `sizeUnits` (`px`/`em`), `appearance: monochrome` (render as a mask in text color), `collapsible`/`collapsed`, `background`, `border`, `borderRadius`. Jitendex ships AVIF illustrations and HanaMinA SVG glyphs.
- Dictionary CSS is scoped as `[data-dictionary="<title>"] { … }` (CSS nesting). Kolobok's CSS uses nesting, `color-mix()` and `var(--font-size-no-units)`, `var(--text-color)`, `var(--fg)`.
- `scopeCss` drops statement at-rules (`@charset`, `@import`, `@namespace`) and leaves `@keyframes` blocks unprefixed.

## Glossary shapes for "only meanings" copying (`definition.js`)

Own heuristic (not Yomitan code; Yomitan has no such mode). `definition.js` holds what is the same for every language (glossary lists, numbered lines, `∥` examples, bilingual detection against Latin/Cyrillic/Greek letters); the markup of a language's dictionaries is in its own script, registered by language tag and picked by the page's `lang` (`definition-ja.js`: the names, brackets and marks below). A language without such a script gets glossary lists and numbered lines only. Checked with 猫/食べる from Jitendex, Kolobok, JMdict (en, ru, bundled), JMdict Extra, Warodai, Kenrowa, Wadoku, 新和英, 大辞林, 新明解, 大辞泉, 三省堂, 明鏡, 広辞苑, 岩波, 旺文社.

- JMdict-based structured content (Jitendex, Kolobok, JMdict exports): `data.content = "glossary"` lists hold the meanings; examples (`example-sentence`), `xref`, `extra-info`, `attribution` sit beside them. One sense = the list's `li` joined by "; ".
- Japanese monolingual structured content (大辞林, 新明解, 三省堂 style): `data.name = "語釈"` spans; `ルビ`/`ルビG` inside them are readings to skip; `語義番号`, `用例` beside.
- Everything else is plain text lines (明鏡, 広辞苑, 岩波, 大辞泉, 旺文社 come as text or unmarked structured content): numbered senses (1, 1., 1), (1), ①, ❶, ➀, ㊀), sub-senses ㋐–㋾, preamble before the first number (headword 【】, 〘〙 labels, 《》 etymology). Extra lines (examples 「」, →/⇒/☞ references, ◆■ notes, [補説], and 〔 notes in monolingual text) run until the next number.
- Bilingual text without numbers (Warodai, Kenrowa): one sense per glossary item, or per 〈…〉 label line (Kenrowa); Japanese-led lines are examples (`日本語∥перевод`, Warodai's `…を食べている питаться`). Bilingual = Latin/Cyrillic/Greek letters outnumber the looked-up language's letters (kanji and kana for Japanese) 2:1.
- Examples inside a sense line (大辞泉/広辞苑: `…食物にいう。宇津保物語「かの―・べ…」`): cut after the last 。 before the first quote containing ―, ━, ～ or 〜.
- Chinese and Korean dictionaries were not sampled; with no rules of their own they get glossary lists and numbered lines only.
