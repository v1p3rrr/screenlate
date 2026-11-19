# Plan: sentence audio recording (2026-11-19)

Status: implementation started on `codex/sentence-audio-recording` from `main` (`5c1e5cce`), authorized by the owner on 2026-11-19. Phase 1 feasibility work is in progress. Product choices below are binding. Change them only after owner confirmation and record the change here. Technical parameters explicitly delegated to measurements are not additional product choices.

## Read this first

- This plan is the implementation authority. [Recovered discussion and research](../notes/video-audio-mining-context.md) preserves the earlier alternatives and rationale; its historical recommendations and pending-question lists do not override the decisions here.
- [Platform research](../notes/audio-capture-platform-research.md), [overlay window behavior](../notes/overlay-system-ui.md), [OCR/screenshot lifetime](../notes/ocr-engines.md), [build environment](../notes/build-environment.md), and [module map](../notes/module-map.md) apply to their respective phases.
- New recording cases are registered as **N, not executed** in [the test model, REC section](../test-model.md#rec-sentence-audio-recording). Run history and measured results belong there; format/resource investigation results belong in a focused note linked from this plan.
- The owner tested and approved the interactive mockup, including the final playhead/overview corrections. Source: `C:/Users/vpr/.codex/visualizations/2026/11/18/01a1ef6c-c252-7673-bd2b-cf18f181effc/screenlate-audio-editor.html`; SHA-256 `3f8c742709e1d0d60c230f819b44858b3b66f7ab924b384342e4c340fde576ce`. It demonstrates interactions, not real capture/playback/Anki. The English specification and wireframe below preserve its behavior independently of that local file. Example timelines and values changed during exploration are not defaults.

## 1. Goal and scope

While manually enabled, remember recent eligible internal playback so a user can mine a sentence without rewinding. Protect the history when a screen scan is acquired, keep a bounded continuation while the user reads definitions, then offer an audio editor before creating/updating an Anki note. Paused-video continuation and uninterrupted live mining use the same mechanism. The app does not know subtitle timings or the linguistic end of an utterance.

Included: a continuous ring, protected scan history, explicit Refresh, waveform navigation, outer trim, multiple reversible internal cuts, optional silence proposals, original/result preview, resource/error handling, and a separate Anki sentence-audio attachment.

Excluded from v1: microphone/fallback recording, ASR/LLMs, automatic sentence matching/selection by speech pauses, subtitle tracking, a video player, automatic player pause/resume, notification-listener media controls, screenshot history, gain/normalization, a codec settings panel, standalone sharing, and crash-draft recovery. Offline word-pronunciation packs remain a separate backlog item. Generic Anki uncertain-commit/transaction redesign is also outside this feature; see section 9.

## 2. Binding decisions

### Capture, retention and resources

| Topic | Agreed behavior |
|---|---|
| Audio source | All eligible internal playback except Screenlate's own preview/pronunciation. Other applications may mix; do not follow/restrict a selected foreground app. No microphone. |
| Availability | Playback capture on API 29+. Keep app minSdk 24; unsupported versions retain existing screenshot/pronunciation behavior. Source policy/profile/usage restrictions remain real limitations. |
| Android 7–9 UI | Recording sentences shows only an Android 10+ availability explanation. Hide recording toggles, durations, silence controls and the duplicate default-editor switch; omit recording commands/audio attachment options in bubble/Add menus. No activation entry point may start capture. Existing screenshots and word pronunciation remain available. |
| Activation | Explicit user start/stop in the bubble hold menu and Audio settings, with one shared runtime state. No automatic start after boot/restart or restoration of expired projection consent. |
| Indicator | Static red dot on the bubble, no blinking. Apply existing e-ink/monochrome conventions; use text/accessibility labels too. System recording notification exposes actual state and Stop. |
| Scan anchor | Protect history once per new screen scan, including app-text-only. Anchor at actual acquisition, not OCR completion, popup opening, Add, or each word hit. |
| History settings | Global H/R/E defaults **5/5/2 minutes**. Each editable from **30 seconds to 10 minutes, step 30 seconds**. Three adjacent controls in Audio. Durations are not permission to exceed resource budgets. |
| H | Independent rolling pre-scan history. New scans pin the available H ending at their acquisition marker. |
| R | Continuous protected waiting from scan through the first operable audio-editor opening, bounded by scan time + R. Reading definitions, note preparation and screenshot crop consume R. |
| E | Separate one-time continuation from first operable audio-editor opening, only if R has not already sealed. Deadline becomes editor-open + E. Unused R does not accumulate. |
| Exhausted context | Keep the complete retained prefix and edits. Ring continues independently. Refresh exposes all retained unseen continuation through the cap, then explains that no further sound can be added; repeated presses repeat the explanation without changes. |
| No renewal | Word/nested lookup changes, repeated Add/editor opening, save/cancel, Refresh, rotation and expanding/collapsing never renew E or revive a sealed context. |
| Missing history | Never join through an unavailable interval, even with confirmation. Keep the old fragment or start a separate fresh scan/context. Recorded silence and intentional cuts are not missing history. |
| Storage | Bounded private temporary PCM files, small working RAM and shared ownership. Owner permits engineering correction if measurements show substantial drawbacks; do not change product behavior silently. |
| Output | Mono, moderate speech quality; codec/rate selected by compatibility/fidelity measurements. No technical quality settings. No separate final-clip duration cap within retained history. |
| Parameter changes | A context admits all H/R/E values at scan time. Changed durations apply to subsequent contexts; show this when editing settings. Global ring changes must not evict protected leases. |
| Stop/restart | Stop new capture but retain already protected data/edits until that context ends. Restart is a new session; old scans get no new audio or editor action until a manual new scan. |
| Dock/service | Docking alone keeps the ring running. Full bubble/accessibility-service shutdown stops capture; already live consumers keep valid data until their context/operation ends. No process-restart recovery. |

### Editor and note flow

| Topic | Agreed behavior |
|---|---|
| Scope | Screen scans, including app-text-only. Typed search, camera and previously acquired photos do not inherit unrelated ring audio. |
| Default Add | Shared default-editor switch is on initially. When applicable, ordinary Add follows attachment defaults: screenshot crop first, then audio. Capture/scan eligibility and active template usage still gate audio. |
| Long-press Add | With an eligible recording context, one menu retains Open in Anki / Update / Add anyway and screenshot/audio/both choices as applicable. Without capture or a preserved eligible context, keep existing screenshot long-press behavior. Stopping capture does not remove access to the current preserved fragment. No new audio options for an old scan after capture was enabled/restarted. |
| Fixed target | Freeze entry, sentence, language, scan and action identity at Add/long-press activation, before a chooser or asynchronous work. |
| Presentation | Start in the popup presentation with dictionary content hidden. Expand the same editor into a wide movable/collapsible floating panel. Outside touches reach the player without closing the editor. |
| Initial source | Show retained committed sound through first editor opening, with a scan marker. A fixed view does not slide with the live ring. |
| First selection | **15 seconds before the scan marker plus 5 after**, intersected with actually available shown source; no invented audio or waiting to make 20 seconds. This is an approximate suggestion. |
| Initial leading quiet | If the initial selection begins with **more than 5 seconds** of confidently detected silence and contains distinguishable non-silent sound, move only its left boundary to **150 ms before** that sound. Entirely quiet or uncertain audio stays untrimmed. Source is never destroyed. |
| Refresh | Append newly retained sound on the right; preserve old source coordinates, selected boundaries and exclusions. Even untouched initial selection does not extend automatically. Save uses the shown edit revision. |
| More notes from scan | Suggest the last confirmed range/cuts, adjustable for the next note. Abandoned edits do not replace it. Previously saved notes stay unchanged. No new E grant. |
| Navigation | Tap seeks; waveform-background drag pans; pinch/buttons zoom. Playhead/trim/cut handles have distinct gestures. Playhead drag seeks without panning. |
| Playhead | Thin vertical line with an attached draggable dot below; no filled seek track. Hide line and dot when offscreen rather than pinning them to an edge. |
| Overview | Tap repositions the zoom window; drag moves it continuously. Grabbing inside the viewport preserves grab offset without jumping. Detailed time labels describe visible bounds relative to scan. |
| Edits | Outer phrase boundaries and shaded internal excluded intervals on one source waveform. Create/select/resize/remove exclusions and undo, following the approved mockup. |
| Preview | Result by default, optional original comparison. Unity gain; phone media volume only. Do not reuse pronunciation volume or seize focus to control the source player. |
| Silence action | Immediate search with remembered parameters and nearby settings icon. Only current outer selection; minimum **1.5 s**, preserve **150 ms at each edge**. Propose reversible cuts; listening is optional. Abstain if the signal cannot be separated usefully. |
| Empty/quiet output | Quiet audio: warn, allow preview/save/Without audio. Zero retained frames after edits: do not export an empty file; offer adjustment or Without audio. |
| Footer | Persistent Cancel / Without audio / Add, with Refresh/new-sound status accessible while body scrolls. |
| Cancel/Back | Both cancel the whole pending note in compact and wide views. Confirm discard only after boundary/cut changes; preview/pan/zoom are not dirty edits. Return dictionary context when still valid. |
| Rotation | Preserve source, edits, playback/view state and original clocks; rebuild presentation only. |
| Another scan | One editor at a time. Finish or cancel current editing first; a failed finish retains retry data and does not execute a destructive queued scan. |

### Settings and Anki

| Topic | Agreed behavior |
|---|---|
| Placement | Shared Recording sentences card on Audio, outside language-specific pronunciation sources. |
| Shared default switch | Audio and Anki show the same setting; Anki copy immediately follows Screenshot by default. Explain ordinary Add versus long-press audio selection and capture requirement. |
| Audio settings content | Recording toggle, default-editor toggle, adjacent H/R/E, next-scan applicability, silence settings/info. **No static Anki field row or new field-configuration screen.** Conditional missing-marker hint may point to normal Anki settings. |
| New marker | `{sentence-audio}` for recorded sentence; `{audio}` keeps word-pronunciation meaning. Both can coexist. |
| Templates | Extend marker catalog/presets/field inference for new note types. Do not automatically migrate or prompt about old mappings. Existing Fill suggested templates already offers Fill empty only / Replace all; reuse it. |
| No used marker | Ordinary Add unchanged; skip audio work/editor. Explain normal field setup near recording settings when relevant. |
| Duplicate Keep | If the effective update policy keeps existing sentence audio, explain before editing; offer continue without editing or change the field setting. Do not override Keep. |
| Without audio update | Preserve existing sentence-audio field(s), rather than writing an empty Replace value. For mixed `{sentence}<br>{sentence-audio}`-style fields, preserve the **entire existing affected field**, notify, update other fields normally. New-note creation still renders its other markers. |
| Transfer failure | If requested sentence media fails before note mutation, show error and offer retry or explicit Without audio. Never silently create a note missing requested recording. |
| Relevant settings changed | Cancel/reset the pending note on destination/model/template/update-policy changes; do not save to an old frozen configuration. Duration/default-attachment/appearance changes alone do not cancel an unrelated operation. |
| Restart/backup | No recording or draft restoration after process death. Back up settings only, not live capture state, consent, PCM, waveform or export/TTS/retry files. |

## 3. User flows and visible states

### Approved editor layout, independent of the local prototype

```text
Sentence audio                 Info   Collapse   Expand/Compact
Frozen word / meaning
Recording / stopped / capped                    Remaining allowance
Retained duration                               Scan marker legend
[ full-history waveform: draggable visible-window rectangle | scan ]
[ zoomed source: start/end handles, shaded cut intervals, thin caret ]
                                            caret-attached dot
Visible start            Relative to scan                 Visible end
Start / End                                         Output duration
Listen     Result / Original                         Zoom out / in
Cut        Find silence   Parameters                 Undo
Selectable cut chips / removable ranges
New retained sound                                    Refresh
                         Cancel    Without audio    Add
```

Compact content scrolls; the footer and essential Refresh status remain accessible. Wide mode is the same draft, not a second editor. Preserve usable touch targets at narrow widths, system insets, keyboard and both orientations. Expand/collapse/move are presentation changes only. No full-screen transparent touch catcher may intercept player controls outside the panel.

Settings card: recording mode; default audio editor with clear ordinary-Add/hold-menu hint; three adjacent duration controls; next-scan applicability; silence parameters; info. Use existing Compose settings components and themes. English primary strings and every declared locale (currently **27**, not the historical 17); Russian wording follows the accepted prototype. Duration formatting supports 30-second increments and RTL without forcing a misleading integer-minute label.

The shared switch's English meaning is **Audio editor by default**, with the hint: **Open on a normal tap of
Add. When off, choose audio from the Add hold menu. Requires audio recording.** This describes editor entry,
not immediate recording/automatic saving, and must not be shortened to an ambiguous Audio by default title.
Both settings copies use equivalent localized wording. Stopped-but-preserved contexts use their explicit
retained-source status as described below.

### Eligibility and pipeline

| Situation | Expected action |
|---|---|
| Capture never enabled / unsupported / search / photo | Existing Add/screenshot/pronunciation pipeline, no unrelated sentence audio. |
| Capture enabled after this scan | Existing pipeline; no audio entry even after moving between words. Explain a manual new scan is required when relevant. |
| Eligible scan, marker absent | Existing pipeline and conditional settings hint. Do not export unused media. |
| Eligible scan, marker present, default audio on | Normal Add freezes target, resolves ordinary validation/duplicate policy, optional screenshot crop, then audio editor. |
| Eligible scan, default audio off | Normal Add omits sentence recording; eligible long-press menu can request it. Omission preserves an existing recording on update. |
| Capture stopped after eligible scan | Existing protected audio remains editable; show stopped state and no promised new continuation. Mode-off does not delete the draft. |
| R/E/resource cap reached | Editor remains usable on saved prefix; Refresh exposes its complete available tail once and reports the cap. |
| Requested audio transfer fails | Keep bounded retryable operation before note mutation; retry or deliberate Without audio. |

No note is created merely by opening/cancelling an attachment stage. A screenshot accepted before audio is held for that pending note; Cancel cancels the note, Without audio continues with its other requested attachments. Repeated Add must not create concurrent editors/exports or lose existing duplicate/open commands.

Preserved-source access after Stop is the agreed exception to live-capture availability: it allows editing/saving
the current fragment, not opening audio for unrelated scans. The default-editor hint describes normal active
recording; stopped-context UI explicitly identifies retained audio and the absence of continuation. Restarting
capture does not silently rebind that old scan to the new session.

## 4. Retention state machine and continuity

Use separate state for capture, protected source, presentation and note operation. One `recording: Boolean` cannot express a capped context with a healthy ring, or stopped capture with a valid editable draft.

Suggested state vocabulary (names may follow implementation style):

- Capture: Off / Starting / Running / Stopping / Failed, with generation ID.
- Context: Waiting(deadline) / EditingContinuation(deadline, firstOpen) / Sealed(reason, endpoint) / Released.
- Presentation: Dictionary / AttachmentChooser / ScreenshotCrop / AudioCompact / AudioWide / Collapsed.
- Note: Preparing / Editing / Exporting / Transferring / Saving / Completed / Cancelled / FailedBeforeWrite.

Let `s` be acquisition time, `o` the first operable editor opening, and H/R/E the admitted values. Protect available contiguous source `[s-H, s]`; during Waiting, endpoint cannot exceed `s+R`. If `o < s+R` and capture continuity/capacity permit, atomically change deadline to `o+E`. At `o >= s+R`, keep the context sealed and grant no E. Stopping/failure may seal earlier. These are logical ranges of **one stream**, not three separately encoded recordings.

Use boot/elapsed monotonic time consistently for marker/deadlines and actual audio timestamps. Frame ranges use session ID plus 64-bit frame offsets. Convert clocks explicitly and measure acquisition/capture offset; never confuse wall clock, uptime and sleep-inclusive time. Timer callbacks only notify: normalize deadlines before open/Refresh/save/publication/context changes. Old timer tokens cannot affect a new stage/generation. Pauses and quiet audio consume elapsed allowance.

Reserve the history/marker when actual scan acquisition starts, before waiting for app-text/OCR results.
Refinement or an on-demand later Anki screenshot does not replace that scan's audio anchor. Failed/cancelled
acquisitions release unused reservations; stale completions cannot attach them to another scan.

Keep distinct endpoints: admitted cutoff, validated committed frames, retained frames and shown revision. Pre-cutoff complete frames queued for writing may publish late; post-cutoff source frames may not enter that context. Refresh publishes a stable, non-overlapping prefix including complete committed frames from the current partial file; it never uses a changing file length as a snapshot.

Examples with default values:

- Scan 00:00, first editor 04:30: prehistory remains available, E ends at 06:30. Reading did not consume E.
- First editor 00:10: E ends at 02:10, not at the old R deadline 05:00 and not after adding unused R.
- Editor requested 04:59 but ready 05:02: R already expired, no E. Request/crop time does not reserve it.
- First editor ready 04:59: E ends 06:59. A late old R callback at 05:00 is ignored.
- User returns after hours: bounded prefix and edits survive while context is live, ring has rolled independently; no bridge to fresh ring audio.

If E admission fails, preserve existing source and explain the reason. Any internal retry keeps the original first-opening deadline and requires still-complete continuity; terminal seals are not reopened. Never convert an overrun, discarded buffer or revoked capture into fabricated silence. Genuine recorded silent frames are valid source.

## 5. Module boundaries and integration points

Create **`core:media`**, an Android library using existing convention plugins and Hilt. It depends inward on common facilities as needed, never on overlay, app, Anki, or GPL renderer/engine internals. Keep runtime API-29 types behind supported-version initialization so minSdk24 paths still load.

| Owner | Responsibility / existing seams |
|---|---|
| `core:media` | Capture sessions, bounded PCM history/leases, protected context state, waveform summaries, edit/range validation, preview, silence analysis, encoding, resource accounting and recording preferences. Small typed facade; internal storage/codec details hidden. |
| `overlay` | Screen acquisition anchor and context lifetime; bubble dot/menu; consent/presentation integration; popup audio editor and gestures; freeze note target; orchestration before Anki. Existing `OverlayController`, `ScreenReads`, `PopupController`, `LookupPage`, `PopupNotes`, `PopupAudioPlayback`, capture package. |
| `core:anki` | `{sentence-audio}` marker/inference, local sentence attachment, omission semantics, normal field/duplicate policy and media transfer. Existing `AnkiNotes`, `NoteRequest`, `AnkiBackend`, `FieldTemplate`. Accept a local artifact, not a dependency on media's capture machinery. |
| `app` | Audio/Anki settings, permission/consent host where appropriate, navigation, backup/reset integration, localization. Existing Audio settings cards/view model, Anki settings, `BackupManager`. |

Facade operations should expose observed state, start/stop, acquire/release scan context, open a shown snapshot, refresh, preview/analyze and export an immutable edit revision. Use typed IDs/ranges/errors and explicit closeable ownership; do not expose mutable chunk lists or internal file layout to UI. Choose precise class names after examining existing conventions. Keep pure range/deadline logic injectable with fake clock/storage/capture for JVM tests.

Manifest/service ownership must have one clear coordinator. On API29, legacy screenshot capture and audio both use MediaProjection: coordinate existing `MediaProjectionHolder`/`MediaProjectionService` rather than competing independently or revoking each other's session. Preserve API24–28 legacy screenshots and API30+ accessibility screenshot behavior. A stopped projection/token is never reused; audio-only capture does not create an unnecessary video stream. Select the smallest ownership refactor after the platform spike, documenting the resulting dependency diagram.

Keep the audio editor implementation original or permissively sourced. If UI uses a WebView page, send compact waveform summaries and typed commands with generation/revision validation, **never full PCM**; put script tests in the existing jsdom harness. A native editor needs equivalent Kotlin/instrumented gesture/state coverage. Do not choose an unrelated UI framework or large media stack just for the prototype; verify the lightest implementation against required overlay/back/pass-through behavior first.

## 6. Capture, self-audio exclusion and platform failures

Implement runtime `RECORD_AUDIO`, projection consent, foreground-service permissions/type and correct target-SDK ordering. Denial, permanently denied permission, abandoned consent, stale success after Stop, background-start rejection and notification/system Stop have distinct honest states. Return from consent/settings does not auto-rescan the old popup or authorize a dead session. At most one recorder generation may run.

Capture only eligible playback usages, excluding Screenlate UID with a consistent configuration; do not combine matching and excluding UID filters. Different app/profile/source policy may yield silence without an observable definitive refusal. Never advertise capture support based solely on a nonzero running flag. On known recorder/projection failure, stop promptly and preserve the validated prefix. Handle AudioRecord initialization/read/dead-object errors and route/format changes without silently gluing sessions/formats.

Exclude local pronunciation and preview at capture time. External TTS-engine live `speak` can have a different UID: synthesize to a private file, wait for successful current utterance completion, then play under Screenlate UID while capture is active/starting/stopping. Bound synthesis/cache work; handle cancellation, voice/language changes, empty/failed files and late callbacks. No live-speak fallback that contaminates capture. Starting capture must coordinate an already audible TTS utterance; stopping must not restore live output before the recorder is actually stopped.

Preview/result/original and pronunciation must have defined single-playback ownership within Screenlate. Do not take automatic audio focus or issue play/pause to other apps. Verify that opening/closing/playing the editor neither pauses/ducks nor resumes the source unexpectedly. Test speaker, headset/Bluetooth, device volume zero, source mute, routing changes, calls/lock and another projection. Do not promise audio capture survives lock/calls or bypasses source restrictions. Debug/release coexistence must not collide in files/provider authorities/notification IDs; excluding this package does not promise to exclude another separately installed build's UID.

## 7. Storage, resource budgets and output fidelity

### Storage contract

Read continuously on a worker into a **bounded** queue. Write fixed-format frame-aligned PCM chunks to private temporary storage; each chunk records session, format, first frame, valid frame count and committed prefix. Handle short/zero writes and errors; publish only validated complete frames. A sustained disk stall/queue exhaustion is an explicit capture failure, not unbounded memory or dropped frames presented as continuous audio.

Ring, context, preview, analysis and export share leases. Remove a file only after its last owner releases it; ring rollover alone cannot delete protected data. Keep descriptor counts bounded instead of holding every chunk open. Chunk overlap is stored once. Account for chunk-boundary slack and writer reservation as well as visible durations. No full-history PCM decode/copy or waveform recomputation on every UI redraw.

Use bounded waveform summary levels and read-ahead, bounded undo metadata/work queues, and cancellable revision-keyed background analysis. Slow callbacks cannot resurrect released sessions. Storage is private and excluded from backups; do not trust cache persistence or an old on-disk manifest after a crash. Detect missing/truncated files and preserve only a proven contiguous valid prefix, rather than appending later files through damage.

### Required resource ledger before enabling the feature

Measure and then record concrete constants, admission formulas and error texts in the engineering note and tests. The owner delegated these technical values; **no unmeasured MB/CPU/battery numbers are approved limits**.

| Resource | Required bound and response |
|---|---|
| Duration | H/R/E plus source-frame validation; ring and protected deadlines enforced independently. |
| Total temp bytes | Unique PCM chunks + slack, summaries, TTS, export/retry artifacts. Hard per-session/process budgets, not just H/R/E. |
| Free storage | Safety reserve checked before admission and during writes; export preflight includes worst required workspace while leased input stays alive. Handle later races with other writers. |
| RAM | Fixed writer queue/read buffers, bounded preview cache and waveform levels; no allocation proportional to full retained PCM. Account for OCR/WebView/bitmap peaks. |
| Pending work | One active editable context/operation, bounded coalesced Refresh, bounded encoder/analysis/TTS jobs and retained retry artifacts. |
| Handles/metadata | Bounded open descriptors, chunk metadata, edit/undo entries and URI grants; cleanup on every terminal path. |

On protected-context quota exhaustion, seal/preserve the valid prefix and report the reason. On global writer/storage/capture failure, stop capture and show that actual state. Do not solve pressure by silently deleting current edits/history or resetting the ring/context. Decline a new operation if keeping existing leases leaves insufficient workspace. Low-memory callbacks drop rebuildable caches/cancel optional analysis first; valid source remains on disk. A process kill can still lose the transient session, as explicitly accepted.

### Arithmetic for planning, not performance claims

At mono PCM16 48 kHz: 96,000 bytes/s. Default H+R+E protects at most 12 minutes = **69.12 MB**. A later disjoint H ring adds 5 minutes = **28.8 MB**; total **97.92 MB** raw retained PCM, plus chunk slack/metadata/workspace. At 44.1 kHz the same 17-minute sum is **89.964 MB**. These are decimal disk bytes, not required RAM allocations. Maximum user durations 10/10/10 with a later 10-minute ring total up to 40 minutes: **230.4 MB** at 48 kHz before overhead. A resource cap can be reached earlier.

At 48 kHz mono, logical continuous writes are **345.6 MB/hour** despite bounded retention. This is why file-backed history needs measured IO/power/thermal tests. Sharing overlap reduces typical space, not the disjoint upper bound. Avoid another complete PCM copy for export; encode directly from leased ranges. No smaller arbitrary clip cap is added to hide export costs.

### Format/encoder verification

Select a compatible moderate mono format with actual `AudioRecord`/`AudioTrack` initialization and encoder capability checks; do not assume universal 24/48 kHz. Prefer one supported rate throughout instead of an unnecessary custom resampler. Validate downmix, clipping, anti-phase stereo/quiet syllables and actual quality before settling format/bitrate. If resampling is necessary, preserve its state across file boundaries.

Temporary chunks are not independent AAC clips. Export the shown immutable edit revision by streaming retained PCM ranges through **one encoder/muxer**; timestamps derive from cumulative kept frames, so cuts remove time. Drain EOS/finalize the container; verify real decoded order/duration and playback in AnkiDroid and desktop Anki on a disposable/local test collection. Do not byte-concatenate encoded chunks or repeatedly re-encode the same source. Encoder delay/padding is distinct from missing/duplicated source frames; document measured tolerance.

## 8. Editing model, races and lifecycle

Store outer range and exclusions in original source-frame coordinates. Normalize/clamp/merge overlapping or adjacent exclusions; validate positive kept output. Output-time and original-time mapping must be deterministic. Seeking into a cut in Result mode resolves to the next kept position (or result end); Original mode can play it. A seek invalidates old queued preview data. Refresh appends source without remapping old edits, undo entries or playhead position.

Initial 15-before/5-after suggestion is intersected with available frames; do not shift it away from the scan just to pad a short history. Run leading-silence refinement only on that first suggestion, with the agreed >5 s rule; do not rerun it after user changes, Refresh or a last-confirmed proposal. Delayed analysis cannot overwrite user edits. The initial suggested/refined range is the clean baseline for discard confirmation. If scan age/available shown source does not include the requested neighborhood, report actual availability rather than inventing a phrase.

Manual silence search operates on a frozen outer-selection/revision. Preserve manual exclusions; accepted suggestions become ordinary reversible cuts. Reject stale analysis after selection/source/context changes. Uniform silence, continuous music/noise and uncertain/very quiet speech are abstention cases, not permission to remove everything. Minimum duration is tested at/below/above 1.5 s, padding at both edges, and the initial >5 s refinement is a separate rule.

Serialize or coalesce Refresh and freeze the shown edit revision for Save. During export/transfer, stale Refresh/waveform/silence/lookup callbacks cannot alter that revision or target. Repeated Save cannot start concurrent note writes. Cancel before mutation releases work safely; once an external note write is underway, cancellation cannot promise rollback. Retain only bounded retry artifacts needed for the chosen operation.

Define context ownership explicitly in overlay integration: same scan, word changes, nested lookups and transient popup hide share one context; a completed new scan/source replacement releases the old context after all live consumers finish. Full process restart has no draft recovery. A new scan requested during editing prompts finish/discard first; queue at most the intent, not a stale acquired screenshot. Acquire the new marker only when the scan actually executes. A failed finish preserves the old operation.

Existing dictionary auto-hide, refined lookup pushes, outside catchers and rotation-driven `onClosed` must recognize editor ownership. They cannot destroy/rebind the draft as if it were an ordinary dictionary popup. Service teardown stops capture; defer deletion of leased artifacts until consumers finish/cancel. Do not add an unrequested independent saved-draft browser to make teardown persistent.

Build a fingerprint for note-invalidating configuration: effective language profile, deck/model, ordered fields/templates, tags and duplicate/field-update rules used by that operation. Revalidate before media transfer and before provider mutation. Relevant change cancels and explains; default attachment/retention/theme/volume changes are for subsequent behavior, not a reason to discard the draft. If an external write is already committed, report its result rather than claiming settings cancellation undid it. External model/deck disappearance and Anki permission loss remain explicit errors.

Reset/restore cannot revive consent/session state or delete leased files behind running workers. Coordinate capture stop and pending operations when reset/restore affects their actual configuration. Startup cleanup covers abandoned PCM, waveform, TTS, encoded output and retry staging, using neutral IDs and bounded traversal. Do not log recognized text, looked-up terms, note/audio contents, term-bearing URLs, or content-derived filenames; diagnostics use sizes/timings/state/error codes.

### Failure and recovery contract

| Event | Preserve / report / permitted next step |
|---|---|
| Permission/consent not granted | No false Running state. Keep any unrelated valid current lease; explain authorization and allow explicit retry. No audio fabricated before start. |
| Recorder/projection stops | Seal validated continuous prefix; show actual stopped reason; edit/save/omit remains possible. Restart requires a fresh session and scan association. |
| R/E or protected-byte limit | Keep complete admitted source and edits; ring may continue; Refresh drains retained tail then repeats cap message. No quota reset through reopening. |
| Slow/full disk or writer queue exhausted | Stop affected capture work honestly before unbounded growth; preserve complete validated frames. Do not silently drop frames and claim continuity. |
| Optional waveform/silence analysis fails | Retain source/edits; stop the failed task and offer retry/manual editing. No forced cuts or note write. |
| Required source file missing/corrupt | Restrict to proven contiguous valid data and explain; never glue later data through the hole. Revalidate selection before preview/export. |
| Empty selection after cuts | No empty attachment export; adjust or explicitly omit. Low amplitude alone is only a warning. |
| Encoder/workspace failure | Keep source/edit revision within existing budgets; retry or Without audio before note mutation. Clean failed output safely. |
| Anki sentence-media transfer fails | No note mutation for that attempt; retry media or deliberately omit while keeping other requested attachments. |
| Note mutation attempted then fails/uncertain | Preserve existing general Anki behavior and show known result; no automatic whole-operation replay or rollback promise. Audio omission is no longer a guaranteed pre-write recovery. |
| Relevant settings/model changes | Cancel/reset pending operation and explain before mutation; a known committed external result cannot be undone by resetting UI. |
| Service/process ends | Stop capture. Release only after live owners finish; process restart clears abandoned temporary session, with no draft/capture restoration. |

## 9. Anki attachment contract

Extend `NoteRequest` with a distinct local sentence-recording attachment/intent. Do not invent an audio-source URL to squeeze local capture into pronunciation `AudioClip`. Represent **requested**, **intentionally omitted**, and **failed** distinctly. For updates, carry field-preservation intent explicitly so missing audio is not rendered as an empty replacement by accident. Match the exact marker token, not a field-name heuristic.

Resolve duplicate/field Keep policies before audio editing whenever possible. If a user elects to change the relevant field setting, that invalidates the pending note; restart through the current configuration. Preserve all affected existing mixed fields on omission and notify once per operation; other fields obey their existing policies. Both pronunciation and sentence audio may be requested independently, including in one template. Escape/render other markers as before.

Export/validate the selected media first, then transfer it via the existing provider-backed `AnkiBackend.addMedia`. Verify a usable returned reference before attempting the note write. Retry of a **pre-note media failure** is safe with operation-owned staging and does not rerun an already committed note update. Without audio still honors other requested attachments. Keep files/grants alive for actual transfer, then release them appropriately. After successful copy, Anki owns its copy; Screenlate must not delete unrelated collection media if a later operation fails.

Existing AddContentApi can return an uncertain result after an insert; fields/tags update is not a general atomic transaction. The owner explicitly kept this shared integration issue outside the audio feature. Preserve current book/open/duplicate behavior and do not introduce a blanket automatic retry, hidden operation tags or an exactly-once promise. New audio work must not worsen it: avoid concurrent/double submissions, record known operation stages, and never offer pre-write omission as if it could undo an already attempted external write. Any broader reconciliation/readback redesign is a separate task with separate approval.

## 10. Implementation sequence and completion gates

Each phase includes implementation, meaningful tests and necessary documentation updates; commit and push each coherent completed milestone. Use the current suitable checkout, inspect unrelated changes first and never include them. Do not begin production UI on assumptions that the platform/resource spike has not established.

### Phase 1 — platform, format and resource feasibility

- Build a disposable capture/encode/preview harness using synthetic fixtures and actual eligible playback. Verify consent/FGS order on target SDK and minSdk gating; API29 legacy projection coordination; modern audio-only capture; revocation and self-audio exclusion including TTS transitions.
- Measure actual capture/preview/encoder formats, decoded output, capture/scan-marker offset, disk throughput and long-session resource use. Exercise default 5/5/2 and maximum configurable values under hard-resource admission, with OCR, preview and export running.
- Record a primary-source API checklist and measured decision note: format/bitrate, chunk size, queue bound, disk/RAM/cache/FD/workspace/free-space limits, slow-write handling and tracing methodology. Recheck current Android docs when implementing; the historical research is not a substitute for target-version validation.
- **Done:** reproducible capture and decoded local output, own-sound tests, supported/unsupported matrix, fault probes, and explicit technical constants with rationale. Unavailable real-device checks are marked pending, not passed. Any required product/default change goes back to owner before proceeding on that assumption.

### Phase 2 — `core:media` state, storage and lifetime foundation

- Register module/build convention/DI; implement small facade, fakeable clock/capture/storage, frame/session identities, settings validation, resource ledger, immutable chunk publication/leases and startup cleanup.
- Implement ring/protected H/R/E transitions, stop/seal reasons, fixed snapshots and append-only Refresh. Add quota/IO/partial-file/stale-callback tests before connecting UI.
- **Done:** deterministic retention/storage tests cover REC timing/continuity/ownership cases, quotas hold with long frame positions, no growing RAM/queue/descriptor count and no edits/source eviction on pressure. API24 initialization remains safe.

### Phase 3 — live service, acquisition anchors and controls

- Implement production permission/consent/service coordinator; integrate legacy screenshot holder safely. Add bubble/settings shared runtime control, honest notification/stop and static dot.
- Attach a protected context to actual screen/app-text acquisition; reuse across word/nested lookups; preserve typed-search/photo isolation. Implement mode-stop/restart/old-scan rules and self-sound/TTS path.
- **Done:** API29 and modern overlay scenarios, startup/stop races, source policy/profile/route cases, existing screenshot regressions and capture-while-popup behavior verified. No idle writer/job when disabled.

### Phase 4 — editor model, preview, waveform and encoding

- Implement original-coordinate edits, initial range/leading-quiet rule, last-confirmed proposal, waveform summaries, time mapping, preview and single-encoder export.
- Add silence proposals/abstention, undo and revision rejection; verify boundaries, cuts and source fidelity with known synthetic samples and decoded output.
- **Done:** REC editing/fidelity tests pass with no lost/duplicated PCM at chunk/Refresh boundaries; output duration matches kept ranges within measured codec tolerance. Preview and export agree; full retained selection is supported within workspace limits.

### Phase 5 — overlay editor UI matching approved mockup

- Implement compact/wide/movable/collapsible presentation, pass-through, persistent footer/Refresh, overview drag, waveform pan/seek/zoom, attached dot, outer/internal handles, cut chips and original/result preview.
- Preserve operation across rotation, refined lookups and popup events; implement Back/Cancel/dirty confirmation and new-scan arbitration. Initial asynchronous suggestions never overwrite edits.
- **Done:** actual emulator UI/gestures verified at narrow widths and portrait/landscape, source controls remain usable, no new allowances on presentation changes, accessible labels/targets and e-ink/large-font/RTL verified. Prototype QA alone is insufficient.

### Phase 6 — Anki pipeline, templates and settings

- Add marker/catalog/inference/presets and typed local attachment/omission. Reuse existing empty-only/all fill actions; no automatic old-mapping migration.
- Integrate default/hold attachment choices with existing duplicate commands; freeze targets before menu; screenshot then audio; cancellation, Keep, mixed fields, no-marker skip and media-failure retry/Without audio.
- Add shared default switch in both settings locations, three adjacent 30-second-step controls, silence settings/info and backup/reset handling. Translate every string in all declared locales.
- **Done:** fake-backend and local-emulator Anki tests show independent `{audio}`/`{sentence-audio}`, correct field preservation, no note on cancelled stages, and no silent missing requested recording. Configuration changes cannot save to stale targets. Existing Anki commands and screenshots still work.

### Phase 7 — fault, endurance and regression acceptance

- Execute REC matrix, relevant existing E/F cases and automated checks below; record results/artifacts and unresolved real-device limitations honestly. Test near-full disk, memory pressure, process death and slow storage throughout capture/preview/export/transfer.
- Compare recording Off/On with the same build/source/device: CPU, PSS/native/renderer memory, storage/write rate, descriptors, wake/thermal/power, OCR/lookup/bubble frame times, preview/seek/Refresh response and maximum export. Include long unattended sessions beyond every cap and repeated scans/notes.
- Update `docs/architecture.md`, `docs/development.md`, `docs/usage.md`, applicable notes/module map, `ai/test-model.md` and `ai/status.md`; NOTICE only if adding dependencies/third-party assets. Public docs stay generic and call remote recognition cloud recognition.
- **Done:** no unresolved continuity/data-loss/stale-target/self-capture/unbounded-growth defects; measured limits enforce the stated contract, touched regressions pass, supported-source limitations are documented, and remaining physical-device checks are visible before feature acceptance.

## 11. Verification and acceptance

Use the REC cases as the coverage checklist, not a claim that tests already exist. Implement pure state/range/file tests in `core:media`; note/template tests in `core:anki`; overlay/controller/gesture tests in `overlay`; settings/backup/localization tests in `app`; jsdom tests for any changed bridge/page scripts. Synthetic fixtures must include long pauses inside one phrase, a pause in the middle of a word, low-volume speech, multiple cuts and a fully quiet interval.

| Requirement family | Implementation phases | Required REC coverage |
|---|---|---|
| Platform/capture/source/exclusion/control | 1, 3, 7 | REC1–11, REC34, REC74 |
| Anchors, H/R/E, continuity and race ordering | 2, 3, 5 | REC12–26, REC35, REC40–43 |
| Resource/fault/cleanup/backup/format | 1, 2, 4, 6, 7 | REC27–39, REC69–72 |
| Editor, gestures, preview, quiet/cuts and lifecycle | 4, 5, 7 | REC40–56, REC73 |
| Settings, attachment pipeline, Anki and stale targets | 3, 6, 7 | REC57–68 |

Test dispatch order permutations with fake clocks and controlled asynchronous completion, not only sleeps.
Physical-device results, decoded-media checks and memory/IO measurements need recorded evidence; a UI
automation success or waveform screenshot cannot substitute for them.

At relevant milestones and final integration run:

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest :build-logic:convention:test
npm --prefix scripts/page-tests ci
npm --prefix scripts/page-tests test
./gradlew :app:lintVitalRelease
```

Follow build-environment notes for PowerShell/JDK/SDK and instrumented-test package isolation. Never `am force-stop`; the test runner also stops its target, so use the documented separate test package. Never sync/log into/modify the owner's Anki collection. Use fakes or emulator AnkiDroid without an account; desktop playback checks use disposable test media/collection, not owner data.

Device/API coverage: API24–28 unsupported audio with existing legacy screenshots; API29 playback plus shared projection screenshot case; API30+ accessibility screenshots; current API37 target; actual phone for real sources, TTS engines, routes, power and lifecycle restrictions. Add/use an API29 environment rather than claiming API24/30 covers it. Existing test-model NF baselines apply; >1.5x comparable previous AVD timings is a finding, not a new invented acceptance budget. New audio-specific thresholds and codec tolerances must be recorded from the Phase 1 measurements before final acceptance.

Final acceptance requires both user scenarios: (1) an utterance continues during dictionary reading and remains editable after it ends; (2) a video paused mid-utterance is resumed manually, Refresh reveals the remainder, recorded pause can be excluded, and the final note has intact audible speech. Also demonstrate a several-minute definition-reading delay without losing pre-scan history, and a long absence without unbounded growth or a discontinuity join.

## 12. Resume, open engineering work and changelog

### Implementation progress

- Branch created from main; no merge to main. [Engineering measurements](../notes/sentence-audio-engineering.md)
  record the isolated API30 probe, AAC end-tail failure, Opus candidate and on-device PCM storage benchmark.
- Registered independent `core:media`; implemented/tested pure deadline/edit/initial-selection models and
  bounded file storage with snapshot/reader ownership. This independent foundation does not require the
  unresolved final codec choice. No app UI/control or Anki integration is connected yet.
- Phase 1 remains open for the explicitly listed compatibility, timestamp, self-TTS, live-queue and final
  resource measurements. Phase 2 foundation work is partial; do not mark either whole phase complete.
- Test source tones now have a 30-second maximum following the owner's audible-emulator report; require
  host-silent runs for further sound-generating experiments and explicit cleanup.

- Current work: Phase 1 on `codex/sentence-audio-recording`; do not merge to main before required checks and owner direction for this feature branch.
- No unanswered product question blocks this plan. Format/codec and numeric resource budgets are intentionally delegated measured engineering work, with a gate before broad implementation. A discovered incompatible behavior/default requires a focused owner question rather than a silent plan rewrite.
- Read this plan's decisions before asking further questions. Keep earlier discussion as history, not a second source of pending requirements. Record each changed decision and measured gate outcome here; update the REC run log when tested.
- 2026-11-19: Created from the complete discussion and approved interactive design. Final owner answers: initial 15 s before / 5 s after, >5 s leading-quiet refinement with 150 ms lead-in and all-quiet preservation; duration range 30 s–10 min in 30 s steps; Back cancels in both editor sizes; independent `core:media`. Defaults remain 5/5/2. Current localization coverage is 27 locales.
- 2026-11-19: Owner authorized implementation on a separate branch and clarified unsupported-device UI: on Android 7–9 show only availability text, hide recording settings and all recording activation/attachment affordances.
- 2026-11-19: Added independent storage/state foundation and isolated API30 feasibility evidence; actual implementation and pending gates are distinguished above. Recorded and stopped the accidentally persistent source tone and added bounded test-source lifetime.
