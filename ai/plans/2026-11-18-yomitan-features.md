# Plan: Yomitan Parity Features & Anki UX Fixes (2026-11-18)

## Decision table

| Topic | Decision |
|---|---|
| `{popup-selection-text}` | Populate from current WebView text selection (`selectedText()`) when adding a note. No extra UI setting. |
| `{clipboard-text}` & `{clipboard-image}` | Populate from Android `ClipboardManager` when used in active Anki field templates (image copied into AnkiDroid media as `<img>`). No extra UI setting. |
| Remember chosen audio clip | Already implemented in `PopupNotes.chosenClips`; no change needed. |
| Collapsible dictionaries in popup | Small chevron/arrow before `.dictionary-name` inside the popup. Tapping collapses/expands that dictionary; collapsed dictionary names persist across popup openings in `PopupAppearanceRepository` (DataStore). No dictionary aliases, no extra clutter in `DictionariesScreen`. |
| Custom text replacements (`textReplacements`) | Per-language ordered list of `{ find, replace, regex, enabled }` rules in `LookupSettingsRepository`. Applied to lookup text before querying (with safe regex validation and timeout/guard against bad patterns). Compact row "Text replacements (N)" inside Lookup settings (language card) opening a management dialog. Imported from Yomitan settings JSON (`translation.textReplacements.groups`). |
| Result grouping mode `merge` | Per-language boolean `mergeVariants` (default `false`) in `LookupSettings`, shown in Lookup settings only for languages with character/reading variants (`LanguageSupport.hasCharacterEntries`). Merges lookup results sharing normalized reading and identical primary-dictionary glossary content (works on already-imported dictionaries without changing `hoshidicts` binary format). Alternative spellings shown compactly beside the headword (`観る・診る`); Anki/audio use primary expression & reading. |
| Harmonic / average frequency in popup | Add `HARMONIC` (`__harmonic__`) and `AVERAGE` (`__average__`) choices directly to the existing "Sort by frequency" selector on `DictionariesScreen` (no new setting in Popup settings). When chosen, entries sort by harmonic/average rank across enabled frequency dictionaries AND the popup displays the harmonic/average chip first before `+N` (which expands all individual frequency dictionaries). |
| Multiple Anki card formats | Deferred to a later milestone to keep Anki settings uncluttered. |
| Anki duplicate behavior selector UI | Restore 3-button `Segments` row (`PREVENT` / `OVERWRITE` / `NEW`) instead of dropdown `Picker`. |
| Anki duplicate button icon bug (`OVERWRITE` mode) | 1) Push duplicate note states progressively (starting from entry 0, without blocking entry 0 on entries 1..N) and reduce initial delay / preserve states across refined scans of the same word. 2) When `duplicateBehavior == OVERWRITE` and `duplicateScope == COLLECTION && duplicateScopeDeckOnly`, check whether a duplicate exists in the target deck before showing `duplicate_overwrite` (`📖↻`) — if duplicates exist only in foreign decks, tapping adds a new note so show `+` (not misleading `📖↻`). 3) Keep switching to `📖` (`added`) after adding/updating in the current scan to prevent accidental double-updates. |
| Hide docked bubble on screenshots | Skipped: Android provides no public API to exclude a visible overlay from system screenshots (`FLAG_SECURE` blocks or blacks out screenshots; `PRIVATE_FLAG_SKIP_SCREENSHOT` is system-only). Screenlate's own Anki screenshots already exclude overlays. |

## Implementation phases

### Phase 1: Anki UX fixes & markers (`{popup-selection-text}`, `{clipboard-text}`, `{clipboard-image}`, `Segments`, duplicate check fix)
1. **`AnkiSettingsScreen.kt`:** Replace `Picker` for `DuplicateBehavior` with `Segments` (3 buttons: Prevent / Update / Add new), keeping the hint below.
2. **Anki duplicate check fix (`AnkiNotes.kt`, `PopupNotes.kt`):**
   - Add `AnkiNotes.duplicateStatus(language, values)` (or `updatableDuplicateIds`) so `PopupNotes.markNotes()` knows when `OVERWRITE` + `COLLECTION` + `duplicateScopeDeckOnly` only matched notes outside the target deck (in which case tapping will add a new note, not update).
   - In `PopupNotes.markNotes()`, emit `page.setNoteStates` progressively per entry (especially entry 0 immediately!) instead of waiting for all N entries sequentially.
   - Preserve existing note states in `popup.js` during refined re-renders of the same word (`continued && refined`).
3. **`{popup-selection-text}` (`popup.js`, `note.js`):**
   - Pass current `selectedText()` from `popup.js` into `NoteData.build(..., { selection })` so `{popup-selection-text}` renders escaped HTML with `<br>` for newlines.
4. **`{clipboard-text}` & `{clipboard-image}` (`PopupNotes.kt`):**
   - When `"clipboard-text"` is in `used`, read primary clip plain text from `ClipboardManager` (escaped HTML with `<br>`).
   - When `"clipboard-image"` is in `used`, check primary clip for an image `Uri` (`content://` with `image/*` MIME), decode & save via `saveScreenshot` / `anki.addMedia`, and fill `{clipboard-image}` with `<img src="...">`!

### Phase 2: Collapsible dictionaries in popup
1. **`PopupAppearanceRepository.kt`:** Add `collapsedDictionaries: Set<String>` (DataStore `stringSetPreferencesKey("popup_collapsed_dictionaries")`) with `setDictionaryCollapsed(title: String, collapsed: Boolean)`. Include in `SettingsKeysTest` & `BackupManager` (if appropriate).
2. **`PageAppearance.kt` & `LookupPage.kt`:**
   - Pass `collapsedDictionaries` list in `PageAppearance.build(...)`.
   - Add JS bridge callback `onToggleDictionary(dictionary: String, collapsed: Boolean)` in `LookupPage` (`OverlayController` & `SearchScreen` persist it via `PopupAppearanceRepository`).
3. **`popup.js` & `popup.css`:**
   - Add a compact chevron button (`.dictionary-toggle`) right before `.dictionary-name`.
   - Clicking toggles `.dictionary.collapsed` on all sections for that dictionary across the popup, updates local `collapsedDictionaries` set, and calls `ScreenlateBridge.onToggleDictionary(dictionary, collapsed)`.
   - When collapsed, only `.dictionary-name` (with chevron) shows; definitions and copy button hide cleanly.

### Phase 3: Harmonic / average frequency sorting & popup chip
1. **Sentinel IDs (`DictionaryLookup.kt`, `YomitanSorter.kt`, `DictionarySettingsRepository.kt`):**
   - Define `FrequencySort.HARMONIC = "__harmonic__"` and `FrequencySort.AVERAGE = "__average__"` alongside real dictionary titles.
   - Implement harmonic mean & arithmetic mean rank computation in Kotlin (matching `note.js`'s rank-based formula) for `YomitanSorter` and `LookupResult.withSortFrequencyFirst`.
2. **`DictionariesScreen.kt` & `DictionariesViewModel.kt`:**
   - When 2+ frequency dictionaries are enabled for a language, offer "Harmonic mean" and "Average" radio options inside the frequency sorting card/list alongside individual frequency dictionaries.
3. **`popup.js` & `popup.css`:**
   - Pass `sortFrequency` (`"__harmonic__"` / `"__average__"` / dictionary title / null) in `noteConfig` (or compute/pass synthetic aggregate frequency group first).
   - When `HARMONIC` or `AVERAGE` is active and a word has 1+ rank-based frequency values, display the aggregate chip first (localized label e.g. `Avg` / `Сред.` or `Harm` / `Гарм.` + value) and let `+N` expand all individual frequency dictionaries!

### Phase 4: Custom text replacements (`textReplacements`) & `merge` variant grouping
1. **Model & persistence (`LookupSettings.kt`, `LookupSettingsRepository.kt`):**
   - Add `TextReplacement(val pattern: String, val replacement: String, val regex: Boolean = false, val enabled: Boolean = true)` and `textReplacements: List<TextReplacement> = emptyList()` per language.
   - Add `mergeVariants: Boolean = false` per language.
   - Apply enabled replacements in `DictionaryLookup` safely (compiling regex with `runCatching`, invalid regex safely skipped, bounded result length to prevent runaway expansion).
   - Import `translation.textReplacements.groups` from Yomitan settings in `YomitanSettings.kt` & `YomitanImportViewModel.kt`!
2. **`merge` grouping (`ShownResults.kt`, `LookupModels.kt`, `popup.js`, `popup.css`):**
   - Add `alternativeExpressions: List<String> = emptyList()` on `TermEntry`.
   - When `settings.mergeVariants` is enabled, merge `LookupResult`s with the same normalized reading and identical primary-dictionary glossary content (preserving primary expression/reading and collecting unique alternative spellings in `alternativeExpressions`).
   - In `popup.js` / `popup.css`, display `alternativeExpressions` compactly beside/after the headword (muted smaller text separated by `・`).
3. **Lookup Settings UI (`LookupSettingsScreen.kt`, `LookupSettingsViewModel.kt`):**
   - In the language card:
     - Show `SwitchRow` "Merge spelling variants" (`lookup_merge_variants`) when `support.hasCharacterEntries`.
     - Show compact navigation/dialog row "Text replacements" (`lookup_text_replacements`) with active count badge → opens dialog to add/edit/toggle/delete/test replacement rules.

### Phase 5: Translations, Tests, Code Review Loop, Merge & Dev Build
1. Add all new strings to all 17 locales (`values*/strings.xml` + `overlay/values*/strings.xml`) and verify with `TranslationsTest`.
2. Add thorough unit tests (Kotlin + JS `popup.test.mjs` / `note.test.mjs`).
3. Run full test & lint suite (`testDebugUnitTest`, `lintDebug`, `scripts/page-tests`).
4. Run `/code-review` skill (Standards + Spec parallel subagents), verify every finding, fix real issues, repeat `/code-review` until zero critical issues remain.
5. Merge `features/yomitan-features` into `main`, push to `origin/main`, update `ai/status.md`, and build local minified ARM64 Dev APK from `main`.
