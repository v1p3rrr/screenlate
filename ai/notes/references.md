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

## Custom audio servers

The Yomitan local audio server (a Python server on port 5050, often run in Docker) builds file URLs from its bind address, so its audio source list names files as `http://0.0.0.0:5050/...`. On a desktop that reaches the desktop itself; on a phone it reaches the phone. Behind a reverse proxy the list needs rewriting (nginx `sub_filter` on `application/json`, with `Accept-Encoding` cleared); the app now also rewrites such local file URLs to the list's scheme, host and port. The owner runs one behind an https domain; its address and server access are not recorded in the repository.
