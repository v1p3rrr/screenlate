# Fixes, backups and Anki check on `main` (2026-10-15)

Owner requests 45-49 of [the languages plan](2026-10-10-languages-implementation.md): download lag, an Anki connection
check, choosing what a backup holds, the Yomitan import inside the backup screen, and the work of the language
branches that does not depend on the new languages, all on `main`. The work is done on `main` and merged into
`languages/main`; the language branch then adds the choice of languages in backups.

## Decisions

| Topic | Decision |
|---|---|
| Release | None for now; `main` only gets the changes (owner, 2026-10-15). |
| Download lag | Seen by the owner on the Dictionaries screen of the languages test build (Japanese and English), maybe everywhere. Find what loads the main thread while a download or import runs and fix it on both branches. |
| Anki connection check | A "Check connection" button on the Anki page asks AnkiDroid again and lists the result per item: AnkiDroid installed, access granted, deck found, note type found, fields match. It sits in a "Connection" card at the top of the page, shown in every state (the not-installed and no-access texts move into it); the list shows ✓/✗ with how to fix a failure, the deck and note type by name, missing and new fields by name; items after a failure are "not checked". Read-only. |
| Backup: settings | Chosen per section when creating, the same list as on restore; all ticked at first. Shown as a step after "Create backup…": a large dialog whose list scrolls while its buttons (Cancel, Next) stay in view; Next opens the file picker. The restore list stays on the screen, as it appears only after a file is chosen. |
| Backup: files | "Fonts" and "Dictionaries" are items of their own (fonts leave the Popup section) that expand with an arrow into a list of files with their sizes, each with its own box. |
| Backup: defaults | The first time every settings section and every font is ticked, the dictionaries are not. The screen remembers the last choice; a font or dictionary added since takes the mark of its item. |
| Restore | The same expandable lists: fonts and dictionaries can be restored one by one. |
| Backup: languages | `languages/main` only: an expandable "Languages" item lists the turned-on languages; a language left out takes out its own settings, its dictionaries, the fonts downloaded for it and its recognition models. Shared sections are chosen as before. Not on `main`, which has Japanese only. |
| Backup: recognition models | Go into the backup with their language (`languages/main`). |
| Yomitan import | A card "Import from Yomitan" on the backup screen with its two parts (settings export, dictionary collection), last, after Restore. The Settings list loses its item; the Dictionaries screen's button opens the backup screen at that card. The Settings item keeps its title "Backup and restore"; its subtitle adds the import from Yomitan. |
| Brought to `main` | The bug fixes below, and of the other changes: the dry wording (strings, README, docs, release-note files), the translation button named in words and off on new installs, the confirmation before deleting a font, Japanese pitch accent colors, short frequency chip names, resumable downloads, tags on kanji entries. Every rule chain behind "+N" turned out to exist only for the languages run through Yomitan's code; for Japanese it moves to the far backlog (owner, 2026-10-15). |
| Testing | Every change on `main` is checked on the emulator, with what it may touch (test model cases), before the merge into `languages/main`. |

## Bug fixes from the language branches

1. The search screen opened from the selection menu showed a white card on the dark theme until its first result
   (`fff2e6a`).
2. The Dictionaries page's first opening showed empty sections for about 0.6 s (`8fab47d`).
3. A language chosen in the system's app settings while the app ran left language names and catalog titles in the
   previous language (`881177f`).
4. Home's audio card opened the top of the Anki page instead of its audio section (`b75305e`). ("Add audio source"
   starting as JapanesePod101 for English, `666162e`, does not concern `main`.)
5. The bubble's hold menu ignored e-ink and followed the system's day or night mode instead of the app's theme
   (`73c6a57`, part of `e0231ba`).
6. Bundled installs were named by their archive, not by their title (part of `e0231ba`). (Catalog downloads named
   by the catalog's localized titles, `9aec26d`, need the language branch's catalog.)
7. A monolingual Japanese dictionary with loanword spellings was taken for Japanese → English (`aca8a48`).
8. A lookup starting at a radical look-alike (⽇ for 日) found nothing (Japanese part of `329dc62`).
9. AnkiDroid's hidden default deck was missing from the deck list (`f9be87b`).
10. A download that broke off showed its technical reason; it now says the connection was lost (`c3c1961`, with
    resumable downloads).

Found by the owner on 2026-10-15 (both branches):

11. After "Update" the update check said "All dictionaries are up to date" while the updates still downloaded. The
    check now closes once every found update has started.
12. The chosen segment's check mark pushed long labels to the edge or cut them (Russian: "По требованию",
    "Системная"). The owner chose to drop the check mark; the fill marks the choice.

## Other changes brought over

- Dry wording: `b8a6fc7`, `c7908d1` (strings in 14 locales, docs, README, release-note files), with the later wording
  fixes that touch shared strings.
- Translation button: `4719e6c`.
- Font deletion confirmation: the dialog of `57381f8` without the Chinese fonts.
- Pitch accent colors: `dd3e825`, `a430fec`.
- Frequency chip names: `773c9a7`.
- Resumable downloads: `4e9c785`, `c3c1961`.
- Every rule chain: not brought over. On the language branches the other chains come only from the lookup through
  Yomitan's code; hoshidicts keeps the first chain to a word and drops the others (見られる → 見る as passive, not
  also as potential). Collecting the dropped chains for Japanese is in the far backlog.
- Kanji entry tags: `d7f1509`.

## Order

1. Bring the fixes and changes above to `main` (cherry-picks where they apply, otherwise ported), with their tests.
2. Download lag: measure on the emulator (debug and release builds), fix.
3. Anki connection check.
4. Backups: sections, fonts and dictionaries lists on creating and restoring, the remembered choice; the Yomitan
   card.
5. Docs (`docs/usage.md`, `docs/architecture.md`), strings in 14 locales, test model cases; emulator checks.
6. Merge `main` into `languages/main`; the Languages item and models in backups there; checks on the emulator.

## Progress (resume here)

Kept current while the work runs so a new session can continue from the last line. Commits on `main` since
`2bdd47d` are local until pushed (nothing pushed yet).

- Step 1 done: every fix and change above is on `main` with tests (commits up to `a617538`).
- Step 2 done: `f782837` (progress throttle, Compose stability config, catalog card lambdas), notes `0a3951f`.
- Items 11-12 done: `884a932`, `c0df4b2`.
- Step 3 done: `73bba6a`, checked on the emulator in English and Russian (all passed; no access; a stale note type),
  test model ANK11. Made without asking (for the owner to review): once shown, the check runs again after a setup
  change and when the page comes back; "not chosen" counts as a failure (a cross with "Choose it below").
- Step 4 done: `9d109bf`, `0192771`, checked on the emulator (test model SET2, SET9-SET11). Design (from the decisions; choices made without asking are marked for the owner's review):
  - `BackupSection.FONTS` is new, after POPUP; POPUP keeps only the settings. A backup of an older version has its
    fonts under POPUP: reading the head goes on through the font entries (`fonts/fonts.json` and the files, which
    come before any dictionary file), so the restore list shows its fonts with their sizes as a Fonts item.
  - Create: the same checklist as restore, in a dialog after "Create backup…" (first inline in the Create card,
    changed by the owner); Fonts and Dictionaries expand into files with sizes. Only the chosen settings sections go into `settings.json`; without the dictionary list `dictionaries.json`
    holds only the dictionaries whose files go in. `BackupDictionary.bytes` is new (the size of its files), so the
    restore list shows dictionary sizes for new backups (older backups: no size).
  - The remembered choice: preference keys `backup_sections_off`, `backup_fonts_on`, `backup_fonts_flipped`,
    `backup_dictionaries_on`, `backup_dictionaries_flipped` (not backed up, cleared by the full reset). A file item
    stores its mark and the files marked otherwise, so a new file takes the item's mark (`FileChoice`).
  - Restore of fonts now adds the chosen fonts or replaces the same font; installed fonts stay (before: the POPUP
    section replaced the whole font set). For review by the owner.
  - Yomitan: `YomitanImportScreen` becomes a card on the backup screen (last); the route, the Settings item and
    `SettingsPage.YOMITAN_IMPORT` go; the Dictionaries button opens the backup screen scrolled to the card.
  - Owner feedback (2026-10-15): the create checklist must not hang open all the time: either fold it under one
    header or show it as a separate step after "Create backup" is pressed. The owner chose the step: a large
    dialog, scrolling list, buttons always in view; the restore list stays as it is. A file item's box is
    ✓ when all its files are chosen, — for some, empty for none (already so: `FileChoice.state`). Then: item titles
    in semibold (owner); also the Yomitan card's section and dictionary rows, the same kind of list on that screen.
- Step 5 done along the way: `docs/usage.md` and `docs/architecture.md` describe the Anki check, the backup choice
  and the Yomitan card; strings in 14 locales; test model ANK11, SET2, SET9-SET11 and the run log.
- `main` pushed (`e6e20bf`).
- Step 6 in progress on `languages/main`: merge commit `41bf4ff` (not pushed). Conflicts resolved with this branch's
  versions wherever main only carried a port; main's new work merged in: the Anki check (`AnkiNotes.check(language)`,
  the check card below the language card, re-run when the shown language changes), the backup choice dialog and
  Yomitan card, the update check fix, the segment fill, the progress throttle and catalog card lambdas. Main's
  `settleInstall` and its test dropped (`settleFirstRun` stays). Builds; 805 unit tests and 127 page tests pass.
  Next: the Languages item with models in backups (design below), then emulator checks of the merge (Anki check
  per language, backup dialog, Yomitan card, update check, download lag measured again), push.
- Languages item design (`languages/main`; choices made without asking are marked "review"):
  - The item is shown when two or more languages are listed: on create the turned-on languages, on restore the
    backup's. With one language it is always included (review). It comes first in the list (review). Each language
    row shows the size of its recognition models when it has any.
  - A language left out takes out: its per-language settings keys (`SettingsKeys.languageOf`), its dictionaries
    (files and list entries, by `sourceLanguage`) and sort dictionary, catalog fonts whose `CatalogFont.language` is
    its code, and the models used only by left-out languages. Dictionaries without a language and fonts of the
    user's own are shared. Dictionaries of languages that are not turned on are not offered on create (review).
  - Audio autoplay and volume live in Japanese's `audio_settings`: with Japanese left out and Audio chosen, the backup
    keeps only those two values in it; restoring Audio without Japanese takes only those two (review).
  - Turned-on languages stay a General setting. Create writes the profile limited to the chosen languages (active one
    moves to the first chosen if left out). Restoring General no longer replaces the turned-on list: the chosen
    languages are added to it, the backup's active language, stars and flags apply when that language is chosen;
    nothing is turned off (review). A backup without the profile only turns its chosen languages on.
  - Models: the manifest lists `models` (id, version, engine, languages, bytes) and `languages` (codes). Files go
    under `models/<id>/` after the fonts and before `dictionary-files/` (older versions skip them). Restoring installs
    a chosen language's model only when no version of it is installed (review); `OcrModelStore.restore` takes a staged
    directory. No catalog model exists yet, so this path is covered by unit tests only.
  - Older backups (no `languages` in the manifest): the languages are the backup's turned-on list (Japanese alone
    without one) plus the supported languages of its dictionaries and fonts.
  - Remembered choice: `backup_languages_on`, `backup_languages_flipped` (app state, cleared by the full reset).
  - The restore summary adds the models restored and the languages turned on.
- Questions for the owner (asked when the owner is back): none yet. Choices made without asking, to show the
  owner: the Anki check re-runs after a setup change and on resume; "not chosen" counts as a failed item;
  restoring fonts merges them into the installed ones instead of replacing all.

## Changelog

- 2026-10-15: plan written from the owner's answers.
- 2026-10-15: the owner reported the update check's "up to date" during updates and the segment check mark; both
  fixed (items 11-12), the check mark removed by the owner's choice.
- 2026-10-15: the owner chose the Anki check's place (a card at the top), the backup checklist on the screen, the
  Yomitan card after Restore and the Settings item's title.
- 2026-10-15: the owner asked to bring everything new into `languages/main` after `main` (step 6). Rule chains for
  Japanese moved to the far backlog (owner); `666162e` and the catalog part of `9aec26d` left out as language-branch
  only.
- 2026-10-15: the owner asked to work on without them, to keep questions until they are back and to keep the
  progress written down; the "Progress" section above is the resume point.
- 2026-10-15: the owner moved the create checklist from the Create card into a dialog after "Create backup…"
  (scrolling list, buttons always in view); the restore list stays on the screen.
- 2026-10-15: the owner asked for the backup list items' titles in semibold.
