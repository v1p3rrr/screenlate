# Status

Current plan: [plans/2026-09-26-initial-plan.md](plans/2026-09-26-initial-plan.md).

## Phases

- [x] Phase 0 — infrastructure
- [x] Phase 1 — overlay + OCR
- [ ] Phase 2 — dictionaries
- [ ] Phase 3 — Anki + audio
- [ ] Phase 4 — polish

## Log

### 2026-09-26

- Phase 0: separate git repo; convention plugins in `build-logic/`; modules `app`, `overlay`, `core:{common,ocr,anki}`, `dictionary:{api,engine-hoshidicts,render-yomitan}`; Hilt 2.60.1, Room 2.8.5, KSP 2.3.12, Kotlin 2.4.20 on AGP 9.4.1; DataStore theme setting; accessibility service stub; onboarding screen (en/ru); debug/release signing; GPL-3.0 LICENSE, NOTICE, README, CLAUDE.md, `ai/` notes.

- Phase 1: Lens client (hand-written protobuf, JPEG ≤1500 px) and ML Kit draft combined in `CompositeOcr`; `TextLayout` hit testing (horizontal, vertical, rotated lines); accessibility overlay with bubble/dock/aim/highlight layer, window screenshots (API 34+) with display fallback, WebView popup shell with placement logic, Quick Settings tile, OCR debug screen and debug image viewer. Verified on the emulator: undock → Lens → popup on the X screenshot and on vertical manga text, rescan tap with line flash, double tap aim toggle, docking by dragging to the edge, offline ML Kit path, tile toggle. Lens measurements are in `notes/lens-protocol.md`.

## Open items

- Not yet verified on the physical phone.
- Rotation handling is minimal (the bubble re-docks on configuration change).
- Test images in `testdata/ocr/` are local only (third-party content, gitignored).
- Release keystore not created; see README.
