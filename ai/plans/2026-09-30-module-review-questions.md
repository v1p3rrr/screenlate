# Module review questions (2026-09-30)

Choices found during the module reviews (`2026-09-30-module-reviews.md`) that need the owner. Each entry: module and
run, the feature, how it works now, the options (recommended first) and what each changes. The code stays as is until
answered; answers go to the plan's changelog.

## Open

- Q11 (overlay, code review 2026-09-30): the word kept from the draft after the final text. How it works now: when
  the final OCR result has no word under the aim, the popup keeps the word found in the draft, drops the spinner and
  makes ➕ active (`OverlayController.onPage`). A note then takes its sentence from the draft's text, which Q1 meant to
  avoid; the final text may have corrected it. Options:
  1. (recommended) Keep the word, but take the note's sentence from the final text at the word's place on screen;
     when the final text has nothing there, the note has no sentence.
  2. Keep ➕ grey for a word from the draft once the final text is in; the user moves the aim to get a word from the
     final text.
  3. Keep as is: the draft sentence goes into the note.

Q1-Q10 were answered on 2026-09-30 (the full texts are in git history) and implemented the same day.

## Answered

- Q1 (2026-09-30): ➕ stays grey while the scan waits for its final result (spinner), so no note comes from a draft or
  from an old popup during a new scan. Follow-ups: a new scan closes the old popup at once; a word read from the app's
  own text keeps ➕ active at once; a tap on the grey ➕ shows a short hint ("waiting for the final text", 14 locales).
- Q2 (2026-09-30): do not cancel the cloud request on ➕ (with Q1, ➕ cannot be pressed before the final result anyway).
- Q3 (2026-09-30): remember deletions; a bundled dictionary the user deleted is never imported again, a kept one is
  still updated when the shipped file changes.
- Q4 (2026-09-30): show the name and the reason on a failed import card.
- Q5 (2026-09-30): two texts (import running: the current one; otherwise "no dictionary with definitions is on",
  with a way to the Dictionaries screen from search). Also: the last enabled dictionary with definitions of a language
  can be neither switched off nor deleted; a toast says why (frequency, pitch and kanji dictionaries stay free).
  May be revisited when other languages come.
- Q6 (2026-09-30): the owner wants the limit to keep rendering fast, not processing: ask the engine for all candidates
  when several term dictionaries are on and cut after our sort; measure a one-kana lookup first, fall back to a margin
  if it is slow.
- Q7 (2026-09-30): a CSP that blocks remote images, media, scripts and fetches but allows remote stylesheets and fonts;
  plus a warning, as Yomitan does, when CSS reaches the network (url(http...), @import http...): under the custom CSS
  field and as a ⚠ on the dictionary card (its styles.css checked at import). The CSS is not rewritten.
- Q8 (2026-09-30): show the Honor/MagicOS path only on phones of that maker; others see the generic text.
- Q9 (2026-09-30): keep as is.
- Q10 (2026-09-30): focus the search field (keyboard) only when the screen opens empty.
