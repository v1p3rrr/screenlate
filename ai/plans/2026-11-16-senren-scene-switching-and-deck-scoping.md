# Senren Scene Switching, Field Append and Deck-Scoped Duplicate Resolution

Date: 2026-11-16  
Status: In progress

## 1. Context and Goals

### Motivation
Senren (洗練) is a popular Anki note type for Japanese language mining (visual novels, anime, novels, manga). Its key feature, **Scene Switching**, allows learners to attach multiple contexts (example sentences, screenshots, audio clips) to a single vocabulary note rather than creating duplicate cards for the same word. In Anki, users can switch between scenes using keyboard shortcuts (`Ctrl + Left/Right`) or clicks.

In Yomitan, users configure note duplicate behavior to allow updating existing cards, with context fields (`Sentence`, `Picture`, `sentenceAudio`) configured to **Append**, while core dictionary fields (`Expression`, `Reading`, `Glossary`) are preserved.

### Objectives in Screenlate
1. **First-class Support for Appending/Scene Mining:** Provide an explicit `APPEND` ("Дополнить") duplicate behavior alongside `PREVENT`, `OVERWRITE`, and `NEW`, pre-configuring context fields for scene appending and vocabulary fields for preservation.
2. **Universal `<br>` Separator for Append:** Automatically separate appended text/sentence contexts with HTML line breaks (`<br>`), while joining media tags (`<img>`, `[sound:...]`) directly without extra breaks. No extra separator UI needed.
3. **Compact Field Header Badge UI (Variant 1):** Place the overwrite mode selector (`[ ➕ Дополнять ▾ ]`) directly in the header row of each field next to the field name. This takes zero extra vertical space and eliminates long text labels.
4. **Safe Deck-Scoped Updates (Fixing Yomitan's Global Overwrite Flaw):** When checking duplicates across the entire collection (`duplicateScope = COLLECTION`), restrict updates/appends to notes belonging to the active target deck (via a toggle enabled by default). If a duplicate exists only in a *different* deck, do not mutate that external deck; instead, create a clean new note in the target deck. When multiple duplicates exist, prioritize the note in the target deck (and the most recent note if multiple exist within the deck).
5. **Idempotency and Edge-Case Safety:** Ensure empty fields don't gain dangling `<br>`, identical sentences aren't duplicated on accidental repeated taps, and media tags (`<img>`, `[sound:...]`) join cleanly.

---

## 2. Decision Table

| Decision | Settled Option | Rationale |
|---|---|---|
| **Append separator** | **Fixed universal `<br>` for text, empty for media** (User decision) | Matches Yomitan/Anki standard HTML field model; completely eliminates bulky separator UI controls. |
| **Field overwrite UI layout** | **Variant 1: Badge button directly in the field header row** (`Field Name` ... `[ ➕ Дополнять ▾ ]`) | Zero extra vertical height, clean visual scanning across 6-10 fields, no text clutter or separator subtitles. |
| **Duplicate behavior UI** | **Dedicated 4-way selector: 🛑 Запретить, 🔄 Заменить, ➕ Дополнить, 📑 Создать** | "Заменить" (Overwrite) sounds destructive. "Дополнить" (Append) explicitly communicates scene/context collection and auto-configures field defaults. |
| **Duplicate behavior layout** | **Compact 4-segment or 2×2 grid with icons and localized dynamic hint** | Fits 360dp mobile screens without text truncation; one-line explanation updates dynamically below. |
| **Deck scoping when searching collection** | **`duplicateScopeDeckOnly` toggle (enabled by default)** | Displayed when `duplicateScope == COLLECTION` and behavior is `OVERWRITE` or `APPEND`. Prevents silently mutating cards in external decks. |
| **Multi-duplicate resolution** | **Current deck priority, then most recent note (`maxByOrNull { it.id }`)** | Unlike Yomitan (which blindly overwrites the oldest note returned by SQLite regardless of deck), Screenlate prioritizes the target deck and the latest note. |
| **Idempotency guard** | **Duplicate segment check: `existing.split("<br>").none { it.trim() == new.trim() }`** | Prevents accidental double-taps from repeating the exact same sentence, while avoiding false positives of simple substring `contains`. |

---

## 3. Architecture and Component Changes

### 3.1. `core:anki` Data Models (`AnkiSettings.kt`)
1. **Expand `DuplicateBehavior`:**
   ```kotlin
   enum class DuplicateBehavior {
       PREVENT,
       OVERWRITE,
       APPEND,
       NEW,
   }
   ```
2. **Expand `AnkiSettings`:**
   ```kotlin
   @Serializable
   data class AnkiSettings(
       ...
       val overwriteModes: Map<String, OverwriteMode> = emptyMap(),
       val savedTemplates: Map<String, NoteTemplate> = emptyMap(),
       ...
       val duplicateScope: DuplicateScope = DuplicateScope.COLLECTION,
       val duplicateScopeDeckOnly: Boolean = true,
       val duplicateAllModels: Boolean = false,
       val duplicateBehavior: DuplicateBehavior = DuplicateBehavior.PREVENT,
   )
   ```
3. **Enhance `OverwriteMode.apply` with `<br>` separator and idempotency:**
   ```kotlin
   fun apply(existing: String, new: String): String = when (this) {
       COALESCE -> existing.ifEmpty { new }
       COALESCE_NEW -> new.ifEmpty { existing }
       OVERWRITE -> new
       SKIP -> existing
       APPEND -> when {
           existing.isEmpty() -> new
           new.isEmpty() -> existing
           // Media tags (<img, [sound:) concatenate directly without <br>
           isMediaTag(existing) || isMediaTag(new) -> if (existing.contains(new)) existing else existing + new
           // Sentences and text join with <br>, avoiding duplicate identical sentences
           existing.split("<br>").any { it.trim() == new.trim() } -> existing
           else -> "$existing<br>$new"
       }
       PREPEND -> when {
           existing.isEmpty() -> new
           new.isEmpty() -> existing
           isMediaTag(existing) || isMediaTag(new) -> if (existing.contains(new)) existing else new + existing
           existing.split("<br>").any { it.trim() == new.trim() } -> existing
           else -> "$new<br>$existing"
       }
   }
   ```

### 3.2. `core:anki` Backend and Note Logic (`AnkiDroid.kt` & `AnkiNotes.kt`)
1. **`ExistingNote` with deck tracking:**
   ```kotlin
   data class ExistingNote(
       val id: Long,
       val modelId: Long,
       val fields: List<String>,
       val deckIds: Set<Long> = emptySet(),
   )
   ```
2. **`AnkiDroid.findDuplicates`:**
   Populate `noteDecks(it.id)` into `ExistingNote`.
3. **`AnkiNotes.add` Scoped Selection:**
   - Filter candidate notes by `modelId`.
   - Calculate active target decks (`setOf(deckId)` or root deck + subdecks if `duplicateScope == DECK_ROOT`).
   - Identify in-deck candidates: `matchingNotes.filter { note -> note.deckIds.any { it in targetDecks } }`.
   - Resolution order:
     1. If in-deck candidate exists: pick the most recent (`maxByOrNull { it.id }`).
     2. If no in-deck candidate exists:
        - If `duplicateScopeDeckOnly == false` (Yomitan legacy fallback): pick most recent out-of-deck note.
        - If `duplicateScopeDeckOnly == true` (default): return `null` (target note not found in deck).
   - If target note is found:
     Merge fields with `mode.apply(existing, new)` and call `anki.updateNote(...)`.
   - If target note is `null` (because duplicate only exists in another deck):
     Call `anki.addNote(modelId, deckId, fields, tags)` to create a new note in the active deck!

### 3.3. `app` Settings UI (`AnkiSettingsScreen.kt` & `AnkiSettingsViewModel.kt`)
1. **4-Segment Duplicate Behavior Selector:**
   - `[ 🛑 Запретить ] [ 🔄 Заменить ] [ ➕ Дополнить ] [ 📑 Создать ]`
   - Dynamic localized helper text below.
2. **Smart Default Application on selecting `APPEND`:**
   - When switching to `APPEND`, `AnkiSettingsViewModel` auto-assigns:
     - Fields containing `{sentence}`, `{screenshot}`, `{picture}`, `{audio}` -> `OverwriteMode.APPEND`.
     - Fields containing `{expression}`, `{reading}`, `{glossary}`, etc. -> `OverwriteMode.COALESCE`.
3. **Deck-Only Scope Switch:**
   - When `duplicateScope == COLLECTION` and behavior is `OVERWRITE` or `APPEND`, display `SwitchRow`:
     `anki_duplicate_deck_only` ("Изменять только в текущей колоде" / "Modify notes in current deck only").
4. **Header Row Badge UI for Field Overwrite Mode (`TemplateField`):**
   - In each field item, the field name row contains the label on the left and a compact badge button on the right:
     `Field Name` ............................ `[ ➕ Дополнять ▾ ]`
   - Tapping opens a dropdown menu with clear icons and concise titles (🛡️ Сохранять, 🔄 Заменять, ➕ Дополнять, ⬇️ В начало, 🚫 Не менять, 🆕 Брать новое).
   - No separator rows or subtitles needed. Zero vertical height penalty!

### 3.4. `overlay` Popup Behavior (`PopupNotes.kt` & `popup.js`)
- `PopupNotes.kt` already routes `AddResult.Updated` and `AddResult.Added` into `report(...)` returning `"added"` and updating openable note IDs.
- For `DuplicateBehavior.APPEND`, when in-deck note exists, tapping adds/appends and turns button into 📖. When duplicate is only in another deck, it adds a new note to the target deck.
- Hold menu retains "Open in AnkiDroid", "Add without picture", "Add anyway".

### 3.5. Localization (`strings.xml`)
Add localized keys across all 17 supported languages:
- `anki_behavior_append` ("Дополнить" / "Append")
- `anki_behavior_append_hint` ("Дописывает новые предложения и скриншоты к карточке (для смены сцен Senren)")
- `anki_duplicate_deck_only` ("Изменять только в текущей колоде")
- `anki_duplicate_deck_only_hint` ("Если дубликат найден в другой колоде, не менять его, а создать новую карточку в текущей")

---

## 4. Edge Cases and Mitigations

1. **Empty Fields:** If `existing` is empty, no `<br>` is inserted. If `new` is empty, existing is preserved without extra breaks.
2. **Accidental Double Taps:** `split("<br>").any { it.trim() == new.trim() }` prevents identical text from duplicating if tapped again.
3. **Media Tags Concatenation:** `<img...>` and `[sound:...]` concatenate directly without `<br>`.
4. **Multiple In-Deck Duplicates:** If user previously added duplicates in the same deck, `maxByOrNull { it.id }` targets the most recently created card.
5. **Backwards Compatibility of Settings:** `duplicateScopeDeckOnly` serializes with default value `true`; older app versions reading newer backups safely ignore unknown keys without resetting settings.

---

## 5. Verification and Test Strategy

1. **Unit Tests (`core:anki`):**
   - `AnkiNotesTest`:
     - Test `DuplicateBehavior.APPEND` with universal `<br>` for sentences and empty for media tags.
     - Test idempotency (appending identical sentence does not duplicate).
     - Test deck scoping: duplicate in deck A vs deck B when `duplicateScopeDeckOnly = true` creates a new note in deck B.
     - Test fallback when `duplicateScopeDeckOnly = false`.
     - Test resolution order when multiple duplicate notes exist (prioritizes current deck, picks newest by ID).
   - `OverwriteModeTest`:
     - Test `apply` with empty existing, empty new, sentence `<br>`, and identical content.
2. **UI & ViewModel Tests (`app`):**
   - `AnkiSettingsViewModelTest`:
     - Test auto-configuration of field overwrite modes when switching `duplicateBehavior` to `APPEND`.
     - Test toggling `duplicateScopeDeckOnly`.
3. **Translation Tests:**
   - Run `TranslationsTest` to verify all 17 locales have matching format arguments and required string keys.
4. **No Emulator Rule:**
   - Execute all verification through `./gradlew testDebugUnitTest` and node page script tests (`npm --prefix scripts/page-tests test`). Do NOT start an Android emulator.

---

## 6. Execution Phases

- [ ] **Phase 1: `core:anki` Models & Logic**
  - Add `DuplicateBehavior.APPEND` and `duplicateScopeDeckOnly` to `AnkiSettings`.
  - Update `OverwriteMode.apply` with `<br>` separator and segment idempotency.
  - Add `deckIds` to `ExistingNote` and populate in `AnkiDroid.findDuplicates`.
  - Implement target-deck-scoped resolution in `AnkiNotes.add`.
  - Add unit tests in `AnkiNotesTest`.
- [ ] **Phase 2: UI in `app` & Strings**
  - Add strings to `values/strings.xml` and `values-ru/strings.xml` (and other 15 locales).
  - Update `AnkiSettingsScreen.kt` with 4-way `DuplicateBehavior` selector and `duplicateScopeDeckOnly` switch.
  - Integrate `OverwriteModePicker` badge button directly into `TemplateField` header row.
  - Update `AnkiSettingsViewModel.kt` to auto-populate suggested append modes.
- [ ] **Phase 3: Integration, Regression Tests & Verification**
  - Verify `PopupNotes.kt` with new `DuplicateBehavior.APPEND`.
  - Run full test suite (`testDebugUnitTest`, `TranslationsTest`, page script tests).
  - Update `ai/status.md` and docs.
  - Update `AnkiSettingsViewModel.kt` to auto-populate suggested append modes and separators.
- [ ] **Phase 3: Integration, Regression Tests & Verification**
  - Verify `PopupNotes.kt` with new `DuplicateBehavior.APPEND`.
  - Run full test suite (`testDebugUnitTest`, `TranslationsTest`, page script tests).
  - Update `ai/status.md` and docs.
