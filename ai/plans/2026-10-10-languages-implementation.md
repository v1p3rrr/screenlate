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
| On-device OCR beyond ML Kit | PaddleOCR only, for Cyrillic, Arabic, Greek and Thai; the scripts it lacks (Hebrew, Georgian, Armenian, Khmer, Lao, Kannada, Syriac) use the cloud for now. Before the Russian stage Paddle is compared with Tesseract on real screenshots and the runtime (MNN or ONNX Runtime) is picked by size and speed; Tesseract alone if Paddle is not clearly better. The OCR layer is built so that Tesseract can be added later without rework (owner, 2026-10-09, requests 15 and 17) |
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
| Scope of the big release | Groups 1–3 (13 languages with Latvian) at least; group 4 joins the same release if it turns out easy to add, decided when group 3 is done. The shared base is built for group 4 from the start (right-to-left text, further OCR models) (owner, 2026-10-10: "we'll see how it goes") |
| Postponed items | The open items (bundled Japanese dictionaries, hosting) are decided when the first language or group 1 is ready, before it is merged (owner, 2026-10-10) |
| First run | Asks which language the user learns, Japanese preselected; another language goes straight to turning it on with its downloads; Japanese needs no downloads as today (owner, 2026-10-09). Changed 2026-10-15: on `languages/main` Japanese goes to its download screen too (row "Bundled Japanese dictionaries") |
| Text to speech | The last default audio source for languages other than Japanese; played only, never put into notes (owner, 2026-10-09) |
| Inflection names and tags | Translated into all 14 interface languages, a language's rule names and descriptions when that language is added; the Wiktionary tag list (~105 words) with the first group and extended per language; tags outside the list show as in the dictionary; Anki's `{conjugation}` keeps the English name (owner, 2026-10-09) |
| Chinese script | Glyph forms (SC/TC `lang` tag) and the headword spelling follow the text under the aim; a setting gives the script for words written alike in both and for typed searches, and may fix one script for everything (owner, 2026-10-09: "1 + a choice in settings"). The setting ("Characters" and the switch "All words in these characters", off by default) is on the Chinese card of the Languages screen, an exception to "Settings layout"; a word found in both spellings is one entry, headed by the chosen script with the switch on and by the text's otherwise, with the definitions of both (owner, 2026-10-15) |
| Turning a language off | A dialog with "Delete its dictionaries and models (N MB)", ticked by default; the language's settings are kept, so turning it on again restores the deck, fonts and the rest, and without the files deleted it turns on at once. The Dictionaries page lists turned-off languages that still have files, with their size and a button that deletes them all; single dictionaries are deleted as today (owner, 2026-10-10, request 22; the place on the Dictionaries page is the agent's choice, delegated) |
| Catalog threshold | Wiktionary pairs are listed from 0.5 MB of archive up (a few thousand words); nearly empty pairs (es→vi 0.0 MB, zh→de 0.1 MB) are left out, and the catalog script adds a pair once it grows past the threshold (owner, 2026-10-10) |
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
| German compounds | When the whole word has no entry, the popup shows its parts in order: the longest dictionary word from the start, then the longest in the rest, linking -s-, -es-, -n- skipped (Haustürschlüssel → Haus, Tür, Schlüssel); a word with its own entry as now. Later the same for other languages with compounds (Dutch, Swedish) (owner, 2026-10-16) |
| Dictionaries we convert | The converter for FreeDict's pairs (de, fr, it, es, pl, en, zh → ru) and OpenRussian is written now; its files are checked on the emulator (adb, a local address) and join the catalog once hosting exists (owner, 2026-10-16) |
| Character dictionaries ticked | KANJIDIC for Japanese, CC-CEDICT Hanzi for Chinese and the hanja dictionary for Korean are ticked on the download screen (an exception to "Turning on a language", where character dictionaries are not) (owner, 2026-10-16) |
| Audio order, groups 2 and 3 | By the measured coverage of ten common words: Wiktionary first for Russian and Polish, Lingua Libre first for Spanish, French, German, Italian, Portuguese, Turkish, Vietnamese and Latvian; text to speech last. A source without the word falls through to the next, so the order only picks whose clip plays and goes into notes when several have one (owner, 2026-10-16) |
| Vietnamese words of several syllables | The lookup also starts one and two syllables before the aimed one; a word that covers the aimed syllable comes first (học sinh), then the syllable's own entries (owner, 2026-10-16) |
| Audio collections | Word audio on the phone (request 52): the app imports a collection the user brings (Local Audio's export for phones) as an offline audio source; our own packs from Wiktionary and Lingua Libre per language, downloaded in the app, come once hosting exists (owner, 2026-10-16) |
| Bundled Japanese dictionaries | Downloaded when Japanese is turned on, the first run included, from the catalog like any language's; the APK no longer carries them (about 25 MB). Only on `languages/main`: `main` keeps them bundled until the big release. Installed copies stay after an upgrade (owner, 2026-10-15; settles the postponed item) |
| OCR engine choice | Paddle and Tesseract are compared on the emulator, inside a debug build, on screenshots taken there with known text, for the scripts ML Kit lacks (Cyrillic, Greek, Thai, Arabic). Measured: the share of wrong characters, the share of words read whole, and whether a tap lands in the right word box. Paddle is chosen when on the Russian screenshots it makes at least a third fewer character errors than Tesseract, is not clearly worse on any script and takes at most twice as long per screen; otherwise Tesseract. With Paddle, MNN or ONNX Runtime by the smaller addition to the APK, unless the other is at least 30% faster per screen. Speed is measured on the emulator; one run on the phone once the owner is back, and the choice changes only if the phone shows a clearly different picture (owner, 2026-10-16) |
| Work while the owner is away | After group 2 and the OCR comparison the agent picks the engine by the row above, builds it in and does group 3 (Russian, Polish, Turkish, Vietnamese, Latvian), each language on its own branch merged after the emulator checks; group 4 is not started (owner, 2026-10-16) |
| OCR screenshots | Taken by the agent on the emulator: Wikipedia and news sites in the browser, the Android interface in the language, several fonts, light and dark themes; kept in `testdata/ocr/` (owner, 2026-10-16) |
| Bubble letters | The docked bubble's letter: Spanish Ñ, French Ç, German ß, Italian È, Portuguese Ã; Russian Я, Polish Ł, Turkish Ğ, Vietnamese Ư, Latvian Ā (owner, 2026-10-16) |
| Russian stress | The headword shows the dictionary's stressed form (ко́шка) with no separate reading line; Anki's `{expression}` is the word without stress, `{reading}` the stressed form as in the dictionary (owner, 2026-10-16) |
| Diacritics of Latin-script languages | Where ML Kit's Latin model loses a language's diacritics (Vietnamese tones, Polish ł, Turkish ğ, Latvian macrons), that language uses the chosen engine's model instead, downloaded when the language is turned on, but only when it makes at least a third fewer character errors than ML Kit on that language's screenshots (owner, 2026-10-16) |

## Open items (postponed by the owner)

Decided when the first language or group 1 is ready, before it is merged into the multi-language branch.

- Hosting the files we build or mirror (OCR runtime models, dictionaries without releases such as OpenRussian and
  FreeDict conversions; wordfreq frequency dictionaries are built on the phone since 2026-10-14): likely a public repository of our own; Hugging Face to be
  looked at. Postponed with no date (owner, 2026-10-13, request 30): remind the owner whenever they ask what is
  left open. Until then test builds download them from wherever is handy (for the emulator, `adb push` and a local
  URL are enough; for the owner's phone, ask before putting anything on a public host).

## Backlog

- Word audio on the phone (request 52), optional: decision row "Audio collections" (the user's own collection
  imported; our packs once hosting exists).

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

## Branches and workflow

- `languages/main`: the multi-language branch, off `main`. (Git cannot hold a branch `languages` next to
  `languages/en`, hence the name.) `main` stays releasable for Japanese fixes and is merged into `languages/main`
  regularly, at least after every release and before every language branch starts.
- `languages/<code>`: one branch per language off `languages/main`, merged back once tested thoroughly on the emulator (owner, 2026-10-14; was: when it works
  on the phone); the next language starts from the merged state.
- `experiment/languages`: reference only; pieces may be reused (QuickJS submodule, `scripts/yomitan-language/`
  bundles, harness and tools). Deleted with the owner's OK after group 1 is merged.
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
  (`scripts/openrussian/`, ru→en 6.7 MB and ru→de 8.2 MB with form-of rows; question 19) and hosted, the Zaliznyak meta dictionary with its non-commercial mark, pronunciation ru 6.1–6.5 MB.
- Polish: forms from the dictionary (pl→en 22 MB). Turkish: dotted and dotless i in case folding. Vietnamese: words of
  several syllables through word resolution; diacritics with the ML Kit Latin model (else Paddle latin).
- Latvian (request 44): Latin letters with macrons and cedillas (ā ē ī ū, ģ ķ ļ ņ, č š ž) through the ML Kit Latin
  model; forms from the dictionary unless Yomitan has rules for it; check Yomitan's language list and the Wiktionary
  editions (lv→en, lv→ru) when the group starts.
- Questions per language: the recommended dictionaries; anything the comparison leaves open.

## Stage E: group 4 (decided when group 3 is done)

Whether it joins the big release (decision row). It would bring right-to-left hit testing (Arabic, Hebrew, Persian),
Paddle's Arabic, Cyrillic, Greek and Thai models, cloud-only scripts (Hebrew, Georgian, Armenian, Khmer, Lao,
Kannada, Syriac) and Ukrainian's 35 rule names.

Candidates named by the owner (request 51): Hindi, Dutch, Malay, Filipino, Indonesian, Serbian, Georgian, Kazakh,
Armenian, Czech, Greek, Bengali, Thai, Belarusian, Kyrgyz. Yomitan (e7334a9) describes hi, nl, tl, id, ka, hy, cs,
el, th, be (transforms for tl, ka, el); it has none for ms, kk, bn, ky, and Serbian only as Serbo-Croatian (sh).
Dictionaries are checked when the group starts; a language without support or dictionaries may be skipped.

## The big release

- Merge `languages/main` into `main`; docs and README; NOTICE; release notes in the agreed style; the version number
  asked from the owner.
- Before it: the owner decides whether the module reviews run on the changed modules; an upgrade test from the last
  release on the phone; the catalog regenerated (wty is rebuilt weekly).

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
