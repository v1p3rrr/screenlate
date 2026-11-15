# Forced Offline Mode Plan

**Date:** 2026-11-15  
**Status:** In Progress / Plan Created  
**Target:** Android 7.0 (API 24) to Android 15 (API 35+)

---

## 1. Context & Objectives

Screenlate is architected as an offline-first, privacy-oriented application:
- Dictionary lookups, de-inflections, and pitch accents operate 100% locally on-device through JNI/C++ (`hoshidicts`) and SQLite.
- AnkiDroid integration (duplicate checks, deck search, note creation) operates strictly via local Android IPC (`ContentProvider`).
- Popup WebView UI and scripts load entirely from bundled APK assets via `WebViewAssetLoader`.
- Screen captures and camera previews operate strictly in local memory.
- The app contains zero tracking, analytics, or telemetry.

However, several convenience features connect to external network endpoints:
1. **Cloud OCR:** Google Lens API (`lens.googleapis.com`) for high-accuracy text recognition.
2. **Audio Pronunciations:** Remote audio sources (JapanesePod101, Forvo, custom Yomitan audio servers, Wiktionary).
3. **Sentence Translation:** Web translation endpoints (Bing Translator, Google Translate, Edge translator).
4. **Dictionary Catalog & Updates:** Remote catalog index (GitHub JSON), dictionary ZIP downloads, and periodic update checks.
5. **Web Fonts:** Dynamic downloading of CJK/custom font files.
6. **App Updates:** GitHub Releases API checks.

### Objectives
Provide a user-controlled, fail-safe **"Forced Offline Mode"** (`offlineMode`):
- Guarantees **zero outbound network traffic** at the socket level.
- Immediately routes all features to their on-device offline alternatives without timeouts, connection delays, or network failure alerts.
- Cleanly updates the UI: hides unavailable online actions (such as sentence translation), disables online-only settings, and indicates pure on-device operation.

---

## 2. Decision Table

| Question | Options Considered | Decision | Rationale |
|---|---|---|---|
| **1. Toggle Placement** | A. In App Settings only (Recommended)<br>B. In App Settings + quick switch in popup<br>C. In App Settings + Home banner | **A. In App Settings only** | Matches the owner's choice. Keeps the floating popup uncluttered and clean; offline mode is an intentional global configuration, not an ephemeral toggle. |
| **2. Sentence Translation Button** | A. Completely hidden when offline (Recommended)<br>B. Disabled/greyed out with tooltip | **A. Completely hidden when offline** | Matches the owner's choice. Prevents cluttering the dictionary popup with non-actionable controls when offline. |
| **3. OCR Engine Settings UI** | A. Lock/disable engine selector on "Device" (Recommended)<br>B. Leave selector enabled with dynamic warning hint | **A. Lock/disable engine selector on "Device"** | Matches the owner's choice. Visually makes it 100% clear that cloud recognition cannot run while offline mode is active. |
| **4. Audio / Pronunciation Fallback** | A. Automatically fall back to system TTS if voice is installed (Recommended)<br>B. Play only disk cache; use TTS only if manually added to sources | **A. Automatically fall back to system TTS** | Matches the owner's choice. Ensures users still hear word pronunciations offline whenever system speech synthesis is available. |
| **5. Network Security Layer** | A. OkHttpClient `OfflineInterceptor` (Recommended)<br>B. Per-service manual if-checks | **A. OkHttpClient `OfflineInterceptor`** | Absolute fail-safe guarantee: all HTTP calls pass through the shared `OkHttpClient` singleton; throwing `OfflineModeException` ensures zero packet leakage. |
| **6. Dictionary Catalog Behavior** | A. Show offline notice + allow local .zip import (Recommended)<br>B. Hide dictionary download screen completely | **A. Show offline notice + allow local .zip import** | Users can still install dictionaries anytime from local storage via SAF. |

---

## 3. Architecture & Data Model

### 3.1 Settings & State (`core:common`)

Add `OFFLINE_MODE` preference in `AppSettingsRepository`:
```kotlin
val offlineMode: Flow<Boolean> = dataStore.data.map { it[OFFLINE_MODE] ?: false }
val cachedOfflineMode: Boolean? get() = dataStore.cached { it[OFFLINE_MODE] ?: false }

suspend fun setOfflineMode(enabled: Boolean) {
    dataStore.edit { it[OFFLINE_MODE] = enabled }
}
```

### 3.2 Network Layer (`core:common`)

1. **`OfflineModeException`:** Subclass of `IOException` indicating requests are blocked by user configuration.
2. **`OfflineInterceptor`:** Interceptor added to the shared `@Singleton OkHttpClient` in `NetworkModule.kt`:
```kotlin
class OfflineInterceptor(private val isOffline: () -> Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (isOffline()) {
            throw OfflineModeException("Network requests are disabled by Offline Mode")
        }
        return chain.proceed(chain.request())
    }
}
```
3. **`NetworkStatus`:** Update `isOnline()`:
```kotlin
fun isOnline(): Boolean {
    if (appSettings.cachedOfflineMode == true) return false
    // Existing ConnectivityManager check...
}
```

### 3.3 OCR Engine Integration (`core:ocr` & `overlay`)

- In `OverlayController.kt`:
  ```kotlin
  val effectiveEngines = if (settings.offlineMode) OcrEngines.DEVICE else settings.ocrEngines
  ```
- Lens requests are never prepared or dispatched (`prepareCloud()` is skipped).
- In `RecognitionSettingsScreen.kt`:
  - When `settings.offlineMode` is true:
    - `Segments` for `OcrEngines` is disabled (locked to `OcrEngines.DEVICE`).
    - Hint displays `R.string.bubble_ocr_engines_offline_locked` ("Offline mode is active. Recognition runs on-device only.").

### 3.4 Audio / Pronunciation (`core:anki`)

- In `AudioFinder.kt`:
  - When `isOffline()`:
    - Skip remote sources (`URL`, `CUSTOM_JSON`, `YOMITAN`).
    - Check local cache directory (`cacheDir/audio`). If cached clip exists, return `Pronunciation.Clip`.
    - If not in cache, immediately resolve to `Pronunciation.Speech.of(term, reading, language)` without waiting on remote timeouts.
  - In `AudioPlayer.kt`:
    - Plays cached clip or speaks via on-device `TextToSpeech`.
    - If `TextToSpeech` does not support the language, returns failure cleanly (audio button appears disabled/inactive).

### 3.5 Sentence Translation (`overlay` & `popup.js`)

- In `PageState.kt` / `LookupPage.kt`:
  - Include `offline: Boolean` in the JSON state sent to `popup.js`.
- In `popup.js`:
  ```javascript
  translateButton.hidden = !shown || compact(state) || state.offline;
  ```
- In `SentenceTranslation.kt` / `PopupNotes.kt`:
  - `forNote()` returns `null` immediately without network calls when offline mode is active.

### 3.6 Dictionaries, Fonts & App Updates

- `DictionaryUpdates`: Silenced when offline.
- `DictionaryCatalog`: Shows offline banner; download button disabled; local `.zip` import remains available.
- `PopupFonts`: Remote font downloads skipped; fallback to system fonts.
- `AppUpdates`: Update checks skipped.

---

## 4. Implementation Phases

### Phase 1: Core Settings & Fail-Safe Network Interceptor
- [ ] Add `OFFLINE_MODE` key and accessors to `AppSettingsRepository` (`core:common`).
- [ ] Implement `OfflineModeException` and `OfflineInterceptor` in `core:common:network`.
- [ ] Integrate `OfflineInterceptor` into `NetworkModule.kt`.
- [ ] Connect `AppSettingsRepository.cachedOfflineMode` to `NetworkStatus.isOnline()`.
- [ ] Unit tests for `OfflineInterceptor` and `NetworkStatus`.

### Phase 2: OCR & Camera Adaptation
- [ ] Update `OverlayController.kt` to force `OcrEngines.DEVICE` when offline mode is active.
- [ ] Verify popup OCR source chip displays "On-device" cleanly (`EngineLabel.DEVICE` / `EngineLabel.DEVICE_OFFLINE`) without error icons.
- [ ] Update `RecognitionSettingsScreen.kt` to disable/lock the engine selector when offline mode is active.
- [ ] Update `CameraRecognition.kt` to coerce camera OCR engines to on-device when offline.

### Phase 3: Audio & Translation Adaptation
- [ ] Update `AudioFinder.kt` to bypass remote network requests when offline and fall back immediately to local cache -> `TextToSpeech`.
- [ ] Update `PageState.kt` and `popup.js` to hide the sentence translate button when `state.offline` is true.
- [ ] Update `SentenceTranslation.kt` to skip translation requests for Anki notes when offline.
- [ ] Page script tests in `scripts/page-tests/` for popup button visibility.

### Phase 4: Dictionaries, Fonts & Update Checks
- [ ] Guard `DictionaryUpdates.kt` background work against offline mode.
- [ ] Update `DictionaryCatalog.kt` to avoid remote fetch attempts when offline and rely on bundled/cached assets.
- [ ] Guard `PopupFonts.kt` against downloading external fonts when offline.
- [ ] Guard `AppUpdates.kt` against remote GitHub checks when offline.

### Phase 5: UI, 17 Locales & Verification
- [ ] Add Offline Mode switch card/row in Settings.
- [ ] Add localized string resources across all 17 locales (`values/`, `values-ru/`, `values-de/`, etc.):
  - `settings_offline_mode`
  - `settings_offline_mode_hint`
  - `bubble_ocr_engines_offline_locked`
  - `catalog_offline_notice`
- [ ] Verify with `TranslationsTest` and full JVM test suite (`./gradlew testDebugUnitTest`).
- [ ] Verify page tests (`npm --prefix scripts/page-tests test`).
- [ ] Build and verify on device / emulator.
