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

- ➕ calls `noteSource()` synchronously: it fixes the shown view's layout and position (the sentence), completes the
  scan's `cloudStop` (`CompositeOcr.recognize(stopCloud)`: the ML Kit page becomes final without a Lens error, a
  cloud-only scan ends without a final and the controller marks it final itself) and cancels and disables OCR boost
  bands for that scan (owner). A picture is taken only while the same scan is open (`scanId`), so the crop editor never
  opens after a dock; the note itself goes on. Its result is shown on the entry of its term (`entryOf`), not by index.
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
