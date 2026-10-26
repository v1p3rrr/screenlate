# Camera lookup: implementation handoff

2026-10-25. Architecture investigation for the approved
[Camera Photo Lookup plan](../plans/2026-10-25-camera-lookup.md). The decision table in that plan is authoritative.
This note records existing-code facts and traps, not additional product decisions.

## Checkpoint and starting state

- Branch: `features/camera-lookup`, from `origin/languages/main` at `d1977c62`.
- Worktree: `C:/Users/vpr/.codex/worktrees/camera-lookup/Screenlate`, attached to the originating Codex chat.
- Planning commit `7cf29809` is pushed. Phase 1.1 (storage and source selection) is built; the plan's handoff names
  its commit and the next phase. The owner resumed on 2026-10-26. Phase 1.2's real camera and viewer are complete;
  the plan's handoff records checks, pending device acceptance and the next implementation steps for phase 1.3.
- This checkout has `local.properties` copied from the main checkout and all native submodules initialized (MNN from
  GitHub with `--depth 1`, hoshidicts recursively); see `build-environment.md`. Do not copy signing secrets or
  private dictionaries.
- Other existing worktrees contain modified/untracked native submodule contents. Preserve them; do not clean/reset.
- A fresh session needs no chat history: read AGENTS, status, the camera plan and this note.

## Built in phase 1.2 (2026-10-26)

`CameraViewModel` receives `PhotoDocuments`, `LookupSources`, `SavedStateHandle` and process-wide `CameraSession`.
It owns only page operations and the retake flag, not the document. Captures set busy synchronously to reject repeated
shutter taps, allocate staging, await a supplied CameraX writer, check cancellation and replace the current document.
Cleanup runs under NonCancellable; failed/cancelled captures retain the old photo. Loading failures stay distinct
from an empty store and offer retry. A viewport is accepted only for the current document id.

`CameraScreen` uses `LifecycleResumeEffect` for the source presentation, a standard scrollable `ConfirmDelete`, and
permission state checked on every resume. `LiveCamera` owns the controller; disposal detaches PreviewView and unbinds
it. Flash is hidden when the bound camera has no flash. Capture waits for CameraX's terminal callback even after
coroutine cancellation, because deleting a staging file before the camera stops writing it is unsafe.

`PhotoViewer` currently fits only. It measures actual screen pixels, decodes a sampled bitmap on IO and draws raw
pixels through the EXIF-to-view matrix. Decoding is not cancellable, so it retains ownership to completion, then
recycles an unpublished cancelled result. A published bitmap is left for collection after disposal so Compose cannot
draw a recycled bitmap. Pan/zoom, language controls and recognition are phase 1.3; new captures remain unrecognized.

Checks: 1533 JVM tests, assembly and app lint; PhotoViewerTest painted pixels for all 8 orientations on API 30 and 37;
LiveCameraTest real focus/pinch/reset on API 30 (SdkSuppress excludes API 37's broken Espresso injector); ProcessTextTest
on API 37. The test needs CAMERA granted to the debug app. Manual API 37 capture took about 6 s in the emulator;
do not treat this as a physical-camera baseline. See the test model for navigation, permission, storage and reboot runs.
The emulated cameras have no flash; actual flash behavior and real-page quality remain physical checks.

## Built in phase 1.1

- `core:photos`: `PhotoStore` keeps one current photo in `noBackupFilesDir/photos/<uuid>/image` with its recognized
  text in `recognition-<revision>` (`RecognitionCodec`: magic `SLPR`, version 1, CRC32; a damaged or unknown file
  reads as `PhotoText.Unreadable` and the image stays). Room (`photos.db`) holds the rows and the current one. Every
  change runs under one mutex; files are written to a temporary name, synced and renamed before the row commits, and
  the first change after a start tidies staging files, rows without an image and directories without a row.
  `commitRecognition` takes the revision it started from and returns null when the photo changed meanwhile. The
  `saved` column is reserved for history (phase 2): unsaved photos are deleted when replaced.
- Coordinates are oriented pixels (EXIF applied, `PhotoOrientation`); `PhotoDocument.width/height` are oriented.
  `PhotoViewport` maps photo → screen as `point × scale + offset`; `visible` is the drawn photo cut to the viewer's
  area. The Camera page must publish the viewport in the overlay's screen pixels (`maximumWindowMetrics` space).
- `LookupSources` (singleton, no Android): the Camera page calls `present()` when it comes to the foreground,
  `showNothing()` / `showPhoto()` / `moveViewport()` while showing, and `close()` when it leaves. A stale
  presentation's calls are ignored. `showPhoto` with the same document and revision keeps the generation.
- Overlay: `ScreenReads` is the only path to `capture`, app text and `prepareLens`; `TextFrame` maps aim, tolerance,
  highlights and anchors; `sourceChange` decides NONE/MOVED/DOCK/CLOSE/ATTACH against the source the open scan uses,
  so a collector that runs after a pull-out already saw the new source changes nothing. Over a photo: only
  `dictionaryReady` is required (no OCR model), the layout is cached per (document, revision), small-text bands are
  never scheduled, the engine chip uses the photo's recorded engines, and a note gets no picture (the photo crop is
  phase 1.4).
- Deferred: the spatial index for `TextLayout.hitTest` (still a linear scan) comes with the photo tiles in 1.3,
  when dense pages make it matter. Controller-level behavior (dock on leave, attach, re-aim on move) has no JVM test:
  `OverlayController` needs a device; check it with the Camera page in 1.2.

## Camera page design (phase 1.2, worked out 2026-10-25)

The navigation bar is built (see the plan's handoff). The Camera page is designed as follows; decisions are in the
plan's table, the rest are implementation choices.

- Live camera: `LifecycleCameraController` from `camera-view` in a `PreviewView` (`AndroidView`), back camera,
  `setEnabledUseCases(IMAGE_CAPTURE)`, `CAPTURE_MODE_MAXIMIZE_QUALITY`, capture `ResolutionSelector` with
  `RATIO_4_3_FALLBACK_AUTO_STRATEGY` + `HIGHEST_AVAILABLE_STRATEGY`, preview with the same aspect, `FIT_CENTER`. The
  controller brings pinch zoom, tap focus and the capture's target rotation (EXIF). `bindToLifecycle(LocalLifecycleOwner)`
  in a `DisposableEffect`, `unbind()` on dispose: leaving the tab or opening the viewer releases the camera.
  After `initializationFuture`: no back camera → "no rear camera" message; `CameraInfo.hasFlashUnit()` decides whether
  the flash icon shows (check that `ProcessCameraProvider.getCameraInfo(selector)` exists in 1.6.2, else use the
  controller's `cameraInfo` after binding).
- Zoom label: observe `controller.zoomState` (plain `Observer`, no livedata-compose dependency); show "2.0×"
  (`NumberFormat`, one decimal) above the shutter when the ratio is not 1, tap → `setZoomRatio(1f)`.
- Focus ring: observe `tapToFocusInfoState` (CameraX 1.6.2), which supplies the preview-relative tap point and
  state; draw the ring at that point and hide it 800 ms after completion. PreviewView owns the gestures.
- Capture: the VM's `capture(write: suspend (File) -> Unit)` takes `newCaptureFile()`, the page writes it with
  `takePicture(OutputFileOptions(file))` in `suspendCoroutine`, then `ensureActive` and `replaceCurrent`; any failure or
  cancellation calls `discard` (under `NonCancellable`), keeps the current photo and shows a snackbar. Log only the
  exception class and `imageCaptureError`, never paths. CameraX has no capture cancellation handle: wait for its
  terminal callback even after cancellation before discarding a file that the camera might still be writing.
- Page state from `PhotoDocuments.current` + a "new photo" flag in `SavedStateHandle`: Loading → nothing; no photo →
  live camera; photo → viewer; flag set → live camera with a close icon (and `BackHandler`) back to the photo. A
  capture clears the flag; delete (`ConfirmDelete`) calls `clearCurrent()`.
- Permission: asked automatically once per process (`CameraSession.permissionAsked`) when the live camera is needed.
  Refused: explanation and a button that requests again; after a refusal with
  `shouldShowRequestPermissionRationale == false` (`CameraSession.permissionBlocked`) the text says access is off and
  the button opens `ACTION_APPLICATION_DETAILS_SETTINGS`. Re-check on resume. The photo viewer needs no permission.
- Viewer (1.2: fit only; pan/zoom is 1.3): `onGloballyPositioned` + `positionOnScreen()` give the viewer's screen
  box; `PhotoViewport.fit(document.width, document.height, box)`; decode with `displaySampleSize(scale)` on IO; draw
  in a `Canvas` with `withTransform({ transform(imageToViewer(...)) }) { drawImage(dstSize = raw size) }`, so the
  bitmap is never rotated or copied. Report (document id, viewport) to the VM; the VM combines it with the page state
  into a `CameraView` for `CameraLookup`, ignoring a viewport of another document. `LifecycleResumeEffect` calls
  `onForeground`/`onBackground`; `onCleared` closes the presentation.
- Strings added in 14 locales: take photo, new photo, back to the photo, return to 1×, delete photo + dialog
  title/text, permission text/allow/blocked/open settings, no rear camera, capture/delete errors, photo cannot be opened.
- Camera initialization and document-load failures offer a retry. A load failure never becomes an empty store that
  can silently be replaced. A viewer publishes its viewport only after decoding succeeds; stale-document callbacks
  cannot reattach an old image. Decoded, published bitmaps are released by collection after disposal rather than
  recycled while Compose might still draw them; an unpublished cancelled decode is recycled explicitly.

## Existing code to follow

All paths below are relative to the repository. They were inspected on the language branch before implementation.

### Bubble and source lifetime

`overlay/.../OverlayController.kt` owns DOCKED/DRAGGING/FLOATING state. `startDrag(fromDock = true)` calls
`startScan`; the delayed single-tap runnable also calls it. `BubbleTouchListener` calls `prepareScan` already on
ACTION_DOWN; it can prepare the cloud connection before a scan starts. Guard all of these, not just `startScan`.

`dock`, `onConfigurationChanged`, hiding the bubble and stopping the service can close/reset a scan. `resetScan`
increments `scanId`, cancels recognition/refinement/lookup, clears `TextLayout` and releases `SharedScreenshot`.
The photo document and saved recognition must survive these operations. Its persistent owner cannot be the service
or an activity/view model. The source session still needs explicit foreground/viewer activation and invalidation.

`onActiveLanguage`/`takeActiveLanguage` currently use the global profile; `scanLanguage` also controls lookup
settings, appearance and model warming. Photo lookup must use its committed recognition language, not merely
the newly selected camera language. Close/rebind note, audio and translation state on source/language changes.

`onForegroundApp` sees packages, not app routes. An app-owned presentation contract must tell the overlay whether
the actual photo viewer is visible. Camera preview/loading must be inactive, never a Screen-source fallback.
Existing hidden-app/service visibility settings still apply to the same bubble.

### Coordinates and layout

`core/ocr/.../OcrModels.kt`: `OcrPage` stores image dimensions, paragraphs, lines, words, separators, character boxes,
vertical/RTL flags and optional paragraph engine provenance. These types currently have no serialization annotations.
Persist the complete information using a versioned codec; do not reduce it to strings and word rectangles.

`TextLayout` already performs language-aware character splitting and hit testing; `ReadingOrder` handles column order.
Its current hit test scans characters. Preserve overlap/tolerance behavior when adding an index. Photo layout stays
in original-image coordinates. The existing overlay assumes screen coordinates in `onAim`, highlights, popup anchors
and crop focus, so an invertible viewport adapter is required in both directions, including screen-pixel tolerance.

### Recognition, tiles and resources

`core/ocr/.../CompositeOcr.kt` provides `recognize(Bitmap, Language, OcrOptions, focus)` and draft/final updates.
It implements screen focus bands and deferred full-image work. A photograph needs a separate coordinated pass:
the owner's choice is to publish only when the whole attempt has terminated, not incremental tile results.

`LensOcrEngine` currently downsizes its input to a maximum side of 1500 and JPEG quality 85. Pass original-resolution
crops no larger than that limit; do not first shrink the whole photo. Split both axes with overlap. Cloud requests
are bounded to two simultaneously. The existing screen small-text option does not govern photo tiling.

`OcrPage.withMissingFrom` discards lines that overlap existing lines; it is unsuitable for stitching tiled words and
paragraphs. Preserve repeated real text, prefer complete boundary words, and reconstruct line/paragraph reading order
for lookup and sentence extraction. Tests must include horizontal and vertical seams and words near four-tile corners.

`ScreenBands` supplies overlapping horizontal bands for screen small-text refinement, not the complete 2D photo grid.
Aim-driven `scheduleBandAt`/`refineBand` must not run on a photo source.

Paddle and Tesseract use mutexes; model holds/deferred release prevent deletion during recognition. ML Kit tasks can
continue after coroutine cancellation. `OcrEngine.recognize` must return/throw only after the engine stops reading the
bitmap. Follow the ownership/cancellation details in `ocr-engines.md`; a cancelled worker result may need disposal.
Large inputs raise native memory substantially; test repeated captures, cancellation and idle trimming.

### Readiness and shared settings

`overlay/.../install/LanguageInstallation.kt` is shared by Home, Search and the bubble. Its current readiness logic
uses `TextSource.APP_TEXT_ONLY` to bypass OCR requirements. Photos require OCR regardless of that screen text-source
setting. Distinguish new-recognition readiness from searching cached text, which must not require a removed OCR model.
Keep `core:photos` from depending on overlay settings: pass an explicit shared OCR settings snapshot inward.

### Anki image and note identity

`OverlayController.noteSource` captures shown text/position, `scanId`, an optional shared screenshot and crop focus.
`PopupNotes` pins the note language; keep that guarantee. Extend source identity to document and recognition revision.

`CapturedScreen`/`SharedScreenshot` represent a bitmap placed at screen left/top, with main-thread reference counting.
`CropView` draws it at natural size and crops by subtracting that screen origin. A large photograph cannot be passed
through this unchanged: fit the editor's view, inverse-map the selection and crop the original image. Preserve the
screen adapter's behavior. Anki attachment cropping does not enable the deferred editing of the photo document itself.

### Navigation, storage and localization

`app/.../navigation/ScreenlateNavHost.kt` currently has one NavHost without a bottom bar. Preserve first-run routes,
deep-link requests and `ProcessTextActivity`'s standalone search. Existing theme notes warn that branching the Compose
content tree can recreate navigation and lose state. Icon overflow must use actual available width and accessible labels.

`app/src/main/res/xml/data_extraction_rules.xml` excludes existing app data from Android backup/transfer. Photo files,
Room metadata and preferences must not enter the app's own backup either. Settings reset must preserve photo documents;
explicit deletion is a separate action. Room changes stay in `core:photos`, not the dictionary registry database.

The project has 14 interface locales, Hilt, KSP and shared Gradle convention plugins. Reuse those; no new annotation
processor. CameraX was checked in the official Android release documentation during planning (stable 1.6.2 then);
verify compatibility when adding dependencies, and add its attribution to NOTICE.

## Verification protocol

Read `ai/test-model.md`: new cases start N, runs record coverage and outcomes, and relevant E regressions must run.
Use the shared emulator lock before device work. No `am force-stop`, no owner's Anki collection, no account sync,
and no app-wide connected test run that uninstalls the working app. Use module/class-specific instrumentation.
UI dumps can temporarily unbind/rebind accessibility and dock the bubble; use screenshots for overlay gesture checks.

Run targeted logic tests after each implementation milestone, then assembleDebug/testDebugUnitTest/lintDebug as required.
Page tests are required for shared popup/note changes. Physical camera quality and flash need real-device evidence;
record them as pending rather than claiming an emulator establishes them.

## Durable handoff protocol

At each checkpoint, update the plan's Resume / handoff and `ai/status.md`: exact completed scope, last commit,
uncommitted files/work, test commands/results, blockers and the next concrete action. Update this note when new
architectural facts are discovered. Commit and push coherent milestones. When paused by the owner, stop after the
current coherent milestone and leave an explicit paused marker; do not start the next milestone.
