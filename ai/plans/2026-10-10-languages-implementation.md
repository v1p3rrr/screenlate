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
| Branches per language | The shared base (profiles, settings split, QuickJS, form-of table, model downloads) is built first in the multi-language branch; then a branch per language (`languages/en`, `languages/zh`, …) merged into it when the language works on the phone (owner, 2026-10-09) |
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

## Open items (postponed by the owner)

Decided when the first language or group 1 is ready, before it is merged into the multi-language branch.

- Bundled Japanese dictionaries (25 MB in the APK): keep them bundled or download them when Japanese is turned on;
  the owner leans to keeping them bundled.
- Hosting the files we build or mirror (OCR runtime models, wordfreq frequency dictionaries, dictionaries without
  releases such as OpenRussian and FreeDict conversions): likely a public repository of our own; Hugging Face to be
  looked at. Until then test builds download them from wherever is handy (for the emulator, `adb push` and a local
  URL are enough; for the owner's phone, ask before putting anything on a public host).

## Branches and workflow

- `languages/main`: the multi-language branch, off `main`. (Git cannot hold a branch `languages` next to
  `languages/en`, hence the name.) `main` stays releasable for Japanese fixes and is merged into `languages/main`
  regularly, at least after every release and before every language branch starts.
- `languages/<code>`: one branch per language off `languages/main`, merged back when the language works on the
  phone; the next language starts from the merged state.
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

- 2026-10-10: plan written from the decisions of `2026-10-09-more-languages.md` and the owner's answers of the day
  (pronunciation dictionaries, scope, turning a language off, catalog threshold, links to unlicensed dictionaries,
  switching during a scan); requests 20–22.
- 2026-10-10: recommended dictionaries (both gloss languages ticked, one entry per gloss language by quality), English
  first; implementation started.
