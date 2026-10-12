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
