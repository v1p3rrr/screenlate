# Architecture

## Modules

```
app ──────────────┬──> overlay ──┬──> core:ocr ──> core:common
                  │              ├──> core:anki ─> core:common
                  │              └──> dictionary:api ──> core:common
                  ├──> dictionary:engine-hoshidicts ──> dictionary:api
                  └──> dictionary:render-yomitan (assets only)
```

| Module | Contents |
|---|---|
| `app` | Application (Hilt, WorkManager factory), Compose screens: home with setup warnings, search, one Settings screen leading to bubble, lookup, dictionaries, Anki and audio, appearance (theme, language, popup font and CSS), Yomitan import and About; OCR test. `ProcessTextActivity` for the text selection menu. |
| `overlay` | Accessibility service, bubble state machine (`OverlayController`), window screenshots, app text from the accessibility tree, the lookup page (`web/LookupPage`, `assets/popup/`), popup placement, crop editor, Anki buttons (`anki/PopupNotes`), page fonts and custom CSS (`fonts/`), Quick Settings tile. |
| `core:common` | `Language` and `LanguageSupport` (everything language-specific: lookup start, spelling variants, romaji, sentence rules, fonts, audio defaults), `MappedText`, geometry, app settings, shared OkHttp client, log redaction. |
| `core:ocr` | `OcrEngine` with a cloud engine (hand-written protobuf) and ML Kit, `CompositeOcr` (draft, then final), `TextLayout` for hit testing. |
| `core:anki` | AnkiDroid API wrapper behind `AnkiBackend`, note settings, field templates, sentence extraction, setup status, audio sources and playback. |
| `dictionary:api` | `DictionaryEngine` interface and lookup models, registry (Room), import queue (WorkManager), bundled dictionaries, download catalog, update check, `YomitanSorter`, Yomitan collection export converter. |
| `dictionary:engine-hoshidicts` | `DictionaryEngine` on top of hoshidicts (C++, JNI). GPL-3.0. |
| `dictionary:render-yomitan` | JavaScript and CSS that render Yomitan structured content, images, furigana and pitch accent. GPL-3.0. |

The two GPL modules implement interfaces or asset contracts defined elsewhere, so they can be replaced without touching the rest.

## From the bubble to a dictionary entry

1. **Scan.** Pulling the bubble out of the dock (or tapping it) takes a screenshot of the app window under the overlays (`takeScreenshotOfWindow`, API 34+). With the "app text" source, the accessibility tree is read first and OCR is skipped if it yields text with character positions.
2. **OCR.** `CompositeOcr` runs ML Kit on the device and the cloud engine in parallel. ML Kit's result arrives first and is shown as a draft; the cloud result replaces it. The cloud engine gets a JPEG of at most 1500 px; its coordinates are normalized to the image and mapped back to the screen.
3. **Hit test.** `TextLayout` splits every recognized word into characters (per-character boxes when the engine has them, otherwise even splits along the reading direction) and finds the character under the aim point. The text from that character to the end of its paragraph, up to 16 characters, is the lookup text.
4. **Lookup.** `DictionaryLookup` loads the enabled dictionaries into the engine on first use. hoshidicts deinflects every prefix of the lookup text, groups results by expression and reading, and sorts them; `YomitanSorter` applies Yomitan's full order including dictionary priority. The language's spelling variants (`LanguageSupport.spellingVariants`) and, when enabled, romaji converted to kana are looked up as well; `MappedText` maps their matched lengths back to the screen text.
5. **Render.** The overlay sends the results as JSON to the lookup page in a pre-warmed WebView. The page builds the cards; glossaries go through `YomitanRender` (structured content, images served from the engine, each dictionary's `styles.css` scoped to its own entries). `overlay/fonts` supplies the page's `lang`, the phone's font for the language declared by its local name and limited to the script, downloaded or added fonts, and the user's CSS, applied last with the page's font list appended to each `font-family`.
6. **Highlight and placement.** The matched length of the first result decides how many characters are highlighted. The popup goes to the side of the word with more space and never covers the bubble.

Aiming again only repeats steps 3–6; the screen is not scanned again until the bubble goes back to the dock or is tapped.

## Anki export

The page builds the dictionary markers of an entry (glossary HTML with scoped CSS, furigana, pitch, frequencies) because it already has the renderer. `PopupNotes` adds the sentence and cloze markers from the OCR paragraph, the audio clip and, on a long press, the cropped screenshot; `AnkiNotes` renders the field templates, handles duplicates and talks to AnkiDroid through its content provider. Media files go to AnkiDroid through a FileProvider.

## Dictionaries

`DictionaryRepository` is the only writer of the registry. Imports (bundled archives, files, downloads, Yomitan collection exports) run one at a time as a foreground WorkManager job: the archive is converted into a staging directory, moved into `files/dictionaries/<uuid>/`, registered, and the engine is reloaded. A dictionary with the same title, or the one an update was started for, is replaced in place.

A Yomitan collection export is one JSON file with the rows of all dictionaries, table by table. `YomitanBackup` reads it twice as UTF-8 bytes without decoding row values: first to measure the chosen dictionaries (`measure`), then to copy their rows into one Yomitan archive per dictionary (`convert`). `CollectionSpace` turns the measurement into the peak storage of the import (all archives exist before the first one is imported, each is deleted after its dictionary is installed) and decides whether the archives are stored uncompressed (fastest), deflated (when only that fits), or not written at all (the task fails with the needed and free space).

## Languages

Language is a parameter of OCR, lookup and rendering (`core.common.Language`). Whatever differs between languages lives behind `LanguageSupport`; only Japanese is implemented so far.

## Privacy

Logs never contain recognized text, looked-up words, note contents or URLs with terms: exceptions are logged through `redacted()`, which keeps the type and stack but drops messages, and URLs through `redactUrl()`.

## Tests

- JVM unit tests next to each module: OCR protocol and layout, `CompositeOcr` with fake engines, sorting, spelling variants and romaji, collection conversion, notes with a fake `AnkiBackend`, audio sources against MockWebServer, fonts and the CSS checker.
- Page scripts: `scripts/page-tests` runs `note.js`, `anki.js` and `popup.js` in jsdom under `node:test`.
- Instrumented tests: the engine with small dictionaries and the whole lookup pipeline, the dictionary registry, the lookup page in a WebView, and an AnkiDroid round trip that skips itself without AnkiDroid.

## Threading

- The overlay runs on the main thread of the accessibility service; OCR, lookups and file work suspend on IO or Default dispatchers.
- The hoshidicts session is used from one coroutine at a time (a mutex in `HoshidictsEngine`); imports do not touch the session and run in parallel with lookups.
- The lookup page talks to Kotlin through `@JavascriptInterface` methods, which post to the main thread.
