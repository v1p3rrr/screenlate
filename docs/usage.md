# Using Screenlate

## First launch

1. Open Screenlate and enable its accessibility service (the start screen links to the settings). The bundled dictionaries install in the background; the start screen shows when they are ready.
2. If the switch in the accessibility settings is greyed out after installing from an APK file: App info → ⋮ → Allow restricted settings.
3. On devices with aggressive battery management, allow Screenlate to run in the background, or the system may stop the service. Settings → Background work shows whether battery optimization is on for Screenlate, asks the system to turn it off, and opens the manufacturer's startup settings on phones that have them.

## The bubble

| Gesture | Result |
|---|---|
| Pull the bubble away from the screen edge | The screen is scanned; aim at a word to see its entry. |
| Move the floating bubble | The entry follows the aim point. The screen is not scanned again. |
| Tap the floating bubble | Scan again, e.g. after scrolling. Recognized lines flash briefly. |
| Hold the floating bubble | Copy the paragraph under the aim (a whole speech bubble or paragraph, without line breaks) or all recognized text. With the aim off text, the paragraph of the word shown in the popup is copied. |
| Double tap the floating bubble | Switch the aim point between "above the finger" and "bubble center". |
| Press ✕ | Close the entry; the bubble stays where it is and keeps the recognized text. |
| Drag the bubble to the left or right edge | Close the entry and dock the bubble: once the bubble's center is past the edge, or when the finger is lifted at the edge. |
| Drag the docked bubble along the edge | Move the dock. |

A word broken at the end of a line or column is looked up as a whole, reading on in the next line or column.

The entry opens above the word or below the bubble and never covers either. Beside vertical text and in landscape it opens to the side with more room, narrower and taller; beside a column in the middle of a narrow screen it can get narrower still. When neither side has room, it goes above or below.

The Quick Settings tile hides and shows the bubble. Turning the screen closes the entry and docks the bubble on the same side.

## Settings

The start screen keeps the search, the service status and warnings; everything else is under Settings:

- **Bubble**: aim point, dock side, bubble size, word highlight, vibration, apps where the bubble hides, whether the popup starts with the recognized text, and the text source:
  - **Screen recognition** (the default): a screenshot is recognized in the cloud; an on-device draft of the lines around the aim appears first. When cloud recognition fails (no network, no answer within 15 seconds, an error), the on-device result stays and ⚠ appears next to the text source; tap it for the reason. Cloud recognition uses mobile data, about 0.1–0.4 MB per scan.
  - **Read app text** (experimental): the app's own text is used where the app exposes character positions. It is exact, ready almost at once and works offline. The screen is still recognized alongside for everything else, such as text in images; whichever is ready first is shown. Some apps report wrong character positions, so the highlight can be off or a neighboring word found; turn it off then.
  - **App text only**: no screenshots and no recognition, only the text apps expose. Text in images, manga and games is not read, and apps that do not expose their text show a message instead. Meant for e-ink readers, old or weak devices where recognition fails or is slow, and slow or metered internet. The recognition settings below are off while it is on; a picture for an Anki note is still taken when asked for.
  - **OCR boost** sends parts of the screen to cloud recognition separately, so small text that a full-screen scan misses is read: on demand (the part under the aim, when nothing was found there) or always. It is not needed by default: turn it on only if small text is not recognized, since every extra part is another request and uses more mobile data.
  - **Screen recognition → Engines**: both (the default), cloud only or device only. With both, the device quickly reads the text around the aim, so words appear almost at once, and the cloud's more accurate result replaces them when it arrives; without network, or if the cloud fails, the device's result stays. Turning one off is a last resort for weak or busy devices: cloud only recognizes nothing without network and shows words only once the server answers; device only works offline but reads vertical text and manga worse, and OCR boost is off; the text source then says just "On-device".
  - **Screen recognition → Reduce load on the device** (off by default): the device reads the lines around the aim at once, but the whole screen only when cloud recognition has not answered within 3 seconds. Less load and battery use; words away from the aim show up later.
- **Lookup**: how many characters from the aim point are considered (16 by default), how many entries a lookup shows (32; more gets slower), romaji typed or recognized as Latin text looked up as kana (taberu → たべる), and the kanji of the matched word shown as their own entries below the results. Without romaji, Latin text is looked up when Japanese follows it (Tシャツ) or when the whole word is a dictionary entry (OL, DNA, CD-ROM), wherever the aim is inside it; single letters are skipped, and letter case counts.
- **Dictionaries**, **Anki and audio**, **Appearance**: see below.
- **Backup and restore**: see below.
- **Background work**: battery optimization for Screenlate and the manufacturer's startup settings, the two things that can stop the bubble in the background.
- **Import from Yomitan**: a Yomitan settings export (Settings → Backup → Export Settings) brings over one profile's dictionary order and sort dictionary, Anki deck, note type, field templates and duplicate handling, audio sources, scan length, entries per lookup, text size and custom popup CSS. A summary lists what was applied and what was skipped. A dictionary collection export is imported here too; single dictionaries (one `.zip` each) are added under Dictionaries, which the screen links to.

When something breaks outside Screenlate (AnkiDroid removed or its permission revoked, the note type or deck deleted, fields renamed, the accessibility service turned off, dictionary files missing, audio sources failing), the start screen says what and links to the fix; several missing dictionaries or failed audio sources share one card. In the popup, ➕ turns grey; tapping it shows the reason and a button that opens the Anki settings.

## Entries

- Tap a link inside a definition to look it up; the back arrow returns.
- Tap a kanji in the headword to see its kanji dictionary entry (install KANJIDIC from the catalog first).
- Tap an inflection step 🧩, a pitch accent or a tag with a description (dotted underline, or a label such as "noun" in dictionaries that describe their labels) to read the explanation at the bottom of the popup. Tag descriptions come from the dictionary's tag list and are kept for dictionaries imported from this version on; import an older dictionary again to get them.
- 🔊 plays the pronunciation from the configured audio sources; hold it to choose among all recordings found.
- ➕ adds a note to Anki; hold it to attach a picture: a crop editor opens with the word's paragraph and some space around it selected. "Whole screen" selects the whole screenshot and turns into "Frame", which brings the previous frame back.
- Hold text in an entry to select it, drag the handles to widen the selection, and tap Copy above it; furigana are left out. Back or a tap outside the popup ends the selection.

Text selected in any app can also be looked up through the "Look up in Screenlate" entry of the selection menu, and the Search screen accepts typed text.

Lookups also try a common respelling of the text: digits as kanji numerals (1人 → 一人), a character before 々 written twice, ッ as っ, and middle dots, commas, hyphens, periods and spaces removed. The text as it is on the screen is always looked up too.

## Appearance

- **Language** of the interface: the system's, or English, Russian, Spanish, French, German, Italian, Portuguese (Brazil), Polish, Turkish, Vietnamese, Japanese, Korean, Chinese (Simplified or Traditional). It can also be set in the system settings: Apps → Screenlate → Language. Translations were made with AI and have not been reviewed by native speakers; a note under the picker says so.
- **Popup font.** By default the popup and the search page use the phone's own Japanese font, so kanji keep their Japanese forms (compare 直 or 骨) even where the phone's main font is Chinese. Free Japanese fonts can be downloaded from the list, or a font file (`.ttf`, `.otf`, `.ttc`) added. Each added file is a font of its own, named by its family and style ("Noto Serif JP Bold"); a file with the same name replaces the earlier one. Web fonts (`.woff`, `.woff2`) cannot be added: convert them to TTF or OTF first. A downloaded or added font is used only for Japanese text by default, so Latin and Cyrillic letters keep the phone's font; turn off "Only for Japanese text" to use it for everything.
- **Text size, weight and letter thickness** apply to every popup font. The weight (normal, medium, semi-bold, bold) uses the font's own weights; a font with a single weight goes from normal straight to bold. Letter thickness draws a thin outline around each letter, which works with any font and adds to the weight. Both are off by default and follow the "Only for Japanese text" switch: with it on, or with the phone's font, only Japanese characters get heavier; with it off, all text does. The e-ink mode's "Make larger" changes only sizes.
- **Custom CSS.** Applied to the popup and the search page after the dictionaries' own styles, like Yomitan's custom popup CSS. Entries use Yomitan's class names, and badges follow Yomitan's tag color variables such as `--tag-frequency-background-color`. Warnings above the field point to lines the browser would skip and to fonts the phone does not have; such fonts fall back to the popup font. Fonts the CSS defines itself with `@font-face` are left as they are. Names of common Windows and macOS Japanese fonts (Meiryo, Yu Gothic, Yu Mincho, MS Mincho, Hiragino) stand for the phone's Japanese sans-serif or serif font.
- **E-ink mode** (off by default): black and white, no animations, frames instead of translucent highlights, and a black bubble and aim; it overrides the theme. Turning it on offers a larger bubble and larger popup text; turning it off, with the switch or by restoring a backup, brings back the previous sizes, except those changed by hand in the meantime (sizes the backup holds win). On devices that look like e-ink readers the start screen offers the mode once, with a link to "App text only".
- Importing a Yomitan settings export can bring over the text size and the custom popup CSS.

## Dictionaries

- The Dictionaries screen lists installed dictionaries by type. Drag the handle to change the order: entries from dictionaries higher in the list come first. Among frequency dictionaries, pick the one used for sorting.
- Import Yomitan archives (`.zip`) from a file, download dictionaries from the catalog, or import a Yomitan "dictionary collection" export (`.json`) to bring over everything installed in Yomitan at once.
- "Check for updates" asks each dictionary's index whether a newer revision exists.
- Each dictionary shows its languages (words → explanations). They come from the dictionary's own description, else from the catalog, else from its content: the script of the headwords and definitions, with Latin text identified by the phone's on-device language detection. Dictionaries installed before this was added get theirs once, from sample lookups. Frequency and pitch dictionaries show only the language of their words. Tap a dictionary and "Change" to set them by hand; a dictionary is used only for lookups in its word language.
- Imports and downloads show their progress in a notification. On Android 13 and later the first import or download asks for the notification permission; while it is missing, the Dictionaries screen offers it again, and once Android stops asking, its button opens the app's notification settings.
- A Yomitan collection export is listed first; check the dictionaries to import (those already installed start unchecked). Large exports are read in place, without a copy.
- Before a collection import writes anything, it checks the free space for the chosen dictionaries. The fastest import needs roughly as much free space as those dictionaries take in the export; with less, the import packs its temporary files more tightly and takes longer. If even that does not fit, the import stops with a message saying how much space is needed. With at least four times the file size free, the check is skipped.
- A dictionary's details link to its website and to where it can be downloaded, when the dictionary or the catalog names them.
- Missing dictionary files are restored automatically for bundled dictionaries; catalog dictionaries can be downloaded again in one tap.

## Backup and restore

- **Create a backup** writes one `.zip` file wherever the file picker allows: the phone, a memory card or cloud storage. It holds all settings (bubble, lookup, Anki, audio, appearance with custom CSS, hidden apps), the order, switches and languages of the dictionaries, and the added fonts. With "Include dictionaries" the dictionaries themselves go in too, ready to use without downloading or importing; the file then gets much larger.
- **Restore** opens a backup and lists its parts. Each checked part replaces what the app has; unchecked parts stay as they are. Dictionaries from the backup are added, or replace the same dictionary; installed dictionaries are never removed. The dictionary order applies to the dictionaries that are installed, so restore the dictionaries themselves in the same run or install them first. A summary lists what was restored.
- A backup is for Screenlate only; Yomitan cannot read it.

## Anki

1. Install AnkiDroid, open Anki and audio settings in Screenlate and allow access to AnkiDroid.
2. Choose a deck and a note type. Field templates are pre-filled from the field names; adjust them with the + button, which inserts markers such as `{expression}`, `{reading}`, `{furigana}`, `{glossary}`, `{sentence}`, `{cloze-body}`, `{audio}` and `{screenshot}`.
3. The first field is used for the duplicate check. Duplicates can be searched in the collection, the deck or the deck's root, and be prevented (➕ becomes 📖, which opens the existing note; hold it to add anyway), overwritten field by field, or added again.

### Audio sources

Sources are tried in the listed order, as in Yomitan: JapanesePod101, LanguagePod101, Jisho, Lingua Libre, Wiktionary, the phone's text-to-speech (played only, never put into notes), a custom URL template with `{term}` and `{reading}`, and a custom URL returning Yomitan's audio source list JSON. Each source has a Test button; failures say what went wrong (for example "The server refused the connection or is not running (ConnectException)"), and sources that failed recently are listed together in one card on the start screen.

A custom URL may point to a server on the home network over plain `http://`, as in Yomitan on a computer. When such a server's list names its files by a local address (`0.0.0.0`, `localhost`, `127.0.0.1`), the files are fetched from the list's server instead. On Android 17 and newer, a source on the home network needs the nearby devices (local network) permission: the app asks for it when such a source is added, imported from Yomitan or restored from a backup, and a failed test offers it again.

## Updates and About

- Release builds look for a new version on GitHub at most once a day when the app is opened, and announce each new version once on the start screen with its changes. "Skip this version" hides it until the next release; About → Updates can still install it and has a "Check for updates" button. The announcements can be turned off there.
- "Update" downloads the APK for the device and installs it. Android asks once to allow installs from Screenlate, and asks to confirm the first update; later updates install without a prompt where Android allows it (Android 12 and newer).
- About also lists the open-source libraries, the third-party notices and the installed dictionaries with their authors and licenses, "Share logs" sends the app's log (with the app version and device model, but no looked-up text) through the share sheet, and "Save to Downloads" writes the same file to `Download/Screenlate/`, where any file manager can open it. "Report a problem" opens a short issue form on GitHub.

## Troubleshooting

- **"This app does not allow screenshots."** The app protects its windows. Try "Read app text".
- **Only the on-device result appears.** Cloud recognition was unreachable or took longer than 15 seconds; the on-device result is used instead.
- **The bubble disappears after a while.** The system stopped the service; see Settings → Background work.
- **The device gets warm or slow while scanning.** Most of a scan's work is the on-device recognition of the whole screen. Turn on "Reduce load on the device" in the bubble settings; as a last resort, choose one engine.
- **The highlight is off or the wrong word is found with "Read app text".** The app reports wrong character positions; turn "Read app text" off.
- **"This app does not expose its text here."** "App text only" is on, and the app under the aim shows its text as an image or does not expose it. Turn "App text only" off to recognize the screen.
- **Kanji look Chinese.** The popup uses the phone's Japanese font; if the phone has none, Appearance offers to download Noto Sans JP.
- **Text-to-speech says nothing.** No Japanese voice is installed; install one in the system's text-to-speech settings.
