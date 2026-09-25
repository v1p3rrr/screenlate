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

Definitions inside an entry: frequency order → dictionary order → score → headword indices → original order.

## Anki duplicate options (defaults)

- `checkForDuplicates`: true
- `duplicateScope`: `collection` | `deck` | `deck-root` (default `collection`)
- `duplicateScopeCheckAllModels`: false
- `duplicateBehavior`: `prevent` | `overwrite` | `new` (default `new`)

## Structured content

- Tags: `br ruby rt rp table thead tbody tfoot tr td th span div ol ul li details summary img a`.
- `data: {k: v}` becomes `data-sc-k="v"`; dictionary `styles.css` targets these attributes (Kolobok relies on it).
- `style` objects use camelCase CSS property names from a whitelist.
- Internal links look like `?query=…&wildcards=off&primary_reading=…`.
- Images: `path`, `width`/`height` + `sizeUnits` (`px`/`em`), `appearance: monochrome` (render as a mask in text color), `collapsible`/`collapsed`, `background`, `border`, `borderRadius`. Jitendex ships AVIF illustrations and HanaMinA SVG glyphs.
- Dictionary CSS is scoped as `[data-dictionary="<title>"] { … }` (CSS nesting). Kolobok's CSS uses nesting, `color-mix()` and `var(--font-size-no-units)`, `var(--text-color)`, `var(--fg)`.
