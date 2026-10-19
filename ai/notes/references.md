# External references

| What | Where | Notes |
|---|---|---|
| Lens client | github.com/KolbyML/chrome-lens-ocr (MIT) | `src/proto.rs`, `src/lib.rs`, `src/constants.rs`; see `lens-protocol.md` |
| hoshidicts | github.com/Manhhao/hoshidicts (`main` GPL-3.0, `main-mit` MIT with a Jiten deconjugator) | Import/query/lookup/deinflect; zstd glossaries; term/freq/pitch dictionaries; no kanji dictionaries |
| Kotlin JNI bridge example | github.com/Manhhao/hoshidicts-kotlin-bridge (no license file) | Reference only; we write our own JNI |
| Hoshi Reader Android | github.com/HuangAntimony/Hoshi-Reader-Android (GPL-3.0) | Uses hoshidicts via JNI; `app/src/main/assets/hoshi-web/popup/popup.js` has the structured-content renderer we extract |
| Yomitan | github.com/yomidevs/yomitan (GPL-3.0) | Lookup and sort rules: see `yomitan-behavior.md` |
| AnkiDroid API | github.com/ankidroid/Anki-Android `api/` (LGPL-3.0), JitPack `com.github.ankidroid:Anki-Android:api-v1.1.0` | Permission `com.ichi2.anki.permission.READ_WRITE_DATABASE`, `<queries>` for `com.ichi2.anki` |
| Jitendex | jitendex.org, `indexUrl` https://jitendex.org/static/yomitan.json (CC BY-SA 4.0) | Catalog download (bundled in v0.1.0 only) |
| JMdict (English) | github.com/yomidevs/jmdict-yomitan (data CC BY-SA 4.0 under the EDRDG licence, converter MIT) | Bundled |
| Warodai | github.com/WarodaiProject/warodai-source (CC BY-NC-ND 3.0), no official Yomitan build | Not in the catalog; file import only |
| Kenrowa | Kenkyusha 和露辞典 (commercial) | Not in the catalog; file import only |
| Kolobok 400k (ja-ru) | ganqqwerty.github.io/jp-ru-kolobok-dictionary, `indexUrl` https://ganqqwerty.github.io/jp-ru-kolobok-dictionary/yomitan.json (CC BY-SA 4.0) | Catalog download |
| Jiten frequency dictionaries | jiten.moe/frequency-dictionaries (CC BY-SA 4.0) | Global list bundled, used for sorting |
| JPDB v2.2 frequency | github.com/Kuuuube/yomitan-dictionaries (no license) | Not bundled; importable by the user |
| Kanjium pitch accents | Yomitan-format pitch dictionary (CC BY-SA 4.0) | Bundled |
| Yomitan API | github.com/yomidevs/yomitan-api | Desktop-only native messaging bridge; not usable on Android |
| Paddle Arabic recognizer | huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec_onnx at 14aaedcd (Apache-2.0) | Arabic, Persian and Urdu letters, no ZWNJ, no Hebrew; converted for the comparison, waits for the Paddle upload; `languages.md`, "Group 4d research" |
| Arabic, Persian, Hebrew fonts | Google Fonts (OFL): Noto Sans Arabic, Amiri, Vazirmatn, Heebo, Frank Ruhl Libre; Noto Nastaliq Urdu for Urdu later | Row "Fonts" of `plans/2026-10-19-group-4d.md` |
| CAMeL Arabic frequency lists | github.com/CAMeL-Lab/Camel_Arabic_Frequency_Lists (CC BY-SA 4.0, their paper to be cited) | MSA, classical and dialect lists; not used (wordfreq has Arabic); the only open dialect list, for Egyptian Arabic later |
| FrequencyWords | github.com/hermitdave/FrequencyWords (code MIT, lists CC BY-SA 4.0) | OpenSubtitles 2018 lists (ar, fa, he, ur 50k); not used |
| FreeDict ara-eng | download.freedict.org/dictionaries/ara-eng/0.6.3 (GPL-2.0-or-later) | A reversed Arabeyes word list of low quality; not used |
| Leipzig Corpora Collection | downloads.wortschatz-leipzig.de/corpora/`<name>`.tar.gz; terms wortschatz.uni-leipzig.de/en/usage (downloads CC BY, the rest CC BY-NC) | Kazakh and Kyrgyz frequency lists (`kaz_newscrawl_2016_100K`, `kir_newscrawl_2016_100K`); see `languages.md`, "Group 4e research" |
| PanLex | panlex.org (now CC BY-NC-SA 4.0); mirror huggingface.co/datasets/cointegrated/panlex-meanings (CC0, 2024-03-01 dump) | Kazakh and Kyrgyz translations into English and Russian, to be converted; license checked again before hosting |
| Apertium Kazakh and Kyrgyz | github.com/apertium (apertium-kaz, apertium-kir, apertium-eng-kaz, apertium-kaz-rus; GPL-3.0) | Lemma pairs and morphological analysers; not used |
| Kazakh and Kyrgyz voices | eSpeak NG (GPL-3.0; kk, ky), RHVoice (LGPL-2.1; Kyrgyz Azamat, Nazgul), sherpa-onnx with Piper kk_KZ (Apache-2.0; voices from ISSAI's Kazakh TTS data, CC BY 4.0) | Engines a user installs; Google's have neither language |
| Lingua Libre items | Kazakh Q9252 (`kaz`), Kyrgyz Q9255 (`kir`) | Commons titles `LL-Q9252 (kaz)-Speaker-word.wav` |

## Language research sources (group 5d: Bulgarian, Macedonian, Mongolian)

| What | Where | Notes |
|---|---|---|
| UD Bulgarian BTB, UD Macedonian MTB | github.com/UniversalDependencies/UD_Bulgarian-BTB, UD_Macedonian-MTB (test sets) | Gold lemmas for lookup coverage; measurements only |
| Leipzig Corpora Collection | downloads.wortschatz-leipzig.de/corpora/mon_news_2020_10K.tar.gz, `_100K`; mon_wikipedia_2021_100K | Downloadable corpora CC BY (the project's other data CC BY-NC); word count files; send a User-Agent |
| Mongolian WordNet (MonWN) | github.com/kbatsuren/monwn | README CC BY-SA 4.0, data file header CC BY 4.0; 23,395 lemmas on Princeton WordNet 3.0 synsets |
| UniMorph khk | github.com/unimorph/khk | Mongolian inflection tables; no license file, not usable |
| WikDict | wikdict.com (bg-en, bg-ru) | CC BY-SA; from Wiktionary translation tables, adds little over wty |
| eSpeak NG languages | github.com/espeak-ng/espeak-ng `docs/languages.md` | Which languages have a voice (bg, mk; no mn) |
| HarfBuzz language tags | github.com/harfbuzz/harfbuzz `src/hb-ot-tag-table.hh` | BCP 47 to OpenType tags (bg BGR, mk MKD, mn MNG) |
| Fonts with Bulgarian forms | github.com/google/fonts `ofl/montserrat`, `ofl/commissioner`, `ofl/sofiasans` | OFL; `locl` BGR (Montserrat, Commissioner), Bulgarian by default (Sofia Sans) |
| Google Cloud voices | docs.cloud.google.com/text-to-speech/docs/list-voices-and-types | A hint at the Android engine's languages; the engine's own list is `assets/voices-list-dsig.pb` in its APK |
| Lingua Libre items | Wikidata Q7918 (bg), Q9296 (mk), Q9246 (mn); Khalkha Q6399808 | Commons titles `LL-Q<item> (<iso639-3>)-<speaker>-<word>.wav` |

## Language research sources (group 5b: Welsh, Irish, Scottish Gaelic, Breton)

| What | Where | Notes |
|---|---|---|
| kaikki.org Breton | kaikki.org/frwiktionary/Breton/, kaikki.org/dictionary/Breton/, kaikki.org/ruwiktionary/Бретонский/, kaikki.org/eswiktionary/Bretón/ (Wiktionary, CC BY-SA 4.0) | Per-language JSONL of wiktextract; the only Breton Wiktionary data (wty has none); French edition writes c'h with U+02BC |
| wty language list | github.com/yomidevs/wiktionary-to-yomitan `assets/languages.json` (code MIT), commit f7cc3d7 | The top 150 languages of the English edition; Breton is 195th, so not built |
| Leipzig Corpora Collection | cym_wikipedia_2021_10K / _100K, gle_wikipedia_2021_10K, gle_newscrawl_2014_300K, bre_wikipedia_2021_10K / _100K (downloads CC BY) | Coverage texts and frequency candidates; no Scottish Gaelic corpus |
| Scottish Gaelic Wikipedia dump | dumps.wikimedia.org/gdwiki/20261001/ (CC BY-SA 4.0) | Coverage text (150 longer articles); measurements only |
| Yr Amliadur | research-data.cardiff.ac.uk, DOI 10.17035/d.2020.0120164107 (CC BY-SA 4.0) | Welsh frequency lists from CorCenCC (54,072 forms); the corpus itself is CC BY-NC-SA |
| An Crúbadán | crubadan.org/files/`<lang>`.zip (CC BY 4.0), Kevin Scannell | Web-crawl word lists for cy, ga, gd, br (top 50,000); unversioned files, site down on 2026-10-19 |
| Eurfa | eurfa.org.uk (GPL/AGPL-3.0), Kevin Donnelly | Welsh lemmas and verb forms, no mutated forms; not used |
| BuNaMo, Gramadán | github.com/michmech/BuNaMo (ODbL), github.com/michmech/Gramadan (MIT) | Irish morphology without glosses or mutations; not used |
| Apertium Celtic pairs | github.com/apertium apertium-br-fr, apertium-cym-eng, apertium-eng-gle, apertium-ga-gd (GPL-2.0 / GPL-3.0) | Translation lexicons; not used |
| FreeDict bre-fra | download.freedict.org/dictionaries/bre-fra/0.8.3 (GPL-2.0-or-later) | 27,034 headwords, French glosses; not used |
| Deloof | kevindonnelly.org.uk/deloof/ (GPL-3.0) | Breton–Dutch, 35,480 headwords; not used |
| circle-flags regional flags | github.com/HatScripts/circle-flags `flags/gb-wls.svg`, `gb-sct.svg`, `fr-bre.svg` (MIT) | Wales, Scotland, Brittany |
| Lingua Libre items | Wikidata Q9309 (cy, `cym`), Q9142 (ga, `gle`), Q9314 (gd, `gla`), Q12107 (br, `bre`) | Commons titles `LL-Q<item> (<iso639-3>)-<speaker>-<word>.wav` |
| eSpeak NG Celtic voices | github.com/espeak-ng/espeak-ng `docs/languages.md` | cy, ga, gd; no Breton |

## Custom audio servers

The Yomitan local audio server (a Python server on port 5050, often run in Docker) builds file URLs from its bind address, so its audio source list names files as `http://0.0.0.0:5050/...`. On a desktop that reaches the desktop itself; on a phone it reaches the phone. Behind a reverse proxy the list needs rewriting (nginx `sub_filter` on `application/json`, with `Accept-Encoding` cleared); the app now also rewrites such local file URLs to the list's scheme, host and port. The owner runs one behind an https domain; its address and server access are not recorded in the repository.
