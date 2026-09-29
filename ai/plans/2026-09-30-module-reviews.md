# Module code reviews (2026-09-30)

Owner request (2026-09-30): review the functional modules of `ai/notes/module-map.md` one after another without a
command per run: several review runs per module, fixing what they find, then the next module. Questions that need the
owner are collected in `2026-09-30-module-review-questions.md` and asked when the owner is back or at the end.

## Decisions

| Decision | Choice |
|---|---|
| Modules | The 11 not reviewed yet: 1–8 and 11–13 of the module map, in the map's order. Modules 9 and 10 (two runs each already) wait for another time. |
| Runs per module | Runs 1 and 2 cover the whole module and its integration paths; run 3 covers the fixes of runs 1–2 and the code around them. A 4th run covers run 3's fixes when run 3 changed code, except very small edits that almost never cause problems (docs, comments, renames, strings). No 5th run: what the 4th finds is fixed and reported. |
| Review level | xhigh (the code-review procedure's level). |
| Findings that need the owner | Behavior, UI, defaults and other real choices go to the questions file with context and options (recommended first); the code stays as is and the work goes on. |
| Plain bugs | Fixed without asking, with tests. |
| Bugs in another module's code | Recorded under that module below and fixed in its own runs. Bugs in modules 9 and 10 are fixed after all 11 modules, each with a check of the code around the fix. |
| Commits | One commit per module after its last run, pushed to `origin main`. Messages describe the change. |
| Checks | Unit tests (and page tests when page scripts change) after every run; the emulator after every run whose fixes touch UI or overlay behavior (Screenlate's accessibility service may be enabled there). |
| Releases | None without the owner's command. |

## Procedure of a run

1. Runs 1–2: read the module's code in full and follow each integration path of the module map into the other side
   (callers, callees, JS bridge, DataStore keys, Room, WorkManager, intents, JNI, network). Run 2 looks again with the
   run 1 fixes in place and pays attention to what run 1 did not cover.
2. Run 3 (and 4): the diff of the module's earlier runs, the enclosing functions and their callers.
3. Findings are ranked with a concrete failure scenario and reported (ReportFindings); plain bugs are fixed, choices go
   to the questions file, bugs in other modules to "Deferred" below.
4. After fixing: tests, emulator when needed, the plan's progress table, docs and notes when behavior changed. The
   ReportFindings list is re-sent with outcomes.
5. After the module's last run: `ai/status.md`, commit, push, a short Russian progress message.

## Current position

Updated after every step so the work survives a context reset or a paused session: module 3 (OCR) done in 4 runs, committing; next: module 4 (dictionaries: api, engine, import), run 1 — read ai/notes/module-map.md for its files, apply the deferred getNullableStringArray item.

## Progress

| # | Module | Run 1 | Run 2 | Run 3 | Run 4 | Commit |
|---|---|---|---|---|---|---|
| 1 | Build, CI and release | 9 found, 9 fixed | 3 found: 1 fixed, 1 question (Q3), 1 deferred | 1 found (test), fixed | not needed | see git log |
| 2 | Common and language support | 4 found: 3 fixed, 1 deferred | 1 found (test), deferred | 1 found (validation), fixed | not needed | see git log |
| 3 | OCR | 7 found, fixed | 4 found, fixed | 3 found, fixed | nothing found | see git log |
| 4 | Dictionary registry, imports and catalog | | | | | |
| 5 | Lookup and engine | | | | | |
| 6 | Lookup page and rendering | | | | | |
| 7 | Anki export | | | | | |
| 8 | Audio | | | | | |
| 11 | App shell, home, search and localization | | | | | |
| 12 | Backup and Yomitan settings import | | | | | |
| 13 | Updates, About and logs | | | | | |
| — | Deferred fixes in modules 9 and 10 | | | | | |

## Findings

Per module and run: the findings, what was fixed, what went to questions.

### Module 1, run 1

1. `DownloadAssetsTask`: the cache is keyed by file name only, so a changed URL keeps the old archive (and a
   re-download could not replace the file on Windows); the CI cache key ignores the task's code.
2. `DownloadAssetsTask`: any HTTP 200 body is cached and bundled, also an error page instead of a zip.
3. `DownloadAssetsTask`: no connect or read timeouts.
4. `release.yml`: the APK version comes from `git describe`, not from the pushed tag (two tags on one commit).
5. `ScreenlateVersion`: `--match v[0-9]*` also takes `v0.2.0-rc1`, which then falls back to 0.1.0.
6. `DownloadAssetsTask`: a redirect without `Location` fails with a bare NPE.
7. `release.yml` header misdescribes manual runs (they use the release key and publish when run on a tag).
8. Setup action: stale "about 48 MB" for the bundled dictionaries (25 MB).
9. `libs.versions.toml`: foundation-layout pinned outside the Compose BOM, filed under build-logic plugins.

All fixed: `DownloadCache` (URL stored next to each file, zip check, 60 s timeouts, replacing move, named errors;
`DownloadCacheTest`, 9 cases), the CI cache key follows `DownloadCache.kt`; `release.yml` writes `version.txt` from
the tag before the build; tag patterns `v[0-9]*.[0-9]*.[0-9]*` minus `v*[!0-9.]*` in the build and the workflow
(checked in a scratch repository); comments; foundation-layout from the BOM (resolves to 1.12.1 as before). Build
and all unit tests pass; the bundled dictionaries were downloaded again through the new cache.

### Module 1, run 2

Also read: R8 keep rules and JNI lookups (nothing found), icon scripts, CI logs of the latest run (caches hit, no JDK
download; compiler warnings of other modules went to Deferred).

1. The bundled Jiten list comes from an unversioned URL; a CI cache refresh ships another size, which re-imports it on
   update and brings it back after a deletion → question Q3.
2. `release.yml`: a failed publish skipped "Keep as artifacts" and lost the built APKs → fixed (`failure() ||`).
3. Compiler warnings in modules 3, 4 and 12 → Deferred.

### Module 1, run 3

Over the diff of runs 1–2 and its callers (the task's only user is `app/build.gradle.kts`; the CI cache key path, the
workflow steps run locally in a scratch repository, the debug build's version on Windows: 0.1.4, code 104). One
finding: `DownloadCacheTest` read its request log across threads without synchronization → fixed
(`CopyOnWriteArrayList`). No 4th run: the only change was that test detail.

### Module 2, run 1

The whole of `core/common` with its tests, and the integration paths: lookup start, spelling variants and romaji in
`DictionaryLookup`/`LookupVariants`/`inSource`, the ML Kit script choice, `Sentence` and the Anki markers, audio
defaults, the overlay's word start, fonts, theme/e-ink settings in the app and the overlay, `AppLanguageResources` in
the application and the accessibility service, the local network checks for audio sources, and every log call that
passes an exception (none of the 14 calls without `.redacted()` can carry recognized text, words or term URLs: they
log ML Kit, capture, dictionary import and AnkiDroid provider failures).

1. `redacted()` recursed forever on a loop of causes → fixed (identity set of copied throwables; test).
2. The shared settings DataStore had no corruption handler: a damaged file crashed every start → fixed (defaults
   replace it; test, skipped on Windows, where DataStore's file storage cannot replace a file).
3. IPv4-mapped IPv6 addresses of private hosts were not local → fixed (dotted and hex forms; test).
4. The theme mode to dark mapping is written three times (app theme, search page, overlay) → Deferred.

### Module 2, run 2

The module again (the Mozc table and its pending rules, `MappedText` replacement mapping and its callers, word start
against lookup start, sentence rules, fonts, audio and Anki marker defaults, app language resources below and above
API 33, which app settings keys backups carry) and the run 1 fixes. One finding: the default audio sources are
`AudioSourceType` names as strings that `AudioSettings.defaultSources` silently drops when they match no type, and no
test ties them together → Deferred (module 8).

### Module 2, run 3

Over the diff of runs 1–2 (the redaction copy, the settings store and its callers, including backups, which restore
through DataStore edits and never write the file; the IPv4-mapped check). One finding: the hex form checked only the
first group of the mapped address → fixed (both groups must be hex; test). No 4th run: that was a one-line
validation.

### Module 3, run 1

`core/ocr` in full, the overlay's scan (`startScan`, `refineBand`, `ocrErrorText`) and the OCR test screen. Seven
findings, all fixed:

- A Lens timeout came out as `TimeoutCancellationException`. With a failed ML Kit run it failed the flow, and the
  overlay rethrows cancellations, so the scan ended with no error and the spinner on. The cloud-only path made it a
  plain `IOException`, shown as a network error. All three Lens paths now turn it into `SocketTimeoutException`
  (tests).
- `recognizeRegion` turned the caller's cancellation into a null result → rethrown (test).
- A cancelled ML Kit task cancelled the waiting coroutine, with the same silent end → fails the call instead (the
  Tasks adapter has no JVM test: its listeners run on the main looper).
- The Lens response was not closed when the call was cancelled while it arrived → closed in the resume handler.
- The protobuf reader took negative and Int-overflowing lengths, which move backwards (an endless loop on a garbled
  response), and read fixed32 without a bounds check → one bounds-checked `advance` (test).
- The deferred opt-in for `advanceUntilIdle` in `CompositeOcrTest`.

### Module 3, run 2

The module again (reading order, layout, bands, symbol alignment, protobuf, network status) with the run 1 fixes, and
the callers: the overlay's scan, warm-up and band refinement, the OCR test screen, the shared HTTP client. Four
findings, all fixed:

- Reading order matched continuations on the paragraphs' own engine field, so a paragraph without one (the page's
  engine) was never joined with one that names it, as lines added by `withMissingFrom` do → effective engines (test).
- The screenshot copy for ML Kit was lost when the scan was cancelled while it was made (withContext drops its
  result) → freed then (test).
- `warmUp` hard-coded Japanese → takes the language; the overlay passes its own.
- Unused `TextLayout.remainingInWord`, `lineText` and `OcrCharacter.wordIndex` → removed.

### Module 3, run 3

Over the diff of runs 1–2. Three findings, all fixed:

- The ML Kit draft started with the default start, so a scan cancelled before it ran skipped its `finally` and never
  freed the copy → started undispatched (test).
- `askLens` also turned a timeout of the caller's own into a `SocketTimeoutException` → stays a cancellation (test).
- The test scheduler's clock needs the opt-in in seven more places → one opt-in on the test class.

### Module 3, run 4

Over run 3's changes (the undispatched start runs only cheap code before ML Kit's first suspension; emissions and
their order stay; the caller check only fires when the caller is cancelled): nothing found. Emulator: a scan with the
cloud result and a lookup; on a slow network the cloud request timed out after 16 s, the device result became final
and the ⚠ said the timeout.

## Deferred

Bugs found in another module's code, fixed in that module's runs (modules 9 and 10: after all 11 modules).

- Module 3 (done in run 1): `CompositeOcrTest` uses `ExperimentalCoroutinesApi` without an opt-in (compiler
  warnings on CI).
- Module 4: `DictionaryImportWorker.kt:139` and `DictionaryImports.kt:197` call the deprecated
  `Data.getStringArray` (use `getNullableStringArray`).
- Module 8: a test that every name in `LanguageSupport.defaultAudioSources` maps to an `AudioSourceType`
  (`AudioSettings.defaultSources` drops unknown names silently).
- Module 12: `YomitanSettingsTest.kt:106-108` has unnecessary `!!` (compiler warnings).
- Modules 9–11 (at the end): the theme mode to dark mapping is written three times (`ui/theme/Theme.kt:76`,
  `search/SearchScreen.kt:87`, `OverlayController.isDarkTheme`); one helper next to `ThemeMode` in core:common.
- Module 9: `OverlayController.ocrErrorText` still matches `TimeoutCancellationException`, which OCR no longer
  reports (a timeout is a `SocketTimeoutException` now); drop that branch and its import.
- Module 9: a cloud-only scan that fails without app text shows the generic OCR error for every reason but offline,
  although `ocrErrorText` knows paused, timeout and HTTP errors; show that reason instead.

## Changelog

- 2026-09-30: plan created from the owner's answers (modules, runs, level xhigh, questions file, deferring other
  modules' bugs, commits per module, emulator after each run).
- 2026-09-30: owner asked to discuss later how ➕ behaves on a draft or an old popup and whether ➕ should cancel the
  cloud request (questions Q1, Q2).
