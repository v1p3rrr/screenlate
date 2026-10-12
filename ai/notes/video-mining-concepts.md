# Mining from video: concepts to choose from

A catalog of alternatives for looking up and mining subtitles of videos playing in other apps (YouTube, browsers,
local players, media servers). Nothing here is decided (owner, 2026-10-12: "we are only thinking through options");
a later session picks from it with the owner. Questions and history: `../experiments.md` (E1–E6). Findings behind
the blocks: `media-pause.md`, `player-subtitles.md`.

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

### D. The bubble as the record button

🎙 turns the floating bubble red; scanning pauses. The user rewinds in the player; a tap on the bubble starts, the
next stops; then the bubble's ring menu (like the hold menu) offers ▶, ↺, ✓, ✕. No extra window over the video.

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

## Open choices

Base concept; entry point; attach rule; consent lifetime; where the pill sits (top, by the bubble, draggable); whether
the popup hides while recording; the field marker; microphone fallback; pre-roll and length cap.
