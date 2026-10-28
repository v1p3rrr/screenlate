# Dictionary engine (hoshidicts) — findings for the dictionaries work

Implemented: submodule at `dictionary/engine-hoshidicts/src/main/cpp/hoshidicts`, JNI in `src/main/cpp/jni_bridge.cpp`, form-of entries in `form_of.cpp`, Kotlin side `HoshidictsEngine`. Instrumented test `HoshidictsEngineTest` covers import, deinflection, form-of entries, frequency, non-BMP text, styles and media.

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
- Storage: `filesDir/dictionaries/<uuid>/`, staging in `filesDir/dictionaries/.staging/<uuid>/` (same file system, rename), downloads in `noBackupFilesDir/dictionary-downloads/` (older versions used `cacheDir`, where the system could delete a stopped download's partial file; the clean-up deletes that directory).
- `DictionaryImportWorker` handles bundled assets, local files, URLs, collection exports and models in one unique WorkManager chain (`APPEND_OR_REPLACE`). Task names travel in a `dictionary-import-name:` tag because WorkInfo does not expose input data. Source kinds travel separately in `dictionary-import-source:`; bundled progress also reports its source for tasks queued before that tag existed. Never infer the bundled source from the display name: it changes to an asset stem such as `jmdict-english` while running. Language file deletion cancels those tasks and waits for their running steps before reading the registry again and deleting files.
- WorkManager stops (2.12): a stopped worker goes back to ENQUEUED with its stop reason and runs again once its
  constraints hold; only `STOP_REASON_CANCELLED_BY_APP` (1) ends it. A result returned while the stop lands is dropped
  and the work runs again, so the worker records every final result (`DictionaryImports.recordEnd`, the output's bytes
  per job id in DataStore) before returning it, and a run that finds a recorded end returns it at once. `Result.retry()`
  is not recorded: it runs again anyway. When the network goes, the read fails with an `IOException` and the reason-7
  stop lands at about the same time, in either order; the worker checks the validated network (now or within 3 s) and
  treats such a failure as a wait for the network, not as a break-off, so it never counts a retry or ends the task.
  `getStopReason()` needs API 31 (minSdk is 30); `Result.Success`/`Retry` are restricted API, compare with
  `Result.retry()` instead.
- WorkManager merges a chained job's input with its prerequisites' outputs (`WorkerWrapper`: `listOf(workSpec.input) +
  getInputsFromPrerequisites`, and `OverwritingInputMerger` lets the later ones win). Before `OwnInputMerger`, a job
  queued after a stopped download in the same chain took that download's `url`, `archive`, `name` and `entry_id`: Resume
  of one stopped card downloaded another stopped entry (seen on 2026-10-22). Jobs queued by older builds keep the
  default merger.
- `BundledDictionaries` installs each `assets/dictionaries/*.zip` once, keyed by name and size in DataStore, so a deleted bundled dictionary does not come back. Zip assets are stored uncompressed (`noCompress`), which `openFd` needs.
- The sort frequency dictionary is the first enabled frequency dictionary (rank-based → ascending, occurrence-based → descending) until phase 4 adds a setting.
- `DictionaryRepository.generation` changes on every reload, engine load and bundled tag-notes fill. `DictionaryLookup` keeps styles, tag descriptions, frequency modes and "has term dictionaries" per generation (`PerGeneration`), so a scan or a shown word no longer re-reads all CSS through JNI or queries Room; `styles()` returns the same list instance until then, which the overlay uses to skip re-encoding it for the page. A new kind of registry change that affects those values must go through `reloadLocked()` or bump the generation itself.
- Glossary media is served to the popup at `https://appassets.androidplatform.net/media?d=<dictionary>&p=<path>`.
- `DictionaryRepository.withLookup(language)` holds the registry mutex through both loading the language's dictionaries
  and the query callback. Use it for every operation that reads the loaded set (terms, characters, CSS, media, language
  sampling); preparing and querying in separate calls allows another language or a registry mutation to replace the
  shared native set between them. Do not call registry mutations from the callback: the mutex is not reentrant.
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

### Size after import of catalog downloads (measured 2026-10-29)

- 150 dictionaries on the API 37 emulator, `du -sk files/dictionaries/*` against the catalog's `downloadSize`
  (installed / download): term dictionaries median 2.22, size-weighted 2.12, 1.2-4.7 (JMdict Russian 4.7, wty-ja-ja
  3.7, small Wiktionary pairs into Russian 3.4-3.9, the smallest up to 5.5); kanji about 3 (Unihan Hanja 5.5, 0.2 MB);
  IPA transcriptions (kind PITCH) median 9.9, weighted 10.7, 7.1-14; Kanjium pitch 13.8; plain frequency lists 8.7
  (Jiten) and 15.9 (aozora-jukugo); word forms 1.1-1.25 (the catalog carries their measured `installedSize`).
- Built frequency dictionaries do not follow their download: wordfreq builds take 1.2-13.6 MB (a short list about 25
  times its download, a long one about 12 MB at the 100,000-word cut), corpus counts 7-14 MB (Thai 4.6 times a 1.5 MB
  archive, the Leipzig ones 0.5-0.8 times 8-24 MB archives).
- Shares per kind miss single dictionaries by up to two times: CC-CEDICT took 4.4 times its archive (33.4 MB),
  CC-CEDICT Hanzi 5.2 (22.2 MB), so the Chinese set (CC-CEDICT, Hanzi, wordfreq) took 69 MB where the shares give
  43 MB. Hence `catalog/installed-sizes.json`: 137 entries measured on the emulator (`scripts/catalog/installed_sizes.py`),
  scaled to the entry's current archive; the shares (`InstalledShare` in `DictionaryCatalog.kt`) only for entries not
  measured. `installedBytesAtMost` (the free space check) takes a measured size plus a quarter, else a share above
  the largest measured.

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
- The tags of phonetic transcriptions (`ipa` term meta) are dropped too; `TranscriptionTagBanks` reads them into
  `transcription_tags.bin`. The Chinese Wiktionary transcriptions (wty-zh-ipa, 38 MB zip, 2026-10-22) have 363,021
  rows with 4,403,032 tagged transcriptions (4.1 million distinct, 27,385 distinct tag lists, 4.3 tags each); the
  first builder kept a hash map entry with a set of tags per transcription and ran out of the 192 MB heap at
  import, so the tags were not written. Format 2 (`STT2`) packs 39 bits of the hash with the tag set
  index into one long, sorts chunks of 2 MB during the import and merges them into the file, which lookups map
  instead of reading (36 MB for wty-zh-ipa; the heap stayed under 80 MB). The import took 62 s on the emulator, most
  of it parsing the banks. Files of format 1 are still read.
- Index strings come back as their raw JSON (`glz::raw_string`): a line break is a backslash and `n`. Revision, author, URLs,
  description and attribution are decoded (`decodeIndexText`); the title stays raw because lookups name
  dictionaries by the engine's title.
- The importer writes into `outputDir / title` and removes that path when an import fails; `std::filesystem` lets a
  `..` segment or an absolute title leave `outputDir`. `ArchiveTitles` refuses such titles (checked on every
  `index.json` of the zip) before the native import.
- Form-of entries (2026-10-15): hoshidicts keeps glossary items `["lemma", ["tag", ...]]` in the glossary and does not
  follow them (the record's "redirects" field is written as 0). The popup drew such rows as numbered items with only
  their "non-lemma" tag, and the row ranked first, as its headword equals the scanned text while the real word matches
  only by its reading (じどう with six empty items above 自動). `form_of.cpp` follows them after hoshidicts' lookup, as
  Yomitan's `_getDictionaryDeinflections` does for every language (Japanese too): one step, the items leave the
  glossary, entries without definitions go, a lemma's terms join with the matched text and the chain plus the tags
  (shown as the dictionary writes them, owner 2026-10-15), and a term found as well or better keeps its result (so
  じどう → 自動 by its reading and 混ぜない → 混ぜる by deinflection look as before). The bridge asks hoshidicts for
  every result and cuts to `maxResults` afterwards, so form rows do not take places. The Japanese Wiktionary
  dictionaries are mostly such rows: wty-ja-ja (2026.10.11) 170,552 of 314,082 rows, wty-ja-en 540,043 of 647,190,
  wty-ja-ru none. wty-ja-ja's items: 106 K inflections (`imperfective godan`, `polite continuative`; the deinflector
  covers the full forms, the bare stems such as まぜ only exist there), 68 K `transliteration` (kana → kanji; 89% of the
  lemmas have that kana as their reading anyway; the rest are on and kun readings of single kanji and readings missing
  from the entry, にっぽんご → 日本語), 4.8 K `alternative` (豪州 → オーストラリア), 1 K `kyūjitai` (社會 → 社会). No row
  mixes definitions and items there. Jitendex (12 K rows, `redirected from 飴とムチ`) and Kolobok (137 K, `вариант
  написания: 社會`) add an item to rows that also show a "⟶ lemma" link; such rows drew an empty second bullet before,
  and now their lemma joins the results with that tag (the first dictionary in order wins when several lead to one
  term; 社會 shows 社会 with Jitendex's tag). Warm lookups on the emulator with ten dictionaries stay at 10–22 ms; the
  first one after start reads the lemmas' pages (681 ms for 豪州). Installed on the emulator: wty-ja-ja 48 MB, wty-ja-en 94 MB; splitting the rows into a
  compact table at import (as `languages/main` does for other languages) would cut them to roughly 22 and 10 MB.
- Headword marks in the form-of table (2026-10-19): `LanguageSupport.headwordMarks` passes the marks to drop
  (`HEADWORD_MARKS` JSON) to `split_form_of`. Russian, Ukrainian, Belarusian, Croatian and Serbian (sh) key form rows without
  their stress marks and store lemmas without them. Greek (`every_headword`) keys every headword that has
  definitions without its tonos, pointing to itself with no tags, and keeps lemmas as written, since its headwords
  carry the tonos; `split_form_of` then reads every bank, and `jni_bridge.cpp` writes the table even when the archive
  is not rewritten (a dictionary without form rows). The lookup needs nothing new: a key without marks is a form.
  Hindi (2026-10-19) adds `replacements` (`{"ँ": "ं"}`): `MarkStripper::keys` gives the headword with the
  replacement, without its marks (nukta after क–ह) and with both, each distinct and unlike the headword, so a
  candrabindu headword is keyed under anusvara too. Wiktionary's Hindi headwords are NFC: nukta letters decomposed
  (क + ़; U+0958–U+095F are composition exclusions), and the bundle composes the text the same way.
- Respelled form rows (2026-10-22, Slovak): with `every_headword`, a headword's self key (no tags) marks its
  result respelled, which sorts after the spellings found as written; the same key of a form-of item in that row
  (robit for robiť's item urobiť "imperfective") did not, so urobiť came before robiť. The table (version 2) stores a
  respelled bit in the low bit of each entry's tag set id; `split_bank` sets it for the keys of form rows where every
  headword is keyed, and `candidate_lookup` treats it as a tag-less item. An entry added both as written and as a key
  keeps the written one. Version 1 tables are read without the flag (`FormOfTableFile` and `FormOfTable::open` accept
  both), so dictionaries imported before keep the old order until they are imported again.
- The letters' key after the replacements (2026-10-22, Slovenian): `MarkStripper::keys` made the key without the
  letters' marks from the text without the stress marks only, so a Slovenian form with ł or ə kept them there (žȓł
  gave žrł, not zrl, and zrl found nothing). The letters' key is now made from that text with the replacements
  applied (ł → l, ə → e). Lithuanian, the only other language with `letter_marks`, has no replacements, so its keys
  are unchanged. Lemma names still lose only the marks: a lemma with ə or ł (várən, 97 form-of items in sl-en) names
  no headword.
- Form rows with template leftovers (2026-10-19, Bengali): wty writes some form headwords with what its templates
  left, and `split_bank` keyed them under the whole text, which no lookup ever gives. `form_headwords` cleans them:
  a romanization or note after whitespace and `(` or `,` is cut off ("মাসটি (maśṭa (colloquial), maśṭi (formal))",
  "বইগুলো , boigulo"), two spellings split at whitespace and `/` are keyed both ("ծագմանը / ծագմանն"; in bn the second
  is a romanization, "কলকাতাকে  / kolkatake", keyed but never matched), whitespace before a combining mark goes (the
  ending set apart, মুখ ে), and a row with `{{{` (an unfilled template parameter, `{{{3}}}ে`) keys nothing. Counts in
  wty *-en: bn 5,972 + 337 + 78 of 53,646 form rows; hi 90 marks apart of 275,411; hy 1,883 of 942,487; ka 11 and 73
  of 503,440. An ending set apart as য় (কলকাতা য়, 89 rows in bn) stays, as য় is a letter; Bengali's own -য় ending
  finds the word anyway. Lemmas are left as they are (none of the samples had leftovers there).
- Entries without a part of speech (2026-10-19, Bengali): wty's Bengali entries have blank `rules`, so a deinflected
  candidate (conditions ≠ 0) never fitted them. `fits` in `candidate_lookup.cpp` already lets an untyped candidate
  match a blank-rule entry (English phrasal verbs); `glue.js` `untypedRules` marks every deinflected candidate of the
  listed languages (bn) untyped. Hindi needs nothing: Yomitan has no Hindi rules, so its candidates have no
  conditions. Georgian and Tagalog have Yomitan rules and blank-rule wty entries too, to be checked on their branches.
- Arabic keys (2026-10-20): wty-ar-en (2026.10.11) has 1,019,643 rows, 992,134 of them form rows and 27,509 with
  definitions. Its headwords are written without harakat (11 carry some), the reading with them (كتاب, كِتَاب; 27,230
  readings have marks), while form rows name their lemmas vowelled (مدارس → مَدْرَسَة). `every_headword` keys each
  headword with `replacements` ({ى→ي, ة→ه}, the spelling running text often uses at a word's end) and with
  `further_replacements` ({أ إ آ ٱ → ا}) made on top of the first ones, so اكبر finds أكبر while أمام does not find
  إمام (the text keeps its hamza and is looked up as written). `MarkStripper::keys` adds the headword with the first
  replacements and with both, without the marks and with them. Lemmas lose their harakat (`lemmas_as_written`
  false), so مَدْرَسَة leads to the headword مدرسة. Greek keeps its lemmas as written:
  `HeadwordMarks.lemmasAsWritten` defaults to `everyHeadword`, which used to decide it.
- Template names among form tags (2026-10-20): 158,443 of wty-ar-en's form rows carry the name of the Wiktionary
  template that made them (`ar-infl-a`, `ar-infl-an-maksura`) as a tag word next to the grammatical ones.
  `cleaned_tags` drops every word `xx-infl` or `xx-infl-…` (two or three lowercase letters) and a tag left empty, for
  every language; no other language's wty export has them in the samples.
- Forms outside the script (2026-10-20): 795 Arabic form rows are romanizations (ḡarafāt), keyed under Latin text
  that an Arabic lookup never gives and that a Latin-script dictionary's lookup could hit. `forms_in_script` drops a
  form row whose headword has none of the `bases` letters (`MarkStripper::drops_form`); Arabic and Persian set it
  (Persian's Cyrillic Tajik forms, ҳалол → حلال).
- Persian keys (2026-10-20): `every_headword` with `replacements` {ي→ی, ك→ک, ى→ی, ۀ→ه} and the zero-width
  non-joiner replaced by nothing, harakat dropped, so آن‌ها is keyed under آنها and كتاب text finds کتاب. The text
  side never holds the non-joiner: the bundle's `removePersianZeroWidthNonJoiner` replaces the text (the candidate
  then has one preprocessor step, which the word itself also gets, so it still ranks first by match length).
- Hebrew keys (2026-10-20): Wiktionary's Hebrew forms are 82% pointed (כְּלָבִים); `every_headword` keys them under
  their letters as well (points and cantillation U+0591-05C7 dropped, but not the maqaf, paseq, sof pasuq and nun
  hafukha), the lemmas form rows name lose their points (`lemmasAsWritten = false`), and form rows with no Hebrew
  letter (Phoenician 𐤊𐤋𐤁) are dropped (`forms_in_script`). wty-he-en imports in 8.0 s on the emulator (engine
  1.6 s) with a form-of table of 235,424 forms.
- Hebrew prefixes are the bundle's own rules (`own-transforms.js`). The prefixes ש, כש and מ each have a first-step
  rule and a narrower one, and Yomitan tries every rule at the first step whatever its conditions, so they give a
  duplicate candidate there; the engine merges candidates by term and drops duplicate chains, so the popup shows one.
  A plain word whose letters are a pointed form's (שלום, the construct שְׁלוֹם) is that form's lemma.
- A form whose lemma is the headword the text is itself adds no chain (owner, 2026-10-20, every language): `visit` in
  `candidate_lookup.cpp` skips a tagged form-of item when its lemma is the text with its own entry and no rule
  applied, or a lemma the table also lists with no tags (the step back to itself of a headword keyed without its
  marks: kuca → kuća). Croatian kuća (its genitive plural too) and Hebrew שלום show no form tags, as the empty chain
  of the direct match is the only one; Yomitan shows the form's tags. Text that is not the headword keeps them
  (kućama, pointed שְׁלוֹם), and so does another lemma of the same text (sam: the adjective sam, and biti through
  "first-person singular present").
- JSON escapes in headwords (2026-10-20, found with a test dictionary): hoshidicts' parser reads a term's expression
  and reading as `std::string_view` over the raw JSON (`yomitan_parser.hpp` `Term`), so a headword written with an
  escape (`"آن\u200Cها"`, or every non-ASCII letter from Python's `json.dump` with its default
  `ensure_ascii`) is stored with the backslash and no lookup matches it, while `split_bank` decodes the same field
  (glaze into `std::string`) for the form-of table. wty and Yomitan's exports write the characters themselves (the
  app finds wty-fa-en's نمی‌دانم), so no catalog dictionary is affected; a third-party dictionary made that way
  would import but find nothing. Not fixed: hoshidicts' code; decoding there costs a copy per string at import.
- A dictionary of forms only (2026-10-28, the Finnish "Word forms" item): hoshidicts' import throws "empty
  dictionary" when no bank leaves an entry (`importer.cpp`, no blob offsets). `split_form_of` with `forms_only`
  therefore writes no archive; `import_forms_only` in `jni_bridge.cpp` makes the folder from the index (title with
  " (forms)") and the form-of table. Without a `.hoshidicts_N` file, `DictionaryQuery::add_dict` skips the folder
  silently, while `load` still opens its table, as tables are read from every term path.
- Languages: most archives name none. `DictionarySample` reads the first 300 rows of the first bank without loading
  it whole; `LanguageGuess` decides by script shares (Latin, Cyrillic or Hangul ≥ 40% of letters is the target language
  even with Japanese examples in the entries; kana under 5% of kana+kanji is Chinese). Measured on the test
  dictionaries: bilinguals have 53–100% foreign letters in their definitions, Japanese monolinguals up to 26% Latin
  (デジタル大辞泉 opens with loanwords and their etymologies; at the former 15% it was taken for ja → en), with Android's `TextClassifier`
  naming Latin and Cyrillic languages. Installed dictionaries were sampled once through lookups of ~80 common words.
