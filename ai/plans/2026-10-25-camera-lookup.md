# Camera Photo Lookup

Approved by the owner on 2026-10-25. Branch: `features/camera-lookup`, based on
`origin/languages/main` at `d1977c62`, rebased on 2026-10-26 onto `languages/main` at `073c50a4`. This plan and all repository documents are English; talk to the owner in Russian.
Implementation stays on the feature branch. Merging into `languages/main` or `main` is a separate step.

## Resume / handoff

Read this section, `ai/status.md`, the decision table below, and the notes named under Architecture before editing.

- Checkout: `C:/Users/vpr/.codex/worktrees/camera-lookup/Screenlate` (managed worktree attached to the Codex chat).
- Branch: `features/camera-lookup`, tracking `origin/features/camera-lookup`.
- Phase 1.5 emulator acceptance is COMPLETE (2026-10-26, resumed by the owner after the pause). Commit
  "Clip photo highlights to the viewer and attach the Camera source from page start" (use `git log` for the hash)
  follows the fixture checkpoint "Stabilize acceptance fixtures and installation-link regression". It fixes two
  findings: highlight margins bled over the toolbar above a zoomed photo (the overlay now clips highlights to the
  photo's visible part, `TextFrame.clip` → `LayerView.clip`); a pull-out right after tapping the Camera tab scanned
  the previous page because the page attached only when resumed (`CameraScreen` now presents from ON_START until
  ON_PAUSE). CAM3, CAM4, CAM6, CAM7 and NAV3 moved to F; E regressions BUB1/BUB2/OCR1/ANK2/AUD1/TR1/UPD2/LNG1 pass.
  Evidence: `ai/test-model.md` run log 2026-10-26 "phase 1.5 acceptance". Root checks: 1586 JVM tests, 0 failures,
  5 skipped; lint without new findings; page scripts unchanged (146/146 earlier).
- Owner request 2026-10-26: a from-scratch visual design of the Camera page by a separate designer agent that does
  not look at the current implementation, several variants for the owner to choose from or correct; then restyle the
  page after the chosen design. The mockups are local (Claude's scratchpad, `camera-design/camera-screen-designs.html`),
  not in the repository. Owner chose variant A with one change (decision table, "Camera page design"); the restyle
  goes first, then phase 2 and phase 3.
- Restyle after design A is DONE (2026-10-26, commit "Restyle the Camera page after the chosen design"): the status
  strip above the photo (`CameraStatusStrip`, pure model `cameraStatus`), the language chip with a dropdown over the
  photo and over the live camera (`CameraLanguageChip`), the warning next to Recognize with a picture of the missing
  regions, Save to gallery always in ⋮, the close icon at the left of the live camera, the ring shutter and the zoom
  pill, the 4:3 preview at its viewer size with a blurred fill from a 320×240 analysis stream (none on e-ink), the
  permission view with an icon and title, "Photo deleted", "Opening photo…", and `PhotoViewport.resized` keeping the
  zoom when the strip changes height. Checks: root `assembleDebug testDebugUnitTest lintDebug --max-workers=1` pass
  (1593 JVM tests, 0 failures, 5 skipped; lint 44 warnings, none new); API 37 by hand and API 30 LiveCameraTest/PhotoViewerTest/PhotoViewerGestureTest (run log
  2026-10-26 "the Camera page restyle"). CAM27–CAM32 moved to F; CAM33 (zoom kept on strip resize) stays N, as adb
  cannot pinch on API 37. Status texts say "regions" like the existing warning, not the mockup's "parts".
- Owner decided 2026-10-26: the Search tab no longer focuses its field on opening (keyboard only on tapping it);
  done and checked on API 37.
  The follow-up outside this feature ("Open Screenlate" from the bubble menu stacked a second MainActivity) is fixed
  in `languages/main` (`073c50a4`, owner's order); this branch was rebased onto it. `main`'s `ai/` copy is unchanged.
- Phases 0, 1.1, 1.2, 1.3 and 1.4 are implemented. Last camera implementation: `e8951271`
  ("Add original-photo note images and explicit gallery export"); verification/docs checkpoint `6c3956a8` follows it.
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
- Phase 2 (internal history) is DONE (2026-10-26, commit "Add the photo history and Camera settings"):
  `PhotoHistory`/`HistoryLimits`/`PhotoThumbnails` in core:photos, Room v3 (`saved`, `savedAt`, `language`),
  `CameraPreferences` with automatic saving and limits; app `PhotoHistoryScreen` (grid, selection, the unsaved-photo
  question shared with closing), `CameraSettingsScreen` (switch, limit fields, usage, Clear history), the history
  button right of the shutter (`LiveCamera`'s `side` slot; the left slot is kept for the phase 3 import button), ✕
  instead of Delete, Save to history in ⋮, the Camera row in Settings. A photo that leaves the history is deleted, the
  open one included (owner's change). Checks: 1636 JVM tests, 0 failures, 5 skipped; lint without findings in the
  camera code; API 37 `PhotoDatabaseTest` 9/9 and the manual flows, API 30 `UnsavedPhotoDialogTest` 3/3 (run log
  2026-10-26 "the photo history and Camera settings"; CAM34–CAM41).
- Phase 3 (existing-image input) is DONE (2026-10-26, commit "Import images from the photo picker and the share
  sheet"): `PhotoImports`/`PhotoImporter` in core:photos (original bytes for JPEG, PNG, WebP and HEIF, else a PNG of
  the upright pixels, at most 24 MP; undecodable files rejected), `CameraViewModel.importImage`/`answerImport` with a
  queue that waits for a loaded, idle page, `CameraState.askingImport` for the question after the pick, the import
  button left of the shutter (`LiveCamera`'s `start` slot), ⋮ Import from gallery over a photo and over the live
  camera, the button on the camera-access page; `ShareImageActivity` (no UI) passes a shared image with its read
  grant to `MainActivity`, which hands it to `SharedImages` and selects the Camera tab (not during the first run).
  The share activity takes only another app's `content:` link (`IntentSanitizer`).
  Checks: 1652 JVM tests, 0 failures, 5 skipped; lint without findings in the new code; API 37
  `PhotoImportDeviceTest` 3/3 and the manual flows on API 37 and API 30 (run log 2026-10-26 "importing images and
  sharing them to the app"; CAM42–CAM46).
- Next concrete action: every phase is done. Left before any merge: the physical-device checks below on the owner's
  phone (with the picker and a share from the phone's gallery added to them); merging is the owner's decision.
- Physical sharpness, focus, flash, capture delay and repeated-capture/memory/drag baselines still need a phone;
  emulator evidence does not establish them. Phase 1 is accepted on emulators only; record these phone checks
  before any merge.
- Cleanup after phase 1.5: both fixtures restored (API 37: Slovenian active, Japanese Anki settings back, no current
  photo, camera language Japanese; API 30: its unanswered first run). The emulator's local unsynced Screenlate Photo
  Test deck holds four more notes; no account/sync/AnkiWeb operations. API 30 now has the universal debug APK (an
  x86-only copy has no native libraries and crashes ML Kit). Test APKs removed, both emulators shut down, shared lock
  released. No new worktree or submodule changes. Read the notes before editing image ownership.
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
    bitmap through the EXIF matrix and publishes its viewport only after decoding. `CameraLookup` attaches from
    the page's start until it pauses (phase 1.5 fix); live/loading/unrecognized/failed views remain NoText, with no
    screen fallback.
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
| Search keyboard | The Search tab opens without focusing its field; the keyboard opens on tapping the field, so the bar stays reachable (owner, 2026-10-26). |
| Home after the tabs | The settings icon, the Search card and the Settings button leave Home; the background-work badge moves to the Settings tab. |
| Camera page | Show the current photograph when present; otherwise open the live camera. |
| Camera layout | The frame sits under the ordinary top bar (title, icons on the right); a round shutter button at the bottom of the frame. The photo viewer uses the same top bar. |
| Camera page design | Variant A of the designer's mockups (owner, 2026-10-26). Viewer: under the top bar a status strip; first row the photo-language chip (flag, name, dropdown), the warning icon and Recognize (text button; filled while a change is pending, hidden while recognizing); second row the status as text ("Recognized · 8 of 8 parts · 12:41", "Recognizing · 3 of 8 parts", "Recognized as Slovenian, 6 of 8 parts · language changed"); a linear progress along the strip's bottom edge; a pending change tints the strip. Language picker: a dropdown from the chip. Live camera (owner's change to A): no strip, only the language chip floating over the preview; the preview shows exactly the 4:3 frame that is captured, at the size the photo has in the viewer, and the rest of the area down to the navigation bar shows a blurred copy of the live image instead of black; the round shutter as drawn (white ring) over it; later import and history buttons left and right of it. No progress mark on the Camera tab while recognition runs on another tab. Mockups: Claude's scratchpad `camera-design/`, not in the repository. |
| New photo over a photo | A camera icon in the viewer's top bar opens the live camera; its close icon and system back return to the photo unchanged. A capture replaces the photo and shows the new one. |
| Flash | Off/Auto/On as one top-bar icon that cycles. Off at every app start; the choice lasts while the app process lives. |
| Camera permission | Asked when the Camera page first opens the live camera. Denied: an explanation in place of the frame with a button that asks again in the app while the system allows it, otherwise opens the app's system settings. The current photo is shown without the permission. |
| Zoom | Pinch zoom; while the ratio is not 1×, a label such as "2.0×" above the shutter, tapping it returns to 1×. |
| Closing the photo | A close icon (✕) at the left of the viewer's top bar replaces the delete icon (owner, 2026-10-26). A photo in the history closes without a question and stays there; one not in the history (manual saving) asks "The current photo isn't saved to history and will be deleted" with Save to history / Delete / Cancel. Afterwards the live camera opens; "Photo deleted" after Delete. Photos leave the history only on the history page, by a limit or with Clear history; a photo that leaves it is deleted, the one open on the Camera tab included, and the tab then shows the live camera (owner, 2026-10-26). |
| Current photograph | Survives navigation, backgrounding, process death and device restart. |
| Replacement | A successfully captured new photograph replaces the current one without confirmation. Cancelled or failed capture preserves it. |
| Viewing | One finger pans; two fingers zoom. Dragging the bubble moves its aim. Reopening fits the photo to the available area. |
| Photo zoom limits | From fit to 8× fit, with pan constrained to the photograph's edges. |
| First recognition | Start automatically after a successful capture. Language/settings changes still require explicit recognition. |
| Recognition lifetime | Continue an already started attempt through tab changes and backgrounding, saving the result to the same document. Process restart never automatically restarts recognition. |
| Bubble actions | A tap briefly highlights text. Pull-out, tap, movement and docking never start photo OCR. |
| Bubble without text to read | On the Camera page with the live camera, a photo still loading, a photo not recognized yet or unreadable stored text: pull-out and tap show nothing (no popup, no message). |
| Bubble glyph over a photo | While a recognized photo is the lookup source, the bubble shows the photo's recognition language; the active language again once the photo is left. |
| Bubble menu over a photo | The language chips are hidden; copying stays. Open app is not shown while any Screenlate window is in front (owner, 2026-10-27; was: Open app stays); a menu left without items does not open. The photo's language is changed on the Camera page. |
| Leaving the photo | The bubble docks when the photo it was out over stops being the lookup source (another page, another app, the live camera). Pulled out over the live camera it shows nothing; the preview is never scanned (owner, 2026-10-25). |
| Language | Separate selection on Camera, restricted to enabled languages. Initially use the active app language, then remember independently. |
| Language or OCR settings change | Requires explicit recognition. Until then keep the actual recognition language and visibly indicate a pending change. |
| OCR engines | Share Cloud, Device and Both and the selected language's local model with ordinary scanning. |
| Photo reading | By default the whole photo is read once, scaled down to 1500 pixels on its longer side, for every engine. "High accuracy" on Camera settings reads by original-resolution overlapping tiles of at most 1500 pixels plus the whole-photo pass for large text, as before (owner, 2026-10-27; was: tiles always). "Recognize more accurately" first in the photo's ⋮ menu reads the current photo by tiles once; shown only for a photo read whole, not while it is recognized. The switch's hint names the time and, with the cloud, the traffic. |
| Camera settings: recognition | A "Recognition" group first: the High accuracy switch and an Engines row showing the current choice that opens Bubble → Screen recognition (owner, 2026-10-27). |
| Result publication | Wait for the entire attempt to terminate. Then publish the assembled result, including usable partial results if some tiles failed. |
| Failure indication | Accessible warning icon in upper controls; details and retry of failed tiles. |
| Anki | First working version includes sentence context and cropping from the original photo. |
| Phone gallery | Explicit export of an ordinary photo; no automatic export. OCR stays in Screenlate's private data. |
| Internal history | Phase 2: manual saving by default, with an automatic-saving switch. Automatic saving stores each photo right after capture, its text joins it when recognized; turning the switch on also saves the current photo (owner, 2026-10-26). |
| History page | A page in the Camera tab: a grid of thumbnails, newest first, each with its date and the flag of its recognition language. A tap opens the photo as the current one; a long press selects several to delete from the history (owner, 2026-10-26). |
| History entry points | As in design A: a round button right of the shutter showing the latest history photo (the history icon while the history is empty and with the e-ink theme); over a photo the ⋮ menu has Save to gallery, Save to history, History and Camera settings last. Save to history shows "Saved to history" and leaves the menu once the photo is saved; with automatic saving it is not shown (owner, 2026-10-26). |
| Opening from the history | With manual saving, if the current photo is not in the history, the same three-button question as closing comes first; with automatic saving no question (owner, 2026-10-26). |
| History limits | Independent count and size limits, both default 0 (unlimited), as number fields (photos; MB) on Camera settings (owner, 2026-10-26). Evict oldest history entries to satisfy nonzero limits. Lowering a limit below the current history asks "N oldest photos will be deleted from history" with Delete / Cancel; Cancel keeps the old limit. The open photo is deleted too when it is among the oldest. |
| Controls | Upper action icons; move actions into overflow only when they do not fit, except Save to gallery, which is always in the overflow menu as in design A (owner, 2026-10-26). Language and recognition status stay visible. |
| Camera settings | One page reachable from Camera's ⋮ menu and general Settings, where its row stands before Appearance (owner, 2026-10-26). It holds automatic saving, the two limits, the history's usage ("37 photos · 182 MB") and Clear history with a confirmation ("All photos in history will be deleted."). Clear history deletes every photo in the history, the one open on the Camera tab included; an open photo never saved to the history (manual saving) stays (owner, 2026-10-26, replacing "the current photo stays on the Camera tab"). |
| Importing images | Phase 3: the system photo picker, one image at a time (owner, 2026-10-26). Entry points as in design A: a round button left of the shutter, Import from gallery in ⋮ over a photo (before Camera settings) and over the live camera (after History, owner), and a button under the camera-permission explanation. The image becomes the current photo; with manual saving an unsaved current photo first gets the same three-button question as opening from the history, after the pick (closing the picker changes nothing; Cancel drops the picked image). Recognition starts at once in the camera language and automatic saving applies, as for a capture; a history tile shows the import time, not the date the image was taken (owner, 2026-10-26). |
| Sharing images to the app | "Share → Screenlate" with one image opens it on the Camera tab and continues exactly as an import: the question, recognition, automatic saving (owner, 2026-10-26). |
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
- [x] 1.5 Documentation, translations, required automated checks and emulator checks; physical-device checks recorded as pending.

Phase 1 delivers camera-to-dictionary-to-Anki. Permission denial, capture failures and missing dictionaries/models
must be actionable without losing the current photo. Reuse readiness with explicit image-source requirements:
app-text-only cannot bypass photo OCR requirements; stored OCR remains searchable without the former OCR model.

### Phase 2: internal history

- [x] Thumbnails, manual save, automatic save, open, selection and deletion; restore saved OCR without recognition.
- [x] Opening makes a document current and fits it to the viewport; repeated save does not duplicate membership.
- [x] Removing a photo from the history deletes it; the current one is closed first (owner's change, 2026-10-26).
- [x] Enforce count/byte limits oldest first; count image, thumbnail and OCR bytes; display usage.
- [x] Shared Camera settings page and both entry points; phone-gallery files remain independent.
- [x] Tests, documentation, translations, emulator checks, commit and push.

### Phase 3: existing-image input

- [x] System photo picker imports into the same private document workflow, preserving orientation and replacement rules.
- [x] "Share → Screenlate" for one image takes the same path.
- [x] Recognize as a new document; tests, docs, translations, emulator checks, commit and push.

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

- 2026-10-26: owner resumed phase 1.5, then requested a pause at the current milestone because of usage limits.
  Finished language/search/popup native regression maintenance (7/7 on API 37), added the opt-in settings-preserving
  acceptance helper and recorded a resumable handoff. No product decisions changed. Remaining manual CAM/NAV,
  ordinary screen/Anki/audio/translation and physical-device checks stay pending; phase 1.5 is not complete.
- 2026-10-26: owner resumed phase 1.5 and asked to finish it. Emulator acceptance done on API 37 and API 30;
  two findings fixed (highlight clip to the photo viewer, Camera source from the page's start). No product decision
  changed. Owner asked for a from-scratch Camera page design by a separate agent, variants to choose from, then a
  restyle after the chosen one (pending). Open: Search keyboard on a blank query; the second MainActivity follow-up.
- 2026-10-26: owner decided that the Search tab opens without the keyboard (it covered the bottom bar); the field
  focuses only when tapped. Added to the decision table.
- 2026-10-26: owner chose design variant A for the Camera page; in the live camera only the language chip over a full-height preview, no strip and no black band; shutter as drawn. Restyle before phase 2.
- 2026-10-26: owner answers on design A: live preview shows the captured 4:3 frame at its viewer size with a blurred fill around it (no black band, no hidden margins); Save to gallery moves into the overflow menu (changes the Controls decision); no progress mark on the Camera tab. Owner had the "Open Screenlate" second-screen fix fast-forwarded into `languages/main` and this branch rebased onto it.
- 2026-10-26: Camera page restyled after design A with the owner's changes; checked on API 37 and API 30. Status texts
  use "regions" (as the existing partial-result warning) instead of the mockup's "parts", and a recognition in another
  language reads "Recognized: <language>". No product decision changed. Next: phase 2.
- 2026-10-26: owner answered the phase 2 questions: the history is a grid page; ✕ replaces the delete icon and closes a
  history photo without removing it, while a photo not in the history asks Save to history / Delete / Cancel, also
  before opening another photo from the history with manual saving; limits as number fields with a question before
  lowering below the current history; automatic saving at capture; the history button shows the latest photo;
  Save to history leaves the menu once done; Camera settings has Clear history and stands before Appearance in
  Settings. Changes the "Deleting the photo" decision (now "Closing the photo").
- 2026-10-26: phase 2 built. Implementation choices within the decisions: the latest history photo always stays
  under a limit; the history is ordered by save time (`savedAt`), earlier rows by id; the language shown on a tile is
  the last recognition's, read from the snapshot on saving when an older row lacks it; thumbnails are 320 px upright
  JPEGs; a megabyte is 10^6 bytes, as Android shows sizes; History and Camera settings are also in ⋮ over the live
  camera; Camera settings opened from Camera stay in the Camera tab, from Settings in the Settings tab; the page title
  is "Camera"; a failed close says "The photo could not be closed."; settings errors show a toast; a history photo
  whose image is gone says the photo can't be read and leaves the history. Found on the emulator and fixed: Save to
  history in the question on the history page did nothing, because the dialog dismissed itself before answering.
- 2026-10-26: owner changed a decision after the emulator check: with automatic saving a photo is in the history at
  once, and a photo that leaves the history (the history page, a limit or Clear history) is deleted, the one open on
  the Camera tab included; the tab then shows the live camera. An open photo never saved to the history stays.
  Replaces "the current photo stays on the Camera tab" in the "Camera settings" row and updates "Closing the photo"
  and "History limits". Phase 2 is complete; next is phase 3 (system photo picker).
- 2026-10-26: owner started phase 3 and answered its questions: one image per import; with manual saving the
  three-button question about an unsaved current photo comes after the pick; recognition at once and automatic saving
  as for a capture; the tile shows the import time; Import from gallery also in ⋮ over the live camera. Owner added
  "Share → Screenlate" for an image to this phase (rows "Importing images" and "Sharing images to the app").
- 2026-10-26: phase 3 implemented and checked on API 37 and API 30; no decision changed. Details the table left open,
  chosen in implementation: formats the photo viewer reads are kept as they came, others become a PNG scaled down past
  24 MP; a picked image waits until the Camera page has loaded its photo and is idle; a share during the first run
  does not switch tabs and opens when the Camera tab is first opened; a share without a read grant says "The image
  could not be opened."; a share of a link to the app's own providers or a `file:` path is ignored (found by lint);
  the API 30 emulator has no system photo picker and gets the Files app's picker instead.
  Every phase is complete; the physical-device checks remain.
- 2026-10-26: owner reworded the Save automatically hint for imports: "Each new photo, taken or imported, goes to
  history right away." in all 14 locales.
- 2026-10-27: owner's feedback on the phone build: photos read by tiles are slow (a 12 MP photo is 9 tiles and the overview, 10 requests two at a time). Decided: rows "Photo reading" (was "Cloud photo OCR") and "Camera settings: recognition"; "Bubble menu over a photo" changed (Open app hidden in the app). Found the same day, fixed without a decision: the popup shows the previous word for a moment when it opens on a new one (the window became visible before the page drew the new state; screen scans too); with Both, a photo tile waited for the device result after the cloud answered.
