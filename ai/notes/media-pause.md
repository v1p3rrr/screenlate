# Pausing the video under the bubble

Experiment E1 (`../experiments.md`), 2026-10-12: pause the player of the app being scanned while its subtitles are looked
up, and resume it when the aim leaves the word, leaving music of other apps alone. Nothing is built yet; the open
choices are listed at the end. Not checked on a device.

## How the overlay scans today

- One screenshot per scan: pulling the bubble out of the dock or tapping the floating bubble (`startScan`) captures the
  screen and recognizes it; moving the aim afterwards only hit-tests that frozen layout (`onAim`). The OCR boost bands
  crop the same screenshot (`scheduleBandAt`), they take no new one.
- "When the aim leaves the word" (`hideOffWord`, `hideWhenOffWord`) closes the popup when the moving aim reaches a
  place without text; the scan stays.
- So with a playing video the layout goes stale as soon as the subtitle changes: the old words can still be looked
  up, the highlight lands on a different frame, and new subtitles need a new scan (tap).

## Ways to pause another app's player

1. **MediaSession through notification access (the one that fits).**
   `MediaSessionManager.getActiveSessions(listener)` needs `MEDIA_CONTENT_CONTROL` (system apps only) or an enabled
   `NotificationListenerService` of the caller (AOSP `MediaSessionManager.java`; `MediaSessionService` checks that the
   listener is granted, not that it is bound). Controllers come in priority order and each one has
   `packageName`, `playbackState` and `transportControls.pause()/play()`. The accessibility service grants none of
   this.
   - Precise: pause only the controller whose package is the app under the aim and whose state is `STATE_PLAYING`
     (and whose actions include pause); music of another package is never touched. Resume only the controller paused
     by us, only if it is still active and still `STATE_PAUSED` (the user has not played or switched it meanwhile).
   - The window under the aim: `ForegroundWindow.pick` gives the focused app; for picture-in-picture or split screen
     the app window whose bounds hold the aim is the better target.
   - The listener needs no code: an empty `NotificationListenerService` subclass with
     `BIND_NOTIFICATION_LISTENER_SERVICE`; it reads no notifications. The system dialog still warns that the app can
     read all notifications, so our explanation dialog must say what it is for.
   - Grant flow: `Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` with the component (API 30+), the list page as
     fallback. Notification access is a restricted setting for apps installed outside a session-based installer
     (Android 13+, Enhanced Confirmation on 15), like accessibility; the "Allow restricted settings" the owner already
     used for accessibility should cover it, to be checked on the phone.
2. **Audio focus** (`AUDIOFOCUS_GAIN_TRANSIENT`, then abandon): well-behaved players (ExoPlayer, Chrome) pause on a
   transient loss and resume by themselves on the gain, and no permission is needed. Rejected as the main way: focus
   goes to whoever holds it, which can be background music; a normal app cannot tell which app plays
   (`AudioPlaybackConfiguration` hides the client uid). Since API 35 a focus request must come from the top app or a
   process in the foreground (`HardeningEnforcer.blockFocusMethod`, app op `TAKE_AUDIO_FOCUS`); whether the bound
   accessibility service qualifies without the keep-alive service is unchecked.
3. **Media keys** (`AudioManager.dispatchMediaKeyEvent`): go to the system's media button session, the app that last
   played; with nothing playing a PLAY key can start the music app. Rejected.
4. **Tapping the player's pause button through accessibility**: YouTube shows it only with controls visible, labels
   are localized, every app differs. Rejected.

## Which players have a session

- Expected to work: the YouTube app; Chrome and Firefox for web video with sound (Chrome's media notification once
  needed media over 5 s; muted autoplay video has no session); local players (VLC, MX Player, mpv, Just Player) and
  streaming apps.
- Streaming services that set `FLAG_SECURE` (most paid ones) give a black screenshot, so their subtitles cannot be
  scanned anyway; app text rarely carries subtitles (ExoPlayer's `SubtitleView` draws them on a canvas).
- Live streams without pause support do not list the pause action; nothing happens there.

## Pitfalls to design for

- Pausing changes the picture: many players show controls, dim the frame and move subtitles above the controls (seen
  in other players; YouTube's behavior to be checked). Capturing before the pause keeps the subtitle as read but may
  leave the highlight off the moved text and make "leaves the word" misfire; pausing first and capturing once the state
  is `STATE_PAUSED` and the frame settled costs some delay per scan.
- The pause should come at the scan's start, not when the popup appears: recognition takes 1.3–4 s and the subtitle
  may change meanwhile.
- After a resume the layout no longer matches the screen. Scanning subtitles "dynamically" needs either a tap per new
  subtitle (today's rescan) or a new scan started by the moving aim once the old one is dropped; each scan is a cloud
  request plus on-device recognition, so many scans may meet the cloud's rate limit.
- Resume rules: also on docking and on closing the popup; not while the crop editor is open; not when the app left
  the foreground (the video would play in the background).
- Privacy: no media titles or metadata in logs.

## Open choices for the owner

- Trigger of the pause: scan start (recommended) or the popup showing.
- Trigger of the resume: tied to "When the aim leaves the word" or its own rule; also on docking and on a closed
  popup.
- New subtitles after a resume: tap as today, or an automatic new scan when the aim comes to rest.
- Capture order: before the pause or after it.
- The setting's place and default, and the notification access dialog.
- Whether audio focus is a fallback when notification access is not granted.
