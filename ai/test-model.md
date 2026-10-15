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
  new ones. `zh-traditional.png` and `zh-vertical.png` are its output with `FONT=C:/Windows/Fonts/msjh.ttc`: the four
  sentences of `zh-sample.png` in Traditional characters, plus 這個問題沒有關係。 in the first, as columns
  (`--vertical`) in the second.
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
  `settings put global http_proxy 10.0.2.2:8899` (revert with `settings put global http_proxy :0`, then `settings delete global http_proxy` (deleting it alone leaves `global_http_proxy_host`/`_port` set and the proxy in force; `dumpsys connectivity | grep -A2 ProxyTracker` shows it)).
- Instrumented runs: per module, e.g. `./gradlew :dictionary:engine-hoshidicts:connectedDebugAndroidTest`; a full
  `connectedDebugAndroidTest` uninstalls the debug app at the end, so reinstall afterwards. The app's
  `SearchLanguageTest` and `InstallationPopupTest` need the API 37 WebView and fail on API 30: install the test APK
  (`assembleDebugAndroidTest`, `adb install -r -t`) on `Pixel_10_Pro` and run them with `am instrument -w -e class …
  com.vpr.screenlate.debug.test/androidx.test.runner.AndroidJUnitRunner`, which keeps the installed app and its data
  (their fixture is isolated); uninstall the test APK and run `debug-device.sh install` to bind the service again.

## Cases

Columns: ID, the check (action → expected), category, coverage, the date of the last check on this branch.

### FR: install, first run, upgrade

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| FR1 | Fresh install → the first run asks for the language, Japanese preselected; Japanese → bundled JMdict, KANJIDIC, frequency and pitch dictionaries install in the background, home shows them ready | E | U LanguageProfilesTest, M | 2026-10-14 |
| FR2 | Fresh install, English chosen → the download screen opens; Japanese stays off and its bundled dictionaries are not installed | F | M | 2026-10-14 (API 30) |
| FR3 | Fresh install → the translation button is off, "Translation in Anki notes" on | E | U TranslationSettingsTest, M | 2026-10-14 |
| FR4 | Upgrade from v0.2.3 with changed settings (bubble size and side, scan length, Anki deck and templates, audio sources, popup font and CSS, theme, hidden apps, dictionary order, switches and sort dictionary) → no first-run question, Japanese on, everything kept | E | M | 2026-10-14 |
| FR5 | Upgrade from v0.2.3 with the translation button untouched → on; turned off in v0.2.3 → stays off | E | U TranslationSettingsTest, M | 2026-10-14 |
| FR6 | Upgrade from v0.2.3 → the accessibility disclosure is not asked again; deleted bundled dictionaries stay deleted | F | M | 2026-10-14 |
| FR7 | A backup made by v0.2.3 (settings, and with dictionaries) restores into the current build: every part listed, settings applied to Japanese, the translation button kept on | E | U BackupFormatTest, M | 2026-10-14 |
| FR8 | The accessibility disclosure: the first tap on a button that opens the accessibility settings shows it; Agree opens the settings, later taps open them at once; About shows the same text | O | U AccessibilityAgreedTest, M | |

### BUB: the bubble

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| BUB1 | Pull the bubble out of the dock over Japanese text → a scan starts and the entry of the word under the aim opens | E | M | 2026-10-14 |
| BUB2 | Move the floating bubble → the entry follows the aim without a new scan | E | M | 2026-10-14 |
| BUB3 | Tap the floating bubble → a new scan, recognized lines flash | F | M | 2026-10-14 |
| BUB4 | Hold the floating bubble → the menu (copy paragraph, copy all, open Screenlate) above the bubble, below when there is no room; Back or a tap outside closes it without other effect | F | U MenuPlacementTest, CopyMenuTextTest, M | 2026-10-14 |
| BUB5 | With two or three quick languages the hold menu shows their flags, the active one ringed; a tap switches the language, the docked glyph changes at once, the next scan uses the new language; one quick language → no flag row | E | M | 2026-10-14 |
| BUB6 | Double tap → the aim switches between above the finger and the bubble center | O | M | |
| BUB7 | ✕ → the entry closes, the bubble stays and keeps the text; it stays closed until the bubble moves or scans | F | M | 2026-10-14 |
| BUB8 | Drag to the left or right edge → docks once the center passes the edge or on release at the edge; top and bottom only with "Top and bottom edges" | F | U DockPlacementTest, M | 2026-10-14 |
| BUB9 | Drag the docked bubble along the edge → the dock moves | O | M | |
| BUB10 | The dock stays clear of the bars and the system gesture strips; an open keyboard lifts a bottom dock; in a fullscreen app (`show <file> swipe`) the top and bottom docks go to the edge with a third past the strip | F | U DockSettingsTest, M | 2026-10-14 |
| BUB11 | Rotating the screen → the entry closes, the bubble docks on the same side | F | M | 2026-10-14 |
| BUB12 | The Quick Settings tile hides and shows the bubble; holding it opens Screenlate | O | M | |
| BUB13 | Home's switch and Bubble → "Show the bubble" hide and show it | F | M | 2026-10-14 |
| BUB14 | An app in the hidden apps list → the bubble hides there and comes back outside it | O | M | |
| BUB15 | E-ink: the hold menu is white with a black frame, black text and a black ring around the active code, no shadow or ripple | F | M | 2026-10-14 |

### OCR: text sources and recognition

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| OCR1 | Screen recognition, both engines → the on-device draft of the band around the aim, then the cloud result; the source chip says Lens | E | U CompositeOcrTest, FocusBandTest, M | 2026-10-14 |
| OCR2 | Cloud unreachable (proxy that never answers) → the on-device result stays, ⚠ next to the source, a tap gives the reason; without any network → the offline message | F | U OcrStatusTest, M | 2026-10-14 |
| OCR3 | Engines: cloud only (nothing without network) and device only (chip "On-device", OCR boost unavailable) | F | M | 2026-10-14 |
| OCR4 | Reduce load → the whole screen is read on the device only after 3 s without a cloud answer (scan log) | O | M | |
| OCR5 | OCR boost on demand and always → small text (`x-small.jpg`) found; extra requests in the log | O | M | |
| OCR6 | Read app text → the app's own text is used (exact highlight, lines flash at once) next to the screen recognition | F | U AccessibilityTextTest, M | 2026-10-14 |
| OCR7 | App text only → no screenshot; an app without text shows the message; holding ➕ still takes a picture | F | M | 2026-10-14 |
| OCR8 | A secure window → "This app does not allow screenshots." | O | M | |
| OCR9 | Vertical text (`manga-page.webp`) → the word under the aim, a word continued in the next column found whole | E | U ReadingOrderTest, M | 2026-10-14 |
| OCR10 | A word broken at a line end is looked up whole (Japanese; English "give up") | F | U TextLayoutTest, M | 2026-10-14 |
| OCR11 | On-device recognition per script: Japanese, English, Chinese, Korean and Russian samples with the matching language active | F | U DeviceOcrTest, M | 2026-10-14 (ja, en), 2026-10-15 (zh) |
| OCR12 | A language the device cannot read → scanned by the cloud alone; with device only → the message that the device does not support it | O | U DeviceOcrTest, M | |
| OCR13 | Character highlight lines up with the characters (corrected on-device boxes) | O | M | |
| OCR14 | No word at the aim on a kanji (`kanji-fallback.png`) → its kanji entry | F | M | 2026-10-14 |
| OCR15 | A language that is not ready → the scan shows the explanation and the installation link before capturing | F | I InstallationPopupTest, M | 2026-10-14 |
| OCR16 | Chinese with cloud recognition: Simplified (`zh-sample.png`, 图书馆 with pinyin), Traditional (`zh-traditional.png`, 沒有關係) and vertical columns read right to left (`zh-vertical.png`: 館 alone, 意思 highlighted down its column). Rescan with a tap after changing the picture: a floating bubble keeps its scan | N | U TextLayoutTest, ReadingOrderTest, M | 2026-10-15 |

### LK: lookup

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| LK1 | Japanese: deinflection, respellings (1人, 々, ッ, dots and spaces, radical look-alikes such as U+2F08 for 人), romaji when on, Latin entries (ＯＬ, Tシャツ) | E | I LookupPipelineTest, HoshidictsEngineTest, U LookupVariantsTest, JapaneseSupportTest | 2026-10-14 |
| LK2 | Sorting by the sort dictionary and dictionary order, as Yomitan | F | U YomitanSorterTest, SortFrequencyTest | 2026-10-14 |
| LK3 | Scan length and entries per lookup apply | O | U QueryScanLengthTest, M | |
| LK4 | "Kanji of the word" adds the kanji entries below the results | O | M | |
| LK5 | English: deinflection, form-of entries, phrases, a table cut short; with the wordfreq dictionary "walk" sorts before "walke" | E | I LanguageLookupTest, M | 2026-10-14 |
| LK6 | Search screen with typed text; "Look up in Screenlate" in a selection menu opens it | E | I ProcessTextTest, M | 2026-10-14 |
| LK7 | Search while the language is not ready → status and link; it searches once the dictionary is ready | F | I SearchLanguageTest | 2026-10-14 |
| LK8 | No enabled dictionary with definitions → the popup and search say so, search offers "Manage dictionaries" | O | M | |
| LK9 | Chinese search by typed pinyin: "nihao", "ni hao", "ni3hao3", "nǐhǎo" find 你好; "shi" lists 是 时 事 first; "shi4" puts fourth-tone words first; "lv", "nü3" (ü); "xi'an" puts 西安 first; words show in the script chosen on the Languages screen; a dictionary imported again builds its index | N | U ChinesePinyinTest, ReadingIndexTest, ReadingIndexBanksTest, ReadingWordsTest, M | 2026-10-14 |
| LK10 | Chinese Wiktionary form-of tags in the interface language: 吓 lists 下 with "alternative Simplified Cantonese", in Russian "альтернативное упрощённое кантонское"; 哄 with Hokkien | N | U FormOfTagsTest, M | 2026-10-15 |
| LK11 | Chinese lookups: the longest word in the text's script (图书馆看书 → 图书馆, 圖書館看書 → 圖書館, each with its `lang`), a radical look-alike (U+2F47 for 日), a fixed Traditional script shows 图书馆 as 圖書館, typed pinyin in the chosen script, typed tones first | N | I ChineseLookupTest, U ChineseSupportTest | 2026-10-15 |

### POP: the popup

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| POP1 | Placement: above the word when it fits, else below the bubble; at the edge it keeps its size; landscape beside the word; vertical text the same | F | U PopupPlacementTest, M | 2026-10-14 |
| POP2 | A link in a definition → its lookup; the back arrow returns | F | P, M | 2026-10-14 |
| POP3 | A kanji in the headword → its KANJIDIC entry | F | M | 2026-10-14 |
| POP4 | Inflection chain 🧩 under the word, "+N" opens the others, a tap on a step shows its description in the interface language | F | P, I JapaneseInflectionsLocaleTest, M | 2026-10-14 |
| POP5 | Frequencies and pitch accents: one shown, "+N" and "−", ㋕ for kana values | F | P, M | 2026-10-14 |
| POP6 | Transcriptions with regions in the audio region order, "+N" shows five, "Show all" the rest, duplicates and SAMPA left out (English with wty-en-ipa imported on this version) | F | U TranscriptionRegionsTest, TranscriptionsTest, P, M | 2026-10-14 |
| POP7 | A tag with a description → the explanation at the bottom of the popup | O | P, M | |
| POP8 | Hold text → selection with handles, Copy; furigana left out; Back or a tap outside ends it | F | M | 2026-10-14 |
| POP9 | The word copy button; definition copying "Everything" and "Only meanings" | O | P, M | |
| POP10 | Auto-hide after adding to Anki and when the aim leaves the word; messages stay | O | M | 2026-10-14 |
| POP11 | "Show the recognized text above the entries" off → the popup starts with the entry | O | M | |
| POP12 | Fonts: the phone's Japanese font (骨 in its Japanese form), a downloaded and an added font, "Only for Japanese text", text weight, letter thickness | F | I PopupFontsTest, LookupPageTest, M | 2026-10-14 |
| POP13 | Custom CSS applies after the dictionaries' styles; warnings for skipped lines, missing fonts and remote servers; remote images blocked | O | U CssCheckTest, M | |
| POP14 | Pitch accent colors: off by default; on → the headword and reading take the first accent's color, each accent button its own; light and dark themes; `--pitch-*-color` in custom CSS overrides | F | P, I LookupPageTest, M | 2026-10-14 |
| POP15 | Light and dark popup; e-ink frames and black border | F | M | 2026-10-14 |
| POP16 | The installation link in the popup while the language is not ready | F | I InstallationPopupTest | 2026-10-14 |
| POP17 | Chinese fonts: five to download with descriptions in the interface language (zh-TW gets the Traditional text); Noto Sans TC downloads, is chosen and loads on the page; a font downloaded for Japanese (M PLUS 1p) is not on the Chinese list and the other way round | N | U FontCatalogFileTest, FontsForLanguageTest, M | 2026-10-15 |
| POP18 | Deleting a font asks "Delete <font>?": Cancel keeps it, Delete removes it and languages that used it go back to the phone's font | N | M | 2026-10-15 |

### AUD: audio

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| AUD1 | 🔊 plays the clip of the highest source that has one; holding it lists all recordings | F | U AudioFinderTest, M | 2026-10-14 |
| AUD2 | A failing source → its Test shows the error; home lists it in the audio card | O | U AudioErrorTest, M | 2026-10-14 |
| AUD3 | English Wiktionary clips follow the Regions order; a changed order applies to the next selection | O | U AudioFinderTest, M | |
| AUD4 | A custom URL source on `http://10.0.2.2:<port>` asks for the local network permission (API 37) and plays | O | U LocalNetworkTest, M | |
| AUD5 | Home's audio card "Audio settings" opens the Anki page at the audio section; "Add audio source" starts as the language's first default kind (English: Lingua Libre) | O | M | 2026-10-14 |
| AUD6 | Chinese: 🔊 on 朋友 plays Wiktionary's `Zh-péngyou.ogg` (found by pinyin), holding it lists Wiktionary, Lingua Libre, then text-to-speech; text-to-speech reads the characters | N | U AudioFinderTest, ChineseSupportTest, M | 2026-10-15 |

### ANK: Anki

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| ANK1 | AnkiDroid access, deck and note type; templates pre-filled for known note types and by field names | F | I AnkiDroidRoundTripTest, U FieldTemplateTest, M | 2026-10-14 |
| ANK2 | ➕ adds a note with the sentence, audio and screenshot; a duplicate shows 📖, which opens it; holding 📖 adds anyway | E | M | 2026-10-14 |
| ANK3 | Holding ➕ opens the crop editor around the paragraph; "Whole screen" ↔ "Frame"; two notes in a row | F | I CropEditorTest, CropViewTest, U CropFocusTest, M | 2026-10-14 |
| ANK4 | While the scan refines its text ➕ is grey and a tap says to wait | O | M | |
| ANK5 | AnkiDroid permission revoked or deck deleted → a home card and a grey ➕ with the reason | O | U AnkiStatusCacheTest, M | |
| ANK6 | Anki settings are per language (English deck apart from Japanese) | F | I AnkiProfileEditorsTest, M | 2026-10-14 |
| ANK7 | `{sentence-translation}` filled from the popup's translation or fetched within 5 s | O | M | |
| ANK8 | Docking during a note keeps the note; rotation or docking closes an open crop editor as cancelled | O | M | |
| ANK9 | Chinese note type with Simplified, Traditional, Pinyin, English, Sound: pre-filled with `{expression-simplified}`, `{expression-traditional}`, `{reading}`, `{glossary}`, `{audio}`; the + list has both script markers; a note for 头发 gets 頭髮 (not 頭發) in Traditional; `{furigana}` puts tóu over 头 and fa over 发 | N | U FieldTemplateTest, ShownResultsTest, P, M | 2026-10-15 |

### TR: sentence translation

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| TR1 | The translation button in the popup: a tap shows the translation, the service name and a copy button; a second tap hides it; a new word starts without it | F | M | 2026-10-14 |
| TR2 | Services in order with the 3 s fallback; all failing → each one's reason | O | U SentenceTranslatorTest, TranslationErrorTest, M | |
| TR3 | "Translate into": search by any name, stars on top | O | U TranslationLanguagesTest, M | |
| TR4 | Test asks every enabled service and shows answers and times | O | M | |

### DIC: dictionaries

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| DIC1 | Import a `.zip` from a file: progress in the card and the notification; Cancel there and in the notification | F | M | 2026-10-14 |
| DIC2 | Catalog download: the ring (waiting, filling, spinning), its stage line, ✕ inactive in the first second; the import after it; the task is named by its catalog title in the interface language, on its card and on home | F | M | 2026-10-14 |
| DIC3 | A download that breaks off retries by itself up to three times (attempt on the card) and continues where it stopped; after that, Resume and Cancel, and the card says the connection was lost (not the technical reason); Cancel deletes the partial file | F | U ResumableDownloadTest, M | 2026-10-14 |
| DIC4 | The app killed during a download (`am kill` in the background) → the download continues by itself, no button; during an import → it restarts | F | M | 2026-10-14 |
| DIC5 | The app dies twice during an import → not started again, the card says so | O | U DictionaryImportsTest, M | |
| DIC6 | Order by drag, switches, sort dictionary; the last dictionary with definitions cannot be switched off or deleted | F | M | 2026-10-14 |
| DIC7 | Check for updates; an old bundled copy updates from the catalog | O | U OutdatedCopiesTest, M | |
| DIC8 | A Yomitan collection export: list, checks, space check, import | O | U CollectionSpaceTest, YomitanBackupJvmTest, I YomitanBackupTest, M | 2026-10-14 (I) |
| DIC9 | ⟲: cancels running tasks, deletes all, reinstalls the bundled ones (spinner until done) | F | U DictionaryResetTest, M | 2026-10-14 |
| DIC10 | A deleted bundled dictionary stays deleted after an app update | F | U BundledDictionariesTest, M | 2026-10-14 |
| DIC11 | Missing dictionary files → the home card; bundled ones restored, catalog ones downloaded again | O | M | |
| DIC12 | Dictionary languages shown and changed by hand; a dictionary is used only for its word language | O | U DictionaryLanguagesTest, M | |
| DIC13 | wordfreq frequency dictionary built on the device: the list downloads, the checksum passes, the dictionary imports and sorts English | O | U WordfreqDictionaryTest, M | 2026-10-14 |
| DIC14 | Importing wty-en-ipa writes the transcription regions (shown in POP6) | O | U TranscriptionTagBanksTest, M | 2026-10-14 |
| DIC15 | The notification permission: the explanation dialog, then the system request, on the first import (API 33+) | O | M | |
| DIC16 | With several languages: language chips on the page, "Other languages" links to the Languages screen | F | M | 2026-10-14 |
| DIC17 | Chinese wordfreq built on the device ranks Traditional spellings too: 頭髮 shows the rank of 头发 | N | U WordfreqDictionaryTest, M | 2026-10-15 |

### LNG: languages

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| LNG1 | Home's languages card: the quick flags, the active one ringed, its name and status, "All languages" | E | M | 2026-10-14 |
| LNG2 | The Languages screen: turned on (radio, star, Flag, Turn off), turned off with files (list with sizes, delete one, change its languages, Delete all, Turn on), not turned on | F | M | 2026-10-14 |
| LNG3 | Star rules: at most three quick languages; making a starred one active swaps; making an unstarred one active moves the star | F | U LanguageProfilesTest, M | 2026-10-14 |
| LNG4 | Flag country: English US by default, another country from "Flag" | O | U FlagsTest, M | 2026-10-14 |
| LNG5 | E-ink on → two-letter codes instead of flags on home, the screen and in the bubble menu; the chosen language chip on the app's pages filled black, the others outlined | F | M | 2026-10-14 |
| LNG6 | The download screen: definition languages (the menu as wide as its button), categories, recommended ticked, sizes, Confirm disabled with a hint, space line; mobile data over 50 MB asks | F | U LanguageSetupTest, LanguageOptionsTest, M | 2026-10-14 |
| LNG7 | A language confirmed before its downloads finish: active at once, home status with progress, scans blocked with the link, search shows status and runs when ready | E | I SearchLanguageTest, InstallationPopupTest, M | 2026-10-14 |
| LNG8 | Turn off: "Delete its dictionaries and models" ticked, also cancels pending downloads before the first file; unticked keeps them | F | I TurnOffLanguageDialogTest, LanguageSwitchTest, M | 2026-10-14 |
| LNG9 | Turning a language on again with kept files: on at once, its settings back | F | I LanguageReactivationTest, M | 2026-10-14 |
| LNG10 | Settings per language stay apart (lookup, popup, Anki, audio, dictionary switches) | F | I AnkiProfileEditorsTest, U LookupSettingsRepositoryTest, M | 2026-10-14 |
| LNG11 | Switching from home's flags; switching during a scan applies from the next scan, a tap on the floating bubble included | F | M | 2026-10-14 |
| LNG12 | Japanese turned off with its files and on again → the bundled dictionaries install again | O | M | |
| LNG13 | Chinese card: "Characters" Simplified or Traditional with its hint, "All words in these characters" off by default; Traditional makes "xuexi" show 學習; the Lookup page has no such block; a backup's General part carries it, only the full reset clears it | N | U SettingsKeysTest, M | 2026-10-15 |

### SET: settings, backup, appearance

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| SET1 | Page resets and the full reset ask first and keep what they promise (dictionaries, fonts, interface language) | F | U SettingsKeysTest, M | 2026-10-14 |
| SET2 | Backup with and without dictionaries through the file picker; restore lists parts, checked parts replace | F | U BackupArchiveTest, BackupFormatTest, M | 2026-10-14 |
| SET3 | Yomitan settings import: the summary, applied to the profile's language | F | U YomitanSettingsTest, M | 2026-10-14 |
| SET4 | Theme (system, light, dark) and colors (own, system) | O | U ThemeModeTest, M | |
| SET5 | E-ink mode: black and white, larger sizes offered, turning off restores them | O | U EInkSizesTest, EInkEnlargementTest, M | |
| SET6 | Interface language: each of the 14 locales; the bubble, popup and notifications follow it; a language chosen in the system's app settings while the app runs renames languages at once (`cmd locale set-app-locales`) | F | I AppLanguageResourcesTest, U TranslationsTest, M | 2026-10-14 |
| SET7 | Background work: battery optimization status and request, persistent notification switch, notifications-off note | O | M | |
| SET8 | Holding an icon button shows its description | O | M | |

### UPD: About, logs, updates

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| UPD1 | About: libraries, notices, installed dictionaries with licenses, "What the service reads and sends", "Report a problem" opens the form (not submitted) | O | M | |
| UPD2 | Logs: share and save to Downloads; after scans, lookups and a note the log has no recognized text, words, note contents or URLs with terms | E | U RedactionTest, M | 2026-10-14 |
| UPD3 | Update check and install (release builds only) | O | D | |

### NF: non-functional

Baselines are measured on `Pixel_10_Pro` (API 37) with the host idle; a value more than 1.5 times the last run's on
the same AVD is a finding. The first run of this model records them in the run log.

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| NF1 | Scan timings from the log: capture, band draft, full draft, cloud, final (earlier: band 1.6–2.4 s, full 5 s on the emulator) | E | M | 2026-10-14 |
| NF2 | Lookup to rendered entry (`popup-eval.mjs`; earlier 30–45 ms, する 100–180 ms) | F | M | 2026-10-14 |
| NF3 | Dragging the bubble: janky frames and the median frame time against the last run's (`dumpsys gfxinfo` after `reset`, over text and over an empty area), the entry keeps up with the aim | E | M | 2026-10-14 |
| NF4 | Pulling the bubble out has no move animation (`android.anim.lf` trace) on API 30 and API 37 | E | U BubbleWindowMoverTest, M | 2026-10-14 (API 37) |
| NF5 | Screen transitions without a white flash or a visible previous screen (screen recording, dark theme) | F | M | 2026-10-14 |
| NF6 | Cold start: the splash until the settings are read, then content, no empty frame; `am start -W` TotalTime | F | M | 2026-10-14 |
| NF7 | Settings pages open with the current values, no defaults shown first; the Dictionaries page's first opening in a process shows a progress indicator in place of the lists, not empty sections (screen recording after a restart) | F | M | 2026-10-14 |
| NF8 | Memory: PSS docked, after a scan, and after 20 scans back near the docked value (no growth) | F | M | 2026-10-14 |
| NF9 | Sizes: APK per ABI against the last release; installed size (app, data) after a fresh install with Japanese | F | M | 2026-10-14 |
| NF10 | Long texts wrap and screens scroll in all 14 locales (download screen, Languages, settings hints; spot checks in de, ru, vi, ja) | F | M | 2026-10-14 |
| NF11 | Import times: JMdict from a file, the bundled set on first run | O | M | 2026-10-14 (bundled) |
| NF12 | Search updates while typing without visible lag | O | M | 2026-10-14 |

## Not on the emulator

Checked by the owner on the phone or not at all; listed so that nothing is forgotten.

- The system stopping the service and "Bring the bubble back"; manufacturer startup settings; battery behavior while
  docked.
- E-ink device detection and the one-time offer; real vibration; mobile-data prompts with a real SIM.
- Restricted settings for an app installed from a file; the in-app update from GitHub (release builds).
- Recognition quality on the owner's apps (manga readers, X, games) and cloud timings on a mobile network.

## Run log

Newest first: date, build, scope, results, cases moved from N, findings.

### 2026-10-14, builds a430fec to fff2e6a, Pixel_10_Pro (API 37)

Scope: the first run of the model, a full regression: every E and F case, the O cases of the areas this branch changed
(languages, dictionaries and downloads, the download screen, transcriptions, pitch colors, Anki per language), the
upgrade and restore cases against v0.2.3, and the NF baselines.

Results: every checked case passed after the fixes below. A note in the Checked column names what was left out.

Not run:
- NF4 on API 30 (checked on API 37 only).
- OCR11 for Chinese, Korean and Russian: those languages are not on this branch yet; their branches check them.
- The O cases outside the branch's changes (38 of 47).

Automated: JVM unit tests 704 pass (4 skipped); page tests 112 pass; instrumented on API 37: engine-hoshidicts 18,
dictionary:api 17, overlay 19, core:anki 2, core:common 4; app: 8 on API 30 (`Screenlate_API30_glide`) and the 3
WebView tests on API 37 with `am instrument`.

Moved from N: LNG1 and LNG7 to E; POP6, POP14, DIC3, DIC4, LNG2, LNG3 and LNG5 to F; DIC13, DIC14 and LNG4 to O.
Added and checked: BUB15 (e-ink hold menu), AUD5 (audio card link, new source kind); DIC2, DIC3, LNG5, LNG6, LNG11,
NF7 and SET6 got the checks of the fixes below.

Fixed during the run (each verified on the emulator):
- A tap rescan after a language switch in the hold menu kept the old language (owner's request; decision row
  "Switching during a scan" updated).
- Catalog downloads were named by their archive (jiten-global-frequency); now by the catalog title.
- A stopped download's card showed the technical reason ("stream was reset: CANCEL"); now "connection lost".
- The hold menu ignored e-ink (tinted background, shadow, colored ring); the chosen language chip on e-ink was grey.
- Home's audio card opened the top of the Anki page; now the audio section. "Add audio source" started as a URL
  source; now as the language's first default kind.
- The definition-language menu on the download screen was narrower than its button.
- The Dictionaries page's first opening in a process showed empty sections for about 0.6 s, then the lists pushed
  them down; now a progress indicator in their place.
- A language chosen in the system's app settings while the app ran left language names, catalog titles and sorted
  lists in the previous language (the default locale is not always updated); the application now sets it from the new
  configuration.
- A monolingual Japanese dictionary with loanword spellings (デジタル大辞泉) was taken for Japanese → English: its
  definitions are up to a quarter Latin. A definition language other than the word's now needs 40 % foreign letters
  (bilingual dictionaries measured 53–100 %).
- The search screen opened from a selection menu showed a white card on the dark screen for the seconds before its
  first result; the card now stays hidden until the first render sets its theme.

Baselines (debug build, host idle):
- NF1 scans: capture 97 ms; the first scan after start ML Kit draft 1.06 s, cloud final 2.36 s; later scans draft
  0.3–0.8 s, final 0.5–2 s; 20 tap rescans of `pitch.png`: draft 0.40–0.58 s, final 0.74–0.84 s.
- NF2 lookups: 1–117 ms from the log.
- NF3 drag: 82 % janky frames, median frame 30–34 ms, 90th percentile 65–73 ms, the same over text and over an empty
  area; per frame about 10 ms of layout and 16 ms of draw command issue in the full-screen layer window. The entry
  keeps up. The 10 % target written before the first measurement does not hold on the emulator; compare with this run.
- NF4: no `android.anim.lf` frames and no `SurfaceAnimationRunner` during a pull-out (12 relayouts).
- NF5: no frame brighter than 44/255 (mean) in the dark theme across nine settings pages and back; pages switch in
  one frame.
- NF6 cold start (`am start -W`, after Home and `run-as kill -9`): 3.22–3.30 s COLD, 2.37 s WARM.
- NF7: tap to page 0.03–0.33 s for most settings pages; the first opening in a process of Bubble 1.07 s and Anki and
  audio 0.73 s.
- NF8 PSS: 186 MB docked after start (English active, no scan); 272 MB after the first Japanese scan; 251 MB after 20
  more; 241 MB docked afterwards, of which 14 MB was the debug image viewer's bitmap (gone with the viewer). No growth.
- NF9 release APKs against v0.2.3: arm64-v8a 50.7 MB (48.4), armeabi-v7a 45.6 MB (43.6), x86_64 51.5 MB (49.1),
  universal 76.4 MB (71.8); the 2.4 MB per ABI are the engine library (+1.3 MB), ML Kit models for more scripts
  (+0.5 MB), code (+0.3 MB) and resources (+0.3 MB). Installed debug build: code 116 MB, data 247 MB with Japanese
  (dictionaries 177 MB) and English (57 MB).
- NF10: download screen, Languages, home and Bubble settings wrap and scroll in de, ru, vi and ja.
- NF11: the bundled set on first run about 20 s; English imports: en-ru 6.9 s, wordfreq 3.0 s, IPA 5.6 s.
- Chinese wordfreq with Traditional spellings (203,820 rows, 1.5 MB): built in 15.6 s on the debug build (2026-10-15).
- NF12: the search shows the result about 0.3 s after the last key; typing does not wait for it.

Observations, not changed:
- The debug build reports version 0.2.2 (code 202) because the v0.2.3 tag is on `main` only; a v0.2.3 install
  upgrades to it only with `adb install -d`. Resolved by the merge into `main`.
- Moving a floating bubble over a screen that changed since its scan looks up the old scan (documented: moving does
  not rescan).
- The download screen's catalog texts stay in the previous language when the interface language changes while that
  screen is open; they follow on the next opening.
- Pitch accent red stays in the e-ink popup (popup.css keeps the accent colors).
- A file picked for import is copied for 1.5–3 s (large archives) before its card appears, with nothing shown meanwhile.
- After ⟲ cancelled a collection import, the Import from Yomitan screen still says "Importing N dictionaries in the
  background" until it is opened again.
- The Yomitan settings import summary marks "Text replacements … are not imported" with ✓ like the imported parts.
- The emulator's top gesture strip is 192 px, so a top dock in a fullscreen app stays at the line (the edge needs the
  strip plus a third of the bubble to fit in the bubble's size); the bottom dock goes to the edge.
- App text on the system Settings page took 0.8–2.3 s to read (23 nodes), once 261 ms; `ocr-engines.md` has 170–620 ms.
- One ANR right after `svc wifi disable`: the home window waited 19 s for a focus event while the composer and
  allocator HALs, SystemUI and the app were all busy in the kernel; not reproduced in three tries. The system then
  showed the ANR dialog again about 15 s after each "Wait", even for a restarted process, until "Close app".
- English phrasal verbs in the past ("picked up") find only the verb: wty-en-ru gives phrases no part of speech, and
  Yomitan's phrasal-verb rule needs a verb entry. Yomitan behaves the same; asked in the plan.
- Deleting a downloaded or added popup font does not ask first, unlike a dictionary; asked in the plan (now asks, POP18).
