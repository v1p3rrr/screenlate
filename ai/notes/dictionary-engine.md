# Dictionary engine (hoshidicts) — findings for the dictionaries work

Implemented: submodule at `dictionary/engine-hoshidicts/src/main/cpp/hoshidicts`, JNI in `src/main/cpp/jni_bridge.cpp`, Kotlin side `HoshidictsEngine`. Instrumented test `HoshidictsEngineTest` covers import, deinflection, frequency, non-BMP text, styles and media.

## Toolchain

- NDK `29.0.14206865` (clang 21) is installed in `D:\Android\Sdk\ndk`; Hoshi Reader Android uses the same version. Set `ndkVersion` in `:dictionary:engine-hoshidicts`. It also gives 16 KB page alignment by default (the emulator uses 16 KB pages).
- CMake 3.31.6 (glaze needs >= 3.31). hoshidicts itself is C++23/C99.
- hoshidicts supports kanji banks (`add_kanji_dict`, `query_kanji`); the old note saying otherwise was wrong.
- ABIs: `arm64-v8a` (phone) and `x86_64` (emulator).
- hoshidicts goes in as a git submodule (it has nested submodules under `external/`: glaze, zstd, unordered_dense, libdeflate, utf8proc, utfcpp, xxHash, kanji-processor). Clone with `--recursive`.

## hoshidicts API (main branch, 2026-09)

- `dictionary_importer::import(zip_path, output_dir, low_ram) -> ImportResult{success, title, summary, error}`. The dictionary lands in `output_dir/<title>`. `Summary` has title, revision, isUpdatable, indexUrl, downloadUrl, author, url, description, attribution, sourceLanguage, targetLanguage, frequencyMode, styles, counts (terms, termMeta by mode, kanji, kanjiMeta, tagMeta, media).
- `DictionaryQuery`: `add_term_dict / add_freq_dict / add_pitch_dict / add_kanji_dict(path)`, `remove_dict`, `set_dict_order(paths)`, `query_kanji(char)` (kanji dictionaries are supported), `get_media_file(dict, path)`, `get_styles()`, `get_freq_dict_order()`.
- `Lookup(query, deinflector).lookup(text, max_results = 16, scan_length = 16, LookupOptions{frequency_dictionary, frequency_order Auto|Ascending|Descending|Disabled, primary_reading})`.
- Sort order: primary reading → matched length → preprocessor steps → deinflection trace length → exact term match → frequency (Auto: all frequency dictionaries in order) → score → reading == expression. Grouping is by (expression, reading), i.e. Yomitan `group` mode. Only dictionary order is missing compared to Yomitan.
- `LookupResult{matched, deinflected, trace[TransformGroup{name, description}], term, preprocessor_steps}`; `TermResult{expression, reading, rules, score, glossaries[GlossaryEntry{dict_name, glossary (JSON array string), definition_tags, term_tags}], frequencies[{dict_name, [{value, display_value}]}], pitches[{dict_name, pitches[{position, pattern, nasal[], devoice[]}], transcriptions[]}]}`. Tag bank metadata (categories, notes) is not exposed.

## Planned bridge design

- Own JNI, no dependency on the unlicensed Kotlin bridge repos. Native calls return one JSON string (lookup results, import summary, styles); Kotlin parses it with kotlinx.serialization. Glossaries are embedded as raw JSON.
- JNI surface: create/destroy session, import, set dictionaries (term/freq/pitch/kanji paths in priority order), lookup(text, maxResults, scanLength, freqDict, freqOrder, primaryReading), styles, media, kanji.
- Serialize native calls (single dispatcher or mutex).
- Import into a temporary folder, then move `<title>` into `filesDir/dictionaries/`, replacing an existing dictionary with the same title.

## Bundled dictionaries (download at build time, not committed)

| Dictionary | URL | Size | License |
|---|---|---|---|
| JMdict (English) 2026-09-27 (pinned; jmdict-yomitan keeps its daily releases) | https://github.com/yomidevs/jmdict-yomitan/releases/download/2026-09-27/JMdict_english.zip | 15.6 MB | CC BY-SA 4.0 (EDRDG licence), converter MIT |
| Jiten Global frequency | https://api.jiten.moe/api/frequency-list/download?downloadType=yomitan (index: https://api.jiten.moe/api/frequency-list/index) | 7.8 MB | CC BY-SA 4.0 |
| Kanjium pitch accents | https://github.com/toasted-nutbread/yomichan-pitch-accent-dictionary/releases/download/1.0.0/kanjium_pitch_accents.zip | 1.1 MB | data CC BY-SA 4.0 (Kanjium), script MIT |

Jitendex (38.7 MB) was bundled in v0.1.0 and moved to the catalog afterwards to shrink the APK. The numeric prefix is a slot (`BundledDictionaries.pending`): installs that got Jitendex in slot 10 keep it and do not get JMdict on top; `DictionaryRepair` reports a formerly bundled dictionary with missing files as one to download again. JMdict's index.json names no languages; imports fill missing languages from the matching catalog entry.

Jiten Global uses `frequencyMode: rank-based` (lower is more frequent → Ascending).

## Renderer source

Hoshi Reader Android `app/src/main/assets/hoshi-web/popup/popup.js` (GPL-3.0): `renderStructuredContent` (~l.1220), `createDefinitionImage` (~l.932), `constructDictCss` (~l.481), furigana segmentation (~l.353–480), pitch graph (~l.1402–1580). Extract into `:dictionary:render-yomitan` with copyright headers.

## App-side pieces (implemented)

- `DictionaryRepository` is the only writer of the registry; every change reloads the engine (`DictionarySet`: term/freq/pitch/kanji directories in priority order, filtered by source language). Replacing a dictionary with the same title keeps its id, priority and enabled state; old files are deleted after the reload unmaps them.
- Storage: `filesDir/dictionaries/<uuid>/`, staging in `filesDir/dictionaries/.staging/<uuid>/` (same file system, rename), downloads in `cacheDir/dictionary-downloads/`.
- `DictionaryImportWorker` handles three sources (bundled assets, a local file, a URL with optional `indexUrl` resolution) in one unique WorkManager chain (`APPEND_OR_REPLACE`). Task names travel in a `dictionary-import-name:` tag because WorkInfo does not expose input data.
- `BundledDictionaries` installs each `assets/dictionaries/*.zip` once, keyed by name and size in DataStore, so a deleted bundled dictionary does not come back. Zip assets are stored uncompressed (`noCompress`), which `openFd` needs.
- The sort frequency dictionary is the first enabled frequency dictionary (rank-based → ascending, occurrence-based → descending) until phase 4 adds a setting.
- `DictionaryRepository.generation` changes on every reload, engine load and bundled tag-notes fill. `DictionaryLookup` keeps styles, tag descriptions, frequency modes and "has term dictionaries" per generation (`PerGeneration`), so a scan or a shown word no longer re-reads all CSS through JNI or queries Room; `styles()` returns the same list instance until then, which the overlay uses to skip re-encoding it for the page. A new kind of registry change that affects those values must go through `reloadLocked()` or bump the generation itself.
- Glossary media is served to the popup at `https://appassets.androidplatform.net/media?d=<dictionary>&p=<path>`.
- Jitendex titles contain the date (`Jitendex.org [2026-08-11]`), so catalog entries match installed dictionaries by `indexUrl` or a title prefix.

## Import speed: what faster importers do and what applies here

Checked 2026-09-27 after the owner asked about a Yomitan fork with much faster imports. Where Yomitan loses time and what such a fork replaces:

- `JSON.parse` of every term bank into objects plus schema validation → a one-pass parser over the raw bytes; glossaries stay raw JSON bytes and are not re-serialized; only `index.json` is validated.
- IndexedDB with several indexes per row → SQLite for metadata plus own binary shard files for term records and content, string interning, zstd with a shared glossary dictionary, content dedup by hash.
- Eager work → reverse (suffix) indexes built lazily on first use, image dimensions and media reads deferred, next term bank prefetched while the current one is written.
- Optional pre-converted archives that skip parsing; ordinary Yomitan zips still work.
- Installed dictionaries stay usable while an import or update runs.
- Its collection export is a binary SQLite image restored by copying it back; Yomitan's JSON collection export is not read at all, so there is no faster JSON collection import to learn from.

hoshidicts already does the equivalent natively: glaze reads glossaries as `raw_json`, term banks are processed on a thread pool, glossaries are zstd-compressed with a dictionary trained per import (`train_zstd_dict`), offsets are radix-sorted, the store is its own binary format, and imports do not block lookups. Bundled Jitendex + Jiten + Kanjium install in ~5 s on the emulator. Nothing from such a zip import path needs porting.

The slow path in Screenlate was our own Yomitan collection export converter (`YomitanBackup` + `RawJsonScanner`).

### Measurements on the owner's export (2026-09-27, cloud container: 4 cores, JDK 21, hoshidicts built with gcc 14 at -O2)

The owner's `yomitan-dictionaries-2026-01-22-22-46-44.json` (2.73 GB, 16 dictionaries, ~4.1 M bank rows, 18 k media files) was downloaded from the owner's Google Drive into the session scratchpad only.

| Step | Before | After |
|---|---|---|
| Read the bytes | 0.75 s | |
| Decode UTF-8 only | 8.2 s | not done any more |
| Scan everything, copy nothing | 11.8 s | ~3 s |
| Convert, deflate level 1 | 45.8 s, 597 MB of archives | 21.5 s |
| Convert, uncompressed | 34.9 s (old code, level 0), 1742 MB | 9.6–10.5 s (disk-bound: CPU ~3.7 s) |
| Measure pass (new) | | ~4 s |
| hoshidicts import of all archives | 5.4 s (deflated) | 4.3 s (stored), 4.9 s (deflated) |

The conversion was ~90% of the collection import. The old converter decoded the file into chars, built a `String` per value, re-encoded and deflated it. Now `RawJsonScanner` works on bytes (quotes, backslashes and brackets never occur inside multi-byte UTF-8 sequences), copies values in whole buffer runs (`capture`: `fill()` flushes the pending part of a value before refilling; copying string by string cost half the CPU time), and `YomitanBackup` keeps a row as offsets into one byte buffer, matches field names as bytes, remembers the last dictionary name, and presizes bank buffers from the previous bank.

Output check: all 16 archives of the new converter (stored and deflated) have the same entries with byte-identical contents as the old converter's; `index.json` is equal as JSON (key order differed before because it came from a `HashMap`). The converter tests pass against both the old and the new implementation. One dictionary has 8 rows fewer than its Yomitan summary count in both versions (rows whose dictionary name is damaged in the export are skipped on purpose).

### Space check (owner decisions 2026-09-27)

- Temporary archives are uncompressed when they fit, deflated when only that fits; the import stops before writing anything when even that does not fit and says how much is needed and free (Dictionaries screen error card).
- The check is exact about the export: a first pass (`YomitanBackup.measure`) counts bank rows, value bytes and media bytes per chosen dictionary. `CollectionSpace` estimates archives (text, or 40% of it deflated, plus media) and installed size (text/2 + 120 B per row + media + 1 MB), and the peak for the import order (all archives exist first; each is deleted after its dictionary is installed), plus a 128 MB reserve.
- Ratios measured per dictionary on the owner's export: deflated banks 5–38% of their text; installed size 12–140% of the text (highest for frequency and kanji dictionaries with small rows). With the chosen coefficients no dictionary exceeded its estimate (lowest estimate/actual 1.03); the peak estimate was 2.2 GB for 1.83 GB actual uncompressed and 1.8 GB for 1.25 GB actual deflated.
- The installed dictionaries of this export take 921 MB.

### Harness

- hoshidicts builds on Linux with gcc 14 (`std::ranges::to`); clang 18 with libstdc++ 13 fails on `std::expected`. CMake >= 3.31 from pip. `-DHOSHIDICTS_BENCHMARK=ON -DHOSHIDICTS_CLI=ON` gives `benchmark-import <zip> <n>` and `hoshidicts-cli import <zip>` (imports next to the zip).
- The converter compiles on a plain JVM (kotlinx-serialization only), so `dictionary:api`'s converter tests and the `BENCHMARK_COLLECTION=1` test can run without the Android SDK in a small Gradle project that points at the source files.
- Still open: timings on the phone (ART, flash storage); going further would mean an import entry point in hoshidicts that reads banks without a zip.

### Checked locally (2026-09-28)

- Desktop (Windows, JDK 21), `BENCHMARK_COLLECTION=1`: measure 4.3 s, convert 7.1 s for 1661 MB of archives (32 s before); estimated peak 2110 MB uncompressed, 1719 MB compressed.
- Emulator (Pixel 10 Pro AVD, API 36, x86_64), all 16 dictionaries of the owner's export replacing installed copies, read from Downloads through the picked URI:

| Free space | Plan | Measure | Write | Install | Total |
|---|---|---|---|---|---|
| ~2.4 GB | uncompressed | 31.6 s | 43.1 s | 26.7 s | 1 min 41 s |
| 1.9 GB | compressed | 34.1 s | 53.7 s | 13.9 s | 1 min 43 s |
| 1.7 GB | not enough (1.9 GB needed) | 33.2 s | | | card in en/ru, staging empty |

  The same import took ~5.5 min before. Reading the 2.7 GB file through the document provider takes ~30 s per pass on the emulator, so the measure pass costs as much as a third of the import; one small dictionary took 60 s, half of it measuring. Owner decision: skip measuring when the free space is at least four times the file (`CollectionSpace.clearlyEnough`).
- `File.usableSpace` reports ~240 MB more than `df` shows as available on the emulator.
- Replaced dictionaries keep their id, position and enabled state; entry and media counts equal the previous import. Missing languages now come from the catalog on replacement (JMdict (Russian) became ja → ru).
- Found on the way, not caused by this change: a collection dictionary whose title differs only by revision from an installed one (Jitendex.org [2026-01-04] vs [2026-08-11]) is shown as "already installed" but imported as a second dictionary; dictionary descriptions and attributions keep JSON escapes (`
`) because hoshidicts returns index strings raw.

## What hoshidicts leaves out (2026-09-28)

- Tag banks are only counted at import; tag names reach lookups, their descriptions (`notes`) do not. `TagBanks`
  reads them from the archive at import into `tag_notes.json` next to the engine's files (the engine ignores other
  files in its directory); bundled dictionaries get theirs from the APK once (`fillBundledTagNotes`).
- Index strings come back as their raw JSON (`glz::raw_string`): a line break is a backslash and `n`. Revision, author, URLs,
  description and attribution are decoded (`decodeIndexText`); the title stays raw because lookups name
  dictionaries by the engine's title.
- The importer writes into `outputDir / title` and removes that path when an import fails; `std::filesystem` lets a
  `..` segment or an absolute title leave `outputDir`. `ArchiveTitles` refuses such titles (checked on every
  `index.json` of the zip) before the native import.
- Languages: most archives name none. `DictionarySample` reads the first 300 rows of the first bank without loading
  it whole; `LanguageGuess` decides by script shares (Latin or Cyrillic ≥ 15% of letters is the target language even
  with Japanese examples in the entries; kana under 5% of kana+kanji is Chinese), with Android's `TextClassifier`
  naming Latin and Cyrillic languages. Installed dictionaries were sampled once through lookups of ~80 common words.
