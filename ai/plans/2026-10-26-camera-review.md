# Code review of the camera feature (2026-10-26)

Owner request (2026-10-26): review the camera feature with the `code-review` skill, following the instruction of
`notes/module-map.md` ("How to run each review"): the camera module (19) first, then the other modules the feature
changed, each with its integration paths followed in full. Only the feature's changes are reviewed; code not
changed by the feature is out of scope ("what was not reviewed before you is out of scope now"). Asked before
writing this plan; the answers are the decision table.

## Decisions

| Topic | Decision |
|---|---|
| Scope | The feature's diff on `features/camera-lookup` against its base `073c50a4` (the merge base with `languages/main`): 155 files, about 12,500 added lines, half of them tests. Each run reads the changed code of its area and follows its integration paths into the code it calls and that calls it, as far as needed to judge the changed code. Defects in unchanged code are out of scope; ones seen on the way are listed under "Seen outside the scope" and not fixed (owner, 2026-10-26). |
| Areas | Four, each a separate set of runs (owner): 1) the photo store, `core:photos`; 2) the Camera screen in `app` with navigation, the share target and the build; 3) the overlay and Anki integration in `overlay`; 4) photo recognition in `core:ocr`. File lists below. |
| Order | The camera module first (areas 1 and 2), then the other modules the feature changed (areas 3 and 4), as the owner asked. |
| Runs per area | As in the first review cycle (owner): runs 1 and 2 over the whole area and its integration paths; run 3 over the fixes of runs 1–2 and the code around them; run 4 only when run 3 changed code, except very small edits (docs, comments, renames, strings). |
| Review tool | The `code-review` skill at `xhigh` with `--max-findings all` (owner), on top of the module map's checklist, given in the skill's arguments with the area's files and integration paths. No `--fix`: every finding is checked against the code first; plain bugs are fixed by hand with tests; false alarms are recorded (owner). |
| Owner's input | Plain bugs are fixed without asking. Choices that need the owner (behavior, UI, defaults) go to "Questions for the owner" with context and options, the code stays as it is, and the work goes on; asked together at the end. Short progress messages during the work (owner). |
| Bugs in another area | Fixed when found, recorded under the area whose code it is, so that area's later runs see the fix. |
| Checks | Unit tests and lint after every run. After an area whose fixes change screens or the overlay: the test-model cases of those places on the emulator. At the end: the E cases of the bubble, OCR, popup and Anki on API 37 and the camera instrumented tests on API 30 (owner). |
| Commits | After each area's last run, pushed to `origin features/camera-lookup`; messages describe the change. No merges. |

## Areas

### 1. Photo store (`core:photos`)

Files: `core/photos/build.gradle.kts`, `core/photos/schemas/**`, all of `core/photos/src/main` (`CameraPreferences`,
`LookupSource`, `PhotoAttempt`, `PhotoDocument`, `PhotoDocuments`, `PhotoGallery`, `PhotoHistory`, `PhotoImage`,
`PhotoImports`, `PhotoOrientation`, `PhotoRecognitionEngine`, `PhotoRecognitionJobs`, `PhotoThumbnails`,
`PhotoViewport`, `store/{AttemptCodec,PhotoDatabase,PhotoStore,PhotosModule,RecognitionCodec}`) and its tests
(`src/test`, `src/androidTest`).

Integration paths: the Camera screen and its view models (area 2: `CameraViewModel`, `CameraRecognition`,
`PhotoHistoryScreen`, `CameraSettingsScreen`, `CameraLookup`); the overlay's source gate, photo frame and Anki crop
(area 3: `LookupSources`, `ScreenReads`, `TextFrame`, `NoteImage`, `PopupNotes`); photo OCR (area 4: `PhotoTiles`,
`PhotoAssembly`, `DeviceOcr.pin`, `CompositeOcr.recognizePhotoTile`); Room v1→v3 and the schemas; the files under
`noBackupFilesDir/photos/` and `.staging`; the `camera.preferences_pb` DataStore and the language profiles; MediaStore
(gallery export); the content resolver and image decoders (imports); Hilt modules; threads, cancellation and process
death.

### 2. Camera screen (`app`)

Files: `app/src/main/java/com/vpr/screenlate/camera/*`, `MainActivity`, `navigation/ScreenlateNavHost`,
`home/HomeScreen`, `search/SearchScreen`, `settings/SettingsScreen`, `ui/components/SettingsRows`, the manifest,
`app/lint.xml`, the new drawables and strings (14 locales), `app/build.gradle.kts`, `gradle/libs.versions.toml`,
`settings.gradle.kts`, `NOTICE`, `app/src/debug/.../PhotoTestAccess`, and the tests (`src/test/.../camera`,
`src/androidTest/.../camera`, the changed `languages` fixtures).

Integration paths: `core:photos` (area 1); CameraX lifecycle and permission; the overlay through `CameraSession`,
`LookupSources` and the page's presence (area 3); language profiles and `LanguageInstallation` readiness (the
language chip, "Can't recognize"); `OverlayIntents.openScreen` and the share grant; the bottom navigation, saved tab
stacks, the first run and deep links (module 16); the system photo picker.

### 3. Overlay and Anki integration (`overlay`)

Files: `OverlayController`, `ScreenlateAccessibilityService`, `source/{ScreenReads,TextFrame}`,
`anki/{NoteImage,PopupNotes}`, `install/LanguageInstallation`, `ui/{CropEditor,CropPlacement,CropView,LayerView}`,
`overlay/build.gradle.kts`, and the tests (`ScreenReadsTest`, `SourceChangeTest`, `TextFrameTest`,
`CropPlacementTest`, `LanguageInstallationTest`, `CropEditorTest`).

Integration paths: `core:photos` sources and image leases (area 1); the screen capture and OCR pipeline, scan and
refinement (modules 4 and 14); the popup page and its state; Anki export and the note image (module 11); the
accessibility service lifecycle and reconnects; the bubble's glyph and language chips; the Camera page's presence
(area 2).

### 4. Photo recognition (`core:ocr`)

Files: `BoxIndex`, `PhotoAssembly`, `PhotoTiles`, the changes in `CompositeOcr`, `OcrModels`, `TextLayout`,
`model/DeviceOcr`, `core/common/.../geometry/Box.kt`, and the tests (`BoxIndexTest`, `PhotoAssemblyTest`,
`PhotoTilesTest`, `CompositeOcrTest`, `DeviceOcrTest`).

Integration paths: `PhotoRecognitionEngine` and `RecognitionCodec` (area 1); hit testing through `TextLayout` in the
overlay (area 3); the OCR engines (ML Kit, PaddleOCR, cloud) and their model pinning; language-specific word and
sentence rules (`notes/language-rules.md`).

## Procedure of a run

1. Runs 1–2: `git diff 073c50a4 HEAD` of the area's files, read with the files in full; then follow the integration
   paths. Run 2 looks again with run 1's fixes in place and pays attention to what run 1 did not cover.
2. `code-review xhigh --max-findings all` with the area's files, its integration paths and the checklist of
   `notes/module-map.md` in the arguments.
3. Every finding is checked against the code: plain bugs are fixed with tests, choices go to the questions, false
   alarms and out-of-scope findings are recorded below.
4. Runs 3–4: the diff of the area's earlier fixes, the enclosing functions and their callers.
5. Unit tests and lint after each run; the emulator after the area when its fixes change screens or the overlay; the
   progress table and findings below; docs and notes when behavior changed.
6. After the area's last run: commit, push, a short Russian progress message.

## Current position

Updated after every step so the work survives a context reset: area 1 is reviewed (runs 1–4) and committed; area 2,
run 1 is next. The API 37 emulator runs under the shared lock.

## Progress

| # | Area | Run 1 | Run 2 | Run 3 | Run 4 | Emulator | Commit |
|---|---|---|---|---|---|---|---|
| 1 | Photo store | 19 findings: 9 fixed, 1 false alarm, 7 skipped, 2 questions | 9 findings: 5 fixed, 2 false alarms, 1 skipped, 1 question | 11 findings: 3 fixed, 2 false alarms, 6 skipped | 4 findings: 4 fixed (one change) | `core:photos` instrumented tests pass on API 37 | done |
| 2 | Camera screen | 1 fixed ahead of the runs (below) | | | | | |
| 3 | Overlay and Anki integration | | | | | | |
| 4 | Photo recognition | | | | | | |
| — | Final E regression | | | | | | |

## Findings

Per area: each finding, its verdict (fixed with the commit, question, false alarm, out of scope).

### Area 1, run 1

1. `PhotoStore.pinImage` made a hard link in `.pins` with a copy fallback, but SELinux denies apps hard links
   (checked on API 37: `ln` in the app's domain gives "Permission denied"), so every note image or gallery export
   copied the whole original under the store mutex. **Fixed:** the store counts open `PhotoImage`s per photo directory;
   deleting a pinned photo drops its row at once and deletes the directory when the last image closes; a restart's
   tidy deletes what abandoned images kept. Tests: `PhotoStoreTest` pin cases, instrumented `PhotoImageTest`.
2. A new recognition took its base revision from the caller's snapshot; when the replaced job committed first, the
   new attempt's first save failed and the recognition ended silently with the old text. **Fixed:** a new
   recognition reads the current document's revision when it starts (a retry keeps its attempt's base). Test:
   `aNewRecognitionReplacesTextCommittedAfterItWasRequested`.
3. `save()` stamped the wall clock and trimmed the history by saved time at once; with the clock set back the photo
   just saved could be the one trimmed. **Fixed:** the saved time is at least the latest one plus 1. Test.
4. A failed deletion of the replaced unsaved photo propagated out of `replaceCurrent`/`open` after the new photo was
   already current, so the camera reported a failed capture and skipped recognition and auto-save. **Fixed:** the
   failure is logged and the photo is left to the next start's tidy. Test.
5. A finished attempt whose tiles all succeeded but was never committed (process death between the last tile and
   the commit) has no remaining tiles, so Retry is not offered. **Skipped:** committing it on retry needs the warning
   UI to treat such an attempt as retryable, which would show the warning for a moment after every successful
   recognition; the window is a few milliseconds and a new recognition recovers.
6. The jobs' collector of the current document ended for good after one error, so replacing the photo no longer
   stopped its recognition. **Fixed:** the observation restarts after a second. Test.
7. Engines BOTH with a fixed device model that was deleted fails the attempt as a device error instead of running
   cloud-only. **False alarm:** by design a fixed model is used as named or the attempt fails, so a retry never merges
   tiles of different models; BOTH without a model is a different request.
8. `PhotoViewport.fit` with an empty area throws, and the warning dialog's region map calls it from a Canvas that can
   be drawn at zero size. **Fixed:** the region map skips drawing at an empty size.
9. Renames are not followed by a directory fsync. **Skipped:** after a power loss right after a write, the outcome
   is a dropped photo or text marked unreadable, both handled; an extra sync per write is not worth it.
10. A failed write or sync left the `.tmp` file until the next start. **Fixed:** `writeAtomically` deletes it on any
    failure. Test.
11. Imports copy the shared or picked stream without a size limit. **Question** (below).
12. The camera language flow writes the active language over the stored choice when the chosen one is turned off,
    so turning it off and on loses the choice. **Question** (below).
13. Every tile's attempt save re-encodes all pages so far and decodes the previous attempt. **Skipped:** pages are
    small (tens of KB per tile), a 35-tile photo stays well under a second of I/O in the background.
14. Every collector of `current` re-reads the recognition and the attempt on each row change. **Skipped:** two
    collectors, reads in the background; revisit if profiling shows it.
15. The history entries flow lists every photo directory on any history row change, including tile progress of an
    auto-saved photo. **Skipped:** background work, revisit with a projection without the attempt columns if the
    history grows large.
16. `documentOf` decodes the attempt also for pins and thumbnails. **Skipped:** same cost class as 14.
17. The engines-only `recognize` overload existed for tests only, leaving the settings path untested. **Fixed:** the
    overload is removed; the tests pass settings, and a test checks that they reach the engine and the text.
18. `AttemptCodec` used bare magic numbers. **Fixed:** named constants.
19. `PhotoEntity.imageBytes` is written and never read. **Skipped:** dropping it needs a Room migration for no
    behavior change.

### Area 1, run 2

1. `PhotoImage.crop` has no pixel limit, so the note's "Whole photo" selection decodes the whole original. Checked on
   the API 37 emulator with an 8160×6120 JPEG (EXIF rotation 6): the crop took 199 MB of native memory in 0.7 s and the
   WebP encoding 1.5 s more; no crash, since bitmap pixels live in native memory since Android 8. The cost is the time,
   the memory peak and a large Anki media file. **Question** (below).
2. `pinImage` returned its image from a `withContext` that changes the dispatcher; when the caller was cancelled after
   the block counted the pin, the result was dropped and the pin never closed, so a later deletion of that photo only
   deferred its directory until the next start. **Fixed:** the pin is taken in a non-cancellable block and closed
   when the caller turns out to be cancelled. Test `aPinWhoseCallerIsCancelledMeanwhileKeepsNoDeletedPhoto` (failed
   before the fix).
3. The last close of a deleted photo's image deleted its directory on the closing thread, which is the main one for
   the overlay (`MainScope`) and the gallery export (`viewModelScope`). **Fixed:** the deletion runs on the store's I/O
   scope.
4. A failed deletion of the closed unsaved photo made `clearCurrent` throw after the current photo was already
   cleared, so the Camera screen reported a failure for a change that had happened. **Fixed:** as for replacement in
   run 1, the failure is logged and the photo goes at the next start. Test (failed before the fix).
5. `commitRecognition` wrote the recognition file before updating the row; a failed update left the file without a
   row naming it. **Fixed:** the file is deleted when the update fails. Test (failed before the fix).
6. The thumbnail is made while the store's mutex is held. **Skipped:** efficiency only; moving it out needs a re-lock
   and a check that the photo was not removed meanwhile.
7. The import conversion could run out of memory on a 16-bit float bitmap. **False alarm:** bitmap pixels are native,
   and the decoded size is bounded by the import's pixel limit.
8. A retry starts from the caller's snapshot of the document, which may be older than the stored one. **False alarm:**
   a newer committed text supersedes the retry's result and the screen shows it.
9. The mapping from recognition errors to the warning's cause had no test. **Fixed:** test
   `aFailedTileKeepsTheCauseItsWarningNames` (offline, paused, refused, server, network, device, unreadable photo,
   other).

### Area 1, run 3

1. `commitRecognition` (run 2, finding 5) deleted the new recognition file on any exception, including the
   cancellation a suspending Room call throws after it changed the row; the row then named a deleted file and the text
   showed as unreadable. **Fixed** in run 4 (below). Test failed before the fix.
2. `saveAttempt` had the same pattern for the attempt file. **Fixed** in run 4.
3. The note's photo source would keep its pin if building the crop focus threw. **False alarm:** nothing between the
   pin and the return can throw (`Box` has no edge checks, the paragraph index belongs to the same layout).
4. The model selection in `PhotoOcrEngine.open` has no unit test. **Skipped:** `DeviceOcr` and `CompositeOcr` are
   built only inside `core:ocr`; the camera instrumented tests cover the path.
5. A failed auto-save write resets the limit fields. **False alarm:** a field resets only while unfocused, and it
   commits its text when it loses focus.
6. A new recognition loads the whole current document to read its revision. **Skipped:** one decode of tens of KB per
   start, small next to the OCR.
7. `save()` reads the whole history for the latest saved time. **Skipped:** small, and the trim reads it anyway.
8. A cancelled `pinImage` still waits for the store lock. **Skipped:** only a background coroutine finishes later.
9. The document observation logs only its first failure in the process. **Skipped:** the same trade-off as the
   camera's settings observation.
10. The architecture doc named the wrong moment of the tidy. **Fixed.**
11. The take-ownership-then-check-cancellation pattern is written by hand in four places. **Skipped:** different
    resources each; a shared helper is a later refactor.

### Area 1, run 4

1–4. The run 3 fix rethrew the cancellation but left the previous recognition and attempt files until the next start
   (counted in the history size), and its test faked the exception instead of a cancelled caller. **Fixed:** the two
   index writes that name a new file run non-cancellably, so the store knows their outcome and deletes exactly the
   files no row names. Test `writesWhoseCallerIsCancelledMeanwhileStillReplaceTheOldFiles` with a fake that throws
   after a write only when its caller was cancelled, as Room does (failed before the fix).

### Area 2, found before its runs

1. `CameraSettingsViewModel.setAutoSave` wrote the preference outside its error handling, so a DataStore write error
   escaped `viewModelScope` and crashed the app. **Fixed:** the write goes through the same handling as the other
   history changes ("The history could not be changed.", the switch keeps the stored value) and nothing is saved.
   Test `aFailedWriteOfAutomaticSavingReportsItAndSavesNothing` (hung without the fix, since the event never came).

## Seen outside the scope

Defects in code the feature did not change, noted for a later full review; not fixed now.

## Questions for the owner

Collected during the runs and asked together at the end.

1. Import size limit (area 1, finding 11). Gallery import and "Share → Screenlate" copy the incoming image into the
   app's storage before checking that it is an image; another app could share a huge or endless stream and fill the
   storage. Options: a limit such as 100 MB with a "too large" message (a 50 MP JPEG is 15–25 MB, a large PNG
   screenshot 30–60 MB); or no limit, as now.
2. Camera language after turning a language off (area 1, finding 12). When the camera's language is turned off in
   the language settings, the camera switches to another active language and stores it, so turning the first one on
   again does not bring it back. Options: keep the stored choice and only show the fallback while it is off; or keep
   the current behavior.

3. Note image size (area 1, run 2, finding 1). "Add to Anki" with "Whole photo" puts the whole original into the
   note: a 50 MP photo becomes an image of several megabytes and takes about two seconds and 200 MB of memory to make.
   Options: cap the note image at about 4 MP (the size of a phone screenshot) by scaling it down; or keep the full
   resolution, as now.
4. Builds of feature branches (optional). The CI workflow builds a debug APK only for pushes to `main`, pull requests
   and manual starts, so a feature branch has no build unless one is started by hand (done for `features/camera-lookup`).
   Options: also build pushes to `features/**`; or keep starting them by hand.

## Changelog

- 2026-10-26: written after the owner's answers (areas, runs, level, fixing, questions, checks).
- 2026-10-26: area 1, run 1 findings and verdicts recorded; two questions added.
- 2026-10-27: area 1, run 2 findings and verdicts recorded, with the emulator measurement of a whole-photo crop; the
  auto-save setting crash fixed ahead of area 2; questions 3 (note image size) and 4 (feature branch builds) added.
- 2026-10-27: area 1, runs 3 and 4 recorded; area 1 committed.
