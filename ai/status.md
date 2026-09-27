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

### 2026-09-27

- First test on the owner's phone produced phase 5 (see the plan). Done so far: bubble/popup placement (bubble above the popup, dock only at the edge, size setting), OCR boost bands (off by default), app text + OCR merge with per-paragraph source labels, Lens pause after 429/403, UI language picker with generated locale config, search screen rename and empty state, no popup without results, Anki export in Yomitan's marker format with Senren/Lapis presets, templates per note type, 📖 for added notes and prevented duplicates (default prevent), per-field overwrite modes, audio clip menu on long press and volume, crash fix for missing AnkiDroid note types.
- Owner interview for the rest of phases 5–8 is complete; decisions are in the plan's table and changelog. CLAUDE.md got the working rules agreed in this session.
- The emulator's AnkiDroid was reset to a local collection without an account after backing up the owner's collection.
- Later on 2026-09-27 (steps 1–10 of the list below done, all committed and pushed): logs without user text (`redacted()`, `redactUrl`); docs and descriptive texts say "cloud recognition" (popup chip keeps "Lens"); `LanguageSupport`/`JapaneseSupport` (lookup start rule, Mozc romaji, sentence rules, kanji, OCR script, Anki markers, audio defaults); lookup settings (scan length, entries per lookup 32, text replacement groups with `MappedText` source mapping, romaji, kanji of the word); one Settings screen, home with problems; broken Anki setup (grey ➕ with reason → Anki settings; field list update), dictionary repair (bundled reinstall, catalog re-download, remove); audio sources LanguagePod101/Jisho/Lingua Libre/Wiktionary/TTS, source dialog with Test, test panel, `AudioPlayer`; Yomitan settings import (profiles, sections, summary; tested with the owner's export on the emulator); collection import rewritten (`RawJsonScanner`, keyless `{"$":[key,row]}` rows were lost before, checklist, read by URI, 2.7 GB: 32 s on desktop JVM, ~5.5 min on the emulator); catalog +19 entries (JMdict languages, JMnedict, KANJIDIC variants, Wiktionary, JPDB v2.2, BCCWJ, Aozora); popup header setting (owner request); landscape popup size, dock only on screen size change.
- Owner feedback, same day: text replacement settings removed (the owner did not ask for the editor); the owner's Yomitan rules are built into Japanese lookups as one spelling variant (`JapaneseSupport.spellingVariants`); popup fonts (system Japanese font by local name, catalog of OFL fonts, own files, size, hint when the phone lacks a Japanese font) and custom CSS with a checker (warnings above the field) and fallback appended to every `font-family`; Yomitan import brings text size and custom popup CSS. Findings in `notes/webview-fonts.md` (the page's `lang` decides glyph forms through `locl`).
- Step 11 (tests) done: page scripts under node:test + jsdom (31 tests); `AnkiBackend` interface with `AnkiNotes` tests (found and fixed: the duplicate check searched for a literal `{marker}` when a value was missing); `AudioFinder` against MockWebServer; `CompositeOcr` with fake engines and virtual time; instrumented: lookup pipeline on hoshidicts, `LookupPage` in a WebView (font serving refuses `..`), AnkiDroid round trip on the emulator's local collection ("Screenlate Test" deck and note type, one note per run).
- Step 12 done: README, `docs/usage.md` (settings, audio sources, Yomitan import, warnings, appearance), `docs/architecture.md` (languages, privacy, tests).
- Emulator state: 20 dictionaries (owner's collection imported), romaji on, audio sources from the owner's Yomitan profile (localhost sources fail there, expected), Anki still points at the missing Basic note type, popup header off, OCR boost on demand, text source app text first, M PLUS 1p downloaded and selected, the owner's desktop-profile CSS imported (text size 14). AnkiDroid has a "Screenlate Test" deck and note type from the round-trip test. Portrait fixed (accelerometer_rotation 0).


## Next

- Phase 6: CI per the decision table (build, unit tests, page tests and lint on every push to main with a debug APK artifact, keeping the last few; tag vX.Y.Z → per-ABI and universal release APKs, armeabi-v7a if the engine builds, source archive with submodules, changelog, GitHub Release; instrumented tests on tags and manual runs), dock glyph from `LanguageSupport.glyph`.
- Phase 7: About with generated library licenses and installed dictionaries' attributions (no mention of the cloud OCR provider), "Share logs", versioning from tags, daily update check against GitHub Releases (can be turned off); signing at the end with the owner.
- Later items and open questions for the owner are listed under phase 7 in the plan: faster collection conversion, e-ink mode, backup/restore (Yomitan-compatible dictionary export?).
- Phase 8 (languages) waits for an interview.

## Open items

- The debug APK is ~120 MB (unminified dex, bundled dictionaries 48 MB, ML Kit). Release builds are not measured yet.
- Lookup-to-render latency on the emulator (measured with `popup-eval.mjs`): 30–45 ms for typical words, ~100–180 ms for する (16 long Jitendex entries). The native lookup takes a few ms; the rest is JSON and rendering. Not measured on the phone.
- AnkiDroid's editor on the emulator shows fields as HTML source; the card preview renders them. Dictionary CSS is included per glossary as a scoped `<style>`.
- Default text source is OCR; "app text first" is opt-in (not confirmed with the owner).

- Not yet verified on the physical phone.
- Rotation handling is minimal (the bubble re-docks on configuration change).
- Test images in `testdata/ocr/` are local only (third-party content, gitignored).
- Release keystore not created; see README.
