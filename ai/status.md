# Status

Current plan: [plans/2026-09-26-initial-plan.md](plans/2026-09-26-initial-plan.md).

## Phases

- [x] Phase 0 — infrastructure
- [x] Phase 1 — overlay + OCR
- [x] Phase 2 — dictionaries
- [x] Phase 3 — Anki + audio
- [x] Phase 4 — polish (except the Yomitan settings backup, which needs an interview)

## Log

### 2026-09-26

- Phase 0: separate git repo; convention plugins in `build-logic/`; modules `app`, `overlay`, `core:{common,ocr,anki}`, `dictionary:{api,engine-hoshidicts,render-yomitan}`; Hilt 2.60.1, Room 2.8.5, KSP 2.3.12, Kotlin 2.4.20 on AGP 9.4.1; DataStore theme setting; accessibility service stub; onboarding screen (en/ru); debug/release signing; GPL-3.0 LICENSE, NOTICE, README, CLAUDE.md, `ai/` notes.

- Phase 1: Lens client (hand-written protobuf, JPEG ≤1500 px) and ML Kit draft combined in `CompositeOcr`; `TextLayout` hit testing (horizontal, vertical, rotated lines); accessibility overlay with bubble/dock/aim/highlight layer, window screenshots (API 34+) with display fallback, WebView popup shell with placement logic, Quick Settings tile, OCR debug screen and debug image viewer. Verified on the emulator: undock → Lens → popup on the X screenshot and on vertical manga text, rescan tap with line flash, double tap aim toggle, docking by dragging to the edge, offline ML Kit path, tile toggle. Lens measurements are in `notes/lens-protocol.md`.

- Phase 2: hoshidicts submodule + own JNI (`jni_bridge.cpp`, UTF-8 byte arrays in, JSON out); `DictionaryEngine` in `dictionary:api` with Room registry (`DictionaryRepository`, `DictionaryStorage`), WorkManager import queue (`DictionaryImports`, foreground `dataSync`), bundled Jitendex/Jiten/Kanjium downloaded at build time (`DownloadAssetsTask`) and installed on first launch (~5 s on the emulator), download catalog fetched from the repo (`catalog/dictionaries.json` in `dictionary:api` assets) grouped by language pair; `YomitanSorter` adds dictionary priority; renderer module `render-yomitan` (structured content, images, scoped styles.css, furigana, pitch); popup cards with frequency/pitch/deinflection chips, glossary links with a back stack; dictionaries screen with sections by type and drag reordering. Verified on the emulator: X screenshot, vertical manga text, Kolobok download and priority, link navigation (`scripts/popup-eval.mjs`).

- Phase 3: `core:anki` (AnkiDroid API from JitPack, `AnkiNotes` with duplicate scope/behavior, `FieldTemplate` markers, `Sentence` extraction, `AudioFinder` with JapanesePod101/URL/custom JSON); Anki and audio settings screen; popup ➕ (tap: note, hold: crop editor with the paragraph pre-selected) and 🔊, duplicate marks, auto-play setting; glossary images copied into AnkiDroid media. Verified on the emulator with AnkiDroid 2.24.1: Basic note with glossary (two dictionaries), sentence, JapanesePod101 audio and a cropped screenshot; duplicate mark after adding.

- Phase 4 (most of it): `LookupPage` shared by the popup and the search screen; `ProcessTextActivity`; update check via `indexUrl` with in-place replacement; sort dictionary choice; KANJIDIC in the catalog and kanji entries on tapping a headword kanji; bubble settings (aim, dock side, highlight, haptics, text source, hidden apps); app text from the accessibility tree (`AccessibilityText`); Yomitan dictionary collection import (`YomitanBackup`, tested with a synthetic export only); release build verified with R8 (keep rules for ML Kit registrars and the JS bridge); `docs/architecture.md`, `docs/usage.md`.

- Later on 2026-09-26: copy button on entries; glossaries passed as JSON text (3–4× faster lookups for long entries); lint clean (API 30 fixes); dark theme, audio playback and clipboard checked on the emulator.

## Next

- Yomitan settings backup: interview the owner about what to import (dictionary order, Anki templates, audio sources, scan settings) before planning.
- Verify the Yomitan collection import with a real export from the owner.
- The owner is going to send a list of dictionaries for the catalog (`dictionary/api/src/main/assets/catalog/dictionaries.json`; the app fetches it from the repository).
- Phone testing: everything so far was verified on the emulator only.

## Open items

- The debug APK is ~120 MB (unminified dex, bundled dictionaries 48 MB, ML Kit). Release builds are not measured yet.
- Lookup-to-render latency on the emulator (measured with `popup-eval.mjs`): 30–45 ms for typical words, ~100–180 ms for する (16 long Jitendex entries). The native lookup takes a few ms; the rest is JSON and rendering. Not measured on the phone.
- AnkiDroid's editor on the emulator shows fields as HTML source; the card preview renders them. Dictionary CSS is included per glossary as a scoped `<style>`.
- Default text source is OCR; "app text first" is opt-in (not confirmed with the owner).

- Not yet verified on the physical phone.
- Rotation handling is minimal (the bubble re-docks on configuration change).
- Test images in `testdata/ocr/` are local only (third-party content, gitignored).
- Release keystore not created; see README.
