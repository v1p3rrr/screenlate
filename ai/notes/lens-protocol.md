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
          10 translation: TranslationData — only with the translate filter, see "Translation" below

Geometry
  1 bounding_box: CenterRotatedBox
      1 center_x: float   (0..1, relative to image width)
      2 center_y: float   (0..1, relative to image height)
      3 width: float
      4 height: float
      5 rotation_z: float (radians)
```

Line text = words joined with their separators. For Japanese, whitespace is stripped. Vertical lines: `|rotation_z| ≈ π/2`, or height > width of the axis-aligned box.

## Measurements (2026-09-26, from a desktop connection)

| Image | Sent | JPEG size | Latency | Result |
|---|---|---|---|---|
| Synthetic 1344×2992, 48/26/18 px glyphs + vertical column | 674×1500 | 45 KB | 0.66 s | All lines correct, including 18 px text (9 px after downscale) |
| Same | full 1344×2992 | 133 KB | 0.90 s | Identical to the downscaled result |
| Same, PNG | 674×1500 | 76 KB | 0.56 s | Identical |
| Manga page 1351×1920 (`testdata/ocr/manga-page.webp`) | 1055×1500 | 388 KB (screentones compress poorly) | 0.96 s | All vertical balloons correct; rotated "Win or Lose" returned with rotation π/2 |
| X thread 377×948 (`testdata/ocr/x-thread.png`) | as is | 70 KB | 0.62 s | All text correct |

Conclusion: downscaling to 1500 px costs nothing in quality; full resolution is accepted if ever needed. Words are morphological segments (e.g. `吾輩は猫である`, `。`, `名前`), separators are empty for Japanese. Vertical lines come with rotation ≈ 0 and a tall box; 90°-rotated horizontal text comes with rotation ≈ ±π/2.

## Paragraph grouping (2026-09-28, `scratchpad` probe of a synthetic 1280×2856 page with 64 px text)

- Horizontal lines of one wrapped paragraph come as one paragraph at 1.25× and at 2.3× line pitch, in reading order.
  Separate horizontal paragraphs are different things: a post's header and its text, a score table's labels.
- Vertical columns come as one paragraph at 1.3× column pitch, but listed **left to right** (the column read second
  comes first). At 2.2× pitch every column is its own paragraph. On the manga page the columns of one balloon came as
  four paragraphs (`商品のクオリティは高く / 上位商品は必ず手に / 入れたい代物が多い`, with 手に|入れたい broken
  across columns).
- `ReadingOrder` (core:ocr) sorts vertical columns right to left and joins a vertical paragraph whose last column runs
  to the bottom with the one starting right after it (same top margin and width, gap ≤ 1.5 columns). Horizontal
  paragraphs are left as they are.

## Translation (probe 2026-10-08, `testdata/ocr/x-thread.png`, from a desktop connection)

The same `crupload` request translates when the client context carries a translate filter (field numbers from
Chromium's `third_party/lens_server_proto/lens_overlay_filters.proto` and `lens_overlay_deep_gleam_data.proto`):

```
LensOverlayClientContext
  17 client_filters: AppliedFilters
      1 filter (repeated): AppliedFilter
          1 filter_type: enum = 2 (TRANSLATE)
          3 translate
              1 target_language: string ("ru")
              2 source_language: string ("auto")
```

- The answer keeps the same OCR text and adds `objects_response.4 deep_gleams`, one per OCR paragraph in the same
  order (49 paragraphs, 49 gleams). Without the filter there are no deep gleams at all.
- `TranslationData`: 1 status{1 code}, 2 target_language, 3 source_language (detected per paragraph), 4 translation,
  5 line (repeated: 1 start, 2 end offsets into the translation, per OCR line). Status codes: 0 UNKNOWN, 1 SUCCESS,
  2 SERVER_ERROR, 3 UNSUPPORTED_LANGUAGE_PAIR, 4 SAME_LANGUAGE, 5 UNKNOWN_SOURCE_LANGUAGE (numbers such as "840"),
  6 INVALID_REQUEST, 7 DEADLINE_EXCEEDED, 8 EMPTY_TRANSLATION, 9 NO_OP_TRANSLATION (handles such as "@user").
- Cost: 22.8 KB answer instead of 12.7 KB, 1.03 s against 1.13 s without the filter; no rendered background images
  came back.
- The unit is Lens's paragraph, not a sentence: a lone fragment translates badly (っぽい → "выход"), and manga
  balloons come as several column paragraphs (see "Paragraph grouping"), so their translation would be split too.
- Quality ja→ru was clearly better than the keyless web endpoint below, e.g. 確かにそう言われると面影を感じるかもですね
  → "Теперь, когда вы об этом упомянули, я вижу сходство" (web endpoint: "Это правда, когда ты говоришь это, ты можешь
  почувствовать следы его лица"). Which model Lens uses is not visible in the answer.

For comparison, the keyless web endpoint `https://translate.googleapis.com/translate_a/single?client=gtx&sl=ja&tl=ru&dt=t&q=...`
answers JSON arrays in 1.3–2.4 s per sentence and names its models: ja→ru went through `ja_en_2023q1` and
`en_ru_2023q1`, i.e. through English.
