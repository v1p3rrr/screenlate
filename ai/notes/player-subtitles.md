# Subtitles and their timings from other players

Experiments E2 and E3 (`../experiments.md`), 2026-10-12: can Screenlate learn the subtitle lines and their timings of a video
playing in another app (built-in player, VLC, MX Player, mpv, YouTube, Chrome, Firefox, Jellyfin), to cut sentence
audio for Anki notes to the line and to rescan when the subtitle changes without docking the bubble. Builds on
`media-pause.md` (notification access, MediaSession). Nothing is built; nothing checked on a device.

## No general API

Android has no way to read another app's current subtitle. What exists is per player, plus generic tricks with the
screen and the audio output. Notification access (needed for the pause anyway) is the key to most of them: it gives
every session's package, title, duration and playback position (`PlaybackState.position`, extrapolated with
`lastPositionUpdateTime` and the speed).

## Per source

- **Players built on a media3 `MediaSession`** (checked in source: Just Player `PlayerActivity`, Findroid
  `BasePlayerActivity`): a media3 `MediaController` built from the platform token (`SessionToken.createSessionToken`)
  connects to the app's session. A controller of an app with an enabled notification listener is trusted
  (`ControllerInfo.isTrusted`) and gets all player commands, `COMMAND_GET_TEXT` included, so `onCues` delivers the
  current text cues with `presentationTimeUs`, the media time the line starts; the next cue group ends it. Bitmap
  subtitles (PGS, VobSub) are dropped across processes (`CueGroup.toBundle` filters them). The text comes without
  OCR; the screen position of the words still needs OCR boxes. Findroid's mpv backend may report no cues.
- **Platform or compat sessions** (VLC `PlaybackService`, mpv-android, NewPipe, the Jellyfin app's own player, most
  closed players): title and position only. VLC's media id is `media/<library id>`, its title the media title;
  mpv's title is `media-title` (the file name unless the file has a title tag).
- **Local files** (any player): find the file by the session's title and duration in a folder the user grants once
  (SAF tree; sidecar `.srt`/`.ass` are not media files, so MediaStore does not list them), read a sidecar file or an
  embedded track (media3 extractor, Apache-2.0: Matroska text, ASS, WebVTT), and cut the audio straight from the file
  (`MediaExtractor` + `MediaMuxer` for AAC or Opus, decode and re-encode otherwise). Unknown: which track the player
  shows and a subtitle delay the user set; matching the OCR text of the screen against the lines near the position
  picks the right line and measures the offset. Network streams have no file.
- **VLC "Remote access"** (newer VLC versions): a web server on the phone, paired with a one-time code shown by VLC;
  its WebSocket `NowPlaying` message has the media `uri`, `progress`, the subtitle track list and the current track
  (`remote-access-server`, `RemoteAccessServer.NowPlaying`). Gives the file and the chosen track without guessing.
- **mpv**: the properties `sub-text`, `sub-start`, `sub-end` hold exactly what is needed, but mpv-android keeps its
  config dir and IPC socket in private storage; only a user Lua script (mpv-android is built with Lua) writing them to
  shared storage would pass them on. A hack for enthusiasts.
- **Jellyfin server** (any client: the Jellyfin app, Findroid, the web client in a browser): `GET /Sessions` gives
  `NowPlayingItem` and `PlayState` (`PositionTicks` in 100 ns ticks, `IsPaused`, `MediaSourceId`,
  `SubtitleStreamIndex`, `AudioStreamIndex`); the subtitle track comes as text from
  `Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/Stream.{srt|vtt|ass}`; the audio from
  `Audio/{itemId}/stream?startTimeTicks=…&audioStreamIndex=…` (transcoded by the server; reading only the line's
  seconds is unchecked). `POST Sessions/{id}/Playing/Pause|Unpause` can also pause the client. Needs the server
  address and an API key or login in our settings. Plex and Emby have similar APIs.
- **YouTube app**: no API; the official Data API downloads captions only for the caller's own videos. Its unofficial
  caption endpoints need the video id (whether the session's metadata carries it is unknown: `adb shell dumpsys
  media_session` while it plays) and are guarded and changed by YouTube. OCR stays the way; audio only by capture.
- **Browsers**: captions drawn in the page DOM (YouTube web and most site players) may reach the accessibility tree
  as text with bounds, without timings; unchecked (our own WebView showed no text nodes, `ocr-engines.md`). Chrome
  has no extensions; Firefox for Android has, and an own extension could read `<video>.textTracks` cues with times and
  send them to the app over localhost. Large effort.

## Generic, for any player that allows screenshots

- **Rescans**: after the pause feature resumes the video, the old scan is dropped and the next resting aim starts a
  new scan; no subtitle source needed. Detecting a subtitle change while the video plays would need screenshots
  (accessibility allows one per 333 ms, `ACCESSIBILITY_TAKE_SCREENSHOT_REQUEST_INTERVAL_TIMES_MS`), and the moving
  picture behind the text changes the band every frame, so only the pixels of the known glyph boxes could be compared.
- **Audio by playback capture**: `AudioPlaybackCaptureConfiguration` needs `RECORD_AUDIO`, the screen-capture consent
  (`createScreenCaptureIntent`, asked again for every projection since Android 14) and a `mediaProjection` foreground
  service; the system shows a capture indicator. Apps targeting API 29+ allow capture of `USAGE_MEDIA` unless they opt
  out (`allowAudioPlaybackCapture="false"`, capture policy); whether YouTube and Chrome allow it is unchecked. A
  rolling buffer of the last minute (~11 MB of 48 kHz stereo PCM) is cut on ➕. Line bounds without a subtitle source
  are guesses (screen changes or a trim editor). With the pause the line has not finished playing at ➕: the clip can
  only be completed after the resume, so the note's audio would be added later.

## Subtitles in the accessibility tree (E3)

App text (`capture/AccessibilityText`) reads only nodes of application windows that report character positions.
Checked in source, native players draw subtitles as pixels:

- media3 / ExoPlayer `SubtitleView` defaults to `VIEW_TYPE_CANVAS`: `CanvasSubtitleOutput.dispatchDraw` paints the
  cues, no text node (`VIEW_TYPE_WEB` is marked very experimental). Used by Just Player, NewPipe (ExoPlayer 2 view),
  the Jellyfin app's native player (media3 `PlayerView`, ASS through libass), Findroid and most ExoPlayer apps.
- VLC and mpv render subtitles with libvlc/libass into the video picture or a surface; no views at all. MX Player and
  the built-in players are closed; most likely drawn too.
- YouTube app: closed, unknown.
- Browsers: captions drawn by the page (YouTube web and most site players) are DOM text, so they may reach the tree as
  text, but WebView (Chrome's engine) answers with the node's bounds for every character (`ocr-engines.md`), which
  app text skips; the text could
  still serve as the exact line and a change signal, with positions from OCR. Native `<track>` captions: unknown.
- A quick check on the phone: text source "App text first", pull the bubble out over a subtitle; the popup's source
  chip says whether app text or OCR found it, and the log line "App text: N of M text nodes" counts the nodes.

So line tracking by app text works almost nowhere; OCR, media3 cues, files or a server stay the sources.

## Replaying the line to record it (E3)

The owner's idea: on ➕, seek the player back to the current line's start, record while it plays, stop at its end.

- Seeking: `transportControls.seekTo` where the session lists `ACTION_SEEK_TO` (VLC `MediaSessionCallback.onSeekTo`,
  mpv-android `MPVActivity.onSeekTo`, the Jellyfin app's `PlayerMediaSessionCallback`; YouTube's notification has a
  seek bar; Chrome to be checked); live streams cannot. After the replay the player is paused again at the line's end
  (or back where it was).
- Recording: playback capture as above (`RECORD_AUDIO`, one screen-capture consent per projection, a
  `mediaProjection` foreground service, the capture indicator). One projection can be kept for the whole watching
  session, so the consent is asked once per session, not per note. The replay is audible; whether it can be muted
  without muting the capture is unchecked.
- Alignment: `AudioRecord.getTimestamp` against `PlaybackState.position` at `lastPositionUpdateTime` maps captured
  frames to media time; a keyframe seek or the player's latency only shifts the window, which the padding absorbs.
- This solves the line not having finished at ➕ (E1 pauses mid-line): the whole line is played again.
- Line bounds:
  - exact with a text source (media3 cues, a file, Jellyfin; app text in the rare player that has it);
  - otherwise from pixels during the replay: the scan gave the line's glyph boxes, so screenshots of the app window
    (`takeScreenshotOfWindow`, API 34+, no overlays; one per 333 ms) tell when the same glyphs appear and vanish;
    ±0.33 s plus padding. Only the glyph pixels are compared, as the video behind them moves; karaoke and fades
    blur it. Without any signal: a fixed window around the pause, then speech detection or a trim editor.
- Remembering the previous line needs tracking during normal playback: free with a text source, three screenshots a
  second while the video plays otherwise.

## Timings without watching the screen (E4)

Ways to know a line's start and end without rescanning the screen while the video plays, best first.

1. **A subtitle file synced by OCR anchors.** Every scan the user makes already pairs a recognized line with the
   player's position P (from the session), and P lies inside that line. Matched fuzzily against the file's lines near
   P, each scan bounds the file's offset (offset within [P − end, P − start]); two scans far apart also give a speed
   ratio (25 against 23.976 fps releases). After that every line's time is known: the current one, the previous one,
   any. Nothing runs between scans. Where the file comes from:
   - next to the video or embedded in it (local files, any player; E2);
   - the Jellyfin server (E2);
   - Jimaku (Japanese subtitles for anime; free account and API key, searched by title or AniList id) or
     OpenSubtitles (API key), searched by the session's title; a different release is fine as long as the lines
     match the screen;
   - a file the user picks once per episode.
   Works for streaming in a browser too (e.g. a Jimaku file for an anime watched on a site). Fails when the file's
   text differs from what is shown (another translation or transcription).
2. **Asking the player** (E2): media3 cues, VLC's remote access, Jellyfin, an mpv script.
3. **Seek probing while paused**, for anything without a file, hardsubs included. On ➕ (the video is already paused
   by E1) seek through the session to P − 1 s, P − 2 s, … until the line's glyph pixels are gone, then bisect the last
   second; the same forwards. About six probes per boundary give ~0.1 s. Each probe is one screenshot of the player's
   window: `takeScreenshotOfWindow` captures the window even under an accessibility overlay (AOSP javadoc), so an
   opaque "measuring" curtain can hide the jumping picture. Accessibility screenshots allow one per 333 ms; timing
   in E5 (about 4–8 s for a local file, much longer when streaming). Probing further back finds the previous line. Then the exact range is replayed for the audio
   (E3), or cut from a file. To check per player: does the frame and its subtitle update after a seek while paused
   (YouTube, Chrome, VLC, mpv, ExoPlayer, which renders text cues on its render loop).
   - With the screen-capture consent that audio capture needs anyway, a `VirtualDisplay` gives frames without the
     333 ms limit, but of the whole display, our overlays included (no curtain).
4. **From the audio** (needs playback capture): speech segments around P by voice activity detection (WebRTC VAD,
   BSD; Silero, MIT; ffsubsync, MIT, aligns subtitle files this way) are cheap but do not always match lines, and
   music hurts them. Speech recognition with word times aligned to the OCR text is closer: the system recognizer takes
   our audio (`RecognizerIntent.EXTRA_AUDIO_SOURCE`, API 33) and may return word times
   (`EXTRA_REQUEST_WORD_TIMING`, API 34), both up to the recognizer installed; a bundled Whisper model is large.
5. **Our own player** for local files and server streams (media3, Apache-2.0): every cue, its times and the audio
   track are ours. The only full guarantee, and a feature of its own.

Not ways: session metadata carries no subtitle data; the system caption settings are style only; Live Caption makes
its own captions from speech and is missing on many phones. For completeness: watching the known glyph boxes with
screenshots and no OCR costs a screenshot copy, not a recognition, three times a second; still continuous.

## Follow-up: how general, YouTube, timing, pixels (E5)

### How general the player sources are

- **media3 sessions**: one implementation for every app whose session is a media3 `MediaSession`; nothing per app.
  `SessionToken.createSessionToken` asks the session for its media3 token and falls back to a legacy token after a
  timeout, so trying every active session is enough. media3 keeps controllers and sessions of different versions
  compatible (they live in different apps), so it is a stable channel. Limits: legacy sessions (`MediaSessionCompat`,
  ExoPlayer 2's `MediaSessionConnector`, as in NewPipe) give no cues; an app may override `onConnect` and reject or
  restrict outside controllers; only text subtitles; only while the player has a text track selected.
- **Media servers**: the Jellyfin API is the same for every client (the Jellyfin app, the web client in any browser,
  Findroid and other clients report their playback to the server); Emby keeps a nearly identical API (Jellyfin is its
  fork); Plex needs a second small adapter (`/status/sessions`). The server's position comes from the clients'
  periodic progress reports; on the phone the local session's position is the precise one.
- **Players with their own web API**: each needs its own adapter. VLC's remote access is the protocol of its web page,
  not a published API, and needs pairing with a code; Kodi has a documented JSON-RPC (player time, current subtitle,
  file). Mostly they only tell which file plays and where, which the general file route below also finds.
- **mpv inside other apps: not general.** mpv-android has no storage permission and no way to add scripts (only
  `mpv.conf` and `input.conf` can be edited; its config dir is private). mpv can open its IPC socket in the abstract
  namespace (`input-ipc-server=@name`, a line in `mpv.conf`), but Android's app isolation forbids connecting to it
  from another app: SELinux MLS allows `unix_stream_socket connectto` only between equal levels
  (`system/sepolicy/private/mls`), and every app since targetSdk 28 has its own categories. Forks that run user
  scripts could only pass the line through hacks, e.g. a script setting `force-media-title` to the subtitle, which
  mpv-android-like apps copy into the session's title. Not worth it.
- **The general route for local files** needs no player adapter: the session's title, duration and position, the
  file found in a granted folder, and OCR anchors that pick the subtitle track the player shows and its delay.

### YouTube

- No player source; the captions file is what those services and tools fetch, and the app can fetch it itself from
  the phone, no third-party service: an InnerTube player request lists the caption tracks, each a `timedtext` URL
  (json3 or srv3 with times; auto-generated tracks carry per-word times). Unofficial: YouTube changes it and since
  2025 asks for a proof-of-origin token for subtitles from some clients but not others (yt-dlp's PO token guide), so
  it breaks now and then and needs fixes; outside the YouTube terms, unfit for a store build. NewPipe Extractor
  (Java, maintained, subtitles supported) is GPL-3.0, which our rules keep out of non-GPL modules: an owner's
  decision; or an own small client.
- The video id: Share → Screenlate in the YouTube app (one tap; reading a copied link in the background is impossible
  since Android 10, so copying would need pasting into our app); maybe automatic if the session's metadata holds the
  id (unchecked), or a YouTube search by the session's title, channel (artist) and duration; in browsers the address
  bar is a text node.
- Then the position from the session and OCR anchors choose the track (manual or auto-generated) and check it. With
  subtitles burned into the video, the auto-generated track still gives the speech times for cutting audio.
- Audio: replay and capture (E3); fetching YouTube's audio stream is possible as NewPipe does, but more fragile.

### How long seek probing takes

- One probe: a seek on pause, the player decoding up to the exact frame (from the previous keyframe; anime encodes
  put keyframes 2–10 s apart), the frame and subtitle drawn, one window screenshot (at most one per 333 ms). Local
  file with hardware decoding: about 0.35–0.6 s.
- Probes: back in 1 s steps until the line is gone (1–4, lines last 1–6 s), then three halvings to 0.125 s; the same
  for the end: 8–14 probes, about 4–8 s per note.
- Streaming: a backward seek may download again (ExoPlayer keeps no back buffer by default,
  `DEFAULT_BACK_BUFFER_DURATION_MS = 0`; YouTube unknown): 0.5–2 s per probe, 10–25 s. Too slow.
- Embedded Matroska subtitles may not show after a seek into the middle of a line (mpv documents this for
  `--demuxer-mkv-subtitle-preroll`; other players may have no remedy), so a probe can see "no line" wrongly. Playing
  forward from before the line does not have this problem.
- Hybrid: two to four coarse probes back, then replay from half a second before the line's start while recording,
  watching for the line's end: about 5–9 s with the audio done. Streaming: no probes, replay from P − 5…7 s, about
  8–12 s.

### Comparing glyph pixels over a moving video

- Comparing the glyph boxes whole fails: the video between the strokes changes every frame. Only the strokes are
  compared: in the reference frame (the scan's paused frame) the pixels of the subtitle's fill color inside the
  character boxes, plus the ring of outline or shadow around them where present. A renderer draws the same line the
  same way every frame, so these stay put while the line shows; when it changes or goes, video takes their place and
  the match collapses.
- Defaults have a box or an outline almost everywhere: mpv and VLC draw white text with a black outline; ExoPlayer's
  `CaptionStyleCompat.DEFAULT` is white text on a black box without an edge; YouTube's default is white text on a
  translucent black box. A user style without either is rare.
- Weak cases: white text without an outline or box over a white scene (the strokes "match" the background); burned-in
  subtitles (compression noise: compare with a tolerance); moving or zooming telops and karaoke color sweeps (compare
  light/dark contrast rather than exact color, or give up). Fallback: recognize the small band on the probe frames
  only and compare text, slower but only on ➕.

## Coverage summary

| Source | Line text and times | Audio clip |
|---|---|---|
| Jellyfin (any client) | exact, server API | exact, from the server |
| media3 players (Just Player, Findroid) | exact, live cues | file (local) or capture |
| Local files in any player | exact, from the file, line picked by position and OCR | exact, from the file |
| VLC with Remote access | exact, file and track known | exact, from the file |
| YouTube app, network streams | OCR only | capture only (if allowed) |
| Browsers | OCR; maybe accessibility text | capture only (if allowed) |
| `FLAG_SECURE` apps | nothing | nothing (capture usually refused too) |

## Checks on the phone before choosing

- `adb shell dumpsys media_session` while each of the owner's players plays: package, title, metadata keys, position
  updates.
- Whether YouTube and Chrome allow playback capture.
- Whether Chrome's accessibility tree gives YouTube web captions as text to our service.
