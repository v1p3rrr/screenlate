# Plan: Dictionary Lookup Fixes & Tap-Lookup in Definitions (2026-11-20)

## Decision table

| Topic | Decision |
|---|---|
| Unambiguous reading-less word merging (`感心`, `思想`) | Merge entries where `reading.isEmpty() \|\| reading == expression` (e.g. from `wty-ja-ja` or `JMnedict`) into an entry that has a distinct reading IF AND ONLY IF all other entries for that expression in the lookup results share the exact same unambiguous reading (e.g. only `かんしん` for `感心`, only `しそう` for `思想`). If multiple distinct readings exist (e.g. `つの` and `かど` for `角`), do NOT merge to prevent false conflation. |
| Reading-less entry harmonic/average frequency | Do NOT compute aggregate (harmonic or average) frequency for entries that lack a distinct reading (`reading.isEmpty() \|\| reading == expression`). In `YomitanSorter`, prefer entries with a distinct reading over entries without one at equal frequency or when frequency is absent. In `popup.js`, hide aggregate frequency chips on reading-less entries. |
| `form_of` redirect with mismatching reading (`それた` -> `逸れる`) | In `form_of.cpp`, when following a `form_of` item (redirecting from a source term to `item.lemma`): if the source entry was in kana or had a known reading (e.g. source reading/expression `それる` from deinflected `それた`), filter out any lemma candidates from `query.query(item.lemma)` whose reading is non-empty and differs from the source reading (e.g. discard `逸れる` with reading `はぐれる`, keep only `逸れる` with reading `それる`). |
| Tap lookup in definitions & examples | Single tap on any word or kanji in `.definition-body` / `.glossary` triggers lookup of the word under tap (`caretRangeFromPoint` in `popup.js`). If results or a single character entry (kanji) are found: pushes the new lookup view onto the navigation stack (`popup.push(state)`), showing the back button `<` in the header. If nothing is found: does nothing (no flicker, no disruption to reading). Long press remains default text selection for copying. Scans for the active profile's language. |
| Tap lookup toggle | Add `tapLookup: Boolean = true` to `PopupAppearance` (persisted in DataStore via `PopupAppearanceRepository`), with a toggle row in `PopupSettingsScreen.kt` ("Всплывающее окно" / "Popup") localized in English and Russian. Default `true`. |

## Implementation phases

### Phase 1: `form_of` reading filtering in C++ engine (`form_of.cpp`)
1. In `form_of.cpp`:
   - Inspect the source result (`results[i]`): extract source reading. If `term.reading.empty()`, but `term.expression` or `results[i].deinflected` is kana, use it as source reading.
   - When iterating over `found->second` (`query.query(item.lemma)`):
     If source reading is known/non-empty, check `term.reading`. If `term.reading` is non-empty and does NOT match the source reading, skip this candidate!
2. Add C++ test cases in `form_of_test.cpp` (or verify with unit tests in `dictionary:engine-hoshidicts`).

### Phase 2: Unambiguous reading-less word merging & harmonic sort fix (`ShownResults.kt`, `YomitanSorter.kt`, `popup.js`)
1. **`ShownResults.kt` (`mergeSameWords`):**
   - For entries where `reading.isEmpty() || reading == expression`:
     Group candidates by `expression`. If all other entries for that `expression` share exactly ONE distinct normalized reading, fold the reading-less entry into that entry (inheriting reading, furigana, and combining definitions/frequencies).
     If there are multiple distinct readings or no entry with a distinct reading, keep it as an independent entry.
2. **`YomitanSorter.kt`:**
   - In `harmonicRank` and `averageRank`: do not compute aggregate rank for entries lacking a distinct reading (`term.reading.isEmpty() || term.reading == term.expression`).
   - In comparator: add `.thenByDescending { it.term.reading.isNotEmpty() && it.term.reading != it.term.expression }` so that entries with a distinct reading always rank above reading-less entries (when frequencies are equal or absent).
3. **`popup.js`:**
   - In `frequencyChip`: do not display synthetic aggregate chip (`Гарм.` / `Avg`) if `reading == expression` or reading is empty.
4. Add unit tests in `ShownResultsTest.kt` and `YomitanSorterTest.kt`.

### Phase 3: Tap-lookup in definitions & toggle setting
1. **Model & persistence (`PopupAppearance.kt`, `PopupAppearanceRepository.kt`):**
   - Add `val tapLookup: Boolean = true` to `PopupAppearance`.
   - Add DataStore key `booleanPreferencesKey("popup_tap_lookup")`.
   - Update `setTapLookup(enabled: Boolean)` in `PopupAppearanceRepository`.
   - Update `PageAppearance.kt` to serialize `tapLookup` into the page configuration JSON.
2. **UI (`PopupSettingsScreen.kt`, strings):**
   - Add strings in `values/strings.xml` and `values-ru/strings.xml`:
     - English: "Lookup on tap in definitions", "Tap words or characters in dictionary definitions to open their lookup"
     - Russian: "Поиск по тапу в определениях", "Нажатие на слова и иероглифы в определениях открывает их карточку"
   - Add `SwitchRow` in `PopupSettingsScreen.kt`.
3. **JS interaction (`popup.js`):**
   - In `popup.js`, add click listener to `.definition-body` / `.glossary`:
     - If `appearance.tapLookup` is false, return.
     - Check `document.caretRangeFromPoint(e.clientX, e.clientY)`.
     - Extract text range starting at click position forward (up to 16 characters for CJK, or word boundary for space-separated languages).
     - Call `ScreenlateBridge.onLookup(scannedText)`.
4. **Kotlin side (`OverlayController.kt` / `LookupPage.kt`):**
   - In `OverlayController.lookupLink`:
     If `lookupResults` is empty and `kanji == null`, DO NOT push an empty error card when triggered by tap-lookup, so tapping on irrelevant punctuation or non-words silently does nothing and doesn't disrupt reading.

### Phase 4: Verification, code review & dev build
1. Run unit tests: Gradle `:dictionary:api:testDebugUnitTest`, `:dictionary:engine-hoshidicts:testDebugUnitTest`, `:overlay:testDebugUnitTest`, `:app:testDebugUnitTest`.
2. Run page script tests: `npm --prefix scripts/page-tests test`.
3. Run `/code-review` skill across the branch changes.
4. Verify and fix all findings.
5. Merge and build local Dev APK.
