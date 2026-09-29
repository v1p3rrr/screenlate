# Module code reviews (2026-09-30)

Owner request (2026-09-30): review the functional modules of `ai/notes/module-map.md` one after another without a
command per run: several review runs per module, fixing what they find, then the next module. Questions that need the
owner are collected in `2026-09-30-module-review-questions.md` and asked when the owner is back or at the end.

## Decisions

| Decision | Choice |
|---|---|
| Modules | The 11 not reviewed yet: 1–8 and 11–13 of the module map, in the map's order. Modules 9 and 10 (two runs each already) wait for another time. |
| Runs per module | Runs 1 and 2 cover the whole module and its integration paths; run 3 covers the fixes of runs 1–2 and the code around them. A 4th run covers run 3's fixes when run 3 changed code, except very small edits that almost never cause problems (docs, comments, renames, strings). No 5th run: what the 4th finds is fixed and reported. |
| Review level | xhigh (the code-review procedure's level). |
| Findings that need the owner | Behavior, UI, defaults and other real choices go to the questions file with context and options (recommended first); the code stays as is and the work goes on. |
| Plain bugs | Fixed without asking, with tests. |
| Bugs in another module's code | Recorded under that module below and fixed in its own runs. Bugs in modules 9 and 10 are fixed after all 11 modules, each with a check of the code around the fix. |
| Commits | One commit per module after its last run, pushed to `origin main`. Messages describe the change. |
| Checks | Unit tests (and page tests when page scripts change) after every run; the emulator after every run whose fixes touch UI or overlay behavior (Screenlate's accessibility service may be enabled there). |
| Releases | None without the owner's command. |

## Procedure of a run

1. Runs 1–2: read the module's code in full and follow each integration path of the module map into the other side
   (callers, callees, JS bridge, DataStore keys, Room, WorkManager, intents, JNI, network). Run 2 looks again with the
   run 1 fixes in place and pays attention to what run 1 did not cover.
2. Run 3 (and 4): the diff of the module's earlier runs, the enclosing functions and their callers.
3. Findings are ranked with a concrete failure scenario and reported (ReportFindings); plain bugs are fixed, choices go
   to the questions file, bugs in other modules to "Deferred" below.
4. After fixing: tests, emulator when needed, the plan's progress table, docs and notes when behavior changed. The
   ReportFindings list is re-sent with outcomes.
5. After the module's last run: `ai/status.md`, commit, push, a short Russian progress message.

## Progress

| # | Module | Run 1 | Run 2 | Run 3 | Run 4 | Commit |
|---|---|---|---|---|---|---|
| 1 | Build, CI and release | | | | | |
| 2 | Common and language support | | | | | |
| 3 | OCR | | | | | |
| 4 | Dictionary registry, imports and catalog | | | | | |
| 5 | Lookup and engine | | | | | |
| 6 | Lookup page and rendering | | | | | |
| 7 | Anki export | | | | | |
| 8 | Audio | | | | | |
| 11 | App shell, home, search and localization | | | | | |
| 12 | Backup and Yomitan settings import | | | | | |
| 13 | Updates, About and logs | | | | | |
| — | Deferred fixes in modules 9 and 10 | | | | | |

## Findings

Per module and run: the findings, what was fixed, what went to questions.

## Deferred

Bugs found in another module's code, fixed in that module's runs (modules 9 and 10: after all 11 modules).

## Changelog

- 2026-09-30: plan created from the owner's answers (modules, runs, level xhigh, questions file, deferring other
  modules' bugs, commits per module, emulator after each run).
- 2026-09-30: owner asked to discuss later how ➕ behaves on a draft or an old popup and whether ➕ should cancel the
  cloud request (questions Q1, Q2).
