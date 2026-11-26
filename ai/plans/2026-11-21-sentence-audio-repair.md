# Plan: sentence audio integration repair (2026-11-21)

Status: implementation in progress. The owner authorized selective redesign of recording-session
coordination and editor/note-operation coordination, following the review order. Work stays on
`codex/sentence-audio-recording`; no routine merge or push to main.

## 1. Authority, checkpoint and scope

- This is the current **execution plan** for repairing the implementation reviewed at `cfc637fa`.
  The [2026-11-19 product plan](2026-11-19-sentence-audio-recording.md), especially its complete binding
  decision table, remains the **product specification**. All those decisions are inherited, not reopened.
  This plan supersedes its implementation sequence and historical progress/resume instructions only.
- The [review](../notes/sentence-audio-review-2026-11-21.md) defines S1–S7, F1–F8 and four additional gaps.
  Review documentation was committed as `2e7ef150`; it did not fix production code.
- [Engineering evidence](../notes/sentence-audio-engineering.md) distinguishes measured probe results
  from unverified compatibility/resource claims. The [handoff](../notes/sentence-audio-handoff.md) remains
  useful for environment/safety constraints; its old unconnected-feature checkpoint is historical.
- The approved prototype remains authoritative for interactions. Its SHA-256 was rechecked on 2026-11-21:
  `3f8c742709e1d0d60c230f819b44858b3b66f7ab924b384342e4c340fde576ce`.
  Local path: `C:/Users/vpr/.codex/visualizations/2026/11/18/01a1ef6c-c252-7673-bd2b-cf18f181effc/screenlate-audio-editor.html`.
  Trial values or persisted prototype state do not change the agreed 5/5/2 defaults.
- No unanswered product question blocks this plan. Threading, internal names, codec verification and
  measured numeric resource budgets are engineering work already delegated by the product plan.
  Ask before changing agreed behavior/defaults or if measurements make the promised behavior infeasible.

Keep Kotlin, Hilt, DataStore, project convention plugins, Android playback-capture/playback/codec adapters
and the WebView editor approach. Keep useful PCM, range, deadline and queue mechanisms after checking their
interfaces. Replace ownership/orchestration, rather than building another facade over conflicting owners.
Do not introduce a media framework, database of drafts, microphone fallback, ASR, subtitle tracking,
automatic source-player controls, audio gain UI or general Anki transaction reconciliation.

## 2. Binding behavior carried into the repair

The following checklist makes the most easily lost decisions visible beside the repair work. It supplements,
and does not abbreviate away, the original decision table and failure contract.

| Area | Required behavior |
|---|---|
| Platform and source | API 29+ only; all eligible internal playback except this Screenlate UID, no microphone or foreground-app restriction. Source/profile/usage restrictions may yield silence. App minSdk remains 24. |
| Unsupported devices | On API 24–28 show only Android 10+ availability text in the recording section. Hide recording controls, both default-editor copies and audio menu options; block every activation path. Existing screenshots/pronunciation continue. |
| Activation and indication | Manual Start/Stop from Audio settings and bubble hold menu, one actual runtime state, static red dot and accessible text, truthful system notification with Stop. No automatic restart or consent restoration. |
| Scan association | Reserve history at actual new screen/app-text acquisition. OCR completion, refinement, later screenshot crop, word movement and nested lookup do not create another anchor. Search/camera/photos have no ring attachment. |
| Time controls | Global H/R/E defaults 5/5/2 minutes, each 30 seconds–10 minutes in 30-second steps, three adjacent controls. Admit all values at scan time; settings changes apply to subsequent scans even without capture restart. |
| Retention | Independent H ring; protected history plus continuous R waiting; one E allowance from first **operable** editor opening only if R has not sealed. Unused R is not added to E. Silence consumes time. |
| Exhaustion | Keep valid prefix and edits. Ring may continue separately. Refresh exposes unseen retained tail once, then explains the cap on every further press. Never renew E or join a missing interval. |
| Stop/restart | Stop preserves eligible existing source; no further continuation. Restart creates a new session; old scans cannot acquire new-session audio/actions until a manual new scan. Existing live readers remain valid until released. |
| Add pipeline | Freeze target before chooser/async work. Default-editor setting starts on; ordinary Add follows screenshot then audio defaults when eligible and a used template contains the marker. Hold menu preserves Open/Update/Add anyway and applicable screenshot/audio/both choices. |
| Source view | Initial source extends through operable opening; shown view stays fixed while recording continues. Refresh appends right without moving existing trim/cuts, viewport reference or untouched initial selection. Save uses the shown revision. |
| Initial selection | Scan−15 s through scan+5 s intersected with available source. Only first suggestion: confidently quiet leading interval >5 s moves the left edge to 150 ms before distinguishable sound; all-quiet/uncertain stays unchanged. |
| More notes | Offer last confirmed range/cuts from the same scan, adjustable; cancelled/abandoned edits do not replace it. No renewed E and no changes to previously saved notes. |
| Editing | Outer handles plus reversible shaded internal cuts, normalized in original frame coordinates; create/select/resize/remove and undo. No inferred linguistic sentence boundaries. |
| Navigation | Tap seeks; background drag pans; playhead dot drag seeks without panning; pinch/buttons zoom. Thin playhead plus attached dot disappear together offscreen; no filled seek track. Overview tap and continuous drag preserve grab offset. |
| Preview and silence | Result default, Original comparison; same source/edit as export, unity gain, phone media volume. Search inside current outer range using remembered 1.5 s minimum/150 ms edge margins; reversible suggestions, optional listening, abstain on uncertain separation. |
| Editor presentation | Dictionary hidden; compact popup expands/moves/collapses as the same draft. Outside touches reach the player. Body scrolls, footer and Refresh remain accessible. Rotation preserves draft/view/playback state and clocks. |
| Cancel and scan | Cancel/Back cancels the whole pending note; confirm only changed boundaries/cuts. Without audio is explicit. Another scan first offers finish/cancel current editing; failed finish preserves it and must not execute the new scan. |
| Settings layout | Global Recording sentences card outside pronunciation sources; recording/default-editor/H/R/E/silence/info. Shared default-editor copy immediately below screenshot default in Anki. No static Anki field row or new field setup screen. |
| Default-editor explanation | Explain normal Add opens the editor; when off, choose audio in the Add hold menu; capture/eligible source is required. Stopped protected source has an explicit retained-audio status. |
| Anki fields | Separate `{sentence-audio}` and existing pronunciation `{audio}`. No used marker means no editor/encode/transfer. New inference/presets only; no automatic old-mapping migration/prompt. Reuse Fill empty only/Replace all. |
| Updates | Respect existing duplicate and Keep/Replace/Append policy. Explain ineffective new audio before editing when Keep retains existing sound. Without audio preserves affected existing fields, including the whole mixed text+audio field, with notice. |
| Errors and settings changes | Pre-note encode/copy failure offers Retry or explicit Without audio. Relevant effective Anki/profile/config changes cancel pending work; unrelated appearance/default/duration changes do not. No automatic retry after an uncertain note mutation. |
| Quality and lifetime | Mono moderate speech quality, no separate clip-length cap inside retained history. Quiet output warns but can save; zero-frame output cannot. Bounded private temporary files/RAM/work, no draft/capture crash recovery; back up settings only. |

### Owner device feedback and approved UX direction (2026-11-24)

- Preliminary owner-device evidence: capture and attachment to an Anki card succeeded on the compact
  minified development build. Preview audibility is uncertain; do not mark it passed or failed.
- Compact editor keeps waveform, listening, Refresh and terminal actions readily available; secondary
  cut/silence tools are disclosed separately. Avoid reducing finger targets to make the layout fit.
- Owner explicitly wants both directional gestures (vertical scroll over waveform background versus
  horizontal waveform navigation) and a visibly usable free scroll area. Thin residual gaps are insufficient.
- Reproduce cut-edge collisions/escape after outer trimming, zoom and undo, including multiple cuts.
  Keep source-frame semantics and reversible editing; screen geometry must match the effective edit.
- Two page regressions cover wholly/partially out-of-phrase cuts and undo. Whole-cut case is red before
  the fix (2 visible handles expected 0); handle visibility and drag limits now respect the phrase.
  All 239 page cases pass. Further collision/real touch/Android checks remain; no new APK delivered.
- Music request means proposing removal of whole music-only gaps between speech, not denoising the
  voice. Optional low-priority investigation only, with conservative abstention and reversible proposals;
  no heavy neural models, automatic destructive deletion or promised music/speech discrimination.
- Next: visual compact/expanded layout and actual vertical/horizontal touch arbitration, button audit,
  source-vs-result preview audibility and conservative silence analysis. Do not describe these as already fixed.

## 3. Repair architecture and ownership

### Recording-session module in `core:media`

The external interface remains small: observe actual state, start/stop, acquire/release a scan context,
open/refresh a shown source, and preview/analyze/export an immutable edit revision. Callers do not need
to know worker threads, chunk paths, lock order or codec lifetime. Use typed session/context/revision IDs,
explicit leases and typed failures. Internal adapters for clock, source, storage and sinks have real
production and test implementations; avoid speculative interface layers.

Replace the lifecycle/state parts of `SentenceRecorder` and `PcmStorageCoordinator` with one serialized
background session owner. Prefer existing coroutine/worker facilities; exact class names may follow the
surrounding code. Capture reads feed a separate bounded PCM queue. Timeline transitions, storage
publication/retention metadata and acquisition of leases execute in one ordered ownership scheme;
the writer cannot independently mutate `ScanAudioTimeline` from another thread.

- Sample processing time when the serialized transition executes. Keep actual acquisition/sample timestamps
  separately: an old event's timestamp is data, not permission to move current state time backwards.
  Never fix this by moving the scan anchor to dispatch/OCR time. Keep elapsed/BOOTTIME conversion explicit.
- No synchronous file scan, waveform/silence pass, codec call or worker join on Main. Snapshot pinning is
  short/ordered; long reads and encoding run outside shared state locks using leased immutable prefixes.
  A dedicated file worker may do IO, but its backlog and capture queue are bounded and fail honestly.
- Commands/control completion and PCM publication must not form an unbounded second queue. Coalesce
  replaceable Refresh/progress work; preserve terminal errors and Stop. Avoid polling sleeps and blind
  timestamp clamps as a substitute for ordering.
- Stop rejects further producer input, unblocks capture, drains the already admitted contiguous prefix,
  seals the context and releases handles exactly once. Waiting for blocking platform calls is asynchronous
  to Main. Restart cannot overlap a still-owned source or reuse a revoked projection token.
- Publish Starting/Running/Stopping/Stopped/Failed truth from the owner; settings, bubble, notification
  and TTS coordination consume it. Failure in capture/writer/storage immediately becomes observable.
- Each session gets its own neutral directory. Transactional startup owns/cleans the source even if any
  start step fails. Prior session leases survive a new session; abandoned cleanup never deletes live files.
- Preserve separate admitted cutoff, committed end, retained end and shown end. Late pre-cutoff frames
  can publish; later frames cannot enter a sealed context. Partial-file snapshots have fixed valid lengths.
- Apply updated duration preferences to future scan admission and unprotected ring policy without evicting
  leases or mutating an existing context's H/R/E. A history increase cannot fabricate earlier audio.

Retain `PcmHistory`, `AudioRange`/`AudioEdit`, `PcmClock`, queue and deadline logic where their contracts
remain sound. Adjust them if they currently conflate historical anchor time with transition time. Test
observable behavior through the session interface; keep useful mathematical/byte-integrity tests, replace
tests that merely mirror obsolete orchestration.

### Editor and pending-note module in `overlay`

`PopupNotes` owns one pending note operation from Add activation through a terminal result. Its identity
contains frozen entry/sentence/language/scan/action, attachment choices and a configuration fingerprint.
The editor is a stage of that operation, not an independent mutable global continuation.

Suggested states: Preparing → Screenshot → Editing → Exporting → Transferring → Saving → Completed,
with explicit FailedBeforeWrite and Cancelled. Skip unrequested stages. Keep capture/context/presentation
states separate: a sealed context may still have a valid editor and a running independent ring.

- One operation ID, one active job/continuation and one terminal result. Duplicate Add cannot replace it.
  All async results carry operation/session/context/source/edit revision identities as relevant; stale
  results are rejected and their owned resources released. Cancellation never completes a different note.
- Kotlin owns the validated immutable draft revision; JS renders it and sends identified edit commands.
  Keep gestures responsive, but synchronize/acknowledge their final revision before Play/Seek/Save.
  Native validation remains required for ranges, cuts, integer coordinates and source availability.
- Preview, seek and export consume the same accepted edit revision. Original comparison uses that outer
  source range without applying cuts. Source-to-output mapping remains deterministic.
- Define bridge messages for Ready/Edit/Play/Pause/Seek/Refresh/Silence/Save/Cancel and their results;
  do not let unrelated callbacks overwrite shared fields. Full PCM never crosses the WebView bridge.
- Dispatch every outgoing WebView/window mutation on Main, including progress, completion and failure.
  A destroyed/rehosted page cannot accept callbacks for its old view generation.
- Define first operable-opening acknowledgement so delayed construction/analysis does not grant E early.
  R is normalized at that instant; if it expired while preparing/cropping, no E. The initial usable shown
  snapshot and opening allowance belong to the same context; reopening/rehosting never gives a new E.
- Keep Exporting/Error visible; do not close the editor on JS Save before native success. During a frozen
  export/transfer, prevent conflicting edits/Refresh/submissions; retain Cancel only where mutation has
  not started. Before mutation, cancellation releases work; after mutation starts it cannot promise rollback.
- Dictionary auto-hide, nested/refined results, ordinary popup close, docking and rotation must respect
  active editor ownership. Rehost compact/wide/collapsed presentation without releasing its draft.
  Service shutdown stops capture and terminates/relinquishes presentation safely after live jobs release.
- Validate configuration fingerprint on relevant settings changes and before transfer/mutation. A known
  committed result remains a result, not a cancellation claim. Include external deck/model/permission errors.
- Promote last confirmed audio only from a non-abandoned successful note operation; retries preserve the
  candidate without replacing the previous confirmed proposal. No stale completion updates another scan.

### Preview and platform/Anki adapters

Rewrite preview run ownership as needed: one generation per Play/Seek/mode change, invalidated queued
samples and callbacks, deterministic stop/sink release. A shared stop Boolean cannot revive an old worker.
Retain correct range traversal and `AudioTrack` sink. Define Screenlate preview/pronunciation mutual
ownership without issuing focus/play/pause commands to the external source.

Keep MediaProjection/AudioRecord/MediaCodec/MediaMuxer and the candidate mono Opus/Ogg approach pending
verification. Separate screenshot-only consent purpose from audio permission; coordinate shared API 29
projection without revoking the other consumer. Keep API 29 classes off unsupported initialization paths.

`core:anki` accepts a typed local attachment/omission intent and returns stage-aware failures; it does not
depend on media capture internals. Keep existing marker/template/preservation logic. The pending operation
owns staging files/grants until transfer finishes or it ends. Separate retryable pre-note media work from
an already attempted provider mutation; never automatically replay the latter.

## 4. Implementation milestones, in order

Every milestone includes tests, relevant docs/test-model evidence, a coherent commit and push to the
feature branch. A temporary failing reproduction is evidence, not a permanently red default test suite.
Do not start a later milestone to hide an unresolved earlier ownership/continuity failure.

### A. Regression foundation and repair baseline

1. Verify branch/HEAD/dirty state, preserve unrelated files and record baseline test inputs/results.
2. Convert the four isolated review regressions into normal appropriate tests as their fixes land:
   asynchronous failure propagation, failed-start cleanup, writer/Refresh ordering and silence abstention.
3. Add DOM regressions for appended source bounds and editor staying open while export is unresolved.
4. Establish a thin integration fixture: fake source/clock, real temporary PCM files, editor interface,
   fake codec/output and fake Anki backend. Check exact target, selected samples and terminal ownership.
   The fake codec proves orchestration only; Android encode/decode proof remains mandatory later.

**Gate:** each bug has a reproducible expected-behavior assertion; untouched baseline is recorded. Test
counts (73 core-media and 212 page at review time) are historical references, not completion criteria.

### B. Recording and preview lifetime foundation

1. Implement the serialized session owner and short lease acquisition described above; remove competing
   timeline access and facade-wide IO locks. Separate processing clock from recorded acquisition timestamps.
2. Make startup/stop/failure transactional and observable; clean every partial-start path, stop/drain
   asynchronously, preserve accepted frames, isolate session directories and old live leases.
3. Repair preview worker generations, sink lifetime and Main-thread outbound callbacks. Cancel prior
   Screenlate playback without capturing it; clean all TTS synthesis/playback failure/interruption paths.
   Coordinate an already speaking external TTS engine before capture begins; while capture is starting,
   running or stopping, use bounded private synthesis and own-UID playback, never live-speak fallback.
   Do not restore live speech until the recorder actually stops; reject stale utterance/voice callbacks.
4. Repair permission/consent purpose, revocation and service coordination, including screenshot-only API 29.
   Cover foreground-service startup rejection, notification/system Stop and single-use/revoked tokens;
   cancelled or late consent must not auto-rescan or start a dead generation.
5. Connect a minimal working capture → scan → preview path before broader editor rewiring.

**Tests:** deterministic command interleavings; writer timestamps older than Refresh; Stop at queue/drain
boundaries; negative/dead/short/zero reads; queue overflow/stall; write/start exceptions; repeated Stop/Start;
late consent/projection callbacks; source closed exactly once; old lease survives restart; WebView calls on
Main; fast seek/play/cancel cannot leave two workers or old progress; permission denial does not block screenshots.

**Gate:** truthful terminal state reaches all observers, no UI-blocking IO/join or stale worker can crash,
and byte-exact contiguous prefix survives failure. Run an actual Android WebView preview callback test.

### C. Pending operation and editor protocol redesign

1. Introduce one owned pending note and immutable draft/edit revisions; freeze target before any chooser.
   Replace shared mutable continuation fields in `AudioEditorController`/`PopupNotes`.
2. Implement identified bridge commands/results, Main dispatch, one Save job and stale-result rejection.
3. Keep Editing/Exporting/FailedBeforeWrite UI coherent; implement one terminal completion on every path.
4. Preserve draft, source leases, playhead/mode/view state and opening clock across rotation and presentation.
   Restore dictionary only if its original context is still valid.
   During the sentence-attachment pipeline, retain pending crop/draft state as required by REC55; do not
   accidentally apply the ordinary crop-popup rotation cancellation to that owned operation. Existing
   standalone screenshot behavior remains independently covered by ANK8.
5. Implement dirty Cancel/Back, configuration invalidation, new-scan finish/cancel arbitration and service
   teardown. A queued scan is only an intent; acquire its marker when it actually runs.

**Tests:** export A completes after cancellation/new operation B; double Save/Add; stale ready/analysis/
Refresh callbacks; rotation during opening/preview/export; auto-hide/nested lookup; all relevant versus
irrelevant settings changes; cancelled screenshot/audio creates no note; failed finish does not execute scan.

**Gate:** every started operation terminates or remains visibly retryable; no callback can alter a different
note/scan/view, no rotation renews E, and no early editor closure strands native ownership.

### D. Editing behavior and sample fidelity

1. Connect current accepted revision to Play/Seek/Original/Result/Save; validate normalization and mapping.
2. Repair Refresh source bounds, stable coordinates, late committed partial tails and coalescing. Show new
   retained duration/cap reason without sliding the editor or extending the selection automatically.
3. Connect initial selection/refinement and last-confirmed proposal; reject delayed overwriting of edits.
4. Preserve approved pan/seek/zoom/playhead/overview/cut/undo gestures. Fix waveform resolution using bounded
   reusable summary levels/viewport requests; avoid rereading all history on each drag or refreshing Main.
5. Stream kept source ranges through one encoder/muxer with deterministic cleanup/EOS/cancellation.
   Replace the unverified fixed 30 s export deadline with a bounded stall/overall policy supported by
   maximum-admitted-clip measurements. Do not introduce a shorter user clip-length cap.

**Tests:** ramps/impulses/markers across chunks, cuts and repeated partial-tail Refresh; result seek into
cuts/end; rapid mode/seek; fixed sample revision during Save; leading quiet at 5 s and just above; truncated
source without joining a hole; actual decoded head/middle/tail and output duration within measured codec
tolerance. Verify several internal cuts and mid-word source continuation without lost/duplicated PCM.

**Gate:** preview and decoded export correspond to the same edit, newly appended audio is selectable,
and all approved navigation works on a real overlay, not only jsdom.

### E. Anki pre-note failure recovery and attachment pipeline

1. Keep screenshot and audio staging under the pending operation; preserve them across safe pre-note retries.
   Transfer validated audio before note mutation; keep file/grant valid for the actual copy.
2. Add explicit Retry/Without audio for encode/workspace/media-copy failures. Without audio continues other
   requested attachments and preserves existing affected fields on updates. Cancel cancels the pending note.
3. Revalidate effective target/configuration before transfer and mutation; preserve duplicate Keep handling,
   mixed-field notice, ordinary/hold attachment choices and Open/Update/Add anyway commands.
4. Clean staging/leases/grants on actual terminal paths, not a generic finally while a retry remains possible.
   Reuse known successful staging where safe; never delete Anki-owned media as a rollback attempt.
5. Preserve current generic uncertain-write/book/open behavior; no automatic whole-note retry or hidden tags.

**Tests:** fake backend failures before copy/during copy/after successful copy/before or after mutation
attempt; repeated Retry; Without audio new/update/mixed field; screenshot first then audio; marker absent;
separate pronunciation/sentence markers including both in one template; duplicate Keep/Replace/Append;
config changes/Anki permission loss/model deletion; no double note and no wrong file association.

**Gate:** requested audio never disappears behind a reported successful note, pre-note failure stays
recoverable, and local emulator AnkiDroid receives/playbacks the intended attachment without account/sync.

### F. Settings, silence and complete resource accounting

1. Wire saved silence settings into every editor and persist editor parameter changes globally. Ensure
   next-scan duration admission works without restarting capture; current contexts remain frozen.
2. Fix silence threshold/contrast abstention, preserving manual cuts and rejecting stale suggestions.
   Cover the review fixture with background RMS 0.1 / louder RMS 0.22: no near-total cut over its louder region.
   Uniform quiet/noise/music and uncertain speech must abstain rather than infer a linguistic phrase.
3. Complete settings/UI parity with the approved checklist: shared switch wording/placement, adjacent
   duration controls, no static field row, unsupported-device hiding, cap/stopped/retry statuses.
4. Finish one accounted resource policy spanning live/old leased PCM, queues, waveform/read-ahead caches,
   TTS, export and retry staging. Every file has an owner and release path; aggregate across live sessions.
   Do not introduce a reverse `core:anki` → capture dependency to share accounting.
5. Measure candidate format, bitrate, chunk/queue/cache/file/descriptor/metadata/workspace/free-space limits
   and export timing. Record constants, admission equations, high-water observations and reasons in the
   engineering note/tests. Existing 128 MiB / 1,500-file / 50 MiB reserve defaults are provisional, not accepted
   whole-feature budgets. Maximum 10/10/10 may legitimately hit an earlier explained resource cap.
6. Audit no-backup/runtime restoration, reset/restore and abandoned cleanup for PCM/waveform/TTS/exports/
   retries. Drop rebuildable caches first under memory pressure; never evict protected data behind readers.

**Tests:** threshold boundaries 1.5 s / padding 150 ms, uncertain/quiet/all-silent/music, restored invalid settings,
30-second increments/limits, all 27 locales, no auto-start, low space at every admission/write/export/retry stage,
quota versus global failure, stale files/live leases, cancelled codecs and finite metadata/undo/work queues.

**Gate:** independent limits are concrete and enforced, saved settings affect runtime as specified, no
silent cut/cap/reset discards the requested content, and measurements do not claim emulator IO is phone power.

### G. Device, endurance and full regression acceptance

1. Run the complete REC matrix plus affected existing overlay/OCR/ANK/AUD/settings/backup E/F cases.
   Add focused new cases below; update category/results only for actually executed coverage.
2. API 24–28: unsupported audio and existing screenshot/pronunciation behavior. API 29: screenshot-only
   without audio permission, both projection consumer start orders, concurrent screenshot/audio and revoke.
   API 30+ and current target: real overlay/WebView/FGS/consent, modern screenshot behavior.
3. Real sources: utterance continues while reading; video paused mid-word then manually resumed/Refresh;
   long definition reading; repeated notes from one scan; long absence past R/E; missing-middle refusal.
4. Verify self-exclusion including external-engine TTS synthesis, routes/headset/Bluetooth, zero media
   volume/source mute, calls/lock/revocation and no unintended source pause/duck/resume. Verify local
   AnkiDroid and disposable desktop Anki playback/quality, including head/tail codec tolerance.
5. Compare Off/capture/protected/preview/analysis/export on the same build/device/source: CPU, PSS/native/
   renderer memory, disk writes/unique bytes, FDs/files/jobs, seek/Refresh/OCR/lookup/bubble latency,
   power/thermal behavior. Repeated scans/notes and unattended runs must reach a bounded plateau.
6. Run build/unit/page/lint/androidTest assembly/convention/release-vital checks; test a minified build's
   bridge/provider behavior. Update architecture/development/usage, module map, engineering notes,
   test model/run log, status and handoff. NOTICE changes only if dependencies/assets actually change.
   Correct the current architecture paragraph's H/R/E unit typo (seconds instead of minutes) and
   describe verified behavior separately from remaining source/device limitations.

**Gate:** no unresolved crash, continuity loss, wrong-target/stale-result, self-capture or unbounded-growth
defect. Missing physical-device evidence stays explicitly pending and prevents claiming full acceptance.
Do not merge/release merely because helper tests or an APK build are green.

## 5. Failure and lifecycle checklist

| Trigger | Required outcome / check |
|---|---|
| Consent denial/cancel/permanent permission denial; success arrives after Stop | Honest non-running state; retry explicit; stale token cleaned; old unrelated leases intact. |
| Source init/read/dead object/format/route discontinuity | Stop/seal at proven prefix, publish actual reason, no hidden restart or fabricated silence. |
| R expires before first operable editor; old timer fires after E grant | No late E in first case; stale R callback cannot seal the valid E stage. |
| Context duration/byte cap versus global disk/queue failure | Context cap retains prefix while ring can continue; global failure stops capture. Refresh remains useful then repeatably explains exhaustion. |
| Slow IO/short writes/full disk after preflight | Bounded queues and complete frame publication; preserve valid prefix; no unbounded waiting on Main. |
| Analysis/cache/waveform fails or memory pressure | Source and manual edits remain; cancel optional work/drop rebuildable cache; retry/manual editing available. |
| Missing/truncated chunk or session restart | No concatenation through a hole or another session; old valid leases survive cleanup until release. |
| Preview seek/stop/editor cancel/rotation races | Flush old samples, ignore old progress, release sinks and leases once; no background WebView mutation. |
| Save/Refresh/silence/double Add interleave | Save owns a frozen shown revision and target; late results cannot edit it or another operation. |
| Quiet versus empty result | Quiet warning allows save/preview/omit; zero kept frames prevents empty export and allows adjust/omit. |
| Encode/copy failure before note write | Keep bounded draft and other attachments; Retry/Without audio/Cancel; no silent missing-audio success. |
| Relevant config changes or target disappears | Cancel/reset pending work and explain, or report known external write result if already attempted. |
| Without audio on update with mixed template | Preserve entire affected old field, notify; other fields follow existing policy. |
| New scan during editor/failed finish | Finish/cancel prompt; no destructive scan or replacement before successful resolution; new anchor only at later acquisition. |
| Dock versus full service shutdown/process death | Dock leaves ring; shutdown stops capture with safe live-owner cleanup; process restart clears abandoned session and does not recover/start recording. |
| Reset/restore/debug-release coexistence | No expired consent restoration, cross-build file/grant collisions, leased-file deletion or runtime audio backups. |

## 6. Resource and verification evidence

Use the original resource ledger and arithmetic, not RAM-sized full recordings. At 48 kHz mono PCM16,
default H+R+E is at most 69.12 MB protected plus a later disjoint 5-minute ring of 28.8 MB: 97.92 MB before overhead.
Maximum 10/10/10 plus a disjoint 10-minute ring is 230.4 MB before overhead. These are disk arithmetic, not approved
budgets or RAM allocations; quotas can seal earlier. Current write-rate arithmetic 345.6 MB/hour requires
real storage/power measurement. Count unique overlapping chunks once, but include old live sessions,
chunk slack, summaries and simultaneously leased input plus export/retry/TTS workspace.

Before admitting work, reserve its bounded workspace and safety margin; recheck writes because external
storage use can race the preflight. Stop/decline honestly rather than clearing source or growing files
indefinitely. Encode streaming ranges once; no full PCM copy or byte-concatenation of encoded fragments.
Document codec delay/padding separately from sample loss. Host-silent fixtures must have finite lifetime.

| Review item | Primary milestone | Required evidence |
|---|---|---|
| S1 outgoing WebView thread | B/C | Real Android Main-thread preview callback test plus stale-view rejection. |
| S2 Main IO/global lock | B/D | Worker/dispatcher checks and measured responsive Stop/Refresh during IO/encode. |
| S3 source/session ownership | B | Failed-start close exactly once; restart leaves previous snapshot readable. |
| S4 artifacts/codec cleanup | B/E/F | Every failure/cancel/completion path closes handles and bounded files. |
| S5 unreported backend failure | B | Fake-source failure reaches public state/settings/notification without explicit Stop. |
| S6 writer/Refresh race | B | Latch-controlled failing interleaving becomes safe; all deadline permutations. |
| S7 screenshot permission regression | B/G | API 29 screenshot-only denial test plus both shared-projection start orders. |
| F1 preview/export mismatch | C/D | Current trim/cuts revision audible and present in decoded output. |
| F2 inaccessible Refresh tail | D | DOM bounds plus real selectable/audible appended frames without duplicates. |
| F3 stranded export error | C/E | Save stays owned/visible; error supports Retry/Without audio/Cancel. |
| F4 wrong operation completion | C/E | Export A finishes after B exists; cannot resolve B or leak A. |
| F5 configuration changes | C/E | Fingerprint changes cancel before mutation, unrelated preferences do not. |
| F6 rotation lifetime | C/G | Rehost same source/edit/playhead/view/allowance during active work. |
| F7 media-copy retry loss | E | Fake backend failure retains attachment; safe retry/omit preserves screenshot/fields. |
| F8 ignored silence settings | F | Global and editor settings persist/use same actual parameters. |
| Additional silence threshold gap | F | Controlled low-contrast fixture abstains/protects louder region. |
| Additional next-scan settings gap | B/F | Change limits during capture; existing context unchanged, next scan updated. |
| Additional waveform resolution gap | D/F | Zoomed short speech/pause detail with bounded background work/cache. |
| Additional resource/export gap | D/F/G | Full ledger, long admitted clip completion/cancel and device plateau evidence. |

## 7. Test model additions and execution policy

Keep REC1–74 and their existing categories/results; do not reset or claim them all passed. Register these
additional review regressions as N, with implementation names linked as they land:

- REC75: asynchronous source/writer failure publishes actual capture state and permits a clean explicit restart.
- REC76: controlled writer/Refresh/Stop ordering cannot move processing time backwards or change the acquisition anchor.
- REC77: failed startup and cross-session cleanup release sources exactly once and preserve old live readers.
- REC78: Android preview callbacks run on Main; obsolete play/view generations cannot update UI or revive workers.
- REC79: trim/cuts/seek/Save share an acknowledged edit revision; Refresh updates selectable source bounds.
- REC80: unresolved/failed export keeps operation UI and retry artifacts; export A cannot complete note B.
- REC81: pre-note media-copy retry/omit retains other attachments and never retries an already attempted note mutation automatically.
- REC82: API 29 screenshot-only consent does not request/require RECORD_AUDIO; recording denial leaves screenshots usable.

Map these to existing REC27–30/34/42/48/51/65–68/69–71 rather than replacing broader acceptance coverage.
Use fake clocks/latches/deferred completions for races, real temporary files for sample integrity, fake
external adapters for stage failures, jsdom for page protocol/gestures, and Android tests for actual
WebView/codec/capture behavior. Do not write tests that only check private method calls or copies of code.

Run focused tests per milestone, then the mandated full checks at integration:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
./gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest :build-logic:convention:test --max-workers=2 --console=plain
npm --prefix scripts/page-tests ci
npm --prefix scripts/page-tests test
./gradlew.bat :app:lintVitalRelease --max-workers=2 --console=plain
```

Actual instrumented runs use documented isolated packages; assembling test APKs is not executing tests.
Do not use `am force-stop`, and do not instrument the installed accessibility-service package in a way
that force-stops it. No owner Anki collection/sync; use fakes or emulator/local disposable collections.
All sound-generating emulators use verified `-no-audio`, bounded source tones and explicit cleanup;
never change unrelated Windows mixer settings. Check host memory before modern AVD startup; known API 37
image/old WebView/Espresso restrictions are environment gaps, not automatic app failures or passes.

Diagnostics use neutral IDs/state/error codes/sizes/timings only. No recognized text, words, note/audio
contents or term-bearing URLs/filenames. All new strings go into every declared locale, currently 27;
verify narrow/320 dp, large-font, landscape, RTL, light/dark/e-ink and accessible targets.
Reuse existing comparable non-functional baselines; timings over 1.5 times the comparable prior AVD run
are investigated as findings, not converted into an invented new audio performance allowance.

## 8. Resume and completion tracking

| Milestone | Status | Evidence |
|---|---|---|
| A regression baseline | Partial | Permanent core regressions landed with fixes; 98 core-media tests, 8 AudioPlayer, 4 playback-owner, 12 popup playback and 8 new editor/pending-operation cases pass; identified editor DOM regressions and frozen-target fake-backend cases are permanent. Full lifecycle fixtures remain. |
| B session/preview lifetime | Partial | Session/preview/consent/terminal retirement and TTS/shared own-playback repair implemented and tested. Required checks and 4 final native tests pass. Physical source/route/external-engine gates remain. |
| C pending operation/protocol | Software implemented; integration acceptance open | Pre-chooser snapshot, Ready, crop/editor resize, dirty native Back, profile invalidation, safe host teardown and token-bound scan resolution implemented. E recovery UI is also implemented; actual service/device acceptance remains in G. |
| D editing/fidelity | Partial | Navigation/preview/source leases, PCM/codec and Ready-snapshot first refinement are implemented. 116 core-media/350 overlay/236 page cases pass. Decoded pilots/cuts, 30min encode, prior 4 media and latest 17 editor/crop native checks pass, with affected gates. Bounded waveform/detail/status and repeatable cap feedback are implemented; latest 17 native editor/crop cases pass (20.7s), including waveform replies/status. Real source/system gesture portions remain G. |
| E Anki recovery | Software implemented; local provider fault/retry and platform playback passed | Editor/source/encoding recovery and Retry/Without/Cancel remain implemented. Official AnkiDroid 2.25.3 in a fresh local API 29 collection passes text add/update, sentence Opus add, byte-identical transfer, decoding, Without audio preservation and replacement. A real missing-source copy failure performs no note mutation and Retry of the same preparation succeeds; MediaPlayer reaches completion. All six core-Anki device cases pass. Physical audibility and desktop Anki remain open. |
| F settings/resources | Partially verified; full physical/unattended matrix remains | Real process-death cleanup, API 29 reset/restore and memory-pressure waveform-cache release pass. API 30's linear growth is isolated to its platform Ogg writer. API 37's 20 settled production exports stay at 6,621,600→6,620,768 native bytes with stable settled descriptors; the no-settle profile is an asynchronous Codec2-pool transient. No production GC/speculative codec change was added. Phone resources and long unattended acceptance stay open; REC rows remain N/partial. |
| G device/regression | Partially verified; full acceptance open | Final API 24/29 legacy capture, API 29 shared consent and API 37 consent/AudioRecord/FGS/accessibility-capture fixtures pass. Focused API 37 recorder/process cleanup passes. Full gates pass in 1m50s with 231 core-Anki, 136 core-media, 354 overlay, 291 app and 247 page cases. Modern WebView editor, real sources/routes, physical audibility/UX, desktop Anki and power/endurance/full acceptance remain. |

Current continuation (updated 2026-11-26): the late local verification block is complete. It added memory-pressure cache
release, final legacy and modern capture/FGS fixtures, real provider fault/retry plus platform playback, and the API 37
settled codec comparison. Do not redo these receipts. Remaining work is physical source/route/audibility/lifecycle/UX and
endurance acceptance, external-TTS self-exclusion, the blocked modern WebView editor check, disposable desktop Anki and
independent translation review. Music is deferred. See `ai/notes/sentence-audio-handoff.md` for current constraints.

Read this plan, original product decisions, review,
status and build notes. Verify HEAD/dirty state before editing. Preserve unrelated submodule content and
the two untracked `docs/images/photo_*2026-11-10*.jpg` files. Commit/push only this feature branch.

Check account-wide weekly usage before long work and between milestones; if less than 2% remains, stop
immediately at the current checkpoint, without spending resets/credits or finishing another build. Keep
handoff/status current incrementally. This overrides the older 80%-used memory rule.

Completion requires all milestones with linked actual evidence and explicitly visible remaining device
limitations; no invented percent complete. Any product change needs an owner answer and changelog entry.

Changelog:
- 2026-11-26: Added application memory-pressure release of only the rebuildable waveform cache, with leased-PCM/rebuild
  regression coverage. Completed final API 24/29 legacy capture and API 37 consent/AudioRecord/FGS/accessibility fixtures,
  real local provider fault/retry with platform playback completion, and the API 37 settled codec comparison. Full gates
  pass with 231 core-Anki, 136 core-media, 354 overlay, 291 app and 247 page cases. No product decision changed; physical
  source/routes/audibility/UX/endurance, external-TTS exclusion, modern WebView editor, desktop Anki and translation review
  remain open.
- 2026-11-26: Added isolated real process-death and file-backed reset/restore regressions, verified API 29 shared
  screenshot/audio consent and API 24 unsupported UI/playback, and completed a fresh local AnkiDroid sentence-media
  round-trip. Measured the 30-minute export, PCM/FD cycles, external-TTS transient writes and repeated codec native heap.
  Recorded the unresolved roughly 29–31 KB/export native growth without changing production code or any product decision.
  Full gates pass; modern FGS/source/physical/endurance acceptance remains open and REC31/32/33/50/51 stay N/partial.
- 2026-11-26: Validated restored and newly saved silence settings against shared 100–30,000 ms minimum-pause and
  0–2,000 ms margin ranges in the data class, repository, Compose and editor page/native guards. Replaced editor/menu
  English placeholders in 23 locales, completing resource coverage across 27 locales. Added red/green restoration and
  boundary regressions; defaults and product decisions are unchanged. Full device/reset/restore and independent
  translation acceptance remain open.
- 2026-11-26: Added one process-scoped sentence staging owner that removes orphaned Opus exports before its first
  creation, preserves live Retry files and rejects failed orphan deletion. Added real-file cleanup regressions and
  audited the fully disabled Android backup/no-backup storage configuration. No product decision changed; actual crash/
  restart, external-engine transient writes, codec native memory and physical/endurance acceptance remain open.
- 2026-11-26: Added local private-TTS admission using a provisional 64 MiB WAV ceiling plus the 50 MiB reserve,
  followed by nonempty/size/reserve result checks and existing owner cleanup. Added workspace/player regressions and
  inventoried preview buffers without changing preview code. No product decision changed; external-engine transient
  writes, codec native memory and physical/endurance acceptance remain open.
- 2026-11-26: Closed the bounded PCM queue after stop's accepted-prefix drain, releasing the default 288,000-byte
  pool from stopped leased sessions without deleting history. Added retained-pool/borrowed-block and post-stop snapshot
  assertions; 132 core-media/354 overlay/291 app cases and affected gates pass. No product decision changed;
  preview/TTS/codec-native accounting and physical/endurance acceptance remain open.
- 2026-11-26: Replaced per-context waveform pyramids with one rebuildable cache and bounded silence analysis to one
  30-minute primitive-array pass at a time. Added cancellation/budget/cache regressions, affected gates and 19 API30
  editor/crop/touch checks. Thresholds and product decisions are unchanged; queue/preview/TTS and physical/endurance
  acceptance remain open.
- 2026-11-26: Bounded export workspace with raw-PCM-plus-slack preflight and per-write/final free-space checks.
  Added workspace and encoding-retry regressions, affected gates and a short API30 codec rerun; its long case was
  skipped by assumption. No product decision changed; aggregate RAM/TTS and physical/endurance acceptance remain open.
- 2026-11-26: Shared the existing provisional PCM byte/file ceilings across all live/leased sessions, added
  reservation rollback and deletion-based release, and made low-space admission include the complete next block.
  Added red/green storage/restart/prefix regressions and affected gates. No numeric budget or product decision
  changed; cache/export/TTS accounting and physical/endurance acceptance remain open.
- 2026-11-26: Made silence separation conservative, preserved manual cuts and rejected stale proposals by
  source/edit identity. Added red/green detector and page regressions plus affected gates. Product decisions
  and defaults are unchanged; aggregate resources and device/local-Anki acceptance remain open.
- 2026-11-25: Consolidated the current handoff and corrected stale C/F/G statuses and the obsolete
  instruction to implement E recovery. No product decisions or application code changed.
- 2026-11-24: Recorded owner's successful capture/Anki attachment and unconfirmed preview result.
  Owner approved secondary-tool disclosure plus directional scrolling and a useful free scroll zone;
  clarified optional music-only gap detection. Reproduced out-of-phrase cut handles and corrected their
  visibility/drag limits; 239 page cases pass. Native/touch/layout and broader collisions remain pending.
- 2026-11-22: Retained editor/source/encoded staging through retryable copy failure; reused successful
  transfers and encoding, replaced changed edits, retired workers before omission/cancel cleanup. Fixed
  actual FileProvider path failure and sleeping-display opening deadlock, both reproduced natively.
  Mixed-field omission has an update notice; message timers do not survive operations. 226 core-Anki/
  353 overlay/291 app/237 page and 18 native editor/crop checks pass; affected gate 4m37s. Owner requests
  a development APK from this checkpoint before starting another block. Product choices unchanged.
- 2026-11-22: Split Anki media/render preparation from provider mutation, cache known successful media
  per role, and reject obsolete/repeated submissions including uncertain acknowledgements. Actual schema/
  deck/permission/target are checked before mutation. Four backend regressions red then green; 225 core-
  Anki/350 overlay cases and core lint pass. Editor retention integration follows; product choices unchanged.
- 2026-11-22: Added bounded reusable amplitude levels, conflated viewport queries and read-only live
  availability/allowance status. Waveform loading no longer blocks Ready; stale detail is ignored,
  Refresh preserves edits and explains every exhausted request. 116 core/350 overlay/236 page cases,
  affected gates and 17 isolated native editor/crop cases pass. Product decisions unchanged.
- 2026-11-22: Reproduced/fixed initial refinement missing speech arriving during page opening and a
  late suggestion replacing an in-flight page edit. Ready source/command revisions, one-shot guards,
  page initialSequence and immediate handle redraw preserve user actions and presentation behavior.
  350 overlay/233 page and 17 native checks pass; affected unit/lint/app/test-APK/vital gates pass.
  Product decisions unchanged.
- 2026-11-22: Connected a pure streaming encoder driver to the native codec/container adapter, exact PCM
  filling, continuous kept timestamps, cancellation and separate progress/overall deadlines. 113 core
  cases, affected integration gates and final 19 native checks pass. Actual decoded head/middle/tail
  and 30min encode measurements are recorded. FD growth was isolated to delayed Codec2 shared-buffer
  eviction (settled plateau), not leaked run files; temporary probes/forced-GC hooks were removed.
  No product defaults or behavior decisions changed; waveform/status and resource/physical gates remain.
- 2026-11-22: Reproduced/fixed byte-granularity kept-range reads and odd waveform reads. Added shared
  exact PCM16 filling for waveform/preview, explicit truncated-source failure and cooperative waveform
  cancellation. 107 core tests and core lint pass; encoder adoption/native fidelity remain next.
  Product decisions unchanged.
- 2026-11-22: Added metadata-only Prepare source reservation, context/session binding and deferred retirement
  for active consumers across capture restart/Close. New scans cannot replace reserved same-session data;
  UI revocation disables future old-scan actions while preserving an active editor. Pending-note cleanup
  includes cancellation before coroutine dispatch. Unit/affected build and 15 native checks pass, including
  original-editor export after restart. E will retain source/draft through its future media-copy retry UI.
  Product decisions unchanged.
- 2026-11-22: Repaired retained-source coordinate bounds, stale/zero-width gestures and live mode seeks.
  Replaced competing preview jobs with an operation-owned conflated mailbox and desired playback intent;
  native output and page progress reject superseded requests. Permanent regressions and affected
  build/native gates pass, with 15 isolated API30 checks. Product decisions unchanged.
- 2026-11-22: Implemented token-bound scan resolution after owned cleanup, active-profile invalidation,
  actual Anki collection-mutation boundary, native/predictive Back plumbing and shared dirty cancel,
  one-shot external crop/editor Finish, host/screen-off suppression and visible protocol rejection.
  Unit/JS/native and affected build gates pass; E/G remain cross-stage integration/acceptance gates.
  Continue remaining stages autonomously as authorized; product decisions unchanged.
- 2026-11-22: Owner requested autonomous continuation through all remaining milestones without yielding
  between blocks. Implemented pre-chooser note preparation, page-scoped request identities, separate
  loading/Ready retention admission and owned crop/editor host resize. Added permanent regressions and
  isolated API30 checks; current status/engineering hold the gate receipts. Product decisions unchanged.
- 2026-11-22: Owner explicitly requested replacement rather than patches. Replaced the editor continuation
  and untagged bridge with AudioEditorOperation, operation IDs, source/edit revisions and frozen command
  snapshots; connected PendingNoteController to the real attachment pipeline. Added encode failure/retry,
  late-output cleanup, cross-scan reads, confirmed-note-only edit reuse and frozen-target/config-change
  regressions. Target is currently captured at native Add; pre-chooser capture and remaining lifecycle
  gates are explicitly pending. Product decisions are unchanged.
- 2026-11-22: Implemented shared own-output leases, capture-lifetime protection, TTS native stop barrier
  and operation-owned pronunciation cleanup. Original native WAV leak reproduced; permanent JVM/native
  regressions added, including drain/queued-feedback/short-speech and pending dictionary audio. Required
  checks and final isolated native tests pass. Product defaults/behavior decisions unchanged.
- 2026-11-21: Retired facade-owned terminal sessions after final scan release, preserving live PCM readers
  and deleting owned empty directories after final lease. Kept native input activity visible through failed
  Stop. Added three facade regressions and strengthened Android cleanup verification. Product choices unchanged.
- 2026-11-21: Isolated screenshot/audio permission purpose, preserved screenshot projection on denied
  audio permission and fixed revocation/cancelled-host immediate retry. Added JVM policy and real API29
  consent/frame/AudioRecord initialization regressions. Product choices unchanged; B remains partial.
- 2026-11-21: Reworked preview run lifetime, output drain/position and bounded generation-checked callbacks.
  Added native short-stream transport padding after a reproduced API30 stall; 500 ms / 5 ms / one-frame
  AudioTrack→Main→WebView fixtures pass. Added 27-locale preview error feedback. Product choices unchanged.
- 2026-11-21: Owner authorized implementation. Added core regressions and the serialized session owner;
  capture/publication/metadata now have one order, public work suspends off Main, startup/cancellation/
  failure/Stop/restart preserve ownership. Added synthetic Android Main-thread test. Product decisions
  unchanged; A/B remain partial and no editor/Anki or complete device acceptance is claimed.
- 2026-11-21: Owner authorized selective redesign in the review order. Created this execution plan,
  inherited all existing product decisions and approved prototype behavior, mapped every review finding
  to repair work/tests and retained measured compatibility/resource gates. No new product choices or
  production fixes were made in this planning milestone.


- 2026-11-26: Isolated API 30 native growth to the platform Ogg writer: all-packet and one-packet muxer fixtures both grow
  1,133,616 bytes over the same measured interval, while codec/extractor lifecycle controls remain near flat. AOSP 11/12 omit
  `ogg_stream_clear`; 13/14/15/main include it. The settled production exporter still passes with flat FD/thread samples.
  No product decision, production codec ownership, container or format changed; modern/physical plateau acceptance remains open.


## Touch repair checkpoint (2026-11-24)

- Owner authorized compact disclosure/directional scrolling, handle/button/preview checks and the usual
  minified Dev update. Music work is explicitly deferred; no VAD dependency was added.
- Compact portrait host uses 55% of available height; expanded remains 88%. Short windows reduce wave
  height/vertical padding while primary controls retain 44px minimum heights. Body has 26px side gutters.
  Vertical background swipes scroll; horizontal gestures pan. Overview supports dragging. Listening/mode,
  Refresh and completion actions stay outside the scroller; cut/silence/zoom/undo are disclosed together.
- Playhead is a 22px dot aligned with the cursor; empty track space no longer intercepts scrolling.
  Actual hidden CSS semantics override flex display for inactive cut edges/proposals. Pointer-owned
  cut drags merge overlapping/adjacent exclusions on release; Undo restores original ranges. Raw cuts
  outside the outer trim remain recoverable. Saved silence values, including zero margin, are read from
  the actual preferences flow; search waits for pending parameter writes.
- Evidence: 245 production-page cases pass. API30 isolated editor/crop/resize/touch: 19 cases pass in 26.245s.
  Final shorter-layout touch rerun passes in 5.951s, requiring the whole initial waveform inside the body.
  Actual injected swipes cover vertical wave/empty seek-track scroll, horizontal pan and dot seeking;
  taps cover disclosure/Cut/Undo. Nonzero PCM reaches waveform and real AudioTrack preview, with mode
  switching/pause, export/copy failure/retry and omission covered. Screenshots visually inspected.
- Headless tests cannot certify phone audibility/finger comfort. Conservative silence abstention,
  aggregate resources, actual source/service routes, local-Anki and endurance acceptance remain pending.
  No owner collection accessed; test package removed and owned silent AVD powered off.

Changelog: 2026-11-24 — approved touch repair implemented; product defaults unchanged; music deferred.
