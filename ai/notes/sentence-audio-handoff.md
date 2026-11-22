# Sentence audio implementation handoff — 2026-11-19

Latest codec continuation (2026-11-22): exact PCM driver/native adapter, continuous kept timestamps,
cancellation, deterministic construction/finalization and separate10s progress/30s+2x duration overall
limits are implemented. 113 core/348 overlay/230 page cases pass; final affected gate passes4m46s.
Actual API30 decoded pilots/cuts and30min stream pass (303294ms,9810093 bytes, own PSS peak57880KiB).
Final4 media+15 editor/crop tests pass3.76s/1.945s. Both packages removed; owned silent headless AVD off.
FD investigation is resolved: transient ashmem from Codec2 caches, settled [63,59,59,59], native eviction
logs; all temporary flags/GC hooks/category probes removed. It is not a whole-feature resource certificate.
Next D waveform/detail/status and initial refinement when speech arrives during preparation/Ready; E
pre-note transfer retry with retained draft/source; F aggregate resources/settings; G actual device/source/
service/route/gesture acceptance. Continue autonomously without yielding; no product decision changed.

Latest PCM continuation (2026-11-22): kept-range streaming now counts bytes, preserving single-byte/
odd reads across cuts; truncation fails instead of returning partial success. Shared readPcm16 fills
complete expected frames for waveform/preview and permits cancellation. 107 core-media cases and core
lint pass (52s). Next adopt this in the streaming encoder with deterministic resource ownership,
cancellation and measured deadlines/native decoded fidelity; finish D waveform/status detail, then E/F/G.
Source retention predecessor 3418a9ce is committed/pushed with the old-editor-after-restart native receipt.
No emulator is running. Continue autonomously; do not interpret the primitive gate as new device acceptance.

Latest source-lifetime continuation (2026-11-22): Prepare reserves the original source before suspension;
UI revocation and consumer retention are independent. Capture restart/Close preserve live old-session
readers; new scans use a separate session and cannot replace a reserved same-session source. Pending-note
retirement cleans pre-dispatch reservations/busy UI before queued scan intent. 103 core-media/348 overlay
cases and affected unit/lint/app/test-APK/vital checks pass (4m38s). Isolated API30 passes 15 tests in
2.198s, including original-editor export after restart. Package removed; owned silent headless AVD off.
Continue D PCM/codec integrity and waveform/status detail. E must extend the source/draft through its
new media-copy retry UI; current source work closes after successful encoding/explicit omission. Then
F aggregate resources/settings and G actual service/source/device acceptance. Continue without yielding.

Latest continuation (2026-11-22): navigation uses retained source coordinates and stale gesture guards;
preview preparation is conflated with desired playback intent and native/page request guards. 101
core-media, 347 overlay and 230 page tests pass; affected unit/lint/app/test-APK/vital checks pass.
Isolated API30 passes 15 tests in 2.484s, including actual bridge Play/Pause and nonzero-start navigation/
resize. Package removed, owned headless/no-audio AVD powered off. Continue D's active source leases across
capture restart, waveform/status detail and codec fidelity/deadlines, then E/F/G without yielding.
Lifecycle predecessor 7d3be01f is already committed/pushed; do not reimplement it. Product decisions stand.

Current resume override (2026-11-22): use the [repair execution plan](../plans/2026-11-21-sentence-audio-repair.md),
[review](sentence-audio-review-2026-11-21.md) and latest status. The owner authorized selective redesign of
session coordination and editor/pending-note ownership. Core A/B work now implements a serialized session
owner and suspending facade with permanent regressions; B's software lifetime work is implemented and
verified. Continue C's operation/revision protocol; physical gates remain. The checkpoint below is historical. Branch, privacy, safe test
environment and weekly-limit instructions remain applicable. No product decision was changed.

Lifecycle continuation (2026-11-22): see the top ai/status.md section. C's Back/language/new-scan owner
integration and affected gates/native rerun are implemented; the final 32/32 AnkiNotes boundary run
also passes. Commit/push the lifecycle checkpoint, then proceed. Then continue D/E/F/G autonomously, without yielding. Base 5b027fcd is
committed/pushed with full/221-page/3-native receipts. Latest lifecycle evidence: 345 overlay, 223 page,
4 native cases (1.979s), affected unit/lint/APK/vital pass (3m3s). E's media-copy recovery and G's real
service/physical/API33+ gesture portions remain; do not equate transport fixtures with full acceptance.

Latest preparation/Ready/resize checkpoint (2026-11-22): Prepare/Commit/Dismiss now freeze note data and
target before the attachment menu; page-recreation tokens cannot collide. Loading uses R; identified Ready
admits E once. Owned crop/editor/preview/export resize keeps the host, source coordinates and draft.
See the newest engineering/status sections for the full gate receipt (running at checkpoint creation).
The owner now requests autonomous continuation through all remaining repair milestones, without yielding
between blocks. Continue C's Back/language/new-scan arbitration, then D/E/F/G. Do not interpret passing
host-resize fixtures as full service/physical acceptance.

Latest editor checkpoint (2026-11-22): old shared editor continuation and primitive bridge are removed.
AudioEditorOperation and PendingNoteController are connected to the actual pipeline, with current-edit
commands, source revisions, one export, visible encode retry, target snapshot at native Add and confirmed-
note-only selection reuse. Read the new engineering section and repair plan's resume paragraph. C remains
partial: pre-chooser snapshot/Ready/rotation/crop/language/Back/new-scan arbitration are next; do not restore
the old globals or interpret passing unit/codec fixtures as complete overlay acceptance.

Previous core checkpoint (`341bd599`): 84 core tests (11 new), 212 page tests and isolated API30 Main-thread synthetic
session test pass. Full project checks passed; the last retired-session no-op fix has focused core/overlay
unit, core lint and test-APK checks. No actual playback/capture/Anki was exercised in this repair milestone.
Previous preview checkpoint: 91 core tests, full build/unit/lint/test-APK/convention/release-vital checks and
two API30 device tests pass. Real AudioTrack + production Main callbacks + WebView handle 24,000 / 240 / 1
silent source frames. Preview owns run cancellation/retirement/drain and bounds outgoing progress. See
engineering evidence for the reproduced short-stream stall and transport-only padding.
Consent-purpose checkpoint: screenshot-only requests bypass audio permission; existing projection obtains
missing audio permission without replacing screenshot capture. Stop clears the generation/session before
waking waiters; independent ephemeral consent tasks prevent immediate retry from reusing a closing host.
Three policy JVM tests and isolated API29 real permission/revocation/frame/AudioRecord initialization checks
pass; see engineering evidence and ignored consent logs. Native fixture requires a freshly installed library
test package with RECORD_AUDIO denied. This does not certify actual source audio or physical routes.
Terminal-session retirement is implemented: facade-owned stopped/failed sessions release their worker/ring
after the last context; live leases survive and remove their files/directory on final release. 94 core tests
pass. A still-stopping native input keeps isCaptureActive true even in Failed. Full required checks and
212 page tests pass. The strengthened API30 Main cleanup and real preview tests pass (2 tests, 1.915 s);
final test-APK/lint passes. See `retire-*.log` and status/test-model.
TTS/shared-playback software repair is implemented with 8 pronunciation, 4 coordinator and 97 core-media
JVM tests; 12 popup playback tests pass. Editor entry cancels pending dictionary audio and prevents a
late lookup from stealing preview. Full required checks and 212 page tests pass, with final focused checks.
Four native API30 tests pass (TTS cleanup/live-stop barrier; real preview/Main/WebView/cleanup; shared
MediaPlayer/AudioTrack with Main interruption). See `audio-repair-2026-11-22/tts-*.log` and latest status.
Continue C after the current checkpoint commit. The isolated silent TTS engine does not certify external
engine UID, physical route or natural-voice quality. C must guard the now-suspending
editor opening/Refresh/analysis paths with operation/revision identity before claiming UI acceptance.

## Resume contract

Continue the existing feature, not a new design discussion. Read root `AGENTS.md`, the owner's memory index,
`ai/status.md`, [the binding implementation plan](../plans/2026-11-19-sentence-audio-recording.md), and
[engineering evidence](sentence-audio-engineering.md). Read the older chronological
`video-audio-mining-context.md` only for a specific unresolved historical point. Product decisions are settled.
Do not re-ask them or convert old proposals/prototype trial settings into defaults.

**Owner's latest operational instruction:** check account usage with `mcp__codex_app__get_usage_limits` at
the beginning and between meaningful steps. Prefer `rateLimitsByLimitId.codex`; the weekly window has
`windowDurationMins = 10080` (normally `secondary`). Remaining percent is `100 - usedPercent`.
If remaining weekly usage is **less than 2%**, stop implementation immediately at the current point. Do not
finish another phase/build, infer quota from context tokens, buy credits or consume a reset. Keep this
checkpoint current before long work; report unavailable usage honestly. This explicit threshold supersedes
the older memory note's 80%-used stopping instruction. Usage is account-wide and can change in other chats.

The owner requested the handoff/prompt now so another agent can resume in a fresh session. No automatic
continuation or quota-reset action is requested. Actual account IDs/usage snapshots do not belong in this repo.

## Branch and checkpoints

- Worktree: `D:/Documents/ClaudeProjects/Screenlate`; branch **`codex/sentence-audio-recording`**.
- Base: `main` at `5c1e5cce` (complete accepted plan, 74 REC cases).
- `51f0d44a`: bounded PCM storage, deadline/edit/initial-selection foundation and isolated playback/codec probes.
- `e2e92de6`: scan timelines, frame clock, bounded reusable capture queue and integrated/concurrent tests.
- The handoff checkpoint after those commits includes the build-checked API29 screenshot/audio probe and
  API34+ full-display consent configuration. Resume from the **current HEAD of this feature branch**.
- Both earlier implementation commits were pushed to `origin/codex/sentence-audio-recording`. Keep commits
  and pushes on this branch. The owner's specific separate-branch instruction takes precedence over the
  generic project main-push default. Do not merge into main as routine cleanup.
- Unrelated pre-existing state must remain untouched: untracked content inside submodules
  `core/ocr-mnn/src/main/cpp/MNN` and `dictionary/engine-hoshidicts/src/main/cpp/hoshidicts`, plus the two
  `docs/images/photo_*2026-11-10*.jpg` files. Do not add them to commits.

## Implemented, tested, not connected to app UI

`core:media` is an independent Android library, using project conventions, currently without app/overlay/Anki
dependencies or live recorder/DI/settings integration. Internal helpers are not the final public facade.

| File | What exists |
|---|---|
| `AudioRange.kt` | Half-open Long source-frame ranges, immutable normalized outer/cut edits, original/result mapping. |
| `RecordingLimits.kt` | Validated immutable 5/5/2 minute H/R/E; 30 s–10 min, 30 s step; API29 availability predicate. |
| `ContextRetention.kt` | Absolute monotonic deadlines; E replaces R only at first in-time operable opening; terminal seals/no renewal. |
| `InitialSelection.kt` | Scan−15 s / scan+5 s clipped to availability; >5 s confident leading-quiet refinement with 150 ms margin; all-quiet unchanged. Classification itself is not implemented. |
| `PcmHistory.kt` | Mono PCM16 files; committed fixed prefixes, snapshots/readers with independent leases, disjoint keep ranges, byte/file limits, failed-write rollback, corruption rejection and fresh-directory validation. |
| `PcmClock.kt` | Integer BOOTTIME/frame conversion with safe intermediate arithmetic; actual device-origin calibration remains pending. |
| `ScanAudioTimeline.kt` | Session/scan identity, scan-time frozen limits, independent ring/protected ranges, fixed shown revisions, explicit Refresh, late pre-cutoff publication, stop preservation, stale-callback rejection. |
| `PcmBlockQueue.kt` | Preallocated bounded capture buffers. Overflow/discontinuity seals producer input, leaves accepted prefix drainable; normal stop drains; leased buffers cannot be reused early. Live capture must act on failures. |

**44 JVM tests pass**, including `ScanAudioHistoryTest` (actual files plus timeline/cap/ring policy) and
`PcmBlockQueueTest` (concurrent producer/writer with reused input, every frame checked).
`PcmHistoryDeviceTest` passed in its **own** library test package on API30. No Screenlate/Anki collection was
modified. None of this means the recording feature can already be used from the application's settings/popup.

## Results and unresolved gate

Detailed measurements are in the engineering note; do not substitute estimates for real evidence.

- API30: 10 s internal playback capture at 48/44.1 kHz works, external 440 Hz tone present, own excluded
  997 Hz tone near residual floor. Confirms local AudioTrack UID filtering, not external TTS engines.
- Current `c2.android.aac.encoder` probe loses the last partial block/end marker. Explicit MP4 EOS duration
  and last-input EOS did not fix it. Do **not** ship this probe's AAC path or assume declared duration proves fidelity.
- Opus/Ogg 48 kHz mono/32 kbit/s preserved the end marker; fixture decoded with ~10.94 ms extra tail.
  It is a candidate, **not a finally selected codec**. AnkiDroid/desktop/physical-device quality and compatibility
  remain required. No gain/normalization/codec UI is approved.
- Android PCM test accelerated 30 source minutes: 172.8 MB written; 97.92 MB max retained/1,020 files;
  descriptors 52→52; final files/bytes zero. PSS samples 23,773→27,582 KiB. This is emulator cached IO,
  not physical flash/power/thermal or whole-app OCR measurement. The test's 128 MiB/1,100-file ceilings are
  injected **test values**, not final production budgets.

The owner heard the first source tone because it was accidentally left running. It was stopped. `ProbeTone`
now stops/releases within 30 seconds. **Every subsequent sound-generating emulator must use `-no-audio`.**
That flag was tested on API30: internal capture still produced 480,000 frames/10 s with external-tone
amplitude ~5,935 and own ~0.237. Source removal/no live record client verified afterward. Do not repeat
audible tones, change unrelated host mixer settings or leave helpers running between sessions.

## Last exact working point: API29 and API37 platform checks

Last code edits, already built/linted successfully:

1. `tools/audio-probe/ProbeActivity.kt` forwards optional `--ez screenshot true`; on API34+ requests
   `MediaProjectionConfig.createConfigForDefaultDisplay()` for the all-source consent path.
2. `ProbeService.kt` optionally creates **one** virtual display with an ImageReader (max two images), drains
   images and records only frame counts/dimensions, while capturing audio using the **same** projection.
   No screenshot pixels are written. Teardown releases display/reader.
3. README documents those switches. Normal runs remain audio-only (no display).

These additions are **build/lint checked, NOT runtime verified** on API29/API37. Next meaningful task is
testing them, then recording results before making production capture/consent decisions.

Environment prepared:

- Installed SDK package `system-images;android-29;google_apis;x86_64` (revision13). No need to download again.
- Created AVD `Screenlate_Audio_API29` under ignored
  `testdata/local-research/sentence-audio/avds/Screenlate_Audio_API29.avd`; set process-local
  `ANDROID_AVD_HOME` to the absolute `.../sentence-audio/avds` before starting it.
- API29 launch **failed** Windows commit-memory admission: ~61,013 MB committed / 62,712 MB limit,
  only ~1,699 MB remaining versus 2,048 MB requested. This is a host resource failure, not audio incompatibility.
  Do not retry repeatedly or close unrelated user processes. Run one owned AVD at a time after resources permit.
- API37 AVD name `Pixel_10_Pro`, system image `system-images/android-37.2/google_apis_playstore_ps16k/x86_64`.
  Its original populated QCOW overlay is corrupt: **never boot/wipe/repair it** as part of this feature.
  An independent base copy exists at
  `testdata/local-research/sentence-audio/api37-fresh/userdata.img` (copied from the small 10,158,080-byte
  `C:/Users/vpr/.android/avd/Pixel_10_Pro.avd/userdata-qemu.img`, not its corrupt overlay).
  Always pass explicit `-data <owned path>` and `-cache <owned path>`, `-no-snapshot`.
- API37 experiment remained ADB-offline and did not yield a test result. Its owned process **PID13292 was
  stopped at handoff**, after executable-path verification. Its new test disk may be an incomplete first boot;
  inspect or use a fresh separate owned copy if necessary. Do not overwrite the original AVD data.
- API30 owned AVD was gracefully shut down to free memory. No audio-generating source is intentionally left running.
- `emulator-5554` is the pre-existing **API24** instance: leave it alone. Last new experiment serials were
  5556 (API29 failed) and 5558 (API37 offline/stopped); always inspect `adb devices -l` first, not assume reuse.

Direct headless backend needs child PATH entries:
`D:/Android/Sdk/emulator`, `emulator/lib64`, `emulator/lib64/gles_swiftshader`, `emulator/lib64/vulkan`.
Executable: `D:/Android/Sdk/emulator/qemu/windows-x86_64/qemu-system-x86_64-headless.exe`.
Start hidden with logs redirected under the ignored run directory and flags
`-no-window -no-audio -no-snapshot-save -no-boot-anim -crash-report-mode never` (API37: `-no-snapshot`).
Do not use `am force-stop`; even app instrumented tests require the documented isolated package.
Close only owned helpers, gracefully where possible; do not stop shared Gradle/other agents' processes blindly.

## Commands, artifacts and validation

Host shell PowerShell. Use JDK21: `$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'`.
Default sandbox launch is broken (`CreateProcessWithLogonW ... 1920`); approved shell calls have used
`sandbox_permissions: require_escalated`. Do not bypass automatic review errors.

Passed full baseline at the first milestone:

```text
./gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest :build-logic:convention:test :app:lintVitalRelease --console=plain
npm --prefix scripts/page-tests ci
npm --prefix scripts/page-tests test
```

195 page tests passed. After timeline/queue changes, relevant module unit/lint/Android-test assembly passed:

```text
./gradlew.bat :core:media:testDebugUnitTest :core:media:lintDebug :core:media:assembleDebugAndroidTest --console=plain
./gradlew.bat '-Pscreenlate.audioProbe=true' :tools:audio-probe:assembleDebug :tools:audio-probe:lintRecorderDebug :tools:audio-probe:lintSourceDebug --console=plain
```

Quote the `-P` argument in PowerShell; bare dotted property was split incorrectly. Probe `lintDebug` is
ambiguous because it has source/recorder flavors; use the two explicit lint tasks. Probe has no lint errors,
four known development-only warnings (API requirement annotation/constant, backup and icon).

Probe APKs: `tools/audio-probe/build/outputs/apk/{source,recorder}/debug/`.
Library test APK: `core/media/build/outputs/apk/androidTest/debug/media-debug-androidTest.apk`.
Library test runner: `com.vpr.screenlate.core.media.test/androidx.test.runner.AndroidJUnitRunner`.
Probe packages: `com.vpr.screenlate.audioprobe.source` and `com.vpr.screenlate.audioprobe`.
Grant recorder RECORD_AUDIO, start source then recorder, approve fresh system projection consent. Source
expires after 30 s; prepare permissions/UI first. No force-stop, no Anki login/sync or owner collection edits.
Use screenshots for coordinates on each API; API29/API37 screen geometry differs from API30's 1080×2280.

Important ignored logs under `testdata/local-research/sentence-audio/`:
`full-checks.log`, `page-tests.log`, `timeline-checks.log`, `platform-probe-build.log`,
`pcm-device-test.log`, `pcm-benchmark-api30.properties`, `result-48000*.properties`,
`fixture-aac-final-eos.properties`, `fixture-opus.properties`, `sdk-api29-install.log`,
`avd-api29-create.log`, `api29-emulator.*.log`, `api37-emulator.*.log`.

## Remaining implementation order

1. Finish feasible platform/resource gate: API29 shared screenshot/audio and modern audio-only consent/capture,
   proper stop/revoke behavior and timestamp-origin calibration; external TTS-file/own-UID path; real-device
   source/route/power checks explicitly remain pending if no phone is connected. Record evidence honestly.
2. Complete `core:media` facade/coordinator: actual AudioRecord producer and writer, deadlines/source-time
   publication, final resource ledger/admission/reserves/cleanup, configuration observation and lifecycle state.
   Do not mistake the standalone queue/timeline tests for this live integration.
3. Production consent/FGS and legacy projection ownership: current overlay `MediaProjectionHolder`/Service
   serve API24–29 screenshots, and permission is maxSdk29. Coordinate API29 ownership; preserve API30+
   accessibility screenshots, and API24–28 availability-only recording UI.
4. Preview/waveform/silence classification and single-encoder export with actual decoded fidelity; then approved
   compact/expanded overlay editor, pass-through/Back/rotation/dirty handling and no renewed allowance.
5. DataStore settings/backup/reset, all27 locales, shared default-editor toggle, hold-menu attachments/duplicate
   actions, separate `{sentence-audio}`, explicit media-error retry/Without audio and mixed-field preservation.
6. Full REC/device/fault/endurance acceptance and docs. Phases1/2 are still partial; others have not started.

Do not expand into ASR, player auto-pause/resume, standalone sharing, microphone fallback, gain controls,
crash recovery or generic Anki transaction redesign. The full binding decisions, tests and acceptance gates
are in the plan; the approved mockup path/hash is recorded there.
