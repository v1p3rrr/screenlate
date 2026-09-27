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
| Bundled dictionaries | JMdict (English, enabled), Jiten Global (frequency), Kanjium (pitch), all three in the APK so everything works offline from the first launch. Jitendex moved from the APK to the catalog to shrink the APK by ~23 MB (owner, 2026-09-27); installs that already have it keep it. Kolobok and others via a download catalog |
| Catalog | `catalog.json` in this repo, fetched by the app from its raw GitHub URL; a copy in the APK is the offline fallback. New dictionaries are added by editing the JSON, no app release needed. Entries carry source and target language |
| Language grouping | Catalog: source language → target language (ja→ru, ja→en, ja→ja; later en→ru, en→en, …) for term dictionaries; frequency, pitch and kanji dictionaries as separate subsections of their source language. Installed list: sections by type (dictionaries, frequency, pitch, kanji), drag order within a section, language pair label on each dictionary. No switch for active target languages: enable/disable dictionaries instead |
| Own dictionaries | Import of Yomitan zip archives from a file (done). Import of a Yomitan database export (the dictionaries themselves) — phase 4, verified and optimized with a real 2.7 GB export in phase 5 |
| Catalog order | Term dictionaries of a source language by target language: the interface language first, then English, then the source language itself (monolingual), then Russian, Spanish, French, German, then the rest alphabetically by name (owner, 2026-09-27). Warodai (CC BY-NC-ND, no official Yomitan build) and Kenrowa (commercial) stay file imports; Kolobok (CC BY-SA 4.0, derived from Jitendex) stays in the catalog |
| Catalog content | Only dictionaries with a free license and an official, stable download URL. Scrapes of commercial dictionaries are not linked; users import them from files. Frequency lists (usage statistics) go in when their authors publish them at a stable URL, even without an explicit license |
| Yomitan settings import | Phase 5. Imports from one profile (chosen at import, the current one preselected): dictionary order, enabled state and sort dictionary (matched by title); Anki deck, note type, field templates with per-field overwrite modes, tags, duplicate settings; audio sources (localhost sources imported as they are), auto-play, volume; scan length, max results; popup font size and custom popup CSS (font names it uses that the phone lacks fall back to the default Japanese font). The font family and text replacements are not imported (Windows fonts do not exist on Android; replacements are built in, see Search settings). Every imported setting is also editable in the app |
| Kanji dictionaries | Phase 4. hoshidicts imports and queries kanji banks, so no own Room import (changed 2026-09-26, see changelog) |
| Cross-references | Lookup inside the popup with a back stack; external links open in the browser |
| OCR | One image ≤1500 px, JPEG. ML Kit Japanese (bundled model) runs in parallel as a draft; the Lens result replaces it immediately, a small spinner shows until then |
| Small text | Lens skips small text in a full-screen image but reads it in a crop. The screen is split into overlapping full-width bands; when the aim rests ~0.3 s where nothing was recognized, that band goes to Lens once per scan and its lines fill the gaps. Bubble setting: off (default) / on demand / all bands with every scan. The owner's original phone screenshots showed Lens reads most small text on the full screen; the missing text in X came from "app text first" skipping OCR, so bands stay an opt-in extra |
| When to OCR | Only when the bubble is pulled out of the dock and on a single tap on the bubble. Further aiming reuses the result even if the screen changed |
| Proxy | None, system network (and VPN if on) |
| Slow network | Lens timeout 15 s, then the ML Kit result becomes final. Anki ➕ waits for Lens |
| Popup timing | Shown immediately while hovering, updated live |
| Bubble gestures | Double tap toggles the aim point (dot ~48dp above the finger ↔ bubble center), remembered. Single tap rescans and highlights all recognized words; the highlight fades after 2–3 s |
| Closing | Bubble back into the dock, or ✕ (closes the popup and docks the bubble). Touches elsewhere pass through to the app |
| Dock | Right edge by default, can be moved to the left, height remembered. The bubble docks only when dragged to the very edge (a few dp), so words at the edge stay reachable. Bubble 48dp by default with a size setting; the docked handle sticks out less |
| Visibility | Quick Settings tile (phase 1). Hide in selected apps (phase 4) |
| Popup header | Setting "Show the recognized text above the entries" (Bubble settings), on by default. Off: the popup starts with the first entry; ✕ moves into the first entry's button row. The header still appears after following a link, since it holds the back button (owner request 2026-09-27; placement and the link case decided by the agent) |
| Popup placement | As in Poe: above the word, or below the bubble, never covering it; the bubble window always stays above the popup. ~85% width (≤420dp) × up to ~35% height, shrinking to the free space down to a minimum, scrollable |
| Misc | Word highlight on. Haptics off (configurable). Theme follows system with an override |
| Anki | Official AnkiDroid API (LGPL-3.0, JitPack). Duplicate settings as in Yomitan: check on, scope collection/deck/deck-root (default collection), check all models off, behavior prevent/overwrite/new (default prevent, changed in phase 5). With prevent a duplicate shows 📖 instead of ➕: tap opens the existing note in AnkiDroid, long press adds anyway. A word added during the current scan shows 📖 until the bubble is docked. Overwrite uses Yomitan's per-field overwrite modes |
| Anki fields | Yomitan's full marker set with Yomitan's output format (pitch categories, harmonic/average frequency, sentence furigana, dynamic `single-glossary-*` / `single-frequency-number-*`, …). Auto-mapping: Yomitan's whole-name + alias rule plus presets for known note types (Senren, Lapis, Kaishi, JPMN). One active note type; field templates are remembered per note type |
| Anki picture | Short tap ➕: note without a picture. Long tap: crop editor (clean screenshot, frame around the paragraph, "whole screen" button) → note with the picture |
| Audio | In the Anki phase: 🔊 and `{audio}`. Sources as in Yomitan: JapanesePod101 by default, custom URL templates and custom JSON, several in priority order. Auto-play of new words off. Phase 5: sources edited in a dialog with Save and Test; a test panel in the settings (test word, play per source); long press on 🔊 lists sources and their clips, the chosen clip goes into `{audio}`; auto-play waits until the aim rests ~0.5 s on a word or the finger is lifted; volume setting |
| Search settings | Scan length and max results, editable in the app. No text replacement settings (owner, 2026-09-27): a Japanese lookup also searches one built-in variant made by the owner's Yomitan rules in order (1/１…9/９ → 一…九, (.)々 → the character doubled, ッ → っ, ・、-. and whitespace removed); the original text is always searched. Romaji is converted to hiragana like Yomitan's `alphabeticToHiragana` (the only Yomitan Japanese preprocessor hoshidicts lacks); matched lengths map back to the original characters |
| UI language | In-app picker (system / English / Russian) and Android's per-app language setting (`localeConfig`) |
| Search screen | Called "Look up words"; an empty state with a hint instead of a blank page |
| Broken setup warnings | When something breaks without the user changing our settings (AnkiDroid removed, permission revoked, note type or deck gone, fields changed; accessibility service turned off; dictionary files missing; an audio source failing), ➕ turns grey instead of disappearing; tapping it shows a very short reason and an "Open Screenlate" button that leads to the details and the fix. The home screen shows the same problems |
| Settings placement | The home screen keeps search, service status and warnings; everything else moves to one "Settings" screen with sections: Bubble, Lookup (scan length, max results, romaji), Dictionaries, Anki and audio, Appearance, Import from Yomitan (profile picker, summary of what was applied and skipped), About. Audio test word: 読む |
| Popup font and CSS | Pages are marked with the source language, and by default the popup asks for the phone's Japanese system font explicitly (Noto Sans CJK JP by its local name), so kanji never take Chinese forms (check: 置く, 直) even when the phone's main font covers Chinese. Windows Japanese font names (Meiryo, Yu Gothic, MS Gothic → sans; Yu Mincho, MS Mincho → serif) resolve to the phone's Japanese fonts. Appearance: font — system, free Japanese fonts downloaded on demand (Noto Sans JP, Noto Serif JP, BIZ UDPGothic, BIZ UDPMincho, Klee One, Zen Maru Gothic, M PLUS 1p; SIL OFL, from the Google Fonts repository), or own .ttf/.otf/.ttc files; font size; a hint to download Noto Sans JP when the phone has no Japanese system font; one custom CSS field applied after dictionary styles in the popup and the search page (Yomitan class names and tag color variables work). Above the CSS field, warnings for syntax errors (with the line) and for font names the phone does not have (owner request). Proprietary fonts are never bundled or offered for download |
| Romaji | "Look up romaji as Japanese" converts Latin text for the lookup only (the search field keeps what was typed); it applies to OCR and search alike and is off by default |
| Audio sources | Yomitan's full set: JapanesePod101, LanguagePod101, Jisho, Lingua Libre, Wiktionary, Android text-to-speech (played only, never put into notes), custom URL, custom JSON. Defaults as in Yomitan: JapanesePod101, LanguagePod101, Jisho. Implemented from the services' behavior, not copied from Yomitan's GPL code |
| Collection import | A Yomitan dictionary collection export is scanned first and shown as a checklist with sizes; already installed dictionaries start unchecked; only checked ones are imported |
| Orientation | Phones in both orientations; no separate tablet layouts. Rotation closes the popup and docks the bubble; the dock keeps its side and relative height |
| Tests environment | Instrumented tests use a fake OCR engine and small test dictionaries so they need no network. Anki tests need AnkiDroid and are skipped without it; they create their note types through the AnkiDroid API. On the emulator AnkiDroid is reset to an empty collection without an account, after backing up the owner's collection (the customized Senren note type) to `testdata/` (not in git). CI runs them in phase 6 (GitHub Actions emulator runner, AnkiDroid installed from its releases) |
| Single kanji entries | As in Poe: below the results, the other kanji of the longest match (not the first one, which the results already cover) get their own entries when a term dictionary has them as words; duplicates of shown entries are skipped. Lookup setting, on by default |
| Kanji notes | Not planned (no ➕ on kanji entries); owner confirmed |
| Anki templates after updates | No automatic migration of saved templates; "Fill in suggested templates" re-applies the defaults |
| Max results | 32 by default (Yomitan's default), a setting with a performance warning for large values or no limit; measure rendering time before settling |
| Missing dictionary files | Bundled dictionaries are reinstalled from the APK automatically, catalog dictionaries offer a one-tap re-download, file imports only warn; always with a warning |
| Wording about the cloud OCR | Human docs and descriptive texts (README, docs/, About, settings hints, the accessibility service description) do not mention Google Lens and say "cloud recognition". The popup's source chip keeps "Lens" (owner, 2026-09-27). Code and ai/ notes keep technical names; NOTICE keeps the MIT attribution of chrome-lens-ocr in neutral wording |
| CI (phase 6) | GitHub Actions. Every push to main: build, unit tests, lint, debug APK artifact (only the last two or three kept). Tag vX.Y.Z: release APKs per ABI (arm64-v8a, x86_64, armeabi-v7a if the native engine builds and tests pass for 32-bit) plus a universal APK, a full source archive including submodules, changelog, published to GitHub Releases. Instrumented emulator tests (AnkiDroid installed from its releases) on tags and on manual runs |
| Signing | Debug key until the rest is done; then the owner creates a release key and stores it in GitHub Secrets. The owner runs keytool and uploads the secrets by hand from step-by-step instructions; the certificate says only `CN=Screenlate` (owner, 2026-09-27) |
| First release | v0.1.0 with short hand-written notes (8–10 points on what the app does); later releases list their commits as before (owner, 2026-09-27) |
| Dock glyph (phase 6) | A characteristic letter of the target language on the docked handle: あ for Japanese, Я for Russian, and so on; none on the floating bubble |
| About screen (phase 7) | Version, GitHub link (sources, bug reports), GPL-3.0, generated list of libraries with licenses, attribution of every installed dictionary from its index.json; no mention of the cloud OCR provider. "Share logs" button: a log file of this app only, with app version and device model, shared through the system share sheet |
| Logs | Logs never contain recognized text, looked-up words, note contents or URLs with terms (redacted); only events, errors, timings, engine states |
| Versioning | Semantic version from the git tag vX.Y.Z; versionCode = X·10000 + Y·100 + Z; 0.x until the first stable release; debug builds append the short commit hash |
| Distribution | GitHub Releases (also usable with Obtainium), plus in-app updates (owner, 2026-09-27): release builds check the latest stable GitHub release at most once a day when the app is opened (no prereleases, no background work, no system notifications). A new version is announced once, with its changelog, as a card on the home screen; dismissing it skips that version, and the next release is announced again. Never blocking: the current version keeps working. About shows the available version and a "Check for updates" button at any time. A setting turns the announcements off. "Update" downloads the APK for the device's ABI (universal as fallback) with progress, checks package name, version and signing certificate, and installs it through PackageInstaller: without confirmation where Android allows it (Android 12+, once Screenlate installed the current version itself), otherwise the system's install prompt; "install unknown apps" is requested for Screenlate on first use |
| More languages (phase 8) | English, Chinese, Korean, and a generic approach for alphabetic languages (Yomitan covers ~60 languages, 20 with deinflection rules). Needs per-language text processing and deinflection outside hoshidicts' Japanese rules, OCR script support (ML Kit covers Latin, Chinese, Devanagari, Japanese, Korean; the cloud OCR more), right-to-left hit testing for Arabic and Hebrew. Details per language in an interview before the phase |
| Text without OCR | Phase 4 (accessibility node tree): a "text source" setting, OCR by default, "app text first" falls back to OCR |
| Languages | Japanese only, but language is a parameter in every layer |
| Extra features (phase 4) | Search screen, "Look up in Screenlate" in the text selection menu (`PROCESS_TEXT`), dictionary update check via `indexUrl` |
| Builds | Debug (`applicationIdSuffix ".debug"`) + release signed with an own key (`keystore.properties`, not in git); installable side by side |
| Phase order | Overlay + OCR → dictionaries → Anki → polish |
| UI rule | Everything that may not fit (popup, screens, lists, crop editor) scrolls; long text wraps. Details the user does not need by default (dictionary licenses and descriptions, release notes, long explanations of a setting) sit behind an ⓘ icon or a separate button and open in a dialog; screens show a short line at most (owner, 2026-09-27) |
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
5. **Phone feedback** (first test on the owner's phone). Done: bubble and popup placement, bubble size, dock zone; OCR boost bands (off by default), app text + OCR, Lens pause after refusals; UI language picker; search screen name and empty state; no popup without results; Yomitan marker set, presets, templates per note type, 📖, overwrite modes, clip menu, volume. Remaining:
   - broken-setup warnings: grey ➕ with a short reason and "Open Screenlate"; the same problems on the home screen; missing dictionaries restored or re-downloaded;
   - one Settings screen (Bubble, Lookup, Dictionaries, Anki and audio, Appearance, Import from Yomitan, About); home keeps search, service status and warnings;
   - Anki screen: source dialog with Save/Test, audio test panel; Yomitan's full audio source set (LanguagePod101, Jisho, Lingua Libre, Wiktionary, Android TTS) with Yomitan's defaults;
   - lookup settings: scan length, max results (32, warning for more), text replacements (empty by default), romaji switch, single kanji entries (on); lookup-worthiness (Latin text skipped unless Japanese follows);
   - language audit: everything language-specific behind one `LanguageSupport` per language (lookup-worthiness, romanization, sentence rules, OCR recognizer, default audio sources, language-only Anki markers), with no Japanese assumptions elsewhere;
   - Yomitan settings import (profile picker, summary); dictionary collection import with a checklist, verified with the owner's 2.7 GB export and sped up;
   - catalog: free dictionaries and author-published frequency lists from the owner's collection;
   - both orientations on phones: check popup sizes and insets in landscape; review density, font scale, cutouts, navigation modes;
   - logs without user text (existing logs cleaned up);
   - tests: unit tests for new logic as it lands; then coverage of older untested parts (AnkiNotes with a fake AnkiDroid, AudioFinder with a mock server, CompositeOcr with fake engines, the page scripts note.js/anki.js/popup.js) and instrumented tests (popup WebView, AnkiDroid round trip with a note type created through the API) with a fake OCR engine and small test dictionaries;
   - not planned: Poe's dot that pops out of the handle when undocking (cosmetic, owner agreed to skip); kanji notes.
6. **Build and publishing** (after phase 5): GitHub Actions per the CI row, per-ABI and universal APKs, source archive with submodules, dock glyph per language; release key set up with the owner at the end.
7. **Release readiness**: About screen with licenses, dictionary attribution and "Share logs"; versioning from tags; in-app update check against GitHub Releases; wording pass that removes the cloud OCR provider from user-facing texts and docs (may be done earlier).
   - later (owner's questions of 2026-09-27): faster collection conversion (byte-level parsing without decoding everything, uncompressed intermediate archives; compare output with the current converter on the owner's export before switching); e-ink readers on Android — likely to work (Android 11+, no Google services needed; 32-bit build from the CI row), an "e-ink mode" (grayscale high-contrast theme, no animations or fading) is an open question for the owner.
   - later (owner request 2026-09-27): backup and restore — settings exported in Screenlate's own versioned format (not Yomitan-compatible: Yomitan validates its settings schema and ours has more), restored in Screenlate; dictionaries as a Screenlate backup of the converted files (fast, Screenlate only) — recommended. A Yomitan-compatible dictionary export would need the original archives kept (about twice the storage): open question for the owner.
8. **More languages** (interview per language first): English, Chinese, Korean, generic alphabetic languages.

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
- 2026-09-27: small-text bands default to off (owner's call after the full-resolution screenshots showed Lens reads most small text; the real cause was "app text first" skipping OCR).
- 2026-09-27: owner decisions for the rest of phase 5 — broken-setup warnings (grey ➕ with a short reason and an "Open Screenlate" button); AnkiDroid on the emulator reset after a backup of the owner's collection; frequency lists in the catalog when published by their authors; localhost audio sources imported as they are. Settings placement, test environment and kanji notes decided by the agent (see the decision table).
- 2026-09-27: owner answers on the remaining points — one Settings screen; romaji for lookups everywhere (off by default); Yomitan's full audio source set; text replacements empty by default; checklist for collection imports; both orientations without per-orientation memory (rotation docks); no kanji notes.
- 2026-09-27: owner request — single kanji entries of the matched word below the results, as in Poe (setting, on by default).
- 2026-09-27: owner request — export of dictionaries and settings (backup/restore); recorded under phase 7 with a recommendation, Yomitan-compatible dictionary export is an open question.
- 2026-09-27: owner questions — speeding up dictionary imports further and running on Android e-ink readers; recorded under phase 7 as later items, the e-ink mode needs the owner's answer.
- 2026-09-27: owner request — a setting to hide the popup's recognized-text header (on by default); ✕ moves down to the first entry.
- 2026-09-27: owner correction — the popup's source chip may keep saying "Lens"; only docs and descriptive texts avoid the name.
- 2026-09-27: owner answers for phases 5–8 — templates re-applied by the button only; max results 32 with a setting; missing dictionaries restored or re-downloaded with a warning; no mention of Google Lens in user-facing texts and docs; CI, signing, ABIs, dock glyph, About screen, logs, versioning, distribution and languages as in the decision table.
- 2026-09-27: owner decisions — text replacement settings removed, the owner's Yomitan rules are built into Japanese lookups instead; popup font (system Japanese by default, downloadable free fonts, own files, size) and one custom CSS field with warnings for broken CSS and missing fonts; the Yomitan import brings the custom popup CSS and font size, not the font family or replacements.
- 2026-09-27: owner request — in-app updates from GitHub Releases: announcement card once per version (skippable, no system notifications, setting to turn off), download on "Update", silent install where Android allows, manual check in About; stable releases only, checked at most daily.
- 2026-09-27: owner request — no walls of text: dictionary licenses in About, release notes ("What's new") and long setting explanations open from an ⓘ icon or a separate button.
- 2026-09-27: owner request — create the release key and publish the first release: v0.1.0, key created by the owner from step-by-step instructions, `CN=Screenlate`, short hand-written notes for this release.
- 2026-09-27: owner feedback after installing v0.1.0 — catalog order of term dictionaries by target language: English, Japanese, Russian, Spanish, French, German, then the rest; asks about Warodai and Kenrowa in the catalog and about bundling JMdict (English) instead of Jitendex to shrink the APK; "vibrate on a new word" does nothing on the phone (bug). Decisions: JMdict in the APK and Jitendex in the catalog; catalog order interface language → English → source language → ru, es, fr, de → rest; Warodai stays a file import.
