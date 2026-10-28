# Languages beyond Japanese: implementation

Status: in progress, stage A (2026-10-10; the owner said to start with the base and the first language). Follows the research and decisions of
`2026-10-09-more-languages.md` (requests 1–19, research status, handoff); its decision table is continued here and
binding from now on. Findings: `notes/languages.md`.

## Owner requests

Requests 1–19 are in the previous plan. New ones:

20. (2026-10-10) Remaining open questions before implementation. IPA is to be named so that users understand it, not by
    the abbreviation; is it highly recommended, if not mandatory, given its small size?
21. (2026-10-10) No intermediate releases and no beta labels: every language is built first, then one big release;
    the owner tests debug builds until then. The postponed items are settled when the first language or group 1 is
    ready, before it is merged.
22. (2026-10-10) Turning a language off asks about its files; the settings also offer to delete the files of languages
    that are turned off, per file or, simpler, all files of such a language at once.
23. (2026-10-10) Build the whole shared base, so later languages are only additions, and English on it. When both are
    done, tested and checked on the emulator, run the code-review skill at xhigh with `--fix` twice over the whole
    multi-language change, following the owner's earlier review instructions (`notes/module-map.md`, "How to run each
    review"): bugs first, and also translations in all 14 locales, docs, tests, privacy, the GPL boundary and the UI
    rules.

24. (2026-10-11) Fix the four findings from the review against `2215893`: keep engine preparation and queries atomic,
    isolate Yomitan dictionary settings by profile language, reset Anki editors on language switches, and offer to
    cancel initial downloads when turning a language off before it has installed files. Add regression tests and
    verify the fixes, including the UI on the emulator.

25. (2026-10-11) Fix the four findings from the follow-up review against `2215893`: cancel bundled imports when
    deleting a language, select and lock the correct dictionary set for media reads, keep the note's captured
    language throughout sentence parsing, and invalidate audio selections when the regional order changes.
    Add regression tests, verify as for request 24, then commit and push.

26. (2026-10-11) Fix the two findings from the third delegated review against `2215893`: enable selected installed
    dictionaries when confirming a language's setup, and reset Search's note/audio state when its language changes.
    Add regression tests, verify on the emulator, commit and push as for requests 24 and 25.

27. (2026-10-11) Show the installation/readiness status after activating a language while its dictionaries or OCR
    are still downloading. The owner wants explicit status for everything needed to recognize words and show
    definitions, or a link to the section showing that progress. Audit today's behavior; collect the placement and
    pre-readiness scanning choices before implementation. Keep errors and remaining optional downloads visible.

28. (2026-10-11) The owner asked why no emulator window is visible. The local checks use `-no-window` and adb;
    UI verification uses screenshots. No change to emulator visibility was requested.

29. (2026-10-11) After all checks, close the emulator started for this session and verify that its processes ended,
    so it does not keep consuming the owner's computer resources. Leave unrelated emulator instances alone.

30. (2026-10-13) Hosting of the files we build or mirror (dictionaries the app cannot download from their authors) is
    postponed with no date. Keep it in the open items and remind the owner of it whenever they ask what is left open
    or unfinished in this task.
31. (2026-10-13) The agent compares the APK size per ABI with the last release itself.
32. (2026-10-13) The agent tests on the emulator itself what the stage's "Done when" left to the owner: Japanese
    behaves as before (regression), an upgrade from the last release keeps every setting and dictionary, and a backup
    made by the last release restores. The owner cannot check the upgrade on the phone: the release and the debug
    build are different apps.
33. (2026-10-13) Check that the translation button (sentence translation) is off by default, after an upgrade and on a fresh
    install. Found: it is on by default, as the decision table of `2026-09-29-feedback-after-0.1.4.md` says; asked.
34. (2026-10-13) Language profiles: a section on the home screen or a screen of its own (not the general settings)
    with every language profile, turned on or not; a turned-off one shows the dictionaries and technical files (OCR
    models) it left behind. There up to three languages are chosen for quick switching, shown as flags (emoji or SVG)
    in the bubble's hold menu; more languages may be turned on.
35. (2026-10-13) Check what happens when a new language is activated before its dictionaries have downloaded
    (request 27 built the readiness status; verify it).
36. (2026-10-13) Downloads must finish after the app is closed, killed or has crashed, or the user can retry them by
    hand.
37. (2026-10-13) The items "noticed, not addressed" (transcription variants, IPA tags, odd form-of tags, "walke"
    before "walk") and the review's skipped navigation callback lost on activity recreation: say what can be done and
    fix what is ours. Done 2026-10-14: the navigation of the first run and "Add a language" now goes through one-shot
    events; the odd form-of tags were an earlier wty revision (the current one is clean); "walke" before "walk" is
    Yomitan's order without a frequency dictionary, fixed by the wordfreq sort dictionary; transcriptions follow the
    "Transcriptions" decision.
38. (2026-10-13) Ask the Chinese and Korean questions now; build both after the agent's own emulator testing and the
    fixes above.
39. (2026-10-14) The sentence translation button is "the translation button" everywhere (UI strings in 14 locales,
    docs, KDoc, notes, plans); "文A" was only the owner's hint for the icon.
40. (2026-10-14) Audit every user-facing text (UI strings in all 14 locales, Russian first; hints and ⓘ texts; docs;
    release notes) for literal, naive, wordy or storytelling phrasing and rewrite it in the dry tone of software
    documentation; detailed explanations stay where needed, in the same tone. The v0.2.3 notes are the example of what
    not to write ("follows the finger again; in 0.2.2 it lagged"): "Fixed the bubble freezing when pulled out from the
    edge" is enough.
41. (2026-10-14) Pitch accent colors for Japanese, like the Chinese tone colors (a switch, off by default).
42. (2026-10-14) A test model of the whole app: test cases for everything that can be checked on the emulator, old
    and new, including non-functional ones (nothing lags, timings). Each case gets a category: checked once; checked
    once and again in a full regression; checked in every regression (large changes, before a release); new (moved
    into one of the others once checked). Cover with integration (instrumented) tests whatever they can cover, so the
    agent does not click through it every time; the rest is checked by hand on the emulator. Order: after all the
    improvements, before the full emulator testing, write the model; then test by it. The agent may install and set
    up AnkiDroid, decks and whatever else the emulator needs, and open harmless, lawful websites in the emulator's
    browser to check recognition through the app (without reading their content beyond that).
43. (2026-10-15) After Chinese, before Korean: the frequency chip shows the dictionary's name shortened to one letter
    unless it is short; a tap on the chip shows the full name, another tap shortens it again. "+N" (more than one
    frequency value) also shows the first chip's full name. Decision row "Frequency chip names".
44. (2026-10-15) Latvian joins group 3, the last one before "every other language".
45. (2026-10-15) The interface lags while dictionaries download; perhaps the main thread is busy.
46. (2026-10-15) A check of the connection to Anki.
47. (2026-10-15) Backups: choose which settings and which files (fonts, anything else) go into a backup.
48. (2026-10-15) The import from Yomitan moves into the backup screen as a section of its own.
49. (2026-10-15) Requests 45-48 and the bug fixes made on the language branches that do not depend on the new
    features go into `main` as well. Plan and decisions: [2026-10-15-main-fixes-and-backups.md](2026-10-15-main-fixes-and-backups.md).
50. (2026-10-15) Order after group 1: ask the questions of groups 2 and 3 now; build everything possible for group 2;
    then the OCR tests for groups 3 and 4 (Paddle against Tesseract, MNN against ONNX Runtime, on real screenshots).
51. (2026-10-16) Group 4 candidates, so they are not forgotten: Hindi, Dutch, Malay, Filipino, Indonesian, Serbian,
    Georgian, Kazakh, Armenian, Czech, Greek, Bengali, Thai, Belarusian, Kyrgyz. Yomitan support and dictionaries are
    checked when group 4 starts; a language without them may be skipped.
52. (2026-10-16) Backlog: word audio kept on the phone, optional (several GB, more with more languages). Like
    Yomitan's local audio server (on a server or in Anki on a computer), or simpler, audio collections downloaded
    through the app; either way the files end up on the phone, so the owner leans to doing it in the app.
53. (2026-10-16) Ask every remaining question now; then the agent works on alone through the whole plan until the
    implementation and the OCR tests are done (rows from "OCR engine choice" to "Diacritics of Latin-script languages").
54. (2026-10-16) While the owner is away: questions that come up wait until everything possible is done; a blocking
    question postpones the task or the part of it that depends on it, without taking the recommended option in
    advance when the question is important or another answer would mean a large rework.
55. (2026-10-16) The region order in the audio settings shows only while something uses it: removing the source it
    exists for (Wiktionary) hides it, unless another source needs it too (question 28: transcriptions use it).
56. (2026-10-16) The audio source picker offers a language only the sources that have it: JapanesePod101, its
    dictionary search and Jisho are Japanese only (German offered JapanesePod101).
57. (2026-10-16) Yomitan's languages listed with their status here (done, on branches, planned, planned without
    Yomitan support, the rest by number of speakers): `notes/languages.md`. Group 4 is too large for one stage;
    split it by script into batches (question 30).
58. (2026-10-16) The release waits until every language is covered, group 4 included; then a large code review, then
    the owner uses the build for a while. Asked whether a prerelease can be offered as a beta that users download
    separately and that stays out of the main releases and their automatic updates (row "Beta builds").
59. (2026-10-16) The OCR comparison covers the later languages too, above all the scripts that differ (Arabic,
    Hebrew and the like), not only groups 1-3 (row "OCR comparison scope").
60. (2026-10-16) Keep all three on-device engines (ML Kit, Tesseract, Paddle): per language, offer the models of
    the engines that read it, the measured best ticked by default and the others downloadable, switchable in the
    recognition settings; ML Kit's Latin model for Latin-script languages; Tesseract fast and best both offered,
    fast recommended where it copes as well (best may be better on stylized text such as comics); Chinese stays on
    ML Kit, which reads Simplified and Traditional in one model. Add the cloud recognition and harder, stylized
    screens to the comparison (run in a separate session). Write the plan, ask, and start the implementation in
    parallel (rows from "On-device OCR engines" on).
61. (2026-10-16) Later: review where the settings live. Anki and audio are different things shown in one place;
    the bubble settings and the recognition settings should be separated or regrouped.
62. (2026-10-16) Think through every case where something the app depends on is missing: no dictionaries (all
    of a language can be deleted), no on-device model (no offline recognition and no draft; settings that need it
    disabled), and anything else; list the limits and the cases to handle (plan `2026-10-17-ocr-engines.md`).
63. (2026-10-16) A model a new language would use that is already installed (the Latin model for a second
    Latin-script language) is not offered again: it shows as installed, ticked and locked; the other models stay
    optional.
64. (2026-10-17) Add PaddleOCR PP-OCRv6 small (one recognizer for Chinese, Japanese, English and 46 Latin-script
    languages, June 2026) to the comparison; it replaces PP-OCRv5's main and Latin recognizers where the
    recommendation rule says it is better (row "PP-OCRv6").
65. (2026-10-17) Paddle's Cyrillic recognizers write Russian words with Latin look-alike letters: fix it where it
    gives no false hits (row "Latin look-alikes in Cyrillic text").
66. (2026-10-17) Pause new languages after group 3 for a build of the 14 languages to use on the phone; no published
    prerelease for now. Paddle goes into that build; the code review comes after it (row "Build of groups 1-3").
67. (2026-10-18) On the phone Jiten does not download, and every retry seems to add more attempts: many "Waiting:
    Jiten" cards under one "Downloading Jiten". Waiting tasks should share one card instead of a card each. Analyze
    briefly, put it in the backlog and do it after the current plan (Backlog, "Download queue").
68. (2026-10-18) On the phone the downloaded on-device models seem about as slow as cloud recognition and ML Kit
    clearly faster; after Paddle's text is shown the spinner keeps turning. Measure scan times in the app per engine
    (the generated screens, the home screen); reconsider the default engines (cloud by default, Both or the device
    alone offered); say that ML Kit is built in for the languages it reads instead of offering a download as the
    default; mark ML Kit as faster where the measurements show it, above all for the draft with Engines on Both
    (plan `2026-10-17-ocr-engines.md`, questions 36-39).
69. (2026-10-20) Letters fixed by look-alike after recognition are for OCR results only: text the app reads from
    the screen's apps is taken as it is, in every language and branch, where the text itself is written right; a
    spelling people often type themselves stays matched in any text (row "Look-alike fixes only after OCR").
70. (2026-10-20) Group 6 research covers Catalan, Galician, Tatar with Bashkir, Icelandic, Afrikaans and Sanskrit
    (for Sanskrit, how to split words joined by sandhi and how to read Latin transliteration). Taiwanese Hokkien
    (Taiwan's Mandarin is covered by Chinese), Russia's other languages (Wiktionary under 5,000 senses each) and the
    rest of the long candidate list are recorded as deferred, with reasons.
71. (2026-10-20) The app keeps its own log: lines written to a file as they happen instead of read from the
    system log at export time (on the owner's Honor phone the system log gives the app nothing; on a ColorOS phone it
    held only the last 22 seconds). It is built on both main and languages/main (owner, 2026-10-21). Built on
    2026-10-22 on `claude/competent-rhodes-6bde05` (based on main) with the owner's answers: at most 8 MB (two files of
    4 MB, oldest lines dropped), in `noBackupFilesDir` (removed with the app); the export is one text file with the own
    log first and the logcat part after it; no button to clear it. Every module logs through `core.common.log.Log`.
    With it, for the bubble disappearing on a ColorOS phone (`ai/notes/oem-background-limits.md`): exit reasons at each
    process start; a stop report when the service stops (on the enabled list or not, time since the last bubble touch
    and scan, recent windows with system package names only); a notification "Screenlate service is off" at every stop,
    a banner without sound, opening the accessibility settings, cancelled when the service runs again, no extra
    permission request; all of it in regular builds (owner, 2026-10-22). Changed with request 78: the notification is
    posted 3 s after the stop, only if the service has not connected again (owner, 2026-10-22). Merged into main and languages/main on
    2026-10-22 (owner).
72. (2026-10-21) Build order of the researched blocks: Thai and Khmer (group 4c), then group 4e's follow-ups (Kazakh and
    Kyrgyz, questions 14-18), then group 5's blocks 5a-5e, then group 6's blocks 6a-6d, one session at a time; each
    block is merged into languages/main before the next starts (owner; placing group 4e's follow-ups right after Thai
    and Khmer is the agent's reading of "4c, then 5, then 6"). Each block's session first measures every engine and
    model of its languages on the emulator and checks its rules' hypotheses on data (the owner's instruction for group 6
    on 2026-10-21, applied to every block by the agent). A session that hangs, fails or waits for the owner never holds
    the chain: the next block starts at once and the stuck one is finished later; sessions working on their own do not
    use the built-in browser, whose site prompts wait for the owner (owner, 2026-10-22, after group 4e's session waited
    six hours on one).
73. (2026-10-22) Upstream reports filed at wty from the owner's account. #399
    (https://github.com/yomidevs/wiktionary-to-yomitan/issues/399): `handle_alt_of_sense` drops alt-of senses tagged
    abbreviation, so contractions and apocopic forms have no entry (German im, zum, zur, vom, beim; Italian un, del,
    della, nel; Portuguese do, da, na, ao; Spanish muy, tu, buen; Catalan del, al, pel; Galician moi), and writes the
    other alt-of rows the wrong way (bé → ben). In wordfreq's top 1,000 words that is 2.2% of the German tokens, 7.5% of
    the Italian, 7.8% of the Portuguese and 0.7% of the Spanish. #400: add Tatar. The owner waits for #399 until
    2026-11-05; then the agent checks the latest wty release (ca-en del, de-en im, it-en del) and, if it is not fixed,
    asks about an own supplement built from kaikki (group 6 plan, question 5). Both were done in wty's release
    2026.10.22 the same day (checked: ca-en del, al, pel, ben; gl-en moi; de-en im, zum, zur, vom, beim; it-en del,
    della, nel, un; `dict/tt` with 13 gloss languages), so the 2026-11-05 check and the supplement are dropped. The
    catalog's wty entries follow `latest` with no pinned hash, so the app gets the fix with no change; their sizes and
    word counts are refreshed against that release once group 5a's catalog edits are merged (group 5a found he-tr now
    passes the catalog threshold), and the owner's archive takes the new files. The release also reverses every
    sense-level alt-of row in every language (from the page to the target now) and adds rows for alt-of senses tagged
    misspelling, nonstandard, obsolete, pronunciation-spelling and misconstruction (question 87; follow-ups in request
    77).
74. (2026-10-22) Completeness check of every session that built or researched something (owner). All their commits are
    in languages/main (5c59894f); no numbered owner question blocks them apart from group 6's 10 and 21 and group 4e's
    14-18 (follow-ups after Thai and Khmer). `lintDebug` had never been run on the group branches; it passes on
    languages/main 5c59894f. Left open: (a) emulator checks not run yet, for each block's build or the next full
    regression: the audit's Czech "l." and Arabic ک ی fix (9ec4c279; two N cases to add to `test-model.md` after group
    4c's merge), ANK14's Chinese part, LK40, DIC20's setup and Home paths, DIC9's interrupted reset, the "Not checked"
    list of the 2026-10-18 run, Tesseract best for ara and heb, Paddle's right-to-left reading order (no model yet), the
    PanLex kk-en import, the 13 locales of group 4e's strings; (b) phone runs: the memory trim, pre-connect timings,
    page recreation, the draft-lead rule behind question 36 (a), the diagnostics build's export; (c) waiting on hosting
    or the owner's uploads: PanLex (its license rechecked in group 4e's follow-up: CC BY-NC-SA 4.0 on the live site, CC0
    on the mirror), ESPDIC, WikDict, MonWN, the Paddle Devanagari and Arabic models; (d) the 5c plan's 17 "technical
    choices of the agent" were never shown to the owner: the 5c build session presents them before writing code; (e)
    research files outside git: 5b's and 5e's OCR harness patches and group 6's screens are kept in the shared notes;
    (f) choices from the emulator-check reports are questions 67-73, the archive's are 74-76.
75. (2026-10-22) Holding the docked bubble opens the bubble menu without the items that need a scan; deferred, see
    Backlog "Bubble menu in the dock".
76. (2026-10-22, night) Autonomous work while the owner sleeps (owner): the build order of request 72 runs to the end;
    when a block's session is done, this session checks and merges it and starts the next. Every block is verified in
    full: unit and page tests, `lintDebug`, the debug build, and every test-model case it adds or touches run on the
    emulator. Order: group 4c's question 9 with the measurements of request 72 (running), the fixes for questions 67 and
    71 (a session of their own: code now, emulator checks when the emulator is free), group 4e's follow-ups (questions
    14 (a) and 15 (b) built, 16 and 18 counted for the owner, the PanLex license rechecked), then 5a, 5b, 5c, 5d, 5e,
    then group 6. Rules for each build session: start from the current `origin/languages/main` and push only its own
    branch; follow its block plan and the decision tables; questions go into the block's plan as Open, marked deferred
    (the part waits) or decided provisionally (with what was built), and are asked in the morning (5c's 17 technical
    choices among them); the emulator only while holding `.worktrees/emulator.lock` in the root checkout; strings in all
    14 locales, NOTICE, docs, `notes/language-rules.md` and `notes/languages.md`, test-model cases with the run log; wty
    data measured on release 2026.10.22 or later (request 77); never `am force-stop` or `am start -S` (uninstall
    directly), no sign-in on the emulator, no `connectedAndroidTest` of the app module, nothing on the owner's phone, no
    browsing outside the work folders; a report to this session with the branch, commits, results, what is not verified
    and the open questions. Paused (owner, 2026-10-22 evening, to keep the week's usage limit): group 5a stops after
    Slovenian; Esperanto, Albanian, Swahili, groups 5b-5e and group 6 (27 languages with Slovenian, 26 left after it)
    run later in a separate run, in the same order.
77. (2026-10-22) Follow-ups of wty's release 2026.10.22 in a session of their own (owner): the KDoc and notes of the
    other-script filters (`ignoredFormTags` of Malay, Indonesian, Turkic Cyrillic and Serbo-Croatian) describe the new
    direction; counts in `notes/languages.md`, `notes/language-rules.md`, the test model and the merged block plans that
    came from older releases are recounted or dated; popups of a few words checked on the emulator with the new files
    (it a and del, de im, ca del, ms baca, a Japanese kyūjitai spelling, kk Алматы); the catalog's wty sizes and word
    counts refreshed once group 5a's branch is merged (merged into `languages/main` on 2026-10-22); the archive's wty
    files refreshed. Group 5a (sv, no, da, ro, la)
    and group 4e (kk, ky) recheck their own wty data; blocks not started yet measure on the new release.
78. (2026-10-22) Crash when the accessibility service comes back (found by group 5a on the emulator, Slovak run): the
    app crashed with `BadTokenException` in `OverlayController.attachWindows`, called from `applySettings`, after the
    service was turned off and on through `settings put`. A plain bug, fixed without questions in a session of its own;
    check whether it plays a part in the ColorOS report (`notes/oem-background-limits.md`), where the system turns the
    service off and on. Fixed on `claude/jolly-cray-bc8ce9` (2026-10-22): the system takes the window token at unbind
    before the service hears of it; windows the system no longer takes are skipped and logged
    (`OverlayWindows.add`), the overlay is added whole or not at all. Reproduced and checked with jdb (UPD8). No part
    in the ColorOS logs (no crash there).
    The same session changes the "service is off" notification (owner, 2026-10-22): it is posted
    3 s after a stop and only if the service has not connected again by then, instead of at once at every stop. Every
    `uiautomator dump` pauses accessibility services for about 1.5 s, so each dump gave a banner that vanished a moment
    later; a short rebind on a phone does the same. A switch turned off and a system unbind without a rebind still
    get the notification. Both checked on the emulator (UPD8, UPD9) and merged into main and languages/main on 2026-10-22.
79. (2026-10-28 evening) Marks folded in the lookup (owner): "взять все буквы, у которых ... есть какие-то вот эти
    диакритические знаки ... при распознавании считать ... как будто бы мы находим вот обычную ... букву без вот этих
    вот точек. А потом по этой букве мы пытаемся найти все слова, где фигурируют вот эти вот буквы" (take every letter
    that carries marks over a base Latin or Cyrillic letter, read it as the base letter, and find every word with any
    of that letter's marked forms), as Russian е finds ё; better than per-language lists measured on a few
    screenshots, which another font or engine misreads differently. Answers of the same evening: decision row "Marks
    folded in the lookup"; built as a step of its own after group 5c's Uzbek and before group 5d.

## Decisions

Moved from `2026-10-09-more-languages.md` on 2026-10-10 (that table is frozen); later rows at the end.

| Topic | Decision |
|---|---|
| Languages and order | 1) English, Chinese, Korean (English and Chinese before Korean); 2) Spanish, French, German, Italian, Portuguese, the most learned languages after them (request 11); 3) the other interface languages: Russian, Polish, Turkish, Vietnamese, and Latvian (request 44); 4) every other language Yomitan supports (owner, 2026-10-09; revised the same day with request 11) |
| Audience | Every user, not only the owner: the app goes to a community of learners of Japanese, where others learn other languages, mostly English and Chinese; later anyone with their own target language (owner, 2026-10-09) |
| Depth | Full support (deinflection, on-device recognition, fonts, audio, Anki), if it is feasible without writing our own Yomitan from scratch (owner, 2026-10-09) |
| Language profiles | Separate language profiles, switched on the home screen; dictionaries and the rest are set up within the profile (owner, 2026-10-09) |
| Settings | Global settings with per-language overrides, decided per settings page where the language matters (CSS and fonts yes, bubble size probably not); the agent proposes the design (owner, 2026-10-09) |
| Required dictionaries | A dictionary a language cannot work without (e.g. its inflection data) is offered for download when the profile is turned on, or imported from a file by the user; without it the language is not usable. Bundling only if small (owner, 2026-10-09) |
| Catalog | Dictionaries for every language with a license that allows it; those without a license or with a bad one listed separately for a decision (owner, 2026-10-09) |
| Phrases | As Yomitan does, if the engine allows it (owner, 2026-10-09) |
| On-device recognition | Bundle the models if small, else optional downloads if possible; cloud only is not wanted; look for models for the languages ML Kit lacks; right-to-left scripts are supported for their languages (owner, 2026-10-09) |
| Popup | The fields change with the language (preliminary, owner, 2026-10-09) |
| Anki | A separate deck per language without asking; each language's Anki settings start empty, with a first guess by some signs where possible, then the user sets them (owner, 2026-10-09) |
| Search and "Look up in Screenlate" | Use the active language profile (owner, 2026-10-09) |
| Branches | `main`; a branch for the multi-language version; branches per language (or per batch) off it; a separate experimental branch for the hypotheses, which may be thrown away (owner, 2026-10-09) |
| First language | English or Chinese, whichever is easier, as the experiment (owner, 2026-10-09) |
| Gloss languages | Every language: at least a dictionary into English, preferably into Russian, optionally into the other main interface languages (owner, 2026-10-09) |
| Deinflection runtime | Yomitan's language code in QuickJS inside the engine's native library, hoshidicts answering exact queries (owner, 2026-10-09) |
| Rule chains | The first chain under the word and a "+N" that opens the others, as frequencies and pitch accents (owner, 2026-10-09) |
| Form-of storage | Our own compact table written at import in the dictionary's folder (form → lemma and tag set ids); hoshidicts gets only the rows with definitions and stays unchanged (owner, 2026-10-09) |
| Second form-of step | Only from a lemma without definitions of its own (шокирующее → шокирующий → шокировать); one step otherwise (owner, 2026-10-09) |
| ML Kit Chinese and Korean | Bundled (+0.56 MB for both); anything within a few MB may be bundled, large files are not carried in the APK (owner, 2026-10-09, request 12) |
| Chinese profiles | One profile for both scripts (owner, 2026-10-09) |
| Frequency dictionaries | Built by us from wordfreq (CC BY-SA 4.0) for the supported languages, one per language (top ~100 K words), in the catalog and offered with the main dictionary when a profile is turned on, declinable; hosting is an open item (owner, 2026-10-09) |
| On-device OCR beyond ML Kit | PaddleOCR only, for Cyrillic, Arabic, Greek and Thai; the scripts it lacks (Hebrew, Georgian, Armenian, Khmer, Lao, Kannada, Syriac) use the cloud for now. Before the Russian stage Paddle is compared with Tesseract on real screenshots and the runtime (MNN or ONNX Runtime) is picked by size and speed; Tesseract alone if Paddle is not clearly better. The OCR layer is built so that Tesseract can be added later without rework (owner, 2026-10-09, requests 15 and 17). Superseded on 2026-10-16 by "On-device OCR engines" |
| OCR runtime and models | The runtime (native code) goes into the APK; the detector and the per-script recognizers are downloads checked against pinned SHA-256 hashes, fetched when a language that needs them is turned on (owner, 2026-10-09) |
| Risky dictionaries | The catalog offers dictionaries with open licenses and non-commercial ones (words.hk, the Zaliznyak meta dictionary), the latter marked with their license and removed if the app ever becomes paid, ad-supported or goes to Google Play; dictionaries without a license are not linked anywhere for now (owner, 2026-10-10; was "only as a link to the author's page in the help"); conversions of commercial dictionaries nowhere; no БКРС for now (owner, 2026-10-09, request 16) |
| Korean lookup start | The lookup starts at the start of the space-separated word, not at the aimed syllable as in Yomitan: a finger aim on a phone often lands on a neighboring syllable, and a start mid-word finds endings or unrelated words (부하 in 공부하고); shorter matches from the start still show, compound parts are found by search (agent's choice, delegated by the owner, 2026-10-09) |
| Settings layout | Language-bound settings stay on today's pages (Lookup, Popup, Anki and audio, Dictionaries) in a block named after the profile with a language switch; with one language turned on the pages look as today (owner, 2026-10-09) |
| Popup text size | Global, as today; a bigger size for one language goes into that language's CSS (owner, 2026-10-09) |
| Switching the language | The home screen's chips and a row of language chips in the bubble's hold menu, shown when more than one language is turned on; no automatic switching by app (owner, 2026-10-09) |
| Branches per language | The shared base (profiles, settings split, QuickJS, form-of table, model downloads) is built first in the multi-language branch; then a branch per language (`languages/en`, `languages/zh`, …) merged into it once the language is tested thoroughly on the emulator (owner, 2026-10-14; was "when the language works on the phone", 2026-10-09) |
| Downloads by the agent | While the owner is away the agent may download, without asking, data from the authors' official sources for building and testing: circle-flags SVGs, wordfreq word lists, Unihan, CC-CEDICT, Wiktionary zh/ko (Hugging Face), KRDICT (Lyroxide); listed in the status afterwards (owner, 2026-10-14). Extended 2026-10-16: Wiktionary (wty) dictionaries of groups 2-4, FreeDict and OpenRussian sources, the Latin fonts from their authors' repositories, PaddleOCR and Tesseract models, the MNN and ONNX Runtime libraries for the app, and the model converters (paddle2onnx, MNN's converter) in a Python virtual environment in the session scratchpad, removed afterwards; nothing is installed system-wide on the PC, Tesseract included |
| Published release notes | Rewritten only in `.github/release-notes/`; the texts of published GitHub releases change only after the owner has seen the drafts (owner, 2026-10-14) |
| Unofficial collection | If the owner keeps a separate collection of unlicensed dictionaries, the app and its repository do not link to it (owner, 2026-10-09, preliminary) |
| Audio regions | A per-language order of regions in the audio settings, used for Wiktionary clips whose file names carry the region; defaults: English US, UK, others; Portuguese Brazil; Spanish Latin America, Spain; French France (owner, 2026-10-09) |
| Turning on a language | After the target language, a download screen (request 19). Gloss languages: the interface language and English, at most two; when the interface language is English or is the target language, the user picks another one in its place. Each category lists every dictionary for the chosen gloss languages with the recommended one ticked; a mandatory category (the main dictionary; the forms dictionary for languages whose forms are only in dictionaries) needs at least one ticked, else Confirm is disabled with a hint; a mandatory item without a choice is a preselected radio button. Optional items ticked by default: the frequency dictionary, the pronunciation dictionary (except Chinese) and the on-device OCR model where the script needs one; glossaries and character dictionaries are not. Every item shows its size; the button shows the download total and, under it, the space after import; not enough free space disables it and says how much is needed; on mobile data a total over 50 MB asks first (owner, 2026-10-10) |
| Releases | No releases of new languages along the way: every language is built first, then one big release; testing in the meantime on debug builds (owner, 2026-10-10) |
| Beta label | None: with a small audience a label would never come off (owner, 2026-10-10) |
| Pronunciation dictionaries | Wiktionary IPA dictionaries are named for users: the category "Pronunciation", the item "Transcription (Wiktionary)" with an ⓘ example (`water → /ˈwɔːtər/`), never "IPA". One item per language, not per gloss language (the transcription is the same in every edition): the edition with the most entries. Ticked by default for every language except Chinese (30 MB, pinyin is already in its main dictionaries) (owner, 2026-10-10; changes the "Turning on a language" row, which had IPA unticked) |
| Scope of the big release | Groups 1–4: the release waits until every batch of group 4 is done, then a code review of all modules, then the owner's own use of the build, then the beta (row "Beta builds"). Group 4 is built in batches by script in the order 4a–4e, each its own branch and emulator checks (owner, 2026-10-16, question 30; was: groups 1–3 at least, group 4 if easy) |
| Beta builds | A separate app next to the regular one: package `com.vpr.screenlate.beta`, named "Screenlate Beta", the icon with a "BETA" ribbon at the bottom; its own dictionaries, settings, bubble and accessibility service. Tags `v1.0.0-beta.N` publish GitHub prereleases, which the regular build's update check and the README's latest-release link never see. A beta build's update check offers newer betas only and says nothing when the final release is out. Published when the owner says, after everything above (owner, 2026-10-16) |
| Version of the big release | 1.0.0; betas 1.0.0-beta.N (owner, 2026-10-16) |
| Build of groups 1-3 | Before group 4: the OCR engines plan with Paddle, then groups 1-3 merged into `languages/main` with their emulator checks, then a debug build ("Screenlate Dev", next to the regular app with its own data) for the owner's phone, sent as an APK file. Not published; the code review of all modules comes after it (owner, 2026-10-17, request 66) |
| Postponed items | The open items (bundled Japanese dictionaries, hosting) are decided when the first language or group 1 is ready, before it is merged (owner, 2026-10-10) |
| First run | Asks which language the user learns, Japanese preselected; another language goes straight to turning it on with its downloads; Japanese needs no downloads as today (owner, 2026-10-09). Changed 2026-10-15: on `languages/main` Japanese goes to its download screen too (row "Bundled Japanese dictionaries") |
| Text to speech | The last default audio source for languages other than Japanese; played only, never put into notes (owner, 2026-10-09) |
| Inflection names and tags | Translated into all 14 interface languages, a language's rule names and descriptions when that language is added; the Wiktionary tag list (~105 words) with the first group and extended per language; tags outside the list show as in the dictionary; Anki's `{conjugation}` keeps the English name (owner, 2026-10-09) |
| Chinese script | Glyph forms (SC/TC `lang` tag) and the headword spelling follow the text under the aim; a setting gives the script for words written alike in both and for typed searches, and may fix one script for everything (owner, 2026-10-09: "1 + a choice in settings"). The setting ("Characters" and the switch "All words in these characters", off by default) is on the Chinese card of the Languages screen, an exception to "Settings layout"; a word found in both spellings is one entry, headed by the chosen script with the switch on and by the text's otherwise, with the definitions of both (owner, 2026-10-15) |
| Turning a language off | A dialog with "Delete its dictionaries and models (N MB)", ticked by default; the language's settings are kept, so turning it on again restores the deck, fonts and the rest, and without the files deleted it turns on at once. The Dictionaries page lists turned-off languages that still have files, with their size and a button that deletes them all; single dictionaries are deleted as today (owner, 2026-10-10, request 22; the place on the Dictionaries page is the agent's choice, delegated) |
| Catalog threshold | A Wiktionary pair is listed when it holds at least 1,500 words with their own meaning (inflected-form rows not counted), whatever its size; the catalog script counts them and adds a pair once it passes (owner, 2026-10-16, question 27; was 0.5 MB of archive, 2026-10-10) |
| Recommended dictionaries | On the download screen the main dictionary of each chosen gloss language is ticked (both, each can be unticked while one stays). Within one gloss language and category one entry is ticked when there are several (Jitendex or JMdict): the catalog marks it, chosen by quality and coverage (Jitendex over JMdict, KRDICT over Wiktionary for Korean), size only breaking ties (owner, 2026-10-10; the rule within a gloss language delegated to the agent) |
| First language branch | English: fewer open questions than Chinese and the simpler lookup; it is also the vehicle of the shared stage (agent's choice, delegated by the owner, 2026-10-10) |
| Switching during a scan | A language chosen in the bubble's hold menu applies from the next scan, a tap on the floating bubble included; the open scan keeps its language until then or until the bubble is docked (owner, 2026-10-10; tap rescans owner, 2026-10-14) |
| Transcription file | The merged transcription dictionary of all Wiktionary editions (`dict/all/<language>`, e.g. en 3.8 MB against en-en 2.8, ru 10.4 against 6.5) instead of the edition with the most entries; still one item per language (owner, 2026-10-10; changes the "Pronunciation dictionaries" row) |
| A language confirmed on the download screen | Turned on and made active at once; until its main dictionary is installed the home screen shows the missing-dictionary card with the download progress (owner, 2026-10-10) |
| Installation status placement | The home screen shows the active language's status and a link to the queue; Search and the popup show an explanation and the same link while the language is not ready (owner, 2026-10-11, request 27) |
| Scanning before installation is ready | Do not start recognition until the mandatory components are ready; show status and a link to the queue immediately (owner, 2026-10-11, request 27) |
| Languages screen | A screen of its own, "Languages", opened from the home screen. Home keeps a compact card: the quick languages' flags (the active one marked), the active language's installation status and an "All languages" button. The screen lists turned-on languages (status, quick-switch star, turn off), turned-off languages with files (their dictionaries and OCR models with sizes, delete all, turn on) and the rest (turn on, which opens the download screen). The block of turned-off languages moves there from the Dictionaries page, which keeps a link (owner, 2026-10-13, request 34) |
| Quick languages | At most three: the active language has a star of its own, at most two more are starred on the Languages screen. Making a starred language active swaps the roles (the old active one keeps a star as an ordinary starred language); making an unstarred one active (also a language just turned on) moves the active language's star to it, and the old active one loses its star even when a place is free (owner, 2026-10-13/14) |
| Flags | Round SVG flags bundled in the app (circle-flags, MIT, with a NOTICE entry; owner, 2026-10-13: "if you want to take someone else's, fine"); with e-ink on, the language's two-letter code replaces the flag (owner, 2026-10-13) |
| Flag country | A default flag per language (English US, Spanish Spain, Portuguese Brazil, Chinese China, French France); only English (US, UK) and Portuguese (Brazil, Portugal) offer a second flag on the Languages screen, every other language has its default flag only (owner, 2026-10-13; the other countries removed 2026-10-16) |
| Downloads that stop | A download waits for the network and retries by itself up to three times, resuming where it broke off when the server allows it; dying while downloading (not importing) does not count towards the two-deaths limit. A download that continues or restarts by itself (WorkManager after a crash, a system kill or a force stop) shows no button; one that has stopped for any reason shows "Resume" (from where it broke off when possible) and "Cancel". Cancel stays available at all times (owner, 2026-10-14, request 36) |
| Transcriptions | Region tags (US, UK, …) are read at import into our own small table (hoshidicts unchanged) and shown in the language's audio region order; duplicates (the same transcription in /…/ and […]) are removed; the list shows the first five and "Show all" (owner, 2026-10-14) |
| Frequency dictionaries | Built on the phone when a language is turned on: the frequency item downloads wordfreq's word list from the author's GitHub (a pinned commit, checked by SHA-256), converts it into a Yomitan frequency dictionary (top ~100 K, as `scripts/frequency/`) and imports it; no hosting needed (owner, 2026-10-14; replaces the hosting of wordfreq dictionaries in the open items) |
| Chinese tone colors | Pinyin colored by tone (1 red, 2 orange, 3 green, 4 blue, neutral grey) behind a switch in the Chinese block of the Popup settings, off by default (owner, 2026-10-14) |
| Japanese pitch accent colors | Headword and reading colored by the first pitch accent in dictionary order, and each accent button in its own color: heiban blue, atamadaka red, nakadaka orange, odaka green, kifuku (a downstep in a verb or i-adjective) purple. A switch in the Japanese block of the Popup settings, off by default; needs a pitch accent dictionary (owner, 2026-10-14, request 41) |
| Chinese script default | Simplified; Traditional when the interface language is Traditional Chinese (owner, 2026-10-14) |
| KRDICT | Listed as CC BY-SA (a derivative of the NIKL data, ShareAlike), with its source; the recommended Korean dictionary (owner, 2026-10-14) |
| Hanja dictionary | Built by our script from Unihan (Unicode License; Korean readings and English meanings) and bundled in the APK (~0.2 MB estimated); installed when Korean is turned on (owner, 2026-10-14) |
| Typed pinyin in search | Found in any form ("nihao", "ni hao", "ni3hao3", "nǐhǎo"); our own toneless pinyin index built at import; exact tones rank higher (owner, 2026-10-14) |
| Translation button default | Off for new installs; an upgrade keeps it on for users who never touched the switch (the value is written as on during the upgrade). The Anki field switch is unchanged (owner, 2026-10-13, request 33; changes the "Translation settings" row of `2026-09-29-feedback-after-0.1.4.md`, "both on by default") |
| Bubble menu languages | Only flags (the active one ringed, the language name for TalkBack); codes on e-ink (owner, 2026-10-13) |
| Bubble glyph | The bubble shows the first letter of the language's own name, capitalized (Deutsch D, русский Р, čeština Č); languages with a script of their own keep their most typical character (Japanese あ, Korean 한, Greek Ω), and languages sharing a script other than Latin or Cyrillic also take their own name's first letter (Arabic ع after the article, Persian ف, Hindi हि, Marathi म, Chinese 中, Cantonese 粵). When two quick languages would show letters that look alike (Latin and Cyrillic look-alikes such as P and Р, and a letter with or without marks, as Č and C), both show their two-letter code instead (EN and ES). Codes of Cyrillic-script languages are written in Cyrillic (РУ, УК, БЕ, БГ, МК, МН, КК, КЫ, СР), on the bubble and in the e-ink codes that replace flags; other languages keep Latin codes (owner, 2026-10-28, replacing the hand-picked letters such as ß, Ř, Æ, Ў) |
| Language flags in more places | A language's flag goes next to its name in the language chips (dictionaries, lookup, Anki, audio and popup text pages), the OCR model list's language headings, the download screen's title, the Languages screen's cards of turned-off languages and the first-run screen's language list; the interface language list already has them. The other places stay without flags: the language headings above the chips, the dictionary pair labels (ja → en), the catalog's per-language counts, the download screen's gloss-language list, the translation dialog's target list, the problems card and the backup page. Flags follow the e-ink rule (codes instead of flags there) and TalkBack keeps reading the name. Built in the step between groups 5c and 5d, with the folded marks (owner, 2026-10-28 night, after the table of places with screenshots; 2026-10-29: "флаги ... в ту же задачу, которая между сессиями языковыми") |
| Chinese audio | Wiktionary (searched by tone-marked pinyin), Lingua Libre, text to speech (reads the characters) (owner, 2026-10-15) |
| Pinyin search order | By the frequency dictionary, as Yomitan, also for a common character under a rare reading (owner, 2026-10-15) |
| Chinese main dictionary | CC-CEDICT ticked for English glosses; Wiktionary zh–en listed unticked (owner, 2026-10-15) |
| Chinese frequency in Traditional | The wordfreq dictionary built on the phone adds each word's Traditional spellings with its rank (owner, 2026-10-15) |
| Chinese readings | Tone marks only ("xíng"); Wiktionary's numbered copy "(xing²)" is dropped; tone colors stay behind their switch (owner, 2026-10-15) |
| Chinese fonts | Noto Sans SC, Noto Serif SC, Noto Sans TC, Noto Serif TC, LXGW WenKai TC to download (owner, 2026-10-15) |
| Deleting a font | The bin next to a downloaded or added popup font asks "Delete <font>?" first, as for dictionaries (owner, 2026-10-15) |
| Phrasal verbs in the past | Yomitan's English phrasal-verb rule ("picked up" → "pick up") also accepts entries without a part of speech; checked for false matches before it ships (owner, 2026-10-15: "make sure there are no false positives") |
| Frequency chip names | A name up to 4 characters (a CJK character counts as 2) is shown whole (JPDB, JLPT, NieR); a longer one as its first character in upper case (BCCWJ → B, wordfreq zh → W, 国語辞典 → 国). A tap on the chip toggles its full name, for that chip only; "+N" shows the first chip's full name too; every lookup starts shortened (owner, 2026-10-15, request 43) |
| Test model location | `ai/test-model.md`, linked from AGENTS.md; no copy in `docs/` (owner, 2026-10-15) |
| Font lists across languages | A language's font list shows the fonts downloaded for it and every added file; a catalog font also shows for every language whose script it covers without other glyph forms: a Latin font for all Latin-script languages whose letters it has (Vietnamese needs its diacritics), a font for several scripts (Latin and Cyrillic) for all of them. Japanese, Chinese and Korean fonts stay with their language. Built with the first Latin fonts in the catalog (stage C) (owner, 2026-10-15) |
| Chinese script fields in Anki | `{expression-simplified}` and `{expression-traditional}`, converted with the character table, a character with several forms takes the one a dictionary lists with the word's reading; such fields are pre-filled with them (owner, 2026-10-15) |
| Phrasal verbs: particles only | A phrase without a part of speech matches a verb form only when an adverb particle follows the verb; prepositions do not ("closed to traffic" stays "close", not "close to", near); the interposed-object rule stays typed only (owner, 2026-10-15, after the explanation) |
| KRDICT packaging | The one with example sentences (owner, 2026-10-15) |
| Korean fonts | Noto Sans KR, Noto Serif KR, Nanum Gothic, Nanum Myeongjo (owner, 2026-10-15) |
| Korean audio | Wiktionary, Lingua Libre, text to speech (owner, 2026-10-15) |
| Hanja dictionary in the APK | Stays in the APK for good (168 KB), also once our own hosting exists; a catalog entry ticked on the download screen, can be unticked or deleted (owner, 2026-10-15) |
| HTML entities in dictionary text | The lookup page turns `&quot;` and `&apos;` left as text into quote marks in any dictionary's text (KRDICT has 496 such entries) (owner, 2026-10-15) |
| Character entry tags | Chips next to the dictionary name, a tap opens the tag's note, for every language (owner, 2026-10-15) |
| STDICT | Listed for Korean, not ticked (owner, 2026-10-15) |
| Text to speech note | The hint under the text-to-speech source ("Playback only; notes take audio from the other sources") is enough; the Sources hint stays as it is (owner, 2026-10-15) |
| Latin fonts | Literata and Inter (Latin, Cyrillic, Greek, Vietnamese), Atkinson Hyperlegible Next (Latin) and Lexend (Latin, Vietnamese), PT Serif and PT Sans (Latin, Cyrillic) to download; each shows for every language whose letters it covers (owner, 2026-10-16) |
| German compounds | When the whole word has no entry, the popup shows its parts in order: the split into the fewest dictionary words, then the one whose shortest part is longest, then the longest word first, linking -s-, -es-, -n- skipped (Haustürschlüssel → Haus, Tür, Schlüssel; Dutch stadsbestuurder → stads, bestuurder); a word with its own entry as now. Later the same for other languages with compounds (Dutch, Swedish) (owner, 2026-10-16; the split rule changed from "the longest word from the start, then the longest in the rest" on 2026-10-19 after a check on dictionary headwords, owner: "как лучше будет") |
| Dictionaries we convert | The converter for FreeDict's pairs (de, fr, it, es, pl, en, zh → ru) and OpenRussian is written now; its files are checked on the emulator (adb, a local address) and join the catalog once hosting exists (owner, 2026-10-16) |
| Character dictionaries ticked | KANJIDIC for Japanese, CC-CEDICT Hanzi for Chinese and the hanja dictionary for Korean are ticked on the download screen (an exception to "Turning on a language", where character dictionaries are not) (owner, 2026-10-16) |
| Audio order, groups 2 and 3 | By the measured coverage of ten common words: Wiktionary first for Russian and Polish, Lingua Libre first for Spanish, French, German, Italian, Portuguese, Turkish, Vietnamese and Latvian; text to speech last. A source without the word falls through to the next, so the order only picks whose clip plays and goes into notes when several have one (owner, 2026-10-16) |
| Vietnamese words of several syllables | The lookup also starts one and two syllables before the aimed one; a word that covers the aimed syllable comes first (học sinh), then the syllable's own entries (owner, 2026-10-16) |
| Audio collections | Word audio on the phone (request 52): the app imports a collection the user brings (Local Audio's export for phones) as an offline audio source; our own packs from Wiktionary and Lingua Libre per language, downloaded in the app, come once hosting exists (owner, 2026-10-16) |
| Bundled Japanese dictionaries | Downloaded when Japanese is turned on, the first run included, from the catalog like any language's; the APK no longer carries them (about 25 MB). Only on `languages/main`: `main` keeps them bundled until the big release. Installed copies stay after an upgrade (owner, 2026-10-15; settles the postponed item) |
| OCR engine choice | Paddle and Tesseract are compared on the emulator, inside a debug build, on screenshots taken there with known text, for the scripts ML Kit lacks (Cyrillic, Greek, Thai, Arabic). Measured: the share of wrong characters, the share of words read whole, and whether a tap lands in the right word box. Paddle is chosen when on the Russian screenshots it makes at least a third fewer character errors than Tesseract, is not clearly worse on any script and takes at most twice as long per screen; otherwise Tesseract. With Paddle, MNN or ONNX Runtime by the smaller addition to the APK, unless the other is at least 30% faster per screen. Speed is measured on the emulator; one run on the phone once the owner is back, and the choice changes only if the phone shows a clearly different picture (owner, 2026-10-16). Since request 60 every engine is kept, and the rule picks each language's recommended model: the fewest errors over the clean and hard screens together; another model replaces the bundled or smallest one only with at least a third fewer errors and at least 0.5 percentage points fewer; time does not count while every engine stays under the 1.6 s of ML Kit's Japanese model. Applied once the hard-screen results are in (owner, 2026-10-16, question 34) |
| Work while the owner is away | After group 2 and the OCR comparison the agent picks the engine by the row above, builds it in and does group 3 (Russian, Polish, Turkish, Vietnamese, Latvian), each language on its own branch merged after the emulator checks; group 4 is not started (owner, 2026-10-16) |
| OCR screenshots | Taken by the agent on the emulator: Wikipedia and news sites in the browser, the Android interface in the language, several fonts, light and dark themes; kept in `testdata/ocr/` (owner, 2026-10-16) |
| Bubble letters | The docked bubble's letter: Spanish Ñ, French Ç, German ß, Italian È, Portuguese Ã; Russian Я, Polish Ł, Turkish Ğ, Vietnamese Ư, Latvian Ā (owner, 2026-10-16); Dutch Ĳ, Czech Ř, Indonesian "ID", Malay "MS", Filipino Ng, Croatian Đ, Serbian Ћ, Ukrainian Ї, Belarusian Ў, Greek Ω (owner, 2026-10-18, question 52) |
| Russian stress | The headword shows the dictionary's stressed form (ко́шка) with no separate reading line; Anki's `{expression}` is the word without stress, `{reading}` the stressed form as in the dictionary (owner, 2026-10-16) |
| Diacritics of Latin-script languages | Where ML Kit's Latin model loses a language's diacritics (Vietnamese tones, Polish ł, Turkish ğ, Latvian macrons), that language uses the chosen engine's model instead, downloaded when the language is turned on, but only when it makes at least a third fewer character errors than ML Kit on that language's screenshots (owner, 2026-10-16) |
| Flag outlines | Every round flag gets a thin outline: light in the dark theme, dark in the light theme, so a stripe of the background's color (Germany's black, Poland's or Japan's white) stays visible (owner, 2026-10-16; question 14) |
| Dictionary reset | No reset button on the Dictionaries screen: each dictionary has its own delete, and the screen's settings (order, switches, sorting) are changed one by one (owner, 2026-10-16; questions 16 and 26) |
| OpenRussian source | The CSV export on the site's own GitHub (2020-09-09): the source least likely to vanish; the 2021 dump differs little (owner, 2026-10-16; question 19) |
| Russian text to speech | Given the stressed reading (ко́шка), unless the emulator check shows the voice reads the mark wrongly; then the word without marks (owner, 2026-10-16; question 25) |
| OCR comparison scope | Every engine (ML Kit, Paddle on MNN and on ONNX Runtime, Tesseract fast and best) that reads the script, on every language of groups 1-3, the group 4 scripts named in "OCR engine choice", and the later languages whose script or letters differ: Hebrew, Persian, Hindi, Bengali, Georgian, Armenian, Ukrainian, Kazakh, Serbo-Croatian in Cyrillic and in Latin, Czech, Lao, Khmer (request 59; web pages only, as they have no interface translation). Since request 60 also the app's cloud recognition, and a second set of hard screens per language: text over a photo-like background and a gradient, white subtitles with an outline, handwritten and comic fonts, small low-contrast text, manga-style vertical Japanese. The results go to the owner before an engine is built in, with the option of two engines split by language and the sizes of each combination (owner, 2026-10-16; replaces the agent's own pick in "Work while the owner is away") |
| Audio regions of German, French, Portuguese | As built: German Germany, Other regions, Austria, Switzerland; French France, Canada, Other regions; Portuguese Brazil, Portugal, Other regions (owner, 2026-10-16, questions 11-13: "the most logical, tried it, fine") |
| German compounds | A word found only as parts gets a line above the entries: "Compound: Küchentisch + Lampenschirm" (the label in every interface language). The order of a part's entries is question 29 (owner, 2026-10-16, question 15) |
| KANJIDIC editions | Every KANJIDIC edition of the chosen gloss languages is ticked when the character dictionaries are; English fills the kanji the others lack (owner, 2026-10-16, question 17) |
| FreeDict into Russian | German, French, Italian, Spanish, Polish and English into Russian are listed, unticked, once they are hosted; Chinese→Russian (74% broken senses) is left out (owner, 2026-10-16, question 18) |
| Vietnamese classifiers | Rows that only name a noun's classifier (cái → bàn, ghế, …) are left out; cái shows its own entries (owner, 2026-10-16, question 21) |
| Vietnamese Hán-Nôm rows | Kept, after the syllable's own entries, labelled "Hán-Nôm spelling" (owner, 2026-10-16, question 22) |
| Ordinals with a period | A number of any length with a period before a lowercase word is no sentence end in German, Polish, Turkish and Latvian, the languages whose ordinals take a period (agent's choice at the owner's request, 2026-10-16, question 24) |
| On-device OCR engines | ML Kit (bundled: Japanese, Chinese, Korean, Latin), Tesseract and PaddleOCR on MNN (runtimes in the APK, models downloaded) are all kept. Per language the download screen ticks the recommended model and lists the other usable ones of that language, unticked; the recognition settings switch between the downloaded ones. Models measured as unusable for a language are not offered (on the clean screens: Tesseract for Japanese, Chinese and Korean, Paddle's Latin model for Vietnamese, Tesseract for Persian) (owner, 2026-10-16, request 60; replaces "On-device OCR beyond ML Kit") |
| ML Kit Latin model | ML Kit's Latin model (about 27 KB; its recognizer is already in the Japanese AAR) reads Latin-script languages instead of the Japanese model (owner, 2026-10-16) |
| Tesseract models | tessdata_fast and tessdata_best are both offered where Tesseract is usable; fast is recommended where it is as accurate as best on the comparison, best where it is clearly better (owner, 2026-10-16) |
| Chinese recognition | ML Kit by default: one model reads Simplified and Traditional (as Paddle's main model does); Paddle's main model is offered as an alternative (owner, 2026-10-16) |
| OCR model choice placement | The per-language model choice is in the recognition settings (for now the Recognition section of the bubble settings): a row per turned-on language with its active model; the row opens the language's models, where one is chosen, others downloaded and deleted. Where settings live is reviewed later (request 61) (owner, 2026-10-16) |
| OCR model names | By engine: "ML Kit (built in)", "Tesseract, fast", "Tesseract, accurate", "PaddleOCR"; an ⓘ says in a sentence what each is good at, from our measurements and outside sources (`notes/ocr-engine-research.md`) (owner, 2026-10-17) |
| Several OCR models at setup | On the download screen every ticked model of the on-device recognition category gets a "main" switch; the recommended one is preselected when ticked, else the best measured of the ticked ones (owner, 2026-10-17) |
| Last dictionary with definitions | As built: a turned-on language's last dictionary with definitions can be neither turned off nor deleted, and a hint says why; the same rule as for a language's active OCR model (owner, 2026-10-17, request 62) |
| Paddle model hosting | The converted MNN models with their character lists, Apache-2.0 license and a note that we converted them are assets of a release of the screenlate repository that holds only model files and is kept out of the update check; uploaded only after the owner approves the file list. Tesseract's traineddata come straight from the tesseract-ocr repositories at a pinned commit (owner, 2026-10-17, question 31) |
| Installed shared OCR models | On the download screen, a model the language uses that is already installed (the Latin recognizer for a second Latin-script language, the Paddle detector) shows ticked and locked, marked as installed, and adds nothing to the total (owner, 2026-10-16, request 63) |
| Deleting OCR models | A language's active model cannot be deleted, only replaced: another one downloaded and chosen, then the old one can go; all of a language's models go when the language's files are deleted. A model another language also lists can be deleted only when no language uses it as its active model; the dialog says that it goes for those languages too. With an active model it cannot be deleted, and the row says which language uses it. The Paddle detector is not listed on its own: it goes with the last Paddle recognizer (owner, 2026-10-16, questions 32 and 33) |
| PP-OCRv6 | PP-OCRv6 small is measured beside PP-OCRv5 on the clean and hard screens; where the recommendation rule finds it better, it takes the place of v5's main and Latin recognizers. v5 stays for the scripts v6 lacks (Korean, Cyrillic, Greek, Arabic, Thai, Devanagari) (owner, 2026-10-17, request 64) |
| Latin look-alikes in Cyrillic text | In a recognized word of a Cyrillic-script language that has Cyrillic letters, Latin letters that look the same (a, c, e, o, p, x, y, A, B, C, E, H, K, M, O, P, T, X) become Cyrillic, whatever the engine. A word made only of such letters stays as read: looking it up as its Cyrillic twin as well was rejected because English words would find Russian ones (TOP as тор, COM as сом) (owner, 2026-10-17, request 65) |
| Look-alike fixes only after OCR | Every language's fixes of letters that an engine reads as look-alikes (Latin in Cyrillic or Greek words, letters a language never writes) and matching OCR misreadings that cannot be fixed one for one (Welsh ý as ŷ, Hungarian ö as ő) apply to OCR results only (`LanguageSupport.ocrWord`, from `OcrPage.inLanguage`; lookup variants need each line's source); text read from the screen's apps is taken as it is. Where people often type the text itself that way (Uzbek o and g for oʻ and gʻ, Persian ک ی in Arabic text), the matching applies to any text (owner, 2026-10-20, request 69) |
| Marks folded in the lookup | Every Latin- and Cyrillic-script language except Vietnamese (whose marks are tones) finds a word with any marks over or under its base letters: dictionaries key every headword and form row also without its marks (the `every_headword` keys Greek and Slovak have, with the letters Unicode does not decompose mapped by a table: ø ł đ ħ ı, Cyrillic ғ қ ң ө ұ ү and the like), and the lookup takes the scanned word without its marks as one more text. Results spelled as read come first, then the others, as the respelled keys sort now. A word without marks finds marked words in any text (fuzfa → fűzfa, as Greek and Slovak do), a wrong mark (fúzfa → fűzfa) is folded only in OCR text, as row "Look-alike fixes only after OCR" says. The rules (endings, prefixes) match their endings as written; the folded form rows cover inflected words the dictionary lists. Kana, Han and the other scripts are left alone. Each language gets a data check of the extra results, as other rules; a language where they swamp the popup goes back to the owner. The per-language misread variants (Hungarian ő ű, Estonian õ) give way to it; one-for-one fixes in `ocrWord` stay, as they also fix the sentence for Anki. Dictionaries get the keys at their next import or update. Built as its own step after group 5c (owner, 2026-10-28 evening, request 79: "Латиница и кириллица без вьетнамского" if it makes sense for all of them, else only where misreads were seen; "Нет знака — везде"; "Правила как написаны"; "После узбекского") |
| OCR model list | A language's models open in a dialog (scrolls when long): ML Kit where it reads the language, then the catalog's models, each with its size, a recommended mark, an ⓘ and Download or Delete. The language rows stand in the Recognition section under the engine choice, below a subheading; with Cloud only or app text only they stay visible but disabled, as the other recognition settings do. Downloading a model only adds it: it becomes the language's model when chosen in the list (owner, 2026-10-17) |
| Region order display | The audio settings show a language's region order while Wiktionary is among its audio sources or a transcription dictionary of the language is installed; the hint names both uses (recordings and transcriptions) (owner, 2026-10-18, question 28; request 55) |
| Default OCR engines | Decided by the in-app timing run (emulator, then the phone): Cloud becomes the default for new installs when the device's draft comes less than 0.5 s before the cloud result on the phone, else Both stays. With Cloud by default, turning a language on downloads no on-device model (owner, 2026-10-18, request 68, question 36 of the OCR engines plan) |
| ML Kit at setup | For a language ML Kit reads (Japanese, Chinese, Korean, the Latin-script languages), the download screen's on-device category lists ML Kit first, marked as built in with no download, and chosen as the main model. The other models are offered unticked, marked "more accurate" where the comparison shows it; a model is ticked by default only for languages ML Kit does not read. A model ticked at setup becomes the main one unless the "main" switch stays on ML Kit; one downloaded later from the model list is only added; with no choice ML Kit is used (owner, 2026-10-18, questions 38 and 40 of the OCR engines plan; changes "On-device OCR engines" and "Several OCR models at setup" for these languages) |
| OCR speed marks | Speed is shown in words only ("faster", "more accurate"), from the timing run and the comparison; no figures for the user. With Engines on Both, a hint says that the on-device model only gives the draft (owner, 2026-10-18, question 39 of the OCR engines plan) |
| Serbian and Croatian | Two languages, Croatian (hr, Latin) and Serbian (sr, Cyrillic and Latin), on the shared Serbo-Croatian dictionaries (Yomitan, Wiktionary and wordfreq have only sh), downloaded once. Each is a language of its own in the app (on-device model, voice, Anki, audio and popup settings, turned on by itself, switched from the bubble), with its own voice, sentence translation language, flag (Croatia, Serbia; no second flag) and e-ink code (HR, SR). Serbian reads both scripts in lookup, voice and cloud recognition; its on-device model is Cyrillic by default (PaddleOCR cyrillic), a Latin model can be chosen in its model list (no ML Kit: Serbian is mostly Cyrillic), and Latin look-alikes become Cyrillic by the line, whatever the model: on a line that is mostly Cyrillic, also in words of look-alikes alone, except Latin words in capitals outside a heading in capitals (OK, TOP); a Latin line keeps them (questions 65-66, 2026-10-19; was "only with a Cyrillic model"). Tesseract: hrv for Croatian, srp for Serbian. No Bosnian or Montenegrin entry for now. Croatian audio: Croatian recordings where some source has them, else the Serbian ones. With both off, one "Turned off" card holds the shared dictionaries and both languages' models, with a "Turn on" button per language (owner, 2026-10-19, questions 55-60 and 61-64; replaces "Serbo-Croatian", which had two entries "Serbo-Croatian (Latin)" and "(Cyrillic)") |
| Indonesian and Malay | Two languages, each with its own dictionaries, frequency list, voice and flag (owner, 2026-10-18, question 45) |
| Filipino name | "Filipino (Tagalog)" in the interface; the code tl inside (Yomitan, Wiktionary, Lingua Libre), fil where ML Kit, tessdata, wordfreq and the voice need it (owner, 2026-10-18, question 46) |
| Own-language dictionaries | Every source language lists its own Wiktionary edition (nl-nl, el-el, cs-cs, id-id, ms-ms, and any later one that passes the threshold) on the download screen, unticked, as if the language were among its gloss languages (owner, 2026-10-18, question 47) |
| Transcription threshold | Transcription dictionaries follow the "Catalog threshold" rule of 1,500 words, not 0.5 MiB: Indonesian, Malay and Belarusian get one; Turkish and Latvian are rechecked (owner, 2026-10-18, question 48) |
| Greek accents | A word written without any tonos also matches headwords that have one (ΣΚΥΛΟΣ, σκυλος → σκύλος; ποτέ and πότε both show for ποτε); a word with tonos matches exactly (agent's choice at the owner's request, 2026-10-18, question 49) |
| Article and pronoun tables | Left as is: a form listed as a form of the other forms of its table shows them (Greek της, Russian он) (owner, 2026-10-18, question 50) |
| Belarusian flag | The official flag from circle-flags (owner, 2026-10-18, question 51) |
| Compound part order | For a part of a German compound, entries whose headword is the part capitalized (nouns) come first: Lampe before the verb lampen; ordinary lookups keep the usual order (owner, 2026-10-18, question 29) |
| Download queue | Two tasks run at a time on two chains, their import steps one at a time (owner: "two at once, imports one by one"); a download that breaks off (no data for 20 s) goes to the end of the queue for each of its three retries (owner); a download of the same catalog entry or URL is queued once, and a stopped one still listed is continued instead (a plain fix) (2026-10-19, request 67) |
| Waiting tasks card | Two or more tasks waiting for their turn share one card ("Waiting (N): …") with "Show the list", a Cancel for each, and "Cancel all" for the waiting ones; the running tasks and a download waiting for its retry keep their own cards. A stopped download gets no Wi-Fi hint, and Jiten no copy on our hosting for now (owner, 2026-10-19, request 67) |
| Partial downloads | A download's archive is kept in the app's own storage (`no_backup`), not in the cache the system clears when storage runs low (owner, 2026-10-19) |
| Waiting for a network | While no network is up, a waiting download's card says "Waiting for a network: …" (ru "Ждёт сеть: …"), a shared waiting card has the line "Waiting for a network" under its title (owner, 2026-10-19); the catalog card's stage and a waiting retry's card (under its retry text) say the same (a plain extension) |
| Furigana markers | Yomitan's names stay (`{furigana}`, `{furigana-plain}`, `{sentence-furigana}`, `{sentence-furigana-plain}`); offered only for Japanese, Chinese and Russian, whose readings go over or into the words (a field that already has them keeps getting them in any language); Chinese and Russian show what they give as a second line in the + menu; Russian puts the stress into the text, in a sentence only on words in their dictionary form (owner, 2026-10-19, the language rules review; `notes/language-rules.md`) |
| Group 5 | Every living language Yomitan describes that no group has, plus Latin and Esperanto (not Toki Pona, Ancient Greek, Old Irish, Manx, Cornish, Hawaiian), and the large languages Yomitan lacks that have Wiktionary dictionaries (sk, sl, lt, mk, az, uz, ur, ta, te, mr, sw). Six blocks by what the app needs: 5a Latin (ro, sv, da, no, sq, eo, la; sk, sl, lt, sw; eu and mt researched, not built: 5a question 4), 5b Celtic with initial mutations (cy, ga, gd, br), 5c long suffix chains (fi, et, hu; az, uz), 5d Cyrillic (bg, mn; mk), 5e other scripts (yue, kn; ta, te, mr), 5f right to left once group 4d is done (yi, arz, aii; ur). A block's research may move a language to another block (owner, 2026-10-19; the agent placed the languages Yomitan lacks, after the semicolons) |
| Group 5 research | Each block is researched in its own session, research and the owner's questions first, as for group 4; 5a-5e start at once, 5f after group 4d's research (owner, 2026-10-19) |
| Group 5 release | Provisionally after the big release, block by block; decided when group 4 is done (owner, 2026-10-19) |

## Open items (postponed by the owner)

Decided when the first language or group 1 is ready, before it is merged into the multi-language branch.

- Hosting the files we build or mirror (dictionaries without releases such as OpenRussian and FreeDict conversions;
  wordfreq frequency dictionaries are built on the phone since 2026-10-14; Paddle's OCR models are decided, row
  "Paddle model hosting"): likely a public repository of our own; Hugging Face to be
  looked at. Postponed with no date (owner, 2026-10-13, request 30): remind the owner whenever they ask what is
  left open. Until then test builds download them from wherever is handy (for the emulator, `adb push` and a local
  URL are enough; for the owner's phone, ask before putting anything on a public host).

- Where the OCR engine choice is offered (Both for a draft and offline scans, the device alone for no cloud): it
  becomes a separate setting, discussed later (owner, 2026-10-18, question 37 of the OCR engines plan).

## Backlog

- Own copies of the dictionaries (owner, 2026-10-16, a separate task): download every dictionary the catalog links to,
  and those we plan to host, and keep them outside the repository (on the owner's computer) in case their sources
  remove them; served from our hosting once it exists. Done on 2026-10-19 (`notes/dictionary-archive.md`): the
  catalog's files, the next languages' Wiktionary pairs, the sources we convert with our conversions and the OCR
  models, 1,083 files (2.4 GiB) checked against their hosts' hashes where published, in a git-ignored folder with its
  refresh script.
- Word audio on the phone (request 52), optional: decision row "Audio collections" (the user's own collection
  imported; our packs once hosting exists).
- Download queue (request 67): built on 2026-10-19, rows "Download queue" and "Waiting tasks card". Jiten fails on
  the phone on mobile data: its host (Hetzner) gives no range support, so every try starts over, and the mobile
  network freezes the connection (`ai/notes/audio-sources.md`). The cards multiplied because the entry points queued a
  download without looking for an unfinished task of the same entry while the stalled one held the single chain.
- Right-to-left interface languages (owner, 2026-10-19, question 70 of `2026-10-19-group-4d.md`): Arabic, Persian
  and Hebrew interface translations with mirrored screens, a separate task after group 5's research (and maybe its
  build) and group 4's build. The strings alone go earlier, in the cloud session of 2026-10-29 (owner, 2026-10-28);
  the mirrored screens are checked on the emulator later, and the strings wait on their branch until then.
- Bubble menu in the dock (owner, 2026-10-22, request 75): holding the docked bubble opens the same menu as holding the
  floating one: the quick-language flags and opening the app. Items that need a scan (copy the paragraph, copy all, and
  any added later) are not shown in the dock, since nothing is scanned there. Now only the floating bubble starts the
  hold timer (`OverlayController.BubbleTouchListener`). Built on 2026-10-28 night with the settings rework
  (`2026-10-28-settings-rework.md`, decision "Docked bubble menu").
- Initials in sentence ends (group 5a, 2026-10-22): a single capital letter with a period ends a sentence in every
  language, so names split (Lithuanian "A. Smetona": in its Leipzig corpus 9,048 such initials stand before a capital
  and 105 at a sentence end). Decided with group 4e's question 19 (owner, 2026-10-22): an initial before a capitalized
  word holds the sentence in every spaced language; built by group 4e.
- Smaller language bundles (2026-10-22): the rules of `own-transforms.js` reach every bundle that imports from it
  (Romanian and Latin rules in the Norwegian, Kazakh, Kyrgyz, Bengali, Persian and Hebrew bundles, about 2.4 KB each),
  because esbuild keeps the rule helpers' calls. `/* @__PURE__ */` on the helpers or one module per language would
  drop them; nothing breaks meanwhile.
- Language flags in more places (owner, 2026-10-28): decided on 2026-10-28 night from the table of places with
  screenshots; see row "Language flags in more places". Built in the step between groups 5c and 5d.
- Bubble glyph by the language's own name (owner, 2026-10-28): the bubble now shows a distinctive letter of each
  language (`LanguageSupport.glyph`: ß, Ř, Æ, Ў). For languages whose script others share (Latin, Cyrillic and the
  like) it would show the first letter of the language's name in that language (Deutsch → D, русский → Р); languages
  with their own script keep their most common or most typical character (Japanese あ, Chinese a character most tied
  to China, as 中 now), and the same wherever it applies. Decided on 2026-10-28 after a table of the letters
  (row "Bubble glyph"); to build.
- Popup delay check (owner, 2026-10-28): on the phone the popup comes noticeably late after the aim reaches a word,
  and it does not look like the debounce but like slowness. Measure the path from the aim's stop to the drawn popup
  (hit test, lookup, page render, window show) on the phone and the emulator from the app's log timings, compare with
  earlier releases, and remove what is slow.
- More interface languages (owner, 2026-10-28): choose, among the languages the app reads, the most widely used ones
  that would be worth translating the interface into later (the interface has 14 locales now). Not worked out yet.
- Interface translation check (owner, 2026-10-28): a separate pass over every interface locale for correct and
  natural translations (terms, grammar, consistency between screens). Not worked out yet.
- PaddleOCR lines (owner, 2026-10-28 evening, question 72 (1); moved here from a proposed session at the owner's
  word): PaddleOCR gives one line per paragraph, and the v5 detector splits Russian lines into overlapping boxes
  (38 paragraphs on the Russian page). Fix the line split and the overlapping boxes, measured on the OCR screenshots.
- Converter data (owner, 2026-10-28 evening, question 72 (3)): in our converters, move FreeDict de-ru's long sense
  labels into the gloss, drop it-ru's "( approfondimento)", handle OpenRussian's "gapirish" through an exception
  list, and give Wiktionary vi-en's "Central" tag a clear name in our tag labels; rebuild the affected dictionaries.
- Empty AnkiDroid browser (owner, 2026-10-28 evening, question 72 (4)): the dictionary button on a duplicate opened
  an empty AnkiDroid browser. Debug first whether the app sends a wrong query or AnkiDroid has not added the note
  yet; fix after that.
- Archive catch-up (owner, 2026-10-28 evening, questions 74 and 76): fix the archive's future list and fetch the
  25 catalog entries it lacks; then delete the copies under future/ that the catalog copies duplicate, once they
  pass the hash check, after showing the owner the list.

## Questions for the owner

The questions collected while the owner was away (2026-10-14) were answered on 2026-10-15; the answers
are decision rows (Chinese audio, pinyin search order, Chinese main dictionary, frequency in Traditional, Chinese
script, readings, fonts, deleting a font, phrasal verbs, test model location). Dragging the bubble is smooth on the
phone in the release, and the drag and drawing code has not changed since v0.2.3, so nothing to do.

Collected after that (autonomous work, 2026-10-15); answered on 2026-10-15, see the decision rows from "Font lists
across languages" on (the HTML entities and the font rule differ from the option that was in use):

1. Fonts downloaded for another language on a language's font list. With Chinese fonts in the catalog, the Chinese
   list also showed M PLUS 1p, a Japanese font (Japanese glyph forms, no Simplified-only characters), and the
   Japanese list would show Noto Sans TC. Options: (a, recommended, in use) a language lists the fonts downloaded
   for it and every added file; another language's font shows only while it is the chosen one; (b) every installed
   font on every list, as before; (c) every installed font, with the other language's ones marked "Downloaded for
   Japanese".
2. Simplified and Traditional fields in Chinese note types. Many Chinese note types have a field for each script
   (Simplified, Traditional), and Yomitan has no marker for either. Options: (a, recommended, in use) two markers,
   `{expression-simplified}` and `{expression-traditional}`, the word converted with the character table, where a
   character has several forms the one a dictionary lists with the word's reading (头发 → 頭髮, not 頭發); such
   fields are pre-filled with them, the first field too; (b) `{expression}` in both fields, the user fixes one by
   hand; (c) leave such fields empty.
3. English phrasal verbs in the past with dictionaries that give phrases no part of speech (decision row "Phrasal
   verbs in the past"). The false-match check over the 20,562 English example sentences of wty-en-ru found that a
   preposition after the verb brings wrong phrases ("closed to traffic" → "close to", near; "saved for" → "save for",
   except) and that the interposed-object rule ("picked the book up") would bring "time is running out" → "time
   out". Options: (a, recommended, in use) a phrase without a part of speech matches a verb form only when the word
   after the verb is an adverb particle (up, out, off, back and the like), and the interposed-object rule keeps
   matching only phrases marked as verbs: 291 of wty-en-ru's 309 verbal phrases are reached, the 18 with a
   preposition only are not (look for, deal with, apply to, …); the one wrong label seen is "ways out" → "way out"
   marked as third person; (b) prepositions too: "look for" and "deal with" as well, with the false matches above;
   (c) as Yomitan: such phrases never match a verb form.

Collected during the Korean work (autonomous, 2026-10-15); answered on 2026-10-15 (decision rows):

4. KRDICT comes in two packagings per gloss language, with and without example sentences (KO-EN 16 MB with
   examples). Both have the same title ("KRDICT EN"), so installing one replaces the other. Options: (a, recommended,
   in use) list the one with examples; (b) list the one without examples (smaller, faster to import); (c) both, the
   one installed last stays.
5. Popup fonts for Korean. Options: (a, recommended, in use) Noto Sans KR and Noto Serif KR (every weight, Korean
   hanja forms), Nanum Gothic and Nanum Myeongjo (regular and bold, small, common on Korean websites); (b) Noto only;
   (c) more, e.g. a handwritten font (Nanum Pen Script) or Gowun Dodum.
6. Korean audio sources by default: (a, recommended, in use) Wiktionary, Lingua Libre, text to speech (Wiktionary
   has recordings of more common words); (b) Lingua Libre first.
7. The hanja dictionary (decision row "Hanja dictionary"): it is a catalog entry whose archive comes with the app,
   ticked on the download screen ("Comes with the app") and installed without a download; it can be unticked or
   deleted like any dictionary, and the download total leaves it out. Options: (a, recommended, in use) as described;
   (b) always installed with Korean, without a box on the download screen, like the bundled Japanese dictionaries.
8. 496 of KRDICT's 85,000 ko–en entries show `&quot;` and `&apos;` as text: the packaging left HTML entities in
   plain text. Yomitan shows them the same way. Options: (a, recommended, in use) leave them, the fix belongs to the
   packaging; (b) turn these two entities into quote marks in any dictionary's text on the lookup page.
9. Character entries showed their dictionary's tags nowhere: KANJIDIC tags kanji jouyou or jinmeiyou, the hanja
   dictionary 교육용 (basic hanja for education) or 인명용 (names). Options: (a, recommended, in use) show them as
   chips next to the dictionary name, a tap opens the tag's note, for every language, as Yomitan does (Japanese
   kanji entries get them too); (b) Korean only; (c) not at all.
10. STDICT, the monolingual standard dictionary (58 MB, CC BY-SA 2.0 KR like KRDICT): (a, recommended, in use)
    listed for Korean, not ticked; (b) not listed.

Collected during group 2 (autonomous, 2026-10-16); the option marked "in use" is built and stays until answered:

11. German audio regions. Wiktionary's recordings from Germany carry no country code in their file names
    (`De-Wasser.ogg`), Austrian ones do (`De-at-…`), so recordings from Germany play as "Other regions" and the
    Germany item only orders transcriptions. The audio settings list Germany, Other regions, Austria, Switzerland.
    Options: (a, in use) as listed; (b) without the Germany item: Other regions, Austria, Switzerland; (c) Germany,
    Austria, Switzerland, Other regions.
12. French audio regions. The decision row "Audio regions" names France only; Canada was added because Wiktionary
    titles Quebec recordings `Fr-ca-…`. Options: (a, in use) France, Canada, Other regions; (b) France, Other regions.
13. Portuguese audio regions. The row names Brazil only; Portugal was added (recordings titled `Pt-pt-…`, and the
    Portuguese flag is offered). Options: (a, in use) Brazil, Portugal, Other regions; (b) Brazil, Other regions.
14. The German flag's black stripe barely shows on the dark theme's cards (Languages screen, Home). Options: (a, in
    use) as it is; (b) a thin light outline around every round flag in the dark theme.
15. German compounds without an entry: the popup shows the entries of each part one after another (Küchentisch, then
    Lampenschirm) and marks the whole word in the recognized text; nothing says the word was split. A part brings
    every entry that spells it, as any lookup does (Arbeit also brings the imperative of arbeiten). Options: (a, in
    use) as it is; (b) a line above the entries: "Küchentisch + Lampenschirm".
16. Dictionary reset (⟲ on the Dictionaries screen) once the APK carries no dictionaries. Today it deletes every
    dictionary and reinstalls the bundled Japanese set. On `languages/main` there is no such set, so after a reset
    every turned-on language is left without dictionaries, and the start screen only says they are missing: a
    language that is already on has no button that opens its download screen (turning it off and on again does).
    Options: (a, recommended) the reset deletes; each turned-on language without its mandatory dictionaries gets a
    start-screen card with a button that opens its download screen (it helps whenever they are gone, not only after
    a reset); (b) the reset also queues the ticked-by-default dictionaries of every turned-on language, with the
    download size in its confirmation; (c) the reset deletes and opens the active language's download screen, the
    other languages as in (a). Postponed, as the answer decides new UI: until then the reset on `languages/main`
    works and reads as on `main` (it reinstalls a bundled set that is empty there).
17. KANJIDIC in other gloss languages. Besides the English KANJIDIC, the catalog has French, Spanish and Portuguese
    editions (about 1 MB each); with the character dictionaries ticked, a French interface ticks the French and the
    English one, and a kanji then shows two entries. Options: (a, in use) every KANJIDIC edition of the chosen gloss
    languages; (b) the English one only; (c) the interface language's edition where there is one, else English.
18. FreeDict into Russian, quality. The converted pairs (WikDict, from Wiktionary translation tables) are mostly
    clean, but in Chinese→Russian 74% of the senses are lists of single Russian words in alphabetical order that mix
    unrelated meanings, and almost every entry is tagged as a noun; English→Russian has 10% such senses (the other
    pairs about 1%). Chinese→Russian otherwise has only the Wiktionary glossary (7.6 MB); English→Russian has the
    Wiktionary dictionary (4.8 MB). Options: (a, recommended) list German, French, Italian, Spanish, Polish and English
    into Russian, unticked, and leave Chinese→Russian out; (b) list all seven, Chinese→Russian with a description that
    says the translations are collected automatically; (c) only the five without English and Chinese. Waits for
    hosting anyway.
19. OpenRussian's data age. The converter reads the site's own CSV export on GitHub, dated 2020-09-09; the official
    SQL dump link is gone (404), and the online database the site points at did not load. A copy of the SQL dump from
    2021-04-20 sits in openrussian-cli, a third-party console dictionary (same license). Options: (a, recommended, in
    use) the 2020 export from the site's GitHub; (b) the 2021 dump from openrussian-cli (the converter would read
    MySQL dumps); (c) you export the current tables from the online database (TogetherDB) yourself, and the converter
    follows its columns. Also built, provisionally: Russian→German beside Russian→English (the CSVs have both), and
    a verb's aspect partner as its last definition ("perfective: сказать").

Collected during group 3 (autonomous, 2026-10-16); the option marked "in use" is built and stays until answered:

20. Latvian into Russian. Wiktionary's lv→ru dictionary is 520,903 bytes, 3 KB under the catalog's 0.5 MB threshold
    (decision row "Catalog threshold"), so Latvian has no dictionary into Russian in the catalog. Options: (a, in
    use) leave it out by the rule; (b) list it, as an exception for a language with nothing else into Russian.
21. Vietnamese classifiers. Wiktionary lists "cái" as the classifier form of hundreds of nouns (cái → bàn, ghế,
    …), so looking up cái brought every one of them. Options: (a, in use) such rows are left out and cái shows its
    own entries; (b) they stay, after cái's own entries.
22. Vietnamese Hán-Nôm rows. Wiktionary links many syllables to the characters that wrote them in chữ Nôm (xa → 車,
    佘, 奢; học → 𭓕); each shows as the character's entry labelled "Hán-Nôm spelling". Options: (a, in use) keep
    them; (b) leave them out like the classifiers; (c) a lookup setting, off by default.
23. Vietnamese on the device. Paddle's Latin model has no letters with tone marks, so if Paddle wins the comparison,
    Vietnamese stays with ML Kit's Latin reading (the decision row on diacritics compares the error rates) or would
    need Tesseract's Vietnamese model beside Paddle (6-9 MB more native code per ABI). Postponed until the
    comparison has run; options: (a) ML Kit and the cloud; (b) Tesseract for Vietnamese as a download.
24. Ordinals with a period. For Latvian years ("1918. gada") a number of three or more digits before a lowercase
    word now counts as an ordinal too. Options: (a, in use) in every language whose ordinals take a period (German,
    Polish, Turkish, Latvian); (b) in Latvian only.
25. Russian text to speech is given the stressed reading (ко́шка), which tells the voice where the stress falls in
    words such as замок; to be checked on the emulator that the voice does not read the mark. Options: (a, in use)
    the stressed reading; (b) the word without stress marks.

Answered on 2026-10-16: 14 (row "Flag outlines"), 16 (row "Dictionary reset"), 19 (row "OpenRussian source"),
20 (listed, row "Catalog threshold"), 23 (the comparison starts now), 25 (row "Russian text to speech"). The
branch `experiment/languages` is left to the agent. The owner asked for more context on 11, 12, 13, 15, 17, 18, 21,
22 and 24 (given in chat on 2026-10-16, with screenshots for the Vietnamese ones); 11 to 13 gain an option: (d)
recordings without a region code count as the language's main country (Germany, France, Brazil), and "Other
regions" goes. Raised by the answers:

26. What the reset on the Dictionaries screen resets. Its settings are the order of the dictionaries, their
    switches and the dictionary results are sorted by. Options: (a, recommended) all three: catalog dictionaries in
    the order the download screen gives them, added ones after them in the order they were installed, every
    dictionary on, the sorting back to the default; (b) no reset button at all.
27. The catalog threshold in words. Counted on samples: Latvian→Russian 9,284 words with their own meaning (520 KB),
    Portuguese→Spanish 4,669 (358 KB), Korean→Polish 4,150 (267 KB), Japanese→Italian 2,844 (170 KB),
    Vietnamese→Russian 1,868 (102 KB), Turkish→Japanese 979 (57 KB), Latvian→Spanish 169 (16 KB); glossaries are
    denser (Polish→Russian glossary 29,139 words in 495 KB, Turkish→Russian 5,120), transcriptions too (Turkish
    16,425 in 303 KB, Latvian 1,700 in 33 KB). Of the 346 pairs among the 14 languages under 0.5 MB, about 210
    hold 1,000 words or more and about 120 hold 3,000 or more (estimated from sizes). Options: (a, recommended) list
    from 1,000 words with their own meaning; (b) from 3,000; (c) keep 0.5 MB, with lv→ru as the only exception.

Question 30 answered on 2026-10-16: (b), every batch in the big release (row "Scope of the big release"). Questions 28
and 29 answered on 2026-10-18: (a) both (rows "Region order display" and "Compound part order").

Answered on 2026-10-16 (second round), decision rows: 11-13 (as built), 15 (the line, with the word "Compound"), 17
(every edition), 18 (without Chinese→Russian), 21 (left out), 22 (kept), 24 (left to the agent: all four languages),
26 (no button), 27 (from 1,500 words). Raised by the answers:

28. The region order in the audio settings (request 55). Only the Wiktionary source uses it for recordings, but the
    popup also orders transcriptions by it (English US before UK), and its hint names only Wiktionary recordings.
    Options: (a, recommended) show it while Wiktionary is among the language's audio sources or a transcription
    dictionary of the language is installed, with a hint that names both; (b) only while Wiktionary is a source,
    transcriptions keep the last order; (c) always, with the hint that names both.
29. Parts of a German compound are looked up like any word, so a part brings every entry spelled like it, in the
    usual order: "lampe" brings the Swiss verb lampen ("to hang down", whose form it is) above the noun Lampe, since
    the part is looked up in lowercase and the noun is capitalized. Options: (a, recommended) for a part of a
    compound, entries whose headword is the part capitalized (nouns) come first; (b) the usual order.
30. Group 4 in batches by script (request 57), each batch its own branch and emulator checks:
    4a like group 3 (Latin, Cyrillic, Greek, spaces): Dutch, Czech, Indonesian with Malay, Filipino (Tagalog),
    Serbo-Croatian, Ukrainian, Belarusian, Greek; 4b new scripts written left to right with spaces: Hindi,
    Georgian, Armenian, Bengali (fonts, OCR per script: ML Kit and Paddle read Devanagari, the others only
    Tesseract or the cloud); 4c no spaces between words: Thai (later Lao, Khmer), the word found as the longest
    match from the tap as in Japanese; 4d right to left: Arabic (later Persian, Hebrew), hit testing, highlight and
    popup layout mirrored; 4e Kazakh and Kyrgyz (Cyrillic, no Yomitan support, found like Turkish). Options: (a,
    recommended) batches in the order 4a, 4b, 4c, 4d, 4e; the big release ships groups 1-3, each batch comes in a
    later release (changes the row "Scope of the big release"); (b) the same batches, all in the big release; (c)
    group 4 as one stage.
31. Answered on 2026-10-17: (a), row "Paddle model hosting". Hosting the converted Paddle models (about 100 MB: the detector and about ten recognizers). Paddle publishes
    ONNX; MNN (2.1 MB in the APK against 19.4 MB for ONNX Runtime) reads only its own format, so the models are
    converted once on a computer and must be downloaded from somewhere. Options: (a, recommended) a release of
    our repository that holds only the model files, kept out of the update check; (b) a separate public repository
    for every file we build (models, FreeDict and OpenRussian conversions); (c) ONNX Runtime with the official
    files from Hugging Face, no hosting but 19 MB more in the APK and 5-20% slower; (d) still postponed: phone
    builds offer no Paddle models until it is decided.
32. Answered on 2026-10-16 (not allowed; row "Deleting OCR models"). Deleting the last downloaded model of a language. Today a language without an on-device model goes to the
    cloud alone with Engines on Both (no draft, nothing offline), and a scan fails with a hint with Engines on
    Device; home lists the missing model. Options: (a, recommended) allowed after a dialog that says so; home
    lists it with a download button, as for missing dictionaries; (b) allowed, the same dialog, home does not
    list it; (c) not allowed: the last model is only replaced or deleted with the language.
33. Answered on 2026-10-16 (only while no language uses it as its active model; row "Deleting OCR models"). Deleting a model several languages use (Paddle's Latin recognizer for Polish, Turkish and Czech; the Paddle
    detector for every Paddle language). Options: (a, recommended) delete removes it from that language; the file
    stays while another language uses it, and the dialog says which; (b) delete removes the file for every
    language, and the dialog lists the languages that lose it.
34. Answered on 2026-10-16: (a), row "OCR engine choice". The rule for the recommended model. The plan's rule (at least a third fewer errors, at most twice the time)
    would, on the clean screens, pick Tesseract for English (0.0% against ML Kit's 0.1%) and rule out Paddle for
    Russian and Greek by time alone (1.2 s against 0.5 s). Options: (a, recommended) fewest errors over the clean
    and hard screens; another model replaces the bundled or smallest one only with at least a third fewer errors
    and at least 0.5 percentage points fewer; time does not count while every engine stays under the 1.6 s of
    ML Kit's Japanese model; (b) the plan's rule with the 0.5-point floor added (time counts).

Group 4a, from the research of 2026-10-18 (`notes/languages.md`, "Group 4a research"); answered on 2026-10-18, see the
decision rows from "Serbo-Croatian" on:

41. Answered: (a). Serbo-Croatian as one language with both scripts, as Yomitan, Wiktionary and wordfreq treat it;
    or Serbian, Croatian and Bosnian as three languages sharing the sh dictionaries; or one language, Latin only.
42. Answered: (b). Serbo-Croatian script for recognition (no model reads both): one model list with the Latin models
    and the Cyrillic one, picked once; or two entries, "Serbo-Croatian (Latin)" and "(Cyrillic)", sharing the
    dictionaries and switched from the bubble like languages.
43. Answered: by script (asked again after "Serbia and Croatia"). Serbo-Croatian flag: Serbia by default with
    Croatia, Bosnia and Herzegovina and Montenegro offered; the code "SH" in a badge; Serbia only. With two entries:
    the Croatian flag on the Latin entry and the Serbian one on the Cyrillic entry; or Serbia on both, Croatia offered.
44. Answered: (a). Serbo-Croatian voice by the text's script (Croatian for Latin, Serbian for Cyrillic); or one voice
    chosen in the audio settings.
45. Answered: two languages ("whichever is more logical, if they are separate"). Indonesian and Malay as two
    languages (own dictionaries, frequency lists, voices, flags) or one entry with both dictionaries.
46. Answered: (c). The name of tl: "Filipino" (Android, ML Kit, tessdata, wordfreq, the voice), "Tagalog" (Yomitan,
    Wiktionary) or "Filipino (Tagalog)".
47. Answered: (a), "for every language, the language itself for its dictionaries". Own-language Wiktionary editions
    (nl-nl 27.4 MB, el-el 37.8, cs-cs 8.4, id-id 2.1, ms-ms 0.8), listed only for gloss languages today: listed,
    unticked, for every source language that has one; or left out.
48. Answered: (a). Transcriptions under 0.5 MiB (id 25.4k words, ms 9.0k, be 14.3k): the 1,500-word rule as for
    pairs; or 0.5 MiB kept for transcriptions.
49. Answered: (a), the agent's choice at the owner's request. Greek text without tonos (capitals, careless typing):
    a word without any tonos also matches headwords with one, a word with tonos only exactly; every word matched
    without tonos; or exact accents only.
50. Answered: (a). Article and pronoun tables (Greek της lists 13 article forms, as Russian он lists вы, его): left as
    is; or form links between two forms that list each other dropped.
51. Answered: (a). Belarusian flag: the official flag from circle-flags; the white-red-white flag drawn by us; or the
    code "BE".
52. Answered: (a). Bubble letters: nl Ĳ, cs Ř, id "ID", ms "MS", tl Ng, sh Đ (Latin) and Ћ (Cyrillic), uk Ї, be Ў,
    el Ω; or IJ, Ů, Ñ, Ґ, Λ.
53. Answered: the agent's choice at the owner's request ("I don't know these languages well"). What the two
    Serbo-Croatian entries share: only the dictionaries, downloaded once; the rest (Anki, audio, popup settings, OCR
    model) their own as for separate languages, each turned on by itself. Or everything but the OCR model and the
    voice, one language whose script switches like a language.
54. Answered: (a). The entries' names: "Serbo-Croatian (Latin)" and "Serbo-Croatian (Cyrillic)"; or "Croatian" and
    "Serbian" (Serbian Latin text would then go through "Croatian").

Collected while building Serbo-Croatian (2026-10-19; the owner left the work running, so each is built with the first
option and changes cheaply). Answered on 2026-10-19: 55 "HR" and "SR"; 56 (a); 57 replaced by the owner's request to
make Serbian and Croatian separate languages (questions 61-64); 58 (a); 59 by question 63; 60 (a):

55. Codes in e-ink mode (letters instead of flags): "SH" for the Latin entry and "СХ" for the Cyrillic one; or "SH"
    for both; or "HR" and "SR".
56. Tesseract models listed: Croatian (hrv) for the Latin entry and Serbian (srp) for the Cyrillic one; or also
    Serbian Latin (srp_latn) and Bosnian (bos) for the Latin entry. PaddleOCR v6 stays the recommended one for Latin
    (measured best), PaddleOCR cyrillic for Cyrillic.
57. Sentence translation from: Croatian for the Latin entry and Serbian (Cyrillic) for the Cyrillic one, as the
    voices; or Serbian (Latin) for the Latin entry; or Bosnian. The services translate the three alike.
58. Flags: Croatia for the Latin entry and Serbia for the Cyrillic one, with no "Flag" button; or a "Flag" button on
    the Latin entry with Croatia, Serbia, Bosnia and Herzegovina and Montenegro (as for English).
59. Lingua Libre: the Serbian recordings (srp, 93 files) for both entries; Croatian (hrv) has one file and Bosnian
    none. Or no Lingua Libre for Serbo-Croatian (Wiktionary first, then the voice).
60. "Turned off" with both entries off: one card for the shared files with "Turn on Serbo-Croatian (Latin)" and "Turn
    on Serbo-Croatian (Cyrillic)"; or one card per entry, the dictionaries on the card of the entry turned off last.

Asked after the owner's request (2026-10-19: "a learner would rather learn Serbian than Serbo-Croatian"), answered
the same day:

61. Answered: (a). Serbian's Latin text: Serbian reads both scripts (lookup, voice, cloud recognition; one on-device
    model, Cyrillic by default, Latin selectable); or three entries, Croatian, Serbian (Cyrillic) and Serbian (Latin);
    or Serbian in Cyrillic only, its Latin text read through Croatian.
62. Answered: (a). Bosnian and Montenegrin: not now (their text reads through Croatian or Serbian); or Bosnian as a
    third language on the shared dictionaries.
63. Answered: other. Croatian recordings: look for a source with Croatian recordings first; if there is none, give
    Croatian the Serbian recordings (Wiktionary, Lingua Libre).
64. Answered: (a). "Turned off" with both off: one card for the shared files, a "Turn on" button per language; or one
    card per language.

Collected while building Croatian and Serbian (2026-10-19), answered the same day:

65. Answered: (a), with care for English words and other words the rule could break: Latin words in capitals stay
    on a Cyrillic line unless the line is a heading in capitals (Cyrillic letters, no lowercase ones). Serbian
    look-alikes (Latin letters that look Cyrillic: a, e, o, j, T and the like). The row says they become
    Cyrillic only with a Cyrillic model. Built instead: by the line, whatever the model, since the final text comes
    from cloud recognition, which reads both scripts. A line whose letters are mostly Cyrillic gets them in Cyrillic,
    also in words made of them alone (Ја, је, Јој, which cloud recognition reads as Latin); a Latin line keeps them
    (the words i, a, u, o). (a, recommended) keep it per line; (b) as the row says, by the model.
66. Answered: (b), "Serbian is mostly Cyrillic". ML Kit (built in, Latin) in Serbian's model list: now the Latin
    choice there is PaddleOCR v6 (21 MB) or nothing; ML Kit is listed for Croatian. (a, recommended) list ML Kit for
    Serbian too; (b) leave it out.
67. Answered (2026-10-22): consistent, logical and without bugs (owner). The agent's reading: a download that loses its
    network at its third failure is waiting for a network, not failed: it shows "Waiting for a network" and resumes, and
    the log, the card and the task agree; a task the user or the app stopped stays stopped. To fix in the next build
    session. A download that WorkManager stops for a lost network (stop reason 7) at the moment of its third failure is
    run again by WorkManager later: a task the log called stopped resumed by itself (emulator, 2026-10-19). (a,
    recommended) Leave it and document it: the download finishes, only the log misleads. (b) Record the stop before the
    cancellation check, so the stopped task stays stopped.
68. Answered (2026-10-22): (a); the first download taking the link is fine (owner). On a slow link the second of two
    parallel downloads gets almost nothing until the first ends. (a, recommended) Leave it. (b) One download at a time
    on a metered or slow network.
69. Answered (owner, 2026-10-28 evening): (a), leave it and measure with the diagnostics build; question 73 keeps the touch as the moment (2026-10-22; the owner asked whether the first diagnostics build has it: both builds the owner got, 2a65358f
    and 9937cc41, open the connection on a touch and log each opening as `lens_preconnect` and the wait as
    `preconnect_wait_ms`): Opening the connection on a touch still leaves the handshake visible when it takes longer
    than the time from the touch to the request (1.5 s on the emulator's mobile network). (a, recommended) Leave it and
    measure on the phone with the diagnostics build. (b) Also open the connection when the bubble leaves the dock or the
    overlay starts.
70. Answered (2026-10-22): (a). The accessibility disclosure does not mention the header-only request that opens the
    connection on a touch (it carries no screenshot or text). (a, recommended) Leave it. (b) Add a sentence.
71. Answered (plain bug, owner's standing rule): (a), built. A plain bug, fixed under the owner's standing rule while the owner was away
    (2026-10-22); built with question 67's fix. Home's audio-failure card names the first turned-on language whose
    sources include the failing source: it said Korean for failures in Vietnamese, English and German lookups. (a,
    recommended) Name the language of the lookup that failed. (b) Leave it.
72. Answered (owner, 2026-10-28 evening; all but (2) moved to the backlog the same evening): fix (1) the PaddleOCR lines and (2) the installed size on the download screen; (3) after an explanation, fix the data in our converters (de-ru long labels moved into the gloss, it-ru "( approfondimento)" dropped, OpenRussian's gapirish through an exception list) and give vi's "Central" tag a clear name in our tag labels; (4) debug first whether we send something wrong or AnkiDroid has not added the note yet ("про анки надо подебажить понять в чем дело для начала, мб передаем не так что то, или он еще добавить не успел"). (2026-10-22): Findings of 2026-10-18 with no fix chosen; which to fix: (1) PaddleOCR gives one line per
    paragraph, and the v5 detector splits Russian lines into overlapping boxes (38 paragraphs on the Russian page); (2)
    the download screen shows the download size, while the installed size is larger (Chinese: 42 MB shown, about 69 MB
    installed); (3) upstream data: FreeDict de-ru's very long sense labels, it-ru's "( approfondimento)", OpenRussian's
    "gapirish", Wiktionary vi-en's "Central" tag; (4) the dictionary button on a duplicate opened an empty AnkiDroid
    browser (AnkiDroid's side).
73. Answered (owner, 2026-10-28 evening): (a), kept. The owner first asked to open the connection when the bubble leaves the dock instead of on any touch ("лучше когда из дока выходит сразу же"); told that the scan starts at that very moment, the owner kept the touch. (2026-10-22): The agent's own values in the four features built on 2026-10-19: PaddleOCR memory trimmed 10 s
    after the last recognition; the popup page created 150 ms after the capture; the connection opened by a HEAD request
    on any touch, at most every 30 s, with a 3 s wait; three new log lines; one sentence in docs/usage. (a, recommended)
    Keep them. (b) Change some (the owner says which).
74. Answered (owner, 2026-10-28 evening; moved to the backlog the same evening): (a), delete the duplicates once the catalog copies pass the hash check, showing the list first. (2026-10-22): The owner's archive keeps copies of the hi, bn, ka, hy, th, ar, kk and ky files under future/,
    made before they were in the catalog. Once their catalog copies pass the checks: (a, recommended) delete the future/
    duplicates (about 56 MiB plus 16 tessdata files); (b) keep them, marked as superseded.
75. Answered (owner, 2026-10-28 evening): (a), as each block puts them in the catalog; 5e keeps or redoes its converted models until then. (2026-10-22): Files of the researched blocks that are not in the catalog yet (5a-5e and group 6: their
    Wiktionary pairs; 5e's converted Tamil and Telugu Paddle models and tessdata tam, tel, kan, mar, now only in a
    session scratchpad). (a, recommended) The archive copies them when each block puts them in the catalog (the normal
    refresh). (b) Pre-copy them all under future/ now. (c) Not at all.
76. Answered (owner, 2026-10-28 evening; moved to the backlog the same evening): (a), fix the future list and fetch them now. (2026-10-22): The archive lacks 25 catalog entries added after 2026-10-19 (the km, fa and he Wiktionary pairs
    and transcriptions, wordfreq fa and he, tessdata khm and heb, the Thai, Kazakh and Kyrgyz frequency corpora; 65 MiB,
    all with pinned hashes). (a, recommended) Fix the script's future list (so the hi-ky files are not fetched twice)
    and fetch them now. (b) Later, with the next refresh.
77. Answered (owner, 2026-10-28 evening): 3 s and 15 s instead of 2 s and 30 s ("3 и 15"), on `main` and `languages/main`; when the network comes back the run continues at once, so the 15 s only matter when the system does not report it (2026-10-22, the download fix of question 67): a download that breaks off while the network
    goes counts as waiting for it when the network is gone within 2 s of the failure; if WorkManager does not stop the
    task then, the run waits 30 s for the network before it ends and runs again later (on the emulator WorkManager
    always stopped it first). (a, built) Keep 2 s and 30 s. (b) Other values (the owner says which).
78. Answered (owner, 2026-10-28 evening): (a), per language, as built (2026-10-22, the audio card fix of question 71): a failing audio source is listed per lookup
    language, and a lookup that finds the source working clears its failure only on that language's card. (a, built) Per
    language: a shared source (Lingua Libre, Wiktionary) that failed in Kyrgyz and Hebrew shows on both cards, and a
    working Kyrgyz lookup leaves the Hebrew card. (b) A working lookup in any language clears the source's failure on
    every card.
79. Answered (owner, 2026-10-28 evening): (a), leave it and look for a filter when the dictionary goes into the catalog (2026-10-22, night checks): the PanLex kk-en conversion lists related words among a headword's translations
    (кітап "book" also gives bookseller, bookshop), as PanLex files them under the same meaning. (a, recommended) Leave
    it until PanLex is hosted, and look for a filter then (for example translations given by only one source). (b)
    Filter them in the converter now.
80. Answered (owner, 2026-10-28 evening): (a), split it in Czech OCR lines (2026-10-22, night checks): after a Czech era (př. n. l.), ML Kit glues the next word to it ("n.l.a" for "n. l.
    a"), so the word after the era is not looked up on its own. (a, recommended) Split it in Czech OCR lines (recognized
    text only). (b) Leave it.
81. Answered (owner, 2026-10-28 evening): (a), "Look up in Screenlate Dev" for the debug build (2026-10-22, night checks): with the release and the debug build both installed, the text selection menu shows
    two identical "Look up in Screenlate" items. (a, recommended) Give the debug build its own label ("Look up in
    Screenlate Dev"). (b) Leave it.
82. Answered (owner, 2026-10-28 evening): (a), one name, "PaddleOCR v6 text detector" (2026-10-22, night checks): the waiting tasks card lists "…, PaddleOCR v6, text detector, …": the comma makes
    the detector look like a separate item. (a, recommended) Name it "PaddleOCR v6 text detector" in the list. (b) Leave
    it.
83. Answered (owner, 2026-10-28 evening): (a), hide Cancel during the last step (2026-10-22, night checks): Cancel in an import's notification during its last milliseconds (the dictionary
    being registered) installs the dictionary anyway. (a, recommended) Hide Cancel during that last step. (b) Remove the
    dictionary again after such a Cancel. (c) Leave it.
84. Plain bug (owner's standing rule), to build if not built yet (2026-10-22, night checks): Home's "Download again" card for a damaged or missing dictionary
    comes back when Home is reopened while that download waits in the queue. A plain bug: the card is hidden while its
    download is queued or running; to build in the next session that works on downloads.
85. Answered (owner, 2026-10-28 evening): (a), drop the spinner from the case (2026-10-22, night checks): case DIC9 (an interrupted dictionary reset) expects a spinner that cannot be seen:
    the deletion takes 450 ms and the screen's first frame comes 4 s after the start. (a, recommended) Drop the spinner
    from the case. (b) Show a spinner while the bundled dictionaries install after the reset.
86. Answered (owner, 2026-10-28 evening): (a), accepted (2026-10-22, night checks): the transcription tags of large dictionaries (Chinese Wiktionary: 4.4 million
    tagged transcriptions) are now kept in a mapped file with a 39-bit hash per transcription (it ran out of memory
    before and installed without tags): about 15 pairs of transcriptions share a hash and show each other's tags, and
    about 8 in a million untagged ones show another's tags. (a, recommended) Accept it. (b) Keep more bits (a larger
    file than its 36 MB).
87. Answered (owner, 2026-10-22): (b). wty's release 2026.10.22 adds form rows for alt-of senses tagged misspelling,
    nonstandard, obsolete, pronunciation-spelling and misconstruction, so a word with its own entry can show a second
    entry below it (it a → ha, anno → hanno; de ein → einen, wa → wir; ms la → lah; on words with an entry about 100 in
    de-en, 50 in it-en, 30 in ja-en), while real misspellings and old spellings on screen now lead to the word. (a) Hide
    such redirects when the matched text has an entry of its own. (b, chosen) Show them as wty and Yomitan do: below the
    word itself (the engine sorts shorter chains first), with the tag's label. (c) Hide them always.

## Branches and workflow

- `languages/main`: the multi-language branch, off `main`. (Git cannot hold a branch `languages` next to
  `languages/en`, hence the name.) `main` stays releasable for Japanese fixes and is merged into `languages/main`
  regularly, at least after every release and before every language branch starts.
- `languages/<code>`: one branch per language off `languages/main`, merged back once tested thoroughly on the emulator (owner, 2026-10-14; was: when it works
  on the phone); the next language starts from the merged state.
- `experiment/languages`: reference only (deleted on 2026-10-21 at d3dcda95bfe613ae3a53a99baf0a704719b8e9cb after its
  measurement tools and lookup harness moved to `scripts/yomitan-language/`); pieces may be reused (QuickJS submodule,
  `scripts/yomitan-language/` bundles, harness and tools). Deleted with the owner's OK after group 1 is merged.
- `ai/` commits (plans, status, notes) are also cherry-picked to `main`, so the plan on `main` is current.
- Commits push to their branch on `origin`; nothing goes to a release until the big release.
- Each language branch starts with a short list of its own questions (below, per language), asked before its work
  starts; the shared stage starts with none left (all asked on 2026-10-10).

## Stage A: the shared base (`languages/main`)

English is the vehicle: the base gets a minimal English (support class, Yomitan bundle, one test dictionary) so every
piece is exercised end to end; `languages/en` then finishes English. Japanese must behave exactly as before
(regression checks at the end of the stage).

### A1. Languages and profiles (`core:common`, `app`)

- `Language` gets a value per supported language; a language appears in the app only when its support class exists.
- `LanguageSupport`: a shared implementation for languages with spaces between words (word resolution, case and
  diacritics come from the Yomitan text processors, sentence terminators, quotes, fonts, ISO 639-3 and Wikidata ids,
  samples, OCR script), with per-language data; Japanese keeps `JapaneseSupport`. Korean and Chinese get their own
  classes in their branches.
- Profiles: the set of turned-on languages and the active one in the preferences DataStore. An upgrade turns Japanese
  on and makes it active; nothing else changes for existing users.
- Per-language preference keys: a helper that suffixes the language code; today's keys stay Japanese's, so no
  migration is needed.
- Every place fixed to Japanese today takes the active language (list in `notes/languages.md`, "What in Screenlate is
  Japanese today": `OverlayController`, search, audio defaults, Anki markers, popup appearance, translation, OCR test,
  `InstalledLanguages.SOURCE`, the "ja" first on the Dictionaries screen).
- Tests: keys per language, an upgrade from the current preferences, support data per language.

### A2. Lookup through Yomitan's language code (`dictionary:engine-hoshidicts`, `dictionary:api`)

- QuickJS-ng (MIT) as a submodule next to hoshidicts, compiled into the same native library (about +1.3 MB per ABI).
- Per-language bundles built by `scripts/yomitan-language/build.mjs` from a pinned Yomitan commit, stored as assets of
  the engine module (tens to hundreds of KB each, ~1 MB for all groups: small enough to bundle). The GPL code stays
  in the engine module; only the bundle of the active language is loaded, one QuickJS context per loaded language.
- The lookup for languages other than Japanese: candidates from Yomitan's deinflection (text processors, transforms,
  condition flags, rule chains) per substring, exact queries in hoshidicts, the part-of-speech condition filter, form-of
  following through the side table (A3), the second step only from lemmas without definitions, one result per
  headword with the longest match, Yomitan's order. Japanese keeps the native hoshidicts path.
- Search resolution per language: "word" for languages with spaces (phrases first: "look up to" → "look up" →
  "look"), "letter" for Chinese, and Korean's start at the spaced word (decision row).
- Rule chains: every chain is returned; the page shows the first and "+N" (A8).
- Inflection names: a per-language resource of rule names and descriptions in the engine module, as
  `JapaneseInflections` (`tools/generate_inflections.py` extended to write the English file of each language);
  the Wiktionary tag words (~105) as resources in `dictionary:api`, translated into the 14 locales; unknown tags shown
  as they are.
- Tests: candidates for the vehicle language against Yomitan's own test cases, the condition filter, the second
  step, sorting; instrumented tests with small test dictionaries (a wty-like one with form-of rows).

### A3. Form-of side table (`dictionary:api` imports, engine)

- At import, form-of rows (glossary of only `[lemma, [tags]]` items) go into our own table in the dictionary's
  folder: sorted distinct forms front-coded in blocks of 16, per form (lemma id, tag set id) pairs, a lemma table and
  a tag set table (format measured in the notes, "Compact form-of storage"). hoshidicts gets the other rows only and
  stays unchanged.
- Where the split runs (rewriting the term banks in Kotlin before hoshidicts imports the staged archive, or in the JNI
  bridge) is chosen by measuring the import of wty ru→en and en→en on the emulator; the target is the import time of
  today plus a little, and about 40 MB instead of 273 MB for ru→en.
- Rows that mix definitions and form-of items (none found in four dictionaries) keep their definitions in hoshidicts
  and their form-of items in the table.
- Tests: table round trip, lookups by every form, a damaged table reported as a broken dictionary (repair offers a
  reinstall as today).

### A4. Catalog, second format (`dictionary:api` catalog, `scripts/`)

- Entries get: category (main, forms, frequency, pronunciation, short translations, characters, on-device model),
  source language, gloss language, mandatory and recommended flags per language, download size and size after import,
  license and a non-commercial mark, SHA-256 where we host the file.
- Wiktionary entries are generated by a script from the Hugging Face tree API (sizes, URLs), from 0.5 MB up (decision
  row); the tree also holds MDict builds under `mdict/` (larger, e.g. en→en 114.7 MB against 107.9 MB for Yomitan),
  which the script skips; descriptions come from templates translated into the 14 locales ("Wiktionary, {language} → {gloss language}"),
  so the catalog itself does not grow by hundreds of hand-written texts. Other entries keep hand-written descriptions
  in all 14 locales.
- Gloss languages: every interface language where a dictionary exists (request 18), Japanese included.
- The Dictionaries screen shows the active language's catalog first; update checks work for the new entries.
- Tests: parsing both formats, the generator's output for a recorded tree listing.

### A5. Turning a language on and off (`app`)

- The language list (languages with support), the download screen as in the decision row (gloss languages,
  categories, ticks and radio buttons, sizes, totals, free space, mobile data over 50 MB), the queue through the
  existing import jobs, progress on the home screen.
- First run asks the language (Japanese preselected); a new language goes to its download screen.
- Home: chips of the turned-on languages and "Add a language"; turning a language off as in the decision row; the
  Dictionaries page block for turned-off languages with files.
- A language whose mandatory dictionary is missing shows a problem card, as the missing Japanese dictionaries do.
- Category names (draft, all 14 locales): Main dictionary, Word forms, Frequency, Pronunciation, Short translations,
  Characters, On-device recognition.

### A6. Settings per language (`app`, `overlay`, `core:anki`, `dictionary:api`)

- The language block on Lookup (scan length, single character entries, frequency dictionary and order), Popup (font,
  "font for all text" per language, custom CSS), Anki and audio (deck, note type, field templates, tags, duplicates,
  audio sources, audio regions) and Dictionaries (which are on and their order); global settings unchanged; one
  turned-on language looks as today.
- Dictionaries belong to a language by their source language; a dictionary whose index names a wrong or no language
  (KRDICT says "ko → ko") gets the language from the existing guess or the user's choice at import.
- Settings reset per page covers the shown language; backup holds every profile (new format version, older backups
  restore into Japanese); Yomitan settings import puts each Yomitan profile into the language of its
  `general.language`.

### A7. Switching and entry points (`overlay`, `app`)

- The bubble's hold menu gets a row of language chips when more than one language is on; the choice applies from the
  next scan; the dock glyph follows the active language.
- Search and "Look up in Screenlate" use the active language; the cloud request names it
  (`locale_context.language`).

### A8. The lookup page (`overlay` assets, `dictionary:render-yomitan`)

- `lang`, fonts and `unicode-range` from the active language; "Only for Japanese text" becomes the language's name.
- Generic markup for non-Japanese dictionaries (`definition-ja.js` stays Japanese); readings as ruby where the
  language has them (pinyin; Russian stress marks are already in readings).
- The rule chain under the word with "+N" opening the others; form-of tags translated.
- Pronunciation (Yomitan's `ipa` term meta) shown where pitch accents are, and the `{phonetic-transcriptions}` marker
  for Anki as in Yomitan.
- Page tests for each.

### A9. Audio (`core:anki` audio, `app`)

- Default sources per language: Lingua Libre and Wiktionary with the language's prefix and ids, text to speech last
  (played only); Japanese unchanged.
- Audio regions per language (decision row) order the Wiktionary clips.
- Fix: Lingua Libre titles whose part before `-term.wav` still holds a hyphen after the speaker (compounds such as
  "kitty-cat") sort last.
- Chinese searches Wiktionary by the pinyin reading (in `languages/zh`).

### A10. Anki (`core:anki`, `app`)

- Settings per language start empty; the first guess as today (note type presets, field names), deck per language.
- Markers offered per language (pitch markers only for Japanese, pronunciation where a pronunciation dictionary is
  on); `{conjugation}` keeps English names.

### A11. OCR layer and model store (`core:ocr`, `app`)

- ML Kit Chinese and Korean recognizers bundled; `OcrScript` from the language picks the recognizer.
- A slot for local engines with downloaded models (Paddle now, Tesseract possible later): an engine interface that
  takes a model set, a model store (download, pinned SHA-256, versions, deleted with the language), the model as a
  catalog category on the download screen. The engine itself comes with group 3 (A11 only builds the slot and the
  store, tested with a fake engine and a small file).
- Line direction is carried through `TextLayout` and `ReadingOrder`, so right-to-left hit testing can be added with
  the first right-to-left language without rework.

### A12. Frequency dictionaries (`scripts/`)

- A script that builds a Yomitan frequency dictionary (rank-based, top ~100 K) from wordfreq for a language, with the
  attribution wordfreq's license asks for; output hosted temporarily for tests (open item).

### A13. Text rules for spaced languages (`core:common`, `core:ocr`)

- Whole-word hit testing and highlight for spaced scripts; sentence splitting that keeps abbreviations ("Mr.",
  "e.g.", per language lists) for the sentence marker.

### A14. Docs and notices

- `docs/architecture.md` (Languages, lookup path, form-of table, catalog format, model store), `docs/development.md`
  (bundles, catalog generator, frequency builder), `docs/usage.md` (turning languages on and off); NOTICE: QuickJS-ng,
  Yomitan's language code (inside the engine module), hangul-js and kanji-processor when Korean comes, wordfreq data.

### After stage A and English (request 23)

Two runs of the code-review skill at xhigh with `--fix` over the whole change (`main..languages/main` plus
`languages/en`), each finding fixed or reported; then the owner tests the debug build.

### Done when

- English can be turned on from the download screen on the emulator, its words looked up with forms and phrases, its
  settings kept apart from Japanese; switching works from the home screen and the bubble.
- Japanese on the phone as before: lookups, Anki notes, audio, backup and restore of a backup made by the current
  release, an upgrade over the current release keeping every setting and dictionary.
- Unit, page and instrumented tests pass; docs updated.

## Stage B: group 1

Per-language checklist (every language branch):

1. Questions for the language (listed below), asked before the work starts.
2. Support class data: fonts, samples, sentence rules, OCR script, audio ids; the Yomitan bundle.
3. Catalog entries: main per gloss language, forms (if mandatory), frequency, pronunciation, short translations,
   characters, on-device model; the recommended ones per gloss language.
4. Inflection names and descriptions of the language in 14 locales; tag words the language adds.
5. A test screenshot in `testdata/ocr/` (`scripts/text-image.py`) and real screenshots from apps on the phone.
6. Checks on the emulator, then on the phone: OCR (cloud and device), lookups with forms and phrases, popup, audio,
   Anki note (emulator AnkiDroid only), timings in the log.
7. Unit and page tests; docs; merge into `languages/main`.

### English (`languages/en`)

- Catalog: wty en→en (115 MB; form-of rows go into the side table), en→ru 6.0 with short translations 4.0, en→the
  other gloss editions, the Simple English edition (to check), pronunciation en 2.8 MB, frequency.
- FreeDict eng-rus (WikDict, 62 K headwords, CC BY-SA) and Mueller 7 (GPL-2+, with the source offer) are converted
  by us and go into the catalog (request 18); their hosting waits for the open item.
- Questions: none left (the recommended dictionaries are settled by the decision row, 2026-10-10).

### Chinese (`languages/zh`)

- Catalog: CC-CEDICT terms and Hanzi (characters), wty zh→en 23 MB, zh→ja, zh→ko, zh→fr, zh→zh, short translations
  zh→ru 7.6 MB, frequency; no pronunciation item (pinyin is in the main dictionaries).
- The script setting (decision row), SC/TC glyph forms, audio by pinyin, the Chinese recognizer.
- Questions: tone colors for pinyin; FreeDict zho-rus (WikDict, 157 K headwords) converted and hosted, as the only
  real Chinese–Russian dictionary; typed pinyin in search.
- Work items (written 2026-10-14 when the branch started; the test dictionaries are in `testdata/dictionaries/zh/`):
  - Z1. `Language.CHINESE` and `ChineseSupport`: glyph 中, no word separator, the Chinese recognizer, letter
    resolution, sentence ends and quotes of Chinese text, Lingua Libre's Mandarin (`cmn`, Q9192), flags cn, tw, hk,
    sg, the phone's Noto CJK SC/TC faces, Hanzi entries below the results ("Characters of the word") and as the
    fallback where no word is found; Yomitan's `zh` bundle (radical normalization, pinyin reading normalizer).
  - Z2. Catalog: CC-CEDICT (terms, both scripts as headwords, pinyin with tone marks) and CC-CEDICT Hanzi
    (characters) from MarvNC's releases, next to the Wiktionary entries already listed.
  - Z3. Scripts: each match is Simplified, Traditional or either, by a character table built from Unihan and CC-CEDICT
    (`scripts/chinese-scripts/build.py`; replaces OpenCC, which is not on the allowed downloads);
    the page's `lang` (zh-Hans or zh-Hant) and the headword spelling follow it, the setting decides for text written
    alike in both and for typed searches, and may fix one script for everything. Wiktionary's Traditional headwords
    found through a Simplified form show the form that was read.
  - Z4. Pinyin: readings shown with tone marks ("xíng (xing²)" → "xíng"); tone colors behind the switch in the
    Popup's Chinese block (decision row).
  - Z5. Typed pinyin in search (decision row): a toneless pinyin index written at import next to the dictionary;
    tone marks or numbers in the query rank exact tones first.
  - Z6. Audio: Wiktionary clips searched by pinyin with tone marks, Lingua Libre's Mandarin, text to speech.
  - Z7. Frequency: the wordfreq list is Simplified; Traditional forms added with Z3's character table when the frequency
    dictionary is built (question "Chinese frequency in Traditional characters", option 1).
  - Z8. Popup fonts for Chinese (free fonts to download, "Only for Chinese text"), Anki first guesses by Chinese
    field names (Hanzi, Pinyin, …), the Wiktionary tags of Chinese entries (Simplified-Chinese, …) translated.
  - Z9. Strings in all 14 locales, docs, NOTICE, unit, page and instrumented tests, test model cases (N), emulator
    checks with Simplified and Traditional screenshots, vertical text included.
  - Z10. The script setting moves from the Lookup page's Chinese block to the Chinese card of the Languages screen
    (decision row "Chinese script"); backup and reset follow it.
  - Z11. Deleting a popup font asks first (decision row "Deleting a font"; all languages).
  - Z12. English phrasal verbs in the past for dictionaries without parts of speech (decision row), with a check for
    false matches over sample text.
  - Then request 43 (frequency chip names), before Korean.

### Korean (`languages/ko`)

- Catalog: KRDICT (11 gloss languages, with and without examples), STDICT, wty ko→en 7.4 MB and others, pronunciation,
  frequency (wordfreq if it has Korean, else CC100 with its license checked).
- The lookup start at the spaced word, the Korean recognizer; rule names are endings and need no translation.
- Questions: KRDICT's packaging has no license of its own (the data is CC BY-SA): list it as usable or not; Hanja
  entries (single characters) if a licensed source exists. Both answered by decision rows ("KRDICT", "Hanja
  dictionary"); the questions that came up during the work are 4-10 in "Questions for the owner".
- Done on `languages/ko` (2026-10-15):
  - `Language.KOREAN` and `KoreanSupport`: glyph 한, space-separated words, the Korean recognizer, letter resolution,
    the lookup start at the spaced word (Latin letters belong to it, digits do not), hanja of the word as entries of
    their own, the phone's Noto CJK KR faces with Korean, Windows and macOS font names; Yomitan's `ko` bundle with
    hangul-js; its only described rule (어간, stem) in 14 locales; the flag of South Korea.
  - Catalog: KRDICT in 11 gloss languages and monolingual (recommended), STDICT, Wiktionary ko-en and others (not
    ticked), transcription, wordfreq; the hanja dictionary built by `scripts/hanja/unihan_hanja.py` from Unihan
    17.0.0 (8,500 characters, 169 kB) carried as an asset and installed through an `asset:///` catalog URL (question
    7). An index that names only its target language (KRDICT says "ko") no longer sets the gloss language on import.
  - Korean Wiktionary form-of tags in 14 locales (hanja form, hangeul, honorific, regions…); tags that carry a piece
    of gloss after two spaces are cut (about 1,000 entries).
  - Popup fonts (question 5); character entries show their tags (question 9).
  - Tests: `KoreanSupportTest`, `KoreanLookupTest` (instrumented), the catalog and font catalog tests, a page test;
    test model cases OCR17, LK13, LK14, POP20, POP21, AUD7, ANK10, DIC18.
  - Known limits (`ai/notes/languages.md`): the device recognizer does not read hanja; KRDICT's hanja headwords have
    no reading, so audio finds nothing for them; some forms reach unrelated lemmas as in Yomitan (수업은 → 수없다).

### Before group 1 is merged

- The postponed items: bundled Japanese dictionaries (decided 2026-10-15: downloaded, row "Bundled Japanese
  dictionaries"); hosting (still postponed). Built 2026-10-16 on `languages/ja-downloads`: the build no longer downloads the
  archives into the APK and Japanese opens its download screen like any language; `BundledDictionaries`, the
  build-logic `DownloadAssetsTask` and the bundled-install code stay while `main` ships the archives (removed at the
  big release). The reset is question 16.
- A look at the APK size per ABI against v0.2.2 (48.4 MB arm64). Done 2026-10-15 against v0.2.3 (the same 48.4 MB):
  arm64-v8a 51.2 MB with English, Chinese and Korean (`ai/test-model.md`, NF9).

## Stage C: group 2 (es, fr, de, it, pt)

- Rule names: es 15, fr 9, de 5; it and pt take their forms from the dictionaries (the forms dictionary is mandatory).
- Catalog: wty →en main, →ru (es 1.2, fr 1.9, de 6.2, it 1.1, pt 0.8 MB) and the other gloss editions, pronunciation
  (best editions: fr-fr 19.2, de-de 12.7, es-es 10 MB, it-en 1.3, pt-en 2.8), frequency.
- Checks: accents with the ML Kit Latin model; audio regions (Brazil, Latin America, Spain, France).
- Questions per language: the recommended dictionaries; FreeDict deu-rus, fra-rus, ita-rus, spa-rus conversions.
- Fonts: the first Latin fonts in the catalog, shown for every Latin-script language they cover (decision row "Font
  lists across languages"); which fonts, asked first.

Progress (2026-10-16): Spanish, French, German, Italian and Portuguese built on their branches, checked on the
emulator (LK16-LK20, AUD8-AUD11, LNG4) and merged into `languages/main` (`12a5ecc`). The Latin fonts (POP23) and
German compounds (LK21) are on `languages/latin-fonts` (`8ac9332`), checked on the emulator, not yet merged: the
module review session holds the root checkout and the emulator. FreeDict conversions: `scripts/freedict/`
(seven pairs into Russian, 0.7-5.5 MB), not yet checked on the emulator (question 18).

## Stage D: group 3 (ru, pl, tr, vi, lv)

- Before Russian: Paddle against Tesseract on real screenshots; MNN against ONNX Runtime by size and speed; the
  chosen engine fills the A11 slot (runtime in the APK, detector and the East Slavic recognizer as downloads).
- With the first downloadable model: the Dictionaries page block of turned-off languages lists only languages with
  dictionaries, and its size and delete text leave models out, although the delete removes them; count the models in
  (the wording needs all 14 locales).
- Russian: forms from the dictionaries, the second form-of step, stress marks in readings, OpenRussian built
  (`scripts/openrussian/`, ru→en 6.7 MB and ru→de 8.2 MB with form-of rows; question 19) and hosted, the Zaliznyak
  meta dictionary with its non-commercial mark, pronunciation ru 6.1–6.5 MB.
- Polish: forms from the dictionary (pl→en 22 MB). Turkish: dotted and dotless i in case folding. Vietnamese: words of
  several syllables through word resolution; diacritics with the ML Kit Latin model (else Paddle latin).
- Latvian (request 44): Latin letters with macrons and cedillas (ā ē ī ū, ģ ķ ļ ņ, č š ž) through the ML Kit Latin
  model; forms from the dictionary unless Yomitan has rules for it; check Yomitan's language list and the Wiktionary
  editions (lv→en, lv→ru) when the group starts.
- Questions per language: the recommended dictionaries; anything the comparison leaves open.

Progress (2026-10-16): the OCR comparison is prepared on `languages/ocr-compare`, a branch not merged as it is: the
PP-OCR pipeline in `core:ocr` (to be taken over if Paddle wins), PP-OCRv5 mobile models from PaddlePaddle's own ONNX
exports, MNN 3.6.1 built from source (no x86_64 Android build is published), Tesseract4Android 4.9.0 (OpenMP) with
tessdata fast and best, ML Kit for the Latin languages, the harness in `experiments/ocr-compare`, ground truth and
scoring in `scripts/ocr-compare` (a test page with known text, Chrome through DevTools, the interface through
uiautomator). Native code per ABI (arm64-v8a / armeabi-v7a / x86_64): MNN 2.1 / 1.3 / 2.6 MB, ONNX Runtime 19.4 /
14.0 / 23.2 MB, Tesseract with Leptonica 8.3 / 6.2 / 8.8 MB; so MNN unless ONNX Runtime is 30% faster. PP-OCRv5's
Latin model has no Vietnamese letters with tone marks (ạ, ấ, ộ…), so Paddle cannot read Vietnamese. On the computer
both runtimes read `ru-sample.png` without an error. The run on the emulator waits for the module review session.

### The OCR comparison (plan of 2026-10-16, row "OCR comparison scope")

- Engines: ML Kit as the app uses it (its Japanese model also for Latin text, the Chinese and Korean models) and
  ML Kit's Latin model, which the app does not carry yet; Paddle PP-OCRv5 mobile (one detector; the recognizers
  main (Chinese, Japanese, English), korean, latin, eslav, cyrillic, el, th, arabic), each on MNN and on ONNX
  Runtime (same models, so the two must read alike and differ in speed and size); Tesseract 5 with tessdata_fast
  and tessdata_best (jpn, jpn_vert, chi_sim, chi_tra, kor, eng, spa, fra, deu, ita, por, pol, tur, lav, vie, rus,
  ell, tha, ara).
- Languages: the 14 of groups 1-3, Japanese also vertical, Chinese Simplified and Traditional; Greek, Thai and
  Arabic for group 4.
- Screenshots taken on the emulator with their text and boxes known (`scripts/ocr-compare/collect.mjs`): the test
  page in Chrome per language (sans, serif, monospace, small grey text, links; light and dark), and Screenlate's
  own screens in each of its interface languages through uiautomator. The real screenshots in `testdata/ocr/` are
  read for a look, not scored (no known text).
- Measured (`score.py`): wrong characters, words read whole, a tap on each word landing in the right word box
  (for Japanese and Chinese, on the right character), the time per screen (median of three runs after a warm-up,
  four threads), native code per ABI and model downloads per language. Emulator times only rank the engines; one
  run on the phone later.
- Decided by the rows "OCR engine choice" and "Diacritics of Latin-script languages"; then, per language, the best
  engine, and combinations with their APK addition per ABI and downloads: ML Kit + Paddle, ML Kit + Tesseract, ML
  Kit + Paddle + Tesseract (Tesseract only where Paddle has no model or lacks letters, e.g. Vietnamese tones),
  and, should Paddle beat ML Kit everywhere, Paddle alone. The results go to the owner before anything is built in.
- Steps: extend the harness (`languages/ocr-compare`) to every language and engine above; download the missing
  models (Paddle main and korean ONNX exports, tessdata of the new languages) and convert them for MNN; take the
  screenshots; run; score; summary table in `ai/notes/ocr-engines.md`; report.

Group 3 code (2026-10-16), each language on its own branch stacked on the previous one and pushed, none merged or
checked on the emulator yet (the module review session holds it): `languages/pl` (Polish; the form-of tags of
Polish, Russian, Turkish and Vietnamese translated), `languages/tr` (Turkish; dotted and dotless i in the language
bundle's case processors), `languages/lv` (Latvian; catalog entries, debitive and renarrative tags, ordinals before
a lowercase word such as "1918. gada"), `languages/vi` (Vietnamese; the lookup also starts one and two syllables
before the aim, classifier form-of rows left out), `languages/ru` (Russian; the stressed reading in the headword's
place through `reading-ru.js`, Cyrillic as an OCR script that only the cloud reads until the chosen engine is
built in). Yomitan has no transforms for any of the five, so forms come from the dictionaries' form-of rows; the
second form-of step was already in the engine. Emulator cases LK22-LK26 wait for the emulator; the sample images
(pl, tr, lv, vi) wait in the session scratchpad until `testdata/` is free.

## Stage E: group 4 (decided when group 3 is done)

Whether it joins the big release (decision row). It would bring right-to-left hit testing (Arabic, Hebrew, Persian),
Paddle's Arabic, Cyrillic, Greek and Thai models, cloud-only scripts (Hebrew, Georgian, Armenian, Khmer, Lao,
Kannada, Syriac) and Ukrainian's 35 rule names.

Candidates named by the owner (request 51): Hindi, Dutch, Malay, Filipino, Indonesian, Serbian, Georgian, Kazakh,
Armenian, Czech, Greek, Bengali, Thai, Belarusian, Kyrgyz. Yomitan (e7334a9) describes hi, nl, tl, id, ka, hy, cs,
el, th, be (transforms for tl, ka, el); it has none for ms, kk, bn, ky, and Serbian only as Serbo-Croatian (sh).
Dictionaries are checked when the group starts; a language without support or dictionaries may be skipped.

### Group 4a (nl, cs, id, ms, tl, sh, uk, be, el)

Starts once the OCR engines plan is done and groups 1-3 are merged (row "Build of groups 1-3"); the owner started it
on 2026-10-19 before the OCR plan's last items, which wait for the phone timings and are adjusted afterwards. Research:
`notes/languages.md`, "Group 4a research"; decisions from the row "Serbo-Croatian" on.

- Shared pieces first, on `languages/main`:
  - the catalog script counts words for the threshold (row "Catalog threshold", built; 51 4a files pass by
    words only) and applies it to transcriptions;
  - own-language editions listed;
  - headwords indexed without stress marks as well (U+0301, U+0300; for sh also U+030F, U+0311, U+0304, U+0302 on
    vowels and r) for be, uk and sh;
  - `OcrScript.GREEK`;
  - the Paddle sets of `paddle_models.py` for the 4a languages: v6 and `latin` for the Latin ones, `cyrillic` for uk,
    be and Serbo-Croatian Cyrillic; `el` converted (its hosting needs the owner's OK of the file list);
  - language codes compared as language tags (`Locale("id").language` is "in"; Android's Filipino is fil).
- Then one branch per language, in this order: nl, cs, id, ms, tl (Latin, as group 3), sh (both entries on one
  branch), uk, be, el.
- Per language, from the research: Yomitan's text processors and transforms (tl, uk, el), abbreviations, quotes,
  `ordinalPeriod` for cs and sh, Greek ; as a sentence end and · not, the apostrophe variants for uk and be, the
  frequency builder fixes (Greek word-final σ → ς, sh Cyrillic spellings by transliteration), dropped tags
  (Serbo-Croatian `Cyrillic` form rows, Baybayin and Jawi alt-of rows), sh audio through Wiktionary's `Sr-` files
  (Latin words transliterated), a Serbian `lang` on Serbo-Croatian text for Noto's glyph forms, the Greek accent
  rule, the audio order by measured coverage, OCR and the test screenshots.
- Timed on the emulator: Yomitan's `addSerboCroatianDiacritics` on long Latin lines (3^n variants per substring,
  capped at 4096) takes 140-260 ms at the cap; no cut of our own was needed.

Progress: nl, cs, id, ms and tl are built on chained branches and checked on the emulator (LK31-LK35, run log of
2026-10-19); `languages/main` is fast-forwarded to `languages/tl` (`3951cea`). Found while checking: the line
"Compound: X + Y" above a split compound (row "German compounds", question 15, owner 2026-10-16) was never built; it
is built now, for German and Dutch (`58ea5fa`). Serbo-Croatian (both entries) is built on `languages/sh`, checked on
the emulator (LK36) and fast-forwarded into `languages/main`; its provisional choices are questions 55-60. Ukrainian
and Belarusian are built on `languages/uk` and checked on the emulator (LK37, LK38). Greek is built on
`languages/el` and checked on the emulator (LK39); its Paddle model is in the `ocr-models-1` release. Group 4a is
done; what is left is the phone timings of the OCR plan. Serbian and Croatian are built as two languages on
`languages/sr` (row "Serbian and Croatian", replacing the two Serbo-Croatian entries), checked on the emulator (LK36
rewritten) and fast-forwarded into `languages/main`; questions 65-66 are answered (look-alikes by the line, Latin capitals kept;
no ML Kit for Serbian).

### Group 4b (hi, bn, ka, hy)

Research of 2026-10-19: `notes/languages.md`, "Group 4b research". Plan, questions and progress:
`2026-10-19-group-4b.md`.

### Group 4c (th, km; lo deferred)

Research of 2026-10-19: `notes/languages.md`, "Group 4c research". Plan, questions and progress:
`2026-10-19-group-4c.md`. Khmer joins Thai now instead of later; Lao is deferred, for a while or for good (owner,
2026-10-19, question 5 of that plan).

### Group 4d (ar, fa, he; ur in group 5f)

Research of 2026-10-19: `notes/languages.md`, "Group 4d research". Plan, questions (69-86) and progress:
`2026-10-19-group-4d.md`. Urdu comes later, on its own (question 69 of that plan; block 5f of row "Group 5"); the
interface stays left to right, right-to-left interface languages are in the backlog (question 70).

### Group 4e (kk, ky)

Research of 2026-10-19: `notes/languages.md`, "Group 4e research". Plan, questions and progress:
`2026-10-19-group-4e.md`. Its row "Hyphenated words" applies to every spaced language, and Kyrgyz PanLex is
ticked as an exception to the row "Recommended dictionaries".

## Stage F: group 5 (decided on 2026-10-19)

Rows "Group 5", "Group 5 research", "Group 5 release". Each block keeps its research, decision table, questions
and progress in a plan file of its own (`2026-10-19-group-5a.md` and so on; question numbers are that file's own),
so the block sessions do not edit the same files; `notes/languages.md` gets one line pointing to it.

- 5a Latin: Romanian, Swedish, Danish, Norwegian, Albanian, Esperanto, Latin; Slovak, Slovenian, Lithuanian, Swahili
  (Basque and Maltese were researched but are not built: 5a question 4). Mostly as group 4a; Swedish and Danish
  compounds as German and Dutch.
- 5b Celtic: Welsh, Irish, Scottish Gaelic, Breton. A word's first letter changes with the grammar (Irish bhean from
  bean), so the lookup needs a rule for initial mutations.
- 5c Long suffix chains: Finnish, Estonian, Hungarian; Azerbaijani, Uzbek. Endings stacked on a stem, compounds in the
  Finnic languages and Hungarian; the Turkic ones as Turkish.
- Between 5c and 5d: marks folded in the lookup (row "Marks folded in the lookup", request 79), so 5d and later
  blocks build on it and write no misread lists of their own; in the same step, language flags in more places (row
  "Language flags in more places").
- 5d Cyrillic: Bulgarian, Mongolian; Macedonian.
- After 5d the chain stops (owner, 2026-10-29): 5e and group 6 start only when the owner asks.
- 5e Other scripts: Cantonese (Han characters as Chinese, jyutping readings), Kannada; Tamil, Telugu, Marathi
  (Brahmic scripts as Hindi; Kannada is read by the cloud only).
- 5f Right to left, after group 4d: Yiddish (Hebrew script), Egyptian Arabic, Assyrian Neo-Aramaic (Syriac script,
  cloud only); Urdu.

## The big release

- Merge `languages/main` into `main`; docs and README; NOTICE; release notes in the agreed style; the version number
  asked from the owner.
- Before it, in this order: every batch of group 4 merged; the code review of all modules (one run per module, see
  `notes/module-map.md`) with its fixes; the owner uses the build for a while; then, when the owner says, betas
  (row "Beta builds"): the build type or flavor for `com.vpr.screenlate.beta` with its name and icon, `release.yml`
  publishing `vX.Y.Z-beta.N` tags as prereleases, the beta build's update feed listing releases and taking the
  newest prerelease, version codes for beta tags. Then an upgrade test from the last release on the phone and the
  catalog regenerated (wty is rebuilt weekly).

## Risks

- Size: en→en and ru→ru are large even with the side table; the download screen's sizes and free space check must be
  right.
- Korean candidates take 9–18 ms per call in QuickJS; a slow phone may need limits on substrings.
- A long-lived branch drifts from `main`: merge `main` in regularly.
- Hosting is undecided; files we build need a stable place before the release.
- wty URLs and sizes change weekly; the catalog script runs before the release, and entries that vanish are handled
  as unavailable downloads.
- Dictionaries whose packaging has no license (KRDICT for Yomitan): decided per language.

## Changelog

- 2026-10-14: request 31: release APKs of `languages/main` (0e24b51, built locally with `-Pscreenlate.splitAbis`)
  against v0.2.3: arm64-v8a 50.6 MB (48.4, +2.2), armeabi-v7a 45.4 MB (43.6, +1.9), x86_64 51.4 MB (49.1, +2.3),
  universal 76.2 MB (71.8, +4.5). Of the arm64 growth: native code +1.23 MB (QuickJS and the language bundles in
  the engine library), ML Kit's Chinese and Korean models +0.53 MB (already bundled by A11), resources +0.22 MB, dex
  +0.19 MB, the catalog +0.04 MB. Chinese and Korean will add little more (the hanja dictionary, ~0.2 MB).

- 2026-10-14: requests 30–41 and the owner's answers: the Languages screen with quick languages and flags, downloads
  that resume or offer "Resume", transcription regions, frequency dictionaries built on the phone from wordfreq (no
  hosting), Chinese tone colors and typed pinyin, Japanese pitch accent colors, the Chinese script default, KRDICT
  as CC BY-SA, a bundled hanja dictionary, the translation button off for new installs only (changes a row of
  `2026-09-29-feedback-after-0.1.4.md`), "translation button" instead of "文A button", and a tone audit of all texts.
  Hosting is postponed with no date. Order of work (revised the same day with request 42): the APK size; requests
  33–41 and our own items of request 37; the test model (request 42) with integration tests; the full emulator
  testing by it (Japanese regression, upgrade and backup from v0.2.3, requests 35 and 36); then Chinese and Korean,
  each tested by the model and merged.

- 2026-10-11: request 25 implemented: bundled imports are identified by source and cancelled before file deletion;
  dictionary media reads select and lock their language; sentence parsing uses the captured note language; the audio
  selection cache includes the regional order. Nine regression tests added and the WorkManager queue test extended.
  JVM and page tests pass; registry (17/17) and Compose/queue tests (5/5) pass on API 30. Phone checks remain open.

- 2026-10-11: request 24 implemented: preparation and queries share the registry lock; Yomitan imports preserve
  other languages' switches and priority slots; Anki editor drafts and refresh answers are isolated by language;
  initial downloads count in the turn-off dialog before files exist. Ten regression tests added. Unit and page
  tests pass, registry tests pass on API 37 and Compose/queue tests on API 30. Phone checks remain open.

- 2026-10-10: plan written from the decisions of `2026-10-09-more-languages.md` and the owner's answers of the day
  (pronunciation dictionaries, scope, turning a language off, catalog threshold, links to unlicensed dictionaries,
  switching during a scan); requests 20–22.
- 2026-10-10: recommended dictionaries (both gloss languages ticked, one entry per gloss language by quality), English
  first; implementation started.
- 2026-10-10: request 23 (whole base, English, then two code-review runs with fixes).
- 2026-10-10: A6 as built. The backup keeps format 1: older versions skip keys they do not know, so a new backup still
  restores there, and an older backup restores into Japanese (its single sort dictionary is Japanese's; each language's
  sort dictionary is now kept by title per language code). The turned-on languages go into the General section and the
  full settings reset keeps them, like the dictionaries. Japanese keeps its old preference keys, other languages add
  `_<code>` (no migration). A page reset clears the shared settings and the shown language's; the dialog says so while
  several languages are on. Page layout with several languages: Lookup and Popup get the language's card (chips,
  scan length, single character entries, romaji; font, its scope, preview sample, catalog, CSS) next to a shared card
  (results; text size and weight); the Anki page, nearly all per language, gets a card at the top that only switches the
  language. Font strings that name the language ("Only for English text") are written per language so they read
  naturally in every locale. Yomitan import goes to the profile's `general.language`, else the active language, and the
  summary names it. Home checks the Anki setup of every turned-on language and audio failures against all their
  sources; its Anki summary shows the active language.
- 2026-10-10: the merged transcription dictionary per language; a confirmed language is turned on and active at once.
- 2026-10-10: A5 as built. First run: the application settles it before anything writes preferences; any preference
  or installed dictionary means an upgrade, which keeps Japanese on without asking. The bundled dictionaries install
  only while Japanese is on and the first run is settled; turning Japanese on again after its dictionaries were
  deleted installs them anew. The download screen's own slot for a gloss language (when the interface language is
  English, the learned language or has no dictionaries) starts empty; downloads go smallest first within each
  category; the free space needed is the size after import plus the largest archive. Size after import is estimated
  from the archive: ×9 for pronunciation dictionaries (measured ×8.1), ×3 for the others (measured ×2–3). The screen
  closes once the downloads are queued and the notification question is answered. Home shows the languages card even
  with one language; "Turn off the chosen language" acts on the active one; deleting a language's files also cancels
  its unfinished downloads. Imports take languages from the catalog entry first, then index.json, where Wiktionary's
  `all` names no language; dictionaries registered under `all` earlier get their catalog language at start.
- 2026-10-10: A7–A9 as built. The language chips sit in the floating bubble's hold menu (the docked bubble has no
  menu); the dock glyph and the scan language already followed the active language. The page shows every rule chain:
  the first under the word, "+N" (bordered, muted) opens the rest; transcriptions are buttons like pitch accents,
  collapsed to one when there are more than two, and a tap lists their dictionaries. Audio regions are a list with
  up and down arrows under the sources (like the sources, no drag); ids `us` (codes `us`), `uk` (`uk`, `gb`) and
  `other`; a stored order keeps only known ids and appends the rest; "Restore the default sources" leaves the regions
  alone. Noticed, not addressed: the merged IPA dictionary holds dozens of variants for common words (about 89 for
  "water", some junk); hoshidicts drops IPA tags; some Wiktionary form-of tags read oddly ("past past participle
  simple and"); "walke" sorts before "walk" (A2 ordering).
- 2026-10-10: A10–A14 as built. A10: the phonetic transcription marker is offered while an enabled pronunciation
  dictionary has transcriptions; pitch markers stay Japanese. A11: `MlKitOcrEngine` keeps one recognizer per script
  (the Japanese model also reads Latin; Devanagari has none); `DeviceOcr` tries ML Kit, then a `ModelOcrEngine` whose
  installed models read the language (none yet: an empty multibinding); `OcrModelStore` (`files/ocr-models`) checks
  the pinned SHA-256, unpacks zips with a path guard and swaps versions; a catalog entry of the category `OCR_MODEL`
  without model, engine, version or checksum is dropped; a language the device cannot read is scanned by the cloud
  alone, and a device-only scan of it shows a message. Lines carry `rightToLeft` (first strong character) and words
  split from the right edge. A12: `scripts/frequency/wordfreq_dictionary.py`, wordfreq's `best` list, capitalized
  copies with the same rank; English top 100K is a 1.0 MB archive (hosting still open). A13: line ends already carry
  each word's separator (ML Kit gives every word the language's, Lens its own), so "give up" broken across lines is
  found (checked on the emulator); a period ends a sentence unless a letter or digit follows at once or it closes an
  abbreviation (`LanguageSupport.endsSentence`); "no" left out of the English list. A14: NOTICE (QuickJS-ng, the
  Yomitan language code, wordfreq data), docs, and an instrumented test of the English path (deinflection, form-of,
  phrase, a table cut short).
- 2026-10-10: second review of the multi-language change. Deleting a language's files also cancels updates of its
  dictionaries, from the turn-off dialog and from the Dictionaries page; the block of turned-off languages leaves models
  out until the first downloadable model (stage D).
- 2026-10-11: request 26 fixed and tested: selected installed dictionaries are enabled on setup confirmation; Search
  closes its old note/audio session on a language switch. Request 27 built after the owner's two decisions:
  active-language readiness and queue link on Home, explanations/links in Search and popup, and no recognition
  before mandatory components are ready. Optional downloads and failures stay visible after lookup is ready.
  Catalog tasks retain language/entry tags; Search observes readiness and reruns when dictionaries arrive.
  Checked JVM tests, page tests (103/103), five Android scenarios and lint; Home and popup checked visually.
  Requests 28–29: explained headless checks, closed the session's emulator and verified its processes gone.
- 2026-10-14: requests 33 and 39 built: "translation button" in the 14 locales, docs, comments and plans (the glyph
  stays only where the icon's shape is meant); the button is off on new installs. The upgrade from a version before
  languages writes it as on in the same write that settles the first run, and restoring an older backup without the
  setting keeps it on too (backups now always carry it). Request 37: one-shot navigation events; the form-of tags and
  "walke" explained in `ai/notes/languages.md`.
- 2026-10-14: request 36 built: downloads wait for the network, retry by themselves three times and resume with Range
  where the server allows it; stopped ones show Resume and Cancel, and the notification has Cancel. Emulator check
  pending (testing phase).
- 2026-10-14: request 34 built: the Languages screen (turned on with active radio, star, flag country and Turn off;
  turned off with files, per-dictionary delete and language change, Delete all, Turn on; not turned on), the compact
  home card with quick flags, flags in the bubble menu (codes on e-ink), stars and flag countries in `LanguageProfiles`
  (backed up with the general settings), circle-flags converted by `scripts/flags/` with a NOTICE entry. The active
  language turned off gives way to the first starred one (agent's choice, within the star rules). Emulator check
  pending.
- 2026-10-14: the "Transcriptions" decision built. The import keeps the tags of tagged transcriptions in
  `transcription_tags.bin` next to the engine's files (a sorted 64-bit hash of term and transcription to a tag set;
  2.5 MB and 30 ms to load for wty-en-ipa), not in Room; a lookup maps flag emoji and per-language accent names
  (`LanguageSupport.transcriptionRegionNames`) to regions, and the page orders by the user's audio regions and labels
  them US/UK/AU. Agent's choices within "regions and cleanup": SAMPA/X-SAMPA-tagged transcriptions and ones with quoted
  text are dropped; a list in one string (`/a/, /b/`) is split; /…/ and […] merge when equal apart from syllable dots
  too; the cleanup happens at lookup, so the `{phonetic-transcriptions}` marker gets the cleaned list as well. water:
  89 → 66, American first. Dictionaries imported earlier show no regions until imported again (on this branch only).
- 2026-10-14: the "Frequency dictionaries" decision built. Catalog entries with `"build": "wordfreq"` (template
  `wordfreq`, one per source language except Japanese, written by `scripts/frequency/wordfreq_catalog.py`) point at
  wordfreq 3.2's lists at commit 912caf6 (large where there is one, small for ko, tr, vi; 0.04–4.5 MB) with SHA-256;
  the import job checks and converts them (`WordfreqDictionary`, 0.4 s for English on a desktop) and imports the
  result. Agent's choices: the built dictionary becomes the sort dictionary only when none was chosen or the chosen
  one is gone (`preferSortDictionary`); capitals follow the language (Turkish i → İ), unlike the Python builder; a
  built dictionary has no update path, so a newer pinned commit reaches only new downloads; the zh entry is wordfreq's
  Simplified list (Traditional text: see the Chinese questions). Emulator check of the whole path pending.
- 2026-10-14: request 41 built (Japanese pitch accent colors). A per-language preference
  `popup_pronunciation_colors` (Japanese keeps the bare key; the Chinese tone colors will reuse it as `_zh`) reaches the
  page as `pronunciationColors`; popup.js marks the headword (first accent of the first dictionary that has one) and
  each accent button with `data-pitch-category` from render.js `pitchCategory`, and popup.css colors them while the
  root's `data-pronunciation-colors` is on. Agent's choices: with one language the switch sits in the Popup elements
  card (there is no language card then), with several in the Japanese card; the reading takes the word's color; colors
  are CSS variables (`--pitch-heiban-color` …) the custom CSS can change; e-ink keeps the light theme's colors (the
  switch is off by default, and color e-ink screens show them); the info panel of an accent is unchanged. Checked in a
  desktop browser in light and dark themes; emulator check pending.
- 2026-10-14: request 40 done (tone audit). About 90 strings rewritten: recognition hints and ⓘ texts, reset
  confirmations, background work and the keep-alive card ("Persistent notification"), bubble state, logs, update and
  notification texts, cloud recognition, translation and Anki errors; a change of meaning in all 14 locales, tone-only
  fixes in English and Russian where the other locales were already neutral, first-person Russian progress texts
  replaced. The usage guide follows the new wording, the README drops conversational phrases, and the release notes in
  `.github/release-notes/` list features by area with fixes in one sentence (v0.2.3: one sentence). GitHub releases
  are unchanged until the owner sees the drafts. Settled wording is in `ai/notes/translations.md` → Tone.
- 2026-10-14: request 42 started. Test model in `ai/test-model.md` (categories E/F/O/N, coverage codes, setup, cases
  by area, non-functional baselines, run log). Found and fixed on the way: custom CSS could not set the pitch accent
  colors in the dark theme; the overlay page tests lacked the renderer's assets. Emulator results go to the model's
  run log.
- 2026-10-14: owner: a tap rescan after a switch in the hold menu takes the new language (it kept the old one until
  the bubble docked, and Japanese text scanned as English found nothing). Decision row "Switching during a scan"
  updated; fixed and checked on the emulator. Found by the emulator run and fixed: catalog downloads show the title
  the catalog lists them by; a download stopped after its retries says "connection lost" instead of the network
  library's message; on e-ink the bubble menu is black and white and the chosen language chip is filled.
- 2026-10-14: the first run of the test model finished (`ai/test-model.md`, run log with the baselines). Further
  fixes from it: home's audio card opens the Anki page at the audio section; a new audio source starts as the
  language's first default kind; the download screen's definition-language menu is as wide as its button; the
  Dictionaries page shows a progress indicator instead of empty sections on its first opening; a language chosen in
  the system's app settings while the app runs also renames languages at once (the default locale follows the
  configuration). Instrumented tests of every module pass (the app's Compose tests on API 30).
- 2026-10-14: the full regression by the test model finished: every E and F case passed (NF4 on API 30 and OCR11 for
  zh, ko and ru left for later). Two more fixes from it: a monolingual Japanese dictionary with loanword spellings is
  no longer taken for Japanese → English (40 % foreign letters needed); the search screen opened from a selection menu
  no longer flashes a white card in the dark theme. Two questions added (English phrasal verbs in the past, deleting
  a font). Next: Chinese on `languages/zh`.
- 2026-10-14: Z3 built. `ChineseScripts` (`core:common`) reads a generated table (`ChineseScriptTable.kt`, 82 KB:
  2,775 Simplified-only and 4,314 Traditional-only characters, the other script's forms by frequency) that
  `scripts/chinese-scripts/build.py` makes from Unihan 17.0.0 (Unicode License V3, in NOTICE with its text) and
  CC-CEDICT's word pairs; Unihan alone marks 图 and 体 as their own Traditional variants. OpenCC is replaced by it
  (not on the allowed downloads; the Z7 question now names this table). Each result carries `lang` (the word's
  script, else the text's, else the setting), which the page sets on the source, entries and character views.
  Wiktionary's `Simplified-Chinese` form-of results show the spelling read and merge with CC-CEDICT's entry; results
  of the same expression and reading merge in general. Lookup page's Chinese block: "Characters" (Simplified /
  Traditional) and "All words in these characters" (off), per language, reset with the Lookup page (question
  "Chinese script setting", option 1). Readings drop Wiktionary's numbered pinyin (question "Chinese readings",
  option 1); character views label both reading fields "Pinyin".
- 2026-10-14: Z4 built. Chinese headwords show pinyin one syllable above each character (`reading.js` registry,
  `reading-zh.js`: own pinyin splitter over a syllable pattern, fewest syllables, no vowel-initial syllable after
  another, erhua r over 儿; a word with letters or a reading that does not match keeps one ruby). The Popup page's
  Chinese block has "Tone colors" (off), reusing `popup_pronunciation_colors_zh`; syllables carry `data-tone` 1–5
  and popup.css colors them with `--tone-N-color` (decision row colors; dark theme variants; e-ink keeps light).
  Agent's choices: only the pinyin is colored, not the characters (the decision row says pinyin); character views
  color their readings too; Anki's `{furigana}` for Chinese is left to Z8.
- 2026-10-14: Z5 built. Typed pinyin finds Chinese words in any form ("nihao", "ni hao", "ni3hao3", "nǐhǎo"; ü as
  v, u: or ü): at import a term dictionary of a language with a reading search gets `reading_index.bin`
  (`ReadingIndex`: words by a 64-bit hash of the toneless key, memory-mapped; the banks are read on up to four
  threads), and `lookupQuery` looks up the first 80 words of the key in the Lookup setting's script and ranks typed
  tones and apostrophes (xi'an → 西安) first, then Yomitan's order. Agent's choices: a key lists its likely common
  words first (a character in more of the dictionary's words counts as more common, a word as its rarest character),
  so the 80 cover a syllable's common characters; words the engine found by an exact reading join the reading
  search's order. Fixes found on the way: results whose readings differ only in spaces merge, as Yomitan's reading
  normalizer for Chinese does (CC-CEDICT "nǐhǎo" and Wiktionary "nǐ hǎo" were two entries); a lookup through
  Yomitan's code no longer fails when its letter resolution splits a character outside the BMP (𣱘), as the engine's
  JSON reader refused the half surrogate.
- 2026-10-15: Z6 built. Wiktionary's Mandarin recordings are searched by the reading's pinyin without spaces
  (`LanguageSupport.recordingTitle`; `Zh-péngyou.ogg`), so a word read several ways gets the clip of the reading shown;
  Lingua Libre's by the characters. Measured on 30 common words: Wiktionary by pinyin 25, Lingua Libre 18, both 28
  (`ai/notes/languages.md`). Agent's choice: Chinese defaults to Wiktionary, Lingua Libre, text to speech (Z1 had
  Lingua Libre first; Chinese is unreleased, nothing to migrate). Fix: text to speech read the reading, which for
  Chinese is pinyin; it now reads the characters (`speechText`), also in the clip menu and the audio source test.
- 2026-10-15: the owner answered every open question (decision rows from "Chinese audio" to "Test model
  location"); new: the Chinese script setting moves to the Languages screen's Chinese card, request 43 (frequency
  chip names) comes after Chinese. Work items Z10–Z12 added.
- 2026-10-15: Latvian joins group 3 at the owner's request (request 44); decision row "Languages and order" and the
  big release's count updated.
- 2026-10-15: Z10 done. The keys are `language_script` and `language_fixed_script` (Chinese is unreleased, nothing to
  migrate); a backup carries them in its General part with the turned-on languages; no page reset clears them, the
  full reset does (agent's choice: the Languages screen has no reset of its own). The hint names words found by pinyin;
  the full reset's text names the Chinese characters and that turned-on languages stay.
- 2026-10-15: Chinese fonts and the font delete confirmation (Z8's fonts, Z11) done: five fonts in the catalog
  (`language` is now the language code, the filter used the BCP 47 tag, which is zh-Hans for Chinese), font
  descriptions in all 14 locales (the Japanese ones had en and ru only), Traditional Chinese interfaces get the
  zh-Hant text (`localeKey`). Deleting a font asks "Delete <font>?" (shared `ConfirmDelete`). Question 1 above
  (other languages' fonts) logged with option (a) in use.
- 2026-10-15: Chinese Anki (Z8's Anki part) done. `{furigana}` and `{sentence-furigana}` of Chinese put a pinyin
  syllable over each character (reading.js rules, as the popup's headwords); Japanese keeps Yomitan's distribution.
  Field guesses take the note language: `LanguageSupport.ankiFieldNames` (Chinese: Simplified, Traditional, Hanzi,
  Chinese, Characters, Pinyin, English and the Chinese names) come before the shared names, and only the language's
  own markers match by name (a "Pitch" field of a Chinese note type stays empty). Question 2 above (Simplified and
  Traditional fields) logged with option (a) in use: `LanguageSupport.scriptMarkers`, filled by
  `DictionaryLookup.inScript`.
- 2026-10-15: Wiktionary tags of Chinese entries translated (Z8's last part): 22 tag words of wty-zh-en's form-of
  items in all 14 locales (scripts: Simplified, Traditional, second-round Simplified; varieties: Cantonese, Hokkien,
  Min Nan, Min, Min Dong, Hakka, Teochew, Wu, Mandarin; regions: mainland China, Taiwan, Hong Kong, southern;
  name, Internet, humorous, euphemistic, dated, vulgar). With the words already translated they cover 99.5% of the
  form-of tag words (counted over the 2026-10-14 revision). `lintDebug` fails in `dictionary:api` on three
  RestrictedApi errors about WorkManager (`Result.Retry`, `Data.Builder.put`), older than this branch.
- 2026-10-15: Z9 done. Checked on the emulator: Simplified, Traditional and vertical Chinese with cloud recognition
  (a vertical miss turned out to be the floating bubble's old scan of another picture: a floating bubble keeps its
  scan until tapped, as designed), Simplified on the device recognizer (the emulator was short of memory, its
  timings are not comparable), deleting a downloaded font that was in use (the phone's font takes over). NOTICE
  names the downloadable fonts; the Lookup summaries say "characters of the word" in all locales. Instrumented
  `ChineseLookupTest` on the real engine. Fix found by it: a lookup starting at a radical look-alike (⽇ for 日, from
  PDFs) was dropped before the engine, which reads radicals as ideographs; Japanese and Chinese now start a word
  there. `scripts/text-image.py --vertical` renders the vertical test picture.
- 2026-10-15: Z12 done. Phrases without a part of speech match English verb forms when the word after the verb is
  an adverb particle; the interposed-object rule stays typed only (question 3, option a in use). Checked over
  wty-en-ru's example sentences, with instrumented tests and on the emulator (test model LK12). The N cases of the
  Chinese work moved to E, F and O in the test model's run log. `docs/usage.md` said the Chinese script is chosen on
  the Lookup page; it is the Languages screen since Z10.
- 2026-10-15: Request 43 done (decision row "Frequency chip names"): `popup.js` shortens a frequency dictionary's
  name wider than four Latin letters to its first character in upper case; the chip becomes a button that toggles
  the whole name, "+N" shows the first one whole, TalkBack reads the whole name. Page test and emulator (POP19).
- 2026-10-15: the Chinese branch merged into `languages/main` (with `main`, v0.2.3 included); `languages/ko` started
  from it. Korean done on the branch (section "Korean"): support class, catalog with KRDICT and the hanja dictionary
  from the app, form-of tags, fonts, character entry tags, emulator checks by the test model. Questions 4-10 logged
  with their recommended options in use.
- 2026-10-15: `languages/ko` merged into `languages/main` (0805427): group 1 (English, Chinese, Korean) is built.
  The engine's instrumented suite passed (29); release APKs: arm64-v8a 51.2 MB, armeabi-v7a 46.0, x86_64 52.0,
  universal 76.8 (v0.2.3: 48.4, 43.6, 49.1, 71.8). Left before group 1 counts as done: the postponed items
  (bundled Japanese dictionaries, hosting) and questions 1-10.
- 2026-10-15: the owner answered questions 1-10: the recommended options stay, except the HTML entities (the page
  turns `&quot;` and `&apos;` into quote marks, on `main` first) and the font lists (a catalog font also shows for
  every language whose script it covers, built with the first Latin fonts). Asked along the way: why the hanja
  dictionary is in the APK (it stays there for good) and a note that text to speech never goes into notes (the hint
  under the source is enough).
- 2026-10-15: the owner settled the bundled Japanese dictionaries (downloaded when Japanese is turned on, on
  `languages/main` only; changes the row "First run") and the order of the work (request 50): the questions of
  groups 2 and 3 now, then group 2, then the OCR tests for groups 3 and 4.
- 2026-10-16: requests 51 (group 4 candidates) and 52 (word audio on the phone, backlog). Group 2 and 3 answers:
  Latin fonts, German compounds, the converter for our own dictionaries, character dictionaries ticked for Japanese,
  Chinese and Korean (changes the row "Turning on a language" for them), audio order by measured coverage,
  Vietnamese words of several syllables, the approach to audio collections.
- 2026-10-16: request 53. Answers before the owner left: the OCR engine choice rule, work while away (group 3 after
  the comparison), downloads extended (row "Downloads by the agent"), screenshots taken on the emulator, bubble letters
  for groups 2 and 3, Russian stress in the headword, the chosen engine for Latin diacritics ML Kit loses. Row "Flag
  country" changed: only English and Portuguese keep a second flag; Australia, Canada, New Zealand, Ireland, Taiwan,
  Hong Kong and Singapore are removed.
- 2026-10-16: request 54 (questions wait while the owner is away). Group 2 built and merged into `languages/main`;
  the Latin fonts and German compounds built on `languages/latin-fonts` (compound parts are looked up letter by
  letter, through a new lookup option). Questions 11-15 logged with the options in use. The French rule label
  "Preterite" is lowercase like the other labels (the generator lowercases a single capitalized word).
- 2026-10-16: character dictionaries ticked (KANJIDIC editions, CC-CEDICT Hanzi; Unihan Hanja already was) and the
  Japanese dictionaries downloaded instead of shipped, on `languages/ja-downloads`. Questions 16 (the reset without
  bundled dictionaries, postponed) and 17 (KANJIDIC editions) logged.
- 2026-10-16: the converters for FreeDict's pairs into Russian and for OpenRussian (ru→en, ru→de) written with unit
  tests; the official OpenRussian dump is gone, so the site's 2020 CSV export is used. Questions 18 (FreeDict quality)
  and 19 (OpenRussian data age) logged.
- 2026-10-16: the OCR comparison prepared on `languages/ocr-compare` (pipeline, harness, ground truth and scoring);
  native code sizes measured; PP-OCRv5's Latin model lacks Vietnamese tone letters. The run waits for the emulator.
- 2026-10-16: group 3 written on `languages/pl`, `tr`, `lv`, `vi` and `ru` (each stacked on the previous one), unit
  and page tests passing, emulator checks pending. Questions 20-25 logged.
- 2026-10-16: the owner answered questions 14, 16, 19, 20, 23 and 25 (rows "Flag outlines", "Dictionary reset",
  "OpenRussian source", "Russian text to speech", "Catalog threshold" changed), asked for more context on the
  others, put the module review off, freed the emulator and asked for the OCR comparison over every engine and
  language, with two engines split by language as an option (row "OCR comparison scope"). Backlog: own copies of
  the dictionaries. Questions 26 and 27 logged.
- 2026-10-16: second round of answers (11-13, 15, 17, 18, 21, 22, 24, 26, 27; new rows from "Audio regions of German,
  French, Portuguese" to "Ordinals with a period", rows "Dictionary reset" and "Catalog threshold" changed). Requests
  55-57 (region order shown only while used, audio sources per language, Yomitan's languages and group 4 in
  batches); questions 28-30 logged.
- 2026-10-16: question 30 answered (every batch of group 4 in the big release); request 58 (release after all
  languages, the code review and the owner's own use; betas). New rows "Beta builds" and "Version of the big
  release"; row "Scope of the big release" changed; the big release section lists the order.
- 2026-10-16: request 59 (the OCR comparison takes in the later languages); row "OCR comparison scope" extended.
- 2026-10-16: requests 60-63 (all three OCR engines kept with per-language models, settings placement review,
  missing dependencies, installed shared models). New rows from "On-device OCR engines" to "Deleting OCR models";
  "On-device OCR beyond ML Kit" superseded; "OCR comparison scope" takes in the cloud and hard screens.
  Questions 31-34 asked. The cloud and hard-screen comparison runs in a separate session on `languages/ocr-compare`.
- 2026-10-16: questions 32-34 answered: rows "Deleting OCR models" (active models only replaced; shared ones only
  while no language uses them as active) and "OCR engine choice" (the recommendation rule). Question 31 explained
  and asked again.
- 2026-10-17: question 31 answered (Paddle's MNN models in a release of our repository, uploaded after the owner
  approves the file list); rows "OCR model names", "Several OCR models at setup" and "Last dictionary with
  definitions" (kept as built) added, "OCR model choice placement" detailed. Outside research on the engines runs in a separate
  session; the OCR engines get their own plan, `2026-10-17-ocr-engines.md`.
- 2026-10-17: the outside research is in `notes/ocr-engine-research.md`. Requests 64 (PP-OCRv6 small joins the
  comparison) and 65 (Latin look-alikes in Cyrillic words); rows "PP-OCRv6" and "Latin look-alikes in Cyrillic text".
- 2026-10-17: row "OCR model list" (a dialog; rows under the engine choice, disabled when the device does not
  recognize; a download does not change the chosen model).
- 2026-10-17: request 66, row "Build of groups 1-3" (a debug build of the 14 languages with Paddle for the owner
  before group 4; no prerelease; the code review after it).
- 2026-10-17: build of groups 1-3 made from `languages/ocr-engines` (the stack of groups 1-3 with the OCR engines and
  Paddle) by CI as the "Screenlate Dev" debug APK; merging the stack into `languages/main` waits for the emulator
  checks (row "Build of groups 1-3").
- 2026-10-18: questions 28 and 29 answered (rows "Region order display" and "Compound part order"); the emulator
  checks of groups 1-3 and the OCR engines run in a separate session on `languages/checks` (`.worktrees/lang`).
- 2026-10-18: requests 67 (download queue, in the backlog) and 68 (scan times and default engines; questions 36-39
  of the OCR engines plan).
- 2026-10-18: questions 36-40 of the OCR engines plan answered: rows "Default OCR engines", "ML Kit at setup" (changes
  "On-device OCR engines" and "Several OCR models at setup" for the languages ML Kit reads), "OCR speed marks"; where
  the engine choice is offered is postponed (Open items).
- 2026-10-18: group 4a questions 41-54 answered: rows "Serbo-Croatian" to "Belarusian flag" added, "Bubble letters"
  extended; Stage E has the group 4a work list.
- 2026-10-19: request 67 answered and built: rows "Download queue" and "Waiting tasks card"; the backlog item now
  records the cause.
- 2026-10-19: answers after the emulator checks: rows "Partial downloads" and "Waiting for a network"; the checks
  branch merged after a review (two minor fixes: decomposed Vietnamese app text, a PaddleOCR release during a trim).
- 2026-10-19: group 4a started before the OCR plan's timing-dependent items (owner: the timings can be applied
  afterwards); `languages/main` fast-forwarded through the stack and `main` merged (`ac61458`). The Greek Paddle
  model may go to `ocr-models-1` once the 4a work is done (owner); that release is a regular one now (owner), not
  marked latest, so the in-app update check (`releases/latest`) still finds v0.2.3.
- 2026-10-19: group 4a shared pieces on `languages/main`. The catalog script counts words (cached in
  `scripts/catalog/word-counts.json`; archives of 8 MB or more pass uncounted) and lists own editions of languages
  outside the gloss languages, not recommended; the existing languages gain 146 small pairs and glossaries that pass
  by words (lv–ru among them), none is lost. Headword marks are dropped at import; the archive scan showed that
  Russian needs it as well (376k form-of items name stressed lemmas), so Russian gets the rule. Paddle and Tesseract
  sets for the 4a languages, `paddle-el` converted (not uploaded yet); cs, uk, be and el recommend the measured
  Paddle models, the unmeasured Latin languages recommend none.
- 2026-10-19: the Latin languages of group 4a (nl, cs, id, ms, tl) checked on the emulator and fast-forwarded into
  `languages/main`. Fixes from the check: a second form-of step never leads back to the text it started from (Jansen →
  Janssen → Jansen showed as a form chain); a capital I read for the l of Czech "n. l." keeps the sentence going.
  Translation of Chinese, Portuguese and Filipino sentences picked no service language (plain bug, fixed). The
  missing "Compound:" line is built next.
- 2026-10-19: the line "Compound: …" built for German and Dutch. Serbo-Croatian built as two entries sharing the
  dictionary code sh, checked on the emulator and fast-forwarded into `languages/main`. Plain fixes found: QuickJS has
  no console, so Yomitan's warning at its 4,096-variant cut failed the lookup (a stub in every bundle); Cyrillic text
  with reading marks found nothing (the glue replaces Yomitan's accent mark processor); a Croatian or Serbian
  interface made the text's own language the default translation target. Provisional choices: questions 55-60.
- 2026-10-19: Ukrainian and Belarusian built on `languages/uk` (Belarusian takes Ukrainian's stress mark and
  apostrophe processors) and checked on the emulator. Plain fixes found: a form-of step back to the scanned text after
  a Yomitan rule showed as a chain on every feminine noun in -а (книга); a word of one letter read in Latin (і, у, В)
  stayed Latin in every Cyrillic language; wordfreq's Ukrainian words also rank their ’ and ʼ spellings.
- 2026-10-19: Greek built on `languages/el` and checked on the emulator. Every headword is keyed without its tonos at
  import (row "Greek accents"); wordfreq's word-final σ becomes ς; Latin look-alikes in Greek words become Greek;
  `paddle-el` uploaded to `ocr-models-1` (owner's OK once the 4a work was ready). Plain fix found: the Lingua Libre
  search took only the ISO 639-3 code, while Greek recordings also carry `gre` and `mis`; it now takes any code.
- 2026-10-19: questions 55-60 answered; at the owner's request Serbo-Croatian becomes two languages, Serbian and
  Croatian, on the shared dictionaries (questions 61-64; row "Serbian and Croatian" replaces "Serbo-Croatian", and
  "Bubble letters" names Croatian Đ and Serbian Ћ).
- 2026-10-19: Croatian and Serbian built on `languages/sr` and checked on the emulator. Croatian audio tries Hr-
  recordings, then the Serbian ones in Cyrillic, and both Lingua Libre items; Serbian looks for Sr- under both
  spellings and searches Lingua Libre in Latin letters; Serbian Latin text goes to the voice in Cyrillic and to the
  translators as sr-Latn. Serbian look-alikes are fixed per line rather than by the model (question 65 asks the
  owner to confirm); question 66 (ML Kit for Serbian) added. Plain fixes: Commons matches inside titles are dropped;
  Јој read as Latin on a Cyrillic line is written in Cyrillic.
- 2026-10-19: questions 65-66 answered. Row "Serbian and Croatian": look-alikes by the line, whatever the model
  (was "only with a Cyrillic model"), and Latin words in capitals stay on Cyrillic lines outside headings in capitals,
  as the owner asked to keep English words safe; no ML Kit in Serbian's model list.
- 2026-10-19: row "Furigana markers" recorded from the language rules review (the owner's decisions there); the
  catalog threshold in words and the Serbo-Croatian diacritics timing marked done in group 4a's list.
- 2026-10-19: small limits of the language rules review fixed at the owner's request ("which are worth fixing"): ML
  Kit's line-end hyphen put back into the line's last word, Roman ordinals before a lowercase word (row "Ordinals with
  a period" extended to Roman numerals), `{phonetic-transcriptions}` with each transcription's tags as Yomitan writes
  them, and the compound split. Row "German compounds" changed (owner): the fewest parts, then the longest shortest
  part, instead of the longest word from the start. Checked on 20,000 long lemma headwords of wty de-en and nl-en each
  (the headword hidden): 2% (de) and 4% (nl) of splits change; of 80 changed ones 47 better, 26 worse in German
  (urlaub + stag, adverbs in -erweise, numerals), about 50 and 13 in Dutch. A wordfreq tie-break was worse
  (middag + ten, dan + school). The other limits stay, with reasons, in `notes/language-rules.md`.
- 2026-10-19: group 4b (Hindi, Bengali, Georgian, Armenian) researched; its plan with the questions is
  `2026-10-19-group-4b.md`, linked from Stage E.
- 2026-10-19: group 4c (Thai, Khmer, Lao) researched; its plan with the owner's answers is `2026-10-19-group-4c.md`,
  linked from Stage E. Lao and Khmer are built now with Thai (question 30 said later). The converted Paddle models
  that wait (Thai, Devanagari) go up in one release when the owner decides (row "Paddle model hosting": the upload
  date is the owner's; question 9 of the group 4b plan said "with the next Paddle models").
- 2026-10-19: Lao deferred, for a while or for good (owner, after a summary of group 4c: one small English
  dictionary, weak on-device recognition, no recordings or voice); Thai and Khmer are built.
- 2026-10-19: group 5 added (owner): its languages, six blocks and research of five blocks at once, the sixth after
  group 4d; provisionally after the big release. Rows "Group 5", "Group 5 research", "Group 5 release", Stage F.
- 2026-10-19: Backlog "Own copies of the dictionaries" done: the private archive with its refresh script outside the
  repository (`notes/dictionary-archive.md`); the script stays in the archive and newer versions replace files only
  after their checks, files their source drops are kept (owner).
- 2026-10-19: group 4d researched (Arabic, Persian, Hebrew; `2026-10-19-group-4d.md`, questions 69-85 answered by
  the owner); Urdu later, on its own; right-to-left interface languages added to the backlog. The build waits for the
  owner's go.
- 2026-10-19: group 4e (Kazakh, Kyrgyz) researched; its plan with the owner's answers is `2026-10-19-group-4e.md`,
  linked from Stage E. Two of its rows reach beyond the group: a tap on a part of a hyphenated word shows the whole
  word when it is a headword, then the tapped part, in every spaced language; Kyrgyz PanLex is ticked next to
  Wiktionary, an exception to "Recommended dictionaries". The line of `notes/languages.md` that kk and ky would get
  the longest matching start is corrected: word resolution never shortens a word.
- 2026-10-20: request 69 and row "Look-alike fixes only after OCR" (owner): look-alike letter fixes are for OCR
  results only, never for text read from apps, in every language and branch; the code already works so.
- 2026-10-20: request 69 refined (owner): the OCR-only rule holds where the text itself is written right; spellings
  people often type themselves (Uzbek o for oʻ) stay matched in any text.
- 2026-10-20: request 70 (owner): group 6 research narrowed to ca, gl, tt, ba, is, af and sa; the other candidates
  are recorded as deferred.
- 2026-10-20: built languages checked against request 69 (owner's answers): the Czech capital I read for the l of
  "n. l." is fixed in OCR text only (`CzechSupport.ocrLines`); Arabic text with the Persian ک ی is matched as ك ي in
  any text (bundle `arabicLetters`); the other rules that reach app text stay, as people type text that way.
- 2026-10-20: request 71 recorded (own log file; analysis given, build later on the owner's word).
- 2026-10-21: request 71 (owner): when built, the own log file goes to both main and languages/main.
- 2026-10-21: request 72 (owner): the build order of the researched blocks, one session at a time.
  `experiment/languages` deleted after its measurement tools and harness moved to `scripts/yomitan-language/` (owner).
- 2026-10-22: request 72 says every block measures its engines first; requests 73 (wty issues #399 and #400, waiting
  for #399 until 2026-11-05) and 74 (the completeness check of every session); questions 67-76 from the reports;
  Basque and Maltese marked as not built in row "Group 5" and Stage F (5a question 4).
- 2026-10-22: questions 67, 68 and 70 answered; question 69 notes that both diagnostics builds open the connection on a
  touch; request 75 and Backlog "Bubble menu in the dock" (the menu on the docked bubble, deferred).
- 2026-10-22: request 76 (the night's autonomous build chain and the rules for its sessions); question 71 decided
  provisionally as a plain bug.
- 2026-10-22: questions 67 and 71 built and merged (`claude/download-audio-fixes-a83144`); questions 77 (the network
  wait's 2 s and 30 s) and 78 (audio failures per language) from that work; the input merger fix (a chained download
  fetched the stopped one's file) was a plain bug.
- 2026-10-22: the night's emulator checks merged (`languages/checks-2`); questions 79-86 from them (84 a plain bug,
  decided provisionally).
- 2026-10-22: request 72 says a hung or waiting session never holds the chain (owner); group 5a started while group 4e's
  session waits on a browser prompt.
- 2026-10-22: wty fixed both reports of request 73 in its release 2026.10.22 (the contractions are back, Tatar added);
  the 2026-11-05 check and the own supplement are dropped.
- 2026-10-22: wty's release 2026.10.22 reverses every alt-of row and adds misspelling and obsolete redirects; question
  87 answered (b), shown as they are; request 77 (the follow-ups, a session of their own); request 76's blocks measure
  on the new release.
- 2026-10-22: request 71 built with the owner's answers (8 MB, one export file, no clear button) together with the
  service stop report and the "service is off" notification (regular builds, a banner without sound, at every stop, no
  extra permission request); merged into main and languages/main (owner).
- 2026-10-22: Backlog item on initials in sentence ends (group 5a's Lithuanian finding).
- 2026-10-22: groups 4e's follow-ups (`languages/kk-followups`) and 5a's chain (`languages/lt`: sv, no, da, ro, la,
  lt) merged into `languages/main`; 4e's new device cases renumbered LK61-LK63; Backlog item on smaller language
  bundles.
- 2026-10-22: group 4e's questions 16 and 18-22 answered (owner): the six stem-changing pronouns skipped by noun endings,
  PanLex capitalized headwords without junk, initials hold the sentence in every spaced language, the verb filter and
  the other-script filter stay, the Kyrgyz -ыш rule added; built by group 4e.
- 2026-10-22: Slovak (`languages/sk`) merged into `languages/main`; group 5a's questions 24-26 answered (owner, all
  (a) as built); request 78 (the overlay crash when the service comes back).
- 2026-10-22: owner decision — the "service is off" notification is posted 3 s after a stop and only if the service
  has not connected again (was: at once at every stop; a `uiautomator dump` gave a banner each time); built together
  with request 78.
- 2026-10-22: owner decision — the build chain pauses after group 5a's Slovenian, to keep the week's usage limit;
  Esperanto, Albanian, Swahili, 5b-5e and group 6 run later in a separate run.
- 2026-10-28 evening: request 79 and decision row "Marks folded in the lookup" (owner): every Latin- and
  Cyrillic-script language but Vietnamese finds words with any marks over its base letters; built between groups 5c
  and 5d.
- 2026-10-28 evening: the owner answered questions 69-86 (the rest of 71 and 84 are plain bugs): to build are 72 (1, 2,
  3 and the AnkiDroid debugging of 4), 77 (3 s and 15 s), 80, 81, 82, 83, 85, and for the archive 74 and 76; 69, 73,
  75, 78, 79 and 86 stay as they are.
- 2026-10-28 late evening: the PaddleOCR lines, converter data, AnkiDroid debugging and archive catch-up of questions
  72, 74 and 76 moved to the backlog as four entries at the owner's word, not built now; the owner asked for tables
  of the flag places and of the bubble letters.
- 2026-10-28 night: the owner decided the bubble glyph (row "Bubble glyph": the own name's first letter, two-letter
  codes when quick languages clash, Cyrillic codes in Cyrillic, also on e-ink; Arabic ع and Persian ف), chose the
  Esperanto web corpus of 2012 (group 5a question 28), and sent five backlog tasks to one cloud session (interface
  translation check, candidate interface languages, smaller language bundles, converter data, the right-to-left
  strings); group 6's research stays in the local chain.
- 2026-10-29: decision row "Language flags in more places" (owner): flags in the language chips, the OCR model list,
  the download screen's title, the cards of turned-off languages and the first-run screen, built in the step
  between groups 5c and 5d; the bubble glyph is built by the coordinating session; Esperanto's frequency dictionary
  now comes from Leipzig's web texts of 2012.
- 2026-10-29: the build chain stops after group 5d; groups 5e and 6 start only at the owner's word.
- 2026-10-29: marks folded in the lookup and language flags in more places built on branch `languages/marks`
  (`2026-10-29-folded-marks-and-flags.md`: technical choices, data check, progress); the data check finds one or two
  extra results on a third or more of running text in Italian, Portuguese, Spanish, Irish and French, so the question
  of the extra results is in that plan, the fold kept as decided until the answer.
