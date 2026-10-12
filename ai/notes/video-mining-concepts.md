# Mining from video: concepts to choose from

A catalog of alternatives for looking up and mining subtitles of videos playing in other apps (YouTube, browsers,
local players, media servers). Nothing here is decided (owner, 2026-10-12: "we are only thinking through options");
a later session picks from it with the owner. Questions and history: `../experiments.md` (E1–E10). Findings behind
the blocks: `media-pause.md`, `player-subtitles.md`. Sketches of every concept over today's bubble and popup (E10),
on the owner's private design canvas (read it with the Artifact tool; boards in Russian):
https://claude.ai/artifact/RYg5awh22DsEwwjym1gpne

## Building blocks

| Block | What it gives | Needs | Costs and risks |
|---|---|---|---|
| Auto-pause (E1) | The video stops while the popup shows, plays on when the aim leaves the word | Notification access | Players move subtitles above their controls on pause; capture order |
| Rescan after resume (E1) | A new scan when the aim rests after the video went on; no docking | Nothing new | One cloud request per looked-up line |
| Player remote (E1, E6) | Our own −1 s / −3 s / play / pause through the player's session | Notification access | Live streams cannot seek |
| Exact line times from a file (E2, E4) | Every line's start and end; previous line too | The file: next to the video, embedded, a media server, Jimaku/OpenSubtitles, YouTube captions, or picked by the user; OCR anchors align it | Fails when the file's text differs from the screen |
| Exact line times from the player (E2, E5) | Live current line | media3 session, or a media server's API | Only those players; text subtitles only |
| Line bounds without a source (E3–E5) | Start and end to ~0.1–0.3 s | Seeks on pause plus window screenshots, or replay and watch | 4–9 s per note; streaming slow; MKV subtitles after seeks |
| Audio from a file or server (E2) | The exact clip, silently | Folder access, or the server | Local files and servers only |
| Audio by playback capture (E3, E6) | Any app that allows capture | `RECORD_AUDIO`, the screen-capture consent, a `mediaProjection` foreground service | Indicator in the status bar; some apps forbid capture |

## Sentence audio by hand (E6)

The owner's simplest idea: next to the screenshot, let the user record the sentence's audio: rewind the video,
start recording, stop it. All concepts below record the device's playback through capture, not the microphone.

### A. Mini recorder

1. 🎙 (see "Entry points") hides the popup; the scan and its notes stay. A small pill shows at the top of the screen;
   the player stays usable under it.
2. The user rewinds in the player, taps ⏺, starts the video, taps ⏹ when the line ends.
3. The pill turns into a review row: ▶ listen, ↺ again, ✂ trim, ✓ save, ✕ discard.

```
┌────────────────┐   ┌──────────────────────┐   ┌──────────────────┐
│ ⏺ Record    ✕ │ → │ ⏹ 0:04 ▁▃▆▃▁▅       │ → │ ▶  ↺  ✂  ✓  ✕   │
└────────────────┘   └──────────────────────┘   └──────────────────┘
```

Needs only capture. Weak spot: the player's own controls (YouTube's double tap jumps 10 s, its bar is imprecise).

### B. Recorder with a remote

A, plus buttons that drive the player through its session: −3 s, −1 s, ▶/⏸, and "⏺ from −3 s" (seek back, start
recording and playback in one tap). ⏹ stops the recording and pauses the video. Without notification access it falls
back to A.

```
┌──────────────────────────────────────┐
│ −3s  −1s  ▶  │  ⏺ from −3s  │   ✕  │
└──────────────────────────────────────┘
```

### C. Instant replay

While the bubble is out, the last ~30 s of sound stay in memory. 🎙 opens a waveform of them with the scan's moment
marked and a suggested selection (the speech around the mark); the user drags the edges, listens, saves. No
rewinding. Capture runs the whole time the bubble is out (indicator on, ~6 MB of memory). With auto-pause the line's
end has not played yet: "⏵ play on" lets it finish and extends the buffer.

```
last 30 s                       scan ▼
▁▂▁▁▃▅▇▅▂▁▁▁▂▆▇▆▅▃▁▁▁▃▅▆▂▁▁▂▃
                 [=========]
▶ listen    ⏵ play on    ✓ save    ✕
```

#### C: load and storage (E8)

C is H without recognition: the same capture, an editor instead of the automatic pick. Turned on only when wanted
(a mode), so nothing runs otherwise.

- Load while on: one `AudioRecord` thread copying ~100 KB/s into a ring buffer; well under a percent of a core, a
  battery cost like a background voice recorder, small next to the screen and video decoding. Unmeasured: check with
  `dumpsys batterystats` on the phone.
- Sizes, 16-bit PCM: 48 kHz mono 96 KB/s (30 s 2.9 MB, 1 min 5.8 MB, 3 min 17 MB, 5 min 29 MB); 24 kHz mono half that
  (fine for speech, music loses its top); AAC at 64–96 kbps 8–12 KB/s (10 min 5–7 MB) at the price of encoding and of
  decoding before a cut. Mono is enough for cards.
- Memory, recommended up to a few minutes: a preallocated ring (no garbage-collection churn); nothing on disk, nothing
  to clean after a crash (the process's death frees it); no flash writes (~350 MB an hour at 48 kHz mono). A crash
  ends the capture session too, which needs a new consent, so the lost audio rarely matters. The process is held by
  the accessibility service, so the system seldom kills it.
- A file, only for long buffers (tens of minutes) or to survive a restart: segments of ~10 s in an app-private,
  backup-excluded folder (`noBackupFilesDir/capture/`), the oldest deleted past the window; everything in the folder
  deleted when the mode stops, when the projection stops, and at every service or app start (leftovers of a crash or
  kill); uninstalling removes it. Optionally AAC to shrink it.
- Opening the editor copies the window (e.g. the last 60 s) out of the ring, so capture goes on ("⏵ play on")
  without overwriting what is being edited.
- A cheap help: the editor opens with the speech before the scan already selected, by WebRTC VAD (BSD, tiny C) or an
  energy threshold; about 0.1 s when it opens, nothing while capturing.

#### C: editor variants (E10)

Four ways to show the editor, sketched on the canvas; the mode itself is a switch in the bubble's hold menu or the
settings, shown by a red dot on the bubble, with a message and "Turn on" when the system ends the capture.

- **C1, bottom panel**: the wave of the last 30 s across the screen, the scan mark, the speech before it selected,
  handles, ▶, "play on" (the selection's end follows the live edge, "enough" pauses again), save. Clear and easy to
  hit; covers the subtitle and the bottom of the frame.
- **C2, inside the popup**: 🎙 switches the popup to an "Audio of the sentence" view with back, like a nested lookup;
  after saving the popup returns with "▶ sentence" in its header. Nothing new on screen, the subtitle stays visible; a
  narrow wave (~320 dp in landscape), small handles.
- **C3, phrase strip**: at the top, the speech segments the VAD found as buttons (▶ on each, duration); the last one
  before the scan is preselected, a tap on a neighbor joins it; "finer" opens C1. Big targets, no dragging, the
  subtitle stays visible; depends on the VAD (music and noise).
- **C4, hint by the bubble**: 🎙 takes the last speech before the scan and plays it in a small pill (✓, ✂, ✕); ‹ ›
  step to neighboring segments. One tap in the usual case, the popup stays open; no wave, relies on the VAD and the
  ear; fine edits only through ✂.

### D. The bubble as the record button

🎙 turns the floating bubble red; scanning pauses. The user rewinds in the player; a tap on the bubble starts, the
next stops; then a menu by the bubble (the card the hold opens) offers ▶, ↺, ✂, ✓, ✕. No extra window over the
video.

### E. Offer after ➕

➕ adds the note at once (with the screenshot as today); a chip "🎙 Add audio" shows by the bubble for ~10 s. A tap
opens A or B; ✓ updates the note (`AnkiBackend.updateNote`). Ignored, the note stays without audio. Nothing blocks the
usual flow.

### F. In and out marks

The user rewinds and plays; taps "[" when the line starts and "]" when it ends, as in a video editor. Capture already
runs when the pill opens, so the clip keeps ~0.5 s before "[" (reaction time) and after "]". Same taps as A, better
edges.

### G. Audio pass after watching

Notes are added during the episode without audio; each remembers the app, the title and the player's position. After
the episode (or at any time) "Record audio for N notes" walks through them: the player seeks to the note's position
−3 s through its session, plays, the user taps ⏹ (or F's marks), next note. Needs the same video open and
notification access. Keeps watching uninterrupted.

### H. Audio memory with an automatic pick (E7)

An opt-in mode, off by default: while it is on, the device's playback is captured into a rolling buffer of the last
few minutes, in memory only. On ➕ the app finds the line's audio in it and attaches it; no rewinding, so it also
works in games and visual novels that cannot go back.

How GameSentenceMiner does it (checked in its source, `replay_handler.py`, `util/media/ffmpeg.py`, `vad.py`): OBS
keeps a replay buffer of video and audio; each text line gets the time it appeared (a text hooker, or OCR that runs
continuously); on a new Anki note GSM saves the buffer, cuts the audio from that line's time (with a per-source
padding: 0 s for hooked or continuous OCR, 3 s for manual or overlay scans, "best-guess") to the next line's time,
and trims the cut with voice activity detection: FireRed VAD by default, Silero, or Whisper (base by default), which
transcribes the cut and accepts it only if its hiragana is similar enough to the mined sentence's. So Whisper does not
search minutes of audio; the line's time comes from the text source, and the models only trim a few seconds.

Screenlate has no text source between scans, only the scan's moment T (the line is on screen then):
- Speech segments in the last 60–120 s before T by a VAD (Silero, MIT, ~2 MB ONNX; WebRTC VAD, BSD): ~0.1–0.3 s.
- Candidate: the latest segment before T, merged with neighbors split by short pauses. In visual novels the voice of
  the line on screen is almost always the latest voice; in video the line may still be playing at T (with auto-pause
  its end comes after the resume, so the pick waits for it).
- Check by speech recognition of the latest one to three candidates, compared with the OCR sentence in kana (GSM's
  similarity gate; our engine gives readings for the sentence), walking back until one matches.
- Low confidence: the trim editor of C opens with the candidates marked, for a manual pick.
- Without a recognition model the pick is "latest speech before T" plus a ▶ to confirm; the model only makes it
  automatic and robust.

Load:
- Continuous part: capture into a ring buffer, no recognition. 3 min of 48 kHz mono 16-bit PCM is ~17 MB; CPU around
  a percent; little next to video decoding and the screen. Our own sounds (word audio) are left out with
  `AudioPlaybackCaptureConfiguration.Builder.excludeUid`.
- On ➕ only: VAD over a minute or two, then recognition of ~3–15 s. whisper.cpp (MIT) sizes from its README: tiny
  75 MiB on disk, ~273 MB in memory; base 142 MiB, ~388 MB; small 466 MiB, ~852 MB (quantized models are smaller).
  Unmeasured guesses for a recent phone, 10 s of audio: tiny and base a second or a few, small around ten. Vosk's small
  Japanese model (Apache-2.0, ~48 MB) is lighter and weaker; enough for matching against a known sentence. The
  system recognizer can take our audio (`EXTRA_AUDIO_SOURCE`, API 33) where an on-device one exists. Models are
  downloads, not in the APK.
- The note is added at once; the audio follows a few seconds later (`AnkiBackend.updateNote`), cached per scan for
  further notes of the same line.

Limits: the mode holds the capture session (consent once per session, indicator on); apps targeting Android 9 or lower
and apps that opt out are not captured (silence, to detect and report), which may hit older visual novel engines;
calls and notification sounds are not captured (only media, game and unknown usages); privacy: the buffer stays in
memory, is dropped when the mode stops, and nothing of it is logged.

## Shared parts of the recording concepts

- **Trim editor** (✂): waveform, two handles, ▶ preview, zoom; silence cut from both ends at once. The audio twin of
  the screenshot's `CropEditor`.
- **Pre-roll**: capture starts when the recorder opens, so ⏺ can keep half a second before the tap.
- **Attach rule**: the recording belongs to the scan (one subtitle line): notes of the scan already added are updated,
  later ones get it at once (`ScanNotes`); or only the last note; or a checklist of the scan's notes.
- **Anki field**: a `{sentence-audio}` marker; the Lapis and Senren presets already list a `SentenceAudio` field and
  leave it empty (`NoteTypePresets`).
- **Capture consent lifetime**: per recording (a system dialog every time, indicator only while recording), while the
  bubble is out (once per watching session), or while the bubble is on. Since Android 14 every new capture session
  asks again; the dialog needs a transparent activity of ours over the video.
- **First use**: an explanation dialog, `RECORD_AUDIO`, then the capture consent.
- **Capture refused**: an app that forbids capture records silence; detect it and say so; maybe offer the microphone.
- **With auto-pause**: no automatic pause or resume while recording; B's ⏹ leaves the video paused.
- **Format**: AAC in `.m4a` (MediaCodec + MediaMuxer), mono; AnkiDroid and desktop Anki play it. A length cap
  (e.g. 30 s).
- **In the popup**: once recorded, a ▶ for the sentence audio in the header.

## System limits of capturing in the background (E9)

Checked in AOSP sources (main branch); MagicOS may differ and has to be checked on the phone.

- **Not silenced in the background.** Playback capture records from `AUDIO_SOURCE_REMOTE_SUBMIX`, which the audio
  policy counts as a virtual source and lets capture whatever the app's state ("Allow capture for virtual (remote
  submix, call audio TX or RX...) sources", `AudioPolicyService.cpp`), unlike the microphone. `RECORD_AUDIO` is still
  needed to create the recorder.
- **No green microphone dot.** The source is tracked under the app op `RECORD_AUDIO_OUTPUT`
  (`ServiceUtilities.cpp`, `getOpForSource`), and SystemUI's privacy indicators watch `RECORD_AUDIO` and the call and
  trigger microphone ops only (`AppOpsControllerImpl.OPS_MIC`).
- **Indicators that do show**:
  - the foreground service notification (a `mediaProjection` service is required); it can sit in a silent
    low-importance channel; since Android 14 the user can swipe it away while the service goes on, and it is listed in
    Quick Settings' active apps;
  - a projection mark in the status bar: the cast icon (SystemUI's `CastControllerImpl` follows the active
    projection) and, in newer versions, a chip for audio-only projections (`MediaProjectionManagerRepository`,
    `Projecting.NoScreen`, behind the flag `statusBarShowAudioOnlyProjectionChip`). It stays while the session is open,
    recording or not.
- **When the system ends the session**: the user stops it from the status bar chip, the cast tile or the
  notification (`MediaProjection.Callback.onStop`; the mode turns off and says so). Locking the screen and the end of a
  phone call stop projections, but not ones without a `VirtualDisplay`: "Continuing MediaProjection as current
  projection has no VirtualDisplay" (`MediaProjectionStopController.isExempt`). A new session cannot start while the
  screen is locked.
- **When the process dies** (the system or MagicOS kills it, a crash, our own update) the session dies with it and
  cannot be restored silently: the next start needs the consent again. The process is held by the accessibility
  service and the foreground service, so plain memory pressure seldom kills it; MagicOS's battery management is the
  same risk the bubble already has (battery exemption, "manage manually" for app launch: the existing background tips).
  The mode shows that it stopped (e.g. on the bubble) instead of failing silently.
- **Consent**: since Android 14 once per session (the token cannot be reused); our transparent activity shows the
  system dialog over the video and starts the `mediaProjection` service while it is in the foreground.
- **Apps that refuse**: targeting Android 9 or lower, or opting out → silence, to detect and report.
- **Screen-recording detection** by other apps (`WindowManager.addScreenRecordingCallback`, API 35) reports windows
  shown in a recording; an audio-only session records no picture (not checked).

## Entry points for 🎙

- The popup header next to the translate button (the audio belongs to the sentence, like the translation).
- The bubble's hold menu ("Record audio"), also without a popup.
- A step after ➕ (E's chip, or a question after the screenshot editor).

## Packages

- **Minimal**: A + trim editor + attach to the scan. Only capture.
- **Comfortable**: B + F's pre-roll + auto-pause; notification access and capture.
- **Automatic where possible**: exact times from a file, a server, media3 or YouTube captions, the clip cut without
  user action (from the file or by replay and capture); B when no source exists.
- **Watch first, mine later**: G on top of Minimal or Comfortable.
- **No rewinding**: C, with the trim editor as its main screen.
- **Audio memory**: H as an opt-in mode (games, visual novels, video), with A or B for when it picks wrong.

## Open choices

Base concept; entry point; attach rule; consent lifetime; where the pill sits (top, by the bubble, draggable); whether
the popup hides while recording; the field marker; microphone fallback; pre-roll and length cap.
