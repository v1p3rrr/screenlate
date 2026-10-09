# More languages (phase 8)

Status: research and experiments; paused 2026-10-09 with a handoff (see "Handoff"). The owner answered the general topics (see Decisions); concrete questions follow
in the language branch once the hypotheses below are checked. Research findings: `notes/languages.md`.

## Owner requests

1. (2026-10-09) List the tasks left, then move on to support for other languages: collect what is needed, including
   how Yomitan works with other languages, and prepare an outline for the discussion: what we need to find out and
   which general topics the owner answers first; concrete questions come after.
2. (2026-10-09) First answer: can hoshidicts serve the other languages, or is it tied to Japanese? If it is, another
   engine would be needed, or a port of Yomitan's engine to Android, which the owner would rather avoid (a rewrite
   from scratch risks many bugs).
3. (2026-10-09) Check whether most languages, at least the main ones, have dictionaries we may put in the catalog
   (licenses and other conditions). Dictionaries without a license or with a bad one go on a separate list for a
   decision; the owner may accept the risk while the app is not on Google Play.
4. (2026-10-09) Find out the size of the on-device recognition models; if small, bundle them, else offer them as
   optional downloads like catalog dictionaries, if that is possible at all. Look for on-device recognition for the
   languages ML Kit does not read before settling for cloud and app text only.
5. (2026-10-09) Owner's question: Chinese in simplified and traditional characters, separate profiles or one?
6. (2026-10-09) Propose how global settings and per-language settings combine, page by page.
7. (2026-10-09) Work order: research deeper (search, tests, the emulator), try one of English or Chinese (whichever is
   easier) in an experimental branch to check the hypotheses, then ask concrete questions in the language branch,
   agree on the implementation plan, and build it from scratch there (the experiments may be thrown away, at most
   their pieces reused).
8. (2026-10-09) Owner's questions: is hoshidicts only for Japanese or for every language like Yomitan, and would the
   fix be a library in another language built in? Answered: storage, query and sorting are language-agnostic; only
   text processing and deinflection are Japanese. The fix tried in the experiment is Yomitan's own language code
   run in QuickJS (a small C JavaScript engine, MIT, compiled into the same native library, about +1.3 MB per ABI)
   plus native form-of following; alternatives are `androidx.javascriptengine` or a Kotlin port. The choice is a
   concrete question for later.
9. (2026-10-09) Also experiment with a language that has no deinflection rules in Yomitan and gets its forms only
   from the dictionary's form-of entries, e.g. Russian.
10. (2026-10-09) Dictionaries: every language needs at least a dictionary into English, preferably also into
    Russian, optionally into the other main interface languages.
11. (2026-10-09) Priority by popularity among learners (an estimate the owner brought): learners in general:
    English, Spanish, French, Japanese, German, Korean, Italian, Chinese, Portuguese; immersion learners:
    Japanese, English, Spanish, Korean, Chinese, then Russian, French and others. These languages get support
    first.
12. (2026-10-09) Do not carry other languages' files in the APK: a user of Japanese only should not get megabytes
    for other languages. Large files a language needs (required dictionaries, on-device recognition models, the
    local OCR engine) are downloaded when its profile is turned on: from the publishers' links as the catalog does
    now, or, at worst, from a public GitHub repository of our own for models and possibly dictionaries.
13. (2026-10-09) Owner's question again (request 5): does Chinese need a split by script, traditional and simplified,
    and by what logic?
14. (2026-10-09) Before deciding on translating inflection names: count how many such names and tags there are in
    total over all languages (counted, `notes/languages.md`, "Inflection names and tags: volume").
15. (2026-10-09) On-device OCR: are Tesseract and PaddleOCR the only candidates; Paddle seems better at recognition,
    Tesseract has more languages; think through the most rational setup, possibly combining them (e.g. Paddle for
    its scripts, Tesseract for the rest). Explain how the engine and the per-language models relate (engine in the
    APK, models downloaded?).
16. (2026-10-09) Risky dictionaries: a summary per language of what is missing with licensed dictionaries only.
    Owner's question: would it be legally fine to upload the unlicensed dictionaries to a repository of ours and
    link to it from the app, so that users download and import them by hand?
17. (2026-10-09) Build the OCR architecture so that Tesseract can be integrated later without much trouble.

## Decisions

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
| Risky dictionaries | The catalog offers dictionaries with open licenses and non-commercial ones (words.hk, the Zaliznyak meta dictionary), the latter marked with their license and removed if the app ever becomes paid, ad-supported or goes to Google Play; dictionaries without a license only as a link to the author's page in the help; conversions of commercial dictionaries nowhere; no БКРС for now (owner, 2026-10-09, request 16) |
| Korean lookup start | The lookup starts at the start of the space-separated word, not at the aimed syllable as in Yomitan: a finger aim on a phone often lands on a neighboring syllable, and a start mid-word finds endings or unrelated words (부하 in 공부하고); shorter matches from the start still show, compound parts are found by search (agent's choice, delegated by the owner, 2026-10-09) |
| Text to speech | The last default audio source for languages other than Japanese; played only, never put into notes (owner, 2026-10-09) |
| Inflection names and tags | Translated into all 14 interface languages, a language's rule names and descriptions when that language is added; the Wiktionary tag list (~105 words) with the first group and extended per language; tags outside the list show as in the dictionary; Anki's `{conjugation}` keeps the English name (owner, 2026-10-09) |
| Chinese script | Glyph forms (SC/TC `lang` tag) and the headword spelling follow the text under the aim; a setting gives the script for words written alike in both and for typed searches, and may fix one script for everything (owner, 2026-10-09: "1 + a choice in settings") |

## Open items (postponed by the owner)

- Bundled Japanese dictionaries (25 MB in the APK): keep them bundled or download them when Japanese is turned on.
  Must be decided before the first multi-language release; the owner leans to keeping them bundled (2026-10-09).
- Hosting the files we build or mirror (OCR runtime libraries, wordfreq frequency dictionaries, dictionaries without
  releases such as OpenRussian): likely a public repository of our own; Hugging Face to be looked at. Decided when
  the main work is done; until then test builds download them from wherever is handy (owner, 2026-10-09).

## General topics for the owner

1. Languages and order: which languages first (English, Chinese, Korean, European languages, Russian or Ukrainian
   for people learning them), and whether "any language with a Yomitan dictionary" (generic support without rules
   of its own) is part of the first round.
2. Who it is for: the owner's own study languages, or users in general (affects the catalog and the defaults).
3. Depth per language: full (deinflection, on-device recognition, fonts, audio, Anki presets) or basic (dictionary
   lookup with case and diacritics handling, form-of entries).
4. Choosing the language of the text: one active language switched by hand (as Yomitan profiles), several at once
   chosen by the script under the aim, a language per app, or a mix. Ambiguous scripts: Han (Japanese or Chinese),
   Latin (English, German, …), Cyrillic (Russian, Ukrainian). English words inside Japanese text.
5. Settings per language (Anki deck and note type, audio sources, font, translation, scan length) or shared.
6. Words and phrases in languages with spaces: the whole word under the aim, multi-word entries first ("look up
   to", "New York"), case and accents ignored.
7. Dictionaries: what the catalog offers per language (Wiktionary main, glossary and IPA, CC-CEDICT, KRDICT, others
   with checked licenses), whether anything is bundled in the APK, character dictionaries (Hanzi, Hanja).
8. On-device recognition: bundle the Chinese and Korean models (APK +~4 MB per script and ABI), download them
   through Google Play services, or cloud only; languages without an on-device model (Russian, Ukrainian, Arabic,
   Hebrew, Thai); right-to-left scripts in or out.
9. Popup per language: readings (pinyin, romanization), IPA in place of pitch accents, audio sources, fonts
   ("Only for Japanese text" becomes the language's text), the dock glyph.
10. Anki per language: note types and suggested templates, Japanese-only markers, a deck per language.
11. Search screen and "Look up in Screenlate": which language they use.
12. How to proceed: a prototype with one language on the emulator first, then the rest; releases marked
    experimental or not.

Technical choices (where deinflection runs: an extended hoshidicts or a Kotlin layer over its exact query; porting
Yomitan's rules, which stay in the GPL modules) come with a recommendation after the general answers.

## To find out (agent)

Status 2026-10-09 after the English and Chinese experiment (`notes/languages.md`, "Experiment"):

- Done: wty en→en, en→ru and zh→en imported in the app and looked up in the popup; form-of followed natively.
  Import time of en→en was not recorded.
- Done: extending hoshidicts is not needed for the lookup itself: Yomitan's language code in QuickJS makes the
  candidates, hoshidicts answers exact queries without a submodule change. Optional submodule changes: form-of at
  import (the "redirects" field), per-row part-of-speech rules.
- Done: lookup cost (candidates 0.2–18 ms per call by language, queries 0.1–4 ms).
- Done: Chinese on device costs about 0 MB more in ML Kit, Korean about 0.8 MB; other engines measured.
- Done (second session): cloud recognition reads Korean; ML Kit needs the Chinese and Korean recognizers (the
  Japanese one reads them as garbage), +0.56 MB in the APK for both. Open: word boxes and `content_language`.
- Done (second session): Korean in the popup (notes, "Korean"), audio per language ("Audio per language"), compact
  form-of storage ("Compact form-of storage"), Cyrillic engine costs ("OCR"), the settings draft below.
- Open: whole-word hit testing and highlight for Latin text beyond the experiment's word start; sentence splitting
  with abbreviations.
- In progress: licenses of catalog candidates per language, a list of risky ones.
- Done: Russian as a language without transforms (request 9): works through the dictionary's form-of entries;
  the form-of rows make wty ru→en 273 MB on disk (notes, "Russian").
- Request 5: CC-CEDICT stores every term under both headwords and wty zh→en links simplified forms to traditional
  ones, so one Chinese profile works for both scripts; only the `lang` tag and fonts (SC or TC glyph forms) differ.
  To be asked with the concrete questions.
- Test material: `testdata/ocr/en-sample.png` and `zh-sample.png`.

## Settings: global and per language (draft, 2026-10-09)

Shown to the owner before the concrete questions. "Lang" = kept per language profile, "Global" = one value for
all languages. A setting that does not apply to a language is hidden in its profile.

| Page | Global | Lang |
|---|---|---|
| Home | – | the profile switch (chips of the turned-on languages), "Add a language" (required dictionary, on-device model, Anki guess); search and "Look up in Screenlate" use the active profile |
| Bubble | show, dock side and edges, aim mode, highlight, haptics, size, small text, text source, hidden apps, recognition engines, "device only when the cloud is late" | the glyph (日 A 中 한 Я) follows the profile, not a setting; a language without an on-device model uses the cloud and says so |
| Lookup | number of results | scan length (default per language), single character entries (kanji, hanzi, hanja; where a character dictionary exists), romaji (Japanese only), frequency dictionary and order |
| Popup | recognized text first, close after ➕, close off the word, text size, weight, thickness, definition copy button and mode | font and "font for all text", custom CSS |
| Anki and audio | AnkiDroid connection, volume, auto-play | deck, note type, field templates (markers offered per language), tags, duplicate check and behavior, audio sources (defaults per language) |
| Translation | everything (button, Anki field, services, target language) | the source language is the profile's |
| Dictionaries | storage (one copy per file even when two profiles use it) | which dictionaries are on and their order; the catalog shows the profile's language first |
| Background work, Appearance, Backup, About | everything | the backup holds every profile |
| Import from Yomitan | – | each Yomitan profile goes to the profile of its `general.language` |

- Storage: per-language keys carry the language code; today's keys stay Japanese's, so an upgrade keeps everything.
- Layout options for the owner: the language-bound items stay on today's pages in a block named after the profile
  with a language switch on the page (one language looks as today), or a "Languages" section with a page per
  language holding all of them.

## Handoff (2026-10-09, end of the research session)

Everything found is in `notes/languages.md`: Yomitan's language support, hoshidicts, the experiment ("Experiment",
"Russian"), dictionaries with licenses and gloss-language coverage ("Catalog candidates and licenses"), OCR sizes.

Branch `experiment/languages` (pushed; may be thrown away, pieces reusable):
- 0732dea English and Chinese, 89f9073 Russian, acf21f1 Korean (`KoreanSupport`, letter resolution) plus
  `scripts/yomitan-language/harness/` (native lookup harness for the emulator) and `scripts/yomitan-language/tools/`
  (`ru_peek.py` rows of a dictionary, `split_formof.py` split lemma and form-of rows, `wty_pairs.py` table of wty
  sizes from the Hugging Face tree API `https://huggingface.co/api/datasets/daxida/wty-release/tree/main/latest/dict/<src>?recursive=true`).
- Bundles: `node scripts/yomitan-language/build.mjs <Yomitan checkout> <out dir> <iso>...` (after `npm ci` there),
  output goes to `dictionary/engine-hoshidicts/src/main/assets/yomitan-language/` (en, zh, ru, ko are committed).
- The overlay's language comes from the file `files/experiment-language` in the debug app (`echo ko | adb shell
  run-as com.vpr.screenlate.debug tee files/experiment-language`); a uiautomator dump reconnects the service, which
  re-reads it. Test images: `scripts/text-image.py` (Cyrillic needs `FONT=C:/Windows/Fonts/arial.ttf`),
  `scripts/debug-device.sh images` and `show <name>.png`; drag the bubble with `input motionevent DOWN/MOVE/UP`, the
  aim sits about 133 px above the bubble's center.

Emulator state (Pixel_10_Pro, started with `-no-snapshot-save`): debug app with the experiment build (Korean);
imported in the app: wty-en-ru, wty-en-en, wty-zh-en, wty-ru-en, KRDICT RU (ko→ru); `experiment-language` = `ko`.
`/sdcard/Download` has wty-en-ru, wty-zh-en, wty-ru-en, KO-RU.KRDICT.No.Examples, Frequency.CC100.Korean.
`/data/local/tmp/hd`: hoshidicts-cli `hd`, harness `ll`, bundles `y-*.js`, imported wty-en-ru, wty-ko-en,
wty-zh-en, wty-ru-en. Space on `/data` is short (under 1 GB free); delete test copies there first.

Was in progress:
- Korean in the popup: KRDICT RU imported (its index.json says target "ko", so the app recorded "ko → ko": the
  language guess trusts a wrong index); the CC100 frequency dictionary not imported yet; no popup screenshot yet.
  Next: import the frequency list, render a Korean sample (needs a Korean font for `text-image.py`, e.g.
  `C:/Windows/Fonts/malgun.ttf`), check deinflection (먹었습니다 → 먹다) and timing (9–18 ms per candidate call).

Second session (2026-10-09): the Korean popup check and items 1–4 below are done (notes and the settings draft);
the frequency list is imported, KRDICT's wrong "ko → ko" stays as is on the emulator. Next: the concrete questions
(item 5), then the new plan (item 6).

Still to do before the concrete questions (told to the owner):
1. Audio per language: whether Lingua Libre and Wiktionary find clips for en, zh, ru, ko when given the language
   (today the Japanese sources and settings are used).
2. Compact form-of storage: estimate the size of a form → (lemma, tags) table against the 231 MB of rows.
3. ML Kit Chinese and Korean recognizers: the real APK delta; Tesseract or PaddleOCR for Cyrillic.
4. Settings design "global + per language", page by page, as a draft in chat. Starting point: language-bound
   settings live in the language profile (dictionaries, Anki deck and note type, audio sources, lookup rules such as
   scan length and text replacements, popup font and CSS), the rest stays global (bubble, appearance, background
   work, translation services and target language, backup), with per-language overrides only where the owner wants
   them (e.g. text size).
5. Then the concrete questions (AskUserQuestion, Russian, recommended option first): QuickJS against
   `androidx.javascriptengine` or a Kotlin port; form-of at import (hoshidicts "redirects" field via a patch or
   fork, or our own side table); translating inflection names (en 17, ko ~450 rules); following form-of a second
   level (ёжика → ёжик → ёж); showing every rule chain; frequency dictionaries we build from wordfreq (CC BY-SA 4.0)
   for the catalog; the risky dictionaries list; one Chinese profile for both scripts (request 5); the multi-language
   version branch and per-language branches. Then the implementation plan, built from scratch on that branch.
6. The implementation plan follows the decision table's order of languages (revised with request 11) and the
   gloss-language rule (request 10). It is a major re-plan: a new dated file in `plans/`, written after the
   concrete questions are answered.

The emulator ran from this session's background task; if it is gone, start it again with
`D:/Android/Sdk/emulator/emulator.exe -avd Pixel_10_Pro -no-snapshot-save` (the data partition keeps the app and
the imported dictionaries).

## Changelog

- 2026-10-09: plan started with the owner's request, the research (`notes/languages.md`) and the topics above.
- 2026-10-09: owner's answers to the general topics (Decisions) and requests 2-7.
- 2026-10-09: English and Chinese experiment in `experiment/languages`; findings in the notes; requests 8 and 9.
- 2026-10-09: Russian experiment (request 9) in the same branch.
- 2026-10-09: request 10 (gloss languages) and its decision row; license survey in the notes.
- 2026-10-09: Korean started in the experiment branch; handoff section for the next session (owner's request).
- 2026-10-09: request 11 (priority languages by popularity among learners); the order of languages revised by
  the owner: the first three as before, then the five most learned European languages, then the remaining
  interface languages, then the rest.
- 2026-10-09: second session: Korean checked in the popup, audio per language, compact form-of storage, ML Kit
  Chinese and Korean measured, Cyrillic engine costs, settings draft (notes and the section above).
- 2026-10-09: concrete questions, batches 1-4: decisions on the deinflection runtime, rule chains, form-of storage,
  the second form-of step, ML Kit Chinese and Korean, Chinese profiles and script, frequency dictionaries, Cyrillic
  OCR (preliminary); requests 12-15; open items (bundled Japanese dictionaries, hosting).
- 2026-10-09: batch 5: on-device OCR beyond ML Kit (Paddle only, Tesseract pluggable later), OCR runtime in the
  APK, inflection names and tags translated; requests 16 and 17.
- 2026-10-09: batch 6: risky dictionaries (open and non-commercial in the catalog, no БКРС for now), Korean lookup
  start (delegated), text to speech as the last default source.
