# Module review questions (2026-09-30)

Choices found during the module reviews (`2026-09-30-module-reviews.md`) that need the owner. Each entry: module and
run, the feature, how it works now, the options (recommended first) and what each changes. The code stays as is until
answered; answers go to the plan's changelog.

## Open

Q1-Q11 were answered on 2026-09-30 (the full texts are in git history). New from the review of v0.1.4..HEAD:

- **Q12. OCR source chip for a word kept from the draft** (overlay, `OverlayController.engineLabel`/`aimedEngine`,
  `OcrStatus.noteWaitsForText`). After Q11 the popup keeps a word ML Kit saw when the cloud result has no word under
  the aim, and the note takes that word and the draft's sentence. The chip and the ➕ wait are computed from the engine
  of the *current* layout under the aim, so such a word shows the cloud chip. Options: (a, recommended) remember the
  engine with the shown lookup (`shownLookup`) and use it for the chip and ➕; (b) keep as is.
- **Q13. Failed final ML Kit pass after band drafts** (overlay, `showScanError`/`showMessage`). In screen mode, band
  drafts may already show a word; if the whole-image ML Kit call then fails, the error message replaces that word, and a
  lookup still in flight (`lookupJob`, not cancelled by `showMessage`) may later draw over the error again. Options:
  (a, recommended) keep a shown word and only stop the spinner, show the error only when nothing is shown, and cancel
  `lookupJob` in `showMessage`; (b) always show the error and cancel the pending lookup.

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
- Q11 (2026-09-30): the note always holds the word the popup shows. When the final text has no word where the draft
  had one, the word kept from the draft and its sentence from the draft go into the note (the current behavior).
