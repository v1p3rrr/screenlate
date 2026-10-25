# Camera Photo Lookup

Approved by the owner on 2026-10-25. Branch: `features/camera-lookup`, based on
`origin/languages/main` at `d1977c62`. This plan and all repository documents are English; talk to the owner in Russian.
Implementation stays on the feature branch. Merging into `languages/main` or `main` is a separate step.

## Resume / handoff

Read this section, `ai/status.md`, the decision table below, and the notes named under Architecture before editing.

- Checkout: `C:/Users/vpr/.codex/worktrees/camera-lookup/Screenlate` (managed worktree attached to the Codex chat).
- Branch: `features/camera-lookup`, tracking `origin/features/camera-lookup`.
- State: paused at the owner's explicit request on 2026-10-25. Phase 0 is complete. Application implementation has
  not started; do not infer that any camera code or `core:photos` module exists.
- Completed commit: `7cf29809` (`Document the camera photo lookup implementation plan`), pushed to origin.
  This handoff is updated by the following documentation checkpoint, `Record the paused camera implementation handoff`.
- Uncommitted implementation: none. The checkpoint changes only plan/status/technical notes and their startup pointer.
- Next after the owner resumes: implement phase 1.1 (persistent document storage and the exclusive lookup-source
  contract), with tests, then update this handoff and commit/push the coherent milestone.
- First read: `ai/notes/camera-lookup.md` preserves the architecture investigation and concrete integration pitfalls.
- Checks: `git diff --check` passed for the planning commit. No build, unit tests, page tests, emulator or phone checks
  have been run for this feature; no device state was changed. Check the checkpoint's diff before committing it.
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
| Camera page | Show the current photograph when present; otherwise open the live camera. |
| Current photograph | Survives navigation, backgrounding, process death and device restart. |
| Replacement | A successfully captured new photograph replaces the current one without confirmation. Cancelled or failed capture preserves it. |
| Viewing | One finger pans; two fingers zoom. Dragging the bubble moves its aim. Reopening fits the photo to the available area. |
| Bubble actions | A tap briefly highlights text. Pull-out, tap, movement and docking never start photo OCR. |
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

- [ ] 1.1 Document storage and source selection: persistence, snapshots, viewport and exclusive targeting; tests for zero screen-scan calls.
- [ ] 1.2 Navigation and camera: bottom tabs with retained state; rear CameraX, quality capture, auto exposure/focus, tap focus, pinch zoom and Off/Auto/On flash; release camera on exit.
- [ ] 1.3 Recognition and viewer: tiles, assembly, progress, terminal partial-result warnings, retry, pan/zoom, language indication and restoration.
- [ ] 1.4 Popup and Anki: definitions, audio, translation and notes use recognition language; original-photo crop and explicit gallery export.
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
