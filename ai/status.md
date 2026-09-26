# Status

Current plan: [plans/2026-09-26-initial-plan.md](plans/2026-09-26-initial-plan.md).

## Phases

- [x] Phase 0 — infrastructure
- [x] Phase 1 — overlay + OCR
- [x] Phase 2 — dictionaries
- [ ] Phase 3 — Anki + audio
- [ ] Phase 4 — polish

## Log

### 2026-09-26

- Phase 0: separate git repo; convention plugins in `build-logic/`; modules `app`, `overlay`, `core:{common,ocr,anki}`, `dictionary:{api,engine-hoshidicts,render-yomitan}`; Hilt 2.60.1, Room 2.8.5, KSP 2.3.12, Kotlin 2.4.20 on AGP 9.4.1; DataStore theme setting; accessibility service stub; onboarding screen (en/ru); debug/release signing; GPL-3.0 LICENSE, NOTICE, README, CLAUDE.md, `ai/` notes.

- Phase 1: Lens client (hand-written protobuf, JPEG ≤1500 px) and ML Kit draft combined in `CompositeOcr`; `TextLayout` hit testing (horizontal, vertical, rotated lines); accessibility overlay with bubble/dock/aim/highlight layer, window screenshots (API 34+) with display fallback, WebView popup shell with placement logic, Quick Settings tile, OCR debug screen and debug image viewer. Verified on the emulator: undock → Lens → popup on the X screenshot and on vertical manga text, rescan tap with line flash, double tap aim toggle, docking by dragging to the edge, offline ML Kit path, tile toggle. Lens measurements are in `notes/lens-protocol.md`.

- Phase 2: hoshidicts submodule + own JNI (`jni_bridge.cpp`, UTF-8 byte arrays in, JSON out); `DictionaryEngine` in `dictionary:api` with Room registry (`DictionaryRepository`, `DictionaryStorage`), WorkManager import queue (`DictionaryImports`, foreground `dataSync`), bundled Jitendex/Jiten/Kanjium downloaded at build time (`DownloadAssetsTask`) and installed on first launch (~5 s on the emulator), download catalog fetched from the repo (`catalog/dictionaries.json` in `dictionary:api` assets) grouped by language pair; `YomitanSorter` adds dictionary priority; renderer module `render-yomitan` (structured content, images, scoped styles.css, furigana, pitch); popup cards with frequency/pitch/deinflection chips, glossary links with a back stack; dictionaries screen with sections by type and drag reordering. Verified on the emulator: X screenshot, vertical manga text, Kolobok download and priority, link navigation (`scripts/popup-eval.mjs`).

## Next

- Anki + audio (phase 3): decisions are in the plan's table; start with the AnkiDroid API (JitPack) in `core:anki`, field templates and ➕ in the popup.

## Open items

- The debug APK is ~120 MB (unminified dex, bundled dictionaries 48 MB, ML Kit). Release builds are not measured yet.
- Lookup latency on the phone is not measured; on the emulator the popup follows the bubble without visible lag.
- Kanji dictionaries import fine (hoshidicts supports them) but nothing displays kanji entries yet (phase 4).

- Not yet verified on the physical phone.
- Rotation handling is minimal (the bubble re-docks on configuration change).
- Test images in `testdata/ocr/` are local only (third-party content, gitignored).
- Release keystore not created; see README.
