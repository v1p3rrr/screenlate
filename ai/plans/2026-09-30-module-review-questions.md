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

### Q5. The "no dictionaries" message always says they are still being installed (module 5 run 1)

- **Feature:** what the popup and the search screen say when a lookup finds nothing and there is no dictionary with
  definitions to search (`overlay_no_dictionaries`). With dictionaries, nothing found hides the popup as in Yomitan.
- **How it works now:** the only text is "Dictionaries are still being installed. Try again in a moment." It fits the
  first minutes after installing the app, while the bundled dictionaries are imported. It is wrong when the user
  deleted or switched off every term dictionary, or has term dictionaries only for another language: the message then
  stays forever. (Since this review the check itself follows the lookup: dictionaries of another language or with
  missing files no longer count, so these cases now reach the message instead of a silent hidden popup.)
- **Options:**
  1. Recommended: two texts. While an import is queued or running: the current one. Otherwise: "No dictionary with
     definitions is on. Open Settings → Dictionaries to add or turn one on." (the search screen could add a button to
     the Dictionaries screen). Needs the import queue state in the overlay and the search screen; strings in 14
     locales.
  2. One neutral text for both cases, e.g. "No dictionary with definitions is installed or on yet." (strings only).
  3. Keep as is.

### Q6. The engine cuts the result list before dictionary priority is applied (module 5 run 1)

- **Feature:** the order and the limit of lookup results (Settings → Lookup → "Entries shown", 32 by default).
- **How it works now:** hoshidicts sorts the candidates by its own order (match length, deinflection, frequency, score)
  and returns the first 32; `YomitanSorter` then applies Yomitan's order, which puts dictionary priority before score.
  An entry that only the higher-priority dictionary has and that ranks 33rd by score is lost, while entries of lower
  priority dictionaries stay. It happens only with several term dictionaries and more than 32 candidates of the same
  match length and frequency, e.g. one-kana matches; the lost entries would be near the end of the list.
- **Options:**
  1. Recommended: ask the engine for all candidates when more than one term dictionary is on, cut after our sort.
     Exact; costs more for lookups with many candidates (all glossaries are read and passed to Kotlin). I would
     measure a one-kana lookup on the phone with the owner's dictionaries first and fall back to option 2 if it is
     slow.
  2. Ask the engine for a margin (e.g. twice the limit): cheap, fixes nearly all cases, not exact.
  3. Keep as is (a note in `yomitan-behavior.md`).

### Q7. The lookup page may load anything from the internet (module 6 run 1)

- **Feature:** the popup and the search screen show dictionary entries in a WebView page. Dictionaries bring their
  own `styles.css` and structured content; Settings → Appearance has a custom CSS field.
- **How it works now:** the page answers its own addresses (page files, dictionary media, fonts) and lets every other
  request go to the network. A dictionary whose CSS says `background: url(https://example.org/x.png)` makes every
  lookup that shows it contact that server, which learns when (and, with per-entry URLs, what) the user looks up.
  Bundled dictionaries do not do this; an imported one could. A custom CSS that loads a web font (`@import` of Google
  Fonts or a `url()` in `@font-face`) works today and is the one legitimate use.
- **Options:**
  1. Recommended: a Content-Security-Policy on the page that blocks remote images, media, scripts and fetches, but
     allows remote stylesheets and fonts, so a custom CSS with a web font keeps working. Dictionary CSS could still
     pull a remote font, which is rare and does not identify the word.
  2. Block every remote request; web fonts only through Settings → Fonts (local files). The strictest; a custom CSS
     with a remote font stops working.
  3. Keep as is.

### Q8. The background work hint names one phone maker (module 11 run 1)

- **Feature:** Settings → Background work → "Launch" card. It opens the phone maker's startup settings when the app
  knows one (a list of 23 screens of 13 makers in `StartupScreens`), otherwise the app info.
- **How it works now:** the card's text (`background_launch_text`, all 14 locales) ends with one maker's exact path:
  "Honor / MagicOS: App launch → Screenlate → Manage manually, with all three options on." Every user sees it,
  whatever their phone. The repo rule keeps README and `docs/` free of device and vendor names; UI strings are not
  named in that rule, so I left it.
- **Options:**
  1. Recommended: show the maker's path only on phones of that maker (a second string, shown when the found startup
     screen is Honor's or Huawei's); others see the generic text. The path stays useful where it applies.
  2. Drop the path from the text: generic for everyone, Honor users find the options themselves.
  3. Keep as is.

### Q9. A system dark mode switch closes a kanji or link view in the search screen (module 11 run 1)

- **Feature:** the search screen ("Look up words", also "Look up in Screenlate" from the text selection menu).
  Tapping a kanji or a link in the results opens it as a view on top, with a back arrow.
- **How it works now:** switching the system to dark mode (by hand or on a schedule) makes Android recreate the
  screen; the results come back in the new theme, but the opened kanji or link view and the scroll position are gone.
  A change of the app's own theme setting does not have this problem (it is set on another screen).
- **Options:**
  1. Recommended: let the app's activities handle the dark mode switch themselves (`configChanges="uiMode"`); Compose
     redraws in the new colors and the search page restyles in place, keeping the view. Every screen then relies on
     Compose for theme colors, which it already does.
  2. Keep as is (a rare case: the switch happens while a view is open).

### Q10. "Look up in Screenlate" opens with the keyboard over the results (module 11 run 2)

- **Feature:** selecting text in another app and choosing "Look up in Screenlate" in the selection menu opens the
  search screen with that text already searched.
- **How it works now:** the screen always puts the cursor in the search field, so the keyboard opens and covers the
  lower half of the results, although the text is already there. The user has to close the keyboard to read. From the
  home screen's "Look up a word" the field is empty and the keyboard is what the user wants.
- **Options:**
  1. Recommended: focus the field (and open the keyboard) only when the screen opens empty; with selected text the
     results use the whole screen and a tap on the field opens the keyboard.
  2. Keep as is.

## Answered
