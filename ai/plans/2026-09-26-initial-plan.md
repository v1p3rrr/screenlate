# Screenlate — initial plan (approved 2026-09-26)

Source of truth for scope and decisions. Small changes are edited in place and listed under "Changelog" at the bottom; a major re-plan goes into a new dated file in this folder.

## Context

Poe (`com.slimecreative.poe`) is a pop-up dictionary: a bubble is pulled out of a dock at the screen edge, aimed at a word, and a dictionary entry appears. Its drawbacks: one built-in dictionary; Anki export and usable OCR require a $10/month subscription.

Goal: a Kotlin app for the owner's phone (Honor, Android 15, MagicOS 9):
- Poe-like bubble mechanics;
- OCR through Google Lens (the same protobuf endpoint Manatan uses: `lensfrontend-pa.googleapis.com/v1/crupload`, schema from `KolbyML/chrome-lens-ocr/src/proto.rs`);
- Yomitan dictionaries, looked up and displayed the way Yomitan does it;
- free AnkiDroid export with a configurable deck, note type and field templates.

## Decisions

| Topic | Decision |
|---|---|
| Git | Separate repo (`github.com/v1p3rrr/screenlate`), commit and push after each phase |
| Languages | Code, docs, commits in English. UI: English default + `values-ru` |
| Testing | Emulator (Pixel, API 35/36, x86_64, Google Play) + the owner's phone over USB |
| DI / storage | Hilt (KSP) / Room for app data / DataStore for preferences |
| Modules | Multi-module; GPL code confined to replaceable modules |
| Lens protobuf | Hand-written codec (~100 lines), no codegen plugin |
| Dictionary engine | hoshidicts (C++, `main` branch, GPL-3.0) via our own JNI bridge, git submodule |
| Rendering | One pre-warmed WebView; Poe-like card design; entry renderer derived from Hoshi Reader Android in a separate GPL module |
| Grouping | Yomitan `group` mode: one card per term + reading, dictionaries inside in user priority order |
| Frequencies | Jiten Global (CC BY-SA) bundled and used for sorting. Importing other frequency dictionaries and picking the sort dictionary — phase 4 |
| Bundled dictionaries | Jitendex (enabled), Jiten Global (frequency), Kanjium (pitch), all three in the APK so everything works offline from the first launch. Kolobok and others via a download catalog |
| Catalog | `catalog.json` in this repo, fetched by the app from its raw GitHub URL; a copy in the APK is the offline fallback. New dictionaries are added by editing the JSON, no app release needed. Entries carry source and target language |
| Language grouping | Catalog: source language → target language (ja→ru, ja→en, ja→ja; later en→ru, en→en, …) for term dictionaries; frequency, pitch and kanji dictionaries as separate subsections of their source language. Installed list: sections by type (dictionaries, frequency, pitch, kanji), drag order within a section, language pair label on each dictionary. No switch for active target languages: enable/disable dictionaries instead |
| Own dictionaries | Import of Yomitan zip archives from a file (done). Import of a Yomitan database export (the dictionaries themselves) — phase 4, verified and optimized with a real 2.7 GB export in phase 5 |
| Catalog content | Only dictionaries with a free license and an official, stable download URL. Scrapes of commercial dictionaries are not linked; users import them from files |
| Yomitan settings import | Phase 5. Imports from one profile (chosen at import, the current one preselected): dictionary order, enabled state and sort dictionary (matched by title); Anki deck, note type, field templates with per-field overwrite modes, tags, duplicate settings; audio sources (localhost sources skipped), auto-play, volume; scan length, max results, text replacements. Every imported setting is also editable in the app |
| Kanji dictionaries | Phase 4. hoshidicts imports and queries kanji banks, so no own Room import (changed 2026-09-26, see changelog) |
| Cross-references | Lookup inside the popup with a back stack; external links open in the browser |
| OCR | One image ≤1500 px, JPEG. ML Kit Japanese (bundled model) runs in parallel as a draft; the Lens result replaces it immediately, a small spinner shows until then |
| Small text | Lens skips small text in a full-screen image but reads it in a crop. The screen is split into overlapping full-width bands; when the aim rests ~0.3 s where nothing was recognized, that band goes to Lens once per scan and its lines fill the gaps. Bubble setting: off / on demand (default) / all bands with every scan |
| When to OCR | Only when the bubble is pulled out of the dock and on a single tap on the bubble. Further aiming reuses the result even if the screen changed |
| Proxy | None, system network (and VPN if on) |
| Slow network | Lens timeout 15 s, then the ML Kit result becomes final. Anki ➕ waits for Lens |
| Popup timing | Shown immediately while hovering, updated live |
| Bubble gestures | Double tap toggles the aim point (dot ~48dp above the finger ↔ bubble center), remembered. Single tap rescans and highlights all recognized words; the highlight fades after 2–3 s |
| Closing | Bubble back into the dock, or ✕ (closes the popup and docks the bubble). Touches elsewhere pass through to the app |
| Dock | Right edge by default, can be moved to the left, height remembered. The bubble docks only when dragged to the very edge (a few dp), so words at the edge stay reachable. Bubble 48dp by default with a size setting; the docked handle sticks out less |
| Visibility | Quick Settings tile (phase 1). Hide in selected apps (phase 4) |
| Popup placement | As in Poe: above the word, or below the bubble, never covering it; the bubble window always stays above the popup. ~85% width (≤420dp) × up to ~35% height, shrinking to the free space down to a minimum, scrollable |
| Misc | Word highlight on. Haptics off (configurable). Theme follows system with an override |
| Anki | Official AnkiDroid API (LGPL-3.0, JitPack). Duplicate settings as in Yomitan: check on, scope collection/deck/deck-root (default collection), check all models off, behavior prevent/overwrite/new (default prevent, changed in phase 5). With prevent a duplicate shows 📖 instead of ➕: tap opens the existing note in AnkiDroid, long press adds anyway. A word added during the current scan shows 📖 until the bubble is docked. Overwrite uses Yomitan's per-field overwrite modes |
| Anki fields | Yomitan's full marker set with Yomitan's output format (pitch categories, harmonic/average frequency, sentence furigana, dynamic `single-glossary-*` / `single-frequency-number-*`, …). Auto-mapping: Yomitan's whole-name + alias rule plus presets for known note types (Senren, Lapis, Kaishi, JPMN). One active note type; field templates are remembered per note type |
| Anki picture | Short tap ➕: note without a picture. Long tap: crop editor (clean screenshot, frame around the paragraph, "whole screen" button) → note with the picture |
| Audio | In the Anki phase: 🔊 and `{audio}`. Sources as in Yomitan: JapanesePod101 by default, custom URL templates and custom JSON, several in priority order. Auto-play of new words off. Phase 5: sources edited in a dialog with Save and Test; a test panel in the settings (test word, play per source); long press on 🔊 lists sources and their clips, the chosen clip goes into `{audio}`; auto-play waits until the aim rests ~0.5 s on a word or the finger is lifted; volume setting |
| Search settings | Scan length, max results and Yomitan-style text replacements (regex groups, original text searched too), editable in the app |
| UI language | In-app picker (system / English / Russian) and Android's per-app language setting (`localeConfig`) |
| Search screen | Called "Look up words"; an empty state with a hint instead of a blank page |
| Later, not scheduled | GitHub Actions pipeline that publishes APKs, per-ABI/device builds, a glyph on the docked handle showing the target language. Interview before starting |
| Text without OCR | Phase 4 (accessibility node tree): a "text source" setting, OCR by default, "app text first" falls back to OCR |
| Languages | Japanese only, but language is a parameter in every layer |
| Extra features (phase 4) | Search screen, "Look up in Screenlate" in the text selection menu (`PROCESS_TEXT`), dictionary update check via `indexUrl` |
| Builds | Debug (`applicationIdSuffix ".debug"`) + release signed with an own key (`keystore.properties`, not in git); installable side by side |
| Phase order | Overlay + OCR → dictionaries → Anki → polish |
| UI rule | Everything that may not fit (popup, screens, lists, crop editor) scrolls; long text wraps |
| License | GPL-3.0 for now. GPL code lives only in `:dictionary:engine-hoshidicts` and `:dictionary:render-yomitan`; replacing them (e.g. hoshidicts MIT branch and an own renderer) removes GPL without touching the rest |

## Architecture

### Modules

```
:app                          Application (Hilt), MainActivity, Compose screens (onboarding, dictionaries, settings, Anki, OCR debug), navigation
:overlay                      AccessibilityService, dock/bubble/aim/highlight, gesture controller, popup window (WebView shell), crop editor, QS tile
:core:common                  Shared models (TextBox, OcrPage, Language), errors, settings (DataStore)
:core:ocr                     OcrEngine interface, LensOcrEngine (OkHttp + hand-written protobuf), MlKitOcrEngine, CompositeOcr (draft → final), TextHitTester
:core:anki                    AnkiRepository (AddContentApi), NoteBuilder (markers), duplicate settings, AudioSources
:dictionary:api               DictionaryEngine interface, LookupResult/TermEntry/Glossary/Frequency/Pitch models, YomitanSorter, registry (Room), catalog, import (WorkManager)
:dictionary:engine-hoshidicts GPL: hoshidicts submodule + own JNI (CMake, C++23) + DictionaryEngine implementation
:dictionary:render-yomitan    GPL: renderer JS/CSS assets (from Hoshi Reader Android) exposing window.YomitanRender
```

Build configuration is shared through convention plugins in `build-logic/`.

### Android APIs

- AccessibilityService:
  - `takeScreenshotOfWindow` (API 34+) captures the app window below our overlays, so the bubble and popup never appear in the image;
  - fallback: `takeScreenshot` with overlays made transparent for one frame;
  - `TYPE_ACCESSIBILITY_OVERLAY` windows (no SYSTEM_ALERT_WINDOW);
  - `layoutInDisplayCutoutMode = ALWAYS`.
- Also: `TileService`, `FileProvider` + `grantUriPermission("com.ichi2.anki")`, `<queries>` for `com.ichi2.anki`, SAF import, WorkManager (foreground, `dataSync`) for import and downloads, `ConnectivityManager`.

### 1. Overlay and gestures (`:overlay`)

States: `Docked → Dragging → Floating(+Popup)`.

- Pulling out of the dock: capture the window, run ML Kit and Lens in parallel; aiming works on the ML Kit draft right away and switches to Lens when it arrives.
- Dragging the floating bubble: reuses the same OCR result, no new requests.
- Single tap (after a ~300 ms double-tap window): new capture + OCR and a highlight of all recognized words that fades after 2–3 s.
- Double tap: toggles the aim mode.
- The popup stays after the finger is lifted. Moving the bubble into the dock zone or tapping ✕ closes the popup and docks the bubble.
- Touches outside the bubble and popup pass through: windows use `FLAG_NOT_TOUCH_MODAL`, the highlight layer `FLAG_NOT_TOUCHABLE`.
- Hit testing runs on every `ACTION_MOVE` (~60 ms debounce); matched characters are highlighted.

### 2. OCR (`:core:ocr`)

- Lens:
  - downscale to ≤1500 px, JPEG (~q85);
  - `LensOverlayServerRequest` (platform WEB = 3, surface CHROMIUM = 4, language `ja`);
  - parse `paragraphs → lines → words` with normalized boxes;
  - 15 s timeout;
  - phase 1 experiment: does Lens accept full resolution? If not and small text suffers, add a refinement request with a crop around the aim point.
- ML Kit (`com.google.mlkit:text-recognition-japanese`, bundled model) produces the draft. `CompositeOcr` emits `Draft → Final`.
- TextHitTester:
  - splits word boxes into character boxes along the main axis; vertical text is detected by `rotation_z ≈ ±π/2` or height > width;
  - picks the character under the aim point or the nearest one within a tolerance;
  - lookup text is up to 16 characters along the line and paragraph (Yomitan's `scanLength`);
  - the Anki sentence is the paragraph cut at `。！？`, respecting `「」`.

### 3. Dictionaries (`:dictionary:*`)

- `DictionaryEngine`: `import(zip) / lookup(text, options) / styles() / media(dict, path) / rebuildQuery(enabled, ordered)`.
- hoshidicts:
  - groups by (expression, reading) and sorts by primary reading → match length → preprocessing steps → deinflection chain → frequency;
  - `YomitanSorter` (Kotlin) adds the missing Yomitan tie-breakers: exact match, dictionary order;
  - our JNI exposes options: frequency dictionary and order, `primary_reading`, `scanLength`, `maxResults`.
- Registry (Room): `dictionaries(id, title, revision, kind term|freq|pitch|kanji, sourceLanguage, targetLanguage, enabled, priority, path, indexUrl, downloadUrl, bundled, importedAt)`, plus the "sort dictionary" setting.
- First run: import bundled Jitendex, Jiten Global and Kanjium from assets with progress. Bundled dictionaries are downloaded at build time, not committed to git.
- Catalog of recommended dictionaries: `catalog/dictionaries.json` in the repo (fetched from GitHub, bundled copy as fallback) with `indexUrl`, `downloadUrl`, kind and language pair. Starts with Kolobok (ja-ru) and the bundled dictionaries; new entries are one record each.
- Dictionaries screen: import from file, download from the catalog grouped by language pair, installed dictionaries in sections by type with drag to reorder inside a section, enable/disable, delete.

### 4. Rendering and popup (`:dictionary:render-yomitan`, `:overlay`)

- Extracted from Hoshi Reader Android `popup.js` (GPL, with copyright headers): `renderStructuredContent`, `createDefinitionImage` (AVIF/SVG, `sizeUnits`, monochrome glyphs, collapsible), `constructDictCss` (per-dictionary `styles.css` scoping), furigana segmentation, pitch accent graph.
- Module API: `YomitanRender.renderGlossary / dictCss / furigana / pitchGraph`.
- Our shell `assets/popup/`: Poe-like card — header (ruby, frequencies, pitch, deinflection, tags), dictionary blocks, ➕ / 🔊 / copy buttons, back stack for links, Lens loading indicator. Yomitan CSS variables `--font-size-no-units`, `--text-color`, `--fg` are defined for both themes.
- The WebView is created ahead of time; media is served through `WebViewAssetLoader`; Kotlin bridge through `@JavascriptInterface`.

### 5. Anki and audio (`:core:anki`)

- Settings: deck, note type, a template per field with markers `{expression} {reading} {furigana} {furigana-plain} {glossary} {glossary-first} {glossary-<dict>} {sentence} {cloze-prefix} {cloze-body} {cloze-suffix} {pitch-accents} {frequencies} {part-of-speech} {tags} {dictionary} {screenshot} {audio}`, tags, duplicate settings.
- ➕: short tap — note without a picture; long tap — crop editor, then note with the picture; if Lens is still pending, wait for the final OCR.
- Audio: list of sources (JapanesePod101 with the placeholder filtered by hash, URL templates with `{term}` / `{reading}`, custom JSON), 🔊 in the popup, auto-play (off by default).

## Phases

0. **Infrastructure**: separate repo, `.gitignore`, module skeleton, convention plugins, Hilt, Room, KSP, DataStore, en/ru, debug/release signing, onboarding (service status, accessibility settings, MagicOS tips), LICENSE (GPL-3.0), NOTICE, README, CLAUDE.md, `ai/` notes.
1. **Overlay + OCR**: service, dock, bubble, aim, gestures, window capture; Lens (codec, client, resolution experiment) + ML Kit + `CompositeOcr`; hit testing, highlight; popup shell showing the recognized line and the word under the aim (no dictionaries yet); QS tile; OcrTest debug screen.
2. **Dictionaries**: hoshidicts (submodule, CMake, JNI; ABIs arm64-v8a + x86_64); `DictionaryEngine`, `YomitanSorter`, Room registry, bundled + file import, catalog (Kolobok); dictionaries screen; renderer module and full cards in the popup, links with back navigation. Result: the full Poe scenario.
3. **Anki + audio**: field mapping settings, ➕ short/long, crop editor, duplicates, audio sources.
4. **Polish**: search screen, `PROCESS_TEXT`, dictionary update check, frequency dictionary import and sort dictionary choice, kanji dictionaries, accessibility-text mode, hide in selected apps, import of a Yomitan database export, Yomitan settings backup (interview first).
5. **Phone feedback** (first test on the owner's phone):
   - bubble: small dock zone, smaller bubble with a size setting, smaller docked handle, Poe-like popup placement, bubble above the popup;
   - OCR: on-demand band refinement for small text;
   - Anki: Yomitan marker set and formats (fixes broken Senren cards), Yomitan auto-mapping + note type presets, templates per note type, 📖 after adding and for prevented duplicates (default prevent), per-field overwrite modes;
   - audio: source dialog, test panel, source/clip choice on long press, auto-play debounce, volume;
   - settings screens: tap outside clears focus, fields stay above the keyboard; search settings (scan length, max results, text replacements);
   - UI language picker; search screen name and empty state;
   - Yomitan settings import; real Yomitan database export verified and import sped up;
   - catalog: free dictionaries from the owner's collection;
   - review of screen-size and device assumptions (density, font scale, landscape, tablets, cutouts, navigation modes).
6. **Later** (interview first): APK publishing pipeline on GitHub, per-ABI builds, dock glyph per language.

## Documentation tasks

- README: keep build steps, requirements and module table current; add a short usage section once phase 2 lands.
- NOTICE: add every third-party code component and bundled dictionary with its license when it is added.
- `docs/architecture.md` (phase 2): module graph, OCR → hit test → lookup → render flow, threading.
- `docs/usage.md` (phase 4): gestures, settings, Anki setup, troubleshooting on MagicOS.
- `ai/status.md`: updated at the end of every work session.
- KDoc: public APIs of `:dictionary:api`, `:core:ocr`, `:core:anki` and non-obvious behavior; no restating of names.

## Risks

- Lens is an unofficial endpoint and may break or get rate-limited; ML Kit and the `OcrEngine` interface mitigate it.
- Hilt with AGP 9: 2.59 had a build bug; using the latest version (2.60.1 works), Koin as a fallback.
- C++23 build through the NDK (hoshidicts with nested submodules): build time, native crash debugging, may need a newer NDK than r26.
- OS limits: `FLAG_SECURE` windows are black on screenshots; MagicOS needs "App launch → Manage manually"; APKs installed from files need "Allow restricted settings".
- Licenses: GPL-3.0 (hoshidicts, Yomitan rules, Hoshi renderer); CC BY-SA 4.0 (Jitendex, Kolobok, Jiten, Kanjium) — attribution in NOTICE and an About screen.

## Verification

- `./gradlew assembleDebug testDebugUnitTest` (JDK 21 at `C:\Program Files\Java\jdk-21`, SDK at `D:\Android\Sdk`).
- Unit tests: Lens protobuf codec (encode, parse a saved response), `TextHitTester` (horizontal and vertical), `YomitanSorter`, `NoteBuilder`, catalog parsing.
- Instrumented tests: JNI import of a mini dictionary; lookup 食べさせられなかった → 食べる; rendering Jitendex and Kolobok fixtures.
- Emulator via adb: `adb install -r`, `adb logcat`, `adb exec-out screencap -p`, enabling the service, gestures via `adb shell input swipe/tap`, OCR on test images.
- Phone: X, Chrome, NHK, vertical novel, manga, paused video; targets: draft ≤0.5 s, Lens ≤1.5–3 s; AnkiDroid note with correct fields, picture and audio.

## Changelog

- 2026-09-26: plan approved. Phase 0 added README/CLAUDE.md/`ai/` docs and the documentation tasks section at the owner's request.
- 2026-09-26: owner decisions during the dictionaries work — catalog fetched from the repo with a bundled fallback; catalog grouped by source → target language, installed dictionaries in sections by type; no active-language switch; Yomitan database export import and settings backup added to phase 4. Bundled set unchanged (all three stay in the APK).
- 2026-09-26: implementation notes, no change of intent — kanji dictionaries use hoshidicts' kanji support instead of an own Room import (the earlier finding that hoshidicts had no kanji support was wrong); text without OCR is a bubble setting with OCR as the default. The owner may still want to confirm the default.
- 2026-09-27: phase 5 from the first phone test. Owner decisions: small text via on-demand bands (setting off/on demand/all); duplicate behavior default changed from new to prevent, with 📖 opening the note; one active note type with field templates remembered per note type (no Yomitan-style multiple card formats); catalog limited to freely licensed dictionaries, the rest imported from files; Yomitan settings import covers dictionaries, Anki, audio and search settings, all editable by hand as well. CI publishing and per-device builds moved to a later phase.
