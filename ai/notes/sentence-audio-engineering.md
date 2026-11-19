# Sentence audio: implementation measurements

Implementation authority: [2026-11-19 plan](../plans/2026-11-19-sentence-audio-recording.md).
Branch: `codex/sentence-audio-recording`, started at `main` commit `5c1e5cce`.
These results are engineering evidence, not complete feature acceptance or a final codec/resource-budget choice.

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

Still needed before feature acceptance/production UI: API29 shared legacy projection and current-API capture,
external TTS exclusion, route/lock/call/revocation tests, actual sources, complete timestamp calibration,
live producer/coordinator integration with the bounded queue, final format and numeric disk/RAM/workspace/cache budgets,
preview/encode integration and fidelity, near-full storage/pressure tests with other app work, and physical
device power/thermal/OCR latency. The foundational module is not yet connected to app settings or the popup.

Primary API checklist rechecked 2026-11-19: [playback eligibility/permission/filter rules](https://developer.android.com/media/platform/av-capture),
[foreground-service prerequisites](https://developer.android.com/develop/background-work/services/fgs/service-types#media-projection),
[session consent and revocation](https://developer.android.com/media/grow/media-projection),
[AudioRecord](https://developer.android.com/reference/android/media/AudioRecord).
