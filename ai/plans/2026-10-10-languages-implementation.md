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

## Decisions

Moved from `2026-10-09-more-languages.md` on 2026-10-10 (that table is frozen); later rows at the end.

| Topic | Decision |
|---|---|
| Languages and order | 1) English, Chinese, Korean (English and Chinese before Korean); 2) Spanish, French, German, Italian, Portuguese, the most learned languages after them (request 11); 3) the other interface languages: Russian, Polish, Turkish, Vietnamese; 4) every other language Yomitan supports, Ukrainian among them (owner, 2026-10-09; revised the same day with request 11) |
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
| Downloads by the agent | While the owner is away the agent may download, without asking, data from the authors' official sources for building and testing: circle-flags SVGs, wordfreq word lists, Unihan, CC-CEDICT, Wiktionary zh/ko (Hugging Face), KRDICT (Lyroxide); listed in the status afterwards (owner, 2026-10-14) |
| Published release notes | Rewritten only in `.github/release-notes/`; the texts of published GitHub releases change only after the owner has seen the drafts (owner, 2026-10-14) |
| Unofficial collection | If the owner keeps a separate collection of unlicensed dictionaries, the app and its repository do not link to it (owner, 2026-10-09, preliminary) |
| Audio regions | A per-language order of regions in the audio settings, used for Wiktionary clips whose file names carry the region; defaults: English US, UK, others; Portuguese Brazil; Spanish Latin America, Spain; French France (owner, 2026-10-09) |
| Turning on a language | After the target language, a download screen (request 19). Gloss languages: the interface language and English, at most two; when the interface language is English or is the target language, the user picks another one in its place. Each category lists every dictionary for the chosen gloss languages with the recommended one ticked; a mandatory category (the main dictionary; the forms dictionary for languages whose forms are only in dictionaries) needs at least one ticked, else Confirm is disabled with a hint; a mandatory item without a choice is a preselected radio button. Optional items ticked by default: the frequency dictionary, the pronunciation dictionary (except Chinese) and the on-device OCR model where the script needs one; glossaries and character dictionaries are not. Every item shows its size; the button shows the download total and, under it, the space after import; not enough free space disables it and says how much is needed; on mobile data a total over 50 MB asks first (owner, 2026-10-10) |
| Releases | No releases of new languages along the way: every language is built first, then one big release; testing in the meantime on debug builds (owner, 2026-10-10) |
| Beta label | None: with a small audience a label would never come off (owner, 2026-10-10) |
| Pronunciation dictionaries | Wiktionary IPA dictionaries are named for users: the category "Pronunciation", the item "Transcription (Wiktionary)" with an ⓘ example (`water → /ˈwɔːtər/`), never "IPA". One item per language, not per gloss language (the transcription is the same in every edition): the edition with the most entries. Ticked by default for every language except Chinese (30 MB, pinyin is already in its main dictionaries) (owner, 2026-10-10; changes the "Turning on a language" row, which had IPA unticked) |
| Scope of the big release | Groups 1–3 (12 languages) at least; group 4 joins the same release if it turns out easy to add, decided when group 3 is done. The shared base is built for group 4 from the start (right-to-left text, further OCR models) (owner, 2026-10-10: "we'll see how it goes") |
| Postponed items | The open items (bundled Japanese dictionaries, hosting) are decided when the first language or group 1 is ready, before it is merged (owner, 2026-10-10) |
| First run | Asks which language the user learns, Japanese preselected; another language goes straight to turning it on with its downloads; Japanese needs no downloads as today (owner, 2026-10-09) |
| Text to speech | The last default audio source for languages other than Japanese; played only, never put into notes (owner, 2026-10-09) |
| Inflection names and tags | Translated into all 14 interface languages, a language's rule names and descriptions when that language is added; the Wiktionary tag list (~105 words) with the first group and extended per language; tags outside the list show as in the dictionary; Anki's `{conjugation}` keeps the English name (owner, 2026-10-09) |
| Chinese script | Glyph forms (SC/TC `lang` tag) and the headword spelling follow the text under the aim; a setting gives the script for words written alike in both and for typed searches, and may fix one script for everything (owner, 2026-10-09: "1 + a choice in settings") |
| Turning a language off | A dialog with "Delete its dictionaries and models (N MB)", ticked by default; the language's settings are kept, so turning it on again restores the deck, fonts and the rest, and without the files deleted it turns on at once. The Dictionaries page lists turned-off languages that still have files, with their size and a button that deletes them all; single dictionaries are deleted as today (owner, 2026-10-10, request 22; the place on the Dictionaries page is the agent's choice, delegated) |
| Catalog threshold | Wiktionary pairs are listed from 0.5 MB of archive up (a few thousand words); nearly empty pairs (es→vi 0.0 MB, zh→de 0.1 MB) are left out, and the catalog script adds a pair once it grows past the threshold (owner, 2026-10-10) |
| Recommended dictionaries | On the download screen the main dictionary of each chosen gloss language is ticked (both, each can be unticked while one stays). Within one gloss language and category one entry is ticked when there are several (Jitendex or JMdict): the catalog marks it, chosen by quality and coverage (Jitendex over JMdict, KRDICT over Wiktionary for Korean), size only breaking ties (owner, 2026-10-10; the rule within a gloss language delegated to the agent) |
| First language branch | English: fewer open questions than Chinese and the simpler lookup; it is also the vehicle of the shared stage (agent's choice, delegated by the owner, 2026-10-10) |
| Switching during a scan | A language chosen in the bubble's hold menu applies from the next scan; the open scan keeps its language until the bubble is docked (owner, 2026-10-10) |
| Transcription file | The merged transcription dictionary of all Wiktionary editions (`dict/all/<language>`, e.g. en 3.8 MB against en-en 2.8, ru 10.4 against 6.5) instead of the edition with the most entries; still one item per language (owner, 2026-10-10; changes the "Pronunciation dictionaries" row) |
| A language confirmed on the download screen | Turned on and made active at once; until its main dictionary is installed the home screen shows the missing-dictionary card with the download progress (owner, 2026-10-10) |
| Installation status placement | The home screen shows the active language's status and a link to the queue; Search and the popup show an explanation and the same link while the language is not ready (owner, 2026-10-11, request 27) |
| Scanning before installation is ready | Do not start recognition until the mandatory components are ready; show status and a link to the queue immediately (owner, 2026-10-11, request 27) |
| Languages screen | A screen of its own, "Languages", opened from the home screen. Home keeps a compact card: the quick languages' flags (the active one marked), the active language's installation status and an "All languages" button. The screen lists turned-on languages (status, quick-switch star, turn off), turned-off languages with files (their dictionaries and OCR models with sizes, delete all, turn on) and the rest (turn on, which opens the download screen). The block of turned-off languages moves there from the Dictionaries page, which keeps a link (owner, 2026-10-13, request 34) |
| Quick languages | At most three: the active language has a star of its own, at most two more are starred on the Languages screen. Making a starred language active swaps the roles (the old active one keeps a star as an ordinary starred language); making an unstarred one active (also a language just turned on) moves the active language's star to it, and the old active one loses its star even when a place is free (owner, 2026-10-13/14) |
| Flags | Round SVG flags bundled in the app (circle-flags, MIT, with a NOTICE entry; owner, 2026-10-13: "if you want to take someone else's, fine"); with e-ink on, the language's two-letter code replaces the flag (owner, 2026-10-13) |
| Flag country | A default flag per language (English US, Spanish Spain, Portuguese Brazil, Chinese China, French France); for languages of several countries the Languages screen offers another country (owner, 2026-10-13) |
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

## Open items (postponed by the owner)

Decided when the first language or group 1 is ready, before it is merged into the multi-language branch.

- Bundled Japanese dictionaries (25 MB in the APK): keep them bundled or download them when Japanese is turned on;
  the owner leans to keeping them bundled.
- Hosting the files we build or mirror (OCR runtime models, dictionaries without releases such as OpenRussian and
  FreeDict conversions; wordfreq frequency dictionaries are built on the phone since 2026-10-14): likely a public repository of our own; Hugging Face to be
  looked at. Postponed with no date (owner, 2026-10-13, request 30): remind the owner whenever they ask what is
  left open. Until then test builds download them from wherever is handy (for the emulator, `adb push` and a local
  URL are enough; for the owner's phone, ask before putting anything on a public host).

## Questions for the owner

Collected during autonomous work (owner away since 2026-10-14); each with its context, asked when the owner is back.
Work that depends on an answer uses the recommended option or waits, as noted.

- Emulator processes (2026-10-14). A disposable `-read-only` Pixel_10_Pro instance started for checking the Languages
  screen crashed at boot: the host could not allocate GPU memory ("Failed to allocate ColorBuffer with Vulkan
  backing"). Its processes (emulator.exe 12780, qemu 127372, crashpad 117336, started 2026-10-14 01:14) may still be
  waiting on the crash dialog; stopping them by PID was refused by the session's permission check. The stale qemu 70992
  from 2026-10-10 could not be stopped either: Windows lists it, but it no longer exists as a process. Please close
  those windows or allow the agent to stop the processes it started. The next attempt used software rendering
  (`-gpu swiftshader_indirect`) and crashed the same way at boot ("Could not allocate host memory", 3.8 GB free for a
  4 GB guest); the agent stopped that one itself. The crashed first instance probably still holds its memory, so the
  emulator testing waits for those windows to be closed; until then the agent keeps building the remaining items.
- Chinese frequency in Traditional characters (2026-10-14). The frequency dictionary built on the phone from wordfreq
  has Chinese words in Simplified characters only (wordfreq folds Traditional into Simplified). For text in
  Traditional characters it gives no frequency, so results are not sorted by it. Options: (1, recommended) when the
  Chinese dictionary is built, add each word's Traditional form with the same rank, converted with OpenCC's tables
  (Apache 2.0, about 1 MB, bundled or downloaded with the list); (2) leave it Simplified only. The agent will decide
  this on the `languages/zh` branch with option 1 unless told otherwise.

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

### Korean (`languages/ko`)

- Catalog: KRDICT (11 gloss languages, with and without examples), STDICT, wty ko→en 7.4 MB and others, pronunciation,
  frequency (wordfreq if it has Korean, else CC100 with its license checked).
- The lookup start at the spaced word, the Korean recognizer; rule names are endings and need no translation.
- Questions: KRDICT's packaging has no license of its own (the data is CC BY-SA): list it as usable or not; Hanja
  entries (single characters) if a licensed source exists.

### Before group 1 is merged

- The postponed items (bundled Japanese dictionaries, hosting).
- A look at the APK size per ABI against v0.2.2 (48.4 MB arm64).

## Stage C: group 2 (es, fr, de, it, pt)

- Rule names: es 15, fr 9, de 5; it and pt take their forms from the dictionaries (the forms dictionary is mandatory).
- Catalog: wty →en main, →ru (es 1.2, fr 1.9, de 6.2, it 1.1, pt 0.8 MB) and the other gloss editions, pronunciation
  (best editions: fr-fr 19.2, de-de 12.7, es-es 10 MB, it-en 1.3, pt-en 2.8), frequency.
- Checks: accents with the ML Kit Latin model; audio regions (Brazil, Latin America, Spain, France).
- Questions per language: the recommended dictionaries; FreeDict deu-rus, fra-rus, ita-rus, spa-rus conversions.

## Stage D: group 3 (ru, pl, tr, vi)

- Before Russian: Paddle against Tesseract on real screenshots; MNN against ONNX Runtime by size and speed; the
  chosen engine fills the A11 slot (runtime in the APK, detector and the East Slavic recognizer as downloads).
- With the first downloadable model: the Dictionaries page block of turned-off languages lists only languages with
  dictionaries, and its size and delete text leave models out, although the delete removes them; count the models in
  (the wording needs all 14 locales).
- Russian: forms from the dictionaries, the second form-of step, stress marks in readings, OpenRussian built and
  hosted, the Zaliznyak meta dictionary with its non-commercial mark, pronunciation ru 6.1–6.5 MB.
- Polish: forms from the dictionary (pl→en 22 MB). Turkish: dotted and dotless i in case folding. Vietnamese: words of
  several syllables through word resolution; diacritics with the ML Kit Latin model (else Paddle latin).
- Questions per language: the recommended dictionaries; anything the comparison leaves open.

## Stage E: group 4 (decided when group 3 is done)

Whether it joins the big release (decision row). It would bring right-to-left hit testing (Arabic, Hebrew, Persian),
Paddle's Arabic, Cyrillic, Greek and Thai models, cloud-only scripts (Hebrew, Georgian, Armenian, Khmer, Lao,
Kannada, Syriac) and Ukrainian's 35 rule names.

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
