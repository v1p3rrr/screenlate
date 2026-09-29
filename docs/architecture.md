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
| `app` | Application (Hilt, WorkManager factory), Compose screens: home with setup warnings, search, one Settings screen leading to bubble, lookup, dictionaries, Anki and audio, appearance (theme, e-ink mode, language, popup font and CSS), Yomitan import, backup and restore (`backup/`), background work and About; OCR test. `ProcessTextActivity` for the text selection menu. |
| `overlay` | Accessibility service, bubble state machine (`OverlayController`), window screenshots, app text from the accessibility tree, the lookup page (`web/LookupPage`, `assets/popup/`), popup placement, crop editor, Anki buttons (`anki/PopupNotes`), page fonts and custom CSS (`fonts/`), Quick Settings tile. |
| `core:common` | `Language` and `LanguageSupport` (everything language-specific: lookup start, spelling variants, romaji, sentence rules, fonts, audio defaults), `MappedText`, geometry, app settings, shared OkHttp client, local network address checks, log redaction. |
| `core:ocr` | `OcrEngine` with a cloud engine (hand-written protobuf) and ML Kit, `CompositeOcr` (draft, then final), `TextLayout` for hit testing. |
| `core:anki` | AnkiDroid API wrapper behind `AnkiBackend`, note settings, field templates, sentence extraction, setup status, audio sources and playback. |
| `dictionary:api` | `DictionaryEngine` interface and lookup models, registry (Room), import queue (WorkManager), bundled dictionaries, download catalog, update check, `YomitanSorter`, Yomitan collection export converter. |
| `dictionary:engine-hoshidicts` | `DictionaryEngine` on top of hoshidicts (C++, JNI). GPL-3.0. |
| `dictionary:render-yomitan` | JavaScript and CSS that render Yomitan structured content, images, furigana and pitch accent. GPL-3.0. |

The two GPL modules implement interfaces or asset contracts defined elsewhere, so they can be replaced without touching the rest.

## From the bubble to a dictionary entry

1. **Scan.** Pulling the bubble out of the dock (or tapping it) takes a screenshot of the app window under the overlays (`takeScreenshotOfWindow`, API 34+; older versions capture the display with the overlays hidden for a frame), so the popup is never recognized and the text under it is. With "Read app text", `AccessibilityText` reads the application windows' accessibility tree while the screen is captured, and OCR runs as well. Neither waits for the other: whichever is ready is shown, and app text takes precedence where both have text, with OCR adding only the lines app text lacks. "App text only" skips the screenshot and OCR (the screenshot is taken later only when a note asks for a picture). Nodes whose character boxes cannot be trusted are left to OCR: the same box for every character (a WebView reporting the node's bounds) and text fields whose first box sits at the field's corner (Compose fields, whose boxes include the padding).
2. **OCR.** `CompositeOcr` runs ML Kit on the device and the cloud engine in parallel. ML Kit first reads a band of the screen around the aim (`FocusBand`, again when the aim has moved out of it), then the whole screen; each result is shown as a draft until the cloud result replaces it. ML Kit's time grows with the amount of text, so the band is ready several times sooner than the whole screen. The model is loaded when the bubble starts. The cloud engine gets a JPEG of at most 1500 px; its coordinates are normalized to the image and mapped back to the screen. `OcrOptions` can limit a scan to one engine or defer ML Kit's whole-screen pass until the cloud engine is 3 s late or fails (`deferWholeImage`). ML Kit's symbol boxes sometimes lag behind the glyphs by up to half a character; `SymbolLag` moves the inner boundaries of such words onto the gaps between glyphs when the pixels clearly show them.
3. **Hit test.** `TextLayout` splits every recognized word into characters (per-character boxes when the engine has them, otherwise even splits along the reading direction) and finds the character under the aim point. The text from that character to the end of its paragraph, up to 16 characters, is the lookup text. `ReadingOrder` puts paragraphs into reading order first: vertical columns right to left, and a column that runs to the bottom of its text joined with the column that goes on from it, so a word broken at a line or column end is read as a whole.
4. **Lookup.** `DictionaryLookup` loads the enabled dictionaries into the engine on first use. hoshidicts deinflects every prefix of the lookup text, groups results by expression and reading, and sorts them; `YomitanSorter` applies Yomitan's full order including dictionary priority. The language's spelling variants (`LanguageSupport.spellingVariants`) and, when enabled, romaji converted to kana are looked up as well; `MappedText` maps their matched lengths back to the screen text.
5. **Render.** The overlay sends the results as JSON to the lookup page in a pre-warmed WebView. The page builds the cards; glossaries go through `YomitanRender` (structured content, images served from the engine, each dictionary's `styles.css` scoped to its own entries). `overlay/fonts` supplies the page's `lang`, the phone's font for the language declared by its local name and limited to the script, downloaded or added fonts, and the user's CSS, applied last with the page's font list appended to each `font-family`.
6. **Highlight and placement.** The matched length of the first result decides how many characters are highlighted. `PopupPlacement` puts the popup above or below horizontal text and beside vertical text (on the side with more space), trying a narrower popup beside a middle column of a narrow screen before falling back to above or below; it never covers the word or the bubble.

Aiming again only repeats steps 3–6; the screen is not scanned again until the bubble goes back to the dock or is tapped.

## Anki export

The page builds the dictionary markers of an entry (glossary HTML with scoped CSS, furigana, pitch, frequencies) because it already has the renderer. `PopupNotes` adds the sentence and cloze markers from the OCR paragraph, the audio clip and, on a long press, the cropped screenshot; `AnkiNotes` renders the field templates, handles duplicates and talks to AnkiDroid through its content provider. Media files go to AnkiDroid through a FileProvider.

## Dictionaries

`DictionaryRepository` is the only writer of the registry. Imports (bundled archives, files, downloads, Yomitan collection exports) run one at a time as a foreground WorkManager job: the archive is converted into a staging directory, moved into `files/dictionaries/<uuid>/`, registered, and the engine is reloaded. A dictionary with the same title, or the one an update was started for, is replaced in place.

A Yomitan collection export is one JSON file with the rows of all dictionaries, table by table. `YomitanBackup` reads it twice as UTF-8 bytes without decoding row values: first to measure the chosen dictionaries (`measure`), then to copy their rows into one Yomitan archive per dictionary (`convert`). `CollectionSpace` turns the measurement into the peak storage of the import (all archives exist before the first one is imported, each is deleted after its dictionary is installed) and decides whether the archives are stored uncompressed (fastest), deflated (when only that fits), or not written at all (the task fails with the needed and free space).

## Backup and restore

`app/backup` writes one zip: a manifest (format version, app version, sections) first, then the preferences as typed JSON grouped by section (`BackupPreferences`: key prefixes decide the section; internal state such as one-time hints is left out), the dictionary list (registry entries without ids, matched by title on restore), the popup fonts, and optionally each dictionary's converted directory. Restoring reads the zip as a stream: checked preference sections are replaced in one DataStore edit, fonts and dictionaries are staged and handed to `PopupFonts.restore` and `DictionaryRepository.restore` (a dictionary with the same title is replaced in place and keeps its id, switch and priority), and the dictionary list is applied last to whatever is installed. `BackupManager` is a singleton with its own scope, so the work goes on when the screen is left.

## Updates and releases

`update/AppUpdates` reads the latest stable GitHub release, picks the APK for the device's ABI, checks that it is a newer Screenlate signed with the same certificates and installs it through `PackageInstaller` (`UpdateReceiver` gets the session status). The version comes from git tags: `vX.Y.Z` gives the name X.Y.Z and the code X·10000 + Y·100 + Z (`build-logic`'s `ScreenlateVersion`). CI (`.github/workflows`) builds and tests every push, and a tag builds per-ABI and universal APKs, a source archive with submodules and a changelog for the GitHub Release.

## Languages

Language is a parameter of OCR, lookup and rendering (`core.common.Language`). Whatever differs between languages lives behind `LanguageSupport`; only Japanese is implemented so far.

## Privacy

Logs never contain recognized text, looked-up words, note contents or URLs with terms: exceptions are logged through `redacted()`, which keeps the type and stack but drops messages, and URLs through `redactUrl()`. The app logs only to logcat, the system's fixed-size ring buffer, so nothing piles up on the device; files are written only when the user shares or saves the log. A scan logs its stages with timings (capture, app text, cloud request, on-device bands, draft and final), and events such as docking, note results and audio lookups are logged as outcomes and counts, never per aim movement.

## Tests

- JVM unit tests next to each module: OCR protocol and layout, `CompositeOcr` with fake engines, sorting, spelling variants and romaji, collection conversion, notes with a fake `AnkiBackend`, audio sources against MockWebServer, fonts and the CSS checker, the backup format and archive, popup placement.
- Page scripts: `scripts/page-tests` runs `note.js`, `anki.js` and `popup.js` in jsdom under `node:test`.
- Instrumented tests: the engine with small dictionaries and the whole lookup pipeline, the dictionary registry, the lookup page in a WebView, the text selection menu entry (`ProcessTextTest`), and an AnkiDroid round trip that skips itself without AnkiDroid.

## Threading

- The overlay runs on the main thread of the accessibility service; OCR, lookups and file work suspend on IO or Default dispatchers.
- The hoshidicts session is used from one coroutine at a time (a mutex in `HoshidictsEngine`); imports do not touch the session and run in parallel with lookups.
- The lookup page talks to Kotlin through `@JavascriptInterface` methods, which post to the main thread.
- The popup's WebView renderer runs at a priority the system may lower while the popup is hidden (`setRendererPriorityPolicy(…, waivedWhenNotVisible = true)`); if the renderer is reclaimed, the page is recreated at the next scan. The search screen's embedded page keeps the default priority.
- A cancelled ML Kit task cannot be stopped, so the on-device pass works on its own copy of the screenshot and frees it when the task ends.
