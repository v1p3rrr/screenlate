# Code review of the new code by module (2026-10-15)

Owner request (2026-10-15): the full code review of `notes/module-map.md`, one run per functional module, each
covering the module and its whole integration path. Asked before writing this plan (2026-10-15/16); the answers are
the decision table. This pass reviews the code changed since the last full review; the full review of all code
follows when every language is done.

## Decisions

| Topic | Decision |
|---|---|
| Modules | The 18 modules of `notes/module-map.md` as rewritten on 2026-10-15 (owner). |
| Scope of this pass | Everything changed since the end of the last full review (`bb57fcc`, 2026-09-30) up to `languages/main`: the language work and `main`'s changes since then (about 29,000 added lines). Each module's run covers its changed code and follows the changed code's integration paths into the code it calls and that calls it. Old code is not reviewed for its own sake now (owner, 2026-10-16: "old code not yet, until the language branch is finished"). |
| Later | When every language is built: the full review of all code, module by module bottom-up (the map's order), new code included again, 3–4 runs per module as in `2026-09-30-module-reviews.md` (owner). |
| Order | The map's order, bottom-up: lower layers are fixed before the modules built on them are reviewed. |
| Runs | One run per module (owner). |
| Review tool | The `code-review` skill at `xhigh`, all findings (`--max-findings all`, owner), `--fix` after each batch of findings (owner), on top of the map's "How to run each review" checklist. Each finding is checked against the code before it is fixed; false alarms are recorded as such. |
| Owner's input | No reports per module (owner). Choices that need the owner (behavior, UI, defaults) go to "Questions for the owner" below with context and options, the code stays as it is, and the work goes on; they are asked together at the end. Plain bugs are fixed without asking, with tests. |
| Bugs outside the module | Fixed when found, recorded under the module whose code it is. |
| Branches | Code that `main` also has is fixed on `main` first (`.worktrees/main`), tested, pushed, then `main` is merged into `languages/main` (root checkout), tested, pushed; code only `languages/main` has is fixed there. A finding in a file that differs between the branches is checked in `main`'s version too. |
| Other sessions | Paused while the review runs: no other work in the checkouts or on the emulator (owner, 2026-10-16). |
| Commits | After each module's fixes, per branch, pushed to `origin`; messages describe the change. |
| Checks | Unit tests (and page tests when page scripts change) after each module's fixes; the emulator after each module whose fixes touch screens or the overlay, with the test model cases of the touched areas (new cases for new behavior); at the end the E cases (every regression) on both branches (owner). |
| Releases | None. |

## Procedure of a run

1. List the module's files changed since `bb57fcc` (`git diff --name-only bb57fcc languages/main` filtered by the
   map's entry) and read them with their diffs; then follow the integration paths of the changed code.
2. Run `code-review xhigh --max-findings all --fix` on those files, with the module's integration paths and the
   checklist in the arguments.
3. Check every finding against the code; fix plain bugs (on `main` first when `main` has the code), with tests; move
   choices to the questions; record false alarms.
4. Tests, the emulator when needed, the progress table and findings below, docs and notes when behavior changed.
5. Commit and push per branch; merge `main` into `languages/main` when `main` changed.

## Progress

Changed files since `bb57fcc` per module (counted by the map's entries; tests of a module count with it):

| # | Module | Changed files (added lines) | Run | Findings | Fixed | Questions | Commits |
|---|---|---|---|---|---|---|---|
| 1 | Build, CI and release | 32 (388) | | | | | |
| 2 | Common | 11 (223) | | | | | |
| 3 | Language support | 21 (1,524) | | | | | |
| 4 | OCR and on-device models | 18 (725) | | | | | |
| 5 | Dictionary engine and Yomitan's language code | 36 (4,271) | | | | | |
| 6 | Lookup | 22 (1,995) | | | | | |
| 7 | Dictionary registry, imports and the Dictionaries screen | 38 (3,647) | | | | | |
| 8 | Dictionary catalog, updates and built dictionaries | 18 (6,789, mostly catalog JSON) | | | | | |
| 9 | Languages: profiles, setup and switching | 41 (3,764) | | | | | |
| 10 | Lookup page and rendering | 19 (1,826) | | | | | |
| 11 | Anki export | 22 (1,354) | | | | | |
| 12 | Audio | 12 (725) | | | | | |
| 13 | Sentence translation | 22 (2,541) | | | | | |
| 14 | Overlay runtime | 29 (2,264) | | | | | |
| 15 | Appearance and fonts | 23 (1,446) | | | | | |
| 16 | App shell, home, search and localization | 23 (1,396) | | | | | |
| 17 | Settings reset, backup and Yomitan import | 22 (2,012) | | | | | |
| 18 | Updates, About and logs | 7 (55) | | | | | |
| — | E regression on both branches | | | | | | |

## Current position

Updated after every step so the work survives a context reset: the plan is written; module 1 is next.

## Findings

Per module: the findings, what was fixed (with the branch and commit), what went to the questions, false alarms.

## Questions for the owner

Collected during the runs and asked together at the end.

## Changelog

- 2026-10-16: written after the owner's answers (modules, scope, order, runs, review tool, reports, sessions, checks).
