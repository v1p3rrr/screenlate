# External Links in Popup (Senren-Inspired)

Date: 2026-11-16  
Status: In progress  

## 1. Context and Goals

### Motivation
In language mining (especially Japanese study with Anki and tools like Senren・洗練), learners frequently want to cross-reference looked-up headwords in specialized web resources:
- Visual search (Google Images) to ground word meaning in pictures.
- In-depth Japanese dictionaries and SRS difficulty stats (JPDB).
- Native context sentences mined from media and anime (Nadeshiko, Immersion Kit).

### Objectives in Screenlate
1. **Configurable External Link Icons per Word:** For each candidate word displayed in the popup, provide optional quick-action icons that open the query in the system's default browser via `ACTION_VIEW`.
2. **Japanese vs Multilingual Scoping:**
   - **Japanese (`ja`):** Up to 4 services: Google Images, JPDB, Nadeshiko, Immersion Kit (Jisho excluded per design).
   - **Other languages (61 languages):** Exclusively Google Images, automatically targeting the regional Google domain for that language (e.g. `google.ru`, `google.de`, `google.fr`, `google.com`).
3. **Adaptive Visual Layout:**
   - **1 external link active:** Placed inline within `.entry-actions` (alongside Copy, Audio, Anki), adding minimal horizontal footprint (~28px). If the word does not fit on the line, standard flex wrapping moves actions below or wraps the word.
   - **> 1 external links active:** Rendered on a dedicated second row (`.entry-external-links`) aligned to the right below the primary actions, preventing long headwords or tags from being squeezed.
4. **Individual Opt-in via Settings:** Disabled by default. Configured in "Popup settings" (`PopupSettingsScreen.kt`) under a dedicated "External links" section with individual toggle switches. The screen dynamically adapts to the selected language profile (showing all 4 for Japanese, only Google Images for others).
5. **Zero Raster Bloat:** Crisp, theme-adaptive monochrome SVG vector icons styled with `--muted` and `--accent`.

---

## 2. Decision Table

| Decision | Settled Option | Rationale |
|---|---|---|
| **Supported services (JA)** | **Google Images, JPDB, Nadeshiko, Immersion Kit** | Matches Senren without Jisho (user requirement). Covers visual, dictionary/SRS, and media context. |
| **Supported services (Non-JA)** | **Google Images only** (with language-specific regional domain) | User requirement; external Japanese services are irrelevant for other languages, while image search is universally valuable. |
| **Link activation default** | **All disabled by default (`false`)** | Preserves existing compact popup layout for users who do not need web redirects. |
| **Settings UI placement** | **Inside language card/section above fonts (`PopupTextSections.kt`)** | Moved into language section right above `FontChoice`. Shows all 4 toggles for Japanese, only Google Images for others. Includes section header and divider. |
| **Popup layout (1 link)** | **Inline inside `.entry-actions`** | Takes only one extra button slot (~28px); wraps cleanly if word is very long. |
| **Popup layout (>1 links)** | **Inside `.entry-info` row following frequency and reading chips** | Flow immediately after chips left-to-right. Eliminates detached floating row under actions. |
| **Anki `COALESCE_NEW` icon** | **Material Design `auto_awesome` (sparkle / star vector)** | Crisp, unambiguous vector icon replacing distorted micro-text `ic_new_label`. |
| **Browser interaction** | **System default browser via `ScreenlateBridge.onOpenUrl`** | Uses established `ACTION_VIEW` intent in `OverlayController.kt`, automatically docking the overlay on click. |
| **Icon visual style** | **Monochrome SVG vectors (18×18 px in 28×28 px touch target)** | Consistent with `copy`, `audio`, and `add` icons; reacts to active theme colors (`--muted` / `--accent`). |

---

## 3. URLs and Query Mapping

| Service | Language | URL Template | Parameter / Note |
|---|---|---|---|
| **Google Images** | Japanese (`ja`) | `https://www.google.co.jp/search?q={word}&tbm=isch` | Uses Japanese Google domain |
| **Google Images** | Other languages | `https://{domain}/search?q={word}&tbm=isch` | Regional ccTLD (e.g. `google.ru`, `google.de`, `google.fr`, `google.com`) |
| **JPDB** | Japanese (`ja`) | `https://jpdb.io/search?q={word}` | Headword expression query |
| **Nadeshiko** | Japanese (`ja`) | `https://nadeshiko.co/search/sentence?query={word}` | Sentence search query |
| **Immersion Kit** | Japanese (`ja`) | `https://www.immersionkit.com/dictionary?keyword={word}` | Anime sentence search query (requires `www.` to avoid query-stripping redirect) |

---

## 4. Architecture and Component Changes

### 4.1. `core:common` Regional Google Domains (`GoogleDomains.kt`)
Create `com.vpr.screenlate.core.common.net.GoogleDomains`:
- Maps `Language` to its regional Google domain (e.g. `Language.RUSSIAN -> "www.google.ru"`, `Language.GERMAN -> "www.google.de"`, `Language.JAPANESE -> "www.google.co.jp"`, etc.).
- Fallback to `"www.google.com"` with `hl={code}` for languages without dedicated country domains.

### 4.2. `overlay` Settings Storage (`PopupAppearanceRepository.kt` & `PopupAppearance.kt`)
1. Extend `PopupAppearance`:
   ```kotlin
   data class PopupAppearance(
       ...
       val externalGoogleImages: Boolean = false,
       val externalJpdb: Boolean = false,
       val externalNadeshiko: Boolean = false,
       val externalImmersionKit: Boolean = false,
   )
   ```
2. Add DataStore preference keys and setter functions:
   - `popup_external_google_images` (Boolean)
   - `popup_external_jpdb` (Boolean)
   - `popup_external_nadeshiko` (Boolean)
   - `popup_external_immersion_kit` (Boolean)

### 4.3. WebView Appearance Bridge (`PageAppearance.kt`)
In `PageAppearance.build`:
- Populate active external links configuration and the resolved Google domain for the language:
  ```kotlin
  putJsonObject("externalLinks") {
      put("googleImages", appearance.externalGoogleImages)
      put("jpdb", appearance.externalJpdb)
      put("nadeshiko", appearance.externalNadeshiko)
      put("immersionKit", appearance.externalImmersionKit)
      put("googleDomain", GoogleDomains.domain(support.language))
  }
  ```

### 4.4. Frontend Popup Rendering (`popup.js` & `popup.css`)
1. **SVG Icons in `ICONS`:**
   - `externalGoogleImages`: clean camera/photo frame vector.
   - `externalJpdb`: stylized vector "JP" monogram.
   - `externalNadeshiko`: stylized 5-petal flower vector.
   - `externalImmersionKit`: media card with play triangle vector.
2. **State & Appearance:**
   - Store `externalLinks` in `popup.js` on `setAppearance(appearance)`.
3. **Entry Action Rendering:**
   - For each word entry, determine available links:
     - If `result.lang === 'ja'` (or page language `ja`): include enabled Japanese links (Google Images, JPDB, Nadeshiko, Immersion Kit).
     - Otherwise: include only Google Images (if enabled), using `externalLinks.googleDomain`.
   - **If exactly 1 link is active:**
     - Append the icon button directly to `.entry-actions`.
   - **If > 1 links are active:**
     - Create an `.entry-external-links` element and append it to `.entry-head` as a secondary row.
   - Attach click handlers calling `ScreenlateBridge.onOpenUrl(url)`.
4. **CSS Layout Rules:**
   - `.entry-actions`: `flex-shrink: 0; margin-left: auto; display: flex; gap: 2px;`
   - `.entry-external-links`: `flex-basis: 100%; display: flex; justify-content: flex-end; gap: 2px; order: 2;`
   - Responsive wrap ensures if the word is long, buttons wrap below rather than squishing text.

### 4.5. Settings UI (`PopupSettingsScreen.kt` & `PopupAppearanceViewModel.kt`)
1. In `PopupAppearanceViewModel`:
   - Add toggles `setExternalGoogleImages`, `setExternalJpdb`, `setExternalNadeshiko`, `setExternalImmersionKit`.
2. In `PopupSettingsScreen`:
   - Add `ExternalLinksCard(appearance, isJapanese, viewModel)`:
     - SectionCard title: "Внешние ссылки" (`popup_external_links_title`).
     - `SwitchRow` for Google Images (`popup_external_google_images`).
     - If `isJapanese`:
       - `SwitchRow` for JPDB (`popup_external_jpdb`).
       - `SwitchRow` for Nadeshiko (`popup_external_nadeshiko`).
       - `SwitchRow` for Immersion Kit (`popup_external_immersion_kit`).

### 4.6. Strings and Localization
- Add keys in `values/strings.xml`:
  - `popup_external_links_title`: "External links" / "Внешние ссылки"
  - `popup_external_google_images`: "Google Images" / "Google Картинки"
  - `popup_external_google_images_hint`: "Open image search in browser" / "Поиск изображений в браузере"
  - `popup_external_jpdb`: "JPDB" / "JPDB"
  - `popup_external_jpdb_hint`: "Open vocabulary entry in jpdb.io" / "Словарная статья на jpdb.io"
  - `popup_external_nadeshiko`: "Nadeshiko" / "Nadeshiko"
  - `popup_external_nadeshiko_hint`: "Search sentence examples in nadeshiko.co" / "Примеры предложений на nadeshiko.co"
  - `popup_external_immersion_kit`: "Immersion Kit" / "Immersion Kit"
  - `popup_external_immersion_kit_hint`: "Search anime sentences in immersionkit.com" / "Примеры из аниме на immersionkit.com"
- Localize all keys across all 17 supported locales.

---

## 5. Verification Plan

1. **Unit Tests:**
   - `GoogleDomainsTest`: Verify correct domains for Japanese, Russian, German, French, English, and fallbacks.
   - `PopupAppearanceRepositoryTest`: Verify reading and writing all 4 external link preferences.
   - `PageAppearanceTest`: Verify `externalLinks` JSON payload structure.
2. **Page Script Tests (`scripts/page-tests` / `popup.test.mjs`):**
   - Single link enabled: verified appended into `.entry-actions`.
   - Multiple links enabled: verified rendered in `.entry-info` following chips.
   - Language filtering: non-JA entry with all 4 enabled only displays Google Images.
   - Click action: verify click dispatches `ScreenlateBridge.onOpenUrl` with correctly encoded URL.
3. **Gradle Suite & Lint:**
   - `./gradlew testDebugUnitTest lintDebug assembleDebug`
   - `TranslationsTest` passing across all 17 locales.

---

## 6. Changelog

- **2026-11-17:**
  - Removed `ExternalLinksCard` from `PopupSettingsScreen.kt`.
  - Moved external links switches into `LanguageCard` / font card in `PopupTextSections.kt` directly above `FontChoice`.
  - Added sub-headers: "Внешние ссылки" (`popup_external_links_title`) and "Шрифт" (`popup_font_title`) separated by dividers.
  - Multiple external links in `popup.js` moved from detached row into `.entry-info` immediately following frequency and pitch chips left-to-right.
  - Single external link remains in `.entry-actions` next to Copy/Audio/Anki.
  - Replaced distorted `ic_new_label.xml` (micro-text NEW) with Material Design `auto_awesome` vector for `OverwriteMode.COALESCE_NEW`.
  - Replaced generic play button icon for Immersion Kit with brand yuzu citrus fruit SVG vector matching official website logo.
  - Fixed Immersion Kit search URL to include `www.` (`https://www.immersionkit.com/dictionary?keyword={word}`), preventing apex redirect from stripping query parameters.

