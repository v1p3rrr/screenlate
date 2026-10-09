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
- Licenses per dictionary: "Catalog candidates and licenses" below.

## Catalog candidates and licenses (checked 2026-10-09)

Licenses from the sources' own pages where they exist; "third parties" marks what only others say. The catalog links
to the publishers' downloads, as it does for Japanese; a few entries would have to be built and hosted by us.

Usable (open licenses):

| Language | Dictionary | Data license | Notes |
|---|---|---|---|
| all ~157 | Wiktionary (wty main, glossary, IPA) | CC BY-SA / GFDL (Wiktionary); converter MIT | Hugging Face, weekly, update index; gloss editions cs de el en simple es fr id it ja ko ku ms nl pl pt ru th tr vi zh |
| many | Wikipedia for Yomitan (MarvNC) | CC BY-SA (Wikipedia via DBpedia abstracts) | encyclopedia entries; DBpedia has no dumps after 2022-12 |
| zh | CC-CEDICT for Yomitan (MarvNC): terms, Hanzi, Canto | CC BY-SA 4.0 on mdbg.net (cc-cedict.org and the Yomitan release say 3.0); converter MIT | daily releases; both scripts |
| zh | wty zh→en (20 MB), Wiktionary Hanzi (MarvNC) | CC BY-SA | |
| ko | KRDICT (Lyroxide; 11 gloss languages incl. en, ru, ja), STDICT, OPENDICT, IPA, CC100 frequency | NIKL: "Creative Commons Attribution-Share Alike" for text (krdict's copyright page, no version; third parties: 2.0 KR); multimedia differs | the repo states no license of its own packaging; GitHub releases |
| ru | wty ru→en (25 MB zip, 273 MB installed), ru→ru (80 MB) | CC BY-SA | |
| ru | OpenRussian (ImenaOphelia converter, Apache-2.0) | CC BY-SA 4.0 | no releases: we would build and host it; its Zaliznyak meta dictionary is CC BY-NC 4.0 (risky list) |
| en | wty en→en (103 MB), en→ru (4.8 MB), en→other editions | CC BY-SA | |
| en–ru | Mueller 7, FreeDict eng-rus | GPL-2+ | no Yomitan builds found; we would convert and host, with the GPL source offer |
| de fr es it pt pl uk tr ar he la … | wty →en, →ru and other editions | CC BY-SA | sizes in "Dictionaries for other languages" |
| id | Indonesian–English (Kamata954) | Wiktionary-based, CC BY-SA | |
| frequency, ~40 languages | wordfreq (rspeer) | data CC BY-SA 4.0, code Apache-2.0 | frozen at 2021 data; large lists for ar bn ca zh cs nl en fi fr de he hi it ja mk nb pl pt ru es sv uk; we would build Yomitan frequency dictionaries and host them |

Coverage by gloss language (owner: at least English, preferably Russian, optionally the other interface languages).
wty sizes in MB from the Hugging Face tree of 2026-10-09 (`latest`); "g" is the glossary dictionary, built from
translation tables (short word-to-word translations through English Wiktionary), "-" none:

| Source | en | ru | es | fr | de | it | pt | pl | tr | vi | ja | ko | zh |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| en | 114.7 | 6.0 g4.0 | 1.8 g3.6 | 9.0 g3.8 | 2.0 g4.1 | 1.8 g2.7 | 1.2 g3.0 | 6.0 g3.6 | 3.4 g1.5 | 7.3 g1.0 | 5.2 g2.2 | 1.3 g1.5 | 6.3 g0.1 |
| zh | 23.0 g1.0 | g7.6 | g0.4 | 1.3 g0.4 | 0.1 g0.5 | 0.1 g0.4 | 0.1 g0.4 | 0.7 g0.4 | 0.0 g0.3 | 0.2 g0.3 | 2.4 g0.4 | 1.2 g0.4 | 9.7 |
| ko | 7.4 g0.6 | 0.4 g0.2 | 0.0 g0.2 | 1.1 g0.2 | 0.0 g0.2 | 0.1 g0.2 | 0.0 g0.2 | 0.3 g0.2 | 0.1 g0.1 | 0.1 g0.1 | 1.6 g0.1 | 4.9 | 10.0 g0.2 |
| ru | 14.2 g2.1 | 77.0 | 0.1 g1.0 | 10.5 g1.4 | 0.4 g1.3 | 0.1 g0.9 | 0.2 g0.6 | 2.0 g0.7 | 1.0 g0.5 | 3.0 g0.3 | 0.8 g0.4 | 1.0 g0.3 | 8.8 g0.4 |
| de | 15.2 g5.2 | 6.2 g2.1 | 0.3 g2.6 | 12.8 g4.2 | 40.3 | 0.7 g2.4 | 0.3 g1.5 | 3.2 g1.7 | 1.6 g1.1 | 0.1 g0.2 | 0.8 g0.6 | 0.3 g0.2 | 6.7 g0.3 |
| fr | 11.2 g4.0 | 1.9 g1.0 | 0.6 g1.8 | 79.8 | 1.1 g1.9 | 0.6 g2.1 | 0.3 g1.0 | 1.8 g0.8 | 6.5 g0.3 | 3.8 g0.2 | 1.1 g0.6 | 0.3 g0.3 | 3.1 g0.4 |
| es | 22.1 g0.6 | 1.2 g0.1 | 21.0 | 5.8 g0.4 | 0.3 g0.3 | 0.3 g0.3 | 0.5 g0.2 | 2.3 g0.1 | 0.2 g0.1 | 0.1 g0.0 | 0.7 g0.1 | 0.3 g0.0 | 3.2 g0.1 |
| it | 17.8 g1.4 | 1.1 g0.1 | 0.4 g0.3 | 21.8 g0.3 | 1.0 g0.4 | 10.5 | 0.3 g0.2 | 2.6 g0.1 | 5.5 g0.0 | 0.0 g0.0 | 0.9 g0.1 | 0.3 g0.0 | 2.6 g0.0 |
| pt | 11.4 g0.6 | 0.8 g0.2 | 0.4 g0.4 | 3.7 g0.4 | 0.2 g0.4 | 0.1 g0.3 | 12.0 | 0.6 g0.2 | 0.1 g0.1 | 0.0 g0.1 | 0.6 g0.2 | 0.1 g0.1 | 1.9 g0.1 |
| pl | 22.2 g1.2 | 1.6 g0.8 | 0.1 g0.7 | 1.7 g0.6 | 1.5 g0.7 | 0.1 g0.6 | 0.2 g0.2 | 24.4 | 0.8 g0.1 | 0.0 g0.0 | 0.4 g0.1 | 0.1 g0.1 | 1.6 g0.1 |
| uk | 7.7 | 3.5 | 0.0 | 2.5 | 0.4 | 0.0 | 0.0 | 2.4 | 0.5 | 0.0 | 0.2 | 0.1 | 0.6 |
| tr | 18.9 g0.4 | 1.3 g0.2 | 0.0 g0.1 | 0.3 g0.3 | 0.1 g0.3 | 0.0 g0.2 | 0.1 g0.1 | 0.5 g0.1 | 19.0 | 0.0 g0.0 | 0.1 g0.1 | g0.0 | 0.6 g0.0 |
| vi | 4.7 g0.2 | 0.1 g0.0 | 0.0 g0.0 | 1.5 g0.1 | 0.1 g0.0 | 0.0 g0.0 | 0.1 g0.0 | 0.1 g0.0 | 0.0 g0.0 | 4.3 | 0.6 g0.0 | 0.3 g0.0 | 1.9 g0.0 |
| ar | 6.4 | 0.8 | 0.0 | 1.4 | 0.1 | 0.0 | 0.1 | 0.8 | 0.3 | 0.0 | 0.1 | 0.1 | 0.4 |
| he | 3.4 | 0.6 | 0.2 | 0.1 | 0.0 | 0.0 | 0.0 | 0.1 | 0.1 | - | 0.1 | 0.0 | 0.3 |
| th | 2.9 g0.1 | 0.2 g0.0 | 0.0 g0.1 | 0.0 g0.0 | 0.0 g0.0 | 0.0 g0.0 | 0.0 g0.0 | 0.2 g0.0 | 0.0 g0.0 | 0.1 g0.0 | 0.2 g0.0 | g0.0 | 0.3 |
| id | 4.3 | 0.3 | 0.0 | 0.8 | 0.0 | 0.0 | 0.1 | 0.1 | 0.0 | 0.0 | 0.1 | 0.1 | 0.3 |

- Beyond Wiktionary: zh→en CC-CEDICT; ko→en, ru, es, fr, ja, zh, vi, th, id, ar, mn KRDICT; ru→en OpenRussian.
- Gaps for Russian glosses: zh→ru has only the wty glossary (7.6 MB); БКРС (bkrs.info, crowd-sourced, DSL
  downloads) is called free by third parties, but no license text was found (risky list). Thai, Vietnamese,
  Indonesian, Arabic and Hebrew have almost nothing into Russian.
- Gaps for English glosses: none among the languages above (Thai and Hebrew are small).

Risky (no license stated, non-commercial, unclear or copyrighted):

| Language | Dictionary | Why |
|---|---|---|
| yue | words.hk for Yomitan (MarvNC) | Non-Commercial Open Data License 1.0 (converter MIT): fine while the app is free, a problem for any paid or ad-supported version |
| yue | CantoDict, Canto CEDICT and the Migaku "Learn Cantonese" set | archived or repackaged data, no license stated |
| yue | Cifu frequency | license not found |
| zh | SUBTLEX-CH, BLCU BCC frequency, Sinica | research data, redistribution terms not found |
| zh | HSK levels (official word list, OCR'd) | facts list, no license stated |
| zh | Oxford, DrEye, Wenlin ABC, Tuttle, 现代汉语词典, 规范词典, 兩岸詞典, 漢語大詞典, MoeDict conversions, Kroll, Vogelsang | commercial dictionaries converted by users; MoeDict's text is © Taiwan's Ministry of Education (an open license for it not found) |
| ko | jarjumarvin KRDICT, Hanja and conjugation dictionaries, SpazzTL supplement | no license stated (KRDICT data itself is CC BY-SA) |
| ru | OpenRussian Zaliznyak meta dictionary | CC BY-NC 4.0 |
| en | Oxford Advanced Learner's, Macmillan, NOAD, Cambridge, Longman (Umbrella's folder) | commercial dictionaries |
| en–ja | 研究社 新英和大辞典, ライトハウス, Oxford thesaurus | commercial (Monokakido rips) |
| de es pt ro en | Migaku conversions: PONS, DUDEN, RAE, Priberam, Oxford, dexonline | commercial or unclear |
| th | LEXiTRON, Pleang Na Nakorn TH-TH | NECTEC terms not found (Language Grid lists it as non-profit research use); the other has none |
| vi | VNEDICT, Free Vietnamese Dictionary Project, stardict-vi, vntk dictionary, Chữ Nôm | licenses not checked yet; MediaFire uploads |
| ar | kaihouguide's language→Arabic set | self-ripped |
| many | Lingoes Vicon dictionaries | ripped from Lingoes |
| zh–ru | БКРС (bkrs.info) | crowd-sourced, called free by third parties; no license text found |
| many | Leipzig frequency lists (leipzig-to-yomitan) | downloads said to be CC BY, but the terms also say CC BY-NC and no commercial use without consent |

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
- Measured in the APK (2026-10-09, debug build, `text-recognition-chinese` and `-korean` 16.0.1 next to the Japanese
  one): +559 KB compressed for both. Korean adds `Kore_ctc` (lstm 796 KB, 500 KB compressed; conv model and label
  map 70 KB) and both add a few 13 KB engine configs (`taser_tflite_gocr{chinese,korean}_and_latin_*`); the Hani
  and Latin models are shared files, merged once. No native library changes (`libmlkit_google_ocr_pipeline.so`
  is common).
- Each script needs its own recognizer: the Japanese one runs the `Jpan` model. On the OCR test screen with the
  cloud blocked (emulator, ~1 s per image): the Japanese recognizer read simplified Chinese as 我作己祭吃対坂了 and
  学羽中文役有意思, and Korean as one garbage line (O1型子9世音円以合C); the Chinese and Korean recognizers read the
  samples correctly (one 壞 came out as 壊). Latin reads fine with the Japanese recognizer.
- Other on-device engines for the scripts ML Kit lacks: PaddleOCR PP-OCRv5 (Apache-2.0) script models Cyrillic
  ~7.5–8 MB, Arabic ~7.6–8, Devanagari ~7.6–7.9, Korean 13.4, ch/ja/en 16.6, detector 4.6 MB, runtimes ONNX Runtime,
  MNN, ncnn or Paddle Lite; Tesseract `tessdata_fast` (download/installed): rus ~1.4/3.7 MB, ukr 3.8, ara ~0.7/1.4,
  heb ~0.4/0.9, tha ~0.9/1.0. Both could be downloaded like catalog items.
- Cyrillic on device, costs (2026-10-09): Tesseract4Android 4.9.0 AAR 12.8 MB for four ABIs (about 3.2 MB per ABI
  in a per-ABI APK, ~10 MB in the universal one; Apache-2.0) plus `rus.traineddata` fast 3.9 MB (best 15.3 MB),
  `ukr` 3.8 MB as downloads. PaddleOCR through ONNX Runtime: `onnxruntime-android` 1.22.0 AAR 28.5 MB for four ABIs
  (about 7 MB per ABI; a reduced build is smaller) plus the detector 4.6 MB and the East Slavic recognizer
  (`eslav_PP-OCRv5_mobile_rec`) ~7.5 MB. ncnn or MNN builds of PP-OCR are smaller but not packaged. Native
  libraries cannot be downloaded later in a clean way, so the runtime goes into the APK and only the models can be
  catalog downloads. Recognition quality on real screenshots not compared yet.
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

### Russian (no transforms in Yomitan)

Same branch, wty ru→en (2026-10-04 build, 25 MB zip). Yomitan's Russian descriptor has only text processors
(capitalization, ё ↔ е, removing the stress mark U+0301); every inflected form comes from the dictionary.

- The dictionary: 1,492,900 rows, of which 1,432,414 are form-of rows (`["кошки","ко́шки","non-lemma","",0,
  [["ко́шка",["genitive singular"]],…]]`, 1.77 M form items over 990 K distinct forms) and 60,486 have definitions.
  Expressions carry no stress marks; readings do (`ко́шка`), and the popup shows them above the headword, which is
  useful for learners. Lemmas in form-of items are sometimes stressed (`ко́шка`), sometimes not; hoshidicts finds both,
  as it matches the reading too. Noise in the data: lemmas like `стать pf`, pre-reform `человѣкъ`, and pronoun tables
  that make "он" a form of вы, его, её, меня, мы, она, они.
- Size: hoshidicts imports it in 2.4 s (CLI) or 4.1 s (app) on the emulator, but it takes 273 MB on disk (en→en:
  293 MB from a 103 MB zip). Split by row kind: the definitions take 18 MB, the form-of rows 231 MB (blobs about
  135 B per row plus a 45 MB hash table). Storing form-of compactly at import (form → lemma and tags, in the record's
  "redirects" field or a side table of our own) would cut the dictionary several times.
- Lookups (harness and popup, cloud recognition read the Cyrillic): Кошками → кошка (instrumental plural), ежика →
  ёжик (ё/е), ко́шки → кошка (stress mark), стали → сталь and стать, людей → человек and люди, шел → идти, пошли →
  послать and пойти, лучше → лучше, хороший, хорошо. Candidates 0.06–0.5 ms, queries 0.03–1.2 ms; 3–46 ms per
  lookup in the app.
- Found:
  - Form-of is followed one level, as in Yomitan: ежика → ёжик shows ёжик's own senses, not "diminutive of ёж"
    (looking up ёжик directly shows ёж). Following a second level would be our own extension.
  - Only the first rule chain shows (кошки: "accusative plural inanimate"; genitive singular and nominative plural
    are lost); Yomitan lists every chain.
  - Homographs have no useful order without a frequency dictionary (послать before пойти).
  - Audio found nothing: the audio sources are the Japanese ones (JapanesePod101, the custom server); per-language
    sources are part of the settings design.

### Korean (2026-10-09, second session)

Same branch, `KoreanSupport` (letter resolution inside the spaced word, as Yomitan), KRDICT RU (ko→ru) and the CC100
Korean frequency list imported in the app, `testdata/ocr/ko-sample.png` (five sentences, Malgun Gothic).

- Cloud recognition read the Korean screenshot (5 paragraphs, 2.4 s); the Japanese ML Kit draft read one line.
- Popup: 먹었습니다 → 먹다 (chain "-았/었 « -(스)ㅂ니다", CC100 285), 싶어요 → 싶다 (-아/어요), 갔어요 → 가다
  (-았/었 « -아/어요), 읽고 → 읽다 (-고), 공부하고 → 공부하다 (-고) then 공부, 한국어를 → 한국어. The whole word
  is highlighted. Lookups take 21–53 ms in the app (candidates 9–18 ms of it).
- Yomitan's Korean rule names are the endings themselves (-았/었, -고), so they need no translation.
- The aim starts the lookup at the aimed syllable, as in Yomitan (Korean is excluded from its word scan
  resolution): aimed at 었 of 먹었습니다 the popup shows the ending -었-, aimed at 부 of 공부하고 it shows 부하 (負荷).
  Starting at the start of the spaced word would find 먹다 from any syllable, but no longer the parts of a compound.
- KRDICT orders homographs by its own number: 먹다¹ "go deaf" before 먹다² "eat" (the ★★★ marks the basic word).
- wty ko→en also has 387 K form-of rows (420 K form items; 47 MB installed of 57 MB).

### Audio per language (2026-10-09)

The app's own Commons searches (`AudioPages.linguaLibreSearch`, `wiktionarySearch`) run for ten common words per
language from the PC (`scripts/yomitan-language/tools/commons_audio.py` in the experiment branch; Commons answers
429 to fast series, so it waits 2 s between requests; each search 0.4–0.7 s):

| Language | Lingua Libre | Wiktionary (`Xx-term.ogg`) | Notes |
|---|---|---|---|
| en | 9/10 | 10/10 | accents in the title: En-us, En-uk, En-au, En-in, En-ca; "look up" has one En-au clip |
| ru | 6/10 | 10/10 | `Ru-кошка.ogg`, by the lemma without stress marks |
| ko | 5/10 | 7/10 | `Ko-먹다.ogg`; 공부하다 has none |
| zh | 2/10 with Q727694, 4/6 with Q9192 | 0/10 by characters | see below |

- Mandarin: Lingua Libre files it as `LL-Q9192 (cmn)-…` (Q727694 has one speaker); Cantonese as `LL-Q9186 (yue)`.
  Wiktionary's Mandarin clips are named by pinyin with tone marks (`Zh-péngyou.ogg`, `Zh-shuǐ.ogg`, `Zh-tiānqì.ogg`,
  `Zh-māo.oga`), some with tone numbers (`Cmn-shan1.flac`), dialects add a place (`Zh-Chengdu-mao1.ogg`). So the
  Wiktionary source must search Chinese by the reading in pinyin (wty readings look like "nǎo (nao³)", CC-CEDICT's
  have tone marks), not by the characters.
- Lingua Libre's pattern `-.*-term.wav` also matches compounds and phrases ending in the word: "kitty-cat",
  "she-cat", "summer-house", "акула-кошка", "транс-человек"; the first result may be one of them. Japanese never had
  hyphens, so it did not show. A title whose part before `-term.wav` still contains a hyphen after the speaker is
  ambiguous (speaker names can hold hyphens too); such titles should go last.
- Phrases: Lingua Libre has none for "look up"; Wiktionary has a few phrase clips.
- JapanesePod101, LanguagePod101 and Jisho are Japanese only; text to speech covers every language the phone has a
  voice for (played only, never put into notes).

### Compact form-of storage (2026-10-09)

Measured on the wty dictionaries with `scripts/yomitan-language/tools/formof_size.py` and `redirects_size.py`
(experiment branch). A side table of our own: sorted distinct forms front-coded in blocks of 16 (binary search over
block starts), per form a varint count and (lemma id, tag set id) varints, a lemma table and a tag set table.

| Dictionary | Form-of rows | Form items | Distinct forms | Lemmas | Tag sets | Compact table | Installed today |
|---|---|---|---|---|---|---|---|
| wty ru→en | 1,432,414 | 1,770,513 | 990,678 | 114,289 | 1,146 | 19.4 MB | 231 MB of 273 |
| wty ko→en | 387,390 | 420,361 | 358,814 | 45,422 | 823 | 5.8 MB | ~47 MB of 57 |
| wty zh→en | 157,318 | 168,057 | 142,800 | 112,096 | 306 | 2.9 MB | – |
| wty en→ru | 15,720 | 15,800 | 13,664 | 8,860 | 170 | 0.3 MB | – |

- ru→en: the form keys take 6.4 MB front-coded (20.9 MB raw), the entries 7.9 MB plus 4 MB of offsets, lemmas
  1.2 MB, tag sets 45 KB. About 12 times smaller than the rows; the whole dictionary would take about 40 MB
  instead of 273 MB.
- hoshidicts' "redirects" field instead (format ≥ 2): the record keeps the strings inline (length-prefixed lemma and
  every tag) and the index still holds the expression and the reading of every row (1.95 M keys for ru→en): about
  226 MB of records plus the hash table, no smaller than today. It would only save parsing the glossary per query.
- Tags: wty uses 654 distinct tag words in ru→en and 1,180 in ko→en (with noise like dialect and romanization
  names); the first 40 cover the grammar (plural, singular, masculine, instrumental, …, second-person, participle,
  perfective). Translating the grammatical ones is a closed list of about 100–150 words.
- None of the four dictionaries has a row mixing definitions and form-of items.
