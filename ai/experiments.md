# Experiments

Exploratory research the owner asked for, kept apart from the plans (owner, 2026-10-12): nothing here is scheduled
or decided. Findings live in the linked notes; an experiment moves into a plan only when the owner says so.

## E1. Pause the video under the bubble (2026-10-12)

Owner's question: can the overlay pause the foreground player (YouTube, a browser player, other player apps, but not
background music in the shade or other media) when the aim is on text and the popup shows, so subtitles can be scanned
as the video goes, and play it again when the aim leaves the text, as "When the aim leaves the word" closes the popup?

State: feasible through MediaSession with notification access; open choices listed. Note: `notes/media-pause.md`.

## E2. Subtitle lines and timings from other players (2026-10-12)

Owner's question: can the app get the subtitle file, or the lines and their timings, from another player (built-in,
VLC, MX Player, mpv, YouTube, Chrome, Firefox, Jellyfin)? That would allow Anki notes with sentence audio cut to the
subtitle, and rescans when the subtitle changes without docking the bubble (with E1).

State: no general API; exact sources for Jellyfin, media3 players, local files and VLC's remote access; OCR and
playback capture elsewhere. Note: `notes/player-subtitles.md`.

## E3. Replay the line to record it; subtitles as app text (2026-10-12)

Owner's question: remember the previous and the current subtitle line with their timings, and on ➕ seek the player a
few seconds back and record the audio from the current line's start to its end. Read the subtitle itself with the app
text feature: do all players expose subtitles that way?

State: replay and capture are feasible; app text carries subtitles in almost no native player. Note:
`notes/player-subtitles.md` ("Replaying the line" and "Subtitles in the accessibility tree").

## E4. Timings without rescanning the screen (2026-10-12)

Owner's question: what other tricks could reliably see or remember subtitle timings, other than constantly rescanning
the screen to detect subtitle changes, which is expensive?

State: five ways ranked; best is a subtitle file synced by the user's own scans (OCR anchors), then asking the player,
then seek probing while paused on ➕. Note: `notes/player-subtitles.md` ("Timings without watching the screen").

## E5. Follow-up on E4 (2026-10-12)

Owner's questions: can "asking the player" be general rather than per player (an mpv script for every player built on
mpv, the same for Jellyfin-like and VLC-like mechanisms and for media3), or is stability hard to promise? For YouTube,
are options 1 and 2 out unless a third-party service downloads the subtitles; could the app download them itself,
at most asking the user for the video's link? How long would seek probing take? Would the moving video not spoil the
comparison of glyph pixels, given that subtitles may have no visible background?

State: media3 and the media servers are general and stable; mpv is not (no scripts in mpv-android, cross-app sockets
forbidden); local files are general through the session and OCR anchors. YouTube captions can be fetched by the app
itself, unofficially; the video id comes by sharing, maybe automatically. Probing 4–8 s for local files, a hybrid
with the replay 5–9 s; strokes plus outline are compared, not whole boxes. Note: `notes/player-subtitles.md`
("Follow-up: how general, YouTube, timing, pixels").

## E6. Recording the sentence audio by hand (2026-10-12)

Owner's idea: the simplest way may be to let the user record the audio when adding an Anki note, besides the
screenshot: rewind the video, start recording, stop it. Wanted: UX concepts. Nothing approved; the owner asked to
gather all variants of the video work into one document to choose from in a later session.

State: seven recording concepts (A–G), shared parts, entry points and packages that combine them with E1–E5. Note:
`notes/video-mining-concepts.md`.

## E7. Audio memory with an automatic pick, as GameSentenceMiner (2026-10-12)

Owner's question: how heavy would it be to do as GameSentenceMiner, which (as the owner understood it) records the
screen with OBS and, when a sentence is added, picks the matching audio from a buffer of a few minutes with Whisper?
And what if Screenlate recorded the audio in a separate opt-in mode and cut the fragment matching the sentence the same
way? That would also help in games such as visual novels, where one cannot go back.

State: GSM takes the line's time from its text source and uses VAD or Whisper only to trim a few seconds. For
Screenlate: concept H; continuous capture is light, recognition runs only on ➕. Note:
`notes/video-mining-concepts.md` ("H. Audio memory with an automatic pick").
