# OCR engines, load and app text

Findings from the load measurements and highlight fixes of 2026-09-28. Code: `core:ocr` (`CompositeOcr`, `OcrOptions`,
`SymbolLag`, `mlkit/MlKitOcrEngine`), `overlay` (`OverlayController.startScan`, `capture/AccessibilityText`,
`web/LookupPage`).

## Cost (emulator, x86_64 without acceleration, debug build)

- Docked: ~0.2% of one core, no alarms or jobs. Main process ~170 MB PSS (~57 MB clean code pages), WebView renderer
  ~35 MB, ML Kit model ~6 MB (not worth freeing on memory pressure).
- One scan: 4.5–10 s of CPU, mostly ML Kit. Lens 1.3–2.9 s wall time, ML Kit band 2.6–4.1 s, ML Kit whole screen
  3–8.8 s. ML Kit's time follows the amount of text, not the image size.
- App text read: 70–150 ms in other apps, 170–620 ms on Screenlate's own screens (home, search, settings, with and
  without the popup shown). A 6.7 s read on Screenlate's screens was seen once in the previous session and could not be
  reproduced afterwards; since then OCR results no longer wait for app text, so a slow read only delays the app text.
  The popup's WebView renderer is unrelated: it is a separate process, and our overlay windows are skipped (only
  `TYPE_APPLICATION` windows are read).
- Phone timings are unknown until the owner shares logs ("OCR draft/final … ms", "ML Kit band … ms", "App text: … ms").

## Scan times in the app (emulator x86_64, debug build 2f698a8, 2026-10-18)

Pixel 10 Pro AVD, 4 cores, 4 GB. Scans of test images in the debug viewer (`testdata/ocr`) and of the launcher's home
screen, a tap on the bubble per scan; medians of 2–4 warm scans per cell, in seconds from the capture start (the
`OverlayController` lines "OCR draft/final from ENGINE: N paragraphs in X ms"). The phone (arm64) is faster or slower
per engine; compare ratios. Draft = the band around the aim; the spinner stops at the final: the whole-screen pass in
"On device", the cloud result in "Both". In brackets: how long the spinner turns after the first text. "–" in Both: the
cloud result came first and the band's draft was dropped.

| Screen | Engine | On device: draft / final | Both: draft / final |
|---|---|---|---|
| `en-sample` | ML Kit Latin | 0.38 / 0.98 (+0.61) | 0.29 / 0.58 (+0.29) |
| | Tesseract fast | 0.20 / 0.57 (+0.37) | 0.31 / 0.64 (+0.29) |
| | PaddleOCR v5 Latin | 0.48 / 1.61 (+1.13) | 0.45 / 0.68 (+0.25) |
| | PaddleOCR v6 | 0.42 / 1.66 (+1.24) | 0.51 / 0.76 (+0.20) |
| | cloud only | final 0.51 | |
| `en-page-light` | ML Kit Latin | 0.73 / 2.82 (+1.95) | – / 0.76 |
| | Tesseract fast | 0.58 / 1.51 (+0.93) | 0.69 / 0.81 (+0.11) |
| | PaddleOCR v5 Latin | 0.71 / 2.84 (+2.15) | – / 0.76 |
| | PaddleOCR v6 | 1.02 / 4.06 (+2.99) | – / 0.73 |
| | cloud only | final 0.65 | |
| home screen | ML Kit Latin | 0.13 / 0.57 (+0.44) | 0.21 / 0.81 (+0.56) |
| | Tesseract fast | 0.12 / 0.39 (+0.26) | 0.20 / 0.73 (+0.54) |
| | PaddleOCR v5 Latin | 0.24 / 1.34 (+1.10) | 0.35 / 0.77 (+0.46) |
| | PaddleOCR v6 | 0.26 / 1.49 (+1.27) | 0.41 / 0.79 (+0.36) |
| | cloud only | final 0.59 | |
| `de-sample` | ML Kit Latin | 0.89 / 1.99 (+1.18) | 0.73 / 0.82 (2 of 3 drafts) |
| | PaddleOCR v5 Latin | 0.61 / 1.98 (+1.35) | 0.56 / 0.81 (+0.31) |
| | PaddleOCR v6 | 0.66 / 2.68 (+1.99) | 0.73 / 0.89 (2 of 3 drafts) |
| | Tesseract fast (deu)\* | 0.61 / 1.28 (+0.67) | 0.62 / 0.93 (+0.31) |
| `de-page-light`\* | Tesseract fast (deu) | 0.99 / 2.87 (+1.88) | 0.80 / 0.97 (1 of 3 drafts) |
| `ru-sample` | Tesseract fast | 0.59 / 1.28 (+0.67) | 0.68 / 0.81 (+0.12) |
| | PaddleOCR v5 Cyrillic | 0.56 / 2.21 (+1.67) | 0.63 / 0.91 (+0.28) |
| `ru-page-light` | Tesseract fast | 0.78 / 2.78 (+2.12) | 0.65 / 0.99 (+0.36) |
| | PaddleOCR v5 Cyrillic | 0.63 / 4.33 (+3.54) | 0.80 / 0.80 (1 of 3 drafts) |
| `ja-page-light` | ML Kit Japanese | 1.02 / 3.68 (+2.78) | – / 0.70 |
| | PaddleOCR v6 | 0.99 / 3.79 (+2.90) | – / 0.64 |
| | cloud only | final 0.60 | |
| `x-game.jpg` (39–60 paragraphs) | ML Kit Japanese | 1.23 / 6.00 (+4.80) | – / 0.87 |
| | PaddleOCR v6 | 0.84 / 5.72 (+4.87) | – / 0.89 |
| | cloud only | final 0.85 | |
| `ko-sample` | ML Kit Korean | 0.83 / 1.87 (+0.94) | – / 0.62 (1 of 4 drafts) |
| `zh-sample`\* | ML Kit Chinese | 1.75 / 4.68 (+2.93) | – / 0.85 |
| | PaddleOCR v6 | 0.75 / 2.33 (+1.58) | 0.63 / 0.97 (+0.34) |
| | cloud only | final 0.73 | |
| `zh-page-light`\* | ML Kit Chinese | 2.88 / 6.84 (+3.96) | – / 0.96 |
| | PaddleOCR v6 | 0.79 / 3.60 (+2.81) | 0.76 / 0.88 (1 of 3 drafts) |
| | cloud only | final 0.90 | |

\* Measured 2026-10-19 on `languages/checks` after `7783c5e` (the same AVD, captures 0.2–0.4 s instead of 0.1–0.2 s,
so these rows run a little slow against the others). ML Kit Chinese is the slowest on-device reader on the emulator:
its band alone takes 1.3–3 s, so in "Both" the cloud result always comes first.

- "On device": the spinner after the first text waits for the whole-screen pass, which runs after the band on the same
  engine: +0.3–1.2 s on short screens, +1–3.5 s on full pages, ~+5 s on the game screen. Tesseract fast has the
  shortest whole pass, PaddleOCR the longest (v6 > v5), ML Kit Latin is in between. On the clean sample the whole pass
  alone matches the offline comparison (`testdata/ocr/compare/ocr-score.txt`): v5 1.13 s vs 0.93, ML Kit Latin 0.60 vs
  1.13, Tesseract fast 0.36 vs 0.40; the user waits band + whole pass, and full pages take 2–3 times longer.
- "Both": the cloud result comes 0.6–0.9 s after the capture (HTTP 0.35–0.6 s, parsing ~80 ms). A PaddleOCR band
  arrives 0.2–0.3 s before it on short screens and after it on full pages, so it shows nothing; ML Kit Latin and
  Tesseract bands come 0.3–0.5 s earlier. The first cloud request after a few idle minutes took ~2 s (2.0–2.2 s,
  seen four times, once after 8 minutes docked): the server had closed the connection.
- Opening the connection on a touch (2026-10-19, `Preconnection`, a HEAD request to the host): after 5 idle minutes the
  HEAD took 0.6 s on the emulator's Wi-Fi and 1.5 s on its mobile network (1.7 s right after the network came back), so
  the connection was new; the scan's request waited for it and then took the usual 0.55–0.7 s on it. The first cloud
  result after the pause came 1.17 s and 1.82 s after the scan start (HTTP 0.72 s and 1.32 s), against 2.0–2.2 s for the
  HTTP request alone before. What it saves is the time from the touch to the request: ~0.3–0.5 s for a tap (capture and
  encoding), ~1 s for a pull-out; a longer handshake still shows. Within 30 s of the last contact no HEAD is sent; a
  HEAD 45 s after it took 86 ms (the pooled connection was still open). Nothing is sent in Device mode or offline
  ("Offline: no active network" at the touch, the scan ends with `OfflineException` as before).
- The cloud result's parsing competes for the 4 cores with a running band: usually ~80 ms after the HTTP response,
  up to 1.4 s when ML Kit Japanese ran a band at the same time.
- Model lifecycle: nothing is released between scans or while docked (8 minutes docked: same native heap); "Released
  the on-device model" only when Engines becomes cloud or the text source app text only. Until 2026-10-19 a model
  chosen in the dialog was loaded by the next scan: v5 Latin detector and recognizer ~0.1 s; v6 ~0.2 s plus a slow
  first inference (band 1.47 s instead of 0.86 s). Since then the overlay warms the active language's model up when
  another one takes over (`DeviceOcr.activeUpdates` → `CompositeOcr.deviceModelChanges`): v6 loads and warms up in
  0.2–0.4 s right after the tap in the dialog, with no scan.
- Cold warm-up (the first recognition on a 32 × 32 image after the process starts, logged as "On-device recognition
  for … warmed up in … ms"): ML Kit Latin 0.65–1.6 s, Korean 0.7 s, Japanese 1.07–1.5 s; PaddleOCR v6 0.48 s. A warm
  engine takes 0.15–0.4 s for the same image. A `uiautomator dump` suspends and rebinds the accessibility service,
  which warms the model up again; checks of the warm-up on choice must not dump the UI after the choice.
- Native memory: a PaddleOCR model's first whole-screen pass raises the native heap by ~370 MB (v5) to ~410 MB (v6)
  and keeps it until the model is released; repeated scans of the same screen stay flat, so this is MNN's session
  memory for the largest input (the detector's 960 × 2144 input for a 1280 × 2856 screen), not a per-scan leak. ML Kit
  Latin adds ~50 MB, Tesseract fast ~38 MB. Switching the active language to one that reads with ML Kit keeps the
  PaddleOCR model and its memory. With it the emulator's main process held 700–760 MB PSS and swapped.
- Freeing it (2026-10-19): `PaddleOcrEngine` trims both networks 10 s after the last recognition (`IdleAction`,
  `MnnNetwork.trim`) and keeps the models loaded. On the Japanese page with v6: MNN 362 → 34 MB in 16–24 ms, native heap
  483 → 143 MB, PSS 714 → 351 MB; three scans 8 s apart trimmed once, 10 s after the last one; the next scan still
  read 14 paragraphs. Trimming after every pass instead would cost ~0.25 s per full-page scan, since the next whole
  pass allocates the buffer again (medians of 8 scans each: band 731 → 791 ms, whole 2678 → 2940 ms, final
  3689 → 3935 ms). MNN details: the session's buffer for intermediate tensors only grows; `Runtime::onGabageCollect(100)`
  frees it (internal header `core/Backend.hpp`, so the build adds `MNN/source` to the include path; the session takes a
  runtime from `Interpreter::createRuntime` to reach it). After a collection the tensors have no memory until the
  session resizes, and `resizeSession` with an unchanged input shape does nothing, so the trim resizes to a wider shape,
  collects, and resizes back to the small one, which takes a small buffer again.
- From the tap to the capture start: 10–20 ms. When the popup's WebView is gone (the service was
  reconnected, or the renderer was reclaimed), `LookupPage.prepare()` creates it on the main thread. Until 2026-10-19
  this ran at the scan start: 0.75–0.9 s of skipped frames before the capture ("Skipped 49 frames", "Davey!
  duration=744ms"), not included in the logged OCR times. Since then it runs 150 ms after the capture, while the
  recognition works on its threads. Checked by crashing the renderer of a hidden popup (`am crash <pid>`, log "renderer
  gone (crashed: true, shown: false)") and scanning: captures 255–265 ms (241–324 ms without the crash), the renderer
  process started 0.25–0.3 s after the capture, the PaddleOCR draft came 0.3–0.6 s later than without the crash
  (1.42–1.58 s vs 1.02–1.11 s), the final within the noise, no "Skipped frames" line; the popup showed the word after
  the scan. A renderer crashed while the popup is shown is recreated at once ("shown: true").
- The spinner stopped at every final (checked in the logs of every scan and once on screenshots); rapid rescans in
  "Both" with PaddleOCR v6 (the next tap right after the final) kept the cloud final at 0.69–1.0 s.
- The emulator drifts: after ~1.5 h of scans with PaddleOCR memory held, captures went from 0.1 s to 0.4–2.9 s and
  ML Kit Japanese bands from 0.9 s to 3–5 s; a cold boot restored it. The table uses runs from healthy states only.

## ML Kit

- The Korean recognizer reads Hangul and Latin but not hanja: 大學校에서 歷史를 came out as 푸에서 끗을 (2026-10-15,
  `testdata/ocr/ko-mixed.png`). Cloud recognition reads them.
- ML Kit has no Cyrillic recognizer: `MlKitOcrEngine` returns no recognizer for `OcrScript.CYRILLIC` (Russian,
  `languages/ru`, 2026-10-16), so only cloud recognition and app text read Russian until an on-device engine for it
  is built in (the OCR comparison in the languages plan).
- A running ML Kit task cannot be cancelled: it keeps reading its bitmap after the scan is cancelled. The on-device pass
  owns a copy of the screenshot and frees it when the task ends (before this, a recycled bitmap was read).
- Symbol boxes: exact in many words, but in some every inner boundary lags ~0.4 character along the reading direction
  (first box too wide, last too narrow), horizontal and vertical. `SymbolLag` moves inner boundaries back by the offset
  at which they cross the least ink, only when that is clearly better; exact words stay untouched. Checked on the
  thread, game, small-text and manga screenshots in `testdata/ocr/`. Lens and app text are not affected.
- ML Kit leaves a line-final hyphen out of the line's words ("impor" / "tant", emulator, LK30), so the lookup cannot
  join a hyphenated word read by ML Kit. Lens keeps the hyphen but gives the word no separator ("impor-tant");
  `withHyphenBreaks` puts the language's separator there.
- `OcrOptions.deferWholeImage` ("Reduce load on the device"): the band around the aim runs at once, the whole screen
  only when Lens is 3 s late, fails or is unavailable.
- Threads: the scan flow is collected on the main thread, so every engine moves its own heavy work off it: ML Kit's
  line building (pixel reads for `SymbolLag`) on Default, `CompositeOcr`'s screenshot copy and band crops on its
  `worker` (Default), the Lens JPEG on Default and the whole HTTP exchange including the body on IO. `withContext`
  waits for its block even when cancelled, so a bitmap freed after the call returns is no longer read.
- But `withContext` to another dispatcher drops its result when the caller was cancelled meanwhile: a bitmap created
  inside it must be freed in a `catch (CancellationException)` (`CompositeOcr.copyOnWorker`). A coroutine cancelled
  before it starts skips its body and its `finally`; the on-device draft therefore starts undispatched.
- No `CancellationException` may leave the scan flow unless the scan itself was cancelled: the overlay rethrows it
  from `.catch`, so the scan ends silently with the spinner on. Lens timeouts become `SocketTimeoutException`
  (`askLens`), a cancelled ML Kit task fails with an ordinary exception.

## Screenshot lifetime (overlay)

- `Bitmap.createBitmap(src, 0, 0, w, h)` returns `src` itself for an immutable source and the full rectangle; code that
  recycles its crop must compare with the source (`CropView.cropped` copies instead, since the note frees it).
- The scan's screenshot is a `SharedScreenshot`, created right after the capture: the scan holds it until `resetScan`,
  the recognition flow and each OCR boost band retain it while they read it, and a note retains it in `notePicture`
  and releases it once the crop editor returns (the editor draws it meanwhile). The bitmap is freed by the last user,
  so a dock, rotation or hide while the editor is open no longer recycles what it draws; closing the scan also closes
  the editor as cancelled (owner). No timeout: the screenshot is needed as long as the bubble is out.
- The screenshot callback copies the hardware buffer on `Dispatchers.Default` (the capturer's executor); a screenshot
  that arrives after the scan was cancelled is recycled in the continuation's `onCancellation`.

- ➕ calls `noteSource()` synchronously: it fixes the shown view's layout and position (the sentence). Until the scan's
  final result ➕ is grey and a tap only toasts a hint (`noteWaitsForText` in `OcrStatus.kt`, page state `noteWait`,
  bridge `onNoteWaiting`); a word from app text (engine ACCESSIBILITY) is exact and can be added at once. A new scan
  closes the previous popup (`closePopup()` in `startScan`), so ➕ of an old word cannot take the new scan's sentence.
  The earlier withdrawal of the pending cloud request on ➕ (`stopCloud`, `cloudWithdrawn`) was removed (owner's
  answers Q1/Q2 of the 2026-09-30 reviews). Checked on the emulator with a hanging proxy (see `build-environment.md`):
  the draft popup shows ➕ grey with the spinner, a tap shows the two-line hint, the final result makes ➕ active, and
  a new scan closes the old popup at once. A picture is
  taken only while the same scan is open (`scanId`), so the crop editor never opens after a dock; the note itself goes
  on, but its result reaches neither the next scan's note memory (`ScanNotes`) nor its popup. It is shown on the entry
  of its term (`entryOf`), not by index.
- `CropEditor` removes its window through a main-thread Handler on coroutine cancellation (`View.post` never runs on
  a view that was not attached); `CropEditorTest` records windows through a `WindowManager` proxy.

## App text (accessibility tree)

- Known bad character boxes, both left to OCR:
  - WebView answering with the node's bounds for every character (`sharesBoxes`);
  - Compose text fields: boxes relative to the whole field, padding included, so up-left by the padding (about three
    characters on the search screen). Detected as an editable node whose first box is at the node's corner
    (`startsAtCorner`).
- A WebView's text (the search screen's result page) did not show up as text nodes at all; OCR covers it.
- A text view gives a combining mark typed apart (U+0301, U+0300) an empty box; `appTextLines` keeps it with its
  letter in the letter's box instead of dropping it and ending the line there.
- App text takes precedence over OCR where both have text; unknown apps with other box errors would show a shifted
  highlight or find a neighboring word. That is why "App text first" stays experimental and off by default (owner,
  2026-09-28).

## Popup renderer

- The popup's WebView uses `setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, waivedWhenNotVisible = true)`, so
  the system may kill the renderer while the popup is hidden. `onRenderProcessGone` then drops the WebView and the next
  scan recreates it; a renderer lost while shown is recreated at once and the popup closes. The search screen's page
  keeps the default priority.
- To simulate a reclaimed renderer: `adb shell ps -A | grep sandboxed_process` for the app's renderer PID, then
  `adb shell am crash <pid>`.

## Highlight layer

- The full-screen highlight layer used to stay composited while docked (~43 MB of graphics buffers). `LayerView` now
  goes `GONE` one second after it has nothing to draw, which takes its window off the screen.
