# Module map and full code review

Owner request (2026-09-29): split the system into functional modules, map how each one is integrated with the others, and later run a code review of the **whole project** (not of recent PRs): **one run per functional module**, each covering the module itself **and its whole integration path** into the modules that use it or that it uses. The review starts only on the owner's explicit command; until then this note is the instruction.

## Gradle modules and dependencies

```
app ──► overlay, core:common, core:ocr, core:anki, dictionary:api, dictionary:engine-hoshidicts, dictionary:render-yomitan
overlay ──► core:common, core:ocr, core:anki, dictionary:api
core:ocr, core:anki, dictionary:api ──api──► core:common
dictionary:engine-hoshidicts ──► dictionary:api          (GPL-3.0, JNI + hoshidicts submodule)
dictionary:render-yomitan (assets only: render.js, anki.js, render.css; GPL-3.0) — loaded by overlay's popup.html
build-logic (convention plugins) — used by every module
```

Non-Gradle integration channels (easy to miss in a review):
- **WebView bridge**: `overlay/web/LookupPage.kt` (`ScreenlateBridge`, `@JavascriptInterface`) ⇄ `overlay/assets/popup/{popup.js,note.js,popup.html,popup.css}` ⇄ `render-yomitan/assets/yomitan-render/{render.js,anki.js,render.css}` (via `../yomitan-render/` in popup.html). JSON contracts: `PageState`, glossary JSON from the engine, note actions.
- **DataStore repositories shared across modules**: `AppSettingsRepository` (core:common), `OverlaySettingsRepository` / `PopupAppearanceRepository` (overlay), `AnkiSettings` / `AudioSettings` (core:anki), `LookupSettings` (dictionary:api). Written by app screens, backup, Yomitan import; read by the overlay service.
- **Room registry**: `DictionaryRepository` / `DictionaryDao` (dictionary:api) — used by engine, lookup, app screens, backup, home problems.
- **WorkManager**: `DictionaryImportWorker` / `DictionaryImports` (imports, downloads, collection conversion), progress read by app (`ImportNotifications`, dictionaries screen, home).
- **Intents**: `OverlayIntents.EXTRA_OPEN` (overlay → app screens), `OverlayServiceStatus` (app home reads the service state), `ProcessTextActivity` (system text selection → search), `UpdateReceiver` (package installer).
- **JNI**: `HoshidictsNative` ⇄ `engine-hoshidicts/src/main/cpp/jni_bridge.cpp` ⇄ hoshidicts submodule (UTF-8 byte arrays in, JSON out).
- **Network**: Lens (core:ocr/lens), audio sources (core:anki/audio, cleartext config, Android 17 local network permission in app/audio), dictionary catalog and downloads, GitHub releases (app/update).
- **Hilt DI**: modules in core:common (`NetworkModule`, `SettingsModule`), dictionary:api (`RegistryModule`), engine (`HoshidictsModule`); the accessibility service field-injects its collaborators.

## Functional modules (one review run each)

Each entry lists the code of the unit, then the integration path the review must follow. Tests of the unit are part of its run.

1. **Build, CI and release** — `build-logic/`, all `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `.github/workflows/{ci,release,instrumented}.yml`, `scripts/` (icon, page-tests, ci-ankidroid-init, debug helpers), versioning (`ScreenlateVersion`), `DownloadAssetsTask` (bundled dictionaries), NOTICE/licenses, signing (never read `keystore.properties` values). Integration: every module's build, asset download into dictionary:api, release notes in `.github/release-notes/`, in-app updater's expectations of release assets (app/update).

2. **Common and language support** — `core/common`: `Language`, `LanguageSupport`, `JapaneseSupport`, `MozcRomaji`, `MappedText`, `geometry/Box`, `Redaction`, `network/LocalNetwork`, `settings/AppSettingsRepository`, `ThemeMode`, DI modules. Integration: lookup start rules and spelling variants (dictionary:api `DictionaryLookup`, `LookupVariants`), OCR script choice (core:ocr/mlkit), sentence rules and Anki markers (core:anki note), audio defaults (core:anki audio), overlay word start and fonts, app theme/e-ink/language settings, redaction in every log call.

3. **OCR** — `core/ocr`: `CompositeOcr`, `OcrEngine`, `OcrModels`, `OcrOptions`, `TextLayout`, `ReadingOrder`, `FocusBand`, `SymbolLag`, `ScreenBands`, `TextOrientation`, `NetworkStatus`, `lens/{LensOcrEngine,LensProtocol,Protobuf}`, `mlkit/MlKitOcrEngine`. Integration: overlay scan flow (`OverlayController.scan`, app text merge with `capture/AccessibilityText`, OCR boost bands, engine chip and ⚠ text, crop focus from line boxes), `overlay/settings` (engines, saving mode), app bubble settings screen, debug OCR test screen. Notes: `ocr-engines.md`, `lens-protocol.md`.

4. **Dictionary registry, imports and catalog** — `dictionary/api`: `registry/*` (Room, storage, titles, updates, index text), `imports/*` (worker, queue, bundled, repair, collection `YomitanBackup` + `RawJsonScanner` + `CollectionSpace`, tag banks), `catalog/DictionaryCatalog` (+ `assets/catalog/dictionaries.json`), `languages/*` (detection, samples, installed languages), `DictionaryEngine` interface (import side). Integration: engine-hoshidicts import implementation, `ScreenlateApplication` (bundled install), app dictionaries screen/VM/languages dialog/`ImportNotifications`/`CatalogOrder`, home problems (missing dictionaries), Yomitan collection import VM, backup (dictionary list and files), About (installed dictionaries). Note: `dictionary-engine.md`.

5. **Lookup and engine** — `dictionary/api`: `DictionaryLookup`, `LookupVariants`, `YomitanSorter`, `model/LookupModels`, `settings/LookupSettings`; `dictionary/engine-hoshidicts`: `HoshidictsEngine`, `HoshidictsNative`, `JapaneseInflections`, `HoshidictsModule`, `cpp/jni_bridge.cpp` (the hoshidicts submodule is third-party: review only our use of it). Integration: overlay lookup (`OverlayController` lookup job, kanji lookups), search screen/VM, `PopupNotes` (sentence furigana, tag notes, styles), app lookup settings, GPL boundary (nothing outside the GPL modules may depend on engine internals). Notes: `yomitan-behavior.md`, `dictionary-engine.md`.

6. **Lookup page and rendering** — `overlay/web/{LookupPage,PageState,SelectionHost}`, `overlay/assets/popup/*`, `dictionary/render-yomitan/assets/yomitan-render/*`. Integration: overlay popup (`popup/PopupController`, placement), search screen (same page), note/audio actions bridge (`PopupNotes`), fonts and CSS injection (`overlay/fonts`), glossary JSON from the engine, page tests in `scripts/page-tests`. Notes: `webview-fonts.md`, `yomitan-behavior.md`.

7. **Anki export** — `core/anki`: `AnkiDroid`, `AnkiBackend`, `AnkiNotes`, `Labels`, `note/{FieldTemplate,NoteTypePresets,Sentence}`, `settings/AnkiSettings`; `overlay/anki/PopupNotes` (note part); `overlay/ui/{CropEditor,CropView,CropFocus}`. Integration: app Anki settings screen/VM, home problems (Anki setup), Yomitan settings importer (templates, deck, model), backup (Anki section), page `anki.js`/`note.js`, screenshot from the overlay (`noteContext`). Owner's Anki data is off limits in any test.

8. **Audio** — `core/anki/audio/{AudioFinder,AudioPages,AudioPlayer,AudioSettings,AudioError}`; app `audio/{AudioSettingsSection,AudioSettingsViewModel,LocalNetworkAccess,AudioErrorText}`; network security config. Integration: `PopupNotes` (play, clip menu, auto-play, `{audio}` in notes), home problems (failed sources card), Yomitan importer (sources), backup (audio section), Android 17 local network permission flow, `MainActivity`.

9. **Overlay runtime** — `overlay/{ScreenlateAccessibilityService,OverlayController,BubbleTileService,OverlayIntents,OverlayServiceStatus}`, `overlay/ui/{BubbleView,LayerView,OverlayWindows}`, `overlay/capture/{ScreenCapturer,AccessibilityText}`, `overlay/popup/{PopupController,PopupPlacement}`, `overlay/settings/OverlaySettingsRepository`. Integration: every core module above (OCR, lookup, page, Anki, audio), app bubble settings, home (service status, background tips), app-text-only and e-ink modes, hidden apps, overlay strings in all locales. Lifecycles, window order, coroutine cancellation and bitmap recycling are the main risks.

10. **Appearance and fonts** — `overlay/fonts/{CssCheck,FontFiles,FontModels,PageAppearance,PageFonts,PopupFonts}`, `overlay/settings/PopupAppearanceRepository`; app `settings/{AppearanceScreen,PopupTextSections,PopupAppearanceViewModel,EInk,LanguageOptions}`, `ui/theme/*`. Integration: lookup page CSS/font serving, search screen, backup (fonts, CSS), Yomitan import (text size, CSS), e-ink effects on the overlay, interface language picker (generated locale config).

11. **App shell, home, search and localization** — `MainActivity`, `MainViewModel`, `ScreenlateApplication`, `navigation/ScreenlateNavHost`, `home/*`, `settings/{SettingsScreen,ProblemReport}`, `background/*`, `search/{SearchScreen,SearchViewModel,ProcessTextActivity}`, `lookup/*` screens, `debug/*`, `ui/components/*`, all `res/values*` string files (14 locales) and `TranslationsTest`. Integration: navigation to every feature screen, home problem cards from dictionaries/Anki/audio, onboarding and service status from the overlay, search through the lookup page, UI rules (scroll, wrap, ⓘ dialogs).

12. **Backup and Yomitan settings import** — app `backup/{BackupArchive,BackupFormat,BackupManager,BackupScreen,BackupViewModel}`, `yomitan/{YomitanSettings,YomitanSettingsImporter,YomitanImportScreen,YomitanImportViewModel,CollectionImportViewModel}`. Integration: every settings repository (app, overlay, popup appearance, lookup, Anki, audio), dictionary registry and files, fonts, local network permission after imports, format versioning and forward compatibility.

13. **Updates, About and logs** — app `update/*` (AppUpdates, Releases, UpdateCards, UpdateReceiver, UpdateSettings, UpdateViewModel), `settings/{AboutScreen,AboutViewModel,LicenseScreens}`, `logs/LogExport`. Integration: GitHub releases and release assets (module 1), PackageInstaller and signing, home update card, privacy of logs across all modules (`Redaction`; logs never hold recognized text, looked-up words, note contents or term URLs).

## How to run each review

- One run per functional module, in the order above unless the owner says otherwise; report findings per run before starting the next.
- Read the unit's code in full, then follow each integration path listed for it into the other side's code (callers and callees), including the non-Gradle channels (JS bridge, DataStore keys, Room, WorkManager, intents, JNI, network).
- Check: correctness and edge cases; coroutine/lifecycle/cancellation and threading (see `docs/architecture.md` → Threading); resource leaks (bitmaps, windows, WebViews, files); error handling and user-facing messages; privacy rules (logs, URLs); the GPL boundary; `Language` as a parameter (no Japanese hard-coding outside Japanese-specific classes); UI rules (scroll, wrap, ⓘ); strings present in all locales; tests covering the logic; docs (`docs/*`, README) matching the behavior.
- Findings are ranked by severity with a concrete failure scenario; fixes follow the usual rules (ask the owner about undecided choices, tests for new logic, verify UI on the emulator).
