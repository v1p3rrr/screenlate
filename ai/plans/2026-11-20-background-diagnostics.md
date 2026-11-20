# Background service diagnostics and performance preservation

## Decision table

| Topic | Decision |
|---|---|
| Scope now | Add diagnostic logs; defer every application optimization, including changes to existing HUD/About polling. |
| Availability | Expanded monitoring only for debuggable Dev builds, including debug-signed minified builds explicitly marked debuggable. |
| Sampling | State events plus one resource sample per minute on a background thread. No alarm, wake lock, network request or sleeping-device wakeup. Coalesce event samples. |
| Privacy | No recognized text, lookup terms, sentence/note contents, app navigation URLs or third-party foreground app identities. Fixed labels, own process metadata and numeric measurements only. |
| UX invariant | Keep OCR models and WebView; preserve OCR quality, dictionary results and warm opening latency. No System.gc(). |
| Later idle work | Only docked, popup closed and no active scan, after at least 10-15 seconds of grace, or on explicit screen-off. Do not disrupt recording, notes or other legitimate work. |
| Audio crash | Separate sentence-audio branch owns reproduction/fix; this task supplies exact crash evidence and a handoff. |
| Workspace | Isolated worktree from main; do not modify the audio session's dirty checkout or merge its feature commits. |

## Current implementation

1. Preserve process name, real/package UID and last-sampled memory semantics in exit records. Select the latest main-process exit for About without discarding isolated exits from the exported history. Do not label every USER_REQUESTED exit a force-stop.
2. Add Dev background monitoring: service connection, keep-alive request/actual foreground lifetime, dock/scan/popup/catcher/WebView transitions, screen on/off and power-policy events. Include monotonic time alongside log wall time.
3. Collect main-process PSS categories, RSS, native allocation, Java heap, CPU deltas, elapsed/awake intervals, thread/descriptor counts and system memory availability. Read power/idle/battery-exemption/background restriction/standby state where supported. Mark unavailable values; do not claim access to private OEM policy or separate renderer metrics.
4. Keep events in the existing persistent bounded journal and include a current snapshot in normal log export. A killed process cannot log its own final callback: the following launch appends historical exit records.
5. Unit-test process attribution and measurement timing/coalescing. Build/lint affected modules. Validate capture/export and release gating on an isolated emulator package; real PowerGenie cleanup remains a physical-device observation, not an emulator result.

## Preliminary optimization proposals, not approved changes

| Candidate | Evidence needed | Preserved behavior / acceptance |
|---|---|---|
| Share HUD/About samples and move existing memory reads off main | Main-thread sampling duration and counts | Same displayed metrics; no added scan or popup delay. |
| Suspend diagnostic UI work at screen-off / accepted safe idle | Idle CPU deltas and actual attached state | Immediate fresh sampling on return; no model/WebView lifecycle change. |
| Gate existing MNN intermediate-buffer trim by accepted safe idle | Actual allocation savings, next-scan timing | Models remain loaded; no loss of OCR quality; do not increase warm latency. |
| Pause a hidden WebView instance without destroying it | Hidden-page CPU and paired resume timing | No global pauseTimers, no frozen Search, no lost DOM/history, no added opening delay. |
| Control heavy result payload work | Candidate count, serialization/render timings and payload sizes | Same ordering, definitions and lookup results; no arbitrary candidate cap. |
| Catcher A/B observation | Matched workloads, screen/power events and natural system exits | Setting-off tests active catcher only; window-presence test needs a build that never attaches it. No automatic rollback or behavior change. |

## Hypotheses and discriminating evidence

1. OEM inactivity/classification differs from overlay interaction: compare actual screen/idle/standby state and recorded bubble use before natural exits.
2. Extra window or active catcher affects OEM classification: compare matched runs with active catcher disabled, then (only if needed) an explicitly approved no-catcher-window build.
3. Resource history influences cleanup: compare interval CPU/memory and actual foreground lifetime. A force-stop description does not expose the proprietary selection threshold.

## Changelog

- 2026-11-20: Owner selected Dev-only expanded logs, state events plus minute samples, and logs-only implementation. All optimizations remain proposals. Existing models, WebView and runtime behavior are binding invariants.

## Audio crash handoff

The owner's log records a main-process CRASH at 2026-11-20 11:22:36.056, PID 27795. The Java exception is
`IllegalArgumentException: Elapsed time must not move backwards`. Full R8 map ID:
`51ac93180a48325512f259a01a522886257ff53ceb5c50c301b1f775e5bb8336`.
The retained `screenlate-dev-c7e9b08e-min.mapping.txt` and `screenlate-dev-377c2125-min.mapping.txt` both match it.
Mapped path: bubble-menu recording-stop action -> OverlayController.stopSentenceRecording -> SentenceRecorder.stop ->
PcmStorageCoordinator.stop -> ScanAudioTimeline.stop -> ContextRetention.advance (line 54).

Investigate an earlier stop timestamp being applied after the writer has advanced retention while shutdown drains
accepted PCM. The monotonic clock itself is not shown to regress. Reproduce with a deterministic producer/writer/stop
ordering test, preserve the accepted prefix and source-time cutoff contract, and fix in the sentence-audio branch.
Do not hide the invariant failure with an unconditional clock clamp or change audio retention rules without evidence.
The force-stop log was supplied separately; this crash has no PowerGenie description. This diagnostics branch does
not contain core:media and does not implement the audio fix.

## Verification checkpoint

- core:common: 615 unit tests, 0 failures/errors, 3 existing skips.
- app: 300 unit tests, 0 failures/errors, 1 existing skip.
- overlay: 330 unit tests, 0 failures/errors.
- Debug lint passes in all three affected modules.
- BackgroundDiagnosticsDeviceTest passes on API 30 in its independent instrumentation package; real process fields
  reach the bounded log and a release-context flag gate refuses startup. No owner app/Anki access or force-stop.
- Physical-device natural-cleanup capture and warm-latency/endurance acceptance remain pending.

## Dev artifact

- Implementation commit: e8124d7b, pushed to origin/main without merging the sentence-audio branch.
- APK: `testdata/local-research/builds/screenlate-dev-e8124d7b-min.apk` in the original checkout.
  Package com.vpr.screenlate.debug, version 1.1.0-background-diagnostics-min (10100), ARM64 only, debuggable.
- SHA-256: `662A7FD1078EC05BAE768BB1AD0A4D9AE447E078BEA2549DA3C0FD857E1D3696`.
- Certificate SHA-256: `3d48893b7563cfa738335edcd9bcd0f148e4d76ef4086511b6eefb2d4e7c3d22`, matching earlier local Dev builds.
- This R8 build emits a partitioned mapping archive, not mapping.txt. Preserve the original
  `screenlate-dev-e8124d7b-min.mapping.prt` (21,260 class partitions plus metadata) for partition-aware retracing.
  Archive SHA-256: `2E324C463F0C57FBEA08A6289396ECD82080383D790C6D5B231828114C3290E2`.
- The app was not installed on the owner's phone. After a natural disappearance, reopen the app and export the
  complete log promptly; the new launch appends the historical main-process exit and any available prior state summary.

## Final verification and cleanup

- The ARM64 Dev APK builds successfully with R8 and the expected Dev signing certificate/debuggable flag.
- Normal release vital lint passes. Five existing real-WebView rendering/bridge/font tests pass on API 30.
  This page-only fixture excludes MNN JNI, which it does not call: the optional 32-bit x86 native build fails with
  incompatible elf_i386 objects in upstream MNN. ARM64 native assembly passed; no MNN source changes were made.
- Independent test packages were uninstalled and the owned read-only, host-silent emulator was shut down.
- Temporary source junctions used for native builds were removed; the source checkout's submodules and audio work
  remain untouched. A future fresh worktree build requires the usual submodule initialization.
- Physical-device cleanup selection, battery impact and warm-latency/endurance measurements remain acceptance work;
  neither the emulator smoke nor added logs establish a fix for the proprietary PowerGenie selector.
