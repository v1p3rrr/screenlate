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
  16.3). Without rules of their own they work like group 3: the word as written and its form-of rows, since word
  resolution never shortens a word (corrected by the group 4e research, which found no longest-start fallback;
  Bengali, Kazakh and Kyrgyz get endings of their own). Paddle's `cyrillic` recognizer has every Kazakh and Kyrgyz
  letter (ә ғ қ ң ө ұ ү һ і), `eslav` does not; Bengali has no Paddle model; ML Kit's Devanagari recognizer turned out to read it (group 4b, 2026-10-19).
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
- wty drops entries in `handle_alt_of_sense` (`src/dict/main/ir/mod.rs`, release 2026.10.11 at 602ffb3, unchanged at
  774f3f6; checked 2026-10-22): an alt-of sense with a filtered tag (`abbreviation`, which kaikki puts on contractions
  and apocopic forms; also misspelling, nonstandard, obsolete and others) counts as handled and is deleted without a
  form row, so de im, zum, zur, vom, beim; it un, del, della, nel; pt do, da, na, ao; es muy, tu, buen; ca del, al, pel;
  gl moi have no entry. In wordfreq's top 1,000 words that is 2.2% of the German tokens, 7.5% of the Italian, 7.8% of
  the Portuguese, 0.7% of the Spanish; French is not affected. Any other alt-of sense is deleted too, and its row goes
  from the target to the page (ca bé → ben, gl auga → agua): about 94% of the rows tagged alt-of point at a term with no
  entry. Reported as https://github.com/yomidevs/wiktionary-to-yomitan/issues/399; the owner waits until 2026-11-05
  before an own supplement (languages plan, request 73). To check a release: `ca-en` has no row for del, `de-en` none
  for im.
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
| many | Leipzig frequency lists (leipzig-to-yomitan) | downloads said to be CC BY, but the terms also say CC BY-NC and no commercial use without consent. Read again for group 4e (Wayback copy of 2025-11-12): CC BY-NC for the site's data and services, CC BY for "the text corpora offered for download"; the Kazakh and Kyrgyz frequency lists are built from those downloads (owner, 2026-10-19) |
| kk ky (many) | PanLex (`cointegrated/panlex-meanings` mirror of the 2024-03-01 dump) | CC0 for the 2024-03-01 dump we use: PanLex's license page said CC0 for its snapshots until at least May 2025 and says CC BY-NC-SA 4.0 since (rechecked 2026-10-22, see "PanLex license history"); stay on that dump. `scripts/panlex/panlex_yomitan.py` converts kk and ky into en and ru (group 4e) |

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
| ms | no descriptor (like kk, ky: form-of rows and the ordinary prefix scan) | none |
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
  Built on `languages/main` 2026-10-19 (`LanguageSupport.headwordMarks`, `MarkStripper` in `archive_split.cpp`). The
  scan of the archives found the lemmas as well: form-of items name a stressed lemma that no headword spells
  (wty ru-en: 376,101 items, почти → почти́ть; 363,544 of them name a headword once the marks go, 11,514 another form,
  1,043 nothing; uk-en 21,689 of 29,039; sh-en 1,531 of 1,677; be-en 1,900 of 1,981). So Russian gets the rule too,
  for lemmas: before it those items led nowhere. Stored lemmas lose the marks; forms keep the stressed key beside the
  plain one. Marks go only after vowels (and r for sh): Belarusian Łacinka ć ś ń ź and Serbo-Croatian ć carry the
  same acute in decomposed form. Only 15 sh main headwords are marked (the letters ȁ ȅ … and "Ciudad de México").
  Installed dictionaries change only when imported again.
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

## Serbo-Croatian on `languages/sh` (2026-10-19)

Superseded on `languages/sr` the same day (next section): the two entries became Croatian and Serbian. The lookup,
frequency, glyph and QuickJS findings below still hold.

- Two entries, `Language.SERBO_CROATIAN_LATIN` (`sh-Latn`) and `SERBO_CROATIAN_CYRILLIC` (`sh-Cyrl`), with the shared
  `dictionaryCode` sh. The entry code keys the settings, the profile, the recognition models (`paddle-v6-sh-Latn`,
  `tesseract-*-hrv`; `paddle-cyrillic-sh-Cyrl`, `tesseract-*-srp`), the voice (hr, sr), the flag and the page's
  `lang`; the dictionary code is the dictionaries' source language (catalog, registry, imports), the Yomitan bundle
  `sh.js`, the wordfreq list, the audio URL templates' `{language}`, cloud recognition's language and the backup's
  dictionary files (either entry takes them). `Language.of("sh")` is null, `Language.withDictionaries("sh")` gives both
  entries, `CatalogEntry.isFor` compares models by entry and dictionaries by dictionary code.
- Files: an entry's files are its models, plus the dictionaries only while the other entry is off (`fileCodes`), so
  turning off one entry while the other is on deletes nothing shared; with both off, one leftover card under sh holds
  the dictionaries and both entries' models, with a "Turn on" button per entry (`leftoverCode`, `leftoverCards`).
- Lookup time on the emulator (Pixel_10_Pro, wty-sh-en, instrumented test): ordinary words 2-10 ms; a line of c, s
  and z ("cecescecescecesc") that reaches Yomitan's 4,096-variant cap 140-260 ms. No cut of our own was needed.
- QuickJS has no `console`: Yomitan warns through `console.warn` when it cuts the variants, and the warning threw, so
  the whole lookup failed. `scripts/yomitan-language/console-stub.js` (in every bundle) drops the messages, which carry
  the looked-up text. Node's `vm` contexts always have a console; the glue test sets `console: undefined`.
- Yomitan's `removeSerboCroatianAccentMarks` matches Latin vowels and r only, so Cyrillic text with reading marks
  (ку̏ћа) found nothing; the glue replaces it with a version that takes the Cyrillic vowels and р as well.
- A word that is also a form of itself shows the form's tag (kuća "genitive plural", as kuća is the genitive plural of
  kuća): the engine merges the direct match and the form-of match into chains [[], [genitive plural]] and shows the
  non-empty one, as Yomitan does. Left as is (`notes/language-rules.md`, limits).
- Frequency: the wordfreq `sh` list is Latin; the builder adds each word's Cyrillic spelling (and its capitalized
  form) with the word's rank, so кућа gets 553 as kuća does.
- Audio: Wiktionary files are found under the prefix `Sr-` with the Cyrillic title (Latin words transliterated by
  `SerboCroatianScripts`); kuća and кућа played from Wiktionary on the emulator. Lingua Libre (`srp`) had nothing for
  the words tried.
- Glyph forms: `lang="sh-Cyrl"` gives Noto's Serbian б in the WebView (`webview-fonts.md`).

## Croatian and Serbian on `languages/sr` (2026-10-19)

The owner chose two languages instead of two script entries (questions 61-64): a learner learns "Serbian" or
"Croatian", not Serbo-Croatian.

- `Language.CROATIAN` (`hr`, Latin) and `SERBIAN` (`sr`, Cyrillic and Latin), both with `dictionaryCode` sh. The code
  keys settings, profile, models (`paddle-v6-hr`, `tesseract-*-hrv`; `paddle-cyrillic-sr` recommended,
  `paddle-v6-sr`, `tesseract-*-srp`), voice, flag (hr, rs; no flag button), e-ink code (HR, SR) and the page `lang`.
  The names are the system's (Croatian, Serbian). Shared behavior stays in the abstract `SerboCroatianSupport`;
  `CroatianSupport` and `SerbianSupport` add script, samples, fonts, glyph (Đ, Ћ) and the hooks below. No migration
  from the unreleased `sh-Latn` and `sh-Cyrl` settings: on the emulator both languages came up off, with the shared
  dictionaries in one "Turned off" card ("Serbo-Croatian · 3 dictionaries · 48 MB", a "Turn on" button each).
- Serbian in both scripts: lookup takes either (the dictionaries hold both spellings); cloud recognition gets sh, as
  before. OCR: `LanguageSupport.ocrLines` gets the page line by line; Serbian sums each line's script balance
  (Cyrillic letters +1, look-alikes and j 0, other Latin letters -1; a line at 0 takes the page's) and fixes
  look-alikes only on Cyrillic lines, there also in words of look-alikes alone (`LookAlikes.fixInScript`: Ја, је,
  Јој, which Lens reads as Latin: every letter of such a word has a Latin twin, so Lens writes the whole word in
  Latin, it does not mix scripts within it). Latin words in capitals stay unless the line is a heading in capitals
  (OK, TOP on buttons and in running text). A Latin line keeps i, a, u, o. The owner chose this over the plan row's
  "only with a Cyrillic model" (question 65): the final text comes from cloud recognition whatever the model.
- Voice: `speechText` gives the voice Latin words in Cyrillic (the sr voice reads Cyrillic for certain; on the
  emulator Google TTS used sr-RS). Translation: `translationTag` sends text that is mostly Latin as `sr-Latn` where
  the service has it (Bing, Edge); Google has no sr-Latn, gets sr and translates Latin text right. On the emulator
  Bing gave "The house is big, and the yard is small." for "Kuća je velika, a dvorište malo."
- Audio (`audio-sources.md`): `LanguageSupport.recordings` lists the Wiktionary prefixes and titles to try
  (`RecordingTitle`), searched in parallel and merged in order; `linguaLibreItems` and `linguaLibreTitle` cover
  Croatian's two items and Serbian's Latin titles. Emulator: ljubav (Croatian) Hr-ljubav.ogg, then Sr-љубав.flac;
  voda (Serbian) Sr-вода.flac, then Lingua Libre (UništihEkonomiju).
- Models: Serbian's list holds PaddleOCR v5 Cyrillic (recommended), PaddleOCR v6 (Latin) and Tesseract srp; no
  ML Kit (question 66: Serbian is mostly Cyrillic). Choosing v6 loaded and warmed it at once (481 ms).

## Ukrainian and Belarusian on `languages/uk` (2026-10-19)

- Bundles: `uk.js` has Yomitan's 35 rules, `removeUkrainianDiacritics` (U+0301) and `ukrainianApostropheVariants`
  (' ‘ ’ ʼ ` ´ each tried as ', ’ and ʼ). Yomitan gives Belarusian capitalization only; `build.mjs` adds Ukrainian's two
  processors to its descriptor (`addedPreprocessors`) and fails if Yomitan's `be` gets more processors of its own.
- Rule names: `values/inflections_uk.xml` from `generate-inflections.mjs`, translated by hand into the 13 other
  locales with the form-of tag terms of each (`form_of_tags.xml`).
- Readings: wty gives uk and be lemmas no reading; forms carry their stress (чита́ю, вады́), which the import drops
  from the key (`headwordMarks`, U+0301 and U+0300 after a vowel). So no reading rule like `reading-ru.js` and no
  furigana markers for either.
- Apostrophes: wordfreq uk, wty uk-en (5,994 headwords) and most other editions write ', wty uk-fr ’ (1,999), uk-ru
  both (1,031 ', 79 ’), be-pl and be-ru ’ and some ʼ. Lookups find any of them through the processors. The wordfreq
  builder gives uk words with ' their ’ and ʼ spellings at the same rank (`frequencySpellings`); the index then says
  "spellings the list leaves out … have its rank" (the sentence covers the Chinese and Serbian scripts too).
  wty uk-ru lists some words under both (п'ятниця, п’ятниця), shown as two entries.
- Chains back to the text: Yomitan's "nominative plural" takes книга for the plural of книго, and wty lists книго as
  the vocative of книга, so every feminine noun in -а in its dictionary form showed "nominative plural « vocative
  singular". `candidate_lookup.cpp` drops a form-of step that leads back to the scanned text (the candidate's
  transformed text) through another text; a form that is its own lemma (kuća) keeps its one-step chain.
- OCR: a word of one letter is taken as Cyrillic in every Cyrillic language (`CyrillicLookAlikes.fix`): і (and), у,
  В and а have no other letter to show their script. Ukrainian adds i, ï → і, ї, Belarusian i, ŭ → і, ў. The overlay
  over a rendered Ukrainian image (Arial, Paddle `cyrillic`) found і as the conjunction and п’ять as п'ять.
- Fonts: the Ukrainian apostrophe ʼ (U+02BC) is outside the Latin and Cyrillic blocks; Ukrainian's system font range
  adds it.
- Sentences: Ukrainian "р." and "рр." (рік, роки), "т. зв.", "у т. ч.", "ст.", "див."; Belarusian "г.", "гг.", "г.
  зн."; "і т. д." and "і г. д." end. Ukrainian т. also holds Russian "т. е.", Belarusian г. Russian and Serbo-Croatian
  "1991 г." (Isolation test).
- Catalog: uk has 9 main pairs (en, ru, de, fr, ja, ko, pl, tr, zh), the large wordfreq list (2.8 MB), the
  transcription and Paddle `cyrillic`; be 4 main pairs (en, ru, ko, pl), no frequency list.
- Emulator (LK37, LK38): книги → книга "nominative plural", W 1109; зробив → зробити "past"; чита́ю → читати; п’ять and
  пʼятниця find the ' headwords (п'ять W 800); Wiktionary audio plays; flags ua and by, Ї and Ў in the bubble;
  Belarusian сям’я → сям'я, вады and ва́ды → вада "genitive singular", праўда.

## Greek on `languages/el` (2026-10-19)

- Bundle: `el.js` has Yomitan's capitalization processors, `removeDoubleAcuteAccents` (an enclitic's second accent:
  πρόσωπό → πρόσωπο) and one rule, ξανα- (again). The rule's last variant drops every accent of what is left
  (ξαναρώτησε → ρωτησε), which only the tonos-free keys below can match. QuickJS lowercases a final Σ to ς
  (ΣΚΥΛΟΣ → σκυλος), checked by the instrumented test. `values/inflections_el.xml` holds no strings: the rule is
  named by its prefix, so `generate-inflections.mjs` no longer asks for a translation file when nothing needs one.
- Tonos: wty headwords carry it (σκύλος), all-caps text never does, and much typed text leaves it out. With
  `HeadwordMarks.everyHeadword` the import keys every headword with definitions without U+0301 in the form-of table,
  as a step without tags back to the headword, and form rows the same way with their lemmas as written. The table is
  written even when the archive needs no rewrite, so el-el and the glossaries get it too. Text with the tonos still
  matches exactly: πότε finds πότε only, ποτε finds ποτέ and πότε (owner's choice). wty el-en: 7,921 tonos-free
  spellings stand for two or more headwords.
- wordfreq: the small `el` list (276 kB) writes the final sigma as σ (τησ, σασ); the builder turns a word-final σ into
  ς (`LanguageSupport.frequencyWord`), so ranks reach the headwords (σκύλος W 3024, ποτέ 120, τώρα 67).
- OCR: ML Kit has no Greek model. Paddle `el` (PP-OCRv5 with the v5 detector, 7.2 MB) is the recommended model,
  uploaded to the `ocr-models-1` release on 2026-10-19; Tesseract `ell` fast and best are listed unticked. Latin
  look-alikes become Greek (`GreekLookAlikes`): the shared capitals, v → ν, n → η, c → ς, u → υ, w → ω, y → γ,
  x → χ; a lone o or H counts as the article ο, Η.
- Sentences: ; and U+037E end a sentence, the ano teleia · does not; abbreviations π.χ., λ.χ., κ. (Mr), αι.,
  βλ., σελ., μ.μ., π.μ. and the others in `GreekSupport` hold it; κ.λπ., κ.τ.λ., κ.ά. and μ.Χ. end. Quotes « » with
  “ ” inside. Elided σ', τ', μ', θ', ν', κ', απ', γι' start a new word after the apostrophe.
- Fonts: the system font range adds Greek and Coptic (U+0370-03FF) and Greek Extended (U+1F00-1FFF); downloadable Latin fonts with Greek (Literata, Inter) show on the list; the "Only for Greek
  text" switch has its string in every locale.
- Audio: Lingua Libre files under Q9129 carry `ell` (167), `gre` (130) or `mis` (84); the search took `ell` only and
  now takes any code (all languages). Of ten common words, two have a recording (γάτα under `ell`, μητέρα under
  `gre`), none a Wiktionary one, so the voice reads most words.
- Wiktionary data shown as it is: form rows join related nouns (σκυλί "masculine" → σκύλος, μητέρα "feminine" →
  πατέρας) and some tags repeat (καλή → καλός "nominative/accusative/vocative singular feminine/feminine").
- Catalog: 6 main pairs (en, ru, fr, pl, tr, zh ticked; el-el 38 MB unticked), 11 short-translation glossaries,
  the transcription, wordfreq, Paddle `el`, Tesseract `ell`.
- Emulator (LK39): σκυλος and ΣΚΥΛΟΣ → σκύλος, ποτε → ποτέ and πότε, πότε alone, ξαναρώτησε → ρωτάω "ξανα- « …",
  καλη ορεξη → καλή όρεξη; flag gr, Ω in the bubble; the Paddle download, its warm-up (131 ms) and the overlay over a
  rendered image (Arial): ΣΚΥΛΟΣ, Νίκος with the sentence "Ο κ. Νίκος είπε· «Έρχομαι αύριο»." translated whole,
  the lone Η as the letter and article, π.χ. inside its sentence.

## Group 4b research (2026-10-19)

Hindi (hi), Bengali (bn), Georgian (ka), Armenian (hy): new scripts, left to right, spaces between words. Sources:
Yomitan e7334a9 (`ext/js/language/`, GPL-3.0), the wty tree on Hugging Face (`latest/dict`, listed and downloaded
2026-10-19, CC BY-SA 4.0; sizes in MB of 10^6 bytes), wordfreq 912caf6 (Apache-2.0 code, data CC BY-SA 4.0), the tessdata
pins of `tessdata_catalog.py` (Apache-2.0), ML Kit text recognition 16.0.1 (the Devanagari AAR from the Gradle cache),
the Paddle devanagari model converted for the comparison (Apache-2.0), AOSP `fonts.xml` (main) and Google Fonts
metadata for the font lists, circle-flags 379588b (MIT), Wikimedia Commons and Lingua Libre (search API) for audio,
the OCR comparison of `ocr-engines.md` (screens of 2026-10-16/17, `testdata/ocr/compare/analyze-rot.txt`). Nothing in
this section was measured on a device.

### Yomitan at e7334a9

| lang | text processors | transforms |
|---|---|---|
| hi | none (descriptor with `exampleText` only) | none |
| bn | no descriptor | none |
| ka | none: no capitalization, so Mtavruli (ᲡᲐᲮᲚᲘ, headings in capitals) is never lowercased | `georgianTransforms` (`language: 'kat'`): 6 conditions, 3 transforms, 16 rules: 13 case and plural endings dropped (ები ებს ებების მა ს ის ით ად ო ში ზე შია ზეა), stem completion გნ → გნი, ნ → ნი, vowel restoration გ → გა; names are ids ("noun-adj-suffix-stripping") with English descriptions |
| hy | capitalization | none |

- Georgian's transforms name the language by its ISO 639-3 code (`kat`); `generate-inflections.mjs` matches
  `descriptor.language === iso` and would not find them.
- Yomitan cuts lookup candidates at characters that are not `\p{Letter}`, so an Indic word is cut at its vowel signs
  and viramas (Mn, Mc): the candidates of "पढ़ता" include "पढ" and "प"; `ShownResults` already drops matches that end
  before a combining mark. Georgian and Armenian letters are all `\p{Letter}`.
- QuickJS-ng 0.17.0 has Unicode tables that lowercase Mtavruli (U+1C90–U+1CBF → U+10D0–U+10FF) and Armenian capitals;
  `ԵՎ` lowercases to `եվ`, never to the ligature `և` (U+0587).

### Dictionaries (wty)

Counting as in group 4a (words with their own meaning); ✗ under 1,500 (then only the word count is given); – no file.
Cells: archive MB, words.

| from | en | ru | pl | zh | fr | it | tr | ko | ja | pt | es | vi | de |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| hi | 5.61 21.6k | 0.30 6.9k | 0.18 3.4k | 0.27 3.6k | 1,255 ✗ | 343 ✗ | 593 ✗ | 879 ✗ | 488 ✗ | 469 ✗ | 186 ✗ | 128 ✗ | 73 ✗ |
| bn | 1.67 9.2k | 0.11 2.2k | 1,235 ✗ | 1,459 ✗ | 279 ✗ | 0.10 1.9k | 363 ✗ | 349 ✗ | 270 ✗ | 293 ✗ | 115 ✗ | 125 ✗ | 103 ✗ |
| ka | 5.49 22.3k | 0.38 7.1k | 0.16 3.5k | 728 ✗ | 0.16 1.8k | 112 ✗ | 1,117 ✗ | 1,112 ✗ | 173 ✗ | 885 ✗ | 229 ✗ | 214 ✗ | 451 ✗ |
| hy | 7.50 17.6k | 0.42 9.5k | 0.12 3.0k | 0.19 2.5k | 0.07 1.6k | – | 0.20 3.3k | 1,424 ✗ | 1,283 ✗ | 417 ✗ | 245 ✗ | 116 ✗ | 678 ✗ |

- Pairs that pass: hi en ru pl zh; bn en ru it; ka en ru pl fr; hy en ru tr pl zh fr. Close to the line: bn→zh 1,459,
  hy→ko 1,424, hi→fr 1,255, bn→pl 1,235.
- No own-language edition and no glossaries for any of the four. The per-pair transcription files wty now publishes
  (`-ipa.zip` beside the pairs) are not what the catalog reads; it reads `dict/all/<lang>/wty-<lang>-ipa.zip`:
  hi 0.66 MB (34.1k words), ka 0.43 (25.4k), hy 0.64 (19.8k), bn 0.20 (7.7k); all four pass the 1,500-word rule.
- Form-of rows in →en: hi 275,411; ka 503,440; hy 942,487; bn 53,646. Headwords are NFC; lemma rows have no readings.
- Hindi: forms cover verbs and nouns (पढ़ता, पढ़ूँगा, लड़कों, किताबें, गई, हूँ, हैं, था). 11,648 Urdu-script rows tagged
  `Urdu` point to the Hindi lemmas (useful for Urdu text, harmless otherwise); 351 romanized rows ("khaṛā hoiye").
  Spellings: headwords write nukta (पढ़ना, बड़ा) and candrabindu (हूँ, माँ, लड़कियाँ) where much printed and typed text
  writes no nukta (पढना) and anusvara (हूं, मां, लड़कियां); none of those spellings is a headword. Keys without
  nukta: 38,248 headwords, 34,659 new spellings, 3,369 shared by two or more headwords. Candrabindu as anusvara:
  52,891 headwords, 49,784 new, 3,107 shared. wordfreq `small_hi` against the headwords: top 1,000 965 exact, 975
  with the candrabindu/anusvara keys, 980 with the nukta-free keys too; top 5,000 4,285 / 4,331 / 4,437; top 20,000
  12,811 / 13,050 / 13,574.
- Bengali: 6,297 form headwords are malformed by the extraction: a native word followed by a transliteration
  ("মাসটি (maśṭa (colloquial), maśṭi (formal))", "বইগুলো , boigulo (formal))") in 5,596 rows, template residue
  ("{{{3}}}ো ({{{4}}}o)") in 78. Keying the native word before the first space with `(`, `/` or `,` after it recovers
  5,268 spellings that are not headwords yet (বইটি, বইগুলো, বাড়িগুলো). Rows tagged "alternative Standard" link
  standard words to dialect lemmas (আমার → হাঁর); left as they are. Coverage on wordfreq `large_bn`:

  | top | exact | with the cleanup | with the cleanup and noun endings |
  |---|---|---|---|
  | 1,000 | 807 | 809 | 869 |
  | 5,000 | 2,790 | 2,812 | 3,328 |
  | 20,000 | 6,551 | 6,697 | 9,058 |

  The noun endings are our own list (classifiers and cases, not from Yomitan): -গুলোতে -গুলিতে -গুলোর -গুলির
  -গুলোকে -গুলিকে -গুলো -গুলি -টিতে -টাতে -টির -টার -টিকে -টাকে -টি -টা -খানা -খানি -দের -েরা -রা -য়ের -য় -ের
  -র -কে -তে -ে.
- Georgian: forms cover nouns and verbs (ვწერ, დავწერე, წიგნის, სახლში); 413 odd rows, `{{{SNM}}}` residue. Most of
  Yomitan's suffix rules duplicate form rows.
- Armenian: 15,761 headwords carry the emphasis mark ՛ U+055B (imperatives: կարդա՛, կազմակերպի՛ր), 11,003 of them with
  no plain twin; some forms carry the question mark ՞ U+055E inside the word (ուրո՞ւ). Text writes the question mark
  on the stressed vowel (ինչո՞ւ for ինչու), so it must go before lookup. 12,010 headwords contain the ligature և;
  capitals have no ligature (ԵՎ, Եվրոպա), so ԵՐԵՎԱՆ lowercases to երեվան and is not found; Եվրոպա itself keeps եվ.
  5,128 rows "orthography spelling X" (tag `traditional`) link the classical spellings to the lemma; their headword
  carries that prefix and needs it cut.
- Word lengths: lemmas longer than 16 UTF-16 units (the default scan length): hi 1.3%, bn 1.1%, ka 7.3%, hy 2.1%
  (cs 2.5%, el 2.6% for comparison); one-word forms longer than 16: ka 5.2%, hy 4.9% (cs 0.7%): ժողովրդավարություն
  (18), კონგრესმენებისთვის (18).

### Word frequency (wordfreq 912caf6)

- hi: `small_hi` only (148 KB, 26.7k words; 1,807 of the top 2,000 are headwords). It writes anusvara where the
  headwords write candrabindu, so हूं and हूँ should share the rank.
- bn: `large_bn` (1.56 MB, 238.7k words) and `small_bn`; 1,422 of the top 2,000 are headwords.
- ka, hy: no list (no frequency order, as for Belarusian).

### OCR

- ML Kit: the Devanagari recognizer (documented: Hindi, Marathi, Nepali, Sanskrit experimental) is a separate AAR,
  2.02 MB; its shared assets are byte for byte those already in the APK, so the APK grows by about 0.6 MB (Deva_ctc
  and Beng_ctc models, about 31 + 283 KB compressed each). It holds a Bengali model too, undocumented; measured on
  the Bengali screens it reads them well (below, "Bengali on the emulator"). Nothing for Georgian or Armenian.
- Paddle: `devanagari_PP-OCRv5_mobile_rec` (568 characters: danda ।, double danda ॥, nukta, candrabindu, Devanagari
  digits, “” ‘’ «»; no Bengali letters, no ZWJ/ZWNJ), converted to MNN for the comparison (7.9 MB,
  `testdata/ocr/compare/models/`); not uploaded. Paddle has no Bengali, Georgian or Armenian recognizer (its `ka` is
  Kannada).
- tessdata (fast / best MB): hin 1.12 / 11.90, ben 0.86 / 11.05, kat 2.52 / 4.49, hye 3.46 / 6.37.
- Measured (`analyze-rot.txt`, CER % over clean + hard): hi ML Kit 1.8, Paddle 2.7, tessdata fast 24.8, best 25.0
  (clean: fast 0.2, best 0.4, ML Kit 0.6, Paddle 0.9); bn fast 34.7, best 27.2 (clean 6.3 against 0.5); ka fast 22.9,
  best 22.8 (clean 1.1 both); hy fast 28.1, best 26.9 (clean 2.9 against 2.0). Cloud recognition: 0.0-0.3. By the
  recommendation rule (row "OCR engine choice"): hi ML Kit; bn, ka, hy tessdata fast, since best is not a third
  better anywhere (bn 22% fewer errors, though 6.3 against 0.5 on clean screens).
- `OcrScript` needs values for Georgian, Armenian and Bengali (Devanagari exists); `MlKitOcrEngine` maps DEVANAGARI
  to nothing today.

### Fonts

- No font of `catalog.json` covers any of the four scripts.
- System (AOSP `fonts.xml`): Noto Sans Devanagari, Bengali, Georgian, Armenian (plus UI variants) and Noto Serif
  Devanagari, Bengali, Georgian, Armenian; PostScript names `NotoSansDevanagari-Regular`, `NotoSerifDevanagari-Regular`
  and so on. The serif ones would be reached through `local()` with a unicode range per script, as Korean's
  `SystemFonts` are; the WebView falls back to the sans ones by itself. To check on the emulator that `local()` finds
  them.
- Downloadable (Google Fonts, all OFL): Devanagari 63 families (Hind, Mukta, Poppins, Martel, Tiro Devanagari Hindi,
  Noto Sans Devanagari…), Bengali 11 (Hind Siliguri, Anek Bangla, Tiro Bangla, Noto Serif Bengali…), Georgian only
  Noto Sans and Serif Georgian, Armenian Noto Sans and Serif Armenian and Handjet.
- Shaping is the WebView's and the system's (HarfBuzz): conjuncts, reordered matras (ि drawn before its consonant)
  and Bengali two-part vowels (ো = ে + া) need nothing from us.

### Hit testing, highlight and word starts

- `TextLayout` splits a word into code points and, without one engine box per code point, divides the word's box
  evenly. In Devanagari and Bengali code points are not left-to-right glyphs (ि is drawn before its consonant, a
  conjunct is one glyph), so a tap can land on any code point of the word. Highlighting merges boxes per line, so the
  whole word is still covered. On the comparison screens ML Kit, Paddle and Tesseract gave one character box per code
  point for these scripts; cloud recognition gives word boxes only.
- A tap that lands on a spacing vowel sign (Mc: ा ी ो in Devanagari, া ী in Bengali) finds nothing today:
  `SpacedLanguageSupport.isWordCharacter` counts letters, digits and Mn only, so the word start is not searched and
  `lookupStart` refuses a mark. Mc and Me marks, ZWJ and ZWNJ between letters, and Armenian's in-word marks
  (՛ U+055B, ՜ U+055C, ՞ U+055E, the apostrophe ՚ U+055A) must count as word characters; the Armenian hyphen ֊ U+058A
  as a connector.
- `wordBefore`, `hasPartner`, `isElision` and `digitsBefore` test `isLetter()`, so "डॉ." (Dr) gives an empty word and
  no abbreviation matches: marks must count as part of the word there too.

### Text rules

- Sentence ends: hi and bn । ॥ . ? ! …; hy ։ U+0589 and the colon OCR gives for it (not when a letter or digit
  follows at once: 10:30), ? ! …, while . (the mijaket) does not end a sentence, so abbreviations do not matter; ka
  . ? ! …. On the comparison screens every engine reads the danda as । (no | or I); Tesseract reads every Armenian
  ։ as a colon, cloud recognition about half of them.
- Abbreviations: hi डॉ प्रो सं ई ई.पू पृ क्र रु लि कं कि.मी से.मी पं स्व, and Latin letter names as initials (ए बी सी
  … जेड); bn ড ডা মো মোহা মি খ্রি পৃ লি কি.মি সে.মি সা রা and initials এ বি সি …; ka ე.ი მაგ წ ძვ ქ გვ ტ იხ შდრ
  პროფ აკად ("ა.შ." ends a sentence).
- Quotes: hi, bn “” ‘’; ka „“ and «»; hy «». The Armenian comma ՝ U+055D and the full stop ։ are not word characters.
- Digits: Devanagari and Bengali digits are Nd (`isDigit`); Western digits are more common in both.
- Normalization: the app never NFC-normalizes scanned text. OCR and typed text may give Bengali ো as ে + া (in
  that order, which NFC composes), and precomposed nukta letters (U+0958–U+095F, U+09DC, U+09DD, U+09DF) that NFC
  decomposes; wty headwords are NFC. A text processor applying NFC is needed for hi and bn.
- Georgian Mtavruli: capitals in headings (ᲡᲐᲥᲐᲠᲗᲕᲔᲚᲝ); a decapitalize processor (QuickJS lowercases them) finds the
  ordinary letters. Capitalizing the first letter is meaningless for Georgian.

### Audio

- Lingua Libre files: hi `hin` (Q1568) 3,430, bn `ben` (Q9610) 68,257, ka `kat` (Q8108) 881, hy `hye` (Q8785)
  3,233.
- Twenty common words each (water, house, book, cat, dog, mother, father, food or to eat, to read, good, friend,
  school, night, day, tree, boy, girl, man, city, love), Lingua Libre / Wiktionary / either: hi 20 / 0 (`Hi-`) / 20;
  bn 20 / 5 (`Bn-`) / 20; ka 1 / 9 (`Ka-წყალი.ogg`) / 10; hy 6 / 18 (`Hy-ջուր.ogg`) / 20. By the row "Audio order":
  Lingua Libre first for hi and bn, Wiktionary first for ka and hy.
- Georgian words recorded by one Lingua Libre speaker are filed under French (`LL-Q150 (fra)-…-წყალი.wav`, 9 of the
  20 words); a search by the Georgian item does not find them, and they are left out.
- Text to speech: Google's engine has Hindi and Bengali (Bangladesh and India) voices; Georgian and Armenian to be
  checked on the emulator.

### Names, flags, codes

- Translation services: `TranslationLanguageTable` already lists hi, bn ("Bangla"), ka and hy with the same codes for
  both services.
- Names (JDK and Android): Hindi, Georgian, Armenian everywhere; bn is "Bangla" in English and Vietnamese (Tiếng
  Bangla) and "Bengali" or its equivalent elsewhere (бенгальский, Bengalisch). Own names: हिन्दी, বাংলা, ქართული,
  հայերեն.
- circle-flags: `in`, `bd`, `ge`, `am` (paths and circles only, as the existing flags).
- Bubble glyph candidates: the first letter of the language's own name (हि, বা, ქ, Հ) or of the alphabet (अ, অ, ა,
  Ա; अ and অ look alike).
- E-ink codes: HI, BN, KA, HY. No Anki markers (no readings).

### Long lines

- Nothing found that grows with the line beyond the usual substrings: Georgian's 16 suffix rules and a Bengali list of
  about 30 endings cost a lookup per rule per substring, like the group 3 rules; import-time keys (nukta, candrabindu,
  Armenian marks) cost nothing at lookup. An Armenian և variant of the text doubles the candidates per եվ/եւ in a
  substring, rarely more than one or two. To be timed on the emulator with the test screens.
- Measured (2026-10-20): the ligature processor replaces every եվ/եւ at once, so a text gets one variant, not one per
  pair. The worst Georgian line found, 24 characters of ისის… (both -ს and -ის apply at every step), gives 24
  candidates in 4.4 ms in Node; ordinary lines 3-8. Lookups on the emulator took 2-4 ms (`Lookup found … in`).

### Plain fixes found (no owner decision)

- Word characters: Mc, Me, ZWJ/ZWNJ between letters and the Armenian in-word marks count as part of a word; the
  abbreviation helpers count marks as letters (see "Hit testing").
- NFC text processor for hi and bn.
- Armenian: a text processor dropping ՛ ՜ ՞ inside words; `HeadwordMarks` for the same marks after Armenian letters,
  so կարդա՛ is keyed as կարդա; a text variant with եվ and եւ as և (the original kept, Եվրոպա).
- Georgian: decapitalize added (glue or `addedPreprocessors`); `generate-inflections.mjs` matching the descriptor by
  ISO 639-3 too; the three rule names translated.
- wty cleanup at import, form rows only: the native word keyed when spaces and `(`, `/` or `,` follow it; rows with
  `{{{` dropped; the prefix "orthography spelling " cut (hy).
- Hindi frequency: anusvara and candrabindu spellings share the rank.
- `OcrScript` values; the ML Kit Devanagari AAR (row "ML Kit Chinese and Korean": a few MB may be bundled); Bengali
  measured on ML Kit; tessdata entries; the Paddle devanagari entry once its upload is approved.
- Catalog: `SOURCES` gains hi, bn, ka, hy; wordfreq `LANGUAGES` hi and bn; flags in, bd, ge, am.
- bn bundle: borrowed from hi (both without processors) under its own code, `iso639_3` `ben`.

### Hindi on the emulator (2026-10-19)

- ML Kit Devanagari on a rendered page (64 px, 1280×2856): the band draft in 3.1-3.3 s, the whole page in 6.0 s. It
  read ख़ाना without its nukta; the import key without nukta still finds ख़ाना, second after खाना. Cloud recognition
  keeps the nukta.
- A tap on a vowel sign (किताब at ता) looks the word up from its start, as the shared word rule intends.
- The serif system face loads by its PostScript and full names, not by the family name (`webview-fonts.md`).

### Bengali (2026-10-19)

- ML Kit: the Devanagari recognizer reads Bengali, CER 0.5% on the clean screens and 4.2% on the hard ones, against
  Tesseract ben fast 6.3% / 52.0% and best 0.5% / 43.3% (`ocr-engines.md`). Bengali reads with it, built in; the
  question of Tesseract's recommended model fell away.
- Wiktionary's Bengali entries have no part of speech, so Yomitan-style conditions never fit them. The app's own noun
  endings (`own-transforms.js`, 28 endings, longest first) are undone once, after at least two letters, and their
  results match entries without a part of speech (`glue.js` `untypedRules`). বইটির → বই, মায়ের → মা, ঘরে → ঘর;
  ছেলেদেরকে stops at ছেলেদের.
- The malformed form rows (romanizations after the form, endings apart, unfilled templates) are cleaned at import for
  every language (`form_headwords`, `dictionary-engine.md`). The 89 rows with য় apart stay; the -য় ending covers them.
- wordfreq `large_bn` is NFC (27,561 words write য় as য + nukta, none U+09DF), as the headwords are.
- The bn bundle takes Hindi's descriptor (exampleText replaced) with `composeCharacters` only: candrabindu and
  anusvara are not interchangeable in Bengali spelling.

### Georgian (2026-10-19)

- Yomitan's Georgian rules were dead twice: its descriptor files them under `kat`, while the language is `ka`, so
  Yomitan's own MultiLanguageTransformer never reaches them; and every condition needs a part of speech, which wty's
  Georgian entries never give (column 4 blank; the tags column says `n`, `pron sg`, `name`). The glue now takes each
  language's rules by the descriptor's iso, and `untypedRules` lets the Georgian ones match untyped entries when three
  letters or more stay (owner, question 11 of the group 4b plan).
- What the rules add over wty ka-en's headwords and form rows (`tools/ka_rules.py` in the session scratchpad): the
  form rows already find 76% of the words of Wiktionary's own examples (3,718 words) and 89% of the comparison
  screens' words. The rules with three letters left find 1.3-3% more words (გაკეთებას → გაკეთება, აკაკის → აკაკი);
  some words also show a wrong neighbor after the right entry (არის, "is", also shows არი). Without a minimum they
  cut ვადის down to ვ.
- The rules chain (n|adj in and out): წიგნს → წიგნ → წიგნი, reached twice (through -გნ and through -ნ); the engine
  keeps one. Stripping -მა from მა leaves an empty text, which the glue drops (in every language).
- Mtavruli: Yomitan gives Georgian no text processors; `addedPreprocessors` adds `decapitalize`
  (ᲡᲐᲥᲐᲠᲗᲕᲔᲚᲝ → საქართველო). Every language with decapitalize lowercases Mtavruli too, which is harmless.
- Inflection names: Yomitan's names are identifiers (noun-adj-suffix-stripping); `generate-inflections.mjs` gives them
  English labels (declension, nominative, dropped vowel) and the 13 other locales are translated by hand.
- OCR: no ML Kit or Paddle model. Tesseract kat over clean + hard screens: fast 22.9%, best 22.8% CER (clean 1.1%
  both); fast (2.5 MB against 4.5 MB) is the recommended model.
- Scan length 24 by default (`LanguageSupport.defaultScanLength`), the setting per language as before.
- No downloadable fonts; the page names Noto Serif Georgian by its local names, the sans falls back by itself.
- Audio: Wiktionary first (more common words recorded than on Lingua Libre, whose Georgian items are few and partly
  filed under French).
- On the emulator (LK44, 2026-10-20): Tesseract kat fast reads a rendered page in about 1 s and reads Mtavruli
  headings too; Google's speech engine has no Georgian voice, so a word without a recording stays silent. Windows'
  Sylfaen has no Mtavruli; test images use the phone's Noto Sans Georgian (`text-image.py` `FALLBACK` for digits and
  punctuation).

### Armenian (2026-10-20)

- Sentences end at the full stop ։ and at the colon OCR and keyboards write for it (Tesseract reads every ։ as `:`),
  not when a letter or digit follows at once (10:30). The period is the Armenian colon (mijaket) and never ends a
  sentence, so Armenian needs no abbreviations; the other spaced languages keep the period rules.
- The emphasis, exclamation and question marks ՛ ՜ ՞ sit on the stressed vowel inside a word (ինչո՞ւ, կարդա՛). They
  count as word characters (`SpacedLanguageSupport.wordMarks`, with the apostrophe ՚), so a tap after the mark still
  starts at the word's start; the comma ՝ and ։ do not. The Armenian hyphen ֊ joins words as - does.
- wty hy-en (17,645 words): the marks appear on form rows only (16,003 rows, 11,161 of them with no unmarked twin,
  mostly imperatives), never on a lemma, so `headwordMarks` keys the marked forms without them and
  `everyHeadword` stays false. The text processor `removeArmenianMarks` drops them from the looked-up text.
- The ligature և is written in 12,576 headwords; texts often write եվ (the reformed spelling's capitals have no
  ligature: ԵՐԵՎԱՆ) or the classical եւ. `armenianLigatureEv` tries և for both, the text as written kept (Եվրոպա is
  written with եվ). It doubles the candidates per եվ/եւ in a substring.
- 3,207 form rows of hy-en read "orthography spelling X" for the classical spelling X of a word (գիւղ → գյուղ); the
  import keys them as X (`form_headwords`). The label is in hy-en only.
- Yomitan's Armenian descriptor has capitalization only and no transforms; the app's two processors are added in
  `build.mjs` (`addedPreprocessors`).
- OCR: no ML Kit or Paddle model. Tesseract hye over clean + hard screens: fast 28.1%, best 26.9% CER (clean 2.9% and
  2.0%); fast (3.5 MB against 6.4 MB) is the recommended model, as for Georgian.
- Scan length 24 by default (5% of the forms longer than 16 characters).
- No downloadable fonts; the page names Noto Serif Armenian by its local names, the sans falls back by itself.
- Audio: Wiktionary first (18 of the 20 common words against 6 on Lingua Libre).
- On the emulator (LK45, 2026-10-20): Tesseract hye fast reads a rendered page in 0.35 s and keeps ՞ where it was;
  an all-caps word lists the lowercase form row before the capitalized name (ԵՐԵՎԱՆ: երևան of երևալ, then Երևան),
  as Yomitan ranks fewer text changes first.

### Questions for the owner

See `plans/2026-10-19-group-4b.md`.

## Group 4c research (2026-10-19)

Thai (th), Lao (lo), Khmer (km): scripts written left to right without spaces between words. Sources: Yomitan
e7334a9 (`ext/js/language/`, `translator.js`, `text-scanner.js`; GPL-3.0), the wty tree on Hugging Face
(`latest/dict`, revision 2026.10.11, listed and downloaded 2026-10-19, CC BY-SA 4.0; sizes in MB of 10^6 bytes),
wordfreq 912caf6, PyThaiNLP 8b2b9a6 (`pythainlp/corpus/`, licenses in `corpus_license.md`), ICU4J 66.1 and 75.1
(Unicode license) for word segmentation, the tessdata pins of `tessdata_catalog.py` (Apache-2.0), the Paddle Thai
model converted for the comparison (Apache-2.0), AOSP `fonts.xml` (main, 15, 16) with the font files and Google Fonts
metadata, circle-flags 379588b (MIT), Wikimedia Commons and Lingua Libre (search API) for audio, Google's TalkBack
language list for voices, the OCR comparison of `ocr-engines.md` (`testdata/ocr/compare/analyze-rot.txt`). Nothing
in this section was measured on a device.

### Yomitan at e7334a9

- Descriptors for th, lo and km (`iso639_3` tha, lao, khm; `exampleText` อ่าน, ອ່ານ, អាន) with no text processors and
  no transforms. The bundles build from them as they are.
- The translator's `_getNextSubstring` drops one character at a time with letter resolution; word resolution cuts at
  the last character that is not `\p{Letter}`, which here includes every vowel and tone mark above or below (Mn), so
  only letter resolution works, as for Japanese and Chinese.
- The browser's text scanner has no segmentation: in its default letter mode the lookup starts at the caret; word mode
  (`SCAN_RESOLUTION_EXCLUDED_LANGUAGES` lists ja, zh, yue, ko, not these three) moves the start back to the last
  character matching `WORD_DELIMITER_REGEX` (`[^\w\p{L}\p{N}]`), which in Thai is the nearest mark or space.

### Dictionaries (wty)

Counting as in group 4a (words with their own meaning); ✗ under 1,500 (then only the word count is given); – no file.
Cells: archive MB, words.

| from | en | ru | pl | zh | fr | it | tr | ko | ja | pt | es | vi | de | own |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| th | 2.72 16.7k | 0.16 3.3k | 0.19 3.5k | 0.28 2.4k | 640 ✗ | 244 ✗ | 601 ✗ | – | 0.14 1.9k | 567 ✗ | 185 ✗ | 570 ✗ | 423 ✗ | 2.94 22.4k |
| lo | 0.27 2.3k | 1,418 ✗ | 650 ✗ | 549 ✗ | 150 ✗ | – | 117 ✗ | 378 ✗ | 93 ✗ | – | – | 219 ✗ | – | – |
| km | 0.96 9.2k | 0.23 5.9k | 845 ✗ | 939 ✗ | 214 ✗ | – | 262 ✗ | 134 ✗ | 58 ✗ | 119 ✗ | – | 461 ✗ | 155 ✗ | – |

- Pairs that pass: th en ru pl zh ja and the Thai edition (th-th, listed unticked by the row "Own-language
  dictionaries"); lo en only (lo-ru 1,418 is just under); km en ru. Outside the gloss languages: lo-th 9,898 words,
  km-th 1,715.
- Glossaries: only Thai has them (from the Thai Wiktionary: a Thai sense label and the translations, เดิน → walk);
  th-en passes (0.08 MB, 2,066), the others have 194-963 words. None for Lao or Khmer.
- Transcriptions (`dict/all/<lang>/wty-<lang>-ipa.zip`): th 0.58 MB (35,452 words), lo 0.25 (13,255, Vientiane and
  Luang Prabang variants), km 0.07 (2,318); all pass. The Khmer file mixes a transliteration, the Wiktionary
  romanization and IPA under one label and covers 20.5% of the km-en headwords (Thai 99.9%, Lao 98.8%).
- Rows (→en): th 29,681, a third form-of (9,820); lo 3,532 (836); km 14,619 (3,677). The ru dictionaries have no
  form-of rows. Form-of tags: `abstract-noun` (การ/ความ + X → X; Lao ການ/ຄວາມ, Khmer ការ/ភាព: th 5,237, lo 514, km
  2,797), `alternative`, `alt-of`, and in Thai and Lao `classifier`: 1,900 Thai rows on 150 classifiers (คน 386, ตัว
  306, ใบ 101), 124 Lao (ໂຕ 43), none in Khmer; a lookup of คน brings 386 "classifier of" entries besides its own, as
  cái did in Vietnamese. 70 Thai letters link to their Morse code (ศ → `...`); 604 exact duplicate form-of rows in
  th-en (lo 50, km 80).
- No readings (`row[1]` is empty). The en dictionaries carry a romanization only in the structured content's grammar
  line (`word • (roman)`): Thai Paiboon (pâap-bplaa-grɔ̀ɔp), Lao Wiktionary transliteration (khǭp khūa), Khmer
  Wiktionary romanization, 8.2% of it corrupted for words with a minor first syllable (ចចក cɑɑcɑcɑɑk for cɑcɑɑk). The
  ru dictionaries have none. The popup shows the line with the definition; `{reading}` stays empty.
- Normalization: NFC changes no headword. Thai is clean (ำ precomposed in 1,033 headwords, never split; no tone
  mark typed before a vowel), with a few old spellings ํ + tone + า (นํ้า). Lao mixes spellings: ໜ ໝ 120 headwords
  against ຫນ ຫມ 28, ຫຼ 52 against ຫລ 43, and 53 words stand in both spellings (ໜາ/ຫນາ, ເຫຼັກ/ເຫລັກ); 50 Lao lemma rows
  say "dated spelling of". Khmer subscript DA ្ដ (225 headwords) and TA ្ត (460) print alike, and 10 words stand in
  both (ក្ដារ/ក្តារ, សួស្ដី/សួស្តី); km-ru has about 60 headwords typed in legacy order.
- Prefixes: 19% of Thai headwords (lo 17%, km 19%) are the start of a longer one, mostly through the form-of
  families การ/ความ (การ+X: 3,450 form-of headwords against 701 lemmas). Single-code-point headwords: th 86, lo 14,
  km 112 (letters, classifiers, symbols). Of 200 sampled Thai lemmas, 118 split into two headwords; about 55% of the
  sample are real compounds or phrases (ฝ่าย|ซ้าย, ธรรม|ศาสตร์, หาย|ไป), 9 splits are wrong (จิต|ระ, คะ|นอง). Lao
  about 22% (many numerals), Khmer about 33%.
- Odd rows: Khmer phrasebook sentences as headwords (ខ្ញុំមិនចេះនិយាយភាសាខ្មែរទេ); 134 Thai rows with an empty part of
  speech (classifier senses, ฯ, ๛, Braille); one `{{place|…` residue row per dictionary; no `{{{`.

### Word frequency

- wordfreq 912caf6: no th, lo or km list.
- PyThaiNLP: `tnc_freq.txt` (words and counts from the Thai National Corpus, 106,122 words, 1.6 MB; SHA-256
  554984d6…dd0dda at 8b2b9a6) and `ttc_freq.txt` (Thai Textbook Corpus, 19,493 words), both listed in
  `corpus_license.md` among the word lists released under CC0-1.0. Against the th-en headwords: 10,311 headwords have a
  TNC count; of the TNC's top 1,000 words 954 are headwords, top 5,000 4,013, top 10,000 6,405, top 20,000 8,690;
  headwords cover 90.7% of the corpus' tokens. Missing from the top 300: ๆ, ‘, พ.ศ., ก็คือ, ล่ะ, หรือไม่.
- Nothing found for Lao or Khmer.

### OCR

- ML Kit: no Thai, Lao or Khmer.
- Paddle: `th_PP-OCRv5_mobile_rec` (Apache-2.0), converted to MNN for the comparison (`model.mnn` 7.9 MB, `dict.txt`
  1.8 KB; `testdata/ocr/compare/models/`), not uploaded. Its character list has every Thai letter, mark and digit
  except ฃ (obsolete), ๎ ๏ ๛ (rare), plus Latin letters, digits and common punctuation; nothing for Lao or Khmer.
  It splits a line into phrases at spaces (`TextRecognition.words`, separator " "), with CTC character positions.
- tessdata (fast / best MB): tha 1.07 / 7.61, lao 6.39 / 13.53, khm 1.45 / 8.10.
- Measured (`analyze-rot.txt`, CER % over clean + hard; clean / hard in brackets):
  - th: Paddle 1.3 (0.4 / 1.8), Tesseract fast 20.8 (0.8 / 33.2), best 21.0 (0.9 / 33.4); cloud 0.0. By the
    recommendation rule Paddle.
  - lo: Tesseract fast 37.6 (16.8 / 53.9), best 37.3 (17.0 / 53.3); cloud 3.6 (4.3 / 3.0). Tesseract reads Lao marks
    as lines of their own and drops lines.
  - km: Tesseract fast 38.3 (8.9 / 57.1), best 37.9 (8.9 / 56.6); cloud 0.1 (0.0 / 0.2).
  - For scale: models not offered as unusable read 13.4% (Paddle Latin on Vietnamese) and 26.4% (Tesseract on
    Persian) on the clean screens.
- How each engine splits the text (clean light screens):
  - Cloud recognition: Thai and Khmer by word, no character boxes; Lao by syllable, and it read ຕະຫຼາດ as ຕະຫຼาด,
    the last two letters Thai (า ด, the same shapes as າ ດ).
  - Paddle: phrases between spaces.
  - Tesseract: Thai one "word" per character or cluster (ห | ้ | อ | ง | ส | ม | ุ | ด; issue #2702), so
    `tesseractPage`, which gives every word the language's separator, would put a space between all of them; Thai
    ำ comes out as ํ + า (ก ํ า ห น ด). Lao and Khmer as phrases between spaces.
- `OcrScript` needs THAI, LAO and KHMER; only THAI maps to an on-device recognizer besides Tesseract.
- `scripts/text-image.py` draws with Pillow, which shapes Thai and Khmer only with libraqm (not on this PC): marks
  stack wrongly and Khmer subscripts come apart. The comparison screens were drawn in the emulator's browser; new
  test screens go the same way.

### Fonts

- No font of `catalog.json` covers Thai, Lao or Khmer (only ฿ U+0E3F in Inter, M PLUS 1p and LXGW WenKai TC).
- System (AOSP `fonts.xml`, the same entries on main, 15 and 16; `und-Thai`, `und-Laoo`, `und-Khmr`, each with an
  `elegant` Sans, the Serif as its `fallbackFor="serif"`, and a `compact` UI face). Names for `local()` read from the
  files:

  | Font | PostScript | Full name | Loops |
  |---|---|---|---|
  | Thai Sans (v1.04) | `NotoSansThai` | `Noto Sans Thai` | looped |
  | Thai UI | `NotoSansThaiUI` | `Noto Sans Thai UI` | looped |
  | Thai Serif | `NotoSerifThai-Regular` | `Noto Serif Thai Regular` | looped |
  | Lao Sans | `NotoSansLao-Regular` | `Noto Sans Lao Regular` | loopless |
  | Lao UI | `NotoSansLaoUI` | `Noto Sans Lao UI` | looped |
  | Lao Serif | `NotoSerifLao-Regular` | `Noto Serif Lao Regular` | looped |
  | Khmer Sans | `NotoSansKhmer-Regular` | `Noto Sans Khmer` (15, v1.901), `Noto Sans Khmer Regular` (16, v2.004) | – |
  | Khmer UI | `NotoSansKhmerUI` | `Noto Sans Khmer UI` | – |
  | Khmer Serif | `NotoSerifKhmer-Regular` | `Noto Serif Khmer Regular` | – |

  - AOSP's Thai Sans is the 2011 looped design, though its metadata says loopless (checked on 22 consonants with a
    head loop: 22 of 22 looped). `NotoSansThai-Regular` and `Noto Sans Thai Regular` name the newer loopless v2
    (Google Fonts) and must not go into the system list.
  - The system Lao Sans lacks 15 rare letters (U+0E86, 0E89, 0E8C, 0E8E-0E93, 0E98, 0EA0, 0EA8, 0EA9, 0EAC, 0EBA;
    Pali and dialect); Thai 87/87 and Khmer 146/146 are complete.
  - Android 15's `NotoSansKhmer-VF.ttf` (1.901) has a non-standard `wght` axis 26-190 (Regular 90). Declared as read
    from `fvar` ("font-weight: 26 190"), a request for 400 clamps to 190 and the text may come out black; 16 has
    100-900. A range should be declared only when the axis reaches 400. To check on the emulator.
  - Natural line heights (hhea, em): Thai 1.37, Lao 1.64 (UI 1.33), Khmer 1.36.
- Looped and loopless Thai: looped is the printed form of textbooks and formal text, loopless that of signs, apps and
  headlines (Typotheque and Chulalongkorn, 2024: looped rated more readable at every age and read slightly faster;
  younger readers cope with loopless at small sizes). Khmer has no such split; Lao has both.
- Downloadable (Google Fonts, all OFL; popularity rank, TTF size, script subset as WOFF2):
  - Thai (34 families, every one with all 87 letters): Kanit (22, loopless, 173 KB), Prompt (36, loopless, 169 KB),
    Chakra Petch (107, display), Noto Sans Thai (114, loopless, variable, 219 KB), Sarabun (143, looped, 90 KB), IBM
    Plex Sans Thai (230, loopless), Noto Serif Thai (303, looped), Noto Sans Thai Looped (628, looped), IBM Plex Sans
    Thai Looped (749).
  - Lao: Noto Sans Lao (loopless), Noto Sans Lao Looped, Noto Serif Lao, Phetsarath (65 of 82 letters).
  - Khmer (28 families): Noto Sans Khmer (79, 360 KB), Nokora, Kantumruy Pro (530, variable, 200 KB), Hanuman (615,
    serif, 126 KB), Battambang, Koh Santepheap, Noto Serif Khmer; Bayon, Angkor, Koulen, Moul are display faces.
  - Authors' files: Noto slim variable TTFs at `notofonts/notofonts.github.io` (`fonts/<Family>/unhinted/
    slim-variable-ttf/`; Sans Thai 32 KB, Thai Looped 57, Serif Thai 42, Sans Lao 34, Lao Looped 59, Serif Lao 44,
    Sans Khmer 120, Serif Khmer 170; `wght` 400-700); Cadson Demak's TTFs committed in their repositories
    (`cadsondemak/Sarabun`, `kanit`, `prompt`); Kantumruy Pro at `sovichet/kantumruy-pro` (`fonts/variable/`); Danh
    Hong's Khmer fonts at `danhhong/<Name>` (`Release/ttf/`). GitHub detects OFL-1.1 on all of them.
- Shaping (stacked marks, Khmer subscripts, leading vowels) is the WebView's and the system's; nothing from us.

### Word starts: where the word under the tap begins

- Today: spaced languages look up from the start of the space-separated word under the aim (`wordStartOffset`);
  Japanese and Chinese (`SearchResolution.LETTER`, empty separator) look up from the aimed character; Vietnamese also
  starts one and two words before the aim (`earlierStartOffset`, `EarlierStarts.wordStarts`, which finds the starts
  after spaces). None of this works for Thai, Lao or Khmer: a space ends a phrase or a sentence, not a word, and the
  median headword is 6-7 characters, so a start at the aimed character lands mid-word most of the time (ผู้หญิง tapped
  on หญิง finds หญิง "woman" alone; โรงเรียน tapped on เรียน finds "to study").
- ICU's word break iterator segments the three scripts with dictionaries (thaidict, laodict, khmerdict; ICU 4.x on).
  Android has had `android.icu.text.BreakIterator` since API 24, ICU 66 at API 30 (the app's minSdk). Checked with
  ICU4J 66.1 and 75.1 (the same output) on sample sentences:
  - th: ฉัน|ไป|โรงเรียน|ทุก|วัน; เด็กๆ|ชอบ|กิน|ข้าว|ผัด|มาก; กรุงเทพฯ| |เป็น|เมือง|หลวง|ของ|ประเทศไทย;
    นายก|รัฐมนตรี|ประกาศ|นโยบาย|ใหม่|เมื่อ|วาน|นี้; ผู้|หญิง|คน|นั้น|กำลัง|อ่าน|หนังสือพิมพ์|อยู่|ที่|ห้อง|สมุด;
    เขา|มี|ตากลม|และ|ผม|ยาว (right); ผ้าตา|กลม|แห้ง|เร็ว (wrong: ผ้า|ตาก|ลม); ราคา| |150| |บาท| |ฯลฯ ...;
    ฉัน|ชอบ| |iPhone| |มากกว่า.
  - lo: ຂ້ອຍ|ໄປ|ໂຮງຮຽນ|ທຸກມື້; ປະເທດ|ລາວ|ມີ|ແມ່|ນ້ຳ|ຂອງ (ແມ່ນ້ຳ "river" split); ພວກ|ເຮົາ|ກິນເຂົ້າ|ໜຽວ|ທຸກ|ວັນ
    (ເຂົ້າໜຽວ "sticky rice" split wrongly).
  - km: ខ្ញុំ|ទៅ|សាលារៀន|រាល់ថ្ងៃ; ប្រទេស|កម្ពុជា|មាន|រាជធានី|ភ្នំពេញ|។; កូន|ៗ|របស់|គាត់|ទៅលេង|នៅផ្ទះ|យាយ.
  - It splits compounds into their parts more often than it errs: of the wty →en headwords without spaces, one ICU
    piece th 52.9%, two 32.9%, three 10.0%, four or more 4.1%; lo 59.6 / 28.5 / 9.8 / 2.1; km 84.9 / 10.1 / 3.2 / 1.8.
    Loanwords fall apart (กรด|นิว|คลี|อิก, កា|បូន|អ៊ី|ដ្រា|ត).
  - The JDK's `java.text.BreakIterator` has an older Thai dictionary (ผู้หญิง, ห้องสมุด, นายกรัฐมนตรี whole; ตาก|ลม
    wrong) and none for Lao or Khmer (whole phrases). Unit tests run against android.jar stubs, where `android.icu`
    returns defaults, so tests need ICU4J (66.1 matches API 30) behind an interface, or Robolectric (not used).
  - Cost: a whole 2,000-character line 0.2-0.3 ms, a new iterator over a 64-character window 0.01-0.04 ms (desktop
    JVM); nothing to worry about on a phone.
  - ICU's sentence iterator does not split Thai at spaces (one "sentence" per line); Khmer splits after ។.
- Ways to find the start (question 1 of the plan):
  - ICU words: the lookup starts at the start of the ICU word under the aim and at the starts of the one or two ICU
    words before it, keeping from an earlier start only words that reach the aimed one, earliest first, then the
    aimed word's own results (the Vietnamese rule). ห้องสมุด tapped on สมุด: ห้องสมุด "library" first, then สมุด
    "notebook". Two earlier words reach every part of 95.8% of Thai headwords (98% of Lao and Khmer), three 98.8%.
    Three lookups per tap with two earlier words, as Vietnamese.
  - Japanese-style: from the aimed character, the user aims at the first letter; no segmentation.
  - Every start: every character start in a window before the aim (up to the scan length), keeping the longest
    dictionary word that covers the aim; independent of ICU's dictionary, about 16 lookups per tap and more junk
    (short words that end at the aim).
- Cloud recognition already returns Thai and Khmer split into words (ห้องสมุด | เปิด | ตั้งแต่ | เก้า | โมง; Khmer
  បណ្ណាល័យ | បើក | នៅ | ម៉ោង), Lao by syllable (ສາ | ທາ | ລະ | ນະ). `TextLayout` flattens words into characters, so
  those boundaries are lost today; ICU covers every engine and app text alike.

### Combining marks: hit testing, highlight, lookup boundaries

- Thai and Lao: vowels above and below and the tone marks are Mn (ั ิ ี ึ ื ุ ู ็ ่ ้ ๊ ๋ ์; Lao ັ ິ ີ ຶ ື ຸ ູ ົ ່ ້ ໊ ໋ ໌ ໍ);
  the following vowels า ำ ะ ๅ (Lao າ ຳ ະ) and the leading vowels เ แ โ ใ ไ (Lao ເ ແ ໂ ໃ ໄ) are letters (Lo), the
  leading ones stored before the consonant they are read after. ฯ and ຯ are Lo, ๆ and ໆ Lm.
- Khmer: dependent vowels are Mc (ា ើ ោ ៅ ះ) or Mn (ិ ី ឹ ឺ ុ ូ ួ), signs ់ ៉ ៊ ៍ ៎ ៏ ័ Mn, the coeng ្ (Mn, ccc 9)
  puts the next consonant below: ខ្ញុំ is ខ ្ ញ ុ ំ, five code points, one cluster.
- `TextLayout.splitBox` divides a word box evenly per code point when the engine gives no box per code point (cloud
  recognition, Tesseract's Thai), so a mark gets a slot of its own: ห้องสมุด gets 8 slots for 6 visible columns and the
  rest of the word drifts left. Marks need no width (Mn, the coeng and the consonant after it) when the box is
  divided; highlighting merges per line, so whole words stay covered either way.
- A lookup must not start on a mark, a following vowel or the consonant right after a leading vowel, nor on the
  consonant after a coeng; a match must not end before a mark or a following vowel, nor end with a leading vowel or a
  coeng. `ShownResults` already drops matches that end before a combining mark (Mn only); Thai and Lao following
  vowels are Lo and Khmer's spacing vowels Mc, so neither is caught today: กาง looked up from ก would show the letter
  ก as a word.
- `SpacedLanguageSupport.isWordCharacter` (letters, digits, Mn) is not used by these languages, which need their own
  support class (empty separator, `SearchResolution.LETTER`, as Chinese); its word characters must take Mc as well
  (Khmer).

### Text rules

- Sentence ends:
  - th: no full stop. A space separates sentences and also clauses, numbers and foreign words, and stands around ๆ
    (เด็ก ๆ, the Royal Institute's spelling) and ฯลฯ; ? ! … and line ends. A period is used in abbreviations (พ.ศ.,
    ม.ค., ดร., อย.) and never ends a sentence.
  - lo: as Thai; a period is used now and then.
  - km: ។ (khan) and ៕ (end of a section), ? ! …; a space separates phrases, not sentences.
- Repetition marks: ๆ ໆ ៗ repeat the word before. Thai headwords write it after a space (กล้วย ๆ, ขำ ๆ, 30 headwords;
  one without: นานๆ); Lao (ຊ້າໆ) and Khmer (ក្មេងៗ) without. Text writes Thai both ways. ICU keeps เด็กๆ as one word and
  splits កូន|ៗ.
- ฯ abbreviates (กรุงเทพฯ, นายกฯ; 5 headwords with it); ฯลฯ is "etc."; ຯ the same in Lao.
- Digits: Thai ๐-๙, Lao ໐-໙, Khmer ០-៩ are Nd; Western digits are more common.
- Quotes: th and lo “” ‘’; km «» and “”.
- Normalization: ำ (U+0E33) and ຳ (U+0EB3) decompose only by compatibility (ํ + า, ໍ + າ), so NFC leaves Tesseract's
  ํา as two characters, and older typing puts the tone mark between them (นํ้า for น้ำ); Thai typed with the tone mark
  before the vowel above (ก่ิ for กิ่) is not reordered by NFC (the vowel's class is 0). Lao ໜ ໝ ຫຼ and ຫນ ຫມ ຫລ, and
  Khmer subscript ្ដ and ្ត, are both in use (see "Dictionaries"); OCR gives one form of each. Khmer text from apps
  carries zero-width spaces (U+200B) between words; no →en headword has one (lo-ru 5, km-ru 3).
- Abbreviations with periods are headwords (อย., ส.ค.ส., ดี.ซี.); `tnc_freq` lists พ.ศ.

### Scan length

- Headwords without spaces longer than 16 characters (the default scan length): th 1.9% (309; 20: 0.6%), lo 0.1%,
  km 0.9% (cs 2.5%, el 2.6%). The row "Scan length" of the group 4b plan (24 for Georgian and Armenian, 16 for the
  other languages) covers these three; nothing to ask.

### Audio

- Lingua Libre (items, title codes, files, speakers): th Q9217 `tha` 289 files, 4 speakers (one recorded 244);
  lo Q9211 `lao` 9 files (place names, a greeting, ພາສາ, ຮ້ອຍ), 1 speaker; km Q9205 `khm` 13 files, 1 speaker.
- Twenty common words (as in group 4b), Lingua Libre / Wiktionary / either: th 11 / 2 (`Th-น้ำ.ogg`, `Th-แมว.ogg`) /
  13; lo 0 / 0 / 0; km 0 / 0 / 0. The app's search queries find every known file in these scripts (no segmentation
  problem). By the row "Audio order": Lingua Libre first for Thai; Lao and Khmer tie at nothing, the default order.
- Commons by prefix: `Th-` 238 audio files, 42 with Thai titles (the rest romanized: `Th-rak.ogg`, tone tests, names;
  the app cannot find those without a romanization); `Km-` 9 Khmer words; `Lo-` and `Lao-` hold no Lao words.
- Text to speech: Google's Android engine lists Thai (Thailand) and Khmer (Cambodia), not Lao (TalkBack's language
  list; not tested on a device); eSpeak NG has Thai only. Lao has no word audio and no voice: the speaker button
  would find nothing. To check on the emulator: which voices Google's engine installs for th and km.

### Names, flags, codes

- Translation services: `TranslationLanguageTable` lists th, lo and km for both services, same codes.
- Names (JDK = ICU 75): Thai / тайский / タイ語 / 泰语 / Thailändisch / Tiếng Thái; Lao / лаосский / ラオ語 / 老挝语 /
  Laotisch / Tiếng Lào; Khmer / кхмерский / クメール語 / 高棉语 / Khmer / Tiếng Khmer. Own names: ไทย, ລາວ, ខ្មែរ.
- Bubble glyph candidates: a letter of the language's own name (ท from ไทย, whose first character is the leading
  vowel ไ; ລ; ខ) or the first letter of the alphabet (ก, ກ, ក; ก and ກ look alike).
- E-ink codes: TH, LO, KM. No Anki markers (no readings in the dictionaries).
- circle-flags 379588b: `th.svg`, `la.svg`, `kh.svg` (paths and one circle, as the existing flags; they convert
  with the repository's own `vector_drawable()` to 765-860 bytes); `scripts/flags/` needs th, la, kh in `COUNTRIES`.

### Long lines

- ICU segmentation is linear and cheap (above); the lookup costs the usual substrings per start: three starts with
  two earlier words, about 16 exact lookups each (no inflection rules). The every-start way would cost about 16
  starts of the same. To be timed on the emulator with the test screens.

### Plain fixes found (no owner decision)

- Clusters: no lookup start on a mark, a following vowel, the consonant after a leading vowel or after a coeng; no
  match ending inside a cluster (before a mark or a following vowel, on a leading vowel or a coeng); `TextLayout`
  gives marks no width when it divides a word box; Khmer word characters take Mc.
- Tesseract words of these scripts joined by the gap between their boxes, not by the language's separator (Thai
  comes one character per word).
- Thai text: ํา read as ำ and ํ + tone + า as tone + ำ (นํ้า → น้ำ); a tone mark typed before the vowel above moved after
  it; the repetition mark tried with and without a space before it (เด็กๆ, เด็ก ๆ).
- Lao text: ໍາ read as ຳ; ຫນ ຫມ ຫລ and ໜ ໝ ຫຼ match each other; Thai letters of the same shape in a Lao word written as
  the Lao ones (cloud recognition's ຕະຫຼาด).
- Khmer text: subscript DA and TA match each other; zero-width spaces dropped from the looked-up text and taken as
  word boundaries.
- Form-of rows: Thai and Lao `classifier` rows left out as the Vietnamese ones are (row "Vietnamese classifiers");
  Morse code links dropped; duplicate rows merged.
- Sentence ends: Khmer ។ ៕ ? ! …; a period after Thai or Lao letters never ends a sentence.
- System font weights: a variable font's range declared only when its axis reaches 400 (Android 15's Noto Sans Khmer).
- `OcrScript` THAI, LAO, KHMER; tessdata entries for tha, lao, khm; the Paddle Thai entry once uploaded.
- Catalog: `SOURCES` gains th, lo, km; flags th, la, kh.

### Questions for the owner

See `plans/2026-10-19-group-4c.md`.

## Group 6 research (2026-10-20)

Catalan, Galician, Icelandic, Afrikaans, Tatar, Bashkir and Sanskrit (request 70; Taiwanese Hokkien, Russia's other
languages and the rest of the long list deferred with reasons): research, proposed blocks 6a-6d, technical choices and
the deferred questions in `plans/2026-10-20-group-6-research.md`. Not started: the build order is the owner's call.

## Group 5b research (2026-10-19)

Welsh, Irish and Scottish Gaelic (Breton researched, not built): research, decisions and work in
`plans/2026-10-19-group-5b.md`.

## Group 5a research (2026-10-19)

Swedish, Norwegian, Danish, Romanian, Latin, Lithuanian, Slovak, Slovenian, Esperanto, Albanian and Swahili (Basque and
Maltese researched, not built): research, decisions and work in `plans/2026-10-19-group-5a.md`.

## Group 5d research (2026-10-19)

Bulgarian, Macedonian and Mongolian: research, decisions and work in `plans/2026-10-19-group-5d.md`.

## Group 5c research (2026-10-19)

Finnish, Estonian, Hungarian, Azerbaijani and Uzbek: research, decisions and work in `plans/2026-10-19-group-5c.md`.

## Group 5e research (2026-10-19)

Cantonese, Kannada, Tamil, Telugu and Marathi: research, OCR measurements, decisions and work in
`plans/2026-10-19-group-5e.md`.

## Group 4d research (2026-10-19)

Arabic (ar), Persian (fa), Hebrew (he): scripts written right to left with spaces between words. Urdu (ur), Egyptian
Arabic (arz) and Yiddish (yi) were looked at for comparison; they belong to group 5f (row "Group 5"; the owner put
Urdu after these three, question 69 of `plans/2026-10-19-group-4d.md`). Sources: Yomitan e7334a9 (`ext/js/language/`,
`translator.js`, `text-scanner.js`; GPL-3.0), the wty tree on Hugging Face (`latest/dict`, release 2026-10-11, wty
commit 602ffb3; listed and downloaded 2026-10-19, CC BY-SA 4.0; sizes in MB of 10^6 bytes) and wty's source at
602ffb3 (MIT), wordfreq 912caf6 (code Apache-2.0, data CC BY-SA 4.0), the tessdata pins of `tessdata_catalog.py`
(Apache-2.0), Paddle's `arabic_PP-OCRv5_mobile_rec` (Hugging Face, Apache-2.0) as converted for the comparison,
cloud recognition probed from the PC with a client that mirrors `LensProtocol`, AOSP `fonts.xml` (main) and Google
Fonts metadata, circle-flags 379588b (MIT), Wikimedia Commons and Lingua Libre (search API) for audio, Google's
TalkBack and Cloud TTS voice lists, JDK 21 (CLDR 42) for names, the OCR comparison of `ocr-engines.md`
(`testdata/ocr/compare/analyze-rot.txt`). Nothing in this section was measured on a device except the OCR comparison
(emulator, 2026-10-16/17).

### Yomitan at e7334a9

- Descriptors: ar (`ara`) with text processors removeArabicScriptDiacritics, removeTatweel, normalizeUnicode (NFKC),
  addHamzaTop, addHamzaBottom, convertAlifMaqsuraToYaa and 50 transforms; arz the same plus convertHaToTaMarbuta; fa
  (`fas`) only removeArabicScriptDiacritics; he (`heb`) no processors and no transforms; no descriptor for ur.
  `recommended-settings.json` sets word scan and search resolution for ar, arz and he.
- removeArabicScriptDiacritics removes 16 code points (U+0618-061A, U+064B-0652, U+0653-0656, U+0670) as a variant;
  NFKC turns presentation forms into letters (ﻛ → ك, ﻻ → لا) and composes ا + U+0654 into أ. No processor maps أ إ آ ٱ
  to ا: the two hamza processors only add a hamza to the first bare alif anywhere in the text (`replace('ا', 'أ')`),
  so 145 of 299 candidates of 17 test words are hamza variants of the article or a medial ā (ألكتاب, كتإبهم), and a
  hamza after a proclitic is never restored (الاسلام gives اسلام, never إسلام). Final ى → ي only one way; ه → ة only in
  arz. Persian ی ک against Arabic ي ك: no mapping anywhere. ZWNJ: never removed. Hebrew niqqud: nothing for he (yi
  removes U+05B0-05C7 and keeps cantillation); final letters only in Yiddish's postprocessor.
- Arabic transforms: 26 conditions, 50 transforms, 7,157 rules (178 prefix, 409 suffix, 6,570 prefix and suffix
  "sandwich" regexes): noun prefixes و ف ب ك ل ال and their chains (وال بال كال لل), possessive suffixes, ـة,
  accusative ـا ـاً, duals, sound plurals ـات ـون ـين, perfect and imperfect verbs with proclitics and object pronouns
  (سيكتبون → كتب), imperatives. Not covered: broken plurals (كتب → كتاب, reached only through form-of rows), weak
  verbs, the passive, adjectives as a part of speech. Every affix is unvocalized; a vocalized stem matches, so vocalized
  text deinflects through its diacritics-free variant. Yomitan's 382 Arabic test cases (261 valid, 121 invalid) all
  pass through the ar bundle.
- Conditions are n, v, pv, iv, cv only (`partOfSpeechFlagsJson('ar')`). wty ar-en writes `n` (12,865 own rows), `v`
  (7,239) or nothing (7,405; adjectives, adverbs, particles) in the rules field, and its form rows `v` 477,746, `n`
  350,029, nothing 164,359. A transformed candidate (conditions ≠ 0) never fits an untagged entry, in Yomitan and in
  the engine, so والطويلة finds nothing through الطويلة (an untagged form of طويل). The engine already lets an
  `untyped` candidate fit an untagged entry (`candidate_lookup.cpp` `fits`, built for English phrasal verbs).
- Persian and Hebrew have no rules: none of می‌ نمی‌ ب ن, ها ان, the ezafe, pronoun suffixes or ‌تر ‌ترین; none of the
  Hebrew prefixes ו ה ב כ ל מ ש. Yiddish has 6 transforms (plurals, umlaut plurals, present forms).
- The glue registers a transformer only when `descriptor.language === language` (`scripts/yomitan-language/glue.js`
  18). Arabic's transforms say `'ar'`, so the arz bundle gets no deinflection; Georgian's say `'kat'`, so the ka
  bundle gets none either (სახლში gives only itself). Keyed by the descriptor's `iso` (as
  `getAllLanguageTransformDescriptors()` returns it), arz passes all 382 Arabic cases and ka gives სახლ.
  `generate-inflections.mjs` looks transforms up the same way (folder and `language === iso`). Upstream Yomitan has
  the same gap (`multi-language-transformer.js` 33).
- Empty candidates: Arabic prefix rules with no stem left (في, لا, وا: 3-8 each; 5 of 621 word starts of one
  sentence) and Yiddish umlaut rules that return `''` (קינדער: 5 of 8 candidates). The glue passes them on.
- Bundles (`build.mjs`, unchanged): ar 62,098 bytes, arz 62,271, fa 45,271, he 44,882, yi 49,611; ur needs a
  `borrowed` entry (`ur: {from: 'fa', iso639_3: 'urd', name: 'Urdu'}`, 45,268 bytes). `addedPreprocessors` only
  replaces the line `textPreprocessors: capitalizationPreprocessors,`, so it cannot add processors to fa (its own
  object) or he (no field): the mechanism needs extending for these two.
- Word resolution (`_getNextSubstring`) cuts at the last `[^\p{Letter}]`: at every haraka and niqqud (Mn), ZWNJ (Cf),
  maqaf (Pd), geresh, gershayim and ASCII quotes (Po). `ShownResults.fromVariant` already drops matches that end
  before an Mn, Mc or Me mark; a match ending before a ZWNJ shows (می for می‌روم, but also the wanted کتاب for
  کتاب‌ها).
- Nothing in the language code, `text-scanner.js` or `translator.js` handles direction. The default sentence
  terminators lack ؟ (U+061F) and ۔ (U+06D4).
- Timing (Node, median of 300 calls): ar 0.19-0.41 ms per call on unvocalized lines of 16-32 units, 0.25-1.36 ms on a
  fully vocalized line (each mark is a cut point, so 3-5 times the candidates); fa and he 0.01-0.06 ms; en 0.12 ms,
  ko 4.6 ms for reference. No processor is combinatorial: at most 64 variants per substring, the 4096 cap is never
  reached. Loading the ar bundle builds 7,157 RegExp objects: 11.4 ms in Node (uk 5.0, ko 4.1); its QuickJS load,
  heap and per-call time are to be measured on the emulator.

### Dictionaries (wty)

Counting as in groups 4a-4c (words with their own meaning); ✗ under 1,500 (then only the word count is given); – no
file. Cells: archive MB, words.

| from | en | ru | de | es | fr | it | ja | ko | pl | pt | tr | vi | zh |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ar | 13.60 20.7k | 0.60 13.5k | 301 ✗ | 701 ✗ | 1.20 11.4k | 547 ✗ | 1,038 ✗ | 0.08 1.6k | 0.74 9.6k | 1,148 ✗ | 0.24 2.2k | 215 ✗ | 0.38 2.2k |
| fa | 3.34 14.8k | 0.44 11.0k | 165 ✗ | 157 ✗ | 0.13 2.8k | 200 ✗ | 1,275 ✗ | 1,319 ✗ | 0.11 2.0k | 864 ✗ | 1,106 ✗ | 98 ✗ | 0.25 3.1k |
| he | 3.32 12.9k | 0.50 9.3k | 107 ✗ | 0.16 1.6k | 1,484 ✗ | 600 ✗ | 0.09 1.7k | 380 ✗ | 0.11 2.4k | 817 ✗ | 1,444 ✗ | – | 0.23 1.7k |
| ur | 1.49 7.7k | 1,353 ✗ | 0.28 1.6k | – | 226 ✗ | – | 223 ✗ | 646 ✗ | 350 ✗ | 501 ✗ | 440 ✗ | 120 ✗ | 938 ✗ |
| arz | 1,111 ✗ | – | – | 104 ✗ | – | – | 66 ✗ | – | – | – | – | – | 180 ✗ |

- Pairs that pass: ar en ru fr ko pl tr zh; fa en ru fr pl zh; he en ru es ja pl zh; ur en de; arz none. ar-en (13.60
  MB) is above the catalog script's `COUNTED_BELOW` (8 MiB) and passes uncounted; it holds 20,683 words.
- No own-language editions and no glossaries for any of the five. Transcriptions (`dict/all/<lang>/wty-<lang>-ipa.zip`):
  ar 0.42 MB 20.6k, fa 0.57 13.2k, he 0.20 8.1k, ur 0.15 8.5k (all pass), arz 1,043 ✗. Their readings: ar the
  vocalized spelling (أقمار → أَقْمَار), fa a romanization (`išārat`), he the key itself.
- How wty writes them (`src/dict/main/ir/mod.rs`): `normalize_orthography` strips 16 Arabic marks from form headwords
  for ar and fa only, putting the original into the reading; Hebrew, Urdu and Egyptian forms keep their pointing.
  Lemma headwords are page titles, unpointed in all five. Readings: ar the vocalized form (98.0% of lemma rows), fa a
  romanization in mixed schemes (97.3%: `classical čuġundar`, `majâzi`, `ensân-e kâmel`), he, ur and arz none (the
  pointed lemma is only in the definition's grammar line, `כֶּלֶב • (kélev …)`).
- Form-of rows (→en): ar 992,134 rows (301.1k distinct forms, 3.29 rows per form: each vowelled case form is its own
  row with the same unvocalized headword); fa 117,286; he 146,417; ur 45,348; arz 6,871. 98.4-99.4% of lemma links
  are headwords; ar 11,029 (ur 336) only after stripping the marks from the link (`form of كِتَاب [plural]`).
  Template names leak as tags in ar (`ar-infl-a` 160,406, `ar-infl-in`, …); "informal" is the pausal form.
- Prefixed forms: ar lists the definite ال forms (203,412 form rows, الكتاب → كتاب) but no proclitic forms (والكتاب,
  بالكتاب, للكتاب) and almost no pronoun-suffix forms (كتابي only as a dual construct). he lists definite ה forms
  (pointed, 4,887) and possessed forms (51,466 tags), not the prefixes ב ל ו מ ש כ (בבית, לבית, והבית are no
  headwords). fa lists verb forms joined (میفرستادیم, نمیدانم: 4,657 می/نمی forms, none with ZWNJ, already lost in
  kaikki's data) and few noun forms (کتابها yes, کتاب‌ها no).
- Arabic spellings (own words / form headwords): hamza alef أ إ آ ٱ 14.5% / 12.4%, final ى 3.6% / 1.2%, final ة 17.2%
  / 5.3%, harakat none (stripped by wty). Keys with every alef form → ا, final ى → ي and final ة → ه: 44,765 new
  spellings, 9,529 shared by two or more headwords (own words 292, على and علي among them). 721 romanized Latin form
  headwords (al-ḡarafāt), 117 Hebrew-script Judeo-Arabic headwords.
- Persian spellings: Persian ی (41.2% of own words) and ک (17.7%); Arabic ي ك almost never (14, 6). ZWNJ in 811 own
  words, 170 of them also written with a space, 180 also joined; no form headword has one. 5,741 Cyrillic form
  headwords (6,192 rows tagged "Cyrillic Tajik", ҳалол → حلال) and 2,519 romanized conjugations (kosdâdim).
- Hebrew spellings: niqqud in 82.4% of form headwords, almost no own words. Unpointed keys: 83,977 new spellings, 23,543
  shared by two or more headwords (homographs such as כבית: no prefixed form of בית, a form of כיבה). 34.8% of
  pointed forms unpoint to defective spelling (חֲנֻכַּת־ → חנכת where text writes חנוכת); plene keys built from the
  points add only 133 of the 20,000 commonest words (24,827 keys), so they are not worth it. Abbreviations use
  gershayim and geresh only (צה״ל, ג׳ירפה); 7,565 construct forms end with a maqaf (כֶּלֶב־); 22.2% of own words end
  in a final letter. 100 malformed Latin form headwords (`form אָרֶץ`).
- Beyond Wiktionary: FreeDict ara-eng (Arabeyes, GPL, reversed list of low quality); nothing open for Persian or
  Hebrew. Hans Wehr is copyrighted; circulating Yomitan conversions are unlicensed.

### Word frequency

- wordfreq 912caf6: `large_ar` (620,701 words), `large_he` (591,944), `small_fa` (31,389), `small_ur` (23,201); none
  for arz. Its preprocessing applies NFKC and removes every Mn mark and tatweel for Arabic and Hebrew script; it folds
  nothing else (no alef forms, no ى/ي, ي/ی or ك/ک) and keeps ZWNJ.
- fa's list holds Arabic ي in 1,803 words and ك in 326 (اين مي هاي براي, كه يك); he's both ASCII and Hebrew quotes
  (צה"ל 17,140 words with `"`, 211 with ״).
- Coverage of the 20,000 commonest words against the X-en headwords (lemmas and forms):
  - ar 13,847 exactly; 14,601 with the spelling keys above; 17,771 with proclitics; 18,851 with pronoun suffixes too
    (the last two are upper bounds, which Yomitan's transforms cover).
  - fa 9,517 exactly; 11,014 with ي ك ى → ی ک ی and ZWNJ removed; 14,603 with our ending rules; 15,081 with the
    verb prefixes too. Missing then: colloquial forms (خوبه, میاد, چیه).
  - he 6,067 exactly; 10,863 against unpointed headwords (and ASCII quotes as Hebrew ones); 17,570 with up to three
    prefix letters split off (an upper bound with false matches).
  - ur 5,516 exactly; 6,575 with folds.
- Other lists: CAMeL Arabic frequency lists (CC BY-SA 4.0, with MSA and dialect lists; the only open one for arz),
  hermitdave/FrequencyWords (OpenSubtitles, CC BY-SA 4.0 content). Leipzig's terms contradict each other.

### OCR

- ML Kit: no Arabic or Hebrew model.
- Paddle `arabic_PP-OCRv5_mobile_rec` (Apache-2.0): ONNX 8.0 MB, MNN 7.98 MB, 747 entries (Arabic block with harakat
  and tatweel, ، ؛ ؟ ۔, Persian پ چ ژ گ ک ی, Urdu ٹ ڈ ڑ ں ھ ہ ے, both digit sets, Latin, symbols); no ZWNJ, no
  Hebrew. Converted for the comparison (`testdata/ocr/compare/models/`); it joins the Paddle models that wait for
  the owner's upload (question 4 of `plans/2026-10-19-group-4c.md`).
- Tesseract (pins of `tessdata_catalog.py`), MB: ara fast 1.43 / best 12.60; fas 0.43 / 3.33; heb 0.96 / 3.70;
  urd 1.40 / 7.99.
- Comparison (emulator, 2026-10-16/17), character errors in % on clean / hard screens:
  - ar: Paddle 2.4 / 3.5, Tesseract fast 5.8 / 58.8, best 5.9 / 56.1, cloud 0.4 / 0.5;
  - fa: Paddle 2.2 / 5.2, Tesseract fast 26.4 / 49.9, best 26.5 / 49.4, cloud 1.4 / 0.7;
  - he: Tesseract fast 0.0 / 48.5, best 0.0 / 43.7, cloud 0.0 / 5.1 (taps right 87%).
  By the rows "OCR engine choice" and "Tesseract models": Paddle for ar and fa, Tesseract fast for he (best is not
  clearly better). Row "On-device OCR engines" keeps Tesseract out of Persian's list (measured unusable), so until
  the Paddle upload Persian has no on-device model, and Arabic offers Tesseract.
- Test pages of this research (`scripts/text-image.py` cannot shape Arabic: Pillow without raqm; drawn with headless
  Chrome at 1080 px in Noto Naskh Arabic, Noto Sans Arabic, Vazirmatn, Noto Sans and Serif Hebrew, with harakat,
  tatweel, ZWNJ, both digit sets, quotes and brackets, mixed Latin). Cloud recognition (PC client) and Paddle (PC,
  `paddle_ref.py` with ONNX Runtime, the app's `readingOrder` applied):

| What | Cloud recognition | Paddle arabic |
|---|---|---|
| Word order, pure RTL line | reading order, rightmost word first | reading order after `readingOrder` |
| Punctuation ، ؟ ؛ . : « » ( ) | separate words, empty separator before them | kept in words; brackets and guillemets come out mirrored: `»اقرأ الكتاب«`, `)الصفحة 12(` |
| 10:30, ٢٠٢٤, ۱۴۰۳ | one word, correct order | ASCII digits correct; Arabic-Indic and Persian digits reversed: ٢٠٢٤ → ٤٢٠٢, ۱۴۰۳ → ۳۰۴۱ |
| Number before the full stop | correct | "الرصيف 5." → "الرصيف .5" |
| Harakat | kept (once an extra sukun) | a vowelled line read badly (scores 0.56-0.64, letters lost) |
| Tatweel | removed | kept as ـ or dropped |
| Persian ی ک | Persian on a Persian page, Arabic on an Arabic page | Persian with Vazirmatn; Arabic ي ك ى with Noto Naskh (كتاب, مى, يك) |
| ZWNJ (می‌خواهم, کتاب‌ها) | always dropped: joined (میخواهم), once a space (خانه ای) | not in its list: joined, or a space with Naskh |
| Latin inside an Arabic line | "iPhone 15 Pro" in reading order (Sans dropped "15") | lost with Naskh, garbled with Sans |
| Latin inside a Hebrew or Persian line | runs in the wrong order (קניתי, 15, iPhone) | – |
| Hebrew niqqud | kept, a dagesh once dropped | – |
| Hebrew ״ ׳ ־ | gershayim → ASCII `"`, geresh → ASCII `'`, maqaf kept | – |
| Arabic word in an English line | order right; كتاب came back with Persian ک | – |

- The locale sent (ar or fa) does not change the output; the response's `content_language` is `fa` for Persian and
  `iw` (the old code) for Hebrew; `LensProtocol` does not read it. Word boxes are right, so the order of a mixed line
  can be rebuilt from geometry.

### Right to left in the app today

- Recognition marks lines: `OcrLine.rightToLeft` from the first strong character (cloud, Tesseract); Paddle's
  `readingOrder` reverses its letters except runs of ASCII letters, digits and `: * . / % + -` (so Arabic-Indic and
  Persian digits are reversed and mirrored characters are not mirrored, see above).
- `TextLayout.splitBox` splits a word box from its right edge in RTL lines; hit testing takes the nearest character;
  the highlight is the union of the matched characters' boxes per line. Arabic letters differ in width (ا, س), so
  evenly split boxes are approximate; with the word-start lookup a tap inside a word finds it anyway. Harakat count as
  characters in the split (group 4c's "marks get no width" fixes that too).
- Tesseract's iterator gives words and symbols in reading order; symbol boxes are kept when there is one per character.
- App text (`AccessibilityText.appTextLines`) ends a line where a character starts left of the previous one by half
  its width: in an RTL text view every next letter does, so each Arabic or Hebrew letter becomes a line of its own,
  and the lines are not marked RTL.
- `LanguageSupport.rightToLeft` exists; nothing reads it.
- Lookup page: `lang` is set per source and result, `dir` nowhere; a block with Arabic text is laid out as LTR
  (trailing punctuation on the wrong side, mixed runs misordered, aligned left).
- Anki: the app fills fields of the user's note types and creates none; the direction of an Arabic field is the card
  template's (Yomitan adds nothing either). AnkiDroid's duplicate check compares the first field's checksum, markup
  included (`AnkiDroid.kt` 64-70).
- Word characters: letters, digits, marks, ZWNJ and ZWJ (since Hindi), so harakat, niqqud, tatweel (Lm) and Persian
  ZWNJ stay inside words. Not yet: geresh ׳ and gershayim ״ (Po), ASCII `'` and `"` between Hebrew letters (cloud
  recognition gives צה"ל, ג'ירפה), maqaf ־ (Pd).
- Sentence ends: `. ! ? … \n`; ؟ (U+061F) and the Urdu ۔ (U+06D4) are missing; ، and ؛ are a comma and a semicolon.

### Fonts

- AOSP `fonts.xml`: Noto Naskh Arabic and its UI variant for `und-Arab`; Noto Sans Hebrew and Noto Serif Hebrew for
  `und-Hebr`. No Noto Sans Arabic, no Nastaliq (Urdu would show in Naskh). PostScript names `NotoNaskhArabic`,
  `NotoNaskhArabicUI`, `NotoSansHebrew`, `NotoSerifHebrew`; `local()` to be checked on the emulator.
- The font catalog has nothing for either script. Downloadable (Google Fonts, OFL): Noto Sans Arabic (0.84 MB), Amiri
  (0.43 MB, book style), Vazirmatn (0.24 MB, the common Persian face), Heebo (0.12 MB), Frank Ruhl Libre (0.18 MB,
  serif); Noto Nastaliq Urdu for later.
- Shaping (joining forms, lam-alef, marks) is the WebView's and the system's HarfBuzz; nothing needed from us.

### Text rules

- Arabic: the spelling keys above (hamza alefs, final ى/ي, final ة/ه) at import, in place of Yomitan's two hamza
  processors; adjectives reached through untyped noun-rule candidates; lemma links compared without harakat;
  `ar-infl-*` tags dropped; romanized form rows dropped.
- Persian: ZWNJ removed in text and keys; ي ك ى → ی ک ی and ۀ → ه in text, keys and frequency spellings; our own ending
  and verb-prefix rules (about 30) in the fa bundle; Cyrillic Tajik and romanized form rows dropped.
- Hebrew: niqqud and cantillation removed in text (a variant) and in keys (U+0591-05C7 except the punctuation maqaf,
  paseq, sof pasuq, nun hafukha); a trailing maqaf dropped from keys; ASCII `'` `"` between Hebrew letters tried as
  ׳ ״; our own prefix rules (up to three of ו ה ב ל מ ש כ). Final letters need nothing: prefixes do not touch a word's
  end and there are no suffix rules.
- Sentence ends: ؟ for Arabic and Persian (۔ for Urdu later); Hebrew . ! ? as now.
- Text to speech: `speechText` is `reading.ifEmpty { term }`; Persian's reading is Latin, so Persian speaks the term.

### Scan length

- Over 16 code points (the default scan length): 2.4% of vowelled Arabic forms and 1.2% of pointed Hebrew forms;
  with up to four code points of prefixes 18.1% and 15.6%; Persian headwords 12.1% (multiword verb forms such as
  داشتیم میفرستادیم). Over 24: 0.2% of vowelled Arabic forms with prefixes, no Hebrew ones, 2.5% of Persian headwords.

### Audio

- Lingua Libre: ar Q13955 `ara` 13,788 files from 67 speakers; fa Q9168 `fas` 4,260 / 11; he Q9288 `heb` 3,615 / 31;
  ur Q1617 `urd` 490 / 8. One code per item, so the app's `\([a-z]{3}\)` matches all. 49 Hebrew words are filed
  under the Arabic item. Arabic varieties have items of their own (South Levantine `ajp` 3,323, Moroccan `ary` 1,770,
  Egyptian `arz` 948, …).
- Wiktionary-style titles on Commons: `Ar-` 1,592 audio files (1,533 in Arabic script), `Fa-` 933 (856), `He-` 1,477
  (1,377; 280 more titled `He-Q<item>-…-Ijon.ogg`, which the app's pattern does not match), `Ur-` 138 (126).
- Twenty common words, the app's own queries, Lingua Libre / Wiktionary / either: ar 12 / 15 / 20 (14 / 15 / 20 when
  the vowelled spelling is searched too); fa 17 / 15 / 19; he 19 / 1 / 20 (20 / 5 / 20 with the pointed spelling);
  ur 5 / 1 / 5. By the row "Audio order": Wiktionary first for Arabic, Lingua Libre first for Persian and Hebrew.
- Vowel marks in titles: `He-` 78% pointed (`He-כֶּלֶב.ogg`), Lingua Libre `ara` 17%, `fas` 14% (one speaker), `Ar-`
  4%, `Fa-` none. Commons search does not ignore marks (`قرأ` finds nothing, `قَرَأَ` two files) and stores titles in
  NFC, which reorders marks (shadda after the vowel), so a vowelled spelling compared locally must be NFC first. The
  marks in titles are the uploader's (`Ar-كِتَابٌ.oga` with tanwin), so a query with the dictionary's vowelled reading
  finds only some. An optional marks class after every letter of the query (tested: Lingua Libre 0.5-0.7 s, Wiktionary
  0.5-1.5 s when narrowed with `intitle:"<word>"`, 16 s without) finds them all, but also homographs (كلب finds the
  verb كَلَبَ, אב the month Av): Arabic 15 / 16 / 20, Hebrew 20 / 5 / 20, Persian 18 / 15 / 19.
- Arabic dialect items have 17 of the twenty words, pronounced as the dialect; not used for Arabic (question 85).
- 204 of the 856 `Fa-` titles use Arabic ي or ك (`Fa-كتاب.ogg` is the clip the Persian entry کتاب cites); the app's
  query with Persian letters misses them. Lingua Libre `fas` uses Persian letters throughout.
- Text to speech: Google's engine has Arabic and Urdu; Hebrew is in Wikipedia's list and Google Cloud's (`he-IL`)
  but not in TalkBack's language list, so it is to be checked on the emulator; no Persian anywhere (Persian audio rests
  on the recordings). `TextToSpeech.setLanguage` passes the ISO 639-2 code, so `he` and `iw` are the same to it.

### Names, flags, codes

- Names (JDK 21, CLDR 42): Arabic / арабский / アラビア語 / 阿拉伯语 / Arabisch / Tiếng Ả Rập; Persian / персидский /
  ペルシア語 / 波斯语 / Persisch / Tiếng Ba Tư (Turkish Farsça); Hebrew / иврит / ヘブライ語 / 希伯来语 / Hebräisch /
  Tiếng Do Thái. Own names العربية, فارسی, עברית. Android's ICU may differ slightly (not checked on the device).
- Codes: Android's `Locale("he").language` is "iw"; the app compares `languageCode` (from the language tag, "he"),
  and `TranslationLanguages.forLocale` maps iw → he. Translation services: ar, fa, he (Google `iw`) in
  `TranslationLanguageTable`; no Arabic varieties in either service.
- circle-flags 379588b: `sa.svg`, `ir.svg`, `il.svg` convert with `circle_flags.py` (3,026, 1,200, 1,050 bytes);
  `flags/language/ar.svg` is a design of its own (lettering on green), fa, he and ur are links to the country flags.
- E-ink codes AR, FA, HE.

### Urdu, Egyptian Arabic and Yiddish (for group 5f)

- Urdu: ur-en 7.7k words and ur-de 1.6k; forms are multiword table cells with a pronoun (ہَم کَریں), so bare forms
  (کریں, والے) are no headwords; 73% of form headwords pointed; Nastaliq is the usual print style and was not measured
  (Paddle's Arabic model knows the Urdu letters); no system Nastaliq font; Lingua Libre 490 files (120 more Urdu-script
  files under the English item, 14 of the twenty words with them), Google voices `ur-PK`/`ur-IN`. The bundle needs `borrowed.ur` and letter variants ک/ك, ی/ي, ہ/ه, ے.
- Egyptian Arabic: arz-en 1,111 words, under the threshold; its bundle needs the glue keyed by iso to deinflect.
- Yiddish: yi-en 1.39 MB; Yomitan's points removal and final letters; candidates never carry the YIVO points.

### Plain fixes found (no owner decision)

- Paddle `readingOrder`: every decimal digit (Nd) kept in its run, bidi-mirrored characters (`( ) [ ] « » < > { }`)
  mirrored, kept signs at a run's edge split off it (except %), so "5." stays "5.".
- App text: in an RTL text view a line ends where a character starts to the right of the previous one, and its lines
  are marked right to left.
- Cloud recognition: the order of a mixed RTL line rebuilt from word boxes (LTR runs kept left to right).
- Letters by language in OCR words (`ocrWord`, as the Cyrillic look-alikes): Persian ي ك ى → ی ک ی; Arabic ک ی → ك ي.
- Word characters: geresh, gershayim and ASCII `'` `"` between Hebrew letters; the maqaf as a connector like the
  hyphen. Sentence end ؟.
- Glue: transforms registered by the descriptor's iso (fixes arz and Georgian); empty deinflected texts dropped;
  `generate-inflections.mjs` by iso. `build.mjs`: `addedPreprocessors` for descriptors without the capitalization
  line.
- Form-of: lemma links compared without marks (ar, ur); template-name tags dropped; form rows in other scripts dropped
  (fa Tajik Cyrillic and romanized, ar romanized, he malformed Latin).
- Frequency spellings: fa ي ك ى → ی ک ی, ZWNJ removed; he ASCII quotes → ׳ ״.
- Audio: the term and reading NFC-normalized before titles are compared; Persian Wiktionary titles searched with
  Arabic ي ك as well.
- Audio, every language: a Commons error answer (HTTP 200 with `error`, such as `cirrussearch-regex-too-busy-error`,
  seen on 7 of about 400 regex searches) counts as "no recording" and the miss is cached for 60 s
  (`AudioFinder.commonsFiles`); it should count as a failure. The search returns a redirect's target title, which
  `wiktionaryFiles` drops when its word differs (`Ar-كتاب.ogg` → `Ar-كِتَابٌ.oga`, the only Wiktionary clip of كتاب).
- Persian speaks the term, not its Latin reading.
- `LensProtocol`: `content_language` "iw" is Hebrew, should it ever be read.
- wordfreq's code license is Apache-2.0 (the group 4b sources line said MIT).

### Outcome on the Arabic branch (2026-10-20)

- Paddle `readingOrder`: the `%` exception was dropped. In an Arabic line's crop, `%` stands apart from its number
  like the other weak signs at a run's edge (W1–W7 and N1 of the bidirectional algorithm), so "50%" is drawn "%50" and
  read back as "50%" only when it is split off like ".". Details in `ocr-engines.md`, "Right-to-left lines".
- Audio redirects: `list=search` returns a redirect's target title, not the redirect itself (checked live on
  2026-10-20 with `Ar-كتاب.ogg`, which redirects to `Ar-كِتَابٌ.oga`). The marks-tolerant title pattern
  (`LanguageSupport.recordingMarks`) keeps such a target, so asking for redirects (`redirects=1`) is not needed. The
  other redirects seen point to other words or to dialect items and are dropped as before.
- The Commons error answer is a failure now (`CommonsErrorException`, `AudioError.Kind.HTTP_STATUS` with the error
  code), not a cached miss.
- `LensProtocol`'s `iw` is left to the Hebrew branch (outcome: nothing reads it, see below).

### Outcome on the Persian branch (2026-10-20)

- Coverage of the rules, measured on the bundle against wty-fa-en's headwords and form rows: of wordfreq's 20,000
  commonest Persian words 11,017 are entries as they are and 15,033 with the rules. The word itself comes first in
  all 11,017 (a longer match, then fewer preprocessor steps, then a shorter chain); 3,902 of them also show entries
  the rules give after it (است → اس, باید → اید). The colloquial endings would add 93-456 words each (overnight
  question 4 in the plan).
- wordfreq `small_fa` builds 29,190 words on the phone (a word with the non-joiner is ranked under both spellings).
  wty-fa-en imports in 5.1 s (engine 0.9 s) with a form-of table of 105,987 forms.
- Cloud recognition drops the non-joiner (کتاب‌ها comes back as کتابها) and, in a Vazirmatn image, some full stops
  after a word in mid-line (آسمان. and ص.), so `{sentence}` then runs on from the line's start. It keeps Persian
  digits and Latin runs in order.
- Google's speech engine on the emulator has no Persian voice (`setLanguage` below `LANG_AVAILABLE`): the voice
  item plays nothing, as in any language without a voice.
- Lingua Libre has Persian recordings (Q9168) for common words; for کتاب three speakers, two titled as written,
  and Wiktionary's `Fa-كتاب.ogg` is found through the Arabic letters.

### Outcome on the Hebrew branch (2026-10-20)

- Coverage of the prefix rules, measured on the bundle against wty-he-en's headwords and form rows: of wordfreq's
  20,000 commonest Hebrew words 10,911 are entries as they are and 17,552 with the rules; the word itself comes first
  in all 10,911; 3,429 also show entries the prefixes give after it (היה → יה, כבר → בר); 6,641 are found only
  through the rules (שלא → לא). The rules follow the grammar's order (ו, then ש or כש, then ב כ ל or מ, then ה; no ה
  after ב כ ל), so three letters at most come off: ושמהבית comes down to הבית, which the dictionary lists as a form
  of בית.
- Wiktionary lists a Biblical or Tiberian transcription first for 464 of the 8,163 words of wty-he-ipa that have a
  Modern Israeli one; `HebrewSupport.firstTranscription` puts the Modern Israeli ones first (plan question 5). 933 of
  its 16,666 transcriptions are written `//a/ [b]/` or `//a//` by the converter (one template giving a phonemic and a
  phonetic form; also 32 Arabic, 40 Egyptian, 9 Persian, 4 Urdu); `Transcriptions.split` takes them apart.
- Cloud recognition reads Hebrew when asked with `he`; the response's `content_language` `iw` is not read. It gives
  the gershayim and the geresh as ASCII quotes and the maqaf as a word break, and once a line with a number after
  ב־, ₪ and Latin words in neither order (`notes/ocr-engines.md`). Tesseract `heb` fast reads the screen offline.
- Lingua Libre (Q9288) has recordings for most common words; Wiktionary's He- files are mostly pointed
  (He-כֶּלֶב.ogg) and are found after a title as written. Google's speech engine on the emulator has a Hebrew voice
  (he-il) and takes the tag `he`.
- `{reading}` in Hebrew is the word itself (the dictionaries give no reading), so it gets the right-to-left span in
  the suggested field templates, as Arabic's vowelled reading does.

### Questions for the owner

See `plans/2026-10-19-group-4d.md` (questions 69-86, all answered on 2026-10-19; the overnight questions 1-6).

## Group 4e research (2026-10-19)

Kazakh (kk) and Kyrgyz (ky): Turkic languages written in Cyrillic, with long chains of endings. Sources: Yomitan
e7334a9 (`ext/js/language/`, `translator.js`; GPL-3.0), the wty tree on Hugging Face (`latest/dict`, revision
2026.10.11, listed and downloaded 2026-10-19, CC BY-SA 4.0; sizes in MB of 10^6 bytes), wordfreq 912caf6, hermitdave
FrequencyWords, the Leipzig Corpora Collection (downloads, and Wayback copies of its pages, as the live site sits
behind a bot check), Apertium, PanLex (the `cointegrated/panlex-meanings` mirror), Tatoeba, 30 Wikipedia articles per
language and Leipzig's news samples for running text, the tessdata pins of `tessdata_catalog.py` (Apache-2.0), Paddle
`cyrillic_PP-OCRv5_mobile_rec` (Apache-2.0) run with ONNX Runtime on the PC, the OCR comparison of `ocr-engines.md`
(`testdata/ocr/compare/analyze-rot.txt`), the catalog's font files, circle-flags 379588b (MIT), Wikimedia Commons
and Lingua Libre (API) for audio, the engines' own language lists for voices and translators, the alphabet decrees,
CLDR, the Windows and AOSP keyboard tables, and grammar descriptions of both languages for the ending rules (no GPL
code). Nothing in this section was measured on a device.

### Yomitan at e7334a9

- No descriptor for kk or ky. The bundles borrow Mongolian's descriptor (Cyrillic, capitalization only; Bulgarian's
  is the same) under their own codes, with the app's own text processors and ending rules (`own-transforms.js`, as
  Bengali's).
- Word resolution never shortens a single word (`_getNextSubstring` drops whole trailing words), so without rules a
  Kazakh or Kyrgyz word is found only as written or through a wty form-of row, as Turkish is today. The line above
  that these languages get "the longest matching start of the word" was wrong.

### Dictionaries (wty)

Counting as in group 4a (words with their own meaning); ✗ under 1,500. Cells: archive MB, words; form rows in
brackets where they matter.

| from | en | ru | fr | pl | zh | ko | tr | ja | pt | it | vi | de | own |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| kk | 1.74 12.0k (74.6k) | 0.80 18.0k (14.4k) | 0.22 4.8k | 0.18 3.0k | 0.19 2.1k | 0.08 1.9k | 663 ✗ | 453 ✗ | 533 ✗ | 236 ✗ | 209 ✗ | 86 ✗ | – |
| ky | 0.62 2.7k (53.4k) | 0.30 3.7k (10.4k) | 1,320 ✗ | 442 ✗ | 759 ✗ | 579 ✗ | 632 ✗ | 127 ✗ | 344 ✗ | – | 481 ✗ | 81 ✗ | – |

- Pairs that pass: kk en ru fr pl zh ko; ky en ru (ky-fr 1,320 just under). Outside the gloss languages: kk-el
  6,422 words, kk-ku 2,346, ky-ku 1,959. Neither language has a Wiktionary edition in wty.
- Transcriptions (`wty-<lang>-ipa.zip`): kk 0.18 MB, 10,825 words; ky 0.04 MB, 1,552 words; both pass.
- kk-ru has more words of its own (18.0k) than kk-en (12.0k), with 3,689 verbs against 744; kk-en has the form
  tables. Together 24.3k distinct headwords.
- The part of speech is in the definition tags (`n` 7,640, `v` 712, `adj` 1,845 in kk-en; `non-lemma` on form
  rows); the rules field is empty in every row.
- Form-of rows:
  - kk-en: about 4,700 nouns declined (13 case and number forms each, no possessives); 168 verbs with aorist,
    preterite and optative forms; 922 `alternative` spellings in Cyrillic (Болғария → Болгария); links from a Cyrillic
    headword to another script: 105 to its Arabic-script spelling (Қазақстан → قازاقستان, tags `Arabic`, `alt-of`),
    91 to its romanization (мен → men, `romanization`), 4 to the 1929 Latin alphabet (жер → çer, `Yañalif`); 71
    rows give a letter's Arabic equivalent (о → و); 42 + 42 single letters link their other case.
  - ky-en: 1,253 nouns with case forms and the first- and second-person possessives; 265 verbs with a few
    participles (келген → келүү); 2,202 Arabic-script headwords pointing to their Cyrillic spelling (only Arabic text
    reaches them) and 24 Cyrillic ones pointing to an Arabic spelling (ата → اتا); 18 letter equivalents, 47 case
    links of single letters, 1 Yañalif row.
  - A lookup of Қазақстан or мен shows a form-of entry ("Arabic … of قازاقستان", "romanization of men") whose
    lemma is in no dictionary, next to the meaning.
  - kk-ru: 1,210 nouns declined; ky-ru: 709 nouns with person forms, 675 declined.
- Suffix headwords: kk 312 (-ға, -ге, -дың …), ky 178.
- Readings: none.

### Other dictionaries

- No Yomitan dictionary for either language besides wty; FreeDict and WikDict have none. sozdik.kz and
  el-sozduk.kg carry no open license; Yudakhin's Kyrgyz–Russian dictionary (1940, 1965) is under copyright.
- Apertium (GPL-3.0): bilingual lemma pairs with a part of speech and no definitions: eng-kaz 32,994 entries,
  kaz-rus 29,572, kaz-kir 8,174, eng-kir 474. GPL, so usable only in the GPL modules; not taken.
- PanLex: the live site says CC BY-NC-SA 4.0 (commercial use only with written permission); older copies of the
  license page say the snapshots were CC0 (see "PanLex license history" below). The Hugging Face mirror `cointegrated/panlex-meanings` (CC0, the 2024-03-01 dump)
  has `kaz.tsv` (37.5k expressions; 31k in the standard Cyrillic variety) and `kir.tsv` (21.3k; 19.7k). Joined with
  `rus.tsv` on the meaning: 19.3k Kazakh and 8.6k Kyrgyz words get a Russian translation. Quality varies: су →
  вода, мышык → кошка, but ушул → "присутствовать", шаар → лондон among город. It has words wty lacks (Kyrgyz
  боюнча, чейин, же, кабыл, аркылуу, азыр, but not экен, учурда). Of the 2,000 most frequent word forms of the
  Leipzig news sample: kk wty 1,287, PanLex 1,052, either 1,405; ky wty 789, PanLex 818, either 1,143.
- Tatoeba: 4,582 Kazakh and 826 Kyrgyz sentences (CC BY 2.0 FR).

### PanLex conversion (2026-10-20)

- `scripts/panlex/panlex_yomitan.py` reads two mirror files at revision `0422f0e60f39` (columns `id`, `langvar`,
  `txt`, `txt_degr`, `meaning`, `langvar_uid`; the `-000` varieties are the standard ones; `eng.tsv` has tens of
  millions of rows, so only the meanings of the kept headwords are read from the target file).
- Filters, chosen on samples: headwords only in the language's lowercase letters (Kazakh words filed as Kyrgyz,
  codes, names, time zones and interface strings go); meanings with more than 4 headwords (loose groups: one Russian
  word for sixteen Kyrgyz ones) or more than 20 translations (the cities of Russia under Оруссия); translations in
  another script. Capitalized Russian translations stay (currencies, Луна, Библия). Russian stress marks are kept in
  the shown spelling and ignored when translations are compared.
- One definition per headword, the translations comma-separated in the order of the number of meanings that give
  them, at most 10; no tags, so the verb chains reach these entries (алды → алуу "держать, брать").
- Headwords: kk-ru 16,374 (256 KB), kk-en 13,835 (230 KB), ky-ru 5,729 (107 KB), ky-en 6,357 (108 KB). Of the 2,000
  most frequent Kyrgyz word forms of the Leipzig news sample, 371 (ru) and 406 (en) are headwords as written.
- Quality: Kazakh is cleaner than Kyrgyz. Kyrgyz keeps strays (элүүнчү → fortieth, кыргыз → Uzbekistan, ушул →
  присутствовать) and many software-localization terms; алды as written gives "лицевой". боюнча, чейин, кабыл and
  аркылуу are PanLex expressions, but none of their meanings has a Russian or English expression, so neither
  archive has them (the research line above counted expressions, not translations).
- On the emulator: import 435-545 ms per archive; азыр → now / сейчас, же → или, либо (no wty entry for either);
  PanLex rows sit under the wty entry of the same headword (қазір, қабылдау).

### PanLex license history (rechecked 2026-10-22)

- panlex.org/license in the Wayback Machine: the copies of 2025-02-19 and 2025-05-25 release the CSV and JSON
  snapshots under CC0 1.0 ("even for commercial purposes"), asking to cite Kamholz et al. 2014. The copy of
  2026-06-12 is the "PanLex Database License": the contents and organization of the database under CC BY-NC-SA 4.0,
  a fixed attribution text to keep with any shared material, commercial use only with written permission
  (info@panlex.org). It says nothing about the earlier snapshots. The copy of 2026-03-28 shows the home page; the
  Wayback listing was offline, so the change lies between 2025-05-25 and 2026-06-12.
- The mirror `cointegrated/panlex-meanings` (card: cc0-1.0, the `20240301` dump, "as explained in"
  panlex.org/license) holds a dump released while the page said CC0. CC0 waives the rights for good, so that copy
  stays CC0; dumps made after the change would be CC BY-NC-SA.
- So: build only from the pinned 2024-03-01 dump (revision `0422f0e60f39`), never from a newer PanLex download; keep
  the citation in NOTICE when the archives are hosted, and name the dump date there.

### Lookup coverage

Measured on running text (30 Wikipedia articles per language on general topics: kk 54k, ky 24k lowercase tokens;
Leipzig newscrawl 2016 10K-sentence samples: kk 121k, ky 115k). Lowercase tokens only, so names are left out.
Steps, cumulative: a headword with its own meaning; a form-of row; own ending rules; the longest start of at least 3
letters that is a headword.

| Text, dictionaries | Headword | + form rows | + own rules | + longest start |
|---|---|---|---|---|
| kk Wikipedia, en | 37.4% | 51.9% | 82.8% | 89.9% |
| kk Wikipedia, ru | 40.3% | 45.9% | 83.3% | 87.7% |
| kk Wikipedia, en + ru | 45.4% | 59.0% | 91.4% | 94.6% |
| kk news, en + ru, Latin i read as і | 42.6% | 58.7% | 91.9% | 95.0% |
| ky Wikipedia, en | 23.3% | 34.9% | 56.1% | 69.7% |
| ky Wikipedia, ru | 19.4% | 26.9% | 41.6% | 54.7% |
| ky Wikipedia, en + ru | 26.4% | 40.8% | 62.5% | 75.9% |
| ky news, en + ru | 25.0% | 38.4% | 63.2% | 76.0% |

- The rules, written from grammar descriptions: a noun is stem [plural] [possessive] [case] [predicative]; a verb is
  stem [voice] [negation], then one of past + person, present + person, participle (+ possessive, case,
  predicative), converb, conditional + person, imperative. Kazakh endings in their two vowel-harmony variants and
  consonant variants (-лар -лер -дар -дер -тар -тер; -ның -нің -дың -дің -тың -тің), Kyrgyz in four (-лар -лер
  -лор -лөр …; -дын -дин -дун -дүн …). The stem is tried as written and with the consonant change at a vowel ending
  undone (аумағы → аумақ, мектебі → мектеп; китеби → китеп). A verb stem becomes the dictionary form: Kazakh stem + у,
  ы/і → у, й → ю (бар- → бару, оқы- → оқу, қой- → қою); Kyrgyz stem + уу/үү, а → оо, е → өө (бар- → баруу, жаша- →
  жашоо, иште- → иштөө), accepted only when that headword is a verb. The longest stem wins; equal ones all show
  (қараңыз → қара "black" and қарау "to look"). The script is `coverage2.py` of the research scratchpad (not kept).
- Precision, 40 samples weighted by frequency, judged by hand: kk 36/40 (wrong: тұратын → тұраты, түскен → түс
  "noon", іне, алдыңда), ky 37/40 (wrong: кайрадан → кайыруу, атм, ашуун → ашуу only related). An earlier version
  that stripped any ending in any order was right on 32/40 (kk) and 17/40 (ky): without the slot order, Kyrgyz's
  small dictionaries pull words down to wrong short stems (журналдар → жуу).
- The longest start was right on about 15 of 40 samples (жетілдіру → жеті "seven", өнеркәсібінің → өнер "art",
  жайгашкан → жай); after the rules it adds kk 3.3, ky 13.4 points, mostly wrong.
- Remaining misses: Kazakh particles written as words of their own (ма/ме/ба/бе/па/пе, да/де/та/те, мен/бен/пен),
  endings after numbers and abbreviations (1990-ға), units (км, млн). Kyrgyz: words neither dictionary has: боюнча
  (797 times in the news sample), чейин 297, же 242, кабыл 185, айтымында, ушул, экен, ылайык, аркылуу, азыр.
- Long words: 0.35% of the Kazakh and 0.37% of the Kyrgyz word forms are longer than 16 letters; the group 4b row
  "Scan length" covers both.
- Group 5c measured the same for Azerbaijani and Uzbek (shared notes of the group sessions): a hand-written Turkic
  ending list added 8 and 15 points of news words there.

### Scripts

- Kazakh Latin: the only alphabet signed is that of Decree 637 (19 Feb 2018): acute letters Á Ǵ Ń Ó Ú Ý (written in
  the decree as base letter + U+0301), I i for і and I ı for и/й, so a capital I stands for both; Sh and Ch. Its 2017
  apostrophe version was replaced. The 2021 drafts (ä ğ ñ ö ş ū ü, I ı = і, İ i = и/й) were never signed; the
  Turkic Academy's recommendation of 15 June 2026 (Ç Ğ Ñ Ş Q, I ı = ы, İ i = і/и, Y = й) is advisory. In September
  2026 officials said the alphabet and spelling rules are still being revised, with no date. Latin is met on
  Kazinform's `qz.inform.kz` (2018 letters), behind Egemen's LAT button and in informal text; about 1.5% of 180k
  Kazakh app reviews are mostly Latin (KazSAnDRA). Kazakh Wikipedia offers no working Latin variant.
- Arabic-script Kazakh (töte jazu) is official in China's Ili prefecture (about 1.56 million Kazakhs in China) and
  rarely met elsewhere; CLDR has `kk_Arab`.
- Kyrgyz: Cyrillic with Ң Ө Ү; no Latin law (the president called a switch premature in 2023). A July 2026 spelling
  draft, not adopted, would write е for э in native words (эне → ене) and йа йу йо for я ю ё.
- Casing: Cyrillic І/і is an ordinary case pair; Unicode's language-specific I rules cover tr, az and lt only. No
  `kk_Latn` locale in CLDR and no sentence-break exceptions for kk or ky.

### Letters lost in typing and OCR

- Keyboards: the Windows Kazakh layout puts ә і ң ғ ү ұ қ ө һ on the number row (no digits there); the Windows Kyrgyz
  layout gives ү ң ө with AltGr; AOSP's keyboard gives the letters on a long press of a Russian key (Kazakh а → ә,
  г → ғ, к → қ, н → ң, о → ө, у → ү ұ, ы → і, э → һ; Kyrgyz у → ү, н → ң, о → ө). Without the long press і becomes ы,
  not и. Gboard 18.4 (checked on the emulator, 2026-10-20): its Kazakh (Cyrillic) layout is the Russian ЙЦУКЕН
  one with the Kazakh letters on long presses: а → ә, г → ғ, к → қ, н → ң, о → ө, у → ұ ү, х → һ, ы → і (и has
  none), so a word typed without long presses comes out in the Russian twins that the letter keys match.
- Latin look-alikes in Cyrillic words: the Kazakh news sample has Latin i (U+0069) for і in 10% of its Cyrillic
  words (мүмкiн, үшiн; Leipzig's sample counts 12,616 such tokens against 20,134 written right), the Kyrgyz one 0.2%,
  Kazakh Wikipedia 0.65%; Latin ə for ә is rarer; Kyrgyz Wikipedia has Latin ɵ for ө and a few words typed wholly in
  Latin look-alikes (Maнac). Roman numerals are sometimes typed with Cyrillic Х and І (ХІІІ ғасыр). By letter, on
  the Leipzig 100K word lists (tokens of Cyrillic words, 2026-10-20): Kazakh Latin i is 38.7% of all i and і, I
  43.8%, h 59.9% of h and һ: typing habits; Latin ə Ə 0.06% of ә Ә; Kyrgyz Latin ɵ 0.04% of ө (Ɵ none), Latin i
  0.26% of и; Latin o 0.7-0.8% of о in both.
- Plain Russian letters for Kazakh ones are a known kind of user text (KazSAnDRA, the KazNLP normalizer, Kazakh press
  on ң "disappearing"); no published rate.
- Matching the Kazakh letters to their Russian twins (қ к, ғ г, ң н, ұ ү у, ө о, ә а, і и, һ х): 0.9% of the kk
  headwords and forms (895 of 103k: он/оң/өң, күн/күң/құн, сал/сәл) share a key with another; Kyrgyz (ң ө ү) 0.7%
  (407 of 60.6k: колуң/көлүң, тор/төр). Adding ы to the і class raises Kazakh to 1.5% (мына/мина, таныды/таниды,
  жын/жін).

### OCR

- Paddle `cyrillic_PP-OCRv5_mobile_rec` (850 characters) has every Kazakh and Kyrgyz letter in both cases. Measured
  before (`analyze-rot.txt`, CER % clean / hard): kk Paddle 0.2 / 3.4 (2.2 over both), Tesseract fast 0.0 / 38.6
  (24.0), best 0.0 / 36.7 (22.8), cloud 0.0 / 0.0. By the recommendation rule Paddle.
- Kyrgyz on the PC (ONNX Runtime, `paddle_ref.py`, the v5 detector and the cyrillic recognizer): eight sentences
  drawn with Pillow in 11 styles (Arial 56 light and dark, 30 and 22 px, bold, italic, Cambria, Segoe UI, Times,
  Courier, white bold over a blurred photo-like background): 0.0% errors in every style. The same run on the Kazakh
  and Russian sentences of the comparison: kk 0.6 / 0.1 (қ → к twice on the dark screen, ұ → ү in Times and Courier),
  ru 0.0 / 0.2. Pillow screens are easier than the browser-drawn ones (no tilt, no device scene); Kyrgyz's letters
  are a subset of Kazakh's. Tesseract was not run on the PC.
- What Paddle gets wrong on the Kazakh device screens: the Kazakh letters read as the Russian letters they come
  from: қ → к (Оқушылар → Окушылар, Қазақстан → Казакстан), ң → н, ғ → г/в/ф (тоғызда → товызда), ұ → ү/у, і → и
  (Кітапхана → Китапхана), ө → о/е, һ → н. Of 323 words 32 were misread; 24 of them match the right word with the
  letters taken as their Russian twins and Latin look-alikes as Cyrillic.
- tessdata (MB, fast / best): kaz 4.73 / 7.53, kir 9.93 / 11.95 (rus 3.86 / 15.30 for scale).
- Speed: as Russian (one model for all Cyrillic languages).

### Fonts

- The catalog's downloadable fonts with the Cyrillic Extended subset (Literata, Inter, PT Serif, PT Sans) have all
  nine Kazakh letters ә ғ қ ң ө ұ ү һ і in both cases (cmap of the catalog's files). kk and ky need `fontSubsets`
  latin, cyrillic, cyrillic-ext (the letters other than і are in U+0460-052F); the system fonts cover U+0400-052F.
  Georgia lacks them (drawn as boxes in the PC run).

### Text rules

- Quotes (CLDR): kk « » and “ ”; ky « » and „ “. In the Wikipedia sample «» dominate (kk 876, ky 580). Dialogue lines
  open with an em dash; Kyrgyz often uses – as the dash.
- Question particle: Kazakh ма/ме/ба/бе/па/пе is a separate word (болған ба?); Kyrgyz -бы/-би/-бу/-бү/-пы … is
  attached (барасыңбы).
- Abbreviations with a period (the Kazakh document guide: units take none, doubled forms one: жж., ғғ., бб.):
  Kazakh т.б. (81 in the sample), ж. (75), жж., ғ., ғғ., б.з.д., б.з.б., мыс., млн., млрд., трлн., т., б., қ., шам.,
  ағыл., орыс., с. е., ш. б., обл., ауд., көш.; Kyrgyz ж. б. (241 spaced, 27 joined), б. з. ч., ж. (1991-ж.), жж.,
  к. (19-к.), кк., б.а., проф., обл., түн., түш., and Russian ones in references (с., т., изд-во). т.б., ж., жж., ғ.
  often end a sentence too; the next character tells.
- Ordinals and dates: a number, a hyphen and an ending (1-ші, 9-орын; Kyrgyz 2007-жылы, 19-кылымдын); Kazakh years
  mostly without the hyphen (1997 жылы). No ordinals with a period in running text, so the row "Ordinals with a
  period" does not extend to them.
- Apostrophes only in foreign names (д’Ивуар).
- Hyphenated words are frequent: kk 934 lowercase hyphenated tokens in the 54k-token Wikipedia sample (сондай-ақ 37,
  қарым-қатынас, азық-түлік, бір-біріне), 93 of them headwords as a whole and 345 more whose parts all are; ky 393, 14
  and 76. After digits: kk 199 (-ші, -інде, -ден), ky 545 (-жылы 161, -жылдын 58, -ж).
- Encyclopedic Kyrgyz contracts with hyphens (м-н for менен, ж-а for жана).

### Word frequency

- wordfreq 912caf6: neither language. hermitdave FrequencyWords: Kazakh only, 4,672 words from two subtitle files
  (CC BY-SA 4.0); no Kyrgyz.
- Leipzig Corpora Collection: the terms page (Wayback copy of 2025-11-12) gives the site's data and services under
  CC BY-NC and "the text corpora offered for download" under CC BY. Archives on `downloads.wortschatz-leipzig.de`
  (static, fixed dates): `kaz_newscrawl_2016` and `kir_newscrawl_2016` in 10K … 1M sentences (100K: 18.7 and
  19.9 MB, SHA-256 dcbe3bf7…e68773 and 145fd1c7…03d9bb; 300K: 56.9 and 60.0 MB; 1M: 194 and 202 MB). Each holds
  `-words.txt` (id, word, count; case-sensitive, punctuation included) at a varying place in the archive.
- The 100K lists: kk 114,695 lowercase Cyrillic word forms after reading Latin i as і (52,751 seen twice or more),
  ky 100,901 (48,535). Of the top 1,000: kk 711 and ky 475 are wty headwords or form rows (the others mostly inflected
  forms: болып, екенін; Kyrgyz менен, деп, боюнча).
- Other corpora (CC-100, OSCAR, MADLAD-400, Wikipedia dumps) are raw text: a list would have to be built and
  hosted.

### Audio

- Lingua Libre: kk Q9252 `kaz` 125 files, 3 speakers; ky Q9255 `kir` 1,288 files, 1 speaker (letters and common
  words). Commons titles carry one code each.
- Twenty common words (as in group 4b), Lingua Libre / Wiktionary / either: kk 4 / 0 / 4 (кітап, мектеп, күн, ағаш);
  ky 17 / 1 / 17 (Ky-китеп). By the row "Audio order": Lingua Libre first for both.
- Commons by prefix: `Kk-` 156 files, titled in a Latin transliteration (Kk-9-toğyz.ogg, city names) or not words,
  some vulgar phrases; the app's search by the Cyrillic word finds none. `Ky-` 110, mostly unrelated images.
- Text to speech: Google's engines list neither language. eSpeak NG has both (F-Droid, GPL-3.0); RHVoice has Kyrgyz
  (Azamat, Nazgul; LGPL); the sherpa-onnx engine has Kazakh Piper voices (ISSAI data, CC BY 4.0; APKs of 44-144 MB);
  Microsoft's cloud has Kazakh. The popup already says "No text-to-speech voice for this language is installed".

### Names, flags, codes

- Translation services: `TranslationLanguageTable` lists kk and ky for both services, same codes. DeepL has KK and
  KY (API key); LibreTranslate and Argos have Kyrgyz only.
- Names (JDK 21, CLDR): Kazakh / казахский / カザフ語 / 哈萨克语 / Kasachisch / kazajo / kazakh / kazako / cazaque /
  kazachski / Kazakça / Tiếng Kazakh / kazahu; Kyrgyz / киргизский / キルギス語 / 柯尔克孜语 / Kirgisisch / kirguís /
  kirghize / kirghiso / quirguiz / kirgiski / Kırgızca / Tiếng Kyrgyz / kirgīzu. Own names: қазақ тілі, кыргызча. The
  Chinese name is the one used in China for its Kyrgyz minority (吉尔吉斯语, after the country, is also used).
- circle-flags 379588b: `kz.svg` (712 bytes), `kg.svg` (869): paths, circles, a mask and a group, as the existing
  flags; `scripts/flags/` needs kz, kg in `COUNTRIES`.
- E-ink codes: KK, KY.

### Plain fixes found (no owner decision)

- Form rows to other scripts (Arabic, romanization, Yañalif) and letter equivalents dropped, as the Jawi and Baybayin
  rows.
- Kyrgyz look-alikes: ɵ Ɵ as ө Ө.
- A hyphen after digits or an abbreviation ends that word; the ending alone is also tried with its hyphen (-ға).
- The Kyrgyz question particle among the rules; abbreviation lists and quotes of both languages.
- Verb chains checked against the part-of-speech tag, since wty's rules field is empty.
- `fontSubsets` with cyrillic-ext; tessdata kaz, kir; `paddle-cyrillic` for kk, ky; flags kz, kg.

### Questions for the owner

See `plans/2026-10-19-group-4e.md`.
