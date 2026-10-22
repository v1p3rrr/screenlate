# Phone makers' background limits and the service turning off

What the system does to an accessibility service whose process dies, what phone makers add, and the first report from
a ColorOS phone. Human docs stay generic (`docs/usage.md`, "Background work"); vendor names belong here.

## Platform facts (AOSP)

- A force-stop takes the package's accessibility services off `enabled_accessibility_services` and persists it
  (`AccessibilityManagerService.onHandleForceStop` → `onPackagesForceStoppedLocked`, checked in `main`, 2026-10-20). The
  switch in the accessibility settings goes off and stays off until the user turns it on. That is why the emulator
  checks never use `am force-stop` (`build-environment.md`).
- A process that dies without a force-stop (crash, low-memory kill) leaves the service enabled: the connection is
  marked crashed (`getCrashedServicesLocked`, summary "Not working"), and ActivityManager starts the process again
  through the binding, normally about a second later. A process that crashes again within a short time is not
  restarted; the service then stays enabled but dead until the user turns it off and on or the app is started.
- Only these turn the switch off: the user (switch, accessibility shortcut), the app's own `disableSelf()`, a
  force-stop, removing or disabling the package, a device policy. v0.2.3 never calls `disableSelf()`.
- The accessibility shortcut ("accessibility button" or volume keys): for a service without
  `flagRequestAccessibilityButton` a tap turns the service on or off. Screenlate's config
  (`overlay/src/main/res/xml/accessibility_service_config.xml`) does not request the button, so the shortcut is a plain
  on/off switch. In current AOSP a force-stop also removes the button target of a service that does request it (flag
  `disable_continuous_shortcut_on_force_stop`), so requesting it would not protect anything.
- When the process dies, the system removes every window of the service at once: bubble, popup and highlight layer
  vanish in one frame.
- Exit reasons: `ActivityManager.getHistoricalProcessExitReasons` (API 30+) tells the app itself why each earlier
  process ended (crash, native crash, low memory, user requested / force-stop, freezer, other with a description).
  `adb shell dumpsys activity exit-info <package>` prints the same without any app change. v0.2.3 does not read them;
  the temporary diagnostics branch does (`exit` events, `notes/diagnostics.md` on that branch).
- The app's own log export (About → Logs) holds only lines of the app's UID: a Java crash (`AndroidRuntime`, "FATAL
  EXCEPTION") and the `libc` "Fatal signal" line of a native crash are there; a stop by the system is not (it is
  logged by system_server), so a log that just ends without a crash line points at a stop from outside.

## ColorOS (OPPO, realme, OnePlus)

- ColorOS stops background apps through its own managers on top of Android's battery optimization: per-app "Battery
  usage" (background activity, auto launch; labels differ between versions), battery "optimization" and freezing
  features, the security app's auto-optimization, and clearing recent apps, which force-stops every app whose card is
  not locked. A force-stop by any of them turns the accessibility switch off (see above).
- The accessibility page of an app shows a ColorOS banner: once accessibility is on, the app may start automatically
  regardless of the auto-launch settings. So the auto-launch setting is not what restarts the service.
- DontKillMyApp has data on one old OPPO model only (accessibility services killed at every screen-off; workaround: lock
  in recents, allow startup, no battery optimization, a foreground notification). Nothing newer was found.
- ColorOS's accessibility page appears to show whether the service runs rather than only the stored list: in the
  report below the switch went off the moment the process died and came back on with the service.
- Screenlate's Background work screen opens `com.coloros.safecenter` startup screens (`background/StartupScreens.kt`);
  whether they exist on ColorOS 15/16 is not checked.
- A Reddit thread in r/Oppo (about July 2026, "New bug on update: system auto turns off all…", seen by the owner as
  screenshots; reddit.com is closed to the session's tools) reports ColorOS 16.0.9.403 on a Find X9 Ultra turning off
  the accessibility services of all downloaded apps at once, in the poster's test the moment the screen was locked,
  with background activity allowed for those apps. A Find X9 Pro on build 400 and another user did not see it. Another
  user found all app permissions reset by the same update (an alarm app stopped working) and some recents locks
  undone. So ColorOS 16 builds can turn off downloaded apps' services, and an update can reset per-app settings.
- Web research of 2026-10-22 (sources checked by hand are marked so):
  - OPPO's reply relayed on a Chinese screen-reader forum (bbs.tatans.cn/topic/123754, 2026-05-19; checked): ColorOS 16
    "gold" builds 16.0.7.200-205 (Find X9 Ultra, Find X8 Ultra, OnePlus Ace 6T, OnePlus 13) shut a sideloaded screen
    reader off at the first unlock after boot. OPPO: its security team now restricts accessibility because of abuse;
    apps from OPPO's store are not affected, only sideloaded ones (APK or third-party sources); a sideload whitelist
    for screen readers was requested. Workaround given: reinstall from OPPO's store.
  - ownvoice, an app distributed on GitHub (PR #252 and its install guide, 2026-10-19; checked): "ColorOS security
    apps" (OPPO, OnePlus, realme) turn off a sideloaded app's service about 2 s after its panel shows over another app;
    its guide adds the app to the allowlist first (Phone Manager, or Security > Viruses & risks > Allowlist). Not
    reproduced by its author, no device or version named. The closest match to our 1.2 s after the bubble is released.
  - OPPO's own post (community.oppo.com thread 2149746442149822464, 2026-07-02) and a June 2026 ColorOS 16 update in
    India describe a prompt with "Turn off Accessibility" for non-official apps behaving suspiciously; a README of
    ShakeGuard (ColorOS 16.0.10.500) shows the dialog "关闭无障碍 / 保持开启" after a service connects. These are
    prompts the user answers, not a silent switch-off.
  - OPPO's ColorOS 14 security white paper: Phone Manager → app security control → "风险行为拦截" (risky behaviour
    interception) watches running apps and can "disable sensitive permissions" or allowlist the app. Probably the
    engine behind the ownvoice allowlist (inference).
  - A different symptom on OnePlus (GitSync PR #1347, Android 16): `o-kill` in exit-info, the service listed as crashed
    with its switch on, not bound again until switched off and on; their fix was a separate process for the service.
    Our case (process alive, switch off) does not fit a kill.
  - Not applicable: restricted settings and in-call blocks act when the service is turned on; Play Protect's enhanced
    fraud protection acts at install; Android 17 Advanced Protection is Android 17 only.

## Report: OPPO Reno 15, ColorOS, v0.2.3 arm64-v8a (2026-10-20)

The user reported that the bubble and the popup disappear and that "something in the settings resets the same way".
They sent a 54 s screen recording (with touch marks) to the owner; it stays out of the repo. The settings in the
recording: aim "above the finger" at first (the default), then "bubble center"; engines and recognition boost not
visible except "OCR boost: Off"; the system accessibility shortcut ("Shortcut: accessibility button") is on, so the
system's floating accessibility button with Screenlate's icon is on screen all the time.

### What the recording shows

Times are seconds into the recording.

| Time | Screen | What happens |
| --- | --- | --- |
| 0.0 | ColorOS accessibility page of Screenlate | Service switch off (the service was already off when the recording started), shortcut switch on. |
| 1.2 | same | The user turns the service on; the bubble appears docked at the right edge. |
| ~2.0–4.1 | same | The bubble is pulled out (scan starts) and moved over the page's text, then released in the middle of the screen. No popup (the text is not in the active language). |
| 4.4 | same | Bubble gone; the service switch flips off by itself (no touch). |
| 5.9–6.2 | same | The user taps the service row; switch on, bubble docked again. |
| ~6.8–8.8 | same | Pulled out, moved over text, released. |
| 9.0 | same | Bubble gone, switch off by itself. |
| 10–15 | Screenlate → Bubble | The user switches "Aim point" from "Above the finger" to "Bubble center" (double tap on the bubble also switches it). |
| 18.2 | accessibility page | The user turns the service on. |
| 19.2–25.9 | same | Bubble pulled out and held over text for ~7 s, then dragged back to the dock. Nothing disappears. |
| 27.2 | same | Pulled out again; at 27.8 a popup with an on-device draft and the cloud spinner. |
| 29.0 | same | The popup closes (cloud result in, nothing found at the aim). |
| 29.8 | same | Bubble gone, switch off by itself. |
| 31.6 | same | The user taps the system's floating accessibility button: the switch turns on and the bubble comes back. |
| 37–38 | AnkiDroid reviewer | The user opens a card; the bubble floats over it. |
| ~44.6 | AnkiDroid | A tap on the floating bubble starts a scan (lines flash). Draft popup at 45.2, cloud result at 45.8. |
| 47.03 | AnkiDroid | Popup, bubble and highlights vanish in one frame. The floating accessibility button flickers at 47.05 and 47.25; a touch at 47.35 away from it. |
| 48.4 | AnkiDroid | The bubble is back, docked at the right edge, with no tap on a switch or on the shortcut. |
| 49.2 | AnkiDroid | Pulled out; draft popup at 49.8, cloud result at 50.6. |
| 51.6 | AnkiDroid | Popup and bubble vanish in one frame. Recording ends at 53. |

### Reading

- Five ends, all 2.2–2.8 s after a scan started; where the cloud result is visible, 0.8–1.2 s after it. One scan
  (19.2) survived ~7 s of holding and ended in the dock. Not every scan dies.
- Everything vanishes in one frame: the process ended (or the service was unbound), not a window bug.
- The switch going off without a touch, and the shortcut tap at 31.6 turning the service on (the shortcut turns an
  enabled service off), say the service was off the enabled list at least then. That needs a force-stop (or an OEM
  rule acting like one). A crash alone leaves it enabled.
- At 48.4 the bubble came back by itself 1.4 s after the process ended, which is Android restarting a crashed or killed
  service. A force-stop would not come back by itself.
- So the recording fits a process that dies during the scan, after which the service is sometimes restarted and
  sometimes taken off (force-stop, or not restarted after repeated crashes). Without a log the two cannot be told
  apart.
- The "settings reset" is not shown in the recording: the aim setting changed at 12 s stayed "bubble center" for the
  rest of it. It may mean the service switch itself, or the aim mode switched by a double tap on the floating bubble
  (by design), or all settings back at defaults (an unreadable settings file is replaced with defaults by
  `SettingsModule`'s corruption handler; DataStore writes through a temp file and rename, so a stop during a write
  should not do that). To ask.

### The first log (About → Logs, saved 22:45 the same day)

Header: OPPO CPH2825, Android 16 (API 36), so ColorOS 16. What it shows and what it rules out:

- It holds one process only, started at 22:45:17 when the user opened the app, and ends 22 s later at the export.
  Nothing of the process that lost the bubble. ColorOS logs a lot (this process alone wrote ~200 framework lines in
  22 s, mostly `Oplus*`), so the shared main and system buffers are overwritten fast; lines of an earlier process of the
  same app are readable only while still in the buffer.
- No `beginning of crash` section. The export reads the default buffers, crash included; a Java crash ("FATAL
  EXCEPTION" from `AndroidRuntime`) is always written to the crash buffer and a native crash's "Fatal signal" line
  normally too, and that buffer holds only crashes, so it keeps them for long. None of Screenlate's: a crash since the
  last reboot is unlikely.
- `WM-ForceStopRunnable: Found unfinished work, scheduling it.` WorkManager runs this check at every process start (the
  app initializes it in `Application.onCreate` through `installBundled()`). On API 30+ it reads
  `getHistoricalProcessExitReasons` and logs "Application was force-stopped, rescheduling." when it finds a
  `REASON_USER_REQUESTED` exit (that is what a force-stop records) newer than the last one it handled. It did not, so
  the process before this one did not end with a force-stop, provided nothing started Screenlate between the
  disappearance and the export (every service start runs the same check and would have taken that exit).
- No "Service connected" in 22 s: the service was not on the enabled list at that time. An enabled service whose
  process died is rebound as soon as the process starts again (ActivityManager starts pending service restarts on
  attach), so it would have connected.
- The rest is ColorOS framework noise (`SchedAssist`, `OplusPredictiveBackController`, an `avc denied` on the zygote
  socket) and the bundled dictionary check; nothing points at Screenlate.

So the log weakens both leading causes: no crash on record, and no force-stop if the export followed the
disappearance directly. That leaves a third reading: ColorOS takes the service off the enabled list (or unbinds it)
without a force-stop, and the process is killed later as an ordinary background process. To tell, the next log has to
cover the moment itself: a larger log buffer before reproducing, the export at once, or exit reasons recorded by the
app.

### Logs of 2026-10-22: the service is turned off while the process lives

The user reproduced it and saved the log at once (07:59:40), then once more after trying other settings (08:08:25).
Phone language Japanese by then (`Locale: ja-RU`). The main buffer kept under a minute of history on this phone.

07:59 log, one process (pid 3594) throughout:

| Time | Event |
| --- | --- |
| 07:58:53 | The user opens Screenlate (process start). |
| 07:59:13.521 | `Service connected` (the user turned the service on). |
| 07:59:14.306 | Pull-out scan: window captured in 55 ms, ML Kit draft 408 ms, cloud final 891 ms; popup window (918x772) and the full-screen layer shown, lookups found entries. |
| 07:59:15.792 | Finger up; the bubble stays floating (`OplusViewDragTouchViewHelper: dispatchTouchView action = 1`, a ColorOS helper in the app process, at every release). |
| 07:59:16.978 | `InputEventReceiver: … Publisher closed input channel` for the bubble, popup and layer windows, then `ScreenlateService: Service stopped` (`onDestroy`). No app line in between, no crash, the same pid goes on. |
| 07:59:17.844 | `Service connected` again in the same process (0.87 s later; probably the user, who had the page or the shortcut at hand). |
| 07:59:18.539 | Second pull-out scan, final at 19.417. |
| 07:59:19.723 | Finger up, bubble floating. |
| 07:59:20.881 | Windows closed and `Service stopped` again, 1.16 s after the finger up. |
| 07:59:23 | The app's own screen comes back (same pid); log saved at 07:59:40. |

08:08 log: the system buffer still had pid 3594 at 08:07 (three overlay windows created at 08:07:25, :34 and :41, so the
service was on again and scanning; the app's own screen hidden at 08:07:50). The main buffer had lost it. A new process
started at 08:08:19, and its WorkManager check logged `Application was force-stopped, rescheduling.`: pid 3594 ended
with a force-stop between 08:07:50 and 08:08:19, with Screenlate in the background (a swipe in recents, or ColorOS).

Reading:

- The bubble disappears because the system unbinds the accessibility service: the window manager removes the
  service's windows, then `onDestroy` runs, and the process lives on. Not a crash and not a kill. This is what the
  recording showed as the switch turning off by itself. The first log (service off, no force-stop exit) fits it too.
- Both stops came 1.16–1.19 s after the finger left the bubble at the end of a pull-out scan, with the bubble left
  floating. The app does nothing at that moment (no line between the finger up and the stop; nothing in v0.2.3 is
  timed ~1.2 s after a release: the layer hides 1 s after it empties, but here the aim kept it full, and auto-play
  logs only failures). In the recording, the one scan that ended in the dock (19.2–25.9) did not lose the service;
  the AnkiDroid stop at 47.03 came ~1.2 s after a tap scan's cloud result.
- Only the user, a force-stop, the app's own `disableSelf()` or a privileged component can take a service off; v0.2.3
  never calls `disableSelf()`. So a ColorOS component turns it off. Which one, and on what signal, is not in the app's
  log: it needs system lines from that second.
- Known candidates, none confirmed: a ColorOS 16 defect that turns off every downloaded app's service (the Reddit
  thread in the ColorOS section; there on screen lock, here on another trigger), a ColorOS security feature that turns off accessibility for a "non-official" app
  behaving suspiciously (an OPPO community post describes a prompt with "Turn off accessibility"; the post now returns
  404), Play Protect's live threat detection (shows a notification), the accessibility shortcut (the user has it on;
  a tap on it turns the service off). Android 17's Advanced Protection rule against non-tool services does not apply
  (Android 16, and it blocks turning the service on at all).
- Force-stops happen as well (08:08), at another moment; they are the separate background-limit problem described in
  the ColorOS section.

### Likely causes, ranked

Ranked from the recording alone; the first log (above) argues against 1 and, with a condition, against 2, and adds 2a.
The 2026-10-22 logs settle it for the scan-time stops: 2a, the service turned off with the process alive. 1 is ruled
out for them; 2 remains for the stops at other times.

1. **The process crashes during or right after recognition on this phone** (Java or native: ML Kit, WebView, the
   engine's JNI, or the scan code). For: the deaths follow scans at a steady interval; the one automatic return at
   48.4 is Android's restart after a process death; a crash right after another one is not restarted, which would
   leave the switch off on a page that shows the running state. Against: the shortcut tap at 31.6 turned the service
   on, which in AOSP means it was off the stored list. Confirm: About → Logs right after a disappearance ("FATAL
   EXCEPTION" or "Fatal signal"), or `dumpsys activity exit-info` with `CRASH` / `CRASH_NATIVE`.
2. **ColorOS force-stops Screenlate** (a power, freezing or "abnormal behavior" manager reacting to the scan's burst
   of CPU, memory and network while Screenlate has no visible activity). For: the switch turns off by itself, and
   only a force-stop does that; the shortcut tap at 31.6 turned it on. Against: the automatic return at 48.4; a killer
   acting within seconds on an app with an enabled accessibility service would be unusual. Confirm: exit-info with
   `USER_REQUESTED` (force-stop) or `OTHER` and a ColorOS description; system lines "Force stopping com.vpr.screenlate
   … : <caller>"; the app's own log ends without a crash line; the keep-alive notification test changes the result.
   2a. **ColorOS turns the service off without a force-stop** (a security or power feature writing the enabled list,
   or unbinding the service). Fits the first log: no crash, no force-stop exit, service off. Confirm: with a large
   log buffer, the app's own lines at the moment show "Service stopped" (`onDestroy`, process still alive) and no
   crash; exit-info shows a later `OTHER`/`LOW_MEMORY`/`FREEZER` kill instead of `USER_REQUESTED`; a ColorOS
   notification or toast at that moment.
3. **Low-memory kill.** Would leave the service enabled and restart it (only the 48.4 case fits). Confirm:
   exit-info `LOW_MEMORY`. Unlikely during a single scan on a recent phone, but cheap to rule out.
4. **The accessibility shortcut.** Not the cause in the recording (no touch at the deaths), but a tap on the floating
   accessibility button turns Screenlate off, and the user uses it. To ask.
5. **Clearing recent apps or ColorOS's auto-optimization** (both force-stop). Not in the recording; the same symptom
   at other times. To ask.

### What to ask and try

Questions (the Russian text for the owner to forward is in the session's report and the shared session notes):

1. Which setting resets: the system switch of the service, or a Screenlate setting (which one, alone or together with
   others)?
2. ColorOS version; does the bubble also disappear when no scan runs (idle, screen off, after closing recents); does it
   come back by itself; has the user ever tapped the floating accessibility button by accident.
3. Settings to check: per-app battery usage (allow background activity and auto launch where shown), no battery
   optimization (Screenlate → Background work → "Allow background work"), the security app's
   auto-optimization off or Screenlate excluded, Screenlate's card locked in recents.
4. Tests, one change at a time, a few scans each: "Keep a notification while the service runs" on (Background work);
   engines "Cloud" only, then "Device" only (Bubble → Screen recognition → Engines); "Reduce load on the
   device" with both engines.
5. Logs: About → Logs → "Save to Downloads" right after a disappearance (only Screenlate's lines, no recognized text).
   With a computer, the exit reasons and filtered system lines:

   ```bash
   adb shell dumpsys activity exit-info com.vpr.screenlate > exit-info.txt
   adb logcat -d -v threadtime -b all | findstr /i "com.vpr.screenlate" > system-lines.txt
   adb logcat -d -v threadtime -b all -s ScreenlateService OverlayController CompositeOcr ScreenCapturer LensOcr LookupPage ScreenlateKeepAlive Screenlate AndroidRuntime libc DEBUG > app-lines.txt
   ```

   (`grep -i` instead of `findstr /i` on macOS and Linux.) The first two hold only lines naming Screenlate; the third
   Screenlate's tags plus crash reports. A larger log buffer in Developer options (Logger buffer size) keeps more
   history.
6. After the first log: was it saved right after a disappearance, with the service not turned on again in between?
   Does ColorOS show anything at that moment (a notification, a toast, an entry in the phone manager or security app)?
   For the next try: Developer options → Logger buffer size at the largest value, reproduce, then save the log at once
   without turning the service on first.
7. After the 2026-10-22 logs, to find the trigger (a few scans each, one change at a time): after the scan, drop the
   bubble at the edge instead of leaving it in the middle; engines "Device" only, then "Cloud" only; the accessibility
   shortcut off for Screenlate; does a notification or prompt appear (Play Protect, the phone manager, security). With
   a computer, a live system log while reproducing catches the component that turns it off:

   ```bash
   adb logcat -c
   adb logcat -v threadtime -b all | findstr /i "accessib screenlate" > live.txt
   ```

   (stop with Ctrl+C a few seconds after the bubble disappears; `grep -i -E "accessib|screenlate"` elsewhere), and
   `adb shell settings get secure enabled_accessibility_services` right after, which shows whether the service is
   still on the list.
9. After the web research (ColorOS section): how Screenlate was installed (browser, file manager, another store), the
   ColorOS build and region; in Phone Manager or Security, the virus scan and the interception records, then add
   Screenlate to the allowlist (Security > Viruses & risks > Allowlist, or Phone Manager) and turn the service on again;
   whether a prompt "Turn off accessibility / Keep on" or a security notification ever appeared (notification history).
8. After the Reddit thread (ColorOS section): turn on the accessibility service of another downloaded app (any app with
   one) and reproduce. If both go off together, ColorOS turns off downloaded apps' services as such, and the app can do
   little beyond telling the user. Also: the exact ColorOS build (Settings → About device → Version), and whether the
   problem began after a system update, which may also have reset the battery and startup settings.

### Possible fixes in the app

Built on 2026-10-22 (`claude/competent-rhodes-6bde05`, regular builds, owner's choice): the app's own log file with
exit reasons at each start (request 71 of the languages plan), the stop report and the notification below. The stop
report reads, e.g.: `Service stopped; on the enabled list: no; bubble touch ended: 1180 ms ago; scan started: 3400 ms
ago; window events in the last 10 s:` followed by one line per window that came to the front, opened or closed, system
packages by name and other apps as "an app"; 3 s later a line checks the list again. The notification shows at every
stop (the app cannot tell a user's switch from the system's), as a banner without sound, and is cancelled when the
service connects again; without the notification permission it is only a log line.

Package visibility (Android 11): the app's `<queries>` makes every app with a launcher icon visible, plus the makers'
security apps listed there; the system's own packages without one (the home screen, system panels, and on ColorOS most
`com.oplus.*` components) are hidden unless force-queryable. The emulator's first report named its launcher "an app".
So a visible package is named when it has `FLAG_SYSTEM`, and a hidden one when its name starts with a system or phone
maker prefix (`hasSystemPackagePrefix`: `com.android.`, `com.google.android.`, `com.coloros.`, `com.oplus.`,
`com.heytap.` and others); other hidden packages stay "an app".

Emulator check, 2026-10-22: `settings put secure enabled_accessibility_services` without Screenlate gives "on the
enabled list: no", the banner, and "3 s after the stop: … running: no"; turning it back on cancels the notification.
`uiautomator dump` also stops the service: UiAutomation pauses accessibility services, the report says "on the enabled
list: yes", and the service connects again 1.5 s later. That is the same shape a ColorOS unbind would have, and a
reason not to run `uiautomator` while checking stops.

| Fix | What it does | Cost |
| --- | --- | --- |
| Exit reasons in the log | At each process start, log the earlier processes' `ApplicationExitInfo` (reason, description, status or signal, importance, PSS/RSS, time; no text) so the About → Logs export answers crash vs stop on any phone. The first log shows the export alone cannot: on ColorOS the buffer loses the dying process before the user gets to the button, while exit records stay for days. Keeping the service's connect and stop events in a small file of their own (a stop with the process alive means the service was turned off, not killed) would separate 2 from 2a | Small: about half a day with tests; reaches users with the next release. The diagnostics branch has a version to borrow from |
| Stop diagnostics | When the service is destroyed while the process lives, log whether it is still on the enabled list (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` is readable) and the package names of system windows that appeared in the last seconds (`getWindows()` / window-state events; system packages only, no text, no user apps), so a ColorOS prompt or component shows in the app's own log | Small: about half a day with tests and an emulator check |
| "Turned off" notification | On such a stop, a notification that the system turned Screenlate off, with a button to the accessibility settings (the app cannot turn itself back on) | Small to medium: half a day, strings in 14 languages; needs the notification permission |
| "Stopped by the system" card | Remember that the service ran; on the next app start, if it is off without a clean stop (onDestroy with the process alive) and the exit reason was a stop or crash, show a Home card with the steps (Background work, the accessibility page) | Medium: 1–1.5 days, strings in 14 languages, emulator check |
| Fix the crash | Once a trace shows it | Unknown until the log |
| Keep-alive on by default on phones with a maker's startup screen | The notification that makes background killers leave the app alone more often | Small (hours), only if the notification test helps |
| Lighter scans by default there | "Reduce load on the device", or skipping the on-device whole-screen pass once the cloud answered | Small to medium, only if the engine tests point there |
| ColorOS allowlist step | Background work on ColorOS points to the security app's allowlist (and a hint that sideloaded apps are restricted), if the user's test shows the allowlist helps | Small to medium; strings in 14 languages; only after the user's result |
| Request the accessibility button | The system shortcut would do something in Screenlate (open or scan) instead of turning the service off | Small to medium; a behavior choice for the owner; no help against a force-stop |
