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

The Quick Settings tile hides and shows the bubble. Turning the screen closes the entry and docks the bubble on the same side.

## Settings

The start screen keeps the search, the service status and warnings; everything else is under Settings:

- **Bubble**: aim point, dock side, bubble size, word highlight, vibration, apps where the bubble hides, whether the popup starts with the recognized text, and the text source:
  - **Screen (OCR)**: a screenshot is recognized in the cloud (an on-device draft appears first).
  - **App text first**: the app's own text is used when it exposes character positions, which is exact and works offline; other screens are recognized as usual.
  - **OCR boost** sends parts of the screen to cloud recognition separately, so small text that a full-screen scan misses is read: on demand (the part under the aim, when nothing was found there) or always.
- **Lookup**: how many characters from the aim point are considered (16 by default), how many entries a lookup shows (32; more gets slower), romaji typed or recognized as Latin text looked up as kana (taberu → たべる), and the kanji of the matched word shown as their own entries below the results.
- **Dictionaries**, **Anki and audio**, **Appearance**: see below.
- **Import from Yomitan**: a Yomitan settings export (Settings → Backup → Export Settings) brings over one profile's dictionary order and sort dictionary, Anki deck, note type, field templates and duplicate handling, audio sources, scan length, entries per lookup, text size and custom popup CSS. A summary lists what was applied and what was skipped. A dictionary collection export is imported here too.

When something breaks outside Screenlate (AnkiDroid removed or its permission revoked, the note type or deck deleted, fields renamed, the accessibility service turned off, dictionary files missing), the start screen says what and links to the fix. In the popup, ➕ turns grey; tapping it shows the reason and a button that opens the Anki settings.

## Entries

- Tap a link inside a definition to look it up; the back arrow returns.
- Tap a kanji in the headword to see its kanji dictionary entry (install KANJIDIC from the catalog first).
- 🔊 plays the pronunciation from the configured audio sources; hold it to choose among all recordings found.
- ➕ adds a note to Anki; hold it to attach a picture: a crop editor opens with the word's paragraph selected.

Text selected in any app can also be looked up through the "Look up in Screenlate" entry of the selection menu, and the Search screen accepts typed text.

Lookups also try a common respelling of the text: digits as kanji numerals (1人 → 一人), a character before 々 written twice, ッ as っ, and middle dots, commas, hyphens, periods and spaces removed. The text as it is on the screen is always looked up too.

## Appearance

- **Popup font.** By default the popup and the search page use the phone's own Japanese font, so kanji keep their Japanese forms (compare 直 or 骨) even where the phone's main font is Chinese. Free Japanese fonts can be downloaded from the list, or a font file (`.ttf`, `.otf`, `.ttc`) added; the text size applies to all of them.
- **Custom CSS.** Applied to the popup and the search page after the dictionaries' own styles, like Yomitan's custom popup CSS. Entries use Yomitan's class names, and badges follow Yomitan's tag color variables such as `--tag-frequency-background-color`. Warnings above the field point to lines the browser would skip and to fonts the phone does not have; such fonts fall back to the popup font. Names of common Windows and macOS Japanese fonts (Meiryo, Yu Gothic, Yu Mincho, MS Mincho, Hiragino) stand for the phone's Japanese sans-serif or serif font.
- Importing a Yomitan settings export can bring over the text size and the custom popup CSS.

## Dictionaries

- The Dictionaries screen lists installed dictionaries by type. Drag the handle to change the order: entries from dictionaries higher in the list come first. Among frequency dictionaries, pick the one used for sorting.
- Import Yomitan archives (`.zip`) from a file, download dictionaries from the catalog, or import a Yomitan "dictionary collection" export (`.json`) to bring over everything installed in Yomitan at once.
- "Check for updates" asks each dictionary's index whether a newer revision exists.
- A Yomitan collection export is listed first; check the dictionaries to import (those already installed start unchecked). Large exports are read in place, without a copy.
- Before a collection import writes anything, it checks the free space for the chosen dictionaries. The fastest import needs roughly as much free space as those dictionaries take in the export; with less, the import packs its temporary files more tightly and takes longer. If even that does not fit, the import stops with a message saying how much space is needed.
- Missing dictionary files are restored automatically for bundled dictionaries; catalog dictionaries can be downloaded again in one tap.

## Anki

1. Install AnkiDroid, open Anki and audio settings in Screenlate and allow access to AnkiDroid.
2. Choose a deck and a note type. Field templates are pre-filled from the field names; adjust them with the + button, which inserts markers such as `{expression}`, `{reading}`, `{furigana}`, `{glossary}`, `{sentence}`, `{cloze-body}`, `{audio}` and `{screenshot}`.
3. The first field is used for the duplicate check. Duplicates can be searched in the collection, the deck or the deck's root, and be prevented (➕ becomes 📖, which opens the existing note; hold it to add anyway), overwritten field by field, or added again.

### Audio sources

Sources are tried in the listed order, as in Yomitan: JapanesePod101, LanguagePod101, Jisho, Lingua Libre, Wiktionary, the phone's text-to-speech (played only, never put into notes), a custom URL template with `{term}` and `{reading}`, and a custom URL returning Yomitan's audio source list JSON. Each source has a Test button; the settings also show sources that failed recently.

## Troubleshooting

- **"This app does not allow screenshots."** The app protects its windows. Try the "App text first" source.
- **Only the on-device result appears.** Cloud recognition was unreachable or took longer than 15 seconds; the on-device result is used instead.
- **The bubble disappears after a while.** The system stopped the service; see the background settings above.
- **Kanji look Chinese.** The popup uses the phone's Japanese font; if the phone has none, Appearance offers to download Noto Sans JP.
- **Text-to-speech says nothing.** No Japanese voice is installed; install one in the system's text-to-speech settings.
