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
| Jitendex 2026.08.11.0 (pinned) | https://github.com/stephenmk/stephenmk.github.io/releases/download/2026.08.11.0/jitendex-yomitan.zip | 38.7 MB | CC BY-SA 4.0 |
| Jiten Global frequency | https://api.jiten.moe/api/frequency-list/download?downloadType=yomitan (index: https://api.jiten.moe/api/frequency-list/index) | 7.8 MB | CC BY-SA 4.0 |
| Kanjium pitch accents | https://github.com/toasted-nutbread/yomichan-pitch-accent-dictionary/releases/download/1.0.0/kanjium_pitch_accents.zip | 1.1 MB | data CC BY-SA 4.0 (Kanjium), script MIT |

Jiten Global uses `frequencyMode: rank-based` (lower is more frequent → Ascending).

## Renderer source

Hoshi Reader Android `app/src/main/assets/hoshi-web/popup/popup.js` (GPL-3.0): `renderStructuredContent` (~l.1220), `createDefinitionImage` (~l.932), `constructDictCss` (~l.481), furigana segmentation (~l.353–480), pitch graph (~l.1402–1580). Extract into `:dictionary:render-yomitan` with copyright headers.

## App-side pieces (implemented)

- `DictionaryRepository` is the only writer of the registry; every change reloads the engine (`DictionarySet`: term/freq/pitch/kanji directories in priority order, filtered by source language). Replacing a dictionary with the same title keeps its id, priority and enabled state; old files are deleted after the reload unmaps them.
- Storage: `filesDir/dictionaries/<uuid>/`, staging in `filesDir/dictionaries/.staging/<uuid>/` (same file system, rename), downloads in `cacheDir/dictionary-downloads/`.
- `DictionaryImportWorker` handles three sources (bundled assets, a local file, a URL with optional `indexUrl` resolution) in one unique WorkManager chain (`APPEND_OR_REPLACE`). Task names travel in a `dictionary-import-name:` tag because WorkInfo does not expose input data.
- `BundledDictionaries` installs each `assets/dictionaries/*.zip` once, keyed by name and size in DataStore, so a deleted bundled dictionary does not come back. Zip assets are stored uncompressed (`noCompress`), which `openFd` needs.
- The sort frequency dictionary is the first enabled frequency dictionary (rank-based → ascending, occurrence-based → descending) until phase 4 adds a setting.
- Glossary media is served to the popup at `https://appassets.androidplatform.net/media?d=<dictionary>&p=<path>`.
- Jitendex titles contain the date (`Jitendex.org [2026-08-11]`), so catalog entries match installed dictionaries by `indexUrl` or a title prefix.

## Import speed: what Manabitan does and what applies here

Checked 2026-09-27 after the owner mentioned a Yomitan fork with much faster imports. It is Manabitan (github.com/ManabiIO/manabitan, GPL-3.0, a Yomitan browser extension fork by the author of Manabi Reader). The import work landed in March–May 2026; the September 14 commits are README, Anki presets and worker reliability.

Where Yomitan loses time and what Manabitan replaced:

- `JSON.parse` of every term bank into objects plus schema validation → a one-pass C parser compiled to WASM over the raw bytes (`ext/js/dictionary/wasm/term-bank-parser.c`, `term-bank-wasm-parser.js`); glossaries stay raw JSON bytes (`raw-term-content.js`) and are not re-serialized; only `index.json` is validated.
- IndexedDB with several indexes per row → SQLite WASM on OPFS for metadata plus own binary shard files for term records and content (`term-record-opfs-store.js`, `term-content-opfs-store.js`), string interning, zstd with a shared glossary dictionary, content dedup by hash.
- Eager work → reverse (suffix) indexes built lazily on first use, image dimensions and media reads deferred, next term bank prefetched while the current one is written.
- Optional pre-converted archives ("artifacts": `manabitan-import-artifact.json`, `term_bank_N.mbtb`) that skip parsing; ordinary Yomitan zips still work.
- Installed dictionaries stay usable while an import or update runs.

hoshidicts already does the equivalent natively: glaze reads glossaries as `raw_json`, term banks are processed on a thread pool, glossaries are zstd-compressed with a dictionary trained per import (`train_zstd_dict`), offsets are radix-sorted, the store is its own binary format, and imports do not block lookups. Bundled Jitendex + Jiten + Kanjium install in ~5 s on the emulator. Nothing from Manabitan's zip import path needs porting.

The slow path in Screenlate is our own Yomitan collection export converter (`YomitanBackup` + `RawJsonScanner`). Today it decodes the whole file from UTF-8 into chars, builds a `String` per value, re-encodes it and deflates it (`BEST_SPEED`) into intermediate zips that hoshidicts then inflates. Transferable ideas, in order of expected gain:

1. Byte-level scanning: work on `ByteArray` buffers, find value boundaries without decoding, copy raw row slices straight into the output; decode only keys, table names and dictionary titles.
2. Store intermediate archives uncompressed (`ZipEntry.STORED`; hoshidicts' `zip.cpp` reads method 0), saving deflate and inflate. Needs a temporary file per dictionary about as large as its data in the export.
3. Parallelism: rows arrive table by table (all terms of all dictionaries first), so no dictionary is complete before the end of the file; parallelize the conversion of separate dictionaries or the writing, not the import of finished ones.
4. Going further, skipping the intermediate zip would need an import entry point in hoshidicts that takes banks from a directory or memory (a change in the GPL module or upstream).

The ~5.5 min on the emulator for the owner's 2.7 GB export was not split into conversion and hoshidicts import; measure both before choosing. The plan's later item (phase 7) covers this work.
