# Test model

Every behavior of the app that can be checked on the emulator, with how it is checked and how often. Written for
request 42 of `plans/2026-10-10-languages-implementation.md`. Keep it current: a new feature adds its cases as N, a
removed one drops them, a run updates the "Checked" column and the run log at the end.

## Categories

| Category | Meaning | When it runs |
|---|---|---|
| **E** | Every regression | Before a release, before merging a language branch, after a large change (engine, overlay, settings storage, a dependency upgrade) |
| **F** | Full regression | Once when built, then in a full regression: before the big release, or when its area changes |
| **O** | Once | Checked when built; again only when its code changes |
| **N** | New | Not checked yet. The first run checks it and moves it to E, F or O, recording the move in the run log |

A regression always includes the E cases of the areas the change touches; a full regression runs E and F everywhere.

## Coverage

| Code | Meaning |
|---|---|
| U | JVM unit test (`./gradlew testDebugUnitTest`), named when one test class carries the case |
| P | Page test (`npm --prefix scripts/page-tests test`): popup.js, render.js, anki.js, note.js, definition.js in jsdom |
| I | Instrumented test (named); runs instead of clicking through when the case is not marked M as well |
| M | By hand on the emulator (the agent drives it with adb, screenshots and logcat) |
| D | Only on a real device; listed under "Not on the emulator" and not part of an agent run |

A case marked I and M has its logic in the test and keeps a short look on the emulator for what the test cannot see
(layout, gestures, the overlay).

## Setup

- AVDs: `Pixel_10_Pro` (API 37, x86_64, 1280×2856) for manual checks, the WebView tests and the engine, registry and
  overlay tests; `Screenlate_API30_x86` or `Screenlate_API30_glide` for the Compose UI tests of `app` (Espresso's input
  injector fails on API 37) and for the pull-out glide on old Android. The API 30 WebView cannot run the popup page.
- Start: `emulator -avd Pixel_10_Pro -no-snapshot-save -no-boot-anim -crash-report-mode never`. Without
  `-crash-report-mode never` an earlier emulator crash leaves a consent dialog that blocks the boot on the desktop
  (it looks like a crash at boot: the log ends with the old crash's annotations).
- `-no-snapshot-save` discards the run's state on exit; check `dumpsys package com.vpr.screenlate.debug` before
  relying on an earlier run.
- Install: `scripts/debug-device.sh install` (also enables the accessibility service). Test images:
  `scripts/debug-device.sh images`, then `show <file> [default|swipe]` as the backdrop. `scripts/text-image.py` renders
  new ones.
- AnkiDroid: the emulator's local collection without an account (never sync, never log in). The owner's deck
  `testdata/anki-backup/3 Mining.apkg` may be imported into it.
- Dictionaries for imports: `testdata/dictionaries/` (Japanese, wty for English). Settings import:
  `testdata/dictionaries/yomitan-settings-*.json`, collection export `yomitan-dictionaries-*.json`.
- The v0.2.3 debug build for the upgrade cases: `testdata/apk/screenlate-v0.2.3-debug-x86_64.apk` (same package as the
  current debug build).
- Logs: `adb logcat -d -s OverlayController ScreenlateService ScreenCapturer AccessibilityText PopupNotes` for scan
  stages and timings; `FATAL EXCEPTION` for crashes.
- Rules: never `am force-stop` (drops the accessibility service; `am kill` after Home instead); a `uiautomator dump`
  reconnects the service and docks the bubble, so use screenshots during overlay checks; the popup page answers
  `scripts/popup-eval.mjs`; Chrome's first-run screen is not clicked through (it accepts terms).
- Network failures: `svc wifi disable` with `adb emu network speed gprs`, or a host socket that never answers plus
  `settings put global http_proxy 10.0.2.2:8899` (revert with `settings delete global http_proxy`).
- Instrumented runs: per module, e.g. `./gradlew :dictionary:engine-hoshidicts:connectedDebugAndroidTest`; a full
  `connectedDebugAndroidTest` uninstalls the debug app at the end, so reinstall afterwards.

## Cases

Columns: ID, the check (action → expected), category, coverage, the date of the last check on this branch.

### FR: install, first run, upgrade

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| FR1 | Fresh install → the first run asks for the language, Japanese preselected; Japanese → bundled JMdict, KANJIDIC, frequency and pitch dictionaries install in the background, home shows them ready | E | U LanguageProfilesTest, M | |
| FR2 | Fresh install, English chosen → the download screen opens; Japanese stays off and its bundled dictionaries are not installed | F | M | |
| FR3 | Fresh install → the translation button is off, "Translation in Anki notes" on | E | U TranslationSettingsTest, M | |
| FR4 | Upgrade from v0.2.3 with changed settings (bubble size and side, scan length, Anki deck and templates, audio sources, popup font and CSS, theme, hidden apps, dictionary order, switches and sort dictionary) → no first-run question, Japanese on, everything kept | E | M | |
| FR5 | Upgrade from v0.2.3 with the translation button untouched → on; turned off in v0.2.3 → stays off | E | U TranslationSettingsTest, M | |
| FR6 | Upgrade from v0.2.3 → the accessibility disclosure is not asked again; deleted bundled dictionaries stay deleted | F | M | |
| FR7 | A backup made by v0.2.3 (settings, and with dictionaries) restores into the current build: every part listed, settings applied to Japanese, the translation button kept on | E | U BackupFormatTest, M | |
| FR8 | The accessibility disclosure: the first tap on a button that opens the accessibility settings shows it; Agree opens the settings, later taps open them at once; About shows the same text | O | U AccessibilityAgreedTest, M | |

### BUB: the bubble

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| BUB1 | Pull the bubble out of the dock over Japanese text → a scan starts and the entry of the word under the aim opens | E | M | |
| BUB2 | Move the floating bubble → the entry follows the aim without a new scan | E | M | |
| BUB3 | Tap the floating bubble → a new scan, recognized lines flash | F | M | |
| BUB4 | Hold the floating bubble → the menu (copy paragraph, copy all, open Screenlate) above the bubble, below when there is no room; Back or a tap outside closes it without other effect | F | U MenuPlacementTest, CopyMenuTextTest, M | |
| BUB5 | With two or three quick languages the hold menu shows their flags, the active one ringed; a tap switches the language, the docked glyph changes at once, the next scan uses the new language; one quick language → no flag row | E | M | |
| BUB6 | Double tap → the aim switches between above the finger and the bubble center | O | M | |
| BUB7 | ✕ → the entry closes, the bubble stays and keeps the text; it stays closed until the bubble moves or scans | F | M | |
| BUB8 | Drag to the left or right edge → docks once the center passes the edge or on release at the edge; top and bottom only with "Top and bottom edges" | F | U DockPlacementTest, M | |
| BUB9 | Drag the docked bubble along the edge → the dock moves | O | M | |
| BUB10 | The dock stays clear of the bars and the system gesture strips; an open keyboard lifts a bottom dock; in a fullscreen app (`show <file> swipe`) the top and bottom docks go to the edge with a third past the strip | F | U DockSettingsTest, M | |
| BUB11 | Rotating the screen → the entry closes, the bubble docks on the same side | F | M | |
| BUB12 | The Quick Settings tile hides and shows the bubble; holding it opens Screenlate | O | M | |
| BUB13 | Home's switch and Bubble → "Show the bubble" hide and show it | F | M | |
| BUB14 | An app in the hidden apps list → the bubble hides there and comes back outside it | O | M | |

### OCR: text sources and recognition

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| OCR1 | Screen recognition, both engines → the on-device draft of the band around the aim, then the cloud result; the source chip says Lens | E | U CompositeOcrTest, FocusBandTest, M | |
| OCR2 | Cloud unreachable (proxy that never answers) → the on-device result stays, ⚠ next to the source, a tap gives the reason; without any network → the offline message | F | U OcrStatusTest, M | |
| OCR3 | Engines: cloud only (nothing without network) and device only (chip "On-device", OCR boost unavailable) | F | M | |
| OCR4 | Reduce load → the whole screen is read on the device only after 3 s without a cloud answer (scan log) | O | M | |
| OCR5 | OCR boost on demand and always → small text (`x-small.jpg`) found; extra requests in the log | O | M | |
| OCR6 | Read app text → the app's own text is used (exact highlight, lines flash at once) next to the screen recognition | F | U AccessibilityTextTest, M | |
| OCR7 | App text only → no screenshot; an app without text shows the message; holding ➕ still takes a picture | F | M | |
| OCR8 | A secure window → "This app does not allow screenshots." | O | M | |
| OCR9 | Vertical text (`manga-page.webp`) → the word under the aim, a word continued in the next column found whole | E | U ReadingOrderTest, M | |
| OCR10 | A word broken at a line end is looked up whole (Japanese; English "give up") | F | U TextLayoutTest, M | |
| OCR11 | On-device recognition per script: Japanese, English, Chinese, Korean and Russian samples with the matching language active | F | U DeviceOcrTest, M | |
| OCR12 | A language the device cannot read → scanned by the cloud alone; with device only → the message that the device does not support it | O | U DeviceOcrTest, M | |
| OCR13 | Character highlight lines up with the characters (corrected on-device boxes) | O | M | |
| OCR14 | No word at the aim on a kanji (`kanji-fallback.png`) → its kanji entry | F | M | |
| OCR15 | A language that is not ready → the scan shows the explanation and the installation link before capturing | F | I InstallationPopupTest, M | |

### LK: lookup

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| LK1 | Japanese: deinflection, respellings (1人, 々, ッ, dots and spaces), romaji when on, Latin entries (ＯＬ, Tシャツ) | E | I LookupPipelineTest, HoshidictsEngineTest, U LookupVariantsTest | |
| LK2 | Sorting by the sort dictionary and dictionary order, as Yomitan | F | U YomitanSorterTest, SortFrequencyTest | |
| LK3 | Scan length and entries per lookup apply | O | U QueryScanLengthTest, M | |
| LK4 | "Kanji of the word" adds the kanji entries below the results | O | M | |
| LK5 | English: deinflection, form-of entries, phrases, a table cut short; with the wordfreq dictionary "walk" sorts before "walke" | E | I LanguageLookupTest, M | |
| LK6 | Search screen with typed text; "Look up in Screenlate" in a selection menu opens it | E | I ProcessTextTest, M | |
| LK7 | Search while the language is not ready → status and link; it searches once the dictionary is ready | F | I SearchLanguageTest | |
| LK8 | No enabled dictionary with definitions → the popup and search say so, search offers "Manage dictionaries" | O | M | |

### POP: the popup

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| POP1 | Placement: above the word when it fits, else below the bubble; at the edge it keeps its size; landscape beside the word; vertical text the same | F | U PopupPlacementTest, M | |
| POP2 | A link in a definition → its lookup; the back arrow returns | F | P, M | |
| POP3 | A kanji in the headword → its KANJIDIC entry | F | M | |
| POP4 | Inflection chain 🧩 under the word, "+N" opens the others, a tap on a step shows its description in the interface language | F | P, I JapaneseInflectionsLocaleTest, M | |
| POP5 | Frequencies and pitch accents: one shown, "+N" and "−", ㋕ for kana values | F | P, M | |
| POP6 | Transcriptions with regions in the audio region order, "+N" shows five, "Show all" the rest, duplicates and SAMPA left out (English with wty-en-ipa imported on this version) | N | U TranscriptionRegionsTest, TranscriptionsTest, P, M | |
| POP7 | A tag with a description → the explanation at the bottom of the popup | O | P, M | |
| POP8 | Hold text → selection with handles, Copy; furigana left out; Back or a tap outside ends it | F | M | |
| POP9 | The word copy button; definition copying "Everything" and "Only meanings" | O | P, M | |
| POP10 | Auto-hide after adding to Anki and when the aim leaves the word; messages stay | O | M | |
| POP11 | "Show the recognized text above the entries" off → the popup starts with the entry | O | M | |
| POP12 | Fonts: the phone's Japanese font (骨 in its Japanese form), a downloaded and an added font, "Only for Japanese text", text weight, letter thickness | F | I PopupFontsTest, LookupPageTest, M | |
| POP13 | Custom CSS applies after the dictionaries' styles; warnings for skipped lines, missing fonts and remote servers; remote images blocked | O | U CssCheckTest, M | |
| POP14 | Pitch accent colors: off by default; on → the headword and reading take the first accent's color, each accent button its own; light and dark themes; `--pitch-*-color` in custom CSS overrides | N | P, I LookupPageTest, M | |
| POP15 | Light and dark popup; e-ink frames and black border | F | M | |
| POP16 | The installation link in the popup while the language is not ready | F | I InstallationPopupTest | |

### AUD: audio

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| AUD1 | 🔊 plays the clip of the highest source that has one; holding it lists all recordings | F | U AudioFinderTest, M | |
| AUD2 | A failing source → its Test shows the error; home lists it in the audio card | O | U AudioErrorTest, M | |
| AUD3 | English Wiktionary clips follow the Regions order; a changed order applies to the next selection | O | U AudioFinderTest, M | |
| AUD4 | A custom URL source on `http://10.0.2.2:<port>` asks for the local network permission (API 37) and plays | O | U LocalNetworkTest, M | |

### ANK: Anki

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| ANK1 | AnkiDroid access, deck and note type; templates pre-filled for known note types and by field names | F | I AnkiDroidRoundTripTest, U FieldTemplateTest, M | |
| ANK2 | ➕ adds a note with the sentence, audio and screenshot; a duplicate shows 📖, which opens it; holding 📖 adds anyway | E | M | |
| ANK3 | Holding ➕ opens the crop editor around the paragraph; "Whole screen" ↔ "Frame"; two notes in a row | F | I CropEditorTest, CropViewTest, U CropFocusTest, M | |
| ANK4 | While the scan refines its text ➕ is grey and a tap says to wait | O | M | |
| ANK5 | AnkiDroid permission revoked or deck deleted → a home card and a grey ➕ with the reason | O | U AnkiStatusCacheTest, M | |
| ANK6 | Anki settings are per language (English deck apart from Japanese) | F | I AnkiProfileEditorsTest, M | |
| ANK7 | `{sentence-translation}` filled from the popup's translation or fetched within 5 s | O | M | |
| ANK8 | Docking during a note keeps the note; rotation or docking closes an open crop editor as cancelled | O | M | |

### TR: sentence translation

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| TR1 | The translation button in the popup: a tap shows the translation, the service name and a copy button; a second tap hides it; a new word starts without it | F | M | |
| TR2 | Services in order with the 3 s fallback; all failing → each one's reason | O | U SentenceTranslatorTest, TranslationErrorTest, M | |
| TR3 | "Translate into": search by any name, stars on top | O | U TranslationLanguagesTest, M | |
| TR4 | Test asks every enabled service and shows answers and times | O | M | |

### DIC: dictionaries

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| DIC1 | Import a `.zip` from a file: progress in the card and the notification; Cancel there and in the notification | F | M | |
| DIC2 | Catalog download: the ring (waiting, filling, spinning), its stage line, ✕ inactive in the first second; the import after it | F | M | |
| DIC3 | A download that breaks off retries by itself up to three times (attempt on the card) and continues where it stopped; after that, Resume and Cancel; Cancel deletes the partial file | N | U ResumableDownloadTest, M | |
| DIC4 | The app killed during a download (`am kill` in the background) → the download continues by itself, no button; during an import → it restarts | N | M | |
| DIC5 | The app dies twice during an import → not started again, the card says so | O | U DictionaryImportsTest, M | |
| DIC6 | Order by drag, switches, sort dictionary; the last dictionary with definitions cannot be switched off or deleted | F | M | |
| DIC7 | Check for updates; an old bundled copy updates from the catalog | O | U OutdatedCopiesTest, M | |
| DIC8 | A Yomitan collection export: list, checks, space check, import | O | U CollectionSpaceTest, YomitanBackupJvmTest, I YomitanBackupTest, M | |
| DIC9 | ⟲: cancels running tasks, deletes all, reinstalls the bundled ones (spinner until done) | F | U DictionaryResetTest, M | |
| DIC10 | A deleted bundled dictionary stays deleted after an app update | F | U BundledDictionariesTest, M | |
| DIC11 | Missing dictionary files → the home card; bundled ones restored, catalog ones downloaded again | O | M | |
| DIC12 | Dictionary languages shown and changed by hand; a dictionary is used only for its word language | O | U DictionaryLanguagesTest, M | |
| DIC13 | wordfreq frequency dictionary built on the device: the list downloads, the checksum passes, the dictionary imports and sorts English | N | U WordfreqDictionaryTest, M | |
| DIC14 | Importing wty-en-ipa writes the transcription regions (shown in POP6) | N | U TranscriptionTagBanksTest, M | |
| DIC15 | The notification permission: the explanation dialog, then the system request, on the first import (API 33+) | O | M | |
| DIC16 | With several languages: language chips on the page, "Other languages" links to the Languages screen | F | M | |

### LNG: languages

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| LNG1 | Home's languages card: the quick flags, the active one ringed, its name and status, "All languages" | N | M | |
| LNG2 | The Languages screen: turned on (radio, star, Flag, Turn off), turned off with files (list with sizes, delete one, change its languages, Delete all, Turn on), not turned on | N | M | |
| LNG3 | Star rules: at most three quick languages; making a starred one active swaps; making an unstarred one active moves the star | N | U LanguageProfilesTest, M | |
| LNG4 | Flag country: English US by default, another country from "Flag" | N | U FlagsTest, M | |
| LNG5 | E-ink on → two-letter codes instead of flags on home, the screen and in the bubble menu | N | M | |
| LNG6 | The download screen: definition languages, categories, recommended ticked, sizes, Confirm disabled with a hint, space line; mobile data over 50 MB asks | F | U LanguageSetupTest, LanguageOptionsTest, M | |
| LNG7 | A language confirmed before its downloads finish: active at once, home status with progress, scans blocked with the link, search shows status and runs when ready | N | I SearchLanguageTest, InstallationPopupTest, M | |
| LNG8 | Turn off: "Delete its dictionaries and models" ticked, also cancels pending downloads before the first file; unticked keeps them | F | I TurnOffLanguageDialogTest, LanguageSwitchTest, M | |
| LNG9 | Turning a language on again with kept files: on at once, its settings back | F | I LanguageReactivationTest, M | |
| LNG10 | Settings per language stay apart (lookup, popup, Anki, audio, dictionary switches) | F | I AnkiProfileEditorsTest, U LookupSettingsRepositoryTest, M | |
| LNG11 | Switching from home's flags; switching during a scan applies from the next scan | F | M | |
| LNG12 | Japanese turned off with its files and on again → the bundled dictionaries install again | O | M | |

### SET: settings, backup, appearance

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| SET1 | Page resets and the full reset ask first and keep what they promise (dictionaries, fonts, interface language) | F | U SettingsKeysTest, M | |
| SET2 | Backup with and without dictionaries through the file picker; restore lists parts, checked parts replace | F | U BackupArchiveTest, BackupFormatTest, M | |
| SET3 | Yomitan settings import: the summary, applied to the profile's language | F | U YomitanSettingsTest, M | |
| SET4 | Theme (system, light, dark) and colors (own, system) | O | U ThemeModeTest, M | |
| SET5 | E-ink mode: black and white, larger sizes offered, turning off restores them | O | U EInkSizesTest, EInkEnlargementTest, M | |
| SET6 | Interface language: each of the 14 locales; the bubble, popup and notifications follow it | F | I AppLanguageResourcesTest, U TranslationsTest, M | |
| SET7 | Background work: battery optimization status and request, persistent notification switch, notifications-off note | O | M | |
| SET8 | Holding an icon button shows its description | O | M | |

### UPD: About, logs, updates

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| UPD1 | About: libraries, notices, installed dictionaries with licenses, "What the service reads and sends", "Report a problem" opens the form (not submitted) | O | M | |
| UPD2 | Logs: share and save to Downloads; after scans, lookups and a note the log has no recognized text, words, note contents or URLs with terms | E | U RedactionTest, M | |
| UPD3 | Update check and install (release builds only) | O | D | |

### NF: non-functional

Baselines are measured on `Pixel_10_Pro` (API 37) with the host idle; a value more than 1.5 times the last run's on
the same AVD is a finding. The first run of this model records them in the run log.

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| NF1 | Scan timings from the log: capture, band draft, full draft, cloud, final (earlier: band 1.6–2.4 s, full 5 s on the emulator) | E | M | |
| NF2 | Lookup to rendered entry (`popup-eval.mjs`; earlier 30–45 ms, する 100–180 ms) | F | M | |
| NF3 | Dragging the bubble: janky frames under 10 % (`dumpsys gfxinfo`), the entry keeps up with the aim | E | M | |
| NF4 | Pulling the bubble out has no move animation (`android.anim.lf` trace) on API 30 and API 37 | E | U BubbleWindowMoverTest, M | |
| NF5 | Screen transitions without a white flash or a visible previous screen (screen recording, dark theme) | F | M | |
| NF6 | Cold start: the splash until the settings are read, then content, no empty frame; `am start -W` TotalTime | F | M | |
| NF7 | Settings pages open with the current values, no defaults shown first | F | M | |
| NF8 | Memory: PSS docked, after a scan, and after 20 scans back near the docked value (no growth) | F | M | |
| NF9 | Sizes: APK per ABI against the last release; installed size (app, data) after a fresh install with Japanese | F | M | |
| NF10 | Long texts wrap and screens scroll in all 14 locales (download screen, Languages, settings hints; spot checks in de, ru, vi, ja) | F | M | |
| NF11 | Import times: JMdict from a file, the bundled set on first run | O | M | |
| NF12 | Search updates while typing without visible lag | O | M | |

## Not on the emulator

Checked by the owner on the phone or not at all; listed so that nothing is forgotten.

- The system stopping the service and "Bring the bubble back"; manufacturer startup settings; battery behavior while
  docked.
- E-ink device detection and the one-time offer; real vibration; mobile-data prompts with a real SIM.
- Restricted settings for an app installed from a file; the in-app update from GitHub (release builds).
- Recognition quality on the owner's apps (manga readers, X, games) and cloud timings on a mobile network.

## Run log

Newest first: date, build, scope, results, cases moved from N, findings.
