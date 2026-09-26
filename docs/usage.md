# Using Screenlate

## First launch

1. Open Screenlate and enable its accessibility service (the start screen links to the settings). The bundled dictionaries install in the background; the start screen shows when they are ready.
2. If the switch in the accessibility settings is greyed out after installing from an APK file: App info → ⋮ → Allow restricted settings.
3. On devices with aggressive battery management, allow Screenlate to run in the background (often called "app launch" or "battery optimization" in the system settings), or the system may stop the service.

## The bubble

| Gesture | Result |
|---|---|
| Pull the bubble away from the screen edge | The screen is scanned; aim at a word to see its entry. |
| Move the floating bubble | The entry follows the aim point. The screen is not scanned again. |
| Tap the floating bubble | Scan again, e.g. after scrolling. Recognized lines flash briefly. |
| Double tap the floating bubble | Switch the aim point between "above the finger" and "bubble center". |
| Drag the bubble to the left or right edge, or press ✕ | Close the entry and dock the bubble. |
| Drag the docked bubble along the edge | Move the dock. |

The Quick Settings tile hides and shows the bubble. The bubble settings let you hide it in chosen apps, pick the dock side and choose the text source:

- **Screen (OCR)**: a screenshot is recognized with Google Lens (an on-device draft appears first).
- **App text first**: the app's own text is used when it exposes character positions, which is exact and works offline; other screens are recognized as usual.

## Entries

- Tap a link inside a definition to look it up; the back arrow returns.
- Tap a kanji in the headword to see its kanji dictionary entry (install KANJIDIC from the catalog first).
- 🔊 plays the pronunciation from the configured audio sources.
- ➕ adds a note to Anki; hold it to attach a picture: a crop editor opens with the word's paragraph selected.

Text selected in any app can also be looked up through the "Look up in Screenlate" entry of the selection menu, and the Search screen accepts typed text.

## Dictionaries

- The Dictionaries screen lists installed dictionaries by type. Drag the handle to change the order: entries from dictionaries higher in the list come first. Among frequency dictionaries, pick the one used for sorting.
- Import Yomitan archives (`.zip`) from a file, download dictionaries from the catalog, or import a Yomitan "dictionary collection" export (`.json`) to bring over everything installed in Yomitan at once.
- "Check for updates" asks each dictionary's index whether a newer revision exists.

## Anki

1. Install AnkiDroid, open Anki and audio settings in Screenlate and allow access to AnkiDroid.
2. Choose a deck and a note type. Field templates are pre-filled from the field names; adjust them with the + button, which inserts markers such as `{expression}`, `{reading}`, `{furigana}`, `{glossary}`, `{sentence}`, `{cloze-body}`, `{audio}` and `{screenshot}`.
3. The first field is used for the duplicate check. Duplicates can be searched in the collection, the deck or the deck's root, and be prevented, overwritten or added again.

## Troubleshooting

- **"This app does not allow screenshots."** The app protects its windows. Try the "App text first" source.
- **Only the on-device result appears.** Google Lens was unreachable or took longer than 15 seconds; the on-device result is used instead.
- **The bubble disappears after a while.** The system stopped the service; see the background settings above.
