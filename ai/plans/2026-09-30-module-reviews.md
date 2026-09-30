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

Updated after every step so the work survives a context reset or a paused session: all modules reviewed, the deferred fixes done and every answer to Q1-Q10 implemented. Nothing is left in this plan; the follow-up review of v0.1.4..HEAD and its open items are in `ai/status.md` (Next session).

## Progress

| # | Module | Run 1 | Run 2 | Run 3 | Run 4 | Commit |
|---|---|---|---|---|---|---|
| 1 | Build, CI and release | 9 found, 9 fixed | 3 found: 1 fixed, 1 question (Q3), 1 deferred | 1 found (test), fixed | not needed | see git log |
| 2 | Common and language support | 4 found: 3 fixed, 1 deferred | 1 found (test), deferred | 1 found (validation), fixed | not needed | see git log |
| 3 | OCR | 7 found, fixed | 4 found, fixed | 3 found, fixed | nothing found | see git log |
| 4 | Dictionary registry, imports and catalog | 9 found: 8 fixed, 1 question (Q4) | 4 found, fixed | nothing found | not needed | see git log |
| 5 | Lookup and engine | 8 found: 5 fixed, 2 questions (Q5, Q6), 1 deferred | 2 found, fixed | 2 found, fixed | nothing found | see git log |
| 6 | Lookup page and rendering | 7 found: 6 fixed, 1 question (Q7) | 6 found, fixed | 1 found, fixed | nothing found | see git log |
| 7 | Anki export | 5 found, fixed | 8 found: 7 fixed, 1 deferred (overlay) | 1 found, fixed | nothing found | see git log |
| 8 | Audio | 8 found, fixed | 3 found, fixed | nothing found | not needed | see git log |
| 11 | App shell, home, search and localization | 14 found: 12 fixed, 2 questions (Q8, Q9) | 4 found: 3 fixed, 1 question (Q10) | 1 found, fixed | not needed | see git log |
| 12 | Backup and Yomitan settings import | 8 found: 7 fixed, 1 noted | 3 found: 2 fixed, 1 noted | 2 found, fixed | not needed | see git log |
| 13 | Updates, About and logs | 6 found: 5 fixed, 1 noted | 1 found, fixed | none | not needed | see git log |
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

### Module 4, run 1

`dictionary/api` (registry, imports, catalog, updates, Yomitan backup), the engine's import path and the
Dictionaries screen's task cards. Nine findings: eight fixed, one question.

- The native importer writes `outputDir/<title>` and deletes it on failure; a title with `..` or a leading `/`
  deleted files outside the staging directory (all dictionaries with `../..`) → `ArchiveTitles` checks every
  `index.json` title before the import and refuses unreadable archives (tests).
- A failed import returned `Result.failure`, which WorkManager passes on to every APPEND dependent unrun → a failure
  is now a successful result with an error; `ImportTask` maps it to FAILED, and any exception is caught with its
  message (tests).
- An update whose new title belonged to another dictionary compared with `replaces`, not the dictionary actually
  replaced, and left the collider's files → compares with the replaced row, deletes the collider's files after the
  reload (androidTest, both fail on the old code).
- `CatalogEntry.matches` ignored the kind: the Jiten frequency entry matched Jitendex → same kind only (test).
- A collection import stopped at the first failing dictionary → imports the rest, then reports each failure by title
  (`importEach`, `YomitanBackup.titleOf`; tests).
- Import tasks were sorted by random UUID → an order tag at enqueue (test).
- The language fill after the bundled install caught only `IOException` → any non-cancellation exception is logged.
- The deferred `getStringArray` deprecation.
- Question Q4: the failed card names the reason instead of the dictionary.

### Module 4, run 2

The whole module again with the run 1 fixes. Four findings, all fixed:

- The engine kept the revision as raw JSON, while the update check compares it with the decoded remote revision: an
  escaped revision (`2026\/09\/30`) always looked like an update and showed its backslashes → decoded like the other
  index texts, also in the one-time decoding of stored texts (androidTest, fails on the old code).
- The docs did not mention the new failure behavior → `docs/architecture.md`, `docs/usage.md`,
  `ai/notes/dictionary-engine.md`.
- The BOM constant and its test were invisible characters in the source → `\uFEFF`.
- No test went through the native importer with a bad title → androidTest; without the check the importer deleted
  the test's sibling directory, which confirms the run 1 finding.

Considered, no change: on Android 14+ `ZipFile` itself refuses archives with `..` or absolute entry names, so such
archives now fail with "failed to open zip" (they only come from malicious archives); the unused `ImportTask.titles`.
Tool note: tool inputs decode `\uXXXX` and halve `\\`; write backslashes in edit scripts with `chr(92)`.

### Module 4, run 3

The diff of runs 1 and 2. Nothing found; checked the collision rule for plain imports (the title match makes `collided` null), works queued by the previous version (no order tag, `Result.failure`), and the error length limit. No run 4. All unit tests, the registry and engine androidTests pass. No emulator run: no screen changed, the task mapping is unit tested.

### Module 5, run 1

`DictionaryLookup`, variants, `YomitanSorter`, lookup settings, the hoshidicts engine and JNI bridge, and their use in
the overlay, the search screen and `PopupNotes`. Eight findings: five fixed, two questions, one deferred.

- `hasTermDictionaries` counted enabled term dictionaries of any language and with missing files, unlike the lookup:
  nothing found then hid the popup silently instead of the no-dictionary message → `hasTermDictionaries(language)`
  asks the prepared lookup (androidTest).
- Dictionary links in the popup were looked up with the scan length setting, so a link to a longer term failed; the
  search screen scanned its whole query without a bound (a long text from the selection menu held the engine lock for
  thousands of prefixes) → `DictionaryLookup.lookupQuery` scans the whole query up to 100 characters, as Yomitan does
  for the search field and links (test).
- After score the sorter preferred kana terms (hoshidicts' rule), while Yomitan orders by longer term, term text and
  more definitions → Yomitan's tiebreaks (test, fails on the old code).
- Scan length and result limit restored from a backup were used unchecked → clamped when read.
- Question Q5: the no-dictionary text always says they are still being installed.
- Question Q6: the engine cuts the list at the limit before dictionary priority is applied.
- Deferred to module 11: `SearchViewModel.search` catches `CancellationException` in `runCatching` (harmless now).

Considered, no change: inflection labels follow the in-app language (the application's `getResources` is wrapped);
negative or huge JNI scan lengths are bounded by the text length; link lookups after a closed screen go to a
destroyed page, which ignores them.

### Module 5, run 2

The whole module again with the run 1 fixes. Two findings, fixed:

- The clamp of restored lookup settings had no test → `LookupSettingsRepositoryTest` (fails on the old code).
- `LookupSettings.SLOW_MAX_RESULTS` was never used (the screen warns above the default) → removed.

Also: `yomitan-behavior.md` notes the sorter's string compare, the engine's cut (Q6) and whole-query lookups. Checked
on the emulator: a 3000-character text from the selection menu opens the search screen at once with the first
term's entries. Considered, no change: the language-aware check now takes the repository lock per empty lookup
(cheap once loaded); the lock order is repository → engine only.

### Module 5, run 3

The diff of runs 1 and 2. Two findings, fixed:

- The language-aware check went through `prepareLookup`, which loads the engine; the overlay calls it outside the
  lookup's `try`, so a failing engine load would have thrown a second time and ended the service →
  `DictionaryRepository.hasTermDictionaries(language)` reads the registry with the same filter as the engine load
  (`usable`), without loading.
- The androidTest toggled a switch only to force a reload → checks the switch, the language and missing files
  directly, and that the loaded set agrees.

### Module 5, run 4

The run 3 change: the extracted filter is the load's own, the check runs on IO without the repository lock (it only
reads). Nothing found. All unit tests, the registry androidTests and the engine androidTests pass.

### Module 6, run 1

- Fixed: the popup's back button drew the earlier view with its note buttons reset and left `PopupNotes` pointing at
  the pushed view's notes; the page now reports `onViewRestored` and the notes are marked again (no auto-play).
- Fixed: `LookupPage.run` dropped a queued render when any other script (note states, audio menu) came in before the
  page was ready; only a new view clears the queue now.
- Fixed: `LookupPage.evaluate` never returned when the renderer died or the page was destroyed; waiting evaluations
  get null. `destroy` also forgets the ready state and the queue.
- Fixed: `scopeCss` took `@charset`/`@import`/`@namespace` plus the next rule for one at-rule (that rule stayed
  unscoped) and prefixed keyframe selectors; statement at-rules are dropped, keyframes kept as they are.
- Fixed: the results key joined expression and reading without a separator.
- Fixed: stacked KDoc on `setActions`, the `PageState` KDoc on `PageTheme`, a misplaced brace.
- Question Q7: the page has no Content-Security-Policy, so dictionary CSS can load remote resources.
- Checked on the emulator: search screen, ➕ gives 📖, a kanji view and back keep 📖.

### Module 6, run 2

- Fixed: clips arriving after the loading audio menu was closed opened it again; they only fill a menu still open
  for that entry.
- Fixed: a menu (audio clips, "add anyway") stayed over a redrawn view and acted on the new view's entries; drawing
  entries closes it.
- Fixed: changing the entry buttons (`setActions`) redrew the entries and dropped the ➕/📖 states; they carry over.
- Fixed: kanji views had only the kanji labels, so the selection's Copy button showed the raw key; every view gets
  the common labels.
- Fixed: structured content could set any CSS property (e.g. `position: fixed` over the popup); only Yomitan's list
  of properties applies now.
- Fixed: kanji outside the basic plane (𠮟) were not kanji for furigana and taps; the pattern covers Yomitan's CJK
  ranges.
- Checked on the emulator: search results with inflection, furigana and Jitendex styles.

### Module 6, run 3

The fixes of runs 1-2 hold: the queue keeps configuration out (it is replayed from `persistent`), evaluations are
only touched on the main thread, the style list matches Yomitan's schema and the Jitendex entries still look the
same. Found next to them:

- Fixed: `Popup.update` (OCR status, theme; the app always sends the first view) replaced a view pushed by a link or
  a kanji tap with the first view, back button still shown. Now it updates the first view in the history, and the
  view on top takes only the theme and OCR status.

### Module 6, run 4

The `update` change: `history[0]` is always the first view, `current` is set whenever a view was pushed, a pushed
view is never compact, so ⚠ stays in the header. Nothing found. Page tests (69) and unit tests pass.

### Module 7, run 1

- Fixed: `{furigana}`, `{furigana-plain}`, `{sentence-furigana}`, `{sentence-furigana-plain}` and `{cloze-body-kana}`
  were not HTML-escaped (OCR text with `<` or `&` broke the card).
- Fixed: `AnkiNotes.add` turned a cancellation into `AddResult.Failed` (an error toast when the overlay stopped).
- Fixed: an enum value unknown to this version (a newer backup) reset all Anki settings; unknown values fall back to
  their defaults now.
- Fixed: the glossary media copy took its extension after the last dot of the whole path (a dot in a folder failed
  the note) and left the file when `addMedia` threw.
- Fixed: failed AnkiDroid queries were logged unredacted (the message may quote the first field).
- Looked at and fine: sentence extraction, duplicate scopes, overwrite modes, presets, the crop editor (frames from
  `CropFocus` are always larger than the minimum size, so the resize ranges are never empty).

### Module 7, run 2

Whole module again, with its integrations (Anki settings screen and view model, home problems, Yomitan settings
import, backup, the overlay's note source, the search screen's notes).

- Fixed: concurrent refreshes of the Anki settings screen could land out of order and show the previous note type's
  fields; the latest refresh now cancels the earlier one (cancellation is rethrown, not shown as an error).
- Fixed: choosing a note type while AnkiDroid returned no fields dropped that note type's saved templates; the switch
  is skipped then.
- Fixed: a template changed from outside while its field had focus (suggested templates) was never shown and the next
  keystroke wrote the old text back; the field takes the new template once it loses focus.
- Fixed: the screen showed "AnkiDroid is not installed" until the first check; the state starts unknown.
- Fixed: the note's screenshot file stayed in the cache when adding failed or was cancelled; it is deleted in finally.
- Fixed: a note AnkiDroid refused was reported with an English reason inside a localized toast; `AddResult.Rejected`
  with a translated message (14 locales).
- Fixed: `PopupNotes.release()` did not cancel a pending auto-play.
- Deferred to the overlay module: the note screenshot in app-text-only mode hides only the bubble and the scan layer,
  so on API 30-33 (and on the display-capture fallback) the popup window is in the picture.
- Emulator: note added from the search screen (local AnkiDroid), Anki settings screen opens straight to the note
  settings.

### Module 7, run 3

Review of the module 7 diff.

- Fixed: `coerceInputValues` does not reach map values, so an overwrite mode unknown to this version (in
  `overwriteModes` or `savedTemplates`) still reset all Anki settings; `OverwriteMode` reads through a serializer
  that maps unknown names to COALESCE (names are stored as before).
- Checked: the latest-wins refresh cannot apply a cancelled result (cancel and result both run on the main thread);
  the template field converges on the stored value after focus changes; the screenshot file is deleted after the
  result is reported.
- Emulator: the stored settings still load after the change.

### Module 7, run 4

Review of the serializer change: names encode as the generated enum serializer did, nothing else serializes the
enum. Nothing found.

### Module 8, run 1

- Fixed: `AudioSettingsRepository.update` read the settings outside the DataStore edit, so close changes overwrote
  each other; it reads and writes in one edit now.
- Fixed: the clip cache ignored the sources and language, so a clip (or "no audio") stayed after the sources changed;
  the key includes them.
- Fixed: a source type unknown to this version reset all audio settings; such sources are left out and other values
  fall back to their defaults.
- Fixed: `AudioSource` had a private companion object, which holds the generated serializer; decoding a source from
  another class failed with `IllegalAccessError` (found while writing the fix above).
- Fixed: without a known content type the clip extension came from the last dot of the whole URL (a host name gave
  `com/`, the write failed and the source was reported as failing); only the file name counts now, and `audio/webm`
  maps to webm.
- Fixed: clip downloads were read whole before the content-type check and without a limit; the type is checked
  first and anything over 10 MB is skipped.
- Fixed: a dialog test that was still running after the dialog closed or its type changed came back and played;
  tests and test clips keep only the latest job.
- Done (deferred from module 1 review): a test that every language's default audio source names map to a source
  type.
- Emulator: the stored sources load, the source test lists clips from every source, a test clip plays.

### Module 8, run 2

Whole module again, with its integrations (popup audio and notes, home failure card, Yomitan import, backup, the
local network permission flow).

- Fixed: without a network every source throws `UnknownHostException`, which is not recorded as a source failure,
  so the word was cached as having no audio until the process restarted; a source that could not be asked now
  keeps the word out of the cache.
- Fixed: Yomitan's old source name `jpod101-alternate` (renamed to `language-pod-101` in Yomitan's options version
  50) was imported as JapanesePod101 word audio.
- Fixed: URL templates take Yomitan's `{language}` placeholder too (docs/usage.md updated; the in-app hints still
  name the two common ones).
- Looked at and fine: the player (completion and errors reset it), text-to-speech start, the home failure card
  (failures of removed sources are hidden), the local network permission flow.

### Module 8, run 3

Review of the module 8 diff: the combined source attempt keeps the old failure bookkeeping (a source that answered
clears its failure before downloads), the size check stops a stream after 10 MB, settings reads apply the legacy
default mapping inside the edit as before, cancelled test jobs leave no stale state. Nothing found; no run 4.

### Module 11, run 1

- Fixed: the back arrow of every screen called `popBackStack()` directly; two quick taps during the 700 ms fade
  popped the home screen too and left an empty window. Back and every button that opens a screen act only while the
  current screen is resumed, so a second tap during a transition also no longer opens a screen twice.
- Fixed: each return to the home screen started another problem check; an older check that finished last put back
  a card a newer one had dropped. A new check cancels the one still running.
- Fixed: after "Download again" on a missing dictionary, the next return to the home screen showed the same card
  again while the download ran (its files are missing until it ends); such dictionaries stay hidden until the import
  queue is idle.
- Fixed: `DictionaryRepository.missingFiles` listed directories on the caller's thread, the main thread for the home
  screen; it runs on the IO dispatcher (dictionary:api, a one-line integration fix).
- Fixed: the scan length slider ends at 40 while the setting (and a Yomitan import) allows 100; a longer imported
  value showed the knob at the end, and touching it cut the value down. The slider widens to the stored value.
- Fixed: a link or kanji lookup in the search screen that finished after the search text changed was pushed on top
  of the new results; it is dropped.
- Fixed: the OCR test screen (About → Tools, also in release builds) decoded a picked camera photo at full size, which
  does not fit in memory or on a canvas; images are scaled to at most 4096 px on the longest side. Picking another
  image while one decoded showed "Job was cancelled" as the error; the cancellation is passed on.
- Fixed: `TranslationsTest` checked quotes line by line and skipped strings that continue over several lines (28 of
  them); it checks whole elements now (checked by adding quotes to one).
- Fixed (deferred): `SearchViewModel` wrapped lookups in `runCatching`, which also caught
  cancellation; a replaced search is cancelled, not shown as empty.
- Fixed (deferred): a theme change rendered the search page again and dropped a pushed view; it updates
  the page in place. A system dark mode switch recreates the activity, which still loses pushed views (Q9).
- Question Q8: the background work screen's launch hint names one phone maker and its menu path, in every locale.
- Emulator: two quick taps on back from Settings leave the home screen shown; two quick taps on Settings open it once;
  the search screen restyles when the system theme changes.

### Module 11, run 2

- Fixed: the home screen's "no dictionaries" card counted term dictionaries of any language, while the popup and the
  search screen use only those for the language looked up (or without a stated language); with only, say, a Chinese
  dictionary on, lookups said "no dictionaries" and the home screen showed nothing. Both use
  `DictionaryEntity.isFor(language)` now (dictionary:api), with a test.
- Fixed: the dictionary check at app start ran outside any error handling, so a failure (e.g. a database or asset
  error) crashed the app on every start; it is logged (class name only) and the home screen checks again. The home
  screen's check shows no missing-files card on such a failure instead of crashing.
- Checked: no string resource is unused (520 names, all referenced).
- Question Q10: "Look up in Screenlate" from the text selection menu opens the keyboard over the results.

### Module 11, run 3

- Fixed: the home check can now be cancelled by a newer one, but the catalog lookup inside it still used
  `runCatching`, which caught the cancellation and carried on; it passes the cancellation on.
- Cleanup: the new log tags' companion objects moved to the end of their classes.
- Run 4 not needed: the change above is a one-line catch.

### Module 12, run 1

- Checked (deferred item): `BackupManager.restoreFrom` writes only paths that `BackupLayout.safePath` accepts (no
  empty, `.`, `..` or absolute segments, no backslashes), and `BackupFormatTest` covers `..`; no zip slip.
- Fixed: a backup from a newer version that lists a section this version does not know failed to decode and was
  reported as "not a backup"; unknown sections are left out and the known ones are offered.
- Fixed: restoring only settings read the whole archive, gigabytes with dictionary files; reading stops where the
  dictionary files start when they are not restored.
- Fixed: the Yomitan settings import read any picked file whole into memory (a dictionary picked by mistake); files
  over 16 MB are "not a settings export". A second pick while one was read could be overwritten by the first.
- Fixed: an error while applying Yomitan settings (database or DataStore) crashed the app; it shows a new
  "the import stopped" message (14 locales) and keeps what was applied.
- Fixed: a second tap on "Import" of a Yomitan collection while the file was copied queued the whole collection
  again; the button is off while it is queued.
- Fixed (deferred): unnecessary `!!` in `YomitanSettingsTest` (compiler warnings).
- Noted, not changed: a restore replaces the settings before the dictionary files are read; if reading fails later,
  the settings stay replaced and the screen says only that the restore failed.

### Module 12, run 2

- Fixed: a Yomitan export can hold a custom (or custom JSON) audio source without a URL, which Yomitan keeps
  switched off in practice; the import added it as a source that failed on every lookup and showed up as a failing
  source on the home screen. Such sources are skipped now (the URL is trimmed first), with a test.
- Fixed: applying Yomitan's dictionary switches reloaded the dictionary engine once per changed switch (plus once for
  the order), which took seconds with many large dictionaries. `DictionaryRepository.reorder` takes the switches and
  applies them with the order in one step; an instrumented test covers it (run on the emulator, 13 passed).
- Checked: backup creation, `BackupViewModel`'s section choice on reopening a backup, `PopupFonts` backup and
  restore, `DictionaryRepository.restore`/`applyStates`, the lookup setters' clamping of imported values.
- Noted, not changed: a Yomitan dictionary name whose revision-less key matches several installed dictionaries takes
  the one of highest priority, while a backup's list reports such a name as ambiguous. Both are reasonable for their
  source (Yomitan titles carry the revision, backups keep exact titles).

### Module 12, run 3

- Fixed: when queuing a collection import failed (e.g. the file could not be copied for lack of space), the screen
  said the file was not a collection export. It says the import could not start now (new string in all locales) and
  the failure is logged without file names.
- Fixed: import order in `BackupArchive`.
- Checked on the emulator: a Yomitan export with a blank custom source, a blank custom JSON source and Jisho imports
  one audio source; restoring only the audio section of a 58 MB backup with dictionary files finishes at once and
  says "Restored".
- Run 4 not needed: run 3 changed only a failure message and imports.

### Module 13, run 1

- Fixed: the updater asked Android to install without a prompt (`USER_ACTION_NOT_REQUIRED`) but lacked
  `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, without which Android ignores the request and always asks. The manifest
  declares it (a normal permission, granted at install; checked on the emulator).
- Fixed: a manual check started in About and left before it finished (the screen's scope cancelled) kept the state
  at "Checking" with the check button off until the app restarted. Checks run in the updater's own scope and a
  running check is joined; an update is not started during a check, and a check does not replace "Installing".
- Fixed: when Android wanted a confirmation and could not show its prompt (the app was in the background by then),
  the state stayed "Installing" with nothing to tap. The prompt is kept in the state and a "Continue installing"
  button opens it again (new string in all locales).
- Fixed: "Installing; Screenlate restarts when it is done" was wrong: Android closes the app on a self-update and
  nothing starts it again. The text says the app closes and to open it again (all locales).
- Fixed: saving a log to Downloads removed the pending entry only on an `IOException`; any failure removes it now.
- Checked: release selection and version codes (`ReleasesTest`), APK verification (package, higher version, same
  signers), the receiver is not exported and its `PendingIntent` is explicit; log lines across modules with
  interpolated values (none carries text, words or term URLs; a saved emulator log had no CJK text or query URLs).
- Noted, not changed: a failed "Share logs" shows nothing (saving shows an error); the updater has no unit tests,
  as it runs only in release builds and wraps `PackageInstaller`.

### Module 13, run 2

- Fixed: an install session that failed before its commit (e.g. writing the APK into it failed) was left open with
  its staged copy; it is abandoned now.
- Checked: the announcement across the home and About view models (each version announced once; a skip is not
  undone by the same state), `ProblemReport` field ids against `.github/ISSUE_TEMPLATE/problem.yml`, the NOTICE and
  libraries screens (NOTICE scrolls both ways on purpose), dictionary attributions (only http links open).

### Module 13, run 3

- Checked the changes of runs 1-2: checks started from the main thread only, so the shared check is not raced; a
  prompt opened again after it was dismissed ends in the session's aborted status ("did not install"); a check is
  refused only while "Installing", which ends with the status or with the app's process. No new issue.
- Docs: `docs/usage.md` says the app closes after an update and mentions "Continue installing".
- Checked on the emulator: the permission is granted at install, About shows the development-build hint, saving a
  log to Downloads works.
- Run 4 not needed: run 3 changed only docs.

### Modules 9 and 10, deferred fixes

- The theme mode to dark mapping is one helper, `ThemeMode.isDark(systemDark)` in core:common, used by the app
  theme, the search screen and the overlay (test added).
- `ocrErrorText` no longer matches `TimeoutCancellationException`; the reason text is `cloudErrorReason`.
- A cloud-only scan that fails without app text shows the cloud's reason (paused, timeout, HTTP, network).
- The display capture (API < 34, and the 34+ fallback when no window is found or its capture fails) now hides the
  bubble, the scan layer and the popup; `ScreenCapturer.capture` takes the hiding step as a parameter, so the fallback
  is covered too (before, 34+ never hid anything).
- `onKanji` rethrows cancellation; kanji and link pushes are dropped when another lookup replaced the popup meanwhile.
- Recheck of these fixes: 1 found, fixed (a dropped link push still reported its word to the note actions).
- Emulator: scan, lookup, kanji push and link lookup work (window capture path on API 37; the display-capture path
  cannot be triggered there).

## Deferred

Bugs found in another module's code, fixed in that module's runs (modules 9 and 10: after all 11 modules).

- Module 3 (done in run 1): `CompositeOcrTest` uses `ExperimentalCoroutinesApi` without an opt-in (compiler
  warnings on CI).
- Module 4 (done in run 1): `DictionaryImportWorker.kt:139` and `DictionaryImports.kt:197` call the deprecated
  `Data.getStringArray` (use `getNullableStringArray`).
- Module 8 (done in run 1): a test that every name in `LanguageSupport.defaultAudioSources` maps to an
  `AudioSourceType` (`AudioSettings.defaultSources` drops unknown names silently).
- Module 11 (done in run 1): `SearchViewModel.search` wraps the lookup in `runCatching`, which also catches `CancellationException`;
  rethrow it (today the cancelled result is dropped by `mapLatest`).
- Module 12 (done in run 1): `YomitanSettingsTest.kt:106-108` has unnecessary `!!` (compiler warnings).
- Module 12 (done in run 1): `BackupManager.restoreFrom` writes a dictionary to `File(directory, path)` with a path from the backup
  archive; check that `BackupArchive` refuses `..` segments and absolute paths (zip slip).
- Modules 9–11 (done at the end): the theme mode to dark mapping is written three times (`ui/theme/Theme.kt:76`,
  `search/SearchScreen.kt:87`, `OverlayController.isDarkTheme`); one helper next to `ThemeMode` in core:common.
- Module 9 (done at the end): `OverlayController.ocrErrorText` still matches `TimeoutCancellationException`, which OCR no longer
  reports (a timeout is a `SocketTimeoutException` now); drop that branch and its import.
- Module 9 (done at the end): a cloud-only scan that fails without app text shows the generic OCR error for every reason but offline,
  although `ocrErrorText` knows paused, timeout and HTTP errors; show that reason instead.
- Module 9 (done at the end): `OverlayController.noteSource` in app-text-only mode captures the screen for the note while the popup is
  shown; `capture()` hides only the bubble and the scan layer, so on API 30-33 (and on the display-capture fallback of
  34+) the popup is in the note's picture. Hide the popup window too, or capture before it is shown.
- Module 9 (done at the end): `OverlayController` `onKanji` wraps the kanji lookup in `runCatching`, which also catches
  `CancellationException` and then pushes a view from a cancelled scan; rethrow it.
- Module 11 (done in run 1): `SearchScreen` renders again when the theme changes (`LaunchedEffect(results, theme)`), which drops a
  pushed view (kanji, link lookup); a theme change could update the page instead, as the overlay does.

## Changelog

- 2026-09-30: plan created from the owner's answers (modules, runs, level xhigh, questions file, deferring other
  modules' bugs, commits per module, emulator after each run).
- 2026-09-30: owner asked to discuss later how ➕ behaves on a draft or an old popup and whether ➕ should cancel the
  cloud request (questions Q1, Q2).
- 2026-09-30: owner asked to stop once the weekly usage limit passes 92% (finish the module in progress first; check usage after each module).
- 2026-09-30: owner answered Q1-Q4 of the questions file (➕ grey until the final result with a hint on tap, active at once for app-text words, a new scan closes the old popup; no cloud cancel on ➕; remember deleted bundled dictionaries; name and reason on failed import cards).
- 2026-09-30: owner answered Q5-Q10 (two no-dictionary texts and no switching off or deleting the last dictionary with definitions; all engine candidates before the cut; CSP plus remote-CSS warnings; vendor path only on that vendor; Q9 kept; keyboard only for an empty search). Next: implement the answers Q1-Q8 and Q10.
- 2026-09-30: owner asked to implement the answers autonomously; a new question pauses that item (recorded in the questions file) and work moves to the next one. Order: Q10, Q8, Q4, Q3, Q5, Q7, Q6, Q1/Q2.
- 2026-09-30: all answers implemented, no new questions. Q5's button from search to the Dictionaries screen was added last (`OPEN_DICTIONARIES`). Q6 measured on the emulator: 5-32 candidates for one-kana and long queries and no slower uncut, so no margin. Q7: the check follows Yomitan's idea of warning about remote URLs; dictionary cards are checked from the loaded styles, so only switched-on dictionaries get the ⚠. Q1: the grey ➕ could not be shown on the emulator (no ML Kit draft before the cloud result); check on the phone.
- 2026-09-30: xhigh code review of the whole change set; two bugs fixed (cancelled deletes, a permanent "installing"), new question Q11 (draft sentence after the final text), minor findings listed in `ai/status.md`.
- 2026-09-30: owner answered Q11: the note holds the word the popup shows; a word kept from the draft takes the draft's sentence. Already so, no code change.
- 2026-09-30: security review of the whole project (owner's request). Fixed with the owner's go-ahead: dictionary CSS or
  titles could end the `<style>` in glossary note fields. Unscoped dictionary `@font-face`, CSS-escaped remote URLs the
  warning misses, and `javascript:` hrefs in exported notes are explained to the owner; fixing the first two changes Q7
  ("the CSS is not rewritten") and needs the owner's answer.
- 2026-09-30: owner's answers on the security review: dictionary `@font-face` stays as it is (Q7 unchanged: the CSS is
  not rewritten); `javascript:` hrefs in exported notes stay as they are; changes go through a PR instead of a push to
  main. The question on the CSS scoper and the remote-file warning is asked again in simpler words.
- 2026-09-30: owner's answers: fix the remote-file warning (read CSS as a browser does); leave the CSS scoper as it is;
  do not watch PR #2.
- 2026-09-30: xhigh code review of PR #2 with fixes: the remote-file warning also reads CRLF/CR/form feed as line
  breaks, lets a hex escape take the line break after it inside a string, keeps `/*` inside an unquoted `url(...)`,
  and keeps escaped whitespace inside an unquoted address (all four hid servers Chromium loads). Open for the owner:
  dictionary CSS is checked raw while the page gets `scopeCss`'s output, which can uncover commented rules.
- 2026-09-30: owner approved a narrow scoper change: `scopeCss` finds the `;` of a statement at-rule outside comments,
  strings and escapes, so dropping it cannot uncover commented rules (verified in Chromium; output unchanged on real
  dictionaries' `styles.css`).
