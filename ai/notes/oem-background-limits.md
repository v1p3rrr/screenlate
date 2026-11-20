# Phone makers' background limits and the service turning off

What the system does to an accessibility service whose process dies, what phone makers add, and the first report from
a ColorOS phone. Human docs stay generic (`docs/usage.md`, "Background work"); vendor names belong here.

## Honor force-stop investigation (2026-11-20)

Implementation follow-up: [background diagnostics and optimization proposals](../plans/2026-11-20-background-diagnostics.md).
Expanded monitoring is Dev-only; all application optimizations remain deferred by the owner.

### Scope and evidence

Read-only analysis requested by the owner: Honor 90 REA-NX9, Android 15 / MagicOS 9; preserve loaded OCR models,
the popup WebView and fast opening, never call `System.gc()`. New idle actions must require a docked bubble,
closed popup and no active scan, with at least 10-15 seconds of grace, or an explicit screen-off transition.
The accidentally attached AnkiConnect APK was explicitly excluded by the owner and was not inspected.

The supplied `screenlate-log-20261120-211607.txt` contains 2,208,935 bytes and 19,333 lines, not an 8 MB dump.
The app's configured ring capacity and the exported file's actual size are different facts. Its own-log section starts
on October 27; historical exit descriptions include older dates. AndroidRuntime/crash and current system/main sections
follow the app log. Scan all sections before concluding that a log contains no crash.
The original log and screenshots are not copied into git. Only event metadata is recorded here.

Inspection used the logged builds (`41155f1d`, `d2341119`, `c7e9b08e`, `8849e5f7`) through `git show`, rather than
treating the current, independently edited sentence-audio working tree as the installed diagnostic build.
Platform statements and their primary-source links are in [primary-source appendix](#android-process-exit-primary-sources).

### Confirmed exits

There are **15** retained main-process exits with an explicit `iAwareR[PowerGenie inactive clean]` force-stop
description, first at November 18 02:01:07.930. The next-process log timestamp is when the app read the historical
record, not when the older process died. The system caller reported is PID 2520; this establishes the reported
system cleanup path, not that a shell `am force-stop` command was executed or which proprietary policy selected it.

| Exit time (local log time) | PID | Importance | Last sampled RSS (MB) | OEM suffix |
|---|---:|---:|---:|---|
| November 18 02:01:07.930 | 29622 | 200 | 332 | 4105102ms |
| November 18 15:31:48.276 | 16803 | 200 | 270 | -1ms |
| November 18 17:14:26.924 | 29669 | 200 | 336 | 4357229ms |
| November 19 01:31:56.793 | 886 | 200 | 201 | 3697824ms |
| November 19 08:22:10.433 | 10547 | 200 | 320 | -1ms |
| November 19 10:20:42.662 | 29336 | 200 | 318 | 4009723ms |
| November 19 14:14:06.617 | 19695 | 125 | 220 | -1ms |
| November 19 14:31:26.939 | 14148 | 125 | 331 | -1ms |
| November 19 16:03:19.959 | 6657 | 125 | 369 | -1ms |
| November 20 02:01:01.135 | 31702 | 125 | 216 | 4231397ms |
| November 20 10:01:22.339 | 2634 | 125 | 184 | -1ms |
| November 20 12:28:10.043 | 19728 | 125 | 245 | -1ms |
| November 20 14:01:42.671 | 15275 | 125 | 204 | -1ms |
| November 20 18:35:57.440 | 21988 | 125 | 199 | -1ms |
| November 20 20:03:30.888 | 16227 | 125 | 197 | -1ms |

Corrections to the initial task summary:

- The first six exits are importance **200 (visible)**, not 125. The later nine prove that foreground-service
  importance does not prevent this cleanup. The earlier values alone do not establish why the keep-alive was absent
  or differently classified; runtime settings/start results were not recorded.
- A universal 60-70-minute idle/sleep rule is not supported. PID 14148 started at 14:22:21 on November 19 and died
  at 14:31:26, about 9 minutes later and about 6 minutes after docking. For the first incident, subtracting
  4105102ms gives 00:52:42, yet the log contains multiple scans after that, including one at 01:23:00 and docking
  at 01:23:07. The suffix cannot safely be labelled time since the last Screenlate scan, docking, or screen-off.
  Screen-off timestamps and the OEM field's specification are unavailable; `-1ms` has no established meaning here.
- The November 20 20:03 incident consists of main PID 16227 force-stopped at `.888`, followed by isolated PID
  17054 at `.898` with `OTHER / ISOLATED NOT NEEDED`. The About screenshot shows the latter because
  `ExitReasons.lastExit()` requests only one unfiltered record. WebView is the likely isolated service; the log
  omits process names/UIDs, so its identity is not directly established. This is a diagnostic-selection defect,
  not evidence that the main force-stop did not happen.
- Before November 18 the retained log also has `USER_REQUESTED` from Settings on November 15 and `REMOVE TASK`
  on November 16. The formatter's unconditional `USER_REQUESTED (force-stop)` label is broader than the actual
  enum semantics. These earlier descriptions do not mention PowerGenie.
- After November 18 there is also a cached-process `SIGNALED / status 9` on November 19 00:18:59, and a real main
  `CRASH` on November 20 11:22:36. Not every subsequent exit is PowerGenie.

### What the code change does and does not show

`41155f1d` includes the November 17 outside-tap/crop changes; the commit itself is a string audit. Comparing the
earlier external-links build `773efde3` with it shows no change to `BubbleKeepAliveService` or the overlay manifest.
The accessibility service change supplies `CropAreaRepository`; it does not alter binding or foreground-service
policy. HUD sampling, aggregate lookup limits and sentence recording were introduced after the first incident and
cannot explain its onset.

In `PopupController` at `8849e5f7`, the catcher is attached once below the popup. When inactive, it is `GONE`,
1x1, `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE`. It becomes full-screen/touchable only for a visible,
drawn popup with outside-tap dismissal enabled. The screen-off receiver is registered only while attached,
visible and enabled, and unregistered on hide/detach. It closes the popup through its ordinary callback; it does
not call `disableSelf()`, stop the keep-alive service, hold a wake lock or install a recurring background timer.
An attached hidden catcher is still an additional window record: whether MagicOS classifies that differently is
an untested OEM hypothesis. No public Android rule or observed trace establishes that link.

Working hypotheses, with discriminating checks rather than a claimed root cause:

1. **OEM background classification/policy or exemption state changed.** Record the package's actual stopped state,
   standby bucket, running foreground services and system power-policy messages at a natural disappearance.
   App activity usage, overlay interaction and system-defined inactivity need not be equivalent. A rule that treats
   them differently is possible, not established by this app-only log.
2. **The added catcher changes OEM window classification.** First compare the same build with outside-tap disabled
   and enabled; this tests the active catcher/receiver behavior only. Disabling the setting still attaches a hidden
   catcher, so testing window presence requires a separate build that never attaches it. Keep all other code/settings
   and workloads equal. A whole old-APK rollback changes too many variables for a decisive result.
3. **Resource bursts affect a proprietary cleanup policy.** Compare matched warm scans and true idle CPU/memory
   deltas with the HUD disabled, without unloading models. Lower resource use may help but is not an established
   exemption. Last-sampled RSS across these exits ranges from 184 to 369 MB; no memory threshold can be inferred.

A conclusive end-to-end reproduction requires the affected firmware and a natural cleanup with system evidence.
An AOSP emulator/unit test cannot reproduce PowerGenie's private selector, and intentionally force-stopping the
package would test a different trigger while disabling accessibility. No device command or system-setting change
was performed for this analysis. Do not claim a tested fix from the source review.

### Resource measurements and UX-preserving candidates

- `Debug.getMemoryInfo()` measures the main process, not the sum of that process and a separate renderer.
  Browser-side WebView allocations and shared mappings can contribute; the residual PSS cannot be assigned to a
  renderer process without a per-process memory breakdown. `nativeAllocatedMb` uses allocator accounting, not
  native PSS, so it cannot be subtracted from PSS to obtain an exact remainder. A small Java heap does not prove
  the absence of retained Java/native/JS objects.
- `Process.getElapsedCpuTime()` counts threads of the main process across cores; a separate renderer's CPU is
  excluded. Use `deltaCpuMs / deltaElapsedRealtimeMs` to express core equivalents, then divide by the actual core
  count only for a whole-device percentage. Four-thread MNN inference (`THREADS = 4`) plus other main-process
  work can raise cumulative CPU rapidly. The current data does not establish 4-8 simultaneous MNN workers or
  attribute a measured CPU jump to rendering.
- `ApplicationExitInfo` PSS/RSS are last periodic samples, not memory immediately before death; zero PSS is
  unavailable sampling here. The explicit force-stop description distinguishes these records from an ordinary
  LMKD kill, but the RSS value alone does not exclude system memory pressure as background context.
- `UI_HIDDEN` means the activity UI became hidden, not a low-memory warning or proof that the OCR pipeline
  stopped. This matters when the popup is an overlay over another app.
- `PaddleOcrEngine` already trims intermediate MNN session buffers after 10 seconds without recognition, preserving
  loaded model objects. The log confirms this repeatedly. Its timer is not conditioned on docking or popup closure:
  any revised idle policy must honor the owner's stronger conditions. Previous measurements document a buffer
  reallocation cost on the next full scan; do not promise zero cost or schedule repeated trimming as a cure.
- Docking already calls `closeScan()`/`resetScan()`, cancels scan/lookup/band jobs and releases scan-owned screenshot
  references. Work already inside non-cancellable/native operations can finish; verify actual in-flight completion
  rather than using the displayed `IDLE` label as the only gate. Notes and other legitimate background work keep
  their own leases. Never recycle a bitmap another consumer still owns.
- The HUD runs a main-thread `snapshot()` plus text updates every second while attached, including after screen-off
  if its window remains attached. About independently samples once per second while subscribed (and for its
  five-second sharing grace). Coalesce these samples, run expensive memory collection off the main thread and
  suspend diagnostic redraw/sampling at screen-off or after the accepted safe-idle grace. Resume on interaction
  before reporting a fresh sample. These changes remove measurement overhead; they cannot explain November 18
  because diagnostics arrived on November 20. No measured overhead estimate is available yet.
- A paired, per-instance `WebView.onPause()`/`onResume()` could reduce some hidden-page work without destroying
  the view, after the accepted safe-idle gate and with resume before `prepare`/render/evaluation. It does not stop
  JavaScript and promises no memory reduction or latency bound. `pauseTimers()` affects every WebView in the app,
  including visible Search, so it is unsuitable as an uncoordinated popup-only action. Popup scripts have no
  obvious recurring `setInterval` loop that this review can blame for sustained idle CPU.
- Existing renderer priority is `IMPORTANT, waivedWhenNotVisible=true`; hidden renderers were reclaimed well
  before this regression (111 `crashed:false, shown:false` messages, first October 27 23:22). `prepare()` recreates
  a lost renderer during scanning. Keeping the WebView object is different from preventing system renderer loss;
  changing priority to retain the renderer trades higher residency for fewer cold opens and is not a force-stop
  protection. Such a behavior tradeoff needs the owner's decision and device timing evidence.
- Dictionary mmap pages are not all permanently resident. Do not close/reload the engine or arbitrarily cap engine
  candidates as a speculative memory fix: `Int.MAX_VALUE` preserves sorting/dictionary-order semantics. It first
  appeared after the first recorded PowerGenie incident. Measure candidates, definition payload and output size
  before selecting an optimization that preserves those semantics.

### Other confirmed anomalies

The November 20 11:22:36 crash has R8 map ID `51ac9318...`. Both locally retained `377c2125` and `c7e9b08e`
mappings match the full ID. Matching class/method/line entries resolve the path to:
`BubbleMenu` recording-stop action -> `OverlayController.stopSentenceRecording` -> `SentenceRecorder.stop` ->
`PcmStorageCoordinator.stop` -> `ScanAudioTimeline.stop` -> `ContextRetention.advance`, which throws
`IllegalArgumentException: Elapsed time must not move backwards`. This is the sentence-recording stop path,
not a PowerGenie event or proof that Android's monotonic clock actually moved backwards. The stop timestamp is
captured before draining/joining the writer, which also advances retention: an earlier event timestamp applied
after a later one is a concrete race candidate. The race is not reproduced or fixed in this analysis.

Four other retained Java crashes (November 2, twice November 5, November 17) involve Chromium initialization with
`Resources.NotFoundException` from `DeviceFormFactor.isTablet`. They precede the first PowerGenie incident and
must be tracked separately from OEM cleanup. No application main-process `LOW_MEMORY` exit is in this retained dump;
this does not establish that the device never experienced memory pressure.

Lookup is usually short but not uniformly 18-90 ms: on November 20 the 81 `Lookup found ...` records range from
1 to 1362 ms, median 48 ms, nearest-rank-by-index p95 631 ms. Examples are 1223 ms / 14 entries at 16:36:59 and
1362 ms / 35 entries at 13:24:42. These are logged lookup-stage wall times, not isolated engine CPU or rendered-popup
latency. Heavy payloads and cold work merit measurement but do not establish the cause of a later force-stop.

### Next evidence, not an approved implementation plan

Before changing behavior, improve exit attribution (main process versus isolated service, retain full history and
process/UID metadata), capture screen-off/on and safe-idle transitions without text, and sample CPU deltas/per-process
memory at those boundaries. Collect system cleanup evidence at a naturally occurring stop; do not force-stop,
disable PowerGenie, reset battery controls, update the owner's app or access the Anki collection as an analysis step.
Any additional diagnostics or A/B build must be requested/approved as the next implementation scope. Product
decisions, including renderer residency, were not changed and no new implementation plan was written.

### Android process exit primary sources

Research date: 2026-11-20. Scope: Android 15 / API 35 source behavior and current public API documentation. No device settings or application code were changed. These sources explain platform behavior; they do not establish the proprietary policy that selected Screenlate for cleanup.

#### Force-stop and accessibility

In AOSP's `android15-release` branch, `AccessibilityManagerService.onPackagesForceStoppedLocked()` removes matching components from `mEnabledServices`, removes their binding/crashed state, and persists `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`. The package monitor invokes this handling for an actual force-stop. This supports the observed disabled accessibility toggle after a package force-stop. It does not mean an `am` shell command was executed: privileged system code can invoke the underlying mechanism. [AOSP Android 15 AccessibilityManagerService, lines 748-799 and 5881-5921](https://github.com/aosp-mirror/platform_frameworks_base/blob/android15-release/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java#L748).

Ordinary service binder death follows a different path: `AccessibilityServiceConnection.binderDied()` marks the service crashed, disconnects and resets it, and updates clients. It does not itself remove the enabled-service setting. `onServiceDisconnected()` delegates to that method. This distinction supports recovery after ordinary process death, but is not a guarantee of immediate or indefinite restart after every possible failure. [AOSP Android 15 AccessibilityServiceConnection, lines 284-291 and 421-442](https://github.com/aosp-mirror/platform_frameworks_base/blob/android15-release/services/accessibility/java/com/android/server/accessibility/AccessibilityServiceConnection.java#L421).

#### Exit records describe processes, not necessarily the main application

`getHistoricalProcessExitReasons()` returns recent matching records in newest-first order from a ring buffer. With PID zero it includes all matching processes; external services bound with `BIND_EXTERNAL_SERVICE` are also included in the package's history. A UI choosing only the newest record can therefore show a renderer/service exit in place of the main application's force-stop. Preserve process name, PID, real UID and package UID, and distinguish the main process from external/isolated processes before attributing service disappearance. [ActivityManager API](https://developer.android.com/reference/android/app/ActivityManager#getHistoricalProcessExitReasons(java.lang.String,%20int,%20int)).

In AOSP Android 15, `OomAdjuster.updateAndTrimProcessLSP()` kills an isolated process when it has no running services and no custom isolated entry point. Its reason is `REASON_OTHER` with `SUBREASON_ISOLATED_NOT_NEEDED`. This is normal isolated-process lifecycle cleanup; the description alone does not identify which external service it was, nor establish a main-process crash. [AOSP Android 15 OomAdjuster, lines 1317-1329](https://github.com/aosp-mirror/platform_frameworks_base/blob/android15-release/services/core/java/com/android/server/am/OomAdjuster.java#L1317).

`ApplicationExitInfo.getRss()` and `getPss()` contain the last sampled memory values, explicitly not exact measurements immediately before death. Zero can mean no sample was taken. `REASON_USER_REQUESTED` covers more than a Settings force-stop, including task removal; the OEM description adds the case-specific evidence here. Isolated processes have a distinct real UID while retaining their associated package UID. Thus RSS 197-199 MB cannot by itself exclude memory pressure or prove an exact idle footprint. [ApplicationExitInfo API](https://developer.android.com/reference/android/app/ApplicationExitInfo).

#### Memory and CPU measurement boundaries

`Debug.getMemoryInfo()` retrieves the calling process's memory usage. It does not automatically aggregate a separate WebView renderer. Main-process PSS can include WebView browser-side allocations, graphics and mappings, but the remainder after subtracting Java/native heaps cannot simply be labelled renderer-process memory. [Debug API](https://developer.android.com/reference/android/os/Debug#getMemoryInfo(android.os.Debug.MemoryInfo)).

`Process.getElapsedCpuTime()` measures accumulated CPU time for the calling process. Android 15 implements it with `clock_gettime(CLOCK_PROCESS_CPUTIME_ID, ...)`. It includes the process's threads across cores, not an independently running renderer's CPU. Compare deltas over a measured wall interval; cumulative totals alone are not an idle CPU load. A 10-second CPU delta over two wall seconds means about five CPU cores on average for that process during that interval. [Process API](https://developer.android.com/reference/android/os/Process#getElapsedCpuTime()), [AOSP Android 15 implementation, lines 1097-1108](https://github.com/aosp-mirror/platform_frameworks_base/blob/android15-release/core/jni/android_util_Process.cpp#L1097).

#### WebView lifecycle and renderer priority

`WebView.onPause()` makes a best-effort pause of safe work such as animations/geolocation but does not pause JavaScript. `pauseTimers()` affects layout/parsing/JavaScript timers globally across all WebViews, so a popup-local idle decision must not freeze a visible search/settings WebView. Resume must balance whichever pause API was used. Neither API promises a particular memory reduction. Renderer priority controls OOM targeting of an out-of-process renderer, not immunity of the main application to OEM force-stop. Default priority is `RENDERER_PRIORITY_IMPORTANT`; `waivedWhenNotVisible=true` requests waived priority when hidden and makes renderer loss more likely. [WebView API](https://developer.android.com/reference/android/webkit/WebView).

`RenderProcessGoneDetail.didCrash()==false` means the renderer was killed by the system; documentation says memory pressure is the most likely cause, not a uniquely proven cause. The callback is distinct from termination of the application process. [RenderProcessGoneDetail API](https://developer.android.com/reference/android/webkit/RenderProcessGoneDetail#didCrash()).

#### What HONOR documents, and what remains unknown

HONOR's support material recommends manual App launch management with background execution allowed, excluding the app from battery optimization, and disabling power-saving modes when investigating background closure. [HONOR: Background apps are forced to close](https://www.honor.com/global/support/content/en-us00428704/).

HONOR also documents locking the app preview in Recents to protect against the user's clear-all operation; this is not documented as a universal exemption from all cleanup. [HONOR: An app running in the background restarts when re-accessed](https://www.honor.com/uk/support/content/en-us00406916/).

The reviewed official material does not specify the `PowerGenie inactive clean` decision algorithm, a guaranteed 60-70-minute timeout, the meaning of its appended millisecond field or `-1ms`, an accessibility-overlay exemption, or a supported developer API to prevent this OEM force-stop. Treat the description as evidence of the reported cleanup path, not proof of its underlying trigger. A dated code change matching the first retained record is a regression candidate, not causal proof; ring-buffer retention also limits claims about the absence of earlier incidents.

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
- Outcome of the 2026-10-20 report (relayed by the owner, 2026-10-28): the user added Screenlate to the security app's
  allowlist (step 9 below), and the service is no longer turned off. So the ownvoice description holds for that phone:
  ColorOS 16's security app turns off a sideloaded app's accessibility service unless the app is on its allowlist.

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

### The overlay crash when the service comes back (2026-10-22)

Group 5a's emulator run found a crash: `BadTokenException` in `OverlayController.attachWindows`, called from the
settings flow, when the service was unbound before the controller had added its windows (languages plan, request 78;
`notes/overlay-system-ui.md`, "The service's window token"). v0.2.3 has the same code. Whether it plays a part here:

- Not in the user's logs. The 2026-10-22 logs show both scan-time stops as a clean `onDestroy` ("Service stopped")
  with the same pid going on and no crash line; the first log had no crash section at all. The crash needs a window
  to be added after the system has taken the token: the overlay's windows in the gap between the service's connect
  and its first settings (2-6 s on the loaded emulator; not measured on a phone) or while the bubble is hidden (the
  bubble switch, an app in the hidden list), or the bubble menu or crop editor opening in that moment. At the
  user's stops the bubble was floating, so its windows were already there.
- It could add a crash to a stop when ColorOS turns the service off right after it was turned on, or when the user
  turns it on and off quickly. A crash then kills the process; the service, already off the list, is not restarted.
  In the app's log it reads "Uncaught exception in thread main" with `BadTokenException` right after the stop
  report. With the fix (`OverlayWindows.add`) such a stop logs "Window not added, the system is unbinding the
  service" and the process lives on.
- So it explains neither the switch going off nor the bubble's return at 48.4 in the recording.

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
packages by name and other apps as "an app"; 3 s later a line checks the list again. The notification is posted with
that check, 3 s after a stop, when the service has not connected again since (owner, 2026-10-22: each
`uiautomator dump` pauses the service for about 1.5 s, and a notification at the stop itself flashed for every dump;
`StopFollowUp` decides, from a count of connections). It comes for any stop the service does not come back from, the
user's switch included (the app cannot tell a user's switch from the system's), as a banner without sound, and is
cancelled when the service connects again; without the notification permission it is only a log line.

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
reason not to run `uiautomator` while checking stops. Since the delayed notification (checked the same day,
test model UPD6 and UPD9): one dump or two in a row post nothing; `settings put` without Screenlate posts it 3.3 s
after the stop; turning the service on removes it within 0.6 s.

| Fix | What it does | Cost |
| --- | --- | --- |
| Exit reasons in the log | At each process start, log the earlier processes' `ApplicationExitInfo` (reason, description, status or signal, importance, PSS/RSS, time; no text) so the About → Logs export answers crash vs stop on any phone. The first log shows the export alone cannot: on ColorOS the buffer loses the dying process before the user gets to the button, while exit records stay for days. Keeping the service's connect and stop events in a small file of their own (a stop with the process alive means the service was turned off, not killed) would separate 2 from 2a | Small: about half a day with tests; reaches users with the next release. The diagnostics branch has a version to borrow from |
| Stop diagnostics | When the service is destroyed while the process lives, log whether it is still on the enabled list (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` is readable) and the package names of system windows that appeared in the last seconds (`getWindows()` / window-state events; system packages only, no text, no user apps), so a ColorOS prompt or component shows in the app's own log | Small: about half a day with tests and an emulator check |
| "Turned off" notification | On such a stop, a notification that the system turned Screenlate off, with a button to the accessibility settings (the app cannot turn itself back on) | Small to medium: half a day, strings in 14 languages; needs the notification permission |
| "Stopped by the system" card | Remember that the service ran; on the next app start, if it is off without a clean stop (onDestroy with the process alive) and the exit reason was a stop or crash, show a Home card with the steps (Background work, the accessibility page) | Medium: 1–1.5 days, strings in 14 languages, emulator check |
| Fix the crash | Once a trace shows it | Unknown until the log |
| Keep-alive on by default on phones with a maker's startup screen | The notification that makes background killers leave the app alone more often | Small (hours), only if the notification test helps |
| Lighter scans by default there | "Reduce load on the device", or skipping the on-device whole-screen pass once the cloud answered | Small to medium, only if the engine tests point there |
| ColorOS allowlist step | Background work on ColorOS points to the security app's allowlist (and a hint that sideloaded apps are restricted), if the user's test shows the allowlist helps | Small to medium; strings in 14 languages; the user's result (2026-10-28): the allowlist helps |
| Request the accessibility button | The system shortcut would do something in Screenlate (open or scan) instead of turning the service off | Small to medium; a behavior choice for the owner; no help against a force-stop |
