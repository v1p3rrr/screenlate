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

## ML Kit

- The Korean recognizer reads Hangul and Latin but not hanja: 大學校에서 歷史를 came out as 푸에서 끗을 (2026-10-15,
  `testdata/ocr/ko-mixed.png`). Cloud recognition reads them.
- A running ML Kit task cannot be cancelled: it keeps reading its bitmap after the scan is cancelled. The on-device pass
  owns a copy of the screenshot and frees it when the task ends (before this, a recycled bitmap was read).
- Symbol boxes: exact in many words, but in some every inner boundary lags ~0.4 character along the reading direction
  (first box too wide, last too narrow), horizontal and vertical. `SymbolLag` moves inner boundaries back by the offset
  at which they cross the least ink, only when that is clearly better; exact words stay untouched. Checked on the
  thread, game, small-text and manga screenshots in `testdata/ocr/`. Lens and app text are not affected.
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

## Engine comparison (2026-10-16)

Harness `experiments/ocr-compare` (instrumented test), ground truth and scoring in `scripts/ocr-compare/`. Emulator
Pixel_10_Pro (x86_64, 1280×2856), screenshots of a test page in six styles (heading, sans, serif, mono, link, small
grey) in light and dark, plus the home and settings screens in each interface language; 92 screens in 33 language
variants. Time: median of three runs after a warm-up, 4 threads, detector width 960; host CPU, so only the ratios
mean something until the phone run.

CER (character errors, %) / words read whole / taps on the right word / seconds per screen. CJK: by characters, taps
on characters; Thai, Lao, Khmer: CER only. `mlkit` is the app's path (Japanese model for Latin text), `mlkit-latin`
ML Kit's Latin model, Paddle is PP-OCRv5 mobile on MNN (ONNX Runtime gives the same text, 5–20% slower).

| lang | mlkit | mlkit-latin | paddle (model) | tesseract fast | tesseract best |
|---|---|---|---|---|---|
| ja | 0.2 – 99.8 1.6 | | 0.1 – 100 1.4 (main) | 1.0 – 57 0.5 | 0.8 – 41 2.7 |
| ja vertical | 6.3 – 94 1.5 | | 10.1 – 96 1.1 (main) | 9.3 – 26 0.6 | 1.5 – 27 1.5 |
| zh | 3.2 – 95 1.3 | | 0.1 – 100 1.5 (main) | 3.6 – 57 0.5 | 1.1 – 59 1.2 |
| zh-Hant | 3.8 – 94 1.4 | | 4.3 – 100 1.5 (main) | 4.5 – 62 0.5 | 2.3 – 59 1.2 |
| ko | 0.3 99 99 1.3 | | 4.8 75 86 1.2 (korean) | 58.5 32 17 0.4 | 61.1 29 17 1.0 |
| en | 0.1 99 100 2.5 | 0.1 100 100 1.1 | 0.0 100 100 0.9 (latin) | 0.0 100 100 0.4 | 0.0 100 100 1.5 |
| de | 0.4 98 98 2.6 | 0.2 99 99 1.1 | 0.3 100 100 1.0 | 0.0 100 100 0.4 | 0.0 100 100 1.5 |
| fr | 0.7 96 95 2.6 | 0.6 97 95 1.1 | 0.0 100 100 0.9 | 0.3 98 100 0.4 | 0.3 98 100 0.6 |
| es | 0.4 98 99 2.5 | 0.3 98 99 1.1 | 0.2 99 98 0.9 | 0.0 100 97 0.4 | 0.0 100 99 1.4 |
| it | 0.5 97 92 2.4 | 0.4 97 92 1.1 | 0.0 100 100 1.0 | 0.2 99 97 0.4 | 0.1 100 99 1.3 |
| pt | 0.6 98 97 2.4 | 0.2 99 97 1.0 | 0.2 99 99 1.0 | 0.2 100 100 0.4 | 0.3 100 100 1.5 |
| pl | 1.1 93 92 2.2 | 1.0 93 92 1.0 | 0.2 99 98 0.9 | 0.0 100 100 0.4 | 0.0 100 100 1.1 |
| tr | 1.8 88 86 2.0 | 1.7 89 86 0.9 | 0.5 97 97 0.9 | 0.1 100 100 0.4 | 0.1 100 100 0.8 |
| lv | 1.1 93 93 2.1 | 1.1 93 93 0.9 | 0.9 97 97 0.8 | 0.0 100 100 0.4 | 0.0 100 100 0.9 |
| vi | 2.2 93 89 2.0 | 1.1 96 94 0.9 | 13.5 44 42 0.9 | 0.6 99 100 0.4 | 0.7 99 99 1.2 |
| cs | 2.2 89 86 2.1 | 0.9 95 92 0.9 | 1.2 95 95 0.9 | 0.1 99 97 0.4 | 0.1 99 99 1.3 |
| sh (Latin) | 0.5 96 93 2.0 | 0.2 98 95 0.9 | 0.0 100 100 1.0 | 0.0 100 99 0.4 | 0.0 100 99 1.0 |
| ru | | | 1.9 94 92 1.0 (eslav); 1.1 98 97 1.2 (cyrillic) | 1.9 97 99 0.5 | 2.3 97 98 1.5 |
| uk | | | 2.3 92 92 1.3 (eslav); 2.2 97 97 1.3 (cyrillic) | 0.6 96 95 0.5 | 0.4 97 97 1.6 |
| kk | | | 0.2 100 100 1.2 (cyrillic) | 0.0 100 100 0.5 | 0.0 100 100 0.8 |
| sh (Cyrillic) | | | 1.0 97 97 1.0 (cyrillic) | 0.0 100 98 0.4 | 0.1 99 98 0.9 |
| el | | | 0.6 98 98 0.9 (el) | 1.6 90 90 0.4 | 0.8 95 95 1.1 |
| ar | | | 2.4 99 99 0.7 (arabic) | 5.8 94 94 0.4 | 5.9 94 93 1.0 |
| fa | | | 2.6 93 93 0.9 (arabic) | 26.4 72 67 0.3 | 26.5 71 67 0.6 |
| he | | | | 0.0 100 100 0.3 | 0.0 100 100 0.6 |
| hi | 0.6 97 97 1.0 (Devanagari model) | | 1.7 93 91 0.9 (devanagari) | 0.2 99 96 0.5 | 0.4 98 96 1.4 |
| bn | | | | 6.3 91 88 0.3 | 0.5 99 99 1.9 |
| ka | | | | 1.1 98 98 0.6 | 1.1 98 98 0.9 |
| hy | | | | 2.9 86 86 0.4 | 2.0 91 91 0.7 |
| th | | | 0.7 – – 0.8 (th) | 0.8 – – 0.5 | 0.9 – – 0.9 |
| lo | | | | 16.8 – – 0.3 | 17.0 – – 1.1 |
| km | | | | 8.9 – – 0.6 | 8.9 – – 1.2 |

Findings:

- PP-OCRv5 has no recognizer for Hebrew, Georgian, Armenian, Bengali, Lao or Khmer; ML Kit has none for Cyrillic,
  Greek, Arabic or Thai (it has Devanagari). Tesseract has every one.
- Paddle's Latin model lacks Vietnamese letters with two marks (ổ, ộ, ữ…): it drops them. Its Cyrillic models
  sometimes write a whole word in Latin look-alikes ("Mama", "sosed"), which no lookup finds. Its Arabic model writes
  Persian ye and kaf as the Arabic letters (scored as equal; a Persian lookup would fold them). In vertical Japanese
  it drops ー and 、。 (the rotated crop).
- Tesseract's symbol boxes in CJK are off by up to a character and its vertical model reports them in rotated
  coordinates; its word boxes overlap neighbours. Taps on CJK land right 26–62% of the time, so Tesseract is no use
  for CJK. With automatic page segmentation it loses whole lines of Korean, the heading of the Bengali page with the
  fast model and part of the Persian page; on Lao it reads the marks above a line as a separate line of garbage.
- ML Kit's Latin model reads Latin text as well as the app's path through the Japanese model, in less than half the
  time (0.9–1.1 s against 2.0–2.6 s), and adds ~27 KB to the APK (the Japanese AAR already holds `Latn_ctc`).
- tessdata_fast is as accurate as tessdata_best on Latin, Cyrillic, Hebrew, Georgian, Thai, Lao and Khmer and 2–3
  times faster; best is clearly better on Bengali (0.5% against 6.3%), Greek and Armenian.
- MNN and ONNX Runtime read identically; MNN is a little faster and its native library is 2.1 MB per arm64 APK
  against 19.4 MB.

Sizes (arm64 APK; armeabi-v7a, x86_64): Tesseract4Android 8.3 MB (6.2, 8.8), MNN 2.1 MB (1.3, 2.6), ONNX Runtime
19.4 MB (14.0, 23.2). Downloads per language, tessdata_fast / best in MB: rus 3.7/14.6, ukr 3.6/10.4, kaz 4.5/7.2,
srp 2.1/8.9, ell 1.4/8.5, tha 1.0/7.3, ara 1.4/12.0, fas 0.4/3.2, heb 0.9/3.5, hin 1.1/11.3, ben 0.8/10.5, kat
2.4/4.3, hye 3.3/6.1, pol 4.5/11.4, tur 4.3/7.1, lav 2.6/5.4, vie 0.5/11.9, ces 3.6/10.4, hrv 3.9/10.7, lao 6.1/12.9,
khm 1.4/7.7. Paddle: detector 4.5 MB once plus one recognizer per script, 7.4–7.9 MB (cyrillic, eslav, el, th,
arabic, devanagari, latin), 12.8 korean, 15.8 main.

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
