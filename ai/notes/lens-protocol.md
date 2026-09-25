# Google Lens protocol (unofficial)

Ported from `KolbyML/chrome-lens-ocr` (MIT, Rust), which ports `chrome-lens-py`. Endpoint and key are the ones Chrome's Lens overlay uses.

- `POST https://lensfrontend-pa.googleapis.com/v1/crupload`
- Headers: `Content-Type: application/x-protobuf`, `X-Goog-Api-Key: <key from chrome-lens-ocr src/constants.rs>`, desktop Chrome `User-Agent`.
- The reference client downsizes images to 1500 px on the longest side and sends PNG. We send JPEG; whether full resolution is accepted is still to be checked.

## Request

```
LensOverlayServerRequest
  1 objects_request: LensOverlayObjectsRequest
      1 request_context: LensOverlayRequestContext
          3 request_id: LensOverlayRequestId
              1 uuid: uint64 (random)
              2 sequence_id: int32 = 1
              3 image_sequence_id: int32 = 1
          4 client_context: LensOverlayClientContext
              1 platform: enum = 3 (WEB)
              2 surface: enum = 4 (CHROMIUM)
              4 locale_context: LocaleContext
                  1 language: string ("ja")
                  2 region: string ("US")
                  3 time_zone: string ("America/New_York")
      3 image_data: ImageData
          1 payload: ImagePayload
              1 image_bytes: bytes
          3 image_metadata: ImageMetadata
              1 width: int32
              2 height: int32
```

## Response (fields we read)

```
LensOverlayServerResponse
  2 objects_response
      3 text: Text
          1 text_layout: TextLayout
              1 paragraphs (repeated): TextLayoutParagraph
                  2 lines (repeated): TextLayoutLine
                      1 words (repeated): TextLayoutWord
                          2 plain_text: string
                          3 text_separator: string (optional)
                          4 geometry: Geometry
                      2 geometry: Geometry
                  3 geometry: Geometry
          2 content_language: string
      4 deep_gleams (repeated)
          10 translation: TranslationData (1 status{1 code}, 4 translation) — unused

Geometry
  1 bounding_box: CenterRotatedBox
      1 center_x: float   (0..1, relative to image width)
      2 center_y: float   (0..1, relative to image height)
      3 width: float
      4 height: float
      5 rotation_z: float (radians)
```

Line text = words joined with their separators. For Japanese, Manatan strips whitespace. Vertical lines: `|rotation_z| ≈ π/2`, or height > width of the axis-aligned box.

## Measurements (2026-09-26, from a desktop connection)

| Image | Sent | JPEG size | Latency | Result |
|---|---|---|---|---|
| Synthetic 1344×2992, 48/26/18 px glyphs + vertical column | 674×1500 | 45 KB | 0.66 s | All lines correct, including 18 px text (9 px after downscale) |
| Same | full 1344×2992 | 133 KB | 0.90 s | Identical to the downscaled result |
| Same, PNG | 674×1500 | 76 KB | 0.56 s | Identical |
| Manga page 1351×1920 (`testdata/ocr/manga-page.webp`) | 1055×1500 | 388 KB (screentones compress poorly) | 0.96 s | All vertical balloons correct; rotated "Win or Lose" returned with rotation π/2 |
| X thread 377×948 (`testdata/ocr/x-thread.png`) | as is | 70 KB | 0.62 s | All text correct |

Conclusion: downscaling to 1500 px costs nothing in quality; full resolution is accepted if ever needed. Words are morphological segments (e.g. `吾輩は猫である`, `。`, `名前`), separators are empty for Japanese. Vertical lines come with rotation ≈ 0 and a tall box; 90°-rotated horizontal text comes with rotation ≈ ±π/2.
