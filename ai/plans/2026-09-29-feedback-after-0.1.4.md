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
