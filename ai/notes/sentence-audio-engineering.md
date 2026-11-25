# Sentence audio: implementation measurements

Implementation authority: [2026-11-19 plan](../plans/2026-11-19-sentence-audio-recording.md).
Current repair execution: [2026-11-21 plan](../plans/2026-11-21-sentence-audio-repair.md), with product
decisions inherited unchanged. Historical measurements below are evidence, not proof that reviewed
production integration defects have been repaired.
Branch: `codex/sentence-audio-recording`, started at `main` commit `5c1e5cce`.
These results are engineering evidence, not complete feature acceptance or a final codec/resource-budget choice.

## Aggregate PCM admission and low-space writes — 2026-11-26

The previous 128 MiB / 1,500-file limits belonged to each `PcmHistory`, so a stopped session with a live lease and
its replacement could exceed both in aggregate. `SentenceRecorder` now owns one process-local `PcmStorageLedger`
shared by all of its session coordinators. The numerical ceilings remain provisional; only their scope changed.
The ledger has synchronized current and high-water byte/file counters. It never chooses audio for eviction.

`PcmHistory` reserves all bytes in an append before publishing any of them and returns the unwritten balance in
`finally`, including partial-IO failure. A chunk file reservation is acquired before creation and released on
creation failure or only after confirmed deletion. Pins and protected ranges still prevent deletion. Direct shared
byte/file regressions were red before histories used the ledger. A facade regression holds an old context lease,
restarts capture, reaches the shared 400-byte fixture ceiling, observes an explicit storage-quota failure on further
input, reads the old bytes intact and returns both counters to zero after final cleanup.

Write-side free-space admission now requires `usableSpace >= minFreeSpaceBytes + nextBlockBytes`; the old reserve-
only comparison was red with 150 bytes available, a 100-byte reserve and a 100-byte block. Another test lowers free
space after one valid block and verifies exact prefix bytes after the next block is refused. Filesystem values are
injected only at the coordinator boundary; real external-full-disk races still rely on the existing IO failure path.

Final evidence: 125 core-media, 353 overlay and 291 app unit cases, zero failures/errors. Affected unit/lint/app/
test-APK/release-vital checks pass in 4m15s (`resource-storage-final-gates.log`, ignored November 25 evidence
folder). No new native, physical, endurance or local-Anki run. Queue, waveform/analysis/preview RAM, codec native
memory, export/retry/TTS workspace and the remaining cleanup/fault matrix still require their own accounting and
measurements; this checkpoint does not accept a complete resource budget or REC50/51.

## Conservative silence separation and proposal identity — 2026-11-26

The review fixture with RMS 0.10 background and a one-second RMS 0.22 region became a permanent expected-empty
regression. It failed before the repair: contrast 2.2 passed the old 2.0 gate, but the resulting 0.25 quiet
threshold exceeded even the louder region. Both manual cuts and leading-quiet refinement now share a separation
calculation that requires a 3.0 ratio between the 90th-percentile sound level and 15th-percentile noise floor.
The quiet threshold is twice the noise floor, retaining the existing 0.015 absolute minimum. This leaves a margin
between accepted sound and the threshold instead of admitting a signal that the threshold itself classifies as
entirely quiet. Digital-zero noise retains the previous absolute-threshold path.

Additional fixtures cover uniformly quiet nonzero audio, deterministic uniform noise and a valid 4:1 sound/background
pause with normal edge padding. Existing zero-silence, minimum-duration and leading-quiet cases remain unchanged.

Proposal identity is enforced at both ends. Native analysis captures source and edit revisions; Refresh cancels
analysis and a result is sent only if both identities still match. The page invalidates pending/displayed proposals
on manual edit, Undo, a changed Refresh source, a new search and Dismiss. Accept copies fresh proposals before
invalidation and appends them to, rather than replacing, existing manual cuts.

Evidence: focused detector and page regressions were red before their fixes. Final results are 120 core-media,
353 overlay, 291 app and 247 page cases with no failures. Affected unit/lint/app/test-APK/release-vital checks pass
in 4m45s. Logs are ignored under `testdata/local-research/audio-repair-2026-11-25/`. No new Android-native,
physical-source or local-Anki acceptance was run; aggregate resources and those gates remain open.

## Ready-snapshot initial refinement and in-flight edits — 2026-11-22

An actual WebView/PCM fixture holds Ready while speech arrives after seven seconds of quiet. Production
kept the old unrefined start (red, 14.022s). The owner now recomputes the first suggestion from the Ready
snapshot after enabling editing, keeping the source revision captured before analysis. It accepts the
result only once, for the same source/command revision, before edits/playback/Refresh and never empties
the selection. Confirmed previous-note choices bypass this suggestion.

A second native fixture holds an edit command in the bridge. It reproduced a client-side overwrite from
100800 to 328800 frames despite the native command guard. Ready now admits its command sequence; the
page separately tracks commands that invalidate initial suggestions. A late initialSequence cannot
replace an in-flight edit or a grabbed handle. Layout-only commands preserve the suggestion; accepted
refinement redraws the handles immediately without an extra edit command.

350 overlay and 233 page cases pass. Owner/sequence gate passes in 2m23s; final asset/lint/app/test-APK/
vital gate passes in 50s. Isolated API30 passes 17 cases in 19.777s, including both new scenarios and actual
export of the preserved in-flight edit. The two new cases intentionally wait for quiet/speech and are
not comparable to the old 15-case quick run. Package removed; owned silent headless AVD powered off.
Remaining D is bounded waveform/detail/status work; E/F/G integration and physical acceptance remain.

## Streaming codec repair and descriptor investigation — 2026-11-22

PcmAudioEncoder drives exact kept-frame input, continuous timestamps, one encoder/container pair and
cancellation checkpoints before/after native calls. Its candidate policy is 10s without observable progress
and 30s + twice selected duration overall; late progress cannot renew an already expired stall. The native
adapter polls input without waiting so ready output can drain; output waits are bounded. Constructor and
terminal cleanup attempt every owned codec/muxer release, including muxer/config/start failures.

113 core-media cases pass; latest core unit/lint/test-APK gate is codec-fd-build.log (1m12s). Actual isolated
API30 codec tests pass 3 cases in 306.08s (codec-fidelity-native.log, longAudioExport=true):
- Six seconds with two internal cuts decode to 192648 frames for 192000 kept frames (+648 / 13.5ms).
  Head/tail 2203Hz pilots and distinct kept-middle tones survive; encoded output is 21716 bytes.
- A 30-minute generated PCM stream (86400000 frames) completes in 303294ms and produces 9810093 bytes.
  Own-process PSS baseline/observed peak: 33625/57880KiB. This follows earlier construction stress,
  excludes remote codec services and does not measure the complete capture/editor/TTS feature or phone power.
- Valid encoding after 16 failed container constructions succeeds, but FDs rose from 78 to 92. A tighter
  four-batch fixture is red: [63,67,71,75] (codec-fd-native.log, 1.476s). Do not claim cleanup is complete.

Descriptor diagnosis: success-only yields the same [63,67,71,75] in 0.957s; failure construction is not
load-bearing. Extra handles are ashmem, with no encoded-file/socket/event-loop growth. Changing only
settling time from 100ms to 7s yields [63,59,59,59] (28.35s), without forced GC. Own-PID runtime logs show
Codec2 BufferPoolAccessor eviction/destruction (codec-fd-pool-retirement.log). This supports delayed
platform buffer-pool cleanup rather than leaked run-owned files/loops. Immediate total-FD equality was
the wrong regression criterion; F still must measure transient caches in the complete resource ledger.
Temporary flags/GC/category probes and strict total-FD assertion were removed; the permanent fixture
retains valid export after 16 failed constructions. No production forced GC or speculative lifetime fix.
Final affected unit/lint/app/core+overlay test-APK/vital gate passes (codec-final-gates.log, 4m46s).
Cleaned API30 rerun: 4 media tests in 3.76s (decoded fidelity, failed construction, actual AudioTrack/
Main/WebView and Main synthetic capture) plus 15 editor/crop tests in 1.945s (production Save, restart
retention, resize, Back and crop suite). Both isolated packages removed; owned silent headless AVD off.
Remaining D includes bounded waveform/detail/status scheduling and the first-selection refinement race
when speech arrives during preparation. E still owns pre-note media-copy retry, F complete resource
accounting/settings and G actual source/service/route/physical acceptance.

## PCM byte-stream integrity — 2026-11-22

Single-byte reads exposed AudioEditInputStream advancing only whole frames and therefore bypassing
cuts. It now counts relative source bytes, accepts arbitrary short/odd stream reads and keeps exact
kept-range order. EOF in required kept/excluded data is an error, not successful partial output. A
reused bounded skip buffer replaces per-skip allocation. readPcm16 fills exactly the expected complete
frames, carries odd reads and rejects zero progress/truncation; waveform and preview use it. Waveform
work checks coroutine cancellation while filling, and a missing tail cannot be drawn as fabricated silence.

107 core-media cases pass, including red/green single-byte cuts and odd-read waveform tests plus
truncated-cut and truncated-waveform checks. Core unit/lint gate passes (pcm-reader-gates.log, 52s).
AudioExporter has not yet adopted the
reader/streaming lifetime repair; native decoded fidelity and measured long-export/cancellation remain D.

## Pending source leases across capture restart — 2026-11-22

SentenceRecorder binds a context to its original coordinator and exposes an idempotent suspending
ContextLease. Acquisition is metadata-only, before Prepare can suspend or show attachment choice. UI
ownership and active-reader ownership are distinct: stopping/restarting revokes future old-scan actions,
but an already reserved note/editor can prepare/read/refresh/preview/export the stopped session. The
new ring cannot provide its continuation or recording status. A replacement scan cannot invalidate a
same-session reserved source. Revocation and reservation are atomic under a short metadata monitor;
coordinator commands and IO never run under that monitor. Final release retires its original session.

PendingNoteController has a retirement hook for resources reserved before its coroutine starts. Cleanup
finishes before releasing the slot/queued scan, including cancellation before the body runs. Menu/busy
cleanup moved to this hook too. Non-audio choices and completed encoding/omission release source work;
E must retain the source/draft through its new retryable transfer stage when that stage is implemented.
Restart clears the old scan's future eligibility without cancelling an already active editor, and config
eligibility requires an actual supported screen context rather than the global recording preference.

Current evidence: 103 core-media and 348 overlay cases pass. New facade tests verify old-session bytes
after restart, new-session independence, rejected replacement while reserved, UI revocation, idempotent
release, final directory cleanup and Close preserving a live reader. Pending-note tests cover cancellation
before preparation dispatch with deferred resource cleanup and scan ordering. Affected unit/lint/app/
test-APK/vital checks pass (source-lease-gates.log, 4m38s). Isolated API30 passes 15 tests in 2.198s;
the production editor remains open across stop/restart and exports its original confirmed edit/context.
Package removed; owned silent headless AVD powered off. F must include retained stopped-session ring/PCM in aggregate quotas;
G still requires real service/source/gesture/physical checks.

## Source coordinates and preview request ownership — 2026-11-22

Navigation uses the retained source start/span rather than the absolute cumulative frame count. Nonzero
source starts and sources shorter than the old 100 ms handle minimum remain bounded. Zoom/pan/overview,
keyboard and pointer trim/cut movement share those constraints. Gesture epochs prevent a drag begun in
an old editor or before resize from editing the replacement; zero-width layout does not divide by zero.
The overview supports continuous no-jump dragging, and a mode change while playing submits the current
cursor/cuts. Cancellation remains available even if the edit snapshot exceeds bridge packet admission.

AudioEditorOperation now owns a conflated preview mailbox: one request preparing, one latest replacement.
The desired playback state is independent of the native player's current state, so a seek during startup
is retained. Pause/edit/Save/cancel invalidate obsolete requests. A thread-safe request guard reaches the
facade and AudioPreviewPlayer, including sink startup, reads/writes and callback delivery. A separate page
preview sequence rejects already queued old progress/stop messages without invalidating playback on
unrelated presentation commands. Pause uses a small identified packet and no longer requires edit fields.

Current evidence: 101 core-media, 347 overlay and 230 page cases pass. New cases cover source-offset/short
navigation, stale and zero-width gestures, continuous overview drag, live mode changes, conflated rapid
seeks, Pause during preparation, suppressed obsolete sink startup and queued page callback rejection.
Affected unit/lint/app/test-APK/release-vital checks pass (navigation-preview-gates.log, 5m31s). Final overlay
unit/lint/native APK rebuild passes in 1m23s. Isolated API30 rerun passes 15 tests in 2.484s: production
Play/Pause via the real bridge, editor/PCM/Opus export and confirmed selection, nonzero-start WebView
navigation with compact/expanded/collapsed resize, and the crop suite. Test package removed; owned
headless/no-audio AVD powered off. These are transport/host fixtures, not actual service/source/route or
API33+ gesture acceptance. Remaining D includes live source leases across capture restart, waveform/
detail/status scheduling and codec fidelity/deadlines.

## Back, profile invalidation and scan resolution — 2026-11-22

PendingNoteController gives queued scan intent an opaque operation token. Finish runs it only after a
successful note and resource retirement; discard runs it only after cancellation cleanup. The completion
callback returns to the owner dispatcher even when the final child completes on IO. Host shutdown/context
closure/language invalidation suppress queued intent. Auto-hide occurs after releasing the slot, before
the queued scan, and checks the original scan again; stale callbacks cannot hide a later dictionary view.

Language invalidation cancels pre-write preparation. AnkiNotes now announces its collection-mutation
boundary on the caller dispatcher immediately before addNote/updateNote, after all media preparation and
fresh-target checks. Cancellation stays valid through copying; once the write is attempted the generic
acknowledgement/book/uncertain-write behavior is retained. Pre-note transfer recovery remains E.

Editor windows take keyboard focus for Back while keeping NOT_TOUCH_MODAL; SelectionHost routes only
active editor Back into the same identified dirty-cancel dialog. API33+ registers/unregisters predictive
back with generation checks. Focus, callbacks and screen-off receiver are retired with the host. A late
scan intent cannot run in a detached/non-interactive host. New scan conflicts use the existing BubbleMenu;
Finish dispatches the same identified Save or a one-shot current crop confirmation. No audio anchor is
created until the queued scan actually runs.

The identified bridge now rejects oversized edit metadata visibly before sending it, instead of leaving
the page Exporting after a dropped packet. Malformed identified commands return their phase/error. Removed
the earlier unrelated 256-cut limit; the existing bounded packet size still limits admission. Whole-feature
metadata/workspace policy is still F and is not claimed by this protocol guard.

Evidence:
- Red owner/JS cases precede fixes. Nine PendingNoteController tests cover menu ownership, single note,
  shutdown/drain, stale scan choices, success/failure, profile invalidation and acknowledgement boundaries.
- 345 overlay unit and 223 page tests pass. Focused AnkiNotes tests cover media-before-write, failed required
  media with no write, and both new/updated-note mutation announcements (last receipt in update-boundary log).
- Affected unit/lint/app/test-APK/vital gate passes: lifecycle-write-boundary.log, 3m3s. Earlier final gate
  passed in 2m18s; native-only rebuild passed in 16s.
- Isolated API30: production external Save and native key Back; compact/expanded/collapsed host with focus
  and non-modal flag checks; crop frame resize; external one-shot crop Finish: 4 tests pass in 1.979s.
  No Anki collection/system capture. Package removed and owned silent headless AVD shut down.
- Actual accessibility-service rotation, supported source apps, API33+ system gesture dispatch, calls/routes
  and physical capture/voice/power remain G. Host proxy/key tests do not certify these portions.

Next D repairs source-coordinate navigation, waveform detail and codec fidelity/deadlines. E integrates
pre-note media-copy recovery; C's full integration gate remains dependent on that UI state and G evidence.

## Pre-chooser preparation, Ready and resize — 2026-11-22

Prepare/Commit/Dismiss reserve a single note operation before the menu, with page-local random namespaces
and per-request sequence IDs. The original source provider captures the audio context ID immediately,
not when a later attachment choice asks for an image. The effective Anki target is captured from the
already displayed profile cache before the asynchronous pipeline; a fresh read is used only before a
profile has ever loaded. Source image acquisition remains lazy so opening the menu cannot request
projection permission. Closing the scan cancels unresolved preparation; ordinary completed attachment
preparation keeps the existing non-audio note-write lifetime.

Timeline preparation freezes a source revision under R. The page starts Opening and sends an identified
Ready only after rendering. Native Ready admits E once, updates available source bounds and the initial
selection, then enables audio actions. Waveform refresh after admission is optional owned work and cannot
replace edits. Duplicate Ready is ignored; R expiry during loading seals the prefix without E.

Resize keeps the existing page and operation. Owned sentence preparation prevents the normal resize→dock
path, including pending crop/preview/export. PopupController relayout handles compact/expanded/collapsed
geometry; the page redraws its waveform without resetting source coordinates, cursor/mode or viewport.
CropEditor already maps its current frame through source coordinates on layout; keeping its host alive
preserves the frame. Ordinary non-audio crop rotation still follows its prior cancellation path.

Verification so far:
- 100 core-media and 341 overlay tests pass; red JS fixtures reproduced premature operability, missing
  resize redraw, stale menu clicks rebuilding a note and identity reuse after page recreation.
- 221 page tests pass, including the final page-identity red→green regression.
- Isolated API30 native run: production Ready/editor/PCM/Opus/confirmed-selection path, host resize in
  all three presentations and pending crop frame preservation: 3 tests pass in 1.473s. Package uninstalled,
  owned no-window/no-audio AVD shut down. Actual service rotation/source-app acceptance remains G.
- Full gate in ignored ready-prepare-full.log passed in 12m38s (single worker, disabled configuration
  cache/problems report). This is a cold rebuild/environment mode, not a comparable app performance
  measurement. Checkpoint committed/pushed as 5b027fcd.
- Earlier overlapping build attempts produced damaged Kotlin incremental cache, and the Gradle report
  writer later timed out despite 99 passing tests. A targeted core-media clean/rebuild and a sequential
  --no-configuration-cache --no-problems-report run passed. No production build setting was changed.

Continue with Back/language/new-scan resolution on the same operation owner. Full transfer retry remains E,
not the already recoverable encoder failure. Physical, resource and complete matrix gates remain open.

## Editor operation redesign checkpoint — 2026-11-22

The shared continuation and untagged callbacks were replaced, not wrapped. AudioEditorOperation owns one
result, source/edit revisions, a cancellable work scope and a single encode attempt. PendingNoteController
owns the real attachment pipeline until resource cleanup completes. Cancellation cannot hand the slot to
a second note while old cleanup still runs. Ordinary non-audio note writes retain their dock behavior.

Commands carry operation identity, sequence, source revision and a complete normalized edit snapshot.
Kotlin validates and accepts the edit before Play/Seek/Save, and native replies cannot close or modify a
different editor. Refresh publishes sourceStart/sourceEnd as well as totalFrames, retaining current edits.
A Refresh/Save race returns current source state instead of leaving the page permanently Exporting.
Encoding failure retains the visible editor. Temporary output cleanup runs off Main, including cancellation
at dispatcher return boundaries. Remembered selection is promoted only after successful note creation/update.

SentenceRecorder now validates the requested AudioContextId before snapshot-backed editor analysis,
preview or export; identical coordinates in a different scan are rejected. NoteTarget captures effective
Anki settings at native Add, validates before media and again before mutation, and supplies all marker/
duplicate/template/write decisions. Preparation observes relevant configuration changes; once a note
mutation is attempted its actual result is retained. Snapshot at original Add/hold before the attachment
chooser, active-language changes and external model-change handling remain explicitly pending.

Evidence:
- Red JVM tests failed to compile before the new operation/target interfaces; red DOM tests reproduced
  premature closure, absent current-edit commands and missing Refresh source bounds.
- 5 AudioEditorOperation and 3 PendingNoteController cases cover double Save/Add, delayed export after
  cancellation, cleanup ownership, retry, source revision rejection and ordinary versus audio dock behavior.
- 3 new fake-Anki cases cover changed configuration before media, during media copy, and irrelevant
  screenshot-default/name changes. No real Anki collection was mutated.
- 98 core-media, 219 core-Anki and 338 overlay unit tests pass at this checkpoint; 215 page tests pass,
  including the ES2019 compatibility check (optional chaining/nullish syntax was rejected and removed).
- Initial required full project checks passed in 4m26s. Final checks after target/lifetime changes are
  recorded in ignored editor-final-full.log (3m34s). Final affected unit/lint/APK/release-vital gates
  pass after the last lifetime/cleanup changes (editor-final-owned.log, 42s).
- API30 isolated overlay package: LookupPageTest#identifiedAudioCommandsReachMainAndAwaitMatchingNativeCompletion
  and AudioEditorDeviceTest#exportOwnsItsEditAndOnlySuccessfulNoteConfirmationReusesIt pass (2 tests,
  1.079s initial run). Final rerun adds OwnPlaybackDeviceTest: 3 tests pass in 1.285s, including
  synchronous callback detachment before page destruction (no WebView resurrection). Actual WebView/Main/PCM/Opus
  are used; this is not system capture or physical UI acceptance. The isolated test package was removed
  and the owned headless, no-audio API30 emulator was shut down.
- Logs: ignored testdata/local-research/audio-repair-2026-11-22/editor-*.log.

Remaining C gates: Ready starts E only when operable; loading/unavailable/failure states; original
pre-chooser snapshot; source lease/rehosting across rotation including pending crop; Back/dirty Cancel;
active language; dictionary auto-hide/refined/nested updates; new-scan finish/cancel with acquisition-time
marker. E still owns pre-note media-copy recovery. Numeric workspace/resource and physical gates remain.

## TTS and shared own-output ownership — 2026-11-22

- `PlaybackCoordinator` in core:common serializes pronunciation/preview output ownership. Native release
  is required before replacement; stale releases cannot clear another lease. Capture holds cover consent
  and actual native input separately; terminal input release closes its hold even when UI state is Failed.
  No focus, pause/duck/resume commands are issued to source apps. Preview interruption reports on Main,
  rejecting a superseded preview request; full editor operation/view identity is still C.
- AudioPlayer uses operation-owned jobs, file/output handles and cancellation gates. Private synthesis
  never falls back to live speech. Failed/empty/timed-out synthesis, caller cancellation, interrupted/
  completed/failed MediaPlayer preparation/playback and late callbacks release the correct owned file.
  Downloaded clips remain owned by their cache. Startup cleanup runs once before an owned WAV is created.
- AndroidSpeechOutput serializes native IPC on one daemon lane with at most two queued tasks. Cancelled
  queued tasks are removed. Init and synthesis waits are each 5 s; Stop waits up to 2 s for IPC success
  and an actual onStop/onDone/onError callback for submitted work. Timeout retains the pending stop,
  blocks replacement and can observe a later acknowledgement; explicit failed IPC can be retried.
  Input length follows TextToSpeech.getMaxSpeechInputLength. These transport bounds are not a complete
  artifact-byte/descriptor/workspace policy; F still owns aggregate accounting and pressure measurements.
- Native baseline: AudioPlayer from `4c256f1e` reproduced the private WAV leak after Stop. Its exact
  source was temporarily built after saving this work, then current work restored in finally. No mutation
  remains. Logs: ignored `testdata/local-research/audio-repair-2026-11-22/tts-native-red.log`.
- The API30 x86 image has no installed engine. The independent library test APK supplies an authored
  TextToSpeechService in its own process, with bounded silent PCM, a valid en-US voice and engine metadata.
  It temporarily selects that engine and restores the previous setting in finally. DEFAULT category and a
  valid default voice are necessary; initial fixture failures before those fixes were not leak reproductions.
  Host launch is -no-window -no-audio -read-only; no tones, downloaded voice engine, app/collection access.
- The fixture exercises real TextToSpeech synthesis transport and MediaPlayer. It does not certify natural
  speech quality, an external engine UID's exclusion, physical audio routes or power/storage behavior.
  Final verification results are tracked in status/test-model; do not infer those gates from a fake engine.
  API contracts: [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech),
  [utterance callbacks](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener),
  [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer).
- Final software evidence: 97 core-media, 8 pronunciation and 4 shared-owner JVM tests pass; all 12 popup
  playback tests pass. A paused post-input queue drain reproduced premature restoration of live speech;
  capture protection now requires actual input release plus a terminal session transition. Queued Busy
  feedback was also red when an older preview request could still update a newer request; delivery is tagged.
- Editor entry cancels pending dictionary audio resolution/auto-play without cancelling note/duplicate work.
  Its playback predicate excludes the hosted editor, so a late lookup/refinement cannot preempt preview.
- Full required checks pass (2m29s), 212 page tests pass, and final popup/short-speech focused rechecks pass.
  Initial integration failures were test-only annotation/constructor migrations, fixed before final checks.
  Native API30 final logs: `tts-native-final.log` (TTS 1 test, 0.466 s), `tts-preview-final.log` (2 tests,
  1.451 s), `tts-own-playback-final.log` (1 test, 0.261 s). The live marker is written after the engine has
  handed off PCM; Stop awaits the real terminal callback. Shared output test checks interruption on Main.
  These timings are fixture execution, not a claimed voice/route/endurance performance improvement.
- Final TTS fixture setup preserves accessibility services with DONT_SUPPRESS_ACCESSIBILITY_SERVICES;
  its test-APK/lint recheck passes (`tts-native-test-final-build.log`). Engine selection was restored to
  null before removing the isolated packages and gracefully powering off the owned emulator.

## Terminal session storage lifetime — 2026-11-21

- Facade-owned coordinators opt into retirement after terminal input state and final scan release. The
  owner closes ring/history/queue on its own thread and terminates once native capture/Stop actually end.
  It does not run another blocking Stop attempt to retire an already terminal session. Raw coordinator
  users retain their explicit AutoCloseable lifetime for storage/probe use.
- PcmHistory notifies its owner once closed and all chunks are gone; only the coordinator deletes its
  own empty session directory. Snapshot/read-stream leases remain valid independently; final reader close
  releases their files even after owner termination. Source references are dropped on CLOSED.
- Three facade tests cover idle stopped ring cleanup with a live reader, retained stopped scan/foreign
  release/final release, and asynchronous failure without a scan. The first was red before retirement.
  The stuck-Stop regression also failed before isCaptureActive included actual input lifetime, beyond
  observable Failed. All 94 core-media tests pass. Full project checks pass in 3m24s, plus 212 page tests.
  API30 `SentenceRecorderDeviceTest` passes both methods in 1.915 s; automatic directory cleanup is now
  required before explicit close, alongside the existing real silent native preview/WebView fixture.
  Final focused test-APK/lint check passes. Evidence: ignored `retire-full-checks.log`, `retire-page-tests.log`,
  `retire-native-build.log` and `retire-device-final.log` in the review research directory.
- Remaining resource-policy work includes complete artifact accounting/cleanup failure reporting and
  real-device storage pressure/endurance; these changes do not establish final byte/time budgets.

## Capture permission purpose and immediate retry — 2026-11-21

- `CapturePermissionRequest` separates audio permission from projection authorization. Screenshot-only
  requests bypass RECORD_AUDIO, including API29; recording is unavailable below API29. Existing sessions
  obtain only missing audio permission, and a permission-only completion is never interpreted as projection data.
- Native API29 checks reproduced a reentrant stop-order defect: completing the consent before clearing
  the active projection allowed the waiter to observe the stopped session. Invalidate/clear/stop the service
  before resuming waiters on both manual and system Stop.
- Immediate retry after revocation exposed a second issue: NEW_TASK could reuse the cancelled consent
  host while it was still closing, without launching a new system dialog. Consent hosts now use independent
  NEW_TASK | MULTIPLE_TASK tasks, still excluded from recents and without retained draft state.
- Three JVM policy tests pass; overlay unit/lint, app and test-APK assembly pass. The isolated API29
  `CapturePermissionDeviceTest` obtains actual frames, denies/retries runtime permission and exercises
  revocation during pending permission. It starts real AudioRecord while capturing a frame, without reading
  or retaining audio samples. This is not real-source capture/codec/route or physical-device acceptance.
- The former existing-projection early return was restored temporarily: the native regression became red
  because no runtime dialog appeared. The targeted mutation was removed before final verification.
- API29 system dialogs are visible in screenshots/server accessibility state while sometimes absent from
  UiAutomation's node tree. The permission adapter waits for the foreground GrantPermissionsActivity and
  uses native Tab/Enter events with VIRTUAL_KEYBOARD/SOURCE_KEYBOARD and current uptime. Events with
  the default unknown source were rejected. No coordinates, arbitrary source-app interaction or force-stop.
- Logs: ignored `testdata/local-research/audio-review-2026-11-21/consent-*.log`. Install the isolated
  `com.vpr.screenlate.overlay.test` package fresh for this fixture: its initial audio permission must be denied.
  Preserve the owner app/collection. Keep REC2/3/6/82 N until all their platform/UI portions pass.
- Final API29 fixture passes in 3.527 s, including fresh screenshot-first and audio-first projection
  start orders, real frames during AudioRecord and after audio Stop, plus revocation/retry. Focused final
  overlay JVM/lint, app and test-APK assembly pass (`consent-final-build.log`, `consent-device-final.log`).

## Preview ownership and short native output — 2026-11-21

- `AudioPreviewPlayer` owns a generation per request, cancellation/sink per run and one retiring slot.
  Stop/pause/flush precede replacement; a blocked old read/output declines replacement instead of growing
  workers/AudioTracks. Natural and error paths release output; failures are typed and delivered on Main.
- Preview uses accepted-byte accounting and native played frames, not queued/read frames, for source-time
  position. Output writes are nonblocking, no-progress waiting is finite (provisional 2 s), and the genuine
  audible tail is drained before completion. Retirement waits retain the existing provisional 500 ms budget.
- `AudioTrack` streaming may not start below its device-specific minimum. API30 reproduced OUTPUT_STALLED
  for a short fixture after the 500 ms fixture succeeded. One bounded native buffer of trailing zero padding
  fixes this; padding is transport-only and cursor/source/export frame counts exclude it. See
  [AudioTrack play/start threshold](https://developer.android.com/reference/android/media/AudioTrack#play())
  and [played-frame counter/reset contract](https://developer.android.com/reference/android/media/AudioTrack#getPlaybackHeadPosition()).
- Head sampling uses a cancellable timed condition wait on the playback worker (up to 50 ms), without
  periodic native Main messages. Outgoing progress coalesces to one latest pending update. A fake-clock
  regression reproduced 1,000 queued progress tasks before the fix and verifies the bounded delivery afterward.
- `SentenceRecorderDeviceTest` passed both methods on host-silent API30: production constructor/Main executor,
  real AudioTrack and actual WebView callbacks at 24,000 / 240 / 1 source frames. Entire class: 2 tests,
  1.705 s. Sources are synthetic silence; this is not speech fidelity, route, policy or capture compatibility.
- Core media has 91 passing unit tests, including 12 preview tests. Full build/unit/lint/test-APK/convention/
  release-vital checks pass; app localization validation includes the new message in all 27 locales.
  Current logs: ignored `testdata/local-research/audio-review-2026-11-21/preview-*.log`.
- Pending: consent purpose, TTS/own-playback arbitration, stopped owner retirement, C's edit/operation/view
  protocol and incoming-command coalescing, and the remaining resource/device/codec acceptance matrix.

## Session ownership repair — 2026-11-21

- `PcmStorageCoordinator` now has one background owner for control commands, clock advancement, PCM
  publication and timeline retention. Capture only supplies a bounded buffer pool. The command queue is
  also bounded (32); production facade calls serialize lease/control admission rather than exposing it to UI.
- `SentenceRecorder` is suspending; startup, source construction in overlay, stop, snapshot acquisition,
  waveform/silence work and export no longer run or hold the facade monitor on Main. Capture state is
  emitted by the actual session, including asynchronous read/writer failures, Starting and Stopping.
- Sessions have independent neutral directories. One initial abandoned-PCM sweep precedes this singleton's
  first capture; restart does not delete a live prior reader's files. Snapshot readers still own their leases.
- Stop uses a dedicated bounded-lifetime attempt. After the provisional 2 s wait budget it reports
  `SOURCE_STOP_FAILED`, keeps the valid source/context and refuses replacement input until the old source
  really releases. This bounds caller waiting; it does not claim Android can forcibly terminate a stuck
  native method. At most the retiring source/stop workers remain; no new source is layered over them.
- Permanent regressions cover failed/cancelled startup, failure propagation, exact samples under concurrent
  Refresh, previous-session readers, responsive/stuck/throwing Stop, preserved context on rejected restart,
  next-scan H/R/E and abandoned-file cleanup. Red/green logs live under ignored
  `testdata/local-research/audio-review-2026-11-21/repair-*.log`.
- API30 isolated `SentenceRecorderDeviceTest` passed on a host-silent read-only emulator: Main caller starts,
  scans, reads actual synthetic PCM files and stops; source methods assert off-Main. No projection, AudioRecord,
  AudioTrack, audible output or collection was involved. The owned emulator was powered off afterward.
- Remaining B gates: preview run ownership, real outgoing WebView thread test, API29 consent purpose,
  complete TTS cleanup/self-exclusion and real-source/route/lifecycle checks. C–G and full resource/codec
  acceptance remain open; this repair does not validate them implicitly.

## Reproducible harness

`tools/audio-probe` is conditionally included with `-Pscreenlate.audioProbe=true`. It builds independent
recorder/source packages, not variants of the installed Screenlate app. See its README. The source emits
440 Hz, the recorder simultaneously emits 997 Hz and excludes its own UID. The recorder keeps synthetic
PCM, encoded output, decoded PCM and metadata in private files; no microphone, network or Anki data is used.

The English-only harness is not linked into the shipped app. It uses project convention plugins and the
current target SDK. A codec-only fixture encodes 480,123 mono frames with a distinct 2203 Hz marker in the
last 480 frames; it produces no audible output. Local artifacts/logs live in ignored
`testdata/local-research/sentence-audio/`.

The first source was erroneously left audible after capture. The owner reported the continuous emulator
tone; the owned API30 emulator was shut down gracefully and its process exit was verified. The harness now
limits each tone to 30 seconds and releases the output automatically. Subsequent sound-generating tests
must use a host-silent backend and explicit cleanup. `-no-audio` was then verified on this API30 image:
capture still returned 480,000 frames in 10,003 ms, external-tone amplitude 5,935.26 versus excluded-own
amplitude 0.237. AudioFlinger confirmed the source tracks were removed and no live record client remained.
The current owned emulator uses that silent flag; do not start another audible source for routine tests.
Do not mute or change unrelated host applications. [Emulator audio options](https://developer.android.com/studio/run/emulator-commandline).

## Playback and codecs: Android 11 / API30

Environment: `Screenlate_API30_glide`, x86 guest on Windows; both probe packages target SDK37.
Source and recorder are separate UIDs. Recorder uses one audio-only projection and mono PCM16.
Projection consent was approved explicitly. These are emulator results, not phone/flash/power measurements.

| Measurement | 48 kHz | 44.1 kHz |
|---|---:|---:|
| Requested captured duration | 10 s | 10 s |
| Captured frames | 480,000 | 441,000 |
| Captured PCM bytes | 960,000 | 882,000 |
| Elapsed capture | 9,998 ms | 10,010 ms |
| External 440 Hz amplitude | 5,948.20 | 5,930.98 |
| Excluded own 997 Hz amplitude | 0.230 | 0.202 |
| Largest synchronous file write | 167 us | 324 us |
| AAC-LC 64 kbit/s output bytes | 83,105 | 83,109 |
| AAC encoding wall time | 1,626 ms | 1,330 ms |
| Decoded AAC frames | 479,232 | 440,320 |

Own-tone leakage is near the synthetic analyzer's residual floor relative to the external tone. This verifies
UID exclusion for local AudioTrack output only. It does not verify external TTS engines or every route/source.
AudioTimestamp with BOOTTIME returned success. The complete capture→scan timestamp calibration is still pending.

### AAC end-of-stream finding

Encoder: `c2.android.aac.encoder`. Captured 480,000 frames decoded to 479,232; 441,000 decoded to 440,320.
An explicit empty MP4 EOS sample set the declared duration correctly but did not restore missing decoded
samples. Moving input EOS onto the last data buffer also did not solve it. The independent 480,123-frame
fixture produced 468 packets and only 479,232 decoded frames. Its final distinct 10 ms marker was absent:
best tail-marker correlation amplitude about 171 versus about 5,730 in the source fixture.

This is a measured failure of the current AAC probe/encoder combination, not a claim that all AAC encoders
drop speech. Do not ship that path, blindly concatenate AAC pieces or accept declared container duration as
proof of sample preservation. A portable AAC solution must demonstrate priming/drain/padding behavior and
retain the end marker; merely changing muxer metadata is insufficient.
[MediaMuxer EOS duration contract](https://developer.android.com/reference/android/media/MediaMuxer#writeSampleData(int,%20java.nio.ByteBuffer,%20android.media.MediaCodec.BufferInfo)).

### Opus candidate

Encoder: `c2.android.opus.encoder`, 48 kHz mono, 32 kbit/s, Ogg container. The same 480,123-frame fixture:

- 501 packets, 54,446 output bytes, 4,124 ms encoding wall time.
- 480,648 decoded frames (525 frames / 10.94 ms extra tail); extractor duration 10,013,500 us.
- Final marker preserved: measured correlation peak about 6,236 near frame 479,640 versus source about
  5,730 near frame 479,627; the lossy-codec amplitude difference is not normalization.

Opus is promising because this probe preserves the source tail and is smaller. It is **not finally selected**:
verify quality/latency, source rate negotiation, first-frame/last-frame fidelity across devices, and local
AnkiDroid/desktop Anki playback first. The Ogg muxer is available from API29, fitting feature availability.
[MediaMuxer output formats](https://developer.android.com/reference/android/media/MediaMuxer.OutputFormat).

## PCM storage benchmark on Android

Production-independent storage primitive: `core:media/PcmHistory`. Instrumented class:
`com.vpr.screenlate.core.media.PcmHistoryDeviceTest`, separate package
`com.vpr.screenlate.core.media.test`; it does not instrument or stop the accessibility-service app.

The test writes **simulated** 30 minutes of 48 kHz mono PCM16 in one-second chunks as fast as possible.
It retains a twelve-minute protected range plus a later disjoint five-minute ring; it does not wait thirty
minutes or run OCR/playback/encoding. Its injected 128 MiB / 1,100-file ceilings are **test parameters**,
not final production budgets. It validates the old first chunk, descriptor stability and final cleanup.

API30 result, 2026-11-19:

| Metric | Result |
|---|---:|
| Logical bytes written | 172,800,000 |
| Peak retained bytes | 97,920,000 |
| Peak chunk files | 1,020 |
| Wall time | 1,016 ms |
| Largest append | 1,619 us |
| Test-process PSS before / after | 23,773 / 27,582 KiB |
| Open descriptors before / after | 52 / 52 |
| Files/retained bytes after release | 0 / 0 |

Writes use ordinary buffered filesystem behavior without per-chunk fsync: crash recovery is excluded.
This measures emulator page-cache/file behavior, not physical flash latency/endurance or power. PSS is a
pair of samples, not a full peak-allocation trace or a claim about whole-app memory under OCR load.

## Implemented invariants and remaining gate

Implemented and unit-tested: H/R/E deadline transitions (no timer-dependent admission or renewed E),
immutable source-coordinate edits, normalized cuts and time mapping, initial selection/refinement,
frame-aligned fixed-prefix file publication, independent snapshot/reader leases, ring/protected disjoint
retention, byte/file quotas, truncated-source rejection and partial-write rollback with terminal writer failure.
Fresh-directory validation prevents unaccounted abandoned files from bypassing the byte ledger. The first
milestone had 26 passing JVM tests plus the separate Android storage test. Full project assemble/unit/lint/androidTest
assembly/convention tests/release vital lint passed; all 195 page tests passed. The optional probe variants
also build/lint without errors (four development-only lint warnings: API annotation/constant, backup and icon).

### Protected timeline and bounded capture buffers

The next independent foundation adds `PcmClock`, `ScanAudioTimeline` and `PcmBlockQueue`; total **44 passing
JVM tests**, module lint and instrumented-test assembly. This is not a connected Android recorder/editor.

- Integer BOOTTIME→frame conversion avoids floating-point drift and intermediate multiplication overflow.
  Actual timestamp-origin calibration remains a backend responsibility and a device gate.
- Session/scan IDs reject old callbacks. Scan-time H/R/E admission, fixed displayed revisions, explicit
  append-only Refresh, late pre-cutoff publication, stopped-source preservation and no new E on reopening
  have deterministic tests. The old source and a later ring remain separate retained ranges.
- Integrated tests drive that policy against real temporary PCM files through long reading, Refresh/cap,
  disjoint ring retention and a subsequent scan, verifying sample bytes and cleanup rather than just metadata.
- The capture-to-writer pool preallocates a bounded number of frame-aligned buffers. Reusing the producer's
  input cannot mutate accepted data; a borrowed writer block cannot be recycled until returned. Overflow
  or frame discontinuity seals producer input and leaves the accepted prefix drainable; it cannot silently
  resume across dropped sound. Normal stop drains, cancellation closes, returned leases are idempotent.
  Concurrent producer/writer tests check every frame with reused input buffers.

Live capture must use these primitives through one serialized coordinator. A pool by itself does not prove
AudioRecord scheduling/overrun behavior, dispatch readiness, actual timestamp accuracy, or final resource
admission. Those remain explicit integration tests, not implied by the 44 unit tests.

### Platform checks: API 29 shared projection and API 37 host resources

Environment:
- API 29: `Screenlate_Audio_API29`, x86_64 guest on Windows with WHPX; both probe packages target SDK 37.
  Tested with `--ez screenshot true`: one `VirtualDisplay` with `ImageReader` drained images while capturing
  audio through the **same** `MediaProjection` session. Silent test run with `-no-audio`.
- API 37: `Pixel_10_Pro`, `android-37.2/google_apis_playstore_ps16k/x86_64` (16k page size image).

API 29 shared capture result (2026-11-19):

| Metric | Measured API 29 result |
|---|---:|
| Captured duration | 9,997 ms |
| Captured frames | 480,000 |
| Captured PCM bytes | 960,000 |
| External 440 Hz amplitude | 2,066.03 |
| Excluded own 997 Hz amplitude | 0.47 |
| Screenshot frames drained | 74 |
| Screenshot dimensions | 1080 × 1920 |
| AudioTimestamp status | 0 (success) |
| AAC encoder | OMX.google.aac.encoder |
| Max synchronous write | 325 us |

This runtime result confirms that on Android 10 (API 29), a single `MediaProjection` session can concurrently
service both legacy screenshot acquisition (`VirtualDisplay`/`ImageReader`) and internal playback capture
(`AudioPlaybackCaptureConfiguration`) with own-UID exclusion.

API 37 finding:
- The official 16k page size image (`ps16k`) mandates at least 4096 MB RAM (`Increasing RAM size to 4096MB`).
- On Windows with ~62 GB committed memory, launching this 4 GB guest image + netsim causes host commit-memory
  exhaustion (`System.OutOfMemoryException` in the host shell).
- This is a host environment memory constraint, not an Android playback capture incompatibility.

### Storage coordinator, waveform downsampling, silence detection and single-encoder export

Added `PcmStorageCoordinator`, `AudioSource`, `AudioRecordSource`, `WaveformSummary`, `SilenceDetector`, and `AudioExporter`:
- `PcmStorageCoordinator` ties together live `AudioSource` input, the bounded `PcmBlockQueue`, `PcmHistory` file storage, and `ScanAudioTimeline` policy on separate capture and writer threads. Quota exhaustion, disk stalls, or queue discontinuities stop capture honestly and seal the queue, preserving the accepted contiguous prefix. Startup cleanup removes abandoned `.pcm` / `.tmp` files.
- `WaveformSummary` computes downsampled peak envelopes directly from chunk streams without full PCM decodes or UI allocations.
- `SilenceDetector` implements both >5 s leading-quiet detection with 150 ms margin for initial selection refinement, and >=1.5 s internal silence proposal cuts with 150 ms edge preservation and dynamic-range abstention.
- `AudioExporter` streams kept frames from `AudioEdit.kept` directly into Opus/Ogg via `MediaCodec` and `MediaMuxer` on API 29+, with presentation timestamps derived from cumulative kept frames.

Total **55 JVM unit tests** pass in `core:media`. Full project assemble, unit tests across all modules, and release vital lint pass cleanly.

Still needed before feature acceptance/production UI:
external TTS exclusion, route/lock/call/revocation tests, actual sources, complete timestamp calibration,
final numeric disk/RAM/workspace/cache budgets, overlay editor UI matching the approved mockup,
DataStore settings and Anki pipeline integration.

Primary API checklist rechecked 2026-11-19: [playback eligibility/permission/filter rules](https://developer.android.com/media/platform/av-capture),
[foreground-service prerequisites](https://developer.android.com/develop/background-work/services/fgs/service-types#media-projection),
[session consent and revocation](https://developer.android.com/media/grow/media-projection),
[AudioRecord](https://developer.android.com/reference/android/media/AudioRecord).
