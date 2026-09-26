# Dictionary engine (hoshidicts) — findings for the dictionaries work

Implemented: submodule at `dictionary/engine-hoshidicts/src/main/cpp/hoshidicts`, JNI in `src/main/cpp/jni_bridge.cpp`, Kotlin side `HoshidictsEngine`. Instrumented test `HoshidictsEngineTest` covers import, deinflection, frequency, non-BMP text, styles and media.

## Toolchain

- NDK `29.0.14206865` (clang 21) is installed in `D:\Android\Sdk\ndk`; Hoshi Reader Android uses the same version. Set `ndkVersion` in `:dictionary:engine-hoshidicts`. It also gives 16 KB page alignment by default (the emulator uses 16 KB pages).
- CMake 3.31.6 (glaze needs >= 3.31). hoshidicts itself is C++23/C99.
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
