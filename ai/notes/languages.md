# Languages beyond Japanese — research for phase 8

Collected 2026-10-09 from Yomitan `master` (833409247c5e, 2026-10-09), wiktionary-to-yomitan (27b475262ffe,
2026-10-04, release metadata of 2026-10-04), the hoshidicts submodule (fcea33f98f94) and ML Kit's docs. Plan and open
questions: `plans/2026-10-09-more-languages.md`.

## Installation readiness and reactivation (2026-10-11)

- A language still activates as soon as setup is confirmed. Selected installed dictionaries are enabled at the
  same time, including a disabled copy kept when the language was turned off; they are not downloaded again.
- `overlay.install.LanguageInstallation` is shared by Home, Search and the overlay. It checks complete dictionary
  files, enabled mandatory categories, exact OCR model versions and the recognition mode. Main dictionaries can be
  user imports or shared dictionaries; other categories match their catalog entries. App text needs no OCR model.
- Home shows the active language's readiness, device OCR capability, remaining optional downloads and failures.
  Search waits for mandatory dictionaries and observes installation changes, searching the same query when ready.
  A scan checks readiness before app-text reads or capture, then shows a message and a queue link when blocked.
- Catalog downloads keep source language and entry IDs in WorkManager tags, independent of progress names; old
  tasks fall back to catalog/installed titles and the bundled source. A paused legacy bundled install also belongs
  to the bundled language. A newer retry supersedes the failed task of the same entry in the status count.
- Search result identity includes the language. A switch closes the old `PopupNotes` session, clearing cached
  Anki status, remembered note IDs, chosen clips, audio menus and auto-play. Late cache/audio answers cannot put
  values back into a closed session. The cache is also keyed by language as a second guard.

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
- ML Kit: the bundled Latin, Japanese, Chinese and Korean models, one per `OcrScript` that has one (since
  2026-10-17 Latin-script languages use the Latin model, not the Japanese one); other scripts need a downloaded
  model (`notes/ocr-engines.md`, plan `2026-10-17-ocr-engines.md`).

## hoshidicts: what is Japanese-only

- `Lookup::lookup` runs `text_processor::process` (NFKC, katakana ↔ hiragana, emphatic sequences, full-width
  alphanumerics, kanji variants, iteration marks …) and the Japanese `Deinflector` (Yomitan's Japanese transforms,
  hand-written in C++, 32-bit condition masks of Japanese parts of speech) on every prefix. There is no language
  parameter; the README says other languages "might need their own deinflector or adjustments to the lookup
  strategy". The `main-mit` branch is the same in this respect.
- Prefixes are shortened one code point at a time (Yomitan's "letter" search resolution).
- `query_raw` matches the expression or the reading exactly: no case folding, no diacritics removal.
- Form-of entries are not followed: the term record has a "redirects" field (format version ≥ 2), but the importer
  writes 0 and the query skips it. The glossary JSON keeps the `["lemma", ["rule", …]]` items as they are. Since
  2026-10-15 the JNI bridge follows them after hoshidicts' Japanese lookup (`form_of.cpp`, see
  `dictionary-engine.md`); before, the popup drew them as empty items with the "non-lemma" tag (and as an empty
  second bullet in Jitendex's and Kolobok's redirect rows).

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

### Yomitan's languages and our status (master e7334a9, 2026-10-16)

60 languages plus "Any / Unknown" (`xxx`: lookup as written, no language rules). T: Yomitan has transforms.

- Done on `languages/main`: ja T, en T, zh, ko T, es T, fr T, de T, it, pt. On branches (group 3): ru, pl, tr, vi, lv.
- Group 4 candidates Yomitan describes: hi, ar T (in the plan from the start, not in request 51), id, tl T (Filipino),
  th, uk T, nl, el T, cs, hy, be, ka T, sh (Serbian is Serbo-Croatian there and in English Wiktionary: one language,
  four standards, Cyrillic and Latin).
- Candidates Yomitan lacks (bn, ms, kk, ky) have wty dictionaries from English Wiktionary: ms→en 1.7 MB, kk→en
  1.7, bn→en 1.7, ky→en 0.6; into Russian kk 0.8, ky 0.3, ms and bn 0.1 (for scale: id→en 3.9, hi→en 5.6, de→en
  16.3). They work like group 3: form-of rows plus the longest matching start of the word (Kazakh and Kyrgyz like
  Turkish). Paddle's `cyrillic` recognizer has every Kazakh and Kyrgyz letter (ә ғ қ ң ө ұ ү һ і), `eslav` does not;
  Bengali has no ML Kit or Paddle model (Tesseract `ben`, cloud).
- The rest, by speakers: arz T (Egyptian Arabic, spoken variety; Wiktionary's own language, same script), yue, fa,
  kn, ro, km, hu, sv, he, bg, sq T, lo, da, fi, no, mn, et, cy, eu T, yi T, mt, aii, ga T, br, gd, haw, gv, kw;
  constructed eo T, tok; historical la T, grc T, sga T.
- PP-OCRv5 mobile recognizers on Hugging Face (PaddlePaddle): main (zh/ja/en), en, latin, eslav, cyrillic, korean,
  el, th, arabic, devanagari, ta, te.

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
- English–Russian outside Wiktionary: Mueller 7 is GPL-2+; FreeDict eng-rus was too before WikDict regenerated it
  (the 2025.11.23 edition is CC BY-SA 3.0, see "Converted dictionaries").
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
- FreeDict (checked 2026-10-09, `freedict.org/freedict-database.json`): pairs into Russian, all generated by WikDict
  (Karl Bartel, from Wiktionary translation tables of several editions, CC BY-SA; TEI, dictd, StarDict, slob, no
  Yomitan builds): zho-rus 156,590 headwords, eng-rus 62,181, pol-rus 28,923, deu-rus 26,809, fra-rus 24,739,
  ita-rus 14,137, ell-rus 14,709, nld-rus, ces-rus, cat-rus, swe-rus, spa-rus 11,982; jpn-rus is a stub; nothing
  from Portuguese, Turkish, Vietnamese or Korean into Russian. Word-to-word translations like the wty glossaries,
  possibly larger; we would convert and host them.
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
- PP-OCRv5 recognizers (2026-10-09, PaddleOCR's multi-language page): korean, latin (~47 languages; its character
  list has no Vietnamese letters with tone marks such as ạ, ấ, ộ, checked 2026-10-16), eslav (ru, be, uk), cyrillic
  (~33 languages incl. bg, sr, mn, kk), arabic (ar, fa, ur, ug, ps, ku, sd), devanagari, th, el, te, ta, en. None for Hebrew, Georgian, Armenian, Khmer, Lao, Syriac or Kannada; PP-OCRv6
  exists, its languages not checked. Yomitan languages whose script neither ML Kit nor Paddle reads: he, yi, ka, hy,
  km, lo, kn, aii (Tesseract has all of them).
- Prior art: PlayTranslate (an Android screen translator) runs PP-OCRv5 on MNN: the detector is bundled in its APK,
  recognizer packs (~8 MB each, Apache-2.0; CJK 16.6, Korean 13.4) are downloaded from Hugging Face
  (`playtranslate/ocr-models`) and checked against pinned SHA-256 hashes. MNN (Apache-2.0) is a smaller runtime than
  ONNX Runtime; its library size not measured yet.
- Google Play forbids downloading executable code (`.so`, dex) from outside Play; models are data and may be
  downloaded. So an OCR runtime goes into the APK, its models are downloads.
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
| es | 9/10 | 4/10 | measured 2026-10-16 with the app's queries (`Q1321 (spa)`, `Es-`), as the rows below |
| fr | 8/10 | 8/10 | `Q150 (fra)`; Lingua Libre has 20+ speakers for common words |
| de | 9/10 | 8/10 | `Q188 (deu)` |
| it | 9/10 | 9/10 | `Q652 (ita)` |
| pt | 8/10 | 3/10 | `Q5146 (por)` |
| pl | 4/10 | 10/10 | `Q809 (pol)` |
| tr | 8/10 | 1/10 | `Q256 (tur)` |
| vi | 8/10 | 0/10 | `Q9199 (vie)`, one-syllable words |
| lv | 0/10 | 1/10 | `Q9078 (lav)`: text to speech only, in practice |

- Mandarin: Lingua Libre files it as `LL-Q9192 (cmn)-…` (Q727694 has one speaker); Cantonese as `LL-Q9186 (yue)`.
  Wiktionary's Mandarin clips are named by pinyin with tone marks (`Zh-péngyou.ogg`, `Zh-shuǐ.ogg`, `Zh-tiānqì.ogg`,
  `Zh-māo.oga`), some with tone numbers (`Cmn-shan1.flac`), dialects add a place (`Zh-Chengdu-mao1.ogg`). So the
  Wiktionary source must search Chinese by the reading in pinyin (wty readings look like "nǎo (nao³)", CC-CEDICT's
  have tone marks), not by the characters.
- Measured again 2026-10-15 for 30 common words (`<scratchpad>/zh/audio/measure.py`, not kept): Lingua Libre
  `LL-Q9192 (cmn)` by characters 18/30 (Traditional 書 has its own file), Wiktionary by tone-marked pinyin without
  spaces (`Zh-péngyou.ogg`) 25/30, Wiktionary by characters 1/30 (a redirect `Zh-北京` → `Zh-Beijing.ogg`); both
  sources together 28/30 (电脑 has none). Commons title shapes: about 5,300 `Zh-<pinyin>.ogg/oga`, about 140 with a space
  (`Zh nǐ hǎo.ogg`, dialect samples), 4 `Cmn-<numbered>.flac`, a few `Zh-cmn-<characters>.opus` and Cantonese
  `Zh-yue-…`, which the 2-letter region group of the Wiktionary pattern does not match. A case-insensitive regex
  (`/…/i`) found nothing more. So Chinese defaults to Wiktionary first (`LanguageSupport.recordingTitle` gives the
  pinyin), then Lingua Libre; text to speech reads the characters (`speechText`), as a voice reads pinyin as Latin.
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

### Inflection names and tags: volume (2026-10-09)

Counted in Yomitan's `ext/js/language/*/*transforms.js` (commit e7334a9) and the form-of items of nine wty
dictionaries (en→ru; ru, ko, zh, es, de, fr, it, pt →en).

- Yomitan rules: 17 of its ~55 languages have transforms. Leaving out Japanese (translated already: 99 names, 98
  descriptions), Korean (450 names that are endings) and affix names (`-able`, Tagalog `-an`): 284 names and 198
  descriptions (1,164 words). Per language: en 14 names / 17 descriptions, es 15/15, fr 9/9, de 5/4, uk 35/35,
  ar 23/50, sq 31/31, eu 75/0 (case combinations), grc 21/0, sga 15/15, eo 7/7, yi 6/6, la 4/4, ka 3/3, ga 1/1,
  tl 20/1. zh, it, pt, ru, pl, tr, vi have no transforms: their forms come from the dictionaries.
- By the order of languages: group 1 (en, zh, ko) 14 names and 17 descriptions; group 2 (es, fr, de, it, pt) 29 and
  28; group 3 (ru, pl, tr, vi) none.
- wty tags: 104 base words (after splitting `nominative/accusative`) cover 99% of the form-of items in each of the
  nine dictionaries (ru 37 words, de 53, fr 26, it 25, pt 28, ko 29, zh 19); about 25 of them are names of dialects,
  scripts and romanizations (Cantonese, Hokkien, Simplified-Chinese, Yale, hanja). The last 1% is a long tail of
  noise: es→en alone has 9,712 distinct tag words, mostly text that leaked into tags.
- wty en→ru revision 2026.10.11 (checked 2026-10-14): 13,664 form-of rows, each item with one tag: plural 7,728,
  gerund 2,614, third-person singular 2,598, past participle 2,592, past 96, plus a few "redirected from …"
  (spelling redirects such as learnt → learn). The odd "past past participle simple and" seen on the emulator on
  2026-10-10 came from an earlier revision; the current data has no "simple" or "and" tags. Regular past forms carry
  only "past participle" (walked → walk), which is wty's data, not our order. The tags of an item are shown in the
  dictionary's order, and Yomitan's own rule ("past" for -ed) is a separate chain.
- "walke" before "walk" for "walked": with no frequency dictionary Yomitan sorts by the matched length and then the
  term length, so the candidate "walke" (from the rule -d) that some dictionary has comes before "walk". A frequency
  sort dictionary puts "walk" first; the planned wordfreq dictionary is set as the language's sort dictionary.
- Total for groups 1 and 2: about 200 strings (45 names, 45 descriptions, ~105 tag words), as many as the Japanese
  names and descriptions translated today; all languages: about 630 strings.

Second form-of step, wty ru→en (`second_level.py`, stress marks removed from lemmas): of 80,860 lemmas named in
form-of items, 53,316 have definitions (40,901 of them are also forms of something), 26,273 are only forms (dead
ends after one step) and 1,271 are missing. Of 991,988 surface forms, 327,148 (a third) find no entry with one step:
mostly participle forms (шокирующее → шокирующий → шокировать, погребающие, сталкивающемуся). Entries over all forms:
690 K with one step, 1.34 M with two (noise: кошки → кот, aspect partners принуждали → принудить, pronoun tables),
1.01 M with a second step only from lemmas without definitions, which finds 315,469 of the 327,148 dead ends and
leaves every form that already finds something as it was.

## Chinese on `languages/zh` (2026-10-14/15)

- Scripts: `scripts/chinese-scripts/build.py` builds `ChineseScriptTable.kt` from Unihan (variants, GB 2312, the 2013
  general standard, Big5) and CC-CEDICT's headword pairs (OpenCC is not on the allowed downloads). A character is
  Simplified only when no Traditional spelling in CC-CEDICT uses it and it replaces a Traditional character there or
  Unihan lists it in the mainland sets but not Big5; Traditional only the other way round; shared characters (后, 里,
  着) are in neither. The other script's forms are ordered by how many CC-CEDICT words use them (头发 → 頭髮, not 頭發).
- Pinyin over characters: `reading-zh.js` splits a tone-marked reading into syllables by a regex of initials and
  finals (plus hng, hm, ng, m, n and erhua r) and gives each syllable a tone (5 = neutral); where the syllables do not
  match the Han characters one to one (letters in the word, odd readings) the word gets one ruby. 儿 takes the erhua r.
- Typed pinyin: the engine cannot search readings in Latin letters, so the import writes a `ReadingIndex` next to the
  dictionary: 64-bit hashes of toneless keys (`ChinesePinyin.key`, "ni3hao3", "nǐhǎo" and "nihao" give the same key),
  each with its words, the likely common ones first (a character in more of the dictionary's words counts as more
  common, a word as common as its rarest character). The banks are read on several threads; the file is mapped.
  `readingMatch` ranks exact tones first.

## Korean on `languages/ko` (2026-10-15)

- KRDICT (Lyroxide 1.0.0): index titles "KRDICT EN", "KRDICT RU", … (the variants with and without examples share
  them), `targetLanguage` "ko" and no `sourceLanguage`, so an index naming only its target no longer sets the gloss
  language (`indexTargetLanguage`). KO-EN: 85,415 headwords, 32 K of them hanja spellings, 6 K phrases; parts of speech
  v, adj or none. Hanja headwords have no reading (the Hangul is only in the glossary), so audio finds nothing for them;
  Wiktionary's form-of entry gives the Hangul word next to it. 496 entries hold `&quot;` and `&apos;` as text.
- The hanja dictionary: `scripts/hanja/unihan_hanja.py` reads Unihan 17.0.0 (`kHangul` "락:0E 낙:0", where E marks a
  reading on the 2007 education list; `kDefinition`, `kTotalStrokes`; tags 교육용 and 인명용 from
  `kKoreanEducationHanja` and `kKoreanName`), readings on the education list first, 8,500 characters in 169 kB;
  reproducible zip. It ships in
  `dictionary/api/src/main/assets/catalog/` and installs through `asset:///` (no network constraint on the job).
- Wiktionary ko-en: 387 K form-of rows with tags like "formal past indicative", "hanja form", "hangeul"; about 1,000
  tags carry leftover gloss after two spaces (cut by `FormOfTags.clean`).
- The device recognizer does not read hanja (`ocr-engines.md`).

## Converted dictionaries: FreeDict and OpenRussian (2026-10-16)

- FreeDict pairs into Russian (edition 2025.11.23, all by WikDict, CC BY-SA 3.0 Unported per the TEI header):
  `scripts/freedict/freedict_yomitan.py` reads the TEI (`entry/form/orth`, `pron`, `gramGrp/pos`, `gen`,
  `sense/cit[@type=trans]/quote`, a nested `sense/def` as the source-language definition of the sense) and writes
  wty-like structured content: per sense a `sense-label` div with the definition, then a `glossary` list. Chinese
  `pron` holds the pinyin as `-{-{xīnyōu}-}-`. Archive sizes: de 1.7 MB, fr 0.9, it 0.7, es 0.7, pl 1.5, en 4.7,
  zh 5.5.
- Quality: zh→ru has 74% of its senses as lists of single Russian words in alphabetical order that mix unrelated
  meanings, and nearly every entry tagged as a noun; en→ru 10% such senses; the others about 1%. Senses with a
  source-language definition above them: de and pl 100%, en 96%, it 65%, es 58%, fr 32% (question 18 in the plan).
- OpenRussian: the official SQL dump URL (`api.openrussian.org/downloads/openrussian-sql.zip`, from openrussian-cli's
  Makefile) returns 404 "Route not found"; the TogetherDB database the site's GitHub README points at
  (`app.togetherdb.com/db/o9puugtgtauo1ih5/russian3/words`) loads with "Request failed". The CSVs in
  `github.com/Badestrand/russian-dictionary` (the site's own backup, export of 2020-09-09, CC BY-SA 4.0) are what the
  converter reads; openrussian-cli (git.fmsbw.de) keeps a copy of the SQL dump from 2021-04-20 (question 19).
- The CSVs: nouns 26,982 rows, verbs 14,871, adjectives 11,941, others 5,031 (one row there has a tab inside a
  translation); English translations for 46 K words, German for 57 K. Stress is an apostrophe after the vowel,
  secondary stress a backtick; one bare form even holds U+0300. Table cells hold alternatives (`зверя'ми//зверьми'`,
  `до'лжной,(до'лжною)`, `но'вы / новы'`), an asterisk before rare forms, `—` for none, and notes or junk
  (`застону'//_устар._застона'ю`, `взъе'лась$II(…`, `серёг=>`, `[[са'мый]] [[лени'вый]]`); the converter drops a cell
  with any symbol besides letters, stress marks, separators, the asterisk and brackets. German translations bracket
  asides with box corners (`└ seinen / ihren┘`, about 1,100 times), turned into parentheses. Gender `both` (28 nouns)
  gets both tags; aspect `both` (567 verbs) gives "present/future" forms.
- Result (`scripts/openrussian/openrussian_yomitan.py`): ru→en 45,968 words and 440,837 form-of rows (597 K form items),
  6.7 MB; ru→de 56,901 words, 517,227 form-of rows, 8.2 MB. Form-of lemmas are stressed (`ко́шка`), so homographs
  resolve to the right one (`за́мка` → `за́мок`); forms whose stress differs get separate rows (`руки́`
  genitive singular, `ру́ки` nominative plural). Verbs get their aspect partner as a last definition
  ("perfective: сказать").

## Group 3: Polish, Turkish, Latvian, Vietnamese, Russian (2026-10-16)

- Yomitan has no transforms for pl, tr, lv, vi or ru; their bundles carry text processors only, so inflected forms
  come from the dictionaries' form-of rows. Yomitan's ru processors drop stress marks (ко́шка → кошка) and turn ё
  into е and back (еж → ёж); vi's move the tone mark between the old and new places (hoà ↔ hòa).
- Turkish case: Yomitan's `decapitalize` and `capitalizeFirstLetter` use JavaScript's case mapping, which turns I
  into i and i into I. `scripts/yomitan-language/glue.js` swaps in Turkish versions for `tr` (`caseProcessors`):
  I ↔ ı, İ ↔ i. `glue.test.mjs` runs the built bundles under node:vm.
- wty pl-en, tr-en, lv-en: lemma rows have no reading. Sample forms: koty → kot, czytaliśmy → czytać, oknie → okno;
  gittik → gitmek, kitabı → kitap, okuyorum → okumak (İstanbul and ışık are lemmas); grāmatu → grāmata, lasīju →
  lasīt, jālasa → lasīt ("indicative debitive"). lv-en's tags add debitive (1,822 items), renarrative (2,716) and
  "form" after a word ("adverbial form", "rare form"), now silent like "degree".
- Latvian writes years as ordinals ("1918. gada 18. novembrī"), so a number of any length followed by a period and a
  lowercase word counts as an ordinal in every language with `ordinalPeriod` (plan question 24).
- wty vi-en: lemma rows have no reading. "cái" is a non-lemma row with 924 items tagged `classifier`
  (cái → bàn, ghế, áo, …); `VietnameseSupport.ignoredFormTags` drops results reached only through them. Many
  syllables are also non-lemma rows pointing to Hán-Nôm characters (`Hán-Nôm` tag: xa → 車, 佘, 奢; học → 𭓕), kept
  and labelled (plan questions 21, 22). Multi-syllable words are lemmas of their own (học sinh, sinh viên); the
  lookup starts one and two syllables before the aim (`earlierStartOffset`, `fromEarlierStarts`) and each
  result's `start` says where its match begins. wty-vi-ipa tags transcriptions with cities (Hanoi, Hue, Saigon,
  Vinh), not countries, so Vietnamese has no transcription regions.
- wty ru-en: lemma rows put the stressed form in the reading (кошка read ко́шка; one-syllable words and words with ё
  have none). Form-of items point to the lemma both unstressed and stressed (кошки → кошка and → ко́шка, in separate
  rows), and some lemmas are themselves forms (кошка → кот "feminine"). The lookup page shows a reading that only
  adds U+0300 or U+0301 in the word's place (`reading-ru.js`); a different reading (еж read ёж) stays ruby.
- Catalog: lv-ru (Wiktionary) is 520,903 bytes, under the 0.5 MiB threshold (plan question 20). The transcription
  dictionaries of Turkish (303 KB) and Latvian (33 KB) are under it too, so neither language has one in the catalog.

## Group 4a research (2026-10-18)

Dutch (nl), Czech (cs), Indonesian (id), Malay (ms), Tagalog (tl), Serbo-Croatian (sh), Ukrainian (uk), Belarusian (be),
Greek (el). Sources: Yomitan e7334a9 (`ext/js/language/`), the wty tree on Hugging Face (`latest/dict`, listed and
downloaded 2026-10-18, sizes in MB of 10^6 bytes), wordfreq 912caf6, the Paddle and tessdata pins of
`scripts/ocr-models/`, circle-flags 379588b, the fonts of `overlay/src/main/assets/fonts/catalog.json` (cmap and GSUB
read from the files), the OCR comparison of `ocr-engines.md` (2026-10-16/17). Nothing measured on a device.

### Yomitan at e7334a9

| lang | text processors | transforms |
|---|---|---|
| nl | capitalization | none |
| cs | capitalization | none |
| id | capitalization, `removeAlphabeticDiacritics` (NFD, drops U+0300–U+036F) | none |
| ms | no descriptor (like kk, ky: form-of rows and the longest start) | none |
| tl | capitalization, diacritics removal | `tagalogTransforms`: 3 conditions, 60 transforms, 635 rules; one transform has a description, the others are affix names (`-an`, `mag-`, `pag-…-an`) |
| sh (iso639_3 `hbs`) | capitalization, `removeSerboCroatianAccentMarks` (combining marks after a e i o u r), `addSerboCroatianDiacritics` (c → č/ć, z → ž, s → š, dj → đ in every combination, capped at `MAX_PROCESS_VARIANTS` 4096) | none |
| uk | capitalization, `removeUkrainianDiacritics` (U+0301), `ukrainianApostropheVariants` (input ' ‘ ’ ʼ \` ´ → ' ’ ʼ); `isTextLookupWorthy`: contains Cyrillic | `ukrainianTransforms`: 6 conditions, 35 transforms, 3,023 rules, all 35 described |
| be | capitalization (no stress removal) | none |
| el | capitalization, `removeDoubleAcuteAccents` | `modernGreekTransforms`: 1 condition, 1 transform (ξανα-), 6 rules |

- `addSerboCroatianDiacritics` grows as 3^n per substring for c and 2^n for s, z: a word like "srecica" gives 18
  variants, and every substring of the scanned text is expanded. Under QuickJS this is the costliest processor so far;
  the cap stops it at 4096 per substring. To be timed on the emulator with long Latin lines.
- Yomitan's text processors change the text, not the headwords: they do not help where the headwords carry marks
  (Belarusian, below).

### Dictionaries (wty)

Counting: distinct headwords of rows whose glossary is not only `[lemma, [tags]]` items (the catalog threshold's "words
with their own meaning", plan row "Catalog threshold"); ✗ under 1,500; – no file. Cells: archive MB, words.

Main dictionaries:

| from | en | ru | de | es | fr | it | ja | ko | pl | pt | tr | vi | zh |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| nl | 8.6 67.7k | 0.6 11.5k | 0.1 1.0k ✗ | 0.09 1.8k | 2.5 30.8k | 0.1 2.9k | 0.3 6.0k | 0.06 1.6k | 0.2 5.5k | 0.1 1.9k | 0.09 2.5k | 0.1 1.1k ✗ | 0.9 9.7k |
| cs | 7.6 47.5k | 1.0 15.1k | 2.5 9.2k | 0.04 698 ✗ | 5.6 25.7k | 0.06 1.1k ✗ | 0.1 2.8k | 0.08 2.2k | 1.6 24.3k | 0.09 1.5k | 0.2 8.0k | 0.03 280 ✗ | 0.6 13.0k |
| id | 3.9 31.3k | 0.2 4.4k | 0.02 234 ✗ | 0.02 216 ✗ | 0.7 13.9k | 0.01 135 ✗ | 0.09 1.1k ✗ | 0.05 1.2k ✗ | 0.07 1.7k | 0.05 1.1k ✗ | 0.03 683 ✗ | 0.02 100 ✗ | 0.2 3.1k |
| ms | 1.7 10.0k | 0.1 2.1k | – | 0.01 106 ✗ | 0.07 1.6k | – | 0.07 981 ✗ | 0.04 765 ✗ | 0.03 751 ✗ | 0.03 687 ✗ | 0.02 612 ✗ | 0.02 140 ✗ | 0.5 14.5k |
| tl | 4.3 28.0k | 0.1 2.2k | 0.03 230 ✗ | 0.01 176 ✗ | 0.08 1.9k | – | 0.04 535 ✗ | 0.02 510 ✗ | 0.05 1.2k ✗ | 0.03 659 ✗ | 0.05 1.9k | 0.02 190 ✗ | 0.2 1.8k |
| sh | 12.0 56.7k | 0.02 244 ✗ | 0.01 67 ✗ | 0.03 489 ✗ | 0.06 1.0k ✗ | – | 0.2 2.3k | – | – | 0.03 567 ✗ | – | 0.02 204 ✗ | 0.6 9.3k |
| uk | 9.8 29.9k | 3.2 36.0k | 0.5 2.4k | 0.02 243 ✗ | 2.9 22.6k | 0.02 364 ✗ | 0.1 1.5k | 0.1 3.1k | 1.8 33.1k | 0.04 688 ✗ | 0.3 10.6k | 0.02 119 ✗ | 0.5 4.9k |
| be | 1.4 5.6k | 1.3 23.3k | 0.2 922 ✗ | – | 0.08 1.5k ✗ | 0.01 86 ✗ | 0.05 395 ✗ | 0.1 2.7k | 1.2 20.8k | 0.03 553 ✗ | 0.04 731 ✗ | 0.02 133 ✗ | 0.1 1.4k ✗ |
| el | 6.0 29.7k | 0.8 10.0k | 0.05 242 ✗ | 0.04 487 ✗ | 0.8 8.9k | 0.04 950 ✗ | 0.09 1.3k ✗ | 0.04 771 ✗ | 1.1 12.1k | 0.07 1.3k ✗ | 6.3 216.6k | 0.03 176 ✗ | 0.4 4.8k |

Glossaries (the source language's own Wiktionary edition; id, tl, sh, uk and be have none, ms has only lists under the
threshold, 511 words into en at most):

| from | en | ru | de | es | fr | it | ja | ko | pl | pt | tr | vi | zh |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| nl | 1.3 39.5k | 0.2 3.9k | 0.7 21.2k | 0.8 28.1k | 0.7 22.2k | 0.2 7.6k | 0.1 2.5k | 0.06 1.4k ✗ | 0.2 5.5k | 0.2 4.3k | 0.09 2.5k | 0.05 1.2k ✗ | 0.07 1.6k |
| cs | 0.7 23.6k | 0.3 11.0k | 0.6 20.9k | 0.2 6.2k | 0.5 17.9k | 0.2 8.4k | 0.08 2.3k | 0.04 912 ✗ | 0.3 12.1k | 0.06 1.9k | 0.04 1.1k ✗ | 0.02 483 ✗ | 0.03 784 ✗ |
| el | 0.8 42.2k | 0.1 5.9k | 0.4 16.6k | 0.1 6.9k | 0.6 32.6k | 0.1 6.4k | 0.05 2.2k | 0.03 1.2k ✗ | 0.1 6.5k | 0.07 3.9k | 0.07 3.8k | 0.02 349 ✗ | 0.04 1.7k |

- Pairs that pass, main: nl en ru es fr it ja ko pl pt tr zh; cs en ru de fr ja ko pl pt tr zh; id en ru fr pl zh;
  ms en ru fr zh; tl en ru fr tr zh; sh en ja zh; uk en ru de fr ja ko pl tr zh; be en ru ko pl; el en ru fr pl tr zh.
  Close to the line (may cross with the next wty release): uk→ja 1,517, cs→pt 1,523, nl→ko 1,566, be→fr 1,475,
  be→zh 1,377.
- The catalog script still filters by `MIN_BYTES` (512 KiB); the word count of the "Catalog threshold" row is not
  implemented on any branch yet. Every 4a file above 512 KiB has more than 1,500 words; 51 files pass by words but not
  by bytes (id→pl 0.07 MB with 1.7k words, ms→zh 0.5 MB, uk→ja, sh→ja, most glossaries) and stay out until it is.
- el→tr: 216.6k words, of which about 90.3k are inflections written as prose glosses ("… sözcüğünün çekimi") and
  111.5k surnames and given names; about 14.8k ordinary entries. It passes either way.
- Own-language editions: nl-nl 27.4 MB, el-el 37.8, cs-cs 8.4 (46.9k words), id-id 2.1 (42.6k), ms-ms 0.8 (10.5k).
  The catalog lists a pair only when the target is in `GLOSSES`, so none of them appears today (fr-fr, de-de do,
  since their languages are gloss languages).
- Transcriptions (`dict/all/<lang>/wty-<lang>-ipa.zip`), MB and words: nl 2.1 (134.4k), cs 1.7 (122.4k), id 0.47
  (25.4k), ms 0.22 (9.0k), tl 0.74 (31.8k), sh 0.67 (51.4k), uk 0.92 (66.2k), be 0.20 (14.3k), el 0.98 (59.4k).
  Under `MIN_BYTES` today: id, ms, be.
- Form-of rows in →en (rows, distinct forms, own words): nl 189,674 / 182.2k / 67.7k; cs 470,860 / 463.2k / 47.5k;
  id 36,717 / 35.3k / 31.3k; ms 36,246 / 33.2k / 10.0k; tl 96,733 / 77.1k / 28.0k; sh 927,700 / 878.4k / 56.7k;
  uk 419,102 / 364.0k / 29.9k; be 58,028 / 55.7k / 5.6k; el 265,861 / 247.8k / 29.7k. Lemma rows have no reading in
  any of them.
- Into Latvian: nl→lv 1.1k, cs→lv 1.4k, el→lv under 1k (lv is not a gloss language anyway).
- Beyond Wiktionary: only Kamata954's Indonesian–English (Wiktionary-based, in "Catalog candidates and licenses") is
  known; nothing else was searched in this pass.

### Word frequency (wordfreq 912caf6)

- Large lists: cs (3.25 MB), nl (1.66), uk (2.77). Small lists only: el 46.9k words, `fil` 30.3k (Tagalog), id
  31.2k, ms 28.8k, `sh` 54.8k (one list for bs, hr, sr, me). Belarusian has none.
- `sh` is Latin only: wordfreq transliterates Serbian Cyrillic to Latin for sr and sh. Cyrillic spellings would have to
  be added by transliteration when the frequency dictionary is built (as the Traditional Chinese forms are), or
  Cyrillic text gets no frequency order.
- Greek: wordfreq writes the final sigma as σ (10,841 words end in σ, none contains ς: τησ, τουσ, πωσ). The builder
  must turn a word-final σ into ς or no frequency matches. 39k of the 46.9k words carry tonos.
- Latin words in the lists: uk small 1,660, el small 3,578 (filtered like the Russian list's).

### OCR

- ML Kit's Latin model lists Czech, Dutch, Indonesian, Malay, Filipino ("fil (or tl)"), Serbian in Latin, Croatian and
  (experimental) Bosnian. Nothing for Cyrillic or Greek. `OcrScript` has no GREEK value yet.
- Paddle character lists: v5 `latin` (836) and v6 small (18,708) have every Czech, Dutch (Ĳ ĳ included), Tagalog,
  Indonesian, Malay and Serbo-Croatian Latin letter (Ǆ ǅ ǆ Ǉ ǈ ǉ Ǌ ǋ ǌ included); v6 lacks „ and ‚ and every
  letter of uk, be, Serbian Cyrillic and Greek beyond Russian and basic Greek. `eslav` (517) has ґ є і ї ў but lacks
  ђ ј љ њ ћ џ; `cyrillic` (850) has every uk, be and Serbian letter and Latin a–z, but no č ć đ š ž. No model reads
  both Serbo-Croatian scripts. `el` (354) has the tonos and dialytika letters, ΐ ΰ, ς, ASCII ; and U+00B7, and » but
  not «. None of the lists has U+037E, U+0387 or U+02BC (they normalize to ; · under NFC; ʼ comes out as ' or ’).
- tessdata (fast / best MB): nld 5.8 / 8.5, ces 3.6 / 10.4, ind 1.1 / 7.9, msa 1.7 / 7.8, fil 1.8 / 8.6 (no `tgl`),
  srp 2.1 / 8.9, srp_latn 3.1 / 9.4, hrv 3.9 / 10.7, bos 2.4 / 5.0, ukr 3.6 / 10.4, bel 3.5 / 10.4, ell 1.4 / 8.5.
- Measured (`ocr-engines.md`, CER %, clean + hard under the plan's rule): cs Paddle v6 1.4 against ML Kit 4.3; sh
  Latin v6 0.3 against 1.7; uk Paddle `cyrillic` 3.4 (eslav 4.4) against tessdata_fast 19.1; sh Cyrillic `cyrillic`
  2.8 against 22.1; el Paddle `el` 1.4 against 25.4. Clean screens: cs tessdata 0.1, v6 1.0, ML Kit Latin 0.9; uk
  `cyrillic` 1.8, eslav 2.6; el Paddle 0.7, tessdata best 0.8. Photos: cs Paddle 13.5 (cloud 5.6), el 11.6 (2.5),
  sh Cyrillic 9.7 (3.7), uk 39.1 (35.9, two signs). nl, id, ms, tl and be were not measured; their letters are the
  ones the Latin and Cyrillic models already read.
- Model sets: `paddle_models.py` offers v6 and `latin` only for en de fr es it pt pl tr lv and `cyrillic` only for
  ru; the 4a languages need adding, and `el` (and `eslav`, if offered at all) need converting and hosting.
  Belarusian would take `cyrillic` as Ukrainian does (eslav is worse on uk and lacks nothing be needs, but it was not
  measured on be).

### Fonts

- Literata covers all nine (Greek and the combining marks included). Inter covers all but U+0311 (inverted breve of
  Serbo-Croatian Cyrillic readings). PT Serif and PT Sans cover Latin and Cyrillic with ґ є і ї ў ђ ј љ њ ћ џ but
  lack Ĳ, ǆ ǉ ǌ, Greek and the combining U+0300, U+030F, U+0304, U+0311 that sh readings use. Atkinson Hyperlegible
  Next: Latin only, without ǆ ǉ ǌ, U+030F, U+0311. Lexend: Latin and Vietnamese only (its file has basic Cyrillic
  without ґ є і ї ў ђ ј, no Greek); the catalog's subsets already say so.
- Serbian Cyrillic forms (upright б; italic б г д п т): only the system Noto Sans/Serif have `locl` for SRB (and MKD);
  Roboto, Literata, Inter and PT have none. The catalog downloads upright files, so italics are synthesized; the
  Serbian forms appear only with Noto and a Serbian `lang`. HarfBuzz (`hb-ot-tag-table.hh`, main) maps `sh` to BOS,
  HRV, SRB and takes the first the font has, so `lang="sh"` should select Noto's SRB too (to check in the WebView;
  `lang="sr"` on Cyrillic text is the fallback).
- Greek: Roboto lacks U+037E (NFC turns it into ; anyway).
- Locale codes on Android: `Locale("id").language` is the old code "in" (as he → iw, yi → ji), `toLanguageTag()`
  gives "id"; the system's Filipino locale is `fil`, while Yomitan and wty use `tl`. Comparisons should go through
  language tags (to check on API 35).

### Text rules

- Ordinal period: cs ("5. května") and sh ("5. maja", "1918. godine", as Latvian) need `ordinalPeriod`; nl, id, ms,
  tl (ika-5), uk, be and el do not.
- Greek: ; is the question mark and ends a sentence; · (ano teleia) is a clause mark and does not. Quotes «…» with
  “…” inside.
- Quotes: cs „…“ ‚…‘ »…«; nl “…” ‘…’ „…”; sh Serbian „…“, Croatian »…« and „…“; uk and be «…» with „…“ inside;
  id, ms, tl “…” ‘…’.
- Apostrophes: nl 's, 't, 'n, zo'n, auto's (wty: auto's → auto "plural", zo'n a lemma, 's → des, eens; 1,500
  headwords with '); uk п'ять, м'ясо (5,994 headwords with ', 2 with ’, 1 with ʼ), be сям'я (520 with '). The word
  scan already keeps ' and ’ between letters (`WORD_CONNECTORS`) and ʼ is a letter; Yomitan's uk variants map ’ ʼ to
  '; be has no such processor, so ’ in Belarusian text needs one (or `ukrainianApostropheVariants` reused).
- Abbreviation drafts:
  - cs: např, tj, tzv, tzn, apod, resp, mj, aj, č, s, str, r, st, ul, nám, MUDr, Ing, Mgr, doc, prof, PhDr, JUDr,
    RNDr.
  - nl: bijv, d.w.z, o.a, m.a.w, e.d, ca, nr, blz, dhr, mevr, mr, dr, prof, ir, ing, drs, jl, a.s.
  - id: dsb, dst, yth, Jl, No, hlm, tsb, sbb, spt, thn, tgl, Bpk, Dr, Prof, S.H (dll often ends a sentence: leave out).
  - ms: Jln, En, Pn, Tn, Sdn, Bhd, hlm, Dr, Prof.
  - tl: Gng, G, Bb, Sto, Sta, Blg, p, Hen, Kgg.
  - sh: npr, tj, tzv, sl, god, g, st, v, str, br, dr, prof, ul, sv, odn, pr. Kr., n. e., p. n. e.; Cyrillic нпр, тј,
    бр, стр, год, в, ул, др, проф, тзв, св, г.
  - uk: напр, див, р, ст, с, м, вул, обл, тис, проф, акад, доц, т. зв, пор, англ, лат.
  - be: г, ст, напр, вул, с, тыс, праф, акад, г. зн.
  - el: π.χ, δηλ, βλ, σελ, αρ, κ, π.Χ, μ.Χ, τ.μ, χλμ, οδ, Αγ.

### Per language

- **Dutch**: no Yomitan rules; forms from 189.7k form-of rows (plural, diminutive, partitive, "Flanders colloquial").
  Rich catalog: 11 main pairs and 11 glossaries pass, nl-nl 27.4 MB. Large wordfreq list. OCR: Latin models; not
  measured, Paddle v6 expected as for de. TTS: Google voices for Belgium and the Netherlands. Audio: Lingua Libre 10/10,
  Wiktionary 10/10 (Nl-kat.ogg; some titles carry speaker initials where the parser expects a region, Nl-tv-hond.ogg).
  Flag nl; bubble letter candidates Ĳ or IJ.
- **Czech**: no Yomitan rules; 470.9k form-of rows (all seven cases, vocative included). 10 main pairs, 9 glossaries,
  cs-cs 8.4 MB. Large wordfreq list. OCR measured: Paddle v6. TTS: Google voice. Audio: Lingua Libre 6/10, Wiktionary
  10/10 (Cs-kočka.ogg). Flag cz; bubble letter Ř (or Ů).
- **Indonesian**: Yomitan strips diacritics only; 36.7k form-of rows (reduplicated plurals rumah-rumah, active and
  passive verbs: membaca → baca, which is also its own entry; equative, jussive). id-en 3.9 MB, id-ru 4.4k words; id-id
  2.1 MB; no glossaries. Small wordfreq list. OCR: ML Kit Latin lists it; Paddle `latin`/v6 have the letters; not
  measured. TTS: Google voice. Audio: Lingua Libre 10/10, Wiktionary 0/10. Flag id; no letter outside basic Latin.
- **Malay**: no Yomitan descriptor (group 3 style). ms-en 1.7 MB, 10.0k words; ms-zh 0.5 MB with 14.5k words; ms-ms 0.8
  MB; 13,997 Jawi alt-of items (baca → باچ). Lingua Libre `msa` (Q9237) 2,414 files, `zsm` none. Small wordfreq list.
  TTS: Google voice (Malaysia). Audio: Lingua Libre 7/10, Wiktionary 4/10 (Ms-MY-kucing.ogg), either 8/10. Flag my
  (Brunei bn and Singapore sg exist in circle-flags).
- **Tagalog**: Yomitan transforms (60, affix names only); wty tl-en 4.3 MB, 28.0k words, 96.7k form-of rows (aspect
  progressive/completive/contemplative, focus objective/benefactive/causative/locative) and 38,896 Baybayin alt-of items
  (basa → ᜊᜐ). Codes differ by source: Yomitan, wty, Lingua Libre `tl`/`tgl`; ML Kit, tessdata, wordfreq and Google TTS
  `fil` "Filipino". Lingua Libre `tgl` (Q34057) 3,269 files, `fil` 7. Audio: Lingua Libre 10/10, Wiktionary 2/10
  (Tl-PH-pusa.ogg). Flag ph; bubble letter candidates Ng or Ñ (Ñ is Spanish's).
- **Serbo-Croatian**: one language in Yomitan, wty, wordfreq; separate in tessdata (srp, srp_latn, hrv, bos), ML Kit
  (Serbian Latin, Croatian, Bosnian), Lingua Libre (srp Q9299 93 files, hrv Q6654 1, bos and hbs none) and Google TTS
  (Serbian, Croatian, Bosnian). wty sh-en 12.0 MB: 31.4k Latin and 25.3k Cyrillic headwords, each a full entry whose
  grammar line names the other spelling ("Cyrillic spelling ку̏ћа"); 41,876 more Cyrillic form rows carry the accented
  spelling and point to the Latin lemma with the tag `Cyrillic` (ма̏чка → mačka), duplicates of the Cyrillic entries
  once marks are ignored. Ekavian and Ijekavian variants link each other (hleb ↔ hljeb, "alternative Ijekavian"); tags
  include future-i/future-ii, pluperfect, conditional-i/-ii, Ikavian. 134k form rows have accent marks in the headword
  (most Cyrillic, and periphrastic forms like "би́ли бисмо прешли"); the plain spellings of common forms exist as
  separate rows (куће, читам, knjige). Only en, ja, zh pass as gloss languages. Audio: Latin words 1/10 (one Lingua
  Libre file), Cyrillic 8/10 from Wiktionary as Sr-кућа.flac (plain fixes below). No sh flag; candidates rs, hr, ba, me.
  Bubble letter candidates Đ (Latin, every standard) or Ћ.
- **Ukrainian**: Yomitan's richest set here (35 described transforms, apostrophe variants); wty uk-en 9.8 MB, uk-ru 3.2
  MB (36.0k words, more than uk-en's 29.9k); 9 main pairs; form rows carry stressed readings (чита́ю), 537 form
  headwords have a secondary-stress mark (а̀втостради). Large wordfreq list. OCR: Paddle `cyrillic`. Lingua Libre `ukr`
  (Q8798) 27,402 files. TTS: Google voice. Audio: Lingua Libre 0/10 (its 27.4k files are mostly one speaker reading
  rarer words and phrases), Wiktionary 10/10 (Uk-кіт.ogg). Flag ua; bubble letter Ї (or Ґ, Є).
- **Belarusian**: Yomitan capitalization only. wty be-en 1.4 MB with only 5.6k words, be-ru 23.3k, be-pl 20.8k. 51,670
  of be-en's form-of rows have a stressed headword (па́льца, ко́шкі) and 45.3k of them have no unstressed twin, so
  "пальца" in plain text finds nothing; be-ru has 3,601 such rows. No wordfreq list (no frequency order). OCR: Paddle
  `cyrillic` presumably (not measured). Lingua Libre `bel` (Q9091) 1,931 files. No Google TTS voice. Audio: Lingua Libre
  2/10, Wiktionary 8/10 (Be-вада.ogg), either 10/10. Flag: circle-flags has only the official red-green `by`. Bubble
  letter Ў.
- **Greek**: Yomitan drops double acute accents, one prefix rule (ξανα-); 265.9k form-of rows, with article tables as
  noise (της lists ο, η, το, τα, τις, τους, τον, τη, την, των… as its lemmas, like the Russian pronoun tables). 6 main
  pairs and 11 glossaries; el-el 37.8 MB. All-caps text has no tonos (ΣΚΥΛΟΣ → σκυλος), and wty headwords carry it
  (σκύλος), so capitals and unaccented text find nothing unless headwords are matched without tonos too. Small wordfreq
  list with the σ problem. OCR: Paddle `el` (not yet converted), `OcrScript.GREEK` needed. Fonts: Literata and Inter
  (and the system's). Lingua Libre `ell` (Q9129) 167 files. TTS: Google voice. Audio: Lingua Libre 2/10, Wiktionary 0/10
  (one loose hit without the El- prefix), so the voice is the main source. Flag gr (cy exists); bubble letter Ω (or Λ).

### Plain fixes found (no owner decision)

- Belarusian form headwords with stress marks: index headwords without U+0301 (and U+0300) at import as well, for be,
  uk and sh (sh also U+030F, U+0311, U+0304, U+0302 on vowels and r), so plain text finds them.
- Serbo-Croatian `Cyrillic` form rows: silent and dropped (`ignoredFormTags`), the Cyrillic entries exist on their own.
- Baybayin and Jawi alt-of rows: the target (ᜊᜐ, باچ) is itself only a form row back to the Latin word, so following
  it brings nothing new; drop the tags `Baybayin` and `Jawi` (the grammar line already shows the spelling).
- Belarusian apostrophes: the Ukrainian apostrophe variants (’ ʼ → ') applied to be as well.
- Greek frequency: word-final σ → ς in the builder.
- sh frequency: add the Cyrillic spellings by transliteration.
- sh audio: Wiktionary's recordings are `Sr-кућа.flac` (Cyrillic, prefix Sr, not Sh); none was found under Sh, Sr,
  Hr or Bs in Latin. The search needs the prefix `sr` and, for Latin text, the word transliterated to Cyrillic
  (lj → љ, nj → њ, dž → џ, đ → ђ …); that turns 1/10 into 8/10 on the test words.
- Greek ; ends a sentence, · does not; cs and sh `ordinalPeriod`.
- `OcrScript.GREEK`; uk, be and Serbian Cyrillic on Paddle `cyrillic` (eslav lacks ђ ј љ њ ћ џ and measured worse on
  uk); the 4a Latin languages on v6/`latin`.
- A Serbian `lang` on Serbo-Croatian text (`sh`, or `sr` on Cyrillic if the WebView ignores `sh`) for Noto's
  Serbian glyph forms.

### Questions for the owner

Answered on 2026-10-18: questions 41-54 and the decision rows from "Serbo-Croatian" on in the languages
plan.

1. Serbo-Croatian: (a, recommended) one language with both scripts, as Yomitan, Wiktionary and wordfreq treat it;
   (b) Serbian, Croatian and Bosnian as three languages sharing the sh dictionaries (each with its own OCR, voice and
   flag); (c) one language, Latin only.
2. Serbo-Croatian script for recognition (no model reads both): (a, recommended) the language's model list holds the
   Latin models and the Cyrillic one, the Latin v6 preselected, the user picks once; (b) two entries, "Serbo-Croatian
   (Latin)" and "(Cyrillic)", sharing the dictionaries, switched from the bubble like languages.
3. Serbo-Croatian flag: (a, recommended) Serbia by default, Croatia, Bosnia and Herzegovina and Montenegro offered on
   the Languages screen as for English and Portuguese (extends the row "Flag country"); (b) the code "SH" in a round
   badge instead of a flag; (c) Serbia only.
4. Serbo-Croatian voice: (a, recommended) by the text's script, Croatian for Latin, Serbian for Cyrillic (to check
   that the Serbian voice reads Latin); (b) one voice chosen in the audio settings.
5. Indonesian and Malay: (a, recommended) two languages (own dictionaries, frequency lists, voices, flags); (b) one
   entry with both dictionaries.
6. The name of tl: (a, recommended) "Filipino" in the UI (Android, ML Kit, tessdata, wordfreq and the voice say so),
   code tl inside; (b) "Tagalog" (Yomitan and Wiktionary); (c) "Filipino (Tagalog)".
7. Own-language Wiktionary editions (nl-nl 27.4 MB, el-el 37.8, cs-cs 8.4, id-id 2.1, ms-ms 0.8): (a, recommended)
   listed, unticked, for every source language that has one; (b) left out unless the language is a gloss language.
8. Transcriptions: (a, recommended) the 1,500-word rule as for pairs (id, ms and be get one; Turkish and Latvian
   rechecked); (b) keep 0.5 MiB for transcriptions.
9. Greek text without tonos (capitals, careless typing): (a, recommended) a word written without any tonos also
   matches headwords that have one (ΣΚΥΛΟΣ → σκύλος), a word with tonos matches exactly; 9,456 of 260k accentless
   spellings join two or more headwords (ποτέ and πότε, νομός and νόμος), which then both show; (b) every word matched
   without tonos; (c) exact accents only.
10. Article and pronoun tables (Greek της → 13 article forms; Russian он → вы, его…): (a, recommended) leave as is,
    as for Russian; (b) drop form links between two forms that list each other.
11. Belarusian flag: (a, recommended) the official flag from circle-flags; (b) the white-red-white flag, drawn by us;
    (c) the code "BE" instead of a flag.
12. Bubble letters: (a, recommended) nl Ĳ, cs Ř, id and ms the codes "ID" and "MS" (no letter of their own), tl Ng,
    sh Đ, uk Ї, be Ў, el Ω; (b) the alternatives above (IJ, Ů, Ñ, Ћ, Ґ, Λ).
