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
| AI translation note | "Translations were made with AI and have not been reviewed by native speakers…", no languages named (owner) |

## Changelog

- 2026-09-29: plan created from the owner's feedback.
- 2026-09-29: owner answers — e-ink restores both sizes, popup font switch (target language by default), AI note without naming languages. Copy paragraph: the owner saw English text with no popup, moved the aim away, held the bubble, and "Copy this paragraph" copied the text nearest to where the aim had passed. Cause: the last hit is kept after the aim leaves text, even when the lookup found nothing and no popup is shown.
- 2026-09-29: owner answers — copy the paragraph under the aim or of the word in the popup; changes go to main without a release for now.
- 2026-09-29: all eight requests done and checked on the emulator (e-ink sizes restored, and a hand-changed text size kept; font switch with a downloaded serif font; engines ⓘ; Yomitan import hint and button; copy menu with English text and no popup, and with a Japanese word in the popup). The e-ink dialog in 12 locales said "to 56 dp" in a way that read as a fixed text size; reworded with the restore note.
