# Yomitan behavior to match

Reference: `yomidevs/yomitan` `ext/js/language/translator.js`, `ext/data/schemas/options-schema.json`.

## Lookup

1. Take up to `scanLength` (16) characters from the cursor. Try prefixes from longest to shortest.
2. For each prefix, build text variants with preprocessors (width conversion, hiragana ↔ katakana, collapsing emphatic sequences, …), deinflect each variant with the language transforms (keeping the rule chain), post-process.
3. Query all candidates at once, matching term or reading exactly. Drop matches whose part-of-speech rules do not satisfy the deinflection conditions.

## Result modes (`resultOutputMode`, default `group`)

- `group`: one entry per term + normalized reading + inflection chain; definitions from all dictionaries inside.
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
- `style` objects use camelCase CSS property names from a whitelist. `render.js` applies the same list (`STYLE_PROPERTIES`); other properties are dropped.
- Internal links look like `?query=…&wildcards=off&primary_reading=…`.
- Images: `path`, `width`/`height` + `sizeUnits` (`px`/`em`), `appearance: monochrome` (render as a mask in text color), `collapsible`/`collapsed`, `background`, `border`, `borderRadius`. Jitendex ships AVIF illustrations and HanaMinA SVG glyphs.
- Dictionary CSS is scoped as `[data-dictionary="<title>"] { … }` (CSS nesting). Kolobok's CSS uses nesting, `color-mix()` and `var(--font-size-no-units)`, `var(--text-color)`, `var(--fg)`.
- `scopeCss` drops statement at-rules (`@charset`, `@import`, `@namespace`) and leaves `@keyframes` blocks unprefixed.

## Glossary shapes for "only meanings" copying (`definition.js`)

Own heuristic (not Yomitan code; Yomitan has no such mode). Checked with 猫/食べる from Jitendex, Kolobok, JMdict (en, ru, bundled), JMdict Extra, Warodai, Kenrowa, Wadoku, 新和英, 大辞林, 新明解, 大辞泉, 三省堂, 明鏡, 広辞苑, 岩波, 旺文社.

- JMdict-based structured content (Jitendex, Kolobok, JMdict exports): `data.content = "glossary"` lists hold the meanings; examples (`example-sentence`), `xref`, `extra-info`, `attribution` sit beside them. One sense = the list's `li` joined by "; ".
- Japanese monolingual structured content (大辞林, 新明解, 三省堂 style): `data.name = "語釈"` spans; `ルビ`/`ルビG` inside them are readings to skip; `語義番号`, `用例` beside.
- Everything else is plain text lines (明鏡, 広辞苑, 岩波, 大辞泉, 旺文社 come as text or unmarked structured content): numbered senses (1, 1., 1), (1), ①, ❶, ➀, ㊀), sub-senses ㋐–㋾, preamble before the first number (headword 【】, 〘〙 labels, 《》 etymology). Extra lines (examples 「」, →/⇒/☞ references, ◆■ notes, [補説], and 〔 notes in monolingual text) run until the next number.
- Bilingual text without numbers (Warodai, Kenrowa): one sense per glossary item, or per 〈…〉 label line (Kenrowa); Japanese-led lines are examples (`日本語∥перевод`, Warodai's `…を食べている питаться`). Bilingual = Latin/Cyrillic/Greek letters outnumber CJK letters 2:1.
- Examples inside a sense line (大辞泉/広辞苑: `…食物にいう。宇津保物語「かの―・べ…」`): cut after the last 。 before the first quote containing ―, ━, ～ or 〜.
- Chinese and Korean dictionaries were not sampled; they fall into the text rules.
