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

Updated after every step so the work survives a context reset: areas 1–3 are reviewed (runs 1–4) and committed;
area 4, run 1 is next. The API 37 emulator runs under the shared lock.

## Progress

| # | Area | Run 1 | Run 2 | Run 3 | Run 4 | Emulator | Commit |
|---|---|---|---|---|---|---|---|
| 1 | Photo store | 19 findings: 9 fixed, 1 false alarm, 7 skipped, 2 questions | 9 findings: 5 fixed, 2 false alarms, 1 skipped, 1 question | 11 findings: 3 fixed, 2 false alarms, 6 skipped | 4 findings: 4 fixed (one change) | `core:photos` instrumented tests pass on API 37 | done |
| 2 | Camera screen | 1 fixed ahead of the runs; 8 findings: 6 fixed, 1 false alarm, 1 skipped | 13 findings (2 found on the emulator): 7 fixed, 5 skipped, 1 question | 3 findings: 3 fixed | 10 findings: 4 fixed, 6 skipped | API 37 by hand: each fix of runs 1–4 | done |
| 3 | Overlay and Anki integration | 14 findings: 10 fixed, 2 skipped, 2 questions | 8 findings (2 found on the emulator): 7 fixed, 1 false alarm; 1 more defect found while fixing, fixed | 7 findings: 5 fixed, 2 skipped | 3 findings: 3 fixed | API 37 by hand: runs 1–4 | done |
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

### Area 2, run 1

1. `MainActivity` (the exported launcher) took an `open=shared-image` request from any app and imported its clip-data
   link without the share screen's check, so a sender could make the app copy its own private files (a `file:` link
   needs no grant) and recognize them. Reproduced on the API 37 emulator with an in-app probe (an instrumented test
   that starts `MainActivity`, since `am start` cannot set clip data): the private file was imported. **Fixed:** the
   link is checked again as the share screen does; the same probe then imports nothing.
2. The share screen's own-provider check compared `Uri.getAuthority()`, which keeps a user prefix: `content://0@<app>.anki.media/…`
   passed as another app's link and reached the app's own FileProvider. Reproduced with `am start` of the share screen
   (the app's cached note image opened and was recognized). **Fixed:** the provider is compared without the prefix, as
   the system resolves it. Test `aUserBeforeTheProviderDoesNotHideTheApp` (failed before the fix); the emulator then
   showed nothing imported.
3. A history tap decided whether to ask about the unsaved current photo from a snapshot that is empty before the
   store's first value and after a read error, so the unsaved photo could be replaced without the question.
   **Fixed:** the tap reads the store's current photo; a read error reports "could not open" and opens nothing. Tests
   `aTapBeforeTheCurrentPhotoIsReadStillAsksAboutIt`, `aCurrentPhotoThatCannotBeReadOpensNothing` (failed before).
4. Two quick taps on history photos opened both. **Fixed:** the page is busy from the tap on. Test
   `aSecondTapWhileAPhotoOpensIsIgnored` (failed before).
5. Recognize and Retry read the language's readiness in a scope without an exception handler, so a settings or
   database read error ended the app. **Fixed:** the scope logs the error and the button does nothing.
6. The viewer's detail patch catches only `IOException`, while `decodeRegion` throws `IllegalArgumentException` for a
   region outside the image. **False alarm:** the viewport keeps the visible region inside the image, and the import
   rejects files whose region decoder reports another size.
7. A camera error after binding (disabled by policy, a fatal device error) leaves a black preview instead of "Camera
   unavailable". **Skipped:** rare; CameraX reopens after recoverable errors, and leaving and returning to the tab
   recreates the camera.
8. The History item of the ⋮ menu stayed enabled during a capture, while the round History button is disabled; a
   history photo opened meanwhile was replaced by the finishing capture. **Fixed:** the item is disabled as the button.

### Area 2, run 2

1. A page request during the first run (the selection-menu search's link to Dictionaries, Anki settings, OCR models)
   switched to the Settings tab, and the tab switch pops the whole stack, so the first run was abandoned and the bar
   shown. Reproduced on the API 37 emulator. **Fixed:** until the first run ends at Home, requests open their page over
   it and the bar stays hidden. Instrumented test `FirstRunRequestTest` (failed before the fix, passes on API 37).
2. With automatic saving on, a photo whose automatic save failed is not in the history, yet closing it, importing
   another image or opening a history photo deleted it without the question. **Fixed:** the question depends only on
   the photo being saved. Tests `aPhotoThatAutomaticSavingFailedToSaveAsksBeforeAnImport`,
   `aPhotoThatAutomaticSavingFailedToSaveStillAsks` (failed before).
3. The top bar's ⋮ menu covers the photo but did not hide it from the bubble. **Fixed:** the menu reports opening and
   closing, and the page is obscured while it is open. Emulator: before, a word of the photo was found under the open
   menu; after, the bubble finds nothing there.
4. The Camera page's links (Manage languages, the readiness dialog's Open) open their page in the Settings tab, so
   Back leads to the Settings list. **Question** (below).
5. A typed photo or size limit was stored only when the field lost focus, which does not happen when the page closes
   with Back. Reproduced on the emulator (the limit stayed 0). **Fixed:** leaving the page stores the typed limit first;
   a limit that would delete photos asks and the page stays. Tests in `CameraSettingsViewModelTest`; on the emulator
   Back and the arrow both store the typed value.
6. A dismissed first permission request counts as a blocked permission, so the page offers the system settings
   instead of asking again. **Skipped:** Android reports both the same way (denied, no rationale); the settings button
   still works.
7. A history photo whose opening ends while the app is paused is opened, but the history page stays. **Skipped:** rare,
   and the opened photo is shown when the user goes back.
8. A failed history read looks like an empty history. **Skipped:** a database read error; a separate message needs a
   new string in 14 locales for a case not seen.
9. Usage did not say that an image shared during the first-launch setup waits until the Camera tab is opened.
   **Fixed** in `docs/usage.md` and `docs/architecture.md`.
10. The recognition controller keeps its readiness observations for the process once the Camera page has opened.
    **Skipped:** a few flows that react only to dictionary and model changes.
11. The tab logic has no automated test. **Skipped** beyond `FirstRunRequestTest`; the test model's navigation cases
    cover the rest.
12. Found on the emulator while checking 3: the page's obscured flag was read inside `SideEffect`, which composition
    does not observe, so neither the language menu nor the ⋮ menu ever hid the photo (a word of the photo was found
    under the language menu). **Fixed:** the flag is computed in composition and passed in. Emulator: no lookup under
    either menu.
13. Found in the emulator log: the live camera set the flash mode on every composition, and the blurred fill
    recomposes it as often as every 100 ms; each assignment makes CameraX send the camera a new request (1274
    `setFlashAsync` lines in 18 minutes of testing). **Fixed:** the mode is set only when it or the controller
    changes; no such line in 10 s of live camera after.

### Area 2, run 3

Over the fixes of runs 1 and 2 (`git diff HEAD`, uncommitted) and their callers. Checked on the API 37 emulator and
found fine: a quick double Back with a typed limit closes one page (`fromResumed` drops the second), and stores it.

1. Leaving Camera settings with a typed limit that would delete photos asks; Cancel left the typed value in the field,
   which keeps the focus, so every Back asked again and the page could be left without deleting only by retyping the
   old limit. Reproduced on the emulator. **Fixed:** a reset (Cancel or a failed change) shows the stored limit again
   also in the focused field and drops the typed one; on the emulator Cancel shows 0 and the next Back leaves.
   Instrumented `LimitFieldTest` (API 30, as the other Compose tests).
2. The ⋮ menu still hid Save to history whenever automatic saving was on, also for a photo whose automatic save
   failed (the rest of run 2's finding 2). **Fixed:** the item depends only on the photo being outside the history; on
   the emulator it shows for a manual capture and stays hidden for an automatically saved one. The view model's
   `autoSave` flow, now unread, is removed.
3. Usage said the unsaved-photo question comes only with manual saving. **Fixed** in `docs/usage.md` and
   `docs/architecture.md`.

### Area 2, run 4

Over the fixes of run 3 and the code around them (Camera settings' limit fields and leaving the page, the ⋮ Save to
history item, the docs).

1. A typed limit was lost when the page closed other than by Back or the arrow, such as by tapping the current tab:
   the field stores its limit when it loses focus, but the store ran in the page's view model, which is cleared at
   once. Reproduced on the emulator (10 typed, 0 stored). **Fixed:** the store goes on after the model is cleared; a
   limit that would delete photos is dropped then, since no page can ask. Test
   `aLimitStoredAsThePageClosesOutlivesTheModel` (failed before); on the emulator 10 is stored. Switching to another
   tab was fine before: the page's model stays, and the question shows on return.
2. Delete in the question asked on leaving the page stored the limit, but the page stayed and needed another Back.
   **Fixed:** the page closes after Delete and stays after Cancel. Tests
   `closingWithALimitThatDeletesPhotosAsksAndClosesAfterDelete` (failed before) and
   `closingWithALimitThatDeletesPhotosStaysAfterCancel`; checked on the emulator.
3. Since run 3 a reset clears the focused field too, and every failed change counted as one, so a failed auto-save
   switch or Clear history dropped the limit being typed. **Fixed:** only a limit that is not taken resets the
   fields. Test assertion in `aFailedWriteOfAutomaticSavingReportsItAndSavesNothing` (failed before).
4. The page and the fields build the whole pair of limits from the page's copy of the stored ones, so a store of the
   other field still running could be overwritten. **Skipped:** the store takes milliseconds, less than typing a
   digit in the other field, and leaving does nothing while a question is open.
5. With automatic saving, Save to history is enabled for a moment after a capture, until the store reports the photo
   saved. **Skipped:** saving twice is harmless (the store skips a saved photo), and the item is gone a moment later.
6. A close that arrives while the app is paused is dropped, and the page stays with the limit stored. **Skipped:**
   needs Home within the milliseconds of the store; as run 2's finding 7.
7. The field kept a counter to tell a reset from a new stored value. **Fixed:** two effects, one per key.
8. The typed limit is kept in the field, in the page and, while asking, in the view model; a draft held by the view
   model would cover every way out. **Skipped:** with finding 1 fixed, every way out works; the rewrite brings
   nothing more.
9. The page's wiring of Back and the arrow has no automated test. **Skipped:** the view model and the field have
   tests, and CAM39 checks the page by hand.
10. Each keystroke recomposes the page. **Skipped:** a small page; no visible cost.

### Area 3, run 1

1. Leaving the photo while a finger dragged the bubble docked it under the finger: the rest of the touch dragged a
   docked bubble, and on release it stayed out of the dock, translucent, with no aim marker and no source, so the
   next photo did not attach. Reproduced on the emulator (Settings opened over the Camera page during the drag).
   **Fixed:** the scan closes at once and the bubble docks when the touch ends; back over a photo before the release,
   it does not dock. Checked on the emulator both ways.
2. The crop frame of a photo note was padded by 48 dp divided by the viewer's current scale, while the editor always
   shows the whole photo: about 48 dp around the paragraph at the fitted view, about 6 dp at 8× zoom. **Fixed:** the
   padding uses the fitted scale (`PhotoViewport.fitScale`), the same whatever the zoom. Test
   `the fitted scale stays whatever the zoom`; the zoom itself cannot be tried through adb (no pinch).
3. The popup is not placed again when the photo is zoomed or panned under the same word; only the highlight follows.
   **Question 6.**
4. Photo notes fill `{document-title}` with the app's own name. **Question 7.**
5. The screen crop moved to `ScreenNoteImage.crop`, while `CropViewTest` still tested the old copy. **Fixed:**
   `ScreenNoteImageTest` (the whole screenshot copied, a frame in screen coordinates of a screenshot below the top, a
   frame over the edge, a single release); `CropViewTest` removed with the dead code.
6. `CropView.cropped()` and the image offset parameters were dead. **Fixed:** removed; the KDoc speaks of an image.
7. Every move of the photo set the bubble's glyph and language again, redrawing the bubble and parsing a locale per
   frame. **Fixed:** set only when they change.
8. `CropEditor.edit` had lost its KDoc (who owns the bitmap, what null means, the focus coordinates) and its
   comments. **Fixed.**
9. The focus went Box → RectF → Box, and the paragraph lookup was written twice. **Fixed:** `NoteContext.focus` is a
   `Box`, built by one local helper.
10. `onAddNote` held the image under two names. **Fixed.**
11. Several statements per line in `NoteImage.kt`, `CropPlacement.kt` and the photo branch of `noteSource`.
    **Fixed.**
12. Fully qualified names in `CropEditorTest` and the import order in the service. **Fixed.**
13. The last photo's layout stays in memory after the Camera page is left. **Skipped:** it is what the bubble uses
    when the page is shown again, and the next photo replaces it.
14. No tests for `PhotoNoteImage` or for the bubble's reaction to source changes. **Skipped in part:**
    `ScreenNoteImageTest` and a new `CropEditorTest` case (Whole photo, then Frame) cover the editor's side;
    `PhotoNoteImage` needs a `PhotoImage`, whose constructor is internal to `core:photos`; the bubble's reaction was
    checked on the emulator.

On the emulator (API 37): the crop editor of a photo note fits the whole photo with the frame around the paragraph;
Whole photo selects all of it, Frame goes back to the paragraph, and the note is added with the picture (the
emulator's local AnkiDroid collection). `CropEditorTest` and `ScreenNoteImageTest` pass on API 37 (11 tests).

### Area 3, run 2

1. Open Anki settings under the grey ➕ opened the Anki settings of the active language, while the reason the popup
   shows is about the scan's language, over a photo the photo's. Seen on the emulator. **Fixed:** the overlay passes
   the language (`OverlayIntents.openAnkiSettings`) and the app opens `AnkiRoute(language)`.
2. The installation link of a photo whose dictionaries are missing opened the dictionaries of the active language.
   **Fixed:** `openInstallation(context, language)`; `DictionariesRoute(language)`, read by `DictionariesViewModel`.
3. The note picture went to WebP without checking the result. WebP takes at most 16383 px per side, and an imported
   long image keeps its size (a 1080 × 22000 screenshot is under the 24 MP limit), so Whole photo gave the note an
   empty picture file and no error. **Fixed:** the picture is scaled down to fit and a failed encoding fails the note.
   Tests `WebpSizeTest`; `WebpLimitTest` (instrumented) confirms the platform refuses a side of 16384 px.
4. A photo with no text said "No text found on the screen." Seen on the emulator. **Fixed:** "No text found in the
   photo." in the 14 languages.
5. A photo deleted between ➕ and taking its image failed the note with the English "Photo image unavailable" in the
   toast, even when the source had changed and the note should drop silently. **Fixed:** the source is checked first;
   otherwise the note fails with "The photo is no longer available." (14 languages).
6. Every scan over a photo set the active language before the photo's. **False alarm:** both happen in one main-thread
   task and the collectors run on the non-immediate main dispatcher, so they only see the photo's language.
7. Every move of the photo mapped all lines to the screen, though they show only during a flash. **Fixed:** mapped
   only while a flash shows (`LayerView.linesShown`).
8. `LanguageInstallation.observe` had no KDoc for `imageSource` and `engines`. **Fixed.**
9. Found while fixing 1 and 2: a request opened its page with `launchSingleTop`, so with the Anki or Dictionaries page
   already on top of the Settings tab its view model kept the old language. **Fixed:** the same page on top is
   replaced.

On the emulator (API 37): a Japanese photo with Slovenian active and the AnkiDroid permission revoked → grey ➕ →
Open Anki settings → the Japanese Anki settings (permission granted again after). App intents with a language open
the Anki settings and the dictionaries on it, a second request with another language replaces the open page, and one
without a language opens the active one. A room photo with no text says "No text found in the photo.".
`WebpLimitTest`, `CropEditorTest` and `ScreenNoteImageTest` pass on API 37 (12 tests).

### Area 3, run 3

1. The Camera page's "cannot recognize" dialog opened the dictionaries of the active language, not of the language it
   names (run 2's finding 2 fixed only the popup's link). **Fixed:** `onOpenDictionaries(language)` →
   `DictionariesRoute(language.code)`.
2. `dockOnRelease` survived a touch whose window was removed (the bubble hidden in an excluded app during the drag), so
   the next pull-out docked on release. **Fixed:** cleared with `touching` in `detachWindows`.
3. A picture that cannot be encoded failed the note with an English message in the localized toast. **Fixed:** "Saving
   the picture failed." in the 14 languages.
4. No automated test for the language of the pages the app opens on request, or for the replacement of the same page.
   **Skipped:** wiring between an intent, the navigation and a view model, with nothing to extract; checked by hand
   (CAM48).
5. The photo frame's padding uses the viewer's fitted scale, while the crop editor fits the photo into its own area,
   so the margin there is 48 dp only as far as the two areas agree (somewhat more for a tall photo, whose editor area
   is taller). **Skipped:** the margin is meant to be approximate; padding in the editor would need the paragraph's
   lines instead of a frame.
6. `LanguagePageRequest` repeated `OcrModelsRequest`, the requests were built by copied code, and `openInstallation`
   had an unused nullable default. **Fixed:** one request type with `next(intent)`; the language is required.
7. `docs/development.md` did not list the WebP tests. **Fixed.**

On the emulator (API 37): the new build opens a language's on-device models from the app intent (the Korean models).
The Camera dialog was not reproduced: every language turned on there has its dictionaries; the route it now uses was
checked through the app intent in run 2. Reproduced in run 4.

### Area 3, run 4

1. The Camera page's "Can't recognize" dialog opening the dictionaries of its language was in no test case: CAM48
   named only the popup's links, CAM32 only the dialog's title. **Fixed:** added to CAM48.
2. The navigation's KDocs of the Anki settings and dictionaries requests did not say that the page opens on the
   request's language and replaces the same page on top; the activity's dictionaries request had no KDoc.
   **Fixed.**
3. The request for a language's on-device models was checked for a language twice, in the activity and in the
   navigation. **Fixed:** only the navigation checks it, as for the other two requests.

On the emulator (API 37): with the Dutch dictionary's folder moved aside, Recognize on a photo set to Dutch shows
"Can't recognize Dutch"; Installation status opens the Dutch dictionaries in place of the Dictionaries page that
showed Japanese, and Back leaves it (the folder was put back, Dutch recognizes again). With the new build, a models
request without a language leaves the app where it is, one with Korean opens the Korean models.

## Seen outside the scope

Defects in code the feature did not change, noted for a later full review; not fixed now.

- A double tap on the bubble switches the aim mode but keeps the aim point of the old mode, so lookups go on at the
  old point until the bubble moves (`OverlayController.toggleAimMode`). The photo's moves keep that point too.
- A screen size change during a touch docks the bubble under the finger (`onConfigurationChanged` calls `dock()`),
  as area 3's finding 1 did before its fix.
- The Search page and the selection menu's search open the Anki settings and the dictionaries of the active language,
  not of the language being searched (`SearchScreen`, `ProcessTextActivity`); the requests can now carry a language.

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
5. Camera links and the tabs (area 2, run 2, finding 4). On the Camera tab, Manage languages in the language menu
   and the Open button of the "not ready" dialog open the language, dictionary or model page in the Settings tab, so
   Back from it leads to the Settings list and the camera needs another tap on its tab. Camera settings, by contrast,
   opens inside the Camera tab. Options: open these pages inside the Camera tab too, so Back returns to the camera; or
   keep them in the Settings tab, where these pages live.
6. Popup and a moving photo (area 3, finding 3). When the bubble rests on a word of a photo and the photo is zoomed or
   panned, the word's highlight follows the word, but the popup stays where it was and can cover the enlarged word.
   Options: place the popup again on each move of the photo, so it follows the word; or keep it in place, as now, so
   it does not move while the photo is zoomed.
7. Document title of photo notes (area 3, finding 4). The `{document-title}` field marker gets the name of the app in
   the foreground; for a photo that is Screenlate itself. Options: leave it empty for photos; a fixed word such as
   "Camera"; the photo's date and time; or the app's name, as now.

## Changelog

- 2026-10-26: written after the owner's answers (areas, runs, level, fixing, questions, checks).
- 2026-10-26: area 1, run 1 findings and verdicts recorded; two questions added.
- 2026-10-27: area 1, run 2 findings and verdicts recorded, with the emulator measurement of a whole-photo crop; the
  auto-save setting crash fixed ahead of area 2; questions 3 (note image size) and 4 (feature branch builds) added.
- 2026-10-27: area 1, runs 3 and 4 recorded; area 1 committed.
- 2026-10-27: area 2, run 1 recorded, with the emulator reproduction of the two shared-link findings.
- 2026-10-27: area 2, run 2 recorded, with two more defects found on the emulator (menus never hid the photo, a flash
  request on every fill frame); question 5 (Camera links and the tabs) added.
- 2026-10-27: area 2, run 3 recorded.
- 2026-10-27: area 2, run 4 recorded, with the emulator reproduction of a typed limit lost on tapping the current tab;
  area 2 committed.
- 2026-10-27: area 3, run 1 recorded, with the emulator reproduction of the bubble docked during a drag; questions 6
  (popup and a moving photo) and 7 (document title of photo notes) added; two defects seen on the emulator kept for
  run 2; two defects outside the scope noted.
- 2026-10-27: area 3, run 2 recorded, with the emulator checks of the language of the Anki settings and dictionaries
  opened from the popup and of the photo's no-text message.
- 2026-10-27: area 3, run 3 recorded; one more defect outside the scope noted (search opens the active language's
  pages).
- 2026-10-27: area 3, run 4 recorded, with the emulator check of the Camera dialog's dictionaries link; area 3
  committed.
