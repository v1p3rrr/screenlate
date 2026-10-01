# Status

Current plan: [plans/2026-09-26-initial-plan.md](plans/2026-09-26-initial-plan.md).

## Next session (handoff, 2026-10-01)

- State: `main` is clean and pushed; last release v0.1.4; no release until the owner says so. All module reviews and
  answers Q1-Q11 are done and committed. Request 11 (bring the bubble back) is done, see the 2026-10-01 log entry.
  Requests 12 (spinner while the hidden-apps list loads), 13 (kanji entry when no word is found) and 14 (bundled
  KANJIDIC, kept aside when one is installed) are done. 15 (popup above/below instead of squeezed at the side) is done.
  16 is done: the branch `claude/elegant-allen-xmu3fx` (requests 18, settings order, and 19, bubble hold menu; 13 and
  14 there) is cherry-picked onto main and checked on the emulator. 17 (`/code-review xhigh --fix` since 764ae60) is
  done: six findings fixed. The owner answered Q1-Q6; requests 20 (popup never low or narrow, covering the word
  instead) and 21 (bubble menu above first, Back and any outside tap close it) are done. The tap that closes the menu
  stays consumed (owner). The merged branch `claude/elegant-allen-xmu3fx` is deleted on GitHub and locally. Nothing
  is open from those. Request 22 (open review findings, bundled dictionary updates) is done, see the 2026-10-01
  log entry; request 23 (settings resets, tooltips) is done, see its log entry. Requests 24 (spinner instead of the
  Dictionaries reset icon) and 25 (imports and resets cut short by a killed app) are done, see their log entry.
  Request 26 (error handling of imports, deletes and resets) is done together with `/code-review xhigh --fix` over
  9ed170e..HEAD, see the log entry "import errors, cancel, retry limits". Request 27 (every string translated, and
  translated well, in all 14 languages) is done, see the log entry "translation audit".
- Open, found while testing: the catalog does not recognize installed Wiktionary dictionaries (upstream renamed
  `kty-*` to `wty-*` and moved the index to Hugging Face); suggested to the owner as a separate task.
- The code review of v0.1.4..HEAD stopped early at the weekly limit; the owner chose to finish it another time. Not
  read or only partly: `DictionaryRepository`/`DictionaryLookup` (beyond `noTermDictionary`), `PerGeneration`,
  `DownloadCache`/`DownloadAssetsTask`/`release.yml`, `Protobuf`/`LensOcrEngine`, `Redaction`, `TextLayout`,
  `render.js`, `anki.js`, `definition.js`/`popup.js`, `CropEditor`, `PopupTextSections`/`PopupAppearanceViewModel`,
  `SearchScreen`/`SearchViewModel`, `OcrTestViewModel` (`git diff v0.1.4..HEAD -- <files>`). `BundledDictionaries`
  and `DictionaryImportWorker` were reworked since and checked on the emulator.
- Known, minor, left: `markDeleted` inflates every bundled zip; two bundled copies still get a third on update;
  `remoteCss` may load the engine and swallows errors; `CssCheck` misreads `user@host` and `/*` inside strings.
- Emulator state: the user's Jiten copy there is a test archive with the revision "Jiten 26-12-01" (imported to
  check that updates leave a user's copy alone); content equals the real Jiten.
- To check on the phone: grey ➕ during a slow cloud scan and its hint; CSS warnings with a real dictionary.
- Security review of the whole project (2026-09-30): fixed — a dictionary's `styles.css` or title could close the
  `<style>` that `note.js` puts into glossary fields and store HTML with event handlers in Anki notes (`styleElement`
  writes `</style` as `<\/style`). Kept by the owner's decision: dictionary `@font-face` is not scoped and can add
  faces to the page's own families ("Screenlate Sans"/"Screenlate Chosen"), so remote per-character fonts can see the
  OCR header text (Q7: the CSS is not rewritten); `javascript:` hrefs from structured content reach exported notes
  (needs a tap); `scopeCss` ends a block at a `}` inside a CSS string, which lets the next rule apply to the whole page
  (the owner chose not to touch the scoper). Fixed: the remote-file warning (`CssCheck.remoteFiles`) now reads CSS as
  a browser does (string-aware comments, decoded escapes, `\\`/tabs in addresses; checked in Chromium); after the PR
  review also CRLF/CR/form feed as line breaks, a hex escape taking the line break after it, `/*` inside an unquoted
  `url(...)`, and escaped whitespace inside an unquoted address (each confirmed in Chromium). `scopeCss` now looks
  for the `;` of a dropped statement at-rule outside comments, strings and escapes (owner's go-ahead for this narrow
  change), so `@x /*;` no longer uncovers commented rules the check does not see; a string left open still ends at
  its `;` as before (owner: keep a broken `@charset "utf-8;` from taking the next rule), which the check reports since it
  reads `url(` inside strings. Output byte-identical to before on the catalog's real `styles.css` (Jitendex,
  Wiktionary) and synthetic and broken at-rule cases. Low: build-time dictionary downloads have no checksum.
- Review of the security-fix branch (2026-09-30, xhigh, with fixes): `atStatementEnd` ended a string only at a
  line feed and read an unquoted `url(...)` address as CSS, so `@x "a<CR>/*" ;` and `@x url(a") /*;` both made
  the scoper drop the at-rule and uncover an `@font-face` that `remoteFiles` reports as commented out - a remote
  font with no ⚠. Fixed with `rawStringEnd` and `urlTokenEnd`; `@import url(a;b.css);` no longer eats the next
  rule either. `remoteFiles` now also reads a bare string of `image-set()`/`-webkit-image-set()` as an address
  (Chromium fetches it, the check saw nothing). Gradle build and unit tests and the page tests run clean here.
  Fixed later in 0a087b9 (scoper hardening): `scopeCss` found the block's `{` with a plain `indexOf`, so a `{` inside a comment split the
  rule and `@media /*{*/ screen { .a { ... } }` yields `& .a`, which Chromium resolves against the document root
  and applies to the whole page (checked in Chromium) - needs the owner's go-ahead, being wider than the narrow
  scoper change approved. Also open: `remoteFiles` scans string contents, so `content: "url(https://x/)"` raises
  a ⚠ for a host nothing loads from, and it reports a percent-encoded or IDN host as written rather than as a
  browser resolves it. Not checked here: the ⚠ and a note with dictionary CSS on a device.

## Phases

- [x] Phase 0 — infrastructure
- [x] Phase 1 — overlay + OCR
- [x] Phase 2 — dictionaries
- [x] Phase 3 — Anki + audio
- [x] Phase 4 — polish (except the Yomitan settings backup, which needs an interview)
- [x] Phase 5 — first phone test feedback
- [x] Phase 6 — build and publishing (first release v0.1.0 published 2026-09-27)
- [x] Phase 7 — release readiness (the owner's open questions pending)

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
- Phase 6: GitHub Actions (`ci.yml` green on every push: build, unit/page/build-logic tests, lint, debug APK artifact, last three kept; `release.yml` by tag or by hand: per-ABI + universal APKs, source archive, changelog, publishes only with the signing secrets; `instrumented.yml` by tag or by hand on an API 34 emulator with AnkiDroid; `scripts/ci-ankidroid-init.sh` grants AnkiDroid's storage access and taps "Get started", so the AnkiDroid round trip runs there too — 19 instrumented tests, none skipped). Release notes: `.github/release-notes/<tag>.md` if present, else commits since the previous tag. armeabi-v7a added after the engine tests passed under ARM translation on a local API 30 x86 image (AVD `Screenlate_API30_x86`). Versions from tags (`ScreenlateVersion`, `version.txt` in source archives). Dock glyph あ.
- Phase 7: About (updates, AboutLibraries list, NOTICE, dictionary attributions behind ⓘ, share logs via `LogExport`), in-app updates (`update/AppUpdates`, PackageInstaller, owner's rules: announced once per version, skippable, no system notifications, manual check, setting), plain HTTP for 127.0.0.1/localhost (local audio servers), owner's rule "details behind ⓘ" applied to About, release notes, bubble, Anki, home and appearance texts. Self-update tested on the emulator up to the install permission (check, notes, permission page); the install itself needs "Install unknown apps" for the test app, which the owner turns on (security setting). The emulator now has the published v0.1.0 as `com.vpr.screenlate` (release-signed; the debug-signed test builds were removed).
- First release v0.1.0 published 2026-09-27 (https://github.com/v1p3rrr/screenlate/releases/tag/v0.1.0): release key made by the owner (PKCS12, alias `screenlate`, `CN=Screenlate`, RSA 4096; kept outside the repo, `keystore.properties` in the repo root; never read its values), four repository secrets set by the owner. Certificate SHA-256 `20b7d9c9d1320be02226842591e05afa4e8486036c76304d5a56a7b8cb720343`: the same in the local build and in all four published APKs (versionCode 100). The published x86_64 APK starts on the emulator, installs the bundled dictionaries, and its update check against GitHub reports 0.1.0 as the latest version.
- Emulator state: 20 dictionaries (owner's collection imported), romaji on, audio sources from the owner's Yomitan profile (localhost sources fail there, expected), Anki still points at the missing Basic note type, popup header off, OCR boost on demand, text source app text first, M PLUS 1p downloaded and selected, the owner's desktop-profile CSS imported (text size 14). AnkiDroid has a "Screenlate Test" deck and note type from the round-trip test. Portrait fixed (accelerometer_rotation 0).
- Research (owner question): the Yomitan fork with faster imports was reviewed; hoshidicts already does what it does for zip imports; only our collection export converter can gain. Findings in `notes/dictionary-engine.md`, the phase 7 later item refined. No code changed.
- Collection import speed-up on branch `claude/elegant-allen-xmu3fx` (cloud session, owner's 2.73 GB export from Google Drive): the converter was ~90% of the import time (46 s vs 5.4 s for hoshidicts on desktop). `RawJsonScanner`/`YomitanBackup` now work on UTF-8 bytes and copy values in buffer runs; archives are stored uncompressed when they fit (`CollectionSpace`, measuring pass `YomitanBackup.measure`), with a "not enough space" message otherwise (new stage "Checking free space", error card with sizes). Conversion ~10 s uncompressed, 21.5 s deflated; output byte-identical to the old converter. JVM tests pass (25) in a plain-JVM harness; CI run on the branch by hand (build, unit tests, lint, page tests) passed. Instrumented tests, the UI and on-device timings are not checked yet (no Android SDK or device in the cloud). Details in `notes/dictionary-engine.md`.

- App icon on branch `claude/elegant-allen-xmu3fx` (cloud session, owner chose every step in galleries): a word lifted out of a line of text with the bubble's aim dot on its place, white background, the overlay's violet; debug build with a bug badge; the same silhouette on the Quick Settings tile and the import notification; icon in the README. Generated by `scripts/icon/icon.py` (plain filled paths, holes cut with skia-pathops), checked by `scripts/icon/check.mjs` (drawables rendered back and compared with the design, safe zone) and aapt2 9.4.1 with API 37 (links without warnings). Legacy WebP mipmaps removed. CI run on the branch by hand passed (build, unit tests, lint without errors, page tests): https://github.com/v1p3rrr/screenlate/actions/runs/36332781284, debug APK in the artifact `screenlate-debug-32f3657`. Not seen on a device yet. Details in `notes/app-icon.md`.

- After v0.1.0 (owner's feedback): JMdict (English) replaces Jitendex in the APK (universal APK 93 → 70 MB), Jitendex stays in the catalog; bundled archives fill slots, so upgrades that have Jitendex keep it without a duplicate JMdict (checked on the emulator: v0.1.0 → new build keeps Jitendex; fresh install gets JMdict ja → en). Missing languages come from the catalog on import. Catalog order: interface language, English, source language, ru, es, fr, de, rest by name. Vibration: a vibrator click with media usage (view haptics were ignored with touch feedback off), once per new word shown in the popup; checked with `dumpsys vibrator_manager`.
- Latin words without romaji (owner's request): `LanguageSupport.lookupStart` returns `LookupStart.Any` or `Whole(length)`; a Latin word on its own (letters and digits joined by - . & /, at least two characters with a letter) only accepts a match of the whole word, single letters never (JMdict lists all 52). `wordStartOffset` moves the overlay lookup to the word's first letter. hoshidicts already converts widths (OL ↔ ＯＬ), case counts as in Yomitan. Checked on the engine (LookupPipelineTest) and on the emulator (aim on L of OL finds ＯＬ, whole word highlighted).
- Phone feedback round (2026-09-27, owner's answers in the plan): ✕ only closes the popup (bubble and scan stay); pulling out of the dock wins over moving it (ratio 1.7, 24 dp from the touch point); OCR boost ⓘ in paragraphs; inflections: 🧩 chain, a tapped step opens a panel at the bottom with its description; names and descriptions of hoshidicts' 72 rules in string resources (en from the engine via `dictionary/engine-hoshidicts/tools/generate_inflections.py`, ru translated), `Transform.label` for display, `name` stays for `{conjugation}`; entry header in one row when the page is ≥420 px wide (word, 🧩, first frequency = sort dictionary with +N, distinct pitch accents without dictionary names, which open in the panel); dictionary name as a floating chip at the start of its first definition; landscape popup beside the word (half width, 90% height, shrinks to ≥260 dp, else above/below); interface language as a drop-down from the generated locale config with flags. Checked on the emulator in portrait (header, panel in en and ru, drop-down); landscape placement only by unit tests so far.
- README for users (2026-09-27): features, screenshots, install, requirements, limitations, privacy, license; developer parts moved to `docs/development.md`. Screenshots (`docs/images/*.webp`) were taken on the emulator with a local release build over the published v0.1.0 (clean settings, JMdict + Jitendex from the catalog, "App text first", text header off so no OCR source chip shows) and own test pages (`scratchpad/readme/*.html`, opened in the system HTML viewer through a MediaStore content URI with `--grant-read-uri-permission`; the viewer has no network, and Chrome's first-run terms are not accepted). Landscape popup checked on the emulator: beside the word, 420 dp wide, ~90% of the height.
- Found while taking them: a WebView answered character-location requests with the node's bounds for every character (whole paragraph highlighted, lookup from its first character); `sharesBoxes` now leaves such nodes to OCR. The floating dictionary chip covered a list marker when a definition opened with a list (Jitendex "＊", JMdict's glossary list); `placeDictionaryName` now puts the chip inline where the first line of text starts.

### 2026-09-28

- Branch `claude/elegant-allen-xmu3fx` (faster collection import, app icon) checked on the emulator and merged through https://github.com/v1p3rrr/screenlate/pull/1 with CI green; collection import skips the counting pass with four times the export free.
- Words broken across lines: horizontal text was already fine (Lens groups wrapped lines); vertical columns are now read right to left and split columns joined (`ReadingOrder`), checked with a synthetic page and the manga page (手に|入れたい).
- On-device draft: a band around the aim first (`FocusBand`), then the whole screen; ML Kit warm-up at start; scan logs with timings.
- Popup: ⚠ with the cloud recognition failure (common causes named, only for recognized words), violet border, #F8F6FD background; tag descriptions on tap (tag banks saved at import as `tag_notes.json`, bundled ones filled from the APK; structured-content titles).
- Notification permission asked at the first import or download, card on the Dictionaries screen; self-update ABI choice checked.
- Dictionary languages: index.json → catalog → content (`LanguageGuess`, `DictionarySample`, Android's TextClassifier for Latin/Cyrillic); installed ones filled once from sample lookups (`InstalledLanguages`), all of the owner's dictionaries came out right; manual edit in the details.
- Fixes: raw JSON escapes in index texts (decoded at import, stored ones once); another revision of an installed dictionary replaces it.
- Logs can be saved to `Download/Screenlate`; catalog +8 entries from MarvNC's list (JLPT, NWJC, CSJ, CEJC, Wiktionary kanji, mozc variants, jitai, JA Wikipedia); grammar dictionaries need no special support.
- v0.1.1 released (https://github.com/v1p3rrr/screenlate/releases/tag/v0.1.1, APKs: arm64-v8a 44 MB, armeabi-v7a 39 MB, x86_64 45 MB, universal 66 MB) from 7e70b42 with CI green.
- After the release (on main, not released): text in the popup can be selected and copied (`SelectionHost` gives Chromium a toolbar-less action mode, the window takes focus while selecting so the handles show, the page's own Copy button, furigana left out); pasted into AnkiDroid's note editor on the emulator as plain lines (note discarded). "Look up in Screenlate" (`PROCESS_TEXT`) is registered and found by the system; the emulator's Android 17 selection toolbar in WebView shows only its first four actions without an overflow, so it was not seen in a menu there.
- Also after the release: holding the floating bubble opens a menu to copy the paragraph under the aim (lines joined) or all recognized text (checked: pasted text is the whole paragraph); the rescan highlight stays amber and about 3 s long (owner, after trying violet and 5 s); with "App text first" OCR lines around the app's text flash too. Not released yet: offer v0.1.2.
- Logging pass (owner, after v0.1.1): scan stages with timings (capture, app text, Lens status/size/time, bands, draft/final), service start/stop, dock/pull-out, note results by kind, audio lookups, Yomitan settings import summary, why the app thinks it is offline (Android reports no network when the system blocks the app's traffic). Logs showed app text read only after the screenshot; now both run in parallel (Files app: app text 128 ms, ready before the 432 ms capture). "App text first" checked on the emulator in the Files app: exact lookups, immediate flash of app lines, bubble copy menu (paragraph and all text), no ⚠ for app text words. Unused drawables and strings removed (lint).
- The owner's phone (v0.1.1) showed "On-device (offline)" with ⚠ on a screenshot of the emulator: either no internet at that moment or the system blocking the app's traffic (MagicOS per-app network switches, background data); the next build logs which.
- Background work screen (Settings → Background work): battery optimization state with a request and the system list, the vendor launch-management hint; GitHub issue templates for the About report button (`ProblemReport`).
- Load and battery measured on the emulator (details in the plan's changelog): docked ~0.2% of a core, no alarms or jobs; a scan costs 4.5–10 s of CPU, most of it ML Kit. Changes: "Engines" setting (Both / Cloud / Device) and "Reduce load on the device" (whole-screen on-device pass waits 3 s for the cloud) in a "Screen recognition" block of the bubble settings; the highlight layer no longer stays composited; a recycled-bitmap fix; the popup's WebView renderer can be reclaimed while docked.
- Highlight shift (owner report): ML Kit's symbol boxes lag ~0.4 character behind the glyphs in some words; `SymbolLag` moves the inner boundaries onto the gaps when the pixels clearly say so (checked on five screenshots, exact words untouched). Compose text fields report character boxes offset by the field's padding; such fields (first box at the node's corner) are left to OCR. Both fixes also correct which character the aim finds, not only the highlight.
- Russian UI calls the bubble «плавающая кнопка» (owner decision; English keeps "bubble").
- App text read time: 68–144 ms in other apps (Settings, photo picker, HTML viewer; one 1.5 s outlier), ~6.7 s on Screenlate's own screens (cause unknown; OCR waits for it).

### 2026-09-28 (later session)

- Owner decisions: "App text first" stays off by default and is marked experimental in its ⓘ (with the speed benefit named); OCR results no longer wait for app text (`OverlayController.startScan`: whichever source is ready is shown, merged with the other; checked on the emulator with an artificial 5 s app text delay: ML Kit draft at 1 s, Lens final at 2 s, app text merged at 5 s).
- The 6.7 s app text read on Screenlate's own screens did not reproduce (170–620 ms on home, search and settings, with and without the popup); unrelated to the reclaimable popup renderer. Findings in `notes/ocr-engines.md` (new, linked from CLAUDE.md).
- Owner question answered: the popup is never scanned (window screenshots on API 34+, overlays hidden on older versions, app text reads application windows only), and text under it is recognized.
- Docs: `docs/usage.md` (Background work, Screen recognition, experimental app text, troubleshooting), `docs/architecture.md` (parallel app text, trusted boxes, `OcrOptions`, `SymbolLag`, renderer priority). Release notes `.github/release-notes/v0.1.2.md`.
- v0.1.2 released (https://github.com/v1p3rrr/screenlate/releases/tag/v0.1.2, APKs: arm64-v8a 44 MB, armeabi-v7a 39 MB, x86_64 45 MB, universal 66 MB) from aff0905; CI, release and instrumented runs green.

### 2026-09-29

- Everything before phase 8 done (owner's instruction: autonomously, checked on the emulator, then released):
  - Dock pull: the bubble docks when its center (the aim in center mode) crosses the edge, or as before when the finger is lifted within 12 dp of it.
  - "App text only" (`TextSource.APP_TEXT_ONLY`): a second switch under "Read app text" (formerly "App text first") with a confirmation; engines, OCR boost and reduce load are disabled; no screenshot unless a note needs a picture; a message when the app exposes no text. The cloud engine hint names its mobile data use.
  - E-ink mode (Appearance): black and white app theme, no ripples or animations, black bubble and aim, frames instead of fills, popup with a black border; turning it on offers a 56 dp bubble and larger popup text; a one-time home hint on known e-ink makers links to "App text only".
  - Backup and restore (`app/backup/`): one zip with a manifest, typed preferences by section, the dictionary list, popup fonts and optionally the converted dictionaries; restore through a checklist that replaces checked sections, adds or replaces dictionaries (never removes), and ends with a summary. Checked on the emulator: 58 MB backup with three dictionaries, full restore, lookups work after it.
  - Dictionary details link to the website and the download.
  - Vertical text in the middle of a narrow screen: the popup goes beside the column down to 140 dp (`narrowMinWidth`), else above or below.
  - "Look up in Screenlate": instrumented `ProcessTextTest` (the system offers our activity for `PROCESS_TEXT`, and it opens with the text).
  - A rescan on the emulator draws frames only on the manga, never on the popup (the owner's question).
- Fixes found while checking: switching e-ink mode returned the app to the home screen (the theme composed the content on separate paths; now one path, see `notes/build-environment.md`); the "no app text" message kept the popup spinner.
- Not checked on the emulator (for the phone): the e-ink home hint and its link to the switch, a picture attached to a note in app text only mode, the restore of downloaded fonts.
- v0.1.3 released (https://github.com/v1p3rrr/screenlate/releases/tag/v0.1.3, APKs: arm64-v8a 44 MB, armeabi-v7a 39 MB, x86_64 45 MB, universal 66 MB) from 092a8d6; CI, release and instrumented runs (with `ProcessTextTest`) green.

### 2026-09-29 (after v0.1.3)

- Home problems grouped: one card for failed audio sources (a line per source with its host and a readable reason) and one for missing dictionaries (a line per dictionary with its own buttons). Failures of sources edited or removed since are left out.
- About: one sentence on what the app is for and "Made by vpr".
- Mentions of the apps that inspired the project and of forks named in research removed from the repository, `ai/` included (git history left as is, owner's choice).
- Default audio sources checked on the emulator: JapanesePod101, LanguagePod101 and Jisho find and play 読む (JapanesePod101 starts after ~1.5 s because of its redirect).
- The owner's audio server (see `notes/references.md` → audio server) works through its https domain: 5 clips found and played on the emulator. Why the phone got nothing is not proven; the proxy's URL rewrite dates from 2026-09-27 20:37 UTC, and before it the list named files as `http://0.0.0.0:5050/...` (the phone itself). The proxy logs show no file download from a phone ever.
- Audio source changes (owner decisions): plain HTTP allowed for any address; a list's local file URLs (`0.0.0.0`, localhost, loopback) are fetched from the list's server when the list came from elsewhere; errors read as a phrase plus the error type (`AudioError`); on Android 17 the local network permission (`ACCESS_LOCAL_NETWORK`) is asked when a source on the home network appears (added, imported, restored, or on start) and offered again in a failed test and the home card. Checked on the emulator (Android 17) with a mock list server at `10.0.2.2`: denied → "No access to the local network" with an Allow button; allowed → list over http, `0.0.0.0` rewritten, clip played.

### 2026-09-29 (interface translations)

- Interface in 12 more locales (de, fr, es, it, pt, pl, tr, vi, ja, ko, zh-Hans, zh-Hant) in all four string modules; `TranslationsTest` checks that every string exists with the same format arguments, plurals have the CLDR categories of their locale, and quotes are escaped. Disclaimer about AI translations in README and under the language picker.
- Every locale checked on the emulator (home, search, all settings screens, the language picker; montages in the session scratchpad). Found and fixed: the picker showed "System" for system-set tags like pt-BR, zh-TW, zh-Hans-CN (`LanguageOptions.selected`); long segment labels were cut (they shrink to 10 sp now); the language chosen in the app reached activities only, so the bubble, popup, crop editor and notifications kept the system language (`AppLanguageResources` in the application and the accessibility service; instrumented test in core:common).
- Owner requests on the way: no offline error with only on-device recognition (the chip says "On-device"); the crop frame starts around the paragraph with 48 dp (`CropFocus`), never at the corner for Lens elements without a size (dropped in `LensProtocol`); "Whole screen" toggles back to "Frame". Checked on the emulator in German with network off, and the crop frame in app-text-only mode (Settings app in Japanese).
- The OCR boost ⓘ now opens with "not needed by default, only when small text is not recognized, more mobile data" (owner request) in all locales.
- Module map for the pending full code review: `notes/module-map.md`.
- Emulator state: the debug app has AnkiDroid access and Anki set to the "Screenlate Test" deck with Basic (Back = `{screenshot}`); app language back to system.
- v0.1.4 released (https://github.com/v1p3rrr/screenlate/releases/tag/v0.1.4, APKs: arm64-v8a 44 MB, armeabi-v7a 40 MB, x86_64 45 MB, universal 67 MB) from 3f0dc60; CI, release and instrumented runs (API 34, including the new `AppLanguageResourcesTest`) green.

### 2026-09-29 (feedback after v0.1.4)

- Owner feedback in `plans/2026-09-29-feedback-after-0.1.4.md`, all done on main, not released (owner: release on command):
  - Engines ⓘ explains "Both".
  - E-ink off restores the bubble and popup text sizes from before "Make larger", each only if unchanged since (`EInkEnlargement`, keys `e_ink_*` outside backups); the hint says it is for e-book readers and similar screens.
  - Popup font: "Only for Japanese text" switch, on by default; the chosen font is declared again as "Screenlate Chosen" with the script's `unicode-range` (`notes/webview-fonts.md`).
  - Bubble copy menu: the paragraph under the aim, else the paragraph of the word in the popup, else only "Copy all" (`CopyMenuText`); before, the last hit was kept after the aim left text.
  - Yomitan import names the single-dictionary import with a button to Dictionaries; "manufacturer's" instead of "maker's"; the AI translation note names no languages.
- Emulator: the debug app's language is English (set for the checks); Noto Serif JP stays downloaded, the phone font is selected.
- Full review of module 10, appearance and fonts (owner's command; findings in the plan). Fixed: `@font-face` in custom CSS is left alone and its family counts as known; the CSS draft survives rotation and is saved when the screen closes; a backup restore that turns e-ink off restores the sizes like the switch (`EInkSizes`); the popup text size is clamped when read; own fonts are copied to a file and read in place, get a new file name on every import, and a restored `fonts.json` with non-plain file names is ignored; `fonts.json` is replaced by rename; the app draws nothing until the theme and e-ink setting are read; `popup.js` no longer sets `lang="ja"`; the add-font button lists `.woff`/`.woff2` in all locales. Left as is (owner): the Japanese punctuation `unicode-range`.
- Checked on the emulator: `@font-face` CSS without a warning and passed to the page untouched, the draft after two rotations and after leaving right after typing, import and re-import of a font file (same id, new file, old deleted, served to the page), e-ink on/off, restoring only "General" from a backup with e-ink off (e-ink off, text size back), 骨 in the Japanese form. A cold start showed the empty window until the settings were read (about 1–1.5 s on the emulator, AOT-compiled); by the owner's choice the splash screen with the icon (`core-splashscreen`, `Theme.Screenlate.Starting`) now stays until then, in the main screen and in "Look up in Screenlate"; checked on the emulator (icon, then content, no empty frame).
- Second review of module 10 (owner's command; 13 findings in the plan), all fixed: font import errors no longer crash and the old files go only after the new one is in place; the chosen font is always named by its "Screenlate Chosen" alias; own files are separate fonts named by family and style with their `OS/2` weight or variable range; WOFF/WOFF2 refused on import; slider rounding; restore summary counts fonts; failed downloads clean up; e-ink switch and "Make larger" serialized; restored font lists checked; font requests for deleted files answer 404; `CssCheck` line numbers by binary search. New tests: `FontFilesTest`, `PageFontsTest`, `CssCheckTest`, page tests, instrumented `PopupFontsTest` and `LookupPageTest`.
- Heavier popup text (owner request, "the phone's font is very thin"): weight (normal to bold) and letter thickness (outline 0–5) sliders under "Text size", off by default, following "Only for Japanese text" (the phone's font: only the script); script runs wrapped by `popup.js`, details in `notes/webview-fonts.md`. Checked on the emulator: sliders, ⓘ, preview, search page in both scopes, the web-font error; with the phone's font the disabled switch now shows on. Emulator state: the phone font selected, weight and thickness off, the test WOFF font and file removed.
- Final check of those changes (owner's command): the phone's font weights and the preview now come from the file the page's `local()` names resolve to (before, `NotoSerifHentaigana.ttf`, also listed for `ja`, could lend the serif face its variable range and stop synthetic bold); a variable font's name leaves out the weight of its default instance; heavier-text marks are recomputed when the custom CSS changes. Tests: `FontFilesTest`, a page test, instrumented `SystemFontFilesTest` (with `PopupFontsTest` and `LookupPageTest`, 12/12 on the emulator).

- Definition copying (owner request): with the switch on (Settings → Popup, off by default), a copy button next to each dictionary's name chip copies that dictionary's definitions for the entry, without the headword. "Everything" copies HTML plus plain text (`ClipData.newHtmlText`); "Only meanings" copies numbered meanings found by `definition.js` (shapes in `notes/yomitan-behavior.md`), else the whole text. A new "Popup" settings screen holds the font, text size and weight, custom CSS and copying; Appearance keeps theme, e-ink and language. Tests: `definition.test.mjs`, a popup page test, `PageFontsTest`; instrumented overlay tests pass. Checked on the emulator in the app's search (both modes, pasted back); the overlay popup uses the same page code and was not opened (the emulator's accessibility service is off).
- Highlight boxes of app text and ML Kit are line-height tall and a bit wider than the glyphs; the owner decided to leave them.
- Review of module 9, overlay runtime (owner's command; 15 findings in the plan). Fixed: the scan screenshot is use-counted (`SharedScreenshot`: the scan and each note being added hold it, the last frees it), so "Whole screen" in the crop editor no longer hands out and recycles the screenshot (`CropView.cropped` copies), and rotation, hiding or docking close an open crop editor as cancelled (`closeScan`, `CropEditor.cancel`); a note's sentence and crop frame come from the layout of the word shown; cancelled duplicate checks and sentence furigana lookups stop instead of writing stale results; hiding the bubble clears highlights and note state; scan errors stop the popup spinner; a cancelled capture no longer falls back to a display capture, and a late screenshot is freed; a cancelled touch no longer opens the copy menu; the cloud response, ML Kit's line building and the screenshot copies and crops run off the main thread; styles, tag descriptions and frequency modes are cached until the dictionaries change (`DictionaryRepository.generation`, `PerGeneration`); the media failure log no longer names the path. Left (owner): the popup in display screenshots; the hard-coded Japanese waits for phase 8. The fixes were reviewed again with their callers; nothing new. Tests: `SharedScreenshotTest`, `PerGenerationTest`, instrumented `CropViewTest` (2/2) and `DictionaryRepositoryTest` (9/9). Not run on the emulator: the overlay needs the accessibility service, which is off there.
- Second review of module 9 (owner's command; 9 findings, all fixed). ➕ now fixes the note's sentence from the view shown when pressed (`NoteSource`) and withdraws the scan's cloud request (`CompositeOcr.recognize(stopCloud)`: the on-device result becomes final; OCR boost stops for that scan) (owner). A note already being added is not cancelled by docking (owner), but a picture is taken only while its scan is open, so the crop editor never opens after a dock. The note's result goes to the entry of its term (`entryOf`), ✕ keeps the scan's note memory, OCR boost is off in app-text-only mode, band crops run off the main thread and retain the shared screenshot, and a note releases the screenshot as soon as the editor returns. Tests: 4 new `CompositeOcrTest` cases (26/26), `NoteEntryTest`, instrumented `CropEditorTest` (4/4) and `CropViewTest` (2/2). Checked on the emulator with the accessibility service on (note add, 📖 after ✕, crop editor, rotation, ➕ during a slow cloud request); two bugs found there and fixed: ➕ after the final result no longer stops OCR boost, and a withdrawn cloud request labels the result "On-device" without a warning. A review of these fixes (7 findings, all fixed) moved the withdrawal signal into the final OCR update (`cloudWithdrawn`), kept offline labels, stopped an ML Kit failure after a withdrawal from showing an OCR error, and kept notes finished after a dock out of the next scan (`ScanNotes`); new `OcrStatusTest`, `ScanNotesTest` and 3 `CompositeOcrTest` cases (29/29).

### 2026-09-30 (module reviews)

- Module reviews (owner's command, `plans/2026-09-30-module-reviews.md`), module 1 build, CI and release (three runs): the bundled dictionary cache downloads a file again when its URL changes, checks that a `.zip` is an archive, has timeouts and names failed URLs (`DownloadCache`, `DownloadCacheTest`); release builds take the version from the tag (`version.txt`), and version lookups skip pre-release tags; a failed publish keeps the APKs as artifacts; foundation-layout comes from the Compose BOM. Question Q3 (a deleted bundled Jiten list comes back when the unversioned file changes) waits for the owner.
- Module 2 common and language support (three runs): `redacted()` survives a loop of causes, a damaged settings file is replaced with the defaults instead of crashing every start, IPv4-mapped IPv6 addresses of private hosts count as local network. Two small items deferred to modules 8 and 9–11 (a test for the default audio source names, one helper for the theme mode's dark mapping).
- Module 3 OCR (four runs): a Lens timeout is a `SocketTimeoutException` in every path, so a timeout with a failed on-device run shows an error instead of a silent stuck scan, and cloud only calls it a timeout; a cancelled ML Kit task fails the scan instead of cancelling it; band refinement propagates cancellation; the Lens response is closed when the call was cancelled; the protobuf reader rejects negative and overflowing lengths; the screenshot copy is freed when a scan is cancelled around it; reading order compares effective engines; warm-up takes the language; unused `TextLayout` members removed. Two overlay items deferred to module 9.
- Module 4 dictionaries (three runs): an archive title with `..` or a leading `/` no longer makes the native importer write or delete files outside its staging directory (`ArchiveTitles`); a failed import no longer fails every import queued after it (failures are results with an error); a collection import goes on past a failing dictionary and names the failed ones; an update that takes another dictionary's title replaces it cleanly; catalog matches need the same kind (Jiten vs Jitendex); import tasks are listed in queue order; the revision is decoded like other index texts; a language fill failure no longer fails the bundled install. Question Q4 (the failed card names the reason, not the dictionary) waits for the owner.
- Module 5 lookup and engine (four runs): the no-dictionary check follows the lookup (language, switch, files) instead of counting every term dictionary, so nothing found no longer hides the popup silently when no usable dictionary exists; dictionary links in the popup and the search field look up the whole query (up to 100 characters) instead of the scan length, which also bounds long texts from the selection menu; the sort's last tiebreaks follow Yomitan (longer term, term text, more definitions); restored lookup settings are clamped. Questions Q5 (the no-dictionary text) and Q6 (the engine cuts the list before dictionary priority) wait for the owner.
- Module 6 lookup page and rendering (four runs): the popup's back button marks notes again (`onViewRestored`), a queued render survives other scripts, `evaluate` returns when the renderer dies, `scopeCss` handles statement at-rules and keyframes, audio menus stay closed and close on redraw, entry-button changes keep note states, kanji views get the common labels, structured-content styles follow Yomitan's list, extension kanji get furigana, and status updates no longer replace a pushed view. Question Q7 (no CSP).
- Module 7 Anki export (four runs): furigana and cloze markers are HTML-escaped, a cancelled add is not an error, settings of a newer version keep what this version knows (enum values and overwrite modes fall back to defaults), glossary media keep their own extension and no temp files stay behind, AnkiDroid query errors are redacted, the Anki settings screen applies only the latest refresh, keeps saved templates when AnkiDroid does not answer, shows outside template changes after typing and no longer flashes "not installed", a refused note has a translated message, and the note screenshot file is always deleted.
- Module 8 audio (three runs): audio settings updates are atomic and survive unknown source types of newer versions, the clip cache follows source changes and does not remember offline misses, clip files get a sane extension and a 10 MB limit, test clips and dialog tests keep only the latest request, Yomitan's `jpod101-alternate` imports as LanguagePod101, URL templates take `{language}`, and every language's default sources are checked by a test.
- Module 11 app shell, home, search and localization (three runs): back and screen buttons act only on the resumed screen, so a double tap during the fade no longer empties the window or opens a screen twice; the home check cancels an older one, hides a dictionary while it downloads again, checks files off the main thread, follows the dictionary language like lookups, and no longer crashes on a failed check (nor does app start); the scan length slider keeps imported values above 40; late link or kanji lookups no longer land on new search results; a theme change keeps a pushed view; search lookups pass cancellation on; the OCR test scales large photos; `TranslationsTest` checks multi-line strings. Questions Q8 (maker-specific launch hint), Q9 (dark mode switch loses a pushed view) and Q10 (keyboard over selection-menu results) wait for the owner.
- Module 12 backup and Yomitan settings import (three runs): a backup from a newer version with sections this one does not know opens without them instead of being refused; restoring without dictionary files stops reading where they start; a Yomitan settings file is read up to 16 MB, a newer pick replaces an older one still loading, and a failed import says so instead of crashing; the collection import cannot be queued twice and says when queuing fails; custom audio sources without a URL are skipped; Yomitan's dictionary switches apply with the order in one engine reload.
- Module 13 updates, About and logs (three runs): the manifest declares the permission Android needs to install updates without a prompt; a check left unfinished no longer keeps "Checking" with the button off; an install prompt that did not show can be opened with "Continue installing"; the text says the app closes after an update instead of restarting; a failed install session is abandoned; a failed log save leaves no pending file.
- Modules 9/10 deferred fixes: shared `ThemeMode.isDark`, cloud-only scan errors name the reason, the display capture (and the 34+ fallback) hides the popup too, stale kanji and link pushes are dropped. Module reviews are complete; the owner's answers to Q1-Q10 are in `ai/plans/2026-09-30-module-review-questions.md`.

### 2026-09-30 (answers to the review questions)

- All answers implemented (owner's command to work autonomously): search opens without the keyboard when text comes from the selection menu (Q10); the vendor launch path shows only on that vendor (Q8); a failed import card names the dictionary and the reason (Q4); bundled dictionaries the user deleted stay deleted, also after updates, with a one-time check for older installs (`BundledDictionaries.markDeleted`, Q3); "installing" and "no dictionary with definitions is on" are two texts, and the last enabled dictionary with definitions of a language can be neither switched off nor deleted (toast; `isLastTermDictionary`; search then offers "Manage dictionaries", not yet seen on the emulator, whose only dictionary cannot be switched off; Q5); the lookup page has a CSP that lets only stylesheets and fonts come from the internet, and the custom CSS field and dictionary cards warn about CSS loading files from the internet (`CssCheck.remoteFiles`, Q7); with several dictionaries with definitions the engine returns every candidate and the cut follows the dictionary order (`engineLimit`; measured: 5-32 candidates, no slower, Q6); ➕ is grey until the scan's final text with a hint on tap, active at once for app-text words, a new scan closes the old popup, and ➕ no longer withdraws the cloud request (the `stopCloud`/`cloudWithdrawn` path is gone, Q1/Q2).
- Checked on the emulator: search without keyboard, bundled worker, last-dictionary toasts, CSP blocking a remote image (no errors for the normal page), both CSS warnings (a test dictionary was imported and deleted again), a new scan closing the popup. Later the same day, with a hanging proxy: the grey ➕ on the draft, its hint toast and ➕ turning active on the final result; the search's "Manage dictionaries" button (JMdict's language was switched to Russian and back for it) opens the Dictionaries screen.
- A bundled update with a new revision mark replaced the old dictionary already (`dictionaryKey`); only with several copies installed it added another, now it takes the bundled copy (`sameDictionary`). A Yomitan settings import or a backup restore that would switch off every dictionary with definitions of a language keeps the first of them on and lists it in the summary (`keptTermDictionaries`).
- Code review (xhigh) of everything since 011c8fd: 11 findings. Fixed: deleting a dictionary no longer stops halfway when the screen is left (`NonCancellable`); "still being installed" shows only while an import runs, an empty registry otherwise says no dictionary with definitions is on. Q11 answered: a word kept from the draft goes into the note with the draft's sentence, as the note always holds the word the popup shows (behavior unchanged, comments updated). Left as known, minor: the last-dictionary rule counts dictionaries without files and lives in the callers; ➕'s wait follows the aimed engine; `markDeleted` inflates every bundled zip; `baseTitle` next to `dictionaryKey`; two bundled copies still get a third on update; `remoteCss` may load the engine and swallows errors; `CssCheck` misreads `user@host` and `/*` inside strings.

## Next

- Next release on the owner's command (feedback after v0.1.4 is on main).
- Waiting for the owner's phone feedback on v0.1.4 (interface languages, overlay in the chosen language, crop frame and toggle, "On-device" chip) and v0.1.3: backup and restore, e-ink mode, app text only, dock pull, plus the v0.1.2 list (icon, collection import timings, import notification, scan timings, vertical text across columns, "Read app text" in X and Chrome, self-update).
- Module reviews and the answers to their questions are done ([plans/2026-09-30-module-reviews.md](plans/2026-09-30-module-reviews.md)). On the phone: the CSS warnings with a real dictionary.
- On the phone or with the emulator's accessibility service on: crop editor (Frame, Whole screen, two notes in a row, rotation while open), a note right after the popup appears (sentence from the final text), hiding the bubble through a hidden app.
- On the phone: after MagicOS stops the service, the start screen's red "bubble is gone" line and whether its button
  opens Screenlate's own accessibility page or the list; the keep-alive notification and whether it keeps the service.
- Phase 8 (more languages) needs an interview per language first.

## Open items

- ML Kit is slow on the emulator (x86 without acceleration): ~5 s for a full screenshot, 1.6–2.4 s for a focus band; its time follows the amount of text, not the image size (a screen at 2/3 scale took as long). Phone timings unknown until the owner shares logs.
- The emulator's debug app has both Jitendex.org [2026-01-04] and [2026-08-11] from before the revision fix; imports no longer add such copies.

- The debug APK is ~120 MB (unminified dex, bundled dictionaries 48 MB, ML Kit). Release APKs: arm64-v8a 69 MB, armeabi-v7a 65 MB, x86_64 70 MB, universal 93 MB.
- Lookup-to-render latency on the emulator (measured with `popup-eval.mjs`): 30–45 ms for typical words, ~100–180 ms for する (16 long Jitendex entries). The native lookup takes a few ms; the rest is JSON and rendering. Not measured on the phone.
- AnkiDroid's editor on the emulator shows fields as HTML source; the card preview renders them. Dictionary CSS is included per glossary as a scoped `<style>`.
- Default text source is OCR; "Read app text" (formerly "App text first") is experimental (owner, 2026-09-28) until it is tried on the phone.

- Not yet verified on the physical phone.
- Rotation handling is minimal (the bubble re-docks on configuration change).
- Popup placement (requests 15 and 20, 2026-10-01): in portrait above the word (vertical text too), else below the bubble, else full size at the edge covering part of the word; in landscape beside the word down to 80% of its width, else full size at the edge. The earlier narrow popup beside a column is gone.
- Test images in `testdata/ocr/` are local only (third-party content, gitignored).

### 2026-09-30 (security review)

- Security review of the whole project in three passes (lookup page and scripts; archives, files, JNI and backup;
  components, network, updates, logs), each finding re-checked separately. One confirmed issue, fixed: dictionary CSS
  or a dictionary title ending the `<style>` element in glossary note fields (reproduced in jsdom; page tests for the
  escape and for unchanged ordinary CSS). Findings below the report threshold are listed under "Next session".
- Owner's answers: `@font-face`, `javascript:` hrefs and the scoper stay; the remote-file warning is fixed (escaped,
  backslashed and tab-split addresses and a `/*` inside a string no longer hide a server; Chromium confirmed which of
  them load). Changes go through https://github.com/v1p3rrr/screenlate/pull/2.

### 2026-09-30 (review of all changes since v0.1.4)

- Reviewed v0.1.4..HEAD at xhigh, cut short by the weekly limit: the dictionary repository, download cache, Lens protobuf, page scripts and crop editor diffs were only partly read. Fixed: a failed font restore now re-reads the installed list. Open, not fixed (behavior choices or minor): the popup chip and ➕ wait follow the aimed engine, not the shown word's (matters for a kept draft word); a failed final ML Kit pass replaces a shown draft word with the error; `showMessage` leaves a pending lookup running; catalog font re-downloads reuse file names; `EInkSizes.enlarge` overwrites an earlier record; `AppUpdates.update` does nothing during a check; plus the skipped items of the previous review.

### 2026-09-30 (scoper hardening)

- The CSS scoper is now text-aware everywhere, not only in the statement at-rule scan: a `{`, `}` or `;` inside a
  comment, a string, an escape or an unquoted `url(...)` no longer opens or closes a block. Without it a dictionary
  could put `/*{*/` in a selector and have the rest of its stylesheet apply to the whole popup page, while the
  remote-file warning, which reads the raw CSS, saw those rules as commented out.
- Proof kept in the plan's changelog: 0 differences against the old scoper on real dictionary stylesheets and on
  ordinary CSS, 8 of 14 targeted escapes closed with none left, no declarations lost (checked in Chromium).

### 2026-10-01 (bringing the bubble back)

- Owner's request 11 in [plans/2026-09-29-feedback-after-0.1.4.md](plans/2026-09-29-feedback-after-0.1.4.md): when the
  phone stops the service, the bubble is gone until the accessibility switch is turned off and on by hand; the home
  screen should bring it back in one tap. Five decision rows and a changelog line for the owner's answers are in that
  plan. PR #2 (the security fixes) was merged into main first; local `main` was at bc6116b when this work started.
- The limit, established and told to the owner: `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` needs
  `WRITE_SECURE_SETTINGS` (signature|privileged), so an app can never re-enable its own accessibility service. Only
  the case "service alive, bubble hidden" is fixable in the app; a stopped service can only be pointed at the settings.
- Implemented and committed to main:
  - `OverlayServiceStatus.running` — a `StateFlow` set in `onServiceConnected` and cleared in `onDestroy`. The service
    shares the app's process, so it is an exact "is the service running" signal, and the start screen follows it live.
  - `OverlayServiceStatus.openAccessibilitySettings` tries Screenlate's own page
    (`android.settings.ACCESSIBILITY_DETAILS_SETTINGS` + `Intent.EXTRA_COMPONENT_NAME`). Stock Android guards that page
    with `OPEN_ACCESSIBILITY_DETAILS_SETTINGS` (signature|installer), so on the emulator it throws a
    `SecurityException` (the first version crashed on every accessibility button, found while checking); a refused or
    missing page opens the list of services with the settings app's highlight extras (`:settings:fragment_args_key`),
    which highlight Screenlate's row on the emulator. Every accessibility button on home goes there (owner's decision).
  - Home: inside the "Accessibility service" card, `BubbleControls` shows one of three states
    (`BubbleState.SHOWN|HIDDEN|STOPPED`, `bubbleState()` in `HomeScreen.kt`, pinned by `BubbleStateTest`). STOPPED wins
    over the visibility setting, is coloured as an error, adds a hint, and its button opens the accessibility settings;
    HIDDEN offers "Bring the bubble back", SHOWN offers "Hide the bubble".
  - A "Show the bubble" switch at the top of the bubble settings, backed by the same `bubbleVisible` preference.
  - `BubbleKeepAliveService` (overlay): a silent ongoing notification (channel `bubble_keep_alive`, `IMPORTANCE_MIN`,
    id 2, `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+, 2-arg `startForeground` below it since minSdk is 30),
    started and stopped by the accessibility service. It follows the new `overlay_keep_alive` preference (off by
    default, owner's decision; backed up with the bubble section), switched from a Settings → Background work card.
    Turning it on asks for the notification permission the first time and writes the setting only after the answer: a
    notification posted before the grant stays hidden until the service restarts (seen on the emulator). A sticky
    restart stops itself when the accessibility switch has been turned off meanwhile. With notifications off the card
    says so and offers to allow them (owner's answer, 2026-10-01; `rememberNotificationsPermission`, shared with the
    Dictionaries card), and `BubbleKeepAliveService.repost` shows the notification once they are allowed.
  - Strings in all 14 locale files of `app` and `overlay`; `docs/usage.md` and `docs/architecture.md` updated.
  - The `setComponentEnabledSetting` rebind trick was rejected by the owner ("не трогать вообще") and is not used.
- Checked on the emulator (API 37, debug build): all three home states (STOPPED reproduced by holding a UiAutomation
  connection, see `notes/build-environment.md`), live switch between them, both accessibility buttons open the list
  with the row highlighted and no crash, hide/show from home and from the bubble settings switch, keep-alive on (with
  the first-time permission dialog; notification in the shade), off (service gone), following the accessibility
  service through an unbind and rebind, and with notifications declined earlier: the line and button, then the
  notification after allowing through the dialog and through the app's notification settings. `./gradlew assembleDebug testDebugUnitTest :app:lintDebug :overlay:lintDebug`
  green, lint only old warnings.
- Not checked: the phone's own case (MagicOS stopping the service; whether its settings app opens the details page).
- Emulator state: the debug app has the notification permission (granted in this check, flag USER_SET), keep-alive
  off, the bubble shown, the accessibility service enabled for the debug app only.

### 2026-10-01 (settings order, cloud session)

- Owner's request 18 (13 on its branch) in [plans/2026-09-29-feedback-after-0.1.4.md](plans/2026-09-29-feedback-after-0.1.4.md): the
  settings list now goes from function to look to system: Bubble, Lookup, Dictionaries, Anki and audio, Popup,
  Appearance, Background work, Import from Yomitan, Backup and restore, About; no group headings (owner's answers).
- Code: only the entry order and the `SettingsPage` enum in `SettingsScreen.kt`; `docs/usage.md` lists the sections in
  the same order. A parallel session was working on main, so the change stays out of every other file.
- On branch `claude/elegant-allen-xmu3fx` (rebased onto main at f3d5f80), not merged. Not checked on a
  device: the list is plain reordering, the Background work badge moves down with its entry.

### 2026-10-01 (bubble hold menu, cloud session)

- Owner's request 19 (14 on its branch): holding the bubble offers "Open Screenlate" after the copy items, and the menu opens toward the
  side with room (on the phone "Copy all" ran off the right edge). Two decision rows in the feedback plan.
- Cause: overlay windows use `FLAG_LAYOUT_NO_LIMITS`, so their display frame is unbounded and `PopupMenu` (a
  `PopupWindow` attached to the bubble window) never shifts or flips itself onto the screen.
- `overlay/.../ui/BubbleMenu.kt`: the menu in its own `TYPE_ACCESSIBILITY_OVERLAY` window (`OverlayWindows.menuParams`:
  not focusable, `FLAG_WATCH_OUTSIDE_TOUCH`, hardware accelerated for the shadow), styled from the DayNight theme's
  popup menu attributes; closes on `ACTION_OUTSIDE`, an item, `closeScan()` and `detachWindows()`. `MenuPlacement`
  (same file) picks the position; `MenuPlacementTest` (6 cases) passes in a plain JVM harness here.
- `OverlayController.showBubbleMenu` always shows the menu; `openApp()` starts the launch intent with
  `NEW_TASK | RESET_TASK_IF_NEEDED` and docks. String `overlay_menu_open_app` in all 14 locales (`overlay_open_app` is
  taken by "Open Anki settings").
- CI run by hand on the branch passed with both commits (build, unit tests, lint without errors, page tests):
  https://github.com/v1p3rrr/screenlate/actions/runs/36783630992, debug APK in the artifact `screenlate-debug-8914b85`.
- Not checked on a device: the menu's look (background, shadow, text), placement near each edge and corner, a tap
  outside and on the bubble closing it, "Open Screenlate" from another app (a background activity start from the
  accessibility service, as the Anki settings button already does), docking afterwards; MagicOS.
- Checked on the emulator after the cherry-pick onto main (2026-10-01): the settings list in the new order, each entry
  opening its own page; the menu below the bubble, above it at the bottom edge, ending at the bubble's right side on
  the right half; a tap outside closes it; "Open Screenlate" from the system settings brought the app's task to the
  front and docked the bubble. Fixed: the card's shadow was clipped by the root's padding (`clipToPadding`).

### 2026-10-01 (popup placement without shrinking, modal bubble menu)

- Owner's answers to the review questions since 764ae60 (requests 20 and 21 in the feedback plan). `PopupPlacement`
  no longer shrinks the height or goes beside vertical text in portrait: above when the whole popup fits, else below
  the bubble, else whole at the screen's edge on the roomier side, covering part of the word or the bubble. Landscape
  stays beside, shrinking to 80% of the width at most, then covers the same way. `Size` lost `minHeight`,
  `besideHeight`, `narrowMinWidth`; `place` lost `vertical` (and so did `PopupController.show`).
- Bubble menu: above the bubble first. The window is now focusable and touch modal (`menuParams`:
  `FLAG_ALT_FOCUSABLE_IM`, no `NOT_FOCUSABLE`/`NOT_TOUCH_MODAL`), so it gets Back and every touch on the screen; a
  touch outside the card closes it on lift and is consumed; a system gesture cancels the touch, so a back swipe closes
  only the menu. Back is handled both as `KEYCODE_BACK` and through `OnBackInvokedCallback` (API 33+).
- While the menu holds focus no application window is focused, so after Home the foreground check would see nothing;
  `focusedAppPackage` now takes the topmost application window when an accessibility overlay holds focus. Right after
  Home our window still had focus on the emulator, and the menu closed about 0.3 s later through this check.
- Checked on the emulator (API 37, gesture navigation): the menu above the bubble; Back key, back swipe (the Settings
  page under it stayed), Home key and Home swipe close it; a tap on a Settings row closes it without opening the row;
  a tap on the bubble closes it; a tap on the card's padding does not; an item works. Popup above a manga column in
  portrait and beside a line in landscape at full width; Back still ends a text selection in the popup on API 37.
- Review of these changes (`/code-review xhigh --fix`, owner's command), 5 findings, all fixed: the fallback took a
  picture-in-picture window for the app under the menu (now skipped; the choice moved to `ForegroundWindow.pick` with
  `ForegroundWindowTest`); a popup larger than the screen started above or left of it in the edge fallback below or to
  the right (the old clamp was lost; caught by the restored oversize test); centering computed once in `place`; an
  exact menu test at the top edge. Home and Back close the menu on the emulator after the change.
- Second review of these changes (owner's command), 2 findings, fixed: the modal menu stayed open when the screen
  turned off and would sit over the lock screen, taking the first unlock touch; it now closes on screen off
  (checked with sleep and wake on the emulator). `focusedAppPackage` renamed
  `foregroundAppPackage`. The emulator has no lock screen set, so the keyguard itself was not seen.
- Third review (owner's command), 1 finding, fixed: `View.onScreenStateChanged` reports off only for
  `Display.STATE_OFF`, so with an always-on display (doze) the menu stayed; it now listens for `ACTION_SCREEN_OFF`
  while open (registered in `show`, unregistered in `dismiss`). Checked with sleep, wake, reopen and Back.

### 2026-10-01 (review leftovers, bundled dictionary updates)

- Owner's request 22: fix the open findings, minimal CSS checks (the user is responsible for CSS they paste; do not
  risk breaking features), no checksum for bundled archives, but make sure an app update neither reinstalls a deleted
  bundled dictionary nor replaces a version the user imported. Answers are in the feedback plan's decision table.
- Bundled updates: `BundledDictionaries` records the title and revision of every copy it installs
  (`bundled_dictionaries_records`, backfilled from the shipped archive for older installs). A new shipped version
  replaces a copy only when the present copy is the recorded one or the shipped archive itself; any other copy with
  that title is the user's and stays, newer or older (its archive key is still recorded, so it is not asked again).
  A dictionary that is gone is installed again unless it was deleted (declined). Repair of missing files goes through
  `bundled_dictionaries_repair`, so a repaired archive is not taken for the user's copy and declined (this was a bug).
- Q12: a word kept from the on-device draft beside a cloud result shows the on-device chip; ➕ waits for the shown
  word's engine. Q13: the error of a failed whole-image pass stays; `showMessage` cancels the pending lookup.
- Minor fixes: `PopupFonts.fetch` writes new file names and deletes the replaced ones after saving the list; `import`
  reports any failure; `EInkSizes.enlarge` chains a second enlargement onto the first; `AppUpdates.update` waits for a
  running check instead of doing nothing; `LookupPage.evaluate` drops a cancelled callback; the last-dictionary rule
  ignores dictionaries without files in both helpers (callers pass `hasFiles`).
- Checked on the emulator with rebuilt APKs carrying modified Jiten archives: records backfilled; a newer shipped
  archive updated Screenlate's copy; a user's import (newer revision) stayed through an older and then the original
  shipped archive; a bundled dictionary whose folder was deleted was reinstalled with its id, position and switches.
  A folder emptied except the engine's hidden `.hoshidicts_N` marker still counts as having files (not a real case).

### 2026-10-01 (settings resets, icon tooltips)

- Owner's request 23: a reset icon at the top right of Settings with a confirmation, and a short tooltip on a long
  press of every icon-only button. Answers are in the feedback plan's decision table ("Settings reset", "Dictionary
  reset", "Tooltips").
- `SettingsReset` removes DataStore keys by name (`SettingsKeys` maps names to pages). The global reset clears every
  page plus update announcements (switch and announced tag), the e-ink offer, the background tip and the
  notification-permission flag; dictionaries, bundled bookkeeping, migrations and the app language stay. Each settings
  page (Bubble, Lookup, Anki and audio, Popup, Appearance, Background work) has its own reset; About has none. The
  bubble reset shows a hidden bubble again and moves it to the default place. Appearance goes through
  `EInkSizes.restore`; Background stops the keep-alive service. Popup replaces the pending CSS save with the default
  first, and an empty CSS now removes the key; Anki rechecks the setup after its reset (the "note type is gone" card
  stayed before).
- `DictionaryReset` (Dictionaries page): cancels import jobs by tag and waits for running workers to leave their
  body, forgets the bundled installs, deletes everything (`DictionaryRepository.deleteAll`) and queues the bundled
  archives; the page shows a progress line meanwhile and the icon is disabled.
- Tooltips: `TooltipIconButton`/`WithTooltip`/`BackButton` in `ui/components/IconButtons.kt` on back, settings, ⓘ,
  delete, download, reset, the ➕ marker button and the audio source arrows. Not in the popup (owner).
- Checked on the emulator: every page reset, the global reset (only bundled and migration keys left), the
  dictionary reset (four bundled dictionaries in the default order; a running Jitendex download cancelled; no
  temporary files left), tooltips and the Russian texts. The test environment was restored afterwards from a copy
  taken before the tests (settings byte-identical, 20 dictionaries, the user's Jiten copy 26-12-01).

### 2026-10-01 (interrupted imports and resets)

- Owner's requests 24 and 25; answers are in the feedback plan's decision table ("Interrupted dictionary reset",
  "Interrupted import") and changelog.
- `DictionaryReset.phase` (DELETING, then INSTALLING until the queued bundled install finishes,
  `DictionaryImports.awaitFinished`): the Dictionaries page shows `IconButtonProgress` (a spinner with the tooltip
  "Resetting dictionaries…") instead of the reset icon for both phases, and the progress line only while deleting.
- A reset writes `dictionary_reset_pending` into the settings DataStore before it cancels or deletes anything and
  removes it once the bundled install is queued; `ScreenlateApplication` calls `resumeInterrupted()` at start, which
  runs the reset again while the flag is set. The key is bookkeeping: the settings resets keep it (`SettingsKeysTest`).
- Imports: WorkManager already reruns a job the process died in. Leftovers are now removed by the first import job of
  each process (`DictionaryImports.firstOfProcess`) instead of by the bundled install: unregistered directories,
  staging entries older than the process, and downloaded/copied archives older than the process that no unfinished
  job reads (jobs carry the tag `dictionary-import-archive:<file name>`; an unfinished job without it, from an older
  version, falls back to the old six-hour rule). `DictionaryRepository.cleanUp` runs under the registry lock.
  Rules are in `cleanUpLeftovers` (`StorageCleanUpTest`) and `archivesInUse` (`DictionaryImportsTest`).
- Checked on the emulator: the spinner through both phases, its tooltip, the icon back after the install; `kill -9`
  0.3 s after confirming a reset with 20 dictionaries (flag set, all 20 still registered) → after the automatic
  restart only the four bundled dictionaries in their shipped versions, the flag gone, nothing in staging or
  downloads; `kill -9` during a catalog download → the job reran about 25 s later and succeeded, and the partial
  archive of the killed process was removed by the next process's first import. The environment was restored from a
  copy taken before the tests (settings identical, 20 dictionaries, the user's Jiten copy 26-12-01).

### 2026-10-01 (import errors, cancel, retry limits)

- Owner's request 26 and `/code-review xhigh --fix` over 9ed170e..HEAD; answers in the feedback plan's decision
  table ("Interrupted import", "Interrupted dictionary reset", "Import and delete errors", "Cancel an import").
- Cancel per task: `DictionaryImports.cancel` stores the id in `dictionary_imports_cancelled` and cancels the job's
  body, which runs inside `CancellableRuns` (unit tested), not the WorkManager job: cancelling that would cancel every
  job appended after it in the unique queue. The worker returns success with `cancelled`, and `importTasks` hides
  such tasks. Downloads check for the cancel before each read, collection reads in their progress callback, and
  `DictionaryRepository.import` checks before it registers (the engine's own import cannot be stopped). A cancelled
  or given-up job deletes its copied archive and releases a kept URI permission (`discardInput`).
- Retry limits: a job with `runAttemptCount >= 2` (two earlier runs the process died in) ends with `interrupted`,
  shown as a failed task with its own message. The reset stores `dictionary_reset_attempts` (replacing
  `dictionary_reset_pending`, never released); `resumeInterrupted` reruns once, then sets
  `dictionary_reset_gave_up`: a one-time dialog on the start screen (`dictionary_reset_gave_up_told`) and a card on
  Dictionaries until ✕ or a successful reset. A reset that throws shows an error card and is not rerun.
- Delete errors (Dictionaries page and the home "missing dictionary" card) no longer crash the app; the page shows a
  card. Orphan directories are listed under the registry lock and deleted outside it (`orphanDirectories`,
  `importLeftovers`, `StorageCleanUpTest`); `leftoversRemoved` is set only once the cleanup ran.
- Checked on the emulator: Cancel on a queued task (ends without running) and on a running 202 MB download (stops at
  once, the next task starts, no partial archive, the cancelled set empties); `kill -9` twice during a download →
  "Import interrupted 2 times; not started again", the failed card, the queue goes on; `dictionary_reset_attempts=2`
  written into the settings → the start dialog once, "Open dictionaries" opens the page with the card, a reset
  clears it, ✕ clears it. Network throttling (`emu network speed`) did not slow the emulator's downloads. The
  environment was restored from a copy (20 dictionaries, settings identical to the baseline).
- Translations: the six new strings in all 14 languages; German uses "du" (also fixed in the older
  `home_bubble_stopped_hint`), and the device is named with each language's existing word.

### 2026-10-01 (translation audit)

- Owner's request 27. Completeness: every key in every locale and module, format arguments and plural categories
  match (`TranslationsTest`). Then every string of all 13 translations read against English; about 250 strings
  rewritten where they were literal, ambiguous or inconsistent. Conventions and shared terms:
  `notes/translations.md`.
- Bug found and fixed: four ⓘ texts written over several lines in the XML lost their paragraph breaks (aapt2 turns a
  raw line break into a space) in all 14 locales. They use `\n` now, and `TranslationsTest` fails on a raw line break.
- Typical fixes: the bubble's "dock" became the side or edge; "replaces the same ones" for backup dictionaries became
  "replaces its installed version"; text weight and letter thickness got distinct words where they were mixed; scan
  length one name everywhere; sentences without a subject (audio sources, cloud recognition pauses); fr and it
  typographic apostrophes and quotes; pt "app"/"celular"/"Baixar"; Turkish case suffixes after placeholders; ko
  spelling (껐다가); a garbled zh-Hant "檢檢查重複複"; zh-Hant 字典 unified to 詞典. English: the backup's lookup part
  says "kanji of the word", as the setting does.
- Build, unit tests and lint clean (lint's Typos warning on Turkish "ayrı ayrı" is correct Turkish). Checked on the
  emulator in Japanese: the app-text-only ⓘ shows its two paragraphs. App language set back to system.
