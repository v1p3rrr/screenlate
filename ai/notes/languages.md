# Languages beyond Japanese — research for phase 8

Collected 2026-10-09 from Yomitan `master` (833409247c5e, 2026-10-09), wiktionary-to-yomitan (27b475262ffe,
2026-10-04, release metadata of 2026-10-04), the hoshidicts submodule (fcea33f98f94) and ML Kit's docs. Plan and open
questions: `plans/2026-10-09-more-languages.md`.

## What in Screenlate is Japanese today

- `Language` has one value, `JAPANESE`. `LanguageSupport` (`core/common/language`) already holds per-language rules:
  dock glyph, word separator, ML Kit script, sentence terminators and quote pairs, Anki markers, default audio
  sources, ISO 639-3 and Wikidata ids (Lingua Libre), `lang` tag, system fonts and `unicode-range`, font and
  translation samples, `lookupStart`, `wordStartOffset`, `spellingVariants`, `fromLatin` (romaji),
  `singleCharacterEntries` and `characterEntry` (kanji).
- Fixed to Japanese outside it: `OverlayController.language`, `SearchViewModel`, `AudioSettings` defaults and
  `AudioSettingsViewModel`, `AnkiSettingsViewModel` markers, `PopupAppearanceViewModel`,
  `TranslationSettingsViewModel`, `OcrTestViewModel`, `InstalledLanguages.SOURCE`, the "ja" first in
  `DictionariesViewModel`, the catalog (only `ja` sources), the bundled dictionaries.
- The page: `definition-ja.js` (markup of Japanese dictionaries), furigana and pitch graphs in `render-yomitan`,
  the "Only for Japanese text" font switch.
- ML Kit: only the bundled Japanese model (`text-recognition-japanese`), used for every `OcrScript`; it reads Latin
  too.

## hoshidicts: what is Japanese-only

- `Lookup::lookup` runs `text_processor::process` (NFKC, katakana ↔ hiragana, emphatic sequences, full-width
  alphanumerics, kanji variants, iteration marks …) and the Japanese `Deinflector` (Yomitan's Japanese transforms,
  hand-written in C++, 32-bit condition masks of Japanese parts of speech) on every prefix. There is no language
  parameter; the README says other languages "might need their own deinflector or adjustments to the lookup
  strategy". The `main-mit` branch is the same in this respect.
- Prefixes are shortened one code point at a time (Yomitan's "letter" search resolution).
- `query_raw` matches the expression or the reading exactly: no case folding, no diacritics removal.
- Form-of entries are not followed: the term record has a "redirects" field (format version ≥ 2), but the importer
  writes 0 and the query skips it. The glossary JSON keeps the `["lemma", ["rule", …]]` items as they are, so the
  renderer gets them raw.

## How Yomitan supports languages

- `ext/js/language/language-descriptors.js`: ~60 languages (`iso`, `iso639_3`, `name`, `exampleText`), each with
  optional:
  - `textPreprocessors`: variants tried for every substring, all combinations (decapitalize and capitalize first
    letter for cased scripts; diacritics removal for it, id, la, ro, sga, tl, grc; ru: ё → е and stress marks;
    de: ß ↔ ss; fr: apostrophe variants; uk: apostrophes and diacritics; Arabic script: diacritics, tatweel, hamza;
    ko: Hangul split into jamo; zh/yue: radical normalization; ja: the set hoshidicts ports);
  - `languageTransforms`: deinflection rules (suffix rewrites with part-of-speech conditions) for ar/arz, de, el, en,
    eo, es, eu, fr, ga, grc, ja, ka, ko, la, sga, sq, tl, uk, yi. Sizes: en 17 transforms, de 7, es 15, fr 9, uk 35,
    ja 77, ko 450 (on jamo). Russian, Chinese, Portuguese, Italian, Polish and most others have none;
  - `textPostprocessors` (ko: jamo back to syllables; yi), `isTextLookupWorthy` (ja, zh, uk), `readingNormalizer`
    (zh: pinyin).
- The language is a profile setting (`general.language`, default `ja`), one per profile; users switch profiles by
  hand or by conditions. Dictionaries are enabled per profile; `index.json` may name `sourceLanguage` and
  `targetLanguage`, and Yomitan does not filter by them.
- Scanning (`text-scanner.js`): with `scanResolution: word` the scan start moves back to the word start, except for
  ja, zh, yue and ko. Lookup (`translator.js` `_getNextSubstring`): `searchResolution: letter` drops one character at
  a time; `word` drops a whole trailing word, so "look up to" tries "look up to", "look up", "look" (phrases first).
- Dictionary deinflection (`_getDictionaryDeinflections`): a glossary item `["lemma", ["rule", …]]` marks the entry as
  an inflected form; Yomitan looks up the lemma, shows the rule chain, and drops such items from the definitions.
  This is how languages without transforms (Russian, Polish, …) still get deinflection.
- Docs: `docs/development/language-features.md`; dictionary list at yomitan.wiki/dictionaries.

## Dictionaries for other languages

- Wiktionary (wiktionary-to-yomitan, "wty", data from kaikki/wiktextract), hosted on Hugging Face
  `daxida/wty-release`, rebuilt about weekly. 157 source languages × 21 Wiktionary editions (gloss languages):
  cs, de, el, en, simple, es, fr, id, it, ja, ko, ku, ms, nl, pl, pt, ru, th, tr, vi, zh. Types: **main** (definitions,
  etymology, examples; inflected forms as separate entries `[lemma, [tag]]`), **glossary** (from translation
  sections, small), **ipa** (pronunciation). The catalog already uses wty for Japanese.
- Main sizes (2026-10-04): en→en 102.9 MB, en→ru 4.8 MB; ru→en 24.9, ru→ru 79.7; de→en 16.3, de→ru 4.8;
  fr→en 10.5, fr→ru 1.6; es→en 20.7, es→ru 1.0; it→en 16.4; pt→en 10.8; pl→en 21.0; uk→en 9.3, uk→ru 3.1;
  zh→en 20.1 (no zh→ru); ko→en 7.1, ko→ru 0.3; tr→en 16.7; ar→en 13.0; he→en 3.2; la→en 25.2. A small size means
  the gloss-language Wiktionary covers few words of that source language.
- Chinese: CC-CEDICT for Yomitan (MarvNC/cc-cedict-yomitan, CC BY-SA 3.0): terms with pinyin, a Hanzi dictionary
  (Yomitan's kanji format), Cantonese variants with Jyutping; words.hk for Cantonese.
- Korean: KRDICT/STDICT (Lyroxide/yomitan-ko-dic).
- Licenses of other candidates (e.g. English–Russian) are not checked yet.

## OCR

- Cloud recognition reads every script; the request names a language (`locale_context.language`), and the response
  has one `content_language` for the whole image (not per paragraph).
- ML Kit v2 scripts: Latin, Chinese, Devanagari, Japanese, Korean. No Cyrillic for Russian or Ukrainian, no Arabic,
  Hebrew or Thai: those languages have no on-device recognition, only cloud and app text.
- Models: bundled (`com.google.mlkit`) ~4 MB per script per ABI; unbundled through Google Play services
  (`com.google.android.gms`) ~260 KB, downloaded on first use, needs Play services.
- `TextLayout` hit testing knows horizontal, vertical and rotated lines; right-to-left lines (Arabic, Hebrew) are not
  handled.
