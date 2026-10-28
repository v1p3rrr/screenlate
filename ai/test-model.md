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
- The AVD has no quick-boot snapshot (saving one hangs, see `ai/notes/build-environment.md`): it cold-boots and keeps
  the state of earlier runs. Check `dumpsys package com.vpr.screenlate.debug` before relying on an earlier run, and
  shut it down with `adb shell reboot -p`.
- Install: `scripts/debug-device.sh install` (also enables the accessibility service). Test images:
  `scripts/debug-device.sh images`, then `show <file> [default|swipe]` as the backdrop. `scripts/text-image.py` renders
  new ones; `PITCH=96` puts its lines close enough to read as one paragraph (at the default 160 each line is one). `zh-traditional.png` and `zh-vertical.png` are its output with `FONT=C:/Windows/Fonts/msjh.ttc`: the four
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
  `settings put global http_proxy 10.0.2.2:8899` (revert with `settings put global http_proxy :0`, then `settings delete global http_proxy` (deleting it alone leaves `global_http_proxy_host`/`_port` set and the proxy in force; `dumpsys connectivity | grep -A2 ProxyTracker` shows it)). `adb emu network speed gprs` does not slow downloads
  over the emulator's Wi-Fi (a 14 MB language installed in seconds): to hold a download, turn Wi-Fi and data off
  (`svc wifi disable`, `svc data disable`).
- Space: the AVD's /data keeps about 0.3–0.5 GB free with the languages installed, at the system's low-storage line
  (installs and imports fail). `settings put global sys_storage_threshold_percentage 1` and
  `sys_storage_threshold_max_bytes 104857600` lower it for a run; delete both settings afterwards. Since the Khmer
  run (2026-10-20) about 0.39 GB stays free and a full debug build (126 MB) no longer installs: install an x86_64-only
  build (`ai/notes/build-environment.md`, "short /data"), or delete the dictionaries of languages already checked
  (owner, 2026-10-20); AnkiDroid and other data stay untouched.
- Instrumented runs: per module, e.g. `./gradlew :dictionary:engine-hoshidicts:connectedDebugAndroidTest`; a full
  `connectedDebugAndroidTest` uninstalls the debug app at the end, so reinstall afterwards. The app's
  `SearchLanguageTest` and `InstallationPopupTest` need the API 37 WebView and fail on API 30: install the test APK
  (`assembleDebugAndroidTest`, `adb install -r -t`) on `Pixel_10_Pro` and run them with `am instrument -w -e class …
  com.vpr.screenlate.debug.test/androidx.test.runner.AndroidJUnitRunner`, which keeps the installed app and its data
  (their fixture is isolated); uninstall the test APK and run `debug-device.sh install` to bind the service again.
- Images from other apps: the Files app shares a file to the app with a read grant (select it, then Share). `am start
  -a android.intent.action.SEND --eu android.intent.extra.STREAM …` from the shell carries no grant, so it only checks
  the "could not be opened" path; with `--grant-read-uri-permission` and a `content://0@<app>.anki.media/…` link it
  checks that the app's own provider is refused. `am start` cannot set clip data, so a request straight to the main
  screen (`open=shared-image` with a clip) needs a temporary instrumented test that starts `MainActivity` with it; it
  runs in the app's process, so a `file:` link to the app's files is what an attacker would send. On API 30 the Files app and its picker ignore `input tap` and long presses on file
  items (only buttons react): select through ⋮ → Select all, or move to the item with `KEYCODE_TAB` and the arrow keys
  and press `KEYCODE_ENTER`.

## Cases

Columns: ID, the check (action → expected), category, coverage, the date of the last check on this branch.

### FR: install, first run, upgrade

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| FR1 | Fresh install → the first run asks for the language, Japanese preselected; Japanese → its download screen with the main dictionary (Jitendex for English), Jiten, Kanjium pitch accents, the transcription, KANJIDIC and PaddleOCR v6 ticked; after Confirm they download and install, home shows them ready; the APK carries no dictionaries (`main`: the bundled set installs without the screen) | E | U LanguageProfilesTest, M | 2026-10-18 |
| FR2 | Fresh install, English chosen → the download screen opens; Japanese stays off and none of its dictionaries are installed | F | M | 2026-10-14 (API 30) |
| FR3 | Fresh install → the translation button is off, "Translation in Anki notes" on | E | U TranslationSettingsTest, M | 2026-10-18 |
| FR4 | Upgrade from v0.2.3 with changed settings (bubble size and side, scan length, Anki deck and templates, audio sources, popup font and CSS, theme, hidden apps, dictionary order, switches and sort dictionary) → no first-run question, Japanese on, everything kept | E | M | 2026-10-18 |
| FR5 | Upgrade from v0.2.3 with the translation button untouched → on; turned off in v0.2.3 → stays off | E | U TranslationSettingsTest, M | 2026-10-18 (turned off) |
| FR6 | Upgrade from v0.2.3 → the accessibility disclosure is not asked again; deleted bundled dictionaries stay deleted | F | M | 2026-10-22 |
| FR7 | A backup made by v0.2.3 (settings, and with dictionaries) restores into the current build: every part listed, settings applied to Japanese, the translation button kept on | E | U BackupFormatTest, M | 2026-10-18 |
| FR8 | The accessibility disclosure: the first tap on a button that opens the accessibility settings shows it; Agree opens the settings, later taps open them at once; About shows the same text | O | U AccessibilityAgreedTest, M | |

### BUB: the bubble

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| BUB1 | Pull the bubble out of the dock over Japanese text → a scan starts and the entry of the word under the aim opens | E | M | 2026-10-27 |
| BUB2 | Move the floating bubble → the entry follows the aim without a new scan | E | M | 2026-10-27 |
| BUB3 | Tap the floating bubble → a new scan, recognized lines flash | F | M | 2026-10-25 |
| BUB4 | Hold the floating bubble → the menu (copy paragraph, copy all, open Screenlate) above the bubble, below when there is no room; Back or a tap outside closes it without other effect | F | U MenuPlacementTest, CopyMenuTextTest, M | 2026-10-14 |
| BUB5 | With two or three quick languages the hold menu shows their flags, the active one ringed; a tap switches the language, the docked glyph changes at once, the next scan uses the new language; one quick language → no flag row | E | M | 2026-10-27 |
| BUB6 | Double tap → the aim switches between above the finger and the bubble center | O | M | |
| BUB7 | ✕ → the entry closes, the bubble stays and keeps the text; it stays closed until the bubble moves or scans | F | M | 2026-10-14 |
| BUB8 | Drag to the left or right edge → docks once the center passes the edge or on release at the edge; top and bottom only with "Top and bottom edges" | F | U DockPlacementTest, M | 2026-10-25 |
| BUB9 | Drag the docked bubble along the edge → the dock moves | O | M | |
| BUB10 | The dock stays clear of the bars and the system gesture strips; an open keyboard lifts a bottom dock; in a fullscreen app (`show <file> swipe`) the top and bottom docks go to the edge with a third past the strip | F | U DockSettingsTest, M | 2026-10-14 |
| BUB11 | Rotating the screen → the entry closes, the bubble docks on the same side | F | M | 2026-10-14 |
| BUB12 | The Quick Settings tile hides and shows the bubble; holding it opens Screenlate | O | M | |
| BUB13 | Home's switch and Bubble → "Show the bubble" hide and show it | F | M | 2026-10-14 |
| BUB14 | An app in the hidden apps list → the bubble hides there and comes back outside it | O | M | |
| BUB15 | E-ink: the hold menu is white with a black frame, black text and a black ring around the active code, no shadow or ripple | F | M | 2026-10-14 |
| BUB16 | No app task; "Look up in Screenlate" in a selection menu, then a button there that opens the app (the grey ➕ → "Open Anki settings", or "Manage dictionaries"); Home → the hold menu's "Open Screenlate", then the launcher icon → the same screen comes back, `dumpsys activity activities` lists one MainActivity, the task's root intent is the launcher's (`dumpsys activity recents`); Back leaves the app | F | I OpenAppTest, M | 2026-10-28 (`main`: the hold menu over a task started by an explicit intent; the search path and OpenAppTest not run) |
| BUB17 | Hold the bubble over any Screenlate page (Home, settings, a photo) → no Open Screenlate in the menu; over another app it is there; with nothing to copy, no language row and Screenlate in front, holding opens no menu and gives no vibration | N | M | 2026-10-28 (API 37, also on `main`; the vibration not seen, the emulator records none) |
| BUB18 | Hold the docked bubble without moving it → the menu with the quick-language flags (two or more quick languages) and Open Screenlate (over another app), no copy items even after a scan; a flag switches the language and the docked glyph changes; over Screenlate with one quick language nothing opens; a move before the hold time still pulls the bubble out or moves the dock; the menu fits on screen at each dock side | N | M | |

### OCR: text sources and recognition

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| OCR1 | Screen recognition, both engines → the on-device draft of the band around the aim, then the cloud result; the source chip says Lens | E | U CompositeOcrTest, FocusBandTest, M | 2026-10-27 |
| OCR2 | Cloud unreachable (proxy that never answers) → the on-device result stays, ⚠ next to the source, a tap gives the reason; without any network → the offline message | F | U OcrStatusTest, M | 2026-10-14 |
| OCR3 | Engines: cloud only (nothing without network) and device only (chip "On-device", OCR boost unavailable) | F | M | 2026-10-14 |
| OCR4 | Reduce load → the whole screen is read on the device only after 3 s without a cloud answer (scan log) | O | M | |
| OCR5 | OCR boost on demand and always → small text (`x-small.jpg`) found; extra requests in the log | O | M | |
| OCR6 | Read app text → the app's own text is used (exact highlight, lines flash at once) next to the screen recognition | F | U AccessibilityTextTest, M | 2026-10-14 |
| OCR7 | App text only → no screenshot; an app without text shows the message; holding ➕ still takes a picture | F | M | 2026-10-14 |
| OCR8 | A secure window → "This app does not allow screenshots." | O | M | |
| OCR9 | Vertical text (`manga-page.webp`) → the word under the aim, a word continued in the next column found whole | E | U ReadingOrderTest, M | 2026-10-18 |
| OCR10 | A word broken at a line end is looked up whole (Japanese; English "give up") | F | U TextLayoutTest, M | 2026-10-18 |
| OCR11 | On-device recognition per script: Japanese, English, Chinese, Korean and Russian samples with the matching language active | F | U DeviceOcrTest, M | 2026-10-14 (ja, en), 2026-10-15 (zh, ko), 2026-10-18 (ja, en, ru with the downloadable models) |
| OCR12 | A language the device cannot read → scanned by the cloud alone; with device only → the message that the device does not support it | O | U DeviceOcrTest, M | |
| OCR13 | Character highlight lines up with the characters (corrected on-device boxes) | O | M | |
| OCR14 | No word at the aim on a kanji (`kanji-fallback.png`) → its kanji entry | F | M | 2026-10-14 |
| OCR15 | A language that is not ready → the scan shows the explanation and the installation link before capturing | F | I InstallationPopupTest, M | 2026-10-14 |
| OCR16 | Chinese with cloud recognition: Simplified (`zh-sample.png`, 图书馆 with pinyin), Traditional (`zh-traditional.png`, 沒有關係) and vertical columns read right to left (`zh-vertical.png`: 館 alone, 意思 highlighted down its column). Rescan with a tap after changing the picture: a floating bubble keeps its scan | F | U TextLayoutTest, ReadingOrderTest, M | 2026-10-15 |
| OCR17 | Korean (`ko-mixed.png`): the cloud reads Hangul, hanja (韓國語, 大學校, 歷史) and Latin (TV, PC); the device recognizer reads Hangul and Latin but not hanja (大學校에서 → 푸에서), shown with the "On-device" chip | F | M | 2026-10-15 |
| OCR18 | Turning on a language with a recommended Paddle model (Russian): the download screen ticks PaddleOCR with the "main" switch, does not list the detector, and the model's size and the total include it; after the download both model directories exist | E | U LanguageSetupTest, M | 2026-10-18 (Turkish, Latvian) |
| OCR19 | A scan with a Paddle model active: the draft comes from DEVICE_MODEL (log `OCR draft from DEVICE_MODEL`) and the lookup runs on it (Russian, `ru-page-light.png`) | E | M | 2026-10-18 |
| OCR20 | The on-device model dialog (Bubble settings, On-device model, a language): download PaddleOCR v6 with its detector (size with it), `detector.properties` unpacked; languages ML Kit does not read that recommend it switch to it when they have no explicit choice, and languages ML Kit reads stay on ML Kit (OCR22) | F | U OcrModelListTest, M | 2026-10-17, 2026-10-18 (a Tesseract download), 2026-10-22 (v6 in the Chinese setup) |
| OCR21 | Offline scan with PaddleOCR v6 (Japanese, `ja-page-light.png`, Wi-Fi and data off): the draft and the final page come from the device, Lens fails with OfflineException; words are found | F | M | 2026-10-18 |
| OCR22 | Turning on a language ML Kit reads (German): the on-device category lists "ML Kit (built in)" first, ticked and locked, with no size; the catalog models are unticked, the recommended one marked "More accurate"; nothing is missing with no model ticked. Ticking PaddleOCR shows the "main" switch on ML Kit and on the model; the model ticked becomes the language's model unless ML Kit stays main. Russian (no ML Kit) still ticks its model (OCR18) | F | U OcrModelsTest, LanguageSetupTest, M | 2026-10-19 (Polish; German was on already) |
| OCR23 | Deleting a model while a scan uses it (a long Paddle whole-screen pass): the model's directory gets a `.deleted` marker, the scan finishes, then the directory goes; a marked directory left by a killed process goes at the next start | O | U OcrModelStoreTest, M | 2026-10-19 |
| OCR24 | Damaged model files (a file of an installed model truncated with adb): after Home's refresh, the card "Recognition models whose files are damaged" lists the model with "Download again" and "Remove"; while it downloads again the card is gone; scans use the next model or ML Kit meanwhile. An engine failure on a model rehashes its files and marks it damaged on a mismatch | O | U OcrModelStoreTest, DeviceOcrTest, DamagedModelsTest, M | 2026-10-19 (Tesseract eng truncated) |
| OCR25 | Model updates: with a catalog that names another version of an installed model (local catalog), "Check for updates" on the Dictionaries screen lists the model with its size beside the dictionaries; "Update" or "Update all" downloads it, the installed version works until the new one replaces it | F | U UpdateCheckTest, M | 2026-10-19 |
| OCR26 | App text across a wrap in a language written with spaces (an English paragraph in an app that reports character positions, wrapped mid-sentence, and one with an explicit line break): the word at the start of the second line looks up alone, not joined to the last word before the wrap; a phrasal verb split by the wrap ("pick" / "up") is found; `{sentence}` has a space at the wrap. Japanese app text across a wrap still joins without one | F | U AccessibilityTextTest, M | 2026-10-19 |
| OCR27 | A model chosen in the on-device model dialog is loaded and warmed up at once, with no scan ("Loaded …" and "On-device recognition for … warmed up in … ms" right after the tap); the same when a model of the active language is installed or deleted. No UI dump after the choice: a dump rebinds the service, which warms the model up anyway | F | U CompositeOcrTest, DeviceOcrTest, M | 2026-10-19 |
| OCR28 | Opening the cloud connection on a touch: with Engines "Both" or cloud, a touch on the bubble (a tap, a pull, a touch on the docked bubble) logs "Lens: connected in … ms" unless the cloud was contacted in the last 30 s; the scan's request waits for it up to 3 s; the first scan after 5 idle minutes gets the cloud result in the usual time, not ~2 s. Nothing opens in Device mode or with app text only; offline the opening fails at once and the scan does not wait for it | F | U PreconnectionTest, CompositeOcrTest, M | 2026-10-19 |
| OCR29 | App text in a right-to-left language: an Arabic file name in the Files app ("اشتريت iPhone 15 من السوق الكبير يوم الجمعة.txt", pushed to Download and renamed with `adb shell mv`) is read as app text (the source chip), in reading order from the aim ("السوق الكبير يوم الجمعة."), with iPhone 15 kept in its line; اشتريت finds اِشْتَرَى | F | U AccessibilityTextTest, M | 2026-10-20 |
| OCR30 | A language no on-device model reads (Persian): the setup screen has no recognition section; the Bubble settings list its on-device model as "None", and its dialog says no on-device model reads the language; with Engines "Both", cloud recognition alone reads the screen ("No on-device recognition for fa; cloud only", then "OCR final from LENS"); offline the popup says there is no network connection and on-device recognition does not support the language; Persian digits (۱۲, ۱۴۰۳) and "iPhone 15" keep their order | F | U CatalogFileTest, OcrModelsTest, M | 2026-10-20 |
| OCR31 | Hebrew recognition: cloud recognition reads the lines in reading order, gives the gershayim and the geresh as ASCII " and ' (ארה"ב finds ארה״ב, וצה"ל finds צבא הגנה לישראל through ו) and the maqaf as a word break (ובית חולים finds בית חולים); a line that mixes a number after ב־, ₪ and "iPhone 15" is put in the order of its word boxes; offline, Tesseract heb fast reads the screen ("OCR final from DEVICE_MODEL", Lens: OfflineException): והבית finds הבית, ארה"ב ארה״ב, ג'ירפה ג׳ירפה, pointed קוֹרֵא קורא; the maqaf comes as a hyphen (ובית-חולים finds בית) and Latin is garbled | F | U LensProtocolTest, M | 2026-10-20 |
| OCR32 | Kazakh recognition: cloud recognition reads ә, қ, ң, ұ, і and the hyphens (АҚШ-қа, 1990-ға) of `testdata/ocr/kk-lines.png`; offline, PaddleOCR's Cyrillic model reads the screen with the Kazakh letters right ("OCR final from DEVICE_MODEL"), and Tesseract kaz fast, chosen in the Bubble settings, reads көлдің, қаһарман and Қазақстан right | F | U CatalogFileTest, KazakhSupportTest, M | 2026-10-20 |
| OCR33 | Kyrgyz recognition: cloud recognition reads ң, ө, ү and the hyphens (2007-жылы, 1991-ж.) of `testdata/ocr/ky-lines.png`; offline, PaddleOCR's Cyrillic model reads the screen with the Kyrgyz letters right ("OCR final from DEVICE_MODEL"), and so does Tesseract kir fast, chosen in the Bubble settings | F | U CatalogFileTest, KyrgyzSupportTest, M | 2026-10-20 |
| OCR34 | Swedish recognition (`testdata/ocr/sv-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`): cloud recognition reads å ä ö; offline, ML Kit's Latin model reads the screen and an å it gives as ả or â is written å before the lookup (på, går found; an å it reads as ä, a Swedish letter, stays: Vära finds nothing); PaddleOCR v5 Latin, marked "more accurate" and chosen in the Bubble settings, reads å ä ö right | F | U SwedishSupportTest, LanguageRulesIsolationTest, CatalogFileTest, M | 2026-10-22 |
| OCR35 | Norwegian recognition (`testdata/ocr/no-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`): cloud recognition reads æ ø å; offline, ML Kit's Latin model reads the screen and an å it gives as ả, â or ä is written å before the lookup (på, går, år found); PaddleOCR v6, marked "more accurate" and chosen in the Bubble settings, reads æ ø å right | F | U NorwegianSupportTest, LanguageRulesIsolationTest, CatalogFileTest, M | 2026-10-22 |
| OCR36 | Danish recognition (`testdata/ocr/da-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`): cloud recognition reads æ ø å (» « may come as >> <<); offline, ML Kit's Latin model reads the screen and an å it gives as ả, â or ä is written å before the lookup (på, går, år found); PaddleOCR v5 Latin, marked "more accurate" and chosen in the Bubble settings, reads æ ø å right | F | U DanishSupportTest, LanguageRulesIsolationTest, CatalogFileTest, M | 2026-10-22 |
| OCR37 | Romanian recognition (`testdata/ocr/ro-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`): cloud recognition reads ă â î ș ț (ş ţ found as ș ț); offline, ML Kit's Latin model reads the screen, and a word whose marks it drops (tara, dupa) still finds țară and după; PaddleOCR v5 Latin, marked "more accurate" and chosen in the Bubble settings, reads the screen (every engine may read Dl. as DI.) | F | U RomanianSupportTest, LanguageRulesIsolationTest, CatalogFileTest, I LanguageLookupTest, M | 2026-10-22 |
| OCR38 | Latin recognition (`testdata/ocr/la-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`, a text with macrons and æ): cloud recognition reads the screen; offline, ML Kit's Latin model reads it (ō ū ē may come as õ ö ũ ü ễ, which the lookup drops with every mark); PaddleOCR v6, marked "more accurate" and chosen in the Bubble settings, reads the screen (both read the capital I of Iulius as l) | F | U LatinSupportTest, CatalogFileTest, I LanguageLookupTest, M | 2026-10-22 |
| OCR39 | Lithuanian recognition (`testdata/ocr/lt-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`, a text with ą č ę ė į š ų ū ž and Ū): cloud recognition reads the screen; offline, ML Kit's Latin model reads it, an Ū, ė or į it reads as Ü, é or ị is found as the Lithuanian letter, and a word whose marks it drops is found too (Ukis finds ūkis); Tesseract lit fast, marked "more accurate" and chosen in the Bubble settings, reads the screen; app text of a Lithuanian sentence with a foreign é (José, AnkiDroid's note editor) keeps the é | F | U LithuanianSupportTest, CatalogFileTest, M | 2026-10-22 |
| OCR40 | Slovak recognition (`testdata/ocr/sk-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`, a text with á ä č ď é í ľ ň ó ô š ť ú ý ž): cloud recognition reads the screen; offline, ML Kit's Latin model reads it, and a word whose marks it drops (ť ď ľ read as t d l) is found through the keys without marks (tazke finds ťažké); Tesseract slk fast, marked "more accurate" and chosen in the Bubble settings, reads the screen | F | U SlovakSupportTest, CatalogFileTest, M | 2026-10-22 |
| OCR41 | Slovenian recognition (`testdata/ocr/sl-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`, a text with č š ž and »…«): cloud recognition reads the screen (» « may come as >> <<); offline, ML Kit's Latin model reads it, and a word whose caron it drops is found through the keys (zena finds žena); PaddleOCR v5 Latin, marked "more accurate" and chosen in the Bubble settings, reads the screen | F | U SlovenianSupportTest, CatalogFileTest, M | 2026-10-22 |
| OCR42 | Esperanto recognition (`testdata/ocr/eo-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`, a text with ĉ ĝ ĥ ĵ ŝ ŭ): cloud recognition reads the screen; offline, ML Kit's Latin model reads it, a hat letter it reads as Š Č ũ ű shows as Ŝ Ĉ ŭ in the recognized text, and a word whose hat it drops is found through the keys; Tesseract fast, marked "more accurate" and chosen in the Text recognition settings, reads the screen; no PaddleOCR model is offered | N | U EsperantoSupportTest, CatalogFileTest, M | 2026-10-28 |
| OCR43 | Albanian recognition (`testdata/ocr/sq-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`, a text with ë ç, «…» and “…”): cloud recognition reads the screen; offline, ML Kit's Latin model reads it; PaddleOCR v5 Latin, marked "more accurate" and chosen under Text recognition → On-device model, reads the screen; a word an engine reads with ě is looked up with ë | F | U AlbanianSupportTest, CatalogFileTest, M | 2026-10-28 |
| OCR44 | Swahili recognition (`testdata/ocr/sw-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`, a text with ’ and “…”): cloud recognition reads the screen; offline, ML Kit's Latin model reads it (no downloadable model is recommended); Ng’ombe finds ng'ombe | F | U SwahiliSupportTest, CatalogFileTest, M | 2026-10-28 |
| OCR45 | Welsh recognition (`testdata/ocr/cy-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`, a text with ŵ ŷ and mutated words): cloud recognition reads tŷ and tŵr; ML Kit reads tŷ as tý, and the popup on tý finds tŷ (the OCR-only variant); Tesseract cym fast reads tŷ and tŵr | F | U WelshSupportTest, CatalogFileTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-28 |
| OCR46 | Irish recognition (`testdata/ocr/ga-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`, a text with the fada, mutated words and d'fhág): cloud recognition reads the fada and the lowercase prefixes before a capital (nGaillimh, hÉireann, tSín); ML Kit reads the prefixes and the fada of lowercase letters and may drop a capital's (hEireann), which then finds nothing, as Irish words are looked up as written | F | U CatalogFileTest, M | 2026-10-28 |
| OCR47 | Scottish Gaelic recognition (`testdata/ocr/gd-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`, a text with the grave accent, hyphenated prefixes, dh'fhàg, th'ann and the acute of mhór): cloud recognition and ML Kit read the grave vowels and the prefixes with their hyphens (na h-Alba, an t-sràid, ar n-eilean) | F | U CatalogFileTest, M | 2026-10-28 |
| OCR48 | Finnish recognition (`testdata/ocr/fi-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`: clitics and possessives, EU:n and STT:lle, 1990-luvulla, 20-vuotias, a long compound): cloud recognition and ML Kit read ä ö, the colons and the hyphens | F | U CatalogFileTest, M | 2026-10-28 |
| OCR49 | Hungarian recognition (`testdata/ocr/hu-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`: ő and ű words, prefixed verb forms, 2023-ban and EU-ban, a long compound): cloud recognition reads ő ű, á é í ó ú and the hyphens; ML Kit reads ű as ú and may capitalize ő, and such a word is still found (LK79) | F | U CatalogFileTest, M | 2026-10-28 |
| OCR50 | Estonian recognition (`testdata/ocr/et-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`: õ, ä, ö, ü words, the negative forms of olema, NATO-ga and 5-ga, „…“ quotes, a compound): cloud recognition reads õ ä ö ü, š ž and the hyphens; ML Kit may read õ as ö, and such a word is still found (LK80) | F | U CatalogFileTest, M | 2026-10-28 |
| OCR51 | Azerbaijani recognition (`testdata/ocr/az-sample.png`, `scripts/text-image.py` with `FONT=C:/Windows/Fonts/segoeui.ttf`: ə, ı, ğ, ö, ü, ç, ş words, noun and verb endings, 2020-ci and ABŞ-da, «…» quotes; `az-misread.png` prints e and i where ə and ı belong): cloud recognition reads ə ı İ and the hyphens; ML Kit may read ə as e and ı as i, and such a word is still found (LK81) | F | U CatalogFileTest, M | 2026-10-28 |

### LK: lookup

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| LK1 | Japanese: deinflection, respellings (1人, 々, ッ, dots and spaces, radical look-alikes such as U+2F08 for 人), romaji when on, Latin entries (ＯＬ, Tシャツ) | E | I LookupPipelineTest, HoshidictsEngineTest, U LookupVariantsTest, JapaneseSupportTest | 2026-10-18 |
| LK2 | Sorting by the sort dictionary and dictionary order, as Yomitan | F | U YomitanSorterTest, SortFrequencyTest | 2026-10-14 |
| LK3 | Scan length and entries per lookup apply | O | U QueryScanLengthTest, M | |
| LK4 | "Kanji of the word" adds the kanji entries below the results | O | M | |
| LK5 | English: deinflection, form-of entries, phrases, a table cut short; with the wordfreq dictionary "walk" sorts before "walke" | E | I LanguageLookupTest, M | 2026-10-18 (I) |
| LK6 | Search screen with typed text; "Look up in Screenlate" in a selection menu opens it | E | I ProcessTextTest, M | 2026-10-22 (a phrase through the selection menu) |
| LK7 | Search while the language is not ready → status and link; it searches once the dictionary is ready | F | I SearchLanguageTest | 2026-10-14 |
| LK8 | No enabled dictionary with definitions → the popup and search say so, search offers "Manage dictionaries" | O | M | |
| LK9 | Chinese search by typed pinyin: "nihao", "ni hao", "ni3hao3", "nǐhǎo" find 你好; "shi" lists 是 时 事 first; "shi4" puts fourth-tone words first; "lv", "nü3" (ü); "xi'an" puts 西安 first; words show in the script chosen on the Languages screen; a dictionary imported again builds its index | F | U ChinesePinyinTest, ReadingIndexTest, ReadingIndexBanksTest, ReadingWordsTest, M | 2026-10-14 |
| LK10 | Chinese Wiktionary form-of tags in the interface language: 吓 lists 下 with "alternative Simplified Cantonese", in Russian "альтернативное упрощённое кантонское"; 哄 with Hokkien | O | U FormOfTagsTest, M | 2026-10-15 |
| LK11 | Chinese lookups: the longest word in the text's script (图书馆看书 → 图书馆, 圖書館看書 → 圖書館, each with its `lang`), a radical look-alike (U+2F47 for 日), a fixed Traditional script shows 图书馆 as 圖書館, typed pinyin in the chosen script, typed tones first | E | I ChineseLookupTest, U ChineseSupportTest | 2026-10-18 |
| LK12 | English phrasal verbs with wty-en-ru (phrases without a part of speech), testdata/ocr/en-phrasal.png: "picked up" → "pick up" with "past", "gives up" → "give up" with "3rd pers. sing. pres"; "closed to" shows "closed", "close", no "close to"; "looked for" shows "look", no "look for" | F | I LanguageLookupTest, M | 2026-10-15 |
| LK13 | Korean lookups: 먹었습니다 → 먹다, 공부하고 → 공부하다 and 가르친다 → 가르치다 with their endings; the aim inside a spaced word starts at its first syllable (보면서 → 보다); 한국어를 → 한국어; TV를 → TV (Latin letters belong to the word), 3월에 → 월 (digits do not); 韓國語 and 大學校 from KRDICT, their Hangul words through Wiktionary's "hanja spelling", the word's hanja below the results; 마셨어요 lists 마시다 among Wiktionary's three lemmas | E | I KoreanLookupTest, U KoreanSupportTest, M | 2026-10-18 (I) |
| LK14 | Korean Wiktionary form-of tags in the interface language ("hanja form" → "hanja spelling", "informal past indicative/interrogative polite"); a tag with leftover gloss ("hanja form   the Korean language”)", about 1,000 entries) shows the tag alone, in Anki's `{conjugation}` too | O | U FormOfTagsTest, I KoreanLookupTest, M | 2026-10-15 |
| LK15 | Japanese form-of rows with wty-ja-ja (`testdata/ocr/form-of-ja.png`: `scripts/text-image.py` with "じどうで動く" "社會の仕組み" "豪州へ行く"): じどう lists 自動, 児童 and the other kanji, no じどう entry of empty items with "non-lemma"; 社會 shows the old-form entries with their "⟶ 社会" links and no empty bullet, then 社会 with the tag of the first dictionary that leads there ("redirected from 社會" with Jitendex); 豪州 brings オーストラリア with "alternative"; a deinflected form keeps its own chain | E | I HoshidictsEngineTest, M | 2026-10-18 (I) |
| LK16 | Spanish lookups (`testdata/ocr/es-sample.png`, `scripts/text-image.py` with Segoe UI): comieron → comer with "preterite"; agua with its wordfreq rank and transcriptions; typed tenés → tener with "informal second person singular present indicative vos form"; ¿ and ¡ start a sentence, «» are quotes, "p. ej." and "Sra." do not end one | E | U SpanishSupportTest, SentenceTest, FormOfTagsTest, M | 2026-10-18 |
| LK17 | French lookups (`testdata/ocr/fr-sample.png`): L’eau → eau (an elided article starts a new word; aujourd’hui and presqu’île stay whole), mangions → manger with "imperfect indicative", finirent → finir with "preterite"; « » are quotes, "M." and "p. ex." do not end a sentence | E | U FrenchSupportTest, SentenceTest, M | 2026-10-18 |
| LK18 | German lookups (`testdata/ocr/de-sample.png`): gegessen → essen with "past participle", größer → groß with "comparative", aufgemacht → aufmachen; „“ and »« are quotes, ordinals ("3. Oktober") and "z. B." do not end a sentence, a year does | E | U GermanSupportTest, SentenceTest, M | 2026-10-18 |
| LK19 | Italian lookups (`testdata/ocr/it-sample.png`): mangiato → mangiare with "past participle" (from the dictionary's form entries; Yomitan has no Italian inflection rules), dell'amico → amico, l'acqua fresca → the phrase; «» are quotes, "sig." and "ecc." do not end a sentence; no audio regions | E | U ItalianSupportTest, SentenceTest, M | 2026-10-18 |
| LK20 | Portuguese lookups (`testdata/ocr/pt-sample.png`): comemos → comer with "first person plural indicative preterite", maiores → maior with "plural masculine/feminine" (form entries; Yomitan has no Portuguese inflection rules), gota d'água → água with BR and PT transcriptions; «» are quotes, "Sra." and "p. ex." do not end a sentence | E | U PortugueseSupportTest, SentenceTest, M | 2026-10-18 |
| LK21 | German compounds without an entry (Search): the line "Compound: Küchentisch + Lampenschirm" above the entries (in the interface language), none for a word with its own entry; Küchentischlampenschirm → Küchentisch, Lampenschirm; Bahnhofsuhrzeiger → Bahnhofsuhr, Zeiger; Arbeitsschreibtisch → Arbeit (with the arbeiten imperative), Schreibtisch, the linking s skipped; Haustürschlüssel, which has an entry, shows only that entry | E | U CompoundsTest, M | 2026-10-18 |
| LK22 | Polish lookups (`testdata/ocr/pl-sample.png`): koty → kot with "nominative/accusative/vocative plural", czytaliśmy → czytać with "plural past masculine personal", oknie → okno (form entries; Yomitan has no Polish inflection rules); „” are quotes, "np.", "w." and the ordinal "5." do not end a sentence; Ł in the bubble, Polish flag; audio tries Wiktionary first | E | U PolishSupportTest, SentenceTest, FormOfTagsTest, M | 2026-10-18 |
| LK23 | Turkish lookups (`testdata/ocr/tr-sample.png`): İstanbul'a → İstanbul (the suffix after the apostrophe belongs to the name), gittik → gitmek, kitabı → kitap with "accusative singular definite", IŞIK → ışık (not işik; dotted and dotless i), çocuklar → çocuk; "vb.", "Dr." and the ordinal "3." do not end a sentence; Ğ in the bubble, Turkish flag; no transcription dictionary in the catalog | E | U TurkishSupportTest, SentenceTest, glue.test.mjs, M | 2026-10-18 |
| LK24 | Latvian lookups (`testdata/ocr/lv-sample.png`): grāmatu → grāmata with "accusative singular", lasīju → lasīt with "first-person singular past indicative", Rīgā → Rīga, jālasa → lasīt with "indicative debitive" (form entries; Yomitan has no Latvian inflection rules); „“ are quotes, "u.c.", the ordinal "18." and the year in "1918. gada" do not end a sentence; Ā in the bubble, Latvian flag; no transcription dictionary in the catalog | E | U LatvianSupportTest, SentenceTest, FormOfTagsTest, M | 2026-10-18 |
| LK31 | Dutch lookups (Dutch text from `scripts/text-image.py` or a Dutch web page): honden → hond with "plural", katje → kat with "diminutive", liep → lopen with "singular past" (form entries; Yomitan has no Dutch inflection rules), auto's → auto and zo'n as one word; a compound without an entry shows its parts under "Compound: rijbewijs + foto" (rijbewijsfoto → rijbewijs, foto; werkgeverstafel → werkgever through the plural werkgevers, tafel); Jansen shows its entry with no form step (Jansen → Janssen → Jansen is not followed); „” and “” are quotes, "bijv.", "d.w.z." and "mr." do not end a sentence, "Jan." does; Ĳ in the bubble, Dutch flag; the download screen lists nl-nl unticked and the transcription dictionary; audio tries Lingua Libre first | E | U DutchSupportTest, SentenceTest, CompoundsTest, FormOfTagsTest, M | 2026-10-19 |
| LK32 | Czech lookups (Czech text from `scripts/text-image.py` or a Czech web page): kočky → kočka with "genitive singular" and the other cases, četla → číst, chodě → chodit with "transgressive", Praze → Praha (form entries; Yomitan has no Czech inflection rules); „“ and »« are quotes, the ordinal in "5. května", "např.", "tj." and "př. n. l." do not end a sentence, also in sans-serif text where OCR reads the l as I; Ř in the bubble, Czech flag; PaddleOCR v6 recommended on the download screen; audio tries Wiktionary first | E | U CzechSupportTest, SentenceTest, FormOfTagsTest, M | 2026-10-19 |
| LK33 | Indonesian lookups (Indonesian text from `scripts/text-image.py` or an Indonesian web page): membaca → baca (form entry, and baca's own entry), rumah-rumah → rumah, dibaca → baca; "Jl.", "No." and "hlm." do not end a sentence, "dll." does; "ID" fits in the docked bubble at every edge, Indonesian flag; audio from Lingua Libre; the download screen lists id-id unticked and the transcription dictionary | E | U IndonesianSupportTest, SentenceTest, glue.test.mjs, M | 2026-10-19 |
| LK34 | Malay lookups (Malay text, `scripts/text-image.py`): membaca (its own entry in wty-ms-en), kucing; baca shows no "Jawi" step (its Jawi spelling only leads back to it); "En.", "Sdn. Bhd." do not end a sentence; "MS" fits in the docked bubble, Malaysian flag; the frequency order from wordfreq | E | U MalaySupportTest, SentenceTest, glue.test.mjs, M | 2026-10-19 |
| LK35 | Filipino lookups (Filipino text, `scripts/text-image.py`): kumain (its own entry, and "complete aspect of kumain"), magluto → luto, magandang → maganda; basa shows no "Baybayin" step and no entry of Basa; "G.", "Sta.", "Blg." do not end a sentence; the language reads "Filipino (Tagalog)" ("Филиппинский (тагалог)" in Russian) on Languages, Home and the download screen; "Ng" fits in the docked bubble, Philippine flag; audio from Lingua Libre, the voice test speaks with a Filipino voice when one is installed; the sentence translation works | E | U FilipinoSupportTest, SentenceTest, TranslationLanguagesTest, glue.test.mjs, M | 2026-10-19 |
| LK37 | Ukrainian lookups (search screen; overlay over a `scripts/text-image.py` image rendered with `FONT=C:/Windows/Fonts/arial.ttf`): книги → книга with "nominative plural" and a wordfreq rank; книга typed as is shows no form chain; зробив → зробити "past"; чита́ю (stress mark) → читати; п’ять, пʼять and п'ять find п'ять; a lone і read by OCR finds the conjunction; "р.", "т. зв." do not end a sentence; flag ua, bubble Ї; audio from Wiktionary; definitions in English and Russian | E | U UkrainianSupportTest, CyrillicLookAlikesTest, SentenceTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-19 |
| LK38 | Belarusian lookups (search screen, overlay): сям’я → сям'я; вады and ва́ды → вада "genitive singular" (a stressed form row); праўда; a lone і read by OCR finds the conjunction; "г.", "г. зн." do not end a sentence; flag by (red-green), bubble Ў; audio from Wiktionary; no frequency list offered | E | U BelarusianSupportTest, SentenceTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-19 |
| LK39 | Greek lookups (search screen; overlay over a `scripts/text-image.py` image rendered with `FONT=C:/Windows/Fonts/arial.ttf`, PaddleOCR `el`): σκυλος and ΣΚΥΛΟΣ → σκύλος with a wordfreq rank and no form step; ποτε shows ποτέ and πότε, πότε only itself; ξαναρώτησε and Ξαναρώτησε → ρωτάω with "ξανα- « …"; καλη ορεξη → καλή όρεξη; a lone Η read by OCR finds the letter and the article; the sentence of a word after "Πού πας;" starts after the ; and keeps "κ.", the ano teleia and «…»; "π.χ." does not end a sentence; flag gr, bubble Ω; audio from Lingua Libre under either code (γάτα: Spiros71, `ell`; μητέρα: MARKELLOS, `gre`), else the voice; the Paddle model downloads from the `ocr-models-1` release and warms up when chosen | E | U GreekSupportTest, SentenceTest, WordfreqDictionaryTest, AudioPagesTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-19 |
| LK36 | Croatian and Serbian, two languages on the shared Serbo-Croatian dictionaries (Latin text `testdata/ocr/sr-latn-sample.png`, Cyrillic `testdata/ocr/sh-cyrl-sample.png`, both from `scripts/text-image.py` with `FONT=C:/Windows/Fonts/arial.ttf`): kuca also finds kuća (house), sesir → šešir, djak → đak; kuća and kuca show kuća without "genitive plural" beside it or behind "+N"; куће → кућа with "genitive singular"; ку̏ћа and kȕća (reading marks) find the word; a long line of c, s and z ("kuca cecescecescecesc") looks up without an error; "npr.", "5. maja", "pr. Kr." and "p. n. e." do not end a sentence; Serbian over Cyrillic text: Јој and је (which cloud recognition reads in Latin letters) show the Cyrillic entries (check the headword's code points with `scripts/popup-eval.mjs`: the letters look alike), a lone у finds the preposition; Serbian over Latin text: a lone u and kući stay Latin; Croatian shows Đ, flag hr and page `lang` hr, Serbian Ћ, flag rs and `lang` sr with the Serbian б, no flag button; both switched from Home and the bubble menu; кућа has the frequency of kuća; Serbian's model list has PaddleOCR v5 Cyrillic recommended and PaddleOCR v6 selectable (loaded and warmed up at once); audio: Croatian ljubav lists Hr-ljubav.ogg, then Sr-љубав.flac; Serbian voda Sr-вода.flac, then Lingua Libre (a Latin title), and the voice item speaks with a Serbian voice; Serbian Latin sentences translate (Bing as sr-Latn); the second language turns on at once with the shared dictionaries; turning one off while the other is on offers nothing shared to delete, with both off one "Turned off" card with a "Turn on" button per language; with a Croatian or Serbian interface, sentences translate into English by default | E | U SerboCroatianSupportTest, SerboCroatianScriptsTest, CyrillicLookAlikesTest, OcrPageLanguageTest, AudioPagesTest, AudioFinderTest, SentenceTranslatorTest, TranslationLanguagesTest, OcrModelsTest, BackupChoiceTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-19 |
| LK25 | Vietnamese lookups (`testdata/ocr/vi-sample.png`): aiming at "sinh" in "học sinh" shows học sinh first (the lookup also starts one and two syllables before), then sinh; "nhà vệ sinh" from its last syllable; hoà and hòa find the same word; "cái" shows its own entries, not the hundreds of nouns it is the classifier of; Hán-Nôm characters labelled "Hán-Nôm spelling"; "TP." does not end a sentence; Ư in the bubble, Vietnamese flag; fonts listed by the vietnamese subset | E | U VietnameseSupportTest, EarlierStartsTest, ShownResultsTest, SentenceTest, M | 2026-10-18 |
| LK26 | Russian lookups (`testdata/ocr/ru-sample.png`, cloud recognition or PaddleOCR v5 Cyrillic on the device): кошки → кошка with "genitive singular" and the other forms, the headword shown as ко́шка with no reading line, the note with `{expression}` кошка and `{reading}` ко́шка; шокирующее → шокирующий → шокировать (the second form-of step); еж finds ёж; text with stress marks (ко́шка) finds кошка; «» and „“ are quotes, "т. е." and "г." do not end a sentence; Я in the bubble, Russian flag; audio tries Wiktionary first; text to speech reads the stressed reading correctly | E | U RussianSupportTest, SentenceTest, reading.test.mjs, popup.test.mjs, M | 2026-10-18 |
| LK27 | German compound part order: a compound without an entry whose part is "lampe" (Küchentischlampe) shows the noun Lampe before the verb lampen among the part's entries; ordinary lookups keep the usual order | F | U CompoundsTest, M | 2026-10-19 |
| LK28 | Stress and tone marks typed as separate characters (app text of a page with stressed words, such as ru.wiktionary.org's page for кошка in the browser): aiming in ко́шка shows кошка and no entries for ко or к; Vietnamese hoà written with a separate tone mark (U+0300) shows the word, not entries for hoa alone, and "hoà bình" first in "Chúng tôi yêu hoà bình."; the highlight of the stressed word is one box; precomposed text unchanged | F | U ShownResultsTest, LanguageRulesIsolationTest, AccessibilityTextTest, glue.test.mjs, M | 2026-10-19 |
| LK29 | Words with two apostrophes (French text "du rock’n’roll", `scripts/text-image.py`): aiming at "roll" looks up rock’n’roll from its start, not "roll"; "va-t’en" still starts at "en", "l’eau" at "eau" | F | U FrenchSupportTest, LanguageRulesIsolationTest, M | 2026-10-19 |
| LK30 | A word hyphenated at a line end (English text with "impor-" ending one line and "tant" starting the next, `scripts/text-image.py`): aiming at either half shows "important" first and highlights both halves; German "Haus- und Gartenarbeit" aimed at "und" shows "und"; "well-" / "known" shows "well-known"; Japanese text with a line ending in "-" unchanged. Cloud recognition; the built-in recognizer is LK40 | F | U LanguageRulesIsolationTest, LookupVariantsTest, LookupStartsTest, HyphenBreaksTest, M | 2026-10-19 |
| LK40 | A word hyphenated at a line end with the built-in recognizer (ML Kit as the language's model, English text with "impor-" ending one line and "tant" starting the next, `scripts/text-image.py` with `PITCH=96`: at the default pitch each line is a paragraph of its own and the halves are looked up apart): aiming at either half shows "important" first and highlights both halves with the hyphen; a line ending in "COVID-19-" or a dash of its own unchanged | F | I MlKitLineEndHyphenTest, U HyphenBreaksTest, M | 2026-10-22 |
| LK41 | Compound splits take the fewest and longest parts: Dutch stadsbestuurder shows "Compound: stads + bestuurder" (not stadsbestuur + der); rijbewijsfoto still rijbewijs + foto, German Arbeitsschreibtisch still Arbeit + Schreibtisch | N | U CompoundsTest, M | — |
| LK42 | Hindi lookups (search screen; overlay over a `scripts/text-image.py` image rendered with `FONT=C:/Windows/Fonts/Nirmala.ttc`, ML Kit Devanagari): पढता and पढ़ता → पढ़ना with its form tags and a wordfreq rank; हूं → होना through the हूँ entry; लडकियां → लड़की; खाना shows खाना and ख़ाना, ख़ाना only itself; हैँ → हैं; किताब aimed at ता is looked up from कि; the sentence of a word after a danda starts after it and keeps "डॉ." and "ए.पी.जे."; flag in, bubble हि; the page's serif face is the phone's Noto Serif Devanagari (`local()`), and Mukta, Hind and Tiro Devanagari Hindi download and are listed for Hindi only; ML Kit reads the screen on the device; audio from Lingua Libre (पानी), else the voice | E | U HindiSupportTest, SentenceTest, WordfreqDictionaryTest, FontCatalogFileTest, MlKitOcrEngineTest, glue.test.mjs, I LanguageLookupTest, SystemFontFilesTest, M | 2026-10-19 |
| LK43 | Bengali lookups (search screen; overlay over a `scripts/text-image.py` image rendered with `FONT=C:/Windows/Fonts/Nirmala.ttc`, ML Kit's Devanagari recognizer): বইটির, বইগুলো (also with ো typed as ে + া) → বই through the endings; ছেলেদের → ছেলে; a form row with a romanization after it (মাসটি) found by its word; the sentence of a word after a danda starts after it and keeps "ড." and "এ.কে."; the language is "Bengali" in the English interface; flag bd, bubble বা; the page's serif face is the phone's Noto Serif Bengali (`local()`), and Hind Siliguri and Tiro Bangla are listed for Bengali only; ML Kit reads the screen on the device; audio from Lingua Libre (বই), else the voice | N | U BengaliSupportTest, SentenceTest, FontCatalogFileTest, MlKitOcrEngineTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-19 |
| LK44 | Georgian lookups (search screen; overlay over a `scripts/text-image.py` image rendered with `FONT` the phone's `NotoSansGeorgian-VF.ttf` and `FALLBACK=C:/Windows/Fonts/arial.ttf` (Sylfaen has no Mtavruli), Tesseract kat fast, then cloud recognition): წიგნს, წიგნები → წიგნი with the rule chip names (declension, nominative) in English and Russian; მას shows its own entry, not მა; a word longer than 16 letters found whole, the Lookup setting showing 24 for Georgian and 16 for English; ᲡᲐᲥᲐᲠᲗᲕᲔᲚᲝ → საქართველო; the sentence keeps "ქ." and "წ."; flag ge, bubble ქ; the page's serif face is the phone's Noto Serif Georgian (`local()`), no downloadable font listed for Georgian; Tesseract fast is the recommended model; audio from Wiktionary (წყალი), else the voice | N | U GeorgianSupportTest, SentenceTest, LookupSettingsRepositoryTest, FontCatalogFileTest, glue.test.mjs, I LanguageLookupTest, SystemFontFilesTest, M | 2026-10-20 |
| LK45 | Armenian lookups (search screen; overlay over a `scripts/text-image.py` image rendered with `FONT` the phone's `NotoSansArmenian-VF.ttf` and `FALLBACK=C:/Windows/Fonts/arial.ttf`, Tesseract hye fast, then cloud recognition): ինչո՞ւ → ինչու, also when tapped after ՞; կարդա՛ and կարդա → կարդալ (imperative); գրքեր → գիրք; Երեվան and Երեվանը → Երևան, ԵՐԵՎԱՆ shows Երևան after the verb form երևան; Եվրոպա found as written; the classical գիւղ → գյուղ; the sentence ends at ։ and at a colon, not at the period or at 10:30 (two sentences on one line: OCR makes every line a paragraph here); a word longer than 16 letters found whole, the Lookup setting showing 24 for Armenian; flag am, bubble Հ; the page's serif face is the phone's Noto Serif Armenian (`local()`), no downloadable font listed for Armenian; Tesseract fast is the recommended model; audio from Wiktionary (ջուր), else the voice | N | U ArmenianSupportTest, SentenceTest, LanguageRulesIsolationTest, LookupSettingsRepositoryTest, FontCatalogFileTest, glue.test.mjs, I LanguageLookupTest, SystemFontFilesTest, M | 2026-10-20 |
| LK46 | Thai lookups (search screen; overlay over an image rendered by `scripts/TextImage.java` at `SIZE=64` with `FONT` the phone's `NotoSansThai-Regular.ttf` and `FALLBACK` the phone's Roboto, as `scripts/text-image.py` does not shape Thai; Tesseract tha fast, then cloud recognition): ห้องสมุด aimed at สมุด shows ห้องสมุด (library) first, then สมุด; โรงเรียน aimed at เรียน shows โรงเรียน first; การตลาด shows การตลาด and การ, not กา; ห้อง shows its own entries without the rooms it is the classifier of; จริงๆ (no space) finds จริง ๆ and the highlight stops at ๆ, นาน ๆ finds นานๆ; น้ำ typed as น + ํ + ้ + า finds น้ำ; กรุงเทพฯ finds กรุงเทพมหานคร; results carry a rank from the Thai National Corpus dictionary; the sentence (translation button) ends at a space between words, not beside ๆ, ฯ or digits, and พ.ศ. holds; flag th, bubble ท; the page's serif face is the phone's Noto Serif Thai (`local()`), and Sarabun, Noto Sans Thai Looped, Kanit and Prompt are listed for Thai and load on the page; Tesseract fast is the recommended model and its text keeps the words of a line together (no spaces between letters); audio for น้ำ from Wiktionary, else the voice | N | U ThaiSupportTest, SentenceTest, LookupStartsTest, TextLayoutTest, TesseractPageTest, CorpusCountsDictionaryTest, CatalogFileTest, FontCatalogFileTest, MlKitOcrEngineTest, glue.test.mjs, I LanguageLookupTest, SystemFontFilesTest, M | 2026-10-20 |
| LK47 | Khmer lookups (search screen; overlay over an image rendered by `scripts/TextImage.java` at `SIZE=64` with `FONT` the phone's `NotoSansKhmer-VF.ttf` and `FALLBACK` the phone's Roboto, the lines given as `@FILE`, a UTF-8 file, since Windows passes Khmer arguments as question marks; cloud recognition, then Tesseract khm fast): សាលារៀន aimed at រៀន shows សាលារៀន (school) first; ក្រុងភ្នំពេញ, which ICU takes as one word, aimed at ពេញ shows ភ្នំពេញ (Phnom Penh) first and ក្រុង after it, aimed back at ក្រុង shows ក្រុង; នៅផ្ទះ aimed at ផ្ទះ shows ផ្ទះ first, រាល់ថ្ងៃ at ថ្ងៃ shows ថ្ងៃ; ក្មេងៗ aimed at ៗ shows ក្មេង; text with zero-width spaces (សាលា + U+200B + រៀន) finds សាលារៀន, and a zero-width space after a word stays out of its highlight; សួស្តី (subscript TA) finds សួស្ដី, ស្ដ្រី (DA) finds ស្ត្រី; no match ends inside a cluster (ខ្ of ខ្ញុំ); the sentence (translation button) ends at ។ and ៕, not at a space; flag kh, bubble ខ; the page's `lang` is km and its serif face the phone's Noto Serif Khmer (`local()`, weights 400 and 700); Kantumruy Pro and Hanuman are listed for Khmer only and load on the page; Tesseract fast is the recommended model and its text keeps the real spaces without adding any inside words; audio for ទឹក from the voice | N | U KhmerSupportTest, EarlierStartsTest, SentenceTest, LookupStartsTest, TextLayoutTest, CatalogFileTest, FontCatalogFileTest, MlKitOcrEngineTest, LanguageRulesIsolationTest, glue.test.mjs, I LanguageLookupTest, SystemFontFilesTest, M | 2026-10-20 |
| LK48 | Arabic lookups (overlay over `testdata/ocr/ar-lines.png`, rendered by `scripts/TextImage.java` at `SIZE=64` with `FONT` the phone's `NotoNaskhArabic-Regular.ttf` and `FALLBACK` the phone's Roboto, the lines as `@FILE`; cloud recognition, then Tesseract ara fast offline): والكبير finds كَبِير (و and the article undone); والمدارس finds مَدْرَسَة through its broken plural; اللغه and العربيه (ه for a final ة) find لُغَة and عَرَبِيَّة; اشتريت finds اِشْتَرَى; كِتَابًا read with its harakat shows كِتَاب first; هل finds هَلْ, نسبة نِسْبَة, قال قَالَ; ذهبت and الكتب show their homographs as recorded under the owner question (ذَهَب before ذَهَبَ); flag sa, bubble ض; the setup ticks wty-ar-en, the transcriptions, wordfreq and Tesseract fast ara; the page's `lang` is ar and its serif face the phone's Noto Naskh Arabic (`local()` by `NotoNaskhArabic` and `Noto Naskh Arabic`); Noto Sans Arabic and Amiri are listed "Only for Arabic text" and a downloaded one loads on the page as "Screenlate Chosen"; Tesseract's highlight covers only the aimed word | F | U ArabicSupportTest, SentenceTest, TextLayoutTest, TesseractPageTest, CatalogFileTest, FontCatalogFileTest, MlKitOcrEngineTest, LanguageRulesIsolationTest, LookupSettingsRepositoryTest, glue.test.mjs, I LanguageLookupTest, SystemFontFilesTest, M | 2026-10-20 |
| LK49 | Persian lookups (overlay over `testdata/ocr/fa-lines.png` and, for the spoken endings, `testdata/ocr/fa-spoken.png` ("کتابمون را دیدم.", "خودشون گفتن.", "دومین کتاب و سیزدهمین روز", "هوا خوبه. زیتون"), rendered by `scripts/TextImage.java` at `SIZE=64` with `FONT` Vazirmatn and `FALLBACK` the phone's Roboto, the lines as `@FILE`; cloud recognition): کتاب‌ها (with the zero-width non-joiner) finds کتاب through -ها, then کاتب; خواهم after a separate می finds خواستن with "first person singular present indicative", matched "می خواهم"; ترین after بزرگ, and بزرگ itself, find بزرگ through -ترین, matched "بزرگ ترین"; كتابهایشان with the Arabic kaf finds کتاب through -ها and -یشان; ستارگان finds ستاره (-گان); خانهٔ finds خانه ("singular ezafe"); نمی‌دانم shows the phrase first, then دانستن through نمی-; "نگاه کن" finds نگاه کردن; کتابمون finds کتاب through "1st pl.", خودشون finds خود, دومین finds دوم through "ordinal", and خوبه does not find خوب through an ending; flag ir, bubble پ; the setup ticks wty-fa-en, the transcriptions and wordfreq; the page's `lang` is fa; Noto Sans Arabic, Amiri and Vazirmatn are listed "Only for Persian text", and a downloaded Vazirmatn loads on the page as "Screenlate Chosen" | F | U PersianSupportTest, SentenceTest, CatalogFileTest, FontCatalogFileTest, LanguageRulesIsolationTest, glue.test.mjs, I LanguageLookupTest, SystemFontFilesTest, M | 2026-10-20 |
| LK50 | Hebrew lookups (overlay over `testdata/ocr/he-lines.png`, rendered by `scripts/TextImage.java` at `SIZE=64` with `FONT` the phone's `NotoSansHebrew-Regular.ttf` and `FALLBACK` the phone's Roboto, the lines as `@FILE`; cloud recognition): והבית finds הבית ("and"), then בית ("the « and"); כשהגעתי finds הגיע ("when « first person singular … past"); לירושלים finds ירושלים ("to"); הכלבים finds כלב ("plural definite masculine"); ושמהבית finds הבית through three prefixes ("from « that « and") and בית only as the dictionary's form הבית; ג'ירפה finds ג׳ירפה; pointed קוֹרֵא finds קורא and קרא; שלום shows the headword without "singular construct", pointed שְׁלוֹם with it; in "קניתי iPhone 15 ב־3,500 ₪ היום." the scan text from קניתי reads "קניתי iPhone 15 .ב־3,500" and from היום only היום; flag il, bubble א; the setup ticks wty-he-en, the transcriptions, wordfreq and Tesseract heb fast; Heebo and Frank Ruhl Libre are listed "Only for Hebrew text", a downloaded Heebo loads on the page as "Screenlate Chosen", and the phone's Noto Serif Hebrew loads as "Screenlate Serif" | F | U HebrewSupportTest, SentenceTest, CatalogFileTest, FontCatalogFileTest, LanguageRulesIsolationTest, glue.test.mjs, I LanguageLookupTest, SystemFontFilesTest, M | 2026-10-20 |
| LK51 | Kazakh lookups (overlay over `testdata/ocr/kk-lines.png` and `kk-lines2.png`, the lines in `kk-lines.txt` and `kk-lines2.txt`, rendered by `scripts/TextImage.java` at `SIZE=64` with PT Sans as `FONT` and `FALLBACK`; cloud recognition): Казакстанның finds Қазақстан ("genitive"); Қазакстан, which keeps Қ and loses қ, finds Қазақстан before қаз; оқыды finds оқу ("past") and not оқ; суды finds су ("accusative"); кітаптарымызда finds кітап ("plural « our « locative"); жаздық finds жаздық, then жазу; қазанда finds қазан ("locative"); он finds он, оң, өн, өң; 1990-ға finds the suffix entry -ға; on the search screen кiтаптар with a Latin i finds кітап, and "оң жақ" finds оң only | F | U KazakhSupportTest, KeyedMatchesTest, SentenceTest, CatalogFileTest, LanguageRulesIsolationTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-22 |
| LK52 | Parts of a hyphenated word (Kazakh, `testdata/ocr/kk-lines2.png`, сондай-ақ in `kk-lines.png`): ас-су aimed at су finds ас-су, then су; бала-шағамен aimed at шағамен finds бала-шаға only (the part finds nothing, and the first part's бала stays out); сондай-ақ aimed at ақ finds the particle -ақ, then ақ (сондай-ақ is no headword) | F | U EarlierStartsTest, KazakhSupportTest, M | 2026-10-22 |
| LK53 | Kyrgyz lookups (overlay over `testdata/ocr/ky-lines.png`, the lines in `ky-lines.txt`, rendered as for LK51; cloud recognition): 2007-жылы aimed at жылы finds жыл; китепкана finds китепкана; Китептерибизде finds китеп ("plural « our « locative"); барасыңбы finds баруу ("present « 2nd sg. « question"); Кол finds кол, then көл; көл finds көл only; Үйлөрдө finds үй ("locative plural"); Ата-энем aimed at энем finds ата-эне, then эне; иштейт finds иштөө ("present « 3rd person"); алды finds ал and алуу; on the search screen көчодө, which keeps one ө and loses the other, finds көчө | F | U KyrgyzSupportTest, SentenceTest, CatalogFileTest, LanguageRulesIsolationTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-22 |
| LK54 | Arabic text written with the Persian kaf and yeh (ک ی), as Persian keyboards and fonts give it (Arabic active; app text, AnkiDroid's note editor): کتاب and والکتاب find كتاب, علی finds علي or على, کبیر finds كبير, as they do written with ك and ي; with Persian active the same words are looked up as written | F | glue.test.mjs, U ArabicSupportTest, M | 2026-10-22 |
| LK55 | Swedish lookups (overlay over `testdata/ocr/sv-sample.png`): Statsministern finds statsminister; läser finds läsa ("present"); skattepengar, which has no entry, shows "Compound: skatt + pengar" and both entries; sjukhusparkering shows sjukhus + parkering; år finds år and not ar; app text with ả (AnkiDroid's note editor opened by sharing "Han gả hem.") is taken as written: gả finds no gå | F | U SwedishSupportTest, CompoundsTest, LanguageRulesIsolationTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-22 |
| LK56 | Norwegian lookups (overlay over `testdata/ocr/no-sample.png`): dagens finds dag through dagen ("genitive"); Norges finds Norge; ellers finds ellers and not eller; mens finds mens first, then men "harm" and not men "but"; tidsmaskin, which has no entry, shows "Compound: tid + maskin" and both entries; år finds år and not ar; app text with ả (AnkiDroid's note editor opened by sharing "Hun gả hjem.") is taken as written: gả finds no gå | F | U NorwegianSupportTest, CompoundsTest, LanguageRulesIsolationTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-22 |
| LK57 | Danish lookups (overlay over `testdata/ocr/da-sample.png`): Huset finds hus; dagens finds dag (a form row); læser finds læse and læser; statsministerkandidat, which has no entry, shows "Compound: statsminister + kandidat" and both entries in the Search screen (the overlay reads 16 letters by default and splits only those); år finds år and not ar; app text with ả (AnkiDroid's note editor opened by sharing "Hun gả hjem.") is taken as written: gả finds no gå | F | U DanishSupportTest, CompoundsTest, LanguageRulesIsolationTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-22 |
| LK58 | Romanian lookups (overlay over `testdata/ocr/ro-sample.png`): Consiliul finds consiliu with the rule "definite"; numărul finds număr (a form row); casă shows casă and not cas; aflată finds afla through aflat ("feminine or plural", then "past participle"); aimed at the a of s-a, the lookup starts at a; aimed at într of într-o, the popup shows într-o or într; dupa and si (typed in the Search screen) find după and și, the exact spelling si first; şi finds și; no result under a Cyrillic headword (o, este) | F | U RomanianSupportTest, RuleOnlyResultsTest, ShownResultsTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-22 |
| LK59 | Latin lookups (overlay over `testdata/ocr/la-sample.png`, and the Search screen): amat finds amo (amō) through its form row; Rōmae and Romae find Roma; uita finds vita; magisque finds magis with the rule "-que" and its description; atque shows atque, not at; voluit shows volo first; amicis shows amiciō before amīcus (Latin has no frequency dictionary) | F | U LatinSupportTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-22 |
| LK60 | Lithuanian lookups (overlay over `testdata/ocr/lt-sample.png`, and the Search screen): žmogaus finds žmogus through its form row žmogaũs; zmogaus and ZMOGUS find žmogus; bites finds bitė; ruda shows ruda first, then rūda; kasti shows kasti, then kąsti; as finds aš; vandens finds vanduo | F | U LithuanianSupportTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-22 |
| LK61 | Kazakh particles after a hyphen and endings checked on data (overlay over `testdata/ocr/kk-lines3.png`, the lines in `kk-lines3.txt`, rendered as for LK51; cloud recognition): сондай-ақ aimed at ақ finds -ақ, then ақ; келді-ау aimed at ау finds -ау first; пікірінше finds пікір ("his, her, their « manner"); болуы finds болу ("his, her, their"); тауып finds табу ("converb") | F | U KazakhSupportTest, EarlierStartsTest, glue.test.mjs, M | 2026-10-22 |
| LK62 | Kyrgyz keyed results after the written ones and endings checked on data (overlay over `testdata/ocr/ky-lines2.png`, the lines in `ky-lines2.txt`; cloud recognition): Түштук finds түшүү ("past « 1st pl."), then түштүк; 19-декабрь aimed at декабрь finds декабрь and no suffix entry -де; өзүнчө finds өз ("his, her, their « manner") | F | U KeyedMatchesTest, EarlierStartsTest, KyrgyzSupportTest, glue.test.mjs, M | 2026-10-22 |
| LK63 | One-letter words do not show an Arabic letter (Wiktionary links each Cyrillic letter to its Arabic equivalent, tag `Cyrillic`): on the search screen Kyrgyz а and в, and Kazakh о, find no entry of ا, ۋ or و | F | U KazakhSupportTest, KyrgyzSupportTest, ShownResultsTest, M | 2026-10-22 |
| LK64 | Slovak lookups (overlay over `testdata/ocr/sk-sample.png`, and the Search screen): robit and ROBIT find robiť; byt shows byt first, then byť; kon finds kôň; dakujem finds ďakujem; robil finds robiť through its form row | F | U SlovakSupportTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-22 |
| LK65 | Czech as Slovak's fallback (Slovak active with wty sk-en, wty cs-en installed): v and však, which sk-en lacks, show cs-en's entries with "Czech" after the word; voda shows sk-en's entry only, without cs-en's definitions; tu shows Slovak tú (question 26); with Czech active the same words show no tag; without a Czech dictionary v finds nothing | F | U FallbackDictionariesTest, P popup.test.mjs, M | 2026-10-22 |
| LK66 | Kazakh and Kyrgyz noun endings skip the pronouns whose case forms change their stem: on the search screen Kyrgyz алды shows алуу ("past") and no ал with the accusative; бизге still finds биз ("dative") | F | U glue `kazakh and kyrgyz noun endings skip the pronouns…`, M | 2026-10-22 |
| LK67 | The Kyrgyz verbal noun in -ыш: болушу finds болуу ("verbal noun « his, her, their"); иш finds only иш, no verb | F | U glue `kyrgyz verbal nouns in -ыш…`, M | 2026-10-22 |
| LK70 | Slovenian lookups (overlay over `testdata/ocr/sl-sample.png`, and the Search screen): vode finds voda through its form row vôde; delal finds delati through dẹ́lał; je finds biti through its form row's lemma bíti; zena and ZENA find žena; se shows se first, then še | F | U SlovenianSupportTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-22 |
| LK71 | Long compounds: with the scan length never changed, the Lookup settings show 24 for Swedish, Danish and Norwegian and 16 for German; aimed at the start of statsministerkandidat in the overlay (an image of "Statsministerkandidaten talade länge.", `scripts/text-image.py`, Segoe UI), the compound line shows statsminister + kandidat | N | U SwedishSupportTest, DanishSupportTest, NorwegianSupportTest, Isolation, M | 2026-10-28 |
| LK72 | Esperanto lookups (overlay over `testdata/ocr/eo-sample.png`, and the Search screen): cxu, CXu and cu find ĉu; mangxajxojn and mangajojn find manĝaĵo; pomojn finds pomo (accusative, plural); malbona shows malbona, then bona; si shows si first, then ŝi; dank’ al and dank' al find dank' al | N | U EsperantoSupportTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-28 |
| LK73 | Albanian lookups (overlay over `testdata/ocr/sq-sample.png`, and the Search screen): rëndësishme finds rëndësishëm through "feminine or plural"; ndodhet finds ndodh; sulmit finds sulm; lejen shows leje (a noun) before any verb; shqiptare, a headword of its own, shows its own entries first, with no ending chip; gjate finds gjatë through "e for ë"; aimed at ka in s'ka, at është in ç'është and at i in t'i (with ' and ’) the lookup starts after the apostrophe | F | U AlbanianSupportTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-28 |
| LK74 | Swahili lookups (overlay over `testdata/ocr/sw-sample.png`, and the Search screen): alisoma finds soma through "past « he/she"; hatukusoma finds soma ("past « we « negative"); aliyemwona finds ona ("object « relative « past « he/she"); kinachoitwa finds itwa; alikula shows kula first; aliunga shows the verb unga only; kupa shows only kupa; Angela finds nothing; ng'ombe and Ng’ombe find ng'ombe | F | U SwahiliSupportTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-28 |
| LK75 | Welsh lookups (overlay over `testdata/ocr/cy-sample.png`, app text in AnkiDroid's note editor, and the Search screen): nghath finds cath and Nghymru Cymru ("nasal mutation"), the highlight over the whole word; fara finds bara ("soft mutation"), hysgol ysgol ("h-prothesis"), chath cath ("aspirate mutation"); mae shows bod's form, and with cy-fr alone no bae (bay); ty finds tŷ in app text and the Search screen, twr only twr; tý in app text and the Search screen finds no tŷ; i’w finds both dictionaries' i'w and i’w | F | U WelshSupportTest, glue.test.mjs, RuleOnlyResultsTest, I LanguageLookupTest, M | 2026-10-28 |
| LK76 | Irish lookups (overlay over `testdata/ocr/ga-sample.png`, app text in AnkiDroid's note editor, and the Search screen): nGaillimh finds Gaillimh ("eclipsis"), the highlight over the whole word; bhean finds bean ("lenition"), tSín Sín ("lenition"), hÉireann Éire through Éireann ("h-prothesis"), t-uisce uisce ("t-prothesis"); in d'fhág a tap on fhág finds fág, a tap on d' the whole word; chomh shows only chomh (no comh), thar shows tar only through ga-en's own row (lenited form); ḃean typed finds bean; fag finds nothing | F | U IrishSupportTest, glue.test.mjs, mutation-exceptions.test.mjs, I LanguageLookupTest, M | 2026-10-28 |
| LK77 | Scottish Gaelic lookups (overlay over `testdata/ocr/gd-sample.png`, app text in AnkiDroid's note editor, and the Search screen): h-Alba finds Alba ("h-prothesis"), the highlight over the whole word; bhean finds bean ("lenition"), t-sràid sràid ("lenition"), t-uisge uisge ("t-prothesis"), n-eilean eilean ("eclipsis"); in Dh'fhàg a tap on fhàg finds fàg, a tap on Dh' the whole word; th'ann finds ann; mhór finds mòr; chan shows can only through gd-en's own row (cha chan), Bhathar no bathar; bith typed finds bìth, oran òran, bata only bata; hAlba without its hyphen finds nothing | F | U GaelicSupportTest, glue.test.mjs, mutation-exceptions.test.mjs, I LanguageLookupTest, M | 2026-10-28 |
| LK78 | Finnish lookups (overlay over `testdata/ocr/fi-sample.png` and the Search screen): Talossanikin finds talo with the chips possessive and clitic, the highlight over the whole word; Onkohan finds olla (clitic); a tap on the n of EU:n or on lle of STT:lle finds EU or STT, the highlight from the word's start; 1990-luvulla aimed at luvulla finds luku (the form row), 20-vuotias aimed at vuotias finds -vuotias (an entry of Wiktionary (fi–en) only); kirjastonhoitaja shows "Compound: kirjasto + hoitaja" or its own entry; Kuusi shows kuusi first and kuu only through the Russian dictionary's own row ("redirected from kuusi"), never through the possessive -si; vesi only vesi; talokin finds talo (clitic) | F | U FinnishSupportTest, CompoundsTest, glue.test.mjs, I LanguageLookupTest, M | 2026-10-28 |
| LK79 | Hungarian lookups (overlay over `testdata/ocr/hu-sample.png` and the Search screen): Megtartottam finds tart with the chips verb prefix and the form row's tags, the highlight over the whole word; megtart and bevétel find their own entries, not tart or vesz; idegen finds idegen, never ide- with gén; a tap on ban of 2023-ban or EU-ban finds -ban (a suffix entry), the highlight from the hyphen; iskolaépület shows "Compound: iskola + épület" or its own entry; in Search, füzfa finds nothing and öt only öt, while the same words recognized (füzfa or fúzfa) find fűzfa, and öt then őt; hűvös read as húvös finds hűvös | F | I LanguageLookupTest, glue, M | 2026-10-28 |
| LK80 | Estonian lookups (overlay over `testdata/ocr/et-sample.png` and the Search screen): pole finds pole, then olema with the chip negative; polnud polnud, then olema; majas finds maja (singular inessive); a tap on ga of NATO-ga or 5-ga finds -ga (a suffix entry), the highlight from the hyphen; linnavalitsus shows "Compound: linna + valitsus" or its own entry; in Search, möte finds nothing and öö only öö, while möte recognized finds mõte | F | I LanguageLookupTest, glue, M | 2026-10-28 |
| LK81 | Azerbaijani lookups (overlay over `testdata/ocr/az-sample.png` and `az-misread.png`, and the Search screen): kitablarımızda finds kitab (the form row's tags where the dictionary lists the form, else the chips plural, our, locative); gəlirəm finds gəlmək (present, 1st sg.); çörəyi finds çörək; belə shows belə and not bel, while a form row stays (yazı shows yazı, then yaz through the accusative row); məktəbdəydi finds məktəb (locative, past copula); olur finds olmaq through the dictionary's row; a tap on ci of 2020-ci or da of ABŞ-da finds the suffix entry where the dictionary has one, the highlight from the hyphen; recognized Seher and Qizlar find səhər and qız; in Search, seher finds nothing | F | I LanguageLookupTest, glue, M | 2026-10-28 |

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
| POP17 | Chinese fonts: five to download with descriptions in the interface language (zh-TW gets the Traditional text); Noto Sans TC downloads, is chosen and loads on the page; a font downloaded for Japanese (M PLUS 1p) is not on the Chinese list and the other way round | O | U FontCatalogFileTest, FontsForLanguageTest, M | 2026-10-15 |
| POP18 | Deleting a font asks "Delete <font>?": Cancel keeps it, Delete removes it and languages that used it go back to the phone's font | O | M | 2026-10-15 |
| POP19 | Frequency chip names: up to four letters whole (a CJK character counts as two), longer as the first letter in upper case ("wordfreq en" → W); a tap shows the whole name, another tap shortens it; "+N" shows the first chip's name whole; the next lookup starts shortened | F | P, M | 2026-10-15 |
| POP20 | Character entry tags: 韓 in Unihan Hanja shows 교육용 next to the dictionary name, a tap opens "On the list of basic hanja for education (2007)"; KANJIDIC's jouyou and jinmeiyou show the same way | F | P, M | 2026-10-15 |
| POP21 | Korean fonts: four to download (Noto Sans KR, Noto Serif KR, Nanum Gothic, Nanum Myeongjo); Nanum Gothic downloads, is chosen and loads on the page in its two weights; with the phone's font the page loads `NotoSansCJKkr-Regular` and `NotoSerifCJKkr-Regular`, and 次 takes its Korean form | O | U FontCatalogFileTest, M | 2026-10-15 |
| POP22 | Quote entities left in a dictionary's plain text (`&quot;`, `&apos;`; 496 KRDICT ko-en entries) show as quote marks on the page and in Anki fields; other entities stay as written | O | P, M | 2026-10-15 |
| POP23 | Latin fonts: six to download (Literata, Inter, Atkinson Hyperlegible Next, Lexend, PT Serif, PT Sans) on the list of every Latin-script language; Lexend downloaded for Portuguese shows on the French list too, not on the Japanese one, and the popup loads it | F | U FontCatalogFileTest, FontsForLanguageTest, M | 2026-10-16 |
| POP24 | Frequency chip initial by language: a Turkish profile with a frequency dictionary named in lowercase starting with "i" (a test archive whose `index.json` title is "ingilizce sıklık") shows İ on its chip (the folded chip shows the first frequency dictionary's initial); an English profile shows I | O | P, M | 2026-10-19 |
| POP25 | A popup page whose renderer was lost while the popup was hidden (`am crash` on the app's sandboxed renderer: "renderer gone (crashed: true, shown: false)") comes back with the next scan, after the capture: "Captured" as fast as without the crash, the renderer process starts after it, the popup then shows the word | O | M | 2026-10-19 |
| POP26 | A right-to-left language's popup (Arabic): the recognized text and the headwords run right to left (`dir="rtl"`), with numbers and Latin words in their own order (iPhone 15, (٢٠٢٤)); each glossary block takes the direction of its first letter (`dir="auto"`): English meanings stay left to right, an Arabic example among them runs right to left; the dictionary name's Latin letters do not turn its block; buttons and numbering keep their places; a reading that only adds harakat takes the word's place (كِتَاب, nothing above it); transcriptions keep the dictionary's order (/ki.taːb/ before Egyptian [keˈtæːb]) | F | P popup.test, reading.test, M | 2026-10-20 |
| POP27 | A Persian entry: the Latin reading follows the word in the headword line (کتاب kitāb), grey, at the text size, left to right, and nothing stands over the word; a copied selection leaves the reading out; the rule chain shows the rules' names in the app's language (کتابهایشان: 🧩 pl. « 3rd pl.), and a tap on one opens its description with Persian examples; the Iranian transcription comes first (کتاب [kʰʲe.t̪ʰɒ́ːb̥] with IR), the classical /ki.ˈtaːb/, Dari, Hazaragi and Tajik ones behind "+N" in the dictionary's order | F | P popup.test, reading.test, M | 2026-10-20 |
| POP28 | A Hebrew entry: the headword and the recognized text run right to left; the rule chain shows the prefixes' names in the app's language (בית from והבית: 🧩 the « and; in Russian артикль « и), and a tap on one opens its description with Hebrew examples ("and (ואני, והבית)", "определённый артикль (הבית)"); the Modern Israeli transcription comes first (כלב /ˈke.lev/), and one written `//a/ [b]/` in the dictionary shows as two | F | P popup.test, U TranscriptionsTest, M | 2026-10-20 |
| POP29 | A Kazakh entry: the rule chain names the endings in the app's language (кітап from кітаптарымызда: plural « our « locative); the Popup settings' font card lists Literata, Inter, PT Serif and PT Sans with "Only for Kazakh text"; PT Serif, downloaded and chosen, sets the Kazakh text, and the Latin glosses as well, since a Cyrillic language's range includes Latin, as for Russian | F | U FontCatalogFileTest, M | 2026-10-20 |
| POP30 | A Kyrgyz entry: the rule chain names the endings in the app's language (китеп from китептерибизде: plural « our « locative) and the transcriptions show (/kitep/ [kiˈtep]); the Popup settings' font card lists Literata, Inter, PT Serif and PT Sans with "Only for Kyrgyz text"; PT Serif, chosen, sets the Kyrgyz text with ң ө ү Ң Ө Ү and the Latin glosses | F | U FontCatalogFileTest, M | 2026-10-20 |
| POP31 | A Swedish entry: the Popup settings' font card lists Literata, Inter, Atkinson Hyperlegible Next, Lexend, PT Serif and PT Sans with "Only for Swedish text"; one chosen sets å ä ö | F | U FontCatalogFileTest, M | 2026-10-22 |
| POP32 | A Norwegian entry: the rule chain names the genitive in the app's language (dagen from dagens: genitive; in Russian родительный падеж), and a tap on it opens "genitive (dagens, Norges)"; the Popup settings' font card lists the Latin fonts with "Only for Norwegian text"; one chosen sets æ ø å | F | U FontCatalogFileTest, M | 2026-10-22 |
| POP33 | A Danish entry: the Popup settings' font card lists the Latin fonts with "Only for Danish text"; one chosen sets æ ø å | F | U FontCatalogFileTest, M | 2026-10-22 |
| POP34 | A Romanian entry: the Popup settings' font card lists the Latin fonts with "Only for Romanian text"; one chosen sets ă â î ș ț; the rule chip of consiliul reads "definite" in English and its translation in Russian | F | U FontCatalogFileTest, M | 2026-10-22 |
| POP35 | A Latin entry: the Popup settings' font card lists Literata, Inter, Lexend, PT Serif and PT Sans, not Atkinson Hyperlegible Next, with "Only for text in Latin"; one chosen sets ā ē ī ō ū; the bubble shows LA and the Languages screen Latin's laurel flag; the rule chip of magisque reads "-que" with its description in English and Russian | F | U FontCatalogFileTest, FlagsTest, M | 2026-10-22 |
| POP36 | A Lithuanian entry: the Popup settings' font card lists Literata, Inter, Atkinson Hyperlegible Next, Lexend, PT Serif and PT Sans with "Only for Lithuanian text"; one chosen sets ą č ę ė į š ų ū ž; the bubble shows Ė and the Languages screen Lithuania's flag | F | U FontCatalogFileTest, FlagsTest, M | 2026-10-22 |
| POP37 | A Slovak entry: the Popup settings' font card lists Literata, Inter, Atkinson Hyperlegible Next, Lexend, PT Serif and PT Sans with "Only for Slovak text"; one chosen sets á ä č ď é í ĺ ľ ň ó ô ŕ š ť ú ý ž; the bubble shows Ô and the Languages screen Slovakia's flag | F | U FontCatalogFileTest, FlagsTest, M | 2026-10-22 |
| POP38 | A Slovenian entry: the Popup settings' font card lists Literata, Inter, Atkinson Hyperlegible Next, Lexend, PT Serif and PT Sans with "Only for Slovenian text"; one chosen sets č š ž; the bubble shows SL and the Languages screen Slovenia's flag | F | U FontCatalogFileTest, FlagsTest, M | 2026-10-22 |
| POP40 | An Esperanto entry: the Popup settings' font card lists Literata, Inter, Lexend, PT Serif and PT Sans with "Only for text in Esperanto", and not Atkinson Hyperlegible Next; one chosen sets ĉ ĝ ĥ ĵ ŝ ŭ; the bubble shows Ĉ and the Languages screen the Esperanto flag (a green star) | N | U FontCatalogFileTest, FlagsTest, M | 2026-10-28 |
| POP39 | Aim at a word, then off text so the popup closes, then at another word → the popup opens with the new word only, the previous one never shows; from one word straight to the next, the popup moves to the new word together with its content | N | M | 2026-10-28 (API 37, frame by frame; `main` by screenshots only) |
| POP41 | An Albanian entry: the Popup settings' font card lists Literata, Inter, Atkinson Hyperlegible Next, Lexend, PT Serif and PT Sans with "Only for Albanian text"; one chosen sets ë ç; the bubble shows Ë and the Languages screen Albania's flag; the rule chip of an ending of our own names it in the interface language (Russian too) | F | U FontCatalogFileTest, FlagsTest, M | 2026-10-28 |
| POP42 | A Swahili entry: the Popup settings' font card lists Literata, Inter, Atkinson Hyperlegible Next, Lexend, PT Serif and PT Sans with "Only for Swahili text" and a Swahili sample sentence; the bubble shows SW and the Languages screen Tanzania's flag; the chips of the prefix rules name them in the interface language (Russian too) | F | U FontCatalogFileTest, FlagsTest, M | 2026-10-28 |
| POP43 | A Welsh entry: the Popup settings' font card lists Literata, Inter, Atkinson Hyperlegible Next and Lexend, not PT Serif or PT Sans, with "Only for Welsh text" and a Welsh sample sentence; one chosen sets ŵ ŷ; the bubble shows Ŵ and the Languages screen Wales's flag; the mutation chips name the mutation in the interface language (Russian too) | F | U FontCatalogFileTest, FlagsTest, M | 2026-10-28 |
| POP44 | An Irish entry: the Popup settings' font card lists every Latin font with "Only for Irish text" and the Irish sample sentence; the bubble shows Á and the Languages screen Ireland's flag; the mutation chips name the mutation in the interface language (Russian too) | F | U FontCatalogFileTest, FlagsTest, M | 2026-10-28 |
| POP45 | A Scottish Gaelic entry: the Popup settings' font card lists every Latin font with "Only for Scottish Gaelic text" and the Gaelic sample sentence; the bubble shows Ò and the Languages screen Scotland's flag; the Russian interface names the language «Шотландский гэльский»; the mutation chips name the mutation in the interface language (Russian too) | F | U FontCatalogFileTest, FlagsTest, GaelicSupportTest, M | 2026-10-28 |
| POP46 | A Finnish entry: the Popup settings' font card lists every Latin font with "Only for Finnish text" and the Finnish sample sentence; the bubble shows Ä and the Languages screen Finland's flag; the clitic and possessive chips and the cases of the form rows are named in the interface language (Russian too: «единственное число инессив», «агентивное причастие») | F | U FontCatalogFileTest, FlagsTest, FinnishSupportTest, M | 2026-10-28 |
| POP47 | A Hungarian entry: the Popup settings' font card lists every Latin font with "Only for Hungarian text" and the Hungarian sample sentence; the bubble shows Ő and the Languages screen Hungary's flag; the verb prefix chip and the cases and conjugations of the form rows are named in the interface language (Russian too: «определённое спряжение», «терминатив») | F | U FontCatalogFileTest, FlagsTest, HungarianSupportTest, FormOfTagsTest, M | 2026-10-28 |
| POP48 | An Estonian entry: the Popup settings' font card lists every Latin font with "Only for Estonian text" and the Estonian sample sentence; the bubble shows Õ and the Languages screen Estonia's flag; the negative chip and the moods and infinitives of the form rows are named in the interface language (Russian too: «отрицание», «косвенное наклонение», «на -da») | F | U FontCatalogFileTest, FlagsTest, EstonianSupportTest, FormOfTagsTest, M | 2026-10-28 |
| POP49 | An Azerbaijani entry: the Popup settings' font card lists Literata, Inter and Lexend with "Only for Azerbaijani text" and the Azerbaijani sample sentence; the bubble shows Ə and the Languages screen Azerbaijan's flag; the ending chips and the tenses of the form rows are named in the interface language (Russian too: «связка прошедшего времени», «эвиденциальное прошедшее») | F | U FontCatalogFileTest, FlagsTest, AzerbaijaniSupportTest, FormOfTagsTest, M | 2026-10-28 |

### AUD: audio

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| AUD1 | 🔊 plays the clip of the highest source that has one; holding it lists all recordings | F | U AudioFinderTest, M | 2026-10-26 |
| AUD2 | A failing source → its Test shows the error; home lists it in the audio card | O | U AudioErrorTest, M | 2026-10-22 |
| AUD3 | English Wiktionary clips follow the Regions order; a changed order applies to the next selection | O | U AudioFinderTest, M | |
| AUD4 | A custom URL source on `http://10.0.2.2:<port>` asks for the local network permission (API 37) and plays | O | U LocalNetworkTest, M | |
| AUD5 | Home's audio card "Audio settings" opens the Anki page at the audio section; "Add audio source" starts as the language's first default kind (English: Lingua Libre) | O | M | 2026-10-14 |
| AUD6 | Chinese: 🔊 on 朋友 plays Wiktionary's `Zh-péngyou.ogg` (found by pinyin), holding it lists Wiktionary, Lingua Libre, then text-to-speech; text-to-speech reads the characters | F | U AudioFinderTest, ChineseSupportTest, M | 2026-10-15 |
| AUD7 | Korean: 🔊 on 가르치다 plays a clip; a hanja headword from KRDICT (大) has no reading and finds no clip, as in Yomitan, while its Hangul entry plays | F | M | 2026-10-15 |
| AUD8 | Spanish regions: Latin America, Spain, Other regions; holding 🔊 on agua lists Lingua Libre, then Wiktionary's `Es-am-lat-agua.ogg` before `Es-agua.oga`, then text-to-speech | F | U AudioPagesTest, M | 2026-10-16 |
| AUD9 | French regions: France (titles `Fr-fr-paris-…` and `Fr-fr-…`), Canada, Other regions; Quebec and Paris transcription tags take the region labels | F | U FrenchSupportTest, M | 2026-10-18 |
| AUD10 | German regions: Germany, Other regions, Austria (`De-at-…`), Switzerland; Chemie shows the standard [çeˈmiː] as DE first, then AT and CH; regional variants inside Germany carry no region | F | U GermanSupportTest, M | 2026-10-16 |
| AUD11 | Portuguese regions: Brazil, Portugal, Other regions (titles `Pt-br-…`, `Pt-pt-…`); regional accents (🇧🇷RdJ, Lisbon) count as their country | F | U PortugueseSupportTest, M | 2026-10-16 |
| AUD12 | The Regions list in the audio settings shows while Wiktionary is among the language's sources or a transcription dictionary of the language is installed, and hides otherwise (English: Wiktionary removed and no wty-en-ipa → hidden; with wty-en-ipa → shown); its hint names recordings and transcriptions | F | U RegionsUsedTest, M | 2026-10-19 |
| AUD13 | Arabic audio: holding 🔊 on كِتَاب lists Wiktionary's Ar-كِتَابٌ.oga (found through its harakat), Lingua Libre's recordings (Modern Standard Arabic speakers only) and the text to speech; a tap plays a recording of the word as written (Lingua Libre's كتاب) before Wiktionary's vowelled one; Google's Arabic voice speaks when chosen | F | U AudioPagesTest, AudioFinderTest, ArabicSupportTest, M | 2026-10-20 |
| AUD14 | Persian audio: holding 🔊 on کتاب lists Lingua Libre's recordings of the word as written, Wiktionary's Fa-كتاب.ogg (found through the Arabic letters), then a Lingua Libre recording titled otherwise, and the text to speech; a recording and Wiktionary's file both play; without a Persian voice (the emulator's Google engine has none) the voice item plays nothing and logs "No text-to-speech voice for fa" | F | U PersianSupportTest, AudioPagesTest, M | 2026-10-20 |
| AUD15 | Hebrew audio: holding 🔊 on כלב lists Lingua Libre's recording of the word as written, Wiktionary's pointed He-כֶּלֶב.ogg and the text to speech; Wiktionary's file plays, and Google's Hebrew voice (he-il) speaks when chosen | F | U HebrewSupportTest, AudioPagesTest, M | 2026-10-20 |
| AUD16 | Kazakh audio: holding 🔊 on кітап lists Lingua Libre's recording and the text to speech; the recording plays; with no Kazakh voice on the device the text to speech item says so | F | U KazakhSupportTest, AudioPagesTest, M | 2026-10-20 |
| AUD17 | Kyrgyz audio: holding 🔊 on китеп lists Lingua Libre's recording, Wiktionary's and the text to speech; the Lingua Libre recording plays | F | U KyrgyzSupportTest, M | 2026-10-20 |
| AUD18 | Several languages turned on share failing sources (Lingua Libre and Wiktionary through a proxy that never answers): a Kyrgyz lookup's 🔊 adds a "Kyrgyz" audio card only, a Hebrew lookup's adds a "Hebrew" card with its own failures, and no other language gets one; "Audio settings" on a card opens that language's audio sources; once the sources answer, a lookup in one language removes only that language's card | O | U CurrentFailuresTest, AudioFinderTest, M | 2026-10-22 |
| AUD19 | Swedish audio: holding 🔊 on vatten lists Wiktionary's Sv-vatten.ogg first, then Lingua Libre and the text to speech; the recording plays, and Google's Swedish voice speaks when chosen | F | U SwedishSupportTest, M | 2026-10-22 |
| AUD20 | Norwegian audio: holding 🔊 on vann lists Lingua Libre's recording first, then Wiktionary's and the text to speech; the recording plays, and Google's Norwegian voice (nb) speaks when chosen | F | U NorwegianSupportTest, M | 2026-10-22 |
| AUD21 | Danish audio: holding 🔊 on hund lists Lingua Libre's recording first, then the text to speech (Wiktionary has none for hund; vand has Da-vand.ogg); the recording plays, and Google's Danish voice speaks when chosen | F | U DanishSupportTest, M | 2026-10-22 |
| AUD22 | Romanian audio: holding 🔊 on apă lists Lingua Libre's recording first, then Wiktionary's where it has one and the text to speech; the recording plays, and Google's Romanian voice speaks when chosen | F | U RomanianSupportTest, M | 2026-10-22 |
| AUD23 | Latin audio: Audio settings show Regions with "Classical pronunciation" and "Ecclesiastical pronunciation" while Wiktionary is a source; holding 🔊 on magnus lists Wiktionary's La-cls- recording first; moving the ecclesiastical one up puts La-ecc- recordings first; the phone's voice speaks Latin where it has one | F | U LatinSupportTest, AudioPagesTest, M | 2026-10-22 |
| AUD24 | Lithuanian audio: Audio settings list Lingua Libre first, Wiktionary, then the phone's voice; holding 🔊 on vanduo lists Lingua Libre's recording; the phone's voice speaks Lithuanian where it has one | F | U LithuanianSupportTest, M | 2026-10-22 |
| AUD25 | Slovak audio: Audio settings list Lingua Libre first, Wiktionary, then the phone's voice; holding 🔊 on mäso lists Lingua Libre's recording (Lingua Libre has no voda); the phone's voice speaks Slovak where it has one | F | U SlovakSupportTest, M | 2026-10-22 |
| AUD26 | Slovenian audio: Audio settings list Lingua Libre first, Wiktionary, then the phone's voice; holding 🔊 on voda lists Lingua Libre's recording; the phone's voice speaks Slovenian where it has one | F | U SlovenianSupportTest, M | 2026-10-22 |
| AUD27 | Esperanto audio: Audio settings list Lingua Libre first, Wiktionary, then the phone's voice; holding 🔊 on akvo lists Lingua Libre's recordings first, then Wiktionary's | N | U EsperantoSupportTest, M | 2026-10-28 |
| AUD28 | Albanian audio: Audio settings list Lingua Libre first, Wiktionary, then the phone's voice; holding 🔊 on a word Lingua Libre has lists its recording; the phone's voice speaks Albanian where it has one | F | U AlbanianSupportTest, M | 2026-10-28 |
| AUD29 | Swahili audio: Audio settings list Wiktionary first, Lingua Libre, then the phone's voice; holding 🔊 on a word Wiktionary has lists its recording; the phone's voice speaks Swahili where it has one | F | U SwahiliSupportTest, M | 2026-10-28 |
| AUD30 | Welsh audio: Audio settings list Wiktionary first, Lingua Libre, then the phone's voice; holding 🔊 on dŵr lists Wiktionary's recording titled without the circumflex; the phone's voice speaks Welsh where it has one | F | U WelshSupportTest, M | 2026-10-28 |
| AUD31 | Irish audio: Audio settings list Lingua Libre first, Wiktionary, then the phone's voice; the Test of uisce finds Lingua Libre's recording | F | U IrishSupportTest, M | 2026-10-28 |
| AUD32 | Scottish Gaelic audio: Audio settings list Lingua Libre first, Wiktionary, then the phone's voice; the Test of uisge finds Lingua Libre's recording; a word with no recording says there is no audio | F | U GaelicSupportTest, M | 2026-10-28 |
| AUD33 | Finnish audio: Audio settings list Wiktionary first, Lingua Libre, then the phone's voice; the Test of vesi finds Wiktionary's recording | F | U FinnishSupportTest, M | 2026-10-28 |
| AUD34 | Hungarian audio: Audio settings list Wiktionary first, Lingua Libre, then the phone's voice; the Test of víz finds Wiktionary's recording | F | U HungarianSupportTest, M | 2026-10-28 |
| AUD35 | Estonian audio: Audio settings list Lingua Libre first, Wiktionary, then the phone's voice; the Test of vesi finds a recording | F | U EstonianSupportTest, M | 2026-10-28 |
| AUD36 | Azerbaijani audio: Audio settings list Lingua Libre first, Wiktionary, then the phone's voice; the Test of su finds a recording | F | U AzerbaijaniSupportTest, M | 2026-10-28 |

### ANK: Anki

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| ANK1 | AnkiDroid access, deck and note type; templates pre-filled for known note types and by field names | F | I AnkiDroidRoundTripTest, U FieldTemplateTest, M | 2026-10-14 |
| ANK2 | ➕ adds a note with the sentence and audio, holding ➕ adds it with a picture from the crop editor; a duplicate shows 📖, which opens it in AnkiDroid's browser; holding 📖 offers "Add anyway" and "Add anyway, with a picture" | E | M | 2026-10-27 |
| ANK3 | Holding ➕ opens the crop editor around the paragraph; "Whole screen" ↔ "Frame"; two notes in a row | F | I CropEditorTest, ScreenNoteImageTest, U CropFocusTest, M | 2026-10-14 |
| ANK4 | While the scan refines its text ➕ is grey and a tap says to wait | O | M | |
| ANK5 | AnkiDroid permission revoked or deck deleted → a home card and a grey ➕ with the reason | O | U AnkiStatusCacheTest, M | |
| ANK6 | Anki settings are per language (English deck apart from Japanese) | F | I AnkiProfileEditorsTest, M | 2026-10-14 |
| ANK7 | `{sentence-translation}` filled from the popup's translation or fetched within 5 s | O | M | |
| ANK8 | Docking during a note keeps the note; rotation or docking closes an open crop editor as cancelled | O | M | |
| ANK9 | Chinese note type with Simplified, Traditional, Pinyin, English, Sound: pre-filled with `{expression-simplified}`, `{expression-traditional}`, `{reading}`, `{glossary}`, `{audio}`; the + list has both script markers; a note for 头发 gets 頭髮 (not 頭發) in Traditional; `{furigana}` puts tóu over 头 and fa over 发 | F | U FieldTemplateTest, ShownResultsTest, P, M | 2026-10-15 |
| ANK11 | Check connection: the AnkiDroid card lists installed, access, deck and note type by name, fields with the missing and new ones; a failure gives its fix and the items after it are not checked; checked again after a setup change and on return to the page; in Russian | O | U AnkiNotesTest, M | 2026-10-15 |
| ANK10 | Korean note in the emulator's local collection: Basic with `{expression}` and `{glossary}`, 마시다 added to the test deck | O | M | 2026-10-15 |
| ANK12 | `{sentence}` edge cases (`scripts/text-image.py`, one paragraph each): Japanese "円周率は３．１４です。次の文。" aimed at 円周率 keeps ３．１４ in the sentence; Turkish "ABC LTD. ŞTİ. dün geldi. Sonra gitti." aimed at "geldi" keeps "ABC LTD. ŞTİ." in the sentence; German "Das ist gut. Er sagte “ja” und ging. Dann kam sie." aimed at "ging" gives "Er sagte “ja” und ging.", not the paragraph; German "Wir haben Plan B. Dann gehen wir." aimed at "gehen" gives "Dann gehen wir.", and "Er sagte “Komm.” Dann ging er." aimed at "ging" gives "Dann ging er." with no leading ”; Polish "Jest ok. Idziemy." aimed at "Idziemy" gives "Idziemy." | F | U SentenceTest, JapaneseSupportTest, TurkishSupportTest, GermanSupportTest, PolishSupportTest, M | 2026-10-19 |
| ANK13 | `{sentence-furigana}` in a language looked up by words (a Russian note type with the field): a word the dictionaries do not know (a name or a typo) gets no reading over a word inside it; known words get theirs; Japanese sentences unchanged | F | U SentenceParserTest, M | 2026-10-19 |
| ANK14 | Furigana markers by language: the + menu of a field lists `{furigana}`, `{furigana-plain}`, `{sentence-furigana}`, `{sentence-furigana-plain}` for Japanese (no second line), Chinese (pinyin hints) and Russian (stress hints), and not for English or Korean; the hint text wraps in the menu. A Russian note: `{furigana}` gives ко́шка with no ruby, `{sentence-furigana}` of "Кошка спит, кошки тоже" stresses Ко́шка and то́же but not кошки. An English profile whose field already has `{furigana}` still fills it | F | U FieldTemplateTest, FuriganaMarkerHintsTest, P, M | 2026-10-19, 2026-10-22 (Chinese) |
| ANK15 | `{sentence}` with Roman ordinals (`scripts/text-image.py`, one paragraph each): Czech "Karel IV. byl král. Potom odešel." aimed at "byl" gives "Karel IV. byl král."; "Vládl Karel IV. Potom odešel." aimed at "odešel" gives "Potom odešel."; German "Ludwig XIV. war König." aimed at "König" gives the whole sentence; "Es regierte Ludwig XIV. Dann kam…" ends at "XIV." | N | U CzechSupportTest, GermanSupportTest, LanguageRulesIsolationTest, M | — |
| ANK16 | `{phonetic-transcriptions}` with tags: an English note of "water" with the Wiktionary transcriptions lists each one with its dictionary tags before it in italics ("(General-American) /ˈwɔ.tɚ/"), as Yomitan's marker writes them; a transcription without tags has none; a Japanese note with a pitch dictionary unchanged | N | U TranscriptionsTest, P note.test, M | — |
| ANK17 | Right-to-left Anki fields (Arabic; a note type with Front, Back, Reading and Sentence in the emulator's local collection): "Fill in suggested templates" gives Reading `<span dir="rtl">{reading}</span>` and Sentence `<span dir="rtl">{sentence}</span>`, and Front `{expression}` without a span; a note added from the popup for قال in "هل تعرف اللغه العربيه؟ قال د. أحمد: نعم." has Front قال, Reading `<span dir="rtl">قَالَ</span>` and Sentence `<span dir="rtl">قال د. أحمد: نعم.</span>` (the sentence starts after ؟ and keeps د.) | F | U FieldTemplateTest, SentenceTest, ArabicSupportTest, M | 2026-10-20 |
| ANK18 | Persian Anki fields (the note type with Front, Back, Reading and Sentence in the emulator's local collection): "Fill in suggested templates" gives Reading `{reading}` without a span, as the reading is Latin, and Sentence `<span dir="rtl">{sentence}</span>`; a note added from the popup for "نگاه کن" has Front نگاه کردن, Reading nigāh kardan and the sentence in the span | F | U FieldTemplateTest, SentenceTest, PersianSupportTest, M | 2026-10-20 |
| ANK19 | Hebrew Anki fields (the note type with Front, Back, Reading and Sentence in the emulator's local collection): "Fill in suggested templates" gives Front `{expression}`, Reading `<span dir="rtl">{reading}</span>` (the reading is the word itself) and Sentence `<span dir="rtl">{sentence}</span>`; a note added from the popup for הכלבים in "כשהגעתי לירושלים הכלבים נבחו." has Front כלב and Sentence `<span dir="rtl">כשהגעתי לירושלים הכלבים נבחו.</span>` | F | U FieldTemplateTest, SentenceTest, HebrewSupportTest, M | 2026-10-20 |
| ANK20 | A Kazakh Anki note: added from the popup for қазанда in "Ол 2007 ж. қазанда келді.", it has Front қазан and the whole sentence as Sentence ("ж." before a lowercase word ends nothing) | F | U SentenceTest, KazakhSupportTest, M | 2026-10-20 |
| ANK21 | A Kyrgyz Anki note: added from the popup for алды in "Ал 1991-ж. жайында өрүк ж. б. алды.", it has Front алуу and the whole sentence as Sentence (1991-ж. and ж. б. before lowercase words end nothing) | F | U SentenceTest, KyrgyzSupportTest, M | 2026-10-20 |
| ANK22 | Czech "n. l." read by OCR (`scripts/text-image.py` with `FONT=C:/Windows/Fonts/arial.ttf`, where l and I look alike, and `PITCH=96` for the line break; cloud recognition, then ML Kit): in "Roku 300 př. n. l. se narodil. Potom odešel." an I read between "n." and a period becomes l however the engine splits the words (cloud recognition gives "n" "." "I" ".", ML Kit "n.I."), also with "n." ending a line, and `{sentence}` of "narodil" is "Roku 300 př. n. l. se narodil."; app text (AnkiDroid's note editor) with a capital I typed, "Roku 300 n. I. Potom odešel.", is taken as written: `{sentence}` of "odešel" is "Potom odešel." | F | U CzechSupportTest, LanguageRulesIsolationTest, M | 2026-10-22 |
| ANK23 | A Swedish Anki note: added from the popup for torget in "Vi ses kl. 8, t.ex. på torget." (`testdata/ocr/sv-sample.png`), it has the whole sentence as Sentence (kl. and t.ex. end nothing); for Han in "Hon är ung. Han är gammal." the sentence is "Han är gammal." (ung. before a capital ends one) | F | U SentenceTest, SwedishSupportTest, M | 2026-10-22 |
| ANK24 | A Norwegian Anki note: added from the popup for epler in "Hun kjøpte f.eks. epler den 17. mai kl. 8." (`testdata/ocr/no-sample.png`), it has the whole sentence as Sentence (f.eks., 17. and kl. end nothing); for Vi in "Byen ble grunnlagt 300 f.Kr. Vi vet lite om den." the sentence is "Vi vet lite om den." (f.Kr. before a capital ends one) | F | U SentenceTest, NorwegianSupportTest, M | 2026-10-22 |
| ANK25 | A Danish Anki note: added from the popup for æbler in "Hun købte f.eks. æbler den 5. maj kl. 8." (`testdata/ocr/da-sample.png`), it has the whole sentence as Sentence (f.eks., 5. and kl. end nothing); for råbte in "»Kom her!« råbte hun." the sentence keeps the guillemets, also with cloud recognition, which reads them as >> << (written » « in OCR text, ANK33); for Vi in "Æbler, pærer osv. Vi gik." the sentence is "Vi gik." (osv. before a capital ends one) | F | U SentenceTest, DanishSupportTest, M | 2026-10-22 |
| ANK26 | A Romanian Anki note: added from the popup for Popescu in "Dl. Popescu a venit la nr. 5, pe str. Lipscani." (`testdata/ocr/ro-sample.png`; app text of the same sentence, as OCR reads Dl. as DI.), it has the whole sentence as Sentence (dl., nr. and str. end nothing); for strigat in "„Vino aici!” a strigat ea." the sentence keeps the quotes, also with cloud recognition, which reads ” as " (written ” after the !, ANK33); for Apoi in "S-a născut în 1999. Apoi a plecat." the sentence is "Apoi a plecat." | F | U SentenceTest, RomanianSupportTest, M | 2026-10-22 |
| ANK27 | A Latin Anki note: for Cicero in "C. Iulius Caesar et M. Tullius Cicero Romae erant." the sentence is whole (the praenomina end nothing); for Liberi in "Liberi erant sex. Deinde abierunt." the sentence ends at sex.; a.C.n. before a lowercase word ends nothing | F | U SentenceTest, LatinSupportTest, M | 2026-10-22 |
| ANK28 | A Lithuanian Anki note: for Kazlauskas in "Prof. Kazlauskas 1950 m. rugsėjo 5 d. gimė Vilniuje." the sentence is whole; for obuoliai in "Vaisiai, t. y. obuoliai ir kriaušės, kainavo 5 tūkst. eurų." it is whole; "Jis gimė 1990. Vėliau išvyko." ends at 1990. | F | U SentenceTest, LithuanianSupportTest, M | 2026-10-22 |
| ANK29 | A Slovak Anki note: for Novák in "Prof. Novák sa narodil 5. mája 1950 v Bratislave." the sentence is whole; for jablká in "Ovocie, t. j. jablká a hrušky, stálo napr. 5 tis. eur." it is whole; "Vznikol v 3. stor. pred Kr. Potom zanikol." ends at Kr. | F | U SentenceTest, SlovakSupportTest, M | 2026-10-22 |
| ANK30 | An initial before a surname holds the sentence: a Kyrgyz note added from the popup for келди in "Президент К. Бакиев Ошко келди." (`scripts/text-image.py`, Segoe UI) has the whole sentence as Sentence | F | U SentenceTest `anInitialBeforeASurnameKeepsTheSentenceInEverySpacedLanguage`, KyrgyzSupportTest, M | 2026-10-22 |
| ANK32 | A Slovenian Anki note: for Novak in "Prof. dr. Novak se je rodil 5. maja 1950 v Ljubljani." the sentence is whole; for jabolka in "Sadje, t. i. jabolka in hruške, je stalo npr. 5 tis. evrov." it is whole; for je after the quote in "»Pridi sem!« je zaklicala." it is whole, for sem inside it "»Pridi sem!«"; "Mesto je nastalo v 3. stol. pr. n. št. Potem je propadlo." ends at št. | F | U SentenceTest, SlovenianSupportTest, M | 2026-10-22 |
| ANK33 | Quotes that cloud recognition reads as plain characters: over `testdata/ocr/sl-anki.png`, the popup's text has »Pridi sem!« and the note for sem has "»Pridi sem!«", the one for je after it "»Pridi sem!« je zaklicala."; over an image of "„Vino aici!” a strigat ea." (Romanian) the strigat note has the whole sentence; the same Slovenian sentence typed with >> << in AnkiDroid's note editor (app text) keeps >> << (there they are no quotes, so the ! ends the sentence: ">>Pridi sem!" for sem, "<< je zaklicala." for je) | N | U OcrQuotesTest, OcrPageLanguageTest, Isolation, M | 2026-10-28 |
| ANK34 | An Esperanto Anki note (`testdata/ocr/eo-anki.png`, one paragraph), with on-device recognition: for foriris the sentence is "Li diris „Jes.“ kaj foriris.", for amiko "“Venu, amiko.”", for venis "“Venu, amiko.” Ŝi venis."; for pomoj "Ekz. pomoj kaj piroj ktp."; for jarcento "La 9-a jarcento a.K. estis longa."; with cloud recognition pomoj and jarcento as above, while foriris, amiko and venis get the " it writes for the “ after Jes. and before Venu (group 5a question 30) | N | U SentenceTest, EsperantoSupportTest, M | 2026-10-28 |
| ANK35 | An Albanian Anki note (over `testdata/ocr/sq-anki.png`, and app text in AnkiDroid's note editor): for banon in "Z. Hoxha banon në rr. Durrësit, nr. 5." the sentence is whole and ends at 5.; for revista in "Libra, revista etj. dhe gazeta janë këtu." it is whole; "Cezari vdiq në vitin 44 p.e.s. Pastaj Roma ndryshoi." ends at p.e.s.; for ka inside "«Ai s'ka kohë!» tha ajo." the note has "«Ai s'ka kohë!»", for tha after it the whole sentence | F | U SentenceTest, AlbanianSupportTest, M | 2026-10-28 |
| ANK36 | A Swahili Anki note (over `testdata/ocr/sw-anki.png`, one paragraph over five lines): for Juma the sentence is "Bw. Juma alifika tar. 5 Mei."; for wali "Walinunua vyakula, k.m. wali na maharage n.k." (n.k. ends it before a capital); for walirudi and for the phrase hata hivyo "Hata hivyo walirudi."; for karibu "“Karibu!”" and for alisema "“Karibu!” alisema Bi. Amina." where recognition keeps the curly quotes (where it writes them as ", plan question 30) | F | U SentenceTest, SwahiliSupportTest, M | 2026-10-28 |
| ANK37 | A Welsh Anki note (over `testdata/ocr/cy-anki.png`, one paragraph over six lines): for yno the sentence is "Pregethodd y Parch. John Williams yno, h.y. yng Nghaerdydd."; for afalau "Prynodd ffrwythau, e.e. afalau a gellyg."; for Dafydd "Daeth Dafydd Jones, Ysw." (Ysw. ends it before a capital); for fawr "Mae'r tŷ yn fawr." (ML Kit writes tý) | F | U SentenceTest, WelshSupportTest, M | 2026-10-28 |
| ANK38 | An Irish Anki note (over `testdata/ocr/ga-anki.png`, one paragraph over six lines, ML Kit): for úlla or piorraí the sentence is "Cheannaigh sí torthaí, m.sh. úlla agus piorraí srl." (srl. ends it before a capital); for leabhar "Féach lch. 5 den leabhar."; for baile "D'fhág an Br. Seán an baile." | F | U SentenceTest, IrishSupportTest, M | 2026-10-28 |
| ANK39 | A Scottish Gaelic Anki note (over `testdata/ocr/gd-anki.png`, one paragraph over seven lines, ML Kit): for ùbhlan the sentence is "Cheannaich i measan, m.e. ùbhlan is peuran msaa." (msaa. ends it before a capital); for Iain "Chunnaic mi an Urr. Iain an-dè."; for dhachaigh "Thàinig an Dr. Dòmhnallach dhachaigh." | F | U SentenceTest, GaelicSupportTest, M | 2026-10-28 |
| ANK40 | A Finnish Anki note (over `testdata/ocr/fi-anki.png`, one paragraph over five lines, ML Kit, cloud recognition where ML Kit misreads the word): for omenoita the sentence is "Ostin 24. toukokuuta hedelmiä, esim. omenoita ja päärynöitä jne." (the ordinal and esim. hold, jne. ends it before a capital); for tuella "Talo valmistui 1990-luvulla EU:n tuella." | F | U SentenceTest, FinnishSupportTest, M | 2026-10-28 |
| ANK41 | A Hungarian Anki note (over `testdata/ocr/hu-anki.png`, one paragraph over several lines, ML Kit, cloud recognition where ML Kit misreads the word): for almát the sentence is "2023. május 3-án gyümölcsöt vettem, pl. almát és körtét stb." (the date, the ordinal and pl. hold, stb. ends it before a capital); for épült "A ház a 19. sz. végén épült." | F | U SentenceTest, HungarianSupportTest, M | 2026-10-28 |
| ANK42 | An Estonian Anki note (over `testdata/ocr/et-anki.png`, one paragraph over several lines, ML Kit, cloud recognition where ML Kit misreads the word): for õunu the sentence is "2020. a. lõpus ostsime u. 5 kg õunu, pirne jne." (the year, a., u. and the number hold, jne. ends it before a capital); for ehitati "Maja ehitati 19. sajandil." | F | U SentenceTest, EstonianSupportTest, M | 2026-10-28 |
| ANK43 | An Azerbaijani Anki note (over `testdata/ocr/az-anki.png`, one paragraph over several lines, ML Kit, cloud recognition where ML Kit misreads the word): for armud the sentence is "Bazardan alma, armud və s. aldıq." (və s. holds before a lowercase word); for yaşayır "Prof. Əliyev Bakı şəh. Nizami küç. 5-də yaşayır." (prof., şəh. and küç. hold); for dəftərlər "Kitablar, dəftərlər və s." (və s. ends it before a capital) | F | U SentenceTest, AzerbaijaniSupportTest, M | 2026-10-28 |

### TR: sentence translation

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| TR1 | The translation button in the popup: a tap shows the translation, the service name and a copy button; a second tap hides it; a new word starts without it | F | M | 2026-10-26 |
| TR2 | Services in order with the 3 s fallback; all failing → each one's reason | O | U SentenceTranslatorTest, TranslationErrorTest, M | |
| TR3 | "Translate into": search by any name, stars on top | O | U TranslationLanguagesTest, M | |
| TR4 | Test asks every enabled service and shows answers and times | O | M | |

### DIC: dictionaries

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| DIC1 | Import a `.zip` from a file: progress in the card and the notification; Cancel there and in the notification | F | M | 2026-10-14 |
| DIC2 | Catalog download: the ring (waiting, filling, spinning), its stage line, ✕ inactive in the first second; the import after it; the task is named by its catalog title in the interface language, on its card and on home | F | M | 2026-10-22 |
| DIC3 | A download that breaks off goes to the end of the queue and retries by itself up to three times (attempt on its card, 30 s, 1 and 2 min apart; the tasks behind it start meanwhile) and continues where it stopped; after that, Resume and Cancel, and the card says the connection was lost (not the technical reason); Cancel deletes the partial file. | F | U ResumableDownloadTest, DictionaryImportsTest, M | 2026-10-22 |
| DIC4 | The app killed during a download (`am kill` in the background) → the download continues by itself, no button; during an import → it restarts | F | M | 2026-10-14 |
| DIC5 | The app dies twice during an import → not started again, the card says so | O | U DictionaryImportsTest, M | |
| DIC6 | Order by drag, switches, sort dictionary; the last dictionary with definitions cannot be switched off or deleted | F | M | 2026-10-18 |
| DIC7 | Check for updates; an old bundled copy updates from the catalog; "Update" on one keeps the others listed; once every found update has started the list closes and the task cards show them, never "All dictionaries are up to date" while they download | O | U OutdatedCopiesTest, UpdateCheckTest, M | 2026-10-15 |
| DIC8 | A Yomitan collection export: list, checks, space check, import | O | U CollectionSpaceTest, YomitanBackupJvmTest, I YomitanBackupTest, M | 2026-10-14 (I) |
| DIC9 | No ⟲ on the Dictionaries screen (removed 2026-10-16, owner); a reset an earlier version left interrupted still finishes at start with the spinner at the top | F | U DictionaryResetTest, M | 2026-10-22 (the interrupted reset; its spinner not seen: deleting ends before a cold start's first frame) |
| DIC10 | A deleted bundled dictionary stays deleted after an app update (`main` only) | F | U BundledDictionariesTest, M | 2026-10-14 |
| DIC11 | Missing dictionary files → the home card; catalog ones downloaded again, copies an earlier version bundled too (`main`: bundled ones restored from the APK) | O | M | |
| DIC12 | Dictionary languages shown and changed by hand; a dictionary is used only for its word language | O | U DictionaryLanguagesTest, M | |
| DIC13 | wordfreq frequency dictionary built on the device: the list downloads, the checksum passes, the dictionary imports and sorts English | O | U WordfreqDictionaryTest, M | 2026-10-14 |
| DIC14 | Importing wty-en-ipa writes the transcription regions (shown in POP6) | O | U TranscriptionTagBanksTest, M | 2026-10-14 |
| DIC15 | The notification permission: the explanation dialog, then the system request, on the first import (API 33+) | O | M | 2026-10-18 |
| DIC16 | With several languages: language chips on the page, "Other languages" links to the Languages screen | F | M | 2026-10-18 (chips) |
| DIC17 | Chinese wordfreq built on the device ranks Traditional spellings too: 頭髮 shows the rank of 头发 | O | U WordfreqDictionaryTest, M | 2026-10-15 |
| DIC18 | Korean download screen: KRDICT (ko–en) ticked, Wiktionary not, wordfreq, transcription and Unihan Hanja ("Comes with the app") ticked; the download total leaves the hanja archive out; everything installs and Korean is ready | F | U LanguageSetupTest, CatalogFileTest, M | 2026-10-15 |
| DIC20 | A download started again while it is queued, running or waiting for its retry (catalog card, language setup, home "Download again", Update after "Check for updates") adds no second task; with its stopped card listed, a new download of it continues it and the stopped card goes | F | U DictionaryImportsTest, M | 2026-10-19 (catalog card, Update), 2026-10-22 (setup, home) |
| DIC21 | Two downloads run at a time, each with its own notification; one finished first while the other imports shows "Waiting" until that import ends; Cancel in either notification cancels only that task | F | M | 2026-10-22 |
| DIC22 | Two or more waiting tasks share one card "Waiting (N): …"; "Show the list" lists them with a Cancel each, "Hide the list" closes it; "Cancel all" cancels only the waiting ones; a single waiting task and a download waiting for its retry keep their own cards | F | U TaskItemsTest, M | 2026-10-22 |
| DIC23 | With Wi-Fi and mobile data off, a queued catalog download's card says "Waiting for a network: …" and its catalog card "Waiting for a network"; a shared waiting card has the line under its title; a waiting retry keeps its retry text with the line under it; a file import does not say it; with the network back the cards go back to "Waiting" or start | F | U TaskItemsTest, DictionaryImportsTest, M | 2026-10-28 (`main`: a queued download offline, which has no shared card; the file import not run) |
| DIC24 | Partial downloads are in `no_backup/dictionary-downloads/`: a stopped download keeps its part after Settings → Storage → Clear cache and resumes from it; the old `cache/dictionary-downloads/` of an older build loses at the next import what no queued import reads (a file import queued there before the update still installs) and goes once empty | F | U StorageCleanUpTest, M | 2026-10-28 (`main`: the update from `2df1e55c` with a file import queued; Clear cache with a download waiting for a network), 2026-10-28 (`main`, before the fix that keeps the queued files) |
| DIC25 | While a language installs (setup just finished), Home shows its install progress and no "no dictionary with definitions" card for the categories being installed; with only a model download left, the bubble card has no "Installing dictionaries…" hint; "Check for updates" names a model update's languages ("Tesseract, fast" · English · size) | F | U InstallingTest, UpdateCheckTest, M | 2026-10-22 |
| DIC26 | A Leipzig frequency list (Kazakh): the setup downloads `kaz_newscrawl_2016_100K` (18 MB, SHA-256 checked), keeps only its word list, builds 100,000 words on the device and registers "Leipzig Corpora Collection (kk)"; the entries show its ranks | F | U CorpusCountsDictionaryTest, test_corpus_catalog.py, M | 2026-10-20 |
| DIC27 | A Leipzig frequency list (Kyrgyz): the setup downloads `kir_newscrawl_2016_100K` (19 MB, SHA-256 checked), builds 100,000 words on the device and registers "Leipzig Corpora Collection (ky)"; the entries show its ranks | F | U CorpusCountsDictionaryTest, test_corpus_catalog.py, M | 2026-10-20 |
| DIC28 | PanLex conversions (`scripts/panlex/panlex_yomitan.py`, kk and ky into ru and en) import from a file with their language pair ("PanLex ky-ru": ky → ru); a lookup shows one line of comma-separated translations with Russian stress marks, under the wty entry of the same headword; verb chains reach these untagged entries (алды → алуу) | O | U test_panlex_yomitan.py, M | 2026-10-20, 2026-10-22 (kk-en, ky-ru) |
| DIC29 | Wi-Fi and mobile data off during a catalog download: its task card says "Waiting for a network: …" and its catalog card "Waiting for a network", the log "stopped by WorkManager (reason 7): waits for a network, then runs again" (or "the network went away during the download (…) and is back; continues", "…, none for 30 s; tries again"); no retry is counted and no "download stopped" card appears; with the network back the download continues from the bytes already there ("… KB already there") and installs | F | U DictionaryImportsTest, M | 2026-10-28 (`main` and `claude/download-fixes-lang` after the fix round), 2026-10-28 (`main`) |
| DIC30 | The network goes at the moment the last retry breaks off (a proxy `10.0.2.2:8899` that never answers for the break-offs, Wi-Fi and data off just before or just after the third retry's 20 s read timeout): the card keeps "Retrying the download (3 of 3)" with "Waiting for a network", never "download stopped"; with the network back (proxy removed) it finishes. A task that ended (stopped after its last retry, cancelled, failed or installed) never runs again: turning the network off and on starts nothing | F | U DictionaryImportsTest, M | 2026-10-22 |
| DIC31 | A download queued after a stopped one in the same chain downloads its own entry: two downloads started together while a stopped download is the last task of a chain (the second goes to that chain) each download and install their own archive; Resume on a stopped card continues that card's entry, also with another stopped card listed | F | U DictionaryImportsTest, M | 2026-10-28 (`main` after the fix round: the retries waiting in the run, two stopped cards, Resume on each), 2026-10-28 (`main`: two downloads after a stopped one, Resume by hand) |
| DIC32 | A transcription dictionary with millions of tagged transcriptions imports with its tags (Chinese: Transcription (Wiktionary), wty-zh-ipa, 4.4 million): no "Reading or writing transcription tags failed" in the log, `transcription_tags.bin` in its directory, and the `{phonetic-transcriptions}` marker of 中国 gives "(zh_pron, Pinyin, Mandarin) Zhōngguó"; a transcription dictionary imported by an older version (the first file format) still shows its tags (English water) | F | U TranscriptionTagsTest, TranscriptionTagBanksTest, M | 2026-10-22 |
| DIC33 | Norwegian dictionaries imported from a file: wty's nn–en archive (its index names nn) and the WikDict no-ru conversion (`scripts/freedict`, its index names no) are both filed under Norwegian, and a Norwegian lookup shows their entries | F | U IndexLanguageTest, test_freedict_yomitan.py, M | 2026-10-22 |
| DIC34 | Czech dictionaries under Slovak: with Slovak on and Czech off, the Dictionaries screen lists the installed cs-en in Slovak's list (cs → en) and "Turned off" does not list it; turning Czech off with "Delete its dictionaries and models" keeps cs-en while Slovak is on; with both off, cs-en is listed under "Turned off" as Czech's | F | U DictionaryLanguagesTest, OcrModelsTest, M | 2026-10-22 |
| DIC35 | PanLex keeps capitalized headwords without time zones and interface strings: a PanLex ky-ru built by the converter after 2026-10-22 imports from a file, and "Иран риалы" shows its PanLex line "Иранский риал" | O | U test_panlex_yomitan.py, M | 2026-10-22 |
| DIC36 | Catalog parts: each translation language and each other kind is a heading with its count ("Russian · 3", no arrow); English, the language's own (Japanese for Japanese) and the interface language's come first, the other translation languages sit inside "Other languages · N" (N languages, closed at first, each inner part indented and closed), then a divider and "Supplementary dictionaries" over frequency, pitch and kanji; with the interface in English only English is open, in Russian only Russian; a download or an update waiting in an inner part opens it and "Other languages"; a part with a download under way or an update waiting opens by itself; a part closed by hand stays closed (also with a download in it) after leaving the page and coming back, and starts from the defaults again after the process restarts; TalkBack reads "Show the list" or "Hide the list" on a heading | N | U CatalogSectionsTest, M | |
| DIC37 | Latin's frequency dictionary: the download screen ticks "Word frequency (Leipzig Corpora Collection)", its description naming Wikipedia articles; it builds on the device from Leipzig's archive (15.5 MB) and the frequency chip shows on Latin entries; amicis shows amicus (rank 2,148) before amicio | N | U CorpusCountsDictionaryTest, CatalogFileTest, LatinSupportTest, M | 2026-10-28 |
| DIC38 | FreeDict slv-eng converted by `scripts/freedict` imports from a file under Slovenian (its index names sl), credits Gorazd Gorup and FreeDict, and ob shows its FreeDict line | O | U test_freedict_yomitan.py, M | 2026-10-28 |
| DIC39 | Esperanto's frequency dictionary: the download screen ticks "Word frequency (Leipzig Corpora Collection)", its description naming Wikipedia articles; it builds on the device from Leipzig's archive (19.1 MB) and the frequency chip shows on Esperanto entries; si still shows si before ŝi, the chips giving si 366 and ŝi 46 | N | U CorpusCountsDictionaryTest, CatalogFileTest, M | 2026-10-28 |
| DIC40 | Albanian's frequency dictionary: the download screen ticks "Word frequency (Leipzig Corpora Collection)", its description naming news texts; it builds on the device from Leipzig's archive (24.9 MB) and the frequency chip shows on Albanian entries | F | U CorpusCountsDictionaryTest, CatalogFileTest, M | 2026-10-28 |
| DIC41 | Swahili's frequency dictionary: the download screen ticks "Word frequency (Leipzig Corpora Collection)", its description naming Wikipedia articles; it builds on the device from Leipzig's archive (18.6 MB) and the frequency chip shows on Swahili entries | F | U CorpusCountsDictionaryTest, CatalogFileTest, M | 2026-10-28 |
| DIC42 | The Finnish word forms item: with Russian chosen and Wiktionary (fi–en) unticked, the download screen lists "Word forms (Wiktionary)" under Word forms, fixed and mandatory, with its download and installed size; it imports the fi-en archive without definitions (import time and installed size recorded in the run log), the Dictionaries screen lists it, and Russian entries are found for inflected words (talossa → talo); with fi-en ticked the item is gone, and an installed fi-en counts as it | F | U LanguageSetupTest, CatalogFileTest, I LanguageLookupTest, M | 2026-10-28 |
| DIC43 | The Hungarian word forms item: with Russian chosen and Wiktionary (hu–en) unticked, the download screen lists "Word forms (Wiktionary)" under Word forms, fixed and mandatory; it imports the hu-en archive without definitions (import time and installed size recorded in the run log), and Russian entries are found for inflected words (házban → ház); with hu-en ticked the item is gone | F | U LanguageSetupTest, CatalogFileTest, M | 2026-10-28 |
| DIC44 | The Estonian word forms item: with Russian chosen and Wiktionary (et–en) unticked, the download screen lists "Word forms (Wiktionary)" under Word forms, fixed and mandatory; it imports the et-en archive without definitions (import time and installed size recorded in the run log), and Russian entries are found for inflected words (majas → maja); with et-en ticked the item is gone | F | U LanguageSetupTest, CatalogFileTest, M | 2026-10-28 |
| DIC45 | The Azerbaijani word forms item: with Russian chosen and Wiktionary (az–en) unticked, the download screen lists "Word forms (Wiktionary)" under Word forms, fixed and mandatory; it imports the az-en archive without definitions (import time and installed size recorded in the run log), and Russian entries are found for inflected words; with az-en ticked the item is gone | F | U LanguageSetupTest, CatalogFileTest, M | 2026-10-28 |
| DIC19 | Spanish download screen: Wiktionary (es–en), wordfreq and the transcription dictionary ticked, 38 MB; everything installs and Spanish is ready | F | U LanguageSetupTest, M | 2026-10-16 |

### LNG: languages

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| LNG1 | Home's languages card: the quick flags, the active one ringed, its name and status, "All languages" | E | M | 2026-10-27 |
| LNG2 | The Languages screen: turned on (radio, star, Flag, Turn off), turned off with files (list with sizes, delete one, change its languages, Delete all, Turn on), not turned on | F | M | 2026-10-18 |
| LNG3 | Star rules: at most three quick languages; making a starred one active swaps; making an unstarred one active moves the star | F | U LanguageProfilesTest, M | 2026-10-14 |
| LNG4 | Flag country: English US by default, the UK from "Flag"; a language with one flag (all but English and Portuguese) has no "Flag" button | O | U FlagsTest, LanguageProfilesTest, M | 2026-10-16 |
| LNG5 | E-ink on → two-letter codes instead of flags on home, the screen and in the bubble menu; the chosen language chip on the app's pages filled black, the others outlined | F | M | 2026-10-14 |
| LNG6 | The download screen: definition languages (the menu as wide as its button), categories, recommended ticked, sizes, Confirm disabled with a hint, space line; mobile data over 50 MB asks | F | U LanguageSetupTest, LanguageOptionsTest, M | 2026-10-22 |
| LNG7 | A language confirmed before its downloads finish: active at once, home status with progress, scans blocked with the link, search shows status and runs when ready | E | I SearchLanguageTest, InstallationPopupTest, M | 2026-10-18 |
| LNG8 | Turn off: "Delete its dictionaries and models" ticked, also cancels pending downloads before the first file; unticked keeps them | F | I TurnOffLanguageDialogTest, LanguageSwitchTest, M | 2026-10-18 |
| LNG9 | Turning a language on again with kept files: on at once, its settings back | F | I LanguageReactivationTest, M | 2026-10-14 |
| LNG10 | Settings per language stay apart (lookup, popup, Anki, audio, dictionary switches) | F | I AnkiProfileEditorsTest, U LookupSettingsRepositoryTest, M | 2026-10-14 |
| LNG11 | Switching from home's flags; switching during a scan applies from the next scan, a tap on the floating bubble included | F | M | 2026-10-14 |
| LNG12 | Japanese turned off with its files and on again → its download screen opens (`main`: the bundled dictionaries install again) | O | M | 2026-10-18 |
| LNG13 | Chinese card: "Characters" Simplified or Traditional with its hint, "All words in these characters" off by default; Traditional makes "xuexi" show 學習; the Lookup page has no such block; a backup's General part carries it, only the full reset clears it | F | U SettingsKeysTest, M | 2026-10-15 |
| LNG14 | Character dictionaries ticked on the download screen: KANJIDIC for Japanese (in each chosen gloss language that has one), CC-CEDICT Hanzi for Chinese, Unihan Hanja for Korean; each can be unticked | F | M | 2026-10-18 |
| LNG15 | Kazakh setup: the download screen offers Wiktionary in six gloss languages (English, Russian, French, Korean, Polish, Chinese), the transcriptions, the Leipzig list and PaddleOCR's Cyrillic model ticked, Tesseract kaz unticked; the flag is Kazakhstan's and the bubble shows Қ | F | U CatalogFileTest, M | 2026-10-20 |
| LNG16 | Kyrgyz setup: the download screen offers Wiktionary, the transcriptions, the Leipzig list and PaddleOCR's Cyrillic model ticked, Tesseract kir fast and accurate unticked; the flag is Kyrgyzstan's and the bubble shows Ң | F | U CatalogFileTest, M | 2026-10-20 |
| LNG17 | Thai and Khmer: under each Tesseract model, in Bubble → On-device model and on the download screen, "Often misreads text on busy screens; cloud recognition is more accurate."; Georgian's Tesseract models have no such line | O | U OcrModelsTest, LanguageRulesIsolationTest, M | 2026-10-22 |
| LNG18 | Swedish setup: the download screen offers Wiktionary in eleven gloss languages, the transcriptions and wordfreq ticked; ML Kit is the main model, PaddleOCR v5 Latin unticked and marked "more accurate", PaddleOCR v6 and Tesseract swe fast and accurate unticked; the flag is Sweden's and the bubble shows Å | F | U CatalogFileTest, M | 2026-10-22 |
| LNG19 | Norwegian setup: the download screen offers Wiktionary nb–en, nb–ja, nb–pl, nb–zh, no–ru, no–fr and no–vi ticked, no–zh and the Nynorsk pairs (nn–en, described "Norwegian Nynorsk → English") unticked, Bokmål's transcription and wordfreq ticked; ML Kit is the main model, PaddleOCR v6 unticked and marked "more accurate", PaddleOCR v5 Latin and Tesseract nor fast and accurate unticked; the flag is Norway's and the bubble shows Ø | F | U CatalogFileTest, generate.test.mjs, M | 2026-10-22 |
| LNG20 | Danish setup: the download screen offers Wiktionary into English, Russian, French, Japanese, Polish, Turkish and Chinese, the transcription and wordfreq ticked; ML Kit is the main model, PaddleOCR v5 Latin unticked and marked "more accurate", PaddleOCR v6 and Tesseract dan fast and accurate unticked; the flag is Denmark's and the bubble shows Æ | F | U CatalogFileTest, M | 2026-10-22 |
| LNG21 | Romanian setup: the download screen offers Wiktionary into English, Russian, Spanish, French, Japanese, Polish, Turkish and Chinese, the transcription and wordfreq ticked; ML Kit is the main model, PaddleOCR v5 Latin unticked and marked "more accurate", PaddleOCR v6 and Tesseract ron fast and accurate unticked; the flag is Romania's and the bubble shows Ș | F | U CatalogFileTest, M | 2026-10-22 |
| LNG22 | Latin setup: the download screen offers Wiktionary into English, Russian, German, Spanish, French, Italian, Japanese, Korean, Polish, Portuguese and Chinese and the transcription, ticked, and no frequency dictionary; ML Kit is the main model, PaddleOCR v6 unticked and marked "more accurate", PaddleOCR v5 Latin and Tesseract lat fast and accurate unticked; the flag is Latin's laurel wreath | F | U CatalogFileTest, FlagsTest, M | 2026-10-22 |
| LNG23 | Lithuanian setup: the download screen offers Wiktionary into English, Russian, German, French, Korean, Polish and Chinese and the transcription, ticked, and wordfreq; ML Kit is the main model, Tesseract lit fast unticked and marked "more accurate" (ticked, it becomes the chosen model), Tesseract accurate and PaddleOCR v5 Latin and v6 unticked; the flag is Lithuania's | F | U CatalogFileTest, FlagsTest, M | 2026-10-22 |
| LNG24 | Slovak setup: the download screen offers Wiktionary into English, Russian, French, Polish, Turkish and Chinese and the transcription, ticked, and wordfreq; after Slovak's own main dictionaries the Czech ones in the chosen languages of definitions, unticked, each with "Fallback dictionary (Czech)"; with only a Czech one ticked the button stays disabled; ticking cs-en downloads it with Slovak's; ML Kit is the main model, Tesseract slk fast unticked and marked "more accurate", Tesseract accurate and PaddleOCR v5 Latin and v6 unticked; the flag is Slovakia's | F | U LanguageSetupTest, CatalogFileTest, FlagsTest, M | 2026-10-22 |
| LNG25 | Slovenian setup: the download screen offers Wiktionary into English, Russian, French, Polish, Turkish and Chinese, the transcription and wordfreq ticked; ML Kit is the main model, PaddleOCR v5 Latin unticked and marked "more accurate", PaddleOCR v6 and Tesseract slv fast and accurate unticked; the flag is Slovenia's and the bubble shows SL | F | U CatalogFileTest, M | 2026-10-22 |
| LNG26 | Esperanto setup: the download screen offers Wiktionary into English, Russian, German, Spanish, French, Japanese, Polish, Portuguese, Turkish and Chinese and the transcription, ticked, and Leipzig's word frequency, ticked; ML Kit is the main model, Tesseract epo fast unticked and marked "more accurate", accurate unticked, no PaddleOCR; the flag is Esperanto's own and the bubble shows Ĉ | N | U CatalogFileTest, M | 2026-10-28 |
| LNG27 | Albanian setup: the download screen offers Wiktionary into English, Russian, German, French, Polish, Turkish and Chinese, the transcription and Leipzig's word frequency ticked; ML Kit is the main model, PaddleOCR v5 Latin unticked and marked "more accurate", PaddleOCR v6 and Tesseract sqi fast and accurate unticked; the flag is Albania's and the bubble shows Ë | F | U CatalogFileTest, M | 2026-10-28 |
| LNG28 | Swahili setup: the download screen offers Wiktionary into English, and Russian, Chinese and Polish under "Another language…", and Leipzig's word frequency ticked, no transcription; ML Kit is the main model, Tesseract swa fast and accurate and PaddleOCR v5 and v6 unticked, none marked "more accurate"; the flag is Tanzania's and the bubble shows SW | F | U CatalogFileTest, M | 2026-10-28 |
| LNG29 | Welsh setup: the download screen ticks Wiktionary into English and the transcription, lists Russian and French under "Another language…", and offers no word frequency; ML Kit is the main model, Tesseract cym fast and accurate and PaddleOCR v5 and v6 unticked, all four marked "more accurate"; the flag is Wales's and the bubble shows Ŵ | F | U CatalogFileTest, M | 2026-10-28 |
| LNG30 | Irish setup: the download screen ticks Wiktionary into English and the transcription, lists Russian, French and Polish under "Another language…", and offers no word frequency; ML Kit is the main model, Tesseract gle fast and accurate and PaddleOCR v5 and v6 unticked, none marked; the flag is Ireland's and the bubble shows Á | F | U CatalogFileTest, M | 2026-10-28 |
| LNG31 | Scottish Gaelic setup: the download screen ticks Wiktionary into English and the transcription, lists Russian under "Another language…", and offers no word frequency; ML Kit is the main model, PaddleOCR v5 and v6 ticked and marked more accurate, Tesseract gla fast and accurate unticked and unmarked; the flag is Scotland's and the bubble shows Ò | F | U CatalogFileTest, M | 2026-10-28 |
| LNG32 | Finnish setup: the download screen ticks Wiktionary into English, the word frequency and the transcription; with Wiktionary (fi–en) unticked, the word forms item is mandatory; ML Kit is the main model, Tesseract fin and PaddleOCR v5 and v6 unticked and unmarked; the flag is Finland's and the bubble shows Ä | F | U CatalogFileTest, LanguageSetupTest, M | 2026-10-28 |
| LNG33 | Hungarian setup: the download screen ticks Wiktionary into English, the word frequency and the transcription; with Wiktionary (hu–en) unticked, the word forms item is mandatory; ML Kit is the main model, Tesseract hun and PaddleOCR v5 and v6 unticked and unmarked; the flag is Hungary's and the bubble shows Ő | F | U CatalogFileTest, LanguageSetupTest, M | 2026-10-28 |
| LNG34 | Estonian setup: the download screen ticks Wiktionary into English, the word frequency (Leipzig news) and the transcription; with Wiktionary (et–en) unticked, the word forms item is mandatory; ML Kit is the main model, PaddleOCR v6 ticked and marked "More accurate", Tesseract est and PaddleOCR v5 unticked; the flag is Estonia's and the bubble shows Õ | F | U CatalogFileTest, LanguageSetupTest, M | 2026-10-28 |
| LNG35 | Azerbaijani setup: the download screen ticks Wiktionary into English, the word frequency (Leipzig news) and the transcription; with Wiktionary (az–en) unticked, the word forms item is mandatory; ML Kit is the main model, PaddleOCR v6 ticked and marked "More accurate", Tesseract aze unticked, PaddleOCR v5 not offered; the flag is Azerbaijan's and the bubble shows Ə | F | U CatalogFileTest, LanguageSetupTest, M | 2026-10-28 |

### SET: settings, backup, appearance

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| SET1 | Page resets and the full reset ask first and keep what they promise (dictionaries, fonts, interface language) | F | U SettingsKeysTest, M | 2026-10-14 |
| SET2 | Backup with a choice through the file picker ("Create backup…" → the parts dialog → Next); the file holds only the chosen sections, fonts and dictionaries; restore lists the parts, checked parts replace, the summary counts the fonts and dictionaries restored | F | U BackupArchiveTest, BackupFormatTest, BackupChoiceTest, M | 2026-10-15 |
| SET3 | Yomitan settings import from the last card of the backup screen: the summary, applied to the profile's language | F | U YomitanSettingsTest, M | 2026-10-15 |
| SET4 | Theme (system, light, dark) and colors (own, system) | O | U ThemeModeTest, M | |
| SET5 | E-ink mode: black and white, larger sizes offered, turning off restores them | O | U EInkSizesTest, EInkEnlargementTest, M | |
| SET6 | Interface language: each of the 14 locales; the bubble, popup and notifications follow it; a language chosen in the system's app settings while the app runs renames languages at once (`cmd locale set-app-locales`) | F | I AppLanguageResourcesTest, U TranslationsTest, M | 2026-10-14 |
| SET7 | Background work: battery optimization status and request, persistent notification switch, notifications-off note | O | M | |
| SET8 | Holding an icon button shows its description | O | M | |
| SET9 | Backup parts dialog: the list scrolls while Cancel and Next stay in view; Fonts and Dictionaries expand into files with sizes; an item's box is ✓, — or empty for all, some or none of its files; Cancel drops the changes; the next backup starts with the last accepted choice; in Russian | F | U BackupChoiceTest, M | 2026-10-15 |
| SET10 | Restore of one font and some of the dictionaries: only those are written, files of the replaced font are not left behind; a backup whose manifest has no Fonts section (older format) still lists its fonts with sizes and restores them | F | U FontBackupTest, BackupArchiveTest, M | 2026-10-15 |
| SET11 | The Dictionaries screen's Yomitan collection button opens the backup screen at the Yomitan card; Settings has no separate Yomitan item, and the backup item's subtitle names the import | O | M | 2026-10-15 |
| SET12 | Languages in backups (two languages on): the parts dialog starts with Languages; a language left out hides its dictionaries and catalog fonts and lowers the dictionary order count; the file's manifest lists the chosen languages, its settings hold no keys of the left-out ones and the turned-on list is limited to the chosen; without Japanese its audio key keeps only volume and autoplay. Restore: one language and no models, no Languages item; General turns the backup's language on after the others ("Turned on" in the summary); a language left unchecked keeps its settings, files and turned-on state. Recognition models in the archive are covered by unit tests until the catalog has one | F | U BackupLanguagesTest, BackupFormatTest, BackupArchiveTest, BackupChoiceTest, OcrModelStoreTest, M | 2026-10-15 |
| SET13 | Segmented choices (theme, colors, bubble aim and side, OCR boost, engines) mark the chosen segment by its fill alone, no check mark; long labels fit (Russian: "Системная", "По требованию") | O | M | 2026-10-15 |
| SET14 | The Settings list: Bubble, Text recognition, Lookup, Dictionaries, Anki, Audio, Popup, Translation, Camera, Appearance, Background work, Backup and restore, About, each with its icon and summary; Text recognition holds Screen recognition (Engines, OCR boost, Reduce load, On-device model), App text (Read app text, App text only with its question) and Test (OCR test opens); About has no Tools card; Camera → Engines, the e-ink hint's "App text only…" (scrolls to App text) and the overlay's "the device cannot read" link (opens that language's models) lead to it | N | M | |
| SET15 | Bubble → "Hide the bubble in apps" says "Shown in all apps" or "Hidden in N apps" (Russian: 1 приложении, 2 и 5 приложениях), an uninstalled hidden app not counted; it opens the list with its search, hidden apps first; a change shows in the count on return | N | M | |
| SET16 | Anki and Audio are two pages, each with the language card while several languages are on; Home's failed audio sources link opens Audio on that language; Anki's reset keeps the audio settings; Audio's reset returns auto play, volume, sources and regions (of another language than Japanese: its own sources and the shared auto play and volume); Text recognition's reset returns the engines, OCR boost, Reduce load, the model choices and the text source, Bubble's no longer touches them | N | U SettingsKeysTest, M | |
| SET17 | Background work ends with "If the service turns off" and its instruction (English and Russian; no vendor names) | N | M | |

### UPD: About, logs, updates

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| UPD1 | About: libraries, notices, installed dictionaries with licenses, "What the service reads and sends", "Report a problem" opens the form (not submitted) | O | M | |
| UPD2 | Logs: share and save to Downloads; after scans, lookups and a note the log has no recognized text, words, note contents or URLs with terms | E | U RedactionTest, M | 2026-10-27 |
| UPD3 | Update check and install (release builds only) | O | D | |
| UPD4 | The export starts with the own log (oldest first: "Process started", the exit reasons of earlier processes, scans, the service's stop report), then the logcat part; lines from before an app restart are still there | F | U LogFileTest, ExitReasonsTest, M | 2026-10-22 |
| UPD5 | The own log keeps at most two files of 4 MB in `noBackupFilesDir/logs` (`run-as` on a debug build); uninstalling the app removes them, a log saved to `Download/Screenlate/` stays | O | U LogFileTest, M | 2026-10-22 (API 30) |
| UPD6 | Turn the service off in the accessibility settings (or `settings put secure enabled_accessibility_services` without Screenlate) → about 3 s later, with the line "3 s after the stop", a banner "Screenlate service is off" without sound, in the app's language; a tap opens the service's settings (or the list); turning the service on removes the notification; with notifications off, no banner and a log line saying so | F | U StopFollowUpTest, M | 2026-10-22 |
| UPD7 | The stop report in the log: "on the enabled list: no" after the switch, the time since the last bubble touch and scan, recent windows with package names of system and phone-maker apps only (hidden ones by name prefix), and a line 3 s later | F | U ServiceStopDiagnosticsTest, M | 2026-10-22 |
| UPD8 | The service turned off before the overlay has added its windows: jdb holds the main thread at the first `OverlayController.applySettings` after the service connects (`stop thread at`), the service is turned off with `settings delete secure enabled_accessibility_services`, the thread goes on (steps in the run log of 2026-10-22, `claude/jolly-cray-bc8ce9`) → no crash; the log has "Window not added, the system is unbinding the service" and then the stop report, the process lives on; turned on again, the bubble shows docked and scans. Also: on and off again with gaps of 0.1-3 s, no crash | F | U OverlayWindowsTest, M | 2026-10-22 |
| UPD9 | The service stops and comes back within 3 s: `uiautomator dump` (UiAutomation pauses the service for about 1.5 s), two dumps in a row, turned off and on again within 3 s → no banner at any point (`dumpsys notification` has no Screenlate notification 3 and 5 s later); the log has the stop report and "3 s after the stop: … running: yes" | F | U StopFollowUpTest, M || 2026-10-22 |

### CAM: camera photos

Recognition-dependent cases await phase 1.3. Phase 1.2 supplies capture, a fit viewer and source activation.

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| CAM1 | A recognized photo on the Camera page, pull the bubble out → the entry of the word under the aim opens from the stored text: the log has no capture, app text or cloud connection; the glyph shows the photo's recognition language | F | U ScreenReadsTest, M | 2026-10-26 (API 37) |
| CAM2 | Pan and zoom the photo under the resting bubble → the highlight and the popup follow the word; another word under the aim opens its entry | F | U TextFrameTest, M | 2026-10-26 (API 30/37) |
| CAM3 | Aim at the toolbar, the letterbox or the part of a zoomed photo outside the viewer → no entry; a highlight is cut at the viewer's edge | F | U TextFrameTest, M | 2026-10-26 (API 37) |
| CAM4 | The live camera, a photo still loading or not recognized yet → pull-out and tap show nothing and do not scan the screen | F | U ScreenReadsTest, M | 2026-10-26 (API 30/37) |
| CAM5 | Bubble out over a photo, then another page, another app or the live camera → the bubble docks; while a finger drags it, it docks when the finger lifts, and back over the photo before that it stays out with the photo's text; back on the photo, the next pull-out takes its text without recognition | F | U SourceChangeTest, M | 2026-10-27 (API 37; leaving during a drag), 2026-10-26 (API 37) |
| CAM6 | Bubble out over the screen of another page, then a recognized photo is shown → the old entry closes and the photo's text is under the aim | F | U SourceChangeTest, M | 2026-10-26 (API 37) |
| CAM7 | Hold the bubble over a photo → copy only: no Open Screenlate (also over any other Screenlate page, BUB17) and no language flags; the docked glyph returns to the active language once the photo is left | F | M | 2026-10-28 (API 37), 2026-10-26 (API 37; Open Screenlate was still shown) |
| CAM8 | Holding Add over a recognized photo opens a fitted original-photo crop; paragraph/frame/whole-photo and cancel work; the frame's margin around the paragraph is the same at any zoom of the viewer; Anki gets the original-pixel crop and sentence in the recognition language, with no screenshot read | F | U CropPlacementTest, PhotoViewportTest; I PhotoImageTest, CropEditorTest; M | 2026-10-27 (manual API 37), 2026-10-26 (API 30/37; manual API 37) |
| CAM9 | The app closed and started again → the current photo and its text come back; a damaged text file leaves the photo without text | F | U PhotoStoreTest, I PhotoDatabaseTest | 2026-10-26 (API 37) |
| CAM10 | No current photo → camera permission is requested once per process; deny → explanation and retry; deny permanently → app settings; grant there and return → live rear camera | F | M | 2026-10-26 (API 37) |
| CAM11 | Capture → fitted current photo; New photo + Close or Back preserves it; a completed new capture replaces it; ✕ closes it (CAM34) | F | U CameraViewModelTest, M | 2026-10-26 (API 37) |
| CAM12 | Tap preview → focus indicator; pinch → camera zoom and the zoom pill; tap the pill → 1× | F | I LiveCameraTest (API 30), D (focus quality) | 2026-10-26 (API 30) |
| CAM13 | All 8 EXIF orientations display upright and report matching original-pixel-to-screen coordinates; loading/disposal never publishes another document's viewport | F | U PhotoDrawingTest, U CameraViewModelTest, I PhotoViewerTest | 2026-10-26 (API 37) |
| CAM14 | Capture with the service unavailable; navigate and background; restart the device → the same private current photograph opens without camera permission being requested again; leaving capture releases the camera | F | U CameraViewModelTest, M | 2026-10-26 (API 37) |
| CAM15 | Flash-capable camera → Off/Auto/On cycle affects capture; Off again in a new process; absent flash → action hidden | N | U PhotoDrawingTest (cycle), D | |
| CAM16 | Tile grid covers both axes/edges; overlap assembly keeps full boundary words, repeats, character boxes, paragraphs, vertical/RTL order and combining marks; each word once across lines and orientations, the longer of two segmentations, no misread cut word, long words cut by both tiles joined also when the cut glyphs are dropped | F | U PhotoTilesTest, PhotoAssemblyTest | 2026-10-27 |
| CAM17 | Indexed hit testing preserves the former linear algorithm's tolerance, center selection, ties and zero-area separator boxes | F | U BoxIndexTest, TextLayoutTest | 2026-10-26 |
| CAM18 | Photo tile engine: Device makes no cloud call; Both waits for all readers, uses cloud or local fallback; pinned model/files ignore later changes, verified once on failure; a scan after a release request keeps the engines past the attempt; stored per-word provenance reads back and v1 still loads | F | U CompositeOcrTest, DeviceOcrTest, RecognitionCodecTest | 2026-10-27 |
| CAM19 | Recognition publishes once after all tiles terminate; usable partial text stays searchable; warning explains failures and retry keeps successful tiles and original settings | F | U PhotoRecognitionJobsTest, CameraPhotoControlsTest; M | 2026-10-26 (API 37: real Cloud, one missing tile) |
| CAM20 | Interrupted attempts and successful tiles survive process restart, remain separate from committed text and require explicit retry; Room v1 upgrades to v2; damaged progress preserves image/text | F | U PhotoStoreTest, AttemptCodecTest; I PhotoDatabaseTest | 2026-10-26 (API 37) |
| CAM21 | Original-resolution region decoding handles all EXIF transforms and the far right/bottom edges without decoding a full-size whole image | F | I PhotoRegionsTest | 2026-10-26 (API 37) |
| CAM22 | Camera language is independently remembered, limited to enabled languages; changes/settings show pending state and never rewrite actual OCR language or auto-recognize | F | U CameraPreferencesTest, CameraPhotoControlsTest; M | 2026-10-26 (API 37) |
| CAM23 | Successful capture starts recognition once; failed/cancelled capture preserves previous photo; work continues off Camera/backgrounded; cached lookup needs no OCR model and APP_TEXT_ONLY cannot bypass new image OCR readiness | F | U CameraViewModelTest, PhotoRecognitionJobsTest, LanguageInstallationTest; M | 2026-10-26 (API 37) |
| CAM24 | Viewer pinch clamps fit..8×, pan clamps edges, reopening fits; only viewport changes and the source/layout generation stays; visible-region details remain sharp | F | U PhotoViewportTest, I PhotoViewerGestureTest (API 30); M (API 37) | 2026-10-26 (API 30/37) |
| CAM25 | Explicit Save to gallery publishes ordinary original JPEG/PNG bytes with orientation and no OCR data; replacing/deleting the current document leaves the exported copy; failure/cancellation removes partial output and permits retry | F | U PhotoGalleryTest, CameraViewModelTest; I PhotoGalleryDeviceTest; M | 2026-10-26 (API 30/37) |
| CAM26 | A retained image survives current-photo replacement/deletion until its owner closes it; a restart removes abandoned pins; late source/preview results cannot open an editor after the scan closes | F | U PhotoStoreTest; I CropEditorTest, PhotoImageTest | 2026-10-26 (API 30/37) |
| CAM27 | The status strip over a photo: the language chip (flag, name, ▾), Recognize filled while there is no result or a change is pending and as a text button otherwise, hidden while recognizing; the status line "Recognized · N of M regions · time" (regions only with more than one), "Recognizing · k of M regions" with progress along the strip's bottom; a language change tints the strip and adds "Language changed" (a frame and bold text with the e-ink theme) | F | U CameraPhotoControlsTest; M | 2026-10-26 (API 37) |
| CAM28 | The language menu from the chip, over a photo and over the live camera: the header, every turned-on language with its flag, "Active" under the app's active language, "Dictionary missing" or "Recognition model missing" under unready ones, a check on the selected one; Manage languages opens Settings → Languages; with the menu open the bubble reads nothing | F | M | 2026-10-27 (API 37) |
| CAM29 | The live camera: the 4:3 frame at the photo's viewer size with a blurred live copy around it and no black band at the bottom (plain black with the e-ink theme); the chip floats over the preview; the white ring shutter shrinks when pressed, a spinner replaces its disc during capture; the zoom pill above it resets to 1×; the captured photo keeps the resolution it had before the blur stream; the log shows no repeated `setFlashAsync` while the preview runs | F | U CameraPhotoControlsTest (fill steps); I LiveCameraTest; M | 2026-10-26 (API 30/37) |
| CAM30 | Top bar: over a photo ✕ at the left and New photo as an icon; ⋮ holds Save to gallery, Save to history (only while the photo is outside the history: with manual saving, or after a failed automatic save), History, Import from gallery and Camera settings; over the live camera ⋮ holds History, Import from gallery and Camera settings; over the live camera with a photo the close icon at the left returns to it, the flash icon at the right; with ⋮ open the bubble reads nothing | F | M | 2026-10-27 (API 37; the menu and the bubble), 2026-10-26 (API 37) |
| CAM31 | The partial-result dialog: the warning icon, the count of missing regions with "Words in these regions can't be looked up.", a small picture of the photo with the missing regions marked, the causes; Retry keeps the recognized regions | F | U CameraPhotoControlsTest; M | 2026-10-26 (API 37) |
| CAM32 | Camera access off or blocked → an icon, a title, the text, a filled button and Import from gallery under it; deleting an unsaved photo on closing shows "Photo deleted"; loading shows "Opening photo…"; recognition blocked by a missing dictionary or model → the dialog is titled "Can't recognize <language>" | F | U CameraViewModelTest; M | 2026-10-26 (API 37) |
| CAM33 | A zoomed photo keeps its zoom and center when the strip changes height (pending tint, a wrapped status line) | N | U PhotoViewportTest; M | |
| CAM34 | ✕ over a photo: a photo in the history closes without a question and stays in the history; one outside it with manual saving asks "The current photo isn't saved to history and will be deleted" — Save to history keeps it there and opens the camera, Delete shows "Photo deleted", Cancel keeps it open; with automatic saving no question, unless saving that photo failed: then ✕, an import (CAM44) and a history photo (CAM37) ask as with manual saving | F | U CameraViewModelTest, PhotoHistoryViewModelTest; I UnsavedPhotoDialogTest (API 30); M | 2026-10-26 (API 30/37; a failed save by unit tests only) |
| CAM35 | ⋮ Save to history → "Saved to history" and the item leaves the menu; with Save automatically on, a capture is in the history at once (thumbnail written) and the item is hidden; turning the switch on saves the open photo | F | U CameraViewModelTest, CameraSettingsViewModelTest, PhotoStoreTest; M | 2026-10-26 (API 37) |
| CAM36 | The round button right of the shutter shows the latest history photo, the history icon while the history is empty and with the e-ink theme; it opens the history page | F | U CameraViewModelTest; M | 2026-10-26 (API 37; e-ink not checked) |
| CAM37 | History page: a grid newest saved first, each tile with its date (time for today) and the flag of its recognition language; a tap opens the photo on the Camera tab with its stored text and no recognition; with manual saving and an unsaved open photo the same three-button question comes first, and Save keeps both photos | F | U PhotoHistoryViewModelTest, PhotoStoreTest; M | 2026-10-26 (API 37) |
| CAM38 | Long press selects, "N selected", taps add or remove, ✕ or Back clears; the trash asks "N photos will be deleted from history" → their folders are gone; the photo open on the Camera tab among them is deleted and the tab shows the live camera | F | U PhotoHistoryViewModelTest, PhotoStoreTest, CameraViewModelTest; M | 2026-10-26 (API 37) |
| CAM39 | Camera settings from ⋮ and from Settings (the row before Appearance): Save automatically, Photo limit and Size limit, MB (digits only, 0 = none), the usage "N photos · size" or "No photos in history"; lowering a limit below the history asks "The N oldest photos…" — Cancel restores the field, Delete trims, the open photo too when it is among them; a limit typed and left with Back (keyboard open or hidden) or the arrow is stored, and one that would delete photos asks first: Delete trims and closes the page, Cancel shows the stored limit again and the next Back leaves; a limit typed and left by tapping the current tab is stored (one that would delete photos is dropped), and by switching tabs asks on return | F | U CameraSettingsViewModelTest, HistoryLimitsTest, PhotoStoreTest; I LimitFieldTest (API 30); M | 2026-10-27 (API 37; leaving with a typed limit, tab taps, Delete and Cancel), 2026-10-26 (API 37) |
| CAM40 | Clear history → "All photos in history will be deleted." → the history and its folders are empty; the photo open on the Camera tab, being in the history, is deleted and the tab shows the live camera; an open photo never saved (manual saving) stays | F | U PhotoStoreTest, CameraViewModelTest, CameraSettingsViewModelTest; M | 2026-10-26 (API 37; the unsaved case by unit test only) |
| CAM41 | Room v2 → v3 keeps the photos (earlier history ordered by id, the current photo outside it); thumbnails are upright 320 px JPEGs | F | I PhotoDatabaseTest | 2026-10-26 (API 37) |
| CAM42 | The round import button left of the shutter, ⋮ Import from gallery over a photo and over the live camera, and the button on the camera-access page open the system photo picker for one image; closing the picker changes nothing and leaves no staged file; the buttons are off while the page is busy | F | M | 2026-10-26 (API 30/37) |
| CAM43 | A picked image opens as the current photo and is recognized at once in the chip's language; with Save automatically it is in the history at once; its history tile shows the import time; an EXIF-rotated JPEG shows upright | F | U CameraViewModelTest; M | 2026-10-26 (API 30/37) |
| CAM44 | An unsaved current photo with manual saving → the question comes after the pick: Cancel keeps the photo and drops the copy, Delete opens the import, Save to history keeps both; a failed save keeps the photo | F | U CameraViewModelTest; M | 2026-10-26 (API 30/37) |
| CAM45 | Share → Screenlate for one image from another app: the app comes to the front on the Camera tab from any tab and imports it as in CAM43–CAM44; during the first run the tab stays and the image opens when the Camera tab is first opened; without a read grant → "The image could not be opened." and the photo stays; a link to the app's own provider (also with a user prefix, `content://0@<app>.anki.media/…`) or a `file:` path → nothing opens, no crash; the same links sent to the main screen as an `open=shared-image` request with clip data → nothing opens | F | U CameraViewModelTest (the queue), ShareImageActivityTest; M | 2026-10-27 (API 37: the prefix and the main screen; the rest 2026-10-26, API 30/37) |
| CAM46 | Formats: JPEG, PNG, WebP and HEIF are stored as they came (same bytes); GIF, BMP and others become a PNG of the upright pixels, scaled down past 24 MP; a file the phone cannot decode → "The image could not be opened." with no file left | F | U PhotoImportsTest; I PhotoImportDeviceTest; M | 2026-10-26 (API 30/37) |
| CAM47 | A recognized photo with no text → the bubble over it says "No text found in the photo." | F | M | 2026-10-27 (API 37) |
| CAM48 | A photo in a language other than the active one: the grey ➕ → "Open Anki settings" opens the Anki settings of the photo's language, the installation link of a photo whose dictionaries are missing opens its dictionaries; on the Camera page, the "Can't recognize <language>" dialog opens the dictionaries of that language; another such request while that page is open replaces it with the new language | F | M | 2026-10-27 (API 37; the popup's dictionaries link through the app intent; the Camera dialog with a dictionary folder moved aside) |
| CAM49 | Whole photo of an imported long image (a side over 16383 px) → the note's picture is scaled to 16383 px, not an empty file | N | U WebpSizeTest; I WebpLimitTest | 2026-10-27 (API 37, instrumented only) |
| CAM50 | An image over 100 MB from the picker or the share sheet → "The image is larger than 100 MB."; the current photo stays, no copy is left | N | U PhotoImportsTest, CameraViewModelTest; M | 2026-10-27 (API 37, the picker) |
| CAM51 | The Camera tab's language turned off in the language settings → the chip shows the active language; turned on again → the Camera tab's own language returns; a language chosen on the chip meanwhile stays | N | U CameraPreferencesTest; M | 2026-10-27 (API 37) |
| CAM52 | Add to Anki with Whole photo of a photo over 4 MP → the note's picture is about 4 MP and upright; a frame under 4 MP keeps the original pixels | N | U FittedSizeTest; I PhotoImageTest; M | 2026-10-27 (API 37, Whole photo) |
| CAM53 | The bubble resting on a word of a photo, the photo panned or zoomed → the popup follows the word; the word out of the visible part → the popup stays where it was | N | M | 2026-10-27 (API 37, pans injected) |
| CAM54 | A note made over a photo fills `{document-title}` with "Camera" in the app's language | N | M | 2026-10-27 (API 37, English) |
| CAM55 | High accuracy on: a photo over 1500 px with lettering taller than 200 px across a tile seam → the lettering reads whole, small text comes from the tiles; the region count and the missing-region map leave the extra pass out; its failure alone gives no warning | N | U PhotoAssemblyTest, PhotoTilesTest, PhotoRecognitionJobsTest, CameraPhotoControlsTest; I PhotoRegionsTest; M | 2026-10-27 (API 37; the failure by unit test only) |
| CAM56 | High accuracy off (the default): a photo over 1500 px is read in one request (the log names one tile), the status has no region count and the bar runs without a share; with Both and a network the device does not read it, with the cloud failing the device's text comes | N | U PhotoTilesTest, PhotoRecognitionJobsTest, CompositeOcrTest, CameraPhotoControlsTest; M | 2026-10-28 (API 37) |
| CAM57 | Camera settings start with Recognition: High accuracy with its hint, Engines with the current choice and a chevron, opening the bubble settings at Screen recognition; Back returns to Camera settings; Settings → Camera reads "Accuracy, photo history, automatic saving, limits"; turning High accuracy on tints the strip of a large photo read whole ("Settings changed"), off leaves a photo read by tiles as it is | N | U CameraPhotoControlsTest; M | 2026-10-28 (API 37) |
| CAM58 | ⋮ over a photo over 1500 px read whole: Recognize more accurately comes first; it reads the photo by tiles once (region count in the status) and then leaves the menu; it is not shown for a photo read by tiles, a photo up to 1500 px or while the photo is recognized | N | U CameraPhotoControlsTest; M | 2026-10-28 (API 37; a photo up to 1500 px by unit test only) |



### NAV: bottom navigation

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| NAV1 | Home, Search, Camera, Settings left to right; query and Settings subpage survive tab changes; reselect returns to tab root; Back from root returns Home | F | M | 2026-10-26 (API 37) |
| NAV2 | Overlay's Anki/settings request opens its page in the Settings tab; text-selection menu still resolves and opens standalone Search | F | I ProcessTextTest, M | 2026-10-26 (API 37; Anki request) |
| NAV3 | First-launch language/setup and debug image viewer hide the bar; finishing setup reaches Home; background-tip badge behaves across tab changes | F | M | 2026-10-26 (API 30/37) |
| NAV4 | The Search tab with a blank query opens without the keyboard, the bar stays usable; tapping the field opens it | F | M | 2026-10-26 (API 37) |
| NAV5 | During the first run, a page requested from outside (the selection-menu search's link to Dictionaries, the overlay's Anki or settings request) opens over it without the bar; Back returns to the first-run question | F | I FirstRunRequestTest; M | 2026-10-27 (API 37) |

### NF: non-functional

Baselines are measured on `Pixel_10_Pro` (API 37) with the host idle; a value more than 1.5 times the last run's on
the same AVD is a finding. The first run of this model records them in the run log.

| ID | Check | Cat | Cov | Checked |
|---|---|---|---|---|
| NF1 | Scan timings from the log: capture, band draft, full draft, cloud, final (earlier: band 1.6–2.4 s, full 5 s on the emulator) | E | M | 2026-10-27 |
| NF2 | Lookup to rendered entry (`popup-eval.mjs`; earlier 30–45 ms, する 100–180 ms) | F | M | 2026-10-14 |
| NF3 | Dragging the bubble: janky frames and the median frame time against the last run's (`dumpsys gfxinfo` after `reset`, over text and over an empty area), the entry keeps up with the aim | E | M | 2026-10-27 |
| NF4 | Pulling the bubble out has no move animation (`android.anim.lf` trace) on API 30 and API 37 | E | U BubbleWindowMoverTest, M | 2026-10-18 (API 37), 2026-10-22 (API 30) |
| NF5 | Screen transitions without a white flash or a visible previous screen (screen recording, dark theme) | F | M | 2026-10-14 |
| NF6 | Cold start: the splash until the settings are read, then content, no empty frame; `am start -W` TotalTime | F | M | 2026-10-14 |
| NF7 | Settings pages open with the current values, no defaults shown first; the Dictionaries page's first opening in a process shows a progress indicator in place of the lists, not empty sections (screen recording after a restart) | F | M | 2026-10-14 |
| NF8 | Memory: PSS docked, after a scan, and after 20 scans back near the docked value (no growth) | F | M | 2026-10-14 |
| NF9 | Sizes: APK per ABI against the last release; installed size (app, data) after a fresh install with Japanese | F | M | 2026-10-14, 2026-10-15 (APK) |
| NF10 | Long texts wrap and screens scroll in all 14 locales (download screen, Languages, settings hints; spot checks in de, ru, vi, ja) | F | M | 2026-10-14 |
| NF11 | Import times: JMdict from a file, the Japanese defaults on first run (download and import) | O | M | 2026-10-14 (bundled) |
| NF12 | Search updates while typing without visible lag | O | M | 2026-10-14 |
| NF13 | PaddleOCR memory: 10 s after the last recognition "Freed the memory of large inputs: … MB" (v6 on `ja-page-light.png`: ~360 → ~35 MB; native heap in `dumpsys meminfo` back near its value before the scan); scans less than 10 s apart do not trim between them; the next scan after a trim reads the same paragraphs | F | U IdleActionTest, M | 2026-10-19 |

## Not on the emulator

Checked by the owner on the phone or not at all; listed so that nothing is forgotten.

- The system stopping the service and "Bring the bubble back"; manufacturer startup settings; battery behavior while
  docked.
- E-ink device detection and the one-time offer; real vibration; mobile-data prompts with a real SIM.
- Restricted settings for an app installed from a file; the in-app update from GitHub (release builds).
- Recognition quality on the owner's apps (manga readers, X, games) and cloud timings on a mobile network.
- A network voice of the system text-to-speech (the emulator's Google TTS offers local voices only).

## Run log

Newest first: date, build, scope, results, cases moved from N, findings.

### 2026-10-28, `languages/az` (uncommitted build on `08463e01`), Pixel_10_Pro (API 37)

Scope: the first checks of Azerbaijani (LNG35, DIC45, OCR51, LK81, POP49, AUD36, ANK43) through the download screen,
the Dictionaries screen, the overlay over `testdata/ocr/az-sample.png`, `az-anki.png` and `az-misread.png` (Segoe UI,
in the debug viewer; the last prints e and i where ə and ı belong: "Seher gelirem." and "Qizlar evdedir.") with ML Kit
alone, cloud recognition alone and both, the Search screen, the Popup, Audio, Appearance and Anki settings and Anki
notes into the local "Screenlate Test" deck ({sentence} as Meaning); the engine's instrumented tests. An x86_64-only
APK.

Results: passed after three fixes (below). Automated: unit tests (1,868, five skipped), lint, the page tests (146), the
glue and mutation exception tests (130, one skipped), the catalog generator tests (11) and the Python tests of the
flag (5), frequency (19) and OCR model (9) scripts passed; `LanguageLookupTest` 41 of 41 and the engine's 67
instrumented tests passed on the emulator, the test package uninstalled after.

- LNG35: Azerbaijan's flag on the Languages screen; with English as the language of definitions Wiktionary (az–en,
  7.7 MB) is the fixed main dictionary, the word frequency (Leipzig news, 5.6 MB) and the transcription (184 kB)
  ticked; 13 MB to download, about 42 MB after import; ML Kit the main model, PaddleOCR v6 marked "More accurate",
  ticked and "Installed" (from the other Latin languages), Tesseract fast (3.5 MB) and accurate (6.3 MB) unticked, no
  PaddleOCR v5. The bubble shows Ə.
- DIC45: with Russian chosen and az–en unticked the Word forms card lists "Word forms (Wiktionary)", 7.7 MB, fixed;
  14 MB to download, about 34 MB after import. The forms task took 16.7 s (the 7,522 KB archive downloaded in 9.9 s,
  imported in 4.7 s): 967,415 forms with 1,128,934 lemma links, no definitions; az–ru took
  5.9 s (3,843 forms of its own); the word frequency downloaded 5,470 KB in 5.7 s and built 46,950 words in 3.4 s;
  the transcription took 4.4 s.
- OCR51: copied with the bubble menu's "Copy all recognized text": cloud recognition read every line of
  `az-sample.png` exactly (ə, ı, ğ, ç, ş, 2020-ci, ABŞ-da, «…»); ML Kit read every line exactly except masasına
  (masasIna), « (read <) and məktəbdəydi (maktəbdəydi).
- LK81: kitablarımızda finds kitab through the row "locative first person plural plural possessed possessive";
  gəlirəm gəlmək ("first person singular present indicative"); çörəyi çörək ("accusative singular definite");
  məktəbdəydi məktəb with the chips locative and past copula; olur olmaq ("third person singular present
  indicative"); yazı shows yazı, then yaz through the dictionary's row "accusative singular"; belə shows belə «так»
  and not bel, then bələmək (the e read as ə, after the word as read); the aim on da of ABŞ-da finds -da (a suffix
  entry, «аффикс местного падежа»), the highlight from the hyphen, then da; ci of 2020-ci finds nothing, as neither
  az–ru nor az–en has -ci. ML Kit's masasIna finds nothing, as az–ru has no masa (az–en has it, and the forms list
  masasına), and its maktəbdəydi nothing either (a is not tried for ə). Over `az-misread.png`, with ML Kit alone and
  with cloud recognition alone: Seher finds səhər, Qizlar qız ("nominative plural"); with both, gelirem finds
  gəlmək and evdedir evdə (3rd person) and ev (locative singular, 3rd person). In Search, seher and qizlar find nothing.
- POP49: the font card lists the phone's font, Lexend (downloaded before), Literata (1.0 MB) and Inter (0.9 MB), no
  PT or Atkinson, with "Only for Azerbaijani text" and the Azerbaijani sample sentence. In Russian, təkrarlamış (a
  verb without form rows) finds təkrarlamaq with «эвиденциальное прошедшее» and məktəbdəydi məktəb with «местный
  падеж» and «связка прошедшего времени»; the interface language restored to System.
- AUD36: sources Lingua Libre, Wiktionary, Text-to-speech; the Test of su found Wiktionary's recording (Az-su.ogg),
  Lingua Libre "No audio", the voice available; kitab found two Lingua Libre recordings, Wiktionary "No audio".
- ANK43 (deck Screenlate Test, engines Both): armud "Bazardan alma, armud və s. aldıq."; yaşamaq (yaşayır, a row of
  az–ru) "Prof. Əliyev Bakı şəh. Nizami küç. 5-də yaşayır."; dəftər (dəftərlər, "nominative plural") "Kitablar,
  dəftərlər və s."
- Fixed: `LanguageRulesIsolationTest` expected "Plan B. dann" to hold in no language; Azerbaijani's "və b." holds
  before a lowercase word, so the test names Azerbaijani. `FontCatalogFileTest` expected Atkinson Hyperlegible Next
  and the PT fonts for every Latin language; they lack ə and are listed as missing for Azerbaijani. şəh. and küç.
  held only before a lowercase word, so "şəh. Nizami" ended a sentence; they hold before any word now, as in
  addresses the street's name or the number follows.
- Seen, not caused by the change: Lingua Libre once answered a test with an error (cirrussearch-regex-too-busy-error,
  shown under "Needs attention" on Home); the next tests worked. Home showed the service as stopped by the system
  after the reinstall while the bubble worked. As before, the overlay kept its scan when the debug viewer opened
  another image in place, until the bubble was tapped. ServiceRequestsTest failed once on a network timeout; the
  rerun passed.

Left on the emulator: the `languages/az` debug app with Azerbaijani set up (az–ru, the forms, the word frequency and
the transcription; its Anki settings on the "Screenlate Test" deck and note type, Meaning {sentence}), the other
languages as before, engines Both, the interface language System, its service enabled; the Azerbaijani notes (armud,
yaşamaq, dəftər) in the local deck; the emulator shut down and the lock released.

### 2026-10-28, `languages/et` (uncommitted build on `c00255b6`), Pixel_10_Pro (API 37)

Scope: the first checks of Estonian (LNG34, DIC44, OCR50, LK80, POP48, AUD35, ANK42) through the download screen, the
Dictionaries screen, the overlay over `testdata/ocr/et-sample.png`, `et-anki.png` and `et-misread.png` (Segoe UI, in
the debug viewer; the last prints ö where õ belongs: "Möte ja vöi." and "Öhtul on öö.") with ML Kit alone and cloud
recognition alone, the Search screen, the Popup, Audio, Appearance and Anki settings and Anki notes into the local
"Screenlate Test" deck ({sentence} as Meaning); the engine's instrumented tests. An x86_64-only APK.

Results: passed after one test fix (below). Automated: unit tests (1,857, five skipped), lint, the page tests (146), the
glue and mutation exception tests (125, one skipped), the catalog generator tests (11) and the Python tests of the
flag (5), frequency (18) and OCR model (9) scripts passed; `LanguageLookupTest` 40 of 40 and the engine's 66
instrumented tests passed on the emulator, the test package uninstalled after.

- LNG34: Estonia's flag on the Languages screen; with English as the language of definitions Wiktionary (et–en,
  2.4 MB) is the fixed main dictionary, the word frequency (Leipzig news, 20 MB to download) and the transcription
  (96 kB) ticked; 23 MB to download, about 69 MB after import; ML Kit the main model, PaddleOCR v6 marked "More
  accurate" and ticked, Tesseract fast (4.5 MB) and accurate (16 MB) unticked, PaddleOCR v5 "Installed" (from the
  other Latin languages). The bubble shows Õ.
- DIC44: with Russian chosen and et–en unticked the Word forms card lists "Word forms (Wiktionary)", 2.4 MB, fixed;
  about 67 MB after import. The forms task took 6.2 s (the 2,321 KB archive downloaded in 3.0 s, imported in 1.0 s):
  233,849 forms with 259,673 lemma links, a 2,555 KiB table, no definitions; et–ru took 5.1 s (214 forms of its own),
  the transcription 1.7 s. The word frequency downloaded 20,002 KB in 15.4 s and built 100,000 words (1,007 KB) in
  5.0 s, 12 MB installed. majas finds maja «дом» ("singular inessive"). Downloaded later from the Dictionaries
  screen, the whole et–en took 5.6 s (import 1.4 s); the forms item stays installed beside it, with no doubled
  results.
- OCR50: copied with the bubble menu's "Copy all recognized text": ML Kit read every line of `et-sample.png`
  exactly (õ, ä, ö, ü, NATO-ga, 5-ga, linnavalitsus) except „Või nii,“, read `Või ni,"`; cloud recognition read every
  line exactly except the closing “, read ".
- LK80: pole finds pole, then olema with the chip negative; polnud polnud, then olema; majas maja; the aim on ga of
  NATO-ga and 5-ga finds -ga (a suffix entry), the highlight from the hyphen, with both engines; linnavalitsus
  "Compound: linna + valitsus". NATO and öösel have no entries in these dictionaries. Over `et-misread.png`, cloud
  recognition: Möte finds mõte, vöi või, Öhtul õhtu ("singular adessive"), öö only öö; ML Kit: Möte, Öhtul and öö
  the same. In Search, möte finds nothing.
- POP48: the font card lists every Latin font with "Only for Estonian text" and the Estonian sample sentence. In
  Russian the chips and form rows of olla, käia, olevat and polnud read «отрицание», «инфинитив на -da» and
  «косвенное наклонение»; the interface language restored to System.
- AUD35: sources Lingua Libre, Wiktionary, Text-to-speech; the Test of vesi found Lingua Libre's recording, Wiktionary
  "No audio", the voice available.
- ANK42 (deck Screenlate Test, ML Kit): õun (õunu, "partitive plural") "2020. a. lõpus ostsime u. 5 kg õunu, pirne
  jne."; ehitama (ehitati, "past indicative impersonal") "Maja ehitati 19. sajandil."
- Fixed: CatalogFileTest listed Estonian among the languages with a wordfreq list; Estonian's frequency comes from
  Leipzig's news, as the test's corpus list says.
- Seen, not caused by the change: the system stopped the accessibility service several times, most often after text
  was pasted into the lookup field with the keyboard up, and bound it again within about a second; while the bubble
  is pulled out, the overlay keeps its scan when the screen changes in place (the debug viewer opening another
  image). Under load, AccessibilityAgreedTest and ResumableDownloadTest failed once each; reruns passed.

Left on the emulator: the `languages/et` debug app with Estonian set up (et–ru, the forms, et–en, the word frequency
and the transcription; its Anki settings on the "Screenlate Test" deck and note type, Meaning {sentence}), the other
languages as before, engines Both, the interface language System, its service enabled; the Estonian notes (õun,
ehitama) in the local deck; the emulator shut down and the lock released.

### 2026-10-28, `languages/hu` (uncommitted build on `8f99513a`), Pixel_10_Pro (API 37)

Scope: the first checks of Hungarian (LNG33, DIC43, OCR49, LK79, POP47, AUD34, ANK41) through the download screen, the
Dictionaries screen, the overlay over `testdata/ocr/hu-sample.png` and `hu-anki.png` (Segoe UI, in the debug viewer)
with ML Kit alone and cloud recognition alone, the Search screen, the Popup, Audio, Appearance and Anki settings and
Anki notes into the local "Screenlate Test" deck ({sentence} as Meaning); the engine's instrumented tests. An
x86_64-only APK.

Results: passed after two fixes (below). Automated: unit tests (1846, five skipped), lint, the page tests (146), the
glue and mutation exception tests (121, one skipped), the catalog generator tests (11) and the Python tests of the
flag, frequency and OCR model scripts passed; `LanguageLookupTest` 39 of 39 and the engine's 65 instrumented tests
passed on the emulator, the test package uninstalled after.

- LNG33: Hungary's flag on the Languages screen; with English as the language of definitions Wiktionary (hu–en, 13 MB)
  is the fixed main dictionary, the word frequency (228 kB) and the transcription (876 kB) ticked; 14 MB to download,
  about 48 MB after import; ML Kit the main model, Tesseract fast (5.3 MB) and accurate (12 MB) unticked, PaddleOCR v6
  and v5 "Installed" (from the other Latin languages) without the "More accurate" mark. The bubble shows Ő.
- DIC43: with Russian chosen and hu–en unticked the Word forms card lists "Word forms (Wiktionary)", 13 MB, fixed;
  15 MB to download, about 29 MB after import. The forms task took 16.2 s (the 12,824 KB archive downloaded in 10.0 s,
  imported in 4.5 s): 1,281,301 forms with 1,315,769 lemma links, a 14,493 KiB table, no definitions; hu–ru took 0.4 s
  (380 forms of its own). The word frequency (46,638 words, 520 KB) was built in 2.2 s. házban finds ház «дом»
  ("singular inessive", W 408, [haːz]). Downloaded later from the Dictionaries screen, the whole hu–en took 4.3 s to
  download and 5.8 s to import. The catalog's installed size of the forms (18 MiB) is a quarter above the table,
  as Finnish's is.
- OCR49: copied with the bubble menu's "Copy all recognized text": cloud recognition read every line of
  `hu-sample.png` exactly (fűzfa, őt, hűvös, őszi, éjjel, 2023-ban, EU-ban, iskolaépület); ML Kit read both ű as ú
  (fúzfa, húvös), the ő of őszi as Ő and éjjel as éjel, the rest exactly.
- LK79: overlay (ML Kit): Megtartottam finds tart ("verb prefix « first person singular definite/indefinite past
  indicative", before the conjugation fix below), the highlight over the whole word; Elolvastam olvas (verb prefix), the highlight from the l (ML Kit's
  box; over the whole word with cloud recognition); Öt öt, then őt; nőtt nő ("past participle"); the aim on ban of
  2023-ban finds -ban (a suffix entry), the highlight from the hyphen; on EU-ban EU ("singular inessive"), then -ban,
  the highlight over EU-ban; fűzfa (read fúzfa) and hűvös (read húvös) find fűzfa and hűvös after the fix below, and
  with cloud recognition too. Search: megtartottam tart; megtart, bevétel and idegen their own entries; bevételt
  bevétel ("accusative singular"); iskolaépület "Compound: iskola + épület"; füzfa "No dictionary entries found."; öt
  only öt; -ban the suffix entry.
- POP47: the font card lists the phone's font, Lexend and PT Serif (downloaded), Literata, Inter, Atkinson Hyperlegible
  Next and PT Sans; "Only for Hungarian text"; the sample "Öt hűvös őszi éjjel az árvíztűrő tükörfúrógép zúgott." In
  Russian the chips read «глагольная приставка», házig ház «единственное число терминатив», megtartottam tart
  «1-е лицо единственное число определённое спряжение/неопределённое спряжение прошедшее время изъявительное
  наклонение» after the fix below; the interface language restored to System.
- AUD34: sources Wiktionary, Lingua Libre, Text-to-speech; the Test of víz found Wiktionary's recording (Hu-víz.ogg),
  Lingua Libre "No audio".
- ANK41 (deck Screenlate Test, ML Kit): alma (almát, "accusative singular") "2023. május 3-án gyümölcsöt vettem, pl.
  almát és körtét stb."; épül (épült, "past participle") "A ház a 19. sz. végén épült."
- Fixed: ML Kit reads ű as ú, which the ö and ü variants did not cover, so fűzfa and hűvös found nothing; the
  OCR-only variant now tries ű for ú too (provisional, the plan's question 15).
- Fixed: wty-hu-en names the definite conjugation both `definite` and `object-definite`; the plain one showed as
  Russian «определённая форма», the noun form of other languages. Hungarian names it as the conjugation
  (`formTagAliases`).
- Seen, not caused by the change: after the service was turned off and on through the secure settings within a
  second, Home said "The bubble is not shown: the system stopped the service" while the bubble worked; right after the
  interface language went from Russian back to System, the language chips of the Anki settings stayed Russian until
  the screen was opened again. Under load, ServiceRequestsTest timed out twice, AudioFinderTest once, and the overlay
  module's test executor crashed once; reruns passed.

Left on the emulator: the `languages/hu` debug app with Hungarian set up (hu–ru, the forms, hu–en, the word frequency
and the transcription; its Anki settings on the "Screenlate Test" deck and note type, Meaning {sentence}), the other
languages as before, engines Device (ML Kit), the interface language System, its service enabled; the Hungarian notes
(alma, épül) in the local deck; the emulator shut down and the lock released.

### 2026-10-28, `languages/fi` (uncommitted build on `0a38e341`), Pixel_10_Pro (API 37)

Scope: the first checks of Finnish (LNG32, DIC42, OCR48, LK78, POP46, AUD33, ANK40) through the download screen, the
Dictionaries screen, the overlay over `testdata/ocr/fi-sample.png` and `fi-anki.png` (Segoe UI, in the debug viewer)
with ML Kit alone and cloud recognition alone, the Search screen, the Popup, Audio, Appearance and Anki settings and
Anki notes into the local "Screenlate Test" deck ({sentence} as Meaning); the engine's instrumented tests. An
x86_64-only APK.

Results: passed after one fix (the form tags below). Automated: unit tests (1834, five skipped), lint, the page tests
(146), the glue and mutation exception tests (116, one skipped), the catalog generator tests (11) and the Python tests
of the flag, frequency and OCR model scripts passed; `LanguageLookupTest` 38 of 38, the engine's 64 instrumented tests
and `DictionaryRepositoryTest` 17 of 17 passed on the emulator, the test package uninstalled after.

- LNG32: Finland's flag on the Languages screen; with English as the language of definitions Wiktionary (fi–en, 43 MB)
  is the fixed main dictionary, the word frequency (4.2 MB) and the transcription (5.7 MB) ticked, no Word forms card;
  ML Kit the main model, Tesseract fast (7.9 MB) and accurate (14 MB) unticked and unmarked, PaddleOCR v6 and v5
  "Installed" (from the other Latin languages) without the "More accurate" mark; 53 MB to download, about 194 MB after
  import. "Another language…" lists Russian, French, Chinese, Italian, Japanese, Korean, Polish, Portuguese and
  Turkish. Home shows Finnish ready and the bubble Ä.
- DIC42: with Russian chosen and Wiktionary (fi–en) unticked the Word forms card lists "Word forms (Wiktionary)",
  43 MB, fixed; 54 MB to download, about 135 MB after import (fi–en ticked: no Word forms card, about 197 MB). The forms
  import read the 42,412 KB archive in 15.9 s: 4,386,686 forms with 4,623,281 lemma links, a 52,852 KiB table, no
  definitions; fi–ru took 1.3 s (8,459 forms of its own). The Dictionaries screen lists "wty-fi-en (forms)", and
  talossa finds talo ("singular inessive") with the Russian definition. Downloaded later from the Dictionaries screen,
  the whole fi–en took 10 s to download and 21.8 s to import.
- OCR48: copied with the bubble menu's "Copy all recognized text": cloud recognition read every line of
  `fi-sample.png` exactly (Talossanikin, EU:n, STT:lle, 1990-luvulla, 20-vuotias, kirjastonhoitaja,
  Kirjoitettiinkohan); ML Kit every line but riittävä, read as rittävä (below).
- LK78: overlay (ML Kit, then cloud recognition with fi–en installed): Talossanikin finds talo ("possessive « clitic «
  singular inessive"), the highlight over the whole word; Onkohan olla ("clitic « third person singular present
  indicative"); the aim on the n of EU:n finds EU through fi–en's row for EU:n ("genitive/accusative singular"), the
  highlight over EU:n; on the first l of STT:lle STT, the highlight over STT; on luvulla of 1990-luvulla luku
  ("singular adessive"), the highlight over luvulla; on vuotias of 20-vuotias -vuotias (fi–en), then vuotias (fi–ru),
  the highlight from the hyphen; on 20 nothing; kirjastonhoitaja its own entry; Kuusi kuusi (number, noun), then kuu
  through fi–ru's own row "redirected from kuusi"; kuusta kuusi ("partitive singular"), then kuu ("singular elative");
  vesi vesi; Kirjoitettiinkohan kirjoittaa ("clitic « clitic « past passive indicative"); talokin talo ("clitic").
  Search: taloon shows talo's illative row only; talossaan talo ("third person singular possessive of inessive", the
  row); rautatieasema its own entry; EU:n and -vuotias "No dictionary entries found." while only fi–ru and the forms
  were installed.
- POP46: the font card lists the phone's font, Lexend and PT Serif (downloaded), Literata, Inter, Atkinson
  Hyperlegible Next and PT Sans; "Only for Finnish text"; the sample "Vähäinen sää ja öinen myrsky veivät laivan
  Ålandin saaristoon." In Russian the chips read «притяжательный суффикс « клитика « единственное число инессив» and
  kirjoittama «агентивное причастие»; the interface language restored to System.
- AUD33: sources Wiktionary, Lingua Libre, Text-to-speech; the Test of vesi found Wiktionary's recording ("Found 1"),
  Lingua Libre "No audio".
- ANK40 (deck Screenlate Test): omena (omenoita, "plural", ML Kit) "Ostin 24. toukokuuta hedelmiä, esim. omenoita ja
  päärynöitä jne."; tuki (tuella, "singular adessive", cloud recognition) "Talo valmistui 1990-luvulla EU:n tuella."
- Fixed: the cases, moods and infinitives of Finnish form rows were in English in every other interface language
  (Russian «единственное число inessive»); `FormOfTags` now translates them in the 14 locales, with the local cases
  Hungarian and Estonian will need.
- Seen, ML Kit's: a doubled narrow letter lost (riittävä read as rittävä, tuella as tuela, which finds nothing); cloud
  recognition reads both. Not fixed here.
- Seen: Kuusi shows kuu after kuusi through fi–ru's own "redirected from kuusi" row, not through the possessive rule;
  the case now says so.

Left on the emulator: the `languages/fi` debug app with Finnish set up (fi–ru, the forms, fi–en, the word frequency
and the transcription; its Anki settings on the "Screenlate Test" deck and note type, Meaning {sentence}), the other
languages as before, engines Device (ML Kit), the interface language System, its service enabled; the Finnish notes
(omena, tuki) in the local deck; the emulator shut down and the lock released.

### 2026-10-28, `languages/gd` (uncommitted build on `d9e40413`), Pixel_10_Pro (API 37)

Scope: the first checks of Scottish Gaelic (LNG31, OCR47, LK77, POP45, AUD32, ANK39) through the download screen, the
overlay over `testdata/ocr/gd-sample.png` and `gd-anki.png` (Segoe UI, in the debug viewer) with both engines, cloud
recognition alone and ML Kit alone, app text in AnkiDroid's note editor, the Search screen, the Popup, Audio,
Appearance and Anki settings and Anki notes into the local "Screenlate Test" deck ({sentence} as Meaning); the
engine's instrumented tests. An x86_64-only APK.

Results: passed. Automated: unit tests (1816), lint, the page tests (146), the glue tests (104, one skipped),
the mutation exception tests (8), the catalog tests (10), the flag tests (5) and the OCR model tests (9) passed;
`LanguageLookupTest` 36 of 36 and the engine's 62 instrumented tests passed on the emulator, the test package
uninstalled after.

- LNG31: Scotland's flag on the Languages screen; Wiktionary into English and the transcription ticked, Russian alone
  under "Another language…", no word frequency; ML Kit the main model, PaddleOCR v6 and v5 ticked and marked "More
  accurate" ("Installed", from the other Latin languages), Tesseract fast (3.1 MB) and accurate (9.6 MB) unticked and
  unmarked; Russian added, 1.3 MB to download, 4.5 MB in all; then Home shows Scottish Gaelic ready and the bubble Ò.
- OCR47: copied with the bubble menu's "Copy all recognized text": cloud recognition and ML Kit both read every line
  of `gd-sample.png` exactly (h-Alba, Dh'fhàg, t-sràid mhór, t-uisge, ar n-eilean, th'ann, bàta mòr); ML Kit joined
  the first three lines into one paragraph.
- LK77: overlay (both engines): h-Alba finds Alba ("h-prothesis"), the highlight over the whole word; bhean finds bean
  ("lenition"); t-sràid mhór finds the phrase sràid mhòr (high street, "lenition") through the acute read as the
  grave, then sràid; mhór finds mór (gd-ru's own headword) and mòr ("lenition"); in Dh'fhàg the aim on fhàg finds fàg
  ("lenition"), on Dh the whole word (fàg, "past dependent personal"); Chan eil finds bi; t-uisge shows its own entry,
  then uisge ("t-prothesis"); n-eilean finds eilean ("eclipsis"); in th'ann the aim on ann finds ann; Bhathar shows
  bhathar and the gd-en rows that name it (thathar, nach, bi, is), no bathar. Search: chan shows abair and can, both
  through gd-en's own rows (future dependent; future negative dependent, cha chan), none through the rule; bith finds
  bìth, oran òran, mor mòr and mór; bata and cas only their own entries; hAlba "No dictionary entries found." App text
  (AnkiDroid's note editor opened by sharing "Dh’fhàg i na h-Alba le bith"): the aim on fhàg after Dh’ finds fàg,
  h-Alba Alba, bith bìth.
- POP45: the font card lists the phone's font, Lexend and PT Serif (downloaded), Literata, Inter, Atkinson
  Hyperlegible Next and PT Sans; "Only for Scottish Gaelic text"; the sample "Ruith an cù bàn sìos an rathad mòr gu
  Loch Èite." In Russian, Home names the language «Шотландский гэльский» and the chips read «лениция», «эклипсис»,
  «протеза t» and «протеза h»; the interface language restored to System.
- AUD32: sources Lingua Libre, Wiktionary, Text-to-speech; the Test of uisge found Lingua Libre's recording (speaker
  DaoineÈibhinn), Wiktionary "No audio".
- ANK39 (deck Screenlate Test, ML Kit): ubhal (ùbhlan, "plural") "Cheannaich i measan, m.e. ùbhlan is peuran msaa.",
  dhachaigh "Thàinig an Dr. Dòmhnallach dhachaigh."; the aim on Iain gave "Chunnaic mi an Urr. lain an-dè." (below).
- Seen, ML Kit's: it read the capital I of Iain as a lowercase l (lain), which the unmarked fallback took to làin, a
  form of làn; cloud recognition reads Iain. A look-alike of every Latin language, not fixed here.
- Seen: the emulator has no Gaelic voice; with autoplay on, a lookup with no recording shows "No text-to-speech voice
  for this language is installed."

Left on the emulator: the `languages/gd` debug app with Scottish Gaelic set up (its Anki settings on the "Screenlate
Test" deck and note type, Meaning {sentence}; ML Kit the main model), Irish and Welsh as before, engines Device (ML
Kit), its service enabled; the Gaelic notes (ubhal, làn, dhachaigh) in the local deck; the emulator shut down and the
lock released.

### 2026-10-28, `languages/ga` (uncommitted build on `e3776224`), Pixel_10_Pro (API 37)

Scope: the first checks of Irish (LNG30, OCR46, LK76, POP44, AUD31, ANK38) through the download screen, the overlay
over `testdata/ocr/ga-sample.png` and `ga-anki.png` (Segoe UI, in the debug viewer) with both engines, cloud
recognition alone and ML Kit alone, app text in AnkiDroid's note editor, the Search screen, the Popup, Audio,
Appearance, Text recognition and Anki settings and Anki notes into the local "Screenlate Test" deck ({sentence} as
Meaning); the engine's instrumented tests. An x86_64-only APK.

Results: passed. Automated: unit tests (1,807, five skipped), lint, the page tests (146), the glue tests (96, one
skipped), the mutation exception tests (7), the catalog tests (10), the flag tests (5) and the OCR model tests (9)
passed; `LanguageLookupTest` 35 of 35 and the engine's 61 instrumented tests passed on the emulator, the test package
uninstalled after.

- LNG30: Ireland's flag on the Languages screen; Wiktionary into English and the transcription ticked, Russian,
  French and Polish under "Another language…", no word frequency; ML Kit the main model, Tesseract fast (1.2 MB) and
  accurate (3.9 MB) unticked and unmarked, PaddleOCR v5 and v6 unmarked ("Installed", from Welsh); French added, 4.2 MB in
  all; then Home shows Irish ready and the bubble Á; Text recognition's model list shows Irish on ML Kit (built in).
- OCR46: copied with the bubble menu's "Copy all recognized text": cloud recognition read every line of
  `ga-sample.png` exactly (nGaillimh, D'fhág, t-uisce, hÉireann, tSín); ML Kit every line but hÉireann, read as
  "hEireann", which finds nothing in the Search screen.
- LK76: overlay (both engines): nGaillimh shows its own entry, then Gaillimh ("eclipsis"), the highlight over the
  whole word; bhean finds bean ("lenition"), Bhí bí ("lenition"), t-uisce its own entry and uisce ("t-prothesis"),
  tSín Sín ("lenition"); hÉireann finds Éire, the first trace a ga-en row ("genitive singular definite"), "h-prothesis"
  in most of the other twelve; chun shows chun first; in D'fhág the aim on fhág finds fág ("lenition"), on D the
  whole word (fág, "preterite"). Search: chun shows chun, then cun through ga-en's row; thar thar, then tar
  ("lenition", the row's lenited form); cheana cheana, then cion through a row, no ceana; chomh only chomh; fag
  "No dictionary entries found." App text (AnkiDroid's note editor opened by sharing "An ḃean i nGaillimh, d’fhág sí
  chomh luath."): ḃean finds bean ("lenition"), the aim on d’ the whole d’fhág (fág), chomh only chomh.
- POP44: the font card lists the phone's font, Lexend and PT Serif (downloaded), Literata, Inter, Atkinson
  Hyperlegible Next and PT Sans; "Only for Irish text"; the sample "Chuaigh bé mhórshách le dlúthspád fíorfhinn trí
  hata mo dhea-phorcáin bhig." In Russian the chips read "лениция" and "эклипсис"; the interface language restored to
  System.
- AUD31: sources Lingua Libre, Wiktionary, Text-to-speech; the Test of uisce found Lingua Libre's recording ("Found
  1, playing the first").
- ANK38 (deck Screenlate Test, ML Kit): piorra (piorraí, "nominative/dative plural definite") "Cheannaigh sí torthaí,
  m.sh. úlla agus piorraí srl.", leabhar "Féach lch. 5 den leabhar.", baile "D'fhág an Br. Seán an baile.". piorraí
  stood in for úlla, whose word úll had a note already from the first try with both engines (below).
- Seen, cloud recognition's: on `ga-anki.png` it gave "sí" at the end of the first line no separator, so úll's
  note read "Cheannaigh sítorthaí, m.sh. …" (the same with Welsh active); it split the paragraph after "Br." ending
  the fourth line and read "lch." as "Ich.". ML Kit gave the paragraph as written (`ocr-engines.md`).
- Seen, the dictionaries': Chuaigh shows cuach (a genitive singular definite row) before téigh (past indicative);
  Irish has no frequency dictionary to order them.
- Seen: the emulator has no Irish voice; with autoplay on, a lookup with no recording in Lingua Libre or Wiktionary
  shows "No text-to-speech voice for this language is installed." (fág, t-uisce, nGaillimh).

Left on the emulator: the `languages/ga` debug app with Irish set up (its Anki settings on the "Screenlate Test" deck
and note type, Meaning {sentence}; ML Kit the main model) and Welsh as before, engines Both, its service enabled; the
Irish notes (úll, piorra, leabhar, baile) in the local deck; the emulator shut down and the lock released.

### 2026-10-28, `languages/cy` (uncommitted build on `b883ed27`), Pixel_10_Pro (API 37)

Scope: the first checks of Welsh (LNG29, OCR45, LK75, POP43, AUD30, ANK37) through the download screen, the overlay
over `testdata/ocr/cy-sample.png` and `cy-anki.png` (Segoe UI, in the debug viewer) with cloud recognition, ML Kit and
Tesseract cym fast, app text in AnkiDroid's note editor, the Search screen, the Popup, Audio, Appearance, Text
recognition and Anki settings and Anki notes into the local "Screenlate Test" deck ({sentence} as Meaning); the
engine's instrumented tests. An x86_64-only APK.

Results: passed after one fix (below). Automated: unit tests (1,799), lint, the page tests (146), the glue tests (88, one
skipped), the mutation exception tests (6), the catalog tests (10), the flag tests (5) and the OCR model tests (9)
passed; `LanguageLookupTest` 34 of 34 and the engine's 60 instrumented tests passed on the emulator, the test package
uninstalled after.

- LNG29: Wales's flag on the Languages screen; Wiktionary into English and the transcription ticked, Russian and French
  under "Another language…", no word frequency; ML Kit the main model, Tesseract fast and accurate and PaddleOCR v5
  and v6 unticked, all four marked "More accurate" (in Text recognition's model list "Recommended"); the bubble shows
  Ŵ. Ticking Tesseract fast on the download screen made it the main model, as for other languages; ML Kit chosen again.
- OCR45: cloud recognition read every line of `cy-sample.png`, tŷ, tŵr and dŵr included; Tesseract fast read every
  line exactly too. ML Kit read tŷ, dŵr and tŵr as "tý", "dw" and "twr"; aimed at tý the popup finds tŷ through
  the OCR-only variant.
- LK75: overlay (ML Kit, cloud recognition) and the Search screen: Nghymru finds Cymru and nghath cath ("nasal
  mutation", the highlight over the whole word; cathod first, see below), fara bara ("soft mutation"), hysgol ysgol
  ("h-prothesis"), chath cath ("aspirate mutation"); mae shows bod's form and no bae with cy-fr; ty finds tŷ, twr
  only twr, ddwr dŵr; tý typed finds nothing; i’w finds both dictionaries' i'w and i’w; dŵr shows dŵr, then tŵr
  ("soft mutation"). App text (AnkiDroid's note editor, "Mae fy nghath yn y tý a ty Mabon"): tý finds nothing, ty
  finds tŷ.
- POP43: the font card lists the phone's font and Lexend (downloaded), Literata, Inter and Atkinson Hyperlegible Next;
  no PT Serif (downloaded for Swahili) or PT Sans; "Only for Welsh text"; with Lexend chosen the sample "Parciais fy
  jac codi baw hud llawn dŵr ger tŷ Mabon." shows ŵ and ŷ; the phone's font chosen again. In Russian the chips read
  "мягкая мутация", "носовая мутация", "придыхательная мутация" and "протеза h"; the interface language restored to
  System.
- AUD30: sources Wiktionary, Lingua Libre, Text-to-speech; the Test of dŵr found Wiktionary's Cy-dwr.ogg, Lingua
  Libre nothing; Google's engine spoke with its Welsh voice (cy-GB); holding 🔊 on dŵr lists Cy-dwr.ogg and the
  phone's voice.
- ANK37 (deck Screenlate Test, ML Kit): yno "Pregethodd y Parch. John Williams yno, h.y. yng Nghaerdydd.", afalau
  (afal, "plural") "Prynodd ffrwythau, e.e. afalau a gellyg.", Dafydd "Daeth Dafydd Jones, Ysw.", fawr (mawr) "Mae'r
  tý yn fawr." with ML Kit's tý. Williams has no entry in the Welsh dictionaries, so the case aims at yno.
- Fixed: chains of two mutations. fara showed para through bara's own "soft" row after the rule's soft mutation, and
  ddwr showed twr through dwr's row. The engine now drops chains with two mutation steps, before the unmarked
  fallback, so ddwr finds dŵr (`RuleOnlyResultsTest`, `LanguageLookupTest`).
- Seen, the dictionaries': nghath shows cathod first, from the French edition's row nghath → cathod ("nasalization");
  the English edition's rows give a few wrong mutated forms (dal → thal, nhal).
- Seen, not the app's: after uiautomator dumps the system stopped the accessibility service; "Bring the bubble back"
  on Home restored it.

Left on the emulator: the `languages/cy` debug app with Welsh set up (its Anki settings on the "Screenlate Test" deck
and note type, Meaning {sentence}; ML Kit the main model, Tesseract fast, PaddleOCR v5 and v6 installed), engines
Both, its service enabled; the Welsh notes in the local deck; the emulator shut down and the lock released.

### 2026-10-28, `languages/sw` (uncommitted build on `f7e55a7e`), Pixel_10_Pro (API 37)

Scope: the first checks of Swahili (LNG28, DIC41, OCR44, LK74, POP42, AUD29, ANK36) through the download screen, the
overlay over `testdata/ocr/sw-sample.png` and `sw-anki.png` (Segoe UI, in the debug viewer) with cloud recognition and
ML Kit, the Search screen, the Popup, Audio, Appearance and Anki settings and Anki notes into the local "Screenlate
Test" deck ({sentence} as Meaning); the engine's instrumented tests. An x86_64-only APK.

Results: passed, with the deviations below; AUD29's recordings not checked, ANK36's quoted sentence not as written.
Automated: unit tests (1,783), lint, the page tests (146), the glue tests (82), the catalog tests (10), the frequency,
flag and OCR model tests passed; `LanguageLookupTest` 33 of 33 and the engine's 59 instrumented tests passed on the
emulator, the test package uninstalled after.

- LNG28: Tanzania's flag on the Languages screen; English definitions by default (sw-en, 2.0 MB), "Another language…"
  Russian, Chinese and Polish; Word frequency (Leipzig Corpora Collection) ticked, 19 MB, its description naming
  Wikipedia articles; no transcription; ML Kit the main model, Tesseract fast (2.2 MB) and accurate (4.9 MB)
  unticked, PaddleOCR v5 and v6 without the "more accurate" mark (both showed "Installed", as earlier Latin-script
  languages had downloaded them). The download was 21 MB, about 62 MB after import; the bubble shows SW.
- DIC41: built on the device as "Leipzig Corpora Collection (sw)", sorting results; the frequency chip shows on
  Swahili entries (soma L 8847, sema L 6233, karibu L 124, hata L 63).
- OCR44: cloud recognition (engines Both) and ML Kit (engines Device) read the words of `sw-sample.png`; aimed at
  Ng’ombe both find Ng'ombe (a name) and ng'ombe. Both write “Karibu!” with straight quotes (see ANK36).
- LK74 (Search screen): alisoma finds soma ("past « he/she"); hatukusoma soma ("past « we « negative"); aliyemwona ona
  ("object « relative « past « he/she"); kinachoitwa itwa ("relative « present « class"); alikula shows kula first,
  then la ("object « past « he/she"); kupa shows only kupa; Angela finds nothing; ng'ombe shows ng'ombe and the name
  Ng'ombe; aliunga shows only the verb unga (join, subscribe, season). Overlay (cloud recognition, then ML Kit):
  Alisoma soma, aliyemwona ona, kinachoitwa itwa, alisema sema ("past « he/she"), Walinunua nunua ("past « they"),
  walirudi rudi; aimed at Angela no popup opens; wali shows wali (cooked rice), then mwali; Hata hivyo finds the
  phrase hata hivyo, then hata.
- POP42: the font card lists the phone's font, Lexend and PT Serif (downloaded), Literata, Inter, Atkinson
  Hyperlegible Next and PT Sans, with "Only for Swahili text" and a Swahili sample sentence. In Russian the prefix
  chips read "объект « относительная форма « прошедшее время « он/она", "прошедшее время « мы « отрицание" and
  "относительная форма « настоящее время « именной класс"; the interface language restored to System.
- AUD29: sources Wiktionary, Lingua Libre, Text-to-speech; the Test button of the phone's voice found it, and Google's
  engine spoke with its Swahili voice (sw-ke). The recordings were not checked: the Wiktionary source's Test answered
  "Failed: The server answered with an error (HTTP 503)", and Home showed "Needs attention: Audio sources that failed"
  for Swahili with Wiktionary and Lingua Libre (HTTP 503, once a timeout).
- ANK36 (deck Screenlate Test): Juma "Bw. Juma alifika tar. 5 Mei.", wali "Walinunua vyakula, k.m. wali na maharage
  n.k.", rudi and hata hivyo "Hata hivyo walirudi." with ML Kit. Both engines wrote “Karibu!” as "Karibu!", so karibu
  got '"Karibu!' with either, and sema '" alisema' with ML Kit (the sentence ended at the line, without Bi. Amina.)
  and '" alisema Bi. Amina.' with cloud recognition. Plan question 30 (straight quotes in OCR text) got Swahili's
  data; with curly quotes SentenceTest gives "“Karibu!” alisema Bi. Amina.".
- Seen, not the app's: after uiautomator dumps the system stopped and rebound the accessibility service; the bubble
  came back with Home's "Bring the bubble back".

Left on the emulator: the `languages/sw` debug app with Swahili set up (its Anki settings on the "Screenlate Test"
deck and note type, Meaning {sentence}), engines Both, its service enabled; the Swahili notes in the local deck; the
emulator shut down and the lock released.

### 2026-10-28 morning, `claude/port-fixes-to-main-3e7385` (0.2.3-0bf33bd9, the code of `d1f8ee9f`) and `claude/download-fixes-lang` (0.2.3-f35f90ec), Pixel_10_Pro (API 37)

Scope: the emulator checks of the fix round: DIC24 as an update, DIC29 and DIC31 on `main`, DIC29 on the language
branch. Side-by-side installs (init scripts in the scratchpad): `main` as `com.vpr.screenlate.debugmain`, the language
branch as `com.vpr.screenlate.debuglang`; both uninstalled afterwards, the `languages/main` debug app not touched.

Results: every case passed.

- DIC24, the update: `main` 2df1e55c installed fresh, Wi-Fi and data off, Колобок 400k queued ("Waiting in the
  queue"), then `grammar.zip` (Dictionary of Japanese Grammar, 389 KB) picked with Import from file: its copy in
  `cache/dictionary-downloads/` queued behind the download; a file `stale-part.zip` planted next to it. 0.2.3-0bf33bd9
  installed over it, the network back: the download's run logged "1 import leftovers, kept 2 queued archives" (the
  planted file gone, the copy kept), Колобок installed, then the file import installed 日本語文法辞典(全集) from the copy.
  After `am kill` in the background and a restart, the bundled install's cleanup removed the empty folder.
- DIC24, Clear cache: Wiktionary (ja–ja) waiting for a network with 12 MB in `no_backup/dictionary-downloads/`; App
  info → Storage & cache → Clear cache emptied `cache/` and kept the part, the process kept running; with the network
  back: "12068 KB already there", installed.
- DIC29 on `main`: Wi-Fi and data off during Wiktionary (ja–ja), four times in a row: each run "stopped by WorkManager
  (reason 7): waits for a network, then runs again", the task card "Waiting for a network: Wiktionary (ja–ja)", the
  catalog card "Waiting for a network", no retry, no "download stopped" card. Each next run started as soon as the
  network was back, run 5 too (with the exponential backoff the downloads had, 30 s doubled per run, not before 4
  minutes after the enqueue, 40 s later); it continued from the 12 MB and installed.
- DIC31 on `main`: JMdict (French) and JMdict (German) queued together with `http_proxy` set to a closed port
  (`10.0.2.2:8898`, refused at once): each broke off (ConnectException) and waited 30 s, 1 and 2 minutes in its one
  run ("retry n of 3 in … s"), meanwhile the task card "Retrying the download (n of 3): JMdict (French)" and the
  catalog card "Waiting in the queue", then "download stopped — connection lost" with Cancel and Resume; German ran
  after French. Proxy removed, JMdict (Russian) and Wiktionary (ja–ru) started together: WorkManager's database lists
  the stopped German task as the Russian task's prerequisite; each downloaded its own archive and installed. Resume on
  the German card (the French one listed above it) downloaded `JMdict_german.zip`, then Resume on French
  `JMdict_french.zip`; both installed.
- DIC29 on the language branch: first run, Japanese, the suggested downloads (53 MB); Wi-Fi and data off 2 s into
  Jitendex: Jitendex (chain 1) and Kanjium Pitch Accents (chain 0) "stopped by WorkManager (reason 7)", 6.9 MB kept;
  the shared card "Waiting (4): Jitendex, Kanjium Pitch Accents, Transcription (Wiktionary), KANJIDIC" with "Waiting
  for a network" under its title, Jitendex's catalog card "Waiting for a network"; no retry. With the network back
  after 50 s both runs started at once, Jitendex "6940 KB already there", every task installed.
- The emulator's network throttle did not help to cut a download midway: at `adb emu network speed 800` a queued
  download did not start, at 2000 no byte came in 10 s, at 3000 a 16 MB file still came in 5 s. Switching the network
  off on the device right after the app's "Downloading" line did (`notes/build-environment.md`).

Left on the emulator: as before (the `languages/main` debug app, its service enabled, the proxy settings deleted, Wi-Fi
and data on, the network speed full, `grammar.zip` removed from Downloads); the emulator shut down and the lock
released after 28 minutes.

### 2026-10-28 night, `claude/port-fixes-to-main-3e7385` (`d1f8ee9f`) and `claude/download-fixes-lang` (from `9a401674`)

Scope: the fix round after the review of the port: the old cache folder keeps the files a queued import reads, a
download whose network went keeps waiting in its run, `main`'s retries wait in the run, the least linear backoff.
Automated only: `main` unit tests 629, page tests 107, lint; `languages/main` unit tests 1750, lint. DIC24, DIC29 and
DIC31 not run on the emulator: group 5a held the lock from 03:01 past 04:49 (run in the morning, the entry above).

### 2026-10-28, `claude/port-fixes-to-main-3e7385` (`main` 2df1e55c with the fixes ported from `languages/main`, 0.2.3-7c4d2b30, side-by-side install), Pixel_10_Pro (API 37)

Scope: the download fixes ported to `main` (the own input merger, the wait for a network, partial downloads in
`no_backup`) and a smoke test of the other ports (the app opened with the launcher's intent, the hold menu without
Open Screenlate over the app, the popup hidden until its new view is drawn). Cases DIC23, DIC24, DIC29, DIC31, BUB16
(the hold menu path), BUB17, POP39. The x86_64-only debug APK was built with an init script (scratchpad only) that set
the application ID to `com.vpr.screenlate.debugmain`, so it installed next to the `languages/main` debug app without
touching its data; uninstalled afterwards. Automated: unit tests 622, page tests 107, lint passed (no issue in the
changed lines).

Results: every case passed.

- First launch: the bundled install (4 dictionaries) in 5.4 s.
- DIC23: Wi-Fi and data off, JMdict (Spanish) queued: the task card "Waiting for a network: JMdict (Spanish)", the
  catalog card "Waiting for a network"; with the network back it started at once and installed. `main` has no shared
  waiting card; a waiting retry and the file import not run.
- DIC29: Wi-Fi and data off 3 s into Колобок 400k (42 MB): "stopped by WorkManager (reason 7): waits for a network,
  then runs again", both cards "Waiting for a network", 1.7 MB in `no_backup/dictionary-downloads/`, nothing in
  `cache/`. Network back after 50 s: run 2, "1660 KB already there", installed; no retry counted.
- DIC31: JMdict (French) through a host socket that accepts and never answers (`settings put global http_proxy
  10.0.2.2:8899`): four runs (60 s timeouts, retries 30 s, 1 min and 2 min apart), then "JMdict (French): download
  stopped — connection lost" with Cancel and Resume. Proxy removed, JMdict (Russian) and Wiktionary (ja–ru) started
  together: each downloaded its own archive (`JMdict_russian.zip`, `wty-ja-ru.zip`) and installed. WorkManager's
  database lists the stopped French task (SUCCEEDED) as the Russian task's prerequisite, the case the default input
  merger got wrong. After `am kill` in the background and a restart the stopped card stayed and nothing ran by
  itself; Resume downloaded `JMdict_french.zip` and installed it.
- DIC24: a file planted in `cache/dictionary-downloads/` went with the first import of the next process (the bundled
  install at start logged "1 import leftovers"); Clear cache not run.
- BUB16, the hold menu path: the task removed, `am start -n …/MainActivity` (the intent the search used before), Home,
  the bubble pulled out over the launcher, its hold menu: Copy all recognized text and Open Screenlate; Open Screenlate
  brought back the same ActivityRecord, one MainActivity in the task. The selection menu's path and `OpenAppTest` not
  run (`am instrument` force-stops its target).
- BUB17: over the app's Home the hold menu has only Copy all recognized text.
- POP39, by screenshots only: `form-of-ja.png` in the debug viewer, the aim on く of 動く → the popup with its entries;
  on to 社會 → the popup moved with 社會's entries; ✕, then the aim on 行く → the popup opened with the new word. Once
  drawn the popup window is touchable again (no `NOT_TOUCHABLE`). Single frames not checked.

Left on the emulator: as before (the `languages/main` debug app, its service enabled, the proxy settings deleted,
Wi-Fi and data on); the emulator shut down and the lock released.

### 2026-10-28, `languages/sq` (`5b2f337e` on `d5bd5919`), Pixel_10_Pro (API 37)

Scope: the first checks of Albanian (LNG27, DIC40, OCR43, LK73, POP41, AUD28, ANK35) through the download screen, the
overlay over `testdata/ocr/sq-sample.png` and `sq-anki.png` (Segoe UI, in the debug viewer) with cloud recognition,
ML Kit and PaddleOCR v5, app text in AnkiDroid's note editor, the Search screen and the selected-text action, the
Popup, Audio and Anki settings and Anki notes into the local "Screenlate Test" deck ({sentence} as Meaning); the
engine's instrumented tests. An x86_64-only APK.

Results: passed, with the deviations below; AUD28's recordings not checked. DIC40 moved from N to F. Automated: unit
tests, lint, the page tests (146), the glue tests (76), the catalog, frequency and flag tests passed; I
`LanguageLookupTest` 32 of 32 and the engine's 58 instrumented tests on the emulator, the test package uninstalled
after.

- LNG27: Albania's flag on the Languages screen; English definitions by default, "Another language…" Russian, French,
  German, Chinese, Polish and Turkish; Word frequency (Leipzig Corpora Collection) ticked, 25 MB, its description
  naming news texts; Transcription (Wiktionary) ticked (119 kB); ML Kit the main model, PaddleOCR v5 "More accurate",
  Tesseract fast (1.9 MB) and accurate (4.6 MB) unticked. Both PaddleOCR models showed ticked and "Installed", as
  earlier Latin-script languages had downloaded them. The download was 28 MB; the bubble shows Ë.
- DIC40: built on the device as "Leipzig Corpora Collection (sq)", sorting results; the frequency chip shows on
  Albanian entries (gjatë L 47, sulm L 1726, leje L 1040, shqiptare L 151).
- OCR43: cloud recognition, ML Kit and PaddleOCR v5 (chosen under Text recognition → On-device model, engines Device)
  read every word and mark of `sq-sample.png`, except that ML Kit reads Ç'është as Çështë (the apostrophe lost, and
  nothing is found there); all three write “Po” as "Po".
- LK73 (Search screen): gjate finds gjatë ("e for ë"); sulmit sulm ("plural « definite", Yomitan's rules); ndodhet
  ndodh ("mediopassive « present indicative third-person singular"); lejen leje ("singular definite accusative"), a
  noun only; shqiptare, a headword of its own, shows its own entries first, then shqiptar and shqyptare through the
  dictionary's form rows, with no ending chip. kryesore finds nothing: sq-en has neither kryesore nor kryesor (sq-fr
  and sq-pl have kryesor); the case had named it from the instrumented test's data and now names shqiptare. The
  selected-text action: s’ka finds ska ("alternative"). Overlay (cloud recognition, then PaddleOCR v5): rëndësishme
  finds rëndësishëm ("feminine or plural"); ndodhet, sulmit and lejen as on the Search screen; aimed at ka in s'ka,
  at është in Ç'është and at i in t'i, the lookup starts after the apostrophe (ka, jam, the entries of i); from the s
  of s'ka it finds ska. The start after ’ was not seen on the device (the sample has ', and the Search screen looks up
  from the text's start); AlbanianSupportTest covers it.
- POP41: the font card lists the phone's font, Lexend and PT Serif (downloaded), Literata, Inter, Atkinson
  Hyperlegible Next and PT Sans, with "Only for Albanian text" and an Albanian sample sentence; with PT Serif chosen,
  the page's chosen face is PT Serif over U+0-24F, which holds ë ç; the phone's font restored after. In Russian the
  chip of an ending of our own reads "женский род или мн. ч."; the interface language restored to System.
- AUD28: sources Lingua Libre, Wiktionary, Text-to-speech; the Test button of the phone's voice found it, and Google's
  engine spoke with its Albanian voice (sq-al). The recordings were not checked: commons.wikimedia.org and
  lingualibre.org answered HTTP 503 to most requests, from the emulator and from the computer, throughout the run;
  holding 🔊 on diell (which Lingua Libre has) listed only Text-to-speech, and Home showed "Needs attention: Audio
  sources that failed" with both sources and the status.
- ANK35 (deck Screenlate Test, cloud recognition): banon "Z. Hoxha banon në rr. Durrësit, nr. 5.", revista "Libra,
  revista etj. dhe gazeta janë këtu.", vitin "Cezari vdiq në vitin 44 p.e.s.", Pastaj "Pastaj Roma ndryshoi.", ka
  "«Ai s'ka kohë!»", tha "«Ai s'ka kohë!» tha ajo.". App text in AnkiDroid's note editor (10 of 10 text nodes):
  banon, added anyway as a duplicate, "Z. Hoxha banon në rr. Durrësit, nr. 5."; the editor's text discarded.

### 2026-10-28, `languages/eo` (`ee52f32d` on `d206dcaf`; DIC39 after the rebase onto `9a401674`), Pixel_10_Pro (API 37)

- Build: debug APK for x86_64 only; unit tests, lint, the page tests (146) and the glue tests passed; LanguageLookupTest
  (31 tests) passed on the emulator, its test package uninstalled after.
- LNG26: the download screen offers English, and "Another language…" Russian, Spanish, French, German, Chinese,
  Japanese, Polish, Portuguese and Turkish; Transcription (Wiktionary) ticked; ML Kit (built in) checked, Tesseract fast
  "More accurate" unticked (4.7 MB), accurate unticked (7.4 MB), no PaddleOCR; the Esperanto flag; the bubble shows Ĉ.
  Downloaded with Tesseract fast ticked (11 MB). Checked before the frequency dictionary existed (DIC39).
- OCR42: cloud recognition (final from cloud) gives the text but for Eĥoŝanĝo, read as Ehoŝanĝo. ML Kit (Engines on
  Device): "HarkovO." still finds Ĥarkovo through the keys, "manĝis jus?", "Ŝi aĉetis fresaj" (Ŝ and ĉ written).
  Tesseract fast, chosen under Text recognition → On-device model ("Recommended · Installed"): "Eĥoŝanĝo ĉiuĵaŭd" with
  every hat (final from the device model).
- LK72: Search: cxu, CXu and cu find ĉu; mangxajxojn and mangajojn find manĝaĵo; pomojn finds pomo (accusative «
  plural); malbona shows malbona, malbono, then bona (mal-); si shows si, then ŝi; dank' alone finds nothing (the
  dictionary has dank' al and dank'al), dank' al and dank’ al (sent through the selected-text action, as `input text`
  cannot type ’) find dank' al. Overlay: Ĉu finds ĉu (cloud); with ML Kit pomojn finds pomo, malbona malbona and
  malbono.
- POP40: the font card lists the phone's font, Lexend, PT Serif, Literata, Inter and PT Sans with "Only for text in
  Esperanto", no Atkinson Hyperlegible Next; PT Serif draws ĥ ŝ ĝ ŭ in the headwords; the phone's font restored after.
- AUD27: sources Lingua Libre, Wiktionary, Text-to-speech; holding 🔊 on akvo lists six Lingua Libre speakers, then
  Eo-akvo.ogg (Wiktionary), then Text-to-speech.
- ANK34 (deck Screenlate Test): on-device (Tesseract fast) foriri "Li diris „Jes.“ kaj foriris.", amiko "“Venu,
  amiko.”", veni "“Venu, amiko.” Ŝi venis."; cloud recognition: pomo "Ekz. pomoj kaj piroj ktp.", jarcento "La 9-a
  jarcento a.K. estis longa.", but it writes the “ after Jes. and the one before Venu as ", so foriri got '" kaj
  foriris.', amiko '"Venu, amiko.”' and veni both sentences. The straight-quote rule reads a " only after ! or ?
  (question 22); plan question 30 added, decided provisionally to leave it.
- DIC39: the catalog's Frequency part lists "Word frequency (Leipzig Corpora Collection)", eo, 19 MB, CC BY, its
  description naming Wikipedia articles; it built on the device as "Leipzig Corpora Collection (eo)", sorting results;
  si shows si (L 366), then ŝi (L 46); au, no headword of its own, finds aŭ (L 22). The tick on the first download
  screen was not seen: Esperanto was already set up (CatalogFileTest covers its recommended flag).
- Seen, not the app's: the emulator's NFC service crashed every few seconds, and the system stopped and rebound the
  accessibility service about every 10 s throughout the run; each time it was back within about a second, and Home
  kept the card "The bubble is not shown: the system stopped the service" while the service ran.

### 2026-10-28, `languages/5a-answers` (`00661d7b` and the straight-quote fix on top), Pixel_10_Pro (API 37)

- Build: debug APK for x86_64 only; unit tests, lint and the page tests passed.
- LK71: Lookup → Scan length shows 24 for Swedish, Norwegian and Danish, 16 for German; over an image of
  "Statsministerkandidaten talade länge." aimed at its start, the popup's compound line is statsminister + kandidat.
- ANK33: `sl-anki.png` with cloud recognition (final from cloud): the sem note has "»Pridi sem!«", the je note
  "»Pridi sem!« je zaklicala.". Romanian "„Vino aici!” a strigat ea.": found, the strigat note had `" a strigat ea.`;
  cloud recognition gives the ! and the " as words of their own, and the rule looked only inside a word. Fixed (the
  character before a word is the last one of the word before it); after the fix the note has "„Vino aici!” a strigat
  ea.". The Slovenian sentence typed with >> << in AnkiDroid's note editor (app text): sem → ">>Pridi sem!", je → "<<
  je zaklicala."; the typed note was discarded, not saved.
- DIC37: the catalog's Frequency part lists "Word frequency (Leipzig Corpora Collection)", la, 16 MB, CC BY, its
  description naming Wikipedia articles; it built on the device as "Leipzig Corpora Collection (la)", sorting results;
  amicis shows amicus (L 2148), amica (6970), then amicio unranked. The rank in the notes said 2,147; recounted as the
  app counts (1 the most frequent) it is 2,148, corrected. The tick on the first download screen was not seen: Latin
  was already set up (CatalogFileTest covers its recommended flag).
- DIC38: `freedict-slv-eng.zip` imported from Downloads lands under Slovenian as "FreeDict sl-en" (sl → en), version
  0.1, author "Gorazd Gorup, FreeDict", 5,552 terms, the GPL notice and a Website button; ob shows its line "at,
  against, along, by, beside, on"; sem shows its "here" after the two Wiktionary dictionaries.

### 2026-10-28 night, `features/settings-rework` (`fe1afd9a` and the catalog layout on top), Pixel_10_Pro (API 37)

- Build: debug APK for x86_64 only; unit tests and lint passed. Slovenian active; the app switched to Russian with
  `cmd locale set-app-locales` for the plural and heading checks.
- SET14: the list in the new order with both new icons; Text recognition holds Screen recognition, App text (its
  question before App text only) and Test (OCR test opens; back keeps the scroll); About has no Tools card; Camera →
  Engines opens Text recognition. The e-ink hint's link and the overlay's models link were not reached (the hint shows
  only on e-ink models).
- SET15: "Shown in all apps", "Hidden in 2 apps"; Russian «Скрыта в 1 приложении», «в 2 приложениях», «в 5
  приложениях», «Показывается во всех приложениях». An uninstalled hidden app not tried. App labels follow the app's
  language, so Cyrillic names sort after Latin ones in the list.
- SET16: Anki and Audio are separate pages with the language card; Audio on Spanish shows Regions and the test word
  "agua"; Audio's reset dialog names autoplay, volume, sources and regions. Bubble's reset kept Engines on Cloud; Text
  recognition's returned Both, OCR boost Off and app text off (the defaults). Home's failed-audio link not reached.
  Found: the Audio hint said "Auto play" while the switch says "Autoplay"; fixed.
- SET17: the "If the service turns off" card ends Background work, in English and Russian.
- DIC36: English UI: "English" open, "Russian" open by itself (JMdict waits for an update), the others closed; a group
  closed by hand stayed closed after leaving the page. The new layout (Russian UI, Japanese): Russian, English and
  Japanese on top, "Other languages · 14" closed with each language indented and closed inside, then a divider,
  "Supplementary dictionaries" and Frequency, Pitch accent, Kanji. Found: Russian headings showed language names in
  lowercase ("японский", "→ русский"); fixed, and the arrows were dropped at the owner's request.
- BUB18: holding the docked bubble over the launcher opens the menu with Open Screenlate only, also after a scan (no
  copy items); a quick move pulls the bubble out as before. Flags not tried (one quick language), nor other dock sides.

### 2026-10-28, `features/camera-followups` (uncommitted build on `be79b43c`), Pixel_10_Pro (API 37)

- Build: the debug APK built with native code for x86_64 only (an init script clearing `ndk.abiFilters` to
  `x86_64`; 93 MB), so no APK copy was needed; unit tests and lint passed. Slovenian active, screen source over
  `sl-sample.png` in the debug viewer.
- POP39: `screenrecord` at 640 × 1424 and every frame extracted (ffmpeg). Popup on "žlica", the bubble docked, pulled
  out and aimed at "Žena" → the highlight first, then after about 150 ms the popup with "žena" only; no frame showed
  "žlica". A popup closed by a lookup without results, then opened on another word: the same. From one word straight
  to the next (the popup open), the old content stayed for one frame after the highlight moved, then the new one
  (about 70 ms). The first popup after the install took about 0.4 s to appear. An aim far off the text keeps the
  popup: "When the aim leaves the word" is off on this AVD.
- BUB17: over the debug viewer (Screenlate in front) the hold menu has Copy this paragraph and Copy all recognized
  text only; over the launcher Open Screenlate too, and it brought the open window back. Over the live camera
  (nothing to copy, one quick language) holding opens no menu. `dumpsys vibrator_manager` lists no vibrations on
  this AVD, not even a pull-out's, so the vibration part is not seen.
- CAM7: over a recognized photo, the copy items only, no flags.
- CAM56: `market-4000.jpg` (4000 × 3000) imported with High accuracy off → one cloud request (1500 × 1125, 164 KB,
  1.9 s), "Recognizing" with a bar without a share, then "Recognized · <time>" without a region count. Wi-Fi and
  data off, Recognize → read on the device (ML Kit's pipeline in the log), recognized whole.
- CAM57: Settings → Camera reads "Accuracy, photo history, automatic saving, limits"; Camera settings start with
  Recognition (High accuracy with its hint, Engines "Both" with a chevron); Engines opens Bubble at Screen
  recognition, Back returns. High accuracy on → the photo read whole shows "Settings changed" with the tinted strip;
  Recognize then reads the overview and 9 tiles (10 requests, "9 of 9 regions"); High accuracy off → that photo stays
  as it is.
- CAM58: ⋮ over the photo read whole → Recognize more accurately first; it read the overview and 9 tiles ("3 of 9
  regions" while reading, "9 of 9 regions" after); then ⋮ has no such item; while a recognition runs it is not shown
  either. A photo up to 1500 px: unit test only.
- Host: the first build attempts failed on the host, not in the code: D: had about 8 GB free, then the Windows commit
  limit ran out (a build left running before a session restart compiled MNN for three ABIs with 20 clang processes,
  while BlueStacks started). `--max-workers=3` and the x86_64-only init script kept this run within memory.
- Cleanup: the test photo deleted (no history), the photo language back to Japanese, the recordings on `/sdcard`
  deleted; `market-4000.jpg` stays in `/sdcard/Pictures`. The AVD powered off, adb stopped.

### 2026-10-27, `features/camera-lookup` at `a16ceda7` (the camera review's last commit), Pixel_10_Pro (API 37) and Screenlate_API30_glide (API 30)

- Build: an x86_64-only copy of the debug APK on API 37; Japanese active, screen source over Search results for
  "shashin".
- BUB1/OCR1: pull-out → cloud final 42 paragraphs in 1.7–1.8 s, 何 with its highlight. The ML Kit band never came
  before the cloud: offline, the band took 3.0–3.9 s and the whole page 7.6–7.9 s (8 and 39 paragraphs), about three
  times the run of 2026-10-26 (draft in 1.1 s), before and after a reboot. The screen OCR path has no change since that
  run and the emulator was idle between scans: the emulator ran slower today (see NF1, NF3). Offline the band draft
  shows first, then the device's final.
- BUB2: moved → lookups only (51–252 ms), no scan.
- ANK2: ➕ → Added; 📖; holding 📖 → "Add anyway" and "Add anyway, with a picture" → crop editor → Added with a picture
  (forced); 📖 opened AnkiDroid's card browser.
- BUB5: Korean starred: the floating bubble's hold menu shows the Japanese and Korean flags, Japanese ringed; Korean →
  glyph 한, log "Active language ko from the bubble menu"; back to Japanese the same way. The hold menu opens only on
  a floating bubble; adb needs separate `motionevent` DOWN and UP for it.
- LNG1: the home card with both flags, the active one ringed; with one quick language no flag row.
- UPD2: the app's own log (2.3 MB) and logcat after the scans, lookups and notes: no CJK characters, none of the words.
- NF1: tap rescans: cloud final 1.1–1.4 s, about 0.1–0.2 s after the scan starts; the device draft as above.
- NF3: dragging the floating bubble over text: 99 % janky frames, median 113 ms, 90th percentile 200 ms (last run
  82 %, 30–34 ms, 65–73 ms). The whole emulator ran about three times slower today, ML Kit's native time included;
  to recheck on the phone or after a host restart.
- Not run: OCR9 (U ReadingOrderTest), OCR18/OCR19 (no Paddle change), the lookup cases (no lookup change), and a hit
  test of a Latin word or a number in an Arabic line on the emulator (TextLayoutTest covers the split; the change
  also applies to screen scans in right-to-left languages).
- Instrumented on API 37: PhotoImageTest 3/3, PhotoRegionsTest 2/2, CropEditorTest 7/7, ScreenNoteImageTest 4/4,
  WebpLimitTest 1/1. On API 30: LimitFieldTest 1/1, FirstRunRequestTest 1/1. A comma list in
  `-Pandroid.testInstrumentationRunnerArguments.class` ran only its first class: run one class per call.
- A minified build of the same commit for the owner's phone (release type with the Dev id and label, debug-signed,
  arm64 only, compressed native code): 20.6 MB. Its x86_64 copy scanned, looked up and opened Camera on API 37. The
  R8 mapping is in `testdata/local-research/builds/`.
- CI (`ci.yml` by hand on `a16ceda7`): passed.
- Cleanup: API 37: the photo history (four test photos) and the current photo deleted; `/sdcard/Pictures/review-*.jpg`,
  an old saved PNG and UI dumps on `/sdcard` deleted; Slovenian active again, Korean unstarred; test packages removed;
  `sys_storage_threshold_max_bytes` set for one install and deleted. Updates of YouTube, YouTube Music, Maps, Gmail,
  Photos and the Google app uninstalled (about 1 GB; the Play Store brings them back): data free 1.6 GB, it had
  590 MB and refused installs. The local "Default" deck holds two more 枚 notes. API 30: the debug app reinstalled at
  its first run (the connected run uninstalls it), the stale `dictionary.api.test` package removed. Both emulators
  shut down; lock released. `/sdcard/AnkiDroid-owner-backup` (1.1 GB) stays.

### 2026-10-27, `features/camera-lookup`, the work from the camera review's answers (uncommitted build on `325fe030`), Pixel_10_Pro (API 37)

- Builds: an x86_64-only copy of the debug APK (storage thresholds lowered for the install, deleted after).
- CAM50: a 101 MB JPEG (a small image padded after its end) picked in the photo picker → "The image is larger than
  100 MB.", the current photo stays, no partial copy in the app's storage. A 104 MB PNG of 5900 × 5900 px filled the
  emulator's memory while the media provider indexed it, and the picker no longer opened until a reboot; the padded
  JPEG avoids that.
- CAM51: Slovenian active, Dutch chosen on the chip → Dutch turned off (dictionaries kept) → the chip shows Slovenian;
  Dutch turned on (it becomes active) and Slovenian made active again → the chip shows Dutch. The chip's choice also
  stays while the active language changes. A language turned on again moves to the end of the chip's list.
- CAM52: Whole photo of a 2400 × 3000 import → a 1789 × 2236 WebP (4.0 MP), upright.
- CAM53: pans injected with `PhotoGestureDriver` (the `core:photos` test APK) → the popup follows its word; over 120
  frames the popup's window drew no frame of its own; UI-thread time per frame 2.62 ms median with the popup, 2.49 ms
  without.
- CAM54: the Sentence field template `{sentence} | {document-title}` → "<sentence> | Camera" (template restored).
- CAM55: a 2400 × 3000 import with a 600 px headline across the seam of the tile rows → "6 of 6 regions" while
  recognizing and when done; the stored recognition holds the headline whole from the overview pass, its tile pieces
  dropped, the small text from the tiles.

### 2026-10-27, `features/camera-lookup`, the Camera screen review's fixes (uncommitted build on `205f3ddb`), Pixel_10_Pro (API 37)

- Builds: an x86_64-only copy of the debug APK (storage thresholds lowered for the install, deleted after); the
  accessibility service bound again after each install.
- `:app:testDebugUnitTest` (camera tests 75/75), `:app:lintDebug` with no findings in the changed files; instrumented
  `FirstRunRequestTest` passes on API 37 (failed before its fix); `LimitFieldTest` waits for the API 30 run.
- By hand, each reproduced before its fix: an `open=shared-image` request to the launcher with a private `file:` link
  and a share of `content://0@<app>…` → nothing imported; a Dictionaries request from the selection-menu search during
  the first run → the page over the first run, Back returns to it, no bar (NAV5); a word of the photo under the open
  language menu and under the open ⋮ menu → the bubble finds nothing there (CAM30); 10 s of live camera → no
  `setFlashAsync` line (CAM29).
- Camera settings (CAM39): a typed limit left with Back (keyboard open or hidden) or the arrow → stored; one that
  would delete a photo → the question; Cancel → the stored limit in the field, the next Back leaves; Delete → trimmed
  and the page closes; a quick double Back → one page closed; 10 typed and the Camera tab tapped → stored (lost
  before); 3 typed and Home tapped → the question on return to the Camera tab.
- ⋮ Save to history: shown for a manual capture, hidden for an automatically saved one (CAM30).

### 2026-10-26, `features/camera-lookup`, importing images and sharing them to the app (uncommitted build on `ea48441d`), Pixel_10_Pro (API 37) and Screenlate_API30_glide (API 30)

- Builds: API 37 an x86_64-only copy of the debug APK (storage thresholds lowered for the install, deleted after);
  API 30 the universal debug APK.
- Root `assembleDebug testDebugUnitTest`: 1652 JVM tests, 0 failures, 5 skipped; `:app:lintDebug` and
  `:core:photos:lintDebug` with no findings in the new code or strings.
- API 37 instrumented `PhotoImportDeviceTest` 3/3 (JPEG bytes and orientation kept, a BMP becomes a PNG the viewer
  reads, text rejected); the test APK uninstalled after.
- API 37 by hand: the import button left of the shutter; closing the picker → no change, `.staging` empty (CAM42);
  a JPEG with EXIF orientation 6 → upright, the stored bytes identical, recognized at once (CAM43); ⋮ over a photo:
  Save to gallery, Save to history, History, Import from gallery, Camera settings; over the live camera: History,
  Import from gallery, Camera settings (CAM30); a GIF over an unsaved photo → the question after the pick, Cancel →
  the photo kept and the copy deleted; again → Delete → stored as PNG, the old folder gone; a BMP → Save to history →
  the old photo in the history with a thumbnail (CAM44, CAM46); a share from the shell without a grant → "The image
  could not be opened." (CAM45); a share from the Files app while on the Settings tab → the Camera tab and the
  question, Delete → the image current; Save automatically on, a shared JPEG → no question, "Photo 28 saved to the
  history"; the history tiles at the import times with the Japanese flag (CAM43); camera access blocked → "Open
  settings" and "Import from gallery" → imported and saved (CAM32, CAM42).
- API 30 by hand: a share from the Files app during the first run → the app in front, still on the first-run question;
  after the first run (Japanese, JMdict and the recommended extras) the Camera tab opened the shared GIF as a PNG of
  1,145,923 bytes, as on API 37, and recognized it (CAM45); ⋮ Import from gallery opened the Files app's picker (no
  system photo picker on this image); a PNG picked over the unsaved photo → the question → Save to history → the GIF
  in the history with a thumbnail, the PNG current with its 602,500 original bytes (CAM44, CAM46).
- Findings, fixed: a share that reached the app without a read grant (the shell's `am start`) did nothing, as passing
  the grant on threw a SecurityException that the start helper swallowed; the share activity now passes the image on
  without the grant flag when it holds none, and the Camera page says it cannot open it. Lint then flagged the
  exported share activity (UnsafeIntentLaunch): any app could hand it a link to the app's own providers or a `file:`
  path into its data. It now takes only another app's `content:` link through `IntentSanitizer`. The first build of
  that crashed on every share (the sanitizer requires a component rule) and the second dropped every link (it keeps
  EXTRA_STREAM only with the read-grant flag allowed); both were found on API 37 before the commit. The final build on
  API 37: the app's own log link and a `file:` path to its preferences → nothing opens, no crash; a media link from
  the shell → "The image could not be opened."; a share from the Files app → imported and recognized. Lint reports
  the Uri read from the share as an intent and ignores `@SuppressLint` and `tools:ignore` there, so `app/lint.xml`
  ignores UnsafeIntentLaunch for that file alone.
- Not checked by hand: a restart from the recent apps does not import the shared image again (it needs the process
  stopped, which these checks never do).
- New: CAM42–CAM46, all F. CAM30 and CAM32 reworded for the import items.

Left on the emulators: API 37 Save automatically off, no photos, camera access granted, the test images and their
media rows deleted, the storage threshold settings deleted, the service bound; API 30 the debug APK reinstalled to
bring back the first run it had before, the test image deleted, the service bound. Both shut down.

### 2026-10-26, `features/camera-lookup`, the photo history and Camera settings (uncommitted build on `9d3b8c24`), Pixel_10_Pro (API 37) and Screenlate_API30_glide (API 30)

- Builds: API 37 an x86_64-only copy of the debug APK (storage thresholds lowered for the install, deleted after);
  API 30 an x86-only copy and the app's test APK.
- Root `assembleDebug testDebugUnitTest`: 1636 JVM tests, 0 failures, 5 skipped; `:app:lintDebug` and
  `:core:photos:lintDebug` with no findings in the camera code or the new strings.
- API 37 instrumented `PhotoDatabaseTest` 9/9 (v2 → v3, history order, upright thumbnail). Its history-order test then
  changed one line (`unsave` → `delete`) and was only compiled again.
- API 37 by hand: the history icon right of the shutter, the latest thumbnail after saving (CAM36); ✕ at the left and
  the ⋮ order (CAM30); Save to history → "Saved to history" and the item gone (CAM35); a saved photo closed silently;
  the unsaved question, Delete → "Photo deleted", Save → in the history (CAM34); the grid with time and flag; opening
  with the question → Save → the old photo with its stored text and no recognition in the log (CAM37); long press,
  "2 selected", the plural question, folders deleted (CAM38); Camera settings from ⋮ and Settings, automatic saving
  hiding the item, "The 2 oldest photos…" with Cancel restoring the field and Delete trimming (CAM39).
- Owner's change during the check: a photo that leaves the history is deleted, the open one included. After it:
  automatic saving put a capture in the history (thumbnail present, no Save to history in ⋮); Clear history →
  "All photos in history will be deleted." → no photo folders, the Camera tab on the live camera (CAM40); deleting the
  open photo on the history page → the live camera (CAM38).
- API 30: `UnsavedPhotoDialogTest` 3/3.
- Findings, fixed: Save to history in the question on the history page did nothing, as the dialog dismissed itself
  (clearing the question) before answering; the answer now comes first (`UnsavedPhotoDialogTest`). Back on the Camera
  tab after a deletion, the history button decoded the deleted photo's thumbnail from the last list it saw
  (BitmapFactory error in the log, the icon shown); a missing file is now skipped.
- Not checked by hand: the e-ink history button; Clear history keeping an unsaved open photo (unit test only).
- New: CAM34–CAM41, all F. CAM11, CAM30 and CAM32 reworded for ✕ instead of Delete.

Left on the emulators: API 37 Save automatically off, both limits 0, no photo, the storage threshold settings
deleted, the service bound; API 30 the test APK removed, the universal debug APK reinstalled (an x86-only copy
lacks ML Kit's libraries), the service bound. Both shut down.

### 2026-10-26, `features/camera-lookup`, the Camera page restyle (uncommitted build on `54b310df`), Pixel_10_Pro (API 37) and Screenlate_API30_glide (API 30)

- Builds: API 37 an x86_64-only copy of the debug APK; API 30 the universal debug APK.
- Unit: CameraPhotoControlsTest (8), CameraViewModelTest (11), CameraLookupTest (3), PhotoDrawingTest (4) and
  PhotoViewportTest passed; lint shows no new warnings in the camera code.
- API 37, camera access off: the crossed-out camera icon, the title, the text and Allow camera; after the access was
  denied in the system dialog, "Camera access is blocked" with Open settings (CAM32).
- Live camera: the 4:3 frame at its viewer size with the blurred room around it and no black band above the tabs;
  the chip at the top left; the ring shutter with a spinner in the ring during capture (CAM29). Photos stay 1280×960
  (the virtual camera's largest JPEG) with the analysis stream at 320×240 (`dumpsys media.camera`).
- Over a photo: New photo and Delete as icons, ⋮ → Save to gallery; the strip "Recognized · 2:05 PM" with Recognize as
  a text button (CAM27, CAM30).
- The chip's menu over the photo and over the live camera: "Photo language", the turned-on languages with flags, a
  check on Japanese, "Active" under the app's active language, Manage languages → Settings → Languages (CAM28).
  Choosing Khmer tinted the strip: "Recognized: Japanese · 2:05 PM · Language changed", Recognize filled; after
  Recognize, "Recognized · 2:07 PM".
- Partial fixture (`PhotoWorkflowTest -e photoFixture partialCloud`): "Recognized · 3 of 4 regions · 2:09 PM ·
  Settings changed" with ⚠; the dialog with the warning icon, "1 of 4 regions could not be recognized. Words in these
  regions can't be looked up.", the picture with the missing region marked and the cause; Retry missing regions →
  "Recognizing · 3 of 4 regions" with the bar at 75 %, then "Recognized · 4 of 4 regions" (CAM27, CAM31).
- With a photo, the live camera has ✕ at the left, which returned to the photo (CAM30). Delete → the dialog →
  "Photo deleted" over the live camera (CAM32).
- E-ink theme: black around the live frame, the chip with "JA"; a language change frames the strip and makes its
  text bold (CAM27, CAM29).
- API 30: LiveCameraTest (1), PhotoViewerTest and PhotoViewerGestureTest (2) passed. Below API 31 the fill is only
  scaled, without blur; not looked at by hand, as the app there was at the first-run language choice.
- Not checked by hand: the pressed shutter shrinking; "Dictionary missing" and "Recognition model missing" in the
  menu (no turned-on language lacked either); the bubble with the menu open; "Opening photo…"; the blocked-recognition
  dialog title (CameraViewModelTest only); CAM33, as adb cannot pinch on API 37 (PhotoViewportTest only).
- Moved to F: CAM27–CAM32. CAM33 stays N.

Left on the emulators: API 37 the Japanese camera language, no photo, e-ink off, camera access revoked with its user
flags cleared, the storage threshold settings deleted, the service bound; API 30 the test APK removed, the service
bound. Both shut down.

### 2026-10-26, `claude/nifty-faraday-59a7fb` (`languages/main` d1977c62 with the fix, 0.2.3-5f4a75d6, x86_64-only copy), Pixel_10_Pro (API 37)

Scope: "Open Screenlate" in the bubble's hold menu started a second MainActivity over a task that the selection
menu's search had started. BUB16 (new, first check, moved to F).

- Before, on 0.2.3-a2235350: `am start -n …/com.vpr.screenlate.MainActivity` (the intent the search used), Home, the
  hold menu's "Open Screenlate" → task #289 with two MainActivity records.
- `OpenAppTest` (4 cases, `am instrument -e class`): passed. With `SINGLE_TOP` removed from `OverlayIntents` for a
  check, "a task started by another intent" and "search opens a screen in the running app" failed (a second instance,
  a recreated one); the two search cases still passed, as the task's root intent is now the launcher's.
- BUB16 by hand: "voda" selected in AnkiDroid's note editor → "Look up in Screenlate" (the second of two such items:
  the release build is installed too) → the grey ➕ after `pm revoke … com.ichi2.anki.permission.READ_WRITE_DATABASE`
  → "Open Anki settings" → task #295 with one MainActivity, root intent `MAIN`/`LAUNCHER`; Home → "Open Screenlate"
  → Anki settings again, the same ActivityRecord, one in the task; Home → the launcher icon → the same; Back → the
  home screen of the app, Back → the launcher. The permission was granted again afterwards.
- The Contacts editor's selection menu on API 37 lists no "Look up in Screenlate" (only Cut, Copy, Share, Translate);
  AnkiDroid's note editor does, behind ⋮.

Left on the emulator: 0.2.3-5f4a75d6 instead of a2235350, data kept, the Anki permission granted, the service bound;
the storage threshold settings deleted again; the emulator shut down.

### 2026-10-26, `features/camera-lookup`, the Search tab without auto-focus (uncommitted build after `a2235350`), Pixel_10_Pro (API 37)

- NAV4: the Search tab with a blank query opened with the field unfocused and no keyboard (`mInputShown=false`), the
  bar visible; tapping the field opened the keyboard; Back, Home, Search again → no keyboard.
- Moved to F: NAV4.

### 2026-10-26, `features/camera-lookup`, phase 1.5 acceptance on `a2027390` with the two fixes below

- Builds: Pixel_10_Pro (API 37) an x86_64-only copy of the debug APK; Screenlate_API30_glide (API 30, 32-bit x86)
  the universal debug APK. An "x86-only" copy has no native libraries (the debug APK carries none for x86), and
  ML Kit crashed at service start with UnsatisfiedLinkError; the universal APK runs armeabi-v7a through translation.
- Fixtures: PhotoRegressionFixtureTest prepare/restore and firstRun on both, unrecognized on API 37;
  PhotoWorkflowTest on API 37 only (`@SdkSuppress(minSdkVersion = 37)`); PhotoAnkiFixtureTest prepare/restore.
- Before the fixes, API 37, generated recognized page: CAM1 again. CAM3: aim on the toolbar, both letterboxes and
  the toolbar over a zoomed photo → no entry. CAM4: live camera, unrecognized photo and running recognition → pull-out
  and tap log "Nothing to read on the Camera page", no capture or app text. CAM6: entry over the Search page, then the
  photo shown → the entry closes, the photo's text attaches. CAM7: hold menu over the photo → Copy (12 characters),
  Open Screenlate (a launcher-started task stays on Camera), no language chips; glyph SL → あ over the photo → SL
  after leaving it. BUB5: flags still offered over the screen. NAV3: the debug image viewer and the first-run
  language list have no bar; finishing the first run reaches Home; the background-tip badge stays across tab changes
  and clears after leaving the Settings list.
- Finding 1 (fixed): a highlight's 2 dp margin drew over the toolbar above a zoomed photo. The overlay now clips
  highlights, margins included, to the photo's visible part (TextFrameTest). After: the toolbar's pixels at y 588-599
  keep their colour, the highlight starts at 600.
- Finding 2 (fixed): a pull-out right after tapping the Camera tab scanned the previous page, because the page became
  the source only when resumed, about 200-300 ms after its entry on API 37. The Camera page now presents its source
  from ON_START until ON_PAUSE. After: API 37 4/4 runs of tab tap + immediate pull-out log "Nothing to read on the
  Camera page", then "Photo text attached (pull-out, 32 paragraphs)" about 230 ms later (one run opened a cloud
  connection on touch-down before the page started; no image was sent). API 30, live camera: 3/3 "Nothing to read".
- E regressions on the fixed build, API 37, Japanese active, screen source over Search results for "shashin":
  BUB1/OCR1 ML Kit draft 7 paragraphs in 1102 ms, cloud final 42 paragraphs in 1900 ms, 何 with its highlight.
  BUB2 moved → lookups only (37 and 128 ms), no scan. ANK2 ➕ → Added; 📖; holding 📖 → "Add anyway" and "Add anyway,
  with a picture" → crop editor → Added with a picture (forced); holding ➕ → crop editor → Added with a picture;
  📖 opened AnkiDroid's browser empty, as before (its deck filter). With `{audio}` in Meaning and
  `{sentence-translation}` in Sentence of the test note type: Added, audio and translation. AUD1 holding 🔊 lists two
  recordings; both play. TR1 translation shown with the service chip and copy (Bing failed after 3 s, Google
  answered in 2.1 s); a second tap hides it; the next word starts without it. UPD2 logcat and the 1.7 MB app log:
  no CJK characters, none of the words, the sentence or its translation. LNG1 home card.
- NAV3 on API 30: first-run list → Arabic → its download page without the bar; Back returns to the list; no download
  started.
- Moved to F: CAM3, CAM4, CAM6, CAM7, NAV3.
- Noticed, not changed: the Search tab focuses its field and opens the keyboard on a blank query, covering the bar
  (asked the owner); "Open Screenlate" from the bubble menu stacks a second MainActivity when the task was started by
  another intent, such as ProcessTextActivity's (before this branch); a screen scan's popup stays after an in-app tab
  change (a scan is a snapshot; before this branch).
- Checks: root `assembleDebug testDebugUnitTest lintDebug --max-workers=1`: 1586 JVM tests, 0 failures/errors,
  5 skipped. Lint flagged one new UseKtx warning in the clip code; fixed with `withSave`, then `assembleDebug
  :overlay:testDebugUnitTest :overlay:lintDebug` passed: overlay 241 tests, its lint back to the 5 earlier warnings.
  Page scripts unchanged. Logs under ignored build/: photo-15b-*.log.
- Cleanup: API 37 generated photo deleted (no current photo, as before), both fixtures restored (Slovenian active,
  Japanese Anki settings back), camera language Japanese, test packages removed, install thresholds unset. The local
  unsynced "Screenlate Photo Test" deck holds four more notes. API 30 preferences restored to its unanswered first run,
  the universal debug APK stays installed, its test package removed. Both emulators shut down; lock released.
- Still pending on a phone: sharpness, focus, flash, capture delay, repeated-capture memory and drag baselines.

### 2026-10-26, `features/camera-lookup`, paused acceptance checkpoint after `6c3956a8`

- Owner requested pause after the current coherent milestone. Phase 1.5 is incomplete; no new CAM/NAV manual case
  was run or promoted. Next session must continue CAM3/4/6/7, NAV3 and relevant screen/Anki/audio/translation cases.
- API 37 class-specific instrumentation: SearchLanguageTest (2), ProcessTextTest (2), InstallationPopupTest (1),
  LanguageReactivationTest (1), LanguageSwitchTest (1): final 7/7 PASS. Initial batch had two failures. Installation
  link failure reproduced alone; fixed obsolete native-test DOM selector to match popup.js `.message-link` button.
  A missing isolated cache archive did not reproduce alone; moved fixture-owned files to noBackupFilesDir. Low-space
  cache eviction is plausible, not conclusively diagnosed. No production camera change was needed for these checks.
- Added explicit emulator-only PhotoRegressionFixtureTest. First smoke was rejected before execution because JUnit
  required a void return; explicit Unit fixed it. Final prepare and restore: 1/1 each, exact common preference-map
  equality checked and private backup removed. Other stages are compiled but not exercised. See camera notes for usage.
- Root `assembleDebug testDebugUnitTest lintDebug :app:assembleDebugAndroidTest --max-workers=1 --console=plain`:
  PASS (2m10s); 1585 JVM tests, 0 failures/errors, 5 skipped. Final helper return/import change reassembled native
  test APK successfully (16s), then prepare/restore passed. Page scripts unchanged; prior 146/146 result retained.
- Logs under ignored build/: photo-15-api37-app-fixed.log, photo-15-final-checks.log, photo-15-fixture-final-build.log,
  photo-15-fixture-prepare.log, photo-15-fixture-restore.log. Test thresholds restored after installations. No Anki
  writes/sync, photo replacement, force-stop, app-wide connected test or phone test in this checkpoint.
- All temporary fixture preferences restored; auxiliary test APK removed and owned emulator shut down at pause.
  Main debug app/data preserved; API 37 still has no current private photo from the previous deletion test.
  Physical sharpness/focus/flash/delay and repeated-capture memory/responsiveness remain pending.

### 2026-10-26, `features/camera-lookup`, original-photo Anki and gallery after `66b66d85`

- Implementation pushed as `e8951271`, followed by import/name cleanup and final documentation. Added CAM25/CAM26,
  replaced the temporary no-photo-image CAM8 expectation with original-photo crop behavior and promoted all three to F.
- Root `assembleDebug testDebugUnitTest lintDebug` and both library test APKs: PASS (3m32s); 1585 JVM tests,
  zero failures/errors, page tests 146/146. One duplicate import introduced during cleanup was removed before this run.
  Targeted suites/app build and all test APKs passed before device checks.
- API 37 and API 30: PhotoImageTest/PhotoGalleryDeviceTest 3/3 and CropEditorTest/CropViewTest 8/8 each. Checks include
  eight orientations, outward fractional selection, larger-than-preview original crop, identical JPEG/PNG byte export,
  orientation metadata, IS_PENDING cleared only after completion, screen coordinates/ownership and editor cancellation.
- Real API 37 popup over a generated Japanese page while app active language was Slovenian: long-press Add opened the
  original-photo editor; Cancel added no note; reopen, Whole photo/Frame toggle and bottom-right resize worked. Add
  exported a 1541×1257 original crop (its drawn frame was about 650×530). Local Anki fixture verified the Japanese
  sentence and image in the dedicated Screenlate Photo Test note. Only this new synthetic attachment was read back.
- Anki fixture prepare/verify/restore passed; the Japanese app settings were restored. Only the emulator's unsynced
  collection was used. No account, sync or AnkiWeb operation. No owner's collection changes.
- Save to gallery showed success. The exported PNG's SHA-256 matched the private original. After deleting the private
  current fixture through Camera, the gallery copy remained. No automatic export and no OCR call from gallery action.
- API 30 is 32-bit x86: x86_64-only shrunk test APKs failed ABI matching, so universal class-specific test APKs were
  installed and passed. Main app/data was not removed. API 37 used x86_64 APKs and temporarily lowered storage
  thresholds; prior settings were restored in finally. No app force-stop or app-wide connected run.
- Pending: physical sharpness/focus/flash/performance and phase 1.5's remaining manual CAM/NAV/audio/translation/
  ordinary screen-Anki regression variants. These runs establish image ownership/cropping/export, not full release acceptance.

### 2026-10-26, `features/camera-lookup`, recognition/viewer continuation after `01d24c0e`

- App assembly/lint and targeted unit suites passed before final root checks: app 187, photos 56, overlay 238,
  OCR 166. Coordinator tests cover terminal publication, two simultaneous workers, retained successful tiles,
  all-failed preservation, explicit-only restart and cancellation while an image reader terminates.
- API 37 `PhotoRegionsTest` + `PhotoDatabaseTest`: 7/7, all 8 EXIF transforms in original pixels, far edges,
  Room migration 1→2 and progress restoration. Separate library test APK removed after checks.
- Emulator-only `PhotoWorkflowTest`: real local ML Kit recognition of generated 2800×2400 Japanese two-column text,
  four tiles and original coordinates; no cloud requests in Device mode. Opt-in partialCloud fixture: four real
  cloud tiles at 1500×1500, then deliberately omit one saved tile for the UI retry scenario.
- Actual bubble: pull-out attached stored Japanese text despite app active language Slovenian; dictionary popup and
  highlights worked. Pinch and one-finger pan moved text beneath a stationary bubble and re-aimed from Japanese 語
  to 写真. No screenshot/app-text read/cloud preparation/refinement occurred and no new OCR requests were sent.
  Native public MotionEvent injection used a separate library test process with accessibility suppression disabled;
  Espresso injection on API 37 is broken. Screenshots, counts and booleans used for evidence; no user text logged.
- Camera language Japanese→Korean displayed Recognized: Japanese plus Settings changed, without OCR; restored Japanese.
  Shared OCR Both→Cloud required explicit recognition; it completed while on Settings, published once, cleared pending.
- Terminal warning: accessible top icon, 1 of 4 missing, network cause, Retry missing regions. Retry sent exactly one
  real cloud request while the app was backgrounded; all four tiles were retained and the warning disappeared.
- CameraX actual capture replaced the fixture with a 1280×960 EXIF-6 emulator frame; automatic OCR sent one 960×1280
  cloud tile without a separate tap. Shared mode restored to Both. No Anki collection changes.
- Emulator PSS observations (not physical-device baselines): fit + loaded dictionaries ~248 MB, zoom/lookup ~316 MB;
  after replacement/refit ~190 MB, after leaving ~198 MB in a newly started process. Physical repeated-capture,
  latency/sharpness/focus/flash remain pending.
- Final root `assembleDebug testDebugUnitTest lintDebug --max-workers=1`: PASS, 1576 JVM tests, zero failures/errors.
  After a final warning-message adjustment, app assembly/unit/lint and the test APK were rebuilt successfully.
- API 30 `PhotoViewerGestureTest`, `PhotoViewerTest`, `LiveCameraTest`: 3/3 PASS. Actual pinch/pan updates only the
  viewport, preserves source generation and recognition identity, and reopening fits again; all 8 painted EXIF
  transforms and preview focus/pinch/reset remain correct. CAM24 promoted to F.
- API 37 full shutdown/reboot restored the captured image, Japanese OCR and independent camera language. Both had
  been restored while the saved result used Cloud: pending indication was correct. No new PhotoOCR/Lens request on
  restoration. The emulator does not reconnect this accessibility service reliably after boot; manual toggle/rebind
  was tested earlier. No app force-stop, app data reset or collection sync was used.
- CAM1, CAM2, CAM5 and CAM9 promoted to F with their real-photo/restart evidence. CAM3/CAM7 remain N for their full
  dedicated manual variations; CAM4 retains N until every inactive/loading variant is checked on-device. Physical
  flash/sharpness/focus and repeated-capture performance are still pending, as are phase 1.4 photo attachments/export.

### 2026-10-26, `features/camera-lookup`, photo OCR foundation after `76a4f679`

Unit verification only; recognition is not wired into the Camera UI at this checkpoint. CAM16-CAM18 added as F after
passing. OCR 165, photos 40, overlay 237 tests passed; app compilation and lint passed (`--max-workers=1`). Page tests
146/146 passed after `npm --prefix scripts/page-tests ci`; no JavaScript changed. Device checks wait for complete UI.
The combining-mark deduplication test initially expected a particular Unicode normalization from the less central
tile; corrected it to assert canonical equality, preserving the engine's actual chosen text and boxes.

### 2026-10-26, `features/camera-lookup` (on `f5a3ca43` with the camera implementation before its commit), Pixel_10_Pro (API 37)

Scope: continuation of phase 1.2, camera and the previously unverified navigation bar. Installed an x86_64-only copy
of the assembled debug APK over the existing app; preserved its data and local Anki collection. Temporarily lowered
the emulator's storage threshold for installs and deleted both overrides afterwards. No force-stop, no Anki writes.

- CAM10, CAM11, CAM13, CAM14, NAV1 and NAV2 added as F after passing. Camera permission: denied twice, opened app
  settings from the blocked state, granted while-in-use there and returned to the working camera. Capture and fitted
  image, retake/back, deletion/cancel/confirmation, replacement and single stored photo passed. Full emulator shutdown
  and cold reboot kept the original photo directory and reopened the stored image. Camera had no active clients in
  the viewer. No flash unit on this emulated camera: flash is correctly hidden, hardware behavior remains D.
- CAM4 partly checked: pull-out/tap/drag over live preview gave no entry, capture, accessibility text or cloud scan;
  log: "Nothing to read on the Camera page". Keep N until the loading/unrecognized-photo variants are checked.
  CAM5-CAM8 await recognized photos in 1.3; do not count these as passed by the preview check.
- Settings > Bubble restored after Camera and back; reselect returned to Settings root, Back returned Home. Typed
  Search text survived Camera and back. An external Anki settings request selected Settings and opened that page.
- `PhotoViewerTest`: all 8 EXIF transforms checked against actual painted colors and reported viewport, 1/1 passed;
  `ProcessTextTest` 2/2 passed in the same class-specific instrumentation run. The first viewer test's disposal
  assertion raced Compose; changed it to wait for disposal, then passed. Test APK uninstalled; main app retained.
- `assembleDebug`, `testDebugUnitTest` (1533 tests, 0 failures/errors in 9 modules) and app `lintDebug` passed with
  `--max-workers=1`. The 15 new JVM tests cover page/source lifetime, stale callbacks, capture concurrency/cancellation,
  failure preservation, load retry, all EXIF matrices and sampling. New strings in all 14 locales pass translation tests.
- No page-script change. Physical book-page sharpness, flash, focus quality, timing and memory under repeated full-size
  captures remain pending. NAV3, CAM15 and recognition-dependent cases remain pending.
- API 30 follow-up on `Screenlate_API30_glide`: `PhotoViewerTest` and `LiveCameraTest` 2/2 passed. The latter drives
  real PreviewView tap focus, two-finger pinch and the accessible zoom-reset button; CAM12 added as F. Fresh-install
  first-run page showed no bottom bar (NAV3 partly checked; finishing setup and the badge are still pending).
  Installed the assembled APK without clearing data; granted CAMERA for the hardware test, removed only the test APK.
  Both emulators were shut down with `reboot -p`, and the shared emulator lock released.

### 2026-10-25, `features/camera-lookup` (on `870ad2c5` with the photo store and lookup source before their commit, 0.2.3-870ad2c5, x86_64-only copy), Pixel_10_Pro (API 37)

Scope: the screen path of the bubble after its reads moved behind the lookup source gate; the photo database's
instrumented test. Before: 0.2.3-4cca2cc0. Slovenian active (one quick language), `sl-sample.png`.

Results: all passed, no crash.

- BUB1: pull-out → "Scan started", window captured, app text read (0 nodes), ML Kit draft and cloud final, the entry
  of "je" with its highlight on the word.
- BUB2: moved over "dan" → lookups only, no new scan. BUB3: tap → a new scan, every line flashed, aligned.
- Hold menu: copy paragraph, copy all, Open Screenlate; no flags (one quick language); Back closed it. Dropped at the
  right edge → "Bubble docked".
- CAM9's I part: `:core:photos:connectedDebugAndroidTest`, `PhotoDatabaseTest` 4 of 4. /data had about 0.5 GB free:
  the 105 MB test APK and the 84 MB app copy installed only with `sys_storage_threshold_percentage 1` and
  `sys_storage_threshold_max_bytes 104857600`, both deleted right after. The test package was uninstalled by Gradle.
  The emulator keeps this build with the service enabled.

### 2026-10-22, `claude/jolly-cray-bc8ce9` (`languages/main` 9736102b with both overlay commits cherry-picked, 0.2.3-4cca2cc0), Pixel_10_Pro (API 37)

Scope: the "service is off" notification posted 3 s after a stop and only when the service has not connected again
(owner's decision); UPD6 again, UPD9 (first check, moved to F). Before: group 5a's build 0.2.3-9736102b.

Results: all passed. A scratch script polled `dumpsys notification` (id 3) every 0.5 s while it stopped the service;
times are seconds since the stopping command.

- One `uiautomator dump`: stop, "Service connected" 1.2 s later, "3 s after the stop: … running: yes"; no
  notification at any poll up to 6.1 s.
- Two dumps in a row: stop, connected, stopped again 1.0 s later; the first stop's check found "running: no" but a new
  connection since the stop, so no notification (a check of `running` alone would have posted one); connected again,
  the second check "running: yes"; none at any poll up to 7.1 s.
- Off and on again 1.5 s later (`settings`): none up to 6.1 s.
- Off, not back: none up to 2.8 s, the notification from 3.3-3.4 s on, with the "3 s after the stop: on the enabled
  list: no, running: no" line; turned on, gone by the next poll (0.6-0.7 s).
- With the app's language set to Russian (`cmd locale set-app-locales … ru-RU`, the system in English) the
  notification's title and text were Russian; the setting was cleared after.
- The same process throughout, no crash; turned on again, three overlay windows.
- Not checked on the emulator: the log line with notifications off (unit test only; revoking the permission kills the
  process).

Left on the emulator: 0.2.3-4cca2cc0 instead of group 5a's 9736102b, data kept; the service on, the bubble docked;
the emulator running.

### 2026-10-22, `languages/sl` (on `9736102b` with the Slovenian changes before their commit), Pixel_10_Pro (API 37)

Scope: the first checks of Slovenian (LNG25, OCR41, LK70, POP38, AUD26, ANK32) through the download screen, the
overlay over `testdata/ocr/sl-sample.png` and an image of the ANK32 sentences (Segoe UI, both in the debug viewer)
with cloud recognition, ML Kit and PaddleOCR v5, app text in AnkiDroid's note editor, the Search screen, the popup
page (`popup-eval.mjs`), the Popup and Anki settings and Anki notes into the local "Screenlate Test" deck
({sentence} as Meaning); the engine's instrumented tests. An x86_64-only APK, installed with the storage line lowered
and restored after.

Results: passed, with the deviations below; the ANK32 case names je instead of zaklicala, which no installed
dictionary has. First checks: all 6 cases (moved from N to F). Automated: unit tests, page tests (146), glue tests
(65, 1 skipped), the catalog and converter tests and lint passed; I `LanguageLookupTest` 30 of 30 and the engine's 56
instrumented tests on the emulator.

- LNG25: Slovenia's flag; English definitions by default, the other languages Russian, French, Chinese, Polish and
  Turkish; wordfreq and the transcription ticked; ML Kit the main model, PaddleOCR v5 marked "More accurate",
  Tesseract fast (3.0 MB) and accurate (5.9 MB) unticked. Both PaddleOCR models showed ticked and "Installed", as
  Swedish, Danish and Norwegian had downloaded them. sl-en, sl-ru, sl-ipa and wordfreq sl (53,998 words, built on the
  device) came to 1.6 MB and imported in 1.7-6.2 s; the bubble shows SL.
- OCR41: cloud recognition, ML Kit and PaddleOCR v5 (chosen in the Bubble settings, engines Device) read every word
  and mark of `sl-sample.png`; cloud recognition read » as >> (« kept), the on-device models kept both.
- LK70 (Search screen): vode shows voda ("genitive singular"); delal delati ("singular masculine l-participle"); je
  shows je, then biti ("third person singular present"), ona and jesti; zena and ZENA show žena; se shows se, then the
  dual pronoun forms mi, me, vi and ve, then še. Overlay (cloud recognition): vode, Zena, Delal, Se, Pridi (priti) and
  Šest (the surname, then šest) as there.
- POP38: the font card lists the phone's font, Lexend and PT Serif (downloaded), Literata, Inter, Atkinson
  Hyperlegible Next and PT Sans, with "Only for Slovenian text" and a Slovenian sample sentence; with PT Serif chosen,
  the page's chosen face is PT Serif over U+0-24F, which holds č š ž.
- AUD26: Lingua Libre, Wiktionary, then the phone's voice; the Test button on voda finds Lingua Libre's recording
  (Zupanurska) and none on Wiktionary; holding 🔊 on voda in the popup lists the recording first. Whether the
  emulator's voice speaks Slovenian was not checked.
- ANK32, cloud recognition: the Novak, jabolko and potem notes have "Prof. dr. Novak se je rodil 5. maja 1950 v
  Ljubljani.", "Sadje, t. i. jabolka in hruške, je stalo npr. 5 tis. evrov." and "Potem je propadlo.". Cloud
  recognition made "Mesto je nastalo" a paragraph of its own (the mesto note has only that line) and read »Pridi sem!«
  as >>Pridi sem!<<, so the priti note has ">>Pridi sem!" (plan question 22).
- ANK32, ML Kit (four paragraphs, the guillemets kept): the je note after the quote (added anyway, as je was in the
  deck) has "»Pridi sem!« je zaklicala.", the sem note inside it "»Pridi sem!«", as a word inside a quote gets the
  quote. ML Kit wrote "v 3." as "v3.", so the je note of the Mesto sentence ends at "Mesto je nastalo v3.". App text
  "Mesto je nastalo v 3. stol. pr. n. št. Potem je propadlo." in AnkiDroid's note editor: the nastati note has
  "Mesto je nastalo v 3. stol. pr. n. št.". PaddleOCR v5 gives each line a paragraph of its own and was not used for
  the notes.
- The emulator restarted three times during the run: once with "dm-verity device corrupted", once when its process
  died, and once on an ext4 error of /data (kernel panic; e2fsck then fixed the quota counts). The host was heavily
  loaded and adb's server restarted. After the restarts Google's "Sign in with ease" screen came up over the share
  sheet; it was closed with Back, nothing tapped.

Left on the emulator: Slovenian on and active with wty sl-en, sl-ru, sl-ipa and wordfreq sl; Slovenian's model ML Kit,
engines Both; PT Serif chosen for Slovenian; Slovenian's Anki settings with deck and note type "Screenlate Test",
{sentence} as Meaning, and nine Slovenian notes in that deck; `sl-sample.png` and `sl-anki.png` in the app's
`files/testdata`. The engine's test APK uninstalled, the storage settings deleted, the emulator powered off.

### 2026-10-22, `claude/jolly-cray-bc8ce9` (`languages/main` 26b3d602 with the fix cherry-picked, 0.2.3-f02f6e38), Pixel_10_Pro (API 37)

Scope: the overlay crash when the service comes back (languages plan, request 78), UPD8 (first check, moved to F).
Before: group 4e's build 0.2.3-f2b9cd74 (`languages/kk-answers`), which has the old code.

- Cause, from the app's own log on the emulator: group 5a's two crashes (15:56:30 and 16:03:23) came 5.4 and 6.6 s
  after "Service connected", in that controller's first `applySettings` from the settings flow, while 5a's
  `uiautomator dump` runs kept stopping and reconnecting the service. The system had taken the service's window token
  before the first settings arrived; `attachWindows` added the windows with it.
- Turning the service on and off with gaps of 0.1-3 s on the idle emulator did not crash either build: the first
  settings arrive within the gap between the connect and the stop.
- Deterministic repro (scratch script): `adb forward tcp:8711 jdwp:<pid>`, jdb `-connect
  com.sun.jdi.SocketAttach:hostname=localhost,port=8711`, `stop thread at
  com.vpr.screenlate.overlay.OverlayController:372` (the `attachWindows` call in `applySettings`: line 372 on
  `languages/main`, 328 on `main`), service on; at the hit, `settings delete
  secure enabled_accessibility_services`, 3 s, `clear` and `resume`. Old build: `BadTokenException: Unable to add
  window -- token android.os.BinderProxy@… is not valid` at `attachWindows` ← `applySettings`, the process died, the
  same trace as 5a's. Fixed build: "W OverlayWindows: Window not added, the system is unbinding the service: …", the
  stop report 20 ms later, the same pid on; turned on again, three overlay windows, the bubble docked, a pull-out scan
  over `kk-lines.png` ran (cloud final in 2 s). The line is in the app's own log too.
- Install: the x86_64-only copy (83 MB) needed the storage threshold lowered for the install (settings deleted
  again).

Left on the emulator: 0.2.3-f02f6e38 (`languages/main` plus the fix) instead of group 4e's f2b9cd74, data kept; the
service on, the bubble docked, the home screen; the emulator running.

### 2026-10-22, `languages/kk-answers` (`8d4a08bb`), Pixel_10_Pro (API 37)

Scope: LK66 and LK67 on the search screen (`PROCESS_TEXT` sent with `am start`, Kyrgyz active), ANK30 through the
overlay over a text image (`scripts/text-image.py`, Segoe UI 72 px) in the debug viewer with cloud recognition, DIC35
with the PanLex ky-ru archive built on the PC by this branch's converter, pushed to Download and imported through
Settings → Dictionaries → Import from file. The x86_64-only copy of the debug build of this tree (built before the
commit, so its name says `f2b9cd74`) was installed over group 5a's build (`7f14c5d5`) with the data kept; the storage
thresholds were lowered for the install and deleted right after it.

Results: passed, no fixes. First checks: LK66, LK67, ANK30 (moved from N to F), DIC35 (O).

- LK66: алды shows only алуу ("past", wty-ky-en "to take"), no ал; бизге shows биз ("dative").
- LK67: болушу shows болуу ("verbal noun « his, her, their") first, then бөлүш and бөлүү; иш shows only иш ("work").
- ANK30: the popup for келди showed келүү ("past", cloud recognition); the note added with ➕ (deck "Screenlate
  Test", note type "Screenlate RTL") has Front келүү and Sentence "Президент К. Бакиев Ошко келди." (read from a copy
  of the emulator's local collection). 📖 then opened an empty AnkiDroid browser for the note's `nid:` (AnkiDroid's
  deck filter, as before). The note stays in the test collection, so a rerun meets a duplicate.
- DIC35: imported as "PanLex ky-ru" (ky → ru); "Иран риалы" shows the PanLex line "Иранский риал, иранский риал,
  иранского риала"; Иран shows wty's entry with PanLex's "Ира́н, Пе́рсия" under it.
- Not checked on the device: the Kazakh pronoun skip (glue tests only), the Russian label "имя действия" (resources
  and lint only), the rebuilt PanLex kk-ru, kk-en and ky-en archives.
- Emulator left: Japanese active, Kyrgyz not in quick switching, as found; PanLex ky-ru removed, the pushed archive
  and the test image deleted. The build left installed is this branch's, not group 5a's `7f14c5d5`. The main screen
  still flags 5a's missing wty-sk-en, wty-sk-ru and wty-cs-en and Japanese's AnkiDroid note type, as found. Powered
  off.

### 2026-10-22, `languages/sk` (`7136899d`), Pixel_10_Pro (API 37)

Scope: the checks left from Romanian's run and group 5a's measurements. ANK26's strigat sentence and question 16's
hyphen aim through app text in AnkiDroid's note editor, Romanian active; Paddle v5 latin, PP-OCRv6 small and Tesseract
fast and best over the clean and hard 5a screens through the ocr-compare test APK (x86_64), installed with the
storage line lowered and restored after.

Results: passed.

- ANK26: app text "„Vino aici!” a strigat ea.": the strigat note has the whole sentence, quotes included, and the
  aici note "„Vino aici!”". strigat was in the deck from the cloud run, so its note was added with "Add anyway".
- Question 16 (Romanian hyphen words): app text "El s-a dus acasă.": aimed at s, the popup lists s-a first, then s;
  aimed at a, it lists a.
- OCR over the 5a screens: the group 5a plan's "Engines on the emulator", results in
  `testdata/ocr/compare/new/5a/device`; no recommendation changes. Tesseract best's first run stopped at Basque,
  whose model had not been pushed, and was run again with it.

Left on the emulator: Slovak on and active again; two more Romanian notes in "Screenlate Test" (aici, strigat); the
test APK uninstalled and the storage settings deleted; the emulator powered off.

### 2026-10-22, `languages/sk` (on `7f14c5d5` with the Slovak changes before their commit), Pixel_10_Pro (API 37)

Scope: the first checks of Slovak (LNG24, OCR40, LK64, LK65, POP37, AUD25, ANK29, DIC34) through the download
screen, the overlay over `testdata/ocr/sk-sample.png` and an image of the ANK29 sentences (Segoe UI, both in the
debug viewer) with cloud recognition, ML Kit, Tesseract slk fast and PaddleOCR v5, the Search screen, the popup page
(`popup-eval.mjs`), the Languages and Dictionaries screens and Anki notes into the local "Screenlate Test" deck
({sentence} as Meaning); the engine's instrumented tests. An x86_64-only APK, installed with the storage line lowered
and restored after.

Results: two bugs fixed, a test word replaced (below); the rest passed with the deviations listed. First checks: all
8 cases (moved from N to F). Automated: unit tests, page tests (146), glue tests (63, 1 skipped), the catalog and
converter tests and lint passed; I `LanguageLookupTest` 29 of 29 and the engine's 55 instrumented tests on the
emulator.

- Fixed: robit showed urobiť and zrobiť before robiť. A form-of item keyed without its marks counted as written,
  while robiť itself, keyed the same way, counted as respelled. Form rows keyed so are respelled now (the form-of
  table's version 2; language rules, bug 46). After importing sk-en and sk-ru again: robit shows robiť, urobiť, then
  zrobiť.
- Fixed: "Delete all" on the turned-off Czech card while Slovak was on deleted wty-cs-en as well; the card had shown
  2 dictionaries, 29 MB, and the turn-off dialog 44 MB. Now the dialog says 29 MB and the log "Deleted the files of
  cs: 2 dictionaries"; wty-cs-en stays (language rules, bug 47).
- `scripts/popup-eval.mjs` took the hidden overlay popup's page before the Search screen's; it takes the visible one
  first now.
- AUD25: Lingua Libre has no Slovak recording of voda (the search is empty); the test word is mäso (TomášPolonec's
  recording, listed first). voda lists only the phone's voice. Whether the emulator's voice speaks Slovak was not
  checked.
- OCR40: cloud recognition and Tesseract slk fast read every word. ML Kit read Robit, tažké, Dieta, Ludia and V without
  their marks; each is found through the keys (tazke finds ťažký, Dieta dieťa and diéta, Ludia ľudia). PaddleOCR v5
  (not required) wrote ť and ľ as t' and l': Robit' finds robiť, Diet'a shows Czech dieta ("genitive plural", through
  diet); Dakujem and Ludia were found. PaddleOCR v6 was not tried.
- LK64: robit, ROBIT, byt, kon, dakujem and robil as the case says, in the overlay and the Search screen. The first
  empty results for robil and kon were characters lost while typing; `srch2.sh` (scratch) now checks the field.
- LK65: v and však show cs-en's entries with "Czech"; voda shows sk-en's and sk-ru's only; tu shows ten (accusative
  singular feminine, through tú; question 26); ze shows z and že; vsak, typed without marks, finds nothing, as the
  Czech dictionaries have no keys without marks; with Czech active, v, voda and potom show no tag; with no Czech
  dictionary installed, v shows "No dictionary entries found".
- ANK29: the notes of Novák, jablká (jablko), v and potom have "Prof. Novák sa narodil 5. mája 1950 v Bratislave.",
  "Ovocie, t. j. jablká a hrušky, stálo napr. 5 tis. eur.", "Vznikol v 3. stor. pred Kr." and "Potom zanikol.".
  vznikol and zanikol are in no installed dictionary; v and potom came from cs-en.
- DIC34: with Slovak on and Czech off, wty-cs-en is in Slovak's list (cs → en) and Czech's card under "Turned off"
  shows 2 dictionaries, 29 MB (wordfreq cs, wty-cs-ipa); with both off, Czech's card shows 1 dictionary, 16 MB
  (cs-en), Slovak's 4 dictionaries, 1 recognition model, 21 MB; turning Slovak on again kept its files and made it
  active. The turn-off dialog's delete box is ticked by default.
- The app crashed with `BadTokenException` in `OverlayController.attachWindows` (via `applySettings`) after the
  accessibility service was turned off and on through `settings put`; not fixed here (a follow-up).
- An ANR while the emulator's load was at 10 (`adb reboot` cleared it); the emulator process crashed once (exit 139)
  during DIC34 and was started again.

Left on the emulator: Slovak on and active with wty sk-en, sk-ru, sk-ipa, wordfreq sk and wty cs-en (Czech not turned
on; wordfreq cs and wty-cs-ipa deleted); Slovak's model Tesseract slk fast, engines Both; PT Serif chosen for Slovak;
Slovak's Anki settings with deck and note type "Screenlate Test", {sentence} as Meaning, and four Slovak notes in that
deck; `sk-sample.png` and `sk-anki.png` in the app's `files/testdata`. The storage settings deleted; the engine's
test APK uninstalled.

### 2026-10-22, `languages/main` (`f7161a80`), Pixel_10_Pro (API 37)

Scope: LK63 on the search screen (`PROCESS_TEXT` sent with `am start`), with the dictionaries installed earlier
(wty kk-en and ky-en of release 2026.10.11; the letter rows are the same in 2026.10.22). Before the install the same
words were looked up on the build left there (`7d4b7e52`, without the fix). The x86_64-only copy of the debug build
was installed over the app with its data kept; /data had 617 MB free and the install was refused for storage, so
`sys_storage_threshold_percentage` and `sys_storage_threshold_max_bytes` were lowered for the install and deleted
right after it.

Results: passed, no fixes. First checks: LK63 (moved from N to F).

- Before the fix (`7d4b7e52`): Kyrgyz а showed а, А and then ا ("Cyrillic", the Arabic letter's entry); в showed в,
  В and ۋ ("Cyrillic"); Kazakh о showed о, О, Ө, then و ("Cyrillic", +2 chains) and ө.
- After (`f7161a80`): Kyrgyz а shows а and А only, в shows в and В only; Kazakh о shows о, О, Ө and ө, no و.
- Emulator left as found: Lithuanian active, Hebrew in quick switching, Kazakh and Kyrgyz not (activating a language
  puts it there only while it is active); the accessibility service was stopped by the system after the boot, as
  before the run. Powered off.

### 2026-10-22, `claude/competent-rhodes-6bde05` (on `cbdacc03`, then with the package-name and log fixes before their commit), Pixel_10_Pro (API 37), Screenlate_API30_glide (API 30)

Scope: the app's own log file, the service's stop report and the "service is off" notification (UPD4-UPD7). x86_64
APK on Pixel_10_Pro installed over the data, armeabi-v7a on the API 30 AVD (its image runs ARM through translation;
the app ships no x86 libraries). The service was turned off with `settings put secure enabled_accessibility_services`.

Results: two bugs in the new code fixed (below), everything else passed. First checks: UPD4, UPD6, UPD7 (moved to
F), UPD5 (O). Automated: unit tests and overlay lint passed.

- UPD7: the report after the switch reads "on the enabled list: no", the time since the bubble touch and the windows
  of the last 10 s, and 3 s later "on the enabled list: no, running: no". The launcher read "an app": Android 11 hides
  the system's packages without a launcher icon from apps, so `getApplicationInfo` fails. Fixed: hidden packages with
  a system or phone-maker prefix are named (`hasSystemPackagePrefix`); after it the report names
  `com.google.android.apps.nexuslauncher` and its activity.
- `uiautomator dump` stops the service too: UiAutomation pauses accessibility services, the report says "on the
  enabled list: yes", and the service connects again about 1.5 s later. Do not dump the UI while checking stops.
- UPD6: a heads-up banner without sound, the bubble gone; a tap opens the accessibility list with Screenlate
  highlighted (the details page needs a permission only system apps hold) and the notification goes away; turning the
  service on again cancels an unopened one. With POST_NOTIFICATIONS revoked: no notification and the line "No
  notification about the stop: notifications are off". The refused details page logged a 40-line stack trace into the
  own log at each tap: now one line.
- Exit reasons at the next start: after the revoke, "PERMISSION_CHANGE … "permissions revoked""; after the install,
  PACKAGE_UPDATED; an earlier force-stop reads "USER_REQUESTED (force-stop)". A process with no earlier exits writes
  no marker file.
- UPD4: one .txt, the header, "=== Screenlate log, oldest first ===" from "Process started", then "=== System log
  (logcat) ===" ending with "Log exported". No Japanese characters and no screen text in it after a bubble tap.
- UPD5 (API 30): `no_backup/logs/screenlate.log` created at the first start; after `adb uninstall`, `run-as` says
  unknown package and `/data/data`, `/data/user/0` and `/data/user_de/0` have no app folder, while
  `Download/Screenlate/screenlate-log-….txt` stays.

Not checked: the phone; a stop made by a phone maker's security app.

Emulator state since this run: Pixel_10_Pro keeps its data under this branch's build (0.2.3-cbdacc03 with the fixes)
instead of `abd00763`, the service on, POST_NOTIFICATIONS granted again; the storage thresholds lowered for the
install are deleted. Screenlate_API30_glide has this build freshly installed (data cleared by the uninstall, never
opened), the service off, no files left in Download.

### 2026-10-22, `languages/lt` (Swedish to Latin on `df034779`, Lithuanian on `7d4b7e52`, the fixes below before their commit), Pixel_10_Pro (API 37)

Scope: the first checks of Swedish, Norwegian, Danish, Romanian, Latin and Lithuanian (LNG18-LNG23, OCR34-OCR39,
LK55-LK60, POP31-POP36, AUD19-AUD24, ANK23-ANK28, DIC33) through the download screen, the overlay over
`scripts/text-image.py` images (`testdata/ocr/<lang>-sample.png`, Segoe UI) in the debug viewer with cloud
recognition, ML Kit and the language's "more accurate" model, the Search screen, app text in AnkiDroid's note editor,
the popup page (`popup-eval.mjs`) and Anki notes into the local "Screenlate Test" deck ({sentence} as Meaning); the
engine's `LanguageLookupTest`. An x86_64-only APK, installed with the storage line lowered and restored after.

Results: three bugs fixed, a test word replaced (below); the rest passed with the deviations listed. First checks: all
37 cases (moved from N to F). Automated: unit tests, page and glue tests, the converter's tests and lint passed; I
`LanguageLookupTest` 28 of 28 on the emulator.

- Fixed: Romanian aflată found nothing. The tags of a rule's text (aflat as an adjective) followed the form-of step to
  the lemma, which is a verb (afla). For Romanian only the rule's text needs them now (glue `freeFormLemmas`,
  `candidate_lookup` `free_lemmas`); Norwegian and Kazakh keep them along the step.
- Fixed: "si" in the Search screen showed și before si. The native lookup puts the spellings found as written first,
  but the app's re-sort had no such key; `LookupResult.respelled` carries it and `YomitanSorter` sorts on it after
  the text normalizations. After it: si, then și; sa, then să; dupa finds după; Lithuanian i, then į; ruda, then
  rūda; kasti, then kąsti.
- Fixed: the WikDict no-ru conversion kept WikDict's link markup (`#{…}#`) and HTML entities in 4,990 definitions;
  `scripts/freedict` writes them as plain text and drops definitions that are only a broken template.
- AUD23: Wiktionary has no recording of aqua (only "aqua vitae"); the Latin test word is magnus now (La-cls-magnus.ogg
  first, La-ecc-magnus.ogg first after moving the ecclesiastical region up). The emulator has no Latin voice.
- OCR34: ML Kit read Våra as Vära and år as å; ä is a Swedish letter, so Vära stays and finds nothing. PaddleOCR v5
  read the sample exactly.
- OCR35: ML Kit dropped the 8 of "kl. 8."; PaddleOCR v6 exact. The samples of OCR34-OCR36 gave no ả, so the ả → å
  step was covered by unit tests only.
- OCR36 and ANK25: cloud recognition read » « as >> <<; the råbte note then had "<< råbte hun." (escaped as &lt;).
  With PaddleOCR v5 the note kept "»Kom her!« råbte hun.". ML Kit read læser as læeser.
- LK57: in the overlay statsministerkandidat showed "Compound: statsminister + kunne": the scan reads 16 letters by
  default, and the cut "kandi" is split as kan (kunne). The Search screen splits the whole word into statsminister +
  kandidat.
- OCR37 and ANK26: every engine read Dl. as DI., so the Popescu sentence from a scan lost "Dl."; app text of the
  sentence gave it whole. Cloud recognition gave ş ţ with cedillas (found as ș ț) and ” as ", and the strigat
  sentence then started at the quote (' " a strigat ea.'); not rechecked with the right quote on the device.
  PaddleOCR v5 lost the ț of țară ("în ară"); ML Kit gave tară and Tara, which find țară.
- OCR38: both on-device engines read the I of Iulius as l (lulius finds nothing); ML Kit read ō as õ, which is found.
  LK59: amicis shows amiciō before amīcus in the overlay and the Search screen (Latin has no frequency dictionary).
- OCR39: ML Kit dropped the marks of Ū and į rather than giving Ü or ị (Ukis finds ūkis). The foreign é was checked
  through app text in AnkiDroid's note editor (José keeps its é), not a browser.
- LK58: casă shows casă, casa and cașă, no cas; O shows o and no Cyrillic entry.
- LNG23: ticking Tesseract lit fast on the download screen made it the chosen model.
- AUD24 and AUD20: the emulator has no Lithuanian voice; the Norwegian recording's playback left no started-state line
  in the log.
- ANR during a Device scan of the Danish sample: the emulator ran out of swap (9 MB free; the main thread in
  `DexFileVerifier`), then the NFC process restarted every 3 s; `adb reboot` cleared both. The UWB HAL aborted once
  during the Norwegian part (emulator noise).
- DIC33: FreeDict no-ru (sourceLanguage no) and wty-nn-en (nn) were both listed under Norwegian, and "bank" showed
  entries from wty-nb-en, wty-nn-en and FreeDict no-ru.

Left on the emulator: the six languages on, Lithuanian active; models sv PaddleOCR v5, no PaddleOCR v6, da PaddleOCR
v5, ro, la and lt ML Kit; fonts chosen for each; FreeDict no-ru and wty-nn-en imported. Deleted for space (owner,
2026-10-20): the Russian and English dictionaries and models, wty-pt-ipa, wty-vi-ipa and wordfreq pt, it and de. The
storage settings deleted, the app locale reset, about 0.57 GB free; the engine's test APK uninstalled.

### 2026-10-22, `languages/checks-2` (on `195fd926` with this branch's fixes before their commit), Pixel_10_Pro (API 37), Screenlate_API30_glide (API 30)

Scope: the checks earlier runs left open: the audit's Czech and Arabic fix (LK54 and ANK22, new), ANK14's Chinese
part, LK40, the interrupted dictionary reset (DIC9), DIC20's setup and home paths, DIC20-DIC22, DIC24's resume after
Clear cache, DIC25; the 2026-10-18 run's open items (a phrase through the selection menu, LNG6 on mobile data, the
DIC2 ring, FR6 with a deleted bundled dictionary, NF4 on API 30, the v6 download); Tesseract's accurate models for
Arabic and Hebrew; the PanLex kk-en file (DIC28). An x86_64-only APK; the app's data was backed up with `run-as tar`
at the start and restored at the end. Network cases used `svc wifi|data` and `adb emu network speed umts`, which
slows mobile data only (not the Wi-Fi). Model updates came from a catalog written to `files/catalog-v2.json`: the
app's fetch of the repository's copy gets 404 on `main`, so the written copy stays.

Results: two bugs and a stale text fixed (below), everything else passed. First checks: LK54, ANK22, LK40, DIC24,
DIC25 (moved to F); new case DIC32 (F). Automated: unit tests, page tests and lint passed.

- LK54 (Arabic active, app text in AnkiDroid's note editor): کتاب finds كتاب, والکتاب finds الكتاب and كتاب, علی
  finds على and علي, کبیر finds كبير, فی finds في. Persian active: کتاب finds Persian کتاب, علی Persian علی.
- ANK22: 9ec4c279's fix did not work on the device: cloud recognition gives "n", ".", "I", "." as four words and ML
  Kit "n.I." as one, and the rule matched word by word. Fixed (`CzechSupport.ocrLines` over the joined words, "n.l"
  and "př.n.l" abbreviations). After it: cloud recognition gives "Roku 300 př. n. l. se narodil." as the sentence;
  ML Kit on lines 84 px apart (Arial 64) puts the l back across the line break, but glues "l.a" (the period and the
  next word); app text "Roku 300 n. I. Potom odešel." keeps the I, and the sentence of odešel is "Potom odešel.".
  Cloud recognition made each line of that image a paragraph of its own.
- LK40: with lines 84 px apart, aiming at impor- shows important first and at tant important then tant, both halves
  highlighted with the hyphen; "COVID-19-" at a line end is not joined (ML Kit breaks the paragraph there), "dash -"
  stays apart. With text-image.py's 160 px each line was a paragraph: no entries at impor-. `PITCH` added to it.
- ANK14: the Chinese profile's + menu lists the four markers with pinyin hints ("Pinyin as text: 你[nǐ] 好[hǎo]").
- LK6: "потому что" selected in AnkiDroid's note editor, "Look up in Screenlate" opens the search screen with the
  phrase entry потому́ что first. With the release and the debug build installed, the menu has two identical items.
- FR6: v0.2.3 (`3f2123c`) installed fresh, the disclosure agreed, the bundled dictionaries installed, Kanjium
  deleted; an upgrade to this build asks nothing, keeps JMdict, Jiten and KANJIDIC, and Kanjium stays deleted.
- DIC9: with `dictionary_reset_attempts` 2 at start: "not tried again", Home's dialog "Dictionary reset did not
  finish" and the Dictionaries card. Its text sent the user to the reset button removed on 2026-10-16: fixed in 14
  languages (delete what is not needed, download what is missing), and the stale sentence left `docs/usage.md`. With
  1: run 2 deleted 18 dictionaries (1 GB) in 450 ms and finished in 2.4 s; the activity's first frame came 4 s after
  the launch, so the spinner of the deleting phase cannot be seen on the emulator.
- DIC2: Jitendex's ring spun with "Downloading", filled with "Downloading · 0%-93%", spun with "Importing", then ✓;
  ✕ in the first second did nothing; the task card named the catalog title. JMnedict queued behind it showed
  "Waiting in the queue".
- LNG6 on mobile data only: the Chinese setup said "Download (100 MB)", then asked "Download over mobile data?".
- DIC21: two imports with their own notifications; a finished download waited for the other import. Cancel in a
  notification during a download cancelled that task and deleted its part; a Cancel that arrived in the last
  milliseconds of a 16 s import came too late (the dictionary installed, then "cancelled by the user" was logged).
- DIC22: "Waiting (3): CC-CEDICT Hanzi, PaddleOCR v6, text detector, PaddleOCR v6" with "Show the list" and "Cancel
  all"; the detector's name, which has a comma, reads like a fourth item.
- DIC20: with the network off, the Serbian setup after the Croatian one queued only its model ("is queued already"
  three times); Home's "Download again" for a dictionary whose directory was removed queued one task, and a second tap
  after Home showed the card again logged "is queued already". DIC23: the shared card had "Waiting for a network".
- DIC24: mobile data off 5 s into a 106 MB download: 41 MB stayed in `no_backup/dictionary-downloads/`; Clear cache in
  the system settings kept it; with data back the download went on from 40,291 KB and installed. A planted
  `cache/dictionary-downloads/old.zip` survived an import in the same process and went with the first import after a
  restart.
- DIC25: during the Chinese install Home showed the progress and no "no dictionary with definitions" card. With only a
  model update left (mobile data at umts speed), Home's service card had no "Installing dictionaries…" and Settings
  showed the dictionary count. "Check for updates" listed "PaddleOCR v6 · Japanese · Chinese · English · Croatian ·
  Serbian · 18 MB" and "Tesseract, accurate · English · 15 MB".
- OCR20: PaddleOCR v6 and its detector came with the Chinese setup, loaded, and Chinese warmed up in 372 ms.
- DIC32: wty-zh-ipa (4.4 million tagged transcriptions) ran out of memory reading its tags ("Reading transcription
  tags failed", OutOfMemoryError, heap 192 MB): the dictionary installed without them. Fixed with a second file
  format (a packed long per transcription, sorted chunks merged into the file, mapped by lookups). After it the
  import wrote a 36 MB file in 62 s with the heap under 80 MB, 中国's marker gives "(zh_pron, Pinyin, Mandarin)
  Zhōngguó", and English water still shows the tags of its first-format file.
- DIC28: PanLex kk-en imported in 0.8 s; қабылдады → қабылдау "acceptance, adoption, anteroom, host, hosting,
  receptionist, accept"; кітап "book, bookseller, bookshop" (PanLex's related words come with the translations).
- Tesseract accurate: ara 13 MB, a final pass of 4.3 s against fast's 1.5 s, the same words and the same garbled
  Latin; heb 3.7 MB, 1.9 s, the same quirks as fast (`ai/notes/ocr-engines.md`).
- NF4 on API 30 (this build installed over 0.2.2 on `Screenlate_API30_glide`): pulling the docked bubble out made 12
  relayouts with no `android.anim.lf` frames, no `SurfaceAnimationRunner` and no `LocalAnimationAdapter`.

Not checked: a network voice for LK26 (the emulator has local voices only); DIC23's file import; the phone.

Emulator state since this run: Pixel_10_Pro has the data from before the run (31 languages on, Kyrgyz active, Hebrew
starred, the bubble hidden) under this branch's build instead of `311e285c`; the storage thresholds lowered for the
installs are deleted. Screenlate_API30_glide has this build in place of 0.2.2, still at its first-run language screen,
the service off.

### 2026-10-22, `claude/download-audio-fixes-a83144` (on `32987ce2` with the download and audio fixes before their commit), Pixel_10_Pro (API 37)

Scope: a download's network loss as a wait for the network, also at the moment of its last retry, and tasks that
ended never running again (DIC3, DIC23, DIC29, DIC30); the queue's input merger (DIC31, found during the run); Home's
audio card per lookup language (AUD2, AUD18). An x86_64-only APK with the storage threshold lowered for the install
(both settings deleted afterwards). Break-offs came from a host socket that accepts and never answers set as the global
proxy (`settings put global http_proxy 10.0.2.2:8899`); `adb emu network speed 1500` did not slow the emulator's
Wi-Fi (14 MB in 5 s).

Results: every case passed after the fix of the input merger below. First checks: DIC29, DIC30, DIC31 (moved to F),
AUD18 (moved to O). Automated: unit tests 1317, page tests 145, lint passed (no issue in the changed files).

- DIC29: Wi-Fi and data off 3 s into the 14 MB transcription download: WorkManager stopped it with reason 7, logged
  "waits for a network, then runs again"; the task card "Waiting for a network: Transcription (Wiktionary)", the
  catalog card "Waiting for a network"; 3.9 MB stayed in `no_backup/dictionary-downloads/`. With the network back
  after 60 s: run 2, "3812 KB already there", installed. No retry counted.
- DIC30, stop first: network off 19.5 s into the third retry's read: WorkManager's stop (reason 7) came 0.2 s before
  the read timeout; the card "Retrying the download (3 of 3)" with "Waiting for a network". With the proxy set, new
  networks do not pass validation (the probe goes through it), so the task waited; the proxy removed, the network
  validated and the same task ran again (run 2) and installed.
- DIC30, failure first: network off 20.4 s into the third retry's read: "the network went away during the download
  (SocketTimeoutException); waits for it" at the timeout, WorkManager's stop 19 ms later; the card as above; with the
  network back the task installed. No "download stopped".
- DIC3: two downloads through the proxy broke off four times each (retries 30 s, 1 min, 2 min apart, the 2 s network
  check after each timeout), then "download stopped — connection lost" with Cancel and Resume. Neither ran again
  across two network losses. Cancel removed its card (nothing was downloaded, so no partial file).
- DIC31: Resume on the "Wiktionary (es–tr)" card downloaded `wty-es-tr-gloss.zip` under the name "Wiktionary glossary
  (es–tr)": the job was queued after the stopped glossary download in the same chain, and WorkManager's default input
  merger laid that job's output (URL, archive, name, entry) over the new job's input. Fixed with `OwnInputMerger`;
  with it, a download queued after the stopped es–tr job (its prerequisite in the WorkManager database) downloaded and
  installed its own `wty-es-ja-gloss.zip`.
- AUD2, AUD18: with Kyrgyz and Hebrew on and the proxy set, 🔊 on китеп (Kyrgyz) gave a "Kyrgyz" card with Lingua
  Libre and Wiktionary; 🔊 on כלב (Hebrew) added a "Hebrew" card; no other language got one (before the fix both would
  have been listed under Hebrew, the first language with those sources). "Audio settings" on the Kyrgyz card opened
  the Anki page with Kyrgyz chosen. Without the proxy, 🔊 on китеп played and the Kyrgyz card went; Hebrew's stayed.

Not checked: DIC20-DIC22 and DIC25 (their code did not change); the in-run 30 s wait for the network (WorkManager
stopped every job first); a WorkManager stop landing after the job's result (covered by unit tests); whether a running
glossary download's card shows the catalog's localized title (the wrong job above showed the name from its input,
"Wiktionary glossary (es–tr)"); the phone.

Emulator state since this run: the debug build of this branch; the Spanish dictionaries downloaded for the checks
deleted again; AnkiDroid not opened; the storage settings and the proxy removed.

### 2026-10-22, `languages/kk-followups` (on `11dd9eb8` with the changes of `29f61dc2` and `6bbe1e93` before their commit), Pixel_10_Pro (API 37)

Scope: every engine of Kazakh and Kyrgyz on the OCR comparison screens, with the harness of `languages/ocr-compare`
(`OcrCompareTest` with the models streamed into its files folder; Kyrgyz added to it locally as Cyrillic with `kir`,
its screens made with `PageActivity` and `collect.mjs --web`, not committed there); LK51-LK53, LK61, LK62 and DIC28 through the
overlay over rendered images (64 px, PT Sans, `TextImage.java`; `kk-lines3.png` and `ky-lines2.png` new) in the
debug viewer with cloud recognition, the search screen opened through the text selection action (`PROCESS_TEXT` sent
with `am start`), and PanLex ky-ru imported from a file. The harness needed more of /data than was free, so
`sys_storage_threshold_percentage` and `sys_storage_threshold_max_bytes` were lowered for the run and deleted
afterwards; the harness was uninstalled. The x86_64-only debug build was installed over the app with its data kept.

Results: passed, no fixes. First checks: LK61, LK62 (moved from N to F).

- Engines: the numbers are in the group 4e plan, "Measurements (2026-10-22)". Paddle ran on ONNX Runtime (the
  harness APK has no MNN library).
- LK61 (`kk-lines3.png`): сондай-ақ aimed at ақ → -ақ (particle, then suffix), then ақ and хақ; келді-ау aimed at
  ау → -ау first; пікірінше → пікір ("his, her, their « manner"); болуы → болу ("his, her, their"); тауып → табу
  ("converb").
- LK62 (`ky-lines2.png`): Түштук → түшүү ("past « 1st pl."), then түштүк; өзүнчө → өз ("his, her, their «
  manner"); 19-декабрь aimed at декабрь → декабрь only, no -де.
- LK52: сондай-ақ in `kk-lines.png` → -ақ first; ас-су aimed at су → ас-су, су; бала-шағамен aimed at шағамен →
  бала-шаға only.
- LK51 (part): Қазакстан → Қазақстан; он → он, оң, өн, өң; 1990-ға → -ға. Search screen: "оң жақ" → оң only;
  кiтаптар with a Latin i → кітап ("plural").
- LK53 (part): 2007-жылы → жыл; Кол → кол, then көл; көл → көл; Ата-энем aimed at энем → ата-эне, эне; алды → ал
  ("accusative"), алуу ("past"). Search screen: көчодө → көчө ("locative singular").
- DIC28 (ky-ru only): imported from Download as "PanLex ky-ru" (ky → ru); алды → алды "лицево́й" (PanLex only), ал
  with "он, она́", алуу with "держа́ть, брать, взять, хвата́ть, схвати́ть, вычитание"; көчө shows "у́лица" under its
  wty entry. The dictionary and the pushed file removed afterwards.
- System: the install took 277 s at a load of about 26, and system_server crashed and restarted after it; the
  accessibility service then stayed unlisted ("Installed services" empty) until `adb reboot`, after which it bound
  when the app was launched. The bubble was hidden after the restart and brought back for the checks.
- Process: dragged from the dock, the bubble lands about 40 px right of the finger, and the next aim from where the
  finger was released starts off by as much (one aim hit the period after келді-ау and was repeated from the bubble's
  place). A tap on the launcher's home screen, meant to close the popup, opened Google's account sign-in; it was left
  with Back without entering anything. Chrome's first-run screen was passed without signing in, usage statistics off.
- Emulator left with Kyrgyz active and Hebrew starred, as before. Powered off.

### 2026-10-22, `languages/km-answers` (on `69da4bbf` with the busy-screen line before its commit), Pixel_10_Pro (API 37)

Scope: every engine of Thai and Khmer on the OCR comparison screens, with the harness of `languages/ocr-compare`
(`OcrCompareTest` with the models streamed into its files folder, `ai/notes/build-environment.md`); LNG17. The
harness needed more of /data than the 0.4 GB left, so `sys_storage_threshold_percentage` (1) and
`sys_storage_threshold_max_bytes` (100 MB) were lowered for the run and deleted afterwards; the harness was
uninstalled. The x86_64-only debug build was installed over the app with its data kept.

Results: passed, no fixes. First check: LNG17 (to O).

- Engines: the numbers are in the group 4c plan, "Measurements"; the errors match the run of 2026-10-16/17 within
  0.2 points. Paddle's Thai model ran on ONNX Runtime (the harness APK has no MNN library).
- Bubble → On-device model: Thai and Khmer show the line under Tesseract fast (after "Recommended · Installed ·
  size") and under Tesseract accurate (after the size); Georgian shows none.
- Download screen: Khmer turned off with its files deleted (owner's permission for checked languages, group 4c
  question 13) and on again: the line under both Tesseract models, after the title and before the size and license.
  Downloaded again (2.5 MB), Khmer back to Ready.
- Emulator left with Kyrgyz active as before, Thai and Khmer turned on (Khmer now listed last), /data 333 MB free.
  Powered off.

### 2026-10-20, `languages/kk` (PanLex conversions, on `8c869164` with the converter before its commit), Pixel_10_Pro (API 37)

Scope: DIC28. The archives PanLex ky-en, ky-ru and kk-ru built on the PC from the mirror at revision `0422f0e60f39`,
pushed to Download and imported through Settings → Dictionaries → Import from file; lookups on the search screen
with the Kyrgyz and the Kazakh profile. Removed afterwards (the dictionaries and the pushed files), as they are not
catalog entries. Gboard's Kazakh layout on the search field.

Results: passed, no fixes. First check: DIC28.

- Import: ky-en 486 ms (engine 424 ms), ky-ru 545 ms, kk-ru 435 ms; languages read from the index (ky → en, ky → ru,
  kk → ru).
- Kyrgyz: азыр → now / сейчас and же → or / или, либо (no wty entry for either); алды → алды "facial, лицевой" as
  written, then ал and алуу, each with the PanLex line under its wty definition (алуу: "hold, take, subtraction",
  "держать, брать, взять, хватать, схватить, вычитание"). Stress marks render (лицево́й).
- Kazakh: қазір → wty "now, at present" with PanLex "сейчас, ныне, теперь" under it; қабылдады → қабылдау ("past"),
  PanLex "принятие, принимать".
- Not reached: боюнча, чейин, кабыл and аркылуу have PanLex expressions but no Russian or English translation.
- Gboard 18.4: its Kazakh (Cyrillic) layout, added for the check and removed afterwards, is the Russian layout
  with ә ғ қ ң ө ұ ү һ і on long presses of а г к н о у х ы; text typed without them is in the Russian twins,
  which the keyed lookup matches (Қазакстан in the Kazakh run).

### 2026-10-20, `languages/kk` (Kyrgyz, on `61dfb28d` with the Kyrgyz changes before their commit), Pixel_10_Pro (API 37)

Scope: Kyrgyz (LK53, OCR33, POP30, AUD17, ANK21, DIC27, LNG16) through the download screen, the overlay over a
rendered image (64 px, PT Sans, `TextImage.java`, `testdata/ocr/ky-lines.png`) in the debug viewer with cloud
recognition and offline with PaddleOCR and Tesseract, the search screen, the popup page (`popup-eval.mjs`), the
Popup settings' font card, audio, a note in the emulator's local AnkiDroid collection (never synced); the lookup
engine's LanguageLookupTest (22/22) from an x86_64-only test APK run with `am instrument`. x86_64-only build
installed over the app with its data kept.

Results: passed, no fixes. First checks: LK53, OCR33, POP30, AUD17, ANK21, DIC27, LNG16.

- Setup: Kyrgyz ticked wty-ky-en, its transcriptions, the Leipzig list and PaddleOCR (installed already, shared with
  Kazakh); Tesseract kir fast added for the offline check. The Leipzig archive (19.0 MB) downloaded in 4.7 s and gave
  100,000 words in 2.8 s (7.9 s in all); wty-ky-en 12.0 s (its download 9.3 s); the transcriptions 1.4 s; Tesseract
  kir 2.4 s; about 14 s for the whole setup. Flag kg, bubble Ң.
- Lookups (cloud recognition): 2007-жылы → жыл; китепкана; Китептерибизде → китеп ("plural « our « locative");
  барасыңбы → баруу ("present « 2nd sg. « question"); Кол → кол, көл; көл → көл; Үйлөрдө → үй ("locative plural");
  энем → ата-эне, эне; иштейт → иштөө ("present « 3rd person"); алды → ал, алуу. Lookups took 3-30 ms.
- Түштук (the first ү kept, the second typed у) finds түшүү ("past « 1st pl."), not түштүк "south": the past ending
  in its twin spelling (-тук for -түк) covers the whole word, and the keyed lookup runs only when the text as written
  finds less. Overnight question 15 (plan). The keyed lookup itself works: on the search screen көчодө → көчө.
- алды shows ал "he/she/it" ("accusative") before алуу ("past"): a nominal ending matches any entry, a pronoun too,
  and ал ranks higher in the Leipzig list; the pronoun's accusative is аны. Overnight question 16 (plan).
- Offline (Wi-Fi and data off, a tap on the bubble for a new scan): PaddleOCR's draft in 0.5 s and its final scan in
  2.6 s; Tesseract kir fast's final scan in 1.9 s; every word above found over both. The Kyrgyz model set back to
  PaddleOCR afterwards.
- Search screen: көчодө → көчө ("locative singular").
- Audio: holding 🔊 on китеп lists Lingua Libre's recording, Wiktionary's Ky-китеп.ogg and the text to speech; the
  recording plays.
- Anki: the Kyrgyz profile set to the deck "Screenlate Test" and the note type "Screenlate RTL" (Front, Back, Reading,
  Sentence); the note for алды has Front алуу and Sentence "Ал 1991-ж. жайында өрүк ж. б. алды.".
- Fonts: the card lists the phone's font, PT Serif (downloaded during the Kazakh run), "Only for Kyrgyz text", and
  Literata, Inter and PT Sans to download; PT Serif, chosen, sets the Kyrgyz text and the English glosses and draws
  ң ө ү Ң Ө Ү; set back to the phone's font.
- Process: the bubble rests where it is released; aims started from its dock position after it had moved were
  system Back gestures from the right edge and closed the viewer. Each aim now starts on the bubble.

### 2026-10-20, `languages/kk` (Kazakh, on `c342b75f`, before the fixes' commit), Pixel_10_Pro (API 37)

Scope: Kazakh (LK51, LK52, OCR32, POP29, AUD16, ANK20, DIC26, LNG15) through the download screen, the overlay over
rendered images (64 px, PT Sans, `TextImage.java`, `testdata/ocr/kk-lines.png` and `kk-lines2.png`) in the
debug viewer with cloud recognition and offline with PaddleOCR and Tesseract, the search screen, the popup page
(`popup-eval.mjs`), the Popup settings' font card, audio, a note in the emulator's local AnkiDroid collection (never
synced); the lookup engine's LanguageLookupTest (20/20) from an x86_64-only test APK run with `am instrument`.
x86_64-only builds installed over the app with its data kept.

Results: passed after two fixes. First checks: LK51, LK52, OCR32, POP29, AUD16, ANK20, DIC26, LNG15.

- Setup: Kazakh ticked wty-kk-en, its transcriptions, the Leipzig list and PaddleOCR; Tesseract kaz fast added for the
  offline check. wty-kk-en took 4.7 s; the Leipzig archive (18.3 MB) downloaded in 4.1 s and gave 100,000 words in
  3.5 s (8.5 s in all); the transcriptions 4.3 s; Tesseract kaz 1.3 s. Flag kz, bubble Қ.
- Lookups (cloud recognition): Казакстанның → Қазақстан ("genitive"); Қазакстан → Қазақстан, then қаз (the keyed
  lookup); суды → су ("accusative"); кітаптарымызда → кітап ("plural « our « locative"); жаздық → жаздық, жазу;
  қазанда → қазан ("locative"); он → он, оң, өн, өң; 1990-ға → -ға; дейін; ас-су aimed at су → ас-су, су; сондай-ақ
  aimed at ақ → ақ. The first lookup after a scan took 15 ms.
- Fix: оқыды showed оқ through -ы and -ды beside оқу: the Turkic rules took the genitive and accusative endings in д
  and т after any letter. They now follow only a consonant (`/C`, the grammar's `consonants`, with Kazakh у among
  them: суды → су); оқыды finds only оқу (glue.test.mjs).
- Fix: бала-шағамен aimed at шағамен showed бала after бала-шаға: the part шағамен found nothing, and the first part's
  words came back. They now stay out whenever the whole word covers the aim (EarlierStartsTest).
- Offline (Wi-Fi and data off): PaddleOCR read the screen in 1.2 s with the Kazakh letters right; Tesseract kaz fast in
  2.6 s, with көлдің, қаһарман and Қазақстан right.
- Search screen: кiтаптар with a Latin i → кітап; "оң жақ" → оң only.
- Audio: holding 🔊 on кітап lists Lingua Libre's recording, which plays; the emulator has no Kazakh voice. With
  auto-play on, every word without a recording shows "No text-to-speech voice" (as in other languages; follow-up).
- Anki: the note for қазанда in "Ол 2007 ж. қазанда келді." has Front қазан and the whole sentence.
- Fonts: the card lists Literata, Inter, PT Serif and PT Sans, "Only for Kazakh text"; PT Serif, downloaded and
  chosen, sets the Kazakh text and the English glosses (a Cyrillic language's range includes Latin, as for Russian).
- Process: one launch with `am start -S` force-stopped the app and turned its accessibility service off; it was turned
  on again through `settings`, and the app was launched with `monkey` since (build-environment.md).

### 2026-10-20, `languages/fa-he-followups` (on `7ca72612`, before its commit), Pixel_10_Pro (API 37)

Scope: the owner's answers to group 4d's overnight questions 3, 4 and 6: Iranian Persian transcriptions first, the
spoken Persian endings kept after measuring, no form tags beside a plain word. The lookup engine's LanguageLookupTest
(20/20) from an x86_64-only test APK run with `am instrument`; the overlay over rendered images in the debug viewer
with cloud recognition (Hebrew `he-lines.png`, Croatian `sr-latn-sample.png`, Persian `fa-spoken.png`, new); the
popup page through `popup-eval.mjs`, in English and with the app in Russian (`cmd locale set-app-locales`, set back
afterwards). x86_64-only build installed over the app with its data kept; no dictionary deleted.

Results: passed, no fixes. The new parts of LK36, LK49, LK50 and POP27 checked; the rest of those cases not re-run.

- Hebrew: שלום → שלום with no rule chain and no "singular construct" (transcription [ʃaˈlom], +8), then שילם.
- Croatian: Kuća → kuća with no chain and no "+N"; kući → kući, then kuća with "dative/locative singular".
- Persian: کتابمون → کتاب through "1st pl." with the Iranian transcription first (IR [kʰʲe.t̪ʰɒ́ːb̥], then
  /ki.ˈtaːb/, AF, Hazaragi and TJ behind "+4"), then کاتب ("1st pl. « plural"); خودشون → خود ("3rd pl."); دومین
  → دوم ("ordinal"), then دویدن (دوم as its first person aorist); سیزدهمین → سیزدهم ("ordinal"); خوبه finds
  nothing; زیتون → زیتون with no chain, then زیستن and زیویدن through "2nd pl. « present stem".
- With the app in Russian the chains read "1-е л. мн. ч." and "порядковое", and a tap on the latter opens
  "порядковое числительное перед существительным (دومین от دوم)".
- Emulator left with Persian active, the app in the system language.

### 2026-10-20, `languages/he` (Hebrew, on `ff7f685a`, before its commit), Pixel_10_Pro (API 37)

Scope: Hebrew (LK50, POP28, AUD15, ANK19, OCR31) through the download screen, the overlay over a rendered image
(64 px, the phone's Noto Sans Hebrew with Roboto, `TextImage.java`, `testdata/ocr/he-lines.png`) in the debug viewer
with cloud recognition and with Tesseract offline, the popup page (`popup-eval.mjs`) in English and Russian, the Popup
settings' font card, audio, a note in the emulator's local AnkiDroid collection (never synced); the lookup engine's
LanguageLookupTest (20/20) and the overlay's SystemFontFilesTest (10/10) instrumented tests, both from x86_64-only test
APKs run with `am instrument`. x86_64-only builds installed over the app with its data kept; the German and Italian
transcription dictionaries (248 MB) were deleted first to make room (owner's permission of 2026-10-20).

Results: passed after three fixes. First checks: LK50, POP28, AUD15, ANK19, OCR31 (moved from N to F).

- Setup: Hebrew ticked wty-he-en, its transcriptions, wordfreq he and Tesseract heb fast. wty-he-en took 8.0 s (engine
  import 1.6 s) with a form-of table of 235,424 forms; wordfreq he 100,000 words in 4.1 s; the transcriptions 59 ms.
  Flag il, bubble א.
- Lookups (cloud recognition): והבית → הבית ("and"), then בית ("the « and"); כשהגעתי → הגיע ("when « first person
  singular … past"); לירושלים → ירושלים ("to"); הכלבים → כלב ("plural definite masculine"); ושמהבית → הבית ("from «
  that « and"), then בית as the form הבית; ארה"ב → ארה״ב, ארצות הברית; וצה"ל → צבא הגנה לישראל; ג'ירפה → ג׳ירפה;
  קוֹרֵא → קורא, קרא; שלום → שלום with "singular construct" (plan question 6), then שילם; היום → היום, יום.
- Fix: cloud recognition gave the line "קניתי iPhone 15 ב־3,500 ₪ היום." as ".ב־ 3,500 ₪ היום iPhone 15 קניתי" (the
  word boxes right), so the scan text from היום ran into "iPhone 15 קניתי" and from קניתי held only the word; lines out
  of the order of their boxes are now put in that order (LensProtocolTest), and the scan texts read "היום" and
  "קניתי iPhone 15 .ב־3,500".
- Fix: 933 of the Hebrew transcriptions are written `//a/ [b]/` or `//a//`; they showed as one with the extra slashes
  and are now split (TranscriptionsTest).
- Fix: Hebrew's `{reading}` is the word itself, Hebrew text, but "Fill in suggested templates" left it without the
  span; it now gets `<span dir="rtl">{reading}</span>` (FieldTemplateTest, HebrewSupportTest). The note added before
  the fix (הכלבים) has Front כלב, Reading כלב and Sentence `<span dir="rtl">כשהגעתי לירושלים הכלבים נבחו.</span>`.
- Cloud recognition: the gershayim and the geresh come as ASCII " and ', the maqaf as a word break (ובית חולים →
  בית חולים). Offline, Tesseract heb fast read the screen in 1.4 s ("Lens: OfflineException"): והבית → הבית, בית;
  ארה"ב → ארה״ב; ג'ירפה → ג׳ירפה; קוֹרֵא as קורא; ובית-חולים with a hyphen → בית; צה״ל read as וצהייל; "iPhone"
  garbled.
- Rule names: the chain of בית from והבית reads "the « and" in English and "артикль « и" with the app in Russian
  (`cmd locale set-app-locales`, set back afterwards); a tap opens "and (ואני, והבית)" / "определённый артикль (הבית)".
- Page: `dir="rtl"` on the headword; the transcriptions of כלב start with /ˈke.lev/.
- Fonts: the card lists Heebo and Frank Ruhl Libre, "Only for Hebrew text"; Heebo, downloaded and chosen, loads on
  the page as "Screenlate Chosen" (U+0590-05FF among its ranges); the phone's Noto Serif Hebrew loads as "Screenlate
  Serif".
- Audio: holding 🔊 on כלב lists Lingua Libre's recording, Wiktionary's He-כֶּלֶב.ogg and the text to speech; on שלום
  Lingua Libre's two recordings and the voice. Wiktionary's file played; the voice item used Google's he-il voice.
- Emulator left with Hebrew active, Heebo chosen, the app in the system language, airplane mode off.

### 2026-10-20, `languages/fa` (Persian, on `22b22cdf`, before its commit), Pixel_10_Pro (API 37)

Scope: Persian (LK49, POP27, AUD14, ANK18, OCR30) through the download screen, the overlay over a rendered image
(64 px, Vazirmatn with Roboto, `TextImage.java` drawing the lines right to left, `testdata/ocr/fa-lines.png`) in the
debug viewer with cloud recognition and offline, the popup page (`popup-eval.mjs`) in English and Russian, the Popup
settings' font card, audio, a note in the emulator's local AnkiDroid collection (never synced); the lookup engine's
LanguageLookupTest (19/19) and the overlay's SystemFontFilesTest (9/9, with the Arabic serif for Persian)
instrumented tests, both from x86_64-only test APKs run with `am instrument`. x86_64-only builds installed over the
app with its data kept.

Results: passed. First checks: LK49, POP27, AUD14, ANK18, OCR30 (moved from N to F).

- Setup: Persian ticked wty-fa-en, its transcriptions and wordfreq fa; no recognition section. wordfreq `small_fa`
  was built in 1.1 s (29,190 words); wty-fa-en took 5.1 s (engine import 914 ms) with a form-of table of 105,987
  forms; the transcriptions 83 ms. Flag ir, bubble پ.
- Lookups (cloud recognition): کتاب‌ها (with the non-joiner) → کتاب through -ها, then کاتب; خواهم after a separate
  می → خواستن ("first person singular present indicative"), matched "می خواهم"; ترین after بزرگ, and بزرگ itself →
  بزرگ through -ترین, matched "بزرگ ترین"; كتابهایشان with the Arabic kaf → کتاب through -ها and -یشان; ستارگان →
  ستاره; خانهٔ → خانه ("singular ezafe"); نمی‌دانم shows the phrase first, then دانستن through نمی-; "نگاه کن" → نگاه
  کردن.
- Rule names: the chain of کتابهایشان reads "pl. « 3rd pl." in English and "мн. ч. « 3-е л. мн. ч." with the app in
  Russian (`cmd locale set-app-locales`, set back afterwards); a tap on the second opens "third person plural after
  a vowel: their (پایشان)" / "3-е лицо мн. ч. после гласной: их (پایشان)". The first pass, before the owner chose
  names, showed the affixes (-ها « -یشان) left to right with the hyphens on the wrong side; a right-to-left chain was
  built for it and taken out again once every rule had a name in the app's language.
- Page: `lang` fa; the Latin reading after the word (کتاب kitāb), grey, at the text size, `dir="ltr"`, left out of a
  copied selection; transcriptions in the dictionary's order (/ki.ˈtaːb/ first, then +4).
- Cloud recognition: lines in reading order; the non-joiner comes back dropped (کتاب‌ها as کتابها); Persian digits
  (۱۲, ۱۴۰۳) and "iPhone 15" keep their order. In this Vazirmatn image it dropped the full stops after آسمان and ص,
  so `{sentence}` ran on from the line's start there. Offline, the scan shows the message that no on-device model
  reads the language and logs "No on-device recognition for fa; cloud only"; the Bubble settings list the model as
  "None" and its dialog says why.
- Fonts: the card lists Noto Sans Arabic, Amiri and Vazirmatn, "Only for Persian text"; Vazirmatn, downloaded and
  chosen, loads on the page as "Screenlate Chosen".
- Audio: holding 🔊 on کتاب lists Lingua Libre's recordings of the word as written, Wiktionary's Fa-كتاب.ogg (found
  through the Arabic letters), a Lingua Libre recording titled otherwise, and the text to speech; a recording and
  Wiktionary's file both played. Google's engine on the emulator has no Persian voice: the voice item plays nothing
  and logs "No text-to-speech voice for fa".
- Anki: "Fill in suggested templates" on a note type with Front, Back, Reading and Sentence gave Reading `{reading}`
  without a span (the reading is Latin) and Sentence `<span dir="rtl">{sentence}</span>`; the note for "نگاه کن" has
  Front نگاه کردن, Reading nigāh kardan and the sentence in the span.
- Emulator: the first run of the overlay's instrumented tests (the 109 MB test APK with every ABI) hung at install
  with about 250 MB free on /data, and `system_server` came back without its package service until a reboot
  (`notes/build-environment.md`).
- Emulator left with Persian active, Vazirmatn chosen and the app in the system language, airplane mode off.

### 2026-10-20, `languages/ar` (Arabic, on `e0c48334`, before its commit), Pixel_10_Pro (API 37)

Scope: Arabic (LK48, POP26, AUD13, ANK17, OCR29) through the download screen, the overlay over a rendered image
(64 px, the phone's Noto Naskh Arabic with Roboto, `TextImage.java` drawing the lines right to left,
`testdata/ocr/ar-lines.png`) in the debug viewer with cloud recognition and with Tesseract offline, app text (a file
name in the Files app), the popup page (`popup-eval.mjs`), the Popup settings' font card, audio, a note in the
emulator's local AnkiDroid collection (never synced); the lookup engine's LanguageLookupTest (18/18) and the overlay's
SystemFontFilesTest (8/8, with the Arabic serif) instrumented tests. x86_64-only builds installed over the app with
its data kept.

Results: passed after three fixes. First checks: LK48, POP26, AUD13, ANK17, OCR29 (moved from N to F).

- Setup: Arabic ticked wty-ar-en, its transcriptions (wty-ar-ipa), wordfreq ar and Tesseract fast ara (1.4 MB).
  wordfreq ar was built in 1.5 s (100,000 words, 738 KB); wty-ar-en took 9.0 s (engine import 3.4 s), with a form-of
  table of 347,496 forms and 17 MB on disk (form_of.bin 9.3 MB). Flag sa, bubble ض.
- Lookups (cloud recognition; the first after a start 383-395 ms with the bundle load, later 5-81 ms): والكبير →
  كَبِير; والمدارس → مَدْرَسَة (and, definite, broken plural); اللغه → لُغَة, العربيه → عَرَبِيَّة; اشتريت → اِشْتَرَى;
  هل → هَلْ; نسبة → نِسْبَة; السوق → سَوَّقَ, سَوْق, سُوق; قال → قَالَ; كتابًا → كُتَّاب, then كِتَاب, and read with its
  kasra (كِتَابًا) كِتَاب first. ذهبت shows ذَهَب (gold) before ذَهَبَ (to go), and الكتب shows كَتَبَ (to write) before
  كِتَاب, whose plural it is: form rows name these lemmas without harakat, so every entry of the letters matches, as
  in Yomitan (an owner question in the plan).
- Cloud recognition: lines in reading order, the final result in about 1.5 s; "اشتريت iPhone 15 من السوق (٢٠٢٤)."
  kept its order.
- Tesseract ara fast offline (a band in about 0.4 s): reads harakat (كِتَابًا); Latin garbled ("اشتريت 15 ©800ظأ"),
  50% read as 9650, a Latin comma for ،. First pass: the highlight of كِتَابًا reached over يقرأ and والكبير, as its
  first symbol's box started at the line's start.
- Page: `lang` ar, `body` left to right; the source and the headwords `dir="rtl"`; the glossary blocks and the
  dictionary name `dir="auto"`, so an Arabic example under English meanings runs right to left. The "Screenlate
  Serif" face first named Google Fonts' Naskh names, of which only "Noto Naskh Arabic" matched the phone's file;
  Android's regular file names itself `NotoNaskhArabic`. First pass of the transcriptions: Egyptian [keˈtæːb] before
  /ki.taːb/.
- Fonts: the card lists Noto Sans Arabic and Amiri (0.8 MB each), "Only for Arabic text". Amiri, downloaded and
  chosen, loads on the page as "Screenlate Chosen"; set back to the phone's font.
- Audio: holding 🔊 on كِتَاب lists Wiktionary's Ar-كِتَابٌ.oga, three Lingua Libre speakers and the text to speech;
  a tap played Lingua Libre's recording of كتاب as written (source 2 of 2, 3.7 s) instead of Wiktionary's vowelled
  one. Google's Arabic voice (ar-xa) is installed and spoke.
- Anki: a note type "Screenlate RTL" (Front, Back, Reading, Sentence); "Fill in suggested templates" wrapped Reading
  and Sentence in `<span dir="rtl">` and left Front plain. The note added for قال: Front قال, Reading
  `<span dir="rtl">قَالَ</span>`, Sentence `<span dir="rtl">قال د. أحمد: نعم.</span>`.
- App text: a file named "اشتريت iPhone 15 من السوق الكبير يوم الجمعة.txt" in Download, laid out right to left by
  Files; source chip "App text"; aimed at السوق the text runs "السوق الكبير يوم الجمعة.", at اشتريت "اشتريت iPhone
  15 من السو", and اشتريت → اِشْتَرَى. The file was removed afterwards.
- Fixes: Tesseract word boxes cut at the word before them (TesseractPageTest); transcriptions in the dictionary's
  order in a language without audio regions (popup.mjs); the Naskh `local()` names (ArabicSupportTest). Rechecked
  after reinstalling: the highlight covers the aimed word only, /ki.taːb/ comes first, `NotoNaskhArabic`, `Noto Naskh
  Arabic` and `NotoNaskhArabic-Bold` load.
- Emulator left with Arabic active and Amiri downloaded, airplane mode off; /data 328 MB free.

### 2026-10-20, `languages/km` (Khmer, on `e34d9fbf`, before its commit), Pixel_10_Pro (API 37)

Scope: Khmer (LK47) through the Languages screen, the download screen, the search screen (text action), the overlay
over a rendered image (64 px, the phone's Noto Sans Khmer with Roboto for digits and punctuation, `TextImage.java`
with the lines in a UTF-8 file) in the debug viewer with cloud recognition and with Tesseract offline, the sentence
translation, the Popup settings' font card, the popup page (`popup-eval.mjs`), audio; the lookup engine's (43/43,
LanguageLookupTest 17/17) and the overlay's (24/24, SystemFontFilesTest 7/7 with the Khmer serif) instrumented tests.
The 126 MB debug build no longer fit in /data (`INSTALL_FAILED_INSUFFICIENT_STORAGE`); an x86_64-only build (82 MB,
`ai/notes/build-environment.md`) was installed over it with the app data kept.

Results: passed after one fix. First check: LK47.

- Setup: Khmer offered wty-km-en (0.96 MB, ticked), transcriptions (69 kB, ticked), Tesseract fast (1.4 MB, ticked,
  main model) and accurate (8.1 MB): 2.5 MB, about 5.0 MB after import. Flag kh, bubble ខ; Khmer is listed last on
  the Languages screen.
- Search: សាលារៀន → school; សួស្តី → សួស្ដី (hello) and ស្ដ្រី → ស្ត្រី (woman) across the two subscripts; ក្មេងៗ → ក្មេង
  (children); សាលា + U+200B + រៀន → សាលារៀន.
- Overlay, cloud recognition (word boxes, split evenly): សាលារៀន, ក្មេងៗ, សួស្ដី, ស្ត្រី, សប្បាយ, រាជធានី, ភ្លៀង found
  from any part. First pass: ក្រុងភ្នំពេញ aimed at ពេញ showed only ក្រុង (city), នៅផ្ទះ at ផ្ទះ only នៅ: ICU on the
  emulator joins these phrases (as ICU4J 66.1 does), and the lookup started at the joined word's start. After the
  fix: ភ្នំពេញ (Phnom Penh), then ក្រុង; ផ្ទះ, then នៅ; ថ្ងៃ, then រាល់; គ្នា in ទាំងអស់គ្នា; back on ក្រុង → ក្រុង. One aim
  at the left edge of a word gave the word before (គេ): the edge of its even share. Sentences (translation button):
  "I go to school every day.", "They are very happy.", "The year 2024 has a lot of rain." (each ended at ។).
- Overlay offline: Tesseract khm fast alone. Its text ("Copy all recognized text" pasted into the search): the real
  spaces kept and none added inside words, which confirms the 0.15 gap; vowel signs dropped in places, ត read as ទ,
  a line split where it starts with ្រ or a stacked consonant; lines 1 and 2 of the paragraph joined with nothing.
- Fix: the lookup also starts inside the aimed word when nothing found reaches the aimed character (Thai and Khmer;
  unit tests in EarlierStartsTest and KhmerSupportTest). Rechecked after reinstalling, as above. Thai was not
  rechecked on the device for this (unit tests only).
- Page: `lang` km; the "Screenlate Serif" face names Noto Serif Khmer by `local()`, and weights 400 and 700 load.
- Fonts: the card lists Kantumruy Pro and Hanuman, "Only for Khmer text". Both downloaded; Kantumruy Pro, chosen,
  loads on the page as "Screenlate Chosen" (100 700) for the Khmer range only. Set back to the phone's font.
- Audio: ទឹក only from the text to speech, which spoke it with Google's Khmer voice; Lingua Libre and Wiktionary have
  no recording.
- Emulator left with Khmer turned on and active, Kantumruy Pro and Hanuman downloaded, airplane mode off; /data
  388 MB free. Powered off.

### 2026-10-20, `languages/th` (Thai, on `12b367c4`), Pixel_10_Pro (API 37)

Scope: Thai (LK46) through the Languages screen, the download screen, the search screen (text action), the overlay
over a rendered image (64 px, the phone's Noto Sans Thai with Roboto for digits and punctuation, `TextImage.java`) in
the debug viewer with cloud recognition and with Tesseract offline, the sentence translation, the Popup settings' font
card, the popup page (`popup-eval.mjs`), audio; the lookup engine's (42/42) and the overlay's (23/23) instrumented
tests.

Results: passed after one fix. First check: LK46.

- Setup: Thai offered wty-th-en (2.7 MB, ticked) and wty-th-th (2.9 MB), the Thai National Corpus frequency (1.6 MB,
  ticked), transcriptions (584 kB, ticked), short translations (79 kB) and Tesseract fast (1.1 MB, ticked, main model)
  and accurate (7.6 MB): 6.0 MB, about 19 MB after import; downloaded, built and imported in under 20 s. Flag th,
  bubble ท.
- Search: การตลาดออนไลน์ → การตลาด (marketing), then การ (rank 2), no กา; ห้อง → its own entries (room; measure), none
  of the rooms it counts; จริงๆ แล้ว → จริง ๆ; นาน ๆ ครั้ง → นานๆ, then นาน; น + ํ + ้ + า + เย็น → น้ำ (rank 130), the four
  characters highlighted; กรุงเทพฯ → กรุงเทพมหานคร through its form row (alternative abbreviation). wty-th-en has no
  เด็ก ๆ, so เด็กๆ shows เด็ก. การตลาด and กรุงเทพมหานคร carry no rank: the corpus counts them in parts.
- Overlay, cloud recognition: ห้องสมุด aimed at สมุด → ห้องสมุด (library, rank 3316), then สมุด, highlighted from ห;
  โรงเรียน aimed at เรียน → โรงเรียน, then เรียน; จริงๆ → จริง ๆ; การตลาด → การตลาด and การ; กรุงเทพฯ → กรุงเทพมหานคร;
  น้ำ → น้ำ. Sentences (translation button): "We drink cold water." (the space before แล้ว ended it); "In 2024, there
  was a lot of rain." (ใน พ.ศ. 2567 … whole: the period after ศ held, and the spaces beside the digits); "Bangkok is the
  capital city of Thailand" (the space after ฯ held); "Children really like going to school. Online marketing is
  growing very fast.": the space after เด็กๆ held, and so did the space after จริงๆ at the line end, as cloud
  recognition put both lines in one paragraph (a limit of the decided rule, in `language-rules.md`).
- Overlay offline: Tesseract tha fast alone (⚠ for the failed cloud request): ห้องสมุด, โรงเรียน, จริงๆ, การตลาด and
  กรุงเทพฯ as above. Its text ("Copy all recognized text" pasted into the search): words kept together
  (ห้องสมุดเปิดตั้งแต่เก้าโมงเช้า, การตลาดออนไลน์โตเร็วมาก); spaces lost beside ๆ and the digits (เด็กๆชอบ,
  พ.ศ.2567มี); lines 4 and 5 made one paragraph joined with nothing (…ของไทยใน พ.ศ.…); ดื่มน้ำ read as ดีมนํา, so aiming
  at น้ำ found only the letter ม.
- Fix: the match of เด็กๆ ชอบ took in the space after ๆ (the engine's LanguageLookupTest failed; on the search screen
  the highlight of จริงๆ ran up to แล้ว): the mai yamok processor no longer adds a space after a ๆ that ends the text.
  Rechecked after reinstalling: the highlight stops at ๆ.
- Page: `lang` th; the "Screenlate Serif" face names Noto Serif Thai by PostScript and full name (`local()`), weight
  400 700, as the phone's NotoSerifThai-Regular.ttf is a variable font (wght 400-700); both weights load. The sans
  face names no font (the default falls back to Noto Sans Thai).
- Fonts: the card lists Sarabun (0.2 MB), Noto Sans Thai Looped (0.2 MB), Kanit (0.4 MB) and Prompt (0.4 MB), "Only
  for Thai text", and a Thai sample in the preview. Sarabun and Noto Sans Thai Looped downloaded; Looped, chosen,
  loads on the page as "Screenlate Chosen" (100 900) for the Thai range only. Set back to the phone's font.
- Audio: น้ำ from the second source, Wiktionary (Th-น้ำ.ogg); Lingua Libre has none; the voice is listed third.
- Emulator left with Thai turned on and active, Sarabun and Noto Sans Thai Looped downloaded, airplane mode off;
  /data 262 MB free (416 MB before: the two reinstalls of the 126 MB build and the Thai data).

### 2026-10-20, `languages/hy` (`60826bc2`, Georgian and Armenian), Pixel_10_Pro (API 37)

Scope: Georgian (LK44) and Armenian (LK45) through the Languages screen, the download screen, the search screen (text
action), the Lookup and Popup settings, the overlay over rendered images (72 px, the phone's Noto Sans Georgian and
Noto Sans Armenian with Arial for digits and punctuation) in the debug viewer with cloud recognition and with
Tesseract offline, the popup page (`popup-eval.mjs`); the lookup engine's (41/41) and the overlay's (22/22)
instrumented tests.

Results: passed. First checks: LK44, LK45.

- Setup: Georgian offered wty-ka-en (5.5 MB), the transcription dictionary (433 kB) and Tesseract fast (2.5 MB, ticked,
  main model) and accurate (4.5 MB, unticked): 8.4 MB, about 23 MB after import. Armenian: wty-hy-en (7.5 MB),
  transcriptions (642 kB), Tesseract fast (3.5 MB, ticked) and accurate (6.4 MB): 12 MB, about 32 MB. Flags ge and am,
  bubbles ქ and Հ.
- Georgian search: წიგნს → წიგნი through the form row (dative singular) and through the rules (nominative «
  declension; in Russian именительный падеж « склонение); წიგნები → წიგნი (nominative plural); მას shows its own
  entry first, then form rows; ᲡᲐᲥᲐᲠᲗᲕᲔᲚᲝ → საქართველო; ბიომრავალფეროვნება (18 letters) whole. Lookup setting: 24
  for Georgian, 16 for Spanish.
- Georgian overlay, cloud recognition: წიგნს, წიგნები, the Mtavruli heading and the long word as in the search; the
  sentence of თბილისში kept "ქ." and "1991 წ." ("He/She was in the city of Tbilisi in 1991." from Bing).
  Tesseract kat fast drafted the page in 0.8 s.
- Georgian overlay offline: Tesseract alone read the page in 1.0 s (⚠ for the failed cloud request); წიგნს → წიგნი,
  the Mtavruli heading → საქართველო.
- Armenian search: ինչո՞ւ → ինչու; կարդա՛ → կարդալ (second person singular imperative), կարդա → կարդալ; գրքեր →
  գիրք; Երեվան → Երևան; ԵՐԵՎԱՆ shows the verb form երևան (of երևալ) first and Երևան second, as Yomitan ranks fewer
  text changes first; Եվրոպա as written; գիւղ → գյուղ (traditional); եւ → և (its form rows say "alternative
  alternative" in the data); ճարտարապետություն (17 letters) whole. Lookup setting: 24.
- Armenian overlay, cloud recognition: aimed at ւ after ՞, the whole Ինչո՞ւ was taken (→ ինչու); Կարդա՛ → կարդալ;
  Երեվանը → Երևան (its definite form row, through the ligature); գիւղ → գյուղ; the long word whole. Sentences: the
  one of ասաց went on past the period ("He said, 'Let's go.'"), the one of եկավ past 10:30 ("He arrived in the
  village at 10:30."), and on lines with two sentences the second started after ։ and after a colon ("He/She then
  left.", "Then he/she left.").
- Armenian overlay offline: Tesseract hye fast read the page in 0.35 s; Ինչո՞ւ aimed after ՞ → ինչու.
- Page: `lang` ka and hy; the "Screenlate Serif" face carries each script's range; `local()` loads Noto Serif
  Georgian and Noto Serif Armenian (and the sans faces) by PostScript and full name, not by family name. Headword
  ինչու has no marks (U+056B 0576 0579 0578 0582).
- Fonts: both cards offer only the phone's font, "Only for Georgian text" and "Only for Armenian text", samples in the
  script.
- Audio: წყალი from the first source (Wiktionary) in 4.1 s; წიგნი has no recording and no voice ("No text-to-speech
  voice for ka"); ջուր from the first source in 2.5 s.

Not checked: Armenian text to speech, a translation of Tesseract's text (offline), Anki notes.

Emulator state since this run: Armenian active and starred, Georgian ready; Wi-Fi and data on; /data had 415 MB free.

### 2026-10-19, `languages/bn` (Bengali, before its commit), Pixel_10_Pro (API 37)

Scope: Bengali (LK43) through the Languages screen, the download screen, the search screen (text action), the
overlay over a rendered Bengali image (64 px, Nirmala UI) in the debug viewer with cloud recognition and with ML Kit
offline, the popup page (`popup-eval.mjs`) and the Popup settings' font card; the lookup engine's instrumented tests
(39/39).

Results: passed. First checks: LK43.

- Setup: the download screen listed wty-bn-en (1.7 MB), the wordfreq list (1.6 MB), the transcription dictionary
  (204 kB), ML Kit (built in, ticked, "Reads Chinese, Japanese, Korean, Devanagari, Bengali and Latin-script text")
  and both Tesseract models (856 kB and 11 MB, unticked). 3.4 MB downloaded, about 12 MB after import; Active · Ready,
  flag bd, "Bengali" in the English interface, bubble বা.
- Search: বইটির → বই (-টির, W 457, /boi/); বইগুলো, also with ো typed as ে + া, → বই (-গুলো); ছেলেদের → ছেলে
  (-দের, W 264); মাসটি → মাস through -টি and through the form rows "nominative singular definite" and "singular
  definite objective" (rows written with a romanization after the form). বই is also a form of বু and বওয়া, which
  follow it.
- Overlay, cloud recognition: দাম, বইটির → বই, বললেন → বলা ("second person third person past polite"); the
  sentence of বললেন started after the danda and kept ড. ("Dr. Yunus said, "Come tomorrow."" from Google), the one
  of ছিলেন kept the initials ("The Prime Minister of Bengal was A. K. Fazlul Huq." from Bing). Audio for বই came from
  the first of two sources in 4.2 s.
- Overlay, ML Kit with Wi-Fi and data off: the band draft (2 paragraphs) in 4.3-4.9 s, Hindi's took 3.1-3.3 s on
  the same emulator; the rest of the page (6 paragraphs) 5.9-7.5 s later, 10.7-11.9 s in all; the cloud request
  failed offline (⚠). বইটির, বইগুলো → বই and ভালো read correctly.
- Page: `lang` bn; the "Screenlate Serif" face carries the Bengali range. `local("NotoSerifBengali-Regular")` and
  `local("Noto Serif Bengali Regular")` load, `local("Noto Serif Bengali")` does not; Noto Sans Bengali loads by its
  PostScript names only.
- Fonts: Bengali's card offers Hind Siliguri (0.5 MB) and Tiro Bangla (0.3 MB) with "Only for Bengali text". None
  downloaded.

Not checked: a font download, the voice for Bengali, Tesseract for Bengali.

Emulator state since this run: Bengali active and starred, Hindi, Croatian and Serbian ready; Wi-Fi and data on;
/data had 364 MB free.

### 2026-10-19, `languages/hi` (Hindi, before its commit), Pixel_10_Pro (API 37)

Scope: Hindi (LK42) through the Languages screen, the download screen, the search screen (text action), the overlay
over a rendered Devanagari image (64 px, Nirmala UI) in the debug viewer with cloud recognition and with ML Kit
offline, the popup page (`popup-eval.mjs`), the Popup settings' font card and the on-device model dialog; the lookup
engine's instrumented tests (38/38) and `SystemFontFilesTest` (3/3).

Results: passed after one fix. First checks: LK42.

- Setup: the download screen listed wty-hi-en (5.6 MB), the wordfreq list (148 kB), the transcription dictionary
  (659 kB), ML Kit (built in, ticked) and both Tesseract models (1.1 and 12 MB). 6.4 MB downloaded, about 23 MB after
  import; Active · Ready, flag in, bubble हि.
- Search: पढता → पढ़ना ("singular masculine contrafactual" +2, W 3347, [pəɽʰ(ə)nɑ]); हूं → होना (W 257); लडकियां →
  लड़की ("plural direct", W 395); खाना → खाना (W 722) and ख़ाना (Wiktionary's "nuqtaless form" row); ख़ाना, decomposed
  and as U+0959, → ख़ाना only; हैँ → हैं (W 9) and होना.
- Overlay, cloud recognition: हैं; "पढता हूं" → पढ़ना as a phrase ("first person singular masculine present
  subjunctive/indicative"); the sentence with डॉ. kept the abbreviation ("Dr. Sharma said, “Come tomorrow." from
  Google), the one with ए.पी.जे. its initials ("A.P.J. Abdul Kalam was a scientist." from Bing). Audio for पढ़ना
  came from the first of two sources in 4.1 s.
- Overlay, ML Kit with Wi-Fi and data off: the band draft (3 paragraphs) in 3.1-3.3 s, the whole page (5
  paragraphs) in 6.0 s; the cloud request failed offline as expected. कलाम found; साफ़ किया → साफ़ करना; किताब aimed at
  ता → किताब, highlighted whole. ML Kit read ख़ाना without its nukta, so खाना came first and ख़ाना second.
- Page: `lang` hi; the "Screenlate Serif" face carries the Devanagari range. `local("NotoSerifDevanagari-Regular")`
  and `local("Noto Serif Devanagari Regular")` load, `local("Noto Serif Devanagari")` does not; Noto Sans Devanagari
  loads by its PostScript name only.
- Fonts: Hindi's card offers Mukta (0.9 MB), Hind (0.6 MB) and Tiro Devanagari Hindi with "Only for Hindi text";
  Spanish's lists the Latin fonts. None downloaded.
- Fix: the ML Kit description (language setup and the on-device model dialog) named Chinese, Japanese, Korean and
  Latin script only; it names Devanagari now in all 14 locales, checked in the model dialog after the rebuild.

Noted, not looked into: Home showed "The note type was not found in AnkiDroid" for Japanese (the emulator's local
AnkiDroid collection).

Not checked: a font download, the voice for Hindi, Tesseract for Hindi.

Emulator state since this run: Hindi active, Croatian starred; Wi-Fi and data on; /data had 414 MB free. Powered off.

### 2026-10-19, `languages/sr` (Croatian and Serbian, before its commit), Pixel_10_Pro (API 37)

Scope: Croatian and Serbian (LK36 as rewritten for the two languages) through the Languages screen, Home, the
on-device model dialog, the search screen (text action), the overlay over `sh-cyrl-sample.png` and the new
`sr-latn-sample.png` in the debug viewer, and the bubble menu; the lookup engine's instrumented tests (74 passed).

Results: passed after one fix. First checks: LK36 as rewritten.

- Setup: the build has no migration from the unreleased Serbo-Croatian entries, so both languages came up off, with
  the shared dictionaries in one "Turned off" card ("Serbo-Croatian · 3 dictionaries · 48 MB", "Turn on Croatian",
  "Turn on Serbian"). Croatian turned on from it: Active, Ready, flag hr, Đ. Serbian then turned on at once from "Not
  turned on": Active, Ready, flag rs, Ћ, no flag button.
- Models: Serbian's list held Tesseract fast and accurate (srp), PaddleOCR v6 (installed) and PaddleOCR v5
  (recommended, installed, in use). Choosing v6 loaded its detector and recognizer and warmed up in 481 ms; a scan
  of the Latin image then drafted with it. Set back to v5.
- Overlay, Serbian over Cyrillic text: кућа W 553; у gave the letter and the preposition. Јој showed the Latin entry
  joj: cloud recognition read all three letters as Latin, and the look-alike fix needs a Cyrillic letter in the word.
  Fixed (on a Cyrillic line a word of look-alikes alone is written in Cyrillic); then Јој gave јој and је gave је
  (бити), checked by the headwords' code points.
- Overlay, Serbian over Latin text: kući → kući, kuća; the lone u stayed Latin (U+0075). Translation: Bing timed out
  once (3 s, its first request) and Google gave "You and I are in the house."; the next sentence came from Bing as
  sr-Latn: "The house is big, and the yard is small."
- Page `lang`: hr for Croatian, sr for Serbian; брдо in the Serbian б.
- Audio: Croatian ljubav listed Hr-ljubav.ogg, Sr-љубав.flac and the voice; Serbian voda Sr-вода.flac,
  UništihEkonomiju (Lingua Libre) and the voice, which spoke with Google's sr-RS voice.
- Turning off: Croatian with Serbian on offered no files; Serbian then offered "Delete its dictionaries and models
  (48 MB)", unticked here, and the shared card came back. Both were turned on again; turning Croatian on made it
  active and left Serbian unstarred (activation moves the stars), so the bubble menu had no flag row until Serbian
  was starred again.

Emulator state since this run: Serbian active, Croatian starred, Greek on and unstarred (it was active);
`sr-latn-sample.png` added to the app's test images. /data had 344 MB free.

### 2026-10-19, `languages/el` (Greek, before its commit), Pixel_10_Pro (API 37)

Scope: Greek (LK39) through the Languages screen, the download screen, the search screen (text action), the
on-device model dialog and the overlay over a rendered Greek image in the debug viewer; the lookup engine's
instrumented tests (37/37).

Results: passed after one fix. First checks: LK39.

- Setup: the download screen listed wty-el-en (6.0 MB), wty-el-el (38 MB, unticked), the wordfreq list (276 kB), the
  transcription dictionary, el-en short translations, PaddleOCR v5 (ticked, main) and both Tesseract models. Paddle
  `el` was not uploaded yet, so Tesseract fast went first (8.7 MB, about 29 MB after import); Ready, flag gr, Ω.
- Lookups: σκυλος and ΣΚΥΛΟΣ → σκύλος (W 3024, /'scilos/); ποτε → ποτέ (W 120) and πότε (W 333); πότε → itself;
  ξαναρώτησε → ρωτάω ("ξανα- « second person singular imperative perfective active", +2); καλη ορεξη → καλή όρεξη,
  then καλός; νερό W 346. Wiktionary's own rows show as written: σκυλί "masculine" → σκύλος, μητέρα "feminine" →
  πατέρας, καλή "…feminine/feminine".
- Fix: holding 🔊 on νερό and μητέρα listed text-to-speech only. Commons has Greek Lingua Libre files under `ell`,
  `gre` and `mis`, and the search asked for `ell`; it now takes any code. After the fix μητέρα lists MARKELLOS (`gre`)
  and γάτα Spiros71 (`ell`), then text-to-speech.
- Paddle: after the upload, the on-device model dialog downloaded PaddleOCR v5 (7.0 MB in 4.2 s, 7.8 MB installed;
  the v5 detector was already there); choosing it loaded paddle-el and warmed up in 131 ms.
- Overlay: ΣΚΥΛΟΣ → σκύλος; Νίκος, whose translated sentence was "Ο κ. Νίκος είπε· «Έρχομαι αύριο»." (Mr. Nikos said:
  "I'm coming tomorrow."); Ξαναρώτησε → ρωτάω; the lone Η → Η (letter, numeral, article); τώρα, whose sentence kept
  "π.χ." inside ("The cat is drinking water, e.g., right now."). Bing failed once with a network error and Google
  answered; Bing answered the second time.

### 2026-10-19, `languages/uk` (Ukrainian and Belarusian, before their commit), Pixel_10_Pro (API 37)

Scope: Ukrainian (LK37) and Belarusian (LK38) through the Languages screen, the download screen, the search screen
(text action) and the overlay over a rendered Ukrainian image in the debug viewer; the lookup engine's
instrumented tests.

Results: passed after one fix. First checks: LK37, LK38.

- Setup: Ukrainian's download screen listed wty-uk-en, the wordfreq list, the transcription dictionary, PaddleOCR v5
  (installed) and both Tesseract models; with Russian added, wty-uk-ru too (17 MB, about 56 MB after import).
  Belarusian: wty-be-ru, wty-be-en and the transcription (2.9 MB), no frequency list. Both Ready; flags ua and by.
- Lookups: книги → книга ("nominative plural", W 1109, [ˈknɪɦɐ]); зробив → зробити ("past"); чита́ю → читати
  ("present"); п’ять → п'ять (W 800); пʼятниця → п'ятниця; сям’я → сям'я; вады and ва́ды → вада ("genitive
  singular"); праўда. Wiktionary audio played (Ogg).
- Fix: книга and п'ятниця typed as is showed "nominative plural « vocative singular" (a Yomitan rule to книго, then
  Wiktionary's vocative row back to книга); fixed in the engine and checked: no chain.
- wty-uk-ru shows п'ятниця twice (its headwords п'ятниця and п’ятниця); left as is (`notes/language-rules.md`).
- Overlay (Belarusian, then Ukrainian active): the lone і found the conjunction; п’ять found п'ять with its
  highlight over the word.

### 2026-10-19, `languages/sh` (Serbo-Croatian, before its commit), Pixel_10_Pro (API 37)

Scope: Serbo-Croatian, both entries (LK36), through the search screen (text action), the overlay over
`testdata/ocr/sh-cyrl-sample.png` in the debug viewer, the Languages screen and the bubble menu; the lookup engine's
instrumented tests. `/data` was short for the install: `Download/3_Mining.apkg` (208 MB, a copy of the deck in
`testdata/anki-backup/`) was deleted.

Results: passed after three fixes. First checks: LK36.

- Setup: the Latin entry's download screen listed wty-sh-en, the wordfreq list, the transcription dictionary and
  PaddleOCR v6 (already installed); Ready. The Cyrillic entry then turned on at once with the shared dictionaries:
  Active, Ready, the Serbian flag, Ћ.
- Lookups: kuca → kuca (doggy), kucati, kuća (house, "genitive plural": kuća is also a form of itself, shown as
  Yomitan shows it); кућа with the frequency 553 from the transliterated wordfreq list; Јој with a Latin J → јој;
  џеп → pocket; the overlay over the Cyrillic image found кућа.
- Glyph forms: headword and sentence in the Serbian б; the search field (a native view) in the Russian one.
- Audio: kuća and кућа from Wiktionary (`Sr-кућа`); text to speech available for both entries; the audio sources
  were set back to the defaults afterwards.
- Bubble menu: with both entries starred, the flag row showed Croatia and Serbia; switching changed Đ and Ћ.
- Turning off: the Latin entry with the Cyrillic one on offered no files to delete; the Cyrillic one then offered the
  shared dictionaries (48 MB), unticked here; one card "Serbo-Croatian · 3 dictionaries · 48 MB" with a button per
  entry; turning the Latin entry on from it made it active, the card went and the Cyrillic entry stayed off.
- The timing test failed on a line of c, s and z: QuickJS has no console and Yomitan warns through it when it cuts
  the variants at 4,096, so the lookup threw. Fixed (a console stub in every bundle); "kuca cecescecescecesc" now
  shows kuca first. Such lines take 140-260 ms, ordinary words 2-10 ms.
- Cyrillic text with reading marks (ку̏ћа) found nothing; fixed in the glue and checked: ку̏ћа → кућа.
- With a Croatian or Serbian interface the default translation target was the text's own language; fixed (unit
  tests).
- Home's "the system stopped the service" after each `uiautomator dump` is the dump rebinding accessibility services
  (`notes/build-environment.md`), not a bug.

### 2026-10-19, `languages/tl` (`db2f5e32` and the fixes below), Pixel_10_Pro (API 37)

Scope: the new Latin-script languages (LK31-LK35): Dutch, Czech, Indonesian, Malay and Filipino, through the search
screen (text action) and the overlay over `scripts/text-image.py` images in the debug viewer. Cloud recognition, the
sentences checked through the translation button. `/data` was nearly full: the French transcription dictionary was
deleted to install the build.

Results: passed after two fixes. First checks: LK31-LK35.

- Setup: each language set up from the Languages screen, every one Ready; names, flags and bubble glyphs (Ĳ, Ř, ID,
  MS, Ng) right, Ng also at the right edge.
- Dutch: honden → hond (plural), katje (its own entry, then kat neuter diminutive), liep → lopen, auto's → auto,
  zo'n whole; rijbewijsfoto and dorpskerkdeur split into two words, werkgeverstafel → werkgever + tafel. stadsbestuurder
  splits as stadsbestuur + der (greedy split, recorded in `notes/language-rules.md`). "mr. Jansen" kept the sentence.
- Czech: kočky → kočka (genitive singular +1), četla → číst, chodě → chodit (transgressive), Praze → Praha; "5.",
  "tj." and "např." kept their lines. In Segoe UI text, cloud recognition read the l of "př. n. l." as I and the
  sentence started after it; fixed, the era with a serif font kept the sentence.
- Indonesian: membaca → baca with "active", dibaca → baca (passive), rumah-rumah → rumah (plural).
- Malay: baca with no Jawi step, membaca and kucing their own entries.
- Filipino: kumain its own entry, basa without Baybayin, magandang → maganda; "G. Santos sa Sta. Cruz" one sentence.
- Dutch Jansen (and Czech v) showed "alternative form « alternative": a second form-of step led back to the text
  (Jansen → Janssen → Jansen). The engine no longer takes such a step; checked again after the fix, and by
  `LanguageLookupTest.formStepsDoNotLeadBackToTheText` on the emulator.
- The first lookup after switching the language took a few seconds (the dictionaries open) with an empty result area.
- The line "Compound: …" above a split compound (decided on 2026-10-16) had not been built; built afterwards and
  checked: rijbewijsfoto shows "Compound: rijbewijs + foto" above both entries.

### 2026-10-19, diagnostics build `9937cc4` (`languages/ocr-engines` at `fa1b086` merged), Pixel_10_Pro (API 37)

Scope: the "Waiting for a network" labels (DIC23) and the partial downloads directory (DIC24), on the diagnostics
build the owner installs. Installed over the previous build with the app data restored (`/data` was full, see
`notes/build-environment.md` if this recurs); Wi-Fi and mobile data off with `svc wifi disable`, `svc data disable`.

Results: passed. DIC23 moved to F: JMdict (Hungarian) queued offline showed "Waiting for a network" on its catalog
card; with JMdict (Slovenian) queued too, the shared card "Waiting (2): …" had the line under its title; with one
cancelled, the single card read "Waiting for a network: JMdict (Hungarian)"; with the network back the download
started at once and installed in 2.7 s (the test dictionary deleted afterwards). DIC24 stays N: the download used
`no_backup/dictionary-downloads/` and `cache/` has no `dictionary-downloads/`; the resume after Clear cache is not run.
The system purged the app's WebView cache for low storage during the run, the case the move away from the cache is for.

### 2026-10-19, `languages/checks` (`ca52b3d`, with the language rules review merged), Pixel_10_Pro (API 37)

Scope: the cases the language rules review added (LK28-LK30, OCR26, ANK12-ANK14, POP24). App text of AnkiDroid's
note editor stood in for the text images and the browser page of ANK12, LK28 and OCR26: text shared to AnkiDroid
opens the editor with it in a field, the field reports character positions, and the shell's `am start -f 0x10008000
-a android.intent.action.SEND` opens a new editor each time. LK29 and LK30 used `scripts/text-image.py` images in the
debug viewer.

Results: every case passed on `cc2cd4b`. The first build (`635b00c`) failed LK28 (Vietnamese) and LK30 (English);
both were fixed in `854a77e` and checked again. First checks: LK28-LK30, OCR26, ANK12-ANK14, POP24. Automated on
`ca52b3d`: unit tests 1024 (4 skipped), page tests 139, `scripts/yomitan-language` tests 8.

- LK28: ко́шка typed with U+0301: кошка and кот, no entries for ко or к, one highlight box. Vietnamese hoà typed with
  U+0300 in "Chúng tôi yêu hoà bình.": "hoà bình" first, then hòa bình, hòa, hoà, one box. On `635b00c` app text
  dropped the mark and ended the line there: only "hoa" was found, and ко́шка was highlighted as two boxes.
- LK29: "roll" looked up rock’n’roll from its start; "en" in va-t’en and "eau" in l’eau as before.
- LK30, cloud recognition (Engines Both): "tant" showed important first, then tant; "impor-" showed important; both
  halves highlighted; "known" after "well-" showed well-known first; German "Haus- und": und alone, Haus alone, no
  joined form; Japanese broken across lines: 電 found 電車, 楽 found 楽しい, the sentence joined with no space. On
  `635b00c` English failed with both engines: cloud recognition gave "impor-" no separator, and ML Kit leaves the
  line-final hyphen out ("impor" / "tant"). `854a77e` fixed it for cloud recognition; the case covers that only.
- OCR26 (on `635b00c` and again on `ca52b3d`, since `854a77e` changed the app text lines): "whole" at the start of a
  wrapped line looked up alone, with a space at the wrap in `{sentence}`; a line-final "pick" showed "pick up" first
  and the sentence "…pick up the new tasks."; "up" on the next line looked up alone; an explicit line break between
  "pick" and "up" gave "pick up"; Japanese across a wrap joined with no space.
- ANK12: 円周率 kept ３．１４ in the sentence; German "ging" gave "Er sagte “ja” und ging.", "gehen" gave "Dann gehen
  wir." and "ging" after “Komm.” gave "Dann ging er." with no leading ”; Turkish "dün geldi" kept "ABC LTD. ŞTİ." in
  the sentence; Polish "Idziemy." ended its sentence, and "ok." did not end "To kosztuje ok. 5 zł i jest ok.
  dobre.". Turkish (18 MB) and Polish (28 MB) were turned on for it.
- ANK13: a Russian note with `{sentence-furigana}`: the made-up word Пракошка got no stress over the кошка inside it;
  то́же got its stress; спит and кот none.
- ANK14: Russian: the + menu listed the four markers with stress hints on a second line, which wrapped; `{furigana}`
  gave ко́шка with no ruby; `{sentence-furigana}` stressed only dictionary forms (Ко́шка, то́же; спит and кошки none).
  Japanese: the four markers, no second line. English and Korean: none; an English field with `{furigana}` typed
  in got the word. Chinese not checked: not installed, the emulator's storage was low.
- POP24: Turkish, the test archive "ingilizce sıklık" (`index.json` title in lowercase): its chip showed "İ 1" when
  unfolded; the folded chip showed W, the initial of wordfreq, the first frequency dictionary. English: "I" on the
  folded chip.

Findings:

- Fixed in `ca52b3d`: the audio finder made its clip directory in the cache once per process. At 98-99 % storage
  use the emulator's system clears app caches every minute ("Free cache requested" in the log); after that every
  clip download failed with FileNotFoundException until the app restarted, and Home showed "Audio sources that
  failed: Wiktionary, Lingua Libre". On `ca52b3d` the clips were found after each clearing and the directory came
  back.
- Owner's choice: Home puts a failing source that several languages share under the first turned-on language that
  lists it (the active one first). The failures above came from Vietnamese, English and German lookups, and the card
  named Korean.

AnkiDroid's local collection on the emulator got three test notes (кошка, кот, whole).

Not checked: ANK14 Chinese; the cases on the phone.

Emulator state since this run: as before it (the data was restored from the backup).

### 2026-10-19, `languages/checks` (`7783c5e` with the scan changes committed after it), Pixel_10_Pro (API 37)

Scope: the cases `languages/ocr-engines` added (OCR22-OCR25, LK27, AUD12), the download queue (DIC3 as changed,
DIC20-DIC22), scan times for Chinese and for Tesseract German, ML Kit's cold warm-up, and four scan changes built on
this branch: freeing PaddleOCR memory once scans pause (NF13), opening the cloud connection on a touch (OCR28),
warming up a model chosen in the dialog (OCR27) and creating a lost popup page after the capture (POP25).

Results: every case passed; no fixes to the merged code. First checks: OCR22-OCR25, OCR27, OCR28, LK27, AUD12, DIC3
(changed), DIC20-DIC22, POP25, NF13. Automated: unit tests 977, page tests 132.

- OCR22 on Polish (German was on already); Russian still ticks its model. OCR23: the scan finished, then the
  directory went; a marked directory left by a killed process went at the next start. OCR24: Tesseract eng truncated
  to 1 MB; scans read with ML Kit meanwhile. OCR25: from a local catalog; the update row says "Tesseract, fast"
  without the language or script it reads.
- LK27: Küchentischlampe lists Lampe before lampen; lampen alone keeps the verb first.
- DIC3: retries 30 s, 1 min and 2 min apart, then "download stopped — connection lost" with Resume and Cancel;
  Resume continued from the bytes already there; Cancel deleted the partial file and its `.state`.
- DIC20: a queued catalog entry shows "Waiting in the queue" with Cancel; downloading a stopped entry resumed it and
  its stopped card went; Update after "Check for updates" while the entry waited for its retry added no task. The
  language setup and home "Download again" paths are covered by unit tests only.
- DIC21: a finished download showed "Waiting" while the other task imported; Cancel in one notification cancelled
  only its task. DIC22: "Waiting (2)" with "Show the list" and "Cancel all".
- Network shaping: `adb emu network delay` applies to new connections only; `network speed 1:1` makes reads time out
  (20 s) while the network stays validated, which is the way to stall a download; a 30 s delay fails the validation
  probe, so WorkManager stops the jobs (stop reason 7).
- OCR27: choosing PaddleOCR v6 for Japanese logged "Loaded paddle-detector-v6", "Loaded paddle-v6" and the warm-up
  (0.2-0.4 s) right after the tap, with no scan.
- OCR28: the first scan after 5 idle minutes: "Lens: connected" in 615 ms (Wi-Fi) and 1474 ms (mobile network), the
  cloud result 1.17 s and 1.82 s after the scan start (before: HTTP alone 2.0-2.2 s); within 30 s no HEAD; Device
  mode and offline sent none.
- POP25: the renderer crashed with the popup hidden ("shown: false"); at the next scan "Captured" took 255-265 ms
  (241-324 ms without the crash), the renderer process started 0.25-0.3 s after the capture, the draft came 0.3-0.6 s
  later than without the crash and no "Skipped frames" line appeared (before: 0.75-0.9 s of skipped frames ahead of
  the capture); the popup showed the word.
- NF13: PaddleOCR v6 on `ja-page-light.png`: "Freed the memory of large inputs: 362 -> 34 MB in 16-21 ms" 10 s after
  the last final; native heap 483 → 143 MB, PSS 714 → 351 MB (128 MB and 293 MB before the scan). Three scans 8 s
  apart trimmed once, after the last. After a trim a UI dump (the service's warm-up on a 32 × 32 image) ran without
  a crash, and the next scans read 14 paragraphs.

Scan times (Device / Both, first text / final): see `ai/notes/ocr-engines.md`, rows marked 2026-10-19. ML Kit's cold
warm-up: Latin 0.65-1.6 s, Korean 0.7 s, Japanese 1.07-1.5 s; PaddleOCR v6 0.48 s.

Findings, not fixed (owner's choices):

- Partial downloads live in the cache directory, which the system cleared when storage ran low; the download then
  failed with a missing file and ended as "connection lost" after three retries.
- A WorkManager stop for a lost network (reason 7) at the moment of the third failure makes WorkManager run the work
  again later: a task logged as stopped resumed by itself.
- On a slow link the second of two parallel downloads gets almost nothing until the first ends.
- The "Waiting" card of a download held offline does not say it waits for the network.
- The log line "retry … queued as null" reads like an error.
- "1 dictionaries" in a count; "no dictionary with definitions" on Home during an installation (as on 2026-10-18).
- A downloaded model is not chosen in the dialog by itself (as designed: it is used once chosen).
- The emulator's UWB service (`android.hardware.uwb-service`) crashes in a loop; AnkiDroid logged an exception of its
  own. Neither involves the app.

Not checked: the changes on the phone (memory and connection timings there, a real mobile network), the
draft-lead rule (the emulator's device drafts lead the cloud by only 0.1-0.35 s), the DIC20 setup and home paths.

Emulator state since this run: as before it (the data was restored from the backup).

### 2026-10-18, `languages/checks` (`2f698a8`), Pixel_10_Pro (API 37)

Scope: the languages work in groups 1-3 and the on-device engines: FR1, LK22-LK26, LNG12, LNG14, DIC9, AUD9 (first
checks), the FreeDict→ru and OpenRussian conversions, every E case, the F cases of OCR, LK, LNG and DIC the engines and
group 3 changed, the model deletion rows, and NF1 for on-device recognition.

Results: every case passed; no code fixes. First checks: LK22-LK26, LNG12, LNG14, DIC9, AUD9, DIC15. Automated: unit
tests pass, page tests 131, engine-hoshidicts instrumented 30/30.

- FR1: the Japanese screen ticks Jitendex, Jiten, Kanjium, the transcription, KANJIDIC and PaddleOCR v6 (79 MB),
  installed in about 50 s.
- LK26: the local ru-RU voice reads кошка and ко́шка the same, за́мок and замок the same, and замо́к differently (the
  server voice not checked). PaddleOCR v5 Cyrillic gives the band draft in about 0.4-0.8 s.
- FreeDict es, fr, de, it, pl, en→ru (`scripts/freedict/freedict_yomitan.py`) and OpenRussian
  (`scripts/openrussian/openrussian_yomitan.py`) converted, imported and looked up; the converter tests pass.
- Model deletion: the active model has no Delete; "Used by" names the languages that read with it; the dialog for a
  shared model names the other languages; Cancel keeps it. Turning a language off deletes only the models no other
  turned-on language uses (Chinese: 3 dictionaries, no models).
- LNG7: the download was held with Wi-Fi and data off (`adb emu network speed gprs` did not slow it).
- OCR20: a Tesseract download; v6 was not downloaded again (many languages read with it). OCR21, FR4, FR5 (turned
  off stays off), FR6 (the disclosure), FR7 passed.

Baselines (NF1, Device engine, `testdata/ocr` pages; band draft / full page / paragraphs):

| Page, model | Band draft | Full page | Paragraphs |
|---|---|---|---|
| en, ML Kit | 0.97-1.1 s | 3.4-3.8 s (first scan 4.1 s) | 7 |
| en, Tesseract fast | 0.67-1.0 s | 2.0-2.95 s | 6 |
| en, PaddleOCR v6 | 0.97-1.2 s | 4.5-4.8 s | 19 |
| en, PaddleOCR v5 Latin | 0.79-1.1 s | 3.4-3.8 s | 20 |
| ja, PaddleOCR v6 | 1.0-1.2 s | 3.8-4.4 s | 14 |
| ja, ML Kit | 1.3-1.6 s | 4.2-4.6 s | 7 |
| ru, PaddleOCR v5 Cyrillic | 0.46-0.81 s | 3.65-4.14 s | 38 |

Capture takes 118-196 ms. Both engines: a Japanese page shows the device draft at 531 ms and the cloud result at
925 ms; a manga page at 1051 ms and 1184 ms. With the emulator's swap full, the first offline scan of a page took 18 s.
NF3 (three drags): 81-84 % janky frames, median frame 22-30 ms, 90th percentile 34-61 ms (2026-10-14: 82 %, 30-34 ms,
65-73 ms). NF4: no `android.anim.lf` frames and no `SurfaceAnimationRunner` during a pull-out.

Findings, not fixed (owner's choices):

- Paddle results come one line per paragraph, and the v5 detector splits Russian lines into overlapping boxes (38
  paragraphs on the Russian page).
- Home shows "no dictionary with definitions" for a language next to its install progress; the progress says
  "Installing dictionaries…" while only a model is left.
- The download screen shows the download size; installed, Chinese takes about 69 MB against the 42 MB shown.
- FreeDict de-ru sense labels are very long, it-ru labels carry "( approfondimento)"; OpenRussian's говорить has the
  upstream gloss "gapirish"; Wiktionary vi-en has a "Central" tag.
- 📖 on a duplicate opened an empty AnkiDroid browser when its deck filter was "Default" or an old browser task was on
  top (AnkiDroid's side).

Not checked: the server TTS voice, phrases with spaces through the text selection menu (adb), LNG6 on mobile data, the
DIC2 ring states, an interrupted reset (DIC9), FR6 with deleted bundled dictionaries, NF4 on API 30, the v6 download
(OCR20), and the parts built after `2f698a8` (the scan error's model button, the damaged-model card, model updates in
Check for updates, deleting a model during a scan).

Emulator state since this run: as before it (the data was restored from a backup): Japanese active; Japanese, Korean,
Spanish, French, German, Italian, Portuguese, Vietnamese, English and Russian turned on; dark theme. AnkiDroid's
local collection keeps the run's test notes.

### 2026-10-17, `languages/ocr-engines` (`79258c9` with the setup fix built locally), Pixel_10_Pro (API 37)

Scope: OCR18-OCR21 (new). Results: passed after one fix: the download screen listed the Paddle detector as its own
item and left it out of the download (fixed in `79258c9`). Emulator state since this run: Russian turned on with
PaddleOCR v5 Cyrillic; PaddleOCR v6 installed and chosen for Japanese, French, German and Italian; Japanese active.
Chrome on the emulator asks to accept its terms on first start (not accepted): pictures are opened in Photos instead
(push to `/sdcard/Pictures`, `content call --uri content://media --method scan_volume --arg external_primary`).

### 2026-10-16, `languages/latin-fonts` (uncommitted build), Pixel_10_Pro (API 37)

Scope: LK21 (new). Results: passed. German active since this run. The parts are looked up letter by letter; whole
word lookups in German stay word by word.

### 2026-10-16, `languages/latin-fonts` (uncommitted build), Pixel_10_Pro (API 37)

Scope: POP23 (new). Results: passed. Emulator state since this run: Lexend downloaded and chosen for Portuguese.

### 2026-10-16, `languages/pt` (Portuguese, uncommitted build), Pixel_10_Pro (API 37)

Scope: LK20, AUD11 (new) and LNG4 for Portuguese. Results: passed. Portuguese turned on with Ã and the Brazilian
flag; "Flag: Brazil" offers Brazil and Portugal. Wiktionary (pt–en), wordfreq and the transcription dictionary
(20 MB) installed in under a minute.

Emulator state since this run: Portuguese active and starred; Japanese, Korean, Spanish, French, German and
Italian turned on.

### 2026-10-16, `languages/it` (Italian, uncommitted build), Pixel_10_Pro (API 37)

Scope: LK19 (new). Results: passed. Italian turned on with È and the Italian flag; Wiktionary (it–en), wordfreq and
the transcription dictionary (27 MB) installed in under a minute.

### 2026-10-16, `languages/de` (German, uncommitted build), Pixel_10_Pro (API 37)

Scope: LK18 and AUD10 (new). Results: passed. German turned on with ß and the German flag; Wiktionary (de–en),
wordfreq and the transcription dictionary (33 MB) installed in under a minute. Found and fixed during the run: the
dictionary's regional-variant tags (northern 🇩🇪⬆️, Southern-Germany) had been counted as Germany and put a
northern [ʃeˈmiː] above the standard [çeˈmiː]; now only standard tags count as Germany. Germany's recordings carry no
region code in Wiktionary titles, so they play as "other regions".

### 2026-10-16, `languages/fr` (French, uncommitted build), Pixel_10_Pro (API 37)

Scope: LK17 (new). Results: passed. French turned on with Ç and the French flag; Wiktionary (fr–en), wordfreq and the
transcription dictionary (29 MB) installed in under a minute. Yomitan names the passé simple "Preterite" with a capital
in English; the other locales have their own names.

### 2026-10-16, `languages/es` (Spanish, uncommitted build), Pixel_10_Pro (API 37)

Scope: the new Spanish cases (LK16, AUD8, DIC19) and LNG4 after the flag reduction.

Results: every case passed. Spanish turned on from the Languages screen with its flag and Ñ in the bubble; the three
dictionaries installed in about a minute. Spanish has no Flag button. The transcription dictionary (all editions)
carries few region tags, so most Spanish transcriptions show without a region, in the dictionary's order.

Emulator state since this run: Spanish active and starred, Japanese and Korean turned on.

### 2026-10-15, `languages/main` at 0116c88 (quote entities, `main` merged), Pixel_10_Pro (API 37)

Scope: POP22 (new); the AVD cold-booted from its disk (Japanese only turned on), so Korean was turned on from the
Languages screen (KRDICT ko-en and wordfreq, 18 MB, ready in under a minute).

Results: passed. 커녕 in the search screen shows KRDICT's second sense as "A postpositional particle meaning "on the
contrary, not to mention"." with quote marks; the archive holds `&quot;` there. Page tests: 128 pass, the new one
covers strings, text items and structured content through the renderer that Anki fields use too.

New cases, checked when written: POP22 (O).

Emulator state since this run: Japanese active, Korean turned on, romaji off.

### 2026-10-15, `languages/main` at 18b634a (the form-of fix merged) with the shared item parser, Pixel_10_Pro (API 37)

Scope: LK15 after the merge; the engine's instrumented tests (30, the language lookups with their form-of tables
among them).

Results: passed, the same entries as on `main`: じどう with its eight words and no じどう entry, 社會 with 社会
"redirected from 社會", 豪州 with オーストラリア and its forms "alternative". No empty item (the only empty-text items are
デジタル大辞泉's pictures). Lookups 34–67 ms.

### 2026-10-15, `main` after 4a92c96 (form-of rows followed, uncommitted build), Pixel_10_Pro (API 37)

Scope: LK15 (new), with Wiktionary (ja–ja) downloaded from the catalog next to the nine other Japanese dictionaries.

Results: passed; LK15 moved from N to E. じどう listed 自動, 児童, 自働, 慈童, 侍童, 兒童, 自仂 and 慈堂 (the last from
wty-ja-ja only), none with a chain and no じどう entry. 社會 showed Jitendex's and Kolobok's old-form entries without the
empty bullet, then 社会 with "redirected from 社會" (Jitendex's item; wty-ja-ja's "kyūjitai" and Kolobok's tag lead to
the same term). 豪州 showed its own entry first, then オーストラリア, 濠洲 and the other forms with "alternative".
Lookups took 10–22 ms warm and 681 ms for the first 豪州 after start.

### 2026-10-15, `languages/main` at 8145fc4 (the romaji rollback merged), Pixel_10_Pro (API 37)

Scope: the rollback of the romaji lookup inside a joined Latin word, with `testdata/ocr/romaji-joined.png`.

Results: as before the change. With romaji on, the aim on "jisho" of "jidoujisho" showed "jidou" (じどう, 自動); the
aim on "la" of "Screenlate" looked up from the word's start ("S", エス). Found on the way, the same as on `main`:
wty-ja-ja "non-lemma" rows (じどう) show as the first result with six numbered items holding only the tag, above 自動.

Emulator state since this run: Japanese active, romaji off.

### 2026-10-15, `main` after e6e20bf (romaji inside a joined Latin word, uncommitted build), Pixel_10_Pro (API 37)

Scope: a new case for the romaji lookup inside a joined Latin word, with `testdata/ocr/romaji-joined.png`.

Results: passed. With romaji on, the aim on "d" of "jidoujisho" showed "jidou" (自動, one lookup in 189 ms); on "o"
it showed "jisho" (辞書 among じし, じしょ: both cover "jisho", as in Yomitan), two lookups of 107 and 122 ms; moving
along "jisho" looked nothing up again. "kitte" showed 切手. "Screenlate" finds nothing from its start, so the aim on
its "a" looked up "la" (ぁ). Seen on the way: wty-ja-* "non-lemma" entries (じし, じどう) showed numbered items without
glosses.

The change was rolled back after this run by the owner's choice (single syllables and letters in the popup); its
case was removed with it.

### 2026-10-15, `languages/main` at 99085b9 (`main` merged), Pixel_10_Pro (API 37)

Scope: the parts of the merge not checked yet: ANK11 with the English Anki settings, SET11, SET3 through the Yomitan
card, DIC7 with the update check closing, the segment fill (SET13).

Results: every case passed. ANK11: English with no deck and no note type showed both crosses with "Choose it below"
and the fields not checked; choosing the deck, then the note type, re-ran the check (all ✓); the Japanese chip showed
Japanese's deck; a field added to the note type in AnkiDroid showed as a new field on return to the page, and "Update
the field list" cleared it. SET3: the Lookup part of a Japanese Yomitan profile, imported while English was active,
went to Japanese ("Language: Japanese"). DIC7: the check found 10 updates, two of them old bundled copies; "Update" on
JMdict (Swedish) kept the other nine listed; "Update all" closed the list at once with the task cards in its place.
JMdict (Hungarian) and the bundled JMdict (English) updated before the rest were cancelled. SET13 in Russian: "По
требованию" and "Системная" fit, the fill alone marks the choice.

New cases, checked when written: SET13 (O). DIC7 gained the update check closing.

Emulator state since this run: English's Anki settings use the deck and note type "Screenlate Test", whose note type
in AnkiDroid now has a third field, Sentence.

### 2026-10-15, `languages/main` after d37b87c (languages in backups, uncommitted build), Pixel_10_Pro (API 37)

Scope: SET12 (new), SET2 and SET9 through it; the English download (DIC2) with frames measured.

Results: every case passed. English turned on from the Languages screen downloaded and imported 113 MB in about 20 s.
`dumpsys gfxinfo` while scrolling home during that download and import: median frame 57 ms, 90th percentile 85 ms,
143 slow UI thread frames of 368; idle on the same screen: 46 ms, 65 ms, 67 of 370 (the emulator is janky at rest,
see NF3). A backup without Japanese held `languages: ["en"]`, no Japanese keys, `audio_settings` with only autoplay
and volume, the three English dictionaries in the list and the English sort dictionary only; the Fonts item (a
Japanese catalog font) disappeared from the dialog. English turned off with its files kept, then restored from that
backup: "Turned on: English", English active again, Japanese kept. A backup of both restored with English unchecked:
30 of 33 dictionaries in the order item, no Dictionaries item (its only file was English), English stayed on and
active. The dialog in Russian wraps; all languages unchecked gives "0 of 2" and "0 dictionaries".

New cases, checked when written: SET12 (F).

### 2026-10-15, `main` after a24171b (backup choice, uncommitted build), Pixel_10_Pro (API 37)

Scope: SET2, SET9 to SET11; ANK11 earlier the same day.

Results: every case passed. A backup without Anki, with 1 font and 2 of 30 dictionaries held no Anki keys, the full
dictionary list, `fonts/fonts.json` and the files of the two dictionaries only. Restoring one of the two dictionaries
and the font: the summary named 1 font and 1 dictionary; the font folder held only the font's files. The older format
was made by removing `FONTS` from that backup's manifest.

New cases, checked when written: SET9 and SET10 (F), SET11 (O).

Findings, fixed: rows with two-line hints touched the next row (4 dp of padding added); the Yomitan card's
explanations used body text instead of the hint style; the files hint showed "0 B" with nothing chosen.

### 2026-10-15, builds 7a6121f to d7f1509 (`languages/ko`), Pixel_10_Pro (API 37)

Scope: the new Korean cases; the engine's `KoreanLookupTest` (4) and the page tests.

Results: every case passed; the dates are in the Checked column.

New cases, checked when written: LK13 (E); OCR17, POP20, AUD7 and DIC18 (F); LK14, POP21 and ANK10 (O). OCR11
checked for Korean.

Findings, fixed: the download total counted the hanja archive the app carries; about 1,000 Korean Wiktionary form-of
tags showed a piece of their gloss; character entries did not show their tags (question 9 in the plan).

Known limits, as in Yomitan or in the data: 수업은 lists 수없다 first (the ㅅ-irregular rule reaches it, and the
longer match sorts first); 마셨어요 lists 마다 before 마시다 (Wiktionary gives both, and wordfreq ranks 마다 high from
the particle); 496 KRDICT entries show `&quot;` and `&apos;` as text (question 8). The device recognizer does not
read hanja.

Merge build 0805427 (`languages/main`): the engine's instrumented suite (29) passed. NF9 release APKs against v0.2.3:
arm64-v8a 51.2 MB (48.4), armeabi-v7a 46.0 MB (43.6), x86_64 52.0 MB (49.1), universal 76.8 MB (71.8); of the
2.9 MB (x86_64): engine library +1.34, ML Kit models +0.52, dex +0.41, resources +0.33, the hanja dictionary
+0.17, language bundles and catalog +0.06; since 2026-10-14 +0.5 MB per ABI.

### 2026-10-15, builds 16aa855 to the frequency chip names, Pixel_10_Pro (API 37)

Scope: the new Chinese cases on `languages/zh`, English phrasal verbs (LK12) and frequency chip names (POP19); the
engine's instrumented tests (25).

Results: every case passed; the dates are in the Checked column.

Moved from N: OCR16, LK9, LK12, POP19, AUD6, ANK9 and LNG13 to F; LK11 to E (instrumented only); LK10, POP17, POP18 and
DIC17 to O.

Findings, fixed: a lookup starting at a radical look-alike (⽇ for 日) found nothing in Japanese and Chinese.

Changed by decision rows: deleting a popup font asks first (POP18); English phrasal verbs in the past found only the
verb with wty-en-ru, whose phrases have no part of speech, and now find phrases with an adverb particle (LK12,
`yomitan-behavior.md` Lookup item 6); frequency chips shorten long dictionary names (POP19).

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
- English phrasal verbs with a preposition ("looked for") find only the verb when the dictionary gives the phrase no
  part of speech (wty-en-ru); phrases with an adverb particle are found since 2026-10-15 (LK12). Asked in the plan.
- Deleting a downloaded or added popup font does not ask first, unlike a dictionary; asked in the plan (now asks, POP18).
