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
| AI translation note | "Translations were made with AI and have not been reviewed by native speakers…", no languages named (owner) |

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

## Changelog

- 2026-09-29: plan created from the owner's feedback.
- 2026-09-29: owner answers — e-ink restores both sizes, popup font switch (target language by default), AI note without naming languages. Copy paragraph: the owner saw English text with no popup, moved the aim away, held the bubble, and "Copy this paragraph" copied the text nearest to where the aim had passed. Cause: the last hit is kept after the aim leaves text, even when the lookup found nothing and no popup is shown.
- 2026-09-29: owner answers — copy the paragraph under the aim or of the word in the popup; changes go to main without a release for now.
- 2026-09-29: all eight requests done and checked on the emulator (e-ink sizes restored, and a hand-changed text size kept; font switch with a downloaded serif font; engines ⓘ; Yomitan import hint and button; copy menu with English text and no popup, and with a Japanese word in the popup). The e-ink dialog in 12 locales said "to 56 dp" in a way that read as a fixed text size; reworded with the restore note.
- 2026-09-29: full review of the appearance and fonts module (owner's command); owner answers: fix all, leave the punctuation range, docs like the app, backup restore turns e-ink off with the same size restore.
- 2026-09-29: review findings fixed (all but 8) with tests, and checked on the emulator. Found while checking: the add-font button listed only `.ttf, .otf, .ttc` although `.woff` and `.woff2` are imported; the label now names them in all locales. Open question: the empty window at a cold start until the theme is read (item 13).
