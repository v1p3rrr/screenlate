# Feedback after v0.1.4

Owner requests after the v0.1.4 release (2026-09-29).

## Owner requests

1. Engines ⓘ: explain how "Both" works, next to the existing "Cloud only" and "Device only" paragraphs.
2. E-ink mode enlarges the bubble; turning the mode off does not bring the previous size back. It must come back, unless the size was changed by hand in the settings after the mode was turned on.
3. E-ink mode description (English, and other languages if needed): say it is for e-book readers and similar screens, since not everyone knows the term "e-ink". In the description, not the title. (No brand names in the UI.)
4. Question: on a long press, is the nearest paragraph copied even when the aim is not on it but further away?
5. "Some phones also stop background apps through the maker's own settings": the owner reads "maker" as something else; use a clearer word.
6. "Translations other than English and Russian were made with AI…": do not mention English and Russian there.
7. Popup font: a chosen font also changes Latin and Cyrillic text; it should apply only to the target language (or a toggle: target language only / everything).
8. Yomitan import: say that separate dictionaries (one zip each) are imported elsewhere, ideally with a button that goes there.
9. The phone's Japanese font is very thin in the popup: a way to make the default font heavier (asked during the second review).
10. Configurable copying of a definition: a setting that turns it on, and a choice of behavior: copy everything the dictionary shows, or only the definitions without the markup around them. At least for Kolobok, JMdict, Ditendex, and the other dictionaries the app supports (asked during the final review).
11. The bubble is gone while the accessibility service is still switched on (Android stopped the app): a button on the home screen that brings the bubble back when the accessibility permission is there (2026-09-30).
12. A loading indicator while the list of apps under "Hide the bubble in apps" loads (2026-10-01).

## Decisions

| Topic | Decision |
|---|---|
| E-ink sizes | Turning e-ink mode off restores both the bubble size and the popup text size that were in place before "Make larger", each only if it was not changed by hand since; the dialog says so (owner) |
| Popup font scope | A switch under the font choice, "Only for Japanese text" (the target language), on by default; off applies the font to all text as before (owner) |
| Copy paragraph | The paragraph under the aim; with the aim off text, the paragraph of the word shown in the popup; otherwise only "Copy all recognized text" (owner) |
| Release | Commit and push to main; release later on the owner's command (owner) |
| Review fixes (appearance and fonts) | All findings fixed except the Japanese punctuation range, which stays as is (owner) |
| AI note in docs | README and `docs/usage.md` say the same as the app, without naming languages (owner) |
| E-ink off through a backup | Restores the enlarged sizes like the switch does; sizes the backup restores win (owner) |
| App start until the theme is read | The system splash screen with the icon stays until the theme and e-ink setting are read, instead of an empty window (owner) |
| AI translation note | "Translations were made with AI and have not been reviewed by native speakers…", no languages named (owner) |
| Own font files | Each added file is its own font in the list, named by its family and style ("Noto Serif JP Bold"; "Regular" left out); only a file with the same name replaces an earlier one (owner) |
| Catalog downloads | Stay as they are: all files of a font (owner) |
| WOFF and WOFF2 | No longer accepted when adding a font file; the error says to convert it to TTF or OTF. Fonts added before keep working (owner) |
| Popup text weight | Two sliders under "Text size": weight (normal, medium, semi-bold, bold) and letter thickness (outline, 0–5); both default to off (owner) |
| Weight scope | Follows "Only for Japanese text": on, only the language's script gets heavier; off, all text; with the phone's font always only the script (owner) |
| E-ink "Make larger" | Changes only the sizes, not the weight (owner) |
| Definition copying | Off by default. When on, a small copy button next to each dictionary's name in an entry copies that dictionary's definition, without the headword; in the popup and in the app's search (owner) |
| Copy modes | "Everything" (default): all the dictionary shows, as HTML with a plain-text alternative, so apps without formatting get plain text, not tags. "Only meanings": the numbered meanings as plain text, without tags, examples, notes, references and form tables (owner) |
| Dictionaries without recognizable meanings | "Only meanings" copies their whole text as plain text; the option's hint says it is not guaranteed for every dictionary. Recognize meanings as generally as possible, checked on Jitendex, JMdict, Kolobok and the owner's test dictionaries (owner) |
| Popup settings | A new settings screen "Popup": font, text size and weight, custom CSS (moved from Appearance) and definition copying. Appearance keeps theme, language and e-ink; Lookup stays as is (owner) |
| Crop editor when the scan closes | Rotation, hiding the bubble or docking closes an open crop editor as if cancelled; the note is not added (owner) |
| Popup in display screenshots | Stays as is: on Android 13 and older, and in the API 34+ fallback to a display screenshot, the popup is not hidden before the capture (owner) |
| Scan screenshot lifetime | Use-counted, no timeout: the scan and each note being added hold it, the last one frees it (owner: users must "know" its lifetime) |
| Overlay performance (review) | ML Kit's line building, screenshot copies and crops off the main thread, the cloud response read on IO; dictionary styles, tag descriptions and frequency modes cached until the dictionaries change (owner) |
| Adding a note during a scan | ➕ cancels the scan's cloud request; the note takes the sentence and picture from the view the button was pressed in, without waiting for recognition to finish (owner) |
| Bringing the bubble back | A button in the home screen's accessibility card. With the service running it shows or hides the bubble (the tile's setting); with the service stopped by Android it opens Screenlate's own page in the accessibility settings, with a hint to turn the switch off and on, because an app cannot write `ENABLED_ACCESSIBILITY_SERVICES` (owner) |
| Accessibility settings button | Every button that opens the accessibility settings goes to Screenlate's own page (`ACTION_ACCESSIBILITY_DETAILS_SETTINGS`), falling back to the list of services (owner) |
| Showing and hiding the bubble in the app | The home button works both ways, and the bubble settings get a "Show the bubble" switch, so the Quick Settings tile is no longer the only way (owner) |
| Keeping the service alive | A switch on Background work, off by default: a foreground service with a permanent quiet notification, which makes the phone's cleaners stop the app less often (owner) |
| Keep-alive with notifications off | The keep-alive card says that notifications are off and the notification is not shown, with a button that asks for the permission, or opens the app's notification settings once Android no longer asks (as on the Dictionaries screen); the notification is posted again once allowed (owner) |
| Forcing a rebind | Not done: toggling our own service component to make the system rebind is left alone, since on some firmwares it drops the service from the enabled list instead (owner) |
| Notes when the scan closes | A note already being prepared or saved is finished; docking, rotation or hiding the bubble does not cancel it (an open crop editor still closes as cancelled) (owner) |
| Review findings that are plain bugs | Fixed without asking the owner (owner) |

## Code review: appearance and fonts

Owner's command (2026-09-29): review one functional module in full with its integrations (`notes/module-map.md`, module 10), and check that the reworded strings are fixed in every language. Findings, most severe first:

1. Custom CSS: `font-family` inside the user's own `@font-face` gets the page font list appended, so the browser drops the rule; the name is also reported as a missing font.
2. Custom CSS edits typed less than 0.4 s before leaving the screen are lost; after rotation the field falls back to the older saved text.
3. E-ink mode turned off by a backup restore keeps the enlarged sizes and a stale enlargement record that can reset hand-set sizes later.
4. The popup text size is not clamped when read; an out-of-range restored value makes "Make larger" shrink the text.
5. README and `docs/usage.md` still name English and Russian in the AI translation note.
6. The English e-ink dialog reads as "56 dp larger" and promises 56 dp when the bubble is already larger.
7. A stray ASCII space after 。 in the ja, zh-Hans and zh-Hant e-ink hint (and after ？ in the ja dialog).
8. Japanese punctuation (… ‥ ― ※) is outside the Japanese `unicode-range`.
9. Re-adding an own font with the same family keeps the file name, so the popup keeps the old font.
10. Font import buffers up to 64 MB in memory.
11. File names in a restored `fonts.json` are not checked (`../`).
12. `fonts.json` is deleted before the new one is renamed onto it.
13. The first frame uses the colour theme before the e-ink setting loads.
14. `popup.js` hard-codes `lang="ja"` on headwords.
15. No tests for `@font-face` in custom CSS, `PopupFonts` file handling, or the e-ink restore flow.

## Code review: appearance and fonts, second run

Owner's command (2026-09-29): review the same module and its integrations again, fix everything. Findings:

1. Font import errors (unreadable file, full disk, failed rename) crash the app; the replaced font's files are deleted before the new file is in place.
2. With "Only for Japanese text" off, the chosen font is named by its family; another installed font of the same family takes its place in the popup.
3. An added Bold file replaces the Regular of the same family, and every own file is declared as weight 400.
4. WOFF and WOFF2 files cannot be previewed on Android and take their name from the file name.
5. The text size slider truncates its value.
6. The restore summary counts font files, not fonts.
7. A failed catalog download leaves partial and earlier files behind.
8. Turning e-ink on and tapping "Make larger" quickly can lose the enlargement record.
9. Weights and family names from a restored font list go into the page CSS unchecked.
10. A font deleted while the page loads it throws in the request handler.
11. Custom CSS line numbers cost quadratic time on every keystroke.
12. The KDoc and `@Suppress` of the backup preference restore sit on the wrong function.
13. No tests for font import and its failures.

## Code review: overlay runtime

Owner's command (2026-09-29): the next module by importance and risk, with its integrations, then a check of the fixes. Module 9 (`notes/module-map.md`). Findings, most severe first:

1. The crop editor's "Whole screen" returned the scan screenshot itself (Android's `createBitmap` for the full rectangle of an immutable bitmap), and the note recycled it: the next picture was lost, and an OCR boost band on it crashed the service.
2. Rotating, hiding the bubble or docking while the crop editor was open recycled the screenshot it drew (crash on redraw or on Add).
3. A note's sentence and crop frame took the current layout with the position of the word shown from an earlier one (wrong sentence, or an error after the cloud result replaced the draft).
4. A cancelled duplicate check still wrote its marks onto the next word's entries.
5. The cloud response body was read on the main thread.
6. Hiding the bubble (hidden app, tile) left the highlights, note state and clip choices of the old scan.
7. The popup is not hidden for display screenshots (Android 13 and older, API 34+ fallback). Left as is (owner).
8. A capture or recognition error left the popup's spinner running.
9. A cancelled scan's window capture was logged as a failure and fell back to a display capture.
10. A cancelled touch on the bubble still opened the copy menu later.
11. ML Kit's line building, the screenshot copy and band crops ran on the main thread.
12. Dictionary styles, tag descriptions and frequency modes were re-read and re-encoded for every scan and shown word.
13. The media failure log named the media path, which can carry the looked-up word.
14. The shared screenshot had no owner: findings 1 and 2 came from recycling without knowing its users.
15. The overlay fixes `Language.JAPANESE` (known; waits for the multi-language phase).

## Code review: overlay runtime, second run

Owner's command (2026-09-29): review the fixes of the first run and the module with its integrations again. Findings:

1. A note waiting for the scan or the lookup went on after docking with the scan's context cleared; in app-text-only mode its late capture was kept as the screenshot of a closed scan and the crop editor opened over the docked bubble.
2. OCR boost stayed active in app-text-only mode: after a note captured a screenshot, bands went to the cloud engine.
3. A note's result ("added", 📖) was written by index into the popup's current view, which could show another word by then.
4. ✕ cleared the scan's note memory (added notes, chosen clips, auto-play), although the scan goes on.
5. The note's sentence came from the view after the cloud result's re-lookup, which could be another word than the note's.
6. OCR boost bands were still copied on the main thread.
7. A note kept the screenshot until the note was saved, although the editor's crop is a separate image.
8. No tests for the crop editor's sessions.
9. A redundant spinner reset and a band guard duplicating the timer's cancel.

## Changelog

- 2026-09-29: plan created from the owner's feedback.
- 2026-09-29: owner answers — e-ink restores both sizes, popup font switch (target language by default), AI note without naming languages. Copy paragraph: the owner saw English text with no popup, moved the aim away, held the bubble, and "Copy this paragraph" copied the text nearest to where the aim had passed. Cause: the last hit is kept after the aim leaves text, even when the lookup found nothing and no popup is shown.
- 2026-09-29: owner answers — copy the paragraph under the aim or of the word in the popup; changes go to main without a release for now.
- 2026-09-29: all eight requests done and checked on the emulator (e-ink sizes restored, and a hand-changed text size kept; font switch with a downloaded serif font; engines ⓘ; Yomitan import hint and button; copy menu with English text and no popup, and with a Japanese word in the popup). The e-ink dialog in 12 locales said "to 56 dp" in a way that read as a fixed text size; reworded with the restore note.
- 2026-09-29: full review of the appearance and fonts module (owner's command); owner answers: fix all, leave the punctuation range, docs like the app, backup restore turns e-ink off with the same size restore.
- 2026-09-29: review findings fixed (all but 8) with tests, and checked on the emulator. Found while checking: the add-font button listed only `.ttf, .otf, .ttc` although `.woff` and `.woff2` are imported; the label now names them in all locales. Open question: the empty window at a cold start until the theme is read (item 13).
- 2026-09-29: owner answer — keep the splash screen with the icon until the theme is read. Done for the main screen and "Look up in Screenlate" (the launcher icon's layers on its light circle), checked on the emulator.
- 2026-09-29: second review of appearance and fonts (owner's command). Owner answers: own files are separate fonts named by family and style; catalog downloads stay; drop WOFF/WOFF2 import; new request — a way to make the popup text heavier, "the phone's font is very thin": weight and letter thickness sliders under "Text size", following the "Only for Japanese text" switch, default off, not part of the e-ink dialog.
- 2026-09-29: all 13 findings of the second review fixed with tests (unit, page, and instrumented `PopupFontsTest`, `LookupPageTest`); heavier text done. Checked on the emulator: the weight and thickness sliders, their ⓘ, the settings preview (Noto Serif JP bold with outline), the search page in both scopes (only Japanese runs heavier; all text heavier with the switch off), and the web-font error for a `.woff` file. Found while checking: with the phone's font the disabled "Only for Japanese text" switch showed the stored value (off) although the phone's font is always limited to the script; it now shows on. The emulator's system CJK font is variable with a 400–900 weight axis.
- 2026-09-29: final check of the second review's changes (owner's command): the phone's font weights now come from the file the page's `local()` names resolve to (Hentaigana is also listed for `ja`), a variable font's name leaves out its default weight, and heavier-text marks follow custom CSS changes. New request 10: configurable definition copying; questions to the owner before the plan.
- 2026-09-29: owner answers on request 10 — a copy button per dictionary, off by default; "Everything" (default, HTML with plain text for other apps) or "Only meanings"; no headword; whole text where meanings cannot be found, with a hint; in the popup and the app's search; a new "Popup" settings screen gathers the popup's settings.
- 2026-09-29: request 10 done: per-dictionary copy button (`definition.js`, `ClipData.newHtmlText` for "Everything"), new "Popup" settings screen with font, text and CSS cards and the copy card; Yomitan import summaries now point to it. Checked on the emulator in the app's search: "Everything" and "Only meanings" for 食べる (JMdict). Owner question on wide app-text and ML Kit highlight boxes answered (the boxes come from the app or ML Kit; trimming to the glyphs would need pixel checks on the screenshot); the owner decided not to change them.
- 2026-09-29: review of module 9, overlay runtime (owner's command), 15 findings. Owner answers: the crop editor closes as cancelled when the scan closes; the popup in display screenshots stays as is; the screenshot's users must "know" its lifetime, so it is use-counted rather than freed on a timeout; all three performance fixes.
- 2026-09-29: findings 1–6 and 8–14 fixed (`SharedScreenshot`, `CropEditor.cancel`, `closeScan`, `PerGeneration` caches by `DictionaryRepository.generation`), 7 and 15 left. The fixes were reviewed again with their callers (search screen notes, service stop, the crop editor's window removal, OCR cancellation); nothing new found. Tests: `SharedScreenshotTest`, `PerGenerationTest`, instrumented `CropViewTest` and a `DictionaryRepositoryTest` case. The overlay was not run on the emulator (its accessibility service is off).
- 2026-09-29: second review of the overlay runtime (owner's command), 9 findings. Owner answers: ➕ cancels the scan's cloud request and the note uses the view as shown; a note in progress is not cancelled by docking; plain bugs are fixed without questions.
- 2026-09-29: all 9 findings of the second overlay review fixed: `NoteSource` fixes the note's view when ➕ is pressed and withdraws the scan's cloud request (`stopCloud`; OCR boost stops for that scan too); a picture only while the note's scan is open; results placed by term; ✕ keeps the note memory; no OCR boost in app-text-only mode; band crops off the main thread; the screenshot released after the editor. Tests: `CompositeOcrTest` (withdrawn requests), `NoteEntryTest`, instrumented `CropEditorTest`. The overlay was not run on the emulator (accessibility service off).
- 2026-09-29: the overlay fixes checked on the emulator with the accessibility service on (owner allowed it): a note from the popup, 📖 kept after ✕ and re-aim, the crop editor on long press and its closing on rotation, ➕ while the cloud request waits (slow network: "Lens request withdrawn", the on-device result becomes final). Two bugs found there and fixed: ➕ after the final result still stopped OCR boost for the scan (`stopCloud` now does nothing once the result is final), and a scan whose cloud request a note withdrew was labeled "On-device (Lens unavailable)" with a warning; it now reads "On-device".
- 2026-09-30: review of the fixes above (owner's command), 7 findings, all fixed: the final update carries `cloudWithdrawn`, so only a confirmed withdrawal turns OCR boost off and labels the chip "On-device", while offline and Lens failures keep their labels; a withdrawal whose ML Kit run failed ends without a final instead of an OCR error; a note that finishes after its scan closed stays out of the next scan (`ScanNotes`); dead band cancellation removed from `stopCloud`; `closeScan` KDoc; chip and OCR boost rules moved to `OcrStatus.kt` with tests. Checked on the emulator: withdrawn on a slow network reads "On-device" without a warning, offline reads "On-device (offline)".
- 2026-09-30: request 11 (the bubble gone while the service is on). Owner answers: the button lives in the accessibility card and works both ways; every accessibility button opens Screenlate's own page there; a "Show the bubble" switch in the bubble settings; an opt-in keep-alive foreground service on Background work; the component-toggle trick is not used. The security-fix PR was merged first, and this work goes to main.
- 2026-10-01: request 11 done and checked on the emulator. Found while checking: stock Android guards Screenlate's own accessibility page with `OPEN_ACCESSIBILITY_DETAILS_SETTINGS` (signature|installer), so every accessibility button on home crashed the app with a `SecurityException`; a refused page now falls back to the list of services like a missing one, with the settings app's highlight extras on Screenlate's row (highlighted on the emulator). The decision row stands: its own page where the phone allows it, the list otherwise, which on stock Android means always. Also fixed: the keep-alive switch is written after the notification permission is answered (a notification posted before the grant stayed hidden until the service restarted); a sticky restart of the keep-alive service stops itself when the accessibility switch is off; the start screen follows the service's running state live.
- 2026-10-01: owner answer — with notifications off, the keep-alive card shows a line and an "Allow notifications" button (the permission is asked only once for the whole app, so a decline at an earlier import left the notification hidden with nothing said). Done with the Dictionaries card's logic moved into `rememberNotificationsPermission`; `BubbleKeepAliveService.repost` posts the notification again after the grant. Checked on the emulator for both the system dialog and the app's notification settings.
- 2026-10-01: request 12 done: a spinner below the app filter until the launcher apps and their icons are read. Checked on the emulator with a temporary 4 s delay.
