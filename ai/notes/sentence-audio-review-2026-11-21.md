# Sentence audio implementation review — 2026-11-21

## Scope and verdict

Reviewed production tree: **`cfc637fa81cf5a98018ddb98531c53add67751a9`**, branch
`codex/sentence-audio-recording`. No production source was changed during this review.

The history was rebuilt after the original handoff. `c82be1e5` is the corresponding handoff commit in the
current ancestry; its `core/media` and `tools/audio-probe` trees match `ba5bacd0`. The scoped diff is
`git diff c82be1e5...cfc637fa`, about 8,700 added lines across feature code/resources/tests. Unrelated
dictionary changes and OEM research are not attributed to the sentence-audio implementation.

Sources of requirements: [binding plan](../plans/2026-11-19-sentence-audio-recording.md), root `AGENTS.md`,
`docs/architecture.md`, `docs/development.md`, relevant module/engineering notes. Standards and Spec were
reviewed independently; the root reviewer traced integration and added controlled reproductions.

**Verdict: keep the codebase and stack, but selectively replace lifecycle/orchestration.** The feature has
substantial implementation, not just a mockup, but normal workflows still have release-blocking failures.
There is no evidence that Kotlin, Android playback capture, Opus/Ogg or the existing WebView renderer must
be abandoned. The critical problems are conflicting state ownership, thread boundaries, stale asynchronous
results and incomplete failure/retry contracts. Rewriting everything would discard useful tested primitives
without itself solving those problems.

Module direction is broadly correct (`app`/`overlay` toward `core:media`/`core:anki`); no new unsupported
framework or GPL implementation-boundary breach was found in the reviewed feature. Android's Ogg muxer
exists from API29, which matches the feature gate. This does not certify every device/Anki client's codec
compatibility. [Android output-format API](https://developer.android.com/reference/android/media/MediaMuxer.OutputFormat#MUXER_OUTPUT_OGG).

## Standards

The first four findings are the independent Standards review; S5–S7 are additional integration findings.
Smell-based recommendations are judgments, not automatic violations or excuses to rewrite by line count.

### S1 — P1: preview calls WebView from the playback thread

`AudioPreviewPlayer.kt:199–214` invokes progress/completion on `audio-preview-player`.
`AudioEditorController.kt:216–221` directly forwards them to `LookupPage`, whose `run()` at `331–334`
calls `WebView.evaluateJavascript` directly. There is no return to Main for outgoing preview updates.

This violates the overlay's main-thread contract in `docs/architecture.md:223–225` and the WebView API's
thread requirement. A progress exception is swallowed by playback and can silently terminate preview;
the final callback is outside the protected catch and can propagate as an uncaught worker exception.
The actual device crash was not reproduced in this review; the wrong-thread call chain is established in
source and contradicts the platform contract. Dispatching inbound bridge methods to Main does not fix
outbound callbacks. [WebView API](https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,%20android.webkit.ValueCallback%3Cjava.lang.String%3E)).

### S2 — P1: blocking IO and one global monitor obstruct Main

`AudioEditorController.edit():81–104` and `onAudioRefresh():264–272` synchronously compute the complete
history waveform on Main. `SentenceRecorder.waveformSummary/findSilence/export` hold the facade's
`@Synchronized` monitor for the entire read/analysis/encode (`117–152`, `206–227`). Main-thread Stop,
Refresh and editor actions need that same monitor. Moving export itself to Default does not prevent
Main from waiting on its lock for seconds. Coordinator Stop also joins worker threads synchronously.

This violates the documented worker-dispatch contract for file work. The correction needs a short state
critical section to acquire immutable operation-owned snapshots, followed by IO outside the UI/state lock.
Adding more `@Synchronized` to all methods would worsen this problem.

### S3 — P2: source/session resource ownership is incomplete

`PcmStorageCoordinator.start():83–86` returns when `audioSource.start()` fails, before assigning it to
`source` at `95`. The facade closes that coordinator, which cannot close the unassigned source; the overlay
does not close it either. This leak was reproduced with an instrumented fake source.

All recorder sessions also share one directory (`SentenceRecorder.kt:36,59`). Restart closes the old
coordinator, then `cleanAbandonedFiles():300–307` deletes every PCM regardless of leases held by an old
preview/snapshot reader. The primitive's pin counts cannot protect against an external startup cleanup.
Use an explicit owner per session and transactional startup; never classify a live previous lease as abandoned.

### S4 — P2: temporary artifacts are tied to callbacks instead of operations

`AudioPlayer.kt:71–74` removes synthesized TTS only on natural playback completion; `stop():43–47`
does not retain/delete the corresponding file. Interruption/preparation cancellation/errors can accumulate
files, and startup cleanup does not cover all TTS/export artifact types. `AudioExporter.kt:40–46` constructs
the codec and then muxer before entering `try`: a muxer-construction failure leaves the codec unclosed.
These local defects share a lifecycle-contract problem; a bounded operation owner should release its files,
codec, grants and jobs on every terminal path.

### S5 — P1: asynchronous capture failure never reaches the public recording state

`PcmStorageCoordinator.fail():272–285` transitions its private state and stops the source for read/queue/
storage failure. `SentenceRecorder` only writes its StateFlow at start, explicit stop and close (`40–85`);
it does not observe coordinator failures. After an actual read failure, `isRecording` remains true, so
settings/bubble/notification can claim recording continues, and another Start returns early.

Controlled reproduction: a fake source starts successfully, returns `-1`, and signals the coordinator's
Stop call. At that point the facade still reports Recording. This is an actual failed regression assertion,
not an inference from UI appearance. One state owner must publish internal terminal failures.

### S6 — P1: writer and UI mutate a non-thread-safe timeline concurrently

Coordinator editor/refresh methods are `@Synchronized`, but `runWriter():257` calls
`audioTimeline.retainedRanges(...)` without acquiring that monitor. Its timestamp is sampled earlier at
`245`. Main can advance `ContextRetention` between sampling and use; `current()` rejects the writer's
now-stale timestamp. The worker has no catch for this `IllegalArgumentException`.

Controlled reproduction pauses the writer immediately after sampling time 10010, performs Refresh at
10020, then lets the writer continue. It throws **`Elapsed time must not move backwards`**. The recent
`maxOf` guards in Stop only address one entry point, not this shared-state race. Serialize all timeline
commands and clock sampling under one ownership scheme without putting file IO under Main's lock.

### S7 — P1: screenshot-only API29 now unnecessarily requires audio permission

`MediaProjectionConsentActivity.checkPermissionsAndRequest():35–42` requests RECORD_AUDIO for every
API29+ projection. The holder's `isRecording` purpose never reaches this activity; its intent contains only
the request ID. Legacy API29 screenshot-only scans use the same consent path. Denying audio permission
therefore prevents existing screenshots/OCR even with sentence recording off. Preserve separate consent
capabilities/purposes while sharing the projection, as required by the compatibility plan.

## Spec

These eight findings are the independent Spec review, kept separate from Standards and in its report order.
Quotes refer to the binding plan, not a new design proposal.

### F1 — P1: preview and exported edits diverge

Spec: **“Preview and export agree.”** `currentEdit` is assigned only at editor opening
(`AudioEditorController.kt:102`); Play/Seek use that original edit at `210–215`, `232–234`. JS sends changed
selection/cuts only when adding (`audio-editor.js:991–997`), while Play only sends cursor/mode. After trim
or cuts, the user can hear the old suggestion and save a different montage. Send/use an identified immutable
edit revision for preview, seek, silence results and export.

### F2 — P1: Refresh does not make continuation accessible

Spec: **“Append newly retained sound on the right.”** Native Refresh sends `totalFrames` but no new
`sourceEnd` (`AudioEditorController.kt:274–288`). JS `updateTimeline():568–577` changes `totalFrames`
while the viewport, summary and selection constraints still use the old `sourceEnd`, set at opening (`456`).

Reproduced with the existing DOM harness: open at 480000 frames, refresh to 960000, resulting state is
`{ totalFrames: 960000, sourceEnd: 480000 }`. Newly captured tail cannot be selected even though it exists.

### F3 — P1: encode failure leaves the editor workflow stranded

Spec: **“Encoder/workspace failure … retry or Without audio.”** JS closes the editor immediately before
calling native Add (`audio-editor.js:995`), reproduced in the DOM harness. On export failure, native only
deletes the output and shows a toast (`AudioEditorController.kt:333–335`); it neither reopens the editor nor
resolves its continuation. Native audio ownership remains active, while dictionary content is displayed
again and the bubble may remain hidden. Keep an explicit Exporting/Error UI until a terminal result.

### F4 — P1: an old asynchronous export can finish another note's continuation

Spec: **“stale … callbacks cannot alter … target.”** `edit()` overwrites shared `activeContextId` and
`activeContinuation`; export uses a shared scope with no operation ID (`AudioEditorController.kt:85,
201–202,326–332,354–358`). After JS hides editor A on Add, dictionary actions become available. Opening B
can replace that continuation, after which A's queued completion reads B's mutable fields and supplies A's
file to B. A's original coroutine can remain suspended. This is a reachable race established by source;
no real collection was modified to demonstrate it. Reject concurrent operations/stale completions by identity.

### F5 — P1: Anki configuration changes do not cancel the pending note

Spec: **“Cancel/reset … on destination/model/template/update-policy changes.”** No fingerprint/observer
guards the pending note. After editor completion `PopupNotes.kt:448` calls `AnkiNotes.add`, which rereads
the deck/model/templates (`AnkiNotes.kt:236–249`). A user changing destination while editing can send the
pending note to a new configuration instead of cancelling as agreed. Freeze identity for validation and
explicitly invalidate pending work on relevant changes, without making unrelated appearance/default changes destructive.

### F6 — P1: rotation destroys rather than rehosts the editor context

Spec: **“Rotation … preserve source, edits … rebuild presentation only.”** `OverlayController.kt:465–470`
docks on resize. Dock/`closeScan():833–838` hides the window and releases context, without an audio-editor
preservation/resolution path. The normal dictionary lifetime has not been separated from the active note
editor's lifetime. Retain its draft and original allowance across presentation recreation.

### F7 — P1: Anki media-copy error loses the retryable draft

Spec: **“show error and offer retry or explicit Without audio.”** Returning Failed from
`AnkiNotes.kt:264–265` correctly avoids silent note creation, but `PopupNotes.kt:468` then deletes the encoded
file in `finally` and only reports a generic error. There is no retained operation/Retry/Without audio step.
This concerns the new pre-note audio-copy contract, not the explicitly deferred generic Anki uncertain-commit redesign.

### F8 — P2: saved silence settings are disconnected from the editor

Spec: **“Search immediately with remembered parameters.”** `recordingSettings` is passed into the
controller (`53`) but never read. Search uses local 1.5 s/150 ms defaults (`65–66`); editor changes only update
those fields (`259–261`) and are not persisted. Changing global settings has no effect on editor search.

## Additional verified gaps, separate from the two reports

- **P2: silence threshold can classify even the louder region as quiet.** `SilenceDetector.kt:41`
  accepts contrast ratios >=2, but the classification threshold at `45` can be 2.5 times the noise floor.
  With background RMS0.1 and a clear one-second louder region RMS0.22, the gate passes while the threshold
  exceeds every window. A controlled five-second fixture proposed cutting `[150,4850)` ms, including the
  complete louder region at `[2000,3000)`. Low-contrast input must abstain or preserve the higher-energy
  signal, not propose one near-total exclusion. Suggestions still require acceptance; this is not an
  assertion that every low-energy waveform can be semantically identified as speech.
- **P2: duration changes wait for recording restart, not just the next scan.** `SentenceRecorder.start():57`
  reads limits once; `PcmStorageCoordinator` never receives later changes. `ScanAudioTimeline.updateLimits`
  has no production caller. Settings UI updates DataStore only. Existing contexts should stay frozen, but new
  contexts in the same running capture must admit the new H/R/E settings.
- **P2: waveform resolution is inadequate at high zoom.** Opening and Refresh compute just 200 peaks for
  the whole history; detailed drawing reuses those same peaks at all zoom levels. At five minutes one peak
  spans 1.5 seconds, at twelve minutes 3.6 seconds. No viewport-resolution request/cache exists, so fine trim
  and short pauses cannot be represented accurately. This is not an unfixable WebView limitation.
- **Resource acceptance remains incomplete.** PCM/queue caps exist, but export workspace, TTS/retry files,
  encoder/preview caches and startup cleanup are not one admitted bounded ledger. Export has a fixed 30 s
  wall deadline (`AudioExporter.kt:58`) regardless of an allowed multi-minute selection. No phone/export
  performance result in this review proves that this supports every admitted range; treat it as a remaining
  measured acceptance requirement, not a demonstrated phone-specific failure or reason to change codec blindly.

## Verification and limits of this review

- Existing `:core:media:testDebugUnitTest :core:anki:testDebugUnitTest :overlay:testDebugUnitTest` baseline:
  Gradle passed with current inputs up-to-date. Core media tests were then actually rerun successfully after
  removing the temporary extra test sourceSet. No production source or permanent test source was edited.
- All **212** existing page tests executed and passed.
- Four additional expected-behavior regressions, injected through an ignored init script, **all failed** on
  the current implementation, confirming S5, S6, the failed-start part of S3 and the silence-threshold gap.
  The writer test captures its
  own uncaught exception and controls the ordering with latches; it does not rely on a probabilistic sleep.
- Independent in-memory DOM reproductions confirm the stale sourceEnd in F2 and premature close in F3.
- No device playback, complete Android UI regression, encoder playback comparison or real Anki collection
  mutation was performed in this audit. Do not describe static reachability as a physically observed crash.
- Local reproducibility artifacts (ignored): `testdata/local-research/audio-review-2026-11-21/` contains
  `review.init.gradle`, `tests/CurrentAudioReviewReproductionTest.kt`, `reproductions.log`,
  `reproduction-results.xml`, baseline logs and restored-baseline log.

## Recommended repair boundaries

This is an assessment, not a replacement binding plan or authorization to silently change product choices.

| Keep and correct | Selectively redesign |
|---|---|
| PCM chunk storage, range/cut model, integer frame clock, bounded pool, file leases | One serialized live-session coordinator, authoritative observable state and nonblocking stop/start ownership |
| Existing MediaProjection/AudioRecord/AudioTrack/MediaCodec approach; verify actual format compatibility | Short snapshot acquisition followed by IO outside state/UI locks; per-session directories and operation-owned resources |
| Most UI markup/gestures and existing renderer | Editor operation identity/revision protocol, preview/export agreement, append bounds, Exporting/Error/Cancel state and stale-job rejection |
| Existing field rendering/inference and correctly separated sentence marker | Pending note identity/config invalidation and explicit pre-note media-failure recovery |

Start with a thin complete scenario (capture → scan → edit → preview → save) plus failure/cancel/rotation,
then repair the remaining agreed behaviors using focused integration tests. A broad framework migration or
throwing away all source files would multiply work while leaving the same concurrency/ownership questions.
Conversely, adding isolated `maxOf`, sleeps, booleans and catch-all handlers to every symptom would leave
the architectural cause in place.

### What should be replaced, and what should remain

| Area | Disposition | Boundary of the change |
|---|---|---|
| `PcmStorageCoordinator` plus lifecycle/state ownership in `SentenceRecorder` | Rewrite coordination, retain the underlying primitives | One serialized owner for clock sampling/timeline transitions and observable failures; asynchronous stop/drain; short snapshot acquisition; IO/encode outside UI/state locks; transactional source/session cleanup. Do not merely clamp timestamps at each failing call. |
| Playback lifecycle in `AudioPreviewPlayer` | Rewrite run ownership where necessary | Per-play cancellation/generation and deterministic teardown, no shared stop flag accidentally reviving an old worker; UI callbacks on Main and stale callbacks rejected. Keep the range traversal and Android sink adapter where correct. |
| `AudioEditorController`, JS/native edit protocol and pending note operation in `PopupNotes` | Rewrite the operation lifecycle | One identified draft/edit revision used by preview, seek and export; one owned continuation/job; explicit Editing/Exporting/Error/Completed/Cancelled transitions; rotation rehosts the same draft; configuration change invalidates it. Preserve existing gesture/rendering code after fixing its data contract. |
| `PcmHistory`, `AudioRange`/`AudioEdit`, `PcmClock`, bounded pool and deadline policy | Keep, integrate under the corrected owner | Existing testable mechanisms are useful. Extend contract tests for session replacement/leases, late publication and settings admission instead of discarding them because orchestration is wrong. |
| `AudioRecordSource`, `AndroidAudioTrackSink`, `AudioExporter` | Keep the platform approach; repair adapters/lifetime | Permission purpose, timestamp calibration, complete cleanup, output workspace, cancellation and measured export time/format compatibility need work. No evidence justifies replacing all platform APIs or the codec solely because UI is broken. |
| Field templates, `{sentence-audio}` separation, Anki field-omission logic and DataStore preferences | Keep most of the implementation | Fix surrounding note/config identity and explicit retry/Without audio; connect duration/silence settings to runtime use. Generic Anki uncertain-commit redesign remains outside scope. |
| Waveform/silence helpers, editor markup/CSS and gesture handlers | Correct selectively | Fix threshold/abstention and settings, use adequate viewport resolution, update appended source bounds. A wholesale Compose/native UI migration is not required to solve these defects. |

These are recommended code boundaries, not changes to agreed UX/defaults or a new owner-approved implementation plan.

### Recommended order

1. Preserve the four failing reproductions as proper regression cases when fixes begin, plus the two DOM
   failures. Establish one short complete test scenario; do not count green helper tests as feature acceptance.
2. Stabilize capture/preview lifecycle first: serialized timeline access, truthful failure state, nonblocking
   Stop, Main-thread UI callbacks, source/session ownership and screenshot-only API29 permission isolation.
   Verify read/disk/queue failure, stop/start and UI actions during recording; do not hide failures with catch-all logic.
3. Replace editor/pending-note operation ownership: stable draft/operation ID and edit revision, one active
   continuation/job, terminal result discipline, cancellation/config invalidation and rotation preservation.
   Keep the editor available on error; stale export A must never finish note B.
4. Repair actual editing behavior through that contract: current trim/cuts used by Play/Seek/Export; Refresh
   extends the selectable source without moving old edits; compare decoded output with the exact preview revision.
5. Complete pre-note Anki recovery: retain bounded audio/screenshot/draft artifacts for Retry/Without audio,
   preserve existing mixed fields as agreed, validate destination configuration and release artifacts only when
   the operation truly ends. Do not introduce automatic whole-note retries after an uncertain external write.
6. Finish secondary correctness/resource gaps: next-scan H/R/E updates, remembered silence settings and
   conservative threshold, waveform resolution, export/TTS/retry workspace/cleanup and measured long-clip support.
   Then run real overlay/player/device regressions and all affected tests/locales; visual polish follows correctness.

Acceptance should demonstrate both live mining and paused mid-utterance continuation, deliberate error/retry/
Without audio, rotation, settings change, rapid repeated Add and prolonged recording. At least one real
Android preview test must cover the outbound Kotlin-to-WebView path; jsdom alone cannot detect its thread violation.

Axis totals: **Standards 7 primary findings** (S1/S6 include process-failure paths); **Spec 8 findings**
(F4 threatens note/audio association). Additional gaps are listed separately; the two axes are not combined
into an artificial quality score or percentage complete.
