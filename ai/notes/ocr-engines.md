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

## Engine comparison (2026-10-16, extended 2026-10-17)

Harness `experiments/ocr-compare` (instrumented test), ground truth and scoring in `scripts/ocr-compare/`, local
material and the scripts for the last two sets in `testdata/ocr/compare/` (outside git). Emulator Pixel_10_Pro
(x86_64, 1280×2856). Four sets:

- clean: screenshots of a test page in six styles (heading, sans, serif, mono, link, small grey) in light and dark,
  plus the home and settings screens in each interface language; 92 screens in 33 language variants;
- hard: 125 screens of `hard.html` in the same languages (below);
- real: 7 app screenshots (feeds, a reply thread, a manga reader, vertical Traditional Chinese, Korean with hanja);
- wild: 112 photos and comic pages (below).

Time: median of three runs after a warm-up, 4 threads, detector width 960; host CPU, so only the ratios mean something
until the phone run. `lens` is cloud recognition: one request per screen, 1.5 s apart; its time is the round trip from
the emulator (median 0.57 s, 0.37–4.7 s over 336 screens), so only its errors are compared.

Paddle (v5 and v6) is `core:ocr`'s pipeline with lines cropped along their rotated rectangles (below, "Rotated
crop"); its earlier numbers, with axis-aligned crops, are quoted only where they show the change.

### Clean screens

CER (character errors, %) / words read whole / taps on the right word / seconds per screen. CJK: by characters, taps
on characters; Thai, Lao, Khmer: CER only. `mlkit` is the app's path (Japanese model for Latin text), `mlkit-latin`
ML Kit's Latin model, Paddle is PP-OCRv5 mobile on MNN (ONNX Runtime gives the same text, 5–20% slower), `paddle v6`
PP-OCRv6 small with its own detector (below). Lens: CER only; its words and taps are 98–100% in every language.
Paddle's seconds are from the run before the rotated crop: the re-run shared the emulator with other work and took
1.0–2.2 s per screen, while the crop itself costs what the old one did (below).

| lang | mlkit | mlkit-latin | paddle (model) | paddle v6 | tesseract fast | tesseract best | lens |
|---|---|---|---|---|---|---|---|
| ja | 0.2 – 99.8 1.6 | | 0.1 – 100 1.4 (main) | 0.1 – 100 1.5 | 1.0 – 57 0.5 | 0.8 – 41 2.7 | 0.0 |
| ja vertical | 6.3 – 94 1.5 | | 9.7 – 96 1.1 (main) | 9.3 – 96 1.3 | 9.3 – 26 0.6 | 1.5 – 27 1.5 | 0.0 |
| zh | 3.2 – 95 1.3 | | 0.1 – 100 1.5 (main) | 0.1 – 100 1.4 | 3.6 – 57 0.5 | 1.1 – 59 1.2 | 0.0 |
| zh-Hant | 3.8 – 94 1.4 | | 4.5 – 100 1.5 (main) | 2.9 – 100 1.5 | 4.5 – 62 0.5 | 2.3 – 59 1.2 | 0.0 |
| ko | 0.3 99 99 1.3 | | 4.6 75 86 1.2 (korean) | | 58.5 32 17 0.4 | 61.1 29 17 1.0 | 0.1 |
| en | 0.1 99 100 2.5 | 0.1 100 100 1.1 | 0.0 100 100 0.9 (latin) | 0.0 100 100 1.5 | 0.0 100 100 0.4 | 0.0 100 100 1.5 | 0.0 |
| de | 0.4 98 98 2.6 | 0.2 99 99 1.1 | 0.3 100 100 1.0 | 0.0 100 100 1.6 | 0.0 100 100 0.4 | 0.0 100 100 1.5 | 0.0 |
| fr | 0.7 96 95 2.6 | 0.6 97 95 1.1 | 0.0 100 100 0.9 | 0.0 100 100 1.6 | 0.3 98 100 0.4 | 0.3 98 100 0.6 | 0.4 |
| es | 0.4 98 99 2.5 | 0.3 98 99 1.1 | 0.2 99 98 0.9 | 0.2 99 98 1.6 | 0.0 100 97 0.4 | 0.0 100 99 1.4 | 0.0 |
| it | 0.5 97 92 2.4 | 0.4 97 92 1.1 | 0.0 100 100 1.0 | 0.0 100 100 1.6 | 0.2 99 97 0.4 | 0.1 100 99 1.3 | 0.0 |
| pt | 0.6 98 97 2.4 | 0.2 99 97 1.0 | 0.0 100 100 1.0 | 0.0 100 100 1.6 | 0.2 100 100 0.4 | 0.3 100 100 1.5 | 0.0 |
| pl | 1.1 93 92 2.2 | 1.0 93 92 1.0 | 0.2 99 98 0.9 | 0.2 98 97 1.5 | 0.0 100 100 0.4 | 0.0 100 100 1.1 | 0.0 |
| tr | 1.8 88 86 2.0 | 1.7 89 86 0.9 | 0.5 96 96 0.9 | 0.7 95 94 1.6 | 0.1 100 100 0.4 | 0.1 100 100 0.8 | 0.1 |
| lv | 1.1 93 93 2.1 | 1.1 93 93 0.9 | 0.8 97 97 0.8 | 0.5 97 97 1.3 | 0.0 100 100 0.4 | 0.0 100 100 0.9 | 0.0 |
| vi | 2.2 93 89 2.0 | 1.1 96 94 0.9 | 13.4 44 41 0.9 | 12.7 46 46 1.5 | 0.6 99 100 0.4 | 0.7 99 99 1.2 | 0.0 |
| cs | 2.2 89 86 2.1 | 0.9 95 92 0.9 | 1.0 95 95 0.9 | 1.0 95 95 1.2 | 0.1 99 97 0.4 | 0.1 99 99 1.3 | 0.0 |
| sh (Latin) | 0.5 96 93 2.0 | 0.2 98 95 0.9 | 0.0 100 100 1.0 | 0.0 100 100 1.3 | 0.0 100 99 0.4 | 0.0 100 99 1.0 | 0.0 |
| ru | | | 1.6 94 93 1.0 (eslav); 1.0 97 97 1.2 (cyrillic) | | 1.9 97 99 0.5 | 2.3 97 98 1.5 | 0.0 |
| uk | | | 2.6 90 90 1.3 (eslav); 1.8 97 97 1.3 (cyrillic) | | 0.6 96 95 0.5 | 0.4 97 97 1.6 | 0.0 |
| kk | | | 0.2 100 100 1.2 (cyrillic) | | 0.0 100 100 0.5 | 0.0 100 100 0.8 | 0.0 |
| sh (Cyrillic) | | | 1.3 96 96 1.0 (cyrillic) | | 0.0 100 98 0.4 | 0.1 99 98 0.9 | 0.0 |
| el | | | 0.7 98 98 0.9 (el) | | 1.6 90 90 0.4 | 0.8 95 95 1.1 | 0.0 |
| ar | | | 2.4 100 100 0.7 (arabic) | | 5.8 94 94 0.4 | 5.9 94 93 1.0 | 0.4 |
| fa | | | 2.2 96 96 0.9 (arabic) | | 26.4 72 67 0.3 | 26.5 71 67 0.6 | 1.4 |
| he | | | | | 0.0 100 100 0.3 | 0.0 100 100 0.6 | 0.0 |
| hi | 0.6 97 97 1.0 (Devanagari model) | | 0.9 96 96 0.9 (devanagari) | | 0.2 99 96 0.5 | 0.4 98 96 1.4 | 0.0 |
| bn | | | | | 6.3 91 88 0.3 | 0.5 99 99 1.9 | 0.0 |
| ka | | | | | 1.1 98 98 0.6 | 1.1 98 98 0.9 | 0.0 |
| hy | | | | | 2.9 86 86 0.4 | 2.0 91 91 0.7 | 0.0 |
| th | | | 0.4 – – 0.8 (th) | | 0.8 – – 0.5 | 0.9 – – 0.9 | 0.0 |
| lo | | | | | 16.8 – – 0.3 | 17.0 – – 1.1 | 4.3 |
| km | | | | | 8.9 – – 0.6 | 8.9 – – 1.2 | 0.0 |

Findings on clean screens:

- PP-OCRv5 has no recognizer for Hebrew, Georgian, Armenian, Bengali, Lao or Khmer; ML Kit has none for Cyrillic,
  Greek, Arabic or Thai (it has Devanagari). Tesseract has every one.
- Paddle's Latin model lacks Vietnamese letters with two marks (ổ, ộ, ữ…): it drops them. Its Cyrillic models
  sometimes write a whole word in Latin look-alikes ("Mama", "sosed"), which no lookup finds. Its Arabic model writes
  Persian ye and kaf as the Arabic letters (scored as equal; a Persian lookup would fold them). In vertical Japanese
  it drops ー and 、。 (the column is read turned a quarter).
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
- Lens: 0–0.4% everywhere except Persian (1.4%, punctuation) and Lao (4.3%, some Lao letters come back as their Thai
  look-alikes).

### Hard screens

`scripts/ocr-compare/hard.html` draws the clean page's texts in styles that are hard to read, four screens per
language: scene (text over a photo-like background, over a strong gradient, outlined subtitles over a busy
background), font (a handwritten font, a comic or display font, small grey text on light grey), italic (sans-serif,
serif, the display font) and tilt (lines turned by −5° in sans-serif, 5° in serif, −10° in the handwritten font); plus
one vertical Japanese page with speech bubbles over screentone. Fonts come from Google Fonts, which has no handwritten
or display font for Georgian and Lao and no handwritten one for Armenian; those blocks use monospace or the other
font. Each block is scored by its style (`score.py --by style`; a turned word's characters are placed along its
slope). `v6 + v5 det` is PP-OCRv6's recognizer after PP-OCRv5's detector.

CER % per language:

| lang | mlkit | mlkit-latin | paddle | paddle cyrillic | paddle v6 | v6 + v5 det | tess fast | tess best | lens |
|---|---|---|---|---|---|---|---|---|---|
| ja | 6.6 |  | 1.3 |  | 0.4 | 1.1 | 63.8 | 70.3 | 0.8 |
| javert | 8.6 |  | 8.6 |  | 3.9 | 5.5 | 72.7 | 34.4 | 0.8 |
| zh | 5.7 |  | 0.2 |  | 0.0 | 0.2 | 56.5 | 54.4 | 0.0 |
| zht | 5.7 |  | 2.3 |  | 0.3 | 1.2 | 60.4 | 59.9 | 0.2 |
| ko | 8.8 |  | 4.5 |  |  |  | 73.9 | 75.2 | 0.3 |
| en | 1.5 | 1.0 | 0.3 |  | 0.3 | 0.7 | 28.7 | 29.4 | 0.0 |
| de | 2.0 | 1.0 | 0.6 |  | 0.3 | 0.9 | 36.3 | 36.3 | 0.0 |
| fr | 2.1 | 1.7 | 0.7 |  | 0.4 | 0.9 | 33.5 | 34.1 | 0.1 |
| es | 2.0 | 2.0 | 0.6 |  | 1.0 | 1.1 | 37.9 | 38.1 | 0.0 |
| it | 1.9 | 1.9 | 0.5 |  | 0.3 | 1.0 | 43.1 | 43.4 | 0.1 |
| pt | 1.4 | 1.0 | 0.2 |  | 0.3 | 0.3 | 31.5 | 31.7 | 0.0 |
| pl | 6.2 | 5.1 | 2.0 |  | 1.7 | 1.9 | 38.4 | 37.5 | 0.2 |
| tr | 3.6 | 3.2 | 1.7 |  | 1.9 | 2.3 | 41.1 | 38.7 | 0.0 |
| lv | 4.0 | 3.8 | 1.5 |  | 1.0 | 1.8 | 40.6 | 40.0 | 0.0 |
| vi | 8.2 | 3.6 | 15.3 |  | 14.5 | 15.1 | 44.4 | 44.3 | 0.0 |
| cs | 5.6 | 2.9 | 1.9 |  | 1.6 | 2.1 | 45.0 | 45.3 | 0.2 |
| sh | 2.4 | 1.6 | 1.0 |  | 0.5 | 1.0 | 26.4 | 31.2 | 0.1 |
| ru |  |  | 3.1 | 2.7 |  |  | 31.2 | 31.2 | 0.0 |
| uk |  |  | 5.4 | 4.3 |  |  | 30.1 | 31.5 | 0.0 |
| kk |  |  | 3.4 |  |  |  | 38.6 | 36.7 | 0.0 |
| shc |  |  | 3.7 |  |  |  | 35.4 | 33.5 | 0.2 |
| el |  |  | 1.8 |  |  |  | 40.1 | 40.6 | 0.0 |
| ar |  |  | 3.5 |  |  |  | 58.8 | 56.1 | 0.5 |
| fa |  |  | 5.2 |  |  |  | 49.9 | 49.4 | 0.7 |
| he |  |  |  |  |  |  | 48.5 | 43.7 | 5.1 |
| hi | 2.5 |  | 3.7 |  |  |  | 39.5 | 39.7 | 0.1 |
| bn |  |  |  |  |  |  | 52.0 | 43.3 | 0.0 |
| ka |  |  |  |  |  |  | 38.3 | 38.2 | 0.5 |
| hy |  |  |  |  |  |  | 44.9 | 43.6 | 0.0 |
| th |  |  | 1.8 |  |  |  | 33.2 | 33.4 | 0.0 |
| lo |  |  |  |  |  |  | 53.9 | 53.3 | 3.0 |
| km |  |  |  |  |  |  | 57.1 | 56.6 | 0.2 |

CER % per style, over every language the engine reads (Paddle v5 also reads Korean, Cyrillic, Greek, Arabic, Persian,
Hindi and Thai; v6 only CJK and Latin):

| style | mlkit | mlkit-latin | paddle | paddle cyrillic | paddle v6 | v6 + v5 det | tess fast | tess best | lens |
|---|---|---|---|---|---|---|---|---|---|
| display | 2.0 | 1.5 | 2.8 | 1.7 | 1.2 | 1.7 | 48.4 | 48.3 | 0.1 |
| gradient | 1.0 | 0.7 | 3.8 | 8.8 | 1.0 | 3.5 | 72.9 | 72.4 | 0.2 |
| hand | 6.3 | 4.1 | 3.9 | 5.2 | 2.1 | 2.7 | 10.2 | 9.1 | 1.1 |
| italic-display | 6.0 | 1.9 | 1.2 | 0.0 | 0.9 | 0.9 | 15.3 | 14.6 | 0.1 |
| italic-sans | 0.8 | 0.9 | 1.0 | 0.0 | 0.9 | 0.8 | 6.2 | 5.3 | 0.1 |
| italic-serif | 1.9 | 1.8 | 1.4 | 0.3 | 1.0 | 1.0 | 6.2 | 6.4 | 0.1 |
| manga | 8.6 |  | 8.6 |  | 3.9 | 5.5 | 72.7 | 34.4 | 0.8 |
| photo | 3.1 | 1.5 | 3.6 | 10.7 | 1.1 | 2.4 | 80.2 | 78.8 | 0.1 |
| small | 0.8 | 0.7 | 1.2 | 0.0 | 1.1 | 1.1 | 50.0 | 50.0 | 0.4 |
| subtitle | 12.1 | 9.6 | 5.6 | 9.1 | 4.3 | 5.1 | 73.9 | 76.6 | 0.3 |
| tilt-hand | 6.8 | 3.8 | 3.0 | 5.0 | 2.7 | 2.6 | 72.3 | 72.1 | 1.2 |
| tilt-sans | 0.6 | 0.4 | 0.9 | 0.0 | 0.9 | 0.9 | 14.8 | 12.4 | 0.1 |
| tilt-serif | 1.3 | 1.0 | 1.1 | 0.0 | 1.1 | 1.1 | 36.4 | 36.3 | 0.0 |

Findings on hard screens:

- Turned lines: Paddle reads them at 0.9–3.0% (v5) and 0.9–2.7% (v6) per style, ML Kit at 0.4–6.8%, Tesseract at
  12–72%, Lens at 0–1.2%. With axis-aligned crops Paddle was at 66–98% (below).
- ML Kit's weak spots are outlined subtitles (9.6–12.1%) and, in CJK, handwriting (21.0% against Paddle's 5.9% and
  v6's 0.7%); Paddle v5's are text over photos and gradients (Latin and CJK: 1.8–3.3%; v6: 0.0–1.1%).
- Tesseract loses whole blocks: small grey text 50%, text over photos 79–80%, gradients 72–73%, outlined subtitles
  74–77%; its page segmentation drops a low-contrast block entirely.
- Lens: 0–0.8% in every language except Hebrew (5.1%: 27–33% on the handwritten font, a Hebrew cursive) and Lao
  (3.0%: 15% on the small grey text).
- tessdata_best against tessdata_fast, clean + hard: best has 63% fewer errors in vertical Japanese (8.5% against
  22.8%; the manga page 34.4% against 72.7%), 22% fewer in Bengali (27.2 against 34.7; clean 0.5 against 6.3), 10%
  fewer in Hebrew (27.2 / 30.2) and 4% in Armenian (26.9 / 28.1); fast is better on horizontal Japanese hard screens
  (63.8 / 70.3) and Serbo-Croatian in Latin (26.4 / 31.2). Elsewhere they are within 2.3 points. Under the rule best
  never replaces fast: where it is clearly better, ML Kit or Paddle is better still (vertical Japanese) or the
  difference stays under a third (Bengali).

### Rotated crop

`core:ocr` used to crop each detected region by its axis-aligned bounding box. For a line turned by 5–10° that box
is several lines tall and takes in parts of the neighbours; squeezed to 48 px, the crop was unreadable: 66–98% CER on
the turned lines, v5 and v6 alike, and 2–9% of taps on the right word. Now `TextDetection` takes each region's
smallest enclosing rectangle at any angle (`OrientedBox`: rotating calipers over the hull of the region's row ends),
unclips it as before, and `PaddlePipeline` warps it upright into the recognizer's input: an affine map through a
`BitmapShader` with clamped edges, as PaddleOCR's `get_rotate_crop_image` does with replicated borders. A rectangle
at least 1.5 times as tall as long is read as a column, top first. Word and character boxes are the bounding boxes of
their stretches along the rectangle, cut to the image.

- Turned lines, CER % per style, before → after: v5 hand 96.1 → 3.0, sans 84.5 → 0.9, serif 77.5 → 1.1; v6 97.0 →
  2.7, 66.9 → 0.9, 66.1 → 1.1; the Cyrillic model 68.7–98.4 → 0.0–5.0. The other styles move by at most 0.8 points
  either way (v5 on the manga page 7.8 → 8.6).
- Taps on turned lines land on the right word 75–92% of the time (ML Kit 68–91%, its Latin model 81–97%), against
  95–96% on straight italic: the bounding box of a slanted word overlaps the lines above and below.
- Photos and comic pages gain the most (pooled, below): v5 23.4 → 12.4% in CJK and 23.1 → 14.0% in Latin, v6 16.4 →
  8.1% and 17.9 → 10.6%. A Latvian tram poster in slanted lettering goes from 94.2% (v5) and 73.3% (v6) to 0.0%, a
  Korean café menu from 15.5% to 3.4%.
- Cost: the warp takes what the axis-aligned crop and scale took, ~2.5 ms per line on the emulator, upright or
  turned by 5° (`CropTimingTest` in the harness: 30 lines on a 1280×2856 screen, 10 rounds after a warm-up).
- The re-run also fixed the harness's right-to-left order: reversed Arabic and Persian words kept their own
  separators, so the first two words of each line came out glued. That affected the earlier hard and wild numbers
  for ar and fa; the clean run predates separators in the output.

### Recommendation under the plan's rule

Fewest errors over clean + hard; another model replaces the base (ML Kit as the app uses it for CJK and Latin,
tessdata_fast elsewhere) only with at least a third fewer errors and at least 0.5 points fewer. "Without tilt" leaves
out the turned lines, which are rare on a phone screen. CER %:

| lang | fewest errors, clean + hard | base | recommended | without tilt: fewest | recommended | lens |
|---|---|---|---|---|---|---|
| ja | paddle v6 0.2, v6 + v5 det 0.5 | mlkit 3.1 | paddle v6 | paddle v6 0.1, v6 + v5 det 0.4 | paddle v6 | 0.4 |
| javert | mlkit 6.8, v6 + v5 det 7.6 | mlkit 6.8 | mlkit | mlkit 6.8, v6 + v5 det 7.6 | mlkit | 0.2 |
| zh | paddle 0.1, paddle v6 0.1 | mlkit 4.3 | paddle v6 | paddle v6 0.1, paddle 0.2 | paddle v6 | 0.0 |
| zht | paddle v6 1.7, v6 + v5 det 2.6 | mlkit 4.7 | paddle v6 | paddle v6 1.9, v6 + v5 det 2.8 | paddle v6 | 0.1 |
| ko | mlkit 4.2, paddle 4.6 | mlkit 4.2 | mlkit | mlkit 3.2, paddle 4.8 | mlkit | 0.2 |
| en | paddle 0.2, paddle v6 0.2 | mlkit 0.8 | paddle | paddle 0.2, paddle v6 0.2 | mlkit | 0.0 |
| de | paddle v6 0.2, paddle 0.4 | mlkit 1.2 | paddle v6 | paddle v6 0.2, paddle 0.4 | paddle v6 | 0.0 |
| fr | paddle v6 0.2, paddle 0.4 | mlkit 1.4 | paddle v6 | paddle v6 0.1, paddle 0.3 | paddle v6 | 0.3 |
| es | paddle 0.4, paddle v6 0.5 | mlkit 1.1 | paddle | paddle 0.3, paddle v6 0.5 | paddle | 0.0 |
| it | paddle v6 0.1, paddle 0.2 | mlkit 1.2 | paddle v6 | paddle v6 0.1, paddle 0.2 | paddle v6 | 0.0 |
| pt | paddle 0.1, paddle v6 0.2 | mlkit 1.0 | paddle | paddle 0.1, paddle v6 0.2 | paddle | 0.0 |
| pl | paddle v6 0.9, paddle 1.0 | mlkit 3.4 | paddle v6 | paddle v6 0.7, paddle 0.8 | paddle v6 | 0.1 |
| tr | paddle 1.1, paddle v6 1.3 | mlkit 2.6 | paddle | paddle 1.0, paddle v6 1.0 | paddle | 0.0 |
| lv | paddle v6 0.8, paddle 1.2 | mlkit 2.9 | paddle v6 | paddle v6 0.7, paddle 1.2 | paddle v6 | 0.0 |
| vi | mlkit-latin 2.2, mlkit 4.9 | mlkit 4.9 | mlkit-latin | mlkit-latin 2.3, mlkit 4.9 | mlkit-latin | 0.0 |
| cs | paddle v6 1.4, paddle 1.6 | mlkit 4.3 | paddle v6 | paddle v6 1.2, paddle 1.4 | paddle v6 | 0.1 |
| sh | paddle v6 0.3, paddle 0.6 | mlkit 1.7 | paddle v6 | paddle v6 0.3, paddle 0.6 | paddle v6 | 0.0 |
| ru | paddle cyrillic 1.9, paddle 2.3 | tess fast 16.1 | paddle cyrillic | paddle cyrillic 2.1, paddle 2.5 | paddle cyrillic | 0.0 |
| uk | paddle cyrillic 3.4, paddle 4.4 | tess fast 19.1 | paddle cyrillic | paddle cyrillic 3.3, paddle 4.4 | paddle cyrillic | 0.0 |
| kk | paddle 2.2, tess best 22.8 | tess fast 24.0 | paddle | paddle 2.1, tess best 16.5 | paddle | 0.0 |
| shc | paddle 2.8, tess best 21.0 | tess fast 22.1 | paddle | paddle 3.0, tess best 16.1 | paddle | 0.1 |
| el | paddle 1.4, tess fast 25.4 | tess fast 25.4 | paddle | paddle 1.7, tess best 23.5 | paddle | 0.0 |
| ar | paddle 3.1, tess best 36.9 | tess fast 38.5 | paddle | paddle 3.3, tess best 31.4 | paddle | 0.5 |
| fa | paddle 4.0, tess best 40.6 | tess fast 40.9 | paddle | paddle 4.3, tess fast 41.0 | paddle | 1.0 |
| he | tess best 27.2, tess fast 30.2 | tess fast 30.2 | tess fast | tess best 24.6, tess fast 26.9 | tess fast | 3.2 |
| hi | mlkit 1.8, paddle 2.7 | tess fast 24.8 | mlkit | mlkit 1.5, paddle 2.9 | mlkit | 0.0 |
| bn | tess best 27.2, tess fast 34.7 | tess fast 34.7 | tess fast | tess best 22.1, tess fast 28.7 | tess fast | 0.0 |
| ka | tess best 22.8, tess fast 22.9 | tess fast 22.9 | tess fast | tess fast 17.8, tess best 17.8 | tess fast | 0.3 |
| hy | tess best 26.9, tess fast 28.1 | tess fast 28.1 | tess fast | tess best 24.5, tess fast 25.9 | tess fast | 0.0 |
| th | paddle 1.3, tess fast 20.8 | tess fast 20.8 | paddle | paddle 1.4, tess fast 20.7 | paddle | 0.0 |
| lo | tess best 37.3, tess fast 37.6 | tess fast 37.6 | tess fast | tess fast 34.0, tess best 34.7 | tess fast | 3.6 |
| km | tess best 37.9, tess fast 38.3 | tess fast 38.3 | tess fast | tess best 35.3, tess fast 35.5 | tess fast | 0.1 |

- Paddle takes ja, zh, zht and every Latin language but Vietnamese: v6 in ja, zh, zht, de, fr, it, pl, lv, cs and
  sh, v5 in en, es, pt and tr; the Cyrillic model in ru and uk; v5's script models in kk, shc, el, ar, fa and th.
  ML Kit stays in vertical Japanese and Korean, its Latin model replaces the app's path in vi, its Devanagari model
  takes hi; tessdata_fast stays in he, bn, ka, hy, lo and km.
- Without the turned lines only English changes: ML Kit stays (0.6 against Paddle's 0.2, under the 0.5 points).
- v5 against v6, clean + hard: v6 is lower in CJK (ja 0.2 / 0.7, vertical Japanese 8.1 / 9.5, zht 1.7 / 3.5; zh
  0.1 both) and by 0.1–0.4 points in the other Latin languages it takes (vi 13.5 / 14.3); v5 is lower by 0.1–0.2 in
  es, pt and tr, and ties in en (0.2). On photos and comic pages v6 is lower in most languages (below); it costs more.

### Real screenshots

Phrases instead of full ground truth: for each phrase, the edits to the closest stretch of what the engine read
(spaces, punctuation and symbols removed, NFKC, case folded); extra text is not counted; a phrase counts as missed when
more than half of it is wrong (`testdata/ocr/compare/scripts/real.py`). One run per screen. CER % (missed / phrases):

| lang (screens) | mlkit | paddle | paddle v6 | v6 + v5 det | tess fast | tess best | lens |
|---|---|---|---|---|---|---|---|
| ja (3) | 0.9 (0/58) | 1.4 (0/58) | 0.9 (0/58) | 1.3 (0/58) | 15.7 (9/58) | 21.1 (15/58) | 0.2 (0/58) |
| javert (2) | 10.2 (0/42) | 19.3 (4/42) | 1.5 (0/42) | 1.5 (0/42) | 34.3 (21/42) | 35.4 (22/42) | 0.0 (0/42) |
| ko (1) | 13.7 (0/5) | 13.7 (0/5) |  |  | 100.0 (5/5) | 76.5 (4/5) | 0.0 (0/5) |
| zht (1) | 6.1 (0/5) | 0.0 (0/5) | 0.0 (0/5) | 0.0 (0/5) | 93.9 (5/5) | 97.0 (5/5) | 0.0 (0/5) |

Paddle v5's 19.3% in vertical Japanese is the reader app's page (36.2%; v6 2.4%). ML Kit and Paddle both miss the
hanja of the Korean screenshot; Lens reads them.

### Photos and comic pages

112 pictures from Wikimedia Commons under free licenses (source pages and licenses in
`testdata/ocr/compare/wild/sources.json`), 32 language variants, 1–7 each: shop and street signs, road signs,
plaques, carved and painted lettering, chalkboards, menus, neon, Fraktur, comic and manga pages with sound effects.
Each is scaled onto a black 1280×2856 screen, as a gallery app shows it. Pictures with political or adult content were
left out. Scored like the real screenshots; CER % (missed / phrases). tessdata_best differs from fast by up to 10
points (Arabic 72.7 against 82.7) and misses as many phrases.

| lang (screens) | mlkit | mlkit-latin | paddle | paddle v6 | v6 + v5 det | tess fast | lens |
|---|---|---|---|---|---|---|---|
| ar (5) |  |  | 25.5 (1/17) |  |  | 82.7 (16/17) | 4.1 (0/17) |
| bn (4) |  |  |  |  |  | 62.8 (6/10) | 0.0 (0/10) |
| cs (1) | 42.8 (6/13) | 30.2 (2/13) | 13.5 (0/13) | 13.5 (0/13) | 15.3 (0/13) | 24.7 (1/13) | 5.6 (0/13) |
| de (3) | 24.2 (7/62) | 21.7 (5/62) | 14.7 (2/62) | 10.4 (1/62) | 12.9 (2/62) | 23.8 (8/62) | 1.2 (0/62) |
| el (4) |  |  | 11.6 (2/15) |  |  | 76.3 (11/15) | 2.5 (1/15) |
| en (3) | 39.7 (14/50) | 31.5 (10/50) | 12.8 (0/50) | 12.2 (0/50) | 11.0 (0/50) | 74.7 (48/50) | 4.6 (0/50) |
| es (3) | 26.4 (4/17) | 17.2 (1/17) | 8.6 (1/17) | 2.9 (0/17) | 8.0 (1/17) | 79.3 (15/17) | 2.3 (0/17) |
| fa (4) |  |  | 9.3 (1/14) |  |  | 99.4 (14/14) | 1.9 (1/14) |
| fr (3) | 52.8 (33/73) | 36.2 (18/73) | 19.4 (7/73) | 14.1 (1/73) | 14.7 (1/73) | 57.7 (33/73) | 10.6 (1/73) |
| he (4) |  |  |  |  |  | 75.7 (18/20) | 0.7 (0/20) |
| hi (4) | 2.2 (0/6) |  | 36.6 (0/6) |  |  | 100.0 (6/6) | 0.0 (0/6) |
| hy (4) |  |  |  |  |  | 60.0 (3/6) | 0.0 (0/6) |
| it (2) | 52.0 (9/13) | 47.4 (7/13) | 20.4 (0/13) | 13.2 (0/13) | 14.5 (0/13) | 82.9 (13/13) | 16.4 (1/13) |
| ja (7) | 45.1 (18/32) |  | 22.5 (7/32) | 16.4 (6/32) | 16.4 (7/32) | 93.9 (30/32) | 11.3 (6/32) |
| javert (3) | 90.4 (18/19) |  | 26.0 (4/19) | 17.3 (2/19) | 14.4 (1/19) | 92.3 (19/19) | 1.0 (0/19) |
| ka (4) |  |  |  |  |  | 63.3 (6/8) | 29.7 (3/8) |
| kk (3) |  |  | 17.4 (0/14) |  |  | 28.4 (5/14) | 6.3 (1/14) |
| km (3) |  |  |  |  |  | 100.0 (6/6) | 0.0 (0/6) |
| ko (3) | 36.3 (13/35) |  | 5.9 (0/35) |  |  | 100.0 (35/35) | 25.6 (6/35) |
| lo (4) |  |  |  |  |  | 51.0 (4/8) | 1.0 (0/8) |
| lv (5) | 5.2 (0/17) | 5.2 (0/17) | 5.5 (0/17) | 4.9 (0/17) | 6.7 (0/17) | 62.4 (10/17) | 1.8 (0/17) |
| pl (3) | 17.5 (6/30) | 9.8 (0/30) | 3.1 (0/30) | 2.4 (0/30) | 2.6 (0/30) | 82.8 (23/30) | 1.7 (0/30) |
| pt (3) | 43.6 (10/17) | 33.1 (7/17) | 15.3 (1/17) | 8.6 (1/17) | 11.7 (1/17) | 100.0 (17/17) | 10.4 (0/17) |
| ru (4) |  |  | 15.2 (1/12) |  |  | 63.7 (10/12) | 1.2 (0/12) |
| sh (3) | 2.4 (1/12) | 0.0 (0/12) | 0.0 (0/12) | 1.6 (0/12) | 0.0 (0/12) | 21.4 (3/12) | 0.0 (0/12) |
| shc (4) |  |  | 9.7 (1/10) |  |  | 100.0 (10/10) | 3.7 (1/10) |
| th (4) |  |  | 8.5 (2/10) |  |  | 25.5 (5/10) | 1.6 (0/10) |
| tr (3) | 8.6 (1/16) | 7.5 (0/16) | 20.4 (1/16) | 8.1 (0/16) | 21.0 (1/16) | 100.0 (16/16) | 3.2 (1/16) |
| uk (2) |  |  | 39.1 (2/7) |  |  | 87.5 (7/7) | 35.9 (3/7) |
| vi (3) | 31.7 (7/19) | 26.2 (6/19) | 21.7 (1/19) | 22.1 (1/19) | 17.1 (1/19) | 96.2 (19/19) | 17.1 (6/19) |
| zh (3) | 7.4 (1/42) |  | 1.5 (0/42) | 1.5 (1/42) | 1.2 (0/42) | 91.7 (38/42) | 3.6 (1/42) |
| zht (4) | 49.0 (13/33) |  | 13.1 (0/33) | 4.8 (0/33) | 6.9 (0/33) | 96.6 (33/33) | 3.4 (0/33) |

Findings on photos and comic pages:

- Lens has the lowest CER in most languages. Paddle is lower in Korean (5.9% against 25.6%; 3.4% against 38.5% on a
  café menu), Chinese (1.2–1.5% / 3.6%), Italian (v6 13.2% / 16.4%) and Portuguese (v6 8.6% / 10.4%); they tie in
  Vietnamese (17.1%, v6 + v5 det) and Serbo-Croatian (0.0%). Lens's weak results are Georgian (29.7%: a stylized name
  comes back in Armenian letters) and Ukrainian (35.9% over two signs; of one it returns a single word). Neither it
  nor any other engine reads the two manga sound effects (100% each).
- Pooled by characters over the languages PP-OCRv6 reads: CJK (799 characters) ML Kit 35.8%, Paddle v5 12.4%, v6
  8.1%, v6 + v5 det 8.0%, Lens 5.3%; Latin (4461 characters) ML Kit 33.4%, its Latin model 25.0%, Paddle v5 14.0%,
  v6 10.6%, v6 + v5 det 11.8%, Lens 6.2%. Paddle beats ML Kit in every CJK and Latin language except Turkish (ML
  Kit's Latin model 7.5% against v6's 8.1% and v5's 20.4%: v5 misreads a shop window, 24.3% against v6's 4.9%) and
  Latvian, where they are even (5.2% against 4.9–6.7%); in Serbo-Croatian both read 0.0%. ML Kit's Devanagari model
  beats Paddle in Hindi (2.2% / 36.6%).
- Tesseract is at 60–100% in 25 of the 32 language variants and misses most phrases in them; its lowest are
  Serbo-Croatian 21.4%, German 23.8%, Czech 24.7% and Thai 25.5%.

### PP-OCRv6 small

`PaddlePaddle/PP-OCRv6_small_det_onnx` and `PP-OCRv6_small_rec_onnx` on Hugging Face (Apache-2.0, 2026-06-18),
converted with the same MNN converter as v5. Bytes, ONNX → MNN: detector 9,880,512 → 9,848,360, recognizer 21,159,378
→ 21,112,692; v5 mobile: detector 4,744,612, main recognizer 16,519,712, Latin 8,026,964. One recognizer for
Chinese, Japanese and Latin script (18,708 characters, against 18,383 in v5's main model): it adds ơ ư ś ź ő ű ļ ņ ķ
ģ, but like v5's Latin model it lacks the Vietnamese letters with two marks, and it has no Cyrillic, Hangul, Arabic or
Thai.

Pipeline: the recognizer takes the same 3×48×320 input with the same normalization and CTC decoding. The detector
expects other post-processing: pixel threshold 0.2, line threshold 0.45, unclip ratio 1.4 (v5: 0.3, 0.6, 1.5).
`core:ocr`'s `TextDetection` has these as constants, so the harness maps the detector's output linearly
(`RescaledDetector`, p → 1.2p + 0.06) and keeps unclip at 1.5 (boxes a little larger). In the app this would become
per-model thresholds.

CER %, v6 against v5:

- clean: CJK 2.3 against 2.8, Latin 1.2 against 1.2 (Vietnamese 12.7 / 13.4, the rest 0.2 / 0.2);
- hard: CJK 0.5 against 1.7, Latin 1.6 against 1.8 (without Vietnamese 0.8 / 0.9); per style photo 1.1 / 3.6,
  gradient 1.0 / 3.8, handwriting 2.1 / 3.9, subtitles 4.3 / 5.6, manga page 3.9 / 8.6, turned lines 0.9–2.7 /
  0.9–3.0;
- real: vertical Japanese 1.5 against 19.3, the rest 0.0–0.9 against 0.0–1.4;
- photos and comic pages: CJK 8.1 against 12.4, Latin 10.6 against 14.0.

v6's recognizer after v5's detector stays within 1.6 points of v6 alone per language on screens (clean and hard), but
reads gradients worse (3.5 against 1.0); on photos it goes both ways (Turkish 21.0 against 8.1, Vietnamese 17.1
against 22.1; pooled 8.0 / 8.1 in CJK and 11.8 / 10.6 in Latin). It saves 4.9 MB (v6's detector against v5's). Time
on the emulator, before the rotated crop: CJK 1.3–1.5 s against v5's 1.1–1.5 s, Latin 1.2–1.6 s against 0.8–1.0 s
(the larger recognizer replaces v5's small Latin one).

Sizes (arm64 APK; armeabi-v7a, x86_64): Tesseract4Android 8.3 MB (6.2, 8.8), MNN 2.1 MB (1.3, 2.6), ONNX Runtime
19.4 MB (14.0, 23.2). Downloads per language, tessdata_fast / best in MB: rus 3.7/14.6, ukr 3.6/10.4, kaz 4.5/7.2,
srp 2.1/8.9, ell 1.4/8.5, tha 1.0/7.3, ara 1.4/12.0, fas 0.4/3.2, heb 0.9/3.5, hin 1.1/11.3, ben 0.8/10.5, kat
2.4/4.3, hye 3.3/6.1, pol 4.5/11.4, tur 4.3/7.1, lav 2.6/5.4, vie 0.5/11.9, ces 3.6/10.4, hrv 3.9/10.7, lao 6.1/12.9,
khm 1.4/7.7. Paddle v5: detector 4.5 MB once plus one recognizer per script, 7.4–7.9 MB (cyrillic, eslav, el, th,
arabic, devanagari, latin), 12.8 korean, 15.8 main. Paddle v6 small: detector 9.4 MB, recognizer 20.1 MB.

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
