# Camera Photo Lookup

Approved by the owner on 2026-10-25. Branch: `features/camera-lookup`, based on
`origin/languages/main` at `d1977c62`. This plan and all repository documents are English; talk to the owner in Russian.
Implementation stays on the feature branch. Merging into `languages/main` or `main` is a separate step.

## Resume / handoff

Read this section, `ai/status.md`, the decision table below, and the notes named under Architecture before editing.

- Checkout: `C:/Users/vpr/.codex/worktrees/camera-lookup/Screenlate` (managed worktree attached to the Codex chat).
- Branch: `features/camera-lookup`, tracking `origin/features/camera-lookup`.
- Phases 0, 1.1, 1.2, 1.3 and 1.4 are implemented. Last pushed implementation: `e8951271`
  ("Add original-photo note images and explicit gallery export"); final verification/docs commit follows it.
  Earlier OCR implementation `9484c556`, handoff `66b66d85`, foundation `01d24c0e`. Feature branch only; no merges.
- Phase 1.4: PhotoDocuments.pinImage/PhotoImage retain original bytes through replacement/clear; NoteImage has screen
  and original-photo adapters; CropPlacement fits photos above controls and maps the selected frame to original pixels;
  crop output honors EXIF. Notes capture recognition/text/language and reject late photo contexts; cancelling a scan
  also invalidates a preview still decoding. PhotoGallery writes original bytes with MediaStore pending publication
  and rollback; Camera has an explicit gallery action. No OCR sidecar, general storage permission or backup addition.
- Required checks PASS: root `assembleDebug testDebugUnitTest lintDebug :overlay:assembleDebugAndroidTest
  :core:photos:assembleDebugAndroidTest --max-workers=1`, 1585 JVM tests, 0 failures; page scripts 146/146.
  App/test APK assembly passes. API 37 and API 30: PhotoImageTest/PhotoGalleryDeviceTest 3/3 and
  CropEditorTest/CropViewTest 8/8 each (all eight orientations, fractional original crops, sampled preview, JPEG/PNG
  byte identity and MediaStore visibility, screenshot regression, frame mapping and preview/cancellation races).
- Real API 37 scenario PASS: generated Japanese 2800×2400 page recognized by Device; dictionary popup while app language
  Slovenian; long-press Add, cancel/reopen, Whole photo/Frame and corner resize; local Anki note stores Japanese sentence
  and original 1541×1257 crop. PhotoAnkiFixtureTest prepare/verify/restore passed. Gallery action success; original bytes
  match, gallery copy survives deleting the private document. No screen capture/OCR caused by these image actions.
- Earlier 1.3 checks remain valid: 7/7 region/migration checks, four 1500×1500 cloud tiles, terminal publication while
  off Camera, stationary-bubble pan/zoom, independent pending language/settings, exactly-one-tile retry backgrounded,
  capture auto-recognition and cold restart without automatic OCR. API 30 viewer/live camera tests 3/3.
- Next concrete action is phase 1.5 verification. Open `ai/test-model.md` CAM/NAV/ANK/BUB/OCR/LANG cases and run the
  remaining manual variations: CAM3 toolbar/letterbox/clipped highlights; all CAM4 inactive/loading variants;
  CAM6 screen→photo transition; CAM7 hold-menu; NAV3 setup/badge/nested/deep-link variations. Re-run relevant E cases
  for ordinary screen lookup/Anki, note audio/translation and settings/languages after the image-source refactor.
  Log exact evidence, promote only checked N cases, and keep physical-device acceptance explicitly pending.
- Physical sharpness, focus, flash, capture delay and repeated-capture/memory/drag baselines still need a phone;
  emulator evidence does not establish them. Do not claim full phase 1 acceptance or merge before recording the
  outstanding checks. Phase 2 history/settings/limits and phase 3 picker have not started; ask only genuinely new
  product choices that their decision table does not settle.
- Cleanup: temporary Japanese Anki app settings restored, shared OCR remains Both, Camera language Japanese.
  Only the emulator's local unsynced Screenlate Photo Test deck/model/note was used; no account/sync/AnkiWeb operations.
  API 37's generated private photo was deleted for the independence test; its ordinary gallery copy remains. Main app
  and other data stay installed. Auxiliary APKs removed, both owned emulators shut down and shared lock released
  at the final checkpoint. No new worktree or submodule changes. Read the notes before editing image ownership.
- Phase 1.2 implementation:
  - `navigation/ScreenlateNavHost.kt`: one Scaffold with a `NavigationBar` (Home, Search, Camera, Settings) on every
    page except the first run and the debug image viewer. Sub-pages stay root-level routes; a tab switch pops
    everything above the root graph with `saveState` and navigates to the tab's root with `restoreState`, so each tab
    keeps its pages. The selected tab is a `rememberSaveable` (the NavController's full back stack is restricted
    API). Back on a tab's root goes Home (`BackHandler`); tapping the open tab pops to its root; overlay requests
    (Anki settings, Dictionaries, OCR models) open in the Settings tab; leaving the Settings list for another tab with
    the background badge marks the tip seen (moved from `SettingsScreen`). Search and Settings lost their back arrows
    (`onBack` nullable in `SearchScreen` and `SettingsScaffold`; `ProcessTextActivity` keeps it).
  - Home lost the settings icon, the Search card and the Settings button; `home_search_title`/`home_search_open`
    removed from all locales. New strings in 14 locales: `nav_home`, `nav_search`, `camera_title`,
    `camera_flash_off/auto/on`.
  - CameraX 1.6.2 (`camera-camera2`, `camera-lifecycle`, `camera-view`) in the catalog and `app`; CAMERA permission
    and optional `android.hardware.camera.any` in the manifest; NOTICE names CameraX. Icons `ic_home`,
    `ic_photo_camera`, `ic_flash_off/auto/on` (Material Icons paths).
  - `CameraViewModel` owns capture and temporary retake state; `CameraScreen` handles permission explanations,
    upper actions, delete confirmation and lifecycle. `LiveCamera` binds a quality-prioritized CameraX controller,
    handles focus/zoom and waits for a terminal capture callback before file disposal. `PhotoViewer` fits a sampled
    bitmap through the EXIF matrix and publishes its viewport only after decoding. `CameraLookup` attaches only
    while resumed; live/loading/unrecognized/failed views remain NoText, with no screen fallback.
  - 16 new strings in all 14 locales. 15 JVM tests, `PhotoViewerTest` and `LiveCameraTest`. Human docs, module map,
    this handoff and `ai/test-model.md` updated. At the 1.2 checkpoint capture did not start OCR; phase 1.3 above supersedes that state.
- After 1.4: finish the remaining phase 1.5 verification above, then internal history is phase 2. Original-photo
  Anki attachments and explicit gallery export are implemented.
- First read: `ai/notes/camera-lookup.md` preserves the architecture investigation, what phase 1.1 built and the
  integration pitfalls.
- Checks for phase 1.1: `assembleDebug testDebugUnitTest lintDebug` pass (`--max-workers=1`; with 2 workers the first
  native build in this checkout ran out of machine memory, and `:dictionary:engine-hoshidicts:assembleDebug` alone
  finished it). Unit tests: 1518 in 9 modules, none failing, among them `core:photos` 38 and `overlay.source` 19. Lint:
  no new findings (platform `ExifInterface` suppressed in `PhotoStore.kt`, minSdk 30). Instrumented
  `PhotoDatabaseTest` 4/4 on Pixel_10_Pro. Emulator regression of the screen path (BUB1-BUB3, hold menu, dock) passed;
  see the test model's run log. No page tests (no shared popup or note change); no phone checks.
- Environment: read `ai/notes/build-environment.md`; JDK 21 at `C:/Program Files/Java/jdk-21`, SDK at `D:/Android/Sdk`.
  New worktrees need a local SDK configuration and initialized native submodules before a full build.
- Existing checkouts and their modified/untracked submodule contents belong to other work. Do not reset or clean them.
- Owner requirement: keep this handoff accurate during work, not just at the end. At each checkpoint record finished
  steps, the last commit (or a descriptive commit subject before committing), uncommitted work, exact checks and
  outcomes, blockers, and the next concrete action. Commit and push coherent milestones so a fresh session can resume.
- No implementation session may depend on this chat or Claude's local memory for the feature's decisions.

## 1. Scope and agreed behavior

The feature provides a persistent photograph that the existing floating bubble inspects using stored OCR coordinates.

| Topic | Decision |
|---|---|
| Main navigation | Bottom navigation: Home, Search, Camera, Settings. Settings is rightmost. |
| Navigation bar | Visible on every page, nested ones included; hidden only in the first run and the debug image viewer. Each tab keeps its own pages and scroll. Back on a tab's first page goes to Home; back on Home leaves the app; Search and Settings lose their back arrows. Tapping the open tab returns to its first page. |
| Home after the tabs | The settings icon, the Search card and the Settings button leave Home; the background-work badge moves to the Settings tab. |
| Camera page | Show the current photograph when present; otherwise open the live camera. |
| Camera layout | The frame sits under the ordinary top bar (title, icons on the right); a round shutter button at the bottom of the frame. The photo viewer uses the same top bar. |
| New photo over a photo | A camera icon in the viewer's top bar opens the live camera; its close icon and system back return to the photo unchanged. A capture replaces the photo and shows the new one. |
| Flash | Off/Auto/On as one top-bar icon that cycles. Off at every app start; the choice lasts while the app process lives. |
| Camera permission | Asked when the Camera page first opens the live camera. Denied: an explanation in place of the frame with a button that asks again in the app while the system allows it, otherwise opens the app's system settings. The current photo is shown without the permission. |
| Zoom | Pinch zoom; while the ratio is not 1×, a label such as "2.0×" above the shutter, tapping it returns to 1×. |
| Deleting the photo | A delete icon in the viewer's top bar (overflow when it does not fit) with a confirmation; afterwards the live camera opens. |
| Current photograph | Survives navigation, backgrounding, process death and device restart. |
| Replacement | A successfully captured new photograph replaces the current one without confirmation. Cancelled or failed capture preserves it. |
| Viewing | One finger pans; two fingers zoom. Dragging the bubble moves its aim. Reopening fits the photo to the available area. |
| Photo zoom limits | From fit to 8× fit, with pan constrained to the photograph's edges. |
| First recognition | Start automatically after a successful capture. Language/settings changes still require explicit recognition. |
| Recognition lifetime | Continue an already started attempt through tab changes and backgrounding, saving the result to the same document. Process restart never automatically restarts recognition. |
| Bubble actions | A tap briefly highlights text. Pull-out, tap, movement and docking never start photo OCR. |
| Bubble without text to read | On the Camera page with the live camera, a photo still loading, a photo not recognized yet or unreadable stored text: pull-out and tap show nothing (no popup, no message). |
| Bubble glyph over a photo | While a recognized photo is the lookup source, the bubble shows the photo's recognition language; the active language again once the photo is left. |
| Bubble menu over a photo | The language chips are hidden; copying and Open app stay. The photo's language is changed on the Camera page. |
| Leaving the photo | The bubble docks when the photo it was out over stops being the lookup source (another page, another app, the live camera). Pulled out over the live camera it shows nothing; the preview is never scanned (owner, 2026-10-25). |
| Language | Separate selection on Camera, restricted to enabled languages. Initially use the active app language, then remember independently. |
| Language or OCR settings change | Requires explicit recognition. Until then keep the actual recognition language and visibly indicate a pending change. |
| OCR engines | Share Cloud, Device and Both and the selected language's local model with ordinary scanning. |
| Cloud photo OCR | Process original-resolution overlapping tiles, each at most 1500 pixels per side. No whole-photo downscaling to that limit. |
| Result publication | Wait for the entire attempt to terminate. Then publish the assembled result, including usable partial results if some tiles failed. |
| Failure indication | Accessible warning icon in upper controls; details and retry of failed tiles. |
| Anki | First working version includes sentence context and cropping from the original photo. |
| Phone gallery | Explicit export of an ordinary photo; no automatic export. OCR stays in Screenlate's private data. |
| Internal history | Later phase: manual saving by default, with an automatic-saving switch. |
| History limits | Independent count and size limits, both default 0 (unlimited). Evict oldest history entries to satisfy nonzero limits. |
| Controls | Upper action icons; move actions into overflow only when they do not fit. Language and recognition status stay visible. |
| Camera settings | One page reachable from Camera and general Settings. |
| Backup | No backup integration for this feature. |
| Deferred | Import existing phone images in phase 3; photo rotation/cropping/perspective editing stays in the backlog. Anki attachment cropping is included earlier. |

## 2. Architecture and integration contracts

Read `docs/architecture.md`, `docs/development.md`, `ai/notes/module-map.md`, `ai/notes/ocr-engines.md`,
`ai/notes/overlay-system-ui.md`, `ai/notes/language-rules.md`, `ai/notes/translations.md` and `ai/test-model.md` as relevant.
Initial research: `ai/notes/camera-and-audio-feasibility.md`. The separate video/audio research branch is not a base
for this implementation and is not merged into it.

### Persistent photo document

Introduce `core:photos`, consumed by `app` and `overlay`. It owns storage, metadata, recognition snapshots and attempts.
Dependencies point inward to `core:common` and `core:ocr`. Use Hilt, Room metadata and DataStore preferences.
CameraX and Compose remain in `app`; dictionary lookup and rendering use their existing interfaces.

Store original images and OCR in private non-cache storage excluded from backup. Keep coordinates in consistently
oriented original-photo pixels, including separators, character boxes, directions and paragraph provenance.
Reconstruct `TextLayout`; do not store viewport-dependent layout objects.

Expose a small interface for observing, replacing and clearing the current document, recognizing it and retrying
failed tiles. Interrupted writes must leave a recoverable committed document. Closing an overlay or resetting
settings must not delete photographs.

### Exclusive lookup source

The source-selection contract has three states:

- Screen: existing screenshot and accessibility-text behavior.
- Photo: a visible document, its committed recognition snapshot and its viewport.
- Inactive camera view: preview, loading or another camera view without an inspectable photo; no screen fallback.

Resolve this before capture, accessibility-text reads, bubble-triggered cloud preparation or on-demand refinement.
Cover every entry point: pull-out, tap, model updates and configuration changes. Neither empty space under the aim
nor unavailable OCR may fall back to scanning the camera UI.

The photo source exists only while its viewer is presented in the foreground. Switching cancels obsolete lookup
work and invalidates callbacks by source/document generation. Leaving restores screen behavior; returning attaches
stored OCR without recognition. The photo lifetime is independent of the accessibility service. Capture and viewing
remain available without that service; the existing bubble requires it and uses the established enable-service flow.

### Coordinates and lookup

Publish a transformation from oriented photo pixels to screen pixels and the visible photo bounds. Invert it for
hit testing; transform and clip boxes for highlights and popup anchors. Toolbars, navigation and letterboxing are
outside the inspectable photo. Account for insets and the bubble's two aim modes.

Keep one `TextLayout` and spatial index per snapshot. Preserve tolerance, overlap selection and language rules.
Pan/zoom changes only the viewport, not OCR or the index. Re-evaluate a stationary aim when the photo moves beneath it.

### Photo recognition

Add a photo-specific path in `core:ocr`, preserving screen recognition. Cover both axes and every edge with overlapping
tiles, each no larger than 1500 pixels. Offset results back to original coordinates. Implement a dedicated overlap
assembler for words, lines and paragraphs; existing `withMissingFrom` can discard partially overlapping lines and
is not the tile merger.

Preserve original photo quality. Decode/crop on workers, bound memory and retain images until engines finish reading.
At most two cloud tile requests run concurrently. Device sends no cloud requests. Both uses the local engine and
existing fallback semantics, recording actual provenance. Tiling is independent of the screen small-text setting;
aiming never schedules recognition.

Each attempt captures document, language and settings. Publish after all work terminates; retain successful tiles
and failed-tile metadata. Explicit rescan starts a new attempt. Retry keeps successful work. Application restoration
never silently restarts network recognition. During recognition, do not publish unfinished tile results.

### Notes and image ownership

Generalize the image supplied to `NoteSource`/`NoteContext` for screen and photo adapters, with their image coordinates
and crop focus. Pin document, snapshot, language and image when adding a note. Adapt the Anki crop editor to fit large
images and map the selection to original pixels. Export an original-photo crop, not a viewer screenshot. Preserve
screen-source behavior, reference ownership and cancellation guarantees. ML Kit can continue reading after cancellation.

## 3. Delivery phases

### Phase 0: repository plan

- [x] Create isolated branch from current `origin/languages/main` (`d1977c62`).
- [x] Save approved English plan, startup memory pointer and status; commit and push (`7cf29809`).

### Phase 1: complete single-photo workflow

- [x] 1.1 Document storage and source selection: persistence, snapshots, viewport and exclusive targeting; tests for zero screen-scan calls.
- [x] 1.2 Navigation and camera: bottom tabs with retained state; rear CameraX, quality capture, auto exposure/focus, tap focus, pinch zoom and Off/Auto/On flash; release camera on exit. Hardware flash and real-page quality remain physical-device acceptance checks.
- [x] 1.3 Recognition and viewer: tiles, assembly, progress, terminal partial-result warnings, retry, pan/zoom, language indication and restoration.
- [x] 1.4 Popup and Anki: definitions, audio, translation and notes use recognition language; original-photo crop and explicit gallery export.
- [ ] 1.5 Documentation, translations, required automated checks and emulator checks; record physical-device checks separately.

Phase 1 delivers camera-to-dictionary-to-Anki. Permission denial, capture failures and missing dictionaries/models
must be actionable without losing the current photo. Reuse readiness with explicit image-source requirements:
app-text-only cannot bypass photo OCR requirements; stored OCR remains searchable without the former OCR model.

### Phase 2: internal history

- [ ] Thumbnails, manual save, automatic save, open, selection and deletion; restore saved OCR without recognition.
- [ ] Opening makes a document current and fits it to the viewport; repeated save does not duplicate membership.
- [ ] Removing history membership preserves a separately retained current document.
- [ ] Enforce count/byte limits oldest first; count image, thumbnail and OCR bytes; display usage.
- [ ] Shared Camera settings page and both entry points; phone-gallery files remain independent.
- [ ] Tests, documentation, translations, emulator checks, commit and push.

### Phase 3: existing-image input

- [ ] System photo picker imports into the same private document workflow, preserving orientation and replacement rules.
- [ ] Recognize as a new document; tests, docs, translations, emulator checks, commit and push.

Backlog: manual photo rotation, cropping and perspective correction. They do not expand the phases above.

## 4. Verification and acceptance

### Automated tests

- Photo gestures make zero calls to capture, accessibility reads, cloud preparation and automatic refinement.
- Source switching rejects late screen, OCR, lookup and note-context results.
- Coordinate round trips, clipping, tolerance and indexed hit tests survive pan, zoom, rotation and window sizes.
- Tiles cover every edge; boundary words survive; duplicates disappear while legitimate repetitions remain.
- Assembly covers horizontal text, vertical Japanese, RTL, combining marks, line breaks and sentence context.
- Publish after every tile terminates; partial failures remain visible; retry only failed tiles.
- Device makes no cloud calls; snapshots retain actual language after settings change.
- Persistence handles interrupted writes, replacement, restart and damaged/incompatible OCR while preserving the image.
- Anki pins the correct image/language and crops original pixels through navigation/replacement.
- History defaults, deduplication, both limits and deletion; picker orientation and replacement.
- Navigation preserves onboarding, deep links and text-selection search.

### Emulator and physical-device checks

Add cases to `ai/test-model.md` as N with coverage and a run log. Verify actual overlay gestures, reconnects and
API 30/API 37 behavior. Run relevant E regressions for bubble, OCR, popup, Anki, languages, navigation and settings.
Use the shared emulator lock. Never `am force-stop`; do not run app-wide connected tests that remove the installed app.
Anki tests use only the emulator's unsynced local collection. No owner's Anki data or account is touched.

Real hardware must verify sharpness, focus, flash, capture delay and dense pages. Measure recognition time, drag/zoom
responsiveness and memory after repeated captures and leaving the viewer; emulator cameras cannot prove image quality.

### Required checks and documentation

Run debug assembly, JVM tests and lint. Run page tests when shared popup/note behavior changes. Add instrumented tests
for persistence, crop coordinates and navigation when JVM tests cannot cover them. Update architecture, usage,
development, module map, test model and status. Attribute CameraX in NOTICE; new UI strings cover all 14 locales.
Logs exclude recognized text, terms, note contents, photo content and sensitive paths.

Commit and push each coherent milestone to `features/camera-lookup`. Record evidence and pending device checks.

## Progress and changelog

- 2026-10-25: owner approved the complete plan and requested implementation in an independently resumable branch.
  Phase 0 committed and pushed as `7cf29809`.
- 2026-10-25: owner asked to finish the current milestone and pause, preserving everything required for a fresh
  session. Phase 0 was the current milestone; no application code had been started. Added the architecture handoff
  note, marked the pause and recorded the exact next step. Implementation resumes at phase 1.1 only on request.
- 2026-10-25: owner resumed the work at phase 1.1 and answered four bubble questions: nothing is shown when the
  Camera page has no text to read; the glyph shows the photo's recognition language; the menu hides the language
  chips over a photo; the bubble docks when the user leaves the photo. Leaving the photo also covers opening the live
  camera from a shown photo (same rule, applied by the implementing session; confirm with the owner).
- 2026-10-25: phase 1.1 built. `core:photos` stores one current photo and its recognized text with atomic writes and
  a tidy-up after interruptions; `LookupSources` is the exclusive source with generations; the overlay reads the
  screen only through `ScreenReads` and aims at a photo through `TextFrame`. Implementation choices within the
  decisions: over a photo only the dictionaries must be installed (stored text needs no OCR model); a note over a
  photo has no picture until the photo crop of 1.4; moving the photo re-aims like moving the bubble but does not reopen
  a popup the user closed; the spatial index waits for 1.3 (the hit test stays a linear scan).
- 2026-10-25: owner confirmed that opening the live camera from a shown photo docks the bubble, and that a bubble
  pulled out over the live camera shows nothing: the preview is never scanned, as its text would not stay under the
  aim. Owner asked to continue with phase 1.2.
- 2026-10-25: owner answered the phase 1.2 questions (rows Navigation bar, Home after the tabs, Camera layout, New
  photo over a photo, Flash, Camera permission, Zoom, Deleting the photo). Owner added: a denied camera permission
  must be explained in place of the camera, with a button that asks again in the app when possible and otherwise
  opens the app's settings.
- 2026-10-25: navigation bar built (phase 1.2, first half); the Camera page is still empty. Implementation choices:
  overlay requests open their pages in the Settings tab, pages opened from a tab stay in that tab, the live preview
  will use FIT_CENTER so the whole captured frame is visible. Paused by the owner at the usage limit with a handoff.
- 2026-10-26: owner resumed after the navigation checkpoint. Phase 1.2 completed with capture, persistence-backed
  viewer, source lifecycle, permission failures/retry and deletion; tests and device evidence are in the handoff and
  test-model run log. No product decisions changed. Next is photo recognition (1.3), then original-photo Anki/export (1.4).
- 2026-10-26: owner started phase 1.3 and confirmed automatic recognition after capture, continuation through tab
  changes/backgrounding without automatic process-restart retries, and photo zoom from fit through 8× fit with bounded pan.
- 2026-10-26: photo OCR foundation implemented and verified independently: 2D tile coverage, overlap assembly,
  immutable hit-test index, pinned local models, terminal tile processing and persisted word provenance (v2 reads v1).
  Capture and viewer still await the attempt coordinator and UI integration; phase 1.3 is not complete.
- 2026-10-26: phase 1.3 completed: recoverable attempts, original-resolution region decoding, bounded recognition,
  terminal snapshots/retry, capture auto-recognition, independent language/readiness/status/warnings and bounded
  sharp pan/zoom. Required build/test/lint and API 30/API 37 evidence recorded in the handoff/test model. No product
  decision changed. Phase 1.4 remains the next implementation milestone; no merge and no Anki data changes.
- 2026-10-26: owner continued phase 1.4 and required resumable repository checkpoints. Added original-photo note
  ownership/crops and gallery export; intermediate implementation pushed as `e8951271`. Native API 30/API 37 checks,
  actual local Anki/gallery scenario and required build/unit/lint/page checks pass (see handoff/test model). Original
  Japanese Anki settings restored. No product decision changed. Remaining phase 1.5 acceptance is the next step;
  no history, picker or merge started.
