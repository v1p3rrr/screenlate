# Subtitles and their timings from other players

Research of 2026-10-12 (owner's request 31): can Screenlate learn the subtitle lines and their timings of a video
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
