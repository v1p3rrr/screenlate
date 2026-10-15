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
| Backup: settings | Chosen per section when creating, the same list as on restore; all ticked at first. The list stays on the screen in the "Create a backup" card, in place of "Include dictionaries", above the Create button. |
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
- Steps 4-6: not started.
- Questions for the owner (asked when the owner is back): none yet.

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
