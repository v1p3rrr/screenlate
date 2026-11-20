# Plan: Translation Picker, Settings Search & Performance Diagnostics (2026-11-20)

## Decision table

| Topic | Decision |
|---|---|
| Translation service selection on long press | Holding the translation button in the popup (both header button and compact inline button) opens a dropdown menu (`showMenu` in `popup.js` / `showTranslationMenu`). The menu lists: 1) "Автоматически (каскад)" / "Auto (cascade)" (default cascade), 2) Bing, 3) Google, 4) Edge. All three services are shown regardless of which are enabled in automatic cascade, allowing manual one-off override. Tapping a service immediately translates with that specific service. |
| Scope of chosen translation & Anki note routing | When a service is chosen via the menu, its translation replaces the current translation block in the popup. If the user creates an Anki note (`+` button), the note's `{sentence-translation}` field receives the translation produced by this chosen service (mirroring `chosenClips` behavior in audio). The manual selection is scoped to the current sentence / popup session and resets back to automatic cascade on a new scan or popup dismissal. |
| Live search in Settings screen | In `SettingsScreen`, the top app bar gets a search action icon. Clicking it reveals a search text field with an immediate clear (`✕`) / back button. The search is a live, deep cross-cutting search across all 13 settings sections (`BUBBLE`, `RECOGNITION`, `LOOKUP`, `DICTIONARIES`, `ANKI`, `AUDIO`, `POPUP`, `TRANSLATION`, `CAMERA`, `APPEARANCE`, `BACKGROUND`, `BACKUP`, `ABOUT`). It indexes option titles, descriptions, section names, and keywords. Tapping a search result navigates directly to that setting's sub-screen (`onOpen(page)`). |
| Performance diagnostics & process exit inspection | In the "About" screen (available in debug/dev builds), display a diagnostic card: current process memory (Total PSS RAM, Java Heap used/max, Native Heap), system memory trimming history (`ComponentCallbacks2.onTrimMemory`), and the previous process exit reason from `ActivityManager.getHistoricalProcessExitReasons` (API 30+) explaining why the last process died (Force-stop by user/OEM, Low Memory Kill, Crash, Freezer, etc.). |
| Floating diagnostics HUD overlay | In "About" (debug/dev builds only), provide a toggle for a floating, draggable diagnostics HUD overlay. When enabled, a semi-transparent floating card is displayed by the accessibility service showing real-time metrics: RAM PSS, Java Heap, thread CPU time, and service lifecycle state (`Idle`, `Scanning`, `OCR`, `Lookup`, `Translate`). The HUD can be dragged anywhere on screen so as not to obscure content. |

## Implementation phases

### Phase 1: Translation service selection on long press & Anki note routing
1. **Frontend (`popup.js`, `popup.test.mjs`):**
   - Update `translateButton` and inline `.action-translate`: use `onTapOrHold(btn, held => { if (held) requestTranslationMenu(btn); else toggleTranslation(); })`.
   - Implement `requestTranslationMenu(button)`: sends `ScreenlateBridge.onTranslationMenu(translationRequests)` and registers pending menu anchor.
   - Implement `showTranslationMenu(request, items)`: renders popup menu using existing `showMenu` helper.
   - On selecting an item: call `ScreenlateBridge.onSelectTranslationService(request, serviceId)`.
   - Update `popup.test.mjs` with coverage for long press, menu rendering, service selection, and translation state updates.
2. **Bridge & Page (`LookupPage.kt`):**
   - Add `onTranslationMenu(request: Int)` and `onSelectTranslationService(request: Int, serviceId: String)` to `LookupPage.Callbacks`.
   - Expose JavascriptInterface methods routing to main thread callbacks.
   - Add `showTranslationMenu(request: Int, items: List<TranslationMenuItem>)` calling JS function.
3. **Controller & Translation logic (`OverlayController.kt`, `SentenceTranslation.kt`, `SentenceTranslator.kt`):**
   - In `SentenceTranslator`: add method to translate with a single specific `TranslationService` (e.g. `translateWith(text, from, target, service)`), returning `TranslationResult`.
   - In `SentenceTranslation`: support translating with an explicit service or cascade, returning `{text, service, sentence}` or error.
   - In `OverlayController`:
     - Maintain `chosenTranslationService: TranslationService?` and `chosenTranslation: String?`.
     - When `onTranslationMenu` is called: build menu items (`auto`, `bing`, `google`, `edge`) with localized labels and send to `showTranslationMenu`.
     - When `onSelectTranslationService` is called: update `chosenTranslationService`, trigger `translate`, update popup UI and store `chosenTranslation`.
     - Clear chosen service/translation on popup hide or new scan.
4. **Anki Note integration (`PopupNotes.kt`):**
   - In `PopupNotes`: check `chosenTranslation` (or translate using `chosenTranslationService` if not yet resolved) when populating `{sentence-translation}` in Anki note values.
   - Add unit tests in `SentenceTranslatorTest.kt` and `PopupNotesTest.kt`.

### Phase 2: Live deep settings search
1. **Settings Index Catalog (`SettingsIndex.kt`):**
   - Create catalog of searchable items across all 13 settings sections.
   - Each item includes: `page: SettingsPage`, `titleRes: Int`, `summaryRes: Int?`, `sectionRes: Int`, `iconRes: Int`, optional keywords.
   - Filter function matching query against localized title, summary, and section strings (case-insensitive, diacritic-insensitive).
2. **UI (`SettingsScreen.kt`, `SettingsScaffold.kt`):**
   - In `SettingsScreen`: add search state (`searchActive: Boolean`, `searchQuery: String`).
   - When search active: top bar shows search text field with focus, clear `✕` button, and back arrow to cancel search.
   - Content area switches from standard 13-category list to lazy list of matching settings items.
   - Each result item shows: section badge/icon, title with highlighted match or clear typography, and summary text.
   - Tapping an item invokes `onOpen(item.page)` to navigate directly to the target screen.
   - Add unit test verifying that all 13 pages are represented and search filtering returns expected results.

### Phase 3: Performance diagnostics & Floating Diagnostics HUD (Dev/Debug)
1. **Diagnostics Collector (`ProcessDiagnostics.kt` / `DiagnosticsRepository.kt` in `core:common` or `app`):**
   - Read memory info: `Debug.MemoryInfo` -> Total PSS in MB, Native Heap in MB, `Runtime.getRuntime()` -> Java Heap used / total in MB.
   - Process thread CPU: `Process.getElapsedCpuTime()` or `SystemClock.currentThreadTimeMillis()`.
   - System memory trim: Register `ComponentCallbacks2` in `Application` to record last `TRIM_MEMORY_*` event with timestamp.
   - Historical process exit: Read `ActivityManager.getHistoricalProcessExitReasons(packageName, 0, 1)` on API 30+ to extract last exit reason, description, and status code.
2. **Diagnostics Card in About (`AboutScreen.kt`, `AboutViewModel.kt`):**
   - When running in debug/dev build (`ApplicationInfo.FLAG_DEBUGGABLE` or `BuildConfig.DEBUG`):
     - Display a Diagnostics section card with live RAM stats, last exit reason, and onTrimMemory status.
     - Add switch row: "Плавающий оверлей диагностики" / "Floating diagnostics HUD".
3. **Floating Diagnostics HUD (`DiagnosticsHud.kt` in `overlay`):**
   - WindowManager overlay view attached to accessibility service when enabled.
   - Semi-transparent, draggable card displaying:
     - `PSS: XX MB | Heap: YY/ZZ MB`
     - `CPU: ~XX ms | State: Idle/Scan/OCR/Lookup/Translate`
   - Touch drag listener allowing moving HUD anywhere on screen.
   - Updates every 1-2 seconds while active.

### Phase 4: Localization, code review, verification & APK build
1. **Localization:** Add UI strings in `values/` and `values-ru/` (and other locales where applicable).
2. **Verification & Tests:**
   - Run Gradle unit tests for `:overlay`, `:core:translate`, `:app`.
   - Run page script tests: `npm --prefix scripts/page-tests test`.
   - Verify lint checks and build debug APK.
3. **Two-axis Code Review:** Run `/code-review` (Standards + Spec) until no critical issues remain.
4. **Final Dev APK & Status update:** Update `ai/status.md` and build minified dev APK.

## Changelog
- 2026-11-20: Initial plan created based on user feedback and approved decisions.
