# Video audio mining: recovered context

2026-11-18. Context recovery requested by the owner. Research only: no concept, default, UI placement or implementation is approved by this note.

## Finding and approval status

The full early catalog was absent from the current `main` checkout. It survives in the local remote-tracking ref `origin/claude/gallant-hawking-pr16aj`, tip `a39dc968` (2026-10-12). Its `ai/experiments.md` explicitly says that nothing is scheduled or decided and that an experiment enters a plan only at the owner's instruction. The concepts and sketch README repeat that nothing is decided. No subsequent approval was found in the checked project plans, notes or available Git history.

The owner explored C (instant replay with manual trimming) in greater detail after H (automatic picking), asking about cost, optional activation, RAM versus files and background restrictions. This is evidence of interest, not a recorded selection. The 2026-10-16 research independently develops the rolling-buffer direction, but explicitly labels its technologies, defaults, UI and markers as proposals requiring later decisions. Do not turn either document into a binding decision table.

The existing language-plan Backlog item "Word audio on the phone" is different: it concerns offline pronunciation collections, not recording sentence audio from a video or game.

## Source map

Primary historical records (immutable Git snapshot; use `git show a39dc968:<path>` if a web link is unavailable):

- [Experiments E1-E10](https://github.com/v1p3rrr/screenlate/blob/a39dc968/ai/experiments.md): the owner's questions and the sequence of exploration.
- [Concept catalog](https://github.com/v1p3rrr/screenlate/blob/a39dc968/ai/notes/video-mining-concepts.md): A-H, C1-C4, attachment rules, storage, capture lifetime, entry points and packages.
- [Player pause research](https://github.com/v1p3rrr/screenlate/blob/a39dc968/ai/notes/media-pause.md): foreground-player targeting, rejected control mechanisms and open interactions.
- [Subtitle and timing research](https://github.com/v1p3rrr/screenlate/blob/a39dc968/ai/notes/player-subtitles.md): source-specific integrations, replay, OCR anchors and seek probing.
- [Sketch sources and README](https://github.com/v1p3rrr/screenlate/tree/a39dc968/ai/sketches/video-mining): overview, entry points, A-H and C1-C4. These `.dc.html` files require the original Design artifact runtime; they are not standalone HTML.
- [Private design canvas](https://claude.ai/artifact/RYg5awh22DsEwwjym1gpne): URL recorded in the repository; access and current rendering were not checked in this session. Boards were written in Russian for the owner.
- [Camera and audio feasibility, 2026-10-16](camera-and-audio-feasibility.md): later scope estimates, buffer/edit/export architecture and four reusable research prompts. The camera feature subsequently received a separate implementation; that does not approve audio.
- [Audio platform research, 2026-10-16](audio-capture-platform-research.md): more conservative Android compatibility, session ownership and source-selection constraints.

## Original problem and discussion sequence

1. Pause only the video under the aim while looking up its subtitles, and optionally resume when leaving the word; leave unrelated background music alone.
2. Obtain subtitle text and timings from players, local files or servers, enabling sentence-audio cuts and updated subtitle scans.
3. Replay the current line on Add to Anki and record it; investigate whether subtitles are available as accessibility app text.
4. Find line boundaries without continuously rescanning the screen: subtitle files synchronized by OCR, player cues, seek probing, audio analysis or an own player.
5. Check how generic player integrations are, whether YouTube captions can be fetched, and the cost/reliability of seek probing.
6. Consider manual sentence recording as a simpler workflow and collect UX alternatives for a later choice.
7. Explore audio memory with automatic selection, inspired by GameSentenceMiner, including games and visual novels where rewinding is impossible.
8. Explore instant replay with manual trimming, optional activation, RAM versus files and system/background restrictions.
9. Draw all concepts, with four editor variants for instant replay.
10. The 2026-10-16 follow-up considers pinning history on long-press Add, then screenshot cropping, then audio editing, optional continuation segments and independent media control. This sequence remains a proposal.

## Recording concepts

All A-H primarily record internal playback, not a microphone. The microphone appeared only as an unresolved fallback when playback capture is refused.

| Concept | Interaction | Main dependency or tradeoff |
|---|---|---|
| A. Mini recorder | Hide the popup, rewind manually in the player, record/stop in a small pill, listen/retry/trim/save/discard | Simplest capture workflow; depends on the player's own rewind controls |
| B. Recorder with remote | A plus -3 s, -1 s, play/pause and "record from -3 s"; Stop also pauses the player | MediaSession access and usable seek/control actions; A as a possible fallback |
| C. Instant replay | Explicitly enable audio memory; retain recent playback; open its waveform, trim, preview and attach without rewinding | Continuous capture only while enabled; finite RAM buffer; needs continuation if the sentence was paused halfway |
| D. Bubble recorder | Turn the bubble into a red record/stop button, temporarily suspending scanning; review from a menu beside it | Reuses the familiar control but changes its interaction mode |
| E. Offer after Add | Add the note immediately, then briefly offer "Add audio"; record via A/B and update that note | Keeps adding fast, but introduces later note updates and cancellation/race handling |
| F. In/out marks | Replay and mark sentence start/end with two taps, keeping a little audio around the taps | Pre-roll/post-roll compensate for reaction time; still requires replay |
| G. Audio pass later | Save notes and media positions while watching; later walk through them and record each line | Same video/session must still be available; persist associations and update notes later |
| H. Automatic audio memory | Buffer recent playback, find speech near the scan, optionally recognize and compare candidate audio to the OCR sentence; attach automatically | VAD and optional ASR models, uncertain matching and a manual editor fallback |

These are composable alternatives, not eight mutually exclusive implementation plans. Original bundles: Minimal = A + editor + attach to scan; Comfortable = B + F pre-roll + auto-pause; Automatic where possible = exact timings/file or replay plus B fallback; Watch first, mine later = G; No rewinding = C; Audio memory = H plus manual fallback.

### Instant replay editor variants

- C1: a wide bottom waveform panel with scan marker, handles, preview and continuation. Easy targets, but covers the subtitle/bottom of the frame.
- C2: an audio editor inside the popup, like a nested lookup, returning to the dictionary view afterward. Preserves the subtitle but offers a narrower waveform.
- C3: a top strip of VAD-detected phrase buttons; select/join neighboring phrases, open C1 for finer edits. Depends on speech segmentation.
- C4: a small pill beside the bubble previews the latest speech; accept, discard, move to neighboring fragments or open trim. Compact, but relies on listening and the VAD selection.

Proposed entry points: popup header beside sentence translation; bubble hold menu; a step or chip after Add. Mode activation was proposed in the bubble hold menu or settings with a recording/status mark on the bubble. None was chosen.

## Exact timing and automatic selection

A MediaSession normally gives media identity/position/control, not universal subtitle text and timings. The older research explores media3 text cues, local sidecar/embedded subtitle tracks, VLC Remote access, Jellyfin and similar servers, and an own local player. These require separate integration and compatibility verification. They are not prerequisites for C with manual trimming.

A subtitle file can be aligned using existing user scans as OCR anchors: recognized text plus player position identify a line and estimate subtitle offset. It fails if the file's text differs. Browser extensions and unofficial YouTube caption extraction were also explored, with maintenance, permissions and licensing issues; they were not selected.

Seek probing would move backward/forward while paused and compare subtitle glyphs, then replay the bounded line. Historical estimates were 4-8 seconds for probing local media, 5-9 seconds for probing plus replay, and substantially longer for streams. These are unmeasured estimates, not current performance guarantees. Subtitles that fail to render after seeking, fades, karaoke, moving backgrounds and stream buffering weaken it.

The historical GameSentenceMiner observation was that text-source timestamps locate a candidate interval; VAD/Whisper trim or check that interval rather than magically search all buffered minutes. Screenlate has only discrete scans, so exact text/audio alignment is an extra problem. The old H proposal compared Japanese readings/kana; a future implementation must use language-aware matching behind language support, not a Japanese-only common path.

## Current implementation inventory (2026-11-18)

Checked against the current `main` checkout (HEAD `00f131c2`, plus unrelated working edits). In user-facing terms, the reusable media features are the existing screenshot attachment and word pronunciation from audio sources; the legacy capture service is the internal screenshot backend for Android 7-10:

- `core/anki/.../AnkiBackend.kt` already offers `addMedia(File, preferredName, MediaKind)` and `updateNote`. FileProvider-backed media export is documented in `docs/architecture.md`.
- `core/anki/.../AnkiNotes.kt`: `NoteRequest` has one `audio: AudioClip?` for `{audio}` pronunciation, plus a screenshot. A separate sentence recording attachment is not modeled.
- `core/anki/.../audio/AudioFinder.kt`: `AudioClip(file, url, extension)` reflects downloaded pronunciation. Reusing it by inventing a URL for local recordings would be a poor seam; generalize or add a local media attachment model.
- `overlay/.../anki/PopupNotes.kt`: existing note context, language capture, crop editor, selected pronunciation clips and scan lifetime can be reused. The history must be pinned before awaiting crop/editor interaction. One pinned draft must not be overwritten by continued capture.
- `{sentence-audio}` was a suggested new marker, not an existing implemented marker. `FieldTemplateTest` expects the `SentenceAudio` field to remain empty under current inference.
- `overlay/.../capture/` now contains `MediaProjectionConsentActivity`, `MediaProjectionHolder` and `MediaProjectionService` for Android 7-10 screen capture. The October statement that no such service exists is outdated.
- `overlay/src/main/AndroidManifest.xml` limits `FOREGROUND_SERVICE_MEDIA_PROJECTION` to API 29. This legacy screenshot flow is not ready for modern playback recording as is. No playback recorder, runtime `RECORD_AUDIO` flow or notification-listener media-control service was found in the app's own sources. Unbuilt MNN demo sources do not count.
- `build-logic/.../AndroidCommon.kt` sets minSdk 24 and targetSdk 37. Platform playback capture begins at API 29, so an eventual feature needs an API gate without raising the whole app's minimum by implication.

## Engineering work and limits

Provisional implementation shape, not an approved plan:

- First test the intended real phone and actual players for capture and media control independently. A player can support either capability without the other.
- Add an explicitly user-started capture lifecycle, permission/consent UX, foreground-service status, stop action and revocation handling. Accessibility screenshot permission does not authorize audio recording.
- Introduce recording/control interfaces and a separate PCM editing layer; `core:media` was proposed, not selected. Keep app/overlay dependencies pointing inward.
- Read AudioRecord on a worker thread into a bounded timestamped/frame-indexed PCM ring. Copy the chosen history into an immutable note draft. Freeze language and source/session identity.
- Build waveform/trim/preview/confirm/cancel, with bounded temporary storage and cleanup. For continuation, join explicit segments while omitting the known non-recording gap; preserve real quiet pauses inside segments. Automatic removal of all silence can damage quiet syllables.
- Keep Screenlate's pronunciation and preview playback out of the source capture, using source UID filtering where appropriate. Same-UID browser tabs/players remain ambiguous.
- Encode the confirmed cut, provisionally AAC/M4A via MediaCodec/MediaMuxer; check actual playback in both AnkiDroid and desktop Anki. Capture does not require saving screen video.
- Add a sentence-audio attachment/marker, template/preset handling, and a defined rule for new versus already-added notes. Update-after-add needs precise note identity and cancellation/race handling.
- Add auto-pause/resume separately if selected: match the foreground player, check available actions, use explicit pause/play, track the exact session and pause ownership. Ordinary state callbacks do not reveal every manual control press. Leave paused when ownership is uncertain. Do not target whichever background session happens to be first.
- Coordinate pause acknowledgement, screenshot and audio timestamps. OCR may still be reading a screenshot whose video has already advanced; pausing can move subtitles or show controls. Decide scan-start versus valid-word-hit pause and capture order explicitly.
- Add unit tests for ring rollover, immutable snapshots, trim boundaries, joins, cancellation and note associations; page tests for any JS editor; device checks for real player output, revocation, source changes, self-audio contamination, session changes, pause races, lifecycle, battery and memory. Extend `ai/test-model.md` when implementation is approved.

Historical storage arithmetic: 48 kHz mono 16-bit PCM = 96,000 bytes/s; 30 s = 2.88 MB, 60 s = 5.76 MB, 3 min = 17.28 MB. Stereo doubles those values. The proposed editor snapshot temporarily adds another copy. RAM was recommended for short buffers; segmented app-private files for long buffers. Buffer length, channels and format remain choices.

The old claims of roughly one percent CPU, tiny battery cost, VAD/ASR latency, protection from process death, exact system indicators and audio-only survival of lock/calls were not measured on the owner's device. Treat them as historical hypotheses. A foreground service does not guarantee survival of OEM process management. ASR model sizes and runtimes need fresh investigation if H is chosen.

Historical 2026-10-16 estimates: basic buffer + trim/preview + export with manual pause, 3,000-5,000 production lines and 1,000-2,500 test lines; full scope with continuation and automatic control, 5,000-9,000 production and 2,000-4,000 test lines total. These are old order-of-magnitude estimates with at least factor-of-two uncertainty, not a current quote or schedule. Basic recording/editing requires no LLM during app use.

## Open decisions preserved for a later implementation discussion

No answers are assumed here. Before writing an implementation plan, collect and settle the applicable choices with the owner.

- Base experience: manual recorder, replay buffer/manual editor, automatic matching, or a staged combination; whether auto-pause belongs in the first scope.
- Entry point, editor placement and whether to hide the dictionary popup; mode activation and status/stop affordance.
- Buffer duration, RAM/files, capture session lifetime, pre/post-roll, clip cap, mono/stereo and encoder format.
- Attachment rule: one note, last note, all notes of the scan, or an explicit selection; attach before adding versus update afterward.
- Sentence-audio field marker and preset behavior, distinct from pronunciation; behavior when capture is unavailable or produces silence, including whether microphone fallback is wanted.
- Continuation recording and optional VAD/silence suggestions; preserve user pauses versus omit only known segment gaps.
- Auto-pause trigger, screenshot order, resume trigger, manual-override uncertainty and rescan behavior after resume; source/session selection for background audio, PiP, split screen and browsers.
- Which real players/content form the initial compatibility target; whether file/server subtitle integration or ASR is worth including later.

## Platform verification (2026-11-18)

Research only. No product decision, device experiment, or application change.

### Documented requirements

- Playback capture exists from Android 10/API 29. It uses `AudioRecord` with an `AudioPlaybackCaptureConfiguration`, requires `RECORD_AUDIO`, user-approved `MediaProjection` consent, and source/capturing apps in the same user profile. Only `USAGE_MEDIA`, `USAGE_GAME`, and `USAGE_UNKNOWN` with effective `ALLOW_CAPTURE_BY_ALL` qualify. Source policies can deny capture; the most restrictive manifest/app/player policy wins. Apps targeting API 28 or below opt in; API 29+ apps allow it by default but can opt out. Usage and UID filters select/exclude sources; matching and exclusion methods for the same dimension cannot be combined. Revocation stops audio delivery; handle `MediaProjection.Callback.onStop()` instead of recording silence. These requirements do not promise compatibility with any named player or title. [Android playback capture](https://developer.android.com/media/platform/av-capture).
- On current targets, declare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, and the service's `mediaProjection` type. Request and obtain capture consent before starting that foreground service; obtain the projection after service creation. Apps targeting Android 15+ cannot launch this service from `BOOT_COMPLETED`. [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#media-projection).
- Consent is per projection session. On Android 14+, do not reuse a consent result for multiple `getMediaProjection()` calls or call `createVirtualDisplay()` repeatedly on one projection. Screen-capture documentation describes one virtual-display creation per session; do not extrapolate that into a requirement to create a display for audio-only capture. A stopped projection is invalid. The guide lists user stop, locking, another projection, and process death as termination causes, and describes a prominent stop chip and lock-screen auto-stop for screen projection on Android 15 QPR1+. [Media projection](https://developer.android.com/media/grow/media-projection).
- Other apps' active platform media sessions can be discovered with `getActiveSessions(ComponentName)` using an enabled `NotificationListenerService` or `MEDIA_CONTENT_CONTROL`. An accessibility service is not listed as authorization. Select the intended package/session rather than assuming the highest-priority session is the visible player. [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName)).

### Requires device experiments or further evidence

- Capture availability and pause/seek/play support are independent. Test the actual apps, content, session metadata, and capture output. A shared UID does not distinguish browser tabs.
- Earlier notes claim audio-only projection survives locking/calls and shows no green microphone dot. This verification does not establish those claims across Android releases or the owner's firmware. Treat them as implementation/device checks, not guarantees. The current screen-projection guide explicitly documents lock-screen termination.
- CPU/battery estimates below one percent were never measured here. Bounded PCM size is calculable (48,000 frames/s * 2 bytes/sample * channels * seconds); actual load, wakeups, thermal effects, and system/OEM process survival require measurements.
- Do not infer that all protected titles behave identically, that silence proves a particular refusal reason, or that projection consent can be silently restored after session/process termination.

## Owner follow-up and revised recommendation (2026-11-18)

This section records the owner's scenarios and a design recommendation, not an implementation plan or approved defaults. The owner tentatively favors C; UX is explicitly for later discussion.

### Requirements and scope supplied by the owner

- A spoken utterance can contain substantial natural pauses (example: 1.5 seconds). Speech-activity segments are not sentence boundaries. Splitting at every quiet interval can damage the intended clip.
- Screenlate does not track every subtitle's appearance/disappearance. Without subtitle timing or audio/text alignment, the latest speech segment may belong to a different line. Background neural inference is not an assumed requirement.
- Visual novel: lookup can start while a character is still speaking. For the initial scenario, the user requests the audio attachment after the utterance ends; the buffer must retain both earlier speech and speech produced after the scan. The source may not support rewinding. A later utterance may already be playing while the popup still shows the earlier line.
- Video: the user may pause while subtitles are visible and speech is incomplete, including halfway through a word. After lookup, the audio workflow must support continuing playback manually and appending the missing sound. The wait while paused needs removal or an editable exclusion, with manual/optional automatic help still to be decided.
- Utterances can be long or short. A fixed latest-short-fragment assumption is inadequate.
- Automatic pause and resume are outside the currently discussed feature. Subtitle timing integrations, an own video player and screenshot-history extensions are separate future topics. The owner acknowledges that the visible picture can change while an earlier line stays selected; do not silently add screenshot-history scope.
- Automatic grouping could be reconsidered with editable joining/selection, but it must also handle natural pauses and continuation interrupted mid-word.

### Proposed core behavior

Use C as the recommended basis: opt-in rolling audio history, a stable audio draft associated with the scanned context, adjustable start/end, preview and continuation. Keep the continuous original and represent edits as retained/excluded time ranges until final export. Natural pauses inside the chosen utterance remain intact. Nothing in a VAD result proves that it matches the OCR sentence.

Associate a marker with the actual screenshot/text acquisition time, rather than only the later Add action or OCR completion. A marker is a navigation aid, not an inferred sentence start/end. At Add, retain history around that marker through the time of the action; allow navigation earlier/later so a subsequent utterance does not silently replace the selected context. Pending context must protect its required history before ring rollover; choose explicit buffer/draft budgets later and expose unavailable/expired history. Long reading time cannot imply unlimited memory or guaranteed recovery beyond the retained window.

For a completed visual-novel utterance, the user selects one continuous range containing the utterance, including its natural internal pauses. Speech after scan acquisition is still available. Newer speech can be excluded by moving the end; speech segmentation is not required. If the beginning predates the retained history, the app cannot reconstruct it.

For paused video, protect the initial history and keep capture running while the user operates the player. The owner subsequently clarified that continuation should extend access to this same continuous recording, rather than require stopping capture and starting a second recording (see the follow-up below). The user needs boundary adjustment or an editable internal exclusion to remove the actual playback wait; editor buttons alone do not identify pause/resume boundaries.

Quietness cannot distinguish a dramatic pause, a paused player and a gap with background music. Without player-state timing or subtitle/audio alignment, automatic detection can suggest ranges but cannot guarantee the correct cut. Even player-state events can lag actual audio, so retain adjustable edges. Reading playback state would be an additional integration/access decision, not a hidden dependency of basic recording.

For a word interrupted halfway, preserve all available samples immediately before and after the interruption. A correct join should reconnect adjacent source audio; broad silence thresholds, obligatory padding or crossfades can delete or alter phonemes. Keep edits reversible, allow a short user-adjustable join gap, and do not claim to recover samples the source/capture did not produce or retained history lost. User seeking or source switching during continuation can create duplicates or missing speech; do not promise seamless automatic correction without timeline information.

### Role of automation and remaining choices

VAD or amplitude analysis may later supply visual hints, candidate edges, or navigation among speech regions. Selecting neighboring regions should retain the intervening natural pauses unless the user explicitly excludes them. Automatic transcript matching is separate future work; the current scan supplies too little temporal information for guaranteed automatic sentence selection. No ASR model is required for the proposed core.

Still open for the later plan: retained history and protected-draft budgets; whether audio history is protected at scan start or a later explicit association; initial selection shown to the user and when its end follows new audio; editable internal exclusions and optional quiet-edge suggestions; handling missing history/source changes; marker/attachment behavior and UI. Auto-pause/resume is already out of scope for this discussion. Ask the owner about actual behavior choices before writing an implementation plan; do not convert recommendations into settled defaults.
## Continuous capture clarification (2026-11-18)

Owner requirement: capture must continue while the popup is visible and while the user starts adding a card. This covers a visual novel still speaking, video mined without pausing, and manually paused video resumed to obtain the rest of an utterance. Continuation means allowing later audio from the same ongoing history into the draft. Starting note creation must not permanently freeze the available history at that instant. Confirming/exporting the selected clip must not itself stop the enabled global capture mode. Explicit mode stop, system revocation and lifecycle failure handling remain separate.

The owner also considered two scenario-specific interactions: audio completed on the fly versus paused-video continuation beginning around lookup/popup display, and asked for a more universal recommendation. No separate mode, button placement or timing rule was selected.

Revised recommendation: one recorder and one continuous timestamped source history, with a protected context around scan acquisition. The editor can use already available sound or expose later audio while the user waits/resumes the source manually. Popup display is a later UI event and cannot be the only start boundary; earlier audio must remain available. A scan marker also does not supply the utterance's exact boundaries.

Keep capture, source history available to the draft, the user's selected ranges, and final exported media distinct. Newly captured sound may extend the editable history without silently moving a selection the user already adjusted. How the selection initially follows the live edge and how the user fixes its end are later UX choices. Do not impose a fixed extra duration or use silence as proof the utterance ended. A separate recording session per continuation and automatic segmentation into spoken phrases are unnecessary for the recommended core.

For a completed utterance, select and confirm the existing range. For an unfinished utterance, retain the old part and wait for later samples, preserving access to source playback controls; then select/confirm the complete range. For a manually paused source, the same timeline includes the wait. Removing that internal wait remains a separate reversible edit; continuous capture does not identify its exact boundaries. Natural pauses inside the utterance remain untouched unless explicitly edited. Buffer/protected-history budgets still need decisions and cannot imply unlimited retention.

This supersedes the assistant's earlier separate-recordings explanation. C remains the preferred direction under discussion; recording continuity is an explicit owner constraint. UX, retention, internal-gap assistance and final attachment behavior are not approved by this clarification. Auto-pause/resume remains outside scope.
