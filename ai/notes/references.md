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

## Language research sources (group 5c: Finnish, Estonian, Hungarian, Azerbaijani, Uzbek)

| What | Where | Notes |
|---|---|---|
| UD test sets | github.com/UniversalDependencies: UD_Finnish-TDT, UD_Azerbaijani-TueCL, UD_Uzbek-UT, UD_Uzbek-UzUDT (CC BY-SA 4.0); UD_Estonian-EDT, UD_Hungarian-Szeged (CC BY-NC-SA 3.0) | Gold lemmas for lookup coverage; measurements only, no test words copied from the NC sets |
| Leipzig Corpora Collection | downloads.wortschatz-leipzig.de/corpora/: `fin_`, `est_`, `hun_`, `aze_`, `uzb_news_2020_10K`; `est_news_2020_100K`, `aze_newscrawl_2013_100K`, `aze_wikipedia_2021_100K`, `uzb_news_2020_30K`, `uzb_wikipedia_2021_100K`, `uzb_newscrawl_2011_100K` (mostly Cyrillic) | Running text for coverage and text counts; frequency candidates for et, az, uz; no 100K news for aze, uzb |
| FrequencyWords et_50k | github.com/hermitdave/FrequencyWords (lists CC BY-SA 4.0) | Subtitle list, compared for Estonian frequency; not chosen |
| ML Kit text recognition languages | developers.google.com/ml-kit/vision/text-recognition/v2/languages | fi, et, hu supported; az, uz experimental |
| PaddleOCR ONNX models | huggingface.co/PaddlePaddle/`<name>`_onnx at the pins of `paddle_models.py` | v5 `latin` lacks ə and ʻ; v6 small has ə; run on the PC with `testdata/ocr/compare/scripts/paddle_ref.py` |
| Finnish and Hungarian analysers | Omorfi (GPL-3.0), Voikko (GPL-2.0+), Hungarian Hunspell data (GPL/LGPL/MPL) | Not used; the ending rules are written from grammar descriptions |
| Lingua Libre items | Wikidata Q1412 (fi), Q9072 (et), Q9067 (hu), Q9292 (az), Q9264 (uz) | Commons titles `LL-Q<item> (<iso639-3>)-<speaker>-<word>.wav`; Wiktionary files `Fi-`, `Et-`, `Hu-`, `Az-`, `Uz-` |

## Custom audio servers

The Yomitan local audio server (a Python server on port 5050, often run in Docker) builds file URLs from its bind address, so its audio source list names files as `http://0.0.0.0:5050/...`. On a desktop that reaches the desktop itself; on a phone it reaches the phone. Behind a reverse proxy the list needs rewriting (nginx `sub_filter` on `application/json`, with `Accept-Encoding` cleared); the app now also rewrites such local file URLs to the list's scheme, host and port. The owner runs one behind an https domain; its address and server access are not recorded in the repository.
