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
- wty main rows of inflected forms look like `["ran","","non-lemma","v",0,[["run",["past"]],…]]`: the glossary holds
  only `[lemma, [tags]]` items. zh→en uses traditional headwords; simplified spellings are form-of rows tagged
  "Simplified-Chinese". Readings are like "nǎo (nao³)".
- Chinese: CC-CEDICT for Yomitan (MarvNC/cc-cedict-yomitan; converter MIT, data CC BY-SA 3.0 per the CC-CEDICT wiki,
  some third parties say 4.0): terms with pinyin, a Hanzi dictionary (Yomitan's kanji format), Cantonese variants
  with Jyutping (CC-CEDICT Canto, CC-Canto); words.hk for Cantonese. `addTermEntry` stores each term under the
  traditional and, when different, the simplified headword, so one dictionary and one profile cover both scripts.
- Korean: KRDICT/STDICT (Lyroxide/yomitan-ko-dic releases): KO-EN KRDICT 15.6 MB (4.3 MB without examples), CC100
  frequency 1.85 MB, IPA 6.6 MB, STDICT, KRDICT for 11 gloss languages. No license stated in the repo; third parties
  say the source data is CC BY-SA 2.0 KR (unconfirmed).
- English–Russian outside Wiktionary: Mueller 7 and FreeDict eng-rus are GPL-2+.

## OCR

- Cloud recognition reads every script; the request names a language (`locale_context.language`), and the response
  has one `content_language` for the whole image (not per paragraph).
- ML Kit v2 scripts: Latin, Chinese, Devanagari, Japanese, Korean. No Cyrillic for Russian or Ukrainian, no Arabic,
  Hebrew or Thai: those languages have no on-device recognition, only cloud and app text.
- Models: bundled (`com.google.mlkit`) ~4 MB per script per ABI; unbundled through Google Play services
  (`com.google.android.gms`) ~260 KB, downloaded on first use, needs Play services (`ModuleInstallClient` installs
  with progress).
- The AARs (16.0.1, compressed): text-recognition 1.38 MB, chinese 2.04, korean 1.90, japanese 2.63, devanagari
  2.02. The models are assets, not per ABI. The Japanese AAR already holds `Hani_ctc/optical/lstm_model.fb`
  (891,872 B, the same file as in the Chinese AAR) and `Latn_ctc` (309,568 B); Korean adds `Kore_ctc` (795,904 B).
  So on-device Chinese costs about 0 MB more, Korean about 0.8 MB.
- Other on-device engines for the scripts ML Kit lacks: PaddleOCR PP-OCRv5 (Apache-2.0) script models Cyrillic
  ~7.5–8 MB, Arabic ~7.6–8, Devanagari ~7.6–7.9, Korean 13.4, ch/ja/en 16.6, detector 4.6 MB, runtimes ONNX Runtime,
  MNN, ncnn or Paddle Lite; Tesseract `tessdata_fast` (download/installed): rus ~1.4/3.7 MB, ukr 3.8, ara ~0.7/1.4,
  heb ~0.4/0.9, tha ~0.9/1.0. Both could be downloaded like catalog items.
- `TextLayout` hit testing knows horizontal, vertical and rotated lines; right-to-left lines (Arabic, Hebrew) are not
  handled.

## Experiment: Yomitan's language code over hoshidicts

Branch `experiment/languages` (not merged, may be thrown away). Japanese keeps the native hoshidicts path; other
languages go through Yomitan's own language code run in QuickJS, and hoshidicts only answers exact queries.

- Pieces:
  - QuickJS-ng v0.17.0 (MIT) as a submodule next to hoshidicts, built into the same native library
    (`quickjs.c`, `libregexp.c`, `libunicode.c`, `dtoa.c`). No `Intl`: a stub `Intl.Collator` is loaded first, as
    Yomitan's `Translator` constructor builds one. Calls come from a pool of threads, so every call starts with
    `JS_UpdateStackTop` (without it QuickJS's stack check failed lookups on IO threads).
  - Per-language bundles built with esbuild from a Yomitan checkout (`scripts/yomitan-language/build.mjs`): the
    `Translator` (not prepared, no database) plus one language's descriptor, text processors and transforms; the
    language list is cut to that language before bundling, as module-level tables of all languages otherwise cost
    22–30 MB of heap. Korean needs the npm packages `hangul-js` and `kanji-processor` (Yomitan's own build makes
    `lib/` files from them).
  - The script returns Yomitan's deinflection candidates (`_getAlgorithmDeinflections`: original text, transformed
    text, deinflected text, text processor steps, condition flags, inflection rule chains) for every substring;
    resolution "word" for languages with spaces, "letter" otherwise.
  - Native code queries each candidate once (exact expression or reading), filters by part-of-speech conditions
    (`conditions == 0 || conditions & flags(term rules)`, Yomitan's `_partOfSpeechToConditionFlagsMap`), strips
    `[lemma, [rules]]` glossary items and looks the lemmas up with the rules added to the chain (Yomitan's
    `_getDictionaryDeinflections`), drops entries left without definitions, keeps one result per headword (longest
    match), and sorts as Yomitan: matched length, text processor steps, chain length, exact match, frequency,
    dictionary order, score.
- Numbers (emulator x86_64): bundles en 48 KB (heap 0.85 MB), zh 43 KB (0.37 MB), ja 209 KB (2.7 MB), ko 270 KB
  (4.3 MB). One candidate call: en 0.2–1.5 ms, zh < 0.3 ms, ja 2–5 ms, ko 9–18 ms. Querying the candidates in
  hoshidicts: 0.1–4 ms (cold up to 15 ms). Native library in the APK: arm64 2.65 → 3.96 MB, armv7 2.11 → 3.10,
  x86_64 2.79 → 4.22 (about +1.3 MB per ABI). QuickJS bytecode (`JS_WriteObject`) is larger than the minified source.
- In the app popup (wty en→ru, en→en, zh→en): English phrases highlight whole ("looked up to"), form-of works ("ran"
  → "run"), Russian glosses show; Chinese simplified text finds the traditional headwords through form-of, pinyin
  shows as ruby.
- Found:
  - Inflection names come from Yomitan in English (ko has about 450); they would need translating like
    `JapaneseInflections`.
  - "Cats" ranks the name "Cat" above "cat", as Yomitan does (the capitalized form is the exact match).
  - Alternative forms with equal keys sort alphabetically; a frequency dictionary would fix the order.
  - Audio sources have nothing for phrases ("No audio found").
  - The Kotlin `YomitanSorter` sorts again after the engine (primary reading, matched length, steps, chain length,
    exact, frequency, dictionary index, score, expression length, expression, glossaries).
  - Form-of could be stored at import time: the record format has a "redirects" field the importer writes as 0;
    patching the importer would save parsing glossaries per query. Per-row part-of-speech filtering is not possible
    as is: terms are grouped by (expression, reading) with their rules merged.
  - hoshidicts stores an empty reading as the expression.
- Alternatives to QuickJS: `androidx.javascriptengine` (a sandboxed WebView process, asynchronous, needs a recent
  WebView); a Kotlin port with tables generated from Yomitan's JS and its test cases; the Rust crate `deinflector`
  (aramrw, aims at a one-to-one port of Yomitan's multi-language transformer, part of yomichan_rs, few languages
  done).
- Yomitan's `Translator` talks to its database through `findTermsBulk`, `findTermMetaBulk`, `findKanjiBulk`,
  `findKanjiMetaBulk`, `findTagMetaBulk`, `findTermsBySequenceBulk`, `findTermsExactBulk`, `getDictionaryInfo`; the
  experiment calls only its deinflection part and does the rest natively.
