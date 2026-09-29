# Module review questions (2026-09-30)

Choices found during the module reviews (`2026-09-30-module-reviews.md`) that need the owner. Each entry: module and
run, the feature, how it works now, the options (recommended first) and what each changes. The code stays as is until
answered; answers go to the plan's changelog.

## Open

### Q1. ➕ adds the word of the popup still on the screen, which may not be the word the next result shows (owner asked to discuss, 2026-09-30)

- **Feature:** adding a note from the overlay popup (module 7 Anki export, module 9 overlay runtime).
- **How it works now:** since 2026-09-29 (second overlay review, owner's decision) ➕ takes the word, sentence and crop
  frame from the view shown at the moment it is pressed (`NoteSource` in `OverlayController.noteSource()`); it no
  longer waits for the final OCR result and the lookup made on it.
- **What was seen on the emulator (2026-09-30):**
  - A scan shows an on-device draft first (ML Kit reads a band around the aim, 5–9 s on the emulator), then the final
    result (cloud recognition, or ML Kit's whole-screen read when the cloud is unavailable or withdrawn). The draft and
    the final text can be split into words differently. In "可愛いと思うんですわ" the draft popup showed 「と」, the final one
    「いと」. ➕ pressed on the draft added 「と」; after the final result the popup showed 「いと」 with ➕ again, because
    its term differs from the added one. The note is correct for what was on the screen, but the user sees ➕ again and
    may think the note was lost, or add a second one.
  - A tap on the bubble starts a new scan, but the previous scan's popup stays on the screen until the new draft
    arrives (about 5–12 s on the emulator). ➕ pressed on that old popup adds the old word, which is fine, but it also
    withdraws the **new** scan's cloud request (the stop belongs to the current scan), and a screenshot for the note
    would come from the new scan.
- **Options** (to discuss; no recommendation yet):
  1. Keep as is: the note is always the word seen when ➕ was pressed.
  2. When a new scan starts, close the old popup or grey out its ➕/📖, so a note can only be made from the current
     scan's text; the draft keeps working as now.
  3. Keep adding from the draft, but after a later result mark the words that overlap the added one (same place on the
     screen) as added too, so ➕ does not reappear for 「いと」 after 「と」.
  4. Go back to waiting: ➕ on a draft waits for the final result and adds the word shown then (the behavior before
     2026-09-29; slow, up to the 15 s cloud timeout).

### Q2. After ➕ the scan never shows the cloud result (owner asked to discuss, 2026-09-30)

- **Feature:** ➕ during a pending cloud request (module 3 OCR, module 9 overlay runtime).
- **How it works now:** owner's decision of 2026-09-29 ("cancel the Lens request when the user adds the current
  word"). ➕ pressed before the scan's final result completes the scan's `cloudStop`: `CompositeOcr` cancels the Lens
  request, the ML Kit whole-screen page becomes final (`cloudWithdrawn`), the chip reads "On-device" without ⚠, and OCR
  boost (cloud reads of small-text bands) stays off for that scan. Other words of the same scan are then looked up only
  in the on-device text until the user starts a new scan (tap on the bubble or a new pull-out). ➕ after the final
  result changes nothing.
- **Why it was done:** the note used to wait for the final result (up to the 15 s cloud timeout) and could get a
  different sentence than the one on the screen. Since the note now fixes its word and sentence when ➕ is pressed
  (Q1), it no longer needs the cancellation to be fast or correct; the cancellation now only decides whether the rest
  of the scan gets the better cloud text, and whether the popup changes under the user after the note.
- **Trade-offs seen:** keeping the cloud request means the popup may switch to the cloud text a few seconds after the
  note (the added word may be split differently, see Q1) and one more request is spent; cancelling it means lower
  on-device quality for every other word of that scan and no OCR boost.
- **Options** (to discuss; no recommendation yet):
  1. Keep cancelling (now).
  2. Do not cancel: the cloud result arrives and replaces the text as usual; the note is unaffected; the added word gets
     📖 again if the new text has the same term (and Q1 option 3 could cover a different split).
  3. Do not cancel, but keep the popup on the word it shows until the aim moves; the cloud text is used from the next
     aim on.
  4. Make it a setting.

## Answered
