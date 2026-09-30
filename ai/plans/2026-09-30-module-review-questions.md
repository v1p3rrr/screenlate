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

### Q3. A bundled dictionary from an unversioned URL comes back after the user deleted it (module 1 run 2, module 4)

- **Feature:** dictionaries shipped in the APK (JMdict English, Jiten frequency list, Kanjium pitch accents) and
  installed on first launch (`BundledDictionaries`, module 4), downloaded at build time (`downloadBundledDictionaries`
  in `app/build.gradle.kts`, module 1).
- **How it works now:** each shipped archive is remembered as installed by name and size. A deleted bundled dictionary
  stays deleted while the same file ships; when a later app version ships a file of the same name with another size, it
  is imported again (replacing the dictionary of the same title, or bringing it back if the user deleted it). This is
  how a deliberate update of a bundled dictionary reaches existing users.
- **The problem:** JMdict and Kanjium come from pinned release URLs, but the Jiten list comes from
  `api.jiten.moe/.../download` without a version. The build caches the file; CI refreshes its cache whenever
  `app/build.gradle.kts` (or, since this review, the download code) changes, so a release made after such a change
  ships a slightly different Jiten file (7 817 828 bytes on 2026-09-26, 7 821 570 bytes on 2026-09-30). On update every
  user gets Jiten imported again (a few seconds in the background), and users who deleted it get it back. The next
  release will do this, because the CI cache key changed in this review.
- **Options** (recommended first):
  1. Remember deletions: a bundled dictionary the user deleted is never imported again, whatever size ships later; one
     the user kept is still updated when the file changes. Keeps the Jiten list current.
  2. Pin the Jiten file: download it once, attach it to a GitHub release of this repository (CC BY-SA allows it with
     attribution) and bundle it from there; it changes only when we update it on purpose. Deleted dictionaries would
     still come back after a deliberate update, unless combined with 1.
  3. Treat the name alone as the identity: once installed or deleted, a bundled file name is never imported again;
     updates of bundled dictionaries then come only through the dictionary update check (`indexUrl`) or a renamed file.
  4. Keep as is.

### Q4. A failed import card does not say which dictionary failed (module 4 run 1, module 11)

- **Feature:** the import queue on the Dictionaries screen. Every download, update or file import is a card; a failed
  one stays until dismissed (`TaskCard` in `DictionariesScreen`).
- **How it works now:** the failed card shows "Import failed: <reason>", and the reason replaces the dictionary name:
  `task.error ?: task.name`. After "Update all" with three updates, a card reads "Import failed: HTTP 404" and nothing
  tells which of the three it was (the order of the cards is the queue order since this review, which helps a little).
  The name is shown only when the reason is unknown.
- **Collections:** since this review a Yomitan collection import goes on after a dictionary of it fails, and the error
  lists each failed one as "Title: reason; Title: reason", so there the names are already in the text.
- **Options:**
  1. Recommended: show both, e.g. "Jitendex: import failed — HTTP 404" (name first, then the reason; for a collection
     the name is the backup file). A changed string in all 14 locales.
  2. Keep as is.

## Answered
