# Overlay UX & Aim Dot Color Plan

**Date:** 2026-11-17
**Branch:** `features/overlay-ux-fixes`

## 1. Objectives

1. **Bubble aim dot color and contrast outline bug:** When a custom bubble color is selected (or any light bubble color is used), the small aim dot (`LayerView`) currently stays in the app theme color and keeps a hardcoded white outline. It must follow the bubble color and switch its outline to dark charcoal (`#1C1B20`) on light colors, matching `BubbleView`.
2. **Close popup on tap outside:** Add a separate Auto-hide toggle in Popup settings (`Popup → Auto-hide`, default OFF). When enabled, tapping anywhere outside the open popup closes it and consumes the touch so it does not reach the underlying app; when disabled, existing pass-through behavior stays unchanged.
3. **Remember screenshot crop area:** Add a global toggle in Anki settings (`Anki → Adding notes`, default OFF) and a synchronized toggle in the crop editor bottom controls. When enabled, the last confirmed crop region for screen screenshots is remembered (separately for portrait and landscape orientations) and restored on the next crop; when disabled, the initial crop frame defaults to the text paragraph around the word as before.

## 2. Decision Table

| Question | Options Considered | Decision | Rationale |
|---|---|---|---|
| **Outside tap touch handling** | A. Consume outside tap (only close popup) when toggle is ON; keep pass-through when OFF<br>B. Pass tap through to underlying app while closing popup | **A. Consume outside tap when ON (default OFF)** | Prevents accidental clicks/dialogue advances in games, VNs, and videos when closing the popup, while preserving existing behavior when the option is off. |
| **Remember crop area placement & scope** | A. `Anki → Adding notes` (below "Screenshot by default"), global across all languages<br>B. Per-language in `AnkiSettings` | **A. `Anki → Adding notes`, global across all languages** | Screen crop geometry is tied to the app/media layout on screen rather than language, and global state syncs cleanly in both directions with the crop editor. |
| **Camera photo applicability** | A. Screen screenshots only (separate portrait & landscape); camera photos keep paragraph focus<br>B. Both screen screenshots and camera photos | **A. Screen screenshots only (portrait & landscape)** | Camera photos vary wildly in framing and aspect ratio; keeping crop memory for screen screenshots avoids misaligned frames on photos and hides the toggle on photo crops. |
| **In-editor toggle behavior** | A. Keep current frame unchanged when toggling; only affects saving on "Add" and next open<br>B. Jump frame immediately if untouched | **A. Keep current frame unchanged** | Predictable and never discards or moves the user's current frame unexpectedly; takes effect on "Add" and subsequent opens. |

## 3. Architecture & Changes

### 3.1 Aim Dot Color & Contrast Outline
- **`overlay/.../ui/LayerView.kt`**:
  - Keep `primaryColor` for the translucent word highlight box (`wordPaint`).
  - Add `aimColor` property for the aim dot (`aimPaint` opaque RGB + `aimOutline` via `PaletteGenerator.bubbleForegroundColor`).
  - In `eInk` mode, set `aimPaint.color = Color.BLACK` and `aimOutline.color = Color.WHITE`; when `eInk` turns off, restore both from `aimColor`.
  - Extract pure helper `aimColors(seed: Int, eInk: Boolean): Pair<Int, Int>` for JVM unit testing.
- **`overlay/.../OverlayController.kt`**:
  - Bind `layerView.aimColor` alongside `bubbleView.bubbleColor` both on startup and in the settings/palette `combine` collector, while keeping `layerView.primaryColor = themeSeed`.

### 3.2 Close Popup on Outside Tap
- **`overlay/.../settings/OverlaySettingsRepository.kt`**:
  - Add `hideOnOutsideTap: Boolean = false` to `OverlaySettings` with DataStore key `popup_hide_outside_tap` and `setHideOnOutsideTap(enabled: Boolean)`.
- **`overlay/.../ui/OverlayWindows.kt`**:
  - Add `outsideCatcherParams()` (`FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE`, 1×1 when inactive, `MATCH_PARENT` and touchable when active).
- **`overlay/.../popup/PopupController.kt`**:
  - Add a transparent `outsideCatcher` view attached immediately below `page.container` (using `OverlayWindows.addAll` in `attach()` and removing both in `detach()`). Window Z-order from bottom to top: `layerView` → `outsideCatcher` → `page.container` → `bubbleView` → dynamic `BubbleMenu`/`CropEditor`.
  - Add `var hideOnOutsideTap: Boolean = false` updating `outsideCatcher` visibility/touchability whenever `visible && !concealed && hideOnOutsideTap`.
  - On `outsideCatcher`, consume `ACTION_DOWN`..`ACTION_UP` and call `callbacks.onClose()` on non-cancelled `ACTION_UP` (ignoring `ACTION_CANCEL` from system edge gestures, matching `BubbleMenu`). Reset pressed state in `hide()`/`detach()`.
- **`overlay/.../OverlayController.kt`**:
  - Forward `popup.hideOnOutsideTap = new.hideOnOutsideTap` in `applySettings`.
- **`app/.../settings/PopupSettingsScreen.kt` & `PopupAppearanceViewModel.kt`**:
  - Add `SwitchRow` in `AutoHideCard` wired to `setHideOnOutsideTap`.
- **Settings reset & backup:** `popup_hide_outside_tap` starts with `popup_`, so `SettingsKeys` (`SettingsSection.POPUP`) and `BackupPreferences` (`BackupSection.POPUP`) cover it automatically. Add to `SettingsKeysTest`.

### 3.3 Remember Screenshot Crop Area
- **`core/anki/.../settings/CropAreaSettings.kt`**:
  - `NormalizedCrop(left, top, right, bottom)` (`@Serializable`) with `isValid`, `isWhole`, `toBox(bounds)`, and `fromBox(selection, bounds)`.
  - `CropAreaSettings(rememberArea = false, portrait = null, landscape = null)` with `cropFor(bounds)` selecting landscape when `bounds.width > bounds.height`, else portrait.
  - `CropAreaRepository` (`@Singleton`) backed by `DataStore<Preferences>` with keys `anki_remember_crop_area`, `anki_crop_portrait`, `anki_crop_landscape`, exposing `settings`, `cachedSettings`, `current()`, `setRememberArea(Boolean)`, and `saveCrop(selection, bounds)`.
- **`overlay/.../ui/CropEditor.kt`**:
  - Extract pure `CropInitialState.resolve(imageBounds, focus, remembered)` computing `initial`, `frame`, and `whole` cleanly (when `remembered` is whole image, `whole = true` and `frame` falls back to paragraph `focus` or center quarter).
  - Add optional `RememberOption(initial, updates, onChange)` to `CropEditor.edit(...)` returning `CropOutcome(bitmap, selection, rememberArea)` or keeping `edit` returning `Bitmap?` with an `onConfirm: ((selection: Box, remember: Boolean) -> Unit)?` callback (or dedicated `RememberConfig`) so existing `CropEditorTest` calls continue compiling without breakage.
  - When `rememberOption != null` (screen screenshots only), display a compact rounded dark pill above the 3 action buttons with label (`crop_remember_area`) and a styled `Switch` synced in both directions with `updates`/`onChange`. Toggling does not alter the current frame.
- **`overlay/.../anki/PopupNotes.kt`, `OverlayController.kt`, `ScreenlateAccessibilityService.kt`**:
  - Inject `CropAreaRepository` into `ScreenlateAccessibilityService` → `OverlayController.AnkiServices` → `PopupNotes`.
  - For screen screenshots (`!image.fit`), pass `rememberOption` and resolved `remembered` crop when `rememberArea` is on; on confirm, if `rememberArea` is on, persist via `cropAreaSettings.saveCrop(selection, image.bounds)`.
  - For camera photos (`image.fit`), pass `rememberOption = null` and `remembered = null`.
- **`app/.../anki/AnkiSettingsViewModel.kt` & `AnkiSettingsScreen.kt`**:
  - Inject `CropAreaRepository`, expose `rememberCropArea: StateFlow<Boolean?>` and `setRememberCropArea(Boolean)`.
  - Add `SwitchRow` in `AnkiSettingsScreen` under "Adding notes" right below "Screenshot by default".
- **Settings reset & backup (`SettingsReset.kt`, `BackupFormat.kt`, `SettingsKeysTest.kt`)**:
  - Map `anki_remember_crop_area`, `anki_crop_portrait`, `anki_crop_landscape` to `SettingsSection.ANKI` (shared across languages).
  - Map `anki_remember_crop_area` to `BackupSection.ANKI` (device-specific crop coordinates stay out of backups, cleared on reset).

### 3.4 Localization & Documentation
- Add localized strings in all 17 locales (`values`, `ar`, `b+zh+Hans`, `b+zh+Hant`, `de`, `es`, `fa`, `fr`, `it`, `iw`, `ja`, `ko`, `pl`, `pt`, `ru`, `tr`, `vi`) in `app` (`popup_hide_outside_tap`, `popup_hide_outside_tap_hint`, `anki_remember_crop_area`, `anki_remember_crop_area_hint`) and `overlay` (`crop_remember_area`).
- Update `docs/usage.md` where popup auto-hide and screenshot cropping are documented.

## 4. Verification & Acceptance Criteria

1. Custom bubble color (e.g., Emerald, Sunset, custom pastel pink/yellow) updates both `BubbleView` and `LayerView` aim dot; light colors invert both the bubble ring and the aim dot outline to dark charcoal (`#1C1B20`); e-ink mode forces black dot + white outline.
2. With "On tap outside" OFF (default), touches outside the popup pass through unchanged. With it ON, tapping outside closes the popup without passing the touch to the app below, while the bubble above remains fully interactive and edge gestures (`ACTION_CANCEL`) do not falsely trigger dismissal.
3. With "Remember screenshot crop area" OFF (default), screenshot cropping starts around the word's paragraph. With it ON, the last confirmed screenshot crop is restored separately for portrait and landscape; "Whole screen" restores as whole screen with the button offering "Frame" back to the paragraph. Camera photos ignore this setting and hide the in-editor switch.
4. Toggling the switch inside the crop editor immediately updates Anki settings (and vice versa), without moving the current crop frame, and decides whether tapping "Add" saves the frame.
5. Full unit tests, lint, instrumented test compilation, `TranslationsTest`, and JS page tests pass cleanly.
